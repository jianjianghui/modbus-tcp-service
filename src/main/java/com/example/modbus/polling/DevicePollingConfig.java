package com.example.modbus.polling;

import com.example.modbus.ModbusConnectionManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration for polling a single Modbus device.
 */
public final class DevicePollingConfig {

    private final String deviceId;
    private final Duration pollInterval;
    private final Duration initialDelay;
    private final ModbusConnectionManager connectionManager;
    private final List<MeasurementDefinition> measurements;

    private DevicePollingConfig(String deviceId,
                                 Duration pollInterval,
                                 Duration initialDelay,
                                 ModbusConnectionManager connectionManager,
                                 List<MeasurementDefinition> measurements) {
        this.deviceId = deviceId;
        this.pollInterval = pollInterval;
        this.initialDelay = initialDelay;
        this.connectionManager = connectionManager;
        this.measurements = measurements;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public ModbusConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public List<MeasurementDefinition> getMeasurements() {
        return measurements;
    }

    public static Builder builder(String deviceId, ModbusConnectionManager connectionManager) {
        return new Builder(deviceId, connectionManager);
    }

    public static final class Builder {
        private final String deviceId;
        private final ModbusConnectionManager connectionManager;
        private Duration pollInterval = Duration.ofSeconds(5);
        private Duration initialDelay = Duration.ZERO;
        private final List<MeasurementDefinition> measurements = new ArrayList<>();
        private final Set<String> measurementIds = new HashSet<>();

        private Builder(String deviceId, ModbusConnectionManager connectionManager) {
            this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
            if (deviceId.isBlank()) {
                throw new IllegalArgumentException("deviceId must not be blank");
            }
            this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager");
        }

        public Builder pollInterval(Duration pollInterval) {
            Objects.requireNonNull(pollInterval, "pollInterval");
            if (pollInterval.isZero() || pollInterval.isNegative()) {
                throw new IllegalArgumentException("pollInterval must be > 0");
            }
            this.pollInterval = pollInterval;
            return this;
        }

        public Builder initialDelay(Duration initialDelay) {
            Objects.requireNonNull(initialDelay, "initialDelay");
            if (initialDelay.isNegative()) {
                throw new IllegalArgumentException("initialDelay must be >= 0");
            }
            this.initialDelay = initialDelay;
            return this;
        }

        public Builder addMeasurement(MeasurementDefinition measurement) {
            Objects.requireNonNull(measurement, "measurement");
            if (!measurementIds.add(measurement.getId())) {
                throw new IllegalArgumentException("Duplicate measurement id: " + measurement.getId());
            }
            measurements.add(measurement);
            return this;
        }

        public DevicePollingConfig build() {
            if (measurements.isEmpty()) {
                throw new IllegalStateException("At least one measurement must be configured");
            }
            return new DevicePollingConfig(
                    deviceId,
                    pollInterval,
                    initialDelay,
                    connectionManager,
                    Collections.unmodifiableList(new ArrayList<>(measurements))
            );
        }
    }
}
