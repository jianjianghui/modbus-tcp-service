package com.example.modbus.polling;

import com.example.modbus.ModbusConnectionManager;
import com.example.modbus.events.MeasurementEvent;
import com.example.modbus.events.MeasurementEventBus;
import com.example.modbus.events.MeasurementSample;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.io.Closeable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Polls configured Modbus devices on a schedule, batching adjacent addresses, emitting events, and recording metrics.
 */
public final class DevicePollingScheduler implements Closeable {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    private final ScheduledExecutorService executor;
    private final MeasurementEventBus eventBus;
    private final MeterRegistry meterRegistry;
    private final boolean ownsExecutor;
    private final ConcurrentMap<String, DevicePollContext> contexts = new ConcurrentHashMap<>();

    public DevicePollingScheduler(MeterRegistry meterRegistry, MeasurementEventBus eventBus) {
        this(createDefaultExecutor(), meterRegistry, eventBus, true);
    }

    public DevicePollingScheduler(ScheduledExecutorService executor, MeterRegistry meterRegistry, MeasurementEventBus eventBus) {
        this(executor, meterRegistry, eventBus, false);
    }

    private DevicePollingScheduler(ScheduledExecutorService executor,
                                   MeterRegistry meterRegistry,
                                   MeasurementEventBus eventBus,
                                   boolean ownsExecutor) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.meterRegistry = meterRegistry;
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.ownsExecutor = ownsExecutor;
    }

    private static ScheduledExecutorService createDefaultExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        int threads = Math.max(2, cores);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(threads, r -> {
            Thread t = new Thread(r, "modbus-poll-" + THREAD_COUNTER.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    public void registerDevice(DevicePollingConfig config) {
        Objects.requireNonNull(config, "config");

        long interval = config.getPollInterval().toMillis();
        if (interval <= 0) {
            throw new IllegalArgumentException("Poll interval must be > 0 for device " + config.getDeviceId());
        }
        long initialDelay = Math.max(0L, config.getInitialDelay().toMillis());

        DevicePollContext context = new DevicePollContext(config, meterRegistry);
        DevicePollContext existing = contexts.putIfAbsent(config.getDeviceId(), context);
        if (existing != null) {
            throw new IllegalArgumentException("Device already registered: " + config.getDeviceId());
        }

        try {
            config.getConnectionManager().start();
            ScheduledFuture<?> future = executor.scheduleAtFixedRate(
                    () -> executePoll(context),
                    initialDelay,
                    interval,
                    TimeUnit.MILLISECONDS
            );
            context.setFuture(future);
        } catch (RuntimeException e) {
            contexts.remove(config.getDeviceId(), context);
            config.getConnectionManager().stop();
            throw e;
        }
    }

    public void unregisterDevice(String deviceId) {
        Objects.requireNonNull(deviceId, "deviceId");
        DevicePollContext context = contexts.remove(deviceId);
        if (context != null) {
            ScheduledFuture<?> future = context.future;
            if (future != null) {
                future.cancel(false);
            }
            context.config.getConnectionManager().stop();
        }
    }

    public boolean isRegistered(String deviceId) {
        Objects.requireNonNull(deviceId, "deviceId");
        return contexts.containsKey(deviceId);
    }

    public Optional<Throwable> getLastError(String deviceId) {
        Objects.requireNonNull(deviceId, "deviceId");
        DevicePollContext context = contexts.get(deviceId);
        if (context == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(context.lastError.get());
    }

    @Override
    public void close() {
        for (DevicePollContext context : contexts.values()) {
            ScheduledFuture<?> future = context.future;
            if (future != null) {
                future.cancel(true);
            }
            context.config.getConnectionManager().stop();
        }
        contexts.clear();
        if (ownsExecutor) {
            executor.shutdownNow();
        }
    }

    private void executePoll(DevicePollContext context) {
        if (!context.inFlight.compareAndSet(false, true)) {
            if (context.backpressureCounter != null) {
                context.backpressureCounter.increment();
            }
            return;
        }

        Timer.Sample sample = context.pollTimer != null ? Timer.start(meterRegistry) : null;
        try {
            List<MeasurementSample> samples = performPoll(context);
            if (!samples.isEmpty()) {
                MeasurementEvent event = new MeasurementEvent(context.config.getDeviceId(), Instant.now(), samples);
                eventBus.publish(event);
            }
            if (sample != null) {
                sample.stop(context.pollTimer);
            }
            context.lastError.set(null);
        } catch (Exception e) {
            context.lastError.set(e);
            if (context.errorCounter != null) {
                context.errorCounter.increment();
            }
        } finally {
            context.inFlight.set(false);
        }
    }

    private List<MeasurementSample> performPoll(DevicePollContext context) throws Exception {
        Map<String, MeasurementSample> samplesById = new HashMap<>();
        ModbusConnectionManager manager = context.config.getConnectionManager();

        for (Batch batch : context.batches) {
            switch (batch.type) {
                case COIL:
                    boolean[] coils = manager.readCoils(batch.startAddress, batch.count);
                    applyBooleanBatch(batch, coils, samplesById);
                    break;
                case DISCRETE_INPUT:
                    boolean[] discreteInputs = manager.readDiscreteInputs(batch.startAddress, batch.count);
                    applyBooleanBatch(batch, discreteInputs, samplesById);
                    break;
                case HOLDING_REGISTER:
                    int[] holding = manager.readHoldingRegisters(batch.startAddress, batch.count);
                    applyRegisterBatch(batch, holding, samplesById);
                    break;
                case INPUT_REGISTER:
                    int[] input = manager.readInputRegisters(batch.startAddress, batch.count);
                    applyRegisterBatch(batch, input, samplesById);
                    break;
                default:
                    throw new IllegalStateException("Unsupported measurement type: " + batch.type);
            }
        }

        List<MeasurementSample> ordered = new ArrayList<>(context.orderedDefinitions.size());
        for (MeasurementDefinition definition : context.orderedDefinitions) {
            MeasurementSample sample = samplesById.get(definition.getId());
            if (sample != null) {
                ordered.add(sample);
            }
        }
        return ordered;
    }

    private void applyBooleanBatch(Batch batch, boolean[] values, Map<String, MeasurementSample> out) {
        if (values.length < batch.count) {
            throw new IllegalStateException("Received fewer boolean values than expected");
        }
        for (MeasurementSlice slice : batch.slices) {
            MeasurementDefinition definition = slice.definition;
            int offset = slice.offset;
            int length = definition.getCount();
            Object value;
            if (length == 1) {
                value = values[offset];
            } else {
                boolean[] copy = new boolean[length];
                System.arraycopy(values, offset, copy, 0, length);
                value = copy;
            }
            out.put(definition.getId(), new MeasurementSample(definition, value));
        }
    }

    private void applyRegisterBatch(Batch batch, int[] values, Map<String, MeasurementSample> out) {
        if (values.length < batch.count) {
            throw new IllegalStateException("Received fewer register values than expected");
        }
        for (MeasurementSlice slice : batch.slices) {
            MeasurementDefinition definition = slice.definition;
            int offset = slice.offset;
            int length = definition.getCount();
            Object value;
            if (length == 1) {
                value = values[offset];
            } else {
                int[] copy = new int[length];
                System.arraycopy(values, offset, copy, 0, length);
                value = copy;
            }
            out.put(definition.getId(), new MeasurementSample(definition, value));
        }
    }

    private static List<Batch> createBatches(List<MeasurementDefinition> definitions) {
        EnumMap<MeasurementType, List<MeasurementDefinition>> byType = new EnumMap<>(MeasurementType.class);
        for (MeasurementDefinition definition : definitions) {
            byType.computeIfAbsent(definition.getType(), t -> new ArrayList<>()).add(definition);
        }

        List<Batch> batches = new ArrayList<>();
        for (Map.Entry<MeasurementType, List<MeasurementDefinition>> entry : byType.entrySet()) {
            List<MeasurementDefinition> sorted = new ArrayList<>(entry.getValue());
            sorted.sort(Comparator.comparingInt(MeasurementDefinition::getAddress));
            int index = 0;
            while (index < sorted.size()) {
                MeasurementDefinition head = sorted.get(index);
                int batchStart = head.getAddress();
                int batchEnd = head.getEndExclusive();
                List<MeasurementSlice> slices = new ArrayList<>();
                slices.add(new MeasurementSlice(head, 0));
                index++;
                while (index < sorted.size()) {
                    MeasurementDefinition next = sorted.get(index);
                    if (next.getAddress() != batchEnd) {
                        break;
                    }
                    slices.add(new MeasurementSlice(next, next.getAddress() - batchStart));
                    batchEnd = next.getEndExclusive();
                    index++;
                }
                int count = batchEnd - batchStart;
                batches.add(new Batch(entry.getKey(), batchStart, count, Collections.unmodifiableList(new ArrayList<>(slices))));
            }
        }
        return Collections.unmodifiableList(batches);
    }

    private static final class DevicePollContext {
        final DevicePollingConfig config;
        final List<MeasurementDefinition> orderedDefinitions;
        final List<Batch> batches;
        final AtomicBoolean inFlight = new AtomicBoolean(false);
        final AtomicReference<Throwable> lastError = new AtomicReference<>();
        final Timer pollTimer;
        final Counter errorCounter;
        final Counter backpressureCounter;
        volatile ScheduledFuture<?> future;

        DevicePollContext(DevicePollingConfig config, MeterRegistry registry) {
            this.config = config;
            this.orderedDefinitions = config.getMeasurements();
            if (orderedDefinitions.isEmpty()) {
                throw new IllegalArgumentException("No measurements configured for device " + config.getDeviceId());
            }
            this.batches = createBatches(orderedDefinitions);
            if (batches.isEmpty()) {
                throw new IllegalArgumentException("No measurement batches created for device " + config.getDeviceId());
            }
            if (registry != null) {
                this.pollTimer = Timer.builder("modbus.poll.duration")
                        .description("Duration of Modbus polling cycles")
                        .tag("device", config.getDeviceId())
                        .register(registry);
                this.errorCounter = Counter.builder("modbus.poll.errors")
                        .description("Number of Modbus polling errors")
                        .tag("device", config.getDeviceId())
                        .register(registry);
                this.backpressureCounter = Counter.builder("modbus.poll.backpressure")
                        .description("Number of Modbus polls skipped due to in-flight work")
                        .tag("device", config.getDeviceId())
                        .register(registry);
            } else {
                this.pollTimer = null;
                this.errorCounter = null;
                this.backpressureCounter = null;
            }
        }

        void setFuture(ScheduledFuture<?> future) {
            this.future = future;
        }
    }

    private static final class Batch {
        final MeasurementType type;
        final int startAddress;
        final int count;
        final List<MeasurementSlice> slices;

        Batch(MeasurementType type, int startAddress, int count, List<MeasurementSlice> slices) {
            this.type = type;
            this.startAddress = startAddress;
            this.count = count;
            this.slices = slices;
        }
    }

    private static final class MeasurementSlice {
        final MeasurementDefinition definition;
        final int offset;

        MeasurementSlice(MeasurementDefinition definition, int offset) {
            this.definition = definition;
            this.offset = offset;
        }
    }
}
