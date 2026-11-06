package org.hmarheritage.ishkeyboard;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class WordDetailActivity extends AppCompatActivity {

    private UserCorpusDatabase corpusDb;
    private String targetWord = "";
    private UserCorpusDatabase.PersonalWord currentWordData;

    private TextView tvHeroWord;
    private TextView badgeIsh;
    private TextView badgeCustom;
    private TextView badgeUsage;
    private TextView tvUsagePreview;
    private EditText etWordText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_word_detail);

        corpusDb = UserCorpusDatabase.getInstance(this);

        targetWord = getIntent().getStringExtra("word");
        if (targetWord == null || targetWord.trim().isEmpty()) {
            Toast.makeText(this, "No word specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        targetWord = targetWord.trim().toLowerCase();

        initViews();
        loadWordData();
    }

    private void initViews() {
        findViewById(R.id.btn_detail_back).setOnClickListener(v -> finish());

        tvHeroWord = findViewById(R.id.tv_hero_word);
        badgeIsh = findViewById(R.id.badge_ish_standard);
        badgeCustom = findViewById(R.id.badge_custom);
        badgeUsage = findViewById(R.id.badge_usage_freq);
        tvUsagePreview = findViewById(R.id.tv_usage_preview);
        etWordText = findViewById(R.id.et_word_text);

        findViewById(R.id.btn_save_all).setOnClickListener(v -> saveWordDetails());
        findViewById(R.id.btn_delete_word_full).setOnClickListener(v -> showDeleteWordDialog());
    }

    private void loadWordData() {
        new Thread(() -> {
            currentWordData = corpusDb.getWordDetails(targetWord);
            if (currentWordData == null) {
                // If not in database yet, create an in-memory draft
                currentWordData = new UserCorpusDatabase.PersonalWord(targetWord, targetWord, 1, 0, false, true, System.currentTimeMillis(), null, null, false);
            }

            runOnUiThread(() -> {
                tvHeroWord.setText(currentWordData.displayWord);
                etWordText.setText(currentWordData.displayWord);

                badgeIsh.setVisibility(currentWordData.isIshStandard ? View.VISIBLE : View.GONE);
                badgeCustom.setVisibility((!currentWordData.isIshStandard || currentWordData.isUserAdded) ? View.VISIBLE : View.GONE);

                if (currentWordData.frequency > 0) {
                    badgeUsage.setText("Typed " + currentWordData.frequency + "×");
                    tvUsagePreview.setText("Usage: Typed " + currentWordData.frequency + " times on this device");
                } else {
                    badgeUsage.setText("Standard Lexicon");
                    tvUsagePreview.setText("Usage: Core ISH baseline vocabulary (Priority: " + Math.max(1, currentWordData.baseFrequency) + ")");
                }
            });
        }).start();
    }

    private void saveWordDetails() {
        String newSpelling = etWordText.getText() != null ? etWordText.getText().toString().trim() : "";

        String cleanedNew = newSpelling.replaceAll("^[^a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+|[^a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+$", "");
        if (cleanedNew.isEmpty() || !cleanedNew.matches("^[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+(-[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+)*$")) {
            Toast.makeText(this, "Please enter a valid word", Toast.LENGTH_SHORT).show();
            return;
        }

        int freq = currentWordData != null ? Math.max(1, currentWordData.frequency) : 1;
        String replacement = currentWordData != null ? currentWordData.suggestReplacement : null;

        new Thread(() -> {
            corpusDb.updateWordDetails(targetWord, cleanedNew, freq, replacement);
            targetWord = cleanedNew.toLowerCase();
            runOnUiThread(() -> {
                Toast.makeText(WordDetailActivity.this, "Saved \"" + cleanedNew + "\"", Toast.LENGTH_SHORT).show();
                loadWordData();
            });
        }).start();
    }

    private void showDeleteWordDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Word")
                .setMessage("Delete \"" + currentWordData.displayWord + "\" from your dictionary?\n\nIt will no longer appear in keyboard suggestions.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    new Thread(() -> {
                        corpusDb.markWordDeleted(targetWord);
                        runOnUiThread(() -> {
                            Toast.makeText(WordDetailActivity.this, "Deleted \"" + currentWordData.displayWord + "\"", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
