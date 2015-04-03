package nate.util;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import nate.CountTokenPairs;
import nate.NERSpan;
import nate.WordEvent;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.PTBTokenizer.PTBTokenizerFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * Nate Chambers
 */
public class Ling {
  public enum Tense { present, past, future };
  Ling() { }

//  public static Tense getTenseOfVerb(Tree tree, int index) {
//    Tree subtree = TreeOperator.indexToSubtree(tree, index);
//    
//    Tree parent = subtree.parent(tree);
//    
//    return Tense.present;
//  }
  
  public static LexicalizedParser createParser(String grammarPath) {
    return LexicalizedParser.loadModel(grammarPath);
  }
  
  /**
   * @return true if the given string is a wh-word
   */
  public static boolean isWhWord(String token) {
    if( token.equals("who") ||
        token.equals("what") ||
        token.equals("where") ||
        token.equals("when") ||
        token.equals("why") ||
        token.equals("which") ||
        token.equals("how") )
      return true;
    else return false;
  }
  
  /**
   * Returns true if the given string is a person reference
   */
  public static boolean isPersonRef(String token) {
    if( token.equals("man") || 
        token.equals("woman") ||
        token.equals("men") ||
        token.equals("women") ||
        token.equals("person") ||
        token.equals("people") ||
        token.equals("boy") ||
        token.equals("girl")
    ) 
      return true;
    else return false;
  }

  public static boolean isAbstractPerson(String token) {
    if( token.equals("nobody") || 
        token.equals("noone") || 
        token.equals("someone") || 
        token.equals("somebody") || 
        token.equals("anyone") || 
        token.equals("anybody") 
        )
      return true;
    else return false;
  }

  /**
   * Returns true if the given string is a person pronoun
   */
  public static boolean isPersonPronoun(String token) {
    if( token.equals("he") || 
        token.equals("him") ||
        token.equals("his") ||
        token.equals("himself") ||
        token.equals("she") ||
        token.equals("her") ||
        token.equals("hers") ||
        token.equals("herself") ||
        token.equals("you") ||
        token.equals("yours") ||
        token.equals("yourself") ||
        token.equals("we") ||
        token.equals("us") ||
        token.equals("our") ||
        token.equals("ours") ||
        token.equals("ourselves") ||
        token.equals("i") ||
        token.equals("me") ||
        token.equals("my") ||
        token.equals("mine") ||
        token.equals("they") ||
        token.equals("them") ||
        token.equals("their") ||
        token.equals("themselves")
    )
      return true;
    else return false;
  }


  /**
   * Returns true if the given string is a potential non-person pronoun
   */
  public static boolean isInanimatePronoun(String token) {
    if( token.equals("it") ||
        token.equals("itself") ||
        token.equals("its") ||
        token.equals("they") ||
        token.equals("them") ||
        token.equals("their") ||
        token.equals("one")
    ) 
      return true;
    else return false;
  }
  
  public static boolean isObjectReference(String token) {
    if( token.equals("that") ||
        token.equals("this") ||
        token.equals("those") ||
        token.equals("these") ||
        token.equals("the") 
    )
      return true;
    else return false;
  }

  private static final Set<String> NUMERALS = new HashSet<String>() {{
    add("one");
    add("two");
    add("three");
    add("four");
    add("five");
    add("six");
    add("seven");
    add("eight");
    add("nine");
    add("ten");
  }};

  public static boolean isNumber(String token) {
    if( NUMERALS.contains(token) || token.matches("^[\\d\\s\\.-/\\\\]+$") )
      return true;
    return false;
  }
  
  /**
   * @return True if the token's first letter is capitalized: A-Z.  False otherwise.
   */
  public static boolean isCapitalized(String token) {
    if( token != null && token.length() > 0 ) {
      char first = token.charAt(0);
      if( first >= 'A' && first <= 'Z' )
        return true;
    }
    return false;
  }
  
