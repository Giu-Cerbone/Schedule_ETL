package com.scheduletl.dto;

import com.scheduletl.domain.ProcessCommand;
import jakarta.validation.constraints.NotNull;

public record ProcessCommandRequest(@NotNull(message = "Command is required") ProcessCommand command) {
}
