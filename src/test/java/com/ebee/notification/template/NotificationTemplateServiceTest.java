package com.ebee.notification.template;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplateServiceTest {

    @Test
    void resolve_escapesHtmlInUserValue() {
        // A user-controlled value containing HTML must be escaped so the
        // rendered email body cannot execute a <script> tag injected via
        // firstName / lastName / etc.
        NotificationTemplateService service = new NotificationTemplateService(new DefaultResourceLoader());
        Map<String, String> placeholders = Map.of(
                "firstName", "<script>alert(1)</script>",
                "lastName", "Doe"
        );
        String resolved = service.resolve("test-template.html", placeholders);
        // The literal <script> tag must not survive unescaped.
        assertThat(resolved).doesNotContain("<script>alert(1)</script>");
        assertThat(resolved).contains("&lt;script&gt;");
        assertThat(resolved).contains("Doe");
    }

    @Test
    void resolve_replacesAllPlaceholders() {
        NotificationTemplateService service = new NotificationTemplateService(new DefaultResourceLoader());
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
}
