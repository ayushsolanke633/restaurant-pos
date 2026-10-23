import React, { useState, useEffect, useCallback } from 'react';
import useAutoDismiss from '../../hooks/useAutoDismiss';
import customerService from '../../services/customerService';
import CustomerModal from '../../components/admin/CustomerModal';
import CustomerDetailsModal from '../../components/admin/CustomerDetailsModal';

export const CustomersPage = () => {
  const [customers, setCustomers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  useAutoDismiss(successMsg, () => setSuccessMsg(''));

  // Search state
  const [searchTerm, setSearchTerm] = useState('');

  // Modals state
  const [isCustomerModalOpen, setIsCustomerModalOpen] = useState(false);
  const [selectedCustomerForEdit, setSelectedCustomerForEdit] = useState(null);

  const [isDetailsModalOpen, setIsDetailsModalOpen] = useState(false);
  const [selectedCustomerForDetails, setSelectedCustomerForDetails] = useState(null);

  const fetchCustomers = useCallback(async (query = '') => {
    setLoading(true);
    setError('');
    try {
      let data = [];
      if (query.trim()) {
        data = await customerService.searchCustomers(query.trim());
      } else {
        data = await customerService.getAllCustomers();
      }
      setCustomers(data || []);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to fetch customer list');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchCustomers(searchTerm);
  }, [searchTerm, fetchCustomers]);

  const handleAddClick = () => {
    setSelectedCustomerForEdit(null);
    setIsCustomerModalOpen(true);
  };

  const handleEditClick = (customer) => {
    setSelectedCustomerForEdit(customer);
    setIsCustomerModalOpen(true);
  };

  const handleDetailsClick = (customer) => {
    setSelectedCustomerForDetails(customer);
    setIsDetailsModalOpen(true);
  };

  const formatDate = (isoString) => {
    if (!isoString) return '—';
    try {
      const date = new Date(isoString);
      return date.toLocaleDateString('en-IN', {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
      });
    } catch {
      return isoString;
    }
  };

  // Metric counts
  const totalCustomers = customers.length;
  const customersWithPhone = customers.filter((c) => Boolean(c.phone)).length;
  const customersWithEmail = customers.filter((c) => Boolean(c.email)).length;

  return (
    <div className="admin-page customers-page">
      {/* Page Header */}
      <div className="page-header">
        <div className="header-title">
          <h1>Customer Management</h1>
          <p className="subtitle">Manage restaurant customer directory, contact information, and delivery addresses</p>
        </div>
        <div className="header-actions">
          <button className="btn btn-primary" onClick={handleAddClick}>
            Add Customer
          </button>
        </div>
      </div>

      {successMsg && (
        <div className="alert alert-success alert-dismissible mb-4">
          <span>{successMsg}</span>
          <button className="alert-close" onClick={() => setSuccessMsg('')}>Close</button>
        </div>
      )}

      {error && <div className="alert alert-danger mb-4">{error}</div>}

      {/* Metrics Summary Grid */}
      <div className="metrics-grid mb-4">
        <div className="metric-card card">
          <div className="metric-header">
            <span className="metric-title">Total Customers</span>
          </div>
          <div className="metric-value">{totalCustomers}</div>
        </div>

        <div className="metric-card card">
          <div className="metric-header">
            <span className="metric-title">With Phone Number</span>
          </div>
          <div className="metric-value text-info">{customersWithPhone}</div>
        </div>

        <div className="metric-card card">
          <div className="metric-header">
            <span className="metric-title">With Email Address</span>
          </div>
          <div className="metric-value text-success">{customersWithEmail}</div>
        </div>
      </div>

      {/* Toolbar / Search */}
      <div className="billing-toolbar mb-4">
        <div className="d-flex gap-2 flex-wrap flex-1">
          <div className="search-box">
            <input
              type="text"
              className="form-input search-input"
              placeholder="Search customers by name, phone, or email..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              style={{ paddingLeft: '1rem' }}
            />
          </div>
        </div>

        <button
          className="btn btn-secondary btn-icon ml-auto"
          onClick={() => fetchCustomers(searchTerm)}
          title="Refresh Customer List"
        >
          Refresh
        </button>
      </div>

      {/* Customers Data Table */}
      {loading ? (
        <div className="loading-container py-5 text-center">
          <div className="spinner mb-3"></div>
          <p className="text-muted">Loading customer directory...</p>
        </div>
      ) : customers.length === 0 ? (
        <div className="empty-state card p-5 text-center">
          <h3>No Customers Found</h3>
          <p className="text-muted m-0">
            {searchTerm
              ? `No customers match your search for "${searchTerm}".`
              : 'Click "Add Customer" to create your first customer profile.'}
          </p>
        </div>
      ) : (
        <div className="table-responsive card">
          <table className="data-table">
            <thead>
              <tr>
                <th>Customer Name</th>
                <th>Phone Number</th>
                <th>Email Address</th>
                <th>Address</th>
                <th>Registered On</th>
                <th className="text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {customers.map((c) => (
                <tr key={c.id}>
                  <td>
                    <div className="font-semibold text-main">{c.name}</div>
                  </td>
                  <td>
                    {c.phone ? (
                      <span className="font-medium text-main">{c.phone}</span>
                    ) : (
                      <span className="text-muted">—</span>
                    )}
                  </td>
                  <td>
                    {c.email ? (
                      <span className="text-main">{c.email}</span>
                    ) : (
                      <span className="text-muted">—</span>
                    )}
                  </td>
                  <td>
                    {c.address ? (
                      <span className="text-sm text-main truncate-text" title={c.address}>
                        {c.address}
                      </span>
                    ) : (
                      <span className="text-muted">—</span>
                    )}
                  </td>
                  <td>
                    <span className="text-xs text-muted">{formatDate(c.createdAt)}</span>
                  </td>
                  <td className="text-right">
                    <div className="d-flex gap-2 justify-content-end align-items-center">
                      <button
                        className="btn btn-secondary btn-sm"
                        onClick={() => handleDetailsClick(c)}
                        title="View Customer Profile Details"
                      >
                        Details
                      </button>

                      <button
                        className="btn btn-secondary btn-sm"
                        onClick={() => handleEditClick(c)}
                        title="Edit Customer Profile"
                      >
                        Edit
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Customer Form Modal (Add / Edit) */}
      <CustomerModal
        customer={selectedCustomerForEdit}
        isOpen={isCustomerModalOpen}
        onClose={() => setIsCustomerModalOpen(false)}
        onSuccess={() => {
          setSuccessMsg(
            selectedCustomerForEdit
              ? 'Customer updated successfully.'
              : 'New customer created successfully.'
          );
          fetchCustomers(searchTerm);
        }}
      />

      {/* Customer Details Modal */}
      <CustomerDetailsModal
        customer={selectedCustomerForDetails}
        isOpen={isDetailsModalOpen}
        onClose={() => setIsDetailsModalOpen(false)}
      />
    </div>
  );
};

export default CustomersPage;
