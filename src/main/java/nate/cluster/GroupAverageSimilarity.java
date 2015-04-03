package nate.cluster;

import java.util.Map;
import java.util.Set;

import nate.util.Dimensional;
import nate.narrative.ScoreCache;


public class GroupAverageSimilarity {

  /**
   * Cosine similarity.
   */
  public static float computeSimilarity(Map<String,Float> x, Map<String,Float> y) {
    float dot = 0.0f;
    float xmag = 0.0f;
    float ymag = 0.0f;

    // loop over x's features
    for( Map.Entry<String,Float> entry : x.entrySet() ) {
      Float yvalue = y.get(entry.getKey());
      if( yvalue != null )
	dot += entry.getValue() * yvalue;
      xmag += entry.getValue() * entry.getValue();
    }

    // loop over y's features
    for( Map.Entry<String,Float> entry : y.entrySet() )
      ymag += entry.getValue() * entry.getValue();

    //    System.out.println("  dot=" + dot + " xmag=" + xmag + " ymag=" + ymag);

    if( xmag != 0.0f && ymag != 0.0f ) {
      double denom = Math.sqrt(xmag) * Math.sqrt(ymag);
      return dot / (float)denom;
    } 
    else return 0.0f;
  }


  /**
   * The hashmap is already a sum of the feature values in
   * across all cluster members.
   * @param numi The number of docs in the first cluster
   * @param numj The number of docs in the second cluster
   */
  public static float computeClusterSimilarity(Map<String,Float> x, int numi, int numj) {
    //    System.out.println(" computeClusterSim with + " + numi + " " + numj + " sizes");
    float sum = Dimensional.dotProduct(x, x);
    sum -= numi + numj;
    float denom = (numi+numj) * (numi+numj-1.0f);

    //    System.out.println(" sum=" + sum + " denom=" + denom + " sim=" + (sum/denom));
    return sum / denom;
  }

  /**
   * This returns the group-average score, all pairs scored, returning the average
   * pair score.  We need the current scores for clusters i and j to calculate this.
   * @param iscore The current cluster score of i.
   * @param isize The number of items in cluster i.
   * @param jscore The current cluster score of j.
   * @param jsize The number of items in cluster j.
   * @param icluster The items in cluster i.
   * @param jcluster The items in cluster j.
   * @param scores A lookup of pairwise scores between data items.
   */
  public static float computeClusterSimilarity(float iscore, int isize,
					       float jscore, int jsize,
					       Set<String> icluster, Set<String> jcluster,
					       ScoreCache scores) {
    float score = 0.0f;
    for( String idatum : icluster ) {
      for( String jdatum : jcluster ) {
	//	System.out.println("   " + idatum + "," + jdatum + " = " + scores.getScore(idatum, jdatum));
	score += scores.getScore(idatum, jdatum);
      }
    }

    // Calculate how many pairs are in each sub-cluster.
    int ipairs = isize * (isize-1) / 2;
    int jpairs = jsize * (jsize-1) / 2;
    int betweenPairs = icluster.size() * jcluster.size();
    // Scale the given average pair score up to total score mass.
    iscore *= ipairs;
    jscore *= jpairs;

    // Average the between-cluster scores.
    //    System.out.println("  " + iscore + "," + jscore + "," + score + " = " +
    //		       ((score + iscore + jscore) / (ipairs + jpairs + betweenPairs)));

    // Return the average pair score.    
    return (score + iscore + jscore) / (ipairs + jpairs + betweenPairs);
  }

  /**
   * Group average score of two feature strings.
   */
  public static float groupAverage(Map<String,Float> x, Map<String,Float> y) {
    float sum = 0.0f;

    for( Map.Entry<String,Float> entry : x.entrySet() )
      sum += entry.getValue();
    for( Map.Entry<String,Float> entry : y.entrySet() )
      sum += entry.getValue();
    sum *= sum;

    int xsize = x.size();
    int ysize = y.size();
    int sizes = xsize + ysize;
    sum -= sizes;

    sum = sum / (sizes * (sizes-1));
    return sum;
  }

}
