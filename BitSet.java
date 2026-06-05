//----------------------------------------------------------------------------------------------------------------------
// Locate set or cleared bits in a  fixed size bit set in log N time.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;                                                                                                     // Standard utility library.

public class BitSet extends Program                                                                                     // Abstract fixed-size bit set using byte-level storage.
 {final int bitSize, bitSize1, bitSize2, logBitSize;                                                                    // Number of bits in the bit set.
  final int byteSize;                                                                                                   // Number of bytes in the bit set.
  final boolean oneTreeBit;                                                                                             // At most only one tree bit present
  final boolean powerOfTwo;                                                                                             // Able to optimize some operations because the requested bitset has a number of elements that is a power of two
  final Build              build;                                                                                       // Memory to use
  final ByteMemory.Ref memoryRef;                                                                                       // Build used to create biotset
  static int bitsetNumbers = 0;                                                                                         // Bitsets created
  final  int bitsetNumber  = ++bitsetNumbers;                                                                           // Number of this bitset

//D1 Constructors                                                                                                       // Construct bit sets of various sizes with the optional ability of locating ones and zeros efficiently

  static class Build                                                                                                    // Specification of a bitset
   {int              bitSize = 1;                                                                                       // Number of bits in the bit set.
    boolean        immediate = true;                                                                                    // Immediate mode execution by default
    Program           parent = null;                                                                                    // Parent program whose code is to be written into.
    ByteMemory.Ref memoryRef = null;                                                                                    // Program memory to be used

    Build bitSize  (int     BitSize  ) {bitSize   = BitSize  ;    return this;}
    Build immediate(boolean Immediate) {immediate = Immediate;    return this;}
    Build memory   (Program.ByteMemory.Ref Ref) {memoryRef = Ref; return this;}
    Build parent   (Program Parent)    {parent    = Parent   ;    return this;}

    int byteSize() {return (Byte.SIZE - 1 + 3 * nextPowerOfTwo(bitSize)) / Byte.SIZE;}                                  // Bytes needed for the bitset and its bit trees

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
    build       = Build;
    bitSize     = nextPowerOfTwo(Build.bitSize);                                                                        // Record size
    bitSize1    = bitSize - 1;
    bitSize2    = bitSize >>> 1;
    powerOfTwo  = bitSize == Build.bitSize;
    logBitSize  = logTwo(bitSize);
    if (bitSize < 2) stop("Size must be two or more, not:", bitSize);                                                   // There is not much point in bit sets with sizes of less than two.
    byteSize    = Build.byteSize();                                                                                     // Bytes needed for the bitset and its bit trees
    oneTreeBit  = bitSize <= 2;                                                                                         // At most only one tree bit present
    if (Build.memoryRef != null) memoryRef = Build.memoryRef;  else memoryRef = byteMemory.new Ref(0);                  // Use memory supplied by caller or create a reference to the default memory
   }

  BitSet initializeMemory()                                                                                             // Initialize memory
   {new ForCount(bitSize)                                                                                               // Clear one tree
     {void body(Int I)
       {setBitNC(I, new Bool(false));
       }
     };

    final Int p = addressZeroTree();                                                                                    // Set zero tree
    new ForCount(bitSize)                                                                                               // For loop to set bits along path in One tree to actual bit
     {void body(Int I)
       {setBitNC(p.Add(I), new Bool(true));
       }
     };
    return this;
   }

  BitSet(int BitSize)              {this(  new Build().bitSize(BitSize));}                                              // Constructor to create a bitset without the ability locate zeroes or ones
  static int bytesNeeded(int Size) {return new Build().bitSize(Size).byteSize();}                                       // Number of bytes needed for a bit set of specified size without the ability to locate zeroes or ones

  public  int size() {return build.bitSize;}                                                                            // Bitset size requested which may differ from the actual size as the size requested is rounded to the next power of two

  private void checkIndex(Int Index)                                                                                    // Check that a bit index is valid
   {new If (Index.lt(0))
     {void Then() {stop("BitSet index cannot be negative:", Index);}
     };

    new If(Index.ge(size()))
     {void Then()
       {stop("BitSet index cannot be greater than or equal to:",
             Index, size());
       }
     };
   }

//D1 Powers and Positions                                                                                               // Operations in numbers related to powers of two

  Int topZero()      {return new Int(2 * bitSize - 2 + bitSize1);}                                                      // Top of the zeros tree if it exists - zero based
  Int topOne ()      {return new Int(2 * bitSize - 2);}                                                                 // Top of the ones  tree if it exists - zero based

  int posOne (int P) {return 1 * bitSize + P;}                                                                          // Position in the ones  tree if it exists, argument is zero based as is the result
  int posZero(int P) {return 2 * bitSize + P - 1;}                                                                      // Position in the zeros tree if it exists, argument is zero based as is the result

  Int posOne (Int P) {final Int r = new Int(); new I() {void action() {r.ex(Int.Ops.set, posOne (P.i()));}}; return r;} // Position in the ones  tree if it exists, argument is zero based as is the result
  Int posZero(Int P) {final Int r = new Int(); new I() {void action() {r.ex(Int.Ops.set, posZero(P.i()));}}; return r;} // Position in the zeros tree if it exists, argument is zero based as is the result

  void checkLow(Int Pos, int Low)                                                                                       // Check that we can step down
   {if (immediate())
     {final int p = topOne().i(), P = Pos.i();                                                                          // Number of elements in the tree
      if (P < Low) stop("Position is below tree:", P);
      if (P > p  ) stop("Position is above tree:", P, p);
     }
   }

  Int zeroToOne(Int Pos) {final Int r = new Int(); new If (Pos.lt(bitSize)) {void Then() {r.set(Pos);} void Else() {r.set(Pos.Sub(bitSize1));}}; return r;} // Translate from Zeros tree to Ones tree
  Int oneToZero(Int Pos) {final Int r = new Int(); new If (Pos.lt(bitSize)) {void Then() {r.set(Pos);} void Else() {r.set(Pos.Add(bitSize1));}}; return r;} // Translate from Ones tree to Zeros tree

  Int childLowZero (Int Pos) {return oneToZero(childLowOne (zeroToOne(Pos)));}                                          // Step to low child in zero tree
  Int childHighZero(Int Pos) {return oneToZero(childHighOne(zeroToOne(Pos)));}                                          // Step to high child in zero tree
  Int parentZero   (Int Pos) {return oneToZero(parentOne   (zeroToOne(Pos)));}                                          // Step to the corresponding parent bit index for this child bit index

  Int childHighOne (Int Pos) {return childLowOne(Pos).Inc();}                                                           // Step to the corresponding child high bit index from this parent bit index
  Int childLowOne  (Int Pos) {return Pos.Dec().mul(2).sub(topOne());}                                                   // Step to the corresponding child low  bit index from this parent bit index
  Int parentOne    (Int Pos) {return Pos.Add(topOne()).add(2).div(2);}                                                  // Step to the corresponding parent bit index for this child bit index

  int zeroPos  (int Pos)                                                                                                // Position in the indicated row of the zero tree
   {if (Pos < bitSize) return Pos;                                                                                      // In bitset body
    return onePos(Pos - zeroBase(true) + bitSize);
   }

  int zeroWidth(int Pos)                                                                                                // Width of the specified position in the indicated row of the zero tree
   {if (Pos < bitSize) return bitSize;
    return oneWidth(Pos - zeroBase(true) + bitSize);
   }

  int onePos  (int Pos)                                                                                                 // Position in the indicated row of the one tree
   {if (Pos < bitSize) return Pos;                                                                                      // In bitset body
    int p = Pos - oneBase(true);
    int b = bitSize2;
    for (int i = 0; i < bitSize; i++) if (p >= b) {p -= b; b >>>= 1;} else break;
    return p;
   }

  int oneWidth(int Pos)                                                                                                 // Width of the specified position in the indicated row of the one tree
   {if (Pos < bitSize) return bitSize;
    int p = Pos - oneBase(true);
    int b = bitSize2;
    for (int i = 0; i < bitSize; i++) if (p >= b) {p -= b; b >>>= 1;} else break;
    return b;
   }

  int zeroBase (boolean X)  {return posZero(0);}                                                                        // Position in the current row
  int oneBase  (boolean X)  {return posOne (0);}                                                                        // Position in the current row

  Int zeroBase ()        {final Int r = new Int("zeroBase" ); new I() {void action() {r.ex(Int.Ops.set, posZero  (0)      );}}; return r;} // Position in the current row
  Int oneBase  ()        {final Int r = new Int("oneBase"  ); new I() {void action() {r.ex(Int.Ops.set, posOne   (0)      );}}; return r;} // Position in the current row
  Int zeroPos  (Int Pos) {final Int r = new Int("zeroPos"  ); new I() {void action() {r.ex(Int.Ops.set, zeroPos  (Pos.i()));}}; return r;} // Position in the current row
  Int zeroWidth(Int Pos) {final Int r = new Int("zeroWidth"); new I() {void action() {r.ex(Int.Ops.set, zeroWidth(Pos.i()));}}; return r;} // Width of the current row
  Int onePos   (Int Pos) {final Int r = new Int("onePos"   ); new I() {void action() {r.ex(Int.Ops.set, onePos   (Pos.i()));}}; return r;} // Position in the current row
  Int oneWidth (Int Pos) {final Int r = new Int("oneWidth" ); new I() {void action() {r.ex(Int.Ops.set, oneWidth (Pos.i()));}}; return r;} // Width of the current row

  Int lowOne(Int Pos)                                                                                                   // Find the lowest bit position with a one in it below the indicated subtree in the ones tree
   {if (immediate() && getBitNC(Pos).Flip().b()) stop("Cannot go low from Pos:", Pos, this);                            // We can only step down from a one in the ones tree
    final Int p = new Int(Pos);                                                                                         // Position in ones tree
    new For (new Int(logBitSize))                                                                                       // Step down
     {void body(Int Index, Bool Continue)
       {final Int a = childLowOne(p);                                                                                   // Lower level bit
        new If (getBitNC(a))                                                                                            // Take lower bit if possible else upper one
         {void Then() {p.set(a);}
          void Else() {p.set(a.inc());}
         };
        new If (p.ge(bitSize)) {void Then() {Continue.set();}};                                                         // Continue while we are in the ones tree
       }
     };
    return p;
   }

  Int highOne(Int Pos)                                                                                                  // Find the highest bit position with a one in it below the indicated subtree in the ones tree
   {if (immediate() && getBitNC(Pos).Flip().b()) stop("Cannot go high from Pos:",   Pos, this);                         // We can only step down from a one in the ones tree
    Int p = new Int(Pos);                                                                                               // Position in ones tree
    new For(new Int(logBitSize))                                                                                        // Step down
     {void body(Int Index, Bool Continue)
       {final Int b = childHighOne(p);                                                                                  // Lower level bit
        new If (getBitNC(b))                                                                                            // Choose upper bit over lower bit if possible
         {void Then() {p.set(b);}
          void Else() {p.set(b.dec());}
         };
        new If (p.ge(bitSize)) {void Then() {Continue.set();}};                                                         // Continue while we are in the ones tree
       }
     };
    return p;
   }

  Int lowZero(Int Pos)                                                                                                  // Find the lowest bit position with a one in it below the indicated subtree in the ones tree
   {if (immediate() && getBitNC(Pos).Flip().b()) stop("Cannot go low from Pos:", Pos, this);                            // We can only step down from a one in the ones tree
    final Int p = new Int(Pos);
    new For(new Int(logBitSize))                                                                                        // Step down
     {void body(Int Index, Bool Continue)
       {new If (p.ge(zeroBase().Add(bitSize2)))
         {void Then()
           {Continue.set();
            final Int a = childLowZero(p);
            new If (getBitNC(a))                                                                                        // Choose lower bit if possible
             {void Then() {p.set(a);}
              void Else() {p.set(a.inc());}
             };
           }
         };
       }
     };
    p.set(childLowZero(p));
    new If (getBitNC(p)) {void Then() {p.inc();}};                                                                      // Choose upper bit if lower bit has a one in it
    return p;
   }

  Int highZero(Int Pos)                                                                                                 // Find the highest bit position with a one in it below the indicated subtree in the ones tree
   {if (immediate() && getBitNC(Pos).Flip().b()) stop("Cannot go high from Pos:",   Pos, this);                         // We can only step down from a one in the ones tree
    final Int p = new Int(Pos);
    new For(new Int(logBitSize))                                                                                        // Step down
     {void body(Int Index, Bool Continue)
       {new If (p.ge(zeroBase().Add(bitSize2)))
         {void Then()
           {Continue.set();
            final Int b = childHighZero(p);
            new If (getBitNC(b))                                                                                        // Choose upper biot over lower bit if possible
             {void Then() {p.set(b); }
              void Else() {p.set(b.dec());}
             };
           }
         };
       }
     };
    p.set( childHighZero(p));
    new If (getBitNC(p)) {void Then(){p.dec();}};                                                                       // Low bit has a one so it must be the next bit up
    return p;
   }

  Bool canGoLeft(Int Pos)                                                                                               // Whether we can go left from the current position
   {checkLow(Pos, bitSize);
    if (immediate() && getBitNC(Pos).Flip().b()) stop("Cannot go low from Pos:", Pos, this);                            // We can only step down from a one in the ones tree
    return new Bool(getBitNC(childLowOne(Pos)));
   }

  Bool canGoRight(Int Pos)                                                                                              // Whether we can go right from the current position
   {checkLow(Pos, bitSize);
    if (immediate() && getBitNC(Pos).Flip().b()) stop("Cannot go low from Pos:",  Pos, this);                           // We can only step down from a one in the ones tree
    return new Bool(getBitNC(childHighOne(Pos)));
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

//D1 Get and Set                                                                                                        // Get and set bits in the  bit tree setting the corresponding paths in the bits trees if necessary

  public void clear(Int Index) {set(Index, new Bool(false));}                                                           // Clear bit and corresponding path bits from the indexed bit to the root of the bit tree
  public void set  (Int Index) {set(Index, new Bool(true ));}                                                           // Set bit and corresponding path bits from the indexed bit to the root of the bit tree
  public void set  (Int Index, Bool Value)                                                                               // Set or clear a bit in the bitset
   {new If (getBit(Index).ne(Value))                                                                                    // Bit not already set to the correct value
     {void Then()
       {setBitNC(Index, Value);                                                                                         // Set the bit
        new If (Value) {void Then() {setOnePath (Index);} void Else() {clearOnePath (Index);}};                         // Set or clear bits along the path from the indexed bit to the root of the ones  tree
        new If (Value) {void Then() {setZeroPath(Index);} void Else() {clearZeroPath(Index);}};                         // Set or clear bits along the path from the indexed bit to the root of the zeros tree
       }
     };
   }

  public Bool get   (Int Index) {return getBit(Index);}                                                                 // Get a bit from the bit set
  public Bool getBit(Int Index)                                                                                         // Get a bit from the bit set
   {if (immediate()) checkIndex(Index);
    return getBitNC(Index);
   }

  Bool    getBitNC(Int Index) {return memoryRef.getBool(Index);}                                                        // Get bit value at an index without checking that the index is valid
  boolean getBitNC(int Index) {return memoryRef.getBool(Index);}                                                        // Get bit value at an index without checking that the index is valid

  void setBit  (Int Index, Bool Value) {memoryRef.putBool(Index, Value);}                                               // Set bit value.
  void setBitNC(Int Index, Bool Value) {memoryRef.putBool(Index, Value);}                                               // Set bit value without checking index

  void moveDownOneLayer(Int b, Int p, Int w) {b.down(); p.add(w); w.down();}                                            // Next layer down in a bit tree
  void moveUpOneLayer  (Int B, Int p, Int w) {w.up();   p.sub(w); B.up()  ;}                                            // Move up one layer in the bit tree

  void setOnePath(Int Index)                                                                                            // Set bits along the path from the indexed bit to the root of the bit tree
   {final Int b = new Int(Index);                                                                                       // Position in level
    final Int p = new Int(0);                                                                                           // Position in bits, width
    final Int w = new Int(bitSize);                                                                                     // Width

    new For(bitSize)                                                                                                    // Set bits along the path to the actual bit in the One tree
     {void body(Int Index, Bool Continue)
       {Continue.set();                                                                                                 // Complete early if we found a bit that does not need setting
        new If (p.ne(0))                                                                                                // Not on the actual bits
         {void Then()                                                                                                   // Not on the actual bits
           {final Int q = new Int(p.Add(b));                                                                            // Position in One tree
            new If (getBitNC(q))                                                                                        // Is the bit already set
             {void Then() {Continue.clear();}                                                                           // Stop creating the path once we have arrived at a tree bit that is correctly set: as there are no changes at this level the upper levels must be ok too
              void Else() {setBitNC(q, new Bool(true));}                                                                // Flip the bit and continue
             };
           }
         };
        moveDownOneLayer(b, p, w);                                                                                      // Next level up
        Continue.and(w.gt(0));                                                                                          // As long as we are in a valid level
       }
     };
   }

  void clearOnePath(Int Index)                                                                                          // Clear bits along the path from the indexed bit to the root of the bit tree
   {final Int b = new Int(Index);                                                                                       // Position in level
    final Int p = new Int(0);                                                                                           // Position in bits, width
    final Int w = new Int(bitSize);                                                                                     // Width

    new For(bitSize)                                                                                                    // Step from root to leaf
     {void body(Int Index, Bool Continue)
       {final Int  B = b.Down();
        final Int  q = p.Add(w).add(B);
        final Int  Q = p.Add2(B);
        Continue.set();                                                                                                 // Complete early if we found a bit that does not need setting
        new If (B.Up().inc().lt(w).and(getBitNC(Q).Flip(), getBitNC(Q.Inc()).Flip()))                                   // Check both bits in the previous row are off
         {void Then()
           {final Int r = new Int(q);
            new If (getBitNC(r).Flip())
             {void Then() {Continue.clear();}                                                                           // Bit is already correctly set so there is nothing more to do
              void Else() {setBitNC(r, new Bool(false));}                                                               // Clear set bit along path to root
             };
           }
         };
        moveDownOneLayer(b, p, w);                                                                                      // Next layer
        Continue.and(w.gt(0));                                                                                          // As long as we are in a valid level
       }
     };
   }

  Int addressZeroTree()                                                                                                 // The zero tree will be held directly after the actual bits if there is no one tree, else beyond the one tree
   {final Int p = new Int(bitSize+bitSize1);
    return p;
   }

  private void clearZeroPath(Int Index)                                                                                 // Clear the target bit and set bits along the path from the indexed bit to the root of the bit tree
   {final Int p = new Int(addressZeroTree());                                                                           // Address zero bit tree
    final Int b = new Int(Index.Down());                                                                                // Position in layer
    final Int w = new Int(bitSize2);                                                                                    // Width of this layer

    new For(bitSize)                                                                                                    // Step from root to leaf
     {void body(Int Index, Bool Continue)
       {final Int  q = new Int(p.Add(b));
        Continue.set();
        new If (getBitNC(q))                                                                                            // Stop creating the path once we have arrived at a tree bit that is correctly set: as there are no changes at this level the upper levels must be ok too
         {void Then() {Continue.clear();}
          void Else() {setBitNC(q, new Bool(true));}
         };
        moveDownOneLayer(b, p, w);                                                                                      // Next layer
        Continue.and(w.gt(0));                                                                                                 // As long as we are in a valid level
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
             {void body(Int Index, Bool Continue)
               {final Int  P = p.dup();                                                                                 // Child layer becomes parent layer
                moveDownOneLayer(b, p, w);                                                                              // Index of bit in child layer, position in child layer, width of child layer
                final Int  Q = P.Add(b).add(b);
                Continue.set();                                                                                         // Complete early if we found a bit that does not need setting
                new If (getBitNC(new Int(Q)).or(getBitNC(new Int(Q.Inc()))))
                 {void Then() {Continue.clear();}                                                                       // There is a one in the upper row so we do not need to clear further down
                  void Else()                                                                                           // Need to show that there are no ones in the upper row
                   {final Int r = new Int(p.Add(b));                                                                    // Bit to set
                    new If (getBitNC(r).Flip())
                     {void Then() {Continue.clear();}                                                                   // Bit is already correctly set so there is nothing more to do
                      void Else() {setBitNC(r, new Bool(false));}                                                       // Clear set bit along path to root
                     };
                   }
                 };
                Continue.and(w.gt(0));                                                                                  // As long as we are in a valid level
               }
             };
           }
         };
       }
     };
   }

