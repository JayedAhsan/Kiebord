use crate::dictionary::combined_parser::{CombinedDictData, DictMetadata};
use crate::dictionary::proximity::KeyProximity;
use std::cmp::Ordering;
use std::collections::{BinaryHeap, HashMap};
use std::time::{SystemTime, UNIX_EPOCH};

pub const MAGIC: u32 = 0x4B42_4443; // "KBDC" (Kiebord Dictionary Core)
pub const VERSION: u16 = 5;
pub const FLAG_LABELS_U8: u16 = 1;
pub const FLAG_DEGREE_U8: u16 = 2;
pub const MAX_SYMBOLS: usize = 256;
pub const CHECKPOINT_STRIDE: usize = 32;
pub const CHECKPOINT_SHIFT: u32 = 5;
const UNUSED_SYMBOL: u16 = 0xFFFF;

fn pad4(len: usize) -> usize {
    (len + 3) & !3
}

/// Zero-copy, in-place binary dictionary with compact trie and word-ID bigrams.
pub struct BinaryDict<'a> {
    bytes: &'a [u8],
    word_count: u32,
    node_count: usize,
    _edge_count: usize,
    _symbol_count: usize,
    _bigram_count: usize,
    labels_are_u8: bool,
    symbols_table: [u16; 256],
    child_count_off: usize,
    checkpoints_off: usize,
    edge_label_off: usize,
    freq_off: usize,
    max_subtree_off: usize,
    is_word_off: usize,
    bigram_head_nodes_off: usize,
    bigram_run_start_off: usize,
    bigram_followers_off: usize,
    _bigram_freqs_off: usize,
    bigram_head_count: usize,
    meta_off: usize,
    meta_len: usize,
}

