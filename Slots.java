//----------------------------------------------------------------------------------------------------------------------
// Distributed slots used to hold the key of the Btree
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

class Slots extends Program                                                                                             // A tree that translates keys into values to be implemented as an application specific integrated circuit
 {final int numberOfKeys;                                                                                               // The maximum number of references maintained by these slots
  final int size;                                                                                                       // Number of bytes needed to hold slots
  final BitSet usedSlotsToKeys;                                                                                         // The slots in use.  Thre are more slotsthan refernces os that they can be distributed with intervening empty slots to make insertions faster,
  final BitSet usedKeys;                                                                                                // The references in use.
  ByteMemory.Ref byteMemoryRef = null;                                                                                  // Byte memory reference containing the slots
  final SlotsMemoryPositions slotsMemoryPositions;                                                                      // Memory layout
  final ByteMemory.Ref refSlotsToKeys;                                                                                  // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
  final ByteMemory.Ref refKeysToSlots;                                                                                  // The slot associated with each in use key
  final ByteMemory.Ref refUsedSlotsToKeys;                                                                              // Bitset showing which slots are being used to map to keys
  final ByteMemory.Ref refUsedKeys;                                                                                     // Bitset showing which keys are in use
  final ByteMemory.Ref refKeys;                                                                                         // Keys used in btree held unordered in this array but ordered by the slot refernces rto them
  final static String  formatKey = "%3d";                                                                               // Format a key for dumping during testing

//D1 Construction                                                                                                       // Construct and layout the slots

  static class Build                                                                                                    // Specification of slots
   {int numberOfKeys = 2;                                                                                               // Number of refernces in the slots
    Program.ByteMemory.Ref memoryRef = null;                                                                            // Program memory to be used

    Build numberOfKeys (int           NumberOfKeys) {numberOfKeys = NumberOfKeys; return this;}
    Build memory       (Program.ByteMemory.Ref Ref) {memoryRef    = Ref;          return this;}
   }

  Slots(Build Build)                                                                                                    // Create the slots
   {numberOfKeys         = Build.numberOfKeys;
    slotsMemoryPositions = new SlotsMemoryPositions();                                                                  // Memory layout
    size                 = slotsMemoryPositions.size;
    if (Build.memoryRef != null) byteMemoryRef = Build.memoryRef;                                                       // Use supplied memeory
    else
     {byteMemory         = new ByteMemory(size);                                                                        // Use local memory as no global memeory supplied
      byteMemoryRef      = byteMemory.new Ref(0);                                                                       // Reference to local memory
     }
    refSlotsToKeys       = byteMemoryRef;                                                                               // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
    refKeysToSlots       = byteMemoryRef.step(slotsMemoryPositions.posKeysToSlots);                                     // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
    refUsedSlotsToKeys   = byteMemoryRef.step(slotsMemoryPositions.posUsedSlotsToKeys);                                // Slots in use
    refUsedKeys          = byteMemoryRef.step(slotsMemoryPositions.posusedKeys);                                        // References in use.  There are fewer references than slots to make insertions faster
    refKeys              = byteMemoryRef.step(slotsMemoryPositions.posKeys);                                            // Keys used in btree held unordered in this array but ordered by the slot refernces rto them
    usedSlotsToKeys      = new BitSet(slotsMemoryPositions.us.memory(refUsedSlotsToKeys));                              // Create bitsets to reference the program and memory used by this program
    usedKeys             = new BitSet(slotsMemoryPositions.ur.memory(refUsedKeys));
    usedSlotsToKeys.initialize();                                                                                       // Initialize the bitsets
    usedKeys .initialize();
    slotsCode();                                                                                                        // Generate code if any coide has been supplied
   }

  void slotsCode() {}                                                                                                   // Override this method to provide code for testing the slots

  Slots(int NumberOfKeys) {this(new Build().numberOfKeys(NumberOfKeys));}                                               // Create the slots in local memory for testing

  void putSlotToKeys(Int Index, Int Key)                                                                                // Set a slot to key reference and the corresponding back reference
   {refSlotsToKeys.putInt(Index, Key);
    refKeysToSlots.putInt(Key, Index);
    usedSlotsToKeys.set(usedSlotsToKeys.new Pos(Index), new Bool(true));
   }

  void delSlotToKeys(Int Index)                                                                                         // Delete a slot
   {final Int K = refSlotsToKeys.getInt(Index);
                  refSlotsToKeys.putInt(Index, new Int(0));
    refKeysToSlots.putInt(K,                   new Int(0));
    usedSlotsToKeys.set(usedSlotsToKeys.new Pos(Index), new Bool(false));                                               // Remove slot from bitset shoing which slots are in use
   }

  void putKey(Int Index, Int Key)                                                                                       // Set a key
   {refKeys .putInt(Index, Key);
    usedKeys.set(usedKeys.new Pos(Index), new Bool(true));
   }

  void delKey(Int Index)                                                                                                // Clear a key
   {refKeys .putInt(Index, new Int(0));
    usedKeys.set(usedKeys.new Pos(Index), new Bool(false));
   }

  Bool getSlotToKeysInUse(Int Index)       {return usedSlotsToKeys.getBit(usedSlotsToKeys   .new Pos(Index));}          // Check whether a slot is in use
  Int  getSlotToKeyValue(Int Index)        {return refSlotsToKeys .getInt(                     Index );}                // Index to keys from slots
  Int  getKeyToSlotValue(Int Index)        {return refKeysToSlots .getInt(                     Index );}                // Index to slots from keys
  Bool getKeyInUse (Int Index)             {return usedKeys .getBit(usedKeys    .new Pos(Index));}                      // Check whether a key is in use
  Int  getKeyValue (Int Index)             {return refKeys  .getInt(                     Index );}                      // Value of referenced key

  boolean getSlotToKeysInUse(int Index)    {return usedSlotsToKeys.getBitNC(Index);}                                    // Check whether a slot is in use
  int     getSlotToKeyValue (int Index)    {return refSlotsToKeys .getInt(Index);}                                      // Index to keys from slot
  int     getKeyToSlotValue (int Index)    {return refKeysToSlots .getInt(Index);}                                      // Index from slots to keys
  boolean getKeyInUse       (int Index)    {return usedKeys .getBitNC(Index);}                                          // Check whether a key is in use
  int     getKeyValue       (int Index)    {return refKeys  .getInt(Index);}                                            // Value of referenced key

  Bool empty() {return usedKeys.empty();}                                                                               // All bits in the corresponding bitset are unused so the Slots must be empty
  Bool full () {return usedKeys.full ();}                                                                               // The number of bits in the bitset slots is either equal to or greater than the number of slots so we cannot rely on them being simultaneously full

  void invalidateMemory   () {byteMemoryRef.invalidate(size);}                                                          // Invalidate the slots in such a way that they are unlikely to work well if subsequently used
  int  numberOfKeys       () {return numberOfKeys;}                                                                     // The number of references in the slots definition
  int  numberOfSlotsToKeys() {return numberOfKeys()<<1;}                                                                // Number of slots from number of refs
  int  redistributionWidth() {return (int)java.lang.Math.sqrt(numberOfKeys());}                                         // Redistribute if the next slot is further than this

  Int  locateFirstUsedSlot() {return usedSlotsToKeys.firstOne().position();}                                            // First available slot
  Int  locateLastUsedSlot () {return usedSlotsToKeys.lastOne().position();}                                             // Absolute position of the last slot in use

  Int locateFirstUnusedKey()                                                                                            // Absolute position of the first unused key
   {final BitSet.Pos p = usedKeys.firstZero();
    final Int        f = new Int(); new If (p.valid()) {void Then() {f.set(p);}}; return f;
   }

  Int stepLeft(Int Start)                                                                                               // Step left to prior occupied slot assuming that such a step is possible
   {final BitSet.Pos q = usedSlotsToKeys.new Pos(Start);
    return usedSlotsToKeys.prevOne(q).position();
   }

  Int stepRight(Int Start)                                                                                              // Step right to the next occupied slot assuming that such a step is possible
   {final BitSet.Pos q = usedSlotsToKeys.new Pos(Start);
    return usedSlotsToKeys.nextOne(q).position();
   }

  Int locateNearestFreeSlotToKey(Int Position, Bool Prev)                                                               // Relative position of the nearest free slot to the indicated position if there is one. Prev will be true if the previos free slot is closest, true if the next free slot is closest, or invalid if there is no free slot
   {final Int r = new Int(0);
    Prev.invalidate();                                                                                                  // Assume no free slot will be found
    new If (getSlotToKeysInUse(Position))                                                                               // The slot is in use
     {void Then()
       {final BitSet.Pos p = usedSlotsToKeys.prevZero(usedSlotsToKeys.new Pos(Position));                               // Prev free slot
        final BitSet.Pos n = usedSlotsToKeys.nextZero(usedSlotsToKeys.new Pos(Position));                               // Next free slot
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
                new I() {void action() {say("CCCC");}};
               }
             };
           }
          void Else()                                                                                                   // Pevious is invalid
           {new If (n.valid())                                                                                          // Next is valid
             {void Then()
               {r.set(n); Prev.set(false);                                                                              // Next is closest
                new I() {void action() {say("DDDDD");}};
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
       {usedKeys.set(usedKeys.new Pos(I), new Bool(true));
       }
      void Else()                                                                                                       // No more keys slots available
       {new I() {void action() {stop("No more slots available in this set of slots");}};
       }
     };
    return I;
   }

  void freeRef(Int Key) {usedKeys.setBit(usedKeys.new Pos(Key), new Bool(false));}                                      // Free a reference to one of the keys in the slots

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
       {delKey(getSlotToKeyValue(P)); delSlotToKeys(P);
       }
      void Else() {new I() {void action() {stop("Slot not in use:", P);}};}                                             // Slot not occupied
     };
   }

  private void moveSlot(BitSet.Pos s, BitSet.Pos S, Bool Continue)                                                      // Move a slot
   {new If (S.valid())
     {void Then()
       {final Int q = getSlotToKeyValue(S);
        delSlotToKeys(S);
        putSlotToKeys(s, q);
        Continue.set(true);                                                                                             // Continue moving slots
       }
     };
   }

  private void moveKey(BitSet.Pos k, BitSet.Pos K, Bool Continue)                                                       // Move a key
   {final Int s = refKeysToSlots.getInt(K);                                                                             // The slot referencing the key
    final Int q = getKeyValue(K);                                                                                       // The value of the key
    delSlotAndKey(s);                                                                                                   // Delete the slot and its associated key
    setSlotAndKey(s, k, q);                                                                                             // Reinsert the key
    Continue.set(true);                                                                                                 // Continue moving keys
   }

  void compactLeft()                                                                                                    // Compact the used slots to the left end
   {final Slots slots = this;
    new If (usedKeys.empty())                                                                                           // Compact slots
     {void Then() {}                                                                                                    // Nothing to compact as empty
      void Else()                                                                                                       // Compact slots first
       {new For(numberOfKeys())                                                                                         // No need to make any more than this number of moves
         {void body(Int Index, Bool Continue)
           {final BitSet.Pos s = usedSlotsToKeys.firstZero();                                                           // First empty slot
            final BitSet.Pos S = usedSlotsToKeys.nextOne(s);                                                            // Next used slot beyond first empty slot
            moveSlot(s, S, Continue);
           }
         };
       }
     };

    new If (usedKeys.empty())                                                                                           // Compact keys
     {void Then() {}                                                                                                    // Nothing to compact as empty
      void Else()
       {new If (usedKeys.full())                                                                                        // Keys cannot be compacted as they are full
         {void Then() {}
          void Else()
           {new For(numberOfKeys())                                                                                     // No need to make any more than this number of moves
             {void body(Int Index, Bool Continue)
               {final BitSet.Pos k = usedKeys .firstZero();                                                             // First empty key
                final BitSet.Pos K = usedKeys .lastOne();                                                               // Last used key so we get the longest possible move
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
    new If (usedKeys.empty())                                                                                           // Compact slots
     {void Then() {}                                                                                                    // Nothing to compact as empty
      void Else()                                                                                                       // Compact slots first
       {new For(numberOfKeys())                                                                                         // No need to make any more than this number of moves
         {void body(Int Index, Bool Continue)
           {final BitSet.Pos s = usedSlotsToKeys.lastZero();                                                            // Last empty slot
            final BitSet.Pos S = usedSlotsToKeys.prevOne(s);                                                            // Previously used slot beyond last empty one
            moveSlot(s, S, Continue);
           }
         };
       }
     };

    new If (usedKeys.empty())                                                                                           // Compact keys
     {void Then() {}                                                                                                    // Nothing to compact as empty
      void Else()
       {new If (usedKeys.full())                                                                                        // Keys cannot be compacted as they are full
         {void Then() {}
          void Else()
           {new For(numberOfKeys())                                                                                     // No need to make any more than this number of moves
             {void body(Int Index, Bool Continue)
               {final BitSet.Pos k = usedKeys .lastZero();                                                              // Last empty key
                final BitSet.Pos K = usedKeys .firstOne();                                                              // First used key so we get the longest possible move
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
    new If (usedKeys.empty())                                                                                           // Something to redistribute
     {void Then() {}                                                                                                    // Nothing to redistrinute as the slots are empty
      void Else()                                                                                                       // Redistribute
       {final Int         N = new Int(numberOfSlotsToKeys());                                                           // Maximum number of slots
        final Int         R = new Int(numberOfKeys());                                                                  // Maximum number of keys
        compactLeft();                                                                                                  // Compact everything to the left so it is in a known position
        final BitSet.Pos  c = usedKeys.firstZero();                                                                     // Number of slots in use
        final Int     space = N.Sub(c).div(c);                                                                          // Space between used slots
        final Int     cover = space.Inc().mul(c.Dec()).inc();                                                           // Covered space from first used slot to last used slot,
        final Int remainder = N.Sub(cover);                                                                             // Uncovered remainder
        final Int         p = remainder.Down();                                                                         // Start position for first used slot giving any over to end to bias slighlty in favor of preseorted data
        new ForCount(c)                                                                                                 // Redistribute used slots
         {void body(Int Index)                                                                                          // Initialize background of slots
           {final Int s = c.Dec().sub(Index);                                                                           // Index of source element to be moved
            final Int t = p.Add(s.Mul(space)).add(s);                                                                   // Index in slots of target element to be set
            final Int k = getSlotToKeyValue(s);                                                                         // Index of key being moved
            final Int K = getKeyValue(k);                                                                               // Value of key being moved
            delSlotAndKey(s);
            setSlotAndKey(t, k, K);
           }
         };
       }
     };
   }

  void shiftUpOne(Int Position, Int Width)                                                                              // Shift up the specified slots by one position to create a free space at the specified position
   {new ForCount(Width)                                                                                                 // Move the indicated slots up one position
     {void body(Int Index)
       {final Int t = Position.Add(Width).sub(Index);                                                                   // Index of source element to be moved
        final Int s = t.Dec();                                                                                          // Index in slots of target element to be set
        final Int k = getSlotToKeyValue(s);                                                                             // Index of key being moved
        final Int K = getKeyValue(k);                                                                                   // Value of key being moved
        delSlotAndKey(s);
        setSlotAndKey(t, k, K);
       }
     };
   }

  void shiftDownOne(Int Position, Int Width)                                                                            // Shift down the specified slots by one position to create a free space at the specified position
   {final Slots slots = this;
    new ForCount(Width)                                                                                                 // Move the indicated slots up one position
     {void body(Int Index)
       {final Int t = Position.Sub(Width).add(Index);                                                                              // Index of source element to be moved
        final Int s = t.Inc();                                                                                          // Index in slots of target element to be set
        final Int k = getSlotToKeyValue(s);                                                                             // Index of key being moved
        final Int K = getKeyValue(k);                                                                                   // Value of key being moved
        delSlotAndKey(s);
        setSlotAndKey(t, k, K);
       }
     };
   }

//  Slots duplicateSlots()                                                                                                // Copy the source slots
//   {final Slots t = new Slots(numberOfKeys, byteMemory);
//    System.arraycopy(byteMemory.bytes, byteMemory.start, t.byteMemory.bytes, byteMemory.start, byteMemory.width);
//    return t;                                                                                                           // The copied slots
//   }
//
//  Slots copySlots(Slots Source)                                                                                         // Copy the source slots
//   {final int n = numberOfKeys, N = Source.numberOfKeys, w = byteMemory.width, W = Source.byteMemory.width;
//    if (n != N) stop("Different number of refs:", n, N);
//    if (w != W) stop("Different widths:", w, W);
//    System.arraycopy(Source.byteMemory.bytes, Source.byteMemory.start, byteMemory.bytes, byteMemory.start, w);
//    return this;                                                                                                        // The copied slots
//   }

/*

  class Slot extends Int                                                                                                // A reference to a slot
   {Slot()            {super();}                                                                                        // An invalid slot
    Slot(int Value)   {super(Value);}                                                                                   // A valid slot
    Slot(Int Value)   {super(Value);}                                                                                   // A slot
    Int       value() {return this;}                                                                                    // The value of the key
    Int         Int() {return this;}                                                                                    // The value of the key
    Slot      right() {return new Slot(Inc());}                                                                         // Step right
    Slot       left() {return new Slot(Dec());}                                                                         // Step left


    Slot locatePrevUsedSlot()                                                                                           // Absolute position of this slot if it is in use or else the next lower used slot
     {return choose_Slot(usedSlotsToKeys(this), ()->this, ()->stepLeft());
     }

    Slot locateNextUsedSlot()                                                                                           // Absolute position of this slot if it is in use or else the next lower used slot
     {return choose_Slot(usedSlotsToKeys(this), ()->this, ()->stepRight());
     }

    Bool eq(Key Key) {return Key.eq(keys(this));}                                                                       // Search key is equal to indexed key
    Bool le(Key Key) {return Key.le(keys(this));}                                                                       // Search key is less than or equal to indexed key
    Bool lt(Key Key) {return eq(Key).flip().and(()->{return le(Key);});}                                                // Search key is less than or equal to indexed key
    Bool ge(Key Key) {return eq(Key).        or(()->{return gt(Key);});}                                                // Search key is less than or equal to indexed key
    Bool gt(Key Key) {return le(Key).flip();}                                                                           // Search key is less than or equal to indexed key

    public String toString()
     {return "Slot: "+i();
     }
   }

  slot valid_slot(Bool Choice, Supplier<Int> Then)                                                                      // Create a valid slot reference
   {return If(Choice, new slot(), ()->new slot(Then.get()), ()->new slot());
   }

  Slot valid_Slot(Bool Choice, Supplier<Int> Then)                                                                      // Create a valid slot reference
   {return If(Choice, new Slot(), ()->new Slot(Then.get()), ()->new Slot());
   }

  Slot choose_Slot(Bool Choice, Supplier<Int> Then, Supplier<Int> Else)                                                 // Choose a slot
   {return If(Choice, new Slot(), ()->new Slot(Then.get()), ()->new Slot(Else.get()));
   }

//D2 Keys                                                                                                               // Define a key

  class Key extends Int                                                                                                 // A key
   {Key()             {super();}                                                                                        // A not valid key
    Key(  int Value)  {super(Value);}                                                                                   // A valid key
    Key(  Int Value)  {super(Value);}                                                                                   // A key
    Int       value() {return this;}
    public String toString()
     {return "Key : "+i();
     }
   }

  Key firstKey()                                                                                                        // First key in slots
   {if (empty().b()) stop("No first key in empty slots");                                                               // First key in slots if there is one
    return keys(locateFirstUsedSlot());
   }

  Key lastKey()                                                                                                         // Last key in slots
   {if (empty().b()) stop("No last key in empty slots");                                                                // Last key in slots if there is one
    return keys(locateLastUsedSlot());
   }

//D2 Slots                                                                                                                // Manage the slots

  void setSlots(int...Slots)                                                                                            // Set slots as used
   {for (int i : range(Slots.length))
     {usedSlotsToKeys(new Slot(new Int(Slots[i])), new Bool(true));
     }
   }

  void clearSlots(int...Slots)                                                                                          // Set slots as not being used
   {for (int i : range(Slots.length))
     {usedSlotsToKeys(new Slot(new Int(Slots[i])), new Bool(false));
     }
   }

  void clearFirstSlot()                                                                                                 // Set the first used slot to not used
   {final Slots.Slot f = locateFirstUsedSlot();
    new If (f.valid()) {void Then() {usedSlotsToKeys(f, new Bool(false));}};
   }

  void clearSlotAndRef(Slot I)                                                                                          // Remove a key from the slots
   {freeRef(new slot(memory.slots(I)));
    clearSlots(I.i());
   }

  slot     slots(Slot I) {return  new slot(memory.slots    (I));}                                                       // The indexed slot
  Bool usedSlotsToKeys(Slot I) {return           memory.usedSlotsToKeys(I);}                                                        // The indexed slot usage indicator
  Bool  usedKeys(slot I) {return           memory.usedKeys (I);}                                                        // The indexed reference usage indicator
  Key       keys(Slot I) {return   new Key(memory.keys(memory.slots(I)));}                                              // The indexed key

  void     slots(Slot I, slot   Ref) {memory.slots    (I, Ref.value());}                                                // The indexed slot
  void usedSlotsToKeys(Slot I, Bool Value) {memory.usedSlotsToKeys(I, Value);}                                                      // The indexed slot usage indicator
  void  usedKeys(slot I, Bool Value) {memory.usedKeys (I, Value);}                                                      // The indexed reference usage indicator
  void      keys(Slot I, Key    Key) {memory.keys(memory.slots(I), Key);}                                               // The indexed key

  Key  key(slot I) {return new Key(memory.keys(I));}                                                                    // Get the key directly
  void key(slot I, Key Key)       {memory.keys(I, Key);}                                                                // Set the key directly

  Allocation name() {return new Allocation(memory.name());}                                                             // Get the name
  void name(Allocation Name)              {memory.name(Name);}                                                          // Set the name

  Int  type()  {return memory.type();}                                                                                  // Get the type
  void type(Int Type) {memory.type(Type);}                                                                              // Set the type

//D2 Refs                                                                                                               // Allocate and free references to keys

  slot allocRef()                                                                                                       // Allocate a reference to one of the keys in the slots. A linear search is used here because in hardware this will be done in parallel
   {final slot I = locateFirstEmptyRef();

    new If (I.valid())
     {void Then()
       {usedKeys(I, new Bool(true));
       }
      void Else()
       {stop("No more slots available in this set of slots");
       }
     };
    return I;
   }

  void freeRef(slot Ref) {usedKeys(Ref, new Bool(false));}                                                              // Free a reference to one of the keys in the slots

//D2 Low level operations                                                                                               // Low level operations on slots

  Int locateNearestFreeSlotToKey(Slot Position)                                                                              // Relative position of the nearest free slot to the indicated position if there is one.
   {final Int r = new Int(0);
    if (usedSlotsToKeys(Position).b())                                                                                        // The slot is not free already. If it is not free we do at least get an error if the specified position is invalid
     {final Int        Q = Position.value();                                                                            // The current position
      final BitSet     s = memory.usedSlotsBits;                                                                        // The bitset to query
      final BitSet.Pos p = s.prevZero(s.new Pos(Q));                                                                    // Prev free slot
      final BitSet.Pos n = s.nextZero(s.new Pos(Q));                                                                    // Next free slot
      final Bool       d = new Bool().clear();                                                                          // Done when set

      if (p.notValid().b() && n.notValid().b()) stop("No more free slots");                                             // The caller should check that the slots are not full before calling us

      new If (p.notValid().and(()->{return n.valid();}))                                                                // Next free slot because no prev free slot
       {void Then()
         {r.set(n.position().Sub(Q)); d.set();
         }
       };
      new If (d.Flip().and                                                                                              // Prev free slot because no next free slot
       (()->{return p.valid();},
        ()->{return n.notValid();}))
       {void Then()
         {r.set(p.position().Sub(Q)); d.set();
         }
       };
      new If (d.Flip())                                                                                                 // Choose nearest slot favoring lower slot if they are both the same distance away
       {void Then()
         {final Int P = p.position().Sub(Q), N = n.position().Sub(Q);                                                   // Relative positions
          r.set(If (P.Neg().le(N), new Int(), ()->P, ()->N));
         }
       };
     }
    return r;
   }

  void shift(Int Position, Int Width)                                                                                   // Shift the specified number of slots around the specified position one bit left or right depending on the sign of the width.  The liberated slot is not initialized.
   {new If (Width.ne(0))                                                                                                // Non zero shift
     {void Then()
       {final Bool p = Width.gt(0);                                                                                     // Whether we are shifting up or down
        new For (If (p, new Int(), ()->Width, ()->Width.Neg()))                                                                    // Move each slot
         {void body(Int i, Bool C)
           {final Int  d = If (p, new Int(), ()->i.Neg(), ()->i);
            final Slot P = new Slot(Position.Add(Width).add(d));
            slots(P, slots(If (p, new Slot(), ()->P.left(), ()->P.right())));                                           // Move slot
            C.set();
           }
         };
        usedSlotsToKeys(new Slot(Position.Add(Width)), new Bool(true));                                                       // We only move occupied slots
       }
     };
   }


  Bool mergeSlot(Slots S, Slot I, slot J)                                                                               // Merge a slot
   {final Bool m = new Bool().clear();                                                                                  // Whether a successful merge occurred
    new If (S.usedSlotsToKeys(I))
     {void Then()
       {    slots(I, S.    slots(I));
        usedSlotsToKeys(I, S.usedSlotsToKeys(I));
         usedKeys(J, S. usedKeys(J));
             keys(I, S.     keys(I));
        m.set();
       }
     };
    return m;
   }

  void mergeCompacted(Slots Left, Slots Right)                                                                          // Merge left and right compacted slots into the current slots
   {final Slots l = Left, r = Right;
    reset();
    new For(numberOfKeys())                                                                                             // Each reference
     {void body(Int i, Bool C)
       {final Slot I = new Slot(i);                                                                                     // The input slots have been compacted so this Slot will match the corresponding slot
        final slot J = new slot(i);
        final Bool c = new Bool().set();                                                                                // Continue until false
        c.and(()->{return mergeSlot(l, I, J).flip();});                                                                 // Merge on left
        c.and(()->{return mergeSlot(r, I, J).flip();});                                                                 // Merge on right
        new If (c)                                                                                                      // Reset center
         {void Then()
           {usedSlotsToKeys(I, new Bool(false));
            usedKeys (J, new Bool(false));
           }
         };
        C.set();
       }
     };
   }

  Bool mergeBack(Slots Left, Slots Right)                                                                               // Merge the specified slots back into the current set of slots
   {Left.compactLeft(); Right.compactRight();
    mergeCompacted(Left, Right);
    return new Bool(true);
   }

  Bool mergeOnRight(Slots Right)                                                                                        // Merge the specified slots from the right
   {return countUsed().Add(Right.countUsed()).gt(numberOfSlotsToKeys()).b() ?
           new Bool(false) :
           mergeBack(duplicateSlots(), Right.duplicateSlots());
   }

  Bool mergeOnLeft(Slots Left)                                                                                          // Merge the specified slots from the left
   {return Left.countUsed().Add(countUsed()).gt(numberOfSlotsToKeys()).b() ?
           new Bool(false) :
           mergeBack(Left.duplicateSlots(), duplicateSlots());
   }

//D2 High level operations                                                                                              // Find, insert, delete values in the slots

  public slot insert(Key Key)                                                                                           // Insert a key into the slots maintaining the order of all the keys in the slots and returning the index of the reference to the key
   {final slot alloc = allocRef();                                                                                      // The location in which to store the search key
    key(alloc, Key);                                                                                                    // Store the new key in the referenced location
    final Locate l = new Locate(Key);                                                                                   // Search for the slot containing the key closest to their search key
    new If   (l.found().Flip())                                                                                         // Not found
     {void Then()
       {new If (l.notFoundBecauseEmpty())                                                                               // Empty place the key in the middle
         {void Then()
           {final Slot S = new Slot(new Int(numberOfSlotsToKeys()).down().i());
            slots     (S, alloc);
            usedSlotsToKeys (S, new Bool(true));
           }
          void Else()
           {new If (l.above())                                                                                          // Insert their key above the found key
             {void Then()
               {final Int i = l;
                final Int w = new Int(locateNearestFreeSlotToKey(l));                                                        // Width of move and direction needed to liberate a slot here - we know there is one because we know the slots are not full
                new If (w.gt(0))                                                                                        // Move up
                 {void Then()                                                                                           // Move up
                   {shift             (i.Inc(), w.Dec());                                                               // Liberate a slot at this point
                    slots    (new Slot(i).right(), alloc);                                                              // Place their current key in the empty slot, it has already been marked as set so there is no point in setting it again
                    usedSlotsToKeys(new Slot(i).right(), new Bool(true));
                   }
                  void Else()
                   {new If (w.lt(0))                                                                                    // Liberate a slot below the current slot
                     {void Then()                                                                                       // Liberate a slot below the current slot
                       {shift(         i,  w);                                                                          // Shift any intervening slots blocking the slot below
                        slots(new Slot(i), alloc);                                                                      // Insert into the slot below
                       }
                     };
                   }
                 };
                new If (w.abs().ge(redistributionWidth()))                                                              // Redistribute if the used slots are densely packed
                 {void Then()
                   {redistribute();
                   }
                 };
               }
              void Else()
               {new If (l.below)                                                                                        // Insert their key below the found key
                 {void Then()
                   {final Int i = l;
                    final Int w = new Slot(locateNearestFreeSlotToKey(l));                                                   // Width of move and direction needed to liberate a slot here - we know there is one because we know the slots are not full
                    new If (w.gt(0))                                                                                    // Move up
                     {void Then()                                                                                       // Move up
                       {shift(i, w);                                                                                    // Liberate a slot at this point
                        slots(l, alloc);                                                                                // Place their current key in the empty slot, it has already been marked as set so there is no point in setting it again
                       }
                      void Else()
                       {new If (w.lt(0))                                                                                // Liberate a slot below the current slot
                         {void Then()                                                                                   // Liberate a slot below the current slot
                           {shift             (i.Dec(),   w.Inc());                                                     // Shift any intervening slots blocking the slot below
                            slots    (new Slot(i).left(), alloc);                                                       // Insert into the slot below
                            usedSlotsToKeys(new Slot(i).left(), new Bool(true));                                              // Mark the free slot at the start of the range of occupied slots as now in use
                           }
                         };
                       }
                     };
                    new If (w.Abs().ge(redistributionWidth()))                                                          // Redistribute if the used slots are densely packed
                     {void Then()
                       {redistribute();
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
    return alloc;                                                                                                       // The index of the reference to the key
   }

  class Locate extends Slot                                                                                             // Locate the slot containing the search key if possible else the key immediately above or below the search key.
   {private boolean above;                                                                                              // The search key is above or equal to the found key
    private boolean below;                                                                                              // The search key is below or equal to the found key
    private boolean all;                                                                                                // Above all or below all if true

    public String toString()                                                                                            // Print the location
     {if (found().b())                return f("%d found", i());
      if (notFoundBecauseEmpty().b()) return "notFoundBecauseEmpty";
      return f("%2d %s %s %s", i(),
                               above ? "above" : "",
                               below ? "below" : "",
                               all   ? "all"   : "");
     }

    void pos(Slot At, boolean Above, boolean Below)                                                                     // Specify the position of the location
     {set(At.i()); above = Above; below = Below;
     }

    void above(Slot At) {pos(At, true, false);}                                                                         // Their search key is above this key
    void below(Slot At) {pos(At, false, true);}                                                                         // Their search key is below this key
    void found(Slot At) {pos(At, true,  true);}                                                                         // Found their search key
    void none ()        {}                                                                                              // Slots are empty

    Bool                found() {return new Bool( above &&  below);}                                                    // Oh America - my new found land.
    Bool notFoundBecauseEmpty() {return new Bool(!above && !below);}                                                    // The slots are empty so neither above or below or exact
    Bool                above() {return new Bool(above);}                                                               // The insertion position is above the located position
    Bool                below() {return new Bool(below);}                                                               // The insertion position is below the located position

    Locate(Key Key)                                                                                                     // Locate the slot containing the search key if possible.
     {super(Key);

      new If (empty())
       {void Then()
         {none();                                                                                                       // Empty so their search key cannot be found
         }
        void Else()
         {final Slot a = locateFirstUsedSlot();                                                                         // Lower limit
          final Slot b = locateLastUsedSlot ();                                                                         // Upper limit
          final Bool      c = new Bool().set();                                                                         // Continue the search unless set
          new If (c.And(()->{return a.eq(Key);})) {void Then() {c.clear(); found(a);            }};                     // Found at the start of the range
          new If (c.And(()->{return b.eq(Key);})) {void Then() {c.clear(); found(b);            }};                     // Found at the end of the range
          new If (c.And(()->{return a.le(Key);})) {void Then() {c.clear(); below(a); all = true;}};                     // Smaller than any key
          new If (c.And(()->{return b.gt(Key);})) {void Then() {c.clear(); above(b); all = true;}};                     // Greater than any key

          new If (c)                                                                                                    // Search
           {void Then()
             {new For(numberOfSlotsToKeys())                                                                                  // Perform a reasonable number of searches knowing the key, if it is present, is within the current range. NB this is not a linear search, the slots are searched using binary search with an upper limit that has fooled some reviewers into thinking that a linear search is being performed.
               {void body(Int i, Bool C)
                 {final Slot M = new Slot(a.Add(b).down());                                                             // Desired mid point - but there might not be a slot in use at this point
                  final Slot A = M.locatePrevUsedSlot();                                                                // Occupied slot on or preceding mid point
                  final Slot B = M.locateNextUsedSlot();                                                                // Occupied slot on or succeeding mid point
                  final Int Ap = A.Int(), ap = a.Int();                                                                 // New and current lower limit of range
                  final Int Bp = B.Int(), bp = b.Int();                                                                 // New and current upper limit of range
                  C.set();                                                                                              // Continue the search unless cleared
                                                                                                                        // Make sure that the new range is tighter than the existing one
                  new If (C.And(()->{return Ap.ne(ap);}, ()->{return A.ge(Key);})) {void Then() {C.clear(); a.set(A);}};
                  new If (C.And(()->{return Ap.ne(bp);}, ()->{return A.le(Key);})) {void Then() {C.clear(); b.set(A);}};
                  new If (C.And(()->{return Bp.ne(ap);}, ()->{return B.ge(Key);})) {void Then() {C.clear(); a.set(B);}};
                  new If (C.And(()->{return Bp.ne(bp);}, ()->{return B.le(Key);})) {void Then() {C.clear(); b.set(B);}};
                  new If (C)                                                                                            // The slots must be adjacent
                   {void Then()
                     {new If (C.And(()->{return a.eq(Key);})) {void Then() {C.clear(); found(a);}};
                      new If (C.And(()->{return b.eq(Key);})) {void Then() {C.clear(); found(b);}};
                      new If (C)                              {void Then() {C.clear(); below(b);}};
                      c.clear();                                                                                        // Search has completed
                     }
                   };
                  C.set(c);                                                                                             // Continue search with new range
                 }
               };
              new If (c)                                                                                                // Incomplete search
               {void Then()
                 {stop(f("%s %s %d", "Searched unsuccessfully more than",
                         "the maximum number of times:", numberOfSlotsToKeys()));
                 }
               };
             }
           };
         }
       };
     }
   }

  Slot locateFirstGe(Key Key)                                                                                           // Locate the slot containing the first key greater than or equal to the search key
   {final Locate l = new Locate(Key);
    final Slot   a = l;
    return If (l.notFoundBecauseEmpty(), new Slot(), ()->new Slot(),
      ()->If (l.below(), new Slot(), ()->a, ()->a.right().locateNextUsedSlot()));
   }

  Slot locate(Key Key)                                                                                                  // Locate the slot containing the current search key if possible.
   {final Locate l = new Locate(Key);                                                                                   // Locate the search key
    return If(l.found(), new Slot(), ()->l, ()->new Slot());                                                            // Found if exact match
   }

  slot find(Key Key)                                                                                                    // Find the index of the current key in the slots
   {final Slot i = locate(Key);
    return If (i.notValid(), new slot(), ()->new slot(), ()->slots(i));
   }

  Bool delete(Key Key)                                                                                                  // Delete the specified key from the slots
   {final Slot i = locate(Key);                                                                                         // Locate the search key
    final Bool C = new Bool();
    new If (i.notValid())                                                                                               // Key not present so no need to delete it
     {void Then()
       {C.clear();                                                                                                      // The key is not in the slots
       }
      void Else()
       {clearSlotAndRef(i);                                                                                             // Delete found key
        C.set();                                                                                                        // Indicate that the key was deleted
       }
     };
    return C;                                                                                                           // Wether the key was found and deleted
   }
*/

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
    s.append("\nslotsKeys: "); for (int i : N) s.append(f(" "+formatKey, getSlotToKeyValue(i)));
    s.append("\nkeysSlots: "); for (int i : N) s.append(f(" "+formatKey, getKeyToSlotValue(i)));
    s.append("\nusedSlots: "); for (int i : N) s.append(                 usedSlotsToKeys.getBitNC(i) ? "   X" : "   .");
    s.append("\nusedKeys : "); for (int i : R) s.append(                 usedKeys .getBitNC(i) ? "   X" : "   .");
    s.append("\nkeys     : "); for (int i : R) s.append(f(" "+formatKey, getKeyValue(i)));
    return ""+s+"\n";
   }

  String printInOrder()                                                                                                 // Print the values in the used slots in order
   {final StringJoiner s = new StringJoiner(", ");
    for (int i : range(numberOfSlotsToKeys()))
     {if (usedSlotsToKeys.getBit(usedSlotsToKeys.new Pos(i)).b()) s.add(""+getKeyValue(new Int(i)).i());
     }
    return ""+s;
   }

//D2 Memory                                                                                                             // Read and write from an array of bytes

  class SlotsMemoryPositions                                                                                            // Positions of fields in memory
   {final int N = numberOfSlotsToKeys();
    final int R = numberOfKeys();

    final BitSet.Build us = new BitSet.Build().bitSize(N).one(true).zero(true);                                         // Specification of bit set for used slots
    final BitSet.Build ur = new BitSet.Build().bitSize(R).one(true).zero(true);                                         // Specification of bit set for references

    final int posSlotsToKeys     = 0;                                                                                   // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
    final int posKeysToSlots     = posSlotsToKeys     + ib(N);                                                          // USed keys to slot referencing the key
    final int posUsedSlotsToKeys = posKeysToSlots     + ib(N);                                                          // Slots in use
    final int posUsedKeysToSlots = posUsedSlotsToKeys + ib(N);                                                          // Slots in use
    final int posusedKeys        = posUsedKeysToSlots + us.byteSize();                                                  // References in use.  There are fewer references than slots to make insertions faster
    final int posKeys            = posusedKeys        + ur.byteSize();                                                  // Keys used in btree held unordered in this array but ordered by the slot refernces rto them
    final int size               = posKeys            + ib(N);                                                          // Size of slots
  }

//D1 Tests                                                                                                              // Tests

//D2 Slots                                                                                                              // Test the slots

  static void test_slots(boolean Ex)
   {final Slots s = new Slots(8)
     {void slotsCode()
       {immediate(Ex);
        final Bool e = usedSlotsToKeys.empty(); ok(()->e, true);
        final Bool f = usedSlotsToKeys.full (); ok(()->f, false);
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
       {immediate(Ex);
        setSlots(2, 4, 5, 6, 9, 10, 12);
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
       {immediate(Ex);
        putKey(new Int(2),  new Int(1));
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
       {immediate(Ex);
        setSlotAndKey(new Int(3),  new Int(2),  new Int(1));
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
       {immediate(Ex);
        setSlotAndKey(new Int(2),  new Int(1),  new Int(1));
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
       {immediate(Ex);
        setSlotAndKey(new Int(2),  new Int(1),  new Int(1));
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

        execute();
       }
     };
   }

  static void test_compactRight()
   {test_compactRight(true);
    test_compactRight(false);
   }

  static void test_redistribute(boolean Ex)
   {final Slots s = new Slots(8)
     {void slotsCode()
       {immediate(Ex);
        maxSteps = 99999;
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
   {final Slots s = new Slots(8)
     {void slotsCode()
       {immediate(Ex);
        maxSteps = 99999;
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

/*
  static void test_locateNearestFreeSlotToKey()
   {final Slots s = new Slots(8);
    s.setSlots(2, 3, 5, 6, 7, 9, 11, 13);
                      //0123456789012345
    ok(s.printSlots(), "..XX.XXX.X.X.X..");
    ok(s.locateNearestFreeSlotToKey(s.new Slot( 0)),  0);
    ok(s.locateNearestFreeSlotToKey(s.new Slot( 1)),  0);
    ok(s.locateNearestFreeSlotToKey(s.new Slot( 2)), -1);
    ok(s.locateNearestFreeSlotToKey(s.new Slot( 3)), +1);
    ok(s.locateNearestFreeSlotToKey(s.new Slot( 4)),  0);
    ok(s.locateNearestFreeSlotToKey(s.new Slot( 5)), -1);
    ok(s.locateNearestFreeSlotToKey(s.new Slot( 6)), -2);
    ok(s.locateNearestFreeSlotToKey(s.new Slot( 8)),  0);
    ok(s.locateNearestFreeSlotToKey(s.new Slot( 9)), -1);
    ok(s.locateNearestFreeSlotToKey(s.new Slot(10)),  0);
    ok(s.locateNearestFreeSlotToKey(s.new Slot(11)), -1);
    ok(s.locateNearestFreeSlotToKey(s.new Slot(12)),  0);
    ok(s.locateNearestFreeSlotToKey(s.new Slot(13)), -1);
    ok(s.locateNearestFreeSlotToKey(s.new Slot(14)),  0);
    ok(s.locateNearestFreeSlotToKey(s.new Slot(15)),  0);

    ok(s.locateFirstUsedSlot().i(),      2);
    ok(s.locateLastUsedSlot ().i(),      13);
    ok(s.new Slot( 9).locatePrevUsedSlot().i(),     9);
    ok(s.new Slot(10).locatePrevUsedSlot().i(),     9);
    ok(s.new Slot(10).locateNextUsedSlot().i(),    11);
    ok(s.new Slot(11).locateNextUsedSlot().i(),    11);

    ok(s.new Slot( 1).locatePrevUsedSlot().notValid(), true);
    ok(s.new Slot(14).locateNextUsedSlot().notValid(), true);
   }

  static void test_redistribute(boolean Ex)
   {final Slots s = new Slots(8);
    for (int i : range(s.numberOfSlotsToKeys()))
     {final Slots.Slot S = s.new Slot(i);
      s.usedSlotsToKeys(S);
      s.setSlots(i);
     }
                                          t.put(()->s.printSlots());
                        s.redistribute(); t.put(()->s.printSlots());
    s.clearFirstSlot(); s.redistribute(); t.put(()->s.printSlots());
    s.clearFirstSlot(); s.redistribute(); t.put(()->s.printSlots());
    s.clearFirstSlot(); s.redistribute(); t.put(()->s.printSlots());
    s.clearFirstSlot(); s.redistribute(); t.put(()->s.printSlots());
    s.clearFirstSlot(); s.redistribute(); t.put(()->s.printSlots());
    s.clearFirstSlot(); s.redistribute(); t.put(()->s.printSlots());
    s.clearFirstSlot(); s.redistribute(); t.put(()->s.printSlots());
    s.clearFirstSlot(); s.redistribute(); t.put(()->s.printSlots());
    s.clearFirstSlot(); s.redistribute(); t.put(()->s.printSlots());
    s.clearFirstSlot(); s.redistribute(); t.put(()->s.printSlots());
    s.clearFirstSlot(); s.redistribute(); t.put(()->s.printSlots());
    s.clearFirstSlot(); s.redistribute(); t.put(()->s.printSlots());
    s.clearFirstSlot(); s.redistribute(); t.put(()->s.printSlots());
    s.clearFirstSlot(); s.redistribute(); t.put(()->s.printSlots());
    s.clearFirstSlot(); s.redistribute(); t.put(()->s.printSlots());
    s.clearFirstSlot(); s.redistribute(); t.put(()->s.printSlots());
    //stop(t.output());
    //0123456789012345
    ok(t.output(), """
XXXXXXXXXXXXXXXX
XXXXXXXXXXXXXXXX
XXXXXXXXXXXXXXX.
.XXXXXXXXXXXXXX.
.XXXXXXXXXXXXX..
..XXXXXXXXXXXX..
..XXXXXXXXXXX...
...XXXXXXXXXX...
...XXXXXXXXX....
X.X.X.X.X.X.X.X.
.X.X.X.X.X.X.X..
..X.X.X.X.X.X...
.X..X..X..X..X..
.X...X...X...X..
..X....X....X...
...X.......X....
.......X........
................
""");
   }

  static void test_redistribute()
   {test_redistribute(true);
    //test_redistribute(false);
   }

  static void test_ifd()
   {final Slots s = new Slots(8);
    s.initialize();
                              ok(s.empty(), true);  ok(s.full(), false);
    s.insert(t.new Key(14));  ok(s.empty(), false); ok(s.full(), false);
    s.insert(t.new Key(13));  ok(s.countUsed(), 2);
    s.insert(t.new Key(16));
    s.insert(t.new Key(15));
    s.insert(t.new Key(18));
    s.insert(t.new Key(17));
    s.insert(t.new Key(12));
    s.insert(t.new Key(11));
    ok(s.printInOrder(), "11, 12, 13, 14, 15, 16, 17, 18");
                        ok(s.empty(), false);
                        ok(s.full(),  true);
    ok(s, """
Slots    : name:  0, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   0   7   6   1   0   3   2   5   4   0   0   0
usedSlotsToKeys:    .   .   .   .   .   X   X   X   X   X   X   X   X   .   .   .
usedKeys :    X   X   X   X   X   X   X   X
keys     :   14  13  16  15  18  17  12  11
""");
    ok(s.locate(t.new Key(11)).i(),  5);
    ok(s.locate(t.new Key(12)).i(),  6);
    ok(s.locate(t.new Key(13)).i(),  7);
    ok(s.locate(t.new Key(14)).i(),  8);
    ok(s.locate(t.new Key(15)).i(),  9);
    ok(s.locate(t.new Key(16)).i(), 10);
    ok(s.locate(t.new Key(17)).i(), 11);
    ok(s.locate(t.new Key(18)).i(), 12);
    ok(s.locate(t.new Key(10)).notValid());
    ok(s.locate(t.new Key(20)).notValid());

    ok(s.key(s.find(t.new Key(14))).i(), 14); ok(s.delete(t.new Key(14)), true); ok(s.printInOrder(), "11, 12, 13, 15, 16, 17, 18");
    ok(s.key(s.find(t.new Key(12))).i(), 12); ok(s.delete(t.new Key(12)), true); ok(s.printInOrder(), "11, 13, 15, 16, 17, 18");
    ok(s.key(s.find(t.new Key(13))).i(), 13); ok(s.delete(t.new Key(13)), true); ok(s.printInOrder(), "11, 15, 16, 17, 18");
    ok(s.key(s.find(t.new Key(16))).i(), 16); ok(s.delete(t.new Key(16)), true); ok(s.printInOrder(), "11, 15, 17, 18");
    ok(s.key(s.find(t.new Key(18))).i(), 18); ok(s.delete(t.new Key(18)), true); ok(s.printInOrder(), "11, 15, 17");
    ok(s.key(s.find(t.new Key(11))).i(), 11); ok(s.delete(t.new Key(11)), true); ok(s.printInOrder(), "15, 17");
    ok(s.key(s.find(t.new Key(17))).i(), 17); ok(s.delete(t.new Key(17)), true); ok(s.printInOrder(), "15");
    ok(s.key(s.find(t.new Key(15))).i(), 15); ok(s.delete(t.new Key(15)), true); ok(s.printInOrder(), "");

    ok(s.locate(t.new Key(10)).notValid()); ok(s.delete(t.new Key(10)), false);
   }

  static void test_idn()                                                                                                // Repeated inserts and deletes
   {final Slots s = new Slots(8);

    final Key k14 = t.new Key(14);
    final Key k13 = t.new Key(13);
    final Key k16 = t.new Key(16);
    final Key k15 = t.new Key(15);

    for (int i = 0; i < s.numberOfSlotsToKeys()*10; i++)
     {s.insert(k14); s.redistribute();
      s.insert(k13); s.redistribute();
      s.insert(k16); s.redistribute();
      s.insert(k15); s.redistribute();
      ok(s.printInOrder(), "13, 14, 15, 16");
      ok(s.countUsed(), 4);
      s.delete(k14); s.redistribute();
      s.delete(k13); s.redistribute();
      s.delete(k16); s.redistribute();
      s.delete(k15); s.redistribute();
      ok(s.printInOrder(), "");
      ok(s.countUsed(), 0);
     }
   }

  static void test_tooManySearches()
   {final Tree  t =   new Tree (8);
    final Slots s = t.new Slots(8);

    s.insert(t.new Key(10));
    s.insert(t.new Key(20));
    ok(s.find(t.new Key(15)).notValid());
   }

  static void test_locateFirstGeKey()
   {final Tree  t =   new Tree (8);
    final Slots s = t.new Slots(8);

    s.usedSlotsToKeys(s.new Slot( 1), t.new Bool(true)); s.slots(s.new Slot( 1), s.new slot(7)); s.usedKeys(s.new slot(7), t.new Bool(true)); s.key(s.new slot(7), t.new Key(22));
    s.usedSlotsToKeys(s.new Slot( 5), t.new Bool(true)); s.slots(s.new Slot( 5), s.new slot(4)); s.usedKeys(s.new slot(4), t.new Bool(true)); s.key(s.new slot(4), t.new Key(24));
    s.usedSlotsToKeys(s.new Slot( 9), t.new Bool(true)); s.slots(s.new Slot( 9), s.new slot(2)); s.usedKeys(s.new slot(2), t.new Bool(true)); s.key(s.new slot(2), t.new Key(26));
    s.usedSlotsToKeys(s.new Slot(14), t.new Bool(true)); s.slots(s.new Slot(14), s.new slot(0)); s.usedKeys(s.new slot(0), t.new Bool(true)); s.key(s.new slot(0), t.new Key(28));
    ok(s, """
Slots    : name:  0, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   4   0   0   0   2   0   0   0   0   0   0
usedSlotsToKeys:    .   X   .   .   .   X   .   .   .   X   .   .   .   .   X   .
usedKeys :    X   .   X   .   X   .   .   X
keys     :   28   0  26   0  24   0   0  22
""");
    ok(s.locateFirstGe(t.new Key(23)).i(),    5);
    ok(s.locateFirstGe(t.new Key(24)).i(),    5);
    ok(s.locateFirstGe(t.new Key(25)).i(),    9);
    ok(s.locateFirstGe(t.new Key(30)).notValid());
   }

  static void test_compactLeft()
   {final Tree  t =   new Tree (8);
    final Slots s = t.new Slots(8);

    s.usedSlotsToKeys(s.new Slot( 1), t.new Bool(true));; s.slots(s.new Slot( 1), s.new slot(7)); s.usedKeys(s.new slot(7), t.new Bool(true));; s.key(s.new slot(7), t.new Key(11));
    s.usedSlotsToKeys(s.new Slot( 5), t.new Bool(true));; s.slots(s.new Slot( 5), s.new slot(4)); s.usedKeys(s.new slot(4), t.new Bool(true));; s.key(s.new slot(4), t.new Key(12));
    s.usedSlotsToKeys(s.new Slot( 9), t.new Bool(true));; s.slots(s.new Slot( 9), s.new slot(2)); s.usedKeys(s.new slot(2), t.new Bool(true));; s.key(s.new slot(2), t.new Key(13));
    s.usedSlotsToKeys(s.new Slot(14), t.new Bool(true));; s.slots(s.new Slot(14), s.new slot(0)); s.usedKeys(s.new slot(0), t.new Bool(true));; s.key(s.new slot(0), t.new Key(14));
    ok(s, """
Slots    : name:  0, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   4   0   0   0   2   0   0   0   0   0   0
usedSlotsToKeys:    .   X   .   .   .   X   .   .   .   X   .   .   .   .   X   .
usedKeys :    X   .   X   .   X   .   .   X
keys     :   14   0  13   0  12   0   0  11
""");
    s.compactLeft();

    ok(s, """
Slots    : name:  0, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   1   2   3   0   0   0   0   0   0   0   0   0   0   0   0
usedSlotsToKeys:    X   X   X   X   .   .   .   .   .   .   .   .   .   .   .   .
usedKeys :    X   X   X   X   .   .   .   .
keys     :   11  12  13  14   0   0   0   0
""");
   }

  static void test_compactRight()
   {final Tree  t =   new Tree (8);
    final Slots s = t.new Slots(8);

    s.usedSlotsToKeys(s.new Slot( 1), t.new Bool(true)); s.slots(s.new Slot( 1), s.new slot(7)); s.usedKeys(s.new slot(7), t.new Bool(true)); s.key(s.new slot(7), t.new Key(11));
    s.usedSlotsToKeys(s.new Slot( 5), t.new Bool(true)); s.slots(s.new Slot( 5), s.new slot(4)); s.usedKeys(s.new slot(4), t.new Bool(true)); s.key(s.new slot(4), t.new Key(12));
    s.usedSlotsToKeys(s.new Slot( 9), t.new Bool(true)); s.slots(s.new Slot( 9), s.new slot(2)); s.usedKeys(s.new slot(2), t.new Bool(true)); s.key(s.new slot(2), t.new Key(13));
    s.usedSlotsToKeys(s.new Slot(14), t.new Bool(true)); s.slots(s.new Slot(14), s.new slot(0)); s.usedKeys(s.new slot(0), t.new Bool(true)); s.key(s.new slot(0), t.new Key(14));
    ok(s, """
Slots    : name:  0, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   4   0   0   0   2   0   0   0   0   0   0
usedSlotsToKeys:    .   X   .   .   .   X   .   .   .   X   .   .   .   .   X   .
usedKeys :    X   .   X   .   X   .   .   X
keys     :   14   0  13   0  12   0   0  11
""");
    s.compactRight();
    ok(s, """
Slots    : name:  0, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   4   5   6   7   0   0   0   0   0   0   0   0
usedSlotsToKeys:    .   .   .   .   X   X   X   X   .   .   .   .   .   .   .   .
usedKeys :    .   .   .   .   X   X   X   X
keys     :    0   0   0   0  11  12  13  14
""");

    ok(s.firstKey().i(), 11);
    ok(s. lastKey().i(), 14);
   }

  static void test_memory()
   {final Tree  t =   new Tree (8);
    final Slots s = t.new Slots(8, ByteBuffer.allocate(200));

    s.usedSlotsToKeys(s.new Slot( 1), t.new Bool(true)); s.slots(s.new Slot( 1), s.new slot(7)); s.usedKeys(s.new slot(7), t.new Bool(true)); s.key(s.new slot(7), t.new Key(11));
    s.usedSlotsToKeys(s.new Slot( 5), t.new Bool(true)); s.slots(s.new Slot( 5), s.new slot(4)); s.usedKeys(s.new slot(4), t.new Bool(true)); s.key(s.new slot(4), t.new Key(12));
    s.usedSlotsToKeys(s.new Slot( 9), t.new Bool(true)); s.slots(s.new Slot( 9), s.new slot(2)); s.usedKeys(s.new slot(2), t.new Bool(true)); s.key(s.new slot(2), t.new Key(13));
    s.usedSlotsToKeys(s.new Slot(14), t.new Bool(true)); s.slots(s.new Slot(14), s.new slot(0)); s.usedKeys(s.new slot(0), t.new Bool(true)); s.key(s.new slot(0), t.new Key(14));
    s.type     (t.new Int (11));
    ok(s, """
Slots    : name:  0, type: 11, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   4   0   0   0   2   0   0   0   0   0   0
usedSlotsToKeys:    .   X   .   .   .   X   .   .   .   X   .   .   .   .   X   .
usedKeys :    X   .   X   .   X   .   .   X
keys     :   14   0  13   0  12   0   0  11
""");

    final Slots        B = s.duplicateSlots();
    final Slots.Memory m = B.memory;

    ok(B, s);

    ok(m.slots       (t.new Int(0)), 0);
    ok(m.slots       (t.new Int(1)), 7);
    ok(m.slots       (t.new Int(2)), 0);
    ok(m.slots       (t.new Int(3)), 0);
    ok(m.slots       (t.new Int(4)), 0);
    ok(m.slots       (t.new Int(5)), 4);
    ok(m.slots       (t.new Int(6)), 0);
    ok(m.usedSlotsToKeys   (t.new Int(0)), false);
    ok(m.usedSlotsToKeys   (t.new Int(1)), true);
    ok(m.usedSlotsToKeys   (t.new Int(2)), false);
    ok(m.usedSlotsToKeys   (t.new Int(3)), false);
    ok(m.usedSlotsToKeys   (t.new Int(4)), false);
    ok(m.usedSlotsToKeys   (t.new Int(5)), true);
    ok(m.usedSlotsToKeys   (t.new Int(6)), false);
    ok(m.usedKeys    (t.new Int(0)), true);
    ok(m.usedKeys    (t.new Int(1)), false);
    ok(m.usedKeys    (t.new Int(2)), true);
    ok(m.usedKeys    (t.new Int(3)), false);
    ok(m.usedKeys    (t.new Int(4)), true);
    ok(m.usedKeys    (t.new Int(5)), false);
    ok(m.usedKeys    (t.new Int(6)), false);
    ok(m.keys        (t.new Int(0)), 14);
    ok(m.keys        (t.new Int(1)),  0);
    ok(m.keys        (t.new Int(2)), 13);
    ok(m.keys        (t.new Int(3)),  0);
    ok(m.keys        (t.new Int(4)), 12);
    ok(m.keys        (t.new Int(5)),  0);
    ok(m.keys        (t.new Int(6)),  0);

    m.slots    (t.new Int(13), t.new Int(6));
    m.usedSlotsToKeys(t.new Int(13), t.new Bool(true));
    m.usedKeys (t.new Int( 6), t.new Bool(true));
    m.keys     (t.new Int( 6), t.new Int(10));

    ok(B, """
Slots    : name:  0, type: 11, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   4   0   0   0   2   0   0   0   6   0   0
usedSlotsToKeys:    .   X   .   .   .   X   .   .   .   X   .   .   .   X   X   .
usedKeys :    X   .   X   .   X   .   X   X
keys     :   14   0  13   0  12   0  10  11
""");
    ok(B.type(), 11);
   }
*/
  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_slots();
    test_locateNearestFreeSlotToKey();
    test_alloc();
    test_set_del_slot_key();
    test_compactLeft();
    test_compactRight();
    test_redistribute();
   }

  static void newTests()                                                                                                // Tests being worked on
   {//oldTests();
    test_shift();
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
