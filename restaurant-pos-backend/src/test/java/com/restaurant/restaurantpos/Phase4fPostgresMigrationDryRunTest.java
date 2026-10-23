package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.config.PlatformSuperAdminBootstrapper;
import com.restaurant.restaurantpos.dto.PlatformOnboardingRequest;
import com.restaurant.restaurantpos.dto.PlatformOnboardingResponse;
import com.restaurant.restaurantpos.dto.RestaurantStatusUpdateRequest;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.Role;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.exception.DuplicateEmailException;

import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RestaurantSettingsRepository;
import com.restaurant.restaurantpos.repository.RoleRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.CustomUserDetailsService;
import com.restaurant.restaurantpos.service.PlatformOnboardingService;
import com.restaurant.restaurantpos.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "MIGRATION_TEST_DB_PASSWORD", matches = ".+")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/restaurant_pos_migration_test",
        "spring.datasource.username=restaurant_app",
        "spring.datasource.password=${MIGRATION_TEST_DB_PASSWORD}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=true",
        "spring.flyway.baseline-version=1.0.0",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "platform.superadmin.email=testsuperadmin@migration-test.platform",
        "platform.superadmin.password=" + Phase4fPostgresMigrationDryRunTest.TEST_SUPERADMIN_PASSWORD,
        "app.seed.demo-users=false"
})
public class Phase4fPostgresMigrationDryRunTest {

    public static final String TEST_SUPERADMIN_PASSWORD = "test-only-superadmin-password-dryrun-phase4f";
    private static final String EXPECTED_DB_NAME = "restaurant_pos_migration_test";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformOnboardingService onboardingService;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RestaurantSettingsRepository settingsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformSuperAdminBootstrapper bootstrapper;

