//----------------------------------------------------------------------------------------------------------------------
// Convert an array of inetgers to a line of hex for OpenRam ROM initialization
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;

//D1 Construct                                                                                                          // Generate the Btree algorithm in Verilog from the equivalent Java code to produce the kernel of "Database on a Chip"

public class ArrayBits extends Test                                                                                     // Develop and test a Java program to create a micro-coded cpu in Verilog
 {final            int imx;                                                                                             // Index of maximum value in array
  final            int bpw;                                                                                             // Bits per word
  final StringBuilder bits = new StringBuilder();                                                                       // Hex representation
  final StringBuilder  hex = new StringBuilder();

  ArrayBits(int[]Array)                                                                                                 // Constructor
   {if (Array.length < 1)                                                                                               // Nothing to convert
     {imx = 0; bpw = 0;
      return;
     }

    imx = max(Array);                                                                                                   // Maximum value in array
    bpw = logTwo(nextPowerOfTwo(Array[imx]));                                                                           // Bits per word

    for(int i = 0;  i < Array.length; ++i)
     {final int a =     Array[i];
      if (a < 0) stop("Element in array is less than zero, index:", i, ", value:", a);
     }

    for(int i = 0; i < Array.length; ++i)                                                                               // Build the ROM bit stream, word by word, MSB first.
     {final int a =   Array[i];
      for  (int b = bpw - 1; b >= 0; --b) bits.append(getBit(a, b) ? '1' : '0');
     }
    while(bits.length() % 4 != 0) bits.append("0");                                                                    // Padd out to as full nibble

    for(int i = 0; i < bits.length() - 3; i += 4)                                                                       // Build the ROM bit stream, word by word, MSB first.
     {int n = 0;
      for (int j = 0; j < 4; ++j) if (bits.charAt(i + j) == '1') n |= 1 << (3 - j);
      hex.append(Character.forDigit(n, 16));
     }
   }

  int max(int[]A) {int m = 0; for (int i = 1; i < A.length; i++) if (A[i] > A[m]) m = i; return m;}                     // The index of the maximum element in the array

//D1 Tests                                                                                                              // Methods useful during testing of byte machine programs

  void testsStartHere() {super.testsStartHere();}                                                                       // Divider between code to be tested and code to drive testing

  static void test_w4()
   {sayCurrentTestName();
    final int [] A = {2,5,1,7,9,3};
    final ArrayBits a = new ArrayBits(A);
    ok(a.imx  , 4);
    ok(a.bpw  , 4);
    ok(a.bits , "001001010001011110010011");
    ok(a.hex  , "251793");
    ok(a.bits.length(), 24);
   }

  static void test_w3()
   {sayCurrentTestName();
    final int [] A = {1,2,3,4,5,6,7};
    final ArrayBits a = new ArrayBits(A);
    ok(a.imx  , 6);
    ok(a.bpw  , 3);
    ok(a.bits , "001010011100101110111000");
    ok(a.hex  , "29cbb8");
    ok(a.bits.length(), 24);
   }


  static void test_w2()
   {sayCurrentTestName();
    final int [] A = {3,1,2,0,1};
    final ArrayBits a = new ArrayBits(A);
    say("AAAA", a.imx);
    say("BBBB", a.bpw);
    say("CCCC", a.bits);
    say("DDDD", a.hex);
    ok(a.imx  , 0);
    ok(a.bpw  , 2);
    ok(a.bits , "110110000100");
    ok(a.hex  , "d84");
    ok(a.bits.length(), 12);
   }

  static void test_w1()
   {sayCurrentTestName();
    final int [] A = {1, 3,1,2,0,1};
    final ArrayBits a = new ArrayBits(A);
    say("AAAA", a.imx);
    say("BBBB", a.bpw);
    say("CCCC", a.bits);
    say("DDDD", a.hex);
    ok(a.imx  , 0);
    ok(a.bpw  , 2);
    ok(a.bits , "110110000100");
    ok(a.hex  , "d84");
    ok(a.bits.length(), 12);
   }

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_w4();
    test_w3();
    test_w2();
   }

  static void newTests()                                                                                                // Tests being worked on
   {oldTests();
    //test_mem(false);
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
