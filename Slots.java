//----------------------------------------------------------------------------------------------------------------------
// Distributed slots used to hold the key of the Btree
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
// locateNearestFreeSlotToKey - add prefernce option for low or high slot so that descending order does fewer moves

package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

class Slots extends Program                                                                                             // A tree that translates keys into values to be implemented as an application specific integrated circuit
 {final int                 numberOfKeys;                                                                               // The maximum number of references maintained by these slots
  final int                         size;                                                                               // Number of bytes needed to hold slots
  final BitSet            usedSlotsToKeys;                                                                              // The slots in use.  Thre are more slotsthan refernces os that they can be distributed with intervening empty slots to make insertions faster
  final BitSet                   usedKeys;                                                                              // The references in use.
  ByteMemory.Ref            byteMemoryRef = null;                                                                       // Byte memory reference containing the slots
  final ByteMemory.Ref     refSlotsToKeys;                                                                              // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
  final ByteMemory.Ref     refKeysToSlots;                                                                              // The slot associated with each in use key
  final ByteMemory.Ref refUsedSlotsToKeys;                                                                              // Bitset showing which slots are being used to map to keys
  final ByteMemory.Ref        refUsedKeys;                                                                              // Bitset showing which keys are in use
  final ByteMemory.Ref            refKeys;                                                                              // Keys used in btree held unordered in this array but ordered by the slot refernces rto them
  final static String           formatKey = "%3d";                                                                      // Format a key for dumping during testing
  final Build.SlotsMemoryPositions slotsMemoryPositions;                                                                // Memory layout

//D1 Construction                                                                                                       // Construct and layout the slots

  static class Build                                                                                                    // Specification of slots
   {boolean            immediate = true;                                                                                // Immediate mode
    boolean                trace = true;                                                                                // Trace execution
    int             numberOfKeys = 2;                                                                                   // Number of refernces in the slots
    ByteMemory.Ref byteMemoryRef;                                                                                       // Program memory to be used
    Program               parent;                                                                                       // Parent program if any
    SlotsMemoryPositions slotsMemoryPositions;                                                                          // Layout of memory

    Build immediate    (boolean  Immediate) {immediate     = Immediate;    return this;}
    Build numberOfKeys (int   NumberOfKeys) {numberOfKeys  = NumberOfKeys; return this;}
    Build memory       (ByteMemory.Ref Ref) {byteMemoryRef = Ref;          return this;}
    Build parent       (Program    Parent)  {parent        = Parent;       return this;}
    Build trace        (boolean     Trace)  {trace         = Trace;        return this;}

    Program.Build programBuild()                                                                                        // Create a description of the needed containing program
     {final Program.Build p = new Program.Build();                                                                      // Description of containing program
      final SlotsMemoryPositions s = slotsMemoryPositions = new SlotsMemoryPositions();                                 // Now we know the size of the slots
      if (byteMemoryRef == null) p.memory(s.size);
      if (parent        != null) p.parent(parent);
      p.immediate(immediate);
      p.trace(trace);
      return p;
     }

    int  numberOfKeys       () {return numberOfKeys;}                                                                   // The number of references in the slots definition
    int  numberOfSlotsToKeys() {return numberOfKeys()<<1;}                                                              // Number of slots from number of refs

    class SlotsMemoryPositions                                                                                          // Positions of fields in memory
     {final int N = numberOfSlotsToKeys();
      final int R = numberOfKeys();

      final BitSet.Build us = new BitSet.Build().bitSize(N).one(true).zero(true);                                       // Specification of bit set for used slots
      final BitSet.Build ur = new BitSet.Build().bitSize(R).one(true).zero(true);                                       // Specification of bit set for references

      final int posSlotsToKeys     = 0;                                                                                 // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
      final int posKeysToSlots     = posSlotsToKeys     + ib(N);                                                        // USed keys to slot referencing the key
      final int posUsedSlotsToKeys = posKeysToSlots     + ib(N);                                                        // Slots in use
      final int posUsedKeysToSlots = posUsedSlotsToKeys + ib(N);                                                        // Slots in use
      final int posusedKeys        = posUsedKeysToSlots + us.byteSize();                                                // References in use.  There are fewer references than slots to make insertions faster
      final int posKeys            = posusedKeys        + ur.byteSize();                                                // Keys used in btree held unordered in this array but ordered by the slot refernces rto them
      final int size               = posKeys            + ib(N);                                                        // Size of slots
     }
   }

  Slots(Build Build)                                                                                                    // Create the slots
   {super(Build.programBuild());
    numberOfKeys         = Build.numberOfKeys;
    slotsMemoryPositions = Build.slotsMemoryPositions;                                                                  // Size of memory used
    size                 = Build.slotsMemoryPositions.size;                                                             // Size of memory used
    byteMemoryRef        = Build.byteMemoryRef != null ? Build.byteMemoryRef : byteMemory.new Ref(0);                   // Either a reference to some memory has been supplied or create a reference to some locally allocated memory to contain the bitset
    refSlotsToKeys       = byteMemoryRef;                                                                               // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
    refKeysToSlots       = byteMemoryRef.step(slotsMemoryPositions.posKeysToSlots);                                     // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
    refUsedSlotsToKeys   = byteMemoryRef.step(slotsMemoryPositions.posUsedSlotsToKeys);                                 // Slots in use
    refUsedKeys          = byteMemoryRef.step(slotsMemoryPositions.posusedKeys);                                        // References in use.  There are fewer references than slots to make insertions faster
    refKeys              = byteMemoryRef.step(slotsMemoryPositions.posKeys);                                            // Keys used in btree held unordered in this array but ordered by the slot refernces rto them
    usedSlotsToKeys      = new BitSet        (slotsMemoryPositions.us.memory(refUsedSlotsToKeys).parent(parentProgram));// Create bitsets to reference the program and memory used by this program
    usedKeys             = new BitSet        (slotsMemoryPositions.ur.memory(refUsedKeys).parent(parentProgram));
    new I() {void action() {deleteFile(tracing);}};                                                                     // Delete the trace file here to avoid including the memory reference calculations above
    slotsCode();                                                                                                        // Generate machine code if any assembler code has been supplied
   }

  void slotsCode() {}                                                                                                   // Override this method to provide code for testing the slots

  Slots(int NumberOfKeys) {this(new Build().numberOfKeys(NumberOfKeys));}                                               // Create the slots in local memory for testing

  void putSlotToKeys(Int Index, Int Key)                                                                                // Set a slot to key reference and the corresponding back reference
   {refSlotsToKeys.putInt(Index, Key);
    refKeysToSlots.putInt(Key, Index);
    usedSlotsToKeys.set(Index, new Bool(true));
   }

  void delSlotToKeys(Int Index)                                                                                         // Delete a slot
   {final Int K = refSlotsToKeys.getInt(Index);
                  refSlotsToKeys.putInt(Index, new Int(0));
    refKeysToSlots.putInt(K,                   new Int(0));
    usedSlotsToKeys.set(Index, new Bool(false));                                               // Remove slot from bitset shoing which slots are in use
   }

  void putKey(Int Index, Int Key)                                                                                       // Set a key
   {refKeys .putInt(Index, Key);
    usedKeys.set   (Index, new Bool(true));
   }

  void delKey(Int Index)                                                                                                // Clear a key
   {refKeys .putInt(Index, new Int(0));
    usedKeys.set   (Index, new Bool(false));
   }

  Bool getSlotToKeysInUse(Int Index)       {return usedSlotsToKeys.getBit(Index);}                                      // Check whether a slot is in use
  Int  getSlotToKeyIndex (Int Index)       {return refSlotsToKeys .getInt(Index);}                                      // Index to keys from slots
  Int  getKeyToSlotIndex (Int Index)       {return refKeysToSlots .getInt(Index);}                                      // Index to slots from keys
  Bool getKeyInUse       (Int Index)       {return usedKeys       .getBit(Index);}                                      // Check whether a key is in use
  Int  getKeyValue       (Int Index)       {return refKeys        .getInt(Index);}                                      // Value of referenced key

  boolean getSlotToKeysInUse(int Index)    {return usedSlotsToKeys.getBitNC(Index);}                                    // Check whether a slot is in use
  int     getSlotToKeyIndex (int Index)    {return refSlotsToKeys .getInt(Index);}                                      // Index to keys from slot
  int     getKeyToSlotIndex (int Index)    {return refKeysToSlots .getInt(Index);}                                      // Index from slots to keys
  boolean getKeyInUse       (int Index)    {return usedKeys .getBitNC(Index);}                                          // Check whether a key is in use
  int     getKeyValue       (int Index)    {return refKeys  .getInt(Index);}                                            // Value of referenced key

  Int  getSlotToKeyValue (Int Index)       {return getKeyValue(getSlotToKeyIndex(Index));}                              // Value of a key via a specified slot
  int  getSlotToKeyValue (int Index)       {return getKeyValue(getSlotToKeyIndex(Index));}                              // Value of a key via a specified slot

  Bool empty() {return usedKeys.empty();}                                                                               // All bits in the corresponding bitset are unused so the Slots must be empty
  Bool full () {return usedKeys.full ();}                                                                               // The number of bits in the bitset slots is either equal to or greater than the number of slots so we cannot rely on them being simultaneously full

  void invalidateMemory   () {byteMemoryRef.invalidate(size);}                                                          // Invalidate the slots in such a way that they are unlikely to work well if subsequently used
  int  numberOfKeys       () {return numberOfKeys;}                                                                     // The number of references in the slots definition
  int  numberOfSlotsToKeys() {return numberOfKeys()<<1;}                                                                // Number of slots from number of refs
  int  redistributionWidth() {return (int)java.lang.Math.sqrt(numberOfKeys());}                                         // Redistribute if the next slot is further than this

  Int  locateFirstUsedSlot() {return usedSlotsToKeys.firstOne();}                                                       // First available slot
  Int  locateLastUsedSlot () {return usedSlotsToKeys.lastOne();}                                                        // Absolute position of the last slot in use

  Int locateFirstUnusedKey()                                                                                            // Absolute position of the first unused key
   {final Int p = usedKeys.firstZero();
    final Int        f = new Int(); new If (p.valid()) {void Then() {f.set(p);}}; return f;
   }

  Int stepLeft (Int Start) {return usedSlotsToKeys.prevOne(Start);}                                                     // Step left to prior occupied slot assuming that such a step is possible
  Int stepRight(Int Start) {return usedSlotsToKeys.nextOne(Start);}                                                     // Step right to the next occupied slot assuming that such a step is possible


  Int locateNearestFreeSlotToKey(Int Position, Bool Prev)                                                               // Absolute position of the nearest free slot to the indicated position if there is one. Prev will be true if the previous free slot is closest, true if the next free slot is closest, or invalid if there is no free slot
   {final Int r = new Int(0);
    Prev.invalidate();                                                                                                  // Assume no free slot will be found
    new If (getSlotToKeysInUse(Position))                                                                               // The slot is in use
     {void Then()
       {final Int p = usedSlotsToKeys.prevZero(Position);                                                               // Prev free slot
        final Int n = usedSlotsToKeys.nextZero(Position);                                                               // Next free slot
        final Bool       d = new Bool(false);                                                                           // Done when set

        new If (p.valid())                                                                                              // Previous is valid
         {void Then()
           {new If (n.valid())                                                                                          // Next is valid
             {void Then()
               {new If (Position.Sub(p).lt(n.Sub(Position)))                                                            // Favor next over previous if they are bith the same distance appart
                 {void Then()
                   {r.set(p); Prev.set(true);                                                                           // Previous is closest
                   }
                  void Else()
                   {r.set(n); Prev.set(false);                                                                          // Next is closest
                   }
                 };
               }
              void Else()
               {r.set(p); Prev.set(true);                                                                               // Previous is closest
               }
             };
           }
          void Else()                                                                                                   // Pevious is invalid
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

  void freeRef(Int Key) {usedKeys.setBit(Key, new Bool(false));}                                                        // Free a reference to one of the keys in the slots

  Bool eq(Int Index, Int Key) {return Key.eq(getKeyValue(Index));}                                                      // Check that the specified key is equal to the indexed key
  Bool le(Int Index, Int Key) {return Key.le(getKeyValue(Index));}                                                      // Check that the specified key is less than or equal to the indexed key
  Bool lt(Int Index, Int Key) {return Key.lt(getKeyValue(Index));}                                                      // Check that the specified key is less than to the indexed key
  Bool ge(Int Index, Int Key) {return Key.ge(getKeyValue(Index));}                                                      // Check that the specified key is greater than or equal to the indexed key
  Bool gt(Int Index, Int Key) {return Key.gt(getKeyValue(Index));}                                                      // Check that the specified key is greater than the the indexed key

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

  private void moveKey(Int T, Int S, Bool Continue)                                                                     // Move a key from the source poisitin to the target position
   {final Int s = refKeysToSlots.getInt(S);                                                                             // The slot referencing the key
    final Int q = getKeyValue(S);                                                                                       // The value of the key
    delSlotAndKey(s);                                                                                                   // Delete the slot and its associated key
    setSlotAndKey(s, T, q);                                                                                             // Reinsert the key
    Continue.set(true);                                                                                                 // Continue moving keys
   }

//D2 Compact and Merge                                                                                                  // Compact and merge slots

//D3 Compact                                                                                                            // Compact slots to the left or right

  void compactLeft()                                                                                                    // Compact the used slots to the left end
   {final Slots slots = this;
    new If (empty())                                                                                                    // Compact slots
     {void Then() {}                                                                                                    // Nothing to compact as empty
      void Else()                                                                                                       // Compact slots first
       {new For(numberOfKeys())                                                                                         // No need to make any more than this number of moves
         {void body(Int Index, Bool Continue)
           {final Int s = usedSlotsToKeys.firstZero();                                                           // First empty slot
            final Int S = usedSlotsToKeys.nextOne(s);                                                            // Next used slot beyond first empty slot
            moveSlot(s, S, Continue);
           }
         };
       }
     };

    new If (usedKeys.empty())                                                                                           // Compact keys
     {void Then() {}                                                                                                    // Nothing to compact as empty
      void Else()
       {new If (full())                                                                                                 // Keys cannot be compacted as they are full
         {void Then() {}
          void Else()
           {new For(numberOfKeys())                                                                                     // No need to make any more than this number of moves
             {void body(Int Index, Bool Continue)
               {final Int k = usedKeys .firstZero();                                                             // First empty key
                final Int K = usedKeys .lastOne();                                                               // Last used key so we get the longest possible move
                new If (K.gt(k))                                                                                        // Compaction possible
                 {void Then()
                   {moveKey(k, K, Continue);
                   }
                 };
               }
             };
           }
         };
       }
     };
   }

  void compactRight()                                                                                                   // Compact the used slots to the right end
   {final Slots slots = this;
    new If (empty())                                                                                                    // Compact slots
     {void Then() {}                                                                                                    // Nothing to compact as empty
      void Else()                                                                                                       // Compact slots first
       {new For(numberOfKeys())                                                                                         // No need to make any more than this number of moves
         {void body(Int Index, Bool Continue)
           {final Int s = usedSlotsToKeys.lastZero();                                                            // Last empty slot
            final Int S = usedSlotsToKeys.prevOne(s);                                                            // Previously used slot beyond last empty one
            moveSlot(s, S, Continue);
           }
         };
       }
     };

    new If (usedKeys.empty())                                                                                           // Compact keys
     {void Then() {}                                                                                                    // Nothing to compact as empty
      void Else()
       {new If (full())                                                                                                 // Keys cannot be compacted as they are full
         {void Then() {}
          void Else()
           {new For(numberOfKeys())                                                                                     // No need to make any more than this number of moves
             {void body(Int Index, Bool Continue)
               {final Int k = usedKeys .lastZero();                                                              // Last empty key
                final Int K = usedKeys .firstOne();                                                              // First used key so we get the longest possible move
                new If (K.lt(k))                                                                                        // Compaction possible
                 {void Then()
                   {moveKey(k, K, Continue);
                   }
                 };
               }
             };
           }
         };
       }
     };
   }

  void redistribute()                                                                                                   // Redistribute the unused slots evenly with a slight bias to having a free slot at the end to assist with data previously sorted into ascending order.
   {final Slots slots = this;
    new If (empty())                                                                                                    // Something to redistribute
     {void Then() {}                                                                                                    // Nothing to redistrinute as the slots are empty
      void Else()                                                                                                       // Redistribute
       {final Int         N = new Int(numberOfSlotsToKeys());                                                           // Maximum number of slots
        final Int         R = new Int(numberOfKeys());                                                                  // Maximum number of keys
        compactLeft();                                                                                                  // Compact everything to the left so it is in a known position
        final Int  c = usedKeys.firstZero();                                                                     // Number of slots in use
        final Int     space = N.Sub(c).div(c);                                                                          // Space between used slots
        final Int     cover = space.Inc().mul(c.Dec()).inc();                                                           // Covered space from first used slot to last used slot,
        final Int remainder = N.Sub(cover);                                                                             // Uncovered remainder
        final Int         p = remainder.Down();                                                                         // Start position for first used slot giving any over to end to bias slighlty in favor of preseorted data
        new ForCount(c)                                                                                                 // Redistribute used slots
         {void body(Int Index)                                                                                          // Initialize background of slots
           {final Int s = c.Dec().sub(Index);                                                                           // Index of source element to be moved
            final Int t = p.Add(s.Mul(space)).add(s);                                                                   // Index in slots of target element to be set
            final Int k = getSlotToKeyIndex(s);                                                                         // Index of key being moved
            final Int K = getKeyValue(k);                                                                               // Value of key being moved
            delSlotAndKey(s);
            setSlotAndKey(t, k, K);
           }
         };
       }
     };
   }

//D2 Merge                                                                                                              // Merge slots

  private void moveSlot(Int T, Int S)                                                                                   // Move a slot from the specified source position to the specified target position
   {final Int k = getSlotToKeyIndex(S);                                                                                 // Index of key being moved
    final Int K = getKeyValue(k);                                                                                       // Value of key being moved
    delSlotAndKey(S);                                                                                                   // Remove source
    setSlotAndKey(T, k, K);                                                                                             // Reinsert source at target
   }

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

  Bool mergeFromRight(Slots Right)                                                                                      // Merge the specified slots from the right
   {final Int N  = new Int(numberOfSlotsToKeys());
    final Int lc =       usedKeys.countOnes();
    final Int rc = Right.usedKeys.countOnes();
    final Bool r = new Bool(false);
    final Slots left = this;
    new If (lc.Add(rc).le(new Int(numberOfKeys())))                                                                     // Can only merge if the result can fit in one set of slots
     {void Then()
       {      compactLeft ();
        Right.compactRight();
        r.set(true);
        new ForCount (N.Sub(rc), N)
         {void body(Int Index)
           {final Int k = Right.getSlotToKeyIndex(Index);                                                               // Index of key being moved
            final Int K = Right.getKeyValue(k);                                                                         // Value of key being moved
            setSlotAndKey(Index, k, K);                                                                                 // Reinsert source at target
           }
         };
       }
     };
    return r;
   }

  Bool mergeFromLeft(Slots Left)                                                                                        // Merge the specified slots from the right
   {final Int N  = new Int(numberOfSlotsToKeys());
    final Int rc =      usedKeys.countOnes();
    final Int lc = Left.usedKeys.countOnes();
    final Bool r = new Bool(false);
    final Slots right = this;
    new If (lc.Add(rc).le(new Int(numberOfKeys())))                                                                     // Can only merge if the result can fit in one set of slots
     {void Then()
       {Left.compactLeft  ();
             compactRight();
        r.set(true);
        new ForCount (lc)
         {void body(Int Index)
           {final Int k = Left.getSlotToKeyIndex(Index);                                                                // Index of key being moved
            final Int K = Left.getKeyValue(k);                                                                          // Value of key being moved
            setSlotAndKey(Index, k, K);                                                                                 // Reinsert source at target
           }
         };
       }
     };
    return r;
   }

//D2 High level operations                                                                                              // Find, insert, delete values in the slots

  class Find                                                                                                            // Find result
   {final Int slot = new Int() ;                                                                                                    // Slot found
    final Bool lower = new Bool(), higher = new Bool(), equal = new Bool(), empty = new Bool();                                                                          // Position of search item relative to the slot found

    void set(Int Slot, boolean Lower, boolean Higher)
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

    new If (empty())                                                                                                    // Nothing to find if all the slots are eopty
     {void Then()
       {f.set(new Int(0), false, false);                                                                                // Empty
       }
      void Else()
       {Int p = u.top();
        Int l = u.low (p);
        Int r = u.high(p);
        Int L = getSlotToKeyValue(l);
        Int R = getSlotToKeyValue(r);

        new If (Key.eq(L))
         {void Then()                                                                                                   // Equal to low bound
           {f.set(l, true, true);
           }
          void Else()
           {new If (Key.eq(R))                                                                                          // Equal to high bound
             {void Then()
               {f.set(r, true, true);
               }
              void Else()
               {new For(new Int(u.logBitSize-1))                                                                        // Step down through ones tree narrowing the search range as we go
                 {void body(Int Index, Bool Continue)
                   {new If (Key.lt(L))
                     {void Then()
                       {f.set(l, true, false);                                                                          // Lower than the left hand side
                       }
                      void Else()
                       {new If (Key.gt(R))
                         {void Then()
                           {f.set(r, false, true);                                                                      // Higher than the right hand side
                           }
                          void Else()
                           {new If (u.canGoLeft(p))                                                                     // Go left
                             {void Then()
                               {final Int lp = u.nextDownLow     (p);                                                   // Upper end of left range
                                final Int lr = u.high            (lp);
                                final Int lR = getSlotToKeyValue(lr);
                                new If (Key.eq(lR))                                                                     // Found at upper end of range
                                 {void Then()
                                   {f.set(lr, true, true);
                                   }
                                  void Else()
                                   {new If (Key.lt(lR))                                                                 // Lower than upper bound
                                     {void Then()
                                       {new If(r.ne(lr))                                                                // New upper bound
                                         {void Then()
                                          {p.set(lp); r.set(lr); R.set(lR);
                                           Continue.set();                                                              // Continue the search
                                          }
                                         void Else()                                                                    // Same upper bound so search has finished
                                          {f.set(lr, true, false);
                                          }
                                        };
                                       }
                                      void Else()
                                       {new If (u.canGoRight(p))                                                        // Greater than so perhaps part of right hand range
                                         {void Then()
                                           {final Int rp = u.nextDownHigh   (p);                                        // Low end of right range
                                            final Int rl = u.low            (rp);
                                            final Int rL = getSlotToKeyValue(rl);
                                            new If (Key.eq(rL))                                                         // Equal to lower bound on right
                                             {void Then()
                                               {f.set(rl, true, true);                                                  // Found
                                               }
                                              void Else()
                                               {new If (Key.lt(rL))
                                                 {void Then()
                                                   {f.set(rl, true, false);                                             // Not found and less than low end of right
                                                   }
                                                  void Else()
                                                   {new If (l.ne(rl))                                                   // New lower bound
                                                     {void Then()
                                                       {p.set(rp); l.set(rl); L.set(rL);                                // Some where in the right hand range of which we already know the upper limits
                                                        Continue.set();                                                 // Continue the search
                                                       }
                                                      void Else()                                                       // Same lower bound so search has finished
                                                       {f.set(rl, false, true);
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
                                 };
                               }
                              void Else()
                               {new If (u.canGoRight(p))                                                                // Could not go left so must have gone right
                                 {void Then()
                                   {p.set(u.nextDownHigh(p));
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
               }
             };
           }
         };
       }
     };
    return f;
   }

  void insert(Int Key)                                                                                                  // Insert a key into the slots
   {if (immediate() && usedKeys.full().b()) stop("No more space to insert key:", Key);                                  // No space left
    new If (empty())                                                                                                    // Slots are empty so insert immediately in the middle
     {void Then()
       {setSlotAndKey(new Int(numberOfKeys), new Int(0), Key);                                                          // Insert immediately in the center
       }
      void Else()                                                                                                       // Insert into a free slot while maintaining the order of the slots
       {final Int  K = usedKeys.firstZero();                                                                            // Position for key in key slots
        final Find f = find(Key);                                                                                       // Find nearest existing key in slots
        final Int  s = new Int(f.slot);                                                                                 // Nearest existing key slot
        final Bool d = new Bool();                                                                                      // Nearest free slot is below - true or above - false relative to the nearest existing key
        final Int  p = locateNearestFreeSlotToKey(s, d);                                                                // Absolute position of nearest free slot

        new If (d)                                                                                                      // Free slot is lower than nearest found key slot
         {void Then()
           {new If (f.lower)                                                                                            // Insert key lower than nearest found key slot
             {void Then()
               {new If (s.Sub(p).eq(1))                                                                                 // Previous slot is free so no movement required
                 {void Then() {setSlotAndKey(s.Dec(), K, Key);}                                                         // Insert key immediately below nearest found key slot in an already empty slot
                  void Else()                                                                                           // Previous free slot has intervening occupied slots
                   {shiftDownOne (p.Inc(), s.Sub(p).Dec());                                                              // Shift block starting one slot above lower free slot and ending one slot below nearest found key slot
                    setSlotAndKey(s.Dec(), K, Key);                                                                     // Insert key immediately below nearest found key slot in a slot freed by moving the previous block down one step
                   }
                 };
               }
              void Else()                                                                                               // Insert above nearest found key slot
               {shiftDownOne(p.Inc(), s.Sub(p));                                                                        // Shift block one slot up from nearest lower free slot and the nearest found key slot down one step
                setSlotAndKey(s, K, Key);                                                                               // Insert key in nearest found key slot
               }
             };
           }
          void Else()                                                                                                   // Nearest free slot is higher than nearest found key slot
           {new If (f.higher)                                                                                           // Insert higher than nearest found key slot
             {void Then()
               {new If (p.Sub(s).eq(1))                                                                                 // Next slot is free so no movement required
                 {void Then() {setSlotAndKey(p, K, Key);}                                                               // Insert key immediately above nearest found key slot in an already empty slot
                  void Else()                                                                                           // Next free slot has intervening occupied slots
                   {shiftUpOne   (s.Inc(), p.Sub(s).Dec());                                                             // Shift block above nearest found key slot
                    setSlotAndKey(s.Inc(), K, Key);                                                                     // Insert key immediately above nearest found key slot in a slot freed by moving the block anove up one step
                   }
                 };
               }
              void Else()                                                                                               // Insert into nearest found key slot after shifting it and the following block up one step
               {shiftUpOne   (s, p.Sub(s));                                                                             // Shift nearest found key and its following neighbors up one step
                setSlotAndKey(s, K, Key);                                                                               // Insert key in nearest found key slot
               }
             };
           }
         };
       }
     };
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
        final Bool E = usedSlotsToKeys.empty(); ok(()->E, false);
        final Bool F = usedSlotsToKeys.full (); ok(()->F, false);
        putSlotToKeys(new Int(0), new Int(1));

        final Int fs = locateFirstUsedSlot(); ok(()->fs, 0);
        final Int ls = locateLastUsedSlot (); ok(()->ls, 2);

        putKey (new Int(1), new Int(11));
        putKey (new Int(3), new Int(22));

        final Slots s = this;
        //new I() {void action() {stop(s);}};
        ok(()->this, """
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
        //new I() {void action() {stop(s);}};
        ok(()->this, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    1   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0
keysSlots:    0   0   0   0   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    X   .   .   .   .   .   .   .   .   .   .   .   .   .   .   .
usedKeys :    .   X   .   .   .   .   .   .
keys     :    0  11   0   0   0   0   0   0
""");
        for (int i = 0, N = numberOfKeys(); i < N; i++) putKey (new Int(i), new Int(i+1));
        final Bool re = usedKeys.empty(); ok(()->re, false);
        final Bool rf = usedKeys.full (); ok(()->rf, true);

        delKey(new Int(3)); final Int k1 = locateFirstUnusedKey(); ok(()->k1, 3);
        delKey(new Int(4)); final Int k2 = locateFirstUnusedKey(); ok(()->k2, 3);
        delKey(new Int(2)); final Int k3 = locateFirstUnusedKey(); ok(()->k3, 2);

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
        //new I() {void action() {stop(s);}};
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
        final Int  q0 = locateNearestFreeSlotToKey(new Int( 0), P); ok(()-> q0,  0); final Bool  p0 = P.valid(); ok(()-> p0, false);
        final Int  q1 = locateNearestFreeSlotToKey(new Int( 1), P); ok(()-> q1,  0); final Bool  p1 = P.valid(); ok(()-> p1, false);
        final Int  q2 = locateNearestFreeSlotToKey(new Int( 2), P); ok(()-> q2,  3);                             ok(()-> P,  false);
        final Int  q3 = locateNearestFreeSlotToKey(new Int( 3), P); ok(()-> q3,  0); final Bool  p3 = P.valid(); ok(()-> p3, false);
        final Int  q4 = locateNearestFreeSlotToKey(new Int( 4), P); ok(()-> q4,  3);                             ok(()-> P,  true);
        final Int  q5 = locateNearestFreeSlotToKey(new Int( 5), P); ok(()-> q5,  7);                             ok(()-> P,  false);
        final Int  q6 = locateNearestFreeSlotToKey(new Int( 6), P); ok(()-> q6,  7);                             ok(()-> P,  false);
        final Int  q7 = locateNearestFreeSlotToKey(new Int( 7), P); ok(()-> q7,  0); final Bool  p7 = P.valid(); ok(()-> p7, false);
        final Int  q8 = locateNearestFreeSlotToKey(new Int( 8), P); ok(()-> q8,  0); final Bool  p8 = P.valid(); ok(()-> p8, false);
        final Int  q9 = locateNearestFreeSlotToKey(new Int( 9), P); ok(()-> q9,  8);                             ok(()-> P,  true);
        final Int q10 = locateNearestFreeSlotToKey(new Int(10), P); ok(()->q10, 11);                             ok(()-> P,  false);
        final Int q11 = locateNearestFreeSlotToKey(new Int(11), P); ok(()->q11,  0); final Bool p11 = P.valid(); ok(()->p11, false);
        final Int q12 = locateNearestFreeSlotToKey(new Int(12), P); ok(()->q12, 13);                             ok(()-> P,  false);
        final Int q13 = locateNearestFreeSlotToKey(new Int(13), P); ok(()->q13,  0); final Bool p13 = P.valid(); ok(()->p13, false);

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
        //new I() {void action() {stop(s);}};
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
        //new I() {void action() {stop(s);}};
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
        //new I() {void action() {stop(s);}};
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
        //new I() {void action() {stop(s);}};
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

  static void test_compactLeft(boolean Ex)
   {final Slots s = new Slots(4)
     {void slotsCode()
       {setSlotAndKey(new Int(2),  new Int(1),  new Int(1));
        setSlotAndKey(new Int(4),  new Int(3),  new Int(2));
        final Slots s = this;
        //new I() {void action() {stop(s);}};
        ok(()->this, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   0   1   0   3   0   0   0
keysSlots:    0   2   0   4   0   0   0   0
usedSlots:    .   .   X   .   X   .   .   .
usedKeys :    .   X   .   X
keys     :    0   1   0   2
""");

        compactLeft();
        //new I() {void action() {stop(s);}};
        ok(()->this, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    1   0   0   0   0   0   0   0
keysSlots:    1   0   0   0   0   0   0   0
usedSlots:    X   X   .   .   .   .   .   .
usedKeys :    X   X   .   .
keys     :    2   1   0   0
""");

        execute();
       }
     };
   }

  static void test_compactLeft()
   {test_compactLeft(true);
    test_compactLeft(false);
   }

  static void test_compactRight(boolean Ex)
   {final Slots s = new Slots(4)
     {void slotsCode()
       {setSlotAndKey(new Int(2),  new Int(1),  new Int(1));
        setSlotAndKey(new Int(4),  new Int(3),  new Int(2));
        final Slots s = this;
        //new I() {void action() {stop(s);}};
        ok(()->this, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   0   1   0   3   0   0   0
keysSlots:    0   2   0   4   0   0   0   0
usedSlots:    .   .   X   .   X   .   .   .
usedKeys :    .   X   .   X
keys     :    0   1   0   2
""");

        compactRight();
        //new I() {void action() {stop(s);}};
        ok(()->this, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   0   0   0   0   0   2   3
keysSlots:    0   0   6   7   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X
usedKeys :    .   .   X   X
keys     :    0   0   1   2
""");

        compactLeft();
        //new I() {void action() {stop(s);}};
        ok(()->this, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    1   0   0   0   0   0   0   0
keysSlots:    1   0   0   0   0   0   0   0
usedSlots:    X   X   .   .   .   .   .   .
usedKeys :    X   X   .   .
keys     :    2   1   0   0
""");

        execute();
       }
     };
   }

  static void test_compactRight()
   {test_compactRight(true);
    test_compactRight(false);
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
        //new I() {void action() {stop(s);}};
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
        //new I() {void action() {stop(s);}};
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
        //new I() {void action() {stop(s);}};
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
        //new I() {void action() {stop(s);}};
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
        //new I() {void action() {stop(s);}};
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
        //new I() {void action() {stop(s);}};
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

  static void test_mergeFromRight(boolean Ex)
   {final int N = 4;
    final Slots s = new Slots(new Build().numberOfKeys(N).immediate(Ex))
     {void slotsCode()
       {final Slots l = this;
        setSlotAndKey(new Int(2), new Int(1), new Int(2));
        setSlotAndKey(new Int(4), new Int(3), new Int(1));
        final Slots r = new Slots(new Build().numberOfKeys(N).immediate(Ex).parent(l))
         {void slotsCode()
           {setSlotAndKey(new Int(2), new Int(1), new Int(3));
            setSlotAndKey(new Int(4), new Int(3), new Int(4));
           }
         };
        mergeFromRight(r);
        ok(()->l, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    1   0   0   0   0   0   2   3
keysSlots:    1   0   6   7   0   0   0   0
usedSlots:    X   X   .   .   .   .   X   X
usedKeys :    X   X   X   X
keys     :    1   2   3   4
""");
        ok(()->r, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    0   0   0   0   0   0   2   3
keysSlots:    0   0   6   7   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X
usedKeys :    .   .   X   X
keys     :    0   0   3   4
""");
        maxSteps = 99999;
        execute();
       }
     };
   }

  static void test_mergeFromRight()
   {test_mergeFromRight(true); test_mergeFromRight(false);
   }

  static void test_mergeFromLeft(boolean Ex)
   {final int N = 4;
    final Slots s = new Slots(new Build().numberOfKeys(N).immediate(Ex))
     {void slotsCode()
       {final Slots r = this;
        setSlotAndKey(new Int(2), new Int(1), new Int(3));
        setSlotAndKey(new Int(4), new Int(3), new Int(4));
        final Slots l = new Slots(new Build().numberOfKeys(N).immediate(Ex).parent(r))
         {void slotsCode()
           {setSlotAndKey(new Int(2), new Int(1), new Int(2));
            setSlotAndKey(new Int(4), new Int(3), new Int(1));
           }
         };
        mergeFromLeft(l);
        //new I() {void action() {stop(l);}};
        ok(()->l, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    1   0   0   0   0   0   0   0
keysSlots:    1   0   0   0   0   0   0   0
usedSlots:    X   X   .   .   .   .   .   .
usedKeys :    X   X   .   .
keys     :    1   2   0   0
""");
        //new I() {void action() {stop(r);}};
        ok(()->r, """
Slots    : refs:  4
positions:    0   1   2   3   4   5   6   7
slotsKeys:    1   0   0   0   0   0   2   3
keysSlots:    1   0   6   7   0   0   0   0
usedSlots:    X   X   .   .   .   .   X   X
usedKeys :    X   X   X   X
keys     :    1   2   3   4
""");
        maxSteps = 99999;
        execute();
       }
     };
   }

  static void test_mergeFromLeft()
   {test_mergeFromLeft(true); test_mergeFromLeft(false);
   }

  static void test_find()
   {final Slots s = new Slots(new Build().numberOfKeys(8))
     {void slotsCode()
       {putSlotToKeys(new Int( 0), new Int(1));
        putSlotToKeys(new Int( 2), new Int(3));
        putSlotToKeys(new Int( 4), new Int(5));
        putSlotToKeys(new Int(15), new Int(0));
        putKey       (new Int( 1), new Int(11));
        putKey       (new Int( 3), new Int(22));
        putKey       (new Int( 5), new Int(33));
        putKey       (new Int( 0), new Int(44));

        final Slots s = this;
        //new I() {void action() {stop(s);}};
        ok(()->this, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    1   0   3   0   5   0   0   0   0   0   0   0   0   0   0   0
keysSlots:   15   0   0   2   0   4   0   0   0   0   0   0   0   0   0   0
usedSlots:    X   .   X   .   X   .   .   .   .   .   .   .   .   .   .   X
usedKeys :    X   X   .   X   .   X   .   .
keys     :   44  11   0  22   0  33   0   0
""");

       ok(getSlotToKeyValue(0), 11);
       ok(getSlotToKeyValue(2), 22);
       ok(getSlotToKeyValue(4), 33);
       ok(getSlotToKeyValue(8), 44);

       //new I() {void action() {stop(s.usedSlotsToKeys);}};
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
        Find f = find(new Int(45));
        //stop(f);
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
  static void test_findRight()
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
        //new I() {void action() {stop(s);}};
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

       //new I() {void action() {stop(s.usedSlotsToKeys);}};
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
        Find f = find(new Int(45));
        //stop(f);
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
        debug = true;
        insert(new Int(15));
        insert(new Int(18));
        insert(new Int(17));
        insert(new Int(12));
        insert(new Int(11));
        final Slots s = this;
        //new I() {void action() {stop(s);}};
        ok(()->this, """
Slots    : refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slotsKeys:    0   0   0   0   0   0   7   6   1   0   3   2   5   4   0   0
keysSlots:    9   8  11  10  13  12   7   6   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X   X   X   X   X   X   X   .   .
usedKeys :    X   X   X   X   X   X   X   X
keys     :   14  13  16  15  18  17  12  11
""");
        execute();
       }
     };
   }

  static void test_insert()
   {test_insert(true);
    //test_insert(false);
   }

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_slots();
    test_locateNearestFreeSlotToKey();
    test_alloc();
    test_set_del_slot_key();
    test_compactLeft();
    test_compactRight();
    test_redistribute();
    test_shift();
    test_slots();
    test_mergeFromRight();
    test_mergeFromLeft();
    test_find();
    test_findRight();
    test_insert();
   }

  static void newTests()                                                                                                // Tests being worked on
   {oldTests();
    test_insert();
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
