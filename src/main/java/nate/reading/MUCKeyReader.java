package nate.reading;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;


/**
 * This class reads all of the MUC templates from a MUC file and
 * stores them indexed by their story IDs.  Functions return the
 * templates associated with any story.
 */
public class MUCKeyReader implements KeyReader {
  Map<String, List<Template>> _storyTemplates = null;
  List<String> _storyNames = null;

  public MUCKeyReader() {
  }

  public MUCKeyReader(String filename) {
    fromFile(filename);
  }

  public int numSlots() { return 4; }
  
  /**
   * @return A list of templates for the given story ID.
   */
  public List<Template> getTemplates(String storyName) {
    //    System.out.println("Retrieving story: " + storyName);
    if( _storyTemplates != null ) {
      //      System.out.println(" - looking up " + storyName);
      return _storyTemplates.get(storyName.toLowerCase());
    }
    else return null;
  }
  
  public Collection<String> getStories() {
    if( _storyNames == null )
      return null;
    else
      return _storyNames;
  }

  /**
   * Print out the number of entities in this reader.
   * @param reader
   */
  public void outputStats() {
  	Counter<String> counts = new ClassicCounter<String>();
  	Counter<String> optionals = new ClassicCounter<String>();
  	
  	// Count the number of MUC entities by their type.
  	for( String story : getStories() ) {
  		List<Template> templates = getTemplates(story);
  		if( templates != null ) {
  			for( Template template : templates ) {
  				List<MUCEntity> entities = template.getMainEntities();
  				if( entities != null ) {
  					for( MUCEntity entity : entities ) {
  						counts.incrementCount(entity.type());
  						if( entity.isOptional() )
  							optionals.incrementCount(entity.type());
  					}
  				}
  			}
  		}
  	}
  	
  	// Print them out.
  	System.out.println("KeyReader # Entities");
  	for( String key : counts.keySet() ) {
  		System.out.println("\t" + key + "\t" + counts.getCount(key) + "\t(including " + optionals.getCount(key) + " optionals)");
  	}
  }
  
  /**
   * Add a single template to the list of templates for the given
   * story ID.
   */
  public void addTemplate(String storyName, MUCTemplate template) {
    //    System.out.println("Adding " + storyName);
    if( _storyTemplates == null ) {
      _storyTemplates = new HashMap<String, List<Template>>();
      _storyNames = new ArrayList<String>();
    }

    // Get the templates already known for this story.
    List<Template> templates = _storyTemplates.get(storyName);
    if( templates == null ) {
      templates = new ArrayList<Template>();
      _storyTemplates.put(storyName, templates);
      _storyNames.add(storyName);
    }

    // Add the template.
    templates.add(template);
  }

  /**
   * Read the templates from MUC formatted key files.
   * e.g. key-dev-0101.muc4
   * Saves the templates in a global map under their story ID's.
   */
  public void fromFile(String filename) {
    System.out.println("MUCKeyReader fromFile: " + filename);
    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      String line;
      String storyName = null;
      MUCTemplate template = null;
      String previousKey = "";

      while( (line = in.readLine()) != null ) {
        //	System.out.println("line: " + line);

        // 0.  MESSAGE: ID                     DEV-MUC3-0001 (NCCOSC)
        if( line.matches("0\\..*MESSAGE: ID.*") ) {
          //	  System.out.println("MESSAGE MATCH!");
          String parts[] = line.split("\\s+");

          // Add the previously created template.
          if( storyName != null && template.size() > 0 ) {
//            System.out.println("Saving template for " + storyName);
            addTemplate(storyName.toLowerCase(), template);

//            System.out.println("Added template:");
//            for( MUCEntity entity : template.getMainEntities() )
//              System.out.println("  -> " + entity);
          }

          // Reset the story and the template for a new one.
          storyName = parts[3];
          //	  System.out.println("Story: " + storyName);
          template = new MUCTemplate();
        }

        // Numbered line in a template...
        // e.g. 19. HUM TGT: DESCRIPTION            "PEASANTS"
        else if( line.matches("\\d+\\.\\s+.+") ) {
          // Trim off the leading number.
          String trimmed = line.substring(4);
          // Split into two parts.
          String parts[] = trimmed.split("\\s\\s+");

          // Nate thinks the * and - both indicate "empty", but not
          // sure why they are both used...they must mean something.
          if( !parts[1].equals("*") && !parts[1].equals("-") ) {
            //	    System.out.println("Saving in template: " + parts[0] + " - " + parts[1]);
            template.put(parts[0], parts[1]);
            previousKey = parts[0];
          }
          // Didn't save this key, so set it to null.
          else previousKey = null;
        }

        // Subsequent line from the last mentioned line.
        // MUC keys can have multiple lines (multiple entities)
        // We append to the previous key.
        else if( previousKey != null && line.matches("\\s+[^\\s].*") ) {
          line = line.trim();
          //	  System.out.println("Appending " + previousKey + " value " + line);
          template.append(previousKey, line);
        }

        //	else System.out.println("Skipping " + line + " previousKey=" + previousKey);
      }
      
      // Cleanup the last one.
      if( storyName != null && template.size() > 0 ) {
        addTemplate(storyName.toLowerCase(), template);
      }
      System.out.println("MUCKeyReader read last story: " + storyName);
    } catch( Exception ex ) { 
      System.out.println("While trying to read file: " + filename);
      ex.printStackTrace(); 
      }
    
