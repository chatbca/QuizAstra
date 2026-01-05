package svvaap.quizastra;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class UserResult extends AppCompatActivity {

    private com.google.firebase.database.ValueEventListener battleResultsListener;
    private String battleChallengeIdField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_result);

        BottomNavigationView navView = findViewById(R.id.bottomNav);

        // Action buttons
        Button btnLeaderboard = findViewById(R.id.btnLeaderboard);
        Button btnBack = findViewById(R.id.btnBack);

        if (btnLeaderboard != null) {
            btnLeaderboard.setOnClickListener(v -> {
                Intent intent = new Intent(UserResult.this, LeaderboardActivity.class);
                startActivity(intent);
            });
        }


        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                Intent intent = new Intent(UserResult.this, UserDashboard.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }

        navView.setSelectedItemId(R.id.nav_home);
        BottomNavHandler.setup(this, navView); // Assuming BottomNavHandler is implemented

        // Read result data
        Intent src = getIntent();
        int score = src != null ? src.getIntExtra("score", -1) : -1;
        int total = src != null ? src.getIntExtra("totalQuestions", -1) : -1;
        if (score < 0 && src != null && src.hasExtra("totalScore")) {
            score = src.getIntExtra("totalScore", -1);
        }

        // Update tiles: Score and Accuracy
        TextView tvScore = findViewById(R.id.tvScore);
        TextView tvAccuracy = findViewById(R.id.tvAccuracy);
        TextView tvCorrectWrong = findViewById(R.id.tvCorrectWrong);
        if (score >= 0 && total > 0) {
            tvScore.setText("Score\n" + score + "/" + total);
            int correct = score;
            int wrong = Math.max(0, total - correct);
            double accuracy = (correct * 100.0) / total;
            tvAccuracy.setText(String.format("Accuracy\n%.0f%%", accuracy));
            if (tvCorrectWrong != null) {
                tvCorrectWrong.setText("Correct: " + correct + "   Wrong: " + wrong);
            }
        }

        // Battle banner if applicable
        String battleChallengeId = src != null ? src.getStringExtra("battleChallengeId") : null;
        battleChallengeIdField = battleChallengeId;
        TextView tvBattleBanner = findViewById(R.id.tvBattleBanner);
        if (battleChallengeId != null && !battleChallengeId.isEmpty() && tvBattleBanner != null) {
            tvBattleBanner.setVisibility(View.VISIBLE);
            SharedPreferences prefs = getSharedPreferences("profile_prefs", MODE_PRIVATE);
            String myUid = prefs.getString("id", "");
            battleResultsListener = new com.google.firebase.database.ValueEventListener() {
                @Override public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snapshot) {
                    java.util.Map<String, Object> resultsRaw = (java.util.Map<String, Object>) snapshot.getValue();
                    if (resultsRaw == null || resultsRaw.size() < 2) {
                        tvBattleBanner.setText("Waiting for opponent to finish...");
                        return;
                    }
                    // Collect exactly two player results (ignore any metadata nodes)
                    java.util.List<java.util.Map<String, Object>> players = new java.util.ArrayList<>(2);
                    for (java.util.Map.Entry<String, Object> e : resultsRaw.entrySet()) {
                        Object v = e.getValue();
                        if (!(v instanceof java.util.Map)) continue;
                        java.util.Map<String, Object> m = (java.util.Map<String, Object>) v;
                        Object uidObj = m.get("uid");
                        if (uidObj == null) continue;
                        String uid = String.valueOf(uidObj);
                        if (uid == null || uid.isEmpty()) continue;
                        players.add(m);
                    }
                    if (players.size() < 2) { tvBattleBanner.setText("Waiting for opponent to finish..."); return; }

                    java.util.Map<String, Object> p1 = players.get(0);
                    java.util.Map<String, Object> p2 = players.get(1);
                    String p1Uid = String.valueOf(p1.get("uid"));
                    String p1Name = String.valueOf(p1.get("name"));
                    int p1Correct = toInt(p1.get("correctCount"));
                    long p1Finish = toLong(p1.get("finishedAt"));
                    long p1Start = toLong(p1.get("startedAt"));
                    long p1Time = (p1Start > 0 && p1Finish > 0) ? (p1Finish - p1Start) : -1L;

                    String p2Uid = String.valueOf(p2.get("uid"));
                    String p2Name = String.valueOf(p2.get("name"));
                    int p2Correct = toInt(p2.get("correctCount"));
                    long p2Finish = toLong(p2.get("finishedAt"));
                    long p2Start = toLong(p2.get("startedAt"));
                    long p2Time = (p2Start > 0 && p2Finish > 0) ? (p2Finish - p2Start) : -1L;

                    // Do not declare a winner until both have finished
                    if (p1Finish <= 0 || p2Finish <= 0) {
                        tvBattleBanner.setText("Waiting for opponent to finish...");
                        return;
                    }

                    // Decide winner globally (not perspective-based)
                    String winnerUid;
                    String winnerName;
                    String loserUid;
                    String loserName;
                    String reason;
                    if (p1Correct != p2Correct) {
                        boolean p1Wins = p1Correct > p2Correct;
                        winnerUid = p1Wins ? p1Uid : p2Uid;
                        winnerName = p1Wins ? p1Name : p2Name;
                        loserUid = p1Wins ? p2Uid : p1Uid;
                        loserName = p1Wins ? p2Name : p1Name;
                        reason = "higher score";
                    } else if (p1Time >= 0 && p2Time >= 0 && p1Time != p2Time) {
                        boolean p1Wins = p1Time < p2Time;
                        winnerUid = p1Wins ? p1Uid : p2Uid;
                        winnerName = p1Wins ? p1Name : p2Name;
                        loserUid = p1Wins ? p2Uid : p1Uid;
                        loserName = p1Wins ? p2Name : p1Name;
                        reason = "faster time";
                    } else if (p1Finish != p2Finish) {
                        boolean p1Wins = p1Finish < p2Finish;
                        winnerUid = p1Wins ? p1Uid : p2Uid;
                        winnerName = p1Wins ? p1Name : p2Name;
                        loserUid = p1Wins ? p2Uid : p1Uid;
                        loserName = p1Wins ? p2Name : p1Name;
                        reason = "earlier finish";
                    } else {
                        tvBattleBanner.setText("Draw! Both scored " + p1Correct);
                        return;
                    }

                    // Apply outcome to Firebase exactly once per battle across both devices
                    try {
                        FirebaseDatabaseHelper.getInstance().applyBattleOutcomeOnce(battleChallengeIdField, winnerUid, loserUid, new FirebaseDatabaseHelper.DatabaseCallback() {
                            @Override public void onSuccess(String message) { /* no-op */ }
                            @Override public void onError(String error) { /* ignore duplicate or transient errors */ }
                        });
                    } catch (Exception ignored) {}

                    boolean iAmWinner = (myUid != null && !myUid.isEmpty()) && myUid.equals(winnerUid);
                    boolean iAmLoser = (myUid != null && !myUid.isEmpty()) && myUid.equals(loserUid);
                    String msg;
                    if (iAmWinner) msg = "You won against " + loserName + "  •  " + reason;
                    else if (iAmLoser) msg = "You lost to " + winnerName + "  •  " + reason;
                    else msg = "Winner: " + winnerName + "  •  " + reason;
                    tvBattleBanner.setText(msg);
                }
                @Override public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError error) {
                    tvBattleBanner.setText("Battle result unavailable");
                }
            };
            FirebaseDatabaseHelper.getInstance().observeBattleResults(battleChallengeId, battleResultsListener);
        }

        // Build review list
        LinearLayout reviewContainer = findViewById(R.id.reviewContainer);
        if (reviewContainer != null && src != null) {
            java.util.ArrayList<String> qTexts = src.getStringArrayListExtra("reviewQTexts");
            java.util.ArrayList<String> correctTexts = src.getStringArrayListExtra("reviewCorrectTexts");
            java.util.ArrayList<String> chosenTexts = src.getStringArrayListExtra("reviewChosenTexts");
            if (qTexts != null && correctTexts != null && chosenTexts != null) {
                int n = Math.min(qTexts.size(), Math.min(correctTexts.size(), chosenTexts.size()));
                for (int i = 0; i < n; i++) {
                    String q = safe(qTexts.get(i));
                    String c = safe(correctTexts.get(i));
                    String ch = safe(chosenTexts.get(i));
                    boolean isCorrect = !ch.isEmpty() && ch.equalsIgnoreCase(c);
                    reviewContainer.addView(buildReviewItem(i + 1, q, isCorrect, c, ch));
                }
            }
        }

        // Update stats automatically (category-aware if provided)
        if (score >= 0) {
            SharedPreferences prefs = getSharedPreferences("profile_prefs", MODE_PRIVATE);
            String userId = prefs.getString("id", null);
            if (userId != null && !userId.isEmpty()) {
                String category = src != null ? src.getStringExtra("category") : null;
                if (category != null && !category.trim().isEmpty()) {
                    FirebaseDatabaseHelper.getInstance().updateUserStatsAccumulatingWithCategory(userId, score, category, new FirebaseDatabaseHelper.DatabaseCallback() {
                        @Override public void onSuccess(String message) { /* no-op */ }
                        @Override public void onError(String error) {
                            Toast.makeText(UserResult.this, "Failed to update stats", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    FirebaseDatabaseHelper.getInstance().updateUserStatsAccumulating(userId, score, new FirebaseDatabaseHelper.DatabaseCallback() {
                        @Override public void onSuccess(String message) { /* no-op */ }
                        @Override public void onError(String error) {
                            Toast.makeText(UserResult.this, "Failed to update stats", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }
    }

    private View buildReviewItem(int number, String question, boolean isCorrect, String correctText, String chosenText) {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(20), dp(20));
        ip.setMarginEnd(dp(8));
        icon.setLayoutParams(ip);
        icon.setImageResource(isCorrect ? android.R.drawable.checkbox_on_background : android.R.drawable.ic_delete);
        row.addView(icon);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvQ = new TextView(this);
        tvQ.setText("Q" + number + ". " + question);
        tvQ.setTextColor(0xFFFFFFFF);
        tvQ.setTextSize(16);
        tvQ.setTypeface(tvQ.getTypeface(), android.graphics.Typeface.BOLD);
        textCol.addView(tvQ);

        TextView tvStatus = new TextView(this);
        if (isCorrect) {
            tvStatus.setText("Correct");
        } else {
            String you = chosenText == null || chosenText.isEmpty() ? "Not answered" : ("You: " + chosenText);
            tvStatus.setText("Wrong  •  Correct: " + correctText + "  •  " + you);
        }
        tvStatus.setTextColor(0x88FFFFFF);
        tvStatus.setTextSize(14);
        textCol.addView(tvStatus);

        row.addView(textCol);
        return row;
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return (int) (v * d);
    }

    private static String safe(String s) { return s == null ? "" : s; }

    // Helpers to parse numeric values from Firebase maps safely
    private static int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
    }

    private static long toLong(Object o) {
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return 0L; }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (battleResultsListener != null && battleChallengeIdField != null && !battleChallengeIdField.isEmpty()) {
            FirebaseDatabaseHelper.getInstance().removeBattleResultsObserver(battleChallengeIdField, battleResultsListener);
            battleResultsListener = null;
        }
    }
}
