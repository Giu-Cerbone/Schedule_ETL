package com.scheduletl.service;

import com.scheduletl.domain.ProcessDefinition;
import com.scheduletl.domain.ProcessEventLog;
import com.scheduletl.domain.ProcessExecution;
import com.scheduletl.domain.ProcessStartSchedule;
import com.scheduletl.domain.Scheduler;
import com.scheduletl.repository.ProcessEventLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Writes the process journal in its own transaction so that entries survive rollbacks of the caller
 * and a logging failure never breaks the process it describes.
 */
@Service
public class ProcessLogService {

    public static final String INFO = "INFO";
    public static final String WARN = "WARN";
    public static final String ERROR = "ERROR";

    private static final Logger logger = LoggerFactory.getLogger(ProcessLogService.class);
    private static final int MAX_MESSAGE = 4000;
    private static final int MAX_DETAILS = 20000;

    private final ProcessEventLogRepository repository;
    private final TransactionTemplate transactionTemplate;

    public ProcessLogService(ProcessEventLogRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void log(ProcessExecution execution, String eventType, String level, String stepName,
                    String message, String details) {
        try {
            ProcessEventLog entry = new ProcessEventLog();
            entry.setLoggedAt(LocalDateTime.now());
            entry.setLevel(level);
            entry.setEventType(eventType);
            entry.setStepName(stepName);
            entry.setMessage(truncate(message == null ? eventType : message, MAX_MESSAGE));
            entry.setDetails(truncate(details, MAX_DETAILS));
            entry.setExecutionId(execution.getId());
            entry.setTriggerType(execution.getTriggerType());
            entry.setState(execution.getState() == null ? null : execution.getState().name());
            entry.setExitCode(execution.getExitCode());
            ProcessDefinition definition = execution.getProcessDefinition();
            if (definition != null) {
                entry.setProcessDefinitionId(definition.getId());
                entry.setProcessName(definition.getName());
                entry.setProcessType(definition.getType() == null ? null : definition.getType().getCode());
                entry.setParentProcessId(definition.getParentProcess() == null
                        ? null : definition.getParentProcess().getId());
                entry.setSequenceOrder(definition.getSequenceOrder());
                entry.setScheduleInfo(describeSchedule(definition));
            }
            Scheduler scheduler = execution.getScheduler();
            if (scheduler != null) {
                entry.setSchedulerId(scheduler.getId());
                entry.setSchedulerName(scheduler.getName());
            }
            transactionTemplate.executeWithoutResult(status -> {
                entry.setStepNumber((int) repository.countByExecutionId(entry.getExecutionId()) + 1);
                repository.save(entry);
            });
        } catch (RuntimeException exception) {
            logger.warn("Unable to write the process journal entry {}: {}", eventType, exception.getMessage());
        }
    }

    static String describeSchedule(ProcessDefinition definition) {
        if (definition.getStartSchedules() == null || definition.getStartSchedules().isEmpty()) {
            return null;
        }
        return definition.getStartSchedules().stream()
                .sorted(Comparator.comparing(ProcessStartSchedule::getDayOfWeek)
                        .thenComparing(ProcessStartSchedule::getStartTime))
                .collect(Collectors.groupingBy(
                        slot -> slot.getDayOfWeek().name()
                                + (slot.getStartDate() == null ? ""
                                : " il " + slot.getStartDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))),
                        java.util.LinkedHashMap::new,
                        Collectors.mapping(slot -> slot.getStartTime().toString(), Collectors.joining(", "))))
                .entrySet().stream()
                .map(entry -> entry.getKey() + " alle " + entry.getValue())
                .collect(Collectors.joining("; "));
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 3) + "...";
    }
}
