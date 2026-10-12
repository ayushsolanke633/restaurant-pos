package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.dto.OrderItemRequest;
import com.restaurant.restaurantpos.dto.OrderItemResponse;
import com.restaurant.restaurantpos.dto.OrderRequest;
import com.restaurant.restaurantpos.dto.OrderResponse;
import com.restaurant.restaurantpos.entity.Customer;
import com.restaurant.restaurantpos.entity.MenuItem;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.OrderItem;
import com.restaurant.restaurantpos.entity.OrderStatusHistory;
import com.restaurant.restaurantpos.entity.RestaurantTable;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.repository.BillRepository;
import com.restaurant.restaurantpos.repository.CustomerRepository;
import com.restaurant.restaurantpos.repository.MenuItemRepository;
import com.restaurant.restaurantpos.repository.OrderItemRepository;
import com.restaurant.restaurantpos.repository.OrderRepository;
import com.restaurant.restaurantpos.repository.OrderStatusHistoryRepository;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RestaurantTableRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantTableRepository tableRepository;
    private final MenuItemRepository menuItemRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final SecurityUtils securityUtils;
    private final KitchenOrderEventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final BillRepository billRepository;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            RestaurantRepository restaurantRepository,
            RestaurantTableRepository tableRepository,
            MenuItemRepository menuItemRepository,
            UserRepository userRepository,
            CustomerRepository customerRepository,
            SecurityUtils securityUtils,
            KitchenOrderEventPublisher eventPublisher,
            NotificationService notificationService,
            BillRepository billRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.restaurantRepository = restaurantRepository;
        this.tableRepository = tableRepository;
        this.menuItemRepository = menuItemRepository;
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.securityUtils = securityUtils;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
        this.billRepository = billRepository;
    }

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        Long currentUserId = securityUtils.getCurrentUserId();
        String currentRole = securityUtils.getCurrentUserRole();

        String orderType = request.getOrderType() != null ? request.getOrderType().toUpperCase() : "";
        if (!"DINE_IN".equals(orderType) && !"TAKEAWAY".equals(orderType)) {
            throw new RuntimeException("Invalid order type");
        }

        if ("KITCHEN".equalsIgnoreCase(currentRole)) {
            throw new AccessDeniedException("Access denied");
        }

        if ("TAKEAWAY".equals(orderType) && !"ADMIN".equalsIgnoreCase(currentRole)) {
            throw new AccessDeniedException("Access denied");
        }

        RestaurantTable table = null;
        User waiter = null;
        Customer customer = null;

        if ("DINE_IN".equals(orderType)) {
            if (request.getTableId() == null) {
                throw new RuntimeException("DINE_IN orders require a table");
            }
            table = findTableScoped(request.getTableId(), restaurantId);

            boolean hasActiveOrder = orderRepository.existsByRestaurantIdAndTableIdAndOrderTypeAndStatusIn(
                    restaurantId,
                    table.getId(),
                    "DINE_IN",
                    Arrays.asList("NEW", "ACCEPTED", "PREPARING", "READY")
            );
            if (hasActiveOrder) {
                Optional<Order> activeOrderOpt = orderRepository.findFirstByRestaurantIdAndTableIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                        restaurantId,
                        table.getId(),
                        "DINE_IN",
                        Arrays.asList("NEW", "ACCEPTED", "PREPARING", "READY")
                );
                if (activeOrderOpt.isPresent() && activeOrderOpt.get().getWaiter() != null
                        && !activeOrderOpt.get().getWaiter().getId().equals(currentUserId)) {
                    throw new RuntimeException("This table already has an active order assigned to another waiter");
                }
                throw new RuntimeException("Table already has an active order");
            }

            if (!"AVAILABLE".equalsIgnoreCase(table.getStatus())) {
                Optional<Order> activeOrderOpt = orderRepository.findFirstByRestaurantIdAndTableIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                        restaurantId,
                        table.getId(),
                        "DINE_IN",
                        Arrays.asList("NEW", "ACCEPTED", "PREPARING", "READY")
                );
                if (activeOrderOpt.isPresent() && activeOrderOpt.get().getWaiter() != null
                        && !activeOrderOpt.get().getWaiter().getId().equals(currentUserId)) {
                    throw new RuntimeException("This table already has an active order assigned to another waiter");
                }
                throw new RuntimeException("Table is not available");
            }

            if ("WAITER".equalsIgnoreCase(currentRole)) {
                waiter = findUserScoped(currentUserId, restaurantId);
            } else {
                waiter = null;
            }

            table.setStatus("OCCUPIED");
            tableRepository.save(table);
        } else {
            if (request.getTableId() != null) {
                throw new RuntimeException("TAKEAWAY orders cannot have a table");
            }
            waiter = null;
        }

        if (request.getCustomerId() != null) {
            customer = findCustomerScoped(request.getCustomerId(), restaurantId);
        }

        Map<Long, DeduplicatedItem> itemMap = deduplicateItems(request.getItems());

        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItemsToSave = new ArrayList<>();

        for (DeduplicatedItem item : itemMap.values()) {
            MenuItem menuItem = findMenuItemScoped(item.menuItemId, restaurantId);

            if (!Boolean.TRUE.equals(menuItem.getIsAvailable())) {
                throw new RuntimeException("Menu item is not available");
            }

            if (menuItem.getCategory() != null && Boolean.FALSE.equals(menuItem.getCategory().getIsActive())) {
                throw new RuntimeException("This category is inactive. Please refresh the menu and try again.");
            }

            BigDecimal unitPrice = menuItem.getPrice();
            BigDecimal itemSubtotal = unitPrice.multiply(BigDecimal.valueOf(item.quantity));
            subtotal = subtotal.add(itemSubtotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setRestaurant(restaurantRepository.getReferenceById(restaurantId));
            orderItem.setMenuItem(menuItem);
            orderItem.setQuantity(item.quantity);
            orderItem.setUnitPrice(unitPrice);
            orderItem.setNotes(item.notes);
            orderItem.setSubtotal(itemSubtotal);
            orderItemsToSave.add(orderItem);
        }

        BigDecimal taxAmount = BigDecimal.ZERO.setScale(2);
        BigDecimal discountAmount = BigDecimal.ZERO.setScale(2);
        BigDecimal totalAmount = subtotal.add(taxAmount).subtract(discountAmount);

        Order order = new Order();
        order.setRestaurant(restaurantRepository.getReferenceById(restaurantId));
        order.setTable(table);
        order.setCustomer(customer);
        order.setWaiter(waiter);
        order.setOrderType(orderType);
        order.setStatus("NEW");
        order.setSubtotal(subtotal);
        order.setTaxAmount(taxAmount);
        order.setDiscountAmount(discountAmount);
        order.setTotalAmount(totalAmount);
        order.setNotes(request.getNotes());

        Order savedOrder = orderRepository.save(order);

        for (OrderItem oi : orderItemsToSave) {
            oi.setOrder(savedOrder);
            oi.setRestaurant(savedOrder.getRestaurant());
        }
        List<OrderItem> savedItems = orderItemRepository.saveAll(orderItemsToSave);

        saveStatusHistory(savedOrder, "NEW", currentUserId);

        eventPublisher.publishOrderCreated(savedOrder);
        notificationService.notifyOrderCreated(savedOrder);

        return mapToResponse(savedOrder, savedItems);
    }


    public OrderResponse getActiveOrderByTable(Long tableId) {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        List<String> activeStatuses = Arrays.asList("NEW", "ACCEPTED", "PREPARING", "READY");

        // 1. Try by table primary key ID
        Optional<RestaurantTable> tableOpt = tableRepository.findByIdAndRestaurantId(tableId, restaurantId);
        if (tableOpt.isPresent()) {
            RestaurantTable table = tableOpt.get();
            Optional<Order> orderOpt = orderRepository.findFirstByRestaurantIdAndTableIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                    restaurantId, table.getId(), "DINE_IN", activeStatuses
            );
            if (orderOpt.isPresent()) {
                List<OrderItem> items = findOrderItemsScoped(orderOpt.get().getId(), restaurantId);
                return mapToResponse(orderOpt.get(), items);
            }
        }

        // 2. Fallback: Try by table number (e.g. if tableId passed is tableNumber such as 3451 or 7)
        Optional<RestaurantTable> tableByNumOpt = tableRepository.findByRestaurantIdAndTableNumber(restaurantId, tableId.intValue());
        if (tableByNumOpt.isPresent()) {
            RestaurantTable table = tableByNumOpt.get();
            Optional<Order> orderOpt = orderRepository.findFirstByRestaurantIdAndTableIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                    restaurantId, table.getId(), "DINE_IN", activeStatuses
            );
            if (orderOpt.isPresent()) {
                List<OrderItem> items = findOrderItemsScoped(orderOpt.get().getId(), restaurantId);
                return mapToResponse(orderOpt.get(), items);
            }
            throw new RuntimeException("No active order found for table " + table.getTableNumber());
        }

        if (tableOpt.isPresent()) {
            throw new RuntimeException("No active order found for table " + tableOpt.get().getTableNumber());
        }

        throw new RuntimeException("Table not found");
    }

    public List<OrderResponse> getWaiterOrderHistory(String status) {
        String currentRole = securityUtils.getCurrentUserRole();
        if ("KITCHEN".equalsIgnoreCase(currentRole)) {
            throw new AccessDeniedException("Access denied");
        }

        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        Long waiterId = securityUtils.getCurrentUserId();

        List<Order> orders;
        if (status != null && !status.isBlank()) {
            String upperStatus = status.trim().toUpperCase();
            if ("COMPLETED".equals(upperStatus) || "CANCELLED".equals(upperStatus)) {
                orders = orderRepository.findByRestaurantIdAndWaiterIdAndOrderTypeAndStatusOrderByCreatedAtDesc(
                        restaurantId, waiterId, "DINE_IN", upperStatus
                );
            } else {
                orders = Collections.emptyList();
            }
        } else {
            orders = orderRepository.findByRestaurantIdAndWaiterIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
                    restaurantId, waiterId, "DINE_IN", Arrays.asList("COMPLETED", "CANCELLED")
            );
        }

        return orders.stream()
                .map(o -> mapToResponse(o, findOrderItemsScoped(o.getId(), restaurantId)))
                .collect(Collectors.toList());
    }

    public List<OrderResponse> getAllOrders(String status, String orderType) {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        String currentRole = securityUtils.getCurrentUserRole();
        Long currentUserId = securityUtils.getCurrentUserId();

        List<Order> orders;

        if ("WAITER".equalsIgnoreCase(currentRole)) {
            if (status != null && !status.isBlank()) {
                orders = orderRepository.findByRestaurantIdAndWaiterIdAndOrderTypeAndStatusOrderByCreatedAtDesc(
                        restaurantId, currentUserId, "DINE_IN", status.toUpperCase()
                );
            } else {
                orders = orderRepository.findByRestaurantIdAndWaiterIdAndOrderTypeOrderByCreatedAtDesc(
                        restaurantId, currentUserId, "DINE_IN"
                );
            }
        } else {
            if (status != null && !status.isBlank() && orderType != null && !orderType.isBlank()) {
                orders = orderRepository.findByRestaurantIdAndStatusAndOrderTypeOrderByCreatedAtDesc(restaurantId, status.toUpperCase(), orderType.toUpperCase());
            } else if (status != null && !status.isBlank()) {
                orders = orderRepository.findByRestaurantIdAndStatusOrderByCreatedAtDesc(restaurantId, status.toUpperCase());
            } else if (orderType != null && !orderType.isBlank()) {
                orders = orderRepository.findByRestaurantIdAndOrderTypeOrderByCreatedAtDesc(restaurantId, orderType.toUpperCase());
            } else {
                orders = orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
            }
        }

        return orders.stream()
                .map(o -> mapToResponse(o, findOrderItemsScoped(o.getId(), restaurantId)))
                .collect(Collectors.toList());
    }

    public OrderResponse getOrderById(Long id) {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        String currentRole = securityUtils.getCurrentUserRole();
        Long currentUserId = securityUtils.getCurrentUserId();

        Order order = findOrderScoped(id, restaurantId);

        if ("WAITER".equalsIgnoreCase(currentRole)) {
            if (!"DINE_IN".equalsIgnoreCase(order.getOrderType())) {
                throw new AccessDeniedException("Access denied");
            }
            if (order.getWaiter() == null || !order.getWaiter().getId().equals(currentUserId)) {
                throw new AccessDeniedException("Access denied");
            }
        }

        List<OrderItem> items = findOrderItemsScoped(id, restaurantId);
        return mapToResponse(order, items);
    }

    @Transactional
    public OrderResponse updateOrder(Long id, OrderRequest request) {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        Long currentUserId = securityUtils.getCurrentUserId();
        String currentRole = securityUtils.getCurrentUserRole();

        Order order = findOrderScoped(id, restaurantId);

        if (!"NEW".equalsIgnoreCase(order.getStatus())) {
            throw new RuntimeException("Order can only be modified while status is NEW");
        }

        if ("KITCHEN".equalsIgnoreCase(currentRole)) {
            throw new AccessDeniedException("Access denied");
        }

        if ("DINE_IN".equalsIgnoreCase(order.getOrderType())) {
            if ("WAITER".equalsIgnoreCase(currentRole)) {
                if (order.getWaiter() == null || !order.getWaiter().getId().equals(currentUserId)) {
                    throw new AccessDeniedException("Access denied");
                }
            }
        } else if ("TAKEAWAY".equalsIgnoreCase(order.getOrderType())) {
            if (!"ADMIN".equalsIgnoreCase(currentRole)) {
                throw new AccessDeniedException("Access denied");
            }
        }

        if (request.getTableId() != null && order.getTable() != null && !request.getTableId().equals(order.getTable().getId())) {
            throw new AccessDeniedException("Access denied: Cannot change table assignment of an existing order");
        }

        if (request.getCustomerId() != null) {
            Customer customer = findCustomerScoped(request.getCustomerId(), restaurantId);
            order.setCustomer(customer);
        }

        Map<Long, DeduplicatedItem> itemMap = deduplicateItems(request.getItems());
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItemsToSave = new ArrayList<>();

        for (DeduplicatedItem item : itemMap.values()) {
            MenuItem menuItem = findMenuItemScoped(item.menuItemId, restaurantId);

            if (!Boolean.TRUE.equals(menuItem.getIsAvailable())) {
                throw new RuntimeException("Menu item is not available");
            }

            if (menuItem.getCategory() != null && Boolean.FALSE.equals(menuItem.getCategory().getIsActive())) {
                throw new RuntimeException("This category is inactive. Please refresh the menu and try again.");
            }

            BigDecimal unitPrice = menuItem.getPrice();
            BigDecimal itemSubtotal = unitPrice.multiply(BigDecimal.valueOf(item.quantity));
            subtotal = subtotal.add(itemSubtotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setRestaurant(order.getRestaurant());
            orderItem.setOrder(order);
            orderItem.setMenuItem(menuItem);
            orderItem.setQuantity(item.quantity);
            orderItem.setUnitPrice(unitPrice);
            orderItem.setNotes(item.notes);
            orderItem.setSubtotal(itemSubtotal);
            orderItemsToSave.add(orderItem);
        }

        order.setSubtotal(subtotal);
        order.setTotalAmount(subtotal.add(order.getTaxAmount()).subtract(order.getDiscountAmount()));
        if (request.getNotes() != null) {
            order.setNotes(request.getNotes());
        }

        orderItemRepository.deleteByOrderId(id);
        List<OrderItem> savedItems = orderItemRepository.saveAll(orderItemsToSave);
        Order savedOrder = orderRepository.save(order);

        eventPublisher.publishOrderUpdated(savedOrder);

        return mapToResponse(savedOrder, savedItems);
    }

    @Transactional
    public OrderResponse addItemsToOrder(Long id, OrderRequest request) {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        Long currentUserId = securityUtils.getCurrentUserId();
        String currentRole = securityUtils.getCurrentUserRole();

        Order order = findOrderScoped(id, restaurantId);

        if (billRepository.existsByOrderId(id)) {
            throw new RuntimeException("Cannot add items because a bill has already been generated for this order");
        }

        List<String> allowedStatuses = Arrays.asList("NEW", "ACCEPTED", "PREPARING", "READY");
        if (!allowedStatuses.contains(order.getStatus() != null ? order.getStatus().toUpperCase() : "")) {
            throw new RuntimeException("Cannot add items to order with status " + order.getStatus());
        }

        if ("KITCHEN".equalsIgnoreCase(currentRole)) {
            throw new AccessDeniedException("Access denied");
        }

        if ("DINE_IN".equalsIgnoreCase(order.getOrderType())) {
            if ("WAITER".equalsIgnoreCase(currentRole)) {
                if (order.getWaiter() == null || !order.getWaiter().getId().equals(currentUserId)) {
                    throw new AccessDeniedException("Access denied");
                }
            }
        } else if ("TAKEAWAY".equalsIgnoreCase(order.getOrderType())) {
            if (!"ADMIN".equalsIgnoreCase(currentRole)) {
                throw new AccessDeniedException("Access denied");
            }
        }

        if (request.getTableId() != null && order.getTable() != null && !request.getTableId().equals(order.getTable().getId())) {
            throw new AccessDeniedException("Access denied: Cannot change table assignment of an existing order");
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("Order items list cannot be empty");
        }

        List<OrderItem> existingItems = findOrderItemsScoped(id, restaurantId);
        List<OrderItem> itemsToSave = new ArrayList<>();

        for (OrderItemRequest req : request.getItems()) {
            if (req.getQuantity() == null || req.getQuantity() <= 0) {
                throw new RuntimeException("Quantity must be greater than 0");
            }

            MenuItem menuItem = findMenuItemScoped(req.getMenuItemId(), restaurantId);

            if (!Boolean.TRUE.equals(menuItem.getIsAvailable())) {
                throw new RuntimeException("Menu item is not available");
            }

            if (menuItem.getCategory() != null && Boolean.FALSE.equals(menuItem.getCategory().getIsActive())) {
                throw new RuntimeException("This category is inactive. Please refresh the menu and try again.");
            }

            BigDecimal unitPrice = menuItem.getPrice();
            String notes = (req.getNotes() != null && !req.getNotes().isBlank()) ? req.getNotes().trim() : null;

            Optional<OrderItem> existingMatch = existingItems.stream()
                    .filter(oi -> oi.getMenuItem().getId().equals(menuItem.getId()) && notesMatch(oi.getNotes(), notes))
                    .findFirst();

            if (existingMatch.isPresent()) {
                OrderItem existing = existingMatch.get();
                existing.setQuantity(existing.getQuantity() + req.getQuantity());
                existing.setSubtotal(unitPrice.multiply(BigDecimal.valueOf(existing.getQuantity())));
                if (!itemsToSave.contains(existing)) {
                    itemsToSave.add(existing);
                }
            } else {
                Optional<OrderItem> newMatch = itemsToSave.stream()
                        .filter(oi -> oi.getMenuItem().getId().equals(menuItem.getId()) && notesMatch(oi.getNotes(), notes))
                        .findFirst();

                if (newMatch.isPresent()) {
                    OrderItem newMatchingItem = newMatch.get();
                    newMatchingItem.setQuantity(newMatchingItem.getQuantity() + req.getQuantity());
                    newMatchingItem.setSubtotal(unitPrice.multiply(BigDecimal.valueOf(newMatchingItem.getQuantity())));
                } else {
                    OrderItem newItem = new OrderItem();
                    newItem.setRestaurant(order.getRestaurant());
                    newItem.setOrder(order);
                    newItem.setMenuItem(menuItem);
                    newItem.setQuantity(req.getQuantity());
                    newItem.setUnitPrice(unitPrice);
                    newItem.setNotes(notes);
                    newItem.setSubtotal(unitPrice.multiply(BigDecimal.valueOf(req.getQuantity())));
                    itemsToSave.add(newItem);
                }
            }
        }

        orderItemRepository.saveAll(itemsToSave);

        List<OrderItem> allItems = findOrderItemsScoped(id, restaurantId);
        BigDecimal subtotal = allItems.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setSubtotal(subtotal);
        order.setTotalAmount(subtotal.add(order.getTaxAmount()).subtract(order.getDiscountAmount()));
        Order savedOrder = orderRepository.save(order);

        eventPublisher.publishOrderUpdated(savedOrder);

        return mapToResponse(savedOrder, allItems);
    }

    private boolean notesMatch(String notes1, String notes2) {
        String n1 = (notes1 != null && !notes1.isBlank()) ? notes1.trim() : null;
        String n2 = (notes2 != null && !notes2.isBlank()) ? notes2.trim() : null;
        return java.util.Objects.equals(n1, n2);
    }

    @Transactional
    public OrderResponse acceptOrder(Long id) {
        if (!"KITCHEN".equalsIgnoreCase(securityUtils.getCurrentUserRole())) {
            throw new AccessDeniedException("Access denied");
        }
        return updateOrderStatus(id, "NEW", "ACCEPTED");
    }

    @Transactional
    public OrderResponse prepareOrder(Long id) {
        if (!"KITCHEN".equalsIgnoreCase(securityUtils.getCurrentUserRole())) {
            throw new AccessDeniedException("Access denied");
        }
        return updateOrderStatus(id, "ACCEPTED", "PREPARING");
    }

    @Transactional
    public OrderResponse readyOrder(Long id) {
        if (!"KITCHEN".equalsIgnoreCase(securityUtils.getCurrentUserRole())) {
            throw new AccessDeniedException("Access denied");
        }
        return updateOrderStatus(id, "PREPARING", "READY");
    }

    @Transactional
    public OrderResponse completeOrder(Long id) {
        if (!"ADMIN".equalsIgnoreCase(securityUtils.getCurrentUserRole())) {
            throw new AccessDeniedException("Access denied");
        }
        throw new RuntimeException("Order can only be completed after successful payment");
    }

    @Transactional
    public OrderResponse cancelOrder(Long id) {
        String currentRole = securityUtils.getCurrentUserRole();
        Long currentUserId = securityUtils.getCurrentUserId();
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }

        if ("KITCHEN".equalsIgnoreCase(currentRole)) {
            throw new AccessDeniedException("Access denied");
        }

        Order order = findOrderScoped(id, restaurantId);

        String currentStatus = order.getStatus();

        if ("ADMIN".equalsIgnoreCase(currentRole)) {
            if (!"NEW".equalsIgnoreCase(currentStatus) && !"ACCEPTED".equalsIgnoreCase(currentStatus)) {
                throw new RuntimeException("Invalid cancellation status");
            }
        } else if ("WAITER".equalsIgnoreCase(currentRole)) {
            if (!"NEW".equalsIgnoreCase(currentStatus)) {
                throw new RuntimeException("Invalid cancellation status");
            }
            if (order.getWaiter() == null || !order.getWaiter().getId().equals(currentUserId)) {
                throw new AccessDeniedException("Access denied");
            }
        }

        if ("DINE_IN".equalsIgnoreCase(order.getOrderType()) && "NEW".equalsIgnoreCase(currentStatus)) {
            if (order.getTable() != null) {
                RestaurantTable table = order.getTable();
                table.setStatus("AVAILABLE");
                tableRepository.save(table);
            }
        }

        order.setStatus("CANCELLED");
        Order saved = orderRepository.save(order);
        saveStatusHistory(saved, "CANCELLED", currentUserId);

        eventPublisher.publishOrderStatusChanged(saved);
        notificationService.notifyOrderCancelled(saved);

        List<OrderItem> items = findOrderItemsScoped(id, restaurantId);
        return mapToResponse(saved, items);
    }

    private OrderResponse updateOrderStatus(Long id, String requiredCurrentStatus, String targetStatus) {
        Long currentUserId = securityUtils.getCurrentUserId();
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        Order order = findOrderScoped(id, restaurantId);

        if (!requiredCurrentStatus.equalsIgnoreCase(order.getStatus())) {
            throw new RuntimeException("Invalid status transition to " + targetStatus);
        }

        order.setStatus(targetStatus);
        Order saved = orderRepository.save(order);
        saveStatusHistory(saved, targetStatus, currentUserId);

        eventPublisher.publishOrderStatusChanged(saved);
        notificationService.notifyOrderStatusChanged(saved);

        List<OrderItem> items = findOrderItemsScoped(id, restaurantId);
        return mapToResponse(saved, items);
    }

    private void saveStatusHistory(Order order, String status, Long changedById) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setRestaurant(order.getRestaurant());
        history.setOrder(order);
        history.setStatus(status);
        history.setChangedBy(userRepository.getReferenceById(changedById));
        orderStatusHistoryRepository.save(history);
    }

    private Map<Long, DeduplicatedItem> deduplicateItems(List<OrderItemRequest> itemRequests) {
        Map<Long, DeduplicatedItem> map = new LinkedHashMap<>();
        for (OrderItemRequest req : itemRequests) {
            Long menuItemId = req.getMenuItemId();
            if (map.containsKey(menuItemId)) {
                DeduplicatedItem existing = map.get(menuItemId);
                existing.quantity += req.getQuantity();
                if (req.getNotes() != null && !req.getNotes().isBlank()) {
                    if (existing.notes == null || existing.notes.isBlank()) {
                        existing.notes = req.getNotes();
                    } else {
                        existing.notes += "; " + req.getNotes();
                    }
                }
            } else {
                map.put(menuItemId, new DeduplicatedItem(menuItemId, req.getQuantity(), req.getNotes()));
            }
        }
        return map;
    }

    private static class DeduplicatedItem {
        Long menuItemId;
        int quantity;
        String notes;

        DeduplicatedItem(Long menuItemId, int quantity, String notes) {
            this.menuItemId = menuItemId;
            this.quantity = quantity;
            this.notes = notes;
        }
    }

    private OrderResponse mapToResponse(Order order, List<OrderItem> items) {
        List<OrderItemResponse> itemResponses = items.stream()
                .map(i -> OrderItemResponse.builder()
                        .id(i.getId())
                        .menuItemId(i.getMenuItem() != null ? i.getMenuItem().getId() : null)
                        .menuItemName(i.getMenuItem() != null ? i.getMenuItem().getName() : null)
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .notes(i.getNotes())
                        .subtotal(i.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .restaurantId(order.getRestaurant() != null ? order.getRestaurant().getId() : null)
                .tableId(order.getTable() != null ? order.getTable().getId() : null)
                .tableNumber(order.getTable() != null ? order.getTable().getTableNumber() : null)
                .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
                .waiterId(order.getWaiter() != null ? order.getWaiter().getId() : null)
                .waiterName(order.getWaiter() != null ? order.getWaiter().getName() : null)
                .orderType(order.getOrderType())
                .status(order.getStatus())
                .subtotal(order.getSubtotal())
                .taxAmount(order.getTaxAmount())
                .discountAmount(order.getDiscountAmount())
                .totalAmount(order.getTotalAmount())
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
        Optional<Order> opt = orderRepository.findByIdAndRestaurantId(id, restaurantId);
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

    private MenuItem findMenuItemScoped(Long id, Long restaurantId) {
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        Optional<MenuItem> opt = menuItemRepository.findByIdAndRestaurantId(id, restaurantId);
        if (opt.isPresent()) {
            return opt.get();
        }
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu item not found with id: " + id));
        if (item.getRestaurant() == null || !restaurantId.equals(item.getRestaurant().getId())) {
            throw new RuntimeException("Menu item not found with id: " + id);
        }
        return item;
    }

    private RestaurantTable findTableScoped(Long id, Long restaurantId) {
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        Optional<RestaurantTable> opt = tableRepository.findByIdAndRestaurantId(id, restaurantId);
        if (opt.isPresent()) {
            return opt.get();
        }
        RestaurantTable table = tableRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Table not found with id: " + id));
        if (table.getRestaurant() == null || !restaurantId.equals(table.getRestaurant().getId())) {
            throw new RuntimeException("Table not found with id: " + id);
        }
        return table;
    }

    private Customer findCustomerScoped(Long id, Long restaurantId) {
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        Optional<Customer> opt = customerRepository.findByIdAndRestaurantId(id, restaurantId);
        if (opt.isPresent()) {
            return opt.get();
        }
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + id));
        if (customer.getRestaurant() == null || !restaurantId.equals(customer.getRestaurant().getId())) {
            throw new RuntimeException("Customer not found with id: " + id);
        }
        return customer;
    }

    private User findUserScoped(Long id, Long restaurantId) {
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        Optional<User> opt = userRepository.findByIdAndRestaurantId(id, restaurantId);
        if (opt.isPresent()) {
            return opt.get();
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        if (user.getRestaurant() == null || !restaurantId.equals(user.getRestaurant().getId())) {
            throw new RuntimeException("User not found with id: " + id);
        }
        return user;
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
}

