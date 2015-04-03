package nate.reading;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * This is just a wrapper class over a Map.
 * It also splits the string keys for the template slots into single
 * entity descriptions.
 * @author Nate Chambers
 */
public class MUCTemplate implements Template {
  Map<String, String> _template = null;
  public static final String HUMAN_PERP = "PERP: INDIVIDUAL ID";
  public static final String ORG_PERP = "PERP: ORGANIZATION ID";
  public static final String HUMAN_TARGET = "HUM TGT: DESCRIPTION";
  public static final String PHYS_TARGET = "PHYS TGT: ID";
  public static final String INSTRUMENT = "INCIDENT: INSTRUMENT ID";

  public static final String[] PERP_ENTITY_TYPES = { HUMAN_PERP, ORG_PERP };
  // The "HUM TGT: NAME" key is always in the DESCRIPTION key.
  public static final String[] HUMTGT_ENTITY_TYPES = { HUMAN_TARGET };
  public static final String[] PHYSTGT_ENTITY_TYPES = { PHYS_TARGET };
  public static final String[] INSTRUMENT_TYPES = { INSTRUMENT };
  public static final String[] MAIN_ENTITY_TYPES = {
    HUMAN_PERP, ORG_PERP, HUMAN_TARGET, PHYS_TARGET };

  public static final String INCIDENT_TYPE = "INCIDENT: TYPE";
  public static final String MESSAGE_ID = "MESSAGE: ID";
  public static final String MESSAGE_TEMPLATE = "MESSAGE: TEMPLATE";
  
  public static final String[] MUC_TYPES = { "ATTACK", "BOMBING", "KIDNAPPING", "ARSON", "ROBBERY", "FORCED WORK STOPPAGE" };


  public MUCTemplate() {
    _template = new HashMap<String, String>();
  }

  /**
   * Takes the MUC string values for the main entity keys and converts
   * them into MUC entity objects for easy evaluation.
   *
   * MUC string formats: "X / Y / Z" -- usually 3 separate mentions, but
   *                                    Z sometimes a subphrase of Y...
   *                     "X : Y : Z" -- usually these are adjoining
   *                                    phrases, but also could just be
   *                                    separate mentions.
   * @return String values of the main entities in a template.
   */
  public List<MUCEntity> getMainEntities() {
    List<MUCEntity> entities = new LinkedList<MUCEntity>();

    entities.addAll(getPerpetrators());
    entities.addAll(getHumanTargets());
    entities.addAll(getPhysicalTargets());
    entities.addAll(getInstruments());
    
    return entities;
  }
  
  public static String shortSlotType(String type) {
    if( type.equals(HUMAN_PERP) || type.equals(ORG_PERP) ) return "PERP";
    else if( type.equals(HUMAN_TARGET) ) return "HTGT";
    else if( type.equals(PHYS_TARGET) ) return "PTGT";
    else if( type.equals(INSTRUMENT) ) return "INST";
    else return null;
  }
  
  /**
   * Some MUC templates are labeled as optional, so we check the string and see if it
   * says thusly.
   * @return True if the template is optional, false otherwise.
   */
  public boolean isOptional() {
    String msg = get(MESSAGE_TEMPLATE);
    if( msg != null && msg.contains("OPTIONAL") )
      return true;
    else
      return false;
  }
  
  public List<MUCEntity> getSlotEntities(int index) {
    List<MUCEntity> entities = null;
    
    if( index == 0 ) entities = getPerpetrators();
    else if( index == 1 ) entities = getHumanTargets();
    else if( index == 2 ) entities = getPhysicalTargets();
    else if( index == 3 ) entities = getInstruments();
    else {
      System.out.println("ERROR getSlotEntities(): index too large at " + index);
      System.exit(-1);
    }
    return entities;
  }
  
  private List<MUCEntity> getPerpetrators() {
    return getEntitiesByType(PERP_ENTITY_TYPES);
  }
  
