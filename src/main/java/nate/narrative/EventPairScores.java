package nate.narrative;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nate.BasicEventAnalyzer;
import nate.Pair;
import nate.WordEvent;
import nate.WordIndex;
import nate.util.SortableScore;
import nate.util.Util;


/**
 * An interface to reading relation scores between pairs of events
 *
 * Reads event/dep pair scores from a score file.
 *   1.000 9351 	 comment:decline;obj:subj
 *   0.868 6546 	 buy:sell;subj:subj
 *   0.863 4992 	 fell:rise;subj:subj
 *
 */
public class EventPairScores implements ScoreCache {
  Map<String,Map<String,Float>> _scores = new HashMap<String, Map<String, Float>>();
  boolean alphabetized = false; // set to true to save only in alphabetical order
  public static final String separator = "-";

  /**
   * Constructors
   */
  public EventPairScores() {
  }
  public EventPairScores(String filename) {
    System.out.println("Loading scores from: " + filename);
    fromFile(filename, null, 0.0f, 0, alphabetized);
  }
  public EventPairScores(String filename, WordIndex index) {
    fromFile(filename, index, 0.0f, 0, alphabetized);
  }
  public EventPairScores(String filename, WordIndex index, float scoreCutoff) {
    fromFile(filename, index, scoreCutoff, 0, alphabetized);
  }
  public EventPairScores(String filename, WordIndex index, 
      float scoreCutoff, int countCutoff) {
    fromFile(filename, index, scoreCutoff, countCutoff, alphabetized);
  }


  /**
   * This function doesn't return all neighbors if alphabetical order is on
   * @returns The neighbors of an event.
   */
  public Map<String,Float> getNeighbors(String key) {
    if( alphabetized ) System.out.println("WARNING: returning neighbors from an alphabetized store. All neighbors won't be returned!");
    return _scores.get(key);
  }

  /**
   * This function doesn't return all neighbors if alphabetical order is on
   * @returns The neighbors of an event.
   */
  public Map<String,Float> getNeighbors(String verb, String dep) {
    if( alphabetized ) System.out.println("WARNING: returning neighbors from an alphabetized store. All neighbors won't be returned!");
    return _scores.get(verb + separator + dep);
  }

  /**
   * Returns neighbors (as a set of strings) of a given vector of events.
   * Any verb seen with any of the given events is returned.
   * This function doesn't return all neighbors if alphabetical order is on
   * @returns The neighbors of an event.
   */
  public Set<String> getNeighbors(Collection<String> keys) {
    Set<String> allNeighbors = new HashSet<String>();

    if( alphabetized ) System.out.println("WARNING: returning neighbors from an alphabetized store. All neighbors won't be returned!");

    for( String key : keys ) {
      Map<String,Float> neighbors = _scores.get(key);
      if( neighbors != null ) {
        for( Map.Entry<String,Float> entry : neighbors.entrySet() ) {
          allNeighbors.add(entry.getKey());
        }
      }
    }

    return allNeighbors;
  }

  /**
   * Returns the verb and dependency from a string representation of an event.
   * This allows our class to use whatever string format it wants.
   * @return A Pair with strings verb and dependency
   */
  public static Pair split(String event) {
    int hyphen = event.lastIndexOf('-');
    if( hyphen > -1 ) {
      String verb = event.substring(0, hyphen);
      String dep  = event.substring(hyphen+1);
      return new Pair(verb, dep);
    } else {
      return null;
    }
  }

  public static String buildKey(String verb, String dep) {
    return verb + separator + dep;
  }

  /**
   * A convenience function that just merges the verbs with their 
   * typed dependencies so we can lookup the score.
   */
  public float getScore(String verb1, String verb2, String dep1, String dep2) {
    String key1 = verb1 + separator + WordEvent.normalizeRelation(dep1);
    String key2 = verb2 + separator + WordEvent.normalizeRelation(dep2);
    return getScore(key1,key2);
  }

