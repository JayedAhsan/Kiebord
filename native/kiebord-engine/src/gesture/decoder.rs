use crate::dictionary::binary_dict::BinaryDict;
use crate::translit::traits::PhoneticTransliterator;
use parking_lot::RwLock;
use std::collections::{HashMap, HashSet};
use std::sync::{Arc, OnceLock};

#[derive(Debug, Clone, PartialEq)]
pub struct GesturePoint {
    pub x: f32,
    pub y: f32,
    pub timestamp_ms: i64,
}

#[derive(Debug, Clone, PartialEq)]
pub struct KeyCenter {
    pub char_code: char,
    pub x: f32,
    pub y: f32,
}

#[derive(Debug, Clone, PartialEq)]
pub struct GestureCandidate {
    pub word: String,
    pub score: f32,
}

#[derive(Clone, Copy, Debug)]
pub struct CompactPhoneticEntry {
    pub node: u32,
    pub keys_offset: u32,
    pub keys_len: u16,
    pub freq: u8,
}

#[inline]
pub fn char_to_bucket_idx(c: char) -> Option<usize> {
    if c.is_ascii_lowercase() {
        Some((c as u8 - b'a') as usize)
    } else if c.is_ascii_uppercase() {
        Some((c.to_ascii_lowercase() as u8 - b'a') as usize)
    } else {
        None
    }
}

pub struct PhoneticIndex {
    pub bucket_ranges: [(u32, u32); 676],
    pub entries: Vec<CompactPhoneticEntry>,
    pub keys_pool: Vec<u8>,
}

impl PhoneticIndex {
    pub fn build(dict: &BinaryDict, transliterator: &dyn PhoneticTransliterator) -> Self {
        struct TempEntry {
            node: u32,
            freq: u8,
            keys: Vec<u8>,
        }

        let mut temp_buckets: Vec<Vec<TempEntry>> = (0..676).map(|_| Vec::new()).collect();

        dict.for_each_word(|node, freq, word_utf16| {
            let word = String::from_utf16_lossy(word_utf16);
            let mut variants = transliterator.to_phonetic_keys(&word);
            variants.sort();
            variants.dedup();
            for v in variants {
                if v.len() >= 2 {
                    let first = v[0];
                    let last = *v.last().unwrap();
                    if let (Some(s_idx), Some(e_idx)) = (char_to_bucket_idx(first), char_to_bucket_idx(last)) {
                        let bucket_idx = s_idx * 26 + e_idx;
                        let keys_bytes: Vec<u8> = v.into_iter().map(|c| c.to_ascii_lowercase() as u8).collect();
                        temp_buckets[bucket_idx].push(TempEntry {
                            node,
                            freq,
                            keys: keys_bytes,
                        });
                    }
                }
            }
        });

        // Deduplicate per bucket and sort by frequency descending (tie-broken by node ascending)
        for bucket in &mut temp_buckets {
            bucket.sort_by(|a, b| a.node.cmp(&b.node).then_with(|| a.keys.cmp(&b.keys)));
            bucket.dedup_by(|a, b| a.node == b.node && a.keys == b.keys);
            bucket.sort_by(|a, b| b.freq.cmp(&a.freq).then_with(|| a.node.cmp(&b.node)));
        }

        let total_entries: usize = temp_buckets.iter().map(|b| b.len()).sum();
        let total_keys: usize = temp_buckets.iter().map(|b| b.iter().map(|e| e.keys.len()).sum::<usize>()).sum();

        let mut entries = Vec::with_capacity(total_entries);
        let mut keys_pool = Vec::with_capacity(total_keys);
        let mut bucket_ranges = [(0u32, 0u32); 676];

        for (idx, bucket) in temp_buckets.into_iter().enumerate() {
            let start = entries.len() as u32;
            let count = bucket.len() as u32;
            bucket_ranges[idx] = (start, count);

            for item in bucket {
                let keys_offset = keys_pool.len() as u32;
                let keys_len = item.keys.len() as u16;
                keys_pool.extend_from_slice(&item.keys);
                entries.push(CompactPhoneticEntry {
                    node: item.node,
                    keys_offset,
                    keys_len,
                    freq: item.freq,
                });
            }
        }

        PhoneticIndex {
            bucket_ranges,
            entries,
            keys_pool,
        }
    }

    #[inline]
    pub fn get_bucket(&self, start_char: char, end_char: char) -> &[CompactPhoneticEntry] {
        let s_idx = match char_to_bucket_idx(start_char) {
            Some(i) => i,
            None => return &[],
        };
        let e_idx = match char_to_bucket_idx(end_char) {
            Some(i) => i,
            None => return &[],
        };
        let bucket_idx = s_idx * 26 + e_idx;
        let (start, count) = self.bucket_ranges[bucket_idx];
        if count == 0 {
            &[]
        } else {
            let start = start as usize;
            let end = start + count as usize;
            &self.entries[start..end]
        }
    }

    #[inline]
    pub fn get_keys<'a>(&'a self, entry: &CompactPhoneticEntry) -> &'a [u8] {
        let start = entry.keys_offset as usize;
        let end = start + entry.keys_len as usize;
        &self.keys_pool[start..end]
    }
}

static PHONETIC_CACHE: OnceLock<RwLock<HashMap<String, Arc<PhoneticIndex>>>> = OnceLock::new();

pub fn get_or_build_phonetic_index(
    dict: &BinaryDict,
    transliterator: &dyn PhoneticTransliterator,
) -> Arc<PhoneticIndex> {
    let cache_lock = PHONETIC_CACHE.get_or_init(|| RwLock::new(HashMap::new()));
    // Share a single PhoneticIndex across Bengali phonetic layouts using identical key mappings
    let id = match transliterator.id() {
        "bn_borno" | "bn_avro" => "bn_phonetic",
        other => other,
    };

    {
        let r = cache_lock.read();
        if let Some(idx) = r.get(id) {
            return Arc::clone(idx);
        }
    }

    let mut w = cache_lock.write();
    if let Some(idx) = w.get(id) {
        return Arc::clone(idx);
    }

    let index = Arc::new(PhoneticIndex::build(dict, transliterator));
    w.insert(id.to_string(), Arc::clone(&index));
    index
}

pub struct GestureDecoder;

#[inline]
fn char_lower(c: char) -> char {
    match c {
        'İ' => 'i',
        'I' => 'i',
        'Ğ' => 'ğ',
        'Ü' => 'ü',
        'Ş' => 'ş',
        'Ö' => 'ö',
        'Ç' => 'ç',
        'Â' => 'â',
        'Î' => 'î',
        'Û' => 'û',
        other => other.to_ascii_lowercase(),
    }
}

#[inline]
fn base_latin_fallback(c: char) -> Option<char> {
    match char_lower(c) {
        'ğ' => Some('g'),
        'ü' => Some('u'),
        'ş' => Some('s'),
        'ı' => Some('i'),
        'ö' => Some('o'),
        'ç' => Some('c'),
        'â' => Some('a'),
        'î' => Some('i'),
        'û' => Some('u'),
        _ => None,
    }
}

#[inline]
fn lookup_key_pos(key_map: &HashMap<char, (f32, f32)>, c: char) -> Option<(f32, f32)> {
    let lower = char_lower(c);
    if let Some(&pos) = key_map.get(&lower) {
        return Some(pos);
    }
    if let Some(base) = base_latin_fallback(lower) {
        if let Some(&pos) = key_map.get(&base) {
            return Some(pos);
        }
    }
    None
}

#[inline]
fn in_key_set(set: &HashSet<char>, key_map: &HashMap<char, (f32, f32)>, c: char) -> bool {
    let lower = char_lower(c);
    if key_map.contains_key(&lower) {
        return set.contains(&lower);
    }
    if let Some(base) = base_latin_fallback(lower) {
        return set.contains(&base);
    }
    false
}

impl GestureDecoder {
    /// Resamples an arbitrary length touch trajectory polyline into exactly `target_count` equidistant points
    pub fn resample_polyline(points: &[GesturePoint], target_count: usize) -> Vec<GesturePoint> {
        let mut resampled = Vec::with_capacity(target_count.max(1));
        Self::resample_polyline_into(points, target_count, &mut resampled);
        resampled
    }

