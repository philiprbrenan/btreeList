#!/usr/bin/perl -I/home/phil/perl/cpan/DataTableText/lib/ -I/home/phil/perl/cpan/GitHubCrud/lib/
#-----------------------------------------------------------------------------------------------------------------------
# Push block memory code to GitHub.
# Philip R Brenan at gmail dot com, Appa Apps Ltd Inc., 2025
#-----------------------------------------------------------------------------------------------------------------------
use v5.38;
use warnings FATAL => qw(all);
use strict;
use Carp;
use Data::Dump qw(dump);
use Data::Table::Text qw(:all);
use GitHub::Crud qw(:all);

my $home    = q(/home/phil/);                                                                                           # Home
my $repo    = q(btreeList);                                                                                             # Repo
my $user    = q(philiprbrenan);                                                                                         # User
my $folder  = fpd $home, $repo;                                                                                         # Home folder
my $shaFile = fpe $folder, q(sha);                                                                                      # Sh256 file sums for each known file to detect changes
my $wf      = q(.github/workflows/main.yml);                                                                            # Work flow on Ubuntu - compile and test
my $wfcpd   = q(.github/workflows/cpd.yml);                                                                             # Work flow on Ubuntu - copy paste detection
my @ext     = qw(.java .pl .md);                                                                                        # Extensions of files to upload to github
my $exclude = q();                                                                                                      # Java files to exclude from testing as they are not yet ready
my $copyAndPasteCheck = 0;                                                                                              # Run copy and paste check

say STDERR timeStamp,  " push to github $repo";

my @files = searchDirectoryTreesForMatchingFiles($folder, @ext);                                                        # Files to upload
my @java  = grep {fe($_) =~ m(java)is} @files;                                                                          # Java files
   @files = changedFiles $shaFile, @files;                                                                              # Filter out files that have not changed
if (!@files)                                                                                                            # No new files
 {say "Everything up to date";
  exit;
 }


if  (1)                                                                                                                 # Upload via github crud
 {for my $s(@files)                                                                                                     # Upload each selected file
   {my $c = $s =~ m(\.(java|perl)\Z) ? readFile($s) : readBinaryFile $s;                                                # Source files might have unicode utf8, other files are binary

    if ($s =~ m(README))                                                                                                # Expand README
     {$c = expandWellKnownWordsAsUrlsInMdFormat $c if $s =~ m(README);
     }

    my $t = swapFilePrefix $s, $folder;                                                                                 # File on github
    my $w = writeFileUsingSavedToken($user, $repo, $t, $c);                                                             # Write file into github
    lll "$w  $t";
   }
 }


writeFileUsingSavedToken($user, $repo,  q(.config/geany/snippets.conf),                                                 # Save the snippets file as this was the thing I missed most after a rebuild
                        readFile(qq($home/.config/geany/snippets.conf)));
writeFileUsingSavedToken($user, $repo,  q(.config/geany/keybindings.conf),                                              # Save the keybindings file for the same reason
                        readFile(qq($home/.config/geany/keybindings.conf)));
writeFileUsingSavedToken($user, $repo,  q(.config/geany/filetypes.java),                                                # Save build commands
                        readFile(qq($home/.config/geany/filetypes.java)));
writeFileUsingSavedToken($user, $repo,  q(.xprofile),                                                                   # Save key bindings
                        readFile(qq($home/.xprofile)));

if (@java)                                                                                                              # Write workflow to test java files
 {my @j = map {fn $_} @java;                                                                                            # Java files
  my $d = dateTimeStamp;
  my $c = q(com/AppaApps/Silicon);                                                                                      # Package to classes folder
  my $j = join ', ', @j;                                                                                                # Java files without extension with separating commas
  my $J = join ' ', map {"$_.java"} @j;                                                                                 # Java files with extension without separating commas
  my $y = <<"END";
# Test $d

name: Test
run-name: $repo

on:
  push:
    paths:
      - '**/main.yml'

#concurrency:
#  group: \${{ github.workflow }}-\${{ github.ref }}
# cancel-in-progress: true

jobs:

  test:
    permissions: write-all
    runs-on: ubuntu-latest

    strategy:
      matrix:
        task: [$j]

    steps:
    - uses: actions/checkout\@v4
      with:
        ref: 'main'

    - name: 'JDK'
      uses: oracle-actions/setup-java\@v1

    - name: Verilog install
      run: |
        sudo apt install iverilog yosys tree

    - name: Position files in package
      run: |
        mkdir -p $c
        cp $J $c
        tree

    - name: Java
      run: |
        javac -g -d Classes -cp Classes $c/*.java
END

  for my $j(@j)                                                                                                         # Java files
   {$y .= <<END;

    - name: Test $j
      if: matrix.task == '$j'
      run: |
        java -XX:+UseZGC -cp Classes $c/$j

END
   }

  my $f = writeFileUsingSavedToken $user, $repo, $wf, $y;                                                               # Upload workflow
  lll "$f  Ubuntu work flow for $repo";
 }
else
 {say STDERR "No Java files changed";
 }

if ($copyAndPasteCheck)                                                                                                 # Write workflow to check copy and pastes in java code
 {my $d = dateTimeStamp;
  my $y = <<"END";
# Test $d

name: Copy Paste Detection

on:
  push:
    paths:
      - '**/cpd.yml'

jobs:
  cpd:
    name: Run Copy/Paste Detection
    runs-on: ubuntu-latest

    steps:
      - name: Checkout repository
        uses: actions/checkout\@v4

      - name: 'JDK'
        uses: oracle-actions/setup-java\@v1

      - name: Install PMD
        run: |
          PMD_VERSION=7.24.0
          wget -q https://github.com/pmd/pmd/releases/download/pmd_releases%2F\${PMD_VERSION}/pmd-dist-\${PMD_VERSION}-bin.zip
          unzip pmd-dist-\${PMD_VERSION}-bin.zip
          mv pmd-bin-\$PMD_VERSION pmd
          echo "\$PWD/pmd-bin-\${PMD_VERSION}/bin" >> \$GITHUB_PATH

      - name: Run CPD (Copy/Paste Detector)
        run: |
          tree

      - name: Run CPD (Copy/Paste Detector)
        run: |
          ./pmd/bin/pmd cpd --minimum-tokens 50  --language java --dir . --format text --no-fail-on-error --no-fail-on-violation --report-file cpd-report.txt

      - name: Show CPD Report
        run: cat cpd-report.txt

      - name: Upload CPD Report
        uses: actions/upload-artifact\@v4
        with:
          name: cpd-report
          path: cpd-report.txt
END

  my $f = writeFileUsingSavedToken $user, $repo, $wfcpd, $y;                                                            # Upload workflow
  lll "$f  Ubuntu copy paste detection work flow for $repo";
 }
