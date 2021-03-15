# React Native Adyen Drop-in

**React Native Adyen Drop-in** is a cross platform (Android & iOS) plugin enabling Adyen Drop-in integration in a React Native project.

## Disclamer

At the moment the implementation is very opiniated on the use case we encountered. Feel free to submit P.R.

## Getting Started

`$ yarn add react-native-adyen-drop-in`

This module uses autolinking and has not been tested on RN<0.60.

## API

```jsx
import AdyenDropIn from 'react-native-adyen-drop-in';

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

// Open the Drop-in component by providing a config and the
// available payment methods.
AdyenDropIn.startPayment(paymentMethodJson, config);

// Provide the Drop-in component the response of your server.
AdyenDropIn.handleResponse(response)

// Register a listener when payment form is submitted
AdyenDropIn.addListener('payment', response => {})
/**
 * response {
 *  data: {
 *      paymentMethod: {
 *          type: "scheme",
 *          recurringDetailReference: string
 *      },
 *      storePaymentMethod: true,
 *  }
 * }
 */

// Register a listener on Adyen3DS2Component and RedirectComponent responses
AdyenDropIn.addListener('payment-details', response => {})
/**
 * response {
 *  data: {
 *      paymentData,
 *      details,
 *  }
 * }
 */

// Register a listener on payment failures
AdyenDropIn.addListener('error', error => {})
/**
 * error {
 *  message: string, // error message
 *  error: string // exception message
 * }
 */

```

## License

This repository is open source and available under the MIT license.