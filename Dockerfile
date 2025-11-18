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

########## SETUP PLAYWRIGHT DEPENDENCIES START ##########
RUN apt-get update && apt-get install -y --no-install-recommends \
      ca-certificates \
      curl \
      unzip \
    && rm -rf /var/lib/apt/lists/* \
    && PLAYWRIGHT_VERSION=$(/app.native --playwright-version | awk '{print $3}') \
    && curl -fsSL "https://repo1.maven.org/maven2/com/microsoft/playwright/driver-bundle/${PLAYWRIGHT_VERSION}/driver-bundle-${PLAYWRIGHT_VERSION}.jar" -o driver-bundle.jar \
    && unzip -q driver-bundle.jar "driver/linux$([ "$(uname -m)" = "aarch64" ] || [ "$(uname -m)" = "arm64" ] && echo '-arm64')/*" -d /tmp/pw \
    && mkdir -p ${PLAYWRIGHT_CLI_DIR} \
    && mv /tmp/pw/driver/linux*/* ${PLAYWRIGHT_CLI_DIR}/ \
    && ${PLAYWRIGHT_CLI_DIR}/node ${PLAYWRIGHT_CLI_DIR}/package/cli.js install chromium --only-shell --with-deps  \
    && apt-get purge -y --auto-remove curl unzip \
    && rm -rf /var/lib/apt/lists/* /var/cache/apt/* cp.txt playwright.jar driver.jar driver-bundle.jar /tmp/pw
########## SETUP PLAYWRIGHT DEPENDENCIES END ##########

# Default entrypoint
ENTRYPOINT ["email-html-validator.native"]
