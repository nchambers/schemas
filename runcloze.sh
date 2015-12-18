#!/bin/bash
#

export outdir=outstories
export outcloze=outcloze

./runexperiment.sh -idf $outdir/tokens-lemmas.idf-all -depcounts $outdir/dep-coref-lemmas.counts -argcounts $outdir/argcounts-verbs.arg -paircounts $outdir/token-pairs-vbnn-coref-fullprep.counts -stories $outcloze
