# 🔐 SYPOS Mobile — Portail Web de Licences

Plateforme Web d'administration et d'émission de clés de licences cryptographiques pour **SYPOS Mobile**.

---

## 🚀 1. Lancer la plateforme en local (Sur votre Mac)

Exécutez simplement :
```bash
python3 license-portal/server.py
```
Puis ouvrez votre navigateur sur : **`http://localhost:8080`**  
*(Mot de passe administrateur par défaut : `sypos2026`)*

---

## 🌐 2. Déployer la plateforme en ligne gratuitement (Accessible de partout)

Cette plateforme est une application web autonome (PWA / SPA) avec signature cryptographique RSA-2048 intégrée. Elle peut être hébergée **gratuitement en 1 clic** sur n'importe quel service d'hébergement web :

### Option A : Vercel (Recommandé)
1. Installez Vercel CLI : `npm i -g vercel` (ou glissez-déposez le dossier `license-portal` sur [vercel.com](https://vercel.com))
2. Dans le dossier `license-portal`, lancez :
   ```bash
   vercel
   ```
3. Votre portail est immédiatement disponible en ligne avec un lien HTTPS (ex: `https://sypos-licenses.vercel.app`).

### Option B : Netlify
1. Glissez-déposez simplement le dossier `license-portal` sur [app.netlify.com/drop](https://app.netlify.com/drop)
2. Votre plateforme est en ligne en 10 secondes !

---

## ✨ Fonctionnalités du Portail

- 🔐 **Sécurisation par Mot de Passe Admin** : Verrouillage d'accès à la plateforme.
- 📱 **Génération de Licences Instantanée** : Saisie du Device ID + Formule (Illimitée, 1 An, Essai 30j).
- 📷 **QR Code Haute Résolution** : Scannable directement par la caméra de l'application SYPOS Mobile.
- 💬 **Envoi WhatsApp en 1 Clic** : Ouvre WhatsApp avec le message d'activation prêt à être envoyé au client.
- 📜 **Registre & Historique** : Sauvegarde automatique de tous vos clients, avec recherche et export CSV.
