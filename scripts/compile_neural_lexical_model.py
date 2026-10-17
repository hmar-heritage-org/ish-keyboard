#!/usr/bin/env python3
"""
Compiles the neural predictive n-gram transitions and ISH Morphological Fused-Split Map
into the Hmar ISH Lexical Model (hhf.hmr.ish.model.js).
Creates a hybrid model that provides:
1. Fast prefix completion for partial words (via TrieModel)
2. Fused word decoupling (Slot 1: literal fused, Slot 2: ISH canonical split, Slot 3: contextual extension)
3. Accurate next-word & speculative phrase predictions (via HmarGPT-mini-v2 transitions)
"""

import os
import sys
import json
import re
from pathlib import Path
from huggingface_hub import hf_hub_download

REPO_ID = "azinamotoe/hmar-ish-dataset"
TOKEN = os.environ.get("HF_TOKEN")
SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent if SCRIPT_DIR.name == "scripts" else SCRIPT_DIR

CLEANING_DIR = REPO_ROOT / "resources" / "cleaning"
ASSET_MODEL_JS = str(REPO_ROOT / "android" / "app" / "src" / "main" / "assets" / "models" / "hhf.hmr.ish" / "hhf.hmr.ish.model.js")
SOURCE_MODEL_JS = str(REPO_ROOT / "lexical_model" / "hhf.hmr.ish" / "source" / "hhf.hmr.ish.model.js")

