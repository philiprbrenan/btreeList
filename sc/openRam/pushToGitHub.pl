#!/usr/bin/perl -I/home/phil/perl/cpan/DataTableText/lib/ -I/home/phil/perl/cpan/GitHubCrud/lib/
#-----------------------------------------------------------------------------------------------------------------------
# Install OpenRam in two containers to create identical environments locally and on github
# Philip R Brenan at gmail dot com, Appa Apps Ltd Inc., 2026
#-----------------------------------------------------------------------------------------------------------------------
use v5.38;
use warnings FATAL => qw(all);
use strict;
use Carp;
use Data::Dump qw(dump);
use Data::Table::Text qw(:all);
use GitHub::Crud qw(:all);

my $userId   = q(phil);                                                                                                 # User name when running Silicon Compiler on Ubuntu - typically the useid of the person wishing to use silicon compiler in this manner
my $user     = q(philiprbrenan);                                                                                        # User name of repository owner on github
my $repo     = q(btreeList);                                                                                            # Repository on github
my $workFlow = q(dockerOpenRam);                                                                                        # Work flow name
my $uGitHub  = q(1001);                                                                                                 # Desired numeric userid on github.
my $upload   = 1;                                                                                                       # Upload teh generated work flow otherwise print it

my $wf       = fpe q(.github/workflows/), $workFlow, q(yml);                                                            # Work flow file on Ubuntu

sub Local()            {"local" ;}                                                                                      # Choose container to build - local version
sub GitHub()           {"github";}                                                                                      # Choose container to build - github version
sub imageName($Target) {"ghcr.io/\${{ github.repository_owner }}/or_$Target:latest";}                                   # Name of container to build

