package com.abdussamadcodes.tutorsync.model;

import androidx.annotation.NonNull;

public class Account {
    private final String id;
    private final String fullName;
    private final String username;
    private final String passwordHash;
    private final String salt;

    public Account(@NonNull String id, @NonNull String fullName, @NonNull String username, @NonNull String passwordHash, @NonNull String salt) {
        this.id = id;
        this.fullName = fullName;
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getFullName() { return fullName; }
    @NonNull public String getUsername() { return username; }
    @NonNull public String getPasswordHash() { return passwordHash; }
    @NonNull public String getSalt() { return salt; }
}