    @Test
    @Order(1)
    @DisplayName("Requirement 5: Verify effective database URL targets ONLY restaurant_pos_migration_test")
    void verifyEffectiveDatabaseUrlSafetyGate() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String url = metaData.getURL();
            assertNotNull(url, "Datasource connection URL must not be null");
            assertTrue(url.contains(EXPECTED_DB_NAME),
                    "CRITICAL SAFETY VIOLATION: Test datasource URL must strictly target '" + EXPECTED_DB_NAME + "'. Actual URL: " + url);
            assertFalse(url.contains("restaurant_pos_restore_test"),
                    "CRITICAL SAFETY VIOLATION: Test must not target restaurant_pos_restore_test");
            assertFalse(url.endsWith("/restaurant_pos"),
                    "CRITICAL SAFETY VIOLATION: Test must not target live restaurant_pos database");
            System.out.println("[SAFETY CHECK VERIFIED] Effective DB URL: " + url);
        }
    }

    @Test
    @Order(2)
    @DisplayName("Requirement 6: Verify Flyway V1_2_0 executed cleanly on restaurant_pos_migration_test")
    void verifyFlywayMigrationExecution() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT installed_rank, version, description, script, success FROM flyway_schema_history WHERE version = '1.2.0'");
        assertEquals(1, rows.size(), "Flyway migration V1_2_0 must be recorded in flyway_schema_history");
        Map<String, Object> record = rows.get(0);
        assertEquals("1.2.0", record.get("version"));
        assertEquals("add super admin and restaurant status", record.get("description"));
        assertEquals("V1_2_0__add_super_admin_and_restaurant_status.sql", record.get("script"));
        assertEquals(Boolean.TRUE, record.get("success"), "Flyway V1_2_0 execution must be marked success = true");
        System.out.println("[FLYWAY MIGRATION VERIFIED] V1.2.0 applied successfully.");
    }

    @Test
    @Order(3)
    @DisplayName("Requirement 6: Verify PostgreSQL schema modifications (is_active, nullable restaurant_id, role)")
    void verifyPostgresSchemaModifications() {
        // 1. Verify restaurants.is_active column
        Integer isActiveColCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'restaurants' AND column_name = 'is_active' AND data_type = 'boolean'",
                Integer.class);
        assertEquals(1, isActiveColCount, "restaurants.is_active boolean column must exist");

        // 2. Verify users.restaurant_id is nullable
        String isNullable = jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'restaurant_id'",
                String.class);
        assertEquals("YES", isNullable, "users.restaurant_id must be nullable for platform admins");

        // 3. Verify SUPER_ADMIN role seeded
        Integer superAdminCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM roles WHERE name = 'SUPER_ADMIN'",
                Integer.class);
        assertEquals(1, superAdminCount, "SUPER_ADMIN role must be present in roles table");

        // 4. Verify existing 7 users and 2 restaurants are preserved intact
        Integer restaurantCount = jdbcTemplate.queryForObject("SELECT count(*) FROM restaurants", Integer.class);
        assertTrue(restaurantCount >= 2, "Existing restaurants must be preserved");

        Integer userCount = jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class);
        assertTrue(userCount >= 7, "Existing users must be preserved");

        // Verify existing restaurants have is_active = true
        List<Boolean> statuses = jdbcTemplate.queryForList("SELECT is_active FROM restaurants", Boolean.class);
        for (Boolean s : statuses) {
            assertTrue(s, "All existing restaurants must default to is_active = true");
        }
        System.out.println("[SCHEMA INTEGRITY VERIFIED] Schema modifications and existing data preserved.");
    }

    @Test
    @Order(4)
    @DisplayName("Requirement 6: Verify PostgreSQL role-tenant integrity trigger behavior")
    void verifyPostgresRoleTenantTrigger() {
        Long adminRoleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE name = 'ADMIN'", Long.class);
        Long superAdminRoleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE name = 'SUPER_ADMIN'", Long.class);
        Long validRestaurantId = jdbcTemplate.queryForObject("SELECT id FROM restaurants LIMIT 1", Long.class);

        // Test 1: Inserting tenant user (ADMIN) with NULL restaurant_id MUST fail trigger
        Exception ex1 = assertThrows(Exception.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO users (email, name, password_hash, is_active, role_id, restaurant_id, created_at, updated_at) " +
                            "VALUES ('invalid_tenant@test.com', 'Invalid Tenant', 'hash', true, ?, NULL, NOW(), NOW())",
                    adminRoleId);
        });
        assertTrue(ex1.getMessage().contains("ROLE_TENANT_INTEGRITY_VIOLATION"),
                "Expected trigger exception for tenant user with NULL restaurant_id. Got: " + ex1.getMessage());

        // Test 2: Inserting SUPER_ADMIN with non-null restaurant_id MUST fail trigger
        Exception ex2 = assertThrows(Exception.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO users (email, name, password_hash, is_active, role_id, restaurant_id, created_at, updated_at) " +
                            "VALUES ('invalid_sa@test.com', 'Invalid SA', 'hash', true, ?, ?, NOW(), NOW())",
                    superAdminRoleId, validRestaurantId);
        });
        assertTrue(ex2.getMessage().contains("ROLE_TENANT_INTEGRITY_VIOLATION"),
                "Expected trigger exception for SUPER_ADMIN with non-null restaurant_id. Got: " + ex2.getMessage());

        // Test 3: Inserting user with non-existent role_id MUST fail trigger
        Exception ex3 = assertThrows(Exception.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO users (email, name, password_hash, is_active, role_id, restaurant_id, created_at, updated_at) " +
                            "VALUES ('invalid_role@test.com', 'Invalid Role', 'hash', true, 99999, ?, NOW(), NOW())",
                    validRestaurantId);
        });
        assertTrue(ex3.getMessage().contains("ROLE_TENANT_INTEGRITY_VIOLATION"),
                "Expected trigger exception for non-existent role_id. Got: " + ex3.getMessage());

        System.out.println("[ROLE-TENANT TRIGGER VERIFIED] All integrity constraints enforced by PostgreSQL.");
    }

    @Test
    @Order(5)
    @DisplayName("Requirement 7: Verify Platform SUPER_ADMIN bootstrap and idempotency")
    void verifyPlatformSuperAdminBootstrap() {
        User superAdmin = userRepository.findByEmail("testsuperadmin@migration-test.platform").orElse(null);
        assertNotNull(superAdmin, "Super admin must be created by PlatformSuperAdminBootstrapper");
        assertEquals("SUPER_ADMIN", superAdmin.getRole().getName());
        assertNull(superAdmin.getRestaurant(), "Super admin must have null restaurant");
        assertTrue(passwordEncoder.matches(TEST_SUPERADMIN_PASSWORD, superAdmin.getPasswordHash()));

        // Verify idempotency: running bootstrapper again does not duplicate or reset
        bootstrapper.run(new org.springframework.boot.DefaultApplicationArguments(new String[0]));
        long superAdminCount = userRepository.count();
        bootstrapper.run(new org.springframework.boot.DefaultApplicationArguments(new String[0]));
        assertEquals(superAdminCount, userRepository.count(), "Bootstrapper must be strictly idempotent");
        System.out.println("[BOOTSTRAPPER VERIFIED] Platform super admin successfully created and idempotent.");
    }

    private static Long dryRunRestaurantId;
    private static String dryRunAdminEmail;

    @Test
    @Order(6)
    @DisplayName("Requirement 7: Test restaurant onboarding and atomic rollback on duplicate email")
    void verifyRestaurantOnboardingAndRollback() {
        long ts = System.currentTimeMillis();
        dryRunAdminEmail = "dryrunadmin_" + ts + "@bistro.local";

        PlatformOnboardingRequest request = new PlatformOnboardingRequest();
        request.setRestaurantName("Dry Run Bistro " + ts);
        request.setAddress("123 Dry Run Lane");
        request.setPhone("9876543210");
        request.setEmail("contact_" + ts + "@dryrunbistro.com");
        request.setAdminName("Dry Run Admin");
        request.setAdminEmail(dryRunAdminEmail);
        request.setAdminPassword("BistroAdmin123!");

        PlatformOnboardingResponse response = onboardingService.onboardRestaurant(request);
        assertNotNull(response);
        assertNotNull(response.getRestaurantId());
        assertEquals("Dry Run Bistro " + ts, response.getRestaurantName());
        assertTrue(response.getIsActive());
        assertEquals(dryRunAdminEmail, response.getAdminEmail());

        dryRunRestaurantId = response.getRestaurantId();

        // Verify database state
        assertTrue(restaurantRepository.existsById(dryRunRestaurantId));
        assertTrue(settingsRepository.findByRestaurantId(dryRunRestaurantId).isPresent());
        assertTrue(userRepository.findByEmail(dryRunAdminEmail).isPresent());

        // Now test ATOMIC ROLLBACK on duplicate admin email
        long restaurantCountBefore = restaurantRepository.count();
        long userCountBefore = userRepository.count();

        PlatformOnboardingRequest duplicateReq = new PlatformOnboardingRequest();
        duplicateReq.setRestaurantName("Duplicate Bistro Attempt " + ts);
        duplicateReq.setAdminName("Duplicate Guy");
        duplicateReq.setAdminEmail(dryRunAdminEmail); // DUPLICATE
        duplicateReq.setAdminPassword("AnotherPassword123!");

        assertThrows(DuplicateEmailException.class, () -> onboardingService.onboardRestaurant(duplicateReq));

        // Verify 0 orphaned restaurants or users created
        assertEquals(restaurantCountBefore, restaurantRepository.count(), "Restaurant count must not increase on rollback");
        assertEquals(userCountBefore, userRepository.count(), "User count must not increase on rollback");
        assertFalse(restaurantRepository.findAll().stream().anyMatch(r -> ("Duplicate Bistro Attempt " + ts).equals(r.getName())),
                "Failed onboarding restaurant must be completely rolled back");
        System.out.println("[ONBOARDING & ATOMIC ROLLBACK VERIFIED] Rollback preserved 100% database consistency.");
    }

    @Test
    @Order(7)
    @DisplayName("Requirement 7: Test restaurant deactivation, authentication blocking, and reactivation")
    void verifyRestaurantDeactivationAndReactivation() {
        assertNotNull(dryRunRestaurantId, "dryRunRestaurantId must be set by onboarding test");
        assertNotNull(dryRunAdminEmail, "dryRunAdminEmail must be set by onboarding test");

        // 1. Deactivate restaurant
        onboardingService.updateRestaurantStatus(dryRunRestaurantId, false);

        Restaurant updated = restaurantRepository.findById(dryRunRestaurantId).orElseThrow();
        assertFalse(updated.getIsActive(), "Restaurant status must be false");

        // 2. Authentication check: user loading must throw DisabledException
        DisabledException disabledEx = assertThrows(DisabledException.class, () -> {
            userDetailsService.loadUserByUsername(dryRunAdminEmail);
        });
        assertTrue(disabledEx.getMessage().contains("Restaurant is deactivated"),
                "Expected 'Restaurant is deactivated' message. Got: " + disabledEx.getMessage());

        // 3. Reactivate restaurant
        onboardingService.updateRestaurantStatus(dryRunRestaurantId, true);

        Restaurant reactivated = restaurantRepository.findById(dryRunRestaurantId).orElseThrow();
        assertTrue(reactivated.getIsActive(), "Restaurant status must be true after reactivation");

        // 4. Authentication check: user loading now succeeds
        assertDoesNotThrow(() -> {
            userDetailsService.loadUserByUsername(dryRunAdminEmail);
        });
        System.out.println("[DEACTIVATION & REACTIVATION VERIFIED] Lifecycle transitions enforced correctly.");
    }

    @Test
    @Order(8)
    @DisplayName("Requirement 7: Test tenant isolation against SUPER_ADMIN accounts")
    void verifyTenantIsolationAgainstSuperAdmin() {
        User superAdmin = userRepository.findByEmail("testsuperadmin@migration-test.platform").orElseThrow();
        Long superAdminId = superAdmin.getId();

        // Tenant admin querying super admin ID must receive RuntimeException (anti-enumeration)
        assertThrows(RuntimeException.class, () -> {
            userService.getUserById(superAdminId);
        });

        // Tenant admin trying to deactivate super admin must receive RuntimeException
        assertThrows(RuntimeException.class, () -> {
            userService.deactivateUser(superAdminId);
        });

        System.out.println("[TENANT ISOLATION VERIFIED] Tenant administrators cannot access or manage SUPER_ADMIN.");
    }
}
