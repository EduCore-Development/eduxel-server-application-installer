#!/usr/bin/env bash
set -euo pipefail

# ==========================================================
#  EDUXEL Release Installer
#  - installs Java 25 (Temurin) if missing
#  - downloads eduxel.jar from GitHub latest release
#  - writes systemd service + /usr/local/bin/eduxel wrapper
#  - runs: java -jar eduxel.jar install <forwarded args>
# ==========================================================

RESET="\033[0m"
BOLD="\033[1m"
DIM="\033[2m"
CYAN="\033[36m"
GREEN="\033[32m"
YELLOW="\033[33m"
RED="\033[31m"
GRAY="\033[90m"

log()  { echo -e "${CYAN}➜${RESET} $*"; }
ok()   { echo -e "${GREEN}${BOLD}✓${RESET} $*"; }
warn() { echo -e "${YELLOW}${BOLD}!${RESET} $*"; }
die()  { echo -e "${RED}${BOLD}✗${RESET} $*" >&2; exit 1; }
hr()   { echo -e "${GRAY}────────────────────────────────────────────────────────${RESET}"; }

need_root() {
  if [[ "${EUID:-999}" -ne 0 ]]; then
    die "Bitte als root ausführen: ${BOLD}sudo bash install.sh${RESET}"
  fi
}

# ---------- Defaults (override via args/env) ----------
RELEASE_REPO="${RELEASE_REPO:-EduCore-Development/eduxel-server-application-installer}" # <-- HIER dein Repo mit dem Release
JAR_ASSET="${JAR_ASSET:-eduxel.jar}"                                                   # Assetname im Release
TAG="${TAG:-latest}"                                                                   # "latest" oder v1.0.0

APP_USER="${APP_USER:-eduxel}"
OPT_DIR="${OPT_DIR:-/opt/eduxel}"
JAR_PATH="${JAR_PATH:-${OPT_DIR}/eduxel.jar}"
SERVICE_NAME="${SERVICE_NAME:-eduxel}"
WRAPPER_PATH="${WRAPPER_PATH:-/usr/local/bin/eduxel}"

INSTALL_LOG="${INSTALL_LOG:-/tmp/eduxel-install.log}"

RUN_JAVA_INSTALL="true"   # kann man per --no-java-install deaktivieren

# Alles nach "--" wird 1:1 an "eduxel install ..." durchgereicht
FORWARD_ARGS=()

# ---------- PM detect/install ----------
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
      export DEBIAN_FRONTEND=noninteractive
      apt-get update -y >>"${INSTALL_LOG}" 2>&1 || true
      apt-get install -y "${pkgs[@]}" >>"${INSTALL_LOG}" 2>&1
      ;;
    dnf) dnf install -y "${pkgs[@]}" >>"${INSTALL_LOG}" 2>&1 ;;
    yum) yum install -y "${pkgs[@]}" >>"${INSTALL_LOG}" 2>&1 ;;
    pacman) pacman -Sy --noconfirm "${pkgs[@]}" >>"${INSTALL_LOG}" 2>&1 ;;
    zypper) zypper --non-interactive in "${pkgs[@]}" >>"${INSTALL_LOG}" 2>&1 ;;
    apk) apk add --no-cache "${pkgs[@]}" >>"${INSTALL_LOG}" 2>&1 ;;
    *) die "Kein unterstützter Package Manager gefunden." ;;
  esac
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Fehlt: ${BOLD}$1${RESET}"
}

