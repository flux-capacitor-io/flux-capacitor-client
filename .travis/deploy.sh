#!/usr/bin/env bash

set -e

mvn versions:set -DnewVersion=${TRAVIS_TAG}
mvn deploy --settings .travis/settings.xml -P release
