package com.scheduletl.service;

import com.scheduletl.domain.UserGroup;
import com.scheduletl.dto.CreateUserGroupRequest;
import com.scheduletl.dto.UserGroupResponse;
import com.scheduletl.repository.UserGroupRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserGroupService {

    private final UserGroupRepository userGroupRepository;

    @Transactional(readOnly = true)
    public List<UserGroupResponse> findAll() {
        return userGroupRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserGroupResponse findById(Long id) {
        return userGroupRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + id));
    }

    @Transactional
    public UserGroupResponse create(CreateUserGroupRequest request) {
        UserGroup entity = new UserGroup();
        entity.setName(request.name());
        entity.setDescription(request.description());
        return toResponse(userGroupRepository.save(entity));
    }

    private UserGroupResponse toResponse(UserGroup group) {
        return new UserGroupResponse(group.getId(), group.getName(), group.getDescription());
    }
}
