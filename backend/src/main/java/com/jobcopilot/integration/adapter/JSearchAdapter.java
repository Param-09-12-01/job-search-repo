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
import com.jobcopilot.util.JsonListUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the JSearch API (RapidAPI). Aggregates postings from many boards.
 * Docs: https://rapidapi.com/letscrape-6bRBa3QguO5/api/jsearch
 */
@Slf4j
@Component
public class JSearchAdapter extends AbstractHttpJobSourceAdapter {

    private static final String SOURCE = JobSourceType.JSEARCH.name();

    private final AppProperties.Integration.JSearch config;
    private final ProfileService profileService;
    private final SettingsService settingsService;

    public JSearchAdapter(RestClient restClient, AppProperties properties, ProfileService profileService,
                          SettingsService settingsService) {
        super(restClient);
        this.config = properties.getIntegration().getJsearch();
        this.profileService = profileService;
        this.settingsService = settingsService;
    }

    @Override
    public JobSourceType type() {
        return JobSourceType.JSEARCH;
    }

    @Override
    public boolean validate() {
        boolean enabled = settingsService.getBooleanValue("integration.jsearch.enabled", config.isEnabled());
        String apiKey = settingsService.getValue("integration.jsearch.api-key", config.getApiKey());
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    @Override
    public List<Object> fetchJobs() {
        return fetchJobs(null);
    }

    @Override
    public List<Object> fetchJobs(LocalDateTime since) {
        List<Object> raw = new ArrayList<>();
        String apiKey = settingsService.getValue("integration.jsearch.api-key", config.getApiKey());
        UriComponentsBuilder urlBuilder = UriComponentsBuilder
                .fromHttpUrl(config.getBaseUrl())
                .path("/search-v2")
                .queryParam("query", buildQuery())
                .queryParam("page", 1)
                .queryParam("num_pages", 1);
        if (since != null) {
            long days = ChronoUnit.DAYS.between(since, LocalDateTime.now(ZoneOffset.UTC));
            int postedDays = postedParam(Math.max((int) days, 1));
            urlBuilder.queryParam("posted", postedDays);
        }
        String url = urlBuilder.build().toUriString();
        try {
            JsonNode body = restClient.get()
                    .uri(url)
                    .header("X-RapidAPI-Key", apiKey)
                    .header("X-RapidAPI-Host", config.getHost())
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new IntegrationException("Empty response from JSearch");
            }
            JsonNode data = body.get("data");
            if (data == null) {
                throw new IntegrationException("JSearch returned no data");
            }
            // v2 wraps jobs under data.jobs; fall back to data as array for v1 compat
            JsonNode jobs = data.has("jobs") ? data.get("jobs") : data;
            if (jobs != null && jobs.isArray()) {
                for (JsonNode node : jobs) {
                    String dateStr = text(node, "job_posted_at_datetime_utc");
                    if (dateStr != null) {
                        var postedAt = parseIsoDate(dateStr);
                        if (postedAt != null && postedAt.isBefore(java.time.LocalDateTime.now(ZoneOffset.UTC).minusDays(15))) {
                            continue;
                        }
                    }
                    raw.add(node);
                }
            }
        } catch (IntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new IntegrationException("JSearch fetch failed: " + e.getMessage(), e);
        }
        log.info("JSearch fetched {} raw postings", raw.size());
        return raw;
    }

    @Override
    protected List<NormalizedJob> doNormalize(List<Object> rawJobs) {
        List<NormalizedJob> normalized = new ArrayList<>();
        for (Object rawObj : rawJobs) {
            if (!(rawObj instanceof JsonNode node)) {
                continue;
            }
            String externalId = text(node, "job_id");
            String title = text(node, "job_title");
            String url = text(node, "job_apply_link");
            if (externalId == null || title == null || url == null) {
                continue;
            }
            String company = text(node, "employer_name");
            String city = text(node, "job_city");
            String country = text(node, "job_country");
            String location = joinLocation(city, country);
            String description = text(node, "job_description");
            boolean remote = node.path("job_is_remote").asBoolean(false)
                    || detectRemote(title, location, description);
            Integer salaryMin = integer(node, "job_min_salary");
            Integer salaryMax = integer(node, "job_max_salary");

            normalized.add(new NormalizedJob(
                    SOURCE,
                    externalId,
                    title,
                    company,
                    location,
                    remote,
                    formatSalary(salaryMin, salaryMax),
                    salaryMin,
                    salaryMax,
                    description,
                    url,
                    parseIsoDate(text(node, "job_posted_at_datetime_utc"))
            ));
        }
        return normalized;
    }

    private String buildQuery() {
        Profile profile = profileService.getProfileEntityOrNull();
        if (profile != null) {
            List<String> titles = JsonListUtil.fromJson(profile.getTitles());
            if (!titles.isEmpty()) {
                return String.join(" ", titles);
            }
        }
        return "software developer";
    }

    private String joinLocation(String city, String country) {
        if (city != null && country != null) {
            return city + ", " + country;
        }
        return city != null ? city : country;
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

    /** Map a day count to the nearest JSearch posted parameter value (1, 3, 7, 14, 30). */
    private static int postedParam(int days) {
        if (days <= 1) return 1;
        if (days <= 3) return 3;
        if (days <= 7) return 7;
        if (days <= 14) return 14;
        return 30;
    }
}
