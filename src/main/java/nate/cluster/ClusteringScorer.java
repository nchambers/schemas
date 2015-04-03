package nate.cluster;

/**
 * An interface that clustering algorithms can use to calculate
 * cluster scores.
 */
public interface ClusteringScorer {

  public double score(Cluster cluster);

  public double scoreMerge(Cluster c1, Cluster c2);
}
