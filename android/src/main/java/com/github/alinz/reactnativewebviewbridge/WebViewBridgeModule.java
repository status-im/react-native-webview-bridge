package com.github.alinz.reactnativewebviewbridge;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.VisibleForTesting;
import android.webkit.ValueCallback;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import javax.annotation.Nullable;

public class WebViewBridgeModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    public ValueCallback<Uri[]> callback;

    @VisibleForTesting
    public static final String REACT_CLASS = "WebViewBridgeModule";

    WebViewBridgeModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    private static String currentDapp;

    @ReactMethod
    public void setCurrentDapp(String dappName, Callback callback) {
        currentDapp = dappName;

        callback.invoke();
    }

    public static void setCurrentDapp(@Nullable String dappName) {
        currentDapp = dappName;
    }

    public static String getCurrentDapp() {
        return currentDapp;
    }

    @SuppressWarnings("unused")
    public Activity getActivity() {
        return getCurrentActivity();
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode != 1 || callback == null) {
            return;
        }

        Uri result = data == null || resultCode != Activity.RESULT_OK ? null : data.getData();
        if (callback != null) {
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    String dataString = data.getDataString();
                    ClipData clipData = data.getClipData();
                    if (clipData != null) {
                        results = new Uri[clipData.getItemCount()];
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            ClipData.Item item = clipData.getItemAt(i);
                            results[i] = item.getUri();
                        }
                    }
                    if (dataString != null)
                        results = new Uri[]{Uri.parse(dataString)};
                }
            }
            callback.onReceiveValue(results);
            callback = null;
        }
    }

    public void onNewIntent(Intent intent) {}
}
