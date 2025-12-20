#!/usr/bin/env bash
set -euo pipefail

export LANG=C.UTF-8
export LC_ALL=C.UTF-8

CONFIG_DIR="/etc/eduxel"
APP_DIR="/opt/eduxel"
CFG="$CONFIG_DIR/config.json"
APP_PORT="${APP_PORT:-45821}"
SERVICE_NAME="eduxel"
BIN="/usr/local/bin/eduxel"

DB_NAME="eduxel"
DB_USER="eduxel"

need_tty() {
  if [ ! -t 0 ] && [ -e /dev/tty ]; then
    exec </dev/tty >/dev/tty 2>&1 || true
  fi
}

is_root() { [ "${EUID:-999}" -eq 0 ]; }

die() { printf "%s\n" "$*" >&2; exit 1; }

pm_detect() {
  if command -v apt-get >/dev/null 2>&1; then echo apt; return; fi
  if command -v dnf >/dev/null 2>&1; then echo dnf; return; fi
  if command -v yum >/dev/null 2>&1; then echo yum; return; fi
  if command -v pacman >/dev/null 2>&1; then echo pacman; return; fi
  if command -v zypper >/dev/null 2>&1; then echo zypper; return; fi
  if command -v apk >/dev/null 2>&1; then echo apk; return; fi
  echo unknown
}

pm_install() {
  local pm="$1"; shift
  local pkgs=("$@")
  case "$pm" in
    apt)
      apt-get update -y >/dev/null 2>&1 || true
      DEBIAN_FRONTEND=noninteractive apt-get install -y "${pkgs[@]}" >/dev/null 2>&1
      ;;
    dnf)
      dnf install -y "${pkgs[@]}" >/dev/null 2>&1
      ;;
    yum)
      yum install -y "${pkgs[@]}" >/dev/null 2>&1
      ;;
    pacman)
      pacman -Sy --noconfirm "${pkgs[@]}" >/dev/null 2>&1
      ;;
    zypper)
      zypper --non-interactive in "${pkgs[@]}" >/dev/null 2>&1
      ;;
    apk)
      apk add --no-cache "${pkgs[@]}" >/dev/null 2>&1
      ;;
    *)
      die "Kein unterstützter Package Manager gefunden."
      ;;
  esac
}

svc_has_systemd() { command -v systemctl >/dev/null 2>&1 && [ -d /run/systemd/system ]; }

svc_enable_start() {
  local name="$1"
  if svc_has_systemd; then
    systemctl daemon-reload >/dev/null 2>&1 || true
    systemctl enable "$name" >/dev/null 2>&1 || true
    systemctl restart "$name" >/dev/null 2>&1 || systemctl start "$name" >/dev/null 2>&1 || true
  else
    nohup /usr/bin/python3 "$APP_DIR/eduxel.py" >/var/log/eduxel.log 2>&1 &
    echo $! > /var/run/eduxel.pid
  fi
}

svc_stop() {
  if svc_has_systemd; then
    systemctl stop "$SERVICE_NAME" >/dev/null 2>&1 || true
  else
    if [ -f /var/run/eduxel.pid ]; then
      kill "$(cat /var/run/eduxel.pid)" >/dev/null 2>&1 || true
      rm -f /var/run/eduxel.pid
    fi
  fi
}

get_public_ip() {
  local ip=""
  ip="$(curl -fsS https://api.ipify.org 2>/dev/null || true)"
  if [ -z "${ip:-}" ]; then
    ip="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
  fi
  if [ -z "${ip:-}" ]; then
    ip="127.0.0.1"
  fi
  printf "%s" "$ip"
}

rand_pass() {
  openssl rand -base64 48 | tr -dc 'A-Za-z0-9!@#$%^&*()_+=-' | head -c 20
}

mysql_cmd_detect() {
  if command -v mariadb >/dev/null 2>&1 && mariadb -e "SELECT 1;" >/dev/null 2>&1; then
    echo "mariadb"
    return
  fi
  if command -v mysql >/dev/null 2>&1 && mysql -u root -e "SELECT 1;" >/dev/null 2>&1; then
    echo "mysql -u root"
    return
  fi
  echo ""
}

