package com.ebee.notification.template;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplateServiceTest {

    private final NotificationTemplateService service = new NotificationTemplateService(new DefaultResourceLoader());

    @Test
    void resolve_escapesHtmlInUserValue() {
        Map<String, String> placeholders = Map.of(
                "firstName", "<script>alert(1)</script>",
                "lastName", "Doe"
        );
        String resolved = service.resolve("test-template.html", placeholders);
        assertThat(resolved).doesNotContain("<script>alert(1)</script>");
        assertThat(resolved).contains("&lt;script&gt;");
        assertThat(resolved).contains("Doe");
    }

    @Test
    void resolve_replacesAllPlaceholders() {
        Map<String, String> placeholders = Map.of(
                "firstName", "Alice",
                "lastName", "Smith",
                "code", "AGT-001"
        );
        String resolved = service.resolve("test-template.html", placeholders);
        assertThat(resolved).contains("Alice");
        assertThat(resolved).contains("Smith");
        assertThat(resolved).contains("AGT-001");
        assertThat(resolved).doesNotContain("{{");
    }

    @Test
    void resolve_channel_resolvesChannelSpecificTemplate() {
        Map<String, String> placeholders = Map.of(
                "firstName", "Bob",
                "lastName", "Jones",
                "code", "SMS-001"
        );
        String resolved = service.resolve("test-template", NotificationChannel.SMS, placeholders);
        assertThat(resolved).contains("SMS code is SMS-001");
        assertThat(resolved).doesNotContain("{{");
    }

    @Test
    void resolve_channel_fallsBackToGenericHtml() {
        Map<String, String> placeholders = Map.of(
                "firstName", "Carol",
                "lastName", "Lee",
                "code", "FALLBACK"
        );
        String resolved = service.resolve("test-template", NotificationChannel.EMAIL, placeholders);
        assertThat(resolved).contains("Carol");
        assertThat(resolved).contains("FALLBACK");
    }

    @Test
    void resolve_channel_doesNotEscapeHtmlForSms() {
        Map<String, String> placeholders = Map.of(
                "firstName", "<b>Bob</b>",
                "lastName", "Smith",
                "code", "RAW"
        );
        String resolved = service.resolve("test-template", NotificationChannel.SMS, placeholders);
        assertThat(resolved).contains("<b>Bob</b>");
        assertThat(resolved).doesNotContain("&lt;");
    }

    @Test
    void resolve_channel_returnsEmptyForMissingTemplate() {
        Map<String, String> placeholders = Map.of("key", "val");
        String resolved = service.resolve("nonexistent-template", NotificationChannel.SMS, placeholders);
        assertThat(resolved).isEmpty();
    }
}
