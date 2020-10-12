//
//  AdyenDropInPayment.swift
//  ReactNativeAdyenDropin
//
//  Created by 罗立树 on 2019/9/27.
//  Copyright © 2019 Facebook. All rights reserved.
//

import Adyen
import Foundation
import SafariServices

@objc(AdyenDropInPayment)
class AdyenDropInPayment: RCTEventEmitter {
  func dispatch(_ closure: @escaping () -> Void) {
    if Thread.isMainThread {
      closure()
    } else {
      DispatchQueue.main.async(execute: closure)
    }
  }

  func requiresMainQueueSetup() -> Bool {
    return true
  }
  var dropInComponent: DropInComponent?
  var threeDS2Component: ThreeDS2Component?
  var publicKey: String?
  var isDropIn:Bool?
  var envName: String?
  var configuration: DropInComponent.PaymentMethodsConfiguration?
  var payment: Payment?

  override func supportedEvents() -> [String]! {
    return [
      "onPaymentFail",
      "onPaymentProvide",
      "onPaymentSubmit",
    ]
  }
}

extension AdyenDropInPayment: DropInComponentDelegate {
  @objc func paymentMethods(_ paymentMethodsJson: NSDictionary, config: NSDictionary) {
    self.isDropIn = true
    var paymentMethods: PaymentMethods?
    do {
        let jsonData = try! JSONSerialization.data(withJSONObject : paymentMethodsJson, options: .prettyPrinted)
        paymentMethods = try Coder.decode(jsonData) as PaymentMethods
    } catch {}

    buildConfig(config)

    let dropInComponent = DropInComponent(paymentMethods: paymentMethods!,
                                          paymentMethodsConfiguration: configuration!)
    self.dropInComponent = dropInComponent
    dropInComponent.delegate = self
    dropInComponent.environment = configuration!.environment
    dropInComponent.payment = payment

    dispatch {
      UIApplication.shared.delegate?.window??.rootViewController!.present(dropInComponent.viewController, animated: true)
    }
  }

  func buildConfig(_ config: NSDictionary) {
    let configuration = DropInComponent.PaymentMethodsConfiguration()
    self.configuration = configuration

    let env = config["environment"] as? String ?? "test"
    if (env == "liveEurope") { configuration.environment = Environment.liveEurope }
    else if (env == "liveUnitedStates") { configuration.environment = Environment.liveUnitedStates }
    else if (env == "liveAustralia") { configuration.environment = Environment.liveAustralia }
    else { configuration.environment = Environment.test }

    configuration.clientKey = config["clientKey"] as? String

    let scheme: [String:Any] = config["scheme"] as? [String:Any] ?? [:]
    configuration.card.showsHolderNameField = scheme["showsHolderNameField"] as? Bool ?? false
    configuration.card.showsStorePaymentMethodField = scheme["showsStorePaymentMethodField"] as? Bool ?? false

    let applePay: [String:Any] = config["applePay"] as? [String:Any] ?? [:]
    configuration.applePay.merchantIdentifier = applePay["merchantIdentifier"] as? String

    let amountDict: [String:Any] = config["amount"] as? [String:Any] ?? [:]
    let amount = Payment.Amount(value: amountDict["value"] as! Int, currencyCode: amountDict["currency"] as! String)
    payment = Payment(amount: amount)
  }

  func didSubmit(_ data: PaymentComponentData, from component: DropInComponent) {
    component.viewController.dismiss(animated: true)
    var paymentMethodMap: Dictionary? = data.paymentMethod.dictionaryRepresentation
    paymentMethodMap!["recurringDetailReference"] = paymentMethodMap!["storedPaymentMethodId"]
    let resultData = ["paymentMethod": paymentMethodMap, "storePaymentMethod": data.storePaymentMethod] as [String: Any]
    
    sendEvent(
      withName: "onPaymentSubmit",
      body: [
        "isDropIn": self.isDropIn,
        "env": self.envName,
        "data": resultData,
      ]
    )
  }

  /// Invoked when additional details have been provided for a payment method.
  ///
  /// - Parameters:
  ///   - data: The additional data supplied by the drop in component..
  ///   - component: The drop in component from which the additional details were provided.
  func didProvide(_ data: ActionComponentData, from component: DropInComponent) {
    component.viewController.dismiss(animated: true)
    let resultData = ["details": data.details.dictionaryRepresentation, "paymentData": data.paymentData] as [String: Any]
    sendEvent(
      withName: "onPaymentProvide",
      body: [
        "isDropIn": self.isDropIn,
        "env": self.envName,
        "data": resultData,
      ]
    )
  }

