package com.scheduletl.repository;

import com.scheduletl.domain.ProcessDefinition;
import com.scheduletl.domain.ProcessScheduleDay;
import com.scheduletl.domain.ProcessStartSchedule;
import com.scheduletl.domain.ProcessType;
import com.scheduletl.domain.Scheduler;
import com.scheduletl.domain.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class ProcessDefinitionRepositoryTest {

    @Autowired
    private ProcessStartScheduleRepository scheduleRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistsAndClaimsRecurringProcessStartSlotOncePerOccurrence() {
        ProcessType type = new ProcessType();
        type.setCode("TEST");
        type.setDescription("Test process");
        entityManager.persist(type);

        UserProfile owner = new UserProfile();
        owner.setUsername("scheduler-test");
        owner.setFullName("Scheduler Test");
        owner.setEmail("scheduler-test@example.test");
        entityManager.persist(owner);

        Scheduler scheduler = new Scheduler();
        scheduler.setName("Test scheduler");
        scheduler.setCronExpression("0 0 2 * * ?");
        scheduler.setOwnerProfile(owner);
        entityManager.persist(scheduler);

        ProcessDefinition definition = new ProcessDefinition();
        definition.setName("Test process");
        definition.setType(type);
        definition.setScheduler(scheduler);
        entityManager.persist(definition);

        ProcessStartSchedule slot = new ProcessStartSchedule();
        slot.setProcessDefinition(definition);
        slot.setDayOfWeek(ProcessScheduleDay.MONDAY);
        slot.setStartTime(LocalTime.of(8, 30));
        entityManager.persistAndFlush(slot);

        assertEquals(1, scheduleRepository.findAll().size());
        LocalDateTime occurrence = LocalDateTime.of(2026, 12, 7, 8, 30);
        assertEquals(1, scheduleRepository.claimScheduledStart(slot.getId(), occurrence));
        assertEquals(0, scheduleRepository.claimScheduledStart(slot.getId(), occurrence));
        assertEquals(1, scheduleRepository.claimScheduledStart(slot.getId(), occurrence.plusDays(7)));
    }
}
