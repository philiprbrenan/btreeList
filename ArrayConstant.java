//----------------------------------------------------------------------------------------------------------------------
// Conver t a constant array of non-negative integers into a minimal set of parallel Verilog assigning if statements
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;

//D1 Construct                                                                                                          // Generate the Btree algorithm in Verilog from the equivalent Java code to produce the kernel of "Database on a Chip"

public class ArrayConstant extends Test                                                                                 // Convert a constant array of non-negative integers into a minimal set of parallel Verilog assigning if statements
 {final Stack<Block> blocks = new Stack<>();                                                                            // Array values that can all be deduced from one comparison
  final int []        array;                                                                                            // Array to convert
  final int          logLen;                                                                                            // Number of address bits required to index the array

  ArrayConstant (int [] Array)                                                                                          // Start with one block for each array element then repeatedly merge adjacent blocks having the same value do their value can bs= selected with one less bit from the index
   {array = Array;
    validate(array);
    logLen = logTwo(Array.length);

    for (int i = 0; i < Array.length; ++i) blocks.push(new Block(i, Array[i]));                                         // Initially each number in the array is unpaired with its neighbors

    for(int i = 1; i <= logLen+1; ++i)                                                                                  // Consider each pair and try and merge them to make a bigger block that can be selected with an index that is one bit shorter. The blocks are scanned log(N) times which seems acceptable as it is not part of the on chip run time
     {final Stack<Block> n = new Stack<>();                                                                             // Resulting stack
      n.push(blocks.elementAt(0));
      for(int b = 1; b < blocks.size(); ++b)                                                                            // Compare each block with the prior block
       {final Block A = n.lastElement();
        final Block B = blocks.elementAt(b);
        if (pair(A, B, i)) A.size = i << 1; else n.push(B);                                                             // Merge the two blocks or continue with two blocks
       }
      if (n.size() == blocks.size()) break;
      blocks.clear();
      blocks.addAll(n);
     }
   }

  boolean pair (Block A, Block B, int Size)                                                                             // Two blocks can be merged when they have the same value, the same size, and differ only in the next address bit
   {if ( A.size != Size  ||  B.size != Size)  return false;                                                             // The blocks must match the current size being merged
    if ((A.base >> Size) != (B.base >> Size)) return false;                                                             // The bases must match
    if ( A.value         !=  B.value)         return false;                                                             // The values must match
    return true;
   }

  public String toString()                                                                                              // Print the details of the mapping
   {final StringBuilder s = new StringBuilder();
    for(Block b : blocks) s.append(""+b);
    return ""+s;
   }

  void validate(int [] Array)                                                                                           // Confirm that the array has at least one element and that all elements are non-negative
   {if (Array.length == 0) stop("Array must have one or more integers");
    for(int i = 0; i < Array.length; ++i)
     {final int a = Array[i];
      if (a < 0) stop("Found array element less than zero which is not allowed. Index:", i, "value:", a);
     }
   }

  String printInBinary(int Value) {return printInBinary(Value, logLen);}                                                // Print a number in binary to fit a field of the default width by adding leading zeros

  String printInBinary(int Value, int Width)                                                                            // Print a number in binary to fit a field of specified width by adding leading zeros
   {final String b = Integer.toBinaryString(Value);
    return "0".repeat(Width - b.length())+b;
   }

  String verilog(String Source, String Target)                                                                          // Print verilog if statements implementing the array
   {final StringBuilder s = new StringBuilder();

    for(Block b : blocks)
     {if (logLen > 0 && b.width() > 0) s.append(s("if ({source}[{size}-:{width}] == {width}'b{base}) {target} <= {value};\n",
      "base",   printInBinary(b.base >> logTwo(b.size), b.width()),
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

  String verilogModule(String File)                                                                                     // Print a verilog module that can be tested
   {final StringBuilder s = new StringBuilder();

    s.append(s("""
module test;
 integer i;
 integer a[{size}];
""", "size", ""+array.length));

    for(int i = 0; i < array.length; ++i) s.append("  initial a["+i+"] = "+array[i]+";\n");

    s.append("""
  function automatic integer v(input integer i);
""");
    s.append(verilog("i", "v").replaceAll(" <= ", "  = " ));

    s.append("""
  endfunction

  initial begin
    #10;
""");

    for(int i = 0; i < array.length; ++i) s.append("  assert(v("+i+") == "+array[i]+") else $fatal(v("+i+") != "+array[i]+");\n");

    s.append("""
  end
endmodule
""");
    writeFile(File, ""+s);
    return ""+s;
   }

  class Block                                                                                                           // A block of indices that index the same array value that can consequently be located with a single if statement
   {final int  base;                                                                                                    // The starting index for this block
    final int value;                                                                                                    // The value of the array element associated with this block
    int        size = 1;                                                                                                // Log of the size of the block

    Block (int Base, int Value) {base = Base; value = Value;}                                                           // Construct

    int width() {return logLen - logTwo(size);}                                                                         // Width of bit-string representation of index that should be considered in the if statement

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
if (i[0-:1] == 1'b0) v <= 0;
if (i[0-:1] == 1'b1) v <= 1;
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
if (i[3-:3] == 3'b000) v <= 1;
if (i[3-:4] == 4'b0010) v <= 2;
if (i[3-:4] == 4'b0011) v <= 0;
if (i[3-:3] == 3'b010) v <= 0;
if (i[3-:3] == 3'b011) v <= 3;
if (i[3-:3] == 3'b100) v <= 3;
if (i[3-:3] == 3'b101) v <= 4;
if (i[3-:4] == 4'b1100) v <= 5;
""");
   }

  static void test_a14()
   {//                0   1   2   3   4   5   6   7   8   9  10  11  12  13  14
    final int [] A = {1,  1,  2,  0,  0,  0,  0,  0,  0,  3,  3,  3,  3,  4,  4};
    final ArrayConstant a = new ArrayConstant(A);
    //stop(a.verilog("i", "v"));
    ok(a.verilog("i", "v"), """
if (i[3-:3] == 3'b000) v <= 1;
if (i[3-:4] == 4'b0010) v <= 2;
if (i[3-:4] == 4'b0011) v <= 0;
if (i[3-:2] == 2'b01) v <= 0;
if (i[3-:4] == 4'b1000) v <= 0;
if (i[3-:4] == 4'b1001) v <= 3;
if (i[3-:3] == 3'b101) v <= 3;
if (i[3-:4] == 4'b1100) v <= 3;
if (i[3-:4] == 4'b1101) v <= 4;
if (i[3-:4] == 4'b1110) v <= 4;
""");
   }

  static void test_a18()
   {//                0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18
    final int [] A = {1,  1,  2,  0,  0,  0,  0,  0,  0,  3,  3,  3,  3,  3,  3,  3,  4,  4,  4};
    final ArrayConstant a = new ArrayConstant(A);
    //stop(a.verilog("i", "v"));
    ok(a.verilog("i", "v"), """
if (i[4-:4] == 4'b0000) v <= 1;
if (i[4-:5] == 5'b00010) v <= 2;
if (i[4-:5] == 5'b00011) v <= 0;
if (i[4-:3] == 3'b001) v <= 0;
if (i[4-:5] == 5'b01000) v <= 0;
if (i[4-:5] == 5'b01001) v <= 3;
if (i[4-:4] == 4'b0101) v <= 3;
if (i[4-:3] == 3'b011) v <= 3;
if (i[4-:4] == 4'b1000) v <= 4;
if (i[4-:5] == 5'b10010) v <= 4;
""");
   }

  static void test_a19()
   {//                0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18  19
    final int [] A = {1,  1,  2,  0,  0,  0,  0,  0,  0,  3,  3,  3,  3,  3,  3,  3,  4,  4,  4,  5};
    final ArrayConstant a = new ArrayConstant(A);
    //stop(a.verilog("i", "v"));
    ok(a.verilog("i", "v"), """
if (i[4-:4] == 4'b0000) v <= 1;
if (i[4-:5] == 5'b00010) v <= 2;
if (i[4-:5] == 5'b00011) v <= 0;
if (i[4-:3] == 3'b001) v <= 0;
if (i[4-:5] == 5'b01000) v <= 0;
if (i[4-:5] == 5'b01001) v <= 3;
if (i[4-:4] == 4'b0101) v <= 3;
if (i[4-:3] == 3'b011) v <= 3;
if (i[4-:4] == 4'b1000) v <= 4;
if (i[4-:5] == 5'b10010) v <= 4;
if (i[4-:5] == 5'b10011) v <= 5;
""");

    final String file = "/tmp/aaaa.v";
    a.verilogModule(file);
    final String cmd = "rm -f aaaa; iverilog -I/tmp/includes/ -g2012 -o aaaa /tmp/aaaa.v  && timeout 1m ./aaaa";
    final ExecCommand x = new ExecCommand(cmd);                                                                         // Execute Verilog commands
    ok(x.exitCode, 0);
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
