package com.scheduletl.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record CreateProcessDefinitionRequest(
        @NotBlank(message = "Process name is required") String name,
        @NotBlank(message = "Process type is required") String type,
        String description,
        String configuration,
        boolean enabled,
        Long parentProcessId,
        @PositiveOrZero(message = "Sequence order must be zero or greater") Integer sequenceOrder,
        boolean runIfParentFails,
        List<ProcessScheduleDayRequest> startSchedule
) {
    public CreateProcessDefinitionRequest(String name, String type, String description, String configuration,
                                          boolean enabled, Long parentProcessId, Integer sequenceOrder,
                                          boolean runIfParentFails) {
        this(name, type, description, configuration, enabled, parentProcessId, sequenceOrder,
                runIfParentFails, null);
    }
}
