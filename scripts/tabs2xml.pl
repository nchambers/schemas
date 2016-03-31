#!/usr/bin/perl -w
#
# Works on the training set of Nasrin's Story Collection.
# Expected line format:
# storyid      workerid    storytitle    sentence1    sentence2    sentence3    sentence4    sentence5
#
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
    my $storyID = $parts[0];
    print OUT "<DOC id=\"$storyID\" type=\"story\">\n";
    print OUT "<TEXT>\n";
    for( my $i = 0; $i < 5; $i++ ) {
	print OUT "<P>\n";
	print OUT $parts[3+$i] . "\n";
	print OUT "</P>\n";
    }
    print OUT "</TEXT>\n</DOC>\n";
}
close OUT;
close IN;
