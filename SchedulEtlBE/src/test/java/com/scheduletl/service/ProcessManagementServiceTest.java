package com.scheduletl.service;

import com.scheduletl.domain.ProcessDefinition;
import com.scheduletl.domain.ProcessExecution;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    @BeforeEach
    void setUp() {
        service = new ProcessManagementService(mock(SchedulerRepository.class),
                processDefinitionRepository, processExecutionRepository, processTypeRepository, shellCommandExecutor);
    }

    @Test
    void startsEnabledChildrenSequentiallyWhenStartingParent() {
        Scheduler scheduler = scheduler();
        ProcessDefinition parent = process(1L, scheduler, null, 0, true);
        ProcessDefinition secondChild = process(2L, scheduler, parent, 2, true);
        ProcessDefinition firstChild = process(3L, scheduler, parent, 1, true);
        ProcessDefinition disabledChild = process(4L, scheduler, parent, 3, false);
        List<Long> executionOrder = new ArrayList<>();

        when(processDefinitionRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(parent.getId()))
                .thenReturn(List.of(firstChild, secondChild, disabledChild));
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(firstChild.getId()))
                .thenReturn(List.of());
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(secondChild.getId()))
                .thenReturn(List.of());
        when(processExecutionRepository.save(any(ProcessExecution.class))).thenAnswer(invocation -> {
            ProcessExecution execution = invocation.getArgument(0);
            executionOrder.add(execution.getProcessDefinition().getId());
            return execution;
        });

        ProcessExecutionResponse response = service.startProcess(scheduler.getId(), parent.getId());

        assertEquals(parent.getId(), response.processDefinitionId());
        assertEquals(List.of(firstChild.getId(), secondChild.getId(),
                parent.getId(), firstChild.getId(), secondChild.getId()), executionOrder);
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
                "Updated", "SQLDATABASE", null, null, true, descendant.getId(), 0);

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
                "Updated", "UNKNOWN", null, null, true, null, 0);

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
        when(shellCommandExecutor.execute(process.getConfiguration()))
                .thenReturn(new ShellScriptExecutor.ExecutionResult(0, "hello from script", false));
        when(processExecutionRepository.save(any(ProcessExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(processDefinitionRepository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(process.getId()))
                .thenReturn(List.of());

        ProcessExecutionResponse response = service.startProcess(scheduler.getId(), process.getId());

        assertEquals("hello from script", response.logs());
        assertEquals(0, response.exitCode());
        assertEquals(com.scheduletl.domain.ProcessState.COMPLETED, response.state());
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
                new CreateProcessDefinitionRequest("Process 2", "SQLDATABASE", null, null, true, root.getId(), 2));

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
        ProcessManagementService creator = new ProcessManagementService(schedulerRepository,
                processDefinitionRepository, processExecutionRepository, processTypeRepository, shellCommandExecutor);

        assertThrows(ResponseStatusException.class, () -> creator.createProcessDefinition(scheduler.getId(),
                new CreateProcessDefinitionRequest("New", "SQLDATABASE", null, null, true, root.getId(), 1)));
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
        return process;
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
