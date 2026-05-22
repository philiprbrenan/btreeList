//----------------------------------------------------------------------------------------------------------------------
// Fixed size bit set which can locate set or cleared bits in log N time.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;                                                                                                     // Standard utility library.

public class BitSet extends Program                                                                                     // Abstract fixed-size bit set using byte-level storage.
 {final int bitSize, bitSize1, bitSize2, logBitSize;                                                                    // Number of bits in the bit set.
  final int byteSize;                                                                                                   // Number of bytes in the bit set.
  final boolean oneTreeBit;                                                                                             // At most only one tree bit present
  final boolean zero, one;                                                                                              // Able to locate zeros and ones via a tree of bits if set
  final ByteMemory.Ref memoryRef;                                                                                       // Memory to use
  static int bitsetNumbers = 0;                                                                                         // Bitsets created
  final  int bitsetNumber  = ++bitsetNumbers;                                                                           // Number of this bitset

//D1 Constructors                                                                                                       // Construct bit sets of various sizes with the optional ability of locating ones and zeros efficiently

  static class Build                                                                                                    // Specification of a bitset
   {int              bitSize = 1;                                                                                       // Number of bits in the bit set.
    boolean             zero = false;                                                                                   // Able to locate zeros via a tree of bits if set
    boolean              one = false;                                                                                   // Able to locate ones via a tree of bits if set
    boolean        immediate = true;                                                                                    // Immediate mode execution by default
    Program           parent = null;                                                                                    // Parent program whose code is to be written into.
    ByteMemory.Ref memoryRef = null;                                                                                    // Program memory to be used

    Build bitSize  (int     BitSize  ) {bitSize   = BitSize  ;    return this;}
    Build zero     (boolean Zero     ) {zero      = Zero     ;    return this;}
    Build one      (boolean One      ) {one       = One      ;    return this;}
    Build immediate(boolean Immediate) {immediate = Immediate;    return this;}
    Build memory   (Program.ByteMemory.Ref Ref) {memoryRef = Ref; return this;}
    Build parent   (Program Parent)    {parent    = Parent   ;    return this;}

    int byteSize()                                                                                                      // Bytes needed for the bitset and its bit trees
     {final int s = zero && one ? 3 : zero || one ? 2 : 1;                                                              // The number of blocks of bits required.  Need the base layer plus blocks for trees of bits to locate ones and/or zeroes
      return (Byte.SIZE - 1 + s * nextPowerOfTwo(bitSize)) / Byte.SIZE;
     }

    Program.Build build()                                                                                               // Description of containing program
     {final Program.Build p = new Program.Build();
      if (memoryRef == null) p.memory(byteSize());
      p.immediate(immediate);
      if (parent != null) p.parent(parent);                                                                             // Place code from this program into this parent program
      return p;
     }
   }

  public BitSet(Build Build)                                                                                            // Constructor
   {super(Build.build());
    bitSize    = nextPowerOfTwo(Build.bitSize);                                                                         // Record size.
    bitSize1   = bitSize - 1;
    bitSize2   = bitSize >>> 1;
    logBitSize = logTwo(bitSize);
    if (bitSize < 2) stop("Size must be two or more, not:", bitSize);                                                   // There is not much point in bit sets with sizes of less than two.
    zero       = Build.zero;                                                                                            // Locate zeroes efficiently
    one        = Build.one;                                                                                             // Locate ones efficiently
    byteSize   = Build.byteSize();                                                                                      // Bytes needed for the bitset and its bit trees
    oneTreeBit = bitSize <= 2;                                                                                          // At most only one tree bit present
    if (Build.memoryRef != null) memoryRef = Build.memoryRef;                                                           // Use memory supplied by caller and program owning memory
    else memoryRef = byteMemory.new Ref(0);                                                                             // Create a reference to the default memory

    new ForCount(bitSize)                                                                                               // Clear bitset at start
     {void body(Int I)
       {setBitNC(I, new Bool(false));
       }
     };

    new If (zero)                                                                                                       // Set all the bits to one in the paths in the zero tree if present to show that all the actual bits are zero
     {void Then()
       {final Int p = addressZeroTree();                                                                                // Position in level, level, width
        new ForCount(bitSize)                                                                                           // For loop to set bits along path in One tree to actual bit
         {void body(Int I)
           {setBitNC(p.Add(I), new Bool(true));
           }
         };
       }
     };
   }

  public BitSet(int BitSize)              {this(new Build().bitSize(BitSize));}                                         // Constructor to create a bitset without the ability locate zeroes or ones
  public BitSet(int BitSize, boolean One) {this(new Build().bitSize(BitSize).one(One));}                                // Constructor to create a bit set with optionally the ability to locate ones

  public static int bytesNeeded(int Size)              {return new Build().bitSize(Size)         .byteSize();}          // Number of bytes needed for a bit set of specified size without the ability to locate zeroes or ones
  public static int bytesNeeded(int Size, boolean One) {return new Build().bitSize(Size).one(One).byteSize();}          // Number of bytes needed for a bit set of specified size with the ability to locate ones if specified.

  public  int size() {return bitSize;}                                                                                  // Bit set size

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

  private void checkOne () {if (!one)stop("This bitset does have the ability to search for ones");}                     // Check that we can search for ones in this bit set
  private void checkZero() {if (!one)stop("This bitset does have the ability to search for zeroes");}                   // Check that we can search for zeroes in this bit set

//D2 Powers and Positions                                                                                               // Operations in numbers related to powers of two


/*
  0    1    2    3    4    5    6    7   8   9  10  11  12  13  14  15
 16        17        18        19       20      21      22      23
 24                  25                 26              27
 28                                     29
 30
*/

  Int top() {return new Int(2*bitSize-2);}                                                                              // Top of the ones tree - zero based

  void checkLow(Int Pos, int Low)                                                                                       // Check that we can step down
   {if (immediate())
     {checkOne();
      final int p = top().i(), P = Pos.i();                                                                             // Number of elements in the tree
      if (P < Low) stop("Position is below tree:", P);
      if (P > p  ) stop("Position is above tree:", P, p);
     }
   }

  Int nextDownHigh(Int Pos) {return nextDownLow(Pos).Inc();}                                                            // Given a bit value at an index after checking that the index is valid
  Int nextDownLow (Int Pos) {checkLow(Pos, bitSize); return Pos.Dec().mul(2).sub(top());}                               // Given a bit value at an index after checking that the index is valid
  Int nextUp      (Int Pos) {checkLow(Pos, 0);       return Pos.Add(top()).add(2).div(2);}                              // Given a bit value at an index after checking that the index is valid

  Int low(Int Pos)                                                                                                      // Find the lowest bit position with a one in it below the indicated subtree in the ones tree
   {checkLow(Pos, bitSize);                                                                                             // Check we are in the ones tree proper
    if (immediate() && getBitNC(Pos).Flip().b()) stop("Cannot go low from Pos:", Pos, this);                            // We can only step down from a one in the ones tree

    final Int p = new Int(Pos);                                                                                         // Position in ones tree
    new For(new Int(logBitSize))                                                                                        // Step down
     {void body(Int Index, Bool Continue)
       {final Int a = nextDownLow(p);                                                                                   // Lower level bit
        new If (getBitNC(a))
         {void Then()
           {p.set(a);
           }
          void Else()
           {p.set(nextDownHigh(p));                                                                                     // Must be a one because w started from a one and the left hand size was zero
           }
         };
        new If (p.ge(bitSize)) {void Then() {Continue.set();}};                                                         // Continue while we are in the ones tree
       }
     };
    return p;
   }

  Int high(Int Pos)                                                                                                     // Find the highest bit position with a one in it below the indicated subtree in the ones tree
   {checkLow(Pos, bitSize);
    if (immediate() && getBitNC(Pos).Flip().b()) stop("Cannot go high from Pos:",   Pos, this);                         // We can only step down from a one in the ones tree
    Int p = new Int(Pos);                                                                                               // Position in ones tree
    new For(new Int(logBitSize))                                                                                        // Step down
     {void body(Int Index, Bool Continue)
       {final Int b = nextDownHigh(p);                                                                                  // Lower level bit
        new If (getBitNC(b))
         {void Then()
           {p.set(b);
           }
          void Else()
           {p.set(nextDownLow(p));                                                                                      // Must be a one because w started from a one and the left hand size was zero
           }
         };
        new If (p.ge(bitSize)) {void Then() {Continue.set();}};                                                         // Continue while we are in the ones tree
       }
     };
    return p;
   }

  Bool canGoLeft(Int Pos)                                                                                               // Whether we can go left from the current position
   {checkLow(Pos, bitSize);
    if (immediate() && getBitNC(Pos).Flip().b()) stop("Cannot go low from Pos:", Pos, this);                            // We can only step down from a one in the ones tree
    return new Bool(getBitNC(nextDownLow(Pos)));
   }

  Bool canGoRight(Int Pos)                                                                                              // Whether we can go right from the current position
   {checkLow(Pos, bitSize);
    if (immediate() && getBitNC(Pos).Flip().b()) stop("Cannot go low from Pos:",  Pos, this);                           // We can only step down from a one in the ones tree
    return new Bool(getBitNC(nextDownHigh(Pos)));
   }

  Bool adjacentOnes(Int A, Int B)                                                                                       // Whether two ones in the ones tree are adjacent
   {final Bool r = new Bool();
    if (immediate() && getBitNC(A).Flip().b()) stop("Bitset entry  is not a one", A);
    if (immediate() && getBitNC(B).Flip().b()) stop("Bitset entry  is not a one", B);
    new If (A.eq(B))
     {void Then()
       {r.clear();
       }
      void Else()
       {new If (A.lt(B))
         {void Then() {r.set(nextOne(A).eq(B));}
          void Else() {r.set(nextOne(B).eq(A));}
         };
       }
     };
    return r;
   }

//D2 Full or empty                                                                                                      // Check whether a bit set is full or empty

  public Bool full()                                                                                                    // Whether the bitset is full
   {final Bool r = new Bool();
    if (zero)
     {new I() {void action() {r.ex(Bool.Ops.set, !getBitNC(2*bitSize-2+ (one ? bitSize1 : 0)));}};                      // There is a zeroes tree, so a zero at the apex of the zeroes tree indicates that the bit set is full
     }
    else r.set(firstZero().notValid());                                                                                 // There is no zeros tree - look for the first zero to check whether the bitset is full
    return r;
   }

  public Bool empty()                                                                                                   // Whether the bit set is empty
   {final Bool r = new Bool();
    if (one)
     {new I() {void action() {r.ex(Bool.Ops.set, !getBitNC(2*bitSize-2));}};                                            // There is a ones tree, so a zero at the apex of the ones tree indicates that the bit set is empty
     }
    else r.set(firstOne().notValid());                                                                                  // There is no ones tree - look for the first one to check whether the bitset is empty
    return r;
   }

//D2 Get and Set                                                                                                        // Get and set bits in the  bit tree setting the corresponding paths in the bits trees if necessary

  public Bool getBit(Int Index)                                                                                         // Get bit value at an index after checking that the index is valid
   {if (immediate()) checkIndex(Index);
    return getBitNC(Index);
   }

  Bool    getBitNC(Int Index) {return memoryRef.getBool(Index);}                                                        // Get bit value at an index without checking that the index is valid
  boolean getBitNC(int Index) {return memoryRef.getBool(Index);}                                                        // Get bit value at an index without checking that the index is valid

  void setBit  (Int Index, Bool Value) {memoryRef.putBool(Index, Value);}                                               // Set bit value.
  void setBitNC(Int Index, Bool Value) {memoryRef.putBool(Index, Value);}                                               // Set bit value without checking index

  public void clear(Int Index) {set(Index, new Bool(false));}                                                           // Clear bit and corresponding path bits from the indexed bit to the root of the bit tree
  public void set  (Int Index) {set(Index, new Bool(true ));}                                                           // Set bit and corresponding path bits from the indexed bit to the root of the bit tree

  public void set  (Int Index, Bool Value)                                                                              // Set or clear bits along the path from the indexed bit to the root of the bit tree
   {new If (getBit(Index).ne(Value))                                                                                    // Bit not already set to the correct value
     {void Then()
       {setBitNC(Index, Value);                                                                                         // Set the bit
        new If (one)  {void Then() {new If (Value) {void Then() {setOnePath (Index);} void Else() {clearOnePath (Index);}};}};
        new If (zero) {void Then() {new If (Value) {void Then() {setZeroPath(Index);} void Else() {clearZeroPath(Index);}};}};
       }
     };
   }

  void moveDownOneLayer(Int b, Int p, Int w) {b.down(); p.add(w); w.down();}                                            // Next layer down in a bit tree
  void moveUpOneLayer  (Int B, Int p, Int w) {w.up();   p.sub(w); B.up()  ;}                                            // Move up one layer in the bit tree

  void setOnePath(Int Index)                                                                                            // Set bits along the path from the indexed bit to the root of the bit tree
   {checkOne();
    final Int b = new Int(Index);                                                                                       // Position in level
    final Int p = new Int(0);                                                                                           // Position in bits, width
    final Int w = new Int(bitSize);                                                                                     // Width

    new For(bitSize)                                                                                                    // Set bits along the path to the actual bit in the One tree
     {void body(Int Index, Bool c)
       {c.set();                                                                                                        // Complete early if we found a bit that does not need setting
        new If (p.ne(0))                                                                                                // Not on the actual bits
         {void Then()                                                                                                   // Not on the actual bits
           {final Int q = new Int(p.Add(b));                                                                            // Position in One tree
            new If (getBitNC(q))                                                                                        // Is the bit already set
             {void Then() {c.clear();}                                                                                  // Stop creating the path once we have arrived at a tree bit that is correctly set: as there are no changes at this level the upper levels must be ok too
              void Else() {setBitNC(q, new Bool(true));}                                                                // Flip the bit and continue
             };
           }
         };
        moveDownOneLayer(b, p, w);                                                                                      // Next level up
        c.and(w.gt(0));                                                                                                 // As long as we are in a valid level
       }
     };
   }

  private void clearOnePath(Int Index)                                                                                  // Clear bits along the path from the indexed bit to the root of the bit tree
   {checkOne();
    final Int b = new Int(Index);                                                                                       // Position in level
    final Int p = new Int(0);                                                                                           // Position in bits, width
    final Int w = new Int(bitSize);                                                                                     // Width

    new For(bitSize)                                                                                                    // Step from root to leaf
     {void body(Int Index, Bool c)
       {final Int  B = b.Down();
        final Int  q = p.Add(w).add(B);
        final Int  Q = p.Add2(B);
        c.set();                                                                                                        // Complete early if we found a bit that does not need setting
        new If (B.Up().inc().lt(w).and(getBitNC(Q).Flip(), getBitNC(Q.Inc()).Flip()))                                   // Check both bits in the previous row are off
         {void Then()
           {final Int r = new Int(q);
            new If (getBitNC(r).Flip())
             {void Then() {c.clear();}                                                                                  // Bit is already correctly set so there is nothing more to do
              void Else() {setBitNC(r, new Bool(false));}                                                               // Clear set bit along path to root
             };
           }
         };
        moveDownOneLayer(b, p, w);                                                                                      // Next layer
        c.and(w.gt(0));                                                                                                 // As long as we are in a valid level
       }
     };
   }

  private Int addressZeroTree()                                                                                         // The zero tree will be held directly after the actual bits if there is no one tree, else beyond the one tree
   {final Int p = new Int(bitSize);
    new If (one) {void Then() {p.add(bitSize1);}};                                                                      // Address first bit of zero bit tree
    return p;
   }

  private void clearZeroPath(Int Index)                                                                                 // Clear the target bit and set bits along the path from the indexed bit to the root of the bit tree
   {checkZero();
    final Int p = new Int(addressZeroTree());                                                                           // Address zero bit tree
    final Int b = new Int(Index.Down());                                                                                // Position in layer
    final Int w = new Int(bitSize2);                                                                                    // Width of this layer

    new For(bitSize)                                                                                                    // Step from root to leaf
     {void body(Int Index, Bool c)
       {final Int  q = new Int(p.Add(b));
        c.set();
        new If (getBitNC(q))                                                                                            // Stop creating the path once we have arrived at a tree bit that is correctly set: as there are no changes at this level the upper levels must be ok too
         {void Then() {c.clear();}
          void Else() {setBitNC(q, new Bool(true));}
         };
        moveDownOneLayer(b, p, w);                                                                                      // Next layer
        c.and(w.gt(0));                                                                                                 // As long as we are in a valid level
       }
     };
   }

  private void setZeroPath(Int Index)                                                                                   // Set bits along the path from the indexed bit to the root of the bit tree unless there is another path running through each bit
   {final Int w = new Int(bitSize2);                                                                                    // Width of child layer
    final Int p = new Int(addressZeroTree());                                                                           // First child layer is the first layer of the zero bit tree
    final Int b = new Int(Index.Down());                                                                                // Index of bit in child layer
    final Int B = new Int(b.Up());                                                                                      // Position in layer above

    new If (getBitNC(new Int(B)).and(getBitNC(new Int(B.Inc()))))                                                       // Check there is a zero
     {void Then()
       {final Int r = new Int(p.Add(b));                                                                                // Position in first layer of Zero tree
        new If (getBitNC(r))                                                                                            // Bit is not already correctly set to show no path so there is nothing more to do
         {void Then()
           {setBitNC(r, new Bool(false));                                                                               // Clear set bit along path to root to show no path

            new For(bitSize)                                                                                            // Step from root to leaf
             {void body(Int Index, Bool c)
               {final Int  P = p.dup();                                                                                 // Child layer becomes parent layer
                moveDownOneLayer(b, p, w);                                                                              // Index of bit in child layer, position in child layer, width of child layer
                final Int  Q = P.Add(b).add(b);
                c.set();                                                                                                // Complete early if we found a bit that does not need setting
                new If (getBitNC(new Int(Q)).or(getBitNC(new Int(Q.Inc()))))
                 {void Then() {c.clear();}                                                                              // There is a one in the upper row so we do not need to clear further down
                  void Else()                                                                                           // Need to show that there are no ones in the upper row
                   {final Int r = new Int(p.Add(b));                                                                    // Bit to set
                    new If (getBitNC(r).Flip())
                     {void Then() {c.clear();}                                                                          // Bit is already correctly set so there is nothing more to do
                      void Else() {setBitNC(r, new Bool(false));}                                                       // Clear set bit along path to root
                     };
                   }
                 };
                c.and(w.gt(0));                                                                                         // As long as we are in a valid level
               }
             };
           }
         };
       }
     };
   }

//D1 Locate Ones                                                                                                        // Find the first, last, next, previous bit set to one

  public Int firstOne()                                                                                                 // Find the index of the first set bit
   {checkOne();
    final Int p = new Int(0);
    final Int r = new Int();

    new If (getBit(p))
     {void Then() {r.set(p);          }
      void Else() {r.copy(nextOne(p));}
     };
    return r;                                                                                                           // Result is valid if found
   }

  public Int lastOne()                                                                                                  // Find the index of the last set bit
   {checkOne();
    final Int p = new Int(bitSize1);
    final Int r = new Int();
    new If (getBit(p))
     {void Then() {r.set(p);}
      void Else() {r.copy(prevOne(p));}
     };
    return r;                                                                                                           // Result is valid if found
   }

  public Int nextOne(Int Start)                                                                                         // Find the index of the next set bit above the specified start bit
   {checkOne();
    if (immediate()) checkIndex(Start);
    final Int Next = new Int();                                                                                         // Invalid indicates not found

    final Int b = new Int(Start);                                                                                       // Position in layer
    final Int w = new Int(bitSize);                                                                                     // Width of layer
    final Int p = new Int(0);                                                                                           // Offset of layer

    new For(bitSize)                                                                                                    // Traverse down through the tree
     {void body(Int i, Bool C)                                                                                          // Traverse down through the tree
       {final Int c = new Int(b.Add(1));                                                                                // Is there a path down from the next bit?
        C.set();                                                                                                        // Whether we are done yet

        new If (c.lt(w).and(getBitNC(new Int(p.Add(c)))))                                                               // Found next up bit
         {void Then()
           {new ForCount(i)                                                                                             // Step down to the leaves
             {void body(Int j)                                                                                          // Step down to the leaves
               {moveUpOneLayer(c, p, w);                                                                                // Move up to next layer
                new If (getBitNC(new Int(p.Add(c))).flip())                                                             // Follow path as low as possible
                 {void Then() {c.inc();}
                 };
               }
             };
            Next.set(c); C.clear();                                                                                     // Found the next element
           }
          void Else()
           {moveDownOneLayer(b, p, w);
            new If (w.eq(0)) {void Then() {C.clear();}};                                                                // Address next level of bits further down in One tree
           }
         };
       }
     };
    return Next;                                                                                                        // Result is valid if found
   }

  public Int prevOne(Int Start)                                                                                         // Find the index of the previous set bit below the specified bit
   {checkOne();
    if (immediate()) checkIndex(Start);
    final Int Prev = new Int();                                                                                         // Invalid indicates not found
    final Int b = new Int(Start);                                                                                       // Position in layer
    final Int w = new Int(bitSize);                                                                                     // Width of layer
    final Int p = new Int(0);                                                                                           // Offset of layer

    new If (b.ne(0))                                                                                                    // At the start so no previous bit
     {void Then()
       {new For(bitSize)                                                                                                // Step up through One bits
         {void body(Int i, Bool C)
           {final Int  B = new Int(b.Dec());                                                                            // Is there a path down from the next bit?
            C.set();                                                                                                    // Whether we have arrived at a bit that is already correctly set
            new If (b.gt(0).and(getBitNC(new Int(p.Add(B)))))                                                           // Found next down bit
             {void Then()
               {new For(i)                                                                                              // Step down to the leaves
                 {void body(Int j, Bool K)
                   {moveUpOneLayer(B, p, w);
                    new If (getBitNC(new Int(p.Add(B).inc())))                                                          // Follow path as low as possible
                     {void Then() {B.inc();}
                     };
                    K.set();
                   }
                 };
                Prev.set(B);                                                                                            // Save the result
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
    return Prev;                                                                                                        // Result is valid if found
   }

//D1 Locate Zeros                                                                                                       // Find the first, last, next, previous bit set to zero

  public Int firstZero()                                                                                                // Find the index of the first set bit
   {checkZero();
    final Int p = new Int(0);
    final Int r = new Int();
    new If (getBit(p))
     {void Then() {r.copy(nextZero(p));}
      void Else() {r.set(p);          }
     };
    return r;                                                                                                           // Result is valid if found
   }

  public Int lastZero()                                                                                                 // Find the index of the last set bit
   {checkZero();
    final Int p = new Int(bitSize1);
    final Int r = new Int();
    new If (getBit(p))
     {void Then() {r.copy(prevZero(p));}
      void Else() {r.set(p);          }
     };
    return r;                                                                                                           // Result is valid if found
   }

  public Int nextZero(Int Start)                                                                                        // Find the index of the next set bit above the specified bit
   {checkZero();
    if (immediate()) checkIndex(Start);
    final Int Next = new Int();                                                                                         // Invalid indicates not found

    final Int b = new Int(Start);                                                                                       // Offset in bits in the current layer
    new If (b.ne(bitSize1))                                                                                             // Not last bit so there might be a next bit
     {void Then()
       {final Int w = new Int(bitSize2);                                                                                // Current width
        final Int p = addressZeroTree();                                                                                // Position in bits
        final Int q = new Int(b.Inc());
        new If (getBit(q).Flip())                                                                                       // Next bit is zero
         {void Then()
           {Next.set(q);                                                                                                // Location of next bit
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
                   {new If (getBitNC(new Int(p.Add(B))))                                                                // Found next up bit
                     {void Then()
                       {new ForCount(i)                                                                                 // Step down to the leaves
                         {void body(Int J)                                                                              // Step down to the leaves
                           {moveUpOneLayer(B, p, w);                                                                    // Move up one layer
                            new If (getBitNC(new Int(p.Add(B))).flip())                                                 // Follow path as low as possible
                             {void Then() {B.inc();}
                             };
                           }
                         };
                        final Int P = new Int(new Int(B.Add(B)));                                                       // Address next level of bits in tree
                        new If (getBit(P))                                                                              // Next zero bit from actual bits
                         {void Then() {Next.set(P).inc();}
                          void Else() {Next.set(P);}
                         };
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
    return Next;                                                                                                        // Result is valid if found
   }

  public Int prevZero(Int Start)                                                                                        // Find the index of the previous set bit below the specified bit
   {checkZero();
    if (immediate()) checkIndex(Start);
    final Int Prev = new Int();                                                                                         // Invalid indicates not found

    final Int b = new Int(Start);
    new If (b.ne(0))                                                                                                    // Not the first bit so there might be a previous bit
     {void Then()
       {new If (new Bool(getBit(new Int(b.Dec()))).flip())                                                              // Prev bit is zero
         {void Then()
           {Prev.set(b.Dec());
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
                   {new If (getBitNC(new Int(p.Add(B))))                                                                // Found next up bit
                     {void Then()
                       {new For(i)                                                                                      // Step down to the leaves
                         {void body(Int j, Bool K)                                                                      // Step down to the leaves
                           {b.up(); moveUpOneLayer(B, p, w);                                                            // Move up one layer
                            new If (getBitNC(new Int(p.Add(b))).flip())                                                 // Follow path as high as possible
                             {void Then() {B.inc();}
                             };
                            K.set();
                           }
                         };
                        final Int P = new Int(B.Add(B));                                                                // Parent row bits - low position
                        final Int Q = new Int(P.dup().inc());                                                           // Parent row bits - high position
                        new If (getBit(new Int(Q)).flip())                                                              // Prev zero bit from actual bits
                         {void Then() {Prev.set(P.inc());}
                          void Else() {Prev.set(P);}
                         };
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
    return Prev;                                                                                                        // Result if found
   }

//D1 Statistics                                                                                                         // Count the number of ones or zero bits in a bit set

  public Int countOnes()                                                                                                // Count ones in bitset
   {final Int c = new Int(0);                                                                                           // Count
    final Int p = firstOne();                                                                                           // First one
    new For(new Int(bitSize))                                                                                           // Step from one to one
     {void body(Int Index, Bool Continue)
       {new If (p.valid())                                                                                              // Latest step is valid
         {void Then()
           {c.inc();
            final Int q = nextOne(p);
            new If (q.valid())                                                                                          // Step to next one
             {void Then()
               {p.set(q);
                Continue.set(true);                                                                                     // Continue stepping
               }
             };
           }
         };
       }
     };
    return c;
   };

  public Int countZeros()                                                                                               // Count zeros in bitset
   {final Int c = new Int(0);                                                                                           // Count
    final Int p = firstZero();
    new For(new Int(bitSize))
     {void body(Int Index, Bool Continue)
       {new If (p.valid())
         {void Then()
           {c.inc();                                                                                                    // Count zeros
            final Int q = nextZero(p);                                                                                  // Next zero
            new If (q.valid())
             {void Then()
               {p.set(q);
                Continue.set(true);                                                                                     // Continue stepping
               }
             };
           }
         };
       }
     };
    return c;
   };

//D1 Print                                                                                                              // Print the bit set

  public String toString()                                                                                              // Print bit set so we can visualize it. This will not be available on the chip so we use normal Java
   {final StringBuilder s = new StringBuilder();
    int p = 0, r = bitSize;

    s.append("BitSet          ");                                                                                       // Title
    for   (int i : range(bitSize)) s.append(f(" %2d", i));                                                              // Positions of bits
    s.append("\n");

    for   (int i : range(1, bitSize))                                                                                   // Print the first line and the first bit tree if present
     {s.append(f("%4d %4d %4d |", i, p, r));
      for (int j : range(r))                                                                                            // Bits in level
       {s.append(f("  %1d", memoryRef.getBool(p + j) ? 1 : 0));
        if (!one && !zero) break;                                                                                       // Only print the first line if there are no tree bits
       }
      s.append("\n");
      if (i == 1)                                                                                                       // The first line is the actual bits
       {if      (one)  s.append("One:\n");                                                                              // One tree present so it comes next
        else if (zero) s.append("Zero:\n");                                                                             // Zero tree present and no One tree present so the zero tree comes next
       }
      p += r;
      r >>>= 1;
      if (r ==  0) break;                                                                                               // Reached the leaves
     }

    if (one && zero)                                                                                                    // Print zero search tree block if both one and zero bit trees are present
     {r = bitSize2;
      s.append("Zero:\n");
      for   (int i : range(1, bitSize))                                                                                 // Each level
       {s.append(f("%4d %4d %4d |", i, p, r));
        for (int j : range(r))                                                                                          // Bits in level
         {s.append(f("  %1d", memoryRef.getBool(p + j) ? 1 : 0));
          if (!one && !zero) break;                                                                                     // Only print the first line if there are no tree bits
         }
        s.append("\n");
        p += r;
        r >>>= 1;
        if (r == 0) break;                                                                                              // Reached the leaves
       }
     }
    return ""+s;
   }

//D1 Tests                                                                                                              // Tests

  static BitSet test_bits(boolean Ex, int N, boolean One, boolean Zero)                                                 // Create test bitset.
   {final Build build = new Build().bitSize(N).one(One).zero(Zero).immediate(Ex);                                       // Allocate backing storage.
    final byte[]bytes = new byte[build.byteSize()];                                                                     // Allocate backing storage.
    final BitSet    b = new BitSet(build);                                                                              // Create a bit set
    return b;                                                                                                           // Return test bitset.
   }

  static void test_bitSet(boolean Ex)                                                                                   // Test bit manipulation.
   {final BitSet b = test_bits(Ex, 23, true, false);                                                                    // Get test bitset.
    final int N = b.size();                                                                                             // Get logical size.
    for (int i = 0; i < N; i++) b.setBit(b.new Int(i), b.new Bool(i % 2 == 0));                                         // Set alternating bits.
    final Bool b4 = b.getBit(b.new Int(b.new Int(4)));                                                                  // Get bit 4.
    final Bool b5 = b.getBit(b.new Int(b.new Int(5)));                                                                  // Get bit 5.
    b.put(b4);                                                                                                          // Verify bit 4.
    b.put(b5);                                                                                                          // Verify bit 5.
    b.execute();

    //stop(b.output());
    ok(b.output(), """
true
false
""");
   }

  static void test_bitSet()                                                                                             // Test bit manipulation.
   {test_bitSet(true);
    test_bitSet(false);
   }

  static void test_prevNext(boolean Ex)                                                                                 // Test tree of searchable one bits
   {final BitSet b = test_bits(Ex, 32, true, true);
    b.maxSteps = 99999;
    final int[]s = new int[]{13, 19, 24, 25, 26, 27, 28, 30, 31};

    for (int i : s) b.set(b.new Int(b.new Int(i)), b.new Bool(true));
    b.ok(()->b, """
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

    final Int o = b.countOnes ().ok( 9);
    final Int z = b.countZeros().ok(23);

    b.adjacentOnes(b.new Int(13), b.new Int(19)).ok(true);
    b.adjacentOnes(b.new Int(13), b.new Int(24)).ok(false);
    b.adjacentOnes(b.new Int(24), b.new Int(19)).ok(true);
    b.adjacentOnes(b.new Int(25), b.new Int(19)).ok(false);
    b.adjacentOnes(b.new Int(25), b.new Int(25)).ok(false);

    if (true)
     {final Int q;
      q = b.prevZero(b.new Int(14));
      b.ok(()->q.i(), 12);
     }

    for (int i : range(13))     b.nextOne(b.new Int( i)).ok( 13);
    for (int i : range(13, 19)) b.nextOne(b.new Int( i)).ok( 19);
    for (int i : range(19, 24)) b.nextOne(b.new Int( i)).ok( 24);
    for (int i : range(23, 28)) b.nextOne(b.new Int( i)).ok(i+1);
                                b.nextOne(b.new Int(28)).ok( 30);
                                b.nextOne(b.new Int(29)).ok( 30);
                                b.nextOne(b.new Int(30)).ok( 31);
                 {final Int q = b.nextOne(b.new Int(31)); b.ok(()->q.v(), false);}

    for (int i : range(14))     {final Int q = b.prevOne(b.new Int( i)); b.ok(()->q.v(), false);}

    for (int i : range(14, 20)) b.prevOne(b.new Int( i)).ok( 13);
    for (int i : range(20, 24)) b.prevOne(b.new Int( i)).ok( 19);
    for (int i : range(25, 29)) b.prevOne(b.new Int( i)).ok(i-1);
                                b.prevOne(b.new Int(30)).ok( 28);
                                b.prevOne(b.new Int(31)).ok( 30);

                 {final Int q = b.firstOne().ok(13);}
                 {final Int q = b. lastOne().ok(31);}

    for (int i : range(12))     b.nextZero(b.new Int( i)).ok(i+1);
                                b.nextZero(b.new Int(12)).ok( 14);
    for (int i : range(13, 18)) b.nextZero(b.new Int( i)).ok(i+1);
    for (int i : range(19, 23)) b.nextZero(b.new Int( i)).ok(i+1);
    for (int i : range(23, 28)) b.nextZero(b.new Int( i)).ok( 29);
    for (int i : range(29, 32)) {final Int q = b.nextZero(b.new Int( i)); b.ok(()->q.v(), false);}


                                {final Int q = b.prevZero(b.new Int( 0)); b.ok(()->q.v(), false);}
    for (int i : range( 1, 14)) b.prevZero(b.new Int( i)).ok(i-1);
                                b.prevZero(b.new Int(14)).ok( 12);
    for (int i : range(15, 19)) b.prevZero(b.new Int( i)).ok(i-1);
                                b.prevZero(b.new Int(20)).ok( 18);
    for (int i : range(21, 24)) b.prevZero(b.new Int( i)).ok(i-1);
    for (int i : range(24, 30)) b.prevZero(b.new Int( i)).ok( 23);
    for (int i : range(30, 32)) b.prevZero(b.new Int( i)).ok( 29);

                                b.firstZero().ok( 0);
                                b. lastZero().ok(29);

    b.execute();
   }

  static void test_prevNext()                                                                                           // Test tree of searchable one bits
   {test_prevNext(true);
    test_prevNext(false);
   }

  static void test_prevNext01(boolean Ex)                                                                               // Test tree of searchable one bits
   {final int N = 16;
    final BitSet b = test_bits(Ex, N, true, true);

    b.ok(()->b, """
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
    for (int i : range(N)) b.set(b.new Int(i), b.new Bool((i / 4) % 2 == 0));
    //stop(b);
    b.ok(()->b, """
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

    for (int i : range( 3))     b.nextOne(b.new Int(i)).ok(i+1);
    for (int i : range( 4,  8)) b.nextOne(b.new Int(i)).ok(  8);
    for (int i : range( 7, 11)) b.nextOne(b.new Int(i)).ok(i+1);
    for (int i : range(11, 16)) {final Int q = b.nextOne(b.new Int(i)); b.ok(()->q.v(),  false);}

                                {final Int q = b.prevOne(b.new Int(0)); b.ok(()->q.v(),  false);}
    for (int i : range( 1,  5)) b.prevOne(b.new Int(i)).ok(i-1);
    for (int i : range( 4,  9)) b.prevOne(b.new Int(i)).ok(  3);
    for (int i : range( 9, 12)) b.prevOne(b.new Int(i)).ok(i-1);
    for (int i : range(12, 16)) b.prevOne(b.new Int(i)).ok( 11);
                                b.firstOne()           .ok(  0);
                                b.lastOne ()           .ok( 11);

    for (int i : range( 3))     b.nextZero(b.new Int( i)).ok(  4);
    for (int i : range( 3,  7)) b.nextZero(b.new Int( i)).ok(i+1);
    for (int i : range( 7, 11)) b.nextZero(b.new Int( i)).ok( 12);
    for (int i : range(11, 15)) b.nextZero(b.new Int( i)).ok( i+1);
                                {final Int q = b.nextZero(b.new Int(15)); b.ok(()->q.v(), false);}

    for (int i : range( 5))     {final Int q = b.prevZero(b.new Int( i)); b.ok(()->q.v(), false);}
    for (int i : range( 5,  9)) b.prevZero(b.new Int( i)).ok(i-1);
    for (int i : range( 8, 12)) b.prevZero(b.new Int( i)).ok(  7);
    for (int i : range(13, 16)) b.prevZero(b.new Int( i)).ok(i-1);
                                b.firstZero()            .ok(  4);
                                b.lastZero ()            .ok( 15);
   }

  static void test_prevNext01()                                                                                         // Test tree of searchable one bits
   {test_prevNext01(true);
    test_prevNext01(false);
   }

  static void test_prevNext10(boolean Ex)                                                                               // Test tree of searchable one bits
   {final int N = 16;
    final BitSet b = test_bits(Ex, N, true, true);
    for (int i : range(N)) b.set(b.new Int(i), b.new Bool((i / 4) % 2 == 1));

   //stop(b);

    b.ok(()->b, """
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

    for (int i : range( 3))     b.nextZero(b.new Int(i)).ok(i+1);
    for (int i : range( 3, 8))  b.nextZero(b.new Int(i)).ok(  8);
    for (int i : range( 7, 11)) b.nextZero(b.new Int(i)).ok(i+1);
    for (int i : range(11, 16)) {final Int q = b.nextZero(b.new Int(i)); b.ok(()->q.v(), false);}

                                {final Int q = b.prevZero(b.new Int(0)); b.ok(()->q.v(), false);}
    for (int i : range( 1,  5)) b.prevZero(b.new Int(i)).ok(i-1);
    for (int i : range( 4,  8)) b.prevZero(b.new Int(i)).ok(  3);
    for (int i : range( 9, 13)) b.prevZero(b.new Int(i)).ok(i-1);
    for (int i : range(12, 16)) b.prevZero(b.new Int(i)).ok( 11);

    for (int i : range( 4))     b.nextOne(b.new Int( i)).ok(  4);
    for (int i : range( 3,  7)) b.nextOne(b.new Int( i)).ok(i+1);
    for (int i : range( 7, 12)) b.nextOne(b.new Int( i)).ok( 12);
    for (int i : range(11, 15)) b.nextOne(b.new Int( i)).ok(i+1);
                                {final Int q = b.nextOne(b.new Int(15)); b.ok(()->q.v(), false);}

    for (int i : range( 5))     {final Int q = b.prevOne(b.new Int( i)); b.ok(()->q.v(), false);}
    for (int i : range( 5,  8)) b.prevOne(b.new Int( i)).ok(i-1);
    for (int i : range( 8, 13)) b.prevOne(b.new Int( i)).ok(  7);
    for (int i : range(13, 16)) b.prevOne(b.new Int( i)).ok(i-1);
                                b.firstOne()            .ok(  4);
                                b.lastOne ()            .ok( 15);
   }

  static void test_prevNext10()                                                                                         // Test tree of searchable one bits
   {test_prevNext10(true);
    test_prevNext10(false);
   }

  static void test_oneZero(boolean Ex)
   {final int N = 8;
    final BitSet b = test_bits(Ex, N, true, true);
    final StringBuilder s = new StringBuilder();
    b.new I() {void action() {s.append("Start:\n"+b);}};

    for (int i : range(N))
     {b.set(b.new Int(i), b.new Bool(true));
      b.new I() {void action() {s.append("Set: "+i+"\n"+b);}};
     }
    for (int i : range(N))
     {b.set(b.new Int(i), b.new Bool(false));
      b.new I() {void action() {s.append("Clear: "+i+"\n"+b);}};
     }
    b.execute();
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

  static void test_oneZero()                                                                                            // Test tree of searchable one bits
   {test_oneZero(true);
    test_oneZero(false);
   }

  static void test_fullEmpty(boolean Ex)
   {final int    N = 16;
    final BitSet b = test_bits(Ex, N, true, true);
    final Bool  e1 = b.empty(); b.ok(()->e1,  true);

    b.new ForCount(b.new Int(N))
     {void body(Int Index)
       {b.full ().ok(false);
        b.set(b.new Int(Index), b.new Bool(true));
        b.empty().ok(false);
       }
     };
    final Bool f1 = b.full(); b.ok(()->f1, true);
    b.execute();
   }

  static void test_fullEmpty()                                                                                          // Test tree of searchable one bits
   {test_fullEmpty(true);
    test_fullEmpty(false);
   }

/*
  0    1    2    3    4    5    6    7   8   9  10  11  12  13  14  15
 16        17        18        19       20      21      22      23
 24                  25                 26              27
 28                                     29
 30
*/

  static void test_powerPos(boolean Ex)                                                                                           // Test tree of searchable one bits
   {final int N = 16;
    final BitSet b = test_bits(Ex, N, true, true);

    for (int i : range(N)) if ((i > 4 && i < 8) || (i > 10 && i < 12)) b.set(b.new Int(i), b.new Bool(true));

    final Int t = b.top(); b.ok(()->t.i(), 30);

    b.nextDownHigh(b.new Int(30)).ok(29);
    b.nextDownLow (b.new Int(30)).ok(28);
    b.nextDownHigh(b.new Int(29)).ok(27);
    b.nextDownLow (b.new Int(29)).ok(26);
    b.nextDownHigh(b.new Int(28)).ok(25);
    b.nextDownLow (b.new Int(28)).ok(24);
    b.nextDownHigh(b.new Int(27)).ok(23);
    b.nextDownLow (b.new Int(27)).ok(22);
    b.nextDownHigh(b.new Int(26)).ok(21);
    b.nextDownLow (b.new Int(26)).ok(20);
    b.nextDownHigh(b.new Int(25)).ok(19);
    b.nextDownLow (b.new Int(25)).ok(18);
    b.nextDownHigh(b.new Int(24)).ok(17);
    b.nextDownLow (b.new Int(24)).ok(16);
    b.nextDownHigh(b.new Int(23)).ok(15);
    b.nextDownLow (b.new Int(23)).ok(14);
    b.nextDownHigh(b.new Int(22)).ok(13);
    b.nextDownLow (b.new Int(22)).ok(12);
    b.nextDownHigh(b.new Int(21)).ok(11);
    b.nextDownLow (b.new Int(21)).ok(10);
    b.nextDownHigh(b.new Int(20)).ok( 9);
    b.nextDownLow (b.new Int(20)).ok( 8);
    b.nextDownHigh(b.new Int(19)).ok( 7);
    b.nextDownLow (b.new Int(19)).ok( 6);
    b.nextDownHigh(b.new Int(18)).ok( 5);
    b.nextDownLow (b.new Int(18)).ok( 4);
    b.nextDownHigh(b.new Int(17)).ok( 3);
    b.nextDownLow (b.new Int(17)).ok( 2);
    b.nextDownHigh(b.new Int(16)).ok( 1);
    b.nextDownLow (b.new Int(16)).ok( 0);

    b.nextUp(b.new Int(29)).ok(30);
    b.nextUp(b.new Int(28)).ok(30);
    b.nextUp(b.new Int(27)).ok(29);
    b.nextUp(b.new Int(26)).ok(29);
    b.nextUp(b.new Int(25)).ok(28);
    b.nextUp(b.new Int(24)).ok(28);
    b.nextUp(b.new Int(23)).ok(27);
    b.nextUp(b.new Int(22)).ok(27);
    b.nextUp(b.new Int(21)).ok(26);
    b.nextUp(b.new Int(20)).ok(26);
    b.nextUp(b.new Int(19)).ok(25);
    b.nextUp(b.new Int(18)).ok(25);
    b.nextUp(b.new Int(17)).ok(24);
    b.nextUp(b.new Int(16)).ok(24);
    b.nextUp(b.new Int(15)).ok(23);
    b.nextUp(b.new Int(14)).ok(23);
    b.nextUp(b.new Int(13)).ok(22);
    b.nextUp(b.new Int(12)).ok(22);
    b.nextUp(b.new Int(11)).ok(21);
    b.nextUp(b.new Int(10)).ok(21);
    b.nextUp(b.new Int( 9)).ok(20);
    b.nextUp(b.new Int( 8)).ok(20);
    b.nextUp(b.new Int( 7)).ok(19);
    b.nextUp(b.new Int( 6)).ok(19);
    b.nextUp(b.new Int( 5)).ok(18);
    b.nextUp(b.new Int( 4)).ok(18);
    b.nextUp(b.new Int( 3)).ok(17);
    b.nextUp(b.new Int( 2)).ok(17);
    b.nextUp(b.new Int( 1)).ok(16);
    b.nextUp(b.new Int( 0)).ok(16);

    //stop("AAAA", b);
    b.ok(()->b, """
BitSet            0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16 |  0  0  0  0  0  1  1  1  0  0  0  1  0  0  0  0
One:
   2   16    8 |  0  0  1  1  0  1  0  0
   3   24    4 |  0  1  1  0
   4   28    2 |  1  1
   5   30    1 |  1
Zero:
   1   31    8 |  1  1  1  0  1  1  1  1
   2   39    4 |  1  1  1  1
   3   43    2 |  1  1
   4   45    1 |  1
""");

    b.low(b.new Int(30)).ok( 5);   b.high(b.new Int(30)).ok(11);
    b.low(b.new Int(29)).ok(11);   b.high(b.new Int(29)).ok(11);
    b.low(b.new Int(28)).ok( 5);   b.high(b.new Int(28)).ok( 7);
    b.low(b.new Int(26)).ok(11);   b.high(b.new Int(26)).ok(11);
    b.low(b.new Int(25)).ok( 5);   b.high(b.new Int(25)).ok( 7);
    b.low(b.new Int(21)).ok(11);   b.high(b.new Int(21)).ok(11);
    b.low(b.new Int(19)).ok( 6);   b.high(b.new Int(19)).ok( 7);
    b.low(b.new Int(18)).ok( 5);   b.high(b.new Int(18)).ok( 5);

    b.canGoLeft(b.new Int(30)).ok( true);   b.canGoRight(b.new Int(30)).ok( true);
    b.canGoLeft(b.new Int(26)).ok(false);   b.canGoRight(b.new Int(26)).ok( true);
    b.canGoLeft(b.new Int(29)).ok( true);   b.canGoRight(b.new Int(29)).ok(false);
    b.execute();
   }

  static void test_powerPos()                                                                                           // Test tree of searchable one bits
   {test_powerPos(true);
    test_powerPos(false);
   }

  static void oldTests()                                                                                                // Tests thought to be stable.
   {test_bitSet();
    test_prevNext01();
    test_prevNext();
    test_prevNext01();
    test_prevNext10();
    test_oneZero();
    test_fullEmpty();
    test_powerPos();
   }

  static void newTests()                                                                                                // Tests under development.
   {oldTests();                                                                                                       // Run baseline tests.
    //test_powerPos();
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
