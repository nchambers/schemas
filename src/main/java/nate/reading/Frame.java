package nate.reading;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nate.util.SortableObject;
import nate.util.SortableScore;


/**
 * Represents a narrative schema, frame, situation, or whatever you want to call it.
 * @author mitts
 *
 */
public class Frame {
  Map<String,Double> _tokenScores = null;
  Map<String,Double> _tokenProbs = null;
  List<String> _centralTokens = null;
  List<String> _orderedByScore; // the tokens in the cluster, but ordered by score.
//  List<String> _tokens = null;
  List<FrameRole> _roles;
//  List<String> _arguments = null;
  int _id;
  double _clusterScore;
  String _type; // indicates what created this frame: clusters or topics?

//  // Entity IDs to their scores.
//  Map<Integer,Double> _entityScores = null;
//  // Entity IDs to their representative string mention.
//  Map<Integer,String> _entityStrings = null;
//  // EntityIDs to a single mention that matched with this frame
//  Map<Integer,EntityMention> _entityMentions = null;

  
  Map<Integer,EntityFiller> _idToEntity = null;
  // Entity IDs mapped to the frame role they fill.
  Map<Integer,Integer> _entityToRole = null;
  
  
  public Frame() {
  }
    
  public Frame(int id, double clusterScore) {
    _id = id;
    _clusterScore = clusterScore;
  }
  
  public Frame(int id, Map<String,Double> tokenScores, double clusterScore) {
    _id = id;
    _clusterScore = clusterScore;
    setTokenScores(tokenScores);
  }
  
  public Frame(double score, Frame clone) {
    _id = clone.getID();
    _clusterScore = score;
    _type = clone.type();
    setTokenScores(clone.getTokenScores());
    if( clone.getRoles() != null )
      for( FrameRole role : clone.getRoles() ) addRole(role);
//    _tokens = null;
//    _arguments = clone.arguments();
  }
  
  public void clear() {
    if( _tokenScores != null ) _tokenScores.clear();
//    if( _tokens != null ) _tokens.clear();
    if( _orderedByScore != null ) _orderedByScore.clear();
  }
  
  public void addToken(String token, double scoreWithFrame) {
    if( _tokenScores == null ) _tokenScores = new HashMap<String, Double>();
    _tokenScores.put(token, scoreWithFrame);
    setTheTokenOrder();
  }
  
  public void setCentralTokens(List<String> tokens) {
    _centralTokens = tokens;
  }

  public List<String> getCentralTokens() {
    return _centralTokens;
  }
  
  public void setClusterScore(double score) {
    _clusterScore = score;
  }
  
  public void setTokenScores(Collection<SortableObject<String>> scores) {
    if( _tokenScores == null ) _tokenScores = new HashMap<String, Double>();
//    if( _tokens == null ) _tokens = new ArrayList<String>();
    clear();
    for( SortableObject<String> obj : scores ) {
      String token = obj.key();
      double score = obj.score();
      _tokenScores.put(token, score);
//      _tokens.add(token);
    }
    setTheTokenOrder();
  }

  public void setTokenScores(Map<String,Double> scores) {
    if( _tokenScores == null ) _tokenScores = new HashMap<String, Double>();
    clear();
    for( Map.Entry<String, Double> entry : scores.entrySet() ) {
      String token = (String)entry.getKey();
      double score = entry.getValue();
      _tokenScores.put(token, score);
    }
    setTheTokenOrder();
  }
  
  public void setTokens(Collection<String> strs) {
    if( _tokenScores == null ) _tokenScores = new HashMap<String,Double>();
    clear();
    
    for( String str : strs )
      _tokenScores.put(str, 0.0);
    setTheTokenOrder();
  }
  
  /**
   * Uses the global token scores and sorts the key by their scores. Saves
   * the order in the token list _orderedByScore.
   */
  private void setTheTokenOrder() {
    if( _orderedByScore == null )
      _orderedByScore = new ArrayList<String>();
    else
      _orderedByScore.clear();

    SortableScore[] sorted = new SortableScore[_tokenScores.size()];
    int i = 0;
    for( String token : _tokenScores.keySet() )
      sorted[i++] = new SortableScore(_tokenScores.get(token), token);
    Arrays.sort(sorted);
    
    for( SortableScore scored : sorted )
      _orderedByScore.add(scored.key());
  }
  
