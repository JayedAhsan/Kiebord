use std::collections::{HashMap, HashSet};
use std::sync::{OnceLock, RwLock};

/// Physical key adjacency and phonetic confusion matrix for typo weighting.
#[derive(Clone, Debug, Default)]
pub struct KeyProximity {
    neighbors: HashMap<char, HashSet<char>>,
    case_pairs: HashSet<(char, char)>,
}

static QWERTY_PROXIMITY: OnceLock<KeyProximity> = OnceLock::new();
static TURKISH_Q_PROXIMITY: OnceLock<KeyProximity> = OnceLock::new();
static TURKISH_F_PROXIMITY: OnceLock<KeyProximity> = OnceLock::new();
static PROBHAT_PROXIMITY: OnceLock<KeyProximity> = OnceLock::new();
static BORNO_PROXIMITY: OnceLock<KeyProximity> = OnceLock::new();
static EMPTY_PROXIMITY: OnceLock<KeyProximity> = OnceLock::new();
static DYNAMIC_LAYOUT_PROXIMITIES: OnceLock<RwLock<HashMap<String, &'static KeyProximity>>> = OnceLock::new();

impl KeyProximity {
    pub fn new() -> Self {
        Self {
            neighbors: HashMap::new(),
            case_pairs: HashSet::new(),
        }
    }

    pub fn add_neighbor_bidirectional(&mut self, a: char, b: char) {
        if a != b {
            self.neighbors.entry(a).or_default().insert(b);
            self.neighbors.entry(b).or_default().insert(a);
        }
    }

    pub fn add_case_pair(&mut self, a: char, b: char) {
        if a != b {
            self.case_pairs.insert((a, b));
            self.case_pairs.insert((b, a));
            self.add_neighbor_bidirectional(a, b);
        }
    }

    pub fn is_case_pair(&self, a: char, b: char) -> bool {
        self.case_pairs.contains(&(a, b))
    }

    pub fn add_confusion_group(&mut self, group: &[char]) {
        for &c1 in group {
            for &c2 in group {
                if c1 != c2 {
                    self.case_pairs.insert((c1, c2));
                    self.neighbors.entry(c1).or_default().insert(c2);
                }
            }
        }
    }

    /// Builds proximity from keyboard row strings.
    pub fn of_rows(rows: &[&str]) -> Self {
        let mut neighbors: HashMap<char, HashSet<char>> = HashMap::new();
        let grid: Vec<Vec<char>> = rows.iter().map(|r| r.chars().collect()).collect();

        for (r, row) in grid.iter().enumerate() {
            for (c, &ch) in row.iter().enumerate() {
                let set = neighbors.entry(ch).or_default();

                // Same row left and right
                if c > 0 {
                    set.insert(row[c - 1]);
                }
                if c + 1 < row.len() {
                    set.insert(row[c + 1]);
                }

                // Row above: c-1, c, c+1
                if r > 0 {
                    let above = &grid[r - 1];
                    for col in c.saturating_sub(1)..=c + 1 {
                        if let Some(&adj) = above.get(col) {
                            set.insert(adj);
                        }
                    }
                }

                // Row below: c-1, c, c+1
                if r + 1 < grid.len() {
                    let below = &grid[r + 1];
                    for col in c.saturating_sub(1)..=c + 1 {
                        if let Some(&adj) = below.get(col) {
                            set.insert(adj);
                        }
                    }
                }
            }
        }

        Self {
            neighbors,
            case_pairs: HashSet::new(),
        }
    }

    /// English QWERTY key proximity.
    pub fn qwerty() -> Self {
        Self::of_rows(&["qwertyuiop", "asdfghjkl", "zxcvbnm"])
    }

