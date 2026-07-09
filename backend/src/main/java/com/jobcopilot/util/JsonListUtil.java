package com.jobcopilot.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Serialization helpers for storing/reading JSON string-list columns
 * ({@code titles}, {@code locations}, {@code keywords}, {@code excludedCompanies}).
 */
@Slf4j
@UtilityClass
public class JsonListUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {
    };

    /** Serialize a list to a JSON array string; returns {@code "[]"} for null/empty. */
    public static String toJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(values);
        } catch (Exception e) {
            log.warn("Failed to serialize list to JSON: {}", e.getMessage());
            return "[]";
        }
    }

    /** Parse a JSON array string to a list; tolerant of null/blank/legacy comma-separated values. */
    public static List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) {
            try {
                return MAPPER.readValue(trimmed, LIST_TYPE);
            } catch (Exception e) {
                log.warn("Failed to parse JSON list, falling back to CSV: {}", e.getMessage());
            }
        }
        // Legacy / defensive fallback: treat as comma-separated.
        List<String> result = new ArrayList<>();
        for (String part : trimmed.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }
}
