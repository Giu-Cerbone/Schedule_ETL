package com.scheduletl.dto;

public record UserProfileResponse(
        Long id,
        String username,
        String fullName,
        String email
) {
}
