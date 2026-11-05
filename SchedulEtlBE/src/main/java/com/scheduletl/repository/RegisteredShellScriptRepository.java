package com.scheduletl.repository;

import com.scheduletl.domain.RegisteredShellScript;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegisteredShellScriptRepository extends JpaRepository<RegisteredShellScript, Long> {

    boolean existsByPath(String path);
    java.util.Optional<RegisteredShellScript> findByPath(String path);
}
