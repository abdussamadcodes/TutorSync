package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Account {
    private final String id;
    private final String fullName;
    private final String username;
    private final String passwordHash;
    private final String salt;
    private final String phone;
    private final String email;
    private final String address;
    private final String dob;
    private final String profilePicPath;

    public Account(@NonNull String id, @NonNull String fullName, @NonNull String username,
                   @NonNull String passwordHash, @NonNull String salt) {
        this(id, fullName, username, passwordHash, salt, null, null, null, null, null);
    }

    public Account(@NonNull String id, @NonNull String fullName, @NonNull String username,
                   @NonNull String passwordHash, @NonNull String salt,
                   @Nullable String phone, @Nullable String email, @Nullable String address,
                   @Nullable String dob, @Nullable String profilePicPath) {
        this.id = id;
        this.fullName = fullName;
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.dob = dob;
        this.profilePicPath = profilePicPath;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getFullName() { return fullName; }
    @NonNull public String getUsername() { return username; }
    @NonNull public String getPasswordHash() { return passwordHash; }
    @NonNull public String getSalt() { return salt; }
    @Nullable public String getPhone() { return phone; }
    @Nullable public String getEmail() { return email; }
    @Nullable public String getAddress() { return address; }
    @Nullable public String getDob() { return dob; }
    @Nullable public String getProfilePicPath() { return profilePicPath; }
}