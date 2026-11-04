package com.scheduletl.dto;

import java.time.LocalDateTime;

public record ProcessDefinitionResponse(
        Long id,
        String name,
        String type,
        String description,
        String configuration,
        boolean enabled,
        Long schedulerId,
        Long parentProcessId,
        int sequenceOrder,
        LocalDateTime createdAt
) {
}
