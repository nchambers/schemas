#!/bin/bash
#

if (( $# < 2 )); then
    echo "runall.sh <infile> <outdir>"
    args="$args $@"
    exit;
fi

export dir=`dirname $1`
export outdir=$2
export file=`basename $1`

# Parse everything. Run Coref.
./runallparser.sh -output $outdir -input giga $dir

echo "RUNIDF"
./runidf.sh -outdir $outdir $outdir

echo "RUNVERBDEP"
./runverbdep.sh -output $outdir -deps $outdir/$file.deps -events $outdir/$file.events -parsed $outdir/$file.parse -ner $outdir/$file.ner

echo "RUNCOUNTPAIRS 1"
./runcountpairs.sh -type base -dependents -fullprep -output $outdir -idf $outdir/tokens-lemmas.idf-all -deps $outdir/$file.deps -parsed $outdir/$file.parse
mv $outdir/token-pairs-lemmas.counts $outdir/token-pairs-base-lemmas-govdep-distlog4.counts

echo "RUNCOUNTPAIRS 2"
./runcountpairs.sh -type vbnn -dependents -fullprep -nodistance -withcoref -output $outdir -idf $outdir/tokens-lemmas.idf-all -deps $outdir/$file.deps -parsed $outdir/$file.parse -events $outdir/$file.events
mv $outdir/token-pairs-lemmas.counts $outdir/token-pairs-vbnn-coref-fullprep.counts

echo "RUNARGS"
./runargs.sh -idf $outdir/tokens-lemmas.idf-all -output $outdir -deps $outdir/$file.deps -events $outdir/$file.events -parsed $outdir/$file.parse -ner $outdir/$file.ner

