#!/bin/sh

for f in $(seq 1 1000)
do
  cat <<EOF
		<itemref idref="p$f.xhtml"/>
EOF
done
