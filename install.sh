#!/bin/bash
set -euo pipefail

export LANG=C.UTF-8
export LC_ALL=C.UTF-8

RESET="\e[0m"
BOLD="\e[1m"
CYAN="\e[36m"
GREEN="\e[32m"
YELLOW="\e[33m"
GRAY="\e[90m"

CONFIG_DIR="/etc/eduxel"
APP_DIR="/opt/eduxel"
CFG="$CONFIG_DIR/config.json"
APP_PORT=45821

DB_NAME="eduxel"
DB_USER="eduxel"

mkdir -p "$CONFIG_DIR" "$APP_DIR"

if [[ "$EUID" -ne 0 ]]; then
  echo -e "${YELLOW}Bitte als root ausführen (oder mit sudo).${RESET}"
  exit 1
fi

clear
echo -e "${CYAN}${BOLD}------------------------------------------"
echo "               E D U X E L"
echo -e "------------------------------------------${RESET}"
echo ""
echo -e "${BOLD}Willkommen zum Eduxel Installer.${RESET}"
echo ""
echo "MariaDB Setup:"
echo "  1) Automatisch installieren & remote DB einrichten"
echo "  2) Manuell (du trägst DB später selbst ein)"
echo ""
read -r -p "> Auswahl (1/2): " OPTION

apt-get update -qq >/dev/null 2>&1 || true
apt-get install -qq jq python3 python3-pip openssl curl >/dev/null 2>&1 || true

PUB_IP="$(curl -fsS https://api.ipify.org 2>/dev/null || true)"
if [[ -z "${PUB_IP:-}" ]]; then
  PUB_IP="$(hostname -I | awk '{print $1}' || true)"
fi
if [[ -z "${PUB_IP:-}" ]]; then
  PUB_IP="127.0.0.1"
fi

rand_db_pass() {
  openssl rand -base64 48 | tr -dc 'A-Za-z0-9!@#$%^&*()_+=-' | head -c 20
}

db_exec_setup() {
  local cmd=""
  if sudo mariadb -e "SELECT 1;" >/dev/null 2>&1; then
    cmd="sudo mariadb"
  elif mysql -u root -e "SELECT 1;" >/dev/null 2>&1; then
    cmd="mysql -u root"
  else
    echo -e "${YELLOW}MariaDB root braucht ein Passwort.${RESET}"
    read -r -s -p "MariaDB root Passwort: " MYSQL_ROOT_PASS
    echo ""
    if mysql -u root -p"$MYSQL_ROOT_PASS" -e "SELECT 1;" >/dev/null 2>&1; then
      cmd="mysql -u root -p$MYSQL_ROOT_PASS"
    else
      echo -e "${YELLOW}DB Login fehlgeschlagen. Abbruch.${RESET}"
      exit 1
    fi
  fi
  echo "$cmd"
}

ensure_mariadb_remote() {
  if ! command -v mariadb >/dev/null 2>&1 && ! command -v mysql >/dev/null 2>&1; then
    apt-get install -qq mariadb-server >/dev/null 2>&1
  fi
  systemctl enable mariadb >/dev/null 2>&1 || true
  systemctl start mariadb >/dev/null 2>&1 || true

  local cnf=""
  if [[ -f /etc/mysql/mariadb.conf.d/50-server.cnf ]]; then
    cnf="/etc/mysql/mariadb.conf.d/50-server.cnf"
  elif [[ -f /etc/mysql/mysql.conf.d/mysqld.cnf ]]; then
    cnf="/etc/mysql/mysql.conf.d/mysqld.cnf"
  fi

  if [[ -n "$cnf" ]]; then
    if grep -qE '^[[:space:]]*bind-address' "$cnf"; then
      sed -i 's/^[[:space:]]*bind-address.*/bind-address = 0.0.0.0/' "$cnf"
    else
      printf "\n[mysqld]\nbind-address = 0.0.0.0\n" >> "$cnf"
    fi
    systemctl restart mariadb >/dev/null 2>&1 || true
  fi
}

SECRET="$(openssl rand -hex 32)"

AUTO=false
DB_PASS=""

if [[ "$OPTION" == "1" ]]; then
  AUTO=true
  DB_PASS="$(rand_db_pass)"

  echo -e "\n${CYAN}➜ MariaDB wird eingerichtet (remote)...${RESET}"
  ensure_mariadb_remote
  MYSQL_CMD="$(db_exec_setup)"

  $MYSQL_CMD <<EOF