    /// Resamples `points` into `resampled` buffer in-place to avoid per-candidate heap allocations.
    #[inline]
    fn resample_polyline_into(
        points: &[GesturePoint],
        target_count: usize,
        resampled: &mut Vec<GesturePoint>,
    ) {
        resampled.clear();
        if points.is_empty() {
            return;
        }
        if points.len() == 1 || target_count <= 1 {
            let count = target_count.max(1);
            resampled.resize(count, points[0].clone());
            return;
        }

        let mut total_length = 0.0f32;
        for i in 1..points.len() {
            let dx = points[i].x - points[i - 1].x;
            let dy = points[i].y - points[i - 1].y;
            total_length += (dx * dx + dy * dy).sqrt();
        }

        if total_length == 0.0 {
            resampled.resize(target_count, points[0].clone());
            return;
        }

        let interval = total_length / (target_count - 1) as f32;
        resampled.push(points[0].clone());

        let mut accumulated = 0.0f32;
        let mut current_pt = points[0].clone();
        let mut i = 1;

        while i < points.len() && resampled.len() < target_count {
            let dx = points[i].x - current_pt.x;
            let dy = points[i].y - current_pt.y;
            let dist = (dx * dx + dy * dy).sqrt();

            if accumulated + dist >= interval {
                let t = (interval - accumulated) / dist;
                let next_pt = GesturePoint {
                    x: current_pt.x + t * dx,
                    y: current_pt.y + t * dy,
                    timestamp_ms: current_pt.timestamp_ms
                        + (t * (points[i].timestamp_ms - current_pt.timestamp_ms) as f32) as i64,
                };
                resampled.push(next_pt.clone());
                current_pt = next_pt;
                accumulated = 0.0;
            } else {
                accumulated += dist;
                current_pt = points[i].clone();
                i += 1;
            }
        }

        if let Some(last) = points.last() {
            while resampled.len() < target_count {
                resampled.push(last.clone());
            }
        }
    }

    /// Decodes a touch trajectory against keyboard key centroids and binary dictionary.
    pub fn decode(
        points: &[GesturePoint],
        keys: &[KeyCenter],
        key_width: f32,
        dict: &BinaryDict,
        limit: usize,
    ) -> Vec<GestureCandidate> {
        Self::decode_with_learned(points, keys, key_width, dict, limit, &[])
    }

    /// Decodes a touch trajectory against keyboard key centroids, binary dictionary, and personalized learned words.
    pub fn decode_with_learned(
        points: &[GesturePoint],
        keys: &[KeyCenter],
        key_width: f32,
        dict: &BinaryDict,
        limit: usize,
        learned_words: &[String],
    ) -> Vec<GestureCandidate> {
        if points.len() < 2 || keys.is_empty() || key_width <= 0.0 {
            return Vec::new();
        }

        let kw = key_width.max(10.0);
        let sample_count = 30;
        let user_path = Self::resample_polyline(points, sample_count);
        let start_pt = &user_path[0];
        let end_pt = &user_path[sample_count - 1];

        let mut key_map: HashMap<char, (f32, f32)> = HashMap::with_capacity(keys.len());
        let mut valid_keys: HashSet<char> = HashSet::with_capacity(keys.len());
        for k in keys {
            let lower = char_lower(k.char_code);
            key_map.insert(lower, (k.x, k.y));
            let mut min_d = f32::MAX;
            for u_pt in &user_path {
                let dx = u_pt.x - k.x;
                let dy = u_pt.y - k.y;
                let d = (dx * dx + dy * dy).sqrt();
                if d < min_d {
                    min_d = d;
                }
            }
            if min_d / kw <= 1.85 {
                valid_keys.insert(lower);
            }
        }

        let start_radius = 2.4 * kw;
        let end_radius = 2.4 * kw;

        let start_keys: Vec<char> = keys
            .iter()
            .filter(|k| {
                let dx = k.x - start_pt.x;
                let dy = k.y - start_pt.y;
                dx * dx + dy * dy <= start_radius * start_radius
            })
            .map(|k| char_lower(k.char_code))
            .filter(|c| valid_keys.contains(c))
            .collect();

        let end_keys: HashSet<char> = keys
            .iter()
            .filter(|k| {
                let dx = k.x - end_pt.x;
                let dy = k.y - end_pt.y;
                dx * dx + dy * dy <= end_radius * end_radius
            })
            .map(|k| char_lower(k.char_code))
            .filter(|c| valid_keys.contains(c))
            .collect();

        if start_keys.is_empty() || end_keys.is_empty() {
            return Vec::new();
        }

        let mut candidates: Vec<GestureCandidate> = Vec::new();
        let mut word_buffer: Vec<u16> = Vec::with_capacity(28);
        let mut ideal_pts_buf: Vec<GesturePoint> = Vec::with_capacity(28);
        let mut resampled_buf: Vec<GesturePoint> = Vec::with_capacity(sample_count);

        for &start_ch in &start_keys {
            let mut start_targets = [0u16; 6];
            let mut target_len = 0usize;
            start_targets[target_len] = start_ch as u16;
            target_len += 1;

            if start_ch.is_ascii_lowercase() {
                start_targets[target_len] = start_ch.to_ascii_uppercase() as u16;
                target_len += 1;
                if start_ch == 'i' {
                    start_targets[target_len] = 'İ' as u16;
                    target_len += 1;
                }
                // On Compact Turkish Q (10-key), diacritic keys are not separate centroids,
                // so allow base keys to also seed words starting with their Turkish diacritics.
                let compact_diacritic = match start_ch {
                    'c' => Some(('ç', 'Ç')),
                    'g' => Some(('ğ', 'Ğ')),
                    'i' => Some(('ı', 'I')),
                    'o' => Some(('ö', 'Ö')),
                    's' => Some(('ş', 'Ş')),
                    'u' => Some(('ü', 'Ü')),
                    _ => None,
                };
                if let Some((d_low, d_up)) = compact_diacritic {
                    if !key_map.contains_key(&d_low) {
                        start_targets[target_len] = d_low as u16;
                        target_len += 1;
                        start_targets[target_len] = d_up as u16;
                        target_len += 1;
                    }
                }
            } else {
                let up_opt = match start_ch {
                    'ı' => Some('I'),
                    'ğ' => Some('Ğ'),
                    'ü' => Some('Ü'),
                    'ş' => Some('Ş'),
                    'ö' => Some('Ö'),
                    'ç' => Some('Ç'),
                    _ => None,
                };
                if let Some(up_ch) = up_opt {
                    start_targets[target_len] = up_ch as u16;
                    target_len += 1;
                }
            }
            for &st in &start_targets[..target_len] {
                if let Some(start_node) = dict.child(0, st) {
                    word_buffer.clear();
                    word_buffer.push(st);
                    Self::collect_words(
                        dict,
                        start_node,
                        &mut word_buffer,
                        &end_keys,
                        &valid_keys,
                        &key_map,
                        &user_path,
                        kw,
                        &mut ideal_pts_buf,
                        &mut resampled_buf,
                        &mut candidates,
                    );
                }
            }
        }

        // 1. Sort dictionary candidates by score (prioritize dictionary words first)
        let mut dict_candidates = candidates;
        let learned_set: HashSet<&String> = learned_words.iter().collect();
        for dc in &mut dict_candidates {
            if learned_set.contains(&dc.word) {
                dc.score += 3.0; // Modest bonus for user-preferred gesture words
            }
        }
        dict_candidates.sort_by(|a, b| b.score.partial_cmp(&a.score).unwrap_or(std::cmp::Ordering::Equal));

        // 2. Strictly evaluate learned words: all characters in the learned word must be traversed by the gesture path
        let dict_word_set: HashSet<String> = dict_candidates.iter().map(|c| c.word.clone()).collect();
        let mut learned_matches: Vec<GestureCandidate> = Vec::new();

        for lw in learned_words {
            if dict_word_set.contains(lw) || lw.chars().any(|c| ('\u{0980}'..='\u{09FF}').contains(&c)) {
                continue;
            }
            let chars: Vec<char> = lw.chars().collect();
            if let Some(score) = Self::evaluate_learned_candidate(&chars, &key_map, &user_path, kw) {
                learned_matches.push(GestureCandidate {
                    word: lw.clone(),
                    score,
                });
            }
        }

        learned_matches.sort_by(|a, b| b.score.partial_cmp(&a.score).unwrap_or(std::cmp::Ordering::Equal));

        // 3. Blend: prioritize top dictionary word, only include learned word if close enough!
        let mut results: Vec<GestureCandidate> = Vec::with_capacity(limit);
        if let Some(top_dict) = dict_candidates.first() {
            if let Some(top_learned) = learned_matches.first() {
                if top_learned.score >= top_dict.score + 1.0 {
                    results.push(top_learned.clone());
                    results.push(top_dict.clone());
                } else {
                    results.push(top_dict.clone());
                    // Only include if genuinely close enough to predict
                    if top_learned.score >= top_dict.score - 5.0 {
                        results.push(top_learned.clone());
                    }
                }
            } else {
                results.push(top_dict.clone());
            }

            for dc in dict_candidates.iter().skip(1) {
                if results.len() >= limit {
                    break;
                }
                if !results.iter().any(|r| r.word == dc.word) {
                    results.push(dc.clone());
                }
            }
        } else {
            for lm in learned_matches {
                if results.len() >= limit {
                    break;
                }
                results.push(lm);
            }
        }

        results.truncate(limit);
        results
    }

