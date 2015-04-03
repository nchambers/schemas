package nate.cluster;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import nate.narrative.ScoreCache;
import nate.util.Dimensional;
import nate.util.SortableObject;
import nate.util.Triple;


public class ClusterUtil {
  public static final int MIN_LINK = 0;
  public static final int SINGLE_LINK = 1;
  public static final int AVERAGE_LINK = 2;
  public static final int NEW_LINK = 3;
  public static final int NEW_LINK_WITH_CONNECTION_PENALTY = 4;

  ClusterUtil() { }


  /**
   * Cosine similarity.
   */
  public static float computeCosine(Map<String,Float> x, Map<String,Float> y) {
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

    if( xmag != 0.0f && ymag != 0.0f ) {
      double denom = Math.sqrt(xmag) * Math.sqrt(ymag);
      return dot / (float)denom;
    } 
    else return 0.0f;
  }


  /**
   * Compute the average feature-value map over all members in a cluster,
   * given the feature vectors of the cluster members.
   * @return The centroid of the datums.
   */
  public static Map<String,Float> computeCentroid(Collection<Map<String,Float>> data) {
    Map<String,Float> average = new HashMap<String, Float>();

    // Loop over the features for this cluster object.
    int size = 0;
    for( Map<String,Float> datum : data ) {
      for( Map.Entry<String,Float> entry : datum.entrySet() ) {
        String feat = entry.getKey();
        Float value = entry.getValue();

        if( average.containsKey(feat) )
          average.put(feat, average.get(feat)+value);
        else average.put(feat, value);
      }
      size++;
    }

    // Normalize the feature value counts by the size of the cluster.
    for( Map.Entry<String,Float> entry : average.entrySet() )
      entry.setValue(entry.getValue() / size);
    Dimensional.lengthNormalize(average);

    return average;
  }


  /**
   * Finds data points that are outliers, i.e. not near any other
   * data points (or at least, not a sufficient number to be interesting).
   * @return A vector of integer indices of points in the given data array.
   */
  public static Set<Integer> findOutliers(Map<String,Float>[] data, 
      float minSimilarityScoreToSave, int minNeighborsNeeded) {
    int dataSize = data.length;
    int[] neighbors = new int[dataSize];

    for( int i = 0; i < dataSize-1; i++ ) {
      for( int j = i+1; j < dataSize; j++ ) {
        float score = Dimensional.dotProduct(data[i], data[j]);
        if( score >= minSimilarityScoreToSave ) {
//          System.out.println(i + "," + j + " = " + score);
          neighbors[i]++;
          neighbors[j]++;
        }
      }
    }

    // Return any data point that doesn't have enough neighbors.
    Set<Integer> outliers = new HashSet<Integer>();
    for( int i = 0; i < dataSize; i++ ) {
      if( neighbors[i] < minNeighborsNeeded )
        outliers.add(i);
    }
    return outliers;
  }

  public static List<List<String>> reconstructClusters(List<Triple> order, Collection<String> names) {
    Vector<Set<Integer>> intClusters = reconstructClusters(order, null, 0, 0);

    List<List<String>> allClusters = new ArrayList<List<String>>();
    for( Set<Integer> cluster : intClusters ) {
      List<String> strCluster = clusterIdsToStrings(cluster, names);
      allClusters.add(strCluster);
    }
    return allClusters;
  }
  
  public static Vector<Set<Integer>> reconstructClusters(List<Triple> order) {
    return reconstructClusters(order, null, 0, 0);
  }
  public static Vector<Set<Integer>> reconstructClusters(List<Triple> order, Collection<String> names, int printFrequency) {
    return reconstructClusters(order, names, printFrequency, 0);
  }

