package nate.schemas;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import edu.stanford.nlp.stats.Counter;
import nate.IDFMap;
import nate.cluster.ParallelKMeans;
import nate.util.Directory;
import nate.util.HandleParameters;
import nate.util.Locks;
import nate.util.Util;

/**
 * Set the num-processes parameter to be the same as how many processes of this class you
 * start up. They must be the same. Each process will process max/num-processes schemas.
 * 
 * ClusterSchemas [-max <int> -k <int> -rw <float>] <schema-dir> <num-processes>
 *
 * -max <int>
 * The number of schemas to read in across all processes
 * 
 * -k <int>
 * The k in k-means.
 * 
 * -rw <float>
 * The weight to put on relations in the schema feature vectors.
 * 
 */
public class ClusterSchemas {
	private int numClusters = 100;
	private int numProcesses = 1;
	public int maxSchemas = 6000000; // 6 million
	private boolean isMainProcess = false;
	public float relationWeight = 10.0f;

	private ParallelKMeans kmeans;
	
	private IDFMap generalIDF;
	private String finalClusterDir = "pkmeans-clusters";

	
  public ClusterSchemas(int numProc) {
  	numProcesses = numProc;
    System.out.println("Loading IDF from: " + IDFMap.findIDFPath());
    generalIDF = new IDFMap(findIDFPath());
  }
  
  public ClusterSchemas(int numClusters, int numProc) {
    this.numClusters = numClusters;
    this.numProcesses = numProc;
    System.out.println("Loading IDF from: " + IDFMap.findIDFPath());
    generalIDF = new IDFMap(findIDFPath());
    
    System.out.println("clusters:\t" + this.numClusters);
    System.out.println("processes:\t" + this.numProcesses);
    System.out.println("relation weight:\t" + this.relationWeight);
  }

  private String findIDFPath() {
    String[] paths = { "ap-tokens.idf-2006", "/home/nchamber/Projects/schemasclusters/ap-tokens.idf-2006" };
    for( String path : paths )
      if( Directory.fileExists(path) )
        return path;
    return null;
  }
  
