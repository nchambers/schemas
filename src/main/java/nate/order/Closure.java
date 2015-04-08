package nate.order;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.Iterator;

import nate.order.tb.TLink;
import nate.order.tb.TimeTimeLink;
import nate.order.tb.EventTimeLink;
import nate.order.tb.EventEventLink;


/**
 * Class to compute closure over a set of temporal relations.
 * @author Nate Chambers
 * @author Shan Wang
 */
public class Closure {
  static boolean report = true;
  static String rulePath = "closure.dat";
  HashMap<String,Integer> rules[];
  HashMap<String,Boolean> flipped[];
  HashMap<Integer,Integer> inverseConstrain;

  public Closure() { 
    this(rulePath);
  }

  public Closure(String path) { 
    loadClosureRules(path);
  }

  // 0: A-B A-C
  // 1: A-B C-A
  // 2: B-A A-C
  // 3: B-A C-A
  private Integer closeLinks(int relation1, int relation2, int matchCase) {
    return rules[matchCase].get(relation1 + " " + relation2);
  }
  private Boolean closeLinksFlip(int relation1, int relation2, int matchCase) {
    return flipped[matchCase].get(relation1 + " " + relation2);
  }
  public Integer inverseConstraint(int relation) {
    return inverseConstrain.get(relation);
  }

  /**
   * Reads the closure rules from a data file
   */
  private void loadClosureRules(String path) {
    try {
      int matchCase = 0;
      BufferedReader in = new BufferedReader(new FileReader(path));

      inverseConstrain = new HashMap();
      rules = new HashMap[4];
      flipped = new HashMap[4];
      for( int i = 0; i < 4; i++ ) {
        rules[i] = new HashMap();
        flipped[i] = new HashMap();
      }

      while( in.ready() ) {
        String line = in.readLine();
        boolean flip;
        // defines start of new match case
        if( !line.matches(".*TLink.*") ) {
          if( line.matches(".*A-B A-C.*") ) matchCase = 0;
          else if( line.matches(".*A-B C-A.*") ) matchCase = 1;
          else if( line.matches(".*B-A A-C.*") ) matchCase = 2;
          else if( line.matches(".*B-A C-A.*") ) matchCase = 3;
          else if( line.matches(".*A-B B-A.*") ) matchCase = 4; // special constraints
        }
        // line comments
        else if( line.indexOf('/') > -1 ) { }
        // e.g. "TLink.SIMULTANEOUS TLink.ENDS TLink.ENDS"
        else if( matchCase < 4 && line.length() > 5 ) {
          String parts[] = line.split("\\s+");
          int first  = relationToInt(parts[0].substring(6));
          int second = relationToInt(parts[1].substring(6));
          int closed = relationToInt(parts[2].substring(6));

          if( parts.length == 4 ) flip = true;
          else flip = false;

          rules[matchCase].put(first + " " + second, new Integer(closed));
          flipped[matchCase].put(first + " " + second, new Boolean(flip));
        }
        // e.g. "TLink.SIMULTANEOUS TLink.SIMULTANEOUS"
        else if( matchCase == 4 && line.length() > 5 ) {
          String parts[] = line.split("\\s+");
          int first  = relationToInt(parts[0].substring(6));
          int second = relationToInt(parts[1].substring(6));
          inverseConstrain.put(first, second);
          System.out.println("Loaded inverse: " + first + " " + second);
        }
      }
    } catch( Exception ex ) { ex.printStackTrace(); }
  }

  /**
   * Returns a new Vector of relations that have been closed after adding
   * the given link if the link is consistent.  Otherwise, the function
   * returns null.  Only consistent closures are performed.
   * @param relations A Vector of TLinks
   * @param link The new TLink to add and compute closure over
   * @return A new Vector of TLinks if the given link is consistent, null otherwise
   */    
  public Vector safeClosure(Vector relations, TLink link) {
    Vector newset = new Vector(relations);
    newset.add(link);
    report = true; // turn closure reports off
    boolean conflicts = computeClosure(newset);
    report = true;

    if( conflicts ) return null;
    else return newset;
  }

