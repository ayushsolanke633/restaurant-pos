import React from 'react';
import { NavLink } from 'react-router-dom';

const sidebarItems = [
  { label: 'Dashboard', path: '/admin', active: true },
  { label: 'Menu', path: '/admin/menu', active: true },
  { label: 'Categories', path: '/admin/categories', active: true },
  { label: 'Tables', path: '/admin/tables', active: true },
  { label: 'Orders', path: '/admin/orders', active: true },
  { label: 'Billing', path: '/admin/billing', active: true },
  { label: 'Staff', path: '/admin/staff', active: true },
  { label: 'Inventory', path: '/admin/inventory', active: true },
  { label: 'Customers', path: '/admin/customers', active: true },
  { label: 'Reports', path: '/admin/reports', active: true },
  { label: 'Settings', path: '/admin/settings', active: true },
];

export const AdminSidebar = ({ isOpen, onClose }) => {
  return (
    <>
      {/* Mobile Backdrop Overlay */}
      {isOpen && (
        <div 
          className="sidebar-backdrop" 
          onClick={onClose}
          aria-hidden="true"
        />
      )}

      <aside className={`admin-sidebar ${isOpen ? 'sidebar-open' : ''}`}>
        <div className="sidebar-header">
          <div className="sidebar-brand">
            <span className="brand-name">POS Admin</span>
          </div>
          <button 
            className="btn-close-sidebar" 
            onClick={onClose}
            aria-label="Close sidebar"
          >
            Close
          </button>
        </div>

        <nav className="sidebar-nav" aria-label="Admin Navigation">
          <ul className="sidebar-menu">
            {sidebarItems.map((item, index) => (
              <li key={index} className="sidebar-item">
                {item.active ? (
                  <NavLink
                    to={item.path}
                    end
                    onClick={onClose}
                    className={({ isActive }) =>
                      `sidebar-link ${isActive ? 'active' : ''}`
                    }
                  >
                    <span className="sidebar-label">{item.label}</span>
                  </NavLink>
                ) : (
                  <div className="sidebar-link disabled" title="Coming soon">
                    <span className="sidebar-label">{item.label}</span>
                    <span className="badge-soon">Soon</span>
                  </div>
                )}
              </li>
            ))}
          </ul>
        </nav>
      </aside>
    </>
  );
};

export default AdminSidebar;
