import 'dart:async';

import 'package:flutter/material.dart';
import 'package:nfc/nfc.dart';

void main() => runApp(MyApp());


class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  // Initialize the nfc plugin high in the widget tree.
  final Nfc _nfc = Nfc();
  String _currentMessage = "No message yet...";
  StreamSubscription<bool> _nfcStateChangeSubscription;
  bool _isConfigured = false;

  @override
  void initState() {
    super.initState();

    // configure the plugin early. No messages are received before the plugin is
    // configured.
    _nfc.configure(
      onMessage: (String message) async {
        print("onMessage: $message");
        _showMessage(message);
      }
    ).then((r) {
      setState(() => _isConfigured = true);
    } );

    // After calling configure, the current nfc state values can be fetched.
    // bool nfcEnabled = _nfc.nfcEnabled;

    // Listen to nfc state changes (nfc adapter is turned on/off)
    _nfcStateChangeSubscription = _nfc.nfcStateChange.listen((nowEnabled) {
      setState(() {});
    });
  }

  @override
  void dispose() {
    super.dispose();
    _nfcStateChangeSubscription.cancel();
  }
  void _showMessage(String message) {
    setState(() {
      _currentMessage = message;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Nfc example app'),
        ),
        body: Center(
          child: !_isConfigured ? CircularProgressIndicator() : Column(
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
