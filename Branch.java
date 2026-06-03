//----------------------------------------------------------------------------------------------------------------------
// Branch of a btree implemented using distributed sparse slots
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
// “8GB DDR4 2666 laptop SODIMM”
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;

class Branch extends Program implements Program.Locatable                                                               // A branch in a tree btree that translates keys into values to be implemented as an application specific integrated circuit
 {final int            maxSize;                                                                                         // The maximum number of entries in a branch of the tree
  final Slots          slots;                                                                                           // Slots used to order keys in branch
  final Int            at            = new Int();                                                                       // A representation of the location of the branch sufficient to be able to free it
  ByteMemory.Ref       byteMemoryRef = null;                                                                            // Byte memory reference containing the tree
  final ByteMemory.Ref refMark;                                                                                         // Mark this node as a branch
  final ByteMemory.Ref refSlots;                                                                                        // The slot associated with each key being used
  final ByteMemory.Ref refData;                                                                                         // Bitset showing which slots are being mapped to keys
  final ByteMemory.Ref refTop;                                                                                          // Target for keys greater than all the keys in the branch bitset
  final Build          build;                                                                                           // Build used to construct this branch
  final static String  formatKey = "%3d";                                                                               // Format a key for dumping during testing

//D1 Construction                                                                                                       // Construct and layout a branch

  static class Build                                                                                                    // Parameters describing a branch
   {Integer         maxSize;                                                                                            // Maximum number of keys in branch
    Int             at;                                                                                                 // The location of the branch
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

    class MemoryPositions                                                                                               // Layout of memory
     {final int posMark  = 0;                                                                                           // A tree consists of nodes: leaves and branches. This field tells us which one we have
      final int posSlots = posMark  + ib();
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
    final Build.MemoryPositions m = build.memoryPositions;
    byteMemoryRef = Build.byteMemoryRef != null ? Build.byteMemoryRef : byteMemory.new Ref(0);                          // Either a reference to some memory has been supplied or create a reference to some locally allocated memory to contain the bitset
    refMark       = byteMemoryRef.step(m.posMark);                                                                      // Mark this node as a branch or a leaf
    refSlots      = byteMemoryRef.step(m.posSlots);                                                                     // Slots order the keys which are stored unordered.  Using one level of indirection to the keys speeds up insertions by allowing the narrower slot references to be moved rather than the wider keys
    refTop        = byteMemoryRef.step(m.posTop);                                                                       // Top - target when the key is larger than all the keys in the branch
    refData       = byteMemoryRef.step(m.posData);                                                                      // Slots in use
    if (build.at != null) at.set(build.at);                                                                             // The location of the leaf if supplied
    slots         = new Slots(new Slots.Build().numberOfKeys(maxSize).memory(refSlots).parent(program()));              // Slots for branch
    branchCode();                                                                                                       // Generate machine code if any assembler code has been supplied
   }

  Branch initializeMemory()                                                                                             // Initialize slots and data associated with the branch
   {clear();                                                                                                            // Clear backing memory
    slots.initializeMemory();                                                                                           // Initialize slots
    return this;
   }

  public Int getLocation() {return at;}                                                                                 // The location of this node in memory

  void branchCode() {}                                                                                                  // Override this method to provide code for testing the branch

  Bool empty()   {return slots.empty();}                                                                                // Is the branch empty
  Bool full ()   {return slots.full ();}                                                                                // Is the branch full
  Int  count()   {return slots.count();}                                                                                // Number of key/data pairs in the branch
  int  maxSize() {return maxSize;}                                                                                      // Number of key/data pairs in the branch

  Int  data(Int Index)            {return refData.getInt(Index);}                                                       // Get data at an index
  void data(Int Index, Int Value) {refData.putInt(Index, Value);}                                                       // The data values are arranged in reverse key order to make the results of compacting the corresponding slots

  int bytesNeeded() {return build.size();}                                                                              // Number of bytes needed to contain a branch
  void      clear() {byteMemoryRef.clear(bytesNeeded());}                                                               // Clear memory associated with the branch and mark as a branch to create a new branch in a known state ready for use

  void copy (Branch Source) {byteMemoryRef.copy(Source.byteMemoryRef, bytesNeeded());}                                  // Copy one branch into another branch
  void invalidate()         {byteMemoryRef.invalidate(bytesNeeded());}                                                  // Invalidate a branch so that it will probably cause errros if an attempt is made to reuse it with it initializing it first

//D1  Delete, find, insert                                                                                              // Delete, find, insert keys and data in a branch

  Int find  (Int Key) {return getDataFromKey(Key, false);}                                                              // Get the data associated with a key
  Int delete(Int Key) {return getDataFromKey(Key, true);}                                                               // Get the data associated with a key and delete the key if it exists.  At this point we do not clean up the value corresponding to the key because the determination of whether the value is valid or not is done solely in the slots and, as there is no prefferd value to set into the values array to mark it as not in use, it is sufficient to leave the existing value there.

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

  Int  top()  {return refTop.getInt();}                                                                                 // Get value of top
  void top(Int Top) {refTop.putInt(Top);}                                                                               // Set value of top

  class StepDown
   {final Int node = new Int();                                                                                         // The next node down
    final Int slot = new Int();                                                                                         // The slot used to step down.  If not set then stepped through top
   }

  StepDown stepDown(Int Key)                                                                                            // Reference of the next branch down that might contain the specified key
   {final Slots.Find f = slots.find(Key);                                                                               // Find result
    final StepDown   r = new StepDown();                                                                                // Result

    new If (f.empty)                                                                                                    // Found the index of a key that is greater than or equal to the search key
     {void Then()                                                                                                       // Step through top because the body of the branch is empty
       {r.slot.invalidate();
        r.node.set(top());
       }
      void Else()
       {new If (f.equal.or(f.lower))                                                                                    // Found the index of a key that is greater than or equal to the search key. Lower refers to the relative position of the search key versus the found key
         {void Then()
           {r.slot.set(f.slot);                                                                                         // Step through slot
            r.node.set(data(slots.getSlotToKeyIndex(f.slot)));                                                          // Next node
           }
          void Else()                                                                                                   // Found the index of a key that was less than the search key, so the next index up, if it exists must be the one we want
           {final Int n = slots.usedSlotsToKeys.nextOne(f.slot);
            r.slot.copy(n);                                                                                             // Copy the slot found if there was one
            new If (n.valid())
             {void Then()
               {r.node.set(data(slots.getSlotToKeyIndex(n)));                                                           // Node at next level down
               }
              void Else()
               {r.node.set(top());                                                                                      // No next key so step down through top
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
    return i;                                                                                                           // Return the slot in the branch in which the key, data pair was inserted
   }

  Int insert(Int Key, Int Data, Int BelowSlot)                                                                          // Insert a key data pair into a branch when the slot below which to make the insertion is known, returning the index of the containing slot. If the specified slot is not valid, the key data pair are added at the end of the slots. This method generates less code than a normal insert does because it does not need to do a find to locate the slot in which to place the key
   {//final Int        i = slots.insert(Key); .// repalce with
    //final Int        k = slots.getSlotToKeyIndex(i);
    final Int r = new Int();                                                                                            // The slot containing the inserted key
    new If(BelowSlot.valid())                                                                                           // Insert below the specified slot if it is valid
     {void Then()
       {if (immediate() && !slots.getSlotToKeysInUse(BelowSlot).b())
         {stop("Slot should be set but is empty:", BelowSlot, slots);
         }
        final Slots.Find f = slots.new Find().set(BelowSlot, true);                                                     // Construct the find to indicate how and where to perform the insertion as if we had just done a find
        r.set(f.insert(Key));                                                                                           // Insert knowing the insertion slot in advance
        final Int k = slots.getSlotToKeyIndex(r);
        refData.putInt(k, Data);                                                                                        // Place data in the location that corresponds to the key slot used to complete the insertion
       }
      void Else()                                                                                                       // No below slot supplied so we must insert at the end in the modle if empty or at the end Insert in the first
       {new If (slots.empty())                                                                                          // Easy insert as the slots are empty
         {void Then()
           {r.set(slots.insertEmpty(Key));                                                                              // Insert immediately in the center
            refData.putInt(new Int(0), Data);                                                                           // Place data in the location that corresponds to the key slot used to complete the insertion
           }
          void Else()                                                                                                   // Insert above the last key, which is known to exist because the slots are not empty
           {final Int l = slots.locateLastUsedSlot();
            final Slots.Find f = slots.new Find().set(l, false);                                                        // Construct the find to indicate how and where to perform the insertion as if we had just done a find
            r.set(f.insert(Key));                                                                                       // Insert knowing the insertion slot in advance
            final Int k = slots.getSlotToKeyIndex(r);
            refData.putInt(k, Data);                                                                                    // Place data in the location that corresponds to the key slot used to complete the insertion
           }
         };
       }
     };
    return r;                                                                                                           // Return the slot in the branch in which the key, data pair was actually inserted
   }

//D1 Compact, Split, Merge                                                                                              // Compact, split or merge branches
//D2 Compact                                                                                                            // Compact a branch to the left or right

  void compactLeft()                                                                                                    // Compact a branch to the left
   {slots.compactSlotsLeft();                                                                                           // Compact the slots to match
    slots.compactKeysLeft((S, t, s)->{refData.putInt(t, refData.getInt(s));});                                          // Compact the slots to match
   }

  void compactRight()                                                                                                   // Compact a branch to the right
   {slots.compactSlotsRight();                                                                                          // Compact the slots to match
    slots.compactKeysRight((S, t, s)->{refData.putInt(t, refData.getInt(s));});                                         // Compact the slots to match
   }

  private void copySplitData(Branch Target, Int Start, Int End)                                                         // Copy out the indexed data values in the specified key range from the specified source and place them in the exact same position in the target
   {final Branch source = this;
    new ForCount (Start, End)
     {void body(Int Index)
       {final Int s =  source.slots.getSlotToKeyIndex(Index);                                                           // Index the key to be copied out
        Target.data(s, source.data(s));                                                                                 // Copy the data value from the source leaf into the exact same position in the left leaf
       }
     };
   }

//D2 Split                                                                                                              // Split a full branch into two branches

  private Int splittingKey()                                                                                            // Splitting key for a branch assuming that the branch has been compacted to the right
   {if (immediate() && count().i() != maxSize()) stop("Branch not full");                                               // The branch must be full
    return slots.getSlotToKeyValue(new Int(maxSize/2));
   }

  Int splitRight(Branch Right)                                                                                          // Split a full branch rightwards into a supplied branch
   {if (immediate() && count().i() != maxSize()) stop("Branch not full");                                               // The branch must be full
    final Branch left = this;
    left .compactLeft();                                                                                                // Compact source slots so we know where they are
    Right.slots.clear();                                                                                                // Clear the target
    final Int sk = left.splittingKey();
    left .copySplitData(Right, new Int(maxSize / 2 + 1), new Int(maxSize()));                                           // Copy the data values associated with the slots
    Right.top(left.top());                                                                                              // Left top is now right top
    left .top(left.data(left.slots.getSlotToKeyIndex(new Int(maxSize / 2))));                                           // Left top is data from splitting key
    left .slots.splitRightOdd(Right.slots);                                                                             // Split the slots
    return sk;                                                                                                          // Splitting key
   }

  Int splitLeft(Branch Left)                                                                                            // Split a full branch leftwards into a supplied branch
   {if (immediate() && count().i() != maxSize()) stop("Branch not full");                                               // The branch must be full
    final Branch right = this;
    right.compactLeft();                                                                                                // Compact source slots so we know where they are
    Left .slots.clear();                                                                                                // Clear target
    final Int sk = right.splittingKey();
    right.copySplitData(Left, new Int(0), new Int(maxSize() / 2));                                                      // Copy the data values associated with the slots
    Left .top(right.data(right.slots.getSlotToKeyIndex(new Int(maxSize / 2))));                                         // Left top is data from splitting key
    slots.splitLeftOdd(Left.slots);                                                                                     // Split the slots
    return sk;                                                                                                          // Splitting key
   }

//D2 Merge                                                                                                              // Merge two leaves

  private void copyMergeData(Branch Source, Int Start, Int End)                                                         // Copy the data values directly in the specified key range from the specified source and place them in the exact same position in the target
   {new ForCount (Start, End)
     {void body(Int Index)
       {data(Index, Source.data(Index));                                                                                // The keys have been compacted left and right so we can copy them from the source position into the same position in the target without collisions
       }
     };
   }

  Bool mergeRight(Branch Right)                                                                                         // Merge the leaf into the right of this leaf
   {final Branch left = this;
    final Int  lc     = left .count();
    final Int  rc     = Right.count();
    final Bool r      = new Bool().clear();

    new If (lc.Add(rc).lt(maxSize()))
     {void Then()
       {r.set();
        final Slots ls = left .slots;
        final Slots Rs = Right.slots;
        left .compactLeft();    Right.compactRight();                                                                   // Compact so both the slots and keys are in opposing extremal positions to avoid collisions when we merge
        ls.compactKeysLeft ((S, t, s)->{left .data(t, left .data(s));});
        Rs.compactKeysRight((S, t, s)->{Right.data(t, Right.data(s));});

        final Int lt = left .top();                                                                                     // Left top
        final Int rt = Right.top();                                                                                     // Right top
        left.copyMergeData(Right, new Int(maxSize).sub(rc), new Int(maxSize()));                                        // Copy the right data values into the left data values
        left.top(rt);                                                                                                   // Set right top
        left.data(lc, lt);                                                                                              // Place left top in left data values
        ls.mergeFromRightOdd(Rs, null);                                                                                 // Split the slots
       }
     };
    return r;
   }

  Bool mergeLeft(Branch Left)                                                                                           // Merge the supplied branch into the left hand side of this branch
   {final Branch right = this;
    final Int    lc    = Left .count();
    final Int    rc    = right.count();
    final Bool   r     = new Bool().clear();

    new If (lc.Add(rc).lt(maxSize()))
     {void Then()
       {r.set();
        final Slots Ls = Left .slots;
        final Slots rs = right.slots;
        Left .compactLeft();    right.compactRight();                                                                   // Compact so both the slots and keys are in opposing extremal positions to avoid collisions when we merge
        Ls.compactKeysLeft ((S, t, s)->{Left .data(t, Left .data(s));});
        rs.compactKeysRight((S, t, s)->{right.data(t, right.data(s));});

        final Int lt = Left .top();                                                                                     // Left top
        final Int rt = right.top();                                                                                     // Right top
        right.copyMergeData(Left, new Int(0), new Int(lc));                                                             // Copy the left data values into the right data values
        right.data(lc, lt);                                                                                             // Place left top in left data values
        rs.mergeFromLeftOdd(Ls, null);                                                                                  // Split the slots
       }
     };
    return r;
   }

  int splitSize() {return maxSize()>>>1;}                                                                               // Size of a split branch

//D1 Iterate                                                                                                            // Iterate over a branch

  interface Iterator                                                                                                    // Process a key data, value in a branch when iterating over the branch
   {void process(Int Key, Int Data);
   }

  void iterate(Iterator Iterator)                                                                                       // Iterate over a leaf
   {final Int f = slots.usedSlotsToKeys.firstOne();
    new If (f.valid())
     {void Then()
       {new For(maxSize)
         {void body(Int Index, Bool Continue)
           {final Int k = slots.getSlotToKeyValue(f), d = data(slots.getSlotToKeyIndex(f));
            new I() {void action() {Iterator.process(k, d);}};
            f.copy(slots.usedSlotsToKeys.nextOne(f));
            Continue.set(f.valid());
           }
         };
       }
     };                                                                                //
   }

//D1 Print                                                                                                              // Print the branch

  StringBuilder print()                                                                                                 // Print the branch
   {final StringBuilder s = new StringBuilder();

    new I() {void action() {s.setLength(0); s.append(f("Branch"));}};

    final Int a = new Int().copy(getLocation());                                                                        // Index in memory if present
    new If (a.valid())
     {void Then()
       {new If (a.gt(0))
         {void Then()
           {new I() {void action() {s.append(f(" at: "+formatKey, a.i())); }};
           }
          void Else()
           {new I() {void action() {s.append(" ".repeat(8)); }};
           }
         };
       }
      void Else()
       {new I() {void action() {s.append(" ".repeat(8)); }};
       }
     };

    new I()
     {void action()
       {s.append(f(" size: "+formatKey, maxSize()));
        s.append(f(" top: " +formatKey, refTop.getInt(0)));
        s.append("\n Ref   Key  Data\n");

        for (int i : range(slots.numberOfSlotsToKeys()))
         {if (slots.getSlotToKeysInUse(i))
           {final String f = "%4d  %4d  %4d\n";
            final int    k = slots.getSlotToKeyIndex(i);
            final String t = f(f, k, slots.getSlotToKeyValue(i), refData.getInt(k));
            s.append(t);
           }
         }
       }
     };
    return s;
   }

  public String toString() {return ""+print();}                                                                         // Print branch

//D1 Tests                                                                                                              // Tests

  static void test_branch(boolean Ex)
   {final Branch l = new Branch(new Build().maxSize(7).immediate(Ex));
    l.initializeMemory();
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    //new I() {void action() {testStop("AAAA", l);}};
    l.check(l.print(), """
Branch         size:   7 top:   0
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

  static void test_compactLeft(boolean Ex)
   {final Branch l = new Branch(new Build().maxSize(7).immediate(Ex));
    l.initializeMemory();
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    l.delete(l.new Int(2));
    //l.new I() {void action() {testStop(l);}};
    l.check(l.print(), """
Branch         size:   7 top:   0
 Ref   Key  Data
   3     1    11
   2     3    33
   1     4    44
""");
    l.compactLeft();
    //l.new I() {void action() {testStop(l);}};
    l.check(l.print(), """
Branch         size:   7 top:   0
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

  static void test_compactRight(boolean Ex)
   {final Branch l = new Branch(new Build().maxSize(7).immediate(Ex));
    l.initializeMemory();
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    //l.new I() {void action() {testStop(l);}};
    l.check(l.print(), """
Branch         size:   7 top:   0
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
""");
    l.compactRight();
    //l.new I() {void action() {testStop(l);}};
    l.check(l.print(), """
Branch         size:   7 top:   0
 Ref   Key  Data
   3     1    11
   6     2    22
   4     3    33
   5     4    44
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
    l.initializeMemory();
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    l.insert(l.new Int(6), l.new Int(66));
    l.insert(l.new Int(7), l.new Int(77));
    l.insert(l.new Int(5), l.new Int(55));
    l.top(l.new Int(99));
    //l.new I() {void action() {testStop(l);}};
    l.check(l.print(), """
Branch         size:   7 top:  99
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
   6     5    55
   4     6    66
   5     7    77
""");
    final Branch r = new Branch(new Build().maxSize(7).immediate(Ex).parent(l));
    r.initializeMemory();
    l.splitRight(r).ok(4);
    //l.new I() {void action() {testStop(l);}};
    l.check(l.print(), """
Branch         size:   7 top:  44
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
""");
    //l.new I() {void action() {testStop(r);}};
    l.check(r.print(), """
Branch         size:   7 top:  99
 Ref   Key  Data
   6     5    55
   4     6    66
   5     7    77
""");
    l.maxSteps = 99999;
    l.execute();
   }

  static void test_splitRight()
   {test_splitRight(true);
    test_splitRight(false);
   }

  static void test_splitLeft(boolean Ex)
   {final Branch r = new Branch(new Build().maxSize(7).immediate(Ex));
    r.initializeMemory();
    r.insert(r.new Int(2), r.new Int(22));
    r.insert(r.new Int(4), r.new Int(44));
    r.insert(r.new Int(3), r.new Int(33));
    r.insert(r.new Int(1), r.new Int(11));
    r.insert(r.new Int(6), r.new Int(66));
    r.insert(r.new Int(7), r.new Int(77));
    r.insert(r.new Int(5), r.new Int(55));
    r.top(r.new Int(99));
    //r.new I() {void action() {testStop(r);}};
    r.check(r.print(), """
Branch         size:   7 top:  99
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
   6     5    55
   4     6    66
   5     7    77
""");
    final Branch l = new Branch(new Build().maxSize(7).immediate(Ex).parent(r));
    r.splitLeft(l).ok(4);
    //r.new I() {void action() {testStop(l);}};
    l.check(l.print(), """
Branch         size:   7 top:  44
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
""");
    //r.new I() {void action() {testStop(r);}};
    r.check(r.print(), """
Branch         size:   7 top:  99
 Ref   Key  Data
   6     5    55
   4     6    66
   5     7    77
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
    l.initializeMemory();
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    l.insert(l.new Int(6), l.new Int(66));
    l.insert(l.new Int(7), l.new Int(77));
    l.insert(l.new Int(5), l.new Int(55));
    l.top(l.new Int(99));
    //l.new I() {void action() {testStop(l);}};
    l.check(l.print(), """
Branch         size:   7 top:  99
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
   6     5    55
   4     6    66
   5     7    77
""");
    final Branch r = new Branch(new Build().maxSize(7).immediate(Ex).parent(l));
    l.splitRight(r);
    //l.new I() {void action() {testStop(l);}};
    l.check(l.print(), """
Branch         size:   7 top:  44
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
""");
    //r.new I() {void action() {testStop(r);}};
    r.check(r.print(), """
Branch         size:   7 top:  99
 Ref   Key  Data
   6     5    55
   4     6    66
   5     7    77
""");
    l.mergeRight(r);
    //l.new I() {void action() {testStop(l);}};
    l.check(l.print(), """
Branch         size:   7 top:  99
 Ref   Key  Data
   1     1    11
   0     2    22
   2     3    33
   3     4    44
   6     5    55
   4     6    66
   5     7    77
""");
    l.maxSteps = 99999;
    l.execute();
   }

  static void test_mergeRight()
   {test_mergeRight(true);
    test_mergeRight(false);
   }

  static void test_mergeLeft(boolean Ex)
   {final Branch r = new Branch(new Build().maxSize(7).immediate(Ex));
    r.initializeMemory();
    r.insert(r.new Int(2), r.new Int(22));
    r.insert(r.new Int(4), r.new Int(44));
    r.insert(r.new Int(3), r.new Int(33));
    r.insert(r.new Int(1), r.new Int(11));
    r.insert(r.new Int(6), r.new Int(66));
    r.insert(r.new Int(7), r.new Int(77));
    r.insert(r.new Int(5), r.new Int(55));
    r.top(r.new Int(99));
    // r.new I() {void action() {testStop(r);}};
    r.check(r.print(), """
Branch         size:   7 top:  99
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
   6     5    55
   4     6    66
   5     7    77
""");
    final Branch l = new Branch(new Build().maxSize(7).immediate(Ex).parent(r));
    r.splitLeft(l);
    //r.new I() {void action() {testStop(l);}};
    l.check(l.print(), """
Branch         size:   7 top:  44
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
""");
    //r.new I() {void action() {testStop(r);}};
    r.check(r.print(), """
Branch         size:   7 top:  99
 Ref   Key  Data
   6     5    55
   4     6    66
   5     7    77
""");
    r.mergeLeft(l);
    //r.new I() {void action() {testStop(r);}};
    r.check(r.print(), """
Branch         size:   7 top:  99
 Ref   Key  Data
   1     1    11
   0     2    22
   2     3    33
   3     4    44
   6     5    55
   4     6    66
   5     7    77
""");
    r.maxSteps = 99999;
    r.execute();
   }

  static void test_mergeLeft()
   {test_mergeLeft(true);
    test_mergeLeft(false);
   }

  static void test_find(boolean Ex)
   {final Branch l = new Branch(new Build().maxSize(7).immediate(Ex));
    l.initializeMemory();
    l.insert(l.new Int(2), l.new Int(22));
    l.insert(l.new Int(4), l.new Int(44));
    l.insert(l.new Int(3), l.new Int(33));
    l.insert(l.new Int(1), l.new Int(11));
    l.insert(l.new Int(6), l.new Int(66));
    l.insert(l.new Int(7), l.new Int(77));
    l.insert(l.new Int(5), l.new Int(55));
    //l.new I() {void action() {testStop(l);}};
    l.check(l.print(), """
Branch         size:   7 top:   0
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
   6     5    55
   4     6    66
   5     7    77
""");
    l.find(l.new Int(0)).notValid().ok(true);
    l.find(l.new Int(1)).ok(11);
    l.find(l.new Int(2)).ok(22);
    l.find(l.new Int(3)).ok(33);
    l.find(l.new Int(4)).ok(44);
    l.find(l.new Int(5)).ok(55);
    l.find(l.new Int(6)).ok(66);
    l.find(l.new Int(7)).ok(77);
    l.find(l.new Int(8)).notValid().ok(true);

    l.delete(l.new Int(2)).ok(22); l.find(l.new Int(2)).notValid().ok(true); l.count().ok(6);
    l.delete(l.new Int(1)).ok(11); l.find(l.new Int(1)).notValid().ok(true); l.count().ok(5);
    l.delete(l.new Int(4)).ok(44); l.find(l.new Int(4)).notValid().ok(true); l.count().ok(4);
    l.delete(l.new Int(3)).ok(33); l.find(l.new Int(3)).notValid().ok(true); l.count().ok(3);
    l.delete(l.new Int(6)).ok(66); l.find(l.new Int(6)).notValid().ok(true); l.count().ok(2);
    l.delete(l.new Int(7)).ok(77); l.find(l.new Int(7)).notValid().ok(true); l.count().ok(1);
    l.delete(l.new Int(5)).ok(55); l.find(l.new Int(5)).notValid().ok(true); l.count().ok(0);
    l.check(l.print(), """
Branch         size:   7 top:   0
 Ref   Key  Data
""");

    l.maxSteps = 99999;
    l.execute();
   }

  static void test_find()
   {test_find(true);
    test_find(false);
   }

  static void test_iterate(boolean Ex)
   {new Branch(new Build().maxSize(7).immediate(Ex))
     {@Override void branchCode()
       {initializeMemory();
        insert(new Int(2), new Int(22));
        insert(new Int(4), new Int(44));
        insert(new Int(3), new Int(33));
        insert(new Int(1), new Int(11));
        insert(new Int(6), new Int(66));
        insert(new Int(7), new Int(77));
        insert(new Int(5), new Int(55));
        top(new Int(88));
        check(print(), """
Branch         size:   7 top:  88
 Ref   Key  Data
   3     1    11
   0     2    22
   2     3    33
   1     4    44
   6     5    55
   4     6    66
   5     7    77
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
""");

        maxSteps = 99999;
        execute();
       }
     };
   }

  static void test_iterate()
   {test_iterate(true);
    test_iterate(false);
   }

  static void test_fixedFields(boolean Ex)
   {final int A = 22, B = 21;
    final Branch l = new Branch(new Build().maxSize(7).immediate(Ex))
     {void branchCode()
       {final Branch l = this;
        l.initializeMemory();
        l.data(new Int(1), new Int(A));
        l.data(new Int(1))  .ok(A);
        final Branch r = new Branch(new Build().maxSize(7).immediate(Ex).parent(l));
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
   {test_fixedFields(true);
    test_fixedFields(false);
   }

  static void test_stepDown(boolean Ex)
   {final int    N = 4;
    final Branch a = new Branch(new Build().maxSize(N-1).immediate(Ex))
     {void branchCode()
       {initializeMemory();
        insert(new Int(10), new Int(1));
        insert(new Int(20), new Int(2));
        insert(new Int(30), new Int(3));
        top   (             new Int(4));
        final Branch.StepDown s1 = stepDown(new Int(02)); s1.node.ok(1); s1.slot.ok(1);
        final Branch.StepDown s2 = stepDown(new Int(12)); s2.node.ok(2); s2.slot.ok(4);
        final Branch.StepDown s3 = stepDown(new Int(22)); s3.node.ok(3); s3.slot.ok(5);
        final Branch.StepDown s4 = stepDown(new Int(32)); s4.node.ok(4); s4.slot.notValid().ok(true);
        execute();
       }
     };
   }

  static void test_knownInsert(boolean Ex)
   {new Branch(new Build().maxSize(7).immediate(Ex))
     {@Override void branchCode()
       {initializeMemory();
        insert(new Int(4), new Int(44), new Int());
        check(print(), """
Branch         size:   7 top:   0
 Ref   Key  Data
   0     4    44
""");
        ok(()->slots, """
Slots    : refs:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   0   0   0   0   0   0   0   0   0   0   0   0
keysSlots:    7   0   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   .   X   .   .   .   .   .   .
usedKeys :    X   .   .   .   .   .   .
keys     :    4   0   0   0   0   0   0
""");

        insert(new Int(2), new Int(22), new Int(7));
        check(print(), """
Branch         size:   7 top:   0
 Ref   Key  Data
   1     2    22
   0     4    44
""");
        ok(()->slots, """
Slots    : refs:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   0   1   0   0   0   0   0   0   0   0   0   0
keysSlots:    7   3   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   X   .   .   .   X   .   .   .   .   .   .
usedKeys :    X   X   .   .   .   .   .
keys     :    4   2   0   0   0   0   0
""");

        insert(new Int(6), new Int(66), new Int());
        check(print(), """
Branch         size:   7 top:   0
 Ref   Key  Data
   1     2    22
   0     4    44
   2     6    66
""");
        ok(()->slots, """
Slots    : refs:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slotsKeys:    0   0   0   1   0   0   0   0   0   0   0   2   0   0
keysSlots:    7   3  11   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   X   .   .   .   X   .   .   .   X   .   .
usedKeys :    X   X   X   .   .   .   .
keys     :    4   2   6   0   0   0   0
""");
        maxSteps = 99999;
        execute();
       }
     };
   }

  static void test_knownInsert()
   {test_knownInsert(true);
    test_knownInsert(false);
   }

  static void test_stepDown()
   {test_stepDown(true);
    test_stepDown(false);
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
    test_iterate();
    test_fixedFields();
    test_stepDown();
    test_knownInsert();
   }

  static void newTests()                                                                                                // Tests being worked on
   {oldTests();
    //test_knownInsert();
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
