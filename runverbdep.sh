#!/bin/bash
#


# Extra memory for java.
export MAVEN_OPTS="-Xmx1000m"


# Having extra space at the end of the string seems to make maven angry.
# Don't add arguments from $@ unless we have to.
args="-info $info -set $dataset"
if (( $# > 0 )); then
    args="$args $@"
fi


mvn exec:java -Dexec.mainClass=nate.CountVerbDepCorefs -Dexec.args="$args"
