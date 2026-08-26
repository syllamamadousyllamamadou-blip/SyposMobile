#!/usr/bin/env python3
"""
SYPOS Mobile - Générateur Officiel de Licences Cryptographiques
=============================================================
Usage :
  python3 tools/license_generator.py (Mode interactif)
  python3 tools/license_generator.py --dev-id SYPOS-DEV-XXXX-YYYY --shop "Boutique X" --plan LIFETIME
"""

import sys
import os
import json
import base64
import time
import subprocess
import argparse
from datetime import datetime, timedelta

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PRIVATE_KEY_PATH = os.path.join(SCRIPT_DIR, "sypos_private_key.pem")
PUBLIC_KEY_PATH = os.path.join(SCRIPT_DIR, "sypos_public_key.pem")

def b64url_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode('utf-8').rstrip('=')

def b64url_decode(s: str) -> bytes:
    padding = '=' * (-len(s) % 4)
    return base64.urlsafe_b64decode(s + padding)

def sign_payload(payload_bytes: bytes) -> str:
    if not os.path.exists(PRIVATE_KEY_PATH):
        raise FileNotFoundError(f"Clé privée introuvable: {PRIVATE_KEY_PATH}")
    
    proc = subprocess.Popen(
        ["openssl", "dgst", "-sha256", "-sign", PRIVATE_KEY_PATH],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE
    )
    sig_bytes, err = proc.communicate(input=payload_bytes)
    if proc.returncode != 0:
        raise RuntimeError(f"Erreur OpenSSL lors de la signature: {err.decode('utf-8')}")
    
    return b64url_encode(sig_bytes)

def generate_license(dev_id: str, shop_name: str, plan_type: str, days: int = 0) -> dict:
    dev_id_clean = dev_id.strip().upper()
    shop_clean = shop_name.strip() if shop_name else "Boutique SYPOS"
    now_ms = int(time.time() * 1000)
    
    if plan_type == "LIFETIME":
        exp_ms = 0
        plan_label = "Licence Définitive (Illimitée à Vie)"
    elif plan_type == "ANNUAL":
        exp_ms = now_ms + (365 * 24 * 3600 * 1000)
        plan_label = "Licence Annuelle (1 An)"
    elif plan_type == "TRIAL":
        d = days if days > 0 else 30
        exp_ms = now_ms + (d * 24 * 3600 * 1000)
        plan_label = f"Licence Essai ({d} Jours)"
    elif plan_type == "CUSTOM":
        exp_ms = now_ms + (days * 24 * 3600 * 1000)
        plan_label = f"Licence Temporaire ({days} Jours)"
    else:
        raise ValueError(f"Plan inconnu: {plan_type}")

    payload = {
        "devId": dev_id_clean,
        "shop": shop_clean,
        "plan": plan_type,
        "exp": exp_ms,
        "iat": now_ms,
        "v": 1
    }
    
    payload_json = json.dumps(payload, separators=(',', ':'), ensure_ascii=False)
    payload_bytes = payload_json.encode('utf-8')
    payload_b64 = b64url_encode(payload_bytes)
    sig_b64 = sign_payload(payload_bytes)
    
    token = f"SYP1.{payload_b64}.{sig_b64}"
    
    exp_str = "Illimitée (À Vie)" if exp_ms == 0 else datetime.fromtimestamp(exp_ms / 1000.0).strftime('%d/%m/%Y à %H:%M')
    
    return {
        "token": token,
        "payload": payload,
        "plan_label": plan_label,
        "expiry_str": exp_str,
        "deviceId": dev_id_clean,
        "shop": shop_clean
    }

def print_whatsapp_format(lic: dict):
    print("\n" + "="*60)
    print("📲 MESSAGE À COPIER / COLLER DIRECTEMENT SUR WHATSAPP :")
    print("="*60)
    msg = f"""✨ *ACTIVATION SYPOS MOBILE* ✨

Bonjour {lic['shop']},
Voici votre clé d'activation officielle pour SYPOS Mobile :

📱 *Appareil :* `{lic['deviceId']}`
🏷️ *Formule :* {lic['plan_label']}
⏳ *Validité :* {lic['expiry_str']}

🔑 *VOTRE CLÉ D'ACTIVATION :*
```{lic['token']}```

👉 *Pour activer :*
1. Ouvrez SYPOS Mobile
2. Cliquez sur *Activer ma licence*
3. Collez la clé ci-dessus ou scannez le QR Code reçu.
4. Cliquez sur *Valider*. Votre caisse est prête !

_Assistance & Support SYPOS Mobile_"""
    print(msg)
    print("="*60)

def main():
    parser = argparse.ArgumentParser(description="Générateur de Licence SYPOS Mobile")
    parser.add_argument("--dev-id", help="Device ID du client (ex: SYPOS-DEV-7A8B-49C2)")
    parser.add_argument("--shop", help="Nom de la boutique du client", default="Boutique SYPOS")
    parser.add_argument("--plan", choices=["LIFETIME", "ANNUAL", "TRIAL", "CUSTOM"], help="Type de licence")
    parser.add_argument("--days", type=int, default=30, help="Nombre de jours pour TRIAL ou CUSTOM")

    args = parser.parse_args()

    print("\n🔐 SYPOS MOBILE — GÉNÉRATEUR OFFICIEL DE LICENCES")
    print("───────────────────────────────────────────────────")

    dev_id = args.dev_id
    shop = args.shop
    plan = args.plan
    days = args.days

    if not dev_id:
        dev_id = input("👉 Entrez le Device ID du client (ex: SYPOS-DEV-7A8B-49C2) : ").strip()
        while not dev_id:
            dev_id = input("❌ Le Device ID est obligatoire : ").strip()

    if not args.shop or args.shop == "Boutique SYPOS":
        s = input(f"👉 Nom de la boutique [{shop}] : ").strip()
        if s:
            shop = s

    if not plan:
        print("\nChoisissez le type de licence :")
        print("  [1] Illimitée / À Vie (LIFETIME)")
        print("  [2] Annuelle - 1 An (ANNUAL)")
        print("  [3] Essai 30 Jours (TRIAL)")
        print("  [4] Personnalisée (CUSTOM - spécifier jours)")
        choice = input("Votre choix (1/2/3/4) [1] : ").strip()
        if choice == "2":
            plan = "ANNUAL"
        elif choice == "3":
            plan = "TRIAL"
            days = 30
        elif choice == "4":
            plan = "CUSTOM"
            days_str = input("Nombre de jours de validité : ").strip()
            days = int(days_str) if days_str.isdigit() else 30
        else:
            plan = "LIFETIME"

    try:
        lic = generate_license(dev_id, shop, plan, days)
        print("\n✅ LICENCE GÉNÉRÉE AVEC SUCCÈS !")
        print(f"• Device ID   : {lic['deviceId']}")
        print(f"• Boutique    : {lic['shop']}")
        print(f"• Formule     : {lic['plan_label']}")
        print(f"• Expiration  : {lic['expiry_str']}")
        print(f"\n🔑 Clé Token :\n{lic['token']}\n")
        print_whatsapp_format(lic)
    except Exception as e:
        print(f"\n❌ Erreur : {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
