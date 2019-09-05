#import "React/RCTView.h"

@class StatusWKWebView;

@protocol RCTWKWebViewDelegate <NSObject>

- (BOOL)webView:(StatusWKWebView *)webView
shouldStartLoadForRequest:(NSMutableDictionary<NSString *, id> *)request
   withCallback:(RCTDirectEventBlock)callback;

@end

@interface StatusWKWebView : RCTView

@property (nonatomic, weak) id<RCTWKWebViewDelegate> delegate;

@property (nonatomic, copy) NSDictionary *source;
@property (nonatomic, assign) UIEdgeInsets contentInset;
@property (nonatomic, assign) BOOL automaticallyAdjustContentInsets;
@property (nonatomic, assign) BOOL sendCookies;
@property (nonatomic, copy) NSString *injectedJavaScript;
@property (nonatomic, copy) NSString *injectedOnStartLoadingJavaScript;

- (void)goForward;
- (void)goBack;
- (void)reload;
- (void)evaluateJavaScript:(NSString *)javaScriptString completionHandler:(void (^)(id, NSError *error))completionHandler;
- (void)sendToBridge:(NSString *)message;

@end
