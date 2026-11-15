package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.dto.MenuItemRequest;
import com.restaurant.restaurantpos.dto.MenuItemResponse;
import com.restaurant.restaurantpos.dto.MenuItemVariationRequest;
import com.restaurant.restaurantpos.dto.MenuItemVariationResponse;
import com.restaurant.restaurantpos.entity.Category;
import com.restaurant.restaurantpos.entity.MenuItem;
import com.restaurant.restaurantpos.entity.MenuItemVariation;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.repository.CategoryRepository;
import com.restaurant.restaurantpos.repository.MenuItemRepository;
import com.restaurant.restaurantpos.repository.MenuItemVariationRepository;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final MenuItemVariationRepository menuItemVariationRepository;
    private final CategoryRepository categoryRepository;
    private final RestaurantRepository restaurantRepository;
    private final SecurityUtils securityUtils;

    public MenuItemService(
            MenuItemRepository menuItemRepository,
            CategoryRepository categoryRepository,
            RestaurantRepository restaurantRepository,
            SecurityUtils securityUtils) {
        this(menuItemRepository, null, categoryRepository, restaurantRepository, securityUtils);
    }

    @Autowired
    public MenuItemService(
            MenuItemRepository menuItemRepository,
            MenuItemVariationRepository menuItemVariationRepository,
            CategoryRepository categoryRepository,
            RestaurantRepository restaurantRepository,
            SecurityUtils securityUtils) {
        this.menuItemRepository = menuItemRepository;
        this.menuItemVariationRepository = menuItemVariationRepository;
        this.categoryRepository = categoryRepository;
        this.restaurantRepository = restaurantRepository;
        this.securityUtils = securityUtils;
    }

    @Transactional
    public MenuItemResponse createMenuItem(MenuItemRequest request) {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found with id: " + restaurantId));

        Category category = findCategoryScoped(request.getCategoryId(), restaurantId);

        List<MenuItemVariationRequest> variationRequests = request.getVariations();
        boolean hasVariations = variationRequests != null && !variationRequests.isEmpty();

        BigDecimal basePrice = request.getPrice();
        if (basePrice == null) {
            if (!hasVariations) {
                throw new RuntimeException("Price is required");
            }
            // Auto-resolve base price from the first/lowest variation
            basePrice = variationRequests.stream()
                    .map(MenuItemVariationRequest::getPrice)
                    .filter(Objects::nonNull)
                    .min(BigDecimal::compareTo)
                    .orElseThrow(() -> new RuntimeException("Variation price is required"));
        }

        MenuItem menuItem = new MenuItem();
        menuItem.setRestaurant(restaurant);
        menuItem.setCategory(category);
        menuItem.setName(request.getName());
        menuItem.setDescription(request.getDescription());
        menuItem.setPrice(basePrice);
        menuItem.setIsAvailable(request.getIsAvailable() != null ? request.getIsAvailable() : true);

        MenuItem savedItem = menuItemRepository.save(menuItem);

        if (hasVariations) {
            saveOrUpdateVariations(savedItem, restaurant, variationRequests);
        }

        return mapToResponse(savedItem);
    }

    @Transactional(readOnly = true)
    public List<MenuItemResponse> getMenuItems(Long categoryId) {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }

        if (categoryId != null) {
            findCategoryScoped(categoryId, restaurantId);

            return menuItemRepository.findByRestaurantIdAndCategoryId(restaurantId, categoryId)
                    .stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        }

        return menuItemRepository.findByRestaurantId(restaurantId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MenuItemResponse getMenuItemById(Long id) {
        MenuItem menuItem = findMenuItemById(id);
        return mapToResponse(menuItem);
    }

    @Transactional
    public MenuItemResponse updateMenuItem(Long id, MenuItemRequest request) {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        MenuItem menuItem = findMenuItemById(id);

        if (request.getCategoryId() != null && !request.getCategoryId().equals(menuItem.getCategory().getId())) {
            Category newCategory = findCategoryScoped(request.getCategoryId(), restaurantId);
            menuItem.setCategory(newCategory);
        }

        menuItem.setName(request.getName());
        menuItem.setDescription(request.getDescription());

        List<MenuItemVariationRequest> variationRequests = request.getVariations();
        boolean hasVariations = variationRequests != null && !variationRequests.isEmpty();

        BigDecimal basePrice = request.getPrice();
        if (basePrice != null) {
            menuItem.setPrice(basePrice);
        } else if (hasVariations) {
            basePrice = variationRequests.stream()
                    .map(MenuItemVariationRequest::getPrice)
                    .filter(Objects::nonNull)
                    .min(BigDecimal::compareTo)
                    .orElse(menuItem.getPrice());
            menuItem.setPrice(basePrice);
        }

        if (request.getIsAvailable() != null) {
            menuItem.setIsAvailable(request.getIsAvailable());
        }

        MenuItem updatedItem = menuItemRepository.save(menuItem);

        if (variationRequests != null) {
            saveOrUpdateVariations(updatedItem, updatedItem.getRestaurant(), variationRequests);
        }

        return mapToResponse(updatedItem);
    }

    @Transactional
    public MenuItemResponse setAvailable(Long id) {
        MenuItem menuItem = findMenuItemById(id);
        menuItem.setIsAvailable(true);
        MenuItem savedItem = menuItemRepository.save(menuItem);
        return mapToResponse(savedItem);
    }

    @Transactional
    public MenuItemResponse setUnavailable(Long id) {
        MenuItem menuItem = findMenuItemById(id);
        menuItem.setIsAvailable(false);
        MenuItem savedItem = menuItemRepository.save(menuItem);
        return mapToResponse(savedItem);
    }

    private void saveOrUpdateVariations(MenuItem menuItem, Restaurant restaurant, List<MenuItemVariationRequest> requests) {
        if (menuItemVariationRepository == null) {
            return;
        }
        List<MenuItemVariation> existing = menuItemVariationRepository
                .findByMenuItemIdAndRestaurantIdOrderBySortOrderAscIdAsc(menuItem.getId(), restaurant.getId());

        Set<Long> processedIds = new HashSet<>();
        Set<String> processedNames = new HashSet<>();
        List<MenuItemVariation> toSave = new ArrayList<>();

        for (int i = 0; i < requests.size(); i++) {
            MenuItemVariationRequest req = requests.get(i);
            String name = req.getName() != null ? req.getName().trim() : "";
            if (name.isBlank()) {
                throw new RuntimeException("Variation name cannot be blank");
            }
            if (name.length() > 100) {
                throw new RuntimeException("Variation name must not exceed 100 characters");
            }
            if (!processedNames.add(name.toLowerCase())) {
                throw new RuntimeException("Duplicate variation name for menu item: " + name);
            }
            if (req.getPrice() == null || req.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Variation price cannot be negative or null");
            }

            MenuItemVariation variation = null;
            if (req.getId() != null) {
                variation = existing.stream()
                        .filter(v -> v.getId().equals(req.getId()))
                        .findFirst()
                        .orElse(null);
            }

            if (variation == null) {
                variation = new MenuItemVariation();
                variation.setRestaurant(restaurant);
                variation.setMenuItem(menuItem);
            }

            variation.setName(name);
            variation.setPrice(req.getPrice());
            variation.setIsAvailable(req.getIsAvailable() != null ? req.getIsAvailable() : true);
            variation.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : i);

            toSave.add(variation);
            if (variation.getId() != null) {
                processedIds.add(variation.getId());
            }
        }

        // Delete any existing variations not in request
        for (MenuItemVariation v : existing) {
            if (!processedIds.contains(v.getId())) {
                menuItemVariationRepository.delete(v);
            }
        }

        menuItemVariationRepository.saveAll(toSave);
    }

    private MenuItem findMenuItemById(Long id) {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
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

    private Category findCategoryScoped(Long categoryId, Long restaurantId) {
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        Optional<Category> opt = categoryRepository.findByIdAndRestaurantId(categoryId, restaurantId);
        if (opt.isPresent()) {
            return opt.get();
        }
        Category cat = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + categoryId));
        if (cat.getRestaurant() == null || !restaurantId.equals(cat.getRestaurant().getId())) {
            throw new RuntimeException("Category not found with id: " + categoryId);
        }
        return cat;
    }

    private MenuItemResponse mapToResponse(MenuItem menuItem) {
        Long restaurantId = menuItem.getRestaurant() != null ? menuItem.getRestaurant().getId() : null;
        List<MenuItemVariationResponse> variationResponses = null;

        if (menuItem.getId() != null && restaurantId != null && menuItemVariationRepository != null) {
            List<MenuItemVariation> vars = menuItemVariationRepository
                    .findByMenuItemIdAndRestaurantIdOrderBySortOrderAscIdAsc(menuItem.getId(), restaurantId);
            if (vars != null && !vars.isEmpty()) {
                variationResponses = vars.stream()
                        .map(v -> MenuItemVariationResponse.builder()
                                .id(v.getId())
                                .menuItemId(menuItem.getId())
                                .name(v.getName())
                                .price(v.getPrice())
                                .isAvailable(v.getIsAvailable())
                                .sortOrder(v.getSortOrder())
                                .build())
                        .collect(Collectors.toList());
            }
        }

        return MenuItemResponse.builder()
                .id(menuItem.getId())
                .restaurantId(restaurantId)
                .categoryId(menuItem.getCategory() != null ? menuItem.getCategory().getId() : null)
                .categoryName(menuItem.getCategory() != null ? menuItem.getCategory().getName() : null)
                .categoryIsActive(menuItem.getCategory() != null ? menuItem.getCategory().getIsActive() : true)
                .name(menuItem.getName())
                .description(menuItem.getDescription())
                .price(menuItem.getPrice())
                .isAvailable(menuItem.getIsAvailable())
                .variations(variationResponses)
                .createdAt(menuItem.getCreatedAt())
                .updatedAt(menuItem.getUpdatedAt())
                .build();
    }
}
