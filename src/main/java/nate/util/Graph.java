package nate.util;

import java.util.Set;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;


public class Graph {
  Graph() { }

  /**
   * @param matrix Adjacency matrix of the graph.
   * @return A set of indices to all vertices in the largest connected component.
   */
  public static Set<Integer> largestConnectedComponent(boolean[][] matrix) {
    int size = 1;
    boolean[] visited = new boolean[matrix.length];
    Set<Integer> largestComponent = null;

    for( int i = 0; i < visited.length; i++ ) {
      if( !visited[i] ) {
	Set<Integer> component = new HashSet();
	component.add(i);

	// Traverse neighbors of i.
	Queue<Integer> queue = new LinkedList();
	queue.add(i);

	while( !queue.isEmpty() ) {
	  Integer parent = queue.remove();
	  visited[parent] = true;
	  for( int j = 0; j < matrix.length; j++ ) {
	    if( matrix[parent][j] && !visited[j] ) {
	      queue.add(j);
	      component.add(j);
	    }
	  }
	}

	// See if it is a larger component.
	if( largestComponent == null || component.size() > largestComponent.size() )
	  largestComponent = component;
      }
    }

    return largestComponent;
  }

}