impl<'a> BinaryDict<'a> {
    /// Compiles parsed `.combined` unigrams and bigrams into a compact `KBDC` binary dictionary.
    pub fn compile_combined(data: &CombinedDictData) -> Vec<u8> {
        // 1. Collect all distinct vocabulary words (unigrams + bigram words)
        let mut vocab_set = std::collections::BTreeSet::new();
        for (w, _) in &data.unigrams {
            let clean = w.trim();
            if !clean.is_empty() {
                vocab_set.insert(clean.to_string());
            }
        }
        for (prev, cands) in &data.bigrams {
            let clean_prev = prev.trim();
            if !clean_prev.is_empty() {
                vocab_set.insert(clean_prev.to_string());
            }
            for (nw, _) in cands {
                let clean_nw = nw.trim();
                if !clean_nw.is_empty() {
                    vocab_set.insert(clean_nw.to_string());
                }
            }
        }

        // Vocabulary sorted in lexicographical UTF-8 byte order
        let vocab: Vec<String> = vocab_set.into_iter().collect();
        let word_count = vocab.len();
        let mut word_to_id: HashMap<&str, u32> = HashMap::with_capacity(word_count);
        for (id, word) in vocab.iter().enumerate() {
            word_to_id.insert(word.as_str(), id as u32);
        }

        // Build unigram frequency map
        let mut freq_map: HashMap<&str, u8> = HashMap::with_capacity(data.unigrams.len());
        for (w, f) in &data.unigrams {
            let clean = w.trim();
            let entry = freq_map.entry(clean).or_insert(*f);
            if *f > *entry {
                *entry = *f;
            }
        }

        // 2. Build intermediate prefix tree in UTF-16 units
        struct TempNode {
            freq: u8,
            is_word: bool,
            children: Vec<(u16, TempNode)>,
        }

        let mut root = TempNode {
            freq: 0,
            is_word: false,
            children: Vec::new(),
        };

        for word in &vocab {
            let f = *freq_map.get(word.as_str()).unwrap_or(&1);
            let units: Vec<u16> = word.encode_utf16().collect();
            let mut curr = &mut root;
            for unit in units {
                let pos = curr.children.iter().position(|(u, _)| *u == unit);
                let idx = match pos {
                    Some(i) => i,
                    None => {
                        curr.children.push((
                            unit,
                            TempNode {
                                freq: 0,
                                is_word: false,
                                children: Vec::new(),
                            },
                        ));
                        curr.children.sort_by_key(|(u, _)| *u);
                        curr.children.iter().position(|(u, _)| *u == unit).unwrap()
                    }
                };
                curr = &mut curr.children[idx].1;
            }
            curr.is_word = true;
            curr.freq = f;
        }

        // 3. Flatten tree breadth-first (BFS)
        // Edge e always points to node e + 1!
        let mut degrees: Vec<u8> = Vec::new();
        let mut edge_labels_raw: Vec<u16> = Vec::new();
        let mut freqs: Vec<u8> = Vec::new();
        let mut is_word: Vec<bool> = Vec::new();

        let mut queue = std::collections::VecDeque::new();
        degrees.push(root.children.len() as u8);
        freqs.push(root.freq);
        is_word.push(root.is_word);
        queue.push_back(&root);

        while let Some(t_node) = queue.pop_front() {
            for &(unit, ref child) in &t_node.children {
                edge_labels_raw.push(unit);
                degrees.push(child.children.len() as u8);
                freqs.push(child.freq);
                is_word.push(child.is_word);
                queue.push_back(child);
            }
        }
        let node_count = freqs.len();
        let edge_count = edge_labels_raw.len();

        let mut child_starts: Vec<u32> = Vec::with_capacity(node_count);
        let mut running_sum = 0u32;
        for &deg in &degrees {
            child_starts.push(running_sum);
            running_sum += deg as u32;
        }

        // 4. Compute max_subtree_freq bottom-up
        let mut max_subtree_freq = freqs.clone();
        for id in (0..node_count).rev() {
            let start = child_starts[id] as usize;
            let count = degrees[id] as usize;
            let mut best = if is_word[id] { freqs[id] } else { 0 };
            for e in start..start + count {
                let child_id = e + 1;
                if child_id < node_count {
                    best = best.max(max_subtree_freq[child_id]);
                }
            }
            max_subtree_freq[id] = best;
        }

        // 5. Symbol table (compact 1-byte edge labels)
        let mut symbols = edge_labels_raw.clone();
        symbols.sort_unstable();
        symbols.dedup();
        let labels_are_u8 = symbols.len() <= MAX_SYMBOLS;
        let symbol_count = if labels_are_u8 { symbols.len() } else { 0 };

        // 6. Checkpoints for 32-node stride
        let checkpoint_count = (node_count >> CHECKPOINT_SHIFT) + 1;
        let mut checkpoints: Vec<u32> = Vec::with_capacity(checkpoint_count);
        for k in 0..checkpoint_count {
            let node_idx = (k << CHECKPOINT_SHIFT).min(node_count - 1);
            checkpoints.push(child_starts[node_idx]);
        }

        // 7. Map each word to its terminal node_id in the BFS tree
        let mut word_to_node: HashMap<&str, u32> = HashMap::with_capacity(word_count);
        for word in &vocab {
            let mut curr = 0u32;
            for unit in word.encode_utf16() {
                let start = child_starts[curr as usize] as usize;
                let count = degrees[curr as usize] as usize;
                let mut found = None;
                for e in start..start + count {
                    if edge_labels_raw[e] == unit {
                        found = Some((e + 1) as u32);
                        break;
                    }
                }
                curr = found.expect("inserted word in trie");
            }
            word_to_node.insert(word.as_str(), curr);
        }

        // 8. Sparse word-ID bigram section
        let mut head_bigrams: Vec<(u32, Vec<(u32, u8)>)> = Vec::new();
        for (head_word, cands) in &data.bigrams {
            if let Some(&head_node) = word_to_node.get(head_word.as_str()) {
                let mut sorted_cands = cands.clone();
                sorted_cands.sort_by(|a, b| b.1.cmp(&a.1));
                let mut followers = Vec::new();
                for (nw, f) in sorted_cands.into_iter().take(24) {
                    if let Some(&nw_node) = word_to_node.get(nw.as_str()) {
                        followers.push((nw_node, f));
                    }
                }
                if !followers.is_empty() {
                    head_bigrams.push((head_node, followers));
                }
            }
        }
        head_bigrams.sort_by_key(|(hn, _)| *hn);

        let bigram_head_count = head_bigrams.len();
        let mut bigram_head_nodes: Vec<u32> = Vec::with_capacity(bigram_head_count);
        let mut bigram_run_start: Vec<u32> = Vec::with_capacity(bigram_head_count + 1);
        let mut bigram_followers: Vec<u32> = Vec::new();
        let mut bigram_freqs: Vec<u8> = Vec::new();

        for (hn, followers) in head_bigrams {
            bigram_head_nodes.push(hn);
            bigram_run_start.push(bigram_followers.len() as u32);
            for (fn_node, freq) in followers {
                bigram_followers.push(fn_node);
                bigram_freqs.push(freq);
            }
        }
        bigram_run_start.push(bigram_followers.len() as u32);
        let total_bigrams = bigram_followers.len();

        // 9. Resolve metadata & calculate section offsets
        let mut meta = data.metadata.clone();
        if meta.date == 0 {
            meta.date = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .map(|d| d.as_secs())
                .unwrap_or(0);
        }
        let meta_bytes = meta.to_header_string().into_bytes();

        let header_bytes = 4 + 2 + 2 + 4 * 6 + 12 * 4;
        let mut cursor = header_bytes;
        let mut offsets = [0u32; 12];

        let mut alloc_section = |size: usize| -> u32 {
            let at = cursor;
            cursor += pad4(size);
            at as u32
        };

        offsets[0] = alloc_section(symbol_count * 2);
        offsets[1] = alloc_section(node_count); // degrees
        offsets[2] = alloc_section(checkpoint_count * 4); // checkpoints
        offsets[3] = alloc_section(if labels_are_u8 { edge_count } else { edge_count * 2 });
        offsets[4] = alloc_section(node_count); // freqs
        offsets[5] = alloc_section(node_count); // max_subtree_freq
        offsets[6] = alloc_section(node_count.div_ceil(8)); // is_word bitset
        offsets[7] = alloc_section(bigram_head_count * 4); // bigram_head_nodes
        offsets[8] = alloc_section((bigram_head_count + 1) * 4); // bigram_run_start
        offsets[9] = alloc_section(total_bigrams * 4); // bigram_followers
        offsets[10] = alloc_section(total_bigrams); // bigram_freqs
        offsets[11] = alloc_section(4 + meta_bytes.len()); // metadata section (u32 len + utf8 bytes)

        // 10. Write binary output
        let mut out = Vec::with_capacity(cursor);
        out.extend_from_slice(&MAGIC.to_be_bytes());
        out.extend_from_slice(&VERSION.to_be_bytes());
        let flags = (if labels_are_u8 { FLAG_LABELS_U8 } else { 0 }) | FLAG_DEGREE_U8;
        out.extend_from_slice(&flags.to_be_bytes());
        out.extend_from_slice(&(word_count as u32).to_be_bytes());
        out.extend_from_slice(&(node_count as u32).to_be_bytes());
        out.extend_from_slice(&(edge_count as u32).to_be_bytes());
        out.extend_from_slice(&(symbol_count as u32).to_be_bytes());
        out.extend_from_slice(&(total_bigrams as u32).to_be_bytes());
        out.extend_from_slice(&(bigram_head_count as u32).to_be_bytes());

        for off in offsets {
            out.extend_from_slice(&off.to_be_bytes());
        }

        // Section 0: symbols
        for &sym in &symbols[..symbol_count] {
            out.extend_from_slice(&sym.to_be_bytes());
        }
        while out.len() < offsets[1] as usize { out.push(0); }

        // Section 1: degrees
        out.extend_from_slice(&degrees);
        while out.len() < offsets[2] as usize { out.push(0); }

        // Section 2: checkpoints
        for &cp in &checkpoints {
            out.extend_from_slice(&cp.to_be_bytes());
        }
        while out.len() < offsets[3] as usize { out.push(0); }

        // Section 3: edge_labels
        if labels_are_u8 {
            for &lbl in &edge_labels_raw {
                let idx = symbols.binary_search(&lbl).expect("symbol in table") as u8;
                out.push(idx);
            }
        } else {
            for &lbl in &edge_labels_raw {
                out.extend_from_slice(&lbl.to_be_bytes());
            }
        }
        while out.len() < offsets[4] as usize { out.push(0); }

        // Section 4: freqs
        out.extend_from_slice(&freqs);
        while out.len() < offsets[5] as usize { out.push(0); }

        // Section 5: max_subtree_freq
        out.extend_from_slice(&max_subtree_freq);
        while out.len() < offsets[6] as usize { out.push(0); }

        // Section 6: is_word bitset
        let mut byte = 0u8;
        for (id, &w) in is_word.iter().enumerate() {
            if w {
                byte |= 1 << (id & 7);
            }
            if id & 7 == 7 {
                out.push(byte);
                byte = 0;
            }
        }
        if node_count & 7 != 0 {
            out.push(byte);
        }
        while out.len() < offsets[7] as usize { out.push(0); }

        // Section 7: bigram_head_nodes
        for &hn in &bigram_head_nodes {
            out.extend_from_slice(&hn.to_be_bytes());
        }
        while out.len() < offsets[8] as usize { out.push(0); }

        // Section 8: bigram_run_start
        for &bs in &bigram_run_start {
            out.extend_from_slice(&bs.to_be_bytes());
        }
        while out.len() < offsets[9] as usize { out.push(0); }

        // Section 9: bigram_followers
        for &bf in &bigram_followers {
            out.extend_from_slice(&bf.to_be_bytes());
        }
        while out.len() < offsets[10] as usize { out.push(0); }

        // Section 10: bigram_freqs
        out.extend_from_slice(&bigram_freqs);
        while out.len() < offsets[11] as usize { out.push(0); }

        // Section 11: metadata
        out.extend_from_slice(&(meta_bytes.len() as u32).to_be_bytes());
        out.extend_from_slice(&meta_bytes);
        while out.len() < cursor { out.push(0); }

        out
    }

