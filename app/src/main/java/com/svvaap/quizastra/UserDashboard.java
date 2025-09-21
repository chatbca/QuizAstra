package com.svvaap.quizastra;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UserDashboard extends AppCompatActivity {

    private TextView welcomeText, textStatQuizzes, textStatBest, textStatStreak;
    private Spinner categorySpinner;
    private RecyclerView recyclerCategories;
    private LinearLayout actionLeaderboard, actionProfile;
    private Button startQuizBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_dashboard);

        bindViews();
        setupWelcome();
        setupStats();
        setupCategorySpinner();
        setupCategoryRecycler();
        setupActions();

        startQuizBtn = findViewById(R.id.start_quiz);
        startQuizBtn.setOnClickListener(v -> {
            String selected = categorySpinner.getSelectedItem() != null ? categorySpinner.getSelectedItem().toString() : null;
            Intent i = new Intent(UserDashboard.this, QuizQuestion.class);
            if (selected != null) i.putExtra("category", selected);
            startActivity(i);
        });

        findViewById(R.id.btnLogout).setOnClickListener(v -> doLogout());

        BottomNavigationView navView = findViewById(R.id.bottomNav);
        navView.setSelectedItemId(R.id.nav_home);
        BottomNavHandler.setup(this, navView);
    }

    private void bindViews() {
        welcomeText = findViewById(R.id.welcome_text);
        textStatQuizzes = findViewById(R.id.textStatQuizzes);
        textStatBest = findViewById(R.id.textStatBest);
        textStatStreak = findViewById(R.id.textStatStreak);
        categorySpinner = findViewById(R.id.categorySpinner);
        recyclerCategories = findViewById(R.id.recyclerCategories);
        actionLeaderboard = findViewById(R.id.actionLeaderboard);
        actionProfile = findViewById(R.id.actionProfile);
    }

    private void setupWelcome() {
        SharedPreferences prefs = getSharedPreferences("profile_prefs", MODE_PRIVATE);
        String name = prefs.getString("name", "User");
        welcomeText.setText("Welcome, " + name);
    }

    private void setupStats() {
        // Load stats for the logged-in user
        SharedPreferences prefs = getSharedPreferences("profile_prefs", MODE_PRIVATE);
        String userId = prefs.getString("id", null);
        if (userId == null || userId.isEmpty()) {
            // No session; keep defaults as 0
            textStatQuizzes.setText("0");
            textStatBest.setText("0");
            textStatStreak.setText("0");
            return;
        }

        FirebaseDatabaseHelper.getInstance().getUserStats(userId, new FirebaseDatabaseHelper.UserStatsCallback() {
            @Override
            public void onStatsLoaded(FirebaseDatabaseHelper.UserStats stats) {
                if (stats.totalQuizzes <= 0) {
                    // New user: hide stats by clearing text
                    textStatQuizzes.setText("");
                    textStatBest.setText("");
                    textStatStreak.setText("");
                } else {
                    textStatQuizzes.setText(String.valueOf(stats.totalQuizzes));
                    // bestScore is used as TOTAL accumulated score across quizzes
                    textStatBest.setText(String.valueOf(stats.bestScore));
                    textStatStreak.setText(String.valueOf(stats.currentStreak));
                }
            }

            @Override
            public void onError(String error) {
                // On error, show zeros but keep UI responsive
                textStatQuizzes.setText("0");
                textStatBest.setText("0");
                textStatStreak.setText("0");
            }
        });
    }

    private void setupCategorySpinner() {
        // Load from Realtime Database
        FirebaseDatabaseHelper.getInstance().getAllCategories(new FirebaseDatabaseHelper.CategoriesCallback() {
            @Override public void onCategoriesLoaded(List<Category> categories) {
                List<String> names = new ArrayList<>();
                for (Category c : categories) names.add(c.getName());
                ArrayAdapter<String> adapter = new ArrayAdapter<>(UserDashboard.this, android.R.layout.simple_spinner_dropdown_item, names);
                categorySpinner.setAdapter(adapter);
            }
            @Override public void onError(String error) {
                // Fall back to a small static list on error
                List<String> fallback = Arrays.asList("General Knowledge", "Science");
                ArrayAdapter<String> adapter = new ArrayAdapter<>(UserDashboard.this, android.R.layout.simple_spinner_dropdown_item, fallback);
                categorySpinner.setAdapter(adapter);
            }
        });
    }

    private void setupCategoryRecycler() {
        recyclerCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        // Load from Realtime Database
        FirebaseDatabaseHelper.getInstance().getAllCategories(new FirebaseDatabaseHelper.CategoriesCallback() {
            @Override public void onCategoriesLoaded(List<Category> categories) {
                List<String> names = new ArrayList<>();
                for (Category c : categories) names.add(c.getName());
                recyclerCategories.setAdapter(new SimpleCategoryAdapter(names));
            }
            @Override public void onError(String error) {
                List<String> popular = new ArrayList<>(Arrays.asList("Math", "Geography", "Movies", "Music", "Coding"));
                recyclerCategories.setAdapter(new SimpleCategoryAdapter(popular));
            }
        });
    }

    private void setupActions() {
        actionLeaderboard.setOnClickListener(v -> startActivity(new Intent(this, LeaderboardActivity.class)));
        actionProfile.setOnClickListener(v -> startActivity(new Intent(this, UserProfile.class)));
    }

    private void doLogout() {
        // Sign out Firebase and clear local prefs
        try { FirebaseDatabaseHelper.getInstance().logout(); } catch (Exception ignored) {}
        getSharedPreferences("profile_prefs", MODE_PRIVATE).edit().clear().apply();
        Intent intent = new Intent(this, UserLogin.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
