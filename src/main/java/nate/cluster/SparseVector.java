package nate.cluster;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * Represents a sparse vector, not filling in all the entries.
 */
public class SparseVector {
  private final int _n;  // vector length
  private Map<Integer, ClusterCell> _indexMap;  // the vector, represented by a hash table

  public SparseVector(int n) {
    _n  = n;
    _indexMap = new HashMap<Integer,ClusterCell>();
  }

  /**
   * Fills the vector cell: vec[i] = value
   */
  public void put(int i, ClusterCell value) {
    if (i < 0 || i >= _n) throw new RuntimeException("Illegal index");
    if (value.toValue() == 0.0f) _indexMap.remove(i);
    else _indexMap.put(i, value);
  }

  /**
   * Retrieve the ith index from the vector.
   */
  public ClusterCell get(int i) {
    if (i < 0 || i >= _n) throw new RuntimeException("Illegal index");
    //    Object value = _indexMap.get(i);
    //    if (value != null) return _indexMap.get(i);
    //    else return null;
    return _indexMap.get(i);
  }

  /**
   * @return All the index positions that are filled (non-zero).
   */
  public Set<Integer> entryIndices() {
    return _indexMap.keySet();
  }

  /**
   * @return All the Map.Entry objects filled in this row.
   */
  public Set<Map.Entry<Integer, ClusterCell>> entries() {
    return _indexMap.entrySet();
  }

  /**
   * Wipe this vector clean.
   */
  public void clear() {
    _indexMap.clear();
  }

  /**
   * @return The number of cells that are filled in.
   */
  public int numFilledEntries() { return _indexMap.size(); }
  public int size() { return _n; }

  public Map<Integer,ClusterCell> indexMap() { return _indexMap; }

  public String toString() {
    String str = "";
    for( Map.Entry<Integer,ClusterCell> entry : _indexMap.entrySet() )
      str += entry.getKey() + " " + entry.getValue().sim();
    return str;
  }

  public void main(String[] args) {
    SparseVector a = new SparseVector(10);
    SparseVector b = new SparseVector(10);
    a.put(3, new ClusterCell(0.50f));
    a.put(9, new ClusterCell(0.75f));
    a.put(6, new ClusterCell(0.11f));
    a.put(6, new ClusterCell(0.00f));
    b.put(3, new ClusterCell(0.60f));
    b.put(4, new ClusterCell(0.90f));
    System.out.println("a = " + a);
    System.out.println("b = " + b);
  }
}
