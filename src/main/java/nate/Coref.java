/**
 * Nate Chambers
 * Stanford University
 * October 2009
 */

package nate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.Vector;
import java.util.Set;
import java.util.HashSet;

import opennlp.tools.lang.english.TreebankLinker;
import opennlp.tools.coref.LinkerMode;
import opennlp.tools.parser.Parse;
import opennlp.tools.coref.DiscourseEntity;
import opennlp.tools.coref.Linker;
import opennlp.tools.coref.mention.DefaultParse;
import opennlp.tools.coref.mention.Mention;
import opennlp.tools.coref.mention.MentionContext;
import opennlp.tools.coref.mention.PTBMentionFinder;
//import opennlp.tools.parser.ParserME;
import opennlp.tools.util.Span;

import nate.util.WordNet;
import nate.util.Ling;


/**
 * Runs the opennlp coref system over parse trees.<br>
 * Processes the parse trees and stores the list of entities.
 */
public class Coref {
	TreebankLinker linker;
	List<Mention> document = new ArrayList<Mention>();
	int sentenceNumber = 1;

	public Coref(String resource) {
		try {
			linker = new TreebankLinker(resource, LinkerMode.TEST);
		} catch( Exception ex ) { ex.printStackTrace(); }
	}

	public void reset() {
		sentenceNumber = 1;
		document.clear();
	}


	public List<EntityMention> processParses(Collection<String> parses) {
//	  System.out.println("in opennlp processParses with " + parses.size());
		// reset the document to be empty
		reset();
		// process each parse
		for( String parse : parses ) processParse(parse);
		// return the entities
		return getEntities();
	}


	public void processParse(String parse) {
//	  System.out.println("parse = " + parse);
		parse = parse.replaceAll("ROOT","TOP"); // TOP token for opennlp

		//    System.out.println(parse + "\nlength=" + parse.length() + "\n");
		if( parse.length() > 35 ) {
			//      System.out.println("Adding to document");
			try {
				Parse p = Parse.parseParse(parse);
				DefaultParse dp = new DefaultParse(p,sentenceNumber);

//					System.out.println(p);
//					p.show();
//					System.out.println("Size " + dp.getTokens().size());
//					System.out.println(dp.getTokens());
//					System.out.flush();
				
				// The getMentions(dp) call freezes on long sentences, usually lists of names and things.
				// I found the 165 size to be the smallest one that froze.
				if( dp.getTokens().size() < 165 ) {

				  Mention[] extents = linker.getMentionFinder().getMentions(dp);
				  //				System.out.println("extents: " + Arrays.toString(extents));
				  //				System.out.flush();

				  // construct new parses for mentions which don't have constituents.
				  //
				  // This code finds parse referents: (NP (PRP$ their) (NN group))
				  // And adds a new NML parent: (NP (NML (PRP$ their)) (NN group))
				  for (int ei=0,en=extents.length;ei<en;ei++) {
				    if (extents[ei].getParse() == null) {
//				      Parse snp = new Parse(p.getText(), extents[ei].getSpan(), "NML", 1.0);
//				      p.insert(snp);
//				      extents[ei].setParse(new DefaultParse(snp,sentenceNumber));
				    }
				  }

				  //				    System.out.println("now:     " + Arrays.toString(extents));
				  document.addAll(Arrays.asList(extents));
				}
				else System.out.println("OpenNLP skipping long sentence: " + p);
			} catch( Exception ex ) { 
				System.out.println("--OPENNLP EXCEPTION-- (processParse) Parse skipped...");
				ex.printStackTrace();
			}
		}
		sentenceNumber++;

		/*
      if (document.size() > 0) {
      DiscourseEntity[] entities = linker.getEntities((Mention[]) document.toArray(new Mention[document.size()]));
      showEntities(entities);
      System.out.println("Altering parse trees:");

      List parses = new ArrayList();
      parses.add(p);
      (new CorefParse(parses,entities)).show();
      }
		 */
	}


	public List<EntityMention> getEntities() {
		// This throws an exception when the extents are added
		// correctly.  Sometimes an NML tag is inserted in the wrong place
		// in the tree, and this following code crashes.
		try {
			if( document.size() > 0 ) {
				DiscourseEntity[] entities = linker.getEntities((Mention[]) document.toArray(new Mention[document.size()]));
				//    showEntities(entities);

				// Create mentions of the entities
				List<EntityMention> newmentions = new ArrayList<EntityMention>();
				int i = 0;
				for( DiscourseEntity entity : entities ) {
					for( Iterator<MentionContext> iter = entity.getMentions(); iter.hasNext(); ) {
						MentionContext mc = iter.next();
						EntityMention mention = new EntityMention(mc.getSentenceNumber(), 
								mc.toText(), mc.getSpan(), i);
						newmentions.add(mention);
					}
					i++;
				}

				return newmentions;
			}
		} catch( Exception ex ) {
			System.out.println("--OPENNLP EXCEPTION-- (getEntities) Must ignore document...");
			ex.printStackTrace();
		}

		return null;
	}