    /// Loads a binary dictionary into an in-place zero-copy reader.
    pub fn from_bytes(bytes: &'a [u8]) -> Result<Self, &'static str> {
        let header_min = 4 + 2 + 2 + 4 * 6 + 11 * 4;
        if bytes.len() < header_min {
            return Err("Binary dict too small");
        }

        let magic = u32::from_be_bytes(bytes[0..4].try_into().unwrap());
        if magic != MAGIC {
            return Err("Invalid binary dict magic (expected KBDC)");
        }

        let version = u16::from_be_bytes(bytes[4..6].try_into().unwrap());
        if version != VERSION && version != 4 {
            return Err("Unsupported binary dict version");
        }

        let flags = u16::from_be_bytes(bytes[6..8].try_into().unwrap());
        let word_count = u32::from_be_bytes(bytes[8..12].try_into().unwrap());
        let node_count = u32::from_be_bytes(bytes[12..16].try_into().unwrap()) as usize;
        let edge_count = u32::from_be_bytes(bytes[16..20].try_into().unwrap()) as usize;
        let symbol_count = u32::from_be_bytes(bytes[20..24].try_into().unwrap()) as usize;
        let bigram_count = u32::from_be_bytes(bytes[24..28].try_into().unwrap()) as usize;
        let bigram_head_count = u32::from_be_bytes(bytes[28..32].try_into().unwrap()) as usize;

        let labels_are_u8 = flags & FLAG_LABELS_U8 != 0;

        let mut offsets = [0usize; 11];
        for i in 0..11 {
            offsets[i] = u32::from_be_bytes(bytes[32 + i * 4..36 + i * 4].try_into().unwrap()) as usize;
        }

        let (meta_off, meta_len) = if version >= 5 && bytes.len() >= 32 + 12 * 4 {
            let m_off = u32::from_be_bytes(bytes[32 + 11 * 4..36 + 11 * 4].try_into().unwrap()) as usize;
            if m_off + 4 <= bytes.len() {
                let m_len = u32::from_be_bytes(bytes[m_off..m_off + 4].try_into().unwrap()) as usize;
                (m_off, m_len)
            } else {
                (0, 0)
            }
        } else {
            (0, 0)
        };

        let mut symbols_table = [UNUSED_SYMBOL; 256];
        if labels_are_u8 {
            for i in 0..symbol_count.min(256) {
                symbols_table[i] = u16::from_be_bytes(bytes[offsets[0] + i * 2..offsets[0] + i * 2 + 2].try_into().unwrap());
            }
        }

        Ok(BinaryDict {
            bytes,
            word_count,
            node_count,
            _edge_count: edge_count,
            _symbol_count: symbol_count,
            _bigram_count: bigram_count,
            labels_are_u8,
            symbols_table,
            child_count_off: offsets[1],
            checkpoints_off: offsets[2],
            edge_label_off: offsets[3],
            freq_off: offsets[4],
            max_subtree_off: offsets[5],
            is_word_off: offsets[6],
            bigram_head_nodes_off: offsets[7],
            bigram_run_start_off: offsets[8],
            bigram_followers_off: offsets[9],
            _bigram_freqs_off: offsets[10],
            bigram_head_count,
            meta_off,
            meta_len,
        })
    }

    pub fn metadata(&self) -> DictMetadata {
        if self.meta_len > 0 && self.meta_off + 4 + self.meta_len <= self.bytes.len() {
            if let Ok(s) = std::str::from_utf8(&self.bytes[self.meta_off + 4..self.meta_off + 4 + self.meta_len]) {
                return DictMetadata::from_header_str(s);
            }
        }
        DictMetadata::default()
    }

    pub fn read_file_metadata(path: &std::path::Path) -> Result<(DictMetadata, u32), &'static str> {
        let file = std::fs::File::open(path).map_err(|_| "Failed to open dict file")?;
        let mmap = unsafe { memmap2::Mmap::map(&file) }.map_err(|_| "Failed to map dict file")?;
        let dict = BinaryDict::from_bytes(&mmap[..])?;
        Ok((dict.metadata(), dict.word_count()))
    }

