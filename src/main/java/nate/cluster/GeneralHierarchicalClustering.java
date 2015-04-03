package nate.cluster;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Vector;

import nate.BasicEventAnalyzer;
import nate.util.Triple;
import nate.util.Util;


/**
 * A Hierarchical Agglomerative Clustering algorithm that takes any given
 * cluster scoring algorithm, and clusters with it.
 *
 * The clustering algorithm takes a collection of objects that have implemented
 * the Cluster interface.  This just means you need an object that can merge
 * itself with another object of the same type.  Given a collection of these,
 * as well as a scorer that can score two objects, this class clusters!
 */
public class GeneralHierarchicalClustering {
  ClusteringScorer _clusterScorer;
  // This is solely for space and efficiency.  We don't want to store all
  // pairwise similarities in memory O(n^2).  We thus only save those that
  // are somewhat similar to each other.
  private float _minSimilarityScoreToSave = 0.3f;
  private float _minClusteringScore = 0.5f;

  /**
   * Empty constructor
   */
  public GeneralHierarchicalClustering(ClusteringScorer scorer) {     
    _clusterScorer = scorer;
    System.out.println("GeneralHierarchicalClustering()");
    System.out.println("  _minSimilarityScoreToSave = " + _minSimilarityScoreToSave);
    System.out.println("  _minClusteringScore = " + _minClusteringScore);
  }

  public void setMinInitialSimilarityScore(float min) {
    System.out.println("setting _minSimilarityScoreToSave = " + min);
    _minSimilarityScoreToSave = min;
  }

  public void setMinClusteringScore(float min) {
    System.out.println("setting _minClusteringScore = " + min);
    _minClusteringScore = min;
  }

  /**
   * HAC clustering, but doesn't return the history of HAC merges, but rather
   * only the final clusters themselves.
   */
  public Set<Integer>[] clusterFully(Collection<Cluster> alldata) {
    // HAC clustering.
    Vector<Triple> history = cluster(alldata);
    // Reconstruct the final clusters.
    Vector<Set<Integer>> clusters = ClusterUtil.reconstructClusters(history);
    Set<Integer> arr[] = new HashSet[clusters.size()];
    return clusters.toArray(arr); 
  }

  /**
   * HAC clustering, returning the full merge list so the clustering tree
   * can be rebuilt.
   */
  public Vector<Triple> cluster(Collection<? extends Cluster> alldata) {
    return simpleCluster(alldata);
    //    return efficientCluster(alldata);
  }
  
  /**
   * Given a set of data, clusters the data!  O(n^3), no priority queues.
   * This uses Manning's algorithm, found online at:
   * http://nlp.stanford.edu/IR-book/html/htmledition/hierarchical-agglomerative-clustering-1.html
   * The data must be represented as a HashMap of features.  Each map is
   * treated as a vector of features, missing features are assumed zero.
   * @return History of merges, represented as a sequence of Pairs.
   *         Each Pair is the i,j index of merging those two clusters.
   */
  public Vector<Triple> simpleCluster(Collection<? extends Cluster> alldata) {
    System.out.println("simpleCluster()");
    for( Cluster nar : alldata ) System.out.println("* " + nar);

    int dataSize = alldata.size();
    Cluster[] data = new Cluster[dataSize];
    data = alldata.toArray(data);

    // Size of each cluster.
    int clusterSizes[] = new int[data.length];
    // Current score for each cluster.
    float clusterScores[] = new float[data.length];
    // True if the index represents an active cluster.
    boolean[] actives = new boolean[dataSize];

    Util.reportMemory();
    System.out.println(dataSize + 
		       " items to cluster, total size: " + (dataSize*dataSize) +
		       " doubles " + ((double)dataSize * (double)dataSize));

    // INITIALIZE matrix to hold all cluster similarities.
    SparseMatrix simMatrix = new SparseMatrix(dataSize);
    for( int i = 0; i < dataSize; i++ ) {
      actives[i] = true;
      clusterSizes[i] = 1;
      clusterScores[i] = 0.0f;
    }

    System.out.println("Created matrix of entities.");
    Util.reportMemory();

    long startTime = System.currentTimeMillis();

    // INITIALIZE pairwise similarities : O(n^2)*O(simCompare)
    for( int i = 0; i < dataSize-1; i++ ) {
      System.out.println(i + " " + data[i]);
      for( int j = i+1; j < dataSize; j++ ) {
	System.out.println(j + " " + data[j]);
	double score = _clusterScorer.scoreMerge(data[i], data[j]);
	if( score >= _minSimilarityScoreToSave ) {
	  ClusterCell cell = new ClusterCell((float)score);
	  //	  System.out.println(i + "," + j + " = " + cell.sim());
	  simMatrix.put(i, j, cell);
	}
      }
    }

    // Sequence history of cluster merges.
    Vector<Triple> history = new Vector();

    // Perform N-1 merges, leaving one cluster over all data.
    for( int k = 1; k < dataSize-1; k++ ) {

      // O(n^2) - search through matrix for best cell
      System.out.println("Searching for best...");
      Triple bestTriple = bestScore(simMatrix, actives, _minClusteringScore);
      System.out.print("done.");
      if( bestTriple == null ) break;

      // Add the best pair to the history.
      history.add(bestTriple);
      int i = (Integer)bestTriple.first();
      int m = (Integer)bestTriple.second();
      float imScore = (Float)bestTriple.third();

      System.out.println(" -> merging clusters " + i + " and " + m);

      // Increment cluster size, save score.
      clusterSizes[i] += clusterSizes[m];
      clusterScores[i] = imScore;
      int iSize = clusterSizes[i];

      // Deactivate the second cluster, now merged with the first.
      actives[m] = false;
      // Delete vector m from the matrix (to save memory).
      simMatrix.clearRow(m);

      // Update the ith cluster from i+m
      data[i].merge(data[m]);

      // Update all remaining active cluster scores with this new cluster.
      int newnulls = 0;
      for( int j = 0; j < dataSize; j++ ) {
	if( actives[j] && i != j ) {
	  // Merges features of i and m with j.
	  double score = _clusterScorer.scoreMerge(data[i], data[j]);
	  //	  System.out.println("score " + i + "," + m + ",j=" + j + " = " + score);
	  //	  System.out.println("  i=" + i + ",j=" + j + " = " + 
	  //			     simMatrix.get((i<j) ? i : j, (i<j) ? j : i));
	  //	  System.out.println("  m=" + m + ",j=" + j + " = " + 
	  //			     simMatrix.get((j<m) ? j : m, (j<m) ? m : j));
	  int first = (i<j) ? i : j;
	  int second = (i<j) ? j : i;
	  ClusterCell cell = simMatrix.get(first, second);
	  if( cell != null || (score >= _minClusteringScore) ) {
	    //	    System.out.println("setting " + first + "," + second + " = " + score);
	    if( cell == null ) {
	      cell = new ClusterCell((float)score);
	      simMatrix.put(first, second, cell);
	      newnulls++;
	    }
	    cell.setSim((float)score);
	  }
	}
      }
      System.out.println("Added " + newnulls + " new similarity scores");
    }

    System.out.println("Returning clustering history");
    BasicEventAnalyzer.reportElapsedTime(startTime);

    return history;
  }