    /// Turkish Q (Full 12-key and Compact 10-key) key proximity and diacritic/case pairs.
    pub fn turkish_q() -> Self {
        let mut prox = Self::of_rows(&[
            "qwertyuıopğü",
            "asdfghjklşi",
            "zxcvbnmöç",
        ]);

        // Also include standard 10-key QWERTY adjacencies for Compact Turkish Q ('i' next to 'u' and 'o')
        let compact_q = Self::of_rows(&["qwertyuiop", "asdfghjkl", "zxcvbnm"]);
        for (&ch, set) in &compact_q.neighbors {
            for &adj in set {
                prox.add_neighbor_bidirectional(ch, adj);
            }
        }

        // Low-cost diacritic and Turkish case pairs (0.10 cost in beam search)
        let diacritic_and_case_pairs: &[(char, char)] = &[
            ('i', 'ı'),
            ('i', 'İ'),
            ('ı', 'I'),
            ('i', 'I'),
            ('ı', 'İ'),
            ('g', 'ğ'),
            ('u', 'ü'),
            ('s', 'ş'),
            ('o', 'ö'),
            ('c', 'ç'),
            ('a', 'â'),
            ('i', 'î'),
            ('u', 'û'),
            ('ğ', 'Ğ'),
            ('ü', 'Ü'),
            ('ş', 'Ş'),
            ('ö', 'Ö'),
            ('ç', 'Ç'),
        ];
        for &(a, b) in diacritic_and_case_pairs {
            prox.add_case_pair(a, b);
        }

        prox
    }

    /// Turkish F (National standard TS 2117 / touch 12-11-9) key proximity and diacritic/case pairs.
    pub fn turkish_f() -> Self {
        let mut prox = Self::of_rows(&[
            "fgğıodrnhpqw",
            "uieaütkmlyş",
            "jövcçzsbx",
        ]);

        // Low-cost diacritic and Turkish case pairs (0.10 cost in beam search)
        let diacritic_and_case_pairs: &[(char, char)] = &[
            ('i', 'ı'),
            ('i', 'İ'),
            ('ı', 'I'),
            ('i', 'I'),
            ('ı', 'İ'),
            ('g', 'ğ'),
            ('u', 'ü'),
            ('s', 'ş'),
            ('o', 'ö'),
            ('c', 'ç'),
            ('a', 'â'),
            ('i', 'î'),
            ('u', 'û'),
            ('ğ', 'Ğ'),
            ('ü', 'Ü'),
            ('ş', 'Ş'),
            ('ö', 'Ö'),
            ('ç', 'Ç'),
        ];
        for &(a, b) in diacritic_and_case_pairs {
            prox.add_case_pair(a, b);
        }

        prox
    }

    /// Bengali Probhat key proximity and phonetically confusable pairs.
    pub fn probhat() -> Self {
        let mut prox = Self::of_rows(&[
            "দধপফবভতমযয",
            "আঅেসহকখগঘ",
            "য়ড়ঢ়ণতথদধ",
        ]);

        let confusion_groups: &[&[char]] = &[
            &['শ', 'ষ', 'স'],
            &['র', 'ড়', 'ঢ়'],
            &['ন', 'ণ'],
            &['য', 'জ'],
            &['ব', 'ভ'],
            &['প', 'ফ'],
            &['ক', 'খ'],
            &['গ', 'ঘ'],
            &['ত', 'ৎ', 'থ'],
            &['দ', 'ধ'],
            &['ি', 'ী'],
            &['ু', 'ূ'],
            &['এ', 'ঐ'],
            &['ও', 'ঔ'],
        ];

        for &group in confusion_groups {
            prox.add_confusion_group(group);
        }

        prox
    }

    /// Backwards compatibility alias for probhat.
    pub fn bengali() -> Self {
        Self::probhat()
    }