  private List<MUCEntity> getHumanTargets() {
    return getEntitiesByType(HUMTGT_ENTITY_TYPES);
  }
  
  private List<MUCEntity> getPhysicalTargets() {
    return getEntitiesByType(PHYSTGT_ENTITY_TYPES);
  }
  
  private List<MUCEntity> getInstruments() {
    return getEntitiesByType(INSTRUMENT_TYPES);
  }
  
  public List<MUCEntity> getEntitiesByType(String[] types) {
    List<MUCEntity> entities = new LinkedList<MUCEntity>();
    for( String type : types ) {
      String value = get(type);
      List<MUCEntity> localentities = mucValueToEntities(value, type);

      if( localentities != null ) {
        for( MUCEntity entity : localentities )
          if( !entities.contains(entity) ) entities.add(entity);
      }
    }
    
    // Set the entities as optional if this template is optional.
    if( isOptional() )
      for( MUCEntity entity : entities )
        entity.setOptional(true);
    
    return entities;
  }

  
  /**
   * Given a (possibly multi-line) string value from a MUC template,
   * split it into its string values.  Sometimes there is just one,
   * but they can contain colons : or slashes / that separate multiple
   * values.
   */
  private List<MUCEntity> mucValueToEntities(String value, String type) {
//    System.out.println("  mucValueToEntities: " + value + " type=" + type);
    if( value != null ) {
      List<MUCEntity> entities = new LinkedList<MUCEntity>();
      String[] lines = value.split("\n");

      // Each line e.g. "JESUITS" / "PRIESTS"
      for( String line : lines ) {
//        	System.out.println("   line: " + line);
        MUCEntity entity = new MUCEntity(get(INCIDENT_TYPE));
        entity.setType(type);
        entities.add(entity);

        // Check if the entity is optional.
        if( line.matches("^\\s*\\?.+$") )
          entity.setOptional(true);
        
        // Try splitting on the colon.
        String[] parts = line.split("[:/]+");
        for( String part : parts ) entity.addMention(cleanValue(part));
      }
      return entities;
    }
    else return null;
  }

  /**
   * Strip off whitespace and quotations from the beginning/end.
   */
  private String cleanValue(String value) {
    // Remove question marks (annotators put them in)
    value = value.replace('?', ' ');
    // Remove leading/trailing whitespace
    value = value.trim();
    // Remove leading/trailing quotations.
    if( value.charAt(0) == '"' && value.charAt(value.length()-1) == '"' )
      value = value.substring(1, value.length()-1);

    // Change internal strings \" to just quotations "
    value = value.replaceAll("\\\\\"", "\"");

    return value;
  }

  /**
   * Append the given string to the end of the current string for
   * this key, adding a newline between them.
   */
  public void append(String key, String value) {
    String currValue = this.get(key);
    if( currValue != null )
      value = currValue + "\n" + value;
    put(key, value);
  }

  public void put(String key, String value) {
    _template.put(key, value);
  }

  public String get(String key) {
    return _template.get(key);
  }

  public int size() {
    return _template.size();
  }

  public void clear() {
    _template.clear();
  }
  
  public String toString() {
    StringBuffer buf = new StringBuffer();
    for( Map.Entry<String, String> entry : _template.entrySet() ) {
      buf.append(entry.getKey() + "\t" + entry.getValue() + "\t");
    }
    return buf.toString();
  }
  
  public String toPrettyString() {
    StringBuffer buf = new StringBuffer();
    Set<String> keys = _template.keySet();
    String[] sorted = new String[keys.size()];
    sorted = keys.toArray(sorted);
    Arrays.sort(sorted);
    
    for( String key : sorted ) {
      String entities = _template.get(key);
      String[] parts = entities.split("\n");
      buf.append("\t" + key + "\t");
      if( key.length() < 7 ) buf.append("\t");
      buf.append(parts[0]);
      for( int ii = 1; ii < parts.length; ii++ ) buf.append("\n\t\t\t" + parts[ii]);
      buf.append("\n");
    }
    return buf.toString();
  }
}
