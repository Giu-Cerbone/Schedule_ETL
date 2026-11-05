package com.scheduletl.controller;

import com.scheduletl.dto.CreateProcessDefinitionRequest;
import com.scheduletl.dto.ProcessDefinitionResponse;
import com.scheduletl.dto.ProcessExecutionResponse;
import com.scheduletl.service.ProcessManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/schedulers")
@Tag(name = "Process Execution", description = "Runtime control of every process inside a scheduler")
@RequiredArgsConstructor
public class ProcessController {

    private final ProcessManagementService processManagementService;

    @GetMapping("/{schedulerId}/processes")
    @Operation(summary = "List all process definitions for a scheduler")
    public ResponseEntity<List<ProcessDefinitionResponse>> listProcesses(@PathVariable Long schedulerId) {
        return ResponseEntity.ok(processManagementService.getProcessDefinitions(schedulerId));
    }

    @PostMapping("/{schedulerId}/processes")
    @Operation(summary = "Create a process definition under a scheduler")
    public ResponseEntity<ProcessDefinitionResponse> createProcess(@PathVariable Long schedulerId,
                                                                 @Valid @RequestBody CreateProcessDefinitionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(processManagementService.createProcessDefinition(schedulerId, request));
    }

    @PutMapping("/{schedulerId}/processes/{processDefinitionId}")
    @Operation(summary = "Update a process definition and its parent or sequence")
    public ResponseEntity<ProcessDefinitionResponse> updateProcess(@PathVariable Long schedulerId,
                                                                   @PathVariable Long processDefinitionId,
                                                                   @Valid @RequestBody CreateProcessDefinitionRequest request) {
        return ResponseEntity.ok(processManagementService.updateProcessDefinition(schedulerId, processDefinitionId, request));
    }

    @PostMapping("/{schedulerId}/processes/{processDefinitionId}/start")
    @Operation(summary = "Queue a process tree for background execution")
    public ResponseEntity<ProcessExecutionResponse> start(@PathVariable Long schedulerId,
                                                        @PathVariable Long processDefinitionId) {
        return ResponseEntity.ok(processManagementService.startProcess(schedulerId, processDefinitionId));
    }

    @PostMapping("/{schedulerId}/processes/{processDefinitionId}/pause")
    @Operation(summary = "Pause a process")
    public ResponseEntity<ProcessExecutionResponse> pause(@PathVariable Long schedulerId,
                                                        @PathVariable Long processDefinitionId) {
        return ResponseEntity.ok(processManagementService.pauseProcess(schedulerId, processDefinitionId));
    }

    @PostMapping("/{schedulerId}/processes/{processDefinitionId}/resume")
    @Operation(summary = "Resume a process")
    public ResponseEntity<ProcessExecutionResponse> resume(@PathVariable Long schedulerId,
                                                         @PathVariable Long processDefinitionId) {
        return ResponseEntity.ok(processManagementService.resumeProcess(schedulerId, processDefinitionId));
    }

    @PostMapping("/{schedulerId}/processes/{processDefinitionId}/stop")
    @Operation(summary = "Stop a process")
    public ResponseEntity<ProcessExecutionResponse> stop(@PathVariable Long schedulerId,
                                                       @PathVariable Long processDefinitionId) {
        return ResponseEntity.ok(processManagementService.stopProcess(schedulerId, processDefinitionId));
    }

    @GetMapping("/{schedulerId}/processes/{processDefinitionId}/history")
    @Operation(summary = "Get execution history for a process")
    public ResponseEntity<List<ProcessExecutionResponse>> history(@PathVariable Long schedulerId,
                                                                 @PathVariable Long processDefinitionId) {
        return ResponseEntity.ok(processManagementService.getExecutionHistory(schedulerId, processDefinitionId));
    }
}
