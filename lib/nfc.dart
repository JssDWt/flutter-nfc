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

  /// Stream transmits a value indicating whether the nfc adapter is enabled.
  /// A new value is streamed when the state changes.
  Stream<bool> get nfcStateChange => _nfcStateController.stream;

  // NOTE: No need to close the streamcontroller, because this is a singleton.
  /// Private controller for nfc state change events.
  StreamController<bool> _nfcStateController = StreamController<bool>.broadcast();
 
  // TODO: Be able to listen to different types of messages in different 
  // callbacks
  /// Sets up [MessageHandler] for incoming messages and starts receiving 
  /// messages from the nfc adapter.
  Future<void> configure({
    MessageHandler onMessage
  }) async {
    _onMessage = onMessage;
    _channel.setMethodCallHandler(_handleMethod);
    Map<String, dynamic> result = await _channel.invokeMethod('configure');
    nfcAvailable = result["nfcAvailable"] as bool;
    nfcEnabled = result["nfcEnabled"] as bool;
  }

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
