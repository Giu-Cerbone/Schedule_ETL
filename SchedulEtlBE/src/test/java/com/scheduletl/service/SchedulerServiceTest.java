package com.scheduletl.service;

import com.scheduletl.domain.Scheduler;
import com.scheduletl.domain.SchedulerStatus;
import com.scheduletl.dto.SchedulerResponse;
import com.scheduletl.dto.UpdateSchedulerStatusRequest;
import com.scheduletl.repository.SchedulerRepository;
import com.scheduletl.repository.UserGroupRepository;
import com.scheduletl.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulerServiceTest {

    @Mock
    private SchedulerRepository schedulerRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserGroupRepository userGroupRepository;

    @Test
    void updateStatusPersistsTheSelectedStatus() {
        Scheduler scheduler = new Scheduler();
        scheduler.setId(1L);
        scheduler.setStatus(SchedulerStatus.ACTIVE);
        when(schedulerRepository.findById(1L)).thenReturn(Optional.of(scheduler));
        when(schedulerRepository.save(scheduler)).thenReturn(scheduler);
        SchedulerService service = new SchedulerService(
                schedulerRepository, userProfileRepository, userGroupRepository);

        SchedulerResponse response = service.updateStatus(
                1L, new UpdateSchedulerStatusRequest(SchedulerStatus.PAUSED));

        assertEquals(SchedulerStatus.PAUSED, scheduler.getStatus());
        assertEquals(SchedulerStatus.PAUSED, response.status());
        verify(schedulerRepository).save(scheduler);
    }

    @Test
    void updateStatusDoesNotAllowLogicalDeletion() {
        SchedulerService service = new SchedulerService(
                schedulerRepository, userProfileRepository, userGroupRepository);

        assertThrows(ResponseStatusException.class, () -> service.updateStatus(
                1L, new UpdateSchedulerStatusRequest(SchedulerStatus.DELETED)));
    }
}
