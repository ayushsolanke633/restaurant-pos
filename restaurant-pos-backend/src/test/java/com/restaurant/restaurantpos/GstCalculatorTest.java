package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.util.GstCalculator;
import com.restaurant.restaurantpos.util.GstCalculator.GstCalculationResult;
import com.restaurant.restaurantpos.util.GstCalculator.GstMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused unit tests for standalone GstCalculator utility.
 * Purely in-memory, deterministic, no database connections.
 */
public class GstCalculatorTest {

    private static final BigDecimal BD_100 = new BigDecimal("100.00");
    private static final BigDecimal BD_ZERO = new BigDecimal("0.00");
    private static final BigDecimal RATE_2_5 = new BigDecimal("2.50");
    private static final BigDecimal RATE_9_0 = new BigDecimal("9.00");

    @Test
    @DisplayName("Mandatory Case 1: Excluded mode - subtotal 100.00, discount 0.00, rates 2.50% + 2.50%")
    void testGstExcludedBasic() {
        GstCalculationResult result = GstCalculator.calculate(
                BD_100,
                BD_ZERO,
                GstMode.GST_EXCLUDED,
                RATE_2_5,
                RATE_2_5,
                true
        );

        assertNotNull(result);
        assertEquals(0, new BigDecimal("100.00").compareTo(result.getTaxableAmount()), "Taxable amount must be 100.00");
        assertEquals(0, new BigDecimal("2.50").compareTo(result.getCgstAmount()), "CGST amount must be 2.50");
        assertEquals(0, new BigDecimal("2.50").compareTo(result.getSgstAmount()), "SGST amount must be 2.50");
        assertEquals(0, new BigDecimal("5.00").compareTo(result.getCombinedTaxAmount()), "Combined tax amount must be 5.00");
        assertEquals(0, new BigDecimal("105.00").compareTo(result.getTotalPayable()), "Total payable must be 105.00");
        assertEquals(GstMode.GST_EXCLUDED, result.getGstMode());
        assertTrue(result.isTaxEnabled());
    }

    @Test
    @DisplayName("Mandatory Case 2: Included mode - gross 100.00, discount 0.00, rates 2.50% + 2.50%")
    void testGstIncludedBasic() {
        GstCalculationResult result = GstCalculator.calculate(
                BD_100,
                BD_ZERO,
                GstMode.GST_INCLUDED,
                RATE_2_5,
                RATE_2_5,
                true
        );

        assertNotNull(result);
        assertEquals(0, new BigDecimal("95.24").compareTo(result.getTaxableAmount()), "Taxable base must be 95.24");
        assertEquals(0, new BigDecimal("4.76").compareTo(result.getCombinedTaxAmount()), "Combined tax must be 4.76");
        assertEquals(0, new BigDecimal("2.38").compareTo(result.getCgstAmount()), "CGST must be 2.38");
        assertEquals(0, new BigDecimal("2.38").compareTo(result.getSgstAmount()), "SGST must be 2.38");
        assertEquals(0, new BigDecimal("100.00").compareTo(result.getTotalPayable()), "Total payable must remain 100.00");
        assertEquals(0, result.getCgstAmount().add(result.getSgstAmount()).compareTo(result.getCombinedTaxAmount()),
                "CGST + SGST must exactly equal combined tax");
        assertEquals(0, result.getTaxableAmount().add(result.getCombinedTaxAmount()).compareTo(result.getTotalPayable()),
                "Taxable base + tax must exactly equal total payable");
    }

    @Test
    @DisplayName("Mandatory Case 3: Excluded mode with discount - subtotal 250.00, discount 50.00, rates 9.00% + 9.00%")
    void testGstExcludedWithDiscount() {
        GstCalculationResult result = GstCalculator.calculate(
                new BigDecimal("250.00"),
                new BigDecimal("50.00"),
                GstMode.GST_EXCLUDED,
                RATE_9_0,
                RATE_9_0,
                true
        );

        assertNotNull(result);
        assertEquals(0, new BigDecimal("200.00").compareTo(result.getTaxableAmount()), "Taxable amount must be 200.00");
        assertEquals(0, new BigDecimal("18.00").compareTo(result.getCgstAmount()), "CGST must be 18.00");
        assertEquals(0, new BigDecimal("18.00").compareTo(result.getSgstAmount()), "SGST must be 18.00");
        assertEquals(0, new BigDecimal("36.00").compareTo(result.getCombinedTaxAmount()), "Combined tax must be 36.00");
        assertEquals(0, new BigDecimal("236.00").compareTo(result.getTotalPayable()), "Total payable must be 236.00");
    }

