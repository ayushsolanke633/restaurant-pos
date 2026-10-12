package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.kitchen.KitchenOrderEvent;
import com.restaurant.restaurantpos.event.KitchenOrderEventListener;
import com.restaurant.restaurantpos.event.OrderChangeEvent;
import com.restaurant.restaurantpos.security.CustomUserDetails;
import com.restaurant.restaurantpos.security.StompAuthChannelInterceptor;
import com.restaurant.restaurantpos.service.CustomUserDetailsService;
import com.restaurant.restaurantpos.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class Step24WebSocketTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private MessageChannel messageChannel;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private StompAuthChannelInterceptor stompInterceptor;

    @InjectMocks
    private KitchenOrderEventListener eventListener;

    private CustomUserDetails kitchenUserR1;
    private CustomUserDetails adminUserR1;
    private CustomUserDetails waiterUserR1;
    private CustomUserDetails kitchenUserR2;

    @BeforeEach
    void setUp() {
        kitchenUserR1 = new CustomUserDetails(1L, 1L, "KITCHEN", "kitchen1@restaurant.com", "pass", true, Collections.emptyList());
        adminUserR1 = new CustomUserDetails(2L, 1L, "ADMIN", "admin1@restaurant.com", "pass", true, Collections.emptyList());
        waiterUserR1 = new CustomUserDetails(3L, 1L, "WAITER", "waiter1@restaurant.com", "pass", true, Collections.emptyList());
        kitchenUserR2 = new CustomUserDetails(4L, 2L, "KITCHEN", "kitchen2@restaurant.com", "pass", true, Collections.emptyList());
    }

    // 1. STOMP CONNECT with valid JWT authenticates user
    @Test
    void testStompConnectSuccess() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer valid_jwt");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtService.extractUsername("valid_jwt")).thenReturn("kitchen1@restaurant.com");
        when(userDetailsService.loadUserByUsername("kitchen1@restaurant.com")).thenReturn(kitchenUserR1);
        when(jwtService.isTokenValid("valid_jwt", kitchenUserR1)).thenReturn(true);

        Message<?> result = stompInterceptor.preSend(message, messageChannel);
        assertNotNull(result);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNotNull(resultAccessor.getUser());
        assertEquals("kitchen1@restaurant.com", resultAccessor.getUser().getName());
    }

    // 2. STOMP CONNECT with invalid JWT is rejected / unauthenticated
    @Test
    void testStompConnectInvalidJwt() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer invalid_jwt");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtService.extractUsername("invalid_jwt")).thenThrow(new RuntimeException("Invalid token"));

        Message<?> result = stompInterceptor.preSend(message, messageChannel);
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNull(resultAccessor.getUser());
    }

    // 3. KITCHEN user of Restaurant 1 can subscribe to Restaurant 1 Kitchen topic
    @Test
    void testKitchenSubscribeOwnRestaurantSuccess() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/restaurants/1/kitchen/orders");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(kitchenUserR1, null, kitchenUserR1.getAuthorities());
        accessor.setUser(auth);

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = stompInterceptor.preSend(message, messageChannel);
        assertNotNull(result);
    }

    // 4. ADMIN user of Restaurant 1 can subscribe to Restaurant 1 Kitchen topic
    @Test
    void testAdminSubscribeOwnRestaurantSuccess() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/restaurants/1/kitchen/orders");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(adminUserR1, null, adminUserR1.getAuthorities());
        accessor.setUser(auth);

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = stompInterceptor.preSend(message, messageChannel);
        assertNotNull(result);
    }

    // 5. WAITER user of Restaurant 1 cannot subscribe to Kitchen topic (Role unauthorized)
    @Test
    void testWaiterSubscribeKitchenTopicRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/restaurants/1/kitchen/orders");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(waiterUserR1, null, waiterUserR1.getAuthorities());
        accessor.setUser(auth);

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () -> stompInterceptor.preSend(message, messageChannel));
    }

    // 6. KITCHEN user of Restaurant 2 cannot subscribe to Restaurant 1 Kitchen topic (Tenant mismatch)
    @Test
    void testCrossTenantSubscriptionRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/restaurants/1/kitchen/orders");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(kitchenUserR2, null, kitchenUserR2.getAuthorities());
        accessor.setUser(auth);

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () -> stompInterceptor.preSend(message, messageChannel));
    }

    // 7. Unauthenticated user subscription rejected
    @Test
    void testUnauthenticatedSubscriptionRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/restaurants/1/kitchen/orders");

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () -> stompInterceptor.preSend(message, messageChannel));
    }

    // 8. Event listener broadcasts event to correct destination
    @Test
    void testEventListenerBroadcastsEvent() {
        KitchenOrderEvent event = KitchenOrderEvent.builder()
                .eventType("ORDER_CREATED")
                .orderId(100L)
                .restaurantId(1L)
                .status("NEW")
                .build();

        eventListener.handleOrderChangeEvent(new OrderChangeEvent(event));

        verify(messagingTemplate).convertAndSend("/topic/restaurants/1/kitchen/orders", event);
    }
}
