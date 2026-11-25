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
LABEL org.opencontainers.image.description="Repose with Java 27 and filter bundles"
ENV JAVA_OPTS=""
ENV APP_ROOT=/etc/repose
ENV APP_VARS=/var/repose
ENV APP_LOGS=/var/log/repose

# Copy JVM artifacts and configuration directly. No Repose packages or
# host init-system files are built or installed.
RUN groupadd --system repose \
    && useradd --system --gid repose --home-dir /usr/share/repose --shell /usr/sbin/nologin repose
COPY rootfs/usr/share/repose/ /usr/share/repose/
COPY rootfs/usr/share/doc/ /usr/share/doc/
COPY rootfs/etc/repose/ /etc/repose/
RUN set -eu; \
    mkdir -p "$APP_VARS" "$APP_LOGS"; \
    chmod 0755 /usr/share/repose /usr/share/repose/filters; \
    chmod 0555 /usr/share/repose/repose.jar /usr/share/repose/filters/*.ear; \
    chmod 0755 /usr/share/repose/clean-repose-deploy; \
    ln -s /usr/share/repose/clean-repose-deploy /usr/bin/clean-repose-deploy; \
    find /usr/share/doc/repose* -type f -exec chmod 0444 {} +; \
    find "$APP_ROOT" -type d -exec chmod 0755 {} +; \
    find "$APP_ROOT" -type f -exec chmod 0640 {} +; \
    chmod 0770 "$APP_ROOT"; \
    chmod 0750 "$APP_VARS" "$APP_LOGS"; \
    sed -i 's,<\(Appender.*RollingFile.*/\)>,<!--\1-->,' "$APP_ROOT/log4j2.xml"; \
    sed -i 's,<\(Appender.*PhoneHomeMessages.*/\)>,<!--\1-->,' "$APP_ROOT/log4j2.xml"; \
    chown -R repose:0 "$APP_ROOT" "$APP_VARS" "$APP_LOGS"; \
    chmod -R g=u "$APP_ROOT" "$APP_VARS" "$APP_LOGS"

WORKDIR /etc/repose
VOLUME /etc/repose
USER repose
EXPOSE 8080
CMD ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /usr/share/repose/repose.jar -c /etc/repose"]
