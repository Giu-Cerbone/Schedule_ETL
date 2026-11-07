package com.scheduletl.dto;

import com.scheduletl.domain.SchedulerStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSchedulerStatusRequest(
        @NotNull(message = "Scheduler status is required") SchedulerStatus status
) {
}
