package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.OrderStatusHistory;
import com.restaurant.restaurantpos.entity.RestaurantSettings;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;
import com.restaurant.restaurantpos.repository.OrderItemRepository;
import com.restaurant.restaurantpos.repository.OrderRepository;
import com.restaurant.restaurantpos.repository.OrderStatusHistoryRepository;
import com.restaurant.restaurantpos.repository.RestaurantSettingsRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AutoAcceptService {

    private static final Logger logger = LoggerFactory.getLogger(AutoAcceptService.class);
    public static final int DEFAULT_DELAY_MINUTES = 5;

    private final OrderRepository orderRepository;
    private final RestaurantSettingsRepository settingsRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final UserRepository userRepository;
    private final KitchenOrderEventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final OrderItemRepository orderItemRepository;

    public AutoAcceptService(
            OrderRepository orderRepository,
            RestaurantSettingsRepository settingsRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            UserRepository userRepository,
            KitchenOrderEventPublisher eventPublisher,
            NotificationService notificationService) {
        this(orderRepository, settingsRepository, orderStatusHistoryRepository,
             userRepository, eventPublisher, notificationService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AutoAcceptService(
            OrderRepository orderRepository,
            RestaurantSettingsRepository settingsRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            UserRepository userRepository,
            KitchenOrderEventPublisher eventPublisher,
            NotificationService notificationService,
            OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.settingsRepository = settingsRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional
    public List<Long> processAutoAcceptOrders() {
        return processAutoAcceptOrders(DEFAULT_DELAY_MINUTES);
    }

    @Transactional
    public List<Long> processAutoAcceptOrders(int delayMinutes) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(delayMinutes);
        List<Order> eligibleOrders = orderRepository.findEligibleForAutoAccept(cutoffTime);
        List<Long> autoAcceptedIds = new ArrayList<>();

        logger.info("[AUTO-ACCEPT] scheduler tick - eligibleOrders={}", eligibleOrders.size());

        for (Order order : eligibleOrders) {
            try {
                logger.info("[AUTO-ACCEPT] restaurantId={} orderId={} createdAt={} currentTime={}",
                        order.getRestaurant().getId(), order.getId(), order.getCreatedAt(), LocalDateTime.now());
                boolean accepted = autoAcceptSingleOrder(order.getId());
                if (accepted) {
                    autoAcceptedIds.add(order.getId());
                }
            } catch (Exception e) {
                logger.error("[AUTO-ACCEPT] Failed to auto-accept order ID {}: {}", order.getId(), e.getMessage(), e);
            }
        }
        return autoAcceptedIds;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean autoAcceptSingleOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return false;
        }

        if (!"DINE_IN".equalsIgnoreCase(order.getOrderType())) {
            return false;
        }

        Optional<RestaurantSettings> settingsOpt = settingsRepository.findByRestaurantId(order.getRestaurant().getId());
        boolean autoAcceptEnabled = settingsOpt.isPresent() && Boolean.TRUE.equals(settingsOpt.get().getAutoAcceptOrders());
        logger.info("[AUTO-ACCEPT] restaurantId={} autoAcceptEnabled={}", order.getRestaurant().getId(), autoAcceptEnabled);

        if (!autoAcceptEnabled) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();

        if ("NEW".equalsIgnoreCase(order.getStatus())) {
            int updated = orderRepository.updateStatusToAcceptedIfNew(orderId, now);
            logger.info("[AUTO-ACCEPT] DB update count={} for orderId={}", updated, orderId);
            if (updated == 0) {
                return false;
            }

            order.setStatus("ACCEPTED");
            order.setUpdatedAt(now);

            if (orderItemRepository != null) {
                List<com.restaurant.restaurantpos.entity.OrderItem> items = orderItemRepository.findByOrderId(orderId);
                for (com.restaurant.restaurantpos.entity.OrderItem item : items) {
                    if ("NEW".equalsIgnoreCase(item.getStatus()) || item.getStatus() == null) {
                        item.setStatus("ACCEPTED");
                    }
                }
                orderItemRepository.saveAll(items);
            }

            User changedByUser = findChangedByUser(order);

            OrderStatusHistory history = new OrderStatusHistory();
            history.setOrder(order);
            history.setRestaurant(order.getRestaurant());
            history.setStatus("ACCEPTED");
            history.setChangedBy(changedByUser);
            orderStatusHistoryRepository.save(history);

            logger.info("[AUTO-ACCEPT] publishing ACCEPTED event for orderId={}", orderId);
            eventPublisher.publishOrderStatusChanged(order);
            notificationService.notifyOrderStatusChanged(order);

            logger.info("Auto-accepted order ID {} for restaurant ID {}", order.getId(), order.getRestaurant().getId());
            return true;
        }

        // Incremental batch auto-accept for orders already progressed beyond NEW
        if (orderItemRepository != null && ("ACCEPTED".equalsIgnoreCase(order.getStatus()) || "PREPARING".equalsIgnoreCase(order.getStatus()) || "READY".equalsIgnoreCase(order.getStatus()))) {
            List<com.restaurant.restaurantpos.entity.OrderItem> items = orderItemRepository.findByOrderId(orderId);
            List<com.restaurant.restaurantpos.entity.OrderItem> newItems = items.stream()
                    .filter(i -> "NEW".equalsIgnoreCase(i.getStatus()))
                    .collect(java.util.stream.Collectors.toList());

            if (newItems.isEmpty()) {
                return false;
            }

            java.util.Set<Integer> acceptedBatches = new java.util.HashSet<>();
            for (com.restaurant.restaurantpos.entity.OrderItem item : newItems) {
                item.setStatus("ACCEPTED");
                acceptedBatches.add(item.getBatchNumber() != null ? item.getBatchNumber() : 1);
            }
            orderItemRepository.saveAll(newItems);

            order.setUpdatedAt(now);
            orderRepository.save(order);

            User changedByUser = findChangedByUser(order);
            for (Integer batch : acceptedBatches) {
                OrderStatusHistory history = new OrderStatusHistory();
                history.setOrder(order);
                history.setRestaurant(order.getRestaurant());
                history.setStatus("ACCEPTED (Batch #" + batch + ")");
                history.setChangedBy(changedByUser);
                orderStatusHistoryRepository.save(history);

                eventPublisher.publishOrderStatusChanged(order, batch, batch > 1);
            }
            notificationService.notifyOrderStatusChanged(order);

            logger.info("Auto-accepted incremental batches {} for order ID {}", acceptedBatches, order.getId());
            return true;
        }

        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean autoAcceptBatch(Long orderId, Integer batchNumber) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || batchNumber == null) {
            return false;
        }
        if (!"DINE_IN".equalsIgnoreCase(order.getOrderType())) {
            return false;
        }

        Optional<RestaurantSettings> settingsOpt = settingsRepository.findByRestaurantId(order.getRestaurant().getId());
        boolean autoAcceptEnabled = settingsOpt.isPresent() && Boolean.TRUE.equals(settingsOpt.get().getAutoAcceptOrders());
        if (!autoAcceptEnabled) {
            return false;
        }

        if (orderItemRepository == null) {
            return false;
        }

        List<com.restaurant.restaurantpos.entity.OrderItem> batchItems = orderItemRepository.findByOrderIdAndBatchNumber(orderId, batchNumber);
        List<com.restaurant.restaurantpos.entity.OrderItem> newItems = batchItems.stream()
                .filter(i -> "NEW".equalsIgnoreCase(i.getStatus()))
                .collect(java.util.stream.Collectors.toList());

        if (newItems.isEmpty()) {
            return false;
        }

        for (com.restaurant.restaurantpos.entity.OrderItem item : newItems) {
            item.setStatus("ACCEPTED");
        }
        orderItemRepository.saveAll(newItems);

        LocalDateTime now = LocalDateTime.now();
        if ("NEW".equalsIgnoreCase(order.getStatus())) {
            order.setStatus("ACCEPTED");
        }
        order.setUpdatedAt(now);
        orderRepository.save(order);

        User changedByUser = findChangedByUser(order);
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setRestaurant(order.getRestaurant());
        history.setStatus("ACCEPTED (Batch #" + batchNumber + ")");
        history.setChangedBy(changedByUser);
        orderStatusHistoryRepository.save(history);

        eventPublisher.publishOrderStatusChanged(order, batchNumber, batchNumber > 1);
        notificationService.notifyOrderStatusChanged(order);
        return true;
    }

    private User findChangedByUser(Order order) {
        if (order.getWaiter() != null) {
            return order.getWaiter();
        }

        List<OrderStatusHistory> historyList = orderStatusHistoryRepository.findByOrderIdAndRestaurantIdOrderByCreatedAtAsc(order.getId(), order.getRestaurant().getId());
        if (!historyList.isEmpty() && historyList.get(0).getChangedBy() != null) {
            return historyList.get(0).getChangedBy();
        }

        List<User> restaurantUsers = userRepository.findByRestaurantId(order.getRestaurant().getId());
        if (!restaurantUsers.isEmpty()) {
            return restaurantUsers.get(0);
        }

        return null;
    }
}
