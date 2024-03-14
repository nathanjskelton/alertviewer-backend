#!/bin/bash

VER=`mvn help:evaluate -Dexpression=project.version -q -DforceStdout`
echo "*** Pushing version $VER ***"
docker push containeryard.evoforge.org/gmdev/platform/cortana-backend:$VER