    /// Borno Phonetic key proximity model:
    /// 1. Lowercase / Uppercase phonetic pairs (e.g. ত|ট, থ|ঠ, দ|ড, ধ|ঢ, ন|ণ, শ|ষ|স, র|ড়|ঢ়, etc.)
    /// 2. Phonetic vowel / kar confusions (e.g. ে|া, এ|আ, অ|ও, etc.)
    /// 3. Phonetic consonant confusions (e.g. জ|য, য|য়, ব|ভ, প|ফ, ক|খ, গ|ঘ, ত|ৎ, ং|ঙ)
    /// 4. QWERTY physical key proximity projected onto Borno transliterated characters for finger slip typos.
    pub fn borno_phonetic() -> Self {
        let mut prox = Self::new();

        // 1. Lowercase vs Uppercase phonetic pairs and primary confusions
        let phonetic_pairs: &[(char, char)] = &[
            ('ত', 'ট'), // t vs T
            ('থ', 'ঠ'), // th vs Th
            ('দ', 'ড'), // d vs D
            ('ধ', 'ঢ'), // dh vs Dh
            ('ন', 'ণ'), // n vs N
            ('ি', 'ী'), // i vs I
            ('ু', 'ূ'), // u vs U
            ('ই', 'ঈ'), // i vs I initial
            ('উ', 'ঊ'), // u vs U initial
            ('ব', 'ভ'), // b vs v / bh
            ('প', 'ফ'), // p vs f / ph
            ('ক', 'খ'), // k vs kh
            ('গ', 'ঘ'), // g vs gh
            ('চ', 'ছ'), // c vs ch / Ch
            ('জ', 'ঝ'), // j vs jh / J
            ('য', 'য়'), // z vs y
            ('জ', 'য'), // j vs z (phonetic confusion)
            ('জ', 'য়'), // j vs y
            ('ে', 'া'), // e vs a vowel sign confusion
            ('এ', 'আ'), // e vs a initial vowel confusion
            ('অ', 'আ'), // o vs a initial
            ('অ', 'ও'), // o inherent vs O
            ('ো', 'ৌ'), // o vs ou
            ('এ', 'ঐ'), // e vs oi / O
            ('ও', 'ঔ'), // o vs ou initial
            ('ৈ', 'ৌ'), // oi vs ou
            ('উ', 'ৌ'),
            ('ঊ', 'ৌ'),
            ('ু', 'ৌ'),
            ('ই', 'ৈ'),
            ('ঈ', 'ৈ'),
            ('ি', 'ৈ'),
            ('ত', 'ৎ'), // t vs t` / TH
            ('ং', 'ঙ'), // ng vs Ng
            ('ঁ', 'ং'), // xx vs ng
            ('হ', 'ঃ'), // h vs ::
        ];

        for &(c1, c2) in phonetic_pairs {
            prox.add_case_pair(c1, c2);
        }

        // Triplet clusters
        prox.add_confusion_group(&['শ', 'ষ', 'স']); // S vs Sh vs s
        prox.add_confusion_group(&['র', 'ড়', 'ঢ়']); // r vs R vs Rh

        // 2. QWERTY physical key proximity projected onto Borno transliterated characters
        let qwerty = Self::qwerty();
        let key_mappings: &[(char, &[char])] = &[
            ('w', &['ও', 'ব']),
            ('e', &['ে', 'এ']),
            ('r', &['র', 'ড়', 'ঢ়']),
            ('t', &['ত', 'ট', 'থ', 'ঠ']),
            ('y', &['য', 'য়']),
            ('u', &['ু', 'উ', 'ূ', 'ঊ', 'ৌ']),
            ('i', &['ি', 'ই', 'ী', 'ঈ', 'ৈ']),
            ('o', &['ও', 'অ', 'ো', 'ঐ', 'ঔ', 'ৈ', 'ৌ']),
            ('p', &['প', 'ফ']),
            ('a', &['া', 'আ']),
            ('s', &['স', 'শ', 'ষ']),
            ('d', &['দ', 'ড', 'ধ', 'ঢ']),
            ('f', &['ফ', 'প']),
            ('g', &['গ', 'ঘ']),
            ('h', &['হ']),
            ('j', &['জ', 'ঝ']),
            ('k', &['ক', 'খ']),
            ('l', &['ল']),
            ('z', &['য', 'জ']),
            ('x', &['ক', 'স']),
            ('c', &['চ', 'ছ']),
            ('v', &['ভ', 'ব']),
            ('b', &['ব', 'ভ']),
            ('n', &['ন', 'ণ']),
            ('m', &['ম']),
        ];

        let map: HashMap<char, &[char]> = key_mappings.iter().copied().collect();

        for (&k1, &k1_chars) in &map {
            if let Some(neighbors) = qwerty.neighbors.get(&k1) {
                for &k2 in neighbors {
                    if let Some(&k2_chars) = map.get(&k2) {
                        for &b1 in k1_chars {
                            for &b2 in k2_chars {
                                prox.add_neighbor_bidirectional(b1, b2);
                            }
                        }
                    }
                }
            }
        }

        prox
    }

