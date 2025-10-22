package com.example.modbus.polling;

import java.util.Objects;

/**
 * Describes a Modbus data point that should be polled periodically.
 */
public final class MeasurementDefinition {

    private final String id;
    private final MeasurementType type;
    private final int address;
    private final int count;

    public MeasurementDefinition(String id, MeasurementType type, int address) {
        this(id, type, address, 1);
    }

    public MeasurementDefinition(String id, MeasurementType type, int address, int count) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        if (address < 0) {
            throw new IllegalArgumentException("address must be >= 0");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count must be >= 1");
        }
        this.address = address;
        this.count = count;
    }

    public String getId() {
        return id;
    }

    public MeasurementType getType() {
        return type;
    }

    public int getAddress() {
        return address;
    }

    public int getCount() {
        return count;
    }

    public int getEndExclusive() {
        return address + count;
    }

    public static MeasurementDefinition coil(String id, int address) {
        return new MeasurementDefinition(id, MeasurementType.COIL, address, 1);
    }

    public static MeasurementDefinition coilRange(String id, int address, int count) {
        return new MeasurementDefinition(id, MeasurementType.COIL, address, count);
    }

    public static MeasurementDefinition discreteInput(String id, int address) {
        return new MeasurementDefinition(id, MeasurementType.DISCRETE_INPUT, address, 1);
    }

    public static MeasurementDefinition discreteInputRange(String id, int address, int count) {
        return new MeasurementDefinition(id, MeasurementType.DISCRETE_INPUT, address, count);
    }

    public static MeasurementDefinition holdingRegister(String id, int address) {
        return new MeasurementDefinition(id, MeasurementType.HOLDING_REGISTER, address, 1);
    }

    public static MeasurementDefinition holdingRegisterRange(String id, int address, int count) {
        return new MeasurementDefinition(id, MeasurementType.HOLDING_REGISTER, address, count);
    }

    public static MeasurementDefinition inputRegister(String id, int address) {
        return new MeasurementDefinition(id, MeasurementType.INPUT_REGISTER, address, 1);
    }

    public static MeasurementDefinition inputRegisterRange(String id, int address, int count) {
        return new MeasurementDefinition(id, MeasurementType.INPUT_REGISTER, address, count);
    }
}
