// swift-tools-version:5.7
import PackageDescription

let package = Package(
    name: "SyposMobile",
    platforms: [
        .iOS(.v15),
        .macOS(.v12)
    ],
    products: [
        .library(
            name: "SyposMobileLib",
            targets: ["SyposMobileLib"]
        )
    ],
    dependencies: [],
    targets: [
        .target(
            name: "SyposMobileLib",
            dependencies: [],
            path: "SyposMobile"
        )
    ]
)
