package org.hmarheritage.ishkeyboard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class UserCorpusDatabase extends SQLiteOpenHelper {
    private static final String TAG = "UserCorpusDatabase";
    private static final String DATABASE_NAME = "ish_user_corpus.db";
    private static final int DATABASE_VERSION = 5;

    // Table: user_sentences (Full sentence AI training corpus)
    public static final String TABLE_SENTENCES = "user_sentences";
    public static final String COL_SENTENCE_ID = "id";
    public static final String COL_SENTENCE_TEXT = "sentence";
    public static final String COL_SENTENCE_WORD_COUNT = "word_count";
    public static final String COL_SENTENCE_TIMESTAMP = "created_at";
    public static final String COL_SENTENCE_UPLOADED = "is_uploaded";

    // Table: user_unigrams (Personal vocabulary, names & hyphenated compounds)
    public static final String TABLE_UNIGRAMS = "user_unigrams";
    public static final String COL_UNIGRAM_WORD = "word";
    public static final String COL_UNIGRAM_DISPLAY = "display_word";
    public static final String COL_UNIGRAM_FREQ = "frequency";
    public static final String COL_UNIGRAM_IS_USER_ADDED = "is_user_added";
    public static final String COL_UNIGRAM_SUGGEST_REPLACEMENT = "suggest_replacement";
    public static final String COL_UNIGRAM_LAST_USED = "last_used";

    // Table: user_bigrams (Personalized word-order transitions)
    public static final String TABLE_BIGRAMS = "user_bigrams";
    public static final String COL_BIGRAM_PREV = "prev_word";
    public static final String COL_BIGRAM_NEXT = "next_word";
    public static final String COL_BIGRAM_FREQ = "frequency";
    public static final String COL_BIGRAM_LAST_USED = "last_used";

    // Table: ish_dictionary (Permanent official 54,000+ ISH vocabulary baseline)
    public static final String TABLE_ISH_DICT = "ish_dictionary";
    public static final String COL_ISH_WORD = "word";
    public static final String COL_ISH_DISPLAY = "display_word";
    public static final String COL_ISH_BASE_FREQ = "base_frequency";
    public static final String COL_ISH_EXAMPLE = "example_sentence";
    public static final String COL_ISH_SUGGEST_REPLACEMENT = "suggest_replacement";
    public static final String COL_ISH_IS_DELETED = "is_deleted";

    // Table: ish_examples (Multiple verified example sentences per word)
    public static final String TABLE_EXAMPLES = "ish_examples";
    public static final String COL_EXAMPLE_ID = "id";
    public static final String COL_EXAMPLE_WORD = "word";
    public static final String COL_EXAMPLE_SENTENCE = "example_sentence";
    public static final String COL_EXAMPLE_CREATED_AT = "created_at";

    // Constraints to keep DB lean & fast
    private static final int MAX_SENTENCES = 10000;
    private static final int MAX_UNIGRAMS = 5000;
    private static final int MAX_BIGRAMS = 10000;

    public static class PersonalWord {
        public final String word;
        public final String displayWord;
        public final int frequency;
        public final int baseFrequency;
        public final boolean isIshStandard;
        public final boolean isUserAdded;
        public final long lastUsed;
        public final String standardExample;
        public final String suggestReplacement;
        public final boolean isDeleted;
        public boolean isExactReplacement = false;
        public boolean isLiteral = false;

        public PersonalWord(String word, String displayWord, int frequency, boolean isUserAdded, long lastUsed) {
            this(word, displayWord, frequency, 0, false, isUserAdded, lastUsed, null, null, false);
        }

        public PersonalWord(String word, String displayWord, int frequency, int baseFrequency, boolean isIshStandard, boolean isUserAdded, long lastUsed, String standardExample) {
            this(word, displayWord, frequency, baseFrequency, isIshStandard, isUserAdded, lastUsed, standardExample, null, false);
        }

        public PersonalWord(String word, String displayWord, int frequency, int baseFrequency, boolean isIshStandard, boolean isUserAdded, long lastUsed, String standardExample, String suggestReplacement, boolean isDeleted) {
            this.word = word;
            this.displayWord = (displayWord != null && !displayWord.isEmpty()) ? displayWord : word;
            this.frequency = frequency;
            this.baseFrequency = baseFrequency;
            this.isIshStandard = isIshStandard;
            this.isUserAdded = isUserAdded;
            this.lastUsed = lastUsed;
            this.standardExample = standardExample;
            this.suggestReplacement = suggestReplacement;
            this.isDeleted = isDeleted;
        }
    }

    public static class WordExample {
        public final int id;
        public final String word;
        public final String exampleSentence;
        public final long createdAt;

        public WordExample(int id, String word, String exampleSentence, long createdAt) {
            this.id = id;
            this.word = word;
            this.exampleSentence = exampleSentence;
            this.createdAt = createdAt;
        }
    }

    public static class ConcordanceEntry {
        public final String sentence;
        public final long timestamp;

        public ConcordanceEntry(String sentence, long timestamp) {
            this.sentence = sentence;
            this.timestamp = timestamp;
        }
    }

    private static UserCorpusDatabase instance;
    private final Context appContext;

    public static synchronized UserCorpusDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new UserCorpusDatabase(context.getApplicationContext());
        }
        return instance;
    }

    private UserCorpusDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.appContext = context;
        try {
            setWriteAheadLoggingEnabled(true);
        } catch (Exception e) {
            Log.w(TAG, "Failed to enable write ahead logging: " + e);
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        try {
            db.enableWriteAheadLogging();
        } catch (Exception e) {
            Log.w(TAG, "Failed to enable WAL in onConfigure: " + e);
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_SENTENCES + " ("
                + COL_SENTENCE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_SENTENCE_TEXT + " TEXT NOT NULL UNIQUE, "
                + COL_SENTENCE_WORD_COUNT + " INTEGER NOT NULL, "
                + COL_SENTENCE_TIMESTAMP + " INTEGER NOT NULL, "
                + COL_SENTENCE_UPLOADED + " INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_UNIGRAMS + " ("
                + COL_UNIGRAM_WORD + " TEXT PRIMARY KEY, "
                + COL_UNIGRAM_DISPLAY + " TEXT, "
                + COL_UNIGRAM_FREQ + " INTEGER DEFAULT 1, "
                + COL_UNIGRAM_IS_USER_ADDED + " INTEGER DEFAULT 0, "
                + COL_UNIGRAM_SUGGEST_REPLACEMENT + " TEXT, "
                + COL_UNIGRAM_LAST_USED + " INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_BIGRAMS + " ("
                + COL_BIGRAM_PREV + " TEXT NOT NULL, "
                + COL_BIGRAM_NEXT + " TEXT NOT NULL, "
                + COL_BIGRAM_FREQ + " INTEGER DEFAULT 1, "
                + COL_BIGRAM_LAST_USED + " INTEGER NOT NULL, "
                + "PRIMARY KEY (" + COL_BIGRAM_PREV + ", " + COL_BIGRAM_NEXT + "))");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ISH_DICT + " ("
                + COL_ISH_WORD + " TEXT PRIMARY KEY, "
                + COL_ISH_DISPLAY + " TEXT, "
                + COL_ISH_BASE_FREQ + " INTEGER DEFAULT 1, "
                + COL_ISH_EXAMPLE + " TEXT, "
                + COL_ISH_SUGGEST_REPLACEMENT + " TEXT, "
                + COL_ISH_IS_DELETED + " INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_EXAMPLES + " ("
                + COL_EXAMPLE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_EXAMPLE_WORD + " TEXT NOT NULL, "
                + COL_EXAMPLE_SENTENCE + " TEXT NOT NULL, "
                + COL_EXAMPLE_CREATED_AT + " INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_examples_word ON " + TABLE_EXAMPLES + " (" + COL_EXAMPLE_WORD + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ish_replacement ON " + TABLE_ISH_DICT + " (" + COL_ISH_SUGGEST_REPLACEMENT + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_unigrams_replacement ON " + TABLE_UNIGRAMS + " (" + COL_UNIGRAM_SUGGEST_REPLACEMENT + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_UNIGRAMS + " ADD COLUMN " + COL_UNIGRAM_DISPLAY + " TEXT");
                db.execSQL("ALTER TABLE " + TABLE_UNIGRAMS + " ADD COLUMN " + COL_UNIGRAM_IS_USER_ADDED + " INTEGER DEFAULT 0");
                db.execSQL("UPDATE " + TABLE_UNIGRAMS + " SET " + COL_UNIGRAM_DISPLAY + " = " + COL_UNIGRAM_WORD);
            } catch (Exception e) {
                Log.w(TAG, "Migration error to v2: " + e);
            }
        }
        if (oldVersion < 3) {
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ISH_DICT + " ("
                        + COL_ISH_WORD + " TEXT PRIMARY KEY, "
                        + COL_ISH_DISPLAY + " TEXT, "
                        + COL_ISH_BASE_FREQ + " INTEGER DEFAULT 1, "
                        + COL_ISH_EXAMPLE + " TEXT, "
                        + COL_ISH_SUGGEST_REPLACEMENT + " TEXT, "
                        + COL_ISH_IS_DELETED + " INTEGER DEFAULT 0)");

                db.execSQL("DELETE FROM " + TABLE_SENTENCES);
                db.execSQL("DELETE FROM " + TABLE_UNIGRAMS);
                db.execSQL("DELETE FROM " + TABLE_BIGRAMS);
            } catch (Exception e) {
                Log.w(TAG, "Migration error to v3: " + e);
            }
        }
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_ISH_DICT + " ADD COLUMN " + COL_ISH_SUGGEST_REPLACEMENT + " TEXT");
            } catch (Exception ignored) {}
            try {
                db.execSQL("ALTER TABLE " + TABLE_ISH_DICT + " ADD COLUMN " + COL_ISH_IS_DELETED + " INTEGER DEFAULT 0");
            } catch (Exception ignored) {}
            try {
                db.execSQL("ALTER TABLE " + TABLE_UNIGRAMS + " ADD COLUMN " + COL_UNIGRAM_SUGGEST_REPLACEMENT + " TEXT");
            } catch (Exception ignored) {}
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_EXAMPLES + " ("
                        + COL_EXAMPLE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + COL_EXAMPLE_WORD + " TEXT NOT NULL, "
                        + COL_EXAMPLE_SENTENCE + " TEXT NOT NULL, "
                        + COL_EXAMPLE_CREATED_AT + " INTEGER NOT NULL)");
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_examples_word ON " + TABLE_EXAMPLES + " (" + COL_EXAMPLE_WORD + ")");
            } catch (Exception ignored) {}
        }
        if (oldVersion < 5) {
            try {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_ish_replacement ON " + TABLE_ISH_DICT + " (" + COL_ISH_SUGGEST_REPLACEMENT + ")");
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_unigrams_replacement ON " + TABLE_UNIGRAMS + " (" + COL_UNIGRAM_SUGGEST_REPLACEMENT + ")");
            } catch (Exception ignored) {}
        }
    }

    private final AtomicBoolean isSeeding = new AtomicBoolean(false);

    /**
     * Seeds the official 54,000-word ISH dictionary from assets/models/wordlist.tsv
     * if the ish_dictionary table is empty.
     * Batched in chunks to keep DB responsive and avoid holding exclusive locks or monitor locks.
     */
    public void seedIshDictionaryIfNeeded() {
        if (!isSeeding.compareAndSet(false, true)) {
            Log.d(TAG, "ISH dictionary seeding already in progress. Skipping duplicate invocation.");
            return;
        }

        try {
            SQLiteDatabase db = getWritableDatabase();
            Cursor c = null;
            try {
                c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_ISH_DICT, null);
                if (c != null && c.moveToFirst() && c.getInt(0) >= 50000) {
                    return; // Already populated
                }
            } catch (Exception e) {
                Log.w(TAG, "Error checking ish_dictionary count: " + e);
            } finally {
                if (c != null) c.close();
            }

            Log.i(TAG, "Seeding ISH dictionary from assets/models/wordlist.tsv in chunks...");
            long start = System.currentTimeMillis();

            String sql = "INSERT OR IGNORE INTO " + TABLE_ISH_DICT + " ("
                    + COL_ISH_WORD + ", " + COL_ISH_DISPLAY + ", " + COL_ISH_BASE_FREQ + ", " + COL_ISH_EXAMPLE + ", " + COL_ISH_SUGGEST_REPLACEMENT + ", " + COL_ISH_IS_DELETED + ") VALUES (?, ?, ?, NULL, NULL, 0)";

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(appContext.getAssets().open("models/wordlist.tsv"), StandardCharsets.UTF_8))) {
                SQLiteStatement stmt = db.compileStatement(sql);
                final int BATCH_SIZE = 2500;
                int count = 0;
                String line;

                db.beginTransaction();
                try {
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) continue;
                        String[] parts = line.split("\t");
                        if (parts.length >= 1) {
                            String rawWord = parts[0].trim();
                            if (rawWord.isEmpty()) continue;
                            int freq = 1;
                            if (parts.length >= 2) {
                                try {
                                    freq = Integer.parseInt(parts[1].trim());
                                } catch (NumberFormatException ignored) {}
                            }
                            stmt.bindString(1, rawWord.toLowerCase());
                            stmt.bindString(2, rawWord);
                            stmt.bindLong(3, freq);
                            stmt.executeInsert();
                            stmt.clearBindings();
                            count++;

                            if (count % BATCH_SIZE == 0) {
                                db.setTransactionSuccessful();
                                db.endTransaction();
                                Thread.yield();
                                db.beginTransaction();
                            }
                        }
                    }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
                Log.i(TAG, "Seeded " + count + " words in ISH dictionary in " + (System.currentTimeMillis() - start) + "ms");
            } catch (Exception e) {
                Log.e(TAG, "Failed seeding ISH dictionary: " + e);
            }
        } finally {
            isSeeding.set(false);
        }
    }

    /**
     * Inserts a complete sentence into the corpus and updates unigrams and bigrams.
     * Records what the user typed verbatim.
     */
    public synchronized void recordSentence(String rawSentence) {
        if (rawSentence == null) return;
        String sentence = rawSentence.trim();
        if (sentence.isEmpty()) return;

        String[] tokens = sentence.split("\\s+");
        if (tokens.length == 0) {
            return;
        }

        long now = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();

        db.beginTransaction();
        try {
            // 1. Insert full sentence if it has at least 2 words (phrase / sentence preservation)
            if (tokens.length >= 2) {
                ContentValues cvSentence = new ContentValues();
                cvSentence.put(COL_SENTENCE_TEXT, sentence);
                cvSentence.put(COL_SENTENCE_WORD_COUNT, tokens.length);
                cvSentence.put(COL_SENTENCE_TIMESTAMP, now);
                cvSentence.put(COL_SENTENCE_UPLOADED, 0);
                db.insertWithOnConflict(TABLE_SENTENCES, null, cvSentence, SQLiteDatabase.CONFLICT_IGNORE);
            }

            // 2. Process Unigrams (all tokens >= 1) and Bigrams (word transitions)
            String prevWord = null;
            for (String rawToken : tokens) {
                String cleaned = rawToken.replaceAll("^[^a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+|[^a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+$", "");
                if (cleaned.isEmpty()) continue;
                if (!cleaned.matches("^[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+(-[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+)*$")) {
                    continue;
                }

                String displayWord = cleaned;
                String word = cleaned.toLowerCase();

                db.execSQL("INSERT INTO " + TABLE_UNIGRAMS + " ("
                        + COL_UNIGRAM_WORD + ", " + COL_UNIGRAM_DISPLAY + ", " + COL_UNIGRAM_FREQ + ", " + COL_UNIGRAM_IS_USER_ADDED + ", " + COL_UNIGRAM_LAST_USED + ") "
                        + "VALUES (?, ?, 1, 0, ?) "
                        + "ON CONFLICT(" + COL_UNIGRAM_WORD + ") DO UPDATE SET "
                        + COL_UNIGRAM_FREQ + " = " + COL_UNIGRAM_FREQ + " + 1, "
                        + COL_UNIGRAM_LAST_USED + " = ?", new Object[]{word, displayWord, now, now});

                if (prevWord != null) {
                    db.execSQL("INSERT INTO " + TABLE_BIGRAMS + " (" + COL_BIGRAM_PREV + ", " + COL_BIGRAM_NEXT + ", " + COL_BIGRAM_FREQ + ", " + COL_BIGRAM_LAST_USED + ") "
                            + "VALUES (?, ?, 1, ?) "
                            + "ON CONFLICT(" + COL_BIGRAM_PREV + ", " + COL_BIGRAM_NEXT + ") DO UPDATE SET "
                            + COL_BIGRAM_FREQ + " = " + COL_BIGRAM_FREQ + " + 1, "
                            + COL_BIGRAM_LAST_USED + " = ?", new Object[]{prevWord, word, now, now});
                }
                prevWord = word;
            }

            db.setTransactionSuccessful();
            Log.d(TAG, "Recorded sentence (" + tokens.length + " words): " + sentence);
        } catch (Exception e) {
            Log.e(TAG, "Error recording sentence: " + e);
        } finally {
            db.endTransaction();
        }

        pruneIfExceedsBudget(db);
    }

    /**
     * Explicitly adds or edits a user word (e.g. from the Personal Dictionary UI).
     */
    public synchronized boolean addOrUpdateUserWord(String word, int frequency, boolean isUserAdded) {
        return addOrUpdateUserWord(word, frequency, isUserAdded, null);
    }

    public synchronized boolean addOrUpdateUserWord(String word, int frequency, boolean isUserAdded, String suggestReplacement) {
        if (word == null) return false;
        String cleaned = word.trim().replaceAll("^[^a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+|[^a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+$", "");
        if (cleaned.isEmpty() || !cleaned.matches("^[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+(-[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+)*$")) {
            return false;
        }

        String displayWord = cleaned;
        String normalized = cleaned.toLowerCase();
        long now = System.currentTimeMillis();
        int safeFreq = Math.max(1, frequency);

        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_UNIGRAM_WORD, normalized);
        cv.put(COL_UNIGRAM_DISPLAY, displayWord);
        cv.put(COL_UNIGRAM_FREQ, safeFreq);
        cv.put(COL_UNIGRAM_IS_USER_ADDED, isUserAdded ? 1 : 0);
        cv.put(COL_UNIGRAM_SUGGEST_REPLACEMENT, suggestReplacement != null && !suggestReplacement.trim().isEmpty() ? suggestReplacement.trim() : null);
        cv.put(COL_UNIGRAM_LAST_USED, now);

        long res = db.insertWithOnConflict(TABLE_UNIGRAMS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        return res != -1;
    }

    /**
     * Retrieves full details for a single word.
     */
    public synchronized PersonalWord getWordDetails(String queryWord) {
        if (queryWord == null) return null;
        String norm = queryWord.trim().toLowerCase();
        SQLiteDatabase db = getReadableDatabase();

        String sql = "SELECT i.word, i.display_word, COALESCE(u.frequency, 0), i.base_frequency, "
                + "1 AS is_ish, COALESCE(u.is_user_added, 0), COALESCE(u.last_used, 0), "
                + "i.example_sentence, COALESCE(u.suggest_replacement, i.suggest_replacement), i.is_deleted "
                + "FROM " + TABLE_ISH_DICT + " i "
                + "LEFT JOIN " + TABLE_UNIGRAMS + " u ON i.word = u.word "
                + "WHERE LOWER(i.word) = ?";

        try (Cursor c = db.rawQuery(sql, new String[]{norm})) {
            if (c != null && c.moveToFirst()) {
                return new PersonalWord(
                        c.getString(0), c.getString(1), c.getInt(2), c.getInt(3),
                        c.getInt(4) == 1, c.getInt(5) == 1, c.getLong(6),
                        c.getString(7), c.getString(8), c.getInt(9) == 1
                );
            }
        }

        // Custom word only in user_unigrams
        String sqlUser = "SELECT word, display_word, frequency, 0, 0, is_user_added, last_used, NULL, suggest_replacement, 0 "
                + "FROM " + TABLE_UNIGRAMS + " WHERE LOWER(word) = ?";
        try (Cursor cu = db.rawQuery(sqlUser, new String[]{norm})) {
            if (cu != null && cu.moveToFirst()) {
                return new PersonalWord(
                        cu.getString(0), cu.getString(1), cu.getInt(2), cu.getInt(3),
                        cu.getInt(4) == 1, cu.getInt(5) == 1, cu.getLong(6),
                        cu.getString(7), cu.getString(8), cu.getInt(9) == 1
                );
            }
        }
        return null;
    }

    /**
     * Updates an existing word entry with new text, frequency, and optional suggest-instead replacement.
     */
    public synchronized boolean updateWordDetails(String oldWord, String newWord, int newFreq, String suggestReplacement) {
        if (oldWord == null || newWord == null) return false;
        String oldNorm = oldWord.trim().toLowerCase();
        String cleanedNew = newWord.trim().replaceAll("^[^a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+|[^a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+$", "");
        if (cleanedNew.isEmpty() || !cleanedNew.matches("^[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+(-[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+)*$")) {
            return false;
        }

        String newNorm = cleanedNew.toLowerCase();
        String replacement = (suggestReplacement != null && !suggestReplacement.trim().isEmpty()) ? suggestReplacement.trim() : null;
        long now = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();

        db.beginTransaction();
        try {
            // Update or move in user_unigrams
            if (!oldNorm.equals(newNorm)) {
                db.delete(TABLE_UNIGRAMS, COL_UNIGRAM_WORD + " = ?", new String[]{oldNorm});
            }
            ContentValues cvUser = new ContentValues();
            cvUser.put(COL_UNIGRAM_WORD, newNorm);
            cvUser.put(COL_UNIGRAM_DISPLAY, cleanedNew);
            cvUser.put(COL_UNIGRAM_FREQ, Math.max(1, newFreq));
            cvUser.put(COL_UNIGRAM_IS_USER_ADDED, 1);
            cvUser.put(COL_UNIGRAM_SUGGEST_REPLACEMENT, replacement);
            cvUser.put(COL_UNIGRAM_LAST_USED, now);
            db.insertWithOnConflict(TABLE_UNIGRAMS, null, cvUser, SQLiteDatabase.CONFLICT_REPLACE);

            // Update in ish_dictionary if present
            ContentValues cvIsh = new ContentValues();
            cvIsh.put(COL_ISH_DISPLAY, cleanedNew);
            cvIsh.put(COL_ISH_SUGGEST_REPLACEMENT, replacement);
            cvIsh.put(COL_ISH_IS_DELETED, 0);
            db.update(TABLE_ISH_DICT, cvIsh, COL_ISH_WORD + " = ?", new String[]{oldNorm});

            // Update attached examples word reference if spelling changed
            if (!oldNorm.equals(newNorm)) {
                ContentValues cvEx = new ContentValues();
                cvEx.put(COL_EXAMPLE_WORD, newNorm);
                db.update(TABLE_EXAMPLES, cvEx, COL_EXAMPLE_WORD + " = ?", new String[]{oldNorm});
            }

            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "updateWordDetails error: " + e);
            return false;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Marks a word deleted. If it's an official ISH word, flags is_deleted=1 so it is excluded from
     * UI and suggestions. If it's a custom word, removes from unigrams/bigrams.
     */
    public synchronized boolean markWordDeleted(String word) {
        if (word == null) return false;
        String normalized = word.trim().toLowerCase();
        SQLiteDatabase db = getWritableDatabase();

        db.beginTransaction();
        try {
            // 1. Remove from user personal usage
            db.delete(TABLE_UNIGRAMS, COL_UNIGRAM_WORD + " = ?", new String[]{normalized});
            db.delete(TABLE_BIGRAMS, COL_BIGRAM_PREV + " = ? OR " + COL_BIGRAM_NEXT + " = ?", new String[]{normalized, normalized});

            // 2. In ISH dictionary, mark is_deleted = 1
            ContentValues cv = new ContentValues();
            cv.put(COL_ISH_IS_DELETED, 1);
            db.update(TABLE_ISH_DICT, cv, COL_ISH_WORD + " = ?", new String[]{normalized});

            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "markWordDeleted error: " + e);
            return false;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Marks multiple words deleted in a single atomic transaction.
     */
    public synchronized int markWordsDeleted(java.util.Collection<String> words) {
        if (words == null || words.isEmpty()) return 0;
        SQLiteDatabase db = getWritableDatabase();
        int count = 0;
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put(COL_ISH_IS_DELETED, 1);
            for (String word : words) {
                if (word == null) continue;
                String normalized = word.trim().toLowerCase();
                db.delete(TABLE_UNIGRAMS, COL_UNIGRAM_WORD + " = ?", new String[]{normalized});
                db.delete(TABLE_BIGRAMS, COL_BIGRAM_PREV + " = ? OR " + COL_BIGRAM_NEXT + " = ?", new String[]{normalized, normalized});
                db.update(TABLE_ISH_DICT, cv, COL_ISH_WORD + " = ?", new String[]{normalized});
                count++;
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "markWordsDeleted error: " + e);
        } finally {
            db.endTransaction();
        }
        return count;
    }

    /**
     * Restores an official word that was previously marked deleted.
     */
    public synchronized boolean restoreWord(String word) {
        if (word == null) return false;
        String normalized = word.trim().toLowerCase();
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_ISH_IS_DELETED, 0);
        return db.update(TABLE_ISH_DICT, cv, COL_ISH_WORD + " = ?", new String[]{normalized}) > 0;
    }

    /**
     * Examples Management: get, add, update, delete individually.
     */
    public synchronized List<WordExample> getExamplesForWord(String word) {
        List<WordExample> list = new ArrayList<>();
        if (word == null) return list;
        String norm = word.trim().toLowerCase();
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor c = db.rawQuery("SELECT " + COL_EXAMPLE_ID + ", " + COL_EXAMPLE_WORD + ", " + COL_EXAMPLE_SENTENCE + ", " + COL_EXAMPLE_CREATED_AT
                + " FROM " + TABLE_EXAMPLES + " WHERE " + COL_EXAMPLE_WORD + " = ? ORDER BY " + COL_EXAMPLE_ID + " ASC", new String[]{norm})) {
            if (c != null) {
                while (c.moveToNext()) {
                    list.add(new WordExample(c.getInt(0), c.getString(1), c.getString(2), c.getLong(3)));
                }
            }
        }
        return list;
    }

    public synchronized boolean addExampleForWord(String word, String exampleSentence) {
        if (word == null || exampleSentence == null || exampleSentence.trim().isEmpty()) return false;
        String norm = word.trim().toLowerCase();
        long now = System.currentTimeMillis();

        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_EXAMPLE_WORD, norm);
        cv.put(COL_EXAMPLE_SENTENCE, exampleSentence.trim());
        cv.put(COL_EXAMPLE_CREATED_AT, now);

        long res = db.insert(TABLE_EXAMPLES, null, cv);
        return res != -1;
    }

    public synchronized boolean updateExample(int exampleId, String newSentence) {
        if (exampleId <= 0 || newSentence == null || newSentence.trim().isEmpty()) return false;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_EXAMPLE_SENTENCE, newSentence.trim());
        return db.update(TABLE_EXAMPLES, cv, COL_EXAMPLE_ID + " = ?", new String[]{String.valueOf(exampleId)}) > 0;
    }

    public synchronized boolean deleteExample(int exampleId) {
        if (exampleId <= 0) return false;
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE_EXAMPLES, COL_EXAMPLE_ID + " = ?", new String[]{String.valueOf(exampleId)}) > 0;
    }

    /**
     * Queries concordance occurrences of a word from real sentences typed by the user.
     */
    public synchronized List<ConcordanceEntry> getConcordanceSentences(String targetWord) {
        List<ConcordanceEntry> results = new ArrayList<>();
        if (targetWord == null || targetWord.trim().isEmpty()) return results;

        String word = targetWord.trim();
        SQLiteDatabase db = getReadableDatabase();

        Pattern pattern = Pattern.compile("(?i)(?<=^|[^a-zA-Z\u00C0-\u024F\u1E6C\u1E6D])"
                + Pattern.quote(word)
                + "(?=[^a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]|$)");

        String query = "SELECT " + COL_SENTENCE_TEXT + ", " + COL_SENTENCE_TIMESTAMP
                + " FROM " + TABLE_SENTENCES
                + " WHERE " + COL_SENTENCE_TEXT + " LIKE ? ORDER BY " + COL_SENTENCE_TIMESTAMP + " DESC LIMIT 100";

        try (Cursor cursor = db.rawQuery(query, new String[]{"%" + word + "%"})) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String sentence = cursor.getString(0);
                    long timestamp = cursor.getLong(1);
                    if (sentence != null && pattern.matcher(sentence).find()) {
                        results.add(new ConcordanceEntry(sentence, timestamp));
                    }
                }
            }
        }
        return results;
    }

    /**
     * Layered vocabulary query supporting 4 filter tabs:
     * - "all": Official non-deleted ISH words + Custom user words (paginated)
     * - "my_words": Only words user typed or custom added
     * - "ish_standard": All official ISH words (non-deleted) with overlaid user stats
     * - "custom": Custom added words or typed words not in official dictionary
     *
     * Performance & UX Orchestration:
     * - Search queries query the entire 54k corpus via index (capped at 50 results).
     * - Alphabetical browsing samples up to 20 words per letter across the whole alphabet.
     * - Frequency browsing returns the top 100 most active words.
     */
    public synchronized List<PersonalWord> getLayeredWords(String searchQuery, String filterType, String sortBy, int limit, int offset) {
        boolean hasSearch = searchQuery != null && !searchQuery.trim().isEmpty();

        // 1. Full search orchestration (fast index lookup, respecting filter tab & sort)
        if (hasSearch) {
            int searchLimit = (limit > 0) ? limit : 50;
            return searchDictionaryWords(searchQuery.trim(), filterType, sortBy, searchLimit);
        }

        // 2. Alphabetical browsing orchestration: 20 words per alphabet letter
        if ("alphabetical".equalsIgnoreCase(sortBy) && ("all".equalsIgnoreCase(filterType) || "ish_standard".equalsIgnoreCase(filterType))) {
            return getAlphabetSampledWords(filterType);
        }

        // 3. Frequency browsing orchestration: top 100 most active words
        int effectiveLimit = ("frequency".equalsIgnoreCase(sortBy)) ? 100 : limit;

        List<PersonalWord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        List<String> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder();

        if ("my_words".equalsIgnoreCase(filterType)) {
            sql.append("SELECT u.word, u.display_word, u.frequency, ")
               .append("COALESCE(i.base_frequency, 0), ")
               .append("CASE WHEN i.word IS NOT NULL AND i.is_deleted = 0 THEN 1 ELSE 0 END AS is_ish, ")
               .append("u.is_user_added, u.last_used, i.example_sentence, ")
               .append("COALESCE(u.suggest_replacement, i.suggest_replacement) AS suggest_replacement, ")
               .append("COALESCE(i.is_deleted, 0) AS is_deleted ")
               .append("FROM ").append(TABLE_UNIGRAMS).append(" u ")
               .append("LEFT JOIN ").append(TABLE_ISH_DICT).append(" i ON u.word = i.word ");

            if ("alphabetical".equalsIgnoreCase(sortBy)) {
                sql.append("ORDER BY u.word ASC ");
            } else if ("recency".equalsIgnoreCase(sortBy)) {
                sql.append("ORDER BY u.last_used DESC ");
            } else {
                sql.append("ORDER BY u.frequency DESC, u.last_used DESC ");
            }

        } else if ("custom".equalsIgnoreCase(filterType)) {
            sql.append("SELECT u.word, u.display_word, u.frequency, 0 AS base_frequency, ")
               .append("0 AS is_ish, u.is_user_added, u.last_used, NULL AS example_sentence, ")
               .append("u.suggest_replacement, 0 AS is_deleted ")
               .append("FROM ").append(TABLE_UNIGRAMS).append(" u ")
               .append("WHERE (u.is_user_added = 1 OR u.word NOT IN (SELECT word FROM ").append(TABLE_ISH_DICT).append(" WHERE is_deleted = 0)) ");

            if ("alphabetical".equalsIgnoreCase(sortBy)) {
                sql.append("ORDER BY u.word ASC ");
            } else if ("recency".equalsIgnoreCase(sortBy)) {
                sql.append("ORDER BY u.last_used DESC ");
            } else {
                sql.append("ORDER BY u.frequency DESC, u.last_used DESC ");
            }

        } else if ("ish_standard".equalsIgnoreCase(filterType)) {
            sql.append("SELECT i.word, i.display_word, ")
               .append("COALESCE(u.frequency, 0) AS user_freq, ")
               .append("i.base_frequency, ")
               .append("1 AS is_ish, ")
               .append("COALESCE(u.is_user_added, 0), ")
               .append("COALESCE(u.last_used, 0), ")
               .append("i.example_sentence, ")
               .append("COALESCE(u.suggest_replacement, i.suggest_replacement) AS suggest_replacement, ")
               .append("i.is_deleted ")
               .append("FROM ").append(TABLE_ISH_DICT).append(" i ")
               .append("LEFT JOIN ").append(TABLE_UNIGRAMS).append(" u ON i.word = u.word ")
               .append("WHERE i.is_deleted = 0 ");

            if ("alphabetical".equalsIgnoreCase(sortBy)) {
                sql.append("ORDER BY i.word ASC ");
            } else if ("recency".equalsIgnoreCase(sortBy)) {
                sql.append("ORDER BY COALESCE(u.last_used, 0) DESC, user_freq DESC, i.base_frequency DESC ");
            } else {
                sql.append("ORDER BY CASE WHEN user_freq > 0 THEN 1 ELSE 0 END DESC, user_freq DESC, i.base_frequency DESC, i.word ASC ");
            }

        } else {
            // "all"
            sql.append("SELECT word, display_word, user_freq, base_frequency, is_ish, is_user_added, last_used, example_sentence, suggest_replacement, is_deleted FROM (");

            sql.append("SELECT i.word AS word, i.display_word AS display_word, ")
               .append("COALESCE(u.frequency, 0) AS user_freq, ")
               .append("i.base_frequency AS base_frequency, ")
               .append("1 AS is_ish, ")
               .append("COALESCE(u.is_user_added, 0) AS is_user_added, ")
               .append("COALESCE(u.last_used, 0) AS last_used, ")
               .append("i.example_sentence AS example_sentence, ")
               .append("COALESCE(u.suggest_replacement, i.suggest_replacement) AS suggest_replacement, ")
               .append("i.is_deleted AS is_deleted ")
               .append("FROM ").append(TABLE_ISH_DICT).append(" i ")
               .append("LEFT JOIN ").append(TABLE_UNIGRAMS).append(" u ON i.word = u.word ")
               .append("WHERE i.is_deleted = 0 ");

            sql.append("UNION ALL ")
               .append("SELECT u.word AS word, u.display_word AS display_word, ")
               .append("u.frequency AS user_freq, ")
               .append("0 AS base_frequency, ")
               .append("0 AS is_ish, ")
               .append("u.is_user_added AS is_user_added, ")
               .append("u.last_used AS last_used, ")
               .append("NULL AS example_sentence, ")
               .append("u.suggest_replacement AS suggest_replacement, ")
               .append("0 AS is_deleted ")
               .append("FROM ").append(TABLE_UNIGRAMS).append(" u ")
               .append("WHERE u.word NOT IN (SELECT word FROM ").append(TABLE_ISH_DICT).append(" WHERE is_deleted = 0) ");

            sql.append(") ");

            if ("alphabetical".equalsIgnoreCase(sortBy)) {
                sql.append("ORDER BY word ASC ");
            } else if ("recency".equalsIgnoreCase(sortBy)) {
                sql.append("ORDER BY last_used DESC, user_freq DESC ");
            } else {
                sql.append("ORDER BY CASE WHEN user_freq > 0 THEN 1 ELSE 0 END DESC, user_freq DESC, base_frequency DESC, word ASC ");
            }
        }

        if (effectiveLimit > 0) {
            sql.append("LIMIT ").append(effectiveLimit);
            if (offset > 0) {
                sql.append(" OFFSET ").append(offset);
            }
        }

        try (Cursor cursor = db.rawQuery(sql.toString(), args.toArray(new String[0]))) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String word = cursor.getString(0);
                    String displayWord = cursor.getString(1);
                    int userFreq = cursor.getInt(2);
                    int baseFreq = cursor.getInt(3);
                    boolean isIsh = cursor.getInt(4) == 1;
                    boolean isUserAdded = cursor.getInt(5) == 1;
                    long lastUsed = cursor.getLong(6);
                    String example = cursor.getString(7);
                    String suggestReplacement = cursor.getString(8);
                    boolean isDeleted = cursor.getInt(9) == 1;

                    list.add(new PersonalWord(word, displayWord, userFreq, baseFreq, isIsh, isUserAdded, lastUsed, example, suggestReplacement, isDeleted));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error executing getLayeredWords query: " + e + " | SQL: " + sql, e);
        }
        return list;
    }

    /**
     * Executes a single search query (prefix or infix) respecting active tab filter and sort preferences.
     */
    private List<PersonalWord> executeSearchQuery(SQLiteDatabase db, String filterType, String sortBy, String query, boolean isInfix, int limit) {
        List<PersonalWord> results = new ArrayList<>();
        if (limit <= 0) return results;

        List<String> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder();

        if ("my_words".equalsIgnoreCase(filterType)) {
            sql.append("SELECT u.word, u.display_word, u.frequency AS user_freq, ")
               .append("COALESCE(i.base_frequency, 0) AS base_frequency, ")
               .append("CASE WHEN i.word IS NOT NULL AND i.is_deleted = 0 THEN 1 ELSE 0 END AS is_ish, ")
               .append("u.is_user_added, u.last_used, i.example_sentence, ")
               .append("COALESCE(u.suggest_replacement, i.suggest_replacement) AS suggest_replacement, ")
               .append("COALESCE(i.is_deleted, 0) AS is_deleted ")
               .append("FROM ").append(TABLE_UNIGRAMS).append(" u ")
               .append("LEFT JOIN ").append(TABLE_ISH_DICT).append(" i ON u.word = i.word ");

            if (!isInfix) {
                sql.append("WHERE u.word LIKE ? ");
                args.add(query + "%");
            } else {
                sql.append("WHERE u.word LIKE ? AND u.word NOT LIKE ? ");
                args.add("%" + query + "%");
                args.add(query + "%");
            }

            if ("alphabetical".equalsIgnoreCase(sortBy)) {
                sql.append("ORDER BY u.word ASC ");
            } else if ("recency".equalsIgnoreCase(sortBy)) {
                sql.append("ORDER BY u.last_used DESC, u.frequency DESC, u.word ASC ");
            } else {
                sql.append("ORDER BY u.frequency DESC, u.last_used DESC, u.word ASC ");
            }

        } else if ("custom".equalsIgnoreCase(filterType)) {
            sql.append("SELECT u.word, u.display_word, u.frequency AS user_freq, 0 AS base_frequency, ")
               .append("0 AS is_ish, u.is_user_added, u.last_used, NULL AS example_sentence, ")
               .append("u.suggest_replacement, 0 AS is_deleted ")
               .append("FROM ").append(TABLE_UNIGRAMS).append(" u ")
               .append("WHERE (u.is_user_added = 1 OR u.word NOT IN (SELECT word FROM ").append(TABLE_ISH_DICT).append(" WHERE is_deleted = 0)) ");

            if (!isInfix) {
                sql.append("AND u.word LIKE ? ");
                args.add(query + "%");
            } else {
                sql.append("AND u.word LIKE ? AND u.word NOT LIKE ? ");
                args.add("%" + query + "%");
                args.add(query + "%");
            }

            if ("alphabetical".equalsIgnoreCase(sortBy)) {
                sql.append("ORDER BY u.word ASC ");
            } else if ("recency".equalsIgnoreCase(sortBy)) {
                sql.append("ORDER BY u.last_used DESC, u.frequency DESC, u.word ASC ");
            } else {
                sql.append("ORDER BY u.frequency DESC, u.last_used DESC, u.word ASC ");
            }

        } else if ("ish_standard".equalsIgnoreCase(filterType)) {
            sql.append("SELECT i.word, i.display_word, ")
               .append("COALESCE(u.frequency, 0) AS user_freq, ")
               .append("i.base_frequency, ")
               .append("1 AS is_ish, ")
               .append("COALESCE(u.is_user_added, 0) AS is_user_added, ")
               .append("COALESCE(u.last_used, 0) AS last_used, ")
               .append("i.example_sentence, ")
               .append("COALESCE(u.suggest_replacement, i.suggest_replacement) AS suggest_replacement, ")
               .append("i.is_deleted ")
               .append("FROM ").append(TABLE_ISH_DICT).append(" i ")
               .append("LEFT JOIN ").append(TABLE_UNIGRAMS).append(" u ON i.word = u.word ")
               .append("WHERE i.is_deleted = 0 ");

            if (!isInfix) {
                sql.append("AND i.word LIKE ? ");
                args.add(query + "%");
            } else {
                sql.append("AND i.word LIKE ? AND i.word NOT LIKE ? ");
                args.add("%" + query + "%");
                args.add(query + "%");
            }

            if ("alphabetical".equalsIgnoreCase(sortBy)) {
                sql.append("ORDER BY i.word ASC ");
            } else if ("recency".equalsIgnoreCase(sortBy)) {
                sql.append("ORDER BY COALESCE(u.last_used, 0) DESC, user_freq DESC, i.base_frequency DESC, i.word ASC ");
            } else {
                sql.append("ORDER BY CASE WHEN user_freq > 0 THEN 1 ELSE 0 END DESC, user_freq DESC, i.base_frequency DESC, i.word ASC ");
            }

        } else {
            // "all" or default
            sql.append("SELECT word, display_word, user_freq, base_frequency, is_ish, is_user_added, last_used, example_sentence, suggest_replacement, is_deleted FROM (");

            sql.append("SELECT i.word AS word, i.display_word AS display_word, ")
               .append("COALESCE(u.frequency, 0) AS user_freq, ")
               .append("i.base_frequency AS base_frequency, ")
               .append("1 AS is_ish, ")
               .append("COALESCE(u.is_user_added, 0) AS is_user_added, ")
               .append("COALESCE(u.last_used, 0) AS last_used, ")
               .append("i.example_sentence AS example_sentence, ")
               .append("COALESCE(u.suggest_replacement, i.suggest_replacement) AS suggest_replacement, ")
               .append("i.is_deleted AS is_deleted ")
               .append("FROM ").append(TABLE_ISH_DICT).append(" i ")
               .append("LEFT JOIN ").append(TABLE_UNIGRAMS).append(" u ON i.word = u.word ")
               .append("WHERE i.is_deleted = 0 ");

            if (!isInfix) {
                sql.append("AND i.word LIKE ? ");
                args.add(query + "%");
            } else {
                sql.append("AND i.word LIKE ? AND i.word NOT LIKE ? ");
                args.add("%" + query + "%");
                args.add(query + "%");
            }

            sql.append("UNION ALL ")
               .append("SELECT u.word AS word, u.display_word AS display_word, ")
               .append("u.frequency AS user_freq, ")
               .append("0 AS base_frequency, ")
               .append("0 AS is_ish, ")
               .append("u.is_user_added AS is_user_added, ")
               .append("u.last_used AS last_used, ")
               .append("NULL AS example_sentence, ")
               .append("u.suggest_replacement AS suggest_replacement, ")
               .append("0 AS is_deleted ")
               .append("FROM ").append(TABLE_UNIGRAMS).append(" u ")
               .append("WHERE u.word NOT IN (SELECT word FROM ").append(TABLE_ISH_DICT).append(" WHERE is_deleted = 0) ");

            if (!isInfix) {
                sql.append("AND u.word LIKE ? ");
                args.add(query + "%");
            } else {
                sql.append("AND u.word LIKE ? AND u.word NOT LIKE ? ");
                args.add("%" + query + "%");
                args.add(query + "%");
            }

            sql.append(") ");

            if ("alphabetical".equalsIgnoreCase(sortBy)) {
                sql.append("ORDER BY word ASC ");
            } else if ("recency".equalsIgnoreCase(sortBy)) {
                sql.append("ORDER BY last_used DESC, user_freq DESC, base_frequency DESC, word ASC ");
            } else {
                sql.append("ORDER BY CASE WHEN user_freq > 0 THEN 1 ELSE 0 END DESC, user_freq DESC, base_frequency DESC, word ASC ");
            }
        }

        sql.append("LIMIT ").append(limit);

        try (Cursor c = db.rawQuery(sql.toString(), args.toArray(new String[0]))) {
            while (c != null && c.moveToNext()) {
                results.add(new PersonalWord(
                        c.getString(0),
                        c.getString(1),
                        c.getInt(2),
                        c.getInt(3),
                        c.getInt(4) == 1,
                        c.getInt(5) == 1,
                        c.getLong(6),
                        c.getString(7),
                        c.getString(8),
                        c.getInt(9) == 1
                ));
            }
        } catch (Exception e) {
            Log.e(TAG, "Search query error (isInfix=" + isInfix + ", filter=" + filterType + "): " + e);
        }

        return results;
    }

    /**
     * Search dictionary with prefix prioritization ('word%') followed by infix ('%word%'),
     * capped at maxResults to guarantee instantaneous UI response,
     * respecting active filter tab (all, my_words, custom, ish_standard) and sortBy (alphabetical, recency, frequency).
     */
    private List<PersonalWord> searchDictionaryWords(String query, String filterType, String sortBy, int maxResults) {
        List<PersonalWord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String q = query.trim().toLowerCase();

        // 1. Prefix match first: 'query%'
        list.addAll(executeSearchQuery(db, filterType, sortBy, q, false, maxResults));

        // 2. If fewer than 20 results, append infix match '%query%'
        if (list.size() < 20) {
            int remaining = maxResults - list.size();
            list.addAll(executeSearchQuery(db, filterType, sortBy, q, true, remaining));
        }
        return list;
    }

    /**
     * Balanced alphabetical browsing: samples up to 20 words for each alphabet letter
     * across the entire 54k lexicon so the user sees a representative A-Z distribution.
     */
    private List<PersonalWord> getAlphabetSampledWords(String filterType) {
        List<PersonalWord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String[] alphabet = {"a", "aw", "b", "c", "ch", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "ng", "o", "p", "r", "s", "t", "ṭ", "u", "v", "z"};

        // First include user typed / custom words if any
        String userSql = "SELECT u.word, u.display_word, u.frequency, 0 AS base_frequency, "
                + "0 AS is_ish, u.is_user_added, u.last_used, NULL AS example_sentence, "
                + "u.suggest_replacement, 0 AS is_deleted "
                + "FROM " + TABLE_UNIGRAMS + " u "
                + "ORDER BY u.word ASC LIMIT 30";
        try (Cursor c = db.rawQuery(userSql, null)) {
            while (c != null && c.moveToNext()) {
                list.add(new PersonalWord(c.getString(0), c.getString(1), c.getInt(2), c.getInt(3),
                        c.getInt(4) == 1, c.getInt(5) == 1, c.getLong(6), c.getString(7), c.getString(8), c.getInt(9) == 1));
            }
        } catch (Exception ignored) {}

        // Then sample 20 words per alphabet letter
        for (String letter : alphabet) {
            String sql = "SELECT i.word, i.display_word, COALESCE(u.frequency, 0) AS user_freq, i.base_frequency, "
                    + "1 AS is_ish, COALESCE(u.is_user_added, 0), COALESCE(u.last_used, 0), i.example_sentence, "
                    + "COALESCE(u.suggest_replacement, i.suggest_replacement), i.is_deleted "
                    + "FROM " + TABLE_ISH_DICT + " i "
                    + "LEFT JOIN " + TABLE_UNIGRAMS + " u ON i.word = u.word "
                    + "WHERE i.is_deleted = 0 AND i.word LIKE ? "
                    + "ORDER BY i.word ASC LIMIT 20";

            try (Cursor c = db.rawQuery(sql, new String[]{letter + "%"})) {
                while (c != null && c.moveToNext()) {
                    String word = c.getString(0);
                    boolean exists = false;
                    for (PersonalWord pw : list) {
                        if (pw.word.equals(word)) { exists = true; break; }
                    }
                    if (!exists) {
                        list.add(new PersonalWord(word, c.getString(1), c.getInt(2), c.getInt(3),
                                c.getInt(4) == 1, c.getInt(5) == 1, c.getLong(6), c.getString(7), c.getString(8), c.getInt(9) == 1));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Alphabet sample error for " + letter + ": " + e);
            }
        }
        return list;
    }

    /**
     * Backward-compatible alias for getLayeredWords.
     */
    public synchronized List<PersonalWord> getPersonalWords(String searchQuery, String filterType, String sortBy) {
        return getLayeredWords(searchQuery, filterType, sortBy, 500, 0);
    }

    /**
     * Returns suggestions for keyboard suggestion bar.
     * 1. If prefix matches an exact suggest_replacement rule (e.g. saruoka -> saruok a, ṭwng -> ṭawng):
     *    - Option 0: The replacement (Recommended / Auto-correct on space)
     *    - Option 1: The literal typed token (so user can always keep literal)
     * 2. For prefix matching (e.g. saruok):
     *    - Looks up words starting with saruok%.
     *    - If a matching word has a replacement (e.g. saruoka -> saruok a), promotes the replacement (saruok a)
     *      ahead of the un-replaced form.
     *    - Keeps regular prefix completions (saruok, saruokna) to fill all slots.
     */
    /**
     * Retrieves predictive completions for a typed prefix with:
     * 1. Exact replacement / auto-correct rule prioritized.
     * 2. Diacritic expansion (e.g. 't' matches both 't%' and 'ṭ%').
     * 3. Composite score heavily prioritizing user-typed and custom words + recency over static baseline.
     */
    public synchronized List<PersonalWord> getSuggestionsForPrefix(String prefix, int limit) {
        List<PersonalWord> list = new ArrayList<>();
        if (prefix == null || prefix.trim().isEmpty()) return list;

        String norm = prefix.trim().toLowerCase();
        SQLiteDatabase db = getReadableDatabase();

        // 1. Check exact replacement rule (for typos, fused words, slangs)
        String exactSql = "SELECT word, display_word, suggest_replacement FROM ("
                + "SELECT word, display_word, suggest_replacement FROM " + TABLE_UNIGRAMS + " WHERE LOWER(word) = ? AND suggest_replacement IS NOT NULL AND length(suggest_replacement) > 0 "
                + "UNION "
                + "SELECT word, display_word, suggest_replacement FROM " + TABLE_ISH_DICT + " WHERE LOWER(word) = ? AND is_deleted = 0 AND suggest_replacement IS NOT NULL AND length(suggest_replacement) > 0"
                + ") LIMIT 1";

        Cursor cExact = db.rawQuery(exactSql, new String[]{norm, norm});
        if (cExact != null) {
            if (cExact.moveToFirst()) {
                String display = cExact.getString(1);
                String exactReplacement = cExact.getString(2);

                // Option 0: The replacement (Recommended / Auto-correct on space)
                PersonalWord repWord = new PersonalWord(exactReplacement.toLowerCase(), exactReplacement, 9999, 0, true, true, System.currentTimeMillis(), null, exactReplacement, false);
                repWord.isExactReplacement = true;
                list.add(repWord);

                // Option 1: The literal typed token (so user can always keep what they literally typed)
                PersonalWord litWord = new PersonalWord(norm, (display != null && !display.isEmpty()) ? display : norm, 9998, 0, false, false, System.currentTimeMillis(), null, null, false);
                litWord.isLiteral = true;
                list.add(litWord);
            }
            cExact.close();
        }

        // 2. Diacritic expansion: 't' matches 't%' and 'ṭ%', 'ṭ' matches 'ṭ%' and 't%'
        String pat1 = norm + "%";
        String pat2 = pat1;
        if (norm.startsWith("t")) {
            pat2 = "ṭ" + norm.substring(1) + "%";
        } else if (norm.startsWith("ṭ")) {
            pat2 = "t" + norm.substring(1) + "%";
        }

        long now = System.currentTimeMillis();
        long oneHourAgo = now - (3600 * 1000L);
        long oneDayAgo = now - (24 * 3600 * 1000L);

        // 3. Query prefix matches with smart composite score
        String prefixSql = "SELECT word, display_word, MAX(frequency) AS frequency, MAX(is_user_added) AS is_user_added, suggest_replacement, MAX(total_score) AS score FROM ("
                + "SELECT word, display_word, frequency, is_user_added, suggest_replacement, "
                + " (frequency * 150 + is_user_added * 400 + CASE WHEN last_used > ? THEN 300 WHEN last_used > ? THEN 150 ELSE 0 END + 500) AS total_score "
                + "FROM " + TABLE_UNIGRAMS + " WHERE (LOWER(word) LIKE ? OR LOWER(word) LIKE ?) "
                + "UNION ALL "
                + "SELECT word, display_word, base_frequency AS frequency, 0 AS is_user_added, suggest_replacement, "
                + " base_frequency AS total_score "
                + "FROM " + TABLE_ISH_DICT + " WHERE is_deleted = 0 AND (LOWER(word) LIKE ? OR LOWER(word) LIKE ?) "
                + ") GROUP BY word ORDER BY score DESC LIMIT 15";

        try (Cursor c = db.rawQuery(prefixSql, new String[]{
                String.valueOf(oneHourAgo), String.valueOf(oneDayAgo), pat1, pat2, pat1, pat2
        })) {
            if (c != null) {
                List<PersonalWord> replacementPromotions = new ArrayList<>();
                List<PersonalWord> regularCompletions = new ArrayList<>();

                while (c.moveToNext()) {
                    String word = c.getString(0);
                    String display = c.getString(1);
                    int freq = c.getInt(2);
                    boolean isUser = c.getInt(3) == 1;
                    String replacement = c.getString(4);

                    if (replacement != null && !replacement.trim().isEmpty()) {
                        String rep = replacement.trim();
                        boolean exists = false;
                        for (PersonalWord pw : list) {
                            if (pw.word.equalsIgnoreCase(rep)) { exists = true; break; }
                        }
                        if (!exists) {
                            for (PersonalWord pw : replacementPromotions) {
                                if (pw.word.equalsIgnoreCase(rep)) { exists = true; break; }
                            }
                        }
                        if (!exists) {
                            PersonalWord pRep = new PersonalWord(rep.toLowerCase(Locale.ROOT), rep, freq + 500, 0, false, isUser, System.currentTimeMillis(), null, rep, false);
                            replacementPromotions.add(pRep);
                        }
                    } else {
                        boolean exists = false;
                        for (PersonalWord pw : list) {
                            if (pw.word.equalsIgnoreCase(word)) { exists = true; break; }
                        }
                        if (!exists) {
                            for (PersonalWord pw : regularCompletions) {
                                if (pw.word.equalsIgnoreCase(word)) { exists = true; break; }
                            }
                        }
                        if (!exists) {
                            regularCompletions.add(new PersonalWord(word, display, freq, isUser, System.currentTimeMillis()));
                        }
                    }
                }

                // Insert promoted replacements first (e.g. saruok a ahead of saruoka)
                for (PersonalWord pw : replacementPromotions) {
                    if (list.size() < limit + 3) {
                        list.add(pw);
                    }
                }
                // Then regular completions
                for (PersonalWord pw : regularCompletions) {
                    if (list.size() < limit + 3) {
                        list.add(pw);
                    }
                }
            }
        }
        return list;
    }

    /**
     * Predicts likely next words following prevWord using:
     * 1. TOP PRIORITY: Learned user bigrams from actual typing sessions
     * 2. Compound split rules (e.g. saruoka -> saruok a yields "a")
     * 3. Fallback high-probability particles/words
     */
    public synchronized List<String> getNextWordPredictions(String prevWord, int limit) {
        List<String> results = new ArrayList<>();
        if (prevWord == null || prevWord.trim().isEmpty()) return results;
        String norm = prevWord.trim().toLowerCase();
        SQLiteDatabase db = getReadableDatabase();

        // 1. TOP PRIORITY: Actual learned user bigrams from typing sessions
        String biSql = "SELECT " + COL_BIGRAM_NEXT + " FROM " + TABLE_BIGRAMS
                + " WHERE " + COL_BIGRAM_PREV + " = ? ORDER BY " + COL_BIGRAM_FREQ + " DESC, " + COL_BIGRAM_LAST_USED + " DESC LIMIT " + limit;
        try (Cursor cBi = db.rawQuery(biSql, new String[]{norm})) {
            while (cBi != null && cBi.moveToNext() && results.size() < limit) {
                String nextW = cBi.getString(0);
                if (nextW != null && !nextW.isEmpty()) {
                    boolean exists = false;
                    for (String r : results) {
                        if (r.equalsIgnoreCase(nextW)) { exists = true; break; }
                    }
                    if (!exists) {
                        results.add(nextW);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Bigram query error: " + e);
        }

        // 2. Compound split rules in ISH dict and user unigrams (e.g. saruok -> "a")
        if (results.size() < limit) {
            String pattern = norm + " %";
            String splitSql = "SELECT suggest_replacement FROM ("
                    + "SELECT suggest_replacement FROM " + TABLE_UNIGRAMS + " WHERE suggest_replacement LIKE ? "
                    + "UNION "
                    + "SELECT suggest_replacement FROM " + TABLE_ISH_DICT + " WHERE is_deleted = 0 AND suggest_replacement LIKE ?"
                    + ") LIMIT 5";
            try (Cursor cSplit = db.rawQuery(splitSql, new String[]{pattern, pattern})) {
                while (cSplit != null && cSplit.moveToNext() && results.size() < limit) {
                    String rep = cSplit.getString(0);
                    if (rep != null) {
                        String[] tokens = rep.trim().split("\\s+");
                        if (tokens.length >= 2 && tokens[0].equalsIgnoreCase(norm)) {
                            String nextW = tokens[1];
                            boolean exists = false;
                            for (String r : results) {
                                if (r.equalsIgnoreCase(nextW)) { exists = true; break; }
                            }
                            if (!exists) {
                                results.add(nextW);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // 3. Fallback high-probability particles/words if no learned bigram exists
        if (results.size() < limit) {
            String[] commonFollowers = {"a", "in", "chu", "hai", "lawm", "tah", "ding", "thei"};
            for (String w : commonFollowers) {
                if (!w.equalsIgnoreCase(norm)) {
                    boolean exists = false;
                    for (String r : results) {
                        if (r.equalsIgnoreCase(w)) { exists = true; break; }
                    }
                    if (!exists) {
                        results.add(w);
                        if (results.size() >= limit) break;
                    }
                }
            }
        }

        return results;
    }

    /**
     * Records a live word transition immediately when words are typed or space is pressed.
     */
    public synchronized void recordWordTransition(String prevWord, String nextWord) {
        if (prevWord == null || nextWord == null) return;
        String prev = prevWord.trim().toLowerCase();
        String next = nextWord.trim().toLowerCase();
        if (prev.isEmpty() || next.isEmpty() || prev.equals(next)) return;
        if (!prev.matches("^[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+(-[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+)*$") ||
            !next.matches("^[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+(-[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+)*$")) {
            return;
        }

        long now = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        try {
            // 1. Update bigrams
            db.execSQL("INSERT INTO " + TABLE_BIGRAMS + " (" + COL_BIGRAM_PREV + ", " + COL_BIGRAM_NEXT + ", " + COL_BIGRAM_FREQ + ", " + COL_BIGRAM_LAST_USED + ") "
                    + "VALUES (?, ?, 1, ?) "
                    + "ON CONFLICT(" + COL_BIGRAM_PREV + ", " + COL_BIGRAM_NEXT + ") DO UPDATE SET "
                    + COL_BIGRAM_FREQ + " = " + COL_BIGRAM_FREQ + " + 1, "
                    + COL_BIGRAM_LAST_USED + " = ?", new Object[]{prev, next, now, now});

            // 2. Update unigram for next
            db.execSQL("INSERT INTO " + TABLE_UNIGRAMS + " (" + COL_UNIGRAM_WORD + ", " + COL_UNIGRAM_DISPLAY + ", " + COL_UNIGRAM_FREQ + ", " + COL_UNIGRAM_IS_USER_ADDED + ", " + COL_UNIGRAM_LAST_USED + ") "
                    + "VALUES (?, ?, 1, 0, ?) "
                    + "ON CONFLICT(" + COL_UNIGRAM_WORD + ") DO UPDATE SET "
                    + COL_UNIGRAM_FREQ + " = " + COL_UNIGRAM_FREQ + " + 1, "
                    + COL_UNIGRAM_LAST_USED + " = ?", new Object[]{next, nextWord.trim(), now, now});
        } catch (Exception e) {
            Log.e(TAG, "recordWordTransition error: " + e);
        }
    }

    public synchronized String getExactReplacement(String word) {
        if (word == null || word.trim().isEmpty()) return null;
        String norm = word.trim().toLowerCase();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT suggest_replacement FROM ("
                + "SELECT suggest_replacement FROM " + TABLE_UNIGRAMS + " WHERE LOWER(word) = ? AND suggest_replacement IS NOT NULL AND length(suggest_replacement) > 0 "
                + "UNION "
                + "SELECT suggest_replacement FROM " + TABLE_ISH_DICT + " WHERE LOWER(word) = ? AND is_deleted = 0 AND suggest_replacement IS NOT NULL AND length(suggest_replacement) > 0"
                + ") LIMIT 1";
        String replacement = null;
        try (Cursor c = db.rawQuery(sql, new String[]{norm, norm})) {
            if (c != null && c.moveToFirst()) {
                replacement = c.getString(0);
            }
        }
        return replacement;
    }

    /**
     * Removes 1x auto-learned typos while protecting manually added words and official dictionary words.
     */
    public synchronized int pruneSingleUseTypos() {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE_UNIGRAMS, COL_UNIGRAM_FREQ + " = 1 AND " + COL_UNIGRAM_IS_USER_ADDED + " = 0", null);
    }

    private void pruneIfExceedsBudget(SQLiteDatabase db) {
        try {
            db.execSQL("DELETE FROM " + TABLE_SENTENCES + " WHERE " + COL_SENTENCE_ID + " IN ("
                    + "SELECT " + COL_SENTENCE_ID + " FROM " + TABLE_SENTENCES
                    + " ORDER BY " + COL_SENTENCE_TIMESTAMP + " ASC LIMIT MAX(0, (SELECT COUNT(*) FROM " + TABLE_SENTENCES + ") - " + MAX_SENTENCES + "))");

            db.execSQL("DELETE FROM " + TABLE_BIGRAMS + " WHERE " + COL_BIGRAM_FREQ + " = 1 AND rowid IN ("
                    + "SELECT rowid FROM " + TABLE_BIGRAMS + " ORDER BY " + COL_BIGRAM_LAST_USED + " ASC LIMIT MAX(0, (SELECT COUNT(*) FROM " + TABLE_BIGRAMS + ") - " + MAX_BIGRAMS + "))");

            db.execSQL("DELETE FROM " + TABLE_UNIGRAMS + " WHERE " + COL_UNIGRAM_IS_USER_ADDED + " = 0 AND " + COL_UNIGRAM_FREQ + " = 1 AND " + COL_UNIGRAM_WORD + " IN ("
                    + "SELECT " + COL_UNIGRAM_WORD + " FROM " + TABLE_UNIGRAMS + " WHERE " + COL_UNIGRAM_IS_USER_ADDED + " = 0"
                    + " ORDER BY " + COL_UNIGRAM_LAST_USED + " ASC LIMIT MAX(0, (SELECT COUNT(*) FROM " + TABLE_UNIGRAMS + ") - " + MAX_UNIGRAMS + "))");
        } catch (Exception e) {
            Log.w(TAG, "Pruning warning: " + e);
        }
    }

    public synchronized int getSentenceCount() {
        return getCount(TABLE_SENTENCES);
    }

    public synchronized int getMyWordsCount() {
        return getCount(TABLE_UNIGRAMS);
    }

    public synchronized int getIshStandardCount() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_ISH_DICT + " WHERE " + COL_ISH_IS_DELETED + " = 0", null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }
        return 0;
    }

    public synchronized int getCustomWordsCount() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_UNIGRAMS + " WHERE " + COL_UNIGRAM_IS_USER_ADDED + " = 1 OR " + COL_UNIGRAM_WORD + " NOT IN (SELECT " + COL_ISH_WORD + " FROM " + TABLE_ISH_DICT + " WHERE " + COL_ISH_IS_DELETED + " = 0)", null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }
        return 0;
    }

    public synchronized int getAllWordsCount() {
        return getIshStandardCount() + getCustomWordsCount();
    }

    private int getCount(String tableName) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + tableName, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }
        return 0;
    }

    /**
     * Exports the live baseline dictionary into standard TSV (word\tfrequency)
     * suitable for Keyman build compilation.
     */
    public synchronized File exportBaselineTsv() {
        try {
            File exportFile = new File(appContext.getCacheDir(), "wordlist.tsv");
            SQLiteDatabase db = getReadableDatabase();

            String sql = "SELECT word, MAX(freq) AS freq FROM ("
                    + "SELECT word, base_frequency AS freq FROM " + TABLE_ISH_DICT + " WHERE is_deleted = 0 "
                    + "UNION ALL "
                    + "SELECT word, frequency AS freq FROM " + TABLE_UNIGRAMS + " WHERE word NOT IN (SELECT word FROM " + TABLE_ISH_DICT + " WHERE is_deleted = 1)"
                    + ") GROUP BY word ORDER BY freq DESC, word ASC";

            try (Cursor c = db.rawQuery(sql, null);
                 OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(exportFile), StandardCharsets.UTF_8)) {
                if (c != null) {
                    while (c.moveToNext()) {
                        String word = c.getString(0);
                        int freq = c.getInt(1);
                        writer.write(word + "\t" + freq + "\n");
                    }
                }
            }
            return exportFile;
        } catch (Exception e) {
            Log.e(TAG, "exportBaselineTsv error: " + e);
            return null;
        }
    }

    /**
     * Exports the complete lexicon archive into JSON with word info, suggest replacements,
     * and all verified example sentences.
     */
    public synchronized File exportFullArchiveJson() {
        try {
            File exportFile = new File(appContext.getCacheDir(), "ish_lexicon_export.json");
            SQLiteDatabase db = getReadableDatabase();
            JSONArray root = new JSONArray();

            // Pre-load all verified examples in one batch query to avoid N+1 DB round-trips
            Map<String, List<String>> examplesMap = new HashMap<>();
            try (Cursor cEx = db.rawQuery("SELECT " + COL_EXAMPLE_WORD + ", " + COL_EXAMPLE_SENTENCE + " FROM " + TABLE_EXAMPLES, null)) {
                if (cEx != null) {
                    while (cEx.moveToNext()) {
                        String w = cEx.getString(0);
                        String s = cEx.getString(1);
                        if (w != null && s != null) {
                            String key = w.toLowerCase(java.util.Locale.ROOT);
                            List<String> list = examplesMap.get(key);
                            if (list == null) {
                                list = new ArrayList<>();
                                examplesMap.put(key, list);
                            }
                            list.add(s);
                        }
                    }
                }
            }

            List<PersonalWord> allWords = getLayeredWords(null, "all", "frequency", -1, 0);
            for (PersonalWord pw : allWords) {
                JSONObject obj = new JSONObject();
                obj.put("word", pw.word);
                obj.put("display_word", pw.displayWord);
                obj.put("base_frequency", pw.baseFrequency);
                obj.put("user_frequency", pw.frequency);
                obj.put("is_standard", pw.isIshStandard);
                obj.put("is_custom", pw.isUserAdded);
                if (pw.suggestReplacement != null && !pw.suggestReplacement.isEmpty()) {
                    obj.put("suggest_replacement", pw.suggestReplacement);
                }

                // Attach verified examples from pre-loaded map
                List<String> examples = examplesMap.get(pw.word.toLowerCase());
                if (examples != null && !examples.isEmpty()) {
                    JSONArray exArr = new JSONArray();
                    for (String ex : examples) {
                        exArr.put(ex);
                    }
                    obj.put("verified_examples", exArr);
                }
                root.put(obj);
            }

            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(exportFile), StandardCharsets.UTF_8)) {
                writer.write(root.toString(2));
            }
            return exportFile;
        } catch (Exception e) {
            Log.e(TAG, "exportFullArchiveJson error: " + e);
            return null;
        }
    }

    public synchronized int getUnigramCount() {
        return getCount(TABLE_UNIGRAMS);
    }

    public synchronized int getBigramCount() {
        return getCount(TABLE_BIGRAMS);
    }

    public synchronized List<String> getAllSentences() {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT " + COL_SENTENCE_TEXT + ", " + COL_SENTENCE_WORD_COUNT + ", " + COL_SENTENCE_TIMESTAMP + " FROM " + TABLE_SENTENCES + " ORDER BY " + COL_SENTENCE_ID + " ASC", null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String text = cursor.getString(0);
                    int wc = cursor.getInt(1);
                    long ts = cursor.getLong(2);
                    list.add("{\"text\":\"" + escapeJson(text) + "\",\"word_count\":" + wc + ",\"timestamp\":" + ts + "}");
                }
            }
        }
        return list;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    public synchronized void clearAllData() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_SENTENCES, null, null);
        db.delete(TABLE_UNIGRAMS, null, null);
        db.delete(TABLE_BIGRAMS, null, null);
        db.delete(TABLE_EXAMPLES, null, null);
        Log.i(TAG, "User corpus database cleared by user request.");
    }
}
