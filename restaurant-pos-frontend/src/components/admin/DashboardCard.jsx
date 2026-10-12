import React from 'react';

export const DashboardCard = ({ title, value, isPlaceholder = true }) => {
  return (
    <div className="card dashboard-card">
      {isPlaceholder && (
        <div className="card-header-row">
          <span className="placeholder-tag">Placeholder</span>
        </div>
      )}
      <div className="card-body">
        <h3 className="card-title">{title}</h3>
        <p className="card-value">{value}</p>
      </div>
    </div>
  );
};

export default DashboardCard;
