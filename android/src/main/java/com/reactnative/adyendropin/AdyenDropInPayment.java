package com.reactnative.adyendropin;

import java.util.Iterator;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.ComponentName;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.wallet.WalletConstants;
import com.adyen.checkout.base.ActionComponentData;
import com.adyen.checkout.base.ComponentError;
import com.adyen.checkout.base.model.PaymentMethodsApiResponse;
import com.adyen.checkout.base.model.payments.request.PaymentComponentData;
import com.adyen.checkout.base.model.payments.request.PaymentMethodDetails;
import com.adyen.checkout.base.model.payments.response.Action;
import com.adyen.checkout.base.model.payments.Amount;
import com.adyen.checkout.base.util.PaymentMethodTypes;
import com.adyen.checkout.card.CardConfiguration;
import com.adyen.checkout.googlepay.GooglePayConfiguration;
import com.adyen.checkout.core.api.Environment;
import com.adyen.checkout.dropin.DropIn;
import com.adyen.checkout.dropin.DropInConfiguration;
import com.adyen.checkout.dropin.service.CallResult;
import com.adyen.checkout.redirect.RedirectComponent;

public class AdyenDropInPayment extends ReactContextBaseJavaModule implements ActivityEventListener {
    DropInConfiguration dropInConfiguration;
    long lastSubmitAt = 0;
    static RedirectComponent redirectComponent;
    static String redirectPaymentData;
    public static AdyenDropInPaymentService dropInService;
    public static AdyenDropInPayment INSTANCE = null;

    public AdyenDropInPayment(@NonNull ReactApplicationContext reactContext) {
        super(reactContext);
        AdyenDropInPayment.INSTANCE = this;
        reactContext.addActivityEventListener(this);
    }

    void buildConfig(ReadableMap configuration) throws JSONException {
        JSONObject config = convertMapToJson(configuration);
        Intent resultIntent = new Intent(this.getCurrentActivity(), this.getCurrentActivity().getClass());
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        DropInConfiguration.Builder builder = new DropInConfiguration.Builder(this.getCurrentActivity(), resultIntent, AdyenDropInPaymentService.class);

        Environment env = Environment.TEST;
        try {
            String environment = config.getString("environment");
            if (environment == "liveEurope") env = Environment.EUROPE;
            else if (environment == "liveAustralia") env = Environment.AUSTRALIA;
            else if (environment == "liveUnitedStates") env = Environment.UNITED_STATES;
        } catch (JSONException e) {}
        builder.setEnvironment(Environment.TEST);

        Locale locale = Locale.getDefault();
        try {
            String[] shopperLocale = config.getString("shopperLocale").split("_");
            locale = new Locale(shopperLocale[0], shopperLocale[1]);
        } catch (JSONException e) {}
        builder.setShopperLocale(locale);

        try {
            JSONObject amountJson = config.getJSONObject("amount");
            Amount amount = Amount.SERIALIZER.deserialize(amountJson);
            builder.setAmount(amount);
        } catch (JSONException e) {}

        String clientKey = null;
        try {
            clientKey = config.getString("clientKey");
            builder.setClientKey(clientKey);
        } catch (JSONException e) {}

        CardConfiguration.Builder cardConfigBuilder = new CardConfiguration.Builder(locale, env);
        if (clientKey != null) {
            cardConfigBuilder.setClientKey(clientKey);
        }
        try {
            String publicKey = config.getJSONObject(PaymentMethodTypes.SCHEME).getString("publicKey");
            cardConfigBuilder.setPublicKey(publicKey);
        } catch (JSONException e) {}
        try {
            Boolean showsHolderNameField = config.getJSONObject(PaymentMethodTypes.SCHEME).getBoolean("showsHolderNameField");
            cardConfigBuilder.setHolderNameRequire(showsHolderNameField);
        } catch (JSONException e) {}
        try {
            Boolean showsStorePaymentMethodField = config.getJSONObject(PaymentMethodTypes.SCHEME).getBoolean("showsStorePaymentMethodField");
            cardConfigBuilder.setShowStorePaymentField(showsStorePaymentMethodField);
        } catch (JSONException e) {}
        builder.addCardConfiguration(cardConfigBuilder.build());

        try {
            String merchantAccount = config.getString("merchantAccount");
            GooglePayConfiguration.Builder googlePayConfigBuilder = new GooglePayConfiguration.Builder(locale, env, merchantAccount);
            if (env == Environment.TEST) {
                googlePayConfigBuilder.setGooglePayEnvironment(WalletConstants.ENVIRONMENT_TEST);
            } else {
                googlePayConfigBuilder.setGooglePayEnvironment(WalletConstants.ENVIRONMENT_PRODUCTION);
            }
            builder.addGooglePayConfiguration(googlePayConfigBuilder.build());
        } catch (JSONException e) {}

        dropInConfiguration = builder.build();
    }

