package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.CategoryRequest;
import com.restaurant.restaurantpos.dto.CategoryResponse;
import com.restaurant.restaurantpos.dto.MenuItemRequest;
import com.restaurant.restaurantpos.dto.MenuItemResponse;
import com.restaurant.restaurantpos.entity.Category;
import com.restaurant.restaurantpos.entity.MenuItem;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.repository.CategoryRepository;
import com.restaurant.restaurantpos.repository.MenuItemRepository;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.CategoryService;
import com.restaurant.restaurantpos.service.MenuItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MenuSecurityTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private CategoryService categoryService;

    @InjectMocks
    private MenuItemService menuItemService;

    private Restaurant restaurant1;
    private Restaurant restaurant2;

    private Category category1;
    private Category categoryOtherTenant;

    private MenuItem menuItem1;

    @BeforeEach
    void setUp() {
        restaurant1 = new Restaurant();
        restaurant1.setId(1L);
        restaurant1.setName("Restaurant One");

        restaurant2 = new Restaurant();
        restaurant2.setId(2L);
        restaurant2.setName("Restaurant Two");

        category1 = new Category();
        category1.setId(10L);
        category1.setRestaurant(restaurant1);
        category1.setName("Appetizers");
        category1.setIsActive(true);

        categoryOtherTenant = new Category();
        categoryOtherTenant.setId(99L);
        categoryOtherTenant.setRestaurant(restaurant2);
        categoryOtherTenant.setName("Other Tenant Starters");
        categoryOtherTenant.setIsActive(true);

        menuItem1 = new MenuItem();
        menuItem1.setId(100L);
        menuItem1.setRestaurant(restaurant1);
        menuItem1.setCategory(category1);
        menuItem1.setName("Spring Rolls");
        menuItem1.setPrice(new BigDecimal("200.00"));
        menuItem1.setIsAvailable(true);
    }

    @Test
    @DisplayName("A. ADMIN can GET categories")
    void testAdminCanGetCategories() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(categoryRepository.findByRestaurantId(1L)).thenReturn(List.of(category1));

        List<CategoryResponse> categories = categoryService.getCategoriesByRestaurant();

        assertNotNull(categories);
        assertEquals(1, categories.size());
        assertEquals("Appetizers", categories.get(0).getName());
    }

    @Test
    @DisplayName("B. WAITER can GET categories")
    void testWaiterCanGetCategories() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(categoryRepository.findByRestaurantId(1L)).thenReturn(List.of(category1));

        List<CategoryResponse> categories = categoryService.getCategoriesByRestaurant();

        assertNotNull(categories);
        assertEquals(1, categories.size());
        assertEquals("Appetizers", categories.get(0).getName());
    }

    @Test
    @DisplayName("C. ADMIN can GET menu items")
    void testAdminCanGetMenuItems() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(menuItemRepository.findByRestaurantId(1L)).thenReturn(List.of(menuItem1));

        List<MenuItemResponse> items = menuItemService.getMenuItems(null);

        assertNotNull(items);
        assertEquals(1, items.size());
        assertEquals("Spring Rolls", items.get(0).getName());
    }

    @Test
    @DisplayName("D. WAITER can GET menu items")
    void testWaiterCanGetMenuItems() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(menuItemRepository.findByRestaurantId(1L)).thenReturn(List.of(menuItem1));

        List<MenuItemResponse> items = menuItemService.getMenuItems(null);

        assertNotNull(items);
        assertEquals(1, items.size());
        assertEquals("Spring Rolls", items.get(0).getName());
    }

    @Test
    @DisplayName("E. Service tenant isolation for Category")
    void testCategoryTenantIsolation() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(categoryRepository.findById(99L)).thenReturn(Optional.of(categoryOtherTenant));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> categoryService.getCategoryById(99L));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("F. Service tenant isolation for MenuItem")
    void testMenuItemTenantIsolation() {
        MenuItem otherTenantItem = new MenuItem();
        otherTenantItem.setId(999L);
        otherTenantItem.setRestaurant(restaurant2);
        otherTenantItem.setName("Other Item");

        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(menuItemRepository.findById(999L)).thenReturn(Optional.of(otherTenantItem));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> menuItemService.getMenuItemById(999L));
        assertTrue(ex.getMessage().contains("not found"));
    }
}
