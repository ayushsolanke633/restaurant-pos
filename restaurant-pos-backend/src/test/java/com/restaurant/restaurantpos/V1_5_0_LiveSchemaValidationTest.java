package com.restaurant.restaurantpos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LIVE_VALIDATION_ENABLED", matches = "true")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/restaurant_pos",
        "spring.datasource.username=restaurant_app",
        "spring.datasource.password=${LIVE_DB_PASSWORD}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=true",
        "spring.flyway.baseline-version=1.0.0",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "app.seed.demo-users=false"
})
public class V1_5_0_LiveSchemaValidationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Backend Schema Validation: Verify Hibernate boots and validates restaurant_pos schema with 0 errors")
    void verifyBackendSchemaValidation() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String url = meta.getURL();
            assertNotNull(url);
            assertTrue(url.contains("restaurant_pos"), "Must target restaurant_pos");
            System.out.println("[BACKEND SCHEMA VALIDATION PASSED] Hibernate EntityManagerFactory initialized and validated against " + url);
        }
    }
}
