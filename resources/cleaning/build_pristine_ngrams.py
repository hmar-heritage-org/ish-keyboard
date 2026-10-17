#!/usr/bin/env python3
"""
Sequential Step 2: Build Pristine Unigrams, Bigrams, and Trigrams.
Aggregates the 2 Bibles (CLB & OV), the normalized markdown corpus,
and the canonical dictionaries into clean N-gram predictive models.
"""

import os
import re
import json
from pathlib import Path
from collections import Counter, defaultdict

# Input Paths
BIBLE_CLB = Path("/home/phxlm/Work/HHF/datasets/corpus-archive/data/json/0010/file.json")
BIBLE_OV = Path("/home/phxlm/Work/HHF/datasets/corpus-archive/data/json/0011/file.json")
NORM_MD_ROOT = Path("/home/phxlm/Work/HHF/ISH-key/resources/cleaning/normalized_markdown")
DICT_001 = Path("/home/phxlm/Work/HHF/datasets/wordlist/data/dictionary-001.json")
DICT_004 = Path("/home/phxlm/Work/HHF/datasets/wordlist/data/dictionary-004.json")
DMAP_PATH = Path("/home/phxlm/Work/HHF/morphology/data/deterministic_map.json")

# Output Paths
OUT_DIR = Path("/home/phxlm/Work/HHF/ISH-key/resources/cleaning")
UNIGRAMS_FILE = OUT_DIR / "pristine_unigrams.tsv"
BIGRAMS_FILE = OUT_DIR / "pristine_bigrams.jsonl"
TRIGRAMS_FILE = OUT_DIR / "pristine_trigrams.jsonl"
REPORT_FILE = OUT_DIR / "step2_ngrams_report.json"

TOKEN_PATTERN = re.compile(r"[a-zA-ZṭṬâêîôûÂÊÎÔÛ\u1E6D\u1E6C]+")

def clean_sentence(text):
    tokens = TOKEN_PATTERN.findall(text)
    return [t.lower() for t in tokens if len(t) >= 1]

def stream_corpus():
    # 1. Stream CLB Bible
    print("Reading CLB Bible (31,102 verses)...")
    with open(BIBLE_CLB, "r", encoding="utf-8") as f:
        clb_data = json.load(f)
    for row in clb_data:
        yield clean_sentence(row["text"])

    # 2. Stream OV Bible
    print("Reading OV Bible (31,102 verses)...")
    with open(BIBLE_OV, "r", encoding="utf-8") as f:
        ov_data = json.load(f)
    for row in ov_data:
        yield clean_sentence(row["text"])

    # 3. Stream Normalized Markdown Corpus (587 files)
    print("Reading Normalized Markdown Corpus (587 articles)...")
    for fpath in NORM_MD_ROOT.glob("**/*.md"):
        with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
            content = f.read()
        if "---" in content:
            content = content.split("---", 1)[-1]
        # Split into sentence-like lines
        for line in re.split(r"[.!?\n\r]+", content):
            tokens = clean_sentence(line)
            if tokens:
                yield tokens

def main():
    unigram_counts = Counter()
    bigram_counts = defaultdict(Counter)
    trigram_counts = defaultdict(Counter)
    total_tokens = 0

    print("Aggregating N-grams across 3+ million words...")
    for sentence in stream_corpus():
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

    print(f"Total tokens processed: {total_tokens:,}")
    print(f"Raw unique unigrams from corpus: {len(unigram_counts):,}")

    # Load deterministic map to link ISH and Classical forms
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
                    # If token was in Pulamte using tr/thr, normalize to canonical
                    if low in dmap:
                        low = dmap[low]["canonical"]
                    elif "thr" in low:
                        low = low.replace("thr", "ṭh")
                    elif "tr" in low:
                        low = low.replace("tr", "ṭ")
                    
                    if low not in unigram_counts:
                        unigram_counts[low] = 5  # Baseline count for rare valid dictionary words
                        dict_added += 1

    print(f"Added {dict_added:,} rare dictionary headwords to unigram vocabulary.")
    print(f"Total vocabulary size: {len(unigram_counts):,} words.")

    # Save Unigrams TSV
    print(f"Saving pristine unigrams to {UNIGRAMS_FILE}...")
    with open(UNIGRAMS_FILE, "w", encoding="utf-8") as f:
        f.write("word\tcount\tish_form\n")
        for word, count in unigram_counts.most_common():
            # Generate ISH form (ṭ -> tr, ṭh -> thr)
            ish_form = word.replace("ṭh", "thr").replace("ṭ", "tr")
            f.write(f"{word}\t{count}\t{ish_form}\n")

    # Save Bigrams JSONL (Next-Word Predictions)
    print(f"Saving pristine bigrams (next-word model) to {BIGRAMS_FILE}...")
    bigram_entries = 0
    with open(BIGRAMS_FILE, "w", encoding="utf-8") as f:
        for w1, counter in bigram_counts.items():
            # Keep top 8 predictions per word to keep mobile model fast and compact
            top_preds = [{"word": w2, "count": c} for w2, c in counter.most_common(8)]
            if top_preds:
                rec = {"word": w1, "predictions": top_preds}
                f.write(json.dumps(rec, ensure_ascii=False) + "\n")
                bigram_entries += 1

    # Save Trigrams JSONL (3-word Context Predictions)
    print(f"Saving pristine trigrams (context model) to {TRIGRAMS_FILE}...")
    trigram_entries = 0
    with open(TRIGRAMS_FILE, "w", encoding="utf-8") as f:
        for context, counter in trigram_counts.items():
            # Only keep contexts that appeared at least twice
            total_context_count = sum(counter.values())
            if total_context_count >= 2:
                top_preds = [{"word": w3, "count": c} for w3, c in counter.most_common(5)]
                rec = {"context": context, "predictions": top_preds}
                f.write(json.dumps(rec, ensure_ascii=False) + "\n")
                trigram_entries += 1

    # Sample Next-Word Predictions for evaluation
    test_words = ["kan", "a", "pathien", "vân", "hung", "chu", "ṭha"]
    sample_eval = {}
    for tw in test_words:
        sample_eval[tw] = [w for w, _ in bigram_counts[tw].most_common(5)]

    test_trigrams = ["a hung", "kan ram", "pathien in", "vân le"]
    sample_tri_eval = {}
    for tc in test_trigrams:
        sample_tri_eval[tc] = [w for w, _ in trigram_counts[tc].most_common(5)]

    # Generate Report
    report = {
        "step": 2,
        "total_tokens_processed": total_tokens,
        "unique_unigrams": len(unigram_counts),
        "bigram_words_with_predictions": bigram_entries,
        "trigram_contexts_stored": trigram_entries,
        "top_30_unigrams": [
            {"word": w, "count": c} for w, c in unigram_counts.most_common(30)
        ],
        "sample_next_word_predictions": sample_eval,
        "sample_trigram_predictions": sample_tri_eval
    }

    with open(REPORT_FILE, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)

    print(f"\nStep 2 Complete! Audit report saved to: {REPORT_FILE}")

if __name__ == "__main__":
    main()