	public void run() {
		List<Parse> parses = new ArrayList<Parse>();
		String lines[] = {"(TOP (S (NP (person (NNP Dave))) (VP (VBD left) (NP (NP (DT the) (NN job) (JJ first) (NN thing)) (PP (IN in) (NP (DT the) (NN morning))))) (. .)) )", "(TOP (S (NP (PRP He)) (VP (VP (VBD drank) (NP (NP (NNS lots)) (PP (IN of) (NP (NN coffee))))) (CC and) (VP (VBD picked) (NP (PRP her)) (PRT (RP up)))) (. .)) )" };

		for( int i = 0; i < lines.length; i++ ) {
			String line = lines[i];

			Parse p = Parse.parseParse(line);
			parses.add(p);
			Mention[] extents = linker.getMentionFinder().getMentions(new DefaultParse(p,sentenceNumber));
			System.out.println("extents: " + Arrays.toString(extents));
			//construct new parses for mentions which don't have constituents.
			for (int ei=0,en=extents.length;ei<en;ei++) {
				if (extents[ei].getParse() == null) {
//					Parse snp = new Parse(p.getText(),extents[ei].getSpan(),"NML",1.0);
//					p.insert(snp);
//					extents[ei].setParse(new DefaultParse(snp,sentenceNumber));
				}
			}
			System.out.println("now:     " + Arrays.toString(extents));
			document.addAll(Arrays.asList(extents));
			sentenceNumber++;
		}

		if (document.size() > 0) {
			DiscourseEntity[] entities = linker.getEntities((Mention[]) document.toArray(new Mention[document.size()]));
			showEntities(entities);
			System.out.println("Altering parse trees:");
			(new CorefParse(parses,entities)).show();
		}
	}


	private static void showEntities(DiscourseEntity[] entities) {
		for (int ei=0,en=entities.length;ei<en;ei++) {
			System.out.println(ei+" "+entities[ei]);

			Iterator<MentionContext> iter = entities[ei].getMentions();
			while( iter.hasNext() ) {
				MentionContext mc = iter.next();
				System.out.println(mc.getSentenceNumber() + " " + mc.getNounPhraseDocumentIndex() + " " + mc.getNounPhraseSentenceIndex() + " " + mc.getIndexSpan() + " " + mc);
			}

		}
	}


	public static void mergeMentions(int id1, int id2, List<EntityMention> mentions) {
		if( id1 > id2 ) {
			int temp = id1;
			id1 = id2;
			id2 = temp;
		}
		for( EntityMention mention : mentions ) {
			if( mention.entityID() == id2 )
				mention.setEntityID(id1);
		}
	}

