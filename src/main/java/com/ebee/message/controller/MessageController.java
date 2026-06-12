package com.ebee.message.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.message.dto.MessageRequest;
import com.ebee.message.dto.MessageResponse;
import com.ebee.message.service.MessageService;
import com.ebee.user.entity.User;
import com.ebee.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class MessageController {

    private final MessageService messageService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            Authentication authentication,
            @Valid @RequestBody MessageRequest request) {
        User admin = getUser(authentication);
        MessageResponse response = messageService.sendMessage(admin, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Message sent successfully", response));
    }

    @GetMapping("/sent")
    public ResponseEntity<ApiResponse<Page<MessageResponse>>> getSentMessages(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 200);
        User admin = getUser(authentication);
        Page<MessageResponse> messages = messageService.getSentMessages(admin,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt")));
        return ResponseEntity.ok(ApiResponse.success("Sent messages retrieved", messages));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MessageResponse>> getMessage(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 200);
        User admin = getUser(authentication);
        MessageResponse response = messageService.getMessageWithRecipients(id, admin,
                PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success("Message retrieved", response));
    }

    private User getUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