    /// Windowed Dynamic Time Warping distance between two sampled polyline sequences.
    pub fn dtw_distance(seq_a: &[GesturePoint], seq_b: &[GesturePoint], window: usize) -> f32 {
        let n = seq_a.len();
        let m = seq_b.len();
        if n == 0 || m == 0 {
            return f32::MAX;
        }

        const MAX_LEN: usize = 64;
        let mut prev = [f32::INFINITY; MAX_LEN];
        let mut curr = [f32::INFINITY; MAX_LEN];

        prev[0] = 0.0;

        for i in 1..=n.min(MAX_LEN - 1) {
            let pt_a = &seq_a[i - 1];
            curr[0] = f32::INFINITY;

            let j_start = if i > window { i - window } else { 1 };
            let j_end = (i + window).min(m.min(MAX_LEN - 1));

            for j in 1..j_start {
                curr[j] = f32::INFINITY;
            }

            for j in j_start..=j_end {
                let pt_b = &seq_b[j - 1];
                let dx = pt_a.x - pt_b.x;
                let dy = pt_a.y - pt_b.y;
                let dist = (dx * dx + dy * dy).sqrt();

                let min_prev = prev[j].min(curr[j - 1]).min(prev[j - 1]);
                curr[j] = dist + min_prev;
            }

            for j in (j_end + 1)..=m.min(MAX_LEN - 1) {
                curr[j] = f32::INFINITY;
            }

            prev = curr;
        }

        let target_m = m.min(MAX_LEN - 1);
        let total = prev[target_m];
        if total.is_finite() {
            total / (n.max(m) as f32)
        } else {
            let mut sum = 0.0;
            let count = n.min(m);
            for k in 0..count {
                let dx = seq_a[k].x - seq_b[k].x;
                let dy = seq_a[k].y - seq_b[k].y;
                sum += (dx * dx + dy * dy).sqrt();
            }
            sum / count.max(1) as f32
        }
    }

    /// Evaluates a personalized learned word strictly.
    /// Requires that EVERY key in the learned word was actually traversed along the user's gesture path
    /// in temporal sequence, with endpoints closely anchored, path lengths proportional, and DTW distance small.
    pub fn evaluate_learned_candidate(
        ideal_chars: &[char],
        key_map: &HashMap<char, (f32, f32)>,
        user_path: &[GesturePoint],
        kw: f32,
    ) -> Option<f32> {
        if ideal_chars.len() < 2 || user_path.len() < 2 {
            return None;
        }

        let mut ideal_pts = Vec::with_capacity(ideal_chars.len());
        for &ch in ideal_chars {
            if ch == '\'' || ch == '\u{2019}' {
                continue;
            }
            if let Some(pos) = lookup_key_pos(key_map, ch) {
                ideal_pts.push(GesturePoint {
                    x: pos.0,
                    y: pos.1,
                    timestamp_ms: 0,
                });
            } else {
                return None;
            }
        }

        // Endpoint proximity: both start and end keys must be anchored within reason
        let d_start = {
            let dx = user_path[0].x - ideal_pts[0].x;
            let dy = user_path[0].y - ideal_pts[0].y;
            (dx * dx + dy * dy).sqrt() / kw
        };
        let d_end = {
            let dx = user_path.last().unwrap().x - ideal_pts.last().unwrap().x;
            let dy = user_path.last().unwrap().y - ideal_pts.last().unwrap().y;
            (dx * dx + dy * dy).sqrt() / kw
        };

        if d_start > 1.35 || d_end > 1.35 {
            return None;
        }

        // Path arc length check: prevents short learned words from matching long gestures (and vice versa)
        let mut user_len = 0.0f32;
        for i in 1..user_path.len() {
            let dx = user_path[i].x - user_path[i - 1].x;
            let dy = user_path[i].y - user_path[i - 1].y;
            user_len += (dx * dx + dy * dy).sqrt();
        }
        let mut ideal_len = 0.0f32;
        for i in 1..ideal_pts.len() {
            let dx = ideal_pts[i].x - ideal_pts[i - 1].x;
            let dy = ideal_pts[i].y - ideal_pts[i - 1].y;
            ideal_len += (dx * dx + dy * dy).sqrt();
        }

        if user_len > 1.8 * ideal_len + 1.5 * kw || ideal_len > 2.0 * user_len + 1.5 * kw {
            return None;
        }

        // Sequential traversal: every key must be approached along trajectory
        let mut last_point_idx: usize = 0;
        let mut total_key_miss = 0.0f32;
        let mut max_key_miss = 0.0f32;

        for k_pt in &ideal_pts {
            let mut min_d = f32::MAX;
            let mut best_u_idx = 0;

            for (u_idx, u_pt) in user_path.iter().enumerate() {
                let dx = u_pt.x - k_pt.x;
                let dy = u_pt.y - k_pt.y;
                let d = (dx * dx + dy * dy).sqrt();
                if d < min_d {
                    min_d = d;
                    best_u_idx = u_idx;
                }
            }

            let norm_d = min_d / kw;
            // Every character must be passed within 1.35 key width
            if norm_d > 1.35 {
                return None;
            }
            if norm_d > max_key_miss {
                max_key_miss = norm_d;
            }
            total_key_miss += norm_d;

            // Sequential progression: gesture must progress forward through word characters
            if best_u_idx < last_point_idx.saturating_sub(2) {
                return None;
            }
            if best_u_idx > last_point_idx {
                last_point_idx = best_u_idx;
            }
        }

        let avg_key_miss = total_key_miss / ideal_pts.len() as f32;
        if avg_key_miss > 1.15 {
            return None;
        }

        // Dynamic Time Warping (DTW) check
        let resampled_ideal = Self::resample_polyline(&ideal_pts, user_path.len());
        let dtw_dist = Self::dtw_distance(user_path, &resampled_ideal, 6) / kw;
        if dtw_dist > 1.35 {
            return None;
        }

        let miss_penalty = if max_key_miss > 1.0 {
            (max_key_miss - 1.0) * 2.0
        } else {
            0.0
        };

        let total_cost = dtw_dist + 0.5 * d_start + 0.5 * d_end + 0.4 * avg_key_miss + miss_penalty;
        if total_cost > 2.0 {
            return None;
        }

        let shape_score = (-total_cost * 1.5).exp();
        let score = 100.0 * shape_score + 10.0;
        Some(score)
    }

    /// Evaluates shape match and scoring of an ideal key trajectory against the user's touch trajectory.
    /// Uses windowed Dynamic Time Warping (DTW), key-visit proximity verification, and log-scaled frequency.
    #[inline]
    pub fn evaluate_ideal_points(
        ideal_pts: &[GesturePoint],
        user_path: &[GesturePoint],
        kw: f32,
        freq: u8,
    ) -> Option<f32> {
        // Start and end anchor distances (normalized by key width)
        let d_start = {
            let dx = user_path[0].x - ideal_pts[0].x;
            let dy = user_path[0].y - ideal_pts[0].y;
            (dx * dx + dy * dy).sqrt() / kw
        };
        let d_end = {
            let dx = user_path.last().unwrap().x - ideal_pts.last().unwrap().x;
            let dy = user_path.last().unwrap().y - ideal_pts.last().unwrap().y;
            (dx * dx + dy * dy).sqrt() / kw
        };

        // If start or end key is too far from gesture endpoints, reject
        if d_start > 2.4 || d_end > 2.4 {
            return None;
        }

        // Key-visit verification: every key in the candidate must be approached by the trajectory
        let mut max_key_miss = 0.0f32;
        let mut total_key_miss = 0.0f32;
        for k_pt in ideal_pts {
            let mut min_d = f32::MAX;
            for u_pt in user_path {
                let dx = u_pt.x - k_pt.x;
                let dy = u_pt.y - k_pt.y;
                let d = (dx * dx + dy * dy).sqrt();
                if d < min_d {
                    min_d = d;
                }
            }
            let norm_d = min_d / kw;
            if norm_d > max_key_miss {
                max_key_miss = norm_d;
            }
            total_key_miss += norm_d;
        }

        // Candidates containing keys never visited (e.g. > 1.85 key widths away) are eliminated
        if max_key_miss > 1.85 {
            return None;
        }

        let avg_key_miss = total_key_miss / ideal_pts.len() as f32;

        // Dynamic Time Warping (DTW) with Sakoe-Chiba band window
        let resampled_ideal = Self::resample_polyline(ideal_pts, user_path.len());
        let dtw_dist = Self::dtw_distance(user_path, &resampled_ideal, 6) / kw;

        // Key miss penalty for keys that were relatively far (> 1.2 key width)
        let miss_penalty = if max_key_miss > 1.2 {
            (max_key_miss - 1.2) * 2.0
        } else {
            0.0
        };

        let total_cost = dtw_dist + 0.5 * d_start + 0.5 * d_end + 0.4 * avg_key_miss + miss_penalty;

        if total_cost > 3.2 {
            return None;
        }

        // Shape score dominates (0 to 100), log-scaled frequency acts as tie-breaker (0 to 15)
        let shape_score = (-total_cost * 1.5).exp();
        let freq_score = (1.0 + freq as f32).ln() / (256.0f32).ln();
        let score = 100.0 * shape_score + 15.0 * freq_score;

        Some(score)
    }

