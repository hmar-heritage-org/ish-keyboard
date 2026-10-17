import json

layout_file = "/home/phxlm/Work/HHF/ISH-key/engine/hmar_ish.keyman-touch-layout"

with open(layout_file, "r") as f:
    data = json.load(f)

# Define the new row 4 for default and shift layers
r4_default = [
    {
        "id": "K_NUMLOCK",
        "text": "*123*",
        "width": "140",
        "sp": "1",
        "nextlayer": "numeric"
    },
    {
        "id": "K_COMMA",
        "text": ",",
        "width": "120",
        "sk": [
            {"text": "'", "id": "K_QUOTE"},
            {"text": "\"", "id": "K_QUOTE", "layer": "shift"},
            {"text": ";", "id": "K_COLON"}
        ]
    },
    {
        "id": "K_SPACE",
        "text": "",
        "width": "480",
        "sp": "0"
    },
    {
        "id": "K_SLASH",
        "layer": "shift",
        "text": "?",
        "width": "120",
        "sk": [
            {"text": "!", "id": "K_1", "layer": "shift"},
            {"text": "/", "id": "K_SLASH"},
            {"text": "-", "id": "K_HYPHEN"}
        ]
    },
    {
        "id": "K_ENTER",
        "text": "*Enter*",
        "width": "140",
        "sp": "1"
    }
]

# Refined Period key on Row 3
refined_period = {
    "id": "K_PERIOD",
    "text": ".",
    "sk": [
        {"text": "!", "id": "K_1", "layer": "shift"},
        {"text": ":", "id": "K_COLON", "layer": "shift"},
        {"text": ";", "id": "K_COLON"},
        {"text": "'", "id": "K_QUOTE"},
        {"text": "\"", "id": "K_QUOTE", "layer": "shift"},
        {"text": "-", "id": "K_HYPHEN"}
    ]
}

# Define overhauled numeric layer
numeric_layer = {
    "id": "numeric",
    "row": [
        {
            "id": 1,
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
            "id": 2,
            "key": [
                {"id": "K_2", "layer": "shift", "text": "@"},
                {"id": "K_3", "layer": "shift", "text": "#"},
                {"id": "U_20B9", "text": "₹", "sk": [
                    {"id": "K_4", "layer": "shift", "text": "$"},
                    {"id": "U_20AC", "text": "€"},
                    {"id": "U_00A3", "text": "£"}
                ]},
                {"id": "K_5", "layer": "shift", "text": "%"},
                {"id": "K_7", "layer": "shift", "text": "&"},
                {"id": "K_HYPHEN", "text": "-"},
                {"id": "K_EQUAL", "layer": "shift", "text": "+"},
                {"id": "K_9", "layer": "shift", "text": "("},
                {"id": "K_0", "layer": "shift", "text": ")"},
                {"id": "K_SLASH", "text": "/"}
            ]
        },
        {
            "id": 3,
            "key": [
                {"id": "K_8", "layer": "shift", "text": "*"},
                {"id": "K_QUOTE", "layer": "shift", "text": "\""},
                {"id": "K_QUOTE", "text": "'"},
                {"id": "K_COLON", "layer": "shift", "text": ":"},
                {"id": "K_COLON", "text": ";"},
                {"id": "K_1", "layer": "shift", "text": "!"},
                {"id": "K_SLASH", "layer": "shift", "text": "?"},
                {"id": "K_HYPHEN", "layer": "shift", "text": "_"},
                {"id": "K_EQUAL", "text": "="},
                {"id": "K_BKSP", "text": "*BkSp*", "width": "100", "sp": "1"}
            ]
        },
        {
            "id": 4,
            "key": [
                {"id": "K_LOWER", "text": "*abc*", "width": "140", "sp": "1", "nextlayer": "default"},
                {"id": "K_COMMA", "text": ",", "width": "120"},
                {"id": "K_SPACE", "text": "", "width": "480", "sp": "0"},
                {"id": "K_PERIOD", "text": ".", "width": "120"},
                {"id": "K_ENTER", "text": "*Enter*", "width": "140", "sp": "1"}
            ]
        }
    ]
}

# Apply to both phone and tablet layouts
for target in ["phone", "tablet"]:
    if target in data:
        # 1. Update default layer
        data[target]["layer"][0]["row"][3]["key"] = r4_default
        # Update period on row 3 (key index 8, right before BkSp)
        data[target]["layer"][0]["row"][2]["key"][8] = refined_period

        # 2. Update shift layer
        data[target]["layer"][1]["row"][3]["key"] = r4_default
        data[target]["layer"][1]["row"][2]["key"][8] = refined_period

        # 3. Replace numeric layer
        data[target]["layer"][2] = numeric_layer

with open(layout_file, "w") as f:
    json.dump(data, f, indent=2)

print("Updated hmar_ish.keyman-touch-layout successfully!")
