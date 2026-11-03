use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jfloat, jint, jstring};
use jni::JNIEnv;

pub mod dictionary;
pub mod gesture;
pub mod khipro;
pub mod m17n;
pub mod spatial;
pub mod translit;

use translit::borno_phonetic::BornoPhonetic;

#[no_mangle]
pub extern "system" fn Java_io_kiebord_feature_ime_NativeEngine_transliterateBorno(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
) -> jstring {
    let input_str: String = match env.get_string(&input) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    let result = BornoPhonetic::convert(&input_str);
    env.new_string(result).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_io_kiebord_feature_ime_NativeEngine_getSuggestions(
    mut env: JNIEnv,
    _class: JClass,
    prefix: JString,
    lang: JString,
    max_results: jint,
) -> jstring {
    let prefix_str: String = match env.get_string(&prefix) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };
    let lang_str: String = match env.get_string(&lang) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };

    let limit = if max_results <= 0 { 5 } else { max_results as usize };
    let result = dictionary::get_suggestions(&prefix_str, &lang_str, limit);
    env.new_string(result).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_io_kiebord_feature_ime_NativeEngine_predictNextWords(
    mut env: JNIEnv,
    _class: JClass,
    prev_word: JString,
    lang: JString,
    max_results: jint,
) -> jstring {
    let prev_str: String = match env.get_string(&prev_word) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };
    let lang_str: String = match env.get_string(&lang) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };

    let limit = if max_results <= 0 { 3 } else { max_results as usize };
    let result = dictionary::predict_next_words(&prev_str, &lang_str, limit);
    env.new_string(result).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_io_kiebord_feature_ime_NativeEngine_loadDictionary(
    mut env: JNIEnv,
    _class: JClass,
    lang: JString,
    path: JString,
) -> jboolean {
    let lang_str: String = match env.get_string(&lang) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let path_str: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    if dictionary::load_dictionary_path(&lang_str, std::path::Path::new(&path_str)) {
        1
    } else {
        0
    }
}

#[no_mangle]
pub extern "system" fn Java_io_kiebord_feature_ime_NativeEngine_registerLayoutProximityRows(
    mut env: JNIEnv,
    _class: JClass,
    layout_id: JString,
    rows: jni::objects::JObjectArray,
) -> jboolean {
    let id_str: String = match env.get_string(&layout_id) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let rows_len = match env.get_array_length(&rows) {
        Ok(l) => l as usize,
        Err(_) => return 0,
    };
    let mut row_strings: Vec<String> = Vec::with_capacity(rows_len);
    for i in 0..rows_len {
        if let Ok(obj) = env.get_object_array_element(&rows, i as jni::sys::jsize) {
            let jstr: JString = obj.into();
            let str_val = match env.get_string(&jstr) {
                Ok(js) => Some(String::from(js)),
                Err(_) => None,
            };
            if let Some(s) = str_val {
                row_strings.push(s);
            }
        }
    }
    let row_refs: Vec<&str> = row_strings.iter().map(|s| s.as_str()).collect();
    let prox = dictionary::proximity::KeyProximity::of_rows(&row_refs);
    dictionary::proximity::KeyProximity::register_layout(&id_str, prox);
    1
}

