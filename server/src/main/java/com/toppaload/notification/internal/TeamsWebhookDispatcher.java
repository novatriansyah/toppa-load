package com.toppaload.notification.internal;

import com.toppaload.notification.TeamsNotificationGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class TeamsWebhookDispatcher implements TeamsNotificationGateway {

    private static final Logger log = LoggerFactory.getLogger(TeamsWebhookDispatcher.class);

    private final RestClient restClient;
    private final String defaultWebhookUrl;
    private final JdbcClient jdbc;

    public TeamsWebhookDispatcher(
            @Value("${app.teams.webhook-url:}") String defaultWebhookUrl,
            JdbcClient jdbc
    ) {
        this.restClient = RestClient.builder().build();
        this.defaultWebhookUrl = defaultWebhookUrl;
        this.jdbc = jdbc;
    }

    @Override
    public boolean sendAdaptiveCard(String title, String summary, Map<String, Object> facts) {
        String url = resolveWebhookUrl();
        return sendAdaptiveCardToUrl(url, title, summary, facts);
    }

    @Override
    public boolean sendTestCard(String webhookUrl) {
        String targetUrl = (webhookUrl != null && !webhookUrl.isBlank()) ? webhookUrl : resolveWebhookUrl();
        return sendAdaptiveCardToUrl(
                targetUrl,
                "Uji Coba Integrasi Microsoft Teams",
                "Koneksi webhook Microsoft Teams Toppa Load berhasil terverifikasi! Sistem siap mengirimkan peringatan beban kerja, batas waktu task, dan pengingat operasional.",
                Map.of(
                        "Aplikasi", "Toppa Load",
                        "Status", "TERHUBUNG (Active)",
                        "Format", "Adaptive Card 1.4"
                )
        );
    }

    public String resolveWebhookUrl() {
        if (this.defaultWebhookUrl != null && !this.defaultWebhookUrl.isBlank()) {
            return this.defaultWebhookUrl;
        }
        try {
            return jdbc.sql("SELECT teams_webhook_url FROM system_settings WHERE id = 1")
                    .query(rs -> rs.next() ? rs.getString("teams_webhook_url") : null);
        } catch (Exception e) {
            return null;
        }
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
