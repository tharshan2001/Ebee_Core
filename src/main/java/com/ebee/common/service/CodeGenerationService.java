package com.ebee.common.service;

import com.ebee.common.enums.District;
import com.ebee.common.repository.CodeSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class CodeGenerationService {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RANDOM_LENGTH = 6;
    private static final int MAX_RETRIES = 20;

    private final SecureRandom random = new SecureRandom();

    private final CodeSequenceRepository codeSequenceRepository;

    public String generateCode(District district) {
        String prefix = district != null ? district.name() : "XX";
        for (int i = 0; i < MAX_RETRIES; i++) {
            String code = prefix + "-" + randomChars();
            if (codeSequenceRepository.tryReserve(code)) {
                return code;
            }
        }
        throw new IllegalStateException(
                "Unable to generate unique code after " + MAX_RETRIES + " attempts for prefix " + prefix);
    }

    private String randomChars() {
        StringBuilder sb = new StringBuilder(RANDOM_LENGTH);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
