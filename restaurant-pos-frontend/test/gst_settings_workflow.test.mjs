import test from 'node:test';
import assert from 'node:assert/strict';

/**
 * Focused Unit Tests for Admin GST Settings Workflow (Stage 1)
 * Validates loading, changing, validating, saving, and reloading GST settings
 * matching the exact implementation of SettingsPage.jsx.
 */

class SettingsPageSimulator {
  constructor(settingsServiceMock) {
    this.settingsService = settingsServiceMock;
    this.initialSettings = null;
    this.formData = {
      taxEnabled: true,
      defaultTaxRate: '0.00',
      gstMode: 'GST_EXCLUDED',
      cgstRate: '0.00',
      sgstRate: '0.00',
      serviceChargeEnabled: false,
      defaultServiceChargeRate: '0.00',
      currency: 'INR',
      timezone: 'Asia/Kolkata',
      invoicePrefix: 'INV',
      receiptHeader: '',
      receiptFooter: '',
      autoAcceptOrders: false,
      kitchenEnabled: true,
    };
    this.validationErrors = {};
    this.error = null;
    this.successMessage = null;
    this.isSaving = false;
    this.loading = false;
  }

  populateForm(data) {
    this.initialSettings = data;
    const defaultTaxRateNum = parseFloat(data.defaultTaxRate) || 0;
    const defaultTaxRateStr = data.defaultTaxRate !== undefined && data.defaultTaxRate !== null ? String(data.defaultTaxRate) : '0.00';
    const half = (Math.round(defaultTaxRateNum * 50) / 100).toFixed(2);
    const remainder = (defaultTaxRateNum - parseFloat(half)).toFixed(2);

    const cgstStr = data.cgstRate !== undefined && data.cgstRate !== null ? String(data.cgstRate) : half;
    const sgstStr = data.sgstRate !== undefined && data.sgstRate !== null ? String(data.sgstRate) : remainder;

    this.formData = {
      taxEnabled: data.taxEnabled ?? true,
      defaultTaxRate: defaultTaxRateStr,
      gstMode: data.gstMode || 'GST_EXCLUDED',
      cgstRate: cgstStr,
      sgstRate: sgstStr,
      serviceChargeEnabled: data.serviceChargeEnabled ?? false,
      defaultServiceChargeRate: data.defaultServiceChargeRate !== undefined && data.defaultServiceChargeRate !== null ? String(data.defaultServiceChargeRate) : '0.00',
      currency: data.currency || 'INR',
      timezone: data.timezone || 'Asia/Kolkata',
      invoicePrefix: data.invoicePrefix || 'INV',
      receiptHeader: data.receiptHeader || '',
      receiptFooter: data.receiptFooter || '',
      autoAcceptOrders: data.autoAcceptOrders ?? false,
      kitchenEnabled: data.kitchenEnabled ?? true,
    };
    this.validationErrors = {};
  }

  async fetchSettings() {
    this.loading = true;
    this.error = null;
    try {
      const data = await this.settingsService.getSettings();
      this.populateForm(data);
    } catch (err) {
      this.error = err.message || 'Failed to load settings';
    } finally {
      this.loading = false;
    }
  }

  handleInputChange(name, val) {
    if (name === 'defaultTaxRate') {
      const parsedRate = parseFloat(val);
      if (!isNaN(parsedRate) && parsedRate >= 0) {
        const half = (Math.round(parsedRate * 50) / 100).toFixed(2);
        const remainder = (parsedRate - parseFloat(half)).toFixed(2);
        this.formData = {
          ...this.formData,
          defaultTaxRate: val,
          cgstRate: half,
          sgstRate: remainder,
        };
      } else {
        this.formData = {
          ...this.formData,
          defaultTaxRate: val,
          cgstRate: '0.00',
          sgstRate: '0.00',
        };
      }
    } else {
      this.formData = {
        ...this.formData,
        [name]: val,
      };
    }

    if (this.validationErrors[name]) {
      delete this.validationErrors[name];
    }
    this.successMessage = null;
    this.error = null;
  }

  validateForm() {
    const errors = {};

    if (this.formData.taxEnabled) {
      const taxRateNum = parseFloat(this.formData.defaultTaxRate);
      if (isNaN(taxRateNum) || taxRateNum < 0 || taxRateNum > 100) {
        errors.defaultTaxRate = 'Tax rate must be between 0.00 and 100.00';
      } else {
        const cgstNum = parseFloat(this.formData.cgstRate);
        const sgstNum = parseFloat(this.formData.sgstRate);
        if (!isNaN(cgstNum) && !isNaN(sgstNum)) {
          const componentSum = parseFloat((cgstNum + sgstNum).toFixed(2));
          const normalizedTotal = parseFloat(taxRateNum.toFixed(2));
          if (Math.abs(componentSum - normalizedTotal) > 0.01) {
            errors.defaultTaxRate = `CGST (${cgstNum}%) + SGST (${sgstNum}%) must equal total tax rate (${normalizedTotal}%)`;
          }
        }
      }
      if (!['GST_EXCLUDED', 'GST_INCLUDED'].includes(this.formData.gstMode)) {
        errors.gstMode = 'GST mode must be either GST_EXCLUDED or GST_INCLUDED';
      }
    }

    this.validationErrors = errors;
    return Object.keys(errors).length === 0;
  }

