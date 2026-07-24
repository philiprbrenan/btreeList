[build-menu]
FT_00_LB=_Compile
FT_00_CM=perl -M"MakeWithPerl" -e"MakeWithPerl::makeWithPerl" -I/home/phil/perl/cpan/MakeWithPerl/lib -- --compile "%d/%f" --javaHome "%d"
FT_00_WD=
FT_01_LB=Run
FT_01_CM=perl -M"MakeWithPerl" -e"MakeWithPerl::makeWithPerl" -I/home/phil/perl/cpan/MakeWithPerl/lib -- --run  "%d/%f" --javaHome "%d"
FT_01_WD=
EX_00_LB=_Execute
EX_00_CM=perl -M"MakeWithPerl" -e"MakeWithPerl::makeWithPerl" -I/home/phil/perl/cpan/MakeWithPerl/lib -- --search "%d/%f" --javaHome "%d"
EX_00_WD=
FT_02_LB=All
FT_02_CM=javac -g -d ~/btreeList/Classes -cp ~/btreeList/Classes ~/btreeList/*.java
FT_02_WD=
