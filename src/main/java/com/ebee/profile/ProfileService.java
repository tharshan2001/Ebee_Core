package com.ebee.profile;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.service.S3Service;
import com.ebee.profile.dto.ProfilePhotoResponse;
import com.ebee.profile.dto.ProfileUpdateRequest;
import com.ebee.profile.dto.SettingsUpdateRequest;
import com.ebee.user.dto.UserProfileResponse;
import com.ebee.user.entity.User;
import com.ebee.user.mapper.UserMapper;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final S3Service s3Service;

    public UserProfileResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return userMapper.toProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(String email, ProfileUpdateRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }

        return userMapper.toProfileResponse(userRepository.save(user));
    }

    @Transactional
    public ProfilePhotoResponse uploadPhoto(String email, MultipartFile file) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        if (user.getProfilePictureUrl() != null) {
            s3Service.deleteFile(user.getProfilePictureUrl());
        }

        String url = s3Service.uploadFile(file, "profiles");
        user.setProfilePictureUrl(url);
        user.setProfilePictureName(file.getOriginalFilename());
        user.setProfilePictureType(file.getContentType());

        userRepository.save(user);

        return new ProfilePhotoResponse(url, file.getOriginalFilename(), file.getContentType());
    }

    @Transactional
    public void deletePhoto(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        if (user.getProfilePictureUrl() != null) {
            s3Service.deleteFile(user.getProfilePictureUrl());
        }

        user.setProfilePictureUrl(null);
        user.setProfilePictureName(null);
        user.setProfilePictureType(null);
        userRepository.save(user);
    }

    @Transactional
    public UserProfileResponse updateSettings(String email, SettingsUpdateRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        if (request.getLoginAlertsEnabled() != null) {
            user.setLoginAlertsEnabled(request.getLoginAlertsEnabled());
        }

        return userMapper.toProfileResponse(userRepository.save(user));
    }
}
