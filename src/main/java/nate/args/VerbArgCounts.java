package nate.args;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import nate.util.Util;


/**
 * This class reads formatted files that contain argument counts.
 * The files are counts of head words that appear as arguments to single
 * verbs.  A hash from verb slots to hashes of argument counts is saved in memory.
 *
 * File line format:
 * arrest:s white 1 rosa 2 mayor 1 judge 1 *per* 82 tibet 1
 *
 */
public class VerbArgCounts {
  // eat:o -> food/12
  Map<String, Map<String,Integer>> _verbHash = null;
  // food -> eat-o/12
  Map<String, Map<String,Integer>> _argHash = null;


  // Counts that are *equal to or greater* are saved.
  int ARGUMENT_CUTOFF = 0;

  public VerbArgCounts() { 
    _verbHash = new HashMap<String, Map<String,Integer>>();
  }

  /**
   * Constructor
   * @param cutoff Don't read arguments with counts below the cutoff.
   */
  public VerbArgCounts(String filename, int cutoff) {
    ARGUMENT_CUTOFF = cutoff;
    _verbHash = new HashMap<String, Map<String,Integer>>();
    fromFile(filename);
  }

  /**
   * Constructor
   * @param cutoff Don't read arguments with counts below the cutoff.
   * @param desiredVerbs Set of verbs that we are interested in.  We will
   *                     only load pairs that include a verb in the set.
   * @param bothdesired If true, then loading from file will only load pairs where both
   *                    words are in the list, not just one.  False means only one.        
   */
  public VerbArgCounts(String filename, int cutoff, Set<String> desiredVerbs) {
    ARGUMENT_CUTOFF = cutoff;
    _verbHash = new HashMap<String, Map<String,Integer>>();
    fromFile(filename, desiredVerbs);
  }

//  public static VerbArgCounts invertCounts(VerbArgCounts argcounts) {
//    VerbArgCounts inverted = new VerbArgCounts();
//    for( String slot : argcounts.keySet() ) {
//      Map<String,Integer> args = argcounts.getArgsForSlot(slot);
//      for( Map.Entry<String, Integer> entry : args.entrySet() ) {
//        inverted.incrementCount(slot, entry.getKey(), entry.getValue());
//      }
//    }
//  }
//
//  public void incrementCount(String slot, String arg, int count) {
//
//  }
  
  /**
   * @return All the slots.
   */
  public Set<String> getAllSlots() {
    if( _verbHash != null )
      return _verbHash.keySet();
    else
      return null;
  }
  
  /**
   * @return All the root tokens that form slots. (e.g. return v-claim for the slot v-claim:s)
   */
  public Set<String> getAllSlotTokens() {
    if( _verbHash != null ) {
      Set<String> tokens = new HashSet<String>();
      for( String slot : _verbHash.keySet() ) {
//        System.out.println("allslots slot " + slot + " substr " + slot.substring(0, slot.lastIndexOf(':')));
        tokens.add(slot.substring(0, slot.lastIndexOf(':')));
      }
      return tokens;
    }
    else
      return null;
  }

  /**
   * @param slot "arrest:s"
   * @return The argument string with the highest count in this slot.
   */
  public String getBestArgForPair(String slot) {
    String highArg = null;
    Map<String,Integer> args = getArgsForSlot(slot);

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
    return _verbHash.size();
  }

  /**
   * @param slot "arrest:s"
   * @return A hashmap of argument heads with their counts
   */
  public Map<String,Integer> getArgsForSlot(String slot) {
    return _verbHash.get(slot);
  }

  public Map<String,Integer> getArgsForSlot(String verb, String role) {
    return getArgsForSlot(buildKey(verb, role));
  }

  /**
   * Remove an argument's count from a slot.
   */
  public void removeArgFromSlot(String slot, String arg) {
    if( _verbHash != null ) {
      Map<String,Integer> argCounts = _verbHash.get(slot);
      if( argCounts != null )
        argCounts.remove(arg);
    }
    
    if( _argHash != null ) {
      Map<String,Integer> slotCounts = _verbHash.get(arg);
      if( slotCounts != null ) {
        slotCounts.remove(slot);
      }
    }
  }
  
  public static String buildKey(String verb, String role) {
    return verb + ":" + role;
  }

  /**
   * Returns the number of times the arg was seen in this slot.
   * @param slot A verb's slot e.g. arrest:s
   * @param arg An argument head string
   */
  public int getCount(String slot, String arg) {
    Map<String,Integer> args = _verbHash.get(slot);
    if( args != null ) {
      Integer count = args.get(arg);
      if( count == null ) return 0;
      else return count.intValue();
    }
    else return 0;
  }


  /**
   * Adds verb-slot counts for an argument to storage.
   * @param pair A verb slot e.g. arrest:s
   * @param counts Map of argument strings to their counts
   */
  public void put(String slot, Map<String,Integer> counts) {
    _verbHash.put(slot, counts);
  }

  public boolean containsSlot(String slot) {
    if( _verbHash != null && _verbHash.containsKey(slot) )
      return true;
    else
      return false;
  }

  public Set<String> keySet() {
    return _verbHash.keySet();
  }

