package nate.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Misc functions for vector operations over maps of features and values.
 * Also some set-based union functions.
 */
public class Dimensional {

  /**
   * Standard dot product: Sum_i x_i * y_i
   */
  public static float dotProduct(final Map<String,Float> x, final Map<String,Float> y) {
    float dot = 0.0f;
    
    if( x.size() < y.size() ) {
      // Loop over x's features
      for( Map.Entry<String,Float> entry : x.entrySet() ) {
        Float yvalue = y.get(entry.getKey());
        if( yvalue != null )
          dot += entry.getValue() * yvalue;
      }
    } else {
      // Loop over y's features
      for( Map.Entry<String,Float> entry : y.entrySet() ) {
        Float xvalue = x.get(entry.getKey());
        if( xvalue != null )
          dot += entry.getValue() * xvalue;
      }
    }

    return dot;
  }
  
  /**
   * Standard dot product: Sum_i x_i * y_i
   * This method assumes the caller knows x has less entries than y, and calls this 
   * method as a small optimization to avoid an if statement that checks map size()
   */
  public static float dotProductSmallestFirst(final Map<String,Float> x, final Map<String,Float> y) {
    float dot = 0.0f;
    
    // Loop over x's features
    for( Map.Entry<String,Float> entry : x.entrySet() ) {
    	Float yvalue = y.get(entry.getKey());
    	if( yvalue != null )
    		dot += entry.getValue() * yvalue;
    }

    return dot;
  }

  /**
   * Standard dot product: Sum_i x_i * y_i

  public static float dotProduct(Map<String,Integer> x, Map<String,Integer> y) {
    float dot = 0.0f;

    if( x.size() < y.size() ) {
      // Loop over x's features
      for( Map.Entry<String,Integer> entry : x.entrySet() ) {
	Integer yvalue = y.get(entry.getKey());
	if( yvalue != null )
	  dot += entry.getValue().floatValue() * yvalue.floatValue();
      }
    } else {
      // Loop over y's features
      for( Map.Entry<String,Integer> entry : y.entrySet() ) {
	Integer xvalue = x.get(entry.getKey());
	if( xvalue != null )
	  dot += entry.getValue().floatValue() * xvalue.floatValue();
      }
    }

    return dot;
  }
   */

  /**
   * Turns a vector into a unit vector (normalize by ||x||)
   */
  public static void lengthNormalize(Map<String,Float> vec) {
    double norm = 0.0f;
    for( Map.Entry<String,Float> entry : vec.entrySet() )
      norm += entry.getValue() * entry.getValue();
    norm = Math.sqrt(norm);

    //    System.out.println("norm=" + norm);
    for( Map.Entry<String,Float> entry : vec.entrySet() )
      entry.setValue(entry.getValue() / (float)norm);
  }

  /**
   * Turns an array of vectors into unit vectors (feature values sum to one).
   */
  public static void lengthNormalize(Map<String,Float>[] data) {
    for( Map<String,Float> vec : data ) {
      lengthNormalize(vec);
    }
  }

  /**
   * Divide each entry's value by the given normalizer amount.
   * @param data The map to normalize.
   * @param normalizer The normalizing amount to divide by.
   */
  public static void normalize(Map<String,Float> data, float normalizer) {
    for( Map.Entry<String,Float> entry : data.entrySet() )
      entry.setValue(entry.getValue() / normalizer);
  }
  public static void normalizeDouble(Map<String,Double> data, double normalizer) {
    for( Map.Entry<String,Double> entry : data.entrySet() )
      entry.setValue(entry.getValue() / normalizer);
  }
  
  /**
   * Turn the counts into a probability distribution.
   */
  public static Map<String,Double> normalizeInt(Map<String,Integer> data) {
    if( data == null ) return null;
    
    Map<String,Double> dist = new HashMap<String,Double>();
    for( Map.Entry<String,Integer> entry : data.entrySet() )
      dist.put(entry.getKey(), entry.getValue().doubleValue());
    
    double sum = (double)sumValues(data);
    normalizeDouble(dist, sum);
    return dist;
  }

  /**
   * Get the keys that have a value greater than the given cutoff.
   */
  public static <E extends Number> List<String> topKeysByValue(Map<String,E> data, double cutoff) {
    if( data == null ) return null;
    
    List<String> topkeys = new ArrayList<String>();
    for( Map.Entry<String,E> entry : data.entrySet() ) {
      if( entry.getValue().doubleValue() >= cutoff )
        topkeys.add(entry.getKey());
    }

    return topkeys;
  }
  
  /**
   * @return The key with the highest value.
   */
  public static <E extends Number> String topKeyByValue(Map<String,E> data) {
    if( data == null ) return null;
    
    String topkey = null;
    E best = null;
    for( Map.Entry<String,E> entry : data.entrySet() ) {
      if( best == null || entry.getValue().doubleValue() >= best.doubleValue() ) {
        topkey = entry.getKey();
        best = entry.getValue();
      }
    }
    return topkey;
  }
  
  public static float cosine(Map<String,Float> x, Map<String,Float> y) {
    float dot = 0.0f;
    float xmag = 0.0f;
    float ymag = 0.0f;

    if( x == null || y == null ) return 0.0f;

    if( x.size() > y.size() ) { 
      Map<String,Float> temp = x;
      x = y;
      y = temp;
    }

    // loop over x's features
    for( Map.Entry<String,Float> entry : x.entrySet() ) {
      Float yvalue = y.get(entry.getKey());
      if( yvalue != null )
        dot += entry.getValue() * yvalue;
      xmag += entry.getValue() * entry.getValue();
    }

    // loop over y's features
    for( Map.Entry<String,Float> entry : y.entrySet() )
      ymag += entry.getValue() * entry.getValue();

    if( xmag != 0.0f && ymag != 0.0f ) {
      double denom = Math.sqrt(xmag) * Math.sqrt(ymag);
      return dot / (float)denom;
    } 
    else return 0.0f;
  }

