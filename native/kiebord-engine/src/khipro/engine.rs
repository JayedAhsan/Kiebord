//! The Khipro runtime: whole-string conversion over an m17n-style spec.
//!
//! Model (verified against upstream conformance tables):
//! - Walk the input; at each position fire the LONGEST map sequence that
//!   matches, across every class rule of the current state. No pending
//!   deferral — the lookahead is the longest-match itself.
//! - Before a winning sequence runs, the shorter prefixes' `Set` actions
//!   leak in first (m17n incremental semantics: eager shorter keys roll
//!   back their text but persist their variables).
//! - A key no rule of the current state handles restarts composition in
//!   init; only in init does a key fall through as a literal char.
//! - `(t ...)` rules are state entry actions (run once at init start and on
//!   every restart into init).
//! - `(undo)` deletes one char before the cursor.

use std::collections::HashMap;

use super::spec::{Action, Cond, Spec, Target, Value};

/// The control character the specs treat as the backspace key.
pub const BACKSPACE: char = '\u{0008}';

pub struct KhiproEngine {
    spec: Spec,
    tape: Vec<char>,
    cursor: usize,
    state: Option<String>,
    vars: HashMap<String, i64>,
    marks: HashMap<String, usize>,
    /// `committed_len` chars of the tape are committed; the rest is preedit.
    committed_len: usize,
    /// Guard against cyclic `(shift ...)` chains in state entry actions of
    /// untrusted (OTA-downloaded) specs.
    entry_depth: usize,
}

impl KhiproEngine {
    pub fn new(spec: Spec) -> Self {
        Self {
            spec,
            tape: Vec::new(),
            cursor: 0,
            state: None,
            vars: HashMap::new(),
            marks: HashMap::new(),
            committed_len: 0,
            entry_depth: 0,
        }
    }

    /// Fresh conversion of a whole buffer.
    pub fn convert(&mut self, buffer: &str) -> String {
        self.reset();
        let input: Vec<char> = buffer.chars().collect();
        let mut state = String::from("init");
        self.state = Some(state.clone());
        self.run_entry_actions(&state);

        let mut i = 0usize;
        while i < input.len() {
            // Shift actions run inside the previous match; always continue
            // from the live state, never a stale local copy.
            state = self.state.clone().unwrap_or_else(|| "init".to_string());
            let best = self.find_best_match(&state, &input, i);
            let Some((map_rule, rule_actions)) = best else {
                if state != "init" {
                    // Restart composition in init and re-process this key.
                    state = "init".to_string();
                    self.state = Some(state.clone());
                    self.run_entry_actions(&state);
                    continue;
                }
                self.insert_at_cursor(&input[i].to_string());
                i += 1;
                continue;
            };

            self.apply_prefix_leaked_sets(&state, &map_rule.seq, &input, i);
            self.run(&map_rule.actions);
            self.run(&rule_actions);
            i += map_rule.seq.len();
        }
        self.visible()
    }

    /// Whole committed + preedit text.
    pub fn visible(&self) -> String {
        self.tape.iter().collect()
    }

    /// Clears the tape, cursor, state, variables and marks.
    pub fn reset(&mut self) {
        self.tape.clear();
        self.cursor = 0;
        self.state = None;
        self.vars.clear();
        self.marks.clear();
        self.committed_len = 0;
        self.entry_depth = 0;
    }

    /// A variable's current value, for conformance diagnostics.
    pub fn var(&self, name: &str) -> i64 {
        self.vars.get(name).copied().unwrap_or(0)
    }

    /// Current state name, for conformance diagnostics.
    pub fn state_debug(&self) -> String {
        self.state.clone().unwrap_or_else(|| "init".to_string())
    }

    /// Longest map sequence matching `input[i..]` across the state's rules.
    fn find_best_match(
        &self,
        state: &str,
        input: &[char],
        i: usize,
    ) -> Option<(super::spec::MapRule, Vec<Action>)> {
        let def = self.spec.states.get(state)?;
        let mut best: Option<(super::spec::MapRule, Vec<Action>)> = None;
        for rule in &def.rules {
            let map_rules = self.spec.maps.get(&rule.class);
            let Some(map_rules) = map_rules else { continue };
            for mr in map_rules {
                if mr.seq.len() <= best.as_ref().map_or(0, |(b, _)| b.seq.len()) {
                    continue;
                }
                if i + mr.seq.len() <= input.len() && input[i..i + mr.seq.len()] == mr.seq[..] {
                    best = Some((mr.clone(), rule.actions.clone()));
                }
            }
        }
        best
    }

    /// Before a winning sequence runs, replay the `Set` actions of every
    /// shorter prefix key of the same state (their eager firings rolled back
    /// their text but persisted variables in real m17n).
    fn apply_prefix_leaked_sets(&mut self, state: &str, seq: &[char], input: &[char], i: usize) {
        for plen in 1..seq.len() {
            if i + plen > input.len() || input[i..i + plen] != seq[..plen] {
                continue;
            }
            let Some(def) = self.spec.states.get(state) else { continue };
            let mut leaked: Vec<Vec<Action>> = Vec::new();
            for rule in &def.rules {
                let Some(map_rules) = self.spec.maps.get(&rule.class) else { continue };
                for mr in map_rules {
                    if mr.seq.len() == plen && mr.seq[..] == input[i..i + plen] {
                        leaked.push(mr.actions.clone());
                    }
                }
            }
            for actions in leaked {
                self.apply_sets_only(&actions);
            }
        }
    }

