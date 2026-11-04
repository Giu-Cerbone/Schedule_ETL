package com.scheduletl.service;

import com.scheduletl.dto.CreateUserProfileRequest;
import com.scheduletl.dto.UserProfileResponse;
import com.scheduletl.domain.UserProfile;
import com.scheduletl.repository.UserProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    @Transactional(readOnly = true)
    public List<UserProfileResponse> findAll() {
        return userProfileRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserProfileResponse findById(Long id) {
        return userProfileRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("User profile not found: " + id));
    }

    @Transactional
    public UserProfileResponse create(CreateUserProfileRequest request) {
        UserProfile entity = new UserProfile();
        entity.setUsername(request.username());
        entity.setFullName(request.fullName());
        entity.setEmail(request.email());
        return toResponse(userProfileRepository.save(entity));
    }

    private UserProfileResponse toResponse(UserProfile profile) {
        return new UserProfileResponse(profile.getId(), profile.getUsername(), profile.getFullName(), profile.getEmail());
    }
}
