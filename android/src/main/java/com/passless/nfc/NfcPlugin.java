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

/** 
 * NfcPlugin --- Flutter plugin for handling nfc messages. 
 * @author Jesse de Wit
*/
public class NfcPlugin implements MethodCallHandler, NewIntentListener, 
  ActivityLifecycleCallbacks {
  /** Name of saved version of @link{#intentHandled}. */
  static final String STATE_INTENT_HANDLED = 
    "com.passless.nfc.state.intent_handled";

  /** Name of the logger for this class. */
  static final String LOGNAME = NfcPlugin.class.getSimpleName();
  
  /** @link{MethodChannel} to pass messages to Dart. */
  private final MethodChannel methodChannel;

  /** The registrar that registered the current plugin. */
  private final Registrar registrar;

  /** @link{NfcAdapter} used to receive messages on foreground dispatch. */
  private NfcAdapter nfcAdapter;

  /** @link{PendingIntent} to listen to nfc intents on foreground dispatch. */
  private PendingIntent nfcPendingIntent;

  /** Filters used to receive nfc messages on foreground dispatch. */
  private IntentFilter[] nfcFilters;

  /** 
   * Value indicating whether the current intent has been handled.
   * <p>
   * This value is used to be able to pass foreground dispatch intents to
   * the onResume method.
   * </p>
   */
  private boolean intentHandled = false;

  /** 
   * Value indicating whether the Dart side of the app is ready to receive
   * messages from this plugin.
   */
  private boolean isConfigured = false;

  /** buffer for messages received before the Dart side was able to receive. */
  private NdefMessage unhandledMessage;

  /** 
   * Plugin registration. 
   * @param registrar @link{Registrar} that registers the plugin.
   */
  public static void registerWith(Registrar registrar) {
    final MethodChannel methodChannel = 
      new MethodChannel(registrar.messenger(), "plugins.passless.com/nfc");
    final NfcPlugin plugin = new NfcPlugin(methodChannel, registrar);

    // Make sure the plugin listens to new intents on foreground dispatch.
    registrar.addNewIntentListener(plugin);

    // Hook in to the other application lifecycle callbacks.
    registrar.activity().getApplication()
      .registerActivityLifecycleCallbacks(plugin);

    // Make this plugin available for methodcalls from flutter dart.
    methodChannel.setMethodCallHandler(plugin);
  }

  /** 
   * Initializes a new instance of the @link{NfcPlugin} class. 
   * @param channel @link{#channel}
   * @param registrar @link{#registrar}
   */
  private NfcPlugin(MethodChannel channel, Registrar registrar) {
    this.methodChannel = channel;
    this.registrar = registrar;
  }

  /**
   * Handles creation of the activity.
   * @param activity The created activity.
   * @param savedInstanceState Optional saved state for the activity.
   */
  @Override
  public void onActivityCreated(
    Activity activity, 
    Bundle savedInstanceState) {
      // Only do work when the 'right' activity is created.
      if (activity == registrar.activity()) {
        if(savedInstanceState != null) {
          this.intentHandled = 
            savedInstanceState.getBoolean(STATE_INTENT_HANDLED);
        }
    
        setNfcSettings();
      }    
  }

  /**
   * Handles activity start.
   * @param activity The started activity.
   */
  @Override
  public void onActivityStarted(Activity activity) {}

  /**
   * Handles resuming of the activity.
   * @param activity
   */
  @Override
  public void onActivityResumed(Activity activity) {
    // Only do work when the 'right' activity is resumed.
    if (activity == registrar.activity()) {
      if (nfcAdapter == null) {
        Log.w(LOGNAME, "onActivityResumed: nfcAdapter is null.");
      }
      else {
        // receive nfc intents in the foreground when available.
        nfcAdapter.enableForegroundDispatch(
          registrar.activity(), 
          nfcPendingIntent, 
          nfcFilters, 
          null);
      }
      
      // either this is a new instance, so intenthandled is false, or 
      // onNewIntent set intenthandled to false. In either case, try to handle
      // the (nfc) intent.
      if (!intentHandled) {
        // TODO: consider adding a call to flutterView.setInitialRoute 
        // in order to avoid switching pages at the start.
        handleIntent();
      }
    }
  }

  /**
   * Handles pausing of the activity.
   * @param activity The paused activity.
   */
  @Override
  public void onActivityPaused(Activity activity) {
    // Only do work when the 'right' activity is paused.
    if (activity == registrar.activity()) {
      if (nfcAdapter == null) {
        Log.w(LOGNAME, "onActivityPaused: nfcAdapter is null.");
      }
      else {
        // App is no longer on foreground, so disable foreground dispatch.
        nfcAdapter.disableForegroundDispatch(registrar.activity());
      }
    }
  }

  /**
   * Handles stopping of the activity.
   * @param activity The stopped activity.
   */
  @Override
  public void onActivityStopped(Activity activity) { }

  /**
   * Handles saving the instance state of the activity.
   * @param activity The activity to save state for.
   * @param outState The state that will be saved.
   */
  @Override
  public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    // Only do work when the 'right' activity is saving state.
    if (activity == registrar.activity()) {
      outState.putBoolean(STATE_INTENT_HANDLED, intentHandled);
    }
  }

  /**
   * Handles destruction of the activity.
   * @param activity The activity destroyed.
   */
  @Override
  public void onActivityDestroyed(Activity activity) { }

  /**
   * Handles new intents when the activity is already on the foreground.
   * @param intent The intent received.
   * @return A value indicating whether this plugin has handled the intent.
   */
  @Override
  public boolean onNewIntent(Intent intent) {
    if (intent == null) {
      Log.w(LOGNAME, "onNewIntent: intent is null.");
      return false;
    }

    String action = intent.getAction();
    Log.d(LOGNAME, String.format("Intent action = '%s'", action));
    
    // TODO: Handle TAG and TECH(?) as well.
    if (!NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
      Log.d(
        LOGNAME, 
        "onNewIntent: action is not ACTION_NDEF_DISCOVERED returning.");
      return false;
    }

    // onResume will handle the intent.
    // NOTE: flutter calls setIntent() for us, no need to do that here.
    this.intentHandled = false;

    // Let flutter know this plugin handles the intent.
    return true;
  }

  /**
   * @link{MethodCallHandler} implementation to receive calls over the 
   * @link{#methodChannel}.
   * @param call The invoking method call.
   * @param result The result that will be returned.
   */
  @Override
  public void onMethodCall(MethodCall call, Result result) {
    switch (call.method) {
      case "configure":
        onConfigure();
        result.success(null);
        break;
      default:
      Log.w(
        LOGNAME, 
        String.format(
          "onMethodCall: received call to '%s', but is not implemented",
          call.method));
        result.notImplemeted();
        break;
    }
  }

  /**
   * Called when the Dart side is ready to receive messages.
   */
  private void onConfigure() {
    // Indicate the plugin is ready to receive messages.
    this.isConfigured = true;

    // Handle any already received unhandled messages.
    if (this.unhandledMessage != null) {
      Log.d(LOGNAME, "onConfigure: handling previously buffered message.");
      handleMessage(this.unhandledMessage);
      this.unhandledMessage = null;
    }
  }

  /**
   * Sets the required variables for receiving messages from nfc.
   */
  private void setNfcSettings() {
    // TODO: Handle non-existant nfc adapter gracefully.
    nfcAdapter = NfcAdapter.getDefaultAdapter(registrar.context());   
    if (nfcAdapter == null) {
        Log.w(LOGNAME, "nfc not supported by device.");
        return;
    }
    
    // TODO: Add more intentfilters for different usecases (TAG, TECH).
    nfcFilters = new IntentFilter[] { 
      new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
    };
    
    // Need this to enable/disable foreground dispatch.
    nfcPendingIntent = PendingIntent.getActivity(
      registrar.activity(), 
      0, 
      new Intent(registrar.activity(), registrar.activity().getClass())
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 
      0);
  }

  /**
   * Handles the current intent.
   * <p>
   * Should only be called when the intent has not been handled before.
   * </p>
   */
  private void handleIntent() {
    Log.i(LOGNAME, "handleIntent: begin");
    intentHandled = true;
    Intent intent = registrar.activity().getIntent();

    if (intent == null) {
      Log.d(LOGNAME, "handleIntent: intent is null, returning.");
      return;
    }
    
    // TODO: Handle other action types, such as TAG and TECH as well.
    if (!NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
      Log.d(
        LOGNAME, 
        "intent action does not equal ACTION_NDEF_DISCOVERED, returning.");
      return;
    }
    
    // Fetch the messages from the intent.
    NdefMessage[] messages = getMessages(intent);
    if (messages.length == 0) {
      // NOTE: This should not be possible. But it is here for completeness.
      Log.w(
        LOGNAME, 
        "handleIntent: No NdefMessages found in intent. returning.");
      return;
    } 
    else if (messages.length > 1){
      Log.w(
        LOGNAME, 
        "More than one message found. Only the first will be processed.");
    }

    // TODO: be able to handle multiple messages?
    NdefMessage message = messages[0];

    // If Dart is not yet ready to receive the message, buffer it.
    if (this.isConfigured) {
      handleMessage(message);
    }
    else {
      Log.d(LOGNAME, "handleIntent: isConfigured==false, buffering message.");
      this.unhandledMessage = message;
    }
  }

  /**
   * Handles the specified message and sends it to the Dart side.
   * @param message The message to handle.
   */
  public void handleMessage(NdefMessage message) {
    String[] payloads = getPayloads(message);

    if (payloads.length == 0) {
      Log.w(LOGNAME, "handleMessage: No payloads found on message.");
      return;
    }

    if (payloads.length > 1) {
      Log.w(
        LOGNAME, 
        "handleMessage: More than one payload was found. " +
        "Only the first will be processed.");
    }

    // TODO: Be able to handle multiple payloads.
    String payload = payloads[0];

    // Call the Dart side.
    methodChannel.invokeMethod("onMessage", payload, new Result(){
      /**
       * Handles success result.
       * @param o The object returned on success.
       */
      @Override
      public void success(Object o) {
        Log.d(LOGNAME, "methodchannel returned success.");
      }

      /**
       * Handles error result.
       * @param code The error code.
       * @param message The error message.
       * @param details Any error details if available.
       */
      @Override
      public void error(String code, String message, Object details) {
        Log.e(
          LOGNAME, 
          String.format(
            "methodchannel returned error. code: '%s'. Message: '%s'", 
            code, 
            message));
      }
      
      /**
       * Handles not implemented result.
       */
      @Override
      public void notImplemented() {
        Log.e(LOGNAME, "methodchannel returned notimplemented.");
      }
    });
  }

  /**
   * Gets @link{NdefMessage}s from the specified @link{Intent}.
   * @param intent The intent to get messages from.
   * @return found messages.
   */
  private NdefMessage[] getMessages(Intent intent) {
    // Fetch ndef messages from the intent
    Parcelable[] rawMessages =
      intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);

    NdefMessage[] messages;
    if (rawMessages == null) {
      Log.w(LOGNAME, "getMessages: rawMessages is null.");
      messages = new NdefMessage[0];
    } 
    else {
      messages = new NdefMessage[rawMessages.length];
      for (int i = 0; i < rawMessages.length; i++) {
          messages[i] = (NdefMessage) rawMessages[i];
      }
    }

    return messages;
  }

  /**
   * Gets the payloads from the specified message.
   * <p>
   * Currently the payloads are processed as UTF-8 string.
   * </p>
   * @param message
   * @return Array containing the payloads strings.
   */
  private String[] getPayloads(NdefMessage message) {
    NdefRecord[] records = message.getRecords();

    if (records == null) {
      Log.w(LOGNAME, "getPayloads: payloads is null.");
      return new String[0];
    }

    String[] payloads = new String[records.length];
    for(int i = 0; i < records.length; i++) {
      byte[] data = records[i].getPayload();

      // TODO: Handle other charsets, this is UTF-8.
      String payload = new String(data, Charset.defaultCharset());
      payloads[i] = payload;
    }

    return payloads;
  }
}