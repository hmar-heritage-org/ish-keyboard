#!/usr/bin/env python3
"""
Scanner for words ending in -ah and -in.
Generates ah_candidates_review.tsv and in_candidates_review.tsv
Cross-references stems against dictionaries and corpus unigrams.
Identifies inviolate root exclusions.
"""

import os
import json
import re
import pandas as pd
from pathlib import Path
from collections import defaultdict

CLEANING_DIR = Path("/home/phxlm/Work/HHF/ISH-key/resources/cleaning")
UNIGRAMS_FILE = CLEANING_DIR / "ish_unigrams.tsv"
DICT_DIR = Path("/home/phxlm/Work/HHF/datasets/wordlist/data")

# 1. Load Dictionary Vocabulary
dict_words = set()
for d_name in ["dictionary-001.json", "dictionary-002.json", "dictionary-003.json", "dictionary-004.json", "dictionary-005.json"]:
    d_path = DICT_DIR / d_name
    if d_path.exists():
        try:
            with open(d_path, "r", encoding="utf-8") as f:
                data = json.load(f)
                if isinstance(data, list):
                    for item in data:
                        if isinstance(item, dict):
                            w = item.get("word") or item.get("headword") or ""
                            w = w.strip().lower()
                            if w:
                                # handle multiword glosses
                                for sub_w in w.split():
                                    dict_words.add(sub_w.lower())
                elif isinstance(data, dict):
                    for k in data.keys():
                        dict_words.add(k.strip().lower())
        except Exception as e:
            print(f"Warning loading {d_name}: {e}")

print(f"Loaded {len(dict_words):,} unique headwords from dictionaries.")

# 2. Load Corpus Unigrams
df_uni = pd.read_csv(UNIGRAMS_FILE, sep="\t")
corpus_vocab = dict(zip(df_uni["word"].astype(str).str.lower(), df_uni["count"]))
print(f"Loaded {len(corpus_vocab):,} corpus unigrams.")

# Vowels set for syllable checks
VOWELS = set("aeiouâêîôûáéíóúàèìòù")

# Inviolate root words that MUST NEVER be split
INVIOLATE_AH_ROOTS = {
    "ah", "tah", "khah", "dah", "thlah", "chah", "bah", "hah", "pah", "rah",
    "chiah", "thah", "mah", "sah", "fah", "lah", "ngah", "gah",
    "vawikhatmah", "engahmah", "tukhawmah", "tukhawma", "tuomah", "iengmah",
    "iemah", "tuompah", "ruolpah", "inpah", "thlapah"
}

INVIOLATE_IN_ROOTS = {
    "in", "hin", "tin", "sin", "din", "rin", "pin", "min", "kin", "bin", "fin",
    "lin", "nin", "hrin", "chin", "ṭhin", "thlin",
    "chanchin", "famin", "champhamin", "têkin", "pêkchin", "thilchin",
    "lungchin", "rilchin", "khawchin", "vânchin", "tlawmchin", "hrietin",
    "vawrhin", "fimin", "khatin"
}

def analyze_ah_word(word, count):
    if len(word) <= 2:
        return "KEEP", "standalone_word", word, ""
    if word in INVIOLATE_AH_ROOTS:
        return "KEEP", "inviolate_root", word, ""
    
    stem = word[:-2] # drop 'ah'
    
    # Must contain at least one vowel
    if not any(c in VOWELS for c in stem):
        return "KEEP", "stem_has_no_vowel", word, ""
    
    # Length check: stem must be >= 2
    if len(stem) < 2:
        return "KEEP", "stem_too_short", word, ""
        
    # Check if stem is in dictionaries or corpus
    in_dict = stem in dict_words
    stem_count = corpus_vocab.get(stem, 0)
    
    if in_dict or stem_count >= 2:
        return "SPLIT", "valid_stem", stem, "ah"
    
    # If stem not directly found, check if stem ends with 'a' or diphthong
    # e.g. khuoah -> khuo (in dict), hnuoiah -> hnuoi (in dict)
    if stem_count > 0:
        return "SPLIT", "stem_in_corpus", stem, "ah"
        
    return "REVIEW", "unverified_stem", stem, "ah"

def analyze_in_word(word, count):
    if len(word) <= 2:
        return "KEEP", "standalone_word", word, ""
    if word in INVIOLATE_IN_ROOTS:
        return "KEEP", "inviolate_root", word, ""
    
    stem = word[:-2] # drop 'in'
    
    # Must contain at least one vowel
    if not any(c in VOWELS for c in stem):
        return "KEEP", "stem_has_no_vowel", word, ""
        
    # Length check
    if len(stem) < 2:
        return "KEEP", "stem_too_short", word, ""
        
    # Handle ergative on words ending in 'a' like 'lalpain' -> 'lalpa' + 'in'
    # 'stem' is already 'lalpa'
    in_dict = stem in dict_words
    stem_count = corpus_vocab.get(stem, 0)
    
    if in_dict or stem_count >= 2:
        return "SPLIT", "valid_stem", stem, "in"
        
    if stem_count > 0:
        return "SPLIT", "stem_in_corpus", stem, "in"
        
    return "REVIEW", "unverified_stem", stem, "in"

# Process all unigrams
ah_records = []
in_records = []

for _, row in df_uni.iterrows():
    w = str(row["word"]).lower().strip()
    c = int(row["count"])
    
    if w.endswith("ah") and len(w) >= 2:
        action, reason, stem, suff = analyze_ah_word(w, c)
        ah_records.append({
            "fused_word": w,
            "action": action,
            "reason": reason,
            "stem": stem,
            "suffix": suff,
            "count": c,
            "stem_in_dict": stem in dict_words,
            "stem_corpus_freq": corpus_vocab.get(stem, 0)
        })
        
    if w.endswith("in") and len(w) >= 2:
        action, reason, stem, suff = analyze_in_word(w, c)
        in_records.append({
            "fused_word": w,
            "action": action,
            "reason": reason,
            "stem": stem,
            "suffix": suff,
            "count": c,
            "stem_in_dict": stem in dict_words,
            "stem_corpus_freq": corpus_vocab.get(stem, 0)
        })

df_ah = pd.DataFrame(ah_records).sort_values(by="count", ascending=False)
df_in = pd.DataFrame(in_records).sort_values(by="count", ascending=False)

out_ah = CLEANING_DIR / "ah_candidates_review.tsv"
out_in = CLEANING_DIR / "in_candidates_review.tsv"

df_ah.to_csv(out_ah, sep="\t", index=False)
df_in.to_csv(out_in, sep="\t", index=False)

print(f"\n--- AH Summary ---")
print(f"Total AH candidate words: {len(df_ah)}")
print(df_ah["action"].value_counts())
print(f"Top 10 SPLIT candidates:")
print(df_ah[df_ah["action"] == "SPLIT"][["fused_word", "stem", "count", "stem_in_dict"]].head(10).to_string(index=False))
print(f"\nTop 10 KEEP candidates:")
print(df_ah[df_ah["action"] == "KEEP"][["fused_word", "reason", "count"]].head(10).to_string(index=False))

print(f"\n--- IN Summary ---")
print(f"Total IN candidate words: {len(df_in)}")
print(df_in["action"].value_counts())
print(f"Top 10 SPLIT candidates:")
print(df_in[df_in["action"] == "SPLIT"][["fused_word", "stem", "count", "stem_in_dict"]].head(10).to_string(index=False))
print(f"\nTop 10 KEEP candidates:")
print(df_in[df_in["action"] == "KEEP"][["fused_word", "reason", "count"]].head(10).to_string(index=False))