    @Test
    @DisplayName("Mandatory Case 4a: Included mode with uneven paise - gross 33.33, rates 2.50% + 2.50%")
    void testGstIncludedUnevenPaise33_33() {
        GstCalculationResult result = GstCalculator.calculate(
                new BigDecimal("33.33"),
                BD_ZERO,
                GstMode.GST_INCLUDED,
                RATE_2_5,
                RATE_2_5,
                true
        );

        assertNotNull(result);
        // 33.33 / 1.05 = 31.742857... -> 31.74
        assertEquals(0, new BigDecimal("31.74").compareTo(result.getTaxableAmount()), "Taxable base must be 31.74");
        // 33.33 - 31.74 = 1.59
        assertEquals(0, new BigDecimal("1.59").compareTo(result.getCombinedTaxAmount()), "Combined tax must be 1.59");
        // 31.74 * 0.025 = 0.7935 -> 0.79
        assertEquals(0, new BigDecimal("0.79").compareTo(result.getCgstAmount()), "CGST must be 0.79");
        // 1.59 - 0.79 = 0.80
        assertEquals(0, new BigDecimal("0.80").compareTo(result.getSgstAmount()), "SGST must be 0.80");
        assertEquals(0, new BigDecimal("33.33").compareTo(result.getTotalPayable()), "Total payable must remain 33.33");
        assertEquals(0, result.getCgstAmount().add(result.getSgstAmount()).compareTo(result.getCombinedTaxAmount()),
                "CGST + SGST must exactly equal combined tax");
    }

    @Test
    @DisplayName("Mandatory Case 4b: Included mode with uneven paise - gross 99.99, rates 2.50% + 2.50%")
    void testGstIncludedUnevenPaise99_99() {
        GstCalculationResult result = GstCalculator.calculate(
                new BigDecimal("99.99"),
                BD_ZERO,
                GstMode.GST_INCLUDED,
                RATE_2_5,
                RATE_2_5,
                true
        );

        assertNotNull(result);
        // 99.99 / 1.05 = 95.22857... -> 95.23
        assertEquals(0, new BigDecimal("95.23").compareTo(result.getTaxableAmount()), "Taxable base must be 95.23");
        // 99.99 - 95.23 = 4.76
        assertEquals(0, new BigDecimal("4.76").compareTo(result.getCombinedTaxAmount()), "Combined tax must be 4.76");
        // 95.23 * 0.025 = 2.38075 -> 2.38
        assertEquals(0, new BigDecimal("2.38").compareTo(result.getCgstAmount()), "CGST must be 2.38");
        // 4.76 - 2.38 = 2.38
        assertEquals(0, new BigDecimal("2.38").compareTo(result.getSgstAmount()), "SGST must be 2.38");
        assertEquals(0, new BigDecimal("99.99").compareTo(result.getTotalPayable()), "Total payable must remain 99.99");
        assertEquals(0, result.getCgstAmount().add(result.getSgstAmount()).compareTo(result.getCombinedTaxAmount()),
                "CGST + SGST must exactly equal combined tax");
    }

