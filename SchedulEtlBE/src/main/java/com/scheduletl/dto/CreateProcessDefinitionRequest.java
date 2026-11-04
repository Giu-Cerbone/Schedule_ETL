package com.scheduletl.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateProcessDefinitionRequest(
        @NotBlank(message = "Process name is required") String name,
        @NotBlank(message = "Process type is required") String type,
        String description,
        String configuration,
        boolean enabled,
        Long parentProcessId,
        @PositiveOrZero(message = "Sequence order must be zero or greater") Integer sequenceOrder
) {
}
