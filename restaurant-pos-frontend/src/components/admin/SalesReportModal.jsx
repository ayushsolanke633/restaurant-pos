import React, { useState } from 'react';
import SalesReportPrintDocument from './SalesReportPrintDocument';

export const SalesReportModal = ({
  isOpen,
  onClose,
  onPrint,
  restaurantName,
  restaurantSettings,
  user,
  fromDate,
  toDate,
  salesReport,
  paymentsReport,
  orderTypesReport,
  statusReport,
  topItemsReport,
  categorySalesReport,
  dailySalesBreakdown,
  isMultiDay,
  isPrinting,
}) => {
  const [selectedReportType, setSelectedReportType] = useState('ALL');

  if (!isOpen) return null;

  const handlePrintClick = () => {
    if (onPrint) {
      onPrint(selectedReportType);
    }
  };

  return (
    <div className="modal-overlay modal-backdrop" onClick={onClose} role="dialog" aria-modal="true">
      <div
        className="modal-container modal-content sales-report-modal"
        style={{ maxWidth: '960px', width: '95%' }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="modal-header d-flex justify-content-between align-items-center">
          <div>
            <h3 className="modal-title font-bold m-0">Print Sales Report</h3>
            <p className="text-xs text-muted m-0 mt-1">
              {isMultiDay
                ? `Reporting Period: ${fromDate} to ${toDate}`
                : `Reporting Period: ${fromDate} (Single Day)`}
            </p>
          </div>
          <button
            type="button"
            className="modal-close-btn"
            onClick={onClose}
            aria-label="Close modal"
          >
            Close
          </button>
        </div>

        {/* Body */}
        <div className="modal-body" style={{ maxHeight: '72vh', overflowY: 'auto' }}>
          {/* Controls Bar: Report Type Scope Selection & Print CTA */}
          <div className="sales-report-controls card p-3 mb-3 bg-secondary-subtle">
            <div className="d-flex justify-content-between align-items-center flex-wrap gap-2">
              <div className="d-flex align-items-center gap-2 flex-wrap">
                <label className="text-xs font-semibold uppercase text-muted m-0">
                  Report Scope:
                </label>
                <div className="btn-group d-flex gap-1 flex-wrap">
                  <button
                    type="button"
                    className={`btn btn-xs ${selectedReportType === 'ALL' ? 'btn-primary' : 'btn-secondary'}`}
                    onClick={() => setSelectedReportType('ALL')}
                  >
                    Complete Report
                  </button>
                  <button
                    type="button"
                    className={`btn btn-xs ${selectedReportType === 'SUMMARY' ? 'btn-primary' : 'btn-secondary'}`}
                    onClick={() => setSelectedReportType('SUMMARY')}
                  >
                    Financial & Payments
                  </button>
                  <button
                    type="button"
                    className={`btn btn-xs ${selectedReportType === 'ITEMS_CATEGORIES' ? 'btn-primary' : 'btn-secondary'}`}
                    onClick={() => setSelectedReportType('ITEMS_CATEGORIES')}
                  >
                    Items & Categories
                  </button>
                  <button
                    type="button"
                    className={`btn btn-xs ${selectedReportType === 'ORDER_TYPES' ? 'btn-primary' : 'btn-secondary'}`}
                    onClick={() => setSelectedReportType('ORDER_TYPES')}
                  >
                    Order Types
                  </button>
                  {isMultiDay && (
                    <button
                      type="button"
                      className={`btn btn-xs ${selectedReportType === 'DAILY' ? 'btn-primary' : 'btn-secondary'}`}
                      onClick={() => setSelectedReportType('DAILY')}
                    >
                      Daily Breakdown
                    </button>
                  )}
                </div>
              </div>

              {/* Print Action Buttons */}
              <div className="d-flex gap-2">
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={onClose}
                  disabled={isPrinting}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  className="btn btn-primary btn-sm d-flex align-items-center gap-1 font-bold"
                  onClick={handlePrintClick}
                  disabled={isPrinting}
                >
                  <span>🖨️</span>
                  <span>{isPrinting ? 'Preparing Print...' : 'Print Report'}</span>
                </button>
              </div>
            </div>
          </div>

          {/* Interactive Print Preview */}
          <div className="sales-report-modal-preview card p-4 shadow-sm border">
            <SalesReportPrintDocument
              restaurantName={restaurantName}
              restaurantSettings={restaurantSettings}
              user={user}
              fromDate={fromDate}
              toDate={toDate}
              salesReport={salesReport}
              paymentsReport={paymentsReport}
              orderTypesReport={orderTypesReport}
              statusReport={statusReport}
              topItemsReport={topItemsReport}
              categorySalesReport={categorySalesReport}
              dailySalesBreakdown={dailySalesBreakdown}
              reportType={selectedReportType}
            />
          </div>
        </div>

        {/* Footer */}
        <div className="modal-footer d-flex justify-content-between align-items-center">
          <span className="text-xs text-muted">
            Tip: Select "Save as PDF" as the destination in the print dialog to export a PDF copy.
          </span>
          <div className="d-flex gap-2">
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={isPrinting}>
              Close
            </button>
            <button
              type="button"
              className="btn btn-primary font-bold"
              onClick={handlePrintClick}
              disabled={isPrinting}
            >
              {isPrinting ? 'Printing...' : 'Print Report'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SalesReportModal;
