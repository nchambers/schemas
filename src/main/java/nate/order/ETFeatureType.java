package nate.order;


/**
 * Types of features for event-time tlinks
 */
public enum ETFeatureType {
  POS0, POS1, POS2, POS3, 
    WORD, LEMMA, SYNSET, 
    TENSE, ASPECT, MODAL, POLARITY, CLASS,
    // Time features
    TIME_TEXT, PREP, SAME_SENTENCE, EVENT_DOMINATES;
}
