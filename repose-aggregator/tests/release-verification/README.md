The primary objective of this project is to provide an automated way of
verifying releases.

To accomplish that objective, a consistent, stable environment is provided
by Docker and a supporting set of scripts. Gradle tasks provide
simple management of the Docker environment.

Tangentially, the scaffolding put in place by this project provides a
relatively simple way of standing up a Repose sandbox. A Repose sandbox
can be used for quickly troubleshooting issues, or experimenting with
different facets of Repose or its environment.

# Gradle Management

Use JDK 27 and a running Docker daemon. Run wrapper commands from the repository
root unless the command explicitly gives a relative wrapper path.
A number of Gradle tasks have been defined to either stand up a Repose
sandbox or run a smoke test against a sandboxed instance of Repose.
These tasks can be viewed using the `../../../gradlew tasks` command on this
project.

A number of Gradle properties are used as the parameters for the
Repose sandbox environment. These properties are described by the
table below. Remember that Gradle accepts properties by using the
following command form to invoke a Gradle task:
`./gradlew <task> -P<property-key>=<property-value>`

| Property Name   | Default Value          | Description |
| --------------- | ---------------------- | ----------- |
| config-dir      | $projectDir/src/config | The absolute path of the directory containing the configured files to be used by Repose. |
| release-version | local                  | The version of Repose to set up in the environment. |

The release-version property should either match a release tag in the
Repose repository, or be one of {local, current}. A value of "local" will
build the project locally and set up the built artifacts. A value of
"current" will set up the latest published release.

# Verifying A Release
Gradle tasks have been set up to allow for verifying either the DEB
packages, the RPM packages, or both. To verify a release, run `./gradlew :repose-aggregator:tests:release-verification:smokeTest -Prelease-version=<version>` (use an actually published release tag) from the repository root.
A successful run confirms the configured smoke checks for those packages. Different
versions require different configurations to test all of the artifacts.
To verify a v7.x release from the main project dir, run something like
`./gradlew :repose-aggregator:tests:release-verification:smokeTest -Prelease-version=7.3.7.1 -Pconfig-dir=/Fully/Qualified/Path/repose/repose-aggregator/tests/release-verification/src/config_7`

# Repose As A Sandbox
A sandbox can only be started in Docker -- Vagrant is no longer supported.

To start the Docker sandbox, run the `buildDebImage` or `buildRpmImage`
task with the desired properties. Once the Docker image has been built,
a shell session can be started within the container to enable open
access. The easiest way to do so is to override the Docker `CMD`
instruction by providing a command when invoking Docker run. For example,
the following command may be used:
`docker run -ti repose:deb-release-verification /bin/bash`
To debug the instance of Repose running in the sandbox, connect a remote
debugger to the port mapped by docker to the exposed JDWP port. So if
the Docker container was started with
`docker run -p 127.0.0.1:18028:10037 -t repose:deb-release-verification tail -f /var/log/repose.log`
then a debugger can be connected to the localhost on port 18028.
Note that we overrode the Docker `CMD` instruction with
`tail -f /var/log/repose.log` to keep the Docker container running.
