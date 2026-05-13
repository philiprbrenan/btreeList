//----------------------------------------------------------------------------------------------------------------------
// Distributed slots used t hold the key of the Btree
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

class Slots extends Program                                                                                             // A tree that translates keys into values to be implemented as an application specific integrated circuit
 {final int numberOfRefs;                                                                                               // The maximum number of references maintained by these slots
  final int size;                                                                                                       // Number of bytes needed to hold slots
  final BitSet usedSlots;                                                                                               // The slots in use.  Thre are more slotsthan refernces os that they can be distributed with intervening empty slots to make insertions faster,
  final BitSet usedRefs;                                                                                                // The references in use.
  ByteMemory.Ref byteMemoryRef = null;                                                                                  // Byte memory reference containing the slots
  final SlotsMemoryPositions slotsMemoryPositions;                                                                      // Memory layout
  final ByteMemory.Ref refSlots;                                                                                        // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
  final ByteMemory.Ref refUsedSlots;                                                                                    // Slots in use
  final ByteMemory.Ref refUsedRefs;                                                                                     // References in use.  There are fewer references than slots to make insertions faster
  final ByteMemory.Ref refKeys;                                                                                         // Keys used in btree held unordered in this array but ordered by the slot refernces rto them

//D1 Construction                                                                                                       // Construct and layout the slots

  static class Build                                                                                                    // Specification of slots
   {int numberOfRefs = 2;                                                                                               // Number of refernces in the slots
    Program.ByteMemory.Ref memoryRef = null;                                                                            // Program memory to be used

    Build numberOfRefs (int           NumberOfRefs) {numberOfRefs = NumberOfRefs; return this;}
    Build memory       (Program.ByteMemory.Ref Ref) {memoryRef    = Ref;          return this;}
   }

  Slots(Build Build)                                                                                                    // Create the slots
   {numberOfRefs         = Build.numberOfRefs;
    slotsMemoryPositions = new SlotsMemoryPositions();                                                                  // Memory layout
    size                 = slotsMemoryPositions.size;
    if (Build.memoryRef != null) byteMemoryRef = Build.memoryRef;                                                       // Use supplied memeory
    else
     {byteMemory    = new ByteMemory(size);                                                                             // Use local memory as no global memeory supplied
      byteMemoryRef = byteMemory.new Ref(0);                                                                            // Reference to local memory
     }
    refSlots     = byteMemoryRef;                                                                                       // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
    refUsedSlots = byteMemoryRef.step(slotsMemoryPositions.posUsedSlots);                                               // Slots in use
    refUsedRefs  = byteMemoryRef.step(slotsMemoryPositions.posUsedRefs);                                                // References in use.  There are fewer references than slots to make insertions faster
    refKeys      = byteMemoryRef.step(slotsMemoryPositions.posKeys);                                                    // Keys used in btree held unordered in this array but ordered by the slot refernces rto them
    usedSlots    = new BitSet(slotsMemoryPositions.us);
    usedRefs     = new BitSet(slotsMemoryPositions.ur);

    usedSlots.program = usedRefs.program = program = byteMemoryRef.program();
   }

  Slots(int NumberOfRefs) {this(new Build().numberOfRefs(NumberOfRefs));}                                               // Create the slots in local memory for testing

  //void setMemory(ByteBuffer Bytes) {memory = new Memory(Bytes);}                                                      // Set memory to be used

//  Slots duplicateSlots()                                                                                                // Copy the source slots
//   {final Slots t = new Slots(numberOfRefs, byteMemory);
//    System.arraycopy(byteMemory.bytes, byteMemory.start, t.byteMemory.bytes, byteMemory.start, byteMemory.width);
//    return t;                                                                                                           // The copied slots
//   }
//
//  Slots copySlots(Slots Source)                                                                                         // Copy the source slots
//   {final int n = numberOfRefs, N = Source.numberOfRefs, w = byteMemory.width, W = Source.byteMemory.width;
//    if (n != N) stop("Different number of refs:", n, N);
//    if (w != W) stop("Different widths:", w, W);
//    System.arraycopy(Source.byteMemory.bytes, Source.byteMemory.start, byteMemory.bytes, byteMemory.start, w);
//    return this;                                                                                                        // The copied slots
//   }

  void invalidateMemory()   {byteMemoryRef.invalidate(size);}                                                           // Invalidate the slots in such a way that they are unlikely to work well if subsequently used
  int numberOfRefs ()       {return numberOfRefs;}                                                                      // The number of references in the slots definition
  int numberOfSlots()       {return numberOfRefs()<<1;}                                                                 // Number of slots from number of refs
  int redistributionWidth() {return (int)java.lang.Math.sqrt(numberOfRefs());}                                          // Redistribute if the next slot is further than this
/*
  void initialize()                                                                                                     // Clear all the slots
   {memory.usedSlotsBits.initialize();
    memory.usedRefsBits .initialize();
   }
*/
  class slot extends Int                                                                                                // A dereferenced slot
   {slot()           {super();}                                                                                         // A not valid dereferenced slot
    slot(int Value)  {super(Value);}                                                                                    // A valid dereferenced slot
    slot(Int Value)  {super(Value);}                                                                                    // A dereferenced slot
    Int      value() {return this;}
    public String toString()
     {return "slot: "+i();
     }
   }
/*
  class Slot extends Int                                                                                                // A reference to a slot
   {Slot()            {super();}                                                                                        // An invalid slot
    Slot(int Value)   {super(Value);}                                                                                   // A valid slot
    Slot(Int Value)   {super(Value);}                                                                                   // A slot
    Int       value() {return this;}                                                                                    // The value of the key
    Int         Int() {return this;}                                                                                    // The value of the key
    Slot      right() {return new Slot(Inc());}                                                                         // Step right
    Slot       left() {return new Slot(Dec());}                                                                         // Step left

    Slot stepLeft()                                                                                                     // Step left to prior occupied slot assuming that such a step is possible
     {final BitSet.Pos q = memory.usedSlotsBits.new Pos(value());
      final BitSet.Pos p = memory.usedSlotsBits.prevOne(q);
      return valid_Slot(p.valid(), ()->p.position());
     }

    Slot stepRight()                                                                                                    // Step right to the next occupied slot assuming that such a step is possible
     {final BitSet.Pos q = memory.usedSlotsBits.new Pos(value());
      final BitSet.Pos p = memory.usedSlotsBits.nextOne(q);
      return valid_Slot(p.valid(), ()->p.position());
     }                                            setSlots

    Slot locatePrevUsedSlot()                                                                                           // Absolute position of this slot if it is in use or else the next lower used slot
     {return choose_Slot(usedSlots(this), ()->this, ()->stepLeft());
     }

    Slot locateNextUsedSlot()                                                                                           // Absolute position of this slot if it is in use or else the next lower used slot
     {return choose_Slot(usedSlots(this), ()->this, ()->stepRight());
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
     {usedSlots(new Slot(new Int(Slots[i])), new Bool(true));
     }
   }

  void clearSlots(int...Slots)                                                                                          // Set slots as not being used
   {for (int i : range(Slots.length))
     {usedSlots(new Slot(new Int(Slots[i])), new Bool(false));
     }
   }

  void clearFirstSlot()                                                                                                 // Set the first used slot to not used
   {final Slots.Slot f = locateFirstUsedSlot();
    new If (f.valid()) {void Then() {usedSlots(f, new Bool(false));}};
   }

  void clearSlotAndRef(Slot I)                                                                                          // Remove a key from the slots
   {freeRef(new slot(memory.slots(I)));
    clearSlots(I.i());
   }

  slot     slots(Slot I) {return  new slot(memory.slots    (I));}                                                       // The indexed slot
  Bool usedSlots(Slot I) {return           memory.usedSlots(I);}                                                        // The indexed slot usage indicator
  Bool  usedRefs(slot I) {return           memory.usedRefs (I);}                                                        // The indexed reference usage indicator
  Key       keys(Slot I) {return   new Key(memory.keys(memory.slots(I)));}                                              // The indexed key

  void     slots(Slot I, slot   Ref) {memory.slots    (I, Ref.value());}                                                // The indexed slot
  void usedSlots(Slot I, Bool Value) {memory.usedSlots(I, Value);}                                                      // The indexed slot usage indicator
  void  usedRefs(slot I, Bool Value) {memory.usedRefs (I, Value);}                                                      // The indexed reference usage indicator
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
       {usedRefs(I, new Bool(true));
       }
      void Else()
       {stop("No more slots available in this set of slots");
       }
     };
    return I;
   }

  void freeRef(slot Ref) {usedRefs(Ref, new Bool(false));}                                                              // Free a reference to one of the keys in the slots

//D2 Statistics                                                                                                         // Query the state of the slots

  Int countUsed()                                                                                                       // Number of slots in use. How can we do this quickly in parallel?
   {final Int n = new Int(0);
    new For(numberOfSlots())
     {void body(Int i, Bool C)
       {new If (usedSlots(new Slot(new Int(i)))) {void Then(){n.inc();}};
        C.set();
       }
     };
    return n;
   }

  Bool empty()    {return memory.usedSlotsBits.empty();}                                                                // All bits in the corresponding bitset are unused so the Slots must be empty
  Bool full ()    {return countUsed().eq(numberOfRefs());}                                                              // The number of bits in the bitset slots is either equal to or greater than the number of slots so we cannot rely on them being simultaneously full
  Bool isBranch() {return new Bool(this instanceof Branch);}                                                            // Is this set of slots implemented in a branch
  Bool isLeaf()   {return new Bool(this instanceof Leaf);}                                                              // Is this set of slots implemented in a leaf

//D2 Low level operations                                                                                               // Low level operations on slots

  Int locateNearestFreeSlot(Slot Position)                                                                              // Relative position of the nearest free slot to the indicated position if there is one.
   {final Int r = new Int(0);
    if (usedSlots(Position).b())                                                                                        // The slot is not free already. If it is not free we do at least get an error if the specified position is invalid
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

  Slot locateFirstUsedSlot()                                                                                            // Absolute position of this slot if it is in use or else the next lower used slot
   {final BitSet.Pos p = memory.usedSlotsBits.firstOne();
    return valid_Slot(p.valid(), ()->p.position());
   }

  Slot locateLastUsedSlot()                                                                                             // Absolute position of the last slot in use
   {final BitSet.Pos p = memory.usedSlotsBits.lastOne();
    return valid_Slot(p.valid(), ()->p.position());
   }

  slot locateFirstEmptyRef()                                                                                            // Absolute position of the first empty reference
   {final BitSet.Pos p = memory.usedRefsBits.firstZero();
    return If (p.valid(), new slot(), ()->new slot(p.position()), ()->new slot());
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
        usedSlots(new Slot(Position.Add(Width)), new Bool(true));                                                       // We only move occupied slots
       }
     };
   }

  void redistribute()                                                                                                   // Redistribute the unused slots evenly with a slight bias to having a free slot at the end to assist with data previously sorted into ascending order.
   {new If (empty().Flip())                                                                                             // Something to redistribute
     {void Then()
       {final Int         N = new Int(numberOfSlots());                                                                 // Maximum number of slots
        final Int         c = new Int(countUsed());                                                                     // Number of slots in use
        final Int     space = new Int(N.Sub(c).div(c));                                                                 // Space between used slots
        final Int     cover = new Int(space.Inc().mul(c.Dec()).inc());                                                  // Covered space from first used slot to last used slot,
        final Int remainder = new Int(Test.max(0, N.Sub(cover).i()));                                                   // Uncovered remainder
        final Int       []s = new Int [N.i()];                                                                          // New slots distribution
        final Bool      []u = new Bool[N.i()];                                                                          // New used slots distribution
        final Int         p = new Int(remainder.Down());                                                                // Start position for first used slot
        new For(N)                                                                                                      // Redistribute slots
         {void body(Int i, Bool C)                                                                                      // Initialize background of slots
           {s[i.i()] = new Int (0);
            u[i.i()] = new Bool().clear();
            C.set();
           };
         };
        new For(N)                                                                                                      // Redistribute slots
         {void body(Int i, Bool C)
           {final Slot I = new Slot(i);
            new If (usedSlots(I))                                                                                       // Redistribute active slots
             {void Then()
               {s[p.i()] = new Int(slots(I).value());
                u[p.i()].set();
                p.add(space).inc();                                                                                     // Spread the used slots out
               }
             };
            C.set();
           }
         };

        memory.usedSlotsBits.initialize();                                                                              // Clear the existing tree bits - faster than deleting each path in turn

        new For(N)                                                                                                      // Copy redistribution back into original avoiding use of java array methods to make everything explicit for hardware conversion
         {void body(Int i, Bool C)
           {final Slot I = new Slot(i);
            slots(I, new slot(s[i.i()].i()));
            usedSlots(I,      u[i.i()]);
            C.set();
           }
         };
       }
     };
   }

  void reset()                                                                                                          // Reset the slots
   {new For(numberOfSlots()) {void body(Int i, Bool C) {slots(new Slot(i), new slot(0)); C.set();}};
    new For(numberOfRefs() ) {void body(Int i, Bool C) {  key(new slot(i), new Key(0));  C.set();}};

    initialize();                                                                                                       // Clear the existing tree bits - faster than deleting each path in turn
   }

  void compactSlot(Slot P, slot Q, Key K)                                                                               // Compact a slot
   {usedSlots(P, new Bool(true));
     usedRefs(Q, new Bool(true));
        slots(P, Q);
         keys(P, K);
   }

  void compactLeft()                                                                                                    // Compact the used slots to the left end
   {new If (empty().Flip())                                                                                             // Something to compact
     {void Then()
       {final Slots d = duplicateSlots();
        reset();
        final Int p = new Int(0);
        new For(numberOfSlots())                                                                                        // Each slot
         {void body(Int i, Bool C)
           {final Slot I = new Slot(i);
            new If (d.usedSlots(I))                                                                                     // Each used slot
             {void Then()
               {compactSlot(new Slot(p.i()), new slot(p.i()), d.keys(I));
                p.inc();
               }
             };
            C.set();
           }
         };
       }
     };
   }

  void compactRight()                                                                                                   // Compact the used slots to the left end
   {new If (empty().Flip())                                                                                             // Something to compact
     {void Then()
       {final Slots d = duplicateSlots(); reset();
        final Int p = new Int(numberOfRefs()-1);
        final Int N = new Int(numberOfSlots());
        new For(N)
         {void body(Int i, Bool C)
           {final Slot I = new Slot(N.Sub(i).dec());
            new If (d.usedSlots(I))                                                                                     // Each used slot
             {void Then()
               {compactSlot(new Slot(p.i()), new slot(p.i()), d.keys(I));
                p.dec();
               }
             };
            C.set();
           }
         };
       }
     };
   }

  Bool mergeSlot(Slots S, Slot I, slot J)                                                                               // Merge a slot
   {final Bool m = new Bool().clear();                                                                                  // Whether a successful merge occurred
    new If (S.usedSlots(I))
     {void Then()
       {    slots(I, S.    slots(I));
        usedSlots(I, S.usedSlots(I));
         usedRefs(J, S. usedRefs(J));
             keys(I, S.     keys(I));
        m.set();
       }
     };
    return m;
   }

  void mergeCompacted(Slots Left, Slots Right)                                                                          // Merge left and right compacted slots into the current slots
   {final Slots l = Left, r = Right;
    reset();
    new For(numberOfRefs())                                                                                             // Each reference
     {void body(Int i, Bool C)
       {final Slot I = new Slot(i);                                                                                     // The input slots have been compacted so this Slot will match the corresponding slot
        final slot J = new slot(i);
        final Bool c = new Bool().set();                                                                                // Continue until false
        c.and(()->{return mergeSlot(l, I, J).flip();});                                                                 // Merge on left
        c.and(()->{return mergeSlot(r, I, J).flip();});                                                                 // Merge on right
        new If (c)                                                                                                      // Reset center
         {void Then()
           {usedSlots(I, new Bool(false));
            usedRefs (J, new Bool(false));
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
   {return countUsed().Add(Right.countUsed()).gt(numberOfSlots()).b() ?
           new Bool(false) :
           mergeBack(duplicateSlots(), Right.duplicateSlots());
   }

  Bool mergeOnLeft(Slots Left)                                                                                          // Merge the specified slots from the left
   {return Left.countUsed().Add(countUsed()).gt(numberOfSlots()).b() ?
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
           {final Slot S = new Slot(new Int(numberOfSlots()).down().i());
            slots     (S, alloc);
            usedSlots (S, new Bool(true));
           }
          void Else()
           {new If (l.above())                                                                                          // Insert their key above the found key
             {void Then()
               {final Int i = l;
                final Int w = new Int(locateNearestFreeSlot(l));                                                        // Width of move and direction needed to liberate a slot here - we know there is one because we know the slots are not full
                new If (w.gt(0))                                                                                        // Move up
                 {void Then()                                                                                           // Move up
                   {shift             (i.Inc(), w.Dec());                                                               // Liberate a slot at this point
                    slots    (new Slot(i).right(), alloc);                                                              // Place their current key in the empty slot, it has already been marked as set so there is no point in setting it again
                    usedSlots(new Slot(i).right(), new Bool(true));
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
                    final Int w = new Slot(locateNearestFreeSlot(l));                                                   // Width of move and direction needed to liberate a slot here - we know there is one because we know the slots are not full
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
                            usedSlots(new Slot(i).left(), new Bool(true));                                              // Mark the free slot at the start of the range of occupied slots as now in use
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
             {new For(numberOfSlots())                                                                                  // Perform a reasonable number of searches knowing the key, if it is present, is within the current range. NB this is not a linear search, the slots are searched using binary search with an upper limit that has fooled some reviewers into thinking that a linear search is being performed.
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
                         "the maximum number of times:", numberOfSlots()));
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

//D2 Print                                                                                                              // Print the slots

  String printSlots()                                                                                                   // Print the occupancy of each slot
   {final StringBuilder s = new StringBuilder();
    for (int i : range(numberOfSlots()))
     {s.append(usedSlots(new Slot(i)).b() ? "X" : ".");
     }
    return ""+s;
   }

  public String toString()                                                                                              // Dump the slots
   {final StringBuilder s = new StringBuilder();
    final int[]N = range(numberOfSlots());
    final int[]R = range(numberOfRefs());
    s.append(f("Slots    : name: %2d, type: %2d, refs: %2d\n",                                                          // Title line
                            name().i(), type().i(), numberOfRefs));
    s.append("positions: ");   for (int i : N) s.append(f(" "+formatKey, i));
    s.append("\nslots    : "); for (int i : N) s.append(f(" "+formatKey, slots(new Slot(i)).i()));
    s.append("\nusedSlots: "); for (int i : N) s.append(             usedSlots(new Slot(i)).b() ? "   X" : "   .");
    s.append("\nusedRefs : "); for (int i : R) s.append(             usedRefs (new slot(i)).b() ? "   X" : "   .");
    s.append("\nkeys     : "); for (int i : R) s.append(f(" "+formatKey,   key(new slot(i)).valid().b() ? key(new slot(i)).i() : 0));
    return ""+s+"\n";
   }

  String printInOrder()                                                                                                 // Print the values in the used slots in order
   {final StringJoiner s = new StringJoiner(", ");
    for (int i : range(numberOfSlots()))
     {if (usedSlots(new Slot(i)).b()) s.add(""+keys(new Slot(i)).i());
     }
    return ""+s;
   }

//D2 Memory                                                                                                             // Read and write from an array of bytes
*/
  class SlotsMemoryPositions                                                                                            // Positions of fields in memory
   {final int N = numberOfSlots();
    final int R = numberOfRefs();

    final BitSet.Build us = new BitSet.Build().bitSize(N).one(true).zero(true);                                         // Specification of bit set for used slots
    final BitSet.Build ur = new BitSet.Build().bitSize(R).one(true).zero(true);                                         // Specification of bit set for references

    final int posSlots     = 0;                                                                                         // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
    final int posUsedSlots = posSlots     + ib(N);                                                                      // Slots in use
    final int posUsedRefs  = posUsedSlots + us.byteSize();                                                              // References in use.  There are fewer references than slots to make insertions faster
    final int posKeys      = posUsedRefs  + ur.byteSize();                                                              // Keys used in btree held unordered in this array but ordered by the slot refernces rto them
    final int size         = posKeys      + ib(N);                                                                      // Size of slots
  }
/*
  class Memory extends SlotsMemoryPositions                                                                             // Memory required to hold bytes
  {final ByteBuffer bytes;                                                                                              // Bytes used by this set of slots

    final BitSet usedSlotsBits = new BitSet(us)                                                                         // Bit storage for used slots
    {void setByte(int I, int V) {bytes.put(new Int(posUsedSlots).Add(I).i(), (byte)V);}                                 // Save used slot bit
      int  getByte(int I) {return bytes.get(new Int(posUsedSlots).Add(I).i());}                                         // Get used slot bit
    };

    final BitSet usedRefsBits  = new BitSet(ur)                                                                         // Bit storage for used refs
    {void setByte(int I, int V) {bytes.put(new Int(posUsedRefs).Add(I).i(), (byte)V);}                                  // Save used ref bit
      int  getByte(int I) {return bytes.get(new Int(posUsedRefs).Add(I).i());}                                          // Get used ref bit
    };

    void copySlots(Memory Memory)                                                                                       // Copy a set of slots from the specified memory into this memory
    {new For(size)
      {void body(Int i, Bool C)
        {bytes.put(i.i(), Memory.bytes.get(i.i()));
          C.set();
        }
      };
    }

    void invalidate()                                                                                                   // Invalidate the slots in such a way that they are unlikely to work well if subsequently used
    {new For(size)
      {void body(Int i, Bool C)
        {bytes.put(i.i(), (byte)-1);
          C.set();
        }
      };
    }

    void clear()                                                                                                        // Clear all bytes in memory to zero which has the beneficial effect of setting all slots to unused
    {new For(size)
      {void body(Int i, Bool C)
        {bytes.put(i.i(), (byte)0);
          C.set();
        }
      };
    }

    Memory()                 {bytes = ByteBuffer.allocate(size);}                                                       // Create memory
    Memory(ByteBuffer Bytes) {bytes = Bytes;}                                                                           // Use a specified memory

    BitSet.Pos   us(Int I) {return usedSlotsBits.new Pos(I);}                                                           // A slot position in the used slots
    BitSet.Pos   ur(Int I) {return usedRefsBits .new Pos(I);}                                                           // A slot position in the references

    Bool  usedSlots(Int I) {return usedSlotsBits.getBit(us(I));}                                                        // Value of indexed used slot
    Bool   usedRefs(Int I) {return usedRefsBits .getBit(ur(I));}                                                        // Value of indexed used reference
    Int       slots(Int I) {return new Int(bytes.getInt(new Int(posSlots).add(ib(I)).i()));}                            // Value of indexed slot
    Int        keys(Int I) {return new Int(bytes.getInt(new Int(posKeys) .add(ib(I)).i()));}                            // Value of key via indexed reference
    Int        name(     ) {return new Int(bytes.getInt(posName));}

    void usedSlots(Int I, Bool V) {usedSlotsBits.set(                 us(I),      V);}                                  // Set value of indexed used slot
    void  usedRefs(Int I, Bool V) {usedRefsBits .set(                 ur(I),      V);}                                  // Set value of indexed used reference
    void     slots(Int I, Int  V) {bytes.putInt(new Int(posSlots).Add(ib(I)).i(), V.i());}                              // Set value of indexed slot
    void      keys(Int I, Int  V) {bytes.putInt(new Int(posKeys ).Add(ib(I)).i(), V.i());}                              // Set value of key via indexed reference
    void      name(       Int  V) {bytes.putInt(posName,                          V.i());}                              // Save the name of the node in memory to assist debugging

    void      type(Int Type) {       bytes.putInt(posType, Type.i());}                                                  // Type of object in which the slots are embedded
    Int       type() {return new Int(bytes.getInt(posType));}
   }
*/

//D1 Tests                                                                                                              // Tests

//D2 Slots                                                                                                              // Test the slots

  static void test_slots()
   {final Slots s = new Slots(8);
    s.refSlots    .putInt(s.new Int(0), s.new Int(11));
    s.refSlots    .putInt(s.new Int(1), s.new Int(22));
    s.refUsedSlots.putInt(s.new Int(0), s.new Int(33));
    s.refUsedSlots.putInt(s.new Int(1), s.new Int(44));
    s.refUsedRefs .putInt(s.new Int(0), s.new Int(55));
    s.refUsedRefs .putInt(s.new Int(1), s.new Int(66));
    s.refKeys     .putInt(s.new Int(0), s.new Int(77));
    s.refKeys     .putInt(s.new Int(1), s.new Int(88));
    s.refKeys     .putInt(s.new Int(2), s.new Int(99));
    s.refKeys     .putInt(s.new Int(3), s.new Int(111));
    //stop(s.byteMemory.toString());
    //stop(md5Sum(s.byteMemory.toString()));
    ok(md5Sum(s.byteMemory.toString()), "30cfe9584d6f5c1131ea30a24d4c2664");
   }

  static void test_slots2()
   {final Slots s = new Slots(8);
    //s.setSlots(2, 3, 5, 6, 7, 9, 11, 13);
   }
/*
  static void test_locateNearestFreeSlot()
   {final Slots s = new Slots(8);
    s.setSlots(2, 3, 5, 6, 7, 9, 11, 13);
                      //0123456789012345
    ok(s.printSlots(), "..XX.XXX.X.X.X..");
    ok(s.locateNearestFreeSlot(s.new Slot( 0)),  0);
    ok(s.locateNearestFreeSlot(s.new Slot( 1)),  0);
    ok(s.locateNearestFreeSlot(s.new Slot( 2)), -1);
    ok(s.locateNearestFreeSlot(s.new Slot( 3)), +1);
    ok(s.locateNearestFreeSlot(s.new Slot( 4)),  0);
    ok(s.locateNearestFreeSlot(s.new Slot( 5)), -1);
    ok(s.locateNearestFreeSlot(s.new Slot( 6)), -2);
    ok(s.locateNearestFreeSlot(s.new Slot( 8)),  0);
    ok(s.locateNearestFreeSlot(s.new Slot( 9)), -1);
    ok(s.locateNearestFreeSlot(s.new Slot(10)),  0);
    ok(s.locateNearestFreeSlot(s.new Slot(11)), -1);
    ok(s.locateNearestFreeSlot(s.new Slot(12)),  0);
    ok(s.locateNearestFreeSlot(s.new Slot(13)), -1);
    ok(s.locateNearestFreeSlot(s.new Slot(14)),  0);
    ok(s.locateNearestFreeSlot(s.new Slot(15)),  0);

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
    for (int i : range(s.numberOfSlots()))
     {final Slots.Slot S = s.new Slot(i);
      s.usedSlots(S);
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
usedSlots:    .   .   .   .   .   X   X   X   X   X   X   X   X   .   .   .
usedRefs :    X   X   X   X   X   X   X   X
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

    for (int i = 0; i < s.numberOfSlots()*10; i++)
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

    s.usedSlots(s.new Slot( 1), t.new Bool(true)); s.slots(s.new Slot( 1), s.new slot(7)); s.usedRefs(s.new slot(7), t.new Bool(true)); s.key(s.new slot(7), t.new Key(22));
    s.usedSlots(s.new Slot( 5), t.new Bool(true)); s.slots(s.new Slot( 5), s.new slot(4)); s.usedRefs(s.new slot(4), t.new Bool(true)); s.key(s.new slot(4), t.new Key(24));
    s.usedSlots(s.new Slot( 9), t.new Bool(true)); s.slots(s.new Slot( 9), s.new slot(2)); s.usedRefs(s.new slot(2), t.new Bool(true)); s.key(s.new slot(2), t.new Key(26));
    s.usedSlots(s.new Slot(14), t.new Bool(true)); s.slots(s.new Slot(14), s.new slot(0)); s.usedRefs(s.new slot(0), t.new Bool(true)); s.key(s.new slot(0), t.new Key(28));
    ok(s, """
Slots    : name:  0, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   4   0   0   0   2   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   .   X   .
usedRefs :    X   .   X   .   X   .   .   X
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

    s.usedSlots(s.new Slot( 1), t.new Bool(true));; s.slots(s.new Slot( 1), s.new slot(7)); s.usedRefs(s.new slot(7), t.new Bool(true));; s.key(s.new slot(7), t.new Key(11));
    s.usedSlots(s.new Slot( 5), t.new Bool(true));; s.slots(s.new Slot( 5), s.new slot(4)); s.usedRefs(s.new slot(4), t.new Bool(true));; s.key(s.new slot(4), t.new Key(12));
    s.usedSlots(s.new Slot( 9), t.new Bool(true));; s.slots(s.new Slot( 9), s.new slot(2)); s.usedRefs(s.new slot(2), t.new Bool(true));; s.key(s.new slot(2), t.new Key(13));
    s.usedSlots(s.new Slot(14), t.new Bool(true));; s.slots(s.new Slot(14), s.new slot(0)); s.usedRefs(s.new slot(0), t.new Bool(true));; s.key(s.new slot(0), t.new Key(14));
    ok(s, """
Slots    : name:  0, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   4   0   0   0   2   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   .   X   .
usedRefs :    X   .   X   .   X   .   .   X
keys     :   14   0  13   0  12   0   0  11
""");
    s.compactLeft();

    ok(s, """
Slots    : name:  0, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   1   2   3   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    X   X   X   X   .   .   .   .   .   .   .   .   .   .   .   .
usedRefs :    X   X   X   X   .   .   .   .
keys     :   11  12  13  14   0   0   0   0
""");
   }

  static void test_compactRight()
   {final Tree  t =   new Tree (8);
    final Slots s = t.new Slots(8);

    s.usedSlots(s.new Slot( 1), t.new Bool(true)); s.slots(s.new Slot( 1), s.new slot(7)); s.usedRefs(s.new slot(7), t.new Bool(true)); s.key(s.new slot(7), t.new Key(11));
    s.usedSlots(s.new Slot( 5), t.new Bool(true)); s.slots(s.new Slot( 5), s.new slot(4)); s.usedRefs(s.new slot(4), t.new Bool(true)); s.key(s.new slot(4), t.new Key(12));
    s.usedSlots(s.new Slot( 9), t.new Bool(true)); s.slots(s.new Slot( 9), s.new slot(2)); s.usedRefs(s.new slot(2), t.new Bool(true)); s.key(s.new slot(2), t.new Key(13));
    s.usedSlots(s.new Slot(14), t.new Bool(true)); s.slots(s.new Slot(14), s.new slot(0)); s.usedRefs(s.new slot(0), t.new Bool(true)); s.key(s.new slot(0), t.new Key(14));
    ok(s, """
Slots    : name:  0, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   4   0   0   0   2   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   .   X   .
usedRefs :    X   .   X   .   X   .   .   X
keys     :   14   0  13   0  12   0   0  11
""");
    s.compactRight();
    ok(s, """
Slots    : name:  0, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   4   5   6   7   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   X   X   X   X   .   .   .   .   .   .   .   .
usedRefs :    .   .   .   .   X   X   X   X
keys     :    0   0   0   0  11  12  13  14
""");

    ok(s.firstKey().i(), 11);
    ok(s. lastKey().i(), 14);
   }

  static void test_memory()
   {final Tree  t =   new Tree (8);
    final Slots s = t.new Slots(8, ByteBuffer.allocate(200));

    s.usedSlots(s.new Slot( 1), t.new Bool(true)); s.slots(s.new Slot( 1), s.new slot(7)); s.usedRefs(s.new slot(7), t.new Bool(true)); s.key(s.new slot(7), t.new Key(11));
    s.usedSlots(s.new Slot( 5), t.new Bool(true)); s.slots(s.new Slot( 5), s.new slot(4)); s.usedRefs(s.new slot(4), t.new Bool(true)); s.key(s.new slot(4), t.new Key(12));
    s.usedSlots(s.new Slot( 9), t.new Bool(true)); s.slots(s.new Slot( 9), s.new slot(2)); s.usedRefs(s.new slot(2), t.new Bool(true)); s.key(s.new slot(2), t.new Key(13));
    s.usedSlots(s.new Slot(14), t.new Bool(true)); s.slots(s.new Slot(14), s.new slot(0)); s.usedRefs(s.new slot(0), t.new Bool(true)); s.key(s.new slot(0), t.new Key(14));
    s.type     (t.new Int (11));
    ok(s, """
Slots    : name:  0, type: 11, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   4   0   0   0   2   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   .   X   .
usedRefs :    X   .   X   .   X   .   .   X
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
    ok(m.usedSlots   (t.new Int(0)), false);
    ok(m.usedSlots   (t.new Int(1)), true);
    ok(m.usedSlots   (t.new Int(2)), false);
    ok(m.usedSlots   (t.new Int(3)), false);
    ok(m.usedSlots   (t.new Int(4)), false);
    ok(m.usedSlots   (t.new Int(5)), true);
    ok(m.usedSlots   (t.new Int(6)), false);
    ok(m.usedRefs    (t.new Int(0)), true);
    ok(m.usedRefs    (t.new Int(1)), false);
    ok(m.usedRefs    (t.new Int(2)), true);
    ok(m.usedRefs    (t.new Int(3)), false);
    ok(m.usedRefs    (t.new Int(4)), true);
    ok(m.usedRefs    (t.new Int(5)), false);
    ok(m.usedRefs    (t.new Int(6)), false);
    ok(m.keys        (t.new Int(0)), 14);
    ok(m.keys        (t.new Int(1)),  0);
    ok(m.keys        (t.new Int(2)), 13);
    ok(m.keys        (t.new Int(3)),  0);
    ok(m.keys        (t.new Int(4)), 12);
    ok(m.keys        (t.new Int(5)),  0);
    ok(m.keys        (t.new Int(6)),  0);

    m.slots    (t.new Int(13), t.new Int(6));
    m.usedSlots(t.new Int(13), t.new Bool(true));
    m.usedRefs (t.new Int( 6), t.new Bool(true));
    m.keys     (t.new Int( 6), t.new Int(10));

    ok(B, """
Slots    : name:  0, type: 11, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   4   0   0   0   2   0   0   0   6   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   X   X   .
usedRefs :    X   .   X   .   X   .   X   X
keys     :   14   0  13   0  12   0  10  11
""");
    ok(B.type(), 11);
   }
*/
  static void oldTests()                                                                                                // Tests thought to be in good shape
   {////test_locateNearestFreeSlot();
    //test_redistribute();
    //test_ifd();
    //test_idn();
    //test_tooManySearches();
    //test_locateFirstGeKey();
    //test_compactLeft();
    //test_compactRight();
    //test_memory();
   }

  static void newTests()                                                                                                // Tests being worked on
   {//oldTests();
    test_slots();
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
