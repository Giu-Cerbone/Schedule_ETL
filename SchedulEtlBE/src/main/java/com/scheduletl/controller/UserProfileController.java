package com.scheduletl.controller;

import com.scheduletl.dto.CreateUserProfileRequest;
import com.scheduletl.dto.UserProfileResponse;
import com.scheduletl.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/profiles")
@Tag(name = "User Profiles", description = "Managing user profiles")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    @Operation(summary = "List all user profiles")
    public ResponseEntity<List<UserProfileResponse>> findAll() {
        return ResponseEntity.ok(userProfileService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user profile by id")
    public ResponseEntity<UserProfileResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(userProfileService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Create a new user profile")
    public ResponseEntity<UserProfileResponse> create(@Valid @RequestBody CreateUserProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userProfileService.create(request));
    }
}
