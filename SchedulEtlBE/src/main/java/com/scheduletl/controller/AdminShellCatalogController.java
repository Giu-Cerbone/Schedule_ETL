package com.scheduletl.controller;

import com.scheduletl.dto.ApprovedShellScriptContentResponse;
import com.scheduletl.dto.ApprovedShellScriptRequest;
import com.scheduletl.dto.AuthorizedShellCommandRequest;
import com.scheduletl.dto.AuthorizedShellCommandResponse;
import com.scheduletl.dto.FileBrowseResponse;
import com.scheduletl.dto.RegisteredShellScriptRequest;
import com.scheduletl.dto.RegisteredShellScriptResponse;
import com.scheduletl.service.FileBrowserService;
import com.scheduletl.service.RegisteredShellScriptService;
import com.scheduletl.service.ShellCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminShellCatalogController {

    private final ShellCatalogService catalogService;
    private final FileBrowserService fileBrowserService;
    private final RegisteredShellScriptService registeredScriptService;

    @GetMapping("/filesystem")
    public FileBrowseResponse browse(@RequestParam(name = "path", required = false) String path) {
        return fileBrowserService.browse(path);
    }

    @GetMapping("/registered-scripts")
    public List<RegisteredShellScriptResponse> listRegisteredScripts() {
        return registeredScriptService.list();
    }

    @GetMapping("/registered-scripts/{id}/content")
    public ApprovedShellScriptContentResponse getRegisteredScriptContent(@PathVariable Long id) throws IOException {
        return registeredScriptService.getContent(id);
    }

    @PutMapping("/registered-scripts/{id}/content")
    public ApprovedShellScriptContentResponse updateRegisteredScriptContent(
            @PathVariable Long id, @Valid @RequestBody ApprovedShellScriptRequest request) throws IOException {
        return registeredScriptService.updateContent(id, request);
    }

    @PostMapping("/registered-scripts")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisteredShellScriptResponse registerScript(@Valid @RequestBody RegisteredShellScriptRequest request)
            throws IOException {
        return registeredScriptService.register(request.name(), request.path());
    }

    @DeleteMapping("/registered-scripts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregisterScript(@PathVariable Long id) {
        registeredScriptService.delete(id);
    }

    @GetMapping("/shell-commands")
    public List<AuthorizedShellCommandResponse> listCommands() {
        return catalogService.listCommands();
    }

    @PostMapping("/shell-commands")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthorizedShellCommandResponse createCommand(@Valid @RequestBody AuthorizedShellCommandRequest request) {
        return catalogService.createCommand(request);
    }

    @PutMapping("/shell-commands/{id}")
    public AuthorizedShellCommandResponse updateCommand(@PathVariable Long id,
                                                         @Valid @RequestBody AuthorizedShellCommandRequest request) {
        return catalogService.updateCommand(id, request);
    }

    @DeleteMapping("/shell-commands/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCommand(@PathVariable Long id) {
        catalogService.deleteCommand(id);
    }

    @GetMapping("/shell-scripts/{fileName}")
    public ApprovedShellScriptContentResponse getScript(@PathVariable String fileName) throws IOException {
        return catalogService.getScript(fileName);
    }

    @PostMapping("/shell-scripts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApprovedShellScriptContentResponse createScript(@Valid @RequestBody ApprovedShellScriptRequest request)
            throws IOException {
        return catalogService.createScript(request);
    }

    @PutMapping("/shell-scripts/{fileName}")
    public ApprovedShellScriptContentResponse updateScript(@PathVariable String fileName,
                                                            @Valid @RequestBody ApprovedShellScriptRequest request)
            throws IOException {
        return catalogService.updateScript(fileName, request);
    }

    @DeleteMapping("/shell-scripts/{fileName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScript(@PathVariable String fileName) throws IOException {
        catalogService.deleteScript(fileName);
    }
}
