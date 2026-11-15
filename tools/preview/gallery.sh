#!/bin/bash
# usage: gallery.sh <theme 0..10> out.png   (renders the theme's four props side by side)
S=${TMPDIR:-/tmp}/gal_$1.json
cd "$(dirname "$0")/../.."
MAZE_DUMP=$S MAZE_GALLERY=1 MAZE_PAL=$((5+$1)) gradle :app:testDebugUnitTest --offline --rerun-tasks --tests '*DumpSceneTest*' -q >/dev/null 2>&1
cd tools/preview && node shot.js $S "$2" "w=1500&h=420&orient=1,0,0,-1.5708&zoom=0.5&post=1&grow=2&hint=0&light=0&gal=1&shadow=0"
