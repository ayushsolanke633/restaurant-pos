package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.dto.UserRequest;
import com.restaurant.restaurantpos.dto.UserResponse;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RoleRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "WAITER", "KITCHEN");

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final RoleRepository roleRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecurityUtils securityUtils;

    public UserService(
            UserRepository userRepository,
            RestaurantRepository restaurantRepository,
            RoleRepository roleRepository,
            BCryptPasswordEncoder passwordEncoder,
            SecurityUtils securityUtils) {
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.securityUtils = securityUtils;
    }

    @Transactional
    public UserResponse createUser(UserRequest request) {
        Long currentRestaurantId = securityUtils.getCurrentRestaurantId();
        if (currentRestaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        if (request.getRestaurantId() != null) {
            securityUtils.validateTenantAccess(request.getRestaurantId());
        }

        Long targetRestaurantId = currentRestaurantId;

        Restaurant restaurant = restaurantRepository.findById(targetRestaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found with id: " + targetRestaurantId));

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RuntimeException("Role not found with id: " + request.getRoleId()));

        validateRoleName(role.getName());

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new RuntimeException("Password is required when creating a user");
        }

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists: " + request.getEmail());
        }

        User user = new User();
        user.setRestaurant(restaurant);
        user.setRole(role);
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        User savedUser = userRepository.save(user);
        return mapToResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = findUserById(id);
        securityUtils.validateTenantAccess(user.getRestaurant().getId());
        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByRestaurant(Long restaurantId) {
        Long currentRestaurantId = securityUtils.getCurrentRestaurantId();
        if (currentRestaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        securityUtils.validateTenantAccess(restaurantId);

        if (!restaurantRepository.existsById(restaurantId)) {
            throw new RuntimeException("Restaurant not found with id: " + restaurantId);
        }

        return userRepository.findByRestaurantId(restaurantId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserResponse updateUser(Long id, UserRequest request) {
        User user = findUserById(id);
        securityUtils.validateTenantAccess(user.getRestaurant().getId());

        if (request.getRestaurantId() != null) {
            securityUtils.validateTenantAccess(request.getRestaurantId());
            if (!request.getRestaurantId().equals(user.getRestaurant().getId())) {
                throw new AccessDeniedException("Cannot change user restaurant");
            }
        }

        if (request.getEmail() != null && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
            Optional<User> existingUserWithEmail = userRepository.findByEmail(request.getEmail());
            if (existingUserWithEmail.isPresent() && !existingUserWithEmail.get().getId().equals(id)) {
                throw new RuntimeException("Email already exists: " + request.getEmail());
            }
        }

        if (request.getRoleId() != null && !request.getRoleId().equals(user.getRole().getId())) {
            Role newRole = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new RuntimeException("Role not found with id: " + request.getRoleId()));
            validateRoleName(newRole.getName());
            user.setRole(newRole);
        }

        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getIsActive() != null) {
            if (!request.getIsActive() && user.getRole() != null && "ADMIN".equalsIgnoreCase(user.getRole().getName())) {
                throw new RuntimeException("ADMIN users cannot be deactivated");
            }
            user.setIsActive(request.getIsActive());
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        User updatedUser = userRepository.save(user);
        return mapToResponse(updatedUser);
    }

    @Transactional
    public UserResponse deactivateUser(Long id) {
        User user = findUserById(id);
        securityUtils.validateTenantAccess(user.getRestaurant().getId());
        if (user.getRole() != null && "ADMIN".equalsIgnoreCase(user.getRole().getName())) { throw new RuntimeException("ADMIN users cannot be deactivated"); }
        user.setIsActive(false);
        User deactivatedUser = userRepository.save(user);
        return mapToResponse(deactivatedUser);
    }

    private User findUserById(Long id) {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        Optional<User> opt = userRepository.findByIdAndRestaurantId(id, restaurantId);
        if (opt.isPresent()) {
            return opt.get();
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        if (user.getRole() != null && "SUPER_ADMIN".equalsIgnoreCase(user.getRole().getName())) {
            throw new RuntimeException("User not found with id: " + id);
        }
        if (user.getRestaurant() == null || !restaurantId.equals(user.getRestaurant().getId())) {
            throw new RuntimeException("User not found with id: " + id);
        }
        return user;
    }

    private void validateRoleName(String roleName) {
        if (!ALLOWED_ROLES.contains(roleName)) {
            throw new RuntimeException("Invalid role");
        }
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .restaurantId(user.getRestaurant() != null ? user.getRestaurant().getId() : null)
                .roleId(user.getRole() != null ? user.getRole().getId() : null)
                .roleName(user.getRole() != null ? user.getRole().getName() : null)
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
