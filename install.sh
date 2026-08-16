#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────
#  DIXY installer — fetches only the prebuilt release artifacts
#  (dixy-agent.jar, dixy-cli.jar, dixy.sh) from a GitHub Release.
#  Never clones the repo, never touches source.
#
#  Usage:
#    curl -sSL https://raw.githubusercontent.com/roy-gunjan743/DIXY/main/install.sh | bash
#
#  Optional:
#    DIXY_INSTALL_DIR=~/tools/dixy   curl -sSL ... | bash
#    DIXY_VERSION=v1.2.0             curl -sSL ... | bash   (pin a version)
# ─────────────────────────────────────────────────────────────────────
set -euo pipefail

REPO="roy-gunjan743/DIXY"
INSTALL_DIR="${DIXY_INSTALL_DIR:-$HOME/.dixy}"
VERSION="${DIXY_VERSION:-latest}"
API_BASE="https://api.github.com/repos/$REPO/releases"

need() { command -v "$1" >/dev/null 2>&1 || { echo "[ERROR] '$1' is required but not installed."; exit 1; }; }
need curl
need java

echo "[dixy-install] Target directory: $INSTALL_DIR"
mkdir -p "$INSTALL_DIR"

# ---- resolve the release to install -----------------------------------
if [ "$VERSION" = "latest" ]; then
    RELEASE_URL="$API_BASE/latest"
else
    RELEASE_URL="$API_BASE/tags/$VERSION"
fi

echo "[dixy-install] Resolving release ($VERSION)..."
RELEASE_JSON="$(curl -fsSL "$RELEASE_URL")" || {
    echo "[ERROR] Could not reach $RELEASE_URL — check the version/tag or your network."
    exit 1
}

TAG="$(echo "$RELEASE_JSON" | grep -m1 '"tag_name"' | sed -E 's/.*"tag_name": *"([^"]+)".*/\1/')"
echo "[dixy-install] Installing release: $TAG"

# ---- pull only the three release assets we actually ship --------------
# (No source archive is ever requested — GitHub's auto-generated
#  "Source code (zip)" asset is deliberately never touched here.)
ASSETS=(dixy-agent.jar dixy-cli.jar dixy.sh)

download_asset() {
    local name="$1"
    local url
    url="$(echo "$RELEASE_JSON" \
        | grep -o "\"browser_download_url\": *\"[^\"]*${name}\"" \
        | sed -E 's/.*"(https:[^"]+)"/\1/' | head -n1)"

    if [ -z "$url" ]; then
        echo "[ERROR] Release $TAG has no asset named '$name'."
        exit 1
    fi
    echo "[dixy-install] Downloading $name..."
    curl -fsSL "$url" -o "$INSTALL_DIR/$name"
}

for asset in "${ASSETS[@]}"; do
    download_asset "$asset"
done

chmod +x "$INSTALL_DIR/dixy.sh"

# ---- expose a `dixy` command on PATH -----------------------------------
BIN_DIR="$HOME/.local/bin"
mkdir -p "$BIN_DIR"
ln -sf "$INSTALL_DIR/dixy.sh" "$BIN_DIR/dixy"

echo ""
echo "[dixy-install] Done. Installed to: $INSTALL_DIR"
if [[ ":$PATH:" != *":$BIN_DIR:"* ]]; then
    echo "[dixy-install] Add this to your shell profile to use the 'dixy' command anywhere:"
    echo "    export PATH=\"\$HOME/.local/bin:\$PATH\""
fi
echo "[dixy-install] Run it with:  dixy   (or $INSTALL_DIR/dixy.sh) inside your project folder."