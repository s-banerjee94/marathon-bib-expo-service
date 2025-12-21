package com.timekeeper.bibexpo.model.entity;

public enum UserRole {
    ROLE_ROOT,             // System head (highest level - super admin)
    ROLE_ADMIN,            // System administrator (manages whole app)
    ROLE_ORGANIZER_ADMIN,  // Organization administrator (manages organization)
    ROLE_ORGANIZER_USER,   // Organization user (team member)
    ROLE_DISTRIBUTOR       // Distributor under organization
}
