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
  var cardComponent: CardComponent?
  var threeDS2Component: ThreeDS2Component?
  var publicKey: String?
  var env: Environment?
  var envName: String?
  var configuration: DropInComponent.PaymentMethodsConfiguration?
  override func supportedEvents() -> [String]! {
    return [
      "onPaymentFail",
      "onPaymentProvide",
      "onPaymentSubmit",
    ]
  }
}

extension AdyenDropInPayment: DropInComponentDelegate {
  @objc func configPayment(_ publicKey: String, env: String) {
    configuration = DropInComponent.PaymentMethodsConfiguration()
    configuration?.card.publicKey = publicKey
    self.publicKey = publicKey
    configuration?.card.showsStorePaymentMethodField = false
    envName = env
    switch env {
    case "live":
      self.env = .live
    default:
      self.env = .test
    }
  }

  @objc func paymentMethods(_ paymentMethodsJson: String) {
    let jsonData: Data? = paymentMethodsJson.data(using: String.Encoding.utf8) ?? Data()
    let paymentMethods: PaymentMethods? = try? JSONDecoder().decode(PaymentMethods.self, from: jsonData!)
    let dropInComponent = DropInComponent(paymentMethods: paymentMethods!,
                                          paymentMethodsConfiguration: configuration!)
    self.dropInComponent = dropInComponent
    dropInComponent.delegate = self
    dropInComponent.environment = env!

    dispatch {
      UIApplication.shared.delegate?.window??.rootViewController!.present(dropInComponent.viewController, animated: true)
    }
  }

  func didSubmit(_ data: PaymentComponentData, from component: DropInComponent) {
    component.viewController.dismiss(animated: true)
//    let returnData=["encryptedCardNumber":encryptedCard.number,
//    "encryptedSecurityCode":encryptedCard.securityCode, "encryptedExpiryMonth":encryptedCard.expiryMonth, "encryptedExpiryYear":encryptedCard.expiryYear] as [String : Any]
    let resultData = ["paymentMethod": data.paymentMethod.dictionaryRepresentation, "storePaymentMethod": data.storePaymentMethod] as [String: Any]
    var paymentMethodData: Dictionary? = resultData["paymentMethod"] as! [String: Any]
    paymentMethodData!["recurringDetailReference"] = paymentMethodData!["storedPaymentMethodId"]
    sendEvent(
      withName: "onPaymentSubmit",
      body: [
        "isDropIn": true,
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
        "isDropIn": true,
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
        "isDropIn": true,
        "env": self.envName,
        "msg": error.localizedDescription,
        "error": String(describing: error),
      ]
    )
  }
}

extension AdyenDropInPayment: PaymentComponentDelegate {
  func getStoredCardPaymentMethod(_ paymentMethods: PaymentMethods, index: Int) -> StoredCardPaymentMethod {
    var paymentMethod: StoredCardPaymentMethod?
    if paymentMethods.stored.count == 1 {
      return paymentMethods.stored[0] as! StoredCardPaymentMethod
    }
    if paymentMethods.stored.count > 1 {
      paymentMethod = paymentMethods.stored[index] as! StoredCardPaymentMethod
    }
    return paymentMethod!
  }

  func getCardPaymentMethodByName(_ paymentMethods: PaymentMethods, name _: String) -> CardPaymentMethod {
    var paymentMethod: CardPaymentMethod?
    if paymentMethods.regular.count == 1 {
      return paymentMethods.regular[0] as! CardPaymentMethod
    }
    if paymentMethods.regular.count > 1 {
      for p in paymentMethods.regular {
        if p.name == "Credit Card" {
          paymentMethod = (p as! CardPaymentMethod)
          break
        }
      }
    }
    return paymentMethod!
  }

  @objc func storedCardPaymentMethod(_ paymentMethodsJson: String, index: Int) {
    let jsonData: Data? = paymentMethodsJson.data(using: String.Encoding.utf8) ?? Data()
    let paymentMethods: PaymentMethods? = try? JSONDecoder().decode(PaymentMethods.self, from: jsonData!)
    let cardPaymentMethod: StoredCardPaymentMethod? = getStoredCardPaymentMethod(paymentMethods!, index: index)
    let cardComponent = CardComponent(paymentMethod: cardPaymentMethod!,
                                      publicKey: publicKey!)
    self.cardComponent = cardComponent
    // Replace CardComponent with the payment method Component that you want to add.
    // Check specific payment method pages to confirm if you need to configure additional required parameters.
    // For example, to enable the Card form, you need to provide your Client Encryption Public Key.
    cardComponent.delegate = self
    cardComponent.environment = env!
    // When you're ready to go live, change this to .live
    // or to other environment values described in https://adyen.github.io/adyen-ios/Docs/Structs/Environment.html
    dispatch { UIApplication.shared.delegate?.window??.rootViewController!.present(cardComponent.viewController, animated: true)
    }
  }

