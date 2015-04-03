package nate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Collection;
import java.util.Set;

import edu.stanford.nlp.trees.TypedDependency;


/**
 * Creates (Writes) a pseudo-xml file of parses.
 */
public class GigaDoc {
  public static String SENTENCE_ELEM = "S";
  public static String ENTITY_ELEM = "ENT";
  public static String EVENT_ELEM = "EV";
  public static String DEP_ELEM = "D";
  public static String NER_ELEM = "N";
  //  BufferedWriter writer;
  PrintWriter writer;
  boolean storyOpen = false;

  public GigaDoc(String docname) throws Exception {
    this(docname, false);
  }

  public GigaDoc(String docname, boolean append) throws Exception {

    File file = new File(docname);
    if( !append && file.exists() ) {
      System.out.println("file " + docname + " already exists");
      throw new Exception();
      //	System.exit(1);
    }

    try {
      writer = new PrintWriter(new BufferedWriter(new FileWriter(docname, append)));
    } catch( Exception ex ) { ex.printStackTrace(); }
  }

  public static boolean fileExists(String filename) {
    File file = new File(filename);
    if( file.exists() ) return true;
    else return false;
  }

  public void addParse(String parse) {
    if( writer != null && storyOpen ) {
      try {
        writer.print("<" + SENTENCE_ELEM + ">\n");
        writer.print(parse.trim());
        writer.print("\n</" + SENTENCE_ELEM + ">\n");
      } catch( Exception ex ) { ex.printStackTrace(); }
    } else {
      System.out.println("WARNING: adding parse with no open story");
    }
  }

  public void addEntity(EntityMention mention) {
    if( writer != null && storyOpen ) {
      try {
        writer.print("<" + ENTITY_ELEM + ">");
        //	writer.print(mention.sentenceID() + " " + mention.entityID() + " " +
        //		     mention.start() + " " + mention.end());
        writer.print(mention.toString());
        writer.print("</" + ENTITY_ELEM + ">\n");
      } catch( Exception ex ) { ex.printStackTrace(); }
    } else {
      System.out.println("WARNING: adding entity with no open story");
    }
  }

  public void addEvent(WordEvent event) {
    if( writer != null && storyOpen ) {
      try {
        writer.print("<" + EVENT_ELEM + ">");
        writer.print(event.toString());
        writer.print("</" + EVENT_ELEM + ">\n");
      } catch( Exception ex ) { ex.printStackTrace(); }
    } else {
      System.out.println("WARNING: adding event with no open story");
    }
  }

  public void addDependencies(Collection<TypedDependency> deps, int sid) {
    if( writer != null && storyOpen ) {
      StringBuffer buf = new StringBuffer();
      buf.append("<DEPS sid=" + sid + ">\n");
      if( deps != null ) {
        // There can be duplicate relations with these new "prime" relations that are added
        // for implicit relations and fillers.
        List<String> inorder = new ArrayList<String>();
        Set<String> added = new HashSet<String>();
        
        try {
          for( TypedDependency dep : deps ) {
            String govstr = dep.gov().toString("value-index");
            String depstr = dep.dep().toString("value-index");
            // The JavaNLP code is now not just returning indices, they put "prime" marks on the integers
            // if the dep was generated as a copy (not explicit in the syntax). We throw these prime markers away.
            if( govstr.charAt(govstr.length()-1) == '\'' ) govstr = govstr.substring(0, govstr.length()-1);
            if( depstr.charAt(depstr.length()-1) == '\'' ) depstr = depstr.substring(0, depstr.length()-1);
            String str = dep.reln() + " " + govstr + " " + depstr;
            if( !added.contains(str) ) {
              added.add(str);
              inorder.add(str);
            }
          }
          
          for( String str : inorder ) {
            buf.append("<" + DEP_ELEM + ">");
            buf.append(str);
            buf.append("</" + DEP_ELEM + ">\n");
          }
        } catch( Exception ex ) { ex.printStackTrace(); }
      }
      buf.append("</DEPS>\n");
      writer.write(buf.toString());
    } else {
      System.out.println("WARNING: adding graph with no open story");
    }
  }

  public void addNER(NERSpan ner) {
    if( writer != null && storyOpen ) {
      try {
        writer.print("<" + NER_ELEM + ">");
        writer.print(ner.toString());
        writer.print("</" + NER_ELEM + ">\n");
      } catch( Exception ex ) { ex.printStackTrace(); }
    } else {
      System.out.println("WARNING: adding event with no open story");
    }
  }

  public void openStory(String name, int idnum) {
    if( writer != null ) {
      try {
        writer.print("<DOC id=\"" + name + "\" num=\"" + idnum +
        "\" type=\"story\" >");
        writer.print("\n<TEXT>\n");
      } catch( Exception ex ) { ex.printStackTrace(); }

      storyOpen = true;
    }
  }

  public void closeStory() {
    if( writer != null ) {
      try {
        writer.print("</TEXT>\n</DOC>\n");
        writer.flush();
      } catch( Exception ex ) { ex.printStackTrace(); }

      storyOpen = false;
    }
  }

  public void closeDoc() {
    try {
      writer.flush();
      writer.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
  }
}