  async handleSubmit() {
    this.successMessage = null;
    this.error = null;

    if (!this.validateForm()) {
      return false;
    }

    this.isSaving = true;

    const payload = {
      taxEnabled: this.formData.taxEnabled,
      defaultTaxRate: parseFloat(this.formData.defaultTaxRate) || 0,
      gstMode: this.formData.gstMode || 'GST_EXCLUDED',
      serviceChargeEnabled: this.formData.serviceChargeEnabled,
      defaultServiceChargeRate: parseFloat(this.formData.defaultServiceChargeRate) || 0,
      currency: this.formData.currency.trim().toUpperCase(),
      timezone: this.formData.timezone.trim(),
      invoicePrefix: this.formData.invoicePrefix.trim().toUpperCase(),
      receiptHeader: this.formData.receiptHeader ? this.formData.receiptHeader.trim() : '',
      receiptFooter: this.formData.receiptFooter ? this.formData.receiptFooter.trim() : '',
      autoAcceptOrders: this.formData.autoAcceptOrders,
      kitchenEnabled: this.formData.kitchenEnabled,
    };

    try {
      const updatedResponse = await this.settingsService.updateSettings(payload);
      this.populateForm(updatedResponse);
      this.successMessage = 'Settings updated successfully!';
      return true;
    } catch (err) {
      this.error = err.message || 'Failed to update settings';
      return false;
    } finally {
      this.isSaving = false;
    }
  }

  handleReset() {
    if (this.initialSettings) {
      this.populateForm(this.initialSettings);
      this.successMessage = null;
      this.error = null;
    }
  }
}

// ---------------------------------------------------------------------------
// TEST SUITE
// ---------------------------------------------------------------------------

test('1. Loading settings populates saved GST mode and component rates', async () => {
  const backendResponse = {
    id: 1,
    restaurantId: 2,
    taxEnabled: true,
    defaultTaxRate: '5.00',
    totalGstRate: '5.00',
    gstMode: 'GST_INCLUDED',
    cgstRate: '2.50',
    sgstRate: '2.50',
    serviceChargeEnabled: false,
    defaultServiceChargeRate: '0.00',
    currency: 'INR',
    timezone: 'Asia/Kolkata',
    invoicePrefix: 'INV',
  };

  const mockService = {
    async getSettings() {
      return backendResponse;
    },
  };

  const page = new SettingsPageSimulator(mockService);
  await page.fetchSettings();

  assert.equal(page.formData.taxEnabled, true);
  assert.equal(page.formData.defaultTaxRate, '5.00');
  assert.equal(page.formData.gstMode, 'GST_INCLUDED');
  assert.equal(page.formData.cgstRate, '2.50');
  assert.equal(page.formData.sgstRate, '2.50');
});

test('2. Legacy settings without explicit GST fields default safely to GST_EXCLUDED with 50/50 split', async () => {
  const legacyResponse = {
    id: 1,
    restaurantId: 2,
    taxEnabled: true,
    defaultTaxRate: '18.00',
    // gstMode, cgstRate, sgstRate null/undefined in legacy record
    serviceChargeEnabled: false,
    defaultServiceChargeRate: '0.00',
    currency: 'INR',
    timezone: 'Asia/Kolkata',
    invoicePrefix: 'INV',
  };

  const mockService = {
    async getSettings() {
      return legacyResponse;
    },
  };

  const page = new SettingsPageSimulator(mockService);
  await page.fetchSettings();

  assert.equal(page.formData.gstMode, 'GST_EXCLUDED');
  assert.equal(page.formData.defaultTaxRate, '18.00');
  assert.equal(page.formData.cgstRate, '9.00');
  assert.equal(page.formData.sgstRate, '9.00');
});

test('3. Changing GST mode updates form state cleanly', async () => {
  const mockService = {
    async getSettings() {
      return { taxEnabled: true, defaultTaxRate: '5.00', gstMode: 'GST_EXCLUDED' };
    },
  };

  const page = new SettingsPageSimulator(mockService);
  await page.fetchSettings();
  assert.equal(page.formData.gstMode, 'GST_EXCLUDED');

  page.handleInputChange('gstMode', 'GST_INCLUDED');
  assert.equal(page.formData.gstMode, 'GST_INCLUDED');

  page.handleInputChange('gstMode', 'GST_EXCLUDED');
  assert.equal(page.formData.gstMode, 'GST_EXCLUDED');
});

