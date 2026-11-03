//! S-expression parser for m17n `.mim` input-method files.
//!
//! `.mim` files are Lisp-style forms: whitespace-separated lists of `(...)`
//! holding quoted strings (`"..."` with `\"` escapes) and nested lists.
//! `;;` starts a line comment, outside and inside lists.

#[derive(Clone, Debug, PartialEq)]
pub enum Sexp {
    /// A quoted string (`"..."`) — literal text.
    Str(String),
    /// A bare token (`input-method`, ` CID`, `?্`) — symbol or char literal.
    Atom(String),
    List(Vec<Sexp>),
}

/// Parses every top-level form in `input`. `;;` comments are skipped.
/// Bare atoms (m17n symbols like `input-method`, state names, numbers)
/// parse as `Str`, same as quoted strings.
pub fn parse(input: &str) -> Result<Vec<Sexp>, String> {
    let chars: Vec<char> = input.chars().collect();
    let mut pos = 0usize;
    let mut forms = Vec::new();
    loop {
        skip_ws_and_comments(&chars, &mut pos);
        if pos >= chars.len() {
            break;
        }
        forms.push(parse_form(&chars, &mut pos, 0)?);
    }
    Ok(forms)
}

/// Nesting cap: downloaded specs are untrusted (OTA), and 256 levels is far
/// beyond anything the Khipro specs use.
const MAX_NESTING: usize = 256;

fn skip_ws_and_comments(chars: &[char], pos: &mut usize) {
    while *pos < chars.len() {
        let c = chars[*pos];
        if c.is_whitespace() {
            *pos += 1;
        } else if c == ';' {
            // m17n comment: `;` to end of line (single or double semicolon).
            while *pos < chars.len() && chars[*pos] != '\n' {
                *pos += 1;
            }
        } else {
            break;
        }
    }
}

fn parse_form(chars: &[char], pos: &mut usize, depth: usize) -> Result<Sexp, String> {
    if depth > MAX_NESTING {
        return Err(format!("nesting deeper than {MAX_NESTING}"));
    }
    match chars[*pos] {
        '(' => parse_list(chars, pos, depth),
        ')' => Err(format!("unexpected ')' at offset {pos}")),
        '"' => Ok(Sexp::Str(parse_string(chars, pos)?)),
        _ => Ok(Sexp::Atom(parse_atom(chars, pos))),
    }
}

fn parse_atom(chars: &[char], pos: &mut usize) -> String {
    let start = *pos;
    while *pos < chars.len()
        && !chars[*pos].is_whitespace()
        && chars[*pos] != '('
        && chars[*pos] != ')'
        && chars[*pos] != ';'
    {
        *pos += 1;
    }
    chars[start..*pos].iter().collect()
}

fn parse_list(chars: &[char], pos: &mut usize, depth: usize) -> Result<Sexp, String> {
    *pos += 1; // consume '('
    let mut items = Vec::new();
    loop {
        skip_ws_and_comments(chars, pos);
        match chars.get(*pos) {
            None => return Err("unbalanced parenthesis: unexpected end of input".to_string()),
            Some(')') => {
                *pos += 1;
                return Ok(Sexp::List(items));
            }
            Some(_) => items.push(parse_form(chars, pos, depth + 1)?),
        }
    }
}

fn parse_string(chars: &[char], pos: &mut usize) -> Result<String, String> {
    *pos += 1; // consume opening quote
    let mut out = String::new();
    loop {
        match chars.get(*pos) {
            None => return Err("unterminated string".to_string()),
            Some('"') => {
                *pos += 1;
                return Ok(out);
            }
            Some('\\') => match chars.get(*pos + 1) {
                None => return Err("unterminated string".to_string()),
                Some(&escaped) => {
                    out.push(escaped);
                    *pos += 2;
                }
            },
            Some(&c) => {
                out.push(c);
                *pos += 1;
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_flat_list_of_four_items() {
        let forms = parse("(input-method bn khipro)").unwrap();
        assert_eq!(forms.len(), 1);
        match &forms[0] {
            Sexp::List(items) => {
                assert_eq!(items.len(), 3);
                assert_eq!(items[0], Sexp::Atom("input-method".to_string()));
                assert_eq!(items[2], Sexp::Atom("khipro".to_string()));
            }
            other => panic!("expected list, got {other:?}"),
        }
    }

    #[test]
    fn parses_nested_lists() {
        let forms = parse("((\"a\" \"b\") (\"c\"))").unwrap();
        assert_eq!(forms.len(), 1);
        match &forms[0] {
            Sexp::List(items) => {
                assert_eq!(items.len(), 2);
                assert_eq!(
                    items[0],
                    Sexp::List(vec![
                        Sexp::Str("a".to_string()),
                        Sexp::Str("b".to_string())
                    ])
                );
                assert_eq!(
                    items[1],
                    Sexp::List(vec![Sexp::Str("c".to_string())])
                );
            }
            other => panic!("expected list, got {other:?}"),
        }
    }

    #[test]
    fn parses_deeply_nested_list() {
        let forms = parse("(((\"a\" \"b\")) (\"c\"))").unwrap();
        match &forms[0] {
            Sexp::List(items) => {
                assert_eq!(
                    items[0],
                    Sexp::List(vec![Sexp::List(vec![
                        Sexp::Str("a".to_string()),
                        Sexp::Str("b".to_string())
                    ])])
                );
            }
            other => panic!("expected list, got {other:?}"),
        }
    }

    #[test]
    fn parses_string_with_escaped_quote() {
        let forms = parse("(\"x\\\"y\")").unwrap();
        assert_eq!(
            forms,
            vec![Sexp::List(vec![Sexp::Str("x\"y".to_string())])]
        );
    }

    #[test]
    fn skips_comment_outside_list() {
        let forms = parse(";; header comment\n(input-method bn khipro)\n;; trailing\n").unwrap();
        assert_eq!(forms.len(), 1);
    }

    #[test]
    fn skips_comment_inside_list() {
        let forms = parse("(\"a\" ;; why\n \"b\")").unwrap();
        assert_eq!(
            forms,
            vec![Sexp::List(vec![
                Sexp::Str("a".to_string()),
                Sexp::Str("b".to_string())
            ])]
        );
    }

    #[test]
    fn unbalanced_paren_is_error() {
        assert!(parse("(\"a\"").is_err());
    }

    #[test]
    fn stray_close_paren_is_error() {
        assert!(parse("(\"a\"))").is_err());
    }

    #[test]
    fn unterminated_string_is_error() {
        assert!(parse("(\"abc").is_err());
    }

    #[test]
    fn deep_nesting_is_rejected_not_crash() {
        let deep = format!("{}{}", "(".repeat(512), ")".repeat(512));
        assert!(parse(&deep).is_err());
    }

    #[test]
    fn parses_bare_atoms() {
        // m17n forms use bare symbols: (input-method bn khipro), (state x), (set V 1).
        let forms = parse("(set CID 1)").unwrap();
        assert_eq!(
            forms,
            vec![Sexp::List(vec![
                Sexp::Atom("set".to_string()),
                Sexp::Atom("CID".to_string()),
                Sexp::Atom("1".to_string()),
            ])]
        );
    }
}
