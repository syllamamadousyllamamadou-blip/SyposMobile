# 🍎 SYPOS Mobile — Version iOS (iPhone & iPad)

Bienvenue dans le projet natif **SYPOS Mobile pour iOS**, développé en **SwiftUI**, **CoreBluetooth**, **AVFoundation** et **PDFKit**.

---

## 📁 Architecture du Projet iOS (`ios/`)

```text
ios/
├── SyposMobile.xcodeproj/      <-- Projet Xcode à ouvrir directement
├── SyposMobile/
│   ├── App/
│   │   ├── SyposMobileApp.swift (Point d'entrée principal de l'application)
│   │   └── ContentView.swift    (Gestion de la navigation TabBar & Sécurité)
│   ├── Models/
│   │   ├── Enums.swift          (Modes de paiement, Statuts, Rôles, Modes métier)
│   │   ├── Product.swift        (Produits & Catégories)
│   │   ├── Ticket.swift         (Factures & Lignes d'articles)
│   │   └── Customer.swift       (Clients, Dépenses, Codes Promo & Paramètres)
│   ├── Database/
│   │   └── DataStore.swift      (Moteur de persistance local 100% hors-ligne)
│   ├── Bluetooth/
│   │   └── BluetoothPrinterManager.swift (Driver thermique ESC/POS BLE avec montants ASCII)
│   ├── Scanner/
│   │   └── CameraBarcodeScannerView.swift (Scanner de codes-barres caméra natif ultra-rapide)
│   ├── Helpers/
│   │   ├── LicenseManager.swift (Gestion des clés de licence 30j / 1 an / illimité)
│   │   └── PdfExportManager.swift (Exportateur et partage PDF natif iOS)
│   ├── ViewModels/
│   │   ├── PosViewModel.swift
│   │   ├── ProductViewModel.swift
│   │   ├── HistoryViewModel.swift
│   │   ├── ReportViewModel.swift
│   │   └── CustomerViewModel.swift
│   ├── Views/
│   │   ├── Pos/                 (Caisse, Panier, Encaissement, Reçu, Mise en attente)
│   │   ├── Product/             (Catalogue, Alertes stock, Valorisation financière)
│   │   ├── History/             (Historique avec filtres de dates & Annulations)
│   │   ├── Report/              (Bilan, Rapport Z de clôture & Dépenses)
│   │   ├── Customer/            (Carnet clients & Recouvrement des dettes)
│   │   ├── Tools/               (Générateur d'étiquettes multi-modes & Fiches WhatsApp)
│   │   ├── Settings/            (Configuration boutique, Imprimante, PIN Admin)
│   │   └── Auth/                (Activation initiale bloquante & Verrouillage PIN)
│   └── Resources/
│       └── Info.plist           (Permissions Appareil Photo & Bluetooth)
```

---

## 🚀 Comment Lancer et Tester sur iPhone

1. **Ouvrir avec Xcode** :
   Double-cliquez sur le fichier :
   📁 **`ios/SyposMobile.xcodeproj`**

2. **Sélectionner votre appareil** :
   - Branchez votre iPhone avec son câble Lightning / USB-C à votre Mac (ou choisissez un simulateur iPhone 14/15/16 Pro).

3. **Lancer l'application** :
   - Cliquez sur le bouton **Play (▶️)** dans Xcode ou tapez `Cmd + R`.
   - L'application s'installe et se lance instantanément sur votre iPhone !

---

## ✨ Fonctionnalités Incluses (100% Identiques à la version Android)

- ✅ **Encaissement tactile ultra-fluide** avec prise en charge Restaurant (Sur place / Emporter) et Supermarché.
- ✅ **Scan caméra natif instantané** de tous les codes-barres (EAN-13, Code 128, QR Code).
- ✅ **Impression thermique Bluetooth BLE** (Reçus de caisse, bordereaux de livraison, rapport Z et étiquettes intelligentes).
- ✅ **Sécurité PIN Admin obligatoire** pour toutes les annulations et suppressions.
- ✅ **Protection par licence** au premier lancement avec activation en 1 clic.
- ✅ **Export PDF & Partage WhatsApp** avec la feuille de partage native iOS.
