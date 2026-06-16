//----------------------------------------------------------------------------------------------------------------------
// Leaf of a btree implemented using distributed sparse slots
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;

class Leaf extends Program implements Program.Locatable                                                                 // A leaf in a btree that translates keys into values to be implemented as an application specific integrated circuit
 {final int            maxSize;                                                                                         // The maximum number of entries in a leaf of the tree
  final Slots          slots;                                                                                           // Slots used to order keys in leaf
  ByteMemory.Ref       byteMemoryRef = null;                                                                            // Byte memory reference containing the tree
  final Bint           at            = new Bint();                                                                      // An optional representation of the location of the leaf sufficient to be able to free it
  final ByteMemory.Ref refMark;                                                                                         // Mark this node as a leaf
  final ByteMemory.Ref refSlots;                                                                                        // The slot associated with each key being used
  final ByteMemory.Ref refData;                                                                                         // Bitset showing which slots are being mapped to keys
  final Build          build;                                                                                           // Build used to construct this leaf

//D1 Construction                                                                                                       // Construct and layout a leaf

  final static class Build                                                                                              // Parameters describing a leaf
   {Integer         maxSize;                                                                                            // Maximum number of keys in leaf
    Int             at;                                                                                                 // The location of the leaf
    boolean         immediate = true;                                                                                   // Immediate execution mode
    boolean         trace     = true;                                                                                   // Trace execution
    Program         parent;                                                                                             // Parent program if any
    ByteMemory.Ref  byteMemoryRef;                                                                                      // Program memory to be used
    MemoryPositions memoryPositions;                                                                                    // Layout of memory
    Slots.Build     slots;                                                                                              // Bytes needed for slots

    Build immediate(boolean Immediate ) {immediate     = Immediate; return this;}
    Build maxSize  (int     MaxSize   ) {maxSize       = MaxSize;   return this;}
    Build memory   (ByteMemory.Ref Ref) {byteMemoryRef = Ref;       return this;}
    Build parent   (Program Parent    ) {parent        = Parent;    return this;}
    Build trace    (boolean Trace     ) {trace         = Trace;     return this;}
    Build at       (Int     At        ) {at            = At;        return this;}

    Program.Build build()                                                                                               // Create a description of the needed containing program
     {final Program.Build p = new Program.Build();                                                                      // Description of containing program
      final Slots.Build   s = slots = new Slots.Build().numberOfKeys(maxSize);
      final Program.Build S = s.build();                                                                                // Has the side effect of computing the size of the slots
      memoryPositions       = new MemoryPositions();
      if (byteMemoryRef == null) p.memory(size());
      if (parent        != null) p.parent(parent);
      p.immediate(immediate);
      p.trace    (trace);
      return p;
     }

    final class MemoryPositions                                                                                         // Layout of memory
     {final int posMark  = 0;                                                                                           // A tree consists of nodes: leaves and branches. This field tells us which one we have
      final int posSlots = posMark  + ib();
      final int posData  = posSlots + slots.size();
      final int size     = posData  + dataBytes();
     }

    int size()      {return memoryPositions.size;}                                                                      // Bytes needed for the slots
    int dataBytes() {return ib(maxSize);}                                                                               // Bytes needed for the data
   }

  Leaf(Build Build)                                                                                                     // Create a description of a leaf
   {super(Build.build());                                                                                               // Program for leaf
    build         = Build;
    maxSize       = Build.maxSize;
    if (maxSize % 2 == 1) stop("MaxSize should be even not odd:", maxSize);                                             // Not strictly true but slightly easier to cope with
    if (maxSize < 2)      stop("MaxSize must be at least 2:",     maxSize);
    final Build.MemoryPositions m = build.memoryPositions;
    byteMemoryRef = Build.byteMemoryRef != null ? Build.byteMemoryRef : byteMemory.new Ref(0);                          // Either a reference to some memory has been supplied or create a reference to some locally allocated memory to contain the bitset
    refMark       = byteMemoryRef.step(m.posMark);                                                                      // Mark this node as a leaf or a branch
    refSlots      = byteMemoryRef.step(m.posSlots);                                                                     // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
    refData       = byteMemoryRef.step(m.posData);                                                                      // Slots in use
    if (build.at != null) at.set(build.at);                                                                             // The location of the leaf if supplied
    slots         = new Slots(new Slots.Build().numberOfKeys(maxSize).memory(refSlots).parent(program()));              // Slots for leaf
    leafCode();                                                                                                         // Generate machine code if any assembler code has been supplied
   }

  Leaf initializeMemory()                                                                                               // Initialize slots and data associated with the leaf
   {clear();                                                                                                            // Clear backing memory
    slots.initializeMemory();                                                                                           // Initialize slots
    return this;
   }
  void leafCode() {}                                                                                                    // Override this method to provide code for testing the leaf

  public Bint getLocation() {return at;}                                                                                 // The location of this node in memory

  Bool empty()   {return slots.empty();}                                                                                // Is the leaf empty
  Bool full ()   {return slots.full ();}                                                                                // Is the leaf full
  Int  count()   {return slots.count();}                                                                                // Number of key/data pairs in the leaf
  int  maxSize() {return maxSize;}                                                                                      // Number of key/data pairs in the leaf

  Int  data(Int Index)            {return refData.getInt(Index);}                                                       // Get data at an index
  void data(Int Index, Int Value) {refData.putInt(Index, Value);}                                                       // Set the data at the specified index

  int bytesNeeded() {return build.size();}                                                                              // Number of bytes needed to contain a leaf
  void      clear() {byteMemoryRef.clear(bytesNeeded());}                                                               // Clear memory associated with the leaf and mark as a leaf to create a new leaf in a known state ready for use

  void copy (Leaf Source) {byteMemoryRef.copy(Source.byteMemoryRef, bytesNeeded());}                                    // Copy one leaf into another leaf
  void invalidate()       {byteMemoryRef.invalidate(bytesNeeded());}                                                    // Invalidate a leaf so that it will probably cause errors if an attempt is made to reuse it with it initializing it first

//D1 Delete, find, insert                                                                                               // Delete, find, insert keys and data in a leaf

  Bint find          (Int Key) {return getDataFromKey(Key, false);}                                                     // Get the data associated with a key
  Bint delete        (Int Key) {return getDataFromKey(Key, true);}                                                      // Get the data associated with a key and delete the key if it exists.  At this point we do not clean up the value corresponding to the key because the determination of whether the value is valid or not is done solely in the slots and, as there is no preferred value to set into the values array to mark it as not in use, it is sufficient to leave the existing value there.

  Bint getDataFromKey(Int Key, boolean Delete)                                                                          // Get the data associated with a key with the option of deleting the key if found
   {final Slots.Find f = slots.find(Key);                                                                               // Find the key
    final Bint       r = new Bint();                                                                                    // Result
    new If (f.equal)                                                                                                    // Found the key
     {void Then()
       {r.set(refData.getInt(slots.getSlotToKeyIndex(f.slot.i())));                                                     // Get data associated with key
        if (Delete) slots.delete(f.slot.i());                                                                           // Delete the key if requested
       }
     };
    return r;                                                                                                           // Return data associated with key
   }

  Slots.Insert insert(Int Key, Int Data)                                                                                // Insert a key data pair into a leaf returning the index of the containing slot
   {if (immediate() && slots.find(Key).equal.b()) stop("Key already exists in leaf:", Key, print());                    // The key must not already be present
    final Slots.Insert i = slots.insert(Key);                                                                           // Insert key
    final Int          k = slots.getSlotToKeyIndex(i.slot.i());                                                         // Key into which the insertion was performed
    refData.putInt(k, Data);
    return i;                                                                                                           // Return the slot in the leaf in which the key, data pair was inserted
   }

//D1 Compact, Split, Merge                                                                                              // Compact, split or merge leaves
//D2 Compact                                                                                                            // Compact a leaf to the left or right

  void compactLeft()                                                                                                    // Compact a leaf to the left
   {slots.compactSlotsLeft();                                                                                           // Compact the slots to match
    slots.compactKeysLeft((S, t, s)->{refData.putInt(t, refData.getInt(s));});                                          // Compact the slots to match
   }

  void compactRight()                                                                                                   // Compact a leaf to the right
   {slots.compactSlotsRight();                                                                                          // Compact the slots to match
    slots.compactKeysRight((S, t, s)->{refData.putInt(t, refData.getInt(s));});                                         // Compact the slots to match
   }

//D2 Split                                                                                                              // Split a full leaf into two leaves

  private Int splittingKey()                                                                                            //N Splitting key for a leaf assuming that the leaf has been compacted to the right
   {if (immediate() && count().i() != maxSize()) stop("Leaf not full");                                                 // The leaf must be full
    final Int l = slots.getSlotToKeyValue(new Int(maxSize/2-1));
    final Int r = slots.getSlotToKeyValue(new Int(maxSize/2  ));
    return l.add(r).down();                                                                                             // Splitting key
   }

  Int splitRight(Leaf Right)                                                                                            // Split a full leaf rightwards into a supplied leaf and return the splitting key value
   {if (immediate() && count().i() != maxSize()) stop("Leaf not full");                                                 // The leaf must be full
    final Leaf left = this;                                                                                             // Current leaf is on the left
    Right.slots.clear();                                                                                                // Clear target
    Right.refData.copy(left.refData, build.dataBytes());                                                                // Copy data - the positions of the keys is not changed by a split so the original key,data positions are still in effect after the copy
    return left.slots.splitRightEven(Right.slots);                                                                      // Split the slots
   }

  Int splitLeft(Leaf Left)                                                                                              // Split a full leaf leftwards into a supplied leaf and return the splitting key value
   {if (immediate() && count().i() != maxSize()) stop("Leaf not full");                                                 // The leaf must be full
    final Leaf right = this;                                                                                            // Current leaf is on the right
    Left.slots.clear();                                                                                                 // Clear target
    Left.refData.copy(right.refData, build.dataBytes());                                                                // Copy data - the positions of the keys is not changed by a split so the original key,data positions are still in effect after the copy
    return right.slots.splitLeftEven(Left.slots);                                                                              // Split the slots
   }

//D2 Merge                                                                                                              // Merge two leaves

  private void copyMergeData(Leaf Source, Int Start, Int End)                                                           // Copy the data values directly in the specified key range from the specified source and place them in the exact same position in the target
   {new ForCount (Start, End)
     {void body(Int Index)
       {data(Index, Source.data(Index));                                                                                // The keys have been compacted left and right so we can copy them from the source position into the same position in the target without collisions
       }
     };
   }

  Bool mergeRight(Leaf Right)                                                                                           //N Merge the specified leaf into the right of this leaf
   {final Leaf left = this;
    final Int  lc   = left .count();
    final Int  rc   = Right.count();
    final Bool r    = new Bool().clear();

    new If (lc.Add(rc).le(maxSize()))
     {void Then()
       {r.set();
        left .compactLeft();    Right.compactRight();                                                                   // Compact so both the slots and keys are in opposing extremal positions to avoid collisions when we merge
        left .slots.compactKeysLeft ((S, t, s)->{left .data(t, left .data(s));});
        Right.slots.compactKeysRight((S, t, s)->{Right.data(t, Right.data(s));});

        left.copyMergeData(Right, new Int(maxSize).sub(rc), new Int(maxSize()));                                        // Copy the right data values into the left data values
        left.slots.mergeFromRightEven(Right.slots);                                                                     // Merge the slots
       }
     };
    return r;
   }

  Bool mergeLeft(Leaf Left)                                                                                             // Merge the leaf into the right of this leaf
   {final Leaf right = this;
    final Int  lc    = Left .count();
    final Int  rc    = right.count();
    final Bool r     = new Bool().clear();

    new If (lc.Add(rc).le(maxSize()))
     {void Then()
       {r.set();
        Left .compactLeft();    right.compactRight();                                                                   // Compact so both the slots and keys are in opposing extremal positions to avoid collisions when we merge
        Left .slots.compactKeysLeft ((S, t, s)->{Left .data(t, Left .data(s));});
        right.slots.compactKeysRight((S, t, s)->{right.data(t, right.data(s));});

        right.copyMergeData(Left, new Int(0), lc);                                                                      // Copy the data values associated with the slots
        right.slots.mergeFromLeftEven(Left.slots);                                                                      // Merge the slots
       }
     };
    return r;
   }

  int splitSize() {return maxSize()>>>1;}                                                                               // Size of a split leaf

//D1 Iterate                                                                                                            // Iterate over a leaf

  interface Iterator                                                                                                    // Process a key,data pair when iterating over the leaf
   {void process(Int Key, Int Data);
   }

  void iterate(Iterator Iterator)                                                                                       // Iterate over a leaf
   {final Bint f = slots.usedSlotsToKeys.firstOne();
    new If (f.valid())
     {void Then()
       {new For(maxSize)
         {void body(Int Index, Bool Continue)
           {final Int i = f.i();
            final Int k = slots.getSlotToKeyValue(i), d = data(slots.getSlotToKeyIndex(i));
            new I() {void a() {Iterator.process(k, d);}};
            f.copy(slots.usedSlotsToKeys.nextOne(i));
            Continue.set(f.valid());
           }
         };
       }
     };
   }

//D1 Print                                                                                                              // Print the leaf

  StringBuilder print()                                                                                                 // Print a leaf
   {final StringBuilder s = new StringBuilder();
    new I() {void a() {s.setLength(0); s.append(f("Leaf  "));}};
    final Bint l = getLocation();

    new If (l)
     {void Then()
       {final Int i = new Int().set(l);
        new If (i.gt(0))
         {void Then()                                                                                                   // Print the index if it is not the root
           {new I() {void a() {s.append(f(" at: %3d", i.i())); }};
           }
          void Else()
           {new I() {void a() {s.append(" ".repeat(8)); }};
           }
         };
       }
      void Else()
       {new I() {void a() {s.append(" ".repeat(8)); }};
       }
     };

    new I()                                                                                                             // Key/Data pairs in key order
     {void a()
       {s.append(f(" size: %2d, count: %2d\n", maxSize(), slots.refCount.getInt(0)));
        s.append(" Ref   Key  Data\n");

        for (int i : range(slots.numberOfSlotsToKeys()))
         {if (slots.getSlotToKeysInUse(i))
           {final String f = "%4d  %4d  %4d\n";
            final int    k = slots.getSlotToKeyIndex(i);
            final String t = f(f, k, slots.getSlotToKeyValue(i), refData.getInt(k));
            s.append(t);
           }
         };
       }
     };
    return s;
   }

  public String toString() {return ""+print();}                                                                         // Print leaf

//D1 Tests                                                                                                              // Tests

  static void test_leaf(boolean Ex)
   {sayCurrentTestName();
    final Leaf l = new Leaf(new Build().maxSize(8).immediate(Ex));
    l.initializeMemory();
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    //testStop("AAAA", l.print());
    l.check(l.print(), """
Leaf           size:  8, count:  4
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
""");
    l.find  (l.new Int(1)).ok(11);
    l.find  (l.new Int(2)).ok(22);
    l.find  (l.new Int(3)).ok(33);
    l.find  (l.new Int(4)).ok(44);

    l.delete(l.new Int(1)).ok(11);
    l.delete(l.new Int(2)).ok(22);
    l.delete(l.new Int(3)).ok(33);
    l.delete(l.new Int(4)).ok(44);

    l.delete(l.new Int(1)).valid().ok(false);

    l.maxSteps = 99999;
    l.execute();
   }

  static void test_leaf()
   {          test_leaf(true);
              test_leaf(false);
   }

  static void test_compactLeft(boolean Ex)
   {sayCurrentTestName();
    final Leaf l = new Leaf(new Build().maxSize(8).immediate(Ex));
    l.initializeMemory();
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    l.delete(l.new Int(2));
    //l.new I() {void a() {testStop(l);}};
    l.check(l.print(), """
Leaf           size:  8, count:  3
 Ref   Key  Data
   3     1    11
   2     3    33
   1     4    44
""");
    l.compactLeft();
    //l.new I() {void a() {testStop(l);}};
    l.check(l.print(), """
Leaf           size:  8, count:  3
 Ref   Key  Data
   0     1    11
   2     3    33
   1     4    44
""");
    l.maxSteps = 99999;
    l.execute();
   }

  static void test_compactLeft()
   {          test_compactLeft(true);
              test_compactLeft(false);
   }

  static void test_compactRight(boolean Ex)
   {sayCurrentTestName();
    final Leaf l = new Leaf(new Build().maxSize(8).immediate(Ex));
    l.initializeMemory();
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    //l.new I() {void a() {testStop(l);}};
    l.check(l.print(), """
Leaf           size:  8, count:  4
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
""");
    l.compactRight();
    //l.new I() {void a() {testStop(l);}};
    l.check(l.print(), """
Leaf           size:  8, count:  4
 Ref   Key  Data
   4     1    11
   7     2    22
   5     3    33
   6     4    44
""");
    l.maxSteps = 99999;
    l.execute();
   }

  static void test_compactRight()
   {          test_compactRight(true);
              test_compactRight(false);
   }

  static void test_splitRight(boolean Ex)
   {sayCurrentTestName();
    final Leaf l = new Leaf(new Build().maxSize(8).immediate(Ex));
    l.initializeMemory();
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    l.insert(l.new Int(6), l.new Int(66));
    l.insert(l.new Int(7), l.new Int(77));
    l.insert(l.new Int(5), l.new Int(55));
    l.insert(l.new Int(8), l.new Int(88));
    //l.new I() {void a() {testStop(l);}};
    l.check(l.print(), """
Leaf           size:  8, count:  8
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
   6     5    55
   4     6    66
   5     7    77
   7     8    88
""");
    final Leaf r = new Leaf(new Build().maxSize(8).immediate(Ex).parent(l));
    r.initializeMemory();
    l.splitRight(r).ok(4);
    //l.new I() {void a() {testStop(l);}};
    l.check(l.print(), """
Leaf           size:  8, count:  4
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
""");
    //l.new I() {void a() {testStop(r);}};
    l.check(r.print(), """
Leaf           size:  8, count:  4
 Ref   Key  Data
   6     5    55
   4     6    66
   5     7    77
   7     8    88
""");
    l.maxSteps = 99999;
    l.execute();
   }

  static void test_splitRight()
   {          test_splitRight(true);
              test_splitRight(false);
   }

  static void test_splitLeft(boolean Ex)
   {sayCurrentTestName();
    final Leaf r = new Leaf(new Build().maxSize(8).immediate(Ex));
    r.initializeMemory();
    r.insert(r.new Int(2), r.new Int(22));
    r.insert(r.new Int(4), r.new Int(44));
    r.insert(r.new Int(3), r.new Int(33));
    r.insert(r.new Int(1), r.new Int(11));
    r.insert(r.new Int(6), r.new Int(66));
    r.insert(r.new Int(7), r.new Int(77));
    r.insert(r.new Int(5), r.new Int(55));
    r.insert(r.new Int(8), r.new Int(88));
    //r.new I() {void a() {testStop(r);}};
    r.check(r.print(), """
Leaf           size:  8, count:  8
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
   6     5    55
   4     6    66
   5     7    77
   7     8    88
""");
    final Leaf l = new Leaf(new Build().maxSize(8).immediate(Ex).parent(r));
    r.splitLeft(l).ok(4);
    //r.new I() {void a() {testStop(l);}};
    l.check(l.print(), """
Leaf           size:  8, count:  4
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
""");
    //r.new I() {void a() {testStop(r);}};
    r.check(r.print(), """
Leaf           size:  8, count:  4
 Ref   Key  Data
   6     5    55
   4     6    66
   5     7    77
   7     8    88
""");
    r.maxSteps = 99999;
    r.execute();
   }

  static void test_splitLeft()
   {          test_splitLeft(true);
              test_splitLeft(false);
   }

  static void test_mergeRight(boolean Ex)
   {sayCurrentTestName();
    final Leaf l = new Leaf(new Build().maxSize(8).immediate(Ex));
    l.initializeMemory();
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    l.insert(l.new Int(6), l.new Int(66));
    l.insert(l.new Int(7), l.new Int(77));
    l.insert(l.new Int(5), l.new Int(55));
    l.insert(l.new Int(8), l.new Int(88));
    l.check(l.print(), """
Leaf           size:  8, count:  8
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
   6     5    55
   4     6    66
   5     7    77
   7     8    88
""");
    final Leaf r = new Leaf(new Build().maxSize(8).immediate(Ex).parent(l));
    l.splitRight(r).ok(4);
    l.check(l.print(), """
Leaf           size:  8, count:  4
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
""");

    r.check(r.print(), """
Leaf           size:  8, count:  4
 Ref   Key  Data
   6     5    55
   4     6    66
   5     7    77
   7     8    88
""");

    l.mergeRight(r);
    l.check(l.print(), """
Leaf           size:  8, count:  8
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
   6     5    55
   4     6    66
   5     7    77
   7     8    88
""");
    l.maxSteps = 99999;
    l.execute();
   }

  static void test_mergeRight()
   {          test_mergeRight(true);
              test_mergeRight(false);
   }

  static void test_mergeLeft(boolean Ex)
   {sayCurrentTestName();
    final Leaf r = new Leaf(new Build().maxSize(8).immediate(Ex));
    r.initializeMemory();
    r.insert(r.new Int(2), r.new Int(22));
    r.insert(r.new Int(4), r.new Int(44));
    r.insert(r.new Int(3), r.new Int(33));
    r.insert(r.new Int(1), r.new Int(11));
    r.insert(r.new Int(6), r.new Int(66));
    r.insert(r.new Int(7), r.new Int(77));
    r.insert(r.new Int(5), r.new Int(55));
    r.insert(r.new Int(8), r.new Int(88));
   // r.new I() {void a() {testStop(r);}};
    r.check(r.print(), """
Leaf           size:  8, count:  8
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
   6     5    55
   4     6    66
   5     7    77
   7     8    88
""");
    final Leaf l = new Leaf(new Build().maxSize(8).immediate(Ex).parent(r));
    r.splitLeft(l);
    //r.new I() {void a() {testStop(l);}};
    l.check(l.print(), """
Leaf           size:  8, count:  4
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
""");
    //r.new I() {void a() {testStop(r);}};
    r.check(r.print(), """
Leaf           size:  8, count:  4
 Ref   Key  Data
   6     5    55
   4     6    66
   5     7    77
   7     8    88
""");
    r.mergeLeft(l);
    //r.new I() {void a() {testStop(r);}};
    r.check(r.print(), """
Leaf           size:  8, count:  8
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
   6     5    55
   4     6    66
   5     7    77
   7     8    88
""");
    r.maxSteps = 99999;
    r.execute();
   }

  static void test_mergeLeft()
   {          test_mergeLeft(true);
              test_mergeLeft(false);
   }

  static void test_find(boolean Ex)
   {sayCurrentTestName();
    final Leaf l = new Leaf(new Build().maxSize(8).immediate(Ex));
    l.initializeMemory();
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    l.insert(l.new Int(6), l.new Int(66));
    l.insert(l.new Int(7), l.new Int(77));
    l.insert(l.new Int(5), l.new Int(55));
    l.insert(l.new Int(8), l.new Int(88));
    l.check(l.print(), """
Leaf           size:  8, count:  8
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
   6     5    55
   4     6    66
   5     7    77
   7     8    88
""");
    l.find(l.new Int(0)).notValid().ok(true);
    l.find(l.new Int(1)).ok(11);
    l.find(l.new Int(2)).ok(22);
    l.find(l.new Int(3)).ok(33);
    l.find(l.new Int(4)).ok(44);
    l.find(l.new Int(5)).ok(55);
    l.find(l.new Int(6)).ok(66);
    l.find(l.new Int(7)).ok(77);
    l.find(l.new Int(8)).ok(88);
    l.find(l.new Int(9)).notValid().ok(true);

    l.delete(l.new Int(2)).ok(22); l.find(l.new Int(2)).notValid().ok(true); l.count().ok(7);
    l.delete(l.new Int(1)).ok(11); l.find(l.new Int(1)).notValid().ok(true); l.count().ok(6);
    l.delete(l.new Int(4)).ok(44); l.find(l.new Int(4)).notValid().ok(true); l.count().ok(5);
    l.delete(l.new Int(3)).ok(33); l.find(l.new Int(3)).notValid().ok(true); l.count().ok(4);
    l.delete(l.new Int(8)).ok(88); l.find(l.new Int(8)).notValid().ok(true); l.count().ok(3);
    l.delete(l.new Int(6)).ok(66); l.find(l.new Int(6)).notValid().ok(true); l.count().ok(2);
    l.delete(l.new Int(7)).ok(77); l.find(l.new Int(7)).notValid().ok(true); l.count().ok(1);
    l.delete(l.new Int(5)).ok(55); l.find(l.new Int(5)).notValid().ok(true); l.count().ok(0);
    l.check(l.print(), """
Leaf           size:  8, count:  0
 Ref   Key  Data
""");

    l.maxSteps = 99999;
    l.execute();
   }

  static void test_find()
   {          test_find(true);
              test_find(false);
   }

  static void test_iterate(boolean Ex)
   {sayCurrentTestName();
    new Leaf(new Build().maxSize(8).immediate(Ex))
     {@Override void leafCode()
       {initializeMemory();
        insert(new Int(2), new Int(22));
        insert(new Int(4), new Int(44));
        insert(new Int(3), new Int(33));
        insert(new Int(1), new Int(11));
        insert(new Int(6), new Int(66));
        insert(new Int(7), new Int(77));
        insert(new Int(5), new Int(55));
        insert(new Int(8), new Int(88));
        check(print(), """
Leaf           size:  8, count:  8
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
   6     5    55
   4     6    66
   5     7    77
   7     8    88
""");
        final StringBuilder s = new StringBuilder();
        iterate((k,d)->s.append(f("%4d  %4d\n", k.i(), d.i())));
        ok(()->s, """
   1    11
   2    22
   3    33
   4    44
   5    55
   6    66
   7    77
   8    88
""");

        maxSteps = 99999;
        execute();
       }
     };
   }

  static void test_iterate()
   {          test_iterate(true);
              test_iterate(false);
   }

  static void test_fixedFields(boolean Ex)
   {sayCurrentTestName();
    final int A = 22, B = 21;
    final Leaf l = new Leaf(new Build().maxSize(8).immediate(Ex))
     {void leafCode()
       {final Leaf l = this;
        l.initializeMemory();
        l.data(new Int(1), new Int(A));
        l.data(new Int(1))     .ok(A);
        final Leaf r = new Leaf(new Build().maxSize(8).immediate(Ex).parent(l));
        r.initializeMemory();
        r.copy(l);

        l.clear();
        l.data(new Int(1))  .ok(0);
        r.data(new Int(1))  .ok(A);
        execute();
       }
     };
   }

  static void test_fixedFields()
   {          test_fixedFields(true);
              test_fixedFields(false);
   }

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_leaf();
    test_compactLeft();
    test_compactRight();
    test_splitRight();
    test_splitLeft();
    test_mergeRight();
    test_mergeLeft();
    test_find();
    test_iterate();
    test_fixedFields();
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
