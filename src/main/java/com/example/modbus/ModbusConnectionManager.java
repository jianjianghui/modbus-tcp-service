package com.example.modbus;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.PlcDriverManager;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;

import java.io.Closeable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ModbusConnectionManager based on Apache PLC4X that supports Modbus TCP and RTU-over-TCP
 * via an Apache PLC4X connection string (e.g., modbus:tcp://host:502?unit-identifier=1).
 *
 * Provides: health checks, timeout/retry configuration, and automatic reconnect with
 * exponential backoff and jitter.
 */
public class ModbusConnectionManager implements Closeable {

    private final ModbusConfig config;

    private final Counter reconnectCounter;

    private final ScheduledExecutorService scheduler;
    private final Random jitterRandom = new Random();

    private final AtomicReference<PlcConnection> connectionRef = new AtomicReference<>();
    private final AtomicReference<Instant> lastConnectedAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastAttemptAt = new AtomicReference<>();
    private final AtomicReference<Throwable> lastError = new AtomicReference<>();
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final PlcDriverManager driverManager;

    public ModbusConnectionManager(ModbusConfig config) {
        this(config, null);
    }

    public ModbusConnectionManager(ModbusConfig config, MeterRegistry meterRegistry) {
        this.config = Objects.requireNonNull(config);
        this.driverManager = new PlcDriverManager();
        this.reconnectCounter = meterRegistry != null
                ? Counter.builder("modbus.connection.reconnects")
                .description("Number of times a Modbus connection has been (re)established")
                .tag("connection", config.getConnectionString())
                .register(meterRegistry)
                : null;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "modbus-conn-manager");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void start() {
        if (closed.get()) {
            throw new IllegalStateException("Manager already closed");
        }
        scheduleImmediateConnect();
    }

    public synchronized void stop() {
        closed.set(true);
        PlcConnection c = connectionRef.getAndSet(null);
        if (c != null) {
            try { c.close(); } catch (Exception ignored) {}
        }
        scheduler.shutdownNow();
    }

    @Override
    public void close() {
        stop();
    }

    public boolean isConnected() {
        PlcConnection c = connectionRef.get();
        try {
            return c != null && c.isConnected();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public HealthStatus health() {
        HealthStatus.Status status;
        if (closed.get()) status = HealthStatus.Status.CLOSED;
        else if (isConnected()) status = HealthStatus.Status.HEALTHY;
        else status = HealthStatus.Status.CONNECTING;

        return new HealthStatus(
                status,
                lastConnectedAt.get(),
                lastAttemptAt.get(),
                lastError.get() == null ? null : (lastError.get().getClass().getSimpleName() + ": " + lastError.get().getMessage())
        );
    }

    private void scheduleImmediateConnect() {
        reconnectAttempts.set(0);
        scheduler.execute(this::connectWithBackoffLoop);
    }

    private void connectWithBackoffLoop() {
        if (closed.get()) return;
        while (!closed.get()) {
            lastAttemptAt.set(Instant.now());
            try {
                PlcConnection existing = connectionRef.get();
                if (existing != null && existing.isConnected()) {
                    return; // already connected
                }

                PlcConnection connection = driverManager.getConnection(config.getConnectionString());
                // Some drivers establish on first request, but explicit connect is fine
                try { connection.connect(); } catch (Throwable ignored) {}

                if (connection.isConnected()) {
                    connectionRef.set(connection);
                    lastConnectedAt.set(Instant.now());
                    lastError.set(null);
                    reconnectAttempts.set(0);
                    if (reconnectCounter != null) {
                        reconnectCounter.increment();
                    }
                    return;
                } else {
                    throw new PlcConnectionException("Connection not established");
                }
            } catch (Throwable e) {
                lastError.set(e);
                PlcConnection c = connectionRef.getAndSet(null);
                if (c != null) { try { c.close(); } catch (Exception ignored) {} }
                sleepBackoff(reconnectAttempts.getAndIncrement());
            }
        }
    }

    private void sleepBackoff(int attempt) {
        long base = config.getInitialBackoff().toMillis();
        long max = config.getMaxBackoff().toMillis();
        long exp = (long) (base * Math.pow(2, Math.min(10, attempt))); // cap exponent growth
        long delay = Math.min(max, Math.max(base, exp));
        double jitter = 1 + ((jitterRandom.nextDouble() * 2 - 1) * config.getJitter());
        long jitteredDelay = Math.max(0, (long) (delay * jitter));
        try {
            Thread.sleep(jitteredDelay);
        } catch (InterruptedException ignored) {
        }
    }

    private PlcConnection requireConnection() throws PlcConnectionException {
        PlcConnection c = connectionRef.get();
        if (c != null) {
            try {
                if (c.isConnected()) return c;
            } catch (Throwable ignored) {}
        }
        connectWithBackoffLoop();
        c = connectionRef.get();
        if (c == null || !c.isConnected()) {
            throw new PlcConnectionException("Unable to obtain a connected Modbus connection");
        }
        return c;
    }

    // ===== Typed helpers: Read single =====

    public boolean readCoil(int address) throws Exception {
        boolean[] v = readCoils(address, 1);
        return v.length > 0 && v[0];
    }

    public boolean readDiscreteInput(int address) throws Exception {
        boolean[] v = readDiscreteInputs(address, 1);
        return v.length > 0 && v[0];
    }

    public int readHoldingRegister(int address) throws Exception {
        int[] v = readHoldingRegisters(address, 1);
        return v.length > 0 ? v[0] : 0;
    }

    public int readInputRegister(int address) throws Exception {
        int[] v = readInputRegisters(address, 1);
        return v.length > 0 ? v[0] : 0;
    }

    // ===== Typed helpers: Read multiple =====

    public boolean[] readCoils(int address, int count) throws Exception {
        return executeWithRetry(() -> doReadBooleans("coil", address, count));
    }

    public boolean[] readDiscreteInputs(int address, int count) throws Exception {
        return executeWithRetry(() -> doReadBooleans("discrete-input", address, count));
    }

    public int[] readHoldingRegisters(int address, int count) throws Exception {
        return executeWithRetry(() -> doReadUInt16("holding-register", address, count));
    }

    public int[] readInputRegisters(int address, int count) throws Exception {
        return executeWithRetry(() -> doReadUInt16("input-register", address, count));
    }

    // ===== Typed helpers: Write =====

    public void writeCoil(int address, boolean value) throws Exception {
        executeWithRetry(() -> {
            doWrite("coil", address, new boolean[]{value});
            return null;
        });
    }

    public void writeCoils(int address, boolean[] values) throws Exception {
        executeWithRetry(() -> {
            doWrite("coil", address, values);
            return null;
        });
    }

    public void writeHoldingRegister(int address, int value) throws Exception {
        executeWithRetry(() -> {
            doWrite("holding-register", address, new int[]{value});
            return null;
        });
    }

    public void writeHoldingRegisters(int address, int[] values) throws Exception {
        executeWithRetry(() -> {
            doWrite("holding-register", address, values);
            return null;
        });
    }

    // ===== Low-level operations =====

    private boolean[] doReadBooleans(String type, int address, int count) throws Exception {
        PlcConnection c = requireConnection();
        String tag = addressSpec(type, address, count);
        PlcReadRequest.Builder b = c.readRequestBuilder();
        b.addTagAddress("r", tag);
        PlcReadRequest req = b.build();
        PlcReadResponse resp = req.execute().get(config.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        ensureOk(resp, "r");
        if (count == 1) {
            return new boolean[]{resp.getBoolean("r")};
        } else {
            List<Boolean> list = resp.getAllBooleans("r");
            boolean[] arr = new boolean[list.size()];
            for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
            return arr;
        }
    }

    private int[] doReadUInt16(String type, int address, int count) throws Exception {
        PlcConnection c = requireConnection();
        String tag = addressSpec(type, address, count);
        PlcReadRequest.Builder b = c.readRequestBuilder();
        b.addTagAddress("r", tag);
        PlcReadRequest req = b.build();
        PlcReadResponse resp = req.execute().get(config.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        ensureOk(resp, "r");
        if (count == 1) {
            // 16-bit unsigned represented as int (0..65535)
            int v = unsigned16(resp.getShort("r"));
            return new int[]{v};
        } else {
            List<Short> vals = resp.getAllShorts("r");
            int[] out = new int[vals.size()];
            for (int i = 0; i < vals.size(); i++) out[i] = unsigned16(vals.get(i));
            return out;
        }
    }

    private void doWrite(String type, int address, boolean[] values) throws Exception {
        PlcConnection c = requireConnection();
        String tag = addressSpec(type, address, values.length);
        PlcWriteRequest.Builder b = c.writeRequestBuilder();
        // For single value, pass scalar; for multiple, pass array
        if (values.length == 1) {
            b.addTagAddress("w", tag, values[0]);
        } else {
            Boolean[] boxed = new Boolean[values.length];
            for (int i = 0; i < values.length; i++) boxed[i] = values[i];
            b.addTagAddress("w", tag, (Object[]) boxed);
        }
        PlcWriteRequest req = b.build();
        PlcWriteResponse resp = req.execute().get(config.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        ensureOk(resp, "w");
    }

    private void doWrite(String type, int address, int[] values) throws Exception {
        PlcConnection c = requireConnection();
        String tag = addressSpec(type, address, values.length);
        PlcWriteRequest.Builder b = c.writeRequestBuilder();
        if (values.length == 1) {
            b.addTagAddress("w", tag, (short) (values[0] & 0xFFFF));
        } else {
            Short[] boxed = new Short[values.length];
            for (int i = 0; i < values.length; i++) boxed[i] = (short) (values[i] & 0xFFFF);
            b.addTagAddress("w", tag, (Object[]) boxed);
        }
        PlcWriteRequest req = b.build();
        PlcWriteResponse resp = req.execute().get(config.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        ensureOk(resp, "w");
    }

    private static void ensureOk(PlcReadResponse resp, String field) throws PlcConnectionException {
        PlcResponseCode code = resp.getResponseCode(field);
        if (code != PlcResponseCode.OK) {
            throw new PlcConnectionException("Read failed: " + code);
        }
    }

    private static void ensureOk(PlcWriteResponse resp, String field) throws PlcConnectionException {
        PlcResponseCode code = resp.getResponseCode(field);
        if (code != PlcResponseCode.OK) {
            throw new PlcConnectionException("Write failed: " + code);
        }
    }

    private static int unsigned16(short s) { return s & 0xFFFF; }

    private static String addressSpec(String type, int address, int count) {
        if (count <= 1) return type + ":" + address;
        return type + ":" + address + "[" + count + "]";
    }

    private <T> T executeWithRetry(Callable<T> op) throws Exception {
        int attempts = 0;
        Exception last = null;
        while (attempts <= config.getMaxRetries()) {
            try {
                return op.call();
            } catch (Exception e) {
                last = e;
                // on exception, try to reconnect and retry with backoff
                try {
                    PlcConnection c = connectionRef.getAndSet(null);
                    if (c != null) try { c.close(); } catch (Exception ignored) {}
                } catch (Throwable ignored) {}
                sleepBackoff(attempts);
                attempts++;
                connectWithBackoffLoop();
            }
        }
        throw last != null ? last : new PlcConnectionException("Operation failed after retries");
    }
}
