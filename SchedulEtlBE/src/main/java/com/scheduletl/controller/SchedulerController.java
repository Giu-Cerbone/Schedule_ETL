package com.scheduletl.controller;

import com.scheduletl.dto.CreateSchedulerRequest;
import com.scheduletl.dto.SchedulerResponse;
import com.scheduletl.dto.UpdateSchedulerStatusRequest;
import com.scheduletl.service.SchedulerService;
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
@Tag(name = "Schedulers", description = "Scheduler management and ownership")
@RequiredArgsConstructor
public class SchedulerController {

    private final SchedulerService schedulerService;

    @GetMapping
    @Operation(summary = "List all schedulers")
    public ResponseEntity<List<SchedulerResponse>> findAll() {
        return ResponseEntity.ok(schedulerService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get scheduler by id")
    public ResponseEntity<SchedulerResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(schedulerService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Create a new scheduler")
    public ResponseEntity<SchedulerResponse> create(@Valid @RequestBody CreateSchedulerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schedulerService.create(request));
    }

    @PutMapping("/{schedulerId}/groups/{groupId}")
    @Operation(summary = "Assign a scheduler to a user group")
    public ResponseEntity<SchedulerResponse> assignToGroup(@PathVariable Long schedulerId, @PathVariable Long groupId) {
        return ResponseEntity.ok(schedulerService.assignToGroup(schedulerId, groupId));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update a scheduler status")
    public ResponseEntity<SchedulerResponse> updateStatus(@PathVariable Long id,
                                                           @Valid @RequestBody UpdateSchedulerStatusRequest request) {
        return ResponseEntity.ok(schedulerService.updateStatus(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a scheduler logically")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        schedulerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
