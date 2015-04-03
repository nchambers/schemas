package nate;

import java.io.BufferedReader;
import java.io.DataInput;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.ling.CoreAnnotations.CopyAnnotation;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;


/**
 * Reads a pseudo-xml file of entity/event information.
 */
public class GigaDocReader {
  BufferedReader in;
  List<WordEvent> events;
  List<EntityMention> entities;
  List<List<TypedDependency>> alldeps;
  List<NERSpan> allner;
  String mainpath;
  int currentDoc = 0, currentStoryNum;
  String currentStory = null;
  Map<String,GrammaticalRelation> stringToGramRels;

  public GigaDocReader() {
    init();
  }
  
  public GigaDocReader(String filename) {
    init();
    mainpath = filename;
    open(mainpath);
  }

  private void init() {
    events = new ArrayList<WordEvent>();
    entities = new ArrayList<EntityMention>();
    alldeps = new ArrayList<List<TypedDependency>>();
    allner = new ArrayList<NERSpan>();
    stringToGramRels = new HashMap<String, GrammaticalRelation>();
  }
  
  public void reset() {
    try {
      if( in != null ) in.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
    open(mainpath);
  }
  
  public void clear() {
    events = new ArrayList<WordEvent>();
    entities = new ArrayList<EntityMention>();
    alldeps = new ArrayList<List<TypedDependency>>();
    allner = new ArrayList<NERSpan>();
  }

  private void open(String filename) {
    try {
      if( filename.endsWith(".gz") )
        in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename))));
      else in = new BufferedReader(new FileReader(filename));
    } catch( Exception ex ) { 
      System.err.println("Error opening " + filename);
      ex.printStackTrace();
    }
  }

  /**
   * Read the events and entities from the next story
   */
  public boolean nextStory() {
    currentDoc++;
    currentStory = null;

    // Clear the entities and events for this story
    entities.clear();
    events.clear();
    alldeps.clear();
    allner.clear();

    if( in != null ) {
      try {
        String line;
        while ( (line = in.readLine()) != null ) {

          // find the doc line of the next "story"
          if ( !line.startsWith("<DOC ") || line.indexOf("type=\"story") < 0 ) continue;
          int namestart = line.indexOf('"') + 1;
          currentStory = line.substring(namestart, line.indexOf('"',namestart));

          // Get the story ID number
          int numstart = line.indexOf("num=\"") + 5;
          int numend   = line.indexOf('"',numstart);
          if( numstart == 4 ) {
            numstart = line.indexOf("num=") + 4;
            numend   = line.indexOf(' ',numstart);
          }
          if( numstart > 3 )
            currentStoryNum = Integer.parseInt(line.substring(numstart,numend));

          // Read in the story
          processStory(in);
          return true;
        }
      } catch( Exception ex ) { ex.printStackTrace(); }
    }

    return false;
  }


  /**
   * Read the events and entities from the next story
   */
  public boolean nextStory(String storyName) {
    currentDoc++;
    currentStory = null;

    // Clear the entities and events for this story
    entities.clear();
    events.clear();
    alldeps.clear();
    allner.clear();

    if( in != null ) {
      try {
        String line;
        while ( (line = in.readLine()) != null ) {

          // find the doc line of the story storyName
          if ( !line.startsWith("<DOC ") || line.indexOf(storyName) < 0 ) continue;
          currentStory = storyName;

          // Get the story ID number
          int numstart = line.indexOf("num=\"") + 5;
          int numend   = line.indexOf('"',numstart);
          if( numstart == 4 ) {
            numstart = line.indexOf("num=") + 4;
            numend   = line.indexOf(' ',numstart);
          }
          if( numstart > 3 )
            currentStoryNum = Integer.parseInt(line.substring(numstart,numend));

          // Read in the story
          processStory(in);
          return true;
        }
      } catch( Exception ex ) { ex.printStackTrace(); }
    }
    return false;
  }

  /**
   * Read and process everything between <TEXT>...</TEXT> tags
   */
  private void processStory(BufferedReader in) throws IOException {
    String line = in.readLine();

    // find the start of the text
    while ( !line.startsWith("<TEXT>") ) {
      if ((line = in.readLine()) == null) 
        return;
    }

    if ( line.startsWith("<TEXT>") ) {
      // Loop until the end of the TEXT is found
      while ((line = in.readLine()) != null) {
        //  System.out.println("line=" + line);
        if (line.startsWith("</TEXT>")) {
          return;
        }
        // entity
        else if ( line.startsWith("<ENT>") ) {
          entities.add(EntityMention.fromString(line.substring(5,line.length()-6)));
        } 
        // event
        else if ( line.startsWith("<EV>") ) {
          WordEvent ev = WordEvent.fromString(line.substring(4,line.length()-5));
          if( ev != null ) events.add(ev);
        } 
        // dependencies
        else if ( line.startsWith("<DEP") ) {
//          System.out.println("DEPS " + line);
          List<TypedDependency> deps = new ArrayList<TypedDependency>();
          while ((line = in.readLine()) != null) {
            if( line.startsWith("</DEP") ) break;
            else if( line.startsWith("<D>") ) {
              TypedDependency dep = stringToDep(line);
              if( dep != null ) deps.add(dep);
            }
          }
          alldeps.add(deps);
        }
        // NER
        else if ( line.startsWith("<N>") ) {
          int end = line.indexOf("</N>");
          NERSpan ner = NERSpan.fromString(line.substring(3,end));
//          System.out.println("NER! " + ner);
          allner.add(ner);
        }
      }
    }
  }
  
  /**
   * Read and process everything between <TEXT>...</TEXT> tags
   */
  public void processStoryRandomAccess(DataInput in) throws IOException {
    String line = in.readLine();

    // find the start of the text
    while ( !line.startsWith("<TEXT>") ) {
      if ((line = in.readLine()) == null) 
        return;
    }

    if ( line.startsWith("<TEXT>") ) {
      // Loop until the end of the TEXT is found
      while ((line = in.readLine()) != null) {
        //	System.out.println("line=" + line);
        if (line.startsWith("</TEXT>")) {
          return;
        }
        // entity
        else if ( line.startsWith("<ENT>") ) {
          entities.add(EntityMention.fromString(line.substring(5,line.length()-6)));
        } 
        // event
        else if ( line.startsWith("<EV>") ) {
          WordEvent ev = WordEvent.fromString(line.substring(4,line.length()-5));
          if( ev != null ) events.add(ev);
        } 
        // dependencies
        else if ( line.startsWith("<DEP") ) {
//          System.out.println("DEPS " + line);
          List<TypedDependency> deps = new ArrayList<TypedDependency>();
          while ((line = in.readLine()) != null) {
            if( line.startsWith("</DEP") ) break;
            else if( line.startsWith("<D>") ) {
              TypedDependency dep = stringToDep(line);
              if( dep != null ) deps.add(dep);
            }
          }
          alldeps.add(deps);
        }
        // NER
        else if ( line.startsWith("<N>") ) {
          int end = line.indexOf("</N>");
          NERSpan ner = NERSpan.fromString(line.substring(3,end));
//          System.out.println("NER! " + ner);
          allner.add(ner);
        }
      }
    }
  }


  /**
   * This is an unbelievably complex way of creating a dependency.
   * I believe it's the only way to do it with javanlp...
   * @param line String of dep: e.g. <D>nsubj testified-17 dealer-16</D>
   */
  public TypedDependency stringToDep(String line) {
    //    System.out.println("line: " + line);
    if( line.length() < 7 ) {
      System.out.println("WARNING: strange dep line: " + line);
      return null;
    }

    return stringToDepNoXML(line.substring(3,line.length()-4), stringToGramRels);
  }

  /**
   * This is an unbelievably complex way of creating a dependency.
   * I believe it's the only way to do it with javanlp...
   * @param line String of dep: e.g. "nsubj testified-17 dealer-16"
   */
  public static TypedDependency stringToDepNoXML(String line, Map<String,GrammaticalRelation> stringToGramRels) {
    //    System.out.println("line: " + line);
    if( line.length() < 5 ) {
      System.out.println("WARNING: strange dep line: " + line);
      return null;
    }

    String parts[] = line.split("\\s+");
    if( parts.length == 3 ) {
      //    System.out.println("parts: " + Arrays.toString(parts));

      GrammaticalRelation rel = stringToGramRels.get(parts[0]);

      // The relation "prep__" sometimes happens, and it throws an exception in CoreNLP.
      // Strip off the __ characters.
      if( rel == null && parts[0].endsWith("__") ) {
        System.out.println("Rel null with __ chars. parts[0]=*" + parts[0] + "*");
        while( parts[0].endsWith("_") ) parts[0] = parts[0].substring(0, parts[0].length()-1);
        rel = stringToGramRels.get(parts[0]);
        System.out.println("\tparts[0]=*" + parts[0] + "*");
        System.out.println("\trel=" + rel);
      }
      
      if( rel == null ) {
        //  rel = new GrammaticalRelation(parts[0],parts[0],null,null,StringUtils.EMPTY_STRING_ARRAY);
        //      rel = GrammaticalRelation.valueOf(parts[0]);
        //  rel = new GrammaticalRelation(Language.English,parts[0],parts[0],null,null);
        try {
          rel = GrammaticalRelation.valueOf(parts[0]);
        } catch( Exception ex ) {
          System.out.println("ERROR on str=*" + parts[0] + "*");
          System.out.println("MAP DUMP");
          for( String key : stringToGramRels.keySet() )
            System.out.println("\t" + key + ": " + stringToGramRels.get(key));
          ex.printStackTrace();
          System.exit(1);
        }
        stringToGramRels.put(parts[0], rel);
      }

      try {
        // "happy-12"
        int hyphen = parts[1].length()-2;
        while( hyphen > -1 && parts[1].charAt(hyphen) != '-' ) hyphen--;
        if( hyphen < 0 ) return null;
        TreeGraphNode gov = new TreeGraphNode(new Word(parts[1].substring(0,hyphen)));
        int end = parts[1].length();
        // "happy-12'"  -- can have many apostrophes, each indicates the nth copy of this relation
        int copies = 0;
        while( parts[1].charAt(end-1) == '\'' ) {
          copies++;
          end--;
        }
        if( copies > 0 ) gov.label().set(CopyAnnotation.class, copies);
        gov.label().setIndex(Integer.parseInt(parts[1].substring(hyphen+1,end)));

        // "sad-3"
        hyphen = parts[2].length()-2;
        while( hyphen > -1 && parts[2].charAt(hyphen) != '-' ) hyphen--;
        if( hyphen < 0 ) return null;
        TreeGraphNode dep = new TreeGraphNode(new Word(parts[2].substring(0,hyphen)));
        end = parts[2].length();
        // "sad-3'"  -- can have many apostrophes, each indicates the nth copy of this relation
        copies = 0;
        while( parts[2].charAt(end-1) == '\'' ) {
          copies++;
          end--;
        }
        if( copies > 0 ) dep.label().set(CopyAnnotation.class, copies);
        dep.label().setIndex(Integer.parseInt(parts[2].substring(hyphen+1,end)));

        return new TypedDependency(rel,gov,dep);
      } catch( Exception ex ) {
          System.out.println("Error on parts[1]=" + parts[1] + " and parts[2]=" + parts[2]);
        ex.printStackTrace();
      }
    }
    return null;
  }


  /**
   * This function is a convenience to calculate the total number of documents
   * contained in the file.
   * @return The number of documents listed in the given file
   */
  public int largestStoryID(String filename) {
    int total = 0, largestStoryID = 0;

    try {
      String line;
      while ( (line = in.readLine()) != null ) {
        // find the doc line of the next "story"
        if ( line.startsWith("<DOC ") && line.indexOf("type=\"story") >= 0 ) {
          total++;
          int storyID = GigawordHandler.parseStoryNum(line);
          System.out.println("parsed line for id " + storyID);
          if( storyID > largestStoryID ) largestStoryID = storyID;
        }
      }
      in.close();

      // restart the marker at the beginning
      if( filename.endsWith(".gz") )
        in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename))));
      else in = new BufferedReader(new FileReader(filename));

    } catch( Exception ex ) { ex.printStackTrace(); }

    return largestStoryID;
  }


  /**
   * Return the story name id from the last DOC in the file
   */
  public String lastStoryName(String filename) {
    try {
      String line;
      while ( (line = in.readLine()) != null ) {
        // find the doc line of the next "story"
        if ( line.startsWith("<DOC ") && line.indexOf("type=\"story") >= 0 ) {
          int namestart = line.indexOf('"') + 1;
          currentStory = line.substring(namestart, line.indexOf('"',namestart));
        }
      }
      in.close();

      // restart the marker at the beginning
      if( filename.endsWith(".gz") )
        in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename))));
      else in = new BufferedReader(new FileReader(filename));

    } catch( Exception ex ) { ex.printStackTrace(); }

    return currentStory;
  }

  /**
   * Close the opened stream
   */
  public void close() {
    if( in != null ) {
      try {
        in.close();
      } catch( Exception ex ) { ex.printStackTrace(); }
    }
  }

  public List<EntityMention> getEntities() {
    return entities;
  }

  public List<WordEvent> getEvents() {
    return events;
  }

  public List<List<TypedDependency>> getDependencies() {
    return alldeps;
  }

  public List<NERSpan> getNER() {
    return allner;
  }

  public String currentStory() { return currentStory; }
  public int currentStoryNum() { return currentStoryNum; }
}