    #[inline]
    fn evaluate_ideal_points_with_buf(
        ideal_pts: &[GesturePoint],
        user_path: &[GesturePoint],
        kw: f32,
        freq: u8,
        max_key_miss: f32,
        total_key_miss: f32,
        resampled_buf: &mut Vec<GesturePoint>,
    ) -> Option<f32> {
        let d_start = {
            let dx = user_path[0].x - ideal_pts[0].x;
            let dy = user_path[0].y - ideal_pts[0].y;
            (dx * dx + dy * dy).sqrt() / kw
        };
        let d_end = {
            let dx = user_path.last().unwrap().x - ideal_pts.last().unwrap().x;
            let dy = user_path.last().unwrap().y - ideal_pts.last().unwrap().y;
            (dx * dx + dy * dy).sqrt() / kw
        };

        if d_start > 2.4 || d_end > 2.4 {
            return None;
        }

        if max_key_miss > 1.85 {
            return None;
        }

        let avg_key_miss = total_key_miss / ideal_pts.len() as f32;

        Self::resample_polyline_into(ideal_pts, user_path.len(), resampled_buf);
        let dtw_dist = Self::dtw_distance(user_path, resampled_buf, 6) / kw;

        let miss_penalty = if max_key_miss > 1.2 {
            (max_key_miss - 1.2) * 2.0
        } else {
            0.0
        };

        let total_cost = dtw_dist + 0.5 * d_start + 0.5 * d_end + 0.4 * avg_key_miss + miss_penalty;

        if total_cost > 3.2 {
            return None;
        }

        let shape_score = (-total_cost * 1.5).exp();
        let freq_score = (1.0 + freq as f32).ln() / (256.0f32).ln();
        let score = 100.0 * shape_score + 15.0 * freq_score;

        Some(score)
    }

    /// Evaluates shape match and scoring of a candidate word path against the user's touch trajectory.
    /// Uses windowed Dynamic Time Warping (DTW), key-visit proximity verification, and log-scaled frequency.
    pub fn evaluate_candidate(
        ideal_chars: &[char],
        key_map: &HashMap<char, (f32, f32)>,
        user_path: &[GesturePoint],
        kw: f32,
        freq: u8,
    ) -> Option<f32> {
        if ideal_chars.len() < 2 || user_path.len() < 2 {
            return None;
        }

        let mut ideal_pts = Vec::with_capacity(ideal_chars.len());
        for &ch in ideal_chars {
            if ch == '\'' || ch == '\u{2019}' {
                continue;
            }
            if let Some(pos) = lookup_key_pos(key_map, ch) {
                ideal_pts.push(GesturePoint {
                    x: pos.0,
                    y: pos.1,
                    timestamp_ms: 0,
                });
            } else {
                return None;
            }
        }

        Self::evaluate_ideal_points(&ideal_pts, user_path, kw, freq)
    }

    /// Evaluates candidate key byte sequence against key positions with zero heap allocation and direct array indexing.
    pub fn evaluate_candidate_bytes(
        ideal_bytes: &[u8],
        key_positions: &[(f32, f32); 26],
        key_present: &[bool; 26],
        user_path: &[GesturePoint],
        kw: f32,
        freq: u8,
    ) -> Option<f32> {
        if ideal_bytes.len() < 2 || user_path.len() < 2 {
            return None;
        }

        let mut ideal_pts = Vec::with_capacity(ideal_bytes.len());
        for &b in ideal_bytes {
            let ch = b.to_ascii_lowercase();
            if ch.is_ascii_lowercase() {
                let idx = (ch - b'a') as usize;
                if key_present[idx] {
                    let pos = key_positions[idx];
                    ideal_pts.push(GesturePoint {
                        x: pos.0,
                        y: pos.1,
                        timestamp_ms: 0,
                    });
                    continue;
                }
            }
            return None;
        }

        Self::evaluate_ideal_points(&ideal_pts, user_path, kw, freq)
    }

    #[inline]
    #[allow(clippy::too_many_arguments)]
    fn evaluate_candidate_bytes_fast(
        ideal_bytes: &[u8],
        key_positions: &[(f32, f32); 26],
        key_present: &[bool; 26],
        key_norm_miss: &[f32; 26],
        user_path: &[GesturePoint],
        kw: f32,
        freq: u8,
        ideal_pts_buf: &mut Vec<GesturePoint>,
        resampled_buf: &mut Vec<GesturePoint>,
    ) -> Option<f32> {
        if ideal_bytes.len() < 2 || user_path.len() < 2 {
            return None;
        }

        ideal_pts_buf.clear();
        let mut max_key_miss = 0.0f32;
        let mut total_key_miss = 0.0f32;

        for &b in ideal_bytes {
            let ch = b.to_ascii_lowercase();
            if ch.is_ascii_lowercase() {
                let idx = (ch - b'a') as usize;
                if key_present[idx] {
                    let norm_d = key_norm_miss[idx];
                    if norm_d > max_key_miss {
                        max_key_miss = norm_d;
                    }
                    total_key_miss += norm_d;
                    let pos = key_positions[idx];
                    ideal_pts_buf.push(GesturePoint {
                        x: pos.0,
                        y: pos.1,
                        timestamp_ms: 0,
                    });
                    continue;
                }
            }
            return None;
        }

        if max_key_miss > 1.85 {
            return None;
        }

        Self::evaluate_ideal_points_with_buf(
            ideal_pts_buf,
            user_path,
            kw,
            freq,
            max_key_miss,
            total_key_miss,
            resampled_buf,
        )
    }

    #[allow(clippy::too_many_arguments)]
    fn collect_words(
        dict: &BinaryDict,
        node: u32,
        buffer: &mut Vec<u16>,
        end_keys: &HashSet<char>,
        valid_keys: &HashSet<char>,
        key_map: &HashMap<char, (f32, f32)>,
        user_path: &[GesturePoint],
        kw: f32,
        ideal_pts_buf: &mut Vec<GesturePoint>,
        resampled_buf: &mut Vec<GesturePoint>,
        results: &mut Vec<GestureCandidate>,
    ) {
        if buffer.len() > 24 {
            return;
        }

        if dict.is_word(node) && buffer.len() >= 2 {
            let last_u16 = *buffer.last().unwrap();
            if let Some(last_ch) = char::from_u32(last_u16 as u32) {
                if in_key_set(end_keys, key_map, last_ch) {
                    let freq = dict.freq(node);
                    if freq >= 1 {
                        ideal_pts_buf.clear();
                        let mut all_valid = true;
                        let mut max_key_miss = 0.0f32;
                        let mut total_key_miss = 0.0f32;

                        for &u in buffer.iter() {
                            if let Some(ch) = char::from_u32(u as u32) {
                                if ch == '\'' || ch == '\u{2019}' {
                                    continue;
                                }
                                if let Some(pos) = lookup_key_pos(key_map, ch) {
                                    let mut min_d = f32::MAX;
                                    for u_pt in user_path {
                                        let dx = u_pt.x - pos.0;
                                        let dy = u_pt.y - pos.1;
                                        let d = (dx * dx + dy * dy).sqrt();
                                        if d < min_d {
                                            min_d = d;
                                        }
                                    }
                                    let norm_d = min_d / kw;
                                    if norm_d > max_key_miss {
                                        max_key_miss = norm_d;
                                    }
                                    total_key_miss += norm_d;
                                    ideal_pts_buf.push(GesturePoint {
                                        x: pos.0,
                                        y: pos.1,
                                        timestamp_ms: 0,
                                    });
                                } else {
                                    all_valid = false;
                                    break;
                                }
                            } else {
                                all_valid = false;
                                break;
                            }
                        }

                        if all_valid && !ideal_pts_buf.is_empty() {
                            if let Some(score) = Self::evaluate_ideal_points_with_buf(
                                ideal_pts_buf,
                                user_path,
                                kw,
                                freq,
                                max_key_miss,
                                total_key_miss,
                                resampled_buf,
                            ) {
                                let word_str = String::from_utf16_lossy(buffer);
                                results.push(GestureCandidate {
                                    word: word_str,
                                    score,
                                });
                            }
                        }
                    }
                }
            }
        }

        let start = dict.child_start(node as usize);
        let count = dict.child_count(node as usize);
        for e in start..start + count {
            let label = dict.edge_label(e);
            let ch = match char::from_u32(label as u32) {
                Some(c) => c,
                None => continue,
            };
            if ch != '\'' && ch != '\u{2019}' && !in_key_set(valid_keys, key_map, ch) {
                continue;
            }
            let child_node = (e + 1) as u32;
            buffer.push(label);
            Self::collect_words(
                dict,
                child_node,
                buffer,
                end_keys,
                valid_keys,
                key_map,
                user_path,
                kw,
                ideal_pts_buf,
                resampled_buf,
                results,
            );
            buffer.pop();
        }
    }

    /// Decodes a touch trajectory against keyboard key centroids for a phonetic / transliteration layout.
    /// The keys on screen are Latin QWERTY, but the target vocabulary comes from the target language dictionary.
    pub fn decode_phonetic(
        points: &[GesturePoint],
        keys: &[KeyCenter],
        key_width: f32,
        dict: &BinaryDict,
        transliterator: &dyn PhoneticTransliterator,
        limit: usize,
    ) -> Vec<GestureCandidate> {
        Self::decode_phonetic_with_learned(points, keys, key_width, dict, transliterator, limit, &[])
    }

