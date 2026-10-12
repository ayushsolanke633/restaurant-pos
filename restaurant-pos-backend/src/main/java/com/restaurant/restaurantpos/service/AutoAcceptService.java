package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.OrderStatusHistory;
import com.restaurant.restaurantpos.entity.RestaurantSettings;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;
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

    public AutoAcceptService(
            OrderRepository orderRepository,
            RestaurantSettingsRepository settingsRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            UserRepository userRepository,
            KitchenOrderEventPublisher eventPublisher,
            NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.settingsRepository = settingsRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
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

        if (!"NEW".equalsIgnoreCase(order.getStatus())) {
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
        int updated = orderRepository.updateStatusToAcceptedIfNew(orderId, now);
        logger.info("[AUTO-ACCEPT] DB update count={} for orderId={}", updated, orderId);
        if (updated == 0) {
            return false;
        }

        order.setStatus("ACCEPTED");
        order.setUpdatedAt(now);

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
