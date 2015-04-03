package nate;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.util.Vector;
import java.util.Arrays;


public class ARFFReader {

  ARFFReader() { }

  /**
   * @return A Vector of arrays. Each array holds the probability assignments
   * to one test case. Array length equals class size.
   */
  public Vector<double[]> fromFile(String filename) {
    System.out.println("Reading arff file " + filename);
    Vector<double[]> vec = new Vector<double[]>();
    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
    
      while( in.ready() ) {
	String line = in.readLine();

	// line = " inst#     actual  predicted error distribution"
	if( line.matches(".*actual.*") ) { 
	}
	else {
	  String[] parts = line.split("\\s+");
	  for( int i = 0; i < parts.length; i++ ) {
	    // .84,.1,0,.06
	    if( parts[i].indexOf(',') > -1 ) {
	      String[] nums = parts[i].split(",");
	      double[] probs = new double[nums.length]; 

	      for( int x = 0; x < probs.length; x++ ) {
		if( nums[x].indexOf('*') == -1 )
		  probs[x] = Double.valueOf(nums[x]);
		else
		  probs[x] = Double.valueOf(nums[x].substring(1));
	      }

	      vec.add(probs);
	      continue;
	    }
	  }
	}
      }
    } catch( Exception ex ) { ex.printStackTrace(); }
    return vec;
  }

}
