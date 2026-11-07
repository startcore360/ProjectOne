package com.edu360.api.platformops;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The stored facts PlatformPermissionService needs: who holds a custom role, what has been handed
 * over, and who has stepped in. Kept behind an interface so the permission rules can be tested
 * without a database.
 */
public interface PlatformAccessLookup {

    /** The custom role's actions, or empty if this person holds only a standard role. An inactive
     *  or archived custom role returns an empty set, which denies everything. */
    Optional<Set<String>> customRoleActions(UUID platformUserId);

    boolean isHandedOver(String actionCode);

    List<String> handedOverActions();

    boolean hasActiveStepIn(UUID platformUserId, String actionCode);

    List<String> activeStepIns(UUID platformUserId);

    /** No custom roles and nothing handed over: standard rights only. */
    static PlatformAccessLookup standardRolesOnly() {
        return new PlatformAccessLookup() {
            @Override
            public Optional<Set<String>> customRoleActions(UUID platformUserId) {
                return Optional.empty();
            }

            @Override
            public boolean isHandedOver(String actionCode) {
                return false;
            }

            @Override
            public List<String> handedOverActions() {
                return List.of();
            }

            @Override
            public boolean hasActiveStepIn(UUID platformUserId, String actionCode) {
                return false;
            }

            @Override
            public List<String> activeStepIns(UUID platformUserId) {
                return List.of();
            }
        };
    }
}
