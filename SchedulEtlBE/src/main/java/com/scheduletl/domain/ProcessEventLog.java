package com.scheduletl.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Journal of everything that happens to a process: scheduling, queueing, execution steps, output and outcome.
 * Process and scheduler data are denormalized so entries survive the deletion of the original definitions.
 */
@Entity
@Table(name = "process_event_logs", indexes = {
        @Index(name = "idx_process_event_logs_logged_at", columnList = "loggedAt"),
        @Index(name = "idx_process_event_logs_execution", columnList = "executionId"),
        @Index(name = "idx_process_event_logs_process", columnList = "processDefinitionId")
})
@Getter
@Setter
@NoArgsConstructor
public class ProcessEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime loggedAt;

    @Column(nullable = false, length = 10)
    private String level;

    @Column(nullable = false, length = 30)
    private String eventType;

    private Integer stepNumber;

    @Column(length = 200)
    private String stepName;

    @Column(nullable = false, length = 4000)
    private String message;

    @Column(length = 20000)
    private String details;

    private Long executionId;
    private Long processDefinitionId;

    @Column(length = 255)
    private String processName;

    @Column(length = 100)
    private String processType;

    private Long parentProcessId;
    private Integer sequenceOrder;
    private Long schedulerId;

    @Column(length = 255)
    private String schedulerName;

    @Column(length = 20)
    private String triggerType;

    @Column(length = 1000)
    private String scheduleInfo;

    @Column(length = 20)
    private String state;

    private Integer exitCode;
}
