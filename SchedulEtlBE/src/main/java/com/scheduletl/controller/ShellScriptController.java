package com.scheduletl.controller;

import com.scheduletl.dto.ShellScriptOptionResponse;
import com.scheduletl.service.RegisteredShellScriptService;
import com.scheduletl.service.ShellCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/shell-scripts")
@RequiredArgsConstructor
public class ShellScriptController {

    private final ShellCatalogService shellCatalogService;
    private final RegisteredShellScriptService registeredScriptService;

    @GetMapping
    public List<ShellScriptOptionResponse> listApprovedScripts() throws IOException {
        return java.util.stream.Stream.concat(shellCatalogService.listScripts().stream(),
                        registeredScriptService.listOptions().stream())
                .sorted(java.util.Comparator.comparing(ShellScriptOptionResponse::id))
                .toList();
    }
}
