package com.jobcopilot.gmail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcopilot.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GmailService {

    private static final Logger log = LoggerFactory.getLogger(GmailService.class);
    private static final String GMAIL_API = "https://gmail.googleapis.com/gmail/v1/users/me";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final SettingsService settingsService;

    public List<GmailEmail> fetchSentEmails(LocalDateTime since) {
        String accessToken = getAccessToken();
        if (accessToken == null) {
            log.warn("Gmail not authorized — no access token available");
            return List.of();
        }

        RestClient restClient = restClientBuilder.baseUrl(GMAIL_API).build();
        String sinceSeconds = String.valueOf(since.toEpochSecond(ZoneOffset.UTC));

        List<GmailEmail> emails = new ArrayList<>();
        String pageToken = null;

        try {
            do {
                final String currentPageToken = pageToken;
                String listResponse = restClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder = uriBuilder.path("/messages")
                                    .queryParam("labelIds", "SENT")
                                    .queryParam("maxResults", "20")
                                    .queryParam("q", "after:" + sinceSeconds);
                            if (currentPageToken != null) {
                                uriBuilder = uriBuilder.queryParam("pageToken", currentPageToken);
                            }
                            return uriBuilder.build();
                        })
                        .header("Authorization", "Bearer " + accessToken)
                        .retrieve()
                        .body(String.class);

                log.debug("Gmail API list response: {}", listResponse);
                JsonNode listJson = objectMapper.readTree(listResponse);
                JsonNode messages = listJson.path("messages");

                for (JsonNode msg : messages) {
                    String msgId = msg.path("id").asText();
                    GmailEmail email = fetchMessage(restClient, accessToken, msgId);
                    if (email != null) {
                        emails.add(email);
                    }
                }

                pageToken = listJson.path("nextPageToken").asText(null);
            } while (pageToken != null && emails.size() < 50);

        } catch (Exception e) {
            log.error("Failed to fetch Gmail sent emails: {}", e.getMessage());
        }

        return emails;
    }

    private GmailEmail fetchMessage(RestClient restClient, String accessToken, String messageId) {
        try {
            String msgResponse = restClient.get()
                    .uri("/messages/" + messageId + "?format=full")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);

            JsonNode msgJson = objectMapper.readTree(msgResponse);
            JsonNode payload = msgJson.path("payload");

            String subject = extractHeader(payload, "Subject");
            String body = extractBody(payload);

            List<String> to = List.of();
            String toHeader = extractHeader(payload, "To");
            if (toHeader != null) {
                to = List.of(toHeader.split("\\s*,\\s*"));
            }

            long internalDate = msgJson.path("internalDate").asLong(0);
            LocalDateTime sentDate = internalDate > 0
                    ? LocalDateTime.ofInstant(Instant.ofEpochMilli(internalDate), ZoneOffset.UTC)
                    : LocalDateTime.now(ZoneOffset.UTC);

            return new GmailEmail(messageId, subject, body, to, sentDate);
        } catch (Exception e) {
            log.warn("Failed to fetch Gmail message {}: {}", messageId, e.getMessage());
            return null;
        }
    }

    private String extractHeader(JsonNode payload, String name) {
        JsonNode headers = payload.path("headers");
        if (headers.isArray()) {
            for (JsonNode header : headers) {
                if (name.equalsIgnoreCase(header.path("name").asText())) {
                    return header.path("value").asText(null);
                }
            }
        }
        return null;
    }

    private String extractBody(JsonNode payload) {
        String body = extractFromParts(payload);
        if (body != null) return body;
        JsonNode parts = payload.path("parts");
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                body = extractFromParts(part);
                if (body != null) return body;
                JsonNode subParts = part.path("parts");
                if (subParts.isArray()) {
                    for (JsonNode subPart : subParts) {
                        body = extractFromParts(subPart);
                        if (body != null) return body;
                    }
                }
            }
        }
        return "";
    }

    private String extractFromParts(JsonNode part) {
        String mimeType = part.path("mimeType").asText("");
        if ("text/plain".equals(mimeType)) {
            String data = part.path("body").path("data").asText(null);
            if (data != null) {
                return new String(Base64.getUrlDecoder().decode(data));
            }
        }
        return null;
    }

    private String getAccessToken() {
        String refreshToken = settingsService.getValue("gmail.refresh-token", null);
        if (refreshToken == null || refreshToken.isBlank()) return null;

        String clientId = settingsService.getValue("gmail.client-id", null);
        String clientSecret = settingsService.getValue("gmail.client-secret", null);
        if (clientId == null || clientSecret == null) return null;

        try {
            RestClient restClient = restClientBuilder.baseUrl(TOKEN_URL).build();
            String response = restClient.post()
                    .uri("")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("client_id=" + java.net.URLEncoder.encode(clientId, "UTF-8")
                            + "&client_secret=" + java.net.URLEncoder.encode(clientSecret, "UTF-8")
                            + "&refresh_token=" + java.net.URLEncoder.encode(refreshToken, "UTF-8")
                            + "&grant_type=refresh_token")
                    .retrieve()
                    .body(String.class);

            JsonNode json = objectMapper.readTree(response);
            return json.path("access_token").asText(null);
        } catch (Exception e) {
            log.error("Failed to refresh Gmail access token: {}. This usually means the stored refresh token is invalid. Try re-authorizing Gmail from the Dashboard.", e.getMessage());
            return null;
        }
    }

    public String generateAuthUrl() {
        String clientId = settingsService.getValue("gmail.client-id", null);
        if (clientId == null) return null;

        String redirectUri = settingsService.getValue("gmail.redirect-uri",
                "http://localhost:8080/api/sync/gmail/callback");

        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + clientId
                + "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri, java.nio.charset.StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=https://www.googleapis.com/auth/gmail.readonly"
                + "&access_type=offline"
                + "&prompt=consent";
    }

    public String exchangeCode(String code) {
        String clientId = settingsService.getValue("gmail.client-id", null);
        String clientSecret = settingsService.getValue("gmail.client-secret", null);
        if (clientId == null || clientSecret == null) return null;

        String redirectUri = settingsService.getValue("gmail.redirect-uri",
                "http://localhost:8080/api/sync/gmail/callback");

        try {
            RestClient restClient = restClientBuilder.baseUrl(TOKEN_URL).build();
            String response = restClient.post()
                    .uri("")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("client_id=" + java.net.URLEncoder.encode(clientId, "UTF-8")
                            + "&client_secret=" + java.net.URLEncoder.encode(clientSecret, "UTF-8")
                            + "&code=" + java.net.URLEncoder.encode(code, "UTF-8")
                            + "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri, "UTF-8")
                            + "&grant_type=authorization_code")
                    .retrieve()
                    .body(String.class);

            JsonNode json = objectMapper.readTree(response);
            String refreshToken = json.path("refresh_token").asText(null);
            if (refreshToken != null && !refreshToken.isBlank()) {
                settingsService.updateValue("gmail.refresh-token", refreshToken);
                return "Authorization successful! Refresh token stored.";
            }
            return "No refresh token in response. You may need to revoke access and re-authorize.";
        } catch (Exception e) {
            log.error("Failed to exchange Gmail auth code: {}", e.getMessage());
            return "Error: " + (e.getMessage() != null ? e.getMessage() : "Unknown error");
        }
    }

    public void disconnect() {
        settingsService.updateValue("gmail.refresh-token", "");
    }

    public String getProfileEmail() {
        String accessToken = getAccessToken();
        if (accessToken == null) return null;
        try {
            RestClient restClient = restClientBuilder.baseUrl(GMAIL_API).build();
            String response = restClient.get()
                    .uri("/profile")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
            return response;
        } catch (Exception e) {
            log.error("Failed to fetch Gmail profile: {}", e.getMessage());
            return null;
        }
    }

    public String diagnosticListMessages() {
        String accessToken = getAccessToken();
        if (accessToken == null) return "No token";
        try {
            RestClient restClient = restClientBuilder.baseUrl(GMAIL_API).build();
            long sinceEpoch = System.currentTimeMillis() / 1000 - 365 * 86400;

            var results = new java.util.LinkedHashMap<String, Object>();

            String body = restClient.get()
                    .uri(ub -> ub.path("/messages").queryParam("maxResults", "3").build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(String.class);
            results.put("no_filter", parseResult(body));

            body = restClient.get()
                    .uri(ub -> ub.path("/messages").queryParam("maxResults", "3").queryParam("labelIds", "SENT").build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(String.class);
            results.put("labelIds=SENT", parseResult(body));

            body = restClient.get()
                    .uri(ub -> ub.path("/messages").queryParam("maxResults", "3").queryParam("q", "in:sent").build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(String.class);
            results.put("q=in:sent", parseResult(body));

            body = restClient.get()
                    .uri(ub -> ub.path("/messages").queryParam("maxResults", "3").queryParam("labelIds", "SENT").queryParam("q", "after:" + sinceEpoch).build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(String.class);
            results.put("labelIds=SENT+q=after", parseResult(body));

            body = restClient.get()
                    .uri(ub -> ub.path("/messages").queryParam("maxResults", "3").queryParam("q", "after:" + sinceEpoch).build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(String.class);
            results.put("q=after_only", parseResult(body));

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(results);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private java.util.Map<String, Object> parseResult(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            return java.util.Map.of(
                    "resultSizeEstimate", json.path("resultSizeEstimate").asInt(0),
                    "messages", json.path("messages").size()
            );
        } catch (Exception e) {
            return java.util.Map.of("error", e.getMessage());
        }
    }
}
