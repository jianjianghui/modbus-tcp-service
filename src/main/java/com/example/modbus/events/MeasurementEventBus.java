package com.example.modbus.events;

import java.util.function.Consumer;

/**
 * Simple internal event bus abstraction used to distribute measurement events.
 */
public interface MeasurementEventBus {

    /**
     * Publish a newly captured measurement event.
     */
    void publish(MeasurementEvent event);

    /**
     * Subscribe to measurement events. The returned {@link AutoCloseable} removes the subscription when closed.
     */
    AutoCloseable subscribe(Consumer<MeasurementEvent> consumer);
}
