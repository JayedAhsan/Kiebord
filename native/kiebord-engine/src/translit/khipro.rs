//! Khipro transliterators: the m17n spec interpreter behind the existing
//! `PhoneticTransliterator` trait, so the IME's transliteration path,
//! suggestion pipeline and layout-id dispatch treat Khipro like any other
//! phonetic layout.

use std::sync::OnceLock;

use crate::khipro;
use crate::translit::traits::PhoneticTransliterator;

pub struct KhiproTransliterator(khipro::Variant);

impl PhoneticTransliterator for KhiproTransliterator {
    fn id(&self) -> &'static str {
        match self.0 {
            khipro::Variant::Classic => "bn_khipro_classic",
            khipro::Variant::Dark => "bn_khipro_dark",
        }
    }

    fn target_dict_lang(&self) -> &'static str {
        "bn"
    }

    fn transliterate(&self, input: &str) -> String {
        khipro::convert(self.0, input)
    }

    /// Gestures are not enabled for Khipro layouts (isQwertyBasedLayout
    /// gate on the IME side); this returns the roman word as a placeholder.
    fn to_phonetic_keys(&self, word: &str) -> Vec<Vec<char>> {
        vec![word.to_lowercase().chars().collect()]
    }
}

pub fn get_classic() -> &'static dyn PhoneticTransliterator {
    static CLASSIC: OnceLock<KhiproTransliterator> = OnceLock::new();
    CLASSIC.get_or_init(|| KhiproTransliterator(khipro::Variant::Classic))
}

pub fn get_dark() -> &'static dyn PhoneticTransliterator {
    static DARK: OnceLock<KhiproTransliterator> = OnceLock::new();
    DARK.get_or_init(|| KhiproTransliterator(khipro::Variant::Dark))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::translit::traits::{get_phonetic_transliterator, is_phonetic_layout};

    #[test]
    fn ids_and_dict_langs_match_registration() {
        let classic = get_phonetic_transliterator("bn_khipro_classic").unwrap();
        let dark = get_phonetic_transliterator("bn_khipro_dark").unwrap();
        assert_eq!(classic.id(), "bn_khipro_classic");
        assert_eq!(classic.target_dict_lang(), "bn");
        assert_eq!(dark.id(), "bn_khipro_dark");
        assert_eq!(dark.target_dict_lang(), "bn");
    }

    #[test]
    fn khipro_ids_are_phonetic_layouts() {
        assert!(is_phonetic_layout("bn_khipro_classic"));
        assert!(is_phonetic_layout("bn_khipro_dark"));
        // existing layouts unaffected
        assert!(is_phonetic_layout("bn_borno"));
        assert!(!is_phonetic_layout("bn_probhat"));
    }

    #[test]
    fn transliterate_is_passthrough_when_unloaded() {
        khipro::reset(khipro::Variant::Dark);
        let dark = get_phonetic_transliterator("bn_khipro_dark").unwrap();
        assert_eq!(dark.transliterate("hello"), "hello");
    }
}
