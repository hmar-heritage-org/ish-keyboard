#!/usr/bin/env python3
"""
Sequential Step 3: Unified Decoupling Pass.
Applies the 4 locked separation lists (-hai, -te, -chal, -pui) across the 3.02M-word corpus.
Generates pristine ISH unigrams, bigrams, and trigrams with decoupled clitics.
"""

import os
import re
import json
import pandas as pd
from pathlib import Path
from collections import Counter, defaultdict

# Input Paths
BIBLE_CLB = Path("/home/phxlm/Work/HHF/datasets/corpus-archive/data/json/0010/file.json")
BIBLE_OV = Path("/home/phxlm/Work/HHF/datasets/corpus-archive/data/json/0011/file.json")
NORM_MD_ROOT = Path("/home/phxlm/Work/HHF/ISH-key/resources/cleaning/normalized_markdown")
DICT_001 = Path("/home/phxlm/Work/HHF/datasets/wordlist/data/dictionary-001.json")
DICT_004 = Path("/home/phxlm/Work/HHF/datasets/wordlist/data/dictionary-004.json")
DMAP_PATH = Path("/home/phxlm/Work/HHF/morphology/data/deterministic_map.json")

# Separation Lists
CLEANING_DIR = Path("/home/phxlm/Work/HHF/ISH-key/resources/cleaning")
HAI_FILE = CLEANING_DIR / "hai_candidates_full.tsv"
TE_FILE = CLEANING_DIR / "te_to_separate.tsv"
CHAL_FILE = CLEANING_DIR / "chal_to_separate.tsv"
PUI_FILE = CLEANING_DIR / "pui_to_separate.tsv"
ANCHOR_FILE = CLEANING_DIR / "anchor_decoupling_manifest.tsv"
REM_FILE = CLEANING_DIR / "remainder_review.tsv"

# Output Paths
OUT_UNIGRAMS = CLEANING_DIR / "ish_unigrams.tsv"
OUT_BIGRAMS = CLEANING_DIR / "ish_bigrams.jsonl"
OUT_TRIGRAMS = CLEANING_DIR / "ish_trigrams.jsonl"
REPORT_FILE = CLEANING_DIR / "step3_decoupling_report.json"

TOKEN_PATTERN = re.compile(r"[a-zA-ZṭṬâêîôûÂÊÎÔÛ\u1E6D\u1E6C]+")

def load_decoupling_map():
    decouple_map = {}
    stats = {}

    # 1. Load -hai
    df_hai = pd.read_csv(HAI_FILE, sep="\t")
    for _, row in df_hai.iterrows():
        fused = str(row["fused_word"]).lower()
        stem = str(row["stem"]).lower()
        decouple_map[fused] = (stem, "hai")
    stats["hai_words"] = len(df_hai)

    # 2. Load -te
    df_te = pd.read_csv(TE_FILE, sep="\t")
    for _, row in df_te.iterrows():
        fused = str(row["word"]).lower()
        parts = row["ish_form"].lower().split()
        if len(parts) == 2:
            decouple_map[fused] = (parts[0], parts[1])
    stats["te_words"] = len(df_te)

    # 3. Load -chal
    df_chal = pd.read_csv(CHAL_FILE, sep="\t")
    for _, row in df_chal.iterrows():
        fused = str(row["word"]).lower()
        parts = row["ish_form"].lower().split()
        if len(parts) == 2:
            decouple_map[fused] = (parts[0], parts[1])
    stats["chal_words"] = len(df_chal)

    # 4. Load -pui
    df_pui = pd.read_csv(PUI_FILE, sep="\t")
    for _, row in df_pui.iterrows():
        fused = str(row["word"]).lower()
        parts = row["ish_form"].lower().split()
        if len(parts) == 2:
            decouple_map[fused] = (parts[0], parts[1])
    stats["pui_words"] = len(df_pui)

    # 5. Add case-inflected animal clitics (e.g. berâm-te-in, bâwng-chal-in, ar-pui-in)
    case_suffixes = [
        ("in", "in"), ("a", "a"), ("ah", "ah"), 
        ("hai", "hai"), ("haiin", "hai in"), ("haia", "hai a")
    ]
    # For every entry in te, chal, pui
    for df_sub, particle in [(df_te, "te"), (df_chal, "chal"), (df_pui, "pui")]:
        for _, row in df_sub.iterrows():
            parts = row["ish_form"].lower().split()
            if len(parts) == 2:
                stem = parts[0]
                base_fused = stem + particle
                for c_suff, c_target in case_suffixes:
                    c_fused = base_fused + c_suff
                    c_parts = [stem, particle] + c_target.split()
                    decouple_map[c_fused] = tuple(c_parts)

    # 6. Load Anchor Manifest (W+haiah -> W hai ah, W+haiin -> W hai in, W+ah -> W ah, W+in -> W in)
    df_anchor = pd.read_csv(ANCHOR_FILE, sep="\t")
    for _, row in df_anchor.iterrows():
        fused = str(row["fused_word"]).lower()
        parts = tuple(row["ish_target"].lower().split())
        decouple_map[fused] = parts
    stats["anchor_words"] = len(df_anchor)

    # 7. Load Remainder Splits (e.g. inah -> in ah, tinin -> tin in, etc.)
    df_rem = pd.read_csv(REM_FILE, sep="\t")
    df_rem_split = df_rem[df_rem["action"] == "SPLIT"]
    rem_count = 0
    for _, row in df_rem_split.iterrows():
        fused = str(row["fused_word"]).lower()
        parts = tuple(row["ish_target"].lower().split())
        if fused not in decouple_map:
            decouple_map[fused] = parts
            rem_count += 1
    stats["remainder_split_words"] = rem_count

    stats["total_decoupled_words"] = len(decouple_map)
    print(f"Loaded decoupling map: {len(decouple_map):,} total forms (including case variants)")
    print(f"  -hai plurals: {stats['hai_words']:,}")
    print(f"  -te animal diminutives: {stats['te_words']:,}")
    print(f"  -chal male animals: {stats['chal_words']:,}")
    print(f"  -pui female animals: {stats['pui_words']:,}")
    print(f"  -anchor postpositions (ah, in, haiah, haiin): {stats['anchor_words']:,}")
    print(f"  -remainder splits: {stats['remainder_split_words']:,}")
    return decouple_map, stats