    /// Decodes a touch trajectory against keyboard key centroids for a phonetic layout, including learned words.
    pub fn decode_phonetic_with_learned(
        points: &[GesturePoint],
        keys: &[KeyCenter],
        key_width: f32,
        dict: &BinaryDict,
        transliterator: &dyn PhoneticTransliterator,
        limit: usize,
        learned_words: &[String],
    ) -> Vec<GestureCandidate> {
        if points.len() < 2 || keys.is_empty() || key_width <= 0.0 {
            return Vec::new();
        }

        let kw = key_width.max(10.0);
        let sample_count = 30;
        let user_path = Self::resample_polyline(points, sample_count);
        let start_pt = &user_path[0];
        let end_pt = &user_path[sample_count - 1];

        let mut key_map: HashMap<char, (f32, f32)> = HashMap::with_capacity(keys.len());
        let mut key_positions = [(0.0f32, 0.0f32); 26];
        let mut key_present = [false; 26];
        let mut key_norm_miss = [f32::MAX; 26];
        for k in keys {
            let lower = k.char_code.to_ascii_lowercase();
            key_map.insert(lower, (k.x, k.y));
            if lower.is_ascii_lowercase() {
                let idx = (lower as u8 - b'a') as usize;
                key_positions[idx] = (k.x, k.y);
                key_present[idx] = true;
                let mut min_d = f32::MAX;
                for u_pt in &user_path {
                    let dx = u_pt.x - k.x;
                    let dy = u_pt.y - k.y;
                    let d = (dx * dx + dy * dy).sqrt();
                    if d < min_d {
                        min_d = d;
                    }
                }
                key_norm_miss[idx] = min_d / kw;
            }
        }

        let start_radius = 2.4 * kw;
        let end_radius = 2.4 * kw;

        let start_keys: Vec<char> = keys
            .iter()
            .filter(|k| {
                let dx = k.x - start_pt.x;
                let dy = k.y - start_pt.y;
                dx * dx + dy * dy <= start_radius * start_radius
            })
            .map(|k| k.char_code.to_ascii_lowercase())
            .collect();

        let end_keys: HashSet<char> = keys
            .iter()
            .filter(|k| {
                let dx = k.x - end_pt.x;
                let dy = k.y - end_pt.y;
                dx * dx + dy * dy <= end_radius * end_radius
            })
            .map(|k| k.char_code.to_ascii_lowercase())
            .collect();

        if start_keys.is_empty() || end_keys.is_empty() {
            return Vec::new();
        }

        let index = get_or_build_phonetic_index(dict, transliterator);

        let mut node_scores: HashMap<u32, f32> = HashMap::new();
        let mut ideal_pts_buf: Vec<GesturePoint> = Vec::with_capacity(28);
        let mut resampled_buf: Vec<GesturePoint> = Vec::with_capacity(sample_count);

        for &s in &start_keys {
            for &e in &end_keys {
                let entries = index.get_bucket(s, e);
                for entry in entries {
                    let keys_bytes = index.get_keys(entry);
                    if let Some(score) = Self::evaluate_candidate_bytes_fast(
                        keys_bytes,
                        &key_positions,
                        &key_present,
                        &key_norm_miss,
                        &user_path,
                        kw,
                        entry.freq,
                        &mut ideal_pts_buf,
                        &mut resampled_buf,
                    ) {
                        let slot = node_scores.entry(entry.node).or_insert(f32::MIN);
                        if score > *slot {
                            *slot = score;
                        }
                    }
                }
            }
        }

        let mut word_scores: HashMap<String, f32> = HashMap::with_capacity(node_scores.len());
        for (node, score) in node_scores {
            if let Some(word) = dict.word_at(node) {
                let norm_word = word.replace("\u{0985}\u{09BE}", "আ");
                let slot = word_scores.entry(norm_word).or_insert(f32::MIN);
                if score > *slot {
                    *slot = score;
                }
            }
        }

        // Modest boost for dictionary words that are in the user's learned words
        let learned_set: HashSet<String> = learned_words
            .iter()
            .map(|w| w.replace("\u{0985}\u{09BE}", "আ"))
            .collect();
        for (w, score) in word_scores.iter_mut() {
            if learned_set.contains(w) {
                *score += 3.0; // Modest bonus so user preference breaks close ties
            }
        }

        let top_dict_score = word_scores.values().copied().fold(f32::MIN, f32::max);

        // Also strictly evaluate custom learned words: they must genuinely match the gesture trajectory!
        for lw in learned_words {
            let variants = transliterator.to_phonetic_keys(lw);
            for v in variants {
                if let Some(score) = Self::evaluate_learned_candidate(
                    &v,
                    &key_map,
                    &user_path,
                    kw,
                ) {
                    // Only accept learned candidate if it's genuinely close to the gesture!
                    // If dictionary candidates exist, a learned word must not lag more than 5.0 points behind top dict score
                    if top_dict_score > f32::MIN && score < top_dict_score - 5.0 {
                        continue;
                    }
                    let norm = lw.replace("\u{0985}\u{09BE}", "আ");
                    let slot = word_scores.entry(norm).or_insert(f32::MIN);
                    if score > *slot {
                        *slot = score;
                    }
                }
            }
        }

        let mut candidates: Vec<GestureCandidate> = word_scores
            .into_iter()
            .map(|(word, score)| GestureCandidate { word, score })
            .filter(|c| !c.word.chars().any(|ch| ch.is_ascii_alphabetic()))
            .collect();

        candidates.sort_by(|a, b| b.score.partial_cmp(&a.score).unwrap_or(std::cmp::Ordering::Equal));
        candidates.truncate(limit);
        candidates
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::dictionary::{get_bn_binary_dict, get_en_binary_dict};
    use crate::translit::borno_phonetic::BornoPhoneticTransliterator;

    #[test]
    fn test_resample_polyline() {
        let pts = vec![
            GesturePoint { x: 0.0, y: 0.0, timestamp_ms: 0 },
            GesturePoint { x: 100.0, y: 0.0, timestamp_ms: 100 },
        ];
        let resampled = GestureDecoder::resample_polyline(&pts, 5);
        assert_eq!(resampled.len(), 5);
        assert!((resampled[0].x - 0.0).abs() < 1e-3);
        assert!((resampled[1].x - 25.0).abs() < 1e-3);
        assert!((resampled[2].x - 50.0).abs() < 1e-3);
        assert!((resampled[3].x - 75.0).abs() < 1e-3);
        assert!((resampled[4].x - 100.0).abs() < 1e-3);
    }

    
#[test]
fn test_decode_jayed_gesture() {
    let dict = get_en_binary_dict().expect("en dict");

    let keys = vec![
        KeyCenter { char_code: 'q', x: 20.0, y: 50.0 },
        KeyCenter { char_code: 'w', x: 60.0, y: 50.0 },
        KeyCenter { char_code: 'e', x: 100.0, y: 50.0 },
        KeyCenter { char_code: 'r', x: 140.0, y: 50.0 },
        KeyCenter { char_code: 't', x: 180.0, y: 50.0 },
        KeyCenter { char_code: 'y', x: 220.0, y: 50.0 },
        KeyCenter { char_code: 'u', x: 260.0, y: 50.0 },
        KeyCenter { char_code: 'i', x: 300.0, y: 50.0 },
        KeyCenter { char_code: 'o', x: 340.0, y: 50.0 },
        KeyCenter { char_code: 'p', x: 380.0, y: 50.0 },
        KeyCenter { char_code: 'a', x: 40.0, y: 120.0 },
        KeyCenter { char_code: 's', x: 80.0, y: 120.0 },
        KeyCenter { char_code: 'd', x: 120.0, y: 120.0 },
        KeyCenter { char_code: 'f', x: 160.0, y: 120.0 },
        KeyCenter { char_code: 'g', x: 200.0, y: 120.0 },
        KeyCenter { char_code: 'h', x: 240.0, y: 120.0 },
        KeyCenter { char_code: 'j', x: 280.0, y: 120.0 },
        KeyCenter { char_code: 'k', x: 320.0, y: 120.0 },
        KeyCenter { char_code: 'l', x: 360.0, y: 120.0 },
        KeyCenter { char_code: 'z', x: 60.0, y: 190.0 },
        KeyCenter { char_code: 'x', x: 100.0, y: 190.0 },
        KeyCenter { char_code: 'c', x: 140.0, y: 190.0 },
        KeyCenter { char_code: 'v', x: 180.0, y: 190.0 },
        KeyCenter { char_code: 'b', x: 220.0, y: 190.0 },
        KeyCenter { char_code: 'n', x: 260.0, y: 190.0 },
        KeyCenter { char_code: 'm', x: 300.0, y: 190.0 },
    ];

    // Swipe path: 'j' (280, 120) -> 'a' (40, 120) -> 'y' (220, 50) -> 'e' (100, 50) -> 'd' (120, 120)
    let stroke = vec![
        GesturePoint { x: 280.0, y: 120.0, timestamp_ms: 0 },
        GesturePoint { x: 160.0, y: 120.0, timestamp_ms: 50 },
        GesturePoint { x: 40.0, y: 120.0, timestamp_ms: 100 },
        GesturePoint { x: 130.0, y: 85.0, timestamp_ms: 150 },
        GesturePoint { x: 220.0, y: 50.0, timestamp_ms: 200 },
        GesturePoint { x: 160.0, y: 50.0, timestamp_ms: 250 },
        GesturePoint { x: 100.0, y: 50.0, timestamp_ms: 300 },
        GesturePoint { x: 110.0, y: 85.0, timestamp_ms: 350 },
        GesturePoint { x: 120.0, y: 120.0, timestamp_ms: 400 },
    ];

    let results = GestureDecoder::decode(&stroke, &keys, 40.0, dict, 10);
    println!("Jayed gesture results: {:?}", results);
    assert!(results.iter().any(|c| c.word == "Jayed" || c.word.eq_ignore_ascii_case("jayed")));
}


    #[test]
    fn test_decode_iphone_gesture() {
        let dict = get_en_binary_dict().expect("en dict");

        let keys = vec![
            KeyCenter { char_code: 'q', x: 20.0, y: 50.0 },
            KeyCenter { char_code: 'w', x: 60.0, y: 50.0 },
            KeyCenter { char_code: 'e', x: 100.0, y: 50.0 },
            KeyCenter { char_code: 'r', x: 140.0, y: 50.0 },
            KeyCenter { char_code: 't', x: 180.0, y: 50.0 },
            KeyCenter { char_code: 'y', x: 220.0, y: 50.0 },
            KeyCenter { char_code: 'u', x: 260.0, y: 50.0 },
            KeyCenter { char_code: 'i', x: 300.0, y: 50.0 },
            KeyCenter { char_code: 'o', x: 340.0, y: 50.0 },
            KeyCenter { char_code: 'p', x: 380.0, y: 50.0 },
            KeyCenter { char_code: 'a', x: 40.0, y: 120.0 },
            KeyCenter { char_code: 's', x: 80.0, y: 120.0 },
            KeyCenter { char_code: 'd', x: 120.0, y: 120.0 },
            KeyCenter { char_code: 'f', x: 160.0, y: 120.0 },
            KeyCenter { char_code: 'g', x: 200.0, y: 120.0 },
            KeyCenter { char_code: 'h', x: 240.0, y: 120.0 },
            KeyCenter { char_code: 'j', x: 280.0, y: 120.0 },
            KeyCenter { char_code: 'k', x: 320.0, y: 120.0 },
            KeyCenter { char_code: 'l', x: 360.0, y: 120.0 },
            KeyCenter { char_code: 'z', x: 60.0, y: 190.0 },
            KeyCenter { char_code: 'x', x: 100.0, y: 190.0 },
            KeyCenter { char_code: 'c', x: 140.0, y: 190.0 },
            KeyCenter { char_code: 'v', x: 180.0, y: 190.0 },
            KeyCenter { char_code: 'b', x: 220.0, y: 190.0 },
            KeyCenter { char_code: 'n', x: 260.0, y: 190.0 },
            KeyCenter { char_code: 'm', x: 300.0, y: 190.0 },
        ];

        // Swipe path for "iPhone": 'i' (300, 50) -> 'p' (380, 50) -> 'h' (240, 120) -> 'o' (340, 50) -> 'n' (260, 190) -> 'e' (100, 50)
        let stroke = vec![
            GesturePoint { x: 300.0, y: 50.0, timestamp_ms: 0 },
            GesturePoint { x: 380.0, y: 50.0, timestamp_ms: 50 },
            GesturePoint { x: 240.0, y: 120.0, timestamp_ms: 100 },
            GesturePoint { x: 340.0, y: 50.0, timestamp_ms: 150 },
            GesturePoint { x: 260.0, y: 190.0, timestamp_ms: 200 },
            GesturePoint { x: 100.0, y: 50.0, timestamp_ms: 250 },
        ];

        let results = GestureDecoder::decode(&stroke, &keys, 40.0, dict, 10);
        println!("iPhone gesture results: {:?}", results);
        assert!(results.iter().any(|c| c.word == "iPhone"));
    }

    #[test]
    fn test_decode_the_gesture() {
        let dict = get_en_binary_dict().expect("en dict");

        // Mock QWERTY key centers: 't' -> 'h' -> 'e'
        let keys = vec![
            KeyCenter { char_code: 'q', x: 20.0, y: 50.0 },
            KeyCenter { char_code: 'w', x: 60.0, y: 50.0 },
            KeyCenter { char_code: 'e', x: 100.0, y: 50.0 },
            KeyCenter { char_code: 'r', x: 140.0, y: 50.0 },
            KeyCenter { char_code: 't', x: 180.0, y: 50.0 },
            KeyCenter { char_code: 'y', x: 220.0, y: 50.0 },
            KeyCenter { char_code: 'u', x: 260.0, y: 50.0 },
            KeyCenter { char_code: 'i', x: 300.0, y: 50.0 },
            KeyCenter { char_code: 'o', x: 340.0, y: 50.0 },
            KeyCenter { char_code: 'p', x: 380.0, y: 50.0 },
            KeyCenter { char_code: 'a', x: 40.0, y: 120.0 },
            KeyCenter { char_code: 's', x: 80.0, y: 120.0 },
            KeyCenter { char_code: 'd', x: 120.0, y: 120.0 },
            KeyCenter { char_code: 'f', x: 160.0, y: 120.0 },
            KeyCenter { char_code: 'g', x: 200.0, y: 120.0 },
            KeyCenter { char_code: 'h', x: 240.0, y: 120.0 },
            KeyCenter { char_code: 'j', x: 280.0, y: 120.0 },
            KeyCenter { char_code: 'k', x: 320.0, y: 120.0 },
            KeyCenter { char_code: 'l', x: 360.0, y: 120.0 },
        ];

        // Swipe points for "the": (180, 50) -> (240, 120) -> (100, 50)
        let stroke = vec![
            GesturePoint { x: 180.0, y: 50.0, timestamp_ms: 0 },
            GesturePoint { x: 210.0, y: 85.0, timestamp_ms: 50 },
            GesturePoint { x: 240.0, y: 120.0, timestamp_ms: 100 },
            GesturePoint { x: 170.0, y: 85.0, timestamp_ms: 150 },
            GesturePoint { x: 100.0, y: 50.0, timestamp_ms: 200 },
        ];

        let results = GestureDecoder::decode(&stroke, &keys, 40.0, dict, 5);
        assert!(!results.is_empty());
        assert_eq!(results[0].word, "the");
    }

    #[test]
    fn test_decode_borno_phonetic_gesture() {
        let dict = match get_bn_binary_dict() {
            Some(d) => d,
            None => return, // In test environments where bn.dict is not deployed
        };
        let translit = BornoPhoneticTransliterator;

        let keys = vec![
            KeyCenter { char_code: 'q', x: 20.0, y: 50.0 },
            KeyCenter { char_code: 'w', x: 60.0, y: 50.0 },
            KeyCenter { char_code: 'e', x: 100.0, y: 50.0 },
            KeyCenter { char_code: 'r', x: 140.0, y: 50.0 },
            KeyCenter { char_code: 't', x: 180.0, y: 50.0 },
            KeyCenter { char_code: 'y', x: 220.0, y: 50.0 },
            KeyCenter { char_code: 'u', x: 260.0, y: 50.0 },
            KeyCenter { char_code: 'i', x: 300.0, y: 50.0 },
            KeyCenter { char_code: 'o', x: 340.0, y: 50.0 },
            KeyCenter { char_code: 'p', x: 380.0, y: 50.0 },
            KeyCenter { char_code: 'a', x: 40.0, y: 120.0 },
            KeyCenter { char_code: 's', x: 80.0, y: 120.0 },
            KeyCenter { char_code: 'd', x: 120.0, y: 120.0 },
            KeyCenter { char_code: 'f', x: 160.0, y: 120.0 },
            KeyCenter { char_code: 'g', x: 200.0, y: 120.0 },
            KeyCenter { char_code: 'h', x: 240.0, y: 120.0 },
            KeyCenter { char_code: 'j', x: 280.0, y: 120.0 },
            KeyCenter { char_code: 'k', x: 320.0, y: 120.0 },
            KeyCenter { char_code: 'l', x: 360.0, y: 120.0 },
            KeyCenter { char_code: 'z', x: 60.0, y: 190.0 },
            KeyCenter { char_code: 'x', x: 100.0, y: 190.0 },
            KeyCenter { char_code: 'c', x: 140.0, y: 190.0 },
            KeyCenter { char_code: 'v', x: 180.0, y: 190.0 },
            KeyCenter { char_code: 'b', x: 220.0, y: 190.0 },
            KeyCenter { char_code: 'n', x: 260.0, y: 190.0 },
            KeyCenter { char_code: 'm', x: 300.0, y: 190.0 },
        ];

        // Gesture: 'a' (40, 120) -> 'm' (300, 190) -> 'i' (300, 50) for "আমি"
        let stroke_ami = vec![
            GesturePoint { x: 40.0, y: 120.0, timestamp_ms: 0 },
            GesturePoint { x: 170.0, y: 155.0, timestamp_ms: 50 },
            GesturePoint { x: 300.0, y: 190.0, timestamp_ms: 100 },
            GesturePoint { x: 300.0, y: 120.0, timestamp_ms: 150 },
            GesturePoint { x: 300.0, y: 50.0, timestamp_ms: 200 },
        ];

        let results_ami = GestureDecoder::decode_phonetic(&stroke_ami, &keys, 40.0, dict, &translit, 5);
        assert!(!results_ami.is_empty(), "Expected results for 'ami'");
        assert_eq!(results_ami[0].word, "আমি");

        // Gesture: 'a' -> 'm' -> 'a' -> 'd' -> 'e' -> 'r' for "আমাদের"
        let stroke_amader = vec![
            GesturePoint { x: 40.0, y: 120.0, timestamp_ms: 0 },    // a
            GesturePoint { x: 300.0, y: 190.0, timestamp_ms: 50 },  // m
            GesturePoint { x: 40.0, y: 120.0, timestamp_ms: 100 },  // a
            GesturePoint { x: 120.0, y: 120.0, timestamp_ms: 150 }, // d
            GesturePoint { x: 100.0, y: 50.0, timestamp_ms: 200 },  // e
            GesturePoint { x: 140.0, y: 50.0, timestamp_ms: 250 },  // r
        ];

        let results_amader = GestureDecoder::decode_phonetic(&stroke_amader, &keys, 40.0, dict, &translit, 5);
        assert!(!results_amader.is_empty(), "Expected results for 'amader'");
        assert_eq!(results_amader[0].word, "আমাদের");
    }

    #[test]
    fn test_decode_borno_phonetic_with_learned_words() {
        let dict = match get_bn_binary_dict() {
            Some(d) => d,
            None => return,
        };
        let translit = BornoPhoneticTransliterator;

        let keys = vec![
            KeyCenter { char_code: 'q', x: 20.0, y: 50.0 },
            KeyCenter { char_code: 'w', x: 60.0, y: 50.0 },
            KeyCenter { char_code: 'e', x: 100.0, y: 50.0 },
            KeyCenter { char_code: 'r', x: 140.0, y: 50.0 },
            KeyCenter { char_code: 't', x: 180.0, y: 50.0 },
            KeyCenter { char_code: 'y', x: 220.0, y: 50.0 },
            KeyCenter { char_code: 'u', x: 260.0, y: 50.0 },
            KeyCenter { char_code: 'i', x: 300.0, y: 50.0 },
            KeyCenter { char_code: 'o', x: 340.0, y: 50.0 },
            KeyCenter { char_code: 'p', x: 380.0, y: 50.0 },
            KeyCenter { char_code: 'a', x: 40.0, y: 120.0 },
            KeyCenter { char_code: 's', x: 80.0, y: 120.0 },
            KeyCenter { char_code: 'd', x: 120.0, y: 120.0 },
            KeyCenter { char_code: 'f', x: 160.0, y: 120.0 },
            KeyCenter { char_code: 'g', x: 200.0, y: 120.0 },
            KeyCenter { char_code: 'h', x: 240.0, y: 120.0 },
            KeyCenter { char_code: 'j', x: 280.0, y: 120.0 },
            KeyCenter { char_code: 'k', x: 320.0, y: 120.0 },
            KeyCenter { char_code: 'l', x: 360.0, y: 120.0 },
            KeyCenter { char_code: 'z', x: 60.0, y: 190.0 },
            KeyCenter { char_code: 'x', x: 100.0, y: 190.0 },
            KeyCenter { char_code: 'c', x: 140.0, y: 190.0 },
            KeyCenter { char_code: 'v', x: 180.0, y: 190.0 },
            KeyCenter { char_code: 'b', x: 220.0, y: 190.0 },
            KeyCenter { char_code: 'n', x: 260.0, y: 190.0 },
            KeyCenter { char_code: 'm', x: 300.0, y: 190.0 },
        ];

        let stroke_amir = vec![
            GesturePoint { x: 40.0, y: 120.0, timestamp_ms: 0 },
            GesturePoint { x: 170.0, y: 155.0, timestamp_ms: 50 },
            GesturePoint { x: 300.0, y: 190.0, timestamp_ms: 100 },
            GesturePoint { x: 300.0, y: 120.0, timestamp_ms: 150 },
            GesturePoint { x: 300.0, y: 50.0, timestamp_ms: 200 },
            GesturePoint { x: 220.0, y: 50.0, timestamp_ms: 250 },
            GesturePoint { x: 140.0, y: 50.0, timestamp_ms: 300 },
        ];

        let learned = vec!["আমির".to_string(), "মা".to_string()];
        let results = GestureDecoder::decode_phonetic_with_learned(&stroke_amir, &keys, 40.0, dict, &translit, 5, &learned);
        assert!(!results.is_empty());
        assert_eq!(results[0].word, "আমির");

        // Now test when user gestures "আমাদের" (a -> m -> a -> d -> e -> r)
        // Even if user previously learned "আমির" and "মা", neither should win over "আমাদের"!
        let stroke_amader = vec![
            GesturePoint { x: 40.0, y: 120.0, timestamp_ms: 0 },
            GesturePoint { x: 170.0, y: 155.0, timestamp_ms: 50 },
            GesturePoint { x: 300.0, y: 190.0, timestamp_ms: 100 },
            GesturePoint { x: 170.0, y: 155.0, timestamp_ms: 150 },
            GesturePoint { x: 40.0, y: 120.0, timestamp_ms: 200 },
            GesturePoint { x: 120.0, y: 120.0, timestamp_ms: 250 },
            GesturePoint { x: 100.0, y: 50.0, timestamp_ms: 300 },
            GesturePoint { x: 140.0, y: 50.0, timestamp_ms: 350 },
        ];
        let results_amader = GestureDecoder::decode_phonetic_with_learned(&stroke_amader, &keys, 40.0, dict, &translit, 5, &learned);
        assert!(!results_amader.is_empty(), "Results for 'amader' should not be empty");
        assert_eq!(results_amader[0].word, "আমাদের", "Expected 'আমাদের' to win even when 'আমির' and 'মা' are learned: {:?}", results_amader);
        assert!(!results_amader.iter().any(|c| c.word == "মা"), "'মা' should not be predicted for long 'amader' gesture");
    }

    #[test]
    fn test_compact_phonetic_index_memory_footprint() {
        let dict = match get_bn_binary_dict() {
            Some(d) => d,
            None => return,
        };
        let translit = BornoPhoneticTransliterator;
        let index = PhoneticIndex::build(dict, &translit);

        let entries_bytes = index.entries.len() * std::mem::size_of::<CompactPhoneticEntry>();
        let keys_bytes = index.keys_pool.len();
        let bucket_ranges_bytes = std::mem::size_of_val(&index.bucket_ranges);
        let total_bytes = entries_bytes + keys_bytes + bucket_ranges_bytes;

        println!(
            "Entries: {}, entries_bytes: {}, keys_bytes: {}, total: {} bytes (~{:.2} MB)",
            index.entries.len(),
            entries_bytes,
            keys_bytes,
            total_bytes,
            total_bytes as f64 / (1024.0 * 1024.0)
        );

        assert!(!index.entries.is_empty(), "Entries should not be empty");
        assert!(!index.keys_pool.is_empty(), "Keys pool should not be empty");
        assert!(
            total_bytes < 7_500_000,
            "Total index footprint must be under 7.5 MB (was {} bytes)",
            total_bytes
        );
    }

    #[test]
    fn test_decode_milking_gesture() {
        let dict = get_en_binary_dict().expect("en dict");
        let keys = vec![
            KeyCenter { char_code: 'q', x: 20.0, y: 50.0 },
            KeyCenter { char_code: 'w', x: 60.0, y: 50.0 },
            KeyCenter { char_code: 'e', x: 100.0, y: 50.0 },
            KeyCenter { char_code: 'r', x: 140.0, y: 50.0 },
            KeyCenter { char_code: 't', x: 180.0, y: 50.0 },
            KeyCenter { char_code: 'y', x: 220.0, y: 50.0 },
            KeyCenter { char_code: 'u', x: 260.0, y: 50.0 },
            KeyCenter { char_code: 'i', x: 300.0, y: 50.0 },
            KeyCenter { char_code: 'o', x: 340.0, y: 50.0 },
            KeyCenter { char_code: 'p', x: 380.0, y: 50.0 },
            KeyCenter { char_code: 'a', x: 40.0, y: 120.0 },
            KeyCenter { char_code: 's', x: 80.0, y: 120.0 },
            KeyCenter { char_code: 'd', x: 120.0, y: 120.0 },
            KeyCenter { char_code: 'f', x: 160.0, y: 120.0 },
            KeyCenter { char_code: 'g', x: 200.0, y: 120.0 },
            KeyCenter { char_code: 'h', x: 240.0, y: 120.0 },
            KeyCenter { char_code: 'j', x: 280.0, y: 120.0 },
            KeyCenter { char_code: 'k', x: 320.0, y: 120.0 },
            KeyCenter { char_code: 'l', x: 360.0, y: 120.0 },
            KeyCenter { char_code: 'z', x: 60.0, y: 190.0 },
            KeyCenter { char_code: 'x', x: 100.0, y: 190.0 },
            KeyCenter { char_code: 'c', x: 140.0, y: 190.0 },
            KeyCenter { char_code: 'v', x: 180.0, y: 190.0 },
            KeyCenter { char_code: 'b', x: 220.0, y: 190.0 },
            KeyCenter { char_code: 'n', x: 260.0, y: 190.0 },
            KeyCenter { char_code: 'm', x: 300.0, y: 190.0 },
        ];

        // Swipe points for "milking": m -> i -> l -> k -> i -> n -> g
        let stroke_milking = vec![
            GesturePoint { x: 300.0, y: 190.0, timestamp_ms: 0 },   // m
            GesturePoint { x: 300.0, y: 50.0, timestamp_ms: 50 },    // i
            GesturePoint { x: 360.0, y: 120.0, timestamp_ms: 100 },  // l
            GesturePoint { x: 320.0, y: 120.0, timestamp_ms: 150 },  // k
            GesturePoint { x: 300.0, y: 50.0, timestamp_ms: 200 },   // i
            GesturePoint { x: 260.0, y: 190.0, timestamp_ms: 250 },  // n
            GesturePoint { x: 200.0, y: 120.0, timestamp_ms: 300 },  // g
        ];

        let results = GestureDecoder::decode(&stroke_milking, &keys, 40.0, dict, 10);
        assert!(!results.is_empty(), "Results should not be empty");
        assert_eq!(results[0].word, "milking", "Expected 'milking' to be the top candidate: {:?}", results);
    }

    #[test]
    fn test_decode_intarnet_gesture() {
        let dict = get_bn_binary_dict().expect("bn dict");
        let translit = BornoPhoneticTransliterator;

        let keys = vec![
            KeyCenter { char_code: 'q', x: 20.0, y: 50.0 },
            KeyCenter { char_code: 'w', x: 60.0, y: 50.0 },
            KeyCenter { char_code: 'e', x: 100.0, y: 50.0 },
            KeyCenter { char_code: 'r', x: 140.0, y: 50.0 },
            KeyCenter { char_code: 't', x: 180.0, y: 50.0 },
            KeyCenter { char_code: 'y', x: 220.0, y: 50.0 },
            KeyCenter { char_code: 'u', x: 260.0, y: 50.0 },
            KeyCenter { char_code: 'i', x: 300.0, y: 50.0 },
            KeyCenter { char_code: 'o', x: 340.0, y: 50.0 },
            KeyCenter { char_code: 'p', x: 380.0, y: 50.0 },
            KeyCenter { char_code: 'a', x: 40.0, y: 120.0 },
            KeyCenter { char_code: 's', x: 80.0, y: 120.0 },
            KeyCenter { char_code: 'd', x: 120.0, y: 120.0 },
            KeyCenter { char_code: 'f', x: 160.0, y: 120.0 },
            KeyCenter { char_code: 'g', x: 200.0, y: 120.0 },
            KeyCenter { char_code: 'h', x: 240.0, y: 120.0 },
            KeyCenter { char_code: 'j', x: 280.0, y: 120.0 },
            KeyCenter { char_code: 'k', x: 320.0, y: 120.0 },
            KeyCenter { char_code: 'l', x: 360.0, y: 120.0 },
            KeyCenter { char_code: 'z', x: 60.0, y: 190.0 },
            KeyCenter { char_code: 'x', x: 100.0, y: 190.0 },
            KeyCenter { char_code: 'c', x: 140.0, y: 190.0 },
            KeyCenter { char_code: 'v', x: 180.0, y: 190.0 },
            KeyCenter { char_code: 'b', x: 220.0, y: 190.0 },
            KeyCenter { char_code: 'n', x: 260.0, y: 190.0 },
            KeyCenter { char_code: 'm', x: 300.0, y: 190.0 },
        ];

        // Swipe: i -> n -> t -> a -> r -> n -> e -> t
        let stroke_intarnet = vec![
            GesturePoint { x: 300.0, y: 50.0, timestamp_ms: 0 },    // i
            GesturePoint { x: 260.0, y: 190.0, timestamp_ms: 50 },  // n
            GesturePoint { x: 180.0, y: 50.0, timestamp_ms: 100 },  // t
            GesturePoint { x: 40.0, y: 120.0, timestamp_ms: 150 },  // a
            GesturePoint { x: 140.0, y: 50.0, timestamp_ms: 200 },  // r
            GesturePoint { x: 260.0, y: 190.0, timestamp_ms: 250 },  // n
            GesturePoint { x: 100.0, y: 50.0, timestamp_ms: 300 },  // e
            GesturePoint { x: 180.0, y: 50.0, timestamp_ms: 350 },  // t
        ];

        let results = GestureDecoder::decode_phonetic(&stroke_intarnet, &keys, 40.0, dict, &translit, 10);
        assert!(!results.is_empty(), "Results for intarnet should not be empty");
        assert_eq!(results[0].word, "ইন্টারনেট", "Expected 'ইন্টারনেট' to be top candidate for intarnet stroke: {:?}", results);

        // Swipe: i -> n -> t -> e -> r -> n -> e -> t (English spelling of internet on QWERTY)
        let stroke_internet = vec![
            GesturePoint { x: 300.0, y: 50.0, timestamp_ms: 0 },    // i
            GesturePoint { x: 260.0, y: 190.0, timestamp_ms: 50 },  // n
            GesturePoint { x: 180.0, y: 50.0, timestamp_ms: 100 },  // t
            GesturePoint { x: 100.0, y: 50.0, timestamp_ms: 150 },  // e
            GesturePoint { x: 140.0, y: 50.0, timestamp_ms: 200 },  // r
            GesturePoint { x: 260.0, y: 190.0, timestamp_ms: 250 },  // n
            GesturePoint { x: 100.0, y: 50.0, timestamp_ms: 300 },  // e
            GesturePoint { x: 180.0, y: 50.0, timestamp_ms: 350 },  // t
        ];

        let results_en = GestureDecoder::decode_phonetic(&stroke_internet, &keys, 40.0, dict, &translit, 10);
        assert!(!results_en.is_empty(), "Results for internet stroke should not be empty");
        assert_eq!(results_en[0].word, "ইন্টারনেট", "Expected 'ইন্টারনেট' to be top candidate (and not 'ইন্টেন্ট') for internet stroke: {:?}", results_en);
    }

    #[test]
    fn test_decode_with_learned_words() {
        let dict = get_en_binary_dict().expect("en dict");

        let keys = vec![
            KeyCenter { char_code: 'q', x: 20.0, y: 50.0 },
            KeyCenter { char_code: 'w', x: 60.0, y: 50.0 },
            KeyCenter { char_code: 'e', x: 100.0, y: 50.0 },
            KeyCenter { char_code: 'r', x: 140.0, y: 50.0 },
            KeyCenter { char_code: 't', x: 180.0, y: 50.0 },
            KeyCenter { char_code: 'y', x: 220.0, y: 50.0 },
            KeyCenter { char_code: 'u', x: 260.0, y: 50.0 },
            KeyCenter { char_code: 'i', x: 300.0, y: 50.0 },
            KeyCenter { char_code: 'o', x: 340.0, y: 50.0 },
            KeyCenter { char_code: 'p', x: 380.0, y: 50.0 },
            KeyCenter { char_code: 'a', x: 40.0, y: 120.0 },
            KeyCenter { char_code: 's', x: 80.0, y: 120.0 },
            KeyCenter { char_code: 'd', x: 120.0, y: 120.0 },
            KeyCenter { char_code: 'f', x: 160.0, y: 120.0 },
            KeyCenter { char_code: 'g', x: 200.0, y: 120.0 },
            KeyCenter { char_code: 'h', x: 240.0, y: 120.0 },
            KeyCenter { char_code: 'j', x: 280.0, y: 120.0 },
            KeyCenter { char_code: 'k', x: 320.0, y: 120.0 },
            KeyCenter { char_code: 'l', x: 360.0, y: 120.0 },
        ];

        let stroke = vec![
            GesturePoint { x: 180.0, y: 50.0, timestamp_ms: 0 },
            GesturePoint { x: 210.0, y: 85.0, timestamp_ms: 50 },
            GesturePoint { x: 240.0, y: 120.0, timestamp_ms: 100 },
            GesturePoint { x: 170.0, y: 85.0, timestamp_ms: 150 },
            GesturePoint { x: 100.0, y: 50.0, timestamp_ms: 200 },
        ];

        let learned = vec!["the".to_string(), "thy".to_string(), "xyz".to_string()];
        let results = GestureDecoder::decode_with_learned(&stroke, &keys, 40.0, dict, 5, &learned);
        assert!(!results.is_empty());
        assert_eq!(results[0].word, "the");
        assert!(!results.iter().any(|c| c.word == "xyz"));
    }
}
