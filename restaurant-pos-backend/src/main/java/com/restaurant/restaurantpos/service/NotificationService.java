package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.dto.notification.NotificationResponse;
import com.restaurant.restaurantpos.dto.notification.UnreadCountResponse;
import com.restaurant.restaurantpos.entity.Notification;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.Payment;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.event.NotificationCreatedEvent;
import com.restaurant.restaurantpos.repository.NotificationRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;
    private final ApplicationEventPublisher applicationEventPublisher;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            SecurityUtils securityUtils,
            ApplicationEventPublisher applicationEventPublisher) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.securityUtils = securityUtils;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUserNotifications() {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        Long recipientUserId = securityUtils.getCurrentUserId();

        List<Notification> notifications = notificationRepository
                .findByRestaurantIdAndRecipientUserIdOrderByCreatedAtDesc(restaurantId, recipientUserId);

        return notifications.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadUserNotifications() {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        Long recipientUserId = securityUtils.getCurrentUserId();

        List<Notification> notifications = notificationRepository
                .findByRestaurantIdAndRecipientUserIdAndIsReadFalseOrderByCreatedAtDesc(restaurantId, recipientUserId);

        return notifications.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount() {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        Long recipientUserId = securityUtils.getCurrentUserId();

        long count = notificationRepository
                .countByRestaurantIdAndRecipientUserIdAndIsReadFalse(restaurantId, recipientUserId);

        return UnreadCountResponse.builder().count(count).build();
    }

    @Transactional
    public NotificationResponse markAsRead(Long id) {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        Long recipientUserId = securityUtils.getCurrentUserId();

        Notification notification = notificationRepository
                .findByIdAndRestaurantIdAndRecipientUserId(id, restaurantId, recipientUserId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        notification.setIsRead(true);
        Notification saved = notificationRepository.save(notification);
        return mapToResponse(saved);
    }

    @Transactional
    public UnreadCountResponse markAllAsRead() {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        Long recipientUserId = securityUtils.getCurrentUserId();

        notificationRepository.markAllAsRead(restaurantId, recipientUserId);
        return UnreadCountResponse.builder().count(0).build();
    }

    // Helper method to create and persist a notification and trigger post-commit event
    @Transactional
    public void createNotification(User recipient, String type, String title, String message, String refType, Long refId) {
        if (recipient == null || !Boolean.TRUE.equals(recipient.getIsActive())) {
            return;
        }

        Notification notification = new Notification();
        notification.setRestaurant(recipient.getRestaurant());
        notification.setRecipientUser(recipient);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setReferenceType(refType);
        notification.setReferenceId(refId);
        notification.setIsRead(false);

        Notification saved = notificationRepository.save(notification);
        if (saved != null) {
            NotificationResponse response = mapToResponse(saved);
            if (response != null) {
                applicationEventPublisher.publishEvent(new NotificationCreatedEvent(response));
            }
        }
    }

    @Transactional
    public void notifyOrderCreated(Order order) {
        if (order == null || order.getRestaurant() == null) return;
        List<User> activeAdmins = userRepository.findByRestaurantIdAndRoleNameIgnoreCaseAndIsActiveTrue(order.getRestaurant().getId(), "ADMIN");
        for (User admin : activeAdmins) {
            createNotification(admin, "ORDER_CREATED", "New Order Created",
                    "New " + order.getOrderType() + " order #" + order.getId() + " created", "ORDER", order.getId());
        }
    }

    @Transactional
    public void notifyOrderStatusChanged(Order order) {
        if (order == null || order.getRestaurant() == null) return;
        List<User> activeAdmins = userRepository.findByRestaurantIdAndRoleNameIgnoreCaseAndIsActiveTrue(order.getRestaurant().getId(), "ADMIN");
        for (User admin : activeAdmins) {
            createNotification(admin, "ORDER_STATUS_CHANGED", "Order Status Updated",
                    "Order #" + order.getId() + " status changed to " + order.getStatus(), "ORDER", order.getId());
        }

        if ("READY".equalsIgnoreCase(order.getStatus()) && order.getWaiter() != null && Boolean.TRUE.equals(order.getWaiter().getIsActive())) {
            createNotification(order.getWaiter(), "ORDER_READY", "Order Ready",
                    "DINE_IN order #" + order.getId() + " is ready!", "ORDER", order.getId());
        }
    }

    @Transactional
    public void notifyOrderCancelled(Order order) {
        if (order == null || order.getRestaurant() == null) return;
        List<User> activeAdmins = userRepository.findByRestaurantIdAndRoleNameIgnoreCaseAndIsActiveTrue(order.getRestaurant().getId(), "ADMIN");
        for (User admin : activeAdmins) {
            createNotification(admin, "ORDER_CANCELLED", "Order Cancelled",
                    "Order #" + order.getId() + " has been cancelled", "ORDER", order.getId());
        }

        if (order.getWaiter() != null && Boolean.TRUE.equals(order.getWaiter().getIsActive())) {
            createNotification(order.getWaiter(), "ORDER_CANCELLED", "Order Cancelled",
                    "Order #" + order.getId() + " has been cancelled", "ORDER", order.getId());
        }
    }

    @Transactional
    public void notifyPaymentCompleted(Order order, Payment payment) {
        if (order == null || order.getRestaurant() == null) return;
        List<User> activeAdmins = userRepository.findByRestaurantIdAndRoleNameIgnoreCaseAndIsActiveTrue(order.getRestaurant().getId(), "ADMIN");
        for (User admin : activeAdmins) {
            createNotification(admin, "PAYMENT_COMPLETED", "Payment Completed",
                    "Payment of " + payment.getAmount() + " completed for Order #" + order.getId(), "PAYMENT", payment.getId());
        }
    }

    private NotificationResponse mapToResponse(Notification n) {
        if (n == null) return null;
        return NotificationResponse.builder()
                .id(n.getId())
                .restaurantId(n.getRestaurant() != null ? n.getRestaurant().getId() : null)
                .recipientUserId(n.getRecipientUser() != null ? n.getRecipientUser().getId() : null)
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .referenceType(n.getReferenceType())
                .referenceId(n.getReferenceId())
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
