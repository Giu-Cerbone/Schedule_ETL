package com.scheduletl.repository;

import com.scheduletl.domain.ProcessExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessExecutionRepository extends JpaRepository<ProcessExecution, Long> {
    List<ProcessExecution> findBySchedulerId(Long schedulerId);
    List<ProcessExecution> findByProcessDefinitionId(Long processDefinitionId);
}
