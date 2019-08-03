#!/usr/bin/env bash

set -e

echo "Importing private gpg key"
gpg --import .travis/signingkey.asc

echo "Matching artifacts version to tag name"
mvn versions:set -DnewVersion=${TRAVIS_TAG}

echo "Deploying artifacts to Maven Central"
mvn clean deploy --settings .travis/settings.xml -DskipTests=true -Dgpg.keyname=5F84CD1775351968CABF0B0D779D2423E1D24D89 -Dgpg.passphrase=$PASSPHRASE -P release
