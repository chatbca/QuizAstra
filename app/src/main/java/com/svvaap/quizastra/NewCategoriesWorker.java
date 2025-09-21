package com.svvaap.quizastra;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NewCategoriesWorker extends Worker {

    private static final String PREFS = "notif_prefs";
    private static final String KEY_LAST_CAT_SIG = "last_cat_sig";

    public NewCategoriesWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        final CountDownLatch latch = new CountDownLatch(1);
        final Result[] out = new Result[]{Result.success()};

        // Fetch categories list once
        QuizAstraApp.getRoot().child("categories").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    List<String> names = new ArrayList<>();
                    for (DataSnapshot s : snapshot.getChildren()) {
                        Object n = s.child("name").getValue();
                        if (n != null) names.add(String.valueOf(n));
                    }
                    Collections.sort(names, String::compareToIgnoreCase);
                    String sig = names.toString(); // simple signature

                    SharedPreferences sp = getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                    String last = sp.getString(KEY_LAST_CAT_SIG, null);
                    if (last == null) {
                        // first run: store signature, don't notify
                        sp.edit().putString(KEY_LAST_CAT_SIG, sig).apply();
                    } else if (!last.equals(sig)) {
                        // Changed: detect if an addition exists
                        int oldCount = 0;
                        try { oldCount = (last.length() <= 2) ? 0 : last.split(",").length; } catch (Exception ignored) {}
                        int newCount = names.size();
                        if (newCount > oldCount) {
                            sendNewCategoryNotification(newCount - oldCount);
                        }
                        // Update signature regardless
                        sp.edit().putString(KEY_LAST_CAT_SIG, sig).apply();
                    }
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

        try { latch.await(12, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        return out[0];
    }

    private void sendNewCategoryNotification(int addedCount) {
        String title = "New categories available";
        String msg = addedCount > 1 ? (addedCount + " new categories to explore!") : "A new category has been added!";
        NotificationUtils.createChannels(getApplicationContext());
        NotificationManagerCompat.from(getApplicationContext())
                .notify(1001, NotificationUtils.builder(getApplicationContext(), NotificationUtils.CH_NEW_CATEGORIES, title, msg).build());
    }
}
