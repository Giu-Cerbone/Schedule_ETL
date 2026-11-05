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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

@Service
public class ProcessManagementService {

    private final SchedulerRepository schedulerRepository;
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final ProcessExecutionRepository processExecutionRepository;
    private final ProcessTypeRepository processTypeRepository;
    private final ProcessExecutionWorker processExecutionWorker;
    private final Executor processExecutionExecutor;
    private final Semaphore processExecutionSemaphore;
    private final Set<Long> activeProcessDefinitions = ConcurrentHashMap.newKeySet();
    private final Object activeProcessLock = new Object();

    public ProcessManagementService(SchedulerRepository schedulerRepository,
                                    ProcessDefinitionRepository processDefinitionRepository,
                                    ProcessExecutionRepository processExecutionRepository,
                                    ProcessTypeRepository processTypeRepository,
                                    ProcessExecutionWorker processExecutionWorker,
                                    @Qualifier("processExecutionExecutor") Executor processExecutionExecutor,
                                    Semaphore processExecutionSemaphore) {
        this.schedulerRepository = schedulerRepository;
        this.processDefinitionRepository = processDefinitionRepository;
        this.processExecutionRepository = processExecutionRepository;
        this.processTypeRepository = processTypeRepository;
        this.processExecutionWorker = processExecutionWorker;
        this.processExecutionExecutor = processExecutionExecutor;
        this.processExecutionSemaphore = processExecutionSemaphore;
    }

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
        definition.setParentProcess(findParentProcess(schedulerId, null, request.parentProcessId()));
        definition.setRunIfParentFails(definition.getParentProcess() != null && request.runIfParentFails());
        definition.setSequenceOrder(newSequenceOrder(schedulerId, definition.getParentProcess(), request.sequenceOrder()));

