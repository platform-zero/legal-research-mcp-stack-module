#!/usr/bin/env sh
set -eu

: "${LEGAL_CORPUS_DATA_DIR:=/data/creator}"
: "${LEGAL_CORPUS_OUTPUT:=/data/corpus/current.jsonl}"
: "${LEGAL_INGESTION_TRIGGER_URL:?LEGAL_INGESTION_TRIGGER_URL is required}"
: "${LEGAL_INGESTION_SHARED_TOKEN:?LEGAL_INGESTION_SHARED_TOKEN is required}"

mkdir -p "$(dirname "$LEGAL_CORPUS_OUTPUT")" "$LEGAL_CORPUS_DATA_DIR"
temporary_output="${LEGAL_CORPUS_OUTPUT}.next"
rm -f "$temporary_output"

mkoalc \
  --sources federal_register_of_legislation,nsw_legislation,queensland_legislation,south_australian_legislation,western_australian_legislation,tasmanian_legislation \
  --output "$temporary_output" \
  --data_dir "$LEGAL_CORPUS_DATA_DIR"

mv "$temporary_output" "$LEGAL_CORPUS_OUTPUT"
curl --fail --silent --show-error \
  --request POST \
  --header "X-Legal-Ingestion-Token: $LEGAL_INGESTION_SHARED_TOKEN" \
  "$LEGAL_INGESTION_TRIGGER_URL"
