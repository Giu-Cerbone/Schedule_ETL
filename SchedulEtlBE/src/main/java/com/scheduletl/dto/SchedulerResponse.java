package com.scheduletl.dto;

import com.scheduletl.domain.SchedulerStatus;

import java.time.LocalDateTime;

public record SchedulerResponse(
        Long id,
        String name,
        String description,
        String cronExpression,
        SchedulerStatus status,
        Long ownerProfileId,
        Long groupId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