  /// Invoked when the drop in component failed with an error.
  ///
  /// - Parameters:
  ///   - error: The error that occurred.
  ///   - component: The drop in component that failed.
  func didFail(with error: Error, from component: DropInComponent) {
    component.viewController.dismiss(animated: true)
    sendEvent(
      withName: "onPaymentFail",
      body: [
        "isDropIn": self.isDropIn,
        "env": self.envName,
        "msg": error.localizedDescription,
        "error": String(describing: error),
      ]
    )
  }
}

extension AdyenDropInPayment: PaymentComponentDelegate {
  /// Invoked when the payment component finishes, typically by a user submitting their payment details.
  ///
  /// - Parameters:
  ///   - data: The data supplied by the payment component.
  ///   - component: The payment component from which the payment details were submitted.
  func didSubmit(_ data: PaymentComponentData, from _: PaymentComponent) {
    var paymentMethodMap: Dictionary? = data.paymentMethod.dictionaryRepresentation
    paymentMethodMap!["recurringDetailReference"] = paymentMethodMap!["storedPaymentMethodId"]
    let resultData = ["paymentMethod": paymentMethodMap, "storePaymentMethod": data.storePaymentMethod] as [String: Any]

    sendEvent(
      withName: "onPaymentSubmit",
      body: [
        "isDropIn": self.isDropIn,
        "env": self.envName,
        "data": resultData,
      ]
    )
  }

  /// Invoked when the payment component fails.
  ///
  /// - Parameters:
  ///   - error: The error that occurred.
  ///   - component: The payment component that failed.
  func didFail(with error: Error, from _: PaymentComponent) {
    sendEvent(
      withName: "onPaymentFail",
      body: [
        "isDropIn": self.isDropIn,
        "env": self.envName,
        "msg": error.localizedDescription,
        "error": String(describing: error),
      ]
    )
  }
}

extension AdyenDropInPayment: ActionComponentDelegate {
  @objc func handleAction(_ actionJson: String) {
    if(actionJson == nil||actionJson.count<=0){
        return;
    }
    var parsedJson = actionJson.replacingOccurrences(of: "THREEDS2FINGERPRINT", with: "threeDS2Fingerprint")
    parsedJson = actionJson.replacingOccurrences(of: "THREEDS2CHALLENGE", with: "threeDS2Challenge")
    parsedJson = actionJson.replacingOccurrences(of: "REDIRECT", with: "redirect")
    if(self.isDropIn!){
        let actionData: Data? = parsedJson.data(using: String.Encoding.utf8) ?? Data()
        let action = try? JSONDecoder().decode(Action.self, from: actionData!)
        dropInComponent?.handle(action!)
      return;
    }
    let actionData: Data? = parsedJson.data(using: String.Encoding.utf8) ?? Data()
    let action:Action? = try! JSONDecoder().decode(Action.self, from: actionData!)

    switch action {
    /// Indicates the user should be redirected to a URL.
    case .redirect(let executeAction):
       let redirectComponent:RedirectComponent = RedirectComponent(action: executeAction)
       redirectComponent.delegate = self
      break;
      /// Indicates a 3D Secure device fingerprint should be taken.
    case .threeDS2Fingerprint(let executeAction):
      if(self.threeDS2Component == nil){
        self.threeDS2Component = ThreeDS2Component()
        self.threeDS2Component!.delegate = self
      }
      self.threeDS2Component!.handle(executeAction)
      break;
      /// Indicates a 3D Secure challenge should be presented.
    case .threeDS2Challenge(let executeAction):
      if(self.threeDS2Component == nil){
        self.threeDS2Component = ThreeDS2Component()
        self.threeDS2Component!.delegate = self
      }
      self.threeDS2Component?.handle(executeAction)
      break;
    default :
      break;
    }
  }
  @objc func handlePaymentResult(_ paymentResult: String) {
  }

  /// Invoked when the action component finishes
  /// and provides the delegate with the data that was retrieved.
  ///
  /// - Parameters:
  ///   - data: The data supplied by the action component.
  ///   - component: The component that handled the action.
  func didProvide(_ data: ActionComponentData, from _: ActionComponent) {
    let resultData = ["details": data.details.dictionaryRepresentation, "paymentData": data.paymentData] as [String: Any]
    sendEvent(
      withName: "onPaymentProvide",
      body: [
        "isDropIn": self.isDropIn as Any,
        "env": self.envName as Any,
        "data": resultData,
      ]
    )
  }

  /// Invoked when the action component fails.
  ///
  /// - Parameters:
  ///   - error: The error that occurred.
  ///   - component: The component that failed.
  func didFail(with error: Error, from _: ActionComponent) {
    sendEvent(
      withName: "onPaymentFail",
      body: [
        "isDropIn": self.isDropIn as Any,
        "env": self.envName as Any,
        "msg": error.localizedDescription,
        "error": String(describing: error),
      ]
    )
  }
}
