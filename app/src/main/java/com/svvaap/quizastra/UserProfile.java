package com.svvaap.quizastra;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.exifinterface.media.ExifInterface;

public class UserProfile extends AppCompatActivity {

    private ImageView imageProfile;
    private TextView textName, textEmail, textPhone, textBio, textPhotoHint, textDob;
    private TextView textStreak, textProfileCompletion;
    private TextView textWins, textLosses;
    private EditText etName, etEmail, etPhone, etBio, etCurrentPassword, etNewPassword, etConfirmNewPassword, etDob;
    private LinearLayout editContainer, saveCancelContainer;
    private ImageView ibEditProfile;
    private ImageButton ibShareProfile;
    private Button btnSaveProfile, btnCancelEdit;
    private Button btnQuickEdit, btnQuickLeaderboard, btnQuickShare;
    private ProgressBar progressProfileCompletion;

    // Live stats observation
    private com.google.firebase.database.ValueEventListener statsListener;
    private String observedUserId;

    private SharedPreferences preferences;
    private static final String PREFS = "profile_prefs";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_BIO = "bio";
    private static final String KEY_PHOTO_URI = "photo_uri";

    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        BottomNavigationView navView = findViewById(R.id.bottomNav);
        navView.setSelectedItemId(R.id.nav_profile);
        BottomNavHandler.setup(this, navView);

