#!/usr/bin/perl -I/home/phil/perl/cpan/DataTableText/lib/
#-------------------------------------------------------------------------------
# XXX
# Philip R Brenan at gmail dot com, Appa Apps Ltd Inc., 2025
#-------------------------------------------------------------------------------
use v5.38;
use warnings FATAL => qw(all);
use strict;
use Carp;
use Data::Dump qw(dump);
use Data::Table::Text qw(:all);

my $owner = "philiprbrenan";                                                                                            # Owner of repo
my $repo  = "btreeList";                                                                                                # Repo

my $outDir   = "artifacts";                                                                                             # Artifacts folder
my $jsonFile = fpe qw(artifacts json);                                                                                  # Json describing artifacts

makePath($outDir);                                                                                                      # Directory for downloaded logs

say STDERR qx(gh run download \$(gh run list --limit 1 --json databaseId --jq '.[0].databaseId') --dir $outDir);          # Download latest run
=pod

if (!-f $jsonFile)                                                                                                      # Get json description of artifacts
 {my $cmd = qq(gh api repos/$owner/$repo/actions/artifacts --per_page 1 --page 1 --paginate --jq '.artifacts[:1]');
  my $json = qx($cmd);
  die "gh command failed\n" if $? != 0;
  owf($jsonFile, $json);
  exit;
 }

my @j = readFile($jsonFile);                                                                                            # Load json

for my $j(keys @j)                                                                                                           # Each line of the json file is a block of json describing one run
 {my $A = decodeJson $j[$j];                                                                                                # Decode
  my $token = qx(gh auth token);                                                                                        # Github token

  my $I = @$A;
  for my $i(keys @$A)                                                                                                   # Runs
   {my $a = $$A[$i];                                                                                                    # Artifacts from this run
    my $u = $$a{archive_download_url};                                                                                  # Local zip file name
    my $z = $u =~ s(\A.*?artifacts/) ()igr =~ s(/zip\Z) ()igr;                                                          # Artifact identifier
    say STDERR "$i/$I $z";                                                                                              # Title
    my $zip = fpe $outDir, $z, q(zip);                                                                                  # Zip file containing artifacts
    next if -e $zip;                                                                                                    # Skip if we have already processed it
    say STDERR qx(curl -sSL -o $zip -H "Authorization: Bearer $token" -H "Accept: application/vnd.github+json" "$u");   # Download zip file

    makePath(my $d = fpd $outDir, $z);                                                                                  # Target unzip folder
    say STDERR qx(unzip -q -o $zip -d $d);                                                                              # Unzip
   }
  exit if $j > 4;
 }
=cut
