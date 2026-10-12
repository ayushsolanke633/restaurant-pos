package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.TableRequest;
import com.restaurant.restaurantpos.dto.TableResponse;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RestaurantTable;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RestaurantTableRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.RestaurantTableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TableOrderingTest {

    @Mock
    private RestaurantTableRepository tableRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private RestaurantTableService tableService;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");
    }

    @Test
    void testGetAllTablesReturnsAscendingTableNumberOrder() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        // Unsorted input table numbers: 16, 13, 1, 15, 11, 2, 8, 7, 3, 4, 5, 14
        int[] tableNums = {16, 13, 1, 15, 11, 2, 8, 7, 3, 4, 5, 14};
        List<RestaurantTable> mockTables = new ArrayList<>();
        long idCounter = 100L;
        for (int num : tableNums) {
            RestaurantTable t = new RestaurantTable();
            t.setId(idCounter++);
            t.setRestaurant(restaurant);
            t.setTableNumber(num);
            t.setCapacity(4);
            t.setStatus("AVAILABLE");
            mockTables.add(t);
        }

        // Repository findByRestaurantIdOrderByTableNumberAsc sorts by tableNumber ASC
        mockTables.sort(Comparator.comparingInt(RestaurantTable::getTableNumber));
        when(tableRepository.findByRestaurantIdOrderByTableNumberAsc(1L)).thenReturn(mockTables);

        List<TableResponse> responses = tableService.getAllTables();
        List<Integer> actualOrder = responses.stream().map(TableResponse::getTableNumber).collect(Collectors.toList());

        List<Integer> expectedOrder = List.of(1, 2, 3, 4, 5, 7, 8, 11, 13, 14, 15, 16);
        assertEquals(expectedOrder, actualOrder, "getAllTables must return tables in ascending tableNumber order");
    }

    @Test
    void testStatusesDoNotAffectOrdering() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);

        RestaurantTable t1 = new RestaurantTable(101L, restaurant, 1, 4, "OCCUPIED", null, null);
        RestaurantTable t2 = new RestaurantTable(102L, restaurant, 2, 2, "AVAILABLE", null, null);
        RestaurantTable t3 = new RestaurantTable(103L, restaurant, 3, 6, "BILLING", null, null);
        RestaurantTable t4 = new RestaurantTable(104L, restaurant, 4, 4, "AVAILABLE", null, null);

        List<RestaurantTable> sortedList = List.of(t1, t2, t3, t4);
        when(tableRepository.findByRestaurantIdOrderByTableNumberAsc(1L)).thenReturn(sortedList);

        List<TableResponse> responses = tableService.getAllTables();
        List<Integer> actualOrder = responses.stream().map(TableResponse::getTableNumber).collect(Collectors.toList());

        assertEquals(List.of(1, 2, 3, 4), actualOrder, "Table statuses must not alter numerical tableNumber order");
    }

    @Test
    void testUpdateTableNumberPreservesIdAndChangesNumber() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        RestaurantTable existingTable = new RestaurantTable(27L, restaurant, 5, 4, "AVAILABLE", null, null);
        when(tableRepository.findById(27L)).thenReturn(Optional.of(existingTable));
        when(tableRepository.existsByRestaurantIdAndTableNumberAndIdNot(1L, 20, 27L)).thenReturn(false);
        when(tableRepository.save(any(RestaurantTable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TableRequest updateReq = new TableRequest();
        updateReq.setTableNumber(20);
        updateReq.setCapacity(4);

        TableResponse response = tableService.updateTable(27L, updateReq);

        assertEquals(27L, response.getId(), "Table ID must remain 27L when updating tableNumber");
        assertEquals(20, response.getTableNumber(), "Table number must update to 20");
    }

    @Test
    void testCreateTableAssignsTableNumber() {
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(tableRepository.existsByRestaurantIdAndTableNumber(1L, 9)).thenReturn(false);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(tableRepository.save(any(RestaurantTable.class))).thenAnswer(invocation -> {
            RestaurantTable t = invocation.getArgument(0);
            t.setId(301L);
            return t;
        });

        TableRequest createReq = new TableRequest();
        createReq.setTableNumber(9);
        createReq.setCapacity(4);

        TableResponse response = tableService.createTable(createReq);

        assertNotNull(response);
        assertEquals(9, response.getTableNumber());
        assertEquals(301L, response.getId());
        verify(tableRepository).existsByRestaurantIdAndTableNumber(1L, 9);
    }
}
