#!/bin/sh

if [ $# -ne 2 ]
then
  echo "usage: directory output.epub" 1>&2
  exit 1
fi

INPUT="$1"
shift
OUTPUT="$1"
shift

OUTPUT_REAL=$(realpath ${OUTPUT}) || exit 1

cd "${INPUT}"
epubzip --overwrite "${OUTPUT_REAL}" || exit 1
epubcheck "${OUTPUT_REAL}" || exit 1

