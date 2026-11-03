package com.toppaload.notification;

import java.util.Map;

public interface TeamsNotificationGateway {

    boolean sendAdaptiveCard(String title, String summary, Map<String, Object> facts);

    boolean sendTestCard(String webhookUrl);
}

