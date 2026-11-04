package com.scheduletl.dto;

import java.util.List;

public record AuthorizedShellCommandResponse(
        String id, String label, String type, String executable, List<String> arguments) {
}
