import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import useAuth from '../hooks/useAuth';
import ProtectedRoute from './ProtectedRoute';
import MainLayout from '../layouts/MainLayout';
import AdminLayout from '../layouts/AdminLayout';
import LoginPage from '../pages/auth/LoginPage';
import AdminDashboardPage from '../pages/admin/AdminDashboardPage';
import CategoriesPage from '../pages/admin/CategoriesPage';
import MenuPage from '../pages/admin/MenuPage';
import TablesPage from '../pages/admin/TablesPage';
import OrdersPage from '../pages/admin/OrdersPage';
import BillingPage from '../pages/admin/BillingPage';
import StaffPage from '../pages/admin/StaffPage';
import InventoryPage from '../pages/admin/InventoryPage';
import CustomersPage from '../pages/admin/CustomersPage';
import ReportsPage from '../pages/admin/ReportsPage';
import SettingsPage from '../pages/admin/SettingsPage';
import KitchenKdsPage from '../pages/kitchen/KitchenKdsPage';
import SuperAdminLayout from '../layouts/SuperAdminLayout';
import SuperAdminRestaurantsPage from '../pages/superadmin/SuperAdminRestaurantsPage';
import { ROLES } from '../utils/constants';

// Root redirect handler based on authentication status and role
const RootRedirect = () => {
  const { isAuthenticated, role, loading, logout } = useAuth();

  if (loading) {
    return (
      <div className="loading-screen">
        <div className="spinner"></div>
        <p>Loading application...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (role === ROLES.SUPER_ADMIN) {
    return <Navigate to="/super-admin/restaurants" replace />;
  }

  if (role === ROLES.ADMIN) {
    return <Navigate to="/admin" replace />;
  }

  if (role === ROLES.KITCHEN) {
    return <Navigate to="/kitchen" replace />;
  }

  logout();
  return <Navigate to="/login" replace />;
};

export const AppRoutes = () => {
  return (
    <Routes>
      {/* Root redirect */}
      <Route path="/" element={<RootRedirect />} />

      {/* Public Login Route */}
      <Route path="/login" element={<LoginPage />} />

      {/* Protected ADMIN Route (uses AdminLayout) */}
      <Route element={<ProtectedRoute allowedRoles={[ROLES.ADMIN]} />}>
        <Route element={<AdminLayout />}>
          <Route path="/admin" element={<AdminDashboardPage />} />
          <Route path="/admin/categories" element={<CategoriesPage />} />
          <Route path="/admin/menu" element={<MenuPage />} />
          <Route path="/admin/tables" element={<TablesPage />} />
          <Route path="/admin/orders" element={<OrdersPage />} />
          <Route path="/admin/billing" element={<BillingPage />} />
          <Route path="/admin/staff" element={<StaffPage />} />
          <Route path="/admin/inventory" element={<InventoryPage />} />
          <Route path="/admin/customers" element={<CustomersPage />} />
          <Route path="/admin/reports" element={<ReportsPage />} />
          <Route path="/admin/settings" element={<SettingsPage />} />
        </Route>
      </Route>

      {/* Protected KITCHEN Route (uses MainLayout) */}
      <Route element={<ProtectedRoute allowedRoles={[ROLES.KITCHEN, ROLES.ADMIN]} />}>
        <Route element={<MainLayout />}>
          <Route path="/kitchen" element={<KitchenKdsPage />} />
        </Route>
      </Route>

      {/* Protected SUPER_ADMIN Route (uses SuperAdminLayout) */}
      <Route element={<ProtectedRoute allowedRoles={[ROLES.SUPER_ADMIN]} />}>
        <Route element={<SuperAdminLayout />}>
          <Route path="/super-admin" element={<Navigate to="/super-admin/restaurants" replace />} />
          <Route path="/super-admin/restaurants" element={<SuperAdminRestaurantsPage />} />
        </Route>
      </Route>

      {/* Fallback redirect */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
};

export default AppRoutes;
