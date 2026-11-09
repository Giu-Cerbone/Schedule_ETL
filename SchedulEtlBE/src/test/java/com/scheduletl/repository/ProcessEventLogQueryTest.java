package com.scheduletl.repository;

import com.scheduletl.domain.ProcessEventLog;
import com.scheduletl.dto.ProcessEventLogPageResponse;
import com.scheduletl.service.ProcessEventLogQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class ProcessEventLogQueryTest {

    @Autowired
    private ProcessEventLogRepository repository;

    @Test
    void filtersByLevelAndFreeText() {
        repository.save(entry("INFO", "Process started", null));
        repository.save(entry("ERROR", "Script failed", "Exception: boom"));
        var service = new ProcessEventLogQueryService(repository);

        ProcessEventLogPageResponse errors = service.search(filter(null, "ERROR"), 0, 50);
        ProcessEventLogPageResponse text = service.search(filter("BOOM", null), 0, 50);
        ProcessEventLogPageResponse all = service.search(filter(null, null), 0, 50);

        assertEquals(1, errors.totalElements());
        assertEquals(1, text.totalElements());
        assertEquals(2, all.totalElements());
    }

    private static ProcessEventLogQueryService.Filter filter(String text, String level) {
        return new ProcessEventLogQueryService.Filter(text, level, null, null, null, null, null, null, null);
    }

    private static ProcessEventLog entry(String level, String message, String details) {
        ProcessEventLog log = new ProcessEventLog();
        log.setLoggedAt(LocalDateTime.now());
        log.setLevel(level);
        log.setEventType("STEP");
        log.setMessage(message);
        log.setDetails(details);
        return log;
    }
}
