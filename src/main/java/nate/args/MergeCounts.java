package nate.args;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import nate.util.HandleParameters;
import nate.util.SortableScore;


/**
 * This class reads a directory of files that contain strings with lists
 * of arguments and their counts on each line, one pair per line.
 * The pairs must be in sorted order.  This code merges all of the files into
 * one single file, maintaining sorted order, and summing all common argument
 * counts.  It merges all files containing "argcounts" in their name.
 *
 * Input: <dir>
 * A directory of files with counts.
 * 
 * Count file example line:
 *   string-no-white-space arg1 34 arg2 9 arg3 1 arg4 88 ...
 *
 * Output: a single text file "merged.all"
 * The format is the same as the input format, but the argument counts are
 * summed across all input files with the same leading string.
 * 
 * MergeCounts [-output <path>] [-cutoff <int>] <dir>
 * 
 * -output
 * -cutoff
 * 
 */
public class MergeCounts {
  String _outputFile = "/jude17/scr1/natec/merged.all";
  String _inputDir;
  // Count is written to file if it matches or is greater than this number.
  int _outputCutoff = 2; // with coref
//  int _outputCutoff = 3; // all pairs

  /**
   * Constructor
   */
  MergeCounts(String[] args) {
    HandleParameters params = new HandleParameters(args);

    if( params.hasFlag("-output") )
      _outputFile = params.get("-output");
    System.out.println("will output to " + _outputFile);

    if( params.hasFlag("-cutoff") )
      _outputCutoff = Integer.valueOf(params.get("-cutoff"));
    System.out.println("outputCutoff\t" + _outputCutoff);

    _inputDir = args[args.length-1];
  }


  /**
   * Merges all of the argcounts* files in the inputDir directory
   * into a single file.  The files must be in sorted order for
   * this to work.
   */
  public void merge() {

    File dir = new File(_inputDir);
    if( dir.isDirectory() ) {
      String line;
      String files[] = dir.list();
      Arrays.sort(files);

      // Create and open readers for all files
      Vector<BufferedReader> inputs = new Vector<BufferedReader>();
      for( String file : files ) {
        if( !file.startsWith(".") && file.contains("counts") ) {
          // Create a reader for each file
          try {
            System.out.println("Opening " + file);
            inputs.add(new BufferedReader(new FileReader(_inputDir + File.separator + file)));
          } catch( Exception ex ) { ex.printStackTrace(); System.exit(1); }
        }
      }

      try {
        // Create the output buffer
        BufferedWriter out = new BufferedWriter(new FileWriter(_outputFile));      

        // Read the first line from each input buffer
        String[] pairs = new String[inputs.size()];
        Map<String,Integer>[] countMaps = new HashMap[inputs.size()];
        int i = 0;
        for( BufferedReader reader : inputs ) {
          line = reader.readLine();
//          System.out.println("line " + line);
//          while( line.matches("NUMDOCS .+") || line.matches("\\s*") || !line.matches(".+\t.+") ) {
          while( !line.matches(".+\t.+") ) {
            line = reader.readLine();
//            System.out.println("now line " + line);
          }

          pairs[i] = line.substring(0,line.indexOf('\t'));
          countMaps[i] = new HashMap<String, Integer>();
          countsFromLine(line, countMaps[i]);
          System.out.println("i " + pairs[i]);
          i++;
        }

        // Loop until all input buffers are exhausted
        int numlines = 0;
        int finished = 0;
        while( finished < pairs.length ) {

          // Find the first line alphabetically
          String lowest = "zzzzzzzz";
          for( String pair : pairs ) {
            if( !pair.equals("*finished*") && pair.compareTo(lowest) < 0 )
              lowest = pair;
          }

          // Sum all matching pairs
          Map<String,Integer> sum = new HashMap<String, Integer>();
          int yy = 0;
          for( String pair : pairs ) {

            if( pair.equals(lowest) ) {
              // Sum the two maps together
              mergeMaps(sum, countMaps[yy]);

              // Read the next line in this input
              line = inputs.elementAt(yy).readLine();
              while( line != null && !line.matches(".+\t.+") )
                line = inputs.elementAt(yy).readLine();
              //	      System.out.println("Read " + line);
              if( line == null ) {
                pairs[yy] = "*finished*";
                finished++;
              } else {
                pairs[yy] = line.substring(0,line.indexOf('\t'));
                countsFromLine(line, countMaps[yy]);
              }
            }
            yy++;
          }

          // Write this one to file
          System.out.println("Writing " + lowest);
          writeCounts(out, lowest, sum);

          numlines++;
        } // while active buffers exist

        out.close();
        System.out.println("wrote to file " + _outputFile);
      } catch( Exception ex ) { ex.printStackTrace(); System.exit(1); }

    } else {
      System.out.println(dir + " is not a directory");
    }
  }


  /**
   * Destructively adds the counts in mergee to the counts in merger.  We change
   * the counts in merger to be the sum, but mergee will stay the same.
   */
  private void mergeMaps(Map<String,Integer> merger, Map<String,Integer> mergee) {
    for( String key : merger.keySet() ) {
      if( mergee.containsKey(key) )
        merger.put(key, merger.get(key) + mergee.get(key));
    }

    for( String key : mergee.keySet() ) {
      if( !merger.containsKey(key) )
        merger.put(key, mergee.get(key));
    }    
  }


  /**
   * Write the given pair and counts to the given output buffer.
   */
  private void writeCounts(BufferedWriter out, String pair, Map<String,Integer> counts) {
    if( counts.size() > 0 ) {
      SortableScore[] scores = new SortableScore[counts.size()];
      int i = 0;
      for( Map.Entry<String,Integer> entry : counts.entrySet() )
        scores[i++] = new SortableScore(entry.getValue(), entry.getKey());
      Arrays.sort(scores);

      if( (int)scores[0].score() >= _outputCutoff ) {
        try {
          out.write(pair);
          for( SortableScore score : scores ) {
            if( (int)score.score() >= _outputCutoff )
              out.write("\t" + score.key() + "\t" + (int)score.score());
            else break;
          }
          out.write("\n");
        } catch( Exception ex ) { ex.printStackTrace(); System.exit(1); }
      }
    }
  }


  /**
   * DESTRUCTIVE: fills a hashmap of strings to counts, splitting the counts from the
   * given line: "verb-pair canwest 1 *per* 1  attorney 1 department 2"
   */
  private void countsFromLine(String line, Map<String,Integer> counts) {
    String[] parts = line.split("\\s+");
    counts.clear();

    if( parts.length > 1 ) {
      if( parts.length % 2 == 1 ) {
        for( int i = 1, n = parts.length; i < n; i += 2 ) {
          int count = Integer.parseInt(parts[i+1]);
          counts.put(parts[i], count);
        }
      }
      else System.out.println("WARNING: pair counts not odd: " + line);
    }
    else System.out.println("WARNING: short line: " + line);
  }


  /**
   * Main
   */
  public static void main(String[] args) {
    if( args.length > 0 ) {
      MergeCounts merger = new MergeCounts(args);
      merger.merge();
    }
    else {
      System.out.println("MergeCounts [-output <path>] [-cutoff <int>] <dir>");
    }
  }
}