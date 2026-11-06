import json
import re

def build_layout():
    # 1. Row 4 for default and shift layers (symmetrical: 140 + 100 + 520 + 100 + 140 = 1000)
    r4_default_shift = [
        {
            "id": "K_NUMLOCK",
            "text": "*123*",
            "width": "140",
            "sp": "1",
            "nextlayer": "numeric"
        },
        {
            "id": "K_TONAL",
            "text": "^",
            "width": "100",
            "sp": "1"
        },
        {
            "id": "K_SPACE",
            "text": "",
            "width": "520",
            "sp": "0"
        },
        {
            "id": "K_COMMA",
            "text": ",",
            "width": "100",
            "sk": [
                {"text": "'", "id": "K_QUOTE"},
                {"text": "\"", "id": "K_QUOTE", "layer": "shift"},
                {"text": ";", "id": "K_COLON"},
                {"text": ":", "id": "K_COLON", "layer": "shift"},
                {"text": "_", "id": "K_HYPHEN", "layer": "shift"}
            ]
        },
        {
            "id": "K_ENTER",
            "text": "*Enter*",
            "width": "140",
            "sp": "1"
        }
    ]

    r3_period = {
        "id": "K_PERIOD",
        "text": ".",
        "sk": [
            {"text": "!", "id": "K_1", "layer": "shift"},
            {"text": "?", "id": "K_SLASH", "layer": "shift"},
            {"text": ",", "id": "K_COMMA"},
            {"text": ":", "id": "K_COLON", "layer": "shift"},
            {"text": ";", "id": "K_COLON"},
            {"text": "-", "id": "K_HYPHEN"},
            {"text": "…", "id": "U_2026"}
        ]
    }

    numeric_layer = {
        "id": "numeric",
        "row": [
            {
                "id": "1",
                "key": [
                    {"id": "K_1", "text": "1"},
                    {"id": "K_2", "text": "2"},
                    {"id": "K_3", "text": "3"},
                    {"id": "K_4", "text": "4"},
                    {"id": "K_5", "text": "5"},
                    {"id": "K_6", "text": "6"},
                    {"id": "K_7", "text": "7"},
                    {"id": "K_8", "text": "8"},
                    {"id": "K_9", "text": "9"},
                    {"id": "K_0", "text": "0"}
                ]
            },
            {
                "id": "2",
                "key": [
                    {"id": "K_2", "layer": "shift", "text": "@"},
                    {"id": "K_3", "layer": "shift", "text": "#"},
                    {"id": "U_20B9", "text": "₹", "sk": [
                        {"id": "K_4", "layer": "shift", "text": "$"},
                        {"id": "U_20AC", "text": "€"},
                        {"id": "U_00A3", "text": "£"},
                        {"id": "U_00A5", "text": "¥"},
                        {"id": "U_00A2", "text": "¢"}
                    ]},
                    {"id": "K_5", "layer": "shift", "text": "%", "sk": [
                        {"text": "‰", "id": "U_2030"},
                        {"text": "°", "id": "U_00B0"}
                    ]},
                    {"id": "K_7", "layer": "shift", "text": "&"},
                    {"id": "K_HYPHEN", "text": "-", "sk": [
                        {"text": "_", "id": "K_HYPHEN", "layer": "shift"},
                        {"text": "—", "id": "U_2014"},
                        {"text": "–", "id": "U_2013"},
                        {"text": "~", "id": "U_007E"},
                        {"text": "•", "id": "U_2022"}
                    ]},
                    {"id": "K_EQUAL", "layer": "shift", "text": "+", "sk": [
                        {"text": "±", "id": "U_00B1"},
                        {"text": "=", "id": "K_EQUAL"},
                        {"text": "÷", "id": "U_00F7"},
                        {"text": "×", "id": "U_00D7"}
                    ]},
                    {"id": "K_9", "layer": "shift", "text": "(", "sk": [
                        {"text": "[", "id": "K_LBRKT"},
                        {"text": "{", "id": "K_LBRKT", "layer": "shift"},
                        {"text": "<", "id": "K_COMMA", "layer": "shift"}
                    ]},
                    {"id": "K_0", "layer": "shift", "text": ")", "sk": [
                        {"text": "]", "id": "K_RBRKT"},
                        {"text": "}", "id": "K_RBRKT", "layer": "shift"},
                        {"text": ">", "id": "K_PERIOD", "layer": "shift"}
                    ]},
                    {"id": "K_SLASH", "text": "/"}
                ]
            },
            {
                "id": "3",
                "key": [
                    {"id": "K_SYMBOLS", "text": "=/<", "width": "100", "sp": "1", "nextlayer": "symbols"},
                    {"id": "K_8", "layer": "shift", "text": "*"},
                    {"id": "K_QUOTE", "layer": "shift", "text": "\""},
                    {"id": "K_QUOTE", "text": "'"},
                    {"id": "K_COLON", "layer": "shift", "text": ":"},
                    {"id": "K_COLON", "text": ";"},
                    {"id": "K_1", "layer": "shift", "text": "!"},
                    {"id": "K_SLASH", "layer": "shift", "text": "?"},
                    {"id": "K_HYPHEN", "layer": "shift", "text": "_"},
                    {"id": "K_BKSP", "text": "*BkSp*", "width": "100", "sp": "1"}
                ]
            },
            {
                "id": "4",
                "key": [
                    {"id": "K_LOWER", "text": "*abc*", "width": "140", "sp": "1", "nextlayer": "default"},
                    {"id": "K_EMOJI", "text": "😊", "width": "100", "sp": "1"},
                    {"id": "K_SPACE", "text": "", "width": "420", "sp": "0"},
                    {"id": "K_COMMA", "text": ",", "width": "100"},
                    {"id": "K_PERIOD", "text": ".", "width": "100"},
                    {"id": "K_ENTER", "text": "*Enter*", "width": "140", "sp": "1"}
                ]
            }
        ]
    }

    symbols_layer = {
        "id": "symbols",
        "row": [
            {
                "id": "1",
                "key": [
                    {"id": "U_007E", "text": "~"},
                    {"id": "U_005C", "text": "\\"},
                    {"id": "U_007C", "text": "|"},
                    {"id": "U_003C", "text": "<"},
                    {"id": "U_003E", "text": ">"},
                    {"id": "U_007B", "text": "{"},
                    {"id": "U_007D", "text": "}"},
                    {"id": "U_005B", "text": "["},
                    {"id": "U_005D", "text": "]"},
                    {"id": "U_005E", "text": "^"}
                ]
            },
            {
                "id": "2",
                "key": [
                    {"id": "U_00B0", "text": "°"},
                    {"id": "U_2022", "text": "•"},
                    {"id": "U_0024", "text": "$"},
                    {"id": "U_20AC", "text": "€"},
                    {"id": "U_00A3", "text": "£"},
                    {"id": "U_00A5", "text": "¥"},
                    {"id": "U_00A2", "text": "¢"},
                    {"id": "U_00A9", "text": "©"},
                    {"id": "U_00AE", "text": "®"},
                    {"id": "U_2122", "text": "™"}
                ]
            },
            {
                "id": "3",
                "key": [
                    {"id": "K_NUMLOCK", "text": "123", "width": "100", "sp": "1", "nextlayer": "numeric"},
                    {"id": "U_00F7", "text": "÷"},
                    {"id": "U_00D7", "text": "×"},
                    {"id": "U_00B6", "text": "¶"},
                    {"id": "U_2206", "text": "∆"},
                    {"id": "U_2260", "text": "≠"},
                    {"id": "U_2248", "text": "≈"},
                    {"id": "U_00B1", "text": "±"},
                    {"id": "K_EQUAL", "text": "="},
                    {"id": "K_BKSP", "text": "*BkSp*", "width": "100", "sp": "1"}
                ]
            },
            {
                "id": "4",
                "key": [
                    {"id": "K_LOWER", "text": "*abc*", "width": "140", "sp": "1", "nextlayer": "default"},
                    {"id": "K_EMOJI", "text": "😊", "width": "100", "sp": "1"},
                    {"id": "K_SPACE", "text": "", "width": "420", "sp": "0"},
                    {"id": "K_COMMA", "text": ",", "width": "100"},
                    {"id": "K_PERIOD", "text": ".", "width": "100"},
                    {"id": "K_ENTER", "text": "*Enter*", "width": "140", "sp": "1"}
                ]
            }
        ]
    }

    return r4_default_shift, r3_period, numeric_layer, symbols_layer