    @ReactMethod
    public void startPayment(ReadableMap paymentMethodsJson, ReadableMap config) throws JSONException {
        buildConfig(config);

        JSONObject jsonObject = convertMapToJson(paymentMethodsJson);
        PaymentMethodsApiResponse paymentMethodsApiResponse = PaymentMethodsApiResponse.SERIALIZER.deserialize(jsonObject);
        final AdyenDropInPayment adyenDropInPayment = this;
        this.getCurrentActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                DropIn.startPayment(adyenDropInPayment.getCurrentActivity(), paymentMethodsApiResponse, dropInConfiguration);
            }
        });
    }

    @ReactMethod
    public void handlePaymentResult(String paymentJson) {
        if (dropInService != null) {
            CallResult callResult = new CallResult(CallResult.ResultType.FINISHED, paymentJson);
            dropInService.handleCallResult(callResult);
        }
    }

    @ReactMethod
    public void handleAction(String actionJson) throws JSONException {
        if (dropInService != null) {
            Action action = Action.SERIALIZER.deserialize(new JSONObject(actionJson));
            withRedirectComponent(new Runnable() {
                @Override
                public void run() {
                    if (AdyenDropInPayment.redirectComponent.canHandleAction(action)) {
                        AdyenDropInPayment.redirectPaymentData = action.getPaymentData();
                    }
                }
            });
            CallResult callResult = new CallResult(CallResult.ResultType.ACTION, actionJson);
            dropInService.handleCallResult(callResult);
        }
    }

    @ReactMethod
    public void handleRedirectURL(String url) {
        withRedirectComponent(new Runnable() {
            @Override
            public void run() {
                AdyenDropInPayment.redirectComponent.handleRedirectResponse(Uri.parse(url));
            }
        });
    }

    void withRedirectComponent(Runnable callback) {
        final AdyenDropInPayment adyenDropInPayment = this;
        FragmentActivity currentActivity = (FragmentActivity) this.getCurrentActivity();
        if (AdyenDropInPayment.redirectComponent != null) {
            if (callback != null) {
                currentActivity.runOnUiThread(callback);
            }
        } else {
            AdyenDropInPayment.redirectComponent = RedirectComponent.PROVIDER.get(currentActivity);
            currentActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    LifecycleOwner lifecycleOwner = (LifecycleOwner) adyenDropInPayment.getCurrentActivity();
                    AdyenDropInPayment.redirectComponent.observe(lifecycleOwner, new Observer<ActionComponentData>() {
                        @Override
                        public void onChanged(ActionComponentData actionComponentData) {
                            Context context = adyenDropInPayment.getCurrentActivity();
                            JSONObject details = ActionComponentData.SERIALIZER.serialize(actionComponentData);
                            // ComponentName componentName = adyenDropInPayment.dropInConfiguration.serviceComponentName;
                            ComponentName componentName = new ComponentName(
                                context.getPackageName(),
                                AdyenDropInPaymentService.class.getName()
                            );

                            // workaround to pass paymentData correctly
                            if (actionComponentData.getPaymentData() == null) {
                                ActionComponentData copy = ActionComponentData.SERIALIZER.deserialize(details);
                                copy.setPaymentData(AdyenDropInPayment.redirectPaymentData);
                                details = ActionComponentData.SERIALIZER.serialize(copy);
                            }

                            AdyenDropInPaymentService.Companion.requestDetailsCall(
                                context,
                                details,
                                componentName
                            );
                        }
                    });
                    AdyenDropInPayment.redirectComponent.observeErrors(lifecycleOwner, new Observer<ComponentError>() {
                        @Override
                        public void onChanged(ComponentError componentError) {
                            adyenDropInPayment.handlePaymentError(componentError);
                        }
                    });
                    if (callback != null) {
                        callback.run();
                    }
                }
            });
        }
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode == DropIn.DROP_IN_REQUEST_CODE) {
            WritableMap resultData = new WritableNativeMap();
            if (resultCode == Activity.RESULT_CANCELED) {
                resultData.putString("result", "canceled");
                // if (data != null && data.hasExtra(DropIn.ERROR_REASON_KEY)) {
                //     resultData.putString("error", data.getStringExtra(DropIn.ERROR_REASON_KEY));
                // }
            } else if (resultCode == Activity.RESULT_OK) {
                resultData.putString("result", "ok");
            } else {
                resultData.putString("result", "unknown");
            }
            this.sendEvent("android-activity-result", resultData);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {}

    @NonNull
    @Override
    public String getName() {
        return AdyenDropInPayment.class.getSimpleName();
    }

    public void triggerPaymentsCall(JSONObject paymentComponentData) {
        // workaround to prevent multi-submit
        long now = System.nanoTime();
        if (now - lastSubmitAt < 3000000000L) return;
        lastSubmitAt = now;

        try {
            WritableMap eventData = new WritableNativeMap();
            eventData.putMap("data", convertJsonToMap(paymentComponentData));
            this.sendEvent("payment", eventData);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void triggerDetailsCall(JSONObject actionComponentData) {
        try {
            WritableMap eventData = new WritableNativeMap();
            eventData.putMap("data", convertJsonToMap(actionComponentData));
            this.sendEvent("payment-details", eventData);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    void handlePaymentError(ComponentError componentError) {
        WritableMap resultData = new WritableNativeMap();
        resultData.putString("message", componentError.getErrorMessage());
        resultData.putString("error", componentError.getException().getMessage());
        this.sendEvent("error", resultData);
    }

    private void sendEvent(String eventName, @Nullable WritableMap params) {
        this.getReactApplicationContext()
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
    }

    public static WritableMap convertJsonToMap(JSONObject jsonObject) throws JSONException {
        WritableMap map = new WritableNativeMap();

        Iterator<String> iterator = jsonObject.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            Object value = jsonObject.get(key);
            if (value instanceof JSONObject) {
                map.putMap(key, convertJsonToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                map.putArray(key, convertJsonToArray((JSONArray) value));
            } else if (value instanceof Boolean) {
                map.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                map.putInt(key, (Integer) value);
            } else if (value instanceof Double) {
                map.putDouble(key, (Double) value);
            } else if (value instanceof String) {
                map.putString(key, (String) value);
            } else {
                map.putString(key, value.toString());
            }
        }
        return map;
    }

    public static WritableArray convertJsonToArray(JSONArray array) throws JSONException {
        WritableNativeArray result = new WritableNativeArray();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONObject) {
                result.pushMap(convertJsonToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                result.pushArray(convertJsonToArray((JSONArray) value));
            } else if (value instanceof Boolean) {
                result.pushBoolean((Boolean) value);
            } else if (value instanceof Integer) {
                result.pushInt((Integer) value);
            } else if (value instanceof Double) {
                result.pushDouble((Double) value);
            } else if (value instanceof String) {
                result.pushString((String) value);
            } else {
                result.pushString(value.toString());
            }
        }

        return result;
    }

    public static JSONObject convertMapToJson(ReadableMap readableMap) throws JSONException {
        JSONObject object = new JSONObject();
        ReadableMapKeySetIterator iterator = readableMap.keySetIterator();
        while (iterator.hasNextKey()) {
            String key = iterator.nextKey();
            switch (readableMap.getType(key)) {
                case Null:
                    object.put(key, JSONObject.NULL);
                    break;
                case Boolean:
                    object.put(key, readableMap.getBoolean(key));
                    break;
                case Number:
                    object.put(key, readableMap.getDouble(key));
                    break;
                case String:
                    object.put(key, readableMap.getString(key));
                    break;
                case Map:
                    object.put(key, convertMapToJson(readableMap.getMap(key)));
                    break;
                case Array:
                    object.put(key, convertArrayToJson(readableMap.getArray(key)));
                    break;
            }
        }
        return object;
    }

    public static JSONArray convertArrayToJson(ReadableArray readableArray) throws JSONException {
        JSONArray array = new JSONArray();
        for (int i = 0; i < readableArray.size(); i++) {
            switch (readableArray.getType(i)) {
                case Null:
                    break;
                case Boolean:
                    array.put(readableArray.getBoolean(i));
                    break;
                case Number:
                    array.put(readableArray.getDouble(i));
                    break;
                case String:
                    array.put(readableArray.getString(i));
                    break;
                case Map:
                    array.put(convertMapToJson(readableArray.getMap(i)));
                    break;
                case Array:
                    array.put(convertArrayToJson(readableArray.getArray(i)));
                    break;
            }
        }
        return array;
    }
}
