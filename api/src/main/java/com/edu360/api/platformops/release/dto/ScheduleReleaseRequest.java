package com.edu360.api.platformops.release.dto;
import java.time.Instant;
import jakarta.validation.constraints.NotNull;
public record ScheduleReleaseRequest(@NotNull Instant at) {}
