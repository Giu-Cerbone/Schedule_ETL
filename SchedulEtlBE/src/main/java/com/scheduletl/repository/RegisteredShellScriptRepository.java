package com.scheduletl.repository;

import com.scheduletl.domain.RegisteredShellScript;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegisteredShellScriptRepository extends JpaRepository<RegisteredShellScript, String> {

    boolean existsByPath(String path);
}
