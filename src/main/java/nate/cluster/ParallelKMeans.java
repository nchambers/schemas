package nate.cluster;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Vector;
import java.util.Collection;
import java.util.Random;

import nate.util.Dimensional;
import nate.util.Directory;
import nate.util.Locks;
import nate.util.Util;


/**
 * The Parallel KMeans clustering algorithm. Intended to be run in parallel with multiple instances
 * of this class. All instances share the same centroids. This is not multi-threaded, but intended
 * to be separate processes.
 *
 * NOTE: KMeans can only work with feature-value vector representations.
 */
public class ParallelKMeans {
  public static String centroidDir = "pkmeans-centroids";
  public static String locksDir = "pkmeans-locks";
  public String finalCentroidsPath = null;
  
  private int dotc = 0, dotadd = 0;
  Random rand = new Random();

  
  private int numberProcesses = 1;
  
  private int _k = 5;
  private int _maxLoops = 10;
  private float _minSimilarityScoreToSave = 0.3f;
  private int _minNeighborsNeeded = 2;
  
  public Map<String,Float>[] centroids; // set at the very end after clustering completes
  

  public ParallelKMeans(int k, int numProc) {
    _k = k;
    numberProcesses = numProc;
   
    // Create locks and centroids directories.
    if( !Directory.fileExists(locksDir) )
      Directory.createDirectory(locksDir);
    if( !Directory.fileExists(centroidDir) )
      Directory.createDirectory(centroidDir);
  }

  public Set<Integer>[] cluster(final Collection<Map<String,Float>> alldata) {
    return cluster(null, alldata);
  }

  public Set<Integer>[] cluster(final List<String> names, final Collection<Map<String,Float>> alldata) {
    return cluster(names, alldata, false);
  }