    /// Runs only the variable-affecting parts of `actions` (Sets, and Sets
    /// reachable through the first true cond branches), mirroring the leak.
    fn apply_sets_only(&mut self, actions: &[Action]) {
        for action in actions {
            match action {
                Action::Set(name, value) => {
                    let v = match value {
                        Value::Num(n) => *n,
                        Value::Var(var) => self.vars.get(var).copied().unwrap_or(0),
                    };
                    self.vars.insert(name.clone(), v);
                }
                Action::Cond(branches) => {
                    for (cond, branch_actions) in branches {
                        if self.eval_cond(cond) {
                            self.apply_sets_only(branch_actions);
                            break;
                        }
                    }
                }
                _ => {}
            }
        }
    }

    fn run_entry_actions(&mut self, state: &str) {
        if self.entry_depth >= 32 {
            return;
        }
        self.entry_depth += 1;
        if let Some(def) = self.spec.states.get(state) {
            let entry = def.entry_actions.clone();
            self.run(&entry);
        }
        self.entry_depth -= 1;
    }

    fn insert_at_cursor(&mut self, text: &str) {
        let chars: Vec<char> = text.chars().collect();
        let n = chars.len();
        let at = self.cursor.min(self.tape.len());
        self.tape.splice(at..at, chars);
        self.cursor = at + n;
        // Marks strictly after the insertion point shift forward; a mark
        // AT the cursor stays (editor-bookmark semantics).
        for m in self.marks.values_mut() {
            if *m > at {
                *m += n;
            }
        }
    }

    fn delete_range(&mut self, from: usize, to: usize) {
        let (from, to) = (from.min(to), to.max(from));
        if to > self.tape.len() || from >= to {
            return;
        }
        self.tape.drain(from..to);
        let len = to - from;
        for m in self.marks.values_mut() {
            if *m >= to {
                *m -= len;
            } else if *m > from {
                *m = from;
            }
        }
    }

    fn run(&mut self, actions: &[Action]) {
        for action in actions {
            self.run_one(action);
        }
    }

    fn run_one(&mut self, action: &Action) {
        match action {
            Action::Insert(s) => self.insert_at_cursor(s),
            Action::Set(name, value) => {
                let v = match value {
                    Value::Num(n) => *n,
                    Value::Var(var) => self.vars.get(var).copied().unwrap_or(0),
                };
                self.vars.insert(name.clone(), v);
            }
            Action::Delete(Target::CursorBack(n)) => {
                let n = (*n).min(self.cursor);
                let start = self.cursor - n;
                self.delete_range(start, self.cursor);
                self.cursor = start;
            }
            Action::Delete(Target::Named(mark)) => {
                if let Some(&m) = self.marks.get(mark) {
                    let (from, to) = (m.min(self.cursor), m.max(self.cursor));
                    self.delete_range(from, to);
                    self.cursor = from;
                }
            }
            Action::Delete(Target::End) => {
                self.delete_range(self.cursor, self.tape.len());
            }
            Action::Move(Target::CursorBack(n)) => {
                self.cursor = self.cursor.saturating_sub(*n);
            }
            Action::Move(Target::Named(mark)) => {
                if let Some(&m) = self.marks.get(mark) {
                    self.cursor = m.min(self.tape.len());
                }
            }
            Action::Move(Target::End) => self.cursor = self.tape.len(),
            Action::Mark(name) => {
                self.marks.insert(name.clone(), self.cursor);
            }
            Action::Shift(s) => {
                self.state = Some(s.clone());
                // Entering a state runs its `(t ...)` entry actions.
                self.run_entry_actions(s);
            }
            Action::Commit => self.committed_len = self.tape.len(),
            Action::Undo => {
                if self.cursor > 0 && !self.tape.is_empty() {
                    self.cursor -= 1;
                    self.tape.remove(self.cursor);
                }
            }
            Action::Backspace => {
                if self.cursor > 0 {
                    self.cursor -= 1;
                    self.tape.remove(self.cursor);
                }
            }
            Action::Cond(branches) => {
                for (cond, actions) in branches {
                    if self.eval_cond(cond) {
                        self.run(actions);
                        break;
                    }
                }
            }
        }
    }

