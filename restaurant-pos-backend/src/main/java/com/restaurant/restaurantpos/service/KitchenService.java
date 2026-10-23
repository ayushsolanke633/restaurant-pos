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
import com.restaurant.restaurantpos.exception.KitchenWorkflowDisabledException;
import com.restaurant.restaurantpos.repository.RestaurantSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final RestaurantSettingsRepository settingsRepository;

    public KitchenService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            UserRepository userRepository,
            SecurityUtils securityUtils,
            KitchenOrderEventPublisher eventPublisher,
            NotificationService notificationService) {
        this(orderRepository, orderItemRepository, orderStatusHistoryRepository,
             userRepository, securityUtils, eventPublisher, notificationService, null);
    }

    @Autowired
    public KitchenService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            UserRepository userRepository,
            SecurityUtils securityUtils,
            KitchenOrderEventPublisher eventPublisher,
            NotificationService notificationService,
            RestaurantSettingsRepository settingsRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.userRepository = userRepository;
        this.securityUtils = securityUtils;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
        this.settingsRepository = settingsRepository;
    }

    @Transactional(readOnly = true)
    public List<KitchenOrderResponse> getKitchenOrders(String statusFilter) {
        verifyKitchenRole();
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }

        if (!isKitchenModeEnabled(restaurantId)) {
            return Collections.emptyList();
        }

        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) {
            String status = statusFilter.trim().toUpperCase();
            if (!Arrays.asList("NEW", "ACCEPTED", "PREPARING", "READY").contains(status)) {
                throw new RuntimeException("Invalid status filter for kitchen queue");
            }
        }

        List<String> activeStatuses = Arrays.asList("NEW", "ACCEPTED", "PREPARING", "READY");
        List<Order> orders = orderRepository.findByRestaurantIdAndStatusInOrderByCreatedAtAsc(restaurantId, activeStatuses);

        List<KitchenOrderResponse> result = new java.util.ArrayList<>();

        for (Order order : orders) {
            List<OrderItem> items = findOrderItemsScoped(order.getId(), restaurantId);
            if (items.isEmpty()) {
                if (statusFilter == null || statusFilter.isBlank() || "ALL".equalsIgnoreCase(statusFilter)
                        || statusFilter.trim().equalsIgnoreCase(order.getStatus())) {
                    result.add(KitchenOrderResponse.builder()
                            .orderId(order.getId())
                            .orderType(order.getOrderType())
                            .tableId(order.getTable() != null ? order.getTable().getId() : null)
                            .tableNumber(order.getTable() != null ? order.getTable().getTableNumber() : null)
                            .status(order.getStatus())
                            .notes(order.getNotes())
                            .createdAt(order.getCreatedAt())
                            .updatedAt(order.getUpdatedAt())
                            .batchNumber(1)
                            .totalBatches(1)
                            .isUpdate(false)
                            .batches(Collections.singletonList(com.restaurant.restaurantpos.dto.kitchen.KitchenBatchSummary.builder()
                                    .batchNumber(1)
                                    .status(order.getStatus())
                                    .itemCount(0)
                                    .totalQuantity(0)
                                    .createdAt(order.getCreatedAt())
                                    .build()))
                            .items(Collections.emptyList())
                            .build());
                }
                continue;
            }

            Map<Integer, List<OrderItem>> batchMap = items.stream()
                    .collect(Collectors.groupingBy(
                            i -> i.getBatchNumber() != null ? i.getBatchNumber() : 1,
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));

            int totalBatches = batchMap.keySet().size();

            List<com.restaurant.restaurantpos.dto.kitchen.KitchenBatchSummary> summaries = new java.util.ArrayList<>();
            for (Map.Entry<Integer, List<OrderItem>> entry : batchMap.entrySet()) {
                int bNum = entry.getKey();
                List<OrderItem> bItems = entry.getValue();
                String bStatus = computeBatchStatus(bItems, order.getStatus());
                summaries.add(com.restaurant.restaurantpos.dto.kitchen.KitchenBatchSummary.builder()
                        .batchNumber(bNum)
                        .status(bStatus)
                        .itemCount(bItems.size())
                        .totalQuantity(bItems.stream().mapToInt(i -> i.getQuantity() != null ? i.getQuantity() : 1).sum())
                        .createdAt(order.getCreatedAt())
                        .build());
            }

            for (Map.Entry<Integer, List<OrderItem>> entry : batchMap.entrySet()) {
                int bNum = entry.getKey();
                List<OrderItem> bItems = entry.getValue();
                String bStatus = computeBatchStatus(bItems, order.getStatus());

                if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) {
                    if (!statusFilter.trim().equalsIgnoreCase(bStatus)) {
                        continue;
                    }
                }

                List<KitchenOrderItemResponse> itemResponses = bItems.stream()
                        .map(i -> KitchenOrderItemResponse.builder()
                                .id(i.getId())
                                .menuItemId(i.getMenuItem() != null ? i.getMenuItem().getId() : null)
                                .menuItemName(i.getMenuItem() != null ? i.getMenuItem().getName() : null)
                                .quantity(i.getQuantity())
                                .notes(i.getNotes())
                                .categoryName(i.getMenuItem() != null && i.getMenuItem().getCategory() != null ? i.getMenuItem().getCategory().getName() : null)
                                .status(i.getStatus() != null ? i.getStatus() : bStatus)
                                .batchNumber(bNum)
                                .build())
                        .collect(Collectors.toList());

                result.add(KitchenOrderResponse.builder()
                        .orderId(order.getId())
                        .orderType(order.getOrderType())
                        .tableId(order.getTable() != null ? order.getTable().getId() : null)
                        .tableNumber(order.getTable() != null ? order.getTable().getTableNumber() : null)
                        .status(bStatus)
                        .notes(order.getNotes())
                        .createdAt(order.getCreatedAt())
                        .updatedAt(order.getUpdatedAt())
                        .batchNumber(bNum)
                        .totalBatches(totalBatches)
                        .isUpdate(bNum > 1)
                        .batches(summaries)
                        .items(itemResponses)
                        .build());
            }
        }

        return result;
    }

    @Transactional(readOnly = true)
    public KitchenOrderResponse getKitchenOrderById(Long id) {
        return getKitchenOrderById(id, null);
    }

    @Transactional(readOnly = true)
    public KitchenOrderResponse getKitchenOrderById(Long id, Integer batchNumber) {
        verifyKitchenRole();
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }

        Order order = findOrderScoped(id, restaurantId);
        List<OrderItem> allItems = findOrderItemsScoped(id, restaurantId);

        if (batchNumber != null) {
            return mapToKitchenOrderResponseForBatch(order, batchNumber, allItems);
        }

        return mapToKitchenOrderResponse(order, allItems);
    }

    @Transactional
    public KitchenOrderResponse acceptOrder(Long id) {
        verifyKitchenRole();
        return transitionBatchStatus(id, null, "NEW", "ACCEPTED");
    }

    @Transactional
    public KitchenOrderResponse acceptBatch(Long id, Integer batchNumber) {
        verifyKitchenRole();
        return transitionBatchStatus(id, batchNumber, "NEW", "ACCEPTED");
    }

    @Transactional
    public KitchenOrderResponse prepareOrder(Long id) {
        verifyKitchenRole();
        return transitionBatchStatus(id, null, "ACCEPTED", "PREPARING");
    }

    @Transactional
    public KitchenOrderResponse prepareBatch(Long id, Integer batchNumber) {
        verifyKitchenRole();
        return transitionBatchStatus(id, batchNumber, "ACCEPTED", "PREPARING");
    }

    @Transactional
    public KitchenOrderResponse readyOrder(Long id) {
        verifyKitchenRole();
        return transitionBatchStatus(id, null, "PREPARING", "READY");
    }

    @Transactional
    public KitchenOrderResponse readyBatch(Long id, Integer batchNumber) {
        verifyKitchenRole();
        return transitionBatchStatus(id, batchNumber, "PREPARING", "READY");
    }

    private KitchenOrderResponse transitionBatchStatus(Long id, Integer batchNumber, String expectedCurrentStatus, String targetStatus) {
        Long currentUserId = securityUtils.getCurrentUserId();
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        if (!isKitchenModeEnabled(restaurantId)) {
            throw new KitchenWorkflowDisabledException("Kitchen workflow is disabled for this restaurant");
        }

        Order order = findOrderScoped(id, restaurantId);
        List<OrderItem> allItems = findOrderItemsScoped(id, restaurantId);

        if (allItems.isEmpty()) {
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

            return mapToKitchenOrderResponse(savedOrder, allItems);
        }

        List<OrderItem> targetItems;
        if (batchNumber != null) {
            targetItems = allItems.stream()
                    .filter(i -> (i.getBatchNumber() != null ? i.getBatchNumber() : 1) == batchNumber)
                    .collect(Collectors.toList());
            if (targetItems.isEmpty()) {
                throw new RuntimeException("Batch " + batchNumber + " not found for order " + id);
            }
        } else {
            targetItems = allItems.stream()
                    .filter(i -> expectedCurrentStatus.equalsIgnoreCase(i.getStatus() != null ? i.getStatus() : order.getStatus()))
                    .collect(Collectors.toList());
            if (targetItems.isEmpty()) {
                if (!expectedCurrentStatus.equalsIgnoreCase(order.getStatus())) {
                    throw new RuntimeException("Invalid status transition to " + targetStatus);
                }
                targetItems = allItems;
            }
        }

        for (OrderItem item : targetItems) {
            String itemStatus = item.getStatus() != null ? item.getStatus() : order.getStatus();
            if (!expectedCurrentStatus.equalsIgnoreCase(itemStatus)) {
                throw new RuntimeException("Invalid status transition to " + targetStatus);
            }
        }

        for (OrderItem item : targetItems) {
            item.setStatus(targetStatus);
        }
        orderItemRepository.saveAll(targetItems);

        String newOrderStatus = computeOverallOrderStatus(allItems);
        order.setStatus(newOrderStatus);
        Order savedOrder = orderRepository.save(order);

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(savedOrder);
        history.setRestaurant(savedOrder.getRestaurant());
        history.setStatus(targetStatus + (batchNumber != null ? " (Batch #" + batchNumber + ")" : ""));
        history.setChangedBy(userRepository.getReferenceById(currentUserId));
        orderStatusHistoryRepository.save(history);

        if (batchNumber != null) {
            eventPublisher.publishOrderStatusChanged(savedOrder, batchNumber, batchNumber > 1);
        } else {
            eventPublisher.publishOrderStatusChanged(savedOrder);
        }
        notificationService.notifyOrderStatusChanged(savedOrder);

        int resolvedBatch = (batchNumber != null) ? batchNumber :
                (targetItems.get(0).getBatchNumber() != null ? targetItems.get(0).getBatchNumber() : 1);
        return mapToKitchenOrderResponseForBatch(savedOrder, resolvedBatch, allItems);
    }

    private String computeOverallOrderStatus(List<OrderItem> allItems) {
        if (allItems.isEmpty()) {
            return "READY";
        }
        boolean allReady = allItems.stream()
                .allMatch(i -> "READY".equalsIgnoreCase(i.getStatus()));
        if (allReady) {
            return "READY";
        }
        boolean anyPreparing = allItems.stream()
                .anyMatch(i -> "PREPARING".equalsIgnoreCase(i.getStatus()) || "READY".equalsIgnoreCase(i.getStatus()));
        if (anyPreparing) {
            return "PREPARING";
        }
        boolean anyAccepted = allItems.stream()
                .anyMatch(i -> "ACCEPTED".equalsIgnoreCase(i.getStatus()));
        if (anyAccepted) {
            return "ACCEPTED";
        }
        return "NEW";
    }

    private String computeBatchStatus(List<OrderItem> batchItems, String fallbackStatus) {
        if (batchItems == null || batchItems.isEmpty()) {
            return fallbackStatus != null ? fallbackStatus : "NEW";
        }
        boolean allReady = batchItems.stream().allMatch(i -> "READY".equalsIgnoreCase(i.getStatus()));
        if (allReady) {
            return "READY";
        }
        boolean anyPreparing = batchItems.stream().anyMatch(i -> "PREPARING".equalsIgnoreCase(i.getStatus()));
        if (anyPreparing) {
            return "PREPARING";
        }
        boolean anyAccepted = batchItems.stream().anyMatch(i -> "ACCEPTED".equalsIgnoreCase(i.getStatus()));
        if (anyAccepted) {
            return "ACCEPTED";
        }
        return "NEW";
    }

    private KitchenOrderResponse mapToKitchenOrderResponseForBatch(Order order, Integer batchNumber, List<OrderItem> allItems) {
        Map<Integer, List<OrderItem>> batchMap = allItems.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getBatchNumber() != null ? i.getBatchNumber() : 1,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        int totalBatches = Math.max(1, batchMap.keySet().size());

        List<com.restaurant.restaurantpos.dto.kitchen.KitchenBatchSummary> summaries = new java.util.ArrayList<>();
        for (Map.Entry<Integer, List<OrderItem>> entry : batchMap.entrySet()) {
            int bNum = entry.getKey();
            List<OrderItem> bItems = entry.getValue();
            String bStatus = computeBatchStatus(bItems, order.getStatus());
            summaries.add(com.restaurant.restaurantpos.dto.kitchen.KitchenBatchSummary.builder()
                    .batchNumber(bNum)
                    .status(bStatus)
                    .itemCount(bItems.size())
                    .totalQuantity(bItems.stream().mapToInt(i -> i.getQuantity() != null ? i.getQuantity() : 1).sum())
                    .createdAt(order.getCreatedAt())
                    .build());
        }

        int targetBatch = (batchNumber != null) ? batchNumber : 1;
        List<OrderItem> targetItems = batchMap.getOrDefault(targetBatch, Collections.emptyList());
        String batchStatus = computeBatchStatus(targetItems, order.getStatus());

        List<KitchenOrderItemResponse> itemResponses = targetItems.stream()
                .map(i -> KitchenOrderItemResponse.builder()
                        .id(i.getId())
                        .menuItemId(i.getMenuItem() != null ? i.getMenuItem().getId() : null)
                        .menuItemName(i.getMenuItem() != null ? i.getMenuItem().getName() : null)
                        .quantity(i.getQuantity())
                        .notes(i.getNotes())
                        .categoryName(i.getMenuItem() != null && i.getMenuItem().getCategory() != null ? i.getMenuItem().getCategory().getName() : null)
                        .status(i.getStatus() != null ? i.getStatus() : batchStatus)
                        .batchNumber(targetBatch)
                        .build())
                .collect(Collectors.toList());

        return KitchenOrderResponse.builder()
                .orderId(order.getId())
                .orderType(order.getOrderType())
                .tableId(order.getTable() != null ? order.getTable().getId() : null)
                .tableNumber(order.getTable() != null ? order.getTable().getTableNumber() : null)
                .status(batchStatus)
                .notes(order.getNotes())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .batchNumber(targetBatch)
                .totalBatches(totalBatches)
                .isUpdate(targetBatch > 1)
                .batches(summaries)
                .items(itemResponses)
                .build();
    }

    private KitchenOrderResponse mapToKitchenOrderResponse(Order order, List<OrderItem> allItems) {
        Map<Integer, List<OrderItem>> batchMap = allItems.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getBatchNumber() != null ? i.getBatchNumber() : 1,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        int totalBatches = Math.max(1, batchMap.keySet().size());

        List<com.restaurant.restaurantpos.dto.kitchen.KitchenBatchSummary> summaries = new java.util.ArrayList<>();
        for (Map.Entry<Integer, List<OrderItem>> entry : batchMap.entrySet()) {
            int bNum = entry.getKey();
            List<OrderItem> bItems = entry.getValue();
            String bStatus = computeBatchStatus(bItems, order.getStatus());
            summaries.add(com.restaurant.restaurantpos.dto.kitchen.KitchenBatchSummary.builder()
                    .batchNumber(bNum)
                    .status(bStatus)
                    .itemCount(bItems.size())
                    .totalQuantity(bItems.stream().mapToInt(i -> i.getQuantity() != null ? i.getQuantity() : 1).sum())
                    .createdAt(order.getCreatedAt())
                    .build());
        }

        List<KitchenOrderItemResponse> itemResponses = allItems.stream()
                .map(i -> KitchenOrderItemResponse.builder()
                        .id(i.getId())
                        .menuItemId(i.getMenuItem() != null ? i.getMenuItem().getId() : null)
                        .menuItemName(i.getMenuItem() != null ? i.getMenuItem().getName() : null)
                        .quantity(i.getQuantity())
                        .notes(i.getNotes())
                        .categoryName(i.getMenuItem() != null && i.getMenuItem().getCategory() != null ? i.getMenuItem().getCategory().getName() : null)
                        .status(i.getStatus() != null ? i.getStatus() : order.getStatus())
                        .batchNumber(i.getBatchNumber() != null ? i.getBatchNumber() : 1)
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
                .batchNumber(totalBatches == 1 ? 1 : null)
                .totalBatches(totalBatches)
                .isUpdate(totalBatches > 1)
                .batches(summaries)
                .items(itemResponses)
                .build();
    }

    private void verifyKitchenRole() {
        String role = securityUtils.getCurrentUserRole();
        if (!"KITCHEN".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private List<OrderItem> findOrderItemsScoped(Long orderId, Long restaurantId) {
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        List<OrderItem> items = orderItemRepository.findByOrderIdAndRestaurantId(orderId, restaurantId);
        if (items != null && !items.isEmpty()) {
            return items;
        }
        List<OrderItem> fallback = orderItemRepository.findByOrderId(orderId);
        return fallback.stream()
                .filter(i -> i.getRestaurant() == null || restaurantId.equals(i.getRestaurant().getId()))
                .collect(Collectors.toList());
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

    private boolean isKitchenModeEnabled(Long restaurantId) {
        if (restaurantId == null || settingsRepository == null) {
            return true;
        }
        return settingsRepository.findByRestaurantId(restaurantId)
                .map(s -> s.getKitchenEnabled() != null ? s.getKitchenEnabled() : true)
                .orElse(true);
    }
}
