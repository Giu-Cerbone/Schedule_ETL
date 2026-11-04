package com.scheduletl.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ApprovedShellScriptRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,127}\\.(sh|cmd|bat|ps1)") String fileName,
        @NotBlank @Size(max = 262144) String content) {
}
