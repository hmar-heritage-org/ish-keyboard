#!/usr/bin/env python3
"""
Sequential Step 1: Normalize Web/Markdown Corpus to Canonical Orthography.
Uses the Google English 333k dataset as the base for loanword preservation,
cross-referenced against Hmar dictionaries to safely normalize native words.
"""

import os
import re
import json
from pathlib import Path
from collections import Counter

# Paths
INPUT_ROOT = Path("/home/phxlm/Work/hmar-heritage-hf/scratch/markdown_corpus")
OUTPUT_ROOT = Path("/home/phxlm/Work/HHF/ISH-key/resources/cleaning/normalized_markdown")
DMAP_PATH = Path("/home/phxlm/Work/HHF/morphology/data/deterministic_map.json")
ENG_PATH = Path("/home/phxlm/Work/HHF/ISH-key/resources/lexicon/english/google-333k-english.txt")
DICT_001 = Path("/home/phxlm/Work/HHF/datasets/wordlist/data/dictionary-001.json")
DICT_004 = Path("/home/phxlm/Work/HHF/datasets/wordlist/data/dictionary-004.json")
REPORT_PATH = Path("/home/phxlm/Work/HHF/ISH-key/resources/cleaning/step1_normalization_report.json")

def load_resources():
    print("Loading linguistic resources...")
    
    # 1. Deterministic Map
    with open(DMAP_PATH, "r", encoding="utf-8") as f:
        dmap = json.load(f)

    # 2. Hmar Dictionary Headwords (Pulamte + Bapui)
    hmar_dict_words = set()
    for dpath in [DICT_001, DICT_004]:
        with open(dpath, "r", encoding="utf-8") as f:
            data = json.load(f)
        for item in data:
            w = item.get("word")
            if w:
                for tok in re.findall(r"\b[a-zA-ZṭṬâêîôûÂÊÎÔÛ\u1E6D\u1E6C]+\b", w):
                    hmar_dict_words.add(tok.lower())

    # Add all dmap keys and canonical targets to Hmar lexicon
    for k, v in dmap.items():
        hmar_dict_words.add(k.lower())
        hmar_dict_words.add(v["canonical"].lower())

    # 3. Google English 333k Corpus
    eng_words = set()
    with open(ENG_PATH, "r", encoding="utf-8") as f:
        for idx, line in enumerate(f):
            parts = line.strip().split("\t")
            if parts:
                w = parts[0].lower()
                eng_words.add(w)
            if idx > 120000:  # Top 120,000 English words covers all modern loanwords
                break

    # 4. Form Loanword Guard: English words that are NOT native Hmar dictionary headwords
    loanword_guard = set()
    for w in eng_words:
        if ("tr" in w or "thr" in w) and (w not in hmar_dict_words):
            loanword_guard.add(w)

    # Sort Hmar tr/thr roots by length descending for greedy compound matching
    hmar_tr_roots = sorted(
        [(k.lower(), dmap[k]["canonical"]) for k in dmap if "tr" in k.lower() or "thr" in k.lower()],
        key=lambda x: len(x[0]),
        reverse=True
    )

    print(f"Loaded {len(dmap):,} deterministic rules.")
    print(f"Loaded {len(hmar_dict_words):,} Hmar dictionary headwords.")
    print(f"Active loanword guard: {len(loanword_guard):,} English tr/thr loanwords protected.")
    return dmap, hmar_dict_words, loanword_guard, hmar_tr_roots

def match_casing(original, replacement):
    if original.isupper():
        return replacement.upper()
    elif original.istitle():
        if replacement.startswith("ṭh") or replacement.startswith("th"):
            return "Ṭh" + replacement[2:]
        return replacement[0].upper() + replacement[1:]
    return replacement

