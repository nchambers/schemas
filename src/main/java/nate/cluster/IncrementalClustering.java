package nate.cluster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nate.narrative.ScoreCache;


public class IncrementalClustering {

  public IncrementalClustering() {
  }


  public Map<String, List<String>> cluster(Collection<String> alldata, 
					   ScoreCache scores, int similarity, int maxsize) {
    if( scores == null ) {
      System.out.println("Null score cache given!");
      System.exit(-1);
    }

    Map<String, List<String>> clusters = new HashMap<String, List<String>>();
    for( String datum : alldata ) {
      List<String> members = buildCluster(datum, alldata, scores, similarity, maxsize);
      clusters.put(datum, members);
    }
    return clusters;
  }

  /**
   * Builds a cluster around the main datum, incrementally adding the nearest neighbors.
   */
  public List<String> buildCluster(String main, Collection<String> alldata, 
				   ScoreCache scores, int similarity, int maxsize) {
    List<String> cluster = new ArrayList<String>();
    cluster.add(main);

    System.out.println("buildCluster main=" + main);

    for( int i = 0; i < maxsize; i++ ) {
      String max = null;
      double maxScore = 0.0;

      // Find the closest datum.
      for( String datum : alldata ) {
	if( !cluster.contains(datum) ) {
	  double score = scoreClusterAddition(cluster, datum, scores);
	  if( score > maxScore ) {
	    maxScore = score;
	    max = datum;
	  }
	}
      }
	
      if( max != null ) {
	cluster.add(max);
	System.out.println("Adding " + max + " score " + maxScore);
      }
      else break;
    }

    return cluster;
  }


  public double scoreClusterAddition(List<String> cluster, String datum, 
				     ScoreCache scores) {
    List<String> single = new ArrayList<String>();
    single.add(datum);

    //    System.out.println("Scoring: " + cluster + " with " + single);
    float score = SingleLinkSimilarity.computeNewLinksClusterSimilarity(cluster, single, scores, false, false);
    //        System.out.println("  -> " + score);
    return (double)score;
  }

}
