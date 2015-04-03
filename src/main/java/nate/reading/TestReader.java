package nate.reading;

import java.io.File;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;

/**
 * EDIT: Nov, 2011
 * 
 * This code stopped working with a JavaNLP update, but I don't remember what it
 * was ever used for, so I'm just commenting out the code.
 *
 */
public class TestReader {

  public TestReader() {
  
  }
  
  public void test(String dirpath) {
//    for (Annotation document: new ParsedGigawordReader(new File(dirpath))) {
//      int sid = 0;
//      for (CoreMap sentence: document.get(CoreAnnotations.SentencesAnnotation.class)) {
//          Tree root = sentence.get(CoreAnnotations.TreeAnnotation.class);
//          System.out.println("TREE " + sid + ": " + root);
//          sid++;
//      }
//      break;
//    }
  }
  
  public static void main(String[] args) {
    TestReader reader = new TestReader();
    reader.test(args[0]);
  }
}
