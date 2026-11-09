package com.scheduletl.service;

import com.scheduletl.domain.ProcessDefinition;
import com.scheduletl.domain.ProcessScheduleDay;
import com.scheduletl.domain.ProcessStartSchedule;
import com.scheduletl.domain.ProcessType;
import com.scheduletl.domain.Scheduler;
import com.scheduletl.repository.ProcessStartScheduleRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledProcessStarterTest {

    private final ProcessStartScheduleRepository repository = mock(ProcessStartScheduleRepository.class);
    private final ProcessManagementService processManagementService = mock(ProcessManagementService.class);
    private final ScheduledProcessStarter starter = new ScheduledProcessStarter(repository, processManagementService);

    @Test
    void startsProcessAtItsConfiguredWeeklySlot() {
        ProcessDefinition definition = new ProcessDefinition();
        definition.setId(12L);
        definition.setType(new ProcessType());
        Scheduler scheduler = new Scheduler();
        scheduler.setId(4L);
        definition.setScheduler(scheduler);

        ProcessStartSchedule slot = new ProcessStartSchedule();
        slot.setId(2L);
        slot.setDayOfWeek(ProcessScheduleDay.MONDAY);
        slot.setStartTime(LocalTime.of(8, 30));
        slot.setProcessDefinition(definition);

        LocalDateTime from = LocalDateTime.of(2026, 12, 7, 8, 29);
        LocalDateTime now = LocalDateTime.of(2026, 12, 7, 8, 31);
        when(repository.findAll()).thenReturn(List.of(slot));
        when(repository.claimScheduledStart(2L, LocalDateTime.of(2026, 12, 7, 8, 30))).thenReturn(1);

        starter.processDueSlots(from, now);

        verify(processManagementService).startScheduledProcess(4L, 12L);
    }

    @Test
    void ignoresSlotsOutsidePollingWindow() {
        ProcessDefinition definition = new ProcessDefinition();
        definition.setId(12L);
        definition.setType(new ProcessType());
        definition.setScheduler(new Scheduler());
        ProcessStartSchedule slot = new ProcessStartSchedule();
        slot.setId(2L);
        slot.setDayOfWeek(ProcessScheduleDay.MONDAY);
        slot.setStartTime(LocalTime.of(8, 30));
        slot.setProcessDefinition(definition);
        when(repository.findAll()).thenReturn(List.of(slot));

        starter.processDueSlots(LocalDateTime.of(2026, 12, 7, 8, 31),
                LocalDateTime.of(2026, 12, 7, 8, 32));

        verify(repository, org.mockito.Mockito.never()).claimScheduledStart(eq(2L),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotStartBeforeOptionalDaySpecificCalendarDate() {
        ProcessDefinition definition = new ProcessDefinition();
        definition.setId(12L);
        definition.setType(new ProcessType());
        definition.setScheduler(new Scheduler());

        ProcessStartSchedule slot = new ProcessStartSchedule();
        slot.setId(2L);
        slot.setDayOfWeek(ProcessScheduleDay.MONDAY);
        slot.setStartTime(LocalTime.of(8, 30));
        slot.setStartDate(LocalDate.of(2026, 12, 14));
        slot.setProcessDefinition(definition);
        when(repository.findAll()).thenReturn(List.of(slot));

        starter.processDueSlots(LocalDateTime.of(2026, 12, 7, 8, 29),
                LocalDateTime.of(2026, 12, 7, 8, 31));

        verify(repository, org.mockito.Mockito.never()).claimScheduledStart(eq(2L),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void startsDatedSlotOnlyOnItsExactDate() {
        ProcessStartSchedule slot = datedSlot(LocalDate.of(2026, 12, 7));
        when(repository.findAll()).thenReturn(List.of(slot));
        when(repository.claimScheduledStart(2L, LocalDateTime.of(2026, 12, 7, 8, 30))).thenReturn(1);

        starter.processDueSlots(LocalDateTime.of(2026, 12, 7, 8, 29), LocalDateTime.of(2026, 12, 7, 8, 31));
        verify(processManagementService).startScheduledProcess(4L, 12L);

        starter.processDueSlots(LocalDateTime.of(2026, 12, 14, 8, 29), LocalDateTime.of(2026, 12, 14, 8, 31));
        verify(repository, org.mockito.Mockito.never()).claimScheduledStart(eq(2L),
                eq(LocalDateTime.of(2026, 12, 14, 8, 30)));
    }

    private ProcessStartSchedule datedSlot(LocalDate date) {
        ProcessDefinition definition = new ProcessDefinition();
        definition.setId(12L);
        definition.setType(new ProcessType());
        Scheduler scheduler = new Scheduler();
        scheduler.setId(4L);
        definition.setScheduler(scheduler);
        ProcessStartSchedule slot = new ProcessStartSchedule();
        slot.setId(2L);
        slot.setDayOfWeek(ProcessScheduleDay.MONDAY);
        slot.setStartTime(LocalTime.of(8, 30));
        slot.setStartDate(date);
        slot.setProcessDefinition(definition);
        return slot;
    }
}
