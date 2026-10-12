import React, { useState, useEffect } from 'react';
import settingsService from '../../services/settingsService';

const COMMON_CURRENCIES = [
  { code: 'INR', label: 'INR (₹) - Indian Rupee' },
  { code: 'USD', label: 'USD ($) - US Dollar' },
  { code: 'EUR', label: 'EUR (€) - Euro' },
  { code: 'GBP', label: 'GBP (£) - British Pound' },
  { code: 'AED', label: 'AED (د.إ) - UAE Dirham' },
  { code: 'CAD', label: 'CAD ($) - Canadian Dollar' },
  { code: 'AUD', label: 'AUD ($) - Australian Dollar' },
  { code: 'SGD', label: 'SGD ($) - Singapore Dollar' },
];

const COMMON_TIMEZONES = [
  'Asia/Kolkata',
  'America/New_York',
  'America/Chicago',
  'America/Los_Angeles',
  'Europe/London',
  'Europe/Paris',
  'Asia/Dubai',
  'Asia/Singapore',
  'Asia/Tokyo',
  'Australia/Sydney',
  'UTC',
];

export const SettingsPage = () => {
  const [initialSettings, setInitialSettings] = useState(null);
  const [formData, setFormData] = useState({
    taxEnabled: true,
    defaultTaxRate: '0.00',
    serviceChargeEnabled: false,
    defaultServiceChargeRate: '0.00',
    currency: 'INR',
    timezone: 'Asia/Kolkata',
    invoicePrefix: 'INV',
    receiptHeader: '',
    receiptFooter: '',
    autoAcceptOrders: false,
  });

  const [loading, setLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);
  const [validationErrors, setValidationErrors] = useState({});

  useEffect(() => {
    fetchSettings();
  }, []);

  const fetchSettings = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await settingsService.getSettings();
      populateForm(data);
    } catch (err) {
      console.error('Failed to load settings:', err);
      setError(err.response?.data?.message || 'Failed to load restaurant settings. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const populateForm = (data) => {
    setInitialSettings(data);
    setFormData({
      taxEnabled: data.taxEnabled ?? true,
      defaultTaxRate: data.defaultTaxRate !== undefined && data.defaultTaxRate !== null ? String(data.defaultTaxRate) : '0.00',
      serviceChargeEnabled: data.serviceChargeEnabled ?? false,
      defaultServiceChargeRate: data.defaultServiceChargeRate !== undefined && data.defaultServiceChargeRate !== null ? String(data.defaultServiceChargeRate) : '0.00',
      currency: data.currency || 'INR',
      timezone: data.timezone || 'Asia/Kolkata',
      invoicePrefix: data.invoicePrefix || 'INV',
      receiptHeader: data.receiptHeader || '',
      receiptFooter: data.receiptFooter || '',
      autoAcceptOrders: data.autoAcceptOrders ?? false,
    });
    setValidationErrors({});
  };

  const handleInputChange = (e) => {
    const { name, value, type, checked } = e.target;
    const val = type === 'checkbox' ? checked : value;

    setFormData((prev) => ({
      ...prev,
      [name]: val,
    }));

    if (validationErrors[name]) {
      setValidationErrors((prev) => ({ ...prev, [name]: null }));
    }
    setSuccessMessage(null);
    setError(null);
  };

  const validateForm = () => {
    const errors = {};

    // Tax Rate validation
    if (formData.taxEnabled) {
      const taxRateNum = parseFloat(formData.defaultTaxRate);
      if (isNaN(taxRateNum) || taxRateNum < 0 || taxRateNum > 100) {
        errors.defaultTaxRate = 'Tax rate must be between 0.00 and 100.00';
      }
    }

    // Service Charge Rate validation
    if (formData.serviceChargeEnabled) {
      const scRateNum = parseFloat(formData.defaultServiceChargeRate);
      if (isNaN(scRateNum) || scRateNum < 0 || scRateNum > 100) {
        errors.defaultServiceChargeRate = 'Service charge rate must be between 0.00 and 100.00';
      }
    }

    // Currency validation
    if (!formData.currency || formData.currency.trim().length !== 3) {
      errors.currency = 'Currency must be a 3-character ISO code (e.g. INR, USD)';
    }

    // Timezone validation
    if (!formData.timezone || !formData.timezone.trim()) {
      errors.timezone = 'Timezone is required';
    }

    // Invoice Prefix validation
    if (!formData.invoicePrefix || formData.invoicePrefix.trim().length === 0 || formData.invoicePrefix.trim().length > 20) {
      errors.invoicePrefix = 'Invoice prefix must be between 1 and 20 characters';
    }

    // Receipt Header validation
    if (formData.receiptHeader && formData.receiptHeader.length > 500) {
      errors.receiptHeader = 'Receipt header cannot exceed 500 characters';
    }

    // Receipt Footer validation
    if (formData.receiptFooter && formData.receiptFooter.length > 500) {
      errors.receiptFooter = 'Receipt footer cannot exceed 500 characters';
    }

    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSuccessMessage(null);
    setError(null);

    if (!validateForm()) {
      return;
    }

    setIsSaving(true);

    const payload = {
      taxEnabled: formData.taxEnabled,
      defaultTaxRate: parseFloat(formData.defaultTaxRate) || 0,
      serviceChargeEnabled: formData.serviceChargeEnabled,
      defaultServiceChargeRate: parseFloat(formData.defaultServiceChargeRate) || 0,
      currency: formData.currency.trim().toUpperCase(),
      timezone: formData.timezone.trim(),
      invoicePrefix: formData.invoicePrefix.trim().toUpperCase(),
      receiptHeader: formData.receiptHeader ? formData.receiptHeader.trim() : '',
      receiptFooter: formData.receiptFooter ? formData.receiptFooter.trim() : '',
      autoAcceptOrders: formData.autoAcceptOrders,
    };

    try {
      const updatedResponse = await settingsService.updateSettings(payload);
      populateForm(updatedResponse);
      setSuccessMessage('Settings updated successfully!');
    } catch (err) {
      console.error('Failed to update settings:', err);
      const backendMessage = err.response?.data?.message || err.message;
      setError(backendMessage || 'Failed to update settings. Please check your inputs.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleReset = () => {
    if (initialSettings) {
      populateForm(initialSettings);
      setSuccessMessage(null);
      setError(null);
    }
  };

  if (loading) {
    return (
      <div className="settings-page">
        <div className="settings-header">
          <h1>System Configuration</h1>
          <p>Manage operational, billing, and localization settings</p>
        </div>
        <div className="settings-loading">
          <div className="spinner"></div>
          <p>Loading system configuration...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="settings-page">
      <div className="settings-header">
        <div>
          <h1>System Configuration</h1>
          <p>Configure operational, billing, tax, and receipt settings for your restaurant</p>
        </div>
        <button
          type="button"
          className="btn-refresh-settings"
          onClick={fetchSettings}
          disabled={loading || isSaving}
          title="Refresh Settings"
        >
          Refresh
        </button>
      </div>

      {successMessage && (
        <div className="settings-alert success">
          <span>{successMessage}</span>
          <button className="alert-close" onClick={() => setSuccessMessage(null)}>Close</button>
        </div>
      )}

      {error && (
        <div className="settings-alert danger">
          <span>{error}</span>
          <button className="alert-close" onClick={() => setError(null)}>Close</button>
        </div>
      )}

      <form onSubmit={handleSubmit} className="settings-form">
        {/* Section 1: Regional & General Settings */}
        <div className="settings-card">
          <div className="card-header">
            <h2>Regional & General Settings</h2>
            <p>System currency, timezone, and document prefixes</p>
          </div>
          <div className="card-body grid-2">
            <div className="form-group">
              <label htmlFor="currency">Currency Code *</label>
              <select
                id="currency"
                name="currency"
                value={formData.currency}
                onChange={handleInputChange}
                disabled={isSaving}
                className={validationErrors.currency ? 'is-invalid' : ''}
              >
                {COMMON_CURRENCIES.map((curr) => (
                  <option key={curr.code} value={curr.code}>
                    {curr.label}
                  </option>
                ))}
                {!COMMON_CURRENCIES.some((c) => c.code === formData.currency) && (
                  <option value={formData.currency}>{formData.currency}</option>
                )}
              </select>
              {validationErrors.currency && (
                <span className="error-text">{validationErrors.currency}</span>
              )}
            </div>

            <div className="form-group">
              <label htmlFor="timezone">Timezone *</label>
              <select
                id="timezone"
                name="timezone"
                value={formData.timezone}
                onChange={handleInputChange}
                disabled={isSaving}
                className={validationErrors.timezone ? 'is-invalid' : ''}
              >
                {COMMON_TIMEZONES.map((tz) => (
                  <option key={tz} value={tz}>
                    {tz}
                  </option>
                ))}
                {!COMMON_TIMEZONES.includes(formData.timezone) && (
                  <option value={formData.timezone}>{formData.timezone}</option>
                )}
              </select>
              {validationErrors.timezone && (
                <span className="error-text">{validationErrors.timezone}</span>
              )}
            </div>

            <div className="form-group">
              <label htmlFor="invoicePrefix">Invoice Prefix *</label>
              <input
                type="text"
                id="invoicePrefix"
                name="invoicePrefix"
                value={formData.invoicePrefix}
                onChange={handleInputChange}
                disabled={isSaving}
                maxLength={20}
                placeholder="e.g. INV"
                className={validationErrors.invoicePrefix ? 'is-invalid' : ''}
              />
              <span className="hint-text">Prefix used for generated billing invoices (1-20 characters)</span>
              {validationErrors.invoicePrefix && (
                <span className="error-text">{validationErrors.invoicePrefix}</span>
              )}
            </div>
          </div>
        </div>

        {/* Section 2: Billing & Tax Settings */}
        <div className="settings-card">
          <div className="card-header">
            <h2>Billing, Tax & Charges</h2>
            <p>Configure default tax rate and service charge percentages applied at billing</p>
          </div>
          <div className="card-body grid-2">
            {/* Tax Settings */}
            <div className="toggle-group-card">
              <div className="toggle-header">
                <div>
                  <label htmlFor="taxEnabled" className="toggle-title">Enable Tax</label>
                  <p className="toggle-desc">Automatically apply tax rate during bill calculation</p>
                </div>
                <label className="switch">
                  <input
                    type="checkbox"
                    id="taxEnabled"
                    name="taxEnabled"
                    checked={formData.taxEnabled}
                    onChange={handleInputChange}
                    disabled={isSaving}
                  />
                  <span className="slider round"></span>
                </label>
              </div>

              {formData.taxEnabled && (
                <div className="form-group margin-top-sm">
                  <label htmlFor="defaultTaxRate">Default Tax Rate (%) *</label>
                  <input
                    type="number"
                    id="defaultTaxRate"
                    name="defaultTaxRate"
                    value={formData.defaultTaxRate}
                    onChange={handleInputChange}
                    disabled={isSaving}
                    step="0.01"
                    min="0"
                    max="100"
                    placeholder="0.00"
                    className={validationErrors.defaultTaxRate ? 'is-invalid' : ''}
                  />
                  {validationErrors.defaultTaxRate && (
                    <span className="error-text">{validationErrors.defaultTaxRate}</span>
                  )}
                </div>
              )}
            </div>

            {/* Service Charge Settings */}
            <div className="toggle-group-card">
              <div className="toggle-header">
                <div>
                  <label htmlFor="serviceChargeEnabled" className="toggle-title">Enable Service Charge</label>
                  <p className="toggle-desc">Apply optional service charge on customer orders</p>
                </div>
                <label className="switch">
                  <input
                    type="checkbox"
                    id="serviceChargeEnabled"
                    name="serviceChargeEnabled"
                    checked={formData.serviceChargeEnabled}
                    onChange={handleInputChange}
                    disabled={isSaving}
                  />
                  <span className="slider round"></span>
                </label>
              </div>

              {formData.serviceChargeEnabled && (
                <div className="form-group margin-top-sm">
                  <label htmlFor="defaultServiceChargeRate">Default Service Charge Rate (%) *</label>
                  <input
                    type="number"
                    id="defaultServiceChargeRate"
                    name="defaultServiceChargeRate"
                    value={formData.defaultServiceChargeRate}
                    onChange={handleInputChange}
                    disabled={isSaving}
                    step="0.01"
                    min="0"
                    max="100"
                    placeholder="0.00"
                    className={validationErrors.defaultServiceChargeRate ? 'is-invalid' : ''}
                  />
                  {validationErrors.defaultServiceChargeRate && (
                    <span className="error-text">{validationErrors.defaultServiceChargeRate}</span>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Section 3: Receipt Customization */}
        <div className="settings-card">
          <div className="card-header">
            <h2>Receipt Layout Customization</h2>
            <p>Customize printed receipt header and footer texts</p>
          </div>
          <div className="card-body grid-2">
            <div className="form-group">
              <label htmlFor="receiptHeader">Receipt Header</label>
              <textarea
                id="receiptHeader"
                name="receiptHeader"
                value={formData.receiptHeader}
                onChange={handleInputChange}
                disabled={isSaving}
                rows={3}
                maxLength={500}
                placeholder="Text displayed at top of customer receipt (e.g. Restaurant name, Address, GSTIN)"
                className={validationErrors.receiptHeader ? 'is-invalid' : ''}
              />
              <span className="char-count">{formData.receiptHeader.length} / 500 characters</span>
              {validationErrors.receiptHeader && (
                <span className="error-text">{validationErrors.receiptHeader}</span>
              )}
            </div>

            <div className="form-group">
              <label htmlFor="receiptFooter">Receipt Footer</label>
              <textarea
                id="receiptFooter"
                name="receiptFooter"
                value={formData.receiptFooter}
                onChange={handleInputChange}
                disabled={isSaving}
                rows={3}
                maxLength={500}
                placeholder="Text displayed at bottom of customer receipt (e.g. Thank you message, Wifi code, Feedback URL)"
                className={validationErrors.receiptFooter ? 'is-invalid' : ''}
              />
              <span className="char-count">{formData.receiptFooter.length} / 500 characters</span>
              {validationErrors.receiptFooter && (
                <span className="error-text">{validationErrors.receiptFooter}</span>
              )}
            </div>
          </div>
        </div>

        {/* Section 4: Operational Automation & Kitchen Settings */}
        <div className="settings-card">
          <div className="card-header">
            <h2>Kitchen Settings & Operational Workflow</h2>
            <p>Configure Kitchen Display System (KDS) order acceptance workflow</p>
          </div>
          <div className="card-body">
            <div className="toggle-group-card inline-toggle">
              <div className="toggle-header">
                <div>
                  <label htmlFor="autoAcceptOrders" className="toggle-title">Auto-Accept Incoming Orders</label>
                  <p className="toggle-desc">Automatically accept NEW DINE_IN orders after 5 minutes on the server.</p>
                  <div className="toggle-status-info">
                    {formData.autoAcceptOrders ? (
                      <span className="badge-info-green">🟢 5 minute server-side auto acceptance</span>
                    ) : (
                      <span className="badge-info-gray">⚪ Manual kitchen acceptance required</span>
                    )}
                  </div>
                </div>
                <label className="switch">
                  <input
                    type="checkbox"
                    id="autoAcceptOrders"
                    name="autoAcceptOrders"
                    checked={formData.autoAcceptOrders}
                    onChange={handleInputChange}
                    disabled={isSaving}
                  />
                  <span className="slider round"></span>
                </label>
              </div>
            </div>
          </div>
        </div>


        {/* Action Controls */}
        <div className="settings-actions">
          <button
            type="button"
            className="btn-cancel-settings"
            onClick={handleReset}
            disabled={isSaving}
          >
            Reset Changes
          </button>

          <button
            type="submit"
            className="btn-save-settings"
            disabled={isSaving}
          >
            {isSaving ? (
              <>
                <span className="spinner-sm"></span> Saving Changes...
              </>
            ) : (
              '💾 Save Changes'
            )}
          </button>
        </div>
      </form>
    </div>
  );
};

export default SettingsPage;
