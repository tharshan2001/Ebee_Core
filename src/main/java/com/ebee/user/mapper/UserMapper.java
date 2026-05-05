package com.ebee.user.mapper;

import com.ebee.user.dto.UserProfileResponse;
import com.ebee.user.dto.UserRequest;
import com.ebee.user.dto.UserResponse;
import com.ebee.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toEntity(UserRequest request);

    UserResponse toResponse(User user);

    UserProfileResponse toProfileResponse(User user);

    List<UserResponse> toResponseList(List<User> users);
}
