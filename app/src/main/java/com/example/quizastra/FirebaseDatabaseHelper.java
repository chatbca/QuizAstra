package com.example.quizastra;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Firebase helper for the USER app. Focused on:
 * - register/login (Realtime DB only, no FirebaseAuth)
 * - users CRUD (basic fields)
 * - categories list (with optional questionCount)
 * - questions list and by category
 */
public class FirebaseDatabaseHelper {

    private static FirebaseDatabaseHelper instance;
    private final DatabaseReference rootRef;

    // Database paths
    private static final String QUESTIONS_PATH = "questions";
    private static final String CATEGORIES_PATH = "categories";
    private static final String USERS_PATH = "users";
    private static final String STATS_PATH = "stats"; // per-user stats: stats/{userId}
    private static final String PRESENCE_PATH = "presence"; // presence/{uid}: { online: bool, lastSeen: long }
    private static final String CHALLENGES_PATH = "challenges"; // challenges/{targetUid}/{challengeId} -> Challenge

    private FirebaseDatabaseHelper() {
        // Use the same instance configured in QuizAstraApp
        this.rootRef = QuizAstraApp.getRoot();
    }

    // (Removed getUserStatsSimple in favor of existing getUserStats(UserStatsCallback))

    /** Load leaderboard by category reading stats/{uid}/byCategory/{category}/points. */
    public void getLeaderboardByCategory(@NonNull String category, @NonNull LeaderboardCallback callback) {
        rootRef.child(USERS_PATH).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot usersSnap) {
                final Map<String, String> idToName = new HashMap<>();
                final Map<String, String> idToPhoto = new HashMap<>();
                for (DataSnapshot u : usersSnap.getChildren()) {
                    String id = Objects.toString(u.child("id").getValue(), u.getKey());
                    String nm = Objects.toString(u.child("name").getValue(), "User");
                    String pb64 = Objects.toString(u.child("photoBase64").getValue(), "");
                    if (id != null) idToName.put(id, nm);
                    if (id != null) idToPhoto.put(id, pb64);
                }
                rootRef.child(STATS_PATH).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot statsSnap) {
                        List<LeaderboardEntry> list = new ArrayList<>();
                        for (DataSnapshot s : statsSnap.getChildren()) {
                            String uid = s.getKey();
                            DataSnapshot catPointsNode = s.child("byCategory").child(category).child("points");
                            DataSnapshot catAttemptsNode = s.child("byCategory").child(category).child("attempts");
                            int points = safeInt(catPointsNode.getValue());
                            int attempts = safeInt(catAttemptsNode.getValue());
                            String nm = idToName.get(uid);
                            if (nm != null) {
                                String pb64 = idToPhoto.get(uid);
                                // Use attempts as secondary tie-breaker in category view
                                LeaderboardEntry e = new LeaderboardEntry(uid, nm, points, attempts, pb64);
                                list.add(e);
                            }
                        }
                        callback.onLoaded(list);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) { callback.onError(error.getMessage()); }
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { callback.onError(error.getMessage()); }
        });
    }

    public static synchronized FirebaseDatabaseHelper getInstance() {
        if (instance == null) instance = new FirebaseDatabaseHelper();
        return instance;
    }

    // ===================== USER PROFILE ===================== //

    public static class UserProfileData {
        public String id;
        public String username; // optional separate from name
        public String name;
        public String email;
        public String phone;
        public String dob; // date of birth, e.g., dd-MM-yyyy
        public String bio;
        public String photoUri;
        public String photoBase64; // Base64 encoded image for cross-device sync
    }

    public interface UserProfileCallback { void onLoaded(UserProfileData data); void onError(String error); }
    public interface SimpleCallback { void onSuccess(); void onError(String error); }

    /** Load user profile fields from users/{id}. */
    public void getUserProfile(@NonNull String userId, @NonNull UserProfileCallback callback) {
        if (userId.isEmpty()) { callback.onError("Missing user id"); return; }
        rootRef.child(USERS_PATH).child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot s) {
                        if (!s.exists()) { callback.onError("User not found"); return; }
                        UserProfileData d = new UserProfileData();
                        d.id = userId;
                        d.username = Objects.toString(s.child("username").getValue(), "");
                        d.name = Objects.toString(s.child("name").getValue(), "");
                        d.email = Objects.toString(s.child("email").getValue(), "");
                        d.phone = Objects.toString(s.child("phone").getValue(), "");
                        d.dob = Objects.toString(s.child("dob").getValue(), "");
                        d.bio = Objects.toString(s.child("bio").getValue(), "");
                        d.photoUri = Objects.toString(s.child("photoUri").getValue(), "");
                        d.photoBase64 = Objects.toString(s.child("photoBase64").getValue(), "");
                        callback.onLoaded(d);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) { callback.onError(error.getMessage()); }
                });
    }

    /** Observe live changes to stats/{userId}. Returns the ValueEventListener so callers can detach. */
    public ValueEventListener observeUserStats(@NonNull String userId, @NonNull UserStatsCallback callback) {
        if (userId == null || userId.isEmpty()) {
            callback.onError("Missing user id");
            return null;
        }
        DatabaseReference ref = rootRef.child(STATS_PATH).child(userId);
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                int total = safeInt(snapshot.child("totalQuizzes").getValue());
                int best = safeInt(snapshot.child("bestScore").getValue());
                int streak = safeInt(snapshot.child("currentStreak").getValue());
                int wins = safeInt(snapshot.child("wins").getValue());
                int losses = safeInt(snapshot.child("losses").getValue());
                callback.onStatsLoaded(new UserStats(total, best, streak, wins, losses));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        };
        ref.addValueEventListener(listener);
        return listener;
    }

    /** Stop observing live stats for a user. */
    public void stopObservingUserStats(@NonNull String userId, @NonNull ValueEventListener listener) {
        if (userId == null || userId.isEmpty() || listener == null) return;
        rootRef.child(STATS_PATH).child(userId).removeEventListener(listener);
    }

    /**
     * Record a battle outcome for the given user.
     * If won=true -> increment wins; else -> increment losses.
     */
    public void updateBattleOutcome(@NonNull String userId, boolean won, @NonNull DatabaseCallback callback) {
        if (userId.isEmpty()) { callback.onError("Missing user id"); return; }
        DatabaseReference statsRef = rootRef.child(STATS_PATH).child(userId);
        statsRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @NonNull
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(@NonNull com.google.firebase.database.MutableData currentData) {
                Map<String, Object> map = (Map<String, Object>) currentData.getValue();
                if (map == null) map = new HashMap<>();
                int wins = safeInt(map.get("wins"));
                int losses = safeInt(map.get("losses"));
                if (won) wins += 1; else losses += 1;
                map.put("wins", wins);
                map.put("losses", losses);
                currentData.setValue(map);
                return com.google.firebase.database.Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (error != null) {
                    callback.onError("Failed to update battle outcome: " + error.getMessage());
                } else if (!committed) {
                    callback.onError("Battle outcome not committed");
                } else {
                    callback.onSuccess("Battle outcome updated");
                }
            }
        });
    }

    /** Convenience: increment wins by 1. */
    public void incrementWins(@NonNull String userId, @NonNull DatabaseCallback callback) {
        updateBattleOutcome(userId, true, callback);
    }

    /** Convenience: increment losses by 1. */
    public void incrementLosses(@NonNull String userId, @NonNull DatabaseCallback callback) {
        updateBattleOutcome(userId, false, callback);
    }

    /**
     * Apply battle outcome exactly once per battle. Uses a transaction on battles/{id}/outcomeApplied flag.
     * If we successfully flip the flag from false/null -> true, we then increment winner/loser counters.
     */
    public void applyBattleOutcomeOnce(@NonNull String battleId,
                                       @NonNull String winnerUid,
                                       @NonNull String loserUid,
                                       @NonNull DatabaseCallback callback) {
        if (battleId.isEmpty() || winnerUid.isEmpty() || loserUid.isEmpty()) {
            callback.onError("Missing battleId or user ids");
            return;
        }
        DatabaseReference flagRef = rootRef.child("battles").child(battleId).child("outcomeApplied");
        flagRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @NonNull
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(@NonNull com.google.firebase.database.MutableData currentData) {
                Boolean applied = currentData.getValue(Boolean.class);
                if (applied != null && applied) {
                    return com.google.firebase.database.Transaction.abort();
                }
                currentData.setValue(true);
                return com.google.firebase.database.Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (error != null) {
                    callback.onError("Failed to mark outcome: " + error.getMessage());
                    return;
                }
                if (!committed) {
                    callback.onError("Outcome already applied");
                    return;
                }
                // Now safely increment winner/loser
                incrementWins(winnerUid, new DatabaseCallback() {
                    @Override public void onSuccess(String message) {
                        incrementLosses(loserUid, new DatabaseCallback() {
                            @Override public void onSuccess(String message) { callback.onSuccess("Outcome applied"); }
                            @Override public void onError(String error) { callback.onError(error); }
                        });
                    }
                    @Override public void onError(String error) { callback.onError(error); }
                });
            }
        });
    }

    /** Update user profile fields in users/{id}. Accepts partial fields via updates map. */
    public void updateUserProfile(@NonNull String userId, @NonNull Map<String, Object> updates, @NonNull SimpleCallback callback) {
        if (userId.isEmpty()) { callback.onError("Missing user id"); return; }
        rootRef.child(USERS_PATH).child(userId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public interface SendChallengeCallback { void onSuccess(String challengeId); void onError(String error); }

    /** Same as sendChallenge but returns the generated challenge id on success. */
    public void sendChallengeReturningId(@NonNull String fromUid, @NonNull String fromName,
                                         @NonNull String toUid, @NonNull String category, @NonNull String categoryName,
                                         @NonNull SendChallengeCallback callback) {
        if (fromUid.isEmpty() || toUid.isEmpty()) { callback.onError("Missing user ids"); return; }
        String cid = rootRef.child(CHALLENGES_PATH).child(toUid).push().getKey();
        if (cid == null) { callback.onError("Failed to create challenge id"); return; }
        Map<String, Object> data = new HashMap<>();
        data.put("id", cid);
        data.put("fromUid", fromUid);
        data.put("fromName", fromName);
        data.put("toUid", toUid);
        data.put("category", category);
        data.put("categoryName", categoryName);
        data.put("status", "pending");
        data.put("createdAt", System.currentTimeMillis());
        rootRef.child(CHALLENGES_PATH).child(toUid).child(cid)
                .setValue(data)
                .addOnSuccessListener(aVoid -> callback.onSuccess(cid))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /** Change password with current password verification. */
    public void changePassword(@NonNull String userId, @NonNull String currentPlain, @NonNull String newPlain, @NonNull SimpleCallback callback) {
        if (userId.isEmpty()) { callback.onError("Missing user id"); return; }
        rootRef.child(USERS_PATH).child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot s) {
                        if (!s.exists()) { callback.onError("User not found"); return; }
                        String salt = Objects.toString(s.child("passwordSalt").getValue(), null);
                        String hash = Objects.toString(s.child("passwordHash").getValue(), null);
                        if (salt == null || hash == null) { callback.onError("Password not set"); return; }
                        String computed = hashPasswordBase64(currentPlain, salt);
                        if (!hash.equals(computed)) { callback.onError("Current password incorrect"); return; }
                        String newSalt = generateSaltBase64();
                        String newHash = hashPasswordBase64(newPlain, newSalt);
                        Map<String, Object> up = new HashMap<>();
                        up.put("passwordSalt", newSalt);
                        up.put("passwordHash", newHash);
                        rootRef.child(USERS_PATH).child(userId).updateChildren(up)
                                .addOnSuccessListener(aVoid -> callback.onSuccess())
                                .addOnFailureListener(e -> callback.onError(e.getMessage()));
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) { callback.onError(error.getMessage()); }
                });
    }

    // ===================== SIMPLE AUTH (DB-only) ===================== //

    /**
     * Register a user in Realtime Database with salted SHA-256 password hashing.
     * Ensures unique email.
     */
    public void registerUserSimple(@NonNull final User user,
                                   @NonNull final String username,
                                   @NonNull final String plainPassword,
                                   @NonNull final SimpleAuthCallback callback) {
        // Delegate to overload without photo
        registerUserSimple(user, username, plainPassword, null, callback);
    }

    /**
     * Overload that accepts optional Base64 profile photo. If provided, will be stored under users/{id}/photoBase64.
     */
    public void registerUserSimple(@NonNull final User user,
                                   @NonNull final String username,
                                   @NonNull final String plainPassword,
                                   final String photoBase64,
                                   @NonNull final SimpleAuthCallback callback) {
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            callback.onError("Email is required");
            return;
        }
        // Normalize email
        user.setEmail(user.getEmail().trim().toLowerCase());
        // 1) Check if email already exists
        rootRef.child(USERS_PATH)
                .orderByChild("email").equalTo(user.getEmail())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            callback.onError("Email already in use");
                            return;
                        }
                        // Fallback: scan all users with case-insensitive/trimmed comparison to avoid duplicates by casing
                        rootRef.child(USERS_PATH).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot all) {
                                String target = user.getEmail(); // already normalized
                                for (DataSnapshot s : all.getChildren()) {
                                    String dbEmail = Objects.toString(s.child("email").getValue(), "");
                                    if (dbEmail.trim().toLowerCase().equals(target)) {
                                        callback.onError("Email already in use");
                                        return;
                                    }
                                }
                                // 2) Create new user with push key
                                String key = rootRef.child(USERS_PATH).push().getKey();
                                if (key == null) {
                                    callback.onError("Could not generate user id");
                                    return;
                                }
                                user.setId(key);

                                // 3) Hash password with random salt
                                String saltB64 = generateSaltBase64();
                                String hashB64 = hashPasswordBase64(plainPassword, saltB64);

                                Map<String, Object> data = new HashMap<>();
                                data.put("id", user.getId());
                                data.put("name", user.getName());
                                data.put("email", user.getEmail());
                                data.put("phone", user.getPhone());
                                data.put("username", username);
                                if (user.getAvatarResId() != null) data.put("avatarResId", user.getAvatarResId());
                                data.put("passwordHash", hashB64);
                                data.put("passwordSalt", saltB64);
                                if (photoBase64 != null && !photoBase64.isEmpty()) {
                                    data.put("photoBase64", photoBase64);
                                }

                                rootRef.child(USERS_PATH).child(user.getId()).setValue(data)
                                        .addOnSuccessListener(aVoid -> {
                                            // Create default stats for the new user
                                            ensureDefaultStats(user.getId());
                                            callback.onSuccess(user);
                                        })
                                        .addOnFailureListener(e -> callback.onError("Failed to save user: " + e.getMessage()));
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                callback.onError("Registration cancelled: " + error.getMessage());
                            }
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError("Registration cancelled: " + error.getMessage());
                    }
                });
    }

    /** Update stats and also aggregate by category under stats/{uid}/byCategory/{category}. */
    public void updateUserStatsAccumulatingWithCategory(@NonNull String userId, int latestScore, @NonNull String category, @NonNull DatabaseCallback callback) {
        if (userId.isEmpty()) { callback.onError("Missing user id"); return; }
        DatabaseReference statsRef = rootRef.child(STATS_PATH).child(userId);
        statsRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @NonNull
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(@NonNull com.google.firebase.database.MutableData currentData) {
                Map<String, Object> map = (Map<String, Object>) currentData.getValue();
                if (map == null) map = new HashMap<>();
                int total = safeInt(map.get("totalQuizzes"));
                int best = safeInt(map.get("bestScore"));
                int streak = safeInt(map.get("currentStreak"));

                total += 1;
                best += Math.max(0, latestScore);
                streak += 1;

                map.put("totalQuizzes", total);
                map.put("bestScore", best);
                map.put("currentStreak", streak);

                if (category != null && !category.trim().isEmpty()) {
                    Object bcObj = map.get("byCategory");
                    Map<String, Object> byCategory = bcObj instanceof Map ? (Map<String, Object>) bcObj : new HashMap<>();
                    Object catObj = byCategory.get(category);
                    Map<String, Object> catMap = catObj instanceof Map ? (Map<String, Object>) catObj : new HashMap<>();
                    int catPoints = safeInt(catMap.get("points"));
                    int attempts = safeInt(catMap.get("attempts"));
                    catPoints += Math.max(0, latestScore);
                    attempts += 1;
                    catMap.put("points", catPoints);
                    catMap.put("attempts", attempts);
                    byCategory.put(category, catMap);
                    map.put("byCategory", byCategory);
                }

                currentData.setValue(map);
                return com.google.firebase.database.Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (error != null) {
                    callback.onError("Failed to update stats: " + error.getMessage());
                } else if (!committed) {
                    callback.onError("Stats update not committed");
                } else {
                    callback.onSuccess("Stats updated");
                }
            }
        });
    }

    // ===================== LEADERBOARD ===================== //

    public static class LeaderboardEntry {
        public String userId;
        public String name;
        public int points; // from bestScore (total accumulated)
        public int totalQuizzes; // for tie-breakers (overall) or attempts (per-category)
        public String photoBase64; // optional avatar for display

        public LeaderboardEntry() {}
        public LeaderboardEntry(String userId, String name, int points) {
            this.userId = userId; this.name = name; this.points = points;
        }

        public LeaderboardEntry(String userId, String name, int points, String photoBase64) {
            this.userId = userId; this.name = name; this.points = points; this.photoBase64 = photoBase64;
        }

        public LeaderboardEntry(String userId, String name, int points, int totalQuizzes, String photoBase64) {
            this.userId = userId; this.name = name; this.points = points; this.totalQuizzes = totalQuizzes; this.photoBase64 = photoBase64;
        }
    }

    public interface LeaderboardCallback { void onLoaded(List<LeaderboardEntry> entries); void onError(String error); }

    /**
     * Load all users joined with their stats (points from bestScore).
     */
    public void getAllUserStatsWithUsers(@NonNull LeaderboardCallback callback) {
        rootRef.child(USERS_PATH).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot usersSnap) {
                // Build base map of id->name
                final Map<String, String> idToName = new HashMap<>();
                final Map<String, String> idToPhoto = new HashMap<>();
                for (DataSnapshot u : usersSnap.getChildren()) {
                    String id = Objects.toString(u.child("id").getValue(), u.getKey());
                    String nm = Objects.toString(u.child("name").getValue(), "User");
                    String pb64 = Objects.toString(u.child("photoBase64").getValue(), "");
                    if (id != null) idToName.put(id, nm);
                    if (id != null) idToPhoto.put(id, pb64);
                }

                rootRef.child(STATS_PATH).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot statsSnap) {
                        List<LeaderboardEntry> list = new ArrayList<>();
                        for (DataSnapshot s : statsSnap.getChildren()) {
                            String uid = s.getKey();
                            int points = safeInt(s.child("bestScore").getValue());
                            int total = safeInt(s.child("totalQuizzes").getValue());
                            String nm = idToName.get(uid);
                            if (nm != null) {
                                String pb64 = idToPhoto.get(uid);
                                list.add(new LeaderboardEntry(uid, nm, points, total, pb64));
                            }
                        }
                        callback.onLoaded(list);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { callback.onError(error.getMessage()); }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { callback.onError(error.getMessage()); }
        });
    }

    /**
     * Update the daily login streak based on the time since last login.
     * Rules:
     * - If first time (no lastLoginAt): streak = 1
     * - If logged in again within 24 hours since last login: do not increment (same 24h cycle)
     * - If logged in between 24h and 48h since last login: increment by 1
     * - If 48h or more since last login: reset streak to 0
     * In all cases, lastLoginAt is updated to nowMillis.
     */
    public void updateDailyLoginStreak(@NonNull String userId, long nowMillis, @NonNull DatabaseCallback callback) {
        if (userId.isEmpty()) { callback.onError("Missing user id"); return; }
        final long DAY = 24L * 60L * 60L * 1000L;
        DatabaseReference statsRef = rootRef.child(STATS_PATH).child(userId);
        statsRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @NonNull
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(@NonNull com.google.firebase.database.MutableData currentData) {
                Map<String, Object> map = (Map<String, Object>) currentData.getValue();
                if (map == null) map = new HashMap<>();
                int streak = safeInt(map.get("currentStreak"));
                long last = safeLong(map.get("lastLoginAt"));

                if (last <= 0) {
                    streak = 1; // first login starts streak
                } else {
                    long diff = nowMillis - last;
                    if (diff < DAY) {
                        // same 24h window: no increment
                    } else if (diff < 2 * DAY) {
                        streak = streak + 1; // next day window: increment
                    } else {
                        streak = 0; // missed a day: reset to zero
                    }
                }

                map.put("currentStreak", streak);
                map.put("lastLoginAt", nowMillis);
                currentData.setValue(map);
                return com.google.firebase.database.Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (error != null) {
                    callback.onError("Failed to update streak: " + error.getMessage());
                } else if (!committed) {
                    callback.onError("Streak update not committed");
                } else {
                    callback.onSuccess("Streak updated");
                }
            }
        });
    }

    /**
     * Accumulate stats after a quiz attempt.
     * - totalQuizzes += 1
     * - bestScore stores TOTAL score across all quizzes (as requested)
     * - currentStreak += 1 (simple per-quiz streak)
     */
    public void updateUserStatsAccumulating(@NonNull String userId, int latestScore, @NonNull DatabaseCallback callback) {
        if (userId.isEmpty()) { callback.onError("Missing user id"); return; }
        DatabaseReference statsRef = rootRef.child(STATS_PATH).child(userId);
        statsRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @NonNull
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(@NonNull com.google.firebase.database.MutableData currentData) {
                Map<String, Object> map = (Map<String, Object>) currentData.getValue();
                if (map == null) map = new HashMap<>();
                int total = safeInt(map.get("totalQuizzes"));
                int best = safeInt(map.get("bestScore")); // used as TOTAL score bucket
                int streak = safeInt(map.get("currentStreak"));

                total += 1;
                best += Math.max(0, latestScore);
                streak += 1;

                map.put("totalQuizzes", total);
                map.put("bestScore", best);
                map.put("currentStreak", streak);
                currentData.setValue(map);
                return com.google.firebase.database.Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (error != null) {
                    callback.onError("Failed to update stats: " + error.getMessage());
                } else if (!committed) {
                    callback.onError("Stats update not committed");
                } else {
                    callback.onSuccess("Stats updated");
                }
            }
        });
    }

    private void handlePasswordCheckAndReturn(@NonNull DataSnapshot s,
                                              @NonNull String dbEmail,
                                              @NonNull String plainPassword,
                                              @NonNull SimpleAuthCallback callback) {
        String id = Objects.toString(s.child("id").getValue(), null);
        String name = Objects.toString(s.child("name").getValue(), "");
        String phone = Objects.toString(s.child("phone").getValue(), "");
        Integer avatar = null;
        try {
            Object a = s.child("avatarResId").getValue();
            if (a != null) avatar = Integer.parseInt(String.valueOf(a));
        } catch (Exception ignored) {}

        String saltB64 = Objects.toString(s.child("passwordSalt").getValue(), null);
        String hashB64 = Objects.toString(s.child("passwordHash").getValue(), null);

        if (saltB64 == null || hashB64 == null) {
            callback.onError("Account not set up for password login");
            return;
        }

        String computed = hashPasswordBase64(plainPassword, saltB64);
        if (!hashB64.equals(computed)) {
            callback.onError("Incorrect password");
            return;
        }

        User user = new User(id, name, dbEmail, phone, avatar);
        callback.onSuccess(user);
    }

    /**
     * Login by email and password against Realtime Database users.
     */
    public void loginUserSimple(@NonNull final String email,
                                @NonNull final String plainPassword,
                                @NonNull final SimpleAuthCallback callback) {
        rootRef.child(USERS_PATH)
                .orderByChild("email").equalTo(email)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            // Fallback: scan all users and match email case-insensitively and trimmed
                            rootRef.child(USERS_PATH).addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot all) {
                                    String target = email == null ? "" : email.trim().toLowerCase();
                                    for (DataSnapshot s : all.getChildren()) {
                                        String dbEmail = Objects.toString(s.child("email").getValue(), "");
                                        if (dbEmail.trim().toLowerCase().equals(target)) {
                                            handlePasswordCheckAndReturn(s, dbEmail, plainPassword, callback);
                                            return;
                                        }
                                    }
                                    callback.onError("No account found with this email");
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    callback.onError("Login cancelled: " + error.getMessage());
                                }
                            });
                            return;
                        }
                        // Expecting exactly one match
                        for (DataSnapshot s : snapshot.getChildren()) {
                            String dbEmail = Objects.toString(s.child("email").getValue(), email);
                            handlePasswordCheckAndReturn(s, dbEmail, plainPassword, callback);
                            return;
                        }
                        callback.onError("Login error: unexpected data");
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError("Login cancelled: " + error.getMessage());
                    }
                });
    }

    /**
     * No-op logout placeholder (kept for compatibility with callers).
     */
    public void logout() { /* no FirebaseAuth; session is managed via SharedPreferences */ }

    // ===================== USERS ===================== //

    public void createOrUpdateUser(@NonNull User user, @NonNull DatabaseCallback callback) {
        if (user.getId() == null || user.getId().isEmpty()) {
            String uid = rootRef.child(USERS_PATH).push().getKey();
            user.setId(uid);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("name", user.getName());
        data.put("email", user.getEmail());
        data.put("phone", user.getPhone());
        if (user.getAvatarResId() != null) data.put("avatarResId", user.getAvatarResId());

        rootRef.child(USERS_PATH).child(user.getId()).setValue(data)
                .addOnSuccessListener(aVoid -> callback.onSuccess("User saved"))
                .addOnFailureListener(e -> callback.onError("Failed to save user: " + e.getMessage()));
    }

    public void getAllUsers(@NonNull UsersCallback callback) {
        rootRef.child(USERS_PATH).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<User> users = new ArrayList<>();
                for (DataSnapshot s : snapshot.getChildren()) {
                    User u = s.getValue(User.class);
                    if (u != null) users.add(u);
                }
                callback.onUsersLoaded(users);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError("Failed to load users: " + error.getMessage());
            }
        });
    }

    // ===================== PRESENCE ===================== //

    /** Mark user online and set onDisconnect to offline. Call from foreground activities. */
    public void setPresenceOnline(@NonNull String userId) {
        if (userId.isEmpty()) return;
        DatabaseReference pRef = rootRef.child(PRESENCE_PATH).child(userId);
        long now = System.currentTimeMillis();
        Map<String, Object> val = new HashMap<>();
        val.put("online", true);
        val.put("lastSeen", now);
        pRef.updateChildren(val);
        pRef.onDisconnect().updateChildren(new HashMap<String, Object>() {{
            put("online", false);
            put("lastSeen", System.currentTimeMillis());
        }});
    }

    public interface OnlineUsersCallback { void onLoaded(List<User> users); void onError(String error); }

    /** Observe online users (registered), excluding optional selfId if provided (non-empty). */
    public com.google.firebase.database.ValueEventListener observeOnlineUsers(@NonNull final String selfId,
                                                                             @NonNull final OnlineUsersCallback callback) {
        DatabaseReference presRef = rootRef.child(PRESENCE_PATH);
        com.google.firebase.database.ValueEventListener l = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot presenceSnap) {
                List<String> onlineIds = new ArrayList<>();
                for (DataSnapshot s : presenceSnap.getChildren()) {
                    boolean online = Boolean.TRUE.equals(s.child("online").getValue(Boolean.class));
                    if (online) onlineIds.add(s.getKey());
                }
                // Join with users
                rootRef.child(USERS_PATH).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot usersSnap) {
                        List<User> list = new ArrayList<>();
                        for (DataSnapshot u : usersSnap.getChildren()) {
                            String uid = u.getKey();
                            if (uid == null) continue;
                            if (selfId != null && !selfId.isEmpty() && uid.equals(selfId)) continue;
                            if (onlineIds.contains(uid)) {
                                User user = u.getValue(User.class);
                                if (user != null) {
                                    if (user.getId() == null || user.getId().isEmpty()) user.setId(uid);
                                    list.add(user);
                                }
                            }
                        }
                        callback.onLoaded(list);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) { callback.onError(error.getMessage()); }
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { callback.onError(error.getMessage()); }
        };
        presRef.addValueEventListener(l);
        return l;
    }

    // ===================== CHALLENGES ===================== //

    public static class Challenge {
        public String id;
        public String fromUid;
        public String fromName;
        public String toUid;
        public String category;
        public String categoryName;
        public String status; // pending | accepted | declined
        public long createdAt;
        public Challenge() {}
    }

    public interface ChallengeListener { void onChallenge(Challenge c); void onError(String error); }

    public void sendChallenge(@NonNull String fromUid, @NonNull String fromName,
                              @NonNull String toUid, @NonNull String category, @NonNull String categoryName,
                              @NonNull SimpleCallback callback) {
        if (fromUid.isEmpty() || toUid.isEmpty()) { callback.onError("Missing user ids"); return; }
        String cid = rootRef.child(CHALLENGES_PATH).child(toUid).push().getKey();
        if (cid == null) { callback.onError("Failed to create challenge id"); return; }
        Map<String, Object> data = new HashMap<>();
        data.put("id", cid);
        data.put("fromUid", fromUid);
        data.put("fromName", fromName);
        data.put("toUid", toUid);
        data.put("category", category);
        data.put("categoryName", categoryName);
        data.put("status", "pending");
        data.put("createdAt", System.currentTimeMillis());
        rootRef.child(CHALLENGES_PATH).child(toUid).child(cid)
                .setValue(data)
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public com.google.firebase.database.ValueEventListener observeIncomingChallenges(@NonNull String myUid,
                                                                                    @NonNull ChallengeListener listener) {
        DatabaseReference ref = rootRef.child(CHALLENGES_PATH).child(myUid);
        com.google.firebase.database.ValueEventListener l = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot s : snapshot.getChildren()) {
                    Challenge c = s.getValue(Challenge.class);
                    if (c != null && "pending".equals(c.status)) {
                        listener.onChallenge(c);
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { listener.onError(error.getMessage()); }
        };
        ref.addValueEventListener(l);
        return l;
    }

    /** Update a challenge status under challenges/{myUid}/{challengeId}/status to accepted/declined. */
    public void respondToChallenge(@NonNull String myUid, @NonNull String challengeId, boolean accept,
                                   @NonNull SimpleCallback cb) {
        String status = accept ? "accepted" : "declined";
        rootRef.child(CHALLENGES_PATH).child(myUid).child(challengeId).child("status")
                .setValue(status)
                .addOnSuccessListener(aVoid -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Remove a previously added incoming challenges listener for a user. */
    public void removeIncomingChallengesObserver(@NonNull String myUid,
                                                 @NonNull com.google.firebase.database.ValueEventListener listener) {
        DatabaseReference ref = rootRef.child(CHALLENGES_PATH).child(myUid);
        ref.removeEventListener(listener);
    }

    // Observe a specific challenge's status (located under challenges/{targetUid}/{challengeId})
    public com.google.firebase.database.ValueEventListener observeChallengeStatus(@NonNull String targetUid,
                                                                                 @NonNull String challengeId,
                                                                                 @NonNull ValueEventListener listener) {
        DatabaseReference ref = rootRef.child(CHALLENGES_PATH).child(targetUid).child(challengeId).child("status");
        ref.addValueEventListener(listener);
        return listener;
    }

    // ===================== BATTLE RESULTS ===================== //

    private static final String BATTLE_RESULTS_PATH = "battleResults"; // battleResults/{challengeId}/{uid}

    public void recordBattleResult(@NonNull String challengeId, @NonNull String uid, @NonNull String name,
                                   int correctCount, long finishedAt, @NonNull SimpleCallback cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        data.put("name", name);
        data.put("correctCount", correctCount);
        data.put("finishedAt", finishedAt);
        rootRef.child(BATTLE_RESULTS_PATH).child(challengeId).child(uid)
                .setValue(data)
                .addOnSuccessListener(aVoid -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public interface BattleResultsCallback { void onLoaded(Map<String, Object> resultsRaw); void onError(String error); }

    public void getBattleResults(@NonNull String challengeId, @NonNull BattleResultsCallback callback) {
        DatabaseReference ref = rootRef.child(BATTLE_RESULTS_PATH).child(challengeId);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                Map<String, Object> map = (Map<String, Object>) snapshot.getValue();
                callback.onLoaded(map);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { callback.onError(error.getMessage()); }
        });
    }

    /** Observe battle results in realtime for a challenge. Caller is responsible for removing the listener. */
    public com.google.firebase.database.ValueEventListener observeBattleResults(@NonNull String challengeId,
                                                                               @NonNull ValueEventListener listener) {
        DatabaseReference ref = rootRef.child(BATTLE_RESULTS_PATH).child(challengeId);
        ref.addValueEventListener(listener);
        return listener;
    }

    /** Remove a previously added battle results listener. */
    public void removeBattleResultsObserver(@NonNull String challengeId,
                                            @NonNull ValueEventListener listener) {
        DatabaseReference ref = rootRef.child(BATTLE_RESULTS_PATH).child(challengeId);
        ref.removeEventListener(listener);
    }

    // Record the start time of a user's battle quiz. Also fills uid/name if absent.
    public void recordBattleStart(@NonNull String challengeId, @NonNull String uid, @Nullable String name, long startedAt, @NonNull SimpleCallback callback) {
        if (challengeId == null || challengeId.isEmpty() || uid == null || uid.isEmpty()) {
            if (callback != null) callback.onError("Invalid params");
            return;
        }
        DatabaseReference ref = rootRef.child(BATTLE_RESULTS_PATH).child(challengeId).child(uid);
        Map<String, Object> updates = new HashMap<>();
        updates.put("uid", uid);
        if (name != null) updates.put("name", name);
        updates.put("startedAt", startedAt);
        ref.updateChildren(updates, (error, r) -> {
            if (callback == null) return;
            if (error == null) callback.onSuccess(); else callback.onError(error.getMessage());
        });
    }

    // ===================== CATEGORIES ===================== //

    public void getAllCategories(@NonNull CategoriesCallback callback) {
        rootRef.child(CATEGORIES_PATH).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Category> list = new ArrayList<>();
                final int total = (int) snapshot.getChildrenCount();
                final int[] processed = {0};

                if (total == 0) {
                    callback.onCategoriesLoaded(list);
                    return;
                }

                for (DataSnapshot s : snapshot.getChildren()) {
                    Category c = s.getValue(Category.class);
                    if (c != null) {
                        // Fetch question count per category (optional)
                        getQuestionCountForCategory(c.getName(), count -> {
                            c.setQuestionCount(count);
                            processed[0]++;
                            if (processed[0] == total) {
                                callback.onCategoriesLoaded(list);
                            }
                        });
                        list.add(c);
                    } else {
                        processed[0]++;
                        if (processed[0] == total) callback.onCategoriesLoaded(list);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError("Failed to load categories: " + error.getMessage());
            }
        });
    }

    private void getQuestionCountForCategory(@NonNull String categoryName, @NonNull QuestionCountCallback callback) {
        rootRef.child(QUESTIONS_PATH).orderByChild("category").equalTo(categoryName)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) { callback.onCountReceived((int) snapshot.getChildrenCount()); }
                    @Override public void onCancelled(@NonNull DatabaseError error) { callback.onCountReceived(0); }
                });
    }

    // ===================== QUESTIONS ===================== //

    public void getAllQuestions(@NonNull QuestionsCallback callback) {
        rootRef.child(QUESTIONS_PATH).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Question> list = new ArrayList<>();
                for (DataSnapshot s : snapshot.getChildren()) {
                    Question q = s.getValue(Question.class);
                    if (q != null) list.add(q);
                }
                callback.onQuestionsLoaded(list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError("Failed to load questions: " + error.getMessage());
            }
        });
    }

    public void getQuestionsByCategory(@NonNull String category, @NonNull QuestionsCallback callback) {
        rootRef.child(QUESTIONS_PATH).orderByChild("category").equalTo(category)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<Question> list = new ArrayList<>();
                        for (DataSnapshot s : snapshot.getChildren()) {
                            Question q = s.getValue(Question.class);
                            if (q != null) list.add(q);
                        }
                        callback.onQuestionsLoaded(list);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError("Failed to load questions: " + error.getMessage());
                    }
                });
    }

    // ===================== CALLBACKS ===================== //

    public interface DatabaseCallback { void onSuccess(String message); void onError(String error); }
    public interface UsersCallback { void onUsersLoaded(List<User> users); void onError(String error); }
    public interface CategoriesCallback { void onCategoriesLoaded(List<Category> categories); void onError(String error); }
    public interface QuestionsCallback { void onQuestionsLoaded(List<Question> questions); void onError(String error); }
    public interface QuestionCountCallback { void onCountReceived(int count); }

    public interface SimpleAuthCallback { void onSuccess(User user); void onError(String error); }

    // Stats callback
    public interface UserStatsCallback { void onStatsLoaded(UserStats stats); void onError(String error); }

    // ===================== UTILS ===================== //

    private String generateSaltBase64() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP);
    }

    private String hashPasswordBase64(@NonNull String password, @NonNull String saltBase64) {
        try {
            byte[] salt = android.util.Base64.decode(saltBase64, android.util.Base64.NO_WRAP);
            byte[] pwdBytes = password.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] combined = new byte[salt.length + pwdBytes.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(pwdBytes, 0, combined, salt.length, pwdBytes.length);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(combined);
            return android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            // Fallback: never return plain password; but if hashing fails, return empty to force mismatch
            return "";
        }
    }

    // Safe parsers for numeric values from Realtime Database
    private static int safeInt(Object obj) {
        if (obj == null) return 0;
        try {
            if (obj instanceof Number) return ((Number) obj).intValue();
            String s = String.valueOf(obj).trim();
            if (s.isEmpty()) return 0;
            return Integer.parseInt(s);
        } catch (Exception ignored) { return 0; }
    }

    private static long safeLong(Object obj) {
        if (obj == null) return 0L;
        try {
            if (obj instanceof Number) return ((Number) obj).longValue();
            String s = String.valueOf(obj).trim();
            if (s.isEmpty()) return 0L;
            return Long.parseLong(s);
        } catch (Exception ignored) { return 0L; }
    }

    // ===================== USER STATS ===================== //

    public static class UserStats {
        public int totalQuizzes;
        public int bestScore;
        public int currentStreak;
        public int wins;
        public int losses;

        public UserStats() {}

        public UserStats(int totalQuizzes, int bestScore, int currentStreak) {
            this.totalQuizzes = totalQuizzes;
            this.bestScore = bestScore;
            this.currentStreak = currentStreak;
        }

        public UserStats(int totalQuizzes, int bestScore, int currentStreak, int wins, int losses) {
            this.totalQuizzes = totalQuizzes;
            this.bestScore = bestScore;
            this.currentStreak = currentStreak;
            this.wins = wins;
            this.losses = losses;
        }
    }

    public void getUserStats(@NonNull String userId, @NonNull UserStatsCallback callback) {
        if (userId == null || userId.isEmpty()) {
            callback.onError("Missing user id");
            return;
        }
        rootRef.child(STATS_PATH).child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int total = safeInt(snapshot.child("totalQuizzes").getValue());
                        int best = safeInt(snapshot.child("bestScore").getValue());
                        int streak = safeInt(snapshot.child("currentStreak").getValue());
                        int wins = safeInt(snapshot.child("wins").getValue());
                        int losses = safeInt(snapshot.child("losses").getValue());
                        callback.onStatsLoaded(new UserStats(total, best, streak, wins, losses));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError("Failed to load stats: " + error.getMessage());
                    }
                });
    }

    /** Ensure a default stats object exists at stats/{userId}. */
    private void ensureDefaultStats(@NonNull String userId) {
        if (userId.isEmpty()) return;
        DatabaseReference statsRef = rootRef.child(STATS_PATH).child(userId);
        statsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Map<String, Object> defaults = new HashMap<>();
                    defaults.put("totalQuizzes", 0);
                    defaults.put("bestScore", 0);
                    defaults.put("currentStreak", 0);
                    defaults.put("wins", 0);
                    defaults.put("losses", 0);
                    statsRef.setValue(defaults);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { /* no-op */ }
        });
    }
}
