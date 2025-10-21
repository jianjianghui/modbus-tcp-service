package com.example.modbus;

import java.time.Duration;

public class ModbusConfig {

    private final String connectionString;
    private final Duration requestTimeout;
    private final int maxRetries;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final double jitter;

    public static Builder builder(String connectionString) {
        return new Builder(connectionString);
    }

    public ModbusConfig(String connectionString,
                        Duration requestTimeout,
                        int maxRetries,
                        Duration initialBackoff,
                        Duration maxBackoff,
                        double jitter) {
        this.connectionString = connectionString;
        this.requestTimeout = requestTimeout;
        this.maxRetries = maxRetries;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.jitter = jitter;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    public double getJitter() {
        return jitter;
    }

    public static final class Builder {
        private final String connectionString;
        private Duration requestTimeout = Duration.ofSeconds(5);
        private int maxRetries = 3;
        private Duration initialBackoff = Duration.ofMillis(250);
        private Duration maxBackoff = Duration.ofSeconds(10);
        private double jitter = 0.2; // +/-20%

        private Builder(String connectionString) {
            this.connectionString = connectionString;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
            return this;
        }

        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
            return this;
        }

        public Builder jitter(double jitter) {
            if (jitter < 0 || jitter > 1) {
                throw new IllegalArgumentException("jitter must be between 0 and 1");
            }
            this.jitter = jitter;
            return this;
        }

        public ModbusConfig build() {
            return new ModbusConfig(connectionString, requestTimeout, maxRetries, initialBackoff, maxBackoff, jitter);
        }
    }
}
