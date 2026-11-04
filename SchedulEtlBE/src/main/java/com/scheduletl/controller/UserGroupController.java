package com.scheduletl.controller;

import com.scheduletl.dto.CreateUserGroupRequest;
import com.scheduletl.dto.UserGroupResponse;
import com.scheduletl.service.UserGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/groups")
@Tag(name = "User Groups", description = "Managing user groups")
@RequiredArgsConstructor
public class UserGroupController {

    private final UserGroupService userGroupService;

    @GetMapping
    @Operation(summary = "List all user groups")
    public ResponseEntity<List<UserGroupResponse>> findAll() {
        return ResponseEntity.ok(userGroupService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user group by id")
    public ResponseEntity<UserGroupResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(userGroupService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Create a new user group")
    public ResponseEntity<UserGroupResponse> create(@Valid @RequestBody CreateUserGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userGroupService.create(request));
    }
}
