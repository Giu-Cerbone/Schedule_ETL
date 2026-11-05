package com.scheduletl.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisteredShellScriptRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 2048) String path) {
}
