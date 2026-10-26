package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.dto.BillRequest;
import com.restaurant.restaurantpos.dto.BillResponse;
import com.restaurant.restaurantpos.dto.PaymentRequest;
import com.restaurant.restaurantpos.dto.PaymentResponse;
import com.restaurant.restaurantpos.entity.Bill;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.OrderStatusHistory;
import com.restaurant.restaurantpos.entity.Payment;
import com.restaurant.restaurantpos.entity.RestaurantTable;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.repository.BillRepository;
import com.restaurant.restaurantpos.repository.OrderRepository;
import com.restaurant.restaurantpos.exception.OrderWorkflowConflictException;
import com.restaurant.restaurantpos.repository.OrderStatusHistoryRepository;
import com.restaurant.restaurantpos.repository.PaymentRepository;
import com.restaurant.restaurantpos.repository.RestaurantSettingsRepository;
import com.restaurant.restaurantpos.repository.RestaurantTableRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.restaurant.restaurantpos.event.KitchenOrderEventPublisher;

@Service
public class BillService {

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final RestaurantTableRepository tableRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;
    private final KitchenOrderEventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final RestaurantSettingsRepository settingsRepository;

    public BillService(
            BillRepository billRepository,
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            RestaurantTableRepository tableRepository,
            UserRepository userRepository,
            SecurityUtils securityUtils,
            KitchenOrderEventPublisher eventPublisher,
            NotificationService notificationService) {
        this(billRepository, paymentRepository, orderRepository, orderStatusHistoryRepository,
             tableRepository, userRepository, securityUtils, eventPublisher, notificationService, null);
    }

    @Autowired
    public BillService(
            BillRepository billRepository,
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            RestaurantTableRepository tableRepository,
            UserRepository userRepository,
            SecurityUtils securityUtils,
            KitchenOrderEventPublisher eventPublisher,
            NotificationService notificationService,
            RestaurantSettingsRepository settingsRepository) {
        this.billRepository = billRepository;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.tableRepository = tableRepository;
        this.userRepository = userRepository;
        this.securityUtils = securityUtils;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
        this.settingsRepository = settingsRepository;
    }

    @Transactional
    public BillResponse createBill(BillRequest request) {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }

        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        Long currentUserId = securityUtils.getCurrentUserId();

        Order order = findOrderScoped(request.getOrderId(), restaurantId);

        boolean kitchenEnabled = isKitchenModeEnabled(restaurantId);
        if (kitchenEnabled) {
            if (!"READY".equalsIgnoreCase(order.getStatus())) {
                throw new OrderWorkflowConflictException("Order is not in READY status");
            }
        } else {
            List<String> activeStatuses = Arrays.asList("NEW", "ACCEPTED", "PREPARING", "READY");
            if (!activeStatuses.contains(order.getStatus() != null ? order.getStatus().toUpperCase() : "")) {
                throw new OrderWorkflowConflictException("Cannot create bill for order with status " + order.getStatus());
            }
        }

        if (billRepository.existsByOrderId(order.getId())) {
            throw new OrderWorkflowConflictException("Bill already exists for this order");
        }

        User generatedBy = findUserScoped(currentUserId, restaurantId);

        String billNumber = generateBillNumber(restaurantId);

        Bill bill = new Bill();
        bill.setRestaurant(order.getRestaurant());
        bill.setOrder(order);
        bill.setBillNumber(billNumber);
        bill.setSubtotal(order.getSubtotal());
        bill.setTaxAmount(order.getTaxAmount());
        bill.setGstMode(order.getGstMode());
        bill.setCgstRate(order.getCgstRate());
        bill.setSgstRate(order.getSgstRate());
        bill.setCgstAmount(order.getCgstAmount() != null ? order.getCgstAmount() : BigDecimal.ZERO.setScale(2));
        bill.setSgstAmount(order.getSgstAmount() != null ? order.getSgstAmount() : BigDecimal.ZERO.setScale(2));
        bill.setDiscountAmount(order.getDiscountAmount());
        bill.setTotalAmount(order.getTotalAmount());
        bill.setGeneratedBy(generatedBy);

