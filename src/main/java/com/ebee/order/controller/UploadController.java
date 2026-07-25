package com.ebee.order.controller;

import com.ebee.common.port.StoragePort;
import com.ebee.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final StoragePort storagePort;

    @PostMapping(value = "/payment-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadPaymentProof(
            @RequestParam("file") MultipartFile file) {
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "payment-proof";
        String url = storagePort.uploadFile(file, "payment-proofs", fileName);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Proof uploaded successfully", Map.of("url", url)));
    }
}
