//! Conformance runner: replays KhiproTeam's TSV test tables through the
//! engine. Table format: header `input<TAB>output`, then one case per line —
//! feed the input string key by key; expected output is the final visible
//! buffer (committed + preedit).

use super::engine::KhiproEngine;
use super::spec::build_spec;

#[derive(Debug, PartialEq)]
pub struct Failure {
    pub input: String,
    pub expected: String,
    pub got: String,
}

/// Rows the upstream spec and the reference engine both disagree on
/// (verified: khipro-library's own conformance binary fails these same
/// rows). The gate treats them as known gaps; anything else fails.
pub const KNOWN_GAP_INPUTS: &[&str] = &["........"];

/// Replays every row of `tsv` through a fresh engine built from `spec_mim`.
/// Returns the failing rows, or a parse/setup error.
pub fn run_tsv_detailed(spec_mim: &str, tsv: &str) -> Result<Vec<Failure>, String> {
    let forms = super::sexp::parse(spec_mim)?;
    let spec = build_spec(&forms)?;
    let mut failures = Vec::new();
    for row in tsv_rows(tsv)? {
        let mut engine = KhiproEngine::new(spec.clone());
        let got = engine.convert(&row.input);
        if got != row.output {
            failures.push(Failure { input: row.input, expected: row.output, got });
        }
    }
    Ok(failures)
}

/// Row count convenience wrapper used by the OTA gate.
pub fn run_tsv(spec_mim: &str, tsv: &str) -> Result<usize, String> {
    Ok(run_tsv_detailed(spec_mim, tsv)?.len())
}

struct Row {
    input: String,
    output: String,
}

fn tsv_rows(tsv: &str) -> Result<Vec<Row>, String> {
    let mut rows = Vec::new();
    for (i, line) in tsv.lines().enumerate() {
        let line = line.trim_end_matches('\r');
        if line.is_empty() {
            continue;
        }
        let mut cells = line.splitn(2, '\t');
        let input = cells.next().unwrap_or_default();
        let output = cells.next().unwrap_or_default();
        if i == 0 && input == "input" {
            continue; // header
        }
        rows.push(Row { input: input.to_string(), output: output.to_string() });
    }
    Ok(rows)
}

/// Where the bundled spec data lives (single copy, shared with the app).
pub(crate) const ASSET_DIR: &str = concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../feature/ime/src/main/assets/khipro/"
);

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reports_mismatching_row() {
        let spec = "(map (shor (\"a\" \"আ\"))) (state (init (shor (shift init))))";
        let tsv = "input\toutput\na\tআ\naa\tআআ\nab\tWRONG\n";
        let failures = run_tsv_detailed(spec, tsv).unwrap();
        assert_eq!(failures.len(), 1);
        assert_eq!(failures[0].input, "ab");
        assert_eq!(failures[0].expected, "WRONG");
        assert_eq!(failures[0].got, "আb");
    }

    #[test]
    fn classic_touch_spec_passes_conformance() {
        let mim = std::fs::read_to_string(format!("{ASSET_DIR}bn-khipro-classic.mim")).unwrap();
        let tsv = std::fs::read_to_string(format!("{ASSET_DIR}khipro-testcases-touch.tsv")).unwrap();
        let failures = run_tsv_detailed(&mim, &tsv).unwrap();
        assert_known_gaps(&failures);
    }

    #[test]
    fn dark_touch_spec_passes_conformance() {
        let mim = std::fs::read_to_string(format!("{ASSET_DIR}bn-khipro-dark.mim")).unwrap();
        let tsv = std::fs::read_to_string(format!("{ASSET_DIR}khipro-testcases-dark-touch.tsv")).unwrap();
        let failures = run_tsv_detailed(&mim, &tsv).unwrap();
        assert_known_gaps(&failures);
    }

    fn assert_known_gaps(failures: &[Failure]) {
        let unexpected: Vec<&Failure> = failures
            .iter()
            .filter(|f| !KNOWN_GAP_INPUTS.contains(&f.input.as_str()))
            .collect();
        assert!(
            unexpected.is_empty(),
            "{} unexpected conformance failures (known gaps excluded): {:?}",
            unexpected.len(),
            &unexpected[..unexpected.len().min(5)]
        );
    }
}
