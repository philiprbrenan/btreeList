#!/usr/bin/perl -I/home/phil/perl/cpan/DataTableText/lib/
#-------------------------------------------------------------------------------
# Download details of latest run on github and store in a a local postgres table
# Philip R Brenan at gmail dot com, Appa Apps Ltd Inc., 2026
#-------------------------------------------------------------------------------
use v5.38;
use warnings FATAL => qw(all);
use strict;
use Carp;
use Data::Dump qw(dump);
use Data::Table::Text qw(:all);

my $owner  = "philiprbrenan";                                                                                           # Owner of repo
my $repo   = "btreeList";                                                                                               # Repo
my $outDir = temporaryFolder();                                                                                         # Artifacts folder

sub execSql($sql)                                                                                                       # Execute some sql
 {say STDERR $sql;
   xxx qq(psql -f ).owf temporaryFile, $sql;
 }

sub downLoadJson()                                                                                                      # Download json produced by latest run on github
 {say STDERR qx(gh run download \$(gh run list --limit 1 --json databaseId --jq '.[0].databaseId') --dir $outDir);      # Download latest run
 }

sub createTable()
 {my $tableDef = <<END;
CREATE TABLE IF NOT EXISTS    verilogStatistics
 (log                         TEXT,
  dateTime                    TIMESTAMP NOT NULL,
  sourceFile                  TEXT      NOT NULL,
  testName                    TEXT      NOT NULL,

  executionSteps              INTEGER,
  instructions                INTEGER,
  codeSize                    INTEGER,
  percent                     FLOAT,

  suppressInstructionTracing  BOOLEAN,
  suppressTraceComments       BOOLEAN,
  compressInstructions        BOOLEAN,
  compressInstructionLabels   BOOLEAN,
  generateVerilog             BOOLEAN,
  runVerilog                  BOOLEAN,
  runSynthesis                BOOLEAN,
  suppressNamesInInstructions BOOLEAN,

  seconds                     FLOAT,
  command                     TEXT,
  github_commit_sha           CHAR(64),

  PRIMARY KEY (log, dateTime, sourceFile, testName)
 );
END

  execSql $tableDef;
 }

sub loadJsonIntoTable()
 {my @f = searchDirectoryTreesForMatchingFiles($outDir, qw(json));
  my @s;

  for my $f(@f)
   {my @j = readFile $f;
    for my $j(@j)
     {my $p = decodeJson $j;
      my @c = sort keys %$p;
      my @v = map {qq('$$p{$_}')} @c;
      my $c = join ", ", @c;
      my $v = join ", ", @v;
      push @s, <<END;
INSERT INTO verilogStatistics ($c) VALUES ($v);
END
     }
   }
  execSql join "\n", @s;
 }

makePath($outDir);                                                                                                      # Directory for downloaded logs
downLoadJson;                                                                                                           # Download json
createTable;                                                                                                            # Create table if it does not exist
loadJsonIntoTable;                                                                                                      # Load json into table
