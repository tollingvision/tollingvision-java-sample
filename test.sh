#!/bin/bash
for i in {1..10000}
do
   java -cp target:target/* com.smartcloudsolutions.tollingvision.TollingVisionSample 127.0.0.1:9080 false 4 ~/work/amazon/tollingvision/images/nam-group ./results.csv "^.{7}" ".*front.*" ".*rear.*" ".*scene.*"
done