    @Test
    @DisplayName("Mandatory Case 5: Zero rates and zero subtotal - both modes")
    void testZeroRatesAndZeroSubtotal() {
        // Zero subtotal, zero rates in EXCLUDED
        GstCalculationResult excludedZero = GstCalculator.calculate(
                BD_ZERO,
                BD_ZERO,
                GstMode.GST_EXCLUDED,
                BD_ZERO,
                BD_ZERO,
                true
        );
        assertEquals(0, BD_ZERO.compareTo(excludedZero.getTaxableAmount()));
        assertEquals(0, BD_ZERO.compareTo(excludedZero.getCgstAmount()));
        assertEquals(0, BD_ZERO.compareTo(excludedZero.getSgstAmount()));
        assertEquals(0, BD_ZERO.compareTo(excludedZero.getCombinedTaxAmount()));
        assertEquals(0, BD_ZERO.compareTo(excludedZero.getTotalPayable()));

        // Zero subtotal, zero rates in INCLUDED
        GstCalculationResult includedZero = GstCalculator.calculate(
                BD_ZERO,
                BD_ZERO,
                GstMode.GST_INCLUDED,
                BD_ZERO,
                BD_ZERO,
                true
        );
        assertEquals(0, BD_ZERO.compareTo(includedZero.getTaxableAmount()));
        assertEquals(0, BD_ZERO.compareTo(includedZero.getCgstAmount()));
        assertEquals(0, BD_ZERO.compareTo(includedZero.getSgstAmount()));
        assertEquals(0, BD_ZERO.compareTo(includedZero.getCombinedTaxAmount()));
        assertEquals(0, BD_ZERO.compareTo(includedZero.getTotalPayable()));

        // Non-zero subtotal with zero rates in INCLUDED (tests zero division protection)
        GstCalculationResult includedNonZeroRatesZero = GstCalculator.calculate(
                BD_100,
                BD_ZERO,
                GstMode.GST_INCLUDED,
                BD_ZERO,
                BD_ZERO,
                true
        );
        assertEquals(0, BD_100.compareTo(includedNonZeroRatesZero.getTaxableAmount()));
        assertEquals(0, BD_ZERO.compareTo(includedNonZeroRatesZero.getCombinedTaxAmount()));
        assertEquals(0, BD_100.compareTo(includedNonZeroRatesZero.getTotalPayable()));
    }

    @Test
    @DisplayName("Mandatory Case 6: Tax disabled - returns zero tax and payable equals subtotal minus discount")
    void testTaxDisabled() {
        GstCalculationResult excludedDisabled = GstCalculator.calculate(
                BD_100,
                new BigDecimal("10.00"),
                GstMode.GST_EXCLUDED,
                RATE_2_5,
                RATE_2_5,
                false
        );
        assertEquals(0, new BigDecimal("90.00").compareTo(excludedDisabled.getTaxableAmount()));
        assertEquals(0, BD_ZERO.compareTo(excludedDisabled.getCgstAmount()));
        assertEquals(0, BD_ZERO.compareTo(excludedDisabled.getSgstAmount()));
        assertEquals(0, BD_ZERO.compareTo(excludedDisabled.getCombinedTaxAmount()));
        assertEquals(0, new BigDecimal("90.00").compareTo(excludedDisabled.getTotalPayable()));
        assertFalse(excludedDisabled.isTaxEnabled());

        GstCalculationResult includedDisabled = GstCalculator.calculate(
                BD_100,
                new BigDecimal("15.00"),
                GstMode.GST_INCLUDED,
                RATE_2_5,
                RATE_2_5,
                false
        );
        assertEquals(0, new BigDecimal("85.00").compareTo(includedDisabled.getTaxableAmount()));
        assertEquals(0, BD_ZERO.compareTo(includedDisabled.getCgstAmount()));
        assertEquals(0, BD_ZERO.compareTo(includedDisabled.getSgstAmount()));
        assertEquals(0, BD_ZERO.compareTo(includedDisabled.getCombinedTaxAmount()));
        assertEquals(0, new BigDecimal("85.00").compareTo(includedDisabled.getTotalPayable()));
        assertFalse(includedDisabled.isTaxEnabled());
    }

