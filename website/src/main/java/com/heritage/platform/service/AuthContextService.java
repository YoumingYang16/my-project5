package com.heritage.platform.service;

import com.heritage.platform.common.ForbiddenException;
import com.heritage.platform.common.ResourceNotFoundException;
import com.heritage.platform.entity.User;
import com.heritage.platform.enums.UserRole;
import com.heritage.platform.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuthContextService {

    private static final Logger logger = LoggerFactory.getLogger(AuthContextService.class);
    
    private final UserRepository userRepository;

    public AuthContextService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireActiveUser() {
        Long userId = resolveCurrentUserId();
        logger.info("requireActiveUser called - userId: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("The user could not be found."));

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new ForbiddenException("This account has been disabled.");
        }

        return user;
    }

    public User requireAdmin() {
        User user = requireActiveUser();
        if (user.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Only administrators can perform this action.");
        }
        return user;
    }

    public User requireContributor() {
        User user = requireActiveUser();
        if (user.getRole() != UserRole.CONTRIBUTOR && user.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Only contributors can perform this action.");
        }
        return user;
    }

    private Long resolveCurrentUserId() {
        logger.info("resolveCurrentUserId called");
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            logger.error("Could not get ServletRequestAttributes");
            throw new ForbiddenException("The current request context is unavailable.");
        }

        HttpServletRequest request = servletAttributes.getRequest();
        Long userId = (Long) request.getAttribute("userId");
        logger.info("userId from request attribute: {}", userId);
        
        if (userId == null) {
            logger.error("userId is null");
            throw new ForbiddenException("Please sign in again before continuing.");
        }
        return userId;
    }
}
