#!/bin/bash

########################
#  Farben
########################

RESET="\e[0m"
BOLD="\e[1m"
CYAN="\e[36m"
GREEN="\e[32m"
YELLOW="\e[33m"
GRAY="\e[90m"

########################
#  Gradient Logo
########################

LOGO1="\e[38;5;51mâ–Œâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–${RESET}"
LOGO2="\e[38;5;45mâ–Œâ–ˆ    â–ˆâ–${RESET}"
LOGO3="\e[38;5;39mâ–Œâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–${RESET}"
LOGO4="\e[38;5;33mâ–Œâ–ˆ    â–ˆâ–${RESET}"
LOGO5="\e[38;5;27mâ–Œâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–${RESET}"

########################
#  Header
########################

clear
echo -e "${CYAN}${BOLD}------------------------------------------"
echo "               E D U X E L"
echo -e "------------------------------------------${RESET}"
echo ""
echo -e "${BOLD}Willkommen zum Eduxel Installer.${RESET}"
echo ""
echo "MariaDB Setup:"
echo "  1) Automatisch installieren & einrichten"
echo "  2) Manuell (du trÃ¤gst DB spÃ¤ter selbst ein)"
echo ""
read -p "> Auswahl (1/2): " OPTION

########################
#  Variablen
########################

DB_NAME="eduxel"
DB_USER="eduxel"
DB_PASS=$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9!@#$%^&*()_+=' | head -c 16)

CONFIG_DIR="/etc/eduxel"
APP_DIR="/opt/eduxel"
IP=$(hostname -I | awk '{print $1}')
APP_PORT=45821

mkdir -p "$CONFIG_DIR"
mkdir -p "$APP_DIR"

apt-get update -qq >/dev/null 2>&1
apt-get install -qq jq python3 python3-pip >/dev/null 2>&1

########################
#  AUTO MODE
########################

if [ "$OPTION" = "1" ]; then
    echo -e "\n${CYAN}âžœ MariaDB wird eingerichtet...${RESET}"

    if ! command -v mariadb >/dev/null 2>&1 && ! command -v mysql >/dev/null 2>&1; then
        apt-get install -qq mariadb-server >/dev/null 2>&1
        systemctl enable mariadb >/dev/null 2>&1
        systemctl start mariadb  >/dev/null 2>&1
    fi

    mysql -u root <<EOF
CREATE DATABASE IF NOT EXISTS ${DB_NAME};
CREATE USER IF NOT EXISTS '${DB_USER}'@'%' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'%';
FLUSH PRIVILEGES;
EOF

    AUTO=true

    cat > "$CONFIG_DIR/config.json" <<EOF
{
    "mode": "auto",
    "database": {
        "host": "$IP",
        "port": 3306,
        "user": "$DB_USER",
        "password": "$DB_PASS",
        "database": "$DB_NAME"
    },
    "app": {
        "port": $APP_PORT,
        "secret": ""
    }
}
EOF

########################
#  MANUAL MODE
########################

else
    AUTO=false

    cat > "$CONFIG_DIR/config.json" <<EOF
{
    "mode": "manual",
    "database": {
        "host": "HIER_EINTRAGEN",
        "port": 3306,
        "user": "HIER_EINTRAGEN",
        "password": "HIER_EINTRAGEN",
        "database": "HIER_EINTRAGEN"
    },
    "app": {
        "port": $APP_PORT,
        "secret": ""
    }
}
EOF
fi

########################
#  Secret IMMER erzeugen
########################

SECRET=$(openssl rand -hex 32)
tmpfile="$CONFIG_DIR/config.tmp"

jq --arg sec "$SECRET" '.app.secret = $sec' "$CONFIG_DIR/config.json" > "$tmpfile"
mv "$tmpfile" "$CONFIG_DIR/config.json"

########################
#  Python Programm
########################

cat > "$APP_DIR/eduxel.py" << 'EOF'
import json, socket

CONFIG = "/etc/eduxel/config.json"

def load():
    with open(CONFIG, "r") as f:
        return json.load(f)

def start(cfg):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.bind(("0.0.0.0", cfg["app"]["port"]))
    s.listen(5)

    sec = cfg["app"]["secret"]
    print(f"[Eduxel] API lÃ¤uft auf Port {cfg['app']['port']}")

    while True:
        conn, _ = s.accept()
        data = conn.recv(1024).decode().strip()

        if data != sec:
            conn.send(b"INVALID")
        else:
            db = cfg["database"]
            conn.send(
                f"OK;HOST={db['host']};PORT={db['port']};USER={db['user']};PASS={db['password']};DB={db['database']}".encode()
            )

        conn.close()

if __name__ == "__main__":
    cfg = load()
    start(cfg)
EOF

chmod +x "$APP_DIR/eduxel.py"

########################
#  systemd Service
########################

cat > /etc/systemd/system/eduxel.service <<EOF
[Unit]
Description=Eduxel Credential Server
After=network.target

[Service]
ExecStart=/usr/bin/python3 $APP_DIR/eduxel.py
Restart=always

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable eduxel >/dev/null 2>&1
systemctl start eduxel  >/dev/null 2>&1

