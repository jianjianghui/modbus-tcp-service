package com.example.modbus.events;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Event published after a Modbus polling cycle completes.
 */
public final class MeasurementEvent {

    private final String deviceId;
    private final Instant timestamp;
    private final List<MeasurementSample> samples;

    public MeasurementEvent(String deviceId, Instant timestamp, List<MeasurementSample> samples) {
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        if (deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(samples, "samples");
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("samples must not be empty");
        }
        this.samples = Collections.unmodifiableList(new ArrayList<>(samples));
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public List<MeasurementSample> getSamples() {
        return samples;
    }
}
