package com.jobcopilot.integration.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobcopilot.config.AppProperties;
import com.jobcopilot.dto.posting.NormalizedJob;
import com.jobcopilot.entity.Profile;
import com.jobcopilot.entity.enums.JobSourceType;
import com.jobcopilot.integration.AbstractHttpJobSourceAdapter;
import com.jobcopilot.service.ProfileService;
import com.jobcopilot.service.SettingsService;
import com.jobcopilot.util.JsonListUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the Adzuna job search API.
 * Docs: https://developer.adzuna.com/
 */
@Slf4j
@Component
public class AdzunaAdapter extends AbstractHttpJobSourceAdapter {

    private static final String SOURCE = JobSourceType.ADZUNA.name();

    private final AppProperties.Integration.Adzuna config;
    private final ProfileService profileService;
    private final SettingsService settingsService;

    public AdzunaAdapter(RestClient restClient, AppProperties properties, ProfileService profileService,
                         SettingsService settingsService) {
        super(restClient);
        this.config = properties.getIntegration().getAdzuna();
        this.profileService = profileService;
        this.settingsService = settingsService;
    }

    @Override
    public JobSourceType type() {
        return JobSourceType.ADZUNA;
    }

    @Override
    public boolean validate() {
        boolean enabled = settingsService.getBooleanValue("integration.adzuna.enabled", config.isEnabled());
        String appId = settingsService.getValue("integration.adzuna.app-id", config.getAppId());
        String appKey = settingsService.getValue("integration.adzuna.app-key", config.getAppKey());
        return enabled && notBlank(appId) && notBlank(appKey);
    }

    @Override
    public List<Object> fetchJobs() {
        List<Object> raw = new ArrayList<>();
        String query = buildQuery();
        String appId = settingsService.getValue("integration.adzuna.app-id", config.getAppId());
        String appKey = settingsService.getValue("integration.adzuna.app-key", config.getAppKey());
        String country = settingsService.getValue("integration.adzuna.country", config.getCountry());
        String url = UriComponentsBuilder
                .fromHttpUrl(config.getBaseUrl())
                .pathSegment("jobs", country, "search", "1")
                .queryParam("app_id", appId)
                .queryParam("app_key", appKey)
                .queryParam("results_per_page", 50)
                .queryParam("what", query)
                .queryParam("content-type", "application/json")
                .build()
                .toUriString();

        JsonNode body = getJson(url);
        JsonNode results = body.get("results");
        if (results != null && results.isArray()) {
            for (JsonNode node : results) {
                // Filter by date (15 days)
                String dateStr = text(node, "created");
                if (dateStr != null) {
                    var postedAt = parseIsoDate(dateStr);
                    if (postedAt != null && postedAt.isBefore(java.time.LocalDateTime.now(ZoneOffset.UTC).minusDays(15))) {
                        continue;
                    }
                }
                raw.add(node);
            }
        }
        log.info("Adzuna fetched {} postings (15 day limit)", raw.size());
        return raw;
    }

    @Override
    protected List<NormalizedJob> doNormalize(List<Object> rawJobs) {
        List<NormalizedJob> normalized = new ArrayList<>();
        for (Object raw : rawJobs) {
            if (!(raw instanceof JsonNode node)) {
                continue;
            }
            String externalId = text(node, "id");
            String title = text(node, "title");
            String url = text(node, "redirect_url");
            if (externalId == null || title == null || url == null) {
                continue;
            }
            String company = node.path("company").path("display_name").asText(null);
            String location = node.path("location").path("display_name").asText(null);
            String description = text(node, "description");
            Integer salaryMin = node.hasNonNull("salary_min") ? (int) node.get("salary_min").asDouble() : null;
            Integer salaryMax = node.hasNonNull("salary_max") ? (int) node.get("salary_max").asDouble() : null;

            normalized.add(new NormalizedJob(
                    SOURCE,
                    externalId,
                    title,
                    company,
                    location,
                    detectRemote(title, location, description),
                    formatSalary(salaryMin, salaryMax),
                    salaryMin,
                    salaryMax,
                    description,
                    url,
                    parseIsoDate(text(node, "created"))
            ));
        }
        return normalized;
    }

    private String buildQuery() {
        Profile profile = profileService.getProfileEntityOrNull();
        if (profile != null) {
            List<String> titles = JsonListUtil.fromJson(profile.getTitles());
            if (!titles.isEmpty()) {
                return URLEncoder.encode(String.join(" ", titles), StandardCharsets.UTF_8);
            }
        }
        return "software developer";
    }

    private String formatSalary(Integer min, Integer max) {
        if (min == null && max == null) {
            return null;
        }
        if (min != null && max != null) {
            return "%,d - %,d".formatted(min, max);
        }
        return "%,d".formatted(min != null ? min : max);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