    pub fn borno_phonetic_cached() -> &'static KeyProximity {
        BORNO_PROXIMITY.get_or_init(Self::borno_phonetic)
    }

    /// Registers a custom layout proximity at runtime for future layouts.
    pub fn register_layout(layout_id: &str, prox: KeyProximity) {
        let registry = DYNAMIC_LAYOUT_PROXIMITIES.get_or_init(|| RwLock::new(HashMap::new()));
        let leaked_ref: &'static KeyProximity = Box::leak(Box::new(prox));
        if let Ok(mut map) = registry.write() {
            map.insert(layout_id.to_string(), leaked_ref);
        }
    }

    /// Retrieves the KeyProximity tied to a given layout identifier.
    /// Supports "bn_borno", "bn_probhat", "en_qwerty", registered custom layouts,
    /// or layouts implementing PhoneticTransliterator::proximity.
    pub fn for_layout(layout_id: &str) -> &'static KeyProximity {
        if layout_id.ends_with("_exact") {
            return EMPTY_PROXIMITY.get_or_init(Self::new);
        }

        // 1. Check custom dynamically registered layout proximities first
        if let Some(registry) = DYNAMIC_LAYOUT_PROXIMITIES.get() {
            if let Ok(guard) = registry.read() {
                if let Some(&custom) = guard.get(layout_id) {
                    return custom;
                }
            }
        }

        // 2. Check if a PhoneticTransliterator defines layout proximity
        if let Some(translit) = crate::translit::get_phonetic_transliterator(layout_id) {
            return translit.proximity().unwrap_or_else(|| EMPTY_PROXIMITY.get_or_init(Self::new));
        }

        // 3. Fallback to predefined layout proximities
        match layout_id {
            "bn_borno" | "bn_national" => BORNO_PROXIMITY.get_or_init(Self::borno_phonetic),
            "bn_probhat" => PROBHAT_PROXIMITY.get_or_init(Self::probhat),
            "bn_avro" => EMPTY_PROXIMITY.get_or_init(Self::new),
            "bn" => BORNO_PROXIMITY.get_or_init(Self::borno_phonetic),
            "tr_f" => TURKISH_F_PROXIMITY.get_or_init(Self::turkish_f),
            "tr" | "tr_q" | "tr_q_compact" => TURKISH_Q_PROXIMITY.get_or_init(Self::turkish_q),
            "en_qwerty" | "en" => QWERTY_PROXIMITY.get_or_init(Self::qwerty),
            _ => {
                if layout_id.starts_with("bn") {
                    BORNO_PROXIMITY.get_or_init(Self::borno_phonetic)
                } else if layout_id == "tr_f" || layout_id.starts_with("tr_f") {
                    TURKISH_F_PROXIMITY.get_or_init(Self::turkish_f)
                } else if layout_id.starts_with("tr") {
                    TURKISH_Q_PROXIMITY.get_or_init(Self::turkish_q)
                } else {
                    QWERTY_PROXIMITY.get_or_init(Self::qwerty)
                }
            }
        }
    }

    /// Checks if two characters are physical keyboard neighbors or confusable.
    pub fn are_neighbors(&self, a: char, b: char) -> bool {
        self.neighbors.get(&a).map_or(false, |s| s.contains(&b))
    }

    /// Returns iterator over neighboring or confusable characters for a given key.
    pub fn get_neighbors(&self, c: char) -> impl Iterator<Item = &char> {
        self.neighbors.get(&c).into_iter().flat_map(|s| s.iter())
    }

    /// Calculates edit penalty:
    /// - 0.0 for identical characters
    /// - 0.4 for neighboring keys (finger slip)
    /// - 1.2 for distant substitution
    pub fn penalty(&self, typed: char, candidate: char) -> f32 {
        if typed == candidate {
            0.0
        } else if self.are_neighbors(typed, candidate) {
            0.4
        } else {
            1.2
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_qwerty_neighbors() {
        let q = KeyProximity::qwerty();
        // 'g' and 'h' are side by side
        assert!(q.are_neighbors('g', 'h'));
        assert!(q.are_neighbors('h', 'g'));

        // 'h' and 'y' (above row)
        assert!(q.are_neighbors('h', 'y'));

        // 'h' and 'b' (below row)
        assert!(q.are_neighbors('h', 'b'));

        // 'q' and 'p' are on opposite ends
        assert!(!q.are_neighbors('q', 'p'));

        // Penalty tests
        assert_eq!(q.penalty('h', 'h'), 0.0);
        assert_eq!(q.penalty('g', 'h'), 0.4);
        assert_eq!(q.penalty('q', 'h'), 1.2);
    }

    #[test]
    fn test_probhat_confusion() {
        let b = KeyProximity::probhat();
        assert!(b.are_neighbors('শ', 'ষ'));
        assert!(b.are_neighbors('র', 'ড়'));
        assert!(b.are_neighbors('ন', 'ণ'));
        assert_eq!(b.penalty('শ', 'ষ'), 0.4);
    }

    #[test]
    fn test_borno_phonetic_proximity() {
        let bp = KeyProximity::borno_phonetic();

        // 1. Specifically requested phonetic pairs
        // ত | ট (t vs T)
        assert!(bp.are_neighbors('ত', 'ট'));
        assert!(bp.are_neighbors('ট', 'ত'));

        // ে | া (e vs a)
        assert!(bp.are_neighbors('ে', 'া'));
        assert!(bp.are_neighbors('া', 'ে'));

        // জ | য (j vs z)
        assert!(bp.are_neighbors('জ', 'য'));
        assert!(bp.are_neighbors('য', 'জ'));

        // ন | ণ (n vs N)
        assert!(bp.are_neighbors('ন', 'ণ'));
        assert!(bp.are_neighbors('ণ', 'ন'));

        // Other lowercase/uppercase pairs
        assert!(bp.are_neighbors('থ', 'ঠ')); // th vs Th
        assert!(bp.are_neighbors('দ', 'ড')); // d vs D
        assert!(bp.are_neighbors('ধ', 'ঢ')); // dh vs Dh
        assert!(bp.are_neighbors('শ', 'ষ')); // S vs Sh
        assert!(bp.are_neighbors('স', 'শ')); // s vs S
        assert!(bp.are_neighbors('র', 'ড়')); // r vs R

        // 2. QWERTY proximity typos projected into Borno Phonetic
        // 't' (ত/ট) is adjacent to 'r' (র) on QWERTY
        assert!(bp.are_neighbors('ত', 'র'));
        assert!(bp.are_neighbors('ট', 'র'));

        // 'e' (ে) is adjacent to 'r' (র) on QWERTY
        assert!(bp.are_neighbors('ে', 'র'));

        // 'j' (জ) is adjacent to 'h' (হ) on QWERTY
        assert!(bp.are_neighbors('জ', 'হ'));

        // 'n' (ন) is adjacent to 'b' (ব) on QWERTY
        assert!(bp.are_neighbors('ন', 'ব'));
    }

    #[test]
    fn test_for_layout_dispatch() {
        let borno = KeyProximity::for_layout("bn_borno");
        assert!(borno.are_neighbors('ত', 'ট'));

        let probhat = KeyProximity::for_layout("bn_probhat");
        assert!(probhat.are_neighbors('শ', 'ষ'));

        let qwerty = KeyProximity::for_layout("en_qwerty");
        assert!(qwerty.are_neighbors('q', 'w'));

        let tr_q = KeyProximity::for_layout("tr_q");
        assert!(tr_q.are_neighbors('ğ', 'ü'));
        assert!(tr_q.are_neighbors('ş', 'i'));
        assert!(tr_q.are_neighbors('ö', 'ç'));
        assert!(tr_q.is_case_pair('i', 'ı'));
        assert!(tr_q.is_case_pair('i', 'İ'));
        assert!(tr_q.is_case_pair('s', 'ş'));
        assert!(tr_q.is_case_pair('c', 'ç'));
        assert!(tr_q.is_case_pair('g', 'ğ'));
        assert!(tr_q.is_case_pair('o', 'ö'));
        assert!(tr_q.is_case_pair('u', 'ü'));

        let tr_f = KeyProximity::for_layout("tr_f");
        assert!(tr_f.are_neighbors('f', 'g'));
        assert!(tr_f.are_neighbors('g', 'ğ'));
        assert!(tr_f.are_neighbors('u', 'i'));
        assert!(tr_f.are_neighbors('j', 'ö'));
        assert!(tr_f.is_case_pair('i', 'ı'));
        assert!(tr_f.is_case_pair('i', 'İ'));
        assert!(tr_f.is_case_pair('g', 'ğ'));

        // Test dynamic registration for future layouts
        let mut custom = KeyProximity::new();
        custom.add_neighbor_bidirectional('x', 'y');
        KeyProximity::register_layout("future_custom_layout", custom);

        let resolved = KeyProximity::for_layout("future_custom_layout");
        assert!(resolved.are_neighbors('x', 'y'));
    }
}
