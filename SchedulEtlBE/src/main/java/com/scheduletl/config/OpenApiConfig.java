package com.scheduletl.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SchedulETL API")
                        .version("1.0.0")
                        .description("API per la gestione di scheduler, schedulazione multipla e controlli runtime dei processi"));
    }
}