def expand_token(tok, decouple_map, depth=0):
    if depth > 5:
        return [tok]
    if tok in decouple_map:
        parts = decouple_map[tok]
        res = []
        for p in parts:
            res.extend(expand_token(p, decouple_map, depth + 1))
        return res
    return [tok]

def clean_and_decouple(text, decouple_map):
    raw_tokens = [t.lower() for t in TOKEN_PATTERN.findall(text) if len(t) >= 1]
    decoupled = []
    for tok in raw_tokens:
        decoupled.extend(expand_token(tok, decouple_map))
    return decoupled

def stream_corpus(decouple_map):
    # 1. Stream CLB Bible
    print("Reading CLB Bible (31,102 verses)...")
    with open(BIBLE_CLB, "r", encoding="utf-8") as f:
        clb_data = json.load(f)
    for row in clb_data:
        yield clean_and_decouple(row["text"], decouple_map)

    # 2. Stream OV Bible
    print("Reading OV Bible (31,102 verses)...")
    with open(BIBLE_OV, "r", encoding="utf-8") as f:
        ov_data = json.load(f)
    for row in ov_data:
        yield clean_and_decouple(row["text"], decouple_map)

    # 3. Stream Normalized Markdown Corpus (587 articles)
    print("Reading Normalized Markdown Corpus (587 articles)...")
    for fpath in NORM_MD_ROOT.glob("**/*.md"):
        with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
            content = f.read()
        if "---" in content:
            content = content.split("---", 1)[-1]
        for line in re.split(r"[.!?\n\r]+", content):
            tokens = clean_and_decouple(line, decouple_map)
            if tokens:
                yield tokens

