#!/usr/bin/env bash

set -e

gpg --import .travis/signingkey.asc
mvn versions:set -DnewVersion=${TRAVIS_TAG}
mvn clean deploy --settings .travis/settings.xml -DskipTests=true -P release
