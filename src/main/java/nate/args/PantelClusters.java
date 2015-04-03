package nate.args;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;


/**
 * A Class that loads Pantel's CBC Clusters from file and offers an interface
 * to look up words and clusters.
 *
 * CBC is an algorithm that clusters by committee (top cluster members), using
 * a centroid of the committee.  These clusters were learned from wikipedia.
 */
public class PantelClusters {
  Map<String, Set<Integer>> words;

  PantelClusters(String path) {
    words = new HashMap();
    readClustersFromFile(path);
  }


  /**
   * Load Pantel's clusters from file into memory, using the given path.
   */
  public void readClustersFromFile(String path) {
    try {
      BufferedReader in = new BufferedReader(new FileReader(path));
      String line;
      int clusterID = -1;
      boolean validCluster = false;

      // Read the lines
      while( (line = in.readLine()) != null ) {
	
	// New cluster
	if( line.startsWith("(\"{") ) {
	  // NOUN cluster (N are noun clusters ... A adjectives, V verbs)
	  if( line.startsWith("(\"{N") ) {
	    String cluster = line.substring(4, line.indexOf(' '));
	    clusterID = Integer.valueOf(cluster);
	    validCluster = true;
	  } else {
	    validCluster = false;
	  }
	}

	// Word in the cluster
	// willing	0.501359 ; 0.0250847	0.0206912
	else if( !line.startsWith(")") && !line.startsWith("(") ) {
	  if( validCluster ) {
	    String parts[] = line.split("\\t");
	    if( parts.length < 2 ) System.out.println("Bad line format: " + line);
	    // Map this word to the cluster ID
	    addWordToCluster(parts[0].toLowerCase(), clusterID);
	  }
	}
      }

      in.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
  }

  /**
   * Puts a word with a cluster ID into memory
   */
  private void addWordToCluster(String word, int cluster) {
    Set clusters;
    // Retrieve or Create the set of clusters for this word
    if( !words.containsKey(word) ) {
      clusters = new HashSet();
      words.put(word, clusters);
    }
    else clusters = words.get(word);
    // Add the ID to our set of clusters that this word maps to
    clusters.add(cluster);
  }
  

  /**
   * @return A set of cluster IDs that the given word appears in.  
   */
  public Set<Integer> getWordClusters(String word) {
    word = word.toLowerCase();
    return words.get(word);
  }

}
