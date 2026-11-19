package com.edu360.api.platformops.delegation;
import java.time.LocalDate; import java.util.UUID; import jakarta.validation.constraints.NotNull;
public record PlatformDelegationRequest(@NotNull UUID delegateUserId,@NotNull LocalDate from,@NotNull LocalDate to) {}
