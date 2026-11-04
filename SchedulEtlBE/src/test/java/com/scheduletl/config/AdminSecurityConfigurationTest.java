package com.scheduletl.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@AutoConfigureMockMvc
class AdminSecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void disablesAdminAuthenticationWithoutBlockingBackendStartup() {
        AdminSecurityConfiguration configuration = new AdminSecurityConfiguration();
        var userDetailsService = configuration.adminUserDetailsService("admin", "", configuration.passwordEncoder());

        assertThrows(UsernameNotFoundException.class, () -> userDetailsService.loadUserByUsername("admin"));
    }

    @Test
    void keepsPublicCatalogReadsAndRequiresAdminForCatalogManagement() throws Exception {
        mockMvc.perform(get("/api/v1/shell-commands"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/session"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/shell-commands"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/session")
                        .header("Authorization", basicCredentials("test-admin", "test-admin-password")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/shell-commands")
                        .header("Authorization", basicCredentials("test-admin", "test-admin-password")))
                .andExpect(status().isOk());
    }

    @Test
    void requiresAdminAuthenticationToCreateOrRunProcesses() throws Exception {
        mockMvc.perform(post("/api/v1/schedulers/1/processes"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/schedulers/1/processes/1/start"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/schedulers/1/processes")
                        .header("Authorization", basicCredentials("test-admin", "test-admin-password")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void explainsInvalidAuthorizedCommandExecutable() throws Exception {
        mockMvc.perform(post("/api/v1/admin/shell-commands")
                        .header("Authorization", basicCredentials("test-admin", "test-admin-password"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "id": "test-command",
                                  "label": "Test command",
                                  "type": "TEST",
                                  "executable": "python",
                                  "arguments": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"status":400,"message":"Executable must be an absolute path."}
                        """, false));
    }

    private String basicCredentials(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
