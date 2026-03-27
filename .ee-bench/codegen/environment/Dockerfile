FROM eclipse-temurin:24

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && \
    apt-get install -y build-essential git curl wget python3 python3-pip ca-certificates gnupg && \
    install -m 0755 -d /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg && \
    chmod a+r /etc/apt/keyrings/docker.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list && \
    apt-get update && \
    apt-get install -y docker-ce-cli && \
    rm -rf /var/lib/apt/lists/*

RUN git clone https://github.com/dpaia/feature-service.git /repo
WORKDIR /repo
RUN git checkout {{ instance.base_commit }}

RUN chmod +x ./mvnw && \
    ./mvnw dependency:go-offline -q

LABEL ee-bench.type="codegen"
LABEL ee-bench.version="1.0"
RUN rm -rf /repo/.ee-bench/ 2>/dev/null || true