test('4. Changing total GST rate dynamically updates CGST and SGST component rates (50/50 split)', async () => {
  const page = new SettingsPageSimulator({});

  // Standard 5% rate -> 2.50% CGST, 2.50% SGST
  page.handleInputChange('defaultTaxRate', '5.00');
  assert.equal(page.formData.defaultTaxRate, '5.00');
  assert.equal(page.formData.cgstRate, '2.50');
  assert.equal(page.formData.sgstRate, '2.50');

  // 18% rate -> 9.00% CGST, 9.00% SGST
  page.handleInputChange('defaultTaxRate', '18.00');
  assert.equal(page.formData.defaultTaxRate, '18.00');
  assert.equal(page.formData.cgstRate, '9.00');
  assert.equal(page.formData.sgstRate, '9.00');

  // Odd cent rate 5.05% -> half-rounded: 2.53% CGST, 2.52% SGST
  page.handleInputChange('defaultTaxRate', '5.05');
  assert.equal(page.formData.defaultTaxRate, '5.05');
  assert.equal(page.formData.cgstRate, '2.53');
  assert.equal(page.formData.sgstRate, '2.52');
  assert.equal((parseFloat(page.formData.cgstRate) + parseFloat(page.formData.sgstRate)).toFixed(2), '5.05');
});

test('5. Rate validation enforces 0-100 range and valid GST mode', () => {
  const page = new SettingsPageSimulator({});

  // Valid 5.00% GST_EXCLUDED
  page.formData.taxEnabled = true;
  page.formData.defaultTaxRate = '5.00';
  page.formData.gstMode = 'GST_EXCLUDED';
  page.formData.cgstRate = '2.50';
  page.formData.sgstRate = '2.50';
  assert.equal(page.validateForm(), true);
  assert.equal(Object.keys(page.validationErrors).length, 0);

  // Negative rate
  page.formData.defaultTaxRate = '-2.00';
  assert.equal(page.validateForm(), false);
  assert.match(page.validationErrors.defaultTaxRate, /between 0.00 and 100.00/);

  // Rate > 100
  page.formData.defaultTaxRate = '105.00';
  assert.equal(page.validateForm(), false);
  assert.match(page.validationErrors.defaultTaxRate, /between 0.00 and 100.00/);

  // Invalid GST mode
  page.formData.defaultTaxRate = '5.00';
  page.formData.gstMode = 'INVALID_MODE';
  assert.equal(page.validateForm(), false);
  assert.match(page.validationErrors.gstMode, /GST_EXCLUDED or GST_INCLUDED/);
});

test('6. Submitting settings sends exact backend payload contract and reloads response', async () => {
  let capturedPayload = null;

  const mockService = {
    async updateSettings(payload) {
      capturedPayload = payload;
      return {
        id: 1,
        restaurantId: 2,
        ...payload,
        cgstRate: '2.50',
        sgstRate: '2.50',
        totalGstRate: '5.00',
      };
    },
  };

  const page = new SettingsPageSimulator(mockService);
  page.formData.taxEnabled = true;
  page.formData.defaultTaxRate = '5.00';
  page.formData.gstMode = 'GST_INCLUDED';
  page.formData.cgstRate = '2.50';
  page.formData.sgstRate = '2.50';

  const success = await page.handleSubmit();
  assert.equal(success, true);
  assert.equal(page.successMessage, 'Settings updated successfully!');

  // Verify backend contract
  assert.notEqual(capturedPayload, null);
  assert.equal(capturedPayload.taxEnabled, true);
  assert.equal(capturedPayload.defaultTaxRate, 5);
  assert.equal(capturedPayload.gstMode, 'GST_INCLUDED');
  assert.equal(capturedPayload.currency, 'INR');

  // Verify form state matches backend return
  assert.equal(page.formData.gstMode, 'GST_INCLUDED');
  assert.equal(page.formData.cgstRate, '2.50');
  assert.equal(page.formData.sgstRate, '2.50');
});

test('7. Resetting restores previously saved GST settings', () => {
  const page = new SettingsPageSimulator({});
  const initial = {
    taxEnabled: true,
    defaultTaxRate: '5.00',
    gstMode: 'GST_EXCLUDED',
    cgstRate: '2.50',
    sgstRate: '2.50',
  };

  page.populateForm(initial);
  assert.equal(page.formData.gstMode, 'GST_EXCLUDED');
  assert.equal(page.formData.defaultTaxRate, '5.00');

  // User edits values without saving
  page.handleInputChange('gstMode', 'GST_INCLUDED');
  page.handleInputChange('defaultTaxRate', '18.00');
  assert.equal(page.formData.gstMode, 'GST_INCLUDED');
  assert.equal(page.formData.defaultTaxRate, '18.00');
  assert.equal(page.formData.cgstRate, '9.00');

  // User hits reset
  page.handleReset();
  assert.equal(page.formData.gstMode, 'GST_EXCLUDED');
  assert.equal(page.formData.defaultTaxRate, '5.00');
  assert.equal(page.formData.cgstRate, '2.50');
  assert.equal(page.formData.sgstRate, '2.50');
});
