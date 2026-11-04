package com.scheduletl.service;

import java.io.IOException;
import java.util.List;

public interface ShellScriptExecutor {

    ExecutionResult execute(String configuration) throws IOException, InterruptedException;

    record ExecutionResult(int exitCode, String output, boolean timedOut) {
    }
}
