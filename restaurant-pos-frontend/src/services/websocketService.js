import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client/dist/sockjs.js';
import { API_BASE_URL, WS_BASE_URL, STORAGE_KEYS } from '../utils/constants';
import authService from './authService';

/**
 * WebSocket & STOMP Connection Manager for Real-Time Kitchen Updates
 */
export class KitchenWebSocketClient {
  static activeClients = new Set();

  static disconnectAll() {
    for (const client of KitchenWebSocketClient.activeClients) {
      try {
        client.disconnect();
      } catch {}
    }
    KitchenWebSocketClient.activeClients.clear();
  }

  constructor({ restaurantId, onOrderEvent, onStatusChange, clientName }) {
    this.restaurantId = restaurantId;
    this.onOrderEvent = onOrderEvent;
    this.onStatusChange = onStatusChange || (() => {});
    this.clientName = clientName || 'KITCHEN';
    this.stompClient = null;
    this.subscription = null;
    this.isConnected = false;
    this.isManualDisconnect = false;

    this.handleGlobalAuthFailure = () => {
      this.disconnect();
    };
  }

  connect() {
    const token = sessionStorage.getItem(STORAGE_KEYS.TOKEN);
    if (!token) {
      this.onStatusChange('UNAUTHENTICATED');
      return;
    }

    if (authService.isTokenExpired(token)) {
      authService.clearSession();
      this.onStatusChange('UNAUTHENTICATED');
      window.dispatchEvent(new Event('auth:unauthorized'));
      return;
    }

    this.isManualDisconnect = false;
    this.onStatusChange('CONNECTING');

    const socketUrl = WS_BASE_URL || `${API_BASE_URL}/ws`;

    this.stompClient = new Client({
      webSocketFactory: () => new SockJS(socketUrl),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    });

    this.stompClient.beforeConnect = () => {
      this.onStatusChange('CONNECTING');
    };

    this.stompClient.onConnect = () => {
      this.isConnected = true;
      this.onStatusChange('CONNECTED');

      // Subscribe to kitchen topic for current restaurant tenant
      const topic = `/topic/restaurants/${this.restaurantId}/kitchen/orders`;

      this.subscription = this.stompClient.subscribe(topic, (message) => {
        try {
          if (message.body) {
            const eventPayload = JSON.parse(message.body);
            if (this.onOrderEvent) {
              this.onOrderEvent(eventPayload);
            }
          }
        } catch (err) {
          console.error('Error parsing WebSocket message payload:', err);
        }
      });
    };

    this.stompClient.onStompError = (frame) => {
      const errMsg = (frame?.headers?.message || '').toLowerCase();
      this.isConnected = false;

      const isAuthError =
        errMsg.includes('access denied') ||
        errMsg.includes('unauthenticated') ||
        errMsg.includes('revoked') ||
        errMsg.includes('expired') ||
        errMsg.includes('unauthorized');

      if (isAuthError) {
        // Stop infinite reconnection loop on authentication/authorization rejection
        this.disconnect();
        this.onStatusChange('AUTH_ERROR');
        authService.clearSession();
        window.dispatchEvent(new Event('auth:unauthorized'));
      } else {
        // Allow temporary broker/network disconnect to retry via reconnectDelay
        this.onStatusChange('DISCONNECTED');
      }
    };

    this.stompClient.onWebSocketClose = () => {
      this.isConnected = false;
      if (!this.isManualDisconnect) {
        this.onStatusChange('DISCONNECTED');
      }
    };

    this.stompClient.onWebSocketError = () => {
      this.isConnected = false;
      if (!this.isManualDisconnect) {
        this.onStatusChange('DISCONNECTED');
      }
    };

    window.addEventListener('auth:unauthorized', this.handleGlobalAuthFailure);
    window.addEventListener('auth:logout', this.handleGlobalAuthFailure);
    KitchenWebSocketClient.activeClients.add(this);

    this.stompClient.activate();
  }

  disconnect() {
    this.isManualDisconnect = true;

    window.removeEventListener('auth:unauthorized', this.handleGlobalAuthFailure);
    window.removeEventListener('auth:logout', this.handleGlobalAuthFailure);
    KitchenWebSocketClient.activeClients.delete(this);

    if (this.subscription) {
      try {
        this.subscription.unsubscribe();
      } catch {}
      this.subscription = null;
    }
    if (this.stompClient) {
      try {
        this.stompClient.reconnectDelay = 0;
        this.stompClient.deactivate();
      } catch {}
      this.stompClient = null;
    }
    this.isConnected = false;
    this.onStatusChange('DISCONNECTED');
  }
}

export default KitchenWebSocketClient;
