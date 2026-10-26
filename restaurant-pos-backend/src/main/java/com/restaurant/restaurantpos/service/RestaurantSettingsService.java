package com.restaurant.restaurantpos.service;

import com.restaurant.restaurantpos.dto.RestaurantSettingsRequest;
import com.restaurant.restaurantpos.dto.RestaurantSettingsResponse;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RestaurantSettings;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RestaurantSettingsRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;

@Service
public class RestaurantSettingsService {

    private final RestaurantSettingsRepository settingsRepository;
    private final RestaurantRepository restaurantRepository;
    private final SecurityUtils securityUtils;

    public RestaurantSettingsService(
            RestaurantSettingsRepository settingsRepository,
            RestaurantRepository restaurantRepository,
            SecurityUtils securityUtils) {
        this.settingsRepository = settingsRepository;
        this.restaurantRepository = restaurantRepository;
        this.securityUtils = securityUtils;
    }

    @Transactional
    public RestaurantSettingsResponse getSettings() {
        String role = securityUtils.getCurrentUserRole();
        if (!"ADMIN".equalsIgnoreCase(role) && !"KITCHEN".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        RestaurantSettings settings = getOrCreateDefaultSettings(restaurantId);
        return mapToResponse(settings);
    }


    @Transactional
    public RestaurantSettingsResponse updateSettings(RestaurantSettingsRequest request) {
        if (!securityUtils.isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        Long restaurantId = securityUtils.getCurrentRestaurantId();
        RestaurantSettings settings = getOrCreateDefaultSettings(restaurantId);

        if (request.getTaxEnabled() != null) {
            settings.setTaxEnabled(request.getTaxEnabled());
        }

        if (request.getGstMode() != null) {
            String rawMode = request.getGstMode().trim();
            String mode = rawMode.toUpperCase(Locale.ROOT);
            if (!"GST_EXCLUDED".equals(mode) && !"GST_INCLUDED".equals(mode)) {
                throw new RuntimeException("Invalid GST mode: must be GST_EXCLUDED or GST_INCLUDED");
            }
            settings.setGstMode(mode);
        }

        if (request.getDefaultTaxRate() != null) {
            BigDecimal taxRate = request.getDefaultTaxRate();
            if (taxRate.compareTo(BigDecimal.ZERO) < 0 || taxRate.compareTo(new BigDecimal("100.00")) > 0) {
                throw new RuntimeException("Tax rate must be between 0 and 100");
            }
            BigDecimal normalizedRate = taxRate.setScale(2, RoundingMode.HALF_UP);
            BigDecimal cgstRate = normalizedRate.divide(BigDecimal.valueOf(2.0), 2, RoundingMode.HALF_UP);
            BigDecimal sgstRate = normalizedRate.subtract(cgstRate).setScale(2, RoundingMode.HALF_UP);
            settings.setDefaultTaxRate(normalizedRate);
            settings.setCgstRate(cgstRate);
            settings.setSgstRate(sgstRate);
        } else if (settings.getDefaultTaxRate() != null && (settings.getCgstRate() == null || settings.getSgstRate() == null)) {
            BigDecimal normalizedRate = settings.getDefaultTaxRate().setScale(2, RoundingMode.HALF_UP);
            BigDecimal cgstRate = normalizedRate.divide(BigDecimal.valueOf(2.0), 2, RoundingMode.HALF_UP);
            BigDecimal sgstRate = normalizedRate.subtract(cgstRate).setScale(2, RoundingMode.HALF_UP);
            settings.setCgstRate(cgstRate);
            settings.setSgstRate(sgstRate);
        }

        if (request.getServiceChargeEnabled() != null) {
            settings.setServiceChargeEnabled(request.getServiceChargeEnabled());
        }

        if (request.getDefaultServiceChargeRate() != null) {
            BigDecimal scRate = request.getDefaultServiceChargeRate();
            if (scRate.compareTo(BigDecimal.ZERO) < 0 || scRate.compareTo(new BigDecimal("100.00")) > 0) {
                throw new RuntimeException("Service charge rate must be between 0 and 100");
            }
            settings.setDefaultServiceChargeRate(scRate.setScale(2, RoundingMode.HALF_UP));
        }

        if (request.getCurrency() != null) {
            String currency = request.getCurrency().trim().toUpperCase();
            if (currency.length() != 3) {
                throw new RuntimeException("Invalid currency format: must be a 3-character ISO currency code");
            }
            settings.setCurrency(currency);
        }

        if (request.getTimezone() != null) {
            String tz = request.getTimezone().trim();
            try {
                ZoneId.of(tz);
            } catch (Exception e) {
                throw new RuntimeException("Invalid timezone");
            }
            settings.setTimezone(tz);
        }

        if (request.getInvoicePrefix() != null) {
            String prefix = request.getInvoicePrefix().trim().toUpperCase();
            if (prefix.isEmpty() || prefix.length() > 20) {
                throw new RuntimeException("Invalid invoice prefix: must be 1 to 20 characters");
            }
            settings.setInvoicePrefix(prefix);
        }

        if (request.getReceiptHeader() != null) {
            String header = request.getReceiptHeader().trim();
            if (header.length() > 500) {
                throw new RuntimeException("Receipt header cannot exceed 500 characters");
            }
            settings.setReceiptHeader(header);
        }

        if (request.getReceiptFooter() != null) {
            String footer = request.getReceiptFooter().trim();
            if (footer.length() > 500) {
                throw new RuntimeException("Receipt footer cannot exceed 500 characters");
            }
            settings.setReceiptFooter(footer);
        }

        boolean currentKitchenEnabled = settings.getKitchenEnabled() != null ? settings.getKitchenEnabled() : true;
        boolean targetKitchenEnabled = request.getKitchenEnabled() != null ? request.getKitchenEnabled() : currentKitchenEnabled;

        if (request.getKitchenEnabled() != null) {
            settings.setKitchenEnabled(request.getKitchenEnabled());
        }

        if (!targetKitchenEnabled) {
            if (Boolean.TRUE.equals(request.getAutoAcceptOrders())) {
                throw new RuntimeException("Kitchen Display System (KDS) must be enabled before Auto-Accept Orders can be turned on");
            }
            settings.setAutoAcceptOrders(false);
        } else {
            if (request.getAutoAcceptOrders() != null) {
                settings.setAutoAcceptOrders(request.getAutoAcceptOrders());
            }
        }

        RestaurantSettings saved = settingsRepository.save(settings);
        return mapToResponse(saved);
    }

    private RestaurantSettings getOrCreateDefaultSettings(Long restaurantId) {
        Optional<RestaurantSettings> opt = settingsRepository.findByRestaurantId(restaurantId);
        if (opt.isPresent()) {
            return opt.get();
        }

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        RestaurantSettings defaults = new RestaurantSettings();
        defaults.setRestaurant(restaurant);
        defaults.setTaxEnabled(true);
        defaults.setDefaultTaxRate(BigDecimal.ZERO.setScale(2));
        defaults.setGstMode("GST_EXCLUDED");
        defaults.setCgstRate(BigDecimal.ZERO.setScale(2));
        defaults.setSgstRate(BigDecimal.ZERO.setScale(2));
        defaults.setServiceChargeEnabled(false);
        defaults.setDefaultServiceChargeRate(BigDecimal.ZERO.setScale(2));
        defaults.setCurrency("INR");
        defaults.setTimezone("Asia/Kolkata");
        defaults.setReceiptHeader(restaurant.getName());
        defaults.setReceiptFooter("Thank you for visiting!");
        defaults.setInvoicePrefix("INV");
        defaults.setAutoAcceptOrders(false);
        defaults.setKitchenEnabled(true);

        return settingsRepository.save(defaults);
    }

    private RestaurantSettingsResponse mapToResponse(RestaurantSettings s) {
        BigDecimal defaultTaxRate = s.getDefaultTaxRate();
        BigDecimal cgstRate = s.getCgstRate();
        BigDecimal sgstRate = s.getSgstRate();

        if (defaultTaxRate != null) {
            defaultTaxRate = defaultTaxRate.setScale(2, RoundingMode.HALF_UP);
            if (cgstRate == null || sgstRate == null) {
                cgstRate = defaultTaxRate.divide(BigDecimal.valueOf(2.0), 2, RoundingMode.HALF_UP);
                sgstRate = defaultTaxRate.subtract(cgstRate).setScale(2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal totalGstRate = defaultTaxRate;
        if (totalGstRate == null && cgstRate != null && sgstRate != null) {
            totalGstRate = cgstRate.add(sgstRate).setScale(2, RoundingMode.HALF_UP);
        }

        String gstMode = s.getGstMode() != null ? s.getGstMode() : "GST_EXCLUDED";

        return RestaurantSettingsResponse.builder()
                .id(s.getId())
                .restaurantId(s.getRestaurant() != null ? s.getRestaurant().getId() : null)
                .taxEnabled(s.getTaxEnabled())
                .defaultTaxRate(defaultTaxRate)
                .totalGstRate(totalGstRate)
                .gstMode(gstMode)
                .cgstRate(cgstRate)
                .sgstRate(sgstRate)
                .serviceChargeEnabled(s.getServiceChargeEnabled())
                .defaultServiceChargeRate(s.getDefaultServiceChargeRate())
                .currency(s.getCurrency())
                .timezone(s.getTimezone())
                .receiptHeader(s.getReceiptHeader())
                .receiptFooter(s.getReceiptFooter())
                .invoicePrefix(s.getInvoicePrefix())
                .autoAcceptOrders(s.getAutoAcceptOrders())
                .kitchenEnabled(s.getKitchenEnabled() != null ? s.getKitchenEnabled() : true)
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
