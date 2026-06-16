package com.ebee.notification.template;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

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

    /**
     * Resolve a template by replacing every {@code {{key}}} with the
     * corresponding entry from {@code placeholders}. Substitution values
     * are HTML-escaped via {@link HtmlUtils#htmlEscape(String)} so that
     * a malicious value like {@code <script>alert(1)</script>} cannot
     * break out of the surrounding HTML when the template is rendered
     * into an email. This is the only safe default for templates that
     * include user-controlled data; if a template needs to embed
     * pre-escaped HTML it should use a different placeholder convention
     * (e.g. {@code {{rawKey}}}) which this resolver does not provide.
     */
    public String resolve(String templateName, Map<String, String> placeholders) {
        String raw = loadWithCache(templateName);
        String resolved = raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String safe = HtmlUtils.htmlEscape(entry.getValue());
            resolved = resolved.replace("{{" + entry.getKey() + "}}", safe);
        }
        return resolved;
    }

    /**
     * Resolve a template for a specific {@link NotificationChannel}.
     * <p>Resolution order:</p>
     * <ol>
     *   <li>{@code {templateName}-{channel}.{ext}} (e.g. {@code order-created-customer-email.html})</li>
     *   <li>{@code {templateName}.html} (generic fallback, backward compat)</li>
     * </ol>
     * <p>Placeholder values are HTML-escaped for {@code EMAIL} channel;
     * {@code SMS} values are substituted as-is (plain text).</p>
     */
    public String resolve(String templateName, NotificationChannel channel, Map<String, String> placeholders) {
        String channelSuffix = "-" + channel.name().toLowerCase();
        String ext = channel == NotificationChannel.EMAIL ? ".html" : ".txt";
        String channelFile = templateName + channelSuffix + ext;

        String content = loadWithCache(channelFile);
        if (content.isEmpty()) {
            String fallback = templateName + ".html";
            content = loadWithCache(fallback);
            if (content.isEmpty()) {
                log.warn("No template found for {} / channel={} (tried {} and {})",
                        templateName, channel, channelFile, fallback);
                return "";
            }
        }

        String resolved = content;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = channel == NotificationChannel.EMAIL
                    ? HtmlUtils.htmlEscape(entry.getValue())
                    : entry.getValue();
            resolved = resolved.replace("{{" + entry.getKey() + "}}", value);
        }
        return resolved;
    }

    private String loadWithCache(String fileName) {
        return cache.computeIfAbsent(fileName, this::loadTemplate);
    }

    private String loadTemplate(String fileName) {
        Resource resource = resourceLoader.getResource(TEMPLATE_PREFIX + fileName);
        if (!resource.exists()) {
            return "";
        }
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load notification template: {}", fileName, e);
            return "";
        }
    }
}
