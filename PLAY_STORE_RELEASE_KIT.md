# 📦 KIT DE PUBLICATION GOOGLE PLAY STORE — KAYO POS MOBILE

Ce guide contient l'ensemble des éléments textuels, réglementaires et techniques nécessaires pour soumettre et publier **KAYO POS** sur la **Google Play Console**.

---

## 1. FICHE GOOGLE PLAY STORE (METADATA)

### A. Titre de l'application (Max 30 caractères)
`KAYO - Caisse & Stock POS`

### B. Description courte (Max 80 caractères)
`Caisse enregistreuse tactile, gestion de stock, reçus WhatsApp & multi-vendeurs.`

### C. Description complète (Max 4000 caractères)
```text
KAYO est l'application de caisse enregistreuse tactile et de gestion commerciale tout-en-un, spécialement conçue pour les commerçants, boutiques, restaurants, supermarchés et prestataires de services en Afrique et à l'international.

Gérez vos encaissements, vos stocks, vos clients et vos vendeurs en toute simplicité, avec ou sans connexion Internet !

🌟 FONCTIONNALITÉS PRINCIPALES :

⚡ ENCAISSEMENT & POINT DE VENTE ULTRA-RAPIDE :
• Saisie intuitive des articles au panier en un toucher.
• Scanner de code-barres ultra-rapide via la caméra de votre smartphone ou douchette.
• Application de remises en pourcentage ou en montant fixe et gestion des codes promo.
• Prise en charge de multiples modes de paiement : Espèces, Wave, Orange Money, MTN MoMo, Moov Money, Carte bancaire et Ventes à crédit.
• Ventes en attente et paniers multiples.

🖨️ REÇUS PROFESSIONNELS & WHATSAPP :
• Impression thermique directe sur imprimantes Bluetooth (format 58 mm et 80 mm) sans configuration complexe.
• Partage instantané du reçu en image PNG haute définition directement sur WhatsApp pour vos clients.
• Personnalisation complète du ticket : nom de la boutique, adresse, téléphone, message de remerciement et logo.

📦 GESTION DU STOCK EN TEMPS RÉEL :
• Suivi précis des quantités disponibles, alertes de stock faible et produits épuisés.
• Réapprovisionnement rapide et ajustements d'entrées/sorties de stock.
• Organisation par catégories avec pastilles de couleurs pour un repérage visuel immédiat.
• Import / Export de vos articles au format CSV / Excel.

👥 GESTION DES VENDEURS & SÉCURITÉ ADMIN :
• Mode Administrateur et Mode Caissier : restreignez l'accès aux paramètres sensibles et aux historiques financiers.
• Verrouillage de sécurité par code PIN pour les annulations de ventes et clôtures de caisse.
• Clôture de caisse et Rapport Z journalier pour contrôler vos espèces physiques et paiements mobiles.

☁️ SYNCHRONISATION MULTI-APPAREILS & CLOUD TEMPS RÉEL :
• Travaillez à plusieurs sur la même boutique : vos vendeurs et vous-même partagez les mêmes données en temps réel via le cloud Firebase.
• Fonctionnement 100% Hors-ligne : continuez à encaisser sans connexion internet, la synchronisation s'effectue automatiquement dès le retour du réseau.

📊 CRM CLIENTS & RELANCE MARKETING :
• Suivi des dettes et gestion des crédits clients avec historique détaillé.
• Relance client WhatsApp en un clic pour les impayés, les remerciements et les offres promotionnelles.
• Générateur de fiches produits pour réseaux sociaux et bordereaux de livraison avec calcul automatique.

Pourquoi choisir KAYO ?
✓ Aucune connexion obligatoire pour encaisser.
✓ Aucune publicité intrusive.
✓ Conçu pour les réalités du commerce moderne.
✓ Support client réactif et accompagnement dédié.

Téléchargez KAYO dès aujourd'hui et digitalisez votre commerce en toute simplicité !
```

### D. Catégorie & Tags
- **Catégorie principale :** Entreprise (Business) ou Finance.
- **Tags recommandés :** Point de vente, Caisse enregistreuse, Gestion de stock, Facturation, Commerce, POS.

---

## 2. POLITIQUE DE CONFIDENTIALITÉ (PRIVACY POLICY)

Google Play exige une politique de confidentialité accessible via une URL publique. Vous pouvez héberger le texte ci-dessous sur votre site, GitHub Pages ou votre projet Firebase.

