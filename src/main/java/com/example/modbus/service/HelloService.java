package com.example.modbus.service;

import com.example.modbus.model.HelloResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class HelloService {
    public HelloResponse hello(String name) {
        String target = (name == null || name.trim().isEmpty()) ? "Modbus" : name.trim();
        return new HelloResponse("Hello, " + target + "!", Instant.now());
    }
}
