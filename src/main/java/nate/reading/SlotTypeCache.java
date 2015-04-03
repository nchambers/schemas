package nate.reading;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.stats.Counter;

import nate.util.Util;
import nate.util.WordNet;

/**
 * A helper class that basically serves as a cache for quick lookup to deteremine
 * if a slot (v-run:s) tends to take a certain type of object (person, location, event...).
 * It also contains the functions to make these calculations, but stores the results
 * in the cache so it only has to do it once for each slot.
 * 
 * All type determinations use WordNet.
 */
public class SlotTypeCache {
  // Type -> Map (Slot -> true/false)
  Map<FrameRole.TYPE,Map<String,Boolean>> _typeSlotCache;
  // Type -> Map (Slot -> Map(counts))
  Map<FrameRole.TYPE,Map<String,Map<String,Integer>>> _typeArgCache;
  WordNet _wordnet;
  
  public SlotTypeCache(WordNet wordnet) {
    _wordnet = wordnet;
    _typeSlotCache = new HashMap<FrameRole.TYPE,Map<String,Boolean>>();
    _typeArgCache = new HashMap<FrameRole.TYPE,Map<String,Map<String,Integer>>>();
  }

  /**
   * Determine if the given argument vector for the given slot (v-run:s) matches the
   * desired type (e.g. person).  We cache the result so we only compute this once
   * and just look it up in the cache next time.
   * @param type The type we want (person, location, etc.)
   * @param slot The slot we want (v-run:s)
   * @param argCounts The vector of arguments seen in that slot.
   * @return True or false if the slot matches the type.
   */
  public boolean slotTypeMatches(FrameRole.TYPE type, String slot, Map<String,Integer> argCounts) {
    if( type == FrameRole.TYPE.ALL ) return true;
    
    // Lookup the cache for this type (e.g. PERSON).
    Map<String,Boolean> slotCache = _typeSlotCache.get(type);
    if( slotCache == null ) {
      slotCache = new HashMap<String,Boolean>();
      _typeSlotCache.put(type, slotCache);
    }
    
    // Lookup the cache for this slot.
    Boolean value = slotCache.get(slot);
    if( value != null ) {
//      System.out.println("cached slot! " + value);
      return value;
    }

    // Compute the type match for this argument vector and cache it.
    else {
//      System.out.println("non-cached slot " + slot);
      if( (type == FrameRole.TYPE.PERSON && isPerson(argCounts, _wordnet)) || 
          (type == FrameRole.TYPE.OTHER && isOther(argCounts, _wordnet)) ||
          (type == FrameRole.TYPE.EVENT && isEvent(argCounts, _wordnet)) || 
          (type == FrameRole.TYPE.PHYSOBJECT && isPhysObject(argCounts, _wordnet)) || 
          (type == FrameRole.TYPE.LOCATION && isLocation(argCounts, _wordnet)) ) {
        slotCache.put(slot, true);
        return true;
      }
      else {
        slotCache.put(slot, false);
        return false;
      }
    }
  }
  
  /**
   * Creates a cloned map that removed any arguments from the argCounts that do not
   * match the desired type (e.g. PERSON).
   * @param type The desired type
   * @param slot The slot that the arguments come from (v-run:s)
   * @param argCounts The argument counts.
   * @return A cloned map with arguments removed.
   */
  public Map<String,Integer> trimArgsByType(FrameRole.TYPE type, String slot, Map<String,Integer> argCounts) {
    if( type == FrameRole.TYPE.ALL || argCounts == null || argCounts.size() == 0 ) 
      return argCounts;

    // Lookup the cache for this type (e.g. PERSON).
    Map<String,Map<String,Integer>> slotCache = _typeArgCache.get(type);
    if( slotCache == null ) {
      slotCache = new HashMap<String,Map<String,Integer>>();
      _typeArgCache.put(type, slotCache);
    }
    
    // Lookup the cache for this slot.
    Map<String,Integer> newmap = slotCache.get(slot);
    if( newmap != null ) {
//      System.out.println("cached map! " + slot);
      return newmap;
    }
//    System.out.println("non-cached map! " + slot);
    
    newmap = new HashMap<String,Integer>();
    slotCache.put(slot, newmap);
    for( Map.Entry<String,Integer> entry : argCounts.entrySet() ) {
      String key = entry.getKey();
      if( (type == FrameRole.TYPE.PERSON && isPerson(key, _wordnet)) ||
          (type == FrameRole.TYPE.LOCATION && isLocation(key, _wordnet)) ||
          (type == FrameRole.TYPE.EVENT && isEvent(key, _wordnet)) ||
          (type == FrameRole.TYPE.PHYSOBJECT && isPhysObject(key, _wordnet)) ||
          (type == FrameRole.TYPE.OTHER && isOther(key, _wordnet)) )
        newmap.put(key, entry.getValue());
      //          else System.out.println("removing " + key);
    }
    
    return newmap;
  }
  
