import React from 'react';
import { API_BASE_URL } from '../../utils/constants';

export const Footer = () => {
  return (
    <footer className="app-footer">
      <div className="footer-content">
        <p>AnnaDhara &copy; {new Date().getFullYear()} — Admin & Kitchen Web Application</p>
        <p className="footer-subtext">Backend Base URL: <code>{API_BASE_URL}</code></p>
      </div>
    </footer>
  );
};

export default Footer;
