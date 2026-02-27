//------------------------------------------------------------------------------
// Fixed size bit set which can locate occupied bits in log N time
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.util.*;                                                             // Standard utility library.

abstract public class BitSet extends Test                                       // Abstract fixed-size bit set using byte-level storage.
 {final int bitSize;                                                            // Number of bits in the bit set.
  final int byteSize;                                                           // Number of bytes in the bit set.

  public BitSet(int BitSize)                                                    // Constructor specifying fixed size.
   {bitSize = nextPowerOfTwo(BitSize);                                          // Record size.
    if (bitSize < 0) stop("Size must be zero or positive");                     // Validate size.
    byteSize = bytesNeeded(BitSize);                                            // A tree of bits
   }

  abstract void setByte(int Index, byte Value);                                 // Write byte to storage backend.
  abstract byte getByte(int Index);                                             // Read byte from storage backend.

  public static int bytesNeeded(int Size)                                       // Number of bytes needed for a bit set of specified size. Need twice as many bits as specified to construct a tree of bits.
   {return (Byte.SIZE - 1 + 2 * nextPowerOfTwo(Size)) / Byte.SIZE;
   }

  int size()                  {return bitSize;}                                 // Bit set size.
  int bitOffset(int bitIndex) {return bitIndex & 7;}                            // Offset inside byte.
  int byteIndex(int bitIndex) {return bitIndex >>> 3;}                          // Byte index in storage.

  public boolean getBit(int Index)                                              // Get bit value.
   {final int bIndex = byteIndex(Index);                                        // Compute byte position.
    final int offset = bitOffset(Index);                                        // Compute bit offset.

    final byte b = getByte(bIndex);                                             // Load byte.
    return ((b >>> offset) & 1) != 0;                                           // Extract bit.
   }

  public void setBit(int Index, boolean Value)                                  // Set bit value.
   {final int bIndex = byteIndex(Index);                                        // Compute byte position.
    final int offset = bitOffset(Index);                                        // Compute bit offset.
    final byte b = getByte(bIndex);                                             // Load byte.
    setByte(bIndex, (byte) (Value ? b | (1 << offset) : b & ~(1 << offset)));   // Modify bit.
   }

  public void setPath(int Index)                                                // Set bits along the path from the indexed bit to the root of the bit tree
   {if (Index < 0 || Index >= bitSize) stop("Index out of range:", Index);      // Index out of range
    for(int b = Index, p = 0, w = bitSize; w > 0; p += w, w >>>= 1, b >>>= 1)   // Step from root to leaf
     {setBit(p+b, true);                                                        // Set bit along path to root
     }
   }

  public Integer pathGt(int Index)                                              // Find the index of the next set bit above the specified bit
   {int b = Index, p = 0, w = nextPowerOfTwo(bitSize);
    for(int i : range(bitSize))                                                 // Much more than necessary
     {int B = b+1;                                                              // Is there a path down from the next bit?
      if (B < w && getBit(p+B))                                                 // Found next up bit
       {for(int j : range(i))                                                   // Step down to the leaves
         {w <<= 1;                                                              // Width of next level
          B   = 2 * B + (getBit(p-w+B+B) ? 0 : 1);                              // Follow path as low as possible
          p  -= w;                                                              // Position of next level in tree
         }
        return B;
       }
      p += w;                                                                   // Address next level of bits in tree
      w >>>= 1; if (w == 0) break;                                              // If we have reached level 0 we are finished
      b >>>= 1;                                                                 // Index in next level
     }
    return null;
   }

  public Integer pathLt(int Index)                                              // Find the index of the previous set bit below the specified bit
   {int b = Index, p = 0, w = nextPowerOfTwo(bitSize);

    for(int i : range(bitSize))                                                 // Much more than necessary
     {int B = b-1;                                                              // Is there a path down from the next bit?
      if (b > 0 && getBit(p+B))                                                 // Found next down bit
       {for(int j : range(i))                                                   // Step down to the leaves
         {w <<= 1;                                                              // Width of next level
          B   = 2 * B + (getBit(p-w+B+B) ? 0 : 1);                              // Follow path as high as possible
          p  -= w;                                                              // Position of next level in tree
         }
        return B;
       }
      p += w;                                                                   // Address next level of bits in tree
      w >>>= 1; if (w == 0) break;                                              // If we have reached level 0 we are finished
      b >>>= 1;                                                                 // Index in next level
     }
    return null;
   }

  public void clearAll()                                                        // Clear all bits.
   {final int bytes = (bitSize + 7) >>> 3;                                      // Compute number of bytes.
    for (int i : range(bytes)) setByte(i, (byte) 0);                            // Zero storage.
   }

  public String toString()                                                      // Print levels in bit tree
   {final StringBuilder s = new StringBuilder();
    int p = 0, r = bitSize;

    s.append("BitSet        ");                                                 // Title
    for   (int i : range(bitSize)) s.append(f(" %2d", i));                      // Positions of bits
    s.append("\n");

    for   (int i : range(1, bitSize))                                           // Each level
     {s.append(f("%4d %4d %4d", i, p, r));
      for (int j : range(r)) s.append(f("  %1d", getBit(p + j) ? 1 : 0));       // Bits in level
      s.append("\n");
      p += r;
      r >>>= 1;
      if (r == 0) break;                                                        // Reached the leaves
     }
    return ""+s;
   }

//D1 Tests                                                                      // Tests

  static BitSet test_bits()                                                     // Create test bitset.
   {final int N = 23;                                                           // Test size.
    final byte[]bytes = new byte[BitSet.bytesNeeded(N)];                        // Allocate backing storage.

    final BitSet b = new BitSet(N)                                              // Create a bit set
     {void setByte(int Index, byte Value) {bytes[Index] = Value;}               // Backend write.
      byte getByte(int Index)      {return bytes[Index];}                       // Backend read.
     };
    return b;                                                                   // Return test bitset.
   }

  static void test_bitSet()                                                     // Test bit manipulation.
   {final BitSet b = test_bits();                                               // Get test bitset.
    final int N = b.size();                                                     // Get logical size.

    for (int i = 0; i < N; i++) b.setBit(i, i % 2 == 0);                        // Set alternating bits.

    ok(b.getBit(4), true);                                                      // Verify bit 4.
    ok(b.getBit(5), false);                                                     // Verify bit 5.
   }

  static void test_bitTree()                                                    // Test tree of bits
   {final BitSet b = test_bits();

    b.clearAll();
    b.setPath(13);
    ok(b, """
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  0  0  0  0  0  0  0  0  0  0  0  0  0  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  1  0  0  0  0  0  0  0  0  0
   3   48    8  0  0  0  1  0  0  0  0
   4   56    4  0  1  0  0
   5   60    2  1  0
   6   62    1  1
""");
    b.setPath(19);
    ok(b, """
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  0  0  0  0  0  0  0  0  0  0  0  0  0  1  0  0  0  0  0  1  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  1  0  0  1  0  0  0  0  0  0
   3   48    8  0  0  0  1  1  0  0  0
   4   56    4  0  1  1  0
   5   60    2  1  1
   6   62    1  1
""");

    for (int i : range(14))     ok(b.pathLt(i) == null);
    for (int i : range(14, 20)) ok(b.pathLt(i), 13);
    for (int i : range(20, 32)) ok(b.pathLt(i), 19);

    for (int i : range(13))     ok(b.pathGt(i), 13);
    for (int i : range(13, 19)) ok(b.pathGt(i), 19);
    for (int i : range(19, 32)) ok(b.pathGt(i) == null);
   }

  static void test_one()
   {final int N = 1;                                                            // Test size.
    final byte[]bytes = new byte[BitSet.bytesNeeded(N)];                        // Allocate backing storage.

    final BitSet b = new BitSet(N)                                              // Create a bit set using the backing storage
     {void setByte(int Index, byte Value) {bytes[Index] = Value;}               // Backend write.
      byte getByte(int Index)      {return bytes[Index];}                       // Backend read.
     };

    b.clearAll();
    b.setPath(0);
    ok(b, """
BitSet          0
""");

    ok(b.pathLt(0) == null);
    ok(b.pathGt(0) == null);
   }

  static void test_two()
   {final int N = 2;                                                            // Test size.
    final byte[]bytes = new byte[BitSet.bytesNeeded(N)];                        // Allocate backing storage.

    final BitSet b = new BitSet(N)                                              // Create a bit set using the backing storage
     {void setByte(int Index, byte Value) {bytes[Index] = Value;}               // Backend write.
      byte getByte(int Index)      {return bytes[Index];}                       // Backend read.
     };

    b.clearAll();
    b.setPath(1);
    ok(b, """
BitSet          0  1
   1    0    2  0  1
""");

    ok(b.pathLt(0) == null, true);
    ok(b.pathLt(1) == null, true);

    ok(b.pathGt(0), 1);
    ok(b.pathGt(1) == null, true);
   }

  static void oldTests()                                                        // Tests thought to be stable.
   {test_bitSet();
    test_bitTree();
    test_one();
    test_two();
   }

  static void newTests()                                                        // Tests under development.
   {oldTests();                                                                 // Run baseline tests.
   }

  public static void main(String[] args)                                        // Program entry point for testing.
   {try                                                                         // Protected execution block.
     {if (github_actions) oldTests(); else newTests();                          // Select tests.
      if (coverageAnalysis) coverageAnalysis(12);                               // Optional coverage analysis.
      testSummary();                                                            // Summarize test results.
      System.exit(testsFailed);                                                 // Exit with status.
     }
     catch(Exception e)                                                         // Exception reporting.
     {System.err.println(e);                                                    // Print exception.
      System.err.println(fullTraceBack(e));                                     // Print traceback.
      System.exit(1);                                                           // Exit failure if an exception occurred.
     }
   }
 }