    @Test
    @DisplayName("Mandatory Case 7: Invalid inputs and unsupported modes are rejected")
    void testInvalidInputsRejected() {
        // Null inputs
        assertThrows(IllegalArgumentException.class, () ->
                GstCalculator.calculate(null, BD_ZERO, GstMode.GST_EXCLUDED, RATE_2_5, RATE_2_5, true));
        assertThrows(IllegalArgumentException.class, () ->
                GstCalculator.calculate(BD_100, null, GstMode.GST_EXCLUDED, RATE_2_5, RATE_2_5, true));
        assertThrows(IllegalArgumentException.class, () ->
                GstCalculator.calculate(BD_100, BD_ZERO, (GstMode) null, RATE_2_5, RATE_2_5, true));
        assertThrows(IllegalArgumentException.class, () ->
                GstCalculator.calculate(BD_100, BD_ZERO, GstMode.GST_EXCLUDED, null, RATE_2_5, true));
        assertThrows(IllegalArgumentException.class, () ->
                GstCalculator.calculate(BD_100, BD_ZERO, GstMode.GST_EXCLUDED, RATE_2_5, null, true));
        assertThrows(IllegalArgumentException.class, () ->
                GstCalculator.calculate(BD_100, BD_ZERO, "GST_EXCLUDED", RATE_2_5, RATE_2_5, (Boolean) null));

        // Negative subtotal
        assertThrows(IllegalArgumentException.class, () ->
                GstCalculator.calculate(new BigDecimal("-0.01"), BD_ZERO, GstMode.GST_EXCLUDED, RATE_2_5, RATE_2_5, true));

        // Negative discount
        assertThrows(IllegalArgumentException.class, () ->
                GstCalculator.calculate(BD_100, new BigDecimal("-5.00"), GstMode.GST_EXCLUDED, RATE_2_5, RATE_2_5, true));

        // Discount exceeds subtotal
        assertThrows(IllegalArgumentException.class, () ->
                GstCalculator.calculate(BD_100, new BigDecimal("100.01"), GstMode.GST_EXCLUDED, RATE_2_5, RATE_2_5, true));

        // Negative rates
        assertThrows(IllegalArgumentException.class, () ->
                GstCalculator.calculate(BD_100, BD_ZERO, GstMode.GST_EXCLUDED, new BigDecimal("-1.00"), RATE_2_5, true));
        assertThrows(IllegalArgumentException.class, () ->
                GstCalculator.calculate(BD_100, BD_ZERO, GstMode.GST_EXCLUDED, RATE_2_5, new BigDecimal("-0.01"), true));

        // Rates exceeding 50%
        assertThrows(IllegalArgumentException.class, () ->
                GstCalculator.calculate(BD_100, BD_ZERO, GstMode.GST_EXCLUDED, new BigDecimal("50.01"), RATE_2_5, true));
        assertThrows(IllegalArgumentException.class, () ->
                GstCalculator.calculate(BD_100, BD_ZERO, GstMode.GST_EXCLUDED, RATE_2_5, new BigDecimal("50.01"), true));

        // Unsupported string modes
        assertThrows(IllegalArgumentException.class, () ->
                GstCalculator.calculate(BD_100, BD_ZERO, "INVALID_MODE", RATE_2_5, RATE_2_5, true));
        assertThrows(IllegalArgumentException.class, () ->
                GstCalculator.calculate(BD_100, BD_ZERO, "", RATE_2_5, RATE_2_5, true));
        assertThrows(IllegalArgumentException.class, () ->
                GstCalculator.calculate(BD_100, BD_ZERO, "   ", RATE_2_5, RATE_2_5, true));
    }

    @Test
    @DisplayName("Mandatory Case 8: Unequal CGST and SGST rates in both modes")
    void testUnequalCgstAndSgstRates() {
        BigDecimal cgstRate = new BigDecimal("6.00");
        BigDecimal sgstRate = new BigDecimal("9.00");

        // GST_EXCLUDED with unequal rates
        GstCalculationResult excluded = GstCalculator.calculate(
                BD_100,
                BD_ZERO,
                GstMode.GST_EXCLUDED,
                cgstRate,
                sgstRate,
                true
        );
        assertEquals(0, new BigDecimal("6.00").compareTo(excluded.getCgstAmount()), "CGST must be 6.00");
        assertEquals(0, new BigDecimal("9.00").compareTo(excluded.getSgstAmount()), "SGST must be 9.00");
        assertEquals(0, new BigDecimal("15.00").compareTo(excluded.getCombinedTaxAmount()), "Combined tax must be 15.00");
        assertEquals(0, new BigDecimal("115.00").compareTo(excluded.getTotalPayable()), "Total payable must be 115.00");

        // GST_INCLUDED with unequal rates
        GstCalculationResult included = GstCalculator.calculate(
                BD_100,
                BD_ZERO,
                GstMode.GST_INCLUDED,
                cgstRate,
                sgstRate,
                true
        );
        // 100 / 1.15 = 86.95652... -> 86.96
        assertEquals(0, new BigDecimal("86.96").compareTo(included.getTaxableAmount()), "Taxable base must be 86.96");
        // 100 - 86.96 = 13.04
        assertEquals(0, new BigDecimal("13.04").compareTo(included.getCombinedTaxAmount()), "Combined tax must be 13.04");
        // 86.96 * 0.06 = 5.2176 -> 5.22
        assertEquals(0, new BigDecimal("5.22").compareTo(included.getCgstAmount()), "CGST must be 5.22");
        // 13.04 - 5.22 = 7.82
        assertEquals(0, new BigDecimal("7.82").compareTo(included.getSgstAmount()), "SGST must be 7.82");
        assertTrue(included.getCgstAmount().compareTo(BigDecimal.ZERO) >= 0, "CGST must be non-negative");
        assertTrue(included.getSgstAmount().compareTo(BigDecimal.ZERO) >= 0, "SGST must be non-negative");
        assertEquals(0, new BigDecimal("100.00").compareTo(included.getTotalPayable()), "Total payable must remain 100.00");
        assertEquals(0, included.getCgstAmount().add(included.getSgstAmount()).compareTo(included.getCombinedTaxAmount()),
                "CGST + SGST must exactly equal combined tax");
    }

