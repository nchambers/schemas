package nate.narrative;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Vector;


/**
 * Basic Narrative Chains:
 * This class takes pairwise scores (pmi or t-test) between events and
 * is able to build discrete sets of narratives centered around single
 * events.  The sets can be large, or trimmed to a certain size, keeping
 * only the most inter-related events.
 */
public class BuildNarrative {
  EventPairScores _pairs; // the pmi or t-test scores between pairs
  float _factor = 0.25f; // threshold factor
  float _scoreCutoff = 0.25f; // group member cutoff


  public BuildNarrative(EventPairScores pairScores) {
    _pairs = pairScores;
  }


  /**
   * Calculates a score for an event neighbor against the entire narrative group
   */
  private float groupScore(String main, String neighbor, HashSet<String> group) {

    // best word-main score
    float score = _pairs.getScore(neighbor, main);

    // loop over other GROUP members
    for( String g : group ) {
      float gscore = _pairs.getScore(neighbor, g);

      if( gscore > _scoreCutoff ) {
        // best main-neighbor score
        float matchmain = _pairs.getScore(main, g);
        score += (gscore + matchmain) / (float)2; // average
      }
    }

    return score;
  }



  /**
   * @desc Creates a discrete clustered narrative centered around
   *       the given event.  All events added to the narrative must have
   *       been seen at least once with the given event.
   * @param event The core event to build the narrative around  "convict-o"
   * @return A hashmap of narrative events and their individual scores
   *         summed over pairwise scores over all events in the narrative.
   *         Does not include the main event.
   */
  public Vector<NarrativeEvent> createNarrative(String event) {
    HashSet<String> group = new HashSet();

    System.out.println("Getting neighbors of " + event + " (BuildNarrative)");
    // Make a list of neighbors
    Map<String,Float> neighborScores = _pairs.getNeighbors(event);
    // Return NOW if there are no neighbors
    if( neighborScores == null ) return null;
    Set<String> neighbors = neighborScores.keySet();

    // DEBUG
    /*
    String[] arr = new String[neighbors.size()];
    arr = neighbors.toArray(arr);
    Arrays.sort(arr);
    System.out.print("Sorted neighbors: ");
    for( int i = 0; i < arr.length; i++ ) System.out.print(" " + arr[i]);
    System.out.println();
     */

    // array to hold the ith event's current score with the group
    float groupScores[] = new float[neighbors.size()];

    System.out.println("Finding best first neighbor...");
    // Find the best neighbor  O(N)
    float bestscore = 0.0f;   float bestmainscore = 0.0f;   String bestn = null;
    int i = 0;
    for( String neigh : neighbors ) {
      float score = neighborScores.get(neigh);
      groupScores[i++] = score;
      if( score > bestscore ) {
        bestscore = score;
        bestn = neigh;
        bestmainscore = score;
      }
    }

    System.out.println("best = " + bestn + " at " + bestscore);
    System.out.println("Finding all members now from " + neighbors.size() + " neighbors...");

    // add all neighbors  - O(N^2)  - N number of neighbors
    boolean changed = true;
    while( changed && bestn != null ) {

      // Add the best
      group.add(bestn);
      changed = false;
      String lastadded = bestn; float lastmainScore = bestmainscore;
      bestn = null;      bestscore = 0.0f;

      // threshold based on size of neighbors words
      float threshold = (group.size()+1) * _factor;

      // update all event scores with the group  - O(N)
      i = 0;
      for( String neigh : neighbors ) {
        float neighLastScore = _pairs.getScore(neigh, lastadded);
        //	System.out.println("neighbor " + neigh + " groupscore = " + neighLastScore);

        groupScores[i] += (neighLastScore + lastmainScore) / 2.0f;

        // if better score
        if( !group.contains(neigh) && groupScores[i] > bestscore ) {
          bestscore = groupScores[i];
          bestn = neigh;
          bestmainscore = neighborScores.get(neigh);
        }
        i++;
      }

      // score high enough?
      if( bestscore > threshold ) {
        group.add(bestn); 
        changed = true;
      }
    }

    // Remove entries that have lower group scores now
    Vector<NarrativeEvent> trimScores = new Vector();
    float threshold = group.size() * _factor; 
    int j = 0;
    for( String neigh : neighbors ) {   
      // only save the ones above the threshold
      if( groupScores[j] >= threshold )
        trimScores.add(new NarrativeEvent(neigh, groupScores[j]));
      //	trimScores.add(new NarrativeEvent(neigh, groupScores[j]/group.size()));
      //	trimScores.add(new NarrativeEvent(neigh, neighborScores.get(neigh)));
      j++;
    }

    return trimScores;
  }



  /**
   * Creates a discrete narrative trimmed to a maximum size
   * @param event The string of the event "convict-o"
   * @param maxsize The maximum size of the narrative to return
   */
  public Vector<NarrativeEvent> createNarrative(String event, int maxsize) {
    Vector<NarrativeEvent> trimmed = new Vector(maxsize);

    // Get the biggest possible narrative, sort 
    Vector<NarrativeEvent> thegroup = createNarrative(event);
    if( thegroup != null ) {
      thegroup.trimToSize();
      NarrativeEvent arr[] = new NarrativeEvent[thegroup.size()];
      thegroup.toArray(arr);    
      Arrays.sort(arr);

      // Trim off the top maxsize elements
      for( int i = 0; i < arr.length && i < maxsize; i++ ) {
        trimmed.add(arr[i]);
      }
    }

    return trimmed;
  }


