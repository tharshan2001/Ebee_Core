package com.ebee.profile;

import com.ebee.common.response.ApiResponse;
import com.ebee.profile.dto.ProfilePhotoResponse;
import com.ebee.profile.dto.ProfileUpdateRequest;
import com.ebee.profile.dto.SettingsUpdateRequest;
import com.ebee.user.dto.UserProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(Authentication authentication) {
        UserProfileResponse response = profileService.getProfile(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved successfully", response));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            Authentication authentication,
            @Valid @RequestBody ProfileUpdateRequest request) {
        UserProfileResponse response = profileService.updateProfile(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", response));
    }

    @PutMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProfilePhotoResponse>> uploadPhoto(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("File is empty"));
        }
        ProfilePhotoResponse response = profileService.uploadPhoto(authentication.getName(), file);
        return ResponseEntity.ok(ApiResponse.success("Photo uploaded successfully", response));
    }

    @DeleteMapping("/photo")
    public ResponseEntity<ApiResponse<Void>> deletePhoto(Authentication authentication) {
        profileService.deletePhoto(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Photo deleted successfully"));
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateSettings(
            Authentication authentication,
            @Valid @RequestBody SettingsUpdateRequest request) {
        UserProfileResponse response = profileService.updateSettings(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("Settings updated successfully", response));
    }
}
