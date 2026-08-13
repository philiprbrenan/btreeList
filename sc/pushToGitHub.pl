#!/usr/bin/perl -I/home/phil/perl/cpan/DataTableText/lib/ -I/home/phil/perl/cpan/GitHubCrud/lib/
#-----------------------------------------------------------------------------------------------------------------------
# Install java and silicon compiler tools in two containers to create identical environments locally and on github
# Philip R Brenan at gmail dot com, Appa Apps Ltd Inc., 2026
#-----------------------------------------------------------------------------------------------------------------------
use v5.38;
use warnings FATAL => qw(all);
use strict;
use Carp;
use Data::Dump qw(dump);
use Data::Table::Text qw(:all);
use GitHub::Crud qw(:all);

my $userId  =  q(phil);                                                                                                 # User name when running Silicon Compiler on Ubuntu - typically the useid of the person wishing to use silicon compiler in this manner
my $user    =  q(philiprbrenan);                                                                                        # User name of repository owner on github
my $repo    =  q(btreeList);                                                                                            # Repository on github
my $uGitHub =  q(1001);                                                                                                 # Desired numeric userid on github.
my $optBase =  q(iverilog openjdk-25-jdk-headless tree yosys gh);                                                       # Optional base packages to be added to containers, assumed to be installable via apt install
my $scTools =  q(openroad klayout yosys opensta);                                                                       # Silicon compiler tools to be installed. These tools will be compiled from source and then the source code and intermediate build objects will be removed to reduce the sizes of the containers
my $wf      =  q(.github/workflows/dockerSiliconCompiler.yml);                                                          # Work flow on Ubuntu

sub Local()            {"local" ;}                                                                                      # Choose container to build - local version
sub GitHub()           {"github";}                                                                                      # Choose container to build - github version
sub imageName($Target) {"ghcr.io/\${{ github.repository_owner }}/sc_$Target:latest";}                                   # Name of container to build

sub createImage($Target)                                                                                                # Install base packages and silion compiler for local use with the correct userid number so that the docker container can write back into the local file system without running into file permission problems
 {$Target eq Local() or $Target eq GitHub() or die "Bad $Target";                                                       # Decode target
  my $G = $Target eq GitHub();
  my $L = $Target eq Local();

  my $imageName  = imageName $Target;                                                                                   # Image name

  my $createUser = $L ? <<END : <<END2;                                                                                 # How we create the user depends on whether it exists or not in the base operating system image. If the userid ubuntu exists we rename it to the requested user id, else if it is not present, as in the case of the ubuntu presented by github  we create a userid with the requested name. This make it possible for the container to write files back into the users workspace when running locally without file permission problems, allowing the build results to be seen outside the local container.
        RUN usermod -l ${userId} ubuntu && groupmod -n ${userId} ubuntu && usermod -d /home/${userId} -m ${userId}   && echo "${userId} ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/${userId}
END
        RUN groupadd --gid $uGitHub ${userId} && useradd --uid $uGitHub --gid $uGitHub --create-home --shell /bin/bash ${userId} && echo "${userId} ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/${userId}
END2

  return <<"END";                                                                                                       # Workflow

    - name: $Target - Install base packages
      run: |
        cat << 'EOF' > Dockerfile
        FROM ubuntu:latest

        ENV DEBIAN_FRONTEND=noninteractive
        ENV PIP_NO_CACHE_DIR=1
        ENV TZ=Etc/UTC

        RUN apt-get update -qq && apt-get install -qq -y tzdata python3-dev python3-pip python3-venv curl git build-essential sudo $optBase; rm -rf /var/lib/apt/lists/*

$createUser
        USER ${userId}
        WORKDIR /home/${userId}

        RUN python3 -m venv ./sc && ./sc/bin/pip install --upgrade pip && ./sc/bin/pip install siliconcompiler && ./sc/bin/sc-install $scTools && (sudo rm -rf .cache .sc .stack || true)
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
        docker build -t $imageName .

    - name: $Target - push Docker image to GHCR
      run: |
        docker push $imageName
END
 }

# Main

my $d = dateTimeStamp;                                                                                                  # Force the file to be different on each push
my $g = imageName GitHub();                                                                                             # Name on github
my $y  = <<"END";                                                                                                       # Workflow
# $d
# Install java and silicon compiler tools in two containers to create identical environments locally and on github

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
