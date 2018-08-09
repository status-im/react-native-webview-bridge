package com.github.alinz.reactnativewebviewbridge;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewGroup.LayoutParams;
import android.webkit.*;
import com.facebook.common.logging.FLog;
import com.facebook.react.bridge.*;
import com.facebook.react.common.ReactConstants;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.UIManagerModule;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.events.Event;
import com.facebook.react.uimanager.events.EventDispatcher;
import com.facebook.react.views.webview.ReactWebViewManager;
import com.facebook.react.views.webview.WebViewConfig;
import com.facebook.react.views.webview.events.TopLoadingErrorEvent;
import com.facebook.react.views.webview.events.TopLoadingFinishEvent;
import com.facebook.react.views.webview.events.TopLoadingStartEvent;
import com.facebook.react.views.webview.events.TopMessageEvent;
import com.facebook.react.bridge.ActivityEventListener;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.OkHttpClient.Builder;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import im.status.ethereum.function.Function;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.ActivityCompat;


import static okhttp3.internal.Util.UTF_8;

public class WebViewBridgeManager extends ReactWebViewManager {
    private static final String REACT_CLASS = "RCTWebViewBridge";
    private static final String BRIDGE_NAME = "__REACT_WEB_VIEW_BRIDGE";

    public final static String HEADER_CONTENT_TYPE = "content-type";

    private static final String MIME_TEXT_HTML = "text/html";
    private static final String MIME_UNKNOWN = "application/octet-stream";

    private static final int COMMAND_SEND_TO_BRIDGE = 101;
    public static final int GEO_PERMISSIONS_GRANTED = 103;
    private static Map<Integer, PermissionRequest> permissionRequest;

    private static final int CAMERA_PERMISSIONS_REQUEST = 0;
    private static final int MIDI_SYSEX_PERMISSIONS_REQUEST = 1;
    private static final int MEDIA_ID_PERMISSIONS_REQUEST = 2;
    private static final int AUDIO_PERMISSIONS_REQUEST = 3;

    private static final String TAG = "WebViewBridgeManager";

    private WebViewConfig mWebViewConfig;
    private static ReactApplicationContext reactNativeContext;
    private OkHttpClient httpClient;
    private static boolean debug;
    Function<String, String> callRPC;
    private WebViewBridgePackage pkg;

