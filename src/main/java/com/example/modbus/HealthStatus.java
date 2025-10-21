package com.example.modbus;

import java.time.Instant;
import java.util.Optional;

public class HealthStatus {

    public enum Status { HEALTHY, UNHEALTHY, CONNECTING, CLOSED }

    private final Status status;
    private final Instant lastConnectedAt;
    private final Instant lastAttemptAt;
    private final String lastError;

    public HealthStatus(Status status, Instant lastConnectedAt, Instant lastAttemptAt, String lastError) {
        this.status = status;
        this.lastConnectedAt = lastConnectedAt;
        this.lastAttemptAt = lastAttemptAt;
        this.lastError = lastError;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getLastConnectedAt() {
        return lastConnectedAt;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public Optional<String> getLastError() {
        return Optional.ofNullable(lastError);
    }
}
