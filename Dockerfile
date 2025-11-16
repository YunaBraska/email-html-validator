# Base image for fetching and preparing the binary
FROM debian:stable-slim AS fetcher

# Install required tools
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl ca-certificates && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Determine the architecture and fetch the latest release
ARG ARCH
RUN ARCH=$(if [ "$(uname -m)" = "aarch64" ]; then echo "arm64"; else echo "amd64"; fi) && \
    echo "Fetching release for ARCH: ${ARCH}" && \
    curl -s "https://api.github.com/repos/YunaBraska/email-html-validator/releases/latest" \
    | grep "browser_download_url.*email-html-validator-linux-${ARCH}-.*.native" \
    | cut -d '"' -f 4 \
    | xargs curl -L -o /email-html-validator.native && \
    chmod +x email-html-validator.native

# Minimal runtime image
FROM debian:stable-slim

# Copy the fetched binary from the build stage
COPY --from=fetcher /email-html-validator.native /usr/local/bin/email-html-validator.native

# Ensure the binary is executable
RUN chmod +x /usr/local/bin/email-html-validator.native

# Default entrypoint
ENTRYPOINT ["v.native"]
