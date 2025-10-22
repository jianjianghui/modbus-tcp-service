package com.example.modbus.events;

import com.example.modbus.polling.MeasurementDefinition;
import com.example.modbus.polling.MeasurementType;

import java.util.Objects;

/**
 * Represents a single measurement captured from a Modbus device during a poll cycle.
 */
public final class MeasurementSample {

    private final MeasurementDefinition definition;
    private final Object value;

    public MeasurementSample(MeasurementDefinition definition, Object value) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.value = Objects.requireNonNull(value, "value");
    }

    public MeasurementDefinition getDefinition() {
        return definition;
    }

    public Object getValue() {
        return value;
    }

    public MeasurementType getType() {
        return definition.getType();
    }

    public boolean isBooleanScalar() {
        return value instanceof Boolean;
    }

    public boolean isBooleanArray() {
        return value instanceof boolean[];
    }

    public boolean isRegisterScalar() {
        return value instanceof Integer;
    }

    public boolean isRegisterArray() {
        return value instanceof int[];
    }

    public boolean getBooleanValue() {
        if (!isBooleanScalar()) {
            throw new IllegalStateException("Measurement is not a boolean scalar");
        }
        return (Boolean) value;
    }

    public boolean[] getBooleanArray() {
        if (!isBooleanArray()) {
            throw new IllegalStateException("Measurement is not a boolean array");
        }
        return ((boolean[]) value).clone();
    }

    public int getRegisterValue() {
        if (!isRegisterScalar()) {
            throw new IllegalStateException("Measurement is not a register scalar");
        }
        return (Integer) value;
    }

    public int[] getRegisterArray() {
        if (!isRegisterArray()) {
            throw new IllegalStateException("Measurement is not a register array");
        }
        return ((int[]) value).clone();
    }
}