  /**
   * The k-means clustering algorithm.
   * 
   * FILES CREATED BY THIS METHOD
   * centroidL-D : L is the loop number and D is the ID of this process.
   *               This file contains the recomputed centroids by this process' data after datum assignment to centroids.
   * centroidL-D-finished : Empty file that appears when the above file is finished writing.
   * 
   * centroidL-averaged : File with the centroids that result from averaging all process centroids.
   *                      L is the loop number that has just completed and these averaged centroids resulted from it.
   * centroidL-averaged-finished : Empty file indicating the above file is finished writing.                    
   * 
   * @param names The list of names, one per data item. (DEBUGGING only)
   * @param alldata The list of feature-values for each data item.
   * @param removeOutliers If true, performs O(n^2) search to remove all points
   *                       that have no near neighbors, before clustering.
   * @return An array of clusters, each is a set of Integers...the indices
   *         for the objects in the given alldata collection.
   */
  public Set<Integer>[] cluster(final List<String> names, final Collection<Map<String,Float>> alldata, boolean removeOutliers) {
    int dataSize = alldata.size();
    int tenthOfData = dataSize / 10;
    Map<String,Float>[] data = new Map[dataSize];
    data = alldata.toArray(data);
    // Length normalize the vectors.
    Dimensional.lengthNormalize(data);

    Set<Integer> outliers = new HashSet<Integer>();
    if( removeOutliers ) {
      System.out.println("**Searching for outliers out of " + dataSize);
      outliers = ClusterUtil.findOutliers(data, _minSimilarityScoreToSave, _minNeighborsNeeded);
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
    Map<String,Float>[] centroids = getInitialCentroids(_k, data, outliers);
//    System.out.println("INITIAL CENTROIDS");  
//    printCentroids(centroids);
    
    System.out.println("ParallelKMeans about to loop over " + data.length + " datums.");
    
    boolean keepLooping = true;
    int loops = 0;
    while( loops < _maxLoops && keepLooping ) {
      long loopStartTime = System.currentTimeMillis();

      int size = 0;
      for( Map<String,Float> centroid : centroids )
        size += centroid.size();
      System.out.println(centroids.length + " centroids with " + size + " entries across all of them.");

      size = 0;
      for( Map<String,Float> datum : data )
        size += datum.size();
      System.out.println(data.length + " datums with " + size + " entries across all of them.");

      System.out.println("**Loop " + loops);
      //      BasicEventAnalyzer.reportElapsedTime(startTime);
      //      BasicEventAnalyzer.reportMemory();
      //      printCentroids(centroids);

      // Clear the clusters.
      for( Set<Integer> cluster : clusters ) cluster.clear();
      zeros.clear();

      long startTime = System.currentTimeMillis();

      // Assign each point to a centroid.  O(k*n)
      dotc = 0; dotadd = 0;
      int datumIndex = 0;
      for( Map<String,Float> datum : data ) {
        if( !outliers.contains(datumIndex) ) {
          int nearest = nearestCentroid(centroids, datum);
//          System.out.println(datum + "\tNEAREST=" + nearest);
          if( nearest == -1 ) zeros.add(datumIndex);
          else clusters[nearest].add(datumIndex);
        }
        datumIndex++;

        // Debugging: progress bar over data
        if( datumIndex == 1 ) System.out.print("|-");
        if( datumIndex % tenthOfData == 0 ) System.out.print("-");
      }
      System.out.println("|");
      System.out.println("Assigning my data points to centroids took this long: ");
      Util.reportElapsedTime(startTime);
//      System.out.println("We checked this many features:\t" + dotc);
//      System.out.println("We multiplied this many features:\t" + dotadd);
      
      size = 0;
      for( Map<String,Float> centroid : centroids )
        size += centroid.size();
      System.out.println(centroids.length + " centroids before normalizing with " + size + " entries across all of them.");
      
      // Recalculate centroids based on their current clusters.
      startTime = System.currentTimeMillis();
      recomputeCentroids(centroids, clusters, data);
      System.out.println("Normalizing centroids took this long: ");
      Util.reportElapsedTime(startTime);

//      size = 0;
//      for( Map<String,Float> centroid : centroids )
//        size += centroid.size();
//      System.out.println(centroids.length + " centroids after normalizing with " + size + " entries across all of them.");
      
      // Save the new centroids to disk for all processes to read.
      int id = 0;
      while( !Locks.getLock("centroid" + loops + "-" + id, locksDir) ) { id++; }
      String outpath          = centroidDir + File.separator + "centroid" + loops + "-" + id;
      printCentroids(centroids, new File(outpath));
      // Create a file saying you're finished, so the next process doesn't read it too early.
      Directory.touch(centroidDir, "centroid" + loops + "-" + id + "-finished");
      
      // Read all process centroids, average, return the new centroids.
      String outname = "centroid" + loops + "-averaged";
      if( Locks.getLock(outname, locksDir) ) {
        System.out.println("Reading all process centroids, and averaging...");
        centroids = readCentroidsAndAverage(loops);
        printCentroids(centroids, new File(centroidDir + File.separator + outname));
        // Now check for convergence.
        if( loops > 1 ) {
          Map<String,Float>[] prevCentroids = readCentroids(centroidDir + File.separator + "centroid" + (loops-1) + "-averaged");
          double diff = compareCentroids(prevCentroids, centroids);
          System.out.println("Centroid diff = " + diff);
          if( diff < .000005d ) Directory.touch(locksDir, "STOP");
        }
        // Tell all processes that they can now read the centroids.
        Directory.touch(centroidDir, "centroid" + loops + "-averaged-finished");
      }

      // Another process averaged, so we wait to read it.
      else {
        startTime = System.currentTimeMillis();
        System.out.println("Waiting for another process to average all centroids...");
        while( !Directory.fileExists(centroidDir + File.separator + "centroid" + loops + "-averaged-finished") ) { 
          try {
            Thread.sleep(250);
          } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
        }
        System.out.println("Waiting took this long: ");
        Util.reportElapsedTime(startTime);
        System.out.println("Reading in averaged centroids...");
        centroids = readCentroids(centroidDir + File.separator + "centroid" + loops + "-averaged");
      }

      size = 0;
      for( Map<String,Float> centroid : centroids )
        size += centroid.size();
      System.out.println(centroids.length + " centroids after reading them in.");
      
      // Should we stop? Check for a "stop" file.
      if( Directory.fileExists(locksDir + File.separator + "STOP") ) {
        System.out.println("Found stop signal: another process determined that the clustering has converged.");
        keepLooping = false;
      }
      
      loops++;
      System.out.println("Entire loop took this long: ");
      Util.reportElapsedTime(loopStartTime);
    }

    System.out.println("ParallelKMeans finished after " + loops + " loops.");
    // Put the "zero" datums into one of the clusters (first one...).
//    System.out.println("Adding " + zeros.size() + " zero vectors to the first cluster");
    clusters[0].addAll(zeros);
    
    // DEBUGGING
    System.out.println("FINAL CENTROIDS");
    printCentroids(centroids);
    this.centroids = centroids;

    return clusters;
  }

  /**
   * Calculates the average score change per feature across all centroids.
   * @param prevCentroids Centroids from previous clustering round.
   * @param centroids Centroids from the current clustering round.
   * @return The average feature value difference across all centroids.
   */
  private double compareCentroids(final Map<String,Float>[] prevCentroids, final Map<String,Float>[] centroids) {
    double diff = 0.0d;
    long totalFeats = 0;
    
    for( int ii = 0; ii < centroids.length; ii++ ) {
      Map<String,Float> pCentroid = prevCentroids[ii];
      Map<String,Float> nCentroid = centroids[ii];
      
      for( Map.Entry<String,Float> pEntry : pCentroid.entrySet() ) {
        Float newValue = nCentroid.get(pEntry.getKey());
        if( newValue == null ) diff += pEntry.getValue();
        else diff += Math.abs(pEntry.getValue() - newValue);
        totalFeats++;
      }
      for( Map.Entry<String,Float> entry : nCentroid.entrySet() )
        if( pCentroid.get(entry.getKey()) == null ) diff += entry.getValue();
    }
    
    return diff / (double)totalFeats;
  }
  
  /**
   * Computes the average feature-value map over all members in a cluster, and
   * sets each centroid to its new average.
   * NOTE: This is destructive, altering the centroids array that is given.
   * @return The average change in feature values across all centroid features.
   */
  private double recomputeCentroids(Map<String,Float>[] centroids, final Set<Integer>[] clusters, final Map<String,Float>[] data) {
    int totalFeats = 0;
    double diff = 0.0f;
    int i = 0;
    for( Set<Integer> cluster : clusters ) {
      Map<String,Float> average = new HashMap<String,Float>();

      // Sanity check.
      if( cluster.size() == 0 )
        System.out.println("WARNING: centroid " + i + " has no data points in its cluster");
        
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
      centroids[i++] = average;
    }

    double change = diff / (double)totalFeats;
    return change;
  }

  /**
   * Keep the top N features in the centroid, and remove all else.
   * @param centroid The centroid to trim
   * @param retain The number of features to retain.
   */
  private void trimCentroid(Map<String,Float> centroid, int retain) {
    int num = 0;
    for( String key : Util.sortKeysByFloatValues(centroid) ) {
      if( num++ > retain )
        centroid.remove(key);
    }
  }
  
  /**
   * Finds the centroid that is closest to the given datum in the feature space.
   * Assumes the datum and centroids are length normalized.
   * Computes the cosine similarity by just doing the dot product.
   * @return The array index of the nearest centroid.
   */
  private int nearestCentroid(final Map<String,Float>[] centroids, final Map<String,Float> datum) {
    int best = -1;
    float bestSim = -1.0f;

    int i = 0;
    for( Map<String,Float> centroid : centroids ) {
      float sim = dotProductSmallestFirst(datum, centroid);
      //      System.out.print(" i=" + i + " sim=" + sim);
      if( sim > bestSim ) {
        bestSim = sim;
        best = i;
      }
      i++;
    }
    //    System.out.println(" best=" + best);
    // If our vector had no features in common, it's best similarity is zero!
    if( bestSim == 0.0f ) return -1;
    else return best;
  }

  /**
   * Standard dot product: Sum_i x_i * y_i
   * This method assumes the caller knows x has less entries than y, and calls this 
   * method as a small optimization to avoid an if statement that checks map size()
   */
  public static float dotProductSmallestFirst(final Map<String,Float> x, final Map<String,Float> y) {
    float dot = 0.0f;
    
    // Loop over x's features
    for( Map.Entry<String,Float> entry : x.entrySet() ) {
      Float yvalue = y.get(entry.getKey());
      if( yvalue != null ) {
        dot += entry.getValue() * yvalue;
//        dotadd++;
      }
//      dotc++;
    }

    //return rand.nextFloat();
    return dot;
  }
  
  /**
   * Chooses k random data points from the given array, and treats those as the
   * starting centroids in k-means.
   * @param ignoreList Don't choose points in this list.
   */
  public static Map<String,Float>[] randomStartingPoints(int k, Map<String,Float>[] data, Set<Integer> ignoreList) {
    int n = data.length;
    Set<Integer> randomIndices = new HashSet<Integer>(k);
    Map<String,Float>[] centroids = new HashMap[k];

    if( k >= n ) {
      System.out.println("ERROR: number of clusters " + k + " is greater than the number of datums " + n);
      System.exit(1);
    }
    
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
//      Util.printFloatMapSortedByValue(centroids[i], 1000);
    }
    return centroids;
  }

