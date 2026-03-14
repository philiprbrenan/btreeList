//------------------------------------------------------------------------------
// Btree with stucks implemented as distributed slots.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//------------------------------------------------------------------------------
// Investigate listAll introducing errors
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.util.*;
import java.nio.ByteBuffer;

class Tree extends Test                                                         // A tree that translates keys into values
 {final Int             maxLeafSize;                                            // The maximum number of entries in a leaf
  final Int           maxBranchSize;                                            // The maximum number of entries in a branch
  final Stack<Allocation> freeChain = new Stack<>();                            // Unallocated leaves and branches
  final Int   MaximumNumberOfLevels = new Int(99);                              // Maximum number of levels in tree to prevent runaways while debugging
  final Int           numberOfNodes;                                            // Maximum number of leaves plus branches in this tree
  final Int              sizeOfNode;                                            // The size of each node in the tree: a node may hold a branch or a leaf
  final Memory               memory;                                            // Memory containing the tree base followed by the leaves and branches of the tree
  final static String     formatKey = "%3d";                                    // Format a key for dumping during testing
  static boolean              debug = false, debug2 = false;                    // Debug if enabled

//D1 Construction                                                               // Construct and layout a tree

  Tree(int MaxLeafSize, int MaxBranchSize, int NumberOfNodes)                   // Create the tree
   {maxLeafSize   = new Int(MaxLeafSize);                                                // The maximum number of entries in a leaf
    maxBranchSize = new Int(MaxBranchSize);                                              // The maximum number of entries in a branch
    numberOfNodes = new Int(NumberOfNodes);                                              // The maximum number of leaves and branches combined

    final String m  = "The maximum ";
    final String m1 = m + "leaf size must be 2 or more, not: "   +MaxLeafSize;
    final String m2 = m + "branch size must be 3 or more, not: " +MaxBranchSize;
    final String m3 = m + "branch size must be odd, not: "       +MaxBranchSize;

    final boolean b1 = maxLeafSize     .lt(2),                                   // Size checks
                  b2 = maxBranchSize   .lt(3),
                  b3 = maxBranchSize.Mod(2).eq(0);

    if (b1 && !b2 && !b3) stop(m1); else if (b1) say(m1);
    if (b2        && !b3) stop(m2); else if (b2) say(m2);
    if (b3              ) stop(m3);
    for (int i = numberOfNodes.i(); i > 0; --i) freeChain.push(new Allocation(new Int(i)));  // Initial free chain. Each active leaf or branch resides in a node of the tree allocated from the free chain. Using a single node size greatly simplifies memory management which is crucial in long running processes like database systems.

    memory        = new Memory();                                               // Memory for the tree
    sizeOfNode    = memory.sizeOfNode;                                          // Size a node in the tree to be able to contain a leaf or a branch which makes it important to choose the number of slots in each so that they have about the same size
    memory.maxLeafSize  (maxLeafSize);                                          // Record the sizes of a leaf or a branch
    memory.maxBranchSize(maxBranchSize);
    memory.numberOfNodes(numberOfNodes);
   }

  Tree(int LeafSize)                {this(LeafSize, LeafSize-1);}               // Create a test tree
  Tree(int LeafSize, int BranchSize){this(LeafSize, BranchSize, 999);}          // Create a test tree

  Int maxLeafSize  () {return maxLeafSize;}                                     // Maximum size of a leaf
  Int maxBranchSize() {return maxBranchSize;}                                   // Maximum size of a branch
  Int numberOfNodes() {return numberOfNodes;}                                   // Maximum number of nodes in tree
  Int           mnl() {return MaximumNumberOfLevels;}                           // Maximum number of levels

  static final class Key extends Int                                            // A key
   {Key(  int Value)  {i(Value);}
    Key(  Int Value)  {i(Value);}
    Int       value() {return this;}
    public String toString()
     {return "Key : "+i();
     }
   }

  static Key Key(int Value) {return new Key(Value);}                            // Create a key with the specified value
  static Key Key(Int Value) {return new Key(Value);}                            // Create a key with the specified value

  static final class Data extends Int                                           // An item of data associated with a key
   {Data(int Value) {i(Value);}
    Data(Int Value) {i(Value);}
    Int     value() {return this;}
    public String toString()
     {return "Data: "+i();
     }
   }

//D1 Allocation                                                                 // Allocate or free a leaf or branch

  enum NodeType {Leaf, Branch}                                                  // Types of nodes

  static Int ib()      {return new Int(Integer.BYTES);}                         // Number of bytes in an integer
  static Int ib(Int I) {return I.Mul(ib());}                                    // Number of bytes in a number of integers

  static class TreeMemoryPositions                                              // Memory positions of fields describing the tree
   {final Int posRoot          = new Int(0);
    final Int posMaxLeafSize   = posRoot          .Add(ib());                   // The maximum number of entries in a leaf
    final Int posMaxBranchSize = posMaxLeafSize   .Add(ib());                   // The maximum number of entries in a branch
    final Int posNumberOfNodes = posMaxBranchSize .Add(ib());                   // Maximum number of nodes in the tree
    final Int size             = posNumberOfNodes .Add(ib());                   // Size of memory in bytes
    Int memorySize() {return size;}                                             // Get the size of memory in bytes
   }

  class LeafMemoryPositions                                                     // Memory positions of fields
   {final Int posData    = getMemorySize(maxLeafSize);                          // Size of slots for a leaf
    final Int posUp      = posData    .Add(ib(maxLeafSize()));                  // Reference to a parent branch if there is one
    final Int posUpIndex = posUp      .Add(ib());                               // Position of the reference to this leaf in the parent branch if there is one, or null if this is the top of the branch
    final Int size       = posUpIndex .Add(ib());                               // Size of the memory
    Int memorySize() {return size;}                                             // Get the size of memory in bytes
   }

  class BranchMemoryPositions                                                   // Memory positions of fields
   {final Int posTop     = getMemorySize(maxBranchSize);                        // Size of slots for a branch
    final Int posData    = posTop     .Add(ib());                               // Position of references to leaves in this branch
    final Int posUp      = posData    .Add(ib(maxBranchSize));                  // Reference to a parent branch if there is one
    final Int posUpIndex = posUp      .Add(ib());                               // Position of the reference to this leaf in the parent branch if there is one, or null if this is the top of the branch
    final Int size       = posUpIndex .Add(ib());                               // Size of the memory
    Int memorySize() {return size;}                                             // Get the size of memory in bytes
   }

  class Allocation extends Int                                                  // An allocated node that could become a leaf or a branch or a tree base
   {Allocation(int At) {i(At);}
    Allocation(Int At) {i(At);}
    Int at()           {return this;}
   }

  Allocation allocate()                                                         // Allocate a leaf or a branch
   {if (freeChain.size() == 0) stop("No more leaves or branches");
    return freeChain.pop();
   }

  void free(Allocation Free)                                                    // Free a leaf or a branch
   {final Int f = Free.at();
    if (f.le(0)) stop("Name of node to free must be positive not:", f);
    if (f.gt(numberOfNodes)) stop("Name of node to free too big:",   f);
    if (freeChain.contains(f))
     {stop("Attempting to free a branch or leaf that has already been freed:",
            Free);
     }
    freeChain.push(Free);
   }

  void freeCheck()                                                              // Check that the free chain is accurate - useful during debugging
   {if (true) return;
    final ListAll      a = new ListAll();
    final Set<Integer> f = new TreeSet<>();
    final Set<Integer> u = new TreeSet<>();

    for(Allocation i : freeChain) f.add(i.at().i());

    for(Leaf l : a.leaves)
     {final Allocation  n = l.name();
      if (freeChain.contains(n)) stop("Leaf on free chain and in tree:",n.at());
      u.add(n.at().i());
      ((Slots)l).memory.usedSlotsBits.integrity();
      ((Slots)l).memory.usedRefsBits .integrity();
     }
    for(Branch b : a.branches)
     {final Allocation  n = b.name();
      if (freeChain.contains(n)) stop("Branch on free chain and in tree:", n);
      u.add(n.at().i());
      ((Slots)b).memory.usedSlotsBits.integrity();
      ((Slots)b).memory.usedRefsBits .integrity();
     }
    for (int i : range(1, numberOfNodes.i()))                                   // Node 0 is the tree base so it is not a leaf or a branch
     {if ( u.contains(i) && !f.contains(i)) continue;
      if (!u.contains(i) &&  f.contains(i)) continue;
      stop("Leaf or branch not in tree and not on free chain:", i);
     }
   }

  Slots fake(Allocation Name)                                                   // Slots used during testing to mock attached branches and leaves
   {final Slots s = new Slots(2);
    s.name(Name);
    return s;
   }

//D1 Slots                                                                      // Slots are used to describe leaves and branches in the tree

  class Slots                                                                   // Maintain key references in ascending order using distributed slots
   {final Int        numberOfRefs;                                              // Number of references which should be equal to or smaller than the number of slots as slots are narrow and references are wide allowing us to use more slots effectively
    final Int redistributionWidth;                                              // Redistribute if the next slot is further than this
    Memory                 memory;                                              // Memory used by the slots. Cannot be final until we can call stuff before constructing super

//D2 Construction                                                               // Construct and layout the slots

    Slots(int NumberOfRefs, ByteBuffer Bytes)                                   // Create the slots using the specified memory
     {numberOfRefs        = new Int(NumberOfRefs);                              // Number of slots referenced
      redistributionWidth = numberOfRefs.Sqrt();                                // Redistribute the slots if we see a run of more than these that are all occupied to make insertion easier.
      if (Bytes == null)                                                        // Memory used by the slots
       {memory = new Memory();                                                  // Create memory used by the slots
        initialize();                                                           // Initialize memory
       }
      else memory = new Memory(Bytes);                                          // Memory used by the slots
     }

    Slots(int NumberOfRefs) {this(NumberOfRefs, null);}                         // Create the slots and some memory to hold them

    Slots(int NumberOfRefs, boolean usable)                                     // Create the slots just to find out how big they will be
     {numberOfRefs        = new Int(NumberOfRefs);                                       // Number of slots referenced
      redistributionWidth = new Int(0);
      memory              = new Memory();
      initialize();
     }

    void setMemory(ByteBuffer Bytes) {memory = new Memory(Bytes);}              // Set memory to be used

    Slots duplicateSlots()                                                      // Copy the source slots
     {final Slots t = new Slots(numberOfRefs.i());
      t.copySlots(this);
      return t;                                                                 // The copied slots
     }

    Slots copySlots(Slots Source)                                               // Copy the source slots
     {final Allocation n = name();                                              // Save name of target
      memory.copySlots(Source.memory);                                          // Copy memory
      name(n);                                                                  // Reload name of target
      return this;                                                              // The copied slots
     }

    void invalidate  () {memory.invalidate();}                                  // Invalidate the slots in such a way that they are unlikely to work well if subsequently used
    Int numberOfRefs () {return numberOfRefs;}
    Int numberOfSlots() {return numberOfRefs().Mul(2);}                         // Number of slots from number of refs

    void initialize()                                                           // Clear all the slots
     {memory.usedSlotsBits.initialize();
      memory.usedRefsBits .initialize();
     }

    final class slot extends Int                                                // A dereferenced slot
     {slot( int Value)  {i(Value);}                                             // A key
      slot( Int Value)  {i(Value);}                                             // A key
      Int       value() {return this;}    // Is this needed
      public String toString()
       {return "slot: "+value();
       }
     }

    final class Slot extends Int                                                // A reference to a slot
     {Slot(int Value)  {i(Value);}                                             // A key
      Slot(Int Value)  {i(Value);}                                             // A key
      Int       value() {return this;}                                          // The value of the key
      Int         Int() {return this;}                                          // The value of the key
      Slot      right() {return new Slot(Inc());}                               // Step right
      Slot       left() {return new Slot(Dec());}                               // Step left

      Slot stepLeft()                                                           // Step left to prior occupied slot assuming that such a step is possible
       {final BitSet.Pos q = memory.usedSlotsBits.new Pos(value());
        final BitSet.Pos p = memory.usedSlotsBits.prevOne(q);
        return p != null ? new Slot(p.position()) : null;
       }

      Slot stepRight()                                                          // Step right to the next occupied slot assuming that such a step is possible
       {final BitSet.Pos q = memory.usedSlotsBits.new Pos(value());
        final BitSet.Pos p = memory.usedSlotsBits.nextOne(q);
        return p != null ? new Slot(p.position()) : null;
       }

      Slot locatePrevUsedSlot()                                                 // Absolute position of this slot if it is in use or else the next lower used slot
       {return usedSlots(this) ? this : stepLeft();
       }

      Slot locateNextUsedSlot()                                                 // Absolute position of this slot if it is in use or else the next lower used slot
       {return usedSlots(this) ? this : stepRight();
       }

      boolean eq(Key Key) {return Key.value().eq(keys(this).value());}           // Search key is equal to indexed key
      boolean le(Key Key) {return Key.value().le(keys(this).value());}           // Search key is less than or equal to indexed key
      boolean lt(Key Key) {return !eq(Key) && le(Key);}                         // Search key is less than or equal to indexed key
      boolean ge(Key Key) {return  eq(Key) || gt(Key);}                         // Search key is less than or equal to indexed key
      boolean gt(Key Key) {return !le(Key);}                                    // Search key is less than or equal to indexed key

      public String toString()
       {return "Slot: "+i();
       }
     }

//D2 Keys                                                                       // Define a key

    Key firstKey()                                                              // First key in slots
     {if (empty()) stop("No first key in empty slots");                         // First key in slots if there is one
      return keys(locateFirstUsedSlot());
     }

    Key lastKey()                                                               // Last key in slots
     {if (empty()) stop("No last key in empty slots");                          // Last key in slots if there is one
      return keys(locateLastUsedSlot());
     }

//D2 Slots                                                                      // Manage the slots

    void setSlots(int...Slots)                                                  // Set slots as used
     {for (int i : range(Slots.length)) usedSlots(new Slot(new Int(Slots[i])), true);
     }

    void clearSlots(int...Slots)                                                // Set slots as not being used
     {for (int i : range(Slots.length)) usedSlots(new Slot(new Int(Slots[i])), false);
     }

    void clearFirstSlot()                                                       // Set the first used slot to not used
     {final Slots.Slot f = locateFirstUsedSlot();
      new If (f != null) {void Then() {usedSlots(f, false);}};
     }

    void clearSlotAndRef(Slot I) {freeRef(new slot(memory.slots    (I))); clearSlots(I.i());} // Remove a key from the slots
    slot           slots(Slot I) {return  new slot(memory.slots    (I));}                     // The indexed slot
    boolean    usedSlots(Slot I) {return           memory.usedSlots(I);}                      // The indexed slot usage indicator
    boolean     usedRefs(slot I) {return           memory.usedRefs (I);}                      // The indexed reference usage indicator
    Key             keys(Slot I) {return   new Key(memory.keys(memory.slots(I)));}            // The indexed key

    void     slots(Slot I, slot    Ref)   {memory.slots    (I, Ref.value());}                 // The indexed slot
    void usedSlots(Slot I, boolean Value) {memory.usedSlots(I, Value);}                       // The indexed slot usage indicator
    void  usedRefs(slot I, boolean Value) {memory.usedRefs (I, Value);}                       // The indexed reference usage indicator
    void      keys(Slot I, Key     Key)   {memory.keys(memory.slots(I), Key);}                // The indexed key

    Key  key(slot I) {return new Key(memory.keys(I));}                      // Get the key directly
    void key(slot I, Key Key)       {memory.keys(I, Key);}                  // Set the key directly

    Allocation name() {return new Allocation(memory.name());}                   // Get the name
    void name(Allocation Name)              {memory.name(Name.at());}           // Set the name

    Int  type()  {return memory.type();}                                        // Get the type
    void type(Int Type) {memory.type(Type);}                                    // Set the type

//D2 Refs                                                                       // Allocate and free references to keys

    slot allocRef()                                                             // Allocate a reference to one of the keys in the slots. A linear search is used here because in hardware this will be done in parallel
     {final slot I = locateFirstEmptyRef();

      if (I != null)
       {usedRefs(I, true);
        return I;
       }
      stop("No more slots available in this set of slots");
      return null;
     }

    void freeRef(slot Ref) {usedRefs(Ref, false);}                              // Free a reference to one of the keys in the slots

//D2 Statistics                                                                 // Query the state of the slots

    Int countUsed()                                                             // Number of slots in use. How can we do this quickly in parallel?
     {final Int n = new Int(0);
      new For(numberOfSlots())
       {boolean body(int i)
         {new If (usedSlots(new Slot(new Int(i)))) {void Then(){n.inc();}};
          return true;
         }
       };
      say("AAAA". n); 
      return n;
     }

    boolean empty() {return memory.usedSlotsBits.empty();}                      // All bits in the corresponding bitset are unused so the Slots must be empty
    boolean full () {return countUsed() == numberOfRefs;}                       // The number of bits in the bitset slots is either equal to or greater than the number of slots so we cannot rely on them being simultaneously full

    boolean adjacentUsedSlots(Int Start, Int Finish)                            // Checks whether two used slots are adjacent
     {if (!usedSlots(new Slot(Start)))  stop("Start  slot  must be occupied but it is empty, slot:", Start);
      if (!usedSlots(new Slot(Finish))) stop("Finish slot  must be occupied but it is empty, slot:", Finish);
      if (Start.ge(Finish))              stop("Start must precede finish:", Start, Finish);

      for (int i : range(Start.Inc().i(), Finish.i()))                          // From start to finish looking for an intermediate used slot
       {if (usedSlots(new Slot(new Int(i)))) return false;
       }
      return true;
     }

//D2 Low level operations                                                       // Low level operations on slots

    Int locateNearestFreeSlot(Slot Position)                                    // Relative position of the nearest free slot to the indicated position if there is one.
     {final Int r = new Int(0);
      if (usedSlots(Position))                                                  // The slot is not free already. If it is not free we do at least get an error if the specified position is invalid
       {final Int Q = Position.value();
        final BitSet     s = memory.usedSlotsBits;
        final BitSet.Pos p = s.prevZero(s.new Pos(Q));                          // Prev free slot
        final BitSet.Pos n = s.nextZero(s.new Pos(Q));                          // Next free slot
        if (p == null && n == null) stop("No more free slots");
        if (p == null && n != null) return n.position().Sub(Q);                  // Next free slot because no prev free slot
        if (p != null && n == null) return p.position().Sub(Q);                  // Prev free slot because no next free slot
        final Int P = p.position().Sub(Q), N = n.position().Sub(Q);              // Relative positions
        r.i((P.Neg().le(N) ? P : N));                                                   // Choose nearest slow favoring lower slot if they are both the same distance away
       }
      return r;
     }

    Slot locateFirstUsedSlot()                                                  // Absolute position of this slot if it is in use or else the next lower used slot
     {final BitSet.Pos p = memory.usedSlotsBits.firstOne();
      return p != null ? new Slot(p.position()) : null;
     }

    Slot locateLastUsedSlot()                                                   // Absolute position of the last slot in use
     {final BitSet.Pos p = memory.usedSlotsBits.lastOne();
      return p != null ? new Slot(p.position()) : null;
     }

    slot locateFirstEmptyRef()                                                  // Absolute position of the first empty reference
     {final BitSet.Pos p = memory.usedRefsBits.firstZero();
      return p != null ? new slot(p.position()) : null;
     }

    void shift(Int Position, Int Width)                                         // Shift the specified number of slots around the specified position one bit left or right depending on the sign of the width.  The liberated slot is not initialized.
     {new If (Width.ne(0))                                                      // Non zero shift
       {void Then()
         {final boolean p = Width.gt(0);
          new For(p ? Width : Width.neg())                                      // Move each slot
           {boolean body(int i)
             {final Slot P = new Slot(Position.Add(Width).add(p ? -i : +i));
              slots(P, slots(p ? P.left() :  P.right()));                       // Move slot
              return true;
             }
           };
          usedSlots(new Slot(Position.Add(Width)), true);                       // We only move occupied slots
         }
       };
     }

    void redistribute()                                                         // Redistribute the unused slots evenly with a slight bias to having a free slot at the end to assist with data previously sorted into ascending order
     {new If (!empty())                                                         // Something to redistribute
       {void Then()
         {final Int         N = new Int(numberOfSlots());                       // Maximum number of slots
          final Int         c = new Int(countUsed());                           // Number of slots in use
          final Int     space = new Int(N.Sub(c).div(c));                       // Space between used slots
          final Int     cover = new Int(space.Inc().mul(c.Dec()).inc());        // Covered space from first used slot to last used slot,
          final Int remainder = new Int(max(0, N.Sub(cover).i()));              // Uncovered remainder
          final Int       []s = new Int [N.i()];                                // New slots distribution
          final Bool      []u = new Bool[N.i()];                                // New used slots distribution
          final Int         p = new Int(remainder.Down());                      // Start position for first used slot
          new For(N)                                                            // Redistribute slots
           {boolean body(int i)                                                 // Initialize background of slots
             {s[i] = new Int (0);
              u[i] = new Bool().clear();
              return true;
             };
           };
          new For(N)                                                            // Redistribute slots
           {boolean body(int i)
             {final Slot I = new Slot(i);
              new If (usedSlots(I))                                             // Redistribute active slots
               {void Then()
                 {s[p.i()] = new Int(slots(I).value());
                  u[p.i()].set();
                  p.add(space).inc();                                           // Spread the used slots out
                 }
               };
              return true;
             }
           };

          memory.usedSlotsBits.initialize();                                    // Clear the existing tree bits - faster than deleting each path in turn

          new For(N)                                                            // Copy redistribution back into original avoiding use of java array methods to make everything explicit for hardware conversion
           {boolean body(int i)
             {final Slot I = new Slot(i);
              slots(I, new slot(s[i].i()));
              usedSlots(I, u[i].b());
              return true;
             }
           };
         }
       };
     }

    void reset()                                                                // Reset the slots
     {new For(numberOfSlots()) {boolean body(int i) {slots(new Slot(i), new slot(0)); return true;}};
      new For(numberOfRefs   ) {boolean body(int i) {  key(new slot(i), Key(0));      return true;}};

      initialize();                                                             // Clear the existing tree bits - faster than deleting each path in turn
     }

    void compactSlot(Slot P, slot Q, Key K)                                     // Compact a slot
     {usedSlots(P, true);
       usedRefs(Q, true);
          slots(P, Q);
           keys(P, K);
     }

    void compactLeft()                                                          // Compact the used slots to the left end
     {new If (!empty())                                                         // Something to compact
       {void Then()
         {final Slots d = duplicateSlots();
          reset();
          final Int p = new Int(0);
          new For(numberOfSlots())                                              // Each slot
           {boolean body(int i)
             {final Slot I = new Slot(i);
              new If (d.usedSlots(I))                                           // Each used slot
               {void Then()
                 {compactSlot(new Slot(p.i()), new slot(p.i()), d.keys(I));
                  p.inc();
                 }
               };
              return true;
             }
           };
         }
       };
     }

    void compactRight()                                                         // Compact the used slots to the left end
     {new If (!empty())                                                         // Something to compact
       {void Then()
         {final Slots d = duplicateSlots(); reset();
          final Int p = numberOfRefs.Dec();
          final Int N = numberOfSlots();
          new For(N)
           {boolean body(int i)
             {final Slot I = new Slot(N.Sub(i).dec());
              new If (d.usedSlots(I))                                           // Each used slot
               {void Then()
                 {compactSlot(new Slot(p.i()), new slot(p.i()), d.keys(I));
                  p.dec();
                 }
               };
              return true;
             }
           };
         }
       };
     }

    boolean mergeSlot(Slots S, Slot I, slot J)                                  // Merge a slot
     {final Bool m = new Bool().clear();                                        // Whether a successful merge occurred
      new If (S.usedSlots(I))
       {void Then()
         {    slots(I, S.    slots(I));
          usedSlots(I, S.usedSlots(I));
           usedRefs(J, S. usedRefs(J));
               keys(I, S.     keys(I));
          m.set();
         }
       };
      return m.b();
     }

    void mergeCompacted(Slots Left, Slots Right)                                // Merge left and right compacted slots into the current slots
     {final Slots l = Left, r = Right;
      reset();
      new For(numberOfRefs)                                                     // Each reference
       {boolean body(int i)
         {final Slot I = new Slot(i);                                           // The input slots have been compacted so this Slot will match the corresponding slot
          final slot J = new slot(i);
          if      (mergeSlot(l, I, J)) {}                                       // Merge on left
          else if (mergeSlot(r, I, J)) {}                                       // Merge on right
          else {usedSlots(I, false); usedRefs(J, false);}                       // Reset center
          return true;
         }
       };
     }

    boolean mergeBack(Slots Left, Slots Right)                                  // Merge the specified slots back into the current set of slots
     {Left.compactLeft(); Right.compactRight();
      mergeCompacted(Left, Right);
      return true;
     }

    boolean mergeOnRight(Slots Right)                                           // Merge the specified slots from the right
     {return countUsed().Add(Right.countUsed()).gt(numberOfSlots()) ? false :
             mergeBack(duplicateSlots(), Right.duplicateSlots());
     }

    boolean mergeOnLeft(Slots Left)                                             // Merge the specified slots from the left
     {return Left.countUsed().Add(countUsed()).gt(numberOfSlots()) ? false :
             mergeBack(Left.duplicateSlots(), duplicateSlots());
     }

//D2 High level operations                                                      // Find, insert, delete values in the slots

    public slot insert(Key Key)                                                 // Insert a key into the slots maintaining the order of all the keys in the slots and returning the index of the reference to the key
     {final slot alloc = allocRef();                                            // The location in which to store the search key
      key(alloc, Key);                                                          // Store the new key in the referenced location
      final Locate l = new Locate(Key);                                         // Search for the slot containing the key closest to their search key
      new If   (!l.above || !l.below)                                           // Not found
       {void Then()
         {new If (!l.above && !l.below)                                         // Empty place the key in the middle
           {void Then()
             {final Slot S = new Slot(new Int(numberOfSlots()).down().i());
              slots     (S, alloc);
              usedSlots (S, true);
             }
            void Else()
             {new If (l.above)                                                  // Insert their key above the found key
               {void Then()
                 {final Int i = l.at;
                  final Int w = new Int(locateNearestFreeSlot(l.at));           // Width of move and direction needed to liberate a slot here - we know there is one because we know the slots are not full
                  new If (w.gt(0))                                              // Move up
                   {void Then()                                                 // Move up
                     {shift             (i.Inc(), w.Dec());                     // Liberate a slot at this point
                      slots    (new Slot(i).right(), alloc);                // Place their current key in the empty slot, it has already been marked as set so there is no point in setting it again
                      usedSlots(new Slot(i).right(), true);
                     }
                    void Else()
                     {new If (w.lt(0))                                          // Liberate a slot below the current slot
                       {void Then()                                             // Liberate a slot below the current slot
                         {shift(         i,  w);                        // Shift any intervening slots blocking the slot below
                          slots(new Slot(i), alloc);                        // Insert into the slot below
                         }
                       };
                     }
                   };
                  new If (w.abs().ge(redistributionWidth))                    // Redistribute if the used slots are densely packed
                   {void Then()
                     {redistribute();
                     }
                   };
                 }
                void Else()
                 {new If (l.below)                                              // Insert their key below the found key
                   {void Then()
                     {final Int i = l.at;
                      final Int w = new Slot(locateNearestFreeSlot(l.at));      // Width of move and direction needed to liberate a slot here - we know there is one because we know the slots are not full
                      new If (w.gt(0))                                          // Move up
                       {void Then()                                             // Move up
                         {shift(i, w);                                          // Liberate a slot at this point
                          slots(l.at, alloc);                                   // Place their current key in the empty slot, it has already been marked as set so there is no point in setting it again
                         }
                        void Else()
                         {new If (w.lt(0))                                      // Liberate a slot below the current slot
                           {void Then()                                         // Liberate a slot below the current slot
                             {shift             (i.Dec(),  w.Inc());            // Shift any intervening slots blocking the slot below
                              slots    (new Slot(i).left(), alloc);             // Insert into the slot below
                              usedSlots(new Slot(i).left(), true);              // Mark the free slot at the start of the range of occupied slots as now in use
                             }
                           };
                         }
                       };
                      new If (w.Abs().ge(redistributionWidth))                  // Redistribute if the used slots are densely packed
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
      return alloc;                                                             // The index of the reference to the key
     }

    class Locate                                                                // Locate the slot containing the search key if possible else the key immediately above or below the search key.
     {Slot at;                                                                  // The point at which the closest key was found
      boolean above;                                                            // The search key is above or equal to the found key
      boolean below;                                                            // The search key is below or equal to the found key
      boolean all;                                                              // Above all or below all if true

      public String toString()                                                  // Print the location
       {if (exact()) return f("%d exact", at.value());
        return f("%2d %s %s %s", at.value(),
                                 above ? "above" : "",
                                 below ? "below" : "",
                                 all   ? "all"   : "");
       }

      void pos(Slot At, boolean Above, boolean Below)                           // Specify the position of the location
       {at = At; above = Above; below = Below;
       }

      void above(Slot At) {pos(At, true, false);}                               // Their search key is above this key
      void below(Slot At) {pos(At, false, true);}                               // Their search key is below this key
      void found(Slot At) {pos(At, true,  true);}                               // Found their search key
      void none ()        {}                                                    // Slots are empty

      boolean exact() {return above && below;}                                  // Oh America - my new found land.

      Locate(Key Key)                                                           // Locate the slot containing the search key if possible.
       {if (empty()) none();                                                    // Empty so their search key cannot be found
        else
         {final Ref<Slot> a = new Ref<>(locateFirstUsedSlot());                 // Lower limit
          final Ref<Slot> b = new Ref<>(locateLastUsedSlot ());                 // Upper limit
          final Bool      d = new Bool().clear();                               // Continue the search unless set
          new If (d.Flip().and(a.get().eq(Key))) {void Then() {d.set(); found(a.get());            }}; // Found at the start of the range
          new If (d.Flip().and(b.get().eq(Key))) {void Then() {d.set(); found(b.get());            }}; // Found at the end of the range
          new If (d.Flip().and(a.get().le(Key))) {void Then() {d.set(); below(a.get()); all = true;}}; // Smaller than any key
          new If (d.Flip().and(b.get().gt(Key))) {void Then() {d.set(); above(b.get()); all = true;}}; // Greater than any key

          new If (!d.b())                                                       // Search
           {void Then()
             {new For(numberOfSlots())                                          // Perform a reasonable number of searches knowing the key, if it is present, is within the current range. NB this is not a linear search, the slots are searched using binary search with an upper limit that has fooled some reviewers into thinking that a linear search is being performed.
               {boolean body(int i)
                 {final Slot M = new Slot(a.get().Add(b.get()).down());         // Desired mid point - but there might not be a slot in use at this point
                  final Slot A = M.locatePrevUsedSlot();                        // Occupied slot on or preceding mid point
                  final Slot B = M.locateNextUsedSlot();                        // Occupied slot on or succeeding mid point
                  final Bool D = new Bool().clear();                            // Continue the search unless set
                  final Int Ap = A.Int(), ap = a.get().Int();                   // New and current lower limit of range
                  final Int Bp = B.Int(), bp = b.get().Int();                   // New and current upper limit of range

                  new If (D.Flip().and(Ap.ne(ap), A.ge(Key))) {void Then() {D.set(); a.set(A);}}; // Make sure that the new range is tighter than the existing one
                  new If (D.Flip().and(Ap.ne(bp), A.le(Key))) {void Then() {D.set(); b.set(A);}};
                  new If (D.Flip().and(Bp.ne(ap), B.ge(Key))) {void Then() {D.set(); a.set(B);}};
                  new If (D.Flip().and(Bp.ne(bp), B.le(Key))) {void Then() {D.set(); b.set(B);}};
                  new If (D.Flip())                                             // The slots must be adjacent
                   {void Then()
                     {new If (D.Flip().and(a.get().eq(Key))) {void Then() {D.set(); found(a.get());}};
                      new If (D.Flip().and(b.get().eq(Key))) {void Then() {D.set(); found(b.get());}};
                      new If (D.Flip())                      {void Then() {D.set(); below(b.get());}};
                      d.set();                                                  // Search has completed
                     }
                   };
                  return !d.b();                                                // Continue search with new range
                 }
               };
              new If (d.Flip())                                                 // Incomplete search
               {void Then()
                 {stop("Searched unsuccessfully more than the maximum number of times:",
                       numberOfSlots());
                 }
               };
             }
           };
         }
       }
     }

    Slot locateFirstGe(Key Key)                                                 // Locate the slot containing the first key greater than or equal to the search key
     {final Locate l = new Locate(Key);
      final Slot   a = l.at;
      return a == null ? null : l.below ? a : a.right().locateNextUsedSlot();
     }

    Slot locate(Key Key)                                                        // Locate the slot containing the current search key if possible.
     {final Locate l = new Locate(Key);                                         // Locate the search key
      return l.exact() ? l.at : null;                                           // Found if exact match
     }

    slot find(Key Key)                                                          // Find the index of the current key in the slots
     {final Slot i = locate(Key);
      return i == null ? null : slots(i);
     }

    boolean delete(Key Key)                                                     // Delete the specified key from the slots
     {final Slot i = locate(Key);                                               // Locate the search key
      final Bool C = new Bool();
      new If (i == null)                                                        // Key not present so no need to delete it
       {void Then()
         {C.clear();                                                            // The key is not in the slots
         }
        void Else()
         {clearSlotAndRef(i);                                                   // Delete found key
          C.set();                                                              // Indicate that the key was deleted
         }
       };
      return C.b();
     }

//D2 Print                                                                      // Print the slots

    String printSlots()                                                         // Print the occupancy of each slot
     {final StringBuilder s = new StringBuilder();
      for (int i : range(numberOfSlots()))
       {s.append(usedSlots(new Slot(i)) ? "X" : ".");
       }
      return ""+s;
     }

    public String toString()                                                    // Dump the slots
     {final StringBuilder s = new StringBuilder();
      final int[]N = range(numberOfSlots());
      final int[]R = range(numberOfRefs);
      s.append(f("Slots    : name: %2d, type: %2d, refs: %2d\n",                // Title line
                              name().at().i(), type().i(), numberOfRefs.i()));
      s.append("positions: ");   for (int i : N) s.append(f(" "+formatKey, i));
      s.append("\nslots    : "); for (int i : N) s.append(f(" "+formatKey, slots(new Slot(i)).i()));
      s.append("\nusedSlots: "); for (int i : N) s.append(             usedSlots(new Slot(i)) ? "   X" : "   .");
      s.append("\nusedRefs : "); for (int i : R) s.append(             usedRefs (new slot(i)) ? "   X" : "   .");
      s.append("\nkeys     : "); for (int i : R) s.append(f(" "+formatKey,   key(new slot(i)) != null ? key(new slot(i)).i() : 0));
      return ""+s+"\n";
     }

    String printInOrder()                                                       // Print the values in the used slots in order
     {final StringJoiner s = new StringJoiner(", ");

      for (int i : range(numberOfSlots()))
       {if (usedSlots(new Slot(i))) s.add(""+keys(new Slot(i)).i());
       }
      return ""+s;
     }

//D2 Memory                                                                     // Read and write from an array of bytes

    class SlotsMemoryPositions                                                  // Positions of fields in memory
     {final Int N = numberOfSlots(), R = numberOfRefs;

      final BitSet.Spec us = new BitSet.Spec(new Int(N), true, true);           // Specification of bit set for used slots
      final BitSet.Spec ur = new BitSet.Spec(new Int(R), true, true);           // Specification of bit set for references

      final Int posType      = new Int(0);
      final Int posSlots     = posType      .Add(ib());
      final Int posUsedSlots = posSlots     .Add(ib(N));
      final Int posUsedRefs  = posUsedSlots .Add(us.byteSize().i());
      final Int posKeys      = posUsedRefs  .Add(ur.byteSize().i());
      final Int posName      = posKeys      .Add(ib(R));
      final Int size         = posName      .Add(ib());
     }

    class Memory extends SlotsMemoryPositions                                   // Memory required to hold bytes
     {final ByteBuffer bytes;                                                   // Bytes used by this set of slots

      final BitSet usedSlotsBits = new BitSet(us)                               // Bit storage for used slots
       {void setByte(Int I, byte V) {bytes.put(posUsedSlots.Add(I).i(), V);}    // Save used slot bit
        byte getByte(Int I)  {return bytes.get(posUsedSlots.Add(I).i());}       // Get used slot bit
       };

      final BitSet usedRefsBits  = new BitSet(ur)                               // Bit storage for used refs
       {void setByte(Int I, byte V) {bytes.put(posUsedRefs.Add(I).i(), V);}     // Save used ref bit
        byte getByte(Int I)  {return bytes.get(posUsedRefs.Add(I).i());}        // Get used ref bit
       };

      void copySlots(Memory Memory)                                             // Copy a set of slots from the specified memory into this memory
       {new For(size)
         {boolean body(int i)
           {bytes.put(i, Memory.bytes.get(i));
            return true;
           }
         };
       }

      void invalidate()                                                         // Invalidate the slots in such a way that they are unlikely to work well if subsequently used
       {new For(size)
         {boolean body(int i)
           {bytes.put(i, (byte)-1);
            return true;
           }
         };
       }

      void clear()                                                              // Clear all bytes in memory to zero which has the beneficial effect of setting all slots to unused
       {new For(size)
         {boolean body(int i)
           {bytes.put(i, (byte)0);
            return true;
           }
         };
       }

      Memory()                 {bytes = ByteBuffer.allocate(size.i());}         // Create memory
      Memory(ByteBuffer Bytes) {bytes = Bytes;}                                 // Use a specified memory

      BitSet.Pos us(Int I) {return usedSlotsBits.new Pos(I);}                   // A slot position in the used slots
      BitSet.Pos ur(Int I) {return usedRefsBits .new Pos(I);}                   // A slot position in the references

      boolean usedSlots (Int I) {return usedSlotsBits.getBit(us(I));}           // Value of indexed used slot
      boolean usedRefs  (Int I) {return usedRefsBits .getBit(ur(I));}           // Value of indexed used reference
      Int     slots     (Int I) {return new Int(bytes.getInt(posSlots.Add(ib(I)).i()));} // Value of indexed slot
      Int     keys      (Int I) {return new Int(bytes.getInt(posKeys .Add(ib(I)).i()));} // Value of key via indexed reference
      Int     name      (     ) {return new Int(bytes.getInt(posName.i()));}

      void    usedSlots (Int I, boolean V) {usedSlotsBits.set(        us(I),      V);} // set value of indexed used slot
      void    usedRefs  (Int I, boolean V) {usedRefsBits .set(        ur(I),      V);} // set value of indexed used reference
      void    slots     (Int I, Int     V) {bytes.putInt(posSlots.Add(ib(I)).i(), V.i());} // set value of indexed slot
      void    keys      (Int I, Int     V) {bytes.putInt(posKeys .Add(ib(I)).i(), V.i());} // set value of key via indexed reference
      void    name      (       Int     V) {bytes.putInt(posName.i(),             V.i());} // Save the name of the node in memory to assist debugging

      void type(Int Type) {       bytes.putInt(posType.i(), Type.i());}             // Type of object in which the slots are embedded
      Int  type()         {return new Int(bytes.getInt(posType.i()));}
     }
   }

  Int getMemorySize(Int NumberOfRefs)                                           // Size of memory for a specified number of references
   {return new Slots(NumberOfRefs.i(), false).memory.size.dup();
   }

//D1 Tree memory                                                                // Memory used to hold the root of the tree, its leaves and branches

  class Memory extends TreeMemoryPositions                                      // Memory used to hold the bytes of the
   {final Int l            = new   LeafMemoryPositions().memorySize();          // Memory positions for leaves
    final Int b            = new BranchMemoryPositions().memorySize();          // Memory positions for branches
    final Int sizeOfNode   = l.max(b);                                          // Size of memory for a branch or a leaf or the base description of the tree - which is held in node 0.
    final Int size         = sizeOfNode.Mul(numberOfNodes.Inc());               // Size of memory for tree assuming that each node can contain a branch or a leaf or the base description of the tree - which is held in node zero
    final ByteBuffer bytes = ByteBuffer.allocate(size.i());                     // Memory occupied by tree

    Int  root()             {return new Int(bytes.getInt(posRoot.i()));}        // Get index of node containing root
    void root(Int Value)           {bytes.putInt(posRoot.i(),      Value.i());} // Set index of node containing root

    Int  maxLeafSize()      {return new Int(bytes.getInt(posMaxLeafSize.i()));} // Get maximum leaf size for the tree
    void maxLeafSize(Int Max)      {bytes.putInt(posMaxLeafSize.i(),   Max.i());}       // Set max leaf size for the tree

    Int  maxBranchSize()    {return new Int(bytes.getInt(posMaxBranchSize.i()));}            // Get maximum branch size for the tree
    void maxBranchSize(Int Max)    {bytes.putInt(posMaxBranchSize.i(), Max.i());}       // Set max branch size for the tree

    Int  numberOfNodes()    {return new Int(bytes.getInt(posNumberOfNodes.i()));}            // Get maximum number of nodes in the tree
    void numberOfNodes(Int Number) {bytes.putInt(posNumberOfNodes.i(), Number.i());}    // Set maximum number of nodes in the tree

    public String toString()                                                    // Print memory
     {final StringBuilder s = new StringBuilder();
      s.append(f("Tree memory:\n"));
      s.append(f("Leaf   size: %4d\n", l));
      s.append(f("Branch size: %4d\n", b));
      s.append(f("Node   size: %4d\n", sizeOfNode));
      s.append(f("Root       : %4d\n", root()));
      s.append(f("MaxLeafSize: %4d\n", maxLeafSize()));
      s.append(f("MaxBranchSz: %4d\n", maxBranchSize()));
      s.append(f("NumberNodes: %4d\n", numberOfNodes()));

      final Int N = numberOfNodes.min(20);
      for (int i : range(N))
       {final Int n = sizeOfNode.Mul(i);
        s.append(f("Node: %4d at %4d\n", i, n));
        final boolean    l = new Int(bytes.getInt(n.i())).eq(new Int(NodeType.Leaf.ordinal()));
        final Allocation a = new Allocation(new Int(i));
        final String t = l ? new Leaf(a).toString() : new Branch(a).toString();
        s.append(t);
       }
      return ""+s;
     }
   }

  ByteBuffer node(Allocation Name)                                              // Address the specified node of the tree
   {final Int n = Name.at(), s = sizeOfNode;
    return memory.bytes.slice(n.Mul(s).i(), s.i());
   }

  String getInt(Int Name, Int Field)                                            // Address the specified node of the tree - useful for debugging
   {return "Node: "+Name+" field: "+
      Field+" = "+memory.bytes.getInt(Name.Mul(sizeOfNode).add(Field).i());
   }

//D1 Root                                                                       // The root of the tree is referenced from a known location which allows any node to act as the root if needed - which simplifies the logic for merging and splitting the root.

  Slots root()                                                                  // Slots representing the root of the tree held in memory
   {final Int r = memory.root();                                                // Current node containing root
    return r.eq(0) ? null :                                                     // Node zero contains the tree base so we can conveniently use zero as a null pointer as no leaf or branch will occupy node zero.
           r.lt(0) ? new Leaf  (new Allocation(r.neg())):                       // Leaf as negative
                     new Branch(new Allocation(r));                             // Branch as positive
   }

  void root(Leaf   Root) {memory.root(Root != null ? Root.name().at().neg() : new Int(0));}   // Set the root in memory with a negative address to show that it is a leaf
  void root(Branch Root) {memory.root(Root != null ? Root.name().at()       : new Int(0));}   // Set the root in memory with a positive address to show that it is a branch

//D1 Leaf                                                                       // Use the slots to model a leaf

  class Leaf extends Slots                                                      // Leaf
   {final Memory memory;                                                        // Memory used by the leaf
    final Allocation node;                                                      // The node holding this leaf

    Leaf()                                                                      // Create a leaf
     {super(maxLeafSize.i());                                                   // Slots for leaf
      node   = allocate();                                                      // Allocate the leaf
      memory = new Memory(node);                                                // Memory for leaf
      super.setMemory(memory.bytes);                                            // Share memory with slots
      super.memory.clear();                                                     // Clear the memory associated slots
      super.initialize();                                                       // Create path bit trees for slots
      name(node);                                                               // Save the name of the node in memory to assist debugging
      type(new Int(NodeType.Leaf.ordinal()));                                            // Set this memory as a leaf
     }

    Leaf(Allocation Name)                                                       // Reuse the leaf at the indexed node in memory
     {super(maxLeafSize.i(), node(Name));                                           // Slots for leaf
      node   = Name;                                                            // Node containing leaf
      memory = new Memory(Name);                                                // Memory for leaf
      super.setMemory(memory.bytes);                                            // Share memory with slots
      name(node);                                                               // Name of the leaf
     }

    Branch up()                                                                 // Parent branch
     {final Int u = memory.up();
      final Branch B = u.gt(0) ? new Branch(new Allocation(u)) : null;
      return B;
     }
    void up(Branch Branch) {memory.up(Branch != null ? Branch.name().at() : new Int(0));}// Set parent branch

    Slot upIndex(Branch Branch)                                                 // Index of this leaf in its parent. We have to return an Integer rather than a slot because we do not know which branch the slot is in
     {final Int i = memory.upIndex(); return i.lt(0) ? null : Branch.new Slot(i);
     }
    void upIndex(Slot Slot)                                                     // Set the index of this leaf in its parent
     {memory.upIndex(Slot != null ? Slot.value() : new Int(-1));                         // -1 represents null in the byte buffer for this index
     }

    Data data(Slot I) {return new Data(memory.data(slots(I).value()));}         // Get value of data field at index
    void data(Slot I, Data Value)                                               // Set value of data field at index
     {memory.data(slots(I).value(), Value != null ? Value.value() : new Int(0));
     }

    Data data(slot I) {return new Data(memory.data(I.value()));}                // Get value of data field at the slot
    void data(slot I, Data Value)                                               // Set value of data field at the slot
     {memory.data(I.value(), Value != null ? Value.value() : new Int(0));
     }

    static boolean ref(Slots L)  {return L instanceof Leaf;}                    // Check whether we are referencing a leaf
    Int splitSize()              {return maxLeafSize.Down();}                      // Size of a split leaf

    Leaf duplicate()                                                            // Duplicate a leaf
     {final Leaf d = new Leaf();
      d.copySlots(this);                                                        // Copy slots
      new For(numberOfRefs())                                                   // Each reference
       {boolean body(int i)                                                     // Each reference
         {final slot I = new slot(i);                                           // Copy data associated with leaf keys
          d.data(I, data(I));                                                   // Copy data associated with leaf keys
          return true;
         }
       };
      return d;
     }

    void free()                                                                 // Free the leaf
     {Tree.this.free(name());                                                   // Add to free chain
      invalidate();                                                             // Invalidate slots
      memory.invalidate();                                                      // Invalidate leaf data
     }

    Leaf splitRight()                                                           // Split out the right hand side of a full leaf
     {final Leaf l = duplicate();
      final Leaf r = splitRight(l);
      return r;
     }

    Leaf splitLeft()                                                            // Split a right leaf into a new left leaf
     {final Leaf l = duplicate();
      l.splitRight(this);
      return l;
     }

    Leaf splitRight(Leaf Right)                                                 // Split a left leaf into an existing right leaf
     {final Ref<Leaf> l = new Ref<>();
      new If (full())
       {void Then()
         {l.set(splitRightFull(Right));                                         //
         }
       };
      return l.get();                                                           // Only full leaves can be split
     }

    Leaf splitRightFull(Leaf Right)                                             // Split a left leaf into an existing right leaf
     {final Int s = new Int(0);                                                 // Count slots used
      new For(numberOfSlots())                                                  // Each slot
       {boolean body(int i)
         {final Slot S = new Slot(i);
          new If (usedSlots(S))                                                 // Slot is in use
           {void Then()                                                         // Slot is in use
             {new If (s.lt(splitSize()))                                        // Still in left leaf
               {void Then()
                 {Right.clearSlotAndRef(S); s.inc();
                 }
                void Else()                                                     // Clear slot being used in right leaf
                 {clearSlotAndRef(S);
                 }
               };
             }
           };
          return true;
         }
       };                                                                       // The new right leaf
      redistribute(); Right.redistribute();
      return Right;
     }

    Int splittingKey()                                                          // Splitting key from a leaf
     {if (!full()) stop("Leaf not full");                                       // The leaf must be full if we are going to split it
      final Int k = new Int(0);                                                 // Splitting key
      final Int p = new Int(0);                                                 // Position in leaf
      new For(numberOfSlots())                                                  // Scan for splitting keys
       {boolean body(int i)
         {new If (usedSlots(new Slot(i)))                                       // Used slot
           {void Then()
             {new If (p.Eq(splitSize().Dec()).or(p.Eq(splitSize())))                // Accumulate splitting key as last on left and first on right of split
               {void Then()
                 {k.add(keys(new Slot(i)).value());
                 }
               };
              p.inc();                                                          // Next position
             }
           };
          return true;
         }
       };
      return k.Down();                                                          // Average splitting key
     }

    Branch split()                                                              // Split a leaf into two leaves and a branch
     {final Leaf   l = duplicate();
      final Leaf   r = l.splitRight();
      final Branch b = new Branch();                                            // Branch into which the leaves will be inserted
      b.insert(new Key(splittingKey()), l);                                     // Insert left
      b.top(r);                                                                 // Right goes to to
      free();
      return b;
     }

    slot insert(Key Key, Data Data)                                             // Insert a key data pair into a leaf
     {final slot i = insert(Key);
      new If (i != null) {void Then() {data(i, Data);}};                        // Save data in allocated reference
      return i;
     }

    void compactLeft()                                                          // Compact the leaf to the left
     {final Data[]d = new Data[numberOfRefs().i()];
      final Int p = new Int(0);
      new For(numberOfSlots())                                                  // Copy leaf data
       {boolean body(int i)
         {final Slot I = new Slot(i);
          new If (usedSlots(I))
           {void Then()
             {d[p.i()] = data(slots(I)); p.inc();
             }
           };
          return true;
         }
       };
      super.compactLeft();                                                      // Compact slots

      new For(numberOfRefs())                                                   // Copy compacted leaf data
       {boolean body(int i)
         {data(new slot(i), d[i]);
          return true;
         }
       };
     }

    void compactRight()                                                         // Compact the leaf to the right
     {final Int   N = numberOfSlots(), R = numberOfRefs();
      final Data[]d = new Data[R.i()];
      final Int   p = new Int(R.Dec().i());                                             // Start at the last slot
      new For(N)                                                                // Compact each slot to the right
       {boolean body(int i)
         {final Slot I = new Slot(N.Sub(i).dec());
          new If (usedSlots(I))
           {void Then()
             {d[p.i()] = data(slots(I)); p.dec();
             }
           };
          return true;
         }
       };
      super.compactRight();                                                     // Compact slots
      new For(R) {boolean body(int i) {data(new slot(i), d[i]); return true;}}; // Copy compacted leaf data
     }

    void mergeData(Leaf Left, Leaf Right)                                       // Merge the data from the compacted left and right slots
     {final Leaf l = Left, r = Right;
      new For(maxLeafSize)
       {boolean body(int i)
         {final slot J = new slot(i);
          new If     (l.usedRefs(J))
           {void Then()
             {data(J, l.data(J));
             }
            void Else()
             {new If (r.usedRefs(J)) {void Then() {data(J, r.data(J));}};
             }
           };
          return true;
         }
       };
     }

    void mergeLeaves(Leaf Left, Leaf Right)                                     // Merge the specified leaves into the current leaf
     {Left.compactLeft ();
      Right.compactRight();
      mergeCompacted(Left, Right);
      mergeData     (Left, Right);
      redistribute();
     }

    boolean mergeFromRight(Leaf Right)                                          // Merge the specified slots from the right
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
      return r.b();
     }

    boolean mergeFromLeft(Leaf Left)                                            // Merge the specified slots from the left
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
      return R.b();
     }

//D2 Memory                                                                     // Memory for leaf

    class Memory extends LeafMemoryPositions                                    // Memory required to hold bytes
     {final ByteBuffer bytes;                                                   // Byte buffer holding memory of this leaf

      void copy(Memory Memory)                                                  // Copy a set of slots from the specified memory into this memory
       {new For(size)
         {boolean body(int i)
           {bytes.put(i, Memory.bytes.get(i));
            return true;
           }
         };
       }

      void invalidate()                                                         // Invalidate the leaf in such a way that it is unlikely to work well if subsequently used
       {new For(size)
         {boolean body(int i)
           {bytes.put(i, (byte)-1);
            return true;
           }
         };
       }

      Memory(Allocation Name) {bytes = node(Name);}                             // Position in tree memory

      Int  up()     {return new Int(bytes.getInt(posUp.i()));}                  // Reference to parent branch. The zero node contains the tree base so zero can be used as a representation of null for references to branches and leaves
      void up(Int Index)   {bytes.putInt(posUp.i(), Index.i());}                // Save address of parent branch into memory

      Int  upIndex(){return new Int(bytes.getInt(posUpIndex.i()));}             // Get index of leaf in its parent from memory
      void upIndex(Int Value)                                                   // Save index of this leaf in its parent branch
       {bytes.putInt(posUpIndex.i(), Value != null ? Value.i() : -1);           // -1 used to indicate top
       }

      Int  data(Int Index)                                                      // Get the index of the leaf in its parent branch from memory
       {return new Int(bytes.getInt(posData.Add(ib(Index)).i()));
       }
      void data(Int Index, Int  Value)                                          // Put the index of the leaf in its parent branch into memory
       {bytes.putInt(posData.Add(ib(Index)).i(), Value.i());
       }
     }

//D2 Print                                                                      // Print the leaf

    String printInOrder()                                                       // Print the values in the used slots in order
     {final StringJoiner k = new StringJoiner(", ");
      final StringJoiner d = new StringJoiner(", ");
      for (int i : range(numberOfSlots()))
       {final Slot I = new Slot(i);
        if (usedSlots(I))
         {k.add(""+keys(I).value());
          d.add(""+memory.data(slots(I).value()));
         }
       }
      return "keys: "+k+"\n"+"data: "+d+"\n";
     }

    public String toString()                                                    // Print a leaf
     {final StringJoiner d = new StringJoiner(" ");
      for (int i : range(numberOfRefs()))
       {d.add(f(formatKey, memory.data(new Int(i)).i()));
       }
      final Branch     P = up();                                                // Containing branch
      final Slots.Slot Q = P != null ? upIndex(P) : null;
      final String U = " up: "   +(P != null ? P.name().at()          : "null");
      final String I = " index: "+(P != null && Q != null ? Q.value() : "null");
      return "Leaf     : "+name().at()+U+I+"\n"+
              super.toString() + "data     :  "+d+"\n";
     }
   }

//D1 Branch                                                                     // Use the slots to model a branch

  class Branch extends Slots                                                    // Branch
   {final Allocation node;                                                      // The node holding this leaf
    final Memory   memory;                                                      // Memory used by the slots

    Branch()                                                                    // Create a branch
     {super(maxBranchSize.i());                                                     // Slots for branch
      node   = allocate();                                                      // Name the branch
      memory = new Memory(node);                                                // Memory for branch
      super.setMemory(memory.bytes);                                            // Share memory with slots
      super.memory.clear();                                                     // Clear the memory associated with the slots
      super.initialize();                                                       // Create path bit trees for slots
      name(node);                                                               // Save the name of the node in memory to assist debugging
      type(new Int(NodeType.Branch.ordinal()));                                 // Set the type to branch
     }

    Branch(Allocation Name)                                                     // Reuse the branch at the indexed node in memory
     {super(maxBranchSize.i());                                                 // Slots for branch
      node   = Name;                                                            // Node containing branch
      memory = new Memory(node);                                                // Memory for branch
      super.setMemory(memory.bytes);                                            // Share memory with slots
      name(node);                                                               // Name of the branch
     }

    Branch up()                                                                 // Name of branch above if any
     {final Int i = memory.up();
      return i.gt(0) ? new Branch(new Allocation(i)) : null;
     }

    void up(Branch Branch)                                                      // Set name of branch above to the indicated branch
     {memory.up(Branch != null ? Branch.name().at() : new Int(0));
     }

    Int  upIndex()          {return new Int(memory.upIndex());}                 // Index of this branch in its parent
    void upIndex(Int Value) {memory.upIndex(Value);}                        // Set the index of this branch in its parent

    static boolean ref(Slots B)    {return B instanceof Branch;}                // Check whether we are referencing a branch

    Int refSign(Slots Slots)                                                    // The sign of a reference according to whether it is a reference to a leaf or a branch
     {final Int R  = new Int();
      new If (Slots == null)
       {void Then()
         {R.i(0);
         }
        void Else()
         {final Int i = Slots.name().at();
          R.i(ref(Slots) ? i : i.neg());
         }
       };
      return R;
     }

    void top(Slots Value) {memory.top(refSign(Value));}                         // Set the top most element

    Slots   top()                                                               // Top element of this branch
     {final Int i = memory.top();
      return i.eq(0) ? null :  i.gt(0) ? new Branch(new Allocation(i)):
                                         new Leaf  (new Allocation(i.neg()));
     }

    void free()                                                                 // Free the branch
     {Tree.this.free(name());                                                   // Add to free chain
      invalidate();                                                             // Invalidate slots
      memory.invalidate();                                                      // Invalidate branch data
     }

    Int splitSize()             {return maxBranchSize.Down();}                  // Size of a split branch
    Slots firstChild()          {return data(locateFirstUsedSlot());}           // First child assuming there is one

    Slots data(Slot Index)                                                      // Step down via indexed slot to the branch or leaf below
     {return usedSlots(Index) ? dataDirect(slots(Index).value()) : null;
     }

    Slots dataDirect(Int Index)                                                 // Step down directly through to the branch or leaf below
     {final Int i = memory.data(Index);
      return i.eq(0) ? null : i.lt(0) ? new Leaf(new Allocation(i.neg())):
                                      new Branch(new Allocation(i));
     }

    void data(Int Index, Slots S)                                               // Set child leaf or branch via the indexed slot
     {dataDirect(slots(new Slot(Index)).value(), S);
     }

    void dataDirect(Int Index, Slots S) {memory.data(Index, refSign(S));}       // Set child leaf or branch directly at the indexed location

    Branch duplicate()                                                          // Duplicate a branch
     {final Branch d = new Branch();
      d.copySlots(this);                                                        // Copy slots
      new For(numberOfRefs())                                                   // Copy used data
       {boolean body(int i)
         {d.memory.data(new Int(i), memory.data(new Int(i)));
          return true;
         }
       };
      d.top(top());
      return d;
     }

    Split splitRight() {return splitRight(duplicate());}                        // Split a left branch into a new right branch

    class Split                                                                 // The result of splitting a branch
     {final Key key;                                                            // The splitting key
      final Branch left, right;                                                 // Left and right sides of split branch
      Split(Key Key, Branch Left, Branch Right)
       {key = Key; left = Left; right = Right;
       }
     }

    Split splitRight(Branch Right)                                              // Split a left branch into an existing right branch
     {final Ref<Split> l = new Ref<>();
      new If (full())
       {void Then()
         {l.set(splitRightFull(Right));                                         //
         }
       };
      return l.get();                                                           // Only full leaves can be split
     }

    Split splitRightFull(Branch Right)                                          // Split a left branch into an existing right branch
     {final Int Count = splitSize();
      final Int s     = new Int(0);                                             // Count slots used
      Ref<Key>  sk    = new Ref<>();                                            // Splitting key

      new For (numberOfSlots())                                                 // Each slot
       {boolean body(int i)
         {final Slot I = new Slot(i);                                           // Slot is in use
          new If (usedSlots(I))                                                 // Slot is in use
           {void Then()
             {new If (s.lt(Count))                                              // Still in left branch
               {void Then()                                                     // Still in left branch
                 {Right.clearSlotAndRef(I);                                     // Free the entry from the right branch as it is being used in the left branch
                  s.inc();                                                      // Number of entries active in left branch
                 }
                void Else()                                                     // Splitting key
                 {new If (s.eq(Count))                                          // Splitting key
                   {void Then()
                     {sk.set(keys(I));
                      top(data(I));
                            clearSlotAndRef(I);
                      Right.clearSlotAndRef(I);
                      s.inc();                                                  // Number of entries active in left branch
                     }
                    void Else()
                     {clearSlotAndRef(I);                                       // Clear slot being used in right branch
                     }
                   };
                 }
               };
             }
           };
          return true;
         }
       };                                                                       // The new right branch
      redistribute(); Right.redistribute();
      return new Split(sk.get(), this, Right);
     }

    Split splitLeft()                                                           // Split a right branch into a new left branch
     {final Branch r = duplicate();
      final Split  s = r.splitRight(this);
      return s;
     }

    Int splittingKey()                                                          // Splitting key from a branch
     {if (!full()) stop("Branch not full");                                     // The branch must be full if we are going to split it
      final Int k = new Int(0);                                                 // Splitting key
      final Int p = new Int(0);                                                 // Find the splitting key
      new For(numberOfSlots())                                                  // Scan for splitting keys
       {boolean body(int i)
         {final Slot I = new Slot(i);
          new If (usedSlots(I))
           {void Then()
             {new If (p.eq(splitSize()))
               {void Then()
                 {k.add(keys(I).value());                                       // Splitting key as last on left and first on right of split
                 }
               };
              p.inc();
             }
           };
          return true;
         }
       };
      return k;                                                                 // Splitting key
     }

    Branch split()                                                              // Split a branch
     {final Int         sk = splittingKey();
      final Branch       l = duplicate();
      final Branch.Split s = l.splitRight();
      final Branch       b = new Branch();
      b.insert(new Key(sk), s.left);
      b.top(s.right);
      return b;
     }

    slot insert(Key Key, Slots Data)                                            // Insert a key data pair into a branch
     {final slot i = insert(Key);
      new If (i != null) {void Then() {dataDirect(i.value(), Data);}};
      return i;
     }

    void compactLeft()                                                          // Compact the branch to the left
     {final Int R = numberOfRefs();
      final Slots[]d = new Slots[R.i()];
      final Int p = new Int(0);
      new For(numberOfSlots())
       {boolean body(int i)
         {final Slot I = new Slot(i);
          new If (usedSlots(I)) {void Then() {d[p.i()] = data(I); p.inc();}};
          return true;
         }
       };
      super.compactLeft();
      new For(R) {boolean body(int i) {dataDirect(new Int(i), d[i]); return true;}};
     }

    void compactRight()                                                         // Compact the branch to the right
     {final Int    N = numberOfSlots(), R = numberOfRefs();
      final Slots[]d = new Slots[R.i()];
      final Int    p = R.Dec();
      new For(N)
       {boolean body(int i)
         {final Slot I = new Slot(N.Sub(i).dec());
          new If (usedSlots(I)) {void Then() {d[p.i()] = data(I); p.dec();}};
          return true;
         }
       };
      super.compactRight();
      new For(R) {boolean body(int i) {dataDirect(new Int(i), d[i]); return true;}};
     }

    void mergeData(Key Key, Branch Left, Branch Right)                          // Merge the data from the compacted left and right slots
     {final Branch l = Left, r = Right;
      new For(maxBranchSize)                                                    // Each slot
       {boolean body(int i)
         {final slot J = new slot(i);
          final Int  I = new Int(i);
          new If      (l.usedRefs(J))
           {void Then()
             {memory.data(I, l.memory.data(I));                                 // Merge from left first
             }
            void Else()
             {new If (r.usedRefs(J))
               {void Then()
                 {memory.data(I, r.memory.data(I));                             // Merge from right last
                 }
               };
             }
           };
          return true;
         }
       };
      insert(Key, l.top()); top(r.top());                                       // Insert left top
     }

    void mergeBranches(Key Key, Branch Left, Branch Right)                      // Merge into the current branch from the specified left and right branches
     {Left .compactLeft();
      Right.compactRight();
      mergeCompacted(Left, Right);
      mergeData(Key, Left, Right);
      redistribute();
     }

    boolean mergeFromRight(Key Key, Branch Right)                               // Merge the specified slots from the right
     {final Bool R = new Bool();
      new If (countUsed().Add(Right.countUsed()).ge(maxBranchSize))
       {void Then()
         {R.clear();
         }
        void Else()
         {mergeBranches(Key, duplicate(), Right.duplicate());
          R.set();
         }
       };
      return R.b();
     }

    boolean mergeFromLeft(Key Key, Branch Left)                                 // Merge the specified slots from the right
     {final Bool R = new Bool();
      new If (Left.countUsed().Add(countUsed()).ge(maxBranchSize))
       {void Then()
         {R.clear();
         }
        void Else()
         {final Branch l = Left.duplicate(), r = duplicate();
          mergeBranches(Key, l, r);
          l.free(); r.free();
          R.set();
         }
       };
      return R.b();
     }

    boolean mergeLeftSibling(Slot Right)                                        // Merge the indicated child with its left sibling if possible.  If the index is null merge into top
     {final Bool R = new Bool();                                                // Result
      final Slots.Slot left = Right != null ? Right.stepLeft() :                // Left sibling from right child
                                              locateLastUsedSlot();             // Sibling prior to top
      new If (left == null)
       {void Then() {R.clear();}                                                // No left sibling
        void Else()
         {final Slots L = data(left);                                           // Left sibling as slots
          new If (Leaf.ref(L))                                                  // Merging leaves
           {void Then()
             {final Leaf l = (Leaf)L;
              final Leaf r = (Leaf)(Right != null ? data(Right) : top());       // Right leaf sibling
              new If (r.mergeFromLeft(l))                                       // Merge left sibling into right
               {void Then()
                 {clearSlotAndRef(left);                                        // Remove left sibling from parent now that it has been merged with its right sibling
                  R.set();
                 }
                void Else() {R.clear();};
               };
             }
            void Else()                                                         // Children are branches
             {final Branch l = (Branch)L;
              final Branch r = (Branch)(Right != null ? data(Right) : top());   // Right leaf sibling
              new If (r.mergeFromLeft(keys(left), l))                           // Merge left sibling into right
               {void Then()
                 {clearSlotAndRef(left);                                        // Remove left sibling from parent now that it has been merged with its right sibling
                  l.free();
                  R.set();
                 }
                void Else() {R.clear();};
               };
             }
           };
         }
       };
      return R.b();
     }

    boolean mergeRightSibling(Slot Left)                                        // Merge the indicated child with its right sibling if possible.  If the index is null merge into top
     {return Left == null ? false : mergeLeftSibling(Left.stepRight());         // Nothing to right of top
     }

    Slots child(Slot Index)                                                     // The indexed child. The index must be valid or null - if null, top is returned
     {if (Index != null && !usedSlots(Index))                                   // The slot must be valid
       {stop("Indexing unused slot:", Index.value());
       }
      return Index == null ? top() : data(Index);                               // A null index produces top
     }

    Tree tree()             {return Tree.this;}                                 // Containing tree
    Slots stepDown(Key Key) {return child(locateFirstGe(Key));}                 // Step down from this branch

    Int count(Slots s)                                                          // Count the number of entries under this branch
     {return Leaf.ref(s) ? s.countUsed() : ((Branch)s).count();
     }

    Int count()                                                                 // Count the number of entries under this branch
     {final Int n = new Int(0);
      new For (numberOfSlots())                                                 // Each slot
       {boolean body(int i)
         {final Slot I = new Slot(i);
          new If (usedSlots(I)) {void Then() {n.add(count(data(I)));}};
          return true;
         }
       };
      return n.Add(count(top()));                                               // Count entries below top
     }

//D2 Memory                                                                     // Memory for a branch

    class Memory extends BranchMemoryPositions                                  // Memory required to hold bytes
     {final ByteBuffer bytes;

      void copy(Memory Memory)                                                  // Copy a set of slots from the specified memory into this memory
       {new For(size)
         {boolean body(int i)
           {bytes.put(i,Memory.bytes.get(i));
            return true;
           }
         };
       }

      void invalidate()                                                         // Invalidate the branch in such a way that it is unlikely to work well if subsequently used
       {new For(size)
         {boolean body(int i)
           {bytes.put(i, (byte)-1);
            return true;
           }
         };
       }

      Memory(Allocation Name) {bytes = node(Name);}                             // Position in tree memory

      Int  up()   {return new Int(bytes.getInt(posUp.i()));}                    // Parent branch
      void up(Int Value) {bytes.putInt(posUp.i(), Value.i());}

      Int upIndex()                                                             // Index of this branch in its parent
       {final Int i = new Int(bytes.getInt(posUpIndex.i())); return i.lt(0) ? null : i;
       }

      void    upIndex(Int Value)                                                // Set the index of this branch in its parent
       {bytes.putInt(posUpIndex.i(), Value != null ? Value.i() : -1);                   // Have to use -1 to represent null as 0 is a valid position
       }

      Int  top ()  {return new Int(bytes.getInt(posTop.i()));}
      void top (Int Top)  {bytes.putInt(posTop.i(), Top.i());}

      Int  data(Int Index)
       {return new Int(bytes.getInt(posData.Add(ib(Index)).i()));
       }
      void data(Int Index, Int  Value)
       {bytes.putInt(posData.Add(ib(Index)).i());
       }
     }

//D2 Print                                                                      // Print a branch

    String printInOrder()                                                       // Print the values in the used slots in order
     {final StringJoiner k = new StringJoiner(", ");
      final StringJoiner d = new StringJoiner(", ");

      for (int i : range(numberOfSlots()))
       {if (usedSlots(new Slot(i)))
         {k.add(""+keys(new Slot(i)).value());
          d.add(""+memory.data(slots(new Slot(i)).value()));
         }
       }
      return "keys: "+k+"\n"+"data: "+d+"\ntop : "+top().name().at()+"\n";
     }

    public String toString()                                                    // Print a branch
     {final StringJoiner d = new StringJoiner(" ");

      for (int i : range(numberOfRefs()))
       {final Int I = new Int(i);
        if (memory.data(I).eq(0)) d.add("  .");
        else d.add(f(formatKey, memory.data(I).i()));
       }
      final StringBuilder s = new StringBuilder();

      final Int    ui = upIndex();
      final String us = ui != null ? ""+ui.i() : "null";
      final int n = name().at().i();
      final int u = memory.up().i();
      final int i = memory.upIndex() != null ? memory.upIndex().i() : -1;           // -1 represents null
      s.append(f("Branch   : %4d   up: %4d  index: %4d\n", n, u, i));
      s.append(super.toString());
      s.append("data     :  "+d+"\n");
      s.append("top      :  "+f(formatKey, memory.top().i())+"\n");
      return ""+s;
     }
   }

//D1 Low Level                                                                  // Low level operations

  void mergeAlongPath(Key Key)                                                  // Merge along the path from the specified key to the root
   {final Find f = find(Key);                                                   // Locate the leaf that should contain the key
    //if (f == null) return;                                                      // Empty tree

    if (f.leaf.up() != null)                                                    // Process path from leaf to root
     {final Ref<Branch> B = new Ref<>(f.leaf.up());                             // First branch
      new For(numberOfNodes)                                                    // Go up the tree merging as we go: only one merge is needed at each level
       {boolean body(int i)
         {final Branch b = B.get();
          final Bool m = new Bool().clear();                                    // Whether we have merged anything yet

          new If (!m.b())
           {void Then()
             {final Slots.Slot l = b.locateFirstGe(Key);                        // Position of key
              m.set(l != null && b.mergeRightSibling(l));                       // Merge right sibling of keyed child
             }
           };

          new If (!m.b())
           {void Then()
             {final Slots.Slot L = b.locateFirstGe(Key);                        // Position of key
              m.set(L != null && b.mergeLeftSibling(L));                        // Merge left sibling of keyed child
             }
           };

          new If (!m.b())
           {void Then()
             {final Slots.Slot k = b.locateFirstGe(Key);                        // Look further left
              m.set(k != null && b.mergeLeftSibling(k.stepLeft()));             // Merge further left sibling
              new If (!m.b())                                                   // Top
               {void Then()
                 {final Slots.Slot S = b.locateLastUsedSlot();
                  m.set(S != null && b.mergeLeftSibling(S));                    // Merge further left of top
                 }
               };
             }
           };

          new If (!m.b())
           {void Then()
             {final Slots.Slot r = b.locateFirstGe(Key);                        // Look further right
              m.set(r != null && b.mergeRightSibling(r.stepRight()));           // Merge further right sibling
             }
           };

          new If (!m.b()) {void Then() {b.mergeLeftSibling(null);}};            // Migrate into top
          B.set(b.up());                                                        // Go up the tree merging as we go: only one merge is needed at each level
          return B.valid();
         }
       };
     }
    mergeRoot(Key);                                                             // Merge the root if possible
   }

  void mergeRoot(Key Key)                                                       // Collapse the root if possible
   {new If (root() != null)                                                     // Tree has content
     {void Then()
       {mergeRootNotEmpty(Key);                                                 // Merge non empty root if possible
       }
     };
    return;
   }

  void mergeRootNotEmpty(Key Key)                                               // Collapse the root if possible
   {new If (Leaf.ref(root()))                                                   // Leaf root
     {void Then()
       {final Leaf l = (Leaf)root();
        new If (l.empty()) {void Then() {l.free(); root((Leaf)null);}};         // Free leaf if it is empty
       }
      void Else()
       {final Branch b = (Branch)root();                                        // Branch root
        new If (b.countUsed().eq(0))                                            // Root body is empty so collapse to top
         {void Then()
           {final Slots t = b.top();
            b.free();
            new If (Leaf.ref(t))
             {void Then()
               {root((Leaf)t);
               }
              void Else()
               {root((Branch)t);
               }
             };
           }
         };

        new If (b.countUsed().eq(1))                                            // Root body is right size to collapse
         {void Then()
           {new If (Leaf.ref(b.top()))                                          // Leaves for children
             {void Then()
               {final Leaf    l = (Leaf)b.firstChild();
                final Leaf    r = (Leaf)b.top();
                final boolean m = l.mergeFromRight(r);
                new If (m) {void Then() {b.free(); r.free(); root(l);}};        // Update root if the leaves were successfully merged
               }
              void Else()
               {final Branch  l = (Branch)b.firstChild();                       // Root has branches for children
                final Branch  r = (Branch)b.top();
                final boolean m = r.mergeFromLeft(b.firstKey(), l);
                new If (m) {void Then() {b.free(); l.free(); root(r);}};        // Update root if the leaves were successfully merged
               }
             };
           }
         };
       }
     };
   }

//D1 High Level                                                                 // High level operations: insert, find, delete

  class Find                                                                    // Find results
   {final Leaf leaf;                                                            // Leaf that should contain the key
    final Key key;                                                              // Search key
    final Slots.Locate locate;                                                  // Location details for key

    Find(Key Key, Leaf Leaf)
     {key    = Key;
      leaf   = Leaf;
      locate = Leaf.new Locate(Key);
     }

    public String toString()
     {final StringBuilder s = new StringBuilder();
      s.append("Find Key : "+key.value()+"\n");
      if (leaf    != null) s.append(""+leaf);
      if (locate  != null) s.append("Locate      : "+locate   +"\n");
      final StringJoiner j = new StringJoiner(", ");
      for(Branch p = leaf.up(); p != null; p = p.up()) j.add(""+p.name().at());
      if (leaf.up() != null) s.append("Path        : "+j+"\n");
      return ""+s;
     }
   }

  Find find(Key Key)
   {final Slots r = root();                                                     // Root of tree
    final Ref<Find> f = new Ref<>();                                            // Find details result
    new If (r != null)                                                          // Non empty tree
     {void Then()
       {new If (Leaf.ref(r))                                                    // Leaf root
         {void Then()
           {final Leaf L = (Leaf)r;
            L.up(null);
            L.upIndex((Slots.Slot)null);                                        // Trace path taken to this leaf
            f.set(new Find(Key, L));
           }
          void Else()
           {final Branch R = (Branch)r;                                         // Start search from root
            R.up(null); R.upIndex(null);                                        // Show that there is nothing above the root
            f.set(find(Key, R));                                                // Start search from root
           }
         };
       }
     };
    return f.get();
   }

  Find find(Key Key, Branch Start)
   {final Ref<Branch> p = new Ref<>(Start);                                     // Start at root
    final Ref<Find>   f = new Ref<>();                                          // Find the Key

    new For(MaximumNumberOfLevels)                                              // Step down from branch to branch splitting as we go
     {boolean body(int i)
       {final Slots.Slot Q = p.get().locateFirstGe(Key);
        final Slots      q = p.get().child(Q);
        new If (Leaf.ref(q))                                                    // Step down to a leaf
         {void Then()
           {final Leaf l = (Leaf)q;
            l.up(p.get()); l.upIndex(Q);                                        // Parent of leaf along find path
            f.set(new Find(Key, l));
           }
          void Else()
           {final Branch b = (Branch)q;
            b.up(p.get()); b.upIndex(Q != null ? Q.value() : null);             // Record parent branch
            p.set(b);                                                           // Step down into non full branch
           }
         };
        return !f.valid();
       }
     };
    if (!f.valid())
     {stop("Find fell off the end of tree after this many searches:", mnl());
     }
    return f.get();
   }

  void insert(Key Key, Data Data)                                               // Insert a key, data pair or update key data pair in the tree
   {final Bool d = new Bool().clear();                                          // Try various insertion methods until one succeeds
    if (root() == null)                                                         // Empty tree
     {final Leaf l = new Leaf(); root(l);                                       // Root is a leaf
      l.insert(Key, Data);                                                      // Insert into leaf root
      d.set();
     }
    else                                                                        // Localize optimized insert for non full leaf or full leaf under non full parent
     {final Find F = find(Key);                                                 // See if key is already present
      if (F.locate.exact())                                                     // Key already present so update data associated with the key
       {final Leaf l = F.leaf;                                                  // Child leaf
        l.data(F.locate.at, Data);                                              // Update data
        d.set();
       }
      if (!d.b() && !F.leaf.full())                                             // Leaf not full so insert directly
       {final Leaf l = F.leaf;                                                  // Child leaf
        l.insert(Key, Data);                                                    // Insert key
        d.set();
       }
      if (!d.b() && F.leaf.up() != null && !F.leaf.up().full())                 // Leaf is full, parent branch is not full so we can split leaf
       {final Branch b = F.leaf.up();                                           // Parent branch
        final Leaf   r = F.leaf;
        final Int   sk = r.splittingKey();
        final Leaf   l = r.splitLeft();
        b.insert(Key(sk), l);                                                   // Insert new left leaf into leaf
        if (Key.value().le(sk)) l.insert(Key, Data); else r.insert(Key, Data);   // Insert new key, data pair into left leaf or right leaf depending on key
        final Slots.Slot K = b.locateFirstGe(Key);                              // Position of leaf in parent
        if (!d.b() && b.mergeLeftSibling (K)) d.set();                          // Merge inserted leaf into prior leaf if possible
        if (!d.b() && b.mergeRightSibling(K)) d.set();                          // Merge inserted leaf into next leaf if possible
        if (!d.b() && K != null)                                                // Some where in the body of the parent branch
         {final Slots.Slot L = K.stepLeft(), R = K.stepRight();                 // Further left and right if possible
          if (!d.b() && L != null && b.mergeLeftSibling (L)) d.set();
          if (!d.b() && R != null && b.mergeLeftSibling (R)) d.set();
          if (!d.b() && R != null && b.mergeRightSibling(R)) d.set();
         }
        if (!d.b() && K == null)                                                // Some where in the body of the parent branch
         {final Slots.Slot L = b.locateLastUsedSlot();
          if (!d.b() && L != null && b.mergeLeftSibling(L))  d.set();
         }
        if (!d.b()) {b.mergeLeftSibling(null); d.set();}                        // Merge towards top
       }
     }

    new If (!d.b())
     {void Then()
       {new If (Leaf.ref(root()))                                               // Leaf root
         {void Then()
           {final Leaf l = (Leaf)root();
            new If (!l.full())                                                  // Still space in leaf root
             {void Then()
               {l.insert(Key, Data);                                            // Insert into leaf root
                return;
               }
              void Else()
               {final Branch b = l.split();                                     // Split full leaf root
                root(b);
                insertTree(Key, Data);                                          // Insert a key, data pair or update key data pair in the tree
               }
             };
           }
          void Else() {insertTree(Key, Data);}                                  // Insert a key, data pair or update key data pair in the tree
         };
       }
     };
   }

  void insertTree(Key Key, Data Data)                                           // Insert a key, data pair or update key data pair in the tree
   {final Ref<Branch> p = new Ref<>((Branch)root());                            // Start at root
    new If (p.get().full())                                                     // Split full root branch
     {void Then()
       {final Branch P = p.get();
        p.set(p.get().split());
        root(p.get());
        P.free();
       }
     };

    final Ref<Branch> q = new Ref<>(find(Key).leaf.up());                       // First branch above leaf
    final Ref<Branch> b = new Ref<>(q.get().up());                              // Parent of first branch above leaf
    final Bool        D = new Bool().clear();                                   // First unfull branch above leaf has been located when set
    new If (b.valid())
     {void Then()
       {new For(numberOfNodes)                                                  // Look for first unfull branch along path up from leaf
         {boolean body(int i)                                                   // Found the first unfull branch
           {new If (!b.get().full())
             {void Then()
               {p.set(b.get()); D.set();
               }
              void Else()
               {q.set(b.get());
                b.set(q.get().up());
               }
             };
            return !D.b() && b.valid();
           }
         };
       }
     };

    final Bool d = new Bool().clear();                                          // Set when done
    new For(MaximumNumberOfLevels)                                              // Step down through the tree from branch to branch splitting as we go until we reach a leaf
     {boolean body(int i)
       {final Slots q = p.get().stepDown(Key);                                  // Step down
        new If (Leaf.ref(q))                                                    // Step down to a leaf
         {void Then()
           {final Leaf r = (Leaf)q;                                             // We have reached a leaf

            new If (r.full())                                                   // Split the leaf if it is full
             {void Then()
              {final Int  sk = r.splittingKey();                                // Splitting key
                final Leaf  l = r.splitLeft();                                  // Right leaf split out of the leaf
                p.get().insert(Key(sk), l);                                     // The parent is known not to be full so the insert will work.  We are inserting left so this works even if we are splitting top
                final Leaf  L = Key.value().le(sk) ? l : r;                      // Choose left or right leaf depending on key
                L.insert(Key, Data);                                            // Insert into left or right leaf which will now have space
               }
              void Else() {r.insert(Key, Data);}                                // Leaf has sufficient space
             };
            mergeAlongPath(Key);                                                // Merge along the path taken by the key to compress the tree
            d.set();
           }
          void Else()                                                           // Step down to a branch
           {final Branch r = (Branch)q;
            new If (r.full())                                                   // Split the leaf if it is full
             {void Then()
               {final Int         sk = r.splittingKey();                        // Splitting key
                final Branch.Split s = r.splitLeft();                           // Branch split out on right from
                p.get().insert(Key(sk), s.left);                                // The parent is known not to be full so the insert will work.  We are inserting left so this works even if we are splitting top
                p.set(Key.value().le(sk) ? s.left : s.right);                    // Traverse left or right
               }
              void Else() {p.set(r);}                                           // Step down into non full branch
             };
           }
         };
        return !d.b();                                                          // Continue until done
       }
     };
    if (!d.b())                                                             // Unable to insert
     {stop("Insert fell off the end of tree after this many searches:", mnl());
     }
   }

  void delete(Key Key)                                                          // Delete a key from the tree
   {new If (root() != null)                                                     // The tree is not empty so there might be something to delete
     {void Then()
       {final Find f = find(Key);                                               // Locate the key in the tree
        new If (f.locate.exact())                                               // Key found so delete it
         {void Then()
           {f.leaf.clearSlotAndRef(f.locate.at);                                // Delete key and data from leaf
            mergeAlongPath(Key);
           }
         };
       }
     };
   }

  Int count()                                                                   // Count the number of entries in the tree
   {final Slots r = root();
    return r == null ? new Int(0) : Leaf.ref(r) ? r.countUsed() : ((Branch)r).count();
   }

//D1 Navigation                                                                 // First, Last key, or find the next or prev key from a given key

  Find first()                                                                  // Find the position of the first key in the key
   {final Ref<Find> f = new Ref<>();
    if (root() != null)                                                         // Non empty tree
     {new If (Leaf.ref(root()))                                                 // The tree is one leaf
       {void Then()
         {final Leaf       l = (Leaf)root();
          final Slots.Slot i = l.locateFirstUsedSlot();
          l.up(null); l.upIndex(i);
          f.set(new Find(l.keys(i), l));
         }
        void Else()                                                             // The tree has at least one branch
         {f.set(goFirst((Branch)root()));                                       // Start at root and go all the way first
         }
       };
     }
    return f.get();
   }

  Find goFirst(Branch Start)                                                    // Go all the way first
   {Ref<Branch> p = new Ref<>(Start);                                           // Start
    Ref<Find>   f = new Ref<>();
    new For(MaximumNumberOfLevels)                                              // Step down from branch to branch
     {boolean body(Int I)
       {final Slots.Slot P = p.get().locateFirstUsedSlot();
        final Slots      q = p.get().child(P);
        final Bool       c = new Bool();                                        // Contune down through tree if set
        new If (Leaf.ref(q))                                                    // Step down to a leaf
         {void Then()
           {final Leaf l = (Leaf)q;
            l.up(p.get()); l.upIndex(P);
            final Int i = l.locateFirstUsedSlot().value();
            f.set(new Find(l.keys(l.new Slot(i)), l));                          // Reached a leaf
            c.clear();
           }
          void Else()
           {final Branch b = (Branch)q;
            b.up(p.get()); b.upIndex(P.value());                                // Step down into non full branch
            p.set(b);
            c.set();
           }
         };
        return c.b();                                                           // Continue unless we have encountered a leaf
       }
     };
    if (!f.valid())
     {stop("First fell off the end of tree after this many searches:", mnl());
     }
    return f.get();
   }

  Find last()                                                                   // Find the position of the last key in the tree
   {final Ref<Find> f = new Ref<>();
    if (root() != null)                                                         // Non empty tree
     {new If (Leaf.ref(root()))
       {void Then()
         {final Leaf l = (Leaf)root();
          final Int  i = l.locateLastUsedSlot().value();
          l.up(null); l.upIndex((Slots.Slot)null);
          f.set(new Find(l.keys(l. new Slot(i)), l));
         }
        void Else()
         {f.set(goLast((Branch)root()));                                        // Start at root and go all the way last
         }
       };
     }
    return f.get();
   }

  Find goLast(Branch Start)                                                     // Go all the way last from the specified position
   {final Ref<Branch> p = new Ref<>(Start);                                     // Start
    final Ref<Find>   f = new Ref<>();

    new For(MaximumNumberOfLevels)                                              // Step down from branch to branch splitting as we go
     {boolean body(int i)
       {final Slots q = p.get().top();
        new If (Leaf.ref(q))                                                    // Step down to a leaf
         {void Then()
           {final Leaf l = (Leaf)q;
            l.up(p.get()); l.upIndex((Slots.Slot)null);
            f.set(new Find(l.keys(l.locateLastUsedSlot()), l));
           }
          void Else()
           {final Branch b = (Branch)q;
            b.up(p.get());                                                      // Reference parent
            p.set(b);                                                           // Step down into non full branch
           }
         };
        return !f.valid();
       };
     };
    if (!f.valid())                                                             // Unable to find tehlat element
     {stop("Last fell off the end of tree after this many searches:", mnl());
     }
    return f.get();
   }

  Find next(Find Found)                                                         // Find the next key beyond the one previously found assuming that the structure of the tree has not changed
   {final Ref<Find> f = new Ref<>();                                            // Next key expressed as a find specification
    final Leaf l = Found.leaf;                                                  // Leaf we are currently traversing
    new If (root() != null && l.up() != null)                                   // Tree has branches
     {void Then()
       {final Slots.Slot r = Found.locate.at.stepRight();                       // Next slot to the right in the leaf
        new If (r != null)                                                      // There is a next slot to the right so go to it
          {void Then()
           {f.set(new Find(l.keys(r), l));                                      // There is a next slot to the right in the leaf so return it
           }
          void Else()                                                           // We are at the end of the current branch
           {final Branch U = l.up();                                            // Parent branch of the leaf

            new If (U.top().name().at()  != l.name().at())                      // In the body of the parent branch of the leaf but not at the top of the parent
             {void Then()
               {final Slots.Slot u = l.upIndex(U);                              // Next sibling slot right
                final Slots.Slot R = u != null ? u.stepRight() : null;          // Next sibling slot right
                final Leaf       L = (Leaf)(R != null ? U.data(R) : U.top());   // Next sibling leaf
                L.up(U); L.upIndex(R);
                f.set(new Find(L.firstKey(), L));
               }
              void Else()
               {final Ref<Branch> q = new Ref<>(l.up());                        // First branch above the leaf
                final Ref<Branch> p = new Ref<>(q.get().up());                  // Last point at which we went left

                new If (p.valid())                                              // Branch above the leaf exists
                 {void Then()
                   {new For(numberOfNodes)                                      // Step up to turning point
                     {boolean body(int i)
                       {final Branch P = p.get(), Q = q.get();
                        new If (P.top().name().at() != Q.name().at())           // In the body of the parent branch of the leaf
                         {void Then()
                           {final Int        I = Q.upIndex();                   // Not null because we are not at the root
                            final Slots.Slot R = P.new Slot(I).stepRight();     // Next sibling to the right
                            final boolean    r = R != null;                     // Next sibling to the right exists
                            final Branch b = (Branch)(r ? P.data(R) : P.top()); // Must be a branch as we are going up through the tree
                            b.up(p.get());  b.upIndex(r ? R.value() : null);
                            f.set(goFirst(b));
                           }
                          void Else()                                           // Go up one more level
                           {q.set(p);
                            p.set(q.get().up());
                           }
                         };
                        return !f.valid() && p.valid();                         // Continue until we find the first leaf
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
    return f.get();
   }

  Find prev(Find Found)                                                         // Find the previous key before the one previously found assuming that the structure of the tree has not changed
   {final Ref<Find> f = new Ref<>();                                            // Details of located previous key expressed as a find specification
    final Leaf l = Found.leaf;                                                  // The leaf we are currently traversing
    new If (root() != null && l.up() != null)                                   // Tree is not empty and not a leaf that we are at the end of
     {void Then()
       {final Slots.Slot s = Found.locate.at.stepLeft();                        // Previous slot in leaf
        new If (s != null)
         {void Then()
          {f.set(new Find(l.keys(s), l));
           }
          void Else()
           {final Branch P = l.up();                                            // Parent
            new If (l.upIndex(P) == null)                                       // Last leaf of parent
             {void Then()
               {final Slots.Slot I = P.locateLastUsedSlot();                    // Element prior to top
                final Leaf       L = (Leaf)P.data(I);
                L.upIndex(I);
                f.set(new Find(L.lastKey(), L));
               }
              void Else()
               {new If (l.upIndex(P).value() != l.locateFirstUsedSlot().value())// Not the first leaf of the parent branch
                 {void Then()
                   {final Slots.Slot U = l.upIndex(P);
                    final Slots.Slot u = U.stepLeft();
                    final Leaf  L = (Leaf)(u != null ? P.data(u) : P.top());
                    L.upIndex(u);
                    f.set(new Find(L.lastKey(), L));
                   }
                  void Else()
                   {final Ref<Branch> q = new Ref<>(l.up());                    // First branch above the leaf
                    final Ref<Branch> p = new Ref<>(q.get().up());              // Locate last point at which we went left

                    new If (p.valid())
                     {void Then()
                       {new For(numberOfNodes)                                  // Go up to the last point where we went left
                         {boolean body(int i)
                           {new If (q.get().upIndex() == null)                  // In the body of the parent branch of the leaf
                             {void Then()
                               {final Branch      P = p.get();
                                 final Slots.Slot I = P.locateLastUsedSlot();
                                final Branch      b = (Branch)P.data(I);
                                b.up(p.get());    b.upIndex(I.value());
                                f.set(goLast(b));
                               }
                              void Else()                                       // Go up to next branch
                               {q.set(p);
                                p.set(q.get().up());
                               }
                             };
                            return !f.valid() && p.valid();                     // Continue until we find the first leaf
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
    return f.get();
   }

//D1 Print                                                                      // Print the tree horizontally

  final int linesToPrintABranch = 4;                                            // The number of lines required to print a branch
  final int maxPrintLevels      = 3;                                            // The maximum number of levels to print - this avoids endless print loops when something goes wrong

  void printLeaf                                                                // Print leaf horizontally
   (Leaf Leaf, Stack<StringBuilder>P, int level, boolean Details,
    Branch Parent, Integer Index)
   {final Leaf L = Leaf;
    padStrings(P, level);

    final StringJoiner s = new StringJoiner(",");
    for (int i : range(L.numberOfSlots()))
     {final Slots.Slot l = L.new Slot(i);
      if (L.usedSlots(l)) s.add(""+L.keys(l).value());
     }
    final int N = level * linesToPrintABranch;                                  // Start line at which to print branch
    P.elementAt(N+0).append(s);
    final String U = Parent != null ? "" + Parent.name().at() : "*";
    final String I = Index  != null ? "" + Index              : "*";
    if (Details)
     {P.elementAt(N+1).append("("+L.name().at()+", "+U+", "+I+")");
     }
    padStrings(P, level);
   }

  void printBranch                                                              // Print branch horizontally
   (Branch Branch, Stack<StringBuilder>P, int level, boolean Details,
    Branch Parent, Integer Index)                                               // Details of parent which might differ from what is actually stored in the tree
   {final Branch B = Branch;
    final int L = level * linesToPrintABranch;                                  // Size of branch

    if (level > maxPrintLevels) return;
    padStrings(P, level);

    if (B.countUsed().gt(0))                                                      // Branch has key, next pairs
     {for (int i : range(B.numberOfSlots()))
       {if (B.usedSlots(B.new Slot(i)))
         {final Slots s = B.data(B.new Slot(i));
          if (s == null) continue;
          final boolean l = Leaf.ref(s), b = Tree.Branch.ref(s);

          if      (l) printLeaf  ((Leaf)  s, P, level+1, Details, B, i);
          else if (b) printBranch((Branch)s, P, level+1, Details, B, i);

            P.elementAt(L+0).append(" "+B.keys(B.new Slot(i)).value());         // Key
          if (Details)
           {P.elementAt(L+1).append("["+B.name().at()+"."+i+"]");               // Branch, key, next pair
            final String U = Parent != null ? ""+Parent.name().at() : "*";      // Parent up from descent
            final String I = Index  != null ? ""+Index              : "*";      // Index in parent up
            P.elementAt(L+2).append("("+s.name().at()+", "+U+", "+I+")");       // Link to next level

            final String um = ""+B.memory.up();                                 // Parent up as recorded
            final String im = ""+B.upIndex();                                   // Index in parent up as recorded
            P.elementAt(L+3).append("("+s.name().at()+", "+um+", "+im+")");     // Link to next level
           }
         }
       }
     }

    if (Details)                                                                // Top of branch
     {P.elementAt(L+2).append
       ("{"+(B.top() != null ? B.top().name().at() : "null")+"}");
     }

    final boolean l = Leaf.ref(B.top()), b = Tree.Branch.ref(B.top());          // Print top leaf
    if      (l) printLeaf  (  (Leaf)B.top(), P, level+1, Details, B, null);
    else if (b) printBranch((Branch)B.top(), P, level+1, Details, B, null);

    padStrings(P, level);                                                       // Equalize the strings used to print the tree
   }

  String printBoxed()                                                           // Print a tree in a box
   {final String  s = ""+this;
    final int     n = longestLine(s)-1;
    final String[]L = s.split("\n");
    final StringJoiner t = new StringJoiner("\n",  "", "\n");
    t.add("+"+("-".repeat(n))+"+");
    for(String l : L) t.add("| "+l);
    t.add("+"+("-".repeat(n))+"+");
    return ""+t;
   }

  void padStrings(Stack<StringBuilder> S, int level)                            // Pad the strings at each level of the tree so we have a vertical face to continue with - a bit like Marc Brunel's tunneling shield
   {final int N = level * linesToPrintABranch + maxLeafSize.i();                // Number of lines we might want
    for (int i = S.size(); i <= N; ++i) S.push(new StringBuilder());            // Make sure we have a full deck of strings
    int m = 0;                                                                  // Maximum length
    for (StringBuilder s : S) m = m < s.length() ? s.length() : m;              // Find maximum length
    for (StringBuilder s : S)                                                   // Pad each string to maximum length
     {if (s.length() < m) s.append(" ".repeat(m - s.length()));                 // Pad string to maximum length
     }
   }

  String printCollapsed(Stack<StringBuilder> S)                                 // Collapse horizontal representation into a string
   {final StringBuilder t = new StringBuilder();                                // Print the lines of the tree that are not blank
    for  (StringBuilder s : S)
     {final String l = ""+s;
      if (!l.isBlank()) t.append(l+"|\n");
     }
    return ""+t;
   }

  public String toString() {return print(false);}                               // Print the tree without details
  public String dump()     {return print(true); }                               // Print the tree with details

  String print(boolean Details)                                                 // Print the tree with and without linkage details
   {final Stack<StringBuilder> P = new Stack<>();
    if (root() == null) return "|\n";                                           // Empty tree
    final boolean lr = Leaf.ref(root());
    if (lr) printLeaf  ((Leaf)  root(), P, 0, Details, null, null);             // Tree is a single leaf
    else    printBranch((Branch)root(), P, 0, Details, null, null);             // Tree has one or more branches
    return printCollapsed(P);                                                   // Remove blank lines and add right fence
   }

  String db()                                                                   // Raw dump of memory used by the tree to assist with debugging
   {final StringBuilder s = new StringBuilder();
    final ByteBuffer b = memory.bytes;
    final int N = b.capacity();
    for (int l = 0, c = 0; l < 10; ++l)                                         // Print first few nodes one per line
     {final StringJoiner j = new StringJoiner(" ");
      for (int i : range(sizeOfNode))
       {if (c++ >= N) break;
        j.add(""+b.get(l * sizeOfNode.i() + i));
       }
      s.append(""+j+"\n");
      if (c >= N) break;
     }
    return ""+s;
   }

//D1 List All                                                                   // Create lists of all leaves and branches in the tree

  class ListAll                                                                 // Create lists of all the leaves and branches in the tree to assist with debugging
   {final Stack<Branch>branches = new Stack<>();
    final Stack<Leaf>  leaves   = new Stack<>();

    void scan(Branch B)
     {new For (B.numberOfSlots())
       {boolean body(int i)
         {final Slots.Slot S = B.new Slot(i);
          if (B.usedSlots(S))
           {final Slots    s = B.data(S);
            final boolean  l = Leaf.ref(s), b = Branch.ref(s);

            if      (l)  leaves  .push((Leaf)  s);
            else if (b) {branches.push((Branch)s); scan((Branch)s);}
           }
          return true;
         }
       };

      final Slots s = B.top();
      final boolean l = Leaf.ref(s), b = Branch.ref(s);
      if      (l)  leaves  .push((Leaf)  s);
      else if (b) {branches.push((Branch)s); scan((Branch)s);}
     }

    ListAll()                                                                   // Create lists of all theleaves and branches in the tree to assist with debugging
     {if (root() == null) return;
      final boolean l = Leaf.ref(root()), b = Branch.ref(root());
      if      (l)  leaves  .push((Leaf)  root());
      else if (b) {branches.push((Branch)root()); scan((Branch)root());}
     }
   }

//D1 Tests                                                                      // Tests

//D2 Slots                                                                      // Test the slots

  static void test_locateNearestFreeSlot()
   {final Tree t = new Tree(8);
    final Slots s = t.new Slots(8);
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

    ok(s.new Slot( 1).locatePrevUsedSlot() == null, true);
    ok(s.new Slot(14).locateNextUsedSlot() == null, true);

    s.setSlots(0, 15);
   }

  static void test_redistribute()
   {final Tree  t =   new Tree (8);
    final Slots s = t.new Slots(8);
    for (int i : range(s.numberOfSlots())) {s.usedSlots(s.new Slot(i)); s.setSlots(i);}
                                                            //0123456789012345
                                          ok(s.printSlots(), "XXXXXXXXXXXXXXXX");
                        s.redistribute(); ok(s.printSlots(), "XXXXXXXXXXXXXXXX");
    s.clearFirstSlot(); s.redistribute(); ok(s.printSlots(), "XXXXXXXXXXXXXXX.");
    s.clearFirstSlot(); s.redistribute(); ok(s.printSlots(), ".XXXXXXXXXXXXXX.");
    s.clearFirstSlot(); s.redistribute(); ok(s.printSlots(), ".XXXXXXXXXXXXX..");
    s.clearFirstSlot(); s.redistribute(); ok(s.printSlots(), "..XXXXXXXXXXXX..");
    s.clearFirstSlot(); s.redistribute(); ok(s.printSlots(), "..XXXXXXXXXXX...");
    s.clearFirstSlot(); s.redistribute(); ok(s.printSlots(), "...XXXXXXXXXX...");
    s.clearFirstSlot(); s.redistribute(); ok(s.printSlots(), "...XXXXXXXXX....");
    s.clearFirstSlot(); s.redistribute(); ok(s.printSlots(), "X.X.X.X.X.X.X.X.");
    s.clearFirstSlot(); s.redistribute(); ok(s.printSlots(), ".X.X.X.X.X.X.X..");
    s.clearFirstSlot(); s.redistribute(); ok(s.printSlots(), "..X.X.X.X.X.X...");
    s.clearFirstSlot(); s.redistribute(); ok(s.printSlots(), ".X..X..X..X..X..");
    s.clearFirstSlot(); s.redistribute(); ok(s.printSlots(), ".X...X...X...X..");
    s.clearFirstSlot(); s.redistribute(); ok(s.printSlots(), "..X....X....X...");
    s.clearFirstSlot(); s.redistribute(); ok(s.printSlots(), "...X.......X....");
    s.clearFirstSlot(); s.redistribute(); ok(s.printSlots(), ".......X........");
    s.clearFirstSlot(); s.redistribute(); ok(s.printSlots(), "................");
   }

  static void test_ifd()
   {final Tree  t =   new Tree (8);
    final Slots s = t.new Slots(8);
    s.initialize();
                        ok(s.empty(), true);  ok(s.full(), false);
    s.insert(Key(14));  ok(s.empty(), false); ok(s.full(), false);
    s.insert(Key(13));  ok(s.countUsed(), 2);
    s.insert(Key(16));
    s.insert(Key(15));
    s.insert(Key(18));
    s.insert(Key(17));
    s.insert(Key(12));
    s.insert(Key(11));
    ok(s.printInOrder(), "11, 12, 13, 14, 15, 16, 17, 18");
                        ok(s.empty(), false);
                        ok(s.full(), true);
    ok(s, """
Slots    : name:  0, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   0   7   6   1   0   3   2   5   4   0   0   0
usedSlots:    .   .   .   .   .   X   X   X   X   X   X   X   X   .   .   .
usedRefs :    X   X   X   X   X   X   X   X
keys     :   14  13  16  15  18  17  12  11
""");
    ok(s.locate(Key(11)).i(),  5);
    ok(s.locate(Key(12)).i(),  6);
    ok(s.locate(Key(13)).i(),  7);
    ok(s.locate(Key(14)).i(),  8);
    ok(s.locate(Key(15)).i(),  9);
    ok(s.locate(Key(16)).i(), 10);
    ok(s.locate(Key(17)).i(), 11);
    ok(s.locate(Key(18)).i(), 12);
    ok(s.locate(Key(10)) == null, true);
    ok(s.locate(Key(20)) == null, true);

    ok(s.key(s.find(Key(14))).i(), 14); ok(s.delete(Key(14)), true); ok(s.printInOrder(), "11, 12, 13, 15, 16, 17, 18");
    ok(s.key(s.find(Key(12))).i(), 12); ok(s.delete(Key(12)), true); ok(s.printInOrder(), "11, 13, 15, 16, 17, 18");
    ok(s.key(s.find(Key(13))).i(), 13); ok(s.delete(Key(13)), true); ok(s.printInOrder(), "11, 15, 16, 17, 18");
    ok(s.key(s.find(Key(16))).i(), 16); ok(s.delete(Key(16)), true); ok(s.printInOrder(), "11, 15, 17, 18");
    ok(s.key(s.find(Key(18))).i(), 18); ok(s.delete(Key(18)), true); ok(s.printInOrder(), "11, 15, 17");
    ok(s.key(s.find(Key(11))).i(), 11); ok(s.delete(Key(11)), true); ok(s.printInOrder(), "15, 17");
    ok(s.key(s.find(Key(17))).i(), 17); ok(s.delete(Key(17)), true); ok(s.printInOrder(), "15");
    ok(s.key(s.find(Key(15))).i(), 15); ok(s.delete(Key(15)), true); ok(s.printInOrder(), "");

    ok(s.locate(Key(10))== null, true); ok(s.delete(Key(10)), false);
   }

  static void test_idn()                                                        // Repeated inserts and deletes
   {final Tree  t =   new Tree (8);
    final Slots s = t.new Slots(8);

    for (int i = 0; i < s.numberOfSlots().i()*10; i++)
     {s.insert(Key(14)); s.redistribute();
      s.insert(Key(13)); s.redistribute();
      s.insert(Key(16)); s.redistribute();
      s.insert(Key(15)); s.redistribute();
      ok(s.printInOrder(), "13, 14, 15, 16");
      ok(s.countUsed(), 4);
      s.delete(Key(14)); s.redistribute();
      s.delete(Key(13)); s.redistribute();
      s.delete(Key(16)); s.redistribute();
      s.delete(Key(15)); s.redistribute();
      ok(s.printInOrder(), "");
      ok(s.countUsed(), 0);
     }
   }

  static void test_tooManySearches()
   {final Tree  t =   new Tree (8);
    final Slots s = t.new Slots(8);

    s.insert(Key(10));
    s.insert(Key(20));
    ok(s.find(Key(15)) == null, true);
   }

  static void test_locateFirstGeKey()
   {final Tree  t =   new Tree (8);
    final Slots s = t.new Slots(8);

    s.usedSlots(s.new Slot( 1), true); s.slots(s.new Slot( 1), s.new slot(7)); s.usedRefs(s.new slot(7), true); s.key(s.new slot(7), Key(22));
    s.usedSlots(s.new Slot( 5), true); s.slots(s.new Slot( 5), s.new slot(4)); s.usedRefs(s.new slot(4), true); s.key(s.new slot(4), Key(24));
    s.usedSlots(s.new Slot( 9), true); s.slots(s.new Slot( 9), s.new slot(2)); s.usedRefs(s.new slot(2), true); s.key(s.new slot(2), Key(26));
    s.usedSlots(s.new Slot(14), true); s.slots(s.new Slot(14), s.new slot(0)); s.usedRefs(s.new slot(0), true); s.key(s.new slot(0), Key(28));
    ok(s, """
Slots    : name:  0, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   4   0   0   0   2   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   .   X   .
usedRefs :    X   .   X   .   X   .   .   X
keys     :   28   0  26   0  24   0   0  22
""");
    ok(s.locateFirstGe(Key(23)).value(),    5);
    ok(s.locateFirstGe(Key(24)).value(),    5);
    ok(s.locateFirstGe(Key(25)).value(),    9);
    ok(s.locateFirstGe(Key(30)) == null, true);
   }

  static void test_compactLeft()
   {final Tree  t =   new Tree (8);
    final Slots s = t.new Slots(8);

    s.usedSlots(s.new Slot( 1), true); s.slots(s.new Slot( 1), s.new slot(7)); s.usedRefs(s.new slot(7), true); s.key(s.new slot(7), Key(11));
    s.usedSlots(s.new Slot( 5), true); s.slots(s.new Slot( 5), s.new slot(4)); s.usedRefs(s.new slot(4), true); s.key(s.new slot(4), Key(12));
    s.usedSlots(s.new Slot( 9), true); s.slots(s.new Slot( 9), s.new slot(2)); s.usedRefs(s.new slot(2), true); s.key(s.new slot(2), Key(13));
    s.usedSlots(s.new Slot(14), true); s.slots(s.new Slot(14), s.new slot(0)); s.usedRefs(s.new slot(0), true); s.key(s.new slot(0), Key(14));
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

    s.usedSlots(s.new Slot( 1), true); s.slots(s.new Slot( 1), s.new slot(7)); s.usedRefs(s.new slot(7), true); s.key(s.new slot(7), Key(11));
    s.usedSlots(s.new Slot( 5), true); s.slots(s.new Slot( 5), s.new slot(4)); s.usedRefs(s.new slot(4), true); s.key(s.new slot(4), Key(12));
    s.usedSlots(s.new Slot( 9), true); s.slots(s.new Slot( 9), s.new slot(2)); s.usedRefs(s.new slot(2), true); s.key(s.new slot(2), Key(13));
    s.usedSlots(s.new Slot(14), true); s.slots(s.new Slot(14), s.new slot(0)); s.usedRefs(s.new slot(0), true); s.key(s.new slot(0), Key(14));
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

    ok(s.firstKey().value(), 11);
    ok(s. lastKey().value(), 14);
   }

  static void test_memory()
   {final Tree  t =   new Tree (8);
    final Slots s = t.new Slots(8, ByteBuffer.allocate(200));

    s.usedSlots(s.new Slot( 1), true); s.slots(s.new Slot( 1), s.new slot(7)); s.usedRefs(s.new slot(7), true); s.key(s.new slot(7), Key(11));
    s.usedSlots(s.new Slot( 5), true); s.slots(s.new Slot( 5), s.new slot(4)); s.usedRefs(s.new slot(4), true); s.key(s.new slot(4), Key(12));
    s.usedSlots(s.new Slot( 9), true); s.slots(s.new Slot( 9), s.new slot(2)); s.usedRefs(s.new slot(2), true); s.key(s.new slot(2), Key(13));
    s.usedSlots(s.new Slot(14), true); s.slots(s.new Slot(14), s.new slot(0)); s.usedRefs(s.new slot(0), true); s.key(s.new slot(0), Key(14));
    s.type     (new Int(11));
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

    ok(m.slots       (new Int(0)), 0);
    ok(m.slots       (new Int(1)), 7);
    ok(m.slots       (new Int(2)), 0);
    ok(m.slots       (new Int(3)), 0);
    ok(m.slots       (new Int(4)), 0);
    ok(m.slots       (new Int(5)), 4);
    ok(m.slots       (new Int(6)), 0);
    ok(m.usedSlots   (new Int(0)), false);
    ok(m.usedSlots   (new Int(1)), true);
    ok(m.usedSlots   (new Int(2)), false);
    ok(m.usedSlots   (new Int(3)), false);
    ok(m.usedSlots   (new Int(4)), false);
    ok(m.usedSlots   (new Int(5)), true);
    ok(m.usedSlots   (new Int(6)), false);
    ok(m.usedRefs    (new Int(0)), true);
    ok(m.usedRefs    (new Int(1)), false);
    ok(m.usedRefs    (new Int(2)), true);
    ok(m.usedRefs    (new Int(3)), false);
    ok(m.usedRefs    (new Int(4)), true);
    ok(m.usedRefs    (new Int(5)), false);
    ok(m.usedRefs    (new Int(6)), false);
    ok(m.keys        (new Int(0)), 14);
    ok(m.keys        (new Int(1)),  0);
    ok(m.keys        (new Int(2)), 13);
    ok(m.keys        (new Int(3)),  0);
    ok(m.keys        (new Int(4)), 12);
    ok(m.keys        (new Int(5)),  0);
    ok(m.keys        (new Int(6)),  0);

    m.slots    (new Int(13), new Int(6));
    m.usedSlots(new Int(13), true);
    m.usedRefs( new Int( 6), true);
    m.keys    ( new Int( 6), new Int(10));

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

  static void test_slots()                                                      // Tests thought to be in good shape
   {test_locateNearestFreeSlot();
    test_redistribute();
    test_ifd();
    test_idn();
    test_tooManySearches();
    test_locateFirstGeKey();
    test_compactLeft();
    test_compactRight();
    test_memory();
   }

//D2 Tree                                                                       // Test the btree

  final static int[]random_32 = {12, 3, 27, 1, 23, 20, 8, 18, 2, 31, 25, 16, 13, 32, 11, 21, 5, 24, 4, 10, 26, 30, 9, 6, 29, 17, 28, 15, 14, 19, 7, 22};
  final static int[]random    = {5918,5624,2514,4291,1791,5109,7993,60,1345,2705,5849,1034,2085,4208,4590,7740,9367,6582,4178,5578,1120,378,7120,8646,5112,4903,1482,8005,3801,5439,4534,9524,6111,204,5459,248,4284,8037,5369,7334,3384,5193,2847,1660,5605,7371,3430,1786,1216,4282,2146,1969,7236,2187,136,2726,9480,5,4515,6082,969,5017,7809,9321,3826,9179,5781,3351,4819,4545,8607,4146,6682,1043,2890,2964,7472,9405,4348,8333,2915,9674,7225,4743,995,1321,3885,6061,9958,3901,4710,4185,4776,5070,8892,8506,6988,2317,9342,3764,9859,4724,5195,673,359,9740,2089,9942,3749,9208,1,7446,7023,5496,4206,3272,3527,8593,809,3149,4173,9605,9021,5120,5265,7121,8667,6911,4717,2535,2743,1289,1494,3788,6380,9366,2732,1501,8543,8013,5612,2393,7041,3350,3204,288,7213,1741,1238,9830,6722,4687,6758,8067,4443,5013,5374,6986,282,6762,192,340,5075,6970,7723,5913,1060,1641,1495,5738,1618,157,6891,173,7535,4952,9166,8950,8680,1974,5466,2383,3387,3392,2188,3140,6806,3131,6237,6249,7952,1114,9017,4285,7193,3191,3763,9087,7284,9170,6116,3717,6695,6538,6165,6449,8960,2897,6814,3283,6600,6151,4624,3992,5860,9557,1884,5585,2966,1061,6414,2431,9543,6654,7417,2617,878,8848,8241,3790,3370,8768,1694,9875,9882,8802,7072,3772,2689,5301,7921,7774,1614,494,2338,8638,4161,4523,5709,4305,17,9626,843,9284,3492,7755,5525,4423,9718,2237,7401,2686,8751,1585,5919,9444,3271,1490,7004,5980,3904,370,5930,6304,7737,93,5941,9079,4968,9266,262,2766,4999,2450,9518,5137,8405,483,8840,2231,700,8049,8823,9811,9378,3811,8074,153,1940,1998,4354,7830,7086,6132,9967,5680,448,1976,4101,7839,3122,4379,9296,4881,1246,4334,9457,5401,1945,9548,8290,1184,3464,132,2458,7704,1056,7554,6203,2270,6070,4889,7369,1676,485,3648,357,1912,9661,4246,1576,1836,4521,7667,6907,2098,8825,7404,4019,8284,3710,7202,7050,9870,3348,3624,9224,6601,7897,6288,3713,932,5596,353,2615,3273,833,1446,8624,2489,3872,486,1091,2493,4157,3611,6570,7107,9153,4543,9504,4746,1342,9737,3247,8984,3640,5698,7814,307,8775,1150,4330,3059,5784,2370,5248,4806,6107,9700,231,3566,5627,3957,5317,5415,8119,2588,9440,2961,9786,4769,466,5411,3080,7623,5031,2378,9286,4801,797,1527,2325,847,6341,5310,1926,9481,2115,2165,5255,5465,5561,3606,7673,7443,7243,8447,2348,7925,6447,8311,6729,4441,7763,8107,267,8135,9194,6775,3883,9639,612,5024,1351,7557,9241,5181,2239,8002,5446,747,166,325,9925,3820,9531,5163,3545,558,7103,7658,5670,8323,4821,6263,7982,59,3700,1082,4474,4353,8637,9558,5191,842,5925,6455,4092,9929,9961,290,3523,6290,7787,8266,7986,7269,6408,3620,406,5964,7289,1620,6726,1257,1993,7006,5545,2913,5093,5066,3019,7081,6760,6779,7061,9051,8852,8118,2340,6596,4594,9708,8430,8659,8920,9268,5431,9203,2823,1427,2203,6422,6193,5214,9566,8791,4964,7575,4350,56,2227,8545,5646,3089,2204,4081,487,8496,2258,4336,6955,3452,556,8602,8251,8569,8636,9430,1025,9459,7137,8392,3553,5945,9414,3078,1688,5480,327,8117,2289,2195,8564,9423,103,7724,3091,8548,7298,5279,6042,2855,3286,3542,9361,420,7020,4112,5320,5366,6379,114,9174,9744,592,5346,3985,3174,5157,9890,1605,3082,8099,4346,7256,8670,5687,6613,6620,1458,1045,7917,2980,2399,1433,3315,4084,178,7056,2132,2728,4421,9195,4181,6017,6229,2945,4627,2809,8816,6737,18,8981,3813,8890,5304,3789,6959,7476,1856,4197,6944,9578,5915,3060,9932,3463,67,7393,9857,5822,3187,501,653,8453,3691,9736,6845,1365,9645,4120,2157,8471,4436,6435,2758,7591,9805,7142,7612,4891,7342,5764,8683,8365,2967,6947,441,2116,6612,1399,7585,972,6548,5481,7733,7209,222,5903,6161,9172,9628,7348,1588,5992,6094,7176,4214,8702,2987,74,8486,9788,7164,5788,8535,8422,6826,1800,8965,4965,565,5609,4686,2556,9324,5000,9809,1994,4737,63,8992,4783,2536,4462,8868,6346,5553,3980,2670,1601,4272,8725,4698,7333,7826,9233,4198,1997,1687,4851,62,7893,8149,8015,341,2230,1280,5559,9756,3761,7834,6805,9287,4622,5748,2320,1958,9129,9649,1644,4323,5096,9490,7529,6444,7478,7044,9525,7713,234,7553,9099,9885,7135,6493,9793,6268,8363,2267,9157,9451,1438,9292,1637,3739,695,1090,4731,4549,5171,5975,7347,5192,5243,1084,2216,9860,3318,5594,5790,1107,220,9397,3378,1353,4498,6497,5442,7929,7377,9541,9871,9895,6742,9146,9409,292,6278,50,5288,2217,4923,6790,4730,9240,3006,3547,9347,7863,4275,3287,2673,7485,1915,9837,2931,3918,635,9131,1197,6250,3853,4303,790,5548,9993,3702,2446,3862,9652,4432,973,41,3507,8585,2444,1633,956,5789,1523,8657,4869,8580,8474,7093,7812,2549,7363,9315,6731,1130,7645,7018,7852,362,1636,2905,8006,4040,6643,8052,7021,3665,8383,715,1876,2783,3065,604,4566,8761,7911,1983,3836,5547,8495,8144,1950,2537,8575,640,8730,8303,1454,8165,6647,4762,909,9449,8640,9253,7293,8767,3004,4623,6862,8994,2520,1215,6299,8414,2576,6148,1510,313,3693,9843,8757,5774,8871,8061,8832,5573,5275,9452,1248,228,9749,2730};

  static void test_emptyTree()
   {final Tree t = new Tree(2, 3,4);
    ok(t.memory.maxLeafSize(),   2);
    ok(t.memory.maxBranchSize(), 3);
    ok(t.memory.numberOfNodes(), 4);
    ok(t.memory.root(),          0);
    ok(t, """
|
""");
   }

  static void test_compactLeafLeft()
   {final Leaf l = new Tree(8).new Leaf();
    l.insert(Key(13), new Data(23));
    l.insert(Key(12), new Data(22));
    l.insert(Key(14), new Data(24));
    l.insert(Key(11), new Data(21));
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
   {final Leaf l = new Tree(8).new Leaf();
    l.insert(Key(13), new Data(23));
    l.insert(Key(12), new Data(22));
    l.insert(Key(14), new Data(24));
    l.insert(Key(11), new Data(21));

  //stop(l);
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
  //stop(l);
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
Tree.debug = true;
    b.insert(Key(12), t.fake(t.new Allocation(22)));
    b.insert(Key(11), t.fake(t.new Allocation(21)));
    b.insert(Key(13), t.fake(t.new Allocation(23)));
    b.top(t.fake(t.new Allocation(4)));
    //stop(b);
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
    //stop(b);
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

  static void test_compactBranchRight()
   {final Tree t = new Tree(8);
    final Branch b = t.new Branch();
    b.insert(Key(12), t.fake(t.new Allocation(12)));
    b.insert(Key(11), t.fake(t.new Allocation(11)));
    b.insert(Key(13), t.fake(t.new Allocation(13)));
    b.top(t.fake(t.new Allocation(4)));
    //stop(b);
    ok(b, """
Branch   :    1   up:    0  index:    0
Slots    : name:  1, type:  1, refs:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slots    :    0   0   0   0   0   0   1   0   2   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X   X   .   .   .   .   .
usedRefs :    X   X   X   .   .   .   .
keys     :   12  11  13   0   0   0   0
data     :  -12 -11 -13   .   .   .   .
top      :   -4
""");

    b.compactRight();
    //stop(b);
    ok(b, """
Branch   :    1   up:    0  index:    0
Slots    : name:  1, type:  1, refs:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slots    :    0   0   0   0   4   5   6   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   X   X   X   .   .   .   .   .   .   .
usedRefs :    .   .   .   .   X   X   X
keys     :    0   0   0   0  11  12  13
data     :    .   .   .   . -11 -12 -13
top      :   -4
""");
   }

  static  Data[]test_leaf_data(int...Values)
   {final Data[]d = new Data[Values.length];
    for (int i = 0; i < d.length; i++) d[i] = new Data(new Int(Values[i]));
    return d;
   }

  static Leaf test_leaf()
   {final Leaf  l = new Tree(8).new Leaf();
    final Data[]d = test_leaf_data(13, 16, 15, 18, 17, 14, 12, 11);
    for (int i = 0; i < d.length; i++) l.insert(Key(d[i].value()), d[i]);
    return l;
   }

  static void test_duplicate_leaf()
   {final Leaf l = test_leaf();
    final Leaf L = l.duplicate();
    //stop(l);
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
    //stop(L);
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
    //stop(l);
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
    //stop(l);
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
    //stop(r);
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

  static Branch test_branch()
   {final Tree t = new Tree(8);
    final Branch b = t.new Branch();

    final int []k = new int [] {13, 16, 15, 17, 14, 12, 11};
    final int []d = new int [] {3,   6,  5,  7,  4,  2, 1};
    for (int i = 0; i < d.length; i++) b.insert(Key(k[i]), t.fake(t.new Allocation(d[i])));
    b.top(t.fake(t.new Allocation(8)));
    return b;
   }

  static void test_duplicate_branch()
   {final Branch b = test_branch();
    final Branch B = b.duplicate();
    //stop(b);
    ok(b, """
Branch   :    1   up:    0  index:    0
Slots    : name:  1, type:  1, refs:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slots    :    6   5   0   0   4   0   2   0   1   0   3   0   0   0
usedSlots:    X   X   X   .   X   .   X   .   X   .   X   .   .   .
usedRefs :    X   X   X   X   X   X   X
keys     :   13  16  15  17  14  12  11
data     :   -3  -6  -5  -7  -4  -2  -1
top      :   -8
""");
    //stop(B);
    ok(B, """
Branch   :    2   up:    0  index:    0
Slots    : name:  2, type:  1, refs:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slots    :    6   5   0   0   4   0   2   0   1   0   3   0   0   0
usedSlots:    X   X   X   .   X   .   X   .   X   .   X   .   .   .
usedRefs :    X   X   X   X   X   X   X
keys     :   13  16  15  17  14  12  11
data     :   -3  -6  -5  -7  -4  -2  -1
top      :   -8
""");
   }

  static void test_splitLeftBranchIntoRight()
   {final Branch       b = test_branch();
    final Branch.Split s = b.splitRight();
    ok(s.left.printInOrder(), """
keys: 11, 12, 13
data: -1, -2, -3
top : 4
""");
    //stop(s.right);
    ok(s.right.printInOrder(), """
keys: 15, 16, 17
data: -5, -6, -7
top : 8
""");
    ok(s.key.value(), 14);
   }

  static void test_splitRightBranchIntoLeft()
   {final Branch       r = test_branch();
    final Branch.Split s = r.splitLeft();
    //stop(s.left);
    ok(s.left.printInOrder(), """
keys: 11, 12, 13
data: -1, -2, -3
top : 4
""");
    //stop(s.right);
    ok(s.right.printInOrder(), """
keys: 15, 16, 17
data: -5, -6, -7
top : 8
""");
    ok(s.key.value(), 14);
   }

  static Leaf test_leaf1()
   {final Leaf  l = new Tree(8,7).new Leaf();
    final Data[]d = test_leaf_data(13, 14, 12, 11);
    for (int i = 0; i < d.length; i++) l.insert(Key(d[i].value()), d[i]);
    return l;
   }

  static Leaf test_leaf2()
   {final Leaf  l = new Tree(8,7).new Leaf();
    final Data[]d = test_leaf_data(16, 15, 18, 17);
    for (int i = 0; i < d.length; i++) l.insert(Key(d[i].value()), d[i]);
    return l;
   }

  static void test_mergeLeafLeft()
   {final Leaf l = test_leaf1();
    final Leaf r = test_leaf2();
    l.mergeFromRight(r);
    //stop(l);
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
    //stop(r));
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

  static Branch test_branch1()
   {final Tree t = new Tree(8);
    final Branch b = t.new Branch();

    final int []k = new int[]{13, 12, 11};
    final int []d = new int[]{ 3,  2,  1};
    for (int i = 0; i < k.length; i++) b.insert(Key(k[i]), t.fake(t.new Allocation(d[i])));
    b.top(t.fake(t.new Allocation(4)));
    return b;
   }

  static Branch test_branch2()
   {final Tree t = new Tree(8);
    final Branch b = t.new Branch();

    final int []k = new int[]{16, 15, 17};
    final int []d = new int[]{6, 5, 7};
    for (int i = 0; i < k.length; i++) b.insert(Key(k[i]), t.fake(t.new Allocation(d[i])));
    b.top(t.fake(t.new Allocation(8)));
    return b;
   }

  static void test_mergeBranchLeft()
   {final Branch l = test_branch1();
    final Branch r = test_branch2();
    l.mergeFromRight(Key(14), r);
    //stop(l);
    ok(l, """
Branch   :    1   up:    0  index:    0
Slots    : name:  1, type:  1, refs:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slots    :    0   0   1   0   2   0   3   0   4   0   5   0   6   0
usedSlots:    X   .   X   .   X   .   X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X   X   X   X
keys     :   11  12  13  14  15  16  17
data     :   -1  -2  -3  -4  -5  -6  -7
top      :   -8
""");
   }

  static void test_mergeBranchRight()
   {final Branch l = test_branch1();
    final Branch r = test_branch2();
    r.mergeFromLeft(Key(14), l);
    //stop(r);
    ok(r, """
Branch   :    1   up:    0  index:    0
Slots    : name:  1, type:  1, refs:  7
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slots    :    0   0   1   0   2   0   3   0   4   0   5   0   6   0
usedSlots:    X   .   X   .   X   .   X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X   X   X   X
keys     :   11  12  13  14  15  16  17
data     :   -1  -2  -3  -4  -5  -6  -7
top      :   -8
""");
   }

  static void test_locateFirstGe()
   {final Tree t = new Tree(8);
    final Slots b = t.new Slots(16);
    b.insert(Key(1));
    b.insert(Key(5));
    b.insert(Key(3));
    b.redistribute();
    //stop(b);
    ok(b, """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   0   0   0   2   0   0   0   0   1   0   0   0
usedSlots:    .   .   X   .   .   .   .   X   .   .   .   .   X   .   .   .
usedRefs :    X   X   X   .   .   .   .   .   .   .   .   .   .   .   .   .
keys     :  1.0 5.0 3.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0
""");

    ok(b.locateFirstGe(Key(0)),  2);
    ok(b.locateFirstGe(Key(1)),  2);
    ok(b.locateFirstGe(Key(2)),  7);
    ok(b.locateFirstGe(Key(3)),  7);
    ok(b.locateFirstGe(Key(4)), 12);
    ok(b.locateFirstGe(Key(5)), 12);
    ok(b.locateFirstGe(Key(6)), null);
   }

  static void test_splitLeaf()
   {final Branch b = test_leaf().split();
    final Tree   t = b.tree();
    t.root(b);
    ok(t, """
            14           |
11,12,13,14   15,16,17,18|
""");
   }

  static void test_splitBranch()
   {final Branch B = test_branch();
    final Branch b = B.split();
    final Tree   t = b.tree();
    t.root(b);
    ok(t.new Branch(t.new Allocation(4)).printInOrder(), """
keys: 14
data: 2
top : 3
""");
    ok(t.new Branch(t.new Allocation(2)).printInOrder(), """
keys: 11, 12, 13
data: -1, -2, -3
top : 4
""");
    ok(t.new Branch(t.new Allocation(3)).printInOrder(), """
keys: 15, 16, 17
data: -5, -6, -7
top : 8
""");
   }

  static void test_insert2()
   {final Tree t = new Tree(2, 3);
    final int N = 32;
    final StringBuilder s = new StringBuilder();
    for (int i = 1; i <= N; ++i)
     {t.insert(Key(i), new Data(i));
      t.freeCheck();
      s.append("Insert: "+i+"\n"+t);
     }
    //stop(s);
    ok(""+s, """
Insert: 1
1|
Insert: 2
1,2|
Insert: 3
  1   |
1  2,3|
Insert: 4
    2   |
1,2  3,4|
Insert: 5
    2  3   |
1,2  3  4,5|
Insert: 6
    2    4   |
1,2  3,4  5,6|
Insert: 7
    2    4  5   |
1,2  3,4  5  6,7|
Insert: 8
    2    4    6   |
1,2  3,4  5,6  7,8|
Insert: 9
         4           |
    2         6  7   |
1,2  3,4  5,6  7  8,9|
Insert: 10
         4              |
    2         6    8    |
1,2  3,4  5,6  7,8  9,10|
Insert: 11
         4                  |
    2         6    8  9     |
1,2  3,4  5,6  7,8  9  10,11|
Insert: 12
                   8            |
    2    4    6          10     |
1,2  3,4  5,6  7,8  9,10   11,12|
Insert: 13
                   8                 |
    2    4    6          10   11     |
1,2  3,4  5,6  7,8  9,10   11   12,13|
Insert: 14
                   8                    |
    2    4    6          10      12     |
1,2  3,4  5,6  7,8  9,10   11,12   13,14|
Insert: 15
                   8                         |
    2    4    6          10      12   13     |
1,2  3,4  5,6  7,8  9,10   11,12   13   14,15|
Insert: 16
                   8                            |
    2    4    6          10      12      14     |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16|
Insert: 17
                   8             12                  |
    2    4    6          10              14   15     |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15   16,17|
Insert: 18
                   8             12                     |
    2    4    6          10              14      16     |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18|
Insert: 19
                   8             12                          |
    2    4    6          10              14      16   17     |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17   18,19|
Insert: 20
                   8                             16             |
    2    4    6          10      12      14              18     |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18   19,20|
Insert: 21
                   8                             16                  |
    2    4    6          10      12      14              18   19     |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18   19   20,21|
Insert: 22
                   8                             16                     |
    2    4    6          10      12      14              18      20     |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18   19,20   21,22|
Insert: 23
                   8                             16                          |
    2    4    6          10      12      14              18      20   21     |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18   19,20   21   22,23|
Insert: 24
                   8                             16                             |
    2    4    6          10      12      14              18      20      22     |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18   19,20   21,22   23,24|
Insert: 25
                   8                             16              20                  |
    2    4    6          10      12      14              18              22   23     |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18   19,20   21,22   23   24,25|
Insert: 26
                   8                             16              20                     |
    2    4    6          10      12      14              18              22      24     |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26|
Insert: 27
                   8                             16              20                          |
    2    4    6          10      12      14              18              22      24   25     |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18   19,20   21,22   23,24   25   26,27|
Insert: 28
                   8                             16                              24             |
    2    4    6          10      12      14              18      20      22              26     |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28|
Insert: 29
                   8                             16                              24                  |
    2    4    6          10      12      14              18      20      22              26   27     |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27   28,29|
Insert: 30
                   8                             16                              24                     |
    2    4    6          10      12      14              18      20      22              26      28     |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28   29,30|
Insert: 31
                   8                             16                              24                          |
    2    4    6          10      12      14              18      20      22              26      28   29     |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28   29   30,31|
Insert: 32
                   8                             16                              24                             |
    2    4    6          10      12      14              18      20      22              26      28      30     |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28   29,30   31,32|
""");
   }

  static void test_insert()
   {final Tree t = new Tree(4, 3);
    ok(t.count(), 0);
    t.insert(Key(11), new Data(21));
    ok(t, """
11|
""");
    t.insert(Key(13), new Data(23));
    ok(t, """
11,13|
""");
    t.insert(Key(12), new Data(22));
    ok(t, """
11,12,13|
""");
    t.insert(Key(14), new Data(24));
    ok(t, """
11,12,13,14|
""");
    t.insert(Key(15), new Data(25));
    ok(t, """
      12        |
11,12   13,14,15|
""");
    t.insert(Key(16), new Data(26));
    ok(t, """
      12           |
11,12   13,14,15,16|
""");
    t.insert(Key(17), new Data(27));
    ok(t, """
            14        |
11,12,13,14   15,16,17|
""");
    t.insert(Key(18), new Data(28));
    ok(t, """
            14           |
11,12,13,14   15,16,17,18|
""");
    t.insert(Key(19), new Data(29));
    ok(t, """
            14      16        |
11,12,13,14   15,16   17,18,19|
""");
    t.insert(Key(20), new Data(30));
    ok(t, """
            14      16           |
11,12,13,14   15,16   17,18,19,20|
""");
    t.insert(Key(21), new Data(31));
    ok(t, """
            14            18        |
11,12,13,14   15,16,17,18   19,20,21|
""");
    t.insert(Key(22), new Data(32));
    ok(t, """
            14            18           |
11,12,13,14   15,16,17,18   19,20,21,22|
""");
    t.insert(Key(23), new Data(33));
    ok(t, """
            14            18      20        |
11,12,13,14   15,16,17,18   19,20   21,22,23|
""");
    t.insert(Key(24), new Data(34));
    ok(t, """
            14            18      20           |
11,12,13,14   15,16,17,18   19,20   21,22,23,24|
""");
    t.insert(Key(25), new Data(35));
    ok(t, """
            14            18            22        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25|
""");
    ok(t.count(), 15);
    t.insert(Key(26), new Data(36));
    ok(t, """
            14            18            22           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26|
""");
    ok(t.count(), 16);
    t.insert(Key(27), new Data(37));
    ok(t, """
                          18                              |
            14                          22      24        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24   25,26,27|
""");
    ok(t.count(), 17);
    t.insert(Key(28), new Data(38));
    ok(t, """
                          18                                 |
            14                          22      24           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24   25,26,27,28|
""");
    ok(t.count(), 18);
    t.insert(Key(29), new Data(39));
    ok(t, """
                          18                                    |
            14                          22            26        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29|
""");
    t.insert(Key(30), new Data(40));
    ok(t, """
                          18                                       |
            14                          22            26           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30|
""");
    t.insert(Key(31), new Data(41));
    ok(t, """
                          18                                            |
            14                          22            26      28        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28   29,30,31|
""");
    t.insert(Key(32), new Data(42));
    ok(t, """
                          18                                               |
            14                          22            26      28           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28   29,30,31,32|
""");
    t.insert(Key(33), new Data(43));
    ok(""+t, """
                                                      26                      |
            14            18            22                          30        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33|
""");
    t.insert(Key(34), new Data(44));
    ok(t, """
                                                      26                         |
            14            18            22                          30           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34|
""");
    t.insert(Key(35), new Data(45));
    ok(t, """
                                                      26                              |
            14            18            22                          30      32        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32   33,34,35|
""");
    t.insert(Key(36), new Data(46));
    ok(t, """
                                                      26                                 |
            14            18            22                          30      32           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32   33,34,35,36|
""");
    t.insert(Key(37), new Data(47));
    ok(t, """
                                                      26                                    |
            14            18            22                          30            34        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36,37|
""");
    t.insert(Key(38), new Data(48));
    ok(t, """
                                                      26                                       |
            14            18            22                          30            34           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36,37,38|
""");
    t.insert(Key(39), new Data(49));
    ok(t, """
                                                      26                                            |
            14            18            22                          30            34      36        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36   37,38,39|
""");
    t.insert(Key(40), new Data(50));
    ok(t, """
                                                      26                                               |
            14            18            22                          30            34      36           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36   37,38,39,40|
""");
    t.insert(Key(41), new Data(51));
    ok(t, """
                                                      26                                                  |
            14            18            22                          30            34            38        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36,37,38   39,40,41|
""");
    t.insert(Key(42), new Data(52));
    ok(t, """
                                                      26                                                     |
            14            18            22                          30            34            38           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36,37,38   39,40,41,42|
""");
    ok(t.count(), 32);

    ok(t.find(Key(10)), """
Find Key : 10
Leaf     : 1 up: 7 index: 0
Slots    : name:  1, type:  0, refs:  4
positions:    0   1   2   3   4   5   6   7
slots    :    0   0   1   0   2   0   3   0
usedSlots:    X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X
keys     :   11  12  13  14
data     :   21  22  23  24
Locate      :  0  below all
Path        : 7, 9
""");

    ok(t.find(Key(23)), """
Find Key : 23
Leaf     : 10 up: 7 index: null
Slots    : name: 10, type:  0, refs:  4
positions:    0   1   2   3   4   5   6   7
slots    :    0   0   1   0   2   0   3   0
usedSlots:    X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X
keys     :   23  24  25  26
data     :   33  34  35  36
Locate      : 0 exact
Path        : 7, 9
""");
   }

  static void test_insert_reverse()
   {final Tree t = new Tree(4, 3);
    final int N = 32;
    for (int i = N; i > 0; i--)
     {t.insert(Key(i), new Data(i));
      ok(t.count(), N-i+1);
     }
    ok(t, """
                                           16                                                     |
        4        8           12                          20            24            28           |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
""");
    final Find n1 = t.first();
    ok(n1, """
Find Key : 1
Leaf     : 12 up: 7 index: 0
Slots    : name: 12, type:  0, refs:  4
positions:    0   1   2   3   4   5   6   7
slots    :    3   0   2   0   1   0   0   0
usedSlots:    X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X
keys     :    4   3   2   1
data     :    4   3   2   1
Locate      : 0 exact
Path        : 7, 9
""");

    final Find  n2 = t.next(n1);  ok(n2.key.value(),   2);
    final Find  n3 = t.next(n2);  ok(n3.key.value(),   3);
    final Find  n4 = t.next(n3);  ok(n4.key.value(),   4);
    final Find  n5 = t.next(n4);  ok(n5.key.value(),   5);
    final Find  n6 = t.next(n5);  ok(n6.key.value(),   6);
    final Find  n7 = t.next(n6);  ok(n7.key.value(),   7);
    final Find  n8 = t.next(n7);  ok(n8.key.value(),   8);
    final Find  n9 = t.next(n8);  ok(n9.key.value(),   9);
    final Find n10 = t.next(n9);  ok(n10.key.value(), 10);
    final Find n11 = t.next(n10); ok(n11.key.value(), 11);
    final Find n12 = t.next(n11); ok(n12.key.value(), 12);
    final Find n13 = t.next(n12); ok(n13.key.value(), 13);
    final Find n14 = t.next(n13); ok(n14.key.value(), 14);
    final Find n15 = t.next(n14); ok(n15.key.value(), 15);
    final Find n16 = t.next(n15); ok(n16.key.value(), 16);
    final Find n17 = t.next(n16); ok(n17.key.value(), 17);
    final Find n18 = t.next(n17); ok(n18.key.value(), 18);
    final Find n19 = t.next(n18); ok(n19.key.value(), 19);
    final Find n20 = t.next(n19); ok(n20.key.value(), 20);
    final Find n21 = t.next(n20); ok(n21.key.value(), 21);
    final Find n22 = t.next(n21); ok(n22.key.value(), 22);
    final Find n23 = t.next(n22); ok(n23.key.value(), 23);
    final Find n24 = t.next(n23); ok(n24.key.value(), 24);
    final Find n25 = t.next(n24); ok(n25.key.value(), 25);
    final Find n26 = t.next(n25); ok(n26.key.value(), 26);
    final Find n27 = t.next(n26); ok(n27.key.value(), 27);
    final Find n28 = t.next(n27); ok(n28.key.value(), 28);
    final Find n29 = t.next(n28); ok(n29.key.value(), 29);
    final Find n30 = t.next(n29); ok(n30.key.value(), 30);
    final Find n31 = t.next(n30); ok(n31.key.value(), 31);
    final Find n32 = t.next(n31); ok(n32.key.value(), 32);
    final Find n33 = t.next(n32); ok(n33 == null, true);

    ok(t.last(), """
Find Key : 32
Leaf     : 3 up: 2 index: null
Slots    : name:  3, type:  0, refs:  4
positions:    0   1   2   3   4   5   6   7
slots    :    0   0   1   0   2   0   3   0
usedSlots:    X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X
keys     :   29  30  31  32
data     :   29  30  31  32
Locate      : 6 exact
Path        : 2, 9
""");


    final Find p31 = t.prev(n32); ok(p31.key.value(), 31);
    final Find p30 = t.prev(p31); ok(p30.key.value(), 30);
    final Find p29 = t.prev(p30); ok(p29.key.value(), 29);
    final Find p28 = t.prev(p29); ok(p28.key.value(), 28);
    final Find p27 = t.prev(p28); ok(p27.key.value(), 27);
    final Find p26 = t.prev(p27); ok(p26.key.value(), 26);
    final Find p25 = t.prev(p26); ok(p25.key.value(), 25);
    final Find p24 = t.prev(p25); ok(p24.key.value(), 24);
    final Find p23 = t.prev(p24); ok(p23.key.value(), 23);
    final Find p22 = t.prev(p23); ok(p22.key.value(), 22);
    final Find p21 = t.prev(p22); ok(p21.key.value(), 21);
    final Find p20 = t.prev(p21); ok(p20.key.value(), 20);
    final Find p19 = t.prev(p20); ok(p19.key.value(), 19);
    final Find p18 = t.prev(p19); ok(p18.key.value(), 18);
    final Find p17 = t.prev(p18); ok(p17.key.value(), 17);
    final Find p16 = t.prev(p17); ok(p16.key.value(), 16);
    final Find p15 = t.prev(p16); ok(p15.key.value(), 15);
    final Find p14 = t.prev(p15); ok(p14.key.value(), 14);
    final Find p13 = t.prev(p14); ok(p13.key.value(), 13);
    final Find p12 = t.prev(p13); ok(p12.key.value(), 12);
    final Find p11 = t.prev(p12); ok(p11.key.value(), 11);
    final Find p10 = t.prev(p11); ok(p10.key.value(), 10);
    final Find  p9 = t.prev(p10); ok(p9.key.value(),   9);
    final Find  p8 = t.prev(p9);  ok(p8.key.value(),   8);
    final Find  p7 = t.prev(p8);  ok(p7.key.value(),   7);
    final Find  p6 = t.prev(p7);  ok(p6.key.value(),   6);
    final Find  p5 = t.prev(p6);  ok(p5.key.value(),   5);
    final Find  p4 = t.prev(p5);  ok(p4.key.value(),   4);
    final Find  p3 = t.prev(p4);  ok(p3.key.value(),   3);
    final Find  p2 = t.prev(p3);  ok(p2.key.value(),   2);
    final Find  p1 = t.prev(p2);  ok(p1.key.value(),   1);
    final Find  p0 = t.prev(p1);  ok(p0 == null, true);
   }

  static Tree test_insert_random_32()
   {final Tree t = new Tree(4, 3);
    for (int i = 0; i < random_32.length; i++)
     {t.insert(Key(random_32[i]), new Data(i));
      t.freeCheck();
      ok(t.count(), i+1);
     }
    ok(t, """
                                        15                                       26                   |
        4      7          11                          19      21         24                    30     |
1,2,3,4  5,6,7  8,9,10,11   12,13,14,15   16,17,18,19   20,21   22,23,24   25,26   27,28,29,30   31,32|
""");
    return t;
   }

  static Tree test_insert_random()
   {final Tree t = new Tree(4, 3);

    for (int i : range(random.length))
     {t.insert(Key(random[i]), new Data(i));
      t.freeCheck();
      ok(t.count(), i+1);
     }
    //stop(t);
    ok(""+t, """
                                                                                            2152                                                                                                                              4957                                                                                      6829                                                                                                                                                           |
                        706                                                                                                                             3441                                    4249                                                        5518                                                                                                               7866                                                             9250                                   |
            368                           1232                1571           1788                          2453           2776                3210                3622           3855                          4524                     5156                          5736           6014           6346                          7071                7352      7606                               8325           8626           8921                               9599               |
 98 226 285     484 583     838 1014 1081      1333 1430 1488      1616 1650      1971 2093      2253 2350      2545 2697      2927 3048 3126      3367      3536      3713 3782      4023 4167      4326 4401      4713 4797      5043      5221 5345 5449      5608      5815 5918      6133 6243      6493 6632 6740      7005      7120 7167 7256      7459      7713 7757      7997 8052 8157      8471 8549      8673 8779      9035 9172      9354 9423 9499      9687 9799 9864|
""");

    return t;
   }

  static void test_delete()
   {final Tree t = new Tree(4, 3);
    final int N = 32;
    for (int i = 1; i <= N; ++i) t.insert(Key(i), new Data(i));

    final StringBuilder s = new StringBuilder();
    s.append("Start\n"+t);
    for (int i = 1; i <= N; ++i)
     {t.delete(Key(i));
      ok(t.count(), N-i);
      s.append("Delete: "+i+"\n"+t);
     }
    ok(s, """
Start
                                           16                                                     |
        4        8           12                          20            24            28           |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 1
                                         16                                                     |
      4        8           12                          20            24            28           |
2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 2
                                       16                                                     |
    4        8           12                          20            24            28           |
3,4  5,6,7,8  9,10,11,12   13,14,15,16   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 3
                                     16                                                     |
  4        8           12                          20            24            28           |
4  5,6,7,8  9,10,11,12   13,14,15,16   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 4
                                  16                                                     |
        8           12                          20            24            28           |
5,6,7,8  9,10,11,12   13,14,15,16   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 5
                                16                                                     |
      8           12                          20            24            28           |
6,7,8  9,10,11,12   13,14,15,16   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 6
                              16                                                     |
    8           12                          20            24            28           |
7,8  9,10,11,12   13,14,15,16   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 7
                            16                                                     |
  8           12                          20            24            28           |
8  9,10,11,12   13,14,15,16   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 8
                         16                                                     |
           12                          20            24            28           |
9,10,11,12   13,14,15,16   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 9
                       16                                                     |
         12                          20            24            28           |
10,11,12   13,14,15,16   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 10
                    16                                                     |
      12                          20            24            28           |
11,12   13,14,15,16   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 11
                 16                                                     |
   12                          20            24            28           |
12   13,14,15,16   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 12
            16                                                     |
                          20            24            28           |
13,14,15,16   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 13
         16                                                     |
                       20            24            28           |
14,15,16   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 14
      16                                                     |
                    20            24            28           |
15,16   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 15
   16                                                     |
                 20            24            28           |
16   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 16
 16                                                     |
               20            24            28           |
   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 17
 16                                                  |
            20            24            28           |
   18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 18
 16                                               |
         20            24            28           |
   19,20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 19
 16                                            |
      20            24            28           |
   20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 20
 16            24            28           |
   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 21
         24            28           |
22,23,24   25,26,27,28   29,30,31,32|
Delete: 22
      24            28           |
23,24   25,26,27,28   29,30,31,32|
Delete: 23
   24            28           |
24   25,26,27,28   29,30,31,32|
Delete: 24
            28           |
25,26,27,28   29,30,31,32|
Delete: 25
         28           |
26,27,28   29,30,31,32|
Delete: 26
      28           |
27,28   29,30,31,32|
Delete: 27
   28           |
28   29,30,31,32|
Delete: 28
29,30,31,32|
Delete: 29
30,31,32|
Delete: 30
31,32|
Delete: 31
32|
Delete: 32
|
""");
   }
  static void test_delete_descending()
   {final Tree t = new Tree(4, 3);
    final int N = 32;
    for (int i = 1; i <= N; ++i) t.insert(Key(i), new Data(i));

    final StringBuilder s = new StringBuilder();
    s.append("Start\n"+t);
    for (int i = N; i > 0; --i)
     {t.delete(Key(i));
      ok(t.count(), i-1);
      s.append("Delete: "+i+"\n"+t);
     }
    ok(s, """
Start
                                           16                                                     |
        4        8           12                          20            24            28           |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
Delete: 32
                                           16                                                  |
        4        8           12                          20            24            28        |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31|
Delete: 31
                                           16                                               |
        4        8           12                          20            24            28     |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   17,18,19,20   21,22,23,24   25,26,27,28   29,30|
Delete: 30
                                           16                                            |
        4        8           12                          20            24            28  |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   17,18,19,20   21,22,23,24   25,26,27,28   29|
Delete: 29
                                           16                                       |
        4        8           12                          20            24           |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   17,18,19,20   21,22,23,24   25,26,27,28|
Delete: 28
                                           16                                    |
        4        8           12                          20            24        |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   17,18,19,20   21,22,23,24   25,26,27|
Delete: 27
                                           16                                 |
        4        8           12                          20            24     |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   17,18,19,20   21,22,23,24   25,26|
Delete: 26
                                           16                              |
        4        8           12                          20            24  |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   17,18,19,20   21,22,23,24   25|
Delete: 25
                                           16                         |
        4        8           12                          20           |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   17,18,19,20   21,22,23,24|
Delete: 24
                                           16                      |
        4        8           12                          20        |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   17,18,19,20   21,22,23|
Delete: 23
                                           16                   |
        4        8           12                          20     |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   17,18,19,20   21,22|
Delete: 22
                                           16                |
        4        8           12                          20  |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   17,18,19,20   21|
Delete: 21
                                           16           |
        4        8           12                         |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   17,18,19,20|
Delete: 20
                                           16        |
        4        8           12                      |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   17,18,19|
Delete: 19
                                           16     |
        4        8           12                   |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   17,18|
Delete: 18
                                           16  |
        4        8           12                |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   17|
Delete: 17
                                           16|
        4        8           12              |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   |
Delete: 16
                                        16|
        4        8           12           |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15   |
Delete: 15
                                     16|
        4        8           12        |
1,2,3,4  5,6,7,8  9,10,11,12   13,14   |
Delete: 14
                                  16|
        4        8           12     |
1,2,3,4  5,6,7,8  9,10,11,12   13   |
Delete: 13
        4        8           16|
1,2,3,4  5,6,7,8  9,10,11,12   |
Delete: 12
        4        8       |
1,2,3,4  5,6,7,8  9,10,11|
Delete: 11
        4        8    |
1,2,3,4  5,6,7,8  9,10|
Delete: 10
        4        8 |
1,2,3,4  5,6,7,8  9|
Delete: 9
        4       |
1,2,3,4  5,6,7,8|
Delete: 8
        4     |
1,2,3,4  5,6,7|
Delete: 7
        4   |
1,2,3,4  5,6|
Delete: 6
        4 |
1,2,3,4  5|
Delete: 5
1,2,3,4|
Delete: 4
1,2,3|
Delete: 3
1,2|
Delete: 2
1|
Delete: 1
|
""");
   }

  static void test_delete_random_32()
   {final Tree t = test_insert_random_32();
    final StringBuilder s = new StringBuilder();
    s.append("Start\n"+t);
    for (int i = 0; i < random_32.length; i++)
     {t.delete(Key(random_32[i]));
      ok(t.count(), random_32.length-i-1);
      s.append("Delete "+random_32[i]+"\n"+t);
     }
    ok(""+s, """
Start
                                        15                                       26                   |
        4      7          11                          19      21         24                    30     |
1,2,3,4  5,6,7  8,9,10,11   12,13,14,15   16,17,18,19   20,21   22,23,24   25,26   27,28,29,30   31,32|
Delete 12
                                     15                                       26                   |
        4      7          11                       19      21         24                    30     |
1,2,3,4  5,6,7  8,9,10,11   13,14,15   16,17,18,19   20,21   22,23,24   25,26   27,28,29,30   31,32|
Delete 3
                                   15                                       26                   |
      4      7          11                       19      21         24                    30     |
1,2,4  5,6,7  8,9,10,11   13,14,15   16,17,18,19   20,21   22,23,24   25,26   27,28,29,30   31,32|
Delete 27
                                   15                                       26                |
      4      7          11                       19      21         24                 30     |
1,2,4  5,6,7  8,9,10,11   13,14,15   16,17,18,19   20,21   22,23,24   25,26   28,29,30   31,32|
Delete 1
                                 15                                       26                |
    4      7          11                       19      21         24                 30     |
2,4  5,6,7  8,9,10,11   13,14,15   16,17,18,19   20,21   22,23,24   25,26   28,29,30   31,32|
Delete 23
                                 15                                  26                |
    4      7          11                       19      21                       30     |
2,4  5,6,7  8,9,10,11   13,14,15   16,17,18,19   20,21   22,24,25,26   28,29,30   31,32|
Delete 20
                                 15                               26                |
    4      7          11                       19   21                       30     |
2,4  5,6,7  8,9,10,11   13,14,15   16,17,18,19   21   22,24,25,26   28,29,30   31,32|
Delete 8
                               15                               26                |
    4      7        11                       19   21                       30     |
2,4  5,6,7  9,10,11   13,14,15   16,17,18,19   21   22,24,25,26   28,29,30   31,32|
Delete 18
                               15                                            |
    4      7        11                       21            26         30     |
2,4  5,6,7  9,10,11   13,14,15   16,17,19,21   22,24,25,26   28,29,30   31,32|
Delete 2
                            15                                            |
        7        11                       21            26         30     |
4,5,6,7  9,10,11   13,14,15   16,17,19,21   22,24,25,26   28,29,30   31,32|
Delete 31
                            15                                       |
        7        11                       21            26           |
4,5,6,7  9,10,11   13,14,15   16,17,19,21   22,24,25,26   28,29,30,32|
Delete 25
                            15                                    |
        7        11                       21         26           |
4,5,6,7  9,10,11   13,14,15   16,17,19,21   22,24,26   28,29,30,32|
Delete 16
                            15                                 |
        7        11                    21         26           |
4,5,6,7  9,10,11   13,14,15   17,19,21   22,24,26   28,29,30,32|
Delete 13
                         15                                 |
        7        11                 21         26           |
4,5,6,7  9,10,11   14,15   17,19,21   22,24,26   28,29,30,32|
Delete 32
                         15                              |
        7        11                 21         26        |
4,5,6,7  9,10,11   14,15   17,19,21   22,24,26   28,29,30|
Delete 11
                    15                              |
        7                      21         26        |
4,5,6,7  9,10,14,15   17,19,21   22,24,26   28,29,30|
Delete 21
                    15                           |
        7                   21         26        |
4,5,6,7  9,10,14,15   17,19   22,24,26   28,29,30|
Delete 5
                  15                           |
      7                   21         26        |
4,6,7  9,10,14,15   17,19   22,24,26   28,29,30|
Delete 24
      7           15            26        |
4,6,7  9,10,14,15   17,19,22,26   28,29,30|
Delete 4
    7           15            26        |
6,7  9,10,14,15   17,19,22,26   28,29,30|
Delete 10
    7        15            26        |
6,7  9,14,15   17,19,22,26   28,29,30|
Delete 26
    7        15         26        |
6,7  9,14,15   17,19,22   28,29,30|
Delete 30
    7        15         26     |
6,7  9,14,15   17,19,22   28,29|
Delete 9
          15         26     |
6,7,14,15   17,19,22   28,29|
Delete 6
        15         26     |
7,14,15   17,19,22   28,29|
Delete 29
        15           |
7,14,15   17,19,22,28|
Delete 17
        15        |
7,14,15   19,22,28|
Delete 28
        15     |
7,14,15   19,22|
Delete 15
7,14,19,22|
Delete 14
7,19,22|
Delete 19
7,22|
Delete 7
22|
Delete 22
|
""");
   }

  static void test_deep()
   {final Tree t = new Tree(2, 3);
    final int N = 256;
    for (int i = 1; i <= N; ++i) t.insert(Key(i), new Data(i));
    ok(t, """
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            128                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
                                                                                                                 32                                                                                                                              64                                                                                                                              96                                                                                                                                                                                                                                                                                                                                                         160                                                                                                                                                                             192                                                                                                                                                                             224                                                                                                                                                                            |
                   8                             16                              24                                                              40                              48                              56                                                              72                              80                              88                                                                     104                                         112                                         120                                                                                     136                                         144                                         152                                                                                     168                                         176                                         184                                                                                     200                                         208                                         216                                                                                     232                                         240                                         248                                        |
    2    4    6          10      12      14              18      20      22              26      28      30              34      36      38              42      44      46              50      52      54              58      60      62              66      68      70              74      76      78              82      84      86              90      92      94              98       100        102                   106        108        110                   114        116        118                   122        124        126                   130        132        134                   138        140        142                   146        148        150                   154        156        158                   162        164        166                   170        172        174                   178        180        182                   186        188        190                   194        196        198                   202        204        206                   210        212        214                   218        220        222                   226        228        230                   234        236        238                   242        244        246                   250        252        254       |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28   29,30   31,32   33,34   35,36   37,38   39,40   41,42   43,44   45,46   47,48   49,50   51,52   53,54   55,56   57,58   59,60   61,62   63,64   65,66   67,68   69,70   71,72   73,74   75,76   77,78   79,80   81,82   83,84   85,86   87,88   89,90   91,92   93,94   95,96   97,98   99,100    101,102    103,104    105,106    107,108    109,110    111,112    113,114    115,116    117,118    119,120    121,122    123,124    125,126    127,128    129,130    131,132    133,134    135,136    137,138    139,140    141,142    143,144    145,146    147,148    149,150    151,152    153,154    155,156    157,158    159,160    161,162    163,164    165,166    167,168    169,170    171,172    173,174    175,176    177,178    179,180    181,182    183,184    185,186    187,188    189,190    191,192    193,194    195,196    197,198    199,200    201,202    203,204    205,206    207,208    209,210    211,212    213,214    215,216    217,218    219,220    221,222    223,224    225,226    227,228    229,230    231,232    233,234    235,236    237,238    239,240    241,242    243,244    245,246    247,248    249,250    251,252    253,254    255,256|
""");
   }

  static void test_idi()
   {final int N = 32;
    final Tree t = new Tree(2,3,32);
    for (int i = 1; i <= N; ++i) {t.insert(Key(i), new Data(i)); t.freeCheck();}
    for (int i = 1; i <= N; ++i) {t.delete(Key(i));              t.freeCheck();}
    for (int i = 1; i <= N; ++i) {t.insert(Key(i), new Data(i)); t.freeCheck();}
    //stop(t.dump());
    ok(t.dump(), """
                                                                                     8                                                                                                   16                                                                                               24                                                                                   |
                                                                                    [5.0]                                                                                               [5.2]                                                                                            [5.4]                                                                                 |
                                                                                    (20, *, *)                                                                                          (13, *, *)                                                                                       (11, *, *){2}                                                                         |
                                                                                    (20, 19, null)                                                                                      (13, 19, null)                                                                                   (11, 19, null)                                                                        |
           2                       4                        6                                                 10                       12                       14                                               18                      20                       22                                             26                   28                   30                  |
          [20.0]                  [20.2]                   [20.4]                                            [13.0]                   [13.2]                   [13.4]                                           [11.0]                  [11.2]                   [11.4]                                         [2.0]                [2.2]                [2.4]                |
          (3, 5, 0)               (21, 5, 0)               (22, 5, 0){23}                                    (16, 5, 2)               (17, 5, 2)               (24, 5, 2){14}                                   (7, 5, 4)               (12, 5, 4)               (15, 5, 4){6}                                  (1, 5, *)            (8, 5, *)            (4, 5, *){9}         |
          (3, -1, null)           (21, -1, null)           (22, -1, null)                                    (16, -1, null)           (17, -1, null)           (24, -1, null)                                   (7, -1, null)           (12, -1, null)           (15, -1, null)                                 (1, 5, null)         (8, 5, null)         (4, 5, null)         |
1,2                    3,4                      5,6                      7,8                      9,10                     11,12                    13,14                    15,16                    17,18                  19,20                    21,22                    23,24                   25,26                27,28                29,30                31,32    |
(3, 20, 0)             (21, 20, 2)              (22, 20, 4)              (23, 20, *)              (16, 13, 0)              (17, 13, 2)              (24, 13, 4)              (14, 13, *)              (7, 11, 0)             (12, 11, 2)              (15, 11, 4)              (6, 11, *)              (1, 2, 0)            (8, 2, 2)            (4, 2, 4)            (9, 2, *)|
""");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_slots();
    test_emptyTree();
    test_compactLeafLeft();
    test_compactLeafRight();
    test_compactBranchLeft();
    test_compactBranchRight();
    test_duplicate_leaf();
    test_splitLeftLeafIntoRight();
    test_splitRightLeafIntoLeft();
    test_duplicate_branch();
    test_splitLeftBranchIntoRight();
    test_splitRightBranchIntoLeft();
    test_mergeLeafLeft();
    test_mergeLeafRight();
    test_mergeBranchLeft();
    test_mergeBranchRight();
    test_splitLeaf();
    test_splitBranch();
    test_insert2();
    test_insert();
    test_insert_reverse();
    test_insert_random();
    test_insert_random_32();
    test_delete();
    test_delete_descending();
    test_delete_random_32();
    test_deep();
    test_idi();
   }

  static void newTests()                                                        // Tests being worked on
   {oldTests();
   }

  public static void main(String[] args)                                        // Test if called as a program
   {try                                                                         // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {if (github_actions) oldTests(); else newTests();                          // Tests to run
      if (coverageAnalysis) coverageAnalysis(12);                               // Coverage analysis
      testSummary();                                                            // Summarize test results
      System.exit(testsFailed);
     }
    catch(Exception e)                                                          // Get a traceback in a format clickable in Geany
     {System.err.println(e);
      System.err.println(fullTraceBack(e));
      System.exit(1);
     }
   }
 }
