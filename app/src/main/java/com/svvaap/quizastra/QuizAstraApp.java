package com.svvaap.quizastra;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class QuizAstraApp extends Application {

    // Replace with your Realtime Database URL
    private static final String DATABASE_URL = "https://quizastra-330f6-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private static FirebaseDatabase firebaseDatabase;
    private static DatabaseReference rootRef;
    private ConnectivityManager.NetworkCallback networkCallback;
    private volatile boolean isConnected = true;

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize Firebase once for the entire app
        FirebaseApp.initializeApp(this);

        // Use specific DB URL to avoid default instance issues
        firebaseDatabase = FirebaseDatabase.getInstance(DATABASE_URL);

        // Optional but recommended: enable local disk persistence (must be before any references are used)
        try {
            firebaseDatabase.setPersistenceEnabled(true);
        } catch (Exception ignored) {
            // setPersistenceEnabled can only be called once; ignore if already enabled
        }

        rootRef = firebaseDatabase.getReference();
        try {
            // Keep critical leaderboard data in sync to minimize discrepancies across devices
            rootRef.child("users").keepSynced(true);
            rootRef.child("stats").keepSynced(true);
        } catch (Exception ignored) {}

        // Notifications: create channels
        NotificationUtils.createChannels(this);

        // Schedule background work
        scheduleWorkers(this);

        // Register activity lifecycle callbacks to know foreground activity
        registerActivityLifecycleCallbacks(lifecycleCallbacks);

        // Attempt to attach challenge observer if user already logged in
        attachChallengeObserverIfPossible();

        // Start monitoring network connectivity globally
        initConnectivityMonitor();
    }

    public static FirebaseDatabase getDatabase() {
        return firebaseDatabase != null ? firebaseDatabase : FirebaseDatabase.getInstance(DATABASE_URL);
    }

    public static DatabaseReference getRoot() {
        return rootRef != null ? rootRef : getDatabase().getReference();
    }

    private void scheduleWorkers(Context ctx) {
        WorkManager wm = WorkManager.getInstance(ctx);

        // New categories checker: every 15 minutes (minimum), requires network
        Constraints netConstraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest catWork = new PeriodicWorkRequest.Builder(NewCategoriesWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(netConstraints)
                .build();
        wm.enqueueUniquePeriodicWork("NewCategoriesWorker", ExistingPeriodicWorkPolicy.REPLACE, catWork);

        // Daily streak reminder: schedule around 7 PM local time
        long initialDelay = computeInitialDelayTo(19, 0); // 19:00
        PeriodicWorkRequest streakWork = new PeriodicWorkRequest.Builder(StreakReminderWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build();
        // Use REPLACE to refresh timing each app launch (ensures consistent target time)
        wm.enqueueUniquePeriodicWork("StreakReminderWorker", ExistingPeriodicWorkPolicy.REPLACE, streakWork);
    }

    private long computeInitialDelayTo(int hourOfDay, int minute) {
        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        next.set(Calendar.HOUR_OF_DAY, hourOfDay);
        next.set(Calendar.MINUTE, minute);
        if (!next.after(now)) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }
        return next.getTimeInMillis() - now.getTimeInMillis();
    }

    // ===================== Global Challenge Listener ===================== //
    private Activity currentActivity;
    private com.google.firebase.database.ValueEventListener challengesListener;
    private String challengesForUid;
    private final java.util.Set<String> shownChallengeIds = new java.util.HashSet<>();

    private final ActivityLifecycleCallbacks lifecycleCallbacks = new ActivityLifecycleCallbacks() {
        @Override public void onActivityCreated(@NonNull Activity activity, android.os.Bundle savedInstanceState) { }
        @Override public void onActivityStarted(@NonNull Activity activity) { }
        @Override public void onActivityResumed(@NonNull Activity activity) {
            currentActivity = activity;
            // Re-attach if needed when user changes (login/logout)
            attachChallengeObserverIfPossible();
            // Reflect current connectivity on resume
            updateNoInternetBanner(activity, !isConnected);
        }
        @Override public void onActivityPaused(@NonNull Activity activity) { }
        @Override public void onActivityStopped(@NonNull Activity activity) { }
        @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull android.os.Bundle outState) { }
        @Override public void onActivityDestroyed(@NonNull Activity activity) {
            if (currentActivity == activity) currentActivity = null;
            removeNoInternetBanner(activity);
        }
    };

    private void attachChallengeObserverIfPossible() {
        SharedPreferences prefs = getSharedPreferences("profile_prefs", MODE_PRIVATE);
        String myUid = prefs.getString("id", null);
        if (myUid == null || myUid.isEmpty()) return;
        if (challengesListener != null && myUid.equals(challengesForUid)) return; // already attached

        // Detach previous if different user
        if (challengesListener != null && challengesForUid != null) {
            FirebaseDatabaseHelper.getInstance().removeIncomingChallengesObserver(challengesForUid, challengesListener);
            challengesListener = null;
        }

        challengesForUid = myUid;
        challengesListener = FirebaseDatabaseHelper.getInstance().observeIncomingChallenges(myUid, new FirebaseDatabaseHelper.ChallengeListener() {
            @Override public void onChallenge(FirebaseDatabaseHelper.Challenge c) {
                if (c == null || c.id == null || c.id.isEmpty()) return;
                if (shownChallengeIds.contains(c.id)) return; // avoid duplicate popups
                shownChallengeIds.add(c.id);
                // Always send a system notification
                sendChallengeNotification(c);
                // If app is in foreground and not in an active quiz, show a dialog
                Activity a = currentActivity;
                if (a != null && !(a instanceof QuizQuestion)) {
                    a.runOnUiThread(() -> showChallengeDialog(a, c));
                }
            }
            @Override public void onError(String error) { /* no-op */ }
        });
    }

    private void showChallengeDialog(@NonNull Activity activity, @NonNull FirebaseDatabaseHelper.Challenge c) {
        String title = "Challenge from " + (c.fromName == null ? "User" : c.fromName);
        String msg = "Category: " + (c.categoryName == null ? c.category : c.categoryName);
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(msg)
                .setCancelable(true)
                .setPositiveButton("Accept", (d, which) -> acceptChallenge(activity, c))
                .setNegativeButton("Decline", (d, which) -> declineChallenge(c))
                .show();
    }

    private void acceptChallenge(@NonNull Context ctx, @NonNull FirebaseDatabaseHelper.Challenge c) {
        FirebaseDatabaseHelper.getInstance().respondToChallenge(c.toUid, c.id, true, new FirebaseDatabaseHelper.SimpleCallback() {
            @Override public void onSuccess() {
                Intent i = new Intent(ctx, BattleCountdownActivity.class);
                i.putExtra("category", c.category);
                i.putExtra("battleChallengeId", c.id);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            }
            @Override public void onError(String error) { /* optionally toast */ }
        });
    }

    private void declineChallenge(@NonNull FirebaseDatabaseHelper.Challenge c) {
        FirebaseDatabaseHelper.getInstance().respondToChallenge(c.toUid, c.id, false, new FirebaseDatabaseHelper.SimpleCallback() {
            @Override public void onSuccess() { /* no-op */ }
            @Override public void onError(String error) { /* no-op */ }
        });
    }

    private void sendChallengeNotification(@NonNull FirebaseDatabaseHelper.Challenge c) {
        Intent open = new Intent(this, BattleActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, c.id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        String title = "Incoming challenge";
        String text = (c.fromName == null ? "A user" : c.fromName) + " challenged you" + (c.categoryName != null ? (" • " + c.categoryName) : "");
        nm.notify(c.id.hashCode(), NotificationUtils.builder(this, NotificationUtils.CH_CHALLENGES, title, text).setContentIntent(pi).build());
    }

    // ===================== Global Connectivity Banner ===================== //
    private void initConnectivityMonitor() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        // Seed initial state
        isConnected = isCurrentlyConnected(cm);

        NetworkRequest req = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                isConnected = true;
                Activity a = currentActivity;
                if (a != null) a.runOnUiThread(() -> updateNoInternetBanner(a, false));
            }

            @Override
            public void onLost(@NonNull Network network) {
                // Might still have another network, re-evaluate
                boolean nowConnected = isCurrentlyConnected(cm);
                isConnected = nowConnected;
                Activity a = currentActivity;
                if (a != null) a.runOnUiThread(() -> updateNoInternetBanner(a, !nowConnected));
            }
        };
        cm.registerNetworkCallback(req, networkCallback);
    }

    private boolean isCurrentlyConnected(@NonNull ConnectivityManager cm) {
        Network active = cm.getActiveNetwork();
        if (active == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private void updateNoInternetBanner(@NonNull Activity activity, boolean show) {
        if (activity.isFinishing()) return;
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        View existing = root.findViewById(R.id.no_internet_banner);
        if (show) {
            if (existing == null) {
                View banner = LayoutInflater.from(activity).inflate(R.layout.view_no_internet_banner, root, false);
                // Place at the very top
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                lp.topMargin = 0;
                ((View) banner).setLayoutParams(lp);
                // Add as first child so it appears on top
                if (root instanceof FrameLayout) {
                    ((FrameLayout) root).addView(banner, 0);
                } else {
                    root.addView(banner, 0);
                }
            } else {
                existing.setVisibility(View.VISIBLE);
            }
        } else {
            if (existing != null) existing.setVisibility(View.GONE);
        }
    }

    private void removeNoInternetBanner(@NonNull Activity activity) {
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        View existing = root.findViewById(R.id.no_internet_banner);
        if (existing != null) root.removeView(existing);
    }
}
