package com.restaurant.restaurantpos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Isolated test verifying that Flyway is completely disabled during Spring Boot startup,
 * and no Flyway migration beans are registered in the ApplicationContext.
 *
 * Runs strictly against an isolated in-memory H2 database; zero PostgreSQL connection.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:flywaydisabledtestdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=test-only-secret-key-for-flyway-disabled-startup-test-123456789",
        "jwt.expiration-ms=86400000",
        "server.port=-1",
        "app.cors.allowed-origins=http://localhost:5173",
        "app.seed.demo-users=false"
})
public class FlywayStartupDisabledIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("Startup Isolation: Verify Flyway bean is NOT registered in ApplicationContext")
    void verifyFlywayBeanNotRegistered() {
        assertFalse(applicationContext.containsBean("flyway"),
                "ApplicationContext must NOT register a Flyway bean when spring.flyway.enabled=false");
    }

    @Test
    @DisplayName("Startup Isolation: Verify FlywayMigrationInitializer bean is NOT registered")
    void verifyFlywayMigrationInitializerNotRegistered() {
        assertFalse(applicationContext.containsBean("flywayInitializer"),
                "ApplicationContext must NOT register FlywayMigrationInitializer when spring.flyway.enabled=false");
    }

    @Test
    @DisplayName("Startup Isolation: Verify spring.flyway.enabled evaluates strictly to false")
    void verifyFlywayPropertyEvaluatesToFalse() {
        String flywayEnabled = environment.getProperty("spring.flyway.enabled");
        assertEquals("false", flywayEnabled,
                "Property spring.flyway.enabled must evaluate strictly to false");
    }
}
