package com.example.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        log.info("Starting sample-gradle-jar");

        Map<String, String> data = new LinkedHashMap<>();
        data.put("message", "hello");
        data.put("status", "ok");

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(data);

        log.info("Serialized JSON: {}", json);
    }
}
