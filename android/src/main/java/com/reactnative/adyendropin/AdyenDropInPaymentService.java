package com.reactnative.adyendropin;

import org.json.JSONObject;
import android.content.Intent;
import com.adyen.checkout.base.ActionComponentData;
import com.adyen.checkout.base.model.payments.request.PaymentComponentData;
import com.adyen.checkout.dropin.service.CallResult;
import com.adyen.checkout.dropin.service.DropInService;

public class AdyenDropInPaymentService extends DropInService {
    public AdyenDropInPaymentService() {
        AdyenDropInPayment.dropInService = this;
    }

    @Override
    public CallResult makePaymentsCall(JSONObject paymentComponentData) {
        if (paymentComponentData == null) {
            return new CallResult(CallResult.ResultType.FINISHED, "");
        }
        if (AdyenDropInPayment.INSTANCE != null) {
            AdyenDropInPayment.INSTANCE.triggerPaymentsCall(paymentComponentData);
        }
        return new CallResult(CallResult.ResultType.WAIT, "");
    }

    @Override
    public CallResult makeDetailsCall(JSONObject actionComponentData) {
        if (actionComponentData == null) {
            return new CallResult(CallResult.ResultType.FINISHED, "");
        }
        if (AdyenDropInPayment.INSTANCE != null) {
            AdyenDropInPayment.INSTANCE.triggerDetailsCall(actionComponentData);
        }
        return new CallResult(CallResult.ResultType.WAIT, "");
    }

    public void handleCallResult(CallResult callResult) {
        super.asyncCallback(callResult);
    }
}