mysql_cmd_prompt() {
  local cmd=""
  cmd="$(mysql_cmd_detect)"
  if [ -n "$cmd" ]; then
    echo "$cmd"
    return
  fi
  need_tty
  printf "MariaDB root Passwort: " >&2
  stty -echo </dev/tty 2>/dev/null || true
  IFS= read -r MYSQL_ROOT_PASS </dev/tty || true
  stty echo </dev/tty 2>/dev/null || true
  printf "\n" >&2
  if mysql -u root -p"$MYSQL_ROOT_PASS" -e "SELECT 1;" >/dev/null 2>&1; then
    echo "mysql -u root -p$MYSQL_ROOT_PASS"
    return
  fi
  die "DB Login fehlgeschlagen (root)."
}

mariadb_server_pkgs() {
  local pm="$1"
  case "$pm" in
    apt) echo "mariadb-server mariadb-client" ;;
    dnf|yum) echo "mariadb-server mariadb" ;;
    pacman) echo "mariadb" ;;
    zypper) echo "mariadb mariadb-client" ;;
    apk) echo "mariadb mariadb-client" ;;
    *) echo "" ;;
  esac
}

mariadb_service_name() {
  if svc_has_systemd; then
    if systemctl list-unit-files 2>/dev/null | grep -q '^mariadb\.service'; then echo mariadb; return; fi
    if systemctl list-unit-files 2>/dev/null | grep -q '^mysql\.service'; then echo mysql; return; fi
  fi
  echo mariadb
}

mariadb_enable_remote_bind() {
  local cnf=""
  for f in \
    /etc/mysql/mariadb.conf.d/50-server.cnf \
    /etc/mysql/mysql.conf.d/mysqld.cnf \
    /etc/my.cnf \
    /etc/my.cnf.d/mariadb-server.cnf \
    /etc/my.cnf.d/server.cnf \
    /etc/my.cnf.d/mysqld.cnf
  do
    if [ -f "$f" ]; then cnf="$f"; break; fi
  done

  if [ -n "$cnf" ]; then
    if grep -qE '^[[:space:]]*bind-address' "$cnf"; then
      sed -i 's/^[[:space:]]*bind-address.*/bind-address = 0.0.0.0/' "$cnf" || true
    else
      printf "\n[mysqld]\nbind-address = 0.0.0.0\n" >> "$cnf"
    fi
  fi

  if svc_has_systemd; then
    local sname
    sname="$(mariadb_service_name)"
    systemctl enable "$sname" >/dev/null 2>&1 || true
    systemctl restart "$sname" >/dev/null 2>&1 || systemctl start "$sname" >/dev/null 2>&1 || true
  fi
}

write_config() {
  local mode="$1" host="$2" port="$3" user="$4" pass="$5" db="$6" app_port="$7" secret="$8"
  mkdir -p "$CONFIG_DIR"
  cat > "$CFG" <<JSON
{
  "mode": "$mode",
  "database": {
    "host": "$host",
    "port": $port,
    "user": "$user",
    "password": "$pass",
    "database": "$db"
  },
  "app": {
    "port": $app_port,
    "secret": "$secret"
  }
}
JSON
}

write_app() {
  mkdir -p "$APP_DIR"
  cat > "$APP_DIR/eduxel.py" <<'PY'
import json, socket

CONFIG = "/etc/eduxel/config.json"

def load():
    with open(CONFIG, "r", encoding="utf-8") as f:
        return json.load(f)

def start(cfg):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("0.0.0.0", int(cfg["app"]["port"])))
    s.listen(50)
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
    start(load())
PY
  chmod +x "$APP_DIR/eduxel.py"
}

write_service() {
  if svc_has_systemd; then
    cat > "/etc/systemd/system/${SERVICE_NAME}.service" <<SVC
[Unit]
Description=Eduxel Credential Server
After=network.target

[Service]
ExecStart=/usr/bin/python3 $APP_DIR/eduxel.py
Restart=always
RestartSec=2

[Install]
WantedBy=multi-user.target
SVC
  fi
}

