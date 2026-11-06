pub mod binary_dict;
pub mod combined_parser;
pub mod proximity;

use binary_dict::BinaryDict;
use combined_parser::CombinedParser;
use proximity::KeyProximity;
use std::path::Path;
use std::sync::OnceLock;

static EN_PROXIMITY: OnceLock<KeyProximity> = OnceLock::new();
static BN_PROXIMITY: OnceLock<KeyProximity> = OnceLock::new();
static TR_PROXIMITY: OnceLock<KeyProximity> = OnceLock::new();

pub fn get_en_proximity() -> &'static KeyProximity {
    EN_PROXIMITY.get_or_init(KeyProximity::qwerty)
}

pub fn get_bn_proximity() -> &'static KeyProximity {
    BN_PROXIMITY.get_or_init(KeyProximity::bengali)
}

pub fn get_tr_proximity() -> &'static KeyProximity {
    TR_PROXIMITY.get_or_init(KeyProximity::turkish_q)
}

static EN_BINARY_DICT: OnceLock<BinaryDict<'static>> = OnceLock::new();
static BN_BINARY_DICT: OnceLock<BinaryDict<'static>> = OnceLock::new();
static TR_BINARY_DICT: OnceLock<BinaryDict<'static>> = OnceLock::new();

/// Loads a compiled .dict file from disk via zero-copy memory mapping.
pub fn load_dictionary_path(lang: &str, path: &Path) -> bool {
    let file = match std::fs::File::open(path) {
        Ok(f) => f,
        Err(_) => return false,
    };
    let mmap = match unsafe { memmap2::Mmap::map(&file) } {
        Ok(m) => m,
        Err(_) => return false,
    };
    let mmap_ref: &'static memmap2::Mmap = Box::leak(Box::new(mmap));
    let static_slice: &'static [u8] = &mmap_ref[..];
    if let Ok(dict) = BinaryDict::from_bytes(static_slice) {
        match lang {
            "bn" | "bn_borno" | "bn_probhat" | "bn_national" => {
                let _ = BN_BINARY_DICT.set(dict);
                true
            }
            "tr" | "tr_q" | "tr_q_compact" | "tr_f" => {
                let _ = TR_BINARY_DICT.set(dict);
                true
            }
            _ => {
                if lang.starts_with("bn") {
                    let _ = BN_BINARY_DICT.set(dict);
                } else if lang.starts_with("tr") {
                    let _ = TR_BINARY_DICT.set(dict);
                } else {
                    let _ = EN_BINARY_DICT.set(dict);
                }
                true
            }
        }
    } else {
        false
    }
}

pub fn get_en_binary_dict() -> Option<&'static BinaryDict<'static>> {
    if let Some(d) = EN_BINARY_DICT.get() {
        return Some(d);
    }
    let candidates = [
        "../../feature/ime/src/main/assets/dictionaries/en.dict",
        "feature/ime/src/main/assets/dictionaries/en.dict",
        "assets/dictionaries/en.dict",
    ];
    for p in candidates {
        let path = Path::new(p);
        if path.exists() {
            if load_dictionary_path("en", path) {
                return EN_BINARY_DICT.get();
            }
        }
    }
    None
}

pub fn get_bn_binary_dict() -> Option<&'static BinaryDict<'static>> {
    if let Some(d) = BN_BINARY_DICT.get() {
        return Some(d);
    }
    let candidates = [
        "../../feature/ime/src/main/assets/dictionaries/bn.dict",
        "feature/ime/src/main/assets/dictionaries/bn.dict",
        "assets/dictionaries/bn.dict",
    ];
    for p in candidates {
        let path = Path::new(p);
        if path.exists() {
            if load_dictionary_path("bn", path) {
                return BN_BINARY_DICT.get();
            }
        }
    }
    None
}

pub fn get_tr_binary_dict() -> Option<&'static BinaryDict<'static>> {
    if let Some(d) = TR_BINARY_DICT.get() {
        return Some(d);
    }
    let candidates = [
        "../../feature/ime/src/main/assets/dictionaries/tr.dict",
        "feature/ime/src/main/assets/dictionaries/tr.dict",
        "assets/dictionaries/tr.dict",
    ];
    for p in candidates {
        let path = Path::new(p);
        if path.exists() {
            if load_dictionary_path("tr", path) {
                return TR_BINARY_DICT.get();
            }
        }
    }
    None
}

/// Compiles a `.combined` file into a binary `.dict` file.
pub fn compile_combined_file(src_path: &Path, out_path: &Path) -> std::io::Result<()> {
    let content = std::fs::read_to_string(src_path)?;
    let parsed = CombinedParser::parse_str(&content);
    let bytes = BinaryDict::compile_combined(&parsed);

    if let Some(parent) = out_path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    std::fs::write(out_path, bytes)?;
    Ok(())
}

/// Returns dictionary metadata and word count formatted as `name=...;author=...;version=...;date=...;license=...;words=...;`.
pub fn get_dictionary_metadata(lang_or_path: &str) -> String {
    let trimmed = lang_or_path.trim();
    if trimmed.ends_with(".dict") || trimmed.contains('/') || trimmed.contains('\\') {
        if let Ok((meta, word_count)) = BinaryDict::read_file_metadata(Path::new(trimmed)) {
            return format!("{}words={};", meta.to_header_string(), word_count);
        }
        return String::new();
    }

    let dict_opt = if trimmed.starts_with("bn") {
        get_bn_binary_dict()
    } else if trimmed.starts_with("tr") {
        get_tr_binary_dict()
    } else {
        get_en_binary_dict()
    };

    if let Some(dict) = dict_opt {
        format!("{}words={};", dict.metadata().to_header_string(), dict.word_count())
    } else {
        String::new()
    }
}

