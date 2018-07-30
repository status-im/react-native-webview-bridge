package com.github.alinz.reactnativewebviewbridge;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.JavaScriptModule;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import im.status.ethereum.function.Function;

public class WebViewBridgePackage implements ReactPackage {

    private boolean debug;
    Function<String, String> callRPC;
    private WebViewBridgeModule module;

    public WebViewBridgePackage(boolean debug, Function<String, String> callRPC) {
        this.debug = debug;
        this.callRPC = callRPC;
    }

    @Override
    public List<NativeModule> createNativeModules(ReactApplicationContext reactApplicationContext) {
        List<NativeModule> modules = new ArrayList<>();
        module = new WebViewBridgeModule(reactApplicationContext);
        modules.add(module);
        return modules;
    }

    @Override
    public List<ViewManager> createViewManagers(ReactApplicationContext reactApplicationContext) {
        WebViewBridgeManager manager = new WebViewBridgeManager(reactApplicationContext, this.debug, this.callRPC, this);
        return Arrays.<ViewManager>asList(manager);
    }

    public List<Class<? extends JavaScriptModule>> createJSModules() {
        return Arrays.asList();
    }


    public WebViewBridgeModule getModule(){
        return module;
    }

}
