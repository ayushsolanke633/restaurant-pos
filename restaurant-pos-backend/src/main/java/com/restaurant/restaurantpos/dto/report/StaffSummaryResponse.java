package com.restaurant.restaurantpos.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffSummaryResponse {

    private Long totalStaff;
    private Long activeStaff;
    private Long inactiveStaff;
    private Long adminCount;
    private Long waiterCount;
    private Long kitchenCount;
}
