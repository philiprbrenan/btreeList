//------------------------------------------------------------------------------
// Fixed size bit set which can locate occupied bits in log N time
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//------------------------------------------------------------------------------
// first/last next/prev ought to return a Bit rather than an Integer
// first/last next/prev not set
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.util.*;                                                             // Standard utility library.

abstract public class BitSet extends Test                                       // Abstract fixed-size bit set using byte-level storage.
 {final int bitSize;                                                            // Number of bits in the bit set.
  final int byteSize;                                                           // Number of bytes in the bit set.
  static boolean debug;

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

  public  int size()                  {return bitSize;}                         // Bit set size.
  private int bitOffset(int bitIndex) {return bitIndex & 7;}                    // Offset inside byte.
  private int byteIndex(int bitIndex) {return bitIndex >>> 3;}                  // Byte index in storage.

  public boolean getBit(int Index)                                              // Get bit value.
   {final int bIndex = byteIndex(Index);                                        // Compute byte position.
    final int offset = bitOffset(Index);                                        // Compute bit offset.

    final byte b = getByte(bIndex);                                             // Load byte.
    return ((b >>> offset) & 1) != 0;                                           // Extract bit.
   }

  void setBit(int Index, boolean Value)                                         // Set bit value.
   {final int bIndex = byteIndex(Index);                                        // Compute byte position.
    final int offset = bitOffset(Index);                                        // Compute bit offset.
    final byte b = getByte(bIndex);                                             // Load byte.
    setByte(bIndex, (byte) (Value ? b | (1 << offset) : b & ~(1 << offset)));   // Modify bit.
   }

  public void path(int Index, boolean Value)                                    // Set or clear bits along the path from the indexed bit to the root of the bit tree preserving the tree structure on clear
   {if (Value) setPath(Index); else clearPath(Index);
   }

  public void setPath(int Index)                                                // Set bits along the path from the indexed bit to the root of the bit tree
   {if (Index < 0 || Index >= bitSize) stop("Index out of range:", Index);      // Index out of range
    for(int b = Index, p = 0, w = bitSize; w > 0; p += w, w >>>= 1, b >>>= 1)   // Step from root to leaf
     {if (getBit(p+b)) return; else setBit(p+b, true);                          // Stop creating the path once we have arrived at a tree bit that is correctly set: as there are no changes at this level the upper levels must be ok too
     }
   }

  public void clearPath(int Index)                                              // Clear bits along the path from the indexed bit to the root of the bit tree unlkess thre is another path running through each bit
   {if (Index < 0 || Index >= bitSize) stop("Index out of range:", Index);      // Index out of range
    if (!getBit(Index)) return;                                                 // Bit already nnot set so teh rest of the path will be correct as no  changes at thos level

    setBit(Index, false);                                                       // Clear the actual bit
    for(int i = 0, b = Index, p = 0, w = bitSize; w > 0; ++i)                   // Step from leaf to root
     {final int B = b>>>1, q = p + w + B;

      if (B+B+1 < w && !getBit(p+B+B) && !getBit(p+B+B+1))                      // Check both bits in the previous row are off
       {if (!getBit(q)) return;                                                 // Bit is already correctly set so there is nothing more to do
        setBit(q, false);                                                       // Clear set bit along path to root
       }
      p += w; w >>>= 1; b >>>= 1;                                               // Next layer
     }
   }

  public Integer first() {return getBit(0) ? 0 : next(0);}                      // Find the index of the first set bit

  public Integer last()                                                         // Find the index of the last set bit
   {final int l = bitSize-1;
    if (getBit(l)) return l;
    return prev(l);
   }

  public Integer next(int Index)                                                // Find the index of the next set bit above the specified bit
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

  public Integer prev(int Index)                                                // Find the index of the previous set bit below the specified bit
   {int b = Index, p = 0, w = nextPowerOfTwo(bitSize);

    for(int i : range(bitSize))                                                 // Much more than necessary
     {int B = b-1;                                                              // Is there a path down from the next bit?
      if (b > 0 && getBit(p+B))                                                 // Found next down bit
       {for(int j : range(i))                                                   // Step down to the leaves
         {w <<= 1;                                                              // Width of next level
          B   = 2 * B + (getBit(p-w+B+B+1) ? 1 : 0);                              // Follow path as high as possible
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
   {for (int i : range(byteSize)) setByte(i, (byte) 0);                         // Zero storage.
   }

  public boolean integrity() {return integrity(true);}                          // Do an integrity check on the bitset to detect corruption

  public boolean integrity(boolean Stop)                                        // Do an integrity check on the bitset to detect corruption and stop on failures unless specified otherwise
   {final byte[]bytes = new byte[BitSet.bytesNeeded(bitSize)];                  // Allocate backing storage.
    final BitSet b = new BitSet(bitSize)
     {void setByte(int Index, byte Value) {bytes[Index] = Value;}               // Backend write.
      byte getByte(int Index)      {return bytes[Index];}                       // Backend read.
     };

    for   (int i : range(bitSize)) if (getBit(i)) b.setPath(i);
    final String g = toString(), e = ""+b;
    if (!g.equals(e))                                                           // Check that the current bit tree matches the expected bit tree
     {if (Stop)                                                                 // Normally we woudl stop and complain
       {ok(g, e);
        stop("Integrity failed in bit set:\n",
      "Got\n"     +  toString(),
      "Expected\n"+b.toString());
       }
      return false;
     }
    return true;
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

  static BitSet test_bits(int N)                                                // Create test bitset.
   {final byte[]bytes = new byte[BitSet.bytesNeeded(N)];                        // Allocate backing storage.

    final BitSet b = new BitSet(N)                                              // Create a bit set
     {void setByte(int Index, byte Value) {bytes[Index] = Value;}               // Backend write.
      byte getByte(int Index)      {return bytes[Index];}                       // Backend read.
     };
    return b;                                                                   // Return test bitset.
   }

  static void test_bitSet()                                                     // Test bit manipulation.
   {final BitSet b = test_bits(23);                                             // Get test bitset.
    final int N = b.size();                                                     // Get logical size.

    for (int i = 0; i < N; i++) b.setBit(i, i % 2 == 0);                        // Set alternating bits.

    ok(b.getBit(4), true);                                                      // Verify bit 4.
    ok(b.getBit(5), false);                                                     // Verify bit 5.
   }

  static void test_bitTree()                                                    // Test tree of bits
   {final BitSet b = test_bits(23);

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
    b.setPath(16);
    ok(b, """
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  0  0  0  0  0  0  0  0  0  0  0  0  0  1  0  0  1  0  0  1  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  1  0  1  1  0  0  0  0  0  0
   3   48    8  0  0  0  1  1  0  0  0
   4   56    4  0  1  1  0
   5   60    2  1  1
   6   62    1  1
""");
    b.clearPath(16);
    ok(b, """
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  0  0  0  0  0  0  0  0  0  0  0  0  0  1  0  0  0  0  0  1  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  1  0  0  1  0  0  0  0  0  0
   3   48    8  0  0  0  1  1  0  0  0
   4   56    4  0  1  1  0
   5   60    2  1  1
   6   62    1  1
""");

    for (int i : range(14))     ok(b.prev(i) == null);
    for (int i : range(14, 20)) ok(b.prev(i), 13);
    for (int i : range(20, 32)) ok(b.prev(i), 19);

    for (int i : range(13))     ok(b.next(i), 13);
    for (int i : range(13, 19)) ok(b.next(i), 19);
    for (int i : range(19, 32)) ok(b.next(i) == null);

    ok(b.first(),   13);
    ok(b.next(1),   13);
    ok(b.next(13),  19);
    ok(b.next(19) == null);

    ok(b.last(),    19);
    ok(b.prev(19),  13);
    ok(b.prev(18),  13);
    ok(b.prev(13) == null);
   }

  static void test_step()                                                       // Test tree of bits
   {final BitSet b = test_bits(23);
    final int[]  n = new int[]{2, 3, 5, 6, 7, 9, 11, 13};

    b.clearAll();
    for (int i : range(n.length)) b.setPath(n[i]);
    ok(b, """
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  0  0  1  1  0  1  1  1  0  1  0  1  0  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0
   3   48    8  1  1  1  1  0  0  0  0
   4   56    4  1  1  0  0
   5   60    2  1  0
   6   62    1  1
""");
    ok(b.first(), 2);  ok(b.last(), 13);
    ok(b.prev( 0) == null);   ok(b.next( 0),  2);
    ok(b.prev( 1) == null);   ok(b.next( 1),  2);
    ok(b.prev( 2) == null);   ok(b.next( 2),  3);
    ok(b.prev( 3), 2);        ok(b.next( 3),  5);
    ok(b.prev( 4), 3);        ok(b.next( 4),  5);
    ok(b.prev( 5), 3);        ok(b.next( 5),  6);
    ok(b.prev( 6), 5);        ok(b.next( 6),  7);
    ok(b.prev( 7), 6);        ok(b.next( 7),  9);
    ok(b.prev( 8), 7);        ok(b.next( 8),  9);
    ok(b.prev( 9), 7);        ok(b.next( 9), 11);
    ok(b.prev(10), 9);        ok(b.next(10), 11);
    ok(b.prev(11), 9);        ok(b.next(11), 13);
    ok(b.prev(12), 11);       ok(b.next(12), 13);
    ok(b.prev(13), 11);       ok(b.next(13) == null);
    ok(b.prev(14), 13);       ok(b.next(14) == null);

    for (int i : range(n.length))
     {b.clearPath(n[i]);
      b.integrity();
     }
   }

  static void test_step_zero()                                                  // Test tree of bits
   {final BitSet b = test_bits(7);

    b.clearAll();
    b.setPath(0); b.setPath(4);
    ok(b, """
BitSet          0  1  2  3  4  5  6  7
   1    0    8  1  0  0  0  1  0  0  0
   2    8    4  1  0  1  0
   3   12    2  1  1
   4   14    1  1
""");
    ok(b.first(), 0);
    ok(b.last(),  4);
    ok(b.prev( 0), null);
    ok(b.prev( 1), 0);
    ok(b.prev( 2), 0);
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

    ok(b.prev(0) == null);
    ok(b.next(0) == null);
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

    ok(b.prev(0) == null, true);
    ok(b.prev(1) == null, true);

    ok(b.next(0), 1);
    ok(b.next(1) == null, true);
   }

  static void test_integrity()
   {final int N = 8;                                                            // Test size.
    final byte[]bytes = new byte[BitSet.bytesNeeded(N)];                        // Allocate backing storage.

    final BitSet b = new BitSet(N)                                              // Create a bit set using the backing storage
     {void setByte(int Index, byte Value) {bytes[Index] = Value;}               // Backend write.
      byte getByte(int Index)      {return bytes[Index];}                       // Backend read.
     };

    b.clearAll();
    b.setPath(1); b.setPath(3);
    ok(b.integrity());
    b.setBit(7, true);
    ok(!b.integrity(false));
   }

  static void test_clearAll()
   {final BitSet b = test_bits(8);

    b.setPath(1); b.setPath(3);
    ok(b.integrity());
    b.clearAll();
    ok(b.integrity());
   }

  static void oldTests()                                                        // Tests thought to be stable.
   {test_bitSet();
    test_bitTree();
    test_one();
    test_two();
    test_step();
    test_step_zero();
    test_integrity();
    test_clearAll();
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