    #[inline]
    pub fn word_count(&self) -> u32 {
        self.word_count
    }

    #[inline]
    pub fn node_count(&self) -> usize {
        self.node_count
    }

    #[inline]
    pub fn child_start(&self, node: usize) -> usize {
        let cp_idx = node >> CHECKPOINT_SHIFT;
        let cp_val = u32::from_be_bytes(self.bytes[self.checkpoints_off + cp_idx * 4..self.checkpoints_off + cp_idx * 4 + 4].try_into().unwrap()) as usize;
        let start_node = node & !(CHECKPOINT_STRIDE - 1);
        let sum: usize = self.bytes[self.child_count_off + start_node..self.child_count_off + node]
            .iter()
            .map(|&d| d as usize)
            .sum();
        cp_val + sum
    }

    #[inline]
    pub fn child_count(&self, node: usize) -> usize {
        self.bytes[self.child_count_off + node] as usize
    }

    #[inline]
    pub fn edge_label(&self, edge: usize) -> u16 {
        if self.labels_are_u8 {
            let sym_idx = self.bytes[self.edge_label_off + edge] as usize;
            self.symbols_table[sym_idx]
        } else {
            u16::from_be_bytes(self.bytes[self.edge_label_off + edge * 2..self.edge_label_off + edge * 2 + 2].try_into().unwrap())
        }
    }

    #[inline]
    pub fn edge_child(edge: usize) -> u32 {
        (edge + 1) as u32
    }

    #[inline]
    pub fn child(&self, node: u32, unit: u16) -> Option<u32> {
        let node_idx = node as usize;
        let start = self.child_start(node_idx);
        let count = self.child_count(node_idx);
        if count == 0 {
            return None;
        }

        let mut lo = start as isize;
        let mut hi = (start + count) as isize - 1;
        while lo <= hi {
            let mid = ((lo + hi) >> 1) as usize;
            match self.edge_label(mid).cmp(&unit) {
                Ordering::Less => lo = mid as isize + 1,
                Ordering::Greater => hi = mid as isize - 1,
                Ordering::Equal => return Some(Self::edge_child(mid)),
            }
        }
        None
    }

    #[inline]
    pub fn is_word(&self, node: u32) -> bool {
        let n = node as usize;
        (self.bytes[self.is_word_off + (n >> 3)] >> (n & 7)) & 1 != 0
    }

    #[inline]
    pub fn freq(&self, node: u32) -> u8 {
        self.bytes[self.freq_off + node as usize]
    }

    #[inline]
    pub fn max_subtree_freq(&self, node: u32) -> u8 {
        self.bytes[self.max_subtree_off + node as usize]
    }

    pub fn find_node(&self, word: &str) -> Option<u32> {
        let mut curr = 0u32;
        let mut exact_ok = true;
        for u in word.encode_utf16() {
            if let Some(next) = self.child(curr, u) {
                curr = next;
            } else {
                exact_ok = false;
                break;
            }
        }
        if exact_ok {
            return Some(curr);
        }

        // Case-insensitive traversal across full word (including Turkish dotted/dotless i/ı and diacritics)
        let units: Vec<u16> = word.encode_utf16().collect();
        let mut current_nodes = vec![0u32];
        for &u in &units {
            let mut next_nodes = Vec::new();
            let (targets, targets_len) = Self::case_variants_u16(u);
            for &node in &current_nodes {
                for &t in &targets[..targets_len] {
                    if let Some(next) = self.child(node, t) {
                        next_nodes.push(next);
                    }
                }
            }
            if next_nodes.is_empty() {
                return None;
            }
            current_nodes = next_nodes;
        }
        current_nodes.iter().copied().find(|&n| self.is_word(n)).or_else(|| current_nodes.first().copied())
    }

    #[inline]
    fn case_variants_u16(u: u16) -> ([u16; 3], usize) {
        let mut targets = [u, 0, 0];
        let len = match u {
            b if b >= b'a' as u16 && b <= b'z' as u16 => {
                targets[1] = b - 32;
                if b == b'i' as u16 {
                    targets[2] = 'İ' as u16;
                    3
                } else {
                    2
                }
            }
            b if b >= b'A' as u16 && b <= b'Z' as u16 => {
                targets[1] = b + 32;
                if b == b'I' as u16 {
                    targets[2] = 'ı' as u16;
                    3
                } else {
                    2
                }
            }
            u_val if u_val == 'İ' as u16 => {
                targets[1] = b'i' as u16;
                2
            }
            u_val if u_val == 'ı' as u16 => {
                targets[1] = b'I' as u16;
                2
            }
            u_val if u_val == 'ğ' as u16 => { targets[1] = 'Ğ' as u16; 2 }
            u_val if u_val == 'Ğ' as u16 => { targets[1] = 'ğ' as u16; 2 }
            u_val if u_val == 'ü' as u16 => { targets[1] = 'Ü' as u16; 2 }
            u_val if u_val == 'Ü' as u16 => { targets[1] = 'ü' as u16; 2 }
            u_val if u_val == 'ş' as u16 => { targets[1] = 'Ş' as u16; 2 }
            u_val if u_val == 'Ş' as u16 => { targets[1] = 'ş' as u16; 2 }
            u_val if u_val == 'ö' as u16 => { targets[1] = 'Ö' as u16; 2 }
            u_val if u_val == 'Ö' as u16 => { targets[1] = 'ö' as u16; 2 }
            u_val if u_val == 'ç' as u16 => { targets[1] = 'Ç' as u16; 2 }
            u_val if u_val == 'Ç' as u16 => { targets[1] = 'ç' as u16; 2 }
            _ => 1,
        };
        (targets, len)
    }

