package svvaap.quizastra;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

// No FirebaseAuth; using Realtime Database only

public class UserLogin extends AppCompatActivity {

    EditText etEmail, etPassword;
    Button btnLogin;
    TextView linkRegister;
    private boolean passwordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_login); // Make sure this matches your XML filename

        // Initialize views
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        linkRegister = findViewById(R.id.linkRegister);

        // Toggle password visibility when eye icon is tapped
        etPassword.setOnTouchListener((v, event) -> {
            final int DRAWABLE_END = 2; // right drawable
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (etPassword.getCompoundDrawables()[DRAWABLE_END] != null) {
                    int drawableWidth = etPassword.getCompoundDrawables()[DRAWABLE_END].getBounds().width();
                    if (event.getRawX() >= (etPassword.getRight() - drawableWidth - etPassword.getPaddingEnd())) {
                        passwordVisible = !passwordVisible;
                        if (passwordVisible) {
                            etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                        } else {
                            etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                        }
                        etPassword.setSelection(etPassword.getText().length());
                        return true;
                    }
                }
            }
            return false;
        });

        // Login button click
        btnLogin.setOnClickListener(v -> {
            String emailRaw = etEmail.getText().toString().trim();
            String email = emailRaw.toLowerCase();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.setError("Please enter a valid email");
                etEmail.requestFocus();
            } else {
                // DB-only sign in
                FirebaseDatabaseHelper.getInstance()
                        .loginUserSimple(email, password, new FirebaseDatabaseHelper.SimpleAuthCallback() {
                            @Override
                            public void onSuccess(User user) {
                                saveSession(user);
                                // Update daily login streak (fire-and-forget)
                                try {
                                    long now = System.currentTimeMillis();
                                    FirebaseDatabaseHelper.getInstance()
                                            .updateDailyLoginStreak(user.getId(), now, new FirebaseDatabaseHelper.DatabaseCallback() {
                                                @Override public void onSuccess(String message) { /* no-op */ }
                                                @Override public void onError(String error) { /* no-op */ }
                                            });
                                } catch (Exception ignored) {}
                                Toast.makeText(UserLogin.this, "Login successful", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(UserLogin.this, UserDashboard.class);
                                startActivity(intent);
                                finish();
                            }

                            @Override
                            public void onError(String error) {
                                // Fallback: try with original email in case it was saved with case differences previously
                                if (error != null && error.toLowerCase().contains("no account")) {
                                    FirebaseDatabaseHelper.getInstance()
                                            .loginUserSimple(emailRaw, password, new FirebaseDatabaseHelper.SimpleAuthCallback() {
                                                @Override public void onSuccess(User user) {
                                                    saveSession(user);
                                                    // Update daily login streak (fire-and-forget)
                                                    try {
                                                        long now = System.currentTimeMillis();
                                                        FirebaseDatabaseHelper.getInstance()
                                                                .updateDailyLoginStreak(user.getId(), now, new FirebaseDatabaseHelper.DatabaseCallback() {
                                                                    @Override public void onSuccess(String message) { /* no-op */ }
                                                                    @Override public void onError(String error) { /* no-op */ }
                                                                });
                                                    } catch (Exception ignored) {}
                                                    Toast.makeText(UserLogin.this, "Login successful", Toast.LENGTH_SHORT).show();
                                                    Intent intent = new Intent(UserLogin.this, UserDashboard.class);
                                                    startActivity(intent);
                                                    finish();
                                                }
                                                @Override public void onError(String err2) {
                                                    applyDbLoginError(err2);
                                                    Toast.makeText(UserLogin.this, err2 != null ? err2 : "Login failed", Toast.LENGTH_LONG).show();
                                                }
                                            });
                                } else {
                                    applyDbLoginError(error);
                                    Toast.makeText(UserLogin.this, error != null ? error : "Login failed", Toast.LENGTH_LONG).show();
                                }
                            }
                        });
            }
        });

        // Register link click
        linkRegister.setOnClickListener(v -> {
            Intent intent = new Intent(UserLogin.this, UserRegister.class);
            startActivity(intent);
        });
    }

    private void applyDbLoginError(String message) {
        if (message == null) return;
        String m = message.toLowerCase();
        if (m.contains("no account")) {
            etEmail.setError("No account found with this email");
            etEmail.requestFocus();
        } else if (m.contains("incorrect password")) {
            etPassword.setError("Incorrect password");
            etPassword.requestFocus();
        } else if (m.contains("invalid email")) {
            etEmail.setError("Invalid email format");
            etEmail.requestFocus();
        }
    }

    private void saveSession(User user) {
        if (user == null) return;
        SharedPreferences prefs = getSharedPreferences("profile_prefs", MODE_PRIVATE);
        prefs.edit()
                .putString("id", user.getId())
                .putString("name", user.getName())
                .putString("email", user.getEmail())
                .apply();
        // Username is no longer used/cached
    }
}