  /**
   * Creates a narrative around the event, but can include any events, even if
   * they weren't seen with the given event (but seen with others).
   * @param maxsize Trims the narrative to the given size, the number of events.
   */
  public Vector<NarrativeEvent> createLooserNarrative(String event, int maxsize) {
    Vector<NarrativeEvent> trimmed = new Vector(maxsize);

    // Get the biggest possible narrative, sort 
    Vector<NarrativeEvent> thegroup = createLooserNarrative(event);
    if( thegroup != null ) {
      thegroup.trimToSize();
      NarrativeEvent arr[] = new NarrativeEvent[thegroup.size()];
      thegroup.toArray(arr);    
      Arrays.sort(arr);

      // Trim off the top maxsize elements
      for( int i = 0; i < arr.length && i < maxsize; i++ ) {
        trimmed.add(arr[i]);
      }
    }

    return trimmed;
  }


  /**
   * Creates a narrative around the event, but can include any events, even if
   * they weren't seen with the given event (but seen with others).
   */
  public Vector<NarrativeEvent> createLooserNarrative(String event) {
    Set<String> group = new HashSet();

    System.out.println("Getting neighbors of " + event + " (BuildNarrative)");
    // Make a list of neighbors
    Map<String,Float> mainPairScores = _pairs.getNeighbors(event);
    // Return NOW if there are no neighbors
    if( mainPairScores == null ) return null;
    Map<String,Float> neighborScores = new HashMap(mainPairScores.size());

    System.out.println("Finding best first neighbor...");
    // Find the best neighbor  O(N)
    float bestscore = 0.0f;   float bestmainscore = 0.0f;   String bestn = null;
    int i = 0;
    for( Map.Entry<String,Float> pair : mainPairScores.entrySet() ) {
      System.out.println("Initial " + pair.getKey() + " = " + pair.getValue());

      float score = pair.getValue();
      if( score > bestscore ) {
        bestscore = score;
        bestn = pair.getKey();
        bestmainscore = score;
      }
      neighborScores.put(pair.getKey(), score);
    }

    System.out.println("best = " + bestn + " at " + bestscore);
    System.out.println("Finding all members now from " + neighborScores.size() + " neighbors...");

    // Add all neighbors  - O(N^2)  - N number of neighbors
    boolean changed = true;
    while( changed && bestn != null ) {

      // Add the best
      //      System.out.println("  ...added " + bestn);
      group.add(bestn);
      changed = false;
      String lastadded = bestn; float lastmainScore = bestmainscore;
      bestn = null;      bestscore = 0.0f;

      // threshold based on size of neighbors words
      float threshold = (group.size()+1) * _factor;

      // Update all event scores with the last added event.  - O(N)
      for( Map.Entry<String,Float> entry : neighborScores.entrySet() ) {
        String neigh = entry.getKey();

        // Don't update already added group members.  All neighbors have updated
        // their scores with those members already.
        if( !group.contains(neigh) ) {
          float neighLastScore = _pairs.getScore(neigh, lastadded);
          // This old score is bad because even words that have zero scores get half
          // of the best event's score ... fake boosting.  Everyone gets boosted by
          // the same amount essentially, even if they have zero connections.
          //	  float updatedScore = entry.getValue() + ((neighLastScore + lastmainScore) / 2.0f);
          float updatedScore = entry.getValue() + ((neighLastScore + (lastmainScore/10.0f)) / 2.0f);
          entry.setValue(updatedScore);
          //	  System.out.println("Update neighbor " + neigh + " score = " + updatedScore);

          // if better score
          if( !group.contains(neigh) && updatedScore > bestscore ) {
            bestscore = updatedScore;
            bestn = neigh;
            Float score = mainPairScores.get(neigh);
            if( score == null ) bestmainscore = 0.0f;
            else {
              //	    System.out.println("Good float = " + score);
              bestmainscore = score.floatValue();
            }
          }
        }
      }

      // score high enough?
      if( bestscore > threshold ) {
        group.add(bestn); 
        changed = true;

        // Make a list of new neighbors.
        Map<String,Float> pairScores = _pairs.getNeighbors(bestn);
        // Add the new neighbors to our neighbor list.
        for( Map.Entry<String,Float> entry : pairScores.entrySet() )
          if( !entry.getKey().equals(event) && !neighborScores.containsKey(entry.getKey()) )
            neighborScores.put(entry.getKey(), 0.0f);
      }
    }

    // Remove entries that have lower group scores now
    Vector<NarrativeEvent> trimScores = new Vector();
    float threshold = group.size() * _factor; 
    int j = 0;
    for( Map.Entry<String,Float> entry : neighborScores.entrySet() ) {
      Float score = entry.getValue();
      // only save the ones above the threshold
      if( score >= threshold )
        trimScores.add(new NarrativeEvent(entry.getKey(), score));
      j++;
    }

    return trimScores;
  }


  /**
   * @return A set of all single events in any event pair.
   */
  public Set<String> getSingleEvents() { return _pairs.getSingleEvents(); }



  public static void main(String[] args) {
    EventPairScores scores = new EventPairScores();

    System.out.println("Loading file...");

    scores.fromFile(args[0], null, 0.0f, 0, false);

    System.out.println("Finished loading file...");

    // test narrative
    BuildNarrative builder = new BuildNarrative(scores);
    Vector<NarrativeEvent> nar = builder.createNarrative("arrested-o");

    nar.trimToSize();
    NarrativeEvent arr[] = new NarrativeEvent[nar.size()];
    //    int i = 0;
    //    for( NarrativeEvent ev : nar ) arr[i++] = ev;
    nar.toArray(arr);    

    Arrays.sort(arr);

    for( int i = 0; i < arr.length; i++ ) {
      System.out.println(arr[i]);
    }

    System.out.println("All done!");



    // single test    
    Vector<NarrativeEvent> n = builder.createNarrative("arrested-o", 12);
    for( NarrativeEvent e : n ) System.out.println(e);
  }
}