    #[inline]
    fn normalize_lowercase(s: &str) -> String {
        let mut out = String::with_capacity(s.len());
        for c in s.chars() {
            if c == 'İ' {
                out.push('i');
            } else {
                for lc in c.to_lowercase() {
                    if lc != '\u{307}' {
                        out.push(lc);
                    }
                }
            }
        }
        out
    }

    /// Traverses the dictionary trie depth-first using a single shared UTF-16 buffer,
    /// invoking `f(node, freq, word_utf16)` at every terminal word node.
    pub fn for_each_word<F: FnMut(u32, u8, &[u16])>(&self, mut f: F) {
        let mut buf = Vec::with_capacity(32);
        self.dfs_words(0, &mut buf, &mut f);
    }

    fn dfs_words<F: FnMut(u32, u8, &[u16])>(&self, node: u32, buf: &mut Vec<u16>, f: &mut F) {
        if node != 0 && self.is_word(node) {
            f(node, self.freq(node), buf);
        }
        let start = self.child_start(node as usize);
        let count = self.child_count(node as usize);
        for e in start..start + count {
            let child = Self::edge_child(e);
            buf.push(self.edge_label(e));
            self.dfs_words(child, buf, f);
            buf.pop();
        }
    }

    pub fn word_freq(&self, word: &str) -> u8 {
        match self.find_node(word) {
            Some(node) if self.is_word(node) => self.freq(node),
            _ => 1,
        }
    }

    #[inline]
    pub fn contains_word(&self, word: &str) -> bool {
        match self.find_node(word) {
            Some(node) => self.is_word(node),
            None => false,
        }
    }

    #[inline]
    pub fn checkpoint(&self, k: usize) -> u32 {
        let off = self.checkpoints_off + k * 4;
        u32::from_be_bytes(self.bytes[off..off + 4].try_into().unwrap())
    }

    #[inline]
    pub fn parent_node(&self, node: usize) -> Option<usize> {
        if node == 0 || node >= self.node_count {
            return None;
        }
        let edge = (node - 1) as u32;
        let checkpoint_count = (self.node_count >> CHECKPOINT_SHIFT) + 1;
        let mut lo = 0isize;
        let mut hi = checkpoint_count as isize - 1;
        let mut best_k = 0usize;
        while lo <= hi {
            let mid = ((lo + hi) >> 1) as usize;
            let cp_val = self.checkpoint(mid);
            if cp_val <= edge {
                best_k = mid;
                lo = mid as isize + 1;
            } else {
                hi = mid as isize - 1;
            }
        }

        let mut p = best_k << CHECKPOINT_SHIFT;
        let mut running = self.checkpoint(best_k);
        let max_p = (p + CHECKPOINT_STRIDE).min(self.node_count);
        let degrees_slice = &self.bytes[self.child_count_off..self.child_count_off + self.node_count];
        while p < max_p {
            running += degrees_slice[p] as u32;
            if edge < running {
                return Some(p);
            }
            p += 1;
        }
        None
    }

    pub fn word_at_node(&self, node: u32) -> Option<String> {
        if node == 0 || node as usize >= self.node_count || !self.is_word(node) {
            return None;
        }
        let mut chars = Vec::with_capacity(16);
        let mut curr = node as usize;
        while curr != 0 {
            let edge = curr - 1;
            chars.push(self.edge_label(edge));
            curr = self.parent_node(curr)?;
        }
        chars.reverse();
        Some(String::from_utf16_lossy(&chars))
    }

    #[inline]
    pub fn word_at(&self, id: u32) -> Option<String> {
        self.word_at_node(id)
    }

    #[inline]
    pub fn find_word_id(&self, word: &str) -> Option<u32> {
        match self.find_node(word) {
            Some(node) if self.is_word(node) => Some(node),
            _ => None,
        }
    }

    #[inline]
    pub fn bigram_head_node(&self, idx: usize) -> u32 {
        let off = self.bigram_head_nodes_off + idx * 4;
        u32::from_be_bytes(self.bytes[off..off + 4].try_into().unwrap())
    }

    #[inline]
    pub fn find_bigram_head(&self, head_node: u32) -> Option<usize> {
        if self.bigram_head_count == 0 {
            return None;
        }
        let mut lo = 0isize;
        let mut hi = self.bigram_head_count as isize - 1;
        while lo <= hi {
            let mid = ((lo + hi) >> 1) as usize;
            let val = self.bigram_head_node(mid);
            match val.cmp(&head_node) {
                Ordering::Less => lo = mid as isize + 1,
                Ordering::Greater => hi = mid as isize - 1,
                Ordering::Equal => return Some(mid),
            }
        }
        None
    }

    #[inline]
    pub fn bigram_run_start(&self, head_idx: usize) -> usize {
        let off = self.bigram_run_start_off + head_idx * 4;
        u32::from_be_bytes(self.bytes[off..off + 4].try_into().unwrap()) as usize
    }

    #[inline]
    pub fn bigram_run_end(&self, head_idx: usize) -> usize {
        let off = self.bigram_run_start_off + (head_idx + 1) * 4;
        u32::from_be_bytes(self.bytes[off..off + 4].try_into().unwrap()) as usize
    }

