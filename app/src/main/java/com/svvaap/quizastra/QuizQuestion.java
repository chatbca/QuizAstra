package com.svvaap.quizastra;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuizQuestion extends AppCompatActivity {

    private TextView tvTimer, tvQuestion, tvQNumber;
    private RadioGroup rgOptions;
    private RadioButton rbA, rbB, rbC, rbD;
    private Button btnNext;
    private ProgressBar progressBar;

    private List<Question> questions = new ArrayList<>();
    private int currentIndex = 0;
    private int score = 0; // total score accumulated
    private CountDownTimer timer;
    private long totalMillis; // total time for quiz

    private String category;
    private String battleChallengeId; // if this is a battle
    private boolean battleStartRecorded = false;

    // For review on results screen
    private final ArrayList<String> reviewQTexts = new ArrayList<>();
    private final ArrayList<String> reviewCorrectTexts = new ArrayList<>();
    private final ArrayList<String> reviewChosenTexts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Disable screenshots / screen recording
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_quiz_question);

        // Hide/disable home (up) button if action bar exists
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setHomeButtonEnabled(false);
            // Optionally hide the action bar for a focused exam experience
            // getSupportActionBar().hide();
        }

        bindViews();

        Intent intent = getIntent();
        category = intent.getStringExtra("category");
        battleChallengeId = intent.getStringExtra("battleChallengeId");
        // If this is a battle quiz, record the start time for time-taken computation
        if (battleChallengeId != null && !battleChallengeId.isEmpty() && !battleStartRecorded) {
            battleStartRecorded = true;
            SharedPreferences prefs = getSharedPreferences("profile_prefs", MODE_PRIVATE);
            String myUid = prefs.getString("id", "");
            String myName = prefs.getString("name", "");
            FirebaseDatabaseHelper.getInstance().recordBattleStart(
                    battleChallengeId, myUid, myName, System.currentTimeMillis(),
                    new FirebaseDatabaseHelper.SimpleCallback() {
                        @Override public void onSuccess() { /* no-op */ }
                        @Override public void onError(String error) { /* ignore */ }
                    }
            );
        }
        if (category == null || category.isEmpty()) {
            Toast.makeText(this, "No category selected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadQuestionsAndStart();
    }

    private void bindViews() {
        tvTimer = findViewById(R.id.timerTextView);
        tvQuestion = findViewById(R.id.questionText);
        tvQNumber = findViewById(R.id.questionNumberTextView);
        rgOptions = findViewById(R.id.optionsGroup);
        rbA = findViewById(R.id.optionA);
        rbB = findViewById(R.id.optionB);
        rbC = findViewById(R.id.optionC);
        rbD = findViewById(R.id.optionD);
        btnNext = findViewById(R.id.nextButton);
        progressBar = findViewById(R.id.progressBar);

        Button btnPrev = findViewById(R.id.previousButton);
        if (btnPrev != null) btnPrev.setVisibility(View.GONE);

        btnNext.setOnClickListener(v -> onNextClicked());

        // Extra guard: disable long-clicks and selection to prevent copy/paste
        View.OnLongClickListener consume = v -> true;
        tvTimer.setLongClickable(false);
        tvQuestion.setTextIsSelectable(false);
        tvQuestion.setLongClickable(false);
        tvQNumber.setTextIsSelectable(false);
        tvQNumber.setLongClickable(false);
        rbA.setLongClickable(false); rbA.setOnLongClickListener(consume);
        rbB.setLongClickable(false); rbB.setOnLongClickListener(consume);
        rbC.setLongClickable(false); rbC.setOnLongClickListener(consume);
        rbD.setLongClickable(false); rbD.setOnLongClickListener(consume);
        rgOptions.setLongClickable(false); rgOptions.setOnLongClickListener(consume);
        progressBar.setLongClickable(false);
    }

    private void loadQuestionsAndStart() {
        FirebaseDatabaseHelper.getInstance().getQuestionsByCategory(category, new FirebaseDatabaseHelper.QuestionsCallback() {
            @Override
            public void onQuestionsLoaded(List<Question> loaded) {
                if (loaded == null || loaded.isEmpty()) {
                    Toast.makeText(QuizQuestion.this, "No questions in this category", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                // Shuffle and pick up to 10
                Collections.shuffle(loaded);
                int limit = Math.min(10, loaded.size());
                questions = new ArrayList<>(loaded.subList(0, limit));

                // Total time: 1 minute per question
                totalMillis = limit * 60L * 1000L;
                startTimer(totalMillis);
                progressBar.setMax(limit);
                progressBar.setProgress(0);

                currentIndex = 0;
                score = 0;
                showCurrentQuestion();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(QuizQuestion.this, "Failed to load: " + error, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void startTimer(long durationMs) {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(durationMs, 1000) {
            @Override public void onTick(long millisUntilFinished) {
                tvTimer.setText(formatTime(millisUntilFinished));
            }
            @Override public void onFinish() { finishQuiz(); }
        }.start();
    }

    private String formatTime(long ms) {
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void showCurrentQuestion() {
        if (currentIndex >= questions.size()) {
            finishQuiz();
            return;
        }
        Question q = questions.get(currentIndex);
        tvQNumber.setText("Question " + (currentIndex + 1) + " of " + questions.size());
        tvQuestion.setText(q.getQuestionText());
        rbA.setText("A) " + q.getOptionA());
        rbB.setText("B) " + q.getOptionB());
        rbC.setText("C) " + q.getOptionC());
        rbD.setText("D) " + q.getOptionD());
        rgOptions.clearCheck();
        progressBar.setProgress(currentIndex);
    }

    private void onNextClicked() {
        // Evaluate current answer
        if (currentIndex < questions.size()) {
            int checkedId = rgOptions.getCheckedRadioButtonId();
            String chosen = null;
            if (checkedId == R.id.optionA) chosen = "A";
            else if (checkedId == R.id.optionB) chosen = "B";
            else if (checkedId == R.id.optionC) chosen = "C";
            else if (checkedId == R.id.optionD) chosen = "D";

            Question q = questions.get(currentIndex);
            String correct = q.getCorrectAnswer();
            // Resolve correct text from answer key (letter or text)
            String correctText = resolveAnswerTextFromKey(q, correct);
            String chosenText = chosen != null ? resolveAnswerTextFromKey(q, chosen) : "";

            boolean isCorrect = false;
            if (chosen != null) {
                // Compare by letter if key is letter, else by text
                if (isLetterKey(correct)) {
                    isCorrect = chosen.equalsIgnoreCase(correct);
                } else {
                    isCorrect = chosenText.equalsIgnoreCase(correct);
                }
            }
            if (isCorrect) score += 1; // 1 point per correct

            // Collect review info
            reviewQTexts.add(q.getQuestionText());
            reviewCorrectTexts.add(nonNull(correctText));
            reviewChosenTexts.add(nonNull(chosenText));
        }

        currentIndex++;
        if (currentIndex >= questions.size()) {
            finishQuiz();
        } else {
            showCurrentQuestion();
        }
    }

    private void finishQuiz() {
        if (timer != null) { timer.cancel(); timer = null; }
        // If this is a battle, record result before navigating
        if (battleChallengeId != null && !battleChallengeId.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences("profile_prefs", MODE_PRIVATE);
            String uid = prefs.getString("id", "");
            String name = prefs.getString("name", "You");
            int correctCount = score; // score equals number of correct answers
            long finishedAt = System.currentTimeMillis();
            FirebaseDatabaseHelper.getInstance().recordBattleResult(battleChallengeId, uid, name, correctCount, finishedAt, new FirebaseDatabaseHelper.SimpleCallback() {
                @Override public void onSuccess() { navigateToResult(); }
                @Override public void onError(String error) { navigateToResult(); }
            });
        } else {
            navigateToResult();
        }
    }

    private void navigateToResult() {
        Intent intent = new Intent(QuizQuestion.this, UserResult.class);
        intent.putExtra("score", score);
        intent.putExtra("totalQuestions", questions != null ? questions.size() : 0);
        intent.putExtra("category", category);
        if (battleChallengeId != null && !battleChallengeId.isEmpty()) {
            intent.putExtra("battleChallengeId", battleChallengeId);
        }
        intent.putStringArrayListExtra("reviewQTexts", reviewQTexts);
        intent.putStringArrayListExtra("reviewCorrectTexts", reviewCorrectTexts);
        intent.putStringArrayListExtra("reviewChosenTexts", reviewChosenTexts);
        startActivity(intent);
        finish();
    }

    private static boolean isLetterKey(String key) {
        if (key == null) return false;
        String k = key.trim();
        return k.equalsIgnoreCase("A") || k.equalsIgnoreCase("B") || k.equalsIgnoreCase("C") || k.equalsIgnoreCase("D");
    }

    private static String resolveAnswerTextFromKey(Question q, String keyOrText) {
        if (q == null || keyOrText == null) return "";
        String k = keyOrText.trim();
        if (k.equalsIgnoreCase("A")) return safe(q.getOptionA());
        if (k.equalsIgnoreCase("B")) return safe(q.getOptionB());
        if (k.equalsIgnoreCase("C")) return safe(q.getOptionC());
        if (k.equalsIgnoreCase("D")) return safe(q.getOptionD());
        return k; // assume it's already answer text
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String nonNull(String s) { return s == null ? "" : s; }
}