    fn eval_cond(&self, cond: &Cond) -> bool {
        let val = |v: &Value| match v {
            Value::Num(n) => *n,
            Value::Var(var) => self.vars.get(var).copied().unwrap_or(0),
        };
        match cond {
            Cond::True => true,
            Cond::Eq(a, b) => val(a) == val(b),
            Cond::Lt(a, b) => val(a) < val(b),
            Cond::Gt(a, b) => val(a) > val(b),
            Cond::And(subs) => subs.iter().all(|c| self.eval_cond(c)),
            Cond::Or(subs) => subs.iter().any(|c| self.eval_cond(c)),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::super::spec::build_spec;
    use super::*;

    fn engine(mim: &str) -> KhiproEngine {
        let forms = super::super::sexp::parse(mim).unwrap();
        KhiproEngine::new(build_spec(&forms).unwrap())
    }

    const CONJUNCT_SPEC: &str = r#"
        (input-method bn test)
        (map
            (byanjon
                ("k" (set CID 1) "ক")
                ("kh" (set CID 2) "খ")
                ("s" (set CID 3)
                    (cond ((> PCID 0) (insert "্")))
                    "স")
            )
            (shor
                ("a" "আ")
                ("i" "ই")
            )
            (slicer ("/" (commit)))
        )
        (state
            (init
                (t (set PCID 0))
                (byanjon (set PCID CID) (shift byanjon-state))
                (shor (shift init))
                (slicer (commit))
            )
            (byanjon-state
                (byanjon
                    (set PCID CID)
                    (cond ((= PCID 0) (shift init)))
                )
                (shor (set PCID 0) (shift init))
                (slicer (commit) (shift init))
            )
        )
    "#;

    #[test]
    fn longest_sequence_wins() {
        let mut e = engine(CONJUNCT_SPEC);
        assert_eq!(e.convert("kh"), "খ");
        assert_eq!(e.convert("k"), "ক");
    }

    #[test]
    fn shorter_prefix_fires_when_longer_cannot_complete() {
        let mut e = engine(CONJUNCT_SPEC);
        assert_eq!(e.convert("ka"), "কআ");
    }

    #[test]
    fn state_shift_and_cond_flow() {
        let mut e = engine(CONJUNCT_SPEC);
        assert_eq!(e.convert("ks"), "ক্স");
    }

    #[test]
    fn commit_action_moves_preedit_to_committed() {
        let mut e = engine(CONJUNCT_SPEC);
        e.convert("k/");
        assert_eq!(e.visible(), "ক");
    }

    #[test]
    fn entry_action_shift_cycle_terminates() {
        // A hostile/broken spec whose state entry actions shift each other
        // forever must not hang or overflow — the depth cap ends it.
        let spec = r#"
            (map (shor ("a" "আ")))
            (state
                (init (shor (shift a-state)))
                (a-state (t (shift b-state)) (shor (shift a-state)))
                (b-state (t (shift a-state)))
            )
        "#;
        let mut e = engine(spec);
        assert_eq!(e.convert("a"), "আ");
    }

    #[test]
    fn unmatched_key_passes_through() {
        let mut e = engine(CONJUNCT_SPEC);
        assert_eq!(e.convert("A"), "A");
    }

    #[test]
    fn unmatched_key_restarts_composition_in_init() {
        // byanjon_state handles only shor; 'z' matches neither there, so
        // composition restarts in init — which handles z as a literal.
        let spec = r#"
            (map
                (byanjon ("k" "ক"))
                (shor ("a" "আ"))
            )
            (state
                (init (byanjon (shift byanjon-state)) (shor (shift init)))
                (byanjon-state (shor (shift init)))
            )
        "#;
        let mut e = engine(spec);
        assert_eq!(e.convert("kaz"), "কআz");
    }

    #[test]
    fn undo_reverts_one_char() {
        let mut e = engine(
            " (map (shor (\"a\" \"আ\") ((BackSpace) (undo)))) (state (init (shor (shift init)))) ",
        );
        assert_eq!(e.convert("a\u{0008}"), "");
    }

    #[test]
    fn mark_and_named_delete_splice_range() {
        let spec = r#"
            (map
                (byanjon ("k" "ক"))
                (shor ("x" (mark cstm1) (insert "XY") (move @-) (delete cstm1)))
            )
            (state
                (init (byanjon (shift init)) (shor (shift init)))
            )
        "#;
        let mut e = engine(spec);
        assert_eq!(e.convert("kx"), "কY");
    }

    #[test]
    fn convert_is_idempotent() {
        let mut e = engine(CONJUNCT_SPEC);
        let first = e.convert("ks/");
        let second = e.convert("ks/");
        assert_eq!(first, second);
        assert_eq!(first, "ক্স");
    }

    #[test]
    fn prefix_sets_leak_before_longer_key_runs() {
        // Typing "ae": "a"'s Set(F 1) ran eagerly in real m17n before "ae"
        // fired; the leak keeps that order so F ends at 2.
        let spec = r#"
            (map
                (shor
                    ("a" (set F 1) "অ")
                    ("ae" (set F 2) "অ্যা")
                )
            )
            (state (init (shor (shift init))))
        "#;
        let mut e = engine(spec);
        assert_eq!(e.convert("ae"), "অ্যা");
        assert_eq!(e.var("F"), 2);
    }

    #[test]
    fn entry_actions_run_once_at_start() {
        let spec = r#"
            (map (shor ("a" "আ")))
            (state (init (t (set CmpC 7)) (shor (shift init))))
        "#;
        let mut e = engine(spec);
        e.convert("a");
        assert_eq!(e.var("CmpC"), 7);
    }
}
