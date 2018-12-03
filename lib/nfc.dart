import 'dart:async';
import 'package:flutter/services.dart';

typedef Future<dynamic> MessageHandler(String message);

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

  // TODO: Be able to listen to different types of messages in different 
  // callbacks
  /// Sets up [MessageHandler] for incoming messages and starts receiving 
  /// messages from the nfc adapter.
  void configure({
    MessageHandler onMessage
  }) {
    _onMessage = onMessage;
    _channel.setMethodCallHandler(_handleMethod);
    _channel.invokeMethod('configure');
  }

  /// Handles methodchannel calls from the native portions of the plugin.
  Future<dynamic> _handleMethod(MethodCall call) async {
    switch (call.method) {
      case "onMessage":
        return _onMessage(call.arguments as String);
      default:
        print("method '${call.method}' is not implemented by _handleMethod.");
    }
  }
}
