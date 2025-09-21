package com.svvaap.quizastra;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotificationUtils {
    public static final String CH_NEW_CATEGORIES = "new_categories";
    public static final String CH_STREAK_REMINDER = "streak_reminder";
    public static final String CH_CHALLENGES = "challenges";

    public static void createChannels(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            NotificationChannel chNew = new NotificationChannel(
                    CH_NEW_CATEGORIES,
                    "New Categories",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            chNew.setDescription("Alerts when new quiz categories are available");
            chNew.enableLights(true);
            chNew.setLightColor(Color.CYAN);
            nm.createNotificationChannel(chNew);

            NotificationChannel chStreak = new NotificationChannel(
                    CH_STREAK_REMINDER,
                    "Streak Reminder",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            chStreak.setDescription("Daily reminder to keep your quiz streak alive");
            chStreak.enableLights(true);
            chStreak.setLightColor(Color.YELLOW);
            nm.createNotificationChannel(chStreak);

            NotificationChannel chChallenges = new NotificationChannel(
                    CH_CHALLENGES,
                    "Challenges",
                    NotificationManager.IMPORTANCE_HIGH
            );
            chChallenges.setDescription("Incoming quiz battle challenges");
            chChallenges.enableLights(true);
            chChallenges.setLightColor(Color.GREEN);
            nm.createNotificationChannel(chChallenges);
        }
    }

    public static NotificationCompat.Builder builder(Context ctx, String channelId, String title, String text) {
        return new NotificationCompat.Builder(ctx, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
    }
}