  public void setTokenProb(String token, double prob) {
    // Save memory, don't save zero probabilities.
    if( prob > 0.0 ) {
      if( _tokenProbs == null ) _tokenProbs = new HashMap<String,Double>();
      _tokenProbs.put(token, prob);
    }
  }
  
  public double getTokenProb(String token) {
    if( _tokenProbs != null ) {
      Double prob = _tokenProbs.get(token); 
      if( prob != null ) return prob;
    }
    return 0.0;
  }
  
//  public void setArguments(Collection<String> args) {
//    if( _arguments != null )
//      _arguments.clear();
//    addArguments(args);
//  }
//  
//  public void addArguments(Collection<String> args) {
//    if( _arguments == null )
//      _arguments = new ArrayList<String>();
//    else
//      _arguments.clear();
//    for( String arg : args ) {
//      if( !_arguments.contains(arg) )
//        _arguments.add(arg);
//    }
//  }
  
  public void addRole(FrameRole role) {
//    System.out.println("adding role " + role);
    if( _roles == null ) _roles = new ArrayList<FrameRole>();
    _roles.add(role);
  }

  public List<FrameRole> getRoles() { return _roles; }

  /**
   * @return A list of all slots (e.g. v-arrest:s) that are in all of the frame roles.
   */
  public List<String> getRoleSlots() {
    if( _roles != null ) {
      List<String> allSlots = new ArrayList<String>();
      
      for( FrameRole role : _roles ) {
        Set<String> roleSlots = role.getSlots();
        for( String slot : roleSlots ) allSlots.add(slot);
      }
      return allSlots;
    }
    return null;
  }
  
  /**
   * Puts the slots in the second role into the first role, given the indices
   * of the two roles we are merging.
   * @param index1 Index to a role.
   * @param index2 Index to a role.
   */
  public void mergeRoles(int index1, int index2) {
    if( index1 > index2 ) {
      int temp = index1;
      index1 = index2;
      index2 = temp;
    }
    
    FrameRole role1 = _roles.get(index1);
    FrameRole role2 = _roles.get(index2);
    // Add all slots from 2 to 1.
    for( String newslot : role2.getSlots() )
      role1.addSlot(newslot);
    // Remove role 2.
    removeRole(role2);
  }

  /**
   * Removes the given role from our list, and updates all role IDs.
   * @param role The role to remove.
   */
  public void removeRole(FrameRole role) {
    if( _roles != null ) {
      int index = _roles.indexOf(role);
    
      // Remove role 2.
      _roles.remove(index);
      // Change all indices above 2.
      if( _entityToRole != null ) {
        for( Map.Entry<Integer, Integer> entry : _entityToRole.entrySet() ) {
          if( entry.getValue() > index )
            entry.setValue(entry.getValue()-1);
        }
      }
    }
  }
  
  public void clearRoles() {
    if( _roles != null ) _roles.clear();
    if( _entityToRole != null ) _entityToRole.clear();
    _roles = null;
    _entityToRole = null;
  }
  
  /**
   * Sort the entities in this frame by their scores, and returning the top n.
   * It returns the entity strings, not their IDs.
   * @param n The number of entities you want returned.
   * @return A sorted list of the top n entities.
   */
  public List<String> getEntitiesByScore(int n) {
    Integer[] topn = getEntityIDsByScore(n);
    
    if( topn != null ) {
      // Convert entity IDs to their string forms.
      List<String> topstrings = new ArrayList<String>();
      for( int i = 0; i < topn.length && i < n; i++ )
        topstrings.add(_idToEntity.get(topn[i]).string());
      return topstrings;
    }
    return null;
  }
  
  public EntityFiller getEntity(int id) {
    if( _idToEntity == null ) return null;
    else return _idToEntity.get(id);
  }
  
  public Set<Integer> getEntityIDs() {
    if( _entityToRole == null ) return null;
    else return _entityToRole.keySet();
  }

  /**
   * Sort the entities in this frame by their scores, and returning the top n.
   * @param n The number of entities you want returned.
   * @return A sorted array of the top n entity IDs.
   */
  public Integer[] getEntityIDsByScore(int n) {
    if( _idToEntity != null && _idToEntity.size() > 0 ) {
      SortableObject<Integer>[] scores = new SortableObject[_idToEntity.size()];
      int i = 0;
      for( Integer id : _idToEntity.keySet() )
        scores[i++] = new SortableObject<Integer>(_idToEntity.get(id).score(), id);
      Arrays.sort(scores);

      // Get the top n.
      Integer[] top = new Integer[Math.min(scores.length, n)];
      for( i = 0; i < top.length; i++ ) top[i] = (Integer)scores[i].key();
      return top;
    }
    return null;
  }

