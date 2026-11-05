package com.scheduletl.service;

import com.scheduletl.domain.RegisteredShellScript;
import com.scheduletl.dto.ApprovedShellScriptContentResponse;
import com.scheduletl.dto.ApprovedShellScriptRequest;
import com.scheduletl.repository.RegisteredShellScriptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisteredShellScriptServiceTest {

    @TempDir
    Path scriptsDirectory;

    @Test
    void readsAndUpdatesContentForARegisteredScript() throws IOException {
        String fileName = System.getProperty("os.name").toLowerCase().contains("win")
                ? "report.cmd"
                : "report.sh";
        Path file = scriptsDirectory.resolve(fileName);
        Files.writeString(file, "old content");

        RegisteredShellScript registeredScript = new RegisteredShellScript();
        registeredScript.setId(1L);
        registeredScript.setName("Daily report");
        registeredScript.setPath(file.toRealPath().toString());

        RegisteredShellScriptRepository repository = mock(RegisteredShellScriptRepository.class);
        when(repository.findById(registeredScript.getId())).thenReturn(Optional.of(registeredScript));
        RegisteredShellScriptService service = new RegisteredShellScriptService(
                repository,
                new FileBrowserService(List.of(scriptsDirectory.toRealPath().toString())),
                mock(ShellCatalogService.class));

        ApprovedShellScriptContentResponse current = service.getContent(registeredScript.getId());
        assertEquals("Daily report", current.name());
        assertEquals(fileName, current.fileName());
        assertEquals("old content", current.content());

        ApprovedShellScriptContentResponse updated = service.updateContent(
                registeredScript.getId(),
                new ApprovedShellScriptRequest("Daily report updated", fileName, null, "updated content"));

        assertEquals("Daily report updated", updated.name());
        assertEquals("updated content", updated.content());
        assertEquals("updated content", Files.readString(file));
    }

    @Test
    void renamesRegisteredScriptWithoutRequiringItsContent() throws IOException {
        String oldFileName = System.getProperty("os.name").toLowerCase().contains("win")
                ? "report.cmd"
                : "report.sh";
        String newFileName = System.getProperty("os.name").toLowerCase().contains("win")
                ? "renamed-report.cmd"
                : "renamed-report.sh";
        Path oldFile = scriptsDirectory.resolve(oldFileName);
        Path newFile = scriptsDirectory.resolve(newFileName);
        Files.writeString(oldFile, "preserve this content");

        RegisteredShellScript registeredScript = new RegisteredShellScript();
        registeredScript.setId(3L);
        registeredScript.setName("Daily report");
        registeredScript.setPath(oldFile.toRealPath().toString());
        RegisteredShellScriptRepository repository = mock(RegisteredShellScriptRepository.class);
        when(repository.findById(3L)).thenReturn(Optional.of(registeredScript));
        RegisteredShellScriptService service = new RegisteredShellScriptService(
                repository,
                new FileBrowserService(List.of(scriptsDirectory.toRealPath().toString())),
                mock(ShellCatalogService.class));

        ApprovedShellScriptContentResponse response = service.updateContent(
                3L, new ApprovedShellScriptRequest("Renamed report", newFileName, null, null));

        assertEquals(newFileName, response.fileName());
        assertEquals("Renamed report", response.name());
        assertEquals(newFile.toRealPath().toString(), registeredScript.getPath());
        assertFalse(Files.exists(oldFile));
        assertEquals("preserve this content", Files.readString(newFile));
        verify(repository).save(registeredScript);
    }

    @Test
    void updatesRegisteredPathToTheFileSelectedFromTheBrowser() throws IOException {
        String extension = System.getProperty("os.name").toLowerCase().contains("win") ? ".cmd" : ".sh";
        Path oldFile = scriptsDirectory.resolve("old" + extension);
        Path selectedFile = scriptsDirectory.resolve("selected" + extension);
        Files.writeString(oldFile, "old");
        Files.writeString(selectedFile, "selected");

        RegisteredShellScript registeredScript = new RegisteredShellScript();
        registeredScript.setId(4L);
        registeredScript.setName("Original");
        registeredScript.setPath(oldFile.toRealPath().toString());
        RegisteredShellScriptRepository repository = mock(RegisteredShellScriptRepository.class);
        when(repository.findById(4L)).thenReturn(Optional.of(registeredScript));
        RegisteredShellScriptService service = new RegisteredShellScriptService(
                repository,
                new FileBrowserService(List.of(scriptsDirectory.toRealPath().toString())),
                mock(ShellCatalogService.class));

        ApprovedShellScriptContentResponse response = service.updateContent(4L,
                new ApprovedShellScriptRequest("Selected file", selectedFile.getFileName().toString(),
                        selectedFile.toRealPath().toString(), null));

        assertEquals(selectedFile.toRealPath().toString(), registeredScript.getPath());
        assertEquals("selected", Files.readString(selectedFile));
        assertEquals(selectedFile.getFileName().toString(), response.fileName());
        verify(repository).save(registeredScript);
    }
}
