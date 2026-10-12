import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import '../storage/secure_storage_service.dart';
import 'backend_url_resolver.dart';

class WebSocketService {
  static final WebSocketService _instance = WebSocketService._internal();

  factory WebSocketService.instance() => _instance;

  factory WebSocketService({
    BackendUrlResolver? urlResolver,
    SecureStorageService? storageService,
  }) {
    if (urlResolver != null) _instance._urlResolver = urlResolver;
    if (storageService != null) _instance._storageService = storageService;
    return _instance;
  }

  @visibleForTesting
  WebSocketService.forTesting({
    BackendUrlResolver? urlResolver,
    SecureStorageService? storageService,
  }) {
    if (urlResolver != null) _urlResolver = urlResolver;
    if (storageService != null) _storageService = storageService;
  }

  WebSocketService._internal();

  late BackendUrlResolver _urlResolver = BackendUrlResolver();
  late SecureStorageService _storageService = SecureStorageService();

  WebSocket? _socket;
  StreamSubscription? _socketSubscription;
  Timer? _reconnectTimer;
  Timer? _pingTimer;

  bool _isConnecting = false;
  bool _isManualDisconnect = false;
  int? _subscribedRestaurantId;

  final ValueNotifier<bool> isConnectedNotifier = ValueNotifier<bool>(false);
  final StreamController<Map<String, dynamic>> _orderEventsController =
      StreamController<Map<String, dynamic>>.broadcast();

  Stream<Map<String, dynamic>> get orderEventsStream =>
      _orderEventsController.stream;

  bool get isConnected => isConnectedNotifier.value;

  Map<String, dynamic>? _decodeJwtClaims(String? token) {
    if (token == null || token.isEmpty) return null;
    final parts = token.split('.');
    if (parts.length != 3) return null;
    try {
      final normalized = base64Url.normalize(parts[1]);
      final resp = utf8.decode(base64Url.decode(normalized));
      final payload = json.decode(resp);
      if (payload is Map<String, dynamic>) return payload;
    } catch (_) {}
    return null;
  }

  Future<void> connect({dynamic restaurantId}) async {
    dynamic resId = restaurantId;
    if (resId == null) {
      final user = await _storageService.getUser();
      resId = user?.restaurantId;
    }

    if (resId == null) return;
    _subscribedRestaurantId = int.tryParse(resId.toString()) ?? (resId is int ? resId : null);

    if (_subscribedRestaurantId == null) return;
    if (isConnected || _isConnecting) return;

    _isManualDisconnect = false;
    _isConnecting = true;

    final token = await _storageService.getToken();
    if (token == null || token.isEmpty) {
      debugPrint('[WAITER WS DEBUG]\nTOKEN PRESENT=false');
      _isConnecting = false;
      return;
    }

    final claims = _decodeJwtClaims(token);
    final role = claims?['role'] ?? 'WAITER';

    debugPrint('[WAITER WS DEBUG]\nTOKEN PRESENT=true');
    debugPrint('[WAITER WS DEBUG]\nJWT ROLE=$role');
    debugPrint('[WAITER WS DEBUG]\nRESTAURANT ID=$_subscribedRestaurantId');

    try {
      final baseUrl = await _urlResolver.resolveUrl();
      if (baseUrl == null || baseUrl.isEmpty) {
        _isConnecting = false;
        _scheduleReconnect();
        return;
      }

      final uri = Uri.parse(baseUrl);
      final wsScheme = uri.scheme == 'https' ? 'wss' : 'ws';
      final hostPort = uri.hasPort ? '${uri.host}:${uri.port}' : uri.host;
      final targetWsUrl = '$wsScheme://$hostPort/ws-pos/websocket';

      debugPrint('[WAITER WS DEBUG]\nCONNECTING\nurl=$targetWsUrl');

      // Primary WebSocket endpoints supported by Spring STOMP / SockJS fallback
      final candidateEndpoints = [
        targetWsUrl,
        '$wsScheme://$hostPort/ws-pos',
        '$wsScheme://$hostPort/ws/websocket',
        '$wsScheme://$hostPort/ws',
      ];

      WebSocket? socket;
      for (final endpoint in candidateEndpoints) {
        try {
          socket = await WebSocket.connect(endpoint).timeout(
            const Duration(seconds: 4),
          );
          if (socket.readyState == WebSocket.open) {
            break;
          }
        } catch (_) {
          socket = null;
        }
      }

      if (socket == null || socket.readyState != WebSocket.open) {
        debugPrint('[WAITER WS DEBUG]\nCONNECTION ERROR\nFailed to establish socket connection');
        _isConnecting = false;
        _scheduleReconnect();
        return;
      }

      _socket = socket;

      // Listen to raw WebSocket data frames
      _socketSubscription = _socket!.listen(
        _handleRawFrame,
        onError: (err) {
          debugPrint('[WAITER WS DEBUG]\nCONNECTION ERROR\n$err');
          _onConnectionClosed();
        },
        onDone: () {
          debugPrint('[WebSocket] Connection closed');
          _onConnectionClosed();
        },
      );

      // Send authenticated STOMP CONNECT frame with Authorization header
      final connectFrame =
          'CONNECT\naccept-version:1.1,1.2\nheart-beat:10000,10000\nAuthorization:Bearer $token\n\n\x00';
      _socket!.add(connectFrame);
      _isConnecting = false;
    } catch (e) {
      debugPrint('[WAITER WS DEBUG]\nCONNECTION ERROR\n$e');
      _isConnecting = false;
      _scheduleReconnect();
    }
  }