  /**
   * Looks up a string verb and its dep compared against another verb/dep
   * @param key1 A verb-role string  e.g. "eat-o"
   * @param key2 A verb-role string  e.g. "eat-o"
   * @returns A similarity score
   */
  public float getScore(String key1, String key2) {
    // alphabetize the order	  
    if( key1.compareTo(key2) > 0 ) {
      String temp = key1;
      key1 = key2;
      key2 = temp;
    }

    // lookup and return
    Map<String,Float> counts = _scores.get(key1);
    if( counts != null ) {
      Float score = counts.get(key2);
      if( score != null ) return score;
    }

    // not found
    return 0;
  }

  /**
   * Takes a string in the format: "improve:put;o:p" and returns two
   * strings, "improve-o" and "improve-p" in a Pair object.
   */
  public Pair splitStringPair(String pair) {
    int semicolon = pair.indexOf(';');
    String verbpair = pair.substring(0,semicolon);
    String deps = pair.substring(semicolon+1);

    int colon = verbpair.indexOf(':');
    String v1 = verbpair.substring(0,colon);
    String v2 = verbpair.substring(colon+1);

    colon = deps.indexOf(':');
    String dep1 = WordEvent.normalizeRelation(deps.substring(0,colon));
    String dep2 = WordEvent.normalizeRelation(deps.substring(colon+1));

    String key1 = v1 + separator + dep1;
    String key2 = v2 + separator + dep2;
    return new Pair(key1, key2);
  }

  /**
   * Given a map from string pairs to scores, this function splits the pairs
   * into individual events and stores them indexed separately.
   * The parameter's string pair format is: "improve:put;o:p".
   */
  public void fromStringPairs(Map<String,Double> scores) {
    clear();
    for( Map.Entry<String,Double> entry : scores.entrySet() ) {
      Pair pair = splitStringPair(entry.getKey());
      // Add both orders, so the lookups for "getNeighbors" always returns
      // all possible neighbors.
      addScore((String)pair.first(), (String)pair.second(), entry.getValue().floatValue());
      addScore((String)pair.second(), (String)pair.first(), entry.getValue().floatValue());
    }
  }

  public void fromFile(String filename, WordIndex index, 
      float scoreCutoff, int countCutoff, boolean alphabetize) {
    fromFile(filename, index, scoreCutoff, countCutoff, alphabetize, null);
  }

