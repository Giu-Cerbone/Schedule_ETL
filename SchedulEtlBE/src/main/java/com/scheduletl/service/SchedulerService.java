package com.scheduletl.service;

import com.scheduletl.domain.*;
import com.scheduletl.dto.CreateSchedulerRequest;
import com.scheduletl.dto.SchedulerResponse;
import com.scheduletl.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SchedulerService {

    private final SchedulerRepository schedulerRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserGroupRepository userGroupRepository;

    @Transactional(readOnly = true)
    public List<SchedulerResponse> findAll() {
        return schedulerRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SchedulerResponse findById(Long schedulerId) {
        Scheduler scheduler = schedulerRepository.findById(schedulerId)
                .orElseThrow(() -> new EntityNotFoundException("Scheduler not found: " + schedulerId));
        return toResponse(scheduler);
    }

    @Transactional
    public SchedulerResponse create(CreateSchedulerRequest request) {
        UserProfile owner = userProfileRepository.findById(request.ownerProfileId())
                .orElseThrow(() -> new EntityNotFoundException("Owner profile not found: " + request.ownerProfileId()));

        Scheduler scheduler = new Scheduler();
        scheduler.setName(request.name());
        scheduler.setDescription(request.description());
        scheduler.setCronExpression(request.cronExpression());
        scheduler.setOwnerProfile(owner);
        scheduler.setStatus(request.status() != null ? request.status() : SchedulerStatus.ACTIVE);

        if (request.groupId() != null) {
            UserGroup group = userGroupRepository.findById(request.groupId())
                    .orElseThrow(() -> new EntityNotFoundException("Group not found: " + request.groupId()));
            scheduler.setGroup(group);
        }

        Scheduler saved = schedulerRepository.save(scheduler);
        return toResponse(saved);
    }

    @Transactional
    public SchedulerResponse assignToGroup(Long schedulerId, Long groupId) {
        Scheduler scheduler = schedulerRepository.findById(schedulerId)
                .orElseThrow(() -> new EntityNotFoundException("Scheduler not found: " + schedulerId));
        UserGroup group = userGroupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));

        scheduler.setGroup(group);
        return toResponse(schedulerRepository.save(scheduler));
    }

    @Transactional
    public void delete(Long schedulerId) {
        Scheduler scheduler = schedulerRepository.findById(schedulerId)
                .orElseThrow(() -> new EntityNotFoundException("Scheduler not found: " + schedulerId));
        scheduler.setStatus(SchedulerStatus.DELETED);
        schedulerRepository.save(scheduler);
    }

    private SchedulerResponse toResponse(Scheduler scheduler) {
        return new SchedulerResponse(
                scheduler.getId(),
                scheduler.getName(),
                scheduler.getDescription(),
                scheduler.getCronExpression(),
                scheduler.getStatus(),
                scheduler.getOwnerProfile() != null ? scheduler.getOwnerProfile().getId() : null,
                scheduler.getGroup() != null ? scheduler.getGroup().getId() : null,
                scheduler.getCreatedAt(),
                scheduler.getUpdatedAt()
        );
    }
}
