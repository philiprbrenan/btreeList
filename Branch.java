//----------------------------------------------------------------------------------------------------------------------
// Branch of a btree implemented using distributed sparse slots
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;

class Branch extends Program                                                                                            // A branch in a tree btree that translates keys into values to be implemented as an application specific integrated circuit
 {final int            maxSize;                                                                                         // The maximum number of entries in a branch of the tree
  final Slots          slots;                                                                                           // Slots used to order keys in branch
  ByteMemory.Ref       byteMemoryRef = null;                                                                            // Byte memory reference containing the slots
  final ByteMemory.Ref refMark;                                                                                         // Mark this node as a branch
  final ByteMemory.Ref refUp;                                                                                           // Parent node
  final ByteMemory.Ref refSlots;                                                                                        // The slot associated with each in use key
  final ByteMemory.Ref refData;                                                                                         // Bitset showing which slots are being used to map to keys
  final ByteMemory.Ref refTop;                                                                                          // Target for keys greater than all the keys in the branch bitset
  final Build          build;                                                                                           // Build used to construct this branch
  final static String  formatKey = "%3d";                                                                               // Format a key for dumping during testing

//D1 Construction                                                                                                       // Construct and layout a branch

  static class Build                                                                                                    // Parameters describing a branch
   {Integer         maxSize;                                                                                            // Maximum number of keys in branch
    boolean         immediate = true;                                                                                   // Immediate mode
    boolean         trace = true;                                                                                       // Trace execution
    Program         parent;                                                                                             // Parent program if any
    ByteMemory.Ref  byteMemoryRef;                                                                                      // Program memory to be used
    MemoryPositions memoryPositions;                                                                                    // Layout of memory
    Slots.Build     slots;                                                                                              // Bytes needed for slots

    Build immediate(boolean Immediate ) {immediate     = Immediate; return this;}
    Build maxSize  (int     MaxSize   ) {maxSize       = MaxSize;   return this;}
    Build memory   (ByteMemory.Ref Ref) {byteMemoryRef = Ref;       return this;}
    Build parent   (Program Parent    ) {parent        = Parent;    return this;}
    Build trace    (boolean Trace     ) {trace         = Trace;     return this;}

    Program.Build build()                                                                                               // Create a description of the needed containing program
     {final Program.Build p = new Program.Build();                                                                      // Description of containing program
      final Slots.Build   s = slots = new Slots.Build().numberOfKeys(maxSize);
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
      final int posUp    = posMark  + ib();
      final int posSlots = posUp    + ib();
      final int posData  = posSlots + slots.size();
      final int posTop   = posData  + ib(maxSize);
      final int size     = posTop   + ib();
     }

    int size() {return memoryPositions.size;}                                                                           // Bytes needed for the slots
   }

  Branch(Build Build)                                                                                                   // Create a description of a branch
   {super(Build.build());                                                                                               // Program for branch
    build         = Build;
    maxSize       = Build.maxSize;
    if (maxSize % 2 == 0) stop("MaxSize should be odd not even:", maxSize);                                             // Not strictly true but slightly easier to cope with
    if (maxSize < 3)      stop("MaxSize must be at least 3:",     maxSize);
    slots         = new Slots(new Slots.Build().numberOfKeys(maxSize).parent(parentProgram));                           // Slots for branch
    final Build.MemoryPositions m = build.memoryPositions;
    byteMemoryRef = Build.byteMemoryRef != null ? Build.byteMemoryRef : byteMemory.new Ref(0);                          // Either a reference to some memory has been supplied or create a reference to some locally allocated memory to contain the bitset
    refMark       = byteMemoryRef.step(m.posMark);                                                                      // Marks thos node as a branch or a branch
    refUp         = byteMemoryRef.step(m.posUp);                                                                        // Reference oft parent node
    refSlots      = byteMemoryRef.step(m.posSlots);                                                                     // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
    refTop        = byteMemoryRef.step(m.posTop);                                                                       // Top - target when the key is larger than all the keys in the branch
    refData       = byteMemoryRef.step(m.posData);                                                                      // Slots in use

    new I() {void action() {deleteFile(tracing);}};                                                                     // Delete the trace file here to avoid including the memory reference calculations above
    branchCode();                                                                                                       // Generate machine code if any assembler code has been supplied
   }


  void branchCode() {}                                                                                                  // Override this method to provide code for testing the branch

  Int find          (Int Key) {return getDataFromKey(Key, false);}                                                      // Get the data associated with a key
  Int delete        (Int Key) {return getDataFromKey(Key, true);}                                                       // Get the data associated with a key and delete the key if it exists.  At this point we do not clean up the value corresponding to the key because the determination of whether the value is valid or not is done solely in the slots and, as there is no prefferd value to set into the values array to mark it as not in use, it is sufficient to leave the existing value there.

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

  Int top() {return refTop.getInt();}                                                                                   // Get value of top
  void top(Int Top) {refTop.putInt(Top);}                                                                               // Set value of top

  Int stepDown(Int Key)                                                                                                 // Reference of the next branch down that might contain the specified key
   {final Slots.Find f = slots.find(Key);                                                                               // Find result
    final Int  r = new Int();                                                                                           // Result
    new If (f.empty)                                                                                                    // Found the index of a key that is greater than or equal to the search key
     {void Then()
       {r.set(top());                                                                                                   // Slot index of found key
       }
      void Else()
       {new If (f.equal.or(f.lower))                                                                                    // Found the index of a key that is greater than or equal to the search key. Lower refers to the relative position of the search key versus the found key
         {void Then()
           {r.set(data(f.slot));                                                                                        // Data associated with found key
           }
          void Else()
           {final Int n = slots.usedSlotsToKeys.nextOne(f.slot);                                                        // Found the index of a key that was less than the search key, so the next index up, if it exists must be the one we want
            new If (n.valid())                                                                                          // Found the index of a key that is greater than or equal to the search key
             {void Then()
               {r.set(data(n));                                                                                         // Data associated with next key
               }
              void Else()
               {r.set(top());                                                                                           // No  next key so use default
               }
             };
           }
         };
       }
     };
    return r;                                                                                                           // Result
   }


  Int insert(Int Key, Int Data)                                                                                         // Insert a key data pair into a branch returning the index of the containing slot
   {final Int i = slots.insert(Key);
    final Int k = slots.getSlotToKeyIndex(i);
    refData.putInt(k, Data);
    return i;
   }

  Bool empty()   {return slots.empty();}                                                                                // Is the branch empty
  Bool full ()   {return slots.full ();}                                                                                // Is the branch full
  Int  count()   {return slots.count();}                                                                                // Number of key/data pairs in the branch
  int  maxSize() {return maxSize;}                                                                                      // Number of key/data pairs in the branch

  Int  data(Int Index)            {return refData.getInt(Index);}                                                       // Get data at an index
  void data(Int Index, Int Value) {refData.putInt(Index, Value);}                                                       // The data values are arranged in reverse key order to make the results of compacting the corresponding slots

  int bytesNeeded() {return build.size();}                                                                              // Number of bytes needed to contain a branch
  void      clear() {byteMemoryRef.clear(bytesNeeded()); setAsBranch();}                                                // Clear memory associated with the branch and mark as a branch to create a new branch in a known state ready for use

  Bool     isBranch() {return refMark.getInt().eq(   2) ;}                                                              // Whether we are on a branch or not
  void  setAsBranch() {       refMark.putInt(new Int(2));}                                                              // Mark this as a branch

  Int  up()         {return refUp.getInt();}                                                                            // Get reference to a parent node
  void up(Int I)    {       refUp.putInt(I);}                                                                           // Set reference to parent node

  void copy (Branch Source) {byteMemoryRef.copy(Source.byteMemoryRef, bytesNeeded());}                                  // Copy one branch into another branch
  void invalidate()         {byteMemoryRef.invalidate(bytesNeeded());}                                                  // Invalidate a branch so that it will probably cause errros if an attempt is made to reuse it with it initializing it first

  void compactLeft()                                                                                                    // Compact a branch to the left
   {slots.compactSlotsLeft();                                                                                           // Compact the slots to match
    slots.compactKeysLeft((S, t, s)->{refData.putInt(t, refData.getInt(s));});                                          // Compact the slots to match
   }

  void compactRight()                                                                                                   // Compact a branch to the right
   {slots.compactSlotsRight();                                                                                          // Compact the slots to match
    slots.compactKeysRight((S, t, s)->{refData.putInt(t, refData.getInt(s));});                                         // Compact the slots to match
   }

  Int splitRight(Branch Right)                                                                                          // Split a full branch rightwards into a supplied branch
   {if (immediate() && count().i() != maxSize()) stop("Branch not full");                                               // The branch must be full
    final Branch left = this;
    left.compactLeft();                                                                                                 // Compact source slots so we know where they are
    Right.slots.clear();                                                                                                // Clear the target
    new ForCount (new Int(maxSize()))        {void body(Int Index) {Right.data(Index, left.data(Index));}};             // Copy the data values associated with the slots
    return slots.splitRightOdd(Right.slots);                                                                            // Split the slots
   }

  Int splitLeft(Branch Left)                                                                                            // Split a full branch leftwards into a supplied branch
   {if (immediate() && count().i() != maxSize()) stop("Branch not full");                                               // The branch must be full
    final Branch right = this;
    right.compactLeft(); Left.slots.clear();                                                                            // Compact source slots so we know where they are and clear target
    new ForCount (new Int(maxSize() / 2))    {void body(Int Index) {Left.data(Index, right.data(Index));}};             // Copy the data values associated with the slots
    return slots.splitLeftOdd(Left.slots);                                                                              // Split the slots
   }

  void mergeRight(Int Key, Branch Right)                                                                                // Merge the branch into the right of this branch
   {final Branch left = this;
    final Int  lc   = left .count();
    final Int  rc   = Right.count();
    left.compactLeft(); Right.compactRight();                                                                           // Compact source slots so we know where they are
    final Bool r = new Bool().clear();

    new If(lc.Add(rc).lt(maxSize()))
     {void Then()
       {r.set();
        new ForCount(rc, new Int(maxSize())) {void body(Int Index) {left.data(Index, Right.data(Index));}};             // Copy the data values associated with the slots
        left.slots.mergeFromRightOdd(Key, Right.slots);                                                                 // Split the slots
       }
     };
   }

  void mergeLeft(Int Key, Branch Left)                                                                                  // Merge the branch into the right of this branch
   {final Branch right = this;
    final Int  lc   = Left .count();
    final Int  rc   = right.count();
    Left.compactLeft(); right.compactRight();                                                                           // Compact source slots so we know where they are
    final Bool r = new Bool().clear();

    new If(lc.Add(rc).lt(maxSize()))
     {void Then()
       {r.set();
        new ForCount(lc)                     {void body(Int Index) {right.data(Index, Left.data(Index));}};             // Copy the data values associated with the slots
        right.slots.mergeFromLeftOdd(Key, Left.slots);                                                                  // Split the slots
       }
     };
   }

  int splitSize() {return maxSize()>>>1;}                                                                               // Size of a split branch

//D2 Print                                                                                                              // Print the branch

  public String toString()                                                                                              // Print a branch
   {final StringBuilder s = new StringBuilder(f("Branch: size: "+formatKey+"\n", maxSize()));
    s.append(" Ref   Key  Data\n");
    for (int i : range(slots.numberOfSlotsToKeys()))
     {if (slots.getSlotToKeysInUse(i))
       {final String f = "%4d  %4d  %4d\n";
        final int    k = slots.getSlotToKeyIndex(i);
        final String t = f(f, k, slots.getSlotToKeyValue(i), refData.getInt(k));
        s.append(t);
       }
     }
    return ""+s;
   }

  String dumpData()                                                                                                     // Dump the data
   {final StringBuilder s = new StringBuilder(f("Branch data: size: "+formatKey+"\n", maxSize()));
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
   {final StringBuilder s = new StringBuilder(f("Branch data: size: "+formatKey+"\n", maxSize()));
    s.append(" Ref  Data\n");
    for (int i : range(slots.numberOfKeys())) s.append(f("%4d  %4d\n",  i, refData.getInt(i)));
    return ""+s;
   }

//D1 Tests                                                                                                              // Tests

  static void test_branch(boolean Ex)
   {final Branch l = new Branch(new Build().maxSize(7).immediate(Ex));
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    //new I() {void action() {testStop("AAAA", l);}};
    l.ok(()->l, """
Branch: size:   8
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

  static void test_branch()
   {test_branch(true);
    test_branch(false);
   }

  static void test_compactLeft(boolean  Ex)
   {final Branch l = new Branch(new Build().maxSize(7).immediate(Ex));
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    l.delete(l.new Int(2));
    //l.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Branch: size:   8
 Ref   Key  Data
   3     1    11
   2     3    33
   1     4    44
""");
    l.compactLeft();
    //l.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Branch: size:   8
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
   {final Branch l = new Branch(new Build().maxSize(7).immediate(Ex));
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    //l.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Branch: size:   8
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
""");
    l.compactRight();
    //l.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Branch: size:   8
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
   {final Branch l = new Branch(new Build().maxSize(7).immediate(Ex));
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
Branch: size:   8
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
    final Branch r = new Branch(new Build().maxSize(7).immediate(Ex).parent(l));
    l.splitRight(r);
    //l.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Branch: size:   8
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
""");
    //l.new I() {void action() {testStop(r);}};
    l.ok(()->r, """
Branch: size:   8
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
   {final Branch r = new Branch(new Build().maxSize(7).immediate(Ex));
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
Branch: size:   8
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
    final Branch l = new Branch(new Build().maxSize(7).immediate(Ex).parent(r));
    r.splitLeft(l);
    //r.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Branch: size:   8
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
""");
    //r.new I() {void action() {testStop(r);}};
    r.ok(()->r, """
Branch: size:   8
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
   {final Branch l = new Branch(new Build().maxSize(7).immediate(Ex));
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
Branch: size:   8
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
    final Branch r = new Branch(new Build().maxSize(7).immediate(Ex).parent(l));
    l.splitRight(r);
    //l.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Branch: size:   8
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
""");
    //r.new I() {void action() {testStop(r);}};
    r.ok(()->r, """
Branch: size:   8
 Ref   Key  Data
   6     5    55
   4     6    66
   5     7    77
   7     8    88
""");
    l.mergeRight(l.new Int(4), r);
    //l.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Branch: size:   8
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
   {final Branch r = new Branch(new Build().maxSize(7).immediate(Ex));
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
Branch: size:   8
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
    final Branch l = new Branch(new Build().maxSize(7).immediate(Ex).parent(r));
    r.splitLeft(l);
    //r.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Branch: size:   8
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
""");
    //r.new I() {void action() {testStop(r);}};
    r.ok(()->r, """
Branch: size:   8
 Ref   Key  Data
   6     5    55
   4     6    66
   5     7    77
   7     8    88
""");
    r.mergeLeft(l.new Int(4), l);
    //r.new I() {void action() {testStop(r);}};
    r.ok(()->r, """
Branch: size:   8
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
   {final Branch l = new Branch(new Build().maxSize(7).immediate(Ex));
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    l.insert(l.new Int(6), l.new Int(66));
    l.insert(l.new Int(7), l.new Int(77));
    l.insert(l.new Int(5), l.new Int(55));
    l.new I() {void action() {testStop(l);}};
    l.ok(()->l, """
Branch: size:   7
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
   6     5    55
   4     6    66
   5     7    77
Branch.java:0609:
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
Branch: size:   8
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
   {final int A = 22, B = 21;
     final Branch l = new Branch(new Build().maxSize(7).immediate(Ex))
     {void branchCode()
       {final Branch l = this;
        l.setAsBranch();
        l.isBranch()        .ok(true);
        l.data(new Int(1), new Int(A));
        l.data(new Int(1))  .ok(A);
        l.up  (new Int(21));
        l.up  ()            .ok(B);
        final Branch r = new Branch(new Build().maxSize(7).immediate(Ex).parent(l));
        r.copy(l);
        r.isBranch()        .ok(true);

        l.clear();
        l.data(new Int(1))  .ok(0);
        l.isBranch()          .ok(true);
        l.up()              .ok(0);
        r.data(new Int(1))  .ok(A);
        r.isBranch()          .ok(true);
        r.up()              .ok(B);
        execute();
       }
     };
   }

  static void test_fixedFields()
   {test_fixedFields(true);
    test_fixedFields(false);
   }

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_branch();
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
    test_find();
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
