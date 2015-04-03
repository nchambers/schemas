package nate.cluster;

import java.util.Map;


public class ClusterCell implements Comparable {
  float _sim = 0.0f;
  Map<String,Float> _featureSum;
  int _index = -1;

  ClusterCell() { }
  ClusterCell(float score) { _sim = score; }
  ClusterCell(int i, float score) { _index = i; _sim = score; }

  public void setSim(float s) { _sim = s; }
  public float sim() { return _sim; }

  public void setFeatureSum(Map<String,Float> sum) {
    _featureSum = sum;
  }
  public Map<String,Float> featureSum() { return _featureSum; }

  public float toValue() { return _sim; }

  public void setIndex(int i) { _index = i; }
  public int index() { return _index; }

  public String toString() { return "" + _sim; }

  // Comparable Interface.
  public int compareTo(Object y) {
    if( _sim < ((ClusterCell)y).sim() ) return 1;
    else if( _sim == ((ClusterCell)y).sim() ) return 0;
    else return -1;
  }
}
