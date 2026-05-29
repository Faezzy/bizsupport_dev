# ==========================================================================
# Build stage
# ==========================================================================
FROM ubuntu:22.04 AS builder

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential \
        cmake \
        git \
        pkg-config \
        libpqxx-dev \
        libssl-dev \
        libpq-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY CMakeLists.txt .
COPY src/ src/
COPY web/ web/
COPY config.ini .

RUN mkdir build && cd build \
    && cmake -DCMAKE_BUILD_TYPE=Release .. \
    && make -j"$(nproc)"

# ==========================================================================
# Runtime stage
# ==========================================================================
FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
        libpqxx-6.4 \
        libssl3 \
        libpq5 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/build/bizsupport .
COPY --from=builder /app/config.ini .
COPY --from=builder /app/web ./web

ENV WEB_ROOT=/app/web

EXPOSE 8080

CMD ["./bizsupport", "/app/config.ini"]
