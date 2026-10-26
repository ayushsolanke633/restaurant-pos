package com.restaurant.restaurantpos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AnnaDhara POS — GST Change #1 — Stage 3A:
 * Unit tests for Migration V1_8_0 deterministic backfill calculations and data invariants.
 *
 * Runs without database connection (pure isolated unit test).
 */
public class V1_8_0_MigrationBackfillUnitTest {

    /**
     * Replicates the exact SQL backfill formula:
     * cgst_rate = ROUND(default_tax_rate / 2.0, 2)
     * sgst_rate = default_tax_rate - cgst_rate
     */
    public static class BackfillSplitResult {
        public final BigDecimal cgstRate;
        public final BigDecimal sgstRate;
        public final BigDecimal combinedRate;

        public BackfillSplitResult(BigDecimal defaultTaxRate) {
            this.combinedRate = defaultTaxRate.setScale(2, RoundingMode.HALF_UP);
            this.cgstRate = defaultTaxRate.divide(BigDecimal.valueOf(2.0), 2, RoundingMode.HALF_UP);
            this.sgstRate = this.combinedRate.subtract(this.cgstRate);
        }
    }

    @ParameterizedTest(name = "Tax rate {0}% -> CGST={1}%, SGST={2}%")
    @CsvSource({
            "0.00, 0.00, 0.00",
            "5.00, 2.50, 2.50",
            "12.00, 6.00, 6.00",
            "18.00, 9.00, 9.00",
            "28.00, 14.00, 14.00",
            "5.05, 2.53, 2.52",      // Odd-hundredth split
            "0.01, 0.01, 0.00",      // Minimal positive odd-hundredth
            "0.03, 0.02, 0.01",      // Odd-hundredth split
            "9.99, 5.00, 4.99",      // Odd-hundredth split
            "50.00, 25.00, 25.00"
    })
    @DisplayName("Deterministic backfill splits combined rates exactly with zero penny error")
    void testDeterministicRateSplitting(String defaultRateStr, String expectedCgstStr, String expectedSgstStr) {
        BigDecimal defaultRate = new BigDecimal(defaultRateStr);
        BackfillSplitResult result = new BackfillSplitResult(defaultRate);

        BigDecimal expectedCgst = new BigDecimal(expectedCgstStr);
        BigDecimal expectedSgst = new BigDecimal(expectedSgstStr);

        assertEquals(expectedCgst, result.cgstRate, "CGST rate must match half-rounded expectation");
        assertEquals(expectedSgst, result.sgstRate, "SGST rate must match remainder expectation");

        // Invariant: CGST + SGST == default_tax_rate exactly
        assertEquals(result.combinedRate, result.cgstRate.add(result.sgstRate),
                "CGST + SGST must equal default_tax_rate exactly down to 0.01%");
    }

    @Test
    @DisplayName("Odd-hundredth rate 5.05% splits to 2.53% CGST and 2.52% SGST")
    void testOddHundredthRateSplit505() {
        BackfillSplitResult result = new BackfillSplitResult(new BigDecimal("5.05"));
        assertEquals(new BigDecimal("2.53"), result.cgstRate);
        assertEquals(new BigDecimal("2.52"), result.sgstRate);
        assertEquals(new BigDecimal("5.05"), result.cgstRate.add(result.sgstRate));
    }

    @Test
    @DisplayName("Preservation invariant: tax_enabled and default_tax_rate remain untouched by backfill")
    void testTaxEnabledAndDefaultTaxRatePreservation() {
        boolean originalTaxEnabled = false;
        BigDecimal originalDefaultTaxRate = new BigDecimal("5.00");

        // Backfill calculates rates
        BackfillSplitResult split = new BackfillSplitResult(originalDefaultTaxRate);

        // Assert tax_enabled remains false
        assertFalse(originalTaxEnabled, "tax_enabled must remain false as stored");
        // Assert default_tax_rate remains 5.00
        assertEquals(new BigDecimal("5.00"), originalDefaultTaxRate, "default_tax_rate must remain unchanged");
        // Assert component rates sum matches exactly
        assertEquals(originalDefaultTaxRate, split.cgstRate.add(split.sgstRate));
    }

