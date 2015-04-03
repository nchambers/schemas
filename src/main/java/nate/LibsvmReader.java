package nate;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.util.Vector;
import java.util.Arrays;


public class LibsvmReader {

  public LibsvmReader() { }

  /**
   * @return A Vector of arrays. Each array holds the probability assignments
   * to one test case. Array length equals class size.
   */
  public Vector<double[]> fromFile(String filename) {
    System.out.println("Reading svmlib file of probabilities: " + filename);
    Vector<double[]> vec = new Vector<double[]>();
    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      int[] positions = null;

      while( in.ready() ) {
	String line = in.readLine();

	// line = "labels 6 3 1 5 2 4"
	if( line.matches("labels .*") ) {
	  // get an array of the numbers
	  String parts[] = line.substring(line.indexOf(' ')+1).split(" ");
	  positions = new int[parts.length];
	  for( int i = 0; i < parts.length; i++ )
	    positions[Integer.valueOf(parts[i])-1] = i;
	  //	  System.out.println("positions " + Arrays.toString(positions));
	}

	// line = "1 0.107628 0.0592344 0.709439 0.0347763 0.0297943 0.0591283"
	else {
	  String[] parts = line.split(" ");

	  // 1 0.84 0.1 0.0 0.06
	  double[] probs = new double[parts.length-1]; 
	  for( int x = 0; x < probs.length; x++ ) {
	    probs[x] = Double.valueOf(parts[positions[x]+1]);
	  }

	  //	  System.out.println(line);
	  //	  System.out.println(Arrays.toString(probs));
	  vec.add(probs);
	}
      }
    } catch( Exception ex ) { ex.printStackTrace(); }
    return vec;
  }

}
