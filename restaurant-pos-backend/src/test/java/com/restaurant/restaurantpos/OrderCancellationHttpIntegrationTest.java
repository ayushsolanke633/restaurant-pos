package com.restaurant.restaurantpos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class OrderCancellationHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testPatchCancelUnauthenticated() throws Exception {
        mockMvc.perform(patch("/api/orders/1/cancel"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testPatchCompleteUnauthenticated() throws Exception {
        mockMvc.perform(patch("/api/orders/1/complete"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }
}
