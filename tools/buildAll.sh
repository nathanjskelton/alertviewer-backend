#!/bin/bash

cd /opt/user/dev/vue/logviewer-ui
npm run build
rm -rf /opt/user/dev/logviewer/src/main/resources/public/*
cp -R dist/* /opt/user/dev/logviewer/src/main/resources/public/

cd /opt/user/dev/logviewer
mvn clean install

rm ~/home/Nathan/logging/logviewer-1.0-SNAPSHOT.jar 
cp target/logviewer-1.0-SNAPSHOT.jar ~/home/Nathan/logging