  /**
   * Create (or read in) the initial random centroids for the kmeans algorithm.
   * @param k    The number of centroids to create.
   * @param data The array of data vectors.
   * @param ignoreList A list of data vectors to ignore and not choose as centroids.
   * @return An array of centroid word vectors.
   */
  public Map<String,Float>[] getInitialCentroids(int k, final Map<String,Float>[] data, final Set<Integer> ignoreList) {
    String initpath = centroidDir + File.separator + "centroidsInit";
        
    System.out.println("Getting initial centroids!");
  	if( Locks.getLock("initialize-centroids", locksDir) ) {
  		Map<String,Float>[] centroids = randomStartingPoints(_k, data, ignoreList);
  		printCentroids(centroids, new File(initpath));
  		Directory.touch(initpath + "-finished");
  		System.out.println("...generated random centroids!");
  		return centroids;
  	} 
  	
  	// Another process chose the initial centroids. Just read them in.
  	else {
  	  // Make sure the process finished creating them.
  	  while( !Directory.fileExists(initpath + "-finished") ) {
  	    System.out.println("Waiting for main process to create the initial centroids...");
  	    try {
          Thread.sleep(250);
        } catch(InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
  	  }

  	  return readCentroids(initpath);
  	}
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
      if( centroid != null ) {
        
        // Print top 10
        List<String> sorted = Util.sortKeysByFloatValues(centroid);
        System.out.print("TOP FEATURES:\t");
        for( int jj = 0; jj < 10 && jj < sorted.size(); jj++ )
          System.out.printf("%s\t%.4f\t", sorted.get(jj), centroid.get(sorted.get(jj)));
        System.out.println();
        
        // Print all
        for( String key : sorted )
          System.out.print(key + "\t" + centroid.get(key) + "\t");
      }
      System.out.println();
      ii++;
    }
  }