CREATE DATABASE IF NOT EXISTS ${DB_NAME};
CREATE USER IF NOT EXISTS '${DB_USER}'@'%' IDENTIFIED BY '${DB_PASS}';
ALTER USER '${DB_USER}'@'%' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'%';
FLUSH PRIVILEGES;
EOF

  cat > "$CFG" <<EOF
{
  "mode": "auto",
  "database": {
    "host": "$PUB_IP",
    "port": 3306,
    "user": "$DB_USER",
    "password": "$DB_PASS",
    "database": "$DB_NAME"
  },
  "app": {
    "port": $APP_PORT,
    "secret": "$SECRET"
  }
}
EOF
else
  cat > "$CFG" <<EOF
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
    "secret": "$SECRET"
  }
}
EOF
fi

cat > "$APP_DIR/eduxel.py" << 'EOF'
import json, socket

CONFIG = "/etc/eduxel/config.json"

def load():
    with open(CONFIG, "r", encoding="utf-8") as f:
        return json.load(f)

def start(cfg):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("0.0.0.0", int(cfg["app"]["port"])))
    s.listen(20)

    sec = cfg["app"]["secret"]
    print(f"[Eduxel] API läuft auf Port {cfg['app']['port']}")

    while True:
        conn, _ = s.accept()
        try:
            data = conn.recv(1024).decode(errors="ignore").strip()
            if data != sec:
                conn.send(b"INVALID")
            else:
                db = cfg["database"]
                conn.send(
                    f"OK;HOST={db['host']};PORT={db['port']};USER={db['user']};PASS={db['password']};DB={db['database']}".encode()
                )
        finally:
            conn.close()

if __name__ == "__main__":
    cfg = load()
    start(cfg)
EOF

chmod +x "$APP_DIR/eduxel.py"

cat > /etc/systemd/system/eduxel.service <<EOF
[Unit]
Description=Eduxel Credential Server
After=network.target

[Service]
ExecStart=/usr/bin/python3 $APP_DIR/eduxel.py
Restart=always
RestartSec=2

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable eduxel >/dev/null 2>&1 || true
systemctl start eduxel >/dev/null 2>&1 || true

cat > /usr/bin/eduxel <<'EOF'
#!/bin/bash
set -euo pipefail

SERVICE="eduxel"
CFG="/etc/eduxel/config.json"
APP_DIR="/opt/eduxel"
CONFIG_DIR="/etc/eduxel"
SERVICE_FILE="/etc/systemd/system/eduxel.service"
BIN="/usr/bin/eduxel"

die_no_root() {
  if [[ "${EUID:-999}" -ne 0 ]]; then
    echo "Bitte als root ausführen (oder mit sudo)."
    exit 1
  fi
}

usage() {
  echo "Eduxel CLI"
  echo "Usage:"
  echo "  eduxel info"
  echo "  eduxel start|stop|restart|status"
  echo "  eduxel enable|disable"
  echo "  eduxel reset"
  echo "  eduxel uninstall"
}

cmd_mysql() {
  if sudo mariadb -e "SELECT 1;" >/dev/null 2>&1; then
    echo "sudo mariadb"
    return
  fi
  if mysql -u root -e "SELECT 1;" >/dev/null 2>&1; then
    echo "mysql -u root"
    return
  fi
  echo ""
}

if [[ "${1:-}" == "info" || "${1:-}" == "-info" ]]; then
  if [[ ! -f "$CFG" ]]; then
    echo "config.json nicht gefunden unter $CFG"
    exit 1
  fi
  jq -r '
    "Mode: \(.mode)\nAPI-Port: \(.app.port)\nSecret: \(.app.secret)\nDB: mysql://\(.database.user)@\(.database.host):\(.database.port)/\(.database.database)"
  ' "$CFG"
  exit 0
fi

