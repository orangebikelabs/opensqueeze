#!/usr/bin/env bash

#
# Copyright (c) 2025 The OpenSqueeze Authors. All Rights Reserved.
# Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
#

set -euo pipefail

terminate_crashpad_handler() {
  # Emulator might hang forever in some circumstances
  # Try to kill problematic process

  # Try SIGTERM first
  echo "Try stopping crashpad_handler…"
  pkill -f -SIGTERM crashpad_handler || true

  # Wait for the process to terminate, and SIGKILL after 5 seconds if still alive
  sleep 5
  if pgrep -f crashpad_handler >/dev/null; then
    echo "crashpad_handler still not terminated, try killing ☠️…"
    pkill -f -SIGKILL crashpad_handler || true
  fi
}

trap terminate_crashpad_handler EXIT

./gradlew connectedAndroidTest