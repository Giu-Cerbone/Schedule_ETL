package com.scheduletl.repository;

import com.scheduletl.domain.ProcessType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessTypeRepository extends JpaRepository<ProcessType, String> {
    List<ProcessType> findAllByOrderByCodeAsc();
}
