package com.scheduletl.service;

import com.scheduletl.domain.ProcessEventLog;
import com.scheduletl.dto.ProcessEventLogPageResponse;
import com.scheduletl.dto.ProcessEventLogResponse;
import com.scheduletl.repository.ProcessEventLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProcessEventLogQueryService {

    private static final int MAX_PAGE_SIZE = 200;

    private final ProcessEventLogRepository repository;

    public record Filter(String text, String level, String eventType, Long schedulerId, Long processDefinitionId,
                         Long executionId, String triggerType, LocalDateTime from, LocalDateTime to) {
    }

    @Transactional(readOnly = true)
    public ProcessEventLogPageResponse search(Filter filter, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<ProcessEventLog> result = repository.findAll(toSpecification(filter),
                PageRequest.of(Math.max(page, 0), safeSize,
                        Sort.by(Sort.Order.desc("loggedAt"), Sort.Order.desc("id"))));
        return new ProcessEventLogPageResponse(result.getContent().stream().map(this::toResponse).toList(),
                result.getTotalElements(), result.getNumber(), result.getSize());
    }

    private Specification<ProcessEventLog> toSpecification(Filter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.text() != null && !filter.text().isBlank()) {
                String pattern = "%" + filter.text().trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("message")), pattern),
                        builder.like(builder.lower(builder.coalesce(root.<String>get("details"), "")), pattern),
                        builder.like(builder.lower(builder.coalesce(root.<String>get("processName"), "")), pattern),
                        builder.like(builder.lower(builder.coalesce(root.<String>get("schedulerName"), "")), pattern),
                        builder.like(builder.lower(builder.coalesce(root.<String>get("stepName"), "")), pattern)));
            }
            equalIfPresent(predicates, builder, root.get("level"), filter.level());
            equalIfPresent(predicates, builder, root.get("eventType"), filter.eventType());
            equalIfPresent(predicates, builder, root.get("triggerType"), filter.triggerType());
            equalIfPresent(predicates, builder, root.get("schedulerId"), filter.schedulerId());
            equalIfPresent(predicates, builder, root.get("processDefinitionId"), filter.processDefinitionId());
            equalIfPresent(predicates, builder, root.get("executionId"), filter.executionId());
            if (filter.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("loggedAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("loggedAt"), filter.to()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void equalIfPresent(List<Predicate> predicates,
                                       jakarta.persistence.criteria.CriteriaBuilder builder,
                                       jakarta.persistence.criteria.Path<?> path, Object value) {
        if (value != null && !(value instanceof String text && text.isBlank())) {
            predicates.add(builder.equal(path, value));
        }
    }

    private ProcessEventLogResponse toResponse(ProcessEventLog log) {
        return new ProcessEventLogResponse(log.getId(), log.getLoggedAt(), log.getLevel(), log.getEventType(),
                log.getStepNumber(), log.getStepName(), log.getMessage(), log.getDetails(), log.getExecutionId(),
                log.getProcessDefinitionId(), log.getProcessName(), log.getProcessType(), log.getParentProcessId(),
                log.getSequenceOrder(), log.getSchedulerId(), log.getSchedulerName(), log.getTriggerType(),
                log.getScheduleInfo(), log.getState(), log.getExitCode());
    }
}
