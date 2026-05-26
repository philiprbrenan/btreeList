//----------------------------------------------------------------------------------------------------------------------
// Leaf of a btree implemented using distributed sparse slots
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;

class Leaf extends Program                                                                                              // A leaf in a tree btree that translates keys into values to be implemented as an application specific integrated circuit
 {final int        maxLeafSize;                                                                                         // The maximum number of entries in a leaf of the tree
  final Slots            slots;                                                                                         // Slots used to order keys in leaf
  ByteMemory.Ref  byteMemoryRef = null;                                                                                 // Byte memory reference containing the slots
  final ByteMemory.Ref refType;                                                                                         // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
  final ByteMemory.Ref refSlots;                                                                                        // The slot associated with each in use key
  final ByteMemory.Ref refData;                                                                                         // Bitset showing which slots are being used to map to keys
  final Build            build;                                                                                         // Build used to construct this leaf
  final static String formatKey = "%3d";                                                                                // Format a key for dumping during testing

//D1 Construction                                                                                                       // Construct and layout a leaf

  static class Build                                                                                                    // Parameters describing a leaf
   {Integer         maxLeafSize;                                                                                        // Maximum number of keys in leaf
    boolean         immediate = true;                                                                                   // Immediate mode
    boolean         trace = true;                                                                                       // Trace execution
    Program         parent;                                                                                             // Parent program if any
    ByteMemory.Ref  byteMemoryRef;                                                                                      // Program memory to be used
    MemoryPositions memoryPositions;                                                                                    // Layout of memory
    Slots.Build     slots;                                                                                              // Bytes needed for slots

    Build immediate    (boolean  Immediate) {immediate     = Immediate;   return this;}
    Build maxLeafSize  (int MaxLeafSize)    {maxLeafSize   = MaxLeafSize; return this;}
    Build memory       (ByteMemory.Ref Ref) {byteMemoryRef = Ref;         return this;}
    Build parent       (Program    Parent)  {parent        = Parent;      return this;}
    Build trace        (boolean     Trace)  {trace         = Trace;       return this;}

    Program.Build build()                                                                                               // Create a description of the needed containing program
     {final Program.Build p = new Program.Build();                                                                      // Description of containing program
      final Slots.Build   s = slots = new Slots.Build().numberOfKeys(maxLeafSize);
      final Program.Build S = s.build();                                                                                // Has the side effect of computing the size of the slots
      memoryPositions = new MemoryPositions();
      if (byteMemoryRef == null) p.memory(size());
      if (parent        != null) p.parent(parent);
      p.immediate(immediate);
      p.trace(trace);
      return p;
     }

    class MemoryPositions                                                                                               // Layout of memory
     {final int posType  = 0;                                                                                           // A tree consists of leaves and branches, this field tells us which one we have
      final int posSlots = posType + ib();
      final int posData  = posSlots+slots.size();
      final int size     = posData+ib(maxLeafSize);
     }

    int size() {return memoryPositions.size;}                                                                           // Bytes needed for the slots
   }

  Leaf(Build Build)                                                                                                     // Create a description of a leaf
   {super(Build.build());                                                                                               // Program for leaf
    build         = Build;
    maxLeafSize   = Build.maxLeafSize;
    slots         = new Slots(new Slots.Build().numberOfKeys(maxLeafSize).parent(parentProgram));                       // Slots for leaf
    final Build.MemoryPositions m = build.memoryPositions;
    byteMemoryRef = Build.byteMemoryRef != null ? Build.byteMemoryRef : byteMemory.new Ref(0);                          // Either a reference to some memory has been supplied or create a reference to some locally allocated memory to contain the bitset
    refType       = byteMemoryRef;                                                                                      // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
    refSlots      = byteMemoryRef.step(m.posSlots);                                                                     // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
    refData       = byteMemoryRef.step(m.posData);                                                                      // Slots in use

    new I() {void action() {deleteFile(tracing);}};                                                                     // Delete the trace file here to avoid including the memory reference calculations above
    leafCode();                                                                                                         // Generate machine code if any assembler code has been supplied
   }

  void leafCode() {}                                                                                                    // Override this method to provide code for testing the leaf

  Int find          (Int Key) {return getDataFromKey(Key, false);}                                                      // Get the data associated with a key
  Int delete        (Int Key) {return getDataFromKey(Key, true);}                                                       // Get the data associated with a key and delete the key
  Int getDataFromKey(Int Key, boolean Delete)                                                                           // Get the data associated with a key with the option of deleting the key if found
   {final Slots.Find f = slots.find(Key);                                                                               // Find the key
    final Int        r = new Int();                                                                                     // Result
    new If (f.equal)                                                                                                    // Found the key
     {void Then()
       {r.set(refData.getInt(slots.getSlotToKeyIndex(f.slot)));                                                         // Get data associated with key
        if (Delete) slots.delSlotAndKey(f.slot);                                                                        // Delete the key if requested
       }
      void Else()                                                                                                       // Key not found
       {r.invalidate();                                                                                                 // Data is invalid showing that the key was not found
       }
     };
    return r;                                                                                                           // Return data associated with key
   }

  Int insert(Int Key, Int Data)                                                                                         // Insert a key data pair into a leaf returning the index of the containing slot
   {final Int i = slots.insert(Key);
    final Int k = slots.getSlotToKeyIndex(i);
    refData.putInt(k, Data);
    return i;
   }

  Bool empty() {return slots.empty();}                                                                                  // Is the leaf empty
  Bool full () {return slots.full ();}                                                                                  // Is the leaf full
  Int  count() {return slots.count();}                                                                                  // Number of key/data pairs in the leaf

  void compactLeft()                                                                                                    // Compact a leaf to the left
   {slots.compactSlotsLeft();                                                                                           // Compact the slots to match
    slots.compactKeysLeft((S, t, s)->{refData.putInt(t, refData.getInt(s));});                                          // Compact the slots to match
   }

  void compactRight()                                                                                                   // Compact a leaf to the right
   {slots.compactSlotsRight();                                                                                          // Compact the slots to match
    slots.compactKeysRight((S, t, s)->{refData.putInt(t, refData.getInt(s));});                                         // Compact the slots to match
   }

  void splitRight(Leaf Right)                                                                                           // Split a full leaf rightwards into a supplied leaf
   {if (immediate() && count().i() != maxLeafSize) stop("Leaf not full");                                               // The leaf must be full
    final Leaf left = this;
    left.compactLeft();                                                                                                      // Compact source slots so we know where they are
    Right.slots.clear();                                                                                                // Clear the target
    new ForCount (new Int(maxLeafSize))                                                                                 // Copy data
     {void body(Int Index)
       {Right.refData.putInt(Index, left.refData.getInt(Index));                                                             // The data values are arranged in reverse key order to make the results of compacting the corresponding slots
       }
     };
    slots.splitRightEven(Right.slots);                                                                                  // Split the slots
   }

  void splitLeft(Leaf Left)                                                                                             // Split a full leaf leftwards into a supplied leaf
   {if (immediate() && count().i() != maxLeafSize) stop("Leaf not full");                                               // The leaf must be full
    final Leaf right = this;
    right.compactLeft();                                                                                                      // Compact source slots so we know where they are
    Left.slots.clear();                                                                                                 // Clear the target
    new ForCount (new Int(maxLeafSize/2))                                                                               // Lower key/data pairs
     {void body(Int Index)
       {Left.refData.putInt(Index, right.refData.getInt(Index));                                                              // Move data values  in order to the lower positions
       }
     };
    slots.splitLeftEven(Left.slots);                                                                                    // Split the slots
   }

  void mergeRight(Leaf Right)                                                                                           // Merge the leaf into the right of this leaf
   {final Leaf left = this;
    final Int  lc   = left .count();
    final Int  rc   = Right.count();
    left.compactLeft(); Right.compactRight();                                                                           // Compact source slots so we know where they are
    final Bool r = new Bool().clear();

    new If(lc.Add(rc).le(maxLeafSize))
     {void Then()
       {r.set();
        new ForCount(rc, new Int(maxLeafSize))                                                                          // Copy data
         {void body(Int Index)
           {left.refData.putInt(Index, Right.refData.getInt(Index));                                                    // The data values are arranged in reverse key order to make the results of compacting the corresponding slots
           }
         };
        left.slots.mergeFromRightEven(Right.slots);                                                                     // Split the slots
       }
     };
   }

  void mergeLeft(Leaf Left)                                                                                             // Merge the leaf into the right of this leaf
   {final Leaf right = this;
    final Int  lc   = Left .count();
    final Int  rc   = right.count();
    Left.compactLeft(); right.compactRight();                                                                           // Compact source slots so we know where they are
    final Bool r = new Bool().clear();

    new If(lc.Add(rc).le(maxLeafSize))
     {void Then()
       {r.set();
        new ForCount(lc)                                                                                                // Copy data
         {void body(Int Index)
           {right.refData.putInt(Index, Left.refData.getInt(Index));                                                    // The data values are arranged in reverse key order to make the results of compacting the corresponding slots
           }
         };
        right.slots.mergeFromLeftEven(Left.slots);                                                                      // Split the slots
       }
     };
   }

/*
  Data data(Slot I) {return new Data(memory.data(slots(I).value()));}                                                 // Get value of data field at index
  void data(Slot I, Data Value)                                                                                       // Set value of data field at index
   {memory.data(slots(I).value(), If (Value.valid(), new Int(), ()->Value.value(), ()->new Int(0)));
   }

  Data data(slot I) {return new Data(memory.data(I.value()));}                                                        // Get value of data field at the slot
  void data(slot I, Data Value)                                                                                       // Set value of data field at the slot
   {memory.data(I.value(),
      If (Value.valid(), new Int(), ()->Value.value(), ()->new Int(0)));
   }

  int splitSize() {return maxLeafSize>>>1;}                                                                           // Size of a split leaf

  Leaf duplicate()                                                                                                    // Duplicate a leaf
   {final Leaf d = new Leaf();
    d.copySlots(this);                                                                                                // Copy slots
    new For(numberOfRefs())                                                                                           // Each reference
     {void body(Int i, Bool C)                                                                                        // Each reference
       {final slot I = new slot(i);                                                                                   // Copy data associated with leaf keys
        d.data(I, data(I));                                                                                           // Copy data associated with leaf keys
        C.set();
       }
     };
    return d;
   }

  void free()                                                                                                         // Free the leaf
   {Tree.this.free(name());                                                                                           // Add to free chain
    invalidate();                                                                                                     // Invalidate slots
    memory.invalidate();                                                                                              // Invalidate leaf data
   }

  Leaf splitRight()                                                                                                   // Split this leaf into a new right leaf and return the new right leaf
   {final Leaf r = duplicate();                                                                                       // Copy the  leaf on the left to create the basis for the new right leaf
    splitLeftFullIntoRight(r);                                                                                        // Remove unwanted elements from leaf and right to effect the split
    return r;
   }

  Leaf splitLeft()                                                                                                    // Split a right leaf into a new left leaf
   {final Leaf l = duplicate();                                                                                       // Make a copy of the existing right leaf to form the basis for the new left leaf
    l.splitLeftFullIntoRight(this);                                                                                   // Remove unwanted elements from leaf and right to effect the split
    return l;
   }

  Leaf splitLeftFullIntoRight(Leaf Right)                                                                             // Split a full left leaf into an existing right leaf
   {final Int s = new Int(0);                                                                                         // Count slots used
    new For(numberOfSlots())                                                                                          // Each slot
     {void body(Int i, Bool C)
       {final Slot S = new Slot(i);
        new If (usedSlots(S))                                                                                         // Slot is in use
         {void Then()                                                                                                 // Slot is in use
           {new If (s.lt(splitSize()))                                                                                // Still in left leaf
             {void Then()
               {Right.clearSlotAndRef(S); s.inc();
               }
              void Else()                                                                                             // Clear slot being used in right leaf
               {clearSlotAndRef(S);
               }
             };
           }
         };
        C.set();
       }
     };                                                                                                               // The new right leaf
    redistribute(); Right.redistribute();
    return Right;
   }

  Int splittingKey()                                                                                                  // Splitting key from a leaf
   {if (full().Flip().b()) testStop("Leaf not full");                                                                     // The leaf must be full if we are going to split it
    final Int k = new Int(0);                                                                                         // Splitting key
    final Int p = new Int(0);                                                                                         // Position in leaf
    new For(numberOfSlots())                                                                                          // Scan for splitting keys
     {void body(Int i, Bool C)
       {new If (usedSlots(new Slot(i)))                                                                               // Used slot
         {void Then()
           {new If (p.eq(splitSize()-1).or(()->{return p.eq(splitSize());}))                                          // Accumulate splitting key as last on left and first on right of split
             {void Then()
               {k.add(keys(new Slot(i)).value());
               }
             };
            p.inc();                                                                                                  // Next position
           }
         };
        C.set();
       }
     };
    return k.Down();                                                                                                  // Average splitting key
   }

  Branch split()                                                                                                      // Split a leaf into two leaves and a branch
   {final Leaf   l = duplicate();
    final Leaf   r = l.splitRight();
    final Branch b = new Branch();                                                                                    // Branch into which the leaves will be inserted
    b.insert(new Key(splittingKey()), l);                                                                             // Insert left
    b.top(r);                                                                                                         // Right goes to to
    free();
    return b;
   }

  slot insert(Key Key, Data Data)                                                                                     // Insert a key data pair into a leaf
   {final slot i = insert(Key);
    new If (i.valid()) {void Then() {data(i, Data);}};                                                                // Save data in allocated reference
    return i;
   }

  void compactLeft()                                                                                                  // Compact the leaf to the left
   {final Data[]d = new Data[numberOfRefs()];
    final Int p = new Int(0);
    new For(numberOfSlots())                                                                                          // Copy leaf data
     {void body(Int i, Bool C)
       {final Slot I = new Slot(i);
        new If (usedSlots(I))
         {void Then()
           {d[p.i()] = data(slots(I)); p.inc();
           }
         };
        C.set();
       }
     };
    super.compactLeft();                                                                                              // Compact slots

    new For(p)                                                                                                        // Copy compacted leaf data
     {void body(Int i, Bool C)
       {data(new slot(i), d[i.i()]);
        C.set();
       }
     };
   }

  void compactRight()                                                                                                 // Compact the leaf to the right
   {final int   N = numberOfSlots(), R = numberOfRefs();
    final Data[]d = new Data[R];
    final Int   p = new Int(R-1);                                                                                     // Start at the last slot
    new For(N)                                                                                                        // Compact each slot to the right
     {void body(Int i, Bool C)
       {final Slot I = new Slot(N-i.i()-1);
        new If (usedSlots(I))
         {void Then()
           {d[p.i()] = data(slots(I)); p.dec();
           }
         };
        C.set();
       }
     };
    super.compactRight();                                                                                             // Compact slots
    new For(p, new Int(R-1))                                                                                          // Copy compacted leaf data
     {void body(Int i, Bool C)
       {data(new slot(i), d[i.i()]);
        C.set();
       }
     };
   }

  void mergeData(Leaf Left, Leaf Right)                                                                               // Merge the data from the compacted left and right slots
   {final Leaf l = Left, r = Right;
    new For(maxLeafSize)
     {void body(Int i, Bool C)
       {final slot J = new slot(i);
        new If     (l.usedRefs(J))
         {void Then()
           {data(J, l.data(J));
           }
          void Else()
           {new If (r.usedRefs(J)) {void Then() {data(J, r.data(J));}};
           }
         };
        C.set();
       }
     };
   }

  void mergeLeaves(Leaf Left, Leaf Right)                                                                             // Merge the specified leaves into the current leaf
   {Left .compactLeft ();
    Right.compactRight();
    mergeCompacted(Left, Right);
    mergeData     (Left, Right);
    redistribute();
   }

  Bool mergeFromRight(Leaf Right)                                                                                     // Merge the specified slots from the right
   {final Bool r = new Bool();
    new If (countUsed().Add(Right.countUsed()).gt(maxLeafSize))
     {void Then()
       {r.clear();
       }
      void Else()
       {mergeLeaves(duplicate(), Right.duplicate());
        r.set();
       }
     };
    return r;
   }

  Bool mergeFromLeft(Leaf Left)                                                                                       // Merge the specified slots from the left
   {final Bool R = new Bool();
    new If (Left.countUsed().Add(countUsed()).gt(maxLeafSize))
     {void Then()
       {R.clear();
       }
      void Else()
       {final Leaf l = Left.duplicate();
        final Leaf r =      duplicate();
        mergeLeaves(l, r);
        Left.free();
        l.free();
        r.free();
        R.set();
       }
     };
    return R;
   }

//D2 Memory                                                                                                             // Memory for leaf

  class Memory extends LeafMemoryPositions                                                                            // Memory required to hold bytes
   {final ByteBuffer bytes;                                                                                           // Byte buffer holding memory of this leaf

    void copy(Memory Memory)                                                                                          // Copy a set of slots from the specified memory into this memory
     {new For(size)
       {void body(Int i, Bool C)
         {bytes.put(i.i(), Memory.bytes.get(i.i()));
          C.set();
         }
       };
     }

    void invalidate()                                                                                                 // Invalidate the leaf in such a way that it is unlikely to work well if subsequently used
     {new For(size)
       {void body(Int i, Bool C)
         {bytes.put(i.i(), (byte)-1);
          C.set();
         }
       };
     }

    Memory(Allocation Name) {bytes = node(Name);}                                                                     // Position in tree memory

    Int  up()     {return new Int(bytes.getInt(posUp));}                                                              // Reference to parent branch. The zero node contains the tree base so zero can be used as a representation of null for references to branches and leaves
    void up(Int Index)   {bytes.putInt(posUp, Index.i());}                                                            // Save address of parent branch into memory

    Int  upIndex(){return new Int(bytes.getInt(posUpIndex));}                                                         // Get index of leaf in its parent from memory
    void upIndex(Int Value)                                                                                           // Save index of this leaf in its parent branch
     {new If(Value.valid())                                                                                           // Save value
       {void Then() {new I() {void action() {bytes.putInt(posUpIndex, Value.i());}};}                                 // Save none null value
        void Else() {new I() {void action() {bytes.putInt(posUpIndex, -1)       ;}};}                                 // -1 used to indicate null which means top
       };
     }

    Int  data(Int Index)                                                                                              // Get the index of the leaf in its parent branch from memory
     {return new Int(bytes.getInt(posData + ib(Index).i()));
     }
    void data(Int Index, Int  Value)                                                                                  // Put the index of the leaf in its parent branch into memory
     {bytes.putInt(posData + ib(Index).i(), Value.i());
     }
   }

//D2 Print                                                                                                              // Print the leaf

  String printInOrder()                                                                                                 // Print the values in the used slots in order
   {final StringJoiner k = new StringJoiner(", ");
    final StringJoiner d = new StringJoiner(", ");
    for (int i : range(numberOfSlots()))
     {final Slot I = new Slot(i);
      if (usedSlots(I).b())
       {k.add(""+keys(I).i());
        d.add(""+memory.data(slots(I)).i());
       }
     }
    return "keys: "+k+"\n"+"data: "+d+"\n";
   }
*/

  public String toString()                                                                                              // Print a leaf
   {final StringBuilder s = new StringBuilder(f("Leaf: size: "+formatKey+"\n", maxLeafSize));
    s.append(" Ref   Key  Data\n");
    for (int i : range(slots.numberOfSlotsToKeys()))
     {if (slots.getSlotToKeysInUse(i))
       {final String f = "%4d  %4d  %4d\n";
        final String t = f(f,  slots.getSlotToKeyIndex(i), slots.getSlotToKeyValue(i), refData.getInt(slots.getSlotToKeyIndex(i)));
        s.append(t);
       }
     }
    return ""+s;
   }

  String dumpData()                                                                                                     // Dump the data
   {final StringBuilder s = new StringBuilder(f("Leaf data: size: "+formatKey+"\n", maxLeafSize));
    s.append(" Ref  Data\n");
    for (int i : range(slots.numberOfKeys()))
     {if (slots.getKeyInUse(i))
       {final String f = "%4d  %4d\n";
        final String t = f(f,  i, refData.getInt(i));
        s.append(t);
       }
     }
    return ""+s;
   }

  String dumpDataArray()                                                                                                // Dump the data array
   {final StringBuilder s = new StringBuilder(f("Leaf data: size: "+formatKey+"\n", maxLeafSize));
    s.append(" Ref  Data\n");
    for (int i : range(slots.numberOfKeys())) s.append(f("%4d  %4d\n",  i, refData.getInt(i)));
    return ""+s;
   }

//D1 Tests                                                                                                              // Tests

  static void test_leaf(boolean Ex)
   {final Leaf l = new Leaf(new Build().maxLeafSize(8).immediate(Ex));
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    //new I() {void action() {testStop("AAAA", l);}};
    l.ok(()->l, """
Leaf: size:   8
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
   {test_leaf(true);
    test_leaf(false);
   }

  static void test_compactLeft(boolean  Ex)
   {final Leaf l = new Leaf(new Build().maxLeafSize(8).immediate(Ex));
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    l.delete(l.new Int(2));
    //l.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Leaf: size:   8
 Ref   Key  Data
   3     1    11
   2     3    33
   1     4    44
""");
    l.compactLeft();
    //l.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Leaf: size:   8
 Ref   Key  Data
   0     1    11
   2     3    33
   1     4    44
""");
    l.maxSteps = 99999;
    l.execute();
   }

  static void test_compactLeft()
   {test_compactLeft(true);
    test_compactLeft(false);
   }

  static void test_compactRight(boolean  Ex)
   {final Leaf l = new Leaf(new Build().maxLeafSize(8).immediate(Ex));
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    //l.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Leaf: size:   8
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
""");
    l.compactRight();
    //l.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Leaf: size:   8
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
   {test_compactRight(true);
    test_compactRight(false);
   }

  static void test_splitRight(boolean Ex)
   {final Leaf l = new Leaf(new Build().maxLeafSize(8).immediate(Ex));
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    l.insert(l.new Int(6), l.new Int(66));
    l.insert(l.new Int(7), l.new Int(77));
    l.insert(l.new Int(5), l.new Int(55));
    l.insert(l.new Int(8), l.new Int(88));
    //l.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Leaf: size:   8
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
    final Leaf r = new Leaf(new Build().maxLeafSize(8).immediate(Ex).parent(l));
    l.splitRight(r);
    //l.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Leaf: size:   8
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
""");
    //l.new I() {void action() {testStop(r);}};
    l.ok(()->r, """
Leaf: size:   8
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
   {test_splitRight(true);
    test_splitRight(false);
   }

  static void test_splitLeft(boolean  Ex)
   {final Leaf r = new Leaf(new Build().maxLeafSize(8).immediate(Ex));
    r.insert(r.new Int(2), r.new Int(22));
    r.insert(r.new Int(4), r.new Int(44));
    r.insert(r.new Int(3), r.new Int(33));
    r.insert(r.new Int(1), r.new Int(11));
    r.insert(r.new Int(6), r.new Int(66));
    r.insert(r.new Int(7), r.new Int(77));
    r.insert(r.new Int(5), r.new Int(55));
    r.insert(r.new Int(8), r.new Int(88));
    //r.new I() {void action() {testStop(r);}};
    r.ok(()->r, """
Leaf: size:   8
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
    final Leaf l = new Leaf(new Build().maxLeafSize(8).immediate(Ex).parent(r));
    r.splitLeft(l);
    //r.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Leaf: size:   8
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
""");
    //r.new I() {void action() {testStop(r);}};
    r.ok(()->r, """
Leaf: size:   8
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
   {test_splitLeft(true);
    test_splitLeft(false);
   }

  static void test_mergeRight(boolean Ex)
   {final Leaf l = new Leaf(new Build().maxLeafSize(8).immediate(Ex));
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    l.insert(l.new Int(6), l.new Int(66));
    l.insert(l.new Int(7), l.new Int(77));
    l.insert(l.new Int(5), l.new Int(55));
    l.insert(l.new Int(8), l.new Int(88));
    //l.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Leaf: size:   8
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
    final Leaf r = new Leaf(new Build().maxLeafSize(8).immediate(Ex).parent(l));
    l.splitRight(r);
    //l.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Leaf: size:   8
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
""");
    //r.new I() {void action() {testStop(r);}};
    r.ok(()->r, """
Leaf: size:   8
 Ref   Key  Data
   6     5    55
   4     6    66
   5     7    77
   7     8    88
""");
    l.mergeRight(r);
    //l.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Leaf: size:   8
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

  static void test_mergeRight()
   {test_mergeRight(true);
    test_mergeRight(false);
   }

  static void test_mergeLeft(boolean  Ex)
   {final Leaf r = new Leaf(new Build().maxLeafSize(8).immediate(Ex));
    r.insert(r.new Int(2), r.new Int(22));
    r.insert(r.new Int(4), r.new Int(44));
    r.insert(r.new Int(3), r.new Int(33));
    r.insert(r.new Int(1), r.new Int(11));
    r.insert(r.new Int(6), r.new Int(66));
    r.insert(r.new Int(7), r.new Int(77));
    r.insert(r.new Int(5), r.new Int(55));
    r.insert(r.new Int(8), r.new Int(88));
   // r.new I() {void action() {testStop(r);}};
    r.ok(()->r, """
Leaf: size:   8
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
    final Leaf l = new Leaf(new Build().maxLeafSize(8).immediate(Ex).parent(r));
    r.splitLeft(l);
    //r.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Leaf: size:   8
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
""");
    //r.new I() {void action() {testStop(r);}};
    r.ok(()->r, """
Leaf: size:   8
 Ref   Key  Data
   6     5    55
   4     6    66
   5     7    77
   7     8    88
""");
    r.mergeLeft(l);
    //r.new I() {void action() {testStop(r);}};
    r.ok(()->r, """
Leaf: size:   8
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
   {test_mergeLeft(true);
    test_mergeLeft(false);
   }

/*
  static void test_ifd()
   {final Tree  t =   new
    final Slots s = t.new Slots(8);
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
   {final Tree  t =   new Tree (8);
    final Slots s = t.new Slots(8);

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

  static void test_compactLeafLeft()
   {final Tree t = new Tree(8);
    final Leaf l = t.new Leaf();
    l.insert(t.new Key(13), t.new Data(23));
    l.insert(t.new Key(12), t.new Data(22));
    l.insert(t.new Key(14), t.new Data(24));
    l.insert(t.new Key(11), t.new Data(21));
    ok(l, """
Leaf     : 1 up: null index: null
Slots    : name:  1, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   0   0   3   1   0   2   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X   X   X   .   .   .   .   .   .
usedRefs :    X   X   X   X   .   .   .   .
keys     :   13  12  14  11   0   0   0   0
data     :   23  22  24  21   0   0   0   0
""");
    l.compactLeft();
    ok(l, """
Leaf     : 1 up: null index: null
Slots    : name:  1, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   1   2   3   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    X   X   X   X   .   .   .   .   .   .   .   .   .   .   .   .
usedRefs :    X   X   X   X   .   .   .   .
keys     :   11  12  13  14   0   0   0   0
data     :   21  22  23  24   0   0   0   0
""");
   }

  static void test_compactLeafRight()
   {final Tree t = new Tree(8);
    final Leaf l = t.new Leaf();
    l.insert(t.new Key(13), t.new Data(23));
    l.insert(t.new Key(12), t.new Data(22));
    l.insert(t.new Key(14), t.new Data(24));
    l.insert(t.new Key(11), t.new Data(21));

                                          //testStop(l);
    ok(l, """
Leaf     : 1 up: null index: null
Slots    : name:  1, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   0   0   3   1   0   2   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X   X   X   .   .   .   .   .   .
usedRefs :    X   X   X   X   .   .   .   .
keys     :   13  12  14  11   0   0   0   0
data     :   23  22  24  21   0   0   0   0
""");
    l.compactRight();
                                          //testStop(l);
    ok(l, """
Leaf     : 1 up: null index: null
Slots    : name:  1, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   4   5   6   7   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   X   X   X   X   .   .   .   .   .   .   .   .
usedRefs :    .   .   .   .   X   X   X   X
keys     :    0   0   0   0  11  12  13  14
data     :    0   0   0   0  21  22  23  24
""");
   }

  static void test_compactBranchLeft()
   {final Tree t = new Tree(8);
    final Branch b = t.new Branch();
    b.insert(t.new Key(12), t.fake(t.new Allocation(22)));
    b.insert(t.new Key(11), t.fake(t.new Allocation(21)));
    b.insert(t.new Key(13), t.fake(t.new Allocation(23)));
    b.top(t.fake(t.new Allocation(4)));
  //testStop(b);
    ok(b, """
Branch   :    1   up:    0  index:    0
Slots    : name:  1, type:  1, refs:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slots    :    0   0   0   0   0   0   1   0   2   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X   X   .   .   .   .   .
usedRefs :    X   X   X   .   .   .   .
keys     :   12  11  13   0   0   0   0
data     :  -22 -21 -23   .   .   .   .
top      :   -4
""");

    b.compactLeft();
  //testStop(b);
    ok(b, """
Branch   :    1   up:    0  index:    0
Slots    : name:  1, type:  1, refs:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slots    :    0   1   2   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    X   X   X   .   .   .   .   .   .   .   .   .   .   .
usedRefs :    X   X   X   .   .   .   .
keys     :   11  12  13   0   0   0   0
data     :  -21 -22 -23   .   .   .   .
top      :   -4
""");
   }

  static  Data[]test_leaf_data(Tree t, int...Values)
   {final Data[]d = new Data[Values.length];
    for (int i = 0; i < d.length; i++) d[i] = t.new Data(t.new Int(Values[i]));
    return d;
   }

  static Leaf test_leaf()
   {final Tree  t = new Tree(8);
    final Leaf  l = t.new Leaf();
    final Data[]d = test_leaf_data(t, 13, 16, 15, 18, 17, 14, 12, 11);
    for (int i = 0; i < d.length; i++) l.insert(t.new Key(d[i].i()), d[i]);
    return l;
   }

  static void test_duplicate_leaf()
   {final Leaf l = test_leaf();
    final Leaf L = l.duplicate();
  //testStop(l);
    ok(l, """
Leaf     : 1 up: null index: null
Slots    : name:  1, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    7   6   0   0   5   0   2   0   1   0   4   0   3   0   0   0
usedSlots:    X   X   X   .   X   .   X   .   X   .   X   .   X   .   .   .
usedRefs :    X   X   X   X   X   X   X   X
keys     :   13  16  15  18  17  14  12  11
data     :   13  16  15  18  17  14  12  11
""");
  //testStop(L);
    ok(L, """
Leaf     : 2 up: null index: null
Slots    : name:  2, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    7   6   0   0   5   0   2   0   1   0   4   0   3   0   0   0
usedSlots:    X   X   X   .   X   .   X   .   X   .   X   .   X   .   .   .
usedRefs :    X   X   X   X   X   X   X   X
keys     :   13  16  15  18  17  14  12  11
data     :   13  16  15  18  17  14  12  11
""");
   }

  static void test_splitLeftLeafIntoRight()
   {final Leaf l = test_leaf();
  //testStop(l);
    ok(l, """
Leaf     : 1 up: null index: null
Slots    : name:  1, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    7   6   0   0   5   0   2   0   1   0   4   0   3   0   0   0
usedSlots:    X   X   X   .   X   .   X   .   X   .   X   .   X   .   .   .
usedRefs :    X   X   X   X   X   X   X   X
keys     :   13  16  15  18  17  14  12  11
data     :   13  16  15  18  17  14  12  11
""");
    final Leaf r = l.splitRight();
  //testStop(l);
    ok(l, """
Leaf     : 1 up: null index: null
Slots    : name:  1, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   6   0   0   0   0   0   0   0   5   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   X   .   .
usedRefs :    X   .   .   .   .   X   X   X
keys     :   13  16  15  18  17  14  12  11
data     :   13  16  15  18  17  14  12  11
""");
  //testStop(r);
    ok(r, """
Leaf     : 2 up: null index: null
Slots    : name:  2, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   2   0   0   0   1   0   0   0   4   0   0   0   3   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   X   .   .
usedRefs :    .   X   X   X   X   .   .   .
keys     :   13  16  15  18  17  14  12  11
data     :   13  16  15  18  17  14  12  11
""");
    ok(l.printInOrder(), """
keys: 11, 12, 13, 14
data: 11, 12, 13, 14
""");

    ok(r.printInOrder(), """
keys: 15, 16, 17, 18
data: 15, 16, 17, 18
""");
   }

  static void test_splitRightLeafIntoLeft()
   {final Leaf r = test_leaf();
    final Leaf l = r.splitLeft();
    ok(l.printInOrder(), """
keys: 11, 12, 13, 14
data: 11, 12, 13, 14
""");
    ok(r.printInOrder(), """
keys: 15, 16, 17, 18
data: 15, 16, 17, 18
""");
   }

  static Leaf test_leaf1()
   {final Tree  t = new Tree(8,7);
    final Leaf  l = t.new Leaf();
    final Data[]d = test_leaf_data(t, 13, 14, 12, 11);
    for (int i = 0; i < d.length; i++) l.insert(t.new Key(d[i].i()), d[i]);
    return l;
   }

  static Leaf test_leaf2()
   {final Tree  t = new Tree(8,7);
    final Leaf  l = t.new Leaf();
    final Data[]d = test_leaf_data(t, 16, 15, 18, 17);
    for (int i = 0; i < d.length; i++) l.insert(t.new Key(d[i].i()), d[i]);
    return l;
   }

  static void test_mergeLeafLeft()
   {final Leaf l = test_leaf1();
    final Leaf r = test_leaf2();
    l.mergeFromRight(r);
  //testStop(l);
    ok(l, """
Leaf     : 1 up: null index: null
Slots    : name:  1, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   1   0   2   0   3   0   4   0   5   0   6   0   7   0
usedSlots:    X   .   X   .   X   .   X   .   X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X   X   X   X   X
keys     :   11  12  13  14  15  16  17  18
data     :   11  12  13  14  15  16  17  18
""");
   }

  static void test_mergeLeafRight()
   {final Leaf l = test_leaf1();
    final Leaf r = test_leaf2();
    r.mergeFromLeft(l);
  //testStop(r));
    ok(r, """
Leaf     : 1 up: null index: null
Slots    : name:  1, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   1   0   2   0   3   0   4   0   5   0   6   0   7   0
usedSlots:    X   .   X   .   X   .   X   .   X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X   X   X   X   X
keys     :   11  12  13  14  15  16  17  18
data     :   11  12  13  14  15  16  17  18
""");
   }
*/

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_leaf();
    test_compactLeft();
    test_compactRight();
    test_splitRight();
    test_splitLeft();
    test_mergeRight();
    test_mergeLeft();
    //test_emptyTree();
    //test_compactLeafLeft();
    //test_compactLeafRight();
    //test_compactBranchLeft();
    //test_compactBranchRight();
    //test_duplicate_leaf();
    //test_splitLeftLeafIntoRight();
    //test_splitRightLeafIntoLeft();
    //test_duplicate_branch();
    //test_splitLeftBranchIntoRight();
    //test_splitRightBranchIntoLeft();
    //test_mergeLeafLeft();
    //test_mergeLeafRight();
    //test_mergeBranchLeft();
    //test_mergeBranchRight();
    //test_splitLeaf();
    //test_splitBranch();
    //test_insert2();
    //test_insert();
    //test_insert_reverse();
    //test_insert_random();
    //test_insert_random_32();
    //test_delete();
    //test_delete_descending();
    //test_delete_random_32();
    //test_deep();
    //test_idi();
   }

  static void newTests()                                                                                                // Tests being worked on
   {//oldTests();
    test_mergeRight();
    test_mergeLeft();
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
