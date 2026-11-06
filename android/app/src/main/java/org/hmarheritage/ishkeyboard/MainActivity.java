package org.hmarheritage.ishkeyboard;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.keyman.engine.KMManager;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int MIN_PORTRAIT_HEIGHT_DP = 220;
    private static final int DEFAULT_PORTRAIT_HEIGHT_DP = 275;
    private static final int MAX_PORTRAIT_HEIGHT_DP = 360;

    private static final int MIN_LANDSCAPE_HEIGHT_DP = 150;
    private static final int DEFAULT_LANDSCAPE_HEIGHT_DP = 192;
    private static final int MAX_LANDSCAPE_HEIGHT_DP = 250;

    private FrameLayout contentFrame;
    private BottomNavigationView bottomNav;

    // Cached views for instantaneous tab switching
    private View viewHome;
    private View viewDictionary;
    private View viewGuide;
    private View viewSettings;

    // Database & Shared Preferences
    private UserCorpusDatabase corpusDb;
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener;

    // Synchronized settings switches across Home and Settings tabs
    private SwitchCompat swHomeVibrate;
    private SwitchCompat swHomeSound;
    private SwitchCompat swHomeTrackpad;
    private SwitchCompat swSettingsPeriod;
    private SwitchCompat swSettingsTrackpad;
    private SwitchCompat swSettingsVibrate;
    private SwitchCompat swSettingsSound;
    private SwitchCompat swSettingsContribute;
    private boolean isUpdatingSwitches = false;

    // Dictionary tab state
    private WordAdapter dictAdapter;
    private String dictSearch = "";
    private String dictFilter = "all";
    private String dictSort = "frequency";
    private int dictSearchSeq = 0;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    // Delete Mode state
    private boolean isDeleteMode = false;
    private final Set<String> selectedWords = new HashSet<>();
    private View layoutDeleteModeBar;
    private TextView tvDeleteModeCount;
    private TextView btnDeleteModeSelectAll;
    private View btnDeleteModeConfirm;
    private TextView tvDeleteButtonLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        corpusDb = UserCorpusDatabase.getInstance(this);
        prefs = getSharedPreferences("KMAPreferences", Context.MODE_PRIVATE);

        contentFrame = findViewById(R.id.main_content_frame);
        bottomNav = findViewById(R.id.bottom_navigation);

        layoutDeleteModeBar = findViewById(R.id.layout_delete_mode_bar);
        tvDeleteModeCount = findViewById(R.id.tv_delete_mode_count);
        btnDeleteModeSelectAll = findViewById(R.id.btn_delete_mode_select_all);
        btnDeleteModeConfirm = findViewById(R.id.btn_delete_mode_confirm);
        tvDeleteButtonLabel = findViewById(R.id.tv_delete_button_label);

        if (findViewById(R.id.btn_delete_mode_close) != null) {
            findViewById(R.id.btn_delete_mode_close).setOnClickListener(v -> exitDeleteMode());
        }
        if (btnDeleteModeSelectAll != null) {
            btnDeleteModeSelectAll.setOnClickListener(v -> toggleSelectAll());
        }
        if (btnDeleteModeConfirm != null) {
            btnDeleteModeConfirm.setOnClickListener(v -> confirmBulkDelete());
        }

        initTabViews();

        prefChangeListener = (sp, key) -> syncAllSwitches();
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener);
        syncAllSwitches();

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                switchTab(viewHome);
                syncAllSwitches();
                updateHomeStatus();
                return true;
            } else if (itemId == R.id.nav_dictionary) {
                switchTab(viewDictionary);
                loadDictionaryWords();
                return true;
            } else if (itemId == R.id.nav_guide) {
                switchTab(viewGuide);
                ScrollView sv = viewGuide.findViewById(R.id.scroll_guide);
                if (sv != null) sv.scrollTo(0, 0);
                return true;
            } else if (itemId == R.id.nav_settings) {
                switchTab(viewSettings);
                syncAllSwitches();
                updateSettingsStats();
                return true;
            }
            return false;
        });

        // Default tab is Home
        switchTab(viewHome);

        // Seed ISH dictionary in background if needed
        new Thread(() -> corpusDb.seedIshDictionaryIfNeeded()).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncAllSwitches();
        updateHomeStatus();
        if (contentFrame.getChildAt(0) == viewDictionary) {
            loadDictionaryWords();
        } else if (contentFrame.getChildAt(0) == viewSettings) {
            updateSettingsStats();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (prefs != null && prefChangeListener != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener);
        }
    }

    private void initTabViews() {
        LayoutInflater inflater = LayoutInflater.from(this);
        viewHome = inflater.inflate(R.layout.fragment_home, contentFrame, false);
        viewDictionary = inflater.inflate(R.layout.fragment_dictionary, contentFrame, false);
        viewGuide = inflater.inflate(R.layout.fragment_guide, contentFrame, false);
        viewSettings = inflater.inflate(R.layout.fragment_settings, contentFrame, false);

        setupHomeTab();
        setupDictionaryTab();
        setupSettingsTab();
    }

    private void switchTab(View targetView) {
        if (isDeleteMode) {
            exitDeleteMode();
        }
        contentFrame.removeAllViews();
        contentFrame.addView(targetView);
    }

    // =========================================================================
    // TAB 1: HOME & PLAYGROUND
    // =========================================================================
    private void setupHomeTab() {
        TextView statusChip = viewHome.findViewById(R.id.home_status_chip);
        Button btnEnable = viewHome.findViewById(R.id.btn_setup_enable);
        Button btnSelect = viewHome.findViewById(R.id.btn_setup_select);
        EditText editPlayground = viewHome.findViewById(R.id.edit_playground);
        TextView btnClear = viewHome.findViewById(R.id.btn_clear_playground);

        statusChip.setOnClickListener(v -> showImePicker());
        btnEnable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        btnSelect.setOnClickListener(v -> showImePicker());

        btnClear.setOnClickListener(v -> editPlayground.setText(""));

        // Keyboard height tuner
        setupHeightTuner();

        // Quick Feedback Switches
        swHomeVibrate = viewHome.findViewById(R.id.switch_home_vibrate);
        swHomeSound = viewHome.findViewById(R.id.switch_home_sound);
        swHomeTrackpad = viewHome.findViewById(R.id.switch_home_trackpad);

        swHomeVibrate.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isUpdatingSwitches) return;
            prefs.edit().putBoolean("pref_vibrate_on_keypress", isChecked).apply();
            syncAllSwitches();
        });
        swHomeSound.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isUpdatingSwitches) return;
            prefs.edit().putBoolean("pref_sound_on_keypress", isChecked).apply();
            syncAllSwitches();
        });
        swHomeTrackpad.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isUpdatingSwitches) return;
            prefs.edit().putBoolean("pref_hold_space_trackpad", isChecked).apply();
            syncAllSwitches();
        });
    }

    private void updateHomeStatus() {
        if (viewHome == null) return;
        boolean isEnabled = isKeyboardEnabled();
        boolean isSelected = isKeyboardSelected();

        View banner = viewHome.findViewById(R.id.card_setup_banner);
        TextView statusChip = viewHome.findViewById(R.id.home_status_chip);
        Button btnEnable = viewHome.findViewById(R.id.btn_setup_enable);
        Button btnSelect = viewHome.findViewById(R.id.btn_setup_select);
        TextView tvDesc = viewHome.findViewById(R.id.tv_setup_desc);

        if (isSelected) {
            banner.setVisibility(View.GONE);
            statusChip.setText("● Active");
            statusChip.setTextColor(getResources().getColor(R.color.badge_active_text));
            statusChip.setBackgroundResource(R.drawable.badge_active);
        } else {
            banner.setVisibility(View.VISIBLE);
            statusChip.setText("Setup Needed");
            statusChip.setTextColor(getResources().getColor(R.color.badge_inactive_text));
            statusChip.setBackgroundResource(R.drawable.badge_notice);

            if (isEnabled) {
                tvDesc.setText("Step 1 complete! Tap Step 2 to select ISH Keyboard.");
                btnEnable.setEnabled(false);
                btnEnable.setText("✓ 1. Enabled");
                btnSelect.setEnabled(true);
            } else {
                tvDesc.setText("Follow these two quick steps to enable ISH Keyboard in Android:");
                btnEnable.setEnabled(true);
                btnEnable.setText("1. Enable in Settings");
                btnSelect.setEnabled(false);
            }
        }
    }

    private void setupHeightTuner() {
        if (viewHome == null) return;
        SeekBar seekHeight = viewHome.findViewById(R.id.seek_home_height);
        TextView tvHeightValue = viewHome.findViewById(R.id.tv_home_height_value);
        if (seekHeight == null || tvHeightValue == null) return;

        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        String prefKey = isLandscape ? "keyboardHeightLandscape" : "keyboardHeightPortrait";
        int defaultDp = isLandscape ? DEFAULT_LANDSCAPE_HEIGHT_DP : DEFAULT_PORTRAIT_HEIGHT_DP;
        int minDp = isLandscape ? MIN_LANDSCAPE_HEIGHT_DP : MIN_PORTRAIT_HEIGHT_DP;
        int maxDp = isLandscape ? MAX_LANDSCAPE_HEIGHT_DP : MAX_PORTRAIT_HEIGHT_DP;
        int maxProgress = maxDp - minDp;

        float density = getResources().getDisplayMetrics().density;
        int currentPx = prefs.getInt(prefKey, 0);
        int currentDp = currentPx > 0 ? Math.round(currentPx / density) : defaultDp;
        int initialProgress = Math.max(0, Math.min(maxProgress, currentDp - minDp));

        seekHeight.setMax(maxProgress);
        seekHeight.setProgress(initialProgress);
        updateHeightLabel(tvHeightValue, minDp + initialProgress, isLandscape);

        seekHeight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int heightDp = minDp + progress;
                updateHeightLabel(tvHeightValue, heightDp, isLandscape);
                if (fromUser) {
                    saveKeyboardHeight(heightDp, isLandscape);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void updateHeightLabel(TextView label, int heightDp, boolean isLandscape) {
        String tag = "Normal";
        if (isLandscape) {
            if (heightDp <= 170) tag = "Compact";
            else if (heightDp <= 210) tag = "Normal";
            else tag = "Tall";
            label.setText(heightDp + " dp (" + tag + " Landscape)");
        } else {
            if (heightDp <= 245) tag = "Compact";
            else if (heightDp <= 285) tag = "Normal";
            else if (heightDp <= 325) tag = "Tall";
            else tag = "Extra Tall";
            label.setText(heightDp + " dp (" + tag + " Portrait)");
        }
    }

    private void saveKeyboardHeight(int heightDp, boolean isLandscape) {
        float density = getResources().getDisplayMetrics().density;
        int heightPx = Math.round(heightDp * density);
        String prefKey = isLandscape ? "keyboardHeightLandscape" : "keyboardHeightPortrait";
        prefs.edit().putInt(prefKey, heightPx).apply();
        KMManager.applyKeyboardHeight(getApplicationContext(), heightPx);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setupHeightTuner();
    }

    // =========================================================================
    // TAB 2: PERSONAL DICTIONARY DASHBOARD
    // =========================================================================
    private void setupDictionaryTab() {
        RecyclerView rv = viewDictionary.findViewById(R.id.rv_dict_words);
        rv.setLayoutManager(new LinearLayoutManager(this));
        dictAdapter = new WordAdapter();
        rv.setAdapter(dictAdapter);

        EditText etSearch = viewDictionary.findViewById(R.id.et_dict_search);
        ImageView btnClear = viewDictionary.findViewById(R.id.btn_dict_clear_search);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                dictSearch = s != null ? s.toString().trim() : "";
                btnClear.setVisibility(dictSearch.isEmpty() ? View.GONE : View.VISIBLE);
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                searchRunnable = MainActivity.this::loadDictionaryWords;
                searchHandler.postDelayed(searchRunnable, 150);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClear.setOnClickListener(v -> etSearch.setText(""));

        // Filter tabs
        TextView tabAll = viewDictionary.findViewById(R.id.tab_dict_all);
        TextView tabMyWords = viewDictionary.findViewById(R.id.tab_dict_my_words);
        TextView tabIsh = viewDictionary.findViewById(R.id.tab_dict_ish);
        TextView tabCustom = viewDictionary.findViewById(R.id.tab_dict_custom);
        TextView btnSort = viewDictionary.findViewById(R.id.btn_dict_sort);

        tabAll.setOnClickListener(v -> setDictFilter("all"));
        tabMyWords.setOnClickListener(v -> setDictFilter("my_words"));
        tabIsh.setOnClickListener(v -> setDictFilter("ish_standard"));
        tabCustom.setOnClickListener(v -> setDictFilter("custom"));

        btnSort.setOnClickListener(v -> {
            if ("frequency".equals(dictSort)) {
                dictSort = "alphabetical";
                btnSort.setText("A–Z ▲");
            } else if ("alphabetical".equals(dictSort)) {
                dictSort = "recency";
                btnSort.setText("Recent ⏱");
            } else {
                dictSort = "frequency";
                btnSort.setText("Freq ▼");
            }
            loadDictionaryWords();
        });

        // Top actions
        viewDictionary.findViewById(R.id.btn_dict_add).setOnClickListener(v -> showAddWordDialog());
        View btnMenu = viewDictionary.findViewById(R.id.btn_dict_menu);
        if (btnMenu != null) {
            btnMenu.setOnClickListener(this::showDictOverflowMenu);
        }
    }

    private void showDictOverflowMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Clean 1-time typos");
        popup.getMenu().add(0, 2, 1, "Select & Delete");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                showCleanTyposDialog();
                return true;
            } else if (item.getItemId() == 2) {
                enterDeleteMode();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void setDictFilter(String filter) {
        dictFilter = filter;
        updateDictTabStyles();
        loadDictionaryWords();
    }

    private void updateDictTabStyles() {
        int activeBg = R.drawable.badge_active;
        int activeColor = getResources().getColor(R.color.brand_emerald);
        int inactiveBg = R.drawable.bg_badge_freq;
        int inactiveColor = getResources().getColor(R.color.text_sub);

        TextView tabAll = viewDictionary.findViewById(R.id.tab_dict_all);
        TextView tabMyWords = viewDictionary.findViewById(R.id.tab_dict_my_words);
        TextView tabIsh = viewDictionary.findViewById(R.id.tab_dict_ish);
        TextView tabCustom = viewDictionary.findViewById(R.id.tab_dict_custom);

        tabAll.setBackgroundResource("all".equals(dictFilter) ? activeBg : inactiveBg);
        tabAll.setTextColor("all".equals(dictFilter) ? activeColor : inactiveColor);

        tabMyWords.setBackgroundResource("my_words".equals(dictFilter) ? activeBg : inactiveBg);
        tabMyWords.setTextColor("my_words".equals(dictFilter) ? activeColor : inactiveColor);

        tabIsh.setBackgroundResource("ish_standard".equals(dictFilter) ? activeBg : inactiveBg);
        tabIsh.setTextColor("ish_standard".equals(dictFilter) ? activeColor : inactiveColor);

        tabCustom.setBackgroundResource("custom".equals(dictFilter) ? activeBg : inactiveBg);
        tabCustom.setTextColor("custom".equals(dictFilter) ? activeColor : inactiveColor);
    }

    private void loadDictionaryWords() {
        final int seq = ++dictSearchSeq;
        final String search = dictSearch;
        final String filter = dictFilter;
        final String sort = dictSort;

        new Thread(() -> {
            List<UserCorpusDatabase.PersonalWord> words = corpusDb.getLayeredWords(search, filter, sort, 300, 0);
            int totalCount = corpusDb.getAllWordsCount();
            int myCount = corpusDb.getMyWordsCount();
            int customCount = corpusDb.getCustomWordsCount();

            runOnUiThread(() -> {
                if (seq != dictSearchSeq || viewDictionary == null) return;
                dictAdapter.setWords(words);

                TextView tvTotal = viewDictionary.findViewById(R.id.tv_metric_total);
                TextView tvTyped = viewDictionary.findViewById(R.id.tv_metric_typed);
                TextView tvCustom = viewDictionary.findViewById(R.id.tv_metric_custom);
                View emptyState = viewDictionary.findViewById(R.id.layout_dict_empty);
                RecyclerView rv = viewDictionary.findViewById(R.id.rv_dict_words);

                if (tvTotal != null) tvTotal.setText(String.format(Locale.US, "%,d", totalCount));
                if (tvTyped != null) tvTyped.setText(String.format(Locale.US, "%,d", myCount));
                if (tvCustom != null) tvCustom.setText(String.format(Locale.US, "%,d", customCount));

                if (words.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                    rv.setVisibility(View.GONE);
                } else {
                    emptyState.setVisibility(View.GONE);
                    rv.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    private void showAddWordDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_word, null);
        EditText etWord = dialogView.findViewById(R.id.et_dialog_word);
        EditText etFreq = dialogView.findViewById(R.id.et_dialog_freq);
        TextView tvError = dialogView.findViewById(R.id.tv_dialog_error);

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
                try { freq = Integer.parseInt(freqStr); } catch (Exception ignored) {}

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
                        Toast.makeText(MainActivity.this, "Added \"" + cleaned + "\"", Toast.LENGTH_SHORT).show();
                        loadDictionaryWords();
                    });
                }).start();
            });
        });
        dialog.show();
    }

    private void showCleanTyposDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clean Up Typos")
                .setMessage("This will remove all auto-learned words that were typed only once (1×) and never reused.\n\nYour manually added custom words and ISH Standard words will be completely preserved.")
                .setPositiveButton("Clean Up", (dialog, which) -> {
                    new Thread(() -> {
                        int pruned = corpusDb.pruneSingleUseTypos();
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Cleaned up " + pruned + " typos!", Toast.LENGTH_SHORT).show();
                            loadDictionaryWords();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showExportDialog() {
        String[] options = {
                "Export TSV (wordlist.tsv for Keyman / static builds)",
                "Export Full Lexicon (JSON with examples & suggestions)"
        };
        new AlertDialog.Builder(this)
                .setTitle("Export Lexicon Baseline")
                .setItems(options, (dialog, which) -> {
                    Toast.makeText(MainActivity.this, "Preparing export...", Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        File file = (which == 0) ? corpusDb.exportBaselineTsv() : corpusDb.exportFullArchiveJson();
                        if (file != null && file.exists()) {
                            runOnUiThread(() -> shareFile(file, which == 0 ? "text/tab-separated-values" : "application/json"));
                        } else {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Export failed", Toast.LENGTH_SHORT).show());
                        }
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void shareFile(File file, String mimeType) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share " + file.getName()));
        } catch (Exception e) {
            Toast.makeText(this, "Sharing error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================================
    // TAB 4: SETTINGS & PREFERENCES
    // =========================================================================
    private void setupSettingsTab() {
        swSettingsPeriod = viewSettings.findViewById(R.id.switch_settings_period);
        swSettingsTrackpad = viewSettings.findViewById(R.id.switch_settings_trackpad);
        swSettingsVibrate = viewSettings.findViewById(R.id.switch_settings_vibrate);
        swSettingsSound = viewSettings.findViewById(R.id.switch_settings_sound);
        swSettingsContribute = viewSettings.findViewById(R.id.switch_settings_contribute);

        View layoutContribute = viewSettings.findViewById(R.id.layout_settings_contribute);
        if (layoutContribute != null) {
            layoutContribute.setOnClickListener(v -> 
                Toast.makeText(this, "Data contribution feature isn't implemented yet.", Toast.LENGTH_SHORT).show()
            );
        }

        swPeriodListener();
        swTrackpadListener();
        swVibrateListener();
        swSoundListener();
        swContributeListener();

        TextView tvVersion = viewSettings.findViewById(R.id.tv_settings_version);
        if (tvVersion != null) {
            tvVersion.setText("Version " + BuildConfig.VERSION_NAME + "\nBuilding for builders. Preserving for tomorrow.");
        }

        viewSettings.findViewById(R.id.btn_settings_clear_corpus).setOnClickListener(v -> showClearHistoryDialog());
    }

    private void swContributeListener() {
        if (swSettingsContribute != null) {
            swSettingsContribute.setEnabled(false);
            swSettingsContribute.setChecked(false);
            swSettingsContribute.setOnClickListener(v -> 
                Toast.makeText(this, "Data contribution feature isn't implemented yet.", Toast.LENGTH_SHORT).show()
            );
            swSettingsContribute.setOnCheckedChangeListener((btn, isChecked) -> {
                if (isUpdatingSwitches) return;
                if (isChecked) {
                    btn.setChecked(false);
                    Toast.makeText(this, "Data contribution feature isn't implemented yet.", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void swPeriodListener() {
        swSettingsPeriod.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isUpdatingSwitches) return;
            prefs.edit().putBoolean("pref_double_tap_period", isChecked).apply();
            syncAllSwitches();
        });
    }

    private void swTrackpadListener() {
        swSettingsTrackpad.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isUpdatingSwitches) return;
            prefs.edit().putBoolean("pref_hold_space_trackpad", isChecked).apply();
            syncAllSwitches();
        });
    }

    private void swVibrateListener() {
        swSettingsVibrate.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isUpdatingSwitches) return;
            prefs.edit().putBoolean("pref_vibrate_on_keypress", isChecked).apply();
            syncAllSwitches();
        });
    }

    private void swSoundListener() {
        swSettingsSound.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isUpdatingSwitches) return;
            prefs.edit().putBoolean("pref_sound_on_keypress", isChecked).apply();
            syncAllSwitches();
        });
    }

    private void syncAllSwitches() {
        if (prefs == null) return;
        isUpdatingSwitches = true;
        try {
            boolean vibrate = prefs.getBoolean("pref_vibrate_on_keypress", true);
            boolean sound = prefs.getBoolean("pref_sound_on_keypress", false);
            boolean trackpad = prefs.getBoolean("pref_hold_space_trackpad", true);
            boolean period = prefs.getBoolean("pref_double_tap_period", true);

            if (swHomeVibrate != null && swHomeVibrate.isChecked() != vibrate) {
                swHomeVibrate.setChecked(vibrate);
            }
            if (swSettingsVibrate != null && swSettingsVibrate.isChecked() != vibrate) {
                swSettingsVibrate.setChecked(vibrate);
            }

            if (swHomeSound != null && swHomeSound.isChecked() != sound) {
                swHomeSound.setChecked(sound);
            }
            if (swSettingsSound != null && swSettingsSound.isChecked() != sound) {
                swSettingsSound.setChecked(sound);
            }

            if (swHomeTrackpad != null && swHomeTrackpad.isChecked() != trackpad) {
                swHomeTrackpad.setChecked(trackpad);
            }
            if (swSettingsTrackpad != null && swSettingsTrackpad.isChecked() != trackpad) {
                swSettingsTrackpad.setChecked(trackpad);
            }

            if (swSettingsPeriod != null && swSettingsPeriod.isChecked() != period) {
                swSettingsPeriod.setChecked(period);
            }

            if (swSettingsContribute != null) {
                swSettingsContribute.setChecked(false);
                swSettingsContribute.setEnabled(false);
            }
        } finally {
            isUpdatingSwitches = false;
        }
    }

    private void updateSettingsStats() {
        // Stats display removed from simplified UI
    }

    private void exportCorpusDataset() {
        new Thread(() -> {
            List<String> lines = corpusDb.getAllSentences();
            if (lines.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "No sentences collected yet.", Toast.LENGTH_SHORT).show());
                return;
            }
            try {
                File cacheDir = new File(getCacheDir(), "exports");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                String dateStr = new SimpleDateFormat("dd-MM-yy", Locale.US).format(new Date());
                int randomCode = 1000 + new java.util.Random().nextInt(9000);
                File exportFile = new File(cacheDir, dateStr + "-" + randomCode + ".jsonl");

                try (FileOutputStream fos = new FileOutputStream(exportFile)) {
                    for (String line : lines) {
                        fos.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                    fos.flush();
                }
                runOnUiThread(() -> shareFile(exportFile, "application/x-jsonlines"));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Export error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showClearHistoryDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Learned Data")
                .setMessage("Are you sure you want to delete all sentences and bigrams learned from your typing history?\n\nYour official ISH dictionary will remain untouched.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    new Thread(() -> {
                        corpusDb.clearAllData();
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Learned data cleared", Toast.LENGTH_SHORT).show();
                            updateSettingsStats();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // =========================================================================
    // SYSTEM INPUT METHOD UTILITIES
    // =========================================================================
    private void showImePicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showInputMethodPicker();
        }
    }

    private boolean isKeyboardEnabled() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return false;
        List<InputMethodInfo> enabledMethods = imm.getEnabledInputMethodList();
        String myPackage = getPackageName();
        for (InputMethodInfo info : enabledMethods) {
            if (info.getPackageName().equals(myPackage)) {
                return true;
            }
        }
        return false;
    }

    private boolean isKeyboardSelected() {
        String currentId = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return currentId != null && currentId.contains(getPackageName());
    }

    @Override
    public void onBackPressed() {
        if (isDeleteMode) {
            exitDeleteMode();
            return;
        }
        super.onBackPressed();
    }

    private void enterDeleteMode() {
        isDeleteMode = true;
        selectedWords.clear();
        if (bottomNav != null) bottomNav.setVisibility(View.GONE);
        if (layoutDeleteModeBar != null) layoutDeleteModeBar.setVisibility(View.VISIBLE);
        updateDeleteModeCount();
        if (dictAdapter != null) dictAdapter.notifyDataSetChanged();
    }

    private void exitDeleteMode() {
        isDeleteMode = false;
        selectedWords.clear();
        if (layoutDeleteModeBar != null) layoutDeleteModeBar.setVisibility(View.GONE);
        if (bottomNav != null) bottomNav.setVisibility(View.VISIBLE);
        if (dictAdapter != null) dictAdapter.notifyDataSetChanged();
    }

    private void toggleWordSelection(String word) {
        if (selectedWords.contains(word)) {
            selectedWords.remove(word);
        } else {
            selectedWords.add(word);
        }
        updateDeleteModeCount();
        if (dictAdapter != null) dictAdapter.notifyDataSetChanged();
    }

    private void toggleSelectAll() {
        if (dictAdapter == null || dictAdapter.list.isEmpty()) return;
        boolean allSelected = true;
        for (UserCorpusDatabase.PersonalWord pw : dictAdapter.list) {
            if (!selectedWords.contains(pw.word)) {
                allSelected = false;
                break;
            }
        }
        if (allSelected) {
            selectedWords.clear();
        } else {
            for (UserCorpusDatabase.PersonalWord pw : dictAdapter.list) {
                selectedWords.add(pw.word);
            }
        }
        updateDeleteModeCount();
        dictAdapter.notifyDataSetChanged();
    }

    private void updateDeleteModeCount() {
        int count = selectedWords.size();
        if (tvDeleteModeCount != null) {
            tvDeleteModeCount.setText(count + " selected");
        }
        if (tvDeleteButtonLabel != null) {
            tvDeleteButtonLabel.setText(count > 0 ? "Delete (" + count + ")" : "Delete");
        }
        if (btnDeleteModeSelectAll != null && dictAdapter != null) {
            boolean allSelected = !dictAdapter.list.isEmpty() && selectedWords.size() >= dictAdapter.list.size();
            btnDeleteModeSelectAll.setText(allSelected ? "Deselect All" : "Select All");
        }
        if (btnDeleteModeConfirm != null) {
            btnDeleteModeConfirm.setAlpha(count > 0 ? 1.0f : 0.4f);
        }
    }

    private void confirmBulkDelete() {
        if (selectedWords.isEmpty()) {
            Toast.makeText(this, "No words selected", Toast.LENGTH_SHORT).show();
            return;
        }
        final int count = selectedWords.size();
        final List<String> toDelete = new ArrayList<>(selectedWords);

        new AlertDialog.Builder(this)
                .setTitle("Delete Words")
                .setMessage("Delete " + count + " selected words from dictionary?\n\nThey will no longer appear in predictions or search.")
                .setPositiveButton("Delete (" + count + ")", (dialog, which) -> {
                    new Thread(() -> {
                        int deleted = corpusDb.markWordsDeleted(toDelete);
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Deleted " + deleted + " words", Toast.LENGTH_SHORT).show();
                            exitDeleteMode();
                            loadDictionaryWords();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // =========================================================================
    // RECYCLERVIEW ADAPTER FOR PERSONAL DICTIONARY
    // =========================================================================
    private class WordAdapter extends RecyclerView.Adapter<WordAdapter.WordViewHolder> {
        final List<UserCorpusDatabase.PersonalWord> list = new ArrayList<>();

        public void setWords(List<UserCorpusDatabase.PersonalWord> words) {
            list.clear();
            if (words != null) list.addAll(words);
            notifyDataSetChanged();
            if (isDeleteMode) updateDeleteModeCount();
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
                holder.tvMeta.setText("Typed " + pw.frequency + (pw.frequency == 1 ? " time" : " times") + (pw.suggestReplacement != null ? " ➔ suggests \"" + pw.suggestReplacement + "\"" : ""));
            } else {
                holder.tvFreqBadge.setVisibility(View.GONE);
                holder.tvMeta.setText("Official dictionary word" + (pw.suggestReplacement != null ? " ➔ suggests \"" + pw.suggestReplacement + "\"" : " • Not typed yet"));
            }

            if (isDeleteMode) {
                holder.ivSelect.setVisibility(View.VISIBLE);
                boolean isSelected = selectedWords.contains(pw.word);
                if (isSelected) {
                    holder.ivSelect.setImageResource(R.drawable.ic_check_circle);
                    holder.layoutCard.setBackgroundResource(R.drawable.bg_card_selected);
                } else {
                    holder.ivSelect.setImageResource(R.drawable.ic_circle_outline);
                    holder.layoutCard.setBackgroundResource(R.drawable.bg_card);
                }
                holder.itemView.setOnClickListener(v -> toggleWordSelection(pw.word));
                holder.itemView.setOnLongClickListener(null);
            } else {
                holder.ivSelect.setVisibility(View.GONE);
                holder.layoutCard.setBackgroundResource(R.drawable.bg_card);
                holder.itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(MainActivity.this, WordDetailActivity.class);
                    intent.putExtra("word", pw.word);
                    startActivity(intent);
                });
                holder.itemView.setOnLongClickListener(v -> {
                    enterDeleteMode();
                    toggleWordSelection(pw.word);
                    return true;
                });
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class WordViewHolder extends RecyclerView.ViewHolder {
            final View layoutCard;
            final TextView tvDisplay;
            final TextView tvBadgeIsh;
            final TextView tvBadgeCustom;
            final TextView tvMeta;
            final TextView tvFreqBadge;
            final ImageView ivSelect;

            public WordViewHolder(@NonNull View itemView) {
                super(itemView);
                layoutCard = itemView.findViewById(R.id.layout_word_card);
                tvDisplay = itemView.findViewById(R.id.tv_word_display);
                tvBadgeIsh = itemView.findViewById(R.id.tv_badge_ish);
                tvBadgeCustom = itemView.findViewById(R.id.tv_badge_custom);
                tvMeta = itemView.findViewById(R.id.tv_word_meta);
                tvFreqBadge = itemView.findViewById(R.id.tv_freq_badge);
                ivSelect = itemView.findViewById(R.id.iv_word_select);
            }
        }
    }

    private void showDeleteWordDialog(UserCorpusDatabase.PersonalWord pw) {
        String msg = pw.isIshStandard
                ? "Delete \"" + pw.displayWord + "\" from the dictionary?\n\nIt will no longer appear in predictions or suggestions."
                : "Remove \"" + pw.displayWord + "\" from your personal dictionary?";

        new AlertDialog.Builder(this)
                .setTitle("Delete Word")
                .setMessage(msg)
                .setPositiveButton("Delete", (dialog, which) -> {
                    new Thread(() -> {
                        corpusDb.markWordDeleted(pw.word);
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Deleted \"" + pw.displayWord + "\"", Toast.LENGTH_SHORT).show();
                            loadDictionaryWords();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
