# syntax=docker/dockerfile:1
# Use Ubuntu as a base, since it works and is familiar.
# The version should be the latest tested, LTS version.
FROM ubuntu:24.04

LABEL org.opencontainers.image.authors="The Repose Team <reposecore@rackspace.com>"

# Setting constants
ENV APP_ROOT=/etc/repose
ENV APP_VARS=/var/repose
ENV APP_LOGS=/var/log/repose

# This environment variable is used to set command-line options.
# The user can manually set these options using the "-e" flag with the Docker run command.
ENV JAVA_OPTS=

# The optional CA bundle is mounted only while downloading packages. It is not
# copied into image layers and normal builds use the system CA roots.
RUN --mount=type=secret,id=proxy_ca \
    set -eu; \
    wget_ca=''; \
    if [ -f /run/secrets/proxy_ca ]; then \
        printf 'Acquire::https::CaInfo "/run/secrets/proxy_ca";\n' > /etc/apt/apt.conf.d/99-build-proxy-ca; \
        wget_ca='--ca-certificate=/run/secrets/proxy_ca'; \
    fi; \
    apt-get update -qq; \
    apt-get install -qq -y ca-certificates wget; \
    wget $wget_ca -q -O /usr/share/keyrings/adoptium.asc https://packages.adoptium.net/artifactory/api/gpg/key/public; \
    echo 'deb [signed-by=/usr/share/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb noble main' > /etc/apt/sources.list.d/adoptium.list; \
    apt-get update -qq; \
    apt-get install -qq -y temurin-27-jre; \
    rm -f /etc/apt/apt.conf.d/99-build-proxy-ca; \
    rm -rf /var/lib/apt/lists/*

# Install local packages from the build context without retaining the DEB
# archives in image layers (the four archives total roughly 400 MB).
RUN --mount=type=bind,target=/tmp/repose-packages \
    dpkg --install /tmp/repose-packages/repose_*.deb \
      /tmp/repose-packages/repose-filter-bundle_*.deb \
      /tmp/repose-packages/repose-extensions-filter-bundle_*.deb \
      /tmp/repose-packages/repose-experimental-filter-bundle_*.deb

# Turn off local logging
RUN sed -i 's,<\(Appender.*RollingFile.*/\)>,<!--\1-->,' ${APP_ROOT}/log4j2.xml
RUN sed -i 's,<\(Appender.*PhoneHomeMessages.*/\)>,<!--\1-->,' ${APP_ROOT}/log4j2.xml

# Arbitrary User ID support
RUN chgrp -R 0 ${APP_ROOT} ${APP_VARS} ${APP_LOGS} && \
    chmod -R g=u ${APP_ROOT} ${APP_VARS} ${APP_LOGS}

# Expose APP_ROOT as a volume for mounting.
WORKDIR ${APP_ROOT}
VOLUME ${APP_ROOT}

# Switch user to repose
USER repose

# Expose the default Repose service port for host port forwarding.
# If the port in the user's system model differs from this port, the user will have to map it manually using the
# "-p" flag with the Docker run command.
EXPOSE 8080

# Start Repose.
CMD exec java $JAVA_OPTS -jar /usr/share/repose/repose.jar -c /etc/repose
