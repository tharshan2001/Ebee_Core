package com.ebee.common.util;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.function.Predicate;

@Service
public class SlugService {

    private static final int MAX_SLUG_LENGTH = 80;

    public String generate(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input must not be null or blank");
        }

        String normalized = Normalizer.normalize(input.trim(), Normalizer.Form.NFD);
        String ascii = normalized.replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("[\\s-]+", "-")
                .replaceAll("^-+|-+$", "");

        if (slug.length() > MAX_SLUG_LENGTH) {
            slug = slug.substring(0, MAX_SLUG_LENGTH).replaceAll("-+$", "");
        }

        return slug;
    }

    public String makeUnique(String baseSlug, Predicate<String> exists) {
        if (!exists.test(baseSlug)) {
            return baseSlug;
        }
        int suffix = 2;
        while (true) {
            String candidate = baseSlug + "-" + suffix;
            if (!exists.test(candidate)) {
                return candidate;
            }
            suffix++;
        }
    }
}
