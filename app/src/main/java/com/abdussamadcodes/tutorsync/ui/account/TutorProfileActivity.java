package com.abdussamadcodes.tutorsync.ui.account;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.account.LocalAccountStore;
import com.abdussamadcodes.tutorsync.databinding.ActivityTutorProfileBinding;
import com.abdussamadcodes.tutorsync.model.Account;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TutorProfileActivity extends BaseActivity {
    private ActivityTutorProfileBinding binding;
    private SessionManager sessionManager;
    private LocalAccountStore accountStore;
    private String activeAccountId;
    private String currentProfilePicPath;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onGalleryImagePicked);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTutorProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.tutorProfileRoot);

        sessionManager = new SessionManager(this);
        accountStore = new LocalAccountStore(this);
        activeAccountId = sessionManager.getActiveAccountId();

        if (activeAccountId.isEmpty()) {
            finish();
            return;
        }

        loadCurrentProfile();
        setupListeners();
    }

    private void loadCurrentProfile() {
        Account account = accountStore.getAccountById(activeAccountId);
        if (account == null) return;

        binding.etFullName.setText(account.getFullName());
        binding.etPhone.setText(account.getPhone() != null ? account.getPhone() : "");
        binding.etEmail.setText(account.getEmail() != null ? account.getEmail() : "");
        binding.etDob.setText(account.getDob() != null ? account.getDob() : "");
        binding.etAddress.setText(account.getAddress() != null ? account.getAddress() : "");

        currentProfilePicPath = account.getProfilePicPath();
        renderProfilePhoto();
    }

    private void renderProfilePhoto() {
        if (currentProfilePicPath != null && !currentProfilePicPath.isEmpty()) {
            File imgFile = new File(currentProfilePicPath);
            if (imgFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                if (bitmap != null) {
                    binding.ivProfilePhoto.setImageTintList(null);
                    binding.ivProfilePhoto.setImageBitmap(bitmap);
                    binding.btnRemovePhoto.setVisibility(View.VISIBLE);
                    return;
                }
            }
        }
        binding.ivProfilePhoto.setImageResource(R.drawable.ic_tutor_logo);
        binding.btnRemovePhoto.setVisibility(View.GONE);
    }

    private void setupListeners() {
        binding.btnSelectPhoto.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        binding.cardAvatar.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        binding.btnRemovePhoto.setOnClickListener(v -> {
            if (currentProfilePicPath != null) {
                File oldFile = new File(currentProfilePicPath);
                if (oldFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    oldFile.delete();
                }
            }
            currentProfilePicPath = null;
            renderProfilePhoto();
        });

        binding.etDob.setOnClickListener(v -> showDobPicker());

        binding.btnSaveProfile.setOnClickListener(v -> saveProfile());
    }

    private void showDobPicker() {
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.profile_dob_hint)
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            binding.etDob.setText(sdf.format(new Date(selection)));
        });

        picker.show(getSupportFragmentManager(), "dob_picker");
    }

    private void onGalleryImagePicked(@Nullable Uri uri) {
        if (uri == null) return;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return;
            Bitmap decoded = BitmapFactory.decodeStream(is);
            if (decoded == null) return;

            // Scale down reasonably to keep storage and backup compact
            Bitmap scaled = scaleBitmapDown(decoded, 512);
            File destFile = new File(getFilesDir(), "profile_" + activeAccountId + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(destFile)) {
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                fos.flush();
            }
            currentProfilePicPath = destFile.getAbsolutePath();
            renderProfilePhoto();
        } catch (Exception e) {
            showMessage(binding.getRoot(), R.string.profile_photo_error);
        }
    }

    private Bitmap scaleBitmapDown(Bitmap realImage, int maxImageSize) {
        float ratio = Math.min(
                (float) maxImageSize / realImage.getWidth(),
                (float) maxImageSize / realImage.getHeight());
        if (ratio >= 1.0f) return realImage;
        int width = Math.round(ratio * realImage.getWidth());
        int height = Math.round(ratio * realImage.getHeight());
        return Bitmap.createScaledBitmap(realImage, width, height, true);
    }

    private void saveProfile() {
        String fullName = binding.etFullName.getText() != null
                ? binding.etFullName.getText().toString().trim() : "";

        if (fullName.isEmpty()) {
            binding.tilFullName.setError(getString(R.string.profile_err_name_required));
            return;
        }
        binding.tilFullName.setError(null);

        String phone = binding.etPhone.getText() != null ? binding.etPhone.getText().toString().trim() : "";
        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        String dob = binding.etDob.getText() != null ? binding.etDob.getText().toString().trim() : "";
        String address = binding.etAddress.getText() != null ? binding.etAddress.getText().toString().trim() : "";

        boolean updated = accountStore.updateProfile(
                activeAccountId,
                fullName,
                phone.isEmpty() ? null : phone,
                email.isEmpty() ? null : email,
                address.isEmpty() ? null : address,
                dob.isEmpty() ? null : dob,
                currentProfilePicPath
        );

        if (updated) {
            finish();
        } else {
            showMessage(binding.getRoot(), R.string.profile_save_failed);
        }
    }
}