#[no_mangle]
pub extern "system" fn Java_io_kiebord_feature_ime_NativeEngine_decodeGesture(
    mut env: JNIEnv,
    _class: JClass,
    points_x: jni::objects::JFloatArray,
    points_y: jni::objects::JFloatArray,
    keys_chars: JString,
    keys_x: jni::objects::JFloatArray,
    keys_y: jni::objects::JFloatArray,
    key_width: jfloat,
    lang: JString,
    max_results: jint,
) -> jstring {
    let empty = env.new_string("").unwrap().into_raw();

    let px_len = match env.get_array_length(&points_x) {
        Ok(l) => l as usize,
        Err(_) => return empty,
    };
    let py_len = match env.get_array_length(&points_y) {
        Ok(l) => l as usize,
        Err(_) => return empty,
    };
    if px_len != py_len || px_len < 2 {
        return empty;
    }

    let mut xs = vec![0.0f32; px_len];
    let mut ys = vec![0.0f32; py_len];
    if env.get_float_array_region(&points_x, 0, &mut xs).is_err() || env.get_float_array_region(&points_y, 0, &mut ys).is_err() {
        return empty;
    }

    let points: Vec<gesture::decoder::GesturePoint> = xs.into_iter().zip(ys.into_iter()).map(|(x, y)| gesture::decoder::GesturePoint {
        x,
        y,
        timestamp_ms: 0,
    }).collect();

    let keys_str: String = match env.get_string(&keys_chars) {
        Ok(s) => s.into(),
        Err(_) => return empty,
    };
    let kx_len = match env.get_array_length(&keys_x) {
        Ok(l) => l as usize,
        Err(_) => return empty,
    };
    let ky_len = match env.get_array_length(&keys_y) {
        Ok(l) => l as usize,
        Err(_) => return empty,
    };
    let chars_vec: Vec<char> = keys_str.chars().collect();
    if chars_vec.len() != kx_len || kx_len != ky_len {
        return empty;
    }

    let mut kxs = vec![0.0f32; kx_len];
    let mut kys = vec![0.0f32; ky_len];
    if env.get_float_array_region(&keys_x, 0, &mut kxs).is_err() || env.get_float_array_region(&keys_y, 0, &mut kys).is_err() {
        return empty;
    }

    let keys: Vec<gesture::decoder::KeyCenter> = chars_vec.into_iter().zip(kxs.into_iter()).zip(kys.into_iter()).map(|((c, x), y)| gesture::decoder::KeyCenter {
        char_code: c,
        x,
        y,
    }).collect();

    let raw_lang_str: String = match env.get_string(&lang) {
        Ok(s) => s.into(),
        Err(_) => "en".to_string(),
    };

    let (lang_id, learned_words) = match raw_lang_str.split_once(';') {
        Some((l, words_str)) => {
            let list: Vec<String> = words_str
                .split('|')
                .map(|s| s.trim().to_string())
                .filter(|s| !s.is_empty())
                .collect();
            (l.to_string(), list)
        }
        None => (raw_lang_str, Vec::new()),
    };

    let translit_opt = translit::get_phonetic_transliterator(&lang_id);
    let (dict_opt, is_phonetic) = match translit_opt {
        Some(t) => {
            let dict = match t.target_dict_lang() {
                "bn" => dictionary::get_bn_binary_dict(),
                _ => dictionary::get_en_binary_dict(),
            };
            (dict, true)
        }
        None => {
            let dict = if lang_id.starts_with("bn") {
                dictionary::get_bn_binary_dict()
            } else {
                dictionary::get_en_binary_dict()
            };
            (dict, false)
        }
    };

    let dict = match dict_opt {
        Some(d) => d,
        None => return empty,
    };

    let is_bn_mode = is_phonetic || lang_id.starts_with("bn");
    let sanitized_learned: Vec<String> = if is_bn_mode {
        learned_words.into_iter().filter(|w| !w.chars().any(|c| c.is_ascii_alphabetic())).collect()
    } else {
        learned_words.into_iter().filter(|w| !w.chars().any(|c| ('\u{0980}'..='\u{09FF}').contains(&c))).collect()
    };

    let limit = if max_results <= 0 { 5 } else { max_results as usize };
    let candidates = if is_phonetic {
        let t = translit_opt.unwrap();
        gesture::decoder::GestureDecoder::decode_phonetic_with_learned(&points, &keys, key_width, dict, t, limit, &sanitized_learned)
    } else {
        gesture::decoder::GestureDecoder::decode_with_learned(&points, &keys, key_width, dict, limit, &sanitized_learned)
    };
    let mut words: Vec<String> = candidates.into_iter().map(|c| c.word).collect();
    if is_bn_mode {
        words.retain(|w| !w.chars().any(|c| c.is_ascii_alphabetic()));
    } else {
        words.retain(|w| !w.chars().any(|c| ('\u{0980}'..='\u{09FF}').contains(&c)));
    }
    let result = words.join("|");
    env.new_string(result).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_io_kiebord_feature_ime_NativeEngine_transliterate(
    mut env: JNIEnv,
    _class: JClass,
    layout_id: JString,
    input: JString,
) -> jstring {
    let layout: String = match env.get_string(&layout_id) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };
    let input_str: String = match env.get_string(&input) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    // 1. If this is an m17n layout, strictly parse via m17n engine.
    // It is NOT a phonetic layout and must never fall back to Borno/Avro phonetic.
    if m17n::is_m17n_layout(&layout) {
        let result = m17n::transliterate_m17n(&layout, &input_str).unwrap_or(input_str);
        return env.new_string(result).unwrap().into_raw();
    }

    if let Some(t) = translit::get_phonetic_transliterator(&layout) {
        let result = t.transliterate(&input_str);
        env.new_string(result).unwrap().into_raw()
    } else if translit::is_phonetic_layout(&layout) {
        let result = BornoPhonetic::convert(&input_str);
        env.new_string(result).unwrap().into_raw()
    } else {
        env.new_string(input_str).unwrap().into_raw()
    }
}

#[no_mangle]
pub extern "system" fn Java_io_kiebord_feature_ime_NativeEngine_isPhoneticLayout(
    mut env: JNIEnv,
    _class: JClass,
    layout_id: JString,
) -> jboolean {
    let layout: String = match env.get_string(&layout_id) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    if translit::is_phonetic_layout(&layout) { 1 } else { 0 }
}

#[no_mangle]
pub extern "system" fn Java_io_kiebord_feature_ime_NativeEngine_isM17nLayout(
    mut env: JNIEnv,
    _class: JClass,
    layout_id: JString,
) -> jboolean {
    let layout: String = match env.get_string(&layout_id) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    if m17n::is_m17n_layout(&layout) { 1 } else { 0 }
}

