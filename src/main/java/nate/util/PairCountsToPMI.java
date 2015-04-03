package nate.util;

import java.util.Map;
import java.util.HashMap;
import java.util.Vector;
import java.util.Arrays;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;

import nate.IDFMap;
import nate.util.SortableObject;

/**
 * Class that reads a file of pair counts and uses the IDF counts to then
 * calculate PMI scores for each pair.  It then creates a new file of
 * PMI scores for every pair in the counts file.
 *   INPUT:  1. File of counts, each line of the format: "verb-pair count"
 *           2. IDF of words to calculate corpus counts.
 *   OUTPUT: 1. File of pmi scores, line format: "verb-pair pmi-score"
 */
public class PairCountsToPMI {
  private IDFMap _idf;
  private String _output = "pmitemp.pmi";
  private String _input = null;


  PairCountsToPMI(String args[]) {
    HandleParameters params = new HandleParameters(args);

    // Read the IDF file.
    if( !params.hasFlag("-idf") ) {
      System.out.println("No IDF given");
      System.exit(1);
    } else _idf = new IDFMap(params.get("-idf"));

    // Check for an output file.
    if( params.hasFlag("-output") ) _output = params.get("-output");
    // Input file, the file of event pair counts.
    _input = args[args.length-1];
  }

  /**
   * Read the counts from file.
   */
  private Map<String,Integer> readCounts(String filename) {
    Map<String,Integer> counts = new HashMap();
    int lines = 0;

    try {
      BufferedReader in;
      in = new BufferedReader(new FileReader(filename));
      
      String line;
      while ( (line = in.readLine()) != null ) {
	// abandon:firebomb;s:s 2
	String parts[] = line.split(" ");
	if( parts.length != 2 ) {
	  System.out.println("Unknown line format: " + line);
	  System.exit(1);
	}
	// Save this count.
	counts.put(parts[0], Integer.parseInt(parts[1]));
	lines++;
      }
      in.close();
    } catch( Exception ex ) { ex.printStackTrace(); }

    System.out.println("Read " + lines + " lines");
    return counts;
  }

  /**
   * Write the pmi scores to the given output path.
   */
  private void writePMIs(Map<String,Integer> counts, Map<String,Double> pmis, String outputPath) {

    // Find the max/min PMI scores.
    double max = -100.0f;
    double min =  100.0f;
    for( Map.Entry<String,Double> entry : pmis.entrySet() ) {
      double pmi = entry.getValue();
      if( pmi > max ) max = pmi;
      else if( pmi < min ) min = pmi;
    }
    double range = max - min;
    
    // Normalize the PMI scores to [0,1].
    Vector<SortableObject> scores = new Vector();
    for( Map.Entry<String,Double> entry : pmis.entrySet() ) {
      double pmi = entry.getValue();
      double norm = (pmi - min) / range;
      scores.add(new SortableObject(norm, 
				    counts.get(entry.getKey()) + "\t" + entry.getKey()));
    }

    // Clear up memory for the sort?
    counts.clear();
    pmis.clear();
    
    // Sort the PMI scores.
    SortableObject arr[] = new SortableObject[scores.size()];
    arr = scores.toArray(arr);
    Arrays.sort(arr);
    
    // Print the PMI scores to file.
    try {      
      PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outputPath)));
      for( SortableObject score : arr ) {
	writer.printf("%.6f\t%s\n", score.score(), score.key());
      }
      writer.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
  }

  /**
   * Use the global input and output paths to read the pair counts from
   * file, calculate PMI scores, and then output the scores to a new file.
   */
  public void calculate() {
    // Read the counts from file.
    System.out.println("Reading counts from " + _input);
    Map<String,Integer> counts = readCounts(_input);

    // Convert the counts to PMI.
    System.out.println("Calculating pmi scores...");
    PMICalculator calc = new PMICalculator();
    calc.setVerbFrequencyCutoff(10);
    Map<String,Double> pmis = calc.intPairCountsToPMI(counts, _idf);
    PMICalculator.reduceMaxPMI(pmis, 14.0);

    // Save to file.
    System.out.println("Writing pmis to " + _output + " ...");
    writePMIs(counts, pmis, _output);
    System.out.println("...done.");
  }


  public static void main(String args[]) {
    PairCountsToPMI converter = new PairCountsToPMI(args);
    converter.calculate();
  }
}