def update_files():
    layout_paths = [
        "engine/hmar_ish.keyman-touch-layout",
        "android/app/src/main/assets/packages/hmar_ish/hmar_ish.keyman-touch-layout"
    ]
    js_paths = [
        "engine/hmar_ish.js",
        "android/app/src/main/assets/packages/hmar_ish/hmar_ish.js",
        "web/engine/hmar_ish.js"
    ]

    r4_default_shift, r3_period, numeric_layer, symbols_layer = build_layout()

    # Read base layout from engine
    with open("engine/hmar_ish.keyman-touch-layout", "r") as f:
        data = json.load(f)

    for target in ["phone", "tablet"]:
        if target in data:
            # 1. Default Layer
            default_layer = data[target]["layer"][0]
            default_layer["row"][3]["key"] = r4_default_shift
            default_layer["row"][2]["key"][8] = r3_period

            # 2. Shift Layer
            shift_layer = data[target]["layer"][1]
            shift_layer["row"][3]["key"] = r4_default_shift
            shift_layer["row"][2]["key"][8] = r3_period

            # Standard 4 layers: default, shift, numeric, symbols
            data[target]["layer"] = [
                default_layer,
                shift_layer,
                numeric_layer,
                symbols_layer
            ]

    # Save touch-layout files
    for lp in layout_paths:
        with open(lp, "w") as f:
            json.dump(data, f, indent=2)
        print("Updated", lp)

    # Convert layout data to compact JSON for JS KVKL
    kvkl_compact = json.dumps(data, separators=(',', ':'))

    for jp in js_paths:
        with open(jp, "r") as f:
            content = f.read()
        # Replace this.KVKL={...};
        new_content = re.sub(r'this\.KVKL=\{.*?\};this\.KVER', lambda m: 'this.KVKL=' + kvkl_compact + ';this.KVER', content)
        with open(jp, "w") as f:
            f.write(new_content)
        print("Updated KVKL in", jp)

if __name__ == "__main__":
    update_files()