  /**
   * Remove the determiner if there is one at the beginning of this
   * string, followed by a space.  Trim the string including the space char.
   */
  public static String trimLeadingDeterminer(String str) {
    int space = str.indexOf(' ');
    if( space != -1 ) {
      char ch = str.charAt(0);
      if( ch == 't' || ch == 'T' || ch == 'A' || ch == 'a' ) {
        String lower = str.toLowerCase();
        if( lower.startsWith("the ") ||
            lower.startsWith("a ") ||
            lower.startsWith("an ") ||
            lower.startsWith("these ") ||
            lower.startsWith("those ")
        )
          return str.substring(space+1);
      }
    }
    return str;
  }


  public static Map<Integer,String> particlesInSentence(Collection<TypedDependency> deps) {
    // Find any particles - O(n)
    Map<Integer, String> particles = new HashMap<Integer, String>();
    for( TypedDependency dep : deps ) {
      if( dep.reln().toString().equals("prt") ) {
        particles.put(dep.gov().index(), dep.dep().label().value());
      }
    }
    return particles;
  }

  /**
   * Finds all words that have object relations, and returns a map from the word indices
   * to a list of all object strings with their indices in the sentence.
   * @return A map from token index (governor) to its list of objects.
   */
  public static Map<Integer,List<WordPosition>> objectsInSentence(int sid, Collection<TypedDependency> sentDeps) {
    Map<Integer,List<WordPosition>> objects = new HashMap<Integer,List<WordPosition>>();
    for( TypedDependency dep : sentDeps ) {
      String reln = CountTokenPairs.normalizeRelation(dep.reln().toString(), false);
      if( reln.equals(WordEvent.DEP_OBJECT) ) {
        List<WordPosition> strs = objects.get(dep.gov().index());
        if( strs == null ) {
          strs = new ArrayList<WordPosition>();
          objects.put(dep.gov().index(), strs);
        }
        strs.add(new WordPosition(sid, dep.dep().index(), dep.dep().label().value().toString().toLowerCase()));
      }
    }
    return objects;
  }

  /**
   * Build a single string of the given List with spaces between the tokens.
   * @param words Any List of HasWord objects.
   * @return A single string (the sentence).
   */
  public static String appendTokens(List<HasWord> words) {
    if( words == null ) return null;

    int i = 0;
    StringBuffer buf = new StringBuffer();
    for( HasWord word : words ) {
      if( i > 0 ) buf.append(' ');
      buf.append(word.word());
      i++;
    }
    return buf.toString();
  }
  
  public static List<List<HasWord>> getSentencesFromText(String str, boolean invertible, String options) {
    List<List<HasWord>> sentences = new ArrayList<List<HasWord>>();
    StringReader reader = new StringReader(str);
    DocumentPreprocessor dp = new DocumentPreprocessor(reader);
    TokenizerFactory factory = null;

    if( invertible ) {
      factory = PTBTokenizer.factory(true, true);
      if( options != null && options.length() > 0 ) 
        options = "invertible=true, " + options;
      else 
        options = "invertible=true";
    } else {
      factory = PTBTokenizer.factory();
    }

//    System.out.println("Setting splitter options=" + options);
    factory.setOptions(options);
    dp.setTokenizerFactory(factory);
    
    Iterator<List<HasWord>> iter = dp.iterator();
    while( iter.hasNext() ) {
      List<HasWord> sentence = iter.next();
      sentences.add(sentence);
    }
    return sentences;
    
  }
  
  /**
   * Splits a string that might contain multiple sentences into lists of sentences.
   */
  public static List<List<HasWord>> getSentencesFromTextNoNorm(String str) {
    return getSentencesFromText(str, false, "ptb3Escaping=false");
  }
  
  /**
   * Splits a string that might contain multiple sentences into lists of sentences.
   * This function does not change any characters (e.g., " -> ''), and preserves whitespace
   * in the word objects.
   */
  public static List<List<HasWord>> getSentencesFromTextNormInvertible(String str) {
    return getSentencesFromText(str, true, "");
  }
  
  /**
   * Splits a string that might contain multiple sentences into lists of sentences.
   * This function does not change any characters (e.g., " -> ''), and preserves whitespace
   * in the word objects.
   */
  public static List<List<HasWord>> getSentencesFromTextNoNormInvertible(String str) {
    return getSentencesFromText(str, true, "ptb3Escaping=false");
  }
  
