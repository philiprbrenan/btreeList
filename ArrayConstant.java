//----------------------------------------------------------------------------------------------------------------------
// Convert a constant array of non negative integers into a minimal set of parallel Verilog if statements
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;

//D1 Construct                                                                                                          // Generate the Btree algorithm in Verilog from the equivalent Java code to produce the kernel of "Database on a Chip"

public class ArrayConstant extends Test                                                                                 // Convert a constant array to a minimal set of parallel Verilog if statements
 {final static Stack<Block> blocks = new Stack<>();                                                                     // Values that can all be deduced from one bit string comparison
  final int []               array;                                                                                     // Array to convert
  final int                    max;                                                                                     // The maximum integer in the array
  final int                 logLen;                                                                                     // The maximum integer in the array

  ArrayConstant (int [] Array)                                                                                          // Construct
   {array = Array;
    max   = maxAndValidate(Array);
    logLen = logTwo(Array.length);
    for (int i = 0; i < Array.length; ++i) blocks.push(new Block(i, Array[i]));

    final Stack<Block>consider = new Stack<>(); consider.addAll(blocks);                                                // Consider all blocks at start

    for(int i = 1; i <= logLen+1; ++i)                                                                                  // Pair the blocks
     {final Stack<Block> n = new Stack<>();                                                                             // Resulting stack
      n.push(blocks.elementAt(0));
      for(int b = 1; b < blocks.size(); ++b)
       {final Block A = n.lastElement();
        final Block B = blocks.elementAt(b);
        if (pair(A, B, i)) A.size = i << 1; else n.push(B);
       }
      if (n.size() == blocks.size()) break;
      blocks.clear();
      blocks.addAll(n);
     }
   }

  boolean pair (Block A, Block B, int Size)                                                                             // Pair two adjacent blocks if possible
   {if ( A.size != Size  ||  B.size != Size)  return false;
    if ((A.base >> Size) != (B.base >> Size)) return false;
    if ( A.value         !=  B.value)         return false;
    return true;
   }

  public String toString()
   {final StringBuilder s = new StringBuilder();
    for(Block b : blocks) s.append(""+b);
    return ""+s;
   }

  int maxAndValidate(int [] Array)                                                                                      // Conform that each array element is non negative and find the maximum value of the array
   {if (Array.length == 0) stop("Array must have one or more integers");
    for(int i = 0; i < Array.length; ++i)
     {final int a = Array[i];
      if (a < 0) stop("Found array element less than zero which is not allowed. Index:", i, "value:", a);
     }
    return max(Array[0], Array);
   }

  String printInBinary(int Value)
   {final String b = Integer.toBinaryString(Value);
    return "0".repeat(logLen - b.length())+b;
   }

  String verilog(String Source, String Target)
   {final StringJoiner s = new StringJoiner("\n");

    for(Block b : blocks)
     {s.add(s("if ({source}[{size}:{width}] == {prefix}) {target} <= {value};",
      "prefix", ""+(b.base >> b.size-1),
      "size",   ""+logLen,
      "source", ""+Source,
      "target", ""+Target,
      "value",  ""+b.value,
      "width",  ""+b.width()));
     }
    return ""+s;
   }

  class Block
   {final int  base;                                                                                                    // The starting integer for this block
    final int value;                                                                                                    // The array element associated with this block
    int        size = 1;                                                                                                // The size of the block

    Block (int Base, int Value) {base = Base; value = Value;}                                                           // The starting integer for this block

    int width() {return logLen + 1 - size;}                                                                             // Width f bit string representation of index that should be considered

    public String toString()
     {final StringBuilder s = new StringBuilder();
      final StringJoiner  j = new StringJoiner(", ");
      return f("%s  %4d  (%d,%d)\n", printInBinary(base), value, size, width());
     }
   }

//D1 Testing                                                                                                            // Methods useful during testing of byte machine programs

  static void test_array()
   {                  0   1   2   3   4   5   6   7   8   9  10  11  12
    final int [] A = {1,  1,  2,  0,  0,  0,  3,  3,  3,  3,  4,  4,  5};
    final ArrayConstant a = new ArrayConstant(A);
    say("BBBB", a.verilog("index", "value"));
   }

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_array();
   }

  static void newTests()                                                                                                // Tests being worked on
   {//oldTests();
    test_array();
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