  @objc func cardPaymentMethod(_ paymentMethodsJson: String, name: String, showHolderField: Bool, showStoreField: Bool) {
    let jsonData: Data? = paymentMethodsJson.data(using: String.Encoding.utf8) ?? Data()
    let paymentMethods: PaymentMethods? = try? JSONDecoder().decode(PaymentMethods.self, from: jsonData!)
    let cardPaymentMethod: CardPaymentMethod? = getCardPaymentMethodByName(paymentMethods!, name: name)

    let cardComponent = CardComponent(paymentMethod: cardPaymentMethod!,
                                      publicKey: publicKey!)
    self.cardComponent = cardComponent
    cardComponent.showsStorePaymentMethodField = showStoreField
    cardComponent.showsHolderNameField = showHolderField
    // Replace CardComponent with the payment method Component that you want to add.
    // Check specific payment method pages to confirm if you need to configure additional required parameters.
    // For example, to enable the Card form, you need to provide your Client Encryption Public Key.
    cardComponent.delegate = self
    cardComponent.environment = env!
    // When you're ready to go live, change this to .live
    // or to other environment values described in https://adyen.github.io/adyen-ios/Docs/Structs/Environment.html
    dispatch {
      UIApplication.shared.delegate?.window??.rootViewController!.present(cardComponent.viewController, animated: true)
    }
  }

  /// Invoked when the payment component finishes, typically by a user submitting their payment details.
  ///
  /// - Parameters:
  ///   - data: The data supplied by the payment component.
  ///   - component: The payment component from which the payment details were submitted.
  func didSubmit(_ data: PaymentComponentData, from _: PaymentComponent) {
    cardComponent?.viewController.dismiss(animated: true)

    var paymentMethodMap: Dictionary? = data.paymentMethod.dictionaryRepresentation
    paymentMethodMap!["recurringDetailReference"] = paymentMethodMap!["storedPaymentMethodId"]
    let resultData = ["paymentMethod": paymentMethodMap, "storePaymentMethod": data.storePaymentMethod] as [String: Any]

    sendEvent(
      withName: "onPaymentSubmit",
      body: [
        "isDropIn": false,
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
    cardComponent?.viewController.dismiss(animated: true)

    sendEvent(
      withName: "onPaymentFail",
      body: [
        "isDropIn": false,
        "env": self.envName,
        "msg": error.localizedDescription,
        "error": String(describing: error),
      ]
    )
  }
}

extension AdyenDropInPayment: ActionComponentDelegate {
  @objc func handleAction(_ actionJson: String) {
    let actionData: Data? = actionJson.data(using: String.Encoding.utf8) ?? Data()
    let action = try? JSONDecoder().decode(Action.self, from: actionData!)
    dropInComponent?.handle(action!)
  }

  @objc func handleRedirectAction(_ actionJson: String) {
    let actionData: Data? = actionJson.data(using: String.Encoding.utf8) ?? Data()
    let redirectAction = try? JSONDecoder().decode(Action.self, from: actionData!)
    let redirectComponent: RedirectComponent = RedirectComponent(action: redirectAction as! RedirectAction)
    redirectComponent.delegate = self
  }

  func handleThreeDS2FingerprintAction(_ actionJson: String) {
    let actionData: Data? = actionJson.data(using: String.Encoding.utf8) ?? Data()
    let action = try? JSONDecoder().decode(Action.self, from: actionData!)
    if threeDS2Component == nil {
      let threeDS2Component = ThreeDS2Component()
      threeDS2Component.delegate = self
      self.threeDS2Component = threeDS2Component
    }
    threeDS2Component!.handle(action as! ThreeDS2FingerprintAction)
  }

  func handleThreeDS2ChallengeAction(_ actionJson: String) {
    let actionData: Data? = actionJson.data(using: String.Encoding.utf8) ?? Data()
    let action = try? JSONDecoder().decode(Action.self, from: actionData!)
    if threeDS2Component == nil {
      let threeDS2Component = ThreeDS2Component()
      threeDS2Component.delegate = self
      self.threeDS2Component = threeDS2Component
    }
    threeDS2Component!.handle(action as! ThreeDS2FingerprintAction)
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
        "isDropIn": false,
        "env": self.envName,
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
        "isDropIn": false,
        "env": self.envName,
        "msg": error.localizedDescription,
        "error": String(describing: error),
      ]
    )
  }
}