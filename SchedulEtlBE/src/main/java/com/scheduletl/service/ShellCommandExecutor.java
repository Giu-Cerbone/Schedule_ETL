package com.scheduletl.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduletl.domain.AuthorizedShellCommand;
import com.scheduletl.repository.AuthorizedShellCommandRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class ShellCommandExecutor implements ShellScriptExecutor {

    private static final Pattern SCRIPT_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private final ObjectMapper objectMapper;
    private final Path allowedDirectory;
    private final boolean enabled;
    private final Duration timeout;
    private final AuthorizedShellCommandRepository commandRepository;

    public ShellCommandExecutor(ObjectMapper objectMapper,
                                @Value("${scheduletl.process.shell.enabled:false}") boolean enabled,
                                @Value("${scheduletl.process.shell.allowed-directory:./approved-scripts}") String allowedDirectory,
                                @Value("${scheduletl.process.shell.timeout-seconds:30}") long timeoutSeconds,
                                AuthorizedShellCommandRepository commandRepository) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.allowedDirectory = Path.of(allowedDirectory).toAbsolutePath().normalize();
        this.commandRepository = commandRepository;
        if (timeoutSeconds < 1 || timeoutSeconds > 300) {
            throw new IllegalArgumentException("Shell process timeout must be between 1 and 300 seconds");
        }
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public synchronized ShellScriptExecutor.ExecutionResult execute(String configuration) throws IOException, InterruptedException {
        if (!enabled) {
            throw new IllegalStateException("Shell execution is disabled. Enable scheduletl.process.shell.enabled to use approved scripts.");
        }

        JsonNode target = readExecutionTarget(configuration);
        List<String> command;
        Path workingDirectory;
        if (target.has("script")) {
            String scriptName = readScriptName(target);
            Files.createDirectories(allowedDirectory);
            Path root = allowedDirectory.toRealPath();
            if (!Files.isDirectory(root)) {
                throw new IllegalStateException("The approved scripts directory is not available.");
            }

            Path script = root.resolve(scriptName).normalize();
            if (!script.getParent().equals(root)) {
                throw new IllegalArgumentException("Only a script file name from the approved scripts directory is allowed.");
            }
            Path realScript = script.toRealPath();
            if (!realScript.startsWith(root) || !Files.isRegularFile(realScript)) {
                throw new IllegalArgumentException("The selected script is not a regular file in the approved scripts directory.");
            }
            command = buildCommand(realScript);
            workingDirectory = root;
        } else {
            AuthorizedShellCommand definition = findConfiguredCommand(readCommandId(target));
            Path executable = Path.of(definition.getExecutable().trim());
            command = new ArrayList<>();
            if (ShellCatalogService.isBareCommandName(executable)) {
                String name = executable.toString();
                if (ShellCatalogService.CMD_BUILTINS.contains(name.toLowerCase())) {
                    if (!isWindows()) {
                        throw new IllegalStateException("The internal command '" + name + "' is only available on Windows.");
                    }
                    // Internal cmd.exe commands are not files
                    command.add("cmd.exe");
                    command.add("/d");
                    command.add("/c");
                } else if (!ShellCatalogService.resolveOnPath(name)) {
                    throw new IllegalStateException("The command '" + name + "' is not a known internal command and was not found in PATH.");
                }
                command.add(name);
            } else {
                if (!executable.isAbsolute() || !Files.isRegularFile(executable)) {
                    throw new IllegalStateException("The configured command executable is not an available absolute file path.");
                }
                command.add(executable.toString());
            }
            command.addAll(definition.getArguments());
            workingDirectory = allowedDirectory.toAbsolutePath().normalize();
        }

        ApprovedScriptRunnable task = new ApprovedScriptRunnable(command, workingDirectory, timeout);
        task.run();
        return task.result();
    }

    private JsonNode readExecutionTarget(String configuration) throws IOException {
        if (configuration == null || configuration.isBlank()) {
            throw new IllegalArgumentException("Shell process configuration must select an approved script or configured command.");
        }
        JsonNode root = objectMapper.readTree(configuration);
        if (root == null || !root.isObject() || root.size() != 1
                || !(root.has("script") || root.has("commandId"))
                || !(root.has("script") ? root.get("script").isTextual() : root.get("commandId").isTextual())) {
            throw new IllegalArgumentException("Shell process configuration must contain only a script file name or an approved command ID.");
        }
        return root;
    }

    private String readScriptName(JsonNode target) {
        String name = target.get("script").asText();
        if (!SCRIPT_NAME.matcher(name).matches() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Script must be a file name without a path.");
        }
        return name;
    }

    private String readCommandId(JsonNode target) {
        return target.get("commandId").asText();
    }

    private AuthorizedShellCommand findConfiguredCommand(String commandId) {
        return commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("The selected command is not in the server allowlist."));
    }

    private List<String> buildCommand(Path script) {
        String name = script.getFileName().toString().toLowerCase();
        List<String> command = new ArrayList<>();
        if (name.endsWith(".sh") && !isWindows()) {
            command.add("/bin/sh");
            command.add(script.toString());
        } else if ((name.endsWith(".cmd") || name.endsWith(".bat")) && isWindows()) {
            command.add("cmd.exe");
            command.add("/d");
            command.add("/s");
            command.add("/c");
            command.add("\"\"" + script + "\"\"");
        } else if (name.endsWith(".ps1") && isWindows()) {
            command.add("powershell.exe");
            command.add("-NoLogo");
            command.add("-NoProfile");
            command.add("-NonInteractive");
            command.add("-File");
            command.add(script.toString());
        } else {
            throw new IllegalArgumentException("Supported scripts are .sh on Unix, and .cmd, .bat or .ps1 on Windows.");
        }
        return command;
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

}
