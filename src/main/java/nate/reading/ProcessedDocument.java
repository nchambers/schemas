package nate.reading;

import java.util.List;

//import edu.stanford.nlp.ie.temporal.timebank.Util;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;

import nate.EntityMention;
import nate.NERSpan;
import nate.util.Util;

public class ProcessedDocument {
  public String storyname;
  public List<String> parses;
  public List<NERSpan> ners;
  public List<EntityMention> mentions;
  public List<List<TypedDependency>> deps;

  public ProcessedDocument(String name, List<String> parses, List<List<TypedDependency>> deps, List<EntityMention> mentions, List<NERSpan> ners) {
    this.storyname = name;
    this.parses = parses;
    this.deps = deps;
    this.mentions = mentions;
    this.ners = ners;
  }
  
  public List<Tree> trees() {
    System.out.println("ERROR (ProcessedDocument.java): this used to work, but on older CoreNLP. Update code if this is actually used.");
    System.exit(1);
//    if( parses != null )
//      return Util.stringsToTrees(parses);
//    else return null;
    return null;
  }
}
