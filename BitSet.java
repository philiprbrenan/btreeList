//----------------------------------------------------------------------------------------------------------------------
// Fixed size bit set which can locate set or cleared bits in log N time.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;                                                                                                     // Standard utility library.

abstract public class BitSet extends Program                                                                            // Abstract fixed-size bit set using byte-level storage.
 {final  int bitSize, bitSize1, bitSize2;                                                                               // Number of bits in the bit set.
  final  int byteSize;                                                                                                  // Number of bytes in the bit set.
  final  boolean oneTreeBit;                                                                                            // At most only one tree bit present
  final  boolean zero, one;                                                                                             // Able to locate zeros and ones via a tree of bits if set
  static boolean debug;                                                                                                 // Debug if true

//D1 Constructors                                                                                                       // Construct bit sets of various sizes with the optional ability of locating ones and zeros efficiently

  static class Build                                                                                                    // Specification of a bitset
   {int  bitSize = 1;                                                                                                   // Number of bits in the bit set.
    boolean zero = false;                                                                                               // Able to locate zeros via a tree of bits if set
    boolean  one = false;                                                                                               // Able to locate ones via a tree of bits if set

    Build bitSize (int     BitSize ) {bitSize  = BitSize ; return this;}
    Build zero    (boolean Zero    ) {zero     = Zero    ; return this;}
    Build one     (boolean One     ) {one      = One     ; return this;}
    int byteSize()                                                                                                      // Bytes needed for the bitset and its bit trees
     {final int s = zero && one ? 3 : zero || one ? 2 : 1;                                                              // The number of blocks of bits required.  Need the base layer plus blocks for trees of bits to locate ones and/or zeroes
      return (Byte.SIZE - 1 + s * nextPowerOfTwo(bitSize)) / Byte.SIZE;
     }
   }

  public BitSet(Build Build)                                                                                            // Constructor
   {bitSize    = nextPowerOfTwo(Build.bitSize);                                                                         // Record size.
    bitSize1   = bitSize - 1;
    bitSize2   = bitSize >>> 1;
    if (bitSize < 2) stop("Size must be two or more, not:", bitSize);                                                   // There is not much point in bit sets with sizes of less than two.
    zero       = Build.zero;                                                                                            // Locate zeroes efficiently
    one        = Build.one;                                                                                             // Locate ones efficiently
    byteSize   = Build.byteSize();                                                                                      // Bytes needed for the bitset and its bit trees
    oneTreeBit = bitSize <= 2;                                                                                          // At most only one tree bit present
   }

  public BitSet(int BitSize)              {this(new Build().bitSize(BitSize));}                                         // Constructor to create a bitset without the ability locate zeroes or ones
  public BitSet(int BitSize, boolean One) {this(new Build().bitSize(BitSize).one(One));}                                // Constructor to create a bit set with optionally the ability to locate ones

  abstract void setByte(Int Index, byte Value);                                                                         // Write byte to storage backend.
  abstract byte getByte(Int Index);                                                                                     // Read byte from storage backend.

  public static int bytesNeeded(int Size)              {return new Build().bitSize(Size)         .byteSize();}          // Number of bytes needed for a bit set of specified size without the ability to locate zeroes or ones
  public static int bytesNeeded(int Size, boolean One) {return new Build().bitSize(Size).one(One).byteSize();}          // Number of bytes needed for a bit set of specified size with the ability to locate ones if specified.

  public  int size()                  {return bitSize;}                                                                 // Bit set size.
  private Int bitOffset(Int bitIndex) {return new Int(bitIndex.i() &   7);}                                             // Offset inside byte.
  private Int byteIndex(Int bitIndex) {return new Int(bitIndex.i() >>> 3);}                                             // Byte index in storage.

  private void checkIndex(Int Index)                                                                                    // Check that a bit index is valid
   {new If (Index.lt(0))
     {void Then() {stop("BitSet index cannot be negative:", Index);}
     };

    new If(Index.ge(bitSize))
     {void Then()
       {stop("BitSet index cannot be greater than or equal to:",
             Index, bitSize);
       }
     };
   }

  private void checkOne()                                                                                               // Check that we can search for ones in this bit set
   {new If (!one)
     {void Then()
       {stop("This bitset does have the ability to search for ones");
       }
     };
   }

  private void checkZero()                                                                                              // Check that we can search for zeroes in this bit set
   {new If (!zero)
     {void Then()
       {stop("This bitset does not have the ability to search for zeroes");
       }
     };
   }

  class Pos extends Int                                                                                                 // A position of a bit in the bit set
   {Int position() {return dup();}                                                                                      // Current position
    public Pos()             {super();}                                                                                 // Construct an invalid bit position
    public Pos(int Position) {super(Position);}                                                                         // Construct a valid bit position
    public Pos(Int Position) {super(Position);}                                                                         // Construct a bit position
    public String toString() {return "Pos: "+i();}                                                                      // Print a bit position
   }

//D2 Get and Set                                                                                                        // Get and set bits in the  bit tree setting the corresponding paths in the bits trees if necessary

  public Bool getBit(Pos Index)                                                                                         // Get bit value at an index after checking that the index is valid
   {checkIndex(Index.position());
    return getBitNC(Index);
   }

  private Bool getBitNC(Pos Index)                                                                                      // Get bit value at an index without checking that the index is valid
   {final Int bIndex = byteIndex(Index.position());                                                                     // Compute byte position.
    final Int offset = bitOffset(Index.position());                                                                     // Compute bit offset.

    final byte b = getByte(bIndex);                                                                                     // Load byte.
    return new Bool(((b >>> offset.i()) & 1) != 0);                                                                     // Extract bit.
   }

  void setBit(Pos Index, boolean Value)                                                                                 // Set bit value.
   {checkIndex(Index.position());
    setBitNC(Index, Value);                                                                                             // Set bit value without index checks
   }

  private void setBitNC(Pos Index, boolean Value)                                                                       // Set bit value without checking index
   {final Int bIndex = byteIndex(Index.position());                                                                     // Compute byte position.
    final Int offset = bitOffset(Index.position());                                                                     // Compute bit offset.
    final byte     b = getByte(bIndex);                                                                                 // Load byte.
    final int      i = offset.i();                                                                                      // Offset within byte
    setByte(bIndex, (byte) (Value ? b | (1 << i) : b & ~(1 << i)));                                                     // Modify bit.
   }

  public void clear(Pos Index) {set(Index, false);}                                                                     // Clear bit and corresponding path bits from the indexed bit to the root of the bit tree
  public void set  (Pos Index) {set(Index, true );}                                                                     // Set bit and corresponding path bits from the indexed bit to the root of the bit tree

  public void set  (Pos Index, boolean Value)                                                                           // Set or clear bits along the path from the indexed bit to the root of the bit tree
   {new If (getBit(Index).b() != Value)                                                                                 // Bit not already set to the correct value
     {void Then()
       {setBitNC(Index, Value);                                                                                         // Set the bit
        new If (one)  {void Then() {new If (Value) {void Then() {setOnePath (Index);} void Else() {clearOnePath (Index);}};}};
        new If (zero) {void Then() {new If (Value) {void Then() {setZeroPath(Index);} void Else() {clearZeroPath(Index);}};}};
       }
     };
   }

  void moveDownOneLayer(Int b, Int p, Int w) {b.down(); p.add(w); w.down();}                                            // Next layer down in a bit tree
  void moveUpOneLayer  (Int B, Int p, Int w) {w.up();   p.sub(w); B.up()  ;}                                            // Move up one layer in the bit tree

  void setOnePath(Pos Index)                                                                                            // Set bits along the path from the indexed bit to the root of the bit tree
   {checkOne();
    final Int b = Index.position();                                                                                     // Position in level
    final Int p = new Int(0);                                                                                           // Position in bits, width
    final Int w = new Int(bitSize);                                                                                     // Width

    new For(bitSize)                                                                                                    // Set bits along the path to the actual bit in the One tree
     {void body(Int Index, Bool c)
       {c.set();                                                                                                        // Complete early if we found a bit that does not need setting
        new If (p.ne(0))                                                                                                // Not on the actual bits
         {void Then()                                                                                                   // Not on the actual bits
           {final Pos q = new Pos(p.Add(b));                                                                            // Position in One tree
            new If (getBitNC(q))                                                                                        // Is the bit already set
             {void Then() {c.clear();}                                                                                  // Stop creating the path once we have arrived at a tree bit that is correctly set: as there are no changes at this level the upper levels must be ok too
              void Else() {setBitNC(q, true);}                                                                          // Flip the bit and continue
             };
           }
         };
        moveDownOneLayer(b, p, w);                                                                                      // Next level up
        c.and(()->{return w.gt(0);});                                                                                   // As long as we are in a valid level
       }
     };
   }

  private void clearOnePath(Pos Index)                                                                                  // Clear bits along the path from the indexed bit to the root of the bit tree
   {checkOne();
    final Int b = Index.position();                                                                                     // Position in level
    final Int p = new Int(0);                                                                                           // Position in bits, width
    final Int w = new Int(bitSize);                                                                                     // Width

    new For(bitSize)                                                                                                    // Step from root to leaf
     {void body(Int Index, Bool c)
       {final Int  B = b.Down();
        final Int  q = p.Add(w).add(B);
        final Int  Q = p.Add2(B);
        c.set();                                                                                                        // Complete early if we found a bit that does not need setting
        new If (B.Up().inc().lt(w).and(                                                                                 // Check both bits in the previous row are off
              ()->{return getBitNC(new Pos(Q      )).Flip();},
              ()->{return getBitNC(new Pos(Q.Inc())).Flip();}))
         {void Then()
           {final Pos r = new Pos(q);
            new If (getBitNC(r).Flip())
             {void Then() {c.clear();}                                                                                  // Bit is already correctly set so there is nothing more to do
              void Else() {setBitNC(r, false);}                                                                         // Clear set bit along path to root
             };
           }
         };
        moveDownOneLayer(b, p, w);                                                                                      // Next layer
        c.and(()->{return w.gt(0);});                                                                                   // As long as we are in a valid level
       }
     };
   }

  private Int addressZeroTree()                                                                                         // The zero tree will be held directly after the actual bits if there is no one tree, else beyond the one tree
   {final Int p = new Int(bitSize);
    new If (one) {void Then() {p.add(bitSize1);}};                                                                      // Address first bit of zero bit tree
    return p;
   }

  private void clearZeroPath(Pos Index)                                                                                 // Clear the target bit and set bits along the path from the indexed bit to the root of the bit tree
   {checkZero();
    final Int p = addressZeroTree();                                                                                    // Address zero bit tree
    final Int b = Index.position().Down();                                                                              // Position in layer
    final Int w = new Int(bitSize2);                                                                                    // Width of this layer

    new For(bitSize)                                                                                                    // Step from root to leaf
     {void body(Int Index, Bool c)
       {final Pos  q = new Pos(p.Add(b));
        c.set();
        new If (getBitNC(q))                                                                                            // Stop creating the path once we have arrived at a tree bit that is correctly set: as there are no changes at this level the upper levels must be ok too
         {void Then() {c.clear();}
          void Else() {setBitNC(q, true);}
         };
        moveDownOneLayer(b, p, w);                                                                                      // Next layer
        c.and(()->{return w.gt(0);});                                                                                   // As long as we are in a valid level
       }
     };
   }

  private void setZeroPath(Pos Index)                                                                                   // Set bits along the path from the indexed bit to the root of the bit tree unlkess thre is another path running through each bit
   {final Int w = new Int(bitSize2);                                                                                    // Width of child layer
    final Int p = addressZeroTree();                                                                                    // First child layer is the first layer of the zero bit tree
    final Int b = Index.position().Down();                                                                              // Index of bit in child layer
    final Int B = b.Up();                                                                                               // Position in layer above

    new If (getBitNC(new Pos(B)).and(
            ()->{return getBitNC(new Pos(B.Inc()));}))                                                                  // Check there is a zero
     {void Then()
       {final Pos r = new Pos(p.Add(b));                                                                                // Position in first layer of Zero tree
        new If (getBitNC(r))                                                                                            // Bit is not already correctly set to show no path so there is nothing more to do
         {void Then()
           {setBitNC(r, false);                                                                                         // Clear set bit along path to root to show no path

            new For(bitSize)                                                                                            // Step from root to leaf
             {void body(Int Index, Bool c)
               {final Int  P = p.dup();                                                                                 // Child layer becomes parent layer
                moveDownOneLayer(b, p, w);                                                                              // Index of bit in child layer, position in child layer, width of child layer
                final Int  Q = P.Add(b).add(b);
                c.set();                                                                                                // Complete early if we found a bit that does not need setting
                new If (getBitNC(new Pos(Q)).or(
                        ()->{return getBitNC(new Pos(Q.Inc()));}))
                 {void Then() {c.clear();}                                                                              // There is a one in the upper row so we do not need to clear further down
                  void Else()                                                                                           // Need to show that there are no ones in the upper row
                   {final Pos r = new Pos(p.Add(b));                                                                    // Bit to set
                    new If (getBitNC(r).Flip())
                     {void Then() {c.clear();}                                                                          // Bit is already correctly set so there is nothing more to do
                      void Else() {setBitNC(r, false);}                                                                 // Clear set bit along path to root
                     };
                   }
                 };
                c.and(()->{return w.gt(0);});                                                                           // As long as we are in a valid level
               }
             };
           }
         };
       }
     };
   }

  public void initialize()                                                                                              // Clear all bits.
   {new For(byteSize)                                                                                                   // Step from root to leaf
     {void body(Int i, Bool C)
       {setByte(i, (byte)0);
        C.set();
       }
     };

    new If (zero)                                                                                                       // Set all the bits to one in the paths in the zero tree if present to show that all the actual bits are zero
     {void Then()
       {final Int p = addressZeroTree();                                                                                // Position in level, level, width
        new For(bitSize)                                                                                                // For loop to set bits along path in One tree to actual bit
         {void body(Int i, Bool C)
           {setBitNC(new Pos(p.Add(i)), true);
            C.set();
           }
         };
       }
     };
   }

//D1 Locate Ones                                                                                                        // Find the first, last, next, previous bit set to one

  public Pos firstOne()                                                                                                 // Find the index of the first set bit
   {checkOne();
    final     Pos  p = new Pos(new Int(0));
    final Ref<Pos> r = new Ref<>();
    new If (getBit(p))
     {void Then() {r.set(p);         }
      void Else() {r.set(nextOne(p));}
     };
    return r.get();
   }

  public Pos lastOne()                                                                                                  // Find the index of the last set bit
   {checkOne();
    final Int l = new Int(bitSize1);

    final Ref<Pos> r = new Ref<>();
    final     Pos  p = new Pos(l);
    new If (getBit(p))
     {void Then() {r.set(        p );}
      void Else() {r.set(prevOne(p));}
     };
    return r.get();
   }

  Pos resultIfFound(Int R)
   {final Ref<Pos> r = new Ref<>();
    new If (R.valid())                                                                                                  // Result if found
     {void Then() {r.set(new Pos(R));}
      void Else() {r.set(new Pos( ));}
     };
    return r.get();
   }

  public Pos nextOne(Pos Index)                                                                                         // Find the index of the next set bit above the specified bit
   {checkOne();
    checkIndex(Index.position());

    final Int b = Index.position();                                                                                     // Position in layer
    final Int w = new Int(bitSize);                                                                                     // Width of layer
    final Int p = new Int(0);                                                                                           // Offset of layer
    final Int n = new Int();                                                                                            // The next element if it exists, offset of layer

    new For(bitSize)                                                                                                    // Traverse down through the tree
     {void body(Int i, Bool C)                                                                                          // Traverse down through the tree
       {final Int  c = new Int(b.Add(1));                                                                               // Is there a path down from the next bit?
        C.set();                                                                                                        // Whether we are done yet
        new If (c.lt(w).and(()->{return getBitNC(new Pos(p.Add(c)));}))                                                 // Found next up bit
         {void Then()
           {new For(i)                                                                                                  // Step down to the leaves
             {void body(Int j, Bool K)                                                                                  // Step down to the leaves
               {moveUpOneLayer(c, p, w);                                                                                // Move up to next layer
                c.add(getBitNC(new Pos(p.Add(c))).b() ? 0 : 1);                                                         // Follow path as low as possible
                K.set();                                                                                                // Continue the loop
               }
             };
            n.set(c); C.clear();                                                                                        // Found the next element
           }
          void Else()
           {moveDownOneLayer(b, p, w);
            new If (w.eq(0)) {void Then() {C.clear();}};                                                                // Address next level of bits further down in One tree
           }
         };
       }
     };
    return resultIfFound(n);                                                                                            // No alternate path down
   }

  public Pos prevOne(Pos Index)                                                                                         // Find the index of the previous set bit below the specified bit
   {checkOne();
    checkIndex(Index.position());
    final Int b = new Int(Index.position());                                                                            // Position in layer
    final Int w = new Int(bitSize);                                                                                     // Width of layer
    final Int p = new Int(0);                                                                                           // Offset of layer
    final Int R = new Int();                                                                                            // Result

    new If (b.ne(0))                                                                                                    // At the start so no previous bit
     {void Then()
       {new For(bitSize)                                                                                                // Step up throgh One bits
         {void body(Int i, Bool C)
           {final Int  B = new Int(b.Dec());                                                                            // Is there a path down from the next bit?
            C.set();                                                                                                    // Whether we have arrived at a bit that is already correctly set
            new If (b.gt(0).and(()->{return getBitNC(new Pos(p.Add(B)));}))                                             // Found next down bit
             {void Then()
               {new For(i)                                                                                              // Step down to the leaves
                 {void body(Int j, Bool K)
                   {moveUpOneLayer(B, p, w);
                    B.add(getBitNC(new Pos(p.Add(B).inc())).b() ? 1 : 0);                                               // Follow path to actual bits
                    K.set();
                   }
                 };
                R.set(B);                                                                                               // Save the result
                C.clear();                                                                                              // Finish the loop
               }
              void Else()
               {moveDownOneLayer(b, p, w);                                                                              // Address next level of bits in tree
                new If (w.eq(0)) {void Then() {C.clear();}};
               }
             };
           }
         };
       }
     };
    return resultIfFound(R);                                                                                            // Result if found
   }

//D1 Locate Zeros                                                                                                       // Find the first, last, next, previous bit set to zero

  public Pos firstZero()                                                                                                // Find the index of the first set bit
   {checkZero();
    final     Pos  p = new Pos(new Int(0));
    final Ref<Pos> r = new Ref<>();
    new If (getBit(p))
     {void Then() {r.set(nextZero(p));}
      void Else() {r.set(p);          }
     };
    return r.get();
   }

  public Pos lastZero()                                                                                                 // Find the index of the last set bit
   {checkZero();
    final     Int  l = new Int(bitSize1);
    final     Pos  p = new Pos(l);
    final Ref<Pos> r = new Ref<>();
    new If (getBit(p))
     {void Then() {r.set(prevZero(p));}
      void Else() {r.set(p);          }
     };
    return r.get();
   }

  public Pos nextZero(Pos Index)                                                                                        // Find the index of the next set bit above the specified bit
   {checkZero();
    checkIndex(Index.position());
    final Int R = new Int();                                                                                            // Result
    final Int b = new Int(Index.position());                                                                            // Offset in bits in the current layer
    new If (b.ne(bitSize1))                                                                                             // Not last bit so there might be a next bit
     {void Then()
       {final Int w = new Int(bitSize2);                                                                                // Current width
        final Int p = addressZeroTree();                                                                                // Position in bits
        final Pos q = new Pos(b.Inc());
        new If (getBit(q).Flip())                                                                                       // Next bit is zero
         {void Then()
           {R.set(q.i());                                                                                               // Location of next bit
           }
          void Else()
           {if (oneTreeBit) return;                                                                                     // No more bits to check because the bitset if trivially small
            b.down();                                                                                                   // First layer of zero tree bits

            new For(bitSize)                                                                                            // Search down through the zero bit tree
             {void body(Int i, Bool C)                                                                                  // Search down through the zero bit tree
               {final Int  B = new Int(b.Inc());                                                                        // Is there a path down from the next bit?
                C.set();                                                                                                // Whether we have arrived at a bit that is already correctly set
                new If (B.ge(w))
                 {void Then()
                   {C.clear();                                                                                          // Nothing beyond to search so no next zero
                   }
                  void Else()
                   {new If (getBitNC(new Pos(p.Add(B))))                                                                // Found next up bit
                     {void Then()
                       {new For(i)                                                                                      // Step down to the leaves
                         {void body(Int j, Bool K)                                                                      // Step down to the leaves
                           {moveUpOneLayer(B, p, w);                                                                    // Move up one layer
                            B.add(getBitNC(new Pos(p.Add(B))).b() ? 0 : 1);                                             // Follow path as low as possible
                            K.set();
                           }
                         };
                        final Int P = new Int(B.Add(B));                                                                // Position in parent layer
                        R.set(P.Add(getBit(new Pos(P)).b() ? 1 : 0));                                                     // Next zero bit from actual bits
                        C.clear();
                       }
                      void Else()
                       {moveDownOneLayer(b, p, w);                                                                      // Address next level of bits in tree
                        new If (w.eq(0)) {void Then() {C.clear();}};                                                    // Address next level of bits in tree
                       }
                     };
                   }
                 };
               }
             };
           }
         };
       }
     };
    return resultIfFound(R);                                                                                            // Result if found
   }

  public Pos prevZero(Pos Index)                                                                                        // Find the index of the previous set bit below the specified bit
   {final Int R = new Int();                                                                                            // Result
    checkZero();
    checkIndex(Index.position());

    final Int b = new Int(Index.position());
    new If (b.ne(0))                                                                                                    // Not the first bit so there might be a previous bit
     {void Then()
       {new If (new Bool(getBit(new Pos(b.Dec()))).flip())                                                              // Prev bit is zero
         {void Then()
           {R.set(b.Dec());
           }
          void Else()
           {if (oneTreeBit) return;                                                                                     // No more bits to check

            final Int w = new Int(bitSize2);                                                                            // Current width
            final Int p = addressZeroTree();                                                                            // Position in bits
            b.down();                                                                                                   // First layer of zero tree bits

            new For(bitSize)                                                                                            // Search down through the zero bit tree
             {void body(Int i, Bool C)                                                                                  // Search down through the zero bit tree
               {final Int  B = b.Dec();                                                                                 // Is there a path down from the next bit?
                C.set();                                                                                                // Whether we have arrived at a bit that is already correctly set
                new If (B.lt(0))                                                                                        // Nothing prior to search so no prev zero
                 {void Then()
                   {C.clear();
                   }
                  void Else()                                                                                           // More prior bits to search
                   {new If (getBitNC(new Pos(p.Add(B))))                                                                // Found next up bit
                     {void Then()
                       {new For(i)                                                                                      // Step down to the leaves
                         {void body(Int j, Bool K)                                                                      // Step down to the leaves
                           {b.up(); moveUpOneLayer(B, p, w);                                                            // Move up one layer
                            B.add(getBitNC(new Pos(p.Add(b))).b() ? 0 : 1);                                             // Follow path as high as possible
                            K.set();
                           }
                         };
                        final Int P = new Int(B.Add(B));                                                                // Parent row bits - low position
                        final Int Q = new Int(P.dup().inc());                                                           // Parent row bits - high position
                        R.set(P.add(!getBit(new Pos(Q)).b() ? 1 : 0));                                                  // Next zero bit from actual bits
                        C.clear();
                       }
                     };
                   }
                 };
                moveDownOneLayer(b, p, w);                                                                              // Address next level of bits in tree
                new If (w.eq(0)) {void Then() {C.clear();}};                                                            // Next layer exists
               }
             };
           }
         };
       }
     };
    return resultIfFound(R);                                                                                            // Result if found
   }

//D1 Full or empty                                                                                                      // Check whether a bit set is full or empty

  public Bool  full() {return firstZero().notValid();}                                                                  // If there are no zero bits then the bit set must be full
  public Bool empty() {return firstOne ().notValid();}                                                                  // If there are no one bits then the bit set must be empty

//D1 Integrity                                                                                                          // Check that the bit trees match the actual bits

  public boolean integrity() {return integrity(true);}                                                                  // Do an integrity check on the bitset to detect corruption

  public boolean integrity(boolean Stop)                                                                                // Do an integrity check on the bitset to detect corruption and stop on failures unless specified otherwise
   {final Build  build = new Build().bitSize(bitSize).one(one).zero(zero);                                              // Specify bit set
    final byte[] bytes = new byte[build.byteSize()];                                                                    // Allocate backing storage.

    final BitSet b = new BitSet(build)                                                                                  // Create an identical bitset
     {void setByte(Int Index, byte Value) {bytes[Index.i()] = Value;}                                                   // Backend write.
      byte getByte(Int Index)      {return bytes[Index.i()];}                                                           // Backend read.
     };

    b.initialize();
    for (int i : range(bitSize)) b.set(new Pos(i), getBit(new Pos(i)).b());                                             // Load bit set

    final String g = toString(), e = ""+b;
    if (!g.equals(e))                                                                                                   // Check that the current bit tree matches the expected bit tree
     {if (Stop)                                                                                                         // Normally we woudl stop and complain
       {ok(g, e);
        stop("Integrity failed in bit set:\n",
      "Got\n"     +  toString(),
      "Expected\n"+b.toString());
       }
      return false;
     }
    return true;
   }

//D1 Print                                                                                                              // Print the bit set

  public String toString()                                                                                              // Print levels in bit tree
   {final StringBuilder s = new StringBuilder();
    final Int p = new Int(0), r = new Int(bitSize);

    s.append("BitSet          ");                                                                                       // Title
    for   (int i : range(bitSize)) s.append(f(" %2d", i));                                                              // Positions of bits
    s.append("\n");

    for   (int i : range(1, bitSize))                                                                                   // Print the first line and the first bit tree if present
     {s.append(f("%4d %4d %4d |", i, p.i(), r.i()));
      for (int j : range(r.i()))                                                                                        // Bits in level
       {s.append(f("  %1d", getBitNC(new Pos(p.Add(j))).b() ? 1 : 0));
        if (!one && !zero) break;                                                                                       // Only print the first line if there are no tree bits
       }
      s.append("\n");
      if (i == 1)                                                                                                       // The first line is the actual bits
       {if      (one)  s.append("One:\n");                                                                              // One tree present so it comes next
        else if (zero) s.append("Zero:\n");                                                                             // Zero tree present and no One tree present so the zero tree comes next
       }
      p.add(r);
      r.down();
      if (r.eq(0).b()) break;                                                                                           // Reached the leaves
     }

    if (one && zero)                                                                                                    // Print zero search tree block if both one and zero bit trees are present
     {r.set(bitSize2);
      s.append("Zero:\n");
      for   (int i : range(1, bitSize))                                                                                 // Each level
       {s.append(f("%4d %4d %4d |", i, p.i(), r.i()));
        for (int j : range(r))                                                                                          // Bits in level
         {s.append(f("  %1d", getBitNC(new Pos(p.Add(j))).b() ? 1 : 0));
          if (!one && !zero) break;                                                                                     // Only print the first line if there are no tree bits
         }
        s.append("\n");
        p.add(r);
        r.down();
        if (r.eq(0).b()) break;                                                                                         // Reached the leaves
       }
     }
    return ""+s;
   }

//D1 Tests                                                                                                              // Tests

  static BitSet test_bits(int N, boolean One, boolean Zero)                                                             // Create test bitset.
   {final Build build = new Build().bitSize(N).one(One).zero(Zero);                                                     // Allocate backing storage.
    final byte[]bytes = new byte[build.byteSize()];                                                                     // Allocate backing storage.

    final BitSet b = new BitSet(build)                                                                                  // Create a bit set
     {void setByte(Int Index, byte Value) {bytes[Index.i()] = Value;}                                                   // Backend write.
      byte getByte(Int Index)      {return bytes[Index.i()];}                                                           // Backend read.
     };
    return b;                                                                                                           // Return test bitset.
   }

  static void test_bitSet()                                                                                             // Test bit manipulation.
   {final BitSet b = test_bits(23, true, false);                                                                        // Get test bitset.
    final int N = b.size();                                                                                             // Get logical size.

    for (int i = 0; i < N; i++) b.setBit(b.new Pos(b.new Int(i)), i % 2 == 0);                                          // Set alternating bits.

    ok(b.getBit(b.new Pos(b.new Int(4))), true);                                                                        // Verify bit 4.
    ok(b.getBit(b.new Pos(b.new Int(5))), false);                                                                       // Verify bit 5.
   }

  static void test_prevNext()                                                                                           // Test tree of searchable one bits
   {final BitSet b = test_bits(32, true, true);
    b.initialize();
    final int[]s = new int[]{13, 19, 24, 25, 26, 27, 28, 30, 31};

    for (int i : s) b.set(b.new Pos(b.new Int(i)), true);

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
                                ok(b.nextOne(b.new Pos(31)).notValid());

    for (int i : range(14))     ok(b.prevOne(b.new Pos(i)).notValid());
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
    for (int i : range(29, 32)) ok(b.nextZero(b.new Pos( i)).notValid());

                                ok(b.prevZero(b.new Pos(b.new Int(0))).notValid());
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

  static void test_prevNext01()                                                                                         // Test tree of searchable one bits
   {final int N = 16;
    final BitSet b = test_bits(N, true, true);
    b.initialize();
                                            //stop(b);
    ok(b, """
BitSet            0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16 |  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
One:
   2   16    8 |  0  0  0  0  0  0  0  0
   3   24    4 |  0  0  0  0
   4   28    2 |  0  0
   5   30    1 |  0
Zero:
   1   31    8 |  1  1  1  1  1  1  1  1
   2   39    4 |  1  1  1  1
   3   43    2 |  1  1
   4   45    1 |  1
""");
    for (int i : range(N))
     { b.set(b.new Pos(i), (i / 4) % 2 == 0);
     }

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

    for (int i : range( 3))     ok(b.nextOne(b.new Pos(i)).position(), b.new Int(i).inc());
    for (int i : range( 4,  8)) ok(b.nextOne(b.new Pos(i)).position(), 8);
    for (int i : range( 7, 11)) ok(b.nextOne(b.new Pos(i)).position(), b.new Int(i).inc());
    for (int i : range(11, 16)) ok(b.nextOne(b.new Pos(i)).notValid());

                                ok(b.prevOne(b.new Pos(b.new Int(0))).notValid());
    for (int i : range( 1,  5)) ok(b.prevOne(b.new Pos(i)).position(), b.new Int(i).dec());
    for (int i : range( 4,  9)) ok(b.prevOne(b.new Pos(i)).position(), 3);
    for (int i : range( 9, 12)) ok(b.prevOne(b.new Pos(i)).position(), b.new Int(i).dec());
    for (int i : range(12, 16)) ok(b.prevOne(b.new Pos(i)).position(), 11);
    ok(b.firstOne().position(),   0);
    ok(b.lastOne().position(),    11);

    for (int i : range( 3))     ok(b.nextZero(b.new Pos( i)).position(), 4);
    for (int i : range( 3,  7)) ok(b.nextZero(b.new Pos( i)).position(), b.new Int(i).inc());
    for (int i : range( 7, 11)) ok(b.nextZero(b.new Pos( i)).position(), 12);
    for (int i : range(11, 15)) ok(b.nextZero(b.new Pos( i)).position(), b.new Int(i).inc());
                                ok(b.nextZero(b.new Pos(15)).notValid());

    for (int i : range( 5))     ok(b.prevZero(b.new Pos( 0)).notValid());
    for (int i : range( 5,  9)) ok(b.prevZero(b.new Pos( i)).position(), b.new Int(i).dec());
    for (int i : range( 8, 12)) ok(b.prevZero(b.new Pos( i)).position(), 7);
    for (int i : range(13, 16)) ok(b.prevZero(b.new Pos( i)).position(), b.new Int(i).dec());
    ok(b.firstZero().position(),  4);
    ok(b.lastZero ().position(), 15);
   }

  static void test_prevNext10()                                                                                         // Test tree of searchable one bits
   {final int N = 16;
     final BitSet b = test_bits(N, true, true);
    b.initialize();
    for (int i : range(N)) b.set(b.new Pos(i), (i / 4) % 2 == 1);

                                            //stop(b);

    for (int i : range( 3))     ok(b.nextZero(b.new Pos(i)).position(), b.new Int(i).inc());
    for (int i : range( 3, 8))  ok(b.nextZero(b.new Pos(i)).position(), 8);
    for (int i : range( 7, 11)) ok(b.nextZero(b.new Pos(i)).position(), b.new Int(i).inc());
    for (int i : range(11, 16)) ok(b.nextZero(b.new Pos(i)).notValid());

                                ok(b.prevZero(b.new Pos(b.new Int(0))).notValid());
    for (int i : range( 1,  5)) ok(b.prevZero(b.new Pos(i)).position(), b.new Int(i).dec());
    for (int i : range( 4,  8)) ok(b.prevZero(b.new Pos(i)).position(), 3);
    for (int i : range( 9, 13)) ok(b.prevZero(b.new Pos(i)).position(), b.new Int(i).dec());
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

    for (int i : range( 4))     ok(b.nextOne(b.new Pos(b.new Int( i))).position(), 4);
    for (int i : range( 3,  7)) ok(b.nextOne(b.new Pos(b.new Int( i))).position(), b.new Int(i).inc());
    for (int i : range( 7, 12)) ok(b.nextOne(b.new Pos(b.new Int( i))).position(), 12);
    for (int i : range(11, 15)) ok(b.nextOne(b.new Pos(b.new Int( i))).position(), b.new Int(i).inc());
                                ok(b.nextOne(b.new Pos(b.new Int(15))).notValid());

    for (int i : range( 5))     ok(b.prevOne(b.new Pos(b.new Int( i))).notValid());
    for (int i : range( 5,  8)) ok(b.prevOne(b.new Pos(b.new Int( i))).position(), b.new Int(i).dec());
    for (int i : range( 8, 13)) ok(b.prevOne(b.new Pos(b.new Int( i))).position(), 7);
    for (int i : range(13, 16)) ok(b.prevOne(b.new Pos(b.new Int( i))).position(), b.new Int(i).dec());
    ok(b.firstOne().position(),   4);
    ok(b.lastOne().position(),    15);
   }

  static void test_integrity()
   {final int N = 8;                                                                                                    // Test size.
    final Build build = new Build().bitSize(N).one(true);                                                               // Allocate backing storage.
    final byte[]bytes = new byte[build.byteSize()];                                                                     // Allocate backing storage.

    final BitSet b = new BitSet(build)                                                                                  // Create a bit set using the backing storage
     {void setByte(Int Index, byte Value) {bytes[Index.i()] = Value;}                                                   // Backend write.
      byte getByte(Int Index)      {return bytes[Index.i()];}                                                           // Backend read.
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
    ok(""+s, """
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
     {ok(b.full().Flip());
      b.set(b.new Pos(i), true);
      ok(b.empty().Flip());
     }
    ok(b.full());
   }

  static void oldTests()                                                                                                // Tests thought to be stable.
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

  static void newTests()                                                                                                // Tests under development.
   {oldTests();                                                                                                         // Run baseline tests.
   }

  public static void main(String[] args)                                                                                // Program entry point for testing.
   {try                                                                                                                 // Protected execution block.
     {if (github_actions) oldTests(); else newTests();                                                                  // Select tests.
      if (coverageAnalysis) coverageAnalysis(12);                                                                       // Optional coverage analysis.
      testSummary();                                                                                                    // Summarize test results.
      System.exit(testsFailed);                                                                                         // Exit with status.
     }
     catch(Exception e)                                                                                                 // Exception reporting.
     {System.err.println(e);                                                                                            // Print exception.
      System.err.println(fullTraceBack(e));                                                                             // Print traceback.
      System.exit(1);                                                                                                   // Exit failure if an exception occurred.
     }
   }
 }
