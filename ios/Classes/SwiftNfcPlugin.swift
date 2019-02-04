import Flutter
import UIKit

public class SwiftNfcPlugin: NSObject, FlutterPlugin {
    let nfcAvailable = false
    let nfcEnabled = false

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(
            name: "plugins.passless.com/nfc",
            binaryMessenger: registrar.messenger(),
            codec: FlutterJSONMethodCodec.sharedInstance()
        )
        let instance = SwiftNfcPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "configure":
            onConfigure()
            let props = [
                "nfcAvailable": nfcAvailable,
                "nfcEnabled": nfcEnabled
            ]
            result(props)
        case "gotoNfcSettings":
            result(
                FlutterError(
                    code: "UNAVAILABLE",
                    message: "NFC settings unavailable",
                    details: nil
                )
            )
        default:
            result(
                FlutterError(
                    code: "UNAVAILABLE",
                    message: "Method not implemented.",
                    details: nil
                )
            )
        }
    }
    
    private func onConfigure() {
        
    }

}
