//----------------------------------------------------------------------------------------------------------------------
// Locate set or cleared bits in a fixed size bit set in log N time.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;                                                                                                     // Standard utility library.

final public class BitSet extends Program                                                                               // Fixed-size bit set using byte-level storage.
 {final int bitSize, bitSize1, bitSize2, logBitSize;                                                                    // Number of bits in the bit set.
  final int              unitsSize;                                                                                     // Number of bytes in the bit set.
  final boolean        powerOfTwo;                                                                                      // Some operations can be optimized if the bitset has a number of elements that is a power of two
  final boolean        trackCount;                                                                                      // Track the count of number of bits set to one
  final Build               build;                                                                                      // Memory to use
  final UnitMemory.Ref  memoryRef;                                                                                      // Build used to create bitset
  final UnitMemory.Ref memoryCount;                                                                                     // Memory for count field of present
  static int         bitsetNumbers = 0;                                                                                 // Bitsets created
  final  int          bitsetNumber = ++bitsetNumbers;                                                                   // Number of this bitset
  final  int[]     limitsUpperOne;                                                                                      // The upper limit of the ones  tree for each possible position in the ones tree
  final  int[]    limitsUpperZero;                                                                                      // The upper limit of the zeros tree for each possible position in the ones tree
  final  int[]     limitsLowerOne;                                                                                      // The lower limit of the ones  tree for each possible position in the ones tree
  final  int[]    limitsLowerZero;                                                                                      // The lower limit of the zeros tree for each possible position in the ones tree
  final  int[]          heightOne;                                                                                      // Position in ones tree to height in ones tree
  final  int[]         heightZero;                                                                                      // Position in zeros tree to height in zeros tree
  final  int[]            posZero;                                                                                      // Position in zeros tree row from position ones tree
  final  int[]             posOne;                                                                                      // Position in ones tree row from position ones tree
  final  String        luoVerilog;                                                                                      // Verilog functions to lookup constants from arrays describing shape of trees
  final  String        luzVerilog;
  final  String        lloVerilog;
  final  String        llzVerilog;
  final  String         hoVerilog;
  final  String         hzVerilog;
  final  String         poVerilog;
  final  String         pzVerilog;

//D1 Constructors                                                                                                       // Construct bit sets of various sizes with the optional ability of locating ones and zeros efficiently

  final static class Build                                                                                              // Specification of a bitset
   {int                bitSize = 1;                                                                                     // Number of bits in the bit set.
    boolean          immediate = true;                                                                                  // Immediate mode execution by default
    boolean         trackCount = true;                                                                                  // Track the count of number of bits set to one
    Program             parent = null;                                                                                  // Parent program whose code is to be written into.
    UnitMemory.Ref   memoryRef = null;                                                                                  // Program memory to be used

    Build   bitSize (int     BitSize  )          {   bitSize = BitSize;   return this;}                                 // Requested size of bitset
    Build immediate (boolean Immediate)          { immediate = Immediate; return this;}                                 // Execute instructions immediately instead of accumulating them
    Build     count (boolean Count)              {trackCount = Count;     return this;}                                 // Maintain a count field to make retrieval of the count an O(1) operation
    Build    memory (Program.UnitMemory.Ref Ref) { memoryRef = Ref;       return this;}                                 // Memory for bitset
    Build    parent (Program Parent)             {    parent = Parent;    return this;}                                 // Parent program for code

    int units()                                                                                                         // Units needed for the bitset, the bitset trees and the count field
     {final int s = UnitMemory.bitsPerUnit();
      return (s - 1 + 3 * nextPowerOfTwo(bitSize)) / s + (trackCount ? 1 : 0);
     }

    Program.Build build ()                                                                                              // Description of containing program
     {final Program.Build p = new Program.Build();
      if (memoryRef == null) p.memory(units());
      p.immediate(immediate);
      if (parent != null) p.parent(parent);                                                                             // Place code from this program into this parent program
      return p;
     }
   }

  public BitSet(Build Build)                                                                                            // Constructor
   {super(Build.build());
    build            = Build;
    bitSize          = nextPowerOfTwo(size());                                                                          // Record size
    bitSize1         = bitSize - 1;
    bitSize2         = bitSize >>> 1;
    powerOfTwo       = bitSize == size();
    logBitSize       = logTwo(bitSize);
    trackCount       = build.trackCount;
    if (bitSize < 2) stop("Size must be two or more, not:", bitSize);                                                   // There is not much point in bit sets with sizes of less than two.
    unitsSize        = Build.units();                                                                                   // Memory units needed for the bitset and its bit trees
    if (Build.memoryRef != null) memoryRef = Build.memoryRef;  else memoryRef = unitMemory.new Ref(0);                  // Use memory supplied by caller or create a reference to the default memory
    memoryCount      = trackCount ? memoryRef.step(unitsNeeded() - 1) : null;                                           // Maintain a count field at the end to determine the number of bits set to one in O(1) time

    luoVerilog       = "x_bitSet_limitsUpperOne_" +bitSize;                                                             // Verilog procedures to make references to constant arrays rather than holding them in memory
    luzVerilog       = "x_bitSet_limitsUpperZero_"+bitSize;
    lloVerilog       = "x_bitSet_limitsLowerOne_" +bitSize;
    llzVerilog       = "x_bitSet_limitsLowerZero_"+bitSize;
     hoVerilog       = "x_bitSet_heightOne_"      +bitSize;
     hzVerilog       = "x_bitSet_heightZero_"     +bitSize;
     poVerilog       = "x_bitSet_posOne_"         +bitSize;
     pzVerilog       = "x_bitSet_posZero_"        +bitSize;

    limitsUpperOne   = new int[top_one ()+1];  limitsUpperOne();                                                        // Upper limits of ones tree
    limitsUpperZero  = new int[top_zero()+2]; limitsUpperZero();                                                        // Upper limits of zeros tree
    limitsLowerOne   = new int[top_one ()+1];  limitsLowerOne();                                                        // Lower limits of ones tree
    limitsLowerZero  = new int[top_zero()+2]; limitsLowerZero();                                                        // Lower limits of zeros tree
    heightOne        = new int[top_one() +1];       heightOne();                                                        // Height of each node in the ones tree
    heightZero       = new int[top_zero()+2];      heightZero();                                                        // Height of each node in the zeros tree
    posOne           = new int[top_one() +1];     posOneArray();                                                        // Position in row in ones tree from position in zeros tree
    posZero          = new int[top_zero()+2];    posZeroArray();                                                        // Position in row in zeros tree from position in zeros tree
   }

  BitSet initializeMemory () {memoryRef.clear(unitsNeeded()); return this;}                                             // Initialize memory
  int         unitsNeeded () {return build.units();}                                                                    // Number of bytes needed for a bit set of specified size without the ability to locate zeros or ones
  int                size () {return build.bitSize;}                                                                    // Bitset size requested which may differ from the actual size as the size requested is rounded to the next power of two
  Int          logBitSize () {return new Int(logBitSize);}                                                              // Log of bit size as an Int to control for loops searching up and down through the bit tree - Up and down, up and down; I will lead them up and down: I am fear'd in field and town. Goblin, lead them up and down.
  Int               count ()                                                                                            // Count number of set bits in bitset - the performasnc will nbe order (1) if the count is being tracked else N log(N)
   {if (!trackCount) stop("Count capability not requested, use Build.count(true) to do so");
    return memoryCount.getInt();
   }

//D1 Get and Set Bits                                                                                                   // Get and set bits in the bitset setting the corresponding paths in the bits trees

  public void clear (Int Index) {set(Index, new Bool(false));}                                                          // Clear bit and corresponding path bits from the indexed bit to the root of the bit tree
  public void   set (Int Index) {set(Index, new Bool(true ));}                                                          // Set bit and corresponding path bits from the indexed bit to the root of the bit tree
  public void   set (Int Index, Bool Value)                                                                             // Set or clear a bit in the bitset
   {if (immediate())                                                                                                    // Check index is in range
     {if (Index.i() <        0) stop("Index less than zero: ", Index);
      if (Index.i() >= bitSize) stop("Index larger than bitset size:", Index, bitSize);
     }
    new If (getBit(Index).ne(Value))                                                                                    // Bit not already set to the correct value in actual bits
     {void Then()
       {if (trackCount)
         {final Int c = memoryCount.getInt();                                                                           // Current count

          new If (Value)                                                                                                // Change the count if the bit is being changed
           {void Then()
             {memoryCount.putInt(new Int(c.Inc()));
             }
            void Else() {memoryCount.putInt(new Int(c.Dec()));}
           };

          if (immediate())
           {final int C = memoryCount.getInt().i();                                                                     // Check new count is in range
            if (C <       0) stop("Count has gone negative:", C);
            if (C > bitSize) stop("Count has gone too high:", C, bitSize);
           }
         }
        setBitNC(Index, Value);                                                                                         // Set the bit
        new If (Value) {void Then() {setOnePath (Index);} void Else() {clearOnePath (Index);}};                         // Set or clear bits along the path from the indexed bit to the root of the ones  tree
        new If (Value) {void Then() {setZeroPath(Index);} void Else() {clearZeroPath(Index);}};                         // Set or clear bits along the path from the indexed bit to the root of the zeros tree
       }
     };
   }

  Bool      getBit (Int Index)             {if (immediate()) checkInActual(Index); return getBitNC(Index);}             // Get a bit from the bit set
  Bool    getBitNC (Int Index)             {return memoryRef.getBool(Index);}                                           // Get bit value at an index without checking that the index is valid
  boolean getBitNC (int Index)             {return memoryRef.getBool(Index);}                                           // Get bit value at an index without checking that the index is valid

  void      setBit (Int Index, Bool Value) {memoryRef.putBool(Index, Value);}                                           // Set bit value.
  void    setBitNC (Int Index, Bool Value) {memoryRef.putBool(Index, Value);}                                           // Set bit value without checking index
  void    setBitNC (Int Index)             {memoryRef.putBool(Index, new Bool(true));}                                  // Set bit value without checking index
  void  clearBitNC (Int Index)             {memoryRef.putBool(Index, new Bool(false));}                                 // Clear a bit value without checking index

  void setOnePath (Int Index)                                                                                           // Set bits along the path from the indexed bit to the root of the ones tree
   {final Int p = parentOne(new Int(Index));                                                                            // Position in ones tree

    new For(logBitSize())                                                                                               // Set bits along the path to the root of the ones tree
     {void body(Int Index, Bool Continue)
       {new If (getBitNC(p).Flip())                                                                                     // Is the bit not already set
         {void Then() {setBitNC(p); p.set(parentOne(p)); Continue.set(); }                                              // Stop creating the path once we have arrived at a tree bit that is correctly set: as there are no changes at this level the upper levels must be ok too
         };
       }
     };
   }

  void clearOnePath (Int Index)                                                                                         // Clear bits along the path to the root of the ones tree if both children are zero
   {final Int p = parentOne(new Int(Index));                                                                            // Position in ones tree
    new For(logBitSize())
     {void body(Int Index, Bool Continue)
       {new If (getBitNC(p))                                                                                            // Bit might need to be cleared
         {void Then()
           {final Int q = childLowOne(p);
            new If (getBitNC(q).Flip())                                                                                 // Both child bits are clear so the parent should be clear as well
             {void Then()
               {new If (getBitNC(q.Inc()).Flip())
                 {void Then() {clearBitNC(p); p.set(parentOne(p)); Continue.set();}                                     // Zeroed the parent so  keep moving up until we encounter a correctly set parent or the root
                 };
               }
             };
           }
         };
       }
     };
   }

  private void setZeroPath (Int Index)                                                                                  // There is a one in the actual bits below this position in the zeros tree
   {final Int p = parentZero(Index);                                                                                    // Parent position in zeros tree
    final Int q = childLowZero(p);                                                                                      // Children in actual bits
    new If (getBitNC(q))                                                                                                // Both actual bits are one so the parent must be zero indicating no zeros
     {void Then()
       {new If (getBitNC(q.Inc()))
         {void Then()
           {setBitNC(p);                                                                                                // Show parent has no zeros
            p.set(parentZero(p));                                                                                       // Move up

            new For(new Int(logBitSize-1))                                                                              // Remaining possible parents
             {void body(Int Index, Bool Continue)
               {final Int q = childLowZero(p);                                                                          // Children of parent
                new If (getBitNC(q))                                                                                    // Both children are zero so the parent must be zero also
                 {void Then()
                   {new If (getBitNC(q.Inc()))
                     {void Then()
                       {new If (getBitNC(p).Flip())
                         {void Then() {setBitNC(p); p.set(parentZero(p)); Continue.set();}                              // Set parent and continue towards the root
                         };
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
   }

  private void clearZeroPath (Int Index)                                                                                // There is a zero in the actual bits under this position in the zeros tree
   {final Int p = parentZero(Index);                                                                                    // Position in zeros tree
    new For(logBitSize())
     {void body(Int Index, Bool Continue)
       {new If (getBitNC(p))                                                                                            // The bit is not already set
         {void Then() {clearBitNC(p); p.set(parentZero(p)); Continue.set();}                                            // Stop creating the path once we have arrived at a zeros tree bit that is correctly set: as there are no changes at this level the upper levels must be ok too
         };
       }
     };
   }

//D1 Powers and Positions                                                                                               // Positions of each bit in each layer in the ones and zeros trees

  void checkInActual (Int Pos)                                                                                          // Check that we are in the actual bits
   {if (immediate())
     {final int a = 0, b = size(), P = Pos.i();
      if (P < a) stop("Position is below actual bits:", P, a);
      if (P > b) stop("Position is above ones tree:", P, b);
     }
   }

  void checkInOnesTree (Int Pos)                                                                                        // Check that we are in the ones tree
   {if (immediate())
     {final int a = base_one(), b = a + bitSize, P = Pos.i();
      if (P < a) stop("Position is below ones tree:", P, a);
      if (P > b) stop("Position is above ones tree:", P, b);
     }
   }

  void checkInActualOrOnes (Int Pos)                                                                                    // Check that we are in the actual bits or the ones tree
   {if (immediate() && Pos.i() < size()) checkInActual(Pos); else checkInOnesTree(Pos);
   }

  void checkInZerosTree(Int Pos)                                                                                        // Check that we are in the zeros tree
   {if (immediate())
     {final int a = base_zero(), b = a + bitSize, P = Pos.i();
      if (P < a) stop("Position is below zeros tree:", P, a);
      if (P > b) stop("Position is above zeros tree:", P, b);
     }
   }

  void checkInActualOrZeros (Int Pos)                                                                                   // Check that we are in the zeros tree or the actual bits
   {if (immediate() && Pos.i() < size()) checkInActual(Pos); else checkInZerosTree(Pos);
   }

  int       top_zero ()        {return 2 * bitSize - 2 + bitSize1;}                                                     // Top of the zeros tree if it exists - zero based
  int        top_one ()        {return 2 * bitSize - 2;}                                                                // Top of the ones  tree if it exists - zero based
  Int         topOne ()        {return new Int(top_one());}                                                             // Top of the ones  tree if it exists - zero based

  int         posOne (int P)   {return 1 * bitSize + P;}                                                                // Position in the ones  tree if it exists, argument is zero based as is the result
  int        posZero (int P)   {return 2 * bitSize + P - 1;}                                                            // Position in the zeros tree if it exists, argument is zero based as is the result

  Int      zeroToOne (Int Pos) {final Int r = new Int(); new If (Pos.lt(bitSize)) {void Then() {r.set(Pos);} void Else() {r.set(Pos.Sub(bitSize1));}}; return r;} // Translate from Zeros tree to Ones tree
  Int      oneToZero (Int Pos) {final Int r = new Int(); new If (Pos.lt(bitSize)) {void Then() {r.set(Pos);} void Else() {r.set(Pos.Add(bitSize1));}}; return r;} // Translate from Ones tree to Zeros tree

  Int   childHighOne (Int Pos) {return childLowOne(Pos).Inc();}                                                         // Step to the corresponding child high bit index from this parent bit index
  Int    childLowOne (Int Pos) {return Pos.Dec().mul(2).sub(topOne());}                                                 // Step to the corresponding child low  bit index from this parent bit index
  Int      parentOne (Int Pos) {return Pos.Add(topOne()).add(2).down();}                                                // Step to the corresponding parent bit index for this child bit index

  Int   childLowZero (Int Pos) {return oneToZero(childLowOne (zeroToOne(Pos)));}                                        // Step to low child in zeros tree
  Int  childHighZero (Int Pos) {return oneToZero(childHighOne(zeroToOne(Pos)));}                                        // Step to high child in zeros tree
  Int     parentZero (Int Pos) {return oneToZero(parentOne   (zeroToOne(Pos)));}                                        // Step to the corresponding parent bit index for this child bit index

  int      base_zero ()        {return posZero(0);}                                                                     // Start of the zeros tree
  int       base_one ()        {return posOne (0);}                                                                     // Start of the ones tree


  Int       baseZero ()        {final Int r = new Int("baseZero" );         r.T();          new I() {void a() {jt(r, base_zero      ()           );} String v() {return vt(r, ""+posZero(0));}};            r.W(); return r;} //N Position in the current row
  Int        baseOne ()        {final Int r = new Int("baseOne"  );         r.T();          new I() {void a() {jt(r, base_one       ()           );} String v() {return vt(r, ""+posOne (0));}};            r.W(); return r;} //N Position in the current row
  Int       pos_zero (Int Pos) {final Int r = new Int("pos_zero" );         r.T(); Pos.S(); new I() {void a() {jt(r, posZero        [sourceInt()]);} String v() {return vt(r, pzVerilog +"(sourceInt)");}}; r.W(); return r;} // Position in the current row in the zeros tree
  Int        pos_one (Int Pos) {final Int r = new Int("pos_one"  );         r.T(); Pos.S(); new I() {void a() {jt(r, posOne         [sourceInt()]);} String v() {return vt(r, poVerilog +"(sourceInt)");}}; r.W(); return r;} //N Position in the current row in the ones tree

  Int  limitUpperOne (Int Pos) {final Int r = new Int("one  upper limit" ); r.T(); Pos.S(); new I() {void a() {jt(r, limitsUpperOne [sourceInt()]);} String v() {return vt(r, luoVerilog+"(sourceInt)");}}; r.W(); return r;} // Upper limit of the current row in the ones tree
  Int limitUpperZero (Int Pos) {final Int r = new Int("zero upper limit");  r.T(); Pos.S(); new I() {void a() {jt(r, limitsUpperZero[sourceInt()]);} String v() {return vt(r, luzVerilog+"(sourceInt)");}}; r.W(); return r;} // Upper limit of the current row in the zeros tree
  Int  limitLowerOne (Int Pos) {final Int r = new Int("one  lower limit" ); r.T(); Pos.S(); new I() {void a() {jt(r, limitsLowerOne [sourceInt()]);} String v() {return vt(r, lloVerilog+"(sourceInt)");}}; r.W(); return r;} // Lower limit of the current row in the ones tree
  Int limitLowerZero (Int Pos) {final Int r = new Int("zero lower limit");  r.T(); Pos.S(); new I() {void a() {jt(r, limitsLowerZero[sourceInt()]);} String v() {return vt(r, llzVerilog+"(sourceInt)");}}; r.W(); return r;} //N Lower limit of the current row in the zeros tree
  Int      heightOne (Int Pos) {final Int r = new Int("one  height" );      r.T(); Pos.S(); new I() {void a() {jt(r, heightOne      [sourceInt()]);} String v() {return vt(r, hoVerilog +"(sourceInt)");}}; r.W(); return r;} // Height of the specified position in the ones tree
  Int     heightZero (Int Pos) {final Int r = new Int("zero height");       r.T(); Pos.S(); new I() {void a() {jt(r, heightZero     [sourceInt()]);} String v() {return vt(r, hzVerilog +"(sourceInt)");}}; r.W(); return r;} // Height of the specified position in the zeros tree

  void   jt(Int R, int    I) {targetInt(I); targetIntValid(true); jTrace(f("%8d "+R.name+" = %8d", currentPc(), I));}   // Java trace of array look ups
  String vt(Int R, String I) {return "targetInt <= "+I+"; " +     vTrace(  "%8d "+R.name+" = %8d", "pc",        I);}    // Java trace of array look ups

  int       pos_zero (int Pos)                                                                                          // Position in the indicated row of the zeros tree
   {final int p = Pos < bitSize ? Pos : Pos < base_zero() ?  0 : pos_one(Pos - base_zero() + bitSize);
    return p;
   }

  int        pos_one (int Pos)                                                                                          // Position in the indicated row of the ones tree
   {if (Pos < bitSize) return Pos;                                                                                      // In bitset body
    int p = Pos - base_one();
    int b = bitSize2;
    for (int i = 0; i < bitSize; i++) if (p >= b) {p -= b; b >>>= 1;} else break;
    return p;
   }

  void  posZeroArray ()                                                                                                 // Position in row from position in ones tree
   {for (int i = 0, N = top_zero(); i <= N; ++i) posZero[i] = pos_zero(i);
    defineArrayViaVerilogFunction(pzVerilog, posZero);
   }

  void   posOneArray ()                                                                                                 // Position in row from position in ones tree
   {for (int i = 0, N = top_one(); i <= N; ++i) posOne[i] = pos_one(i);
    defineArrayViaVerilogFunction(poVerilog, posOne);
   }

  void limitsUpperOne ()                                                                                                // Upper limits of the ones tree
   {int l = bitSize1, w = bitSize;
    final int N = top_one();
    for (int i = 0; i <= N; ++i) {limitsUpperOne[i] = l; if (i >= l) {w >>>= 1; l += w;}}
    defineArrayViaVerilogFunction(luoVerilog, limitsUpperOne);
   }

  void limitsUpperZero ()                                                                                               // Upper limits of the zeros tree
   {for (int i = 0, N = top_one(); i <= N; ++i)
     {if (i < bitSize) limitsUpperZero[i] = limitsUpperOne[i];
      else {limitsUpperZero[bitSize1 + i] = limitsUpperOne[i] + bitSize1;}
     }
    defineArrayViaVerilogFunction(luzVerilog, limitsUpperZero);
   }

  void limitsLowerOne ()                                                                                                // Lower limits of the ones tree
   {int l = 0, w = bitSize;
    for (int i = 0, N = top_one(); i <= N; ++i) {limitsLowerOne[i] = l; if (i >= l+w-1) {l += w; w >>>= 1;}}
    defineArrayViaVerilogFunction(lloVerilog, limitsLowerOne);
   }

  void limitsLowerZero ()                                                                                               // Lower limits of the zeros tree
   {for (int i = 0, N = top_one(); i <= N; ++i)
     {if (i < bitSize) limitsLowerZero[i] = limitsLowerOne[i];
      else {limitsLowerZero[bitSize1 + i] = limitsLowerOne[i] + bitSize1;}
     }
    defineArrayViaVerilogFunction(llzVerilog, limitsLowerZero);
   }

  void heightOne ()                                                                                                     // Height of each node in the ones tree
   {int l = 0, w = bitSize, h = 0;
    for (int i = 0, N = top_one(); i <= N; ++i) {heightOne[i] = h; if (i >= l+w-1) {l += w; w >>>= 1; ++h;}}
    defineArrayViaVerilogFunction(hoVerilog, heightOne);
   }

  void heightZero ()                                                                                                    // Height of each node in the zeros tree
   {for (int i = 0, N = top_one(); i <= N; ++i)
     {if (i < bitSize) heightZero[i] = heightOne[i]; else {heightZero[bitSize1 + i] = heightOne[i];}
     }
    defineArrayViaVerilogFunction(hzVerilog, heightZero);
   }

  Int lowOne (Int Pos)                                                                                                  // Find the lowest bit position with a one in it below the indicated subtree in the ones tree
   {subStart("Bitset.lowOne");
    checkInActualOrOnes(Pos);
    if (immediate() && getBitNC(Pos).Flip().b()) stop("Cannot go low from Pos:", Pos, this);                            // We can only step down from a one in the ones tree
    final Int p = new Int(Pos);                                                                                         // Position in ones tree
    new ForCount(heightOne(Pos))                                                                                        // Step down through the ones tree to reach the actual bits
     {void body(Int Index)
       {p.set(childLowOne(p));                                                                                          // Lower level bit
        new If (getBitNC(p))                                                                                            // Take lower bit if possible else upper one
         {void Then() {p.set(p);}
          void Else() {p.set(p.Inc());}
         };
       }
     };
    subFinish();
    return p;
   }

  Int highOne (Int Pos)                                                                                                 // Find the highest bit position with a one in it below the indicated subtree in the ones tree
   {subStart("Bitset.highOne");
    checkInActualOrOnes(Pos);
    if (immediate() && getBitNC(Pos).Flip().b()) stop("Cannot go high from Pos:",   Pos, this);                         // We can only step down from a one in the ones tree
    final Int p = new Int(Pos);                                                                                         // Position in ones tree
    new ForCount(heightOne(Pos))                                                                                        // Step down through the ones tree to reach the actual bits
     {void body(Int Index)
       {p.set(childHighOne(p));                                                                                         // Lower level bit
        new If (getBitNC(p))                                                                                            // Choose upper bit over lower bit if possible
         {void Then() {p.set(p);}
          void Else() {p.set(p.Dec());}
         };
       }
     };
    subFinish();
    return p;
   }

  Int lowZero (Int Pos)                                                                                                 // Find the lowest bit position with a zero in it below the indicated subtree in the zeros tree
   {subStart("Bitset.lowZero");
    checkInActualOrZeros(Pos);
    if (immediate() && getBitNC(Pos).b()) stop("Cannot go low from Pos:", Pos, this);                                   // We can only step down from a zero in the zeros tree
    final Int p = new Int(Pos);                                                                                         // Position in zeros tree
    new ForCount (heightZero(Pos))                                                                                      // Step down through the zeros tree to reach the actual bits
     {void body(Int Index)
       {p.set(childLowZero(p));                                                                                         // Lower level bit
        new If (getBitNC(p))                                                                                            // Take lower bit if possible else upper one
         {void Then() {p.set(p.Inc());}
          void Else() {p.set(p);}
         };
       }
     };
    subFinish();
    return p;
   }

  Int highZero (Int Pos)                                                                                                // Find the highest bit position with a zero in it below the indicated subtree in the zeros tree
   {subStart("Bitset.highZero");
    checkInActualOrZeros(Pos);
    if (immediate() && getBitNC(Pos).b()) stop("Cannot go high from Pos:", Pos, this);                                  // We can only step down from a zero in the zeros tree
    final Int p = new Int(Pos);                                                                                         // Position in zeros tree
    new ForCount (heightZero(Pos))                                                                                      // Step down through the zeros tree to reach the actual bits
     {void body(Int Index)
       {p.set(childHighZero(p));                                                                                        // Lower  level bit
        new If (getBitNC(p))                                                                                            // Take upper bit if possible else lower one
         {void Then() {p.set(p.Dec());}
          void Else() {p.set(p);}
         };
       }
     };
    subFinish();
    return p;
   }

  Bool canGoLeftToOne (Int Pos)                                                                                         // Whether we can go left from the current position
   {subStart("Bitset.canGoLeft");
    checkInOnesTree(Pos);
    if (immediate() && getBitNC(Pos).Flip().b()) stop("Cannot go low from Pos:", Pos, this);                            // We can only step down from a one in the ones tree
    final Bool r = new Bool(getBitNC(childLowOne(Pos)));
    subFinish();
    return r;
   }

  Bool canGoRightToOne (Int Pos)                                                                                        // Whether we can go right from the current position
   {subStart("Bitset.canGoRight");
    checkInOnesTree(Pos);
    if (immediate() && getBitNC(Pos).Flip().b()) stop("Cannot go low from Pos:",  Pos, this);                           // We can only step down from a one in the ones tree
    final Bool r = new Bool(getBitNC(childHighOne(Pos)));
    subFinish();
    return r;
   }

//Bool adjacentOnes(Int A, Int B)                                                                                       //N Whether two ones in the actual bits are separated by zero or more zeros
// {final Bool r = new Bool();
//  checkInActual(A);
//  checkInActual(B);
//  if (immediate() && getBitNC(A).Flip().b()) stop("Bitset entry  is not a one", A);
//  if (immediate() && getBitNC(B).Flip().b()) stop("Bitset entry  is not a one", B);
//  new If (A.eq(B))
//   {void Then()
//     {r.clear();
//     }
//    void Else()
//     {new If (A.lt(B))
//       {void Then() {r.set(nextOne(A).eq(B));}
//        void Else() {r.set(nextOne(B).eq(A));}
//       };
//     }
//
//   };
//  return r;
// }

//D1 Locate Ones                                                                                                        // Find the first, last, next, previous bit set to one

  public Bint firstOne()                                                                                                // Find the index of the first set bit
   {subStart("Bitset.firstOne");
    final Int  p = new Int(0); p.name = "p";                                                                            // Offset of first bit
new I() {void a() {say("AAAA", p);} boolean trace() {return false;}};
    final Bint r = new Bint();                                                                                          // Result
    new If (getBit(p))
     {void Then() {r.set(p);          }
      void Else() {r.copy(nextOne(p));}                                                                                 // Use copy because the result might be invalid showing that there is no first one
     };
    subFinish();
    return r;                                                                                                           // Result is valid if found
   }

  public Bint lastOne()                                                                                                 // Find the index of the last set bit
   {subStart("Bitset.lastOne");
    final Int  p = new Int(size()-1);                                                                                   // Offset of last bit
    final Bint r = new Bint();                                                                                          // Result
    new If (getBit(p))
     {void Then() {r.set(p);}
      void Else() {r.copy(prevOne(p));}                                                                                 // Use copy because the result might be invalid showing that there is no last one
     };
    subFinish();
    return r;                                                                                                           // Result is valid if found
   }

  public Bint nextOne(Int Start)                                                                                        // Find the index of the next set bit above the specified start bit. This is the most heavily used routine by far
   {subStart("Bitset.nextOne");
    checkInActual(Start);
    if (immediate()) checkInActual(Start);
    final Bint Next = new Bint();                                                                                       // Next one if any
    final Int  p    = new Int(Start);                                                                                   // Start position

    new For(logBitSize())                                                                                               // Traverse down through the tree to the root
     {void body(Int I, Bool C)
       {final Int q = p.Inc();                                                                                          // Next bit over
        new If (q.le(limitUpperOne(p)))                                                                                 // Found adjacent bit set to one to the right of the path up from the start bit
         {void Then()
           {new If (getBitNC(q))
             {void Then()                                                                                               // Found the adjacent bit to the right
               {Next.set(lowOne(q));
               }
              void Else()                                                                                               // No adjacent one yet
               {p.set(parentOne(p));                                                                                    // Move up to parent
                C.set();                                                                                                // Whether we are done yet
               }
             };
           }
         };
       }
     };

    if (!powerOfTwo)                                                                                                    // Check result is in range if the requested bitset has a size that is not a power of two
     {new If (Next.valid())                                                                                             // Only relevant if there is a next value
       {void Then()
         {new If (Next.i().ge(size()))                                                                                  // Valid but out of range
           {void Then()
             {Next.invalidate();
             }
           };
         }
       };
     }
    subFinish();
    return Next;                                                                                                        // Result is valid if found
   }

  public Bint prevOne(Int Start)                                                                                        // Find the index of the previous set bit below the specified bit
   {subStart("Bitset.nextOne");
    if (immediate()) checkInActual(Start);
    final Bint Prev = new Bint();                                                                                       // Invalid indicates not found
    final Int p     = new Int(Start);                                                                                   // Start position

    new For(logBitSize())                                                                                               // Traverse down through the tree to the root
     {void body(Int I, Bool C)
       {new If (p.gt(limitLowerOne(p)))                                                                                 // Found adjacent bit set to one to the left of the path up from the start bit
         {void Then()                                                                                                   // Found the adjacent bit to the left
           {new If (getBitNC(p.Dec()))                                                                                  // Found adjacent bit set to one to the left of the path up from the start bit
             {void Then()                                                                                               // Found the adjacent bit to the left
               {Prev.set(highOne(p.Dec()));                                                                             // Highest one bit in adjacent ones tree
               }
              void Else()                                                                                               // No adjacent one yet
               {p.set(parentOne(p));                                                                                    // Move up to parent
                C.set();                                                                                                // Whether we are done yet
               }
             };
           }
         };
       }
     };
    subFinish();
    return Prev;                                                                                                        // Result is valid if found
   }

//D1 Locate Zeros                                                                                                       // Find the first, last, next, previous bit set to zero

  public Bint firstZero()                                                                                               // Find the index of the first set bit
   {subStart("Bitset.firstZero");
    final Int  p = new Int(0);
    final Bint r = new Bint();                                                                                          // Result
    new If (getBit(p))
     {void Then() {r.copy(nextZero(p));}                                                                                // Use copy because the result might be invalid showing that there is no first zero
      void Else() {r.set(p);          }
     };
    subFinish();
    return r;                                                                                                           // Result is valid if found
   }

  public Bint lastZero()                                                                                                // Find the index of the last set bit
   {subStart("Bitset.lastZero");
    final Int  p = new Int(size()-1);
    final Bint r = new Bint();                                                                                          // Result
    new If (getBit(p))
     {void Then() {r.copy(prevZero(p));}                                                                                // Use copy because the result might be invalid showing that there is no last zero
      void Else() {r.set(p);           }
     };
    subFinish();
    return r;                                                                                                           // Result is valid if found
   }

  public Bint nextZero(Int Start)                                                                                       // Find the index of the next clear  bit above the specified bit
   {subStart("Bitset.nextZero");
    checkInActual(Start);

    if (immediate()) checkInActual(Start);
    final Bint Next = new Bint();                                                                                       // Invalid indicates not found
    final Int  p    = new Int(Start);                                                                                   // Start position
    final Int  Q    = p.Inc();

    new If (Q.le(limitUpperZero(p)))                                                                                    // Adjacent bit exists
     {void Then()                                                                                                       // Found the adjacent bit to the right
       {new If (getBitNC(Q))                                                                                            // Adjacent bit exists and is set so we must search
         {void Then()                                                                                                   // Found the adjacent bit to the right
           {p.set(parentZero(p));                                                                                       // Move int zeros tree
            new For(logBitSize())                                                                                       // Traverse down through the tree to the root
             {void body(Int I, Bool C)
               {final Int q = p.Inc();                                                                                  // Next bit over

                new If (q.le(limitUpperZero(p)))                                                                        // Found adjacent bit set to one to the right of the path up from the start bit
                 {void Then()                                                                                           // Found the adjacent bit to the right
                   {new If (getBitNC(q).Flip())                                                                         // Found adjacent bit set to one to the right of the path up from the start bit
                     {void Then()                                                                                       // Found the adjacent bit to the right
                       {Next.set(lowZero(q));                                                                           // Lowest one bit in adjacent zeros tree
                       }
                      void Else()                                                                                       // No adjacent one yet
                       {p.set(parentZero(p));                                                                           // Move up to parent
                        C.set();                                                                                        // Whether we are done yet
                       }
                     };
                   }
                 };
               }
             };
           }
          void Else()
           {new If (Q.le(limitUpperZero(p)))                                                                            // Adjacent bit amongst the actual bits exists and is set so we must search
             {void Then() {Next.set(Q);}                                                                                // Next slot is zero and so the one we want
             };
           }
         };
       }
     };

    if (!powerOfTwo)                                                                                                    // Check result is in range if the requested bitset has a size that is not a power of two
     {new If (Next.valid())                                                                                             // Only relevant if there is a next value
       {void Then()
         {new If (Next.i().ge(size()))                                                                                  // Valid but out of range
           {void Then()
             {Next.invalidate();
             }
           };
         }
       };
     }
    subFinish();
    return Next;                                                                                                        // Result is valid if found
   }

  public Bint prevZero(Int Start)                                                                                       // Find the index of the previous set bit below the specified bit
   {subStart("Bitset.prevZero");
    checkInActual(Start);
    if (immediate()) checkInActual(Start);
    final Bint Prev = new Bint();                                                                                       // Location of previous zero or invalid of there is not one
    final Int  p    = new Int(Start);                                                                                   // Start position in body of bitset
    new If (p.gt(0))                                                                                                    // Not at the start of bitset
     {void Then()
       {final Int q = p.Dec();                                                                                          // Position to left
        new If (getBitNC(q).Flip())                                                                                     // Adjacent zero
         {void Then()
           {Prev.set(q);
           }
          void Else()                                                                                                   // Go down through zeros tree looking for a one
           {new For(logBitSize())
             {void body(Int Up, Bool ContinueUp)
               {p.set(parentZero(p));                                                                                   // Every bit has a parent except the topmost bit in the tree but the loop will terminated on count before then
                new If (pos_zero(p).gt(0))                                                                              // At start of row
                 {void Then()                                                                                           // At start of row - not found
                   {p.dec();                                                                                            // Position to left
                    new If (getBitNC(p).Flip())                                                                         // Found a one
                    {void Then()                                                                                        // Reached a one so we turn over and head back up the tree going as high as possible
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
    subFinish();
    return Prev;
   }

//D1 Statistics                                                                                                         // Count the number of ones or zero bits in a bit set

//D2 Full or empty                                                                                                      // Check whether a bit set is full or empty

  public Bool full () {return new Bool(firstZero().notValid());}                                                        // Whether the bitset is full - in log N time. It might be better to keep a separate count field if the extra overhead can be justified
  public Bool empty() {return new Bool(firstOne ().notValid());}                                                        // Whether the bitset is empty

  public Bool twoOrMoreOnes()                                                                                           //N Whether there two or more ones in the bitset
   {subStart("Bitset.twoOrMoreOnes");
    final Bool r = new Bool(false);                                                                                     // Assume contrary
    final Int  p = new Int(topOne());                                                                                   // Start at top of ones tree

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
                  void Else() {new I() {void a() {stop("Should not happen");} boolean trace() {return false;}};}        // Both high and low are zero but this should not happen
                 };
               }
             };
           }
         };
       }
     };
    subFinish();
    return r;                                                                                                           // Whether the bitset has two or more ones
   }

//D2 Counts                                                                                                             // The number of bits set to zero or one in the bitset. Superceded by Slots.count as it is believed that the extra cost of maintaining the count is offset by faster access to the current count. However, the count is not being maintained at the bitset level to avoid duplicating effort.  It might be better to transfer the counting logic to Bitset from Slots with the possibility of making it optional when a count is not required

  public Int countAllOnes()                                                                                             // Count ones in bitset
   {subStart("Bitset.countAllOnes");
    final Int  c = new Int(0);                                                                                          // Count
    final Bint p = firstOne();                                                                                          // Position in bitset starting at first one
    new For(new Int(size()))                                                                                            // Step from one to one
     {void body(Int Index, Bool Continue)
       {new If (p.valid())                                                                                              // Latest step is valid
         {void Then()
           {c.inc();
            final Bint q = nextOne(p.i());                                                                              // Step to next one
            new If (q.valid())                                                                                          // Valid if we are still in the bitset
             {void Then()
               {p.copy(q);                                                                                              // Continue from the found one
                Continue.set(true);                                                                                     // Continue stepping
               }
             };
           }
         };
       }
     };
    subFinish();
    return c;                                                                                                           // Return count
   };

  public Int countAllZeros()                                                                                            // Count zeros in bitset
   {subStart("Bitset.countAllZeros");
    final Int  c = new Int(0);                                                                                          // Count
    final Bint p = firstZero();
    new For(new Int(size()))
     {void body(Int Index, Bool Continue)
       {new If (p.valid())
         {void Then()
           {c.inc();
            final Bint q = nextZero(p.i());                                                                             // Next zero
            new If (q.valid())
             {void Then()
               {p.copy(q);                                                                                              // Continue from the found zero
                Continue.set(true);                                                                                     // Continue stepping
               }
             };
           }
         };
       }
     };
    subFinish();
    return c;                                                                                                           // Return count
   };

//D1 Print                                                                                                              // Print the bit set

  public String toString ()                                                                                             // Print bit set so we can visualize it. This will not be available on the chip so we use normal Java
   {subStart("Bitset.toString");
    final StringBuilder s = new StringBuilder();
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

    subFinish();
    return ""+s;
   }

//D1 Tests                                                                                                              // Tests

  static BitSet test_bits(boolean Ex, int N)                {return test_bits(Ex, N, false);}                           // Create test bitset.
  static BitSet test_bits(boolean Ex, int N, boolean Count)                                                             // Create test bitset.
   {subStart("BitSet.test_bits");
    final Build build = new Build().bitSize(N).immediate(Ex);                                                           // Describe bitset
    final BitSet    b = new BitSet(build  );                                                                            // Create a bit set
    subFinish();
    return b;                                                                                                           // Return test bitset.
   }

  static void test_prevNext(boolean Ex)
   {final BitSet b = test_bits(Ex, 32);
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
   1   63   16 |  0  0  0  0  0  0  0  0  0  0  0  0  1  1  0  1
   2   79    8 |  0  0  0  0  0  0  1  0
   3   87    4 |  0  0  0  0
   4   91    2 |  0  0
   5   93    1 |  0
""");

    final Int o = b.countAllOnes ().ok( 9);
    final Int z = b.countAllZeros().ok(23);

    //b.adjacentOnes(b.new Int(13), b.new Int(19)).ok(true);
    //b.adjacentOnes(b.new Int(13), b.new Int(24)).ok(false);
    //b.adjacentOnes(b.new Int(24), b.new Int(19)).ok(true);
    //b.adjacentOnes(b.new Int(25), b.new Int(19)).ok(false);
    //b.adjacentOnes(b.new Int(25), b.new Int(25)).ok(false);

    if (true)
     {final Bint q = b.prevZero(b.new Int(14));
      q.ok(true);
      q.ok(12);
     }

    for (int i : range(13))     b.nextOne(b.new Int( i)).ok( 13);
    for (int i : range(13, 19)) b.nextOne(b.new Int( i)).ok( 19);
    for (int i : range(19, 24)) b.nextOne(b.new Int( i)).ok( 24);
    for (int i : range(23, 28)) b.nextOne(b.new Int( i)).ok(i+1);
                                b.nextOne(b.new Int(28)).ok( 30);
                                b.nextOne(b.new Int(29)).ok( 30);
                                b.nextOne(b.new Int(30)).ok( 31);
                                b.nextOne(b.new Int(31)).ok(false);

    for (int i : range(14))     b.prevOne(b.new Int( i)).ok(false);

    for (int i : range(14, 20)) b.prevOne(b.new Int( i)).ok( 13);
    for (int i : range(20, 24)) b.prevOne(b.new Int( i)).ok( 19);
    for (int i : range(25, 29)) b.prevOne(b.new Int( i)).ok(i-1);
                                b.prevOne(b.new Int(30)).ok( 28);
                                b.prevOne(b.new Int(31)).ok( 30);

                                b.firstOne().ok(13);
                                b. lastOne().ok(31);

    for (int i : range(12))     b.nextZero(b.new Int( i)).ok(i+1);
                                b.nextZero(b.new Int(12)).ok( 14);
    for (int i : range(13, 18)) b.nextZero(b.new Int( i)).ok(i+1);
    for (int i : range(19, 23)) b.nextZero(b.new Int( i)).ok(i+1);
    for (int i : range(23, 28)) b.nextZero(b.new Int( i)).ok( 29);
    for (int i : range(29, 32)) b.nextZero(b.new Int( i)).ok(false);


                                b.prevZero(b.new Int( 0)).ok(false);
    for (int i : range( 1, 14)) b.prevZero(b.new Int( i)).ok(i-1);
                                b.prevZero(b.new Int(14)).ok( 12);
    for (int i : range(15, 19)) b.prevZero(b.new Int( i)).ok(i-1);
                                b.prevZero(b.new Int(20)).ok( 18);
    for (int i : range(21, 24)) b.prevZero(b.new Int( i)).ok(i-1);
    for (int i : range(24, 30)) b.prevZero(b.new Int( i)).ok( 23);
    for (int i : range(30, 32)) b.prevZero(b.new Int( i)).ok( 29);

                                b.firstZero().ok( 0);
                                b. lastZero().ok(29);
    b.maxSteps(999_999);
    b.execute();
   }

  static void test_prevNext()                                                                                           // Test tree of searchable one bits
   {          test_prevNext(true);
              test_prevNext(false);
   }

  static void test_prevNext01(boolean Ex)                                                                               // Test tree of searchable one bits
   {sayCurrentTestName();
    final int N = 16;
    final BitSet b = test_bits(Ex, N);

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
   1   31    8 |  1  1  0  0  1  1  0  0
   2   39    4 |  1  0  1  0
   3   43    2 |  0  0
   4   45    1 |  0
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

    b.maxSteps(999_999);
    b.execute();
   }

  static void test_prevNext01()
   {          test_prevNext01(true);
              test_prevNext01(false);
   }

  static void test_prevNext10(boolean Ex)
   {sayCurrentTestName();
    final int N = 16;
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
   1   31    8 |  0  0  1  1  0  0  1  1
   2   39    4 |  0  1  0  1
   3   43    2 |  0  0
   4   45    1 |  0
""");

    for (int i : range( 3))     b.nextZero(b.new Int(i)).ok(i+1);
    for (int i : range( 3, 8))  b.nextZero(b.new Int(i)).ok(  8);
    for (int i : range( 7, 11)) b.nextZero(b.new Int(i)).ok(i+1);
    for (int i : range(11, 16)) b.nextZero(b.new Int(i)).ok(false);

                                b.prevZero(b.new Int(0)).ok(false);
    for (int i : range( 1,  5)) b.prevZero(b.new Int(i)).ok(i-1);
    for (int i : range( 4,  8)) b.prevZero(b.new Int(i)).ok(  3);
    for (int i : range( 9, 13)) b.prevZero(b.new Int(i)).ok(i-1);
    for (int i : range(12, 16)) b.prevZero(b.new Int(i)).ok( 11);

    for (int i : range( 4))     b.nextOne(b.new Int( i)).ok(  4);
    for (int i : range( 3,  7)) b.nextOne(b.new Int( i)).ok(i+1);
    for (int i : range( 7, 12)) b.nextOne(b.new Int( i)).ok( 12);
    for (int i : range(11, 15)) b.nextOne(b.new Int( i)).ok(i+1);
                                b.nextOne(b.new Int(15)).ok(false);

    for (int i : range( 5))     b.prevOne(b.new Int( i)).ok(false);
    for (int i : range( 5,  8)) b.prevOne(b.new Int( i)).ok(i-1);
    for (int i : range( 8, 13)) b.prevOne(b.new Int( i)).ok(  7);
    for (int i : range(13, 16)) b.prevOne(b.new Int( i)).ok(i-1);
                                b.firstOne()            .ok(  4);
                                b.lastOne ()            .ok( 15);
    b.maxSteps(999_999);
    b.execute();
   }

  static void test_prevNext10()
   {          test_prevNext10(true);
              test_prevNext10(false);
   }

  static void test_oneZero(boolean Ex)
   {sayCurrentTestName();
    final int N = 8;
    final BitSet b = test_bits(Ex, N, true);
    final StringBuilder s = new StringBuilder();
    b.new I() {void a() {s.append("Start:\n"+b);}         boolean trace() {return false;}};

    for (int i : range(N))
     {b.count().ok(i);
      b.set(b.new Int(i));
      b.new I() {void a() {s.append("Set: "+i+"\n"+b);}   boolean trace() {return false;}};
     }
    for (int i : range(N))
     {b.count().ok(N-i);
      b.clear(b.new Int(i));
      b.new I() {void a() {s.append("Clear: "+i+"\n"+b);} boolean trace() {return false;}};
     }
    b.count().ok(0);
    b.maxSteps(999_999);
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
   1   15    4 |  0  0  0  0
   2   19    2 |  0  0
   3   21    1 |  0
Set: 0
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  1  0  0  0  0  0  0  0
One:
   2    8    4 |  1  0  0  0
   3   12    2 |  1  0
   4   14    1 |  1
Zero:
   1   15    4 |  0  0  0  0
   2   19    2 |  0  0
   3   21    1 |  0
Set: 1
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  1  1  0  0  0  0  0  0
One:
   2    8    4 |  1  0  0  0
   3   12    2 |  1  0
   4   14    1 |  1
Zero:
   1   15    4 |  1  0  0  0
   2   19    2 |  0  0
   3   21    1 |  0
Set: 2
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  1  1  1  0  0  0  0  0
One:
   2    8    4 |  1  1  0  0
   3   12    2 |  1  0
   4   14    1 |  1
Zero:
   1   15    4 |  1  0  0  0
   2   19    2 |  0  0
   3   21    1 |  0
Set: 3
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  1  1  1  1  0  0  0  0
One:
   2    8    4 |  1  1  0  0
   3   12    2 |  1  0
   4   14    1 |  1
Zero:
   1   15    4 |  1  1  0  0
   2   19    2 |  1  0
   3   21    1 |  0
Set: 4
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  1  1  1  1  1  0  0  0
One:
   2    8    4 |  1  1  1  0
   3   12    2 |  1  1
   4   14    1 |  1
Zero:
   1   15    4 |  1  1  0  0
   2   19    2 |  1  0
   3   21    1 |  0
Set: 5
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  1  1  1  1  1  1  0  0
One:
   2    8    4 |  1  1  1  0
   3   12    2 |  1  1
   4   14    1 |  1
Zero:
   1   15    4 |  1  1  1  0
   2   19    2 |  1  0
   3   21    1 |  0
Set: 6
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  1  1  1  1  1  1  1  0
One:
   2    8    4 |  1  1  1  1
   3   12    2 |  1  1
   4   14    1 |  1
Zero:
   1   15    4 |  1  1  1  0
   2   19    2 |  1  0
   3   21    1 |  0
Set: 7
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  1  1  1  1  1  1  1  1
One:
   2    8    4 |  1  1  1  1
   3   12    2 |  1  1
   4   14    1 |  1
Zero:
   1   15    4 |  1  1  1  1
   2   19    2 |  1  1
   3   21    1 |  1
Clear: 0
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  0  1  1  1  1  1  1  1
One:
   2    8    4 |  1  1  1  1
   3   12    2 |  1  1
   4   14    1 |  1
Zero:
   1   15    4 |  0  1  1  1
   2   19    2 |  0  1
   3   21    1 |  0
Clear: 1
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  0  0  1  1  1  1  1  1
One:
   2    8    4 |  0  1  1  1
   3   12    2 |  1  1
   4   14    1 |  1
Zero:
   1   15    4 |  0  1  1  1
   2   19    2 |  0  1
   3   21    1 |  0
Clear: 2
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  0  0  0  1  1  1  1  1
One:
   2    8    4 |  0  1  1  1
   3   12    2 |  1  1
   4   14    1 |  1
Zero:
   1   15    4 |  0  0  1  1
   2   19    2 |  0  1
   3   21    1 |  0
Clear: 3
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  0  0  0  0  1  1  1  1
One:
   2    8    4 |  0  0  1  1
   3   12    2 |  0  1
   4   14    1 |  1
Zero:
   1   15    4 |  0  0  1  1
   2   19    2 |  0  1
   3   21    1 |  0
Clear: 4
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  0  0  0  0  0  1  1  1
One:
   2    8    4 |  0  0  1  1
   3   12    2 |  0  1
   4   14    1 |  1
Zero:
   1   15    4 |  0  0  0  1
   2   19    2 |  0  0
   3   21    1 |  0
Clear: 5
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  0  0  0  0  0  0  1  1
One:
   2    8    4 |  0  0  0  1
   3   12    2 |  0  1
   4   14    1 |  1
Zero:
   1   15    4 |  0  0  0  1
   2   19    2 |  0  0
   3   21    1 |  0
Clear: 6
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  0  0  0  0  0  0  0  1
One:
   2    8    4 |  0  0  0  1
   3   12    2 |  0  1
   4   14    1 |  1
Zero:
   1   15    4 |  0  0  0  0
   2   19    2 |  0  0
   3   21    1 |  0
Clear: 7
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  0  0  0  0  0  0  0  0
One:
   2    8    4 |  0  0  0  0
   3   12    2 |  0  0
   4   14    1 |  0
Zero:
   1   15    4 |  0  0  0  0
   2   19    2 |  0  0
   3   21    1 |  0
""");
   }

  static void test_oneZero()
   {          test_oneZero(true);
              test_oneZero(false);
   }

  static void test_fullEmpty(int N, boolean Ex)
   {sayCurrentTestName();
    final BitSet b = test_bits(Ex, N);

    b.empty().ok(true);
    b.new ForCount(b.new Int(N))
     {void body(Int Index)
       {b.full ().ok(false);
        final Int c = b.countAllOnes();
        c.ok(Index);
        b.set(b.new Int(Index));
        b.empty().ok(false);
        b.countAllZeros().ok(b.new Int(N).Dec().sub(Index));
       }
     };
    b.full().ok(true);

    b.maxSteps(999_999);
    b.execute();
   }

  static void test_fullEmpty()
   {          test_fullEmpty( 9, true);
              test_fullEmpty( 9, false);
              test_fullEmpty(16, true);
              test_fullEmpty(16, false);
   }

  static void test_count(int N, boolean Ex)
   {sayCurrentTestName();
    final BitSet b = test_bits(Ex, N);

    b.set(b.new Int(  1),   b.new Bool(true));
    b.set(b.new Int(N-1),   b.new Bool(true));
    b.countAllOnes ().ok(2);
    b.countAllZeros().ok(N-2);

    b.set(b.new Int(N/2),   b.new Bool(true));
    b.countAllOnes ().ok(3);
    b.countAllZeros().ok(N-3);

    b.set(b.new Int(N/2+1), b.new Bool(true));
    b.countAllOnes ().ok(4);
    b.countAllZeros().ok(N-4);

    b.maxSteps(999_999);
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
   {sayCurrentTestName();
    final int N = 16;
    final BitSet b = test_bits(Ex, N);

    for (int i : range(N)) if ((i > 4 && i < 8) || (i > 10 && i < 12)) b.set(b.new Int(i), b.new Bool(true));

    b.topOne().ok(30); /*b.topZero().ok(45);*/ b.baseOne() .ok(16); b.baseZero().ok(31);

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
   1   31    8 |  0  0  0  1  0  0  0  0
   2   39    4 |  0  0  0  0
   3   43    2 |  0  0
   4   45    1 |  0
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

    b.canGoLeftToOne(b.new Int(30)).ok( true); b.canGoRightToOne(b.new Int(30)).ok( true);
    b.canGoLeftToOne(b.new Int(26)).ok(false); b.canGoRightToOne(b.new Int(26)).ok( true);
    b.canGoLeftToOne(b.new Int(29)).ok( true); b.canGoRightToOne(b.new Int(29)).ok(false);

    b.baseOne() .ok(16); b.baseZero().ok(31);
    b.execute();
   }

  static void test_powerPosOneZero()
   {          test_powerPosOneZero(true);
              test_powerPosOneZero(false);
   }

  static void test_twoOrMoreOnes(boolean Ex)
   {sayCurrentTestName();
    final BitSet b = test_bits(Ex, 32);

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

/*
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

  static void test_limits(boolean Ex)
   {sayCurrentTestName();
    final BitSet b = test_bits(Ex, 16);

    b.limitUpperOne(b.new Int( 0)).ok(b.new Int(15));  b.limitUpperZero(b.new Int(    0)).ok(b.new Int(15));
    b.limitUpperOne(b.new Int( 1)).ok(b.new Int(15));  b.limitUpperZero(b.new Int(    1)).ok(b.new Int(15));
    b.limitUpperOne(b.new Int( 2)).ok(b.new Int(15));  b.limitUpperZero(b.new Int(    2)).ok(b.new Int(15));
    b.limitUpperOne(b.new Int( 3)).ok(b.new Int(15));  b.limitUpperZero(b.new Int(    3)).ok(b.new Int(15));
    b.limitUpperOne(b.new Int( 4)).ok(b.new Int(15));  b.limitUpperZero(b.new Int(    4)).ok(b.new Int(15));
    b.limitUpperOne(b.new Int( 5)).ok(b.new Int(15));  b.limitUpperZero(b.new Int(    5)).ok(b.new Int(15));
    b.limitUpperOne(b.new Int( 6)).ok(b.new Int(15));  b.limitUpperZero(b.new Int(    6)).ok(b.new Int(15));
    b.limitUpperOne(b.new Int( 7)).ok(b.new Int(15));  b.limitUpperZero(b.new Int(    7)).ok(b.new Int(15));
    b.limitUpperOne(b.new Int( 8)).ok(b.new Int(15));  b.limitUpperZero(b.new Int(    8)).ok(b.new Int(15));
    b.limitUpperOne(b.new Int( 9)).ok(b.new Int(15));  b.limitUpperZero(b.new Int(    9)).ok(b.new Int(15));
    b.limitUpperOne(b.new Int(10)).ok(b.new Int(15));  b.limitUpperZero(b.new Int(   10)).ok(b.new Int(15));
    b.limitUpperOne(b.new Int(11)).ok(b.new Int(15));  b.limitUpperZero(b.new Int(   11)).ok(b.new Int(15));
    b.limitUpperOne(b.new Int(12)).ok(b.new Int(15));  b.limitUpperZero(b.new Int(   12)).ok(b.new Int(15));
    b.limitUpperOne(b.new Int(13)).ok(b.new Int(15));  b.limitUpperZero(b.new Int(   13)).ok(b.new Int(15));
    b.limitUpperOne(b.new Int(14)).ok(b.new Int(15));  b.limitUpperZero(b.new Int(   14)).ok(b.new Int(15));
    b.limitUpperOne(b.new Int(15)).ok(b.new Int(15));  b.limitUpperZero(b.new Int(   15)).ok(b.new Int(15));
    b.limitUpperOne(b.new Int(16)).ok(b.new Int(23));  b.limitUpperZero(b.new Int(15+16)).ok(b.new Int(15+23));
    b.limitUpperOne(b.new Int(17)).ok(b.new Int(23));  b.limitUpperZero(b.new Int(15+17)).ok(b.new Int(15+23));
    b.limitUpperOne(b.new Int(18)).ok(b.new Int(23));  b.limitUpperZero(b.new Int(15+18)).ok(b.new Int(15+23));
    b.limitUpperOne(b.new Int(19)).ok(b.new Int(23));  b.limitUpperZero(b.new Int(15+19)).ok(b.new Int(15+23));
    b.limitUpperOne(b.new Int(20)).ok(b.new Int(23));  b.limitUpperZero(b.new Int(15+20)).ok(b.new Int(15+23));
    b.limitUpperOne(b.new Int(21)).ok(b.new Int(23));  b.limitUpperZero(b.new Int(15+21)).ok(b.new Int(15+23));
    b.limitUpperOne(b.new Int(22)).ok(b.new Int(23));  b.limitUpperZero(b.new Int(15+22)).ok(b.new Int(15+23));
    b.limitUpperOne(b.new Int(23)).ok(b.new Int(23));  b.limitUpperZero(b.new Int(15+23)).ok(b.new Int(15+23));
    b.limitUpperOne(b.new Int(24)).ok(b.new Int(27));  b.limitUpperZero(b.new Int(15+24)).ok(b.new Int(15+27));
    b.limitUpperOne(b.new Int(25)).ok(b.new Int(27));  b.limitUpperZero(b.new Int(15+25)).ok(b.new Int(15+27));
    b.limitUpperOne(b.new Int(26)).ok(b.new Int(27));  b.limitUpperZero(b.new Int(15+26)).ok(b.new Int(15+27));
    b.limitUpperOne(b.new Int(27)).ok(b.new Int(27));  b.limitUpperZero(b.new Int(15+27)).ok(b.new Int(15+27));
    b.limitUpperOne(b.new Int(28)).ok(b.new Int(29));  b.limitUpperZero(b.new Int(15+28)).ok(b.new Int(15+29));
    b.limitUpperOne(b.new Int(29)).ok(b.new Int(29));  b.limitUpperZero(b.new Int(15+29)).ok(b.new Int(15+29));
    b.limitUpperOne(b.new Int(30)).ok(b.new Int(30));  b.limitUpperZero(b.new Int(15+30)).ok(b.new Int(15+30));

    b.limitLowerOne(b.new Int( 0)).ok(b.new Int( 0));  b.limitLowerZero(b.new Int(    0)).ok(b.new Int( 0));
    b.limitLowerOne(b.new Int( 1)).ok(b.new Int( 0));  b.limitLowerZero(b.new Int(    1)).ok(b.new Int( 0));
    b.limitLowerOne(b.new Int( 2)).ok(b.new Int( 0));  b.limitLowerZero(b.new Int(    2)).ok(b.new Int( 0));
    b.limitLowerOne(b.new Int( 3)).ok(b.new Int( 0));  b.limitLowerZero(b.new Int(    3)).ok(b.new Int( 0));
    b.limitLowerOne(b.new Int( 4)).ok(b.new Int( 0));  b.limitLowerZero(b.new Int(    4)).ok(b.new Int( 0));
    b.limitLowerOne(b.new Int( 5)).ok(b.new Int( 0));  b.limitLowerZero(b.new Int(    5)).ok(b.new Int( 0));
    b.limitLowerOne(b.new Int( 6)).ok(b.new Int( 0));  b.limitLowerZero(b.new Int(    6)).ok(b.new Int( 0));
    b.limitLowerOne(b.new Int( 7)).ok(b.new Int( 0));  b.limitLowerZero(b.new Int(    7)).ok(b.new Int( 0));
    b.limitLowerOne(b.new Int( 8)).ok(b.new Int( 0));  b.limitLowerZero(b.new Int(    8)).ok(b.new Int( 0));
    b.limitLowerOne(b.new Int( 9)).ok(b.new Int( 0));  b.limitLowerZero(b.new Int(    9)).ok(b.new Int( 0));
    b.limitLowerOne(b.new Int(10)).ok(b.new Int( 0));  b.limitLowerZero(b.new Int(   10)).ok(b.new Int( 0));
    b.limitLowerOne(b.new Int(11)).ok(b.new Int( 0));  b.limitLowerZero(b.new Int(   11)).ok(b.new Int( 0));
    b.limitLowerOne(b.new Int(12)).ok(b.new Int( 0));  b.limitLowerZero(b.new Int(   12)).ok(b.new Int( 0));
    b.limitLowerOne(b.new Int(13)).ok(b.new Int( 0));  b.limitLowerZero(b.new Int(   13)).ok(b.new Int( 0));
    b.limitLowerOne(b.new Int(14)).ok(b.new Int( 0));  b.limitLowerZero(b.new Int(   14)).ok(b.new Int( 0));
    b.limitLowerOne(b.new Int(15)).ok(b.new Int( 0));  b.limitLowerZero(b.new Int(   15)).ok(b.new Int( 0));
    b.limitLowerOne(b.new Int(16)).ok(b.new Int(16));  b.limitLowerZero(b.new Int(15+16)).ok(b.new Int(15+16));
    b.limitLowerOne(b.new Int(17)).ok(b.new Int(16));  b.limitLowerZero(b.new Int(15+17)).ok(b.new Int(15+16));
    b.limitLowerOne(b.new Int(18)).ok(b.new Int(16));  b.limitLowerZero(b.new Int(15+18)).ok(b.new Int(15+16));
    b.limitLowerOne(b.new Int(19)).ok(b.new Int(16));  b.limitLowerZero(b.new Int(15+19)).ok(b.new Int(15+16));
    b.limitLowerOne(b.new Int(20)).ok(b.new Int(16));  b.limitLowerZero(b.new Int(15+20)).ok(b.new Int(15+16));
    b.limitLowerOne(b.new Int(21)).ok(b.new Int(16));  b.limitLowerZero(b.new Int(15+21)).ok(b.new Int(15+16));
    b.limitLowerOne(b.new Int(22)).ok(b.new Int(16));  b.limitLowerZero(b.new Int(15+22)).ok(b.new Int(15+16));
    b.limitLowerOne(b.new Int(23)).ok(b.new Int(16));  b.limitLowerZero(b.new Int(15+23)).ok(b.new Int(15+16));
    b.limitLowerOne(b.new Int(24)).ok(b.new Int(24));  b.limitLowerZero(b.new Int(15+24)).ok(b.new Int(15+24));
    b.limitLowerOne(b.new Int(25)).ok(b.new Int(24));  b.limitLowerZero(b.new Int(15+25)).ok(b.new Int(15+24));
    b.limitLowerOne(b.new Int(26)).ok(b.new Int(24));  b.limitLowerZero(b.new Int(15+26)).ok(b.new Int(15+24));
    b.limitLowerOne(b.new Int(27)).ok(b.new Int(24));  b.limitLowerZero(b.new Int(15+27)).ok(b.new Int(15+24));
    b.limitLowerOne(b.new Int(28)).ok(b.new Int(28));  b.limitLowerZero(b.new Int(15+28)).ok(b.new Int(15+28));
    b.limitLowerOne(b.new Int(29)).ok(b.new Int(28));  b.limitLowerZero(b.new Int(15+29)).ok(b.new Int(15+28));
    b.limitLowerOne(b.new Int(30)).ok(b.new Int(30));  b.limitLowerZero(b.new Int(15+30)).ok(b.new Int(15+30));

    b.maxSteps(999_999);
    b.execute();
   }

  static void test_limits()
   {          test_limits(true);
              test_limits(false);
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

  static void test_lowHighZero(boolean Ex)
   {sayCurrentTestName();
    final int N = 16;
    final BitSet b = test_bits(Ex, N);

    for (int i : range(N)) if ((i > 4 && i < 8) || (i > 10 && i < 12)) b.set(b.new Int(i), b.new Bool(true));

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
   1   31    8 |  0  0  0  1  0  0  0  0
   2   39    4 |  0  0  0  0
   3   43    2 |  0  0
   4   45    1 |  0
""");

    b.highZero(b.new Int(15+30)).ok(15);
    b.highZero(b.new Int(15+29)).ok(15);
    b.highZero(b.new Int(15+28)).ok( 4);
    b.highZero(b.new Int(15+27)).ok(15);
    b.highZero(b.new Int(15+26)).ok(10);
    b.highZero(b.new Int(15+25)).ok( 4);
    b.highZero(b.new Int(15+24)).ok( 3);
    b.highZero(b.new Int(15+23)).ok(15);
    b.highZero(b.new Int(15+22)).ok(13);
    b.highZero(b.new Int(15+21)).ok(10);
    b.highZero(b.new Int(15+20)).ok( 9);
    b.highZero(b.new Int(15+18)).ok( 4);
    b.highZero(b.new Int(15+17)).ok( 3);
    b.highZero(b.new Int(15+16)).ok( 1);

    b.maxSteps(999_999);
    b.execute();
   }

  static void test_lowHighZero()
   {          test_lowHighZero(true);
              test_lowHighZero(false);
   }

  static void test_4(boolean Ex)
   {sayCurrentTestName();
    final int N = 8;
    final BitSet b = test_bits(Ex, N);

    for (int i : range(N)) if (i == 2 || i == 4 || i == 5 || i == 6) b.set(b.new Int(i));

    //stop(b);
    b.ok(()->b, """
BitSet            0  1  2  3  4  5  6  7
   1    0    8 |  0  0  1  0  1  1  1  0
One:
   2    8    4 |  0  1  1  1
   3   12    2 |  1  1
   4   14    1 |  1
Zero:
   1   15    4 |  0  0  1  0
   2   19    2 |  0  0
   3   21    1 |  0
""");

    b.prevZero(b.new Int(2)).ok(1);
    b.nextZero(b.new Int(2)).ok(3);
   }

  static void test_4()
   {          test_4(true);
              test_4(false);
   }

  static void oldTests()                                                                                                // Tests thought to be stable.
   {if (rtg( 1)) test_prevNext();
    if (rtg( 2)) test_prevNext01();
    if (rtg( 3)) test_prevNext10();
    if (rtg( 4)) test_oneZero();
    if (rtg( 5)) test_fullEmpty();
    if (rtg( 6)) test_count();
    if (rtg( 7)) test_powerPosOneZero();
    if (rtg( 8)) test_twoOrMoreOnes();
    if (rtg( 9)) test_limits();
    if (rtg(10)) test_lowHighZero();
    if (rtg(11)) test_4();
   }

  static void newTests()                                                                                                // Tests under development.
   {oldTests();                                                                                                         // Run baseline tests.
   }

  public static void main(String[] args)                                                                                // Program entry point for testing.
   {testGroup = args.length > 0 ? args[0] : null;                                                                       // Test groups if supplied
    try                                                                                                                 // Protected execution block.
     {deleteAllFiles(verilogFolder, 99);                                                                                // Delete generated Verilog files created by a prior run of the current test
      if (github_actions) oldTests(); else newTests();                                                                  // Select tests.
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
