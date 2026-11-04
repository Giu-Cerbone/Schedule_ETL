package com.scheduletl.dto;

import com.scheduletl.domain.SchedulerStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSchedulerRequest(
        @NotBlank(message = "Scheduler name is required") String name,
        String description,
        @NotBlank(message = "Cron expression is required") String cronExpression,
        @NotNull(message = "Owner profile id is required") Long ownerProfileId,
        Long groupId,
        SchedulerStatus status
) {
}
