package com.timekeeper.bibexpo.model.entity;

public enum UserRole {
    ROOT,             // System head (highest level - super admin)
    ADMIN,            // System administrator (manages whole app)
    ORGANIZER_ADMIN,  // Organization administrator (manages organization)
    ORGANIZER_USER,   // Organization user (team member)
    DISTRIBUTOR       // Distributor under organization
}
