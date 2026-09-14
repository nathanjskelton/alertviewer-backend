#!/bin/bash

set -a
source "$(dirname "$0")/.env"
set +a

if [ -z "$IMAGE" ] || [ -z "$CONTAINER_CMD" ]; then
    echo "IMAGE or CONTAINER_CMD is not set - check .env" >&2
    exit 1
fi

VER=`mvn help:evaluate -Dexpression=project.version -q -DforceStdout`
echo "*** Pushing $IMAGE version $VER ***"
$CONTAINER_CMD push $IMAGE:$VER
