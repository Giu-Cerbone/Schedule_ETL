package com.scheduletl.config;

import com.scheduletl.repository.ProcessDefinitionRepository;
import com.scheduletl.repository.ProcessTypeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ProcessTypeInitializationTest {

    @Autowired
    private ProcessTypeRepository processTypeRepository;

    @Autowired
    private ProcessDefinitionRepository processDefinitionRepository;

    @Test
    void initializesAllowedTypesAndRelatesSampleProcesses() {
        Set<String> typeCodes = processTypeRepository.findAll().stream()
                .map(com.scheduletl.domain.ProcessType::getCode)
                .collect(Collectors.toSet());

        assertEquals(Set.of("SQLDATABASE", "SHELL_CMD", "SCRIPT"), typeCodes);
        assertTrue(processDefinitionRepository.findAll().stream()
                .allMatch(process -> typeCodes.contains(process.getType().getCode())));
    }
}
