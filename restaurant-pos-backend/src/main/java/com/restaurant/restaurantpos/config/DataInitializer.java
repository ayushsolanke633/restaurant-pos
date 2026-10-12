package com.restaurant.restaurantpos.config;

import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.repository.RoleRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initRoles(RoleRepository roleRepository) {
        return args -> {
            List<String> requiredRoles = List.of("ADMIN", "WAITER", "KITCHEN");
            for (String roleName : requiredRoles) {
                if (roleRepository.findByName(roleName).isEmpty()) {
                    Role role = new Role();
                    role.setName(roleName);
                    roleRepository.save(role);
                }
            }
        };
    }

    @Bean
    @org.springframework.context.annotation.Profile("dev")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "app.seed.demo-users", havingValue = "true", matchIfMissing = false
    )
    public CommandLineRunner initWaiterPassword(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        return args -> {
            userRepository.findByEmail("waiter.senior@example.com").ifPresent(user -> {
                if (!passwordEncoder.matches("password", user.getPasswordHash())) {
                    user.setPasswordHash(passwordEncoder.encode("password"));
                    userRepository.save(user);
                }
            });
        };
    }
}
