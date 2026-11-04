package com.scheduletl.controller;

import com.scheduletl.dto.ProcessTypeResponse;
import com.scheduletl.repository.ProcessTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/process-types")
@RequiredArgsConstructor
public class ProcessTypeController {

    private final ProcessTypeRepository processTypeRepository;

    @GetMapping
    public List<ProcessTypeResponse> listProcessTypes() {
        return processTypeRepository.findAllByOrderByCodeAsc().stream()
                .map(type -> new ProcessTypeResponse(type.getCode(), type.getDescription()))
                .toList();
    }
}
