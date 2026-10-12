package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.InventoryItemRequest;
import com.restaurant.restaurantpos.dto.InventoryItemResponse;
import com.restaurant.restaurantpos.dto.InventoryTransactionRequest;
import com.restaurant.restaurantpos.dto.InventoryTransactionResponse;
import com.restaurant.restaurantpos.entity.InventoryItem;
import com.restaurant.restaurantpos.entity.InventoryTransaction;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.User;
import com.restaurant.restaurantpos.repository.InventoryItemRepository;
import com.restaurant.restaurantpos.repository.InventoryTransactionRepository;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.UserRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class Step20InventoryTest {

    @Mock
    private InventoryItemRepository itemRepository;

    @Mock
    private InventoryTransactionRepository transactionRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private InventoryService inventoryService;

    private Restaurant restaurantA;
    private Restaurant restaurantB;
    private User adminUser;
    private InventoryItem riceItem;

    @BeforeEach
    void setUp() {
        restaurantA = new Restaurant();
        restaurantA.setId(1L);
        restaurantA.setName("Restaurant A");

        restaurantB = new Restaurant();
        restaurantB.setId(2L);
        restaurantB.setName("Restaurant B");

        adminUser = new User();
        adminUser.setId(10L);
        adminUser.setName("Admin User");
        adminUser.setRestaurant(restaurantA);

        riceItem = new InventoryItem();
        riceItem.setId(100L);
        riceItem.setRestaurant(restaurantA);
        riceItem.setName("Rice");
        riceItem.setUnit("KG");
        riceItem.setCurrentQuantity(new BigDecimal("10.000"));
        riceItem.setMinimumQuantity(new BigDecimal("15.000"));
        riceItem.setCostPerUnit(new BigDecimal("50.00"));
        riceItem.setIsActive(true);
    }

    // 1. ADMIN creates inventory item
    @Test
    void testAdminCreatesInventoryItem() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(itemRepository.existsByRestaurantIdAndNameIgnoreCase(1L, "Rice")).thenReturn(false);
        when(restaurantRepository.getReferenceById(1L)).thenReturn(restaurantA);
        when(itemRepository.save(any(InventoryItem.class))).thenAnswer(i -> {
            InventoryItem item = i.getArgument(0);
            item.setId(100L);
            return item;
        });

        InventoryItemRequest req = new InventoryItemRequest("Rice", "KG", new BigDecimal("10.000"), new BigDecimal("55.00"), true);
        InventoryItemResponse res = inventoryService.createInventoryItem(req);

        assertNotNull(res);
        assertEquals("Rice", res.getName());
        assertEquals(BigDecimal.ZERO.setScale(3), res.getCurrentQuantity());
        assertTrue(res.getLowStock()); // 0 <= 10 -> lowStock true
    }

    // 2 & 3 & 23. Non-ADMIN (WAITER/KITCHEN) cannot create or modify inventory
    @Test
    void testNonAdminCannotCreateInventoryItem() {
        when(securityUtils.isAdmin()).thenReturn(false);

        InventoryItemRequest req = new InventoryItemRequest("Rice", "KG", new BigDecimal("10.000"), new BigDecimal("55.00"), true);
        assertThrows(AccessDeniedException.class, () -> inventoryService.createInventoryItem(req));
    }

    // 5. Duplicate inventory item rejected -> 409 Conflict
    @Test
    void testDuplicateInventoryItemRejected() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(itemRepository.existsByRestaurantIdAndNameIgnoreCase(1L, "Rice")).thenReturn(true);

        InventoryItemRequest req = new InventoryItemRequest("Rice", "KG", new BigDecimal("10.000"), new BigDecimal("55.00"), true);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> inventoryService.createInventoryItem(req));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    // 6 & 7. ADMIN gets tenant-isolated inventory list
    @Test
    void testGetAllInventoryItemsTenantIsolated() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(itemRepository.findByRestaurantId(1L)).thenReturn(Arrays.asList(riceItem));

        List<InventoryItemResponse> list = inventoryService.getAllInventoryItems();
        assertEquals(1, list.size());
        assertEquals("Rice", list.get(0).getName());
        assertEquals(1L, list.get(0).getRestaurantId());
    }

    // 8 & 27. Cross-tenant access rejected
    @Test
    void testCrossTenantInventoryItemAccessRejected() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(itemRepository.findByIdAndRestaurantId(200L, 1L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> inventoryService.getInventoryItemById(200L));
        assertTrue(ex.getMessage().contains("not found"));
    }

    // 9 & 10. Purchase increases stock and creates PURCHASE transaction
    @Test
    void testPurchaseIncreasesStockAndCreatesTransaction() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);

        when(itemRepository.findByIdAndRestaurantId(100L, 1L)).thenReturn(Optional.of(riceItem));
        when(userRepository.getReferenceById(10L)).thenReturn(adminUser);
        when(transactionRepository.save(any(InventoryTransaction.class))).thenAnswer(i -> {
            InventoryTransaction t = i.getArgument(0);
            t.setId(500L);
            return t;
        });

        InventoryTransactionRequest req = new InventoryTransactionRequest(new BigDecimal("25.000"), "PURCHASE", null);
        InventoryTransactionResponse res = inventoryService.purchaseStock(100L, req);

        assertNotNull(res);
        assertEquals("PURCHASE", res.getType());
        assertEquals(new BigDecimal("25.000"), res.getQuantity());
        assertEquals(new BigDecimal("35.000"), riceItem.getCurrentQuantity()); // 10 + 25 = 35
        verify(itemRepository).save(riceItem);
    }

    // 11 & 12. Adjustment positive increases, negative decreases stock
    @Test
    void testAdjustmentStock() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);

        when(itemRepository.findByIdAndRestaurantId(100L, 1L)).thenReturn(Optional.of(riceItem));
        when(userRepository.getReferenceById(10L)).thenReturn(adminUser);
        when(transactionRepository.save(any(InventoryTransaction.class))).thenAnswer(i -> i.getArgument(0));

        // Negative adjustment -3 (10 -> 7)
        InventoryTransactionRequest req1 = new InventoryTransactionRequest(new BigDecimal("-3.000"), "ADJUSTMENT", null);
        inventoryService.adjustStock(100L, req1);
        assertEquals(new BigDecimal("7.000"), riceItem.getCurrentQuantity());

        // Positive adjustment +5 (7 -> 12)
        InventoryTransactionRequest req2 = new InventoryTransactionRequest(new BigDecimal("5.000"), "ADJUSTMENT", null);
        inventoryService.adjustStock(100L, req2);
        assertEquals(new BigDecimal("12.000"), riceItem.getCurrentQuantity());
    }

    // 13. Adjustment cannot make stock negative -> 400 Bad Request
    @Test
    void testAdjustmentCannotMakeStockNegative() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(itemRepository.findByIdAndRestaurantId(100L, 1L)).thenReturn(Optional.of(riceItem));

        InventoryTransactionRequest req = new InventoryTransactionRequest(new BigDecimal("-15.000"), "ADJUSTMENT", null);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> inventoryService.adjustStock(100L, req));
        assertTrue(ex.getMessage().contains("negative stock"));
    }

    // 14 & 15. Wastage decreases stock and cannot exceed current stock
    @Test
    void testWastageDecreasesStockAndCannotExceedCurrentStock() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserId()).thenReturn(10L);
        when(itemRepository.findByIdAndRestaurantId(100L, 1L)).thenReturn(Optional.of(riceItem));

        // Wastage exceeding current stock (15 > 10) -> 400 Bad Request
        InventoryTransactionRequest invalidReq = new InventoryTransactionRequest(new BigDecimal("15.000"), "WASTAGE", null);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> inventoryService.recordWastage(100L, invalidReq));
        assertTrue(ex.getMessage().contains("cannot exceed current stock"));

        // Valid wastage 2.000 (10 -> 8)
        when(userRepository.getReferenceById(10L)).thenReturn(adminUser);
        when(transactionRepository.save(any(InventoryTransaction.class))).thenAnswer(i -> i.getArgument(0));

        InventoryTransactionRequest validReq = new InventoryTransactionRequest(new BigDecimal("2.000"), "WASTAGE", null);
        inventoryService.recordWastage(100L, validReq);
        assertEquals(new BigDecimal("8.000"), riceItem.getCurrentQuantity());
    }

    // 16-19. Zero or negative invalid transaction quantities rejected
    @Test
    void testInvalidTransactionQuantitiesRejected() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(itemRepository.findByIdAndRestaurantId(100L, 1L)).thenReturn(Optional.of(riceItem));

        // Purchase zero or negative
        assertThrows(RuntimeException.class, () -> inventoryService.purchaseStock(100L, new InventoryTransactionRequest(BigDecimal.ZERO, "PURCHASE", null)));
        assertThrows(RuntimeException.class, () -> inventoryService.purchaseStock(100L, new InventoryTransactionRequest(new BigDecimal("-5.000"), "PURCHASE", null)));

        // Wastage zero or negative
        assertThrows(RuntimeException.class, () -> inventoryService.recordWastage(100L, new InventoryTransactionRequest(BigDecimal.ZERO, "WASTAGE", null)));
        assertThrows(RuntimeException.class, () -> inventoryService.recordWastage(100L, new InventoryTransactionRequest(new BigDecimal("-1.000"), "WASTAGE", null)));

        // Adjustment zero
        assertThrows(RuntimeException.class, () -> inventoryService.adjustStock(100L, new InventoryTransactionRequest(BigDecimal.ZERO, "ADJUSTMENT", null)));
    }

    // 20. Inactive item cannot receive transactions
    @Test
    void testInactiveItemCannotReceiveTransactions() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        riceItem.setIsActive(false);
        when(itemRepository.findByIdAndRestaurantId(100L, 1L)).thenReturn(Optional.of(riceItem));

        InventoryTransactionRequest req = new InventoryTransactionRequest(new BigDecimal("5.000"), "PURCHASE", null);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> inventoryService.purchaseStock(100L, req));
        assertTrue(ex.getMessage().contains("Inactive inventory item cannot receive transactions"));
    }

    // 24. Low stock flag works correctly
    @Test
    void testLowStockFlagCalculation() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        // 10 <= 15 -> lowStock = true
        riceItem.setCurrentQuantity(new BigDecimal("10.000"));
        riceItem.setMinimumQuantity(new BigDecimal("15.000"));
        when(itemRepository.findByIdAndRestaurantId(100L, 1L)).thenReturn(Optional.of(riceItem));

        InventoryItemResponse res1 = inventoryService.getInventoryItemById(100L);
        assertTrue(res1.getLowStock());

        // 20 > 15 -> lowStock = false
        riceItem.setCurrentQuantity(new BigDecimal("20.000"));
        InventoryItemResponse res2 = inventoryService.getInventoryItemById(100L);
        assertFalse(res2.getLowStock());
    }

    // 26. Inventory history remains accessible after deactivation
    @Test
    void testInventoryHistoryRemainsAfterDeactivation() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        riceItem.setIsActive(false);
        when(itemRepository.findByIdAndRestaurantId(100L, 1L)).thenReturn(Optional.of(riceItem));

        InventoryTransaction tx = new InventoryTransaction();
        tx.setId(1L);
        tx.setInventoryItem(riceItem);
        tx.setType("PURCHASE");
        tx.setQuantity(new BigDecimal("10.000"));

        when(transactionRepository.findByInventoryItemIdAndInventoryItemRestaurantIdOrderByCreatedAtDesc(100L, 1L))
                .thenReturn(Arrays.asList(tx));

        List<InventoryTransactionResponse> history = inventoryService.getTransactionsByItemId(100L);
        assertEquals(1, history.size());
        assertEquals("PURCHASE", history.get(0).getType());
    }
}
