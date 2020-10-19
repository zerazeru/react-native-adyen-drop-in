# React Native Adyen Drop-In

**React Native Adyen Drop-In** is a cross platform (Android & iOS) plugin enabling Adyen Drop-In integration in a React-Native project.

## Disclamer

At the moment the implementation is very opiniated on the use case we encountered. Feel free to submit P.R.

## Getting Started

`$ yarn add react-native-adyen-drop-in`

This module uses autolinking and has not been tested on RN<0.60.

## API

```jsx
import AdyenDropIn from 'react-native-adyen-drop-in'

const config = {
    environment: 'test', // test / liveEurope / liveUnitedStates / liveAustralia
    clientKey: 'xxxx_xxxxx...',
    merchantAccount: 'XXXXX',
    shopperLocale: 'en_US',
    amount: {currency: 'EUR', value: 2500}, // 25€
    scheme: {
        publicKey: 'xxxxx...', // client encryption public key
        showsHolderNameField: true,
        showsStorePaymentMethodField: true,
    },
};

// Supply Adyen with the available payment methods. Populate it from https://docs.adyen.com/api-explorer/#/PaymentSetupAndVerificationService/paymentMethods or supply custom JSON yourself.
AdyenDropIn.paymentMethods(paymentMethodJson, config);

// Handle further actions (like 3DS etc) asked by Adyen in (action key in /payments response - ie iDEAL, Bancontact)
AdyenDropIn.handleAction(actionJson)

// Notify Adyen Drop In of the payment result.
AdyenDropIn.handlePaymentResult(paymentResult)

// Register a listener on Adyen3DS2Component and RedirectComponent responses
AdyenDropIn.onPaymentProvide((response) => {})
/**
 * response {
 *  isDropIn: boolean,
 *  env: string,
 *  msg: string,
 *  data: {
 *      paymentData,
 *      details,
 *  }
 * }
 */

// Register a listener on payment failures
AdyenDropIn.onPaymentFail((error) => {})
/**
 * error {
 *  isDropIn: boolean,
 *  env: string,
 *  msg: string, // error message
 *  error: string // exception message
 * }
 */

// Register a listener when payment form is submitted
AdyenDropIn.onPaymentSubmit((response) => {})
/**
 * response {
 *  isDropIn: boolean,
 *  env: string,
 *  data: {
 *      paymentMethod: {
 *          type: "scheme",
 *          recurringDetailReference: string
 *      },
 *      storePaymentMethod: true,
 *  }
 * }
 */


```

## License

This repository is open source and available under the MIT license.