case "${1:-}" in
  start)
    die_no_root
    systemctl start "$SERVICE"
    echo "OK"
    ;;
  stop)
    die_no_root
    systemctl stop "$SERVICE"
    echo "OK"
    ;;
  restart)
    die_no_root
    systemctl restart "$SERVICE"
    echo "OK"
    ;;
  status)
    systemctl status "$SERVICE" --no-pager
    ;;
  enable)
    die_no_root
    systemctl enable "$SERVICE" >/dev/null 2>&1
    echo "OK"
    ;;
  disable)
    die_no_root
    systemctl disable "$SERVICE" >/dev/null 2>&1
    echo "OK"
    ;;
  uninstall)
    die_no_root
    echo "UNINSTALL: Entfernt Service, Config, App & CLI."
    read -r -p "Wirklich deinstallieren? (y/N): " confirm
    if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
      echo "Abgebrochen."
      exit 0
    fi
    systemctl stop "$SERVICE" >/dev/null 2>&1 || true
    systemctl disable "$SERVICE" >/dev/null 2>&1 || true
    rm -f "$SERVICE_FILE"
    systemctl daemon-reload >/dev/null 2>&1 || true
    rm -rf "$APP_DIR" "$CONFIG_DIR"
    rm -f "$BIN"
    echo "OK"
    ;;
  reset)
    die_no_root
    if [[ ! -f "$CFG" ]]; then
      echo "config.json nicht gefunden unter $CFG"
      exit 1
    fi
    id="EDUXEL-RESET-$(date +%s)"
    echo "Bestätigungs-ID: $id"
    read -r -p "Tippe die ID exakt ein um fortzufahren: " typed
    if [[ "$typed" != "$id" ]]; then
      echo "Falsche ID. Abbruch."
      exit 1
    fi

    mode="$(jq -r '.mode' "$CFG")"
    new_secret="$(openssl rand -hex 32)"

    if [[ "$mode" == "auto" ]]; then
      db_name="$(jq -r '.database.database' "$CFG")"
      db_user="$(jq -r '.database.user' "$CFG")"
      new_pass="$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9!@#$%^&*()_+=-' | head -c 20)"

      mysqlcmd="$(cmd_mysql)"
      if [[ -z "$mysqlcmd" ]]; then
        echo "Konnte MariaDB root nicht automatisch nutzen. Reset abgebrochen."
        exit 1
      fi

      $mysqlcmd <<EOF
ALTER USER '${db_user}'@'%' IDENTIFIED BY '${new_pass}';
GRANT ALL PRIVILEGES ON ${db_name}.* TO '${db_user}'@'%';
FLUSH PRIVILEGES;
EOF

      tmp="${CFG}.tmp"
      jq --arg sec "$new_secret" --arg pass "$new_pass" '
        .app.secret=$sec | .database.password=$pass
      ' "$CFG" > "$tmp" && mv "$tmp" "$CFG"

      systemctl restart "$SERVICE" >/dev/null 2>&1 || true
      echo "OK"
      echo "Neues Secret: $new_secret"
      echo "Neues DB-Passwort: $new_pass"
      exit 0
    fi

    tmp="${CFG}.tmp"
    jq --arg sec "$new_secret" '.app.secret=$sec' "$CFG" > "$tmp" && mv "$tmp" "$CFG"
    systemctl restart "$SERVICE" >/dev/null 2>&1 || true
    echo "OK"
    echo "Neues Secret: $new_secret"
    ;;
  ""|-h|--help|help)
    usage
    ;;
  *)
    usage
    exit 1
    ;;
esac
EOF

chmod +x /usr/bin/eduxel

echo ""
echo -e "${GREEN}${BOLD}------------------------------------------"
echo "        ✓ Eduxel erfolgreich installiert!"
echo -e "------------------------------------------${RESET}"
echo ""
echo -e "${BOLD}Server-IP:   ${RESET}$PUB_IP"
echo -e "${BOLD}API-Port:    ${RESET}$APP_PORT"
echo -e "${BOLD}Secret:      ${RESET}$SECRET"
echo -e "${BOLD}Mode:        ${RESET}$( [[ "$AUTO" == "true" ]] && echo "auto (remote DB)" || echo "manual" )"
echo ""

if [[ "$AUTO" == "true" ]]; then
  echo -e "${GREEN}Remote DB Daten:${RESET}"
  echo "DB-Host:       $PUB_IP"
  echo "DB-Port:       3306"
  echo "DB-User:       $DB_USER"
  echo "DB-Name:       $DB_NAME"
  echo "DB-Passwort:   $DB_PASS"
  echo ""
  echo -e "${YELLOW}Wichtig:${RESET} Firewall/Provider muss Port 3306 erlauben, sonst kommt nie eine Verbindung."
fi

echo ""
echo -e "${CYAN}Nutze '${BOLD}eduxel info${RESET}${CYAN}' für Status & Infos.${RESET}"
echo ""
