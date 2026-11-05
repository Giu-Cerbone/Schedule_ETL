package com.scheduletl.service;

import com.scheduletl.domain.RegisteredShellScript;
import com.scheduletl.dto.ApprovedShellScriptContentResponse;
import com.scheduletl.dto.ApprovedShellScriptRequest;
import com.scheduletl.dto.RegisteredShellScriptResponse;
import com.scheduletl.dto.ShellScriptOptionResponse;
import com.scheduletl.repository.RegisteredShellScriptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
                .map(script -> new ShellScriptOptionResponse(script.id(), script.name(), script.fileName(), script.type()))
                .toList();
    }

    public ApprovedShellScriptContentResponse getContent(Long id) throws IOException {
        Path file = registeredFile(id);
        String fileName = file.getFileName().toString();
        String type = ShellCatalogService.scriptType(fileName).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "The registered script type is not supported."));
        RegisteredShellScript script = repository.findById(id).orElseThrow();
        return new ApprovedShellScriptContentResponse(script.getName(), fileName, type, Files.readString(file));
    }

    public ApprovedShellScriptContentResponse updateContent(Long id, ApprovedShellScriptRequest request)
            throws IOException {
        Path file = registeredFile(id);
        String oldFileName = file.getFileName().toString();
        String oldType = ShellCatalogService.scriptType(oldFileName).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "The registered script type is not supported."));
        if (!ShellCatalogService.FILE_NAME.matcher(request.fileName()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a script filename with a supported extension.");
        }
        String type = ShellCatalogService.scriptType(request.fileName()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "The script extension is not supported on this backend operating system."));
        if (!oldType.equals(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Changing the script filename cannot change its type.");
        }
        RegisteredShellScript script = repository.findById(id).orElseThrow();
        Path target = file;
        if (request.path() != null && !request.path().isBlank()) {
            try {
                Path requestedPath = Path.of(request.path()).toAbsolutePath().normalize();
                target = requestedPath.equals(file.toAbsolutePath().normalize())
                        ? file
                        : fileBrowser.requireAllowedFile(request.path());
            } catch (java.nio.file.InvalidPathException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected script path is invalid.", exception);
            }
            String selectedFileName = target.getFileName().toString();
            if (!selectedFileName.equals(request.fileName())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The filename must match the selected script path.");
            }
            String selectedType = ShellCatalogService.scriptType(selectedFileName).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "The script extension is not supported on this backend operating system."));
            if (!oldType.equals(selectedType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Changing the script path cannot change its type.");
            }
            repository.findByPath(target.toString())
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "The selected script path is already registered.");
                    });
        } else {
            target = file.resolveSibling(request.fileName());
            if (!target.equals(file)) {
                if (Files.exists(target)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "A script with this filename already exists.");
                }
                try {
                    Files.move(file, target);
                } catch (FileAlreadyExistsException exception) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "A script with this filename already exists.", exception);
                }
            }
        }
        if (request.name() != null && !request.name().isBlank()) {
            script.setName(request.name().trim());
        }

        script.setPath(target.toRealPath().toString());

        if (request.content() != null && !request.content().isBlank()) {
            replaceContent(target, request.content());
        }
        repository.save(script);
        String fileName = target.getFileName().toString();
        String content = request.content() == null ? "" : request.content();
        return new ApprovedShellScriptContentResponse(script.getName(), fileName, type, content);
    }

    public RegisteredShellScriptResponse register(String requestedName, String requestedPath) throws IOException {
        Path file = fileBrowser.requireAllowedFile(requestedPath);
        String fileName = file.getFileName().toString();
        if (!ShellCatalogService.FILE_NAME.matcher(fileName).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a script filename with a supported extension.");
        }
        ShellCatalogService.scriptType(fileName).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "The script extension is not supported on this backend operating system."));
        RegisteredShellScript script = repository.findByPath(file.toString()).orElse(null);
        if (script != null) {
            script.setName(requestedName.trim());
            return toResponse(repository.save(script));
        }
        script = new RegisteredShellScript();
        script.setName(requestedName.trim());
        script.setPath(file.toString());
        return toResponse(repository.save(script));
    }

    public void delete(Long id) {
        RegisteredShellScript script = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registered script not found."));
        try {
            Path file = Path.of(script.getPath());
            if (Files.exists(file) && catalogService.isApprovedScriptPath(file)) {
                catalogService.deleteScript(Path.of(script.getPath()).getFileName().toString());
                return;
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cannot delete the approved script.", exception);
        }
        repository.deleteById(id);
    }

    private Path registeredFile(Long id) {
        RegisteredShellScript script = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registered script not found."));
        Path configuredPath = Path.of(script.getPath());
        Path file;
        try {
            file = catalogService.isApprovedScriptPath(configuredPath)
                    ? configuredPath.toRealPath()
                    : fileBrowser.requireAllowedFile(script.getPath());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cannot access the registered script.", exception);
        }
        String fileName = file.getFileName().toString();
        if (!ShellCatalogService.FILE_NAME.matcher(fileName).matches()
                || ShellCatalogService.scriptType(fileName).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The registered script type is not supported.");
        }
        return file;
    }

    private void replaceContent(Path file, String content) throws IOException {
        Path temporary = Files.createTempFile(file.getParent(), ".registered-script-", ".tmp");
        try {
            Files.writeString(temporary, ShellCatalogService.validateContent(content));
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private RegisteredShellScriptResponse toResponse(RegisteredShellScript script) {
        String fileName = Path.of(script.getPath()).getFileName().toString();
        return new RegisteredShellScriptResponse(script.getId(), script.getName(), fileName,
                ShellCatalogService.scriptType(fileName).orElse(""), script.getPath());
    }
}
