package nate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import nate.util.Directory;
import nate.util.TreeOperator;
import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.DefaultPaths;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
//import edu.stanford.nlp.trees.semgraph.SemanticGraph;
//import edu.stanford.nlp.trees.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;

/**
 * Ok. This is me hacking the Stanford CoreNLP pipeline so I can inject my own parse trees.
 * They assume the pipeline runs everything, and the coref system depends on everything,
 * including NER. This class thus makes up a fake Annotation document, injected with
 * my parse trees and tokenization. It then calls the pipeline on the Annotation. I call
 * both NER and Coref. So this class actually runs an NER system too. I figure NER is
 * pretty fast, so let's just use their most recent one and not what I might have stored
 * on disk.
 * 
 * Later improvement might be to take my pre-computed NER and manually put it into the
 * annotation. This will be a time savings for running only coref now. However, it might
 * miss out on NER improvements if my NER results are out of date.
 */
public class CorefStanford {
  StanfordCoreNLP pipeline;
  
  public CorefStanford() {
    // creates a StanfordCoreNLP object, with POS tagging, lemmatization, NER, parsing, and coreference resolution 
    Properties props = new Properties();
    props.put("annotators", "ner, dcoref");
    props.setProperty("ner.model", findModel());
    pipeline = new StanfordCoreNLP(props, false);
  }

  private String findModel() {
    if( Directory.fileExists("/home/nchamber/code/resources/all.3class.distsim.crf.ser.gz") )
      return "/home/nchamber/code/resources/all.3class.distsim.crf.ser.gz";
    if( Directory.fileExists("/home/sammy/code/resources/all.3class.distsim.crf.ser.gz") )
      return "/home/sammy/code/resources/all.3class.distsim.crf.ser.gz";
    if( Directory.fileExists("C:\\cygwin\\home\\sammy\\code\\resources\\all.3class.distsim.crf.ser.gz") )
      return "C:\\cygwin\\home\\sammy\\code\\resources\\all.3class.distsim.crf.ser.gz";
    if( Directory.fileExists("all.3class.distsim.crf.ser.gz") )
      return "all.3class.distsim.crf.ser.gz";
    else System.out.println("WARNING (CorefStanford): model all.3class.distsim.crf.ser.gz not found!");
    return null;
  }
  
  /**
   * Start from parsed trees, and run the coref.
   */
  public List<EntityMention> processParses(Collection<Tree> trees) {
    CoreLabelTokenFactory tokenfactory = new CoreLabelTokenFactory();
    List<EntityMention> entities = null;

    // Create an empty Annotation
    Annotation document = new Annotation("");

    try {
      // Setup the sentences using CoreMaps and CoreLabels.
      List<CoreMap> sentences = new ArrayList<CoreMap>();
      for( Tree tree : trees ) {
        List<CoreLabel> sentence = new ArrayList<CoreLabel>();
        CoreMap sent = new ArrayCoreMap(1);
        sent.set(TokensAnnotation.class,sentence);
        sentences.add(sent);

        // Now add the leaves from the trees as separate tokens.
        List<String> strs = TreeOperator.stringLeavesFromTree(tree);
        List<String> pos = TreeOperator.posTagsFromTree(tree);
        int start = 0, index = 0;
        for( String str : strs ) {
          CoreLabel label = tokenfactory.makeToken(str, start, start+str.length());
          start += str.length() + 1;
          label.set(PartOfSpeechAnnotation.class, pos.get(index++));
          sentence.add(label);
        }

        // Now add the parse tree.
        sent.set(TreeAnnotation.class, tree);
      }
      // Add all sentences as an annotation to the document.
      document.set(CoreAnnotations.SentencesAnnotation.class, sentences);

      //    for( CoreMap sen : sentences ) {
      //      System.out.println(sen);
      //    }

      // NOTE: You can see each annotator get created in the StanfordCoreNLP.java class. 
      //       Look at its function getDefaultAnnotatorPool()
      pipeline.annotate(document);

      //    System.out.println("AFTER");
      //    for( CoreMap sen : sentences )
      //      System.out.println(sen);      

      // This is the coreference link graph
      // Each chain stores a set of mentions that link to each other,
      // along with a method for getting the most representative mention
      // Both sentence and token offsets start at 1!
      Map<Integer, CorefChain> graph = document.get(CorefChainAnnotation.class);
      //    for( Integer id : graph.keySet() ) System.out.println(id + "\t" + graph.get(id));
      entities = extractEntities(graph);
      
    } catch( Exception ex ) {
      System.out.println("--STANFORD COREF EXCEPTION-- Parses skipped...");
      ex.printStackTrace();
    }
      
    return entities;
  }
  
