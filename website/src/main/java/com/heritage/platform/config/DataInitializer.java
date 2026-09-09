package com.heritage.platform.config;

import com.heritage.platform.entity.Category;
import com.heritage.platform.entity.User;
import com.heritage.platform.enums.UserRole;
import com.heritage.platform.repository.CategoryRepository;
import com.heritage.platform.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String adminNickname;

    public DataInitializer(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.username:}") String adminUsername,
            @Value("${app.bootstrap-admin.password:}") String adminPassword,
            @Value("${app.bootstrap-admin.nickname:Platform administrator}") String adminNickname) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername == null ? "" : adminUsername.trim();
        this.adminPassword = adminPassword == null ? "" : adminPassword;
        this.adminNickname = adminNickname == null || adminNickname.isBlank()
                ? "Platform administrator"
                : adminNickname.trim();
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0 && !adminUsername.isBlank() && !adminPassword.isBlank()) {
            userRepository.save(new User(
                    adminUsername,
                    passwordEncoder.encode(adminPassword),
                    adminNickname,
                    null,
                    UserRole.ADMIN,
                    Boolean.TRUE
            ));
        }

        List<Category> defaultCategories = List.of(
                Category.create("传统技艺", "手工艺、织染、雕刻等"),
                Category.create("传统戏曲", "戏曲、曲艺、说唱艺术"),
                Category.create("古建筑", "古城、古桥、古园林与历史建筑"),
                Category.create("民俗节庆", "节日仪式、民间风俗与庆典"),
                Category.create("其他", "其他文化遗产内容")
        );
        defaultCategories.stream()
                .filter(category -> !categoryRepository.existsByName(category.getName()))
                .forEach(categoryRepository::save);
    }
}
