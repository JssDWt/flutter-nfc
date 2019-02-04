package com.passless.plugins.nfc;

import android.util.Log;
import io.flutter.plugin.common.MethodChannel.Result;

public class MethodChannelResult implements Result {
    private String logName;
    private String method;

    public MethodChannelResult(String logname, String method) {
        this.logName = logname;
        this.method = method;
    }

    /**
     * Handles success result.
     * @param o The object returned on success.
     */
    @Override
    public void success(Object o) {
        Log.d(
            logName, 
            String.format("methodchannel '%s' returned success.", method));
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
            logName, 
            String.format(
                "methodchannel '%s' returned error. code: '%s'. Message: '%s'", 
                method,
                code, 
                message));
    }

    /**
     * Handles not implemented result.
     */
    @Override
    public void notImplemented() {
        Log.e(logName, String.format(
            "methodchannel '%s' returned notimplemented.",
            method));
    }
}