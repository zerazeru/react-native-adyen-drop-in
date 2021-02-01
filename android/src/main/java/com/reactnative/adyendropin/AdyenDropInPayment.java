package com.reactnative.adyendropin;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

import com.adyen.checkout.adyen3ds2.Adyen3DS2Component;
import com.adyen.checkout.base.ActionComponentData;
import com.adyen.checkout.base.ComponentError;
import com.adyen.checkout.base.PaymentComponentState;
import com.adyen.checkout.base.component.BaseActionComponent;
import com.adyen.checkout.base.model.PaymentMethodsApiResponse;
import com.adyen.checkout.base.model.paymentmethods.PaymentMethod;
import com.adyen.checkout.base.model.payments.request.PaymentMethodDetails;
import com.adyen.checkout.base.model.payments.response.Action;
import com.adyen.checkout.base.model.payments.response.QrCodeAction;
import com.adyen.checkout.base.model.payments.response.RedirectAction;
import com.adyen.checkout.base.model.payments.response.Threeds2ChallengeAction;
import com.adyen.checkout.base.model.payments.response.Threeds2FingerprintAction;
import com.adyen.checkout.base.model.payments.response.VoucherAction;
import com.adyen.checkout.base.model.payments.Amount;
import com.adyen.checkout.base.util.PaymentMethodTypes;
import com.adyen.checkout.card.CardComponent;
import com.adyen.checkout.card.CardConfiguration;
import com.adyen.checkout.googlepay.GooglePayConfiguration;
import com.adyen.checkout.core.api.Environment;
import com.adyen.checkout.cse.Card;
import com.adyen.checkout.cse.EncryptedCard;
import com.adyen.checkout.cse.Encryptor;
import com.adyen.checkout.dropin.DropIn;
import com.adyen.checkout.dropin.DropInConfiguration;
import com.adyen.checkout.dropin.service.CallResult;
import com.adyen.checkout.redirect.RedirectComponent;
import com.adyen.checkout.redirect.RedirectUtil;
import com.google.android.gms.wallet.WalletConstants;
import com.facebook.react.bridge.*;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


