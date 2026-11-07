## What is Repose?

Repose is an open-source, RESTful, middleware platform that transparently integrates with your existing infrastructure. Repose provides highly scalable and extensible solutions to API processing tasks including authentication, rate limiting, access control, logging, and more.

Please see our website for more information!
<http://www.OpenRepose.org/>

## Base Image

The current Dockerfiles use Ubuntu 24.04 and CentOS Stream 10, with the Temurin 27 JRE.
The Gradle build uses the `centos` directory and `-centos` tag suffix for the Stream image.
Local builds install packages produced from the current source. Passing `-Prepose-version` selects remote package installation.

These source definitions do not change existing images on Docker Hub. Build and publish a tag before describing it as a Java 27 image.

## How To Run This Image

This image will set up the suggested environment for running Repose. This includes installing Repose along with all of its dependencies, setting reasonable JVM options, exposing a service port, and declaring a volume for configuration.

Please see the Docker page in our documentation for more information, including specific commands to run!
<http://www.OpenRepose.org/versions/latest/recipes/docker.html>

## Tags

The publishing tasks use the tag format `x.x.x.x` or `x.x.x.x-centos`.

Tags that have already been published (e.g., older OS-specific tags) will continue to work, but there is no plan to maintain them going forward.

## Contact Us

Please contact us at <ReposeCore@Rackspace.com> with any questions or concerns!

## Other Information

Please see our documentation for more information on configuring and customizing your Repose instance!
<http://www.OpenRepose.org/versions/latest/>