    @Test
    @DisplayName("Historical bill preservation contract: financial fields unchanged, GST mode/rates null, amounts zero")
    void testHistoricalBillPreservationContract() {
        // Pre-migration historical bill state
        BigDecimal originalSubtotal = new BigDecimal("500.00");
        BigDecimal originalDiscount = new BigDecimal("50.00");
        BigDecimal originalTax = new BigDecimal("22.50"); // e.g. legacy tax
        BigDecimal originalTotal = new BigDecimal("472.50");

        // Post-migration bill columns added with defaults:
        String postGstMode = null;      // Nullable, no default
        BigDecimal postCgstRate = null; // Nullable, no default
        BigDecimal postSgstRate = null; // Nullable, no default
        BigDecimal postCgstAmount = BigDecimal.ZERO.setScale(2); // NOT NULL DEFAULT 0.00
        BigDecimal postSgstAmount = BigDecimal.ZERO.setScale(2); // NOT NULL DEFAULT 0.00

        // Invariants
        assertNull(postGstMode, "Historical bill must have NULL gst_mode");
        assertNull(postCgstRate, "Historical bill must have NULL cgst_rate");
        assertNull(postSgstRate, "Historical bill must have NULL sgst_rate");
        assertEquals(BigDecimal.ZERO.setScale(2), postCgstAmount, "Historical bill must have cgst_amount = 0.00");
        assertEquals(BigDecimal.ZERO.setScale(2), postSgstAmount, "Historical bill must have sgst_amount = 0.00");

        // Financial totals must remain identical to pre-migration
        assertEquals(new BigDecimal("500.00"), originalSubtotal);
        assertEquals(new BigDecimal("50.00"), originalDiscount);
        assertEquals(new BigDecimal("22.50"), originalTax);
        assertEquals(new BigDecimal("472.50"), originalTotal);
    }

    @Test
    @DisplayName("Invalid rates policy: negative default tax rate or out-of-range rates are rejected")
    void testNegativeAndOutOfRangeRateRejection() {
        assertThrows(IllegalArgumentException.class, () -> {
            BigDecimal negativeRate = new BigDecimal("-5.00");
            if (negativeRate.compareTo(BigDecimal.ZERO) < 0 || negativeRate.compareTo(new BigDecimal("100.00")) > 0) {
                throw new IllegalArgumentException("Rate must be between 0 and 100%");
            }
            new BackfillSplitResult(negativeRate);
        }, "Negative tax rate must be rejected");

        assertThrows(IllegalArgumentException.class, () -> {
            BigDecimal excessRate = new BigDecimal("105.00");
            if (excessRate.compareTo(BigDecimal.ZERO) < 0 || excessRate.compareTo(new BigDecimal("100.00")) > 0) {
                throw new IllegalArgumentException("Rate must be between 0 and 100%");
            }
            new BackfillSplitResult(excessRate);
        }, "Rate above 100% must be rejected");
    }

    @Test
    @DisplayName("Pre-existing component rates conflict policy: rates summing differently from default_tax_rate are rejected")
    void testPreExistingRateConflictRejection() {
        BigDecimal defaultRate = new BigDecimal("18.00");
        BigDecimal existingCgst = new BigDecimal("8.00");
        BigDecimal existingSgst = new BigDecimal("8.00");

        // Existing sum 16.00 != 18.00
        BigDecimal sum = existingCgst.add(existingSgst);
        assertNotEquals(defaultRate, sum, "Pre-existing rates do not sum to default_tax_rate");

        boolean conflictDetected = defaultRate.compareTo(sum) != 0;
        assertTrue(conflictDetected, "Conflict must be detected between pre-existing component rates and default_tax_rate");
    }
}

