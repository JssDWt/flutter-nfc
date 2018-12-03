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
  String currentMessage = "No message yet...";

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
    );
  }

  void _showMessage(String message) {
    setState(() {
      currentMessage = message;
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
          child: Text(currentMessage),
        ),
      ),
    );
  }
}
