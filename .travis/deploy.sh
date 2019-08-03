#!/usr/bin/env bash

set -e

# only do deployment when travis detects a new tag
if [ ! -z "$TRAVIS_TAG" ]
then
    echo "commit is tagged with $TRAVIS_TAG"

    # only deploy tags if they start with a number
    if [[ $TRAVIS_TAG =~ ^[0-9].*$ ]];
    then
        echo "deploying release $TRAVIS_TAG"
        mvn --settings .travis/settings.xml org.codehaus.mojo:versions-maven-plugin:2.3:set -DnewVersion=$TRAVIS_TAG -Prelease

        gpg2 --keyring=$TRAVIS_BUILD_DIR/pubring.gpg --no-default-keyring --import .travis/signingkey.asc
        gpg2 --allow-secret-key-import --keyring=$TRAVIS_BUILD_DIR/secring.gpg --no-default-keyring --import .travis/signingkey.asc

        mvn clean deploy --settings .travis/settings.xml -Dgpg.executable=gpg2 -Dgpg.keyname=851216B95C455EF2A5FDC634905DD694F5A57F2C -Dgpg.passphrase=$PASSPHRASE -Dgpg.publicKeyring=$TRAVIS_BUILD_DIR/pubring.gpg -Dgpg.secretKeyring=$TRAVIS_BUILD_DIR/secring.gpg -DskipTests=true --batch-mode --update-snapshots -Prelease;
    else
        echo "not deploying because the tag did not begin with a number"
    fi
else
    echo "commit isn't tagged -> no need to deploy a new release"
fi
