package com.scheduletl.repository;

import com.scheduletl.domain.AuthorizedShellCommand;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorizedShellCommandRepository extends JpaRepository<AuthorizedShellCommand, Long> {
}