def normalize_token(token, dmap, hmar_dict_words, loanword_guard, hmar_tr_roots, stats):
    low = token.lower()
    if "tr" not in low and "thr" not in low:
        return token

    # 1. Exact match in deterministic map -> Definitely Hmar
    if low in dmap:
        target = dmap[low]["canonical"]
        out = match_casing(token, target)
        stats["substitutions"][(token, out)] += 1
        return out

    # 2. English loanword guard (e.g. district, central, train, truck, transfer)
    if low in loanword_guard:
        stats["loanwords_guarded"][low] += 1
        return token

    # 3. Compound root match (agglutinative forms e.g. chanchinthrahai, bultranna)
    replaced_low = low
    matched_root = False
    for root, canon in hmar_tr_roots:
        if root in replaced_low:
            replaced_low = replaced_low.replace(root, canon)
            matched_root = True

    if matched_root:
        out = match_casing(token, replaced_low)
        stats["substitutions"][(token, out)] += 1
        return out

    # 4. If token is in Hmar dictionary headwords, convert tr/thr -> ṭ/ṭh
    if low in hmar_dict_words:
        target = low.replace("thr", "ṭh").replace("tr", "ṭ")
        out = match_casing(token, target)
        stats["substitutions"][(token, out)] += 1
        return out

    # 5. Fallback rule: If not recognized as English loanword, treat as native Hmar
    fallback = low.replace("thr", "ṭh").replace("tr", "ṭ")
    out = match_casing(token, fallback)
    stats["substitutions"][(token, out)] += 1
    return out

def process_file(file_path, output_path, dmap, hmar_dict_words, loanword_guard, hmar_tr_roots, stats):
    with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
        content = f.read()

    # Preserve markdown header metadata (frontmatter)
    header = ""
    body = content
    if content.startswith("---"):
        parts = content.split("---", 2)
        if len(parts) >= 3:
            header = "---" + parts[1] + "---\n"
            body = parts[2]
    elif content.startswith("# "):
        parts = content.split("---\n", 1)
        if len(parts) == 2:
            header = parts[0] + "---\n"
            body = parts[1]

    # Tokenize body preserving formatting and punctuation
    word_pattern = re.compile(r"([a-zA-ZṭṬâêîôûÂÊÎÔÛ\u1E6D\u1E6C]+)")

    def replacer(match):
        tok = match.group(1)
        return normalize_token(tok, dmap, hmar_dict_words, loanword_guard, hmar_tr_roots, stats)

    normalized_body = word_pattern.sub(replacer, body)
    final_content = header + normalized_body

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(final_content)

def main():
    dmap, hmar_dict_words, loanword_guard, hmar_tr_roots = load_resources()

    stats = {
        "files_processed": 0,
        "substitutions": Counter(),
        "loanwords_guarded": Counter()
    }

    all_files = list(INPUT_ROOT.glob("**/*.md"))
    print(f"Starting sequential Step 1 normalization across {len(all_files)} markdown files...")

    for fpath in all_files:
        rel_path = fpath.relative_to(INPUT_ROOT)
        out_path = OUTPUT_ROOT / rel_path
        process_file(fpath, out_path, dmap, hmar_dict_words, loanword_guard, hmar_tr_roots, stats)
        stats["files_processed"] += 1

    total_subs = sum(stats["substitutions"].values())
    total_guards = sum(stats["loanwords_guarded"].values())

    print(f"\nStep 1 Normalization Complete!")
    print(f"Files Processed: {stats['files_processed']}")
    print(f"Total Hmar Words Normalized: {total_subs:,}")
    print(f"Total English Loanwords Protected: {total_guards:,}")

    # Generate Audit Report
    report = {
        "step": 1,
        "description": "Sequential normalization of web/markdown corpus using English baseline loanword guard",
        "total_files": stats["files_processed"],
        "total_substitutions": total_subs,
        "total_loanwords_protected": total_guards,
        "top_substitutions": [
            {"from": k[0], "to": k[1], "count": v}
            for k, v in stats["substitutions"].most_common(40)
        ],
        "top_protected_loanwords": [
            {"word": k, "count": v}
            for k, v in stats["loanwords_guarded"].most_common(40)
        ]
    }

    with open(REPORT_PATH, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)

    print(f"Audit report saved to: {REPORT_PATH}")

if __name__ == "__main__":
    main()