    #[inline]
    pub fn bigram_follower(&self, idx: usize) -> u32 {
        let off = self.bigram_followers_off + idx * 4;
        u32::from_be_bytes(self.bytes[off..off + 4].try_into().unwrap())
    }

    #[inline]
    pub fn bigram_freq(&self, idx: usize) -> u8 {
        self.bytes[self._bigram_freqs_off + idx]
    }

    /// Predicts next words based on preceding committed word in O(1) time without heap allocations.
    pub fn predict_next(&self, prev_word: &str, limit: usize) -> Vec<String> {
        let clean = Self::normalize_lowercase(prev_word.trim());
        let head_node = match self.find_node(&clean).or_else(|| self.find_node(prev_word.trim())) {
            Some(node) if self.is_word(node) => node,
            _ => return Vec::new(),
        };

        let head_idx = match self.find_bigram_head(head_node) {
            Some(idx) => idx,
            None => return Vec::new(),
        };

        let start = self.bigram_run_start(head_idx);
        let end = self.bigram_run_end(head_idx);
        let count = (end - start).min(limit);
        let mut results = Vec::with_capacity(count);
        for i in 0..count {
            let follower_node = self.bigram_follower(start + i);
            if let Some(w) = self.word_at(follower_node) {
                results.push(w);
            }
        }
        results
    }

    /// High-performance admissible best-first beam search with branch-and-bound pruning.
    pub fn suggest(&self, input: &str, limit: usize, prox: &KeyProximity) -> Vec<String> {
        let trimmed = input.trim();
        if trimmed.is_empty() {
            return Vec::new();
        }

        let is_all_upper = trimmed.chars().all(|c| !c.is_alphabetic() || c.is_uppercase())
            && trimmed.chars().any(|c| c.is_alphabetic());
        let is_title_case = !is_all_upper
            && trimmed.chars().next().map_or(false, |c| c.is_uppercase());
        let is_turkish_context = prox.is_case_pair('i', 'İ') || trimmed.contains('İ') || trimmed.contains('ı');

        let search_input = Self::normalize_lowercase(trimmed);
        let typed: Vec<u16> = search_input.encode_utf16().collect();

        const MAX_ACCUM_LEN: usize = 48;

        #[derive(Clone, Copy)]
        struct State {
            node: u32,
            typed_pos: usize,
            accum: [u16; MAX_ACCUM_LEN],
            accum_len: u8,
            cost: f32,
            bound: f32,
        }

        impl State {
            #[inline]
            fn accum_slice(&self) -> &[u16] {
                &self.accum[..self.accum_len as usize]
            }

            #[inline]
            fn accum_len(&self) -> usize {
                self.accum_len as usize
            }

            #[inline]
            fn with_pushed(&self, unit: u16) -> Option<([u16; MAX_ACCUM_LEN], u8)> {
                let len = self.accum_len as usize;
                if len >= MAX_ACCUM_LEN {
                    return None;
                }
                let mut next = self.accum;
                next[len] = unit;
                Some((next, (len + 1) as u8))
            }

            #[inline]
            fn with_pushed2(&self, u1: u16, u2: u16) -> Option<([u16; MAX_ACCUM_LEN], u8)> {
                let len = self.accum_len as usize;
                if len + 1 >= MAX_ACCUM_LEN {
                    return None;
                }
                let mut next = self.accum;
                next[len] = u1;
                next[len + 1] = u2;
                Some((next, (len + 2) as u8))
            }
        }

        impl PartialEq for State {
            fn eq(&self, other: &Self) -> bool {
                self.bound == other.bound
            }
        }
        impl Eq for State {}
        impl PartialOrd for State {
            fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
                self.bound.partial_cmp(&other.bound)
            }
        }
        impl Ord for State {
            fn cmp(&self, other: &Self) -> Ordering {
                self.partial_cmp(other).unwrap_or(Ordering::Equal)
            }
        }

        let max_cost = match typed.len() {
            0..=2 => 0.45f32,
            3..=4 => 0.75f32,
            _ => 1.1f32,
        };
        let mut heap: BinaryHeap<State> = BinaryHeap::with_capacity(256);
        let mut candidates: HashMap<String, (f32, u8)> = HashMap::new();

        let root_mf = self.max_subtree_freq(0) as f32;
        heap.push(State {
            node: 0,
            typed_pos: 0,
            accum: [0u16; MAX_ACCUM_LEN],
            accum_len: 0,
            cost: 0.0,
            bound: root_mf + 50.0,
        });

        let mut expansions = 0;
        let max_expansions = 1500;

