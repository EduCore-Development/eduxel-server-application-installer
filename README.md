# 🚀 Eduxel Server Application – Installer

Der **Eduxel Installer** ist ein vollautomatisierter Installer für die **Eduxel Server Application**.  
Er richtet den kompletten Server-Stack ein – von Java über Datenbank bis Webserver – und startet die Anwendung produktionsbereit.

Ziel: **Ein Befehl. Alles läuft.**  
Kein manuelles Setup, kein Copy-Paste-Chaos, kein Gefrickel.

---

## ✨ Features

• Automatische Installation von **Java 25 (Eclipse Temurin)**  
• Download der **aktuellen eduxel.jar aus GitHub Releases**  
• Einrichtung eines **systemd-Services mit Autostart**  
• Eigene **CLI (`eduxel`)**  
• **MariaDB Setup** (automatisch oder manuell)  
• **Apache & Caddy** inkl. Reverse Proxy und HTTPS  
• **DNS-Check** für Domains (A-Record)  
• **Website-Deployment** (GitHub Repo → npm install → npm run build → Deploy)  
• Update-fähig über GitHub Releases  
• Saubere Logs und klare Statusmeldungen  

---

## 📦 Voraussetzungen

• Linux Server (empfohlen: Ubuntu oder Debian)  
• Root-Zugriff (sudo)  
• Internetverbindung  
• Optional: eine Domain für Web- und HTTPS-Setup  

---

## ⚡ Quick Install

Der empfohlene Weg ist die Installation über den offiziellen Proxy-Endpunkt:

curl -fsSL https://edu-core.dev/i | bash

Der Installer lädt automatisch die neueste Version aus den GitHub Releases.

---

## ⚙️ Installation mit Parametern

Parameter können direkt an den Installer übergeben werden.  
Alles nach dem zweiten `--` wird an das Java-CLI weitergereicht.

Beispiel mit Domain und Website-Repository:

curl -fsSL https://edu-core.dev/i | bash -s -- -- --domain panel.example.com --repo https://github.com/OWNER/website.git --branch main

---

## 🔧 Installer-Optionen

Diese Optionen betreffen den Bootstrap-Installer selbst:

• --release-repo OWNER/REPO  
  GitHub Repository, aus dem das Release geladen wird  

• --tag latest  
  Release-Tag (z. B. latest oder v1.0.0)  

• --jar-asset eduxel.jar  
  Name des JAR-Assets im Release  

• --user eduxel  
  Linux-User, unter dem der Service läuft  

• --no-java-install  
  Überspringt die Java-Installation  

---

## 🧠 Optionen für „eduxel install“

Diese Optionen werden direkt an die Java-Anwendung weitergereicht:

• --domain DOMAIN  
  Domain für Web- und HTTPS-Setup  

• --repo URL  
  GitHub Repository für die Website  

• --branch NAME  
  Branch für das Website-Deployment  

• --skip-web  
  Überspringt das Webserver-Setup  

• --skip-mariadb  
  Überspringt das MariaDB-Setup  

• --allow-no-dns  
  Ignoriert DNS-Mismatch (nur Warnung)  

---

## ▶️ Service & Verwaltung

Nach der Installation läuft Eduxel als systemd-Service.

Wichtige Befehle:

• systemctl status eduxel  
• systemctl restart eduxel  
• journalctl -u eduxel -f  

CLI verwenden:

• eduxel info  
• eduxel install  
• eduxel reset  

---

## 📂 Wichtige Pfade

• Anwendung: /opt/eduxel/eduxel.jar  
• Konfiguration: /etc/eduxel/  
• Service: /etc/systemd/system/eduxel.service  
• CLI: /usr/local/bin/eduxel  

---

## 🔄 Updates

Updates erfolgen über GitHub Releases.  
Ein erneuter Installationslauf lädt automatisch die neueste Version und startet den Service neu.

---

## 🛡️ Sicherheitshinweis

Der Installer benötigt Root-Rechte, da Systemdienste, Benutzer und Server-Software eingerichtet werden.  
Bitte nur auf vertrauenswürdigen Systemen ausführen.

---

## ❤️ EduCore

Eduxel ist ein Projekt der **EduCore Development**.  
Feedback, Issues und Pull Requests sind willkommen.

GitHub: https://github.com/EduCore-Development