# ---------- Java 25 (Temurin) ----------
install_jdk25() {
  require_cmd curl
  require_cmd tar

  if command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -qE 'version "25|openjdk 25|Temurin-25|25\.'; then
    ok "Java 25 ist bereits vorhanden"
    return 0
  fi

  local arch os arch_api api dl
  arch="$(uname -m)"
  os="$(uname -s | tr '[:upper:]' '[:lower:]')"
  [[ "${os}" == "linux" ]] || die "Dieses Script ist für Linux gedacht."

  arch_api="$(case "${arch}" in
    x86_64|amd64) echo x64 ;;
    aarch64|arm64) echo aarch64 ;;
    *) echo "${arch}" ;;
  esac)"

  api="https://api.adoptium.net/v3/binary/latest/25/ga/${os}/${arch_api}/jdk/hotspot/normal/eclipse?project=jdk"
  dl="/tmp/temurin-jdk25.tar.gz"

  log "Installiere Java 25 (Temurin) -> /opt/jdk-25"
  rm -f "${dl}" >>"${INSTALL_LOG}" 2>&1 || true
  curl -fL --retry 3 --retry-delay 1 -o "${dl}" "${api}" >>"${INSTALL_LOG}" 2>&1
  rm -rf /opt/jdk-25 >>"${INSTALL_LOG}" 2>&1 || true
  mkdir -p /opt/jdk-25 >>"${INSTALL_LOG}" 2>&1
  tar -xzf "${dl}" -C /opt/jdk-25 --strip-components=1 >>"${INSTALL_LOG}" 2>&1

  if command -v update-alternatives >/dev/null 2>&1; then
    update-alternatives --install /usr/bin/java java /opt/jdk-25/bin/java 2525 >>"${INSTALL_LOG}" 2>&1 || true
    update-alternatives --install /usr/bin/javac javac /opt/jdk-25/bin/javac 2525 >>"${INSTALL_LOG}" 2>&1 || true
    update-alternatives --set java /opt/jdk-25/bin/java >>"${INSTALL_LOG}" 2>&1 || true
    update-alternatives --set javac /opt/jdk-25/bin/javac >>"${INSTALL_LOG}" 2>&1 || true
  fi

  java -version >>"${INSTALL_LOG}" 2>&1 || true
  java -version 2>&1 | grep -qE '25' || die "Java 25 nicht aktiv. Check: java -version"
  ok "Java aktiv: $(java -version 2>&1 | head -n 1)"
}

# ---------- User + dirs ----------
ensure_user() {
  if id "${APP_USER}" >/dev/null 2>&1; then
    ok "User '${APP_USER}' existiert bereits"
  else
    log "Erstelle User '${APP_USER}'"
    useradd -m -d "/home/${APP_USER}" -s /bin/bash "${APP_USER}" >>"${INSTALL_LOG}" 2>&1
    ok "User erstellt"
  fi
}

ensure_dirs() {
  mkdir -p "${OPT_DIR}" >>"${INSTALL_LOG}" 2>&1
  chmod 0755 "${OPT_DIR}" >>"${INSTALL_LOG}" 2>&1 || true
}

# ---------- Download jar from release ----------
fetch_jar_url() {
  require_cmd curl
  require_cmd jq

  local api url
  if [[ "${TAG}" == "latest" ]]; then
    api="https://api.github.com/repos/${RELEASE_REPO}/releases/latest"
  else
    api="https://api.github.com/repos/${RELEASE_REPO}/releases/tags/${TAG}"
  fi

  url="$(curl -fsSL "${api}" | jq -r --arg n "${JAR_ASSET}" '.assets[] | select(.name==$n) | .browser_download_url' | head -n 1)"
  [[ -n "${url}" && "${url}" != "null" ]] || die "Konnte Asset '${JAR_ASSET}' nicht finden in Release (${RELEASE_REPO}, tag=${TAG})."
  echo "${url}"
}

download_jar() {
  local url="$1"
  log "Lade ${JAR_ASSET} aus Release (${TAG})"
  curl -fL --retry 3 --retry-delay 1 -o "${JAR_PATH}.tmp" "${url}" >>"${INSTALL_LOG}" 2>&1
  mv "${JAR_PATH}.tmp" "${JAR_PATH}" >>"${INSTALL_LOG}" 2>&1
  chmod 0644 "${JAR_PATH}" >>"${INSTALL_LOG}" 2>&1
  ok "JAR bereit: ${JAR_PATH}"
}

# ---------- Wrapper ----------
write_wrapper() {
  cat > "${WRAPPER_PATH}" <<EOS
#!/usr/bin/env bash
set -euo pipefail
exec /usr/bin/java -jar ${JAR_PATH} "\$@"
EOS
  chmod +x "${WRAPPER_PATH}" >>"${INSTALL_LOG}" 2>&1
  ok "CLI: ${WRAPPER_PATH}"
}