### Texte de la Politique de Confidentialité :
```markdown
# Politique de Confidentialité — KAYO POS Mobile
Dernière mise à jour : 12 Octobre 2026

La présente politique de confidentialité décrit la façon dont KAYO (« nous », « notre » ou « l'Application ») collecte, utilise et protège les données de ses utilisateurs (« vous » ou « le Commerçant »).

### 1. Données collectées
KAYO collecte uniquement les informations nécessaires au bon fonctionnement de vos activités commerciales :
- **Informations de compte :** Adresse email et identifiant utilisateur pour l'authentification sécurisée (Firebase Authentication).
- **Données d'activité commerciale :** Catalogue de produits, prix, mouvements de stocks, historiques de ventes et dépenses enregistrés par vous.
- **Informations clients :** Noms et numéros de téléphone enregistrés volontairement par le commerçant dans son répertoire client pour l'émission de reçus et le suivi des crédits.
- **Photos des produits :** Stockées localement sur votre appareil pour l'affichage visuel des articles.

### 2. Utilisation des données
Vos données sont strictement utilisées pour :
- Fournir et synchroniser les services de caisse et de gestion des stocks entre vos appareils autorisés.
- Permettre l'impression thermique Bluetooth et l'envoi de reçus via WhatsApp.
- Assurer la sauvegarde sécurisée de votre comptabilité sur l'infrastructure Cloud Google Firebase.

### 3. Partage des données
**Nous ne vendons, ne louons et ne partageons aucune de vos données personnelles ou commerciales avec des tiers à des fins publicitaires ou de prospection.**
Les données sont hébergées de manière confidentielle et isolée sur l'infrastructure Google Cloud / Firebase (conforme aux normes internationales ISO 27001 et RGPD).

### 4. Permissions requises et justifications
- **Appareil photo (Camera) :** Utilisé exclusivement pour scanner les codes-barres des articles lors de la vente ou de l'inventaire. Aucune image n'est enregistrée sans votre action.
- **Bluetooth (Bluetooth Connect & Scan) :** Utilisé uniquement pour découvrir et se connecter aux imprimantes thermiques de reçus. Nous n'utilisons pas le Bluetooth pour la localisation.
- **Internet :** Nécessaire pour la synchronisation cloud en temps réel entre plusieurs caisses.

### 5. Sécurité et conservation des données
Toutes les communications entre l'application et nos serveurs sont protégées par le protocole de chiffrement TLS / HTTPS. Vous pouvez à tout moment exporter vos données ou demander la suppression définitive de votre compte et de l'ensemble de vos données associées.

### 6. Contact
Pour toute question relative à cette politique de confidentialité ou pour exercer vos droits, contactez-nous à :
- **Email :** support@sypos.pro (ou votre adresse email de support)
- **Téléphone / WhatsApp :** +225 07 58 24 55 30
```

---

## 3. QUESTIONNAIRE DE SÉCURITÉ DES DONNÉES (DATA SAFETY)

Lors de la configuration dans la Google Play Console, renseignez le formulaire **Sécurité des données** comme suit :

1. **L'application collecte-t-elle ou partage-t-elle des données utilisateur ?**
   $\rightarrow$ **Oui**.
2. **Toutes les données collectées par l'application sont-elles chiffrées en transit ?**
   $\rightarrow$ **Oui** (Toutes les requêtes passent par HTTPS / TLS avec Firebase).
3. **Fournissez-vous un moyen pour les utilisateurs de demander la suppression de leurs données ?**
   $\rightarrow$ **Oui** (Sur demande auprès du support).
4. **Types de données déclarées :**
   - **Informations personnelles :**
     - *Adresse e-mail* : Collectée pour l'authentification et la gestion du compte.
   - **Données financières :**
     - *Historique des transactions d'achat/vente* : Collecté pour la fonctionnalité de caisse et comptabilité de l'entreprise.
   - **Coordonnées (facultatif / si fichier client activé) :**
     - *Nom et Numéro de téléphone des clients* : Gérés par le commerçant pour l'envoi de reçus et la gestion des créances.
5. **Partage avec des tiers :**
   $\rightarrow$ **Non**, aucune donnée n'est partagée avec des réseaux publicitaires ou des courtiers de données.

---

## 4. FICHIER BUNDLE (AAB) GÉNÉRÉ & SIGNÉ

- **Emplacement du livrable de production :**  
  `app/build/outputs/bundle/release/app-release.aab`
- **Taille :** 25,4 Mo
- **Package ID :** `com.sypos.mobile`
- **Target SDK :** 34 (Android 14 — conforme aux exigences Google Play 2024-2026)
- **Min SDK :** 26 (Android 8.0 Oreo)
- **Statut de la signature :** Signé avec la clé RSA `sypos-release.jks` (Validité : 10 000 jours, expiration en 2054).
- **Vérification cryptographique :** Validée avec succès (`jarsigner -verify`).
