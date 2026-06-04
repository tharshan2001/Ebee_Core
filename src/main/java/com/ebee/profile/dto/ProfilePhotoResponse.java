package com.ebee.profile.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProfilePhotoResponse {

    private String profilePictureUrl;
    private String profilePictureName;
    private String profilePictureType;
}
