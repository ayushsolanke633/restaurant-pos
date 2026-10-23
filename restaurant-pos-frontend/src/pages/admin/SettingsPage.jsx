import React, { useState, useEffect } from 'react';
import useAutoDismiss from '../../hooks/useAutoDismiss';
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
    kitchenEnabled: true,
  });

  const [showKitchenModal, setShowKitchenModal] = useState(false);
  const [pendingKitchenEnabled, setPendingKitchenEnabled] = useState(true);

  const [loading, setLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);
  const [validationErrors, setValidationErrors] = useState({});

  useAutoDismiss(successMessage, () => setSuccessMessage(null));

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
      kitchenEnabled: data.kitchenEnabled ?? true,
    });
    setPendingKitchenEnabled(data.kitchenEnabled ?? true);
    setValidationErrors({});
  };

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

  useEffect(() => {
    fetchSettings();
  }, []);

  const handleInputChange = (e) => {
    const { name, value, type, checked } = e.target;
    const val = type === 'checkbox' ? checked : value;

    if (name === 'autoAcceptOrders' && val && !formData.kitchenEnabled) {
      setError('Kitchen Display System (KDS) must be enabled before Auto-Accept Orders can be turned on.');
      setSuccessMessage(null);
      return;
    }

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

  const handleAutoAcceptToggle = (e) => {
    const nextVal = e.target.checked;
    if (nextVal && !formData.kitchenEnabled) {
      e.target.checked = false;
      setError('Kitchen Display System (KDS) must be enabled before Auto-Accept Orders can be turned on.');
      setSuccessMessage(null);
      return;
    }
    setFormData((prev) => ({
      ...prev,
      autoAcceptOrders: nextVal,
    }));
    if (validationErrors.autoAcceptOrders) {
      setValidationErrors((prev) => ({ ...prev, autoAcceptOrders: null }));
    }
    setError(null);
    setSuccessMessage(null);
  };

  const handleAutoAcceptWrapperClick = (e) => {
    if (!formData.kitchenEnabled) {
      e.preventDefault();
      setError('Kitchen Display System (KDS) must be enabled before Auto-Accept Orders can be turned on.');
      setSuccessMessage(null);
    }
  };

  const handleKitchenModeToggle = (e) => {
    const nextVal = e.target.checked;
    setPendingKitchenEnabled(nextVal);
    setShowKitchenModal(true);
  };

  const confirmKitchenModeChange = async () => {
    const nextVal = pendingKitchenEnabled;
    setIsSaving(true);
    setError(null);
    setSuccessMessage(null);
    try {
      const updatedResponse = await settingsService.updateSettings({
        kitchenEnabled: nextVal,
      });
      setInitialSettings(updatedResponse);
      setFormData((prev) => ({
        ...prev,
        kitchenEnabled: updatedResponse.kitchenEnabled ?? nextVal,
        autoAcceptOrders: updatedResponse.autoAcceptOrders ?? (nextVal ? prev.autoAcceptOrders : false),
      }));
      setPendingKitchenEnabled(updatedResponse.kitchenEnabled ?? nextVal);
      setShowKitchenModal(false);
      setSuccessMessage(
        nextVal
          ? 'Kitchen Display System (KDS) Workflow enabled successfully.'
          : 'Kitchen-Free Mode enabled successfully. Auto-Accept Orders has been automatically turned OFF.'
      );
    } catch (err) {
      console.error('Failed to update kitchen mode setting:', err);
      const backendMessage = err.response?.data?.message || err.message;
      setError(backendMessage || 'Failed to update kitchen mode setting. Please try again.');
      setShowKitchenModal(false);
      setPendingKitchenEnabled(formData.kitchenEnabled);
    } finally {
      setIsSaving(false);
    }
  };

  const cancelKitchenModeChange = () => {
    if (isSaving) return;
    setShowKitchenModal(false);
    setPendingKitchenEnabled(formData.kitchenEnabled);
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

    // Auto-Accept Orders dependency validation
    if (formData.autoAcceptOrders && !formData.kitchenEnabled) {
      errors.autoAcceptOrders = 'Kitchen Display System (KDS) must be enabled before Auto-Accept Orders can be turned on';
    }

    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSuccessMessage(null);
    setError(null);

    if (formData.autoAcceptOrders && !formData.kitchenEnabled) {
      setError('Kitchen Display System (KDS) must be enabled before Auto-Accept Orders can be turned on.');
      return;
    }

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
      kitchenEnabled: formData.kitchenEnabled,
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

        {/* Section 4: Order & Kitchen Settings */}
        <div className="settings-card">
          <div className="card-header">
            <h2>Order & Kitchen Settings</h2>
            <p>Configure Kitchen Display System (KDS) order workflows and operational automation</p>
          </div>
          <div className="card-body">
            {/* Kitchen Mode Setting (KDS vs Kitchen-Free) */}
            <div className="toggle-group-card inline-toggle mb-4">
              <div className="toggle-header">
                <div>
                  <label htmlFor="kitchenEnabled" className="toggle-title">Kitchen Display System (KDS) Workflow</label>
                  <p className="toggle-desc">
                    {formData.kitchenEnabled
                      ? 'Enabled: Full KDS workflow. Orders are dispatched to the kitchen queue and must be marked READY before billing.'
                      : 'Disabled: Kitchen-free operation. Orders bypass kitchen status requirements and can be billed directly.'}
                  </p>
                  <div className="toggle-status-info">
                    {formData.kitchenEnabled ? (
                      <span className="badge-info-green">🟢 Kitchen Mode Enabled (KDS active; READY required for billing)</span>
                    ) : (
                      <span className="badge-info-gray">⚪ Kitchen-Free Mode (Direct billing enabled without KDS)</span>
                    )}
                  </div>
                </div>
                <label className="switch">
                  <input
                    type="checkbox"
                    id="kitchenEnabled"
                    name="kitchenEnabled"
                    checked={formData.kitchenEnabled}
                    onChange={handleKitchenModeToggle}
                    disabled={isSaving}
                  />
                  <span className="slider round"></span>
                </label>
              </div>
            </div>

            {/* Auto Accept Incoming Orders */}
            <div
              className={`toggle-group-card inline-toggle ${!formData.kitchenEnabled ? 'disabled-toggle-card' : ''}`}
              onClick={!formData.kitchenEnabled ? handleAutoAcceptWrapperClick : undefined}
            >
              <div className="toggle-header">
                <div>
                  <label htmlFor="autoAcceptOrders" className="toggle-title">Auto-Accept Incoming Orders</label>
                  <p className="toggle-desc">Automatically accept NEW DINE_IN orders after 5 minutes on the server.</p>
                  <div className="toggle-status-info">
                    {!formData.kitchenEnabled ? (
                      <span className="badge-info-amber">⚠️ Disabled: Kitchen Display System (KDS) must be enabled first</span>
                    ) : formData.autoAcceptOrders ? (
                      <span className="badge-info-green">🟢 5 minute server-side auto acceptance</span>
                    ) : (
                      <span className="badge-info-gray">⚪ Manual kitchen acceptance required</span>
                    )}
                  </div>
                </div>
                <label
                  className="switch"
                  title={
                    !formData.kitchenEnabled
                      ? 'Kitchen Display System (KDS) must be enabled before Auto-Accept Orders can be turned on'
                      : (formData.autoAcceptOrders ? 'Disable Auto-Accept' : 'Enable Auto-Accept')
                  }
                >
                  <input
                    type="checkbox"
                    id="autoAcceptOrders"
                    name="autoAcceptOrders"
                    checked={formData.autoAcceptOrders}
                    onChange={handleAutoAcceptToggle}
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

      {/* Confirmation Modal for Kitchen Mode Switch */}
      {showKitchenModal && (
        <div
          className="modal-overlay modal-backdrop"
          onClick={isSaving ? undefined : cancelKitchenModeChange}
          role="dialog"
          aria-modal="true"
          aria-labelledby="kitchen-modal-title"
        >
          <div
            className="modal-container modal-content"
            style={{ maxWidth: '520px' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header">
              <h3 id="kitchen-modal-title">Confirm Kitchen Mode Change</h3>
              <button
                type="button"
                className="modal-close-btn"
                onClick={cancelKitchenModeChange}
                disabled={isSaving}
                aria-label="Close confirmation dialog"
              >
                ×
              </button>
            </div>

            <div className="modal-body">
              {pendingKitchenEnabled ? (
                <div>
                  <p className="mb-2">
                    You are switching to <strong>Kitchen-Enabled (KDS) Mode</strong>.
                  </p>
                  <p className="text-muted mb-3" style={{ fontSize: '0.95rem' }}>
                    Orders will be dispatched to kitchen display screens and must reach <strong>READY</strong> status before bills can be generated.
                  </p>
                </div>
              ) : (
                <div>
                  <p className="mb-2">
                    You are switching to <strong>Kitchen-Free Mode</strong>.
                  </p>
                  <p className="text-muted mb-3" style={{ fontSize: '0.95rem' }}>
                    Active orders can be billed directly by administrators without requiring kitchen status transitions. Existing staff accounts, historical orders, bills, and payments will be preserved.
                  </p>
                  <div className="alert alert-info mb-2" style={{ fontSize: '0.9rem', backgroundColor: '#e0f2fe', borderColor: '#bae6fd', color: '#0369a1', padding: '0.6rem 0.75rem', borderRadius: '6px' }}>
                    <strong>Note:</strong> Auto-Accept Incoming Orders will automatically be turned <strong>OFF</strong>.
                  </div>
                </div>
              )}
              <div className="alert alert-warning mb-0" style={{ fontSize: '0.9rem' }}>
                <strong>Important:</strong> Active in-progress orders are not cancelled or reset. Ensure your team is aligned before saving.
              </div>
            </div>

            <div className="modal-footer" style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '1rem' }}>
              <button
                type="button"
                className="btn btn-secondary"
                onClick={cancelKitchenModeChange}
                disabled={isSaving}
              >
                Cancel
              </button>
              <button
                type="button"
                className="btn btn-primary"
                onClick={confirmKitchenModeChange}
                disabled={isSaving}
              >
                {isSaving ? (
                  <>
                    <span className="spinner-sm"></span> Updating Mode...
                  </>
                ) : (
                  'Confirm Mode Change'
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default SettingsPage;
