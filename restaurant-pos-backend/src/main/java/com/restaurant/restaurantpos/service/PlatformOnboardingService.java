package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.dto.PlatformOnboardingRequest;
import com.restaurant.restaurantpos.dto.PlatformOnboardingResponse;
import com.restaurant.restaurantpos.dto.PlatformRestaurantResponse;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RestaurantSettings;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.exception.DuplicateEmailException;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RestaurantSettingsRepository;
import com.restaurant.restaurantpos.repository.RoleRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.RestaurantWebSocketSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PlatformOnboardingService {

    private static final Logger log = LoggerFactory.getLogger(PlatformOnboardingService.class);

    private final RestaurantRepository restaurantRepository;
    private final RestaurantSettingsRepository restaurantSettingsRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RestaurantWebSocketSessionManager webSocketSessionManager;

    public PlatformOnboardingService(
            RestaurantRepository restaurantRepository,
            RestaurantSettingsRepository restaurantSettingsRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            BCryptPasswordEncoder passwordEncoder,
            RestaurantWebSocketSessionManager webSocketSessionManager) {
        this.restaurantRepository = restaurantRepository;
        this.restaurantSettingsRepository = restaurantSettingsRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.webSocketSessionManager = webSocketSessionManager;
    }

    /**
     * Atomically onboards a new restaurant, default configuration settings, and initial admin account.
     * Full transactional rollback is guaranteed upon any validation or persistence failure.
     */
    @Transactional(rollbackFor = Exception.class)
    public PlatformOnboardingResponse onboardRestaurant(PlatformOnboardingRequest request) {
        String cleanAdminEmail = request.getAdminEmail() != null ? request.getAdminEmail().trim().toLowerCase() : null;

        // 1. Fail-fast pre-checks
        if (cleanAdminEmail == null || cleanAdminEmail.isBlank()) {
            throw new IllegalArgumentException("Admin email is required.");
        }

        if (userRepository.findByEmail(cleanAdminEmail).isPresent()) {
            throw new DuplicateEmailException("Email already exists: " + cleanAdminEmail);
        }

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role not found in system."));

        // 2. Persist Restaurant
        Restaurant restaurant = new Restaurant();
        restaurant.setName(request.getRestaurantName().trim());
        restaurant.setAddress(request.getAddress());
        restaurant.setPhone(request.getPhone());
        restaurant.setEmail(request.getEmail());
        restaurant.setGstNumber(request.getGstNumber());
        restaurant.setIsActive(true);

        Restaurant savedRestaurant = restaurantRepository.save(restaurant);

        // 3. Persist Production Default Settings
        RestaurantSettings settings = new RestaurantSettings();
        settings.setRestaurant(savedRestaurant);
        settings.setCurrency("INR");
        settings.setTimezone("Asia/Kolkata");
        settings.setTaxEnabled(true);
        settings.setDefaultTaxRate(BigDecimal.valueOf(5.00).setScale(2));
        settings.setServiceChargeEnabled(false);
        settings.setDefaultServiceChargeRate(BigDecimal.ZERO.setScale(2));
        settings.setAutoAcceptOrders(true);
        settings.setInvoicePrefix("INV");

        restaurantSettingsRepository.save(settings);

        // 4. Persist Initial Tenant Admin
        User adminUser = new User();
        adminUser.setRestaurant(savedRestaurant);
        adminUser.setRole(adminRole);
        adminUser.setName(request.getAdminName().trim());
        adminUser.setEmail(cleanAdminEmail);
        adminUser.setPhone(request.getAdminPhone());
        adminUser.setPasswordHash(passwordEncoder.encode(request.getAdminPassword()));
        adminUser.setIsActive(true);

        User savedAdmin = userRepository.save(adminUser);

        // Safe audit log without logging credentials
        log.info("Successfully onboarded restaurant ID={} ({}) with admin email={}",
                savedRestaurant.getId(), savedRestaurant.getName(), cleanAdminEmail);

        return PlatformOnboardingResponse.builder()
                .restaurantId(savedRestaurant.getId())
                .restaurantName(savedRestaurant.getName())
                .adminEmail(savedAdmin.getEmail())
                .adminName(savedAdmin.getName())
                .isActive(savedRestaurant.getIsActive())
                .createdAt(savedRestaurant.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<PlatformRestaurantResponse> getAllRestaurants() {
        return restaurantRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(this::mapToPlatformResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PlatformRestaurantResponse getRestaurantById(Long id) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Restaurant not found with id: " + id));
        return mapToPlatformResponse(restaurant);
    }

    @Transactional(rollbackFor = Exception.class)
    public PlatformRestaurantResponse updateRestaurantStatus(Long id, boolean isActive) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Restaurant not found with id: " + id));

        restaurant.setIsActive(isActive);
        Restaurant saved = restaurantRepository.save(restaurant);

        if (!isActive) {
            log.info("Deactivating restaurant ID={}. Invalidating active WebSocket sessions.", id);
            webSocketSessionManager.deactivateRestaurant(id);
        } else {
            log.info("Reactivating restaurant ID={}.", id);
            webSocketSessionManager.reactivateRestaurant(id);
        }

        return mapToPlatformResponse(saved);
    }

    private PlatformRestaurantResponse mapToPlatformResponse(Restaurant restaurant) {
        String adminEmail = userRepository.findFirstByRestaurantIdAndRole_NameIgnoreCase(restaurant.getId(), "ADMIN")
                .map(User::getEmail)
                .orElse(null);
        Long totalUsers = userRepository.countByRestaurantId(restaurant.getId());

        return PlatformRestaurantResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .address(restaurant.getAddress())
                .phone(restaurant.getPhone())
                .email(restaurant.getEmail())
                .gstNumber(restaurant.getGstNumber())
                .isActive(restaurant.getIsActive())
                .adminEmail(adminEmail)
                .totalUsers(totalUsers != null ? totalUsers : 0L)
                .createdAt(restaurant.getCreatedAt())
                .updatedAt(restaurant.getUpdatedAt())
                .build();
    }
}
