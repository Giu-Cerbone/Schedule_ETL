package com.scheduletl.dto;

import com.scheduletl.domain.ProcessState;

import java.time.LocalDateTime;

public record ProcessExecutionResponse(
        Long id,
        Long schedulerId,
        Long processDefinitionId,
        ProcessState state,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Integer exitCode,
        String statusMessage,
        String logs
) {
}