        bindViews();
        setupPickImage();
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadProfile();
        setupClicks();
    }

    @Override
    protected void onStart() {
        super.onStart();
        String userId = getSharedPreferences("profile_prefs", MODE_PRIVATE).getString("id", null);
        if (userId != null && !userId.isEmpty() && statsListener == null) {
            observedUserId = userId;
            statsListener = FirebaseDatabaseHelper.getInstance().observeUserStats(userId, new FirebaseDatabaseHelper.UserStatsCallback() {
                @Override public void onStatsLoaded(FirebaseDatabaseHelper.UserStats stats) {
                    int totalQuizzes = stats != null ? stats.totalQuizzes : 0;
                    int bestScore = stats != null ? stats.bestScore : 0;
                    int currentStreak = stats != null ? stats.currentStreak : 0;
                    int wins = stats != null ? stats.wins : 0;
                    int losses = stats != null ? stats.losses : 0;
                    TextView tvQuizzes = findViewById(R.id.textQuizzes);
                    TextView tvScore = findViewById(R.id.textScore);
                    TextView tvStreak = textStreak;
                    TextView tvWins = textWins;
                    TextView tvLosses = textLosses;
                    if (tvQuizzes != null) tvQuizzes.setText(String.valueOf(totalQuizzes));
                    if (tvScore != null) tvScore.setText(String.valueOf(bestScore));
                    if (tvStreak != null) tvStreak.setText(String.valueOf(currentStreak));
                    if (tvWins != null) tvWins.setText(String.valueOf(wins));
                    if (tvLosses != null) tvLosses.setText(String.valueOf(losses));
                }
                @Override public void onError(String error) { /* ignore for UI */ }
            });
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (observedUserId != null && statsListener != null) {
            FirebaseDatabaseHelper.getInstance().stopObservingUserStats(observedUserId, statsListener);
            statsListener = null;
            observedUserId = null;
        }
    }

    private void bindViews() {
        imageProfile = findViewById(R.id.imageProfile);
        textName = findViewById(R.id.textName);
        textEmail = findViewById(R.id.textEmail);
        textPhone = findViewById(R.id.textPhone);
        textDob = findViewById(R.id.textDob);
        textBio = findViewById(R.id.textBio);
        textPhotoHint = findViewById(R.id.textPhotoHint);
        textStreak = findViewById(R.id.textStreak);
        textProfileCompletion = null; // removed from layout
        textWins = findViewById(R.id.textWins);
        textLosses = findViewById(R.id.textLosses);
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etDob = findViewById(R.id.etDob);
        etBio = findViewById(R.id.etBio);
        etCurrentPassword = findViewById(R.id.etCurrentPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword);
        editContainer = findViewById(R.id.editContainer);
        saveCancelContainer = findViewById(R.id.saveCancelContainer);
        ibEditProfile = findViewById(R.id.ibEditProfile);
        ibShareProfile = findViewById(R.id.ibShareProfile);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnCancelEdit = findViewById(R.id.btnCancelEdit);
        btnQuickEdit = null; // removed from layout
        btnQuickLeaderboard = null; // removed from layout
        btnQuickShare = null; // removed from layout (replaced by ibShareProfile)
        progressProfileCompletion = null; // removed from layout
    }

    private void setupPickImage() {
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                onImageSelected(uri);
            }
        });
    }

    private void onImageSelected(Uri uri) {
        try {
            // Ensure we can open the stream (then close immediately; actual decode happens separately)
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) {
                Toast.makeText(this, "Unable to read image", Toast.LENGTH_SHORT).show();
                return;
            }
            try { is.close(); } catch (Exception ignored) {}

            // Decode a scaled bitmap from Uri to keep memory and size manageable
            Bitmap bitmap = decodeScaledBitmapFromUri(uri, 1080, 1080);
            if (bitmap == null) {
                Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show();
                return;
            }

            // Apply EXIF orientation if available
            bitmap = applyExifOrientation(uri, bitmap);

            // Compress to <= 1MB without truncation
            final int MAX_BYTES = 1000 * 1024; // 1MB cap
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int quality = 90;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, buffer);
            while (buffer.size() > MAX_BYTES && quality > 50) {
                buffer.reset();
                quality -= 10;
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, buffer);
            }
            byte[] bytes = buffer.toByteArray();

            // Base64-encode
            String imageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP);

            // Update preview with proper scaling
            if (imageProfile != null) {
                imageProfile.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageProfile.setAdjustViewBounds(true);
                imageProfile.setImageBitmap(bitmap);
            }

            // Save locally
            preferences.edit().putString(KEY_PHOTO_URI, imageBase64).apply();

            // Save to Firebase
            String userId = getSharedPreferences("profile_prefs", MODE_PRIVATE).getString("id", null);
            if (userId != null && !userId.isEmpty()) {
                java.util.Map<String, Object> updates = new java.util.HashMap<>();
                updates.put("photoBase64", imageBase64);
                FirebaseDatabaseHelper.getInstance().updateUserProfile(userId, updates, new FirebaseDatabaseHelper.SimpleCallback() {
                    @Override public void onSuccess() { 
                        Toast.makeText(UserProfile.this, "Image uploaded", Toast.LENGTH_SHORT).show();
                        computeAndRenderProfileCompletion();
                    }
                    @Override public void onError(String error) { 
                        Toast.makeText(UserProfile.this, "Failed to upload image: " + error, Toast.LENGTH_SHORT).show(); 
                    }
                });
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to attach image: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ... (rest of the code remains the same)

    private void loadBase64Image(String base64String) {
        try {
            if (base64String == null || base64String.isEmpty()) {
                return;
            }

            // Remove data:image prefix if present
            String cleanBase64 = base64String;
            if (base64String.startsWith("data:image")) {
                int commaIndex = base64String.indexOf(",");
                if (commaIndex != -1) {
                    cleanBase64 = base64String.substring(commaIndex + 1);
                }
            }

            // Decode Base64 to byte array
            byte[] decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT);

            // Convert to Bitmap with preferred config
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length, opts);

            if (bitmap != null && imageProfile != null) {
                imageProfile.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageProfile.setAdjustViewBounds(true);
                imageProfile.setImageBitmap(bitmap);
            }
        } catch (Exception e) {
            // Silently fail - don't show error toast for image loading issues
            // The default profile image will remain visible
        }
    }

    // Decode a scaled bitmap from Uri to keep memory and size manageable
    private Bitmap decodeScaledBitmapFromUri(@NonNull Uri uri, int reqWidth, int reqHeight) throws IOException {
        // First decode bounds
        InputStream boundsIs = getContentResolver().openInputStream(uri);
        if (boundsIs == null) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(boundsIs, null, opts);
        boundsIs.close();

        // Calculate inSampleSize
        opts.inSampleSize = calculateInSampleSize(opts, reqWidth, reqHeight);
        opts.inJustDecodeBounds = false;

        // Decode with inSampleSize set
        InputStream decodeIs = getContentResolver().openInputStream(uri);
        if (decodeIs == null) return null;
        Bitmap bitmap = BitmapFactory.decodeStream(decodeIs, null, opts);
        decodeIs.close();
        return bitmap;
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    // Apply EXIF orientation if available
    private Bitmap applyExifOrientation(@NonNull Uri uri, @NonNull Bitmap bitmap) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return bitmap;
            ExifInterface exif = new ExifInterface(is);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: matrix.postRotate(90); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.preScale(-1, 1); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.preScale(1, -1); break;
                default: return bitmap;
            }
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) bitmap.recycle();
            return rotated;
        } catch (Exception ignored) {
            return bitmap;
        }
    }

    /**
     * Show warning dialog about image size limit before allowing user to select photo
     */
    private void showImageSizeWarning() {
        new AlertDialog.Builder(this)
                .setTitle("Select Profile Photo")
                .setMessage("Please select an image up to 1MB in size for best performance.\n\nLarger images will be automatically resized to fit the limit.")
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton("Choose Photo", (dialog, which) -> {
                    pickImageLauncher.launch("image/*");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupClicks() {
        if (ibEditProfile != null) {
            ibEditProfile.setOnClickListener(v -> enterEditMode());
        }
        btnCancelEdit.setOnClickListener(v -> exitEditMode(false));
        btnSaveProfile.setOnClickListener(v -> saveProfile());
        imageProfile.setOnClickListener(v -> {
            if (editContainer.getVisibility() == View.VISIBLE) {
                showImageSizeWarning();
            }
        });
        if (etDob != null) {
            etDob.setOnClickListener(v -> showDobPicker());
        }
        if (btnQuickEdit != null) {
            btnQuickEdit.setOnClickListener(v -> {
                enterEditMode();
                // Scroll to top so edit fields are visible
                View root = findViewById(android.R.id.content);
                if (root != null) root.post(() -> root.scrollTo(0, 0));
            });
        }
        if (btnQuickLeaderboard != null) {
            btnQuickLeaderboard.setOnClickListener(v -> openLeaderboard());
        }
        if (btnQuickShare != null) { btnQuickShare.setOnClickListener(v -> shareProfile()); }
        if (ibShareProfile != null) { ibShareProfile.setOnClickListener(v -> shareProfile()); }
    }

    private void enterEditMode() {
        etName.setText(textName.getText());
        etEmail.setText(textEmail.getText());
        etPhone.setText(textPhone.getText());
        etBio.setText(textBio.getText());
        if (textDob != null && etDob != null) {
            String dv = textDob.getText() == null ? "" : textDob.getText().toString();
            if (dv.startsWith("DOB:")) {
                dv = dv.substring(4).trim();
            }
            etDob.setText(dv);
        }
        editContainer.setVisibility(View.VISIBLE);
        saveCancelContainer.setVisibility(View.VISIBLE);
        if (textPhotoHint != null) textPhotoHint.setVisibility(View.VISIBLE);
    }

    private void exitEditMode(boolean saved) {
        editContainer.setVisibility(View.GONE);
        saveCancelContainer.setVisibility(View.GONE);
        if (textPhotoHint != null) textPhotoHint.setVisibility(View.GONE);
        if (saved) {
            Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveProfile() {
        String name = safeText(etName);
        String email = safeText(etEmail);
        String phone = safeText(etPhone);
        String bio = safeText(etBio);
        String dob = safeText(etDob);
        String curPw = safeText(etCurrentPassword);
        String newPw = safeText(etNewPassword);
        String confPw = safeText(etConfirmNewPassword);

        if (TextUtils.isEmpty(name)) { etName.setError("Required"); return; }
        if (TextUtils.isEmpty(email)) { etEmail.setError("Required"); return; }
        if (!TextUtils.isEmpty(newPw) || !TextUtils.isEmpty(confPw) || !TextUtils.isEmpty(curPw)) {
            if (TextUtils.isEmpty(curPw)) { etCurrentPassword.setError("Required"); return; }
            if (newPw.length() < 6) { etNewPassword.setError("Min 6 chars"); return; }
            if (!newPw.equals(confPw)) { etConfirmNewPassword.setError("Passwords do not match"); return; }
        }
        // Save to Firebase
        String userId = getSharedPreferences("profile_prefs", MODE_PRIVATE).getString("id", null);
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "No session", Toast.LENGTH_SHORT).show();
            return;
        }
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("name", name);
        updates.put("email", email);
        updates.put("phone", phone);
        updates.put("dob", dob);
        updates.put("bio", bio);

        FirebaseDatabaseHelper.getInstance().updateUserProfile(userId, updates, new FirebaseDatabaseHelper.SimpleCallback() {
            @Override public void onSuccess() {
                applyProfileToViews(name, email, bio, phone, dob);
                if (!TextUtils.isEmpty(newPw)) {
                    FirebaseDatabaseHelper.getInstance().changePassword(userId, curPw, newPw, new FirebaseDatabaseHelper.SimpleCallback() {
                        @Override public void onSuccess() { /* optional toast */ }
                        @Override public void onError(String error) { Toast.makeText(UserProfile.this, error, Toast.LENGTH_SHORT).show(); }
                    });
                }
                exitEditMode(true);
                computeAndRenderProfileCompletion();
            }
            @Override public void onError(String error) { Toast.makeText(UserProfile.this, error, Toast.LENGTH_LONG).show(); }
        });
    }

    private String safeText(@NonNull EditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private void loadProfile() {
        String userId = getSharedPreferences("profile_prefs", MODE_PRIVATE).getString("id", null);
        if (userId == null || userId.isEmpty()) {
            // fallback to local prefs
            String name = preferences.getString(KEY_NAME, "USER NAME");
            String email = preferences.getString(KEY_EMAIL, "user@example.com");
            String phone = preferences.getString("phone", "+0 0000000000");
            String bio = preferences.getString(KEY_BIO, "Bio: Lifelong learner.");
            String dob = preferences.getString("dob", "");
            applyProfileToViews(name, email, bio, phone, dob);
            String photo = preferences.getString(KEY_PHOTO_URI, null);
            if (photo != null) {
                // Check if it's Base64 or URI
                if (photo.startsWith("data:") || photo.length() > 100) {
                    // Likely Base64
                    loadBase64Image(photo);
                } else {
                    // Likely URI
                    try { imageProfile.setImageURI(Uri.parse(photo)); } catch (Exception ignored) {}
                }
            }
            computeAndRenderProfileCompletion();
            return;
        }
        FirebaseDatabaseHelper.getInstance().getUserProfile(userId, new FirebaseDatabaseHelper.UserProfileCallback() {
            @Override public void onLoaded(FirebaseDatabaseHelper.UserProfileData d) {
                applyProfileToViews(d.name, d.email, d.bio, d.phone, d.dob);
                // Load profile image - prioritize Base64 for cross-device sync
                if (d.photoBase64 != null && !d.photoBase64.isEmpty()) {
                    loadBase64Image(d.photoBase64);
                } else if (d.photoUri != null && !d.photoUri.isEmpty()) {
                    try { imageProfile.setImageURI(Uri.parse(d.photoUri)); } catch (Exception ignored) {}
                }
                // load stats for tiles
                FirebaseDatabaseHelper.getInstance().getUserStats(userId, new FirebaseDatabaseHelper.UserStatsCallback() {
                    @Override public void onStatsLoaded(FirebaseDatabaseHelper.UserStats stats) {
                        int totalQuizzes = stats != null ? stats.totalQuizzes : 0;
                        int bestScore = stats != null ? stats.bestScore : 0;
                        int currentStreak = stats != null ? stats.currentStreak : 0;
                        int wins = stats != null ? stats.wins : 0;
                        int losses = stats != null ? stats.losses : 0;
                        TextView tvQuizzes = findViewById(R.id.textQuizzes);
                        TextView tvScore = findViewById(R.id.textScore);
                        TextView tvStreak = textStreak;
                        TextView tvWins = textWins;
                        TextView tvLosses = textLosses;
                        if (tvQuizzes != null) tvQuizzes.setText(String.valueOf(totalQuizzes));
                        if (tvScore != null) tvScore.setText(String.valueOf(bestScore));
                        if (tvStreak != null) tvStreak.setText(String.valueOf(currentStreak));
                        if (tvWins != null) tvWins.setText(String.valueOf(wins));
                        if (tvLosses != null) tvLosses.setText(String.valueOf(losses));
                        // simple tile clicks
                        if (tvQuizzes != null) tvQuizzes.setOnClickListener(v -> openLeaderboard());
                        if (tvScore != null) tvScore.setOnClickListener(v -> openLeaderboard());
                        if (tvStreak != null) tvStreak.setOnClickListener(v -> Toast.makeText(UserProfile.this, "Current streak: " + currentStreak, Toast.LENGTH_SHORT).show());
                    }
                    @Override public void onError(String error) { /* ignore */ }
                });
                computeAndRenderProfileCompletion();
            }
            @Override public void onError(String error) {
                Toast.makeText(UserProfile.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void computeAndRenderProfileCompletion() {
        try {
            int total = 5; // name, email, phone, dob, bio
            int filled = 0;
            if (textName != null && !isEmpty(textName.getText())) filled++;
            if (textEmail != null && !isEmpty(textEmail.getText())) filled++;
            if (textPhone != null && !isEmpty(textPhone.getText())) filled++;
            if (textDob != null && !isEmpty(textDob.getText()) && !"DOB: --/--/----".contentEquals(textDob.getText())) filled++;
            if (textBio != null && !isEmpty(textBio.getText())) filled++;
            int percent = (int) Math.round((filled * 100.0) / total);
            if (progressProfileCompletion != null) progressProfileCompletion.setProgress(percent);
            if (textProfileCompletion != null) textProfileCompletion.setText(percent + "%");
        } catch (Exception ignored) {}
    }

    private boolean isEmpty(CharSequence cs) {
        return cs == null || cs.toString().trim().isEmpty();
    }

    private void applyProfileToViews(String name, String email, String bio, String phone, String dob) {
        textName.setText(name);
        textEmail.setText(email);
        textPhone.setText(phone == null || phone.isEmpty() ? "+0 0000000000" : phone);
        textBio.setText(bio);
        if (textDob != null) {
            String label = (dob == null || dob.isEmpty()) ? "DOB: --/--/----" : ("DOB: " + dob);
            textDob.setText(label);
        }
    }

    private void showDobPicker() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        int y = c.get(java.util.Calendar.YEAR);
        int m = c.get(java.util.Calendar.MONTH);
        int d = c.get(java.util.Calendar.DAY_OF_MONTH);
        android.app.DatePickerDialog dlg = new android.app.DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String formatted = String.format(java.util.Locale.getDefault(), "%02d-%02d-%04d", dayOfMonth, month + 1, year);
            if (etDob != null) etDob.setText(formatted);
        }, y, m, d);
        dlg.show();
    }

    private void openLeaderboard() {
        try {
            startActivity(new Intent(this, LeaderboardActivity.class));
        } catch (Exception ignored) {}
    }

    private void shareProfile() {
        String name = textName != null ? textName.getText().toString() : "My Profile";
        String score = ((TextView) findViewById(R.id.textScore)) != null ? ((TextView) findViewById(R.id.textScore)).getText().toString() : "";
        String streak = textStreak != null ? textStreak.getText().toString() : "";
        String shareText = "Check out my QuizAstra profile!\nName: " + name + (score.isEmpty() ? "" : "\nBest Score: " + score) + (streak.isEmpty() ? "" : "\nStreak: " + streak + " days");
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(sendIntent, "Share Profile"));
    }


}