    @Test
    @DisplayName("Stage 2A.1 Regression: Asymmetric Zero-SGST Rates in GST_INCLUDED must never produce negative SGST")
    void testAsymmetricZeroSgstGstIncludedRegression() {
        // Case 1: Gross 100.00, CGST 50%, SGST 0%, GST_INCLUDED
        // Divisor = 1.50 -> Taxable = 66.67, TotalTax = 33.33
        // Unadjusted CGST = round(66.67 * 0.50) = 33.34 (> TotalTax 33.33)
        // Defensively adjusted CGST = 33.33, SGST = 0.00
        GstCalculationResult res50 = GstCalculator.calculate(
                BD_100,
                BD_ZERO,
                GstMode.GST_INCLUDED,
                new BigDecimal("50.00"),
                BD_ZERO,
                true
        );
        assertNotNull(res50);
        assertTrue(res50.getCgstAmount().compareTo(BigDecimal.ZERO) >= 0, "CGST must be non-negative");
        assertTrue(res50.getSgstAmount().compareTo(BigDecimal.ZERO) >= 0, "SGST must be non-negative");
        assertEquals(0, new BigDecimal("66.67").compareTo(res50.getTaxableAmount()), "Taxable amount must be 66.67");
        assertEquals(0, new BigDecimal("33.33").compareTo(res50.getCgstAmount()), "CGST must be 33.33");
        assertEquals(0, new BigDecimal("0.00").compareTo(res50.getSgstAmount()), "SGST must be 0.00");
        assertEquals(0, new BigDecimal("33.33").compareTo(res50.getCombinedTaxAmount()), "Combined tax must be 33.33");
        assertEquals(0, res50.getCgstAmount().add(res50.getSgstAmount()).compareTo(res50.getCombinedTaxAmount()),
                "CGST + SGST must exactly equal combined tax");
        assertEquals(0, new BigDecimal("100.00").compareTo(res50.getTotalPayable()), "Total payable must equal gross amount");

        // Case 2: Gross 100.00, CGST 9%, SGST 0%, GST_INCLUDED
        // Divisor = 1.09 -> Taxable = 91.74, TotalTax = 8.26
        // CGST = round(91.74 * 0.09) = 8.26, SGST = 0.00
        GstCalculationResult res9 = GstCalculator.calculate(
                BD_100,
                BD_ZERO,
                GstMode.GST_INCLUDED,
                new BigDecimal("9.00"),
                BD_ZERO,
                true
        );
        assertNotNull(res9);
        assertTrue(res9.getCgstAmount().compareTo(BigDecimal.ZERO) >= 0, "CGST must be non-negative");
        assertTrue(res9.getSgstAmount().compareTo(BigDecimal.ZERO) >= 0, "SGST must be non-negative");
        assertEquals(0, new BigDecimal("91.74").compareTo(res9.getTaxableAmount()), "Taxable amount must be 91.74");
        assertEquals(0, new BigDecimal("8.26").compareTo(res9.getCgstAmount()), "CGST must be 8.26");
        assertEquals(0, new BigDecimal("0.00").compareTo(res9.getSgstAmount()), "SGST must be 0.00");
        assertEquals(0, new BigDecimal("8.26").compareTo(res9.getCombinedTaxAmount()), "Combined tax must be 8.26");
        assertEquals(0, res9.getCgstAmount().add(res9.getSgstAmount()).compareTo(res9.getCombinedTaxAmount()),
                "CGST + SGST must exactly equal combined tax");
        assertEquals(0, new BigDecimal("100.00").compareTo(res9.getTotalPayable()), "Total payable must equal gross amount");

        // Case 3: Reverse Asymmetric - Gross 100.00, CGST 0%, SGST 50%, GST_INCLUDED
        GstCalculationResult res0_50 = GstCalculator.calculate(
                BD_100,
                BD_ZERO,
                GstMode.GST_INCLUDED,
                BD_ZERO,
                new BigDecimal("50.00"),
                true
        );
        assertNotNull(res0_50);
        assertTrue(res0_50.getCgstAmount().compareTo(BigDecimal.ZERO) >= 0, "CGST must be non-negative");
        assertTrue(res0_50.getSgstAmount().compareTo(BigDecimal.ZERO) >= 0, "SGST must be non-negative");
        assertEquals(0, new BigDecimal("66.67").compareTo(res0_50.getTaxableAmount()), "Taxable amount must be 66.67");
        assertEquals(0, new BigDecimal("0.00").compareTo(res0_50.getCgstAmount()), "CGST must be 0.00");
        assertEquals(0, new BigDecimal("33.33").compareTo(res0_50.getSgstAmount()), "SGST must be 33.33");
        assertEquals(0, new BigDecimal("33.33").compareTo(res0_50.getCombinedTaxAmount()), "Combined tax must be 33.33");
        assertEquals(0, res0_50.getCgstAmount().add(res0_50.getSgstAmount()).compareTo(res0_50.getCombinedTaxAmount()),
                "CGST + SGST must exactly equal combined tax");
        assertEquals(0, new BigDecimal("100.00").compareTo(res0_50.getTotalPayable()), "Total payable must equal gross amount");
    }

