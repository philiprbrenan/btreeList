//----------------------------------------------------------------------------------------------------------------------
// Use OpenRam to create a random access memory
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

//D1 Construct                                                                                                          // Generate the Btree algorithm in Verilog from the equivalent Java code to produce the kernel of "Database on a Chip"

public class Ram extends Test                                                                                           // Create a random access memory
 {final int        words;                                                                                               // Number of words in memory
  final int         size;                                                                                               // Size of each word in bytes
  final String      name;                                                                                               // Name of memory
  final FileNames folder;                                                                                               // Folder for OpenRam
  final static FileNames verilogTestsFolder = new FileNames(pwd()).verilog().tests();                                   // Verilog tests folder

  Ram(int Words, int Size, FileNames Folder, String Name)
   {words = Words; size = Size; folder = Folder; name = Name;

    final StringBuilder s = new StringBuilder();
    final FileNames     f = folder.down(Name);
    s.append(s("""
word_size           = {size}
num_words           = {words}

num_rw_ports        = 0
num_r_ports         = 1
num_w_ports         = 1

check_lvsdrc        = True

output_name         = "{name}"
output_path         = "macro/{name}"

tech_name           = "sky130"
nominal_corner_only = True

route_supplies      = "ring"
check_lvsdrc        = True
print_banner        = False
""",
"size",  ""+size,
"words", ""+words,
"name",  name));
    final String p = writeFile(f.same(name).py$(),               s);
     final String c = s(
"docker run --rm  -v{f}:{f} -w{f} ghcr.io/philiprbrenan/or_local:latest python3 /opt/OpenRAM/sram_compiler.py {n}",
"f", f.folder,
"n", f.same(name).py());

    if (!github_action)                                                                                                 // Run openRam if local, cannot get a container working yet from within a container
     {final ExecCommand x = new ExecCommand(c);
      say("AAAA", x);
     }
   }

//D1 Tests                                                                                                              // Tests

  void testsStartHere() {super.testsStartHere();}                                                                       // Divider between code to be tested and code to drive testing

  private static void test_python()
   {sayCurrentTestName();
    final Ram a = new Ram(32, 32, verilogTestsFolder, "Ram");
   }

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_python();
   }

  static void newTests()                                                                                                // Tests being worked on
   {oldTests();
   }

  public static void main(String[] args)                                                                                // Test if called as a program
   {try                                                                                                                 // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {if (github_action) oldTests(); else newTests();                                                                   // Tests to run
      testSummary();                                                                                                    // Summarize test results
      System.exit(testsFailed);
     }
    catch(Exception e)                                                                                                  // Get a traceback in a format clickable in Geany
     {System.err.println(e);
      System.err.println(fullTraceBack(e));
      System.exit(testsFailed);
     }
   }
 }