  private void printCentroids(Map<String,Float>[] centroids, File file) {
    try {
      PrintWriter writer = new PrintWriter(file);
      int ii = 0;
      writer.println(centroids.length);
      for( Map<String,Float> centroid : centroids ) {
        writer.println("Centroid " + ii);
        
        // Print top 10
        List<String> sorted = Util.sortKeysByFloatValues(centroid);
        writer.print("TOP FEATURES:\t");
        for( int jj = 0; jj < 10 && jj < sorted.size(); jj++ )
          writer.print(sorted.get(jj) + "\t" + centroid.get(sorted.get(jj)) + "\t");
        writer.println();
        
        // Print all
        for( String key : sorted )
          writer.print(key + "\t" + centroid.get(key) + "\t");
        writer.println();
        ii++;
      }
      writer.close();
      System.out.println("printCentroids finished " + file.getName() + " with " + centroids.length + " centroids printed.");
    } catch( IOException ex ) { ex.printStackTrace(); System.exit(1); }
  }
  
  /**
   * Load the centroids in the given file. Format is assumed to be the same created
   * by the above printCentroids method.
   * @param path The path to the file.
   * @return An array of maps, each map is a centroid's vector.
   */
  private Map<String,Float>[] readCentroids(String path) {
    try {
      BufferedReader in = new BufferedReader(new FileReader(path));
      String line = in.readLine();
      int n = Integer.parseInt(line);
      int ii = 0;

      System.out.println("readCentroids file has " + n + " centroids in it.");
      Map<String,Float>[] centroids = new HashMap[n];
      
      line = in.readLine();
      while( line != null ) {
        if( line.startsWith("Centroid ") || line.matches("^\\s*$")) {
          line = in.readLine();

          // Sometimes we have "TOP FEATURES" as a line.
          if( line.startsWith("TOP FEATURES") ) line = in.readLine();
          
          Map<String,Float> roid = new HashMap<String,Float>();
          centroids[ii] = roid;
          ii++;
          
          try {
            // Some centroids are empty because no datums matched.
            if( !line.matches("^\\s*$") ) {
              String[] parts = line.split("\t");
              for( int jj = 0; jj < parts.length; jj += 2 )
                roid.put(parts[jj], Float.parseFloat(parts[jj+1]));
            }
          } catch( Exception ex ) {
            System.out.println("ERROR on line: *" + line + "*");
            System.out.println("parts size is " + line.split("\t").length);
            
            System.out.println("Reading the next line: " + in.readLine());
            
            ex.printStackTrace();
            System.exit(1);
          }
        }
        else {
          System.out.println("ERROR in readCentroids, unknown line format: " + line);
          System.exit(1);
        }
        line = in.readLine();
      }
      
      in.close();
      return centroids;
      
    } catch( Exception ex ) { 
      System.err.println("Error opening " + path);
      ex.printStackTrace();
      System.exit(1);
    }
    
    return null;
  }

