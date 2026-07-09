package com.jobcopilot.integration.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobcopilot.config.AppProperties;
import com.jobcopilot.dto.posting.NormalizedJob;
import com.jobcopilot.entity.Profile;
import com.jobcopilot.entity.enums.JobSourceType;
import com.jobcopilot.exception.IntegrationException;
import com.jobcopilot.integration.AbstractHttpJobSourceAdapter;
import com.jobcopilot.service.ProfileService;
import com.jobcopilot.util.JsonListUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

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

    public JSearchAdapter(RestClient restClient, AppProperties properties, ProfileService profileService) {
        super(restClient);
        this.config = properties.getIntegration().getJsearch();
        this.profileService = profileService;
    }

    @Override
    public JobSourceType type() {
        return JobSourceType.JSEARCH;
    }

    @Override
    public boolean validate() {
        return config.isEnabled() && config.getApiKey() != null && !config.getApiKey().isBlank();
    }

    @Override
    public List<Object> fetchJobs() {
        List<Object> raw = new ArrayList<>();
        String url = UriComponentsBuilder
                .fromHttpUrl(config.getBaseUrl())
                .path("/search")
                .queryParam("query", buildQuery())
                .queryParam("page", 1)
                .queryParam("num_pages", 1)
                .build()
                .toUriString();
        try {
            JsonNode body = restClient.get()
                    .uri(url)
                    .header("X-RapidAPI-Key", config.getApiKey())
                    .header("X-RapidAPI-Host", config.getHost())
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new IntegrationException("Empty response from JSearch");
            }
            JsonNode data = body.get("data");
            if (data != null && data.isArray()) {
                data.forEach(raw::add);
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
}
