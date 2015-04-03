package nate.args;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;


/**
 * Takes a file of verb pairs and their argument counts, and maps all of the
 * arguments into CBC clusters.
 * Outputs a file called "argcounts-clusters"
 */
public class ConvertCountsToClusters {
  String outfile = "argcounts-clusters";
  Map<String, Set<Integer>> words;
  PantelClusters clusters;

  ConvertCountsToClusters(String clusterDataPath) {
    clusters = new PantelClusters(clusterDataPath);
  }


  /**
   * @param filename The file with the argument counts
   */
  public void convert(String filename) {
    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      String line;
      BufferedWriter out = new BufferedWriter(new FileWriter(outfile));

      // Read the lines
      // aah:elicit;s:s *per* 1 part 1 fireplace 1 garnish 1 grace 1 -rrb- 1
      while( (line = in.readLine()) != null ) {
	Map<String,Integer> localCounts = new HashMap();
	String parts[] = line.split(" ");
	
	for( int i = 1; i < parts.length; i += 2 ) {
	  int count = Integer.valueOf(parts[i+1]);
	  Set<Integer> ids = clusters.getWordClusters(parts[i]);

	  if( ids != null ) {
	    // Increment each cluster ID
	    for( Integer id : ids ) {
	      Integer p = localCounts.get(id.toString());
	      if( p != null ) p += count;
	      else localCounts.put(id.toString(), count);
	    }
	  }
	  // Just save the word string if no cluster
	  else { 
	    localCounts.put(parts[i], count);
	  }
	}

	// Print the verb pair
	out.write(parts[0]);
	// Print the cluster IDs and their counts
	for( Map.Entry entry : localCounts.entrySet() ) {
	  out.write(" " + entry.getKey() + " " + entry.getValue());
	}
	out.write("\n");
      }
      out.close();
    } catch( Exception ex ) { ex.printStackTrace(); System.exit(1); }
  }
  

  /**
   * Main
   *
   * Give a path to the clusters, and a path to the file to convert to clusters.
   */
  public static void main(String[] args) {
    // Test narrative
    ConvertCountsToClusters converter = new ConvertCountsToClusters(args[0]);

    converter.convert(args[1]);
  }
}
