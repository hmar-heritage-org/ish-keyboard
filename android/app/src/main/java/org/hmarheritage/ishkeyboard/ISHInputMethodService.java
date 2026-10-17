package org.hmarheritage.ishkeyboard;

import com.keyman.engine.data.Keyboard;
import com.keyman.engine.KMManager;
import com.keyman.engine.KMManager.KeyboardType;
import com.keyman.engine.KMHardwareKeyboardInterpreter;
import com.keyman.engine.KeyboardEventHandler.OnKeyboardEventListener;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.inputmethodservice.InputMethodService;
import android.util.Log;
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
import android.widget.FrameLayout;

import java.util.HashMap;

public class ISHInputMethodService extends InputMethodService implements OnKeyboardEventListener {

    private static final String TAG = "ISHKeyboard";
    private View inputView = null;
    private KMHardwareKeyboardInterpreter interpreter = null;

    @Override
    public void onCreate() {
        super.onCreate();
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
            "1.1.1",        // Keyboard Version
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
    }

    private void setupWebView(View view) {
        if (view == null) return;
        if (view instanceof WebView) {
            WebView wv = (WebView) view;
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
                            byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
                            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                        }
                    } catch (Throwable e) {
                        Log.e(TAG, "Failed to read model code: " + e);
                    }
                    return null;
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

    @Override
    public void onDestroy() {
        inputView = null;
        KMManager.removeKeyboardEventListener(this);
        interpreter = null;
        KMManager.onDestroy();
        super.onDestroy();
    }

    @Override
    public View onCreateInputView() {
        if (inputView == null) {
            inputView = KMManager.createInputView(this);
        }
        setupWebView(inputView);
        KMManager.setBanner(KeyboardType.KEYBOARD_TYPE_SYSTEM, KMManager.BannerType.SUGGESTION);
        KMManager.showBanner(true);
        int kbHeight = KMManager.getKeyboardHeight(this);
        KMManager.applyKeyboardHeight(this, kbHeight);

        ViewGroup parent = (ViewGroup) inputView.getParent();
        if (parent != null) {
            parent.removeView(inputView);
        }
        return inputView;
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        KMManager.updateSelectionRange(KeyboardType.KEYBOARD_TYPE_SYSTEM, newSelStart, newSelEnd);
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
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
            if (lm != null) {
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
        setInputView(onCreateInputView());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        KMManager.onConfigurationChanged(newConfig);
    }

    @Override
    public void onConfigureWindow(Window win, boolean isFullscreen, boolean isCandidatesOnly) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly);
        win.setLayout(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
    }

    @Override
    public void onComputeInsets(InputMethodService.Insets outInsets) {
        super.onComputeInsets(outInsets);
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Point size = new Point(0, 0);
        if (wm != null && wm.getDefaultDisplay() != null) {
            wm.getDefaultDisplay().getSize(size);
        }

        int inputViewHeight = 0;
        if (inputView != null) {
            inputViewHeight = inputView.getHeight();
        }

        int bannerHeight = KMManager.getBannerHeight(this);
        int kbHeight = KMManager.getKeyboardHeight(this);
        outInsets.contentTopInsets = inputViewHeight - bannerHeight - kbHeight;
        outInsets.visibleTopInsets = outInsets.contentTopInsets;
        outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION;
        outInsets.touchableRegion.set(0, outInsets.contentTopInsets, size.x, size.y);
    }

    @Override
    public void onKeyboardLoaded(KeyboardType keyboardType) {
        Log.d(TAG, "onKeyboardLoaded: " + keyboardType);
        if (keyboardType == KeyboardType.KEYBOARD_TYPE_SYSTEM) {
            setupWebView(inputView);
            KMManager.setBanner(KeyboardType.KEYBOARD_TYPE_SYSTEM, KMManager.BannerType.SUGGESTION);
            KMManager.showBanner(true);
            int kbHeight = KMManager.getKeyboardHeight(this);
            KMManager.applyKeyboardHeight(this, kbHeight);

            HashMap<String, String> lm = KMManager.getAssociatedLexicalModel("hmr");
            if (lm != null) {
                Log.d(TAG, "Registering lexical model in onKeyboardLoaded: " + lm);
                KMManager.registerLexicalModel(lm);
            }
        }
    }

    @Override
    public void onKeyboardChanged(String newKeyboard) {}

    @Override
    public void onKeyboardShown() {}

    @Override
    public void onKeyboardDismissed() {}

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
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
            java.io.File pkgDir = new java.io.File(getDir("data", Context.MODE_PRIVATE), "packages/hmar_ish");
            if (!pkgDir.exists()) {
                pkgDir.mkdirs();
            }
            String[] filesToSync = {"hmar_ish.js", "hmar_ish.kmx", "hmar_ish.keyman-touch-layout"};
            for (String fname : filesToSync) {
                java.io.File dest = new java.io.File(pkgDir, fname);
                try (java.io.InputStream in = getAssets().open("packages/hmar_ish/" + fname);
                     java.io.OutputStream out = new java.io.FileOutputStream(dest)) {
                    byte[] buffer = new byte[8192];
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
            try (java.io.InputStream in = getAssets().open("models/hhf.hmr.ish/hhf.hmr.ish.model.js");
                 java.io.OutputStream out = new java.io.FileOutputStream(modelDest)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            } catch (Exception e) {
                Log.w(TAG, "Sync model failed: " + e.getMessage());
            }
            java.io.File hostDest = new java.io.File(getDir("data", Context.MODE_PRIVATE), "android-host.js");
            try (java.io.InputStream in = getAssets().open("android-host.js");
                 java.io.OutputStream out = new java.io.FileOutputStream(hostDest)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            } catch (Exception e) {
                Log.w(TAG, "Sync android-host.js failed: " + e.getMessage());
            }

            Log.i(TAG, "Successfully synced latest keyboard and model assets to app_data!");
        } catch (Exception e) {
            Log.e(TAG, "Error syncing keyboard assets: " + e.getMessage());
        }
    }
}
