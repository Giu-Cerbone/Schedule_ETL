package com.scheduletl.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Creates the H2 data folder and the DDL export folder before the datasource and Hibernate start.
 */
public class DatabaseDirectoriesInitializer implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        create(environment.getProperty("scheduletl.database.directory"));
        create(environment.getProperty("scheduletl.database.ddl-directory"));
    }

    private void create(String directory) {
        if (directory == null || directory.isBlank()) {
            return;
        }
        try {
            Files.createDirectories(Path.of(directory));
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot create directory " + directory, exception);
        }
    }
}
