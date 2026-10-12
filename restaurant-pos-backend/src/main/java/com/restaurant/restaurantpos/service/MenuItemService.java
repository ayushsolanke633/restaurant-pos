package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.dto.MenuItemRequest;
import com.restaurant.restaurantpos.dto.MenuItemResponse;
import com.restaurant.restaurantpos.entity.Category;
import com.restaurant.restaurantpos.entity.MenuItem;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.repository.CategoryRepository;
import com.restaurant.restaurantpos.repository.MenuItemRepository;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final CategoryRepository categoryRepository;
    private final RestaurantRepository restaurantRepository;
    private final SecurityUtils securityUtils;

    public MenuItemService(
            MenuItemRepository menuItemRepository,
            CategoryRepository categoryRepository,
            RestaurantRepository restaurantRepository,
            SecurityUtils securityUtils) {
        this.menuItemRepository = menuItemRepository;
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

        MenuItem menuItem = new MenuItem();
        menuItem.setRestaurant(restaurant);
        menuItem.setCategory(category);
        menuItem.setName(request.getName());
        menuItem.setDescription(request.getDescription());
        menuItem.setPrice(request.getPrice());
        menuItem.setIsAvailable(request.getIsAvailable() != null ? request.getIsAvailable() : true);

        MenuItem savedItem = menuItemRepository.save(menuItem);
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
        menuItem.setPrice(request.getPrice());
        if (request.getIsAvailable() != null) {
            menuItem.setIsAvailable(request.getIsAvailable());
        }

        MenuItem updatedItem = menuItemRepository.save(menuItem);
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

    private MenuItem findMenuItemById(Long id) {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        java.util.Optional<MenuItem> opt = menuItemRepository.findByIdAndRestaurantId(id, restaurantId);
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
        java.util.Optional<Category> opt = categoryRepository.findByIdAndRestaurantId(categoryId, restaurantId);
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
        return MenuItemResponse.builder()
                .id(menuItem.getId())
                .restaurantId(menuItem.getRestaurant() != null ? menuItem.getRestaurant().getId() : null)
                .categoryId(menuItem.getCategory() != null ? menuItem.getCategory().getId() : null)
                .categoryName(menuItem.getCategory() != null ? menuItem.getCategory().getName() : null)
                .categoryIsActive(menuItem.getCategory() != null ? menuItem.getCategory().getIsActive() : true)
                .name(menuItem.getName())
                .description(menuItem.getDescription())
                .price(menuItem.getPrice())
                .isAvailable(menuItem.getIsAvailable())
                .createdAt(menuItem.getCreatedAt())
                .updatedAt(menuItem.getUpdatedAt())
                .build();
    }
}
