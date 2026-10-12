import 'package:flutter/material.dart';
import '../../../../core/network/backend_url_resolver.dart';
import '../../../../core/storage/secure_storage_service.dart';
import '../../../../shared/widgets/custom_card.dart';

class ConnectionPage extends StatefulWidget {
  final VoidCallback? onConnected;
  final BackendUrlResolver? urlResolver;
  final SecureStorageService? storageService;

  const ConnectionPage({
    super.key,
    this.onConnected,
    this.urlResolver,
    this.storageService,
  });

  @override
  State<ConnectionPage> createState() => _ConnectionPageState();
}

class _ConnectionPageState extends State<ConnectionPage> {
  late final BackendUrlResolver _urlResolver;
  late final SecureStorageService _storageService;

  bool _isSearching = true;
  String? _resolvedUrl;

  @override
  void initState() {
    super.initState();
    _storageService = widget.storageService ?? SecureStorageService();
    _urlResolver = widget.urlResolver ??
        BackendUrlResolver(storageService: _storageService);
    _startDiscovery();
  }

  Future<void> _startDiscovery({bool force = false}) async {
    setState(() {
      _isSearching = true;
    });

    final url = await _urlResolver.resolveUrl(forceRediscovery: force);

    if (!mounted) return;

    if (url != null && url.isNotEmpty) {
      setState(() {
        _isSearching = false;
        _resolvedUrl = url;
      });
      if (widget.onConnected != null) {
        widget.onConnected!();
      } else {
        Navigator.of(context).pushReplacementNamed('/');
      }
    } else {
      setState(() {
        _isSearching = false;
      });
    }
  }

  Future<void> _showAdvancedDialog() async {
    final currentManual = await _storageService.getManualServerUrl();
    final controller = TextEditingController(text: currentManual ?? '');
    String? dialogError;
    bool isTesting = false;

    if (!mounted) return;

    await showDialog(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              title: const Text('Advanced Server Address'),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Text(
                    'Enter POS server IP or hostname (e.g. 192.168.1.42:8080):',
                    style: TextStyle(fontSize: 14),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: controller,
                    decoration: InputDecoration(
                      labelText: 'Server Address',
                      hintText: 'http://192.168.1.42:8080',
                      border: const OutlineInputBorder(),
                      errorText: dialogError,
                    ),
                  ),
                  if (isTesting) ...[
                    const SizedBox(height: 12),
                    const Row(
                      children: [
                        SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        ),
                        SizedBox(width: 8),
                        Text('Testing connection...', style: TextStyle(fontSize: 13)),
                      ],
                    ),
                  ],
                ],
              ),
              actions: [
                if (currentManual != null && currentManual.isNotEmpty)
                  TextButton(
                    onPressed: isTesting
                        ? null
                        : () async {
                            await _storageService.clearManualServerUrl();
                            if (dialogContext.mounted) {
                              Navigator.of(dialogContext).pop();
                            }
                            _startDiscovery(force: true);
                          },
                    child: const Text('Clear Override', style: TextStyle(color: Colors.red)),
                  ),
                TextButton(
                  onPressed: isTesting
                      ? null
                      : () => Navigator.of(dialogContext).pop(),
                  child: const Text('Cancel'),
                ),
                ElevatedButton(
                  onPressed: isTesting
                      ? null
                      : () async {
                          final text = controller.text.trim();
                          if (text.isEmpty) {
                            setDialogState(() {
                              dialogError = 'Please enter a server address';
                            });
                            return;
                          }

                          setDialogState(() {
                            isTesting = true;
                            dialogError = null;
                          });

                          final normalized = _urlResolver.normalizeUrl(text);
                          final isHealthy = await _urlResolver.checkHealth(normalized);

                          if (!dialogContext.mounted) return;

                          if (isHealthy) {
                            await _storageService.saveManualServerUrl(normalized);
                            if (dialogContext.mounted) {
                              Navigator.of(dialogContext).pop();
                            }
                            _startDiscovery();
                          } else {
                            setDialogState(() {
                              isTesting = false;
                              dialogError = 'Could not connect to $normalized. Please verify server is running.';
                            });
                          }
                        },
                  child: const Text('Save & Connect'),
                ),
              ],
            );
          },
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Connection Status'),
      ),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24.0),
            child: CustomCard(
              child: Padding(
                padding: const EdgeInsets.all(8.0),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const SizedBox(height: 16),
                    Text(
                      _isSearching
                          ? 'Connecting to Restaurant POS...'
                          : (_resolvedUrl != null
                              ? 'Connected to Backend'
                              : 'Could not find Restaurant POS server'),
                      style: theme.textTheme.headlineSmall,
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 8),
                    if (_isSearching) ...[
                      const SizedBox(height: 12),
                      const Center(child: CircularProgressIndicator()),
                      const SizedBox(height: 16),
                      Text(
                        'Searching local network...',
                        style: theme.textTheme.bodyMedium?.copyWith(color: Colors.grey[700]),
                        textAlign: TextAlign.center,
                      ),
                    ] else if (_resolvedUrl != null) ...[
                      Text(
                        'Server URL: $_resolvedUrl',
                        style: theme.textTheme.bodyMedium?.copyWith(color: Colors.green[800]),
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 20),
                      ElevatedButton(
                        onPressed: () {
                          if (widget.onConnected != null) {
                            widget.onConnected!();
                          } else {
                            Navigator.of(context).pushReplacementNamed('/');
                          }
                        },
                        child: const Text('Continue'),
                      ),
                    ] else ...[
                      const SizedBox(height: 12),
                      Text(
                        'Please check:',
                        style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                      ),
                      const SizedBox(height: 8),
                      const Text('• Phone and POS laptop are connected to the same Wi-Fi.'),
                      const SizedBox(height: 4),
                      const Text('• POS server is running.'),
                      const SizedBox(height: 4),
                      const Text('• Windows Firewall allows port 8080.'),
                      const SizedBox(height: 24),
                      Row(
                        children: [
                          Expanded(
                            child: OutlinedButton(
                              onPressed: () => _startDiscovery(force: true),
                              child: const Text('Retry'),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: ElevatedButton(
                              onPressed: _showAdvancedDialog,
                              child: const Text('Advanced Address'),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
