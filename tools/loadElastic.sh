#!/bin/bash

curl -H "Content-Type: application/json" -XPOST "http://localhost:9200/app/_doc" -d "{ \"@timestamp\":\"2021-08-10:20:00.001\", \"fields.log_type\": \"General\", \"fields.env\": \"production\", \"priority\": \"WARN\", \"message\": \"2021-08-10:20:00,001 WARN This is a similar warning message, number 1 of 6\" }"