  /**
   * Returns true if the given link is consistent with the rest of the
   * relations.  This function actually performs closure, so the test is
   * pretty expensive to run.
   * @return True if the link is consistent, false otherwise
   */    
  public boolean isConsistent(Vector relations, TLink link) {
    Vector newset = new Vector(relations);
    newset.add(link);
    boolean conflicted = computeClosure(newset);

    return conflicted;
  }

  /**
   * Compute a larger closed set of relations using transitivity rules of 
   * temporal reasoning.  Adds the new links directly to the relations Vector.
   * @param relations A Vector of TLinks
   * @return True if the closure is consistent, false if a conflict occurred.
   */
  public boolean computeClosure(List<TLink> relations, boolean prints) {
    boolean noneAdded = false;
    TLink tlink1 = null, tlink2 = null;
    TLink event2 = null;
    String eid, eid2, B, C;
    int rel1;
    int matchCase = 0;
    int size = 0, oldsize;
    int iter = 0;
    int start;
    boolean conflict = false, c = true;

    if( prints ) report = true;
    else report = false;

    if( report ) {
      System.out.print("Computing Closure...");
      System.out.println(relations.size() + " relations");
    }

    // Save what we've seen already
    HashMap<String,Integer> seen = new HashMap<String,Integer>();
    for( Iterator it = relations.iterator(); it.hasNext(); ) {
      TLink tlink = (TLink)it.next();
      seen.put(tlink.event1()+tlink.event2(), relationToInt(tlink.relation().toString()));
    }


    while (!noneAdded) {
      //System.out.println("iter = " + iter);
      oldsize = size;
      size = relations.size();
      for (int i = 0; i < size; i++) {
        if (i >= oldsize) start = i + 1;
        else start = oldsize;
        tlink1 = (TLink)relations.get(i);
        rel1 = relationToInt(tlink1.relation().toString());
        eid = tlink1.event1();
        eid2 = tlink1.event2();

        for (int j = start; j < size; j++) {
          tlink2 = (TLink)relations.get(j);
          int rel2 = relationToInt(tlink2.relation().toString());
          B = null;
          C = null;

          // Find which out of 4 transitive patterns to use
          if (eid.equals(tlink2.event1())) {
            //A-B-Rel, A-C-Rel
            if (!eid2.equals(tlink2.event2())) {
              matchCase = 0;
              B = eid2;
              C = tlink2.event2();
            }
          } else if (eid.equals(tlink2.event2())) {
            //A-B-Rel, C-A-Rel
            if (!eid2.equals(tlink2.event1())) {
              matchCase = 1;
              B = eid2;
              C = tlink2.event1();
            }
          } else if (eid2.equals(tlink2.event1())) {
            //B-A-Rel, A-C-Rel
            if (!eid.equals(tlink2.event2())) {
              matchCase = 2;
              B = eid;
              C = tlink2.event2();
            }
          } else if (eid2.equals(tlink2.event2())) {
            //B-A-Rel, C-A-Rel
            if (!eid.equals(tlink2.event1())) {
              matchCase = 3;
              B = eid;
              C = tlink2.event1();
            }
          }

          // Ignore closing trivial relations such as A-A-INCL, A-A-SIMUL
          if (rel1 == relationToInt(TLink.TYPE.SIMULTANEOUS.toString()) || rel1 == relationToInt(TLink.TYPE.INCLUDES.toString())) {
            if (eid.equals(eid2)) matchCase = -1;
          } else if (rel2 == relationToInt(TLink.TYPE.SIMULTANEOUS.toString()) || rel2 == relationToInt(TLink.TYPE.INCLUDES.toString())) {
            if (tlink2.event2().equals(tlink2.event1())) matchCase = -1;
          }

          if( B != null && C != null && matchCase != -1 ) {
            // Find the relation to close it	  
            Integer newrel = closeLinks(rel1, rel2, matchCase);

            if( newrel == null ) { }
            else if( !closeLinksFlip(rel1, rel2, matchCase) )
              c = addlink(seen, relations, B, C, newrel);
            else 
              c = addlink(seen, relations, C, B, newrel);

            // If this new link conflicts, remember that
            if( !c ) conflict = true;
          }
        }

        // add an inverse rule if you must  BEFORE -> AFTER 
        Integer newrel = inverseConstraint(rel1);
        if( newrel != null ) {
          c = addlink(seen, relations, eid2, eid, newrel);
          System.out.println("Adding inverse" + eid2 + " " + eid + "=" + newrel);
        }

      }
      noneAdded = (relations.size() == size);
      iter++;
    }

    return conflict;
  }

