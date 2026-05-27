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
  final ByteMemory.Ref refMark;                                                                                         // Mark this node as a leaf
  final ByteMemory.Ref refUp;                                                                                           // Parent node
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
     {final int posMark  = 0;                                                                                           // A tree consists of nodes: leaves and branches. This field tells us which one we have
      final int posUp    = posMark + ib();
      final int posSlots = posUp   + ib();
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
    refMark       = byteMemoryRef;                                                                                      // Marks thos node as a leaf or a branch
    refUp         = byteMemoryRef.step(m.posUp);                                                                        // Reference oft parent node
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

  Int  data(Int Index)            {return refData.getInt(Index);}                                                       // Get data at an index
  void data(Int Index, Int Value) {refData.putInt(Index, Value);}                                                       // The data values are arranged in reverse key order to make the results of compacting the corresponding slots

  Bool isLeaf()   {return refMark.getInt().eq(   1) ;}                                                                  // Whether we are on a leaf or not
  void makeLeaf() {       refMark.putInt(new Int(1));}                                                                  // Mark this as a leaf

  Int  up()       {return refUp.getInt();}                                                                              // Whether we are on a leaf or not
  void up(Int I)  {       refUp.putInt(I);}                                                                             // Mark this as a leaf

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
    left.compactLeft();                                                                                                 // Compact source slots so we know where they are
    Right.slots.clear();                                                                                                // Clear the target
    new ForCount (new Int(maxLeafSize))        {void body(Int Index) {Right.data(Index, left.data(Index));}};           // Copy the data values associated with the slots
    slots.splitRightEven(Right.slots);                                                                                  // Split the slots
   }

  void splitLeft(Leaf Left)                                                                                             // Split a full leaf leftwards into a supplied leaf
   {if (immediate() && count().i() != maxLeafSize) stop("Leaf not full");                                               // The leaf must be full
    final Leaf right = this;
    right.compactLeft(); Left.slots.clear();                                                                            // Compact source slots so we know where they are and clear target
    new ForCount (new Int(maxLeafSize / 2))    {void body(Int Index) {Left.data(Index, right.data(Index));}};           // Copy the data values associated with the slots
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
        new ForCount(rc, new Int(maxLeafSize)) {void body(Int Index) {left.data(Index, Right.data(Index));}};           // Copy the data values associated with the slots
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
        new ForCount(lc)                       {void body(Int Index) {right.data(Index, Left.data(Index));}};           // Copy the data values associated with the slots
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
    l.maxSteps = 99999;
    l.execute();
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

  static void test_find(boolean  Ex)
   {final Leaf l = new Leaf(new Build().maxLeafSize(8).immediate(Ex));
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    l.insert(l.new Int(6), l.new Int(66));
    l.insert(l.new Int(7), l.new Int(77));
    l.insert(l.new Int(5), l.new Int(55));
    l.insert(l.new Int(8), l.new Int(88));
   // l.new I() {void action() {testStop(l);}};
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
   // l.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Leaf: size:   8
 Ref   Key  Data
""");

    l.maxSteps = 99999;
    l.execute();
   }

  static void test_find()
   {test_find(true);
    test_find(false);
   }

  static void test_fixedFields(boolean  Ex)
   {final int  u = 21;
    final Leaf l = new Leaf(new Build().maxLeafSize(8).immediate(Ex))
     {void leafCode()
       {makeLeaf();
        isLeaf().ok(true);
        up(new Int(u));
        up().ok(u);
        execute();
       }
     };
   }

  static void test_fixedFields()
   {test_fixedFields(true);
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
    test_fixedFields();
   }

  static void newTests()                                                                                                // Tests being worked on
   {//oldTests();
    test_fixedFields();
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
