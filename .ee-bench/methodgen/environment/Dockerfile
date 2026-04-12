FROM python:3.12-slim

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && \
    apt-get install -y build-essential git curl && \
    rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir tree-sitter tree-sitter-java

RUN git clone https://github.com/dpaia/feature-service.git /repo
WORKDIR /repo
RUN git checkout {{ instance.base_commit }}

LABEL ee-bench.type="methodgen"
LABEL ee-bench.version="1.0"
RUN rm -rf /repo/.ee-bench/ 2>/dev/null || true
