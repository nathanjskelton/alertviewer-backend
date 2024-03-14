#!/bin/bash

VER=`mvn help:evaluate -Dexpression=project.version -q -DforceStdout`
echo "*** Building version $VER ***"
rm -rf target
mvn clean install
docker build --build-arg VERSION=$VER -t containeryard.evoforge.org/gmdev/platform/cortana-backend:$VER .
