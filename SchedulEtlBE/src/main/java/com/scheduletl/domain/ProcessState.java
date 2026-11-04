package com.scheduletl.domain;

public enum ProcessState {
    CREATED,
    QUEUED,
    RUNNING,
    PAUSED,
    STOPPING,
    STOPPED,
    COMPLETED,
    FAILED,
    CANCELLED
}
