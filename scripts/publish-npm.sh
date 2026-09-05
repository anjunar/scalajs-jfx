#!/usr/bin/env bash
set -euo pipefail

INSTALL_DEPENDENCIES="${INSTALL_DEPENDENCIES:-0}"
SKIP_VERIFY="${SKIP_VERIFY:-0}"
SKIP_LINK_BRIDGE="${SKIP_LINK_BRIDGE:-0}"
DRY_RUN="${DRY_RUN:-0}"

usage() {
  cat <<'EOF'
Usage: scripts/publish-npm.sh [options]

Publishes the jfx-* npm packages plus scalajs-jfx-bridge in dependency order.
jfx-demo and the standalone scalajs-jfx CSS package are intentionally excluded.

Options:
  --install-dependencies
                       Run npm ci before verification and publishing
  --skip-verify        Skip npm run verify for each package
  --skip-link-bridge   Reuse the existing linked Scala.js bridge
  --dry-run            Pack packages without publishing them
  -h, --help           Show this help

Environment variables with the same names are also supported with value 1.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --install-dependencies)
      INSTALL_DEPENDENCIES=1
      shift
      ;;
    --skip-verify)
      SKIP_VERIFY=1
      shift
      ;;
    --skip-link-bridge)
      SKIP_LINK_BRIDGE=1
      shift
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
cd "$REPO_ROOT"
NPM_CACHE="${REPO_ROOT}/target/npm-publish-cache"
mkdir -p "$NPM_CACHE"

# Keep the order dependency-aware. jfx-demo and the standalone scalajs-jfx CSS
# package are intentionally not part of this publishing set.
PACKAGE_DIRECTORIES=(
  jfx-core
  scalajs-jfx-bridge
  jfx-json
  jfx-router
  jfx-controls
  jfx-viewport
  jfx-forms
  jfx-editor
)

for package_directory in "${PACKAGE_DIRECTORIES[@]}"; do
  manifest_path="${REPO_ROOT}/npm/${package_directory}/package.json"
  if [[ ! -f "$manifest_path" ]]; then
    echo "Package manifest not found: ${manifest_path}" >&2
    exit 1
  fi

  package_name="$(node -p "require('./npm/${package_directory}/package.json').name")"
  package_private="$(node -p "require('./npm/${package_directory}/package.json').private === true")"
  if [[ "$package_private" == "true" ]]; then
    echo "Refusing to publish private package '${package_name}'." >&2
    exit 1
  fi
  if [[ "$package_name" != @anjunar/jfx-* && "$package_name" != "@anjunar/scalajs-jfx-bridge" ]]; then
    echo "Unexpected package name '${package_name}' in ${manifest_path}." >&2
    exit 1
  fi
done

if [[ "$SKIP_LINK_BRIDGE" != "1" ]]; then
  echo "Linking the Scala.js bridge..."
  sbtn "scalajs-jfx-bridge/fullLinkJS"
fi

if [[ "$INSTALL_DEPENDENCIES" == "1" ]]; then
  echo "Installing npm workspaces..."
  npm --cache "$NPM_CACHE" ci --no-audit --no-fund
elif [[ ! -d "${REPO_ROOT}/node_modules" ]]; then
  echo "node_modules is missing. Run this script with --install-dependencies." >&2
  exit 1
fi

for package_directory in "${PACKAGE_DIRECTORIES[@]}"; do
  workspace="npm/${package_directory}"
  package_name="$(node -p "require('./${workspace}/package.json').name")"
  package_version="$(node -p "require('./${workspace}/package.json').version")"
  echo "Processing ${package_name}@${package_version}..."

  if [[ "$SKIP_VERIFY" != "1" ]]; then
    echo "  Running verification..."
    npm --cache "$NPM_CACHE" run verify --workspace "$workspace"
  fi

  publish_arguments=(--cache "$NPM_CACHE" publish --workspace "$workspace" --access public)
  if [[ "$DRY_RUN" == "1" ]]; then
    publish_arguments+=(--dry-run)
    echo "  Packing ${package_name} (dry run)..."
  else
    echo "  Publishing ${package_name}..."
  fi

  npm "${publish_arguments[@]}"
done

if [[ "$DRY_RUN" == "1" ]]; then
  echo "All selected npm packages were packed successfully (dry run)."
else
  echo "All selected npm packages were published successfully."
fi