def load_fused_manifest():
    print("Loading fused-to-split morphological maps...")
    fused_map = {}

    # 1. High-frequency verbal and pronominal prefixes
    prefixes = {
        # a- / an- (3rd person)
        'anih': 'a nih', 'ani': 'a ni', 'alo': 'a lo', 'ahung': 'a hung', 'aum': 'a um',
        'athra': 'a ṭha', 'atha': 'a ṭha', 'apawimaw': 'a pawimaw', 'apawimawh': 'a pawimawh',
        'aphat': 'a phat', 'ati': 'a ti', 'ahriet': 'a hriet', 'anei': 'a nei', 'ala': 'a la',
        'afe': 'a fe', 'ana': 'a na', 'asawt': 'a sawt', 'athawk': 'a ṭhawk', 'aṭhawk': 'a ṭhawk',
        'anvaia': 'an vai a', 'anvaiah': 'an vai ah', 'anvaiin': 'an vai in',
        'anmani': 'an mani', 'anfe': 'an fe', 'anti': 'an ti', 'anlo': 'an lo',
        'anhung': 'an hung', 'anum': 'an um', 'anthra': 'an ṭha', 'anṭha': 'an ṭha',
        'annih': 'an nih', 'anni': 'an ni',
        # ka- / kan- (1st person)
        'kanih': 'ka nih', 'kani': 'ka ni', 'kahung': 'ka hung', 'kalo': 'ka lo', 'kaum': 'ka um',
        'kati': 'ka ti', 'kafe': 'ka fe', 'kanei': 'ka nei', 'kahriet': 'ka hriet', 'kala': 'ka la',
        'kannih': 'kan nih', 'kanni': 'kan ni', 'kanhung': 'kan hung', 'kanlo': 'kan lo',
        'kanum': 'kan um', 'kanti': 'kan ti', 'kanfe': 'kan fe', 'kanvaia': 'kan vai a',
        'kanvaiin': 'kan vai in', 'kanmani': 'kan mani',
        # i- / in- (2nd person)
        'inih': 'i nih', 'ini': 'i ni', 'ihung': 'i hung', 'ilo': 'i lo', 'ium': 'i um',
        'iti': 'i ti', 'ife': 'i fe', 'inei': 'i nei', 'ihriet': 'i hriet', 'ila': 'i la',
        'innih': 'in nih', 'inni': 'in ni', 'inhung': 'in hung', 'inlo': 'in lo',
        'inum': 'in um', 'inti': 'in ti', 'infe': 'in fe'
    }
    fused_map.update(prefixes)

    # 2. Anchor manifest (ah, in, haiah, haiin)
    anchor_path = CLEANING_DIR / 'anchor_decoupling_manifest.json'
    if anchor_path.exists():
        with open(anchor_path, 'r', encoding='utf-8') as f:
            anchors = json.load(f)
            for k, v in anchors.items():
                fused_map[k.lower()] = ' '.join(v) if isinstance(v, list) else v.lower()

    # 3. Hai candidates
    hai_path = CLEANING_DIR / 'hai_candidates_full.json'
    if hai_path.exists():
        with open(hai_path, 'r', encoding='utf-8') as f:
            hais = json.load(f)
            for item in hais:
                if isinstance(item, dict):
                    fused_map[item['fused_word'].lower()] = f"{item['stem'].lower()} hai"
                elif isinstance(item, str):
                    fused_map[item.lower()] = f"{item[:-3].lower()} hai"

    # 4. Te, Chal, Pui
    for fname, p in [('te_to_separate.json', 'te'), ('chal_to_separate.json', 'chal'), ('pui_to_separate.json', 'pui')]:
        p_path = CLEANING_DIR / fname
        if p_path.exists():
            with open(p_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
                for item in data:
                    if isinstance(item, dict):
                        word = item['word'].lower()
                        target = item.get('ish_form', f"{word[:-len(p)]} {p}").lower()
                        fused_map[word] = target

    print(f"Total verified fused mappings loaded: {len(fused_map):,}")
    return fused_map

def main():
    print("=" * 65)
    print(" Compiling Neural Predictive Lexical Model for Hmar ISH Keyboard")
    print("=" * 65)
    
    # 1. Download / Load transitions
    print(f"Fetching transitions from Hugging Face ({REPO_ID})...")
    trans_path = hf_hub_download(
        repo_id=REPO_ID,
        filename="ish_ngram_transitions.json",
        repo_type="dataset",
        token=TOKEN
    )
    print(f"Loaded transitions from: {trans_path}")
    
    with open(trans_path, "r", encoding="utf-8") as f:
        raw_transitions = json.load(f)
    print(f"Total raw contexts: {len(raw_transitions):,}")
    
    # Prune transitions to top-6 next words per context
    pruned_transitions = {}
    for ctx, nxt_dict in raw_transitions.items():
        if not nxt_dict:
            continue
        sorted_candidates = sorted(nxt_dict.items(), key=lambda x: x[1], reverse=True)[:6]
        total_count = sum(c for _, c in sorted_candidates)
        norm_ctx = ctx.lower().strip()
        pruned_transitions[norm_ctx] = [[w, round(c / total_count, 3)] for w, c in sorted_candidates]
        
    print(f"Pruned contexts: {len(pruned_transitions):,}")
    transitions_json = json.dumps(pruned_transitions, ensure_ascii=False, separators=(',', ':'))
    print(f"Compact transitions payload size: {len(transitions_json.encode('utf-8')) / (1024*1024):.2f} MB")
    
    # 2. Load Fused Map
    fused_map = load_fused_manifest()
    fused_json = json.dumps(fused_map, ensure_ascii=False, separators=(',', ':'))
    print(f"Compact fused map payload size: {len(fused_json.encode('utf-8')) / 1024:.2f} KB")

    # 3. Read current file from SOURCE_MODEL_JS (which kmc compiled)
    with open(SOURCE_MODEL_JS, "r", encoding="utf-8") as f:
        content = f.read()
        
    # Find the trie data start
    trie_marker = '{"totalWeight":'
    idx_trie = content.find(trie_marker)
    if idx_trie == -1:
        print("Error: Could not locate trie data.")
        sys.exit(1)
        
    opt_marker = ", {\n  wordBreaker:"
    idx_opt = content.find(opt_marker, idx_trie)
    if idx_opt == -1:
        opt_marker = ",{\n  wordBreaker:"
        idx_opt = content.find(opt_marker, idx_trie)
        
    trie_data = content[idx_trie:idx_opt]
    
    # Construct clean, syntactically guaranteed JS
    full_js = f"""(function() {{
'use strict';
var definitions = {{
  defaults: {{
    version: "18.0.253",
    applyCasing: function defaultApplyCasing(casing, text) {{
      switch (casing) {{
        case 'lower':
          return text.toLowerCase();
        case 'upper':
          return text.toUpperCase();
        case 'initial':
          var headCode = text.charCodeAt(0);
          var headUnitLength = 1;
          if (text.length > 1 && headCode >= 0xD800 && headCode <= 0xDBFF) {{
            var lowSurrogateCode = text.charCodeAt(1);
            if (lowSurrogateCode >= 0xDC00 && lowSurrogateCode <= 0xDFFF) {{
              headUnitLength++;
            }}
          }}
          return text.substring(0, headUnitLength).toUpperCase().concat(text.substring(headUnitLength));
      }}
    }}
  }},
  model: {{
    searchTermToKey: function defaultCasedSearchTermToKey(wordform, applyCasing) {{
      return Array.from(wordform
        .normalize('NFKD')
        .replace(/[\\u0300-\\u036F]/g, ''))
        .map(function (c) {{ return applyCasing('lower', c); }})
        .join('')
        .replace(/[‘’]/g, "'")
        .replace(/[“”]/g, '"');
    }}
  }},
  applyCasing: function(caseToApply, text) {{
    return definitions.defaults.applyCasing(caseToApply, text);
  }},
  searchTermToKey: function(text) {{
    return definitions.model.searchTermToKey(text, definitions.applyCasing);
  }}
}};

var _neuralTransitions = {transitions_json};
var _fusedMap = {fused_json};

function matchCase(original, target) {{
  if (!original || !target) return target;
  if (original === original.toUpperCase()) {{
    return target.toUpperCase();
  }}
  if (original[0] === original[0].toUpperCase()) {{
    return target.charAt(0).toUpperCase() + target.slice(1);
  }}
  return target;
}}

function getMorphologicalSplit(word, fusedMap) {{
  if (!word || word.length < 3) return null;
  var low = word.toLowerCase();
  if (fusedMap[low]) {{
    return fusedMap[low];
  }}
  // Dynamic Suffixes
  if (low.endsWith("haiah") && low.length > 5) {{
    return low.slice(0, -5) + " hai ah";
  }} else if (low.endsWith("haiin") && low.length > 5) {{
    return low.slice(0, -5) + " hai in";
  }} else if (low.endsWith("hai") && low.length > 4 && !low.endsWith("thai") && !low.endsWith("phai")) {{
    return low.slice(0, -3) + " hai";
  }} else if (low.endsWith("tein") && low.length > 4) {{
    return low.slice(0, -4) + " te in";
  }} else if (low.endsWith("teah") && low.length > 4) {{
    return low.slice(0, -4) + " te ah";
  }} else if (low.endsWith("te") && low.length > 3) {{
    return low.slice(0, -2) + " te";
  }} else if (low.endsWith("chal") && low.length > 5) {{
    return low.slice(0, -4) + " chal";
  }} else if (low.endsWith("puiin") && low.length > 5) {{
    return low.slice(0, -5) + " pui in";
  }} else if (low.endsWith("puiah") && low.length > 5) {{
    return low.slice(0, -5) + " pui ah";
  }} else if (low.endsWith("pui") && low.length > 4) {{
    return low.slice(0, -3) + " pui";
  }} else if (low.endsWith("naw") && low.length > 5) {{
    return low.slice(0, -3) + " naw";
  }} else if (low.endsWith("takin") && low.length > 6) {{
    return low.slice(0, -5) + " tak in";
  }} else if (low.endsWith("tak") && low.length > 5) {{
    return low.slice(0, -3) + " tak";
  }} else if (low.endsWith("zie") && low.length > 4) {{
    return low.slice(0, -3) + " zie";
  }} else if (low.endsWith("lem") && low.length > 4) {{
    return low.slice(0, -3) + " lem";
  }}
  return null;
}}

function HmarHybridModel(baseTrie, transitions, fusedMap) {{
  this.base = baseTrie;
  this.transitions = transitions;
  this.fusedMap = fusedMap;
  this.punctuation = baseTrie.punctuation;
  this.languageUsesCasing = true;
  this.applyCasing = baseTrie.applyCasing;
  this.wordbreaker = baseTrie.wordbreaker;
}}

HmarHybridModel.prototype.configure = function(caps) {{
  return this.base.configure(caps);
}};

HmarHybridModel.prototype.toKey = function(word) {{
  return this.base.toKey(word);
}};

HmarHybridModel.prototype.predict = function(transform, context) {{
  var left = (context && context.left) ? context.left : "";
  if (transform && transform.insert && !left.endsWith(transform.insert)) {{
    left += transform.insert;
  }}
  
  // Keyman applies sample.transform against preInput context!
  // Any characters inserted by the current keystroke transform are NOT yet in preInput.
  var delOffset = transform ? ((transform.deleteLeft || 0) - (transform.insert ? transform.insert.length : 0)) : 0;
  
  var isBoundary = false;
  if (!left || left.length === 0 || /\\s$/.test(left)) {{
    isBoundary = true;
  }}
  
  // 1. Boundary: Auto-Split on Space or Next-Word Prediction
  if (isBoundary && left.trim().length > 0) {{
    var words = left.trim().split(/\\s+/);
    var prevWord = words[words.length - 1];
    
    // Auto-split when space is pressed right after a fused word
    var autoSplit = getMorphologicalSplit(prevWord, this.fusedMap);
    if (autoSplit) {{
      var casedAuto = matchCase(prevWord, autoSplit) + " ";
      // When space was typed, delOffset is -1.
      // delOffset + prevWord.length + 1 = prevWord.length!
      // This deletes strictly prevWord from preInput without consuming the preceding space.
      var delLenAuto = Math.max(0, delOffset + prevWord.length + 1);
      return [{{
        sample: {{
          transform: {{ insert: casedAuto, deleteLeft: delLenAuto }},
          displayAs: casedAuto.trim(),
          p: 1.0
        }},
        p: 1.0
      }}];
    }}
    
    var cleanWords = words.map(function(w) {{
      return w.replace(/[^\\w\\u00C0-\\u024F\\u1E6C\\u1E6D]/g, "").toLowerCase();
    }}).filter(Boolean);
    
    var preds = null;
    if (cleanWords.length >= 2) {{
      var ctx2 = cleanWords[cleanWords.length - 2] + " " + cleanWords[cleanWords.length - 1];
      if (this.transitions[ctx2]) {{
        preds = this.transitions[ctx2];
      }}
    }}
    if (!preds && cleanWords.length >= 1) {{
      var ctx1 = cleanWords[cleanWords.length - 1];
      if (this.transitions[ctx1]) {{
        preds = this.transitions[ctx1];
      }}
    }}
    
    if (preds && preds.length > 0) {{
      var top3 = preds.slice(0, 3);
      return top3.map(function(item) {{
        var word = item[0];
        var prob = item[1];
        return {{
          sample: {{
            transform: {{ insert: word, deleteLeft: 0 }},
            displayAs: word,
            p: prob
          }},
          p: prob
        }};
      }});
    }}
  }}
  
  // 2. Mid-Word Typing: Check for Fused / Agglutinated words
  var lastMatch = left.match(/[a-zA-Z\\u00C0-\\u024F\\u1E6C\\u1E6D]+$/);
  var lastWord = lastMatch ? lastMatch[0] : "";
  
  if (lastWord.length >= 3) {{
    var split = getMorphologicalSplit(lastWord, this.fusedMap);
    if (split) {{
      var casedSplit = matchCase(lastWord, split);
      // delLenWord = delOffset + lastWord.length ensures preInput deletion matches
      // the exact prefix in preInput, preserving the preceding space!
      var delLenWord = Math.max(0, delOffset + lastWord.length);
      
      // EXACTLY 2 OPTIONS ONLY:
      // Option 1 (Left): Literal fused version as typed
      var slot1 = {{
        sample: {{
          transform: {{ insert: lastWord, deleteLeft: delLenWord }},
          displayAs: lastWord,
          p: 0.1
        }},
        p: 0.1
      }};
      
      // Option 2 (Right): Canonical ISH Split (Primary / Default)
      var slot2 = {{
        sample: {{
          transform: {{ insert: casedSplit, deleteLeft: delLenWord }},
          displayAs: casedSplit,
          p: 1.0
        }},
        p: 1.0
      }};
      
      return [slot1, slot2];
    }}
  }}
  
  // 3. Fallback to standard Trie Completion (top 3)
  var trieResults = this.base.predict(transform, context);
  if (trieResults && trieResults.length > 0) {{
    // Filter out English plural suggestions ending in 's'
    trieResults = trieResults.filter(function(res) {{
      var disp = (res.sample && res.sample.displayAs) || "";
      if (disp.length > 2 && disp.endsWith("s") && !lastWord.toLowerCase().endsWith("s")) {{
        return false;
      }}
      return true;
    }});
    if (trieResults.length > 3) {{
      return trieResults.slice(0, 3);
    }}
    return trieResults;
  }}
  return [];
}};

var _baseTrie = new models.TrieModel({trie_data}, {{
  wordBreaker: wordBreakers['default'],
  searchTermToKey: definitions.searchTermToKey
}});

LMLayerWorker.loadModel(new HmarHybridModel(_baseTrie, _neuralTransitions, _fusedMap));
}})();
"""
    
    print(f"Writing updated model to {ASSET_MODEL_JS}...")
    with open(ASSET_MODEL_JS, "w", encoding="utf-8") as f:
        f.write(full_js)
    print(f"Writing updated model to {SOURCE_MODEL_JS}...")
    with open(SOURCE_MODEL_JS, "w", encoding="utf-8") as f:
        f.write(full_js)
        
    print(f"✅ Successfully compiled Neural Lexical Model! Size: {len(full_js.encode('utf-8')) / (1024*1024):.2f} MB")

if __name__ == "__main__":
    main()
