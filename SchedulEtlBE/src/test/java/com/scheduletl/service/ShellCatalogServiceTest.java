package com.scheduletl.service;

import com.scheduletl.domain.AuthorizedShellCommand;
import com.scheduletl.dto.AuthorizedShellCommandRequest;
import com.scheduletl.dto.AuthorizedShellCommandResponse;
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
}
