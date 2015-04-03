package nate.cluster;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Vector;
import java.util.Collection;
import java.util.Random;

import nate.util.Dimensional;
import nate.util.Util;


/**
 * A Hierarchical Agglomerative Clustering algorithm that uses group
 * average similarity for cluster comparisons.
 *
 * NOTE: KMeans can only work with feature-value vector representations.
 */
public class KMeans {
  private int _k = 5;
  private int _maxLoops = 20;
  public float _minSimilarityScoreToSave = 0.3f;
  public int _minNeighborsNeeded = 2;

  public KMeans(int k) {
    _k = k;
  }

  public Set<Integer>[] cluster(Collection<Map<String,Float>> alldata) {
    return cluster(null, alldata);
  }

  public Set<Integer>[] cluster(List<String> names, Collection<Map<String,Float>> alldata) {
    return cluster(names, alldata, true);
  }

  /**
   * The k-means clustering algorithm.
   * @param names The list of names, one per data item. (DEBUGGING only)
   * @param alldata The list of feature-values for each data item.
   * @param removeOutliers If true, performs O(n^2) search to remove all points
   *                       that have no near neighbors, before clustering.
   * @return An array of clusters, each is a set of Integers...the indices
   *         for the objects in the given alldata collection.
   */
  public Set<Integer>[] cluster(List<String> names, Collection<Map<String,Float>> alldata, boolean removeOutliers) {
    int dataSize = alldata.size();
    Map<String,Float>[] data = new Map[dataSize];
    data = alldata.toArray(data);
    // Length normalize the vectors.
    Dimensional.lengthNormalize(data);

    Set<Integer> outliers = new HashSet<Integer>();
    if( removeOutliers ) {
      System.out.println("**Searching for outliers out of " + dataSize);
      outliers = ClusterUtil.findOutliers(data, _minSimilarityScoreToSave,
          _minNeighborsNeeded);
      //    Set<Integer> outliers = new HashSet();
      System.out.println("**Found " + outliers.size() + " outliers");
      //    for( Integer index : outliers ) 
      //      System.out.println(index + " " + names.elementAt(index));
    }

    // Create cluster vectors.
    Set<Integer> clusters[] = new HashSet[_k];
    for( int i = 0; i < clusters.length; i++ ) clusters[i] = new HashSet<Integer>();
    Set<Integer> zeros = new HashSet<Integer>();

    // Choose random centroid starting points.
    Map<String,Float>[] centroids = randomStartingPoints(_k, data, outliers);

    int loops = 0;
    float loopChange = 1.0f;
    while( loops < _maxLoops && loopChange > .00005f ) {
      //      System.out.println("**Loop " + loops);
      //      BasicEventAnalyzer.reportElapsedTime(startTime);
      //      BasicEventAnalyzer.reportMemory();
      //      printCentroids(centroids);

      // Clear the clusters.
      for( Set<Integer> cluster : clusters ) cluster.clear();
      zeros.clear();

      // Assign each point to a centroid.  O(k*n)
      int datumIndex = 0;
      for( Map<String,Float> datum : data ) {
        if( !outliers.contains(datumIndex) ) {
          int nearest = nearestCentroid(centroids, datum);
          if( nearest == -1 ) zeros.add(datumIndex);
          else clusters[nearest].add(datumIndex);
        }
        datumIndex++;
      }

      // Recalculate centroids based on their current clusters.
      loopChange = normalizeCentroids(centroids, clusters, data);
      //      System.out.println("**Centroids average feature disruption: " + loopChange);
      loops++;
      System.out.print(".");
    }

    System.out.println("KMeans finished after " + loops + " loops.");
    // Put the "zero" datums into one of the clusters (first one...).
//    System.out.println("Adding " + zeros.size() + " zero vectors to the first cluster");
    clusters[0].addAll(zeros);
    
    // DEBUGGING
    printCentroids(centroids);

    return clusters;
  }

  /**
   * Computes the average feature-value map over all members in a cluster, and
   * sets each centroid to its new average.
   * NOTE: This is destructive, altering the centroids array that is given.
   * @return The average change in feature values across all centroid features.
   */
  private float normalizeCentroids(Map<String,Float>[] centroids,
      Set<Integer>[] clusters, Map<String,Float>[] data) {
    int totalFeats = 0;
    float diff = 0.0f;
    int i = 0;
    for( Set<Integer> cluster : clusters ) {
      Map<String,Float> average = new HashMap<String,Float>();

      for( Integer index : cluster ) {
        Map<String,Float> datum = data[index];

        // Loop over the features for this cluster object.
        for( Map.Entry<String,Float> entry : datum.entrySet() ) {
          String feat = entry.getKey();
          Float value = entry.getValue();

          if( average.containsKey(feat) )
            average.put(feat, average.get(feat)+value);
          else average.put(feat, value);
        }
      }

      // Normalize the feature value counts by the size of the cluster.
      for( Map.Entry<String,Float> entry : average.entrySet() )
        entry.setValue(entry.getValue() / (float)cluster.size());
      Dimensional.lengthNormalize(average);

      // Calculate change from old centroid to new.
      Map<String,Float> origCentroid = centroids[i];
      for( Map.Entry<String,Float> entry : average.entrySet() ) {
        Float origValue = origCentroid.get(entry.getKey());
        if( origValue == null ) diff += entry.getValue();
        else diff += Math.abs(entry.getValue() - origValue);
        totalFeats++;
      }
      for( Map.Entry<String,Float> entry : origCentroid.entrySet() )
        if( average.get(entry.getKey()) == null ) diff += entry.getValue();

//      System.out.println("centroid " + i + " feat-size " + average.size() + " cluster-size " + cluster.size());
      centroids[i++] = average;
    }

    return diff / (float)totalFeats;
  }

