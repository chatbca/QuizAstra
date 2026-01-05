package svvaap.quizastra;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class StreakReminderWorker extends Worker {

    public StreakReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Get current user
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("profile_prefs", Context.MODE_PRIVATE);
        String userId = prefs.getString("id", null);
        if (userId == null || userId.isEmpty()) {
            return Result.success(); // no user logged in, skip
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final Result[] out = new Result[]{Result.success()};

        // Fetch user's current streak to personalize message
        QuizAstraApp.getRoot().child("stats").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot s) {
                try {
                    int currentStreak = 0;
                    Object cs = s.child("currentStreak").getValue();
                    if (cs instanceof Long) currentStreak = ((Long) cs).intValue();
                    else if (cs instanceof Integer) currentStreak = (Integer) cs;

                    sendStreakNotification(currentStreak);
                    out[0] = Result.success();
                } catch (Exception e) {
                    out[0] = Result.retry();
                } finally {
                    latch.countDown();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                out[0] = Result.retry();
                latch.countDown();
            }
        });

        try { latch.await(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        return out[0];
    }

    private void sendStreakNotification(int currentStreak) {
        String title = "Keep your streak alive!";
        String msg = currentStreak > 0
                ? ("You're on a " + currentStreak + "-day streak. Take a quiz today!")
                : "Start your learning streak today!";
        NotificationUtils.createChannels(getApplicationContext());
        NotificationManagerCompat.from(getApplicationContext())
                .notify(1002, NotificationUtils.builder(getApplicationContext(), NotificationUtils.CH_STREAK_REMINDER, title, msg).build());
    }
}
