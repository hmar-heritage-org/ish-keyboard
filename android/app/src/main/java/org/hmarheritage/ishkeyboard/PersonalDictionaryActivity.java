package org.hmarheritage.ishkeyboard;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PersonalDictionaryActivity extends AppCompatActivity {

    private UserCorpusDatabase corpusDb;
    private RecyclerView rvWords;
    private WordAdapter adapter;
    private View layoutEmpty;
    private TextView tvEmptyDesc;
    private EditText etSearch;
    private ImageView btnClearSearch;
    private TextView tvHeaderCount;

    // Filter Tabs
    private TextView tabAll;
    private TextView tabMyWords;
    private TextView tabIsh;
    private TextView tabCustom;
    private TextView btnSort;

    private String currentSearch = "";
    private String currentFilter = "all"; // "all", "my_words", "ish_standard", "custom"
    private String currentSort = "frequency"; // "frequency", "alphabetical", "recency"

    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable searchRunnable;
    private int searchSequence = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_dictionary);

        corpusDb = UserCorpusDatabase.getInstance(this);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        tvHeaderCount = findViewById(R.id.tv_header_count);
        rvWords = findViewById(R.id.rv_personal_words);
        layoutEmpty = findViewById(R.id.layout_empty_state);
        tvEmptyDesc = findViewById(R.id.tv_empty_desc);
        etSearch = findViewById(R.id.et_search_words);
        btnClearSearch = findViewById(R.id.btn_clear_search);

        tabAll = findViewById(R.id.tab_filter_all);
        tabMyWords = findViewById(R.id.tab_filter_my_words);
        tabIsh = findViewById(R.id.tab_filter_ish);
        tabCustom = findViewById(R.id.tab_filter_custom);
        btnSort = findViewById(R.id.btn_sort);

        View fabAdd = findViewById(R.id.fab_add_word);
        fabAdd.setOnClickListener(v -> showAddWordDialog(null, 5));
        findViewById(R.id.btn_empty_add).setOnClickListener(v -> showAddWordDialog(null, 5));

        findViewById(R.id.btn_clean_typos).setOnClickListener(v -> showCleanTyposDialog());
        findViewById(R.id.btn_export_dictionary).setOnClickListener(v -> showExportDialog());

        rvWords.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WordAdapter();
        rvWords.setAdapter(adapter);

        setupSearchAndFilters();

        new Thread(() -> {
            corpusDb.seedIshDictionaryIfNeeded();
            runOnUiThread(this::loadWords);
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadWords();
    }

    private void setupSearchAndFilters() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearch = s != null ? s.toString().trim() : "";
                btnClearSearch.setVisibility(currentSearch.isEmpty() ? View.GONE : View.VISIBLE);
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                searchRunnable = PersonalDictionaryActivity.this::loadWords;
                searchHandler.postDelayed(searchRunnable, 150);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> etSearch.setText(""));

        tabAll.setOnClickListener(v -> setFilter("all"));
        tabMyWords.setOnClickListener(v -> setFilter("my_words"));
        tabIsh.setOnClickListener(v -> setFilter("ish_standard"));
        tabCustom.setOnClickListener(v -> setFilter("custom"));

        btnSort.setOnClickListener(v -> {
            if ("frequency".equals(currentSort)) {
                currentSort = "alphabetical";
                btnSort.setText("A–Z ▲");
            } else if ("alphabetical".equals(currentSort)) {
                currentSort = "recency";
                btnSort.setText("Recent ⏱");
            } else {
                currentSort = "frequency";
                btnSort.setText("Freq ▼");
            }
            loadWords();
        });
    }

    private void setFilter(String filter) {
        currentFilter = filter;
        updateTabStyles();
        loadWords();
    }

    private void updateTabStyles() {
        int activeBg = R.drawable.badge_active;
        int activeColor = getResources().getColor(R.color.brand_emerald);
        int inactiveBg = R.drawable.bg_badge_freq;
        int inactiveColor = getResources().getColor(R.color.text_sub);

        tabAll.setBackgroundResource("all".equals(currentFilter) ? activeBg : inactiveBg);
        tabAll.setTextColor("all".equals(currentFilter) ? activeColor : inactiveColor);

        tabMyWords.setBackgroundResource("my_words".equals(currentFilter) ? activeBg : inactiveBg);
        tabMyWords.setTextColor("my_words".equals(currentFilter) ? activeColor : inactiveColor);

        tabIsh.setBackgroundResource("ish_standard".equals(currentFilter) ? activeBg : inactiveBg);
        tabIsh.setTextColor("ish_standard".equals(currentFilter) ? activeColor : inactiveColor);

        tabCustom.setBackgroundResource("custom".equals(currentFilter) ? activeBg : inactiveBg);
        tabCustom.setTextColor("custom".equals(currentFilter) ? activeColor : inactiveColor);
    }

    private void loadWords() {
        final int seq = ++searchSequence;
        final String search = currentSearch;
        final String filter = currentFilter;
        final String sort = currentSort;
        new Thread(() -> {
            List<UserCorpusDatabase.PersonalWord> words = corpusDb.getLayeredWords(search, filter, sort, 300, 0);
            int myWordsCount = corpusDb.getMyWordsCount();
            int ishCount = corpusDb.getIshStandardCount();
            int customCount = corpusDb.getCustomWordsCount();
            int allCount = corpusDb.getAllWordsCount();

            runOnUiThread(() -> {
                if (seq != searchSequence) return;
                adapter.setWords(words);
                tvHeaderCount.setText(myWordsCount + " typed by you • " + ishCount + " standard • " + customCount + " custom");
                tabAll.setText("All (" + allCount + ")");
                tabMyWords.setText("My Words (" + myWordsCount + ")");
                tabIsh.setText("ISH Standard (" + ishCount + ")");
                tabCustom.setText("Custom (" + customCount + ")");

                if (words.isEmpty()) {
                    layoutEmpty.setVisibility(View.VISIBLE);
                    rvWords.setVisibility(View.GONE);
                    if (!currentSearch.isEmpty()) {
                        tvEmptyDesc.setText("No vocabulary matching \"" + currentSearch + "\".");
                    } else if ("my_words".equals(currentFilter)) {
                        tvEmptyDesc.setText("You haven't typed any words yet.\nStart typing in any app and your words will appear here.");
                    } else if ("custom".equals(currentFilter)) {
                        tvEmptyDesc.setText("You haven't manually added any custom words yet.");
                    } else {
                        tvEmptyDesc.setText("No dictionary words available.");
                    }
                } else {
                    layoutEmpty.setVisibility(View.GONE);
                    rvWords.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    private void showExportDialog() {
        String[] options = {
                "Export TSV (wordlist.tsv for Keyman / static builds)",
                "Export Full Lexicon (JSON with examples & suggestions)"
        };

        new AlertDialog.Builder(this)
                .setTitle("Export Dictionary Baseline")
                .setItems(options, (dialog, which) -> {
                    Toast.makeText(PersonalDictionaryActivity.this, "Preparing export...", Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        File file = (which == 0) ? corpusDb.exportBaselineTsv() : corpusDb.exportFullArchiveJson();
                        if (file != null && file.exists()) {
                            runOnUiThread(() -> shareExportedFile(file, which == 0 ? "text/tab-separated-values" : "application/json"));
                        } else {
                            runOnUiThread(() -> Toast.makeText(PersonalDictionaryActivity.this, "Export failed", Toast.LENGTH_SHORT).show());
                        }
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void shareExportedFile(File file, String mimeType) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Save or Share: " + file.getName()));
        } catch (Exception e) {
            Toast.makeText(this, "Export sharing error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showAddWordDialog(String prefillWord, int prefillFreq) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_word, null);
        TextView tvTitle = dialogView.findViewById(R.id.dialog_title);
        EditText etWord = dialogView.findViewById(R.id.et_dialog_word);
        EditText etFreq = dialogView.findViewById(R.id.et_dialog_freq);
        TextView tvError = dialogView.findViewById(R.id.tv_dialog_error);

        boolean isEdit = prefillWord != null && !prefillWord.isEmpty();
        if (isEdit) {
            tvTitle.setText("Edit Personal Word");
            etWord.setText(prefillWord);
            etFreq.setText(String.valueOf(prefillFreq));
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                String input = etWord.getText() != null ? etWord.getText().toString().trim() : "";
                String freqStr = etFreq.getText() != null ? etFreq.getText().toString().trim() : "5";

                int freq = 5;
                try {
                    freq = Integer.parseInt(freqStr);
                } catch (Exception ignored) {}

                String cleaned = input.replaceAll("^[^a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+|[^a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+$", "");
                if (cleaned.isEmpty() || !cleaned.matches("^[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+(-[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+)*$")) {
                    tvError.setVisibility(View.VISIBLE);
                    return;
                }

                tvError.setVisibility(View.GONE);
                final int finalFreq = Math.max(1, freq);
                new Thread(() -> {
                    corpusDb.addOrUpdateUserWord(cleaned, finalFreq, true);
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        Toast.makeText(PersonalDictionaryActivity.this, "Saved \"" + cleaned + "\"", Toast.LENGTH_SHORT).show();
                        loadWords();
                    });
                }).start();
            });
        });

        dialog.show();
    }

    private void showDeleteConfirmationDialog(UserCorpusDatabase.PersonalWord pw) {
        String msg = pw.isIshStandard
                ? "Delete \"" + pw.displayWord + "\" from the dictionary?\n\nIt will no longer appear in predictions or suggestions."
                : "Remove \"" + pw.displayWord + "\" from your personal dictionary?\n\nIt will no longer appear in keyboard suggestions.";

        new AlertDialog.Builder(this)
                .setTitle("Delete Word")
                .setMessage(msg)
                .setPositiveButton("Delete", (dialog, which) -> {
                    new Thread(() -> {
                        corpusDb.markWordDeleted(pw.word);
                        runOnUiThread(() -> {
                            Toast.makeText(PersonalDictionaryActivity.this, "Deleted \"" + pw.displayWord + "\"", Toast.LENGTH_SHORT).show();
                            loadWords();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCleanTyposDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clean Up Typos")
                .setMessage("This will remove all auto-learned words that were typed only once (1×) and never reused.\n\nYour manually added custom words and ISH Standard words will be completely preserved.")
                .setPositiveButton("Clean Up", (dialog, which) -> {
                    new Thread(() -> {
                        int pruned = corpusDb.pruneSingleUseTypos();
                        runOnUiThread(() -> {
                            Toast.makeText(PersonalDictionaryActivity.this, "Cleaned up " + pruned + " typos!", Toast.LENGTH_LONG).show();
                            loadWords();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private class WordAdapter extends RecyclerView.Adapter<WordAdapter.WordViewHolder> {
        private final List<UserCorpusDatabase.PersonalWord> list = new ArrayList<>();

        public void setWords(List<UserCorpusDatabase.PersonalWord> words) {
            list.clear();
            if (words != null) list.addAll(words);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_personal_word, parent, false);
            return new WordViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
            UserCorpusDatabase.PersonalWord pw = list.get(position);
            holder.tvDisplay.setText(pw.displayWord);

            holder.tvBadgeIsh.setVisibility(pw.isIshStandard ? View.VISIBLE : View.GONE);
            holder.tvBadgeCustom.setVisibility((!pw.isIshStandard || pw.isUserAdded) ? View.VISIBLE : View.GONE);

            if (pw.frequency > 0) {
                holder.tvFreqBadge.setText(pw.frequency + "×");
                holder.tvFreqBadge.setVisibility(View.VISIBLE);
                holder.tvMeta.setText("Typed " + pw.frequency + (pw.frequency == 1 ? " time" : " times") + " by you" + (pw.suggestReplacement != null ? " ➔ suggests \"" + pw.suggestReplacement + "\"" : ""));
            } else {
                holder.tvFreqBadge.setVisibility(View.GONE);
                holder.tvMeta.setText("Official dictionary word" + (pw.suggestReplacement != null ? " ➔ suggests \"" + pw.suggestReplacement + "\"" : " • Not typed yet"));
            }

            // Click opens the Full-Screen Word Detail & Editor Activity!
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(PersonalDictionaryActivity.this, WordDetailActivity.class);
                intent.putExtra("word", pw.word);
                startActivity(intent);
            });

            if (holder.btnDelete != null) {
                holder.btnDelete.setOnClickListener(v -> showDeleteConfirmationDialog(pw));
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class WordViewHolder extends RecyclerView.ViewHolder {
            final TextView tvDisplay;
            final TextView tvBadgeIsh;
            final TextView tvBadgeCustom;
            final TextView tvMeta;
            final TextView tvFreqBadge;
            final ImageView btnDelete;

            public WordViewHolder(@NonNull View itemView) {
                super(itemView);
                tvDisplay = itemView.findViewById(R.id.tv_word_display);
                tvBadgeIsh = itemView.findViewById(R.id.tv_badge_ish);
                tvBadgeCustom = itemView.findViewById(R.id.tv_badge_custom);
                tvMeta = itemView.findViewById(R.id.tv_word_meta);
                tvFreqBadge = itemView.findViewById(R.id.tv_freq_badge);
                btnDelete = null;
            }
        }
    }
}
