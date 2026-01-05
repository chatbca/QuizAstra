package svvaap.quizastra;

import android.app.Activity;
import android.content.Intent;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class BottomNavHandler {
    public static void setup(final Activity activity, BottomNavigationView navView) {
        navView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                if (!(activity instanceof UserDashboard)) {
                    activity.startActivity(new Intent(activity, UserDashboard.class));
                    activity.finish();
                }
                return true;
            } else if (id == R.id.nav_leaderboard) {
                if (!(activity instanceof LeaderboardActivity)) {
                    activity.startActivity(new Intent(activity, LeaderboardActivity.class));
                    activity.finish();
                }

                return true;
            } else if (id == R.id.nav_battle) {
                if (!(activity instanceof BattleActivity)) {
                    activity.startActivity(new Intent(activity, BattleActivity.class));
                    activity.finish();
                }
                return true;
            }
            else if (id == R.id.nav_profile) {
                if (!(activity instanceof UserProfile)) {
                    activity.startActivity(new Intent(activity, UserProfile.class));
                    activity.finish();
                }

                return true;
            }
            return false;
        });
    }
}
