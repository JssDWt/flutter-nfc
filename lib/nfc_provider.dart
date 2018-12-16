import 'dart:async';

import 'package:flutter/material.dart';
import 'package:nfc/nfc.dart';

class NfcProvider extends StatefulWidget {
  final Widget child;
  NfcProvider({this.child});

  @override
  _NfcProviderState createState() => _NfcProviderState();

  static Nfc of(BuildContext context) {
    final _NfcProvider provider = 
      context.inheritFromWidgetOfExactType(_NfcProvider);
    return provider.nfc;
  }
}

class _NfcProviderState extends State<NfcProvider> {
  Nfc _nfc;
  StreamSubscription<bool> _nfcStateChange;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_nfc == null) {
      _nfc = Nfc(); 
    }

    if (_nfcStateChange == null) {
      _nfcStateChange = _nfc.nfcStateChange.listen((newValue) {
        setState((){});
      });
    }
  }

  @override
  void dispose() {
    super.dispose();
    if (_nfcStateChange != null) {
      _nfcStateChange.cancel();
      _nfcStateChange = null;
    }
  }

  @override
  Widget build(BuildContext context) {
    return _NfcProvider(
      nfc: _nfc,
      child: widget.child,
    );
  }
}

class _NfcProvider extends InheritedWidget {
  final Nfc nfc;

  _NfcProvider({ Key key, this.nfc, Widget child })
    : super(key: key, child: child);

  @override
  bool updateShouldNotify(_NfcProvider oldWidget) => 
    nfc.nfcEnabled != oldWidget.nfc.nfcEnabled
      || nfc.nfcAvailable != oldWidget.nfc.nfcAvailable;
}