package com.scheduletl.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateUserGroupRequest(
        @NotBlank(message = "Group name is required") String name,
        String description
) {
}
