package nate.reading;

import java.util.HashMap;
import java.util.Map;


public class SlotVectorCache {
  Map<String,Map<String,Integer>> corefVecs = new HashMap<String,Map<String,Integer>>();
  Map<String,Map<String,Integer>> argVecs = new HashMap<String,Map<String,Integer>>();
  Map<String,Map<String,Integer>> argCorefVecs = new HashMap<String,Map<String,Integer>>();
  
  public SlotVectorCache() {
    // TODO Auto-generated constructor stub
  }

  public Map<String,Integer> getArgumentVector(String slot) {
    if( argVecs == null )
      argVecs = new HashMap<String,Map<String,Integer>>();
    return argVecs.get(slot);
  }
  
  public Map<String,Integer> getCorefVector(String slot) {
    if( corefVecs == null )
      corefVecs = new HashMap<String,Map<String,Integer>>();
    return corefVecs.get(slot);
  }

  public Map<String,Integer> getArgAndCorefVector(String slot) {
    if( argCorefVecs == null )
      argCorefVecs = new HashMap<String,Map<String,Integer>>();
    return argCorefVecs.get(slot);
  }
  
  public void setArgumentVector(String slot, Map<String,Integer> vec) {
    argVecs.put(slot, vec);
  }
  
  public void setCorefVector(String slot, Map<String,Integer> vec) {
    corefVecs.put(slot, vec);
  }
  
  public void setArgAndCorefVector(String slot, Map<String,Integer> vec) {
    argCorefVecs.put(slot, vec);
  }
}
