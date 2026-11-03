package com.toppaload.notification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class TeamsWebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TeamsWebhookDispatcher.class);

    private final RestClient restClient;
    private final String defaultWebhookUrl;

    public TeamsWebhookDispatcher(@Value("${app.teams.webhook-url:}") String defaultWebhookUrl) {
        this.restClient = RestClient.builder().build();
        this.defaultWebhookUrl = defaultWebhookUrl;
    }

    public boolean sendAdaptiveCard(String title, String summary, Map<String, Object> facts) {
        return sendAdaptiveCardToUrl(this.defaultWebhookUrl, title, summary, facts);
    }

    public boolean sendAdaptiveCardToUrl(String webhookUrl, String title, String summary, Map<String, Object> facts) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return false;
        }

        try {
            List<Map<String, String>> factsList = facts != null ? facts.entrySet().stream()
                    .map(e -> Map.of("title", e.getKey(), "value", String.valueOf(e.getValue())))
                    .toList() : List.of();

            Map<String, Object> cardContent = Map.of(
                    "$schema", "http://adaptivecards.io/schemas/adaptive-card.json",
                    "type", "AdaptiveCard",
                    "version", "1.4",
                    "body", List.of(
                            Map.of("type", "TextBlock", "text", "🔔 " + title, "weight", "Bolder", "size", "Medium"),
                            Map.of("type", "TextBlock", "text", summary, "wrap", true),
                            Map.of("type", "FactSet", "facts", factsList)
                    )
            );

            Map<String, Object> payload = Map.of(
                    "type", "message",
                    "attachments", List.of(
                            Map.of(
                                    "contentType", "application/vnd.microsoft.card.adaptive",
                                    "content", cardContent
                            )
                    )
            );

            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Sent Microsoft Teams alert: {}", title);
            return true;
        } catch (Exception e) {
            log.warn("Failed to dispatch Microsoft Teams webhook: {}", e.getMessage());
            return false;
        }
    }
}

