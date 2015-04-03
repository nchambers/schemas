package nate.narrative;

import java.util.Set;


public interface ScoreCache {

  public float getScore(String key1, String key2);

  public void setScore(String key1, String key2, float score);
  
  // Remove all pairs that have this key.
  public void removeKey(String key);
  
  public Set<String> keySet();

  // Print the top N pairs.
  public void printSorted(int n);
  // Print all pairs.
  public void printSorted();
}