def main():
    decouple_map, stats = load_decoupling_map()

    unigram_counts = Counter()
    bigram_counts = defaultdict(Counter)
    trigram_counts = defaultdict(Counter)
    total_tokens = 0
    decoupled_events = 0

    print("Executing Unified Decoupling Pass across 3+ million words...")
    for sentence in stream_corpus(decouple_map):
        total_tokens += len(sentence)
        for i, word in enumerate(sentence):
            unigram_counts[word] += 1
            if i + 1 < len(sentence):
                w_next = sentence[i + 1]
                bigram_counts[word][w_next] += 1
            if i + 2 < len(sentence):
                w_next = sentence[i + 1]
                w_next2 = sentence[i + 2]
                trigram_counts[f"{word} {w_next}"][w_next2] += 1

    print(f"Total tokens processed after decoupling: {total_tokens:,}")
    print(f"Raw unique unigrams: {len(unigram_counts):,}")

    # Load deterministic map for ISH normalizations
    with open(DMAP_PATH, "r", encoding="utf-8") as f:
        dmap = json.load(f)

    # Ingest Canonical Dictionaries to ensure rare dictionary roots are included
    print("Ingesting dictionary headwords (Pulamte & Bapui)...")
    dict_added = 0
    for dpath in [DICT_001, DICT_004]:
        with open(dpath, "r", encoding="utf-8") as f:
            data = json.load(f)
        for item in data:
            w = item.get("word")
            if w:
                for tok in TOKEN_PATTERN.findall(w):
                    low = tok.lower()
                    if len(expand_token(low, decouple_map)) > 1:
                        continue  # Do not add decoupled words as single entries
                    if low in dmap:
                        low = dmap[low]["canonical"]
                    elif "thr" in low:
                        low = low.replace("thr", "ṭh")
                    elif "tr" in low:
                        low = low.replace("tr", "ṭ")
                    
                    if low not in unigram_counts:
                        unigram_counts[low] = 5  # Baseline count
                        dict_added += 1

    print(f"Added {dict_added:,} rare dictionary headwords to unigram vocabulary.")
    print(f"Total clean vocabulary size: {len(unigram_counts):,} words.")

    # Save Cleaned Unigrams TSV
    print(f"Saving cleaned ISH unigrams to {OUT_UNIGRAMS}...")
    with open(OUT_UNIGRAMS, "w", encoding="utf-8") as f:
        f.write("word\tcount\tish_form\n")
        for word, count in unigram_counts.most_common():
            ish_form = word.replace("ṭh", "thr").replace("ṭ", "tr")
            f.write(f"{word}\t{count}\t{ish_form}\n")

    # Save Bigrams JSONL (Next-Word Predictions)
    print(f"Saving cleaned ISH bigrams to {OUT_BIGRAMS}...")
    bigram_entries = 0
    with open(OUT_BIGRAMS, "w", encoding="utf-8") as f:
        for w1, counter in bigram_counts.items():
            top_preds = [{"word": w2, "count": c} for w2, c in counter.most_common(8)]
            if top_preds:
                rec = {"word": w1, "predictions": top_preds}
                f.write(json.dumps(rec, ensure_ascii=False) + "\n")
                bigram_entries += 1

    # Save Trigrams JSONL (Context Predictions)
    print(f"Saving cleaned ISH trigrams to {OUT_TRIGRAMS}...")
    trigram_entries = 0
    with open(OUT_TRIGRAMS, "w", encoding="utf-8") as f:
        for context, counter in trigram_counts.items():
            top_preds = [{"word": w3, "count": c} for w3, c in counter.most_common(5)]
            if top_preds:
                rec = {"context": context, "predictions": top_preds}
                f.write(json.dumps(rec, ensure_ascii=False) + "\n")
                trigram_entries += 1

    # Generate Report
    report = {
        "step": 3,
        "action": "unified_decoupling_pass",
        "decoupled_dictionary_stats": stats,
        "total_tokens_processed": total_tokens,
        "clean_vocabulary_size": len(unigram_counts),
        "bigram_words_with_predictions": bigram_entries,
        "trigram_contexts_stored": trigram_entries,
        "top_decoupled_unigram_boosts": {
            "hai_particle_count": unigram_counts.get("hai", 0),
            "te_particle_count": unigram_counts.get("te", 0),
            "chal_particle_count": unigram_counts.get("chal", 0),
            "pui_particle_count": unigram_counts.get("pui", 0),
            "mi_stem_count": unigram_counts.get("mi", 0),
            "berâm_stem_count": unigram_counts.get("berâm", 0),
            "bâwng_stem_count": unigram_counts.get("bâwng", 0),
            "kêl_stem_count": unigram_counts.get("kêl", 0),
            "ar_stem_count": unigram_counts.get("ar", 0)
        },
        "sample_clitic_predictions": {
            "berâm": [w for w, c in bigram_counts["berâm"].most_common(5)],
            "bâwng": [w for w, c in bigram_counts["bâwng"].most_common(5)],
            "kêl": [w for w, c in bigram_counts["kêl"].most_common(5)],
            "mi": [w for w, c in bigram_counts["mi"].most_common(5)],
            "nau": [w for w, c in bigram_counts["nau"].most_common(5)]
        }
    }

    with open(REPORT_FILE, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)

    print(f"Step 3 decoupling report saved to {REPORT_FILE}.")
    print("\nSample Next-Word Predictions for stems:")
    for stem, preds in report["sample_clitic_predictions"].items():
        print(f"  '{stem}' -> {preds}")

if __name__ == "__main__":
    main()