  /**
   * @return All entity IDs, sorted by score.
   */
  public Integer[] getEntityIDsByScore() {
    return getEntityIDsByScore(900000);
  }

  public List<String> getTokensOrderedByScore() {
    return _orderedByScore;
  }
  
  public void removeEntity(Integer id) {
    if( _entityToRole != null )  _entityToRole.remove(id);
    if( _idToEntity != null )  _idToEntity.remove(id);
//    if( _entityScores != null )  _entityScores.remove(id);
//    if( _entityStrings != null ) _entityStrings.remove(id);
  }
  
  public List<Integer> getEntityIDsOfRole(int roleid) {
    List<Integer> ids = new ArrayList<Integer>();
    if( _entityToRole != null ) {
      for( Map.Entry<Integer,Integer> entry : _entityToRole.entrySet() ) {
        if( entry.getValue() == roleid )
          ids.add(entry.getKey());
      }
    }
    return ids;
  }
  
  /**
   * Gets the role that an entity ID is in, and returns null if the entity is
   * mapped to the -1 role, or if it is not present at all.
   */
  public FrameRole getRoleOfEntity(int id) {
    if( _entityToRole != null ) { 
      Integer roleid = _entityToRole.get(id);
      if( roleid == null || roleid == -1 ) return null;
      else return _roles.get(roleid);
    }
    return null;
  }
  
  public int getNumRoles() {
    if( _roles == null ) return 0;
    else return _roles.size();
  }
  
  public void setEntityRole(EntityFiller filler, int roleid) {
    if( _entityToRole == null ) _entityToRole = new HashMap<Integer, Integer>();
    _entityToRole.put(filler.id(), roleid);

    if( _idToEntity == null ) _idToEntity = new HashMap<Integer,EntityFiller>();
    _idToEntity.put(filler.id(), filler);
  }
  
//  public void setEntityRole(int entityid, int roleid) {
//    if( _entityToRole == null ) _entityToRole = new HashMap<Integer, Integer>();
//    _entityToRole.put(entityid, roleid);
//  }

//  public void setEntityScore(int entityid, double score) {
//    if( _entityScores == null ) _entityScores = new HashMap<Integer, Double>();
//    _entityScores.put(entityid, score);
//  }
//
//  public void setEntityString(int entityid, String str) {
//    if( _entityStrings == null ) _entityStrings = new HashMap<Integer, String>();
//    _entityStrings.put(entityid, str);
//  }
//
//  public String getEntityString(int id) {
//    if( _entityStrings == null ) return null;
//    return _entityStrings.get(id);
//  }
//  
//  public void setEntityMention(int entityid, EntityMention mention) {
//    if( _entityMentions == null ) _entityMentions = new HashMap<Integer, EntityMention>();
//    _entityMentions.put(entityid, mention);
//  }
//
//  public EntityMention getEntityMention(int id) {
//    if( _entityMentions == null ) return null;
//    return _entityMentions.get(id);
//  }

  public Set<String> tokens() {
//      return _tokens;
    if( _tokenScores != null )
      return _tokenScores.keySet();
    else return null;
  }
  
//  public List<String> arguments() {
//    return _arguments;
//  }

  public int getID() {
    return _id;
  }
  
  public void setType(String type) {
    _type = type;
  }
  
  public String type() {
    return _type;
  }
  
  public boolean contains(String token) {
    return _tokenScores.containsKey(token);
  }
  
  public double tokenScore(String token) {
      Double score = _tokenScores.get(token);
      if( score != null ) return score;
      else return 0.0f;
  }
  
  public Map<String,Double> getTokenScores() {
    return _tokenScores;
  }
  
  public double clusterScore() { return _clusterScore; }
  
