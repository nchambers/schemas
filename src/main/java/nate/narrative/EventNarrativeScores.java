package nate.narrative;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Set;

import nate.BasicEventAnalyzer;
import nate.WordEvent;
import nate.WordIndex;
import nate.util.Util;


/**
 * An interface to reading Narrative Chain relations between events.
 * Easy lookup of event pairs, scores are loaded into memory from a file.
 *
 * Reads event/dep pair scores from the file:
 *   *aah
 *      ooh subj:subj 0.889
 *      elicit subj:subj 0.479
 *      draw subj:subj 0.305
 *
 */
public class EventNarrativeScores {
  HashMap<String,HashMap<String,Float>> scores = new HashMap();

  public EventNarrativeScores() {
  }
  public EventNarrativeScores(String filename) {
    fromFile(filename, null, 0.0f);
  }
  public EventNarrativeScores(String filename, WordIndex index) {
    fromFile(filename, index, 0.0f);
  }
  public EventNarrativeScores(String filename, WordIndex index, float cutoff) {
    fromFile(filename, index, cutoff);
  }

  /**
   * A convenience function that just merges the verbs with their 
   * typed dependencies so we can lookup the score.
   */
  public float getScore(String verb1, String verb2, String dep1, String dep2) {
    String key1 = verb1 + "-" + WordEvent.normalizeRelation(dep1);
    String key2 = verb2 + "-" + WordEvent.normalizeRelation(dep2);
    return getScore(key1,key2);
  }

  /**
   * Looks up a string verb and its dep compared against another verb/dep
   * @param key1 A verb-role string  e.g. "eat-o"
   * @param key2 A verb-role string  e.g. "eat-o"
   * @returns A similarity score
   */
  public float getScore(String key1, String key2) {
    /*
    // alphabetize the order	  
    if( key1.compareTo(key2) > 0 ) {
      String temp = key1;
      key1 = key2;
      key2 = temp;
    }
    */

    // lookup and return
    HashMap<String,Float> counts = scores.get(key1);
    if( counts != null ) {
      Float score = counts.get(key2);
      if( score != null ) return score;
    }

    // not found
    return 0;
  }

  public Set<String> getNeighbors(int verb, String dep) {
    String key = verb + "-" + WordEvent.normalizeRelation(dep);
    HashMap<String,Float> counts = scores.get(key);

    if( counts != null ) return counts.keySet();
    else return null;
  }

  /**
   * Read the scores from a file 
   * @param cutoff Ignore scores below the given cutoff
   */
  public void fromFile(String filename, WordIndex index, float cutoff) {
    scores.clear();
    String verb1 = ""; String line;
    HashMap<String,Float> counts;

    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
    
      while( (line = in.readLine()) != null ) {
	// *aahing
	if( line.charAt(0) == '*' ) {
	  verb1 = line.substring(1).trim(); // trim off leading asterisk
	  if( index != null ) verb1 = index.get(verb1).toString();
	}
	// pegging prep:obj 0.488
	else {
	  String parts[] = line.trim().split(" ");
	  String verb2 = parts[0];
	  if( index != null ) verb2 = index.get(verb2).toString();
	  float score = Float.valueOf(parts[2]);

	  // don't record scores below the cutoff
	  if( score >= cutoff ) {
	    int colon = parts[1].indexOf(':');
	    String dep1 = WordEvent.normalizeRelation(parts[1].substring(0,colon));
	    String dep2 = WordEvent.normalizeRelation(parts[1].substring(colon+1));
	    
	    String key1 = verb1 + "-" + dep1;
	    String key2 = verb2 + "-" + dep2;

	    // save the verb pair
	    if( scores.containsKey(key1) ) counts = scores.get(key1);
	    else {
	      counts = new HashMap();
	      scores.put(key1,counts);
	    }
	    counts.put(key2,score);
	  }
	}
      }
    } catch(Exception ex) { ex.printStackTrace(); }
  }

  public static void main(String[] args) {
    EventNarrativeScores scores = new EventNarrativeScores(args[0]);

    // testing...
    Util.reportMemory();
    System.out.println(scores.getScore("abandoning","opposes","obj","subj"));
    System.out.println(scores.getScore("opposes","abandoning","subj","obj"));
    System.out.println(scores.getScore("abandoning","opposes","o","s"));
  }
}
