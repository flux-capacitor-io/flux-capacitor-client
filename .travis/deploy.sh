#!/usr/bin/env bash

set -e

# only do deployment when travis detects a new tag
if [ ! -z "$TRAVIS_TAG" ]
then
    echo "commit is tagged with $TRAVIS_TAG"

    # only deploy tags if they start with a number
    if [ "$TRAVIS_BRANCH" = 'master' ] && [ "$TRAVIS_PULL_REQUEST" == 'false' ] && [ $TRAVIS_TAG =~ ^[0-9].*$ ];
    then
        echo "deploying release $TRAVIS_TAG"
        mvn --settings .travis/settings.xml org.codehaus.mojo:versions-maven-plugin:2.3:set -DnewVersion=$TRAVIS_TAG -Prelease
        mvn clean deploy --settings .travis/settings.xml -DskipTests=true -Prelease
    else
        echo "not deploying because the tag did not begin with a number"
    fi
else
    echo "commit isn't tagged -> no need to deploy a new release"
fi
