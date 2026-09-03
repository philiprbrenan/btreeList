//----------------------------------------------------------------------------------------------------------------------
// Convert an array of integers to a single line of hex for OpenRAM ROM initialization
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

//D1 Construct                                                                                                          // Generate the Btree algorithm in Verilog from the equivalent Java code to produce the kernel of "Database on a Chip"

public class RamBits extends Test                                                                                       // Develop and test a Java program to create a micro-coded cpu in Verilog
 {final static int BITS_PER_NIBBLE = 4;                                                                                 // Number of bits per nibble
  final int     max;                                                                                                    // Maximum value in array
  final int     bpw;                                                                                                    // Bits per word
  final String bits;                                                                                                    // Bit representation
  final String  hex;                                                                                                    // Hex representation

  RamBits(int[]Array)                                                                                                   // Constructor
   {if (Array == null || Array.length < 1) {max = 0; bpw = 0; bits = hex = null; return;}                               // Nothing to convert

    checkArray(Array);                                                                                                  // Only non negative integers are allowed
    max  = max(Array);                                                                                                  // Maximum value in array

    if (max == 0) stop("RAM not required as all the elements of the array are zero");                                   // Must have a positive element otherwise no RAM needed - cannot be called before the check for negative number otherwise this message might be misleading

    bpw  = logTwo(max + 1);                                                                                             // Bits per word needed to accommodate maximum value
    bits = convertIntsToBits(Array, bpw);                                                                               // Convert input integers into bits
    hex  = convertBitsToNibbles(bits, BITS_PER_NIBBLE);                                                                 // Convert bits to nibbles
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

  private String convertBitsToNibbles(String B, int N)                                                                  // Convert a string of  bits into nibbles
   {final StringBuilder b = new StringBuilder(B);                                                                       // Bits to convert allowing for padding of necessary
    final StringBuilder x = new StringBuilder(B.length() / N + 1);                                                      // Nibbles

    while(b.length() % N != 0) b.append('0');                                                                           // Pad out bit presentation to full nibble

    for   (int i = 0, n = 0; i < b.length(); i += N, n = 0)                                                             // Convert blocks of bits to hex nibbles
     {for (int j = 0; j < N; ++j) if (b.charAt(i + j) == '1') n |= 1 << (N - j - 1);                                    // Convert block  of bits to integer bh shifting each 1 bit into position
      x.append(Character.forDigit(n, 1 << N));                                                                          // Convert integer to nibble
     }
    return ""+x;                                                                                                        // Hex nibble representation
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
    ok(a.bpw  , 1);
    ok(a.bits , "10011");
    ok(a.hex  , "98");
   }

  private static void test_w2()
   {sayCurrentTestName();
    final int [] A = {3,1,2,0,1};
    final RamBits a = new RamBits(A);
    ok(a.bpw  , 2);
    ok(a.bits , "1101100001");
    ok(a.hex  , "d84");
   }

  private static void test_w4()
   {sayCurrentTestName();
    final int [] A = {2,5,1,7,9,3};
    final RamBits a = new RamBits(A);
    ok(a.bpw  , 4);
    ok(a.bits , "001001010001011110010011");
    ok(a.hex  , "251793");
   }

  private static void test_w3_7()
   {sayCurrentTestName();
    final int [] A = {1,2,3,4,5,6,7};
    final RamBits a = new RamBits(A);
    ok(a.bpw  , 3);
    ok(a.bits , "001010011100101110111");
    ok(a.hex  , "29cbb8");
   }

  private static void test_w3_8()
   {sayCurrentTestName();
    final int [] A = {1,2,3,4,5,6,8};
    final RamBits a = new RamBits(A);
    ok(a.bpw  , 4);
    ok(a.bits , "0001001000110100010101101000");
    ok(a.hex  , "1234568");
   }

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_max();
    test_checkArray();
    test_convertIntsToBits();
    test_convertBitsToNibbles();

    test_w1();
    test_w2();
    test_w3_7();
    test_w3_8();
    test_w4();
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
