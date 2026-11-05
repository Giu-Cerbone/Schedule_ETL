package com.scheduletl.dto;

import java.util.List;

public record FileBrowseResponse(String path, String parent, List<Entry> entries) {

    public record Entry(String name, String path, boolean directory) {
    }
}
