FROM python:3.12-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential curl tesseract-ocr libtesseract-dev libleptonica-dev \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY stack.containers/legal-corpus-creator/ /app/
RUN pip install --no-cache-dir .
COPY stack.containers/legal-corpus-creator/docker/run-legislation-sync.sh /usr/local/bin/run-legislation-sync
RUN chmod 0555 /usr/local/bin/run-legislation-sync
ENTRYPOINT ["/usr/local/bin/run-legislation-sync"]
