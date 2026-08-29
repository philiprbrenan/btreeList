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

    for(int i = 1; i <= logLen+1; ++i)                                                                                  // Consider each pair and try and merge them into a bigger block
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
     {s.append(s("if ({source}[{size}:{width}] == {prefix}) {target} <= {value};\n",
      "prefix", ""+(b.base >> b.size-1),
      "size",   ""+logLen,
      "source", ""+Source,
      "target", ""+Target,
      "value",  ""+b.value,
      "width",  ""+b.width()));
     }
    return ""+s;
   }

  class Block                                                                                                           // A block of index = value pairs that can be located with a single if statement
   {final int  base;                                                                                                    // The starting integer for this block
    final int value;                                                                                                    // The array element associated with this block
    int        size = 1;                                                                                                // The size of the block

    Block (int Base, int Value) {base = Base; value = Value;}                                                           // The starting integer for this block

    int width() {return logLen + 1 - size;}                                                                             // Width of bit string representation of index that should be considered in the if statement

    public String toString()
     {final StringBuilder s = new StringBuilder();
      final StringJoiner  j = new StringJoiner(", ");
      return f("%s  %4d  (%d,%d)\n", printInBinary(base), value, size, width());
     }
   }

//D1 Testing                                                                                                            // Methods useful during testing of byte machine programs

  static void test_a12()
   {//                0   1   2   3   4   5   6   7   8   9  10  11  12
    final int [] A = {1,  1,  2,  0,  0,  0,  3,  3,  3,  3,  4,  4,  5};
    final ArrayConstant a = new ArrayConstant(A);
    ok(a.verilog("index", "value"), """
if (index[4:3] == 0) value <= 1;
if (index[4:4] == 2) value <= 2;
if (index[4:4] == 3) value <= 0;
if (index[4:3] == 2) value <= 0;
if (index[4:3] == 3) value <= 3;
if (index[4:3] == 4) value <= 3;
if (index[4:3] == 5) value <= 4;
if (index[4:4] == 12) value <= 5;
""");
   }

  static void test_a14()
   {//                0   1   2   3   4   5   6   7   8   9  10  11  12  13  14
    final int [] A = {1,  1,  2,  0,  0,  0,  0,  0,  0,  3,  3,  3,  3,  4,  4};
    final ArrayConstant a = new ArrayConstant(A);
    //stop(a.verilog("i", "v"));
    ok(a.verilog("i", "v"), """
if (i[4:3] == 0) v <= 1;
if (i[4:4] == 2) v <= 2;
if (i[4:4] == 3) v <= 0;
if (i[4:1] == 0) v <= 0;
if (i[4:4] == 8) v <= 0;
if (i[4:4] == 9) v <= 3;
if (i[4:3] == 5) v <= 3;
if (i[4:4] == 12) v <= 3;
if (i[4:4] == 13) v <= 4;
if (i[4:4] == 14) v <= 4;
""");
   }

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_a12();
    test_a14();
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
