import React, { useState, useEffect } from 'react';
import billingService from '../../services/billingService';
import ThermalReceipt from './ThermalReceipt';
import { CURRENCY_SYMBOL } from '../../utils/constants';

export const BillModal = ({ order, isOpen, onClose, onPaymentSuccess }) => {
  const [bill, setBill] = useState(null);
  const [loadingBill, setLoadingBill] = useState(false);
  const [generatingBill, setGeneratingBill] = useState(false);
  const [submittingPayment, setSubmittingPayment] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState('CASH');
  const [transactionRef, setTransactionRef] = useState('');
  const [paperWidth, setPaperWidth] = useState('80mm');
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);
  const [showConfirm, setShowConfirm] = useState(false);

  // When modal opens or order changes, fetch bill for this order if available
  useEffect(() => {
    if (isOpen && order) {
      setBill(null);
      setError(null);
      setSuccessMsg(null);
      setShowConfirm(false);
      setPaymentMethod('CASH');
      setTransactionRef('');
      fetchBillForOrder(order.id);
    }
  }, [isOpen, order]);

  // Handle Page Scroll Lock & Escape Key Close
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };

    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    window.addEventListener('keydown', handleKeyDown);

    return () => {
      document.body.style.overflow = originalOverflow || '';
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, onClose]);

  const fetchBillForOrder = async (orderId) => {
    setLoadingBill(true);
    setError(null);
    try {
      const existingBill = await billingService.getBillByOrderId(orderId);
      setBill(existingBill);
    } catch (err) {
      // 404/Bill not found is expected if bill hasn't been generated yet
      setBill(null);
    } finally {
      setLoadingBill(false);
    }
  };

  const handleGenerateBill = async () => {
    if (!order) return;
    setGeneratingBill(true);
    setError(null);
    try {
      const newBill = await billingService.createBill(order.id);
      setBill(newBill);
      setSuccessMsg(`Bill ${newBill.billNumber} generated successfully!`);
    } catch (err) {
      const serverMsg = err.response?.data?.message || err.message || '';
      if (serverMsg.toLowerCase().includes('not in ready status')) {
        setError(`Order #${order.id} is currently ${order.status || 'IN PREPARATION'}. Kitchen must mark the order as READY before generating a bill.`);
      } else if (serverMsg.toLowerCase().includes('already exists')) {
        // Fetch existing bill if already created
        fetchBillForOrder(order.id);
      } else {
        setError(serverMsg || 'Failed to generate bill');
      }
    } finally {
      setGeneratingBill(false);
    }
  };

  const handleProcessPayment = async () => {
    if (!bill) {
      setError('Please generate a bill first before collecting payment.');
      return;
    }

    setSubmittingPayment(true);
    setError(null);
    try {
      const paymentPayload = {
        amount: bill.totalAmount,
        paymentMethod: paymentMethod.toUpperCase(),
        transactionReference: transactionRef.trim() || null,
      };

      const paymentResult = await billingService.processPayment(bill.id, paymentPayload);
      setSuccessMsg(`Payment of ${CURRENCY_SYMBOL}${paymentResult.amount} recorded via ${paymentResult.paymentMethod}. Order COMPLETED and Table Released!`);
      
      setTimeout(() => {
        if (onPaymentSuccess) {
          onPaymentSuccess(paymentResult);
        }
        onClose();
      }, 1200);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to process payment');
      setSubmittingPayment(false);
      setShowConfirm(false);
    }
  };

  const handlePrintReceipt = () => {
    window.print();
  };

  if (!isOpen || !order) return null;

  const orderTypeBadgeClass = order.orderType === 'DINE_IN' ? 'badge-dinein' : 'badge-takeaway';

  return (
    <div
      className="modal-overlay modal-backdrop"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-labelledby="bill-modal-title"
    >
      <div
        className="modal-container modal-content bill-modal"
        style={{ maxWidth: '680px' }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <div className="bill-modal-title">
            <h3 id="bill-modal-title">Billing & Thermal Receipt — Order #{order.id}</h3>
            <span className={`badge ${orderTypeBadgeClass}`}>
              {order.orderType === 'DINE_IN' ? `Dine-In (${order.tableName || `Table ${order.tableNumber || ''}`})` : 'Takeaway'}
            </span>
          </div>
          <button className="modal-close-btn" onClick={onClose} aria-label="Close modal">Close</button>
        </div>

        <div className="modal-body">
          {error && <div className="alert alert-danger mb-3">{error}</div>}
          {successMsg && <div className="alert alert-success mb-3">{successMsg}</div>}

          {/* Loading state for fetching bill */}
          {loadingBill ? (
            <div className="text-center py-4">
              <div className="spinner"></div>
              <p className="text-muted mt-2">Checking bill status...</p>
            </div>
          ) : (
            <>
              {/* Paper Width Selector & Print Button */}
              {bill && (
                <div className="d-flex justify-content-between align-items-center mb-3">
                  <div className="paper-width-toggle m-0">
                    <button
                      type="button"
                      className={`toggle-btn ${paperWidth === '80mm' ? 'active' : ''}`}
                      onClick={() => setPaperWidth('80mm')}
                    >
                      80mm Receipt
                    </button>
                    <button
                      type="button"
                      className={`toggle-btn ${paperWidth === '58mm' ? 'active' : ''}`}
                      onClick={() => setPaperWidth('58mm')}
                    >
                      58mm Receipt
                    </button>
                  </div>

                  <button
                    type="button"
                    className="btn btn-secondary btn-sm"
                    onClick={handlePrintReceipt}
                    title="Print Thermal Receipt"
                  >
                    Print Receipt
                  </button>
                </div>
              )}

              {/* Thermal Receipt Preview Area */}
              <div className="printable-receipt-area mb-4">
                <ThermalReceipt
                  bill={bill}
                  order={order}
                  paperWidth={paperWidth}
                />
              </div>

              {/* Payment Method Selector & Controls (Enabled only if Bill exists) */}
              {bill ? (
                order.status === 'COMPLETED' ? (
                  <div className="alert alert-success text-center py-3 font-weight-bold mb-0">
                    Payment Recorded & Order Completed ({CURRENCY_SYMBOL}{Number(bill.totalAmount).toFixed(2)})
                  </div>
                ) : (
                  <div className="payment-controls-section card p-3">
                    <h4 className="section-title mb-3">Select Payment Method & Process</h4>
                    <div className="payment-method-selector mb-3">
                      <button
                        type="button"
                        className={`payment-method-btn ${paymentMethod === 'CASH' ? 'selected' : ''}`}
                        onClick={() => setPaymentMethod('CASH')}
                      >
                        <span className="method-label">CASH</span>
                      </button>
                      <button
                        type="button"
                        className={`payment-method-btn ${paymentMethod === 'UPI' ? 'selected' : ''}`}
                        onClick={() => setPaymentMethod('UPI')}
                      >
                        <span className="method-label">UPI</span>
                      </button>
                      <button
                        type="button"
                        className={`payment-method-btn ${paymentMethod === 'CARD' ? 'selected' : ''}`}
                        onClick={() => setPaymentMethod('CARD')}
                      >
                        <span className="method-label">CARD</span>
                      </button>
                    </div>

                    {/* Optional Reference Input for UPI or CARD */}
                    {(paymentMethod === 'UPI' || paymentMethod === 'CARD') && (
                      <div className="form-group mb-3">
                        <label className="form-label">
                          {paymentMethod === 'UPI' ? 'UPI Reference / Transaction ID (Optional)' : 'Card Approval / Ref # (Optional)'}
                        </label>
                        <input
                          type="text"
                          className="form-input"
                          placeholder={paymentMethod === 'UPI' ? 'e.g. 123456789012' : 'e.g. AUTH-45892'}
                          value={transactionRef}
                          onChange={(e) => setTransactionRef(e.target.value)}
                          disabled={submittingPayment}
                        />
                      </div>
                    )}

                    {/* Confirmation Box */}
                    {showConfirm ? (
                      <div className="payment-confirm-box p-3 border-accent">
                        <div className="confirm-text mb-2">
                          <strong>Confirm Payment Collection?</strong>
                          <p className="m-0 text-muted">
                            Collect <strong>{CURRENCY_SYMBOL}{Number(bill.totalAmount).toFixed(2)}</strong> via <strong>{paymentMethod}</strong> for Bill #{bill.billNumber}?
                          </p>
                        </div>
                        <div className="d-flex gap-2">
                          <button
                            type="button"
                            className="btn btn-success flex-1"
                            onClick={handleProcessPayment}
                            disabled={submittingPayment}
                          >
                            {submittingPayment ? (
                              <>
                                <span className="spinner-sm"></span> Processing...
                              </>
                            ) : (
                              'Confirm & Process Payment'
                            )}
                          </button>
                          <button
                            type="button"
                            className="btn btn-secondary"
                            onClick={() => setShowConfirm(false)}
                            disabled={submittingPayment}
                          >
                            Cancel
                          </button>
                        </div>
                      </div>
                    ) : (
                      <button
                        type="button"
                        className="btn btn-primary btn-block btn-lg"
                        onClick={() => setShowConfirm(true)}
                        disabled={submittingPayment}
                      >
                        Record {paymentMethod} Payment ({CURRENCY_SYMBOL}{Number(bill.totalAmount).toFixed(2)})
                      </button>
                    )}
                  </div>
                )
              ) : (
                <div className="text-center mt-3">
                  {order.status !== 'READY' && (
                    <div className="alert alert-warning mb-3">
                      Order is currently <strong>{order.status}</strong>. If kitchen has completed food preparation, you can generate official bill below.
                    </div>
                  )}
                  <button
                    type="button"
                    className="btn btn-primary btn-lg btn-block"
                    onClick={handleGenerateBill}
                    disabled={generatingBill}
                  >
                    {generatingBill ? (
                      <>
                        <span className="spinner-sm"></span> Generating Bill...
                      </>
                    ) : (
                      'Generate Official Bill'
                    )}
                  </button>
                </div>
              )}
            </>
          )}
        </div>

        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={onClose} disabled={submittingPayment}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
};

export default BillModal;
