#!/usr/bin/env perl
use strict;
# This is a simple perl script to test the command facility.
open(my $ih, '<', $ARGV[0]) || die "Could not open input: $!";
my $count = 0;
while (! eof $ih) {
    $count++;
    my $line = <$ih>;
    print "$count. $line";
}
close $ih;
