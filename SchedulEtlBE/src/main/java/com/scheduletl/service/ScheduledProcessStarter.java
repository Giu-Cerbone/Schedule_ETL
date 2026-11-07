package com.scheduletl.service;

import com.scheduletl.domain.ProcessScheduleDay;
import com.scheduletl.domain.ProcessStartSchedule;
import com.scheduletl.domain.SchedulerStatus;
import com.scheduletl.repository.ProcessStartScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ScheduledProcessStarter {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledProcessStarter.class);
    private final ProcessStartScheduleRepository scheduleRepository;
    private final ProcessManagementService processManagementService;
    private LocalDateTime lastCheckedAt;

    @Scheduled(fixedDelayString = "${scheduletl.process.scheduler-poll-ms:10000}")
    public synchronized void startDueProcesses() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = lastCheckedAt == null ? now.minusSeconds(30) : lastCheckedAt;
        lastCheckedAt = now;
        processDueSlots(from, now);
    }

    void processDueSlots(LocalDateTime from, LocalDateTime now) {
        for (ProcessStartSchedule slot : scheduleRepository.findAll()) {
            if (!slot.getProcessDefinition().isEnabled()
                    || slot.getProcessDefinition().getScheduler().getStatus() != SchedulerStatus.ACTIVE
                    || (slot.getStartDate() != null && now.toLocalDate().isBefore(slot.getStartDate()))) {
                continue;
            }
            LocalDate date = slot.getStartDate() != null && from.toLocalDate().isBefore(slot.getStartDate())
                    ? slot.getStartDate() : from.toLocalDate();
            while (!date.isAfter(now.toLocalDate())) {
                LocalDateTime scheduledAt = date.atTime(slot.getStartTime());
                if (date.getDayOfWeek() == toDayOfWeek(slot.getDayOfWeek())
                        && scheduledAt.isAfter(from) && !scheduledAt.isAfter(now)
                        && scheduleRepository.claimScheduledStart(slot.getId(), scheduledAt) == 1) {
                    try {
                        processManagementService.startScheduledProcess(
                                slot.getProcessDefinition().getScheduler().getId(),
                                slot.getProcessDefinition().getId());
                    } catch (ResponseStatusException exception) {
                        logger.warn("Scheduled start for process {} was rejected: {}",
                                slot.getProcessDefinition().getId(), exception.getReason());
                    }
                }
                date = date.plusDays(1);
            }
        }
    }

    private DayOfWeek toDayOfWeek(ProcessScheduleDay day) {
        return DayOfWeek.valueOf(day.name());
    }
}
