package com.scheduletl.service;

import com.scheduletl.domain.RegisteredShellScript;
import com.scheduletl.dto.RegisteredShellScriptResponse;
import com.scheduletl.dto.ShellScriptOptionResponse;
import com.scheduletl.repository.RegisteredShellScriptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Scripts referenced by absolute path instead of being copied into the approved directory. */
@Service
@RequiredArgsConstructor
public class RegisteredShellScriptService {

    private final RegisteredShellScriptRepository repository;
    private final FileBrowserService fileBrowser;
    private final ShellCatalogService catalogService;

    public List<RegisteredShellScriptResponse> list() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(RegisteredShellScript::getId))
                .map(this::toResponse)
                .toList();
    }

    public List<ShellScriptOptionResponse> listOptions() {
        return list().stream()
                .filter(script -> !script.type().isEmpty())
                .map(script -> new ShellScriptOptionResponse(script.id(), script.fileName(), script.type()))
                .toList();
    }

    public RegisteredShellScriptResponse register(String requestedId, String requestedPath) throws IOException {
        Path file = fileBrowser.requireAllowedFile(requestedPath);
        String fileName = file.getFileName().toString();
        if (!ShellCatalogService.FILE_NAME.matcher(fileName).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a script filename with a supported extension.");
        }
        ShellCatalogService.scriptType(fileName).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "The script extension is not supported on this backend operating system."));
        String id = requestedId == null || requestedId.isBlank() ? fileName : requestedId.trim();
        boolean idInUse = repository.existsById(id)
                || catalogService.listScripts().stream().anyMatch(script -> script.id().equals(id));
        if (idInUse) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A script with this ID already exists.");
        }
        if (repository.existsByPath(file.toString())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This script path is already registered.");
        }
        RegisteredShellScript script = new RegisteredShellScript();
        script.setId(id);
        script.setPath(file.toString());
        return toResponse(repository.save(script));
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registered script not found.");
        }
        repository.deleteById(id);
    }

    private RegisteredShellScriptResponse toResponse(RegisteredShellScript script) {
        String fileName = Path.of(script.getPath()).getFileName().toString();
        return new RegisteredShellScriptResponse(script.getId(), fileName,
                ShellCatalogService.scriptType(fileName).orElse(""), script.getPath());
    }
}