  /**
   * Read the scores from a file 
   * @param scoreCutoff Ignore pairs with scores below this cutoff.
   * @param countCutoff Ignore pairs with seen counts below this number.
   * @param alphabetize Set to true if you want the pairs saved in alphabetical order.
   *        This saves lots of memory, but disables the getNeighbors function!
   * @param mainVerb The only verb we care about, ignore all other pairs
   * @return A set of verbs that are seen with our main verb
   */
  public Set<String> fromFile(String filename, WordIndex index, 
      float scoreCutoff, int countCutoff,
      boolean alphabetize, String mainVerb) {
    Set<String> goodNeighbors = null;
    alphabetized = alphabetize;
    _scores.clear();
    String line;
    int numLines = 0;

    if( index == null ) System.out.println("WARNING: no word to ID index given in EventPairScores");

    // If we are saving only desired pairs
    if( mainVerb != null ) {
      System.out.println("Only loading pairs with the verb " + mainVerb);
      goodNeighbors = new HashSet<String>();
    }

    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));

      while( (line = in.readLine()) != null ) {
        // not a blank line
        if( !line.matches("^\\s*$") ) {
          numLines++;
          String parts[] = line.trim().split("\\s+");

          float score = Float.valueOf(parts[0]);
          int numseen = Integer.valueOf(parts[1]);

          // don't record scores below the cutoff
          if( score >= scoreCutoff && numseen >= countCutoff) {

            // parts[2] = comment:decline;obj:subj
            int colon = parts[2].indexOf(':');
            int semi  = parts[2].indexOf(';');

            String verb1 = parts[2].substring(0,colon);
            String verb2 = parts[2].substring(colon+1,semi);
            if( index != null ) verb1 = index.get(verb1).toString();
            if( index != null ) verb2 = index.get(verb2).toString();

            // Only save the pair if the main verb is there
            if( mainVerb == null ||
                verb1.equals(mainVerb) || verb2.equals(mainVerb) ) {

              // Save good neighbors to return later
              if( mainVerb != null && verb1.equals(mainVerb) ) goodNeighbors.add(verb2);
              if( mainVerb != null && verb2.equals(mainVerb) ) goodNeighbors.add(verb1);

              colon = parts[2].indexOf(':',semi);
              String dep1 = WordEvent.normalizeRelation(parts[2].substring(semi+1,colon));
              String dep2 = WordEvent.normalizeRelation(parts[2].substring(colon+1));
              String key1 = verb1 + separator + dep1;
              String key2 = verb2 + separator + dep2;

              //	      System.out.println("key1=" + key1 + " key2=" + key2);
              // alphabetize the order	  
              if( alphabetized && key1.compareTo(key2) > 0 ) {
                String temp = key1;
                key1 = key2;
                key2 = temp;
                addScore(key1, key2, score);
              }
              else {
                addScore(key1, key2, score);
                addScore(key2, key1, score);
              }
            }	    
          }
        }
      }
      in.close();
    } catch(Exception ex) { ex.printStackTrace(); }

    // Now read in all the pairs with these neighbors.
    // Building narratives must compare the neighbors to each other, so we need all
    // pairs that include the main verb *or* a neighbor of the main verb.
    if( mainVerb != null ) {
      int numSaved = fromFileDesired(filename, index, scoreCutoff, countCutoff,
          alphabetize, goodNeighbors);
      System.out.println("Saved " + numSaved + " of " + numLines + " lines");
    }

    return goodNeighbors;
  }


  /**
   * Read the scores from a file for only a specified set of verbs.  This function does
   * NOT clear the pairs' scores hash table, but rather adds to it.
   *
   * @param scoreCutoff Ignore scores below the given cutoff
   * @param countCutoff Ignore pairs with seen counts below this number.
   * @param alphabetize Set to true if you want the pairs saved in alphabetical order.
   *        This saves lots of memory, but disables the getNeighbors function!
   * @param desiredVerbs A set of verbs we care about, ignore all other pairs
   * @return The number of lines that it saved.
   */
  public int fromFileDesired(String filename, WordIndex index, 
      float scoreCutoff, int countCutoff,
      boolean alphabetize, Set<String> desiredVerbs) {
    int numSaved = 0;
    alphabetized = alphabetize;
    //    _scores.clear();
    String line;

    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));

      while( (line = in.readLine()) != null ) {
        // not a blank line
        if( !line.matches("^\\s*$") ) {
          String parts[] = line.trim().split("\\s+");

          float score = Float.valueOf(parts[0]);
          int numseen = Integer.valueOf(parts[1]);

          // don't record scores below the cutoff
          if( score >= scoreCutoff && numseen >= countCutoff ) {

            // parts[2] = comment:decline;obj:subj
            int colon = parts[2].indexOf(':');
            int semi  = parts[2].indexOf(';');

            String verb1 = parts[2].substring(0,colon);
            String verb2 = parts[2].substring(colon+1,semi);
            if( index != null ) verb1 = index.get(verb1).toString();
            if( index != null ) verb2 = index.get(verb2).toString();

            // Only save the pair if the main verb is there
            if( desiredVerbs == null ||
                desiredVerbs.contains(verb1) || desiredVerbs.contains(verb2) ) {
              numSaved++;

              // Split the relation pair, e.g. "s:o"
              colon = parts[2].indexOf(':',semi);
              String dep1 = WordEvent.normalizeRelation(parts[2].substring(semi+1,colon));
              String dep2 = WordEvent.normalizeRelation(parts[2].substring(colon+1));
              String key1 = verb1 + separator + dep1;
              String key2 = verb2 + separator + dep2;

              // alphabetize the order	  
              if( alphabetized && key1.compareTo(key2) > 0 ) {
                String temp = key1;
                key1 = key2;
                key2 = temp;
                addScore(key1, key2, score);
              }
              else {
                addScore(key1, key2, score);
                addScore(key2, key1, score);
              }
            }	    
          }
        }
      }
      in.close();
    } catch(Exception ex) { ex.printStackTrace(); }

    return numSaved;
  }

  /**
   * @return The event pair with the highest score.
   */
  public String highestScore() {
    String best = null;
    float bestscore = 0.0f;

    for( String str : _scores.keySet() ) {
      Map<String,Float> events = _scores.get(str);
      for( String str2 : events.keySet() ) {
        Float score = events.get(str2);
        if( score > bestscore ) {
          bestscore = score;
          best = str;
        }
      }
    }
    return best;
  }

  public Set<String> keySet() {
    return getSingleEvents();
  }
  
  /**
   * @return All of the event strings involved in any pairs this class has.
   */
  public Set<String> getSingleEvents() {
    Set<String> events = new HashSet<String>();
    for( Map.Entry<String,Map<String,Float>> entry : _scores.entrySet() ) {
      events.add(entry.getKey());
      for( String sib : entry.getValue().keySet() )
        events.add(sib);
    }
    return events;
  }

  /**
   * Saves the score of an event pair key1 and key2. 
   */
  public void addScore(String key1, String key2, float score) {
    Map<String,Float> counts;
    if( _scores.containsKey(key1) ) counts = _scores.get(key1);
    else {
      counts = new HashMap<String, Float>();
      _scores.put(key1,counts);
    }
    counts.put(key2,score);
  }

  public void setScore(String key1, String key2, float score) {
    addScoreSorted(key1, key2, score);
  }
  
  /**
   * Saves the score of an event pair key1 and key2 in sorted order. 
   */
  public void addScoreSorted(String key1, String key2, float score) {
    if( key1.compareTo(key2) > 0 )
      addScore(key2, key1, score);
    else
      addScore(key1, key2, score);
  }
  
  /**
   * Removes any pairs that contain the given key.
   */
  public void removeKey(String key) {
    // Remove all pairs starting with the key.
    _scores.remove(key);
    
    // Remove all pairs ending with the key.
    for( Map.Entry<String,Map<String,Float>> entry : _scores.entrySet() ) {
      Map<String,Float> scores = entry.getValue();
      scores.remove(key);
    }
  }
  
  public void clear() {
    _scores.clear();
  }

  public int size() {
    return _scores.size();
  }

  /**
   * Print the pairs with their scores in sorted order.
   */
  public void printSorted() {
    printSorted(Integer.MAX_VALUE);
  }
  public void printSorted(int maxpairs) {
    List<SortableScore> scores = new ArrayList<SortableScore>();
    for( Map.Entry<String,Map<String,Float>> entry : _scores.entrySet() ) {
      for( Map.Entry<String,Float> entry2 : entry.getValue().entrySet() ) {
        String pair = entry.getKey() + "\t" + entry2.getKey();
        scores.add(new SortableScore(entry2.getValue(), pair));
      }
    }
    System.out.println("Num pairs: " + scores.size());
    
    // Sort and Print.
    SortableScore[] arr = new SortableScore[scores.size()];
    arr = scores.toArray(arr);
    Arrays.sort(arr);
    int num = 0;
    for( SortableScore scored : arr ) {
      if( scored.score() > 0.0f ) { 
        System.out.printf("%s\t%.1f\n", scored.key(), scored.score());
        num++;
      }
      if( num >= maxpairs ) break;
    }
  }
  
  
  /**
   * Main: Just for debugging.
   */
  public static void main(String[] args) {
    EventPairScores scores = new EventPairScores();
    scores.fromFile(args[0], null, 0.0f, 0, true);

    // testing...
    Util.reportMemory();
    System.out.println(scores.getScore("abandoning","opposes","obj","subj"));
    System.out.println(scores.getScore("opposes","abandoning","subj","obj"));
    System.out.println(scores.getScore("abandoning","opposes","o","s"));
    System.out.println(scores.getScore("convicted","conspired","o","s"));
  }
}
