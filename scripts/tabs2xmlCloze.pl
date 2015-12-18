#!/usr/bin/perl -w
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
	print OUT $parts[1+$i] . "\n";
	print OUT "</P>\n";
    }
    print OUT "</TEXT>\n</DOC>\n";

    # Story with 2nd ending.
    print OUT "<DOC id=\"$storyID2\" type=\"story\">\n";
    print OUT "<TEXT>\n";
    for( my $i = 0; $i < 4; $i++ ) {
	print OUT "<P>\n";
	print OUT $parts[1+$i] . "\n";
	print OUT "</P>\n";
    }
    print OUT "<P>\n";
    print OUT $parts[6] . "\n";
    print OUT "</P>\n";
    print OUT "</TEXT>\n</DOC>\n";
}
close OUT;
close IN;
