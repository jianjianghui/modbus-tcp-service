package com.example.modbus.model;

import java.time.Instant;

public class HelloResponse {
    private String message;
    private Instant timestamp;

    public HelloResponse() {
    }

    public HelloResponse(String message, Instant timestamp) {
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
