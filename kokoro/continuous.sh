#!/bin/bash

set -e
set -x

# For MacOS builds, link Docker to run as 'docker'.
ln -s /Applications/Docker.app/Contents/Resources/bin/docker /usr/local/bin/docker

# Stops any left-over containers.
docker stop $(docker ps -aq)

cd github/jib

(cd jib-core; ./gradlew clean build integrationTest publishToMavenLocal --info)
(cd jib-maven-plugin; ./mvnw clean install cobertura:cobertura -B -U -X)