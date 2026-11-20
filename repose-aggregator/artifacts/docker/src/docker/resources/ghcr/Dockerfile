# syntax=docker/dockerfile:1
FROM ubuntu:24.04

# Install the patched Temurin runtime from its signed repository. The official
# 27-jre container tag does not yet include this out-of-cycle patch.
ARG TEMURIN_VERSION=27.0.1.1.0+4-0
RUN apt-get update -qq && apt-get install -qq -y --no-install-recommends ca-certificates wget \
    && wget -q -O /usr/share/keyrings/adoptium.asc https://packages.adoptium.net/artifactory/api/gpg/key/public \
    && echo 'deb [signed-by=/usr/share/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb noble main' > /etc/apt/sources.list.d/adoptium.list \
    && apt-get update -qq && apt-get install -qq -y --no-install-recommends "temurin-27-jre=${TEMURIN_VERSION}" \
    && rm -rf /var/lib/apt/lists/*

LABEL org.opencontainers.image.authors="The Repose Team <reposecore@rackspace.com>"
LABEL org.opencontainers.image.title="Repose"
LABEL org.opencontainers.image.description="Repose with Java 27 and packaged filter bundles"
ENV JAVA_OPTS=""
ENV APP_ROOT=/etc/repose
ENV APP_VARS=/var/repose
ENV APP_LOGS=/var/log/repose

# Use the patched Java runtime and extract application files from the
# locally built packages. Host init-system/package-manager hooks are unnecessary
# inside this container. Bind mounts keep the DEB archives out of image layers.
RUN groupadd --system repose \
    && useradd --system --gid repose --home-dir /usr/share/repose --shell /usr/sbin/nologin repose
RUN --mount=type=bind,target=/tmp/repose-packages \
    set -eu; \
    for package in /tmp/repose-packages/repose_*.deb \
      /tmp/repose-packages/repose-filter-bundle_*.deb \
      /tmp/repose-packages/repose-extensions-filter-bundle_*.deb \
      /tmp/repose-packages/repose-experimental-filter-bundle_*.deb; do \
        dpkg-deb --extract "$package" /; \
    done; \
    mkdir -p "$APP_ROOT" "$APP_VARS" "$APP_LOGS"; \
    sed -i 's,<\(Appender.*RollingFile.*/\)>,<!--\1-->,' "$APP_ROOT/log4j2.xml"; \
    sed -i 's,<\(Appender.*PhoneHomeMessages.*/\)>,<!--\1-->,' "$APP_ROOT/log4j2.xml"; \
    chown -R repose:0 "$APP_ROOT" "$APP_VARS" "$APP_LOGS"; \
    chmod -R g=u "$APP_ROOT" "$APP_VARS" "$APP_LOGS"

WORKDIR /etc/repose
VOLUME /etc/repose
USER repose
EXPOSE 8080
CMD ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /usr/share/repose/repose.jar -c /etc/repose"]
