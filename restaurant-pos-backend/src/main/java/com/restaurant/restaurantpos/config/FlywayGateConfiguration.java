package com.restaurant.restaurantpos.config;

import com.restaurant.restaurantpos.util.FlywayApprovalGateCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot Configuration for Flyway Execution Boundary Guard
 *
 * Automatically registers FlywayApprovalGateCallback into the Spring-managed Flyway bean
 * via FlywayConfigurationCustomizer.
 */
@Configuration
@ConditionalOnClass(org.flywaydb.core.Flyway.class)
public class FlywayGateConfiguration {

    @Bean
    public FlywayApprovalGateCallback flywayApprovalGateCallback() {
        return new FlywayApprovalGateCallback();
    }

    @Bean
    public FlywayConfigurationCustomizer flywayGateCustomizer(FlywayApprovalGateCallback callback) {
        return configuration -> configuration.callbacks(callback);
    }
}
