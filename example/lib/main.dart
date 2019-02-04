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
  String _currentMessage = "No message yet...";

  @override
  void initState() {
    super.initState();
    _configureNfc();
  }

  Future<void> _configureNfc() async {
    await Nfc().configure(onMessage: _showMessage);
    setState(() {});
  }

  Future<void> _showMessage(String message) async {
    setState(() {
      _currentMessage = message;
    });
  }

  @override
  Widget build(BuildContext context) {
    final nfc = NfcProvider.of(context);
    print("running build");
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Nfc example app'),
        ),
        body: Center(
          child: !nfc.isConfigured ? CircularProgressIndicator() : Column(
            children: <Widget>[
              Text("Nfc ${nfc.nfcAvailable ? "is" : "is NOT"} available on this device."),
              Text("Nfc is turned ${nfc.nfcEnabled ? "on" : "off"}."),
              Text("Last nfc message: '$_currentMessage'"),
              RaisedButton(
                child: Text("NFC settings"),
                onPressed: () {
                  nfc.gotoNfcSettings();
                },
              )
            ],
          ),
        ),
      ),
    );
  }
}
