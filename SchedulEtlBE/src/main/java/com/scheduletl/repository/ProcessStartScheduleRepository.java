package com.scheduletl.repository;

import com.scheduletl.domain.ProcessStartSchedule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProcessStartScheduleRepository extends JpaRepository<ProcessStartSchedule, Long> {

    @Override
    @EntityGraph(attributePaths = {"processDefinition", "processDefinition.scheduler"})
    List<ProcessStartSchedule> findAll();

    @Modifying
    @Transactional
    @Query("""
            update ProcessStartSchedule s
            set s.lastTriggeredAt = :scheduledAt
            where s.id = :scheduleId
              and (s.lastTriggeredAt is null or s.lastTriggeredAt < :scheduledAt)
              and s.processDefinition.enabled = true
              and s.processDefinition.scheduler.status = com.scheduletl.domain.SchedulerStatus.ACTIVE
            """)
    int claimScheduledStart(@Param("scheduleId") Long scheduleId,
                            @Param("scheduledAt") LocalDateTime scheduledAt);
}
