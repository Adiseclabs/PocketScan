#!/usr/bin/env bash
# PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
# Usage: bash scripts/set_package.sh your.app.package   (run from repo root, once, right after unzipping)
set -e; NEW=$1; OLD=com.pocketlab; D=app/src/main/java
[ -z "$NEW" ] && { echo "usage: $0 your.app.package"; exit 1; }
[ "$NEW" = "$OLD" ] && exit 0
mv "$D/com/pocketlab" /tmp/pl_src; mkdir -p "$D/${NEW//.//}"; cp -r /tmp/pl_src/. "$D/${NEW//.//}/"; rm -rf /tmp/pl_src
grep -rl "$OLD" "$D/${NEW//.//}" docs | xargs sed -i "s/$OLD/$NEW/g"
echo "Done. Package is now $NEW"
