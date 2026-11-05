package com.scheduletl.dto;

import java.util.List;

public record AuthorizedShellCommandResponse(
        Long id, String name, String type, String executable, List<String> arguments) {
}
