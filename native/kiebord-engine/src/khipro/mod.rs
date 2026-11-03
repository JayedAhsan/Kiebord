//! Khipro compositional input method: m17n spec interpreter.
//!
//! The registry holds one engine per variant ([`Variant::Classic`] and
//! [`Variant::Dark`]). A spec is parsed and built outside the lock and only
//! then swapped in, so a live variant is never observable half-built; OTA
//! validation builds throwaway [`engine::KhiproEngine`] instances directly
//! and never touches this registry.
pub mod conformance;
pub mod engine;
pub mod sexp;
pub mod spec;

use std::sync::{OnceLock, RwLock};

use engine::KhiproEngine;

/// The two bundled Khipro layouts. JNI passes these as ints.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Variant {
    Classic = 0,
    Dark = 1,
}

impl Variant {
    fn slot(self) -> usize {
        match self {
            Variant::Classic => 0,
            Variant::Dark => 1,
        }
    }
}

fn engines() -> &'static [RwLock<Option<KhiproEngine>>; 2] {
    static ENGINES: OnceLock<[RwLock<Option<KhiproEngine>>; 2]> = OnceLock::new();
    ENGINES.get_or_init(|| [RwLock::new(None), RwLock::new(None)])
}

/// Parses and builds `mim_text` into an engine and atomically swaps it in
/// for `v`. On a parse error nothing changes.
pub fn load_spec(v: Variant, mim_text: &str) -> Result<(), String> {
    let forms = sexp::parse(mim_text)?;
    let spec = spec::build_spec(&forms)?;
    let engine = KhiproEngine::new(spec);
    let mut slot = engines()[v.slot()]
        .write()
        .map_err(|_| "registry lock poisoned".to_string())?;
    *slot = Some(engine);
    Ok(())
}

/// Whether a spec is loaded for `v`.
pub fn is_loaded(v: Variant) -> bool {
    engines()[v.slot()]
        .read()
        .map(|s| s.is_some())
        .unwrap_or(false)
}

/// Whole-buffer conversion with the spec loaded for `v`; the input passes
/// through unchanged when nothing is loaded. Takes the write lock (the
/// engine mutates its tape during conversion).
pub fn convert(v: Variant, buffer: &str) -> String {
    let mut slot = match engines()[v.slot()].write() {
        Ok(s) => s,
        Err(_) => return buffer.to_string(),
    };
    match slot.as_mut() {
        Some(engine) => engine.convert(buffer),
        None => buffer.to_string(),
    }
}

/// Unloads `v`; convert falls back to passthrough until the next load.
pub fn reset(v: Variant) {
    if let Ok(mut slot) = engines()[v.slot()].write() {
        *slot = None;
    }
}

#[cfg(test)]
mod registry_tests {
    use super::*;

    /// The registry is process-global; serialize mutating tests.
    fn registry_lock() -> std::sync::MutexGuard<'static, ()> {
        static LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());
        LOCK.lock().unwrap_or_else(|e| e.into_inner())
    }

    const MINI_A: &str = r#"
        (map (byanjon ("k" "ক") ("s" "শ")) (shor ("a" "আ")) (slicer ("/" (commit))))
        (state (init (byanjon (shift byanjon-state)) (shor (shift init)) (slicer (commit))))
    "#;
    const MINI_B: &str = r#"
        (map (byanjon ("k" "KO") ("s" "SO")) (shor ("a" "AO")) (slicer ("/" (commit))))
        (state (init (byanjon (shift byanjon-state)) (shor (shift init)) (slicer (commit))))
    "#;

    #[test]
    fn convert_before_load_is_passthrough() {
        let _guard = registry_lock();
        reset(Variant::Classic);
        reset(Variant::Dark);
        reset(Variant::Dark);
        assert_eq!(convert(Variant::Dark, "ks"), "ks");
        assert!(!is_loaded(Variant::Dark));
    }

    #[test]
    fn load_then_convert_and_hot_reload() {
        let _guard = registry_lock();
        reset(Variant::Classic);
        reset(Variant::Dark);
        load_spec(Variant::Classic, MINI_A).unwrap();
        assert!(is_loaded(Variant::Classic));
        assert_eq!(convert(Variant::Classic, "ks/"), "কশ");
        load_spec(Variant::Classic, MINI_B).unwrap();
        assert_eq!(convert(Variant::Classic, "ks/"), "KOSO");
        assert_eq!(convert(Variant::Classic, "ka"), "KOAO");
        reset(Variant::Classic);
        assert!(!is_loaded(Variant::Classic));
        assert_eq!(convert(Variant::Classic, "ks"), "ks");
    }

    #[test]
    fn variants_are_independent() {
        let _guard = registry_lock();
        reset(Variant::Classic);
        reset(Variant::Dark);
        load_spec(Variant::Classic, MINI_A).unwrap();
        reset(Variant::Dark);
        assert_eq!(convert(Variant::Classic, "ks"), "কশ");
        assert_eq!(convert(Variant::Dark, "ks"), "ks");
    }

    #[test]
    fn bad_spec_is_rejected_and_keeps_current() {
        let _guard = registry_lock();
        reset(Variant::Classic);
        reset(Variant::Dark);
        load_spec(Variant::Classic, MINI_A).unwrap();
        assert!(load_spec(Variant::Classic, "(unknownform x)").is_err());
        assert!(is_loaded(Variant::Classic));
        assert_eq!(convert(Variant::Classic, "ks"), "কশ");
    }
}
