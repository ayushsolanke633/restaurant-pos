package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.dto.RestaurantSettingsRequest;
import com.restaurant.restaurantpos.dto.RestaurantSettingsResponse;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RestaurantSettings;
import com.restaurant.restaurantpos.repository.RestaurantRepository;
import com.restaurant.restaurantpos.repository.RestaurantSettingsRepository;
import com.restaurant.restaurantpos.security.SecurityUtils;
import com.restaurant.restaurantpos.service.RestaurantSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class Stage3D1RestaurantGstSettingsUnitTest {

    @Mock
    private RestaurantSettingsRepository settingsRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private RestaurantSettingsService settingsService;

    private Restaurant restaurant;
    private RestaurantSettings existingSettings;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Dhaba");

        existingSettings = new RestaurantSettings();
        existingSettings.setId(10L);
        existingSettings.setRestaurant(restaurant);
        existingSettings.setTaxEnabled(true);
        existingSettings.setDefaultTaxRate(new BigDecimal("0.00"));
        existingSettings.setGstMode("GST_EXCLUDED");
        existingSettings.setCgstRate(new BigDecimal("0.00"));
        existingSettings.setSgstRate(new BigDecimal("0.00"));

        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(existingSettings));
        when(settingsRepository.save(any(RestaurantSettings.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Zero rate: defaultTaxRate = 0.00 results in equal zero halves (0.00 + 0.00)")
    void testZeroRate() {
        RestaurantSettingsRequest req = RestaurantSettingsRequest.builder()
                .defaultTaxRate(new BigDecimal("0.00"))
                .build();

        RestaurantSettingsResponse res = settingsService.updateSettings(req);

        assertEquals(new BigDecimal("0.00"), res.getDefaultTaxRate());
        assertEquals(new BigDecimal("0.00"), res.getTotalGstRate());
        assertEquals(new BigDecimal("0.00"), res.getCgstRate());
        assertEquals(new BigDecimal("0.00"), res.getSgstRate());
        assertEquals(res.getTotalGstRate(), res.getCgstRate().add(res.getSgstRate()));
        assertEquals(res.getDefaultTaxRate(), res.getTotalGstRate());
    }

    @Test
    @DisplayName("Normal rate: defaultTaxRate = 5.00 results in equal halves (2.50 + 2.50)")
    void testNormalRate500() {
        RestaurantSettingsRequest req = RestaurantSettingsRequest.builder()
                .defaultTaxRate(new BigDecimal("5.00"))
                .build();

        RestaurantSettingsResponse res = settingsService.updateSettings(req);

        assertEquals(new BigDecimal("5.00"), res.getDefaultTaxRate());
        assertEquals(new BigDecimal("5.00"), res.getTotalGstRate());
        assertEquals(new BigDecimal("2.50"), res.getCgstRate());
        assertEquals(new BigDecimal("2.50"), res.getSgstRate());
        assertEquals(res.getDefaultTaxRate(), res.getCgstRate().add(res.getSgstRate()));
    }

    @Test
    @DisplayName("Odd-hundredth rate: defaultTaxRate = 5.05 results deterministically in 2.53 CGST and 2.52 SGST")
    void testOddHundredthRate505() {
        RestaurantSettingsRequest req = RestaurantSettingsRequest.builder()
                .defaultTaxRate(new BigDecimal("5.05"))
                .build();

        RestaurantSettingsResponse res = settingsService.updateSettings(req);

        assertEquals(new BigDecimal("5.05"), res.getDefaultTaxRate());
        assertEquals(new BigDecimal("5.05"), res.getTotalGstRate());
        assertEquals(new BigDecimal("2.53"), res.getCgstRate());
        assertEquals(new BigDecimal("2.52"), res.getSgstRate());
        assertEquals(new BigDecimal("5.05"), res.getCgstRate().add(res.getSgstRate()));
        assertEquals(res.getDefaultTaxRate(), res.getTotalGstRate());
    }

    @ParameterizedTest(name = "Total GST {0}% -> CGST {1}% + SGST {2}%")
    @CsvSource({
            "0.01, 0.01, 0.00",
            "0.03, 0.02, 0.01",
            "5.05, 2.53, 2.52",
            "9.99, 5.00, 4.99",
            "12.00, 6.00, 6.00",
            "18.00, 9.00, 9.00",
            "28.00, 14.00, 14.00",
            "100.00, 50.00, 50.00"
    })
    @DisplayName("Deterministic rate splitting across standard and odd-hundredth rates")
    void testDeterministicRateSplitting(String totalRateStr, String expectedCgstStr, String expectedSgstStr) {
        RestaurantSettingsRequest req = RestaurantSettingsRequest.builder()
                .defaultTaxRate(new BigDecimal(totalRateStr))
                .build();

        RestaurantSettingsResponse res = settingsService.updateSettings(req);

        BigDecimal expectedTotal = new BigDecimal(totalRateStr);
        BigDecimal expectedCgst = new BigDecimal(expectedCgstStr);
        BigDecimal expectedSgst = new BigDecimal(expectedSgstStr);

        assertEquals(expectedTotal, res.getDefaultTaxRate());
        assertEquals(expectedTotal, res.getTotalGstRate());
        assertEquals(expectedCgst, res.getCgstRate());
        assertEquals(expectedSgst, res.getSgstRate());
        assertEquals(expectedTotal, res.getCgstRate().add(res.getSgstRate()));
    }

    @Test
    @DisplayName("Disabled tax: taxEnabled = false preserves saved defaultTaxRate, rates, and gstMode")
    void testDisabledTaxPreservesSettings() {
        existingSettings.setDefaultTaxRate(new BigDecimal("5.00"));
        existingSettings.setCgstRate(new BigDecimal("2.50"));
        existingSettings.setSgstRate(new BigDecimal("2.50"));
        existingSettings.setGstMode("GST_INCLUDED");
        existingSettings.setTaxEnabled(true);

        // Disabling tax without modifying rates or mode
        RestaurantSettingsRequest req = RestaurantSettingsRequest.builder()
                .taxEnabled(false)
                .build();

        RestaurantSettingsResponse res = settingsService.updateSettings(req);

        assertFalse(res.getTaxEnabled(), "taxEnabled must be false");
        assertEquals(new BigDecimal("5.00"), res.getDefaultTaxRate(), "defaultTaxRate must not be wiped out");
        assertEquals(new BigDecimal("5.00"), res.getTotalGstRate());
        assertEquals(new BigDecimal("2.50"), res.getCgstRate(), "cgstRate must be preserved");
        assertEquals(new BigDecimal("2.50"), res.getSgstRate(), "sgstRate must be preserved");
        assertEquals("GST_INCLUDED", res.getGstMode(), "gstMode must be preserved");
    }

    @Test
    @DisplayName("Simultaneous tax disable and rate update: updates rates while taxEnabled is false")
    void testSimultaneousTaxDisableAndRateUpdate() {
        RestaurantSettingsRequest req = RestaurantSettingsRequest.builder()
                .taxEnabled(false)
                .defaultTaxRate(new BigDecimal("18.00"))
                .gstMode("GST_EXCLUDED")
                .build();

        RestaurantSettingsResponse res = settingsService.updateSettings(req);

        assertFalse(res.getTaxEnabled());
        assertEquals(new BigDecimal("18.00"), res.getDefaultTaxRate());
        assertEquals(new BigDecimal("18.00"), res.getTotalGstRate());
        assertEquals(new BigDecimal("9.00"), res.getCgstRate());
        assertEquals(new BigDecimal("9.00"), res.getSgstRate());
        assertEquals("GST_EXCLUDED", res.getGstMode());
    }

    @Test
    @DisplayName("GST mode: supports GST_EXCLUDED and GST_INCLUDED (case-insensitive and trimmed)")
    void testGstModeSupport() {
        // GST_INCLUDED
        RestaurantSettingsRequest reqIncluded = RestaurantSettingsRequest.builder()
                .gstMode("GST_INCLUDED")
                .build();
        RestaurantSettingsResponse resIncluded = settingsService.updateSettings(reqIncluded);
        assertEquals("GST_INCLUDED", resIncluded.getGstMode());

        // Case-insensitive / trimmed
        RestaurantSettingsRequest reqLowercase = RestaurantSettingsRequest.builder()
                .gstMode("  gst_excluded  ")
                .build();
        RestaurantSettingsResponse resLowercase = settingsService.updateSettings(reqLowercase);
        assertEquals("GST_EXCLUDED", resLowercase.getGstMode());
    }

    @Test
    @DisplayName("Invalid GST mode: rejects unsupported mode strings")
    void testInvalidGstModeRejected() {
        RestaurantSettingsRequest reqInvalid = RestaurantSettingsRequest.builder()
                .gstMode("VAT")
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> settingsService.updateSettings(reqInvalid));
        assertTrue(ex.getMessage().contains("Invalid GST mode"), "Must indicate invalid GST mode");

        RestaurantSettingsRequest reqEmpty = RestaurantSettingsRequest.builder()
                .gstMode("   ")
                .build();

        RuntimeException exEmpty = assertThrows(RuntimeException.class, () -> settingsService.updateSettings(reqEmpty));
        assertTrue(exEmpty.getMessage().contains("Invalid GST mode"));
    }

    @Test
    @DisplayName("Invalid rate: rejects negative tax rates")
    void testNegativeRateRejected() {
        RestaurantSettingsRequest req = RestaurantSettingsRequest.builder()
                .defaultTaxRate(new BigDecimal("-0.01"))
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> settingsService.updateSettings(req));
        assertTrue(ex.getMessage().contains("Tax rate must be between 0 and 100"));
    }

    @Test
    @DisplayName("Invalid rate: rejects rates exceeding 100%")
    void testRateExceeding100Rejected() {
        RestaurantSettingsRequest req = RestaurantSettingsRequest.builder()
                .defaultTaxRate(new BigDecimal("100.01"))
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> settingsService.updateSettings(req));
        assertTrue(ex.getMessage().contains("Tax rate must be between 0 and 100"));
    }

    @Test
    @DisplayName("Legacy uninitialized existing settings: normalizes unpopulated cgstRate/sgstRate upon update")
    void testUninitializedExistingSettingsNormalized() {
        existingSettings.setDefaultTaxRate(new BigDecimal("12.00"));
        existingSettings.setCgstRate(null);
        existingSettings.setSgstRate(null);
        existingSettings.setGstMode(null);

        // Update unrelated property (e.g. currency)
        RestaurantSettingsRequest req = RestaurantSettingsRequest.builder()
                .currency("INR")
                .build();

        RestaurantSettingsResponse res = settingsService.updateSettings(req);

        assertEquals(new BigDecimal("12.00"), res.getDefaultTaxRate());
        assertEquals(new BigDecimal("12.00"), res.getTotalGstRate());
        assertEquals(new BigDecimal("6.00"), res.getCgstRate());
        assertEquals(new BigDecimal("6.00"), res.getSgstRate());
        assertEquals("GST_EXCLUDED", res.getGstMode());
    }

    @Test
    @DisplayName("getSettings: returns consistent totalGstRate and split rates for current settings")
    void testGetSettingsConsistency() {
        existingSettings.setDefaultTaxRate(new BigDecimal("5.05"));
        existingSettings.setCgstRate(new BigDecimal("2.53"));
        existingSettings.setSgstRate(new BigDecimal("2.52"));
        existingSettings.setGstMode("GST_EXCLUDED");

        RestaurantSettingsResponse res = settingsService.getSettings();

        assertEquals(new BigDecimal("5.05"), res.getDefaultTaxRate());
        assertEquals(new BigDecimal("5.05"), res.getTotalGstRate());
        assertEquals(new BigDecimal("2.53"), res.getCgstRate());
        assertEquals(new BigDecimal("2.52"), res.getSgstRate());
        assertEquals(res.getTotalGstRate(), res.getCgstRate().add(res.getSgstRate()));
    }
}
