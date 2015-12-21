#!/usr/bin/perl -w
#
# Works on the dev set for version 3.
# Expected line format:
# Id      InputStoryid    InputSentence1  InputSentence2  InputSentence3  InputSentence4  RandomFifthSentenceQuiz1        RandomFifthSentenceQuiz2        AnswerRightEnding
#
# Version 1 is missing column 2 that is in version 3, so this 
# doesn't work on version 1 anymore. Just change indices if 
# you need it.
#

my $file = $ARGV[0];

open(IN, $file) or die "Cannot open $file ($!)\n";
open(OUT, ">stories.xml") or die "Cannot open to write ($!)\n";

# Skip the header line.
my $line = <IN>;

# Loop over all stories, one per line.
while( $line = <IN> ) {
    chomp $line;
    $line =~ s/\r//g; # Windows carriage returns.

    my @parts = split(/\t/, $line);
    my $storyID1 = $parts[0];
    my $storyID2 = $parts[0];
    my $answer = $parts[@parts-1];

    if( $answer eq "1" ) {
	$storyID1 = $storyID1 . "-correct";
	$storyID2 = $storyID2 . "-incorrect";
    }
    elsif( $answer eq "2" ) {
	$storyID1 = $storyID1 . "-incorrect";
	$storyID2 = $storyID2 . "-correct";
    }
    else {
	print "Unknown answer $answer from: $line\n";
	exit;
    }

    # Story with 1st ending.
    print OUT "<DOC id=\"$storyID1\" type=\"story\">\n";
    print OUT "<TEXT>\n";
    for( my $i = 0; $i < 5; $i++ ) {
	print OUT "<P>\n";
	print OUT $parts[2+$i] . "\n";
	print OUT "</P>\n";
    }
    print OUT "</TEXT>\n</DOC>\n";

    # Story with 2nd ending.
    print OUT "<DOC id=\"$storyID2\" type=\"story\">\n";
    print OUT "<TEXT>\n";
    for( my $i = 0; $i < 4; $i++ ) {
	print OUT "<P>\n";
	print OUT $parts[2+$i] . "\n";
	print OUT "</P>\n";
    }
    print OUT "<P>\n";
    print OUT $parts[7] . "\n";
    print OUT "</P>\n";
    print OUT "</TEXT>\n</DOC>\n";
}
close OUT;
close IN;
