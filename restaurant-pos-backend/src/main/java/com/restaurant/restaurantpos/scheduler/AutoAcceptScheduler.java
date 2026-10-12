package com.restaurant.restaurantpos.scheduler;

import com.restaurant.restaurantpos.service.AutoAcceptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AutoAcceptScheduler {

    private static final Logger logger = LoggerFactory.getLogger(AutoAcceptScheduler.class);
    private final AutoAcceptService autoAcceptService;

    public AutoAcceptScheduler(AutoAcceptService autoAcceptService) {
        this.autoAcceptService = autoAcceptService;
    }

    @Scheduled(fixedDelayString = "${app.auto-accept.fixed-delay-ms:10000}")
    public void runAutoAcceptTask() {
        try {
            autoAcceptService.processAutoAcceptOrders();
        } catch (Exception e) {
            logger.error("Error executing auto-accept scheduled task: {}", e.getMessage(), e);
        }
    }
}
