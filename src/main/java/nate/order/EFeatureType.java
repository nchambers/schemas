package nate.order;


/**
 * Types of features for single events, not tlinks
 */
public enum EFeatureType {
  POS0, POS1, POS2, POS3, // POS3 is the event root
    MODAL_WORD, HAVE_WORD, BE_WORD, NOT_WORD, 
    WORD, LEMMA, SYNSET, 
    TENSE, ASPECT, MODAL, POLARITY, CLASS;
}