    // Print some basic statistics.
    outputStats();
  }

  /**
   * Outputs the total number of entities in each slot, given a MUC template reader.
   * We don't double count repeated occurrences of the same entity in the same document:
   * for instance, if there are multiple templates with the same perpetrator.
   */
  public void debugCountEntities(MUCKeyReader reader) {
    List<MUCEntity> humanperps = new ArrayList<MUCEntity>();
    List<MUCEntity> orgperps = new ArrayList<MUCEntity>();
    List<MUCEntity> instruments = new ArrayList<MUCEntity>();
    List<MUCEntity> victims = new ArrayList<MUCEntity>();
    List<MUCEntity> targets = new ArrayList<MUCEntity>();
    int humans = 0, orgs = 0, numinstruments = 0, numvictims = 0, numtargets = 0;
    
    for( String story : reader.getStories() ) {
      System.out.println(story);
      humanperps.clear();
      orgperps.clear();
      instruments.clear();
      victims.clear();
      targets.clear();
      for( Template template : reader.getTemplates(story) ) {
        if( template.isOptional() ) System.out.println("OPTIONAL! " + template.get(MUCTemplate.MESSAGE_ID));
        
        String[] types = { MUCTemplate.HUMAN_PERP };
        List<MUCEntity> entities = template.getEntitiesByType(types);
        for( MUCEntity entity : entities ) if( !humanperps.contains(entity) ) humanperps.add(entity);

        types[0] = MUCTemplate.ORG_PERP;
        entities = template.getEntitiesByType(types);
        for( MUCEntity entity : entities ) if( !orgperps.contains(entity) ) orgperps.add(entity);

        types[0] = MUCTemplate.INSTRUMENT;
        entities = template.getEntitiesByType(types);
        for( MUCEntity entity : entities ) if( !instruments.contains(entity) ) instruments.add(entity);

        types[0] = MUCTemplate.HUMAN_TARGET;
        entities = template.getEntitiesByType(types);
        for( MUCEntity entity : entities ) if( !victims.contains(entity) ) victims.add(entity);

        types[0] = MUCTemplate.PHYS_TARGET;
        entities = template.getEntitiesByType(types);
        for( MUCEntity entity : entities ) if( !targets.contains(entity) ) targets.add(entity);
      }
      humans += humanperps.size();
      orgs += orgperps.size();
      numinstruments += instruments.size();
      numvictims += victims.size();
      numtargets += targets.size();
    }

    System.out.println("Human Perps: " + humans);
    System.out.println("Organization Perps: " + orgs);
    System.out.println("Victims: " + numvictims);
    System.out.println("Targets: " + numtargets);
    System.out.println("Instruments: " + numinstruments);
  }
  
  
  // DEBUGGING ONLY
  public static void main(String[] args) {
    MUCKeyReader reader = new MUCKeyReader();
    reader.fromFile("/Users/mitts/Projects/corpora/MUC/key-tst3-4.muc4");
    reader.debugCountEntities(reader);
  }
}
