#!/usr/bin/env python3
"""
Anchor-based Forward Decoupling Scanner for Hmar.
1. Uses verified clean anchor roots W to forward-generate:
   - W + haiah -> W hai ah
   - W + haiin -> W hai in
   - W + ah    -> W ah
   - W + in    -> W in
2. Resolves standalone 'haiah' -> 'hai ah' and 'haiin' -> 'hai in'.
3. Quarantines and categorizes the remainder (inviolate roots, proper nouns, compound forms).
"""

import json
from pathlib import Path
import pandas as pd

CLEANING_DIR = Path("/home/phxlm/Work/HHF/ISH-key/resources/cleaning")
UNIGRAMS_FILE = CLEANING_DIR / "pristine_unigrams.tsv"
DICT_DIR = Path("/home/phxlm/Work/HHF/datasets/wordlist/data")

# 1. Load dictionary words
dict_words = set()
for d_name in ["dictionary-001.json", "dictionary-002.json", "dictionary-003.json", "dictionary-004.json"]:
    p = DICT_DIR / d_name
    if p.exists():
        with open(p, "r", encoding="utf-8") as f:
            data = json.load(f)
            for item in data:
                if isinstance(item, dict):
                    w = str(item.get("word") or item.get("headword") or "").strip().lower()
                    if w and len(w) >= 2 and " " not in w:
                        dict_words.add(w)

VOWELS = set("aeiouâêîôûáéíóúàèìòù")

# Filter roots: must contain at least one vowel and not end with target suffixes
clean_roots = {w for w in dict_words if any(c in VOWELS for c in w) and not w.endswith(("ah", "in", "hai", "haiin", "haiah"))}

# 2. Load corpus unigrams
df_uni = pd.read_csv(UNIGRAMS_FILE, sep="\t")
corpus_vocab = {str(w).lower(): c for w, c in zip(df_uni["word"], df_uni["count"])}

# Add frequent validated nouns/roots from corpus (freq >= 20, len >= 3, must have vowel)
for w, c in corpus_vocab.items():
    if c >= 20 and len(w) >= 3 and any(c_v in VOWELS for c_v in w) and not w.endswith(("ah", "in", "hai", "haiin", "haiah")):
        clean_roots.add(w)

# Explicitly remove consonant-only or false roots
clean_roots.discard("thl")
clean_roots.discard("ngh")
clean_roots.discard("ttr")

print(f"Total clean anchor roots: {len(clean_roots):,}")

# Forward Matching
anchor_splits = []

# Standalone cases
if "haiah" in corpus_vocab:
    anchor_splits.append({
        "fused_word": "haiah",
        "ish_target": "hai ah",
        "category": "standalone_haiah",
        "tokens": corpus_vocab["haiah"]
    })
if "haiin" in corpus_vocab:
    anchor_splits.append({
        "fused_word": "haiin",
        "ish_target": "hai in",
        "category": "standalone_haiin",
        "tokens": corpus_vocab["haiin"]
    })

resolved_words = {"haiah", "haiin"}

# 1. W + haiah -> W hai ah
for root in clean_roots:
    cand = root + "haiah"
    if cand in corpus_vocab and cand not in resolved_words:
        resolved_words.add(cand)
        anchor_splits.append({
            "fused_word": cand,
            "ish_target": f"{root} hai ah",
            "category": "W_haiah",
            "tokens": corpus_vocab[cand]
        })

# 2. W + haiin -> W hai in
for root in clean_roots:
    cand = root + "haiin"
    if cand in corpus_vocab and cand not in resolved_words:
        resolved_words.add(cand)
        anchor_splits.append({
            "fused_word": cand,
            "ish_target": f"{root} hai in",
            "category": "W_haiin",
            "tokens": corpus_vocab[cand]
        })

# 3. W + ah -> W ah
for root in clean_roots:
    cand = root + "ah"
    if cand in corpus_vocab and cand not in resolved_words:
        resolved_words.add(cand)
        anchor_splits.append({
            "fused_word": cand,
            "ish_target": f"{root} ah",
            "category": "W_ah",
            "tokens": corpus_vocab[cand]
        })

# 4. W + in -> W in
for root in clean_roots:
    cand = root + "in"
    if cand in corpus_vocab and cand not in resolved_words:
        resolved_words.add(cand)
        anchor_splits.append({
            "fused_word": cand,
            "ish_target": f"{root} in",
            "category": "W_in",
            "tokens": corpus_vocab[cand]
        })

