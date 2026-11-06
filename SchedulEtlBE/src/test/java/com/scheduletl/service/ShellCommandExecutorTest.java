package com.scheduletl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduletl.domain.AuthorizedShellCommand;
import com.scheduletl.domain.RegisteredShellScript;
import com.scheduletl.repository.AuthorizedShellCommandRepository;
import com.scheduletl.repository.RegisteredShellScriptRepository;
import com.scheduletl.repository.RegisteredShellScriptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ShellCommandExecutorTest {

    @TempDir
    Path approvedDirectory;

    @Test
    void rejectsPathsAndAcceptsOnlyAFileName() {
        ShellCommandExecutor executor = executor(true, 2);

        assertThrows(IllegalArgumentException.class, () -> executor.execute("{\"script\":\"../outside.sh\"}"));
        assertThrows(IllegalArgumentException.class, () -> executor.execute("{\"script\":\"/tmp/outside.sh\"}"));
        assertThrows(IllegalArgumentException.class, () -> executor.execute("{\"script\":\"test.sh\",\"command\":\"id\"}"));
        assertThrows(IllegalArgumentException.class, () -> executor.execute("{\"command\":\"id\"}"));
        assertThrows(IllegalArgumentException.class, () -> executor.execute("{\"commandId\":\"not-allowlisted\"}"));
    }

    @Test
    void doesNotRunScriptsWhenExecutionIsDisabled() {
        ShellCommandExecutor executor = executor(false, 2);

        assertThrows(IllegalStateException.class, () -> executor.execute("{\"script\":\"test.sh\"}"));
    }

    @Test
    void runsApprovedUnixScriptAndCapturesStandardOutputAndError() throws IOException, InterruptedException {
        assumeFalse(isWindows(), "Unix shell integration test");
        Files.writeString(approvedDirectory.resolve("approved.sh"), "printf 'stdout'; printf 'stderr' >&2\n");

        ShellScriptExecutor.ExecutionResult result = executor(true, 2).execute("{\"script\":\"approved.sh\"}");

        assertEquals(0, result.exitCode());
        assertFalse(result.timedOut());
        assertTrue(result.output().contains("stdout"));
        assertTrue(result.output().contains("stderr"));
    }

    @Test
    void listsOnlyScriptsCompatibleWithTheCurrentOperatingSystemAndShowsTheirType() throws IOException {
        Files.writeString(approvedDirectory.resolve("approved.sh"), "echo ok\n");
        Files.writeString(approvedDirectory.resolve("ignored.txt"), "not executable\n");
        Files.writeString(approvedDirectory.resolve("approved.cmd"), "@echo off\r\necho no\r\n");

        RegisteredShellScript unixScript = registeredScript(1L, approvedDirectory.resolve("approved.sh"));
        RegisteredShellScript windowsScript = registeredScript(2L, approvedDirectory.resolve("approved.cmd"));
        RegisteredShellScriptRepository scriptsRepository = mock(RegisteredShellScriptRepository.class);
        when(scriptsRepository.findAll()).thenReturn(List.of(unixScript, windowsScript));
        ShellCatalogService catalog = new ShellCatalogService(mock(AuthorizedShellCommandRepository.class),
                scriptsRepository);
        ReflectionTestUtils.setField(catalog, "allowedDirectory", approvedDirectory.toString());
        List<com.scheduletl.dto.ShellScriptOptionResponse> scripts = catalog.listScripts();

        assertEquals(isWindows() ? 1 : 1, scripts.size());
        assertEquals(isWindows() ? "approved.cmd" : "approved.sh", scripts.get(0).fileName());
        assertEquals(isWindows() ? "CMD" : "SH", scripts.get(0).type());
    }

    @Test
    void createsApprovedScriptsDirectoryWhenListingAnEmptyCatalog() throws IOException {
        Path missingDirectory = approvedDirectory.resolve("not-created-yet");
        ShellCatalogService catalog = new ShellCatalogService(mock(AuthorizedShellCommandRepository.class),
                mock(RegisteredShellScriptRepository.class));
        ReflectionTestUtils.setField(catalog, "allowedDirectory", missingDirectory.toString());

        assertTrue(catalog.listScripts().isEmpty());
        assertTrue(Files.isDirectory(missingDirectory));
    }

    @Test
    void runsApprovedWindowsCommandFileAndCapturesOutput() throws IOException, InterruptedException {
        assumeTrue(isWindows(), "Windows command integration test");
        Files.writeString(approvedDirectory.resolve("approved.cmd"),
                "@echo off\r\necho stdout\r\necho stderr 1>&2\r\nexit /b 0\r\n");

        ShellScriptExecutor.ExecutionResult result = executor(true, 2).execute("{\"script\":\"approved.cmd\"}");

        assertEquals(0, result.exitCode());
        assertFalse(result.timedOut());
        assertTrue(result.output().contains("stdout"));
        assertTrue(result.output().contains("stderr"));
    }

    @Test
    void runsAScriptRegisteredByAbsolutePathOutsideTheApprovedDirectory(@TempDir Path elsewhere)
            throws IOException, InterruptedException {
        assumeTrue(isWindows(), "Windows command integration test");
        Path script = elsewhere.resolve("external.cmd");
        Files.writeString(script, "@echo off\r\necho external\r\nexit /b 0\r\n");
        RegisteredShellScript registered = new RegisteredShellScript();
        registered.setId(1L);
        registered.setName("External");
        registered.setPath(script.toString());
        RegisteredShellScriptRepository registry = mock(RegisteredShellScriptRepository.class);
        when(registry.findById(1L)).thenReturn(Optional.of(registered));
        ShellCommandExecutor executor = new ShellCommandExecutor(new ObjectMapper(), true,
                approvedDirectory.toString(), 2, mock(AuthorizedShellCommandRepository.class), registry);

        ShellScriptExecutor.ExecutionResult result = executor.execute("{\"script\":\"1\"}");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("external"));
    }

    @Test
    void stopsAnApprovedScriptWhenItExceedsTheTimeout() throws IOException, InterruptedException {
        assumeFalse(isWindows(), "Unix shell integration test");
        Files.writeString(approvedDirectory.resolve("slow.sh"), "sleep 5\n");

        ShellScriptExecutor.ExecutionResult result = executor(true, 1).execute("{\"script\":\"slow.sh\"}");

        assertTrue(result.timedOut());
        assertEquals(-1, result.exitCode());
        assertTrue(result.output().contains("timed out"));
    }

    @Test
    void stopsOnlyTheFailedRunnableDescendants() throws IOException, InterruptedException {
        assumeFalse(isWindows(), "Unix shell integration test");
        Path pidFile = approvedDirectory.resolve("child.pid");
        Files.writeString(approvedDirectory.resolve("failed-with-child.sh"),
                "sleep 30 &\necho $! > \"" + pidFile + "\"\nsleep 0.3\nexit 1\n");

        ShellScriptExecutor.ExecutionResult result =
                executor(true, 3).execute("{\"script\":\"failed-with-child.sh\"}");

        assertEquals(1, result.exitCode());
        long childPid = Long.parseLong(Files.readString(pidFile).trim());
        ProcessHandle child = ProcessHandle.of(childPid).orElseThrow();
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (child.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertFalse(child.isAlive(), "The failed runnable's child process should be terminated");
    }

    @Test
    void runnableCanOnlyBeStartedOnce() throws IOException, InterruptedException {
        assumeFalse(isWindows(), "Unix shell integration test");
        Files.writeString(approvedDirectory.resolve("single-run.sh"), "printf 'ran'\n");
        Path script = approvedDirectory.resolve("single-run.sh").toRealPath();
        ApprovedScriptRunnable task = new ApprovedScriptRunnable(
                List.of("/bin/sh", script.toString()), approvedDirectory, java.time.Duration.ofSeconds(2));

        task.run();

        assertTrue(task.result().output().contains("ran"));
        assertThrows(IllegalStateException.class, task::run);
    }

    @Test
    void streamsOutputWhileTheApprovedScriptIsStillRunning() throws IOException, InterruptedException {
        assumeFalse(isWindows(), "Unix shell integration test");
        Files.writeString(approvedDirectory.resolve("streaming.sh"),
                "printf 'first line\\n'\nsleep 1\nprintf 'second line\\n'\n");
        List<String> updates = new CopyOnWriteArrayList<>();

        ShellScriptExecutor.ExecutionResult result =
                executor(true, 3).execute("{\"script\":\"streaming.sh\"}", updates::add);

        assertTrue(updates.stream().anyMatch(update -> update.contains("first line")
                && !update.contains("second line")));
        assertTrue(result.output().contains("first line"));
        assertTrue(result.output().contains("second line"));
    }

    @Test
    void runsOnlyAConfiguredCommandAndCapturesItsOutput() throws IOException, InterruptedException {
        assumeFalse(isWindows(), "Unix command integration test");
        Path executable = Path.of("/usr/bin/printf");
        assumeTrue(Files.isRegularFile(executable), "printf is not installed");
        AuthorizedShellCommand command = authorizedCommand(1L, "REPORT", executable,
                List.of("approved command output"));
        AuthorizedShellCommandRepository repository = repositoryFor(command);
        ShellCommandExecutor executor = executor(true, 2, repository);

        ShellScriptExecutor.ExecutionResult result = executor.execute("{\"commandId\":\"1\"}");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("approved command output"));
    }

    @Test
    void runsConfiguredWindowsCommandWithoutShellText() throws IOException, InterruptedException {
        assumeTrue(isWindows(), "Windows command integration test");
        String systemRoot = System.getenv("SystemRoot");
        assumeTrue(systemRoot != null, "SystemRoot is not configured");
        Path executable = Path.of(systemRoot, "System32", "hostname.exe");
        assumeTrue(Files.isRegularFile(executable), "hostname.exe is not installed");
        AuthorizedShellCommand command = authorizedCommand(1L, "SYSTEM", executable, List.of());
        ShellCommandExecutor executor = executor(true, 2, repositoryFor(command));

        ShellScriptExecutor.ExecutionResult result = executor.execute("{\"commandId\":1}");

        assertEquals(0, result.exitCode());
        assertFalse(result.output().isBlank());
    }

    private ShellCommandExecutor executor(boolean enabled, long timeoutSeconds) {
        return executor(enabled, timeoutSeconds, mock(AuthorizedShellCommandRepository.class));
    }

    private ShellCommandExecutor executor(boolean enabled, long timeoutSeconds,
                                          AuthorizedShellCommandRepository repository) {
        return new ShellCommandExecutor(new ObjectMapper(), enabled, approvedDirectory.toString(), timeoutSeconds,
                repository, mock(RegisteredShellScriptRepository.class));
    }

    private AuthorizedShellCommand authorizedCommand(Long id, String type, Path executable, List<String> arguments) {
        AuthorizedShellCommand command = new AuthorizedShellCommand();
        command.setId(id);
        command.setName("command-" + id);
        command.setType(type);
        command.setExecutable(executable.toString());
        command.setArguments(arguments);
        return command;
    }

    private RegisteredShellScript registeredScript(Long id, Path path) {
        RegisteredShellScript script = new RegisteredShellScript();
        script.setId(id);
        script.setName(path.getFileName().toString());
        script.setPath(path.toAbsolutePath().normalize().toString());
        return script;
    }

    private AuthorizedShellCommandRepository repositoryFor(AuthorizedShellCommand command) {
        AuthorizedShellCommandRepository repository = mock(AuthorizedShellCommandRepository.class);
        when(repository.findById(command.getId())).thenReturn(Optional.of(command));
        return repository;
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