/// Generates .dict binary files directly from .combined sources.
pub fn generate_dict_assets(combined_dir: &Path, out_dir: &Path) -> std::io::Result<()> {
    std::fs::create_dir_all(out_dir)?;

    let en_src = combined_dir.join("en.combined");
    let en_out = out_dir.join("en.dict");
    compile_combined_file(&en_src, &en_out)?;

    let bn_src = combined_dir.join("bn.combined");
    let bn_out = out_dir.join("bn.dict");
    compile_combined_file(&bn_src, &bn_out)?;

    let tr_src = combined_dir.join("tr.combined");
    if tr_src.exists() {
        let tr_out = out_dir.join("tr.dict");
        compile_combined_file(&tr_src, &tr_out)?;
    }

    Ok(())
}

/// Main entry point for query suggestions: exact completions + proximity typo corrections.
/// Returns a pipe-delimited string of suggestions, e.g. "the|that|this"
pub fn get_suggestions(input: &str, lang_or_layout: &str, max_results: usize) -> String {
    let is_bn = lang_or_layout.starts_with("bn");
    let is_tr = lang_or_layout.starts_with("tr");
    let dict_opt = if is_bn {
        get_bn_binary_dict()
    } else if is_tr {
        get_tr_binary_dict()
    } else {
        get_en_binary_dict()
    };
    let prox = KeyProximity::for_layout(lang_or_layout);

    if let Some(dict) = dict_opt {
        let mut words = dict.suggest(input, max_results, prox);

        // If Bengali layout, check for Reph substitution (র + consonant -> র্ + consonant)
        if is_bn && input.contains('\u{09B0}') {
            let input_chars: Vec<char> = input.chars().collect();
            for i in 0..input_chars.len() {
                if input_chars[i] == '\u{09B0}' && i + 1 < input_chars.len() {
                    let next = input_chars[i + 1];
                    if crate::translit::borno_phonetic::BornoPhonetic::is_bengali_consonant(next) {
                        let mut reph_chars = input_chars.clone();
                        reph_chars.insert(i + 1, '\u{09CD}'); // র + ্ = র্ (Reph)
                        let reph_word: String = reph_chars.into_iter().collect();
                        if dict.contains_word(&reph_word) && !words.contains(&reph_word) {
                            words.insert(0, reph_word);
                            if words.len() > max_results {
                                words.truncate(max_results);
                            }
                        }
                    }
                }
            }
        }

        // Bengali Dental na vs Retroflex na (ন <-> ণ) substitution
        if is_bn && (input.contains('\u{09A8}') || input.contains('\u{09A3}')) {
            let n_alt: String = input
                .chars()
                .map(|c| match c {
                    '\u{09A8}' => '\u{09A3}', // ন -> ণ
                    '\u{09A3}' => '\u{09A8}', // ণ -> ন
                    other => other,
                })
                .collect();
            if dict.contains_word(&n_alt) && !words.contains(&n_alt) {
                let insert_pos = if !dict.contains_word(input) { 0 } else if words.len() > 1 { 1 } else { words.len() };
                words.insert(insert_pos, n_alt);
                if words.len() > max_results {
                    words.truncate(max_results);
                }
            }
        }

        let mut kar_normalized_stems: Vec<String> = Vec::new();
        if is_bn {
            let mut ou_candidates: Vec<String> = Vec::new();
            let input_chars: Vec<char> = input.chars().collect();

            let mut direct_ou_bases: Vec<String> = Vec::new();

            if input.contains("\u{09CB}\u{0989}") {
                let norm = input.replace("\u{09CB}\u{0989}", "\u{09CC}");
                ou_candidates.push(norm.clone());
                direct_ou_bases.push(norm.clone());
                kar_normalized_stems.push(norm);
            }

            for i in 0..input_chars.len() {
                if crate::translit::borno_phonetic::BornoPhonetic::is_bengali_consonant(input_chars[i])
                    && i + 1 < input_chars.len()
                    && (input_chars[i + 1] == '\u{0989}' || input_chars[i + 1] == '\u{098A}')
                {
                    let mut cand_chars = input_chars.clone();
                    cand_chars[i + 1] = '\u{09CC}';
                    let base_ou: String = cand_chars.into_iter().collect();
                    ou_candidates.push(base_ou.clone());
                    direct_ou_bases.push(base_ou.clone());
                    if !kar_normalized_stems.contains(&base_ou) {
                        kar_normalized_stems.push(base_ou.clone());
                    }
                    if base_ou.contains('\u{099C}') {
                        ou_candidates.push(base_ou.replace('\u{099C}', "\u{09AF}"));
                    }
                    if base_ou.contains('\u{09B0}') {
                        ou_candidates.push(base_ou.replace('\u{09B0}', "\u{09DC}"));
                    }
                    if base_ou.contains('\u{09C1}') {
                        ou_candidates.push(base_ou.replace('\u{09C1}', "\u{09C2}"));
                    }
                }
            }

            if input.contains('\u{09CC}') {
                ou_candidates.push(input.replace('\u{09CC}', "\u{0989}"));
            }

            for init_pat in ["\u{0985}\u{0989}", "\u{0993}\u{0989}", "\u{0985}\u{098A}"] {
                if input.starts_with(init_pat) {
                    let rest = &input[init_pat.len()..];
                    let base_init = format!("\u{0994}{}", rest);
                    ou_candidates.push(base_init.clone());
                    direct_ou_bases.push(base_init.clone());
                    kar_normalized_stems.push(base_init);
                    if rest.contains('\u{09B6}') {
                        ou_candidates.push(format!("\u{0994}{}", rest.replace('\u{09B6}', "\u{09B7}")));
                    }
                    if rest.contains('\u{09B8}') {
                        ou_candidates.push(format!("\u{0994}{}", rest.replace('\u{09B8}', "\u{09B7}")));
                    }
                }
            }

            for cand in ou_candidates {
                if dict.contains_word(&cand) && !words.contains(&cand) {
                    let insert_pos = if !dict.contains_word(input) { 0 } else if words.len() > 1 { 1 } else { words.len() };
                    words.insert(insert_pos, cand);
                    if words.len() > max_results {
                        words.truncate(max_results);
                    }
                }
            }
            for base in direct_ou_bases {
                if !words.contains(&base) {
                    let insert_pos = if !dict.contains_word(input) && dict.find_node(&base).is_some() {
                        0
                    } else if words.len() > 1 {
                        1
                    } else {
                        words.len()
                    };
                    words.insert(insert_pos, base);
                    if words.len() > max_results {
                        words.truncate(max_results);
                    }
                }
            }
        }

        if is_bn {
            let mut oi_candidates: Vec<String> = Vec::new();
            let mut direct_oi_bases: Vec<String> = Vec::new();
            let input_chars: Vec<char> = input.chars().collect();

            if input.contains("\u{09CB}\u{0987}") {
                let norm = input.replace("\u{09CB}\u{0987}", "\u{09C8}");
                oi_candidates.push(norm.clone());
                direct_oi_bases.push(norm.clone());
                kar_normalized_stems.push(norm);
            }

            for i in 0..input_chars.len() {
                if crate::translit::borno_phonetic::BornoPhonetic::is_bengali_consonant(input_chars[i])
                    && i + 1 < input_chars.len()
                    && (input_chars[i + 1] == '\u{0987}' || input_chars[i + 1] == '\u{0988}')
                {
                    let mut cand_chars = input_chars.clone();
                    cand_chars[i + 1] = '\u{09C8}';
                    let base_oi: String = cand_chars.into_iter().collect();
                    oi_candidates.push(base_oi.clone());
                    direct_oi_bases.push(base_oi.clone());
                    if !kar_normalized_stems.contains(&base_oi) {
                        kar_normalized_stems.push(base_oi.clone());
                    }
                    for pat in ["\u{09B6}\u{099F}", "\u{09B6}\u{09CD}\u{09A4}", "\u{09B8}\u{09CD}\u{09A4}", "\u{09B8}\u{09CD}\u{099F}"] {
                        if base_oi.contains(pat) {
                            oi_candidates.push(base_oi.replace(pat, "\u{09B7}\u{09CD}\u{099F}"));
                        }
                    }
                    if base_oi.contains('\u{09B8}') {
                        oi_candidates.push(base_oi.replace('\u{09B8}', "\u{09B6}"));
                    }
                    if base_oi.contains('\u{09B6}') {
                        oi_candidates.push(base_oi.replace('\u{09B6}', "\u{09B8}"));
                    }
                }
            }

            if input.contains('\u{09C8}') {
                oi_candidates.push(input.replace('\u{09C8}', "\u{0987}"));
            }

            for init_pat in ["\u{0985}\u{0987}", "\u{0993}\u{0987}", "\u{0985}\u{0988}"] {
                if input.starts_with(init_pat) {
                    let rest = &input[init_pat.len()..];
                    let base_init = format!("\u{0990}{}", rest);
                    oi_candidates.push(base_init.clone());
                    direct_oi_bases.push(base_init.clone());
                    kar_normalized_stems.push(base_init);
                    if rest.contains('\u{09B6}') {
                        oi_candidates.push(format!("\u{0990}{}", rest.replace('\u{09B6}', "\u{09B8}")));
                    }
                    if rest.contains('\u{09B8}') {
                        oi_candidates.push(format!("\u{0990}{}", rest.replace('\u{09B8}', "\u{09B6}")));
                    }
                }
            }

            for cand in oi_candidates {
                if dict.contains_word(&cand) && !words.contains(&cand) {
                    let insert_pos = if !dict.contains_word(input) { 0 } else if words.len() > 1 { 1 } else { words.len() };
                    words.insert(insert_pos, cand);
                    if words.len() > max_results {
                        words.truncate(max_results);
                    }
                }
            }
            for base in direct_oi_bases {
                if !words.contains(&base) {
                    let insert_pos = if !dict.contains_word(input) && dict.find_node(&base).is_some() {
                        0
                    } else if words.len() > 1 {
                        1
                    } else {
                        words.len()
                    };
                    words.insert(insert_pos, base);
                    if words.len() > max_results {
                        words.truncate(max_results);
                    }
                }
            }
        }

        // Bengali o <-> O (ো-কার) substitution: e.g. যাব -> যাবো, যাবনা -> যাবোনা
        if is_bn {
            let input_chars: Vec<char> = input.chars().collect();
            let mut okar_candidates: Vec<String> = Vec::new();

            // 1. Append ো-কার to ending consonant (e.g. যাব -> যাবো, করব -> করবো)
            if let Some(&last_char) = input_chars.last() {
                if crate::translit::borno_phonetic::BornoPhonetic::is_bengali_consonant(last_char) {
                    let mut cand = input.to_string();
                    cand.push('\u{09CB}'); // ো
                    okar_candidates.push(cand);
                }
            }

            // 2. Insert ো-কার after non-final consonant without vowel sign (e.g. যাবনা -> যাবোনা)
            for i in 0..input_chars.len() {
                let c = input_chars[i];
                if crate::translit::borno_phonetic::BornoPhonetic::is_bengali_consonant(c) && i + 1 < input_chars.len() {
                    let next = input_chars[i + 1];
                    // If next is consonant, insert ো
                    if crate::translit::borno_phonetic::BornoPhonetic::is_bengali_consonant(next) {
                        let mut cand_chars = input_chars.clone();
                        cand_chars.insert(i + 1, '\u{09CB}');
                        let single_okar: String = cand_chars.into_iter().collect();
                        if let Some(&last_c) = input_chars.last() {
                            if crate::translit::borno_phonetic::BornoPhonetic::is_bengali_consonant(last_c) {
                                let mut double_okar = single_okar.clone();
                                double_okar.push('\u{09CB}');
                                okar_candidates.push(double_okar);
                            }
                        }
                        okar_candidates.push(single_okar);
                    }
                }
            }

            if input.starts_with('\u{0985}') {
                let rest = &input['\u{0985}'.len_utf8()..];
                okar_candidates.push(format!("\u{0993}{}", rest));
                if rest.contains('\u{09B6}') {
                    okar_candidates.push(format!("\u{0993}{}", rest.replace('\u{09B6}', "\u{09B7}")));
                }
            } else if input.starts_with('\u{0993}') {
                let rest = &input['\u{0993}'.len_utf8()..];
                okar_candidates.push(format!("\u{0985}{}", rest));
            }

            // 3. Remove ো-কার if present (e.g. যাবো -> যাব)
            if input.contains('\u{09CB}') {
                let cand: String = input.chars().filter(|&c| c != '\u{09CB}').collect();
                okar_candidates.push(cand);
            }

            for cand in okar_candidates {
                if dict.contains_word(&cand) && !words.contains(&cand) {
                    let insert_pos = if !dict.contains_word(input) { 0 } else if words.len() > 1 { 1 } else { words.len() };
                    words.insert(insert_pos, cand);
                    if words.len() > max_results {
                        words.truncate(max_results);
                    }
                }
            }
        }

        // Bengali ri <-> rri (র ফলা + ি-কার <-> ঋ-কার / ঋ) substitution
        if is_bn {
            let mut ri_candidates: Vec<String> = Vec::new();

            // র ফলা + ি-কার (্ + র + ি) -> ঋ-কার (ৃ) e.g. ক্রি -> কৃ, ম্রিত -> মৃত
            if input.contains("\u{09CD}\u{09B0}\u{09BF}") {
                let base_rri = input.replace("\u{09CD}\u{09B0}\u{09BF}", "\u{09C3}");
                ri_candidates.push(base_rri.clone());
                kar_normalized_stems.push(base_rri.clone());

                if base_rri.contains('\u{09B6}') {
                    ri_candidates.push(base_rri.replace('\u{09B6}', "\u{09B7}"));
                }
                if base_rri.contains('\u{09A8}') {
                    ri_candidates.push(base_rri.replace('\u{09A8}', "\u{09A3}"));
                }
                for pat in ["\u{09B6}\u{099F}", "\u{09B6}\u{09CD}\u{09A4}", "\u{09B8}\u{09CD}\u{09A4}", "\u{09B8}\u{09CD}\u{099F}"] {
                    if base_rri.contains(pat) {
                        ri_candidates.push(base_rri.replace(pat, "\u{09B7}\u{09CD}\u{099F}"));
                    }
                }
                for pat in ["\u{09B6}\u{09A8}", "\u{09B6}\u{09CD}\u{09A8}", "\u{09B8}\u{09CD}\u{09A8}"] {
                    if base_rri.contains(pat) {
                        ri_candidates.push(base_rri.replace(pat, "\u{09B7}\u{09CD}\u{09A3}"));
                    }
                }
                if base_rri.ends_with('\u{09BF}') {
                    let without_i = &base_rri[..base_rri.len() - '\u{09BF}'.len_utf8()];
                    ri_candidates.push(format!("{}\u{09C0}", without_i));
                }
                if base_rri.starts_with('\u{0986}') {
                    let rest = &base_rri['\u{0986}'.len_utf8()..];
                    ri_candidates.push(format!("\u{0985}{}", rest));
                }
            }
            // ঋ-কার (ৃ) -> র ফলা + ি-কার (্ + র + ি)
            if input.contains('\u{09C3}') {
                ri_candidates.push(input.replace('\u{09C3}', "\u{09CD}\u{09B0}\u{09BF}"));
            }

            // Word-initial রি (র + ি) -> ঋ e.g. রিশি -> ঋষি, রিন -> ঋণ, রিতু -> ঋতু
            if input.starts_with("\u{09B0}\u{09BF}") {
                let rest = &input["\u{09B0}\u{09BF}".len()..];
                let base_init = format!("\u{098B}{}", rest);
                ri_candidates.push(base_init.clone());
                kar_normalized_stems.push(base_init);
                // Combined with শ -> ষ (e.g. রিশি -> ঋষি)
                if rest.contains('\u{09B6}') {
                    ri_candidates.push(format!("\u{098B}{}", rest.replace('\u{09B6}', "\u{09B7}")));
                }
                // Combined with ন -> ণ (e.g. রিন -> ঋণ)
                if rest.contains('\u{09A8}') {
                    ri_candidates.push(format!("\u{098B}{}", rest.replace('\u{09A8}', "\u{09A3}")));
                }
            }
            // Word-initial ঋ -> রি (র + ি)
            if input.starts_with('\u{098B}') {
                let rest = &input['\u{098B}'.len_utf8()..];
                ri_candidates.push(format!("\u{09B0}\u{09BF}{}", rest));
            }

            for cand in ri_candidates {
                if dict.contains_word(&cand) && !words.contains(&cand) {
                    let insert_pos = if !dict.contains_word(input) { 0 } else if words.len() > 1 { 1 } else { words.len() };
                    words.insert(insert_pos, cand);
                    if words.len() > max_results {
                        words.truncate(max_results);
                    }
                }
            }
        }

        if is_bn && !kar_normalized_stems.is_empty() {
            for stem in kar_normalized_stems {
                let stem_sugs = dict.suggest(&stem, max_results, prox);
                for sug in stem_sugs {
                    if !words.contains(&sug) {
                        if words.len() < max_results {
                            words.push(sug);
                        } else {
                            break;
                        }
                    }
                }
            }
        }

        // If Borno Phonetic layout and room remains, also evaluate phonetic transliteration alternatives
        if words.len() < max_results && lang_or_layout == "bn_borno" {
            let alts = crate::translit::borno_phonetic::BornoPhonetic::suggest_transliterations(input);
            for alt in alts {
                if alt != input && !words.contains(&alt) && dict.contains_word(&alt) {
                    words.push(alt);
                    if words.len() >= max_results {
                        break;
                    }
                }
            }
        }

        // Strict language separation: purge cross-language words
        if is_bn {
            words.retain(|w| !w.chars().any(|c| c.is_ascii_alphabetic()));
        } else {
            words.retain(|w| !w.chars().any(|c| ('\u{0980}'..='\u{09FF}').contains(&c)));
        }

        words.join("|")
    } else {
        String::new()
    }
}

