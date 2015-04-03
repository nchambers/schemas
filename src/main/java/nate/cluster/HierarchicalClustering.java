package nate.cluster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Vector;

import nate.BasicEventAnalyzer;
import nate.Pair;
import nate.narrative.ScoreCache;
import nate.util.Dimensional;
import nate.util.Triple;
import nate.util.Util;


/**
 * A Hierarchical Agglomerative Clustering algorithm that uses group
 * average similarity for cluster comparisons.
 */
public class HierarchicalClustering {
  // This is solely for space and efficiency.  We don't want to store all
  // pairwise similarities in memory O(n^2).  We thus only save those that
  // are somewhat similar to each other.
  private float _minSimilarityScoreToSave = 0.3f;
  private float _minClusteringScore = 0.5f;

  /**
   * Empty constructor
   */
  public HierarchicalClustering() {     
    System.out.println("HierarchicalClustering()");
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
  public Set<Integer>[] clusterFully(Collection<Map<String,Float>> alldata) {
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
  public Vector<Triple> cluster(Collection<Map<String,Float>> alldata) {
    //return simpleCluster(alldata);
    return efficientCluster(alldata);
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
  public Vector<Triple> simpleCluster(Collection<Map<String,Float>> alldata) {
    int dataSize = alldata.size();
    Map<String,Float>[] data = new Map[dataSize];
    data = alldata.toArray(data);
    // Length normalize the vectors.
    Dimensional.lengthNormalize(data);
    // Sum of values for each feature across all cluster members.
    Map<String,Float>[] featSums = new Map[data.length];
    // Size of each cluster.
    int clusterSizes[] = new int[data.length];
    // Current score for each cluster.
    float clusterScores[] = new float[data.length];
    // True if the index represents an active cluster.
    boolean[] actives = new boolean[dataSize];

    System.out.println("Length normalized the data.");
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

    // INITIALIZE featSums (sum of all feature values in the cluster)
    for( int j = 0; j < dataSize; j++ ) 
      featSums[j] = Dimensional.cloneMap(data[j]);

    long startTime = System.currentTimeMillis();

    // INITIALIZE pairwise similarities : O(n^2)*O(simCompare)
    for( int i = 0; i < dataSize-1; i++ ) {
      for( int j = i+1; j < dataSize; j++ ) {
        float score = Dimensional.dotProduct(data[i], data[j]);
        if( score >= _minSimilarityScoreToSave ) {
          ClusterCell cell = new ClusterCell(score);
          cell.setFeatureSum(Dimensional.sumFeatures(data[i], data[j]));
          //	  System.out.println(i + "," + j + " = " + cell.sim());
          simMatrix.put(i, j, cell);
        }
      }
      if( i % 100 == 0 ) {
        System.out.println("i = " + i);
        Util.reportMemory();
        BasicEventAnalyzer.reportElapsedTime(startTime);
      }
    }

    // Sequence history of cluster merges.
    Vector<Triple> history = new Vector();

    // Perform N-1 merges, leaving one cluster over all data.
    for( int k = 1; k < dataSize-1; k++ ) {
      if( k % 100 == 0 ) {
        Util.reportMemory();
        BasicEventAnalyzer.reportElapsedTime(startTime);
      }

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

      // Sum all the feature vectors together between i and m.
      Map<String,Float> bestFeatSum = simMatrix.get(i, m).featureSum();
      featSums[i] = bestFeatSum;

      // Increment cluster size, save score.
      clusterSizes[i] += clusterSizes[m];
      clusterScores[i] = imScore;
      int iSize = clusterSizes[i];

      // Deactivate the second cluster, now merged with the first.
      actives[m] = false;
      // Delete vector m from the matrix (to save memory).
      simMatrix.clearRow(m);

      // Update all remaining active cluster scores with this new cluster.
      int newnulls = 0;
      for( int j = 0; j < dataSize; j++ ) {
        if( actives[j] && i != j ) {
          // Merges features of i and m with j.
          Map<String,Float> imjFeatSum = Dimensional.sumFeatures(bestFeatSum, featSums[j]);
          int jSize = clusterSizes[j];
          float score = GroupAverageSimilarity.computeClusterSimilarity(imjFeatSum, iSize, jSize);
          //	  System.out.println("score " + i + "," + m + ",j=" + j + " = " + score);
          //	  System.out.println("  i=" + i + ",j=" + j + " = " + 
          //			     simMatrix.get((i<j) ? i : j, (i<j) ? j : i));
          //	  System.out.println("  m=" + m + ",j=" + j + " = " + 
          //			     simMatrix.get((j<m) ? j : m, (j<m) ? m : j));
          int first = (i<j) ? i : j;
          int second = (i<j) ? j : i;
          ClusterCell cell = simMatrix.get(first, second);
          if( cell != null ||
              (score >= _minClusteringScore &&
                  averageScoreForNewEdges(imScore, clusterScores[j], score,
                      clusterSizes[i], clusterSizes[j]) >= _minSimilarityScoreToSave) ) {
            //	    System.out.println("setting " + first + "," + second + " = " + score);
            if( cell == null ) {
              cell = new ClusterCell(score);
              simMatrix.put(first, second, cell);
              newnulls++;
            }
            cell.setSim(score);
            cell.setFeatureSum(imjFeatSum);
          }
        }
      }
      System.out.println("Added " + newnulls + " new similarity scores");
    }

    System.out.println("Returning clustering history");
    BasicEventAnalyzer.reportElapsedTime(startTime);

    return history;
  }

  /*
  public Vector<Triple> simpleClusterScoredPairs(SparseMatrix simMatrix, int dataSize) {
    // Size of each cluster.
    int clusterSizes[] = new int[data.length];
    // Current score for each cluster.
    float clusterScores[] = new float[data.length];
    // True if the index represents an active cluster.
    boolean[] actives = new boolean[dataSize];

    // Initialize members of each cluster.
    Set<Integer>[] clusterMembers = new HashSet[data.length];
    for( int i = 0; i < data.length; i++ ) {
      clusterMembers[i] = new HashSet();
      clusterMembers[i].add(i);
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
      // Add m's members to the ith cluster.
      clusterMembers[i].addAll(clusterMembers[m]);
      clusterMembers[m].clear();

      System.out.println(" -> merging clusters " + i + " and " + m);

      // Increment cluster size, save score.
      clusterSizes[i] += clusterSizes[m];
      clusterScores[i] = imScore;
      int iSize = clusterSizes[i];

      // Deactivate the second cluster, now merged with the first.
      actives[m] = false;
      // Delete vector m from the matrix (to save memory).
      simMatrix.clearRow(m);

      // Update all remaining active cluster scores with this new cluster.
      int newnulls = 0;
      for( int j = 0; j < dataSize; j++ ) {
	if( actives[j] && i != j ) {
	  // Merges features of i and m with j.
	  int jSize = clusterSizes[j];
	  float score = GroupAverageSimilarity.computeClusterSimilarity(simMatrix, clusterMembers[i],
									clusterMembers[j]);
	  //	  System.out.println("score " + i + "," + m + ",j=" + j + " = " + score);
	  //	  System.out.println("  i=" + i + ",j=" + j + " = " + 
	  //			     simMatrix.get((i<j) ? i : j, (i<j) ? j : i));
	  //	  System.out.println("  m=" + m + ",j=" + j + " = " + 
	  //			     simMatrix.get((j<m) ? j : m, (j<m) ? m : j));
	  int first = (i<j) ? i : j;
	  int second = (i<j) ? j : i;
	  ClusterCell cell = simMatrix.get(first, second);
	  if( cell != null ||
	      (score >= _minClusteringScore &&
	       averageScoreForNewEdges(imScore, clusterScores[j], score,
				       clusterSizes[i], clusterSizes[j]) >= _minSimilarityScoreToSave) ) {
	    //	    System.out.println("setting " + first + "," + second + " = " + score);
	    if( cell == null ) {
	      cell = new ClusterCell(score);
	      simMatrix.put(first, second, cell);
	      newnulls++;
	    }
	    cell.setSim(score);
	  }
	}
      }
      System.out.println("Added " + newnulls + " new similarity scores");
    }

    System.out.println("Returning clustering history");
    BasicEventAnalyzer.reportElapsedTime(startTime);

    return history;
  }
   */


  /**
   * Given a set of data, clusters the data!  O(n^2 * lg n), uses priority queues.
   * This uses Manning's algorithm, found online at:
   * http://nlp.stanford.edu/IR-book/html/htmledition/hierarchical-agglomerative-clustering-1.html
   * The data must be represented as a HashMap of features.  Each map is
   * treated as a vector of features, missing features are assumed zero.
   * @return History of merges, represented as a sequence of Pairs.
   *         Each Pair is the i,j index of merging those two clusters.
   */
  public Vector<Triple> efficientCluster(Collection<Map<String,Float>> alldata) {
    int dataSize = alldata.size();
    Map<String,Float>[] data = new Map[dataSize];
    data = alldata.toArray(data);
    // Length normalize the vectors.
    Dimensional.lengthNormalize(data);
    // Sum of values for each feature across all cluster members.
    Map<String,Float>[] featSums = new Map[data.length];
    // Size of each cluster.
    int clusterSizes[] = new int[data.length];
    // Current score for each cluster.
    float clusterScores[] = new float[data.length];
    // True if the index represents an active cluster.
    boolean[] actives = new boolean[dataSize];

    System.out.println("Length normalized the data.");
    Util.reportMemory();
    System.out.println(dataSize + 
        " items to cluster, total size: " + (dataSize*dataSize) +
        " doubles " + ((double)dataSize * (double)dataSize));

    // INITIALIZE matrix to hold all cluster similarities.
    SparseMatrix simMatrix = new SparseMatrix(dataSize);
    PriorityQueue<ClusterCell> simQueues[] = new PriorityQueue[dataSize];
    for( int i = 0; i < dataSize; i++ ) {
      actives[i] = true;
      clusterSizes[i] = 1;
      // Don't specify 'datasize' capacity for the queue.
      // We keep only high scoring similarities, which is very low.
      simQueues[i] = new PriorityQueue(); 
    }

    System.out.println("Created priority queues for entities.");
    Util.reportMemory();

    // INITIALIZE featSums (sum of all feature values in the cluster)
    for( int j = 0; j < dataSize; j++ ) 
      featSums[j] = Dimensional.cloneMap(data[j]);

    long startTime = System.currentTimeMillis();

    // INITIALIZE pairwise similarities : O(n^2)*O(simCompare)
    Set<Integer> hasNeighbor = new HashSet<Integer>();
    for( int i = 0; i < dataSize-1; i++ ) {
      PriorityQueue<ClusterCell> queue = simQueues[i];
      for( int j = i+1; j < dataSize; j++ ) {
        float score = Dimensional.dotProduct(data[i], data[j]);
        if( score >= _minSimilarityScoreToSave ) {
          ClusterCell cell = new ClusterCell(score);
          cell.setFeatureSum(Dimensional.sumFeatures(data[i], data[j]));
          cell.setIndex(j);
          System.out.println(i + "," + j + " = " + cell.sim());
          queue.add(cell);
          simMatrix.put(i, j, cell);
          hasNeighbor.add(i);
          hasNeighbor.add(j);
        }
      }
      if( i % 100 == 0 ) {
        System.out.println("i = " + i);
        Util.reportMemory();
        BasicEventAnalyzer.reportElapsedTime(startTime);
      }
    }

    System.out.println("Finished initial pairwise similarities.");
    Util.reportMemory();

    // Inactivate any objects that don't have near neighbors.
    int loners = 0;
    for( int i = 0; i < dataSize; i++ ) {
      if( !hasNeighbor.contains(i) ) {
        actives[i] = false;
        loners++;
      }
    }
    System.out.println(loners + " loners inactivated.");
    hasNeighbor.clear();
    hasNeighbor = null;

    // Sequence history of cluster merges.
    Vector<Triple> history = new Vector();

    // Perform N-1 merges, leaving one cluster over all data.
    for( int k = 1; k < dataSize-1; k++ ) {
      if( k % 100 == 0 ) {
        Util.reportMemory();
        BasicEventAnalyzer.reportElapsedTime(startTime);
      }

      // O(n^2) - search through matrix for best cell
      System.out.println("Searching for best...");
      Triple bestTriple = bestScore(simQueues, actives, _minClusteringScore);
      System.out.print("done.");
      if( bestTriple == null ) break;

      // Add the best pair to the history.
      history.add(bestTriple);
      int i = (Integer)bestTriple.first();
      int m = (Integer)bestTriple.second();
      float imScore = (Float)bestTriple.third();

      System.out.println(" -> merging clusters " + i + " and " + m);

      // Sum all the feature vectors together between i and m.
      ClusterCell imCell = simMatrix.get(i, m);
      Map<String,Float> bestFeatSum = imCell.featureSum();
      featSums[i] = bestFeatSum;

      // Increment cluster size, save score.
      clusterSizes[i] += clusterSizes[m];
      clusterScores[i] = (Float)bestTriple.third();

      // Deactivate the second cluster, now merged with the first.
      actives[m] = false;
      // Delete vector m from the matrix (to save memory).
      simQueues[m].clear();
      simQueues[m] = null;
      simMatrix.clearRow(m);
      PriorityQueue iqueue = simQueues[i];
      iqueue.remove(imCell);

      // NOTE: Rather than checking every data point j.  We only need to check all j's
      //       that either (1) cluster i had a score with or (2) cluster m had a score with.
      /*
      SparseVector ivec = simMatrix.getRow(i);
      SparseVector mvec = simMatrix.getRow(m);
      Set<Integer> updateList = new HashSet(iVec.keySet());
      updateList.addAll(mvec.keySet());
      for( Integer j : updateList ) {
       */
      System.out.println("Updating all clusters...");

      // Update all remaining active cluster scores with this new cluster.
      int newnulls = 0;
      for( int j = 0; j < dataSize; j++ ) {
        if( actives[j] && i != j ) {
          // Merges features of i and m with j.
          Map<String,Float> featSum = Dimensional.sumFeatures(bestFeatSum, featSums[j]);
          float score = GroupAverageSimilarity.computeClusterSimilarity(featSum, clusterSizes[i], clusterSizes[j]);
          //	  System.out.println("i,j sizes = " + clusterSizes[i] + "," + clusterSizes[j]);

          //	  System.out.println("score " + i + "," + m + ",j=" + j + " = " + score);
          //	  System.out.println("  i=" + i + ",j=" + j + " = " + 
          //			     simMatrix.get((i<j) ? i : j, (i<j) ? j : i));
          //	  System.out.println("  m=" + m + ",j=" + j + " = " + 
          //			     simMatrix.get((j<m) ? j : m, (j<m) ? m : j));
          int first = (i<j) ? i : j;
          int second = (i<j) ? j : i;
          ClusterCell cell = simMatrix.get(first, second);
          if( cell != null ||
              (score >= _minClusteringScore &&
                  averageScoreForNewEdges(imScore, clusterScores[j], score,
                      clusterSizes[i], clusterSizes[j]) >= _minSimilarityScoreToSave) ) {
            //	    System.out.print("setting " + first + "," + second + " = " + score);
            if( cell == null ) {
              //	      System.out.print(" ...null");
              cell = new ClusterCell(second, score);
              //	      System.out.println(" ...put " + first + " " + second);
              simMatrix.put(first, second, cell);
              newnulls++;
            }
            else {
              //	      System.out.println(" ...not null i=" + i + " first=" + first + " cell=" + cell);
              if( i == first ) iqueue.remove(cell);
              else simQueues[first].remove(cell);
            }

            cell.setSim(score);
            cell.setFeatureSum(featSum);
            if( i == first ) iqueue.add(cell);
            else simQueues[first].add(cell);
          }
          // remove the mth cluster from the queue
          cell = simMatrix.get(j,m);
          if( cell != null ) simQueues[j].remove(cell);
        }
      }
      System.out.println("Added " + newnulls + " new similarity scores");
    }

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
    /*
    for( int i = 0; i < dataSize-1; i++ ) {
      // Only search rows that are active.
      if( actives[i] ) {
	for( int j = i+1; j < dataSize; j++ ) {
	  if( actives[j] ) {
	    //	    float score = matrix[i][j].sim();
	    ClusterCell cell = matrix.get(i,j);
	    if( cell != null ) {
	      float score = cell.sim();
	      if( score > bestSim ) {
		bestSim = score;
		best = new Pair(i, j);
	      }
	    }
	  }
	}
      }
    }
     */
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

  public List<Triple> efficientCluster(Collection<String> alldata, ScoreCache scores, int similarity) {
    return efficientCluster(alldata, scores, similarity, null);
  }

  /**
   * Given a set of data, clusters the data!  O(n^2 * lg n), uses priority queues.
   * This sort of uses Manning's algorithm, found online at:
   * http://nlp.stanford.edu/IR-book/html/htmledition/hierarchical-agglomerative-clustering-1.html
   * However, the data is just a bunch of strings, and we are given a lookup table
   * that takes any two strings and returns the similarity score.  Hence, Manning's
   * algorithm optimizes feature-value representations, but this just looks up scores.
   * @param singlelink Use single-link scoring if true, otherwise group-average.
   * @return History of merges, represented as a sequence of Pairs.
   *         Each Triple is the i,j index of merging those two clusters, and their merge score.
   */
  public List<Triple> efficientCluster(Collection<String> alldata, ScoreCache scores, int similarity, List<Pair<String,String>> disallowConstraints) {
    int dataSize = alldata.size();
    String[] data = new String[dataSize];
    data = alldata.toArray(data);
    // Size of each cluster.
    int clusterSizes[] = new int[data.length];
    // Current score for each cluster.
    float clusterScores[] = new float[data.length];
    // The clusters as they are built.
    Set<String> clusters[] = new HashSet[data.length];
    // True if the index represents an active cluster.
    boolean[] actives = new boolean[dataSize];

    Util.reportMemory();
    System.out.println(dataSize + 
        " items to cluster, total size: " + (dataSize*dataSize) +
        " doubles " + ((double)dataSize * (double)dataSize));

    // INITIALIZE matrix to hold all cluster similarities.
    SparseMatrix simMatrix = new SparseMatrix(dataSize);
    PriorityQueue<ClusterCell> simQueues[] = new PriorityQueue[dataSize];
    for( int i = 0; i < dataSize; i++ ) {
      actives[i] = true;
      clusterSizes[i] = 1;
      clusters[i] = new HashSet<String>();
      clusters[i].add(data[i]);
      // Don't specify 'datasize' capacity for the queue.
      // We keep only high scoring similarities, which is very low.
      simQueues[i] = new PriorityQueue<ClusterCell>(); 
    }

    System.out.println("Created priority queues for entities.");
    Util.reportMemory();
    long startTime = System.currentTimeMillis();

    // INITIALIZE pairwise similarities : O(n^2)*O(simCompare)
    Set<Integer> hasNeighbor = new HashSet<Integer>();
    for( int i = 0; i < dataSize-1; i++ ) {
      PriorityQueue<ClusterCell> queue = simQueues[i];
      for( int j = i+1; j < dataSize; j++ ) {
        float score = scores.getScore(data[i], data[j]);
        if( score >= _minSimilarityScoreToSave ) {
          if( disallowConstraints == null || validClusterMerge(data[i], data[j], disallowConstraints) ) {
            ClusterCell cell = new ClusterCell(score);
            cell.setIndex(j);
            //            System.out.println("hiercluster " + data[i] + "," + data[j] + " =\t" + cell.sim());
            queue.add(cell);
            simMatrix.put(i, j, cell);
            hasNeighbor.add(i);
            hasNeighbor.add(j);
          }
          //          else System.out.println("CLUSTER invalid merge " + data[i] + " and " + data[j]);
        }
      }
      if( i % 100 == 0 ) {
        System.out.println("i = " + i);
        Util.reportMemory();
        BasicEventAnalyzer.reportElapsedTime(startTime);
      }
    }

    System.out.println("Finished initial pairwise similarities.");
    Util.reportMemory();

    // Inactivate any objects that don't have near neighbors.
    int loners = 0;
    for( int i = 0; i < dataSize; i++ ) {
      if( !hasNeighbor.contains(i) ) {
        actives[i] = false;
        loners++;
        System.out.println("loner " + data[i]);
      }
    }
    System.out.println(loners + " loners inactivated.");
    hasNeighbor.clear();
    hasNeighbor = null;

    // Sequence history of cluster merges.
    List<Triple> history = new ArrayList<Triple>();

    // Perform N-1 merges, leaving one cluster over all data.
    for( int k = 1; k < dataSize-1; k++ ) {
      if( k % 100 == 0 ) {
        Util.reportMemory();
        BasicEventAnalyzer.reportElapsedTime(startTime);
      }

      // O(n^2) - search through matrix for best cell
      //      System.out.println("Searching for best...");
      Triple bestTriple = bestScore(simQueues, actives, _minClusteringScore);
      //      System.out.print("done.");
      if( bestTriple == null ) break;

      // Add the best pair to the history.
      history.add(bestTriple);
      int i = (Integer)bestTriple.first();
      int m = (Integer)bestTriple.second();
      float imScore = (Float)bestTriple.third();

      System.out.println(" -> merging clusters " + i + " " + data[i] + " and " + m + " " + data[m] + " score=" + imScore);

      // Sum all the feature vectors together between i and m.
      ClusterCell imCell = simMatrix.get(i, m);

      // Increment cluster size, save score.
      clusterSizes[i] += clusterSizes[m];
      clusterScores[i] = (Float)bestTriple.third();
      clusters[i].addAll(clusters[m]);

      // Deactivate the second cluster, now merged with the first.
      actives[m] = false;
      // Delete vector m from the matrix (to save memory).
      //      System.out.println("clearing queue " + m + " with " + simQueues[m].size() + " objects");
      simQueues[m].clear();
      simQueues[m] = null;
      simMatrix.clearRow(m);
      PriorityQueue<ClusterCell> iqueue = simQueues[i];
      iqueue.remove(imCell);

      // Update all remaining active cluster scores with this new cluster.
      int newnulls = 0;
      for( int j = 0; j < dataSize; j++ ) {
        if( actives[j] && i != j ) {
          // Merges features of i and m with j.
          float score = 0.0f;

          // Don't allow clusters to merge if they violate hard constraints.
//          if( disallowConstraints == null || validClusterMerge(clusters[i], clusters[j], disallowConstraints) ) {

            if( similarity == ClusterUtil.SINGLE_LINK )
              score = SingleLinkSimilarity.computeClusterSimilarity(clusters[i], clusters[j], scores);
            else if( similarity == ClusterUtil.MIN_LINK )
              score = SingleLinkSimilarity.computeMinClusterSimilarity(clusters[i], clusters[j], scores);
            else if( similarity == ClusterUtil.NEW_LINK )
              score = SingleLinkSimilarity.computeNewLinksClusterSimilarity(clusters[i], clusters[j], scores, false, false);
            else if( similarity == ClusterUtil.NEW_LINK_WITH_CONNECTION_PENALTY )
              score = SingleLinkSimilarity.computeNewLinksClusterSimilarity(clusters[i], clusters[j], scores, true, false);
            else
              score = GroupAverageSimilarity.computeClusterSimilarity(imScore, clusterSizes[i],
                  clusterScores[j], clusterSizes[j],
                  clusters[i], clusters[j],
                  scores);
//          }
//          else System.out.println("Disallowed " + clusters[i] + " with " + clusters[j]);

          if( disallowConstraints != null ) {
//            float prevscore = score;
            int invalids = validClusterMerge(clusters[i], clusters[j], disallowConstraints);
            if( invalids > 0 ) {
//              System.out.println("sizei=" + clusters[i].size() + " sizej=" + clusters[j].size());
//              System.out.println("  sizei=" + (float)clusters[i].size()*.5f + " sizej=" + (float)clusters[j].size()*.5f);

              // Don't allow any merge where there are more violated constraints than items in a cluster.
              if( invalids > (float)clusters[i].size()*.5f || invalids > (float)clusters[j].size()*.5f )
                score = 0.0f;
//              else if( invalids > 0 ) {
//                System.out.println("  score=" + score);
//                float factor = (float)Math.pow(0.8f, invalids);
//                System.out.println("  factor=" + factor);
//                score = score * factor;
//                System.out.println("  score=" + score);
//              }
//              System.out.println("# invalids = " + invalids + " score=" + prevscore + " .5^=" + Math.pow(0.5f, invalids) + " now=" + score);
            }
          }

          // If score is significant enough, update j.
          int first = (i<j) ? i : j;
          int second = (i<j) ? j : i;
          ClusterCell cell = simMatrix.get(first, second);
          if( cell != null || score >= _minClusteringScore ) {
            //	       averageScoreForNewEdges(imScore, clusterScores[j], score,
            //				       clusterSizes[i], clusterSizes[j]) >= _minSimilarityScoreToSave) ) {
            //	    System.out.print("setting " + first + "," + second + " = " + score);
            if( cell == null ) {
              //	      System.out.print(" ...null");
              cell = new ClusterCell(second, score);
              //	      System.out.println(" ...put " + first + " " + second);
              simMatrix.put(first, second, cell);
              newnulls++;
            }
            else {
              //	      System.out.println(" ...not null i=" + i + " first=" + first + " cell=" + cell);
              if( i == first ) iqueue.remove(cell);
              else simQueues[first].remove(cell);
            }

            cell.setSim(score);
            if( i == first ) iqueue.add(cell);
            else simQueues[first].add(cell);
          }
          // remove the mth cluster from the queue
          cell = simMatrix.get(j,m);
          if( cell != null ) simQueues[j].remove(cell);
        }
      }
      //      System.out.println("Added " + newnulls + " new similarity scores");
    }

    return history;
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
    for( Map.Entry<String,Float> entry : m.entrySet() )
      System.out.println(entry.getKey() + " " + entry.getValue());
  }

  /**
   * Given a list of string pairs that are not allowed to be in the same cluster
   * together, check if the two strings match a pair.  Return false if they match.
   * @param disallow Pairs of String objects...pairs that can't be in the same cluster.
   */
  private boolean validClusterMerge(String str1, String str2, List<Pair<String,String>> disallow) {
    for( Pair<String,String> pair : disallow ) {
      String first = pair.first();
      String second = pair.second();
      if( str1.equals(first) && str2.equals(second) ) return false;
      if( str2.equals(first) && str1.equals(second) ) return false;
    }
    return true;
  }

  private int validClusterMerge(Set<String> cluster1, Set<String> cluster2, List<Pair<String,String>> disallow) {
    int invalids = 0;
    for( String token1 : cluster1 ) {
      for( String token2 : cluster2 ) {
        if( !validClusterMerge(token1, token2, disallow) )
          invalids++;
      }
    }
    return invalids;
  }

  public void testSimilarities() {

    HashMap<String,Float> datum = new HashMap<String, Float>();
    datum.put("red", 3.0f);
    datum.put("blue", 2.0f);
    datum.put("green", 1.0f);

    HashMap<String,Float> datum2 = new HashMap<String, Float>();
    datum2.put("red", 6.0f);
    datum2.put("blue", 4.0f);
    datum2.put("green", 2.0f);

    HashMap<String,Float> datum3 = new HashMap<String, Float>();
    //    datum3.put("red", 3.0f);
    datum3.put("blue", 3.0f);
    datum3.put("green", 1.0f);

    // These similarities should be the same as the length normalized
    // similarities with just the dot product.
    System.out.println("1,2 sim = " + GroupAverageSimilarity.computeSimilarity(datum, datum2));
    System.out.println("1,3 sim = " + GroupAverageSimilarity.computeSimilarity(datum, datum3));

    Dimensional.lengthNormalize(datum);
    Dimensional.lengthNormalize(datum2);
    Dimensional.lengthNormalize(datum3);
    System.out.print("datum: "); printHashMap(datum);
    System.out.print("datum2: "); printHashMap(datum2);
    System.out.print("datum3: "); printHashMap(datum3);
    System.out.println("1,2 sim = " + Dimensional.dotProduct(datum, datum2));
    System.out.println("1,3 sim = " + Dimensional.dotProduct(datum, datum3));
  }

  public void test() {
    Vector<Map<String,Float>> data = new Vector();
    Map<String,Float> datum;

    datum = new HashMap();
    data.add(datum);
    datum.put("red", 3.0f);
    datum.put("blue", 2.0f);
    datum.put("green", 1.0f);

    datum = new HashMap();
    data.add(datum);
    //    datum.put("red", 3.0f);
    datum.put("blue", 3.0f);
    datum.put("green", 1.0f);

    datum = new HashMap();
    data.add(datum);
    datum.put("red", 6.0f);
    datum.put("blue", 4.0f);
    datum.put("green", 2.0f);

    datum = new HashMap();
    data.add(datum);
    datum.put("red", 3.0f);
    datum.put("blue", 8.0f);
    datum.put("green", 1.0f);

    datum = new HashMap();
    data.add(datum);
    //    datum.put("red", 3.0f);
    datum.put("blue", 2.0f);
    datum.put("green", 1.0f);

    datum = new HashMap();
    data.add(datum);
    datum.put("red", 1.0f);
    datum.put("blue", 6.0f);
    datum.put("green", 2.0f);

    Vector<Triple> order = simpleCluster(data);
    for( Triple pair : order ) {
      System.out.println("**" + pair);
    }
  }

  public void testDiff() {
    float oldsim = 0.8426059f;
    float othersim = 0.0f;
    float[] newsims = { 0.6319544f, 0.6332774f, 0.63309866f, 0.70284134f };
    int imsize = 7;
    int jsize = 1;

    for( float newsim : newsims ) {
      float avgdiff = averageScoreForNewEdges(oldsim, othersim, newsim, imsize, jsize);
      System.out.println(newsim + " averaged " + avgdiff);
    }    

  }

  public static void main(String[] args) {
    HierarchicalClustering cluster = new HierarchicalClustering();
    //    cluster.testDiff();
    //    cluster.testSimilarities();
    cluster.test();
  }
}
