package svvaap.quizastra;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.MotionEvent;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class UserRegister extends AppCompatActivity {

    private ViewFlipper viewFlipper;
    private Button btnCompleteRegistration;
    private Button btnNextStep1, btnBackStep1;
    private Button btnFinishRegistration;
    private Button btnGetStarted;
    private Button btnUploadPhoto, btnCapturePhoto;
    private ImageButton btnBackPhoto, btnBackTerms, btnBackPersonal;
    private CheckBox checkboxTerms;
    private EditText etPassword, etConfirmPassword, etFullName, etEmail, etPhoneReg, etDob;
    private ProgressBar passwordStrengthBar;
    private TextView tvPasswordStrength;
    // Hold data across steps
    private String pendingEmail = "";
    private String pendingPassword = "";
    private String pendingFullName = "";
    private String pendingPhone = "";
    private String pendingDob = "";
    // Registration photo
    private de.hdodenhof.circleimageview.CircleImageView profileImage;
    private String regPhotoBase64 = "";
    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<Void> takePhotoLauncher;
    
    // Custom step ordering to match desired flow: 0 About, 3 Personal, 1 Create, 2 Photo, 4 Terms, 5 Welcome
    private final int[] stepOrder = new int[]{0, 3, 1, 2, 4, 5};
    private int stepPos = 0; // index into stepOrder

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_register);
        
        // Initialize ViewFlipper
        viewFlipper = findViewById(R.id.viewFlipper);
        // Ensure starting on About step
        if (viewFlipper != null) {
            viewFlipper.setDisplayedChild(stepOrder[stepPos]);
        }
        
        // Initialize UI elements for each step
        initializeStep1UI();
        initializeStep2UI();
        initializeStep3UI();
        initializeStep4UI();
        initializeStep5UI();
        setupImagePickers();
        initializeAboutStep();
    }

    private boolean handlePasswordToggle(EditText field, MotionEvent event) {
        final int DRAWABLE_END = 2; // Right drawable index
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (field.getCompoundDrawables()[DRAWABLE_END] != null) {
                int width = field.getCompoundDrawables()[DRAWABLE_END].getBounds().width();
                if (event.getRawX() >= (field.getRight() - width - field.getPaddingEnd())) {
                    boolean isVisible = field.getTransformationMethod() instanceof PasswordTransformationMethod == false;
                    if (isVisible) {
                        field.setTransformationMethod(PasswordTransformationMethod.getInstance());
                    } else {
                        field.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                    }
                    field.setSelection(field.getText().length());
                    return true;
                }
            }
        }
        return false;
    }
    
    private void initializeStep1UI() {
        // Create Account step (Step 1)
        btnNextStep1 = findViewById(R.id.btnNextStep1);
        btnBackStep1 = findViewById(R.id.btnBackStep1);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        passwordStrengthBar = findViewById(R.id.passwordStrengthBar);
        tvPasswordStrength = findViewById(R.id.tvPasswordStrength);
        // Eye icon toggles for password fields
        etPassword.setOnTouchListener((v, event) -> handlePasswordToggle(etPassword, event));
        etConfirmPassword.setOnTouchListener((v, event) -> handlePasswordToggle(etConfirmPassword, event));
        
        btnNextStep1.setOnClickListener(v -> {
            if (validateAccountDetails()) {
                // Save password for later registration
                pendingPassword = etPassword.getText().toString().trim();
                Toast.makeText(UserRegister.this, "Account details saved", Toast.LENGTH_SHORT).show();
                moveToNextStep();
            }
        });
        
        btnBackStep1.setOnClickListener(v -> moveToPreviousStep());
        
        // Password strength checker
        etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePasswordStrength(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
    
    private void initializeStep2UI() {
        // Profile Photo step (Step 2)
        btnUploadPhoto = findViewById(R.id.btnUploadPhoto);
        btnCapturePhoto = findViewById(R.id.btnTakePhoto);
        btnBackPhoto = findViewById(R.id.btnBackPhoto);
        profileImage = findViewById(R.id.profileImage);
        Button btnBackStep2 = findViewById(R.id.btnBackStep2);
        Button btnNextStep2 = findViewById(R.id.btnNextStep2);
        
        btnBackPhoto.setOnClickListener(v -> moveToPreviousStep());
        btnBackStep2.setOnClickListener(v -> moveToPreviousStep());
        
        btnUploadPhoto.setOnClickListener(v -> showImageSizeWarning(false));
        
        btnNextStep2.setOnClickListener(v -> {
            Toast.makeText(UserRegister.this, "Profile photo step completed", Toast.LENGTH_SHORT).show();
            moveToNextStep();
        });
        
        btnCapturePhoto.setOnClickListener(v -> showImageSizeWarning(true));
    }

    private void setupImagePickers() {
        // Gallery picker
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                onImageSelectedFromUri(uri);
            }
        });
        // Camera preview to Bitmap (no file provider required)
        takePhotoLauncher = registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bmp -> {
            if (bmp != null) {
                onImageCaptured(bmp);
            }
        });
    }

    private void showImageSizeWarning(boolean forCamera) {
        new AlertDialog.Builder(this)
                .setTitle("Select Profile Photo")
                .setMessage("Please select an image up to 1MB in size for best performance.\n\nLarger images will be automatically resized to fit the limit.")
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton(forCamera ? "Open Camera" : "Choose Photo", (d, w) -> {
                    if (forCamera) takePhotoLauncher.launch(null); else pickImageLauncher.launch("image/*");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void onImageSelectedFromUri(Uri uri) {
        try {
            if (profileImage != null) profileImage.setImageURI(uri);
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) {
                Toast.makeText(this, "Unable to read image", Toast.LENGTH_SHORT).show();
                return;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int nRead;
            int total = 0;
            int max = 1000 * 1024; // 1MB cap
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
                total += nRead;
                if (total > max) break;
            }
            buffer.flush();
            is.close();
            byte[] bytes = buffer.toByteArray();
            regPhotoBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            Toast.makeText(this, "Photo attached", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            regPhotoBase64 = "";
            Toast.makeText(this, "Failed to attach photo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void onImageCaptured(Bitmap bmp) {
        try {
            if (profileImage != null) profileImage.setImageBitmap(bmp);
            // Compress to JPEG and cap at ~1MB
            int quality = 92;
            byte[] bytes;
            do {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, quality, out);
                bytes = out.toByteArray();
                quality -= 7; // step down quality if too big
            } while (bytes.length > 1000 * 1024 && quality > 30);
            regPhotoBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            Toast.makeText(this, "Photo captured", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            regPhotoBase64 = "";
            Toast.makeText(this, "Failed to capture photo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void initializeStep3UI() {
        // Terms & Conditions step (Step 3)
        checkboxTerms = findViewById(R.id.checkboxTerms);
        btnBackTerms = findViewById(R.id.btnBackTerms);
        Button btnBackStep3 = findViewById(R.id.btnBackStep3);
        Button btnNextStep3 = findViewById(R.id.btnNextStep3);
        
        btnBackTerms.setOnClickListener(v -> moveToPreviousStep());
        btnBackStep3.setOnClickListener(v -> moveToPreviousStep());
        
        btnNextStep3.setOnClickListener(v -> {
            if (checkboxTerms.isChecked()) {
                // Ensure credentials collected in earlier steps
                if (pendingPassword == null || pendingPassword.isEmpty()) {
                    Toast.makeText(UserRegister.this, "Please set a password in Step 1", Toast.LENGTH_LONG).show();
                    // Navigate back towards Step 1
                    while (stepPos > 1) moveToPreviousStep();
                    etPassword.requestFocus();
                    return;
                }
                if (pendingPassword.length() < 6) {
                    etPassword.setError("Password too short (min 6 characters)");
                    Toast.makeText(UserRegister.this, "Password too short (min 6 characters)", Toast.LENGTH_LONG).show();
                    while (stepPos > 1) moveToPreviousStep();
                    return;
                }

                // Perform DB registration now using collected personal info
                FirebaseDatabaseHelper db = FirebaseDatabaseHelper.getInstance();
                User user = new User(null, pendingFullName, pendingEmail, pendingPhone, null);
                // Temporarily store DOB into User via setter if available later; fallback via profile update
                db.registerUserSimple(user, "", pendingPassword, regPhotoBase64, new FirebaseDatabaseHelper.SimpleAuthCallback() {
                    @Override
                    public void onSuccess(User created) {
                        SharedPreferences prefs = getSharedPreferences("profile_prefs", MODE_PRIVATE);
                        prefs.edit()
                                .putString("photoBase64", regPhotoBase64)
                                .apply();
                        saveSession(created);
                        // If DOB was provided, update profile with it
                        if (created != null && created.getId() != null && !pendingDob.isEmpty()) {
                            java.util.Map<String, Object> up = new java.util.HashMap<>();
                            up.put("dob", pendingDob);
                            FirebaseDatabaseHelper.getInstance().updateUserProfile(created.getId(), up, new FirebaseDatabaseHelper.SimpleCallback() {
                                @Override public void onSuccess() { /* no-op */ }
                                @Override public void onError(String error) { /* no-op */ }
                            });
                        }
                        Toast.makeText(UserRegister.this, "Registered successfully", Toast.LENGTH_SHORT).show();
                        moveToNextStep();
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(UserRegister.this, error != null ? error : "Registration failed", Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                Toast.makeText(UserRegister.this, "Please agree to the Terms & Conditions to continue", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initializeAboutStep() {
        // About step is the first child; simple Next button moves to Step 1 (Create Account)
        Button btnNextAbout = findViewById(R.id.btnNextAbout);
        if (btnNextAbout != null) {
            btnNextAbout.setOnClickListener(v -> moveToNextStep());
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
    
    private void initializeStep4UI() {
        // Personal Information step (now Step 3 visually after About & Photo)
        btnFinishRegistration = findViewById(R.id.btnFinishRegistration);
        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        btnBackPersonal = findViewById(R.id.btnBackPersonal);
        etPhoneReg = findViewById(R.id.etPhoneReg);
        etDob = findViewById(R.id.etDob);

        // Date picker for DOB
        if (etDob != null) {
            etDob.setOnClickListener(v -> showDobPicker());
        }
        
        btnFinishRegistration.setOnClickListener(v -> {
            if (validatePersonalInfo()) {
                // Persist the collected inputs; actual registration occurs after Terms acceptance
                pendingFullName = etFullName.getText().toString().trim();
                pendingEmail = etEmail.getText().toString().trim().toLowerCase();
                pendingPhone = etPhoneReg != null ? etPhoneReg.getText().toString().trim() : "";
                pendingDob = etDob != null ? etDob.getText().toString().trim() : "";
                moveToNextStep();
            }
        });
        
        btnBackPersonal.setOnClickListener(v -> moveToPreviousStep());
    }
    
    private void initializeStep5UI() {
        // Welcome Screen step (Step 5)
        btnGetStarted = findViewById(R.id.btnGetStarted);
        
        btnGetStarted.setOnClickListener(v -> {
            // Registration complete, navigate to dashboard
            Toast.makeText(UserRegister.this, "Welcome to QuizAstra!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(UserRegister.this, UserDashboard.class);
            startActivity(intent);
            finish();
        });
    }
    
    private void moveToNextStep() {
        if (stepPos < stepOrder.length - 1) {
            stepPos++;
            if (viewFlipper != null) viewFlipper.setDisplayedChild(stepOrder[stepPos]);
        }
    }
    
    private void moveToPreviousStep() {
        if (stepPos > 0) {
            stepPos--;
            if (viewFlipper != null) viewFlipper.setDisplayedChild(stepOrder[stepPos]);
        } else {
            finish(); // Go back to login
        }
    }
    
    private boolean validateAccountDetails() {
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (password.isEmpty()) {
            etPassword.setError("Password is required");
            Toast.makeText(UserRegister.this, "Please enter a password", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (confirmPassword.isEmpty()) {
            etConfirmPassword.setError("Please confirm your password");
            Toast.makeText(UserRegister.this, "Please confirm your password", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            Toast.makeText(UserRegister.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        return true;
    }
    
    private boolean validatePersonalInfo() {
        String fullName = etFullName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhoneReg != null ? etPhoneReg.getText().toString().trim() : "";
        String dobStr = etDob != null ? etDob.getText().toString().trim() : "";
        
        if (fullName.isEmpty()) {
            etFullName.setError("Full name is required");
            Toast.makeText(UserRegister.this, "Please enter your full name", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (email.isEmpty()) {
            etEmail.setError("Email is required");
            Toast.makeText(UserRegister.this, "Please enter your email address", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Please enter a valid email address");
            Toast.makeText(UserRegister.this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            return false;
        }
        // Optional phone basic validation if provided
        if (!phone.isEmpty() && phone.replaceAll("[^0-9]", "").length() < 7) {
            etPhoneReg.setError("Enter a valid phone number");
            return false;
        }
        if (dobStr.isEmpty()) {
            etDob.setError("Please select your date of birth");
            return false;
        }
        
        return true;
    }
    
    private void updatePasswordStrength(String password) {
        // Simple password strength calculation
        int strength = 0;
        
        if (password.length() >= 8) strength += 20;
        if (password.matches(".*[A-Z].*")) strength += 20;
        if (password.matches(".*[a-z].*")) strength += 20;
        if (password.matches(".*[0-9].*")) strength += 20;
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};:\\\\|,.<>/?].*")) strength += 20;
        
        String strengthLabel;
        int progress;
        
        if (strength <= 20) {
            strengthLabel = "Weak";
            progress = strength;
        } else if (strength <= 60) {
            strengthLabel = "Medium";
            progress = strength;
        } else {
            strengthLabel = "Strong";
            progress = strength;
        }
        
        tvPasswordStrength.setText(strengthLabel);
        passwordStrengthBar.setProgress(progress);
    }

    private void saveSession(User user) {
        if (user == null) return;
        SharedPreferences prefs = getSharedPreferences("profile_prefs", MODE_PRIVATE);
        prefs.edit()
                .putString("id", user.getId())
                .putString("name", user.getName())
                .putString("email", user.getEmail())
                .apply();
    }

    private void applyRegisterFieldErrors(String code) {
        if (code == null) return;
        switch (code) {
            case "ERROR_INVALID_EMAIL":
                etEmail.setError("Invalid email format");
                etEmail.requestFocus();
                break;
            case "ERROR_EMAIL_ALREADY_IN_USE":
                etEmail.setError("Email already in use");
                etEmail.requestFocus();
                break;
            case "ERROR_WEAK_PASSWORD":
                etPassword.setError("Weak password (min 6 characters)");
                etPassword.requestFocus();
                break;
            default:
                // no-op
        }
    }

    private String userFriendlyRegisterMessage(String code, String fallback) {
        if (code == null) return fallback != null ? fallback : "Registration failed";
        switch (code) {
            case "ERROR_INVALID_EMAIL": return "Please enter a valid email address.";
            case "ERROR_EMAIL_ALREADY_IN_USE": return "This email is already registered.";
            case "ERROR_WEAK_PASSWORD": return "Password too weak. Use at least 6 characters.";
            case "ERROR_OPERATION_NOT_ALLOWED": return "Email/Password sign-up is disabled. Enable it in Firebase Console.";
            case "ERROR_NETWORK_REQUEST_FAILED": return "Network error. Check your internet connection.";
            default:
                return (fallback != null && !fallback.isEmpty()) ? fallback : ("Registration failed (" + code + ")");
        }
    }

    @Override
    public void onBackPressed() {
        if (stepPos > 0) {
            moveToPreviousStep();
        } else {
            super.onBackPressed();
        }
    }
}