#[no_mangle]
pub extern "system" fn Java_io_kiebord_feature_ime_NativeEngine_loadM17nRules(
    mut env: JNIEnv,
    _class: JClass,
    layout_id: JString,
    rules_content: JString,
) -> jboolean {
    let id_str: String = match env.get_string(&layout_id) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let rules_str: String = match env.get_string(&rules_content) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    match m17n::register_m17n_layout(&id_str, &rules_str) {
        Ok(_) => 1,
        Err(_) => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_io_kiebord_feature_ime_NativeEngine_transliterateM17n(
    mut env: JNIEnv,
    _class: JClass,
    layout_id: JString,
    input: JString,
) -> jstring {
    let id_str: String = match env.get_string(&layout_id) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };
    let input_str: String = match env.get_string(&input) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    let result = m17n::transliterate_m17n(&id_str, &input_str).unwrap_or_else(|| input_str);
    env.new_string(result).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_io_kiebord_feature_ime_NativeEngine_getTransliterationAlternatives(
    mut env: JNIEnv,
    _class: JClass,
    layout_id: JString,
    raw_input: JString,
) -> jstring {
    let layout: String = match env.get_string(&layout_id) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };
    let input: String = match env.get_string(&raw_input) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };

    if layout == "bn_borno" {
        let alts = BornoPhonetic::suggest_transliterations(&input);
        let joined = alts.join("|");
        env.new_string(joined).unwrap().into_raw()
    } else {
        env.new_string("").unwrap().into_raw()
    }
}

#[no_mangle]
pub extern "system" fn Java_io_kiebord_feature_ime_NativeEngine_getDictionaryMetadata(
    mut env: JNIEnv,
    _class: JClass,
    lang_or_path: JString,
) -> jstring {
    let query: String = match env.get_string(&lang_or_path) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };
    let result = dictionary::get_dictionary_metadata(&query);
    env.new_string(result).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_io_kiebord_feature_ime_NativeEngine_isWord(
    mut env: JNIEnv,
    _class: JClass,
    word: JString,
    lang: JString,
) -> jboolean {
    let word_str: String = match env.get_string(&word) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let lang_str: String = match env.get_string(&lang) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    if dictionary::is_word(&word_str, &lang_str) {
        1
    } else {
        0
    }
}

#[no_mangle]
pub extern "system" fn Java_io_kiebord_feature_ime_NativeEngine_ping(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    1
}


// ---- Khipro (m17n spec interpreter) ----

fn khipro_variant(v: jint) -> khipro::Variant {
    if v == khipro::Variant::Dark as jint {
        khipro::Variant::Dark
    } else {
        khipro::Variant::Classic
    }
}

#[no_mangle]
pub extern "system" fn Java_io_kiebord_feature_ime_NativeEngine_khiproLoadSpec(
    mut env: JNIEnv,
    _class: JClass,
    variant: jint,
    mim_text: JString,
) -> jboolean {
    let mim: String = match env.get_string(&mim_text) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    match khipro::load_spec(khipro_variant(variant), &mim) {
        Ok(()) => 1,
        Err(_) => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_io_kiebord_feature_ime_NativeEngine_khiproConvert(
    mut env: JNIEnv,
    _class: JClass,
    variant: jint,
    buffer: JString,
) -> jstring {
    let buffer: String = match env.get_string(&buffer) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };
    let result = khipro::convert(khipro_variant(variant), &buffer);
    env.new_string(result).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_io_kiebord_feature_ime_NativeEngine_khiproLoaded(
    _env: JNIEnv,
    _class: JClass,
    variant: jint,
) -> jboolean {
    if khipro::is_loaded(khipro_variant(variant)) { 1 } else { 0 }
}

#[no_mangle]
pub extern "system" fn Java_io_kiebord_feature_ime_NativeEngine_khiproReset(
    _env: JNIEnv,
    _class: JClass,
    variant: jint,
) {
    khipro::reset(khipro_variant(variant));
}

/// OTA gate: replays `tsv_text` through a scratch engine built from
/// `mim_text`. Returns "" when only known-gap rows differ, else a report of
/// the first unexpected failures. Never touches the live registry.
#[no_mangle]
pub extern "system" fn Java_io_kiebord_feature_ime_NativeEngine_khiproValidateSpec(
    mut env: JNIEnv,
    _class: JClass,
    variant: jint,
    mim_text: JString,
    tsv_text: JString,
) -> jstring {
    let _ = variant; // the scratch engine carries its own spec; kept for API symmetry
    let mim: String = match env.get_string(&mim_text) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("unreadable mim").unwrap().into_raw(),
    };
    let tsv: String = match env.get_string(&tsv_text) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("unreadable tsv").unwrap().into_raw(),
    };
    let report = match khipro::conformance::run_tsv_detailed(&mim, &tsv) {
        Ok(failures) => {
            let unexpected: Vec<_> = failures
                .iter()
                .filter(|f| !khipro::conformance::KNOWN_GAP_INPUTS.contains(&f.input.as_str()))
                .collect();
            if unexpected.is_empty() {
                String::new()
            } else {
                let f = &unexpected[0];
                format!(
                    "{} failed: first input={:?} expected={:?} got={:?}",
                    unexpected.len(),
                    f.input,
                    f.expected,
                    f.got
                )
            }
        }
        Err(e) => e,
    };
    env.new_string(report).unwrap().into_raw()
}
