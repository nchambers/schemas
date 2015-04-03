package nate.cluster;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Vector;
import java.util.Collection;

import nate.util.Dimensional;
import nate.util.Triple;


public class HybridHAC {
  
  public HybridHAC() { }


  public Set<Integer>[] cluster(Vector<String> names,
				Collection<Map<String,Float>> alldata) {
    int dataSize = alldata.size();
    Map<String,Float>[] data = new Map[dataSize];
    data = alldata.toArray(data);
    Dimensional.lengthNormalize(data);

    // Top-Down.
    DivisiveClustering divisive = new DivisiveClustering();
    Set<Integer>[] initialClusters = divisive.cluster(names, alldata);

    System.out.println("**Finished divisive clustering with " + 
		       initialClusters.length + " clusters.");

    // Bottom-Up.
    HierarchicalClustering hac = new HierarchicalClustering();
    hac.setMinInitialSimilarityScore(0.1f);
    hac.setMinClusteringScore(0.25f);

    Vector<Set<Integer>> finalClusters = new Vector();
    for( Set<Integer> cluster : initialClusters ) {

      System.out.println("**Running HAC with cluster of size " + cluster.size());
      KMeans.printCluster(names, cluster);

      // Build this cluster's unique list of feature vectors.
      Vector<Map<String,Float>> clusterData = new Vector();
      Map<Integer,Integer> idMap = new HashMap();
      int i = 0;
      for( Integer id : cluster ) {
	clusterData.add(data[id]);
	idMap.put(i++, id);
      }

      Set<Integer>[] hacClusters = hac.clusterFully(clusterData);
      System.out.println("**Finished HAC with " + hacClusters.length + " clusters.");
      int totalclustered = 0;
      for( Set<Integer> hacCluster : hacClusters ) {
	Set<Integer> temp = DivisiveClustering.recoverIndices(hacCluster, idMap);
	System.out.print("--");  KMeans.printCluster(names, temp);
	finalClusters.add(temp);
	totalclustered += temp.size();
      }
      System.out.println("**Total items clustered = " + totalclustered + " out of " +
			 clusterData.size());
    }

    System.out.println("**Returning " + finalClusters.size() + " clusters.");

    // Return the final clusters.
    Set<Integer> arr[] = new HashSet[finalClusters.size()];
    arr = finalClusters.toArray(arr);
    return arr;
  }

}
