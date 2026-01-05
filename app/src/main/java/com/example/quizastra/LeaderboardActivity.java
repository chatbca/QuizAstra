package com.example.quizastra;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.text.TextUtils;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;

public class LeaderboardActivity extends AppCompatActivity {

    ImageView avatar1, avatar2, avatar3;
    TextView user1name, user2name, user3name, tvMyPosition;
    RecyclerView recyclerView;
    LeaderboardAdapter adapter;
    List<FirebaseDatabaseHelper.LeaderboardEntry> allEntries = new ArrayList<>();
    Spinner spinnerCategory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        avatar1 = findViewById(R.id.avatar1);
        avatar2 = findViewById(R.id.avatar2);
        avatar3 = findViewById(R.id.avatar3);
        user1name = findViewById(R.id.user1name);
        user2name = findViewById(R.id.user2name);
        user3name = findViewById(R.id.user3name);
        tvMyPosition = findViewById(R.id.tvMyPosition);
        recyclerView = findViewById(R.id.leaderboardRecycler);
        spinnerCategory = findViewById(R.id.spinnerCategory);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LeaderboardAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        setupCategorySpinnerAndLoad();

        BottomNavigationView navView = findViewById(R.id.bottomNav);
        navView.setSelectedItemId(R.id.nav_leaderboard);
        BottomNavHandler.setup(this, navView);
    }

    private void setupCategorySpinnerAndLoad() {
        // Load categories into spinner with an "Overall" first option
        FirebaseDatabaseHelper.getInstance().getAllCategories(new FirebaseDatabaseHelper.CategoriesCallback() {
            @Override
            public void onCategoriesLoaded(List<Category> categories) {
                List<String> items = new ArrayList<>();
                items.add("Overall");
                for (Category c : categories) items.add(c.getName());
                ArrayAdapter<String> adapter = new ArrayAdapter<>(LeaderboardActivity.this, android.R.layout.simple_spinner_dropdown_item, items);
                spinnerCategory.setAdapter(adapter);

                spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        String sel = (String) parent.getItemAtPosition(position);
                        if (sel == null || sel.equals("Overall")) {
                            loadOverall();
                        } else {
                            loadByCategory(sel);
                        }
                    }
                    @Override public void onNothingSelected(AdapterView<?> parent) { }
                });
            }

            @Override
            public void onError(String error) {
                // Fallback to Overall only
                ArrayAdapter<String> adapter = new ArrayAdapter<>(LeaderboardActivity.this, android.R.layout.simple_spinner_dropdown_item, Collections.singletonList("Overall"));
                spinnerCategory.setAdapter(adapter);
                loadOverall();
            }
        });
    }

    private void loadOverall() {
        FirebaseDatabaseHelper.getInstance().getAllUserStatsWithUsers(new FirebaseDatabaseHelper.LeaderboardCallback() {
            @Override public void onLoaded(List<FirebaseDatabaseHelper.LeaderboardEntry> entries) {
                applyLeaderboard(entries);
            }
            @Override public void onError(String error) { /* consider showing a toast */ }
        });
    }

    private void loadByCategory(String category) {
        FirebaseDatabaseHelper.getInstance().getLeaderboardByCategory(category, new FirebaseDatabaseHelper.LeaderboardCallback() {
            @Override public void onLoaded(List<FirebaseDatabaseHelper.LeaderboardEntry> entries) {
                applyLeaderboard(entries);
            }
            @Override public void onError(String error) { /* consider showing a toast */ }
        });
    }

    private void applyLeaderboard(List<FirebaseDatabaseHelper.LeaderboardEntry> entries) {
        if (entries == null) entries = new ArrayList<>();
        // Sort deterministically to match admin:
        // 1) points desc; 2) totalQuizzes/attempts desc; 3) name (Locale.ROOT, case-insensitive); 4) userId
        Collections.sort(entries, new Comparator<FirebaseDatabaseHelper.LeaderboardEntry>() {
            @Override public int compare(FirebaseDatabaseHelper.LeaderboardEntry a, FirebaseDatabaseHelper.LeaderboardEntry b) {
                int p = Integer.compare(b.points, a.points);
                if (p != 0) return p;
                int tq = Integer.compare(b.totalQuizzes, a.totalQuizzes);
                if (tq != 0) return tq;
                String an = a.name == null ? "" : a.name.toLowerCase(java.util.Locale.ROOT);
                String bn = b.name == null ? "" : b.name.toLowerCase(java.util.Locale.ROOT);
                int n = an.compareTo(bn);
                if (n != 0) return n;
                String au = a.userId == null ? "" : a.userId;
                String bu = b.userId == null ? "" : b.userId;
                return au.compareTo(bu);
            }
        });

        allEntries = entries;

        // Top 3
        String n1 = entries.size() > 0 ? safe(entries.get(0).name) : "-";
        String n2 = entries.size() > 1 ? safe(entries.get(1).name) : "-";
        String n3 = entries.size() > 2 ? safe(entries.get(2).name) : "-";
        user1name.setText(n1);
        user2name.setText(n2);
        user3name.setText(n3);
        // Show user avatars if available; otherwise keep default avatar (medals are overlaid in layout)
        if (entries.size() > 0 && !setAvatarFromBase64(avatar1, entries.get(0).photoBase64)) {
            avatar1.setImageResource(R.drawable.avatar_default);
        }
        if (entries.size() > 1 && !setAvatarFromBase64(avatar2, entries.get(1).photoBase64)) {
            avatar2.setImageResource(R.drawable.avatar_default);
        }
        if (entries.size() > 2 && !setAvatarFromBase64(avatar3, entries.get(2).photoBase64)) {
            avatar3.setImageResource(R.drawable.avatar_default);
        }

        // Remaining list starting from index 3
        List<FirebaseDatabaseHelper.LeaderboardEntry> remaining = new ArrayList<>();
        if (entries.size() > 3) remaining = entries.subList(3, entries.size());
        adapter = new LeaderboardAdapter(remaining);
        recyclerView.setAdapter(adapter);

        updateMyPosition(entries);
    }

    /**
     * Decode a Base64 image string and set it to the provided ImageView.
     * Returns true if successfully set, false if invalid/empty.
     */
    private boolean setAvatarFromBase64(ImageView iv, String b64) {
        try {
            if (iv == null || TextUtils.isEmpty(b64)) return false;
            String clean = b64;
            if (b64.startsWith("data:image")) {
                int idx = b64.indexOf(',');
                if (idx != -1) clean = b64.substring(idx + 1);
            }
            byte[] bytes = Base64.decode(clean, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp != null) {
                iv.setImageBitmap(bmp);
                return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private void updateMyPosition(List<FirebaseDatabaseHelper.LeaderboardEntry> entries) {
        if (tvMyPosition == null) return;
        SharedPreferences prefs = getSharedPreferences("profile_prefs", MODE_PRIVATE);
        String userId = prefs.getString("id", null);
        if (userId == null || userId.isEmpty()) {
            tvMyPosition.setText("Your position: -  •  Points: -");
            return;
        }
        int pos = -1;
        int pts = 0;
        for (int i = 0; i < entries.size(); i++) {
            FirebaseDatabaseHelper.LeaderboardEntry e = entries.get(i);
            if (userId.equals(e.userId)) {
                pos = i + 1; // 1-based rank including top3
                pts = e.points;
                break;
            }
        }
        if (pos <= 0) {
            tvMyPosition.setText("Your position: -  •  Points: " + pts);
        } else {
            tvMyPosition.setText("Your position: " + pos + "  •  Points: " + pts);
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
