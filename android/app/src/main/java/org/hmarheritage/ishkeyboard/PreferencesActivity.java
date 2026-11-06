package org.hmarheritage.ishkeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

public class PreferencesActivity extends AppCompatActivity {

    public static final String PREF_FILE = "KMAPreferences";
    public static final String KEY_VIBRATE = "pref_vibrate_on_keypress";
    public static final String KEY_VIBRATION_STRENGTH = "pref_vibration_strength"; // 0: Subtle, 1: Normal, 2: Firm
    public static final String KEY_SOUND = "pref_sound_on_keypress";
    public static final String KEY_DOUBLE_SPACE = "pref_double_tap_period";
    public static final String KEY_TRACKPAD = "pref_hold_space_trackpad";

    private SharedPreferences prefs;
    private SwitchCompat switchVibrate;
    private View containerVibrationStrength;
    private TextView tvStrengthLabel;
    private SeekBar seekVibrationStrength;
    private SwitchCompat switchSound;
    private SwitchCompat switchDoubleSpace;
    private SwitchCompat switchTrackpad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences);

        prefs = getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        switchVibrate = findViewById(R.id.switch_vibrate);
        containerVibrationStrength = findViewById(R.id.container_vibration_strength);
        tvStrengthLabel = findViewById(R.id.tv_strength_label);
        seekVibrationStrength = findViewById(R.id.seek_vibration_strength);
        switchSound = findViewById(R.id.switch_sound);
        switchDoubleSpace = findViewById(R.id.switch_double_space);
        switchTrackpad = findViewById(R.id.switch_trackpad);

        initValues();
        setupListeners();
    }

    private void initValues() {
        boolean vibrate = prefs.getBoolean(KEY_VIBRATE, true);
        switchVibrate.setChecked(vibrate);
        containerVibrationStrength.setVisibility(vibrate ? View.VISIBLE : View.GONE);

        int strength = prefs.getInt(KEY_VIBRATION_STRENGTH, 1);
        seekVibrationStrength.setProgress(strength);
        updateStrengthLabel(strength);

        switchSound.setChecked(prefs.getBoolean(KEY_SOUND, false));
        switchDoubleSpace.setChecked(prefs.getBoolean(KEY_DOUBLE_SPACE, true));
        switchTrackpad.setChecked(prefs.getBoolean(KEY_TRACKPAD, true));
    }

    private void setupListeners() {
        switchVibrate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_VIBRATE, isChecked).apply();
            containerVibrationStrength.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked) {
                previewVibration(seekVibrationStrength.getProgress());
            }
        });

        seekVibrationStrength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateStrengthLabel(progress);
                if (fromUser) {
                    prefs.edit().putInt(KEY_VIBRATION_STRENGTH, progress).apply();
                    previewVibration(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        switchSound.setOnCheckedChangeListener((buttonView, isChecked) ->
            prefs.edit().putBoolean(KEY_SOUND, isChecked).apply());

        switchDoubleSpace.setOnCheckedChangeListener((buttonView, isChecked) ->
            prefs.edit().putBoolean(KEY_DOUBLE_SPACE, isChecked).apply());

        switchTrackpad.setOnCheckedChangeListener((buttonView, isChecked) ->
            prefs.edit().putBoolean(KEY_TRACKPAD, isChecked).apply());
    }

    private void updateStrengthLabel(int strength) {
        if (strength == 0) {
            tvStrengthLabel.setText("Subtle (10ms)");
        } else if (strength == 2) {
            tvStrengthLabel.setText("Firm (35ms)");
        } else {
            tvStrengthLabel.setText("Normal (20ms)");
        }
    }

    private void previewVibration(int strength) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (strength == 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
                    } else {
                        v.vibrate(10);
                    }
                } else if (strength == 2) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK));
                    } else {
                        v.vibrate(35);
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
                    } else {
                        v.vibrate(20);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }
}
