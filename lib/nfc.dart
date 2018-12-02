import 'dart:async';

import 'package:flutter/services.dart';

typedef Future<dynamic> MessageHandler(String message);

class Nfc {
  factory Nfc() => _instance;

  Nfc.private(MethodChannel channel) 
    : _channel = channel;
  
  // TODO: Change methodchannel name to include domain.
  static final Nfc _instance = Nfc.private(
    const MethodChannel('nfcplugin'));

  final MethodChannel _channel;

  MessageHandler _onMessage;

  // TODO: Be able to listen to different types of messages in different 
  // callbacks
  /// Sets up [MessageHandler] for incoming messages.
  void configure({
    MessageHandler onMessage
  }) {
    _onMessage = onMessage;
    _channel.setMethodCallHandler(_handleMethod);
    _channel.invokeMethod('configure');
  }

  Future<dynamic> _handleMethod(MethodCall call) async {
    switch (call.method) {
      case "onMessage":
        return _onMessage(call.arguments as String);
      default:
        print("method '${call.method}' is not implemented by _handleMethod.");
    }
  }
}
