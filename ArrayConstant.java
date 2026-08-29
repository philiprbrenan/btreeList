//----------------------------------------------------------------------------------------------------------------------
// Convert a constant array of non negative integers into a minimal set of parallel Verilog assigning if statements
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;

//D1 Construct                                                                                                          // Generate the Btree algorithm in Verilog from the equivalent Java code to produce the kernel of "Database on a Chip"

public class ArrayConstant extends Test                                                                                 // Convert a constant array of non negative integers into a minimal set of parallel Verilog assigning if statements
 {final Stack<Block> blocks = new Stack<>();                                                                            // Array values that can all be deduced from one comparison
  final int []        array;                                                                                            // Array to convert
  final int          logLen;                                                                                            // The next power of two that contains the length of the array

  ArrayConstant (int [] Array)                                                                                          // Construct
   {array = Array;
    validate(array);
    logLen = logTwo(Array.length);

    for (int i = 0; i < Array.length; ++i) blocks.push(new Block(i, Array[i]));                                         // Initially each number in the array is unpaired with its neighbors

    for(int i = 1; i <= logLen+1; ++i)                                                                                  // Consider each pair and try and merge them into a bigger block. The blocks are scanned log(N) times which seems acceptable as it is not part of the on chip run time
     {final Stack<Block> n = new Stack<>();                                                                             // Resulting stack
      n.push(blocks.elementAt(0));
      for(int b = 1; b < blocks.size(); ++b)                                                                            // Compare each block with the lprior block
       {final Block A = n.lastElement();
        final Block B = blocks.elementAt(b);
        if (pair(A, B, i)) A.size = i << 1; else n.push(B);                                                             // Merge the two blocks or continue with two blocks
       }
      if (n.size() == blocks.size()) break;
      blocks.clear();
      blocks.addAll(n);
     }
   }

  boolean pair (Block A, Block B, int Size)                                                                             // Pair two adjacent blocks if possible
   {if ( A.size != Size  ||  B.size != Size)  return false;                                                             // The blocks must match the current size being merged
    if ((A.base >> Size) != (B.base >> Size)) return false;                                                             // The base must match
    if ( A.value         !=  B.value)         return false;                                                             // The value must match
    return true;
   }

  public String toString()                                                                                              // Print the details of the mapping
   {final StringBuilder s = new StringBuilder();
    for(Block b : blocks) s.append(""+b);
    return ""+s;
   }

  void validate(int [] Array)                                                                                           // Confirm that the array has at least one element and that all elements are non negative
   {if (Array.length == 0) stop("Array must have one or more integers");
    for(int i = 0; i < Array.length; ++i)
     {final int a = Array[i];
      if (a < 0) stop("Found array element less than zero which is not allowed. Index:", i, "value:", a);
     }
   }

  String printInBinary(int Value)                                                                                       // Print an index of the array in binary
   {final String b = Integer.toBinaryString(Value);
    return "0".repeat(logLen - b.length())+b;
   }

  String verilog(String Source, String Target)                                                                          // Print verilog if statements implementing the array
   {final StringBuilder s = new StringBuilder();

    for(Block b : blocks)
     {if (logLen > 0 && b.width() > 0) s.append(s("if ({source}[{size}-:{width}] == {base}[{size}-:{width}]}) {target} <= {value};\n",
      "base",   ""+b.base,
      "size",   ""+(logLen-1),
      "source", ""+Source,
      "target", ""+Target,
      "value",  ""+b.value,
      "width",  ""+b.width()));
      else            s.append(s("{target} <= {value};\n",
      "target", ""+Target,
      "value",  ""+b.value));
     }
    return ""+s;
   }

  class Block                                                                                                           // A block of indices that index the same array value that can consequently be located with a single if statement
   {final int  base;                                                                                                    // The starting index for this block
    final int value;                                                                                                    // The value of the array element associated with this block
    int        size = 1;                                                                                                // Log of the size of the block

    Block (int Base, int Value) {base = Base; value = Value;}                                                           // Construct

    int width() {return logLen - logTwo(size);}                                                                                  // Width of bit string representation of index that should be considered in the if statement

    public String toString()                                                                                            // Print the current state of a block of indices pointing to the same array value
     {final StringBuilder s = new StringBuilder();
      final StringJoiner  j = new StringJoiner(", ");
      return f("%s  %4d  (%d,%d)\n", printInBinary(base), value, size, width());
     }
   }

//D1 Testing                                                                                                            // Tests

  static void test_a1()
   {//                       0
    final int []        A = {1};
    final ArrayConstant a = new ArrayConstant(A);
    //stop(a.verilog("i", "v"));
    ok(a.verilog("i", "v"), """
v <= 1;
""");
   }

  static void test_a2_0()
   {//                0   1
    final int [] A = {0,  1};
    final ArrayConstant a = new ArrayConstant(A);
    //stop(a.verilog("i", "v"));
    ok(a.verilog("i", "v"), """
if (i[0-:1] == 0[0-:1]}) v <= 0;
if (i[0-:1] == 1[0-:1]}) v <= 1;
""");
   }

  static void test_a2_1()
   {//                0   1
    final int [] A = {1,  1};
    final ArrayConstant a = new ArrayConstant(A);
    //stop(a.verilog("i", "v"));
    ok(a.verilog("i", "v"), """
v <= 1;
""");
   }

  static void test_a12()
   {//                0   1   2   3   4   5   6   7   8   9  10  11  12
    final int [] A = {1,  1,  2,  0,  0,  0,  3,  3,  3,  3,  4,  4,  5};
    final ArrayConstant a = new ArrayConstant(A);
    //stop(a.verilog("i", "v"));
    ok(a.verilog("i", "v"), """
if (i[3-:3] == 0[3-:3]}) v <= 1;
if (i[3-:4] == 2[3-:4]}) v <= 2;
if (i[3-:4] == 3[3-:4]}) v <= 0;
if (i[3-:3] == 4[3-:3]}) v <= 0;
if (i[3-:3] == 6[3-:3]}) v <= 3;
if (i[3-:3] == 8[3-:3]}) v <= 3;
if (i[3-:3] == 10[3-:3]}) v <= 4;
if (i[3-:4] == 12[3-:4]}) v <= 5;
""");
   }

  static void test_a14()
   {//                0   1   2   3   4   5   6   7   8   9  10  11  12  13  14
    final int [] A = {1,  1,  2,  0,  0,  0,  0,  0,  0,  3,  3,  3,  3,  4,  4};
    final ArrayConstant a = new ArrayConstant(A);
    //stop(a.verilog("i", "v"));
    ok(a.verilog("i", "v"), """
if (i[3-:3] == 0[3-:3]}) v <= 1;
if (i[3-:4] == 2[3-:4]}) v <= 2;
if (i[3-:4] == 3[3-:4]}) v <= 0;
if (i[3-:2] == 4[3-:2]}) v <= 0;
if (i[3-:4] == 8[3-:4]}) v <= 0;
if (i[3-:4] == 9[3-:4]}) v <= 3;
if (i[3-:3] == 10[3-:3]}) v <= 3;
if (i[3-:4] == 12[3-:4]}) v <= 3;
if (i[3-:4] == 13[3-:4]}) v <= 4;
if (i[3-:4] == 14[3-:4]}) v <= 4;
""");
   }

  static void test_a18()
   {//                0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18
    final int [] A = {1,  1,  2,  0,  0,  0,  0,  0,  0,  3,  3,  3,  3,  3,  3,  3,  4,  4,  4};
    final ArrayConstant a = new ArrayConstant(A);
    //stop(a.verilog("i", "v"));
    ok(a.verilog("i", "v"), """
if (i[4-:4] == 0[4-:4]}) v <= 1;
if (i[4-:5] == 2[4-:5]}) v <= 2;
if (i[4-:5] == 3[4-:5]}) v <= 0;
if (i[4-:3] == 4[4-:3]}) v <= 0;
if (i[4-:5] == 8[4-:5]}) v <= 0;
if (i[4-:5] == 9[4-:5]}) v <= 3;
if (i[4-:4] == 10[4-:4]}) v <= 3;
if (i[4-:3] == 12[4-:3]}) v <= 3;
if (i[4-:4] == 16[4-:4]}) v <= 4;
if (i[4-:5] == 18[4-:5]}) v <= 4;
""");
   }

  static void test_a19()
   {//                0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18  19
    final int [] A = {1,  1,  2,  0,  0,  0,  0,  0,  0,  3,  3,  3,  3,  3,  3,  3,  4,  4,  4,  5};
    final ArrayConstant a = new ArrayConstant(A);
    //stop(a.verilog("i", "v"));
    ok(a.verilog("i", "v"), """
if (i[4-:4] == 0[4-:4]}) v <= 1;
if (i[4-:5] == 2[4-:5]}) v <= 2;
if (i[4-:5] == 3[4-:5]}) v <= 0;
if (i[4-:3] == 4[4-:3]}) v <= 0;
if (i[4-:5] == 8[4-:5]}) v <= 0;
if (i[4-:5] == 9[4-:5]}) v <= 3;
if (i[4-:4] == 10[4-:4]}) v <= 3;
if (i[4-:3] == 12[4-:3]}) v <= 3;
if (i[4-:4] == 16[4-:4]}) v <= 4;
if (i[4-:5] == 18[4-:5]}) v <= 4;
if (i[4-:5] == 19[4-:5]}) v <= 5;
""");
   }

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_a1();
    test_a2_0();
    test_a2_1();
    test_a12();
    test_a14();
    test_a18();
    test_a19();
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
