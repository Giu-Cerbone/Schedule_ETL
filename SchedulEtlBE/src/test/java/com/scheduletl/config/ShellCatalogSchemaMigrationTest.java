package com.scheduletl.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellCatalogSchemaMigrationTest {

    @Test
    void migratesLegacyCatalogsAndProcessReferencesToGeneratedIds() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:shell-catalog-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE authorized_shell_commands (
                    id VARCHAR(128) PRIMARY KEY,
                    label VARCHAR(255) NOT NULL,
                    type VARCHAR(255) NOT NULL,
                    executable VARCHAR(2048) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE authorized_shell_command_arguments (
                    argument_order INTEGER NOT NULL,
                    command_id VARCHAR(128) NOT NULL,
                    argument_value VARCHAR(2048) NOT NULL,
                    PRIMARY KEY (argument_order, command_id),
                    FOREIGN KEY (command_id) REFERENCES authorized_shell_commands(id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE registered_shell_scripts (
                    file_name VARCHAR(128) PRIMARY KEY,
                    path VARCHAR(2048) NOT NULL UNIQUE
                )
                """);
        jdbc.execute("CREATE TABLE process_definitions (id BIGINT PRIMARY KEY, configuration VARCHAR(4000))");
        jdbc.update("INSERT INTO authorized_shell_commands VALUES (?, ?, ?, ?)",
                "legacy-export", "Export", "REPORT", "C:\\\\tools\\\\export.exe");
        jdbc.update("INSERT INTO authorized_shell_command_arguments VALUES (?, ?, ?)",
                0, "legacy-export", "daily");
        jdbc.update("INSERT INTO registered_shell_scripts VALUES (?, ?)",
                "legacy.sh", "C:\\\\approved\\\\legacy.sh");
        jdbc.update("INSERT INTO process_definitions VALUES (?, ?)",
                1L, "{\"commandId\":\"legacy-export\"}");
        jdbc.update("INSERT INTO process_definitions VALUES (?, ?)",
                2L, "{\"script\":\"legacy.sh\"}");

        new ShellCatalogSchemaMigration(jdbc, new ObjectMapper())
                .run(new org.springframework.boot.DefaultApplicationArguments(new String[0]));
        new ShellCatalogSchemaMigration(jdbc, new ObjectMapper())
                .run(new org.springframework.boot.DefaultApplicationArguments());

        Long commandId = jdbc.queryForObject("SELECT id FROM authorized_shell_commands", Long.class);
        Long scriptId = jdbc.queryForObject("SELECT id FROM registered_shell_scripts", Long.class);
        assertEquals("Export", jdbc.queryForObject("SELECT name FROM authorized_shell_commands WHERE id = ?",
                String.class, commandId));
        assertEquals("legacy.sh", jdbc.queryForObject("SELECT name FROM registered_shell_scripts WHERE id = ?",
                String.class, scriptId));
        assertEquals(commandId, jdbc.queryForObject(
                "SELECT command_id FROM authorized_shell_command_arguments", Long.class));
        assertEquals("{\"commandId\":" + commandId + "}",
                jdbc.queryForObject("SELECT configuration FROM process_definitions WHERE id = 1", String.class));
        assertEquals("{\"script\":\"" + scriptId + "\"}",
                jdbc.queryForObject("SELECT configuration FROM process_definitions WHERE id = 2", String.class));
    }

    @Test
    void repairsNumericCatalogIdsAndBackfillsMissingNames() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:shell-catalog-partial-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE authorized_shell_commands (
                    id BIGINT PRIMARY KEY,
                    label VARCHAR(255) NOT NULL,
                    type VARCHAR(255) NOT NULL,
                    executable VARCHAR(2048) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE registered_shell_scripts (
                    id BIGINT PRIMARY KEY,
                    path VARCHAR(2048) NOT NULL UNIQUE
                )
                """);
        jdbc.update("INSERT INTO authorized_shell_commands VALUES (?, ?, ?, ?)",
                17L, "Legacy export", "REPORT", "C:\\\\tools\\\\export.exe");
        jdbc.update("INSERT INTO registered_shell_scripts VALUES (?, ?)",
                22L, "C:\\\\approved\\\\daily.sh");

        new ShellCatalogSchemaMigration(jdbc, new ObjectMapper())
                .run(new org.springframework.boot.DefaultApplicationArguments());

        assertEquals("Legacy export", jdbc.queryForObject(
                "SELECT name FROM authorized_shell_commands WHERE id = 17", String.class));
        assertEquals("daily.sh", jdbc.queryForObject(
                "SELECT name FROM registered_shell_scripts WHERE id = 22", String.class));
        assertIdentityPrimaryKey(jdbc, "AUTHORIZED_SHELL_COMMANDS");
        assertIdentityPrimaryKey(jdbc, "REGISTERED_SHELL_SCRIPTS");

        jdbc.update("INSERT INTO authorized_shell_commands (name, type, executable) VALUES (?, ?, ?)",
                "New command", "REPORT", "C:\\\\tools\\\\new.exe");
        jdbc.update("INSERT INTO registered_shell_scripts (name, path) VALUES (?, ?)",
                "New script", "C:\\\\approved\\\\new.sh");
        assertTrue(jdbc.queryForObject(
                "SELECT id FROM authorized_shell_commands WHERE name = 'New command'", Long.class) > 17L);
        assertTrue(jdbc.queryForObject(
                "SELECT id FROM registered_shell_scripts WHERE name = 'New script'", Long.class) > 22L);
    }

    private void assertIdentityPrimaryKey(JdbcTemplate jdbc, String table) {
        assertEquals("YES", jdbc.queryForObject("""
                SELECT IS_IDENTITY FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = ? AND COLUMN_NAME = 'ID'
                """, String.class, table));
        assertTrue(jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = ? AND CONSTRAINT_TYPE = 'PRIMARY KEY'
                """, Integer.class, table) > 0);
    }
}