    @Test
    @DisplayName("Boundary and Discount Edge Cases")
    void testBoundaryAndDiscountEdgeCases() {
        // Full discount (100% discount): subtotal 100.00, discount 100.00
        GstCalculationResult fullDiscount = GstCalculator.calculate(
                BD_100,
                BD_100,
                GstMode.GST_EXCLUDED,
                RATE_2_5,
                RATE_2_5,
                true
        );
        assertEquals(0, BD_ZERO.compareTo(fullDiscount.getTaxableAmount()));
        assertEquals(0, BD_ZERO.compareTo(fullDiscount.getCgstAmount()));
        assertEquals(0, BD_ZERO.compareTo(fullDiscount.getSgstAmount()));
        assertEquals(0, BD_ZERO.compareTo(fullDiscount.getCombinedTaxAmount()));
        assertEquals(0, BD_ZERO.compareTo(fullDiscount.getTotalPayable()));

        // Exactly 50.00% rate allowed (boundary maximum)
        GstCalculationResult maxRate = GstCalculator.calculate(
                BD_100,
                BD_ZERO,
                GstMode.GST_EXCLUDED,
                new BigDecimal("50.00"),
                new BigDecimal("50.00"),
                true
        );
        assertEquals(0, new BigDecimal("50.00").compareTo(maxRate.getCgstAmount()));
        assertEquals(0, new BigDecimal("50.00").compareTo(maxRate.getSgstAmount()));
        assertEquals(0, new BigDecimal("100.00").compareTo(maxRate.getCombinedTaxAmount()));
        assertEquals(0, new BigDecimal("200.00").compareTo(maxRate.getTotalPayable()));

        // String mode parsing (case-insensitivity)
        GstCalculationResult stringMode = GstCalculator.calculate(
                BD_100,
                BD_ZERO,
                "gst_included",
                RATE_2_5,
                RATE_2_5,
                Boolean.TRUE
        );
        assertEquals(GstMode.GST_INCLUDED, stringMode.getGstMode());
        assertEquals(0, new BigDecimal("100.00").compareTo(stringMode.getTotalPayable()));
    }
}