  public boolean computeClosure(List<TLink> relations) {
    return computeClosure(relations, true);
  }


  public Integer getClosed(int matchCase, int relation, int relation2) {
    //    System.out.println("closing..." + relation + " " + relation2);
    Integer rel = closeLinks(relation, relation2, matchCase);
    //    System.out.println("closed..." + rel);
    return rel;
  }

  public boolean getFlipped(int matchCase, int relation, int relation2) {
    return closeLinksFlip(relation, relation2, matchCase);
  }


  /**
   * Creates the appropriate type of TLink, based on the string from of A and B.
   * If it is e30 or ei12 then it is an event, whereas t14 is a time.
   * @param relations A vector of TLinks to which to add a new link
   * @param A The id of the first event/time
   * @param B The id of the second event/time
   * @param relation The type of relation between A and B
   * @return FALSE if the link CONFLICTS with an existing link, true otherwise
   */
  private static boolean addlink(HashMap seen, List<TLink> relations, String A, String B, int relation) {
    TLink.TYPE rel = TLink.TYPE.valueOf(intToStringRelation(relation));
    
    // Make sure we don't already have a relation
    if( seen.containsKey(A+B) ) {
      TLink.TYPE current = TLink.TYPE.valueOf(intToStringRelation((Integer)seen.get(A+B)));
      if( current != rel ) {

        // some relation clashes are ok
        if( (current == TLink.TYPE.BEFORE  && rel == TLink.TYPE.IBEFORE) ||
            (current == TLink.TYPE.IBEFORE && rel == TLink.TYPE.BEFORE) )
          return true;

        if( report ) {
          System.err.println("Closure conflict: " + A + " " + B);
          System.err.println("...old relation " + A + " " + seen.get(A+B) + " " + B + " adding new relation " + A + " " + relation + " " + B);
        }
        return false;
      }
    }
    // Make sure the inverse relation doesn't exist
    else if( seen.containsKey(B+A) ) {
      TLink.TYPE reverse = TLink.TYPE.valueOf(intToStringRelation((Integer)seen.get(B+A)));
      // inverse simultaneous relations are harmless, just ignore
      if( (rel == TLink.TYPE.SIMULTANEOUS && 
          reverse  == TLink.TYPE.SIMULTANEOUS) ||
          // INCLUDES and BEGINS/ENDS is ok
          (rel == TLink.TYPE.INCLUDES && 
              (reverse == TLink.TYPE.BEGINS || reverse == TLink.TYPE.ENDS)) ||
              // BEGINS/ENDS and INCLUDES is ok
              (reverse == TLink.TYPE.INCLUDES &&
                  (rel == TLink.TYPE.BEGINS || rel == TLink.TYPE.ENDS)) ||
                  // BEFORE and AFTER is ok
                  (reverse == TLink.TYPE.AFTER && rel == TLink.TYPE.BEFORE) ||
                  // AFTER and BEFORE is ok
                  (reverse == TLink.TYPE.BEFORE && rel == TLink.TYPE.AFTER) )
        return true;

      if( report ) {
        System.err.println("Closure conflict: " + A + " " + B);
        System.err.println("...old relation " + B + " " + seen.get(B+A) + " " + A + " adding new relation " + A + " " + relation + " " + B);
      }
      return false;
    }
    // Else, add the new relation
    else {
      TLink link;
      int times = 0, events = 0;

      // See what type of relation we are adding (e.g. event-time)
      // YES, this depends on making sure all time variables start with 't'
      if( A.charAt(0) == 't' ) times++;
      else events++;
      if( B.charAt(0) == 't' ) times++;
      else events++;

      // Create the appropriate TLink
      if( times == 2 ) link = new TimeTimeLink(A, B, rel, true);
      else if( times == 1 ) link = new EventTimeLink(A, B, rel, true);
      else link = new EventEventLink(A, B, rel, true);

      relations.add(link);
      seen.put(A+B,relation);

      //      System.out.println("Added link " + A + " " + relation + " " + B);
    }

    return true;
  }


