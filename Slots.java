//----------------------------------------------------------------------------------------------------------------------
// Distributed sparse slots used to hold the key of the Btree
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

class Slots extends Program                                                                                             // A tree that translates keys into values to be implemented as an application specific integrated circuit
 {final int                 numberOfKeys;                                                                               // The maximum number of references maintained by these slots
  final int                         size;                                                                               // Number of bytes needed to hold slots
  final BitSet            usedSlotsToKeys;                                                                              // The slots in use.  There are more slots than references so that they can be distributed with intervening empty slots to make insertions faster
  final BitSet                   usedKeys;                                                                              // The references in use.
  ByteMemory.Ref            byteMemoryRef = null;                                                                       // Byte memory reference containing the slots
  final ByteMemory.Ref     refSlotsToKeys;                                                                              // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
  final ByteMemory.Ref     refKeysToSlots;                                                                              // The slot associated with each in use key
  final ByteMemory.Ref refUsedSlotsToKeys;                                                                              // Bitset showing which slots are being used to map to keys
  final ByteMemory.Ref        refUsedKeys;                                                                              // Bitset showing which keys are in use
  final ByteMemory.Ref            refKeys;                                                                              // Keys used in btree held unordered in this array but ordered by the slot references to them
  final Build                       build;                                                                              // Build details
  final static String           formatKey = "%3d";                                                                      // Format a key for dumping during testing

//D1 Construction                                                                                                       // Construct and layout the slots

  static class Build                                                                                                    // Specification of slots
   {boolean            immediate = true;                                                                                // Immediate mode
    boolean                trace = false;                                                                               // Trace execution
    int             numberOfKeys = 2;                                                                                   // Number of references in the slots
    ByteMemory.Ref byteMemoryRef;                                                                                       // Program memory to be used
    Program               parent;                                                                                       // Parent program if any
    Build.MemoryPositions memoryPositions;                                                                              // Offsets of fields describing this leaf in memory

    Build immediate    (boolean  Immediate) {immediate     = Immediate;    return this;}
    Build numberOfKeys (int   NumberOfKeys) {numberOfKeys  = NumberOfKeys; return this;}
    Build memory       (ByteMemory.Ref Ref) {byteMemoryRef = Ref;          return this;}
    Build parent       (Program    Parent)  {parent        = Parent;       return this;}
    Build trace        (boolean     Trace)  {trace         = Trace;        return this;}

    Program.Build build()                                                                                               // Create a description of the needed containing program
     {final Program.Build   p = new Program.Build();                                                                    // Description of containing program
      final MemoryPositions s = memoryPositions = new MemoryPositions();                                                // Now we know the size of the slots
      if (byteMemoryRef == null) p.memory(s.size);
      if (parent        != null) p.parent(parent);
      p.immediate(immediate);
      p.trace(trace);
      return p;
     }

    int numberOfKeys       () {return numberOfKeys;}                                                                    // The number of references in the slots definition
    int numberOfSlotsToKeys() {return numberOfKeys() << 1;}                                                             // Number of slots from number of refs

    class MemoryPositions                                                                                               // Positions of fields in memory
     {final int N = numberOfSlotsToKeys();
      final int R = numberOfKeys();

      final BitSet.Build us = new BitSet.Build().bitSize(N);                                                            // Specification of bit set for used slots
      final BitSet.Build ur = new BitSet.Build().bitSize(R);                                                            // Specification of bit set for references

      final int posSlotsToKeys     = 0;                                                                                 // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
      final int posKeysToSlots     = posSlotsToKeys     + ib(N);                                                        // Used keys to slot referencing the key
      final int posUsedSlotsToKeys = posKeysToSlots     + ib(N);                                                        // Slots in use
      final int posUsedKeysToSlots = posUsedSlotsToKeys + ib(N);                                                        // Slots in use
      final int posusedKeys        = posUsedKeysToSlots + us.byteSize();                                                // References in use.  There are fewer references than slots to make insertions faster
      final int posKeys            = posusedKeys        + ur.byteSize();                                                // Keys used in btree held unordered in this array but ordered by the slot references to them
      final int size               = posKeys            + ib(N);                                                        // Size of slots
     }

    int size() {return memoryPositions.size;}                                                                           // Bytes needed for the slots
   }

  Slots(Build Build)                                                                                                    // Create the slots
   {super(Build.build());
    build                = Build;                                                                                       // Save build details
    numberOfKeys         = Build.numberOfKeys;                                                                          // Maximum number of keys
    size                 = Build.size();                                                                                // Size of memory used to hold a leaf
    final Build.MemoryPositions m = build.memoryPositions;
    byteMemoryRef        = Build.byteMemoryRef != null ? Build.byteMemoryRef : byteMemory.new Ref(0);                   // Either a reference to some memory has been supplied or create a reference to some locally allocated memory to contain the bitset
    refSlotsToKeys       = byteMemoryRef;                                                                               // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
    refKeysToSlots       = byteMemoryRef.step(m.posKeysToSlots);                                                        // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
    refUsedSlotsToKeys   = byteMemoryRef.step(m.posUsedSlotsToKeys);                                                    // Slots in use
    refUsedKeys          = byteMemoryRef.step(m.posusedKeys);                                                           // References in use.  There are fewer references than slots to make insertions faster
    refKeys              = byteMemoryRef.step(m.posKeys);                                                               // Keys used in btree held unordered in this array but ordered by the slot references to them
    usedSlotsToKeys      = new BitSet        (m.us.memory(refUsedSlotsToKeys).parent(parentProgram));                   // Create bitsets to reference the program and memory used by this program
    usedKeys             = new BitSet        (m.ur.memory(refUsedKeys)       .parent(parentProgram));
    new I() {void action() {deleteFile(tracing);}};                                                                     // Delete the trace file here to avoid including the memory reference calculations above
    slotsCode();                                                                                                        // Generate machine code if any assembler code has been supplied
   }

  void slotsCode() {}                                                                                                   // Override this method to provide code for testing the slots

  Slots(int NumberOfKeys) {this(new Build().numberOfKeys(NumberOfKeys));}                                               // Create the slots in local memory for testing

//D2 Internal                                                                                                           // Low level internal operations on slots

  void putSlotToKeys(Int Index, Int Key)                                                                                // Set a slot to key reference and the corresponding back reference
   {refSlotsToKeys.putInt(Index, Key);
    refKeysToSlots.putInt(Key, Index);
    usedSlotsToKeys.set(Index, new Bool(true));
   }

  void delSlotToKeys(Int Index)                                                                                         // Delete a slot
   {final Int K = refSlotsToKeys.getInt(Index);
                  refSlotsToKeys.putInt(Index, new Int(0));
    refKeysToSlots.putInt(K,                   new Int(0));
    usedSlotsToKeys.set(Index, new Bool(false));                                                                        // Remove slot from bitset
   }

  void putKey(Int Index, Int Key)                                                                                       // Set a key
   {refKeys .putInt(Index, Key);
    usedKeys.set   (Index, new Bool(true));
   }

  void delKey(Int Index)                                                                                                // Clear a key
   {refKeys .putInt(Index, new Int(0));
    usedKeys.set   (Index, new Bool(false));
   }

  Bool    getSlotToKeysInUse(Int Index)    {return usedSlotsToKeys.getBit(Index);}                                      // Check whether a slot is in use
  Int     getSlotToKeyIndex (Int Index)    {return refSlotsToKeys .getInt(Index);}                                      // Index to keys from slots
  Int     getKeyToSlotIndex (Int Index)    {return refKeysToSlots .getInt(Index);}                                      // Index to slots from keys
  Bool    getKeyInUse       (Int Index)    {return usedKeys       .getBit(Index);}                                      // Check whether a key is in use
  Int     getKeyValue       (Int Index)    {return refKeys        .getInt(Index);}                                      // Value of referenced key

  boolean getSlotToKeysInUse(int Index)    {return usedSlotsToKeys.getBitNC(Index);}                                    // Check whether a slot is in use
  int     getSlotToKeyIndex (int Index)    {return refSlotsToKeys .getInt(Index);}                                      // Index to keys from slot
  int     getKeyToSlotIndex (int Index)    {return refKeysToSlots .getInt(Index);}                                      // Index from slots to keys
  boolean getKeyInUse       (int Index)    {return usedKeys       .getBitNC(Index);}                                    // Check whether a key is in use
  int     getKeyValue       (int Index)    {return refKeys        .getInt(Index);}                                      // Value of referenced key

  Int     getSlotToKeyValue (Int Index)    {return getKeyValue(getSlotToKeyIndex(Index));}                              // Value of a key via a specified slot
  int     getSlotToKeyValue (int Index)    {return getKeyValue(getSlotToKeyIndex(Index));}                              // Value of a key via a specified slot

  Bool    empty             ()             {return usedKeys.empty();}                                                   // All bits in the corresponding bitset are unused so the Slots must be empty
  Bool    full              ()             {return usedKeys.full ();}                                                   // The number of bits in the bitset slots is either equal to or greater than the number of slots so we cannot rely on them being simultaneously full
  Int     count             ()             {return usedKeys.countOnes();}                                               // Count the nunber of keys in use

  void invalidateMemory     ()             {byteMemoryRef.invalidate(size);}                                            // Invalidate the slots in such a way that they are unlikely to work well if subsequently used
  int  numberOfKeys         ()             {return numberOfKeys;}                                                       // The number of references in the slots definition
  int  numberOfSlotsToKeys  ()             {return numberOfKeys()<<1;}                                                  // Number of slots from number of refs
  int  redistributionWidth  ()             {return (int)java.lang.Math.sqrt(numberOfKeys());}                           // Redistribute if the next slot is further than this

  Int  locateFirstUsedSlot  ()             {return usedSlotsToKeys.firstOne();}                                         // Index of first used slot
  Int  locateLastUsedSlot   ()             {return usedSlotsToKeys.lastOne();}                                          // Index of last used slot

  Int locateFirstUnusedKey  ()                                                                                          // Absolute position of the first unused key
   {final Int p = usedKeys.firstZero();
    final Int f = new Int(); new If (p.valid()) {void Then() {f.set(p);}}; return f;
   }

  Int stepLeft             (Int Start)     {return usedSlotsToKeys.prevOne(Start);}                                     // Step left to prior occupied slot assuming that such a step is possible
  Int stepRight            (Int Start)     {return usedSlotsToKeys.nextOne(Start);}                                     // Step right to the next occupied slot assuming that such a step is possible

  Int locateNearestFreeSlotToKey(Int Position, Bool FavorLow, Bool Prev)                                                // Absolute position of the nearest free slot to the indicated position if there is one. Prev will be true if the previous free slot is closest, true if the next free slot is closest, or invalid if there is no free slot
   {final Int r = new Int(0);
    Prev.invalidate();                                                                                                  // Assume no free slot will be found
    new If (getSlotToKeysInUse(Position))                                                                               // The slot is in use
     {void Then()
       {final Int  p = usedSlotsToKeys.prevZero(Position);                                                              // Prev free slot
        final Int  n = usedSlotsToKeys.nextZero(Position);                                                              // Next free slot
        final Bool d = new Bool(false);                                                                                 // Done when set

        new If (p.valid())                                                                                              // Previous is valid
         {void Then()
           {new If (n.valid())                                                                                          // Next is valid
             {void Then()
               {new If (FavorLow)
                 {void Then()
                   {new If (Position.Sub(p).le(n.Sub(Position)))                                                        // Favor next over previous if they are both the same distance apart
                     {void Then() {r.set(p); Prev.set(true) ;}                                                          // Previous is closest
                      void Else() {r.set(n); Prev.set(false);}                                                          // Next is closest
                     };
                   }
                  void Else()
                   {new If (Position.Sub(p).lt(n.Sub(Position)))                                                        // Favor next over previous if they are both the same distance apart
                     {void Then() {r.set(p); Prev.set(true) ;}                                                          // Previous is closest
                      void Else() {r.set(n); Prev.set(false);}                                                          // Next is closest
                     };
                   }
                 };
               }
              void Else()
               {r.set(p); Prev.set(true);                                                                               // Previous is closest
               }
             };
           }
          void Else()                                                                                                   // Previous is invalid
           {new If (n.valid())                                                                                          // Next is valid
             {void Then()
               {r.set(n); Prev.set(false);                                                                              // Next is closest
               }
             };
           }
         };
       }
     };
    return r;
   }

  Int locateNearestFreeSlotToKey(Int Position, Bool Prev)                                                               // Absolute position of the nearest free slot to the indicated position if there is one. Prev will be true if the previous free slot is closest, true if the next free slot is closest, or invalid if there is no free slot
   {return locateNearestFreeSlotToKey(Position, new Bool(false), Prev);
   }

  Int allocKey()                                                                                                        // Allocate a key
   {final Int I = locateFirstUnusedKey();
    new If (I.valid())                                                                                                  // Found an empty key
     {void Then()
       {usedKeys.set(I, new Bool(true));
       }
      void Else()                                                                                                       // No more keys slots available
       {new I() {void action() {stop("No more slots available in this set of slots");}};
       }
     };
    return I;
   }

  void setSlots(int...Slots)                                                                                            // Set slots for testing
   {for (int i : range(Slots.length)) putSlotToKeys(new Int(Slots[i]), new Int(i+1));
   }

  void setSlotAndKey(Int P, Int Q, Int K) {putSlotToKeys(P, Q); putKey (Q, K);}                                         // Set a key and a slot to point to the key

  void delSlotAndKey(Int P)                                                                                             // Delete an occupied slot and its corresponding key
   {new If (getSlotToKeysInUse(P))
     {void Then()
       {delKey(getSlotToKeyIndex(P)); delSlotToKeys(P);
       }
      void Else() {new I() {void action() {stop("Slot not in use:", P);}};}                                             // Slot not occupied
     };
   }

  private void moveSlot(Int T, Int S, Bool Continue)                                                                    // Move a slot from source to target
   {new If (S.valid())
     {void Then()
       {final Int q = getSlotToKeyIndex(S);
        delSlotToKeys(S);
        putSlotToKeys(T, q);
        Continue.set(true);                                                                                             // Continue moving slots
       }
     };
   }

  private void moveSlot(Int T, Int S)                                                                                   // Move a slot from the specified source position to the specified target position
   {final Int k = getSlotToKeyIndex(S);                                                                                 // Index of key being moved
    final Int K = getKeyValue(k);                                                                                       // Value of key being moved
    delSlotAndKey(S);                                                                                                   // Remove source
    setSlotAndKey(T, k, K);                                                                                             // Reinsert source at target
   }

  private void moveKey(Int T, Int S, Bool Continue)                                                                     // Move a key from the source position to the target position
   {final Int s = refKeysToSlots.getInt(S);                                                                             // The slot referencing the key
    final Int q = getKeyValue(S);                                                                                       // The value of the key
    delSlotAndKey(s);                                                                                                   // Delete the slot and its associated key
    setSlotAndKey(s, T, q);                                                                                             // Reinsert the key
    Continue.set(true);                                                                                                 // Continue moving keys
   }

  void copy(Slots Source)  {byteMemoryRef.copy(Source.byteMemoryRef, build.size());}                                    // Copy source into this

  void clear()                                                                                                          // Clear the slots
   {final Slots slots = this;
    compactSlotsLeft();                                                                                                 // Place slots in a known position
    new ForCount(count())                                                                                               // Clear compacted slots
     {void body(Int Index)
       {delSlotAndKey(Index);
       }
     };
   }

//D3 Compact, Split and Merge                                                                                           // Compact to the left or right, redistribute and merge slots

//D4 Compact                                                                                                            // Compact slots to the left or right

  void compactSlotsLeft()                                                                                               // Compact the slots to the left hand end
   {new If (empty().Flip())                                                                                             // Compact slots
     {void Then() {}                                                                                                    // Nothing to compact as empty
       {new For(numberOfKeys())                                                                                         // No need to make any more than this number of moves
         {void body(Int Index, Bool Continue)
           {final Int s = usedSlotsToKeys.firstZero();                                                                  // First empty slot
            final Int S = usedSlotsToKeys.nextOne(s);                                                                   // Next used slot beyond first empty slot
            moveSlot(s, S, Continue);
           }
         };
       }
     };
   }

  void compactSlotsRight()                                                                                              // Compact the slots to the right hand end
   {final Slots slots = this;
    new If (empty())                                                                                                    // Compact slots
     {void Then() {}                                                                                                    // Nothing to compact as empty
      void Else()
       {new For(numberOfKeys())                                                                                         // No need to make any more than this number of moves
         {void body(Int Index, Bool Continue)
           {final Int s = usedSlotsToKeys.lastZero();                                                                   // Last empty slot
            final Int S = usedSlotsToKeys.prevOne(s);                                                                   // Previously used slot beyond last empty one
            moveSlot(s, S, Continue);
           }
         };
       }
     };
   }

  interface CompactKey {void update(Slots Slots, Int target, Int Source);}                                              // Observe the compaction of a key so that external data can be compacted in the same way

  void compactKeysLeft() {compactKeysLeft((S, t, s)->{});}                                                              // Compact the keys to the left using as few moves as possible
  void compactKeysLeft(CompactKey CompactKey)                                                                           // Compact the keys to the left using as few moves as possible while allowing the caller to observe the moves made
   {final Slots slots = this;
    new If (empty().Flip())                                                                                             // Keys cannot be compacted if the slots are full or empty
     {void Then()
       {new If (full().Flip())                                                                                          // Keys cannot be compacted if the slots are full or empty
         {void Then()
           {new For(numberOfKeys())                                                                                     // No need to make any more than this number of moves
             {void body(Int Index, Bool Continue)
               {final Int k = usedKeys .firstZero();                                                                    // First empty key
                final Int K = usedKeys .lastOne();                                                                      // Last used key so we get the longest possible move
                new If (K.gt(k))                                                                                        // Compaction possible
                 {void Then()
                   {moveKey(k, K, Continue);
                    if (CompactKey != null) CompactKey.update(slots, k, K);                                             // Expose the compaction move
                   }
                 };
               }
             };
           }
         };
       }
     };
   }

  void compactKeysRight() {compactKeysRight((S, t, s)->{});}                                                            // Compact the keys to the right using as few moves as possible
  void compactKeysRight(CompactKey CompactKey)                                                                          // Compact the keys to the right using as few moves as possible while allowing the caller to observe the moves made
    {final Slots slots = this;
     new If (empty().Flip())                                                                                            // Keys cannot be compacted if the slots are full or empty
      {void Then()
        {new If (full().Flip())                                                                                         // Keys cannot be compacted if the slots are full or empty
         {void Then()
           {new For(numberOfKeys())                                                                                     // No need to make any more than this number of moves
             {void body(Int Index, Bool Continue)
               {final Int k = usedKeys .lastZero();                                                                     // Last empty key
                final Int K = usedKeys .firstOne();                                                                     // First used key so we get the longest possible move
                new If (K.lt(k))                                                                                        // Compaction possible
                 {void Then()
                   {moveKey(k, K, Continue);
                    if (CompactKey != null) CompactKey.update(slots, k, K);                                                    // Expose the compaction move
                   }
                 };
               }
             };
           }
         };
       }
     };
   }

  void redistribute()                                                                                                   // Improve insert performance by making the slots sparse while leaving the keys in their current positions
   {final Slots slots = this;
    new If (empty().Flip())                                                                                             // Something to redistribute
     {void Then()                                                                                                       // Redistribute
       {final Int         N = new Int(numberOfSlotsToKeys());                                                           // Maximum number of slots
        final Int         R = new Int(numberOfKeys());                                                                  // Maximum number of keys
        compactSlotsLeft();                                                                                             // Compact slots to the left so it is in a known position
        final Int         c = usedSlotsToKeys.firstZero();                                                              // Number of slots in use
        final Int     space = N.Sub(c).div(c);                                                                          // Space between used slots
        final Int     cover = space.Inc().mul(c.Dec()).inc();                                                           // Covered space from first used slot to last used slot,
        final Int remainder = N.Sub(cover);                                                                             // Uncovered remainder
        final Int         p = remainder.Down();                                                                         // Start position for first used slot giving any over to end to bias slightly in favor of presorted data
        new ForCount(c)                                                                                                 // Redistribute used slots
         {void body(Int Index)                                                                                          // Initialize background of slots
           {final Int s = c.Dec().sub(Index);                                                                           // Index of source element to be moved
            final Int t = p.Add(s.Mul(space)).add(s);                                                                   // Index in slots of target element to be set
            final Int k = getSlotToKeyIndex(s);                                                                         // Index of key being moved
             delSlotToKeys(s);                                                                                          // Delete the slot to key refence while retaining the key
             putSlotToKeys(t, k);                                                                                       // New position for slot to key
           }
         };
       }
     };
   }

//D4 Split                                                                                                              // Split full slots into left and right hand pieces

//D5 Even                                                                                                               // Splitting an even number of slots

  void splitRightEven(Slots Right)                                                                                      // Split a full set of slots that contains an even number of entries optionally redistributing the slots
   {final int N = numberOfKeys();
    if (N % 2 == 1) stop("Slot set must have an even number of entries");
    if (immediate() && full().flip().b()) stop("Slots are not full so cannot be split");

    final Slots left = this;
    left.compactSlotsLeft();                                                                                            // Compacting the source on the left will not affect the order of the keys
    Right.copy(left);                                                                                                   // Duplicate left into right

    new ForCount(new Int(N/2))                                                                                          // Clear lower half of target right slots
     {void body(Int Index)
       {Right.delSlotAndKey(Index);
       }
     };

    new ForCount(new Int(N/2), new Int(N))                                                                              // Clear upper half of left slots
     {void body(Int Index)
       {left.delSlotAndKey(Index);
       }
     };

    left .redistribute();                                                                                               // Redistribute source and target slots if requested
    Right.redistribute();
   }

  void splitLeftEven(Slots Left)                                                                                        // Split a full set of slots that contains an even number of entries optionally redistributing the slots
   {final int N = numberOfKeys();
    if (N % 2 == 1) stop("Slot set must have an even number of entries");
    if (immediate() && full().flip().b()) stop("Slots are not full so cannot be split");

    final Slots right = this;
    right.compactSlotsLeft();                                                                                          // Compacting the source on the right will not affect the order of the keys
    Left.copy(right);                                                                                                   // Duplicate right into left

    new ForCount(new Int(N/2))                                                                                          // Clear lower half of target left slots
     {void body(Int Index)
       {right.delSlotAndKey(Index);
       }
     };

    new ForCount(new Int(N/2), new Int(N))                                                                              // Clear upper half of left slots
     {void body(Int Index)
       {Left.delSlotAndKey(Index);                                                                                      // Delete key
       }
     };
    Left .redistribute();                                                                                               // Redistribute source and target slots if requested
    right.redistribute();
   }

//D5 Odd                                                                                                                // Splitting an odd number of slots

  void splitRightOdd(Slots Right)                                                                                       // Split a full set of slots that contains an odd number of entries optionally redistributing the slots in the source and target slots
   {final int N = numberOfKeys();
    final Int M = new Int(N/2);                                                                                         // Mid point
    final Int R = new Int(N/2+1);                                                                                       // Start of right range
    if (N % 2 == 0) stop("Slot set must have an odd number of entries");
    if (immediate() && full().flip().b()) stop("Slots are not full so cannot be split");

    final Slots left = this;
    left.compactSlotsLeft();                                                                                            // Compacting the source on the left will not affect the order of the keys
    Right.copy(left);                                                                                                   // Duplicate left into right

    new ForCount(R)                                                                                                     // Clear lower half of target right slots
     {void body(Int Index)
       {Right.delSlotAndKey(Index);
       }
     };

    new ForCount(M, new Int(N))                                                                                         // Clear upper half of left slots
     {void body(Int Index)
       {left.delSlotAndKey(Index);
       }
     };

    left .redistribute();                                                                                               // Redistribute source and target slots if requested
    Right.redistribute();
   }

  void splitLeftOdd(Slots Left)                                                                                         // Split a full set of slots that contains an odd number of entries optionally redistributing the slots in the source and target slots
   {final int N = numberOfKeys();
    final Int M = new Int(N/2);                                                                                         // Mid point
    final Int R = new Int(N/2+1);                                                                                       // Start of right range
    if (N % 2 == 0) stop("Slot set must have an odd number of entries");
    if (immediate() && full().flip().b()) stop("Slots are not full so cannot be split");

    final Slots right = this;
    right.compactSlotsLeft();                                                                                           // Compacting the source on the left will not affect the order of the keys
    Left.copy(right);                                                                                                   // Duplicate left into right

    new ForCount(R)                                                                                                     // Clear lower half of target right slots
     {void body(Int Index)
       {right.delSlotAndKey(Index);
       }
     };

    new ForCount(M, new Int(N))                                                                                         // Clear upper half of left slots
     {void body(Int Index)
       {Left.delSlotAndKey(Index);
       }
     };

    Left .redistribute();                                                                                               // Redistribute source and target slots if requested
    right.redistribute();
   }

//D4 Merge                                                                                                              // Merge slots

//D5 Even                                                                                                               // Merge slots with an even maximum number of keys

  Bool mergeFromRightEven(Slots Right) {return mergeFromRightEven(Right, (S, t, s)->{});}                               // Merge the specified slots from the right without observing the results
  Bool mergeFromRightEven(Slots Right, CompactKey CompactKey)                                                           // Merge the specified slots from the right
   {final Slots left = this;
    final Int      N = new Int(numberOfSlotsToKeys());
    final Int     lc = left .usedKeys.countOnes();                                                                      // Count on left
    final Int     rc = Right.usedKeys.countOnes();                                                                      // Count on right
    final Bool     r = new Bool(false);                                                                                 // Assume a merge is not possible

    new If (lc.Add(rc).le(new Int(numberOfKeys())))                                                                     // Can only merge if the result can fit in one set of slots
     {void Then()
       {r.set(true);                                                                                                    // Able to merge
        if (CompactKey != null)                                                                                         // Skip compaction if already done by the caller
         {left .compactSlotsLeft ();
          Right.compactSlotsRight();
          left .compactKeysLeft  (CompactKey);
          Right.compactKeysRight (CompactKey);
         }
        new ForCount (N.Sub(rc), N)                                                                                     // Merge right into left
         {void body(Int Index)
           {final Int k = Right.getSlotToKeyIndex(Index);                                                               // Index of key from right
            final Int K = Right.getKeyValue(k);                                                                         // Value of key from right
            left.setSlotAndKey(Index, k, K);                                                                            // Reinsert right key into left in same position
           }
         };
        left .redistribute();                                                                                           // Redistribute left
        Right.redistribute();                                                                                           // Redistribute right
       }
     };
    return r;
   }

  Bool mergeFromLeftEven(Slots Left) {return mergeFromLeftEven(Left, (S, t, s)->{});}                                   // Merge the specified slots from the right
  Bool mergeFromLeftEven(Slots Left, CompactKey CompactKey)                                                             // Merge the specified slots from the right
   {final Slots right = this;
    final Int       N = new Int(numberOfSlotsToKeys());
    final Int      rc = right.usedKeys.countOnes();
    final Int      lc = Left .usedKeys.countOnes();
    final Bool      r = new Bool(false);

    new If (lc.Add(rc).le(new Int(numberOfKeys())))                                                                     // Can only merge if the result can fit in one set of slots
     {void Then()
       {r.set(true);
        if (CompactKey != null)                                                                                         // Skip compaction if already done by the caller
         {Left .compactSlotsLeft ();
          right.compactSlotsRight();
          Left .compactKeysLeft  (CompactKey);
          right.compactKeysRight (CompactKey);
         }
        new ForCount (lc)
         {void body(Int Index)
           {final Int k = Left.getSlotToKeyIndex(Index);                                                                // Index of key from left
            final Int K = Left.getKeyValue(k);                                                                          // Value of key from left
            right.setSlotAndKey(Index, k, K);                                                                           // Reinsert left key in right target
           }
         };
        Left .redistribute();                                                                                           // Redistribute left
        right.redistribute();                                                                                           // Redistribute right
       }
     };
    return r;
   }

//D5 Odd                                                                                                                // Merge slots with an odd maximum number of keys

  Bool mergeFromRightOdd(Slots Right) {return mergeFromRightOdd(Right, (S, t, s)->{});}                                 // Merge the specified slots from the right without observing the results
  Bool mergeFromRightOdd(Slots Right, CompactKey CompactKey)                                                            // Merge the specified slots from the right
   {final Slots left = this;
    final Int      N = new Int(numberOfSlotsToKeys());
    final Int     lc = left .usedKeys.countOnes();                                                                      // Count on left
    final Int     rc = Right.usedKeys.countOnes();                                                                      // Count on right
    final Bool     r = new Bool(false);                                                                                 // Assume a merge is not possible

    new If (lc.Add(rc).le(new Int(numberOfKeys())))                                                                     // Can only merge if the result can fit in one set of slots
     {void Then()
       {r.set(true);                                                                                                    // Able to merge
        if (CompactKey != null)                                                                                         // Skip compaction if already done by the caller
         {left .compactSlotsLeft ();
          Right.compactSlotsRight();
          left .compactKeysLeft  (CompactKey);
          Right.compactKeysRight (CompactKey);
         }
        new ForCount (N.Sub(rc), N)                                                                                     // Merge right into left
         {void body(Int Index)
           {final Int k = Right.getSlotToKeyIndex(Index);                                                               // Index of key from right
            final Int K = Right.getKeyValue(k);                                                                         // Value of key from right
            left.setSlotAndKey(Index, k, K);                                                                            // Reinsert right key into left in same position
           }
         };

        final Int lh = left .getSlotToKeyValue(lc.Dec());                                                               // Highest key in left
        final Int Rl = Right.getSlotToKeyValue(new Int(Right.numberOfSlotsToKeys()).sub(rc));                           // Lowest key on right
        final Int sk = lh.Add(Rl).div(2);                                                                               // Splitting key

        setSlotAndKey(lc, lc, sk);                                                                                      // Insert separating key
        left .redistribute();                                                                                           // Redistribute left
        Right.redistribute();                                                                                           // Redistribute right
       }
     };
    return r;
   }

  Bool mergeFromLeftOdd(Slots Left) {return mergeFromLeftOdd(Left, (S, t, s)->{});}                                     // Merge the specified slots from the right
  Bool mergeFromLeftOdd(Slots Left, CompactKey CompactKey)                                                              // Merge the specified slots from the right
   {final Slots right = this;
    final Int       N = new Int(numberOfSlotsToKeys());
    final Int      rc = right.usedKeys.countOnes();
    final Int      lc = Left .usedKeys.countOnes();
    final Bool      r = new Bool(false);

    new If (lc.Add(rc).lt(new Int(numberOfKeys())))                                                                     // Can only merge if the result can fit in one set of slots with space for the additional key
     {void Then()
       {r.set(true);
        if (CompactKey != null)                                                                                         // Skip compaction if already done by the caller
         {Left .compactSlotsLeft ();
          right.compactSlotsRight();
          Left .compactKeysLeft  (CompactKey);
          right.compactKeysRight (CompactKey);
         }
        new ForCount (lc)
         {void body(Int Index)
           {final Int k = Left.getSlotToKeyIndex(Index);                                                                // Index of key from left
            final Int K = Left.getKeyValue(k);                                                                          // Value of key from left
            right.setSlotAndKey(Index, k, K);                                                                           // Reinsert left key in right target
           }
         };
        final Int Lh = Left .getSlotToKeyValue(lc.Dec());                                                               // Highest key in left
        final Int rl = right.getSlotToKeyValue(new Int(right.numberOfSlotsToKeys()).sub(rc));                           // Lowest key on right
        final Int sk = Lh.Add(rl).div(2);                                                                               // Splitting key
        setSlotAndKey(lc, lc, sk);                                                                                      // Insert separating key
        Left .redistribute();                                                                                           // Redistribute left
        right.redistribute();                                                                                           // Redistribute right
       }
     };
    return r;
   }

//D4 Shift                                                                                                              // Shift the slots to the left/down one position or up/right one position

  void shiftUpOne(Int Position, Int Width)                                                                              // Shift up the specified slots by one position to create a free space at the specified position
   {new ForCount(Width)                                                                                                 // Move the indicated slots up one position
     {void body(Int Index)
       {final Int t = Position.Add(Width).sub(Index);                                                                   // Index of source element to be moved
        final Int s = t.Dec();                                                                                          // Index in slots of target element to be set
        moveSlot(t, s);
       }
     };
   }

  void shiftDownOne(Int Position, Int Width)                                                                            // Shift down the specified slots by one position to create a free space at the specified position
   {final Slots slots = this;
    new ForCount(Width)                                                                                                 // Move the indicated slots up one position
     {void body(Int Index)
       {final Int t = Position.Sub(Width).add(Index);                                                                   // Index of source element to be moved
        final Int s = t.Inc();                                                                                          // Index in slots of target element to be set
        moveSlot(t, s);
       }
     };
   }

//D2 High level operations                                                                                              // Find, insert, delete values in the slots

  class Find                                                                                                            // Find result
   {final Int   slot = new Int() ;                                                                                      // Slot found
    final Bool lower = new Bool(), higher = new Bool(), equal = new Bool(), empty = new Bool();                         // Position of search item relative to the slot found

    void set(Int Slot, Bool Lower, Bool Higher)
     {slot.set(Slot); lower.set(Lower); higher.set(Higher);
      equal.set(lower.dup().and(higher));
      empty.set(lower.dup().or (higher).flip());
     }

    public String toString()
     {return "Find(" + "slot=" + slot + ", lower=" + lower + ", higher=" + higher +
             ", equal=" + equal + ", empty=" + empty + ')';
     }
   }

  Find find(Int Key)                                                                                                    // Find a key in the slots
   {final BitSet u = usedSlotsToKeys;
    final Find   f = new Find();
    f.slot.invalidate();                                                                                                // Show that nothing has been found yet

    new If (empty())                                                                                                    // Nothing to find if all the slots are empty
     {void Then()
       {f.set(new Int(0), new Bool(false), new Bool(false));                                                            // Empty
       }
      void Else()                                                                                                       // Not empty
       {Int p = u.topOne();                                                                                             // Position in ones tree
        Int l = u.lowOne (p);                                                                                           // Index of lower bound in slots
        Int r = u.highOne(p);                                                                                           // Index of upper bound in slots
        Int L = getSlotToKeyValue(l);                                                                                   // Value of Key at lower bound of search
        Int R = getSlotToKeyValue(r);                                                                                   // Value of key at upper bound of search

        new If (Key.eq(L))
         {void Then()                                                                                                   // Equal to low bound of search
           {f.set(l, new Bool(true), new Bool(true));
           }
          void Else()
           {new If (Key.eq(R))                                                                                          // Equal to high bound of search
             {void Then()
               {f.set(r, new Bool(true), new Bool(true));
               }
              void Else()
               {new For(new Int(u.logBitSize-1))                                                                        // Step down through ones tree narrowing the search range as we go
                 {void body(Int Index, Bool Continue)
                   {new If (Key.lt(L))
                     {void Then()
                       {f.set(l, new Bool(true), new Bool(false));                                                      // Lower than the lower bound
                       }
                      void Else()
                       {new If (Key.gt(R))
                         {void Then()
                           {f.set(r, new Bool(false), new Bool(true));                                                  // Higher than the upper bound
                           }
                          void Else()                                                                                   // Search range
                           {new If (u.canGoLeft(p))                                                                     // Go left if possible  to search first part of range if it exists
                             {void Then()
                               {final Int lp = u.childLowOne    (p);                                                    // Upper end of left range
                                final Int lr = u.highOne        (lp);                                                   // Index of upper end of range
                                final Int lR = getSlotToKeyValue(lr);                                                   // Value of key at upper end of range

                                new If (Key.eq(lR))                                                                     // Found at upper end of range
                                 {void Then()
                                   {f.set(lr, new Bool(true), new Bool(true));                                          // Equals current upper end of range
                                   }
                                  void Else()                                                                           // Search new sub range
                                   {new If (Key.lt(lR))                                                                 // Lower than upper bound
                                     {void Then()
                                       {p.set(lp); r.set(lr); R.set(lR);                                                // Set new upper bound of search range
                                        Continue.set();                                                                 // Continue the search  with new bounds
                                       }
                                      void Else()
                                       {new If (u.canGoRight(p))                                                        // Greater than anything in the left subrange so perhaps part of right hand subrange
                                         {void Then()
                                           {final Int rp = u.childHighOne   (p);                                        // Low end of right range
                                            final Int rl = u.lowOne         (rp);                                       // Index of lower end of search range
                                            final Int rL = getSlotToKeyValue(rl);                                       // Key at lower end of search range
                                            new If (Key.eq(rL))                                                         // Equal to lower bound on right
                                             {void Then()
                                               {f.set(rl, new Bool(true), new Bool(true));                              // Found equal to lower end of right sub range
                                               }
                                              void Else()                                                               // Continue search
                                               {new If (Key.lt(rL))                                                     // Less than the lower bound of right sub range
                                                 {void Then()
                                                   {f.set(rl, new Bool(true), new Bool(false));                         // Not found and less than low end of right
                                                   }
                                                  void Else()                                                           // Search new range
                                                   {p.set(rp); l.set(rl); L.set(rL);                                    // Somewhere in the right hand range of which we already know the upper limits
                                                    Continue.set();                                                     // Continue the search
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
                              void Else()
                               {new If (u.canGoRight(p))                                                                // Could not go left so must have gone right
                                 {void Then()
                                   {p.set(u.childHighOne(p));                                                           // Move to right sub range which has the same bounds as the parent range
                                    Continue.set();                                                                     // Continue the search
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
                new If (f.slot.notValid())                                                                              // Not found so must be inside final range
                 {void Then()
                   {f.set(l, new Bool(false), new Bool(true));
                   }
                 };
               }
             };
           }
         };
       }
     };
    return f;
   }

  Int findGe(Int Key)                                                                                                   // Find the index of the first key in the slots that is either equal to or greater than the specified key else return invalid if there is no such key
   {final Find f = find(Key);                                                                                           // Find the key result
    final Int  r = new Int();                                                                                           // If the slots contains keys and one of them is greater than or equal to the search key, then return the index of that key, else return invalid
    new If (f.equal.or(f.lower))                                                                                        // Found the index of a key that is greater than or equal to the search key
     {void Then()
       {r.set(f.slot);                                                                                                  // Slot index of found key
       }
      void Else()
       {r.copy(usedSlotsToKeys.nextOne(f.slot));                                                                         // Found the index of a key that was less than the search key, so the next index up, if it exists must be the one we want. Have to use copy because the value might be invalid.
       }
     };
    return r;                                                                                                           // Result found if valid, if invalid greater than any key in the slots
   }


  Int insert(Int Key)                                                                                                   // Insert a key into the slots and return the slot choosen
   {if (immediate() && usedKeys.full().b()) stop("No more space to insert key:", Key);                                  // No space left
    final Int P = new Int();

    new If (empty())                                                                                                    // Slots are empty so insert immediately in the middle
     {void Then()
       {setSlotAndKey(P.set(new Int(numberOfKeys)), new Int(0), Key);                                                   // Insert immediately in the center
       }
      void Else()                                                                                                       // Insert into a free slot while maintaining the order of the slots
       {final Int  K = usedKeys.firstZero();                                                                            // Position for key in key slots
        final Find f = find(Key);                                                                                       // Find nearest existing key in slots
        final Int  s = new Int(f.slot);                                                                                 // Nearest existing key slot
        final Bool d = new Bool();                                                                                      // Nearest free slot is below - true or above - false relative to the nearest existing key
        final Int  p = locateNearestFreeSlotToKey(s, f.lower, d);                                                       // Absolute position of nearest free slot

        new If (s.Sub(p).abs().ge(redistributionWidth()))                                                               // Redistribution width
         {void Then()
           {redistribute();                                                                                             // Redistribute slots
            K.set(usedKeys.firstZero());                                                                                // Position for key in key slots
            final Find F = find(Key);                                                                                   // Locate key in redistributed slots
            f.set(F.slot, F.lower, F.higher);                                                                           // Set find details as we do not have references available
            s.set(new Int(f.slot));                                                                                     // Nearest existing key slot
            p.set(locateNearestFreeSlotToKey(s, f.lower, d));                                                           // Absolute position of nearest free slot
           }
         };

        new If (d)                                                                                                      // Free slot is lower than nearest found key slot
         {void Then()
           {new If (f.lower)                                                                                            // Insert key lower than nearest found key slot
             {void Then()
               {new If (s.Sub(p).eq(1))                                                                                 // Previous slot is free so no movement required
                 {void Then()                                                                                           // Insert key immediately below nearest found key slot in an already empty slot
                   {setSlotAndKey(P.set(s.Dec()), K, Key);
                   }
                  void Else()                                                                                           // Previous free slot has intervening occupied slots
                   {shiftDownOne (p.Inc(), s.Sub(p).Dec());                                                             // Shift block starting one slot above lower free slot and ending one slot below nearest found key slot
                    final Int k = s.Dec();                                                                              // Insert at this index
                    setSlotAndKey(P.set(k), K, Key);                                                                    // Insert key immediately below nearest found key slot in a slot freed by moving the previous block down one step
                   }
                 };
               }
              void Else()                                                                                               // Insert above nearest found key slot
               {shiftDownOne(p.Inc(), s.Sub(p));                                                                        // Shift block one slot up from nearest lower free slot and the nearest found key slot down one step
                setSlotAndKey(P.set(s), K, Key);                                                                        // Insert key in nearest found key slot
               }
             };
           }
          void Else()                                                                                                   // Nearest free slot is higher than nearest found key slot
           {new If (f.higher)                                                                                           // Insert higher than nearest found key slot
             {void Then()
               {new If (p.Sub(s).eq(1))                                                                                 // Next slot is free so no movement required
                 {void Then() {setSlotAndKey(P.set(p), K, Key);}                                                        // Insert key immediately above nearest found key slot in an already empty slot
                  void Else()                                                                                           // Next free slot has intervening occupied slots
                   {shiftUpOne   (s.Inc(), p.Sub(s).Dec());                                                             // Shift block above nearest found key slot
                    final Int k = s.Inc();                                                                              // Insert at this index
                    setSlotAndKey(P.set(k), K, Key);                                                                    // Insert key immediately above nearest found key slot in a slot freed by moving the block above up one step
                    P.set        (k);                                                                                   // Record insertion index
                   }
                 };
               }
              void Else()                                                                                               // Insert into nearest found key slot after shifting it and the following block up one step
               {shiftUpOne   (s, p.Sub(s));                                                                             // Shift nearest found key and its following neighbors up one step
                setSlotAndKey(P.set(s), K, Key);                                                                        // Insert key in nearest found key slot
               }
             };
           }
         };
       }
     };
    return P;
   }

//D2 Print                                                                                                              // Print the slots

  String printSlots()                                                                                                   // Print the occupancy of each slot
   {final StringBuilder s = new StringBuilder();
    for (int i : range(numberOfSlotsToKeys()))
     {s.append(getSlotToKeysInUse(new Int(i)).b() ? "X" : ".");
     }
    return ""+s;
   }

  public String toString()                                                                                              // Dump the slots
   {final StringBuilder s = new StringBuilder();
    final int[]N = range(numberOfSlotsToKeys());
    final int[]R = range(numberOfKeys());
    s.append(f("Slots    : refs: %2d\n", numberOfKeys));                                                                // Title
    s.append("positions: ");   for (int i : N) s.append(f(" "+formatKey, i));
    s.append("\nslotsKeys: "); for (int i : N) s.append(f(" "+formatKey, getSlotToKeyIndex(i)));
    s.append("\nkeysSlots: "); for (int i : N) s.append(f(" "+formatKey, getKeyToSlotIndex(i)));
    s.append("\nusedSlots: "); for (int i : N) s.append(                 usedSlotsToKeys.getBitNC(i) ? "   X" : "   .");
    s.append("\nusedKeys : "); for (int i : R) s.append(                 usedKeys       .getBitNC(i) ? "   X" : "   .");
    s.append("\nkeys     : "); for (int i : R) s.append(f(" "+formatKey, getKeyValue(i)));
    return ""+s+"\n";
   }

  String printInOrder()                                                                                                 // Print the values in the used slots in order
   {final StringJoiner s = new StringJoiner(", ");
    for (int i : range(numberOfSlotsToKeys()))
     {if (usedSlotsToKeys.getBit(new Int(i)).b()) s.add(""+getKeyValue(new Int(i)).i());
     }
    return ""+s;
   }

//D1 Tests                                                                                                              // Tests

//D2 Slots                                                                                                              // Test the slots

  static void test_slots(boolean Ex)
   {final Slots s = new Slots(new Build().numberOfKeys(8).immediate(Ex))
     {void slotsCode()
       {final Bool e = empty();
         ok(()->e, true);
        final Bool f = usedSlotsToKeys.full ();
        ok(()->f, false);
        putSlotToKeys(new Int(2), new Int(3));
        usedSlotsToKeys.empty().ok(false);
        usedSlotsToKeys.full ().ok(false);
        putSlotToKeys(new Int(0), new Int(1));

        locateFirstUsedSlot().ok(0);
        locateLastUsedSlot ().ok(2);

        putKey (new Int(1), new Int(11));
        putKey (new Int(3), new Int(22));

        final Slots s = this;
        final Slots t = new Slots(s.build.parent(s).memory(null)); t.copy(s);                                           // Create some more memory and copy the slots into it
        //new I() {void action() {testStop(s);}};
        ok(()->s, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    1   0   3   0   0   0   0   0   0   0   0   0   0   0   0   0
keysSlots:    0   0   0   2   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    X   .   X   .   .   .   .   .   .   .   .   .   .   .   .   .
usedKeys :    .   X   .   X   .   .   .   .
keys     :    0  11   0  22   0   0   0   0
""");
        delSlotToKeys(new Int(2));
        delKey (new Int(3));
        //new I() {void action() {testStop(s);}};
        ok(()->s, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    1   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0
keysSlots:    0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    X   .   .   .   .   .   .   .   .   .   .   .   .   .   .   .
usedKeys :    .   X   .   .   .   .   .   .
keys     :    0  11   0   0   0   0   0   0
""");
        for (int i = 0, N = numberOfKeys(); i < N; i++) putKey (new Int(i), new Int(i+1));
        usedKeys.empty().ok(false);
        usedKeys.full ().ok(true);

        delKey(new Int(3)); locateFirstUnusedKey().ok(3);
        delKey(new Int(4)); locateFirstUnusedKey().ok(3);
        delKey(new Int(2)); locateFirstUnusedKey().ok(2);

        ok(()->t, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    1   0   3   0   0   0   0   0   0   0   0   0   0   0   0   0
keysSlots:    0   0   0   2   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    X   .   X   .   .   .   .   .   .   .   .   .   .   .   .   .
usedKeys :    .   X   .   X   .   .   .   .
keys     :    0  11   0  22   0   0   0   0
""");

        execute();
       }
     };
   }

  static void test_slots()
   {test_slots(true);
    test_slots(false);
   }

  static void test_locateNearestFreeSlotToKey(boolean Ex)
   {final Slots s = new Slots(16)
     {void slotsCode()
       {setSlots(2, 4, 5, 6, 9, 10, 12);
        final Slots s = this;
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs: 16
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18  19  20  21  22  23  24  25  26  27  28  29  30  31
slotsKeys:    0   0   1   0   2   3   4   0   0   5   6   0   7   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0
keysSlots:    0   2   4   5   6   9  10  12   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   X   .   X   X   X   .   .   X   X   .   X   .   .   .   .   .   .   .   .   .   .   .   .   .   .   .   .   .   .   .
usedKeys :    .   .   .   .   .   .   .   .   .   .   .   .   .   .   .   .
keys     :    0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0
""");

        final Bool P = new Bool();
        locateNearestFreeSlotToKey(new Int( 0), P).ok( 0); final Bool  p0 = P.valid(); ok(()-> p0, false);
        locateNearestFreeSlotToKey(new Int( 1), P).ok( 0); final Bool  p1 = P.valid(); ok(()-> p1, false);
        locateNearestFreeSlotToKey(new Int( 2), P).ok( 3);                             ok(()-> P,  false);
        locateNearestFreeSlotToKey(new Int( 3), P).ok( 0); final Bool  p3 = P.valid(); ok(()-> p3, false);
        locateNearestFreeSlotToKey(new Int( 4), P).ok( 3);                             ok(()-> P,  true);
        locateNearestFreeSlotToKey(new Int( 5), P).ok( 7);                             ok(()-> P,  false);
        locateNearestFreeSlotToKey(new Int( 6), P).ok( 7);                             ok(()-> P,  false);
        locateNearestFreeSlotToKey(new Int( 7), P).ok( 0); final Bool  p7 = P.valid(); ok(()-> p7, false);
        locateNearestFreeSlotToKey(new Int( 8), P).ok( 0); final Bool  p8 = P.valid(); ok(()-> p8, false);
        locateNearestFreeSlotToKey(new Int( 9), P).ok( 8);                             ok(()-> P,  true);
        locateNearestFreeSlotToKey(new Int(10), P).ok(11);                             ok(()-> P,  false);
        locateNearestFreeSlotToKey(new Int(11), P).ok( 0); final Bool p11 = P.valid(); ok(()->p11, false);
        locateNearestFreeSlotToKey(new Int(12), P).ok(13);                             ok(()-> P,  false);
        locateNearestFreeSlotToKey(new Int(13), P).ok( 0); final Bool p13 = P.valid(); ok(()->p13, false);

        execute();
       }
     };
   }

  static void test_locateNearestFreeSlotToKey()
   {test_locateNearestFreeSlotToKey(true);
    test_locateNearestFreeSlotToKey(false);
   }

  static void test_alloc(boolean Ex)
   {final Slots s = new Slots(4)
     {void slotsCode()
       {putKey(new Int(2),  new Int(1));
        final Slots s = this;
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   0   0   0   0   0   0   0
keysSlots:    0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   .   .
usedKeys :    .   .   X   .
keys     :    0   0   1   0
""");

        final Int k0 = allocKey(); putKey(k0,  new Int(2));
        final Int k1 = allocKey(); putKey(k1,  new Int(3));
        final Int k4 = allocKey(); putKey(k4,  new Int(4));
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   0   0   0   0   0   0   0
keysSlots:    0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   .   .
usedKeys :    X   X   X   X
keys     :    2   3   1   4
""");

        execute();
       }
     };
   }

  static void test_alloc()
   {test_alloc(true);
    test_alloc(false);
   }

  static void test_set_del_slot_key(boolean Ex)
   {final Slots s = new Slots(4)
     {void slotsCode()
       {setSlotAndKey(new Int(3),  new Int(2),  new Int(1));
        setSlotAndKey(new Int(4),  new Int(3),  new Int(2));
        final Slots s = this;
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   0   0   2   3   0   0   0
keysSlots:    0   0   3   4   0   0   0   0
usedSlots:    .   .   .   X   X   .   .   .
usedKeys :    .   .   X   X
keys     :    0   0   1   2
""");

        delSlotAndKey(new Int(3));
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   0   0   0   3   0   0   0
keysSlots:    0   0   0   4   0   0   0   0
usedSlots:    .   .   .   .   X   .   .   .
usedKeys :    .   .   .   X
keys     :    0   0   0   2
""");

        execute();
       }
     };
   }

  static void test_set_del_slot_key()
   {test_set_del_slot_key(true);
    test_set_del_slot_key(false);
   }

  static void test_compact(boolean Ex)
   {final Slots s = new Slots(4)
     {void slotsCode()
       {setSlotAndKey(new Int(2),  new Int(1),  new Int(1));
        setSlotAndKey(new Int(4),  new Int(3),  new Int(2));
        final Slots s = this;
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   0   1   0   3   0   0   0
keysSlots:    0   2   0   4   0   0   0   0
usedSlots:    .   .   X   .   X   .   .   .
usedKeys :    .   X   .   X
keys     :    0   1   0   2
""");

        compactSlotsLeft();
        //new I() {void action() {testStop(s);}};
        ok(()->s, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    1   3   0   0   0   0   0   0
keysSlots:    0   0   0   1   0   0   0   0
usedSlots:    X   X   .   .   .   .   .   .
usedKeys :    .   X   .   X
keys     :    0   1   0   2
""");

        compactSlotsRight();
        //new I() {void action() {testStop(s);}};
        ok(()->s, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   0   0   0   0   0   1   3
keysSlots:    0   6   0   7   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X
usedKeys :    .   X   .   X
keys     :    0   1   0   2
""");

        final StringBuilder T = new StringBuilder();
        compactKeysLeft((S, b, a)->{T.append(a.i()+"->"+b.i()+";");});
        //new I() {void action() {testStop(T, s);}};
        ok(()->T, "3->0;");
        ok(()->s, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   0   0   0   0   0   1   0
keysSlots:    7   6   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X
usedKeys :    X   X   .   .
keys     :    2   1   0   0
""");

        final StringBuilder U = new StringBuilder();
        compactKeysRight((S, b, a)->{U.append(a.i()+"->"+b.i()+";");});
        //new I() {void action() {testStop(U, s);}};
        ok(()->U, "0->3;1->2;");
        ok(()->s, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   0   0   0   0   0   2   3
keysSlots:    0   0   6   7   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X
usedKeys :    .   .   X   X
keys     :    0   0   1   2
""");

        final StringBuilder W = new StringBuilder();
        compactKeysLeft ((S, b, a)->{W.append(a.i()+"->"+b.i()+";");});
        compactSlotsLeft();
        //new I() {void action() {testStop(W, s);}};
        ok(()->W, "3->0;2->1;");
        ok(()->s, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    1   0   0   0   0   0   0   0
keysSlots:    1   0   0   0   0   0   0   0
usedSlots:    X   X   .   .   .   .   .   .
usedKeys :    X   X   .   .
keys     :    2   1   0   0
""");

        final StringBuilder X = new StringBuilder();
        compactKeysRight ((S, b, a)->{X.append(a.i()+"->"+b.i()+";");});
        compactSlotsRight();
        //new I() {void action() {testStop(X, s);}};
        ok(()->X, "0->3;1->2;");
        ok(()->s, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   0   0   0   0   0   2   3
keysSlots:    0   0   6   7   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X
usedKeys :    .   .   X   X
keys     :    0   0   1   2
""");

        execute();
       }
     };
   }

  static void test_compact()
   {test_compact(true);
    test_compact(false);
   }

  static void test_redistribute(boolean Ex)
   {final Slots s = new Slots(new Build().numberOfKeys(8).immediate(Ex))
     {void slotsCode()
       {maxSteps = 99999;
        setSlotAndKey(new Int(2),   new Int(1),  new Int(1));
        setSlotAndKey(new Int(4),   new Int(3),  new Int(2));
        setSlotAndKey(new Int(7),   new Int(2),  new Int(3));
        setSlotAndKey(new Int(8),   new Int(4),  new Int(4));
        setSlotAndKey(new Int(12),  new Int(5),  new Int(5));
        setSlotAndKey(new Int(13),  new Int(6),  new Int(6));
        setSlotAndKey(new Int(14),  new Int(0),  new Int(7));
        final Slots s = this;
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   1   0   3   0   0   2   4   0   0   0   5   6   0   0
keysSlots:   14   2   7   4   8  12  13   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   X   .   X   .   .   X   X   .   .   .   X   X   X   .
usedKeys :    X   X   X   X   X   X   X   .
keys     :    7   1   3   2   4   5   6   0
""");

        redistribute();
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   1   0   3   0   2   0   4   0   5   0   6   0   0   0   0
keysSlots:   13   1   5   3   7   9  11   0   0   0   0   0   0   0   0   0
usedSlots:    .   X   .   X   .   X   .   X   .   X   .   X   .   X   .   .
usedKeys :    X   X   X   X   X   X   X   .
keys     :    7   1   3   2   4   5   6   0
""");
        execute();
       }
     };
   }

  static void test_redistribute()
   {test_redistribute(true);
    test_redistribute(false);
   }

  static void test_shift(boolean Ex)
   {final Slots s = new Slots(new Build().numberOfKeys(8).immediate(Ex))
     {void slotsCode()
       {maxSteps = 99999;
        setSlotAndKey(new Int(2),   new Int(1),  new Int(1));
        setSlotAndKey(new Int(4),   new Int(3),  new Int(2));
        setSlotAndKey(new Int(7),   new Int(2),  new Int(3));
        setSlotAndKey(new Int(8),   new Int(4),  new Int(4));
        setSlotAndKey(new Int(12),  new Int(5),  new Int(5));
        setSlotAndKey(new Int(13),  new Int(6),  new Int(6));
        setSlotAndKey(new Int(14),  new Int(0),  new Int(7));
        final Slots s = this;
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   1   0   3   0   0   2   4   0   0   0   5   6   0   0
keysSlots:   14   2   7   4   8  12  13   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   X   .   X   .   .   X   X   .   .   .   X   X   X   .
usedKeys :    X   X   X   X   X   X   X   .
keys     :    7   1   3   2   4   5   6   0
""");


        shiftUpOne(new Int(2), new Int(1));
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   1   3   0   0   2   4   0   0   0   5   6   0   0
keysSlots:   14   3   7   4   8  12  13   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   X   X   .   .   X   X   .   .   .   X   X   X   .
usedKeys :    X   X   X   X   X   X   X   .
keys     :    7   1   3   2   4   5   6   0
""");

        shiftUpOne(new Int(3), new Int(2));
        shiftUpOne(new Int(4), new Int(2));
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   0   0   1   3   2   4   0   0   0   5   6   0   0
keysSlots:   14   5   7   6   8  12  13   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   X   X   X   X   .   .   .   X   X   X   .
usedKeys :    X   X   X   X   X   X   X   .
keys     :    7   1   3   2   4   5   6   0
""");

        shiftDownOne(new Int(14), new Int(3));
        shiftDownOne(new Int(13), new Int(3));
        shiftDownOne(new Int(12), new Int(3));
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   0   0   1   3   2   4   5   6   0   0   0   0   0
keysSlots:   11   5   7   6   8   9  10   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   X   X   X   X   X   X   X   .   .   .   .
usedKeys :    X   X   X   X   X   X   X   .
keys     :    7   1   3   2   4   5   6   0
""");

        execute();
       }
     };
   }

  static void test_shift()
   {test_shift(true);
    test_shift(false);
   }

  static void test_mergeFromRightEven(boolean Ex)
   {final int N = 4;
    final Slots s = new Slots(new Build().numberOfKeys(N).immediate(Ex))
     {void slotsCode()
       {final Slots l = this;
        insert(new Int(2));
        insert(new Int(1));
        final Slots r = new Slots(new Build().numberOfKeys(N).immediate(Ex).parent(l))
         {void slotsCode()
           {insert(new Int(3));
            insert(new Int(4));
           }
         };
        mergeFromRightEven(r).ok(true);
        ok(()->l, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    1   0   0   0   3   0   2   0
keysSlots:    2   0   6   4   0   0   0   0
usedSlots:    X   .   X   .   X   .   X   .
usedKeys :    X   X   X   X
keys     :    2   1   4   3
""");
        ok(()->r, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   3   0   0   0   2   0   0
keysSlots:    0   0   5   1   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .
usedKeys :    .   .   X   X
keys     :    0   0   4   3
""");
        maxSteps = 99999;
        execute();
       }
     };
   }

  static void test_mergeFromRightEven()
   {test_mergeFromRightEven(true);
    test_mergeFromRightEven(false);
   }

  static void test_mergeFromLeftEven(boolean Ex)
   {final int N = 4;
    final Slots s = new Slots(new Build().numberOfKeys(N).immediate(Ex))
     {void slotsCode()
       {final Slots r = this;
        r.insert(new Int(3));
        r.insert(new Int(4));
        final Slots l = new Slots(new Build().numberOfKeys(N).immediate(Ex).parent(r))
         {void slotsCode()
           {insert(new Int(2));
            insert(new Int(1));
           }
         };
        mergeFromLeftEven(l).ok(true);
        //new I() {void action() {testStop(l);}};
        ok(()->l, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   1   0   0   0   0   0   0
keysSlots:    5   1   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .
usedKeys :    X   X   .   .
keys     :    2   1   0   0
""");
        //new I() {void action() {testStop(r);}};
        ok(()->r, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    1   0   0   0   3   0   2   0
keysSlots:    2   0   6   4   0   0   0   0
usedSlots:    X   .   X   .   X   .   X   .
usedKeys :    X   X   X   X
keys     :    2   1   4   3
""");
        maxSteps = 99999;
        execute();
       }
     };
   }

  static void test_mergeFromLeftEven()
   {test_mergeFromLeftEven(true);
    test_mergeFromLeftEven(false);
   }

  static void test_mergeFromRightOdd(boolean Ex)
   {final int N = 5;
    final Slots s = new Slots(new Build().numberOfKeys(N).immediate(Ex))
     {void slotsCode()
       {final Slots l = this;
        l.insert(new Int(2));
        l.insert(new Int(1));
        final Slots r = new Slots(new Build().numberOfKeys(N).immediate(Ex).parent(l))
         {void slotsCode()
           {insert(new Int(4));
            insert(new Int(5));
           }
         };
        //new I() {void action() {testStop(l); }};
        ok(()->l, """
Slots    : refs:  5
positions:    0   1   2   3   4   5   6   7   8   9
slotsKeys:    0   0   0   0   1   0   0   0   0   0
keysSlots:    5   4   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   X   X   .   .   .   .
usedKeys :    X   X   .   .   .
keys     :    2   1   0   0   0
""");
        //new I() {void action() {testStop(r); }};
        ok(()->r, """
Slots    : refs:  5
positions:    0   1   2   3   4   5   6   7   8   9
slotsKeys:    0   0   0   0   0   0   1   0   0   0
keysSlots:    5   6   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   X   X   .   .   .
usedKeys :    X   X   .   .   .
keys     :    4   5   0   0   0
""");

        l.mergeFromRightOdd(r).ok(true);

        //new I() {void action() {testStop(l); }};
        //new I() {void action() {testStop(r); }};
        ok(()->l, """
Slots    : refs:  5
positions:    0   1   2   3   4   5   6   7   8   9
slotsKeys:    1   0   0   0   2   0   4   0   3   0
keysSlots:    2   0   4   8   6   0   0   0   0   0
usedSlots:    X   .   X   .   X   .   X   .   X   .
usedKeys :    X   X   X   X   X
keys     :    2   1   3   5   4
""");
        ok(()->r, """
Slots    : refs:  5
positions:    0   1   2   3   4   5   6   7   8   9
slotsKeys:    0   0   4   0   0   0   0   3   0   0
keysSlots:    0   0   0   7   2   0   0   0   0   0
usedSlots:    .   .   X   .   .   .   .   X   .   .
usedKeys :    .   .   .   X   X
keys     :    0   0   0   5   4
""");
        mergeFromRightOdd(l).ok(false);
        maxSteps = 99999;
        execute();
       }
     };
   }

  static void test_mergeFromRightOdd()
   {test_mergeFromRightOdd(true);
    test_mergeFromRightOdd(false);
   }

  static void test_mergeFromLeftOdd(boolean Ex)
   {final int N = 5;
    final Slots s = new Slots(new Build().numberOfKeys(N).immediate(Ex))
     {void slotsCode()
       {final Slots r = this;
        r.insert(new Int(4));
        r.insert(new Int(5));
        final Slots l = new Slots(new Build().numberOfKeys(N).immediate(Ex).parent(r))
         {void slotsCode()
           {insert(new Int(1));
            insert(new Int(2));
           }
         };
        //new I() {void action() {testStop(r);}};
        ok(()->r, """
Slots    : refs:  5
positions:    0   1   2   3   4   5   6   7   8   9
slotsKeys:    0   0   0   0   0   0   1   0   0   0
keysSlots:    5   6   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   X   X   .   .   .
usedKeys :    X   X   .   .   .
keys     :    4   5   0   0   0
""");
        //new I() {void action() {testStop(l);}};
        ok(()->l, """
Slots    : refs:  5
positions:    0   1   2   3   4   5   6   7   8   9
slotsKeys:    0   0   0   0   0   0   1   0   0   0
keysSlots:    5   6   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   X   X   .   .   .
usedKeys :    X   X   .   .   .
keys     :    1   2   0   0   0
""");

        r.mergeFromLeftOdd(l).ok(true);

        //new I() {void action() {testStop(r);}};
        ok(()->r, """
Slots    : refs:  5
positions:    0   1   2   3   4   5   6   7   8   9
slotsKeys:    0   0   1   0   2   0   4   0   3   0
keysSlots:    0   2   4   8   6   0   0   0   0   0
usedSlots:    X   .   X   .   X   .   X   .   X   .
usedKeys :    X   X   X   X   X
keys     :    1   2   3   5   4
""");
        maxSteps = 99999;
        execute();
       }
     };
   }

  static void test_mergeFromLeftOdd()
   {test_mergeFromLeftOdd(true);
    test_mergeFromLeftOdd(false);
   }

  static void test_find()
   {final Slots s = new Slots(new Build().numberOfKeys(8))
     {void slotsCode()
       {putSlotToKeys(new Int( 0), new Int(1));
        putSlotToKeys(new Int( 2), new Int(3));
        putSlotToKeys(new Int( 4), new Int(5));
        putSlotToKeys(new Int(15), new Int(0));
                                                 count().ok(0);
        putKey       (new Int( 1), new Int(11)); count().ok(1);
        putKey       (new Int( 3), new Int(22)); count().ok(2);
        putKey       (new Int( 5), new Int(33)); count().ok(3);
        putKey       (new Int( 0), new Int(44)); count().ok(4);

        final Slots s = this;
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    1   0   3   0   5   0   0   0   0   0   0   0   0   0   0   0
keysSlots:   15   0   0   2   0   4   0   0   0   0   0   0   0   0   0   0
usedSlots:    X   .   X   .   X   .   .   .   .   .   .   .   .   .   .   X
usedKeys :    X   X   .   X   .   X   .   .
keys     :   44  11   0  22   0  33   0   0
""");

       getSlotToKeyValue(new Int(0)).ok(11);
       getSlotToKeyValue(new Int(2)).ok(22);
       getSlotToKeyValue(new Int(4)).ok(33);
       getSlotToKeyValue(new Int(8)).ok(44);

       //new I() {void action() {testStop(s.usedSlotsToKeys);}};
       ok(s.usedSlotsToKeys, """
BitSet            0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16 |  1  0  1  0  1  0  0  0  0  0  0  0  0  0  0  1
One:
   2   16    8 |  1  1  1  0  0  0  0  1
   3   24    4 |  1  1  0  1
   4   28    2 |  1  1
   5   30    1 |  1
Zero:
   1   31    8 |  1  1  1  1  1  1  1  1
   2   39    4 |  1  1  1  1
   3   43    2 |  1  1
   4   45    1 |  1
""");
        maxSteps = 99999;
        execute();
        ok(find(new Int( 5)), "Find(slot=0, lower=true, higher=false, equal=false, empty=false)");
        ok(find(new Int(11)), "Find(slot=0, lower=true, higher=true, equal=true, empty=false)");
        ok(find(new Int(15)), "Find(slot=2, lower=true, higher=false, equal=false, empty=false)");
        ok(find(new Int(22)), "Find(slot=2, lower=true, higher=true, equal=true, empty=false)");
        ok(find(new Int(25)), "Find(slot=4, lower=true, higher=false, equal=false, empty=false)");
        ok(find(new Int(33)), "Find(slot=4, lower=true, higher=true, equal=true, empty=false)");
        ok(find(new Int(35)), "Find(slot=15, lower=true, higher=false, equal=false, empty=false)");
        ok(find(new Int(44)), "Find(slot=15, lower=true, higher=true, equal=true, empty=false)");
        ok(find(new Int(45)), "Find(slot=15, lower=false, higher=true, equal=false, empty=false)");
       }
     };
   }

  static void test_findRight()                                                                                          // Same as find but with the slots on the right
   {final Slots s = new Slots(new Build().numberOfKeys(8))
     {void slotsCode()
       {putSlotToKeys(new Int( 9), new Int(1));
        putSlotToKeys(new Int(11), new Int(3));
        putSlotToKeys(new Int(13), new Int(5));
        putSlotToKeys(new Int(15), new Int(0));
        putKey       (new Int( 1), new Int(11));
        putKey       (new Int( 3), new Int(22));
        putKey       (new Int( 5), new Int(33));
        putKey       (new Int( 0), new Int(44));

        final Slots s = this;
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   0   0   0   0   0   0   1   0   3   0   5   0   0
keysSlots:   15   9   0  11   0  13   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   .   .   .   X   .   X   .   X   .   X
usedKeys :    X   X   .   X   .   X   .   .
keys     :   44  11   0  22   0  33   0   0
""");

       ok(getSlotToKeyValue( 9), 11);
       ok(getSlotToKeyValue(11), 22);
       ok(getSlotToKeyValue(13), 33);
       ok(getSlotToKeyValue(15), 44);

       //new I() {void action() {testStop(s.usedSlotsToKeys);}};
       ok(s.usedSlotsToKeys, """
BitSet            0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16 |  0  0  0  0  0  0  0  0  0  1  0  1  0  1  0  1
One:
   2   16    8 |  0  0  0  0  1  1  1  1
   3   24    4 |  0  0  1  1
   4   28    2 |  0  1
   5   30    1 |  1
Zero:
   1   31    8 |  1  1  1  1  1  1  1  1
   2   39    4 |  1  1  1  1
   3   43    2 |  1  1
   4   45    1 |  1
""");
        maxSteps = 99999;
        execute();
        ok(find(new Int( 5)), "Find(slot=9, lower=true, higher=false, equal=false, empty=false)");
        ok(find(new Int(11)), "Find(slot=9, lower=true, higher=true, equal=true, empty=false)");
        ok(find(new Int(15)), "Find(slot=11, lower=true, higher=false, equal=false, empty=false)");
        ok(find(new Int(22)), "Find(slot=11, lower=true, higher=true, equal=true, empty=false)");
        ok(find(new Int(25)), "Find(slot=13, lower=true, higher=false, equal=false, empty=false)");
        ok(find(new Int(33)), "Find(slot=13, lower=true, higher=true, equal=true, empty=false)");
        ok(find(new Int(35)), "Find(slot=15, lower=true, higher=false, equal=false, empty=false)");
        ok(find(new Int(44)), "Find(slot=15, lower=true, higher=true, equal=true, empty=false)");
        ok(find(new Int(45)), "Find(slot=15, lower=false, higher=true, equal=false, empty=false)");
       }
     };
   }

  static void test_insert(boolean Ex)
   {final Slots s = new Slots(new Build().numberOfKeys(8).immediate(Ex))
     {void slotsCode()
       {insert(new Int(14));
        insert(new Int(13));
        insert(new Int(16));
        insert(new Int(15));
        insert(new Int(18));
        insert(new Int(17));
        insert(new Int(12));
        insert(new Int(11));
        final Slots s = this;
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    7   6   0   1   0   0   0   3   0   2   0   5   0   4   0   0
keysSlots:    5   3   9   7  13  11   1   0   0   0   0   0   0   0   0   0
usedSlots:    X   X   .   X   .   X   .   X   .   X   .   X   .   X   .   .
usedKeys :    X   X   X   X   X   X   X   X
keys     :   14  13  16  15  18  17  12  11
""");
        maxSteps = 99999;
        execute();
       }
     };
   }

  static void test_insert()
   {test_insert(true);
    test_insert(false);
   }

  static void test_insert2(boolean Ex)
   {final Slots s = new Slots(new Build().numberOfKeys(8).immediate(Ex))
     {void slotsCode()
       {insert(new Int(11)).ok( 8);
        insert(new Int(12)).ok( 9);
        insert(new Int(13)).ok(10);
        insert(new Int(15)).ok(11);
        insert(new Int(16)).ok(12);
        insert(new Int(17)).ok(13);
        insert(new Int(18)).ok(14);
        insert(new Int(14)).ok( 6);
        final Slots s = this;
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   1   0   2   7   3   0   4   0   5   0   6   0   0
keysSlots:    1   3   5   7   9  11  13   6   0   0   0   0   0   0   0   0
usedSlots:    .   X   .   X   .   X   X   X   .   X   .   X   .   X   .   .
usedKeys :    X   X   X   X   X   X   X   X
keys     :   11  12  13  15  16  17  18  14
""");
        maxSteps = 99999;
        execute();
       }
     };
   }

  static void test_insert2()
   {test_insert2(true);
    test_insert2(false);
   }

  static void test_findGe(boolean Ex)
   {final Slots s = new Slots(new Build().numberOfKeys(8).immediate(Ex))
     {void slotsCode()
       {insert(new Int(11)).ok( 8);
        insert(new Int(22)).ok( 9);
        insert(new Int(33)).ok(10);
        insert(new Int(44)).ok(11);
        insert(new Int(55)).ok(12);
        insert(new Int(66)).ok(13);
        insert(new Int(77)).ok(14);
        insert(new Int(88)).ok(15);

        redistribute();
        //testStop(this, usedSlotsToKeys);
        ok(()->this, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   1   0   2   0   3   0   4   0   5   0   6   0   7   0
keysSlots:    0   2   4   6   8  10  12  14   0   0   0   0   0   0   0   0
usedSlots:    X   .   X   .   X   .   X   .   X   .   X   .   X   .   X   .
usedKeys :    X   X   X   X   X   X   X   X
keys     :   11  22  33  44  55  66  77  88
""");
        //testStop(usedSlotsToKeys);
        ok(()->usedSlotsToKeys, """
BitSet            0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16 |  1  0  1  0  1  0  1  0  1  0  1  0  1  0  1  0
One:
   2   16    8 |  1  1  1  1  1  1  1  1
   3   24    4 |  1  1  1  1
   4   28    2 |  1  1
   5   30    1 |  1
Zero:
   1   31    8 |  1  1  1  1  1  1  1  1
   2   39    4 |  1  1  1  1
   3   43    2 |  1  1
   4   45    1 |  1
""");

        findGe(new Int(11)).ok( 0);
        findGe(new Int(22)).ok( 2);
        findGe(new Int(33)).ok( 4);
        findGe(new Int(44)).ok( 6);
        findGe(new Int(55)).ok( 8);
        findGe(new Int(66)).ok(10);
        findGe(new Int(77)).ok(12);
        findGe(new Int(88)).ok(14);

        findGe(new Int(10)).ok(( 0));
        findGe(new Int(20)).ok(( 2));
        findGe(new Int(30)).ok(( 4));
        findGe(new Int(40)).ok(( 6));
        findGe(new Int(50)).ok(( 8));
        findGe(new Int(60)).ok((10));
        findGe(new Int(70)).ok((12));
        findGe(new Int(80)).ok((14));
        findGe(new Int(90)).notValid().ok(true);

        maxSteps = 99999;
        execute();
       }
     };
   }

  static void test_findGe()
   {test_findGe(true);
    test_findGe(false);
   }

  static void test_splitRightEven(boolean Ex)
   {final int N = 8;
    final Slots s = new Slots(new Build().numberOfKeys(N).immediate(Ex))
     {void slotsCode()
       {insert(new Int(11)).ok( 8);
        insert(new Int(12)).ok( 9);
        insert(new Int(13)).ok(10);
        insert(new Int(15)).ok(11);
        insert(new Int(16)).ok(12);
        insert(new Int(17)).ok(13);
        insert(new Int(18)).ok(14);
        insert(new Int(14)).ok( 6);
        final Slots s = this;
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   1   0   2   7   3   0   4   0   5   0   6   0   0
keysSlots:    1   3   5   7   9  11  13   6   0   0   0   0   0   0   0   0
usedSlots:    .   X   .   X   .   X   X   X   .   X   .   X   .   X   .   .
usedKeys :    X   X   X   X   X   X   X   X
keys     :   11  12  13  15  16  17  18  14
""");
        final Slots t = new Slots(new Build().numberOfKeys(N).immediate(Ex).parent(s));
        t.insert(new Int(11));
        s.splitRightEven(t);
        //new I() {void action() {testStop(s);}};
        ok(()->s, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   0   0   1   0   0   0   2   0   0   0   7   0   0
keysSlots:    1   5   9   0   0   0   0  13   0   0   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   X   .   .
usedKeys :    X   X   X   .   .   .   .   X
keys     :   11  12  13   0   0   0   0  14
""");
        //new I() {void action() {testStop(t);}};
        ok(()->t, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   3   0   0   0   4   0   0   0   5   0   0   0   6   0   0
keysSlots:    0   0   0   1   5   9  13   0   0   0   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   X   .   .
usedKeys :    .   .   .   X   X   X   X   .
keys     :    0   0   0  15  16  17  18   0
""");
        maxSteps = 99999;
        execute();
       }
     };
   }

  static void test_splitRightEven()
   {test_splitRightEven(true);
    test_splitRightEven(false);
   }

  static void test_splitLeftEven(boolean Ex)
   {final int N = 8;
    final Slots r = new Slots(new Build().numberOfKeys(N).immediate(Ex))
     {void slotsCode()
       {insert(new Int(11)).ok( 8);
        insert(new Int(12)).ok( 9);
        insert(new Int(13)).ok(10);
        insert(new Int(15)).ok(11);
        insert(new Int(16)).ok(12);
        insert(new Int(17)).ok(13);
        insert(new Int(18)).ok(14);
        insert(new Int(14)).ok( 6);
        final Slots r = this;
        //new I() {void action() {testStop(r);}};
        ok(()->r, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   1   0   2   7   3   0   4   0   5   0   6   0   0
keysSlots:    1   3   5   7   9  11  13   6   0   0   0   0   0   0   0   0
usedSlots:    .   X   .   X   .   X   X   X   .   X   .   X   .   X   .   .
usedKeys :    X   X   X   X   X   X   X   X
keys     :   11  12  13  15  16  17  18  14
""");
        final Slots l = new Slots(new Build().numberOfKeys(N).immediate(Ex).parent(r));
        l.insert(new Int(11)); l.compactSlotsRight();
        r.splitLeftEven(l);
        //new I() {void action() {testStop(l);}};
        //new I() {void action() {testStop(r);}};
        ok(()->l, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   0   0   1   0   0   0   2   0   0   0   7   0   0
keysSlots:    1   5   9   0   0   0   0  13   0   0   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   X   .   .
usedKeys :    X   X   X   .   .   .   .   X
keys     :   11  12  13   0   0   0   0  14
""");
        ok(()->r, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   3   0   0   0   4   0   0   0   5   0   0   0   6   0   0
keysSlots:    0   0   0   1   5   9  13   0   0   0   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   X   .   .
usedKeys :    .   .   .   X   X   X   X   .
keys     :    0   0   0  15  16  17  18   0
""");
        maxSteps = 99999;
        execute();
       }
     };
   }

  static void test_splitLeftEven()
   {test_splitLeftEven(true);
    test_splitLeftEven(false);
   }

  static void test_splitRightOdd(boolean Ex)
   {final int N = 7;
    final Slots s = new Slots(new Build().numberOfKeys(N).immediate(Ex))
     {void slotsCode()
       {insert(new Int(11));
        insert(new Int(12));
        insert(new Int(13));
        insert(new Int(15));
        insert(new Int(16));
        insert(new Int(17));
        insert(new Int(14));
        final Slots s = this;
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   0   1   0   2   6   3   0   4   0   5   0   0
keysSlots:    1   3   5   7   9  11   6   0   0   0   0   0   0   0
usedSlots:    .   X   .   X   .   X   X   X   .   X   .   X   .   .
usedKeys :    X   X   X   X   X   X   X
keys     :   11  12  13  15  16  17  14
""");
        final Slots t = new Slots(new Build().numberOfKeys(N).immediate(Ex).parent(s));
        t.insert(new Int(11));
        s.splitRightOdd(t);
        //new I() {void action() {testStop(s);}};
        ok(()->s, """
Slots    : refs:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   0   0   0   0   1   0   0   0   2   0   0   0
keysSlots:    2   6  10   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   X   .   .   .   X   .   .   .   X   .   .   .
usedKeys :    X   X   X   .   .   .   .
keys     :   11  12  13   0   0   0   0
""");
        //new I() {void action() {testStop(t);}};
        ok(()->t, """
Slots    : refs:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   3   0   0   0   4   0   0   0   5   0   0   0
keysSlots:    0   0   0   2   6  10   0   0   0   0   0   0   0   0
usedSlots:    .   .   X   .   .   .   X   .   .   .   X   .   .   .
usedKeys :    .   .   .   X   X   X   .
keys     :    0   0   0  15  16  17   0
""");
        maxSteps = 99999;
        execute();
       }
     };
   }

  static void test_splitRightOdd()
   {test_splitRightOdd(true);
    test_splitRightOdd(false);
   }

  static void test_splitLeftOdd(boolean Ex)
   {final int N = 7;
    final Slots s = new Slots(new Build().numberOfKeys(N).immediate(Ex))
     {void slotsCode()
       {insert(new Int(11));
        insert(new Int(12));
        insert(new Int(13));
        insert(new Int(15));
        insert(new Int(16));
        insert(new Int(17));
        insert(new Int(14));
        final Slots s = this;
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   0   1   0   2   6   3   0   4   0   5   0   0
keysSlots:    1   3   5   7   9  11   6   0   0   0   0   0   0   0
usedSlots:    .   X   .   X   .   X   X   X   .   X   .   X   .   .
usedKeys :    X   X   X   X   X   X   X
keys     :   11  12  13  15  16  17  14
""");
        final Slots t = new Slots(new Build().numberOfKeys(N).immediate(Ex).parent(s));
        t.insert(new Int(11)); t.compactSlotsRight();
        s.splitLeftOdd(t);
        //new I() {void action() {testStop(t);}};
        //new I() {void action() {testStop(s);}};
        ok(()->t, """
Slots    : refs:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   0   0   0   0   1   0   0   0   2   0   0   0
keysSlots:    2   6  10   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   X   .   .   .   X   .   .   .   X   .   .   .
usedKeys :    X   X   X   .   .   .   .
keys     :   11  12  13   0   0   0   0
""");
        ok(()->s, """
Slots    : refs:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   3   0   0   0   4   0   0   0   5   0   0   0
keysSlots:    0   0   0   2   6  10   0   0   0   0   0   0   0   0
usedSlots:    .   .   X   .   .   .   X   .   .   .   X   .   .   .
usedKeys :    .   .   .   X   X   X   .
keys     :    0   0   0  15  16  17   0
""");
        maxSteps = 99999;
        execute();
       }
     };
   }

  static void test_splitLeftOdd()
   {test_splitLeftOdd(true);
    test_splitLeftOdd(false);
   }

  static void test_clear(boolean Ex)
   {final int N = 7;
    final Slots s = new Slots(new Build().numberOfKeys(N).immediate(Ex))
     {void slotsCode()
       {insert(new Int(11));
        insert(new Int(12));
        insert(new Int(13));
        insert(new Int(15));
        insert(new Int(16));
        insert(new Int(17));
        insert(new Int(14));
        final Slots s = this;
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   0   1   0   2   6   3   0   4   0   5   0   0
keysSlots:    1   3   5   7   9  11   6   0   0   0   0   0   0   0
usedSlots:    .   X   .   X   .   X   X   X   .   X   .   X   .   .
usedKeys :    X   X   X   X   X   X   X
keys     :   11  12  13  15  16  17  14
""");
        clear();
        //new I() {void action() {testStop(s);}};
        ok(()->this, """
Slots    : refs:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   0   0   0   0   0   0   0   0   0   0   0   0
keysSlots:    0   0   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   .   .   .   .   .   .   .   .
usedKeys :    .   .   .   .   .   .   .
keys     :    0   0   0   0   0   0   0
""");
        maxSteps = 99999;
        execute();
       }
     };
   }

  static void test_clear()
   {test_clear(true);
    test_clear(false);
   }

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_slots();
    test_locateNearestFreeSlotToKey();
    test_alloc();
    test_set_del_slot_key();
    test_compact();
    test_redistribute();
    test_shift();
    test_mergeFromRightEven();
    test_mergeFromLeftEven();
    test_mergeFromRightOdd();
    test_mergeFromLeftOdd();
    test_find();
    test_findRight();
    test_insert();
    test_insert2();
    test_findGe();
    test_splitRightEven();
    test_splitLeftEven();
    test_splitRightOdd();
    test_splitLeftOdd();
    test_clear();
   }

  static void newTests()                                                                                                // Tests being worked on
   {oldTests();
   }

  public static void main(String[] args)                                                                                // Test if called as a program
   {try                                                                                                                 // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {if (github_actions) oldTests(); else newTests();                                                                  // Tests to run
      if (coverageAnalysis) coverageAnalysis(12);                                                                       // Coverage analysis
      testSummary();                                                                                                    // Summarize test results
      System.exit(testsFailed);
     }
    catch(Exception e)                                                                                                  // Get a traceback in a format clickable in Geany
     {System.err.println(e);
      System.err.println(fullTraceBack(e));
      System.exit(1);
     }
   }
 }
