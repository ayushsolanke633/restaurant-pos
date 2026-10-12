import React, { useState, useEffect, useCallback } from 'react';
import useAuth from '../../hooks/useAuth';
import staffService from '../../services/staffService';
import StaffModal from '../../components/admin/StaffModal';

export const StaffPage = () => {
  const { user } = useAuth();
  const restaurantId = user?.restaurantId || 1;
  const currentUserId = user?.userId;

  const [staffMembers, setStaffMembers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  // Filters & Search
  const [searchTerm, setSearchTerm] = useState('');
  const [roleFilter, setRoleFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');

  // Modals
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedStaff, setSelectedStaff] = useState(null);
  const [confirmDeactivateId, setConfirmDeactivateId] = useState(null);

  const fetchStaffMembers = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await staffService.getStaffMembers(restaurantId);
      setStaffMembers(data || []);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to fetch staff members');
    } finally {
      setLoading(false);
    }
  }, [restaurantId]);

  useEffect(() => {
    fetchStaffMembers();
  }, [fetchStaffMembers]);

  const handleAddClick = () => {
    setSelectedStaff(null);
    setIsModalOpen(true);
  };

  const handleEditClick = (member) => {
    setSelectedStaff(member);
    setIsModalOpen(true);
  };

  const handleDeactivate = async (id) => {
    setError('');
    const targetMember = staffMembers.find((m) => m.id === id);
    if (targetMember?.roleName === 'ADMIN') {
      setError('ADMIN accounts cannot be deactivated.');
      setConfirmDeactivateId(null);
      return;
    }

    try {
      await staffService.deactivateStaffMember(id, targetMember?.roleName);
      setSuccessMsg('Staff member deactivated successfully.');
      setConfirmDeactivateId(null);
      fetchStaffMembers();
      setTimeout(() => setSuccessMsg(''), 4000);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to deactivate staff member');
    }
  };

  const handleActivate = async (member) => {
    setError('');
    try {
      await staffService.activateStaffMember(member.id, member);
      setSuccessMsg(`Staff member ${member.name} activated successfully.`);
      fetchStaffMembers();
      setTimeout(() => setSuccessMsg(''), 4000);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to activate staff member');
    }
  };

  // Filter staff members by search term, role filter, and status filter
  const filteredStaff = staffMembers.filter((member) => {
    // 1. Search term (name or email)
    if (searchTerm) {
      const term = searchTerm.toLowerCase();
      const nameMatch = member.name?.toLowerCase().includes(term);
      const emailMatch = member.email?.toLowerCase().includes(term);
      if (!nameMatch && !emailMatch) return false;
    }

    // 2. Role filter
    if (roleFilter !== 'ALL' && member.roleName !== roleFilter) {
      return false;
    }

    // 3. Status filter
    if (statusFilter === 'ACTIVE' && !member.isActive) return false;
    if (statusFilter === 'INACTIVE' && member.isActive) return false;

    return true;
  });

  const getRoleBadgeClass = (roleName) => {
    switch (roleName) {
      case 'ADMIN':
        return 'chip-admin';
      case 'WAITER':
        return 'chip-waiter';
      case 'KITCHEN':
        return 'chip-kitchen';
      default:
        return 'chip-default';
    }
  };

  const getRoleLabel = (roleName) => {
    switch (roleName) {
      case 'ADMIN':
        return 'Admin';
      case 'WAITER':
        return 'Waiter';
      case 'KITCHEN':
        return 'Kitchen';
      default:
        return roleName;
    }
  };

  return (
    <div className="admin-page staff-page">
      {/* Page Header */}
      <div className="page-header">
        <div className="header-title">
          <h1>Staff Management</h1>
          <p className="subtitle">Manage restaurant staff accounts, roles, and access permissions</p>
        </div>
        <div className="header-actions">
          <button className="btn btn-primary" onClick={handleAddClick}>
            Add Staff Member
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

      {/* Toolbar / Filters */}
      <div className="billing-toolbar mb-4">
        <div className="d-flex gap-2 flex-wrap flex-1">
          <div className="search-box">
            <input
              type="text"
              className="form-input search-input"
              placeholder="Search staff by name or email..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>

          <div className="filter-group d-flex gap-2">
            <select
              className="form-input filter-select"
              value={roleFilter}
              onChange={(e) => setRoleFilter(e.target.value)}
            >
              <option value="ALL">All Roles</option>
              <option value="ADMIN">Admin</option>
              <option value="WAITER">Waiter</option>
              <option value="KITCHEN">Kitchen</option>
            </select>

            <select
              className="form-input filter-select"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
            >
              <option value="ALL">All Statuses</option>
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
            </select>
          </div>
        </div>

        <button className="btn btn-secondary btn-icon ml-auto" onClick={fetchStaffMembers} title="Refresh Staff List">
          Refresh
        </button>
      </div>

      {/* Staff Data Table */}
      {loading ? (
        <div className="loading-container py-5 text-center">
          <div className="spinner mb-3"></div>
          <p className="text-muted">Loading staff list...</p>
        </div>
      ) : filteredStaff.length === 0 ? (
        <div className="empty-state card p-5 text-center">
          <h3>No Staff Members Found</h3>
          <p className="text-muted m-0">
            {searchTerm || roleFilter !== 'ALL' || statusFilter !== 'ALL'
              ? 'No staff members match your search and filter criteria.'
              : 'Click "Add Staff Member" to create your first staff account.'}
          </p>
        </div>
      ) : (
        <div className="table-responsive card">
          <table className="data-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Phone</th>
                <th>Role</th>
                <th>Status</th>
                <th className="text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredStaff.map((member) => {
                const isCurrentUser = member.id === currentUserId;
                const isAdmin = member.roleName === 'ADMIN';
                const isDeactivatingThis = confirmDeactivateId === member.id && !isAdmin;

                return (
                  <tr key={member.id}>
                    <td>
                      <div className="staff-name-cell">
                        <span className="font-semibold text-main">{member.name}</span>
                        {isCurrentUser && <span className="badge badge-current-user">You</span>}
                      </div>
                    </td>
                    <td>{member.email}</td>
                    <td>{member.phone || '—'}</td>
                    <td>
                      <span className={`role-chip ${getRoleBadgeClass(member.roleName)}`}>
                        {getRoleLabel(member.roleName)}
                      </span>
                    </td>
                    <td>
                      <span className={`status-badge ${member.isActive ? 'status-active' : 'status-inactive'}`}>
                        {member.isActive ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td className="text-right">
                      {isDeactivatingThis ? (
                        <div className="d-flex gap-2 justify-content-end align-items-center">
                          <span className="text-danger font-semibold text-xs">Confirm deactivation?</span>
                          <button
                            className="btn btn-danger btn-sm"
                            onClick={() => handleDeactivate(member.id)}
                          >
                            Yes, Deactivate
                          </button>
                          <button
                            className="btn btn-secondary btn-sm"
                            onClick={() => setConfirmDeactivateId(null)}
                          >
                            Cancel
                          </button>
                        </div>
                      ) : (
                        <div className="d-flex gap-2 justify-content-end">
                          <button
                            className="btn btn-secondary btn-sm"
                            onClick={() => handleEditClick(member)}
                          >
                            Edit
                          </button>

                          {!isAdmin && (
                            member.isActive ? (
                              <button
                                className="btn btn-danger-outline btn-sm"
                                onClick={() => setConfirmDeactivateId(member.id)}
                                title="Deactivate staff account"
                              >
                                Deactivate
                              </button>
                            ) : (
                              <button
                                className="btn btn-success btn-sm"
                                onClick={() => handleActivate(member)}
                              >
                                Activate
                              </button>
                            )
                          )}
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Staff Form Modal */}
      <StaffModal
        staffMember={selectedStaff}
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSuccess={fetchStaffMembers}
      />
    </div>
  );
};

export default StaffPage;
