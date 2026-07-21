package com.jobcopilot.integration.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobcopilot.config.AppProperties;
import com.jobcopilot.dto.posting.NormalizedJob;
import com.jobcopilot.entity.enums.JobSourceType;
import com.jobcopilot.entity.Profile;
import com.jobcopilot.integration.AbstractHttpJobSourceAdapter;
import com.jobcopilot.service.ProfileService;
import com.jobcopilot.service.SettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

/**
 * Adapter for Lever public postings. One company handle per Lever account; multiple handles are
 * configured via {@code app.integration.lever.companies} (comma-separated).
 * Docs: https://github.com/lever/postings-api
 */
@Slf4j
@Component
public class LeverAdapter extends AbstractHttpJobSourceAdapter {

    private static final String SOURCE = JobSourceType.LEVER.name();
    private static final int MAX_DAYS_OLD = 15;

    private final AppProperties.Integration.Lever config;
    private final SettingsService settingsService;
    private final ProfileService profileService;

    public LeverAdapter(RestClient restClient, AppProperties properties,
                        SettingsService settingsService, ProfileService profileService) {
        super(restClient);
        this.config = properties.getIntegration().getLever();
        this.settingsService = settingsService;
        this.profileService = profileService;
    }

    @Override
    public JobSourceType type() {
        return JobSourceType.LEVER;
    }

    @Override
    public boolean validate() {
        boolean enabled = settingsService.getBooleanValue("integration.lever.enabled", config.isEnabled());
        String companiesCsv = settingsService.getValue("integration.lever.companies", config.getCompanies());
        return enabled && !companies(companiesCsv).isEmpty();
    }

    @Override
    public List<Object> fetchJobs() {
        Profile profile = profileService.getProfileEntityOrNull();
        List<Object> raw = new ArrayList<>();
        String companiesCsv = settingsService.getValue("integration.lever.companies", config.getCompanies());
        for (String company : companies(companiesCsv)) {
            String url = "%s/%s?mode=json".formatted(config.getBaseUrl(), company);
            try {
                JsonNode body = getJson(url);
                if (body.isArray()) {
                    for (JsonNode node : body) {
                        String title = text(node, "text");
                        String descPlain = stripHtml(text(node, "descriptionPlain"));
                        String descHtml = stripHtml(text(node, "description"));
                        String description = descPlain != null ? descPlain : descHtml;
                        // Match by title OR by keywords in description
                        if (!matchesProfileTitles(title, profile) && !matchesProfileKeywords(title, description, profile)) {
                            continue;
                        }
                        // Filter by date (15 days)
                        JsonNode createdAt = node.get("createdAt");
                        if (createdAt != null && createdAt.isNumber()) {
                            var postedAt = java.time.LocalDateTime.ofEpochSecond(
                                    createdAt.asLong() / 1000, 0, java.time.ZoneOffset.UTC);
                            if (postedAt.isBefore(java.time.LocalDateTime.now(ZoneOffset.UTC).minusDays(MAX_DAYS_OLD))) {
                                continue;
                            }
                        }
                        raw.add(new CompanyNode(company, node));
                    }
                }
            } catch (Exception e) {
                log.warn("Lever company '{}' fetch failed: {}", company, e.getMessage());
            }
        }
        log.info("Lever fetched {} postings across {} company(ies) (filtered by profile + {} day limit)",
                raw.size(), companies(companiesCsv).size(), MAX_DAYS_OLD);
        return raw;
    }

    @Override
    protected List<NormalizedJob> doNormalize(List<Object> rawJobs) {
        List<NormalizedJob> normalized = new ArrayList<>();
        for (Object rawObj : rawJobs) {
            if (!(rawObj instanceof CompanyNode companyNode)) {
                continue;
            }
            JsonNode node = companyNode.node();
            String externalId = text(node, "id");
            String title = text(node, "text");
            String url = text(node, "hostedUrl");
            if (externalId == null || title == null || url == null) {
                continue;
            }
            JsonNode categories = node.get("categories");
            String location = categories != null ? categories.path("location").asText(null) : null;
            String commitment = categories != null ? categories.path("commitment").asText(null) : null;
            String description = stripHtml(text(node, "descriptionPlain") != null
                    ? text(node, "descriptionPlain") : text(node, "description"));
            boolean remote = detectRemote(title, location, description)
                    || (commitment != null && commitment.toLowerCase().contains("remote"));

            normalized.add(new NormalizedJob(
                    SOURCE,
                    companyNode.company() + ":" + externalId,
                    title,
                    capitalize(companyNode.company()),
                    location,
                    remote,
                    null,
                    null,
                    null,
                    description,
                    url,
                    parseEpochMillis(node.get("createdAt"))
            ));
        }
        return normalized;
    }

    private java.time.LocalDateTime parseEpochMillis(JsonNode createdAt) {
        if (createdAt == null || !createdAt.isNumber()) {
            return null;
        }
        long millis = createdAt.asLong();
        return java.time.LocalDateTime.ofEpochSecond(millis / 1000, 0, java.time.ZoneOffset.UTC);
    }

    private List<String> companies(String companiesCsv) {
        if (companiesCsv == null || companiesCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(companiesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String stripHtml(String html) {
        if (html == null) {
            return null;
        }
        return html.replaceAll("<[^>]+>", " ")
                .replaceAll("&[a-zA-Z]+;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Pairs a raw job node with the company handle it came from. */
    private record CompanyNode(String company, JsonNode node) {
    }
}
