#!/bin/bash
#

# Remove locks (only needed if doing expensive IR stuff)
rm locks/*

# Extra memory for java.
export MAVEN_OPTS="-Xmx3000m"


if test -z "$3"; then
    echo "runlabeldoc.sh <token-type> <max-cluster-size>"
    exit
fi

# base, vb, nn, vbnn, vbnom
clustertype=$1
# size of max cluster (e.g. 30)
clustersize=$2

shift
shift
shift
shift

dict=/u/nlp/rte/resources/lex/WordNet-3.0/dict
wordnet=./jwnl_file_properties.xml

# Build the arguments to the class
args="-type $clustertype -clustersize $clustersize -notopics -dependents"
if (( $# > 0 )); then
    args="$args $@"
fi


mvn.bat exec:java -Dexec.mainClass=nate.reading.LabelDocument -Dexec.args="$args"
