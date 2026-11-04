package com.scheduletl.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class ProcessDefinitionRepositoryTest {

    @Autowired
    private ProcessDefinitionRepository repository;

    @Test
    void supportsSchedulerAndParentLookups() {
        assertTrue(repository.findBySchedulerIdOrderByParentProcessIdAscSequenceOrderAscIdAsc(1L).isEmpty());
        assertTrue(repository.findByParentProcessIdOrderBySequenceOrderAscIdAsc(1L).isEmpty());
    }
}
