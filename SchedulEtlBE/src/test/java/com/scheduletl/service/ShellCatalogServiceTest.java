package com.scheduletl.service;

import com.scheduletl.domain.AuthorizedShellCommand;
import com.scheduletl.dto.AuthorizedShellCommandRequest;
import com.scheduletl.dto.AuthorizedShellCommandResponse;
import com.scheduletl.dto.ShellCommandOptionResponse;
import com.scheduletl.repository.AuthorizedShellCommandRepository;
import com.scheduletl.repository.RegisteredShellScriptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShellCatalogServiceTest {

    @TempDir
    Path directory;

    @Test
    void updatesCommandWithoutReplacingItsGeneratedId() throws Exception {
        Path executable = Files.createFile(directory.resolve("tool.exe"));
        AuthorizedShellCommand existing = new AuthorizedShellCommand();
        existing.setId(7L);
        AuthorizedShellCommandRepository commands = mock(AuthorizedShellCommandRepository.class);
        when(commands.findById(7L)).thenReturn(Optional.of(existing));
        when(commands.save(existing)).thenReturn(existing);
        ShellCatalogService service = new ShellCatalogService(commands, mock(RegisteredShellScriptRepository.class));

        AuthorizedShellCommandResponse response = service.updateCommand(7L,
                new AuthorizedShellCommandRequest("Updated tool", "REPORT", executable.toString(), List.of("daily")));

        assertEquals(7L, response.id());
        assertEquals("Updated tool", response.name());
        verify(commands).save(existing);
    }

    @Test
    void includesExecutableAndArgumentsInCommandOptions() {
        AuthorizedShellCommand command = new AuthorizedShellCommand();
        command.setId(3L);
        command.setName("Print status");
        command.setType("REPORT");
        command.setExecutable("echo");
        command.setArguments(List.of("status"));
        AuthorizedShellCommandRepository commands = mock(AuthorizedShellCommandRepository.class);
        when(commands.findAll()).thenReturn(List.of(command));
        ShellCatalogService service = new ShellCatalogService(commands, mock(RegisteredShellScriptRepository.class));

        List<ShellCommandOptionResponse> options = service.listCommandOptions();

        assertEquals(1, options.size());
        assertEquals("echo", options.getFirst().executable());
        assertEquals(List.of("status"), options.getFirst().arguments());
    }
}
