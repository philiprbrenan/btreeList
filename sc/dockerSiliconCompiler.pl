#!/usr/bin/perl -I/home/phil/perl/cpan/DataTableText/lib/ -I/home/phil/perl/cpan/GitHubCrud/lib/
#-----------------------------------------------------------------------------------------------------------------------
# Install java and silicon compiler in a container that can run either on github or locally.
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

sub Local()            {"local" ;}                                                                                      # Name on local
sub GitHub()           {"github";}                                                                                      # Name on github
sub imageName($target) {"ghcr.io/philiprbrenan/sc_$target:latest";}                                                     # Return name

sub createLocal()                                                                                                       # Install base packages and silion compiler for local use with the correct userid number so that the docker container can write back to the local file system without running into file permission problems
 {my $l = imageName Local();                                                                                            # Image name
  my $u = 1000;                                                                                                         # Local userid
  return <<"END";                                                                                                       # Workflow
    - name: Install base packages
      run: |
        cat << 'EOF' > Dockerfile
        FROM ubuntu:latest

        ENV DEBIAN_FRONTEND=noninteractive
        ENV TZ=Etc/UTC

        RUN apt-get update  -qq
        RUN apt-get install -qq -y tzdata python3-dev python3-pip python3-venv curl git build-essential sudo   iverilog openjdk-25-jdk-headless tree yosys gh; rm -rf /var/lib/apt/lists/*

        RUN usermod -l phil ubuntu && groupmod -n phil ubuntu && usermod -d /home/phil -m phil && echo "phil ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/phil
        USER phil
        WORKDIR /home/phil

        RUN python3 -m venv ./sc; ./sc/bin/pip install --upgrade pip; ./sc/bin/pip install siliconcompiler; ./sc/bin/sc-install openroad klayout yosys sv2v netgen opensta verible

        ENV PATH=/home/phil/sc/bin:/home/phil/.local/bin/:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
        CMD ["/bin/bash", "--login"]
        EOF

    - name: Local - log in to GitHub Container Registry
      uses: docker/login-action\@v2
      with:
        registry: ghcr.io
        username: \${{ github.actor }}
        password: \${{ secrets.GITHUB_TOKEN }}

    - name: Local - Build Docker image
      run: |
        docker build -t $l .

    - name: Local - push Docker image to GHCR
      run: |
        docker push $l
END
 }

sub createGitHub()                                                                                                      # Create an image for use on github  - we have to change userid number to match what github expects
 {my $l = imageName Local();                                                                                            # Base image name
  my $g = imageName GitHub();                                                                                           # Image name
  my $u = 1001;                                                                                                         # Userid on github
  return <<"END";                                                                                                       # Workflow
    - name: Github - change userid
      run: |
        cat << 'EOF' > Dockerfile
        FROM $l
        USER root
        RUN groupmod -g 1001 phil && usermod -u 1001 phil && chown -R 1001:1001 /home/phil

        USER phil
        WORKDIR /home/phil
        EOF

    - name: GitHub - log in to GitHub Container Registry
      uses: docker/login-action\@v2
      with:
        registry: ghcr.io
        username: \${{ github.actor }}
        password: \${{ secrets.GITHUB_TOKEN }}

    - name: GitHub - build Docker image
      run: |
        docker build -t $g .

    - name: GitHub - push Docker image to GHCR
      run: |
        docker push $g
END
 }

# Main

my $d = dateTimeStamp;                                                                                                  # Force the file to be different on each push
my $g = imageName GitHub();                                                                                             # Name on github
my $y  = <<"END";                                                                                                       # Workflow
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
END
$y .= createLocal();
$y .= createGitHub();
$y .= <<END;
  test:
    needs: build
    if: github.event_name == 'push' && needs.build.result == 'success'
    runs-on: ubuntu-latest

    container:
      image: $g
      options: --privileged --user=phil

    permissions:
      contents: write

    steps:
    - name: Show identities
      run: |
        id
        id phil
        ls -ld /__w

    - name: Checkout code
      uses: actions/checkout\@v4

    - name: test container
      run: |
        pip show siliconcompiler
        python3 -c 'import siliconcompiler; print(siliconcompiler.__version__)'
END

my $f = writeFileUsingSavedToken $user, $repo, $wf,     $y;                                                             # Upload workflow
lll "$f  $wf";

my $p = fne $0;
my $F = writeFileUsingSavedToken $user, $repo, "sc/$p", $p;                                                             # Upload this file
lll "$F  sc/$p";
