package org.hmarheritage.ishkeyboard;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class PredictionsActivity extends AppCompatActivity {

    private TextView tvSentenceCount;
    private TextView tvUnigramCount;
    private TextView tvBigramCount;
    private Button btnExportDataset;
    private Button btnClearData;
    private UserCorpusDatabase corpusDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_predictions);

        corpusDb = UserCorpusDatabase.getInstance(this);

        tvSentenceCount = findViewById(R.id.tv_sentence_count);
        tvUnigramCount = findViewById(R.id.tv_unigram_count);
        tvBigramCount = findViewById(R.id.tv_bigram_count);
        btnExportDataset = findViewById(R.id.btn_export_dataset);
        btnClearData = findViewById(R.id.btn_clear_data);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        btnExportDataset.setOnClickListener(v -> exportDataset());

        btnClearData.setOnClickListener(v -> showClearConfirmationDialog());

        findViewById(R.id.btn_manage_dictionary).setOnClickListener(v -> {
            Intent intent = new Intent(PredictionsActivity.this, PersonalDictionaryActivity.class);
            startActivity(intent);
        });

        refreshStats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStats();
    }

    private void refreshStats() {
        new Thread(() -> {
            int sentences = corpusDb.getSentenceCount();
            int unigrams = corpusDb.getUnigramCount();
            int bigrams = corpusDb.getBigramCount();

            runOnUiThread(() -> {
                tvSentenceCount.setText(sentences + " Sentences Buffered");
                tvUnigramCount.setText(String.valueOf(unigrams));
                tvBigramCount.setText(String.valueOf(bigrams));
            });
        }).start();
    }

    private void exportDataset() {
        new Thread(() -> {
            List<String> lines = corpusDb.getAllSentences();
            if (lines.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(PredictionsActivity.this, "No sentences collected yet. Type with the keyboard to buffer text!", Toast.LENGTH_LONG).show());
                return;
            }

            try {
                File cacheDir = new File(getCacheDir(), "exports");
                if (!cacheDir.exists()) cacheDir.mkdirs();

                String dateStr = new java.text.SimpleDateFormat("dd-MM-yy", java.util.Locale.US).format(new java.util.Date());
                int randomCode = 1000 + new java.util.Random().nextInt(9000); // 4-digit random code
                String fileName = dateStr + "-" + randomCode + ".jsonl";

                File exportFile = new File(cacheDir, fileName);
                try (FileOutputStream fos = new FileOutputStream(exportFile)) {
                    for (String line : lines) {
                        fos.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                    fos.flush();
                }

                Uri uri = FileProvider.getUriForFile(
                        PredictionsActivity.this,
                        getPackageName() + ".fileprovider",
                        exportFile
                );

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/jsonl");
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Hmar AI Training Corpus (" + fileName + ")");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Exported " + lines.size() + " Hmar conversational sentences (" + fileName + ") from ISH Keyboard.");
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                runOnUiThread(() -> startActivity(Intent.createChooser(shareIntent, "Export Hmar AI Corpus")));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(PredictionsActivity.this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showClearConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Reset Data?")
                .setMessage("Are you sure you want to clear your buffered sentences and learned dictionary? This cannot be undone.")
                .setPositiveButton("Clear Everything", (dialog, which) -> {
                    new Thread(() -> {
                        corpusDb.clearAllData();
                        runOnUiThread(() -> {
                            refreshStats();
                            Toast.makeText(PredictionsActivity.this, "Local history & dictionary reset.", Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
