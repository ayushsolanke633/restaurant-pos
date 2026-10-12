package com.restaurant.restaurantpos.config;

import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.repository.RoleRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Performs secure, one-time bootstrap of the platform SUPER_ADMIN account.
 * Reads credentials strictly from PLATFORM_SUPER_ADMIN_EMAIL and PLATFORM_SUPER_ADMIN_PASSWORD.
 *
 * Security Requirements:
 * 1. Validate configuration format and strength.
 * 2. Hash password with BCrypt.
 * 3. Never reset or overwrite an existing platform admin.
 * 4. Never log credentials or password hashes.
 */
@Component
@Order(10)
public class PlatformSuperAdminBootstrapper implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformSuperAdminBootstrapper.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${PLATFORM_SUPER_ADMIN_EMAIL:${platform.super-admin.email:#{null}}}")
    private String configuredEmail;

    @Value("${PLATFORM_SUPER_ADMIN_PASSWORD:${platform.super-admin.password:#{null}}}")
    private String configuredPassword;

    public PlatformSuperAdminBootstrapper(
            UserRepository userRepository,
            RoleRepository roleRepository,
            BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrapSuperAdmin(configuredEmail, configuredPassword);
    }

    public void bootstrapSuperAdmin(String emailInput, String passwordInput) {
        String email = resolveValue(emailInput, "PLATFORM_SUPER_ADMIN_EMAIL");
        String password = resolveValue(passwordInput, "PLATFORM_SUPER_ADMIN_PASSWORD");

        boolean emailBlank = (email == null || email.isBlank());
        boolean passwordBlank = (password == null || password.isBlank());

        if (emailBlank && passwordBlank) {
            log.info("Platform super admin bootstrap skipped: No credentials provided via environment or properties.");
            return;
        }

        if (emailBlank) {
            throw new IllegalStateException(
                    "Invalid platform super admin bootstrap configuration: PLATFORM_SUPER_ADMIN_PASSWORD was provided, but PLATFORM_SUPER_ADMIN_EMAIL is missing."
            );
        }

        if (passwordBlank) {
            throw new IllegalStateException(
                    "Invalid platform super admin bootstrap configuration: PLATFORM_SUPER_ADMIN_EMAIL was provided, but PLATFORM_SUPER_ADMIN_PASSWORD is missing."
            );
        }

        String cleanEmail = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(cleanEmail).matches()) {
            throw new IllegalStateException(
                    "Invalid platform super admin bootstrap configuration: PLATFORM_SUPER_ADMIN_EMAIL is not a valid email address."
            );
        }

        if (password.length() < 8) {
            throw new IllegalStateException(
                    "Invalid platform super admin bootstrap configuration: PLATFORM_SUPER_ADMIN_PASSWORD does not meet minimum length requirement (at least 8 characters)."
            );
        }

        // Never reset an existing platform admin
        if (userRepository.existsByRole_Name("SUPER_ADMIN")) {
            log.info("Platform super admin already exists in the system. Bootstrap skipped.");
            return;
        }

        if (userRepository.findByEmail(cleanEmail).isPresent()) {
            log.warn("A user with email '{}' already exists in the system. Platform super admin bootstrap skipped.", cleanEmail);
            return;
        }

        Role superAdminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setName("SUPER_ADMIN");
                    return roleRepository.save(newRole);
                });

        User superAdmin = new User();
        superAdmin.setName("Platform Super Admin");
        superAdmin.setEmail(cleanEmail);
        superAdmin.setRole(superAdminRole);
        superAdmin.setRestaurant(null); // Platform level, no tenant
        superAdmin.setIsActive(true);
        superAdmin.setPasswordHash(passwordEncoder.encode(password));

        userRepository.save(superAdmin);

        // Never log the password or password hash!
        log.info("Platform super admin successfully bootstrapped with email: {}", cleanEmail);
    }

    private String resolveValue(String propValue, String envKey) {
        if (propValue != null && !propValue.isBlank()) {
            return propValue;
        }
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isBlank()) {
            return envVal;
        }
        return null;
    }
}
