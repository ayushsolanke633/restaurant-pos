package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.dto.report.*;
import com.restaurant.restaurantpos.entity.Bill;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.OrderItem;
import com.restaurant.restaurantpos.entity.Payment;
import com.restaurant.restaurantpos.entity.RestaurantSettings;
import com.restaurant.restaurantpos.repository.CustomerRepository;
import com.restaurant.restaurantpos.repository.InventoryItemRepository;
import com.restaurant.restaurantpos.repository.OrderItemRepository;
import com.restaurant.restaurantpos.repository.OrderRepository;
import com.restaurant.restaurantpos.repository.PaymentRepository;
import com.restaurant.restaurantpos.repository.RestaurantSettingsRepository;
import com.restaurant.restaurantpos.repository.RestaurantTableRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final RestaurantTableRepository tableRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final RestaurantSettingsRepository settingsRepository;
    private final SecurityUtils securityUtils;

    public ReportService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository,
            InventoryItemRepository inventoryItemRepository,
            RestaurantTableRepository tableRepository,
            UserRepository userRepository,
            CustomerRepository customerRepository,
            RestaurantSettingsRepository settingsRepository,
            SecurityUtils securityUtils) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.tableRepository = tableRepository;
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.settingsRepository = settingsRepository;
        this.securityUtils = securityUtils;
    }

    private ZoneId getRestaurantZoneId(Long restaurantId) {
        if (restaurantId == null) {
            return DEFAULT_ZONE;
        }
        try {
            Optional<RestaurantSettings> opt = settingsRepository.findByRestaurantId(restaurantId);
            if (opt.isPresent() && opt.get().getTimezone() != null && !opt.get().getTimezone().trim().isEmpty()) {
                return ZoneId.of(opt.get().getTimezone().trim());
            }
        } catch (Exception e) {
            // Fallback to default business timezone if setting is invalid or missing
        }
        return DEFAULT_ZONE;
    }

    public DashboardResponse getDashboardSummary() {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        ZoneId zone = getRestaurantZoneId(restaurantId);
        LocalDate today = LocalDate.now(zone);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        List<Order> todayOrders = orderRepository.findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(restaurantId, start, end);
        long totalOrders = todayOrders.size();
        long completedOrders = todayOrders.stream().filter(o -> "COMPLETED".equalsIgnoreCase(o.getStatus())).count();
        long cancelledOrders = todayOrders.stream().filter(o -> "CANCELLED".equalsIgnoreCase(o.getStatus())).count();
        long pendingOrders = todayOrders.stream().filter(o -> Arrays.asList("NEW", "ACCEPTED", "PREPARING").contains(o.getStatus().toUpperCase())).count();

        List<Payment> todayPaidPayments = paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(restaurantId, "PAID", start, end);

        BigDecimal totalRevenue = calculateSum(todayPaidPayments, p -> p.getAmount());
        BigDecimal cashAmount = calculateSum(todayPaidPayments.stream().filter(p -> "CASH".equalsIgnoreCase(p.getPaymentMethod())).collect(Collectors.toList()), p -> p.getAmount());
        BigDecimal upiAmount = calculateSum(todayPaidPayments.stream().filter(p -> "UPI".equalsIgnoreCase(p.getPaymentMethod())).collect(Collectors.toList()), p -> p.getAmount());
        BigDecimal cardAmount = calculateSum(todayPaidPayments.stream().filter(p -> "CARD".equalsIgnoreCase(p.getPaymentMethod())).collect(Collectors.toList()), p -> p.getAmount());

        List<Order> paidOrders = todayPaidPayments.stream()
                .filter(p -> p.getBill() != null && p.getBill().getOrder() != null)
                .map(p -> p.getBill().getOrder())
                .filter(o -> "COMPLETED".equalsIgnoreCase(o.getStatus()))
                .distinct()
                .collect(Collectors.toList());

        BigDecimal totalTax = calculateSum(paidOrders, o -> o.getTaxAmount());
        BigDecimal totalDiscount = calculateSum(paidOrders, o -> o.getDiscountAmount());

        long paidCount = paidOrders.size();
        BigDecimal averageOrderValue = paidCount > 0 ? totalRevenue.divide(BigDecimal.valueOf(paidCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);

        Long lowStockCount = inventoryItemRepository.countLowStockItems(restaurantId);
        if (lowStockCount == null) lowStockCount = 0L;

        Long occupiedTables = tableRepository.countByRestaurantIdAndStatus(restaurantId, "OCCUPIED");
        Long billingTables = tableRepository.countByRestaurantIdAndStatus(restaurantId, "BILLING");
        long activeTables = (occupiedTables != null ? occupiedTables : 0L) + (billingTables != null ? billingTables : 0L);

        return DashboardResponse.builder()
                .date(today)
                .totalOrders(totalOrders)
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .pendingOrders(pendingOrders)
                .totalRevenue(totalRevenue)
                .totalTax(totalTax)
                .totalDiscount(totalDiscount)
                .averageOrderValue(averageOrderValue)
                .cashAmount(cashAmount)
                .upiAmount(upiAmount)
                .cardAmount(cardAmount)
                .lowStockItemCount(lowStockCount)
                .activeTableCount(activeTables)
                .build();
    }

    public SalesReportResponse getSalesReport(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<Order> rangeOrders = orderRepository.findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(restaurantId, start, end);
        long totalOrders = rangeOrders.size();
        long completedOrders = rangeOrders.stream().filter(o -> "COMPLETED".equalsIgnoreCase(o.getStatus())).count();
        long cancelledOrders = rangeOrders.stream().filter(o -> "CANCELLED".equalsIgnoreCase(o.getStatus())).count();

        List<Payment> rangePaidPayments = paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(restaurantId, "PAID", start, end);

        BigDecimal totalRevenue = calculateSum(rangePaidPayments, p -> p.getAmount());
        BigDecimal cashAmount = calculateSum(rangePaidPayments.stream().filter(p -> "CASH".equalsIgnoreCase(p.getPaymentMethod())).collect(Collectors.toList()), p -> p.getAmount());
        BigDecimal upiAmount = calculateSum(rangePaidPayments.stream().filter(p -> "UPI".equalsIgnoreCase(p.getPaymentMethod())).collect(Collectors.toList()), p -> p.getAmount());
        BigDecimal cardAmount = calculateSum(rangePaidPayments.stream().filter(p -> "CARD".equalsIgnoreCase(p.getPaymentMethod())).collect(Collectors.toList()), p -> p.getAmount());

        List<Order> paidOrders = rangePaidPayments.stream()
                .filter(p -> p.getBill() != null && p.getBill().getOrder() != null)
                .map(p -> p.getBill().getOrder())
                .filter(o -> "COMPLETED".equalsIgnoreCase(o.getStatus()))
                .distinct()
                .collect(Collectors.toList());

        BigDecimal totalTax = calculateSum(paidOrders, o -> o.getTaxAmount());
        BigDecimal totalDiscount = calculateSum(paidOrders, o -> o.getDiscountAmount());

        long paidCount = paidOrders.size();
        BigDecimal averageOrderValue = paidCount > 0 ? totalRevenue.divide(BigDecimal.valueOf(paidCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);

        return SalesReportResponse.builder()
                .from(from)
                .to(to)
                .totalOrders(totalOrders)
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .totalRevenue(totalRevenue)
                .totalTax(totalTax)
                .totalDiscount(totalDiscount)
                .averageOrderValue(averageOrderValue)
                .cashAmount(cashAmount)
                .upiAmount(upiAmount)
                .cardAmount(cardAmount)
                .build();
    }

    public List<DailySalesResponse> getDailySalesBreakdown(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        List<DailySalesResponse> list = new ArrayList<>();

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            LocalDateTime start = d.atStartOfDay();
            LocalDateTime end = d.plusDays(1).atStartOfDay();

            List<Order> dayOrders = orderRepository.findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(restaurantId, start, end);
            long ordersCount = dayOrders.size();
            long completedCount = dayOrders.stream().filter(o -> "COMPLETED".equalsIgnoreCase(o.getStatus())).count();
            long cancelledCount = dayOrders.stream().filter(o -> "CANCELLED".equalsIgnoreCase(o.getStatus())).count();

            List<Payment> dayPayments = paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(restaurantId, "PAID", start, end);
            BigDecimal revenue = calculateSum(dayPayments, p -> p.getAmount());

            list.add(DailySalesResponse.builder()
                    .date(d)
                    .orders(ordersCount)
                    .completedOrders(completedCount)
                    .cancelledOrders(cancelledCount)
                    .revenue(revenue)
                    .build());
        }

        return list;
    }

    public List<PaymentMethodReportResponse> getPaymentMethodBreakdown(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<Payment> payments = paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(restaurantId, "PAID", start, end);

        List<String> methods = Arrays.asList("CASH", "UPI", "CARD");
        List<PaymentMethodReportResponse> result = new ArrayList<>();

        for (String m : methods) {
            List<Payment> methodPayments = payments.stream().filter(p -> m.equalsIgnoreCase(p.getPaymentMethod())).collect(Collectors.toList());
            long count = methodPayments.size();
            BigDecimal amount = calculateSum(methodPayments, p -> p.getAmount());
            result.add(PaymentMethodReportResponse.builder()
                    .paymentMethod(m)
                    .transactionCount(count)
                    .amount(amount)
                    .build());
        }

        return result;
    }

    public OrderStatusReportResponse getOrderStatusStatistics(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<Order> orders = orderRepository.findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(restaurantId, start, end);

        long newCount = orders.stream().filter(o -> "NEW".equalsIgnoreCase(o.getStatus())).count();
        long acceptedCount = orders.stream().filter(o -> "ACCEPTED".equalsIgnoreCase(o.getStatus())).count();
        long preparingCount = orders.stream().filter(o -> "PREPARING".equalsIgnoreCase(o.getStatus())).count();
        long readyCount = orders.stream().filter(o -> "READY".equalsIgnoreCase(o.getStatus())).count();
        long completedCount = orders.stream().filter(o -> "COMPLETED".equalsIgnoreCase(o.getStatus())).count();
        long cancelledCount = orders.stream().filter(o -> "CANCELLED".equalsIgnoreCase(o.getStatus())).count();

        return OrderStatusReportResponse.builder()
                .newOrders(newCount)
                .acceptedOrders(acceptedCount)
                .preparingOrders(preparingCount)
                .readyOrders(readyCount)
                .completedOrders(completedCount)
                .cancelledOrders(cancelledCount)
                .build();
    }

    public List<OrderTypeReportResponse> getOrderTypeReport(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<Order> orders = orderRepository.findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(restaurantId, start, end);
        List<Payment> payments = paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(restaurantId, "PAID", start, end);

        List<String> types = Arrays.asList("DINE_IN", "TAKEAWAY");
        List<OrderTypeReportResponse> list = new ArrayList<>();

        for (String type : types) {
            List<Order> typeOrders = orders.stream().filter(o -> type.equalsIgnoreCase(o.getOrderType())).collect(Collectors.toList());
            long orderCount = typeOrders.size();
            long completedCount = typeOrders.stream().filter(o -> "COMPLETED".equalsIgnoreCase(o.getStatus())).count();
            long cancelledCount = typeOrders.stream().filter(o -> "CANCELLED".equalsIgnoreCase(o.getStatus())).count();

            List<Payment> typePayments = payments.stream()
                    .filter(p -> p.getBill() != null && p.getBill().getOrder() != null && type.equalsIgnoreCase(p.getBill().getOrder().getOrderType()))
                    .collect(Collectors.toList());

            BigDecimal revenue = calculateSum(typePayments, p -> p.getAmount());

            list.add(OrderTypeReportResponse.builder()
                    .orderType(type)
                    .orderCount(orderCount)
                    .completedCount(completedCount)
                    .cancelledCount(cancelledCount)
                    .revenue(revenue)
                    .build());
        }

        return list;
    }

    public List<TopSellingItemResponse> getTopSellingItems(LocalDate from, LocalDate to, Integer limit) {
        validateDateRange(from, to);
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        int actualLimit = limit == null ? 10 : limit;
        if (actualLimit <= 0 || actualLimit > 50) {
            throw new RuntimeException("Limit must be between 1 and 50");
        }

        Long restaurantId = securityUtils.getCurrentRestaurantId();
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<Object[]> rows = orderItemRepository.findTopSellingItems(restaurantId, start, end);

        return rows.stream().map(r -> {
            Long itemId = (Long) r[0];
            String name = (String) r[1];
            Long quantitySold = ((Number) r[2]).longValue();
            BigDecimal revenue = (BigDecimal) r[3];
            return TopSellingItemResponse.builder()
                    .menuItemId(itemId)
                    .menuItemName(name)
                    .quantitySold(quantitySold)
                    .revenue(revenue != null ? revenue.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2))
                    .build();
        }).limit(actualLimit).collect(Collectors.toList());
    }

    public List<CategorySalesResponse> getCategorySalesReport(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<Object[]> rows = orderItemRepository.findCategorySales(restaurantId, start, end);

        return rows.stream().map(r -> {
            Long catId = (Long) r[0];
            String name = (String) r[1];
            Long quantitySold = ((Number) r[2]).longValue();
            BigDecimal revenue = (BigDecimal) r[3];
            return CategorySalesResponse.builder()
                    .categoryId(catId)
                    .categoryName(name)
                    .quantitySold(quantitySold)
                    .revenue(revenue != null ? revenue.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2))
                    .build();
        }).collect(Collectors.toList());
    }

    public InventorySummaryResponse getInventorySummary() {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();

        Long activeItems = inventoryItemRepository.countByRestaurantIdAndIsActiveTrue(restaurantId);
        Long lowStock = inventoryItemRepository.countLowStockItems(restaurantId);
        Long outOfStock = inventoryItemRepository.countOutOfStockItems(restaurantId);
        BigDecimal totalValue = inventoryItemRepository.calculateTotalInventoryValue(restaurantId);

        return InventorySummaryResponse.builder()
                .totalActiveItems(activeItems != null ? activeItems : 0L)
                .lowStockItemCount(lowStock != null ? lowStock : 0L)
                .outOfStockItemCount(outOfStock != null ? outOfStock : 0L)
                .totalInventoryValue(totalValue != null ? totalValue.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2))
                .build();
    }

    public TableSummaryResponse getTableSummary() {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();

        Long total = tableRepository.countByRestaurantId(restaurantId);
        Long available = tableRepository.countByRestaurantIdAndStatus(restaurantId, "AVAILABLE");
        Long occupied = tableRepository.countByRestaurantIdAndStatus(restaurantId, "OCCUPIED");
        Long billing = tableRepository.countByRestaurantIdAndStatus(restaurantId, "BILLING");

        return TableSummaryResponse.builder()
                .totalTables(total != null ? total : 0L)
                .availableTables(available != null ? available : 0L)
                .occupiedTables(occupied != null ? occupied : 0L)
                .billingTables(billing != null ? billing : 0L)
                .build();
    }

    public StaffSummaryResponse getStaffSummary() {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();

        Long total = userRepository.countByRestaurantId(restaurantId);
        Long active = userRepository.countByRestaurantIdAndIsActive(restaurantId, true);
        Long inactive = userRepository.countByRestaurantIdAndIsActive(restaurantId, false);
        Long admin = userRepository.countByRestaurantIdAndRoleNameIgnoreCase(restaurantId, "ADMIN");
        Long waiter = userRepository.countByRestaurantIdAndRoleNameIgnoreCase(restaurantId, "WAITER");
        Long kitchen = userRepository.countByRestaurantIdAndRoleNameIgnoreCase(restaurantId, "KITCHEN");

        return StaffSummaryResponse.builder()
                .totalStaff(total != null ? total : 0L)
                .activeStaff(active != null ? active : 0L)
                .inactiveStaff(inactive != null ? inactive : 0L)
                .adminCount(admin != null ? admin : 0L)
                .waiterCount(waiter != null ? waiter : 0L)
                .kitchenCount(kitchen != null ? kitchen : 0L)
                .build();
    }

    public CustomerSummaryResponse getCustomerSummary() {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        ZoneId zone = getRestaurantZoneId(restaurantId);
        LocalDate today = LocalDate.now(zone);

        Long total = customerRepository.countByRestaurantId(restaurantId);
        Long newToday = customerRepository.countByRestaurantIdAndCreatedAtGreaterThanEqual(restaurantId, today.atStartOfDay());

        return CustomerSummaryResponse.builder()
                .totalCustomers(total != null ? total : 0L)
                .newCustomersToday(newToday != null ? newToday : 0L)
                .build();
    }

    public List<ReportBillResponse> getReportBills(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<Payment> rangePaidPayments = paymentRepository.findByBillRestaurantIdAndPaymentStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(restaurantId, "PAID", start, end);

        return rangePaidPayments.stream()
                .filter(p -> p.getBill() != null && p.getBill().getOrder() != null)
                .filter(p -> "COMPLETED".equalsIgnoreCase(p.getBill().getOrder().getStatus()))
                .sorted((p1, p2) -> {
                    if (p1.getPaidAt() != null && p2.getPaidAt() != null) {
                        int cmp = p1.getPaidAt().compareTo(p2.getPaidAt());
                        if (cmp != 0) return cmp;
                    }
                    if (p1.getBill() != null && p2.getBill() != null && p1.getBill().getId() != null && p2.getBill().getId() != null) {
                        return p1.getBill().getId().compareTo(p2.getBill().getId());
                    }
                    return p1.getId().compareTo(p2.getId());
                })
                .map(this::mapToReportBillResponse)
                .collect(Collectors.toList());
    }

    private ReportBillResponse mapToReportBillResponse(Payment payment) {
        Bill bill = payment.getBill();
        Order order = bill.getOrder();

        Integer tableNumber = null;
        String tableName = null;
        if ("DINE_IN".equalsIgnoreCase(order.getOrderType()) && order.getTable() != null) {
            tableNumber = order.getTable().getTableNumber();
            if (tableNumber != null) {
                tableName = "Table " + tableNumber;
            }
        }

        String waiterName = order.getWaiter() != null ? order.getWaiter().getName() : null;

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
        List<ReportBillItemResponse> itemResponses = orderItems.stream().map(item -> {
            String menuItemName = item.getMenuItem() != null ? item.getMenuItem().getName() : null;
            BigDecimal itemSubtotal = item.getSubtotal() != null ? item.getSubtotal() :
                    (item.getUnitPrice() != null && item.getQuantity() != null ?
                            item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())) : BigDecimal.ZERO);
            return ReportBillItemResponse.builder()
                    .id(item.getId())
                    .menuItemId(item.getMenuItem() != null ? item.getMenuItem().getId() : null)
                    .menuItemName(menuItemName)
                    .name(menuItemName)
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .notes(item.getNotes())
                    .subtotal(itemSubtotal)
                    .amount(itemSubtotal)
                    .build();
        }).collect(Collectors.toList());

        BigDecimal subtotal = bill.getSubtotal() != null ? bill.getSubtotal() : BigDecimal.ZERO;
        BigDecimal discount = bill.getDiscountAmount() != null ? bill.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal tax = bill.getTaxAmount() != null ? bill.getTaxAmount() : BigDecimal.ZERO;
        BigDecimal total = bill.getTotalAmount() != null ? bill.getTotalAmount() : BigDecimal.ZERO;

        return ReportBillResponse.builder()
                .billId(bill.getId())
                .id(bill.getId())
                .orderId(order.getId())
                .billNumber(bill.getBillNumber())
                .orderType(order.getOrderType())
                .tableNumber(tableNumber)
                .tableName(tableName)
                .waiterName(waiterName)
                .createdAt(bill.getCreatedAt())
                .paidAt(payment.getPaidAt())
                .paymentMethod(payment.getPaymentMethod())
                .paymentStatus(payment.getPaymentStatus())
                .subtotal(subtotal)
                .discountAmount(discount)
                .discount(discount)
                .taxAmount(tax)
                .tax(tax)
                .totalAmount(total)
                .total(total)
                .items(itemResponses)
                .build();
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new RuntimeException("Date range 'from' and 'to' parameters are required");
        }
        if (from.isAfter(to)) {
            throw new RuntimeException("Invalid date range: 'from' date must be on or before 'to' date");
        }
    }

    private <T> BigDecimal calculateSum(List<T> items, java.util.function.Function<T, BigDecimal> extractor) {
        BigDecimal sum = BigDecimal.ZERO;
        for (T item : items) {
            BigDecimal val = extractor.apply(item);
            if (val != null) {
                sum = sum.add(val);
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }
}
