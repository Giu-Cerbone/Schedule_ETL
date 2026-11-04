package com.scheduletl.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "process_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type", referencedColumnName = "code", nullable = false)
    private ProcessType type;

    @Column(length = 2000)
    private String description;

    @Column(length = 4000)
    private String configuration;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private int sequenceOrder;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheduler_id", nullable = false)
    private Scheduler scheduler;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_process_id")
    private ProcessDefinition parentProcess;

    @OneToMany(mappedBy = "parentProcess")
    private List<ProcessDefinition> childProcesses = new ArrayList<>();

    @OneToMany(mappedBy = "processDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProcessExecution> executions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
