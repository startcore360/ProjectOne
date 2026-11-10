package com.edu360.api.platformops.role;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.platformops.PlatformAccessLookup;

import lombok.RequiredArgsConstructor;

/** Reads rights on every request, so a change takes effect at once rather than at the next login. */
@Component
@RequiredArgsConstructor
public class StoredPlatformAccessLookup implements PlatformAccessLookup {

    private final PlatformUserRepository platformUserRepository;
    private final PlatformCustomRoleRepository roleRepository;
    private final PlatformCustomRoleActionRepository actionRepository;
    private final PlatformStepInRepository stepInRepository;

    @Override
    public Optional<Set<String>> customRoleActions(UUID platformUserId) {
        return platformUserRepository.findById(platformUserId)
                .filter(user -> user.getPlatformRoleId() != null)
                .map(user -> roleRepository.findById(user.getPlatformRoleId())
                        .filter(role -> role.getStatus() == PlatformCustomRoleStatus.ACTIVE)
                        .map(role -> actionRepository.findByPlatformRoleId(role.getPlatformRoleId()).stream()
                                .map(PlatformCustomRoleAction::getActionCode)
                                .collect(Collectors.toSet()))
                        .orElse(Set.of()));
    }

    @Override
    public boolean isHandedOver(String actionCode) {
        return actionRepository.isHandedOver(actionCode);
    }

    @Override
    public List<String> handedOverActions() {
        return actionRepository.findHandedOverActions();
    }

    @Override
    public boolean hasActiveStepIn(UUID platformUserId, String actionCode) {
        return stepInRepository.existsByActorUserIdAndActionCodeAndExpiresAtAfter(platformUserId, actionCode, Instant.now());
    }

    @Override
    public List<String> activeStepIns(UUID platformUserId) {
        return stepInRepository.findByActorUserIdAndExpiresAtAfter(platformUserId, Instant.now()).stream()
                .map(PlatformStepIn::getActionCode)
                .distinct()
                .toList();
    }
}