  /**
   * Given a counter of arguments (or any string), remove all keys from the counter that do not match
   * the given type as mostly determined by wordnet lookups.
   * @param type The type (person, location, event, etc)
   * @param argCounts The words and counts
   * @param wordnet Wordnet, of course.
   */
  public static void trimArgsByType(FrameRole.TYPE type, Counter<String> argCounts, WordNet wordnet) {
    if( type == FrameRole.TYPE.ALL || argCounts == null || argCounts.size() == 0 ) 
      return;
    
    Set<String> removal = new HashSet<String>();
    for( String key : argCounts.keySet() ) {
      if( (type == FrameRole.TYPE.PERSON && !isPerson(key, wordnet)) ||
          (type == FrameRole.TYPE.LOCATION && !isLocation(key, wordnet)) ||
          (type == FrameRole.TYPE.EVENT && !isEvent(key, wordnet)) ||
          (type == FrameRole.TYPE.PHYSOBJECT && !isPhysObject(key, wordnet)) ||
          (type == FrameRole.TYPE.OTHER && !isOther(key, wordnet)) )
        removal.add(key);
    }
    
    for( String remove : removal )
      argCounts.remove(remove);    
  }
  
  public static boolean isOther(String key, WordNet wordnet) {
    if( isPerson(key, wordnet) || isLocation(key, wordnet) )
      return false;
    else return true;
  }
  
  public static boolean isOther(Map<String,Integer> argCounts, WordNet wordnet) {
    // If there is an instance of this word as a person or location, just return false now.
    if( isPerson(argCounts, wordnet) || isLocation(argCounts, wordnet) ) 
      return false;
    
    return true;
  }
  
  /**
   * This defines a physical object as anything in wordnet that is physical or a material,
   * but NOT including people.
   */
  public static boolean isPhysObject(String key, WordNet wordnet) {
    // capital district city front town neighborhood
    // These are locations and phys-objects ... want to filter, but not just by saying !isLocation... 
    if( wordnet.isNonPersonLocationPhysicalObject(key) || wordnet.isMaterial(key) )
      return true;
    else return false;
  }
  
  public static boolean isPhysObject(Map<String,Integer> argCounts, WordNet wordnet) {
    if( argCounts != null && argCounts.size() > 0 ) {
      int totalCount = Util.sumValues(argCounts);
      int physCount = 0;
      
      for( Map.Entry<String, Integer> entry : argCounts.entrySet() ) {
        String key = entry.getKey();
        if( isPhysObject(key, wordnet) )
          physCount += entry.getValue();
      }

      float ratio = (float)physCount / (float)totalCount;
      //    System.out.println("isPerson ratio = " + ratio);
      if( ratio > 0.3f ) return true;
      else return false;
    } else return false;
  }

  public static boolean isTime(String key, WordNet wordnet) {
    if( wordnet.isTime(key) )
      return true;
    else return false;
  }
  
  public static boolean isEvent(String key, WordNet wordnet) {
    if( wordnet.isNounEvent(key) )
      return true;
    else return false;
  }
  
  public static boolean isEvent(Map<String,Integer> argCounts, WordNet wordnet) {
    if( argCounts != null && argCounts.size() > 0 ) {
      int totalCount = 0;
      int events = 0;
      
      for( Map.Entry<String, Integer> entry : argCounts.entrySet() ) {
        String key = entry.getKey();
        if( isEvent(key, wordnet) ) {
          events += entry.getValue();
          //        System.out.println("  " + key + " *wordnet ");
        }
        totalCount += entry.getValue();
      }

      float ratio = (float)events / (float)totalCount;

      if( ratio > 0.3f ) return true;
      else return false;
    } else return false;
  }
  
  public static boolean isPerson(String key, WordNet wordnet) {
    if( key.equalsIgnoreCase("PERSON") || key.equalsIgnoreCase("*properson*") || key.equalsIgnoreCase("ORGANIZATION") ||
        wordnet.isNounPersonOrGroup(key, true, false) )
      return true;
    else return false;
  }
  
  public static boolean isPerson(Map<String,Integer> argCounts, WordNet wordnet) {
    if( argCounts != null && argCounts.size() > 0 ) {
      int totalCount = Util.sumValues(argCounts);
      int persons = 0;
      
      for( Map.Entry<String, Integer> entry : argCounts.entrySet() ) {
        String key = entry.getKey();
        if( isPerson(key, wordnet) )
          persons += entry.getValue();
      }

      float ratio = (float)persons / (float)totalCount;
      //    System.out.println("isPerson ratio = " + ratio);
      if( ratio > 0.3f ) return true;
      else return false;
    } else return false;
  }
  
  public static boolean isLocation(String key, WordNet wordnet) {
    if( key.equalsIgnoreCase("LOCATION") || wordnet.isLocation(key) )
      return true;
    else return false;
  }
  
  public static boolean isLocation(Map<String,Integer> argCounts, WordNet wordnet) {
    if( argCounts != null && argCounts.size() > 0 ) {
      int totalCount = Util.sumValues(argCounts);
      int locations = 0;

      for( Map.Entry<String, Integer> entry : argCounts.entrySet() ) {
        String key = entry.getKey();
        if( key.equalsIgnoreCase("LOCATION") ) {
          locations += entry.getValue();
          //        System.out.println("  " + key + " *NE ");
        }
        else if( wordnet.isLocation(key) ) {
          locations += entry.getValue();
          //        System.out.println("  " + key + " *wordnet ");
        }
        //      else System.out.println("  " + key + " other ");
      }

      float ratio = (float)locations / (float)totalCount;
      //    System.out.println("isPerson ratio = " + ratio);
      if( ratio > 0.3f ) return true;
      else return false;
    } else return false;
  }
}
