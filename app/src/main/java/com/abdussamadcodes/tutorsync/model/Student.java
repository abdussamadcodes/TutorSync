package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Student {
    private final String id;
    private final String accountId;
    private final String name;
    private final String grade;
    private final String school;
    private final String guardianName;
    private final String guardianPhone;
    private final String address;
    private final boolean isActive;

    public Student(@NonNull String id, @NonNull String accountId, @NonNull String name,
                   @Nullable String grade, @Nullable String school, @Nullable String guardianName,
                   @Nullable String guardianPhone, @Nullable String address, boolean isActive) {
        this.id = id;
        this.accountId = accountId;
        this.name = name;
        this.grade = grade;
        this.school = school;
        this.guardianName = guardianName;
        this.guardianPhone = guardianPhone;
        this.address = address;
        this.isActive = isActive;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getAccountId() { return accountId; }
    @NonNull public String getName() { return name; }
    @Nullable public String getGrade() { return grade; }
    @Nullable public String getSchool() { return school; }
    @Nullable public String getGuardianName() { return guardianName; }
    @Nullable public String getGuardianPhone() { return guardianPhone; }
    @Nullable public String getAddress() { return address; }
    public boolean isActive() { return isActive; }
}