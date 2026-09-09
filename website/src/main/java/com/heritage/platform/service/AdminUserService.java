package com.heritage.platform.service;

import com.heritage.platform.common.BadRequestException;
import com.heritage.platform.common.ResourceNotFoundException;
import com.heritage.platform.dto.request.AdminUserRoleUpdateRequest;
import com.heritage.platform.dto.response.AdminUserPageResult;
import com.heritage.platform.dto.response.AdminUserSummaryResponse;
import com.heritage.platform.entity.User;
import com.heritage.platform.enums.PostStatus;
import com.heritage.platform.enums.UserRole;
import com.heritage.platform.repository.PostRepository;
import com.heritage.platform.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final AuthContextService authContextService;
    private final ContributorApplicationService contributorApplicationService;

    public AdminUserService(
            UserRepository userRepository,
            PostRepository postRepository,
            AuthContextService authContextService,
            ContributorApplicationService contributorApplicationService
    ) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.authContextService = authContextService;
        this.contributorApplicationService = contributorApplicationService;
    }

    @Transactional(readOnly = true)
    public List<AdminUserSummaryResponse> listUsers(String username, UserRole role, Boolean active) {
        authContextService.requireAdmin();

        String trimmedUsername = username == null ? "" : username.trim();
        List<User> users = userRepository.searchAdminUsers(trimmedUsername, role, active);

        return users.stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public AdminUserPageResult listUsersPage(String username, UserRole role, Boolean active, Integer page, Integer size) {
        authContextService.requireAdmin();

        String trimmedUsername = username == null ? "" : username.trim();
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 10 : size;
        if (pageNumber < 0) {
            pageNumber = 0;
        }
        if (pageSize <= 0) {
            pageSize = 10;
        }
        if (pageSize > 50) {
            pageSize = 50;
        }

        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<User> users = userRepository.searchAdminUsers(trimmedUsername, role, active, pageable);

        return new AdminUserPageResult(
                users.getContent().stream().map(this::toSummary).toList(),
                users.getNumber(),
                users.getSize(),
                users.getTotalElements(),
                users.getTotalPages(),
                users.hasPrevious(),
                users.hasNext()
        );
    }

    @Transactional
    public AdminUserSummaryResponse updateRole(Long userId, AdminUserRoleUpdateRequest request) {
        User admin = authContextService.requireAdmin();

        User user = findUser(userId);

        if (user.getRole() == UserRole.ADMIN) {
            throw new BadRequestException("Administrator roles cannot be changed.");
        }

        if (request.role() == UserRole.ADMIN) {
            throw new BadRequestException("Users cannot be promoted to administrator here.");
        }

        if (request.role() == user.getRole()) {
            throw new BadRequestException("This user already has that role.");
        }

        boolean validTransition = (user.getRole() == UserRole.USER && request.role() == UserRole.CONTRIBUTOR)
                || (user.getRole() == UserRole.CONTRIBUTOR && request.role() == UserRole.USER);
        if (!validTransition) {
            throw new BadRequestException("Only USER and CONTRIBUTOR roles can be adjusted here.");
        }

        user.changeRole(request.role());
        if (request.role() == UserRole.CONTRIBUTOR) {
            contributorApplicationService.approvePendingApplicationsForApplicant(user, admin);
        }
        return toSummary(user);
    }

    @Transactional
    public AdminUserSummaryResponse activate(Long userId) {
        authContextService.requireAdmin();
        User user = findUser(userId);
        validateManageableUserStatusTarget(user, false);

        if (Boolean.TRUE.equals(user.getActive())) {
            throw new BadRequestException("This user account is already active.");
        }

        user.activate();
        return toSummary(user);
    }

    @Transactional
    public AdminUserSummaryResponse deactivate(Long userId) {
        User admin = authContextService.requireAdmin();
        User user = findUser(userId);

        if (admin.getId().equals(user.getId())) {
            throw new BadRequestException("You cannot deactivate the administrator account that is currently signed in.");
        }

        validateManageableUserStatusTarget(user, true);

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new BadRequestException("This user account is already inactive.");
        }

        user.deactivate();
        return toSummary(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("The user could not be found."));
    }

    private void validateManageableUserStatusTarget(User user, boolean deactivating) {
        if (user.getRole() == UserRole.ADMIN) {
            throw new BadRequestException(deactivating
                    ? "Administrator accounts cannot be deactivated."
                    : "Administrator accounts cannot be activated here.");
        }
    }

    private AdminUserSummaryResponse toSummary(User user) {
        return new AdminUserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getRole(),
                user.getActive(),
                postRepository.countByAuthorIdAndStatus(user.getId(), PostStatus.PUBLISHED),
                user.getUpdatedAt()
        );
    }
}
