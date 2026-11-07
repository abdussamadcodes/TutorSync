package com.abdussamadcodes.tutorsync.ui.student;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.abdussamadcodes.tutorsync.databinding.ActivityStudentFormBinding;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;

import java.util.UUID;

public class StudentFormActivity extends BaseActivity {

    private ActivityStudentFormBinding binding;
    private StudentRepository repository;
    private SessionManager sessionManager;
    private String existingStudentId = null;

    private final ActivityResultLauncher<Intent> pickContactLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    processSelectedContact(result.getData().getData());
                }
            });

    private final ActivityResultLauncher<String> requestContactsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    launchContactPicker();
                } else {
                    showMessage(binding.getRoot(), "Contacts permission is required to import students.");
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudentFormBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());

        repository = new StudentRepository();
        sessionManager = new SessionManager(this);

        // Required to display onCreateOptionsMenu in the MaterialToolbar
        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        existingStudentId = getIntent().getStringExtra("student_id");
        if (existingStudentId != null) {
            binding.toolbar.setTitle("Edit Student");
            binding.btnSave.setText("Update Student");
            loadExistingStudentData();
        } else {
            // Hide the ID field completely for new students (it's auto-generated)
            binding.etStudentId.setVisibility(View.GONE);
            ((View) binding.etStudentId.getParent().getParent()).setVisibility(View.GONE);
            binding.toolbar.setTitle("Add Student");
        }

        binding.btnSave.setOnClickListener(v -> saveStudent());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (existingStudentId == null) {
            MenuItem importItem = menu.add(Menu.NONE, 1, Menu.NONE, "Import");
            importItem.setIcon(android.R.drawable.ic_menu_myplaces);
            importItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1) {
            checkContactsPermissionAndImport();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void checkContactsPermissionAndImport() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            launchContactPicker();
        } else {
            requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
        }
    }

    private void launchContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        pickContactLauncher.launch(intent);
    }

    private void processSelectedContact(Uri contactUri) {
        if (contactUri == null) return;

        try (Cursor cursor = getContentResolver().query(contactUri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {

                int nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
                int idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID);
                int hasPhoneIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER);

                if (nameIndex == -1 || idIndex == -1 || hasPhoneIndex == -1) return;

                String contactName = cursor.getString(nameIndex);
                String contactId = cursor.getString(idIndex);
                String contactNumber = "";

                int hasPhoneNumber = cursor.getInt(hasPhoneIndex);
                if (hasPhoneNumber > 0) {
                    try (Cursor phoneCursor = getContentResolver().query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            new String[]{contactId},
                            null)) {
                        if (phoneCursor != null && phoneCursor.moveToFirst()) {
                            int numberIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                            if (numberIndex != -1) {
                                contactNumber = phoneCursor.getString(numberIndex);
                            }
                        }
                    }
                }

                if (binding.etName != null) binding.etName.setText(contactName);
                if (binding.etGuardianPhone != null) binding.etGuardianPhone.setText(contactNumber);

                showMessage(binding.getRoot(), "Imported " + contactName + " from contacts");
            }
        } catch (Exception e) {
            showMessage(binding.getRoot(), "Failed to read contact data.");
        }
    }

    private void loadExistingStudentData() {
        String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty() || existingStudentId == null) return;
        Student student = repository.getStudentById(accountId, existingStudentId);
        if (student != null) {
            binding.etStudentId.setText(student.getStudentIdDisplay());
            binding.etStudentId.setEnabled(false);
            binding.etName.setText(student.getName());
            binding.etGrade.setText(student.getGrade());
            binding.etSchool.setText(student.getSchool());
            binding.etGuardianName.setText(student.getGuardianName());
            binding.etGuardianPhone.setText(student.getGuardianPhone());
            binding.etAddress.setText(student.getAddress());
        }
    }

    private void saveStudent() {
        String name = binding.etName.getText() != null ? binding.etName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            binding.etName.setError("Name is required");
            return;
        }

        String accountId = sessionManager.getActiveAccountId();
        // displayId is ignored on insert (auto-generated) and ignored on update (permanent)
        String displayId = binding.etStudentId.getText() != null ? binding.etStudentId.getText().toString().trim() : "";
        String grade = binding.etGrade.getText() != null ? binding.etGrade.getText().toString().trim() : "";
        String school = binding.etSchool.getText() != null ? binding.etSchool.getText().toString().trim() : "";
        String guardianName = binding.etGuardianName.getText() != null ? binding.etGuardianName.getText().toString().trim() : "";
        String guardianPhone = binding.etGuardianPhone.getText() != null ? binding.etGuardianPhone.getText().toString().trim() : "";
        String address = binding.etAddress.getText() != null ? binding.etAddress.getText().toString().trim() : "";

        if (existingStudentId != null) {
            Student student = new Student(existingStudentId, accountId, displayId, name, grade, school, guardianName, guardianPhone, address, true);
            if (repository.updateStudent(student)) {
                showMessage(binding.getRoot(), "Student updated successfully");
                finish();
            } else {
                showMessage(binding.getRoot(), "Failed to update student");
            }
        } else {
            Student student = new Student(UUID.randomUUID().toString(), accountId, null, name, grade, school, guardianName, guardianPhone, address, true);
            int result = repository.insertStudent(student);
            if (result == 1) {
                showMessage(binding.getRoot(), "Student added successfully");
                finish();
            } else {
                showMessage(binding.getRoot(), "Failed to add student.");
            }
        }
    }
}