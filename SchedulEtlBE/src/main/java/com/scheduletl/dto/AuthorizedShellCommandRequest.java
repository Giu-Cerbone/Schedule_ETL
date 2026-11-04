package com.scheduletl.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AuthorizedShellCommandRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,127}") String id,
        @NotBlank @Size(max = 200) String label,
        @NotBlank @Size(max = 80) String type,
        @NotBlank @Size(max = 2048) String executable,
        @Size(max = 32) List<@Size(max = 2048) String> arguments) {
}
