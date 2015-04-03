package nate.reading;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import nate.util.SortableScore;
import nate.util.Util;


public class FrameRole {
  public static enum TYPE { ALL, PERSON, LOCATION, EVENT, OTHER, PHYSOBJECT };
  
  Set<String> _slots;
  Map<String,Double> _args;
  TYPE _type = TYPE.PERSON;
  
  
  public FrameRole(TYPE type, Set<String> slots, SortableScore[] fillerScores) {
    _slots = slots;
    setArgScores(fillerScores);
    setType(type);
  }
  
  public void setType(TYPE type) { _type = type; }
  
  public Set<String> getSlots() { return _slots; }
  
  public void addSlot(String slot) {
    if( _slots == null ) _slots = new HashSet<String>();
    _slots.add(slot);
  }
    
  public void setArgScores(SortableScore[] argScores) {
    if( argScores != null ) {
      if( _args == null ) _args = new HashMap<String,Double>();
      for( SortableScore score : argScores ) {
        _args.put(score.key(), score.score());
      }
    }
  }
  
  public Map<String,Double> getArgs() { return _args; }
  
  public String toString(boolean detailed) {
    StringBuffer buf = new StringBuffer();
    if( _slots != null ) {
      for( String slot : _slots ) {
        buf.append(slot);
        buf.append(' ');
      }
      buf.append(_type);
      
      // Append argument scores if detailed info is desired.
      if( detailed ) {
        buf.append(" :");
        for( String key : Util.sortKeysByValues(_args) )
          buf.append(" " + key + "," + String.format("%.6f",_args.get(key)));
      }
    }
    return buf.toString();
  }
  
  public String toString() {
    return toString(false);
  }
}
