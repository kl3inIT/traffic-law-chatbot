package com.vn.traffic.chatbot.parameter.service;

import com.vn.traffic.chatbot.parameter.repo.AiParameterSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Centralized provider that reads and parses the active AiParameterSet YAML content.
 *
 * <p>Reads fresh from DB on every call. Callers should be aware that this
 * performs a DB read per invocation; caching can be added later if needed.
 *
 * <p>Security note (T-04-05): YAML content is parsed as data (Map&lt;String, Object&gt;),
 * never executed as code. Fallback values ensure degraded operation if YAML is malformed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveParameterSetProvider {

    private final AiParameterSetRepository repository;

    /**
     * Returns the parsed YAML content of the currently active parameter set.
     * Falls back to empty map if no active set exists or if the YAML cannot be parsed.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getActiveParams() {
        return repository.findByActiveTrue()
                .map(ps -> parseYaml(ps.getContent()))
                .orElse(Map.of());
    }

    /**
     * Returns a String value at the given dot-path (e.g. "messages.disclaimer").
     * Returns {@code fallback} if the path does not exist or the value is not a String.
     */
    public String getString(String dotPath, String fallback) {
        Object value = navigate(getActiveParams(), dotPath);
        if (value instanceof String s) {
            return s;
        }
        return fallback;
    }

    /**
     * Returns an int value at the given dot-path.
     * Returns {@code fallback} if the path does not exist or the value is not numeric.
     */
    public int getInt(String dotPath, int fallback) {
        Object value = navigate(getActiveParams(), dotPath);
        if (value instanceof Number n) {
            return n.intValue();
        }
        return fallback;
    }

    /**
     * Returns a double value at the given dot-path.
     * Returns {@code fallback} if the path does not exist or the value is not numeric.
     */
    public double getDouble(String dotPath, double fallback) {
        Object value = navigate(getActiveParams(), dotPath);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return fallback;
    }

    /**
     * Returns a list of maps at the given dot-path (e.g. "caseAnalysis.requiredFacts").
     * Returns an empty list if the path does not exist or the value is not a list.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getList(String dotPath) {
        Object value = navigate(getActiveParams(), dotPath);
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    // --- Private helpers ---

    /**
     * Navigates nested maps using a dot-separated path.
     * Returns null if any level is missing or not a Map.
     */
    @SuppressWarnings("unchecked")
    private Object navigate(Map<String, Object> params, String dotPath) {
        if (params == null || params.isEmpty() || dotPath == null || dotPath.isBlank()) {
            return null;
        }
        String[] segments = dotPath.split("\\.", -1);
        Object current = params;
        for (String segment : segments) {
            if (current instanceof Map<?, ?> map) {
                current = ((Map<String, Object>) map).get(segment);
            } else {
                return null;
            }
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseYaml(String content) {
        if (content == null || content.isBlank()) {
            return Map.of();
        }
        try {
            Yaml yaml = new Yaml();
            Object parsed = yaml.load(content);
            if (parsed instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            log.warn("ActiveParameterSetProvider: YAML root is not a map, returning empty params");
            return Map.of();
        } catch (Exception ex) {
            log.warn("ActiveParameterSetProvider: failed to parse YAML content: {}", ex.getMessage());
            return Map.of();
        }
    }
}
