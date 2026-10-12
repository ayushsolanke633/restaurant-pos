package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.notification.NotificationResponse;
import com.restaurant.restaurantpos.dto.notification.UnreadCountResponse;
import com.restaurant.restaurantpos.entity.*;
import com.restaurant.restaurantpos.event.NotificationCreatedEvent;
import com.restaurant.restaurantpos.event.NotificationEventListener;
import com.restaurant.restaurantpos.repository.NotificationRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.CustomUserDetails;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.security.StompAuthChannelInterceptor;
import com.restaurant.restaurantpos.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class Step25NotificationTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MessageChannel messageChannel;

    @InjectMocks
    private NotificationService notificationService;

    @InjectMocks
    private NotificationEventListener eventListener;

    @InjectMocks
    private StompAuthChannelInterceptor stompInterceptor;

    private Restaurant restaurantA;
    private Restaurant restaurantB;
    private User adminUserA;
    private User inactiveAdminA;
    private User waiterUserA;
    private User kitchenUserA;
    private CustomUserDetails customAdminA;
    private CustomUserDetails customWaiterA;

    @BeforeEach
    void setUp() {
        restaurantA = new Restaurant();
        restaurantA.setId(1L);
        restaurantA.setName("Royal Dine");

        restaurantB = new Restaurant();
        restaurantB.setId(2L);
        restaurantB.setName("Ocean Breeze");

        Role roleAdmin = new Role();
        roleAdmin.setName("ADMIN");

        Role roleWaiter = new Role();
        roleWaiter.setName("WAITER");

        Role roleKitchen = new Role();
        roleKitchen.setName("KITCHEN");

        adminUserA = new User();
        adminUserA.setId(10L);
        adminUserA.setRestaurant(restaurantA);
        adminUserA.setRole(roleAdmin);
        adminUserA.setIsActive(true);

        inactiveAdminA = new User();
        inactiveAdminA.setId(11L);
        inactiveAdminA.setRestaurant(restaurantA);
        inactiveAdminA.setRole(roleAdmin);
        inactiveAdminA.setIsActive(false);

        waiterUserA = new User();
        waiterUserA.setId(20L);
        waiterUserA.setRestaurant(restaurantA);
        waiterUserA.setRole(roleWaiter);
        waiterUserA.setIsActive(true);

        kitchenUserA = new User();
        kitchenUserA.setId(30L);
        kitchenUserA.setRestaurant(restaurantA);
        kitchenUserA.setRole(roleKitchen);
        kitchenUserA.setIsActive(true);

        customAdminA = new CustomUserDetails(10L, 1L, "ADMIN", "admin@restaurant.com", "pass", true, Collections.emptyList());
        customWaiterA = new CustomUserDetails(20L, 1L, "WAITER", "waiter@restaurant.com", "pass", true, Collections.emptyList());

        lenient().when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // 1. ADMIN can access own notifications
    @Test
    void testAdminAccessOwnNotifications() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);

        Notification n = new Notification(1L, restaurantA, adminUserA, "ORDER_CREATED", "New Order", "Msg", "ORDER", 100L, false, LocalDateTime.now());
        when(notificationRepository.findByRestaurantIdAndRecipientUserIdOrderByCreatedAtDesc(1L, 10L))
                .thenReturn(Collections.singletonList(n));

        List<NotificationResponse> res = notificationService.getUserNotifications();
        assertEquals(1, res.size());
        assertEquals("ORDER_CREATED", res.get(0).getType());
    }

    // 2. WAITER can access own notifications
    @Test
    void testWaiterAccessOwnNotifications() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(20L);

        Notification n = new Notification(2L, restaurantA, waiterUserA, "ORDER_READY", "Order Ready", "Msg", "ORDER", 100L, false, LocalDateTime.now());
        when(notificationRepository.findByRestaurantIdAndRecipientUserIdOrderByCreatedAtDesc(1L, 20L))
                .thenReturn(Collections.singletonList(n));

        List<NotificationResponse> res = notificationService.getUserNotifications();
        assertEquals(1, res.size());
        assertEquals("ORDER_READY", res.get(0).getType());
    }

    // 3. KITCHEN can access own notifications
    @Test
    void testKitchenAccessOwnNotifications() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(30L);

        when(notificationRepository.findByRestaurantIdAndRecipientUserIdOrderByCreatedAtDesc(1L, 30L))
                .thenReturn(Collections.emptyList());

        List<NotificationResponse> res = notificationService.getUserNotifications();
        assertNotNull(res);
        assertTrue(res.isEmpty());
    }

    // 4 & 5. User cannot access another user's or another restaurant's notification
    @Test
    void testUserCannotAccessOtherNotifications() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(20L); // Waiter user 20L

        when(notificationRepository.findByIdAndRestaurantIdAndRecipientUserId(99L, 1L, 20L))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> notificationService.markAsRead(99L));
    }

    // 6, 7. New notification defaults to unread & mark as read works
    @Test
    void testMarkAsReadWorks() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);

        Notification n = new Notification(1L, restaurantA, adminUserA, "ORDER_CREATED", "New Order", "Msg", "ORDER", 100L, false, LocalDateTime.now());
        when(notificationRepository.findByIdAndRestaurantIdAndRecipientUserId(1L, 1L, 10L))
                .thenReturn(Optional.of(n));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        NotificationResponse res = notificationService.markAsRead(1L);
        assertTrue(res.getIsRead());
    }

    // 8 & 9. Mark-all-read & unread count work
    @Test
    void testUnreadCountAndMarkAllRead() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);

        when(notificationRepository.countByRestaurantIdAndRecipientUserIdAndIsReadFalse(1L, 10L)).thenReturn(5L);
        UnreadCountResponse countRes = notificationService.getUnreadCount();
        assertEquals(5L, countRes.getCount());

        UnreadCountResponse markAllRes = notificationService.markAllAsRead();
        assertEquals(0L, markAllRes.getCount());
        verify(notificationRepository).markAllAsRead(1L, 10L);
    }

    // 10. ORDER_CREATED creates admin notification
    @Test
    void testOrderCreatedNotifiesAdmins() {
        Order order = new Order();
        order.setId(100L);
        order.setRestaurant(restaurantA);
        order.setOrderType("DINE_IN");

        when(userRepository.findByRestaurantIdAndRoleNameIgnoreCaseAndIsActiveTrue(1L, "ADMIN"))
                .thenReturn(Collections.singletonList(adminUserA));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> {
            Notification n = i.getArgument(0);
            n.setId(55L);
            return n;
        });

        notificationService.notifyOrderCreated(order);
        verify(notificationRepository).save(any(Notification.class));
        verify(eventPublisher).publishEvent(any(NotificationCreatedEvent.class));
    }

    // 11. ORDER_READY creates waiter notification when waiterId exists
    @Test
    void testOrderReadyNotifiesWaiter() {
        Order order = new Order();
        order.setId(100L);
        order.setRestaurant(restaurantA);
        order.setWaiter(waiterUserA);
        order.setStatus("READY");

        when(userRepository.findByRestaurantIdAndRoleNameIgnoreCaseAndIsActiveTrue(1L, "ADMIN"))
                .thenReturn(Collections.singletonList(adminUserA));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        notificationService.notifyOrderStatusChanged(order);

        // Should save 2 notifications: 1 for Admin, 1 for Waiter
        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    // 12. ADMIN-created DINE_IN order (waiterId = null) does not create waiter notification
    @Test
    void testAdminCreatedOrderDoesNotNotifyWaiterOnReady() {
        Order order = new Order();
        order.setId(101L);
        order.setRestaurant(restaurantA);
        order.setWaiter(null); // Admin created order
        order.setStatus("READY");

        when(userRepository.findByRestaurantIdAndRoleNameIgnoreCaseAndIsActiveTrue(1L, "ADMIN"))
                .thenReturn(Collections.singletonList(adminUserA));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        notificationService.notifyOrderStatusChanged(order);

        // Should save 1 notification only (for Admin)
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    // 13 & 14. ORDER_CANCELLED and PAYMENT_COMPLETED create admin notifications
    @Test
    void testCancelledAndPaymentCompletedNotifyAdmin() {
        Order order = new Order();
        order.setId(102L);
        order.setRestaurant(restaurantA);

        Payment payment = new Payment();
        payment.setId(500L);
        payment.setAmount(new BigDecimal("150.00"));

        when(userRepository.findByRestaurantIdAndRoleNameIgnoreCaseAndIsActiveTrue(1L, "ADMIN"))
                .thenReturn(Collections.singletonList(adminUserA));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        notificationService.notifyOrderCancelled(order);
        notificationService.notifyPaymentCompleted(order, payment);

        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    // 15. Inactive admins do not receive notifications
    @Test
    void testInactiveAdminDoesNotReceiveNotification() {
        Order order = new Order();
        order.setId(103L);
        order.setRestaurant(restaurantA);

        // findByRestaurantIdAndRoleNameIgnoreCaseAndIsActiveTrue returns ONLY active admins
        when(userRepository.findByRestaurantIdAndRoleNameIgnoreCaseAndIsActiveTrue(1L, "ADMIN"))
                .thenReturn(Collections.singletonList(adminUserA)); // inactiveAdminA not included

        notificationService.notifyOrderCreated(order);
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    // 16, 17, 18. STOMP Notification Subscription Isolation (User & Tenant)
    @Test
    void testStompNotificationSubscriptionSecurity() {
        // Valid subscription: User 10L subscribing to own notification topic /topic/restaurants/1/notifications/10
        StompHeaderAccessor accessorValid = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessorValid.setDestination("/topic/restaurants/1/notifications/10");
        UsernamePasswordAuthenticationToken authAdmin = new UsernamePasswordAuthenticationToken(customAdminA, null, customAdminA.getAuthorities());
        accessorValid.setUser(authAdmin);

        Message<?> resValid = stompInterceptor.preSend(MessageBuilder.createMessage(new byte[0], accessorValid.getMessageHeaders()), messageChannel);
        assertNotNull(resValid);

        // Invalid: User 10L trying to subscribe to User 20L notification topic
        StompHeaderAccessor accessorOtherUser = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessorOtherUser.setDestination("/topic/restaurants/1/notifications/20");
        accessorOtherUser.setUser(authAdmin);

        assertThrows(AccessDeniedException.class, () -> stompInterceptor.preSend(MessageBuilder.createMessage(new byte[0], accessorOtherUser.getMessageHeaders()), messageChannel));

        // Invalid: User 10L trying to subscribe to Restaurant 2 notification topic
        StompHeaderAccessor accessorOtherTenant = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessorOtherTenant.setDestination("/topic/restaurants/2/notifications/10");
        accessorOtherTenant.setUser(authAdmin);

        assertThrows(AccessDeniedException.class, () -> stompInterceptor.preSend(MessageBuilder.createMessage(new byte[0], accessorOtherTenant.getMessageHeaders()), messageChannel));
    }

    // 19. Existing Kitchen WebSocket functionality still works
    @Test
    void testExistingKitchenWebSocketStillWorks() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/restaurants/1/kitchen/orders");
        UsernamePasswordAuthenticationToken authAdmin = new UsernamePasswordAuthenticationToken(customAdminA, null, customAdminA.getAuthorities());
        accessor.setUser(authAdmin);

        Message<?> res = stompInterceptor.preSend(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), messageChannel);
        assertNotNull(res);
    }

    // 20. Notification event listener broadcasts message after commit
    @Test
    void testNotificationEventListenerBroadcasts() {
        NotificationResponse res = NotificationResponse.builder()
                .id(1L)
                .restaurantId(1L)
                .recipientUserId(20L)
                .type("ORDER_READY")
                .title("Order Ready")
                .message("Order #100 is ready")
                .build();

        eventListener.handleNotificationCreated(new NotificationCreatedEvent(res));
        verify(messagingTemplate).convertAndSend("/topic/restaurants/1/notifications/20", res);
    }
}
