import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import useAuth from '../../hooks/useAuth';
import DashboardCard from '../../components/admin/DashboardCard';
import OrderDetailsModal from '../../components/admin/OrderDetailsModal';
import reportService from '../../services/reportService';
import orderService from '../../services/orderService';
import { CURRENCY_SYMBOL } from '../../utils/constants';

const SalesOverviewChart = ({ data }) => {
  const [hoveredPoint, setHoveredPoint] = useState(null);

  if (!data || data.length === 0) return null;

  const width = 540;
  const height = 175;
  const paddingLeft = 55;
  const paddingRight = 20;
  const paddingTop = 20;
  const paddingBottom = 35;

  const chartW = width - paddingLeft - paddingRight;
  const chartH = height - paddingTop - paddingBottom;

  const revenues = data.map((d) => Number(d.revenue || 0));
  const maxRev = Math.max(...revenues, 100);

  const yLabels = [
    { val: maxRev, y: paddingTop },
    { val: maxRev / 2, y: paddingTop + chartH / 2 },
    { val: 0, y: paddingTop + chartH },
  ];

  const points = data.map((item, index) => {
    const rev = Number(item.revenue || 0);
    const x = paddingLeft + (index / Math.max(data.length - 1, 1)) * chartW;
    const y = paddingTop + chartH - (rev / maxRev) * chartH;
    return {
      x,
      y,
      rev,
      date: item.date,
      orders: item.orders || 0,
      index,
    };
  });

  const lineD = points.reduce((acc, p, i) => `${acc} ${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`, '');
  const firstP = points[0];
  const lastP = points[points.length - 1];
  const areaD = `${lineD} L ${lastP.x.toFixed(1)} ${(paddingTop + chartH).toFixed(1)} L ${firstP.x.toFixed(1)} ${(paddingTop + chartH).toFixed(1)} Z`;

  const formatShortDate = (dateStr) => {
    if (!dateStr) return '';
    try {
      const [y, m, d] = dateStr.split('-').map(Number);
      const dt = new Date(y, m - 1, d);
      const dayName = dt.toLocaleDateString('en-US', { weekday: 'short' });
      return `${dayName} ${dt.getDate()}`;
    } catch {
      return dateStr;
    }
  };

  const formatFullDate = (dateStr) => {
    if (!dateStr) return '';
    try {
      const [y, m, d] = dateStr.split('-').map(Number);
      const dt = new Date(y, m - 1, d);
      return dt.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
    } catch {
      return dateStr;
    }
  };

  const formatYAxisVal = (val) => {
    if (val >= 1000000) return `${CURRENCY_SYMBOL}${(val / 1000000).toFixed(1)}M`;
    if (val >= 1000) return `${CURRENCY_SYMBOL}${(val / 1000).toFixed(1)}k`;
    return `${CURRENCY_SYMBOL}${Math.round(val)}`;
  };

  const total7DayRevenue = revenues.reduce((a, b) => a + b, 0);
  const isAllZero = revenues.every((r) => r === 0);

  return (
    <div className="sales-overview-container">
      <div className="sales-overview-summary">
        <div>
          <span className="sales-overview-summary-title">Total Realized Revenue (Last 7 Days)</span>
          {isAllZero && (
            <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '2px' }}>
              No sales recorded in the last 7 days.
            </div>
          )}
        </div>
        <div className="sales-overview-summary-value">
          {CURRENCY_SYMBOL}
          {total7DayRevenue.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
        </div>
      </div>

      <div className="sales-chart-wrapper">
        {hoveredPoint && (
          <div
            className="chart-tooltip-box"
            style={{
              left: `${(hoveredPoint.x / width) * 100}%`,
              top: `${(hoveredPoint.y / height) * 100}%`,
            }}
          >
            <div className="chart-tooltip-date">{formatFullDate(hoveredPoint.date)}</div>
            <div className="chart-tooltip-revenue">
              {CURRENCY_SYMBOL}
              {hoveredPoint.rev.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
            </div>
            <div className="chart-tooltip-orders">
              {hoveredPoint.orders} paid order{hoveredPoint.orders === 1 ? '' : 's'}
            </div>
          </div>
        )}

        <svg className="sales-svg-chart" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none">
          <defs>
            <linearGradient id="salesGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#34342F" stopOpacity="0.2" />
              <stop offset="100%" stopColor="#34342F" stopOpacity="0.0" />
            </linearGradient>
          </defs>

          {yLabels.map((lbl, idx) => (
            <g key={idx}>
              <line
                x1={paddingLeft}
                y1={lbl.y}
                x2={width - paddingRight}
                y2={lbl.y}
                className={idx === yLabels.length - 1 ? 'chart-axis-line' : 'chart-grid-line'}
              />
              <text
                x={paddingLeft - 8}
                y={lbl.y + 4}
                textAnchor="end"
                className="chart-axis-label"
              >
                {formatYAxisVal(lbl.val)}
              </text>
            </g>
          ))}

          <path d={areaD} className="chart-area-path" />
          <path d={lineD} className="chart-line-path" />

          {points.map((pt, idx) => (
            <g key={idx}>
              <text
                x={pt.x}
                y={height - 8}
                textAnchor="middle"
                className="chart-axis-label"
                style={{
                  fontWeight: pt.index === points.length - 1 ? '700' : '500',
                  fill: pt.index === points.length - 1 ? '#34342F' : 'var(--wn-text-muted, #716C62)',
                }}
              >
                {formatShortDate(pt.date)}
              </text>

              <rect
                x={pt.x - 25}
                y={paddingTop}
                width={50}
                height={chartH + 10}
                fill="transparent"
                style={{ cursor: 'pointer' }}
                onMouseEnter={() => setHoveredPoint(pt)}
                onMouseLeave={() => setHoveredPoint(null)}
              />

              <circle
                cx={pt.x}
                cy={pt.y}
                r={hoveredPoint?.index === idx ? 7 : 4.5}
                className={`chart-point ${hoveredPoint?.index === idx ? 'active' : ''}`}
                onMouseEnter={() => setHoveredPoint(pt)}
                onMouseLeave={() => setHoveredPoint(null)}
              />
            </g>
          ))}
        </svg>
      </div>
    </div>
  );
};