//D1 Locate Ones                                                                                                        // Find the first, last, next, previous bit set to one

  public Int firstOne()                                                                                                 // Find the index of the first set bit
   {final Int p = new Int(0);                                                                                           // Offset of first bit
    final Int r = new Int();

    new If (getBit(p))
     {void Then() {r.set(p);          }
      void Else() {r.copy(nextOne(p));}                                                                                 // Use copy because the result might be invalid showing that there is no first one
     };
    return r;                                                                                                           // Result is valid if found
   }

  public Int lastOne()                                                                                                  // Find the index of the last set bit
   {final Int p = new Int(size()-1);                                                                                    // Offset of last bit
    final Int r = new Int();
    new If (getBit(p))
     {void Then() {r.set(p);}
      void Else() {r.copy(prevOne(p));}                                                                                 // Use copy because the result might be invalid showing that there is no last one
     };
    return r;                                                                                                           // Result is valid if found
   }

  public Int nextOne(Int Start)                                                                                         // Find the index of the next set bit above the specified start bit
   {if (immediate()) checkIndex(Start);
    final Int Next = new Int();                                                                                         // Invalid indicates not found

    final Int b = new Int(Start);                                                                                       // Position in layer
    final Int w = new Int(bitSize);                                                                                     // Width of layer
    final Int p = new Int(0);                                                                                           // Offset of layer

    new For(bitSize)                                                                                                    // Traverse down through the tree  to the root
     {void body(Int I, Bool C)
       {final Int c = new Int(b.Add(1));                                                                                // Is there a path down from the next bit?
        C.set();                                                                                                        // Whether we are done yet

        new If (c.lt(w).and(getBitNC(new Int(p.Add(c)))))                                                               // Found next up bit
         {void Then()
           {new ForCount(I)                                                                                             // Traverse up through the tree to a leaf
             {void body(Int J)
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

    if (!powerOfTwo)                                                                                                    // Check result is in range if the requested bitset has a size that is not a power of two
     {new If (Next.valid())                                                                                             // Only relevant if there is a next value
       {void Then()
         {new If (Next.ge(size()))                                                                                      // Valid but out of range
           {void Then()
             {Next.invalidate();
             }
           };
         }
       };
     }
    return Next;                                                                                                        // Result is valid if found
   }

  public Int prevOne(Int Start)                                                                                         // Find the index of the previous set bit below the specified bit
   {if (immediate()) checkIndex(Start);
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
               {new ForCount(i)                                                                                         // Step down to the leaves
                 {void body(Int j)
                   {moveUpOneLayer(B, p, w);
                    new If (getBitNC(new Int(p.Add(B).inc())))                                                          // Follow path as low as possible
                     {void Then() {B.inc();}
                     };
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
   {final Int p = new Int(0);
    final Int r = new Int();
    new If (getBit(p))
     {void Then() {r.copy(nextZero(p));}                                                                                // Use copy because the result might be invalid showing that there is no first zero
      void Else() {r.set(p);          }
     };
    return r;                                                                                                           // Result is valid if found
   }

  public Int lastZero()                                                                                                 // Find the index of the last set bit
   {final Int p = new Int(size()-1);
    final Int r = new Int();
    new If (getBit(p))
     {void Then() {r.copy(prevZero(p));}                                                                                // Use copy because the result might be invalid showing that there is no last zero
      void Else() {r.set(p);           }
     };
    return r;                                                                                                           // Result is valid if found
   }

  public Int nextZero(Int Start)                                                                                        // Find the index of the next set bit above the specified bit
   {if (immediate()) checkIndex(Start);
    final Int Next = new Int();                                                                                         // Invalid indicates not found

    final Int b = new Int(Start);                                                                                       // Offset in bits in the current layer
    new If (b.ne(bitSize1))                                                                                             // Not last bit so there might be a next bit
     {void Then()
       {final Int w = new Int(bitSize2);                                                                                // Current width
        final Int p = addressZeroTree();                                                                                // Position in bits
        final Int q = new Int(b.Inc());
        new If (getBitNC(q).Flip())                                                                                     // Next bit is zero. This bit might be outside the actual range of the bitset if the boitset size is not a power of two so do not check the index when getting the bit.
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
                        new If (getBitNC(P))                                                                            // Next zero bit from actual bits.  getBit() not usable if the bitset size os not a power of two
                         {void Then() {Next.set(P).inc();}
                          void Else() {Next.set(P);      }
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

    if (!powerOfTwo)                                                                                                    // Check result is in range if the requested bitset has a size that is not a power of two
     {new If (Next.valid())                                                                                             // Only relevant if there is a next value
       {void Then()
         {new If (Next.ge(size()))                                                                                      // Valid but out of range
           {void Then()
             {Next.invalidate();
             }
           };
         }
       };
     }
    return Next;                                                                                                        // Result is valid if found
   }
/*

  1    1    1    0    1    1    1    1    1    1    1    1    1    0    1    1
  0         1         0         0         0         0         1         0
  1                   0                   0                   1
  1                                       1
  1

  0    1    2    3    4    5    6    7    8    9   10   11   12    13   14   15
 16        17        18        19        20        21        22         23
 24                  25                  26                  27
 28                                      29
 30
*/

  Int prevZero(Int Start)                                                                                               // Find the index of the previous set bit below the specified bit
   {if (immediate()) checkIndex(Start);
    final Int Prev = new Int();                                                                                         // Location of previous zero ro ivalid of thre is not one
    final Int p = new Int(Start);                                                                                       // Start position in body of bitset
    new If (p.eq(0))                                                                                                    // At start of bitset
     {void Then() {}                                                                                                    // At start of bitset - not found
      void Else()
       {final Int q = p.Dec();                                                                                          // Position to left
        new If (getBitNC(q).Flip())                                                                                     // Adjacent zero
         {void Then()
           {Prev.set(q);
           }
          void Else()                                                                                                   // Go down through zeros tree looking for a one
           {new For(logBitSize)
             {void body(Int Up, Bool ContinueUp)
               {p.set(parentZero(p));                                                                                   // Every bit has a parent exceptteh topmost bit ion the tree but the loop will terminated on count before then
                new If (zeroPos(p).eq(0))                                                                               // At start of row
                 {void Then() {}                                                                                        // At start of row - not found
                  void Else()
                   {p.dec();                                                                                            // Position to left
                    new If (getBitNC(p))                                                                                // Found a one
                    {void Then()                                                                                        // Reached a one so we turn over and head back up the tree goinfg as high as possible
                      {Prev.set(highZero(p));                                                                           // Highest zero from this point
                      }
                     void Else()                                                                                        // Continue the search for a one
                      {ContinueUp.set();
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
    return Prev;
   }

//D1 Statistics                                                                                                         // Count the number of ones or zero bits in a bit set

//D2 Full or empty                                                                                                      // Check whether a bit set is full or empty

  public Bool full () {return new Bool(firstZero().notValid());}                                                        // Whether the bitset is full - in log N time. It might be better to keep a separate count field if the extra overhead can be justified
  public Bool empty() {return new Bool(firstOne ().notValid());}                                                        // Whether the bitset is empty

  public Bool twoOrMoreOnes()                                                                                           // Whether there two or more ones in the bitset
   {final Bool r = new Bool(false);                                                                                     // Assume contrary
    final Int  p = new Int(topOne());                                                                                   // Start at top of one tree

    new If (getBitNC(p))                                                                                                // The root has a one so the bit set is not empty
     {void Then()
       {new For(new Int(logBitSize))                                                                                    // Step down looking for an adjacent sub tree that also has a one
         {void body(Int Index, Bool Continue)
           {final Int l = childLowOne (p);
            final Int h = childHighOne(p);
            new If (getBitNC(l))                                                                                        // Check lower child
             {void Then()
               {new If (getBitNC(h))
                 {void Then() {r.set(true);}                                                                            // Two or more one bits are set to one in the bit set
                  void Else() {p.set(l); Continue.set();}                                                               // Low is one so search sub tree
                 };
               }
              void Else()                                                                                               // Low is zero
               {new If (getBitNC(h))
                 {void Then() {p.set(h); Continue.set();}                                                               // High is one so search sub tree
                  void Else() {new I() {void action() {stop("Should not happen"); }};}                                  // Both high and low are zero but this should not happen
                 };
               }
             };
           }
         };
       }
     };
    return r;                                                                                                           // Whether the bitset has two or nore ones
   }

//D2 Counts                                                                                                             // The number of bits set to zero or one in the bitset

  public Int countOnes()                                                                                                // Count ones in bitset
   {final Int c = new Int(0);                                                                                           // Count
    final Int p = firstOne();                                                                                           // Position in bitset starting at first one
    new For(new Int(size()))                                                                                            // Step from one to one
     {void body(Int Index, Bool Continue)
       {new If (p.valid())                                                                                              // Latest step is valid
         {void Then()
           {c.inc();
            final Int q = nextOne(p);                                                                                   // Step to next one
            new If (q.valid())                                                                                          // Valid if we are still in the bitset
             {void Then()
               {p.set(q);
                Continue.set(true);                                                                                     // Continue stepping
               }
             };
           }
         };
       }
     };
    return c;                                                                                                           // Return count
   };

  public Int countZeros()                                                                                               // Count zeros in bitset
   {final Int c = new Int(0);                                                                                           // Count
    final Int p = firstZero();
    new For(new Int(size()))
     {void body(Int Index, Bool Continue)
       {new If (p.valid())
         {void Then()
           {c.inc();
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
    return c;                                                                                                           // Return count
   };

//D1 Print                                                                                                              // Print the bit set

  public String toString()                                                                                              // Print bit set so we can visualize it. This will not be available on the chip so we use normal Java
   {final StringBuilder s = new StringBuilder();
    int p = 0, r = bitSize;

    s.append("BitSet          ");                                                                                       // Title
    for   (int i : range(size())) s.append(f(" %2d", i));                                                               // Positions of bits
    s.append("\n");

    for   (int i : range(1, size()))                                                                                    // Print the first line and the first bit tree if present
     {s.append(f("%4d %4d %4d |", i, p, r));
      for (int j : range(r))                                                                                            // Bits in level
       {if (i > 1 || j < size()) s.append(f("  %1d", memoryRef.getBool(p + j) ? 1 : 0));
       }
      s.append("\n");
      if (i == 1)                                                                                                       // The first line is the actual bits
       {s.append("One:\n");                                                                                             // One tree present so it comes next
       }
      p += r;
      r >>>= 1;
      if (r == 0) break;                                                                                                // Reached the leaves
     }

    r = bitSize2;
    s.append("Zero:\n");
    for   (int i : range(1, size()))                                                                                    // Each level
     {s.append(f("%4d %4d %4d |", i, p, r));
      for (int j : range(r))                                                                                            // Bits in level
       {s.append(f("  %1d", memoryRef.getBool(p + j) ? 1 : 0));
       }
      s.append("\n");
      p += r;
      r >>>= 1;
      if (r == 0) break;                                                                                                // Reached the leaves
     }

    return ""+s;
   }

//D1 Tests                                                                                                              // Tests

  static BitSet test_bits(boolean Ex, int N)                                                                            // Create test bitset.
   {final Build build = new Build().bitSize(N).immediate(Ex);                                                           // Allocate backing storage.
    final byte[]bytes = new byte[build.byteSize()];                                                                     // Allocate backing storage.
    final BitSet    b = new BitSet(build).initializeMemory();                                                           // Create a bit set
    return b;                                                                                                           // Return test bitset.
   }

  static void test_prevNext(boolean Ex)                                                                                 // Test tree of searchable one bits
   {final BitSet b = test_bits(Ex, 32);
    b.maxSteps = 99999;
    final int[]s = new int[]{13, 19, 24, 25, 26, 27, 28, 30, 31};

    for (int i : s) b.set(b.new Int(i));
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
   {          test_prevNext(true);
              test_prevNext(false);
   }

  static void test_prevNext01(boolean Ex)                                                                               // Test tree of searchable one bits
   {final int N = 16;
    final BitSet b = test_bits(Ex, N);

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
    //testStop(b);
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
    for (int i : range(11, 16)) b.nextOne(b.new Int(i)).notValid().ok(true);

                                b.prevOne(b.new Int(0)).notValid().ok(true);
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
                                b.nextZero(b.new Int(15)).notValid().ok(true);

    for (int i : range( 5))     b.prevZero(b.new Int( i)).notValid().ok(true);
    for (int i : range( 5,  9)) b.prevZero(b.new Int( i)).ok(i-1);
    for (int i : range( 8, 12)) b.prevZero(b.new Int( i)).ok(  7);
    for (int i : range(13, 16)) b.prevZero(b.new Int( i)).ok(i-1);
                                b.firstZero()            .ok(  4);
                                b.lastZero ()            .ok( 15);
   }

  static void test_prevNext01()                                                                                         // Test tree of searchable one bits
   {          test_prevNext01(true);
              test_prevNext01(false);
   }

  static void test_prevNext10(boolean Ex)                                                                               // Test tree of searchable one bits
   {final int N = 16;
    final BitSet b = test_bits(Ex, N);
    for (int i : range(N)) b.set(b.new Int(i), b.new Bool((i / 4) % 2 == 1));

   //testStop(b);

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
   {          test_prevNext10(true);
              test_prevNext10(false);
   }

  static void test_oneZero(boolean Ex)
   {final int N = 8;
    final BitSet b = test_bits(Ex, N);
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
    //testStop(s);
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
   {          test_oneZero(true);
              test_oneZero(false);
   }

  static void test_fullEmpty(int N, boolean Ex)
   {final BitSet b = test_bits(Ex, N);

    b.empty().ok(true);
    b.new ForCount(b.new Int(N))
     {void body(Int Index)
       {b.full ().ok(false);
        b.countOnes ().ok(Index);
        b.set(b.new Int(Index), b.new Bool(true));
        b.empty().ok(false);
        b.countZeros().ok(b.new Int(N).Dec().sub(Index));
       }
     };
    b.full().ok(true);

    b.maxSteps = 99999;
    b.execute();
   }

  static void test_fullEmpty()                                                                                          // Test tree of searchable one bits
   {          test_fullEmpty( 9, true);
              test_fullEmpty( 9, false);
              test_fullEmpty(16, true);
              test_fullEmpty(16, false);
   }

  static void test_count(int N, boolean Ex)
   {final BitSet b = test_bits(Ex, N);

    b.set(b.new Int(  1),   b.new Bool(true));
    b.set(b.new Int(N-1),   b.new Bool(true));
    b.countOnes ().ok(2);
    b.countZeros().ok(N-2);

    b.set(b.new Int(N/2),   b.new Bool(true));
    b.countOnes ().ok(3);
    b.countZeros().ok(N-3);

    b.set(b.new Int(N/2+1), b.new Bool(true));
    b.countOnes ().ok(4);
    b.countZeros().ok(N-4);

    b.maxSteps = 99999;
    b.execute();
   }

  static void test_count()
   {          test_count( 9, true);
              test_count( 9, false);
              test_count(17, true);
              test_count(17, false);
              test_count(32, true);
              test_count(32, false);
   }

/*
  0    1    2    3    4    5    6    7   8   9  10  11  12  13  14  15
 16        17        18        19       20      21      22      23
 24                  25                 26              27
 28                                     29
 30

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

*/

  static void test_powerPosOneZero(boolean Ex)
   {final int N = 16;
    final BitSet b = test_bits(Ex, N);

    for (int i : range(N)) if ((i > 4 && i < 8) || (i > 10 && i < 12)) b.set(b.new Int(i), b.new Bool(true));

    b.topOne().ok(30); b.topZero().ok(45); b.oneBase() .ok(16); b.zeroBase().ok(31);

    b.childHighZero(b.new Int(45)).ok(44);
    b.childLowZero (b.new Int(45)).ok(43);
    b.childHighZero(b.new Int(44)).ok(42);
    b.childLowZero (b.new Int(44)).ok(41);
    b.childHighZero(b.new Int(43)).ok(40);
    b.childLowZero (b.new Int(43)).ok(39);
    b.childHighZero(b.new Int(42)).ok(38);
    b.childLowZero (b.new Int(42)).ok(37);
    b.childHighZero(b.new Int(41)).ok(36);
    b.childLowZero (b.new Int(41)).ok(35);
    b.childHighZero(b.new Int(40)).ok(34);
    b.childLowZero (b.new Int(40)).ok(33);
    b.childHighZero(b.new Int(39)).ok(32);
    b.childLowZero (b.new Int(39)).ok(31);
    b.childHighZero(b.new Int(38)).ok(15);
    b.childLowZero (b.new Int(38)).ok(14);
    b.childHighZero(b.new Int(37)).ok(13);
    b.childLowZero (b.new Int(37)).ok(12);
    b.childHighZero(b.new Int(36)).ok(11);
    b.childLowZero (b.new Int(36)).ok(10);
    b.childHighZero(b.new Int(35)).ok( 9);
    b.childLowZero (b.new Int(35)).ok( 8);
    b.childHighZero(b.new Int(34)).ok( 7);
    b.childLowZero (b.new Int(34)).ok( 6);
    b.childHighZero(b.new Int(33)).ok( 5);
    b.childLowZero (b.new Int(33)).ok( 4);
    b.childHighZero(b.new Int(32)).ok( 3);
    b.childLowZero (b.new Int(32)).ok( 2);
    b.childHighZero(b.new Int(31)).ok( 1);
    b.childLowZero (b.new Int(31)).ok( 0);

    b.parentZero(b.new Int(44)).ok(45);
    b.parentZero(b.new Int(43)).ok(45);
    b.parentZero(b.new Int(42)).ok(44);
    b.parentZero(b.new Int(41)).ok(44);
    b.parentZero(b.new Int(40)).ok(43);
    b.parentZero(b.new Int(39)).ok(43);
    b.parentZero(b.new Int(38)).ok(42);
    b.parentZero(b.new Int(37)).ok(42);
    b.parentZero(b.new Int(36)).ok(41);
    b.parentZero(b.new Int(35)).ok(41);
    b.parentZero(b.new Int(34)).ok(40);
    b.parentZero(b.new Int(33)).ok(40);
    b.parentZero(b.new Int(32)).ok(39);
    b.parentZero(b.new Int(31)).ok(39);
    b.parentZero(b.new Int(15)).ok(38);
    b.parentZero(b.new Int(14)).ok(38);
    b.parentZero(b.new Int(13)).ok(37);
    b.parentZero(b.new Int(12)).ok(37);
    b.parentZero(b.new Int(11)).ok(36);
    b.parentZero(b.new Int(10)).ok(36);
    b.parentZero(b.new Int( 9)).ok(35);
    b.parentZero(b.new Int( 8)).ok(35);
    b.parentZero(b.new Int( 7)).ok(34);
    b.parentZero(b.new Int( 6)).ok(34);
    b.parentZero(b.new Int( 5)).ok(33);
    b.parentZero(b.new Int( 4)).ok(33);
    b.parentZero(b.new Int( 3)).ok(32);
    b.parentZero(b.new Int( 2)).ok(32);
    b.parentZero(b.new Int( 1)).ok(31);
    b.parentZero(b.new Int( 0)).ok(31);

    b.childHighOne(b.new Int(30)).ok(29);
    b.childLowOne (b.new Int(30)).ok(28);
    b.childHighOne(b.new Int(29)).ok(27);
    b.childLowOne (b.new Int(29)).ok(26);
    b.childHighOne(b.new Int(28)).ok(25);
    b.childLowOne (b.new Int(28)).ok(24);
    b.childHighOne(b.new Int(27)).ok(23);
    b.childLowOne (b.new Int(27)).ok(22);
    b.childHighOne(b.new Int(26)).ok(21);
    b.childLowOne (b.new Int(26)).ok(20);
    b.childHighOne(b.new Int(25)).ok(19);
    b.childLowOne (b.new Int(25)).ok(18);
    b.childHighOne(b.new Int(24)).ok(17);
    b.childLowOne (b.new Int(24)).ok(16);
    b.childHighOne(b.new Int(23)).ok(15);
    b.childLowOne (b.new Int(23)).ok(14);
    b.childHighOne(b.new Int(22)).ok(13);
    b.childLowOne (b.new Int(22)).ok(12);
    b.childHighOne(b.new Int(21)).ok(11);
    b.childLowOne (b.new Int(21)).ok(10);
    b.childHighOne(b.new Int(20)).ok( 9);
    b.childLowOne (b.new Int(20)).ok( 8);
    b.childHighOne(b.new Int(19)).ok( 7);
    b.childLowOne (b.new Int(19)).ok( 6);
    b.childHighOne(b.new Int(18)).ok( 5);
    b.childLowOne (b.new Int(18)).ok( 4);
    b.childHighOne(b.new Int(17)).ok( 3);
    b.childLowOne (b.new Int(17)).ok( 2);
    b.childHighOne(b.new Int(16)).ok( 1);
    b.childLowOne (b.new Int(16)).ok( 0);

    b.parentOne(b.new Int(29)).ok(30);
    b.parentOne(b.new Int(28)).ok(30);
    b.parentOne(b.new Int(27)).ok(29);
    b.parentOne(b.new Int(26)).ok(29);
    b.parentOne(b.new Int(25)).ok(28);
    b.parentOne(b.new Int(24)).ok(28);
    b.parentOne(b.new Int(23)).ok(27);
    b.parentOne(b.new Int(22)).ok(27);
    b.parentOne(b.new Int(21)).ok(26);
    b.parentOne(b.new Int(20)).ok(26);
    b.parentOne(b.new Int(19)).ok(25);
    b.parentOne(b.new Int(18)).ok(25);
    b.parentOne(b.new Int(17)).ok(24);
    b.parentOne(b.new Int(16)).ok(24);
    b.parentOne(b.new Int(15)).ok(23);
    b.parentOne(b.new Int(14)).ok(23);
    b.parentOne(b.new Int(13)).ok(22);
    b.parentOne(b.new Int(12)).ok(22);
    b.parentOne(b.new Int(11)).ok(21);
    b.parentOne(b.new Int(10)).ok(21);
    b.parentOne(b.new Int( 9)).ok(20);
    b.parentOne(b.new Int( 8)).ok(20);
    b.parentOne(b.new Int( 7)).ok(19);
    b.parentOne(b.new Int( 6)).ok(19);
    b.parentOne(b.new Int( 5)).ok(18);
    b.parentOne(b.new Int( 4)).ok(18);
    b.parentOne(b.new Int( 3)).ok(17);
    b.parentOne(b.new Int( 2)).ok(17);
    b.parentOne(b.new Int( 1)).ok(16);
    b.parentOne(b.new Int( 0)).ok(16);

    //testStop("AAAA", b);
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

    b.lowOne(b.new Int(30)).ok( 5);   b.highOne(b.new Int(30)).ok(11);
    b.lowOne(b.new Int(29)).ok(11);   b.highOne(b.new Int(29)).ok(11);
    b.lowOne(b.new Int(28)).ok( 5);   b.highOne(b.new Int(28)).ok( 7);
    b.lowOne(b.new Int(26)).ok(11);   b.highOne(b.new Int(26)).ok(11);
    b.lowOne(b.new Int(25)).ok( 5);   b.highOne(b.new Int(25)).ok( 7);
    b.lowOne(b.new Int(21)).ok(11);   b.highOne(b.new Int(21)).ok(11);
    b.lowOne(b.new Int(19)).ok( 6);   b.highOne(b.new Int(19)).ok( 7);
    b.lowOne(b.new Int(18)).ok( 5);   b.highOne(b.new Int(18)).ok( 5);

    b.lowZero(b.new Int(15+30)).ok( 0);   b.highZero(b.new Int(15+30)).ok(15);
    b.lowZero(b.new Int(15+29)).ok( 8);   b.highZero(b.new Int(15+29)).ok(15);
    b.lowZero(b.new Int(15+28)).ok( 0);   b.highZero(b.new Int(15+28)).ok( 4);
    b.lowZero(b.new Int(15+27)).ok(12);   b.highZero(b.new Int(15+27)).ok(15);
    b.lowZero(b.new Int(15+26)).ok( 8);   b.highZero(b.new Int(15+26)).ok(10);
    b.lowZero(b.new Int(15+25)).ok( 4);   b.highZero(b.new Int(15+25)).ok( 4);
    b.lowZero(b.new Int(15+24)).ok( 0);   b.highZero(b.new Int(15+24)).ok( 3);
    b.lowZero(b.new Int(15+23)).ok(14);   b.highZero(b.new Int(15+23)).ok(15);
    b.lowZero(b.new Int(15+22)).ok(12);   b.highZero(b.new Int(15+22)).ok(13);
    b.lowZero(b.new Int(15+21)).ok(10);   b.highZero(b.new Int(15+21)).ok(10);
    b.lowZero(b.new Int(15+20)).ok( 8);   b.highZero(b.new Int(15+20)).ok( 9);
    b.lowZero(b.new Int(15+18)).ok( 4);   b.highZero(b.new Int(15+18)).ok( 4);
    b.lowZero(b.new Int(15+17)).ok( 2);   b.highZero(b.new Int(15+17)).ok( 3);
    b.lowZero(b.new Int(15+16)).ok( 0);   b.highZero(b.new Int(15+16)).ok( 1);

    b.canGoLeft(b.new Int(30)).ok( true); b.canGoRight(b.new Int(30)).ok( true);
    b.canGoLeft(b.new Int(26)).ok(false); b.canGoRight(b.new Int(26)).ok( true);
    b.canGoLeft(b.new Int(29)).ok( true); b.canGoRight(b.new Int(29)).ok(false);

    b.oneBase() .ok(16);                                                    b.zeroBase().ok(31);
    b.onePos(b.new Int( 0)).ok( 0); b.oneWidth(b.new Int( 0)).ok(16);       b.zeroPos(b.new Int(    0)).ok( 0); b.zeroWidth(b.new Int(    0)).ok(16);
    b.onePos(b.new Int( 1)).ok( 1); b.oneWidth(b.new Int( 1)).ok(16);       b.zeroPos(b.new Int(    1)).ok( 1); b.zeroWidth(b.new Int(    1)).ok(16);
    b.onePos(b.new Int( 2)).ok( 2); b.oneWidth(b.new Int( 2)).ok(16);       b.zeroPos(b.new Int(    2)).ok( 2); b.zeroWidth(b.new Int(    2)).ok(16);
    b.onePos(b.new Int( 3)).ok( 3); b.oneWidth(b.new Int( 3)).ok(16);       b.zeroPos(b.new Int(    3)).ok( 3); b.zeroWidth(b.new Int(    3)).ok(16);
    b.onePos(b.new Int( 4)).ok( 4); b.oneWidth(b.new Int( 4)).ok(16);       b.zeroPos(b.new Int(    4)).ok( 4); b.zeroWidth(b.new Int(    4)).ok(16);
    b.onePos(b.new Int( 5)).ok( 5); b.oneWidth(b.new Int( 5)).ok(16);       b.zeroPos(b.new Int(    5)).ok( 5); b.zeroWidth(b.new Int(    5)).ok(16);
    b.onePos(b.new Int( 6)).ok( 6); b.oneWidth(b.new Int( 6)).ok(16);       b.zeroPos(b.new Int(    6)).ok( 6); b.zeroWidth(b.new Int(    6)).ok(16);
    b.onePos(b.new Int( 7)).ok( 7); b.oneWidth(b.new Int( 7)).ok(16);       b.zeroPos(b.new Int(    7)).ok( 7); b.zeroWidth(b.new Int(    7)).ok(16);
    b.onePos(b.new Int( 8)).ok( 8); b.oneWidth(b.new Int( 8)).ok(16);       b.zeroPos(b.new Int(    8)).ok( 8); b.zeroWidth(b.new Int(    8)).ok(16);
    b.onePos(b.new Int( 9)).ok( 9); b.oneWidth(b.new Int( 9)).ok(16);       b.zeroPos(b.new Int(    9)).ok( 9); b.zeroWidth(b.new Int(    9)).ok(16);
    b.onePos(b.new Int(10)).ok(10); b.oneWidth(b.new Int(10)).ok(16);       b.zeroPos(b.new Int(   10)).ok(10); b.zeroWidth(b.new Int(   10)).ok(16);
    b.onePos(b.new Int(11)).ok(11); b.oneWidth(b.new Int(11)).ok(16);       b.zeroPos(b.new Int(   11)).ok(11); b.zeroWidth(b.new Int(   11)).ok(16);
    b.onePos(b.new Int(12)).ok(12); b.oneWidth(b.new Int(12)).ok(16);       b.zeroPos(b.new Int(   12)).ok(12); b.zeroWidth(b.new Int(   12)).ok(16);
    b.onePos(b.new Int(13)).ok(13); b.oneWidth(b.new Int(13)).ok(16);       b.zeroPos(b.new Int(   13)).ok(13); b.zeroWidth(b.new Int(   13)).ok(16);
    b.onePos(b.new Int(14)).ok(14); b.oneWidth(b.new Int(14)).ok(16);       b.zeroPos(b.new Int(   14)).ok(14); b.zeroWidth(b.new Int(   14)).ok(16);
    b.onePos(b.new Int(15)).ok(15); b.oneWidth(b.new Int(15)).ok(16);       b.zeroPos(b.new Int(   15)).ok(15); b.zeroWidth(b.new Int(   15)).ok(16);
    b.onePos(b.new Int(16)).ok( 0); b.oneWidth(b.new Int(16)).ok(8);        b.zeroPos(b.new Int(15+16)).ok( 0); b.zeroWidth(b.new Int(15+16)).ok(8);
    b.onePos(b.new Int(17)).ok( 1); b.oneWidth(b.new Int(17)).ok(8);        b.zeroPos(b.new Int(15+17)).ok( 1); b.zeroWidth(b.new Int(15+17)).ok(8);
    b.onePos(b.new Int(18)).ok( 2); b.oneWidth(b.new Int(18)).ok(8);        b.zeroPos(b.new Int(15+18)).ok( 2); b.zeroWidth(b.new Int(15+18)).ok(8);
    b.onePos(b.new Int(19)).ok( 3); b.oneWidth(b.new Int(19)).ok(8);        b.zeroPos(b.new Int(15+19)).ok( 3); b.zeroWidth(b.new Int(15+19)).ok(8);
    b.onePos(b.new Int(20)).ok( 4); b.oneWidth(b.new Int(20)).ok(8);        b.zeroPos(b.new Int(15+20)).ok( 4); b.zeroWidth(b.new Int(15+20)).ok(8);
    b.onePos(b.new Int(21)).ok( 5); b.oneWidth(b.new Int(21)).ok(8);        b.zeroPos(b.new Int(15+21)).ok( 5); b.zeroWidth(b.new Int(15+21)).ok(8);
    b.onePos(b.new Int(22)).ok( 6); b.oneWidth(b.new Int(22)).ok(8);        b.zeroPos(b.new Int(15+22)).ok( 6); b.zeroWidth(b.new Int(15+22)).ok(8);
    b.onePos(b.new Int(23)).ok( 7); b.oneWidth(b.new Int(23)).ok(8);        b.zeroPos(b.new Int(15+23)).ok( 7); b.zeroWidth(b.new Int(15+23)).ok(8);
    b.onePos(b.new Int(24)).ok( 0); b.oneWidth(b.new Int(24)).ok(4);        b.zeroPos(b.new Int(15+24)).ok( 0); b.zeroWidth(b.new Int(15+24)).ok(4);
    b.onePos(b.new Int(25)).ok( 1); b.oneWidth(b.new Int(25)).ok(4);        b.zeroPos(b.new Int(15+25)).ok( 1); b.zeroWidth(b.new Int(15+25)).ok(4);
    b.onePos(b.new Int(26)).ok( 2); b.oneWidth(b.new Int(26)).ok(4);        b.zeroPos(b.new Int(15+26)).ok( 2); b.zeroWidth(b.new Int(15+26)).ok(4);
    b.onePos(b.new Int(27)).ok( 3); b.oneWidth(b.new Int(27)).ok(4);        b.zeroPos(b.new Int(15+27)).ok( 3); b.zeroWidth(b.new Int(15+27)).ok(4);
    b.onePos(b.new Int(28)).ok( 0); b.oneWidth(b.new Int(28)).ok(2);        b.zeroPos(b.new Int(15+28)).ok( 0); b.zeroWidth(b.new Int(15+28)).ok(2);
    b.onePos(b.new Int(29)).ok( 1); b.oneWidth(b.new Int(29)).ok(2);        b.zeroPos(b.new Int(15+29)).ok( 1); b.zeroWidth(b.new Int(15+29)).ok(2);
    b.onePos(b.new Int(30)).ok( 0); b.oneWidth(b.new Int(30)).ok(1);        b.zeroPos(b.new Int(15+30)).ok( 0); b.zeroWidth(b.new Int(15+30)).ok(1);

    b.execute();
   }

  static void test_powerPosOneZero()
   {          test_powerPosOneZero(true);
              test_powerPosOneZero(false);
   }

  static void test_twoOrMoreOnes(boolean Ex)                                                                            // Two or more ones
   {final BitSet b = test_bits(Ex, 32);

    b.set  (b.new Int(17)); b.twoOrMoreOnes().ok(false);
    b.set  (b.new Int(12)); b.twoOrMoreOnes().ok(true);
    b.set  (b.new Int(24)); b.twoOrMoreOnes().ok(true);
    b.clear(b.new Int(12)); b.twoOrMoreOnes().ok(true);
    b.clear(b.new Int(11)); b.twoOrMoreOnes().ok(true);
    b.clear(b.new Int(17)); b.twoOrMoreOnes().ok(false);

    b.execute();
   }

  static void test_twoOrMoreOnes()
   {          test_twoOrMoreOnes(true);
              test_twoOrMoreOnes(false);
   }

  static void oldTests()                                                                                                // Tests thought to be stable.
   {test_prevNext01();
    test_prevNext();
    test_prevNext01();
    test_prevNext10();
    test_oneZero();
    test_fullEmpty();
    test_count();
    test_powerPosOneZero();
    test_twoOrMoreOnes();
   }

  static void newTests()                                                                                                // Tests under development.
   {//oldTests();                                                                                                         // Run baseline tests.
    test_twoOrMoreOnes();
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
