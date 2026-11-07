package com.scheduletl.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(
        name = "process_start_schedules",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_process_start_schedule_slot",
                columnNames = {"process_definition_id", "day_of_week", "start_date", "start_time"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class ProcessStartSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private ProcessScheduleDay dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_definition_id", nullable = false)
    private ProcessDefinition processDefinition;
}