  void _handleRawFrame(dynamic rawData) {
    final text = rawData.toString();

    if (text.startsWith('CONNECTED')) {
      debugPrint('[WAITER WS DEBUG]\nCONNECTED');
      isConnectedNotifier.value = true;
      _cancelReconnectTimer();
      _startHeartbeat();

      // Subscribe to kitchen order topic for the restaurant
      if (_subscribedRestaurantId != null) {
        final destination = '/topic/restaurants/$_subscribedRestaurantId/kitchen/orders';
        debugPrint('[WAITER WS DEBUG]\nSUBSCRIBING\ndestination=$destination');
        final subscribeFrame =
            'SUBSCRIBE\nid:sub-0\ndestination:$destination\n\n\x00';
        _socket?.add(subscribeFrame);
        debugPrint('[WAITER WS DEBUG]\nSUBSCRIBED');
      }
    } else if (text.startsWith('MESSAGE')) {
      _parseStompMessage(text);
    }
  }

  void _parseStompMessage(String frameText) {
    try {
      final doubleNewlineIdx = frameText.indexOf('\n\n');
      if (doubleNewlineIdx == -1) return;

      var body = frameText.substring(doubleNewlineIdx + 2);
      if (body.endsWith('\x00')) {
        body = body.substring(0, body.length - 1);
      }
      body = body.trim();

      if (body.isNotEmpty) {
        final decoded = json.decode(body);
        if (decoded is Map<String, dynamic>) {
          final eventType = decoded['eventType'] ?? decoded['type'] ?? decoded['status'] ?? 'ORDER_EVENT';
          final orderId = decoded['id'] ?? decoded['orderId'] ?? 'N/A';
          debugPrint('[WAITER WS DEBUG]\nEVENT RECEIVED\neventType=$eventType\norderId=$orderId');
          _orderEventsController.add(decoded);
        }
      }
    } catch (e) {
      debugPrint('[WebSocket] Failed to parse message body: $e');
    }
  }

  void _startHeartbeat() {
    _pingTimer?.cancel();
    _pingTimer = Timer.periodic(const Duration(seconds: 10), (_) {
      if (_socket != null && _socket!.readyState == WebSocket.open) {
        try {
          _socket!.add('\n'); // Heartbeat ping frame
        } catch (_) {}
      }
    });
  }

  void _onConnectionClosed() {
    _socketSubscription?.cancel();
    _socketSubscription = null;
    _pingTimer?.cancel();
    _pingTimer = null;
    _socket = null;
    isConnectedNotifier.value = false;

    if (!_isManualDisconnect) {
      _scheduleReconnect();
    }
  }

  void _scheduleReconnect() {
    if (_isManualDisconnect) return;
    _reconnectTimer?.cancel();
    _reconnectTimer = Timer(const Duration(seconds: 5), () {
      connect();
    });
  }

  void _cancelReconnectTimer() {
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
  }

  Future<void> disconnect() async {
    _isManualDisconnect = true;
    _cancelReconnectTimer();
    _pingTimer?.cancel();
    _pingTimer = null;
    await _socketSubscription?.cancel();
    _socketSubscription = null;
    await _socket?.close();
    _socket = null;
    _subscribedRestaurantId = null;
    _isConnecting = false;
    isConnectedNotifier.value = false;
  }
}