        while let Some(state) = heap.pop() {
            expansions += 1;
            if expansions > max_expansions {
                break;
            }

            if state.cost > max_cost {
                continue;
            }

            // Pruning: if we have enough candidates and state cannot beat the worst
            if candidates.len() >= limit * 3 {
                let worst_score = candidates.values().map(|(s, _)| *s).fold(f32::INFINITY, f32::min);
                if state.bound < worst_score {
                    continue;
                }
            }

            // Reached end of typed string
            if state.typed_pos == typed.len() {
                if self.is_word(state.node) {
                    let freq = self.freq(state.node);
                    let word_str = String::from_utf16_lossy(state.accum_slice());
                    let is_full_exact = state.cost == 0.0 && state.accum_len() == typed.len();
                    let is_contraction_exact = (state.cost - 0.01).abs() < 1e-4 && state.accum_len() == typed.len() + 1;
                    let extra_len = (state.accum_len() as f32 - typed.len() as f32).max(0.0);

                    let target_first_ch = search_input.chars().next();
                    let target_second_ch = search_input.chars().nth(1);
                    let is_first_mismatch = match (target_first_ch, word_str.chars().next()) {
                        (Some(fc), Some(ac)) if fc == ac || prox.is_case_pair(fc, ac) => false,
                        (Some(fc), Some(ac)) => {
                            if let (Some(sc), Some(a_sc)) = (target_second_ch, word_str.chars().nth(1)) {
                                !((ac == sc || prox.is_case_pair(sc, ac)) && (a_sc == fc || prox.is_case_pair(fc, a_sc)))
                            } else {
                                true
                            }
                        }
                        _ => false,
                    };

                    // Frequency-first scoring:
                    // For exact matches (cost == 0), frequency dominates completely.
                    // Full exact match gets a bonus so exact typed word stays on top.
                    // Exact contractions (e.g. he'll for hell, we'll for well) also get high bonus.
                    // Raw mapping case pairs (lower vs upper) have minimal penalty (0.10).
                    // Unrelated first-character physical slips receive heavy penalty (-75.0).
                    let score = (freq as f32)
                        + if is_full_exact {
                            50.0
                        } else if is_contraction_exact {
                            48.0
                        } else {
                            0.0
                        }
                        - (state.cost * 80.0)
                        - if is_first_mismatch { 75.0 } else { 0.0 }
                        - (0.001 * extra_len);

                    let entry = candidates.entry(word_str).or_insert((f32::NEG_INFINITY, 0));
                    if score > entry.0 {
                        *entry = (score, freq);
                    }
                }

                // Prefix completions (descend deeper)
                // For exact matches and contractions, descend up to 10 extra characters.
                // For typo corrections, only allow at most 1 extra character (e.g. plural 's') to avoid explosive branching.
                let max_extra = if state.cost <= 0.01 { 10 } else { 1 };
                if state.accum_len() < typed.len() + max_extra {
                    let start = self.child_start(state.node as usize);
                    let count = self.child_count(state.node as usize);
                    for e in start..start + count {
                        let child_node = Self::edge_child(e);
                        let label = self.edge_label(e);
                        if let Some((next_accum, next_len)) = state.with_pushed(label) {
                            let child_mf = self.max_subtree_freq(child_node) as f32;
                            let child_cost = state.cost;
                            let extra = (next_len as f32 - typed.len() as f32).max(0.0);
                            let child_bound = child_mf - (child_cost * 80.0) - (0.001 * extra);
                            heap.push(State {
                                node: child_node,
                                typed_pos: state.typed_pos,
                                accum: next_accum,
                                accum_len: next_len,
                                cost: child_cost,
                                bound: child_bound,
                            });
                        }
                    }
                }
                continue;
            }

            let target = typed[state.typed_pos];

            // 1. Exact match (case-insensitive across full word)
            let (exact_targets, exact_len) = Self::case_variants_u16(target);

            for &t_unit in &exact_targets[..exact_len] {
                if let Some(child_node) = self.child(state.node, t_unit) {
                    if let Some((next_accum, next_len)) = state.with_pushed(t_unit) {
                        let child_mf = self.max_subtree_freq(child_node) as f32;
                        let extra = (next_len as f32 - (state.typed_pos + 1) as f32).max(0.0);
                        let is_exact = state.cost == 0.0;
                        let bound = child_mf
                            + if is_exact { 50.0 } else { 0.0 }
                            - (state.cost * 80.0)
                            - (0.001 * extra);
                        heap.push(State {
                            node: child_node,
                            typed_pos: state.typed_pos + 1,
                            accum: next_accum,
                            accum_len: next_len,
                            cost: state.cost,
                            bound,
                        });
                    }
                }
            }

            // 1b. Contraction apostrophe bypass (e.g. user typed 'hell' for 'he'll', 'well' for 'we'll', 'cant' for 'can't')
            if state.accum_len > 0 {
                const APOSTROPHES: [u16; 2] = [b'\'' as u16, 0x2019];
                for &ap in &APOSTROPHES {
                    if let Some(ap_node) = self.child(state.node, ap) {
                        if let Some(child_node) = self.child(ap_node, target) {
                            if let Some((next_accum, next_len)) = state.with_pushed2(ap, target) {
                                let child_mf = self.max_subtree_freq(child_node) as f32;
                                let extra = (next_len as f32 - (state.typed_pos + 1) as f32).max(0.0);
                                let is_exact = state.cost == 0.0;
                                let child_cost = state.cost + 0.01;
                                let bound = child_mf
                                    + if is_exact { 48.0 } else { 0.0 }
                                    - (child_cost * 80.0)
                                    - (0.001 * extra);
                                heap.push(State {
                                    node: child_node,
                                    typed_pos: state.typed_pos + 1,
                                    accum: next_accum,
                                    accum_len: next_len,
                                    cost: child_cost,
                                    bound,
                                });
                            }
                        }
                    }
                }
            }

            // 2. Proximity substitution
            if let Some(target_ch) = char::from_u32(target as u32) {
                for &neighbor in prox.get_neighbors(target_ch) {
                    let n_u16 = neighbor as u16;
                    if n_u16 != target {
                        if let Some(child_node) = self.child(state.node, n_u16) {
                            let is_case = prox.is_case_pair(target_ch, neighbor);
                            let sub_cost = if is_case { 0.10 } else { 0.40 };
                            if state.cost + sub_cost <= max_cost {
                                if let Some((next_accum, next_len)) = state.with_pushed(n_u16) {
                                    let child_mf = self.max_subtree_freq(child_node) as f32;
                                    let child_cost = state.cost + sub_cost;
                                    heap.push(State {
                                        node: child_node,
                                        typed_pos: state.typed_pos + 1,
                                        accum: next_accum,
                                        accum_len: next_len,
                                        cost: child_cost,
                                        bound: child_mf - (child_cost * 80.0),
                                    });
                                }
                            }
                        }
                    }
                }
            }

            // 3. Transposition (adjacent swap: 'teh' -> 'the')
            if state.typed_pos + 1 < typed.len() && state.cost + 0.40 <= max_cost {
                let next_target = typed[state.typed_pos + 1];
                if let Some(c1) = self.child(state.node, next_target) {
                    if let Some(c2) = self.child(c1, target) {
                        if let Some((next_accum, next_len)) = state.with_pushed2(next_target, target) {
                            let child_mf = self.max_subtree_freq(c2) as f32;
                            let child_cost = state.cost + 0.40;
                            heap.push(State {
                                node: c2,
                                typed_pos: state.typed_pos + 2,
                                accum: next_accum,
                                accum_len: next_len,
                                cost: child_cost,
                                bound: child_mf - (child_cost * 80.0),
                            });
                        }
                    }
                }
            }

            // 4. Repeated letter in dictionary word omitted by user ('helo' -> 'hello', 'gelo' -> 'hello')
            if let Some(&last_unit) = state.accum_slice().last() {
                if state.cost + 0.15 <= max_cost {
                    if let Some(repeat_node) = self.child(state.node, last_unit) {
                        if let Some((next_accum, next_len)) = state.with_pushed(last_unit) {
                            let child_mf = self.max_subtree_freq(repeat_node) as f32;
                            let child_cost = state.cost + 0.15;
                            heap.push(State {
                                node: repeat_node,
                                typed_pos: state.typed_pos,
                                accum: next_accum,
                                accum_len: next_len,
                                cost: child_cost,
                                bound: child_mf - (child_cost * 80.0),
                            });
                        }
                    }
                }
            }

            // 5. Letter omission / deletion in typed input ('আমদের' -> 'আমাদের')
            if state.cost == 0.0 && state.accum_len > 0 && state.cost + 0.60 <= max_cost {
                let start = self.child_start(state.node as usize);
                let count = self.child_count(state.node as usize);
                for e in start..start + count {
                    let child_node = Self::edge_child(e);
                    let label = self.edge_label(e);
                    if label != target && label != b'\'' as u16 && label != 0x2019 {
                        // Check if grandchild matches current target
                        if let Some(grandchild) = self.child(child_node, target) {
                            if let Some((next_accum, next_len)) = state.with_pushed2(label, target) {
                                let child_mf = self.max_subtree_freq(grandchild) as f32;
                                let child_cost = state.cost + 0.60;
                                heap.push(State {
                                    node: grandchild,
                                    typed_pos: state.typed_pos + 1,
                                    accum: next_accum,
                                    accum_len: next_len,
                                    cost: child_cost,
                                    bound: child_mf - (child_cost * 80.0),
                                });
                            }
                        }
                    }
                }
            }

            // 6. Insertion (user typed an extra letter inside word)
            if state.cost == 0.0 && state.accum_len >= 2 && state.cost + 0.65 <= max_cost {
                let child_cost = state.cost + 0.65;
                let bound = state.bound - (0.65 * 80.0);
                heap.push(State {
                    node: state.node,
                    typed_pos: state.typed_pos + 1,
                    accum: state.accum,
                    accum_len: state.accum_len,
                    cost: child_cost,
                    bound,
                });
            }
        }

