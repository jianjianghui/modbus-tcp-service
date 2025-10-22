# Modbus Connection Manager

A small Java library providing a robust Modbus connection manager based on Apache PLC4X (plc4j). It supports Modbus TCP and RTU-over-TCP via connection strings such as:

- `modbus:tcp://192.168.1.10:502?unit-identifier=1`

Features:
- Automatic reconnect with exponential backoff and jitter
- Configurable request timeouts and retries
- Health checks (connected/connecting/closed and last error)
- Typed helpers for reading/writing coils and registers
- Scheduled polling per device with automatic batching of adjacent addresses
- Measurement events published through an in-memory event bus
- Micrometer-based metrics exportable via a Prometheus HTTP endpoint

## Requirements
- Java 11+
- Maven 3.8+

## Usage

```
ModbusConfig config = ModbusConfig.builder("modbus:tcp://192.168.1.10:502?unit-identifier=1")
        .requestTimeout(Duration.ofSeconds(5))
        .maxRetries(3)
        .initialBackoff(Duration.ofMillis(250))
        .maxBackoff(Duration.ofSeconds(10))
        .jitter(0.2)
        .build();

ModbusConnectionManager manager = new ModbusConnectionManager(config);
manager.start();

boolean coil1 = manager.readCoil(1);
int hr100 = manager.readHoldingRegister(100);
manager.writeCoil(5, true);
manager.writeHoldingRegister(10, 1234);

HealthStatus health = manager.health();
System.out.println("Status: " + health.getStatus());

manager.close();
```

## Polling, Events, and Metrics

```java
PrometheusMetricsServer metricsServer = new PrometheusMetricsServer(8080);
MeasurementEventBus eventBus = new InMemoryMeasurementEventBus();
DevicePollingScheduler scheduler = new DevicePollingScheduler(metricsServer.getRegistry(), eventBus);

eventBus.subscribe(event -> {
    System.out.println("Received measurements from " + event.getDeviceId());
    event.getSamples().forEach(sample ->
            System.out.println(" - " + sample.getDefinition().getId() + " = " + sample.getValue()));
});

ModbusConnectionManager pollingManager = new ModbusConnectionManager(config, metricsServer.getRegistry());
DevicePollingConfig pollConfig = DevicePollingConfig.builder("device-1", pollingManager)
        .pollInterval(Duration.ofSeconds(5))
        .addMeasurement(MeasurementDefinition.holdingRegister("hr100", 100))
        .addMeasurement(MeasurementDefinition.holdingRegister("hr101", 101))
        .addMeasurement(MeasurementDefinition.coil("coil2", 2))
        .build();

scheduler.registerDevice(pollConfig);
```

Remember to stop the scheduler and metrics server when finished:

```
scheduler.close();
metricsServer.close();
```

## Notes
- This library depends on Apache PLC4X `plc4j-api` and `plc4j-driver-modbus`.
- Connection strings are passed through to PLC4X as-is, so you can use any PLC4X-supported Modbus options, including unit identifiers for Modbus TCP and RTU-over-TCP scenarios.
