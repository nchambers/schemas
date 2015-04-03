package nate.cluster;


public interface Cluster {

  /**
   * A destructive function that merges another cluster into this one.
   */
  public void merge(Object cluster);
}
