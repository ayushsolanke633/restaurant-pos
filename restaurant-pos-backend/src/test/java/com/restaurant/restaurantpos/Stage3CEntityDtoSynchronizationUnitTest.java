package com.restaurant.restaurantpos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restaurant.restaurantpos.dto.BillResponse;
import com.restaurant.restaurantpos.dto.OrderResponse;
import com.restaurant.restaurantpos.dto.RestaurantSettingsRequest;
import com.restaurant.restaurantpos.dto.RestaurantSettingsResponse;
import com.restaurant.restaurantpos.entity.Bill;
import com.restaurant.restaurantpos.entity.Order;
import com.restaurant.restaurantpos.entity.Restaurant;
import com.restaurant.restaurantpos.entity.RestaurantSettings;
import jakarta.persistence.Column;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class Stage3CEntityDtoSynchronizationUnitTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    private Column getColumnAnnotation(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Field field = clazz.getDeclaredField(fieldName);
        Column col = field.getAnnotation(Column.class);
        assertNotNull(col, "Field " + fieldName + " on " + clazz.getSimpleName() + " must have @Column annotation");
        return col;
    }

    private void invokeOnCreate(Object entity) throws Exception {
        Method onCreate = entity.getClass().getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        onCreate.invoke(entity);
    }

    @Test
    @DisplayName("RestaurantSettings entity mapping and defaults contract")
    void testRestaurantSettingsEntityMappingAndDefaults() throws Exception {
        // JPA Column annotations verification
        Column gstModeCol = getColumnAnnotation(RestaurantSettings.class, "gstMode");
        assertEquals("gst_mode", gstModeCol.name());
        assertFalse(gstModeCol.nullable(), "gst_mode must be non-null in restaurant_settings");
        assertEquals(20, gstModeCol.length());

        Column cgstRateCol = getColumnAnnotation(RestaurantSettings.class, "cgstRate");
        assertEquals("cgst_rate", cgstRateCol.name());
        assertFalse(cgstRateCol.nullable(), "cgst_rate must be non-null in restaurant_settings");
        assertEquals(5, cgstRateCol.precision());
        assertEquals(2, cgstRateCol.scale());

        Column sgstRateCol = getColumnAnnotation(RestaurantSettings.class, "sgstRate");
        assertEquals("sgst_rate", sgstRateCol.name());
        assertFalse(sgstRateCol.nullable(), "sgst_rate must be non-null in restaurant_settings");
        assertEquals(5, sgstRateCol.precision());
        assertEquals(2, sgstRateCol.scale());

        Column defaultTaxRateCol = getColumnAnnotation(RestaurantSettings.class, "defaultTaxRate");
        assertEquals("default_tax_rate", defaultTaxRateCol.name());
        assertFalse(defaultTaxRateCol.nullable());
        assertEquals(5, defaultTaxRateCol.precision());
        assertEquals(2, defaultTaxRateCol.scale());

        // Default instantiation verification
        RestaurantSettings settings = new RestaurantSettings();
        assertEquals("GST_EXCLUDED", settings.getGstMode());
        assertEquals(new BigDecimal("0.00"), settings.getCgstRate());
        assertEquals(new BigDecimal("0.00"), settings.getSgstRate());
        assertEquals(new BigDecimal("0.00"), settings.getDefaultTaxRate());
        assertTrue(settings.getTaxEnabled());

        // Lifecycle onCreate null recovery verification
        settings.setGstMode(null);
        settings.setCgstRate(null);
        settings.setSgstRate(null);
        settings.setDefaultTaxRate(null);
        invokeOnCreate(settings);

        assertEquals("GST_EXCLUDED", settings.getGstMode());
        assertEquals(new BigDecimal("0.00"), settings.getCgstRate());
        assertEquals(new BigDecimal("0.00"), settings.getSgstRate());
        assertEquals(new BigDecimal("0.00"), settings.getDefaultTaxRate());
    }

    @Test
    @DisplayName("Order entity mapping and legacy null support contract")
    void testOrderEntityMappingAndLegacyNullSupport() throws Exception {
        // JPA Column annotations verification
        Column gstModeCol = getColumnAnnotation(Order.class, "gstMode");
        assertEquals("gst_mode", gstModeCol.name());
        assertTrue(gstModeCol.nullable(), "gst_mode must be nullable in orders for legacy rows");
        assertEquals(20, gstModeCol.length());

        Column cgstRateCol = getColumnAnnotation(Order.class, "cgstRate");
        assertEquals("cgst_rate", cgstRateCol.name());
        assertTrue(cgstRateCol.nullable(), "cgst_rate must be nullable in orders for legacy rows");
        assertEquals(5, cgstRateCol.precision());
        assertEquals(2, cgstRateCol.scale());

        Column sgstRateCol = getColumnAnnotation(Order.class, "sgstRate");
        assertEquals("sgst_rate", sgstRateCol.name());
        assertTrue(sgstRateCol.nullable(), "sgst_rate must be nullable in orders for legacy rows");
        assertEquals(5, sgstRateCol.precision());
        assertEquals(2, sgstRateCol.scale());

        Column cgstAmountCol = getColumnAnnotation(Order.class, "cgstAmount");
        assertEquals("cgst_amount", cgstAmountCol.name());
        assertFalse(cgstAmountCol.nullable(), "cgst_amount must be non-null in orders");
        assertEquals(10, cgstAmountCol.precision());
        assertEquals(2, cgstAmountCol.scale());

        Column sgstAmountCol = getColumnAnnotation(Order.class, "sgstAmount");
        assertEquals("sgst_amount", sgstAmountCol.name());
        assertFalse(sgstAmountCol.nullable(), "sgst_amount must be non-null in orders");
        assertEquals(10, sgstAmountCol.precision());
        assertEquals(2, sgstAmountCol.scale());

        Column taxAmountCol = getColumnAnnotation(Order.class, "taxAmount");
        assertEquals("tax_amount", taxAmountCol.name());
        assertFalse(taxAmountCol.nullable(), "tax_amount must remain non-null combined tax");

        // Instantiation defaults
        Order order = new Order();
        assertNull(order.getGstMode(), "Order gstMode must default to null for legacy compatibility");
        assertNull(order.getCgstRate(), "Order cgstRate must default to null for legacy compatibility");
        assertNull(order.getSgstRate(), "Order sgstRate must default to null for legacy compatibility");
        assertEquals(new BigDecimal("0.00"), order.getCgstAmount());
        assertEquals(new BigDecimal("0.00"), order.getSgstAmount());

        // Lifecycle onCreate null recovery for amounts while keeping rates nullable
        order.setCgstAmount(null);
        order.setSgstAmount(null);
        invokeOnCreate(order);
        assertNull(order.getGstMode(), "gstMode remains null on legacy order");
        assertNull(order.getCgstRate(), "cgstRate remains null on legacy order");
        assertNull(order.getSgstRate(), "sgstRate remains null on legacy order");
        assertEquals(new BigDecimal("0.00"), order.getCgstAmount());
        assertEquals(new BigDecimal("0.00"), order.getSgstAmount());
    }

    @Test
    @DisplayName("Bill entity mapping and legacy null support contract")
    void testBillEntityMappingAndLegacyNullSupport() throws Exception {
        // JPA Column annotations verification
        Column gstModeCol = getColumnAnnotation(Bill.class, "gstMode");
        assertEquals("gst_mode", gstModeCol.name());
        assertTrue(gstModeCol.nullable(), "gst_mode must be nullable in bills for legacy rows");
        assertEquals(20, gstModeCol.length());

        Column cgstRateCol = getColumnAnnotation(Bill.class, "cgstRate");
        assertEquals("cgst_rate", cgstRateCol.name());
        assertTrue(cgstRateCol.nullable(), "cgst_rate must be nullable in bills for legacy rows");
        assertEquals(5, cgstRateCol.precision());
        assertEquals(2, cgstRateCol.scale());

        Column sgstRateCol = getColumnAnnotation(Bill.class, "sgstRate");
        assertEquals("sgst_rate", sgstRateCol.name());
        assertTrue(sgstRateCol.nullable(), "sgst_rate must be nullable in bills for legacy rows");
        assertEquals(5, sgstRateCol.precision());
        assertEquals(2, sgstRateCol.scale());

        Column cgstAmountCol = getColumnAnnotation(Bill.class, "cgstAmount");
        assertEquals("cgst_amount", cgstAmountCol.name());
        assertFalse(cgstAmountCol.nullable(), "cgst_amount must be non-null in bills");
        assertEquals(10, cgstAmountCol.precision());
        assertEquals(2, cgstAmountCol.scale());

        Column sgstAmountCol = getColumnAnnotation(Bill.class, "sgstAmount");
        assertEquals("sgst_amount", sgstAmountCol.name());
        assertFalse(sgstAmountCol.nullable(), "sgst_amount must be non-null in bills");
        assertEquals(10, sgstAmountCol.precision());
        assertEquals(2, sgstAmountCol.scale());

        Column taxAmountCol = getColumnAnnotation(Bill.class, "taxAmount");
        assertEquals("tax_amount", taxAmountCol.name());
        assertFalse(taxAmountCol.nullable(), "tax_amount must remain non-null combined tax");

        // Instantiation defaults
        Bill bill = new Bill();
        assertNull(bill.getGstMode(), "Bill gstMode must default to null for legacy compatibility");
        assertNull(bill.getCgstRate(), "Bill cgstRate must default to null for legacy compatibility");
        assertNull(bill.getSgstRate(), "Bill sgstRate must default to null for legacy compatibility");
        assertEquals(new BigDecimal("0.00"), bill.getCgstAmount());
        assertEquals(new BigDecimal("0.00"), bill.getSgstAmount());

        // Lifecycle onCreate null recovery for amounts while keeping rates nullable
        bill.setCgstAmount(null);
        bill.setSgstAmount(null);
        invokeOnCreate(bill);
        assertNull(bill.getGstMode(), "gstMode remains null on legacy bill");
        assertNull(bill.getCgstRate(), "cgstRate remains null on legacy bill");
        assertNull(bill.getSgstRate(), "sgstRate remains null on legacy bill");
        assertEquals(new BigDecimal("0.00"), bill.getCgstAmount());
        assertEquals(new BigDecimal("0.00"), bill.getSgstAmount());
    }

    @Test
    @DisplayName("RestaurantSettingsResponse exposes GST mode, total GST rate, CGST, SGST, and defaultTaxRate")
    void testRestaurantSettingsResponseSerialization() throws Exception {
        RestaurantSettingsResponse response = RestaurantSettingsResponse.builder()
                .id(1L)
                .restaurantId(10L)
                .taxEnabled(true)
                .defaultTaxRate(new BigDecimal("5.00"))
                .totalGstRate(new BigDecimal("5.00"))
                .gstMode("GST_EXCLUDED")
                .cgstRate(new BigDecimal("2.50"))
                .sgstRate(new BigDecimal("2.50"))
                .currency("INR")
                .timezone("Asia/Kolkata")
                .build();

        String json = objectMapper.writeValueAsString(response);
        JsonNode node = objectMapper.readTree(json);

        assertEquals("GST_EXCLUDED", node.get("gstMode").asText());
        assertEquals(5.00, node.get("defaultTaxRate").asDouble());
        assertEquals(5.00, node.get("totalGstRate").asDouble());
        assertEquals(2.50, node.get("cgstRate").asDouble());
        assertEquals(2.50, node.get("sgstRate").asDouble());

        // Round-trip deserialization
        RestaurantSettingsResponse roundTrip = objectMapper.readValue(json, RestaurantSettingsResponse.class);
        assertEquals("GST_EXCLUDED", roundTrip.getGstMode());
        assertEquals(new BigDecimal("5.00"), roundTrip.getDefaultTaxRate());
        assertEquals(new BigDecimal("5.00"), roundTrip.getTotalGstRate());
        assertEquals(new BigDecimal("2.50"), roundTrip.getCgstRate());
        assertEquals(new BigDecimal("2.50"), roundTrip.getSgstRate());
    }

    @Test
    @DisplayName("RestaurantSettingsRequest accepts optional gstMode while preserving defaultTaxRate")
    void testRestaurantSettingsRequestDeserialization() throws Exception {
        // Payload with gstMode
        String jsonWithGst = "{\"defaultTaxRate\": 18.00, \"gstMode\": \"GST_INCLUDED\"}";
        RestaurantSettingsRequest req = objectMapper.readValue(jsonWithGst, RestaurantSettingsRequest.class);
        assertEquals(new BigDecimal("18.00"), req.getDefaultTaxRate());
        assertEquals("GST_INCLUDED", req.getGstMode());

        // Legacy payload without gstMode
        String legacyJson = "{\"defaultTaxRate\": 5.00}";
        RestaurantSettingsRequest legacyReq = objectMapper.readValue(legacyJson, RestaurantSettingsRequest.class);
        assertEquals(new BigDecimal("5.00"), legacyReq.getDefaultTaxRate());
        assertNull(legacyReq.getGstMode());
    }

    @Test
    @DisplayName("OrderResponse exposes GST mode, rates, CGST/SGST amounts, and preserves taxAmount")
    void testOrderResponseSerialization() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(101L)
                .restaurantId(10L)
                .orderType("DINE_IN")
                .status("NEW")
                .subtotal(new BigDecimal("500.00"))
                .taxAmount(new BigDecimal("25.00"))
                .discountAmount(BigDecimal.ZERO.setScale(2))
                .totalAmount(new BigDecimal("525.00"))
                .gstMode("GST_EXCLUDED")
                .cgstRate(new BigDecimal("2.50"))
                .sgstRate(new BigDecimal("2.50"))
                .cgstAmount(new BigDecimal("12.50"))
                .sgstAmount(new BigDecimal("12.50"))
                .build();

        String json = objectMapper.writeValueAsString(response);
        JsonNode node = objectMapper.readTree(json);

        assertEquals("GST_EXCLUDED", node.get("gstMode").asText());
        assertEquals(2.50, node.get("cgstRate").asDouble());
        assertEquals(2.50, node.get("sgstRate").asDouble());
        assertEquals(12.50, node.get("cgstAmount").asDouble());
        assertEquals(12.50, node.get("sgstAmount").asDouble());
        assertEquals(25.00, node.get("taxAmount").asDouble());

        // Legacy order with null GST mode and rates
        OrderResponse legacyResponse = OrderResponse.builder()
                .id(102L)
                .restaurantId(10L)
                .subtotal(new BigDecimal("500.00"))
                .taxAmount(new BigDecimal("25.00"))
                .discountAmount(BigDecimal.ZERO.setScale(2))
                .totalAmount(new BigDecimal("525.00"))
                .gstMode(null)
                .cgstRate(null)
                .sgstRate(null)
                .cgstAmount(new BigDecimal("0.00"))
                .sgstAmount(new BigDecimal("0.00"))
                .build();

        String legacyJson = objectMapper.writeValueAsString(legacyResponse);
        JsonNode legacyNode = objectMapper.readTree(legacyJson);
        assertTrue(legacyNode.get("gstMode").isNull());
        assertTrue(legacyNode.get("cgstRate").isNull());
        assertTrue(legacyNode.get("sgstRate").isNull());
        assertEquals(0.00, legacyNode.get("cgstAmount").asDouble());
        assertEquals(0.00, legacyNode.get("sgstAmount").asDouble());
        assertEquals(25.00, legacyNode.get("taxAmount").asDouble());
    }

    @Test
    @DisplayName("BillResponse represents frozen snapshot and maintains legacy bill null compatibility")
    void testBillResponseSerialization() throws Exception {
        BillResponse response = BillResponse.builder()
                .id(201L)
                .restaurantId(10L)
                .orderId(101L)
                .billNumber("BILL-000001")
                .subtotal(new BigDecimal("1000.00"))
                .taxAmount(new BigDecimal("50.00"))
                .discountAmount(BigDecimal.ZERO.setScale(2))
                .totalAmount(new BigDecimal("1050.00"))
                .gstMode("GST_EXCLUDED")
                .cgstRate(new BigDecimal("2.50"))
                .sgstRate(new BigDecimal("2.50"))
                .cgstAmount(new BigDecimal("25.00"))
                .sgstAmount(new BigDecimal("25.00"))
                .createdAt(LocalDateTime.now())
                .build();

        String json = objectMapper.writeValueAsString(response);
        JsonNode node = objectMapper.readTree(json);

        assertEquals("GST_EXCLUDED", node.get("gstMode").asText());
        assertEquals(2.50, node.get("cgstRate").asDouble());
        assertEquals(2.50, node.get("sgstRate").asDouble());
        assertEquals(25.00, node.get("cgstAmount").asDouble());
        assertEquals(25.00, node.get("sgstAmount").asDouble());
        assertEquals(50.00, node.get("taxAmount").asDouble());

        // Legacy bill compatibility
        BillResponse legacyResponse = BillResponse.builder()
                .id(202L)
                .restaurantId(10L)
                .orderId(102L)
                .billNumber("BILL-000002")
                .subtotal(new BigDecimal("1000.00"))
                .taxAmount(BigDecimal.ZERO.setScale(2))
                .discountAmount(BigDecimal.ZERO.setScale(2))
                .totalAmount(new BigDecimal("1000.00"))
                .gstMode(null)
                .cgstRate(null)
                .sgstRate(null)
                .cgstAmount(new BigDecimal("0.00"))
                .sgstAmount(new BigDecimal("0.00"))
                .createdAt(LocalDateTime.now())
                .build();

        String legacyJson = objectMapper.writeValueAsString(legacyResponse);
        JsonNode legacyNode = objectMapper.readTree(legacyJson);
        assertTrue(legacyNode.get("gstMode").isNull());
        assertTrue(legacyNode.get("cgstRate").isNull());
        assertTrue(legacyNode.get("sgstRate").isNull());
        assertEquals(0.00, legacyNode.get("cgstAmount").asDouble());
        assertEquals(0.00, legacyNode.get("sgstAmount").asDouble());
        assertEquals(0.00, legacyNode.get("taxAmount").asDouble());
    }
}
