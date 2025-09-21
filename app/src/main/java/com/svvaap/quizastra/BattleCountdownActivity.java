package com.svvaap.quizastra;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class BattleCountdownActivity extends AppCompatActivity {

    private TextView tvTitle, tvCountdown;
    private String category;
    private String challengeId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Fullscreen immersive feel
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_battle_countdown);

        tvTitle = findViewById(R.id.tvBattleBeginsTitle);
        tvCountdown = findViewById(R.id.tvBattleCountdown);

        Intent src = getIntent();
        category = src != null ? src.getStringExtra("category") : null;
        challengeId = src != null ? src.getStringExtra("battleChallengeId") : null;

        // 10-second countdown
        new CountDownTimer(10_000, 1_000) {
            @Override public void onTick(long millisUntilFinished) {
                long sec = millisUntilFinished / 1000L;
                tvCountdown.setText(String.valueOf(sec));
            }
            @Override public void onFinish() {
                launchQuiz();
            }
        }.start();
    }

    private void launchQuiz() {
        Intent i = new Intent(BattleCountdownActivity.this, QuizQuestion.class);
        i.putExtra("category", category);
        i.putExtra("battleChallengeId", challengeId);
        startActivity(i);
        finish();
    }
}
