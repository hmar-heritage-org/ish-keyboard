package org.hmarheritage.ishkeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EmojiPanelView extends LinearLayout {

    public interface EmojiListener {
        void onEmojiSelected(String emoji);
        void onBackspace();
        void onClose();
    }

    private static final String PREF_NAME = "ISH_EMOJI_PREFS";
    private static final String KEY_RECENTS = "recent_emojis";
    private static final int MAX_RECENTS = 32;

    private final EmojiListener listener;
    private final SharedPreferences prefs;
    private final List<String> recentList = new ArrayList<>();
    private int currentCategoryIndex = 1; // Default to Smileys (index 1)

    private LinearLayout tabContainer;
    private final TextView[] tabViews = new TextView[EmojiData.CATEGORY_ICONS.length];
    private TextView categoryTitleView;
    private RecyclerView recyclerView;
    private EmojiAdapter adapter;

    public EmojiPanelView(Context context, EmojiListener listener) {
        super(context);
        this.listener = listener;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadRecents();
        initView();
    }

    private void loadRecents() {
        recentList.clear();
        String saved = prefs.getString(KEY_RECENTS, "");
        if (saved != null && !saved.isEmpty()) {
            String[] parts = saved.split(",");
            for (String p : parts) {
                if (!p.trim().isEmpty()) {
                    recentList.add(p.trim());
                }
            }
        }
        if (recentList.isEmpty()) {
            // Default popular starter emojis if none yet
            recentList.addAll(Arrays.asList("❤️", "😂", "👍", "🙏", "😊", "🔥", "🥰", "😭", "✨", "🎉", "👏", "🤣", "😍", "🥳", "💯", "🙌"));
        }
    }

    private void saveRecents() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recentList.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(recentList.get(i));
        }
        prefs.edit().putString(KEY_RECENTS, sb.toString()).apply();
    }

    private void addRecent(String emoji) {
        recentList.remove(emoji);
        recentList.add(0, emoji);
        while (recentList.size() > MAX_RECENTS) {
            recentList.remove(recentList.size() - 1);
        }
        saveRecents();
    }

    private void initView() {
        setOrientation(VERTICAL);
        setBackgroundColor(Color.parseColor("#151D18")); // Deep Forest Dark Theme

        // 1. Top Category Tab Bar
        HorizontalScrollView tabScroll = new HorizontalScrollView(getContext());
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(44)));
        tabScroll.setBackgroundColor(Color.parseColor("#151D18"));

        tabContainer = new LinearLayout(getContext());
        tabContainer.setOrientation(HORIZONTAL);
        tabContainer.setGravity(Gravity.CENTER_VERTICAL);
        tabContainer.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        for (int i = 0; i < EmojiData.CATEGORY_ICONS.length; i++) {
            final int catIdx = i;
            TextView tv = new TextView(getContext());
            tv.setText(EmojiData.CATEGORY_ICONS[i]);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            tv.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
            tv.setLayoutParams(lp);

            tv.setOnClickListener(v -> selectCategory(catIdx));
            tabViews[i] = tv;
            tabContainer.addView(tv);
        }
        tabScroll.addView(tabContainer);
        addView(tabScroll);

        // 2. Middle Emoji Grid
        recyclerView = new RecyclerView(getContext());
        recyclerView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f));
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 8));
        recyclerView.setHasFixedSize(true);
        recyclerView.setPadding(dp(4), dp(4), dp(4), dp(4));
        recyclerView.setClipToPadding(false);

        adapter = new EmojiAdapter();
        recyclerView.setAdapter(adapter);
        addView(recyclerView);

        // 3. Bottom Action Bar
        LinearLayout bottomBar = new LinearLayout(getContext());
        bottomBar.setOrientation(HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));
        bottomBar.setBackgroundColor(Color.parseColor("#151D18"));
        bottomBar.setPadding(dp(8), dp(4), dp(8), dp(6));

        // ABC Button to flip back to keyboard
        TextView btnAbc = new TextView(getContext());
        btnAbc.setText("ABC");
        btnAbc.setTypeface(Typeface.DEFAULT_BOLD);
        btnAbc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btnAbc.setTextColor(Color.WHITE);
        btnAbc.setGravity(Gravity.CENTER);
        GradientDrawable abcBg = new GradientDrawable();
        abcBg.setCornerRadius(dp(6));
        abcBg.setColor(Color.parseColor("#2E7D32")); // HHF Emerald Green
        btnAbc.setBackground(abcBg);
        LinearLayout.LayoutParams abcLp = new LinearLayout.LayoutParams(dp(70), dp(38));
        btnAbc.setLayoutParams(abcLp);
        btnAbc.setOnClickListener(v -> {
            if (listener != null) listener.onClose();
        });
        bottomBar.addView(btnAbc);

        // Category title indicator
        categoryTitleView = new TextView(getContext());
        categoryTitleView.setText(EmojiData.CATEGORY_NAMES[currentCategoryIndex]);
        categoryTitleView.setTextColor(Color.parseColor("#80A08A"));
        categoryTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        categoryTitleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        categoryTitleView.setLayoutParams(titleLp);
        bottomBar.addView(categoryTitleView);

        // Backspace Button
        TextView btnBksp = new TextView(getContext());
        btnBksp.setText("⌫");
        btnBksp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        btnBksp.setTextColor(Color.WHITE);
        btnBksp.setGravity(Gravity.CENTER);
        GradientDrawable bkspBg = new GradientDrawable();
        bkspBg.setCornerRadius(dp(6));
        bkspBg.setColor(Color.parseColor("#25332A"));
        btnBksp.setBackground(bkspBg);
        LinearLayout.LayoutParams bkspLp = new LinearLayout.LayoutParams(dp(60), dp(38));
        btnBksp.setLayoutParams(bkspLp);

        // Tap and auto-repeat on hold
        setupBackspaceRepeat(btnBksp);
        bottomBar.addView(btnBksp);

        addView(bottomBar);

        // Initial selection
        selectCategory(currentCategoryIndex);
    }

    private void setupBackspaceRepeat(View btn) {
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onBackspace();
                }
                handler.postDelayed(this, 60);
            }
        };

        btn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (listener != null) listener.onBackspace();
                    handler.postDelayed(runnable, 350);
                    v.setPressed(true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(runnable);
                    v.setPressed(false);
                    return true;
            }
            return false;
        });
    }

    public void selectCategory(int index) {
        if (index < 0 || index >= EmojiData.CATEGORY_NAMES.length) return;
        currentCategoryIndex = index;

        for (int i = 0; i < tabViews.length; i++) {
            if (i == index) {
                tabViews[i].setBackgroundColor(Color.parseColor("#2E7D32"));
            } else {
                tabViews[i].setBackgroundColor(Color.TRANSPARENT);
            }
        }

        categoryTitleView.setText(EmojiData.CATEGORY_NAMES[index]);

        List<String> items;
        if (index == 0) {
            items = new ArrayList<>(recentList);
        } else {
            items = Arrays.asList(EmojiData.getCategoryEmojis(index));
        }

        adapter.setEmojis(items);
        recyclerView.scrollToPosition(0);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private class EmojiAdapter extends RecyclerView.Adapter<EmojiViewHolder> {
        private final List<String> emojis = new ArrayList<>();

        public void setEmojis(List<String> list) {
            emojis.clear();
            if (list != null) {
                emojis.addAll(list);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public EmojiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            tv.setGravity(Gravity.CENTER);
            int height = dp(44);
            tv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
            return new EmojiViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull EmojiViewHolder holder, int position) {
            final String emoji = emojis.get(position);
            holder.textView.setText(emoji);
            holder.textView.setOnClickListener(v -> {
                addRecent(emoji);
                if (listener != null) {
                    listener.onEmojiSelected(emoji);
                }
                if (currentCategoryIndex == 0) {
                    setEmojis(new ArrayList<>(recentList));
                }
            });
        }

        @Override
        public int getItemCount() {
            return emojis.size();
        }
    }

    private static class EmojiViewHolder extends RecyclerView.ViewHolder {
        final TextView textView;
        public EmojiViewHolder(@NonNull View itemView) {
            super(itemView);
            this.textView = (TextView) itemView;
        }
    }
}
