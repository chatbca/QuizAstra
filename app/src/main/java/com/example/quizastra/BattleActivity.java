package com.example.quizastra;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class BattleActivity extends AppCompatActivity {

    private RecyclerView rvOnlineUsers;
    private final List<User> users = new ArrayList<>();
    private BattleUsersAdapter adapter;
    private String myUid;
    private String myName;
    private ValueEventListener presenceListener;
    private ValueEventListener incomingChallengesListener;
    private AlertDialog currentIncomingDialog;
    private boolean isDialogShowing = false;
    private final java.util.Set<String> startedChallenges = new java.util.HashSet<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battle);
        setTitle("Battle Field 1 v 1");

        // Bottom nav
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        if (nav != null) {
            nav.setSelectedItemId(R.id.nav_battle);
            BottomNavHandler.setup(this, nav);
        }

        SharedPreferences prefs = getSharedPreferences("profile_prefs", MODE_PRIVATE);
        myUid = prefs.getString("id", "");
        myName = prefs.getString("name", "You");

        rvOnlineUsers = findViewById(R.id.rvOnlineUsers);
        rvOnlineUsers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BattleUsersAdapter(users, this::showCategoryPickerAndChallenge);
        rvOnlineUsers.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Presence: mark online
        FirebaseDatabaseHelper.getInstance().setPresenceOnline(myUid);
        // Observe online users
        presenceListener = FirebaseDatabaseHelper.getInstance().observeOnlineUsers(myUid, new FirebaseDatabaseHelper.OnlineUsersCallback() {
            @Override public void onLoaded(List<User> list) {
                users.clear();
                users.addAll(list);
                if (adapter != null) adapter.notifyDataSetChanged();
            }
            @Override public void onError(String error) { /* ignore */ }
        });
        // Incoming challenges
        incomingChallengesListener = FirebaseDatabaseHelper.getInstance().observeIncomingChallenges(myUid, new FirebaseDatabaseHelper.ChallengeListener() {
            @Override public void onChallenge(FirebaseDatabaseHelper.Challenge c) {
                showIncomingChallengeDialog(c);
            }
            @Override public void onError(String error) { /* ignore */ }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Listeners are singletons; the observe methods return the listener so callers can remove if needed.
        // Firebase automatically removes Activity references when destroyed, but we can be explicit here if needed.
        // Not removing in this scaffold to keep it simple.
        if (currentIncomingDialog != null && currentIncomingDialog.isShowing()) {
            currentIncomingDialog.dismiss();
        }
        isDialogShowing = false;
    }

    private void showCategoryPickerAndChallenge(User target) {
        FirebaseDatabaseHelper.getInstance().getAllCategories(new FirebaseDatabaseHelper.CategoriesCallback() {
            @Override public void onCategoriesLoaded(List<Category> categories) {
                if (categories == null || categories.isEmpty()) {
                    showToast("No categories available");
                    return;
                }
                String[] names = new String[categories.size()];
                for (int i = 0; i < categories.size(); i++) names[i] = categories.get(i).getName();
                new AlertDialog.Builder(BattleActivity.this)
                        .setTitle("Select Category")
                        .setItems(names, (d, which) -> {
                            String cat = names[which];
                            // Send and then observe acceptance to auto-join for challenger
                            FirebaseDatabaseHelper.getInstance().sendChallengeReturningId(
                                    myUid, myName, target.getId(), cat, cat,
                                    new FirebaseDatabaseHelper.SendChallengeCallback() {
                                        @Override public void onSuccess(String challengeId) {
                                            showToast("Challenge sent to " + target.getName());
                                            showToast("Waiting for acceptance...");
                                            FirebaseDatabaseHelper.getInstance().observeChallengeStatus(
                                                    target.getId(), challengeId,
                                                    new com.google.firebase.database.ValueEventListener() {
                                                        @Override public void onDataChange(com.google.firebase.database.DataSnapshot s) {
                                                            String status = s.getValue(String.class);
                                                            showToast("Challenge status: " + String.valueOf(status));
                                                            if ("accepted".equals(status)) {
                                                                if (!startedChallenges.contains(challengeId)) {
                                                                    startedChallenges.add(challengeId);
                                                                    Intent i = new Intent(BattleActivity.this, BattleCountdownActivity.class);
                                                                    i.putExtra("category", cat);
                                                                    i.putExtra("battleChallengeId", challengeId);
                                                                    startActivity(i);
                                                                }
                                                            } else if ("declined".equals(status)) {
                                                                showToast(target.getName() + " declined your challenge");
                                                            }
                                                        }
                                                        @Override public void onCancelled(com.google.firebase.database.DatabaseError e) { }
                                                    }
                                            );
                                        }
                                        @Override public void onError(String error) { showToast("Failed: " + error); }
                                    }
                            );
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
            @Override public void onError(String error) { showToast(error); }
        });
    }

    private void showIncomingChallengeDialog(FirebaseDatabaseHelper.Challenge c) {
        if (isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
        if (c == null) return;
        if (c.id == null || c.toUid == null) return; // invalid payload
        String cat = (c.category != null && !c.category.isEmpty()) ? c.category : c.categoryName;
        if (cat == null || cat.isEmpty()) return;
        if (isDialogShowing) return; // avoid multiple dialogs

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Challenge Received")
                .setMessage((c.fromName != null ? c.fromName : "Someone") + " challenged you in " + (c.categoryName != null ? c.categoryName : cat) + ". Accept?")
                .setPositiveButton("Accept", (d, w) -> {
                    FirebaseDatabaseHelper.getInstance().respondToChallenge(c.toUid, c.id, true, new FirebaseDatabaseHelper.SimpleCallback() {
                        @Override public void onSuccess() {
                            // Start quiz for both users (receiver flow here); we pass category name as extra
                            try {
                                Intent i = new Intent(BattleActivity.this, BattleCountdownActivity.class);
                                i.putExtra("category", cat);
                                i.putExtra("battleChallengeId", c.id);
                                startActivity(i);
                            } catch (Exception e) {
                                showToast("Unable to start quiz: " + e.getMessage());
                            }
                        }
                        @Override public void onError(String error) { showToast(error); }
                    });
                })
                .setNegativeButton("Decline", (d, w) -> FirebaseDatabaseHelper.getInstance().respondToChallenge(c.toUid, c.id, false, new FirebaseDatabaseHelper.SimpleCallback() {
                    @Override public void onSuccess() { showToast("Declined challenge"); }
                    @Override public void onError(String error) { showToast(error); }
                }));

        currentIncomingDialog = builder.create();
        currentIncomingDialog.setOnDismissListener(dialog -> isDialogShowing = false);
        isDialogShowing = true;
        currentIncomingDialog.show();
    }

    private void showToast(String msg) { android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show(); }

    // RecyclerView adapter is defined in BattleUsersAdapter.java
}