  public static Frame fromString(String str) {
    Frame frame = null;
    
    String[] parts = str.split("\t+\\s*");
//    for( String part : parts ) System.out.println("*part: " + part);
    
    // 134     802732.83697
    int id = Integer.parseInt(parts[0]);
    double score = Double.parseDouble(parts[1]);

    // n-stoppage v-stop n-arrest n-stop n-strike v-shine n-shining
//    String[] tokens = parts[2].split("\\s+");
//    Set<String> settokens = new HashSet<String>();
//    for( String token : tokens ) settokens.add(token);
//    frame.setTokens(settokens);
    
    // n-stoppage 8.1 v-stop 7.6 n-arrest 7.5 n-stop 7.0 n-strike 5.4 v-shine 3.1
    String[] subparts = parts[3].split("\\s+");
    Map<String,Double> tokenScores = new HashMap<String,Double>();
    for( int i = 0; i < subparts.length; i += 2)
      tokenScores.put(subparts[i], Double.valueOf(subparts[i+1]));
    frame = new Frame(id, tokenScores, score);    
    
    // [...],[...],[...]  or  "no roles!"
    if( parts.length > 4 && parts[4].startsWith("[") ) {
//      System.out.println("Parsing the roles...");
      String[] roles = parts[4].split("\\],\\[");

      // [n-effort:poss n-action:p_by n-party:p_of PERSON * arg1,.3432 arg2,.1253 arg3,.0582 ...]
      for( String strrole : roles ) {
//        System.out.println("Checking role str: " + strrole);
        // Strip off the leading [ and trailing ].
        if( strrole.charAt(0) == '[' ) strrole = strrole.substring(1);
        if( strrole.endsWith("]") ) strrole = strrole.substring(0, strrole.length()-1);
        String[] rolesplit = strrole.split(" : ");

        // e.g. n-effort:poss n-action:p_by n-party:p_of PERSON
//        System.out.println("Slots split: " + rolesplit[0]);
        String[] slots = rolesplit[0].split("\\s+");
        Set<String> setslots = new HashSet<String>();
        for( int i = 0; i < slots.length-1; i++ ) setslots.add(slots[i]);

        // e.g. arg1,.3432 arg2,.1253 arg3,.0582
        SortableScore[] argScores = null;
        if( rolesplit.length > 1 ) {
          //        System.out.println("Args split: " + rolesplit[1]);
          String[] strargscores = rolesplit[1].split("\\s+");
          argScores = new SortableScore[strargscores.length];
          for( int i = 0; i < strargscores.length; i++ ) {
            int comma = strargscores[i].lastIndexOf(',');
            argScores[i] = new SortableScore(Double.parseDouble(strargscores[i].substring(comma+1)), strargscores[i].substring(0, comma));
            //          System.out.println("  - added " + scores[i]);
          }
        }

//        System.out.println("New role with " + setslots.size() + " slots and " + scores.length + " arg scores.");
        FrameRole role = new FrameRole(FrameRole.TYPE.valueOf(slots[slots.length-1]), setslots, argScores);
        frame.addRole(role);
      }
    }
//    else System.out.println("No roles.");

//    System.out.println("debug read frame: " + frame.toString(9999, true));
    return frame;
  }

  public String entitiesToString() {
    StringBuffer sb = new StringBuffer();
    if( _entityToRole != null ) {
      sb.append("roles");
      for( Integer id : _entityToRole.keySet() ) {
        sb.append('\t');
        sb.append("role" + _entityToRole.get(id));
        sb.append(' ');
        sb.append(_idToEntity.get(id));
      }
    }
    return sb.toString();
  }
  
  public String toString(int maxTokens, boolean detailed) {
    StringBuffer sb = new StringBuffer();
    sb.append(_id + "\t" + String.format("%.4f",_clusterScore) + "\t");
    int i = 0;
    if( _orderedByScore != null ) {
      for( String token : _orderedByScore ) {
        sb.append(" " + token);
        if( i++ > maxTokens ) {
          sb.append(" ...");
          break;
        }
      }
    }
    sb.append("\t");

    // Print the tokens again, with their scores now.
    if( detailed && _orderedByScore != null ) {
      for( String token : _orderedByScore ) {
        sb.append(" " + token + " " + String.format("%.6f", _tokenScores.get(token)));
        if( i++ > maxTokens ) {
          sb.append(" ...");
          break;
        }
      }
    }
    sb.append("\t");

    if( _roles != null ) {
      sb.append("\t");
      i = 0;
      for( FrameRole role : _roles )
        sb.append(((i++ > 0) ? ",[" : "[") + role.toString(detailed) + "]");      
    } else sb.append("no roles!");
    if( _entityToRole != null ) {
      sb.append("\t");
      sb.append(entitiesToString());
    }
    return sb.toString();
  }

  public String toString() {
    return toString(25, false);
  }
}