import android.net.Uri;
import android.util.Log;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AdyenDropInPayment extends ReactContextBaseJavaModule {
    DropInConfiguration dropInConfiguration;
    RedirectComponent redirectComponent;
    Adyen3DS2Component adyen3DS2Component;
    Environment environment;
    boolean isDropIn;
    static Map<String, BaseActionComponent> ACTION_COMPONENT_MAP = new ConcurrentHashMap<>();
    public static AdyenDropInPaymentService dropInService;
    public static AdyenDropInPayment INSTANCE = null;


    public AdyenDropInPayment(@NonNull ReactApplicationContext reactContext) {
        super(reactContext);
        AdyenDropInPayment.INSTANCE = this;
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
    public void paymentMethods(ReadableMap paymentMethodsJson, ReadableMap config) throws JSONException {
        isDropIn = true;
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
        if (isDropIn) {
            CallResult callResult = new CallResult(CallResult.ResultType.FINISHED, paymentJson);
            dropInService.handleAsyncCallback(callResult);
            return;
        }
    }

    @ReactMethod
    public void handleRedirectURL(String url) {
        final AdyenDropInPayment adyenDropInPayment = this;
        this.getCurrentActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                initActionComponents(adyenDropInPayment.getReactApplicationContext());
                redirectComponent.handleRedirectResponse(Uri.parse(url));
            }
        });
    }

    public void onNewIntent(Activity activity, Intent intent) {
        initActionComponents(this.getReactApplicationContext());
        Uri data = intent.getData();
        if (data != null && data.toString().startsWith(RedirectUtil.REDIRECT_RESULT_SCHEME)) {
            redirectComponent.handleRedirectResponse(data);
        }
    }

    public RedirectComponent getRedirectComponent() {
        return redirectComponent;
    }

    public void setRedirectComponent(RedirectComponent redirectComponent) {
        this.redirectComponent = redirectComponent;
    }

    BaseActionComponent getActionComponent(Action action) {
        if (ACTION_COMPONENT_MAP.containsKey(action.getType())) {
            return ACTION_COMPONENT_MAP.get(action.getType());
        }
        BaseActionComponent actionComponent = null;
        switch (action.getType()) {
            case RedirectAction.ACTION_TYPE:
                actionComponent = RedirectComponent.PROVIDER.get((FragmentActivity) this.getCurrentActivity());
                break;
            case Threeds2FingerprintAction.ACTION_TYPE:
                actionComponent = Adyen3DS2Component.PROVIDER.get((FragmentActivity) this.getCurrentActivity());
                break;
            case Threeds2ChallengeAction.ACTION_TYPE:
                actionComponent = Adyen3DS2Component.PROVIDER.get((FragmentActivity) this.getCurrentActivity());
                break;
            case QrCodeAction.ACTION_TYPE:
                actionComponent = Adyen3DS2Component.PROVIDER.get((FragmentActivity) this.getCurrentActivity());
                break;
            case VoucherAction.ACTION_TYPE:
                actionComponent = Adyen3DS2Component.PROVIDER.get((FragmentActivity) this.getCurrentActivity());
                break;
            default:
                break;
        }
        if (actionComponent != null) {

            ACTION_COMPONENT_MAP.put(action.getType(), actionComponent);


        }
        return actionComponent;

    }

    void initActionComponents(ReactApplicationContext reactContext) {
        final AdyenDropInPayment adyenDropInPayment = this;
        FragmentActivity currentActivity = (FragmentActivity) reactContext.getCurrentActivity();
        if (redirectComponent == null) {
            redirectComponent = RedirectComponent.PROVIDER.get(currentActivity);
            redirectComponent.observe(currentActivity, new Observer<ActionComponentData>() {

                @Override
                public void onChanged(ActionComponentData actionComponentData) {
                    adyenDropInPayment.handlePaymentProvide(actionComponentData);
                }
            });
            redirectComponent.observeErrors(currentActivity, new Observer<ComponentError>() {
                @Override
                public void onChanged(ComponentError componentError) {
                    adyenDropInPayment.handlePaymentError(componentError);
                }
            });
        }
        if (adyen3DS2Component == null) {
            adyen3DS2Component = Adyen3DS2Component.PROVIDER.get(currentActivity);
            adyen3DS2Component.observe(currentActivity, new Observer<ActionComponentData>() {

                @Override
                public void onChanged(ActionComponentData actionComponentData) {
                    adyenDropInPayment.handlePaymentProvide(actionComponentData);
                }
            });
            adyen3DS2Component.observeErrors(currentActivity, new Observer<ComponentError>() {
                @Override
                public void onChanged(ComponentError componentError) {
                    adyenDropInPayment.handlePaymentError(componentError);
                }
            });
        }
    }

    @ReactMethod
    public void handleAction(String actionJson) {

        if (isDropIn) {
            CallResult callResult = new CallResult(CallResult.ResultType.ACTION, actionJson);
            dropInService.handleAsyncCallback(callResult);
            return;
        }
        if (actionJson == null || actionJson.length() <= 0) {
            return;
        }

        try {
            final AdyenDropInPayment adyenDropInPayment = this;
            Action action = Action.SERIALIZER.deserialize(new JSONObject(actionJson));


            this.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    initActionComponents(adyenDropInPayment.getReactApplicationContext());
                    BaseActionComponent actionComponent = adyenDropInPayment.getActionComponent(action);
                    if (actionComponent != null) {
                        actionComponent.handleAction(adyenDropInPayment.getCurrentActivity(), action);
                    }
                }
            });


        } catch (JSONException e) {
            e.printStackTrace();
        }


    }

    @NonNull
    @Override
    public String getName() {
        return AdyenDropInPayment.class.getSimpleName();
    }


    public void handlePaymentSubmit(PaymentComponentState paymentComponentState) {
        if (paymentComponentState.isValid()) {
            WritableMap eventData = new WritableNativeMap();
            WritableMap data = new WritableNativeMap();
            PaymentMethodDetails paymentMethodDetails = paymentComponentState.getData().getPaymentMethod();
            JSONObject jsonObject = PaymentMethodDetails.SERIALIZER.serialize(paymentMethodDetails);
            try {
                WritableMap paymentMethodMap = convertJsonToMap(jsonObject);
                data.putMap("paymentMethod", paymentMethodMap);
                data.putBoolean("storePaymentMethod", paymentComponentState.getData().isStorePaymentMethodEnable());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            eventData.putBoolean("isDropIn", this.isDropIn);
            eventData.putMap("data", data);
            this.sendEvent(this.getReactApplicationContext(), "onPaymentSubmit", eventData);
        }

    }

    public void handlePaymentProvide(ActionComponentData actionComponentData) {
        WritableMap data = null;
        try {
            data = convertJsonToMap(ActionComponentData.SERIALIZER.serialize(actionComponentData));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        WritableMap resultData = new WritableNativeMap();
        resultData.putBoolean("isDropIn", this.isDropIn);
        resultData.putString("msg", "");
        resultData.putMap("data", data);
        this.sendEvent(this.getReactApplicationContext(), "onPaymentProvide", resultData);
    }

    void handlePaymentError(ComponentError componentError) {
        WritableMap resultData = new WritableNativeMap();
        resultData.putBoolean("isDropIn", this.isDropIn);
        resultData.putString("msg", componentError.getErrorMessage());
        resultData.putString("error", componentError.getException().getMessage());
        this.sendEvent(this.getReactApplicationContext(), "onPaymentFail", resultData);
    }

    PaymentMethod getCardPaymentMethod(PaymentMethodsApiResponse
                                               paymentMethodsApiResponse, String name) {
        List<PaymentMethod> paymentMethodList = paymentMethodsApiResponse.getPaymentMethods();
        if (name == null || name.trim().length() <= 0) {
            name = "Credit Card";
        }
        for (PaymentMethod paymentMethod : paymentMethodList) {
            if (paymentMethod.getName().equalsIgnoreCase(name)) {
                return paymentMethod;
            }
        }
        return null;
    }


    private void sendEvent(ReactContext reactContext,
                           String eventName,
                           @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }
}
