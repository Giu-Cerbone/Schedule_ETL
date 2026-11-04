package com.scheduletl.service;

import com.scheduletl.domain.*;
import com.scheduletl.dto.CreateProcessDefinitionRequest;
import com.scheduletl.dto.ProcessDefinitionResponse;
import com.scheduletl.dto.ProcessExecutionResponse;
import com.scheduletl.repository.ProcessDefinitionRepository;
import com.scheduletl.repository.ProcessExecutionRepository;
import com.scheduletl.repository.ProcessTypeRepository;
import com.scheduletl.repository.SchedulerRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProcessManagementService {

    private final SchedulerRepository schedulerRepository;
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final ProcessExecutionRepository processExecutionRepository;
    private final ProcessTypeRepository processTypeRepository;
    private final ShellScriptExecutor shellCommandExecutor;

    @Transactional(readOnly = true)
    public List<ProcessDefinitionResponse> getProcessDefinitions(Long schedulerId) {
        return processDefinitionRepository.findBySchedulerIdOrderByParentProcessIdAscSequenceOrderAscIdAsc(schedulerId).stream()
                .map(this::toDefinitionResponse)
                .toList();
    }

    @Transactional
    public ProcessDefinitionResponse createProcessDefinition(Long schedulerId, CreateProcessDefinitionRequest request) {
        Scheduler scheduler = schedulerRepository.findById(schedulerId)
                .orElseThrow(() -> new EntityNotFoundException("Scheduler not found: " + schedulerId));

        ProcessDefinition definition = new ProcessDefinition();
        definition.setScheduler(scheduler);
        definition.setName(request.name());
        definition.setType(findProcessType(request.type()));
        definition.setDescription(request.description());
        definition.setConfiguration(request.configuration());
        definition.setEnabled(request.enabled());
        definition.setSequenceOrder(request.sequenceOrder() != null ? request.sequenceOrder() : 0);
        definition.setParentProcess(findParentProcess(schedulerId, null, request.parentProcessId()));

        ProcessDefinition saved = processDefinitionRepository.save(definition);
        return toDefinitionResponse(saved);
    }

    @Transactional
    public ProcessDefinitionResponse updateProcessDefinition(Long schedulerId, Long processDefinitionId,
                                                             CreateProcessDefinitionRequest request) {
        ProcessDefinition definition = findProcessDefinition(schedulerId, processDefinitionId);
        definition.setName(request.name());
        definition.setType(findProcessType(request.type()));
        definition.setDescription(request.description());
        definition.setConfiguration(request.configuration());
        definition.setEnabled(request.enabled());
        definition.setSequenceOrder(request.sequenceOrder() != null ? request.sequenceOrder() : 0);
        definition.setParentProcess(findParentProcess(schedulerId, processDefinitionId, request.parentProcessId()));
        return toDefinitionResponse(processDefinitionRepository.save(definition));
    }

    @Transactional
    public ProcessExecutionResponse startProcess(Long schedulerId, Long processDefinitionId) {
        ProcessDefinition definition = findProcessDefinition(schedulerId, processDefinitionId);
        return executeTreeCommand(definition, ProcessCommand.START, ProcessState.RUNNING, "Process started successfully");
    }

    @Transactional
    public ProcessExecutionResponse pauseProcess(Long schedulerId, Long processDefinitionId) {
        ProcessDefinition definition = findProcessDefinition(schedulerId, processDefinitionId);
        return executeTreeCommand(definition, ProcessCommand.PAUSE, ProcessState.PAUSED, "Process paused");
    }

    @Transactional
    public ProcessExecutionResponse resumeProcess(Long schedulerId, Long processDefinitionId) {
        ProcessDefinition definition = findProcessDefinition(schedulerId, processDefinitionId);
        return executeTreeCommand(definition, ProcessCommand.RESUME, ProcessState.RUNNING, "Process resumed");
    }

    @Transactional
    public ProcessExecutionResponse stopProcess(Long schedulerId, Long processDefinitionId) {
        ProcessDefinition definition = findProcessDefinition(schedulerId, processDefinitionId);
        return executeTreeCommand(definition, ProcessCommand.STOP, ProcessState.STOPPED, "Process stopped");
    }

    @Transactional(readOnly = true)
    public List<ProcessExecutionResponse> getExecutionHistory(Long schedulerId, Long processDefinitionId) {
        findProcessDefinition(schedulerId, processDefinitionId);
        return processExecutionRepository.findByProcessDefinitionId(processDefinitionId).stream()
                .map(this::toExecutionResponse)
                .toList();
    }

    private ProcessDefinition findProcessDefinition(Long schedulerId, Long processDefinitionId) {
        ProcessDefinition definition = processDefinitionRepository.findById(processDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException("Process not found: " + processDefinitionId));

        if (!definition.getScheduler().getId().equals(schedulerId)) {
            throw new EntityNotFoundException("Process does not belong to scheduler: " + schedulerId);
        }

        return definition;
    }

    private ProcessType findProcessType(String typeCode) {
        return processTypeRepository.findById(typeCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unsupported process type: " + typeCode));
    }

    private ProcessDefinition findParentProcess(Long schedulerId, Long processDefinitionId, Long parentProcessId) {
        if (parentProcessId == null) {
            return null;
        }

        ProcessDefinition parent = findProcessDefinition(schedulerId, parentProcessId);
        if (parentProcessId.equals(processDefinitionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A process cannot be its own parent");
        }

        ProcessDefinition ancestor = parent;
        while (ancestor != null) {
            if (ancestor.getId().equals(processDefinitionId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A process cannot be moved under one of its descendants");
            }
            ancestor = ancestor.getParentProcess();
        }
        return parent;
    }

    private ProcessExecutionResponse executeTreeCommand(ProcessDefinition definition, ProcessCommand command,
                                                        ProcessState state, String statusMessage) {
        ProcessExecutionResponse rootExecution = executeCommand(definition, command, state, statusMessage);
        List<ProcessDefinition> children = processDefinitionRepository
                .findByParentProcessIdOrderBySequenceOrderAscIdAsc(definition.getId());
        for (ProcessDefinition child : children) {
            if (command != ProcessCommand.START || child.isEnabled()) {
                executeTreeCommand(child, command, state, statusMessage);
            }
        }
        return rootExecution;
    }

    private ProcessExecutionResponse executeCommand(ProcessDefinition definition, ProcessCommand command, ProcessState state, String statusMessage) {
        ProcessExecution execution = new ProcessExecution();
        execution.setScheduler(definition.getScheduler());
        execution.setProcessDefinition(definition);
        execution.setState(state);
        execution.setStatusMessage(statusMessage);
        execution.setLogs("Command: " + command + " | status: " + statusMessage + " | at: " + LocalDateTime.now());
        execution.setStartedAt(LocalDateTime.now());
        execution.setExitCode(0);

        if (command == ProcessCommand.START && "SHELL_CMD".equals(definition.getType().getCode())) {
            try {
                ShellScriptExecutor.ExecutionResult result = shellCommandExecutor.execute(definition.getConfiguration());
                execution.setLogs(result.output());
                execution.setExitCode(result.exitCode());
                execution.setState(result.exitCode() == 0 ? ProcessState.COMPLETED : ProcessState.FAILED);
                execution.setStatusMessage(result.timedOut()
                        ? "Script execution timed out"
                        : result.exitCode() == 0 ? "Script completed successfully" : "Script failed");
            } catch (IOException exception) {
                execution.setState(ProcessState.FAILED);
                execution.setExitCode(-1);
                execution.setStatusMessage("Unable to execute the approved script");
                execution.setLogs("I/O error while starting or reading the approved script.");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                execution.setState(ProcessState.FAILED);
                execution.setExitCode(-1);
                execution.setStatusMessage("Script execution was interrupted");
                execution.setLogs("Script execution was interrupted before completion.");
            } catch (IllegalArgumentException | IllegalStateException exception) {
                execution.setState(ProcessState.FAILED);
                execution.setExitCode(-1);
                execution.setStatusMessage("Script execution was rejected");
                execution.setLogs(exception.getMessage());
            }
        }

        execution.setFinishedAt(LocalDateTime.now());
        ProcessExecution saved = processExecutionRepository.save(execution);
        return toExecutionResponse(saved);
    }

    private ProcessDefinitionResponse toDefinitionResponse(ProcessDefinition processDefinition) {
        return new ProcessDefinitionResponse(
                processDefinition.getId(),
                processDefinition.getName(),
                processDefinition.getType().getCode(),
                processDefinition.getDescription(),
                processDefinition.getConfiguration(),
                processDefinition.isEnabled(),
                processDefinition.getScheduler() != null ? processDefinition.getScheduler().getId() : null,
                processDefinition.getParentProcess() != null ? processDefinition.getParentProcess().getId() : null,
                processDefinition.getSequenceOrder(),
                processDefinition.getCreatedAt()
        );
    }

    private ProcessExecutionResponse toExecutionResponse(ProcessExecution execution) {
        return new ProcessExecutionResponse(
                execution.getId(),
                execution.getScheduler() != null ? execution.getScheduler().getId() : null,
                execution.getProcessDefinition() != null ? execution.getProcessDefinition().getId() : null,
                execution.getState(),
                execution.getStartedAt(),
                execution.getFinishedAt(),
                execution.getExitCode(),
                execution.getStatusMessage(),
                execution.getLogs()
        );
    }
}
