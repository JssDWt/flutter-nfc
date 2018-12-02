package com.passless.nfc;

import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.common.PluginRegistry.NewIntentListener;

import java.nio.charset.Charset;

/** NfcPlugin */
public class NfcPlugin implements MethodCallHandler, NewIntentListener, 
  ActivityLifecycleCallbacks {
  static final String CHANNEL = "flutter.passless.com/nfcchannel";
  static final String STATE_INTENT_HANDLED = 
    "com.passless.nfc.state.intent_handled";
  static final String LOGNAME = "NfcPlugin";

  private final MethodChannel methodChannel;
  private final Registrar registrar;
  private NfcAdapter nfcAdapter;
  private PendingIntent nfcPendingIntent;
  private IntentFilter[] nfcFilters;
  private boolean intentHandled = false;
  private boolean isConfigured = false;
  private NdefMessage unhandledMessage;

  /** Plugin registration. */
  public static void registerWith(Registrar registrar) {
    Log.i(LOGNAME, "registerWith: begin");
    final MethodChannel methodChannel = 
      new MethodChannel(registrar.messenger(), "nfcplugin");
    final NfcPlugin plugin = new NfcPlugin(methodChannel, registrar);

    // Make sure the plugin listens to new intents on foreground dispatch.
    registrar.addNewIntentListener(plugin);

    // Hook in to the other application lifecycle callbacks.
    registrar.activity().getApplication()
      .registerActivityLifecycleCallbacks(plugin);

    // Make this plugin available for methodcalls from flutter dart.
    methodChannel.setMethodCallHandler(plugin);
    Log.i(LOGNAME, "registerWith: end");
  }

  private NfcPlugin(MethodChannel channel, Registrar registrar) {
    this.methodChannel = channel;
    this.registrar = registrar;
  }

  @Override
  public void onActivityCreated(
    Activity activity, 
    Bundle savedInstanceState) {
      Log.i(LOGNAME, "onActivityCreated: begin");
      if (activity == registrar.activity()) {
        if(savedInstanceState != null) {
          Log.i(
            LOGNAME, 
            "About to get STATE_INTENT_HANDLED from savedInstanceState.");
          this.intentHandled = 
            savedInstanceState.getBoolean(STATE_INTENT_HANDLED);
            Log.i(
              LOGNAME, 
              String.format(
                "Got STATE_INTENT_HANDLED: %b", 
                this.intentHandled));
        }
    
        setNfcSettings();
      }
    
      Log.i(LOGNAME, "onActivityCreated: end");
  }

  @Override
  public void onActivityStarted(Activity activity) {}

  @Override
  public void onActivityResumed(Activity activity) {
    Log.i(LOGNAME, "onActivityResumed: begin");
    if (activity == registrar.activity()) {
      if (nfcAdapter != null) {
        Log.i(LOGNAME, "about to enable foreground dispatch.");
        // receive nfc intents in the foreground when available.
        nfcAdapter.enableForegroundDispatch(
          registrar.activity(), 
          nfcPendingIntent, 
          nfcFilters, 
          null);
        Log.i(LOGNAME, "Enabled foreground dispatch.");
      }
      
      // either this is a new instance, so intenthandled is false, or 
      // onnewintent set intenthandled to false. In either case, try to handle
      // the (nfc) intent.
      if (!intentHandled) {
        handleIntent();
      }
    }

    Log.i(LOGNAME, "onActivityResumed: end");
  }

  @Override
  public void onActivityPaused(Activity activity) {
    Log.i(LOGNAME, "onActivityPaused: begin");
    if (activity == registrar.activity()) {
      if (nfcAdapter != null) {
        // App is no longer on foreground, so disable foreground dispatch.
        Log.i(LOGNAME, "About to disable foreground dispatch.");
        nfcAdapter.disableForegroundDispatch(registrar.activity());
        Log.i(LOGNAME, "Disabled foreground dispatch.");
      }
    }

    Log.i(LOGNAME, "onActivityPaused: end");
  }

  @Override
  public void onActivityStopped(Activity activity) { }

  @Override
  public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    Log.i(LOGNAME, "onActivitySaveInstanceState: begin");
    if (activity == registrar.activity()) {
      Log.i(LOGNAME, "About to set STATE_INTENT_HANDLED");
      // Always save the current intenthandled state.
      outState.putBoolean(STATE_INTENT_HANDLED, intentHandled);
      Log.i(LOGNAME, "STATE_INTENT_HANDLED set.");
    }

    Log.i(LOGNAME, "onActivitySaveInstanceState: end");
  }

  @Override
  public void onActivityDestroyed(Activity activity) { }

  @Override
  public boolean onNewIntent(Intent intent) {
    Log.i(LOGNAME, "onNewIntent: begin");
    if (intent == null) {
      Log.i(LOGNAME, "onNewIntent: intent is null.");
      return false;
    }

    String action = intent.getAction();
    Log.i(LOGNAME, String.format("Intent action = '%s'", action));
    
    if (!NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
      Log.i(LOGNAME, "onNewIntent: action is not ACTION_NDEF_DISCOVERED.");
      return false;
    }

    // onResume will handle the intent.
    this.intentHandled = false;

    Log.i(LOGNAME, "onNewIntent: end");

    // TODO: call setintent here? Or does flutter do that for us?
    return true;
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    Log.i(LOGNAME, "onMethodCall: begin");
    if (call.method.equals("configure")) {
      onConfigure();
      result.success(true);
    } else {
      result.notImplemented();
    }

    Log.i(LOGNAME, "onMethodCall: end");
  }

  private void onConfigure() {
    this.isConfigured = true;
    if (this.unhandledMessage != null) {
      handleMessage(this.unhandledMessage);
      this.unhandledMessage = null;
    }
  }

  private void setNfcSettings() {
    Log.i(LOGNAME, "setNfcSettings: begin");

    // TODO: Handle non-existant nfc adapter gracefully.
    nfcAdapter = NfcAdapter.getDefaultAdapter(registrar.context());   
    if (nfcAdapter == null) {
        Log.w(LOGNAME, "nfc not supported by device.");
        return;
    }
    
    // TODO: Add more intentfilters for different usecases.
    nfcFilters = new IntentFilter[] { 
      new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
    };
    
    Log.i(LOGNAME, "About to set PendingIntent.");
    // Need this to enable/disable foreground dispatch.
    nfcPendingIntent = PendingIntent.getActivity(
      registrar.activity(), 
      0, 
      new Intent(registrar.activity(), registrar.activity().getClass())
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 
      0);
    Log.i(LOGNAME, "PendingIntent set.");
    Log.i(LOGNAME, "setNfcSettings: end");
  }

  private void handleIntent() {
    Log.i(LOGNAME, "handleIntent: begin");
    intentHandled = true;
    Intent intent = registrar.activity().getIntent();

    if (intent == null) {
      Log.i(LOGNAME, "intent is null, returning.");
      return;
    }
    
    // TODO: Handle other action types, such as tag and tech as well.
    if (!NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
      Log.i(
        LOGNAME, 
        "intent action does not match ACTION_NDEF_DISCOVERED, returning.");
      return;
    }
    
    NdefMessage[] messages = getMessages(intent);
    if (messages.length == 0) {
      Log.i(LOGNAME, "No NdefMessages found in intent. returning.");
      return;
    }

    // TODO: be able to handle multiple messages?
    NdefMessage message = messages[0];

    if (this.isConfigured) {
      handleMessage(message);
    }
    else {
      this.unhandledMessage = message;
    }

    Log.i(LOGNAME, "handleIntent: end");
  }

  public void handleMessage(NdefMessage message) {
    String[] payloads = getPayloads(message);

    // TODO: be able to handle multiple payloads.
    String payload = payloads[0];

    // Call the Dart side.
    methodChannel.invokeMethod("onMessage", payload, new Result() {
      @Override
      public void success(Object o) {
        Log.i(LOGNAME, "methodchannel returned success.");
      }
    
      @Override
      public void error(String code, String message, Object o) {
        Log.e(
          LOGNAME, 
          String.format(
            "methodchannel returned error. code: '%s'. Message: '%s'", 
            code, 
            message));
      }
    
      @Override
      public void notImplemented() {
        Log.e(LOGNAME, "methodchannel returned notimplemented.");
      }
    });
  }

  private NdefMessage[] getMessages(Intent intent) {
    // Fetch ndef messages from the intent
    Parcelable[] rawMessages =
    intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);

    NdefMessage[] messages;
    if (rawMessages == null) {
      Log.i(LOGNAME, "rawMessages is null.");
      messages = new NdefMessage[0];
    } else {
      Log.i(LOGNAME, 
        String.format("Found %d raw messages", rawMessages.length));
      messages = new NdefMessage[rawMessages.length];
      for (int i = 0; i < rawMessages.length; i++) {
          messages[i] = (NdefMessage) rawMessages[i];
      }
    }

    return messages;
  }

  private String[] getPayloads(NdefMessage message) {
    NdefRecord[] records = message.getRecords();
    Log.i(LOGNAME, String.format("Message has %d records.", records.length));

    // An NdefMessage always has at least one payload.
    String[] payloads = new String[records.length];
    for(int i = 0; i < records.length; i++) {
      byte[] data = records[i].getPayload();

      // TODO: Handle other charsets, this is UTF-8.
      String payload = new String(data, Charset.defaultCharset());
      Log.i(LOGNAME, String.format("payload %d, data: %s", i, payload));

      payloads[i] = payload;
    }

    return payloads;
  }
}
