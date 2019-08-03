#!/usr/bin/env bash

set -e

gpg --allow-secret-key-import --import .travis/codesigning.asc
mvn versions:set -DnewVersion=${TRAVIS_TAG}
mvn deploy --settings .travis/settings.xml -P release
