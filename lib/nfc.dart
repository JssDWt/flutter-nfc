import 'dart:async';
import 'package:flutter/services.dart';

typedef Future<dynamic> MessageHandler(String message);

// TODO: Make this a stateful widget?
/// Nfc plugin part of the Dart side.
class Nfc {

  /// Returns an Nfc instance.
  factory Nfc() => _instance;

  /// Initializes a new instance of the Nfc class.
  Nfc.private(MethodChannel channel) 
    : _channel = channel;
  
  /// Singleton instance of the Nfc plugin class.
  static final Nfc _instance = Nfc.private(
    const MethodChannel('plugins.passless.com/nfc', JSONMethodCodec()));

  /// The channel used to pass/receive messages to native platforms.
  final MethodChannel _channel;

  /// Callback handler called when a new ndef message is received.
  MessageHandler _onMessage;

  /// Value indicating whether nfc is available on the current device.
  bool nfcAvailable = false;

  /// Value indicating whether nfc is currently enabled on the device.
  bool nfcEnabled = false;

  /// Value indicating whether nfc has been configured.
  bool isConfigured = false;

  /// Stream transmits a value indicating whether the nfc adapter is enabled.
  /// A new value is streamed when the state changes.
  Stream<bool> get nfcStateChange => _nfcStateController.stream;

  // NOTE: No need to close the streamcontroller, because this is a singleton.
  /// Private controller for nfc state change events.
  StreamController<bool> _nfcStateController 
    = StreamController<bool>.broadcast();

  /// Stream transmits messages received over nfc.
  Stream<String> get messages => _nfcMessageController.stream;

  // NOTE: No need to close the streamcontroller, because this is a singleton.
  /// Private controller for received ndef messages.
  StreamController<String> _nfcMessageController 
    = StreamController<String>.broadcast();
 
  /// Sets up the nfc plugin, in order to notify the native part that dart
  /// is ready to receive messages. After configuration is complete, 
  /// [isConfigured] will be `true`, the values for [nfcAvailable] and
  /// [nfcEnabled] will be set and the broadcast streams are initialized.
  Future<void> configure() async {
    _channel.setMethodCallHandler(_handleMethod);
    Map<String, dynamic> result = await _channel.invokeMethod('configure');
    nfcAvailable = result["nfcAvailable"] as bool;
    nfcEnabled = result["nfcEnabled"] as bool;
    isConfigured = true;
  }

  /// Navigate to the nfc settings on the phone.
  Future<void> gotoNfcSettings() async {
    await _channel.invokeMethod('gotoNfcSettings');
  }

  /// Handles methodchannel calls from the native portions of the plugin.
  Future<dynamic> _handleMethod(MethodCall call) async {
    print(call);
    switch (call.method) {
      case "setNfcEnabled":
        nfcEnabled = call.arguments as bool;
        _nfcStateController.sink.add(nfcEnabled);
        return "nothing";
      case "onMessage":
        return _onMessage(call.arguments as String);

      default:
        print("method '${call.method}' is not implemented by _handleMethod.");
    }
  }
}
