package com.scheduletl.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AuthorizedShellCommandRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 80) String type,
        @NotBlank @Size(max = 2048) String executable,
        @Size(max = 32) List<@Size(max = 2048) String> arguments) {
}
