import 'dart:async';

import 'package:flutter/material.dart';
import 'package:nfc/nfc.dart';
import 'package:nfc/nfc_provider.dart';

void main() => runApp(
  NfcProvider(
    child: MyApp(),
  )
);

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  // Initialize the nfc plugin high in the widget tree.
  Nfc _nfc;
  StreamSubscription _messageListener;
  String _currentMessage = "No message yet...";

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _nfc = NfcProvider.of(context);
    _messageListener = _nfc.messages.listen(_showMessage);
  }

  @override
  void dispose() {
    super.dispose();
    if (_messageListener != null) {
      _messageListener.cancel();
      _messageListener = null;
    }
  }

  void _showMessage(String message) {
    setState(() {
      _currentMessage = message;
    });
  }

  @override
  Widget build(BuildContext context) {
    print("running build");
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Nfc example app'),
        ),
        body: Center(
          child: !_nfc.isConfigured ? CircularProgressIndicator() : Column(
            children: <Widget>[
              Text("Nfc ${_nfc.nfcAvailable ? "is" : "is NOT"} available on this device."),
              Text("Nfc is turned ${_nfc.nfcEnabled ? "on" : "off"}."),
              Text("Last nfc message: '$_currentMessage'"),
              RaisedButton(
                child: Text("NFC settings"),
                onPressed: () {
                  _nfc.gotoNfcSettings();
                },
              )
            ],
          ),
        ),
      ),
    );
  }
}
