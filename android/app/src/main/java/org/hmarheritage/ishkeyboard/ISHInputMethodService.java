package org.hmarheritage.ishkeyboard;

import com.keyman.engine.data.Keyboard;
import com.keyman.engine.KMManager;
import com.keyman.engine.KMManager.KeyboardType;
import com.keyman.engine.KMHardwareKeyboardInterpreter;
import com.keyman.engine.KeyboardEventHandler.OnKeyboardEventListener;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.FrameLayout;

import java.util.HashMap;
import java.util.List;

public class ISHInputMethodService extends InputMethodService implements OnKeyboardEventListener {

    private static final String TAG = "ISHKeyboard";
    private FrameLayout rootInputContainer = null;
    private View keymanView = null;
    private EmojiPanelView emojiPanelView = null;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private KMHardwareKeyboardInterpreter interpreter = null;
    private SentenceProcessor sentenceProcessor = null;
    private EditorInfo currentEditorInfo = null;
    private final java.util.concurrent.ExecutorService backgroundExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    /** Guard to prevent redundant registerLexicalModel calls (each triggers an 11.7MB JS model load). */
    private boolean lexicalModelRegistered = false;

    @Override
    public void onCreate() {
        super.onCreate();
        sentenceProcessor = new SentenceProcessor(this);
        KMManager.setDebugMode(BuildConfig.DEBUG);
        KMManager.addKeyboardEventListener(this);
        KMManager.initialize(getApplicationContext(), KeyboardType.KEYBOARD_TYPE_SYSTEM);
        interpreter = new KMHardwareKeyboardInterpreter(getApplicationContext(), KeyboardType.KEYBOARD_TYPE_SYSTEM);

        // Add ISH Keyboard package (hmar_ish.kmp in assets/)
        Keyboard kbInfo = new Keyboard(
            "hmar_ish",     // Package ID
            "hmar_ish",     // Keyboard ID
            "ISH Keyboard", // Keyboard Name
            "hmr",          // Language ID
            "Hmar",         // Language Name
            BuildConfig.VERSION_NAME, // Keyboard Version
            null,           // Help URL
            "",             // KMP URL
            true,           // isNew
            KMManager.KMDefault_KeyboardFont,
            KMManager.KMDefault_KeyboardFont
        );
        syncLatestKeyboardAssets();
        KMManager.addKeyboard(this, kbInfo);

        // Register and associate the ISH Dual Lexicon model
        HashMap<String, String> lmInfo = new HashMap<>();
        lmInfo.put(KMManager.KMKey_PackageID, "hhf.hmr.ish");
        lmInfo.put(KMManager.KMKey_LexicalModelID, "hhf.hmr.ish");
        lmInfo.put(KMManager.KMKey_LexicalModelName, "ISH Dual Lexicon");
        lmInfo.put(KMManager.KMKey_LanguageID, "hmr");
        lmInfo.put(KMManager.KMKey_LanguageName, "Hmar");
        lmInfo.put(KMManager.KMKey_LexicalModelVersion, "1.0.0");
        lmInfo.put(KMManager.KMKey_CustomModel, "true");
        KMManager.addLexicalModel(getApplicationContext(), lmInfo);
        KMManager.registerAssociatedLexicalModel("hmr");

        // Enable predictive suggestion banner and preferences
        android.content.SharedPreferences prefs = getSharedPreferences("KMAPreferences", MODE_PRIVATE);
        prefs.edit()
            .putBoolean(KMManager.getLanguagePredictionPreferenceKey("hmr"), true)
            .putBoolean(KMManager.getLanguageCorrectionPreferenceKey("hmr"), true)
            .apply();

        WebView systemKb = KMManager.getKMKeyboard(KeyboardType.KEYBOARD_TYPE_SYSTEM);
        if (systemKb != null) {
            setupWebView(systemKb);
        }

        // Pre-warm keymanView and WebView during service startup for instant keyboard display
        if (keymanView == null) {
            try {
                keymanView = KMManager.createInputView(this);
                setupWebView(keymanView);
            } catch (Throwable t) {
                Log.w(TAG, "Pre-warming input view failed: " + t);
            }
        }
    }

