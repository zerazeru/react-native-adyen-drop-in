import Adyen
import Foundation
import SafariServices

@objc(AdyenDropInPayment)
class AdyenDropInPayment: RCTEventEmitter {
  var dropInComponent: DropInComponent?
  var publicKey: String?
  var configuration: DropInComponent.PaymentMethodsConfiguration?
  var payment: Payment?

  func requiresMainQueueSetup() -> Bool {
    return true
  }

  override func supportedEvents() -> [String]! {
    return [
      "payment",
      "payment-details",
      "error",
    ]
  }

  func dispatch(_ closure: @escaping () -> Void) {
    if Thread.isMainThread {
      closure()
    } else {
      DispatchQueue.main.async(execute: closure)
    }
  }

  @objc func handlePaymentResult(_ paymentResult: String) {
    dispatch {
      self.dropInComponent?.stopLoading(withSuccess: true, completion: {
        self.dropInComponent?.viewController.dismiss(animated: true)
      })
    }
  }

  @objc func handleRedirectURL(_ url: String) {
    dispatch {
      RedirectComponent.applicationDidOpen(from: URL(string: url)!)
    }
  }
}

extension AdyenDropInPayment: DropInComponentDelegate {
  @objc func startPayment(_ paymentMethodsJson: NSDictionary, config: NSDictionary) {
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
      if var topController = UIApplication.shared.keyWindow?.rootViewController {
        while let presentedViewController = topController.presentedViewController {
          topController = presentedViewController
        }
        topController.present(dropInComponent.viewController, animated: true)
      }
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
    var paymentMethodMap: Dictionary? = data.paymentMethod.dictionaryRepresentation
    paymentMethodMap!["recurringDetailReference"] = paymentMethodMap!["storedPaymentMethodId"]
    let resultData = ["paymentMethod": paymentMethodMap, "storePaymentMethod": data.storePaymentMethod] as [String: Any]
    
    sendEvent(
      withName: "payment",
      body: [
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
      withName: "payment-details",
      body: [
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
      withName: "error",
      body: [
        "message": error.localizedDescription,
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
      withName: "payment",
      body: [
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
      withName: "error",
      body: [
        "message": error.localizedDescription,
        "error": String(describing: error),
      ]
    )
  }
}

extension AdyenDropInPayment: ActionComponentDelegate {
  @objc func handleAction(_ actionJson: String) {
    let actionData: Data? = actionJson.data(using: String.Encoding.utf8) ?? Data()
    let action: Action? = try! JSONDecoder().decode(Action.self, from: actionData!)

    dispatch {
      self.dropInComponent?.handle(action!)
    }
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
      withName: "payment-details",
      body: [
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
      withName: "error",
      body: [
        "message": error.localizedDescription,
        "error": String(describing: error),
      ]
    )
  }
}
