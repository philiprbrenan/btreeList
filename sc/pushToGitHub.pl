#!/usr/bin/perl -I/home/phil/perl/cpan/DataTableText/lib/ -I/home/phil/perl/cpan/GitHubCrud/lib/
#-----------------------------------------------------------------------------------------------------------------------
# Install java and silicon compiler in two containers, one for use on github, the other for local use
# Philip R Brenan at gmail dot com, Appa Apps Ltd Inc., 2026
#-----------------------------------------------------------------------------------------------------------------------
use v5.38;
use warnings FATAL => qw(all);
use strict;
use Carp;
use Data::Dump qw(dump);
use Data::Table::Text qw(:all);
use GitHub::Crud qw(:all);

my $user   =  q(philiprbrenan);                                                                                         # User on github
my $userId =  q(phil);                                                                                                  # User on ubuntu
my $repo   =  q(btreeList);                                                                                             # Repo
my $home   = qq(/home/$userId/btreeList);                                                                               # Home folder
my $wf     =  q(.github/workflows/dockerSiliconCompiler.yml);                                                           # Work flow on Ubuntu

sub Local()            {"local" ;}                                                                                      # Name on local
sub GitHub()           {"github";}                                                                                      # Name on github
sub imageName($target) {"ghcr.io/philiprbrenan/sc_$target:latest";}                                                     # Return name

sub createImage($Target)                                                                                                # Install base packages and silion compiler for local use with the correct userid number so that the docker container can write back to the local file system without running into file permission problems
 {$Target eq Local() or $Target eq GitHub() or die "Bad $Target";                                                       # Decode target
  my $G = $Target eq GitHub();
  my $L = $Target eq Local();

  my $l = imageName $Target;                                                                                            # Image name
  my $u = $G ? 1001 : 1000;                                                                                             # Userid number

  my $createUser = $L ? <<END : <<END2;                                                                                 # How we create the user depends on whether it exists or not in the base operating system image
        RUN usermod -l ${userId} ubuntu && groupmod -n ${userId} ubuntu && usermod -d /home/${userId} -m ${userId}   && echo "${userId} ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/${userId}
END
        RUN groupadd --gid 1001 ${userId} && useradd --uid 1001 --gid 1001 --create-home --shell /bin/bash ${userId} && echo "${userId} ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/${userId}
END2

  return <<"END";                                                                                                       # Workflow
    - name: $Target - Install base packages
      run: |
        cat << 'EOF' > Dockerfile
        FROM ubuntu:latest

        ENV DEBIAN_FRONTEND=noninteractive
        ENV PIP_NO_CACHE_DIR=1
        ENV TZ=Etc/UTC

        RUN apt-get update  -qq && apt-get install -qq -y tzdata python3-dev python3-pip python3-venv curl git build-essential sudo   iverilog openjdk-25-jdk-headless tree yosys gh; rm -rf /var/lib/apt/lists/*

$createUser
        USER ${userId}
        WORKDIR /home/${userId}

        RUN python3 -m venv ./sc && ./sc/bin/pip install --upgrade pip && ./sc/bin/pip install siliconcompiler && ./sc/bin/sc-install openroad klayout yosys opensta && (sudo rm -rf .cache .sc .stack || true)
        ENV PATH=/home/${userId}/sc/bin:/home/${userId}/.local/bin/:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
        CMD ["/bin/bash", "--login"]
        EOF

    - name: $Target - log in to GitHub Container Registry
      uses: docker/login-action\@v2
      with:
        registry: ghcr.io
        username: \${{ github.actor }}
        password: \${{ secrets.GITHUB_TOKEN }}

    - name: $Target - Build Docker image
      run: |
        docker build -t $l .

    - name: $Target - push Docker image to GHCR
      run: |
        docker push $l
END
 }

# Main

my $d = dateTimeStamp;                                                                                                  # Force the file to be different on each push
my $g = imageName GitHub();                                                                                             # Name on github
my $y  = <<"END";                                                                                                       # Workflow
# $d
# Install java and silicon compiler in two containers, one for use on github, the other for local use

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
$y .= createImage Local();
$y .= createImage GitHub();
$y .= <<END;
  test:
    needs: build
    if: github.event_name == 'push' && needs.build.result == 'success'
    runs-on: ubuntu-latest

    container:
      image: $g
      options: --privileged --user=$userId

    permissions:
      contents: write

    steps:
    - name: Checkout code
      uses: actions/checkout\@v4

    - name: Test gitHub version of container
      run: |
        pip show siliconcompiler
        python3 -c 'import siliconcompiler; print(siliconcompiler.__version__)'
END

my $f = writeFileUsingSavedToken $user, $repo, $wf,     $y;                                                             # Upload workflow
lll "$f  $wf";

my $p = fne $0;
my $F = writeFileUsingSavedToken $user, $repo, "sc/$p", $p;                                                             # Upload this file
lll "$F  sc/$p";