        ProcessDefinition saved = processDefinitionRepository.save(definition);
        return toDefinitionResponse(saved);
    }

    @Transactional
    public ProcessDefinitionResponse updateProcessDefinition(Long schedulerId, Long processDefinitionId,
                                                             CreateProcessDefinitionRequest request) {
        ProcessDefinition definition = findProcessDefinition(schedulerId, processDefinitionId);
        ProcessType type = findProcessType(request.type());
        ProcessDefinition newParent = findParentProcess(schedulerId, processDefinitionId, request.parentProcessId());
        applySequenceOrder(schedulerId, definition, newParent, request.sequenceOrder());
        definition.setName(request.name());
        definition.setType(type);
        definition.setDescription(request.description());
        definition.setConfiguration(request.configuration());
        definition.setEnabled(request.enabled());
        definition.setParentProcess(newParent);
        definition.setRunIfParentFails(newParent != null && request.runIfParentFails());
        return toDefinitionResponse(processDefinitionRepository.save(definition));
    }

    private static ProcessDefinition rootOf(ProcessDefinition definition) {
        ProcessDefinition current = definition;
        while (current.getParentProcess() != null) {
            current = current.getParentProcess();
        }
        return current;
    }

    private List<ProcessDefinition> treeMembers(Long schedulerId, ProcessDefinition root) {
        return processDefinitionRepository.findBySchedulerIdOrderByParentProcessIdAscSequenceOrderAscIdAsc(schedulerId)
                .stream()
                .filter(item -> rootOf(item).getId().equals(root.getId()))
                .toList();
    }

    private int newSequenceOrder(Long schedulerId, ProcessDefinition parent, Integer requested) {
        if (parent == null) {
            return 0;
        }
        List<ProcessDefinition> members = treeMembers(schedulerId, rootOf(parent));
        if (requested == null) {
            return members.stream().mapToInt(ProcessDefinition::getSequenceOrder).max().orElse(0) + 1;
        }
        if (requested < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Order 0 is reserved to the root process; child processes start from 1");
        }
        if (members.stream().anyMatch(item -> item.getSequenceOrder() == requested)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Another process in this hierarchy already uses execution order " + requested);
        }
        return requested;
    }

    /** Root processes are always 0; descendants keep a unique order in their hierarchy, swapping on a clash. */
    private void applySequenceOrder(Long schedulerId, ProcessDefinition definition, ProcessDefinition newParent,
                                    Integer requested) {
        if (newParent == null) {
            definition.setSequenceOrder(0);
            return;
        }
        ProcessDefinition targetRoot = rootOf(newParent);
        boolean sameTree = rootOf(definition).getId().equals(targetRoot.getId());
        List<ProcessDefinition> subtree = descendantsAndSelf(schedulerId, definition);
        List<ProcessDefinition> others = treeMembers(schedulerId, targetRoot).stream()
                .filter(item -> subtree.stream().noneMatch(node -> node.getId().equals(item.getId())))
                .toList();

        if (sameTree) {
            int oldOrder = definition.getSequenceOrder();
            int target = requested != null ? requested : oldOrder;
            if (target < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Order 0 is reserved to the root process; child processes start from 1");
            }
            ProcessDefinition clash = treeMembers(schedulerId, targetRoot).stream()
                    .filter(item -> !item.getId().equals(definition.getId()) && item.getSequenceOrder() == target)
                    .findFirst().orElse(null);
            if (clash != null) {
                clash.setSequenceOrder(oldOrder >= 1 ? oldOrder
                        : treeMembers(schedulerId, targetRoot).stream()
                                .mapToInt(ProcessDefinition::getSequenceOrder).max().orElse(0) + 1);
                processDefinitionRepository.save(clash);
            }
            definition.setSequenceOrder(target);
            return;
        }

        int target = requested != null ? requested
                : Math.max(others.stream().mapToInt(ProcessDefinition::getSequenceOrder).max().orElse(0),
                subtree.stream().filter(node -> !node.getId().equals(definition.getId()))
                        .mapToInt(ProcessDefinition::getSequenceOrder).max().orElse(0)) + 1;
        if (target < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Order 0 is reserved to the root process; child processes start from 1");
        }
        java.util.Set<Integer> used = new java.util.HashSet<>();
        others.forEach(item -> used.add(item.getSequenceOrder()));
        boolean clash = !used.add(target);
        for (ProcessDefinition node : subtree) {
            if (!node.getId().equals(definition.getId()) && !used.add(node.getSequenceOrder())) {
                clash = true;
            }
        }
        if (clash) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Execution orders of the moved process tree clash with the destination hierarchy");
        }
        definition.setSequenceOrder(target);
    }

    private List<ProcessDefinition> descendantsAndSelf(Long schedulerId, ProcessDefinition definition) {
        return processDefinitionRepository.findBySchedulerIdOrderByParentProcessIdAscSequenceOrderAscIdAsc(schedulerId)
                .stream()
                .filter(item -> {
                    for (ProcessDefinition current = item; current != null; current = current.getParentProcess()) {
                        if (current.getId().equals(definition.getId())) return true;
                    }
                    return false;
                })
                .toList();
    }

    @Transactional
    public ProcessExecutionResponse startProcess(Long schedulerId, Long processDefinitionId) {
        ProcessDefinition definition = findProcessDefinition(schedulerId, processDefinitionId);
        ProcessNode processTree = snapshotTree(definition);
        List<Long> claimedIds = processTree.definitionIds();
        synchronized (activeProcessLock) {
            if (claimedIds.stream().anyMatch(activeProcessDefinitions::contains)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This process or one of its descendants is already queued or running");
            }
            activeProcessDefinitions.addAll(claimedIds);
        }

        try {
            QueuedProcessNode queuedTree = queueTree(processTree, definition.getScheduler(), "Queued for execution");
            ProcessExecutionResponse response = new ProcessExecutionResponse(
                    queuedTree.executionId(), schedulerId, processDefinitionId, ProcessState.QUEUED,
                    null, null, null, "Queued for execution", "Queued for execution");
            dispatchAfterCommit(() -> {
                try {
                    executeTree(queuedTree);
                } finally {
                    claimedIds.forEach(activeProcessDefinitions::remove);
                }
            }, () -> {
                try {
                    cancelTree(queuedTree, "Execution was not started because the worker is unavailable");
                } finally {
                    claimedIds.forEach(activeProcessDefinitions::remove);
                }
            });
            return response;
        } catch (RuntimeException exception) {
            claimedIds.forEach(activeProcessDefinitions::remove);
            throw exception;
        }
    }

    private ProcessNode snapshotTree(ProcessDefinition definition) {
        List<ProcessNode> children = processDefinitionRepository
                .findByParentProcessIdOrderBySequenceOrderAscIdAsc(definition.getId()).stream()
                .filter(ProcessDefinition::isEnabled)
                .map(this::snapshotTree)
                .toList();
        return new ProcessNode(definition.getId(), definition.getType().getCode(), definition.getConfiguration(),
                definition.isRunIfParentFails(), children);
    }

    private QueuedProcessNode queueTree(ProcessNode node, Scheduler scheduler, String message) {
        ProcessDefinition definition = processDefinitionRepository.getReferenceById(node.definitionId());
        ProcessExecution execution = new ProcessExecution();
        execution.setScheduler(scheduler);
        execution.setProcessDefinition(definition);
        execution.setState(ProcessState.QUEUED);
        execution.setStatusMessage(message);
        execution.setLogs(message);
        execution.setStartedAt(null);
        execution.setExitCode(null);
        ProcessExecution queued = processExecutionRepository.save(execution);
        List<QueuedProcessNode> children = node.children().stream()
                .map(child -> queueTree(child, scheduler, "Queued after parent process " + node.definitionId()))
                .toList();
        return new QueuedProcessNode(node, queued.getId(), children);
    }

    private void dispatchAfterCommit(Runnable task, Runnable onRejected) {
        Runnable dispatch = () -> {
            try {
                processExecutionExecutor.execute(task);
            } catch (RejectedExecutionException exception) {
                try {
                    onRejected.run();
                } catch (RuntimeException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
                throw exception;
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }

    private void executeTree(QueuedProcessNode node) {
        processExecutionWorker.markRunning(node.executionId());
        ProcessExecutionWorker.Outcome outcome;
        boolean permitAcquired = false;
        try {
            if ("SHELL_CMD".equals(node.process().type())) {
                processExecutionSemaphore.acquire();
                permitAcquired = true;
            }
            outcome = processExecutionWorker.execute(node.process().type(), node.process().configuration());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            outcome = new ProcessExecutionWorker.Outcome(ProcessState.FAILED, -1,
                    "Process execution was interrupted", "Process execution was interrupted before completion.");
        } finally {
            if (permitAcquired) {
                processExecutionSemaphore.release();
            }
        }
        processExecutionWorker.complete(node.executionId(), outcome);

        List<QueuedProcessNode> runnableChildren = new ArrayList<>();
        for (QueuedProcessNode child : node.children()) {
            if (outcome.state() == ProcessState.FAILED && !child.process().runIfParentFails()) {
                cancelTree(child, "Skipped because parent process " + node.process().definitionId() + " failed");
            } else {
                runnableChildren.add(child);
            }
        }
        if (runnableChildren.size() == 1) {
            executeTree(runnableChildren.getFirst());
        } else if (!runnableChildren.isEmpty()) {
            CompletableFuture<?>[] tasks = runnableChildren.stream()
                    .map(child -> CompletableFuture.runAsync(() -> executeTree(child), processExecutionExecutor))
                    .toArray(CompletableFuture[]::new);
            try {
                CompletableFuture.allOf(tasks).join();
            } catch (CompletionException exception) {
                throw new IllegalStateException("A child process branch failed unexpectedly", exception.getCause());
            }
        }
    }

    private void cancelTree(QueuedProcessNode node, String reason) {
        processExecutionWorker.cancelQueued(node.executionId(), reason);
        for (QueuedProcessNode child : node.children()) {
            cancelTree(child, "Skipped because an ancestor process failed");
        }
    }

    private record ProcessNode(Long definitionId, String type, String configuration, boolean runIfParentFails,
                               List<ProcessNode> children) {
        private List<Long> definitionIds() {
            List<Long> ids = new ArrayList<>();
            ids.add(definitionId);
            children.forEach(child -> ids.addAll(child.definitionIds()));
            return ids;
        }
    }

    private record QueuedProcessNode(ProcessNode process, Long executionId, List<QueuedProcessNode> children) {
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
        execution.setExitCode(command == ProcessCommand.CANCEL ? null : 0);

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
                processDefinition.isRunIfParentFails(),
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
