package com.restaurant.restaurantpos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RestaurantPosApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestaurantPosApplication.class, args);
    }
}

