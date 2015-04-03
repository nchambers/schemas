package nate;

import java.util.Set;
import java.util.HashSet;
import java.util.Vector;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.io.File;
import java.io.StringReader;
import java.io.BufferedReader;
import java.io.FileInputStream;

import net.didion.jwnl.JWNL;
import net.didion.jwnl.dictionary.Dictionary;
import net.didion.jwnl.data.IndexWord;
import net.didion.jwnl.data.POS;

import edu.stanford.nlp.trees.TypedDependency;

import nate.util.WordNet;
import nate.util.HandleParameters;
import nate.util.Directory;

/**
 * This class takes a directory of dependencies and counts all the
 * governors tokens to calculate IDF scores.  The point is to only
 * score head words, not frivolous arguments.  Output are IDF scores
 * and counts for words, not POS tagged words.
 *
 * By default this code assumes Gigaword and saves idf scores by the
 * year it pulls from the filenames...
 * Use the -saveatend flag to turn this off and save once at the end.
 *
 * Output is files tokens-nnvb.idf and tokens-nnvb-lemmas.idf
 *
 */
public class CalculateIDFDependencies {
  String _wordnetPath = "";
  WordNet _wordnet;
  String _depsDir = "";
  String _duplicatesPath = "duplicates";
  Set<String> _duplicates;
  boolean _saveYears = true;

  // ugly globals...
  Set<String> _seen = new HashSet(100);
  Set<String> _seenLemmas = new HashSet(100);
  Set<Integer> _seenIndices = new HashSet(100);

  IDFMap idf;
  IDFMap idfLemmas;


  public CalculateIDFDependencies(String[] args) {
    HandleParameters params = new HandleParameters(args);

    idf = new IDFMap(75000);
    idfLemmas = new IDFMap(75000);

    // Save IDF scores along the way by year, or not.
    if( params.hasFlag("-saveatend") )
      _saveYears = false;
    else _saveYears = true;
    System.out.println("Saving by year: " + _saveYears);

    // WordNet
    _wordnet = new WordNet(params.get("-wordnet"));

    // Duplicate Gigaword files to ignore
    _duplicates = GigawordDuplicates.fromFile(_duplicatesPath);

    // Input directory path.
    _depsDir = args[args.length - 1];
  }


  /**
   * Recurses over a tree and adds an IDF counts leaves
   * Leaves must be Nouns, Verbs or Adjectives to be counted
   */
  public void countDepIDF(TypedDependency dep) {
    int index = dep.gov().index();

    //    System.out.println(dep);

    // Don't double count a token!
    if( !_seenIndices.contains(index) ) {
      _seenIndices.add(index);
      String token = dep.gov().label().value().toString().toLowerCase();
      String reln = dep.reln().toString();

      //       if( token.contains("arrest") ) {
      // 	System.out.println("**" + dep);
      //       }

      // Only count tokens that start with a letter
      if( token.matches("^[a-z].*") ) {
        // Count token
        if( !_seen.contains(token) ) {
          _seen.add(token);
          idf.increaseDocCount(token);
        }

        String lemma;
        // Lemmatize nouns if we know they are nouns.
        if( isNounRelation(reln) )
          lemma = _wordnet.nounToLemma(token);
        // Else try verb lemma first, then noun if that fails.
        else {
          lemma = _wordnet.verbToLemma(token);
          if( lemma == null ) lemma = _wordnet.nounToLemma(token);
        }
        // No lemma, last resort is just the token itself.
        if( lemma == null ) lemma = token;

        if( !_seenLemmas.contains(lemma) ) {
          _seenLemmas.add(lemma);
          idfLemmas.increaseDocCount(lemma);
        }

        idf.increaseTermFrequency(token);
        idfLemmas.increaseTermFrequency(lemma);
      }
    }
  }

  /**
   * @return True if the relation is a 100% sure noun modifying relation.
   *         False otherwise.
   */
  private boolean isNounRelation(String reln) {
    if( reln.equals("det") || reln.equals("nn") || 
        reln.equals("amod") || reln.equals("poss") || reln.equals("num") )
      return true;
    else return false;
  }

  /**
   * @desc Process dependencies for a single document.
   * @param alldeps A vector of sentences represented by a vector of dependencies.
   */
  private void calculateIDF( List<List<TypedDependency>> alldeps ) {
    // Clear the global seen list for this new document.
    _seen.clear();
    _seenLemmas.clear();

    // Loop over each document's deps.
    for( List<TypedDependency> sentenceDeps : alldeps ) {
      // Clear seen indices from earlier sentences.
      _seenIndices.clear();
      for( TypedDependency dep : sentenceDeps ) {
        countDepIDF(dep);
      }
    }
  }


  /**
   * This function loops over .parse files in a directory and counts 
   * the words for IDF scores.
   */
  public void count() {
    if( _depsDir.length() > 0 ) {
      for( String file : Directory.getFilesSorted(_depsDir) ) {
        if( file.contains("deps") ) {
          String year = file.substring(8,12);
          String month = file.substring(12,14);
          System.out.println("file: " + file);

          // Open the file
          GigaDocReader deps = new GigaDocReader(_depsDir + File.separator + file);
          deps.nextStory();
          List<List<TypedDependency>> alldeps = deps.getDependencies();

          // Loop over each story
          while( deps.currentStory() != null ) {
            if( _duplicates.contains(deps.currentStory()) )
              System.out.println("Duplicate! " + deps.currentStory());
            else {
              System.out.println(deps.currentStory());
              idf.increaseDocCount();
              idfLemmas.increaseDocCount();

              // Count the words in this story	      
              calculateIDF( alldeps );
            }
            deps.nextStory();
            alldeps = deps.getDependencies();
          }

          // Save to file!
          if( _saveYears && month.equals("12") ) {
            idf.calculateIDF();
            idf.saveToFile("tokens-nnvb.idf" + "-" + year);
            idfLemmas.calculateIDF();
            idfLemmas.saveToFile("tokens-nnvb-lemmas.idf" + "-" + year);
          }
        }
      }

      // Save all the IDFs if we didn't save by year already.
      if( !_saveYears ) {
        idf.calculateIDF();
        idf.saveToFile("tokens-nnvb.idf");
        idfLemmas.calculateIDF();
        idfLemmas.saveToFile("tokens-nnvb-lemmas.idf");
      }
    }
  }



  public static void main(String[] args) {
    CalculateIDFDependencies idf = new CalculateIDFDependencies(args);
    idf.count();
  }
}
