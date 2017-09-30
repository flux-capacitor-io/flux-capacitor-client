#!/usr/bin/env bash

set -e

# only do deployment, when travis detects a new tag
if [ ! -z "$TRAVIS_TAG" ]
then
    echo "commit is tagged with $TRAVIS_TAG"

    # only release tags that start with a number
    if [[ $TRAVIS_TAG =~ ^[0-9].*$ ]];
    then
        echo "deploying release $TRAVIS_TAG"
        mvn --settings .travis/settings.xml org.codehaus.mojo:versions-maven-plugin:2.3:set -DnewVersion=$TRAVIS_TAG -Prelease

        if [ ! -z "$TRAVIS" -a -f "$HOME/.gnupg" ]; then
            shred -v ~/.gnupg/*
            rm -rf ~/.gnupg
        fi

        source .travis/gpg.sh

        mvn clean deploy --settings .travis/settings.xml -DskipTests=true --batch-mode --update-snapshots -Prelease


        if [ ! -z "$TRAVIS" ]; then
            shred -v ~/.gnupg/*
            rm -rf ~/.gnupg
        fi
    else
        echo "not deploying release because the tag did not begin with a number"
    fi
else
    echo "commit isn't tagged -> no need to deploy a new release"
fi