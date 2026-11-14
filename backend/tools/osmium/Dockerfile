# osmium-tool (https://osmcode.org/osmium-tool/) for extracting places from the map file.
FROM debian:bookworm-slim
RUN apt-get update \
 && apt-get install -y --no-install-recommends osmium-tool \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /work
ENTRYPOINT ["osmium"]