export const AdminDashboardPage = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const displayName = user?.name || user?.email || 'Admin User';

  const [todaySales, setTodaySales] = useState(null);
  const [todayOrders, setTodayOrders] = useState(null);
  const [activeTables, setActiveTables] = useState(null);
  const [pendingKitchenOrders, setPendingKitchenOrders] = useState(null);
  const [dashboardLoading, setDashboardLoading] = useState(true);
  const [dashboardError, setDashboardError] = useState(false);

  // Recent Orders State
  const [recentOrders, setRecentOrders] = useState([]);
  const [recentOrdersLoading, setRecentOrdersLoading] = useState(true);
  const [recentOrdersError, setRecentOrdersError] = useState(false);
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [isDetailsModalOpen, setIsDetailsModalOpen] = useState(false);

  // Sales Overview State (Last 7 Days)
  const [salesOverviewData, setSalesOverviewData] = useState([]);
  const [salesOverviewLoading, setSalesOverviewLoading] = useState(true);
  const [salesOverviewError, setSalesOverviewError] = useState(false);

  const formatDateISO = (d) => {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  };

  const getTodayISO = () => formatDateISO(new Date());
  const getDaysAgoISO = (days) => {
    const d = new Date();
    d.setDate(d.getDate() - days);
    return formatDateISO(d);
  };

  useEffect(() => {
    let isMounted = true;

    const fetchDashboardData = async () => {
      try {
        setDashboardLoading(true);
        setDashboardError(false);
        const data = await reportService.getDashboardSummary();
        if (isMounted) {
          const revenue = data?.totalRevenue != null ? Number(data.totalRevenue) : 0;
          const orders = data?.totalOrders != null ? Number(data.totalOrders) : 0;
          const tables = data?.activeTableCount != null ? Number(data.activeTableCount) : 0;
          const pending = data?.pendingOrders != null ? Number(data.pendingOrders) : 0;
          setTodaySales(revenue);
          setTodayOrders(orders);
          setActiveTables(tables);
          setPendingKitchenOrders(pending);
          setDashboardLoading(false);
        }
      } catch {
        if (isMounted) {
          setDashboardError(true);
          setDashboardLoading(false);
        }
      }
    };

    const fetchRecentOrders = async () => {
      try {
        setRecentOrdersLoading(true);
        setRecentOrdersError(false);
        const orders = await orderService.getOrders();
        if (isMounted) {
          const list = Array.isArray(orders) ? orders : [];
          const sorted = [...list].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
          setRecentOrders(sorted.slice(0, 5));
          setRecentOrdersLoading(false);
        }
      } catch {
        if (isMounted) {
          setRecentOrdersError(true);
          setRecentOrdersLoading(false);
        }
      }
    };

    const fetchSalesOverview = async () => {
      try {
        setSalesOverviewLoading(true);
        setSalesOverviewError(false);
        const fromStr = getDaysAgoISO(6);
        const toStr = getTodayISO();
        const data = await reportService.getDailySalesBreakdown(fromStr, toStr);
        if (isMounted) {
          setSalesOverviewData(Array.isArray(data) ? data : []);
          setSalesOverviewLoading(false);
        }
      } catch {
        if (isMounted) {
          setSalesOverviewError(true);
          setSalesOverviewLoading(false);
        }
      }
    };

    fetchDashboardData();
    fetchRecentOrders();
    fetchSalesOverview();

    return () => {
      isMounted = false;
    };
  }, []);

  const handleOrderClick = (order) => {
    setSelectedOrder(order);
    setIsDetailsModalOpen(true);
  };

  const formatOrderTime = (dateStr) => {
    if (!dateStr) return '—';
    try {
      const date = new Date(dateStr);
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch {
      return dateStr;
    }
  };

  const renderStatusBadge = (status) => {
    const s = (status || '').toUpperCase();
    let badgeClass = 'badge-inactive';

    if (s === 'NEW') badgeClass = 'badge-new';
    else if (s === 'ACCEPTED') badgeClass = 'badge-accepted';
    else if (s === 'PREPARING') badgeClass = 'badge-preparing';
    else if (s === 'READY') badgeClass = 'badge-ready';
    else if (s === 'COMPLETED') badgeClass = 'badge-completed';
    else if (s === 'CANCELLED') badgeClass = 'badge-cancelled';

    return <span className={`badge ${badgeClass}`}>{status}</span>;
  };

  const formatSalesValue = () => {
    if (dashboardLoading) {
      return 'Loading...';
    }
    if (dashboardError) {
      return 'Unavailable';
    }
    if (todaySales !== null) {
      return `${CURRENCY_SYMBOL}${todaySales.toLocaleString('en-IN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      })}`;
    }
    return `${CURRENCY_SYMBOL}0.00`;
  };

  const formatOrdersValue = () => {
    if (dashboardLoading) {
      return 'Loading...';
    }
    if (dashboardError) {
      return 'Unavailable';
    }
    if (todayOrders !== null) {
      return `${todayOrders}`;
    }
    return '0';
  };

  const formatActiveTablesValue = () => {
    if (dashboardLoading) {
      return 'Loading...';
    }
    if (dashboardError) {
      return 'Unavailable';
    }
    if (activeTables !== null) {
      return `${activeTables}`;
    }
    return '0';
  };

  const formatPendingKitchenOrdersValue = () => {
    if (dashboardLoading) {
      return 'Loading...';
    }
    if (dashboardError) {
      return 'Unavailable';
    }
    if (pendingKitchenOrders !== null) {
      return `${pendingKitchenOrders}`;
    }
    return '0';
  };

  const summaryCards = [
    {
      title: "Today's Sales",
      value: formatSalesValue(),
      isPlaceholder: false,
    },
    {
      title: "Today's Orders",
      value: formatOrdersValue(),
      isPlaceholder: false,
    },
    {
      title: 'Active Tables',
      value: formatActiveTablesValue(),
      isPlaceholder: false,
    },
    {
      title: 'Pending Kitchen Orders',
      value: formatPendingKitchenOrdersValue(),
      isPlaceholder: false,
    },
  ];

  const quickActions = [
    { title: 'Create Order', desc: 'Create new POS order', path: '/admin/orders' },
    { title: 'Manage Tables', desc: 'View table status & floor layout', path: '/admin/tables' },
    { title: 'Manage Menu', desc: 'Manage categories & menu items', path: '/admin/menu' },
    { title: 'Billing', desc: 'Generate bills & process payments', path: '/admin/billing' },
    { title: 'Inventory', desc: 'Manage stock & transactions', path: '/admin/inventory' },
    { title: 'Staff', desc: 'Manage restaurant staff', path: '/admin/staff' },
  ];

  return (
    <div className="admin-dashboard-page">
      {/* A. Welcome Section */}
      <section className="welcome-banner card">
        <div className="welcome-content">
          <h2 className="welcome-title">Welcome back, {displayName}</h2>
          <p className="welcome-subtitle">
            Here's what's happening with your restaurant today.
          </p>
        </div>
        <div className="banner-badge">
          <span>Phase 2 • Step 3 Layout Foundation</span>
        </div>
      </section>

      {/* B. Summary Cards Grid */}
      <section className="dashboard-metrics-section">
        <div className="grid grid-4">
          {summaryCards.map((card, idx) => (
            <DashboardCard
              key={idx}
              title={card.title}
              value={card.value}
              isPlaceholder={card.isPlaceholder}
            />
          ))}
        </div>
      </section>

      {/* C. Quick Actions Section */}
      <section className="quick-actions-section">
        <h3 className="section-heading">Quick Actions</h3>
        <div className="grid grid-3">
          {quickActions.map((action, idx) => (
            <button
              key={idx}
              className="card quick-action-btn"
              onClick={() => navigate(action.path)}
              type="button"
            >
              <div className="action-text">
                <span className="action-title">{action.title}</span>
                <span className="action-desc">{action.desc}</span>
              </div>
            </button>
          ))}
        </div>
      </section>

      {/* D. Recent Orders & E. Sales Overview Section */}
      <div className="grid grid-2 dashboard-sections-row">
        {/* D. Recent Orders */}
        <section className="card dashboard-section-card">
          <div className="section-header-row">
            <h3 className="section-heading">Recent Orders</h3>
            <button
              className="btn btn-sm btn-secondary"
              onClick={() => navigate('/admin/orders')}
              style={{ fontSize: '0.8rem', padding: '0.25rem 0.6rem' }}
              type="button"
            >
              View All
            </button>
          </div>

          {recentOrdersLoading ? (
            <div className="empty-state">
              <div className="spinner mb-2"></div>
              <p className="empty-state-message">Loading recent orders...</p>
            </div>
          ) : recentOrdersError ? (
            <div className="empty-state">
              <p className="empty-state-message">Unable to load recent orders</p>
              <span className="empty-state-sub">
                Please check connection or try refreshing the dashboard.
              </span>
            </div>
          ) : recentOrders.length === 0 ? (
            <div className="empty-state">
              <p className="empty-state-message">No recent orders</p>
              <span className="empty-state-sub">
                New POS orders will appear here automatically.
              </span>
            </div>
          ) : (
            <div className="table-responsive" style={{ overflowX: 'auto' }}>
              <table className="data-table recent-orders-table" style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr>
                    <th style={{ textAlign: 'left', padding: '0.5rem 0.75rem' }}>Order</th>
                    <th style={{ textAlign: 'left', padding: '0.5rem 0.75rem' }}>Type / Table</th>
                    <th style={{ textAlign: 'left', padding: '0.5rem 0.75rem' }}>Amount</th>
                    <th style={{ textAlign: 'left', padding: '0.5rem 0.75rem' }}>Status</th>
                    <th style={{ textAlign: 'left', padding: '0.5rem 0.75rem' }}>Time</th>
                  </tr>
                </thead>
                <tbody>
                  {recentOrders.map((order) => (
                    <tr
                      key={order.id}
                      onClick={() => handleOrderClick(order)}
                      className="clickable-row"
                      style={{ cursor: 'pointer', borderBottom: '1px solid var(--border-color)' }}
                    >
                      <td style={{ padding: '0.6rem 0.75rem', fontWeight: 'bold' }}>#{order.id}</td>
                      <td style={{ padding: '0.6rem 0.75rem' }}>
                        {order.orderType === 'DINE_IN'
                          ? `Dine In · ${
                              order.tableNumber ? `T${order.tableNumber}` : order.tableId ? `T${order.tableId}` : 'Table'
                            }`
                          : 'Takeaway'}
                      </td>
                      <td style={{ padding: '0.6rem 0.75rem', fontWeight: 'bold' }}>
                        {CURRENCY_SYMBOL}
                        {Number(order.totalAmount || order.subtotal || 0).toFixed(2)}
                      </td>
                      <td style={{ padding: '0.6rem 0.75rem' }}>{renderStatusBadge(order.status)}</td>
                      <td style={{ padding: '0.6rem 0.75rem', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                        {formatOrderTime(order.createdAt)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        {/* E. Sales Overview */}
        <section className="card dashboard-section-card">
          <div className="section-header-row">
            <h3 className="section-heading">Sales Overview</h3>
            <span className="sales-overview-header-badge">Last 7 Days</span>
          </div>

          {salesOverviewLoading ? (
            <div className="empty-state">
              <div className="spinner mb-2"></div>
              <p className="empty-state-message">Loading sales overview...</p>
            </div>
          ) : salesOverviewError ? (
            <div className="empty-state">
              <p className="empty-state-message">Unable to load sales overview</p>
              <span className="empty-state-sub">
                Please check connection or try refreshing the dashboard.
              </span>
            </div>
          ) : (
            <SalesOverviewChart data={salesOverviewData} />
          )}
        </section>
      </div>

      {/* Order Details Modal */}
      {isDetailsModalOpen && selectedOrder && (
        <OrderDetailsModal
          isOpen={isDetailsModalOpen}
          onClose={() => {
            setIsDetailsModalOpen(false);
            setSelectedOrder(null);
          }}
          order={selectedOrder}
          onGenerateBill={() => {
            setIsDetailsModalOpen(false);
            setSelectedOrder(null);
            navigate('/admin/billing');
          }}
        />
      )}
    </div>
  );
};

export default AdminDashboardPage;
