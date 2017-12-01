#!/usr/bin/env bash

set -e

rm -r dist
tsc
npm version patch
npm publish