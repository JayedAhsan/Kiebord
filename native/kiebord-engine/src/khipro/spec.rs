//! Translates parsed m17n `.mim` forms into an in-memory [`Spec`].
//!
//! Grammar actually used by the Khipro specs (bn-khipro*.mim):
//! `(map (classname (seq action... "insert"?) ...) ...)` — each block names a
//! key class; trailing bare string in a rule is an implicit insert.
//! `(state (statename (key-or-class action...) ...) ...)` — dispatch on a
//! literal key or a class name.
//! Actions: bare string, `(insert s)`, `(set V n|Var)`, `(delete @-|@-N|cstmN)`,
//! `(move @-|@>|cstmN)`, `(mark cstmN)`, `(shift state)`, `(commit)`, `(undo)`,
//! `(backspace)`, `(cond ((= V n) action...) ...)`.
//! `(macro (name body...))` — zero-arg, spliced at call sites.
//! `(input-method ...)`, `(title ...)`, `(description ...)` — ignored.

use std::collections::HashMap;

use super::sexp::Sexp;

#[derive(Clone, Debug, PartialEq)]
pub struct Spec {
    pub maps: HashMap<String, Vec<MapRule>>,
    pub states: HashMap<String, StateDef>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct MapRule {
    pub seq: Vec<char>,
    pub actions: Vec<Action>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct StateDef {
    /// Actions of the state's `(t ...)` rule, run once on state entry.
    pub entry_actions: Vec<Action>,
    /// Class dispatch rules: a named map's sequences with their actions.
    pub rules: Vec<StateRule>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct StateRule {
    /// The named map whose sequences this rule dispatches on.
    pub class: String,
    pub actions: Vec<Action>,
}

#[derive(Clone, Debug, PartialEq)]
pub enum Action {
    Insert(String),
    Set(String, Value),
    Delete(Target),
    Move(Target),
    Mark(String),
    Shift(String),
    Commit,
    Undo,
    Backspace,
    Cond(Vec<(Cond, Vec<Action>)>),
}

#[derive(Clone, Debug, PartialEq)]
pub enum Value {
    Num(i64),
    Var(String),
}

#[derive(Clone, Debug, PartialEq)]
pub enum Target {
    /// Delete/move N characters before the cursor (`@-`, `@-2` … `@-5`).
    CursorBack(usize),
    /// A named mark (`cstm1` … `cstm4`).
    Named(String),
    /// `@>` — the end of the buffer.
    End,
}

#[derive(Clone, Debug, PartialEq)]
pub enum Cond {
    /// A bare literal condition (e.g. `1`) — always true, m17n's else.
    True,
    Eq(Value, Value),
    Lt(Value, Value),
    Gt(Value, Value),
    And(Vec<Cond>),
    Or(Vec<Cond>),
}

pub fn build_spec(forms: &[Sexp]) -> Result<Spec, String> {
    let mut maps: HashMap<String, Vec<MapRule>> = HashMap::new();
    let mut states: HashMap<String, StateDef> = HashMap::new();

    // First pass: macros may be defined after the map/state that calls them.
    let mut macros: HashMap<String, Vec<Sexp>> = HashMap::new();
    for form in forms {
        let Sexp::List(items) = form else {
            return Err("top-level form must be a list".to_string());
        };
        if matches!(str_of(items.first(), "form")?.as_str(), "macro") {
            for def in items.iter().skip(1) {
                let Sexp::List(parts) = def else {
                    return Err("macro definition must be a list".to_string());
                };
                let name = str_of(parts.first(), "macro name")?;
                macros.insert(name, parts[1..].to_vec());
            }
        }
    }

    for form in forms {
        let Sexp::List(items) = form else {
            return Err("top-level form must be a list".to_string());
        };
        let head = str_of(items.first(), "form")?;
        match head.as_str() {
            "input-method" | "title" | "description" => {}
            "macro" => {
                for def in items.iter().skip(1) {
                    let Sexp::List(parts) = def else {
                        return Err("macro definition must be a list".to_string());
                    };
                    let name = str_of(parts.first(), "macro name")?;
                    macros.insert(name, parts[1..].to_vec());
                }
            }
            "map" => {
                for block in items.iter().skip(1) {
                    let Sexp::List(rules) = block else {
                        return Err("map block must be a list of rules".to_string());
                    };
                    let class = str_of(rules.first(), "map class name")?;
                    let mut parsed = Vec::new();
                    for rule in rules.iter().skip(1) {
                        if let Some(parsed_rule) = parse_map_rule(rule, &macros)? {
                            parsed.push(parsed_rule);
                        }
                    }
                    maps.insert(class, parsed);
                }
            }
            "state" => {
                for block in items.iter().skip(1) {
                    let Sexp::List(rules) = block else {
                        return Err("state block must be a list of rules".to_string());
                    };
                    let name = str_of(rules.first(), "state name")?;
                    let mut def = StateDef { entry_actions: Vec::new(), rules: Vec::new() };
                    for rule in rules.iter().skip(1) {
                        let Sexp::List(parts) = rule else {
                            return Err(format!("state rule must be a list, got {rule:?}"));
                        };
                        // `(t actions...)` is the state's entry-actions rule.
                        if matches!(parts.first(), Some(Sexp::Atom(a)) if a == "t") {
                            for a in parts.iter().skip(1) {
                                translate_actions(a, &macros, &mut def.entry_actions, 0)?;
                            }
                            continue;
                        }
                        let class = str_of(parts.first(), "dispatch class")?;
                        let mut actions = Vec::new();
                        for a in parts.iter().skip(1) {
                            translate_actions(a, &macros, &mut actions, 0)?;
                        }
                        def.rules.push(StateRule { class, actions });
                    }
                    states.insert(name, def);
                }
            }
            other => return Err(format!("unknown top-level form ({other} ...)")),
        }
    }

    Ok(Spec { maps, states })
}

/// m17n keysym-named keys used by the Khipro specs, mapped to the runtime
/// key char the IME feeds. Keypad keysyms have no runtime equivalent and
/// their rules are skipped (the conformance gate flags it if that matters).
fn keysym_char(name: &str) -> Option<char> {
    match name {
        "BackSpace" | "Backspace" => Some(super::engine::BACKSPACE),
        _ => None,
    }
}

/// A rule whose key is `(Keysym)`. Returns None for keysyms we never feed.
fn keysym_of(item: Option<&Sexp>) -> Result<Option<char>, String> {
    match item {
        Some(Sexp::List(parts)) if parts.len() == 1 => {
            let name = str_of(parts.first(), "keysym")?;
            Ok(keysym_char(&name))
        }
        _ => Ok(None),
    }
}

fn parse_map_rule(rule: &Sexp, macros: &HashMap<String, Vec<Sexp>>) -> Result<Option<MapRule>, String> {
    let Sexp::List(parts) = rule else {
        return Err(format!("map rule must be a list, got {rule:?}"));
    };
    let seq: Vec<char> = match keysym_of(parts.first())? {
        Some(ch) => vec![ch],
        None if matches!(parts.first(), Some(Sexp::List(_))) => return Ok(None),
        None => str_of(parts.first(), "map rule key sequence")?.chars().collect(),
    };
    let mut actions = Vec::new();
    for item in parts.iter().skip(1) {
        translate_actions(item, macros, &mut actions, 0)?;
    }
    Ok(Some(MapRule { seq, actions }))
}

fn translate_actions(
    item: &Sexp,
    macros: &HashMap<String, Vec<Sexp>>,
    out: &mut Vec<Action>,
    depth: usize,
) -> Result<(), String> {
    if depth > 32 {
        return Err("macro expansion deeper than 32 (cyclic macro?)".to_string());
    }
    match item {
        Sexp::Str(s) => {
            out.push(Action::Insert(s.clone()));
            Ok(())
        }
        Sexp::Atom(a) => Err(format!("bare atom in action position: {a}")),
        Sexp::List(parts) => {
            let head = str_of(parts.first(), "action")?;
            match head.as_str() {
                "insert" => match parts.get(1) {
                    // `(insert ?x)` inserts the single char x (elisp syntax).
                    Some(Sexp::Atom(a)) if a.starts_with('?') => {
                        out.push(Action::Insert(a[1..].to_string()));
                        Ok(())
                    }
                    arg => {
                        let s = str_of(arg, "insert argument")?;
                        out.push(Action::Insert(s));
                        Ok(())
                    }
                },
                "set" => {
                    let name = str_of(parts.get(1), "set variable name")?;
                    let value = value_of(parts.get(2), "set value")?;
                    out.push(Action::Set(name, value));
                    Ok(())
                }
                "delete" => {
                    let target = target_of(parts.get(1), "delete target")?;
                    out.push(Action::Delete(target));
                    Ok(())
                }
                "move" => {
                    let arg = str_of(parts.get(1), "move target")?;
                    let target = match arg.as_str() {
                        "@>" => Target::End,
                        other => parse_target(other)?,
                    };
                    out.push(Action::Move(target));
                    Ok(())
                }
                "mark" => {
                    let name = str_of(parts.get(1), "mark name")?;
                    out.push(Action::Mark(name));
                    Ok(())
                }
                "shift" => {
                    let s = str_of(parts.get(1), "shift target state")?;
                    out.push(Action::Shift(s));
                    Ok(())
                }
                "commit" => {
                    out.push(Action::Commit);
                    Ok(())
                }
                "undo" => {
                    out.push(Action::Undo);
                    Ok(())
                }
                "backspace" => {
                    out.push(Action::Backspace);
                    Ok(())
                }
                "cond" => {
                    let mut branches = Vec::new();
                    for branch in parts.iter().skip(1) {
                        let Sexp::List(parts) = branch else {
                            return Err("cond branch must be a list".to_string());
                        };
                        let cond = match parts.first() {
                            Some(c) => parse_cond(c)?,
                            None => return Err("cond branch missing condition".to_string()),
                        };
                        let mut actions = Vec::new();
                        for a in parts.iter().skip(1) {
                            translate_actions(a, macros, &mut actions, depth + 1)?;
                        }
                        branches.push((cond, actions));
                    }
                    out.push(Action::Cond(branches));
                    Ok(())
                }
                name if macros.contains_key(name) => {
                    for a in &macros[name] {
                        translate_actions(a, macros, out, depth + 1)?;
                    }
                    Ok(())
                }
                other => Err(format!("unknown action ({other} ...)")),
            }
        }
    }
}

fn parse_cond(form: &Sexp) -> Result<Cond, String> {
    let Sexp::List(parts) = form else {
        // A bare atom condition (e.g. `1`) is the always-true else branch.
        return Ok(Cond::True);
    };
    let op = str_of(parts.first(), "condition operator")?;
    let operands = || -> Result<(Value, Value), String> {
        Ok((
            value_of(parts.get(1), "condition operand")?,
            value_of(parts.get(2), "condition operand")?,
        ))
    };
    match op.as_str() {
        "=" => {
            let (a, b) = operands()?;
            Ok(Cond::Eq(a, b))
        }
        "<" => {
            let (a, b) = operands()?;
            Ok(Cond::Lt(a, b))
        }
        ">" => {
            let (a, b) = operands()?;
            Ok(Cond::Gt(a, b))
        }
        "and" | "&" | "or" | "|" => {
            let mut subs = Vec::new();
            for sub in parts.iter().skip(1) {
                subs.push(parse_cond(sub)?);
            }
            if op == "and" || op == "&" {
                Ok(Cond::And(subs))
            } else {
                Ok(Cond::Or(subs))
            }
        }
        other => Err(format!("unknown condition operator ({other} ...)")),
    }
}

fn parse_target(arg: &str) -> Result<Target, String> {
    if let Some(rest) = arg.strip_prefix("@-") {
        let n = if rest.is_empty() { 1 } else { rest.parse::<usize>().map_err(|_| format!("bad delete/move offset {arg}"))? };
        return Ok(Target::CursorBack(n));
    }
    if arg.starts_with('@') {
        return Err(format!("unknown cursor marker {arg}"));
    }
    Ok(Target::Named(arg.to_string()))
}

fn target_of(item: Option<&Sexp>, what: &str) -> Result<Target, String> {
    parse_target(&str_of(item, what)?)
}

fn value_of(item: Option<&Sexp>, what: &str) -> Result<Value, String> {
    let s = str_of(item, what)?;
    if let Ok(n) = s.parse::<i64>() {
        Ok(Value::Num(n))
    } else {
        Ok(Value::Var(s))
    }
}

fn str_of(item: Option<&Sexp>, what: &str) -> Result<String, String> {
    match item {
        Some(Sexp::Str(s)) => Ok(s.clone()),
        Some(Sexp::Atom(s)) => Ok(s.clone()),
        other @ Some(_) => Err(format!("{what}: unexpected form where name expected: {other:?}")),
        None => Err(format!("{what}: missing")),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn build(mim: &str) -> Result<Spec, String> {
        let forms = super::super::sexp::parse(mim).unwrap();
        build_spec(&forms)
    }

    #[test]
    fn builds_map_with_implicit_insert_and_set() {
        let spec = build(
            "(input-method bn khipro)\n\
             (map\n\
             \x20(byanjon\n\
             \x20\x20(\"k\" (set CID 1) \"ক\")\n\
             \x20\x20(\"kh\" (set CID 2) (set CmpC 1) \"খ\")\n\
             )\n)\
             (state\n\
             \x20(init\n\
             \x20\x20(byanjon (shift byanjon-state))\n\
             \x20\x20(t (commit))\n\
             \x20)\n\
             \x20(byanjon-state (t (shift init)))\n\
             )\n",
        )
        .unwrap();
        let byanjon = &spec.maps["byanjon"];
        assert_eq!(byanjon.len(), 2);
        assert_eq!(byanjon[0].seq, vec!['k']);
        assert_eq!(
            byanjon[0].actions,
            vec![
                Action::Set("CID".into(), Value::Num(1)),
                Action::Insert("ক".into()),
            ]
        );
        assert_eq!(byanjon[1].seq, vec!['k', 'h']);
        assert_eq!(byanjon[1].actions.len(), 3);
        let init = &spec.states["init"];
        // (t (commit)) became entry actions; only the class rule remains.
        assert_eq!(init.entry_actions, vec![Action::Commit]);
        assert_eq!(init.rules.len(), 1);
        assert_eq!(init.rules[0].class, "byanjon");
        assert_eq!(init.rules[0].actions, vec![Action::Shift("byanjon-state".into())]);
        let byanjon_state = &spec.states["byanjon-state"];
        assert_eq!(byanjon_state.entry_actions, vec![Action::Shift("init".into())]);
        assert!(byanjon_state.rules.is_empty());
    }

    #[test]
    fn expands_zero_arg_macro_at_call_site() {
        let spec = build(
            "(macro\n\
             \x20(reset_it (mark cstm1) (set Cnst1 CnstID))\n\
             )\n\
             (map (shor (\"a\" (reset_it) \"আ\")))\n",
        )
        .unwrap();
        assert_eq!(
            spec.maps["shor"][0].actions,
            vec![
                Action::Mark("cstm1".into()),
                Action::Set("Cnst1".into(), Value::Var("CnstID".into())),
                Action::Insert("আ".into()),
            ]
        );
    }

    #[test]
    fn parses_delete_move_mark_targets() {
        let spec = build(
            "(map (shor (\"x\" (delete @-2) (move @>) (mark cstm1) (delete cstm1) (move @-) )))\n",
        )
        .unwrap();
        assert_eq!(
            spec.maps["shor"][0].actions,
            vec![
                Action::Delete(Target::CursorBack(2)),
                Action::Move(Target::End),
                Action::Mark("cstm1".into()),
                Action::Delete(Target::Named("cstm1".into())),
                Action::Move(Target::CursorBack(1)),
            ]
        );
    }

    #[test]
    fn parses_cond_branches() {
        let spec = build(
            "(map (shor (\"x\" (cond ((= CID 0) (shift init)) ((> CID 2) (commit))))))\n",
        )
        .unwrap();
        match &spec.maps["shor"][0].actions[0] {
            Action::Cond(branches) => {
                assert_eq!(branches.len(), 2);
                assert_eq!(branches[0].0, Cond::Eq(Value::Var("CID".into()), Value::Num(0)));
                assert_eq!(branches[0].1, vec![Action::Shift("init".into())]);
                assert_eq!(branches[1].0, Cond::Gt(Value::Var("CID".into()), Value::Num(2)));
            }
            other => panic!("expected cond, got {other:?}"),
        }
    }

    #[test]
    fn cyclic_macro_is_rejected() {
        let forms = super::super::sexp::parse(
            "(macro (m (m))) (map (shor (\"a\" (m) \"আ\")))",
        )
        .unwrap();
        assert!(build_spec(&forms).is_err());
    }

    #[test]
    fn unknown_top_level_form_is_error() {
        assert!(build("(frobnicate x)").is_err());
    }

    #[test]
    fn backspace_and_undo_actions_parse() {
        let spec = build("(map (shor (\"x\" (backspace) (undo) (commit))))\n").unwrap();
        assert_eq!(
            spec.maps["shor"][0].actions,
            vec![Action::Backspace, Action::Undo, Action::Commit]
        );
    }
}

#[cfg(test)]
mod keysym_tests {
    use super::*;

    #[test]
    fn keysym_backspace_rule_maps_to_backspace_key_and_kp_rules_are_skipped() {
        let forms = super::super::sexp::parse(
            "(map (shor ((BackSpace) (undo)) ((KP_0) \"০\") (\"a\" \"আ\")))",
        )
        .unwrap();
        let spec = build_spec(&forms).unwrap();
        let rules = &spec.maps["shor"];
        // KP_0 has no runtime key: skipped. BackSpace maps to \u{0008}.
        assert_eq!(rules.len(), 2);
        assert_eq!(rules[0].seq, vec!['\u{0008}']);
        assert_eq!(rules[0].actions, vec![Action::Undo]);
    }

    #[test]
    fn ampersand_cond_is_and() {
        let forms = super::super::sexp::parse(
            "(map (shor (\"x\" (cond ((& (= A 1) (> B 2)) (commit))))))",
        )
        .unwrap();
        let spec = build_spec(&forms).unwrap();
        match &spec.maps["shor"][0].actions[0] {
            Action::Cond(branches) => {
                assert_eq!(
                    branches[0].0,
                    Cond::And(vec![
                        Cond::Eq(Value::Var("A".into()), Value::Num(1)),
                        Cond::Gt(Value::Var("B".into()), Value::Num(2)),
                    ])
                );
            }
            other => panic!("expected cond, got {other:?}"),
        }
    }
}
