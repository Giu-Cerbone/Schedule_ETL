package com.scheduletl.dto;

import com.scheduletl.domain.ProcessScheduleDay;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ProcessScheduleDayRequest(
        @NotNull ProcessScheduleDay dayOfWeek,
        List<@NotNull LocalTime> times,
        LocalDate startDate
) {
}
