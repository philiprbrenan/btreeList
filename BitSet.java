//------------------------------------------------------------------------------
// Fixed size bit set which can locate set or cleared bits in log N time
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.util.*;                                                             // Standard utility library.

abstract public class BitSet extends Test                                       // Abstract fixed-size bit set using byte-level storage.
 {final int bitSize;                                                            // Number of bits in the bit set.
  final int byteSize;                                                           // Number of bytes in the bit set.
  final boolean zero, one;                                                      // Able to locate zeros and ones via a tree of bits if set
  static boolean debug;

//D1 Constructors                                                               // Construct bit sets of various sizes with the optional ability of locating ones and zeros efficiently

  public BitSet(int BitSize, boolean One, boolean Zero)                         // Constructor
   {bitSize = nextPowerOfTwo(BitSize);                                          // Record size.
    new If (bitSize < 0) {void Then() {stop("Size must be zero or positive");}};// Validate size.
    zero = Zero;                                                                // Locate zeroes efficiently
    one = One;                                                                  // Locate ones efficiently
    byteSize = bytesNeeded(BitSize, one, zero);                                 // A tree of bits
   }

  public BitSet(Spec Spec)                                                      // Constructor using a specification
   {this(Spec.bitSize(), Spec.one(), Spec.zero());                              // Record size.
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

  private void checkIndex(int Index)                                            // Check that a bit index is valid
   {new If(Index < 0)
     {void Then() {stop("BitSet index cannot be negative:", Index);}
     };
    new If(Index >= bitSize)
     {void Then()
       {stop("BitSet index cannot be greater than or equal to:", Index, bitSize);
       }
     };
   }

  private void checkOne()                                                       // Check that we can search for ones in this bit set
   {new If (!one)
     {void Then()
       {stop("This bitset does have the ability to search for ones");
       }
     };
   }

  private void checkZero()                                                      // Check that we can search for zeroes in this bit set
   {new If (!zero)
     {void Then()
       {stop("This bitset does not have the ability to search for zeroes");
       }
     };
   }

  static class Spec                                                             // Specification of a bitset
   {private final int  bitSize;                                                 // Number of bits in the bit set.
    private final int byteSize;                                                 // Number of bytes in the bit set.
    private final boolean zero;                                                 // Able to locate zeros via a tree of bits if set
    private final boolean  one;                                                 // Able to locate ones via a tree of bits if set

    public Spec(int Size, boolean One, boolean Zero)
     { bitSize = Size;
      byteSize = bytesNeeded(Size, One, Zero);                                  // Number of bytes needed for a bit set of specified size with or without the ability to locate zeroes and ones
          zero = Zero;
           one = One;
     }
    public Spec(int Size, boolean One) {this(Size, One,   false);}
    public Spec(int Size)              {this(Size, false, false);}

    public int bitSize () {return bitSize;}
    public int byteSize() {return byteSize;}
    public boolean zero() {return zero;}
    public boolean one () {return one;}
   }

  class Pos                                                                     // A position of a bit in the bit set
   {final int position;                                                         // Position of the bit
    int position() {return position;}
    public Pos(int Position)                                                    // Construct a bit position
     {position = Position;
     }
    public String toString() {return ""+position();}                            // Print a bit position
   }

// D2 Get and Set                                                               // Get and set bits in the  bit tree setting the corresponding paths in the bits trees if necessary

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
    if (bitSize == 0) return;                                                   // Tree with no entries

    new Runnable()                                                              // For loop to set bits along path in One tree to actual bit
     {int b = Index.position(), p = 0, w = bitSize;                             // Position in level, level, width

      public void run()                                                         // Set bits along the path to the actual bit in the One tree
       {new For(bitSize)                                                        // Step from root to leaf
         {boolean body(int Index)
           {if (p != 0)                                                         // Not on the actual bits
             {final Pos q = new Pos(p+b);                                       // Position in One tree
              if (getBitNC(q)) return false; else setBitNC(q, true);            // Stop creating the path once we have arrived at a tree bit that is correctly set: as there are no changes at this level the upper levels must be ok too
             }
            b >>>= 1; p += w; w >>>= 1;                                         // Next level up
            return w > 0;                                                       // As long as we are in a valid level
           }
         };
       }
     }.run();
   }

  private void clearOnePath(Pos Index)                                          // Clear bits along the path from the indexed bit to the root of the bit tree
   {checkOne();
    if (bitSize == 0) return;                                                   // Tree with no entries

    new Runnable()                                                              // For loop to set bits along path in One tree to actual bit
     {int b = Index.position(), p = 0, w = bitSize;                             // Position in level, level, width

      public void run()                                                         // Set bits along the path to the actual bit in the One tree
       {new For(bitSize)                                                        // Step from root to leaf
         {boolean body(int Index)
           {final int B = b>>>1, q = p + w + B, Q = p + B + B;
            if (B+B+1 < w && !getBitNC(new Pos(Q)) && !getBitNC(new Pos(Q+1)))  // Check both bits in the previous row are off
             {final Pos r = new Pos(q);
              if (!getBitNC(r)) return false;                                   // Bit is already correctly set so there is nothing more to do
                   setBitNC(r, false);                                          // Clear set bit along path to root
             }
            b >>>= 1; p += w; w >>>= 1;                                         // Next layer
            return w > 0;                                                       // As long as we are in a valid level
           }
         };
       }
     }.run();
   }

  private int addressZeroTree()                                                 // The zero tree will be held directly after the actual bits if there is no one tree, else beyond the one tree
   {int p = bitSize; if (one) p += bitSize-1;                                   // Address first bit of zero bit tree
    return p;
   }

  private void clearZeroPath(Pos Index)                                         // Clear the target bit and set bits along the path from the indexed bit to the root of the bit tree
   {checkZero();
    if (bitSize == 0) return;                                                   // Tree with no entries

    new Runnable()                                                               // For loop to set bits along path in One tree to actual bit
     {int p = addressZeroTree();                                                // Address zero bit tree
      int b = Index.position()>>>1;                                             // Position in layer
      int w = bitSize>>>1;                                                      // Width of this layer

      public void run()                                                         // Set bits along the path to the actual bit in the One tree
       {new For(bitSize)                                                        // Step from root to leaf
         {boolean body(int Index)
           {final Pos q = new Pos(p+b);
            if (getBitNC(q)) return false; else setBitNC(q, true);              // Stop creating the path once we have arrived at a tree bit that is correctly set: as there are no changes at this level the upper levels must be ok too
            b >>>= 1; p += w; w >>>= 1;                                         // Next layer
            return w > 0;                                                       // As long as we are in a valid level
           }
         };
       }
     }.run();
   }

  private void setZeroPath(Pos Index)                                           // Set bits along the path from the indexed bit to the root of the bit tree unlkess thre is another path running through each bit
   {if (bitSize == 0) return;                                                   // Tree with no entries

    new Runnable()                                                              // Set bits along the path to the actual bit in the One tree
     {int p = addressZeroTree();                                                // First child layer is the first layer of the zero bit tree
      int w = bitSize >>> 1;                                                    // Width of child layer
      int b = Index.position() >>> 1;                                           // Index of bit in child layer

      public void run()
       {if (!getBitNC(new Pos(b+b)) || !getBitNC(new Pos(b+b+1))) return;       // Check there is a zero
        final Pos r = new Pos(p+b);                                             // Position in first layer of Zero tree
        if (!getBitNC(r)) return;                                               // Bit is already correctly set to show no path so there is nothing more to do
             setBitNC(r,  false);                                               // Clear set bit along path to root to show no path

        new For(bitSize)                                                        // Step from root to leaf
         {boolean body(int Index)
           {int P = p;                                                          // Child layer becomes parent layer
            b  >>>= 1;                                                          // Index of bit in child layer
            p    += w;                                                          // New child layer
            w  >>>= 1;                                                          // Child layer width
            int Q = P + b + b;
            if ( getBitNC(new Pos(Q)) || getBitNC(new Pos(Q+1))) return false;  // There is a one in the upper row so we do not need to clear further down
            final Pos r = new Pos(p+b);
            if (!getBitNC(r)) return false;                                     // Bit is already correctly set so there is nothing more to do
                 setBitNC(r,  false);                                           // Clear set bit along path to root
            return w > 0;                                                       // As long as we are in a valid level
           }
         };
       }
     }.run();
   }

  public void initialize()                                                      // Clear all bits.
   {new For(byteSize)                                                           // Step from root to leaf
     {boolean body(int i)
       {setByte(i, (byte)0);
        return true;
       }
     };

    if (zero)                                                                   // Set all the bits to one in the paths in the zero tree if present to show that all the actual bits are zero
     {final int p = addressZeroTree();                                              // Position in level, level, width
      new For(bitSize)                                                          // For loop to set bits along path in One tree to actual bit
       {boolean body(int i)
         {setBitNC(new Pos(p+i), true); return true;
         }
       };
     }
   }

//D2 Locate Ones                                                                // Find the first, last, next, previous bit set to one

  public Pos firstOne()                                                         // Find the index of the first set bit
   {checkOne();
    return getBit(new Pos(0)) ? new Pos(0) : nextOne(new Pos(0));
   }

  public Pos lastOne()                                                          // Find the index of the last set bit
   {checkOne();
    final int l = bitSize-1;
    return getBit(new Pos(l)) ? new Pos(l) : prevOne(new Pos(l));
   }

  public Pos nextOne(Pos Index)                                                 // Find the index of the next set bit above the specified bit
   {checkOne();
    checkIndex(Index.position());

    final Int b = new Int(Index.position());                                    // Position in layer
    final Int w = new Int(bitSize);                                             // Width of layer
    final Int p = new Int(0);                                                   // Offset of layer
    final Int n = new Int();                                                    // The next element if it exists, offset of layer

    if (b.eq(new Int(w).sub(1))) return null;                                   // At the end so no next bit

    new For(bitSize)                                                            // Traverse down through the tree
     {boolean body(int i)                                                       // Traverse down through the tree
       {final Int c = new Int(b).add(1);                                        // Is there a path down from the next bit?
        if (c.lt(w) && getBitNC(new Pos(new Int(p).add(c).i())))                // Found next up bit
         {new For(i)                                                            // Step down to the leaves
           {boolean body(int j)                                                 // Step down to the leaves
             {w.up(); p.sub(w); c.up();                                         // Move up to next layer
              c.add(getBitNC(new Pos(new Int(p).add(c).i())) ? 0 : 1);          // Follow path as low as possible
              return true;                                                      // Continue the loop
             }
           };
          n.i(c); return false;                                                 // Found the next element
         }
        else
         {p.add(w); w.down(); if (w.eq(0)) return false; b.down();              // Address next level of bits further down in One tree
         }
        return true;                                                            // Continue the loop
       }
     };
    return n.valid() ? new Pos(n.i()) : null;                                   // No alternate path down
   }

  public Pos prevOne(Pos Index)                                                 // Find the index of the previous set bit below the specified bit
   {checkOne();
    checkIndex(Index.position());
    int b = Index.position(), p = 0, w = bitSize;
    if (b == 0) return null;                                                    // At the start so no previous bit

    for(int i : range(bitSize))                                                 // Much more than necessary
     {int B = b-1;                                                              // Is there a path down from the next bit?
      if (b > 0 && getBitNC(new Pos(p+B)))                                      // Found next down bit
       {for(int j : range(i))                                                   // Step down to the leaves
         {w <<= 1; p -= w; B <<= 1;
          B  += getBitNC(new Pos(p+B+1)) ? 1 : 0;                               // Follow path as high as possible
         }
        return new Pos(B);
       }
      p += w; w >>>= 1; if (w == 0) break; b >>>= 1;                            // Address next level of bits in tree
     }
    return null;                                                                // No alternate path down
   }

//D2 Locate Zeros                                                               // Find the first, last, next, previous bit set to zero

  public Pos firstZero()                                                        // Find the index of the first set bit
   {checkZero();
    return !getBit(new Pos(0)) ? new Pos(0) : nextZero(new Pos(0));
   }

  public Pos lastZero()                                                         // Find the index of the last set bit
   {checkZero();
    final int l = bitSize-1;
    return !getBit(new Pos(l)) ? new Pos(l) : prevZero(new Pos(l));
   }

  public Pos nextZero(Pos Index)                                                // Find the index of the next set bit above the specified bit
   {checkZero();
    checkIndex(Index.position());
    int b = Index.position(), w = bitSize>>>1, p = addressZeroTree();
    if (b == bitSize-1)        return null;                                     // Last bit so no next bit
    if (!getBit(new Pos(b+1))) return new Pos(b+1);                             // Next bit is zero
    if (bitSize == 2)          return null;                                     // No more bits to check
    b >>>= 1;                                                                   // First layer of zero tree bits

    for(int i : range(bitSize))                                                 // Search down through the zero bit tree
     {int B = b+1;                                                              // Is there a path down from the next bit?
      if (B >= w) return null;                                                  // Nothing beyond to search so no next zero
      if (getBitNC(new Pos(p+B)))                                               // Found next up bit
       {for(int j : range(i))                                                   // Step down to the leaves
         {w <<= 1; p -= w; B <<= 1;
          B  += getBitNC(new Pos(p+B)) ? 0 : 1;                                 // Follow path as low as possible
         }
        return new Pos(B+B+(getBit(new Pos(B+B)) ? 1 : 0));                     // Next zero bit from actual bits
       }
      p += w; w >>>= 1; if (w == 0) break; b >>>= 1;                            // Address next level of bits in tree
     }
    return null;                                                                // No alternate path down
   }

  public Pos prevZero(Pos Index)                                                // Find the index of the previous set bit below the specified bit
   {checkZero();
    checkIndex(Index.position());

    int b = Index.position();
    if (b == 0)                return null;                                     // First bit so no prev bit
    if (!getBit(new Pos(b-1))) return new Pos(b-1);                             // Prev bit is zero
    if (bitSize == 2)          return null;                                     // No more bits to check

    int w = bitSize>>>1, p = addressZeroTree(); b >>>= 1;                       // First layer of zero tree bits

    for(int i : range(bitSize))                                                 // Search down through the zero bit tree
     {int B = b-1;                                                              // Is there a path down from the next bit?
      if (B < 0) return null;                                                   // Nothing prior to search so no prev zero
      if (getBitNC(new Pos(p+B)))                                               // Found next up bit
       {for(int j : range(i))                                                   // Step down to the leaves
         {b <<= 1;                                                              // Position of next level in tree
          w <<= 1; p -= w; B <<= 1;
          B  += getBitNC(new Pos(p+b)) ? 0 : 1;                                 // Follow path as high as possible
         }
        return new Pos(B+B+(!getBit(new Pos(B+B+1)) ? 1 : 0));                  // Next zero bit from actual bits
       }
      p += w; w >>>= 1; if (w == 0) break; b >>>= 1;                            // Address next level of bits in tree
     }
    return null;                                                                // No alternate path down
   }

//D2 Full or empty                                                              // Check whether a bit set is full or empty

  public boolean  full() {return firstZero() == null;}                          // If there are no zero bits then the bit set must be full
  public boolean empty() {return firstOne () == null;}                          // If there are no one bits then the bit set must be empty

//D2 Integrity                                                                  // Check that the bit trees match the actual bits

  public boolean integrity() {return integrity(true);}                          // Do an integrity check on the bitset to detect corruption

  public boolean integrity(boolean Stop)                                        // Do an integrity check on the bitset to detect corruption and stop on failures unless specified otherwise
   {final BitSet.Spec spec = new BitSet.Spec(bitSize, one, zero);               // Specify bit set
    final byte[]bytes = new byte[spec.byteSize()];                              // Allocate backing storage.

    final BitSet b = new BitSet(spec)                                           // Create an identical bitset
     {void setByte(int Index, byte Value) {bytes[Index] = Value;}               // Backend write.
      byte getByte(int Index)      {return bytes[Index];}                       // Backend read.
     };

    b.initialize();
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

//D2 Print                                                                      // Print the bit set

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
   {final BitSet.Spec spec = new BitSet.Spec(N, One, Zero);                     // Allocate backing storage.
    final byte[]bytes = new byte[spec.byteSize()];                              // Allocate backing storage.

    final BitSet b = new BitSet(spec)                                           // Create a bit set
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
    b.initialize();
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

    for (int i : range(13))     ok(b.nextOne(b.new Pos(i)).position(), 13);
    for (int i : range(13, 19)) ok(b.nextOne(b.new Pos(i)).position(), 19);
    for (int i : range(19, 24)) ok(b.nextOne(b.new Pos(i)).position(), 24);
    for (int i : range(23, 28)) ok(b.nextOne(b.new Pos(i)).position(), i+1);
                                ok(b.nextOne(b.new Pos(28)).position(), 30);
                                ok(b.nextOne(b.new Pos(29)).position(), 30);
                                ok(b.nextOne(b.new Pos(30)).position(), 31);
                                ok(b.nextOne(b.new Pos(31)) == null);

    for (int i : range(14))     ok(b.prevOne(b.new Pos(i)) == null);
    for (int i : range(14, 20)) ok(b.prevOne(b.new Pos(i)).position(), 13);
    for (int i : range(20, 24)) ok(b.prevOne(b.new Pos(i)).position(), 19);
    for (int i : range(25, 29)) ok(b.prevOne(b.new Pos(i)).position(), i-1);
                                ok(b.prevOne(b.new Pos(30)).position(), 28);
                                ok(b.prevOne(b.new Pos(31)).position(), 30);
    ok(b.firstOne().position(),   13);
    ok(b.lastOne().position(),    31);

    for (int i : range(12))     ok(b.nextZero(b.new Pos( i)).position(), i+1);
                                ok(b.nextZero(b.new Pos(12)).position(), 14);
    for (int i : range(13, 18)) ok(b.nextZero(b.new Pos( i)).position(), i+1);
    for (int i : range(19, 23)) ok(b.nextZero(b.new Pos( i)).position(), i+1);
    for (int i : range(23, 28)) ok(b.nextZero(b.new Pos( i)).position(), 29);
    for (int i : range(29, 32)) ok(b.nextZero(b.new Pos( i)) == null);

                                ok(b.prevZero(b.new Pos(0)) == null);
    for (int i : range( 1, 14)) ok(b.prevZero(b.new Pos( i)).position(), i-1);
                                ok(b.prevZero(b.new Pos(14)).position(), 12);
    for (int i : range(15, 19)) ok(b.prevZero(b.new Pos( i)).position(), i-1);
                                ok(b.prevZero(b.new Pos(20)).position(), 18);
    for (int i : range(21, 24)) ok(b.prevZero(b.new Pos( i)).position(), i-1);
    for (int i : range(24, 30)) ok(b.prevZero(b.new Pos( i)).position(), 23);
    for (int i : range(30, 32)) ok(b.prevZero(b.new Pos( i)).position(), 29);

    ok(b.firstZero().position(),  0);
    ok(b.lastZero() .position(), 29);
   }

  static void test_prevNext01()                                                 // Test tree of searchable one bits
   {final int N = 16;
    final BitSet b = test_bits(N, true, true);
    b.initialize();
    for (int i : range(N)) b.set(b.new Pos(i), (i / 4) % 2 == 0);

    //stop(b);
    ok(b, """
BitSet            0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16 |  1  1  1  1  0  0  0  0  1  1  1  1  0  0  0  0
One:
   2   16    8 |  1  1  0  0  1  1  0  0
   3   24    4 |  1  0  1  0
   4   28    2 |  1  1
   5   30    1 |  1
Zero:
   1   31    8 |  0  0  1  1  0  0  1  1
   2   39    4 |  0  1  0  1
   3   43    2 |  1  1
   4   45    1 |  1
""");

    for (int i : range( 3))     ok(b.nextOne(b.new Pos(i)).position(), i+1);
    for (int i : range( 4,  8)) ok(b.nextOne(b.new Pos(i)).position(), 8);
    for (int i : range( 7, 11)) ok(b.nextOne(b.new Pos(i)).position(), i+1);
    for (int i : range(11, 16)) ok(b.nextOne(b.new Pos(i)) == null);

                                ok(b.prevOne(b.new Pos(0)) == null);
    for (int i : range( 1,  5)) ok(b.prevOne(b.new Pos(i)).position(), i-1);
    for (int i : range( 4,  9)) ok(b.prevOne(b.new Pos(i)).position(), 3);
    for (int i : range( 9, 12)) ok(b.prevOne(b.new Pos(i)).position(), i-1);
    for (int i : range(12, 16)) ok(b.prevOne(b.new Pos(i)).position(), 11);
    ok(b.firstOne().position(),   0);
    ok(b.lastOne().position(),    11);

    for (int i : range( 3))     ok(b.nextZero(b.new Pos( i)).position(), 4);
    for (int i : range( 3,  7)) ok(b.nextZero(b.new Pos( i)).position(), i+1);
    for (int i : range( 7, 11)) ok(b.nextZero(b.new Pos( i)).position(), 12);
    for (int i : range(11, 15)) ok(b.nextZero(b.new Pos( i)).position(), i+1);
                                ok(b.nextZero(b.new Pos(15)) == null);

    for (int i : range( 5))     ok(b.prevZero(b.new Pos( 0)) == null);
    for (int i : range( 5,  9)) ok(b.prevZero(b.new Pos( i)).position(), i-1);
    for (int i : range( 8, 12)) ok(b.prevZero(b.new Pos( i)).position(), 7);
    for (int i : range(13, 16)) ok(b.prevZero(b.new Pos( i)).position(), i-1);
    ok(b.firstZero().position(),  4);
    ok(b.lastZero ().position(), 15);
   }

  static void test_prevNext10()                                                 // Test tree of searchable one bits
   {final int N = 16;
     final BitSet b = test_bits(N, true, true);
    b.initialize();
    for (int i : range(N)) b.set(b.new Pos(i), (i / 4) % 2 == 1);

    //stop(b);

    for (int i : range( 3))     ok(b.nextZero(b.new Pos(i)).position(), i+1);
    for (int i : range( 3, 8))  ok(b.nextZero(b.new Pos(i)).position(), 8);
    for (int i : range( 7, 11)) ok(b.nextZero(b.new Pos(i)).position(), i+1);
    for (int i : range(11, 16)) ok(b.nextZero(b.new Pos(i)) == null);

                                ok(b.prevZero(b.new Pos(0)) == null);
    for (int i : range( 1,  5)) ok(b.prevZero(b.new Pos(i)).position(), i-1);
    for (int i : range( 4,  8)) ok(b.prevZero(b.new Pos(i)).position(), 3);
    for (int i : range( 9, 13)) ok(b.prevZero(b.new Pos(i)).position(), i-1);
    for (int i : range(12, 16)) ok(b.prevZero(b.new Pos(i)).position(), 11);

    ok(b, """
BitSet            0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16 |  0  0  0  0  1  1  1  1  0  0  0  0  1  1  1  1
One:
   2   16    8 |  0  0  1  1  0  0  1  1
   3   24    4 |  0  1  0  1
   4   28    2 |  1  1
   5   30    1 |  1
Zero:
   1   31    8 |  1  1  0  0  1  1  0  0
   2   39    4 |  1  0  1  0
   3   43    2 |  1  1
   4   45    1 |  1
""");

    for (int i : range( 4))     ok(b.nextOne(b.new Pos( i)).position(), 4);
    for (int i : range( 3,  7)) ok(b.nextOne(b.new Pos( i)).position(), i+1);
    for (int i : range( 7, 12)) ok(b.nextOne(b.new Pos( i)).position(), 12);
    for (int i : range(11, 15)) ok(b.nextOne(b.new Pos( i)).position(), i+1);
                                ok(b.nextOne(b.new Pos(15)) == null);

    for (int i : range( 5))     ok(b.prevOne(b.new Pos( i)) == null);
    for (int i : range( 5,  8)) ok(b.prevOne(b.new Pos( i)).position(), i-1);
    for (int i : range( 8, 13)) ok(b.prevOne(b.new Pos( i)).position(), 7);
    for (int i : range(13, 16)) ok(b.prevOne(b.new Pos( i)).position(), i-1);
    ok(b.firstOne().position(),   4);
    ok(b.lastOne().position(),    15);
   }

  static void test_integrity()
   {final int N = 8;                                                            // Test size.
    final BitSet.Spec spec = new BitSet.Spec(N, true);                          // Allocate backing storage.
    final byte[]bytes = new byte[spec.byteSize()];                              // Allocate backing storage.

    final BitSet b = new BitSet(spec)                                           // Create a bit set using the backing storage
     {void setByte(int Index, byte Value) {bytes[Index] = Value;}               // Backend write.
      byte getByte(int Index)      {return bytes[Index];}                       // Backend read.
     };

    b.initialize();
    b.set(b.new Pos(1), true); b.set(b.new Pos(3), true);
    ok(b.integrity());
    b.setBit(b.new Pos(7), true);
    ok(!b.integrity(false));
   }

  static void test_initialize()
   {final BitSet b = test_bits(8, true, false);

    b.set(b.new Pos(1), true); b.set(b.new Pos(3), true);
    ok(b.integrity());
    b.initialize();
    ok(b.integrity());
   }

  static void test_oneZero()
   {final int N = 8;
    final BitSet b = test_bits(N, true, true);
    b.initialize();
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

    for (int i : range(N))
     {b.set(b.new Pos(i), false);
      s.append("Clear: "+i+"\n"+b);
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
Clear: 0
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  0  1  1  1  1  1  1  1
One:
   2    8    4 |  1  1  1  1
   3   12    2 |  1  1
   4   14    1 |  1
Zero:
   1   15    4 |  1  0  0  0
   2   19    2 |  1  0
   3   21    1 |  1
Clear: 1
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  0  0  1  1  1  1  1  1
One:
   2    8    4 |  0  1  1  1
   3   12    2 |  1  1
   4   14    1 |  1
Zero:
   1   15    4 |  1  0  0  0
   2   19    2 |  1  0
   3   21    1 |  1
Clear: 2
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  0  0  0  1  1  1  1  1
One:
   2    8    4 |  0  1  1  1
   3   12    2 |  1  1
   4   14    1 |  1
Zero:
   1   15    4 |  1  1  0  0
   2   19    2 |  1  0
   3   21    1 |  1
Clear: 3
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  0  0  0  0  1  1  1  1
One:
   2    8    4 |  0  0  1  1
   3   12    2 |  0  1
   4   14    1 |  1
Zero:
   1   15    4 |  1  1  0  0
   2   19    2 |  1  0
   3   21    1 |  1
Clear: 4
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  0  0  0  0  0  1  1  1
One:
   2    8    4 |  0  0  1  1
   3   12    2 |  0  1
   4   14    1 |  1
Zero:
   1   15    4 |  1  1  1  0
   2   19    2 |  1  1
   3   21    1 |  1
Clear: 5
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  0  0  0  0  0  0  1  1
One:
   2    8    4 |  0  0  0  1
   3   12    2 |  0  1
   4   14    1 |  1
Zero:
   1   15    4 |  1  1  1  0
   2   19    2 |  1  1
   3   21    1 |  1
Clear: 6
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  0  0  0  0  0  0  0  1
One:
   2    8    4 |  0  0  0  1
   3   12    2 |  0  1
   4   14    1 |  1
Zero:
   1   15    4 |  1  1  1  1
   2   19    2 |  1  1
   3   21    1 |  1
Clear: 7
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
""");
   }

  static void test_fullEmpty()
   {final int N = 16;
    final BitSet b = test_bits(N, true, true);
    b.initialize();
    ok(b.empty());
    for (int i : range(N))
     {ok(!b.full());
      b.set(b.new Pos(i), true);
      ok(!b.empty());
     }
    ok(b.full());
   }

  static void oldTests()                                                        // Tests thought to be stable.
   {test_bitSet();
    test_prevNext01();
    test_prevNext();
    test_integrity();
    test_initialize();
    test_oneZero();
    test_prevNext01();
    test_prevNext10();
    test_fullEmpty();
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
