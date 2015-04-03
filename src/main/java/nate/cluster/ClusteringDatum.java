package nate.cluster;

import java.util.Map;

/**
 * Class that holds a Map of features.
 * The main purpose is to keep features attached to the key/name of
 * whatever it is we are clustering.  Then we can filter out datums
 * during clustering and not worry about lining up the keys with the
 * feature sets.
 */
public class ClusteringDatum {
  String key;
  Map<String,Float> features = null;

  public ClusteringDatum() {
  }
  public ClusteringDatum(String k) {
    setKey(k);
  }

  public String key() { return key; }
  public void setKey(String k) { key = k; }

  public Map<String,Float> features() { return features; }
  public void setFeatures(Map<String,Float> feats) { features = feats; }

  public String toString() { return key; }
}