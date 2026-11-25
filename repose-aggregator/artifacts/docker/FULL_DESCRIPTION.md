## Repose

Repose is an open-source, RESTful middleware platform for API authentication,
rate limiting, access control, logging and other request processing.

This fork publishes one versioned Linux amd64 image to
[GitHub Container Registry](https://github.com/anthony-cervantes/repose/pkgs/container/repose).
The image uses Ubuntu 24.04 and the pinned Temurin 27 runtime; its JAR, filter
EARs, configuration and license notices are staged directly from Gradle.

## Run

```bash
docker run -d --name repose -p 8080:8080 ghcr.io/anthony-cervantes/repose:10.1.2
```

Build from source and smoke-test locally with
`bash scripts/build-container.sh local/repose:dev` from the repository root.
Only version tag pushes publish to GHCR; Docker Hub, RPM and DEB deployments
are historical and not maintained by this fork.
