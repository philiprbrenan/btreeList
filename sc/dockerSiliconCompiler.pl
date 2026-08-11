#!/usr/bin/perl -I/home/phil/perl/cpan/DataTableText/lib/ -I/home/phil/perl/cpan/GitHubCrud/lib/
#-----------------------------------------------------------------------------------------------------------------------
# Install java and silicon compiler in a container that can run as a github action for testing Java to ASIC flow
# Philip R Brenan at gmail dot com, Appa Apps Ltd Inc., 2025
#-----------------------------------------------------------------------------------------------------------------------
use v5.38;
use warnings FATAL => qw(all);
use strict;
use Carp;
use Data::Dump qw(dump);
use Data::Table::Text qw(:all);
use GitHub::Crud qw(:all);

my $user = q(philiprbrenan);                                                                                            # User
my $repo = q(btreeList);                                                                                                # Repo
my $home = q(/home/phil/btreeList);                                                                                     # Home folder
my $wf   = q(.github/workflows/dockerSiliconCompiler.yml);                                                              # Work flow on Ubuntu

my $n    = "ghcr.io/philiprbrenan/sc:latest";                                                                           # Container storage on github
my $d    = dateTimeStamp;                                                                                               # Force the file to be different on each push
my $y    = <<"END";                                                                                                     # Workflow
# Test $d

name: Docker Silicon Compiler
run-name: $repo

on:
  push:
    paths:
      - '**/dockerSiliconCompiler.yml'

jobs:

  build:
    permissions:
      contents: read
      packages: write                                                                                                   # Needed for GHCR push

    runs-on: ubuntu-latest

    steps:
    - name: Checkout code
      uses: actions/checkout\@v4

    - name: 'Install silicon compiler in a docker container based on: https://docs.siliconcompiler.com/en/stable/user_guide/installation.html'
      run: |
        cat << 'EOF' > Dockerfile
        FROM ubuntu:22.04

        ENV DEBIAN_FRONTEND=noninteractive
        ENV TZ=Etc/UTC

        RUN apt-get update; apt-get install -y tzdata python3-dev python3-pip python3-venv curl git build-essential sudo   iverilog openjdk-25-jdk-headless tree yosys gh; rm -rf /var/lib/apt/lists/*; useradd -ms /bin/bash phil && echo "phil ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/phil

        # Everything below runs as phil
        USER phil
        WORKDIR /home/phil

        RUN python3 -m venv ./sc; ./sc/bin/pip install --upgrade pip; ./sc/bin/pip install siliconcompiler; ./sc/bin/sc-install openroad klayout yosys sv2v netgen opensta verible || true

        ENV PATH=/home/phil/sc/bin:/home/phil/.local/bin/:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
        CMD ["/bin/bash", "--login"]
        EOF

    - name: Log in to GitHub Container Registry
      uses: docker/login-action\@v2
      with:
        registry: ghcr.io
        username: \${{ github.actor }}
        password: \${{ secrets.GITHUB_TOKEN }}

    - name: Build Docker image
      run: |
        docker build -t $n .

    - name: Push Docker image to GHCR
      run: |
        docker push $n

  test:
    needs: build
    if: github.event_name == 'push' && needs.build.result == 'success'
    runs-on: ubuntu-latest

    container:
      image: $n
      options: --privileged

    permissions:
      contents: write

    steps:
    - name: Checkout code
      uses: actions/checkout\@v4

    - name: test container
      run: |
        pip show siliconcompiler
        python3 -c 'import siliconcompiler; print(siliconcompiler.__version__)'
END

my $f = writeFileUsingSavedToken $user, $repo, $wf,     $y;                                                             # Upload workflow
lll "$f  $wf";

my $F = writeFileUsingSavedToken $user, $repo, "sc/$0", $0;                                                             # Upload this file
lll "$F  sc/$0";
