package com.scheduletl.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisteredShellScriptRequest(
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,127}|",
                message = "use only letters, numbers, dot, underscore and hyphen (no spaces), starting with a letter or number")
        String id,
        @NotBlank @Size(max = 2048) String path) {
}
