#!/bin/bash

curl -H "Content-Type: application/json" -XPOST "http://localhost:9200/app/_doc" -d "{ \"@timestamp\":\"2021-08-10T11:01:01.001\", \"fields.log_type\": \"General\", \"fields.env\": \"production\", \"priority\": \"ERROR\", \"message\": \"2020-07-13T11:01:01,001 ERROR This is a (similar) ERROR foo message\n number 1 of 6\" }"

curl -H "Content-Type: application/json" -XPOST "http://localhost:9200/app/_doc" -d "{ \"@timestamp\":\"2021-08-10T11:01:02.001\", \"fields.log_type\": \"General\", \"fields.env\": \"production\", \"priority\": \"ERROR\", \"message\": \"2020-07-13T11:01:02,001 ERROR This is a (similar) ERROR bar message\n number 2 of 6\" }"

curl -H "Content-Type: application/json" -XPOST "http://localhost:9200/app/_doc" -d "{ \"@timestamp\":\"2021-08-10T11:01:03.001\", \"fields.log_type\": \"General\", \"fields.env\": \"production\", \"priority\": \"ERROR\", \"message\": \"2020-07-13T11:01:03,001 ERROR This is a (similar) ERROR bas message\n number 3 of 6\" }"

curl -H "Content-Type: application/json" -XPOST "http://localhost:9200/app/_doc" -d "{ \"@timestamp\":\"2021-08-10T11:01:04.001\", \"fields.log_type\": \"General\", \"fields.env\": \"production\", \"priority\": \"ERROR\", \"message\": \"2020-07-13T11:01:04,001 ERROR This is a similar ERROR foo message\n number 4 of 6\" }"

curl -H "Content-Type: application/json" -XPOST "http://localhost:9200/app/_doc" -d "{ \"@timestamp\":\"2021-08-10T11:01:05.001\", \"fields.log_type\": \"General\", \"fields.env\": \"production\", \"priority\": \"ERROR\", \"message\": \"2020-07-13T11:01:05,001 ERROR This is a similar ERROR bar message, [12345] 5 of 6\" }"

curl -H "Content-Type: application/json" -XPOST "http://localhost:9200/app/_doc" -d "{ \"@timestamp\":\"2021-08-10T11:01:06.001\", \"fields.log_type\": \"General\", \"fields.env\": \"production\", \"priority\": \"ERROR\", \"message\": \"2020-07-13T11:01:06,001 ERROR This is a similar ERROR bas message, [12345] 6 of 6\" }"



