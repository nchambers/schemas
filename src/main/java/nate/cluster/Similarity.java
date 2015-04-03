package nate.cluster;

import java.util.Map;

public interface Similarity {

  public float computeSimilarity(Map<String,Float> x, Map<String,Float> y);
  public float computeClusterSimilarity(Map<String,Float> x, int numi, int numj);
  //  public float computeClusterSimilarity(Map<String,Float> x, Map<String,Float> y);
  //  public float computeSimilarity(Map<String,Float> x, 
  //				 Map<String,Float> y, Map<String,Float> z);
}
