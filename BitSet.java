//------------------------------------------------------------------------------
// Fixed size bit set which can locate occupied bits in log N time
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//------------------------------------------------------------------------------
// Pregenerate a tree specification so that the same specification can be used to create the backing storage as well as the tree it self
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.util.*;                                                             // Standard utility library.

abstract public class BitSet extends Test                                       // Abstract fixed-size bit set using byte-level storage.
 {final int bitSize;                                                            // Number of bits in the bit set.
  final int byteSize;                                                           // Number of bytes in the bit set.
  final boolean zero, one;                                                      // Able to locate zeros and ones via a tree of bits if set
  static boolean debug;

  public BitSet(int BitSize, boolean One, boolean Zero)                         // Constructor specifying fixed size.
   {bitSize = nextPowerOfTwo(BitSize);                                          // Record size.
    if (bitSize < 0) stop("Size must be zero or positive");                     // Validate size.
    zero = Zero;                                                                // Locate zeroes efficiently
    one = One;                                                                  // Locate ones efficiently
    byteSize = bytesNeeded(BitSize, one, zero);                                 // A tree of bits
   }

  public BitSet(int BitSize)              {this(BitSize, false, false);}        // Constructor to create a bitset without the ability locate zeroes or ones
  public BitSet(int BitSize, boolean One) {this(BitSize, One,   false);}        // Constructor to create a bit set with optionally the ability to locate ones

  abstract void setByte(int Index, byte Value);                                 // Write byte to storage backend.
  abstract byte getByte(int Index);                                             // Read byte from storage backend.

  public static int bytesNeeded(int Size, boolean One, boolean Zero)            // Number of bytes needed for a bit set of specified size with or without the ability to locate zeroes and ones
   {int s = 1; if (Zero) s++; if (One) s++;                                     // The number of blocks of bits required.  Need the base layer plus blocks for trees of bits to locate ones and/or zeroes
    return (Byte.SIZE - 1 + s * nextPowerOfTwo(Size)) / Byte.SIZE;
   }

  public static int bytesNeeded(int Size)                                       // Number of bytes needed for a bit set of specified size without the ability to locate zeroes or ones
   {return bytesNeeded(Size, false, false);
   }

  public static int bytesNeeded(int Size, boolean One)                          // Number of bytes needed for a bit set of specified size with the ability to locate ones if specified.
   {return bytesNeeded(Size, One,   false);
   }

  public  int size()                  {return bitSize;}                         // Bit set size.
  private int bitOffset(int bitIndex) {return bitIndex & 7;}                    // Offset inside byte.
  private int byteIndex(int bitIndex) {return bitIndex >>> 3;}                  // Byte index in storage.

  class Pos                                                                     // A position of a bit in the bit set
   {final int position;                                                         // Position of the bit
    int position() {return position;}
    public Pos(int Position)                                                    // Construct a bit position
     {position = Position;
     }
    public String toString() {return ""+position();}                            // Print a bit position
   }

  private void checkIndex(int Index)                                            // Check that a bit index is valid
   {if (Index < 0) stop("BitSet index cannot be negative:", Index);
    if (Index >= bitSize)
     {stop("BitSet index cannot be greater than or equal to:", Index, bitSize);
     }
   }

  private void checkOne()                                                       // Check that we can search for ones in this bit set
   {if (!one)
     {stop("This bitset does have the ability to search for ones");
     }
   }

  private void checkZero()                                                      // Check that we can search for zeroes in this bit set
   {if (!zero)
     {stop("This bitset does not have the ability to search for zeroes");
     }
   }

  public boolean getBit(Pos Index)                                              // Get bit value at an index after checking that the index is valid
   {checkIndex(Index.position());
    return getBitNC(Index);
   }

  private boolean getBitNC(Pos Index)                                           // Get bit value at an index without checking that the index is valid
   {final int bIndex = byteIndex(Index.position());                             // Compute byte position.
    final int offset = bitOffset(Index.position());                             // Compute bit offset.

    final byte b = getByte(bIndex);                                             // Load byte.
    return ((b >>> offset) & 1) != 0;                                           // Extract bit.
   }

  void setBit(Pos Index, boolean Value)                                         // Set bit value.
   {checkIndex(Index.position());
    setBitNC(Index, Value);                                                     // Set bit value without index checks
   }

  private void setBitNC(Pos Index, boolean Value)                               // Set bit value without checking index
   {final int bIndex = byteIndex(Index.position());                             // Compute byte position.
    final int offset = bitOffset(Index.position());                             // Compute bit offset.
    final byte b = getByte(bIndex);                                             // Load byte.
    setByte(bIndex, (byte) (Value ? b | (1 << offset) : b & ~(1 << offset)));   // Modify bit.
   }

  public void clear(Pos Index) {set(Index, false);}                             // Clear bit and corresponding path bits from the indexed bit to the root of the bit tree
  public void set  (Pos Index) {set(Index, true );}                             // Set bit and corresponding path bits from the indexed bit to the root of the bit tree

  public void set  (Pos Index, boolean Value)                                   // Set or clear bits along the path from the indexed bit to the root of the bit tree
   {if (!one && !zero) stop("Cannot use path unless One or Zero paths chosen");
    if (getBit(Index) == Value) return;                                         // Already set to the correct value so nothing changes
    setBitNC(Index, Value);                                                     // Set the bit
    if (bitSize < 2) return;                                                    // The One and Zero trees would have no entries
    if (one)  {if (Value) setOnePath (Index); else clearOnePath (Index);}
    if (zero) {if (Value) setZeroPath(Index); else clearZeroPath(Index);}
   }

  private void setOnePath(Pos Index)                                            // Set bits along the path from the indexed bit to the root of the bit tree
   {checkOne();
    final int x = Index.position();
    for(int b = x, p = 0, w = bitSize; w > 0; p += w, w >>>= 1, b >>>= 1)       // Step from root to leaf
     {if (p == 0) continue;                                                     // Skip the actual bits
      final Pos q = new Pos(p+b);
      if (getBitNC(q)) return; else setBitNC(q, true);                          // Stop creating the path once we have arrived at a tree bit that is correctly set: as there are no changes at this level the upper levels must be ok too
     }
   }

  private void clearOnePath(Pos Index)                                          // Clear bits along the path from the indexed bit to the root of the bit tree
   {checkOne();
    for(int i = 0, b = Index.position(), p = 0, w = bitSize; w > 0; ++i)        // Step from leaf to root
     {final int B = b>>>1, q = p + w + B;

      if (B+B+1 < w && !getBitNC(new Pos(p+B+B)) && !getBitNC(new Pos(p+B+B+1)))// Check both bits in the previous row are off
       {if (!getBitNC(new Pos(q))) return;                                      // Bit is already correctly set so there is nothing more to do
        setBitNC(new Pos(q), false);                                            // Clear set bit along path to root
       }
      p += w; w >>>= 1; b >>>= 1;                                               // Next layer
     }
   }

  private int addressZeroTree()                                                 // The zero tree will be held directly after the actual bits if there is no one tree, else beyond the one tree
   {int p = bitSize; if (one) p += bitSize-1;                                   // Address first bit of zero bit tree
    return p;
   }

  private void clearZeroPath(Pos Index)                                         // Clear the target bit and set bits along the path from the indexed bit to the root of the bit tree
   {checkZero();
    int p = addressZeroTree();                                                  // Address zero bit tree
    for(int b = Index.position()>>>1, w = bitSize>>>1; w > 0; p += w, w >>>= 1, b >>>= 1)      // Step from root to leaf
     {final Pos q = new Pos(p+b);
      if (getBitNC(q)) return; else setBitNC(q, true);                          // Stop creating the path once we have arrived at a tree bit that is correctly set: as there are no changes at this level the upper levels must be ok too
     }
   }

  private void setZeroPath(Pos Index)                                           // Set bits along the path from the indexed bit to the root of the bit tree unlkess thre is another path running through each bit
   {int P = 0;                                                                  // Position in the parent layer
    int W = bitSize;                                                            // Width of parent layer
    int p = addressZeroTree();                                                  // First child layer is the first layer of the zero bit tree
    int w = W >>> 1;                                                            // Width of child layer
    int b = Index.position() >>> 1;                                             // Index of bit in child layer

    if (!getBitNC(new Pos(P+b+b)) || !getBitNC(new Pos(P+b+b+1))) return;       // Check there is a zero
    if (!getBitNC(new Pos(p+b))) return;                                        // Bit is already correctly set to show no path so there is nothing more to do
         setBitNC(new Pos(p+b),  false);                                        // Clear set bit along path to root to show no path

    for(int i = 0; w > 0; ++i)                                                  // Step from first row of tree to root
     {P    = p;                                                                 // Child layer becomes parent layer
      W    = w;                                                                 // Width of parent layer
      p   += w;                                                                 // New child layer
      w >>>= 1;                                                                 // Child layer width
      b    = b >>> 1;                                                           // Index of bit in child layer
      if ( getBitNC(new Pos(P+b+b)) || getBitNC(new Pos(P+b+b+1))) return;      // There is a one n the upper row so we do not need to clear further down
      if (!getBitNC(new Pos(p+b))) return;                                      // Bit is already correctly set so there is nothing more to do
           setBitNC(new Pos(p+b),  false);                                      // Clear set bit along path to root
     }
   }

  public Pos firstOne()                                                         // Find the index of the first set bit
   {return getBit(new Pos(0)) ? new Pos(0):nextOne(new Pos(0));
   }

  public Pos lastOne()                                                          // Find the index of the last set bit
   {final int l = bitSize-1;
    return getBit(new Pos(l)) ? new Pos(l) : prevOne(new Pos(l));
   }

  public Pos nextOne(Pos Index)                                                 // Find the index of the next set bit above the specified bit
   {checkIndex(Index.position());
    int b = Index.position(), p = 0, w = nextPowerOfTwo(bitSize);
    for(int i : range(bitSize))                                                 // Much more than necessary
     {int B = b+1;                                                              // Is there a path down from the next bit?
      if (B < w && getBitNC(new Pos(p+B)))                                      // Found next up bit
       {for(int j : range(i))                                                   // Step down to the leaves
         {w <<= 1;                                                              // Width of next level
          B   = 2 * B + (getBitNC(new Pos(p-w+B+B)) ? 0 : 1);                   // Follow path as low as possible
          p  -= w;                                                              // Position of next level in tree
         }
        return new Pos(B);
       }
      p += w;                                                                   // Address next level of bits in tree
      w >>>= 1; if (w == 0) break;                                              // If we have reached level 0 we are finished
      b >>>= 1;                                                                 // Index in next level
     }
    return null;
   }

  public Pos prevOne(Pos Index)                                                 // Find the index of the previous set bit below the specified bit
   {checkIndex(Index.position());
    int b = Index.position(), p = 0, w = nextPowerOfTwo(bitSize);

    for(int i : range(bitSize))                                                 // Much more than necessary
     {int B = b-1;                                                              // Is there a path down from the next bit?
      if (b > 0 && getBitNC(new Pos(p+B)))                                      // Found next down bit
       {for(int j : range(i))                                                   // Step down to the leaves
         {w <<= 1;                                                              // Width of next level
          B   = 2 * B + (getBitNC(new Pos(p-w+B+B+1)) ? 1 : 0);                 // Follow path as high as possible
          p  -= w;                                                              // Position of next level in tree
         }
        return new Pos(B);
       }
      p += w;                                                                   // Address next level of bits in tree
      w >>>= 1; if (w == 0) break;                                              // If we have reached level 0 we are finished
      b >>>= 1;                                                                 // Index in next level
     }
    return null;
   }

  public void clearAll()                                                        // Clear all bits.
   {for (int i : range(byteSize)) setByte(i, (byte)0);
    if (zero)                                                                   // Set all the bits to one in the paths in the zero tree if present to show that all the actual bits are zero
     {for (int p = addressZeroTree(), j = 0; j < bitSize; j++)
       {setBitNC(new Pos(p+j), true);
       }
     }
   }

  public boolean integrity() {return integrity(true);}                          // Do an integrity check on the bitset to detect corruption

  public boolean integrity(boolean Stop)                                        // Do an integrity check on the bitset to detect corruption and stop on failures unless specified otherwise
   {final byte[]bytes = new byte[BitSet.bytesNeeded(bitSize, one, zero)];       // Allocate backing storage.

    final BitSet b = new BitSet(bitSize, one, zero)                             // Create an identical bitset
     {void setByte(int Index, byte Value) {bytes[Index] = Value;}               // Backend write.
      byte getByte(int Index)      {return bytes[Index];}                       // Backend read.
     };

    b.clearAll();
    for (int i : range(bitSize)) b.set(new Pos(i), getBit(new Pos(i)));         // Load bit set

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

    s.append("BitSet          ");                                               // Title
    for   (int i : range(bitSize)) s.append(f(" %2d", i));                      // Positions of bits
    s.append("\n");

    for   (int i : range(1, bitSize))                                           // Print the first line and the first bit tree if present
     {s.append(f("%4d %4d %4d |", i, p, r));
      for (int j : range(r))                                                    // Bits in level
       {s.append(f("  %1d", getBitNC(new Pos(p + j)) ? 1 : 0));
        if (!one && !zero) break;                                               // Only print the first line if there are no tree bits
       }
      s.append("\n");
      if (i == 1)                                                               // The first line is the actual bits
       {if      (one)  s.append("One:\n");                                      // One tree present so it comes next
        else if (zero) s.append("Zero:\n");                                     // Zero tree present and no One tree present so the zero tree comes next
       }
      p += r;
      r >>>= 1;
      if (r == 0) break;                                                        // Reached the leaves
     }

    if (one && zero)                                                            // Print zero search tree block if both one and zero bit trees are present
     {r = bitSize>>>1;
      s.append("Zero:\n");
      for   (int i : range(1, bitSize))                                         // Each level
       {s.append(f("%4d %4d %4d |", i, p, r));
        for (int j : range(r))                                                  // Bits in level
         {s.append(f("  %1d", getBitNC(new Pos(p + j)) ? 1 : 0));
          if (!one && !zero) break;                                             // Only print the first line if there are no tree bits
         }
        s.append("\n");
        p += r;
        r >>>= 1;
        if (r == 0) break;                                                      // Reached the leaves
       }
     }
    return ""+s;
   }

//D1 Tests                                                                      // Tests

  static BitSet test_bits(int N, boolean One, boolean Zero)                     // Create test bitset.
   {final byte[]bytes = new byte[BitSet.bytesNeeded(N, One, Zero)];             // Allocate backing storage.

    final BitSet b = new BitSet(N, One, Zero)                                   // Create a bit set
     {void setByte(int Index, byte Value) {bytes[Index] = Value;}               // Backend write.
      byte getByte(int Index)      {return bytes[Index];}                       // Backend read.
     };
    return b;                                                                   // Return test bitset.
   }

  static void test_bitSet()                                                     // Test bit manipulation.
   {final BitSet b = test_bits(23, true, false);                                // Get test bitset.
    final int N = b.size();                                                     // Get logical size.

    for (int i = 0; i < N; i++) b.setBit(b.new Pos(i), i % 2 == 0);             // Set alternating bits.

    ok(b.getBit(b.new Pos(4)), true);                                           // Verify bit 4.
    ok(b.getBit(b.new Pos(5)), false);                                          // Verify bit 5.
   }

  static void test_prevNext()                                                   // Test tree of searchable one bits
   {final BitSet b = test_bits(32, true, true);
    b.clearAll();
    final int[]s = new int[]{13, 19, 24, 25, 26, 27, 28, 30, 31};

    for (int i : s) b.set(b.new Pos(i), true);

    //stop(b);
    ok(b, """
BitSet            0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32 |  0  0  0  0  0  0  0  0  0  0  0  0  0  1  0  0  0  0  0  1  0  0  0  0  1  1  1  1  1  0  1  1
One:
   2   32   16 |  0  0  0  0  0  0  1  0  0  1  0  0  1  1  1  1
   3   48    8 |  0  0  0  1  1  0  1  1
   4   56    4 |  0  1  1  1
   5   60    2 |  1  1
   6   62    1 |  1
Zero:
   1   63   16 |  1  1  1  1  1  1  1  1  1  1  1  1  0  0  1  0
   2   79    8 |  1  1  1  1  1  1  0  1
   3   87    4 |  1  1  1  1
   4   91    2 |  1  1
   5   93    1 |  1
""");
    for (int i : range(14))     ok(b.prevOne(b.new Pos(i)) == null);
    for (int i : range(14, 20)) ok(b.prevOne(b.new Pos(i)).position(), 13);
    for (int i : range(20, 24)) ok(b.prevOne(b.new Pos(i)).position(), 19);
    for (int i : range(25, 29)) ok(b.prevOne(b.new Pos(i)).position(), i-1);
    ok(b.prevOne(b.new Pos(30)).position(), 28);
    ok(b.prevOne(b.new Pos(31)).position(), 30);

    for (int i : range(13))     ok(b.nextOne(b.new Pos(i)).position(), 13);
    for (int i : range(13, 19)) ok(b.nextOne(b.new Pos(i)).position(), 19);
    for (int i : range(19, 24)) ok(b.nextOne(b.new Pos(i)).position(), 24);
    for (int i : range(23, 28)) ok(b.nextOne(b.new Pos(i)).position(), i+1);
    ok(b.nextOne(b.new Pos(28)).position(), 30);
    for (int i : range(29, 31)) ok(b.nextOne(b.new Pos(i)).position(), i+1);
    ok(b.nextOne(b.new Pos(31)) == null);

    ok(b.firstOne().position(),   13);
    ok(b.lastOne().position(),    31);
   }

  static void test_integrity()
   {final int N = 8;                                                            // Test size.
    final byte[]bytes = new byte[BitSet.bytesNeeded(N, true)];                  // Allocate backing storage.

    final BitSet b = new BitSet(N, true)                                        // Create a bit set using the backing storage
     {void setByte(int Index, byte Value) {bytes[Index] = Value;}               // Backend write.
      byte getByte(int Index)      {return bytes[Index];}                       // Backend read.
     };

    b.clearAll();
    b.set(b.new Pos(1), true); b.set(b.new Pos(3), true);
    ok(b.integrity());
    b.setBit(b.new Pos(7), true);
    ok(!b.integrity(false));
   }

  static void test_clearAll()
   {final BitSet b = test_bits(8, true, false);

    b.set(b.new Pos(1), true); b.set(b.new Pos(3), true);
    ok(b.integrity());
    b.clearAll();
    ok(b.integrity());
   }

  static void test_oneZero()
   {final int N = 8;
    final BitSet b = test_bits(N, true, true);
    b.clearAll();
    final StringBuilder s = new StringBuilder();
    s.append("Start:\n"+b);

    for (int i : range(N))
     {b.set(b.new Pos(i), true);
      s.append("Set: "+i+"\n"+b);
     }
    //stop(s);
    ok(s, """
Start:
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  0  0  0  0  0  0  0  0
One:
   2    8    4 |  0  0  0  0
   3   12    2 |  0  0
   4   14    1 |  0
Zero:
   1   15    4 |  1  1  1  1
   2   19    2 |  1  1
   3   21    1 |  1
Set: 0
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  1  0  0  0  0  0  0  0
One:
   2    8    4 |  1  0  0  0
   3   12    2 |  1  0
   4   14    1 |  1
Zero:
   1   15    4 |  1  1  1  1
   2   19    2 |  1  1
   3   21    1 |  1
Set: 1
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  1  1  0  0  0  0  0  0
One:
   2    8    4 |  1  0  0  0
   3   12    2 |  1  0
   4   14    1 |  1
Zero:
   1   15    4 |  0  1  1  1
   2   19    2 |  1  1
   3   21    1 |  1
Set: 2
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  1  1  1  0  0  0  0  0
One:
   2    8    4 |  1  1  0  0
   3   12    2 |  1  0
   4   14    1 |  1
Zero:
   1   15    4 |  0  1  1  1
   2   19    2 |  1  1
   3   21    1 |  1
Set: 3
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  1  1  1  1  0  0  0  0
One:
   2    8    4 |  1  1  0  0
   3   12    2 |  1  0
   4   14    1 |  1
Zero:
   1   15    4 |  0  0  1  1
   2   19    2 |  0  1
   3   21    1 |  1
Set: 4
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  1  1  1  1  1  0  0  0
One:
   2    8    4 |  1  1  1  0
   3   12    2 |  1  1
   4   14    1 |  1
Zero:
   1   15    4 |  0  0  1  1
   2   19    2 |  0  1
   3   21    1 |  1
Set: 5
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  1  1  1  1  1  1  0  0
One:
   2    8    4 |  1  1  1  0
   3   12    2 |  1  1
   4   14    1 |  1
Zero:
   1   15    4 |  0  0  0  1
   2   19    2 |  0  1
   3   21    1 |  1
Set: 6
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  1  1  1  1  1  1  1  0
One:
   2    8    4 |  1  1  1  1
   3   12    2 |  1  1
   4   14    1 |  1
Zero:
   1   15    4 |  0  0  0  1
   2   19    2 |  0  1
   3   21    1 |  1
Set: 7
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  1  1  1  1  1  1  1  1
One:
   2    8    4 |  1  1  1  1
   3   12    2 |  1  1
   4   14    1 |  1
Zero:
   1   15    4 |  0  0  0  0
   2   19    2 |  0  0
   3   21    1 |  0
""");
   }

  static void oldTests()                                                        // Tests thought to be stable.
   {test_bitSet();
    test_prevNext();
    test_integrity();
    test_clearAll();
    test_oneZero();
   }

  static void newTests()                                                        // Tests under development.
   {oldTests();                                                                 // Run baseline tests.
    test_oneZero();
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
