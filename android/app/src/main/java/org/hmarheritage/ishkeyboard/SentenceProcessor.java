package org.hmarheritage.ishkeyboard;

import android.content.Context;
import android.text.InputType;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import java.util.regex.Pattern;

public class SentenceProcessor {
    private static final String TAG = "SentenceProcessor";

    // OTP & Financial sensitive patterns (Never record)
    private static final Pattern OTP_PATTERN = Pattern.compile("(?i)\\b(otp|c-?otp|cvv|pin|passcode|verification code)\\b");
    private static final Pattern STANDALONE_NUMBER_CODE = Pattern.compile("^\\s*\\d{4,8}\\s*$");
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("\\b(?:\\d[ -]*?){13,19}\\b");

    private final Context context;
    private final UserCorpusDatabase corpusDb;
    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.Set<String> recordedSentenceHashes = java.util.Collections.synchronizedSet(
        java.util.Collections.newSetFromMap(
            new java.util.LinkedHashMap<String, Boolean>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Boolean> eldest) {
                    return size() > 256;
                }
            }
        )
    );

    public SentenceProcessor(Context context) {
        this.context = context.getApplicationContext();
        this.corpusDb = UserCorpusDatabase.getInstance(this.context);
    }

    /**
     * Checks if the current text field is safe to learn from.
     * Passwords, PINs, and incognito fields are 100% ignored.
     */
    public boolean isSafeField(EditorInfo editorInfo) {
        if (editorInfo == null) return false;

        int inputType = editorInfo.inputType;
        int classType = inputType & InputType.TYPE_MASK_CLASS;
        int variation = inputType & InputType.TYPE_MASK_VARIATION;

        // Skip numeric/phone/datetime fields
        if (classType == InputType.TYPE_CLASS_NUMBER ||
            classType == InputType.TYPE_CLASS_PHONE ||
            classType == InputType.TYPE_CLASS_DATETIME) {
            return false;
        }

        // Skip password fields
        if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) {
            return false;
        }

        // Check incognito / private browsing flag
        if ((editorInfo.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) {
            return false;
        }

        return true;
    }

    /**
     * Inspects text before the cursor and extracts complete sentences when a sentence
     * boundary (. ? ! or newline) is detected, or on field completion.
     */
    public void processBuffer(String fullTextBeforeCursor, EditorInfo editorInfo) {
        if (!isSafeField(editorInfo) || fullTextBeforeCursor == null) return;

        String text = fullTextBeforeCursor.trim();
        if (text.isEmpty()) return;

        // Split by standard sentence delimiters (. ! ? or newline)
        String[] parts = text.split("(?<=[.!?\\n])\\s+");
        if (parts.length == 0) return;

        // The complete sentences are all parts except possibly the last one (if it's still being typed)
        for (int i = 0; i < parts.length; i++) {
            String candidate = parts[i].trim();
            boolean isTerminated = candidate.endsWith(".") || candidate.endsWith("?") || 
                                   candidate.endsWith("!") || candidate.endsWith("\n");

            // Only process if it is a completed sentence boundary
            if (isTerminated) {
                evaluateAndRecord(candidate);
            }
        }
    }

    /**
     * Resets the session cache when switching to a new text field.
     */
    public void resetSession() {
        recordedSentenceHashes.clear();
    }

    /**
     * When user sends, hits enter, or finishes editing the field.
     */
    public void onFieldFinished(String fullText, EditorInfo editorInfo) {
        if (!isSafeField(editorInfo) || fullText == null) return;
        String text = fullText.trim();
        if (text.isEmpty()) return;

        // Process all completed sentences first
        processBuffer(text, editorInfo);

        // Check if there is an uncompleted trailing fragment (e.g. sent without terminal punctuation)
        String[] parts = text.split("(?<=[.!?\\n])\\s+");
        if (parts.length > 0) {
            String lastPart = parts[parts.length - 1].trim();
            boolean isTerminated = lastPart.endsWith(".") || lastPart.endsWith("?") || 
                                   lastPart.endsWith("!") || lastPart.endsWith("\n");
            if (!isTerminated && !lastPart.isEmpty()) {
                evaluateAndRecord(lastPart);
            }
        }
    }

    private void evaluateAndRecord(String rawSentence) {
        if (rawSentence == null || rawSentence.isEmpty()) return;
        String sentence = rawSentence.trim();

        // 1. Avoid duplicate recordings in the active session
        if (!recordedSentenceHashes.add(sentence)) {
            return;
        }

        // 2. Strict OTP / Card / Banking check
        if (STANDALONE_NUMBER_CODE.matcher(sentence).matches() ||
            OTP_PATTERN.matcher(sentence).find() ||
            CARD_NUMBER_PATTERN.matcher(sentence).find()) {
            Log.d(TAG, "Dropped sensitive / OTP candidate.");
            return;
        }

        // 3. Process any valid tokens (even single-word inputs)
        String[] tokens = sentence.split("\\s+");
        if (tokens.length < 1) {
            return;
        }

        // 4. Save to persistent SQLite corpus & user dictionary
        executor.execute(() -> {
            try {
                corpusDb.recordSentence(sentence);
            } catch (Throwable t) {
                Log.e(TAG, "Error saving sentence to corpus: " + t);
            }
        });
    }
}
