package com.scheduletl.service;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public interface ShellScriptExecutor {

    ExecutionResult execute(String configuration) throws IOException, InterruptedException;

    default ExecutionResult execute(String configuration, Consumer<String> outputListener)
            throws IOException, InterruptedException {
        return execute(configuration);
    }

    record ExecutionResult(int exitCode, String output, boolean timedOut) {
    }
}