df_anchor = pd.DataFrame(anchor_splits).sort_values(by="tokens", ascending=False)
out_anchor_tsv = CLEANING_DIR / "anchor_decoupling_manifest.tsv"
out_anchor_json = CLEANING_DIR / "anchor_decoupling_manifest.json"
df_anchor.to_csv(out_anchor_tsv, sep="\t", index=False)
with open(out_anchor_json, "w", encoding="utf-8") as f:
    json.dump({row["fused_word"]: row["ish_target"].split() for _, row in df_anchor.iterrows()}, f, indent=2)

print(f"\n--- Forward Anchor Matches Locked ---")
print(f"Total words resolved: {len(df_anchor):,}")
print(f"Total token occurrences: {df_anchor['tokens'].sum():,}")
print(df_anchor["category"].value_counts())

# Remainder Analysis
# Find all words ending in ah, in, haiah, haiin not yet in resolved_words
INVIOLATE_ROOTS = {
    "ah", "tah", "khah", "dah", "thlah", "chah", "bah", "hah", "pah", "rah",
    "chiah", "thah", "mah", "sah", "fah", "lah", "ngah", "gah", "tlah", "nghah",
    "in", "hin", "tin", "sin", "din", "rin", "pin", "min", "kin", "bin", "fin",
    "lin", "nin", "hrin", "chin", "ṭhin", "thlin", "khin", "thin",
    "chanchin", "famin", "champhamin", "têkin", "pêkchin", "thilchin",
    "iengtin", "Benjamin", "benjamin", "philistin", "Philistin", "watkin", "Watkin"
}

remainder = []
for w, c in corpus_vocab.items():
    if w in resolved_words:
        continue
    if w.endswith(("haiah", "haiin", "ah", "in")):
        # Categorize
        if w in INVIOLATE_ROOTS or len(w) <= 2:
            action = "KEEP"
            reason = "inviolate_root"
            target = w
        elif w == "inah":
            action = "SPLIT"
            reason = "in_plus_ah"
            target = "in ah"
        elif w == "tinah":
            action = "SPLIT"
            reason = "tin_plus_ah"
            target = "tin ah"
        elif w == "tinin":
            action = "SPLIT"
            reason = "tin_plus_in"
            target = "tin in"
        elif w == "chuleiin":
            action = "SPLIT"
            reason = "chulei_plus_in"
            target = "chulei in"
        elif w == "mithmuah":
            action = "SPLIT"
            reason = "mithmu_plus_ah"
            target = "mithmu ah"
        elif w == "lovin":
            action = "SPLIT"
            reason = "lov_plus_in"
            target = "lo in"
        elif w.endswith("haiah") and len(w) > 6:
            stem = w[:-5]
            action = "SPLIT"
            reason = "remainder_haiah"
            target = f"{stem} hai ah"
        elif w.endswith("haiin") and len(w) > 6:
            stem = w[:-5]
            action = "SPLIT"
            reason = "remainder_haiin"
            target = f"{stem} hai in"
        elif w.endswith("ah") and len(w) > 3 and w[:-2] in corpus_vocab:
            action = "SPLIT"
            reason = "corpus_stem_ah"
            target = f"{w[:-2]} ah"
        elif w.endswith("in") and len(w) > 3 and w[:-2] in corpus_vocab:
            action = "SPLIT"
            reason = "corpus_stem_in"
            target = f"{w[:-2]} in"
        else:
            action = "REVIEW"
            reason = "unverified"
            target = w
            
        remainder.append({
            "fused_word": w,
            "action": action,
            "reason": reason,
            "ish_target": target,
            "tokens": c
        })

df_rem = pd.DataFrame(remainder).sort_values(by="tokens", ascending=False)
out_rem_tsv = CLEANING_DIR / "remainder_review.tsv"
df_rem.to_csv(out_rem_tsv, sep="\t", index=False)

print(f"\n--- Remainder Analysis ---")
print(f"Total remainder words: {len(df_rem):,}")
print(df_rem["action"].value_counts())
print(f"\nTop 15 Remainder words:")
print(df_rem[["fused_word", "action", "reason", "ish_target", "tokens"]].head(15).to_string(index=False))