  /**
   * Take the stepwise pair merges and reconstruct the full clusters.
   * Each pair contains the two main indices of each cluster.  The second
   * number is the cluster that is merged into the first.
   * @param order The vector returned by the clustering algorithm, merge orders.
   * @param names Only needed if you want to print them as they're rebuilt...list of names.
   * @param printFrequency The given integer tells the function to print the clusters every 
   *                       nth merge where n is the printFrequency.  Set to zero for no printing.
   * @param stopAtClusterSize The clustering algorithm will stop when a cluster reaches this size
   *                          or larger, returning all clusters at that moment in time.
   *                          Set to zero to reconstruct entire order.
   * @return A Vector of Sets, each set is a cluster of Integer IDs.
   */
  public static Vector<Set<Integer>> reconstructClusters(List<Triple> order, Collection<String> names, int printFrequency, int stopAtClusterSize) {
    Map<Integer, Set<Integer>> clusters = new HashMap<Integer, Set<Integer>>();

    int i = 0;
    for( Triple merge : order ) {
      Set<Integer> cluster = clusters.get(merge.first());
      if( cluster == null ) {
        cluster = new HashSet<Integer>();
        cluster.add((Integer)merge.first());
        clusters.put((Integer)merge.first(), cluster);
      }

      Set<Integer> cluster2 = clusters.get(merge.second());
      if( cluster2 == null ) {
        cluster.add((Integer)merge.second());
      }
      else {
        // Copy the elements in the second cluster to the first one.
        cluster.addAll(cluster2);
        // Copy the main element of the second cluster too!
        cluster.add((Integer)merge.second());
        // Now get rid of it the entire second cluster.
        clusters.remove((Integer)merge.second());
      }

      // Stop now if cluster is bigger than are max size.
      if( stopAtClusterSize > 0 && cluster.size() >= stopAtClusterSize )
        break;

      // Print them for debugging.
      if( printFrequency > 0 && i % printFrequency == 0 ) {
        System.out.println("--");
        for( Map.Entry<Integer,Set<Integer>> entry : clusters.entrySet() )
          printCluster(names, entry.getValue());
        System.out.println(merge.third());
      }
      i++;
    }

    // Add the key maps to their own clusters.
    Vector<Set<Integer>> finalClusters = new Vector<Set<Integer>>();
    for( Map.Entry<Integer, Set<Integer>> entry : clusters.entrySet() ) {
      Set<Integer> cluster = entry.getValue();
      cluster.add(entry.getKey());
      finalClusters.add(cluster);
    }
    return finalClusters;
  }


  public static void printCluster(Collection<String> names, Set<Integer> cluster) {
    String[] arr = new String[names.size()];
    arr = names.toArray(arr);

    // Print the integers.
    boolean first = true;
    for( Integer nameID : cluster ) {
      if( !first ) System.out.print(", ");
      System.out.print(arr[nameID]);
      first = false;
    }
    System.out.println();
  }

  /**
   * Take the stepwise cluster merges and reconstruct the full clusters.
   * Each triple contains the two main indices of each cluster.  The second
   * number is the cluster that is merged into the first.
   * @param names Can be any object that has a toString() function.
   * @param order The merging history of the cluster algorithm.
   */
  public static void printClusters(Vector<String> names, Vector<Triple> order) {
    Vector<Set<Integer>> clusters = reconstructClusters(order);

    for( Set<Integer> cluster : clusters ) {
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
      System.out.println("\n------");
    }
  }

  /**
   * Take the stepwise pair merges and reconstruct the full clusters.
   * Saves the resulting clusters to file.
   * Each pair contains the two main indices of each cluster.  The second
   * number is the cluster that is merged into the first.
   * @param names The list of entity names in order as read from the file 
   *              that was clustered.
   * @param order The history as returned by the clustering algorithm.
   * @param filename The path to the file to save in.
   */
  public static void saveClustersToFile(Vector<String> names, Vector<Triple> order,
      String filename) {
    Vector<Set<Integer>> clusters = reconstructClusters(order);
    saveFormedClustersToFile(names, clusters, filename);
  }

  /**
   * Takes a list of clusters (a cluster is a set of integers), and maps the integer
   * IDs to the given list of string entity names.  Saves the string names to file.
   * @param names The list of entity names in order as read from the file 
   *              that was clustered.
   * @param clusters The list of clusters of entity IDs
   * @param filename The path to the file to save in.
   */
  public static void saveFormedClustersToFile(Vector<String> names, 
      Collection<Set<Integer>> clusters, String filename) {
    try {
      BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
      for( Set<Integer> cluster : clusters ) {
        boolean first = true;
        for( Integer nameID : cluster ) {
          if( !first ) writer.write(", ");
          writer.write(names.get(nameID));
          first = false;
        }
        writer.write("\n");
      }
    } catch( IOException ex ) { ex.printStackTrace(); }
  }

