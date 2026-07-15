//----------------------------------------------------------------------------------------------------------------------
// Distributed sparse slots used to hold the key of the Btree.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
//Improvements Distribute sequential occupied slots across any adjacent empty ones
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;

class Slots extends Program                                                                                             // Maps a sparse slot to a key allowing keys to be inserted in order into an array that can be binary searched
 {final int                 numberOfKeys;                                                                               // The maximum number of references maintained by these slots
  final int                         size;                                                                               // Number of bytes needed to hold slots
  final BitSet            usedSlotsToKeys;                                                                              // The slots in use.  There are more slots than references so that they can be distributed with intervening empty slots to make insertions faster
  final BitSet                   usedKeys;                                                                              // The references in use.
  UnitMemory.Ref            unitMemoryRef = null;                                                                       // Byte memory reference containing the slots
  final UnitMemory.Ref     refSlotsToKeys;                                                                              // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
  final UnitMemory.Ref     refKeysToSlots;                                                                              // The slot associated with each in use key
  final UnitMemory.Ref refUsedSlotsToKeys;                                                                              // Bitset showing which slots are being used to map to keys
  final UnitMemory.Ref        refUsedKeys;                                                                              // Bitset showing which keys are in use
  final UnitMemory.Ref            refKeys;                                                                              // The keys are held unordered in this array but ordered by the slot references to them
  final Build                       build;                                                                              // Build details

//D1 Construction                                                                                                       // Construct and layout the slots

  final static class Build                                                                                              // Specification of slots
   {boolean            immediate = true;                                                                                // Immediate mode
    boolean                trace = false;                                                                               // Trace execution
    int             numberOfKeys = 2;                                                                                   // Number of references in the slots
    UnitMemory.Ref unitMemoryRef;                                                                                       // Program memory to be used
    Program               parent;                                                                                       // Parent program if any
    Build.MemoryPositions memoryPositions;                                                                              // Offsets of fields describing this leaf in memory

    Build immediate    (boolean  Immediate) {immediate     = Immediate;    return this;}
    Build numberOfKeys (int   NumberOfKeys) {numberOfKeys  = NumberOfKeys; return this;}
    Build memory       (UnitMemory.Ref Ref) {unitMemoryRef = Ref;          return this;}
    Build parent       (Program    Parent)  {parent        = Parent;       return this;}
    Build trace        (boolean     Trace)  {trace         = Trace;        return this;}

    Program.Build build()                                                                                               // Create a description of the needed containing program
     {subStart("Slots.build()");
      final Program.Build   p = new Program.Build();                                                                    // Description of containing program
      final MemoryPositions s = memoryPositions = new MemoryPositions();                                                // Now we know the size of the slots
      if (unitMemoryRef == null) p.memory(s.size);
      if (parent        != null) p.parent(parent);
      p.immediate(immediate);
      p.trace(trace);
      subFinish();
      return p;
     }

    int numberOfKeys ()        {return numberOfKeys;}                                                                   // The number of references in the slots definition
    int numberOfSlotsToKeys () {return numberOfKeys() << 1;}                                                            // Number of slots from number of refs

    final class MemoryPositions                                                                                         // Positions of fields in memory
     {final int N = numberOfSlotsToKeys();
      final int R = numberOfKeys();
      final BitSet.Build us = new BitSet.Build().bitSize(N);                                                            // Specification of bit set for used slots
      final BitSet.Build ur = new BitSet.Build().bitSize(R).count(true);                                                // Specification of bit set for references

      final int posSlotsToKeys     = 0;                                                                                 // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
      final int posKeysToSlots     = posSlotsToKeys     + N;                                                            // Used keys to slot referencing the key
      final int posUsedSlotsToKeys = posKeysToSlots     + N;                                                            // Slots in use
      final int posUsedKeysToSlots = posUsedSlotsToKeys + N;                                                            // Slots in use
      final int posusedKeys        = posUsedKeysToSlots + us.units();                                                   // References in use.  There are fewer references than slots to make insertions faster
      final int posKeys            = posusedKeys        + ur.units();                                                   // Keys used in btree held unordered in this array but ordered by the slot references to them
      final int size               = posKeys            + N;                                                            // Count of used slots
     }

    int size() {return memoryPositions.size;}                                                                           // Bytes needed for the slots
   }

  Slots(Build Build)                                                                                                    // Create the slots
   {super(Build.build());
    subStart("Slots");
    build                = Build;                                                                                       // Save build details
    numberOfKeys         = Build.numberOfKeys;                                                                          // Maximum number of keys
    size                 = Build.size();                                                                                // Size of memory used to hold a leaf
    final Build.MemoryPositions m = build.memoryPositions;
    unitMemoryRef        = Build.unitMemoryRef != null ? Build.unitMemoryRef : unitMemory.new Ref(0);                   // Either a reference to some memory has been supplied or create a reference to some locally allocated memory to contain the bitset
    refSlotsToKeys       = unitMemoryRef;                                                                               // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
    refKeysToSlots       = unitMemoryRef.step(m.posKeysToSlots);                                                        // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
    refUsedSlotsToKeys   = unitMemoryRef.step(m.posUsedSlotsToKeys);                                                    // Slots in use
    refUsedKeys          = unitMemoryRef.step(m.posusedKeys);                                                           // References in use.  There are fewer references than slots to make insertions faster
    refKeys              = unitMemoryRef.step(m.posKeys);                                                               // Keys used in btree held unordered in this array but ordered by the slot references to them
    usedSlotsToKeys      = new BitSet(m.us.memory(refUsedSlotsToKeys).parent(parentProgram));                           // Create bitsets to reference the program and memory used by this program
    usedKeys             = new BitSet(m.ur.memory(refUsedKeys)       .parent(parentProgram));
    slotsCode();                                                                                                        // Generate machine code if any assembler code has been supplied
    subFinish();
   }

  Slots initializeMemory()                                                                                              // Initialize memory
   {unitMemoryRef.clear(build.size());
    return this;
   }

  void slotsCode() {}                                                                                                   // Override this method to provide code for testing the slots

//D2 Internal                                                                                                           // Low level internal operations on slots

  void putSlotToKeys (Int Index, Int Key)                                                                               // Set a slot to key reference and the corresponding back reference
   {subStart("Slots.putSlotToKeys");
    refSlotsToKeys.putInt(Index, Key);                                                                                  // Set forward  reference
    refKeysToSlots.putInt(Key, Index);                                                                                  // Set backward reference
    usedSlotsToKeys.set(Index, new Bool(true));                                                                         // Set bit showing this slot reference is active
    subFinish();
   }

  void delSlotToKeys (Int Index)                                                                                        // Delete a slot
   {subStart("Slots.delSlotToKeys");
    final Int K = refSlotsToKeys.getInt(Index);                                                                         // Slot to key index
                  refSlotsToKeys.putInt(Index, new Int(0));                                                             // Zero forward reference
    refKeysToSlots.putInt(K,                   new Int(0));                                                             // Zero backward reference
    usedSlotsToKeys.set  (Index,               new Bool(false));                                                        // Make this slot reference inactive
    subFinish();
   }

  void putKey(Int Index, Int Key)                                                                                       // Set a key
   {subStart("Slots.putKey");
    refKeys .putInt(Index, Key);
    usedKeys.set   (Index, new Bool(true));
    subFinish();
   }

  void delKey(Int Index)                                                                                                // Clear a key
   {subStart("Slots.delKey");
    refKeys.putInt(Index, new Int(0));                                                                                  // Clear the key by zeroing it - this is not strictly necessary - it does make tests neater
    usedKeys.set  (Index, new Bool(false));
    subFinish();
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

  Bool                empty ()             {return usedKeys.empty();}                                                   // All bits in the corresponding bitset are unused so the Slots must be empty
  Bool                 full ()             {return usedKeys.full ();}                                                   // The number of bits in the bitset slots is either equal to or greater than the number of slots so we cannot rely on them being simultaneously full
  Int                 count ()             {return usedKeys.count();}                                                   // The computed number of keys in the slots
//void  invalidateMemory ()                {unitMemoryRef.invalidate(size);}                                            // Invalidate the slots in such a way that they are unlikely to work well if subsequently used
  Int          numberOfKeys ()             {return new Int(numberOfKeys);}                                              // The number of references in the slots definition
  int   numberOfSlotsToKeys ()             {return numberOfKeys<<1;}                                                    // Number of slots from number of refs
  int   redistributionWidth ()             {return (int)java.lang.Math.sqrt(numberOfKeys);}                             // Redistribute if the next slot is further than this
  Bint  locateFirstUsedSlot ()             {return usedSlotsToKeys.firstOne();}                                         // Index of first used slot
  Bint   locateLastUsedSlot ()             {return usedSlotsToKeys.lastOne();}                                          // Index of last used slot
  Bint             stepLeft (Int Start)    {return usedSlotsToKeys.prevOne(Start);}                                     // Step left to prior occupied slot assuming that such a step is possible
  Bint            stepRight (Int Start)    {return usedSlotsToKeys.nextOne(Start);}                                     // Step right to the next occupied slot assuming that such a step is possible

  Bint locateFirstUnusedKey ()             {return usedKeys.firstZero();}                                               // Absolute position of the first unused key

  Int locateNearestFreeSlotToKey(Int Position, Bool FavorLow)                                                           // Absolute position of the nearest free slot to the indicated position.  There will always be one as there are always more slots than keys. Prev will be true if the previous free slot is closest, else false if the next free slot is closest.
   {subStart("Slots.locateNearestFreeSlotToKey");
    final Slots slots = this;                                                                                           // Slots to search
    final Int       r = new Int(0);                                                                                     // Search radius
    new If (getSlotToKeysInUse(Position))                                                                               // The slot is in use as expected
     {void Then()
       {final Bint p = usedSlotsToKeys.prevZero(Position);                                                              // Prev free slot
        final Bint n = usedSlotsToKeys.nextZero(Position);                                                              // Next free slot
        final Bool d = new Bool(false);                                                                                 // Done when set

        new If (p.valid())                                                                                              // Previous is valid
         {void Then()
           {new If (n.valid())                                                                                          // Next is valid
             {void Then()
               {new If (FavorLow)
                 {void Then()
                   {new If (Position.Sub(p.i()).le(n.i().Sub(Position)))                                                // Favor next over previous if they are both the same distance apart
                     {void Then() {r.set(p);}                                                                           // Previous is closest
                      void Else() {r.set(n);}                                                                           // Next is closest
                     };
                   }
                  void Else()
                   {new If (Position.Sub(p.i()).lt(n.i().Sub(Position)))                                                // Favor next over previous if they are both the same distance apart
                     {void Then() {r.set(p);}                                                                           // Previous is closest
                      void Else() {r.set(n);}                                                                           // Next is closest
                     };
                   }
                 };
               }
              void Else()
               {r.set(p);
               }
             };
           }
          void Else()                                                                                                   // Previous is invalid
           {new If (n.valid())                                                                                          // Next is valid
             {void Then()
               {r.set(n);
               }
             };
           }
         };
       }
      void Else()                                                                                                       // Complain if we are handed an empty slot
       {if (immediate()) stop("Slot required to be in use but is not in use:", Position, slots);
       }
     };
    subFinish();
    return r;
   }

  Int locateNearestFreeSlotToKey (Int Position) {return locateNearestFreeSlotToKey(Position, new Bool(false));}         // Locate the nearest free slot favoring a higher slot over a lower one if they are both the same  distance away

  Int allocKey ()                                                                                                       // Allocate a key
   {subStart("Slots.allocKey");
    final Bint I = locateFirstUnusedKey();
    I.elseStop("No room for key");                                                                                      // No more keys slots available
    new If (I) {void Then() {usedKeys.set(I.i());}};                                                                    // Show that the key as having been allocated
    subFinish();
    return I.i();
   }

  void setSlots (int...Slots)                                                                                           // Set slots for testing
   {for (int i : range(Slots.length)) putSlotToKeys(new Int(Slots[i]), new Int(i+1));
   }

  void setSlotAndKey (Int P, Int Q, Int K) {putSlotToKeys(P, Q); putKey (Q, K);}                                        // Set a key and a slot to point to the key

  void delSlotAndKey (Int P)                                                                                            // Delete an occupied slot and its corresponding key
   {if (immediate() && !getSlotToKeysInUse(P).b()) stop("Slot not in use:", P);                                         // Slot not occupied
    delKey(getSlotToKeyIndex(P)); delSlotToKeys(P);                                                                     // Free key assumed to exist and slots referring to it
   }

  private void moveSlot (Bint T, Bint S, Bool Continue)                                                                 // Move a slot from source to target
   {subStart("Slots.moveSlot(BBb");
    new If (S)
     {void Then()
       {final Int q = getSlotToKeyIndex(S.i());
        delSlotToKeys(S.i());
        putSlotToKeys(T.i(), q);
        Continue.set(true);                                                                                             // Continue moving slots
       }
     };
    subFinish();
   }

  private void moveSlot (Int T, Int S)                                                                                  // Move a slot from the specified source position to the specified target position
   {subStart("Slots.moveSlot(II)");
    final Int k = getSlotToKeyIndex(S);                                                                                 // Index of key being moved
    final Int K = getKeyValue(k);                                                                                       // Value of key being moved
    delSlotAndKey(S);                                                                                                   // Remove source
    setSlotAndKey(T, k, K);                                                                                             // Reinsert source at target
    subFinish();
   }

  private void moveKey (Bint T, Bint S, Bool Continue)                                                                  // Move a key from the source position to the target position
   {subStart("Slots.moveKey");
    final Int s = refKeysToSlots.getInt(S.i());                                                                         // The slot referencing the key
    final Int q = getKeyValue(S.i());                                                                                   // The value of the key
    delSlotAndKey(s);                                                                                                   // Delete the slot and its associated key
    setSlotAndKey(s, T.i(), q);                                                                                         // Reinsert the key
    Continue.set(true);                                                                                                 // Continue moving keys
    subFinish();
   }

  void copy (Slots Source) {unitMemoryRef.copy(Source.unitMemoryRef, build.size());}                                    // Copy source into this

//D3 Compact, Split and Merge                                                                                           // Compact to the left or right, redistribute and merge slots

//D4 Compact                                                                                                            // Compact slots to the left or right

  void compactSlotsLeft ()                                                                                              // Compact the slots to the left hand side
   {subStart("Slots.compactSlotsLeft");
    new If (empty().Flip())                                                                                             // Compact slots
     {void Then() {}                                                                                                    // Nothing to compact as empty
       {new For(numberOfKeys())                                                                                         // No need to make any more than this number of moves
         {void body(Int Index, Bool Continue)
           {final Bint s = usedSlotsToKeys.firstZero();                                                                 // First empty slot
            final Bint S = usedSlotsToKeys.nextOne(s.i());                                                              // Next used slot beyond first empty slot
            moveSlot(s, S, Continue);
           }
         };
       }
     };
    subFinish();
   }

  void compactSlotsRight ()                                                                                             // Compact the slots to the right hand side
   {subStart("Slots.compactSlotsRight");
    final Slots slots = this;
    new If (empty())                                                                                                    // Compact slots
     {void Then() {}                                                                                                    // Nothing to compact as empty
      void Else()
       {new For(numberOfKeys())                                                                                         // No need to make any more than this number of moves
         {void body(Int Index, Bool Continue)
           {final Bint s = usedSlotsToKeys.lastZero();                                                                  // Last empty slot
            final Bint S = usedSlotsToKeys.prevOne(s.i());                                                              // Previously used slot beyond last empty one
            moveSlot(s, S, Continue);
           }
         };
       }
     };
    subFinish();
   }

  interface CompactKey {void update(Slots Slots, Int target, Int Source);}                                              // Observe the compaction of a key so that external data can be compacted in the same way

  void compactKeysLeft() {compactKeysLeft((S, t, s)->{});}                                                              // Compact the keys to the left using as few moves as possible
  void compactKeysLeft(CompactKey CompactKey)                                                                           // Compact the keys to the left using as few moves as possible while allowing the caller to observe the moves made
   {subStart("Slots.compactKeysLeft");
    final Slots slots = this;
    new If (empty().Flip())                                                                                             // Keys cannot be compacted if the slots are full or empty
     {void Then()
       {new If (full().Flip())                                                                                          // Keys cannot be compacted if the slots are full or empty
         {void Then()
           {new For(numberOfKeys())                                                                                     // No need to make any more than this number of moves
             {void body(Int Index, Bool Continue)
               {final Bint k = usedKeys .firstZero();                                                                   // First empty key
                final Bint K = usedKeys .lastOne();                                                                     // Last used key so we get the longest possible move
                new If (K.i().gt(k.i()))                                                                                // Compaction possible
                 {void Then()
                   {moveKey(k, K, Continue);
                    if (CompactKey != null) CompactKey.update(slots, k.i(), K.i());                                     // Expose the compaction move
                   }
                 };
               }
             };
           }
         };
       }
     };
    subFinish();
   }

  void compactKeysRight() {compactKeysRight((S, t, s)->{});}                                                            // Compact the keys to the right using as few moves as possible
  void compactKeysRight(CompactKey CompactKey)                                                                          // Compact the keys to the right using as few moves as possible while allowing the caller to observe the moves made
   {subStart("Slots.compactKeysRight");
    final Slots slots = this;
     new If (empty().Flip())                                                                                            // Keys cannot be compacted if the slots are full or empty
      {void Then()
        {new If (full().Flip())                                                                                         // Keys cannot be compacted if the slots are full or empty
         {void Then()
           {new For(numberOfKeys())                                                                                     // No need to make any more than this number of moves
             {void body(Int Index, Bool Continue)
               {final Bint k = usedKeys .lastZero();                                                                    // Last empty key
                final Bint K = usedKeys .firstOne();                                                                    // First used key so we get the longest possible move
                new If (K.i().lt(k.i()))                                                                                // Compaction possible
                 {void Then()
                   {moveKey(k, K, Continue);
                    if (CompactKey != null) CompactKey.update(slots, k.i(), K.i());                                     // Expose the compaction move
                   }
                 };
               }
             };
           }
         };
       }
     };
    subFinish();
   }

  void redistribute()                                                                                                   // Improve insert performance by making the slots sparse while leaving the keys in their current positions
   {subStart("Slots.redistribute");
    final Slots slots = this;
    new If (empty().Flip())                                                                                             // Something to redistribute
     {void Then()                                                                                                       // Redistribute
       {final Int         N = new Int(numberOfSlotsToKeys());                                                           // Maximum number of slots
        final Int         R = new Int(numberOfKeys());                                                                  // Maximum number of keys
        compactSlotsLeft();                                                                                             // Compact slots to the left so it is in a known position
        final Int         c = usedSlotsToKeys.firstZero().i(); c.name = "c";                                            // Number of slots in use
        final Int     space = N.Sub(c).div(c);                                                                          // Space between used slots
        final Int     cover = space.Inc().mul(c.Dec()).inc();                                                           // Covered space from first used slot to last used slot,
        final Int remainder = N.Sub(cover);                                                                             // Uncovered remainder
        final Int         p = remainder.Down();                                                                         // Start position for first used slot giving any over to end to bias slightly in favor of presorted data
        new ForCount(c)                                                                                                 // Redistribute used slots
         {void body(Int Index)                                                                                          // Initialize background of slots
           {final Int s = c.Dec().sub(Index);                                                                           // Index of source element to be moved
            final Int t = p.Add(s.Mul(space)).add(s);                                                                   // Index in slots of target element to be set
            final Int k = getSlotToKeyIndex(s);                                                                         // Index of key being moved
            delSlotToKeys(s);                                                                                           // Delete the slot to key reference while retaining the key
            putSlotToKeys(t, k);                                                                                        // New position for slot to key
           }
         };
       }
     };
    subFinish();
   }

//D4 Split                                                                                                              // Split full slots into left and right hand pieces

//D5 Even                                                                                                               // Splitting an even number of slots

  Int splitRightEven(Slots Right)                                                                                       // Split a full set of slots that contains an even number of entries then redistribute the slots. Return the splitting key
   {subStart("Slots.splitRightEven");
    final int N = numberOfKeys;
    if (N % 2 == 1) stop("Slot set must have an even number of entries");
    if (immediate() && full().flip().b()) stop("Slots are not full so cannot be split");

    final Slots left = this;
    left.compactSlotsLeft();                                                                                            // Compacting the source on the left will not affect the order of the keys
    Right.copy(left);                                                                                                   // Duplicate left into right

    final Int sk = new Int(left.getSlotToKeyValue(new Int(numberOfKeys/2-1))).                                          // Splitting key is half the two middle keys
                       add(left.getSlotToKeyValue(new Int(numberOfKeys/2-0))).down();

    new ForCount(new Int(N/2))             {void body(Int Index) {Right.delete(Index);}};                               // Clear lower half of target right slots
    new ForCount(new Int(N/2), new Int(N)) {void body(Int Index) {left .delete(Index);}};                               // Clear upper half of left slots
    left .redistribute();                                                                                               // Redistribute source and target slots if requested
    Right.redistribute();
    subFinish();
    return sk;                                                                                                          // Return splitting key
   }

  Int splitLeftEven(Slots Left)                                                                                         // Split a full set of slots that contains an even number of entries, redistribute the slots. Return the splitting key
   {subStart("Slots.splitLeftEven");
    final int N = numberOfKeys;
    if (N % 2 == 1) stop("Slot set must have an even number of entries");
    if (immediate() && full().flip().b()) stop("Slots are not full so cannot be split");

    final Slots right = this;
    right.compactSlotsLeft();                                                                                           // Compacting the source on the right will not affect the order of the keys
    Left.copy(right);                                                                                                   // Duplicate right into left

    final Int sk = new Int(right.getSlotToKeyValue(new Int(numberOfKeys/2-1))).                                         // Splitting key is half the two middle keys
                       add(right.getSlotToKeyValue(new Int(numberOfKeys/2-0))).down();

    new ForCount(new Int(N/2))             {void body(Int Index) {right.delete(Index);}};                               // Clear lower half of target left slots
    new ForCount(new Int(N/2), new Int(N)) {void body(Int Index) {Left .delete(Index);}};                               // Clear upper half of left slots
    Left .redistribute();                                                                                               // Redistribute source and target slots if requested
    right.redistribute();
    subFinish();
    return sk;                                                                                                          // Return splitting key
   }

//D5 Odd                                                                                                                // Splitting an odd number of slots

  Int splitRightOdd(Slots Right)                                                                                        // Split a full set of slots that contains an odd number of entries redistributing the slots in the source and target slots. Return the index of the splitting key
   {subStart("Slots.splitRightOdd");
    final int N = numberOfKeys;
    final Int M = new Int(N/2);                                                                                         // Mid point
    final Int R = new Int(N/2+1);                                                                                       // Start of right range
    if (N % 2 == 0) stop("Slot set must have an odd number of entries");
    if (immediate() && full().flip().b()) stop("Slots are not full so cannot be split");

    final Slots left = this;
    left.compactSlotsLeft();                                                                                            // Compacting the source on the left will not affect the order of the keys
    Right.copy(left);                                                                                                   // Duplicate left into right

    final Int sk = new Int(left.getSlotToKeyIndex(M));                                                                  // Get the index of the splitting key. The actual key can be recovered from the index.
    final Int sK = new Int(left.getKeyValue(sk));                                                                       // Value of the splitting key

    new ForCount(R)             {void body(Int Index) {Right.delete(Index);}};                                          // Clear lower half of target right slots
    new ForCount(M, new Int(N)) {void body(Int Index) {left .delete(Index);}};                                          // Clear upper half of left slots

    left.refKeys.putInt(sk, sK);                                                                                        // Leave splitting key in position so that the returned splitting key index can still refer to it, but the slot has been marked as free so it is only valid until it is overwritten

    left .redistribute();                                                                                               // Redistribute source and target slots if requested
    Right.redistribute();
    subFinish();
    return sk;                                                                                                          // Return the index of the splitting key
   }

  Int splitLeftOdd(Slots Left)                                                                                          // Split a full set of slots that contains an odd number of entries optionally redistributing the slots in the source and target slots. Return the index of the splitting key
   {subStart("Slots.splitLeftOdd");
    final int N = numberOfKeys;
    final Int M = new Int(N/2);                                                                                         // Mid point
    final Int R = new Int(N/2+1);                                                                                       // Start of right range
    if (N % 2 == 0) stop("Slot set must have an odd number of entries");
    if (immediate() && full().flip().b()) stop("Slots are not full so cannot be split");

    final Slots right = this;
    right.compactSlotsLeft();                                                                                           // Compacting the source on the left will not affect the order of the keys
    Left.copy(right);                                                                                                   // Duplicate left into right

    final Int sk = new Int(right.getSlotToKeyIndex(M));                                                                 // Get the index of the splitting key. The actual key can be recovered from the index.
    final Int sK = new Int(right.getKeyValue(sk));                                                                      // Value of the splitting key

    new ForCount(R)              {void body(Int Index) {right.delete(Index);}};                                         // Clear lower half of target right slots
    right.refKeys.putInt(sk, sK);                                                                                       // Leave splitting key in position so that the returned splitting key index can still refer to it, but the slot has been marked as free so it is only valid until it is overwritten
    new ForCount(M, new Int(N))  {void body(Int Index) {Left .delete(Index);}};                                         // Clear upper half of left slots
    Left .redistribute();                                                                                               // Redistribute source and target slots if requested
    right.redistribute();
    subFinish();
    return sk;                                                                                                          // Return the index of the splitting key
   }

//D4 Merge                                                                                                              // Merge slots

//D5 Even                                                                                                               // Merge slots with an even maximum number of keys

  Bool mergeFromRightEven(Slots Right) {return mergeFromRightEven(Right, (S, t, s)->{});}                               //N Merge the specified slots from the right without observing the results
  Bool mergeFromRightEven(Slots Right, CompactKey CompactKey)                                                           //N Merge the specified slots from the right
   {subStart("Slots.mergeFromRightEven");
    final Slots left = this;
    final Int      N = new Int(numberOfSlotsToKeys());
    final Int     lc = left .count();                                                                                   // Count on left
    final Int     rc = Right.count();                                                                                   // Count on right
    final Bool     r = new Bool(false);                                                                                 // Assume a merge is not possible

    new If (lc.Add(rc).le(new Int(numberOfKeys())))                                                                     // Can only merge if the result can fit in one set of slots
     {void Then()
       {r.set(true);                                                                                                    // Able to merge
        if (CompactKey != null)                                                                                         // Skip compaction if already done by the caller
         {left .compactSlotsLeft( );
          Right.compactSlotsRight();
          left .compactKeysLeft(CompactKey);
          Right.compactKeysRight(CompactKey);
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
    subFinish();
    return r;
   }

  Bool mergeFromLeftEven(Slots Left) {return mergeFromLeftEven(Left, (S, t, s)->{});}                                   // Merge the specified slots from the right
  Bool mergeFromLeftEven(Slots Left, CompactKey CompactKey)                                                             // Merge the specified slots from the right
   {subStart("Slots.mergeFromLeftEven");
    final Slots right = this;
    final Int       N = new Int(numberOfSlotsToKeys());
    final Int      rc = right.count();
    final Int      lc = Left .count();
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
    subFinish();
    return r;
   }

//D5 Odd                                                                                                                // Merge slots with an odd maximum number of keys and insert the splitting key/  The process of compacting the keys can optionally be observed to align other data structures with the slots

  Bool mergeFromRightOdd(Slots Right, Int Sk) {return mergeFromRightOdd(Right, Sk, (S, t, s)->{});}                     //N Merge the specified slots from the right without observing the key compaction process
  Bool mergeFromRightOdd(Slots Right, Int Sk, CompactKey CompactKey)                                                    //N Merge the specified slots from the right observing the key compaction process
   {subStart("Slots.mergeFromRightOdd");
    final Slots left = this;
    final Int      N = new Int(numberOfSlotsToKeys());
    final Int     lc = left .count();                                                                                   // Count on left
    final Int     rc = Right.count();                                                                                   // Count on right
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

        left.setSlotAndKey(lc, lc, Sk);                                                                                 // Insert splitting key
        left .redistribute();                                                                                           // Redistribute left
        Right.redistribute();                                                                                           // Redistribute right
       }
     };
    subFinish();
    return r;
   }

  Bool mergeFromLeftOdd(Slots Left, Int Sk) {return mergeFromLeftOdd(Left, Sk, (S, t, s)->{});}                         // Merge the specified slots from the right without observing the key compaction process
  Bool mergeFromLeftOdd(Slots Left, Int Sk, CompactKey CompactKey)                                                      // Merge the specified slots from the right observing the key compaction process
   {subStart("Slots.mergeFromLeftOdd");
    final Slots right = this;
    final Int       N = new Int(numberOfSlotsToKeys());
    final Int      rc = right.count();
    final Int      lc = Left .count();
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

        setSlotAndKey(lc, lc, Sk);                                                                                      // Insert splitting key
        Left .redistribute();                                                                                           // Redistribute left
        right.redistribute();                                                                                           // Redistribute right
       }
     };
    subFinish();
    return r;
   }

//D4 Shift                                                                                                              // Shift the slots to the left/down one position or up/right one position

  void shiftUpOne(Int Position, Int Width)                                                                              // Shift up the specified slots by one position to create a free space at the specified position
   {subStart("Slots.shiftUpOne");
    new ForCount(Width)                                                                                                 // Move the indicated slots up one position
     {void body(Int Index)
       {final Int t = Position.Add(Width).sub(Index);                                                                   // Index of source element to be moved
        final Int s = t.Dec();                                                                                          // Index in slots of target element to be set
        moveSlot(t, s);
       }
     };
    subFinish();
   }

  void shiftDownOne(Int Position, Int Width)                                                                            // Shift down the specified slots by one position to create a free space at the specified position
   {final Slots slots = this;
    subStart("Slots.shiftDownOne");
    new ForCount(Width)                                                                                                 // Move the indicated slots up one position
     {void body(Int Index)
       {final Int t = Position.Sub(Width).add(Index);                                                                   // Index of source element to be moved
        final Int s = t.Inc();                                                                                          // Index in slots of target element to be set
        moveSlot(t, s);
       }
     };
    subFinish();
   }

//D4 Stuck                                                                                                              // The bitset can be made to operate like a fixed size stack - a stuck - as long as only stuck operations are used on it

//  private String slotsAsStuck(String Message)            {return "BitSet acting as a stuck "+Message;}                  // Useful component of an error message
//  private String slotsStuckAs(String Message)            {return Message+" a bitSet acting as a stuck";}                // Useful component of an error message
//  private String slotsAsStuck(String Message, Int Index) {return Message+" a bitSet acting as a stuck: "+Index;}        // Useful component of an error message
//
//  void stuckPush(Int Key)                                                                                               // Push a key onto a bitset acting as a stuck
//   {full().stop(slotsStuckAs("is full so cannot push"));
//    final Int t = usedKeys.firstZero().i();
//    getSlotToKeysInUse(t).stop(slotsAsStuck("Non stuck operation has been applied to"));
//    setSlotAndKey(t, t, Key);
//   }
//
//  Int stuckPop()                                                                                                        // Push a key onto a bitset acting as a stuck
//    {empty()              .stop(slotsAsStuck("is empty so cannot pop"));
//    final Int t = usedKeys.firstZero().i().dec();
//    final Int v = getSlotToKeyValue(t);
//    delSlotAndKey(t);
//    return v;
//   }
//
//  void stuckPut(Int Index, Int Key)                                                                                     // Overwrite an existing key or extend the stuck by one element if possible to accommodate a new key
//   {Index.lt(0)                   .stop(slotsAsStuck("Index cannot be less than zero when accessing", Index));
//    Index.ge(numberOfKeys())      .stop(slotsAsStuck("Index too large for put on",                    Index));
//    Index.gt(usedKeys.firstZero()).stop(slotsAsStuck("Index addressing beyond bounds of",             Index));
//    setSlotAndKey(Index, Index, Key);
//   }
//
//  Int stuckGet(Int Index)                                                                                               // Get the element at the indicated position in the bitset acting as a stuck
//   {Index.lt(0)                   .stop(slotsAsStuck("Index cannot be less than zero when accessing", Index));
//    Index.ge(usedKeys.firstZero()).stop(slotsAsStuck("Index addressing beyond bounds of",             Index));
//    return getSlotToKeyValue(Index);
//   }

//D2 High level operations                                                                                              // Find, insert, delete values in the slots

  final class Find                                                                                                      // Find result
   {final Bint  slot = new Bint();                                                                                      // Slot found
    final Bool lower = new Bool(), higher = new Bool(), equal = new Bool(), empty = new Bool();                         // Position of search item relative to the slot found
    boolean insertBelow = false;                                                                                        // If true, then the key to be inserted should be inserted below the indicated slot, otherwise the insertion position will be determined at run time.  Setting this flag reduces the amount of code generated because the case where the key has to be inserted above the found key can be safely ignored
    boolean insertAbove = false;                                                                                        // If true, then the key to be inserted should be inserted above the indicated slot, otherwise the insertion position will be determined at run time.  Setting this flag reduces the amount of code generated because the case where the key has to be inserted above the found key can be safely ignored

    Find set(Int Slot, Bool Lower, Bool Higher)                                                                         // Set a find result
     {slot .set(Slot); lower.set(Lower); higher.set(Higher);
      equal.set(lower.dup().and(higher));
      empty.set(lower.dup().or (higher).flip());
say("AAAA", lower,  higher, empty);
      insertBelow = false;                                                                                              // Determine whether to insert above or below at runtime
      return this;
     }

    Find set(Int Slot, boolean InsertBelow)                                                                             // Set a find result showing that a key should be inserted in the slot above (false) or below(true) below the indicated slot
     {slot .set(Slot);
      equal.set(false);
      empty.set(false);
      if (InsertBelow) {lower.set(true);  higher.set(false); insertBelow = true;  insertAbove = false;}                 // Insist on an insert below the found key
      else             {lower.set(false); higher.set(true);  insertBelow = false; insertAbove = true; }                 // Insist on an insert above the found key
      return this;
     }

    void copy(Find Source)                                                                                              // Copy a find result
     {slot  .copy(Source.slot);
      lower .set(Source.lower);
      higher.set(Source.higher);
      equal .set(Source.equal);
      empty .set(Source.empty);
     }

    Int insert(Int Key)                                                                                                 // Insert a key into the slots in light of the find result and return the slot chosen. The slots are assumed to be not empty.
     {final Int  P = new Int();                                                                                         // The slot into which the key was inserted
      final Find f = this;                                                                                              // Find nearest existing key in slots
      final Bint K = usedKeys.firstZero();                                                                              // Position for key in key slots
      final Int  s = new Int(f.slot.i());     // Unwrap?                                                                // Nearest existing key slot
      final Int  p = locateNearestFreeSlotToKey(s, f.lower);                                                            // Absolute position of nearest free slot

      if (immediate() && empty().b()) stop("Insert after find requires a non empty set of slots");                      // The slots must have at least one element

      new If (s.Sub(p).abs().ge(redistributionWidth()))                                                                 // Redistribution width
       {void Then()
         {final Int b = new Int(getSlotToKeyIndex(f.slot.i()));                                                         // Index of the key before redistribution
          redistribute();                                                                                               // Redistribute slots
          final Int a = new Int(getKeyToSlotIndex(b));                                                                  // Recover new position of slot referring to the found key
          K.copy(usedKeys.firstZero());                                                                                 // Position for key in key slots
          //final Find F = find(Key);                                                                                   // Locate key in redistributed slots
          f.set(a, f.lower, f.higher);                                                                                  // Locate key in redistributed slots
          s.set(new Int(f.slot.i()));                                                                                   // Nearest existing key slot
          p.set(locateNearestFreeSlotToKey(s, f.lower));                                                                // Absolute position of nearest free slot
         }
       };

      new If (p.lt(s))                                                                                                  // The nearest free slot is lower than nearest found key slot
       {void Then()
         {final Runnable lower = new Runnable()                                                                         // The key should be inserted below the found key
           {public void run()
             {new If (s.Sub(p).eq(1))                                                                                   // Previous slot is free so no movement required
               {void Then()                                                                                             // Insert key immediately below nearest found key slot in an already empty slot
                 {final Bint L = usedSlotsToKeys.prevOne(p);                                                            // Previous used slot
                  final Int  l = new Int();                                                                             // Previous used slot
                  new If (L.notValid()) {void Then() {l.set(0);} void Else() {l.set(L).inc();}};                        // Last zero
                  final Int m = l.add(p).down();                                                                        // Middle zero between lower and upper limit of zeros
                  setSlotAndKey(P.set(m), K.i(), Key);                                                                  // Insert key in the middle zero
                 }
                void Else()                                                                                             // Previous free slot has intervening occupied slots
                 {//Improvements Distribute block across any adjacent zeros
                  shiftDownOne (p.Inc(), s.Sub(p).Dec());                                                               // Shift block starting one slot above lower free slot and ending one slot below nearest found key slot
                  setSlotAndKey(P.set(s.Dec()), K.i(), Key);                                                            // Insert key immediately below nearest found key slot in a slot freed by moving the previous block down one step
                 }
               };
             }
           };

          final Runnable upper = new Runnable()                                                                         // The key should be inserted above the found key
           {public void run()
             {shiftDownOne(p.Inc(), s.Sub(p));                                                                          // Shift block one slot up from nearest lower free slot and the nearest found key slot down one step
              setSlotAndKey(P.set(s), K.i(), Key);                                                                      // Insert key in nearest found key slot
             }
           };

          if      (insertBelow) {lower.run();}                                                                          // Known in advance that the key should be inserted below the found key with the nearest free slot being lower than the found key
          else if (insertAbove) {upper.run();}                                                                          // Known in advance that the key should be inserted above the found key with the nearest free slot being lower than the found key
          else                                                                                                          // Determine at runtime whether to insert above or below the found key
           {new If (f.lower)                                                                                            // Insert key lower than nearest found key slot
             {void Then() {lower.run();}
              void Else() {upper.run();}                                                                                // Insert above nearest found key slot
             };
           }
         }

        void Else()                                                                                                     // The nearest free slot is higher than nearest found key slot
         {final Runnable lower = new Runnable()                                                                         // The key should be inserted below the found key
           {public void run()
             {shiftUpOne   (s, p.Sub(s));                                                                               // Shift nearest found key and its following neighbors up one step
              setSlotAndKey(P.set(s), K.i(), Key);                                                                      // Insert key in nearest found key slot
             }
           };

          final Runnable upper = new Runnable()                                                                         // The key should be inserted above the found key
           {public void run()
             {new If (p.Sub(s).eq(1))                                                                                   // Next slot is free so no movement required
               {void Then()                                                                                             // Insert key immediately above nearest found key slot in an already empty slot
                 {final Bint U = usedSlotsToKeys.nextOne(p);                                                            // Next used slot
                  final Int  u = new Int();                                                                             // Next used slot
                  new If (U.notValid()) {void Then() {u.set(numberOfSlotsToKeys());} void Else() {u.set(U).Inc();}};    // Last zero
                  final Int m = u.add(p).down();                                                                        // Middle zero between lower and upper limit of zeros
                  setSlotAndKey(P.set(m), K.i(), Key);                                                                  // Insert key in the middle zero
                 }
                void Else()                                                                                             // Next free slot has intervening occupied slots
                 {shiftUpOne   (s.Inc(), p.Sub(s).Dec());                                                               // Shift block above nearest found key slot
                  final Int k = s.Inc();                                                                                // Insert at this index
                  setSlotAndKey(P.set(k), K.i(), Key);                                                                  // Insert key immediately above nearest found key slot in a slot freed by moving the block above up one step
                 }
               };
             }
           };

          if      (insertBelow) {lower.run();}                                                                          // Known in advance that the key should be inserted below the found key
          else if (insertAbove) {upper.run();}                                                                          // Known in advance that the key should be inserted above the found key
          else                                                                                                          // Determine at runtime whether to insert the key above or below the found key
           {new If (f.higher)                                                                                           // Insert higher than nearest found key slot
             {void Then() {upper.run();}
              void Else() {lower.run();}
             };
           }
         }
       };

      return P;                                                                                                         // Slot into which the key was inserted
     }

    public String toString ()
     {return "Find(" + "slot=" + slot + ", lower=" + lower + ", higher=" + higher +
             ", equal=" + equal + ", empty=" + empty + ')';
     }

    StringBuilder print() {return new StringBuilder(""+this);}

   }

  Find find (Int Key)                                                                                                   // Find a key in the slots
   {final BitSet u = usedSlotsToKeys;
    final Find   f = new Find();
//  f.slot.invalidate();                                                                                                // Show that nothing has been found yet

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
                           {new If (u.canGoLeftToOne(p))                                                                // Go left if possible  to search first part of range if it exists
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
                                       {new If (u.canGoRightToOne(p))                                                   // Greater than anything in the left subrange so perhaps part of right hand subrange
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
                               {new If (u.canGoRightToOne(p))                                                           // Could not go left so must have gone right
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

  Bint findGe(Int Key)                                                                                                  // Find the index of the first key in the slots that is either equal to or greater than the specified key else return invalid if there is no such key
   {final Find f = find(Key);                                                                                           // Find the key result
    final Bint r = new Bint();                                                                                          // If the slots contains keys and one of them is greater than or equal to the search key, then return the index of that key, else return invalid
    new If (f.equal.or(f.lower))                                                                                        // Found the index of a key that is greater than or equal to the search key
     {void Then() {r.set                         (f.slot.i()) ;}                                                        // Slot index of found key
      void Else() {r.copy(usedSlotsToKeys.nextOne(f.slot.i()));}                                                        // Found the index of a key that was less than the search key, so the next index up, if it exists must be the one we want. Have to use copy because the value might be invalid.
     };
    return r;                                                                                                           // Result found if valid, if invalid greater than any key in the slots
   }

  final class Insert                                                                                                    // Results of an insertion
   {final Int  key      = new Int ("key");                                                                              // The key being inserted
    final Bint slot     = new Bint();                                                                                   // Slot index referring to the inserted key
    final Bool inserted = new Bool("inserted");                                                                         // True if the key did not exist prior to the insertion else false

    void set(Int Key, Int Slot, boolean Inserted) {key.set(Key); slot.set(Slot); inserted.set(Inserted);}               // Record insertion result

    public String toString ()
     {final StringBuilder s = new StringBuilder();                                                                      // Print insertion result
      s.append("Insert: "+key+", "+slot+", "+inserted);
      return ""+s;
     }
   }

  Insert insert (Int Key)                                                                                               // Insert a key into slots and return the slot chosen and indicate whether this is a new key or an existing one
   {if (immediate() && usedKeys.full().b()) stop("No more space to insert key:", Key);                                  // No space left
    final Insert i = new Insert();                                                                                      // Record the insertion result
    new If (empty())                                                                                                    // Slots are empty so insert immediately in the middle
     {void Then()
       {i.set(Key, insertEmpty(Key), true);                                                                             // New key as slots are empty
       }
      void Else()                                                                                                       // Insert into a free slot while maintaining the order of the slots
       {final Find f = find(Key);                                                                                       // Find nearest existing key in slots
        new If (f.equal)
         {void Then() {i.set(Key, f.slot.i(),    false);}                                                               // Existing key
          void Else() {i.set(Key, f.insert(Key), true);}                                                                // New key in non empty slots per find results
         };
       }
     };
    return i;                                                                                                           // Slot into which the key was inserted
   }

  Int insertEmpty (Int Key)                                                                                             // Insert a key into slots known to be empty and return the slot chosen
   {if (immediate() && !usedKeys.empty().b()) stop("Slots must be empty");                                              // Slots must be empty
    final Int P = new Int();                                                                                            // Slot into which the key was inserted
    setSlotAndKey(P.set(new Int(numberOfKeys)), new Int(0), Key);                                                       // Insert immediately in the center
    return P;                                                                                                           // Place key in first key slot
   }

  void delete (Int Slot)                                                                                                // Delete a key
   {delSlotAndKey(Slot);                                                                                                // Delete key
   }

//D2 Print                                                                                                              // Print the slots

  StringBuilder print ()                                                                                                // Print the slots
   {final StringBuilder s = new StringBuilder();
    final int[]N = range(numberOfSlotsToKeys());
    final int[]R = range(numberOfKeys);
    final Int c = count();
    new I()
     {void a()
       {clearStringBuilder(s);
        s.append(f("Slots    : size: %2d, count: %2d\n", numberOfKeys, c.i()));                                         // Title line
        s.append("positions: ");   for (int i : N) s.append(f(" %3d", i));
        s.append("\nslotsKeys: "); for (int i : N) s.append(f(" %3d", getSlotToKeyIndex(i)));
        s.append("\nkeysSlots: "); for (int i : N) s.append(f(" %3d", getKeyToSlotIndex(i)));
        s.append("\nusedSlots: "); for (int i : N) s.append(          usedSlotsToKeys.getBitNC(i) ? "   X" : "   .");
        s.append("\nusedKeys : "); for (int i : R) s.append(          usedKeys       .getBitNC(i) ? "   X" : "   .");
        s.append("\nkeys     : "); for (int i : R) s.append(f(" %3d", getKeyValue(i)));
        s.append("\n");
       }
      boolean trace() {return false;}
     };

    return s;
   }

  public String toString() {return ""+print();}                                                                         // Print the slots as a string

//D1 Tests                                                                                                              // Tests

//D2 Slots                                                                                                              // Test the slots

  static void test_slots (boolean Ex)
   {sayCurrentTestName();
    final Slots s = new Slots(new Build().numberOfKeys(8).immediate(Ex))
     {void slotsCode()
       {
        putSlotToKeys(new Int(2), new Int(3));
/*
        usedSlotsToKeys.empty().ok(false);
        usedSlotsToKeys.full().ok(false);
        putSlotToKeys(new Int(0), new Int(1));

        locateFirstUsedSlot().ok(0);
        locateLastUsedSlot ().ok(2);

        putKey (new Int(1), new Int(11));
        putKey (new Int(3), new Int(22));

        final Slots s = this;
        final Slots t = new Slots(s.build.parent(s).memory(null));                                                      // Create some more memory and copy the slots into it
        t.copy(s);
        s.check(s.print(), """
Slots    : size:  8, count:  2
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    1   0   3   0   0   0   0   0   0   0   0   0   0   0   0   0
keysSlots:    0   0   0   2   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    X   .   X   .   .   .   .   .   .   .   .   .   .   .   .   .
usedKeys :    .   X   .   X   .   .   .   .
keys     :    0  11   0  22   0   0   0   0
""");

        delSlotToKeys(new Int(2));
        delKey       (new Int(3));
        //new I() {void a() {testStop(s);}};
        s.check(s.print(), """
Slots    : size:  8, count:  1
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    1   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0
keysSlots:    0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    X   .   .   .   .   .   .   .   .   .   .   .   .   .   .   .
usedKeys :    .   X   .   .   .   .   .   .
keys     :    0  11   0   0   0   0   0   0
""");

        for (int i = 0, N = numberOfKeys; i < N; i++) putKey (new Int(i), new Int(i+1));
        usedKeys.empty().ok(false);
        usedKeys.full ().ok(true);


        delKey(new Int(3)); locateFirstUnusedKey().ok(3);
        delKey(new Int(4)); locateFirstUnusedKey().ok(3);
        delKey(new Int(2)); locateFirstUnusedKey().ok(2);

        t.check(t.print(), """
Slots    : size:  8, count:  2
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    1   0   3   0   0   0   0   0   0   0   0   0   0   0   0   0
keysSlots:    0   0   0   2   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    X   .   X   .   .   .   .   .   .   .   .   .   .   .   .   .
usedKeys :    .   X   .   X   .   .   .   .
keys     :    0  11   0  22   0   0   0   0
""");
*/
        execute();
       }
     };
   }

  static void test_slots()
   {          test_slots(true);
              test_slots(false);
   }

  static void test_locateNearestFreeSlotToKey(boolean Ex)
   {sayCurrentTestName();
    final Slots s = new Slots(new Build().numberOfKeys(16).immediate(Ex))
     {void slotsCode()
       {setSlots(2, 4, 5, 6, 9, 10, 12);
        final Slots s = this;
        //stop(s);
        check(print(), """
Slots    : size: 16, count:  0
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18  19  20  21  22  23  24  25  26  27  28  29  30  31
slotsKeys:    0   0   1   0   2   3   4   0   0   5   6   0   7   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0
keysSlots:    0   2   4   5   6   9  10  12   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   X   .   X   X   X   .   .   X   X   .   X   .   .   .   .   .   .   .   .   .   .   .   .   .   .   .   .   .   .   .
usedKeys :    .   .   .   .   .   .   .   .   .   .   .   .   .   .   .   .
keys     :    0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0
""");

        //locateNearestFreeSlotToKey(new Int( 2), new Bool( true)).ok( 1);
          locateNearestFreeSlotToKey(new Int( 2), new Bool(false)).ok( 3);
        //locateNearestFreeSlotToKey(new Int( 4), new Bool( true)).ok( 3);
        //locateNearestFreeSlotToKey(new Int( 5), new Bool( true)).ok( 3);
        //locateNearestFreeSlotToKey(new Int( 5), new Bool(false)).ok( 7);
        //locateNearestFreeSlotToKey(new Int( 6), new Bool( true)).ok( 7);
        //locateNearestFreeSlotToKey(new Int( 9), new Bool( true)).ok( 8);
        //locateNearestFreeSlotToKey(new Int(10), new Bool( true)).ok(11);
        //locateNearestFreeSlotToKey(new Int(12), new Bool( true)).ok(11);
        //locateNearestFreeSlotToKey(new Int(12), new Bool(false)).ok(13);

        execute();
       }
     };
   }

  static void test_locateNearestFreeSlotToKey()
   {          test_locateNearestFreeSlotToKey(true);
              test_locateNearestFreeSlotToKey(false);
   }

  static void test_alloc(boolean Ex)
   {sayCurrentTestName();
    final Slots s = new Slots(new Build().numberOfKeys(4).immediate(Ex))
     {void slotsCode()
       {putKey(new Int(2),  new Int(1));
        final Slots s = this;
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  4, count:  1
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
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  4, count:  4
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
   {          test_alloc(true);
              test_alloc(false);
   }

  static void test_set_del_slot_key(boolean Ex)
   {sayCurrentTestName();
    final Slots s = new Slots(new Build().numberOfKeys(4).immediate(Ex))
     {void slotsCode()
       {setSlotAndKey(new Int(3),  new Int(2),  new Int(1));
        setSlotAndKey(new Int(4),  new Int(3),  new Int(2));
        final Slots s = this;
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  4, count:  2
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   0   0   2   3   0   0   0
keysSlots:    0   0   3   4   0   0   0   0
usedSlots:    .   .   .   X   X   .   .   .
usedKeys :    .   .   X   X
keys     :    0   0   1   2
""");

        delete(new Int(3));
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  4, count:  1
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
   {          test_set_del_slot_key(true);
              test_set_del_slot_key(false);
   }

  static void test_compact(boolean Ex)
   {sayCurrentTestName();
    final Slots s = new Slots(new Build().numberOfKeys(4).immediate(Ex))
     {void slotsCode()
       {setSlotAndKey(new Int(2),  new Int(1),  new Int(1));
        setSlotAndKey(new Int(4),  new Int(3),  new Int(2));
        final Slots s = this;
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  4, count:  2
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   0   1   0   3   0   0   0
keysSlots:    0   2   0   4   0   0   0   0
usedSlots:    .   .   X   .   X   .   .   .
usedKeys :    .   X   .   X
keys     :    0   1   0   2
""");

        compactSlotsLeft();
        //new I() {void a() {testStop(s);}};
        s.check(s.print(), """
Slots    : size:  4, count:  2
positions:    0   1   2   3   4   5   6   7
slotsKeys:    1   3   0   0   0   0   0   0
keysSlots:    0   0   0   1   0   0   0   0
usedSlots:    X   X   .   .   .   .   .   .
usedKeys :    .   X   .   X
keys     :    0   1   0   2
""");

        compactSlotsRight();
        //new I() {void a() {testStop(s);}};
        s.check(s.print(), """
Slots    : size:  4, count:  2
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   0   0   0   0   0   1   3
keysSlots:    0   6   0   7   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X
usedKeys :    .   X   .   X
keys     :    0   1   0   2
""");

        final StringBuilder T = new StringBuilder();
        compactKeysLeft((S, b, a)->{new I() {void a() {T.append(a.i()+"->"+b.i()+";");} boolean trace() {return false;}};});
        //new I() {void a() {testStop(T, s);}};
        ok(()->T, "3->0;");
        s.check(s.print(), """
Slots    : size:  4, count:  2
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   0   0   0   0   0   1   0
keysSlots:    7   6   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X
usedKeys :    X   X   .   .
keys     :    2   1   0   0
""");

        final StringBuilder U = new StringBuilder();
        compactKeysRight((S, b, a)->{new I() {void a() {U.append(a.i()+"->"+b.i()+";");} boolean trace() {return false;}};});
        //new I() {void a() {testStop(U, s);}};
        ok(()->U, "0->3;1->2;");
        s.check(s.print(), """
Slots    : size:  4, count:  2
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   0   0   0   0   0   2   3
keysSlots:    0   0   6   7   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X
usedKeys :    .   .   X   X
keys     :    0   0   1   2
""");

        final StringBuilder W = new StringBuilder();
        compactKeysLeft ((S, b, a)->{new I() {void a() {W.append(a.i()+"->"+b.i()+";");} boolean trace() {return false;}};});
        compactSlotsLeft();
        //new I() {void a() {testStop(W, s);}};
        ok(()->W, "3->0;2->1;");
        s.check(s.print(), """
Slots    : size:  4, count:  2
positions:    0   1   2   3   4   5   6   7
slotsKeys:    1   0   0   0   0   0   0   0
keysSlots:    1   0   0   0   0   0   0   0
usedSlots:    X   X   .   .   .   .   .   .
usedKeys :    X   X   .   .
keys     :    2   1   0   0
""");

        final StringBuilder X = new StringBuilder();
        compactKeysRight ((S, b, a)->{new I() {void a() {X.append(a.i()+"->"+b.i()+";");} boolean trace() {return false;}};});
        compactSlotsRight();
        //new I() {void a() {testStop(X, s);}};
        ok(()->X, "0->3;1->2;");
        s.check(s.print(), """
Slots    : size:  4, count:  2
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   0   0   0   0   0   2   3
keysSlots:    0   0   6   7   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X
usedKeys :    .   .   X   X
keys     :    0   0   1   2
""");
        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_compact()
   {          test_compact(true);
              test_compact(false);
   }

  static void test_redistribute(boolean Ex)
   {sayCurrentTestName();
    final Slots s = new Slots(new Build().numberOfKeys(8).immediate(Ex))
     {void slotsCode()
       {maxSteps(9_999_999);
        setSlotAndKey(new Int(2),   new Int(1),  new Int(1));
        setSlotAndKey(new Int(4),   new Int(3),  new Int(2));
        setSlotAndKey(new Int(7),   new Int(2),  new Int(3));
        setSlotAndKey(new Int(8),   new Int(4),  new Int(4));
        setSlotAndKey(new Int(12),  new Int(5),  new Int(5));
        setSlotAndKey(new Int(13),  new Int(6),  new Int(6));
        setSlotAndKey(new Int(14),  new Int(0),  new Int(7));
        final Slots s = this;
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  8, count:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   1   0   3   0   0   2   4   0   0   0   5   6   0   0
keysSlots:   14   2   7   4   8  12  13   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   X   .   X   .   .   X   X   .   .   .   X   X   X   .
usedKeys :    X   X   X   X   X   X   X   .
keys     :    7   1   3   2   4   5   6   0
""");

        redistribute();
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  8, count:  7
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
   {          test_redistribute(true);
              test_redistribute(false);
   }

  static void test_shift(boolean Ex)
   {sayCurrentTestName();
    final Slots s = new Slots(new Build().numberOfKeys(8).immediate(Ex))
     {void slotsCode()
       {maxSteps(9_999_999);
        setSlotAndKey(new Int(2),   new Int(1),  new Int(1));
        setSlotAndKey(new Int(4),   new Int(3),  new Int(2));
        setSlotAndKey(new Int(7),   new Int(2),  new Int(3));
        setSlotAndKey(new Int(8),   new Int(4),  new Int(4));
        setSlotAndKey(new Int(12),  new Int(5),  new Int(5));
        setSlotAndKey(new Int(13),  new Int(6),  new Int(6));
        setSlotAndKey(new Int(14),  new Int(0),  new Int(7));
        final Slots s = this;
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  8, count:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   1   0   3   0   0   2   4   0   0   0   5   6   0   0
keysSlots:   14   2   7   4   8  12  13   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   X   .   X   .   .   X   X   .   .   .   X   X   X   .
usedKeys :    X   X   X   X   X   X   X   .
keys     :    7   1   3   2   4   5   6   0
""");


        shiftUpOne(new Int(2), new Int(1));
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  8, count:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   1   3   0   0   2   4   0   0   0   5   6   0   0
keysSlots:   14   3   7   4   8  12  13   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   X   X   .   .   X   X   .   .   .   X   X   X   .
usedKeys :    X   X   X   X   X   X   X   .
keys     :    7   1   3   2   4   5   6   0
""");

        shiftUpOne(new Int(3), new Int(2));
        shiftUpOne(new Int(4), new Int(2));
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  8, count:  7
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
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  8, count:  7
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
   {          test_shift(true);
              test_shift(false);
   }

  static void test_mergeFromRightEven(boolean Ex)
   {sayCurrentTestName();
    final int N = 4;
    final Slots s = new Slots(new Build().numberOfKeys(N).immediate(Ex))
     {void slotsCode()
       {final Slots l = this;
        insert(new Int(2));
        insert(new Int(1));
        final Slots r = new Slots(new Build().numberOfKeys(N).immediate(Ex).parent(l))
         {void slotsCode()
           {final Insert i3 = insert(new Int(3)); i3.inserted.ok(true);
            final Insert i4 = insert(new Int(4)); i4.inserted.ok(true);
            final Insert j3 = insert(new Int(3)); j3.inserted.ok(false);  j3.slot.i().eq(i3.slot.i()).ok(true);
            final Insert j4 = insert(new Int(4)); j4.inserted.ok(false);  j4.slot.i().eq(i4.slot.i()).ok(true);
           }
         };
        mergeFromRightEven(r).ok(true);
        l.check(l.print(), """
Slots    : size:  4, count:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    1   0   0   0   3   0   2   0
keysSlots:    2   0   6   4   0   0   0   0
usedSlots:    X   .   X   .   X   .   X   .
usedKeys :    X   X   X   X
keys     :    2   1   4   3
""");
        r.check(r.print(), """
Slots    : size:  4, count:  2
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   3   0   0   0   2   0   0
keysSlots:    0   0   5   1   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .
usedKeys :    .   .   X   X
keys     :    0   0   4   3
""");
        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_mergeFromRightEven()
   {          test_mergeFromRightEven(true);
              test_mergeFromRightEven(false);
   }

  static void test_mergeFromLeftEven(boolean Ex)
   {sayCurrentTestName();
    final int N = 4;
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
        //new I() {void a() {testStop(l);}};
        l.check(l.print(), """
Slots    : size:  4, count:  2
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   1   0   0   0   0   0   0
keysSlots:    5   1   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .
usedKeys :    X   X   .   .
keys     :    2   1   0   0
""");
        //new I() {void a() {testStop(r);}};
        r.check(r.print(), """
Slots    : size:  4, count:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    1   0   0   0   3   0   2   0
keysSlots:    2   0   6   4   0   0   0   0
usedSlots:    X   .   X   .   X   .   X   .
usedKeys :    X   X   X   X
keys     :    2   1   4   3
""");
        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_mergeFromLeftEven()
   {          test_mergeFromLeftEven(true);
              test_mergeFromLeftEven(false);
   }

  static void test_mergeFromRightOdd(boolean Ex)
   {sayCurrentTestName();
    final int N = 5;
    final Slots s = new Slots(new Build().numberOfKeys(N).immediate(Ex))
     {void slotsCode()
       {final Slots l = this;
        //l.insert(new Int(2));
        //l.insert(new Int(1));
        final Slots r = new Slots(new Build().numberOfKeys(N).immediate(Ex).parent(l))
         {void slotsCode()
           {insert(new Int(4));
            insert(new Int(5));
           }
         };
        //new I() {void a() {testStop(l); }};
//        l.check(l.print(), """
//Slots    : size:  5, count:  2
//positions:    0   1   2   3   4   5   6   7   8   9
//slotsKeys:    0   0   1   0   0   0   0   0   0   0
//keysSlots:    5   2   0   0   0   0   0   0   0   0
//usedSlots:    .   .   X   .   .   X   .   .   .   .
//usedKeys :    X   X   .   .   .
//keys     :    2   1   0   0   0
//""");
        //new I() {void a() {testStop(r); }};
        r.check(r.print(), """
Slots    : size:  5, count:  2
positions:    0   1   2   3   4   5   6   7   8   9
slotsKeys:    0   0   0   0   0   0   0   0   1   0
keysSlots:    5   8   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   X   .   .   X   .
usedKeys :    X   X   .   .   .
keys     :    4   5   0   0   0
""");
        l.mergeFromRightOdd(r, new Int(3)).ok(true);
//
//        //new I() {void a() {testStop(l); }};
//        //new I() {void a() {testStop(r); }};
//        l.check(l.print(), """
//Slots    : size:  5, count:  5
//positions:    0   1   2   3   4   5   6   7   8   9
//slotsKeys:    1   0   0   0   2   0   4   0   3   0
//keysSlots:    2   0   4   8   6   0   0   0   0   0
//usedSlots:    X   .   X   .   X   .   X   .   X   .
//usedKeys :    X   X   X   X   X
//keys     :    2   1   3   5   4
//""");
//        r.check(r.print(), """
//Slots    : size:  5, count:  2
//positions:    0   1   2   3   4   5   6   7   8   9
//slotsKeys:    0   0   4   0   0   0   0   3   0   0
//keysSlots:    0   0   0   7   2   0   0   0   0   0
//usedSlots:    .   .   X   .   .   .   .   X   .   .
//usedKeys :    .   .   .   X   X
//keys     :    0   0   0   5   4
//""");
//        mergeFromRightOdd(l, new Int(3)).ok(false);
        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_mergeFromRightOdd()
   {          test_mergeFromRightOdd(true);
              test_mergeFromRightOdd(false);
   }

  static void test_mergeFromLeftOdd(boolean Ex)
   {sayCurrentTestName();
    final int   N = 5;
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
        //new I() {void a() {testStop(r);}};
        r.check(r.print(), """
Slots    : size:  5, count:  2
positions:    0   1   2   3   4   5   6   7   8   9
slotsKeys:    0   0   0   0   0   0   0   0   1   0
keysSlots:    5   8   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   X   .   .   X   .
usedKeys :    X   X   .   .   .
keys     :    4   5   0   0   0
""");
        //new I() {void a() {testStop(l);}};
        l.check(l.print(), """
Slots    : size:  5, count:  2
positions:    0   1   2   3   4   5   6   7   8   9
slotsKeys:    0   0   0   0   0   0   0   0   1   0
keysSlots:    5   8   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   X   .   .   X   .
usedKeys :    X   X   .   .   .
keys     :    1   2   0   0   0
""");

        r.mergeFromLeftOdd(l, new Int(3)).ok(true);

        //new I() {void a() {testStop(r);}};
        r.check(r.print(), """
Slots    : size:  5, count:  5
positions:    0   1   2   3   4   5   6   7   8   9
slotsKeys:    0   0   1   0   2   0   4   0   3   0
keysSlots:    0   2   4   8   6   0   0   0   0   0
usedSlots:    X   .   X   .   X   .   X   .   X   .
usedKeys :    X   X   X   X   X
keys     :    1   2   3   5   4
""");
        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_mergeFromLeftOdd()
   {          test_mergeFromLeftOdd(true);
              test_mergeFromLeftOdd(false);
   }

  static void test_find(boolean Ex)
   {sayCurrentTestName();
    final Slots s = new Slots(new Build().numberOfKeys(8).immediate(Ex))
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
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  8, count:  4
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

       //new I() {void a() {testStop(s.usedSlotsToKeys);}};
       ok(()->s.usedSlotsToKeys, """
BitSet            0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16 |  1  0  1  0  1  0  0  0  0  0  0  0  0  0  0  1
One:
   2   16    8 |  1  1  1  0  0  0  0  1
   3   24    4 |  1  1  0  1
   4   28    2 |  1  1
   5   30    1 |  1
Zero:
   1   31    8 |  0  0  0  0  0  0  0  0
   2   39    4 |  0  0  0  0
   3   43    2 |  0  0
   4   45    1 |  0
""");
        maxSteps(9_999_999);
        execute();
        check(find(new Int( 5)).print(), "Find(slot=Bint(0), lower=true, higher=false, equal=false, empty=false)");
        check(find(new Int(11)).print(), "Find(slot=Bint(0), lower=true, higher=true, equal=true, empty=false)");
        check(find(new Int(15)).print(), "Find(slot=Bint(2), lower=true, higher=false, equal=false, empty=false)");
        check(find(new Int(22)).print(), "Find(slot=Bint(2), lower=true, higher=true, equal=true, empty=false)");
        check(find(new Int(25)).print(), "Find(slot=Bint(4), lower=true, higher=false, equal=false, empty=false)");
        check(find(new Int(33)).print(), "Find(slot=Bint(4), lower=true, higher=true, equal=true, empty=false)");
        check(find(new Int(35)).print(), "Find(slot=Bint(15), lower=true, higher=false, equal=false, empty=false)");
        check(find(new Int(44)).print(), "Find(slot=Bint(15), lower=true, higher=true, equal=true, empty=false)");
        check(find(new Int(45)).print(), "Find(slot=Bint(15), lower=false, higher=true, equal=false, empty=false)");
       }
     };
   }

  static void test_find()
   {          test_find(true);
              test_find(false);
   }

  static void test_findRight(boolean Ex)                                                                                          // Same as find but with the slots on the right
   {sayCurrentTestName();
    final Slots s = new Slots(new Build().numberOfKeys(8).immediate(Ex))
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
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  8, count:  4
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   0   0   0   0   0   0   1   0   3   0   5   0   0
keysSlots:   15   9   0  11   0  13   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   .   .   .   X   .   X   .   X   .   X
usedKeys :    X   X   .   X   .   X   .   .
keys     :   44  11   0  22   0  33   0   0
""");

       getSlotToKeyValue(new Int( 9)).ok(11);
       getSlotToKeyValue(new Int(11)).ok(22);
       getSlotToKeyValue(new Int(13)).ok(33);
       getSlotToKeyValue(new Int(15)).ok(44);

       //new I() {void a() {testStop(s.usedSlotsToKeys);}};
       ok(()->s.usedSlotsToKeys, """
BitSet            0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
   1    0   16 |  0  0  0  0  0  0  0  0  0  1  0  1  0  1  0  1
One:
   2   16    8 |  0  0  0  0  1  1  1  1
   3   24    4 |  0  0  1  1
   4   28    2 |  0  1
   5   30    1 |  1
Zero:
   1   31    8 |  0  0  0  0  0  0  0  0
   2   39    4 |  0  0  0  0
   3   43    2 |  0  0
   4   45    1 |  0
""");
        maxSteps(9_999_999);
        execute();
        check(find(new Int( 5)).print(), "Find(slot=Bint(9), lower=true, higher=false, equal=false, empty=false)");
        check(find(new Int(11)).print(), "Find(slot=Bint(9), lower=true, higher=true, equal=true, empty=false)");
        check(find(new Int(15)).print(), "Find(slot=Bint(11), lower=true, higher=false, equal=false, empty=false)");
        check(find(new Int(22)).print(), "Find(slot=Bint(11), lower=true, higher=true, equal=true, empty=false)");
        check(find(new Int(25)).print(), "Find(slot=Bint(13), lower=true, higher=false, equal=false, empty=false)");
        check(find(new Int(33)).print(), "Find(slot=Bint(13), lower=true, higher=true, equal=true, empty=false)");
        check(find(new Int(35)).print(), "Find(slot=Bint(15), lower=true, higher=false, equal=false, empty=false)");
        check(find(new Int(44)).print(), "Find(slot=Bint(15), lower=true, higher=true, equal=true, empty=false)");
        check(find(new Int(45)).print(), "Find(slot=Bint(15), lower=false, higher=true, equal=false, empty=false)");
       }
     };
   }

  static void test_findRight()
   {          test_findRight(true);
              test_findRight(false);
   }

  static void test_insert(boolean Ex)
   {sayCurrentTestName();
    final Slots s = new Slots(new Build().numberOfKeys(8).immediate(Ex))
     {void slotsCode()
       {final Slots         s = this;
        final StringBuilder t = new StringBuilder();

        new ForCount(new Int(8))                                                                                        // Using this rather complex for loop reduces the amount of code generated
         {void body(Int Index)
           {final Int  k = new Int();
            final int[]i = new int[] {14, 13, 16, 15, 18, 17, 12, 11};
            new I()                                                                                                     // Set the key to insert
             {void   a() {k.ex(Int.Ops.set, i[Index.i()]);}
              String v()
               {final StringBuilder s = new StringBuilder("case("+Index.vn()+") ");
                for(int j = 0; j < i.length; ++j) s.append(""+j+":"+k.vn() + " <= "+i[j]+"; ");
                s.append(" endcase");
                return ""+s;
               }
              boolean trace() {return false;}
             };

            insert(k);
            final StringBuilder p = s.print();
            new I() {void a() {t.append(p);}  boolean trace() {return false;}};
           }
         };

        check(t, """
Slots    : size:  8, count:  1
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0
keysSlots:    8   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   .   .   X   .   .   .   .   .   .   .
usedKeys :    X   .   .   .   .   .   .   .
keys     :   14   0   0   0   0   0   0   0
Slots    : size:  8, count:  2
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   1   0   0   0   0   0   0   0   0   0   0   0   0
keysSlots:    8   3   0   0   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   X   .   .   .   .   X   .   .   .   .   .   .   .
usedKeys :    X   X   .   .   .   .   .   .
keys     :   14  13   0   0   0   0   0   0
Slots    : size:  8, count:  3
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   1   0   0   0   0   0   0   0   0   2   0   0   0
keysSlots:    8   3  12   0   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   X   .   .   .   .   X   .   .   .   X   .   .   .
usedKeys :    X   X   X   .   .   .   .   .
keys     :   14  13  16   0   0   0   0   0
Slots    : size:  8, count:  4
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   1   0   0   0   0   0   0   3   0   2   0   0   0
keysSlots:    8   3  12  10   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   X   .   .   .   .   X   .   X   .   X   .   .   .
usedKeys :    X   X   X   X   .   .   .   .
keys     :   14  13  16  15   0   0   0   0
Slots    : size:  8, count:  5
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   1   0   0   0   0   0   0   3   0   2   0   4   0
keysSlots:    8   3  12  10  14   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   X   .   .   .   .   X   .   X   .   X   .   X   .
usedKeys :    X   X   X   X   X   .   .   .
keys     :   14  13  16  15  18   0   0   0
Slots    : size:  8, count:  6
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   1   0   0   0   0   0   0   3   0   2   5   4   0
keysSlots:    8   3  12  10  14  13   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   X   .   .   .   .   X   .   X   .   X   X   X   .
usedKeys :    X   X   X   X   X   X   .   .
keys     :   14  13  16  15  18  17   0   0
Slots    : size:  8, count:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   6   0   1   0   0   0   0   0   0   3   0   2   5   4   0
keysSlots:    8   3  12  10  14  13   1   0   0   0   0   0   0   0   0   0
usedSlots:    .   X   .   X   .   .   .   .   X   .   X   .   X   X   X   .
usedKeys :    X   X   X   X   X   X   X   .
keys     :   14  13  16  15  18  17  12   0
Slots    : size:  8, count:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    7   6   0   1   0   0   0   0   0   0   3   0   2   5   4   0
keysSlots:    8   3  12  10  14  13   1   0   0   0   0   0   0   0   0   0
usedSlots:    X   X   .   X   .   .   .   .   X   .   X   .   X   X   X   .
usedKeys :    X   X   X   X   X   X   X   X
keys     :   14  13  16  15  18  17  12  11
""");
        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_insert()
   {          test_insert(true);
              test_insert(false);
   }

  static void test_insert2(boolean Ex)
   {sayCurrentTestName();
    final Slots s = new Slots(new Build().numberOfKeys(8).immediate(Ex))
     {void slotsCode()
       {insert(new Int(11));
        insert(new Int(12));
        insert(new Int(13));
        insert(new Int(15));
        insert(new Int(16));
        insert(new Int(17));
        insert(new Int(18));
        insert(new Int(14));
        final Slots s = this;
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  8, count:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   0   1   0   2   7   3   0   4   0   5   0   6   0
keysSlots:    2   4   6   8  10  12  14   7   0   0   0   0   0   0   0   0
usedSlots:    .   .   X   .   X   .   X   X   X   .   X   .   X   .   X   .
usedKeys :    X   X   X   X   X   X   X   X
keys     :   11  12  13  15  16  17  18  14
""");
        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_insert2()
   {          test_insert2(true);
              test_insert2(false);
   }

  static void test_findGe(boolean Ex)
   {sayCurrentTestName();
    final Slots s = new Slots(new Build().numberOfKeys(8).immediate(Ex))
     {void slotsCode()
       {insert(new Int(11));
        insert(new Int(22));
        insert(new Int(33));
        insert(new Int(44));
        insert(new Int(55));
        insert(new Int(66));
        insert(new Int(77));
        insert(new Int(88));

        redistribute();
        //testStop(this, usedSlotsToKeys);
        check(print(), """
Slots    : size:  8, count:  8
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
   1   31    8 |  0  0  0  0  0  0  0  0
   2   39    4 |  0  0  0  0
   3   43    2 |  0  0
   4   45    1 |  0
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

        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_findGe()
   {          test_findGe(true);
              test_findGe(false);
   }

  static void test_splitRightEven(boolean Ex)
   {sayCurrentTestName();
    final int N = 8;
    final Slots s = new Slots(new Build().numberOfKeys(N).immediate(Ex))
     {void slotsCode()
       {insert(new Int(11));
        insert(new Int(12));
        insert(new Int(13));
        insert(new Int(15));
        insert(new Int(16));
        insert(new Int(17));
        insert(new Int(18));
        insert(new Int(14));
        final Slots s = this;
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  8, count:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   0   1   0   2   7   3   0   4   0   5   0   6   0
keysSlots:    2   4   6   8  10  12  14   7   0   0   0   0   0   0   0   0
usedSlots:    .   .   X   .   X   .   X   X   X   .   X   .   X   .   X   .
usedKeys :    X   X   X   X   X   X   X   X
keys     :   11  12  13  15  16  17  18  14
""");
        final Slots t = new Slots(new Build().numberOfKeys(N).immediate(Ex).parent(s));
        t.insert(new Int(11));
        s.splitRightEven(t).ok(14);
        //new I() {void a() {testStop(s);}};
        s.check(s.print(), """
Slots    : size:  8, count:  4
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   0   0   1   0   0   0   2   0   0   0   7   0   0
keysSlots:    1   5   9   0   0   0   0  13   0   0   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   X   .   .
usedKeys :    X   X   X   .   .   .   .   X
keys     :   11  12  13   0   0   0   0  14
""");
        //new I() {void a() {testStop(t);}};
        t.check(t.print(), """
Slots    : size:  8, count:  4
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   3   0   0   0   4   0   0   0   5   0   0   0   6   0   0
keysSlots:    0   0   0   1   5   9  13   0   0   0   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   X   .   .
usedKeys :    .   .   .   X   X   X   X   .
keys     :    0   0   0  15  16  17  18   0
""");
        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_splitRightEven()
   {          test_splitRightEven(true);
              test_splitRightEven(false);
   }

  static void test_splitLeftEven(boolean Ex)
   {sayCurrentTestName();
    final int N = 8;
    final Slots r = new Slots(new Build().numberOfKeys(N).immediate(Ex))
     {void slotsCode()
       {insert(new Int(11));
        insert(new Int(12));
        insert(new Int(13));
        insert(new Int(15));
        insert(new Int(16));
        insert(new Int(17));
        insert(new Int(18));
        insert(new Int(14));
        final Slots r = this;
        //new I() {void a() {testStop(r);}};
        r.check(r.print(), """
Slots    : size:  8, count:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   0   1   0   2   7   3   0   4   0   5   0   6   0
keysSlots:    2   4   6   8  10  12  14   7   0   0   0   0   0   0   0   0
usedSlots:    .   .   X   .   X   .   X   X   X   .   X   .   X   .   X   .
usedKeys :    X   X   X   X   X   X   X   X
keys     :   11  12  13  15  16  17  18  14
""");
        final Slots l = new Slots(new Build().numberOfKeys(N).immediate(Ex).parent(r));
        l.insert(new Int(11)); l.compactSlotsRight();
        r.splitLeftEven(l).ok(14);
        //new I() {void a() {testStop(l);}};
        //new I() {void a() {testStop(r);}};
        l.check(l.print(), """
Slots    : size:  8, count:  4
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   0   0   1   0   0   0   2   0   0   0   7   0   0
keysSlots:    1   5   9   0   0   0   0  13   0   0   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   X   .   .
usedKeys :    X   X   X   .   .   .   .   X
keys     :   11  12  13   0   0   0   0  14
""");
        r.check(r.print(), """
Slots    : size:  8, count:  4
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   3   0   0   0   4   0   0   0   5   0   0   0   6   0   0
keysSlots:    0   0   0   1   5   9  13   0   0   0   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   X   .   .
usedKeys :    .   .   .   X   X   X   X   .
keys     :    0   0   0  15  16  17  18   0
""");
        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_splitLeftEven()
   {          test_splitLeftEven(true);
              test_splitLeftEven(false);
   }

  static void test_splitRightOdd(boolean Ex)
   {sayCurrentTestName();
    final int N = 7;
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
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  7, count:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   0   0   0   1   0   0   2   6   0   3   4   5
keysSlots:    2   5   8  11  12  13   9   0   0   0   0   0   0   0
usedSlots:    .   .   X   .   .   X   .   .   X   X   .   X   X   X
usedKeys :    X   X   X   X   X   X   X
keys     :   11  12  13  15  16  17  14
""");
        final Slots t = new Slots(new Build().numberOfKeys(N).immediate(Ex).parent(s));
        t.insert(new Int(11));
        s.splitRightOdd(t).ok(6);
        //new I() {void a() {testStop(s);}};
        s.check(s.print(), """
Slots    : size:  7, count:  3
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   0   0   0   0   1   0   0   0   2   0   0   0
keysSlots:    2   6  10   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   X   .   .   .   X   .   .   .   X   .   .   .
usedKeys :    X   X   X   .   .   .   .
keys     :   11  12  13   0   0   0  14
""");
        //new I() {void a() {testStop(t);}};
        t.check(t.print(), """
Slots    : size:  7, count:  3
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   3   0   0   0   4   0   0   0   5   0   0   0
keysSlots:    0   0   0   2   6  10   0   0   0   0   0   0   0   0
usedSlots:    .   .   X   .   .   .   X   .   .   .   X   .   .   .
usedKeys :    .   .   .   X   X   X   .
keys     :    0   0   0  15  16  17   0
""");
        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_splitRightOdd()
   {          test_splitRightOdd(true);
              test_splitRightOdd(false);
   }

  static void test_splitLeftOdd(boolean Ex)
   {sayCurrentTestName();
    final int N = 7;
    final Slots s = new Slots(new Build().numberOfKeys(N).immediate(Ex))
     {void slotsCode()
       {insert(new Int(11));
        insert(new Int(12));
        insert(new Int(13));
        insert(new Int(15));
        insert(new Int(16));
        insert(new Int(17));
        insert(new Int(14));
        final Slots r = this;
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  7, count:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   0   0   0   1   0   0   2   6   0   3   4   5
keysSlots:    2   5   8  11  12  13   9   0   0   0   0   0   0   0
usedSlots:    .   .   X   .   .   X   .   .   X   X   .   X   X   X
usedKeys :    X   X   X   X   X   X   X
keys     :   11  12  13  15  16  17  14
""");
        final Slots l = new Slots(new Build().numberOfKeys(N).immediate(Ex).parent(r));
        l.insert(new Int(11)); l.compactSlotsRight();
        r.splitLeftOdd(l).ok(6);
        //new I() {void a() {testStop(t);}};
        //new I() {void a() {testStop(s);}};
        l.check(l.print(), """
Slots    : size:  7, count:  3
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   0   0   0   0   1   0   0   0   2   0   0   0
keysSlots:    2   6  10   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   X   .   .   .   X   .   .   .   X   .   .   .
usedKeys :    X   X   X   .   .   .   .
keys     :   11  12  13   0   0   0   0
""");
        r.check(r.print(), """
Slots    : size:  7, count:  3
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   3   0   0   0   4   0   0   0   5   0   0   0
keysSlots:    0   0   0   2   6  10   0   0   0   0   0   0   0   0
usedSlots:    .   .   X   .   .   .   X   .   .   .   X   .   .   .
usedKeys :    .   .   .   X   X   X   .
keys     :    0   0   0  15  16  17  14
""");
        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_splitLeftOdd()
   {          test_splitLeftOdd(true);
              test_splitLeftOdd(false);
   }

  static void test_clear(boolean Ex)
   {sayCurrentTestName();
    final int N = 7;
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
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  7, count:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   0   0   0   1   0   0   2   6   0   3   4   5
keysSlots:    2   5   8  11  12  13   9   0   0   0   0   0   0   0
usedSlots:    .   .   X   .   .   X   .   .   X   X   .   X   X   X
usedKeys :    X   X   X   X   X   X   X
keys     :   11  12  13  15  16  17  14
""");
        initializeMemory();
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  7, count:  0
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   0   0   0   0   0   0   0   0   0   0   0   0
keysSlots:    0   0   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   .   .   .   .   .   .   .   .
usedKeys :    .   .   .   .   .   .   .
keys     :    0   0   0   0   0   0   0
""");
        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_clear()
   {          test_clear(true);
              test_clear(false);
   }

//  static void test_stuck(boolean Ex)
//   {sayCurrentTestName();
//    final Slots s = new Slots(new Build().numberOfKeys(8).immediate(Ex))
//     {void slotsCode()
//       {initializeMemory();
//        stuckPush(new Int(11));
//        stuckPush(new Int(22));
//        stuckPush(new Int(33));
//        stuckPush(new Int(44));
//        stuckPut (new Int(1), new Int(2));
//        stuckPut (new Int(3), new Int(4));
//        stuckGet (new Int(0)).ok(11);
//        stuckGet (new Int(1)).ok(2);
//        stuckGet (new Int(2)).ok(33);
//        stuckGet (new Int(3)).ok(4);
//        stuckPop ().ok(4);
//        stuckPop ().ok(33);
//        stuckPop ().ok(2);
//        stuckPop ().ok(11);
//        maxSteps(9_999_999);
//        execute();
//       }
//     };
//   }
//
//  static void test_stuck()
//   {          test_stuck(true);
//              test_stuck(false);
//   }

  static void test_insertKnown(boolean Ex)
   {sayCurrentTestName();
    final int N = 7;
    final Slots s = new Slots(new Build().numberOfKeys(N).immediate(Ex))
     {void slotsCode()
       {final Slots s = this;
        insertEmpty(new Int(4)).ok(7);
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  7, count:  1
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   0   0   0   0   0   0   0   0   0   0   0   0
keysSlots:    7   0   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   .   X   .   .   .   .   .   .
usedKeys :    X   .   .   .   .   .   .
keys     :    4   0   0   0   0   0   0
""");
        final Find f = new Find().set(new Int(7), true);  f.insert(new Int(2));
        final Find F = new Find().set(new Int(7), false); F.insert(new Int(6));
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  7, count:  3
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   0   1   0   0   0   0   0   0   0   2   0   0
keysSlots:    7   3  11   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   X   .   .   .   X   .   .   .   X   .   .
usedKeys :    X   X   X   .   .   .   .
keys     :    4   2   6   0   0   0   0
""");
        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_insertKnown()
   {          test_insertKnown(true);
              test_insertKnown(false);
   }

  static void test_delete(boolean Ex)
   {sayCurrentTestName();
    final int N = 4;
    final Slots s = new Slots(new Build().numberOfKeys(N).immediate(Ex))
     {void slotsCode()
       {final Slots s = this;
                            count().ok(0);
        insert(new Int(4)); count().ok(1);
        insert(new Int(2)); count().ok(2);
        insert(new Int(3)); count().ok(3);
        insert(new Int(1)); count().ok(4);
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  4, count:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    3   1   2   0   0   0   0   0
keysSlots:    4   1   2   0   0   0   0   0
usedSlots:    X   X   X   .   X   .   .   .
usedKeys :    X   X   X   X
keys     :    4   2   3   1
""");
        delete(new Int(2)); count().ok(3);
        delete(new Int(1)); count().ok(2);
        delete(new Int(0)); count().ok(1);
        delete(new Int(4)); count().ok(0);
        //new I() {void a() {testStop(s);}};
        check(print(), """
Slots    : size:  4, count:  0
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   0   0   0   0   0   0   0
keysSlots:    0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   .   .
usedKeys :    .   .   .   .
keys     :    0   0   0   0
""");
        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_delete()
   {          test_delete(true);
              test_delete(false);
   }

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {if (rtg( 1)) test_slots();
    if (rtg( 2)) test_locateNearestFreeSlotToKey();
    if (rtg( 3)) test_alloc();
    if (rtg( 4)) test_set_del_slot_key();
    if (rtg( 5)) test_compact();
    if (rtg( 6)) test_redistribute();
    if (rtg( 7)) test_shift();
    if (rtg( 8)) test_mergeFromRightEven();
    if (rtg( 9)) test_mergeFromLeftEven();
    if (rtg(10)) test_mergeFromRightOdd();
    if (rtg(11)) test_mergeFromLeftOdd();
    if (rtg(12)) test_find();
    if (rtg(13)) test_findRight();
    if (rtg(14)) test_insert();
    if (rtg(15)) test_insert2();
    if (rtg(16)) test_findGe();
    if (rtg(17)) test_splitRightEven();
    if (rtg(18)) test_splitLeftEven();
    if (rtg(19)) test_splitRightOdd();
    if (rtg(20)) test_splitLeftOdd();
    if (rtg(21)) test_clear();
    if (rtg(22)) test_insertKnown();
    if (rtg(23)) test_delete();
//  test_stuck();
   }

  static void newTests()                                                                                                // Tests being worked on
   {//oldTests();
    test_findRight(true);
   }

  public static void main(String[] args)                                                                                // Test if called as a program
   {testGroup = args.length > 0 ? args[0] : null;                                                                       // Test groups if supplied
    try                                                                                                                 // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {deleteAllFiles(verilogFolder, 99);                                                                                // Delete generated Verilog files created by a prior run of the current test
      if (github_actions) oldTests(); else newTests();                                                                  // Tests to run
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
