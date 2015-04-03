package nate.cluster;

import java.util.Map;


/**
 * The purpose of this class is simply to keep track of an object as it
 * is clustered.  The features vector is what is used in clustering, but
 * the string key lets you set it to whatever you want so you can recover
 * it later.
 */
public class ClusterDatum {
  Map<String,Float> features;
  String key;

  public ClusterDatum(String k) {
    key = k;
  }
  public ClusterDatum(String k, Map<String,Float> feats) {
    key = k;
    features = feats;
  }

  public void setFeatures(Map<String,Float> feats) {
    features = feats;
  }

  public String key() { return key; }
  public Map<String,Float> features() { return features; }
}