  private List<EntityMention> extractEntities(Map<Integer, CorefChain> graph) {
    List<EntityMention> mentions = new ArrayList<EntityMention>();
    
    for( Integer id : graph.keySet() ) {
      CorefChain chain = graph.get(id);
      for( CorefMention cmen : chain.getMentionsInTextualOrder() ) {
        EntityMention mention = new EntityMention(cmen.sentNum, cmen.mentionSpan, cmen.startIndex, cmen.endIndex-1, cmen.corefClusterID);
//        System.out.println(cmen + "\t->\t" + mention);
        mentions.add(mention);
      }
    }
    return mentions;
  }

  /**
   * NOTE: this is a copied example from Stanford's CoreNLP. I don't actually use this code, but
   *       referenced it to make the real runit(trees) function below.
  public void runit() {
    // creates a StanfordCoreNLP object, with POS tagging, lemmatization, NER, parsing, and coreference resolution 
    Properties props = new Properties();
    props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    // read some text in the text variable
    String text = "David ran as fast as he could, but the dog caught him."; // Add your text here!

    // create an empty Annotation just with the given text
    Annotation document = new Annotation(text);

    // run all Annotators on this text
    pipeline.annotate(document);

    // these are all the sentences in this document
    // a CoreMap is essentially a Map that uses class objects as keys and has values with custom types
    List<CoreMap> sentences = document.get(SentencesAnnotation.class);

    for(CoreMap sentence: sentences) {
      // traversing the words in the current sentence
      // a CoreLabel is a CoreMap with additional token-specific methods
      for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
        // this is the text of the token
        String word = token.get(TextAnnotation.class);
        // this is the POS tag of the token
        String pos = token.get(PartOfSpeechAnnotation.class);
        // this is the NER label of the token
        String ne = token.get(NamedEntityTagAnnotation.class);       
      }

      // this is the parse tree of the current sentence
      Tree tree = sentence.get(TreeAnnotation.class);
      System.out.println("tree: " + tree);

      // this is the Stanford dependency graph of the current sentence
      SemanticGraph dependencies = sentence.get(CollapsedCCProcessedDependenciesAnnotation.class);
    }

    // This is the coreference link graph
    // Each chain stores a set of mentions that link to each other,
    // along with a method for getting the most representative mention
    // Both sentence and token offsets start at 1!
    Map<Integer, CorefChain> graph = 
      document.get(CorefChainAnnotation.class);
    for( Integer id : graph.keySet() ) {
      System.out.println(id + "\t" + graph.get(id));
    }
  }
   */

  /**
   * @param args
   */
  public static void main(String[] args) {
    TreeFactory tf = new LabeledScoredTreeFactory();
    List<Tree> trees = new ArrayList<Tree>();
    trees.add(TreeOperator.stringToTree("(TOP (S (NP (person (NNP Dave))) (VP (VBD left) (NP (NP (DT the) (NN job) (JJ first) (NN thing)) (PP (IN in) (NP (DT the) (NN morning))))) (. .)) )", tf));
    trees.add(TreeOperator.stringToTree("(TOP (S (NP (PRP He)) (VP (VP (VBD drank) (NP (NP (NNS lots)) (PP (IN of) (NP (NN coffee))))) (CC and) (VP (VBD picked) (NP (PRP her)) (PRT (RP up)))) (. .)) )", tf));

    CorefStanford coref = new CorefStanford();
    coref.processParses(trees);
  }

}
