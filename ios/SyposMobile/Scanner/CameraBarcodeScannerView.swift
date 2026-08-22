import SwiftUI
import AVFoundation

public struct CameraBarcodeScannerView: UIViewControllerRepresentable {
    public var onBarcodeScanned: (String) -> Void
    @Environment(\.presentationMode) var presentationMode

    public init(onBarcodeScanned: @escaping (String) -> Void) {
        self.onBarcodeScanned = onBarcodeScanned
    }

    public func makeUIViewController(context: Context) -> ScannerViewController {
        let vc = ScannerViewController()
        vc.delegate = context.coordinator
        return vc
    }

    public func updateUIViewController(_ uiViewController: ScannerViewController, context: Context) {}

    public func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    public class Coordinator: NSObject, ScannerViewControllerDelegate {
        let parent: CameraBarcodeScannerView

        init(parent: CameraBarcodeScannerView) {
            self.parent = parent
        }

        func didFindBarcode(_ code: String) {
            let generator = UINotificationFeedbackGenerator()
            generator.notificationOccurred(.success)
            parent.onBarcodeScanned(code)
            parent.presentationMode.wrappedValue.dismiss()
        }
    }
}

protocol ScannerViewControllerDelegate: AnyObject {
    func didFindBarcode(_ code: String)
}

public class ScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    weak var delegate: ScannerViewControllerDelegate?
    private var captureSession: AVCaptureSession?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var isTorchOn = false

    public override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        setupCamera()
        setupOverlay()
    }

    private func setupCamera() {
        let session = AVCaptureSession()
        guard let videoCaptureDevice = AVCaptureDevice.default(for: .video) else { return }
        guard let videoInput = try? AVCaptureDeviceInput(device: videoCaptureDevice) else { return }

        if session.canAddInput(videoInput) {
            session.addInput(videoInput)
        } else {
            return
        }

        let metadataOutput = AVCaptureMetadataOutput()
        if session.canAddOutput(metadataOutput) {
            session.addOutput(metadataOutput)
            metadataOutput.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
            metadataOutput.metadataObjectTypes = [
                .ean13, .ean8, .code128, .code39, .qr, .upce, .pdf417
            ]
        } else {
            return
        }

        let preview = AVCaptureVideoPreviewLayer(session: session)
        preview.frame = view.layer.bounds
        preview.videoGravity = .resizeAspectFill
        view.layer.addSublayer(preview)

        self.previewLayer = preview
        self.captureSession = session

        DispatchQueue.global(qos: .userInitiated).async {
            session.startRunning()
        }
    }

    private func setupOverlay() {
        // Reticle target guide
        let targetView = UIView()
        targetView.layer.borderColor = UIColor.systemGreen.cgColor
        targetView.layer.borderWidth = 3
        targetView.layer.cornerRadius = 16
        targetView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(targetView)

        // Close Button
        let closeBtn = UIButton(type: .system)
        closeBtn.setImage(UIImage(systemName: "xmark.circle.fill"), for: .normal)
        closeBtn.tintColor = .white
        closeBtn.translatesAutoresizingMaskIntoConstraints = false
        closeBtn.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)
        view.addSubview(closeBtn)

        // Torch Button
        let torchBtn = UIButton(type: .system)
        torchBtn.setImage(UIImage(systemName: "bolt.circle.fill"), for: .normal)
        torchBtn.tintColor = .white
        torchBtn.translatesAutoresizingMaskIntoConstraints = false
        torchBtn.addTarget(self, action: #selector(torchTapped), for: .touchUpInside)
        view.addSubview(torchBtn)

        NSLayoutConstraint.activate([
            targetView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            targetView.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            targetView.widthAnchor.constraint(equalTo: view.widthAnchor, multiplier: 0.75),
            targetView.heightAnchor.constraint(equalToConstant: 220),

            closeBtn.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            closeBtn.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            closeBtn.widthAnchor.constraint(equalToConstant: 44),
            closeBtn.heightAnchor.constraint(equalToConstant: 44),

            torchBtn.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            torchBtn.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            torchBtn.widthAnchor.constraint(equalToConstant: 44),
            torchBtn.heightAnchor.constraint(equalToConstant: 44)
        ])
    }

    @objc private func closeTapped() {
        dismiss(animated: true)
    }

    @objc private func torchTapped() {
        guard let device = AVCaptureDevice.default(for: .video), device.hasTorch else { return }
        do {
            try device.lockForConfiguration()
            isTorchOn.toggle()
            device.torchMode = isTorchOn ? .on : .off
            device.unlockForConfiguration()
        } catch {}
    }

    public func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        if let metadataObject = metadataObjects.first as? AVMetadataMachineReadableCodeObject, let stringValue = metadataObject.stringValue {
            captureSession?.stopRunning()
            delegate?.didFindBarcode(stringValue)
        }
    }

    public override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if captureSession?.isRunning == true {
            captureSession?.stopRunning()
        }
    }
}
