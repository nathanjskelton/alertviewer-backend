#!/bin/bash

cd /opt/user/dev/vue/alertviewer-ui
npm run build
rm -rf /opt/user/dev/alertviewer-backend/src/main/resources/public/*
cp -R dist/* /opt/user/dev/alertviewer-backend/src/main/resources/public/

cd /opt/user/dev/alertviewer-backend
mvn clean install

rm ~/home/Nathan/logging/alertviewer-backend-1.0-SNAPSHOT.jar 
cp target/alertviewer-backend-1.0-SNAPSHOT.jar ~/home/Nathan/logging

