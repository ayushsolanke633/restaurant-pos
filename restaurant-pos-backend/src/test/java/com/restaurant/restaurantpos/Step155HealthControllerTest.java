package com.restaurant.restaurantpos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class Step155HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testHealthEndpointUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("restaurant-pos-backend"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.discoveryName").value("restaurant-pos"))
                .andExpect(jsonPath("$.port").value(8080));
    }

    @Test
    public void testBusinessEndpointProtected() throws Exception {
        mockMvc.perform(get("/api/tables"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthenticated"));
    }
}