  /**
   * Generates a complete set of NONE tlinks between all pairs of events that
   * are not already tlinks.  One pair A-B or B-A, not both A-B and B-A.
   * @param tlinks The current TLinks in the document.
   * @param events All of the events in the document.
   * @param eiidToID A mapping from the eiid's to id's in TimeBank. Use null if no mapping needed (TempEval).
   * @return Vector of NONE tlinks
   */
  public Vector<TLink> addNoneLinks(List<TLink> tlinks, List<TextEvent> events, Map<String,String> eiidToID) {
    Vector<TLink> newlinks = new Vector<TLink>();
    HashMap<String,HashSet<String>> map = new HashMap<String,HashSet<String>>();

    // Save the tlinks for quick access
    for( TLink link : tlinks ) {
      HashSet<String> set = map.get(link.event1());
      if( set == null ) set = new HashSet<String>();
      set.add(link.event2());
      map.put(link.event1(), set);
    }

    // Generate all pairs of NONE links
    for( TextEvent event1 : events ) {
      for( TextEvent event2 : events ) {
        if( !event1.equals(event2) ) {
          // only event pairs 1 sentence away
          if( Math.abs(event1.sid() - event2.sid()) < 2 ) {
            String id1 = event1.eiid();
            String id2 = event2.eiid();
            if( eiidToID != null && eiidToID.containsKey(id1) ) id1 = eiidToID.get(id1);
            if( eiidToID != null && eiidToID.containsKey(id2) ) id2 = eiidToID.get(id2);

            // if this pair is not yet linked
            if( !containsLink(map, id1, id2) ) {
              // randomly choose order
              String first = id1;
              String second = id2;
              if( Math.random() < 0.5 ) {
                first = second;
                second = event1.eiid();
              }

              // Add to the map
              HashSet<String> set = map.get(first);
              if( set == null ) set = new HashSet<String>();
              set.add(second);
              map.put(first, set);

              // Create TLink
              newlinks.add(new EventEventLink(first, second, "none"));
            }
          }
        }
      }
    }
    return newlinks;
  }


  /**
   * @return True if the relation event1-event2 or event2-event1 exists in the hashmap
   */
  public boolean containsLink(HashMap<String,HashSet<String>> map, String event1, String event2) {
    if( map.containsKey(event1) && map.get(event1).contains(event2) )
      return true;
    else if( map.containsKey(event2) && map.get(event2).contains(event1) )
      return true;
    else return false;
  }

  public static int relationToInt(String rel) {
    if( rel.equalsIgnoreCase("before") ) return 1;
    if( rel.equalsIgnoreCase("ibefore") ) return 2;
    if( rel.equalsIgnoreCase("includes") ) return 3;
    if( rel.equalsIgnoreCase("begins") ) return 4;
    if( rel.equalsIgnoreCase("ends") ) return 5;
    if( rel.equalsIgnoreCase("simultaneous") ) return 6;
    if( rel.equalsIgnoreCase("none") ) return 7;
    if( rel.equalsIgnoreCase("after") ) return 8;
    System.err.println("(TLink.java) Converting an unknown relation (" + rel + ")!");
    return 1;
  }

  public static String intToStringRelation(int rel) {
    if( rel == 1 ) return "BEFORE";
    if( rel == 2 ) return "IBEFORE";
    if( rel == 3 ) return "INCLUDES";
    if( rel == 4 ) return "BEGINS";
    if( rel == 5 ) return "ENDS";
    if( rel == 6 ) return "SIMULTANEOUS";
    if( rel == 8 ) return "AFTER";
    if( rel == 9 ) return "OVERLAP";
    if( rel == 10 ) return "BEFORE_OR_OVERLAP";
    if( rel == 11 ) return "OVERLAP_OR_AFTER";

    if( rel == 7 ) return "NONE";
    return null;
  }


  public void printRules() {
    for( int i = 0; i < rules.length; i++ ) {
      System.out.println("i=" + i);
      System.out.println(rules[i]);
    }
  }
}