#!/usr/bin/env python3
"""
SYPOS Mobile - Serveur Local du Portail de Licences
==================================================
Lance un serveur HTTP léger pour accéder à la plateforme de licences depuis
votre ordinateur ou votre smartphone connecté au même réseau WiFi.
"""

import http.server
import socketserver
import os
import sys
import webbrowser

PORT = 8080
DIRECTORY = os.path.dirname(os.path.abspath(__file__))

class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def log_message(self, format, *args):
        # Clean logging
        sys.stderr.write(f"[SYPOS Portal] {args[0]} - {args[1]}\n")

def main():
    port = PORT
    if len(sys.argv) > 1 and sys.argv[1].isdigit():
        port = int(sys.argv[1])

    with socketserver.TCPServer(("", port), Handler) as httpd:
        url = f"http://localhost:{port}"
        print("\n" + "="*60)
        print("🚀 PLATEFORME DE LICENCES SYPOS MOBILE EN LIGNE !")
        print("="*60)
        print(f"👉 Accès local : {url}")
        print(f"👉 Mot de passe Administrateur par défaut : sypos2026")
        print("\nAppuyez sur Ctrl+C pour arrêter le serveur.")
        print("="*60 + "\n")
        
        try:
            webbrowser.open(url)
        except Exception:
            pass

        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nArrêt du serveur SYPOS.")

if __name__ == "__main__":
    main()
