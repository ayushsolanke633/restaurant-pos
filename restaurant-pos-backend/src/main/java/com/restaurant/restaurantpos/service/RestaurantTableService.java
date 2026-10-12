package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.dto.TableRequest;
import com.restaurant.restaurantpos.dto.TableResponse;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RestaurantTable;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RestaurantTableRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RestaurantTableService {

    private final RestaurantTableRepository tableRepository;
    private final RestaurantRepository restaurantRepository;
    private final SecurityUtils securityUtils;

    public RestaurantTableService(
            RestaurantTableRepository tableRepository,
            RestaurantRepository restaurantRepository,
            SecurityUtils securityUtils) {
        this.tableRepository = tableRepository;
        this.restaurantRepository = restaurantRepository;
        this.securityUtils = securityUtils;
    }

    public TableResponse createTable(TableRequest request) {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }

        if (tableRepository.existsByRestaurantIdAndTableNumber(restaurantId, request.getTableNumber())) {
            throw new RuntimeException("Table number already exists in this restaurant");
        }

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setTableNumber(request.getTableNumber());
        table.setCapacity(request.getCapacity());
        table.setStatus("AVAILABLE");

        RestaurantTable saved = tableRepository.save(table);
        return mapToResponse(saved);
    }

    public List<TableResponse> getAllTables() {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        return tableRepository.findByRestaurantIdOrderByTableNumberAsc(restaurantId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public TableResponse getTableById(Long id) {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        RestaurantTable table = findTableScoped(id, restaurantId);
        return mapToResponse(table);
    }

    public TableResponse updateTable(Long id, TableRequest request) {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        RestaurantTable table = findTableScoped(id, restaurantId);

        if (tableRepository.existsByRestaurantIdAndTableNumberAndIdNot(restaurantId, request.getTableNumber(), id)) {
            throw new RuntimeException("Table number already exists in this restaurant");
        }

        table.setTableNumber(request.getTableNumber());
        table.setCapacity(request.getCapacity());

        RestaurantTable saved = tableRepository.save(table);
        return mapToResponse(saved);
    }

    public TableResponse updateStatusToAvailable(Long id) {
        return changeStatus(id, "AVAILABLE");
    }

    public TableResponse updateStatusToOccupied(Long id) {
        return changeStatus(id, "OCCUPIED");
    }

    public TableResponse updateStatusToBilling(Long id) {
        return changeStatus(id, "BILLING");
    }

    private TableResponse changeStatus(Long id, String targetStatus) {
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        RestaurantTable table = findTableScoped(id, restaurantId);

        validateStatusTransition(table.getStatus(), targetStatus);

        table.setStatus(targetStatus);
        RestaurantTable saved = tableRepository.save(table);
        return mapToResponse(saved);
    }

    private RestaurantTable findTableScoped(Long id, Long restaurantId) {
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant context required");
        }
        java.util.Optional<RestaurantTable> opt = tableRepository.findByIdAndRestaurantId(id, restaurantId);
        if (opt.isPresent()) {
            return opt.get();
        }
        RestaurantTable table = tableRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Table not found"));
        if (table.getRestaurant() == null || !restaurantId.equals(table.getRestaurant().getId())) {
            throw new RuntimeException("Table not found");
        }
        return table;
    }

    private void validateStatusTransition(String currentStatus, String targetStatus) {
        if (currentStatus != null && currentStatus.equalsIgnoreCase(targetStatus)) {
            return;
        }

        boolean valid = false;
        if ("AVAILABLE".equalsIgnoreCase(currentStatus) && "OCCUPIED".equalsIgnoreCase(targetStatus)) {
            valid = true;
        } else if ("OCCUPIED".equalsIgnoreCase(currentStatus) && "BILLING".equalsIgnoreCase(targetStatus)) {
            valid = true;
        } else if ("BILLING".equalsIgnoreCase(currentStatus) && "AVAILABLE".equalsIgnoreCase(targetStatus)) {
            valid = true;
        }

        if (!valid) {
            throw new RuntimeException("Invalid table status transition from " + currentStatus + " to " + targetStatus);
        }
    }

    private TableResponse mapToResponse(RestaurantTable table) {
        return TableResponse.builder()
                .id(table.getId())
                .restaurantId(table.getRestaurant() != null ? table.getRestaurant().getId() : null)
                .tableNumber(table.getTableNumber())
                .capacity(table.getCapacity())
                .status(table.getStatus())
                .createdAt(table.getCreatedAt())
                .updatedAt(table.getUpdatedAt())
                .build();
    }
}
