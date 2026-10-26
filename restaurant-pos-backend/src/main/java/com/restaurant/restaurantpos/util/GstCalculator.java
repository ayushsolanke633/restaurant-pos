package com.restaurant.restaurantpos.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/**
 * Reusable, deterministic GST calculation utility for AnnaDhara POS.
 *
 * Supports:
 * - GST_INCLUDED: Extracts tax from gross discounted amount without double taxation.
 * - GST_EXCLUDED: Calculates tax on discounted amount and adds to payable total.
 * - Separate CGST and SGST rates (0% to 50% each).
 * - Discount applied strictly before tax calculation.
 * - Exact two-decimal monetary precision using BigDecimal and RoundingMode.HALF_UP.
 * - Guaranteed exact equality: CGST + SGST == combined tax (zero 1-paisa rounding discrepancies).
 */
public final class GstCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_RATE = new BigDecimal("50.00");
    private static final int MONETARY_SCALE = 2;
    private static final int DIVISION_PRECISION_SCALE = 10;

    private GstCalculator() {
        // Prevent instantiation
    }

    /**
     * Supported GST modes.
     */
    public enum GstMode {
        GST_INCLUDED,
        GST_EXCLUDED;

        public static GstMode fromString(String modeStr) {
            if (modeStr == null || modeStr.trim().isEmpty()) {
                throw new IllegalArgumentException("GST mode cannot be null or empty");
            }
            String normalized = modeStr.trim().toUpperCase(Locale.ROOT);
            for (GstMode mode : values()) {
                if (mode.name().equals(normalized)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unsupported GST mode: " + modeStr);
        }
    }

    /**
     * Immutable result container for GST calculation results.
     */
    public static final class GstCalculationResult {
        private final BigDecimal subtotal;
        private final BigDecimal discountAmount;
        private final BigDecimal taxableAmount;
        private final BigDecimal cgstRate;
        private final BigDecimal cgstAmount;
        private final BigDecimal sgstRate;
        private final BigDecimal sgstAmount;
        private final BigDecimal combinedTaxAmount;
        private final BigDecimal totalPayable;
        private final GstMode gstMode;
        private final boolean taxEnabled;

        public GstCalculationResult(
                BigDecimal subtotal,
                BigDecimal discountAmount,
                BigDecimal taxableAmount,
                BigDecimal cgstRate,
                BigDecimal cgstAmount,
                BigDecimal sgstRate,
                BigDecimal sgstAmount,
                BigDecimal combinedTaxAmount,
                BigDecimal totalPayable,
                GstMode gstMode,
                boolean taxEnabled) {
            this.subtotal = subtotal;
            this.discountAmount = discountAmount;
            this.taxableAmount = taxableAmount;
            this.cgstRate = cgstRate;
            this.cgstAmount = cgstAmount;
            this.sgstRate = sgstRate;
            this.sgstAmount = sgstAmount;
            this.combinedTaxAmount = combinedTaxAmount;
            this.totalPayable = totalPayable;
            this.gstMode = gstMode;
            this.taxEnabled = taxEnabled;
        }

        public BigDecimal getSubtotal() {
            return subtotal;
        }

        public BigDecimal getDiscountAmount() {
            return discountAmount;
        }

        public BigDecimal getTaxableAmount() {
            return taxableAmount;
        }

        public BigDecimal getCgstRate() {
            return cgstRate;
        }

        public BigDecimal getCgstAmount() {
            return cgstAmount;
        }

        public BigDecimal getSgstRate() {
            return sgstRate;
        }

        public BigDecimal getSgstAmount() {
            return sgstAmount;
        }

        public BigDecimal getCombinedTaxAmount() {
            return combinedTaxAmount;
        }

        public BigDecimal getTaxAmount() {
            return combinedTaxAmount;
        }

        public BigDecimal getTotalPayable() {
            return totalPayable;
        }

        public BigDecimal getTotalAmount() {
            return totalPayable;
        }

        public GstMode getGstMode() {
            return gstMode;
        }

        public boolean isTaxEnabled() {
            return taxEnabled;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GstCalculationResult that = (GstCalculationResult) o;
            return taxEnabled == that.taxEnabled &&
                    compareToZero(subtotal, that.subtotal) &&
                    compareToZero(discountAmount, that.discountAmount) &&
                    compareToZero(taxableAmount, that.taxableAmount) &&
                    compareToZero(cgstRate, that.cgstRate) &&
                    compareToZero(cgstAmount, that.cgstAmount) &&
                    compareToZero(sgstRate, that.sgstRate) &&
                    compareToZero(sgstAmount, that.sgstAmount) &&
                    compareToZero(combinedTaxAmount, that.combinedTaxAmount) &&
                    compareToZero(totalPayable, that.totalPayable) &&
                    gstMode == that.gstMode;
        }

        private boolean compareToZero(BigDecimal a, BigDecimal b) {
            if (a == null && b == null) return true;
            if (a == null || b == null) return false;
            return a.compareTo(b) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(subtotal, discountAmount, taxableAmount, cgstRate,
                    cgstAmount, sgstRate, sgstAmount, combinedTaxAmount, totalPayable,
                    gstMode, taxEnabled);
        }

        @Override
        public String toString() {
            return "GstCalculationResult{" +
                    "taxableAmount=" + taxableAmount +
                    ", cgstRate=" + cgstRate +
                    ", cgstAmount=" + cgstAmount +
                    ", sgstRate=" + sgstRate +
                    ", sgstAmount=" + sgstAmount +
                    ", combinedTaxAmount=" + combinedTaxAmount +
                    ", totalPayable=" + totalPayable +
                    ", gstMode=" + gstMode +
                    ", taxEnabled=" + taxEnabled +
                    '}';
        }
    }

    /**
     * Overload accepting String GST mode and Boolean taxEnabled.
     */
    public static GstCalculationResult calculate(
            BigDecimal subtotal,
            BigDecimal discountAmount,
            String gstModeStr,
            BigDecimal cgstRate,
            BigDecimal sgstRate,
            Boolean taxEnabled) {
        if (taxEnabled == null) {
            throw new IllegalArgumentException("taxEnabled cannot be null");
        }
        GstMode mode = GstMode.fromString(gstModeStr);
        return calculate(subtotal, discountAmount, mode, cgstRate, sgstRate, taxEnabled.booleanValue());
    }

    /**
     * Deterministic GST calculation execution.
     *
     * @param subtotal Gross item subtotal before discount
     * @param discountAmount Discount amount to deduct prior to taxation
     * @param gstMode GST_INCLUDED or GST_EXCLUDED
     * @param cgstRate CGST rate percentage (0.00 to 50.00)
     * @param sgstRate SGST rate percentage (0.00 to 50.00)
     * @param taxEnabled Master tax switch
     * @return Immutable GstCalculationResult with scale 2 monetary outputs
     */
    public static GstCalculationResult calculate(
            BigDecimal subtotal,
            BigDecimal discountAmount,
            GstMode gstMode,
            BigDecimal cgstRate,
            BigDecimal sgstRate,
            boolean taxEnabled) {

        validateInputs(subtotal, discountAmount, gstMode, cgstRate, sgstRate);

        BigDecimal normalizedSubtotal = subtotal.setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
        BigDecimal normalizedDiscount = discountAmount.setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
        BigDecimal normalizedCgstRate = cgstRate.setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
        BigDecimal normalizedSgstRate = sgstRate.setScale(MONETARY_SCALE, RoundingMode.HALF_UP);

        BigDecimal discountedAmount = normalizedSubtotal.subtract(normalizedDiscount);

        // When tax is disabled: zero CGST, zero SGST, zero combined tax. Total is subtotal - discount.
        if (!taxEnabled) {
            BigDecimal zero = BigDecimal.ZERO.setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
            return new GstCalculationResult(
                    normalizedSubtotal,
                    normalizedDiscount,
                    discountedAmount,
                    normalizedCgstRate,
                    zero,
                    normalizedSgstRate,
                    zero,
                    zero,
                    discountedAmount,
                    gstMode,
                    false
            );
        }

        if (gstMode == GstMode.GST_EXCLUDED) {
            BigDecimal taxableAmount = discountedAmount;
            BigDecimal cgstAmount = taxableAmount.multiply(normalizedCgstRate)
                    .divide(HUNDRED, MONETARY_SCALE, RoundingMode.HALF_UP);
            BigDecimal sgstAmount = taxableAmount.multiply(normalizedSgstRate)
                    .divide(HUNDRED, MONETARY_SCALE, RoundingMode.HALF_UP);
            BigDecimal combinedTax = cgstAmount.add(sgstAmount).setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
            BigDecimal totalPayable = taxableAmount.add(combinedTax).setScale(MONETARY_SCALE, RoundingMode.HALF_UP);

            return new GstCalculationResult(
                    normalizedSubtotal,
                    normalizedDiscount,
                    taxableAmount,
                    normalizedCgstRate,
                    cgstAmount,
                    normalizedSgstRate,
                    sgstAmount,
                    combinedTax,
                    totalPayable,
                    gstMode,
                    true
            );
        } else {
            // GST_INCLUDED
            BigDecimal grossAmount = discountedAmount;
            BigDecimal combinedRate = normalizedCgstRate.add(normalizedSgstRate);

            BigDecimal taxableAmount;
            BigDecimal totalTax;
            BigDecimal cgstAmount;
            BigDecimal sgstAmount;

            if (combinedRate.compareTo(BigDecimal.ZERO) == 0) {
                taxableAmount = grossAmount;
                totalTax = BigDecimal.ZERO.setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
                cgstAmount = BigDecimal.ZERO.setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
                sgstAmount = BigDecimal.ZERO.setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
            } else {
                BigDecimal divisor = BigDecimal.ONE.add(
                        combinedRate.divide(HUNDRED, DIVISION_PRECISION_SCALE, RoundingMode.HALF_UP)
                );
                taxableAmount = grossAmount.divide(divisor, MONETARY_SCALE, RoundingMode.HALF_UP);
                totalTax = grossAmount.subtract(taxableAmount).setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
                cgstAmount = taxableAmount.multiply(normalizedCgstRate)
                        .divide(HUNDRED, MONETARY_SCALE, RoundingMode.HALF_UP);
                // Defensive allocation adjustment: CGST must not exceed total extracted tax,
                // and if SGST rate is zero, all extracted tax belongs to CGST.
                // Guarantees both components are nonnegative and CGST + SGST == totalTax exactly.
                if (cgstAmount.compareTo(totalTax) > 0 || normalizedSgstRate.compareTo(BigDecimal.ZERO) == 0) {
                    cgstAmount = totalTax;
                }
                sgstAmount = totalTax.subtract(cgstAmount).setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
            }

            BigDecimal totalPayable = grossAmount;

            return new GstCalculationResult(
                    normalizedSubtotal,
                    normalizedDiscount,
                    taxableAmount,
                    normalizedCgstRate,
                    cgstAmount,
                    normalizedSgstRate,
                    sgstAmount,
                    totalTax,
                    totalPayable,
                    gstMode,
                    true
            );
        }
    }

    private static void validateInputs(
            BigDecimal subtotal,
            BigDecimal discountAmount,
            GstMode gstMode,
            BigDecimal cgstRate,
            BigDecimal sgstRate) {

        if (subtotal == null) {
            throw new IllegalArgumentException("Subtotal cannot be null");
        }
        if (discountAmount == null) {
            throw new IllegalArgumentException("Discount amount cannot be null");
        }
        if (gstMode == null) {
            throw new IllegalArgumentException("GST mode cannot be null");
        }
        if (cgstRate == null) {
            throw new IllegalArgumentException("CGST rate cannot be null");
        }
        if (sgstRate == null) {
            throw new IllegalArgumentException("SGST rate cannot be null");
        }

        if (subtotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Subtotal cannot be negative: " + subtotal);
        }
        if (discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Discount amount cannot be negative: " + discountAmount);
        }
        if (discountAmount.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("Discount amount (" + discountAmount + ") cannot exceed subtotal (" + subtotal + ")");
        }
        if (cgstRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("CGST rate cannot be negative: " + cgstRate);
        }
        if (sgstRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("SGST rate cannot be negative: " + sgstRate);
        }
        if (cgstRate.compareTo(MAX_RATE) > 0) {
            throw new IllegalArgumentException("CGST rate cannot exceed 50%: " + cgstRate);
        }
        if (sgstRate.compareTo(MAX_RATE) > 0) {
            throw new IllegalArgumentException("SGST rate cannot exceed 50%: " + sgstRate);
        }
    }
}