  public static float cosineInteger(Map<String,Integer> x, Map<String,Integer> y) {
    float dot = 0.0f;
    float xmag = 0.0f;
    float ymag = 0.0f;

    if( x == null || y == null ) return 0.0f;

    if( x.size() > y.size() ) { 
      Map<String,Integer> temp = x;
      x = y;
      y = temp;
    }

    // loop over x's features
    for( Map.Entry<String,Integer> entry : x.entrySet() ) {
      Integer yvalue = y.get(entry.getKey());
      if( yvalue != null )
        dot += entry.getValue().floatValue() * yvalue.floatValue();
      xmag += entry.getValue().floatValue() * entry.getValue().floatValue();
    }

    // loop over y's features
    for( Map.Entry<String,Integer> entry : y.entrySet() )
      ymag += entry.getValue().floatValue() * entry.getValue().floatValue();

    if( xmag != 0.0f && ymag != 0.0f ) {
      double denom = Math.sqrt(xmag) * Math.sqrt(ymag);
      return dot / (float)denom;
    } 
    else return 0.0f;
  }

  public static double cosineDouble(Map<String,Double> x, Map<String,Double> y) {
    double dot = 0.0;
    double xmag = 0.0;
    double ymag = 0.0;

    if( x == null || y == null ) return 0.0;

    if( x.size() > y.size() ) { 
      Map<String,Double> temp = x;
      x = y;
      y = temp;
    }

    // loop over x's features
    for( Map.Entry<String,Double> entry : x.entrySet() ) {
      Double yvalue = y.get(entry.getKey());
      if( yvalue != null )
        dot += entry.getValue() * yvalue;
      xmag += entry.getValue() * entry.getValue();
    }

    // loop over y's features
    for( Map.Entry<String,Double> entry : y.entrySet() )
      ymag += entry.getValue() * entry.getValue();

    if( xmag != 0.0f && ymag != 0.0f ) {
      double denom = Math.sqrt(xmag) * Math.sqrt(ymag);
      return dot / denom;
    } 
    else return 0.0;
  }
  
  /**
   * @return A Map that is the union of all features in both given
   *         maps, and sums their values together.
   */
  public static Map<String,Float> sumFeatures(Map<String,Float> x, Map<String,Float> y) {
    Map<String,Float> sums = new HashMap(x.size());

    // loop over x's features
    for( Map.Entry<String,Float> entry : x.entrySet() ) {
      Float yvalue = y.get(entry.getKey());
      //      System.out.println("  yvalue = " + yvalue);
      if( yvalue != null ) {
        //	System.out.println("!!got yvalue from key=" + entry.getKey());
        sums.put(entry.getKey(), (yvalue + entry.getValue()));
      }
      else sums.put(entry.getKey(), entry.getValue());
    }

    // loop over y's features
    for( Map.Entry<String,Float> entry : y.entrySet() ) {
      //      System.out.println("  y key = " + entry.getKey());
      if( !sums.containsKey(entry.getKey()) )
        sums.put(entry.getKey(), entry.getValue());
      //      else System.out.println("  --> already contained");
    }

    return sums;
  }

  /**
   * @return The Integer values all summed up.
   */
  public static int sumValues(Map<String,Integer> map) {
    int sum = 0;
    if( map != null ) {
      for( Integer num : map.values() )
        sum += num;
    }
    return sum;
  }
  
  public static float sumValuesFloat(Map<String,Float> map) {
    float sum = 0;
    if( map != null ) {
      for( Float num : map.values() )
        sum += num;
    }
    return sum;
  }
  
  public static double sumValuesDouble(Map<String,Double> map) {
    double sum = 0;
    if( map != null ) {
      for( Double num : map.values() )
        sum += num;
    }
    return sum;
  }
  
  /**
   * Counts how many keys exist in the intersection of the two maps' key sets.
   */
  public static <E> int numKeyOverlap(Map<String,E> x, Map<String,E> y) {
    int overlaps = 0;

    if( x == null || y == null ) return 0;

    // loop over x's features
    for( String key : x.keySet() ) {
      if( y.containsKey(key) )
        overlaps++;
    }
    return overlaps;
  }
  
  /**
   * @return A shallow copy of the given map.  The keys/values of the new
   *         map contain the same objects.
   */
  public static Map<String,Float> cloneMap(Map<String,Float> themap) {
    Map<String,Float> newmap = new HashMap();
    for( Map.Entry<String,Float> entry : themap.entrySet() )
      newmap.put(entry.getKey(), entry.getValue());
    return newmap;
  }


  /**
   * Efficient intersection size calculation without requiring new memory
   * **Tested, it works...
   */
  public static int intersectionSize(Collection<String> set1, 
      Collection<String> set2) {
    int count = 0;
    if( set1 != null && set2 != null ) {
      // iterate over the smaller one
      if( set1.size() < set2.size() ) {
        for( String role : set1 )
          if( set2.contains(role) ) count++;
      } else {
        for( String role : set2 )
          if( set1.contains(role) ) count++;
      }
    }
    return count;
  }

  /**
   * @return The intersection of two collections.
   */
  public static Collection intersect(Collection set1, 
      Collection set2) {
    Set intersect = new HashSet();

    // Intersection empty if both are null.    
    if( set1 != null && set2 != null ) {
      Collection smaller = set2;
      Collection bigger  = set1;
      if( set1.size() < set2.size() ) {
        smaller = set1;
        bigger  = set2;
      }

      for( Object role : smaller )
        if( bigger.contains(role) ) 
          intersect.add(role);
    }

    return intersect;
  }

}
