import { NativeEventEmitter, NativeModules, Linking } from 'react-native';

const AdyenDropIn = NativeModules.AdyenDropInPayment;
const eventEmitter = new NativeEventEmitter(AdyenDropIn);

export default {
  /**
   * Start Drop-in
   *
   * @param {object} paymentMethodJson
   * @param {object} config
   *
   * @returns {*}
   */
  startPayment(paymentMethodJson, config) {
    if (!this.handleRedirectURL) {
      this.handleRedirectURL = ({ url }) => AdyenDropIn.handleRedirectURL(url);
      Linking.addEventListener('url', this.handleRedirectURL);
    }
    return AdyenDropIn.startPayment(paymentMethodJson, config);
  },

  /**
   * Handle server response
   * @param {string|object} response
   * @returns {*}
   */
  handleResponse(response) {
    if (!response) return;
    if (typeof response === 'string') response = JSON.parse(response);
    if (response.action) {
      return AdyenDropIn.handleAction(JSON.stringify(response.action));
    } else {
      return AdyenDropIn.handlePaymentResult(JSON.stringify(response));
    }
  },
  addListener(eventName, listener) {
    return eventEmitter.addListener(eventName, listener);
  },
  listenerCount(eventName) {
    if (eventEmitter.listenerCount) {
      return eventEmitter.listenerCount(eventName);
    } else {
      return eventEmitter.listeners(eventName).length;
    }
  },
};