	/**
	 * This function takes normal OpenNLP output and merges entities together
	 * that have the same strings.  We use WordNet to lookup the words and
	 * make sure they are unknown ... then merge any entities that have exact matches.
	 */
	public static List<EntityMention> mergeNameEntities(List<EntityMention> mentions,
			WordNet wordnet) {
		Map<String,Set<EntityMention>> unknowns = new HashMap<String,Set<EntityMention>>();
		Set<String> capitalized = new HashSet<String>();
		Set<String> lastwords = new HashSet<String>();
		Map<Integer,List<EntityMention>> byLength = new HashMap<Integer,List<EntityMention>>();

		//    System.out.println("--mergenameentities--");

		for( EntityMention mention : mentions ) {
			// Skip conjunctive mentions.
			if( mention.string().indexOf(" and ") == -1 && 
					mention.string().indexOf(" or ") == -1 ) {
				// split into words.
				String[] words = mention.string().split("\\s+");
				int i = 0;

				// Save mentions in buckets by length of their strings
				if( words.length > 1 ) {
					int length = mention.string().length();
					List<EntityMention> mens = byLength.get(length);
					if( mens == null ) {
						mens = new ArrayList<EntityMention>();
						byLength.put(length, mens);
					}
					mens.add(mention);
				}

				// Save mentions with names.
				for( String word : words ) {
					String lowered = word.toLowerCase();
					if( wordnet.isUnknown(lowered) ) {
						if( !Ling.isPersonPronoun(lowered) && 
								!Ling.isInanimatePronoun(lowered) ) {
							// check if capitalized
							if( Character.isUpperCase(word.charAt(0)) )
								capitalized.add(lowered);
							// check if final word
							if( i == words.length-1 )
								lastwords.add(lowered);
							// save this mention
							Set<EntityMention> seen = unknowns.get(lowered);
							if( seen == null ) {
								seen = new HashSet<EntityMention>();
								unknowns.put(lowered, seen);
							}
							seen.add(mention);
						}
					}
				}
			}
		}

		// Merge (multi-word) mentions with the same exact strings.
		// Multi-word is reliably correct, single words become more ambiguous.
		for( Integer length : byLength.keySet() ) {
			List<EntityMention> mens = byLength.get(length);
			for( int i = 0; i < mens.size()-1; i++ ) {
				for( int j = i+1; j < mens.size(); j++ ) {
					EntityMention mentioni = mens.get(i);
					EntityMention mentionj = mens.get(j);
					if( mentioni.entityID() != mentionj.entityID() ) {
						if( mentioni.string().equalsIgnoreCase(mentionj.string()) ) {
							//	      System.out.println("  -- merging same " + mentioni + " with " + mentionj);	    
							mergeMentions(mentioni.entityID(), mentionj.entityID(), mentions);
						}
					}
				}
			}
		}

		// Merge mentions with unknown words. --- single word phrases
		for( String word : unknowns.keySet() ) {
			Set<EntityMention> seen = unknowns.get(word);
			if( seen.size() > 1 ) {
				if( capitalized.contains(word) && lastwords.contains(word) ) {
					// find exact matches
					EntityMention[] arr = new EntityMention[seen.size()];
					arr = seen.toArray(arr);
					for( int i = 0; i < arr.length-1; i++ ) {
						for( int j = i+1; j < arr.length; j++ ) {
							EntityMention mentioni = arr[i];
							EntityMention mentionj = arr[j];
							if( mentioni.entityID() != mentionj.entityID() ) {
								if( mentioni.string().equalsIgnoreCase(mentionj.string()) ) {
									//		  System.out.println("  -- merging name " + mentioni + " with " + mentionj);	    
									mergeMentions(mentioni.entityID(), mentionj.entityID(), mentions);
								}
							}
						}
					}
				}
			}
		}

		return mentions;
	}



	class CorefParse {
		private Map parseMap;
		private List parses;

		public CorefParse(List parses, DiscourseEntity[] entities) {
			this.parses = parses;
			parseMap = new HashMap();
			for (int ei=0,en=entities.length;ei<en;ei++) {
				if (entities[ei].getNumMentions() > 1) {
					for (Iterator mi=entities[ei].getMentions();mi.hasNext();) {
						MentionContext mc = (MentionContext) mi.next();
						Parse mentionParse = ((DefaultParse) mc.getParse()).getParse();
						parseMap.put(mentionParse,new Integer(ei+1));
						//System.err.println("CorefParse: "+mc.getParse().hashCode()+" -> "+ (ei+1));
					}
				}
			}
		}

		public void show() {
			for (int pi=0,pn=parses.size();pi<pn;pi++) {
				Parse p = (Parse) parses.get(pi);
				show(p);
				System.out.println();
			}
		}

		private void show(Parse p) {
			int start;
			start = p.getSpan().getStart();
//			if (!p.getType().equals(ParserME.TOK_NODE)) {
//				System.out.print("(");
//				System.out.print(p.getType());
//				if (parseMap.containsKey(p)) {
//					System.out.print("#"+parseMap.get(p));
//				}
//				//System.out.print(p.hashCode()+"-"+parseMap.containsKey(p));
//				System.out.print(" ");
//			}
			Parse[] children = p.getChildren();
			for (int pi=0,pn=children.length;pi<pn;pi++) {
				Parse c = children[pi];
				Span s = c.getSpan();
				if (start < s.getStart()) {
					System.out.print(p.getText().substring(start, s.getStart()));
				}
				show(c);
				start = s.getEnd();
			}
			System.out.print(p.getText().substring(start, p.getSpan().getEnd()));
//			if (!p.getType().equals(ParserME.TOK_NODE)) {
//				System.out.print(")");
//			}
		}
	}


	public static void main(String[] args) {
		Coref coref = new Coref("/home/nchamber/code/resources/opennlp-tools-1.3.0/models/english/coref");
		//   coref.run();
		coref.processParse("(TOP (S (NP (person (NNP Dave))) (VP (VBD left) (NP (NP (DT the) (NN job) (JJ first) (NN thing)) (PP (IN in) (NP (DT the) (NN morning))))) (. .)) )");
		coref.processParse("(TOP (S (NP (PRP He)) (VP (VP (VBD drank) (NP (NP (NNS lots)) (PP (IN of) (NP (NN coffee))))) (CC and) (VP (VBD picked) (NP (PRP her)) (PRT (RP up)))) (. .)) )");
		System.out.println("Entities: " + coref.getEntities());
	}
}