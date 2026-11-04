package com.scheduletl.repository;

import com.scheduletl.domain.ProcessDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessDefinitionRepository extends JpaRepository<ProcessDefinition, Long> {
    List<ProcessDefinition> findBySchedulerIdOrderByParentProcessIdAscSequenceOrderAscIdAsc(Long schedulerId);
    List<ProcessDefinition> findByParentProcessIdOrderBySequenceOrderAscIdAsc(Long parentProcessId);
    List<ProcessDefinition> findByTypeCode(String code);
}
