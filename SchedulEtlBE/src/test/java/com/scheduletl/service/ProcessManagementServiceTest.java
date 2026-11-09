package com.scheduletl.service;

import com.scheduletl.domain.ProcessDefinition;
import com.scheduletl.domain.ProcessExecution;
import com.scheduletl.domain.ProcessScheduleDay;
import com.scheduletl.domain.ProcessStartSchedule;
import com.scheduletl.domain.ProcessState;
import com.scheduletl.domain.ProcessType;
import com.scheduletl.domain.Scheduler;
import com.scheduletl.dto.CreateProcessDefinitionRequest;
import com.scheduletl.dto.ProcessDefinitionResponse;
import com.scheduletl.dto.ProcessExecutionResponse;
import com.scheduletl.repository.ProcessDefinitionRepository;
import com.scheduletl.repository.ProcessExecutionRepository;
import com.scheduletl.repository.ProcessTypeRepository;
import com.scheduletl.repository.SchedulerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.time.LocalTime;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessManagementServiceTest {

    @Mock
    private ProcessDefinitionRepository processDefinitionRepository;

    @Mock
    private ProcessExecutionRepository processExecutionRepository;

    @Mock
    private ProcessTypeRepository processTypeRepository;

    @Mock
    private ShellScriptExecutor shellCommandExecutor;

    private ProcessManagementService service;
    private final ConcurrentHashMap<Long, ProcessDefinition> definitions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ProcessExecution> processExecutions = new ConcurrentHashMap<>();
    private final AtomicLong executionIds = new AtomicLong();

    @BeforeEach
    void setUp() {
        service = service(mock(SchedulerRepository.class));
        processExecutions.clear();
        executionIds.set(0);
        lenient().when(processDefinitionRepository.getReferenceById(anyLong()))
                .thenAnswer(invocation -> definitions.get(invocation.getArgument(0)));
        lenient().when(processExecutionRepository.findById(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(processExecutions.get(invocation.getArgument(0))));
        lenient().when(processExecutionRepository.save(any())).thenAnswer(invocation -> {
            ProcessExecution execution = invocation.getArgument(0);
            if (execution.getId() == null) {
                execution.setId(executionIds.incrementAndGet());
            }
            processExecutions.put(execution.getId(), execution);
            return execution;
        });
    }

    @Test
    void queuesTreeAndStartsEnabledChildrenOnIndependentBranches() {
        Scheduler scheduler = scheduler();
        ProcessDefinition parent = process(1L, scheduler, null, 0, true);
        ProcessDefinition secondChild = process(2L, scheduler, parent, 2, true);
        ProcessDefinition firstChild = process(3L, scheduler, parent, 1, true);
        ProcessDefinition disabledChild = process(4L, scheduler, parent, 3, false);
        when(processDefinitionRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(parent.getId()))
                .thenReturn(List.of(firstChild, secondChild, disabledChild));
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(firstChild.getId()))
                .thenReturn(List.of());
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(secondChild.getId()))
                .thenReturn(List.of());
        ProcessExecutionResponse response = service.startProcess(scheduler.getId(), parent.getId());

        assertEquals(parent.getId(), response.processDefinitionId());
        assertEquals(ProcessState.QUEUED, response.state());
        assertEquals(Set.of(parent.getId(), firstChild.getId(), secondChild.getId()),
                processExecutions.values().stream()
                        .filter(execution -> execution.getState() == ProcessState.RUNNING)
                        .map(execution -> execution.getProcessDefinition().getId())
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void persistsRecurringStartSchedule() {
        Scheduler scheduler = scheduler();
        SchedulerRepository schedulerRepository = mock(SchedulerRepository.class);
        var startSchedule = List.of(
                new com.scheduletl.dto.ProcessScheduleDayRequest(
                        com.scheduletl.domain.ProcessScheduleDay.MONDAY,
                        List.of(LocalTime.of(8, 30), LocalTime.of(12, 0)), LocalDate.of(2026, 12, 7)),
                new com.scheduletl.dto.ProcessScheduleDayRequest(
                        com.scheduletl.domain.ProcessScheduleDay.FRIDAY,
                        List.of(LocalTime.of(9, 15)), LocalDate.of(2026, 12, 4)));
        AtomicReference<ProcessDefinition> savedDefinition = new AtomicReference<>();
        when(schedulerRepository.findById(scheduler.getId())).thenReturn(Optional.of(scheduler));
        when(processTypeRepository.findById("SQLDATABASE")).thenReturn(Optional.of(processType()));
        when(processDefinitionRepository.save(any())).thenAnswer(invocation -> {
            ProcessDefinition definition = invocation.getArgument(0);
            definition.setId(1L);
            savedDefinition.set(definition);
            return definition;
        });

        ProcessDefinitionResponse response = service(schedulerRepository).createProcessDefinition(
                scheduler.getId(),
                new CreateProcessDefinitionRequest("Scheduled", "SQLDATABASE", null, null,
                        true, null, 0, false, startSchedule));

        assertEquals(3, savedDefinition.get().getStartSchedules().size());
        assertEquals(LocalDate.of(2026, 12, 7), savedDefinition.get().getStartSchedules().getFirst().getStartDate());
        assertEquals(2, response.startSchedule().getFirst().times().size());
        assertEquals(LocalDate.of(2026, 12, 7), response.startSchedule().getFirst().startDate());
        assertEquals(LocalTime.of(9, 15), response.startSchedule().getLast().times().getFirst());
        assertEquals(LocalDate.of(2026, 12, 4), response.startSchedule().getLast().startDate());
    }

    @Test
    void preventsManualStartWhenProcessHasAConfiguredSchedule() {
        Scheduler scheduler = scheduler();
        ProcessDefinition definition = new ProcessDefinition();
        definition.setId(8L);
        definition.setScheduler(scheduler);
        ProcessStartSchedule slot = new ProcessStartSchedule();
        slot.setDayOfWeek(ProcessScheduleDay.MONDAY);
        slot.setStartTime(LocalTime.of(8, 30));
        slot.setProcessDefinition(definition);
        definition.getStartSchedules().add(slot);
        when(processDefinitionRepository.findById(8L)).thenReturn(Optional.of(definition));

        assertThrows(ResponseStatusException.class,
                () -> service(Runnable::run, new Semaphore(1))
                        .startProcess(scheduler.getId(), definition.getId()));
    }

    @Test
    void childFailureDoesNotChangeParentOrSiblingExecutionStates() throws Exception {
        Scheduler scheduler = scheduler();
        ProcessDefinition parent = process(1L, scheduler, null, 0, true);
        parent.setType(shellCommandType());
        parent.setConfiguration("parent");
        ProcessDefinition failingChild = process(2L, scheduler, parent, 1, true);
        failingChild.setType(shellCommandType());
        failingChild.setConfiguration("failing-child");
        ProcessDefinition sibling = process(3L, scheduler, parent, 2, true);
        sibling.setType(shellCommandType());
        sibling.setConfiguration("sibling");

        when(processDefinitionRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(parent.getId()))
                .thenReturn(List.of(failingChild, sibling));
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(failingChild.getId()))
                .thenReturn(List.of());
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(sibling.getId()))
                .thenReturn(List.of());
        when(shellCommandExecutor.execute(any(), any())).thenAnswer(invocation -> {
            String configuration = invocation.getArgument(0);
            return new ShellScriptExecutor.ExecutionResult(
                    "failing-child".equals(configuration) ? 1 : 0, configuration, false);
        });

        service.startProcess(scheduler.getId(), parent.getId());

        assertEquals(ProcessState.COMPLETED, processExecutions.get(1L).getState());
        assertEquals(ProcessState.FAILED, processExecutions.get(2L).getState());
        assertEquals(ProcessState.COMPLETED, processExecutions.get(3L).getState());
    }

    @Test
    void executesIndependentChildBranchesConcurrently() throws Exception {
        Scheduler scheduler = scheduler();
        ProcessDefinition parent = process(1L, scheduler, null, 0, true);
        parent.setType(shellCommandType());
        parent.setConfiguration("parent");
        ProcessDefinition firstChild = process(2L, scheduler, parent, 1, true);
        firstChild.setType(shellCommandType());
        firstChild.setConfiguration("first");
        ProcessDefinition secondChild = process(3L, scheduler, parent, 2, true);
        secondChild.setType(shellCommandType());
        secondChild.setConfiguration("second");
        CountDownLatch childrenStarted = new CountDownLatch(2);
        CountDownLatch allowChildrenToFinish = new CountDownLatch(1);
        when(processDefinitionRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(parent.getId()))
                .thenReturn(List.of(firstChild, secondChild));
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(firstChild.getId()))
                .thenReturn(List.of());
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(secondChild.getId()))
                .thenReturn(List.of());
        when(shellCommandExecutor.execute(any(), any())).thenAnswer(invocation -> {
            String configuration = invocation.getArgument(0);
            if (!"parent".equals(configuration)) {
                childrenStarted.countDown();
                if (!allowChildrenToFinish.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("The child branches did not overlap");
                }
            }
            return new ShellScriptExecutor.ExecutionResult(0, configuration, false);
        });

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            ProcessManagementService parallelService = service(executor, new Semaphore(2, true));
            ProcessExecutionResponse response = parallelService.startProcess(scheduler.getId(), parent.getId());

            assertEquals(ProcessState.QUEUED, response.state());
            assertTrue(childrenStarted.await(5, TimeUnit.SECONDS),
                    "Both independent child processes should run concurrently");
            allowChildrenToFinish.countDown();
        } finally {
            allowChildrenToFinish.countDown();
        }
    }

    @Test
    void executesIndependentSchedulersConcurrently() throws Exception {
        Scheduler firstScheduler = scheduler();
        Scheduler secondScheduler = scheduler();
        secondScheduler.setId(8L);
        ProcessDefinition first = process(11L, firstScheduler, null, 0, true);
        ProcessDefinition second = process(12L, secondScheduler, null, 0, true);
        first.setType(shellCommandType());
        first.setConfiguration("scheduler-one");
        second.setType(shellCommandType());
        second.setConfiguration("scheduler-two");
        CountDownLatch schedulersStarted = new CountDownLatch(2);
        CountDownLatch allowSchedulersToFinish = new CountDownLatch(1);
        when(processDefinitionRepository.findById(first.getId())).thenReturn(Optional.of(first));
        when(processDefinitionRepository.findById(second.getId())).thenReturn(Optional.of(second));
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(first.getId()))
                .thenReturn(List.of());
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(second.getId()))
                .thenReturn(List.of());
        when(shellCommandExecutor.execute(any(), any())).thenAnswer(invocation -> {
            String configuration = invocation.getArgument(0);
            schedulersStarted.countDown();
            if (!allowSchedulersToFinish.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Independent schedulers did not overlap");
            }
            return new ShellScriptExecutor.ExecutionResult(0, configuration, false);
        });

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            ProcessManagementService parallelService = service(executor, new Semaphore(2, true));

            assertEquals(ProcessState.QUEUED,
                    parallelService.startProcess(firstScheduler.getId(), first.getId()).state());
            assertEquals(ProcessState.QUEUED,
                    parallelService.startProcess(secondScheduler.getId(), second.getId()).state());
            assertTrue(schedulersStarted.await(5, TimeUnit.SECONDS),
                    "Different schedulers should execute concurrently");
            allowSchedulersToFinish.countDown();
        } finally {
            allowSchedulersToFinish.countDown();
        }
    }

    @Test
    void cancelsQueuedTreeWhenExecutionCannotBeDispatched() {
        Scheduler scheduler = scheduler();
        ProcessDefinition process = process(1L, scheduler, null, 0, true);
        when(processDefinitionRepository.findById(process.getId())).thenReturn(Optional.of(process));
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(process.getId()))
                .thenReturn(List.of());
        Executor rejectingExecutor = task -> {
            throw new RejectedExecutionException("Executor is shutting down");
        };
        ProcessManagementService rejectingService = service(rejectingExecutor, new Semaphore(4, true));

        assertThrows(RejectedExecutionException.class,
                () -> rejectingService.startProcess(scheduler.getId(), process.getId()));

        assertEquals(ProcessState.CANCELLED, processExecutions.get(1L).getState());
    }

    @Test
    void cancelsChildTreeWhenParentFailsByDefault() throws Exception {
        Scheduler scheduler = scheduler();
        ProcessDefinition parent = process(1L, scheduler, null, 0, true);
        parent.setType(shellCommandType());
        parent.setConfiguration("{\"script\":\"parent.sh\"}");
        ProcessDefinition child = process(2L, scheduler, parent, 1, true);
        ProcessDefinition grandchild = process(3L, scheduler, child, 2, true);
        when(processDefinitionRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(parent.getId()))
                .thenReturn(List.of(child));
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(child.getId()))
                .thenReturn(List.of(grandchild));
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(grandchild.getId()))
                .thenReturn(List.of());
        when(shellCommandExecutor.execute(eq(parent.getConfiguration()), any()))
                .thenReturn(new ShellScriptExecutor.ExecutionResult(1, "failed", false));

        ProcessExecutionResponse response = service.startProcess(scheduler.getId(), parent.getId());

        assertEquals(ProcessState.QUEUED, response.state());
        assertEquals(ProcessState.CANCELLED, processExecutions.get(2L).getState());
        assertEquals(ProcessState.CANCELLED, processExecutions.get(3L).getState());
    }

    @Test
    void runsChildWhenConfiguredToContinueAfterParentFailure() throws Exception {
        Scheduler scheduler = scheduler();
        ProcessDefinition parent = process(1L, scheduler, null, 0, true);
        parent.setType(shellCommandType());
        parent.setConfiguration("{\"script\":\"parent.sh\"}");
        ProcessDefinition child = process(2L, scheduler, parent, 1, true);
        child.setRunIfParentFails(true);
        when(processDefinitionRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(parent.getId()))
                .thenReturn(List.of(child));
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(child.getId()))
                .thenReturn(List.of());
        when(shellCommandExecutor.execute(eq(parent.getConfiguration()), any()))
                .thenReturn(new ShellScriptExecutor.ExecutionResult(1, "failed", false));

        ProcessExecutionResponse response = service.startProcess(scheduler.getId(), parent.getId());

        assertEquals(ProcessState.QUEUED, response.state());
        assertEquals(ProcessState.RUNNING, processExecutions.get(2L).getState());
    }

    @Test
    void rejectsMovingProcessUnderItsOwnDescendant() {
        Scheduler scheduler = scheduler();
        ProcessDefinition process = process(1L, scheduler, null, 0, true);
        ProcessDefinition descendant = process(2L, scheduler, process, 0, true);
        process.setParentProcess(descendant);

        when(processDefinitionRepository.findById(process.getId())).thenReturn(Optional.of(process));
        when(processDefinitionRepository.findById(descendant.getId())).thenReturn(Optional.of(descendant));
        when(processTypeRepository.findById("SQLDATABASE")).thenReturn(Optional.of(processType()));

        CreateProcessDefinitionRequest request = new CreateProcessDefinitionRequest(
                "Updated", "SQLDATABASE", null, null, true, descendant.getId(), 0, false);

        assertThrows(ResponseStatusException.class,
                () -> service.updateProcessDefinition(scheduler.getId(), process.getId(), request));
    }

    @Test
    void rejectsUnsupportedProcessType() {
        Scheduler scheduler = scheduler();
        ProcessDefinition process = process(1L, scheduler, null, 0, true);
        when(processDefinitionRepository.findById(process.getId())).thenReturn(Optional.of(process));
        when(processTypeRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        CreateProcessDefinitionRequest request = new CreateProcessDefinitionRequest(
                "Updated", "UNKNOWN", null, null, true, null, 0, false);

        assertThrows(ResponseStatusException.class,
                () -> service.updateProcessDefinition(scheduler.getId(), process.getId(), request));
    }

    @Test
    void storesShellOutputAndExitStatus() throws Exception {
        Scheduler scheduler = scheduler();
        ProcessDefinition process = process(1L, scheduler, null, 0, true);
        process.setType(shellCommandType());
        process.setConfiguration("{\"script\":\"approved.sh\"}");
        when(processDefinitionRepository.findById(process.getId())).thenReturn(Optional.of(process));
        when(shellCommandExecutor.execute(eq(process.getConfiguration()), any()))
                .thenReturn(new ShellScriptExecutor.ExecutionResult(0, "hello from script", false));
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(process.getId()))
                .thenReturn(List.of());

        ProcessExecutionResponse response = service.startProcess(scheduler.getId(), process.getId());

        assertEquals("Queued for execution", response.logs());
        assertEquals(ProcessState.QUEUED, response.state());
        assertEquals("hello from script", processExecutions.get(response.id()).getLogs());
        assertEquals(ProcessState.COMPLETED, processExecutions.get(response.id()).getState());
    }

    @Test
    void swapsOrdersWhenAChildTakesAnOrderAlreadyUsedInTheHierarchy() {
        Scheduler scheduler = scheduler();
        ProcessDefinition root = process(1L, scheduler, null, 0, true);
        ProcessDefinition first = process(2L, scheduler, root, 1, true);
        ProcessDefinition second = process(3L, scheduler, root, 2, true);
        when(processDefinitionRepository.findById(first.getId())).thenReturn(Optional.of(first));
        when(processDefinitionRepository.findById(root.getId())).thenReturn(Optional.of(root));
        when(processDefinitionRepository.findBySchedulerIdOrderByParentProcessIdAscSequenceOrderAscIdAsc(scheduler.getId()))
                .thenReturn(List.of(root, first, second));
        when(processTypeRepository.findById("SQLDATABASE")).thenReturn(Optional.of(processType()));
        when(processDefinitionRepository.save(any(ProcessDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProcessDefinitionResponse response = service.updateProcessDefinition(scheduler.getId(), first.getId(),
                new CreateProcessDefinitionRequest("Process 2", "SQLDATABASE", null, null, true, root.getId(), 2, false));

        assertEquals(2, response.sequenceOrder());
        assertEquals(1, second.getSequenceOrder());
    }

    @Test
    void rejectsACreatedChildWithAnOrderAlreadyUsedInTheHierarchy() {
        Scheduler scheduler = scheduler();
        ProcessDefinition root = process(1L, scheduler, null, 0, true);
        ProcessDefinition child = process(2L, scheduler, root, 1, true);
        when(processDefinitionRepository.findById(root.getId())).thenReturn(Optional.of(root));
        when(processDefinitionRepository.findBySchedulerIdOrderByParentProcessIdAscSequenceOrderAscIdAsc(scheduler.getId()))
                .thenReturn(List.of(root, child));
        when(processTypeRepository.findById("SQLDATABASE")).thenReturn(Optional.of(processType()));
        SchedulerRepository schedulerRepository = mock(SchedulerRepository.class);
        when(schedulerRepository.findById(scheduler.getId())).thenReturn(Optional.of(scheduler));
        ProcessManagementService creator = service(schedulerRepository);

        assertThrows(ResponseStatusException.class, () -> creator.createProcessDefinition(scheduler.getId(),
                new CreateProcessDefinitionRequest("New", "SQLDATABASE", null, null, true, root.getId(), 1, false)));
    }

    private Scheduler scheduler() {
        Scheduler scheduler = new Scheduler();
        scheduler.setId(7L);
        return scheduler;
    }

    private ProcessDefinition process(Long id, Scheduler scheduler, ProcessDefinition parent, int order, boolean enabled) {
        ProcessDefinition process = new ProcessDefinition();
        process.setId(id);
        process.setScheduler(scheduler);
        process.setParentProcess(parent);
        process.setSequenceOrder(order);
        process.setEnabled(enabled);
        process.setName("Process " + id);
        process.setType(processType());
        definitions.put(id, process);
        return process;
    }

    private ProcessManagementService service(SchedulerRepository schedulerRepository) {
        return service(schedulerRepository, Runnable::run, new Semaphore(4, true));
    }

    private ProcessManagementService service(Executor executor, Semaphore semaphore) {
        return service(mock(SchedulerRepository.class), executor, semaphore);
    }

    private ProcessManagementService service(SchedulerRepository schedulerRepository, Executor executor,
                                             Semaphore semaphore) {
        return new ProcessManagementService(schedulerRepository, processDefinitionRepository,
                processExecutionRepository, processTypeRepository,
                new ProcessExecutionWorker(processExecutionRepository, shellCommandExecutor,
                        mock(ProcessLogService.class)), executor, semaphore);
    }

    private ProcessType processType() {
        ProcessType processType = new ProcessType();
        processType.setCode("SQLDATABASE");
        processType.setDescription("SQL database process");
        return processType;
    }

    private ProcessType shellCommandType() {
        ProcessType processType = new ProcessType();
        processType.setCode("SHELL_CMD");
        processType.setDescription("Shell command");
        return processType;
    }
}
