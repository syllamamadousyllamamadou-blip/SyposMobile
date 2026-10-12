# 📱 SYPOS Mobile Pro — Architecture du Projet

Application de Caisse Enregistreuse & Point de Vente (POS) tout-en-un avec fonctionnement 100% Hors-ligne, Impression Thermique Bluetooth ESC/POS, CRM Clients, Reçus WhatsApp PNG, Sauvegarde JSON et Synchronisation Google Sheets à distance.

---

## 📂 Structure des Dossiers & Rôles

```
POS MOBILE/
├── 🤖 app/                 # Application Android Native (Kotlin + Jetpack Compose + Room)
│   ├── src/main/java/     # Code source Kotlin (Architecture MVVM + Clean Architecture)
│   │   ├── data/          # Room DB, Entités, DAOs, SharedPreferences
│   │   ├── domain/        # Modèles métier & Interfaces des repositories
│   │   ├── ui/            # Écrans Compose (POS, Catalogue, CRM Clients, Historique, Rapport, Paramètres)
│   │   └── util/          # Helpers (Receipt PNG Canvas, Bluetooth ESC/POS, Sync Google Sheets, Backup JSON)
│   └── res/               # Ressources Android (XML, Thèmes, Icônes, Strings)
│
├── 🌐 web/                 # Application Web / PWA (Fonctionne sur tout navigateur & mobile)
│   ├── index.html         # Interface utilisateur HTML5 complète (POS, CRM, Modales, Reçus)
│   ├── app.js             # Moteur JavaScript (IndexedDB, Web Bluetooth, html2canvas, Google Sheets)
│   ├── style.css          # Design moderne, responsive et thème sombre/clair
│   ├── manifest.json      # Configuration PWA installable sur écran d'accueil
│   └── service-worker.js  # Cache hors-ligne pour fonctionnement sans connexion internet
│
├── 🍏 ios/                 # Application iOS Native (Swift + SwiftUI)
│   └── SyposMobile/       # Vues SwiftUI, ViewModels, DataStore local & gestion Bluetooth
│
├── 🔑 license-portal/      # Portail Web Administrateur de Génération des Licences
│   ├── index.html         # Interface Web sécurisée de génération & signature cryptographique RSA
│   ├── server.py          # Serveur local Python pour tests
│   └── README.md          # Guide d'utilisation du portail
│
├── 🛠️ tools/               # Utilitaires & Clés Cryptographiques
│   ├── license_generator.py # Script Python de signature des licences
│   ├── sypos_private_key.pem # Clé privée RSA (Secret Administrateur)
│   └── sypos_public_key.pem  # Clé publique RSA embarquée dans les apps
│
├── 📦 builds/              # Fichiers binaires compilés (APK Android, etc.)
│   └── SyposMobile-v1.0.apk # Dernier build APK installable
│
├── ⚙️ Fichiers Gradle      # Scripts de build Android (build.gradle.kts, settings.gradle.kts, gradlew)
└── 🚀 vercel.json          # Configuration de déploiement Web automatique
```

---

## 🌟 Principaux Modules & Fonctionnalités

1. **POS & Caisse Rapide** : Saisie express au panier, recherche produits, lecture de codes-barres par caméra, remises, multi-paiements (Espèces, Wave, Orange Money, MTN, Moov, Carte).
2. **Reçus WhatsApp PNG** : Génération instantanée de tickets de caisse graphiques haute définition prêts à être partagés sur WhatsApp.
3. **Base de Données Clients CRM** : Fiche client, historique des dépenses cumulées (CFA), nombre de passages, et relances marketing WhatsApp en 1 clic (*Remerciement/Fidélité*, *Nouveautés*, *Clients Inactifs*).
4. **Suivi à Distance Google Sheets** : Envoi asynchrone ultra-rapide des ventes et dépenses vers un tableur Google Sheets via Webhook Apps Script sans ralentir les encaissements.
5. **Sauvegarde & Restauration** : Export/Import complet de la base de données en format JSON (partageable par WhatsApp / Drive) pour sécuriser l'activité.
6. **Impression Thermique ESC/POS** : Support direct des imprimantes Bluetooth 58mm et 80mm.