write_cli() {
  cat > "$BIN" <<'CLI'
#!/usr/bin/env bash
set -euo pipefail

SERVICE="eduxel"
CFG="/etc/eduxel/config.json"
APP_DIR="/opt/eduxel"
CONFIG_DIR="/etc/eduxel"
BIN="/usr/local/bin/eduxel"

need_root() {
  if [ "${EUID:-999}" -ne 0 ]; then
    echo "Bitte als root ausführen (oder mit sudo)."
    exit 1
  fi
}

svc_has_systemd() { command -v systemctl >/dev/null 2>&1 && [ -d /run/systemd/system ]; }

svc_start() {
  if svc_has_systemd; then systemctl start "$SERVICE" >/dev/null 2>&1; else nohup /usr/bin/python3 "$APP_DIR/eduxel.py" >/var/log/eduxel.log 2>&1 & echo $! > /var/run/eduxel.pid; fi
}

svc_stop() {
  if svc_has_systemd; then systemctl stop "$SERVICE" >/dev/null 2>&1 || true
  else
    if [ -f /var/run/eduxel.pid ]; then kill "$(cat /var/run/eduxel.pid)" >/dev/null 2>&1 || true; rm -f /var/run/eduxel.pid; fi
  fi
}

svc_restart() { svc_stop; svc_start; }

usage() {
  echo "Eduxel CLI"
  echo "Usage:"
  echo "  eduxel info"
  echo "  eduxel start|stop|restart|status"
  echo "  eduxel enable|disable"
  echo "  eduxel reset"
  echo "  eduxel uninstall"
}

cmd_mysql_detect() {
  if command -v mariadb >/dev/null 2>&1 && mariadb -e "SELECT 1;" >/dev/null 2>&1; then echo "mariadb"; return; fi
  if command -v mysql >/dev/null 2>&1 && mysql -u root -e "SELECT 1;" >/dev/null 2>&1; then echo "mysql -u root"; return; fi
  echo ""
}

if [[ "${1:-}" == "info" || "${1:-}" == "-info" ]]; then
  if [[ ! -f "$CFG" ]]; then echo "config.json nicht gefunden: $CFG"; exit 1; fi
  jq -r '"Mode: \(.mode)\nAPI-Port: \(.app.port)\nSecret: \(.app.secret)\nDB: mysql://\(.database.user)@\(.database.host):\(.database.port)/\(.database.database)"' "$CFG"
  exit 0
fi

case "${1:-}" in
  start)
    need_root
    svc_start
    echo "OK"
    ;;
  stop)
    need_root
    svc_stop
    echo "OK"
    ;;
  restart)
    need_root
    svc_restart
    echo "OK"
    ;;
  status)
    if svc_has_systemd; then systemctl status "$SERVICE" --no-pager; else
      if [ -f /var/run/eduxel.pid ] && kill -0 "$(cat /var/run/eduxel.pid)" >/dev/null 2>&1; then echo "running (pid $(cat /var/run/eduxel.pid))"; else echo "stopped"; fi
    fi
    ;;
  enable)
    need_root
    if svc_has_systemd; then systemctl enable "$SERVICE" >/dev/null 2>&1; echo "OK"; else echo "Kein systemd: enable nicht verfügbar."; exit 1; fi
    ;;
  disable)
    need_root
    if svc_has_systemd; then systemctl disable "$SERVICE" >/dev/null 2>&1; echo "OK"; else echo "Kein systemd: disable nicht verfügbar."; exit 1; fi
    ;;
  uninstall)
    need_root
    read -r -p "Wirklich deinstallieren? (y/N): " confirm
    if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then echo "Abgebrochen."; exit 0; fi
    svc_stop || true
    if svc_has_systemd; then
      systemctl disable "$SERVICE" >/dev/null 2>&1 || true
      rm -f "/etc/systemd/system/${SERVICE}.service"
      systemctl daemon-reload >/dev/null 2>&1 || true
    fi
    rm -rf "$APP_DIR" "$CONFIG_DIR"
    rm -f "$BIN"
    echo "OK"
    ;;
  reset)
    need_root
    if [[ ! -f "$CFG" ]]; then echo "config.json nicht gefunden: $CFG"; exit 1; fi
    id="EDUXEL-RESET-$(date +%s)"
    echo "Bestätigungs-ID: $id"
    read -r -p "Tippe die ID exakt ein: " typed
    if [[ "$typed" != "$id" ]]; then echo "Falsche ID. Abbruch."; exit 1; fi

    mode="$(jq -r '.mode' "$CFG")"
    new_secret="$(openssl rand -hex 32)"

    if [[ "$mode" == "auto" ]]; then
      new_pass="$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9!@#$%^&*()_+=-' | head -c 20)"
      db_name="$(jq -r '.database.database' "$CFG")"
      db_user="$(jq -r '.database.user' "$CFG")"
      mysqlcmd="$(cmd_mysql_detect)"
      if [[ -z "$mysqlcmd" ]]; then echo "Reset: kein Root-DB Zugriff automatisch möglich."; exit 1; fi
      $mysqlcmd <<SQL
