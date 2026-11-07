# To build this image, use a command like the following:
# docker build --build-arg REPOSE_VERSION=latest -t repose-container .
# To run the built image, use a command like the following:
# docker run --rm repose-container
FROM quay.io/centos/centos:stream10

MAINTAINER The Repose Team <reposecore@rackspace.com>

# This build-arg is used to pass the Repose version number which will be set up in this image.
ARG REPOSE_VERSION

# The service ports -- one for accepting HTTP traffic to Repose, and the other for connecting a JDWP debugger
# Note that these are not exposed to the host by default, but can be using the Docker CLI -p or -P flag.
EXPOSE 8080 10037
ENV PATH="/opt/repose-verification-venv/bin:${PATH}"

RUN yum update -q -y && yum install -q -y ca-certificates wget \
    && printf '%s\n' '[Adoptium]' 'name=Eclipse Adoptium' \
       'baseurl=https://packages.adoptium.net/artifactory/rpm/centos/$releasever/$basearch' \
       'enabled=1' 'gpgcheck=1' \
       'gpgkey=https://packages.adoptium.net/artifactory/api/gpg/key/public' > /etc/yum.repos.d/adoptium.repo \
    && yum install -q -y temurin-27-jre
RUN yum install -q -y git gcc gcc-c++ make rpm-build curl python3 python3-pip
RUN wget --quiet -O /etc/yum.repos.d/openrepose.repo https://nexus.openrepose.org/repository/el/openrepose.repo

# Note: The COPY instruction was used rather than VOLUME since the behave of VOLUME varies based on the host OS
COPY etc_repose /release-verification/etc_repose
COPY fake-services /release-verification/fake-services
COPY scripts /release-verification/scripts
COPY *.rpm /release-verification/

RUN rm /release-verification/DELETE-ME.rpm

RUN sh /release-verification/scripts/node_install.sh

RUN sh /release-verification/scripts/fake_keystone_prepare.sh
RUN cp /release-verification/fake-services/fake-keystone2/package.json /opt/fake-keystone/package.json
RUN cp /release-verification/fake-services/fake-keystone2/package-lock.json /opt/fake-keystone/package-lock.json
RUN cp /release-verification/fake-services/fake-keystone2/app.js /opt/fake-keystone/app.js
RUN sh /release-verification/scripts/fake_keystone_install.sh

RUN sh /release-verification/scripts/fake_origin_prepare.sh
RUN cp /release-verification/fake-services/fake-origin/package.json /opt/fake-origin/package.json
RUN cp /release-verification/fake-services/fake-origin/package-lock.json /opt/fake-origin/package-lock.json
RUN cp /release-verification/fake-services/fake-origin/app.js /opt/fake-origin/app.js
RUN sh /release-verification/scripts/fake_origin_install.sh

RUN yum update -y --quiet && sh /release-verification/scripts/repose_install_rpm.sh $REPOSE_VERSION
RUN cp --force /release-verification/etc_repose/* /etc/repose/

CMD ["/bin/bash", "/release-verification/scripts/verify.sh"]
