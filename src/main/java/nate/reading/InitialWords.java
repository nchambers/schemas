package nate.reading;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nate.IDFMap;
import nate.util.HandleParameters;
import nate.util.SortableScore;

/**
 * Compares how often a word appears in the first couple sentences to how often
 * it appears in general.  Sorts the list by score before returning.
 * The first IDF map given should be counts over the first couple of sentences in
 * the documents.  The second "general" map should be over the entire documents.
 *
 * 
 * -firstidf
 * The IDF counts for tokens in the first couple of sentences of docs.
 *
 * -generalidf
 * The IDF counts for all tokens in the general corpus.
 *
 *
 */
public class InitialWords {
  IDFMap _initialIDF;
  IDFMap _generalIDF;


  public InitialWords(String args[]) {
    HandleParameters params = new HandleParameters(args);

    _initialIDF = new IDFMap(params.get("-firstidf"));
    _generalIDF = new IDFMap(params.get("-generalidf"));
  }
  
  public InitialWords(IDFMap initial, IDFMap general) {
      _initialIDF = initial;
      _generalIDF = general;
  }

  public SortableScore[] calculateKeyTokens() {
      return calculateKeyTokens(0);
  }

  /**
   * Score a single token with the initial sentence score.
   * @param token Token to score.
   * @param initialCountCutoff The number of times a token must appear in the
   * first couple of sentences to be scored.
   * @return The score based on initial sentence occurrences.
   */
  public float scoreToken(String token, int initialCountCutoff) {
    int initialDocs = _initialIDF.getDocCount(token);
    int generalDocs = _generalIDF.getDocCount(token);
    
    if( initialDocs >= initialCountCutoff ) {
      //System.out.println("returning " + token + " initialDocs=" + initialDocs);
      return (float)initialDocs / (float)generalDocs;
    }
    else return 0.0f;
  }
  
  /**
   * Compares how often a word appears in the first couple sentences to how often
   * it appears in general.  Sorts the list by score before returning.
   * @param initialCountCutoff Ignores words that appear in less than this number of
   *                           documents' first few sentences.
   * @return A sorted list of words with their scores.
   */
  public SortableScore[] calculateKeyTokens(int initialCountCutoff) {
    List<SortableScore> scores = new ArrayList<SortableScore>();
    for( String token : _generalIDF.getWords() ) {
      float score = scoreToken(token, initialCountCutoff);
//        System.out.printf("%s\t%d\t%d\t= %.2f\n", token, initialDocs, generalDocs, score);
      scores.add(new SortableScore(score, token));
    }
    SortableScore[] arr = new SortableScore[scores.size()];
    arr = scores.toArray(arr);
    Arrays.sort(arr);
    return arr;
  }

  
  public static void main(String[] args) {
    InitialWords initial = new InitialWords(args);
    SortableScore[] scores = initial.calculateKeyTokens(500);
    for( SortableScore score : scores )
      System.out.println(score);
  }
}

