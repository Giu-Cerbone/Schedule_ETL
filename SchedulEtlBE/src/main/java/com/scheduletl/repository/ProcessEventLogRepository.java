package com.scheduletl.repository;

import com.scheduletl.domain.ProcessEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessEventLogRepository
        extends JpaRepository<ProcessEventLog, Long>, JpaSpecificationExecutor<ProcessEventLog> {
    long countByExecutionId(Long executionId);
}