        Bill savedBill = billRepository.save(bill);

        if ("DINE_IN".equalsIgnoreCase(order.getOrderType()) && order.getTable() != null) {
            RestaurantTable table = order.getTable();
            table.setStatus("BILLING");
            tableRepository.save(table);
        }

        return mapToBillResponse(savedBill);
    }

    public List<BillResponse> getAllBills() {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        List<Bill> bills = billRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
        return bills.stream().map(this::mapToBillResponse).collect(Collectors.toList());
    }

    public BillResponse getBillById(Long id) {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        Bill bill = findBillScoped(id, restaurantId);
        return mapToBillResponse(bill);
    }

    public BillResponse getBillByOrderId(Long orderId) {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        findOrderScoped(orderId, restaurantId);
        Bill bill = billRepository.findByRestaurantIdAndOrderId(restaurantId, orderId)
                .orElseThrow(() -> new RuntimeException("Bill not found with order id: " + orderId));
        return mapToBillResponse(bill);
    }

    @Transactional
    public PaymentResponse processPayment(Long billId, PaymentRequest request) {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }

        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        Long currentUserId = securityUtils.getCurrentUserId();

        Bill bill = findBillScoped(billId, restaurantId);

        Order order = bill.getOrder();

        boolean kitchenEnabled = isKitchenModeEnabled(restaurantId);
        if (kitchenEnabled) {
            if (!"READY".equalsIgnoreCase(order.getStatus())) {
                throw new OrderWorkflowConflictException("Order is not in READY status");
            }
        } else {
            List<String> activeStatuses = Arrays.asList("NEW", "ACCEPTED", "PREPARING", "READY");
            if (!activeStatuses.contains(order.getStatus() != null ? order.getStatus().toUpperCase() : "")) {
                throw new OrderWorkflowConflictException("Cannot process payment for order with status " + order.getStatus());
            }
        }

        if (paymentRepository.existsByBillIdAndPaymentStatus(bill.getId(), "PAID")) {
            throw new OrderWorkflowConflictException("Successful payment already exists for this bill");
        }

        String method = request.getPaymentMethod() != null ? request.getPaymentMethod().toUpperCase() : "";
        if (!Arrays.asList("CASH", "UPI", "CARD").contains(method)) {
            throw new RuntimeException("Invalid payment method");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(bill.getTotalAmount()) != 0) {
            throw new RuntimeException("Payment amount must equal bill total amount");
        }

        User changedBy = findUserScoped(currentUserId, restaurantId);

        Payment payment = new Payment();
        payment.setRestaurant(bill.getRestaurant());
        payment.setBill(bill);
        payment.setAmount(request.getAmount());
        payment.setPaymentMethod(method);
        payment.setPaymentStatus("PAID");
        payment.setTransactionReference(request.getTransactionReference());
        payment.setPaidAt(LocalDateTime.now());

        Payment savedPayment = paymentRepository.save(payment);

        order.setStatus("COMPLETED");
        Order savedOrder = orderRepository.save(order);
        if (savedOrder == null) {
            savedOrder = order;
        }

        OrderStatusHistory history = new OrderStatusHistory();
        history.setRestaurant(savedOrder.getRestaurant() != null ? savedOrder.getRestaurant() : bill.getRestaurant());
        history.setOrder(savedOrder);
        history.setStatus("COMPLETED");
        history.setChangedBy(changedBy);
        orderStatusHistoryRepository.save(history);

        eventPublisher.publishOrderStatusChanged(savedOrder);
        notificationService.notifyPaymentCompleted(savedOrder, savedPayment);

        if ("DINE_IN".equalsIgnoreCase(order.getOrderType()) && order.getTable() != null) {
            RestaurantTable table = order.getTable();
            table.setStatus("AVAILABLE");
            tableRepository.save(table);
        }

        return mapToPaymentResponse(savedPayment);
    }

    public List<PaymentResponse> getPaymentsByBillId(Long billId) {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        Bill bill = findBillScoped(billId, restaurantId);

        List<Payment> payments = paymentRepository.findByBillIdAndRestaurantId(bill.getId(), restaurantId);
        if (payments.isEmpty()) {
            payments = paymentRepository.findByBillId(bill.getId()).stream()
                    .filter(p -> p.getRestaurant() == null || restaurantId.equals(p.getRestaurant().getId()))
                    .collect(Collectors.toList());
        }
        return payments.stream().map(this::mapToPaymentResponse).collect(Collectors.toList());
    }

    private synchronized String generateBillNumber(Long restaurantId) {
        Optional<Bill> lastBillOpt = billRepository.findTopByRestaurantIdOrderByIdDesc(restaurantId);
        long nextNum = 1;
        if (lastBillOpt.isPresent()) {
            String lastNumStr = lastBillOpt.get().getBillNumber();
            if (lastNumStr != null && lastNumStr.startsWith("BILL-")) {
                try {
                    nextNum = Long.parseLong(lastNumStr.substring(5)) + 1;
                } catch (NumberFormatException e) {
                    nextNum = billRepository.countByRestaurantId(restaurantId) + 1;
                }
            } else {
                nextNum = billRepository.countByRestaurantId(restaurantId) + 1;
            }
        }
        return String.format("BILL-%06d", nextNum);
    }

    private BillResponse mapToBillResponse(Bill bill) {
        return BillResponse.builder()
                .id(bill.getId())
                .restaurantId(bill.getRestaurant() != null ? bill.getRestaurant().getId() : null)
                .orderId(bill.getOrder() != null ? bill.getOrder().getId() : null)
                .billNumber(bill.getBillNumber())
                .subtotal(bill.getSubtotal())
                .taxAmount(bill.getTaxAmount())
                .gstMode(bill.getGstMode())
                .cgstRate(bill.getCgstRate())
                .sgstRate(bill.getSgstRate())
                .cgstAmount(bill.getCgstAmount())
                .sgstAmount(bill.getSgstAmount())
                .discountAmount(bill.getDiscountAmount())
                .totalAmount(bill.getTotalAmount())
                .generatedById(bill.getGeneratedBy() != null ? bill.getGeneratedBy().getId() : null)
                .generatedByName(bill.getGeneratedBy() != null ? bill.getGeneratedBy().getName() : null)
                .createdAt(bill.getCreatedAt())
                .build();
    }

    private PaymentResponse mapToPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .billId(payment.getBill() != null ? payment.getBill().getId() : null)
                .orderId(payment.getBill() != null && payment.getBill().getOrder() != null ? payment.getBill().getOrder().getId() : null)
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .paymentStatus(payment.getPaymentStatus())
                .transactionReference(payment.getTransactionReference())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    private Bill findBillScoped(Long id, Long restaurantId) {
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        Optional<Bill> opt = billRepository.findByIdAndRestaurantId(id, restaurantId);
        if (opt.isPresent()) {
            return opt.get();
        }
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bill not found with id: " + id));
        if (bill.getRestaurant() == null || !restaurantId.equals(bill.getRestaurant().getId())) {
            throw new RuntimeException("Bill not found with id: " + id);
        }
        return bill;
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

    private User findUserScoped(Long id, Long restaurantId) {
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        if (id == null) {
            return null;
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

    private boolean isKitchenModeEnabled(Long restaurantId) {
        if (restaurantId == null || settingsRepository == null) {
            return true;
        }
        return settingsRepository.findByRestaurantId(restaurantId)
                .map(s -> s.getKitchenEnabled() != null ? s.getKitchenEnabled() : true)
                .orElse(true);
    }
}
