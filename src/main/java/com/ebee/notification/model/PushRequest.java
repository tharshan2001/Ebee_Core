package com.ebee.notification.model;

import java.util.Map;

public record PushRequest(String token, String title, String body, Map<String, String> data) {
}
