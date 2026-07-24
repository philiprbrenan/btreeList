#!/usr/bin/perl -I/home/phil/perl/cpan/DataTableText/lib/ -I/home/phil/perl/cpan/GitHubCrud/lib/
#-----------------------------------------------------------------------------------------------------------------------
# Push container build to GitHub.
# Philip R Brenan at gmail dot com, Appa Apps Ltd Inc., 2026
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
my $wf      = q(.github/workflows/build-container.yml);                                                                 # Work flow on Ubuntu to build container

say STDERR timeStamp,  " push build cpntainer to github $repo";

my $d = dateTimeStamp;
my $y = <<"END";
# Test $d
name: Build container image to run tests more quickly by avoiding initial installs

on:
  push:
    paths:
      - ".github/workflows/build-container.yml"

permissions:
  contents: read
  packages: write

jobs:
  build-container:

    runs-on: ubuntu-latest

    steps:

      - name: Checkout repository
        uses: actions/checkout\@v4


      - name: Create Dockerfile
        run: |
          cat > Dockerfile <<'EOF'
          FROM ubuntu:24.04

          RUN apt-get clean
          RUN rm -rf /var/lib/apt/lists/*
          RUN apt-get update

          RUN DEBIAN_FRONTEND=noninteractive apt-get install -y git iverilog  openjdk-25-jdk-headless tree
          RUN rm -rf /var/lib/apt/lists/*

          WORKDIR /workspace
          EOF


      - name: Login to GitHub Container Registry
        uses: docker/login-action\@v3
        with:
          registry: ghcr.io
          username: \${{ github.actor }}
          password: \${{ secrets.GITHUB_TOKEN }}


      - name: Build image with Podman
        run: |
          podman build -t ghcr.io/\${{ github.repository_owner }}/btree:latest  .


      - name: Push image
        run: |
          podman push     ghcr.io/\${{ github.repository_owner }}/btree:latest
END

my $f = writeFileUsingSavedToken $user, $repo, $wf, $y;                                                               # Upload workflow
lll "$f  Work flow for push build container to: $repo";
