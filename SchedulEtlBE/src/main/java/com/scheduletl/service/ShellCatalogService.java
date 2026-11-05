package com.scheduletl.service;

import com.scheduletl.domain.AuthorizedShellCommand;
import com.scheduletl.dto.ApprovedShellScriptContentResponse;
import com.scheduletl.dto.ApprovedShellScriptRequest;
import com.scheduletl.dto.AuthorizedShellCommandRequest;
import com.scheduletl.dto.AuthorizedShellCommandResponse;
import com.scheduletl.dto.ShellCommandOptionResponse;
import com.scheduletl.dto.ShellScriptOptionResponse;
import com.scheduletl.repository.AuthorizedShellCommandRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ShellCatalogService {

    static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}\\.(sh|cmd|bat|ps1)");

    private final AuthorizedShellCommandRepository commandRepository;
    @Value("${scheduletl.process.shell.allowed-directory:./approved-scripts}")
    private String allowedDirectory;

    @Transactional(readOnly = true)
    public List<ShellCommandOptionResponse> listCommandOptions() {
        return commandRepository.findAll().stream()
                .sorted(Comparator.comparing(AuthorizedShellCommand::getId))
                .map(command -> new ShellCommandOptionResponse(command.getId(), command.getLabel(), command.getType()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuthorizedShellCommandResponse> listCommands() {
        return commandRepository.findAll().stream()
                .sorted(Comparator.comparing(AuthorizedShellCommand::getId))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AuthorizedShellCommandResponse createCommand(@Valid AuthorizedShellCommandRequest request) {
        if (commandRepository.existsById(request.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An authorized command with this ID already exists.");
        }
        return toResponse(commandRepository.save(toEntity(request)));
    }

    @Transactional
    public AuthorizedShellCommandResponse updateCommand(String id, @Valid AuthorizedShellCommandRequest request) {
        if (!id.equals(request.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The command ID cannot be changed.");
        }
        if (!commandRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Authorized command not found: " + id);
        }
        return toResponse(commandRepository.save(toEntity(request)));
    }

    @Transactional
    public void deleteCommand(String id) {
        if (!commandRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Authorized command not found: " + id);
        }
        commandRepository.deleteById(id);
    }

    public List<ShellScriptOptionResponse> listScripts() throws IOException {
        Path root = scriptsDirectory();
        try (Stream<Path> entries = Files.list(root)) {
            return entries
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> FILE_NAME.matcher(name).matches())
                    .map(name -> scriptType(name).map(type -> new ShellScriptOptionResponse(name, name, type)).orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(ShellScriptOptionResponse::fileName))
                    .toList();
        }
    }

    public ApprovedShellScriptContentResponse getScript(String fileName) throws IOException {
        String type = validateFileName(fileName);
        Path script = scriptsDirectory().resolve(fileName);
        if (!Files.isRegularFile(script, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Approved script not found.");
        }
        return new ApprovedShellScriptContentResponse(fileName, type, Files.readString(script));
    }

    public ApprovedShellScriptContentResponse createScript(@Valid ApprovedShellScriptRequest request) throws IOException {
        String type = validateFileName(request.fileName());
        Path script = scriptsDirectory().resolve(request.fileName());
        if (Files.exists(script, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An approved script with this name already exists.");
        }
        Files.writeString(script, validateContent(request.content()), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return new ApprovedShellScriptContentResponse(request.fileName(), type, request.content());
    }

    public ApprovedShellScriptContentResponse updateScript(String fileName, @Valid ApprovedShellScriptRequest request)
            throws IOException {
        String type = validateFileName(fileName);
        if (!fileName.equals(request.fileName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The script file name cannot be changed.");
        }
        Path root = scriptsDirectory();
        Path script = root.resolve(fileName);
        if (!Files.isRegularFile(script, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Approved script not found.");
        }
        Path temporary = Files.createTempFile(root, ".approved-script-", ".tmp");
        try {
            Files.writeString(temporary, validateContent(request.content()));
            try {
                Files.move(temporary, script, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, script, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return new ApprovedShellScriptContentResponse(fileName, type, request.content());
    }

    public void deleteScript(String fileName) throws IOException {
        validateFileName(fileName);
        Path script = scriptsDirectory().resolve(fileName);
        if (!Files.isRegularFile(script, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Approved script not found.");
        }
        Files.delete(script);
    }

    private Path scriptsDirectory() throws IOException {
        Path configuredPath = Path.of(allowedDirectory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(configuredPath);
            if (!Files.isDirectory(configuredPath)) {
                throw new IOException("Configured path is not a directory.");
            }
            return configuredPath.toRealPath();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Cannot access the approved scripts directory: " + configuredPath, exception);
        }
    }

    private String validateFileName(String fileName) {
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a script filename with a supported extension.");
        }
        return scriptType(fileName).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "The script extension is not supported on this backend operating system."));
    }

    static java.util.Optional<String> scriptType(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        if (!windows && name.endsWith(".sh")) return java.util.Optional.of("SH");
        if (windows && name.endsWith(".cmd")) return java.util.Optional.of("CMD");
        if (windows && name.endsWith(".bat")) return java.util.Optional.of("BAT");
        if (windows && name.endsWith(".ps1")) return java.util.Optional.of("POWERSHELL");
        return java.util.Optional.empty();
    }

    private String validateContent(String content) {
        if (content == null || content.isBlank() || content.indexOf('\0') >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Script content must be non-empty text without NUL characters.");
        }
        if (content.length() > 262144) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Script content exceeds 256 KiB.");
        }
        return content;
    }

    private AuthorizedShellCommandRequest validateExecutable(AuthorizedShellCommandRequest request) {
        Path executable;
        try {
            executable = Path.of(request.executable());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Executable path is invalid.");
        }
        if (isBareCommandName(executable)) {
            if (!CMD_BUILTINS.contains(executable.toString().toLowerCase()) && !resolveOnPath(executable.toString())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Command was not found in PATH and is not a known shell builtin.");
            }
            return request;
        }
        if (!executable.isAbsolute()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Executable must be an absolute path or a bare command name.");
        }
        if (!Files.isRegularFile(executable)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Executable must reference an existing file.");
        }
        return request;
    }

    static final java.util.Set<String> CMD_BUILTINS = java.util.Set.of(
            "dir", "cls", "echo", "type", "copy", "move", "del", "ver", "set", "cd", "md", "mkdir", "rd", "rmdir",
            "ren", "rename", "date", "time", "vol", "title");

    static boolean isBareCommandName(Path path) {
        return !path.isAbsolute() && path.getNameCount() == 1 && path.getParent() == null
                && !path.toString().contains("/") && !path.toString().contains("\\");
    }

    static boolean resolveOnPath(String name) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return false;
        List<String> candidates = new java.util.ArrayList<>(List.of(name));
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            String pathExt = System.getenv().getOrDefault("PATHEXT", ".COM;.EXE;.BAT;.CMD");
            for (String ext : pathExt.split(";")) candidates.add(name + ext);
        }
        for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) continue;
            for (String candidate : candidates) {
                try {
                    if (Files.isRegularFile(Path.of(dir, candidate))) return true;
                } catch (RuntimeException ignored) {
                    // invalid PATH entry
                }
            }
        }
        return false;
    }

    private AuthorizedShellCommand toEntity(AuthorizedShellCommandRequest request) {
        validateExecutable(request);
        AuthorizedShellCommand command = new AuthorizedShellCommand();
        command.setId(request.id());
        command.setLabel(request.label().trim());
        command.setType(request.type().trim());
        command.setExecutable(Path.of(request.executable()).normalize().toString());
        command.setArguments(request.arguments() == null ? List.of() : List.copyOf(request.arguments()));
        return command;
    }

    private AuthorizedShellCommandResponse toResponse(AuthorizedShellCommand command) {
        return new AuthorizedShellCommandResponse(command.getId(), command.getLabel(), command.getType(),
                command.getExecutable(), command.getArguments());
    }
}
