package com.jobcopilot.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobcopilot.dto.posting.NormalizedJob;
import com.jobcopilot.exception.IntegrationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Shared helpers for HTTP-based adapters: GET-to-JSON, safe text extraction, remote detection,
 * and date parsing. Concrete adapters extend this to keep provider classes focused on mapping.
 */
@Slf4j
public abstract class AbstractHttpJobSourceAdapter implements JobSourceAdapter {

    protected final RestClient restClient;

    protected AbstractHttpJobSourceAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    /** Perform a GET and parse the body as a Jackson tree. */
    protected JsonNode getJson(String url) {
        try {
            JsonNode body = restClient.get().uri(url).retrieve().body(JsonNode.class);
            if (body == null) {
                throw new IntegrationException("Empty response from " + type());
            }
            return body;
        } catch (IntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new IntegrationException("HTTP call failed for " + type() + ": " + e.getMessage(), e);
        }
    }

    protected String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    protected Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.asInt();
    }

    protected boolean detectRemote(String title, String location, String description) {
        String haystack = (nullToEmpty(title) + " " + nullToEmpty(location) + " "
                + nullToEmpty(description)).toLowerCase();
        return haystack.contains("remote") || haystack.contains("work from home")
                || haystack.contains("wfh") || haystack.contains("anywhere");
    }

    protected LocalDateTime parseIsoDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // fall through to epoch-millis attempt
        }
        try {
            long epoch = Long.parseLong(value);
            return LocalDateTime.ofEpochSecond(epoch > 1_000_000_000_000L ? epoch / 1000 : epoch,
                    0, ZoneOffset.UTC);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    protected String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    protected abstract List<NormalizedJob> doNormalize(List<Object> rawJobs);

    @Override
    public List<NormalizedJob> normalize(List<Object> rawJobs) {
        if (rawJobs == null || rawJobs.isEmpty()) {
            return List.of();
        }
        return doNormalize(rawJobs);
    }
}
