#!/usr/bin/env python3
"""
Sequential Step 4: Blend English Loanwords (Top 25k).
Combines the pristine Hmar unigrams with the top 25k English words from the Google 1T corpus.
Guards native Hmar homographs and scales frequencies smoothly for mobile prediction.
"""

import os
import math
import json
import pandas as pd
from pathlib import Path

# Paths
CLEANING_DIR = Path("/home/phxlm/Work/HHF/ISH-key/resources/cleaning")
HMAR_UNIGRAMS = CLEANING_DIR / "ish_unigrams.tsv"
ENG_CORPUS = Path("/home/phxlm/Work/HHF/ISH-key/resources/lexicon/english/google-333k-english.txt")
OUT_DUAL_UNIGRAMS = CLEANING_DIR / "ish_dual_lexicon_unigrams.tsv"
REPORT_FILE = CLEANING_DIR / "step4_english_blend_report.json"

TOP_K_ENGLISH = 25000

def main():
    print("Loading pristine Hmar unigrams...")
    df_hmar = pd.read_csv(HMAR_UNIGRAMS, sep="\t")
    hmar_dict = {}
    for _, row in df_hmar.iterrows():
        hmar_dict[str(row["word"]).lower()] = (int(row["count"]), str(row["ish_form"]))

    print(f"Loaded {len(hmar_dict):,} pristine Hmar unigrams.")

    print(f"Loading top {TOP_K_ENGLISH:,} English words from Google corpus...")
    eng_words = []
    with open(ENG_CORPUS, "r", encoding="utf-8") as f:
        for idx, line in enumerate(f):
            if idx >= TOP_K_ENGLISH:
                break
            parts = line.strip().split()
            if len(parts) == 2:
                eng_words.append((parts[0].lower(), int(parts[1])))

    max_raw = eng_words[0][1]
    min_raw = eng_words[-1][1]
    print(f"English raw count range: {min_raw:,} to {max_raw:,}")

    def scale_count(raw):
        # Scale to range [5, 350]
        norm = (math.log(raw) - math.log(min_raw)) / (math.log(max_raw) - math.log(min_raw))
        return max(5, int(5 + norm * 345))

    homographs_guarded = []
    loanwords_added = []

    for word, raw_count in eng_words:
        if word in hmar_dict:
            homographs_guarded.append((word, hmar_dict[word][0], raw_count))
        else:
            scaled = scale_count(raw_count)
            # In English loanwords, ish_form is the English spelling itself
            loanwords_added.append({
                "word": word,
                "count": scaled,
                "ish_form": word,
                "origin": "english_loanword"
            })

    print(f"Guarded native Hmar homographs: {len(homographs_guarded):,}")
    print(f"Pure English loanwords added: {len(loanwords_added):,}")

    # Build combined unigram dataset
    combined_rows = []
    # 1. Add all Hmar words
    for w, (cnt, ish) in hmar_dict.items():
        combined_rows.append({
            "word": w,
            "count": cnt,
            "ish_form": ish,
            "origin": "hmar_native"
        })

    # 2. Add English loanwords
    for item in loanwords_added:
        combined_rows.append(item)

    df_combined = pd.DataFrame(combined_rows)
    df_combined = df_combined.sort_values(by="count", ascending=False)

    print(f"Total dual-lexicon vocabulary size: {len(df_combined):,} words")

    # Save to TSV
    print(f"Saving dual-lexicon unigrams to {OUT_DUAL_UNIGRAMS}...")
    df_combined.to_csv(OUT_DUAL_UNIGRAMS, sep="\t", index=False)

    # Save Audit Report
    report = {
        "step": 4,
        "action": "english_loanword_blend",
        "hmar_native_words": len(hmar_dict),
        "english_top_k_loaded": len(eng_words),
        "homographs_guarded_count": len(homographs_guarded),
        "pure_loanwords_added_count": len(loanwords_added),
        "total_dual_vocabulary_size": len(df_combined),
        "sample_guarded_homographs": [
            {"word": w, "hmar_corpus_count": hc} for w, hc, rc in homographs_guarded[:20]
        ],
        "sample_loanwords_added": [
            {"word": item["word"], "scaled_count": item["count"]} for item in loanwords_added[:25]
        ]
    }

    with open(REPORT_FILE, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)

    print(f"Report saved to {REPORT_FILE}.")

if __name__ == "__main__":
    main()