# ---------- systemd service ----------
write_systemd() {
  if ! command -v systemctl >/dev/null 2>&1 || [[ ! -d /run/systemd/system ]]; then
    warn "Kein systemd erkannt -> Service wird nicht eingerichtet."
    return 0
  fi

  cat > "/etc/systemd/system/${SERVICE_NAME}.service" <<EOS
[Unit]
Description=Eduxel Credential Server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=${APP_USER}
Group=${APP_USER}
WorkingDirectory=${OPT_DIR}
ExecStart=/usr/bin/java -jar ${JAR_PATH} serve
Restart=always
RestartSec=2
TimeoutStopSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOS

  systemctl daemon-reload >>"${INSTALL_LOG}" 2>&1 || true
  systemctl enable "${SERVICE_NAME}" >>"${INSTALL_LOG}" 2>&1 || true
  systemctl restart "${SERVICE_NAME}" >>"${INSTALL_LOG}" 2>&1 || true
  ok "systemd Service aktiv: ${SERVICE_NAME}"
}

# ---------- Run java installer (your big setup) ----------
run_java_install() {
  if [[ "${RUN_JAVA_INSTALL}" != "true" ]]; then
    warn "Java-Installer übersprungen (--no-java-install)."
    return 0
  fi
  log "Starte Java Installer: eduxel install ${FORWARD_ARGS[*]:-}"
  /usr/bin/java -jar "${JAR_PATH}" install "${FORWARD_ARGS[@]}" >>"${INSTALL_LOG}" 2>&1
  ok "Java Installer fertig"
}

usage() {
  cat <<EOF
Eduxel Release Installer

Usage:
  sudo bash install.sh [options] -- [args for "eduxel install ..."]

Options:
  --release-repo OWNER/REPO   Repo wo der Release liegt (default: ${RELEASE_REPO})
  --tag TAG                   "latest" oder z.B. v1.0.0 (default: ${TAG})
  --jar-asset NAME            Assetname (default: ${JAR_ASSET})
  --user NAME                 Linux User (default: ${APP_USER})
  --no-java-install           Nur bootstrap (Java+Jar+Service), ohne "eduxel install ..."

Examples:
  sudo bash install.sh --release-repo EduCore-Development/eduxel-server-application-installer -- --domain panel.example.com --repo https://github.com/.../site.git
  sudo bash install.sh --tag v1.0.0 -- --skip-web
EOF
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --release-repo) RELEASE_REPO="$2"; shift 2 ;;
      --tag) TAG="$2"; shift 2 ;;
      --jar-asset) JAR_ASSET="$2"; shift 2 ;;
      --user) APP_USER="$2"; shift 2 ;;
      --no-java-install) RUN_JAVA_INSTALL="false"; shift 1 ;;
      -h|--help|help) usage; exit 0 ;;
      --) shift; FORWARD_ARGS=("$@"); break ;;
      *)
        die "Unbekanntes Argument: $1 (nutze --help). Wenn das an eduxel install soll: setz vorher --"
        ;;
    esac
  done
}

main() {
  : > "${INSTALL_LOG}"
  need_root
  parse_args "$@"

  hr
  echo -e "${CYAN}${BOLD}E D U X E L  •  Release Installer${RESET}"
  hr
  echo -e "${GRAY}Release: ${RELEASE_REPO} (tag=${TAG})${RESET}"
  echo -e "${GRAY}Asset:   ${JAR_ASSET}${RESET}"
  echo -e "${GRAY}Log:     ${INSTALL_LOG}${RESET}"
  hr

  local pm
  pm="$(pm_detect)"
  [[ "${pm}" != "unknown" ]] || die "Kein unterstützter Package Manager gefunden."

  # Base deps (jq needed for GitHub API parsing)
  pm_install "${pm}" ca-certificates curl jq tar openssl >>"${INSTALL_LOG}" 2>&1 || true

  ensure_user
  ensure_dirs
  install_jdk25

  local jar_url
  jar_url="$(fetch_jar_url)"
  download_jar "${jar_url}"

  write_wrapper
  run_java_install
  write_systemd

  hr
  ok "Fertig."
  echo -e "${GRAY}Service: systemctl status ${SERVICE_NAME} --no-pager${RESET}"
  echo -e "${GRAY}Logs:    journalctl -u ${SERVICE_NAME} -f${RESET}"
  echo -e "${GRAY}CLI:     eduxel --help${RESET}"
  hr
}

main "$@"
