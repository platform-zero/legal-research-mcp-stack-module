#!/usr/bin/env sh
set -eu

: "${LEGAL_CORPUS_DATA_DIR:=/data/creator}"
: "${LEGAL_CORPUS_OUTPUT:=/data/corpus/current.jsonl}"
: "${LEGAL_CORPUS_OCR_THREADS:=1}"
: "${LEGAL_CORPUS_MAX_CONCURRENT_OCR:=1}"
: "${LEGAL_INGESTION_TRIGGER_URL:?LEGAL_INGESTION_TRIGGER_URL is required}"
: "${LEGAL_INGESTION_SHARED_TOKEN:?LEGAL_INGESTION_SHARED_TOKEN is required}"

export TESSDATA_PREFIX="${TESSDATA_PREFIX:-/usr/share/tesseract-ocr/5/tessdata}"

mkdir -p "$(dirname "$LEGAL_CORPUS_OUTPUT")" "$LEGAL_CORPUS_DATA_DIR"
temporary_output="${LEGAL_CORPUS_OUTPUT}.next"
# Keep an interrupted temporary corpus so reruns can repair/deduplicate it and
# add only missing documents instead of restarting a multi-hour scrape.
touch "$temporary_output"

mkoalc \
  --sources federal_register_of_legislation,nsw_legislation,queensland_legislation,south_australian_legislation,western_australian_legislation,tasmanian_legislation \
  --output "$temporary_output" \
  --data_dir "$LEGAL_CORPUS_DATA_DIR" \
  --num_threads "$LEGAL_CORPUS_OCR_THREADS" \
  --max-concurrent-ocr "$LEGAL_CORPUS_MAX_CONCURRENT_OCR"

mv "$temporary_output" "$LEGAL_CORPUS_OUTPUT"
curl --fail --silent --show-error \
  --request POST \
  --header "X-Legal-Ingestion-Token: $LEGAL_INGESTION_SHARED_TOKEN" \
  "$LEGAL_INGESTION_TRIGGER_URL"