  private Map<String,Float>[] readCentroidsAndAverage(int round) {
    System.out.println("readCentroidsAndAverage: round " + round + " checking " + centroidDir);
    
    List<String> paths = new ArrayList<String>();
    
  	// Check and wait for all processes to finish this round.
    int prevprint = 0;
  	while( paths.size() < numberProcesses ) {
  		// Count files like 'centroid3-2'
  		for( String file : Directory.getFiles(centroidDir) ) {
  			if( file.matches("centroid" + round + "-\\d+") && Directory.fileExists(centroidDir + File.separator + file + "-finished") )
  				paths.add(centroidDir + File.separator + file);
  		}
  		// Print status, but don't keep printing until we find more centroids.
  		if( paths.size() > prevprint ) {
  		  System.out.println("Found " + paths.size() + " completed centroids.");
  		  prevprint = paths.size();
  		}

      // Sleep to wait for other processes.
  		if( paths.size() < numberProcesses ) {
  		  paths.clear(); // reset to try again
  		  try {
  		    Thread.sleep(250);
  		  } catch(InterruptedException ex) {
  		    Thread.currentThread().interrupt();
  		  }
  		}
  	}

  	// Average all process centroids.
  	Map<String,Float>[] centroids = new HashMap[_k];
		for( String filepath : paths ) {
		  System.out.println("Reading centroid file: " + filepath);
		  Map<String,Float>[] roids = readCentroids(filepath);
		  
		  // Sanity check.
		  if( roids.length != centroids.length ) {
		    System.out.println("Centroids length " + centroids.length + " doesn't match roids length " + roids.length);
		    System.exit(1);
		  }

		  System.out.println("roids length " + roids.length);
		  
		  for( int ii = 0; ii < roids.length; ii++ ) {
		    if( roids[ii] == null ) {
          System.out.println("NULL CENTROID FROM FILE: centroid " + ii);
          System.exit(1);
		    }

		    
		    if( centroids[ii] == null )
		      centroids[ii] = roids[ii];
		    else
		      centroids[ii] = Dimensional.sumFeatures(centroids[ii], roids[ii]);
		  }
		}
		
		
		System.out.println("Pre-Normalized new centroids");
    //printCentroids(centroids);
		for( int ii = 0; ii < centroids.length; ii++ ) {
		  // Trim vector to the top 2000 features.
		  trimCentroid(centroids[ii], 2000);
		  // Normalize values.
		  Dimensional.normalize(centroids[ii], numberProcesses);
		}
  	  	
  	return centroids;
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
    Vector<Map<String,Float>> data = new Vector<Map<String,Float>> ();
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
    ParallelKMeans cluster = new ParallelKMeans(3, 1);
    cluster.test();
  }
}

