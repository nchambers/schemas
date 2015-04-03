package nate.reading;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

import nate.CountTokenPairs;
import nate.IDFMap;
import nate.narrative.EventPairScores;
import nate.util.HandleParameters;



/**
 * This class takes pair counts and outputs PMI scores in a matrix format
 * that Richard needs for his clustering algorithm.  It outputs two files:
 *   1. word to index
 *   2. pairwise matrix of pmis
 *
 * -domainidf
 * Path to the IDF scores for the domain corpus.
 *
 * -paircounts
 * Path to the token pair counts from CountTokenPairs.
 */
public class CountsToPMI {
  int _docCutoff = 50;
  IDFMap _domainIDF;
  //  IDFMap _generalIDF;

  CountTokenPairs _tokenPairCounts;

  String _outindexpath = "words.indices";
  String _outmatrixpath = "words.matrix";


  public CountsToPMI(String args[]) {
    HandleParameters params = new HandleParameters(args);

    _domainIDF = new IDFMap(params.get("-domainidf"));
    //    _generalIDF = new IDFMap(params.get("-generalidf"));

    _tokenPairCounts = new CountTokenPairs(params.get("-paircounts"));
  }


  public void countsToPMI() {
    int max = 1;
    Map<String,Integer> vocab = new HashMap();
    Map<Integer,String> reverseVocab = new HashMap();
    int allPairsCount = _tokenPairCounts.getTotalCount();

    // Build the vocabulary.
    for( String token : _tokenPairCounts.intKeySet() ) {
      if( !vocab.containsKey(token) ) {
        reverseVocab.put(max, token);
        vocab.put(token, max++);
      }
      for( String token2 : _tokenPairCounts.intKeySet(token) ) {
        if( !vocab.containsKey(token2) ) {
          reverseVocab.put(max, token2);
          vocab.put(token2, max++);
        }
      }
    }

    System.out.println("vocab size " + vocab.size());
    System.out.println("reverse size " + reverseVocab.size());
    System.out.println("max= " + max);

    // Write vocab to file
    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(_outindexpath));
      for( int i = 1; i < max; i++ )
        out.write(i + "\t" + reverseVocab.get(i) + "\n");
      out.close();
    } catch( Exception ex ) { ex.printStackTrace(); }


    // Calculate PMIs, write to file.
    // Ignore all pairs with less that 1 occurrence count.
    EventPairScores pmis = StatisticsDeps.pairCountsToPMI(_tokenPairCounts, _domainIDF,
        null, 1, 1.0);
    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(_outmatrixpath));

      for( String token : _tokenPairCounts.intKeySet() ) {
        int index1 = vocab.get(token);
        for( String token2 : _tokenPairCounts.intKeySet(token) ) {
          int index2 = vocab.get(token2);
          double pmi = pmis.getScore(token, token2);
          if( pmi > 0.0 )
            out.write(index1 + "\t" + index2 + "\t" + pmi + "\n");
        }
      }
      out.close();
    } catch( Exception ex ) { ex.printStackTrace(); }

  }


  public static void main(String[] args) {
    CountsToPMI count = new CountsToPMI(args);
    count.countsToPMI();
  }
}
