package com.scheduletl.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.LocalDate;
import java.util.List;

import com.scheduletl.domain.ProcessScheduleDay;

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
        boolean runIfParentFails,
        List<ProcessScheduleDayResponse> startSchedule,
        LocalDateTime createdAt
) {
    public record ProcessScheduleDayResponse(ProcessScheduleDay dayOfWeek, LocalDate startDate, List<LocalTime> times) {
    }
}