    private void setupWebView(View view) {
        if (view == null) return;
        if (view instanceof WebView) {
            WebView wv = (WebView) view;
            if ("ish_setup".equals(wv.getTag())) {
                return;
            }
            wv.setTag("ish_setup");
            WebSettings ws = wv.getSettings();
            ws.setAllowFileAccess(true);
            ws.setAllowContentAccess(true);
            ws.setAllowFileAccessFromFileURLs(true);
            ws.setAllowUniversalAccessFromFileURLs(true);

            wv.addJavascriptInterface(new Object() {
                @android.webkit.JavascriptInterface
                public String getModelCode(String lmId) {
                    try {
                        try (java.io.InputStream is = getAssets().open("models/" + lmId + "/" + lmId + ".model.js")) {
                            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                            byte[] data = new byte[16384];
                            int nRead;
                            while ((nRead = is.read(data, 0, data.length)) != -1) {
                                buffer.write(data, 0, nRead);
                            }
                            buffer.flush();
                            String code = new String(buffer.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                            Log.d(TAG, "Loaded model " + lmId + " directly from assets (" + code.length() + " chars)");
                            return code;
                        } catch (Throwable t) {
                            Log.w(TAG, "Could not open asset model directly: " + t);
                        }

                        java.io.File f = new java.io.File(KMManager.getLexicalModelsDir(), lmId + java.io.File.separator + lmId + ".model.js");
                        if (f.exists()) {
                            try (java.io.FileInputStream fis = new java.io.FileInputStream(f);
                                 java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
                                byte[] buf = new byte[16384];
                                int len;
                                while ((len = fis.read(buf)) != -1) {
                                    bos.write(buf, 0, len);
                                }
                                return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                            }
                        }
                    } catch (Throwable e) {
                        Log.e(TAG, "Failed to read model code: " + e);
                    }
                    return null;
                }

                @android.webkit.JavascriptInterface
                public String getFusedMap() {
                    try (java.io.InputStream is = getAssets().open("models/fused_map.json")) {
                        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                        byte[] data = new byte[16384];
                        int nRead;
                        while ((nRead = is.read(data, 0, data.length)) != -1) {
                            buffer.write(data, 0, nRead);
                        }
                        buffer.flush();
                        return new String(buffer.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Throwable t) {
                        Log.w(TAG, "Could not open fused_map.json: " + t);
                    }
                    return null;
                }

                @android.webkit.JavascriptInterface
                public String getTextBeforeCursor(int n) {
                    try {
                        android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
                        if (ic != null) {
                            CharSequence cs = ic.getTextBeforeCursor(n, 0);
                            if (cs != null) return cs.toString();
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Could not get text before cursor: " + t);
                    }
                    return "";
                }

                @android.webkit.JavascriptInterface
                public void onSentenceBoundary(String text) {
                    try {
                        if (sentenceProcessor != null && currentEditorInfo != null) {
                            sentenceProcessor.processBuffer(text, currentEditorInfo);
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Error in onSentenceBoundary: " + t);
                    }
                }

                @android.webkit.JavascriptInterface
                public void openEmojiPicker() {
                    mainHandler.post(() -> showEmojiPanel(true));
                }

                @android.webkit.JavascriptInterface
                public void onCursorScrubberStart() {
                    triggerHaptic(VibrationEffect.EFFECT_CLICK, 25);
                }

                @android.webkit.JavascriptInterface
                public void onCursorScrubberEnd() {
                    triggerHaptic(VibrationEffect.EFFECT_CLICK, 15);
                }

                @android.webkit.JavascriptInterface
                public void moveCursor(final int direction) {
                    mainHandler.post(() -> {
                        try {
                            InputConnection ic = getCurrentInputConnection();
                            if (ic != null) {
                                if (direction < 0) {
                                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT);
                                } else if (direction > 0) {
                                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT);
                                }
                            }
                            triggerHaptic(VibrationEffect.EFFECT_TICK, 10);
                        } catch (Throwable t) {
                            Log.w(TAG, "moveCursor error: " + t);
                        }
                    });
                }

                @android.webkit.JavascriptInterface
                public void onKeyTap() {
                    mainHandler.post(() -> ISHInputMethodService.this.onKeyTap());
                }

                @android.webkit.JavascriptInterface
                public boolean isDoubleTapPeriodEnabled() {
                    android.content.SharedPreferences prefs = getSharedPreferences("KMAPreferences", Context.MODE_PRIVATE);
                    return prefs.getBoolean("pref_double_tap_period", true);
                }

                @android.webkit.JavascriptInterface
                public boolean isTrackpadEnabled() {
                    android.content.SharedPreferences prefs = getSharedPreferences("KMAPreferences", Context.MODE_PRIVATE);
                    return prefs.getBoolean("pref_hold_space_trackpad", true);
                }

                @android.webkit.JavascriptInterface
                public String getPersonalSuggestions(String prefix, int limit) {
                    try {
                        if (prefix == null || prefix.trim().length() < 2) return "[]";
                        List<UserCorpusDatabase.PersonalWord> list = UserCorpusDatabase.getInstance(ISHInputMethodService.this).getSuggestionsForPrefix(prefix.trim(), limit);
                        org.json.JSONArray arr = new org.json.JSONArray();
                        for (UserCorpusDatabase.PersonalWord pw : list) {
                            org.json.JSONObject obj = new org.json.JSONObject();
                            obj.put("word", pw.displayWord);
                            obj.put("freq", pw.frequency);
                            obj.put("isUser", pw.isUserAdded);
                            obj.put("isExactReplacement", pw.isExactReplacement);
                            obj.put("isLiteral", pw.isLiteral);
                            if (pw.suggestReplacement != null && !pw.suggestReplacement.trim().isEmpty()) {
                                obj.put("replacement", pw.suggestReplacement.trim());
                            }
                            arr.put(obj);
                        }
                        return arr.toString();
                    } catch (Throwable t) {
                        Log.w(TAG, "getPersonalSuggestions error: " + t);
                        return "[]";
                    }
                }

                @android.webkit.JavascriptInterface
                public String getNextWordPredictions(String prevWord, int limit) {
                    try {
                        if (prevWord == null || prevWord.trim().isEmpty()) return "[]";
                        List<String> list = UserCorpusDatabase.getInstance(ISHInputMethodService.this).getNextWordPredictions(prevWord.trim(), limit);
                        org.json.JSONArray arr = new org.json.JSONArray();
                        for (String w : list) {
                            arr.put(w);
                        }
                        return arr.toString();
                    } catch (Throwable t) {
                        Log.w(TAG, "getNextWordPredictions error: " + t);
                        return "[]";
                    }
                }

                @android.webkit.JavascriptInterface
                public String getExactReplacement(String word) {
                    try {
                        if (word == null || word.trim().isEmpty()) return null;
                        return UserCorpusDatabase.getInstance(ISHInputMethodService.this).getExactReplacement(word.trim());
                    } catch (Throwable t) {
                        Log.w(TAG, "getExactReplacement error: " + t);
                        return null;
                    }
                }

                @android.webkit.JavascriptInterface
                public void onWordTransition(String prev, String next) {
                    try {
                        if (prev != null && next != null) {
                            backgroundExecutor.execute(() -> {
                                try {
                                    UserCorpusDatabase.getInstance(ISHInputMethodService.this).recordWordTransition(prev, next);
                                } catch (Throwable t) {
                                    Log.w(TAG, "recordWordTransition executor error: " + t);
                                }
                            });
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "onWordTransition error: " + t);
                    }
                }
            }, "ishBridge");

            wv.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                    Log.d("KM_CONSOLE", "[" + consoleMessage.messageLevel() + "] "
                        + consoleMessage.message() + " (" + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber() + ")");
                    return true;
                }
            });
            Log.d(TAG, "Configured WebView settings, ishBridge, and ChromeClient on " + wv);
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                setupWebView(vg.getChildAt(i));
            }
        }
    }

    public void onKeyTap() {
        android.content.SharedPreferences prefs = getSharedPreferences("KMAPreferences", Context.MODE_PRIVATE);
        boolean vibrate = prefs.getBoolean("pref_vibrate_on_keypress", true);
        if (vibrate) {
            int strength = prefs.getInt("pref_vibration_strength", 1);
            int effectType = VibrationEffect.EFFECT_CLICK;
            long durationMs = 20;
            if (strength == 0) {
                effectType = VibrationEffect.EFFECT_TICK;
                durationMs = 10;
            } else if (strength == 2) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    effectType = VibrationEffect.EFFECT_HEAVY_CLICK;
                }
                durationMs = 35;
            }
            triggerHaptic(effectType, durationMs);
        }

        boolean sound = prefs.getBoolean("pref_sound_on_keypress", false);
        if (sound) {
            try {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 1.0f);
                }
            } catch (Throwable ignored) {}
        }
    }

    private void triggerHaptic(int effectType, long fallbackMs) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    v.vibrate(VibrationEffect.createPredefined(effectType));
                } else {
                    v.vibrate(fallbackMs);
                }
            }
        } catch (Throwable ignored) {}
    }

    private int getVisibleKeyboardHeight() {
        int kbHeight = KMManager.getKeyboardHeight(this);
        int bannerHeight = KMManager.getBannerHeight(this);
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (kbHeight <= 0) {
            float density = getResources().getDisplayMetrics().density;
            kbHeight = Math.round((isLandscape ? 192 : 275) * density);
        }
        if (bannerHeight <= 0) {
            float density = getResources().getDisplayMetrics().density;
            bannerHeight = Math.round((isLandscape ? 38 : 45) * density);
        }
        return kbHeight + bannerHeight;
    }

    public void showEmojiPanel(boolean show) {
        if (rootInputContainer == null || keymanView == null || emojiPanelView == null) return;
        if (show) {
            int totalH = getVisibleKeyboardHeight();
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) emojiPanelView.getLayoutParams();
            if (lp == null) {
                lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, totalH, Gravity.BOTTOM);
            } else {
                lp.height = totalH;
                lp.gravity = Gravity.BOTTOM;
            }
            emojiPanelView.setLayoutParams(lp);
            keymanView.setVisibility(View.INVISIBLE);
            emojiPanelView.setVisibility(View.VISIBLE);
            emojiPanelView.bringToFront();
        } else {
            emojiPanelView.setVisibility(View.GONE);
            keymanView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroy() {
        backgroundExecutor.shutdown();
        rootInputContainer = null;
        keymanView = null;
        emojiPanelView = null;
        lexicalModelRegistered = false;
        KMManager.removeKeyboardEventListener(this);
        interpreter = null;
        KMManager.onDestroy();
        super.onDestroy();
    }

    @Override
    public View onCreateInputView() {
        // NOTE: Do NOT add an early-return cache here. Android's setInputView() calls
        // mInputFrame.addView(v), which throws IllegalStateException if the view already
        // has a parent. We must always rebuild child hierarchy and detach rootInputContainer
        // from any prior IME window parent before returning it.

        if (keymanView == null) {
            keymanView = KMManager.createInputView(this);
        }
        // setupWebView is tag-guarded — safe to call every time, skips if already configured
        setupWebView(keymanView);
        KMManager.setBanner(KeyboardType.KEYBOARD_TYPE_SYSTEM, KMManager.BannerType.SUGGESTION);
        KMManager.showBanner(true);
        int kbHeight = KMManager.getKeyboardHeight(this);
        KMManager.applyKeyboardHeight(this, kbHeight);

        if (rootInputContainer == null) {
            rootInputContainer = new FrameLayout(this);
            FrameLayout.LayoutParams rLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            rootInputContainer.setLayoutParams(rLp);
        } else {
            // Detach all children so we can re-add them cleanly
            rootInputContainer.removeAllViews();
        }
        rootInputContainer.setBackgroundColor(Color.TRANSPARENT);

        // Detach keymanView from any prior parent before adding to rootInputContainer
        ViewGroup parent = (ViewGroup) keymanView.getParent();
        if (parent != null) {
            parent.removeView(keymanView);
        }
        FrameLayout.LayoutParams keymanLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        rootInputContainer.addView(keymanView, keymanLp);

        if (emojiPanelView == null) {
            emojiPanelView = new EmojiPanelView(this, new EmojiPanelView.EmojiListener() {
                @Override
                public void onEmojiSelected(String emoji) {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        ic.commitText(emoji, 1);
                    }
                    onKeyTap();
                }

                @Override
                public void onBackspace() {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                    onKeyTap();
                }

                @Override
                public void onClose() {
                    showEmojiPanel(false);
                }
            });
        }

        // Detach emojiPanelView from any prior parent before adding to rootInputContainer
        ViewGroup emojiParent = (ViewGroup) emojiPanelView.getParent();
        if (emojiParent != null) {
            emojiParent.removeView(emojiPanelView);
        }
        emojiPanelView.setVisibility(View.GONE);
        int totalH = getVisibleKeyboardHeight();
        FrameLayout.LayoutParams emojiLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, totalH, Gravity.BOTTOM);
        rootInputContainer.addView(emojiPanelView, emojiLp);

        // Detach rootInputContainer from any prior IME window parent — Android's setInputView()
        // calls addView() on its internal mInputFrame, which throws if the view already has a parent.
        ViewGroup rcParent = (ViewGroup) rootInputContainer.getParent();
        if (rcParent != null) {
            rcParent.removeView(rootInputContainer);
        }

        return rootInputContainer;
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        KMManager.updateSelectionRange(KeyboardType.KEYBOARD_TYPE_SYSTEM, newSelStart, newSelEnd);
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        showEmojiPanel(false);
        if (!restarting && sentenceProcessor != null) {
            sentenceProcessor.resetSession();
        }
        attribute.imeOptions |= EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN;
        super.onStartInput(attribute, restarting);
        KMManager.onStartInput(attribute, restarting);
        KMManager.resetContext(KeyboardType.KEYBOARD_TYPE_SYSTEM);
        KMManager.setBanner(KeyboardType.KEYBOARD_TYPE_SYSTEM, KMManager.BannerType.SUGGESTION);
        KMManager.showBanner(true);
        int kbHeight = KMManager.getKeyboardHeight(this);
        KMManager.applyKeyboardHeight(this, kbHeight);

        if (KMManager.isKeyboardLoaded(KeyboardType.KEYBOARD_TYPE_SYSTEM)) {
            HashMap<String, String> lm = KMManager.getAssociatedLexicalModel("hmr");
            if (lm != null && !lexicalModelRegistered) {
                lexicalModelRegistered = true;
                KMManager.registerLexicalModel(lm);
            }
        }

        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ExtractedText icText = ic.getExtractedText(new ExtractedTextRequest(), 0);
            if (icText != null) {
                KMManager.updateText(KeyboardType.KEYBOARD_TYPE_SYSTEM, icText.text.toString());
                int selStart = icText.startOffset + icText.selectionStart;
                int selEnd = icText.startOffset + icText.selectionEnd;
                KMManager.updateSelectionRange(KeyboardType.KEYBOARD_TYPE_SYSTEM, selStart, selEnd);
            }
        }
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        this.currentEditorInfo = attribute;
        showEmojiPanel(false);
        // Always call setInputView — onCreateInputView() detaches rootInputContainer from any
        // prior IME window parent before returning, so addView() inside setInputView never throws.
        setInputView(onCreateInputView());
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        showEmojiPanel(false);
        try {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null && sentenceProcessor != null && currentEditorInfo != null) {
                CharSequence cs = ic.getTextBeforeCursor(2000, 0);
                if (cs != null && cs.length() > 0) {
                    sentenceProcessor.onFieldFinished(cs.toString(), currentEditorInfo);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error in onFinishInputView: " + t);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        KMManager.onConfigurationChanged(newConfig);
        int kbHeight = KMManager.getKeyboardHeight(this);
        KMManager.applyKeyboardHeight(this, kbHeight);
        if (emojiPanelView != null) {
            int totalH = getVisibleKeyboardHeight();
            ViewGroup.LayoutParams lp = emojiPanelView.getLayoutParams();
            if (lp != null) {
                lp.height = totalH;
                emojiPanelView.setLayoutParams(lp);
            }
        }
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public void onConfigureWindow(Window win, boolean isFullscreen, boolean isCandidatesOnly) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly);
        win.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    }

    @Override
    public void onComputeInsets(InputMethodService.Insets outInsets) {
        super.onComputeInsets(outInsets);

        int windowH = 0;
        int windowW = 0;

        Window win = getWindow() != null ? getWindow().getWindow() : null;
        if (win != null && win.getDecorView() != null) {
            View decor = win.getDecorView();
            windowH = decor.getHeight();
            windowW = decor.getWidth();
        }

        if (windowH <= 0 || windowW <= 0) {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.graphics.Rect bounds = wm.getCurrentWindowMetrics().getBounds();
                    if (windowH <= 0) windowH = bounds.height();
                    if (windowW <= 0) windowW = bounds.width();
                } else {
                    Point size = new Point(0, 0);
                    if (wm.getDefaultDisplay() != null) {
                        wm.getDefaultDisplay().getSize(size);
                        if (windowH <= 0) windowH = size.y;
                        if (windowW <= 0) windowW = size.x;
                    }
                }
            }
        }

        if (windowH <= 0 || windowW <= 0) {
            boolean isLand = (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
            if (windowH <= 0) windowH = isLand ? 1080 : 2400;
            if (windowW <= 0) windowW = isLand ? 2400 : 1080;
        }

        int totalH = getVisibleKeyboardHeight();
        int topInsets = Math.max(0, windowH - totalH);
        outInsets.contentTopInsets = topInsets;
        outInsets.visibleTopInsets = topInsets;
        outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION;
        outInsets.touchableRegion.set(0, topInsets, windowW, windowH);
    }

    @Override
    public void onKeyboardLoaded(KeyboardType keyboardType) {
        Log.d(TAG, "onKeyboardLoaded: " + keyboardType);
        if (keyboardType == KeyboardType.KEYBOARD_TYPE_SYSTEM) {
            setupWebView(keymanView);
            KMManager.setBanner(KeyboardType.KEYBOARD_TYPE_SYSTEM, KMManager.BannerType.SUGGESTION);
            KMManager.showBanner(true);
            int kbHeight = KMManager.getKeyboardHeight(this);
            KMManager.applyKeyboardHeight(this, kbHeight);

            HashMap<String, String> lm = KMManager.getAssociatedLexicalModel("hmr");
            if (lm != null) {
                Log.d(TAG, "Registering lexical model in onKeyboardLoaded: " + lm);
                if (!lexicalModelRegistered) {
                    lexicalModelRegistered = true;
                    KMManager.registerLexicalModel(lm);
                } else {
                    Log.d(TAG, "Skipping duplicate registerLexicalModel (already registered once this session)");
                }
            }
        }
    }

    @Override
    public void onKeyboardChanged(String newKeyboard) {}

    @Override
    public void onKeyboardShown() {}

    @Override
    public void onKeyboardDismissed() {
        showEmojiPanel(false);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (emojiPanelView != null && emojiPanelView.getVisibility() == View.VISIBLE) {
                    showEmojiPanel(false);
                    return true;
                }
                if (isInputViewShown()) {
                    KMManager.hideSystemKeyboard();
                    return true;
                }
            }
        }
        return interpreter != null && interpreter.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return interpreter != null && interpreter.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
        return interpreter != null && interpreter.onKeyMultiple(keyCode, count, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        return interpreter != null && interpreter.onKeyLongPress(keyCode, event);
    }

    private void syncLatestKeyboardAssets() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("KMAPreferences", Context.MODE_PRIVATE);
            int lastSyncedVer = prefs.getInt("pref_synced_version_code", -1);
            int currentVer = BuildConfig.VERSION_CODE;
            boolean needsSync = (lastSyncedVer != currentVer);

            java.io.File pkgDir = new java.io.File(getDir("data", Context.MODE_PRIVATE), "packages/hmar_ish");
            if (!pkgDir.exists()) {
                pkgDir.mkdirs();
            }
            String[] filesToSync = {"hmar_ish.js", "hmar_ish.kmx", "hmar_ish.keyman-touch-layout"};
            for (String fname : filesToSync) {
                java.io.File dest = new java.io.File(pkgDir, fname);
                if (!needsSync && dest.exists() && dest.length() > 0) {
                    continue;
                }
                try (java.io.InputStream in = getAssets().open("packages/hmar_ish/" + fname);
                     java.io.OutputStream out = new java.io.FileOutputStream(dest)) {
                    byte[] buffer = new byte[32768];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Sync file " + fname + " failed: " + e.getMessage());
                }
            }

            java.io.File modelDir = new java.io.File(getDir("data", Context.MODE_PRIVATE), "models/hhf.hmr.ish");
            if (!modelDir.exists()) {
                modelDir.mkdirs();
            }
            java.io.File modelDest = new java.io.File(modelDir, "hhf.hmr.ish.model.js");
            if (needsSync || !modelDest.exists() || modelDest.length() == 0) {
                try (java.io.InputStream in = getAssets().open("models/hhf.hmr.ish/hhf.hmr.ish.model.js");
                     java.io.OutputStream out = new java.io.FileOutputStream(modelDest)) {
                    byte[] buffer = new byte[32768];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Sync model failed: " + e.getMessage());
                }
            }
            java.io.File hostDest = new java.io.File(getDir("data", Context.MODE_PRIVATE), "android-host.js");
            if (needsSync || !hostDest.exists() || hostDest.length() == 0) {
                try (java.io.InputStream in = getAssets().open("android-host.js");
                     java.io.OutputStream out = new java.io.FileOutputStream(hostDest)) {
                    byte[] buffer = new byte[32768];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Sync android-host.js failed: " + e.getMessage());
                }
            }

            prefs.edit().putInt("pref_synced_version_code", currentVer).apply();
            Log.i(TAG, "Keyboard assets sync check completed (needsSync=" + needsSync + ")");
        } catch (Exception e) {
            Log.e(TAG, "Error syncing keyboard assets: " + e.getMessage());
        }
    }
}
