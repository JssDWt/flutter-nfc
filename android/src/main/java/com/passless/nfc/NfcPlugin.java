package com.passless.nfc;

import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.Log;

import io.flutter.plugin.common.JSONMethodCodec;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.common.PluginRegistry.NewIntentListener;

import java.nio.charset.Charset;
import java.util.HashMap;

/** 
 * NfcPlugin --- Flutter plugin for handling nfc messages. 
 * @author Jesse de Wit
*/
public class NfcPlugin implements MethodCallHandler, NewIntentListener, 
  ActivityLifecycleCallbacks {
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
   * Value indicating whether the nfc feature is available on the device. 
   * Defaults to true.
   * */
  private boolean nfcAvailable = true;

  /** Value indicating whether the @link{NfcAdapter} is currently enabled. */
  private boolean nfcEnabled;

  /** Listens to nfc adapter state changes (off/on) */
  private BroadcastReceiver nfcStateChangeListener;
  
  /** IntentFilter for detecting nfc adapter state changes. */
  private IntentFilter nfcStateChangeIntentFilter
    = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
    
  /** 
   * Value indicating whether the current intent should been handled.
   * <p>
   * This value is used to be able to pass foreground dispatch intents to
   * the onResume method.
   * </p>
   */
  private boolean shouldHandleIntent = true;

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
    Log.w(LOGNAME, "registerWith called.");
    final MethodChannel methodChannel = new MethodChannel(
      registrar.messenger(), 
      "plugins.passless.com/nfc", 
      JSONMethodCodec.INSTANCE);
    final NfcPlugin plugin = new NfcPlugin(methodChannel, registrar);
    plugin.initialize();

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
  public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

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
    if (activity != registrar.activity()) {
      return;
    }

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

      // Check the current nfc adapter state.
      checkStateChange();
      
      // start the broadcast receiver to listen for nfc state changes (on/off)
      registrar.context().registerReceiver(
        nfcStateChangeListener, 
        nfcStateChangeIntentFilter);
    }
    
    // either this is a new instance, so intent should be handled, or 
    // onNewIntent set shouldHandleIntent to true. In either case, try to handle
    // the (nfc?) intent.
    if (shouldHandleIntent) {
      // TODO: consider adding a call to flutterView.setInitialRoute 
      // in order to avoid switching pages at the start.
      handleIntent();
    }
  }

  /**
   * Handles pausing of the activity.
   * @param activity The paused activity.
   */
  @Override
  public void onActivityPaused(Activity activity) {
    // Only do work when the 'right' activity is paused.
    if (activity != registrar.activity()) {
      return;
    }
    if (nfcAdapter == null) {
      Log.w(LOGNAME, "onActivityPaused: nfcAdapter is null.");
    }
    else {
      // App is no longer on foreground, so disable foreground dispatch.
      nfcAdapter.disableForegroundDispatch(registrar.activity());

      // Also unregister the broadcastreceiver.
      registrar.context().unregisterReceiver(nfcStateChangeListener);
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
  public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

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

    switch (action) {
      case NfcAdapter.ACTION_NDEF_DISCOVERED:
      case NfcAdapter.ACTION_TAG_DISCOVERED:
        Log.d(
          LOGNAME, 
          String.format("onNewIntent: Got nfc action '%s'", action));

        // onResume will handle the intent.
        // NOTE: flutter calls setIntent() for us, no need to do that here.
        this.shouldHandleIntent = true;

        // Let flutter know this plugin handles the intent.
        return true;
      default:
        Log.d(
          LOGNAME, 
          String.format(
            "onNewIntent: action '%s' is not nfc. returning.", 
            action));
        return false;
    }
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

        HashMap<String, Boolean> props = new HashMap<String, Boolean>();
        props.put("nfcAvailable", nfcAvailable);
        props.put("nfcEnabled", nfcEnabled);
        result.success(props);
        break;
      case "gotoNfcSettings":
        if (!nfcAvailable) {
          result.error("1", "NFC settings not available.", null);
        }
        else {
          Intent intent = new Intent(Settings.ACTION_NFC_SETTINGS);
          registrar.activity().startActivity(intent);
          result.success(null);
        }
        break;
      default:
        Log.w(
          LOGNAME, 
          String.format(
            "onMethodCall: received call to '%s', but is not implemented",
            call.method));
          result.notImplemented();
        break;
    }
  }

  private void initialize() {
    // Discover nfc feature.
    Context context = registrar.context();
    Activity activity = registrar.activity();
    PackageManager pm = context.getPackageManager();
    if (!pm.hasSystemFeature(PackageManager.FEATURE_NFC))
    {
      nfcAvailable = false;
      Log.w(LOGNAME, "nfc feature not found on device.");
      return;
    }

    Log.d(LOGNAME, "nfc feature found on device.");
    nfcAdapter = NfcAdapter.getDefaultAdapter(context);   
    if (nfcAdapter == null) {
        nfcAvailable = false;
        Log.w(LOGNAME, "nfc not supported by device (nfcAdapter is null).");
        return;
    }
    
    Log.d(LOGNAME, "nfc adapter found.");

    // TODO: Add more intentfilters for different usecases:
    // (ACTION_TECH_DISCOVERED, TAG_LOST?).
    nfcFilters = new IntentFilter[] { 
      new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
      new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
    };

    // Need this to enable/disable foreground dispatch.
    nfcPendingIntent = PendingIntent.getActivity(
      activity, 
      0, 
      new Intent(activity, activity.getClass())
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 
      0);  

    nfcStateChangeListener = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        checkStateChange();
      }
    };

    // Get the current nfc adapter state.
    nfcEnabled = nfcAdapter.isEnabled();
  }
  private void checkStateChange() {
    boolean nowEnabled = nfcAdapter.isEnabled();
    if (nowEnabled != nfcEnabled) {
      nfcEnabled = nowEnabled;
      notifyNfcState();
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

  private void notifyNfcState() {
    this.methodChannel.invokeMethod(
      "setNfcEnabled",
      nfcEnabled,
      new MethodChannelResult(LOGNAME, "setNfcEnabled")
    );
  }

  /**
   * Handles the current intent.
   * <p>
   * Should only be called when the intent has not been handled before.
   * </p>
   */
  private void handleIntent() {
    Log.i(LOGNAME, "handleIntent: begin");
    shouldHandleIntent = false;
    Intent intent = registrar.activity().getIntent();

    if (intent == null) {
      Log.w(LOGNAME, "handleIntent: intent is null, returning.");
      return;
    }
    
    // TODO: Handle other action types, such as TAG and TECH as well.
    if (!NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
      Log.w(
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
      Log.w(LOGNAME, "handleIntent: isConfigured==true, handling message.");
      handleMessage(message);
    }
    else {
      Log.w(LOGNAME, "handleIntent: isConfigured==false, buffering message.");
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
    methodChannel.invokeMethod(
      "onMessage", 
      payload, 
      new MethodChannelResult(LOGNAME, "onMessage"));
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