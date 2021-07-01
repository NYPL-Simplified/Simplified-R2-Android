#!/bin/sh

for f in $(seq 1 1000)
do
  (cat <<EOF
<?xml version="1.0" encoding="utf-8"?>

<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"
      lang="en-GB"
      epub:prefix="z3998: http://www.daisy.org/z3998/2012/vocab/structure/, se: http://standardebooks.org/vocab/1.0"
      xml:lang="en-GB">
<head>
  <title>Chapter $f</title>
</head>
<body epub:type="bodymatter z3998:fiction">
<section id="p$f" role="doc-chapter" epub:type="chapter">
  <h2 epub:type="title z3998:roman">$f</h2>
  <p>Page $f</p>
</section>
</body>
</html>
EOF
) > p$f.xhtml
done