  /**
   * Finds the centroid that is closest to the given datum in the feature space.
   * Assumes the datum and centroids are length normalized.
   * Computes the cosine similarity by just doing the dot product.
   * @return The array index of the nearest centroid.
   */
  private int nearestCentroid(Map<String,Float>[] centroids, Map<String,Float> datum) {
    int k = centroids.length;
    int best = -1;
    float bestSim = -1.0f;

    for( int i = 0; i < k; i++ ) {
      float sim = Dimensional.dotProduct(centroids[i], datum);
      //      System.out.print(" i=" + i + " sim=" + sim);
      if( sim > bestSim ) {
        bestSim = sim;
        best = i;
      }
    }
    //    System.out.println(" best=" + best);
    // If our vector had no features in common, it's best similarity is zero!
    if( bestSim == 0.0f ) return -1;
    else return best;
  }

  /**
   * Chooses k random data points from the given array, and treats those as the
   * starting centroids in k-means.
   * @param ignoreList Don't choose points in this list.
   */
  public static Map<String,Float>[] randomStartingPoints(int k, Map<String,Float>[] data,
      Set<Integer> ignoreList) {
    int n = data.length;
    Set<Integer> randomIndices = new HashSet<Integer>(k);
    Map<String,Float>[] centroids = new HashMap[k];

    Random rand = new Random();
    for( int i = 0; i < k; i++ ) {
      // Generate a random index.
      int chosen = rand.nextInt(n);
      // Keep generating until its a new one.
      while( randomIndices.contains(chosen) || ignoreList.contains(chosen) )
        chosen = rand.nextInt(n);
      randomIndices.add(chosen);
      // Copy that index's map to be a centroid.
      centroids[i] = createCopy(data[chosen]);
//      System.out.println("Random index " + chosen + " feat-size " + centroids[i].size());
    }
    return centroids;
  }

  /**
   * Creates a duplicate Map with the same key-value pairs.
   */
  public static Map<String,Float> createCopy(Map<String,Float> themap) {
    Map<String,Float> copy = new HashMap<String,Float>();
    for( Map.Entry<String,Float> entry : themap.entrySet() )
      copy.put(entry.getKey(), entry.getValue());
    return copy;
  }

  /**
   * Print the centroids' key/value pairs. For debugging.
   */
  private void printCentroids(Map<String,Float>[] centroids) {
    int ii = 0;
    for( Map<String,Float> centroid : centroids ) {
      System.out.println("Centroid " + ii);
      for( String key : Util.sortKeysByFloatValues(centroid) )
        System.out.println(key + "\t" + centroid.get(key));
      ii++;
    }
  }


  /**
   * Take the stepwise cluster merges and reconstruct the full clusters.
   * Each triple contains the two main indices of each cluster.  The second
   * number is the cluster that is merged into the first.
   */
  public static void printClusters(List<String> names, Set<Integer>[] clusters) {
    for( Set<Integer> cluster : clusters )
      printCluster(names, cluster);
  }

  public static void printCluster(List<String> names, Set<Integer> cluster) {
    // Print the integers.
    boolean first = true;
    for( Integer nameID : cluster ) {
      if( !first ) System.out.print(", ");
      System.out.print(nameID);
      first = false;
    }
    System.out.println();

    // Print the names.
    first = true;
    for( Integer nameID : cluster ) {
      if( !first ) System.out.print(", ");
      System.out.print(names.get(nameID));
      first = false;
    }
    System.out.println();
  }

  public void test() {
    Vector<Map<String,Float>> data = new Vector();
    Map<String,Float> datum;

    datum = new HashMap<String,Float>();
    data.add(datum);
    datum.put("red", 3.0f);
    datum.put("blue", 2.0f);
    datum.put("green", 1.0f);

    datum = new HashMap<String,Float>();
    data.add(datum);
    //    datum.put("red", 3.0f);
    datum.put("blue", 3.0f);
    datum.put("green", 1.0f);

    datum = new HashMap<String,Float>();
    data.add(datum);
    datum.put("red", 6.0f);
    datum.put("blue", 4.0f);
    datum.put("green", 1.5f);

    datum = new HashMap<String,Float>();
    data.add(datum);
    datum.put("red", 3.0f);
    datum.put("blue", 8.0f);
    datum.put("green", 1.0f);

    datum = new HashMap<String,Float>();
    data.add(datum);
    //    datum.put("red", 3.0f);
    datum.put("blue", 2.0f);
    datum.put("green", 1.0f);

    datum = new HashMap<String,Float>();
    data.add(datum);
    datum.put("red", 1.0f);
    datum.put("blue", 6.0f);
    datum.put("green", 2.0f);

    Set<Integer>[] clusters = cluster(null, data);
    for( Set<Integer> cluster : clusters ) {
      System.out.println("***");
      for( Integer index : cluster ) {
        System.out.println(data.elementAt(index));
      }
    }
  }


  public static void main(String[] args) {
    KMeans cluster = new KMeans(3);
    cluster.test();
  }
}