  /**
   * Takes a list of clusters (a cluster is a set of integers), and maps the integer
   * IDs to the given list of string entity names.  Saves the string names to file.
   * @param names The list of entity names in order as read from the file 
   *              that was clustered.
   * @param clusters The list of clusters of entity IDs
   * @param filename The path to the file to save in.
   */
  public static void saveFormedClustersToFile(Vector<String> names, 
      Set<Integer>[] clusters, String filename) {
    try {
      BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
      for( Set<Integer> cluster : clusters ) {
        boolean first = true;
        for( Integer nameID : cluster ) {
          if( !first ) writer.write(", ");
          writer.write(names.get(nameID));
          first = false;
        }
        writer.write("\n");
      }
    } catch( IOException ex ) { ex.printStackTrace(); }
  }

  /**
   * Reads clusters from a file. One cluster per line and names of objects 
   * in clusters are separated by commas.
   * @param filename The path to the file to read.
   */
  public static Vector<Set<String>> clustersFromFile(String filename) {
    Vector<Set<String>> clusters = new Vector<Set<String>>();

    // Read clusters from file.
    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      String line;
      while( (line = in.readLine()) != null ) {
        Set<String> cluster = new HashSet<String>();
        String parts[] = line.split(",");
        for( String part : parts )
          cluster.add(part.trim());
        clusters.add(cluster);
      }
    } catch( IOException ex ) { ex.printStackTrace(); }

    return clusters;
  }


  /**
   * Gives the complete edges summation score of the cluster.
   */
  public static double computeClusterScoreFull(Collection<String> cluster, ScoreCache scores) {
    String[] arr = new String[cluster.size()];
    arr = cluster.toArray(arr);
    double score = 0.0;

    for( int i = 0; i < arr.length-1; i++ ) {
      for( int j = i+1; j < arr.length; j++ ) {
        score += (double)scores.getScore(arr[i], arr[j]);
      }
    }
    return score;
  }

  public static List<String> clusterIdsToStrings(Collection<Integer> ids, Collection<String> names) {
    String[] arr = new String[names.size()];
    arr = names.toArray(arr);

    List<String> strs = new ArrayList<String>();
    for( Integer id : ids ) 
      strs.add(arr[id]);
    return strs;
  }

  /**
   * Score each token in the cluster by the sum of its edge scores within the cluster, normalized
   * by the number of edges.
   * @return A map of the tokens and their scores.
   */
  public static Map<String,Double> scoreClusterTokens(Collection<String> cluster, ScoreCache scores) {
    String[] arr = new String[cluster.size()];
    arr = cluster.toArray(arr);
    Map<String,Double> sums = new HashMap<String, Double>();
    int numPairs = 0;

    // Initialize score array.
    for( String token : arr ) sums.put(token, 0.0);

    for( int i = 0; i < arr.length-1; i++ ) {
      String tokeni = arr[i];
      for( int j = i+1; j < arr.length; j++ ) {
        String tokenj = arr[j];
        double score = (double)scores.getScore(tokeni, tokenj);
//        System.out.println("--" + tokeni + " " + tokenj + " " + score + " " + sums.get(tokeni));
        sums.put(tokeni, sums.get(tokeni)+score);
        sums.put(tokenj, sums.get(tokenj)+score);
        numPairs++;
      }
    }
    
    // Normalize by the number of possible pairs.
    for( Map.Entry<String, Double> entry : sums.entrySet() ) {
      entry.setValue(entry.getValue() / numPairs);
    }
    
    return sums;
  }
  
  /**
   * Score each token in the cluster by the sum of its edge scores within the cluster.
   * Sort on these scores, return an array of strings.
   */
  public static String[] sortClusterTokens(Collection<String> cluster, ScoreCache scores) {
    //String[] arr = new String[cluster.size()];
    //arr = cluster.toArray(arr);
    //Map<Integer,Double> sums = new HashMap();

    Map<String,Double> sums = scoreClusterTokens(cluster, scores);
    
    // Sort the scores.
    SortableObject[] sorted = new SortableObject[sums.size()];
    int i = 0;
    for( String token : sums.keySet() )
      sorted[i++] = new SortableObject(sums.get(token), token);
    Arrays.sort(sorted);

    // Put in an array of Strings, not SortableObjects.
    String[] strs = new String[sorted.length];
    i = 0;
    for( SortableObject obj : sorted )
      strs[i++] = (String)obj.key();

    return strs;
  }

}