  public void fromFile(String filename) {
    fromFile(filename, null);
  }

  /**
   * Returns true if the given set of verbs contains the verb in the given
   * slot.  False otherwise.
   * @param slot Format is "arrest:s"
   */
  private boolean containsDesiredVerb(Set<String> desiredVerbs, String slot) {
    int colon = slot.indexOf(':');
    String verb = slot.substring(0, colon);

    if( desiredVerbs.contains(verb) ) return true;
    else return false;
  }


  /**
   * @return A hashmap of verb slots in which this argument appeared
   */
  public Map<String,Integer> getSlots(String arg) {
    if( _argHash == null ) {
      System.out.println("Building the inverse arg->verbcount table");
      _argHash = new HashMap<String, Map<String, Integer>>();
      buildArgHash();
    }
    //    System.out.println("getSlots for arg " + arg);
    return _argHash.get(arg);
  }

  /**
   * Adds their counts to ours.
   */
  public void addCounts(VerbArgCounts counts) {
    _argHash = null;
    
    for( String slot : counts.keySet() ) {
      // Get our current argument counts for this slot.
      Map<String,Integer> mySlotCounts = _verbHash.get(slot);
      if( mySlotCounts == null ) {
        mySlotCounts = new HashMap<String,Integer>();
        put(slot, mySlotCounts);
      }
      
      // Add their argument counts to ours.
      Map<String,Integer> theirSlotCounts = counts.getArgsForSlot(slot);
      for( Map.Entry<String, Integer> entry : theirSlotCounts.entrySet() )
        Util.incrementCount(mySlotCounts, entry.getKey(), entry.getValue());
    }
  }
  
  /**
   * Assuming the slot->argcount hash is built, invert the table and build
   * the arg->slotcount table.
   */
  private void buildArgHash() {
    //    System.out.println("building inverse");
    for( Map.Entry<String, Map<String,Integer>> entry : _verbHash.entrySet() ) {
      String verb = entry.getKey();
      //      System.out.println("verb = " + verb);
      for( Map.Entry<String,Integer> entry2 : entry.getValue().entrySet() ) {
        String arg = entry2.getKey();
        Integer count = entry2.getValue();

        //	System.out.println("arg = " + arg + " = " + count);

        Map<String,Integer> counts = _argHash.get(arg);
        if( counts == null ) {
          counts = new HashMap<String, Integer>();
          _argHash.put(arg, counts);
        }
        counts.put(verb, count);
      }
    }
  }

  /**
   * Read a hash of argument nouns to parent/counts<br>
   * e.g. "arrest:s -> food/12"
   * @param bothdesired If true, then both tokens in the pair must be in the desired list.
   *                    If false, only one token in the pair must be in the desired list.
   */
  public void fromFile(String filename, Set<String> desiredVerbs) {
    String line;
    clear();

    if( desiredVerbs != null ) 
      System.out.println("Loading arg counts for " + desiredVerbs.size() + " verbs");
    else System.out.println("Loading all arg counts");

    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      int lines = 0;
      int saved = 0;

      while( (line = in.readLine()) != null ) {
        lines++;

        int tab = line.indexOf('\t');
        if( tab == -1 ) {
          System.err.println("Bad line format: " + line);
          System.exit(1);
        }

        // Read the verb pair from the front of the line
        String slot = line.substring(0, tab);

        // See if we want this pair or no
        boolean saveSlot = true;
        if( desiredVerbs != null )
          saveSlot = containsDesiredVerb(desiredVerbs, slot);

        if( saveSlot ) {
          saved++;

          // Sanity check
          if( _verbHash.containsKey(slot) ) {
            System.out.println("Repeated key? " + slot);
            System.exit(1);
          }

          // Create the arguments hash
          Map<String,Integer> slotArgCounts = new HashMap<String, Integer>();
          _verbHash.put(slot, slotArgCounts);

          // Read the arguments from the end of the line
          //	  System.out.println("line = " + line);
          String[] parts = line.substring(tab+1).split("\t");
          for( int i = 0, n = parts.length; i < n; i += 2 ) {
            int count = Integer.parseInt(parts[i+1]);
            if( count >= ARGUMENT_CUTOFF )
              slotArgCounts.put(parts[i], count);
          }
        } // if saveSlot
      } // while

      in.close();
      System.out.println("Saved " + saved + " of " + lines + " lines");
    } catch(Exception ex) { ex.printStackTrace(); }
  }

  /**
   * Find all keys in the map that start with the given string.
   */
  public Set<String> keysThatStartWith(String start) {
    Set<String> keys = new HashSet<String>();
    for( Map.Entry<String, Map<String,Integer>> entry : _verbHash.entrySet() ) {
      if( entry.getKey().startsWith(start) )
        keys.add(entry.getKey());
    }
    return keys;
  }

  public void setVerbHash(Map<String, Map<String,Integer>> verbHash) {
    _verbHash = verbHash;
    // TODO: this should really be created here, if we need it for something.
    _argHash = null;
  }
  
  public void clear() {
    if( _verbHash != null ) _verbHash.clear();
    if( _argHash != null ) _argHash.clear();
  }
}