    public WebViewBridgeManager(ReactApplicationContext context, boolean debug, Function<String, String> callRPC, WebViewBridgePackage pkg) {
        this.reactNativeContext = context;
        this.debug = debug;
        this.callRPC = callRPC;
        this.pkg = pkg;
        Builder b = new Builder();
        httpClient = b
                .followRedirects(false)
                .followSslRedirects(false)
                .build();

        permissionRequest = new HashMap<>();
        mWebViewConfig = new WebViewConfig() {
            public void configWebView(WebView webView) {
            }
        };
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    public @Nullable
    Map<String, Integer> getCommandsMap() {
        Map<String, Integer> commandsMap = super.getCommandsMap();

        commandsMap.put("sendToBridge", COMMAND_SEND_TO_BRIDGE);
        commandsMap.put("geoPermissionsGranted", GEO_PERMISSIONS_GRANTED);

        return commandsMap;
    }

    protected static class ReactWebChromeClient extends WebChromeClient {

        String origin;
        GeolocationPermissions.Callback callback;
        WebViewBridgePackage pkg;

        public ReactWebChromeClient(WebViewBridgePackage pkg) {
            this.pkg = pkg;
        }

        public boolean onShowFileChooser (WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
            pkg.getModule().callback = filePathCallback;
            openFileChooserView();
            return true;
        }

        private void openFileChooserView(){
            try {
                final Intent galleryIntent = new Intent(Intent.ACTION_PICK);
                galleryIntent.setType("image/*");
                final Intent chooserIntent = Intent.createChooser(galleryIntent, "choose file");
                this.pkg.getModule().getActivity().startActivityForResult(chooserIntent, 1);
            } catch (Exception e) {
                Log.d("customwebview", e.toString());
            }
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            try {
                WritableMap params = Arguments.createMap();
                JSONObject event = new JSONObject();
                event.put("type", "request_geo_permissions");
                params.putString("jsonEvent", event.toString());
                reactNativeContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit("gethEvent", params);
                this.callback = callback;
                this.origin = origin;
            } catch (JSONException e) {

            }
        }

        public void geoCallback() {
            if (callback != null) {
                callback.invoke(origin, true, false);
            }
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            for (String permission : request.getResources()) {
                if (permission.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                    int permissionCheck = ContextCompat.checkSelfPermission(
                            reactNativeContext.getCurrentActivity(),
                            Manifest.permission.CAMERA
                    );
                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        permissionRequest.put(CAMERA_PERMISSIONS_REQUEST, request);
                        ActivityCompat.requestPermissions(
                                reactNativeContext.getCurrentActivity(),
                                new String[]{Manifest.permission.CAMERA},
                                CAMERA_PERMISSIONS_REQUEST);
                    }
                } else if (permission.equals(PermissionRequest.RESOURCE_MIDI_SYSEX)) {
                    int permissionCheck = ContextCompat.checkSelfPermission(
                            reactNativeContext.getCurrentActivity(),
                            Manifest.permission.BIND_MIDI_DEVICE_SERVICE
                    );
                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        permissionRequest.put(MIDI_SYSEX_PERMISSIONS_REQUEST, request);
                        ActivityCompat.requestPermissions(
                                reactNativeContext.getCurrentActivity(),
                                new String[]{Manifest.permission.BIND_MIDI_DEVICE_SERVICE},
                                MIDI_SYSEX_PERMISSIONS_REQUEST);
                    }
                } else if (permission.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                    int permissionCheck = ContextCompat.checkSelfPermission(
                            reactNativeContext.getCurrentActivity(),
                            Manifest.permission.RECORD_AUDIO
                    );
                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        permissionRequest.put(AUDIO_PERMISSIONS_REQUEST, request);
                        ActivityCompat.requestPermissions(
                                reactNativeContext.getCurrentActivity(),
                                new String[]{Manifest.permission.RECORD_AUDIO},
                                AUDIO_PERMISSIONS_REQUEST);
                    }
                } else if (permission.equals(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) {
                    int permissionCheck = ContextCompat.checkSelfPermission(
                            reactNativeContext.getCurrentActivity(),
                            Manifest.permission.MEDIA_CONTENT_CONTROL
                    );
                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        permissionRequest.put(MEDIA_ID_PERMISSIONS_REQUEST, request);
                        ActivityCompat.requestPermissions(
                                reactNativeContext.getCurrentActivity(),
                                new String[]{Manifest.permission.MEDIA_CONTENT_CONTROL},
                                MEDIA_ID_PERMISSIONS_REQUEST);
                    }
                }
            }
        }
    }

    public static void grantAccess(int requestCode) {
        PermissionRequest request = permissionRequest.get(requestCode);
        if (request != null) {
            request.grant(request.getResources());
            permissionRequest.put(requestCode, null);
        }
    }

    public static Boolean urlStringLooksInvalid(String urlString) {
        return urlString == null || 
               urlString.trim().equals("") || 
               !(urlString.startsWith("http") && !urlString.startsWith("www")) || 
               urlString.contains("|"); 
    }

    public static Boolean responseRequiresJSInjection(Response response) {
        // we don't want to inject JS into redirects
        if (response.isRedirect()) {
            return false;
        }

        // ...okhttp appends charset to content type sometimes, like "text/html; charset=UTF8"
        final String contentTypeAndCharset = response.header(HEADER_CONTENT_TYPE, MIME_UNKNOWN);
        // ...and we only want to inject it in to HTML, really
        return contentTypeAndCharset.startsWith(MIME_TEXT_HTML);
    }

    public WebResourceResponse shouldInterceptRequest(WebResourceRequest request, Boolean onlyMainFrame, ReactWebView webView) {
        Uri url = request.getUrl();
        String urlStr = url.toString();

        Log.d(TAG, "new request ");
        Log.d(TAG, "url " + urlStr);
        Log.d(TAG, "host " + request.getUrl().getHost());
        Log.d(TAG, "path " + request.getUrl().getPath());
        Log.d(TAG, "main " + request.isForMainFrame());
        Log.d(TAG, "headers " + request.getRequestHeaders().toString());
        Log.d(TAG, "method " + request.getMethod());

        if (onlyMainFrame && !request.isForMainFrame()) {
            return null;
        }

        if (WebViewBridgeManager.urlStringLooksInvalid(urlStr)) {
            return null;
        }

        try {
            Request req = new Request.Builder()
                    .url(urlStr)
                    .header("User-Agent", userAgent)
                    .build();

            Response response = httpClient.newCall(req).execute();

            Log.d(TAG, "response headers " + response.headers().toString());
            Log.d(TAG, "response code " + response.code());
            Log.d(TAG, "response suc " + response.isSuccessful());

            if (!WebViewBridgeManager.responseRequiresJSInjection(response)) {
                return null;
            }

            InputStream is = response.body().byteStream();
            MediaType contentType = response.body().contentType();
            Charset charset = contentType != null ? contentType.charset(UTF_8) : UTF_8;

            if (response.code() == HttpURLConnection.HTTP_OK) {
                is = new InputStreamWithInjectedJS(is, webView.injectedOnStartLoadingJS, charset);
            }

            Log.d(TAG, "inject our custom JS to this request");
            return new WebResourceResponse("text/html", charset.name(), is);
        } catch (IOException e) {
            return null;
        }
    }

    static String userAgent = "";

    @Override
    protected WebView createViewInstance(ThemedReactContext reactContext) {
        final ReactWebView webView = new ReactWebView(reactContext);
        userAgent = webView.getSettings().getUserAgentString();
        reactContext.addLifecycleEventListener(webView);

        mWebViewConfig.configWebView(webView);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setGeolocationEnabled(true);
        webView.setInitialScale(1);

        // Fixes broken full-screen modals/galleries due to body height being 0.
        webView.setLayoutParams(
                new LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT));

        if (debug && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        ReactWebChromeClient client = new ReactWebChromeClient(pkg);
        webView.setWebChromeClient(client);
        webView.setWebViewClient(new ReactWebViewClient());
        webView.addJavascriptInterface(new JavascriptBridge(webView), "WebViewBridge");
        StatusBridge bridge = new StatusBridge(reactContext, webView, this.callRPC);
        webView.addJavascriptInterface(bridge, "StatusBridge");
        webView.setStatusBridge(bridge);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ServiceWorkerController swController = ServiceWorkerController.getInstance();
            swController.setServiceWorkerClient(new ServiceWorkerClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    Log.d(TAG, "shouldInterceptRequest / ServiceWorkerClient");
                    WebResourceResponse response = WebViewBridgeManager.this.shouldInterceptRequest(request, false, webView);
                    if (response != null) {
                        Log.d(TAG, "shouldInterceptRequest / ServiceWorkerClient -> return intersept response");
                        return response;
                    }

                    Log.d(TAG, "shouldInterceptRequest / ServiceWorkerClient -> intercept response is nil, delegating up");
                    return super.shouldInterceptRequest(request);
                }
            });
        }

        return webView;
    }

    @Override
    public void receiveCommand(WebView root, int commandId, @Nullable ReadableArray args) {
        super.receiveCommand(root, commandId, args);

        switch (commandId) {
            case COMMAND_SEND_TO_BRIDGE:
                sendToBridge(root, args.getString(0));
                break;
            case GEO_PERMISSIONS_GRANTED:
                ((ReactWebChromeClient) ((ReactWebView) root).getWebChromeClient()).geoCallback();
                break;
            default:
                //do nothing!!!!
        }
    }

    private void sendToBridge(WebView root, String message) {
        String script = "WebViewBridge.onMessage('" + message + "');";
        WebViewBridgeManager.evaluateJavascript(root, script, null);
    }

    static private void evaluateJavascript(WebView root, String javascript, ValueCallback<String> callback) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            root.evaluateJavascript(javascript, callback);
        } else {
            root.loadUrl("javascript:" + javascript);
        }
    }

    @ReactProp(name = "allowFileAccessFromFileURLs")
    public void setAllowFileAccessFromFileURLs(WebView root, boolean allows) {
        root.getSettings().setAllowFileAccessFromFileURLs(allows);
    }

    @ReactProp(name = "allowUniversalAccessFromFileURLs")
    public void setAllowUniversalAccessFromFileURLs(WebView root, boolean allows) {
        root.getSettings().setAllowUniversalAccessFromFileURLs(allows);
    }

    @Override
    @ReactProp(name = "injectedJavaScript")
    public void setInjectedJavaScript(WebView view, @Nullable String injectedJavaScript) {
        ((ReactWebView) view).setInjectedJavaScript(injectedJavaScript);
    }

    @Override
    @ReactProp(name = "messagingEnabled")
    public void setMessagingEnabled(WebView view, boolean enabled) {
        ((ReactWebView) view).setMessagingEnabled(enabled);
    }

    @ReactProp(name = "injectedOnStartLoadingJavaScript")
    public void setInjectedOnStartLoadingJavaScript(WebView view, @Nullable String injectedJavaScript) {
        ((ReactWebView) view).setInjectedOnStartLoadingJavaScript(injectedJavaScript);
    }

    @ReactProp(name = "localStorageEnabled")
    public void setLocalStorageEnabled(WebView view, boolean enabled) {
        if (enabled) {
            view.getSettings().setDomStorageEnabled(true);
            view.getSettings().setDatabaseEnabled(true);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                view.getSettings().setDatabasePath("/data/data/" + view.getContext().getPackageName() + "/databases/");
            }
        }
    }

    @Override
    protected void addEventEmitters(ThemedReactContext reactContext, WebView view) {

    }

    private static class ReactWebView extends WebView implements LifecycleEventListener {
        private @Nullable
        String injectedJS;
        private @Nullable
        String injectedOnStartLoadingJS;
        private StatusBridge bridge;
        private boolean messagingEnabled = false;

        private class ReactWebViewBridge {
            ReactWebView mContext;

            ReactWebViewBridge(ReactWebView c) {
                mContext = c;
            }

            @JavascriptInterface
            public void postMessage(String message) {
                mContext.onMessage(message);
            }
        }

        /**
         * WebView must be created with an context of the current activity
         * <p>
         * Activity Context is required for creation of dialogs internally by WebView
         * Reactive Native needed for access to ReactNative internal system functionality
         */
        public ReactWebView(ThemedReactContext reactContext) {
            super(reactContext);
        }

        @Override
        public void onHostResume() {
            // do nothing
        }

        @Override
        public void onHostPause() {
            // do nothing
        }

        @Override
        public void onHostDestroy() {
            cleanupCallbacksAndDestroy();
        }

        public void setInjectedJavaScript(@Nullable String js) {
            injectedJS = js;
        }

        public void setInjectedOnStartLoadingJavaScript(@Nullable String js) {
            injectedOnStartLoadingJS = js;
        }

        public void setMessagingEnabled(boolean enabled) {
            if (messagingEnabled == enabled) {
                return;
            }

            messagingEnabled = enabled;
            if (enabled) {
                addJavascriptInterface(new ReactWebViewBridge(this), BRIDGE_NAME);
                linkBridge();
            } else {
                removeJavascriptInterface(BRIDGE_NAME);
            }
        }

        public void callInjectedJavaScript() {
            if (getSettings().getJavaScriptEnabled() &&
                    injectedJS != null &&
                    !TextUtils.isEmpty(injectedJS)) {
                loadUrl("javascript:(function() {\n" + injectedJS + ";\n})();");
            }
        }

        public void linkBridge() {
            if (messagingEnabled) {
                if (debug && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    // See isNative in lodash
                    String testPostMessageNative = "String(window.postMessage) === String(Object.hasOwnProperty).replace('hasOwnProperty', 'postMessage')";
                    WebViewBridgeManager.evaluateJavascript(this, testPostMessageNative, new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {
                            if (value.equals("true")) {
                                FLog.w(ReactConstants.TAG, "Setting onMessage on a WebView overrides existing values of window.postMessage, but a previous value was defined");
                            }
                        }
                    });
                }

                loadUrl("javascript:(" +
                        "window.originalPostMessage = window.postMessage," +
                        "window.postMessage = function(data) {" +
                        BRIDGE_NAME + ".postMessage(String(data));" +
                        "}" +
                        ")");
            }
        }

        public void onMessage(String message) {
            dispatchEvent(this, new TopMessageEvent(this.getId(), message));
        }

        private void cleanupCallbacksAndDestroy() {
            setWebViewClient(null);
            destroy();
        }

        public void setStatusBridge(StatusBridge bridge) {
            this.bridge = bridge;
        }

        private ReactWebChromeClient chromeClient;

        @Override
        public void setWebChromeClient(WebChromeClient client) {
            super.setWebChromeClient(client);
            chromeClient = (ReactWebChromeClient) client;
        }

        public ReactWebChromeClient getWebChromeClient() {
            return this.chromeClient;
        }

        public StatusBridge getStatusBridge() {
            return this.bridge;
        }
    }

    private class ReactWebViewClient extends WebViewClient {

        private boolean mLastLoadFailed = false;

        @Override
        public void onPageFinished(WebView webView, String url) {
            super.onPageFinished(webView, url);

            if (!mLastLoadFailed) {
                ReactWebView reactWebView = (ReactWebView) webView;
                reactWebView.callInjectedJavaScript();
                reactWebView.linkBridge();
                emitFinishEvent(webView, url);
            }
        }

        @Override
        public void onPageStarted(WebView webView, String url, Bitmap favicon) {
            super.onPageStarted(webView, url, favicon);
            mLastLoadFailed = false;

            dispatchEvent(
                    webView,
                    new TopLoadingStartEvent(
                            webView.getId(),
                            createWebViewEvent(webView, url)));
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (request == null || view == null) {
                return false;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

                /*
                 * In order to follow redirects properly, we return null in interceptRequest().
                 * Doing this breaks the web3 injection on the resulting page, so we have to reload to
                 * make sure web3 is available.
                 * */

                if (request.isForMainFrame() && request.isRedirect()) {
                    view.loadUrl(request.getUrl().toString());
                    return true;
                }
            }

            /*
             * API < 24: TODO: implement based on https://github.com/toshiapp/toshi-android-client/blob/f4840d3d24ff60223662eddddceca8586a1be8bb/app/src/main/java/com/toshi/view/activity/webView/ToshiWebClient.kt#L99
             * */
            return super.shouldOverrideUrlLoading(view, request);
        }

        @Override
        public void onReceivedError(
                WebView webView,
                int errorCode,
                String description,
                String failingUrl) {
            super.onReceivedError(webView, errorCode, description, failingUrl);
            mLastLoadFailed = true;

            // In case of an error JS side expect to get a finish event first, and then get an error event
            // Android WebView does it in the opposite way, so we need to simulate that behavior
            emitFinishEvent(webView, failingUrl);

            WritableMap eventData = createWebViewEvent(webView, failingUrl);
            eventData.putDouble("code", errorCode);
            eventData.putString("description", description);

            dispatchEvent(
                    webView,
                    new TopLoadingErrorEvent(webView.getId(), eventData));
        }

        @Override
        public void doUpdateVisitedHistory(WebView webView, String url, boolean isReload) {
            super.doUpdateVisitedHistory(webView, url, isReload);

            dispatchEvent(
                    webView,
                    new TopLoadingStartEvent(
                            webView.getId(),
                            createWebViewEvent(webView, url)));
        }

        private void emitFinishEvent(WebView webView, String url) {
            dispatchEvent(
                    webView,
                    new TopLoadingFinishEvent(
                            webView.getId(),
                            createWebViewEvent(webView, url)));
        }

        private WritableMap createWebViewEvent(WebView webView, String url) {
            WritableMap event = Arguments.createMap();
            event.putDouble("target", webView.getId());
            // Don't use webView.getUrl() here, the URL isn't updated to the new value yet in callbacks
            // like onPageFinished
            event.putString("url", url);
            event.putBoolean("loading", !mLastLoadFailed && webView.getProgress() != 100);
            event.putString("title", webView.getTitle());
            event.putBoolean("canGoBack", webView.canGoBack());
            event.putBoolean("canGoForward", webView.canGoForward());
            return event;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            return null;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Log.d(TAG, "shouldInterceptRequest / WebViewClient");
            WebResourceResponse response = WebViewBridgeManager.this.shouldInterceptRequest(request, true, (ReactWebView)view);
            if (response != null) {
                Log.d(TAG, "shouldInterceptRequest / WebViewClient -> return intercept response");
                return response;
            }

            Log.d(TAG, "shouldInterceptRequest / WebViewClient -> intercept response is nil, delegating up");
            return super.shouldInterceptRequest(view, request);
        }
    }

    @Override
    public void onDropViewInstance(WebView webView) {
        //super.onDropViewInstance(webView);
        ((ThemedReactContext) webView.getContext()).removeLifecycleEventListener((ReactWebView) webView);
        ((ReactWebView) webView).cleanupCallbacksAndDestroy();
    }

    protected static void dispatchEvent(WebView webView, Event event) {
        ReactContext reactContext = (ReactContext) webView.getContext();
        EventDispatcher eventDispatcher =
                reactContext.getNativeModule(UIManagerModule.class).getEventDispatcher();
        eventDispatcher.dispatchEvent(event);
    }
}
