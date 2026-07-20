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
 * Adapter for RemoteOK's free public API. No API key required.
 * Fetches jobs matching profile titles, filtered to last 15 days.
 * Docs: https://remoteok.com/api
 */
@Slf4j
@Component
public class RemoteOkAdapter extends AbstractHttpJobSourceAdapter {

    private static final String SOURCE = JobSourceType.REMOTEOK.name();
    private static final int MAX_DAYS_OLD = 15;

    private final AppProperties.Integration.RemoteOk config;
    private final SettingsService settingsService;
    private final ProfileService profileService;

    public RemoteOkAdapter(RestClient restClient, AppProperties properties,
                           SettingsService settingsService, ProfileService profileService) {
        super(restClient);
        this.config = properties.getIntegration().getRemoteOk();
        this.settingsService = settingsService;
        this.profileService = profileService;
    }

    @Override
    public JobSourceType type() {
        return JobSourceType.REMOTEOK;
    }

    @Override
    public boolean validate() {
        return settingsService.getBooleanValue("integration.remoteok.enabled", config.isEnabled());
    }

    @Override
    public List<Object> fetchJobs() {
        Profile profile = profileService.getProfileEntityOrNull();
        List<Object> raw = new ArrayList<>();
        try {
            JsonNode body = getJson(config.getBaseUrl());
            if (body != null && body.isArray()) {
                for (JsonNode node : body) {
                    if (!node.hasNonNull("id")) {
                        continue;
                    }
                    String position = text(node, "position");
                    String description = text(node, "description");
                    // Match by title OR by keywords in description
                    if (!matchesProfileTitles(position, profile) && !matchesProfileKeywords(position, description, profile)) {
                        continue;
                    }
                    // Filter by date (15 days)
                    String dateStr = text(node, "date");
                    if (dateStr != null) {
                        var postedAt = parseIsoDate(dateStr);
                        if (postedAt != null && postedAt.isBefore(java.time.LocalDateTime.now().minusDays(MAX_DAYS_OLD))) {
                            continue;
                        }
                    }
                    raw.add(node);
                }
            }
        } catch (Exception e) {
            throw new IntegrationException("RemoteOK fetch failed: " + e.getMessage(), e);
        }
        log.info("RemoteOK fetched {} postings (filtered by profile titles + {} day limit)", raw.size(), MAX_DAYS_OLD);
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
            String title = text(node, "position");
            String url = text(node, "url");
            if (externalId == null || title == null || url == null) {
                continue;
            }
            String company = text(node, "company");
            String location = text(node, "location");
            if (location == null || location.isBlank()) {
                location = "Remote";
            }
            String description = text(node, "description");

            Integer salaryMin = integer(node, "salary_min");
            Integer salaryMax = integer(node, "salary_max");

            normalized.add(new NormalizedJob(
                    SOURCE,
                    externalId,
                    title,
                    company,
                    location,
                    true,
                    formatSalary(salaryMin, salaryMax),
                    salaryMin,
                    salaryMax,
                    description,
                    url,
                    parseIsoDate(text(node, "date"))
            ));
        }
        return normalized;
    }

    private String formatSalary(Integer min, Integer max) {
        if (min == null && max == null) {
            return null;
        }
        if (min != null && max != null) {
            return "$%,d - $%,d".formatted(min, max);
        }
        return "$%,d".formatted(min != null ? min : max);
    }
}
