package com.scheduletl.controller;

import com.scheduletl.dto.ProcessEventLogPageResponse;
import com.scheduletl.service.ProcessEventLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/process-logs")
@Tag(name = "Process Logs", description = "Journal of process scheduling, execution steps and outcomes")
@RequiredArgsConstructor
public class ProcessEventLogController {

    private final ProcessEventLogQueryService queryService;

    @GetMapping
    @Operation(summary = "Search the process journal with optional filters")
    public ResponseEntity<ProcessEventLogPageResponse> search(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) Long schedulerId,
            @RequestParam(required = false) Long processDefinitionId,
            @RequestParam(required = false) Long executionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(queryService.search(new ProcessEventLogQueryService.Filter(
                text, level, eventType, schedulerId, processDefinitionId, executionId, triggerType, from, to),
                page, size));
    }
}
