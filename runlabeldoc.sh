#!/bin/bash
#


# Extra memory for java.
export MAVEN_OPTS="-Xmx1800m"


if test -z "$3"; then
    echo "runlabeldoc.sh <domain> <token-type> <max-cluster-size>"
    exit
fi

# all, kidnap, bombing, attack
DOMAIN=$1
# base, vb, nn, vbnn, vbnom
clustertype=$2
# size of max cluster (e.g. 30)
clustersize=$3

shift
shift
shift
shift

muckey=../corpora/muc34/TASK/CORPORA/key-$DOMAIN.muc4
#muckey=../corpora/muc34/TASK/CORPORA/dev/key-dev-0001.muc4

#corpuspmi=pattern-nyt.mi.ordered
corpuspmi=pattern-muc-gigasearched.mi.ordered

domainpmi=pattern-muc-gigasearched.mi.ordered

CLASSPATH=$CLASSPATH:/user/natec/lib/opennlp-tools-1.3.0.jar:/user/natec/resources/opennlp-tools-1.3.0/lib/maxent-2.4.0.jar:/user/natec/resources/opennlp-tools-1.3.0/lib/trove.jar

dict=/u/nlp/rte/resources/lex/WordNet-3.0/dict
wordnet=./jwnl_file_properties.xml

# Build the arguments to the class
args="-type $clustertype -clustersize $clustersize -notopics -dependents"
if (( $# > 0 )); then
    args="$args $@"
fi


mvn.bat exec:java -Dexec.mainClass=nate.reading.LabelDocument -Dexec.args="$args"