ALTER USER '${db_user}'@'%' IDENTIFIED BY '${new_pass}';
GRANT ALL PRIVILEGES ON ${db_name}.* TO '${db_user}'@'%';
FLUSH PRIVILEGES;
SQL
      tmp="${CFG}.tmp"
      jq --arg sec "$new_secret" --arg pass "$new_pass" '.app.secret=$sec | .database.password=$pass' "$CFG" > "$tmp" && mv "$tmp" "$CFG"
      svc_restart || true
      echo "OK"
      echo "Neues Secret: $new_secret"
      echo "Neues DB-Passwort: $new_pass"
      exit 0
    fi

    tmp="${CFG}.tmp"
    jq --arg sec "$new_secret" '.app.secret=$sec' "$CFG" > "$tmp" && mv "$tmp" "$CFG"
    svc_restart || true
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
CLI
  chmod +x "$BIN"
}

main() {
  if ! is_root; then die "Bitte als root ausführen (oder mit sudo)."; fi

  need_tty

  printf "%s\n" "------------------------------------------"
  printf "%s\n" "               E D U X E L"
  printf "%s\n" "------------------------------------------"
  printf "\n"
  printf "%s\n\n" "Willkommen zum Eduxel Installer."
  printf "%s\n" "MariaDB Setup:"
  printf "%s\n" "  1) Automatisch installieren & remote DB einrichten"
  printf "%s\n" "  2) Manuell (du trägst DB später selbst ein)"
  printf "\n"
  read -r -p "> Auswahl (1/2): " OPTION

  local pm
  pm="$(pm_detect)"
  [ "$pm" != "unknown" ] || die "Kein unterstützter Package Manager gefunden."

  pm_install "$pm" curl openssl jq python3

  local pub_ip secret
  pub_ip="$(get_public_ip)"
  secret="$(openssl rand -hex 32)"

  mkdir -p "$CONFIG_DIR" "$APP_DIR"

  if [[ "$OPTION" == "1" ]]; then
    local db_pass mysqlcmd pkgs
    db_pass="$(rand_pass)"
    pkgs="$(mariadb_server_pkgs "$pm")"
    if [ -n "$pkgs" ]; then
      pm_install "$pm" $pkgs
    else
      die "Konnte MariaDB Pakete für dieses System nicht bestimmen."
    fi

    mariadb_enable_remote_bind
    mysqlcmd="$(mysql_cmd_prompt)"

    $mysqlcmd <<SQL
CREATE DATABASE IF NOT EXISTS ${DB_NAME};
CREATE USER IF NOT EXISTS '${DB_USER}'@'%' IDENTIFIED BY '${db_pass}';
ALTER USER '${DB_USER}'@'%' IDENTIFIED BY '${db_pass}';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'%';
FLUSH PRIVILEGES;
SQL

    write_config "auto" "$pub_ip" "3306" "$DB_USER" "$db_pass" "$DB_NAME" "$APP_PORT" "$secret"
  else
    write_config "manual" "HIER_EINTRAGEN" "3306" "HIER_EINTRAGEN" "HIER_EINTRAGEN" "HIER_EINTRAGEN" "$APP_PORT" "$secret"
  fi

  write_app
  write_service
  write_cli
  svc_enable_start "$SERVICE_NAME"

  printf "\n"
  printf "%s\n" "------------------------------------------"
  printf "%s\n" "        ✓ Eduxel erfolgreich installiert!"
  printf "%s\n" "------------------------------------------"
  printf "\n"
  printf "Server-IP:   %s\n" "$pub_ip"
  printf "API-Port:    %s\n" "$APP_PORT"
  printf "Secret:      %s\n" "$secret"
  printf "Mode:        %s\n" "$( [ "$OPTION" == "1" ] && echo "auto (remote DB)" || echo "manual" )"
  printf "\n"
  printf "CLI:         %s\n" "$BIN"
  printf "\n"
  if [[ "$OPTION" == "1" ]]; then
    printf "Wichtig: Port 3306 muss in Firewall/Provider offen sein.\n"
  fi
}

main