  /**
   * Reads schemas from a text file of their output. Text files were probably created by GigaExtractor.java
   * @param filename File with the schemas.
   * @param numToRead The number of schemas to read from this file (if greater than file, reads entire file).
   * @return A list of schema objects.
   */
  public static List<Schema> readSchemas(String filename, int numToRead) {
    BufferedReader in = null;
    try {
      if( filename.endsWith(".gz") )
        in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename))));
      else in = new BufferedReader(new FileReader(filename));
    } catch( Exception ex ) { 
      System.err.println("Error opening " + filename);
      ex.printStackTrace();
    }
    
    List<Schema> schemas = new ArrayList<Schema>();
    int numSchemas = 0;
    
    try {
      // Loop over all schemas in the file.
      String line = in.readLine();
      while( line != null ) {

        if( numSchemas > numToRead ) break;

        // Read blank lines.
        while( line != null && line.matches("^\\s*$") ) {
          line = in.readLine();
        }
        
      	// Read all schema lines into one buffer.
        if( line != null ) {        
        	StringBuffer buf = new StringBuffer();
        	while( line != null && !line.matches("^\\s*$") ) {
        		buf.append(line);
        		buf.append("\n");
        		line = in.readLine();
        	}

        //	System.out.println("pre-schema str: " + buf);
        	// Build schema.
        	Schema schema = Schema.fromString(buf.toString());
        	cleanSchemaCounts(schema);
//        	System.out.println("Text in: " + buf.toString());
//        	System.out.println("Schema: " + schema);
        	schemas.add(schema);
        	numSchemas++;
        }
        
      }
      
      in.close();
    } catch( IOException ex ) { ex.printStackTrace(); }

    System.out.println("Read in " + numSchemas + " schemas.");
    return schemas;
  }
  
  // Remove punctuation tokens that shouldn't have been extracted.
  public static void cleanSchemaCounts(Schema schema) {
    schema.getTokenCounts().remove("-lrb-");
    schema.getTokenCounts().remove("-rrb-");
  }
  
  /**
   * Cluster the schemas in the given text file.
   * @param dirname The file with the schemas to cluster.
   */
  public void cluster(String dirname) {
  	isMainProcess = Locks.getLock("mainprocess");
  	
  	String[] files = Directory.getFilesSorted(dirname);
  	int numFilesToCluster = files.length / numProcesses;
  	int maxSchemasToLoad = maxSchemas / numProcesses;
  	int numLoaded = 0;
  	
  	List<String> filePaths = new ArrayList<String>();
  	
  	// Read a subset of the files in the directory.
  	List<Schema> schemas = new ArrayList<Schema>();
  	for( String file : Directory.getFilesSorted(dirname) ) {
  		if( file.contains("nyt") || file.contains("apw") )
  			if( numLoaded < numFilesToCluster && Locks.getLock(file) ) {
  				System.out.println("Reading schemas from " + file);
  				int numToRead = maxSchemasToLoad - schemas.size();
  				schemas.addAll(readSchemas(dirname + File.separator + file, numToRead));
  				filePaths.add(dirname + File.separator + file);
  				numLoaded++;
  			}
  	}
  	
  	// Cluster!
    System.out.println("Clustering " + schemas.size() + " schemas in this process.");
    Util.reportMemory();
    long startTime = System.currentTimeMillis();
    Set<Integer>[] clusters = cluster(schemas);
    Util.reportMemory();
    Util.reportElapsedTime(startTime);
    
    // Debug cluster sizes.
    System.out.println("There are " + clusters.length + " clusters.");
    int count = 0;
    for( Set<Integer> cl : clusters ) count += (cl != null ? cl.size() : 0);
    System.out.println("There are " + count + " schemas that were clustered.");
    
    // Re-read the schemas into memory (the cluster method frees the schemas).
    schemas = new ArrayList<Schema>();
    for( String path : filePaths ) {
      System.out.println("Re-reading schemas from " + path);
      int numToRead = maxSchemasToLoad - schemas.size();
      schemas.addAll(readSchemas(path, numToRead));
      System.out.println("Read in " + schemas.size() + " schemas.");
    }

    // Write clusters to disk.
    writeClusters(clusters, schemas);
    printClusters(clusters, schemas); // debugging, the main process will aggregate all together
    
    // Free memory.
    schemas.clear();
    schemas = null;
    
    if( isMainProcess )
      readAllProcessClusters(clusters.length);
    else
      System.out.println("Finished. Not the main process.");

    Util.reportMemory();
    Util.reportElapsedTime(startTime);
  }
  
  /**
   * Cluster the given list of schema objects.
   * @param schemas The schemas to cluster.
   * @return The clusters. Each array cell is a set of schema IDs, the indices in the list that was given.
   */
  public Set<Integer>[] cluster(List<Schema> schemas) {
    kmeans = new ParallelKMeans(numClusters, numProcesses);
    
    List<Map<String,Float>> allfeats = new ArrayList<Map<String,Float>>(schemas.size());
    List<String> docnames = new ArrayList<String>(schemas.size());
    
    for( int ii = 0; ii < schemas.size(); ii++ ) {
      Schema schema = schemas.get(ii);
      schemas.set(ii, null); // remove the schema to free up memory
    	
      Map<String,Float> feats = featurizeSchema(schema);

//      System.out.println("SCHEMA: " + schema);
//      System.out.println("FEATS: " + feats);
      
      allfeats.add(feats);
      docnames.add(schema.getDocname());
    }
    
    System.out.println("Calling kmeans now with " + numClusters + " clusters and " + allfeats.size() + " feature vectors...");
    Util.reportMemory();
    Set<Integer>[] clusters = kmeans.cluster(docnames, allfeats, false);
    return clusters;    
}
  
  /**
   * Convert a schema into a feature vector.
   * @param schema The schema to convert to features.
   * @return A map from feature names to feature counts.
   */
  private Map<String,Float> featurizeSchema(Schema schema) {
    Map<String,Float> feats = new HashMap<String,Float>();

    // Features based on events.
    for( Relation rel : schema.getRelations() ) {
      float relIDF = generalIDF.get(rel.predicate);
      // Unknown words with length are rare, high IDF
      if( relIDF == 0.0f ) relIDF = 10.0f;
      
      if( rel.particle != null )
        feats.put("REL-" + rel.predicate + " " + rel.particle, relationWeight*relIDF);
      else
        feats.put("REL-" + rel.predicate, relationWeight*relIDF);
    }

    // Features based on sentence tokens.
    if( schema.getTokenCounts() != null ) {
      Counter<String> counts = schema.getTokenCounts();
      for( Map.Entry<String, Double> entry : counts.entrySet() ) {
        // Find the lowest IDF score for this token.
        float idf1 = generalIDF.get(entry.getKey());
        
        // Unknown words with length are rare, high IDF
        if( idf1 == 0 && entry.getKey().length() > 4 ) idf1 = 10.0f;
        // Unknown words with no length are things like: 's 'm 'd
        else if( idf1 == 0 ) idf1 = 0.5f;
        
//        System.out.println(entry.getKey() + " count " + entry.getValue() + " idf=" + idf1);
        feats.put(entry.getKey(), idf1*entry.getValue().floatValue());
      }
    }
    
    return feats;
  }
  
  /**
   * This is only called by the one process that grabbed the isMainProcess lock.
   * 
   * This method reads files from disk that contain serialized arrays of schemas.
   * Each process writes its own file, putting its schemas into the clusters that 
   * their local kmeans mapped to centroids. This method reads them all, and merges
   * the schemas together, then prints the final merging. It's basically the reduce
   * in map-reduce.
   * @param numClusters The number of clusters that kmeans used.
   */
  private void readAllProcessClusters(int numClusters) {
  	
  	//	Check and wait for all processes to finish.
    List<String> paths = new ArrayList<String>();
    while( paths.size() < numProcesses ) {
  		// Count files like 'centroid3-2'
   		for( String file : Directory.getFiles(ParallelKMeans.centroidDir) ) {
    		if( file.startsWith("finalclusters") && Directory.fileExists(ParallelKMeans.centroidDir + File.separator + file + "-finished") )
   				paths.add(ParallelKMeans.centroidDir + File.separator + file);
   		}
   		System.out.println("Found " + paths.size() + " completed clusters.");

       // Sleep to wait for other processes.
   		if( paths.size() < numProcesses ) {
   		  paths.clear(); // reset to try again
   		  try {
   		    Thread.sleep(250);
   		  } catch(InterruptedException ex) {
   		    Thread.currentThread().interrupt();
   		  }
   		}
   	}
   	
  	// Looks for files e.g., "finalclusters2"
  	for( String file : paths ) {
  		System.out.println("Reading final clusters from: " + file);
  		try {
  		  // Read in all clusters from one process
  			ObjectInputStream input = new ObjectInputStream(new FileInputStream(file));
  			final List<Schema>[] clusters = (List<Schema>[])(input.readObject());
  			// Append the one process' clusters to each cluster file.
  			Directory.createDirectory(finalClusterDir);
  			for( int ii = 0; ii < clusters.length; ii++ ) {
  				Map<String,Float> centroid = kmeans.centroids[ii];
  				final List<Schema> schemas = clusters[ii];
  			  FileWriter writer = new FileWriter(new File(finalClusterDir + File.separator + ii), true);
  				if( schemas != null )
  					// Sort schemas by centroid closeness and print the sorted schema order.
  					for( Integer index : sortSchemasByCentroidLikeness(schemas, centroid) )
  						writer.write(schemas.get(index) + "\n");
  				writer.close();
  			}
  			input.close();
  		} catch (Exception e) {
  			e.printStackTrace();
  		}
  	}
  	
  	// Each cluster now has its own file in the cluster directory.
  	System.out.println("\nAGGREGATED CLUSTERS ACROSS ALL PROCESSES: check " + finalClusterDir);
  }
  
  /**
   * Sorts the given schemas based on dot product similarity to the given centroid.
   * @param schemas
   * @param centroid
   */
  private List<Integer> sortSchemasByCentroidLikeness(final List<Schema> schemas, final Map<String,Float> centroid) {
  	Map<Integer,Float> indexToScore = new HashMap<Integer,Float>();
  	for( int ii = 0; ii < schemas.size(); ii++ ) {
  		// Compare schema ii to the centroid. Add score to Map
  		Schema schema = schemas.get(ii);
  		Map<String,Float> feats = featurizeSchema(schema);
  		// TODO: how? featurize?
  		float dotprod = ParallelKMeans.dotProductSmallestFirst(feats, centroid);
  		indexToScore.put(ii, dotprod);
  	}
  	// Sort the HashMap and return a list of Integers, the sorted Schema indices in the given List 
  	return Util.sortKeysByFloatValues(indexToScore);
  }
  
  /**
   * Writes the clusters of schemas to file as a serialized object.
   * WARNING: this doubles the RAM requirement of the schemas List since we're creating
   *          another array of all the schemas in the given list.
   * @param clusters The schema IDs clustered.
   * @param schemas The list of schemas.
   */
  private void writeClusters(Set<Integer>[] clusters, List<Schema> schemas) {
  	List<Schema>[] schemaclusters = new ArrayList[clusters.length];
  	for( int ii = 0; ii < clusters.length; ii++ ) {
  		schemaclusters[ii] = new ArrayList<Schema>();
  		for( Integer id : clusters[ii] ) {
  			schemaclusters[ii].add(schemas.get(id));
  		}
  	}
  	
  	try {
  		// Find a process ID
  		int id = 0;
  		while( !Locks.getLock(ParallelKMeans.centroidDir + File.separator + "finalclusters" + id) ) id++;
  		// Serialize and write the array.
  		ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(ParallelKMeans.centroidDir + File.separator + "finalclusters" + id));
  		output.writeObject(schemaclusters);
  		output.close();
  		// Make a separate "finished" file to indicate the clusters can be read.
  		Directory.touch(ParallelKMeans.centroidDir, "finalclusters" + id + "-finished");
  	} catch (Exception e) {
  		e.printStackTrace();
  	}
  }
  
  private void printClusters(Set<Integer>[] clusters, List<Schema> schemas) {
  	for( int ii = 0; ii < clusters.length; ii++ ) {
  		System.out.println("\n***********************************");
  		System.out.println("***********************************");
  		System.out.println("CLUSTER " + ii);
  		System.out.println("***********************************");
  		System.out.println("***********************************");
  		for( Integer id : clusters[ii] ) {
  			System.out.println(schemas.get(id));
  		}
  	}
  }
  
  /**
   * Main.
   * 
   * ClusterSchemas <schema-dir> <num-processes-running-over-dir>
   */
  public static void main(String[] args) {
    if( args.length < 2 ) {
      System.out.println("ClusterSchemas [-k <num-clusters>] <dir> <num-processes>");
    }
    else {
      ClusterSchemas cluster = null;
      
      HandleParameters params = new HandleParameters(args);

      // Number of k clusters in kmeans.
      if( params.hasFlag("-k") ) {
        int k = Integer.parseInt(params.get("-k"));
        cluster = new ClusterSchemas(k, Integer.parseInt(args[args.length-1]));
      }
      else {
        cluster = new ClusterSchemas(Integer.parseInt(args[1]));
      }
      
      // Max number of schemas to read in.
      if( params.hasFlag("-max") )
      	cluster.maxSchemas = Integer.parseInt(params.get("-max"));

      // Extra weight to put on predicates in the vectors.
      if( params.hasFlag("-rw") )
        cluster.relationWeight = (float)Double.parseDouble(params.get("-rw"));

      System.out.println("clusters:\t" + cluster.numClusters);
      System.out.println("processes:\t" + cluster.numProcesses);
      System.out.println("relation weight:\t" + cluster.relationWeight);
      
      // Cluster a directory.
      cluster.cluster(args[args.length-2]);
    }
  }

}
