package com.abdussamadcodes.tutorsync.ui.account;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.account.LocalAccountStore;
import com.abdussamadcodes.tutorsync.databinding.ActivityTutorProfileBinding;
import com.abdussamadcodes.tutorsync.model.Account;
import com.abdussamadcodes.tutorsync.security.AppLockManager;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.abdussamadcodes.tutorsync.util.AppPreferences;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TutorProfileActivity extends BaseActivity {
    private static final String STATE_PROFILE_PIC_PATH = "state_profile_pic_path";

    private ActivityTutorProfileBinding binding;
    private SessionManager sessionManager;
    private LocalAccountStore accountStore;
    private String activeAccountId;
    private String currentProfilePicPath;

    private int defaultAvatarPaddingLeft;
    private int defaultAvatarPaddingTop;
    private int defaultAvatarPaddingRight;
    private int defaultAvatarPaddingBottom;
    private ImageView.ScaleType defaultAvatarScaleType;
    private ColorStateList defaultAvatarTintList;

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

        // Capture initial XML avatar styling so we can switch cleanly between placeholder icon and full photo
        defaultAvatarPaddingLeft = binding.ivProfilePhoto.getPaddingLeft();
        defaultAvatarPaddingTop = binding.ivProfilePhoto.getPaddingTop();
        defaultAvatarPaddingRight = binding.ivProfilePhoto.getPaddingRight();
        defaultAvatarPaddingBottom = binding.ivProfilePhoto.getPaddingBottom();
        defaultAvatarScaleType = binding.ivProfilePhoto.getScaleType();
        defaultAvatarTintList = ImageViewCompat.getImageTintList(binding.ivProfilePhoto);

        wireToolbarIfPresent(binding.getRoot());
        loadCurrentProfile();

        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_PROFILE_PIC_PATH)) {
            currentProfilePicPath = savedInstanceState.getString(STATE_PROFILE_PIC_PATH);
            renderProfilePhoto();
        }

        setupListeners();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (currentProfilePicPath != null) {
            outState.putString(STATE_PROFILE_PIC_PATH, currentProfilePicPath);
        }
    }

    private void wireToolbarIfPresent(@NonNull View root) {
        if (root instanceof MaterialToolbar) {
            ((MaterialToolbar) root).setNavigationOnClickListener(v -> finish());
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                wireToolbarIfPresent(vg.getChildAt(i));
            }
        }
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
            if (imgFile.exists() && imgFile.length() > 0) {
                Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                if (bitmap != null) {
                    binding.ivProfilePhoto.setImageDrawable(null);
                    ImageViewCompat.setImageTintList(binding.ivProfilePhoto, null);
                    binding.ivProfilePhoto.clearColorFilter();
                    binding.ivProfilePhoto.setPadding(0, 0, 0, 0);
                    binding.ivProfilePhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    binding.ivProfilePhoto.setImageBitmap(bitmap);
                    binding.btnRemovePhoto.setVisibility(View.VISIBLE);
                    return;
                }
            }
        }

        binding.ivProfilePhoto.setImageBitmap(null);
        binding.ivProfilePhoto.setPadding(
                defaultAvatarPaddingLeft,
                defaultAvatarPaddingTop,
                defaultAvatarPaddingRight,
                defaultAvatarPaddingBottom
        );
        binding.ivProfilePhoto.setScaleType(
                defaultAvatarScaleType != null ? defaultAvatarScaleType : ImageView.ScaleType.CENTER_INSIDE
        );
        binding.ivProfilePhoto.setImageResource(R.drawable.ic_tutor_logo);
        ImageViewCompat.setImageTintList(binding.ivProfilePhoto, defaultAvatarTintList);
        binding.btnRemovePhoto.setVisibility(View.GONE);
    }

    private void setupListeners() {
        View.OnClickListener launchGalleryListener = v -> {
            AppLockManager.getInstance().markExternalFlowStarted();
            pickImageLauncher.launch("image/*");
        };

        binding.btnSelectPhoto.setOnClickListener(launchGalleryListener);
        binding.cardAvatar.setOnClickListener(launchGalleryListener);

        binding.btnRemovePhoto.setOnClickListener(v -> {
            if (currentProfilePicPath != null) {
                File oldFile = new File(currentProfilePicPath);
                if (oldFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    oldFile.delete();
                }
            }
            currentProfilePicPath = null;
            accountStore.updateProfilePicPath(activeAccountId, null);
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
        try {
            Bitmap scaled = decodeAndScaleFromUri(uri, 512);
            if (scaled == null) {
                showMessage(binding.getRoot(), R.string.profile_photo_error);
                return;
            }

            File destFile = new File(getFilesDir(), "profile_" + activeAccountId + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(destFile)) {
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, fos);
                fos.flush();
            }

            currentProfilePicPath = destFile.getAbsolutePath();
            accountStore.updateProfilePicPath(activeAccountId, currentProfilePicPath);
            renderProfilePhoto();
        } catch (Throwable t) {
            showMessage(binding.getRoot(), R.string.profile_photo_error);
        }
    }

    @Nullable
    private Bitmap decodeAndScaleFromUri(@NonNull Uri uri, int maxDimension) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
            return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                int width = info.getSize().getWidth();
                int height = info.getSize().getHeight();
                if (width > maxDimension || height > maxDimension) {
                    float ratio = Math.min((float) maxDimension / width, (float) maxDimension / height);
                    int targetWidth = Math.max(1, Math.round(width * ratio));
                    int targetHeight = Math.max(1, Math.round(height * ratio));
                    decoder.setTargetSize(targetWidth, targetHeight);
                }
            });
        }

        // Fallback for API 26-27: two-pass sampled decode
        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;
        try (InputStream boundsStream = getContentResolver().openInputStream(uri)) {
            if (boundsStream == null) return null;
            BitmapFactory.decodeStream(boundsStream, null, boundsOptions);
        }

        int sampleSize = 1;
        int outW = boundsOptions.outWidth;
        int outH = boundsOptions.outHeight;
        while ((outW / sampleSize) > maxDimension * 2 || (outH / sampleSize) > maxDimension * 2) {
            sampleSize *= 2;
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = sampleSize;
        decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap decoded;
        try (InputStream decodeStream = getContentResolver().openInputStream(uri)) {
            if (decodeStream == null) return null;
            decoded = BitmapFactory.decodeStream(decodeStream, null, decodeOptions);
        }

        if (decoded == null) return null;
        return scaleBitmapDown(decoded, maxDimension);
    }

    private Bitmap scaleBitmapDown(Bitmap realImage, int maxImageSize) {
        float ratio = Math.min(
                (float) maxImageSize / realImage.getWidth(),
                (float) maxImageSize / realImage.getHeight());
        if (ratio >= 1.0f) return realImage;
        int width = Math.max(1, Math.round(ratio * realImage.getWidth()));
        int height = Math.max(1, Math.round(ratio * realImage.getHeight()));
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
            hideKeyboard();
            loadCurrentProfile();
            boolean isBangla = AppPreferences.LANG_BANGLA.equals(appPreferences.getLanguageCode());
            showMessage(binding.getRoot(), isBangla
                    ? "প্রোফাইল সফলভাবে সেভ হয়েছে"
                    : "Profile saved successfully");
        } else {
            showMessage(binding.getRoot(), R.string.profile_save_failed);
        }
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused != null) {
            focused.clearFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            }
        }
    }
}