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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class Step23SettingsTest {


    @Mock
    private RestaurantSettingsRepository settingsRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private RestaurantSettingsService settingsService;

    private Restaurant restaurantA;
    private Restaurant restaurantB;

    @BeforeEach
    void setUp() {
        restaurantA = new Restaurant();
        restaurantA.setId(1L);
        restaurantA.setName("Royal Dine");

        restaurantB = new Restaurant();
        restaurantB.setId(2L);
        restaurantB.setName("Ocean Breeze");

        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");
        when(securityUtils.isAdmin()).thenReturn(true);
    }


    // 1-10. GET /api/settings creates lazy default settings if none exist
    @Test
    void testGetSettingsCreatesLazyDefaults() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.empty());
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurantA));

        when(settingsRepository.save(any(RestaurantSettings.class))).thenAnswer(invocation -> {
            RestaurantSettings s = invocation.getArgument(0);
            s.setId(100L);
            return s;
        });

        RestaurantSettingsResponse res = settingsService.getSettings();

        assertNotNull(res);
        assertEquals(100L, res.getId());
        assertEquals(1L, res.getRestaurantId());
        assertTrue(res.getTaxEnabled());
        assertEquals(new BigDecimal("0.00"), res.getDefaultTaxRate());
        assertFalse(res.getServiceChargeEnabled());
        assertEquals(new BigDecimal("0.00"), res.getDefaultServiceChargeRate());
        assertEquals("INR", res.getCurrency());
        assertEquals("Asia/Kolkata", res.getTimezone());
        assertEquals("Royal Dine", res.getReceiptHeader());
        assertEquals("Thank you for visiting!", res.getReceiptFooter());
        assertEquals("INV", res.getInvoicePrefix());
        assertFalse(res.getAutoAcceptOrders());
        assertTrue(res.getKitchenEnabled());
    }

    // GET /api/settings returns existing settings if present
    @Test
    void testGetSettingsReturnsExisting() {
        RestaurantSettings existing = new RestaurantSettings();
        existing.setId(5L);
        existing.setRestaurant(restaurantA);
        existing.setCurrency("USD");
        existing.setTimezone("America/New_York");
        existing.setTaxEnabled(true);
        existing.setDefaultTaxRate(new BigDecimal("8.50"));
        existing.setServiceChargeEnabled(true);
        existing.setDefaultServiceChargeRate(new BigDecimal("10.00"));
        existing.setReceiptHeader("Header");
        existing.setReceiptFooter("Footer");
        existing.setInvoicePrefix("BILL");
        existing.setAutoAcceptOrders(true);

        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(existing));

        RestaurantSettingsResponse res = settingsService.getSettings();

        assertEquals(5L, res.getId());
        assertEquals("USD", res.getCurrency());
        assertEquals("America/New_York", res.getTimezone());
        assertEquals(new BigDecimal("8.50"), res.getDefaultTaxRate());
        assertEquals(new BigDecimal("10.00"), res.getDefaultServiceChargeRate());
        assertEquals("BILL", res.getInvoicePrefix());
        assertTrue(res.getAutoAcceptOrders());
    }

    // 11. PUT /api/settings updates existing settings successfully
    @Test
    void testUpdateSettingsSuccess() {
        RestaurantSettings existing = new RestaurantSettings();
        existing.setId(10L);
        existing.setRestaurant(restaurantA);
        existing.setCurrency("INR");
        existing.setTimezone("Asia/Kolkata");
        existing.setTaxEnabled(true);
        existing.setDefaultTaxRate(new BigDecimal("0.00"));
        existing.setServiceChargeEnabled(false);
        existing.setDefaultServiceChargeRate(new BigDecimal("0.00"));
        existing.setReceiptHeader("Old Header");
        existing.setReceiptFooter("Old Footer");
        existing.setInvoicePrefix("INV");
        existing.setAutoAcceptOrders(false);

        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(existing));
        when(settingsRepository.save(any(RestaurantSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RestaurantSettingsRequest req = RestaurantSettingsRequest.builder()
                .taxEnabled(true)
                .defaultTaxRate(new BigDecimal("5.00"))
                .serviceChargeEnabled(true)
                .defaultServiceChargeRate(new BigDecimal("2.50"))
                .currency("  usd ")
                .timezone("America/New_York")
                .invoicePrefix("  bill ")
                .receiptHeader("New Header")
                .receiptFooter("New Footer")
                .autoAcceptOrders(true)
                .kitchenEnabled(true)
                .build();

        RestaurantSettingsResponse res = settingsService.updateSettings(req);

        assertEquals("USD", res.getCurrency());
        assertEquals("America/New_York", res.getTimezone());
        assertEquals(new BigDecimal("5.00"), res.getDefaultTaxRate());
        assertTrue(res.getServiceChargeEnabled());
        assertEquals(new BigDecimal("2.50"), res.getDefaultServiceChargeRate());
        assertEquals("BILL", res.getInvoicePrefix());
        assertEquals("New Header", res.getReceiptHeader());
        assertEquals("New Footer", res.getReceiptFooter());
        assertTrue(res.getAutoAcceptOrders());
        assertTrue(res.getKitchenEnabled());
    }

    @Test
    void testDisablingKitchenTurnsAutoAcceptOff() {
        RestaurantSettings existing = new RestaurantSettings();
        existing.setId(10L);
        existing.setRestaurant(restaurantA);
        existing.setKitchenEnabled(true);
        existing.setAutoAcceptOrders(true);

        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(existing));
        when(settingsRepository.save(any(RestaurantSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        RestaurantSettingsRequest disableReq = RestaurantSettingsRequest.builder()
                .kitchenEnabled(false)
                .build();

        RestaurantSettingsResponse res = settingsService.updateSettings(disableReq);
        assertFalse(res.getKitchenEnabled());
        assertFalse(res.getAutoAcceptOrders());
        assertFalse(existing.getKitchenEnabled());
        assertFalse(existing.getAutoAcceptOrders());
    }

    @Test
    void testCannotEnableAutoAcceptWhenKitchenDisabled() {
        RestaurantSettings existing = new RestaurantSettings();
        existing.setId(10L);
        existing.setRestaurant(restaurantA);
        existing.setKitchenEnabled(false);
        existing.setAutoAcceptOrders(false);

        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(existing));

        // Attempt 1: Enable auto-accept while kitchen is already disabled
        RestaurantSettingsRequest enableAutoAcceptReq = RestaurantSettingsRequest.builder()
                .autoAcceptOrders(true)
                .build();

        RuntimeException ex1 = assertThrows(RuntimeException.class, () -> settingsService.updateSettings(enableAutoAcceptReq));
        assertTrue(ex1.getMessage().contains("Kitchen Display System (KDS) must be enabled"));

        // Attempt 2: Simultaneously disable kitchen and enable auto-accept
        RestaurantSettingsRequest invalidComboReq = RestaurantSettingsRequest.builder()
                .kitchenEnabled(false)
                .autoAcceptOrders(true)
                .build();

        RuntimeException ex2 = assertThrows(RuntimeException.class, () -> settingsService.updateSettings(invalidComboReq));
        assertTrue(ex2.getMessage().contains("Kitchen Display System (KDS) must be enabled"));
    }

    @Test
    void testReEnablingKitchenDoesNotTurnAutoAcceptOn() {
        RestaurantSettings existing = new RestaurantSettings();
        existing.setId(10L);
        existing.setRestaurant(restaurantA);
        existing.setKitchenEnabled(false);
        existing.setAutoAcceptOrders(false);

        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(existing));
        when(settingsRepository.save(any(RestaurantSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        RestaurantSettingsRequest enableKitchenReq = RestaurantSettingsRequest.builder()
                .kitchenEnabled(true)
                .build();

        RestaurantSettingsResponse res = settingsService.updateSettings(enableKitchenReq);
        assertTrue(res.getKitchenEnabled());
        assertFalse(res.getAutoAcceptOrders());
        assertTrue(existing.getKitchenEnabled());
        assertFalse(existing.getAutoAcceptOrders());
    }

    @Test
    void testKitchenModeSwitchingBothDirections() {
        RestaurantSettings existing = new RestaurantSettings();
        existing.setId(20L);
        existing.setRestaurant(restaurantA);
        existing.setKitchenEnabled(true);

        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(existing));
        when(settingsRepository.save(any(RestaurantSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Switch to Kitchen-Free (false)
        RestaurantSettingsRequest disableReq = RestaurantSettingsRequest.builder()
                .kitchenEnabled(false)
                .build();
        RestaurantSettingsResponse disabledRes = settingsService.updateSettings(disableReq);
        assertFalse(disabledRes.getKitchenEnabled());
        assertFalse(existing.getKitchenEnabled());

        // Switch back to Kitchen-Enabled (true)
        RestaurantSettingsRequest enableReq = RestaurantSettingsRequest.builder()
                .kitchenEnabled(true)
                .build();
        RestaurantSettingsResponse enabledRes = settingsService.updateSettings(enableReq);
        assertTrue(enabledRes.getKitchenEnabled());
        assertTrue(existing.getKitchenEnabled());
    }

    // 13 & 14. Tax rate bounds checking (0 to 100)
    @Test
    void testTaxRateValidation() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(new RestaurantSettings()));

        RestaurantSettingsRequest reqLow = RestaurantSettingsRequest.builder()
                .defaultTaxRate(new BigDecimal("-1.00"))
                .build();

        RuntimeException exLow = assertThrows(RuntimeException.class, () -> settingsService.updateSettings(reqLow));
        assertTrue(exLow.getMessage().contains("must be between 0 and 100"));

        RestaurantSettingsRequest reqHigh = RestaurantSettingsRequest.builder()
                .defaultTaxRate(new BigDecimal("100.01"))
                .build();

        RuntimeException exHigh = assertThrows(RuntimeException.class, () -> settingsService.updateSettings(reqHigh));
        assertTrue(exHigh.getMessage().contains("must be between 0 and 100"));
    }

    // 16 & 17. Service charge rate bounds checking (0 to 100)
    @Test
    void testServiceChargeRateValidation() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(new RestaurantSettings()));

        RestaurantSettingsRequest reqLow = RestaurantSettingsRequest.builder()
                .defaultServiceChargeRate(new BigDecimal("-0.01"))
                .build();

        RuntimeException exLow = assertThrows(RuntimeException.class, () -> settingsService.updateSettings(reqLow));
        assertTrue(exLow.getMessage().contains("must be between 0 and 100"));

        RestaurantSettingsRequest reqHigh = RestaurantSettingsRequest.builder()
                .defaultServiceChargeRate(new BigDecimal("105.00"))
                .build();

        RuntimeException exHigh = assertThrows(RuntimeException.class, () -> settingsService.updateSettings(reqHigh));
        assertTrue(exHigh.getMessage().contains("must be between 0 and 100"));
    }

    // 18 & 19. Currency validation
    @Test
    void testCurrencyValidation() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(new RestaurantSettings()));

        RestaurantSettingsRequest reqInvalid = RestaurantSettingsRequest.builder()
                .currency("USDD")
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> settingsService.updateSettings(reqInvalid));
        assertTrue(ex.getMessage().contains("3-character ISO currency code"));
    }

    // 20. Timezone validation
    @Test
    void testTimezoneValidation() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(new RestaurantSettings()));

        RestaurantSettingsRequest reqInvalid = RestaurantSettingsRequest.builder()
                .timezone("Invalid/Timezone")
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> settingsService.updateSettings(reqInvalid));
        assertTrue(ex.getMessage().contains("Invalid timezone"));
    }

    // 21. Invoice prefix validation
    @Test
    void testInvoicePrefixValidation() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(new RestaurantSettings()));

        RestaurantSettingsRequest reqInvalid = RestaurantSettingsRequest.builder()
                .invoicePrefix("VERY_LONG_INVOICE_PREFIX_EXCEEDING_TWENTY_CHARACTERS")
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> settingsService.updateSettings(reqInvalid));
        assertTrue(ex.getMessage().contains("must be 1 to 20 characters"));
    }

    // 22 & 23. Receipt header/footer length validation
    @Test
    void testReceiptHeaderFooterLengthValidation() {
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(new RestaurantSettings()));

        String longText = "A".repeat(501);

        RestaurantSettingsRequest reqHeader = RestaurantSettingsRequest.builder()
                .receiptHeader(longText)
                .build();

        RuntimeException exHeader = assertThrows(RuntimeException.class, () -> settingsService.updateSettings(reqHeader));
        assertTrue(exHeader.getMessage().contains("cannot exceed 500 characters"));

        RestaurantSettingsRequest reqFooter = RestaurantSettingsRequest.builder()
                .receiptFooter(longText)
                .build();

        RuntimeException exFooter = assertThrows(RuntimeException.class, () -> settingsService.updateSettings(reqFooter));
        assertTrue(exFooter.getMessage().contains("cannot exceed 500 characters"));
    }

    // 24 & 25. KITCHEN can GET settings, but cannot PUT settings. WAITER gets AccessDenied on both.
    @Test
    void testKitchenCanGetButNotUpdateSettings() {
        when(securityUtils.getCurrentUserRole()).thenReturn("KITCHEN");
        when(securityUtils.getCurrentRestaurantId()).thenReturn(1L);
        when(settingsRepository.findByRestaurantId(1L)).thenReturn(Optional.of(new RestaurantSettings()));

        assertDoesNotThrow(() -> settingsService.getSettings());

        when(securityUtils.isAdmin()).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> settingsService.updateSettings(new RestaurantSettingsRequest()));
    }

    @Test
    void testWaiterReceivesAccessDeniedOnSettings() {
        when(securityUtils.getCurrentUserRole()).thenReturn("WAITER");
        when(securityUtils.isAdmin()).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> settingsService.getSettings());
        assertThrows(AccessDeniedException.class, () -> settingsService.updateSettings(new RestaurantSettingsRequest()));
    }

    // 26. Multi-tenant isolation test
    @Test
    void testTenantIsolation() {
        when(securityUtils.getCurrentUserRole()).thenReturn("ADMIN");
        when(securityUtils.isAdmin()).thenReturn(true);
        when(securityUtils.getCurrentRestaurantId()).thenReturn(2L); // Logged in as Restaurant B
        when(settingsRepository.findByRestaurantId(2L)).thenReturn(Optional.empty());
        when(restaurantRepository.findById(2L)).thenReturn(Optional.of(restaurantB));
        when(settingsRepository.save(any(RestaurantSettings.class))).thenAnswer(i -> i.getArgument(0));

        RestaurantSettingsResponse res = settingsService.getSettings();

        assertEquals(2L, res.getRestaurantId());
        assertEquals("Ocean Breeze", res.getReceiptHeader());

        // Verify findByRestaurantId was called with 2L, never 1L
        verify(settingsRepository).findByRestaurantId(2L);
        verify(settingsRepository, never()).findByRestaurantId(1L);
    }
}

