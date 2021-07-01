#!/bin/sh

for f in $(seq 1 1000)
do
  cat <<EOF
		<item href="text/p$f.xhtml" id="p$f.xhtml" media-type="application/xhtml+xml"/>
EOF
done
