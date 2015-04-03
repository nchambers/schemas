package nate.util;

import java.util.Map;
import java.util.HashMap;

import nate.IDFMap;
import nate.WordEvent;
import nate.CalculateIDF;

/**
 * Helper class with static functions to take sets of counts and turn
 * them into PMI scores;
 *
 * *** Make sure the IDF map includes POS information if the strings in your
 *     counts include it.
 */
public class PMICalculator {
  // Individual verbs must be seen this many times to count the verb pair.
  private static int VERB_FREQ_CUTOFF = 100;

  PMICalculator() {
  }

  public void setVerbFrequencyCutoff(int cut) {
    VERB_FREQ_CUTOFF = cut;
  }

  /**
   * Increment the count of a key in a map of string->integer.
   */
  public static void incrementCount(Map<String,Integer> counts, 
				    String str, Integer count) {
    Integer prev = counts.get(str);
    if( prev == null ) 
      counts.put(str, new Integer(count));
    else
      counts.put(str, prev+count);
  }

  /**
   * Set any PMI score greater than the given max score to the max.
   */
  public static void reduceMaxPMI(Map<String,Double> pmis, double max) {
    for( Map.Entry<String,Double> entry : pmis.entrySet() ) {
      if( entry.getValue() > max )
	entry.setValue(max);
    }
  }

  /**
   * HashMap: key is a string representing a word pair separated by a colon.
   *          value is the number of times the pair was seen.
   * Computes the PMI score for each key, using the overall token count as
   * given in the IDFMap by summing over all word counts.
   * @param A map from strings to counts.  The string key is assumed to be a pair
   *        or words separated by a colon...the X and Y of P(X,Y)/P(X)P(Y).
   *        String format: "lives:scored;p:s"
   * @param An IDFMap object that contains corpus term frequencies.
   * @return A map with the same keys, but the values are PMI scores.
   */
  public static Map<String,Double> pairCountsToPMI(Map<String,Float> counts,
						   IDFMap idf) {
    /*
    NOTE: This isn't needed anymore as the IDFMap gives us exact corpus-wide counts.
    int totalCount = 0;
    Map<String,Integer> singleCounts = new HashMap();
    // Take one loop through the data to count overall occurrences.
    for( Map.Entry<String,Integer> entry : counts.entrySet() ) {
      String pairString = entry.getKey();
      Integer count = entry.getValue();
      int colon = pairString.indexOf(':');

      String w1 = pairString.substring(0,colon);
      String w2 = pairString.substring(colon+1);

      incrementCount(singleCounts, w1, count);
      incrementCount(singleCounts, w2, count);
      totalCount += count*2;
    }
    */

    // P(x,y) / P(x)P(y)  =  C(x,y)*n / C(x)*C(y)
    Map<String,Double> pmis = new HashMap(counts.size());
    for( Map.Entry<String,Float> entry : counts.entrySet() ) {
      String pairString = entry.getKey();
      Float count = entry.getValue();
      int colon = pairString.indexOf(':');
      int semicolon = pairString.indexOf(';');

      String w1 = pairString.substring(0,colon);
      String w2 = pairString.substring(colon+1,semicolon);
      int count1 = idf.getFrequency(w1);
      int count2 = idf.getFrequency(w2);

      if( count1 > VERB_FREQ_CUTOFF && count2 > VERB_FREQ_CUTOFF ) {
	double denom = (double)count1 * (double)count2;
	double pmi = count * (double)idf.totalCorpusCount() / denom;

	System.out.println(w1 + " with " + w2 + " " + count + " C(1)=" + 
			   count1 + " C(2)=" +
			   count2 + " total=" + idf.totalCorpusCount()
			   + "denom = " + denom + " pmi=" + pmi);
	
	pmis.put(pairString, pmi);
      }
    }

    return pmis;
  }


  /**
   * This function uses the IDF counts to skip pairs that have low occurring verbs.
   * It is important that the IDF map matches the format of the counts strings.
   * For instance, if the counts include POS information (arrest*0), then the IDF
   * map should also include it (v-arrest).
   * The function checks if the POS info is there, and assumes this is so if it is.
   */
  public static Map<String,Double> intPairCountsToPMI(Map<String,Integer> counts,
						      IDFMap idf) {
    // P(x,y) / P(x)P(y)  =  C(x,y)*n / C(x)*C(y)
    Map<String,Double> pmis = new HashMap(counts.size());
    for( Map.Entry<String,Integer> entry : counts.entrySet() ) {
      String pairString = entry.getKey();
      Integer count = entry.getValue();
      int colon = pairString.indexOf(':');
      int semicolon = pairString.indexOf(';');

      String k1 = pairString.substring(0,colon);
      String k2 = pairString.substring(colon+1,semicolon);

      // Some word counts contain words plus a marker for a verb or nominal.
      // Split off the marker for the IDF lookup!
      // "arrest*0" instead of just "arrest"
      String w1 = WordEvent.stripWordFromPOSTag(k1);
      String w2 = WordEvent.stripWordFromPOSTag(k2);

      // If the words contain POS markers (arrest*0), we need to build the special IDF lookup
      // with the POS tag (v-arrest).
      if( !w1.equals(k1) ) {
	w1 = CalculateIDF.createKey(w1, Integer.parseInt(WordEvent.stripPOSTagFromWord(k1)));
	w2 = CalculateIDF.createKey(w2, Integer.parseInt(WordEvent.stripPOSTagFromWord(k2)));
      }

      int count1 = idf.getFrequency(w1);
      int count2 = idf.getFrequency(w2);

      if( count1 > VERB_FREQ_CUTOFF && count2 > VERB_FREQ_CUTOFF ) {
	double denom = (double)count1 * (double)count2;
	double pmi = count.doubleValue() * (double)idf.totalCorpusCount() / denom;

// 	System.out.println(w1 + " with " + w2 + " " + count + " C(1)=" + 
// 			   count1 + " C(2)=" +
// 			   count2 + " total=" + idf.totalCorpusCount()
// 			   + "denom = " + denom + " pmi=" + pmi);
	
	pmis.put(pairString, pmi);
      }
      else {
	System.out.println("Skipping " + w1 + " with " + w2);
      }
    }

    return pmis;
  }


  public static double calculatePMI(int pairCount, int totalCorpusPairs,
				    int xCount, int yCount, int totalTokens,
				    boolean debug) {
    return calculatePMI((double)pairCount, totalCorpusPairs, xCount, yCount, totalTokens, debug);
  }

  public static double calculatePMI(double pairCount, int totalCorpusPairs,
				    int xCount, int yCount, int totalTokens,
				    boolean debug) {
    double joint = pairCount / (double)totalCorpusPairs;

    double totalTokensDouble = (double)totalTokens;
    double indep = ((double)xCount / totalTokensDouble) * ((double)yCount / totalTokensDouble);

    double pmi = joint / indep;

    if( debug )
      System.out.printf("count=%d\txc=%d\tyc=%d\tpmi=%f\n", pairCount, xCount, yCount, pmi);

    return pmi;
  }
}