sub createImage($Target)                                                                                                # Install base packages and silicon compiler for local use with the correct userid number so that the docker container can write back into the local file system without running into file permission problems
 {my $G = $Target||'' eq GitHub();
  my $L = $Target||'' eq Local();
     $G or $L or die "Bad $Target";                                                                                     # Decode target

  my $imageName  = imageName $Target;                                                                                   # Image name

  my $createUser = $L ? <<END : <<END2;                                                                                 # How we create the user depends on whether it exists or not in the base operating system image. If the userid ubuntu exists we rename it to the requested user id, else if it is not present, as in the case of the ubuntu presented by github  we create a userid with the requested name. This make it possible for the container to write files back into the users workspace when running locally without file permission problems, allowing the build results to be seen outside the local container.
        RUN usermod -l ${userId} ubuntu \\
        && groupmod -n ${userId} ubuntu \\
        && usermod -d /home/${userId} -m ${userId}   \\
        && echo "${userId} ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/${userId}
END
        RUN groupadd --gid $uGitHub ${userId} \\
        && useradd --uid $uGitHub --gid $uGitHub --create-home --shell /bin/bash ${userId} \\
        && echo "${userId} ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/${userId}
END2

  return <<"END";                                                                                                       # Generate workflow on container

    - name: $Target - Create Docker file
      run: |
        cat << 'EOF' > Dockerfile
        FROM ubuntu:20.04
        LABEL org.opencontainers.image.source = "https://github.com/$user/$repo"

        ENV DEBIAN_FRONTEND=noninteractive
        ENV TZ=America/Los_Angeles

        RUN ln -s /usr/share/zoneinfo/\${TZ} /etc/localtime
        RUN echo "\${TZ}" > /etc/timezone

        RUN apt-get update
        RUN apt-get --no-install-recommends -y upgrade

        ### General build dependencies ###

        RUN apt-get install --no-install-recommends -y \\
            build-essential \\
            git \\
            ssh \\
            vim \\
            gosu \\
            autoconf \\
            automake \\
            libtool \\
            bison \\
            flex

        # Use bash instead of dash
        RUN rm /bin/sh \\
        && ln -s /bin/bash /bin/sh

        ### Python / OpenRAM ###

        RUN apt-get install --no-install-recommends -y \\
            python3 \\
            python3-numpy \\
            python3-scipy \\
            python3-pip \\
            python3-matplotlib \\
            python3-venv \\
            python3-sklearn \\
            python3-subunit \\
            python3-coverage

        ### Netgen ###

        RUN apt-get install --no-install-recommends -y \\
            m4 \\
            csh \\
            tk \\
            tk-dev \\
            tcl-dev

        ### ngspice ###

        RUN apt-get install --no-install-recommends -y \\
            libxaw7-dev \\
            libreadline8 \\
            libreadline-dev

        ### X11 ###

        RUN apt-get install --no-install-recommends -y \\
            libx11-dev \\
            libcairo2-dev

        ### KLayout ###

        RUN apt-get install --no-install-recommends -y \\
            qt5-default \\
            qtcreator \\
            ruby-full \\
            ruby-dev \\
            python3-dev \\
            qtmultimedia5-dev \\
            libqt5multimediawidgets5 \\
            libqt5multimedia5-plugins \\
            libqt5multimedia5 \\
            libqt5svg5-dev \\
            libqt5designer5 \\
            libqt5designercomponents5 \\
            libqt5xmlpatterns5-dev \\
            qttools5-dev

        ### KLayout ###

        ARG KLAYOUT_COMMIT=ea1bf40a1ee1c1c934e47a0020417503ab3d7e7e

        WORKDIR /root

        RUN git clone https://github.com/KLayout/klayout
        WORKDIR /root/klayout
        RUN git checkout \${KLAYOUT_COMMIT} \\
        && ./build.sh -qt5 -j\$(nproc) \\
        && cp -r bin-release /usr/local/klayout \\
        && rm -rf /root/klayout

        ### Trilinos ###

        ARG TRILINOS_COMMIT=trilinos-release-12-12-1

        RUN apt-get update
        RUN apt-get install --no-install-recommends -y \\
            cmake \\
            libfftw3-dev \\
            mpich \\
            libblas-dev \\
            liblapack-dev \\
            libsuitesparse-dev \\
            libfl-dev \\
            openmpi-bin \\
            libopenmpi-dev \\
            gfortran

        WORKDIR /root

        RUN git clone --depth 1 \\
            --branch \${TRILINOS_COMMIT} \\
            https://github.com/trilinos/Trilinos.git \\
            && mkdir /root/Trilinos/build \\
            && cd /root/Trilinos/build \\
            && cmake \\
            -G "Unix Makefiles" \\
            -DCMAKE_C_COMPILER=mpicc \\
            -DCMAKE_CXX_COMPILER=mpic++ \\
            -DCMAKE_Fortran_COMPILER=mpif77 \\
            -DCMAKE_CXX_FLAGS="-O3 -fPIC" \\
            -DCMAKE_C_FLAGS="-O3 -fPIC" \\
            -DCMAKE_Fortran_FLAGS="-O3 -fPIC" \\
            -DCMAKE_INSTALL_PREFIX=/usr/local/XyceLibs/Parallel \\
            -DCMAKE_MAKE_PROGRAM="make" \\
            -DTrilinos_ENABLE_NOX=ON \\
            -DNOX_ENABLE_LOCA=ON \\
            -DTrilinos_ENABLE_EpetraExt=ON \\
            -DEpetraExt_BUILD_BTF=ON \\
            -DEpetraExt_BUILD_EXPERIMENTAL=ON \\
            -DEpetraExt_BUILD_GRAPH_REORDERINGS=ON \\
            -DTrilinos_ENABLE_TrilinosCouplings=ON \\
            -DTrilinos_ENABLE_Ifpack=ON \\
            -DTrilinos_ENABLE_ShyLU=ON \\
            -DTrilinos_ENABLE_Isorropia=ON \\
            -DTrilinos_ENABLE_AztecOO=ON \\
            -DTrilinos_ENABLE_Belos=ON \\
            -DTrilinos_ENABLE_Teuchos=ON \\
            -DTeuchos_ENABLE_COMPLEX=ON \\
            -DTrilinos_ENABLE_Amesos=ON \\
            -DAmesos_ENABLE_KLU=ON \\
            -DTrilinos_ENABLE_Sacado=ON \\
            -DTrilinos_ENABLE_Kokkos=ON \\
            -DTrilinos_ENABLE_Zoltan=ON \\
            -DTrilinos_ENABLE_ALL_OPTIONAL_PACKAGES=OFF \\
            -DTrilinos_ENABLE_CXX11=ON \\
            -DTPL_ENABLE_AMD=ON \\
            -DAMD_LIBRARY_DIRS="/usr/lib" \\
            -DTPL_AMD_INCLUDE_DIRS="/usr/include/suitesparse" \\
            -DTPL_ENABLE_BLAS=ON \\
            -DTPL_ENABLE_LAPACK=ON \\
            -DTPL_ENABLE_MPI=ON \\
            /root/Trilinos \\
            && make -j4     \\
            && make install \\
            && rm -rf /root/Trilinos

        ### Xyce ###

        ARG XYCE_COMMIT=b7bb12d81f11d8b50141262537299b09d64b5565

        WORKDIR /root

        RUN git clone https://github.com/Xyce/Xyce.git \\
        && cd /root/Xyce \\
        && git checkout \${XYCE_COMMIT} \\
        && ./bootstrap \\
        && mkdir /root/Xyce/build \\
        && cd /root/Xyce/build \\
        && ../configure \\
            CXXFLAGS="-O3 -std=c++11" \\
            ARCHDIR="/usr/local/XyceLibs/Parallel" \\
            CPPFLAGS="-I/usr/include/suitesparse" \\
            --enable-mpi \\
            CXX=mpicxx \\
            CC=mpicc \\
            F77=mpif77 \\
            --prefix=/usr/local/Xyce/Parallel \\
            --enable-shared \\
            --enable-xyce-shareable \\
            && make -j4 install \\
            && rm -rf /root/Xyce

        ### ngspice ###

        ARG NGSPICE_COMMIT=032b1c32c4dbad45ff132bcfac1dbecadbd8abb0

        WORKDIR /root

        RUN git clone git://git.code.sf.net/p/ngspice/ngspice  \\
            && cd /root/ngspice \\
            && git checkout \${NGSPICE_COMMIT} \\
            && ./autogen.sh  \\
            && ./configure --enable-openmp --with-readline \\
            && make \\
            && make install \\
            && rm -rf /root/ngspice

        ### Netgen ###

        ARG NETGEN_COMMIT=1.5.221

        WORKDIR /root

        RUN git clone git://opencircuitdesign.com/netgen netgen  \\
        && cd  /root/netgen \\
        && git checkout \${NETGEN_COMMIT} \\
        && ./configure \\
        && make -j\$(nproc) \\
        && make install \\
        && rm -rf /root/netgen

        ### Icarus Verilog ###

        RUN apt-get install --no-install-recommends -y iverilog

        ### Magic ###

        ARG MAGIC_COMMIT=8.3.363

        WORKDIR /root

        RUN git clone git://opencircuitdesign.com/magic magic \\
        && cd /root/magic \\
        && git checkout \${MAGIC_COMMIT} \\
        && ./configure \\
        && make        \\
        && make install \\
        && rm -rf /root/magic

        ########################################################################
        # OpenRAM
        ########################################################################

        ARG OPENRAM_COMMIT=stable

        WORKDIR /opt

        RUN git clone https://github.com/VLSIDA/OpenRAM.git OpenRAM
        WORKDIR /opt/OpenRAM
        RUN git checkout \${OPENRAM_COMMIT}
        RUN git config --global --add safe.directory /opt/OpenRAM

        ########################################################################
        # Ciel
        #
        # Ciel requires Python 3.8+ and Ubuntu 20.04 is supported.
        ########################################################################

        RUN python3 -m pip install --no-cache-dir --upgrade ciel

        ########################################################################
        # OpenRAM / PDK environment
        ########################################################################

        ENV OPENRAM_HOME=/opt/OpenRAM/compiler
        ENV OPENRAM_TECH=/opt/OpenRAM/technology
        ENV PDK_ROOT=/opt/pdk

        ENV PYTHONPATH=/opt/OpenRAM/compiler:/opt/OpenRAM/technology/sky130

        RUN mkdir -p \${PDK_ROOT}

        ########################################################################
        # Install the Sky130 PDK using OpenRAM's own pinned installation
        ########################################################################

        RUN make sky130-pdk

        ########################################################################
        # Install the OpenRAM Sky130 SRAM library and technology files
        ########################################################################

        RUN make sky130-install

        ########################################################################
        # Configure OpenRAM to use the tools already installed in this image
        # rather than attempting to bootstrap them with Nix.
        ########################################################################

        RUN sed -i 's/^    use_nix = True\$/    use_nix = False/' \\
            /opt/OpenRAM/compiler/options.py \\
            && grep -q '^    use_nix = False\$' /opt/OpenRAM/compiler/options.py

        ########################################################################
        # Install OpenRAM as a Python package
        ########################################################################

        RUN make library

        ########################################################################
        # Verify the installation
        ########################################################################

        RUN test -d \${PDK_ROOT}/sky130A
        RUN test -d \${PDK_ROOT}/skywater-pdk
        RUN test -d \${PDK_ROOT}/sky130_fd_bd_sram
        RUN test -d /opt/OpenRAM/technology/sky130
        RUN python3 -c "import openram"

        ########################################################################
        # Clean up
        ########################################################################

        RUN apt-get remove -y \\
            build-essential \\
            autoconf \\
            automake \\
            libtool \\
            bison \\
            flex \\
            tcl-dev \\
            tk-dev \\
            cmake

        RUN apt-get clean
        RUN rm -rf /var/lib/apt/lists/*

        ########################################################################
        # Generic user
        ########################################################################

        RUN useradd $userId
        RUN mkdir /home/$userId
        RUN chown -R $userId /home/$userId
        RUN chgrp -R $userId /home/$userId

        ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games:/usr/local/games:/snap/bin

        ENV KLAYOUT_HOME=/usr/local/klayout
        ENV PATH=\${PATH}:\${KLAYOUT_HOME}
        #ENV LD_LIBRARY_PATH=\${KLAYOUT_HOME}

        ENV XYCE_HOME=/usr/local/Xyce/Parallel
        ENV XYCE_PATH=\${XYCE_HOME}/bin
        ENV PATH=\${PATH}:\${XYCE_PATH}
        ENV XYCE_LIB=\${XYCE_HOME}/lib

        ENV LD_LIBRARY_PATH=\${LD_LIBRARY_PATH}:\${KLAYOUT_HOME}:\${XYCE_LIB}

        USER $userId
        WORKDIR /home/$userId

        CMD ["/bin/bash"]

        ########################################################################
        # Look for files that can be removed
        ########################################################################

        RUN echo "=== /opt ===" \\
        && du -sh /opt/* 2>/dev/null | sort -h \\
        && echo "=== /root ===" \\
        && du -sh /root/* 2>/dev/null | sort -h \\
        && echo "=== /usr/local ===" \\
        && du -sh /usr/local/* 2>/dev/null | sort -h
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
my $l = imageName Local();                                                                                              # Name locally
my $y  = <<"END";                                                                                                       # Workflow
# $d
# Install OpenRam in two containers to create identical environments locally and on github

run-name: Docker Open Ram

on:
  push:
    paths:
      - '**/$workFlow.yml'

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
#$y .= createImage GitHub();
$y .= <<END;

  test:
    needs: build
    if: github.event_name == 'push' && needs.build.result == 'success'
    runs-on: ubuntu-latest

    container:
      image: $l
      options: --privileged --user=root

    permissions:
      contents: write

    steps:
    - name: Checkout code
      uses: actions/checkout\@v4

    - name: Test gitHub version of container
      run: |
        pip3 show openram
END
if (!$upload)                                                                                                           # Show generated workflow if dry run
 {say STDOUT $y; exit;
 }
else
 {my $p = fne $0;
  my $f = writeFileUsingSavedToken $user, $repo, $wf,     $y; lll "$f  $wf";                                            # Upload workflow
  my $F = writeFileUsingSavedToken $user, $repo, "sc/$p", $p; lll "$F  sc/$p";                                          # Upload this file
 }
