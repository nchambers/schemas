package nate.cluster;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Vector;
import java.util.Collection;
import java.util.Random;
import java.util.Arrays;

import nate.util.Dimensional;
import nate.BasicEventAnalyzer;


public class DivisiveClustering {
  float _minImprovement = 0.02f;
  int _clusterMaxSize = 2000;
  int _clusterMinSize = 100;

  public DivisiveClustering() {
    System.out.println("DivisiveClustering()");
    System.out.println("  _minImprovement = " + _minImprovement);
    System.out.println("  _clusterMaxSize = " + _clusterMaxSize);
    System.out.println("  _clusterMinSize = " + _clusterMinSize);
  }


  public Set<Integer>[] cluster(Vector<String> names,
				Collection<Map<String,Float>> alldata) {
    int dataSize = alldata.size();
    Map<String,Float>[] data = new Map[dataSize];
    data = alldata.toArray(data);
    Dimensional.lengthNormalize(data);

    // The KMeans algorithm.
    KMeans kmeans = new KMeans(2);
    int numSplit = 1;

    // Initialize cluster vectors.
    Vector<Set<Integer>> clusters = new Vector();
    Vector<Set<Integer>> newClusters = new Vector();
    Vector<Set<Integer>> finishedClusters = new Vector();
    // Create the initial cluster, the entire dataset.
    Set<Integer> topCluster = new HashSet();
    for( int i = 0; i < data.length; i++ ) topCluster.add(i);
    clusters.add(topCluster);

    // Keep splitting clusters.
    while( numSplit > 0 ) {
      numSplit = 0;
      newClusters = new Vector();

      // Try to split each cluster.
      System.out.println("**Splitting " + clusters.size() + " clusters");
      for( Set<Integer> cluster : clusters ) {
	// Build this cluster's unique list of feature vectors.
	Vector<Map<String,Float>> clusterData = new Vector();
	Map<Integer,Integer> idMap = new HashMap();
	int i = 0;
	for( Integer id : cluster ) {
	  clusterData.add(data[id]);
	  idMap.put(i++, id);
	}

	// KMeans with 2 clusters.
	System.out.println("Calling KMeans on size " + clusterData.size());
	Set<Integer> split[] = kmeans.cluster(null, clusterData, false);
	float improvement = _minImprovement;

	System.out.println("Split cluster size " + clusterData.size() + " to " +
			   split[0].size() + " and " + split[1].size());

	// If the cluster is huge, we split anyway.
	if( cluster.size() > _clusterMaxSize ) { }
	// Else we check the overall score improvement.
	else {
	  float score  = scoreFullCluster(clusterData);
	  float score1 = (split[0].size() == 1) ? score : scoreCluster(split[0], clusterData);
	  float score2 = (split[1].size() == 1) ? score : scoreCluster(split[1], clusterData);
	  improvement = (score1 - score) + (score2 - score);
	  System.out.println("score=" + score + " split1=" + score1 + " split2=" + score2);
	  System.out.println("**improvement = " + improvement);
	}

	if( split[0].size() < _clusterMinSize || split[1].size() < _clusterMinSize )
	  finishedClusters.add(cluster);
	// If splitting is a good improvement, we split!
	else if( improvement >= _minImprovement ) {
	  newClusters.add(recoverIndices(split[0], idMap));
	  newClusters.add(recoverIndices(split[1], idMap));
	  numSplit++;
	  System.out.println("Added 2 new clusters from split.");
	} else {
	  finishedClusters.add(cluster);
	  System.out.println("Didn't add clusters from split.");
	}
      }

      System.out.println("**Total newClusters = " + newClusters.size());
      System.out.println("**Total finished = " + finishedClusters.size());
      clusters = newClusters;
    }

    Set<Integer> arr[] = new HashSet[finishedClusters.size()];
    arr = finishedClusters.toArray(arr);
    return arr;
  }

  /**
   * Given a list of IDs in a cluster, compare all data items with the IDs
   * to each other and return a cluster score.
   */
  private float scoreCluster(Set<Integer> cluster, Vector<Map<String,Float>> clusterData) {
    float score = 0.0f;
    for( Integer id : cluster ) {
      for( Integer id2 : cluster ) {
	if( id < id2 )
	  score += Dimensional.dotProduct(clusterData.elementAt(id),
					  clusterData.elementAt(id2));
      }
    }

    // Average pair score.
    score /= (cluster.size() * (cluster.size()-1)) / 2;
    return score;
  }


  /**
   * Compare all data items to each other and return a cluster score.
   */
  private float scoreFullCluster(Vector<Map<String,Float>> clusterData) {
    int size = clusterData.size();
    float score = 0.0f;
    //    int numScored = 0;
    for( int i = 0; i < size; i++ ) {
      for( int j = i+1; j < size; j++ ) {
	float sim = Dimensional.dotProduct(clusterData.elementAt(i),
					   clusterData.elementAt(j));
	score += sim;
	//	numScored++;
      }
    }

    //    System.out.println("  calc=" + numScored + " size=" + ((size * (size-1)) / 2));
    // Average pair score;    
    score /= (size * (size-1)) / 2;
    return score;
  }

  /**
   * Assumes that the originals Vector is sorted by integer.
   * Misaligned is a set, so we sort it first.
   */
  public static Set<Integer> recoverIndices(Set<Integer> cluster, 
					    Map<Integer,Integer> idMap) {
    Set<Integer> aligned = new HashSet(cluster.size());
    for( Integer id : cluster )
      aligned.add(idMap.get(id));
    return aligned;
  }
}


