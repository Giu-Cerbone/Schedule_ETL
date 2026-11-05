package com.scheduletl.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class AdminSecurityConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminSecurityConfiguration.class);

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/shell-commands/**", "/api/v1/shell-scripts/**").permitAll()
                        .requestMatchers("/api/v1/shell-commands/**", "/api/v1/shell-scripts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/schedulers/*/processes/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/schedulers/*/processes/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/schedulers/*/processes/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/schedulers/*/processes/**").hasRole("ADMIN")
                        .anyRequest().permitAll()
                )
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService adminUserDetailsService(
            @Value("${scheduletl.admin.username}") String username,
            @Value("${scheduletl.admin.password}") String password,
            PasswordEncoder passwordEncoder) {
        if (password == null || password.isBlank()) {
            LOGGER.warn("SCHEDULETL_ADMIN_PASSWORD is not configured. The backend will start, but admin authentication is disabled.");
            return requestedUsername -> {
                throw new UsernameNotFoundException("Admin authentication is disabled. Configure SCHEDULETL_ADMIN_PASSWORD and restart the backend.");
            };
        }
        return new InMemoryUserDetailsManager(User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .roles("ADMIN")
                .build());
    }
}
