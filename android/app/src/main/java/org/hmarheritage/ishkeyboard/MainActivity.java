package org.hmarheritage.ishkeyboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.keyman.engine.KMManager;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int MIN_HEIGHT_DP = 220;
    private static final int DEFAULT_HEIGHT_DP = 275;

    // View containers
    private View layoutOnboarding;
    private View layoutMainSettings;

    // Onboarding views
    private TextView onboardingStatusText;
    private Button btnEnable;
    private Button btnSelect;

    // Dashboard views
    private TextView statusChip;
    private TextView textHeightValue;
    private SeekBar seekHeight;
    private EditText editTest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind containers
        layoutOnboarding = findViewById(R.id.layout_onboarding);
        layoutMainSettings = findViewById(R.id.layout_main_settings);

        // Bind onboarding controls
        onboardingStatusText = findViewById(R.id.onboarding_status_text);
        btnEnable = findViewById(R.id.btn_enable);
        btnSelect = findViewById(R.id.btn_select);

        // Bind dashboard controls
        statusChip = findViewById(R.id.status_chip);
        textHeightValue = findViewById(R.id.text_height_value);
        seekHeight = findViewById(R.id.seek_height);
        editTest = findViewById(R.id.edit_test);

        // Step 1: Open system IME settings
        btnEnable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
                startActivity(intent);
            }
        });

        // Step 2: Show input method picker
        btnSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImePicker();
            }
        });

        // Active status chip also allows switching keyboards on demand
        statusChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImePicker();
            }
        });

        setupHeightControls();
        setupMenuPlaceholders();
    }

    private void showImePicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showInputMethodPicker();
        }
    }

    private void setupHeightControls() {
        SharedPreferences prefs = getSharedPreferences("KMAPreferences", Context.MODE_PRIVATE);
        float density = getResources().getDisplayMetrics().density;
        int currentPx = prefs.getInt("keyboardHeightPortrait", 0);
        int currentDp = currentPx > 0 ? Math.round(currentPx / density) : DEFAULT_HEIGHT_DP;

        int initialProgress = Math.max(0, Math.min(140, currentDp - MIN_HEIGHT_DP));
        seekHeight.setProgress(initialProgress);
        updateHeightLabel(MIN_HEIGHT_DP + initialProgress);

        seekHeight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int heightDp = MIN_HEIGHT_DP + progress;
                updateHeightLabel(heightDp);
                if (fromUser) {
                    saveKeyboardHeight(heightDp);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void updateHeightLabel(int heightDp) {
        String label = "Normal";
        if (heightDp <= 245) label = "Compact";
        else if (heightDp <= 285) label = "Normal";
        else if (heightDp <= 325) label = "Tall";
        else label = "Extra Tall";

        textHeightValue.setText(heightDp + " dp (" + label + ")");
    }

    private void saveKeyboardHeight(int heightDp) {
        float density = getResources().getDisplayMetrics().density;
        int heightPx = Math.round(heightDp * density);

        SharedPreferences prefs = getSharedPreferences("KMAPreferences", Context.MODE_PRIVATE);
        prefs.edit().putInt("keyboardHeightPortrait", heightPx).apply();

        KMManager.applyKeyboardHeight(getApplicationContext(), heightPx);
    }

    private void setupMenuPlaceholders() {
        // Orthography Guide Dedicated Page
        findViewById(R.id.menu_item_guide).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, OrthographyGuideActivity.class);
                startActivity(intent);
            }
        });

        // About Dedicated Page
        findViewById(R.id.menu_item_about).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, AboutActivity.class);
                startActivity(intent);
            }
        });

        // Other menu items
        findViewById(R.id.menu_item_theme).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Themes menu coming next!", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.menu_item_preferences).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Preferences menu coming next!", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.menu_item_predictions).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Predictions & AI Research coming next!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        boolean isEnabled = isKeyboardEnabled();
        boolean isSelected = isKeyboardSelected();

        if (isSelected) {
            // Keyboard is active and selected!
            // Hide onboarding completely, show main companion dashboard
            layoutOnboarding.setVisibility(View.GONE);
            layoutMainSettings.setVisibility(View.VISIBLE);
        } else {
            // Keyboard is not selected: show onboarding flow
            layoutOnboarding.setVisibility(View.VISIBLE);
            layoutMainSettings.setVisibility(View.GONE);

            if (isEnabled) {
                onboardingStatusText.setText("Step 1 complete! Tap Step 2 to select ISH Keyboard.");
                btnEnable.setEnabled(false);
                btnEnable.setText("✓ 1. Enabled in Settings");
                btnSelect.setEnabled(true);
            } else {
                onboardingStatusText.setText("Follow these two quick steps to start typing in Hmar:");
                btnEnable.setEnabled(true);
                btnEnable.setText("1. Enable in System Settings");
                btnSelect.setEnabled(false);
            }
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
}
