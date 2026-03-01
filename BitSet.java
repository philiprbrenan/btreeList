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

  @SuppressWarnings("this-escape")
  public BitSet(int BitSize, boolean One, boolean Zero)                         // Constructor specifying fixed size.
   {bitSize = nextPowerOfTwo(BitSize);                                          // Record size.
    if (bitSize < 0) stop("Size must be zero or positive");                     // Validate size.
    zero = Zero;                                                                // Locate zeroes efficiently
    one = One;                                                                  // Locate ones efficiently
    byteSize = bytesNeeded(BitSize, one, zero);                                 // A tree of bits
    clearAll();                                                                 // Clear backing storage
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

  public void path(Pos Index, boolean Value)                                    // Set or clear bits along the path from the indexed bit to the root of the bit tree
   {if (!one && !zero) stop("Cannot use path unless One or Zero paths chosen");
    if (one)  {if (Value) setOnePath (Index); else clearOnePath (Index);}
    if (zero) {if (Value) setZeroPath(Index); else clearZeroPath(Index);}
   }

  private void setOnePath(Pos Index)                                            // Set bits along the path from the indexed bit to the root of the bit tree
   {checkOne();
    final int x = Index.position();
    checkIndex(x);
    for(int b = x, p = 0, w = bitSize; w > 0; p += w, w >>>= 1, b >>>= 1)       // Step from root to leaf
     {final Pos q = new Pos(p+b);
      if (getBitNC(q)) return; else setBitNC(q, true);                          // Stop creating the path once we have arrived at a tree bit that is correctly set: as there are no changes at this level the upper levels must be ok too
     }
   }

  private void clearOnePath(Pos Index)                                          // Clear bits along the path from the indexed bit to the root of the bit tree
   {checkOne();
    final int x = Index.position();
    checkIndex(x);
    if (!getBit(Index)) return;                                                 // Bit already not set so the rest of the path will be correct as no changes at this level

    setBitNC(Index, false);                                                     // Clear the actual bit
    for(int i = 0, b = x, p = 0, w = bitSize; w > 0; ++i)                       // Step from leaf to root
     {final int B = b>>>1, q = p + w + B;

      if (B+B+1 < w && !getBitNC(new Pos(p+B+B)) && !getBitNC(new Pos(p+B+B+1)))// Check both bits in the previous row are off
       {if (!getBitNC(new Pos(q))) return;                                      // Bit is already correctly set so there is nothing more to do
        setBitNC(new Pos(q), false);                                            // Clear set bit along path to root
       }
      p += w; w >>>= 1; b >>>= 1;                                               // Next layer
     }
   }

  private int addressZeroTree()                                                 // The zeeo tree will be held directly after the actual bits if there is no one tree, else beyond the one tree
   {int p = bitSize; if (one) p += bitSize;                                     // Address last bit tree if needed
    return p;
   }

  private void clearZeroPath(Pos Index)                                         // Clear the target bit and set bits along the path from the indexed bit to the root of the bit tree
   {checkZero();
    final int x = Index.position();
    checkIndex(x);
    if (!getBitNC(Index)) return; else setBitNC(Index, false);                  // Stop creating the path once we have arrived at a tree bit that is correctly set: as there are no changes at this level the upper levels must be ok too
    int p = addressZeroTree();                                                  // Address zero bit tree
    for(int b = x>>>1, w = bitSize>>>1; w > 0; p += w, w >>>= 1, b >>>= 1)      // Step from root to leaf
     {final Pos q = new Pos(p+b);
      if (getBitNC(q)) return; else setBitNC(q, true);                          // Stop creating the path once we have arrived at a tree bit that is correctly set: as there are no changes at this level the upper levels must be ok too
     }
   }

  private void setZeroPath(Pos Index)                                           // Set bits along the path from the indexed bit to the root of the bit tree unlkess thre is another path running through each bit
   {checkZero();
    final int x = Index.position();
    checkIndex(x);
    if (getBit(Index)) return;                                                  // Bit already set so the rest of the path will be correct as no changes were made at this level
    if (bitSize < 2) return;                                                    // No bit tree for such a small bit set

    setBitNC(Index, true);                                                      // Set the actual bit
    int P = 0;
    int W = bitSize;
    int p = addressZeroTree();                                                  // Address zero bit tree
    int w = W >>> 1;
    int b = x >>> 1;
    final boolean BothOn = getBitNC(new Pos(P+b+b)) && getBitNC(new Pos(P+b+b+1));         // Check there is a zero
    if (!BothOn) return;                                                        // There is a zero so no need to clear the path that already indiocates that there is a zero there
    if (!getBitNC(new Pos(p+b))) return;                                        // Bit is already correctly set to show no path so there is nothing more to do
         setBitNC(new Pos(p+b), false);                                              // Clear set bit along path to root to show no path

    for(int i = 0; w > 0; ++i)                                                  // Step from leaf to root
     {P  = p;
      W  = w;
      p += w;
      w >>>= 1;
      b  = b >>> 1;
      final boolean bothOff = !getBitNC(new Pos(P+b+b)) && !getBitNC(new Pos(P+b+b+1)); // Check there is a zero
    if (!bothOff) return;                                                       // There is a zero so no need to clear the path that already indiocates that there is a zero there
    if (!getBitNC(new Pos(p+b))) return;                                       // Bit is already correctly set so there is nothing more to do
         setBitNC(new Pos(p+b), false);                                      // Clear set bit along path to root
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
     {for (int p = addressZeroTree(), i = 0; i < bitSize; i++)
       {setBitNC(new Pos(p+i), true);
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

    for   (int i : range(bitSize))
     {if (getBit(new Pos(i))) b.setOnePath(new Pos(i));
     }

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

    for   (int i : range(1, bitSize))                                           // Print the first line and the first bit tree of present
     {s.append(f("%4d %4d %4d", i, p, r));
      for (int j : range(r))                                                    // Bits in level
       {s.append(f("  %1d", getBitNC(new Pos(p + j)) ? 1 : 0));
        if (!one && !zero) break;                                               // Only print the first line if there are no tree bits
       }
      s.append("\n");
      p += r;
      r >>>= 1;
      if (r == 0) break;                                                        // Reached the leaves
     }

    if (one && zero)                                                            // Print zero search tree block if both one and zero bit trees are present
     {r = bitSize;
      for   (int i : range(1, bitSize))                                         // Each level
       {s.append(f("%4d %4d %4d", i, p, r));
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

  static void test_oneBitTree()                                                 // Test tree of searchable one bits
   {final BitSet b = test_bits(23, true, false);
    b.clearAll();
    b.setOnePath(b.new Pos(13));
    ok(b, """
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  0  0  0  0  0  0  0  0  0  0  0  0  0  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  1  0  0  0  0  0  0  0  0  0
   3   48    8  0  0  0  1  0  0  0  0
   4   56    4  0  1  0  0
   5   60    2  1  0
   6   62    1  1
""");
    b.setOnePath(b.new Pos(19));
    ok(b, """
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  0  0  0  0  0  0  0  0  0  0  0  0  0  1  0  0  0  0  0  1  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  1  0  0  1  0  0  0  0  0  0
   3   48    8  0  0  0  1  1  0  0  0
   4   56    4  0  1  1  0
   5   60    2  1  1
   6   62    1  1
""");
    b.setOnePath(b.new Pos(16));
    ok(b, """
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  0  0  0  0  0  0  0  0  0  0  0  0  0  1  0  0  1  0  0  1  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  1  0  1  1  0  0  0  0  0  0
   3   48    8  0  0  0  1  1  0  0  0
   4   56    4  0  1  1  0
   5   60    2  1  1
   6   62    1  1
""");
    b.clearOnePath(b.new Pos(16));
    ok(b, """
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  0  0  0  0  0  0  0  0  0  0  0  0  0  1  0  0  0  0  0  1  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  1  0  0  1  0  0  0  0  0  0
   3   48    8  0  0  0  1  1  0  0  0
   4   56    4  0  1  1  0
   5   60    2  1  1
   6   62    1  1
""");

    for (int i : range(14))     ok(b.prevOne(b.new Pos(i)) == null);
    for (int i : range(14, 20)) ok(b.prevOne(b.new Pos(i)).position(), 13);
    for (int i : range(20, 32)) ok(b.prevOne(b.new Pos(i)).position(), 19);

    for (int i : range(13))     ok(b.nextOne(b.new Pos(i)).position(), 13);
    for (int i : range(13, 19)) ok(b.nextOne(b.new Pos(i)).position(), 19);
    for (int i : range(19, 32)) ok(b.nextOne(b.new Pos(i)) == null);

    ok(b.firstOne().position(),   13);
    ok(b.nextOne(b.new Pos( 1)).position(),   13);
    ok(b.nextOne(b.new Pos(13)).position(),  19);
    ok(b.nextOne(b.new Pos(19)) == null);

    ok(b.lastOne().position(),   19);
    ok(b.prevOne(b.new Pos(19)).position(),  13);
    ok(b.prevOne(b.new Pos(18)).position(),  13);
    ok(b.prevOne(b.new Pos(13)) == null);
   }

  static void test_allOneBitTree()                                                 // Test tree of searchable zero bits
   {final BitSet b = test_bits(14, true, false);
    final StringBuilder s = new StringBuilder();
    b.clearAll();
    s.append("Start:\n"+b);

    for (int i : range(b.bitSize))
     {b.setOnePath(b.new Pos(i));
      s.append("At: "+i+"\n"+b);
     }
    //stop(s);
    ok(s, """
Start:
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   16    8  0  0  0  0  0  0  0  0
   3   24    4  0  0  0  0
   4   28    2  0  0
   5   30    1  0
At: 0
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   16    8  1  0  0  0  0  0  0  0
   3   24    4  1  0  0  0
   4   28    2  1  0
   5   30    1  1
At: 1
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  1  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   16    8  1  0  0  0  0  0  0  0
   3   24    4  1  0  0  0
   4   28    2  1  0
   5   30    1  1
At: 2
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  1  1  1  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   16    8  1  1  0  0  0  0  0  0
   3   24    4  1  0  0  0
   4   28    2  1  0
   5   30    1  1
At: 3
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  1  1  1  1  0  0  0  0  0  0  0  0  0  0  0  0
   2   16    8  1  1  0  0  0  0  0  0
   3   24    4  1  0  0  0
   4   28    2  1  0
   5   30    1  1
At: 4
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  1  1  1  1  1  0  0  0  0  0  0  0  0  0  0  0
   2   16    8  1  1  1  0  0  0  0  0
   3   24    4  1  1  0  0
   4   28    2  1  0
   5   30    1  1
At: 5
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0  0
   2   16    8  1  1  1  0  0  0  0  0
   3   24    4  1  1  0  0
   4   28    2  1  0
   5   30    1  1
At: 6
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  1  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0
   2   16    8  1  1  1  1  0  0  0  0
   3   24    4  1  1  0  0
   4   28    2  1  0
   5   30    1  1
At: 7
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  1  1  1  1  1  1  1  1  0  0  0  0  0  0  0  0
   2   16    8  1  1  1  1  0  0  0  0
   3   24    4  1  1  0  0
   4   28    2  1  0
   5   30    1  1
At: 8
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  1  1  1  1  1  1  1  1  1  0  0  0  0  0  0  0
   2   16    8  1  1  1  1  1  0  0  0
   3   24    4  1  1  1  0
   4   28    2  1  1
   5   30    1  1
At: 9
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  1  1  1  1  1  1  1  1  1  1  0  0  0  0  0  0
   2   16    8  1  1  1  1  1  0  0  0
   3   24    4  1  1  1  0
   4   28    2  1  1
   5   30    1  1
At: 10
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  1  1  1  1  1  1  1  1  1  1  1  0  0  0  0  0
   2   16    8  1  1  1  1  1  1  0  0
   3   24    4  1  1  1  0
   4   28    2  1  1
   5   30    1  1
At: 11
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  1  1  1  1  1  1  1  1  1  1  1  1  0  0  0  0
   2   16    8  1  1  1  1  1  1  0  0
   3   24    4  1  1  1  0
   4   28    2  1  1
   5   30    1  1
At: 12
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  1  1  1  1  1  1  1  1  1  1  1  1  1  0  0  0
   2   16    8  1  1  1  1  1  1  1  0
   3   24    4  1  1  1  1
   4   28    2  1  1
   5   30    1  1
At: 13
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  1  1  1  1  1  1  1  1  1  1  1  1  1  1  0  0
   2   16    8  1  1  1  1  1  1  1  0
   3   24    4  1  1  1  1
   4   28    2  1  1
   5   30    1  1
At: 14
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  0
   2   16    8  1  1  1  1  1  1  1  1
   3   24    4  1  1  1  1
   4   28    2  1  1
   5   30    1  1
At: 15
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1
   2   16    8  1  1  1  1  1  1  1  1
   3   24    4  1  1  1  1
   4   28    2  1  1
   5   30    1  1
""");

    final StringBuilder S = new StringBuilder();                                    // a
    for (int i : range(b.bitSize))
     {b.clearOnePath(b.new Pos(i));
      S.append("Clear: "+i+"\n"+b);
     }
    //stop(S);
    ok(S, """
Clear: 0
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  0  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1
   2   16    8  1  1  1  1  1  1  1  1
   3   24    4  1  1  1  1
   4   28    2  1  1
   5   30    1  1
Clear: 1
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  0  0  1  1  1  1  1  1  1  1  1  1  1  1  1  1
   2   16    8  0  1  1  1  1  1  1  1
   3   24    4  1  1  1  1
   4   28    2  1  1
   5   30    1  1
Clear: 2
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  0  0  0  1  1  1  1  1  1  1  1  1  1  1  1  1
   2   16    8  0  1  1  1  1  1  1  1
   3   24    4  1  1  1  1
   4   28    2  1  1
   5   30    1  1
Clear: 3
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  0  0  0  0  1  1  1  1  1  1  1  1  1  1  1  1
   2   16    8  0  0  1  1  1  1  1  1
   3   24    4  0  1  1  1
   4   28    2  1  1
   5   30    1  1
Clear: 4
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  0  0  0  0  0  1  1  1  1  1  1  1  1  1  1  1
   2   16    8  0  0  1  1  1  1  1  1
   3   24    4  0  1  1  1
   4   28    2  1  1
   5   30    1  1
Clear: 5
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  0  0  0  0  0  0  1  1  1  1  1  1  1  1  1  1
   2   16    8  0  0  0  1  1  1  1  1
   3   24    4  0  1  1  1
   4   28    2  1  1
   5   30    1  1
Clear: 6
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  0  0  0  0  0  0  0  1  1  1  1  1  1  1  1  1
   2   16    8  0  0  0  1  1  1  1  1
   3   24    4  0  1  1  1
   4   28    2  1  1
   5   30    1  1
Clear: 7
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  0  0  0  0  0  0  0  0  1  1  1  1  1  1  1  1
   2   16    8  0  0  0  0  1  1  1  1
   3   24    4  0  0  1  1
   4   28    2  0  1
   5   30    1  1
Clear: 8
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  0  0  0  0  0  0  0  0  0  1  1  1  1  1  1  1
   2   16    8  0  0  0  0  1  1  1  1
   3   24    4  0  0  1  1
   4   28    2  0  1
   5   30    1  1
Clear: 9
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  0  0  0  0  0  0  0  0  0  0  1  1  1  1  1  1
   2   16    8  0  0  0  0  0  1  1  1
   3   24    4  0  0  1  1
   4   28    2  0  1
   5   30    1  1
Clear: 10
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  0  0  0  0  0  0  0  0  0  0  0  1  1  1  1  1
   2   16    8  0  0  0  0  0  1  1  1
   3   24    4  0  0  1  1
   4   28    2  0  1
   5   30    1  1
Clear: 11
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  0  0  0  0  0  0  0  0  0  0  0  0  1  1  1  1
   2   16    8  0  0  0  0  0  0  1  1
   3   24    4  0  0  0  1
   4   28    2  0  1
   5   30    1  1
Clear: 12
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  0  0  0  0  0  0  0  0  0  0  0  0  0  1  1  1
   2   16    8  0  0  0  0  0  0  1  1
   3   24    4  0  0  0  1
   4   28    2  0  1
   5   30    1  1
Clear: 13
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  0  0  0  0  0  0  0  0  0  0  0  0  0  0  1  1
   2   16    8  0  0  0  0  0  0  0  1
   3   24    4  0  0  0  1
   4   28    2  0  1
   5   30    1  1
Clear: 14
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  1
   2   16    8  0  0  0  0  0  0  0  1
   3   24    4  0  0  0  1
   4   28    2  0  1
   5   30    1  1
Clear: 15
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   16    8  0  0  0  0  0  0  0  0
   3   24    4  0  0  0  0
   4   28    2  0  0
   5   30    1  0
""");
   }

  static void test_allZeroBitTree()                                                // Test tree of searchable zero bits
   {final BitSet b = test_bits(23, false, true);
    final StringBuilder s = new StringBuilder();
    b.clearAll();
    s.append("Start:\n"+b);

    for (int i : range(b.bitSize))
     {b.setZeroPath(b.new Pos(i));
      s.append("At: "+i+"\n"+b);
     }
    //stop(s);
    ok(s, """
Start:
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1
   3   48    8  1  1  1  1  1  1  1  1
   4   56    4  1  1  1  1
   5   60    2  1  1
   6   62    1  1
At: 0
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1
   3   48    8  1  1  1  1  1  1  1  1
   4   56    4  1  1  1  1
   5   60    2  1  1
   6   62    1  1
At: 1
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1
   3   48    8  1  1  1  1  1  1  1  1
   4   56    4  1  1  1  1
   5   60    2  1  1
   6   62    1  1
At: 2
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1
   3   48    8  1  1  1  1  1  1  1  1
   4   56    4  1  1  1  1
   5   60    2  1  1
   6   62    1  1
At: 3
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  1  1  1  1  1  1  1  1  1  1  1  1  1  1
   3   48    8  0  1  1  1  1  1  1  1
   4   56    4  1  1  1  1
   5   60    2  1  1
   6   62    1  1
At: 4
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  1  1  1  1  1  1  1  1  1  1  1  1  1  1
   3   48    8  0  1  1  1  1  1  1  1
   4   56    4  1  1  1  1
   5   60    2  1  1
   6   62    1  1
At: 5
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  1  1  1  1  1  1  1  1  1  1  1  1  1
   3   48    8  0  1  1  1  1  1  1  1
   4   56    4  1  1  1  1
   5   60    2  1  1
   6   62    1  1
At: 6
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  1  1  1  1  1  1  1  1  1  1  1  1  1
   3   48    8  0  1  1  1  1  1  1  1
   4   56    4  1  1  1  1
   5   60    2  1  1
   6   62    1  1
At: 7
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  1  1  1  1  1  1  1  1  1  1  1  1
   3   48    8  0  0  1  1  1  1  1  1
   4   56    4  0  1  1  1
   5   60    2  1  1
   6   62    1  1
At: 8
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  1  1  1  1  1  1  1  1  1  1  1  1
   3   48    8  0  0  1  1  1  1  1  1
   4   56    4  0  1  1  1
   5   60    2  1  1
   6   62    1  1
At: 9
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  1  1  1  1  1  1  1  1  1  1  1
   3   48    8  0  0  1  1  1  1  1  1
   4   56    4  0  1  1  1
   5   60    2  1  1
   6   62    1  1
At: 10
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  1  1  1  1  1  1  1  1  1  1  1
   3   48    8  0  0  1  1  1  1  1  1
   4   56    4  0  1  1  1
   5   60    2  1  1
   6   62    1  1
At: 11
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  1  1  1  1  1  1  1  1  1  1
   3   48    8  0  0  0  1  1  1  1  1
   4   56    4  0  1  1  1
   5   60    2  1  1
   6   62    1  1
At: 12
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  1  1  1  1  1  1  1  1  1  1
   3   48    8  0  0  0  1  1  1  1  1
   4   56    4  0  1  1  1
   5   60    2  1  1
   6   62    1  1
At: 13
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  0  1  1  1  1  1  1  1  1  1
   3   48    8  0  0  0  1  1  1  1  1
   4   56    4  0  1  1  1
   5   60    2  1  1
   6   62    1  1
At: 14
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  0  1  1  1  1  1  1  1  1  1
   3   48    8  0  0  0  1  1  1  1  1
   4   56    4  0  1  1  1
   5   60    2  1  1
   6   62    1  1
At: 15
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  0  0  1  1  1  1  1  1  1  1
   3   48    8  0  0  0  0  1  1  1  1
   4   56    4  0  0  1  1
   5   60    2  0  1
   6   62    1  1
At: 16
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  0  0  1  1  1  1  1  1  1  1
   3   48    8  0  0  0  0  1  1  1  1
   4   56    4  0  0  1  1
   5   60    2  0  1
   6   62    1  1
At: 17
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  0  0  0  1  1  1  1  1  1  1
   3   48    8  0  0  0  0  1  1  1  1
   4   56    4  0  0  1  1
   5   60    2  0  1
   6   62    1  1
At: 18
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  0  0  0  1  1  1  1  1  1  1
   3   48    8  0  0  0  0  1  1  1  1
   4   56    4  0  0  1  1
   5   60    2  0  1
   6   62    1  1
At: 19
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  0  0  0  0  1  1  1  1  1  1
   3   48    8  0  0  0  0  0  1  1  1
   4   56    4  0  0  1  1
   5   60    2  0  1
   6   62    1  1
At: 20
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  0  0  0  0  1  1  1  1  1  1
   3   48    8  0  0  0  0  0  1  1  1
   4   56    4  0  0  1  1
   5   60    2  0  1
   6   62    1  1
At: 21
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  0  0  0  0  0  1  1  1  1  1
   3   48    8  0  0  0  0  0  1  1  1
   4   56    4  0  0  1  1
   5   60    2  0  1
   6   62    1  1
At: 22
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  0  0  0  0  0  1  1  1  1  1
   3   48    8  0  0  0  0  0  1  1  1
   4   56    4  0  0  1  1
   5   60    2  0  1
   6   62    1  1
At: 23
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  0  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  0  0  0  0  0  0  1  1  1  1
   3   48    8  0  0  0  0  0  0  1  1
   4   56    4  0  0  0  1
   5   60    2  0  1
   6   62    1  1
At: 24
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  0  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  0  0  0  0  0  0  1  1  1  1
   3   48    8  0  0  0  0  0  0  1  1
   4   56    4  0  0  0  1
   5   60    2  0  1
   6   62    1  1
At: 25
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  0  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  0  0  0  0  0  0  0  1  1  1
   3   48    8  0  0  0  0  0  0  1  1
   4   56    4  0  0  0  1
   5   60    2  0  1
   6   62    1  1
At: 26
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  0  0  0  0  0
   2   32   16  0  0  0  0  0  0  0  0  0  0  0  0  0  1  1  1
   3   48    8  0  0  0  0  0  0  1  1
   4   56    4  0  0  0  1
   5   60    2  0  1
   6   62    1  1
At: 27
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  0  0  0  0
   2   32   16  0  0  0  0  0  0  0  0  0  0  0  0  0  0  1  1
   3   48    8  0  0  0  0  0  0  0  1
   4   56    4  0  0  0  1
   5   60    2  0  1
   6   62    1  1
At: 28
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  0  0  0
   2   32   16  0  0  0  0  0  0  0  0  0  0  0  0  0  0  1  1
   3   48    8  0  0  0  0  0  0  0  1
   4   56    4  0  0  0  1
   5   60    2  0  1
   6   62    1  1
At: 29
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  0  0
   2   32   16  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  1
   3   48    8  0  0  0  0  0  0  0  1
   4   56    4  0  0  0  1
   5   60    2  0  1
   6   62    1  1
At: 30
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  0
   2   32   16  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  1
   3   48    8  0  0  0  0  0  0  0  1
   4   56    4  0  0  0  1
   5   60    2  0  1
   6   62    1  1
At: 31
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1  1
   2   32   16  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   3   48    8  0  0  0  0  0  0  0  0
   4   56    4  0  0  0  0
   5   60    2  0  0
   6   62    1  0
""");
   }

  static void test_step()                                                       // Test tree of bits
   {final BitSet b = test_bits(23, true, false);
    final int[]  n = new int[]{2, 3, 5, 6, 7, 9, 11, 13};

    b.clearAll();
    for (int i : range(n.length)) b.setOnePath(b.new Pos(n[i]));
    ok(b, """
BitSet          0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
   1    0   32  0  0  1  1  0  1  1  1  0  1  0  1  0  1  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
   2   32   16  0  1  1  1  1  1  1  0  0  0  0  0  0  0  0  0
   3   48    8  1  1  1  1  0  0  0  0
   4   56    4  1  1  0  0
   5   60    2  1  0
   6   62    1  1
""");
    ok(b.firstOne().position(), 2);  ok(b.lastOne().position(), 13);
    ok(b.prevOne(b.new Pos( 0)) == null);         ok(b.nextOne(b.new Pos( 0)).position(),  2);
    ok(b.prevOne(b.new Pos( 1)) == null);         ok(b.nextOne(b.new Pos( 1)).position(),  2);
    ok(b.prevOne(b.new Pos( 2)) == null);         ok(b.nextOne(b.new Pos( 2)).position(),  3);
    ok(b.prevOne(b.new Pos( 3)).position(), 2);   ok(b.nextOne(b.new Pos( 3)).position(),  5);
    ok(b.prevOne(b.new Pos( 4)).position(), 3);   ok(b.nextOne(b.new Pos( 4)).position(),  5);
    ok(b.prevOne(b.new Pos( 5)).position(), 3);   ok(b.nextOne(b.new Pos( 5)).position(),  6);
    ok(b.prevOne(b.new Pos( 6)).position(), 5);   ok(b.nextOne(b.new Pos( 6)).position(),  7);
    ok(b.prevOne(b.new Pos( 7)).position(), 6);   ok(b.nextOne(b.new Pos( 7)).position(),  9);
    ok(b.prevOne(b.new Pos( 8)).position(), 7);   ok(b.nextOne(b.new Pos( 8)).position(),  9);
    ok(b.prevOne(b.new Pos( 9)).position(), 7);   ok(b.nextOne(b.new Pos( 9)).position(), 11);
    ok(b.prevOne(b.new Pos(10)).position(), 9);   ok(b.nextOne(b.new Pos(10)).position(), 11);
    ok(b.prevOne(b.new Pos(11)).position(), 9);   ok(b.nextOne(b.new Pos(11)).position(), 13);
    ok(b.prevOne(b.new Pos(12)).position(), 11);  ok(b.nextOne(b.new Pos(12)).position(), 13);
    ok(b.prevOne(b.new Pos(13)).position(), 11);  ok(b.nextOne(b.new Pos(13)) == null);
    ok(b.prevOne(b.new Pos(14)).position(), 13);  ok(b.nextOne(b.new Pos(14)) == null);

    for (int i : range(n.length))
     {b.clearOnePath(b.new Pos(n[i]));
      b.integrity();
     }
   }

  static void test_step_zero()                                                  // Test tree of bits
   {final BitSet b = test_bits(7, true, false);

    b.clearAll();
    b.setOnePath(b.new Pos(0)); b.setOnePath(b.new Pos(4));
    ok(b, """
BitSet          0  1  2  3  4  5  6  7
   1    0    8  1  0  0  0  1  0  0  0
   2    8    4  1  0  1  0
   3   12    2  1  1
   4   14    1  1
""");
    ok(b.firstOne().position(), 0);
    ok(b.lastOne() .position(),  4);
    ok(b.prevOne(b.new Pos( 0)) == null);
    ok(b.prevOne(b.new Pos( 1)).position(), 0);
    ok(b.prevOne(b.new Pos( 2)).position(), 0);
   }

  static void test_one()
   {final int N = 1;                                                            // Test size.
    final byte[]bytes = new byte[BitSet.bytesNeeded(N, true)];                  // Allocate backing storage.

    final BitSet b = new BitSet(N, true)                                        // Create a bit set using the backing storage
     {void setByte(int Index, byte Value) {bytes[Index] = Value;}               // Backend write.
      byte getByte(int Index)      {return bytes[Index];}                       // Backend read.
     };

    b.clearAll();
    b.setOnePath(b.new Pos(0));
    ok(b, """
BitSet          0
""");

    ok(b.prevOne(b.new Pos(0)) == null);
    ok(b.nextOne(b.new Pos(0)) == null);
   }

  static void test_two()
   {final int N = 2;                                                            // Test size.
    final byte[]bytes = new byte[BitSet.bytesNeeded(N, true)];                  // Allocate backing storage.

    final BitSet b = new BitSet(N, true)                                        // Create a bit set using the backing storage
     {void setByte(int Index, byte Value) {bytes[Index] = Value;}               // Backend write.
      byte getByte(int Index)      {return bytes[Index];}                       // Backend read.
     };

    b.clearAll();
    b.setOnePath(b.new Pos(1));
    ok(b, """
BitSet          0  1
   1    0    2  0  1
""");

    ok(b.prevOne(b.new Pos(0)) == null, true);
    ok(b.prevOne(b.new Pos(1)) == null, true);

    ok(b.nextOne(b.new Pos(0)).position(), 1);
    ok(b.nextOne(b.new Pos(1)) == null, true);
   }

  static void test_integrity()
   {final int N = 8;                                                            // Test size.
    final byte[]bytes = new byte[BitSet.bytesNeeded(N, true)];                  // Allocate backing storage.

    final BitSet b = new BitSet(N, true)                                        // Create a bit set using the backing storage
     {void setByte(int Index, byte Value) {bytes[Index] = Value;}               // Backend write.
      byte getByte(int Index)      {return bytes[Index];}                       // Backend read.
     };

    b.clearAll();
    b.setOnePath(b.new Pos(1)); b.setOnePath(b.new Pos(3));
    ok(b.integrity());
    b.setBit(b.new Pos(7), true);
    ok(!b.integrity(false));
   }

  static void test_clearAll()
   {final BitSet b = test_bits(8, true, false);

    b.setOnePath(b.new Pos(1)); b.setOnePath(b.new Pos(3));
    ok(b.integrity());
    b.clearAll();
    ok(b.integrity());
   }

  static void oldTests()                                                        // Tests thought to be stable.
   {test_bitSet();
    test_oneBitTree();
    test_allOneBitTree();
    test_allZeroBitTree();
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
