package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract;

public class Student {
    private final String id;
    private final String accountId;
    private final String studentIdDisplay;
    private final String name;
    private final String grade;
    private final String school;
    private final String guardianName;
    private final String guardianPhone;
    private final String address;
    private final boolean isActive;
    private final long updatedAt;

    public Student(@NonNull String id, @NonNull String accountId, @Nullable String studentIdDisplay, @NonNull String name,
                   @Nullable String grade, @Nullable String school, @Nullable String guardianName,
                   @Nullable String guardianPhone, @Nullable String address, boolean isActive) {
        this(id, accountId, studentIdDisplay, name, grade, school, guardianName, guardianPhone, address, isActive, System.currentTimeMillis());
    }

    public Student(@NonNull String id, @NonNull String accountId, @Nullable String studentIdDisplay, @NonNull String name,
                   @Nullable String grade, @Nullable String school, @Nullable String guardianName,
                   @Nullable String guardianPhone, @Nullable String address, boolean isActive, long updatedAt) {
        this.id = TutorSyncContract.ensureValidUuid(id);
        this.accountId = accountId;
        this.studentIdDisplay = studentIdDisplay;
        this.name = name;
        this.grade = grade;
        this.school = school;
        this.guardianName = guardianName;
        this.guardianPhone = guardianPhone;
        this.address = address;
        this.isActive = isActive;
        this.updatedAt = updatedAt > 0 ? updatedAt : System.currentTimeMillis();
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getAccountId() { return accountId; }
    @Nullable public String getStudentIdDisplay() { return studentIdDisplay; }
    @NonNull public String getName() { return name; }
    @Nullable public String getGrade() { return grade; }
    @Nullable public String getSchool() { return school; }
    @Nullable public String getGuardianName() { return guardianName; }
    @Nullable public String getGuardianPhone() { return guardianPhone; }
    @Nullable public String getAddress() { return address; }
    public boolean isActive() { return isActive; }
    public long getUpdatedAt() { return updatedAt; }
    public long getLastModified() { return updatedAt; }

    @NonNull
    public String getFormattedDisplayName() {
        if (studentIdDisplay != null && !studentIdDisplay.trim().isEmpty()) {
            return name + " · " + studentIdDisplay;
        }
        return name;
    }
}