#import <Foundation/Foundation.h>
#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RCT_EXTERN_MODULE(AdyenDropInPayment, NSObject)

+(BOOL)requiresMainQueueSetup
{
  return YES;
}

RCT_EXTERN_METHOD(startPayment:(NSDictionary *)paymentMethodsJson config:(NSDictionary *)config)
RCT_EXTERN_METHOD(handleAction:(NSString)actionJson)
RCT_EXTERN_METHOD(handlePaymentResult:(NSString)paymentResultJson)
RCT_EXTERN_METHOD(handleRedirectURL:(NSString)url)

@end
