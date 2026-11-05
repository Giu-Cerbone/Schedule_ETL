package com.scheduletl.service;

import com.scheduletl.dto.FileBrowseResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Lists server-side folders and supported script files so an administrator can pick an absolute path. */
@Service
public class FileBrowserService {

    private final List<Path> roots;

    public FileBrowserService(@Value("${scheduletl.process.shell.browse-roots:}") List<String> configuredRoots) {
        this.roots = configuredRoots.stream()
                .filter(root -> !root.isBlank())
                .map(root -> Path.of(root.trim()).toAbsolutePath().normalize())
                .toList();
    }

    public FileBrowseResponse browse(String requestedPath) {
        if (requestedPath == null || requestedPath.isBlank()) {
            return new FileBrowseResponse("", null, rootEntries());
        }
        Path directory = parse(requestedPath);
        ensureAllowed(directory);
        if (!Files.isDirectory(directory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected path is not a directory.");
        }
        List<FileBrowseResponse.Entry> entries;
        try (Stream<Path> children = Files.list(directory)) {
            entries = children
                    .map(child -> toEntry(child))
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing((FileBrowseResponse.Entry entry) -> !entry.directory())
                            .thenComparing(entry -> entry.name().toLowerCase()))
                    .toList();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot read the selected directory.", exception);
        }
        Path parent = directory.getParent();
        String parentPath = parent != null && isAllowed(parent) ? parent.toString() : "";
        return new FileBrowseResponse(directory.toString(), parentPath, entries);
    }

    /** Validates that a file path may be registered and returns its normalized real path. */
    Path requireAllowedFile(String requestedPath) {
        Path file = parse(requestedPath);
        ensureAllowed(file);
        try {
            Path real = file.toRealPath();
            ensureAllowed(real);
            if (!Files.isRegularFile(real)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected path is not a file.");
            }
            return real;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected file does not exist.", exception);
        }
    }

    private List<FileBrowseResponse.Entry> rootEntries() {
        List<Path> base = roots.isEmpty()
                ? Arrays.stream(File.listRoots()).map(File::toPath).toList()
                : roots;
        return base.stream()
                .map(root -> new FileBrowseResponse.Entry(root.toString(), root.toString(), true))
                .toList();
    }

    private FileBrowseResponse.Entry toEntry(Path child) {
        try {
            Path name = child.getFileName();
            if (name == null) return null;
            if (Files.isDirectory(child)) {
                return new FileBrowseResponse.Entry(name.toString(), child.toString(), true);
            }
            if (Files.isRegularFile(child) && ShellCatalogService.scriptType(name.toString()).isPresent()) {
                return new FileBrowseResponse.Entry(name.toString(), child.toString(), false);
            }
        } catch (SecurityException ignored) {
            // unreadable entry
        }
        return null;
    }

    private Path parse(String value) {
        Path path;
        try {
            path = Path.of(value.trim());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The path is invalid.");
        }
        if (!path.isAbsolute()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The path must be absolute.");
        }
        return path.normalize();
    }

    private void ensureAllowed(Path path) {
        if (!isAllowed(path)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The path is outside the configured browse roots.");
        }
    }

    private boolean isAllowed(Path path) {
        return roots.isEmpty() || roots.stream().anyMatch(path::startsWith);
    }
}
