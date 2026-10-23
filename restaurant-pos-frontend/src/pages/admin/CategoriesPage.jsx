import React, { useState, useEffect, useCallback } from 'react';
import useAutoDismiss from '../../hooks/useAutoDismiss';
import categoryService from '../../services/categoryService';
import CategoryModal from '../../components/admin/CategoryModal';

export const CategoriesPage = () => {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  useAutoDismiss(successMsg, () => setSuccessMsg(''));
  
  // Modal state
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [actionLoadingId, setActionLoadingId] = useState(null);

  const fetchCategories = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await categoryService.getCategories();
      setCategories(Array.isArray(data) ? data : []);
    } catch (err) {
      if (err.response) {
        if (err.response.status === 403) {
          setError('Access Denied: You do not have permission to view categories.');
        } else {
          setError(err.response.data?.message || 'Failed to load categories. Please try again.');
        }
      } else {
        setError('Unable to connect to the server. Please check your network connection.');
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchCategories();
  }, [fetchCategories]);

  // Handle open add modal
  const handleOpenAddModal = () => {
    setEditingCategory(null);
    setIsModalOpen(true);
  };

  // Handle open edit modal
  const handleOpenEditModal = (cat) => {
    setEditingCategory(cat);
    setIsModalOpen(true);
  };

  // Handle submit form (Create or Update)
  const handleFormSubmit = async (formData) => {
    setIsSubmitting(true);
    setError('');
    setSuccessMsg('');

    try {
      if (editingCategory) {
        const updated = await categoryService.updateCategory(editingCategory.id, formData);
        setCategories((prev) =>
          prev.map((c) => (c.id === editingCategory.id ? updated : c))
        );
        setSuccessMsg(`Category "${updated.name}" updated successfully.`);
      } else {
        const created = await categoryService.createCategory(formData);
        setCategories((prev) => [...prev, created]);
        setSuccessMsg(`Category "${created.name}" created successfully.`);
      }
      setIsModalOpen(false);
    } catch (err) {
      if (err.response) {
        if (err.response.status === 403) {
          setError('You do not have permission to perform this action.');
        } else {
          setError(err.response.data?.message || 'Failed to save category.');
        }
      } else {
        setError('Unable to connect to server. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  // Handle Toggle Active/Inactive status
  const handleToggleStatus = async (cat) => {
    const isCurrentlyActive = cat.isActive;
    const actionName = isCurrentlyActive ? 'deactivate' : 'activate';
    
    if (
      !window.confirm(
        `Are you sure you want to ${actionName} category "${cat.name}"?`
      )
    ) {
      return;
    }

    setActionLoadingId(cat.id);
    setError('');
    setSuccessMsg('');

    try {
      let updated;
      if (isCurrentlyActive) {
        updated = await categoryService.deactivateCategory(cat.id);
      } else {
        updated = await categoryService.activateCategory(cat.id);
      }
      setCategories((prev) =>
        prev.map((c) => (c.id === cat.id ? updated : c))
      );
      setSuccessMsg(
        `Category "${updated.name}" is now ${
          updated.isActive ? 'Active' : 'Inactive'
        }.`
      );
    } catch (err) {
      if (err.response) {
        setError(err.response.data?.message || `Failed to ${actionName} category.`);
      } else {
        setError('Unable to connect to server. Please try again.');
      }
    } finally {
      setActionLoadingId(null);
    }
  };

  // Helper date formatter
  const formatDate = (dateStr) => {
    if (!dateStr) return '—';
    try {
      const date = new Date(dateStr);
      return date.toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      });
    } catch {
      return dateStr;
    }
  };

  return (
    <div className="admin-page-container">
      {/* Header Banner */}
      <div className="page-header-banner">
        <div className="header-text">
          <h1 className="page-title">Categories</h1>
          <p className="page-subtitle">
            Manage food & beverage categories for your restaurant menu
          </p>
        </div>
        <button className="btn btn-primary" onClick={handleOpenAddModal}>
          Add Category
        </button>
      </div>

      {/* Alerts */}
      {error && (
        <div className="alert alert-danger" role="alert">
          <span>{error}</span>
          <button
            type="button"
            className="alert-dismiss"
            onClick={() => setError('')}
          >
            Close
          </button>
        </div>
      )}

      {successMsg && (
        <div className="alert alert-success" role="alert">
          <span>{successMsg}</span>
          <button
            type="button"
            className="alert-dismiss"
            onClick={() => setSuccessMsg('')}
          >
            Close
          </button>
        </div>
      )}

      {/* Main Content Area */}
      {loading ? (
        <div className="loading-state">
          <div className="spinner"></div>
          <p>Loading categories...</p>
        </div>
      ) : categories.length === 0 ? (
        <div className="card empty-state-card">
          <h3>No Categories Found</h3>
          <p>Create your first menu category to start adding menu items.</p>
          <button className="btn btn-primary" onClick={handleOpenAddModal}>
            Add First Category
          </button>
        </div>
      ) : (
        <div className="card table-card">
          <div className="table-header-info">
            <span className="table-count">
              Total Categories: <strong>{categories.length}</strong> (
              {categories.filter((c) => c.isActive).length} Active)
            </span>
          </div>
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Category Name</th>
                  <th>Description</th>
                  <th>Status</th>
                  <th>Created Date</th>
                  <th className="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {categories.map((cat) => (
                  <tr key={cat.id} className={!cat.isActive ? 'row-inactive' : ''}>
                    <td className="font-weight-bold">{cat.name}</td>
                    <td className="text-muted text-truncate">
                      {cat.description || '—'}
                    </td>
                    <td>
                      <span
                        className={`status-badge ${
                          cat.isActive ? 'status-active' : 'status-inactive'
                        }`}
                      >
                        {cat.isActive ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td className="text-muted">{formatDate(cat.createdAt)}</td>
                    <td className="text-right">
                      <div className="action-buttons">
                        <button
                          className="btn-action btn-edit"
                          onClick={() => handleOpenEditModal(cat)}
                          title="Edit Category"
                          disabled={actionLoadingId === cat.id}
                        >
                          Edit
                        </button>
                        <button
                          className={`btn-action ${
                            cat.isActive ? 'btn-deactivate' : 'btn-activate'
                          }`}
                          onClick={() => handleToggleStatus(cat)}
                          title={cat.isActive ? 'Deactivate Category' : 'Activate Category'}
                          disabled={actionLoadingId === cat.id}
                        >
                          {actionLoadingId === cat.id
                            ? '...'
                            : cat.isActive
                            ? 'Deactivate'
                            : 'Activate'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Add / Edit Category Modal */}
      <CategoryModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSubmit={handleFormSubmit}
        initialData={editingCategory}
        isSubmitting={isSubmitting}
      />
    </div>
  );
};

export default CategoriesPage;
