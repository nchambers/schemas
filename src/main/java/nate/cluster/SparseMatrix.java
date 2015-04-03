package nate.cluster;


/**
 * Represents a sparse matrix, for memory-saving purposes.
 */
public class SparseMatrix {
  private final int _n;           // N-by-N matrix
  private SparseVector[] rows;   // the rows, each row is a sparse vector
  
  public SparseMatrix(int size) {
    _n = size;
    rows = new SparseVector[size];
    for (int i = 0; i < size; i++) rows[i] = new SparseVector(size);
  }
  
  /**
   * Add a value to the matrix.
   */
  public void put(int i, int j, ClusterCell value) {
    if (i < 0 || i >= _n) throw new RuntimeException("Illegal index");
    if (j < 0 || j >= _n) throw new RuntimeException("Illegal index");
    rows[i].put(j, value);
  }

  /**
   * @return The value in this matrix cell.
   */
  public ClusterCell get(int i, int j) {
    if (i < 0 || i >= _n) throw new RuntimeException("Illegal index");
    if (j < 0 || j >= _n) throw new RuntimeException("Illegal index");
    return rows[i].get(j);
  }

  /**
   * @return A single Sparse row.
   */
  public SparseVector getRow(int i) {
    if (i < 0 || i >= _n) throw new RuntimeException("Illegal index");
    return rows[i];
  }

  /**
   * Clears all entries in a row, effectively setting them to zero.
   * In regards to storage, it removes all row objects.
   */
  public void clearRow(int i) {
    rows[i].clear();
  }

  /**
   * @return The number of cells that are filled in.
   */
  public int numFilledEntries() { 
    int sum = 0;
    for (int i = 0; i < _n; i++)
      sum += rows[i].numFilledEntries();
    return sum;
  }

  /**
   * You shouldn't really ever use this if it's a huge matrix...
   */
  public String toString() {
    String str = "N = " + _n + ", nonzeros = " + numFilledEntries() + "\n";
    for (int i = 0; i < _n; i++)
      str += i + ": " + rows[i] + "\n";
    return str;
  }

  public int length() { return _n; }
  
  // for testing...
  public static void main(String[] args) {
    SparseMatrix A = new SparseMatrix(5);
    SparseVector x = new SparseVector(5);
    A.put(0, 0, new ClusterCell(1.0f));
    A.put(1, 1, new ClusterCell(1.0f));
    A.put(2, 2, new ClusterCell(1.0f));
    A.put(3, 3, new ClusterCell(1.0f));
    A.put(4, 4, new ClusterCell(1.0f));
    A.put(2, 4, new ClusterCell(0.3f));
    x.put(0, new ClusterCell(0.75f));
    x.put(2, new ClusterCell(0.11f));
    System.out.println("x     : " + x);
    System.out.println("A     : " + A);
    //    System.out.println("Ax    : " + A.times(x));
    //    System.out.println("A + A : " + A.plus(A));
  }
}
