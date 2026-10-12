import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client/dist/sockjs.js';
import { API_BASE_URL, WS_BASE_URL, STORAGE_KEYS } from '../utils/constants';

/**
 * WebSocket & STOMP Connection Manager for Real-Time Kitchen Updates
 */
export class KitchenWebSocketClient {
  constructor({ restaurantId, onOrderEvent, onStatusChange, clientName }) {
    this.restaurantId = restaurantId;
    this.onOrderEvent = onOrderEvent;
    this.onStatusChange = onStatusChange || (() => {});
    this.clientName = clientName || 'KITCHEN';
    this.stompClient = null;
    this.subscription = null;
    this.isConnected = false;
  }

  connect() {
    const token = sessionStorage.getItem(STORAGE_KEYS.TOKEN);
    if (!token) {
      this.onStatusChange('UNAUTHENTICATED');
      return;
    }

    this.onStatusChange('CONNECTING');

    const socketUrl = WS_BASE_URL || `${API_BASE_URL}/ws`;

    this.stompClient = new Client({
      webSocketFactory: () => new SockJS(socketUrl),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      debug: (str) => {
        // Silent or debug log
      },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    });

    this.stompClient.beforeConnect = () => {
      this.onStatusChange('CONNECTING');
    };

    this.stompClient.onConnect = (frame) => {
      this.isConnected = true;
      this.onStatusChange('CONNECTED');

      // Subscribe to kitchen topic for current restaurant tenant
      const topic = `/topic/restaurants/${this.restaurantId}/kitchen/orders`;

      console.log(`[KDS WS] connected`);
      console.log(`[KDS WS] subscribed destination=${topic}`);

      this.subscription = this.stompClient.subscribe(topic, (message) => {
        try {
          if (message.body) {
            const eventPayload = JSON.parse(message.body);
            console.log(`[KDS WS] received event=`, eventPayload);
            console.log(`[KDS WS] eventType=${eventPayload.eventType || 'ORDER_STATUS_CHANGED'} orderId=${eventPayload.orderId} status=${eventPayload.status}`);
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
      console.error('STOMP protocol error:', frame?.headers ? frame.headers['message'] : frame);
      this.isConnected = false;
      this.onStatusChange('DISCONNECTED');
    };

    this.stompClient.onWebSocketClose = () => {
      this.isConnected = false;
      this.onStatusChange('DISCONNECTED');
    };

    this.stompClient.onWebSocketError = (event) => {
      console.error('WebSocket error:', event);
      this.isConnected = false;
      this.onStatusChange('DISCONNECTED');
    };

    this.stompClient.activate();
  }

  disconnect() {
    if (this.subscription) {
      try {
        this.subscription.unsubscribe();
      } catch (e) {}
      this.subscription = null;
    }
    if (this.stompClient) {
      try {
        this.stompClient.deactivate();
      } catch (e) {}
      this.stompClient = null;
    }
    this.isConnected = false;
    this.onStatusChange('DISCONNECTED');
  }
}

export default KitchenWebSocketClient;
