package com.scheduletl.dto;

import java.util.List;

public record ProcessEventLogPageResponse(
        List<ProcessEventLogResponse> items,
        long totalElements,
        int page,
        int size
) {
}