/// Checks whether `word` exists as an exact terminal word in the specified dictionary.
pub fn is_word(word: &str, lang_or_layout: &str) -> bool {
    let trimmed = word.trim();
    if trimmed.is_empty() {
        return false;
    }
    let is_bn = lang_or_layout.starts_with("bn");
    let is_tr = lang_or_layout.starts_with("tr");
    let dict_opt = if is_bn {
        get_bn_binary_dict()
    } else if is_tr {
        get_tr_binary_dict()
    } else {
        get_en_binary_dict()
    };
    match dict_opt {
        Some(dict) => dict.contains_word(trimmed),
        None => false,
    }
}

/// Main entry point for next-word prediction based on previous committed word.
/// Returns a pipe-delimited string of predicted words, e.g. "are|is|do"
pub fn predict_next_words(prev_word: &str, lang_or_layout: &str, max_results: usize) -> String {
    let is_bn = lang_or_layout.starts_with("bn");
    let is_tr = lang_or_layout.starts_with("tr");
    let dict_opt = if is_bn {
        get_bn_binary_dict()
    } else if is_tr {
        get_tr_binary_dict()
    } else {
        get_en_binary_dict()
    };

    if let Some(dict) = dict_opt {
        let mut words = dict.predict_next(prev_word, max_results);
        if is_bn {
            words.retain(|w| !w.chars().any(|c| c.is_ascii_alphabetic()));
        } else {
            words.retain(|w| !w.chars().any(|c| ('\u{0980}'..='\u{09FF}').contains(&c)));
        }
        words.join("|")
    } else {
        String::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_en_exact_and_proximity() {
        let dict = get_en_binary_dict().expect("dict loaded");
        let prox = get_en_proximity();

        // Exact prefix
        let th_sug = dict.suggest("th", 5, prox);
        assert!(!th_sug.is_empty());
        assert_eq!(th_sug[0], "the");

        // Typo: neighbor key slip ('g' instead of 'h' in 'gelo' -> 'hello')
        let gelo_sug = dict.suggest("gelo", 10, prox);
        assert!(!gelo_sug.is_empty());
        assert!(gelo_sug.contains(&"hello".to_string()));

        // Typo: omission ('helo' -> 'hello')
        let helo_sug = dict.suggest("helo", 5, prox);
        assert!(!helo_sug.is_empty());
        assert!(helo_sug.contains(&"hello".to_string()));

        // Typo: transposition ('hte' -> 'the')
        let hte_sug = dict.suggest("hte", 3, prox);
        assert!(!hte_sug.is_empty());
        assert_eq!(hte_sug[0], "the");
    }

    #[test]
    fn test_suggestions_ranked_by_frequency() {
        let dict = get_en_binary_dict().expect("en dict loaded");
        let prox = get_en_proximity();

        // If nothing is typed (empty input), suggest returns empty
        let empty_sug = dict.suggest("", 5, prox);
        assert!(empty_sug.is_empty());

        // Exact prefix suggestions for "th" must be sorted with highest frequency first
        let th_sug = dict.suggest("th", 5, prox);
        assert_eq!(th_sug[0], "the"); // "the" has max frequency 255
    }

    #[test]
    fn test_next_word_prediction() {
        // English bigrams
        let en_dict = get_en_binary_dict().expect("en dict loaded");
        assert!(en_dict.contains_word("thank"));
        assert!(en_dict.contains_word("you"));
        let after_thank = predict_next_words("thank", "en", 3);
        assert!(!after_thank.is_empty());
        let thank_items: Vec<&str> = after_thank.split('|').collect();
        assert_eq!(thank_items[0], "you");
        for word in &thank_items {
            assert!(en_dict.contains_word(word), "Predicted English word '{}' must exist in dict", word);
        }

        let after_how = predict_next_words("how", "en", 3);
        assert!(!after_how.is_empty());

        // Bengali bigrams from .combined corpus format
        let bn_dict = get_bn_binary_dict().expect("bn dict loaded");

        // Helper closure to assert next words and verify that every predicted word actually exists in bn.dict
        let assert_bn_prediction = |prev_word: &str, expected_words: &[&str]| {
            assert!(
                bn_dict.contains_word(prev_word),
                "Head word '{}' from .combined must actually exist in dictionary",
                prev_word
            );
            let raw = predict_next_words(prev_word, "bn", expected_words.len());
            assert!(!raw.is_empty(), "Predictions for '{}' should not be empty", prev_word);
            let items: Vec<&str> = raw.split('|').collect();
            for word in &items {
                assert!(
                    bn_dict.contains_word(word),
                    "Predicted Bengali word '{}' following '{}' must actually exist in dictionary",
                    word,
                    prev_word
                );
            }
            assert_eq!(items, expected_words, "Next word prediction mismatch for '{}'", prev_word);
        };

        // 1. আমাদের -> এই, আগ্রহের, দেশে
        assert_bn_prediction("আমাদের", &["এই", "আগ্রহের", "দেশে"]);

        // 2. অনেক -> সময়, বেশি, ব্যবহারকারী
        assert_bn_prediction("অনেক", &["সময়", "বেশি", "ব্যবহারকারী"]);

        // 3. অত্যন্ত -> ইতিবাচক, জরুরি, ফজিলতপূর্ণ
        assert_bn_prediction("অত্যন্ত", &["ইতিবাচক", "জরুরি", "ফজিলতপূর্ণ"]);

        // 4. আমি -> এই, একেবারে, এখন
        assert_bn_prediction("আমি", &["এই", "একেবারে", "এখন"]);

        // 5. তুমি -> কি, অনেক, কখন
        assert_bn_prediction("তুমি", &["কি", "অনেক", "কখন"]);

        // 6. তিনি -> বলেন, নিজেই, বললেন
        assert_bn_prediction("তিনি", &["বলেন", "নিজেই", "বললেন"]);

        // 7. ভালো -> আছি, কাজের, খারাপ
        assert_bn_prediction("ভালো", &["আছি", "কাজের", "খারাপ"]);
    }


#[test]
fn test_iphone_casing() {
    let dict = get_en_binary_dict().expect("dict loaded");
    let prox = get_en_proximity();
    let res = dict.suggest("iphone", 3, prox);
    println!("iphone suggestions: {:?}", res);
    assert_eq!(res[0], "iPhone");
    assert!(dict.contains_word("iphone"));
    assert!(dict.contains_word("iPhone"));
    assert_eq!(dict.word_freq("iphone"), 168);
}

    #[test]
    fn test_en_casing() {
        let dict = get_en_binary_dict().expect("dict loaded");
        let prox = get_en_proximity();

        let title_sug = dict.suggest("Th", 3, prox);
        assert_eq!(title_sug[0], "The");

        let upper_sug = dict.suggest("TH", 3, prox);
        assert_eq!(upper_sug[0], "THE");

        let jayed_sug = dict.suggest("jayed", 5, prox);
        assert_eq!(jayed_sug[0], "Jayed");

        let jayed_title = dict.suggest("Jayed", 5, prox);
        assert_eq!(jayed_title[0], "Jayed");

        let jayed_upper = dict.suggest("JAYED", 5, prox);
        assert_eq!(jayed_upper[0], "JAYED");

        assert!(dict.contains_word("jayed"));
        assert!(dict.contains_word("Jayed"));
        assert_eq!(dict.word_freq("jayed"), 110);
        assert_eq!(dict.word_freq("Jayed"), 110);
    }

    #[test]
    fn test_bn_proximity_and_typos() {
        let dict = get_bn_binary_dict().expect("dict loaded");
        let prox = get_bn_proximity();

        // Exact prefix
        let bn_sug = dict.suggest("আম", 10, prox);
        assert!(!bn_sug.is_empty());
        assert!(bn_sug.contains(&"আমি".to_string()));

        // Typo: missing kar 'আমদের' -> 'আমাদের'
        let typo_sug = dict.suggest("আমদের", 3, prox);
        assert!(!typo_sug.is_empty());
        assert_eq!(typo_sug[0], "আমাদের");
    }

    #[test]
    fn test_pipe_delimited_output() {
        let en_pipe = get_suggestions("hte", "en", 3);
        let items: Vec<&str> = en_pipe.split('|').collect();
        assert_eq!(items[0], "the");

        let bn_pipe = get_suggestions("আম", "bn", 5);
        assert!(!bn_pipe.is_empty());
        let bn_items: Vec<&str> = bn_pipe.split('|').collect();
        assert!(bn_items.contains(&"আমি") || bn_items.contains(&"আম"));
    }

    #[test]
    fn test_borno_phonetic_layout_suggestions() {
        // 'টুমি' (typed with uppercase T) should suggest 'তুমি' (with lowercase t) in Borno Phonetic layout
        let tumi_sugs = get_suggestions("টুমি", "bn_borno", 5);
        let items: Vec<&str> = tumi_sugs.split('|').filter(|s| !s.is_empty()).collect();
        assert!(items.contains(&"তুমি"), "Expected 'তুমি' in suggestions for 'টুমি': {:?}", items);

        let litar_sugs = get_suggestions("লিতার", "bn_borno", 5);
        let lt_items: Vec<&str> = litar_sugs.split('|').filter(|s| !s.is_empty()).collect();
        assert_eq!(lt_items.first(), Some(&"লিটার"), "Expected 'লিটার' as top substitution suggestion for 'লিতার': {:?}", lt_items);

        // 'কারন' (typed with lowercase n) should suggest 'কারণ' (with uppercase N) in Borno Phonetic layout
        let karon_sugs = get_suggestions("কারন", "bn_borno", 5);
        let k_items: Vec<&str> = karon_sugs.split('|').filter(|s| !s.is_empty()).collect();
        assert!(k_items.contains(&"কারণ"), "Expected 'কারণ' in suggestions for 'কারন': {:?}", k_items);

        // "করম" (typed as kormo) should suggest "কর্ম" via Reph substitution
        let kormo_sugs = get_suggestions("করম", "bn_borno", 5);
        let km_items: Vec<&str> = kormo_sugs.split('|').filter(|s| !s.is_empty()).collect();
        assert!(km_items.contains(&"কর্ম"), "Expected 'কর্ম' in suggestions for 'করম': {:?}", km_items);

        // "যাব" (typed as jabo) should suggest "যাবো" via O-kar substitution
        let jabo_sugs = get_suggestions("যাব", "bn_borno", 5);
        let j_items: Vec<&str> = jabo_sugs.split('|').filter(|s| !s.is_empty()).collect();
        assert!(j_items.contains(&"যাবো"), "Expected 'যাবো' in suggestions for 'যাব': {:?}", j_items);

        // "যাবনা" (typed as jabona) should suggest "যাবোনা" via O-kar insertion
        let jabona_sugs = get_suggestions("যাবনা", "bn_borno", 5);
        let jn_items: Vec<&str> = jabona_sugs.split('|').filter(|s| !s.is_empty()).collect();
        assert!(jn_items.contains(&"যাবোনা"), "Expected 'যাবোনা' in suggestions for 'যাবনা': {:?}", jn_items);

        // "রিশি" (typed as rishi) should suggest "ঋষি" via ri -> rri (ঋ-kar) substitution
        let rishi_sugs = get_suggestions("রিশি", "bn_borno", 5);
        let rs_items: Vec<&str> = rishi_sugs.split('|').filter(|s| !s.is_empty()).collect();
        assert!(rs_items.contains(&"ঋষি"), "Expected 'ঋষি' in suggestions for 'রিশি': {:?}", rs_items);

        // "রিন" (typed as rin) should suggest "ঋণ" via ri -> rri and n -> N substitution
        let rin_sugs = get_suggestions("রিন", "bn_borno", 5);
        let rn_items: Vec<&str> = rin_sugs.split('|').filter(|s| !s.is_empty()).collect();
        assert!(rn_items.contains(&"ঋণ"), "Expected 'ঋণ' in suggestions for 'রিন': {:?}", rn_items);

        // "ম্রিত" (typed as mrit) should suggest "মৃত" via ri -> rri substitution
        let mrit_sugs = get_suggestions("ম্রিত", "bn_borno", 5);
        let mr_items: Vec<&str> = mrit_sugs.split('|').filter(|s| !s.is_empty()).collect();
        assert!(mr_items.contains(&"মৃত"), "Expected 'মৃত' in suggestions for 'ম্রিত': {:?}", mr_items);

        let nouka_sugs = get_suggestions("নউকা", "bn_borno", 5);
        let nk_items: Vec<&str> = nouka_sugs.split('|').filter(|s| !s.is_empty()).collect();
        assert!(nk_items.contains(&"নৌকা"), "Expected 'নৌকা' in suggestions for 'নউকা': {:?}", nk_items);

        let boikal_sugs = get_suggestions("বইকাল", "bn_borno", 5);
        let bk_items: Vec<&str> = boikal_sugs.split('|').filter(|s| !s.is_empty()).collect();
        assert!(bk_items.contains(&"বৈকাল"), "Expected 'বৈকাল' in suggestions for 'বইকাল': {:?}", bk_items);

        let toiri_sugs = get_suggestions("তইরি", "bn_borno", 5);
        let tr_items: Vec<&str> = toiri_sugs.split('|').filter(|s| !s.is_empty()).collect();
        assert!(tr_items.contains(&"তৈরি"), "Expected 'তৈরি' in suggestions for 'তইরি': {:?}", tr_items);

        let proyojon_sugs = get_suggestions("প্রয়জন", "bn_borno", 5);
        let pj_items: Vec<&str> = proyojon_sugs.split('|').filter(|s| !s.is_empty()).collect();
        assert!(pj_items.contains(&"প্রয়োজন"), "Expected 'প্রয়োজন' in suggestions for 'প্রয়জন': {:?}", pj_items);

        let brishti_sugs = get_suggestions("ব্রিশটি", "bn_borno", 5);
        let br_items: Vec<&str> = brishti_sugs.split('|').filter(|s| !s.is_empty()).collect();
        assert!(br_items.contains(&"বৃষ্টি"), "Expected 'বৃষ্টি' in suggestions for 'ব্রিশটি': {:?}", br_items);

        let prithibi_sugs = get_suggestions("প্রিথিবি", "bn_borno", 5);
        let pr_items: Vec<&str> = prithibi_sugs.split('|').filter(|s| !s.is_empty()).collect();
        assert!(pr_items.contains(&"পৃথিবী"), "Expected 'পৃথিবী' in suggestions for 'প্রিথিবি': {:?}", pr_items);
    }

    #[test]
    fn test_contractions_without_apostrophe() {
        let dict = get_en_binary_dict().expect("dict loaded");
        let prox = get_en_proximity();

        // "hell" should suggest both "hell" and "he'll" right at the top
        let hell_sugs = dict.suggest("hell", 5, prox);
        assert!(hell_sugs.contains(&"he'll".to_string()), "Expected 'he'll' in suggestions for 'hell': {:?}", hell_sugs);
        assert!(hell_sugs.contains(&"hell".to_string()), "Expected 'hell' in suggestions for 'hell': {:?}", hell_sugs);
        // he'll should be within top 2
        let pos_hell = hell_sugs.iter().position(|w| w == "he'll").unwrap();
        assert!(pos_hell <= 1, "'he'll' should be top 2, got index {}", pos_hell);

        // "well" should suggest both "well" and "we'll" right at the top
        let well_sugs = dict.suggest("well", 5, prox);
        assert!(well_sugs.contains(&"we'll".to_string()), "Expected 'we'll' in suggestions for 'well': {:?}", well_sugs);
        assert!(well_sugs.contains(&"well".to_string()), "Expected 'well' in suggestions for 'well': {:?}", well_sugs);
        let pos_well = well_sugs.iter().position(|w| w == "we'll").unwrap();
        assert!(pos_well <= 1, "'we'll' should be top 2, got index {}", pos_well);

        // "cant" should suggest "can't" as #1
        let cant_sugs = dict.suggest("cant", 5, prox);
        assert_eq!(cant_sugs[0], "can't");

        // "dont" should suggest "don't" as #1
        let dont_sugs = dict.suggest("dont", 5, prox);
        assert_eq!(dont_sugs[0], "don't");

        // "wont" should suggest "won't" as #1
        let wont_sugs = dict.suggest("wont", 5, prox);
        assert_eq!(wont_sugs[0], "won't");
    }

    #[test]
    fn test_dictionary_metadata_roundtrip_and_assets() {
        let sample = "name=Kiebord Bangla Word Dictionary;author=Jayed Ahsan Saad;version=3;date=1761681600;license=Only for Kiebord;\nword=বাংলা,f=200\nword=ভাষা,f=180\n";
        let parsed = CombinedParser::parse_str(sample);
        let bytes = BinaryDict::compile_combined(&parsed);
        let dict = BinaryDict::from_bytes(&bytes).expect("valid compiled dict");
        let meta = dict.metadata();
        assert_eq!(meta.name, "Kiebord Bangla Word Dictionary");
        assert_eq!(meta.author, "Jayed Ahsan Saad");
        assert_eq!(meta.version, 3);
        assert_eq!(meta.date, 1761681600);
        assert_eq!(meta.license, "Only for Kiebord");
        assert_eq!(dict.word_count(), 2);

        let bn_meta_str = get_dictionary_metadata("bn");
        assert!(bn_meta_str.contains("name=Kiebord Bangla Word Dictionary;"), "bn meta: {}", bn_meta_str);
        assert!(bn_meta_str.contains("author=Jayed Ahsan Saad;"), "bn meta: {}", bn_meta_str);
        assert!(bn_meta_str.contains("license=Only for Kiebord;"), "bn meta: {}", bn_meta_str);

        let en_meta_str = get_dictionary_metadata("en");
        assert!(en_meta_str.contains("name=Kiebord English Word Dictionary;"), "en meta: {}", en_meta_str);
        assert!(en_meta_str.contains("author=Jayed Ahsan Saad;"), "en meta: {}", en_meta_str);
        assert!(en_meta_str.contains("license=Only for Kiebord;"), "en meta: {}", en_meta_str);
    }

    #[test]
    fn test_turkish_dictionary_and_diacritic_suggestions() {
        let tr_dict = get_tr_binary_dict().expect("tr dict loaded");
        let prox = get_tr_proximity();

        assert!(tr_dict.contains_word("merhaba"));
        assert!(tr_dict.contains_word("teşekkürler"));
        assert!(tr_dict.contains_word("güzel"));
        assert!(tr_dict.contains_word("türkçe"));

        // Diacritic-insensitive typo matching (e.g. typing "tesekkur" or "guzel" on compact keyboard)
        let guzel_sugs = tr_dict.suggest("guzel", 5, prox);
        assert!(guzel_sugs.contains(&"güzel".to_string()), "Expected 'güzel' in suggestions for 'guzel': {:?}", guzel_sugs);

        let cok_sugs = tr_dict.suggest("cok", 5, prox);
        assert!(cok_sugs.contains(&"çok".to_string()), "Expected 'çok' in suggestions for 'cok': {:?}", cok_sugs);

        // Turkish dotted İ title-case and uppercase formatting
        let ist_title = tr_dict.suggest("İst", 5, prox);
        assert!(ist_title.iter().any(|w| w.starts_with('İ')), "Expected title case starting with 'İ': {:?}", ist_title);

        let iyi_upper = tr_dict.suggest("İY", 5, prox);
        assert!(iyi_upper.contains(&"İYİ".to_string()), "Expected 'İYİ' in uppercase suggestions: {:?}", iyi_upper);

        // Next word prediction in Turkish
        let after_te = predict_next_words("teşekkür", "tr", 3);
        let items: Vec<&str> = after_te.split('|').filter(|s| !s.is_empty()).collect();
        assert_eq!(items.first(), Some(&"ederim"));
    }
}
