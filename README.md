# nfc

Flutter plugin for sending and receiving NFC (Ndef) messages.

The package is currently not yet on publang. 

## Supported features
- Receiving Ndef message with a single record. The record will be interpreted as UTF-8 string.
- Currently only for Android. iOS will follow.

## Installation
Clone the github repo next to your flutter project.
Add the following to your publang.yaml:
```yaml
dependencies:
  ...
  nfc:
    path: ../flutter-nfc
  ...
```

In `android/source/main/AndroidManifest.xml` add the required permissions and, optionally, intent filters.
```xml
<!-- Include the NFC permission-->
<uses-permission android:name="android.permission.NFC" />

<!-- Optional: tell Google play store that nfc is required to install the app -->
<uses-feature android:name="android.hardware.nfc" android:required="true" />

<!-- Add intent filters under your activity -->
<intent-filter>
    <action android:name="android.nfc.action.NDEF_DISCOVERED"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <data android:mimeType="application/mymimetype+json" />
</intent-filter>
```

## Usage
Make sure to configure nfc high up in the widget tree, in order to be able to receive messages. No messages are received to the Dart side until the nfc plugin is configured.
Provide a callback method to `onMessage` in order to receive the nfc message.
```dart
final Nfc _nfc = Nfc();

@override 
void initState() {
  _nfc.configure(
    onMessage: (String message) async {
      print(message);
      await _showMessage(message);
    }
  );
}
```
