package com.jobcopilot.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobcopilot.dto.posting.NormalizedJob;
import com.jobcopilot.exception.IntegrationException;
import com.jobcopilot.entity.Profile;
import com.jobcopilot.util.JsonListUtil;
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

    /** Filter jobs to only those posted within the given number of days. */
    protected List<NormalizedJob> filterRecent(List<NormalizedJob> jobs, int maxDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(maxDays);
        return jobs.stream()
                .filter(j -> j.postedAt() == null || j.postedAt().isAfter(cutoff))
                .toList();
    }

    /** Build a search query from the user's profile titles. */
    protected String buildProfileQuery(Profile profile, String fallback) {
        if (profile == null) {
            return fallback;
        }
        List<String> titles = JsonListUtil.fromJson(profile.getTitles());
        if (titles.isEmpty()) {
            return fallback;
        }
        return String.join(" ", titles);
    }

    /** Check if a job title matches any of the profile titles. */
    protected boolean matchesProfileTitles(String jobTitle, Profile profile) {
        if (profile == null || jobTitle == null) {
            return true;
        }
        List<String> titles = JsonListUtil.fromJson(profile.getTitles());
        if (titles.isEmpty()) {
            return true;
        }
        String lower = jobTitle.toLowerCase();
        return titles.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::toLowerCase)
                .anyMatch(t -> lower.contains(t) || t.contains(lower) || tokenOverlap(lower, t) >= 0.3);
    }

    protected double tokenOverlap(String a, String b) {
        String[] at = a.split("\\s+");
        String[] bt = b.split("\\s+");
        if (at.length == 0) {
            return 0.0;
        }
        long common = java.util.Arrays.stream(at)
                .filter(token -> token.length() > 2)
                .filter(token -> java.util.Arrays.asList(bt).contains(token))
                .count();
        return (double) common / at.length;
    }

    /** Get all profile keywords as a lowercase list. */
    protected List<String> getProfileKeywords(Profile profile) {
        if (profile == null) return List.of();
        List<String> kw = JsonListUtil.fromJson(profile.getKeywords());
        return kw.stream().filter(k -> k != null && !k.isBlank()).map(String::toLowerCase).toList();
    }

    /** Check if text (title + description) contains any of the profile keywords. */
    protected boolean matchesProfileKeywords(String title, String description, Profile profile) {
        if (profile == null) return true;
        List<String> keywords = getProfileKeywords(profile);
        if (keywords.isEmpty()) return true;
        String haystack = nullToEmpty(title).toLowerCase() + " " + nullToEmpty(description).toLowerCase();
        return keywords.stream().anyMatch(kw -> haystack.contains(kw));
    }

    /** Build a search query combining profile titles and top keywords for API search params. */
    protected String buildProfileSearchQuery(Profile profile, String fallback) {
        if (profile == null) return fallback;
        List<String> titles = JsonListUtil.fromJson(profile.getTitles());
        List<String> keywords = getProfileKeywords(profile);
        java.util.LinkedHashSet<String> terms = new java.util.LinkedHashSet<>();
        titles.stream().filter(t -> t != null && !t.isBlank()).forEach(terms::add);
        keywords.stream().limit(5).forEach(terms::add);
        return terms.isEmpty() ? fallback : String.join(" ", terms);
    }
}
