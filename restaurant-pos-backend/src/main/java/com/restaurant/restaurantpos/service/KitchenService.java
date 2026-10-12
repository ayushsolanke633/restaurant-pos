package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.dto.kitchen.KitchenOrderItemResponse;
import com.restaurant.restaurantpos.dto.kitchen.KitchenOrderResponse;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.OrderItem;
import com.restaurant.restaurantpos.entity.OrderStatusHistory;
import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;
import com.restaurant.restaurantpos.repository.OrderItemRepository;
import com.restaurant.restaurantpos.repository.OrderRepository;
import com.restaurant.restaurantpos.repository.OrderStatusHistoryRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class KitchenService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;
    private final KitchenOrderEventPublisher eventPublisher;
    private final NotificationService notificationService;

    public KitchenService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            UserRepository userRepository,
            SecurityUtils securityUtils,
            KitchenOrderEventPublisher eventPublisher,
            NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.userRepository = userRepository;
        this.securityUtils = securityUtils;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<KitchenOrderResponse> getKitchenOrders(String statusFilter) {
        verifyKitchenRole();
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }

        List<Order> orders;
        if (statusFilter != null && !statusFilter.isBlank()) {
            String status = statusFilter.trim().toUpperCase();
            if (!Arrays.asList("NEW", "ACCEPTED", "PREPARING", "READY").contains(status)) {
                throw new RuntimeException("Invalid status filter for kitchen queue");
            }
            orders = orderRepository.findByRestaurantIdAndStatusOrderByCreatedAtAsc(restaurantId, status);
        } else {
            List<String> activeStatuses = Arrays.asList("NEW", "ACCEPTED", "PREPARING", "READY");
            orders = orderRepository.findByRestaurantIdAndStatusInOrderByCreatedAtAsc(restaurantId, activeStatuses);
        }

        return orders.stream()
                .map(this::mapToKitchenOrderResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public KitchenOrderResponse getKitchenOrderById(Long id) {
        verifyKitchenRole();
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }

        Order order = findOrderScoped(id, restaurantId);

        return mapToKitchenOrderResponse(order);
    }

    @Transactional
    public KitchenOrderResponse acceptOrder(Long id) {
        verifyKitchenRole();
        return transitionStatus(id, "NEW", "ACCEPTED");
    }

    @Transactional
    public KitchenOrderResponse prepareOrder(Long id) {
        verifyKitchenRole();
        return transitionStatus(id, "ACCEPTED", "PREPARING");
    }

    @Transactional
    public KitchenOrderResponse readyOrder(Long id) {
        verifyKitchenRole();
        return transitionStatus(id, "PREPARING", "READY");
    }

    private KitchenOrderResponse transitionStatus(Long id, String expectedCurrentStatus, String targetStatus) {
        Long currentUserId = securityUtils.getCurrentUserId();
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }

        Order order = findOrderScoped(id, restaurantId);

        if (!expectedCurrentStatus.equalsIgnoreCase(order.getStatus())) {
            throw new RuntimeException("Invalid status transition to " + targetStatus);
        }

        order.setStatus(targetStatus);
        Order savedOrder = orderRepository.save(order);

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(savedOrder);
        history.setRestaurant(savedOrder.getRestaurant());
        history.setStatus(targetStatus);
        history.setChangedBy(userRepository.getReferenceById(currentUserId));
        orderStatusHistoryRepository.save(history);

        eventPublisher.publishOrderStatusChanged(savedOrder);
        notificationService.notifyOrderStatusChanged(savedOrder);

        return mapToKitchenOrderResponse(savedOrder);
    }

    private void verifyKitchenRole() {
        String role = securityUtils.getCurrentUserRole();
        if (!"KITCHEN".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private KitchenOrderResponse mapToKitchenOrderResponse(Order order) {
        Long restaurantId = order.getRestaurant() != null ? order.getRestaurant().getId() : null;
        List<OrderItem> items = (restaurantId != null) ?
                orderItemRepository.findByOrderIdAndRestaurantId(order.getId(), restaurantId) :
                orderItemRepository.findByOrderId(order.getId());
        if (items.isEmpty()) {
            items = orderItemRepository.findByOrderId(order.getId()).stream()
                    .filter(i -> i.getRestaurant() == null || (restaurantId != null && restaurantId.equals(i.getRestaurant().getId())))
                    .collect(Collectors.toList());
        }

        List<KitchenOrderItemResponse> itemResponses = items.stream()
                .map(i -> KitchenOrderItemResponse.builder()
                        .id(i.getId())
                        .menuItemId(i.getMenuItem() != null ? i.getMenuItem().getId() : null)
                        .menuItemName(i.getMenuItem() != null ? i.getMenuItem().getName() : null)
                        .quantity(i.getQuantity())
                        .notes(i.getNotes())
                        .categoryName(i.getMenuItem() != null && i.getMenuItem().getCategory() != null ? i.getMenuItem().getCategory().getName() : null)
                        .build())
                .collect(Collectors.toList());

        return KitchenOrderResponse.builder()
                .orderId(order.getId())
                .orderType(order.getOrderType())
                .tableId(order.getTable() != null ? order.getTable().getId() : null)
                .tableNumber(order.getTable() != null ? order.getTable().getTableNumber() : null)
                .status(order.getStatus())
                .notes(order.getNotes())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(itemResponses)
                .build();
    }

    private Order findOrderScoped(Long id, Long restaurantId) {
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        java.util.Optional<Order> opt = orderRepository.findByIdAndRestaurantId(id, restaurantId);
        if (opt.isPresent()) {
            return opt.get();
        }
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));
        if (order.getRestaurant() == null || !restaurantId.equals(order.getRestaurant().getId())) {
            throw new RuntimeException("Order not found with id: " + id);
        }
        return order;
    }
}
