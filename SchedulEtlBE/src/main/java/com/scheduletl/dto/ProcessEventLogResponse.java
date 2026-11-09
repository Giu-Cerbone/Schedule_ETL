package com.scheduletl.dto;

import java.time.LocalDateTime;

public record ProcessEventLogResponse(
        Long id,
        LocalDateTime loggedAt,
        String level,
        String eventType,
        Integer stepNumber,
        String stepName,
        String message,
        String details,
        Long executionId,
        Long processDefinitionId,
        String processName,
        String processType,
        Long parentProcessId,
        Integer sequenceOrder,
        Long schedulerId,
        String schedulerName,
        String triggerType,
        String scheduleInfo,
        String state,
        Integer exitCode
) {
}
