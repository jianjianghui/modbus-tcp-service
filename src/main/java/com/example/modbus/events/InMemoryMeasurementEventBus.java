package com.example.modbus.events;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Thread-safe in-memory implementation of {@link MeasurementEventBus}.
 */
public final class InMemoryMeasurementEventBus implements MeasurementEventBus {

    private final CopyOnWriteArrayList<Consumer<MeasurementEvent>> subscribers = new CopyOnWriteArrayList<>();

    @Override
    public void publish(MeasurementEvent event) {
        Objects.requireNonNull(event, "event");
        for (Consumer<MeasurementEvent> subscriber : subscribers) {
            subscriber.accept(event);
        }
    }

    @Override
    public AutoCloseable subscribe(Consumer<MeasurementEvent> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        subscribers.add(consumer);
        return () -> subscribers.remove(consumer);
    }
}
