//----------------------------------------------------------------------------------------------------------------------
// Convert an array of integers to a single line of hex for OpenRAM ROM initialization
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

//D1 Construct                                                                                                          // Generate the Btree algorithm in Verilog from the equivalent Java code to produce the kernel of "Database on a Chip"

public class RamBits extends Test                                                                                       // Develop and test a Java program to create a micro-coded cpu in Verilog
 {final static int BITS_PER_BYTE = 8, BITS_PER_NIBBLE = BITS_PER_BYTE >>> 1;                                            // Number of bits per byte and nibble
  final int     max;                                                                                                    // Maximum value in array
  final int     bpw;                                                                                                    // Bits per word
  final int     wpr;                                                                                                    // Words per row
  final String bits;                                                                                                    // Bit representation
  final String  hex;                                                                                                    // Hex representation
  final int[] array;                                                                                                    // Array being converted
  final int  trails;                                                                                                    // Number of trailing words so finish the final row

  final static FileNames verilogTestsFolder = new FileNames(fp(pwd(), "verilog")).tests();                              // Verilog tests folder

  RamBits(int[]Array)                                                                                                   // Constructor
   {array = Array;
    if (array == null || array.length < 1) {max = wpr = bpw = trails = 0; bits = hex = null; return;}                   // Nothing to convert

    checkArray(array);                                                                                                  // Only non negative integers are allowed and there most be at least one non zero entry otherwise there is not much need for a random access memory
    max  = max(array);                                                                                                  // Maximum value in array

    if (max == 0) stop("RAM not required as all the elements of the array are zero");                                   // Must have a positive element otherwise no RAM needed - cannot be called before the check for negative number otherwise this message might be misleading

    bpw     = roundUp(logTwo(max + 1), BITS_PER_BYTE);           wpr = wordsPerRow();                                   // Bits per word needed to accommodate maximum value rounded up to nearest byte as OpenRAM measures word size in bytes
    trails  = array.length % wpr == 0 ? 0 : wpr - array.length % wpr;                                                   // Trailing words on final row

    bits    = convertIntsToBits(array, bpw);                                                                            // Convert input integers into bits
    hex     = convertBitsToNibbles(bits, BITS_PER_NIBBLE) + "F".repeat(trails * bpw / BITS_PER_NIBBLE);                 // Convert bits to nibbles and add trailing zeroes to fill out the final row
   }

  private int max(int[]A) {int m = 0; for (int i = 1; i < A.length; i++) if (A[i] > m) m = A[i]; return m;}             // The maximum element in an array

  private void checkArray(int[]A)                                                                                       // Check the integers can be converted
   {for (int i = 0; i < A.length; ++i)                                                                                  // Convert input integers into bits
     {if (A[i] < 0) stop("Element in array is less than zero, index:", i, ", value:", A[i]);                            // Must be in range
     }
   }

  private String convertIntsToBits(int[]A, int W)                                                                       // Convert an array of integers into words of bits of the specified size
   {final StringBuilder b = new StringBuilder(A.length * W);                                                            // Bit representation
    for (int a : A) for (int j = W; j > 0; --j) b.append(getBit(a, j-1) ? '1' : '0');                                   // Append next bit to bit representation
    return ""+b;                                                                                                        // Bit representation
   }

  private String convertBitsToNibbles(String B, int W)                                                                  // Convert a string of  bits into nibbles of specified width
   {final StringBuilder b = new StringBuilder(B);                                                                       // Bits to convert allowing for padding of necessary
    final StringBuilder x = new StringBuilder(B.length() / W + 1);                                                      // Nibbles

    while(b.length() % W != 0) b.append('0');                                                                           // Pad out bit presentation to full nibble

    for   (int i = 0, n = 0; i < b.length(); i += W, n = 0)                                                             // Convert blocks of bits to hex nibbles
     {for (int j = 0; j < W; ++j) if (b.charAt(i + j) == '1') n |= 1 << (W - j - 1);                                    // Convert block  of bits to integer by shifting each 1 bit into position
      x.append(Character.forDigit(n, 1 << W));                                                                          // Convert integer to nibble
     }
    return ""+x;                                                                                                        // Hex nibble representation
   }

  int bytesPerWord () {return bpw / BITS_PER_BYTE;}                                                                     // Bytes per word
  int wordsPerRow ()  {return (int)Math.ceil(Math.sqrt((double)array.length / bytesPerWord() / BITS_PER_BYTE));}        // Words per row assuming bits occupy squares

  void generate(String Name, FileNames Folder)                                                                          // Generate the memory
   {final StringBuilder s = new StringBuilder();
    final FileNames     f = Folder.down(Name);
    s.append(s("""
word_size           = {wordSize}
#@words_per_row     = {rowSize}

check_lvsdrc        = True

rom_data            = "includes/{name}.hex"
data_type           = "hex"

output_name         = "{name}"
output_path         = "macro/{name}"

tech_name           = "sky130"
nominal_corner_only = True

route_supplies      = "ring"
check_lvsdrc        = True
""",
"wordSize", ""+bytesPerWord(),
"rowSize",  ""+wordsPerRow (),
"name",     Name));
    final String p = writeFile(f.same(Name).py$(),               s);
    final String d = writeFile(f.includes().same(Name).hex$(), hex+"\n");

    final String c = s(
"docker run --rm  -v{dir}:{dir} -w{dir} ghcr.io/philiprbrenan/or_local:latest python3 /opt/OpenRAM/rom_compiler.py {name}",
"dir",  f.folder,
"name", f.same(Name).py());

    if (!github_actions)                                                                                                // Run openRam if local, cannot get a container working yet from within a container
     {final ExecCommand x = new ExecCommand(c);
      say("AAAA", x);
     }
   }

//D1 Tests                                                                                                              // Tests

  void testsStartHere() {super.testsStartHere();}                                                                       // Divider between code to be tested and code to drive testing

  private static void test_max()
   {sayCurrentTestName();
    final int [] a = {2,5,1,7,9,3};
    ok(new RamBits(null).max(a), 9);
   }

  private static void test_checkArray()
   {sayCurrentTestName();
    final int [] a = {2,5,1,7,9,3};
    new RamBits(a).checkArray(a);
   }

  private static void test_convertIntsToBits()
   {sayCurrentTestName();
    final int [] a = {2,5,1,7,9,3};
    ok(new RamBits(null).convertIntsToBits(a, 4), "001001010001011110010011");
   }

  private static void test_convertBitsToNibbles()
   {sayCurrentTestName();
    final int [] a = {2,5,1,7,9,3};
    ok(new RamBits(null).convertBitsToNibbles("001001010001011110010011", 4), "251793");
   }

  private static void test_w1()
   {sayCurrentTestName();
    final int [] A = {1, 0, 0, 1, 1};
    final RamBits a = new RamBits(A);
    ok(a.bpw  , 8);
    ok(a.bits , "0000000100000000000000000000000100000001");
    ok(a.hex  , "0100000101");
   }

  private static void test_w2()
   {sayCurrentTestName();
    final int [] A = {3,1,2,0,1};
    final RamBits a = new RamBits(A);
    ok(a.bpw  , 8);
    ok(a.bits , "0000001100000001000000100000000000000001");
    ok(a.hex  , "0301020001");
   }

  private static void test_w3()
   {sayCurrentTestName();
    final int [] A = {1,2,3,4,5,6,7};
    final RamBits a = new RamBits(A);
    ok(a.bpw  , 8);
    ok(a.bits , "00000001000000100000001100000100000001010000011000000111");
    ok(a.hex  , "01020304050607");
   }

  private static void test_w4()
   {sayCurrentTestName();
    final int [] A = {2,5,1,7,9,3};
    final RamBits a = new RamBits(A);
    ok(a.bpw  , 8);
    ok(a.bits , "000000100000010100000001000001110000100100000011");
    ok(a.hex  , "020501070903");
   }

  private static void test_w9()
   {sayCurrentTestName();
    final int [] A = {257, 258, 259, 260};
    final RamBits a = new RamBits(A);
    ok(a.bpw  , 16);
    ok(a.wpr  , 1);
    ok(a.bits , "0000000100000001000000010000001000000001000000110000000100000100");
    ok(a.hex  , "0101010201030104");
//trails  = array.length % wpr == 0 ? 0 : wpr - array.length % wpr;                                                   // Trailing words on final row
//int wordsPerRow ()  {return sqrt(bytesPerWord() * BITS_PER_BYTE * array.length);}                                     // Words per row assuming bits occupy squares
   }

  private static void test_python()
   {sayCurrentTestName();
    final int [] A = {1, 2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31};
    final RamBits a = new RamBits(A);
    //ok(a.bits , "00000001000000100000001100000101000001110000101100001101");
    //ok(a.hex  , "01020305070b0d");
    a.generate("RomPrimes", verilogTestsFolder);
   }

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_max();
    test_checkArray();
    test_convertIntsToBits();
    test_convertBitsToNibbles();

    test_w1();
    test_w2();
    test_w3();
    test_w4();
    test_w9();

    test_python();
   }

  static void newTests()                                                                                                // Tests being worked on
   {oldTests();
   }

  public static void main(String[] args)                                                                                // Test if called as a program
   {try                                                                                                                 // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {if (github_actions) oldTests(); else newTests();                                                                  // Tests to run
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
