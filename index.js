import { NativeEventEmitter, NativeModules } from 'react-native';

const AdyenDropIn = NativeModules.AdyenDropInPayment;
const EventEmitter = new NativeEventEmitter(AdyenDropIn);
const eventMap = {};
const addListener = (key, listener) => {
  if (!eventMap[key]) {
    eventMap[key] = [];
  }
  const ee = EventEmitter.addListener(key, listener);
  eventMap[key].push(ee);
  return ee;
};

export default {
  /**
   * Start Drop-in
   *
   * @param {object} paymentMethodJson
   * @param {object} config
   *
   * @returns {*}
   */
  paymentMethods(paymentMethodJson, config) {
    return AdyenDropIn.paymentMethods(paymentMethodJson, config);
  },

  /**
   * handle Action from payments
   * @param actionJson
   * @returns {*}
   */
  handleAction(actionJson) {
    if (typeof actionJson === 'object') {
      actionJson = JSON.stringify(actionJson);
    }
    this._validateParam(actionJson, 'handleAction', 'string');
    return AdyenDropIn.handleAction(actionJson);
  },
  handlePaymentResult(paymentJson) {
    if (typeof paymentJson === 'object') {
      paymentJson = JSON.stringify(paymentJson);
    }
    this._validateParam(paymentJson, 'handlePaymentResult', 'string');
    return AdyenDropIn.handlePaymentResult(paymentJson);
  },

  /**
   *  call when need to do more action
   */
  onPaymentProvide(mOnPaymentProvide) {
    this._validateParam(mOnPaymentProvide, 'onPaymentProvide', 'function');
    return addListener('onPaymentProvide', e => {
      mOnPaymentProvide(e);
    });
  },
  // /**
  //  * call when cancel a payment
  //  * @param mOnPaymentCancel
  //  */
  // onPaymentCancel(mOnPaymentCancel) {
  //     this._validateParam(
  //         mOnPaymentCancel,
  //         'onPaymentCancel',
  //         'function',
  //     );
  //     onPaymentCancelListener = events.addListener(
  //         'mOnPaymentCancel',
  //         e => {
  //             mOnPaymentCancel(e);
  //         },
  //     );
  // },
  /**
   * call when payment fail
   * @param {mOnError} mOnError
   */
  onPaymentFail(mOnPaymentFail) {
    this._validateParam(mOnPaymentFail, 'onPaymentFail', 'function');
    return addListener('onPaymentFail', e => {
      mOnPaymentFail(e);
    });
  },
  /**
   * call when payment submit ,send to server do payments
   */
  onPaymentSubmit(mOnPaymentSubmit) {
    this._validateParam(mOnPaymentSubmit, 'onPaymentSubmit', 'function');
    return addListener('onPaymentSubmit', e => {
      mOnPaymentSubmit(e);
    });
  },

  /**
   * @param {*} param
   * @param {String} methodName
   * @param {String} requiredType
   * @private
   */
  _validateParam(param, methodName, requiredType) {
    if (typeof param !== requiredType) {
      throw new Error(
        `Error: AdyenDropIn.${methodName}() requires a ${
          requiredType === 'function' ? 'callback function' : requiredType
        } but got a ${typeof param}`
      );
    }
  },
  events: EventEmitter,
  removeListeners() {
    Object.keys(eventMap).forEach(eventKey => {
      if (!eventMap[eventKey]) {
        return;
      }
      eventMap[eventKey].forEach(ee => {
        if (!ee.remove) {
          return;
        }
        ee.remove();
      });
      eventMap[eventKey] = undefined;
    });
  }
};
