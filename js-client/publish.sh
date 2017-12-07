#!/usr/bin/env bash

set -e

rm -rf dist
tsc
npm version patch
npm publish --userconfig ~/.npmrc-renedewaele