        let mut ranked: Vec<(String, f32, u8)> = candidates.into_iter().map(|(w, (s, f))| (w, s, f)).collect();
        ranked.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(Ordering::Equal).then_with(|| b.2.cmp(&a.2)));

        let mut final_suggestions = Vec::new();
        for (word, _, _) in ranked.into_iter().take(limit) {
            let formatted = if is_all_upper {
                if is_turkish_context {
                    word.chars()
                        .flat_map(|c| match c {
                            'i' => vec!['İ'],
                            'ı' => vec!['I'],
                            other => other.to_uppercase().collect(),
                        })
                        .collect()
                } else {
                    word.to_uppercase()
                }
            } else if is_title_case {
                let mut c = word.chars();
                match c.next() {
                    None => String::new(),
                    Some(first) => {
                        let first_up: String = if is_turkish_context && first == 'i' {
                            "İ".to_string()
                        } else if is_turkish_context && first == 'ı' {
                            "I".to_string()
                        } else {
                            first.to_uppercase().collect()
                        };
                        first_up + c.as_str()
                    }
                }
            } else {
                word
            };
            final_suggestions.push(formatted);
        }

        final_suggestions
    }

    #[allow(dead_code)]
    pub fn top_idle_words(&self, limit: usize) -> Vec<String> {
        let mut heap: BinaryHeap<(u8, u32, Vec<u16>)> = BinaryHeap::new();
        heap.push((self.max_subtree_freq(0), 0, Vec::new()));
        let mut best_words: Vec<(u8, String)> = Vec::new();
        let mut seen = std::collections::HashSet::new();

        while let Some((mf, node, accum)) = heap.pop() {
            if best_words.len() >= limit {
                let min_freq = best_words.last().map(|(f, _)| *f).unwrap_or(0);
                if mf < min_freq {
                    break;
                }
            }

            if self.is_word(node) && !accum.is_empty() {
                let w = String::from_utf16_lossy(&accum);
                if seen.insert(w.clone()) {
                    let freq = self.freq(node);
                    best_words.push((freq, w));
                    best_words.sort_by(|a, b| b.0.cmp(&a.0).then_with(|| a.1.len().cmp(&b.1.len())));
                    if best_words.len() > limit * 2 {
                        best_words.truncate(limit * 2);
                    }
                }
            }

            let start = self.child_start(node as usize);
            let count = self.child_count(node as usize);
            for e in start..start + count {
                let child = Self::edge_child(e);
                let label = self.edge_label(e);
                let mut next_accum = accum.clone();
                next_accum.push(label);
                let child_mf = self.max_subtree_freq(child);
                heap.push((child_mf, child, next_accum));
            }
        }

        best_words.into_iter().take(limit).map(|(_, w)| w).collect()
    }
}
