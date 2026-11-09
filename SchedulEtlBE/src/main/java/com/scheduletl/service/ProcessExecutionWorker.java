package com.scheduletl.service;

import com.scheduletl.domain.ProcessExecution;
import com.scheduletl.domain.ProcessState;
import com.scheduletl.repository.ProcessExecutionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class ProcessExecutionWorker {

    private final ProcessExecutionRepository processExecutionRepository;
    private final ShellScriptExecutor shellScriptExecutor;
    private final ProcessLogService processLogService;

    @Transactional
    public void markRunning(Long executionId) {
        ProcessExecution execution = findExecution(executionId);
        execution.setState(ProcessState.RUNNING);
        execution.setStartedAt(LocalDateTime.now());
        execution.setStatusMessage("Process started");
        execution.setLogs("Process execution started at: " + execution.getStartedAt());
        processExecutionRepository.save(execution);
        processLogService.log(execution, "STARTED", ProcessLogService.INFO, "Avvio",
                "Process started", "Process execution started at: " + execution.getStartedAt());
    }

    @Transactional(readOnly = true)
    public void logStep(Long executionId, String stepName, String message) {
        processExecutionRepository.findById(executionId).ifPresent(execution ->
                processLogService.log(execution, "STEP", ProcessLogService.INFO, stepName, message, null));
    }

    @Transactional(readOnly = true)
    public void logEvent(Long executionId, String eventType, String level, String message) {
        processExecutionRepository.findById(executionId).ifPresent(execution ->
                processLogService.log(execution, eventType, level, null, message, null));
    }

    public Outcome execute(String processType, String configuration) {
        return execute(processType, configuration, ignored -> {});
    }

    public Outcome execute(String processType, String configuration, Consumer<String> outputListener) {
        if (!"SHELL_CMD".equals(processType)) {
            return new Outcome(ProcessState.RUNNING, null, "Process started successfully",
                    "No execution adapter is configured for process type " + processType);
        }
        try {
            ShellScriptExecutor.ExecutionResult result = shellScriptExecutor.execute(configuration, outputListener);
            return new Outcome(result.exitCode() == 0 ? ProcessState.COMPLETED : ProcessState.FAILED,
                    result.exitCode(),
                    result.timedOut() ? "Script execution timed out"
                            : result.exitCode() == 0 ? "Script completed successfully" : "Script failed",
                    result.output());
        } catch (IOException exception) {
            return new Outcome(ProcessState.FAILED, -1, "Unable to execute the approved script",
                    "I/O error while starting or reading the approved script.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Outcome(ProcessState.FAILED, -1, "Script execution was interrupted",
                    "Script execution was interrupted before completion.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return new Outcome(ProcessState.FAILED, -1, "Script execution was rejected", exception.getMessage());
        }
    }

    @Transactional
    public void updateLogs(Long executionId, String logs) {
        ProcessExecution execution = findExecution(executionId);
        execution.setLogs(logs);
        processExecutionRepository.save(execution);
    }

    @Transactional
    public void complete(Long executionId, Outcome outcome) {
        ProcessExecution execution = findExecution(executionId);
        execution.setState(outcome.state());
        execution.setExitCode(outcome.exitCode());
        execution.setStatusMessage(outcome.statusMessage());
        execution.setLogs(outcome.logs());
        execution.setFinishedAt(LocalDateTime.now());
        processExecutionRepository.save(execution);
        String level = outcome.state() == ProcessState.FAILED ? ProcessLogService.ERROR
                : outcome.state() == ProcessState.STOPPED || outcome.state() == ProcessState.CANCELLED
                ? ProcessLogService.WARN : ProcessLogService.INFO;
        processLogService.log(execution, outcome.state().name(), level, "Esito",
                outcome.statusMessage() + (outcome.exitCode() == null ? "" : " (exit code " + outcome.exitCode() + ")"),
                outcome.logs());
    }

    @Transactional
    public void cancelQueued(Long executionId, String reason) {
        ProcessExecution execution = findExecution(executionId);
        execution.setState(ProcessState.CANCELLED);
        execution.setStatusMessage(reason);
        execution.setLogs(reason);
        execution.setExitCode(null);
        execution.setFinishedAt(LocalDateTime.now());
        processExecutionRepository.save(execution);
        processLogService.log(execution, "CANCELLED", ProcessLogService.WARN, "Annullamento", reason, null);
    }

    private ProcessExecution findExecution(Long executionId) {
        return processExecutionRepository.findById(executionId)
                .orElseThrow(() -> new EntityNotFoundException("Execution not found: " + executionId));
    }

    public record Outcome(ProcessState state, Integer exitCode, String statusMessage, String logs) {
    }
}
