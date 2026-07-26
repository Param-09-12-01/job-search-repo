package com.jobcopilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcopilot.gmail.GmailEmail;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    public EmailClassification classify(GmailEmail email) {
        String prompt = buildPrompt(email);
        String requestBody = buildRequestBody(prompt);

        try {
            int timeout = aiProperties.getOllama().getTimeoutSeconds();
            ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                    .withConnectTimeout(Duration.ofSeconds(timeout))
                    .withReadTimeout(Duration.ofSeconds(timeout));

            RestClient restClient = restClientBuilder
                    .baseUrl(aiProperties.getOllama().getBaseUrl())
                    .requestFactory(ClientHttpRequestFactories.get(settings))
                    .build();

            String response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return parseResponse(response);
        } catch (Exception e) {
            log.warn("Ollama classification failed for email '{}': {}", email.subject(), e.getMessage());
            return new EmailClassification(false, null, null, 0.0);
        }
    }

    private String buildPrompt(GmailEmail email) {
        return """
                You are analyzing an email sent from a job seeker's Gmail.
                
                Determine if this email is a job application the user sent to apply for a position.
                
                Reply ONLY with valid JSON (no markdown, no code fences):
                {
                  "isJobApplication": boolean,
                  "company": "company name or null",
                  "jobTitle": "job title or null",
                  "confidence": 0.0-1.0
                }
                
                Email subject: %s
                
                Email body: %s
                
                Recipients: %s
                """.formatted(
                email.subject(),
                truncate(email.body(), 3000),
                String.join(", ", email.recipients())
        );
    }

    private String buildRequestBody(String prompt) {
        try {
            Map<String, Object> message = Map.of("role", "user", "content", prompt);
            Map<String, Object> body = Map.of(
                    "model", aiProperties.getOllama().getModel(),
                    "messages", new Object[]{message},
                    "stream", false,
                    "format", "json"
            );
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Ollama request", e);
        }
    }

    private EmailClassification parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("message").path("content").asText("{}");
            JsonNode classification = objectMapper.readTree(content);

            boolean isJob = classification.path("isJobApplication").asBoolean(false);
            String company = classification.path("company").asText(null);
            String jobTitle = classification.path("jobTitle").asText(null);
            double confidence = classification.path("confidence").asDouble(0.0);

            return new EmailClassification(isJob, company, jobTitle, confidence);
        } catch (Exception e) {
            log.warn("Failed to parse Ollama response: {}", e.getMessage());
            return new EmailClassification(false, null, null, 0.0);
        }
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
