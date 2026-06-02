package com.ebee.notification.template;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationTemplateService {

    private static final String TEMPLATE_PREFIX = "classpath:templates/notification/";

    private final ResourceLoader resourceLoader;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String resolve(String templateName, Map<String, String> placeholders) {
        String raw = cache.computeIfAbsent(templateName, this::loadTemplate);
        String resolved = raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return resolved;
    }

    private String loadTemplate(String templateName) {
        Resource resource = resourceLoader.getResource(TEMPLATE_PREFIX + templateName);
        if (!resource.exists()) {
            log.warn("Notification template not found: {}", templateName);
            return "";
        }
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load notification template: {}", templateName, e);
            return "";
        }
    }
}
