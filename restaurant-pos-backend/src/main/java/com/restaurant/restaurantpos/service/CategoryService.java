package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.dto.CategoryRequest;
import com.restaurant.restaurantpos.dto.CategoryResponse;
import com.restaurant.restaurantpos.entity.Category;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.repository.CategoryRepository;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final RestaurantRepository restaurantRepository;
    private final SecurityUtils securityUtils;

    public CategoryService(
            CategoryRepository categoryRepository,
            RestaurantRepository restaurantRepository,
            SecurityUtils securityUtils) {
        this.categoryRepository = categoryRepository;
        this.restaurantRepository = restaurantRepository;
        this.securityUtils = securityUtils;
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found with id: " + restaurantId));

        Category category = new Category();
        category.setRestaurant(restaurant);
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        Category savedCategory = categoryRepository.save(category);
        return mapToResponse(savedCategory);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoriesByRestaurant() {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        return categoryRepository.findByRestaurantId(restaurantId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        Category category = findCategoryById(id);
        return mapToResponse(category);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = findCategoryById(id);

        category.setName(request.getName());
        category.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            category.setIsActive(request.getIsActive());
        }

        Category updatedCategory = categoryRepository.save(category);
        return mapToResponse(updatedCategory);
    }

    @Transactional
    public CategoryResponse activateCategory(Long id) {
        Category category = findCategoryById(id);
        category.setIsActive(true);
        Category savedCategory = categoryRepository.save(category);
        return mapToResponse(savedCategory);
    }

    @Transactional
    public CategoryResponse deactivateCategory(Long id) {
        Category category = findCategoryById(id);
        category.setIsActive(false);
        Category savedCategory = categoryRepository.save(category);
        return mapToResponse(savedCategory);
    }

    private Category findCategoryById(Long id) {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        java.util.Optional<Category> opt = categoryRepository.findByIdAndRestaurantId(id, restaurantId);
        if (opt.isPresent()) {
            return opt.get();
        }
        Category cat = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
        if (cat.getRestaurant() == null || !restaurantId.equals(cat.getRestaurant().getId())) {
            throw new RuntimeException("Category not found with id: " + id);
        }
        return cat;
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .restaurantId(category.getRestaurant() != null ? category.getRestaurant().getId() : null)
                .name(category.getName())
                .description(category.getDescription())
                .isActive(category.getIsActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
