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
 * Adapter for Greenhouse public job boards. One board token per company; multiple tokens are
 * configured via {@code app.integration.greenhouse.boards} (comma-separated).
 * Docs: https://developers.greenhouse.io/job-board.html
 */
@Slf4j
@Component
public class GreenhouseAdapter extends AbstractHttpJobSourceAdapter {

    private static final String SOURCE = JobSourceType.GREENHOUSE.name();
    private static final int MAX_DAYS_OLD = 15;

    private final AppProperties.Integration.Greenhouse config;
    private final SettingsService settingsService;
    private final ProfileService profileService;

    public GreenhouseAdapter(RestClient restClient, AppProperties properties,
                             SettingsService settingsService, ProfileService profileService) {
        super(restClient);
        this.config = properties.getIntegration().getGreenhouse();
        this.settingsService = settingsService;
        this.profileService = profileService;
    }

    @Override
    public JobSourceType type() {
        return JobSourceType.GREENHOUSE;
    }

    @Override
    public boolean validate() {
        boolean enabled = settingsService.getBooleanValue("integration.greenhouse.enabled", config.isEnabled());
        String boardsCsv = settingsService.getValue("integration.greenhouse.boards", config.getBoards());
        return enabled && !boards(boardsCsv).isEmpty();
    }

    @Override
    public List<Object> fetchJobs() {
        Profile profile = profileService.getProfileEntityOrNull();
        List<Object> raw = new ArrayList<>();
        String boardsCsv = settingsService.getValue("integration.greenhouse.boards", config.getBoards());
        for (String board : boards(boardsCsv)) {
            String url = "%s/%s/jobs?content=true".formatted(config.getBaseUrl(), board);
            try {
                JsonNode body = getJson(url);
                JsonNode jobs = body.get("jobs");
                if (jobs != null && jobs.isArray()) {
                    for (JsonNode node : jobs) {
                        String title = text(node, "title");
                        String description = stripHtml(text(node, "content"));
                        // Match by title OR by keywords in description
                        if (!matchesProfileTitles(title, profile) && !matchesProfileKeywords(title, description, profile)) {
                            continue;
                        }
                        // Filter by date (15 days)
                        String dateStr = text(node, "updated_at");
                        if (dateStr != null) {
                            var postedAt = parseIsoDate(dateStr);
                            if (postedAt != null && postedAt.isBefore(java.time.LocalDateTime.now(ZoneOffset.UTC).minusDays(MAX_DAYS_OLD))) {
                                continue;
                            }
                        }
                        raw.add(new BoardNode(board, node));
                    }
                }
            } catch (Exception e) {
                log.warn("Greenhouse board '{}' fetch failed: {}", board, e.getMessage());
            }
        }
        log.info("Greenhouse fetched {} postings across {} board(s) (filtered by profile + {} day limit)",
                raw.size(), boards(boardsCsv).size(), MAX_DAYS_OLD);
        return raw;
    }

    @Override
    protected List<NormalizedJob> doNormalize(List<Object> rawJobs) {
        List<NormalizedJob> normalized = new ArrayList<>();
        for (Object rawObj : rawJobs) {
            if (!(rawObj instanceof BoardNode boardNode)) {
                continue;
            }
            JsonNode node = boardNode.node();
            String externalId = node.hasNonNull("id") ? node.get("id").asText() : null;
            String title = text(node, "title");
            String url = text(node, "absolute_url");
            if (externalId == null || title == null || url == null) {
                continue;
            }
            String location = node.path("location").path("name").asText(null);
            String description = stripHtml(text(node, "content"));
            String company = capitalize(boardNode.board());

            normalized.add(new NormalizedJob(
                    SOURCE,
                    boardNode.board() + ":" + externalId,
                    title,
                    company,
                    location,
                    detectRemote(title, location, description),
                    null,
                    null,
                    null,
                    description,
                    url,
                    parseIsoDate(text(node, "updated_at"))
            ));
        }
        return normalized;
    }

    private List<String> boards(String boardsCsv) {
        if (boardsCsv == null || boardsCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(boardsCsv.split(","))
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

    /** Pairs a raw job node with the board token it came from (board becomes the company). */
    private record BoardNode(String board, JsonNode node) {
    }
}
