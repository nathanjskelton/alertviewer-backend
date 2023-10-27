#!/bin/bash

mvn clean install
docker build -t containeryard.evoforge.org/gmdev/platform/cortana-backend:$1 .
