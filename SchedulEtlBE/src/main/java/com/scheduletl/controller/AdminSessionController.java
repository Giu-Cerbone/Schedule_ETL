package com.scheduletl.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminSessionController {

    @GetMapping("/session")
    public AdminSessionResponse currentSession(Authentication authentication) {
        return new AdminSessionResponse(authentication.getName());
    }

    public record AdminSessionResponse(String username) {
    }
}
