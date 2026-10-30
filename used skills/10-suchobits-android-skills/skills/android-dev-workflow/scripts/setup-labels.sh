#!/usr/bin/env bash
# Setup triage labels using the configured issue tracker.
# Safe to run multiple times — skips existing labels.
#
# Usage:
#   bash skills/android-dev-workflow/scripts/setup-labels.sh

set -euo pipefail

SCRIPTS_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPTS_DIR/lib/tracker.sh"

echo "Setting up triage labels ($TRACKER_BACKEND)..."
echo
tracker_create_labels
echo
echo "Done. Labels are ready for issue-based dispatch."
