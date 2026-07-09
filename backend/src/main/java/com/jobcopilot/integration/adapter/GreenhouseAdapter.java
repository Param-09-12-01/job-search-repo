package com.jobcopilot.integration.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobcopilot.config.AppProperties;
import com.jobcopilot.dto.posting.NormalizedJob;
import com.jobcopilot.entity.enums.JobSourceType;
import com.jobcopilot.integration.AbstractHttpJobSourceAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
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

    private final AppProperties.Integration.Greenhouse config;

    public GreenhouseAdapter(RestClient restClient, AppProperties properties) {
        super(restClient);
        this.config = properties.getIntegration().getGreenhouse();
    }

    @Override
    public JobSourceType type() {
        return JobSourceType.GREENHOUSE;
    }

    @Override
    public boolean validate() {
        return config.isEnabled() && !boards().isEmpty();
    }

    @Override
    public List<Object> fetchJobs() {
        List<Object> raw = new ArrayList<>();
        for (String board : boards()) {
            String url = "%s/%s/jobs?content=true".formatted(config.getBaseUrl(), board);
            try {
                JsonNode body = getJson(url);
                JsonNode jobs = body.get("jobs");
                if (jobs != null && jobs.isArray()) {
                    jobs.forEach(node -> raw.add(new BoardNode(board, node)));
                }
            } catch (Exception e) {
                // Isolate per-board failures so one bad board doesn't sink the rest.
                log.warn("Greenhouse board '{}' fetch failed: {}", board, e.getMessage());
            }
        }
        log.info("Greenhouse fetched {} raw postings across {} board(s)", raw.size(), boards().size());
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

    private List<String> boards() {
        if (config.getBoards() == null || config.getBoards().isBlank()) {
            return List.of();
        }
        return Arrays.stream(config.getBoards().split(","))
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
