package nate.args;

import java.util.Vector;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.io.BufferedReader;
import java.io.FileReader;


/**
 * This class reads a file of pairs with their argument counts and sorts
 * the lines by the pair.  It outputs the sorted order to STDOUT.  This
 * is basically the same as the unix sort utility, but Nate found that 
 * unix sort orders underscore _ and semicolon ; differently than Java
 * sort.
 *
 * Input example line:
 *   string-no-white-space arg1 34 arg2 9 arg3 1 arg4 88 ...
 *
 * Output is sorted to stdout.
 */
public class SortArgs {
  String inputFile;

  /**
   * Constructor
   */
  SortArgs(String filename) {
    inputFile = filename;
  }

  /**
   * Read a file, get the leading pair, sort the array, output the result.
   */
  public void sort() {
    Vector<String> lines = new Vector();
    Map<String,String> map = new HashMap();

    try {
      BufferedReader reader = new BufferedReader(new FileReader(inputFile));
      String line = reader.readLine();

      // Save all lines
      while( line != null ) {
	String key = line.substring(0,line.indexOf(' '));
	map.put(key, line);
	line = reader.readLine();	
      }
      reader.close();
    } catch( Exception ex ) { ex.printStackTrace(); }

    // Sort
    String arr[] = new String[map.size()];
    map.keySet().toArray(arr);
    Arrays.sort(arr);
    
    // Write to STDOUT
    for( int i = 0, n = arr.length; i < n; i++ ) {
      System.out.println(map.get(arr[i]));
    }
  }
  

  /**
   * Main
   */
  public static void main(String[] args) {
    if( args.length > 0 ) {
      SortArgs sorter = new SortArgs(args[0]);
      sorter.sort();
    }
  }
}