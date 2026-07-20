package com.jobcopilot.integration.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobcopilot.config.AppProperties;
import com.jobcopilot.dto.posting.NormalizedJob;
import com.jobcopilot.entity.Profile;
import com.jobcopilot.entity.enums.JobSourceType;
import com.jobcopilot.exception.IntegrationException;
import com.jobcopilot.integration.AbstractHttpJobSourceAdapter;
import com.jobcopilot.service.ProfileService;
import com.jobcopilot.service.SettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for Findwork.dev API. Requires a free API key.
 * Fetches jobs matching profile titles, filtered to last 15 days.
 * Docs: https://findwork.dev/developers/
 */
@Slf4j
@Component
public class FindworkAdapter extends AbstractHttpJobSourceAdapter {

    private static final String SOURCE = JobSourceType.FINDWORK.name();
    private static final int MAX_DAYS_OLD = 15;

    private final AppProperties.Integration.Findwork config;
    private final SettingsService settingsService;
    private final ProfileService profileService;

    public FindworkAdapter(RestClient restClient, AppProperties properties,
                           SettingsService settingsService, ProfileService profileService) {
        super(restClient);
        this.config = properties.getIntegration().getFindwork();
        this.settingsService = settingsService;
        this.profileService = profileService;
    }

    @Override
    public JobSourceType type() {
        return JobSourceType.FINDWORK;
    }

    @Override
    public boolean validate() {
        boolean enabled = settingsService.getBooleanValue("integration.findwork.enabled", config.isEnabled());
        String apiKey = settingsService.getValue("integration.findwork.api-key", config.getApiKey());
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    @Override
    public List<Object> fetchJobs() {
        Profile profile = profileService.getProfileEntityOrNull();
        String apiKey = settingsService.getValue("integration.findwork.api-key", config.getApiKey());
        List<Object> raw = new ArrayList<>();

        String query = buildProfileSearchQuery(profile, "java developer backend engineer spring boot");
        String encoded = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        String url = "%s/jobs/?search=%s".formatted(config.getBaseUrl(), encoded);

        try {
            JsonNode body = restClient.get()
                    .uri(url)
                    .header("Authorization", "Token " + apiKey)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new IntegrationException("Empty response from Findwork");
            }
            JsonNode results = body.get("results");
            if (results != null && results.isArray()) {
                for (JsonNode node : results) {
                    // Findwork API search param already filters by relevance; skip title filter
                    // Filter by date (15 days)
                    String dateStr = text(node, "date_posted");
                    if (dateStr != null) {
                        var postedAt = parseIsoDate(dateStr);
                        if (postedAt != null && postedAt.isBefore(java.time.LocalDateTime.now().minusDays(MAX_DAYS_OLD))) {
                            continue;
                        }
                    }
                    raw.add(node);
                }
            }
        } catch (IntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new IntegrationException("Findwork fetch failed: " + e.getMessage(), e);
        }
        log.info("Findwork fetched {} postings (filtered by profile titles + {} day limit)", raw.size(), MAX_DAYS_OLD);
        return raw;
    }

    @Override
    protected List<NormalizedJob> doNormalize(List<Object> rawJobs) {
        List<NormalizedJob> normalized = new ArrayList<>();
        for (Object rawObj : rawJobs) {
            if (!(rawObj instanceof JsonNode node)) {
                continue;
            }
            String externalId = text(node, "id");
            String title = text(node, "role");
            String url = text(node, "url");
            if (externalId == null || title == null || url == null) {
                continue;
            }
            String company = text(node, "company_name");
            String location = text(node, "location");
            if (location == null || location.isBlank()) {
                location = "Remote";
            }
            String description = text(node, "text");

            normalized.add(new NormalizedJob(
                    SOURCE,
                    externalId,
                    title,
                    company,
                    location,
                    true,
                    null,
                    null,
                    null,
                    description,
                    url,
                    parseIsoDate(text(node, "date_posted"))
            ));
        }
        return normalized;
    }
}
