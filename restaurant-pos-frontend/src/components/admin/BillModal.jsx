import React, { useState, useEffect, useCallback, useRef } from 'react';
import useAutoDismiss from '../../hooks/useAutoDismiss';
import billingService from '../../services/billingService';
import ThermalReceipt from './ThermalReceipt';
import { CURRENCY_SYMBOL } from '../../utils/constants';

export const BillModal = ({ order, isOpen, onClose, onPaymentSuccess, onBillGenerated }) => {
  const [bill, setBill] = useState(null);
  const [loadingBill, setLoadingBill] = useState(false);
  const [generatingBill, setGeneratingBill] = useState(false);
  const [submittingPayment, setSubmittingPayment] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState('CASH');
  const [transactionRef, setTransactionRef] = useState('');
  const [paperWidth, setPaperWidth] = useState('80mm');
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);
  const [paymentCompleted, setPaymentCompleted] = useState(false);

  useAutoDismiss(successMsg, () => setSuccessMsg(null));

  // Stable callback refs to avoid re-triggering effects on parent re-render
  const onBillGeneratedRef = useRef(onBillGenerated);
  const onPaymentSuccessRef = useRef(onPaymentSuccess);
  const initializeInProgressRef = useRef(false);

  useEffect(() => {
    onBillGeneratedRef.current = onBillGenerated;
    onPaymentSuccessRef.current = onPaymentSuccess;
  });

  const resetModalState = () => {
    setBill(null);
    setLoadingBill(false);
    setGeneratingBill(false);
    setSubmittingPayment(false);
    setPaymentMethod('CASH');
    setTransactionRef('');
    setError(null);
    setSuccessMsg(null);
    setPaymentCompleted(false);
    initializeInProgressRef.current = false;
  };

  const handleClose = useCallback(() => {
    if (submittingPayment || generatingBill) return;
    onClose();
  }, [submittingPayment, generatingBill, onClose]);

  // Initialize or fetch bill in the background when modal opens
  const initializeBill = useCallback(async (orderToBill) => {
    if (!orderToBill || !orderToBill.id || initializeInProgressRef.current) return;
    initializeInProgressRef.current = true;
    setLoadingBill(true);
    setError(null);
    setSuccessMsg(null);

    const isAlreadyCompleted = orderToBill.status === 'COMPLETED';
    setPaymentCompleted(isAlreadyCompleted);

    try {
      // 1. Check if a bill already exists for this order
      let existingBill = null;
      try {
        existingBill = await billingService.getBillByOrderId(orderToBill.id);
      } catch {
        // Expected 404 if bill has not yet been generated
        existingBill = null;
      }

      if (existingBill) {
        setBill(existingBill);
        return;
      }

      // If order is completed or cancelled, do not attempt to create a bill
      if (isAlreadyCompleted || orderToBill.status === 'CANCELLED') {
        setBill(null);
        return;
      }

      // 2. Automatically generate bill in background using existing billing workflow
      setGeneratingBill(true);
      const newBill = await billingService.createBill(orderToBill.id);
      setBill(newBill);
      if (onBillGeneratedRef.current) {
        onBillGeneratedRef.current(newBill, orderToBill);
      }
    } catch (err) {
      const serverMsg = err.response?.data?.message || err.message || '';
      if (serverMsg.toLowerCase().includes('already exists')) {
        // Fallback: If bill already exists, fetch it
        try {
          const existing = await billingService.getBillByOrderId(orderToBill.id);
          setBill(existing);
        } catch {
          setError(serverMsg);
        }
      } else if (serverMsg.toLowerCase().includes('not in ready status')) {
        setBill(null);
        setError(
          `Order #${orderToBill.id} is currently ${orderToBill.status || 'IN PREPARATION'}. All kitchen batches must be marked as READY before generating a bill.`
        );
      } else {
        setBill(null);
        setError(serverMsg || 'Failed to initialize bill');
      }
    } finally {
      initializeInProgressRef.current = false;
      setLoadingBill(false);
      setGeneratingBill(false);
    }
  }, []);

  // When modal opens or order changes, initialize bill automatically
  useEffect(() => {
    if (isOpen && order?.id) {
      setPaymentMethod('CASH');
      setTransactionRef('');
      initializeBill(order);
    } else if (!isOpen) {
      resetModalState();
    }
  }, [isOpen, order, initializeBill]);

  // Handle Page Scroll Lock & Escape Key Close
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        handleClose();
      }
    };

    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    window.addEventListener('keydown', handleKeyDown);

    return () => {
      document.body.style.overflow = originalOverflow || '';
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, handleClose]);

  // Process payment directly without extra confirmation panel
  const handleProcessPayment = async () => {
    if (!bill || submittingPayment) {
      if (!bill) setError('Please wait for bill initialization before collecting payment.');
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
      setPaymentCompleted(true);
      setSuccessMsg(
        `Payment of ${CURRENCY_SYMBOL}${Number(paymentResult.amount).toFixed(2)} recorded via ${paymentResult.paymentMethod}. Order COMPLETED and Table Released!`
      );

      if (onPaymentSuccessRef.current) {
        onPaymentSuccessRef.current(paymentResult);
      }
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Failed to process payment';
      setError(msg);
    } finally {
      setSubmittingPayment(false);
    }
  };

  const handlePrintReceipt = () => {
    window.print();
  };

  if (!isOpen || !order) return null;

  const orderTypeBadgeClass = order.orderType === 'DINE_IN' ? 'badge-dinein' : 'badge-takeaway';
  const isOrderCompleted = order.status === 'COMPLETED' || paymentCompleted;

  return (
    <div
      className="modal-overlay modal-backdrop"
      onClick={handleClose}
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
              {order.orderType === 'DINE_IN'
                ? `Dine-In (${order.tableName || `Table ${order.tableNumber || ''}`})`
                : 'Takeaway'}
            </span>
          </div>
          <button className="modal-close-btn" onClick={handleClose} aria-label="Close modal">
            Close
          </button>
        </div>

        <div className="modal-body">
          {error && <div className="alert alert-danger mb-3">{error}</div>}
          {successMsg && (
            <div className="alert alert-success alert-dismissible mb-3">
              <span>{successMsg}</span>
              <button
                type="button"
                className="alert-close"
                onClick={() => setSuccessMsg(null)}
              >
                Close
              </button>
            </div>
          )}

          {/* Loading state for fetching/generating bill */}
          {loadingBill || generatingBill ? (
            <div className="text-center py-4">
              <div className="spinner"></div>
              <p className="text-muted mt-2">
                {generatingBill ? 'Generating bill...' : 'Checking bill status...'}
              </p>
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
                <ThermalReceipt bill={bill} order={order} paperWidth={paperWidth} />
              </div>

              {/* Payment Method Selector & Controls (Enabled only if Bill exists) */}
              {bill ? (
                isOrderCompleted ? (
                  <div className="alert alert-success text-center py-3 font-weight-bold mb-0">
                    Payment Recorded & Order Completed ({CURRENCY_SYMBOL}
                    {Number(bill.totalAmount).toFixed(2)})
                  </div>
                ) : (
                  <div className="payment-controls-section card p-3">
                    <h4 className="section-title mb-3">Select Payment Method & Process</h4>
                    <div className="payment-method-selector mb-3">
                      <button
                        type="button"
                        className={`payment-method-btn ${paymentMethod === 'CASH' ? 'selected' : ''}`}
                        onClick={() => setPaymentMethod('CASH')}
                        disabled={submittingPayment}
                      >
                        <span className="method-label">CASH</span>
                      </button>
                      <button
                        type="button"
                        className={`payment-method-btn ${paymentMethod === 'UPI' ? 'selected' : ''}`}
                        onClick={() => setPaymentMethod('UPI')}
                        disabled={submittingPayment}
                      >
                        <span className="method-label">UPI</span>
                      </button>
                      <button
                        type="button"
                        className={`payment-method-btn ${paymentMethod === 'CARD' ? 'selected' : ''}`}
                        onClick={() => setPaymentMethod('CARD')}
                        disabled={submittingPayment}
                      >
                        <span className="method-label">CARD</span>
                      </button>
                    </div>

                    {/* Optional Reference Input for UPI or CARD */}
                    {(paymentMethod === 'UPI' || paymentMethod === 'CARD') && (
                      <div className="form-group mb-3">
                        <label className="form-label">
                          {paymentMethod === 'UPI'
                            ? 'UPI Reference / Transaction ID (Optional)'
                            : 'Card Approval / Ref # (Optional)'}
                        </label>
                        <input
                          type="text"
                          className="form-input"
                          placeholder={
                            paymentMethod === 'UPI' ? 'e.g. 123456789012' : 'e.g. AUTH-45892'
                          }
                          value={transactionRef}
                          onChange={(e) => setTransactionRef(e.target.value)}
                          disabled={submittingPayment}
                        />
                      </div>
                    )}

                    {/* Direct Single Primary Action Button */}
                    <button
                      type="button"
                      className="btn btn-primary btn-block btn-lg"
                      onClick={handleProcessPayment}
                      disabled={submittingPayment || !bill}
                      id="collect-payment-btn"
                    >
                      {submittingPayment ? (
                        <>
                          <span className="spinner-sm"></span> Processing Payment...
                        </>
                      ) : (
                        `Collect Payment & Complete (${CURRENCY_SYMBOL}${Number(bill.totalAmount).toFixed(2)})`
                      )}
                    </button>
                  </div>
                )
              ) : (
                <div className="text-center mt-3">
                  {order.status !== 'READY' && (
                    <div className="alert alert-warning mb-3">
                      Order is currently <strong>{order.status}</strong>. Ensure all kitchen batches are completed and marked as READY before generating bill.
                    </div>
                  )}
                  <button
                    type="button"
                    className="btn btn-primary btn-lg btn-block"
                    onClick={() => initializeBill(order)}
                    disabled={generatingBill || loadingBill}
                  >
                    {generatingBill ? (
                      <>
                        <span className="spinner-sm"></span> Generating Bill...
                      </>
                    ) : (
                      'Retry Generating Bill'
                    )}
                  </button>
                </div>
              )}
            </>
          )}
        </div>

        <div className="modal-footer">
          <button
            className="btn btn-secondary"
            onClick={handleClose}
            disabled={submittingPayment || generatingBill}
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};

export default BillModal;
