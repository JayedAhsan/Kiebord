#!/usr/bin/env python3
"""
tools/pack_lang.py
Standalone compiler and packaging tool for Kiebord .bornolang language extension packs.
Uses only standard library modules.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile


REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SRC_DIR = REPO_ROOT / "assets-src" / "language-packs"
DEFAULT_OUT_DIR = REPO_ROOT / "language-packs" / "releases"
DEFAULT_INDEX_PATH = REPO_ROOT / "language-packs" / "index.json"
NATIVE_CARGO_TOML = REPO_ROOT / "native" / "borno-engine" / "Cargo.toml"


def compute_sha256(filepath: Path) -> str:
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()


def validate_pack_json(pack_dir: Path) -> dict:
    manifest_path = pack_dir / "pack.json"
    if not manifest_path.is_file():
        raise FileNotFoundError(f"Missing pack.json in {pack_dir}")

    with open(manifest_path, "r", encoding="utf-8") as f:
        try:
            data = json.load(f)
        except json.JSONDecodeError as e:
            raise ValueError(f"Invalid JSON in pack.json: {e}")

    required_fields = ["id", "version", "language", "displayName", "author", "layouts"]
    for field in required_fields:
        if field not in data:
            raise ValueError(f"pack.json missing required field: '{field}'")

    if not isinstance(data["layouts"], list) or len(data["layouts"]) == 0:
        raise ValueError("pack.json 'layouts' must be a non-empty array of layout IDs")

    layouts_dir = pack_dir / "layouts"
    if not layouts_dir.is_dir():
        raise FileNotFoundError(f"Missing layouts directory in {pack_dir}")

    for layout_id in data["layouts"]:
        layout_file = layouts_dir / f"{layout_id}.json"
        if not layout_file.is_file():
            raise FileNotFoundError(f"Layout file not found for declared layout '{layout_id}': {layout_file}")
        with open(layout_file, "r", encoding="utf-8") as lf:
            try:
                layout_data = json.load(lf)
                if "rows" not in layout_data or not isinstance(layout_data["rows"], list):
                    raise ValueError(f"Layout '{layout_id}.json' must contain a 'rows' array")
            except json.JSONDecodeError as le:
                raise ValueError(f"Invalid JSON in layout '{layout_id}.json': {le}")

    # Validate optional rules file if declared
    if "scriptRulesFile" in data and data["scriptRulesFile"]:
        rules_path = pack_dir / data["scriptRulesFile"]
        if not rules_path.is_file():
            raise FileNotFoundError(f"Declared scriptRulesFile not found: {rules_path}")
        with open(rules_path, "r", encoding="utf-8") as rf:
            json.load(rf)

    # Validate optional char folding file if declared
    if "charFoldingFile" in data and data["charFoldingFile"]:
        folding_path = pack_dir / data["charFoldingFile"]
        if not folding_path.is_file():
            raise FileNotFoundError(f"Declared charFoldingFile not found: {folding_path}")
        with open(folding_path, "r", encoding="utf-8") as ff:
            json.load(ff)

    return data


def ensure_dictionary_compiled(pack_dir: Path, manifest: dict):
    dict_file_rel = manifest.get("dictionaryFile")
    dict_dir = pack_dir / "dictionaries"
    if not dict_dir.is_dir():
        dict_dir = pack_dir / "dictionary"

    if dict_dir.is_dir():
        for combined_file in dict_dir.glob("*.combined"):
            stem = combined_file.stem
            target_dict = (pack_dir / "dictionaries" / f"{stem}.dict")
            if not target_dict.parent.exists():
                target_dict.parent.mkdir(parents=True, exist_ok=True)

            should_compile = False
            if not target_dict.exists():
                should_compile = True
            elif combined_file.stat().st_mtime > target_dict.stat().st_mtime:
                should_compile = True

            if should_compile:
                print(f"Compiling dictionary: {combined_file.name} -> {target_dict.name}")
                cmd = [
                    "cargo", "run",
                    "--manifest-path", str(NATIVE_CARGO_TOML),
                    "--bin", "compile_dict",
                    str(combined_file),
                    str(target_dict)
                ]
                res = subprocess.run(cmd, capture_output=True, text=True)
                if res.returncode != 0:
                    raise RuntimeError(f"compile_dict failed: {res.stderr or res.stdout}")
                print("Dictionary compilation complete.")

    if dict_file_rel:
        dict_path = pack_dir / dict_file_rel
        if not dict_path.is_file():
            alt_path = pack_dir / dict_file_rel.replace("dictionaries/", "dictionary/")
            if alt_path.is_file():
                manifest["dictionaryFile"] = str(Path(dict_file_rel).name)
            else:
                raise FileNotFoundError(f"Declared dictionaryFile not found: {dict_path}")


def package_bornolang(pack_dir: Path, manifest: dict, out_dir: Path) -> Path:
    pack_id = manifest["id"]
    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / f"{pack_id}.bornolang"

    files_to_pack = []

    # 1. pack.json
    files_to_pack.append((pack_dir / "pack.json", "pack.json"))

    # 2. layouts
    layouts_dir = pack_dir / "layouts"
    for layout_id in manifest["layouts"]:
        layout_file = layouts_dir / f"{layout_id}.json"
        files_to_pack.append((layout_file, f"layouts/{layout_id}.json"))

    # 3. dictionary
    if manifest.get("dictionaryFile"):
        dict_file = pack_dir / manifest["dictionaryFile"]
        if dict_file.is_file():
            files_to_pack.append((dict_file, f"dictionaries/{dict_file.name}"))

    # 4. script rules
    if manifest.get("scriptRulesFile"):
        rules_file = pack_dir / manifest["scriptRulesFile"]
        if rules_file.is_file():
            files_to_pack.append((rules_file, "rules/script_rules.json"))

    rules_dir = pack_dir / "rules"
    if rules_dir.is_dir():
        for rule_file in rules_dir.iterdir():
            if rule_file.is_file():
                arcname = f"rules/{rule_file.name}"
                if not any(a == arcname for _, a in files_to_pack):
                    files_to_pack.append((rule_file, arcname))

    # 5. char folding
    if manifest.get("charFoldingFile"):
        folding_file = pack_dir / manifest["charFoldingFile"]
        if folding_file.is_file():
            files_to_pack.append((folding_file, "gesture/char_folding.json"))

    if out_path.exists():
        out_path.unlink()

    with zipfile.ZipFile(out_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for disk_path, arcname in files_to_pack:
            zf.write(disk_path, arcname)

    return out_path


def update_catalog_index(manifest: dict, pack_path: Path, sha256_hash: str, index_path: Path):
    if not index_path.exists():
        index_entries = []
    else:
        with open(index_path, "r", encoding="utf-8") as f:
            try:
                index_entries = json.load(f)
                if not isinstance(index_entries, list):
                    index_entries = []
            except json.JSONDecodeError:
                index_entries = []

    pack_id = manifest["id"]
    size_bytes = pack_path.stat().st_size
    has_dict = bool(manifest.get("dictionaryFile"))

    description = manifest.get("description")
    if not description:
        layout_count = len(manifest.get("layouts", []))
        dict_desc = " and dictionary" if has_dict else ""
        description = f"{manifest['displayName']} ({layout_count} layout(s){dict_desc})"

    entry = {
        "id": pack_id,
        "name": manifest["displayName"],
        "language": manifest["language"],
        "version": manifest["version"],
        "author": manifest["author"],
        "description": description,
        "downloadUrl": f"https://raw.githubusercontent.com/JayedAhsan/Kiebord/main/language-packs/releases/{pack_id}.bornolang",
        "sha256": sha256_hash,
        "sizeBytes": size_bytes,
        "hasDictionary": has_dict
    }

    if manifest.get("credit") or manifest.get("acknowledgement"):
        entry["credit"] = manifest.get("credit") or manifest.get("acknowledgement")
    if manifest.get("languageDisplayName") or manifest.get("language_display_name"):
        entry["languageDisplayName"] = manifest.get("languageDisplayName") or manifest.get("language_display_name")
    if manifest.get("dictionaryCredit") or manifest.get("dictionary_credit") or manifest.get("wordlistCredit"):
        entry["dictionaryCredit"] = manifest.get("dictionaryCredit") or manifest.get("dictionary_credit") or manifest.get("wordlistCredit")
    if manifest.get("dictionaryLicense") or manifest.get("dictionary_license") or manifest.get("wordlistLicense"):
        entry["dictionaryLicense"] = manifest.get("dictionaryLicense") or manifest.get("dictionary_license") or manifest.get("wordlistLicense")

    # Upsert entry
    existing_idx = next((i for i, e in enumerate(index_entries) if e.get("id") == pack_id), None)
    if existing_idx is not None:
        index_entries[existing_idx] = entry
    else:
        index_entries.append(entry)

    with open(index_path, "w", encoding="utf-8") as f:
        json.dump(index_entries, f, indent=2, ensure_ascii=False)
        f.write("\n")


def process_pack(pack_dir: Path, out_dir: Path, index_path: Path, validate_only: bool, no_index: bool):
    print(f"Processing pack at: {pack_dir}")
    manifest = validate_pack_json(pack_dir)
    print(f"  Valid manifest for '{manifest['displayName']}' (id: {manifest['id']}, v{manifest['version']})")
    print(f"  Layouts: {', '.join(manifest['layouts'])}")

    if validate_only:
        print("  Validation successful (--validate-only set).")
        return

    ensure_dictionary_compiled(pack_dir, manifest)

    out_file = package_bornolang(pack_dir, manifest, out_dir)
    sha256 = compute_sha256(out_file)
    size = out_file.stat().st_size
    print(f"  Packaged: {out_file.relative_to(REPO_ROOT)}")
    print(f"  Size: {size:,} bytes | SHA-256: {sha256}")

    if not no_index and index_path:
        update_catalog_index(manifest, out_file, sha256, index_path)
        print(f"  Updated catalog: {index_path.relative_to(REPO_ROOT)}")


def main():
    parser = argparse.ArgumentParser(description="Kiebord Language Pack Compiler & Packager (.bornolang)")
    parser.add_argument("pack", nargs="?", help="Pack ID under assets-src/language-packs/ or custom path to pack folder")
    parser.add_argument("--all", action="store_true", help="Process all packs under assets-src/language-packs/")
    parser.add_argument("--out-dir", type=Path, default=DEFAULT_OUT_DIR, help="Destination directory for .bornolang files")
    parser.add_argument("--index-path", type=Path, default=DEFAULT_INDEX_PATH, help="Path to index.json catalog")
    parser.add_argument("--validate-only", action="store_true", help="Validate pack schema without packaging")
    parser.add_argument("--no-index", action="store_true", help="Do not update index.json")

    args = parser.parse_args()

    if args.all:
        if not DEFAULT_SRC_DIR.is_dir():
            print(f"Error: Default source directory not found: {DEFAULT_SRC_DIR}", file=sys.stderr)
            sys.exit(1)
        subdirs = [d for d in DEFAULT_SRC_DIR.iterdir() if d.is_dir() and (d / "pack.json").is_file()]
        if not subdirs:
            print(f"No valid language pack directories found under {DEFAULT_SRC_DIR}")
            sys.exit(0)
        for d in sorted(subdirs):
            process_pack(d, args.out_dir, args.index_path, args.validate_only, args.no_index)
            print()
    elif args.pack:
        pack_input = Path(args.pack)
        if pack_input.is_dir() and (pack_input / "pack.json").is_file():
            target_dir = pack_input
        else:
            target_dir = DEFAULT_SRC_DIR / args.pack
            if not (target_dir.is_dir() and (target_dir / "pack.json").is_file()):
                print(f"Error: Language pack not found at {target_dir}", file=sys.stderr)
                sys.exit(1)
        process_pack(target_dir, args.out_dir, args.index_path, args.validate_only, args.no_index)
    else:
        parser.print_help()
        sys.exit(1)


if __name__ == "__main__":
    main()

