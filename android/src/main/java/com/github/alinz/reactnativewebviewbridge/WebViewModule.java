package com.github.alinz.reactnativewebviewbridge;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ActivityEventListener;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.webkit.ValueCallback;
import android.util.Log;

import com.facebook.react.common.annotations.VisibleForTesting;

public class WebViewModule extends ReactContextBaseJavaModule implements ActivityEventListener {
    public ValueCallback<Uri[]> callback;

    @VisibleForTesting
    public static final String REACT_CLASS = "AndroidWebViewModule";

    public WebViewModule(ReactApplicationContext context){
        super(context);
        context.addActivityEventListener(this);
    }

    @Override
    public String getName(){
        return REACT_CLASS;
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