package com.scheduletl.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class ApprovedScriptRunnable implements Runnable {

    private static final int MAX_OUTPUT_CHARACTERS = 7_500;

    private final List<String> command;
    private final Path workingDirectory;
    private final Duration timeout;
    private volatile ShellScriptExecutor.ExecutionResult result;
    private volatile Exception failure;
    private boolean started;

    ApprovedScriptRunnable(List<String> command, Path workingDirectory, Duration timeout) {
        this.command = List.copyOf(command);
        this.workingDirectory = workingDirectory;
        this.timeout = timeout;
    }

    @Override
    public synchronized void run() {
        if (started) {
            throw new IllegalStateException("An approved script task can only run once.");
        }
        started = true;

        Process process;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true);
            configureEnvironment(processBuilder.environment());
            process = processBuilder.start();
        } catch (IOException exception) {
            failure = exception;
            return;
        }

        CompletableFuture<CapturedOutput> outputFuture =
                CompletableFuture.supplyAsync(() -> readOutput(process.getInputStream()));
        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                terminate(process);
            }
        } catch (InterruptedException exception) {
            terminateAfterInterruption(process);
            Thread.currentThread().interrupt();
            failure = exception;
            return;
        }

        CapturedOutput output;
        try {
            output = outputFuture.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            outputFuture.cancel(true);
            terminateAfterInterruption(process);
            Thread.currentThread().interrupt();
            failure = exception;
            return;
        } catch (TimeoutException exception) {
            outputFuture.cancel(true);
            output = new CapturedOutput("[Output collection timed out]", true);
        } catch (ExecutionException exception) {
            failure = new IOException("Unable to collect approved script output", exception.getCause());
            return;
        }

        String text = output.text();
        if (output.truncated()) {
            text += System.lineSeparator() + "[Output truncated: maximum size reached]";
        }
        if (!finished) {
            text += System.lineSeparator() + "[Execution timed out after " + timeout.toSeconds() + " seconds]";
        }
        result = new ShellScriptExecutor.ExecutionResult(finished ? process.exitValue() : -1, text, !finished);
    }

    ShellScriptExecutor.ExecutionResult result() throws IOException, InterruptedException {
        if (failure instanceof InterruptedException) {
            throw (InterruptedException) failure;
        }
        if (failure instanceof IOException) {
            throw (IOException) failure;
        }
        if (failure != null) {
            throw new IllegalStateException("Approved script execution failed.", failure);
        }
        if (result == null) {
            throw new IllegalStateException("Approved script task has not completed.");
        }
        return result;
    }

    private CapturedOutput readOutput(InputStream input) {
        StringBuilder output = new StringBuilder();
        boolean truncated = false;
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            char[] buffer = new char[1024];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                int remaining = MAX_OUTPUT_CHARACTERS - output.length();
                if (remaining > 0) {
                    if (count > remaining) {
                        output.append(buffer, 0, remaining);
                        truncated = true;
                    } else {
                        output.append(buffer, 0, count);
                    }
                } else {
                    truncated = true;
                }
            }
        } catch (IOException exception) {
            return new CapturedOutput(output + System.lineSeparator() + "[Unable to read process output]", true);
        }
        return new CapturedOutput(output.toString(), truncated);
    }

    private void terminate(Process process) throws InterruptedException {
        process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroy();
        if (!process.waitFor(1, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(1, TimeUnit.SECONDS);
        }
    }

    private void terminateAfterInterruption(Process process) {
        process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private void configureEnvironment(java.util.Map<String, String> environment) {
        environment.clear();
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            String systemRoot = System.getenv("SystemRoot");
            if (systemRoot == null || systemRoot.isBlank()) {
                throw new IllegalStateException("Windows SystemRoot is not configured.");
            }
            environment.put("SystemRoot", systemRoot);
            environment.put("WINDIR", systemRoot);
            environment.put("PATH", Path.of(systemRoot, "System32")
                    + System.getProperty("path.separator")
                    + Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0"));
        } else {
            environment.put("PATH", "/usr/bin:/bin");
            environment.put("HOME", workingDirectory.toString());
        }
    }

    private record CapturedOutput(String text, boolean truncated) {
    }
}
