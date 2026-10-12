package com.restaurant.restaurantpos.service;

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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private final InventoryItemRepository itemRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;

    public InventoryService(
            InventoryItemRepository itemRepository,
            InventoryTransactionRepository transactionRepository,
            RestaurantRepository restaurantRepository,
            UserRepository userRepository,
            SecurityUtils securityUtils) {
        this.itemRepository = itemRepository;
        this.transactionRepository = transactionRepository;
        this.restaurantRepository = restaurantRepository;
        this.userRepository = userRepository;
        this.securityUtils = securityUtils;
    }

    @Transactional
    public InventoryItemResponse createInventoryItem(InventoryItemRequest request) {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }

        Long restaurantId = securityUtils.getCurrentRestaurantId();
        String name = request.getName() != null ? request.getName().trim() : "";

        if (itemRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantId, name)) {
            throw new RuntimeException("Inventory item with name '" + name + "' already exists");
        }

        Restaurant restaurant = restaurantRepository.getReferenceById(restaurantId);

        InventoryItem item = new InventoryItem();
        item.setRestaurant(restaurant);
        item.setName(name);
        item.setUnit(request.getUnit().trim());
        item.setCurrentQuantity(BigDecimal.ZERO.setScale(3));
        item.setMinimumQuantity(request.getMinimumQuantity());
        item.setCostPerUnit(request.getCostPerUnit());
        item.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        InventoryItem savedItem = itemRepository.save(item);
        return mapToItemResponse(savedItem);
    }

    public List<InventoryItemResponse> getAllInventoryItems() {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        List<InventoryItem> items = itemRepository.findByRestaurantId(restaurantId);
        return items.stream().map(this::mapToItemResponse).collect(Collectors.toList());
    }

    public InventoryItemResponse getInventoryItemById(Long id) {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        InventoryItem item = itemRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new RuntimeException("Inventory item not found"));
        return mapToItemResponse(item);
    }

    @Transactional
    public InventoryItemResponse updateInventoryItem(Long id, InventoryItemRequest request) {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        InventoryItem item = itemRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new RuntimeException("Inventory item not found"));

        String name = request.getName() != null ? request.getName().trim() : "";
        if (itemRepository.existsByRestaurantIdAndNameIgnoreCaseAndIdNot(restaurantId, name, id)) {
            throw new RuntimeException("Inventory item with name '" + name + "' already exists");
        }

        item.setName(name);
        item.setUnit(request.getUnit().trim());
        item.setMinimumQuantity(request.getMinimumQuantity());
        item.setCostPerUnit(request.getCostPerUnit());
        if (request.getIsActive() != null) {
            item.setIsActive(request.getIsActive());
        }

        InventoryItem saved = itemRepository.save(item);
        return mapToItemResponse(saved);
    }

    @Transactional
    public InventoryItemResponse activateInventoryItem(Long id) {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        InventoryItem item = itemRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new RuntimeException("Inventory item not found"));

        item.setIsActive(true);
        InventoryItem saved = itemRepository.save(item);
        return mapToItemResponse(saved);
    }

    @Transactional
    public InventoryItemResponse deactivateInventoryItem(Long id) {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        InventoryItem item = itemRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new RuntimeException("Inventory item not found"));

        item.setIsActive(false);
        InventoryItem saved = itemRepository.save(item);
        return mapToItemResponse(saved);
    }

    @Transactional
    public InventoryTransactionResponse purchaseStock(Long id, InventoryTransactionRequest request) {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        Long currentUserId = securityUtils.getCurrentUserId();

        InventoryItem item = itemRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new RuntimeException("Inventory item not found"));

        if (!Boolean.TRUE.equals(item.getIsActive())) {
            throw new RuntimeException("Inactive inventory item cannot receive transactions");
        }

        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Purchase quantity must be greater than 0");
        }

        item.setCurrentQuantity(item.getCurrentQuantity().add(request.getQuantity()));
        itemRepository.save(item);

        User createdBy = userRepository.getReferenceById(currentUserId);

        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setRestaurant(item.getRestaurant());
        transaction.setInventoryItem(item);
        transaction.setType("PURCHASE");
        transaction.setQuantity(request.getQuantity());
        transaction.setReferenceType(request.getReferenceType() != null ? request.getReferenceType() : "PURCHASE");
        transaction.setReferenceId(request.getReferenceId());
        transaction.setCreatedBy(createdBy);

        InventoryTransaction savedTransaction = transactionRepository.save(transaction);
        return mapToTransactionResponse(savedTransaction);
    }

    @Transactional
    public InventoryTransactionResponse adjustStock(Long id, InventoryTransactionRequest request) {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        Long currentUserId = securityUtils.getCurrentUserId();

        InventoryItem item = itemRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new RuntimeException("Inventory item not found"));

        if (!Boolean.TRUE.equals(item.getIsActive())) {
            throw new RuntimeException("Inactive inventory item cannot receive transactions");
        }

        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            throw new RuntimeException("Adjustment quantity cannot be zero");
        }

        BigDecimal newQuantity = item.getCurrentQuantity().add(request.getQuantity());
        if (newQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Stock adjustment cannot result in negative stock");
        }

        item.setCurrentQuantity(newQuantity);
        itemRepository.save(item);

        User createdBy = userRepository.getReferenceById(currentUserId);

        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setRestaurant(item.getRestaurant());
        transaction.setInventoryItem(item);
        transaction.setType("ADJUSTMENT");
        transaction.setQuantity(request.getQuantity());
        transaction.setReferenceType(request.getReferenceType() != null ? request.getReferenceType() : "ADJUSTMENT");
        transaction.setReferenceId(request.getReferenceId());
        transaction.setCreatedBy(createdBy);

        InventoryTransaction savedTransaction = transactionRepository.save(transaction);
        return mapToTransactionResponse(savedTransaction);
    }

    @Transactional
    public InventoryTransactionResponse recordWastage(Long id, InventoryTransactionRequest request) {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        Long currentUserId = securityUtils.getCurrentUserId();

        InventoryItem item = itemRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new RuntimeException("Inventory item not found"));

        if (!Boolean.TRUE.equals(item.getIsActive())) {
            throw new RuntimeException("Inactive inventory item cannot receive transactions");
        }

        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Wastage quantity must be greater than 0");
        }

        if (request.getQuantity().compareTo(item.getCurrentQuantity()) > 0) {
            throw new RuntimeException("Wastage quantity cannot exceed current stock");
        }

        item.setCurrentQuantity(item.getCurrentQuantity().subtract(request.getQuantity()));
        itemRepository.save(item);

        User createdBy = userRepository.getReferenceById(currentUserId);

        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setRestaurant(item.getRestaurant());
        transaction.setInventoryItem(item);
        transaction.setType("WASTAGE");
        transaction.setQuantity(request.getQuantity());
        transaction.setReferenceType(request.getReferenceType() != null ? request.getReferenceType() : "WASTAGE");
        transaction.setReferenceId(request.getReferenceId());
        transaction.setCreatedBy(createdBy);

        InventoryTransaction savedTransaction = transactionRepository.save(transaction);
        return mapToTransactionResponse(savedTransaction);
    }

    public List<InventoryTransactionResponse> getAllTransactions() {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        List<InventoryTransaction> transactions = transactionRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
        if (transactions.isEmpty()) {
            transactions = transactionRepository.findByInventoryItemRestaurantIdOrderByCreatedAtDesc(restaurantId);
        }
        return transactions.stream().map(this::mapToTransactionResponse).collect(Collectors.toList());
    }

    public List<InventoryTransactionResponse> getTransactionsByItemId(Long itemId) {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        InventoryItem item = itemRepository.findByIdAndRestaurantId(itemId, restaurantId)
                .orElseThrow(() -> new RuntimeException("Inventory item not found"));

        List<InventoryTransaction> transactions = transactionRepository.findByInventoryItemIdAndRestaurantIdOrderByCreatedAtDesc(item.getId(), restaurantId);
        if (transactions.isEmpty()) {
            transactions = transactionRepository.findByInventoryItemIdAndInventoryItemRestaurantIdOrderByCreatedAtDesc(item.getId(), restaurantId);
        }
        return transactions.stream().map(this::mapToTransactionResponse).collect(Collectors.toList());
    }

    private InventoryItemResponse mapToItemResponse(InventoryItem item) {
        boolean isLowStock = item.getCurrentQuantity() != null
                && item.getMinimumQuantity() != null
                && item.getCurrentQuantity().compareTo(item.getMinimumQuantity()) <= 0;

        return InventoryItemResponse.builder()
                .id(item.getId())
                .restaurantId(item.getRestaurant() != null ? item.getRestaurant().getId() : null)
                .name(item.getName())
                .unit(item.getUnit())
                .currentQuantity(item.getCurrentQuantity())
                .minimumQuantity(item.getMinimumQuantity())
                .costPerUnit(item.getCostPerUnit())
                .isActive(item.getIsActive())
                .lowStock(isLowStock)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private InventoryTransactionResponse mapToTransactionResponse(InventoryTransaction t) {
        return InventoryTransactionResponse.builder()
                .id(t.getId())
                .inventoryItemId(t.getInventoryItem() != null ? t.getInventoryItem().getId() : null)
                .inventoryItemName(t.getInventoryItem() != null ? t.getInventoryItem().getName() : null)
                .type(t.getType())
                .quantity(t.getQuantity())
                .referenceType(t.getReferenceType())
                .referenceId(t.getReferenceId())
                .createdById(t.getCreatedBy() != null ? t.getCreatedBy().getId() : null)
                .createdByName(t.getCreatedBy() != null ? t.getCreatedBy().getName() : null)
                .createdAt(t.getCreatedAt())
                .build();
    }
}