  /**
   * Given a matrix of scores, find the best score and return the
   * coordinate.  The i,j clusters at the coordinate must both be
   * active in the actives array.
   * @param actives The list of entities that should be considered.
   * @param minScore Only returns the best score above this given score.
   *                 Null otherwise.
   */
  private Triple bestScore(SparseMatrix matrix, boolean[] actives, float minScore) {
    Triple best = null;
    float bestSim = 0.0f;
    int dataSize = matrix.length();

    for( int i = 0; i < dataSize-1; i++ ) {
      // Only search rows that are active.
      if( actives[i] ) {
	SparseVector row = matrix.getRow(i);
	for( Map.Entry<Integer, ClusterCell> entry : row.entries() ) {
	  Integer j = entry.getKey();
	  if( j > i && actives[j] ) {
	    float score = entry.getValue().sim();
	    if( score > bestSim ) {
	      bestSim = score;
	      best = new Triple(i, j, score);
	    }
	  }
	}
      }
    }
    System.out.println(best + " with sim = " + bestSim);
    if( bestSim >= minScore )
      return best;
    // Don't return a pair if it isn't greater than the minimum required.
    else {
      System.out.println("bestScore similarity too small");
      return null;
    }
  }


  /**
   * Given a matrix of scores, find the best score and return the
   * coordinate.  The i,j clusters at the coordinate must both be
   * active in the actives array.
   */
  //  private Pair bestScore(ClusterCell[][] matrix, boolean[] actives) {
  private Triple bestScore(PriorityQueue<ClusterCell>[] queues, boolean[] actives, float minScore) {
    Triple best = null;
    float bestSim = 0.0f;
    int dataSize = queues.length;

    for( int i = 0; i < dataSize; i++ ) {
      // Only search rows that are active.
      if( actives[i] ) {
	PriorityQueue<ClusterCell> queue = queues[i];
	ClusterCell bestLocal = queue.peek();
	if( bestLocal != null && bestLocal.sim() > bestSim ) {
	  bestSim = bestLocal.sim();
	  best = new Triple(i, bestLocal.index(), bestSim);
	}
      }
    }

    //    System.out.println(best + " with sim = " + bestSim);
    if( bestSim >= minScore )
      return best;
    // Don't return a cluster pair if it isn't greater than the minimum required.
    else {
      System.out.println("bestScore similarity too small");
      return null;
    }
  }

  /**
   * Assuming we know the cluster scores for cluster i and j, and then for i+j
   * merged together, we may want to know what the average score of each new edge
   * between i and j members is.  For instance, a high scoring i and j may have
   * very low scoring edges between their members ... hence we shouldn't merge them,
   * even though their merged score might still be somewhat high.
   * @return The average score of each new edge between the i and j clusters when
   *         they are merged together (group average scoring).
   */
  private float averageScoreForNewEdges(float iScore, float jScore, float ijScore,
					int iSize, int jSize) {
    float avgdiff = (ijScore*(iSize+jSize)*(iSize+jSize-1) 
		     - iScore*iSize*(iSize-1)
		     - jScore*jSize*(jSize-1))
      /  (2 * jSize * iSize);
    return avgdiff;
  }

  public static void printHashMap(Map<String,Float> m) {
    for( Map.Entry entry : m.entrySet() )
      System.out.println(entry.getKey() + " " + entry.getValue());
  }

}