########################
#  eduxel CLI Tool
########################

cat > /usr/bin/eduxel <<EOF
#!/bin/bash

RESET="\e[0m"
BOLD="\e[1m"

LOGO1="\e[38;5;51mâ–Œâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–${RESET}"
LOGO2="\e[38;5;45mâ–Œâ–ˆ    â–ˆâ–${RESET}"
LOGO3="\e[38;5;39mâ–Œâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–${RESET}"
LOGO4="\e[38;5;33mâ–Œâ–ˆ    â–ˆâ–${RESET}"
LOGO5="\e[38;5;27mâ–Œâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–${RESET}"

SERVICE="eduxel"
CFG="/etc/eduxel/config.json"

if [[ "\$1" == "info" || "\$1" == "-info" ]]; then
mode=\$(jq -r '.mode' "\$CFG")
port=\$(jq -r '.app.port' "\$CFG")
secret=\$(jq -r '.app.secret' "\$CFG")
host=\$(jq -r '.database.host' "\$CFG")
db=\$(jq -r '.database.database' "\$CFG")

printf "%b   ${BOLD}Eduxel Framework${RESET}\n" "\$LOGO1"
printf "%b   Engine: python-service\n" "\$LOGO2"
printf "%b   Version: v1.0.0\n" "\$LOGO3"
printf "%b   Folder: /opt/eduxel\n" "\$LOGO4"
printf "%b   Config: /etc/eduxel/config.json\n" "\$LOGO5"
echo  "          Service: \$SERVICE.service"
echo  "          Mode: \$mode"
echo  "          API-Port: \$port"
echo  "          Secret: \$secret"

if [[ "\$mode" == "auto" ]]; then
    echo "          DB: mysql://\$host/\$db"
fi

exit 0
fi

case "\$1" in
  start)
    systemctl start "\$SERVICE"
    echo "Eduxel Service gestartet."
    exit 0
    ;;
  stop)
    systemctl stop "\$SERVICE"
    echo "Eduxel Service gestoppt."
    exit 0
    ;;
  restart)
    systemctl restart "\$SERVICE"
    echo "Eduxel Service neu gestartet."
    exit 0
    ;;
  enable)
    systemctl enable "\$SERVICE"
    echo "Autostart fÃ¼r Eduxel aktiviert."
    exit 0
    ;;
  disable)
    systemctl disable "\$SERVICE"
    echo "Autostart fÃ¼r Eduxel deaktiviert."
    exit 0
    ;;
  status)
    systemctl status "\$SERVICE" --no-pager
    exit 0
    ;;
  uninstall|remove)
    echo "Dieses Kommando deinstalliert Eduxel komplett (Service, Dateien, CLI)."
    read -p "Fortfahren? (y/N): " confirm
    if [[ "\$confirm" == "y" || "\$confirm" == "Y" ]]; then
      systemctl stop "\$SERVICE" 2>/dev/null
      systemctl disable "\$SERVICE" 2>/dev/null
      rm -f /etc/systemd/system/eduxel.service
      systemctl daemon-reload
      rm -rf /opt/eduxel /etc/eduxel
      rm -f /usr/bin/eduxel
      echo "Eduxel wurde vollstÃ¤ndig entfernt."
    else
      echo "Abgebrochen."
    fi
    exit 0
    ;;
esac

echo "Eduxel CLI"
echo "Usage: eduxel info|start|stop|restart|enable|disable|status|uninstall"
EOF

chmod +x /usr/bin/eduxel

########################
#  Finale Ausgabe
########################

echo ""
echo -e "${GREEN}${BOLD}------------------------------------------"
echo "        âœ“ Eduxel erfolgreich installiert!"
echo -e "------------------------------------------${RESET}"
echo ""

printf "%b   ${BOLD}Eduxel installiert${RESET}\n" "$LOGO1"
printf "%b   Server-IP:   %s\n" "$LOGO2" "$IP"
printf "%b   API-Port:    %s\n" "$LOGO3" "$APP_PORT"
printf "%b   Secret:      %s\n" "$LOGO4" "$SECRET"
if [ "$AUTO" = true ]; then
    printf "%b   Mode:        auto (DB automatisch erstellt)\n" "$LOGO5"
else
    printf "%b   Mode:        manual (DB manuell eintragen)\n" "$LOGO5"
fi

echo ""

if [ "$AUTO" = true ]; then
    echo -e "${GREEN}Automatische DB-Einrichtung:${RESET}"
    echo "DB-Host:       $IP"
    echo "DB-Port:       3306"
    echo "DB-User:       $DB_USER"
    echo "DB-Name:       $DB_NAME"
    echo "DB-Passwort:   $DB_PASS"
else
    echo -e "${YELLOW}MANUELLER MODUS:${RESET}"
    echo "Trage deine DB-Daten ein in:"
    echo "  $CONFIG_DIR/config.json"
fi

echo ""
echo -e "${CYAN}Nutze '${BOLD}eduxel info${RESET}${CYAN}' fÃ¼r Status & Infos.${RESET}"
echo ""