  /**
   * Splits a string that might contain multiple sentences into lists of sentences.
   */
  public static List<List<HasWord>> getSentencesFromText(String str) {
    return getSentencesFromText(str, false, "americanize=false");
  }

  // NATE note: never actually double-checked that this new function works...
  public static List<Word> getWordsFromString(String str) {
    PTBTokenizerFactory<Word> factory = (PTBTokenizerFactory<Word>)PTBTokenizer.factory();
    // Stanford's tokenizer actually changes words to American...altering our original text. Stop it!!
    factory.setOptions("americanize=false");
    Tokenizer<Word> tokenizer = factory.getTokenizer(new BufferedReader(new StringReader(str)));
    return tokenizer.tokenize();
  }

  //    DocumentPreprocessor dp = new DocumentPreprocessor(new StringReader(str));
  //    List<Word> words = dp.getWordsFromString(str);
  //  }

  /**
   * Removes all leading and trailing punctuation.
   */
  public static String trimPunctuation(String token) {
    int start = 0, end = 0;
    for( start = 0; start < token.length(); start++ ) {
      char ch = token.charAt(start);
      if( (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') )
        break;
    }

    if( start < token.length() )
      for( end = token.length()-1; end >= 0; end-- ) {
        char ch = token.charAt(end);
        if( (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') )
          break;
      }

    if( start > end )
      return "";
    else if( start > 0 || end < token.length()-1 )
      return token.substring(start, end+1);
    else 
      return token;
  }
 

  /**
   * @return True if the indexed word has a known definite determiner.
   *         False otherwise, but this doesn't necessarily mean it's not definite.
   */
  public static boolean isDefinite(Vector<TypedDependency> deps, int index) {
    //    System.out.println("isdef top with " + index);
    for( TypedDependency dep : deps ) {
      int govIndex = dep.gov().index();
      if( govIndex == index ) {
        //  System.out.println("isdef dep match: " + dep);
        if( dep.reln().toString().equals("det") ) {
          //    System.out.println("isdef " + dep + " index=" + index);
          String determiner = dep.dep().toString();
          // the, that, this, these, those, them
          if( determiner.startsWith("th") )
            return true;
        }
      }
    }
    return false;
  }

  public static boolean isProper(List<NERSpan> ners, int index) {
    for( NERSpan ner : ners ) {
      if( ner.end()-1 == index )
        return true;
    }
    return false;
  }

  public static boolean isNominative(String govLemma, char normalPOS, WordNet wordnet) {
    if( normalPOS == 'n' ) {
      if( wordnet.isNominalization(govLemma) ) {
        //  System.out.println("isNominalization " + govLemma);
        return true;
      }
      if( wordnet.isNounEvent(govLemma) ) {
        //  System.out.println("isNounEvent " + govLemma);
        return true;
      }
    }

    return false;
  }

  /**
   * Determines if the given word index is the possessor in a possessive
   * relationship.  This is either a "poss" reln, or a "prep_of" relation when
   * the index is a definite NP or an NER recognized (proper) noun.
   *
   * NOTE: For MUC, most important possessives are event nouns, so we could just
   *       call isNominative and not this function.
   */
  public static boolean isPossessive(Tree tree, List<NERSpan> ners, 
      Vector<TypedDependency> deps, int index) {
    Tree subtree = TreeOperator.indexToSubtree(tree, index);
    String posTag = subtree.label().value();

    if( posTag.startsWith("NN") ) {

      for( TypedDependency dep : deps ) {
        int depIndex = dep.dep().index();
        if( depIndex == index ) {
          String reln = dep.reln().toString();

          if( reln.equals("poss") ) return true;

          if( reln.equals("prep_of") ) {
            if( isDefinite(deps, index) || isProper(ners, index) ) {
              //      String gov = dep.gov().label().value().toString().toLowerCase();
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  public static void main(String[] args) {
    System.out.println("88.4 " + Ling.isNumber("88.4"));
    System.out.println("47 1/2 " + Ling.isNumber("47 1/2"));
    System.out.println("a47 1/2 " + Ling.isNumber("a47 1/2"));
  }
}