package nate.cluster;

import java.util.Collection;

import nate.narrative.ScoreCache;


public class SingleLinkSimilarity {

  /**
   * Similarity score based on the highest scoring edge crossing clusters.
   */
  public static float computeClusterSimilarity(Collection<String> s1, Collection<String> s2, 
      ScoreCache scores) {
    //     Set<String> s1 = new HashSet();
    //     Set<String> s2 = new HashSet();
    //     for( Integer member1 : c1 ) s1.add(data[member1]);
    //     for( Integer member2 : c2 ) s2.add(data[member2]);

    float maxscore = 0.0f;
    for( String member1 : s1 ) {
      for( String member2 : s2 ) {
        float score = scores.getScore(member1, member2);
        if( score > maxscore )
          maxscore = score;
      }
    }
    return maxscore;
  }

  /**
   * Gives the minimum non-zero edge crossing the two clusters.
   */
  public static float computeMinClusterSimilarity(Collection<String> s1, Collection<String> s2, 
      ScoreCache scores) {
    float minscore = Float.MAX_VALUE;
    for( String member1 : s1 ) {
      for( String member2 : s2 ) {
        float score = scores.getScore(member1, member2);
        // Ignore pairs that were never seen.
        if( score > 0.0f && score < minscore )
          minscore = score;
      }
    }
    return minscore;
  }

  /**
   * Gives the average score of all edges crossing the two clusters.
   */
  public static float computeNewLinksClusterSimilarity(Collection<String> s1, Collection<String> s2, 
      ScoreCache scores) {
    return computeNewLinksClusterSimilarity(s1, s2, scores, false, false);
  }

  public static float computeNewLinksClusterSimilarity(Collection<String> s1, Collection<String> s2, 
      ScoreCache scores, boolean connectionPenalty, boolean debug) {
    float score = 0.0f;
    int numlinks = 0;
    int matched = 0;
    for( String member1 : s1 ) {
      float sum = 0.0f;
      for( String member2 : s2 ) {
        if( debug ) System.out.printf("%s %s %.1f\n", member1, member2, scores.getScore(member1, member2));
        float local = scores.getScore(member1, member2);
        score += local;
        sum += local;
        numlinks++;
        if( local > 0.0f ) matched++;
      }
      if( debug ) System.out.printf("%s sum = %.1f\n", member1, sum);
    }
    if( debug ) System.out.println("score=" + score + "/" + numlinks + " = " + (score/(float)numlinks));
    float finalscore = score / (float)numlinks;

    if( !connectionPenalty )
      return finalscore;
    else {
      if( debug ) System.out.printf("penalty on %.2f\t%d\t%d\n", finalscore, matched, numlinks);
      float penalty = (float)matched / (float)numlinks;
      if( penalty < 0.68f ) penalty = 0.25f; // harsh penalty for loose clusters!
      return finalscore * penalty;
    }
  }

}
