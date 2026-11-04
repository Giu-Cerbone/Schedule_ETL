package com.scheduletl.controller;

import com.scheduletl.dto.ShellCommandOptionResponse;
import com.scheduletl.service.ShellCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shell-commands")
@RequiredArgsConstructor
public class ShellCommandController {

    private final ShellCatalogService shellCatalogService;

    @GetMapping
    public List<ShellCommandOptionResponse> listConfiguredCommands() {
        return shellCatalogService.listCommandOptions();
    }
}
