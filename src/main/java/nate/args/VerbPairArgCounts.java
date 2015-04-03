package nate.args;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * This class reads formatted files that contain argument counts.
 * The files are counts of head words that appear as arguments to pairs of
 * verbs that had coreferring arguments.  A hash from pairs to hashes
 * of argument counts is saved in memory.
 *
 * File line format:
 * arrest:convict;obj:obj white 1 rosa 2 mayor 1 judge 1 *per* 82 tibet 1
 *
 */
public class VerbPairArgCounts {
  // arrest:convict;obj:obj -> food/12
  Map<String, Map<String,Integer>> pairHash = null;

  // Counts that are *equal to or greater* are saved.
  int ARGUMENT_CUTOFF = 0;

  // The splitting character in the file. (most are tabs, my legacy files are spaces)
  final String spliton = "\t";

  /**
   * Constructor
   * @param cutoff Don't read arguments with counts below the cutoff.
   */
  public VerbPairArgCounts(String filename, int cutoff) {
    ARGUMENT_CUTOFF = cutoff;
    pairHash = new HashMap();
    fromFile(filename);
  }

  /**
   * Constructor
   * @param cutoff Don't read arguments with counts below the cutoff.
   * @param desiredVerbs Set of verbs that we are interested in.  We will
   *                     only load pairs that include a verb in the set.
   */
  public VerbPairArgCounts(String filename, int cutoff, Set<String> desiredVerbs) {
    ARGUMENT_CUTOFF = cutoff;
    pairHash = new HashMap();
    fromFile(filename, desiredVerbs);
  }

  /**
   * @param pair "arrest:convict;subj:subj"
   * @return The argument string with the highest count for this pair
   */
  public String getBestArgForPair(String pair) {
    String highArg = null;
    Map<String,Integer> args = getArgsForPair(pair);

    if( args != null ) {
      int high = 0;
      for( Map.Entry<String,Integer> entry : args.entrySet() ) {
        if( entry.getValue() > high ) {
          high = entry.getValue();
          highArg = entry.getKey();
        }
      }
    }
    return highArg;
  }

  public int size() {
    return pairHash.size();
  }

  /**
   * Given verbs and dependency types, put them into one string that
   * matches the hashmap keys.  It maintains alphabetical order.
   */
  public static String buildKey(String verb1, String dep1, String verb2, String dep2) {
    if( verb1.compareTo(verb2) > 0 ) {
      StringBuffer sb = new StringBuffer(verb2);
      sb.append(':');
      sb.append(verb1);
      sb.append(';');
      sb.append(dep2);
      sb.append(':');
      sb.append(dep1);
      return sb.toString();
      //      return verb2 + ":" + verb1 + ";" + dep2 + ":" + dep1;
    }
    else {
      StringBuffer sb = new StringBuffer(verb1);
      sb.append(':');
      sb.append(verb2);
      sb.append(';');
      sb.append(dep1);
      sb.append(':');
      sb.append(dep2);
      return sb.toString();
      //      return verb1 + ":" + verb2 + ";" + dep1 + ":" + dep2;
    }
  }

  /**
   * @param slot "arrest:convict;subj:subj"
   * @return A hashmap of argument heads with their counts
   */
  public Map<String,Integer> getArgsForPair(String pair) {
    return pairHash.get(pair);
  }

  /**
   * Returns the number of times the arg was seen with both events.
   * @param pair An event pair e.g. arrest:convict;subj:subj
   * @param arg An argument head string
   */
  public Integer getCount(String pair, String arg) {
    Map<String,Integer> args = pairHash.get(pair);
    if( args != null )
      return args.get(arg);
    else return null;
  }


  /**
   * Adds verb-slot counts for an argument to storage
   * @param pair A verb pair e.g. arrest:convict;subj:subj
   * @param counts Map of argument strings to their counts
   */
  public void put(String pair, Map<String,Integer> counts) {
    pairHash.put(pair, counts);
  }


  public Set<String> pairKeySet() {
    return pairHash.keySet();
  }

  public void fromFile(String filename) {
    fromFile(filename, null);
  }

  /**
   * Returns true if the given verb pair string contains two verbs, both of 
   * which are in the given set of desired verbs.  False otherwise.
   * @param pair Format is "arrest:convict;s:o"
   */
  private boolean containsDesiredVerb(Set<String> desiredVerbs, String pair) {
    int colon = pair.indexOf(':');
    String verb1 = pair.substring(0, colon);
    int semi = pair.indexOf(';');
    String verb2 = pair.substring(colon+1, semi);

    if( desiredVerbs.contains(verb1) && desiredVerbs.contains(verb2) )
      return true;

    return false;
  }

 
  /**
   * Read a hash of argument nouns to parent/counts<br>
   * e.g. "arrest:convict;obj:obj -> food/12"
   */
  public void fromFile(String filename, Set<String> desiredVerbs) {
    String line;
    pairHash.clear();

    if( desiredVerbs != null ) 
      System.out.println("Loading arg counts for " + desiredVerbs.size() + " verbs");
    else System.out.println("Loading all arg counts");

    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      int lines = 0;
      int saved = 0;

      while( (line = in.readLine()) != null ) {
        lines++;

        int tab = line.indexOf(spliton);
        if( tab == -1 ) {
          System.err.println("Bad line format: " + line);
          System.exit(1);
        }

        // Read the verb pair from the front of the line
        String pair = line.substring(0, tab);

        // See if we want this pair or no
        boolean savePair = true;
        if( desiredVerbs != null )
          savePair = containsDesiredVerb(desiredVerbs, pair);

        if( savePair ) {
          saved++;

          // Sanity check
          if( pairHash.containsKey(pair) ) {
            System.out.println("Repeated key? " + pair);
            System.exit(1);
          }

          // Create the arguments hash
          Map<String,Integer> pairArgCounts = new HashMap();
          pairHash.put(pair, pairArgCounts);

          // Read the arguments from the end of the line
          //	  System.out.println("line = " + line);
          String[] parts = line.substring(tab+1).split(spliton);
          for( int i = 0, n = parts.length; i < n; i += 2 ) {
            int count = Integer.parseInt(parts[i+1]);
            if( count >= ARGUMENT_CUTOFF )
              pairArgCounts.put(parts[i], count);
          }
        } // if savePair
      } // while

      in.close();
      System.out.println("Saved " + saved + " of " + lines + " lines");
    } catch(Exception ex) { ex.printStackTrace(); }
  }

  public void clear() {
    pairHash.clear();
  }
}