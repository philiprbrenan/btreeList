//----------------------------------------------------------------------------------------------------------------------
// Btree with stucks implemented as distributed sparse slots
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
// Optimize merges by merging into the most full node from the least full node
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;

class Tree extends Program                                                                                              // A tree that translates keys into values to be implemented as an application specific integrated circuit
 {final int           maxLeafSize;                                                                                      // The maximum number of entries in a leaf of the tree
  final int         maxBranchSize;                                                                                      // The maximum number of entries in a branch of the tree
  final int         rootFanLevels;                                                                                      // Number of levels from the root to fan out to make the launching of multiple parallel readers faster.
  final int            leafFanOut;                                                                                      // The amount of fan out in a leaf
  final int          branchFanOut;                                                                                      // The amount of fan out in a branch
  final BitSet          freeChain;                                                                                      // Nodes currently free
  final int         numberOfNodes;                                                                                      // Maximum number of leaves plus branches in this tree
  final int maximumNumberOfLevels;                                                                                      // Maximum number of levels in tree to prevent runaways while debugging
  final int              nodeSize;                                                                                      // The size of each node in the tree: a node must be able to hold a branch or a leaf
  final int      logTwoSizeOfNode;                                                                                      // LOg two of the size of a node
  final Memory.Ref       refNodes;                                                                                      // The nodes associated with this tree
  final Memory.Ref   refFreeChain;                                                                                      // The free chain for this tree
  final Memory.Ref       refCount;                                                                                      // The number of keys in this tree
  final Memory.Ref      refHeight;                                                                                      // The height of the tree
  final Build               build;                                                                                      // Memory containing the tree base followed by the leaves and branches of the tree
  final int   linesToPrintABranch = 4;                                                                                  // The number of lines required to print a branch
  final Memory          mergePath;                                                                                      // Memory for the steps taken along the merge path - each integer corresponds to the location of a branch in the path from the root to the leaf that should contain the key

//D1 Construction                                                                                                       // Construct and layout a tree

  final static class Build                                                                                              // Parameters describing a tree
   {boolean       immediate = true;                                                                                     // Immediate execution mode
    int          branchSize;                                                                                            // Size of a branch
    int            leafSize;                                                                                            // Size of a leaf
    int            nodeSize;                                                                                            // Size of a node: a leaf or a branch whichever is bigger. By using fixed size memory allocation we greatly simplify memory allocation - so it is worth adjusting the branch and leaf sizes to be as equal as possible.
    int    logTwoSizeOfNode;                                                                                            // Log two size of a node in the tree

    int         maxLeafSize =  4;
    int       maxBranchSize =  3;
    int       numberOfNodes = 99;
    int       rootFanLevels =  0;
    int          leafFanOut =  0;
    int        branchFanOut =  0;

    String             name;
    BitSet.Build  freeChain;
    Branch.Build     branch;
    Leaf  .Build       leaf;
    int unitsNeededForNodes;                                                                                            // Bytes needed for all the nodes
    int  unitsNeededForFree;                                                                                            // Bytes needed for free chain
    MemoryPositions memoryPositions;                                                                                    // Layout of memory

    Build     immediate (boolean Immediate    ) {immediate     = Immediate;     return this;}
    Build   maxLeafSize (int     MaxLeafSize  ) {maxLeafSize   = MaxLeafSize  ; return this;}
    Build maxBranchSize (int     MaxBranchSize) {maxBranchSize = MaxBranchSize; return this;}
    Build numberOfNodes (int     NumberOfNodes) {numberOfNodes = NumberOfNodes; return this;}
    Build rootFanLevels (int     Levels       ) {rootFanLevels = Levels;        return this;}
    Build    leafFanOut (int     LeafFanOut   ) {leafFanOut    = LeafFanOut;    return this;}
    Build  branchFanOut (int     BranchFanOut ) {branchFanOut  = BranchFanOut;  return this;}
    Build          name (String Name)           {name          = Name;          return this;}

    Program.Build build()                                                                                               // Describe the program used to execute the tree algorithm
     {final Program.Build p = new Program.Build();                                                                      // Description of containing program
      freeChain             = new BitSet .Build().bitSize(numberOfNodes); freeChain.build();                            // Size of free chain
      branch                = new Branch .Build().maxSize(maxBranchSize); branch   .build();                            // Size of a branch
      leaf                  = new Leaf   .Build().maxSize(maxLeafSize)  ; leaf     .build();                            // Size of a leaf
      leafSize              = leaf.size();
      branchSize            = branch.size();
      nodeSize              = nextPowerOfTwo(max(branchSize, leafSize));                                                // Next power of two to make it easy to find the node we are in for a given address and hence set locking
      logTwoSizeOfNode      = logTwo(nodeSize);                                                                         // Log two size of a node in the tree
      unitsNeededForNodes   = numberOfNodes * nodeSize;
      unitsNeededForFree    = freeChain.units();
      memoryPositions       = new MemoryPositions();

      if (name != null) p.name(name);                                                                                   // Optional name of program containing tree code
      p.memory(size(), new AddressToArea());                                                                            // Memory size and address to area lambda
      p.immediate(immediate);                                                                                           // Immediate or delayed execution mode
      p.checkAllLocksHaveBeenReleased();                                                                                // Always check that all locks have been freed at the end of execution
      return p;
     }

    class MemoryPositions                                                                                               // Layout of memory
     {final int posNodes     = 0;                                                                                       // A tree consists of nodes: leaves and branches. This field tells us which one we have
      final int posFreeChain = posNodes     + unitsNeededForNodes;                                                      // Free chain
      final int posCount     = posFreeChain + unitsNeededForFree;                                                       // Number of key/value pairs
      final int posHeight    = posCount     + 1;                                                                        // Height of tree
      final int size         = posHeight    + 1;                                                                        // Size of memory holding tree
     }

    int size () {return memoryPositions.size;}                                                                          // Bytes needed for the slots

    class AddressToArea extends Program.AddressToArea                                                                   // Translate addresses into areas in memory to be locked
     {final int logTwo;                                                                                                 // Lock number is normally the index of the node
      final int freeChain;                                                                                              // Lock number for free chain
      final int count;                                                                                                  // Lock number for count field
      final int height;                                                                                                 // Lock number for height field
      final int nodes;                                                                                                  // Lock number for height field

      AddressToArea()                                                                                                   // Overridable convert an address in memory to a lock index
       {logTwo    = logTwoSizeOfNode;                                                                                   // Lock number is normally the index of the node
        freeChain = memoryPositions.posFreeChain;                                                                       // Lock number for free chain
        count     = memoryPositions.posCount;                                                                           // Lock number for count field
        height    = memoryPositions.posHeight;                                                                          // Lock number for height field
        nodes     = numberOfNodes;                                                                                      // Lock number for height field
       }

      int addressToArea(int Address)                                                                                    // Overridable convert an address in memory to a lock index
       {if (Address < freeChain) return Address >>> logTwoSizeOfNode;                                                   // Lock number is normally the index of the node
        if (Address < count)     return numberOfNodes + 0;                                                              // Lock number for free chain
        if (Address < height)    return numberOfNodes + 1;                                                              // Lock number for count field
                                 return numberOfNodes + 2;                                                              // Lock number for height field
       }
     }
   }

  Tree(Build Build)                                                                                                     // Create the tree
   {super(Build.build());
    maxLeafSize      = Build.maxLeafSize;                                                                               // The maximum number of entries in a leaf
    maxBranchSize    = Build.maxBranchSize;                                                                             // The maximum number of entries in a branch
    numberOfNodes    = Build.numberOfNodes;                                                                             // The maximum number of leaves and branches combined
    maximumNumberOfLevels = logTwo(numberOfNodes);                                                                      // The maximum number of levels needed to step down through the tree because it is so well balanced
    rootFanLevels    = Build.rootFanLevels;                                                                             // Number of levels including the root to be subjected to root fan out
    leafFanOut       = min(Build.leafFanOut,   maxLeafSize);                                                            // Fan out at leaf if requested
    branchFanOut     = min(Build.branchFanOut, maxBranchSize);                                                          // Fan out at branch if requested

    final String  m  = "The maximum ";
    final String  m1 = m + "leaf size must be 2 or more, not: "   +maxLeafSize;
    final String  m2 = m + "branch size must be 3 or more, not: " +maxBranchSize;
    final String  m3 = m + "branch size must be odd, not: "       +maxBranchSize;

    final boolean b1 = maxLeafSize       <  2;                                                                          // Size checks
    final boolean b2 = maxBranchSize     <  3;
    final boolean b3 = maxBranchSize % 2 == 0;

    if (b1 && !b2 && !b3) stop(m1); else if (b1) say(m1);                                                               // Check parameters and describe any errors
    if (b2        && !b3) stop(m2); else if (b2) say(m2);
    if (b3              ) stop(m3);
    build            = Build;                                                                                           // Keep the build for future reference
    nodeSize         = build.nodeSize;                                                                                  // Size of a node in the tree
    logTwoSizeOfNode = logTwo(nodeSize);                                                                                // Size of a node in the tree
    final Memory.Ref unitMemoryRef = mainMemory.new Ref(0);                                                             // Memory used by tree
    refNodes         = unitMemoryRef.step(build.memoryPositions.posNodes);                                              // Memory for nodes
    refFreeChain     = unitMemoryRef.step(build.memoryPositions.posFreeChain);                                          // Memory for free chain
    refCount         = unitMemoryRef.step(build.memoryPositions.posCount);                                              // Memory for key count
    refHeight        = unitMemoryRef.step(build.memoryPositions.posHeight);                                             // Memory for height of tree

    mergePath        = new Memory(mnl2(), "mergePath", new Memory.ReadWrite(), null);                                   // Memory for the steps taken along the merge path - each integer corresponds to the location of a branch in the path from the root to the leaf that should contain the key

    lockFreeChain();                                                                                                    // Lock free chain
    freeChain  = new BitSet(build.freeChain.memory(refFreeChain).parent(this));                                         // Memory for free chain
    for (int i = 0, N = numberOfNodes; i < N; ++i) freeChain.set(new Int(i));                                           // Initial free chain with root as an allocated leaf. Each active leaf or branch resides in a node of the tree allocated from the free chain. Using a single node size greatly simplifies memory management which is crucial in long running processes like database systems.
    unlockFreeChain();                                                                                                  // Unlock free chain

    lockHeight(); refHeight.putInt(One); unlockHeight();                                                                // Current height of the tree is the one leaf root

    leaf().unlockLeaf();                                                                                                // Initialize the root as a leaf and unlock it
    treeCode();
   }

  void     treeCode () {}                                                                                               // Override to apply code to the tree

  int   maxLeafSize () {return maxLeafSize;}                                                                            // Maximum size of a leaf
  int maxBranchSize () {return maxBranchSize;}                                                                          // Maximum size of a branch
  int numberOfNodes () {return numberOfNodes;}                                                                          // Maximum number of nodes in tree
  int           mnl () {return maximumNumberOfLevels;}                                                                  // Maximum number of levels
  int          mnl2 () {return maximumNumberOfLevels << 1;}                                                             // Twice the maximum number of levels to make sure that we really do have enough space to describe a path through the tree

  void         lockRoot () { getLock(root());}                                                                          // Lock root
  void       unlockRoot () {freeLock(root());}                                                                          // Unlock root
  Bit      rootIsLocked () {return isLocked(Zero);}                                                                     // Test whether the root is locked
  Bit    rootIsUnLocked () {return isLocked(Zero).flip();}                                                              // Test whether the root is unlocked

  void   lockFreeChain () { getLock(numberOfNodes + 0);}                                                                // Lock free chain
  void       lockCount () { getLock(numberOfNodes + 1);}                                                                // Lock free count of number of entries in tree
  void      lockHeight () { getLock(numberOfNodes + 2);}                                                                // Lock free height of tree
  void unlockFreeChain () {freeLock(numberOfNodes + 0);}                                                                // Unlock free chain
  void     unlockCount () {freeLock(numberOfNodes + 1);}                                                                // Unlock count of entries in tree
  void    unlockHeight () {freeLock(numberOfNodes + 2);}                                                                // Unlock height of tree

  Int      allocate ()                                                                                                  // Allocate a leaf or a branch using the first free node on the free chain
   {lockFreeChain();
    final Bint A = freeChain.firstOne();                                                                                // First element on free chain
    A.elseStop("No more leaves or branches available for allocation");                                                  // Out of memory check
    final Int a = new Int("index").set(A);                                                                              // First element on free chain
    freeChain.clear(a);                                                                                                 // Remove indexed node from free chain
    unlockFreeChain();                                                                                                  // Unlock the free chain
    return a;                                                                                                           // Return index of allocated node
   }

  void free (Locatable Free)                                                                                            // Free a leaf or a branch and invalidate its contents
   {final Bint a = Free.getLocation();
    lockFreeChain();
    freeChain.set(a.i());
    unlockFreeChain();
   }

  Bit isAllocated (Int Node)                                                                                            // Check whether a node is allocated
   {lockFreeChain(); final Bit f = freeChain.getBit(Node).Flip(); unlockFreeChain(); return f;
   }

  Int nodeAddress  (Int Node)                                                                                           // Convert an index to a byte address of node in memory
   {if (immediate())
     {if (Node.gt(new Int(numberOfNodes)).b()) stop("Node too big:",                                     Node);         // Check in range
      lockFreeChain();
      if (freeChain.getBit(Node).b()) stop("Attempting to access a branch or leaf that has been freed:", Node);         // Complain if the node has been freed and not reallocated
      unlockFreeChain();
     }
    return Node.Mul(nodeSize);                                                                                          // Actual byte position of this node in memory
   }

  enum BranchOrLeaf                                                                                                     // Branch or leaf
   {leaf(1), branch(2);
    private final int value;
    BranchOrLeaf (int Value) {value = Value;}
    int value ()             {return value;}
   }

  Int         root () {return Zero;}                                                                                    // The root is always at node zero
  Bit   isRootLeaf () {return checkType(root(), BranchOrLeaf.leaf  );}                                                  // Whether the root is a leaf
  Bit isRootBranch () {return checkType(root(), BranchOrLeaf.branch);}                                                  // Whether the root is a branch

  Bit checkType (Int Node, BranchOrLeaf Type)                                                                           // Check the type of a node
   {final Int a = nodeAddress(Node);
    final Int t = mainMemory.getInt(a);
    final Bit r = new Bit(false);
    new If (t.eq(Type.value())) {void Then() {r.set(true);}};
    return r;
   }

  void setType (Int Node, BranchOrLeaf Type)                                                                            // Set the type of a node
   {final Int a = nodeAddress(Node);
    mainMemory.putInt(a, new Int(Type.value()));
   }

  Bit isBranch (Int Node) {return checkType(Node, BranchOrLeaf.branch);}                                                // Whether the indexed node a branch
  Bit   isLeaf (Int Node) {return checkType(Node, BranchOrLeaf.leaf  );}                                                // Whether the indexed node a leaf

  Leaf leaf (Int Node) {return leaf(Node, true);}                                                                       // Index an existing leaf in memory            confirming that it really is a leaf
  Leaf leaf (Int Node, boolean Check)                                                                                   // Index an existing leaf in memory optionally confirming that it really is a leaf
   {getLock(Node);                                                                                                      // Lock the leaf if necessary
    if (immediate() && Check && !isLeaf(Node).b()) stop("Not a leaf:", Node);                                           // Check the location actually holds a leaf
    final Memory.Ref r = mainMemory.new Ref(nodeAddress(Node));                                                         // Address leaf
    final Tree    tree = this;                                                                                          // Current tree

    return new Leaf(build.leaf.parent(program()).memory(r).at(Node))                                                    // Return locked leaf based at the indexed address
     {Bit full()                                                                                                        // Whether the branch should be regarded as full or not
       {final Bit F = super.full();                                                                                     // Normal result of full
        if (leafFanOut > 0)                                                                                             // Leaf fan out has been requested
         {final Int h = tree.height();                                                                                  // Make leaves close to the root small to reduce queuing for the root branch when operating multiple parallel readers. This does waste siomemmeory, but not much over the entire tree whilst the reduced queuing at the root is expected to be a significant improvement
          final Bit f = new Bit("Full", false);                                                                         // Whether the leaf is full under the criterion of root fan out
          new If (h.le(rootFanLevels))                                                                                  // Close enough to the root
           {void Then() {f.set(count().ge(leafFanOut));}                                                                // The amount of fanout requested for leaves close to the root
            void Else() {f.set(F);}                                                                                     // For other levels use the normal definition of full
           };
          return f;
         }
        return F;                                                                                                       // Revert to normal definition of full - i.e. when the branch is actually full
       }

      Int mergeLimit ()                                                                                                 // Maximum size of merge
       {final Int m = new Int("maxSize").set(maxSize());                                                                // Full size
        if (leafFanOut > 0)                                                                                             // Leaf fan out has been requested
         {final Int h = tree.height();                                                                                  // Make leaves close to the root small to reduce queuing for the root branch when operating multiple parallel readers. This does waste siomemmeory, but not much over the entire tree whilst the reduced queuing at the root is expected to be a significant improvement
          new If (h.le(rootFanLevels)) {void Then() {m.min(leafFanOut);}};                                              // Close enough to the root to  leaf request fan out
         }
        return m;                                                                                                       // Revert to normal definition of full - i.e. when the branch is actually full
       }
     };
   }

  Leaf makeLeaf (Int Node)                                                                                              // Make a leaf from the specified node given the locked status of the node
   {final Leaf l = leaf(Node, false);
    l.clear();
    setType(Node, BranchOrLeaf.leaf);
    return l;                                                                                                           // Return locked leaf
   }
  Leaf     leaf ()         {return makeLeaf(allocate());}                                                               // Create and initialize a branch in memory and return its index

  Branch branch (Int Node) {return branch(Node, true);}                                                                 // Index an existing branch in memory            confirming that it really is a branch
  Branch branch (Int Node, boolean Check)                                                                               // Index an existing branch in memory optionally confirming that it really is a branch
   {getLock(Node);                                                                                                      // Lock the branch
    if (immediate() && Check && !isBranch(Node).b()) stop("Not a branch:", Node);                                       // Check the location actually holds a branch
    final Memory.Ref   r = mainMemory.new Ref(nodeAddress(Node));                                                       // Address branch
    final Tree      tree = this;                                                                                        // Current tree

    return new Branch(build.branch.parent(program()).memory(r).at(Node))                                                // Base branch at the indexed address
     {Bit full()                                                                                                        // Whether the branch should be regarded as full or not
       {final Bit F = super.full();                                                                                     // Normal result of full
        if (rootFanLevels > 0)                                                                                          // Root fan out has been requested
         {final Int d = tree.height().Dec().sub(level());                                                               // Make branches close to the root small to reduce queuing for the root branch when operating multiple parallel readers. This does waste siomemmeory, but not much over the entire tree whilst the reduced queuing at the root is expected to be a significant improvement
          final Bit f = new Bit("Full", false);                                                                         // Whether the branch is full under the criterion of root fan out
          new If (d.le(rootFanLevels))                                                                                  // Close enough to the root
           {void Then() {f.set(count().gt(branchFanOut));}                                                              // The amount of fan out requested for branches close to the root
            void Else() {f.set(F);}                                                                                     // For other levels use the normal definition of full
           };
          return f;
         }
        return F;                                                                                                       // Revert to normal definition of full - i.e. when the branch is actually full
       }

      Int mergeLimit ()                                                                                                 // Maximum size of merge
       {final Int m = new Int("maxSize").set(maxSize());                                                                // Full size
        if (branchFanOut > 0)                                                                                           // Branch fan out has been requested
         {final Int d = tree.height().Dec().sub(level());                                                               // Make branches close to the root small to reduce queuing for the root branch when operating multiple parallel readers. This does waste siomemmeory, but not much over the entire tree whilst the reduced queuing at the root is expected to be a significant improvement
          new If (d.le(rootFanLevels)) {void Then() {m.min(branchFanOut);}};                                            // Close enough to the root to branch request fan out
         }
        return m;                                                                                                       // Revert to normal definition of full - i.e. when the branch is actually full
       }
     };
   }

  Branch makeBranch (Int Node)                                                                                          // Make a branch from the specified node
   {final Branch b = branch(Node, false);
    b.clear();
    setType(Node, BranchOrLeaf.branch);
    return b;
   }

  Branch branch () {return makeBranch(allocate());}                                                                     // Create and initialize a branch in memory and return its index

  Int     count () {lockCount();  final Int c = refCount .getInt();                            unlockCount(); return c;}// Number of keys in tree
  Int  countInc () {lockCount();  final Int c = refCount .getInt(); refCount .putInt(c.inc()); unlockCount(); return c;}// Increment the key count
  Int  countDec () {lockCount();  final Int c = refCount .getInt(); refCount .putInt(c.dec()); unlockCount(); return c;}// Decrement the key count

  Int    height () {lockHeight(); final Int c = refHeight.getInt();                            unlockHeight();return c;}// Height of tree
  Int heightInc () {lockHeight(); final Int c = refHeight.getInt(); refHeight.putInt(c.inc()); unlockHeight();return c;}// Increment the height of the tree
  Int heightDec () {lockHeight(); final Int c = refHeight.getInt(); refHeight.putInt(c.dec()); unlockHeight();return c;}// Decrement the height of the tree

  StringBuilder dumpTree ()                                                                                             // Dump the tree
   {subStart("Tree.dumpTree");
    final StringBuilder s = new StringBuilder();

    lockFreeChain();
    final Int           f = new Int(numberOfNodes()).Sub(freeChain.count());
    unlockFreeChain();

    new I()                                                                                                             // Dump the tree statistics
     {void a()
       {s.setLength(0);
        s.append(f("Tree memory dump\n"));
        s.append(f("Leaf   size   : %4d\n", build.leafSize));
        s.append(f("Branch size   : %4d\n", build.branchSize));
        s.append(f("Node   size   : %4d\n", nodeSize));
        s.append(f("MaxLeafSize   : %4d\n", maxLeafSize));
        s.append(f("MaxBranchSize : %4d\n", maxBranchSize));
        s.append(f("NumberOfNodes : %4d\n", numberOfNodes));
        s.append(f("Allocations   : %4d\n", f.i()));
        s.append(f("Number of Keys: %4d\n", refCount .getInt(0)));
        s.append(f("Height        : %4d\n", refHeight.getInt(0)));
       }
      boolean trace () {return false;}
     };

    new ForCount(min(numberOfNodes, 20))                                                                                // Dump the leaves and branches
     {void body(Int Index)
       {new If(isAllocated(Index))
         {void Then()
           {final boolean locked = isLocked(Index).b();
             new If (isLeaf(Index))
             {void Then() {final StringBuilder t = leaf  (Index).print(); new I() {void a() {s.append(t);} boolean trace() {return false;}};}
              void Else() {final StringBuilder t = branch(Index).print(); new I() {void a() {s.append(t);} boolean trace() {return false;}};}
             };
            if (!locked) freeLock(Index);
           }
         };
       }
     };
    subFinish();
    return s;
   }

//D1 Private Methods                                                                                                    // Private methods supporting Find, insert and delete

  final class FirstEntry                                                                                                // Locate the first entry in the tree and return its key and data
   {final  Bint leaf = new Bint ("leaf index");                                                                         // First leaf
    final  Bint  key = new Bint ("key");                                                                                // First key in first leaf
    final  Int  data = new  Int ("data");                                                                               // Data associated with first key

    FirstEntry  ()                                                                                                      // Locate the first entry in the tree
     {subStart("Tree.FirstEntry");
      final Int p = root().dup();                                                                                       // Start at root
      getLock(p);                                                                                                       // Lock the root so no-one can modify it while we are reading it

      new For(mnl())                                                                                                    // Step down from branch to branch
       {void body(Int Index, Bit Continue)
         {new If (isLeaf(p))                                                                                            // On a leaf
           {void Then()
             {leaf.set(p);                                                                                              // Record leaf
              final Leaf  l = leaf(p);                                                                                  // Details of leaf
              final Slots s = l.slots;                                                                                  // Slots for leaf
              final Bint  f = s.firstSlot();                                                                            // First used slot is the first entry
              new If (f)
               {void Then()
                 {key .set(       s.getSlotToKeyValue(f.i()));                                                          // Key from first slot
                  data.set(l.data(s.getSlotToKeyIndex(f.i())));                                                         // Data associated with key
                 }
               };
             }
            void Else()                                                                                                 // On a branch
             {final Branch b = branch(p);                                                                               // Branch
              final Slots  s = b.slots;                                                                                 // Slots for branch
              final Bint   f = s.firstSlot();                                                                           // First used slot is the first entry
              final Int    d = new Int("down");
              new If (f)                                                                                                // Body of branch is not empty
               {void Then() {d.set(b.data(s.getSlotToKeyIndex(f.i())));}                                                // Step to first child
                void Else() {d.set(b.top());}                                                                           // Step through top as there are no entries in the body of the branch
               };
              getLock(d); freeLock(p); p.set(d);                                                                        // Lock child, unlock parent, step down
              Continue.set();                                                                                           // Continue traverse towards a leaf
             }
           };
         }
       };

      if (immediate() && !leaf.valid().b()) stop("Find fell off the end of tree after this many searches:", mnl());
      subFinish("Tree.FirstEntry");
     }

    public String toString ()                                                                                           // Print the find results
     {immediateModeCheck();
      subStart("Tree.FirstEntry.toString");

      final StringBuilder s = new StringBuilder("First Entry: ");
      if (leaf.valid().b()) s.append(f("Leaf : %2d, key : %2d, data : %2d", leaf.i(), key.i(), data.i()));
      s.append("\n");
      subFinish();
      return ""+s;
     }
   }

  final class LastEntry                                                                                                 // Locate the last entry in the tree and return its key and data
   {final  Bint leaf = new Bint ("leaf index");                                                                         // Last leaf
    final  Bint  key = new Bint ("key");                                                                                // Last key in last leaf
    final  Int  data = new  Int ("data");                                                                               // Data associated with last key

    LastEntry  ()                                                                                                       // Locate the last entry in the tree
     {subStart("Tree.LastEntry");
      final Int p = root().dup();                                                                                       // Start at root
      getLock(p);                                                                                                       // Lock the root so no-one can modify it while we are reading it

      new For(mnl())                                                                                                    // Step down from branch to branch
       {void body(Int Index, Bit Continue)
         {new If (isLeaf(p))                                                                                            // On a leaf
           {void Then()
             {leaf.set(p);                                                                                              // Record leaf
              final Leaf  l = leaf(p);                                                                                  // Details of leaf
              final Slots s = l.slots;                                                                                  // Slots for leaf
              final Bint  f = s.lastSlot();                                                                             // Last used slot is the last entry
              new If (f)
               {void Then()
                 {key .set(       s.getSlotToKeyValue(f.i()));                                                          // Key from last slot
                  data.set(l.data(s.getSlotToKeyIndex(f.i())));                                                         // Data associated with key
                 }
               };
             }
            void Else()                                                                                                 // On a branch
             {final Branch b = branch(p);                                                                               // Branch
              final Slots  s = b.slots;                                                                                 // Slots for branch
              final Bint   f = s.firstSlot();                                                                           // Last used slot is the last entry
              final Int    d = b.top();                                                                                 // Step through top as there are no entries in the body of the branch
              getLock(d); freeLock(p); p.set(d);                                                                        // Lock child, unlock parent, step down
              Continue.set();                                                                                           // Continue traverse towards a leaf
             }
           };
         }
       };

      if (immediate && !leaf.valid().b()) stop("Find fell off the end of tree after this many searches:", mnl());
      subFinish();
     }

    public String toString ()                                                                                           // Print the find results
     {immediateModeCheck();
      subStart("Tree.LastEntry.toString");

      final StringBuilder s = new StringBuilder("Last Entry: ");
      if (leaf.valid().b()) s.append(f("Leaf : %2d, key : %2d, data : %2d", leaf.i(), key.i(), data.i()));
      s.append("\n");
      subFinish();
      return ""+s;
     }
   }

  final class NextEntry                                                                                                 // Locate the next entry greater than the given key and return the located key and data if present
   {final  Bint leaf = new Bint ("leaf index");                                                                         // Last leaf
    final  Bint  key = new Bint ("key");                                                                                // Last key in last leaf
    final  Int  data = new  Int ("data");                                                                               // Data associated with last key

    NextEntry  (Int Key)                                                                                                // Locate the last entry in the tree
     {subStart("Tree.NextEntry");
      getLock(root());
      final FindLeafForNextKey f = new FindLeafForNextKey(Key);                                                         // Start at root
      final Leaf           l = leaf(f.leaf);
      final Slots          s = l.slots;
      final Slots.FindGe   n = s.new FindGe(f.key);
      leaf.set(f.leaf);
      new If (n.slot)
       {void Then()
         {key .set(       s.getSlotToKeyValue(n.slot.i()));                                                             // Key from last slot
          data.set(l.data(s.getSlotToKeyIndex(n.slot.i())));                                                            // Data associated with key
         }
        void Else() {l.unlockLeaf();}                                                                                   // Free the lock on the leaf as no next key was found
       };
      subFinish();
     }

    public String toString ()                                                                                           // Print the find results
     {immediateModeCheck();
      subStart("Tree.NextEntry.toString");

      final StringBuilder s = new StringBuilder("Next Entry: ");
      if (leaf.valid().b())
       {s.append(f("Leaf : %2d", leaf.ii()));
        if (key.valid().b()) s.append(f(", key : %2d, data : %2d", key.ii(), data.i()));
       }
      else s.append(" no leaf found");
      s.append("\n");
      subFinish();
      return ""+s;
     }
   }

  abstract class Traverse                                                                                               // Traverse the tree processing each key, data pair in ascending order with the  option to terminate processing after any key
   {Traverse ()
     {final FirstEntry f = new FirstEntry();                                                                            // Start with the first key
      new If (f.key)
       {void Then()
         {final Int k = new Int("Key", f.key.i());
          final Bit c = new Bit("Continue").clear();
          traverse(new Int(0), f.key.i(), f.data, c);

          new If (c)                                                                                                    // Continue if caller requests more than thr first key
           {void Then()
             {new Until()                                                                                               // Each following key as long as the caller shows interest
               {void body(Int Index, Bit Continue)
                 {final NextEntry n = new NextEntry(k);
                  new If (n.key)
                   {void Then() {traverse(Index.Inc(), n.key.i(), n.data, Continue); k.set(n.key.i());}
                   };
                 }
               };
             }
           };
         }
       };
     }
    abstract void traverse(Int Index, Int Key, Int Data, Bit Continue);                                                 // Override to process each key, data pair
   }

  class FindLeafForKey                                                                                                  // Find the leaf that should contain the specified key.  The leaf is returned with a read lock on it to prevent any modifications of the leaf before the caller has had a chance to process it.  Consequently the caller must release the read lock promptly
   {final  Int key  = new Int ("key");                                                                                  // Search key
    final  Int leaf = new Int("leaf index");                                                                            // Leaf that should contain the key. There will always be such a leaf as the tree starts off as a empty leaf. The leaf will be locked

    FindLeafForKey (Int Key)                                                                                            // Find the specified key in a leaf in the tree
     {subStart("Tree."+title());
      final Int p = root().dup();                                                                                       // Start at root
      key.set(Key);                                                                                                     // Record the search key for posterity

      new For(mnl())                                                                                                    // Step down from branch to branch
       {void body(Int Index, Bit Continue)
         {new If (isLeaf(p))                                                                                            // On a leaf
           {void Then()
             {leaf.set(p);                                                                                              // Show the key and matching leaf, free the read lock after the key and data have been read from the leaf
             }
            void Else()                                                                                                 // On a branch
             {final Branch.StepDown d = branch(p).new StepDown(key);                                                    // Step down details
              freeLock(p);
              p.set(d.node);                                                                                            // Step down to next level
              Continue.set();                                                                                           // Continue search
             }
           };
         }
       };

      //if (immediate && !leaf.valid().b()) stop("Find fell off the end of tree after this many searches:", mnl());     // Leaf is now an "Int" because a leaf will always be found
      subFinish();
     }

    public String toString ()                                                                                           // Print the find results
     {immediateModeCheck();
      subStart("Tree."+title()+".toString");
      final StringBuilder s = new StringBuilder(title()+" : "+printKey()+", "+leaf.valid().b()+"\n");
      s.append(leaf(leaf).print());
      subFinish();
      return ""+s;
     }

    String printKey () {return ""+key;}                                                                                 // Key to print
    String    title () {return "FindLeafForKey";}                                                                       // Title
   }

  final class FindLeafForNextKey extends FindLeafForKey                                                                 // Find the leaf that should contain the next key after the specified key. Choosing the next key as the next key plus one works because the key in each branch slot is always equal to the largest key in its sub tree, so stepping one beyond this forces the search into the next sub tree.  Any key value can be used, we ae not dependent on stepping from actual key to actual key.  Searching for a key smaller than any key in the tree finds the first leaf although "FirstEntry" is faster. Searching for a key  larger than any key finds the last leaf although  "LastEntry" is more efficient. Finding a leyThe leaf is returned with a read lock on it to prevent any modifications of the leaf before the caller has had a chance to process it.  Consequently the caller must release the read lock promptly
   {FindLeafForNextKey (Int Key) {super(Key.Inc());}                                                                    // Find the leaf containing the smallest next possible key
    String printKey () {return ""+(key.i()-1);}                                                                         // Key to print
    String    title () {return "FindLeafForNextKey";}                                                                   // Title
   }

  final class FindBranch                                                                                                // Find a branch that contains a specified key
   {final  Int    key = new  Int("key");                                                                                // Search key
    final Bint branch = new Bint("branch index");                                                                       // Index of the branch containing the key if the key was found
    final  Int   slot = new  Int("slot");                                                                               // Slot in a valid branch reference containing the key

    FindBranch (Int Key)                                                                                                // Find the specified key in a branch in the tree
     {subStart("Tree.findBranch");
      final Int p = root().dup();                                                                                       // Start at root
      getLock(p);                                                                                                       // Lock the root so no-one can modify it while we are reading it
      key.set(Key);

      new For(mnl())                                                                                                    // Step down from branch to branch
       {void body(Int Index, Bit Continue)
         {new If (isBranch(p))                                                                                          // On a branch
           {void Then()
             {final Branch.StepDown d = branch(p).new StepDown(Key);                                                    // Step down details
              new If (d.eqKey)
               {void Then() {branch.set(p); slot.set(d.slot);}                                                          // Found the matching key so terminate the search and hold a read lock on the branch so found
                void Else() {getLock(d.node); freeLock(p); p.set(d.node); Continue.set();}                              // Step down to the next branch
               };
             }
            void Else() {freeLock(p);}                                                                                  // Stepped down to a leaf so release the read lock on the parent branch because a branch containing the key was not found
           };
         }
       };

      if (immediate && !branch.b().v) stop("Find fell off the end of tree after this many searches:", mnl());
      subFinish();
     }

    public String toString ()                                                                                           // Print the find results
     {subStart("Tree.FindBranch.toString");
      final StringBuilder s = new StringBuilder();
      s.append("Find branch key: "+key+", valid: "+branch.b().i);
      if (branch.b().i) s.append(", branch:"+branch.ii()+", slot: "+slot);
      s.append("\n");
      subFinish();
      return ""+s;
     }
   }

  final class MaximumKey                                                                                                // Maximum key under a specified branch
   {final  Int branch = new Int("branch index");                                                                        // Index of the branch containing the key if the key was found
    final Bint maxKey = new Bint("maximum key");                                                                        // Maximum key

    MaximumKey (Int Branch)                                                                                             // Maximum key under a specified branch
     {subStart("Tree.MaximumKey");
      branch.set(Branch);
      final Int p = branch.dup();                                                                                       // Start at branch
      getLock(p);                                                                                                       // Lock the root so no-one can modify it while we are reading it

      new For(mnl())                                                                                                    // Step down from branch to branch
       {void body(Int Index, Bit Continue)
         {new If (isBranch(p))                                                                                          // On a branch
           {void Then()
             {final Int t = branch(p).top();                                                                            // Step through top towards maximum element
              getLock(t); freeLock(p); p.set(t); Continue.set();                                                        // Step down through top to the next branch
             };
            void Else()                                                                                                 // We have arrived at a leaf - find the maximum element
             {final Slots s = leaf(p).slots;                                                                            // Slots
              final Bint  i = s.lastSlot();                                                                             // Index of slot referencing last key
              new If (i) {void Then() {maxKey.set(s.getSlotToKeyValue(i.i()));}};                                       // Last key in leaf if there is one
              freeLock(p);
             }
           };
         }
       };
      subFinish();
     }

    public String toString ()                                                                                           // Print maximum key if it exists
     {return maxKey.valid().b() ? f("Branch: %2d,  maximum key: %2d\n", branch.i(), maxKey.ii()) :
                                  f("Branch: %2d is empty\n",           branch.i());
     }
   }

  final class Path                                                                                                      // Record the path from the root to the leaf that should contain a key
   {final Int          key = new  Int("key");                                                                           // Search key
    final Int         leaf = new  Int("leaf");                                                                          // Leaf that should contain the key
    final Int        start = new  Int("start").set(1);                                                                  // Current start position for entries in the path.  We leave some space at the front to insert the details of a split root branch if necessary
    final Int         step = new  Int("step");                                                                          // Current step in the path
    final Int lastLeftStep = new  Int("last left step");                                                                // The node we arrived at when we last stepped left
    final Bint       split = new Bint("split");                                                                         // The splitting branch is the uppermost branch directly connected to the leaf by intervening full branches which will all have to be split from the top down to permit the splitting of a full leaf
    final Bint        over = new Bint("over");                                                                          // The splitting branch is the uppermost branch directly connected to the leaf by intervening full branches which will all have to be split from the top down to permit the splitting of a full leaf
    final Bit        eqKey = new  Bit("equal key");                                                                     // A branch has a key that is equal to the search key
    final Memory.Ref  path = mergePath.new Ref(0);                                                                      // Branches along path

    Path(Int Key)                                                                                                       // Locate the path to the leaf that should contain the key and lock all nodes from the root to the leaf
     {subStart("Tree.Path");
      final Int p = root().dup(); getLock(p);                                                                           // Start at root
      final Bit valid = new Bit(false);                                                                                 // Whether a leaf was reached

      key .set(Key);                                                                                                    // Record search key
      step.set(start);                                                                                                  // Start recording entries in the path at this point beginning with the root
      mergePath.clear();                                                                                                // Clear the path
      eqKey    .clear();                                                                                                // Clear the equal key indicator to show that so far we have not encountered a branch entry containing the search key
      lastLeftStep.set(p);

      new For(mnl())                                                                                                    // Step down from branch to branch
       {void body(Int Index, Bit Continue)
         {new If (isLeaf(p))                                                                                            // On a leaf
           {void Then()
             {valid.set();                                                                                              // Reached a leaf
              leaf.set(p);                                                                                              // End the path on a leaf
             }
            void Else()                                                                                                 // On a branch
             {final Branch.StepDown d = branch(p).new StepDown(key);                                                    // Step down
              new If (d.eqKey) {void Then() {eqKey.set();}};                                                            // Stepped through a key equal to the search key
              path.putInt(step, p);                                                                                     // Record index of node along path
              step.inc();                                                                                               // Next step along the path
              p.set(d.node);                                                                                            // Step down
              Continue.set();                                                                                           // Continue search
             }
           };
         }
       };
      if (immediate() && !valid.b()) stop("Find fell off the end of tree after this many searches:", mnl());
      subFinish();
     }

    void splitPoint ()                                                                                                  // Locate the split point: the uppermost full branch directly connected to the leaf by intervening full branches which will have to be split from the top back down to the parent of the leaf to permit the splitting of a full leaf. The branches that are to be split will remain locked, the remainder will be unlocked.  The locked branchs will be unlocked after splitting down or merging up has been completed.
     {subStart("Tree.splitPoint");

      final Int u = new Int();                                                                                          // Location of split point
      new For(start, step)                                                                                              // Steap along the path from the current start position
       {void body(Int Index, Bit Continue)                                                                              // Step up from leaf to root
         {final Int p = step.Sub(Index).add(start).dec();                                                               // Position on path
          final Int b = path.getInt(p);                                                                                 // Branch index
          new If (branch(b).full())                                                                                     // On a full branch
           {void Then() {split.set(p); Continue.set();}                                                                 // Highest full branch so far that will need splitting
            void Else()                                                                                                 // The branch above the highest branch that needs splitting
             {over.set(p);                                                                                              // Branch over highest splitting branch
              new ForCount(start, p) {void body(Int Index) {freeLock(path.getInt(Index));}};                            // Unlock any branches above the over branch
             }
           };
         }
       };
      subFinish();
     }

    void splitDown ()                                                                                                   // Split from the splitting top most splitting branch if such a branch exists
     {subStart("Tree.splitDown");

      new If (split)                                                                                                    // The top most branch to split
       {void Then()
         {new If (path.getInt(split.i()).eq(0))                                                                         // Split the root branch
           {void Then()
             {final SplitRootBranch s = new SplitRootBranch();                                                          // Split the root branch
              final Branch b = s.root;                                                                                  // Root branch
              new If (key.le(s.splitKey))                                                                               // Update the path if the key to be inserted is less then the splitting key as the path will now go through the split out left branch
               {void Then() {path.putInt(start, s.left .at.i()); s.right.unlockBranch();}                               // Step through left node which is known to exist because we just inserted it. Unlock the right branch as we are going through the left branch
                void Else() {path.putInt(start, s.right.at.i()); s.left .unlockBranch();}                               // Divert through first element of root now that it has been split. Free the left branch as we are going through the right branch
               };

              start.dec(); path.putInt(start, s.root.at.i());                                                           // Add the root back to the path after addressing a path element in which to record the root
              split.set(split.i().inc());                                                                               // Step up over split root which no longer needs splitting
             }
           };
          new ForCount(split.i(), step)                                                                                 // Split full branches which are not the root in descending order so that there is always enough room in the parent branch to accept the splitting key
           {void body(Int Index)
             {final Branch r = branch(path.getInt(Index.Dec()));                                                        // Parent branch whose child should be split
              final Branch c = branch(path.getInt(Index)      );                                                        // Child branch that should be split
              final Branch.StepDown d = r.new StepDown(key    );                                                        // Step down
              final Branch l = branch();                                                                                // Create a new branch to split into
              final Int   sk = c.splitLeft(l);                                                                          // Splitting key

              new If (d.slot)                                                                                           // Stepped through top
               {void Then() {r.insert(sk, l.getLocation().i(), d.slot);}                                                // Insert split out branch just below the key in this slot
                void Else() {r.insert(sk, l.getLocation().i(), new Bint());}                                            // Insert split out branch as last element of parent branch body
               };

              new If (key.le(sk))                                                                                       // Update the path if the key to be inserted is less then the splitting key as the path will now go through the split out left branch
               {void Then() {path.putInt(Index, l.getLocation().i()); r.unlockBranch();}                                // Update path with diversion through left branch
                void Else() {                                         l.unlockBranch();}                                // Diversion continues through right branch so we can unlock the left branch
               };
             }
           };
         }
       };
      subFinish();
     }

    void mergeUp ()                                                                                                     // Merge up from the leaf to the splitting point
     {subStart("Tree.mergeUp");

      final Leaf l = leaf(leaf);
      new If (l.empty())                                                                                                // Leaf is now empty and so can be removed along with its empty parents from the tree
       {void Then()
         {final Int q = new Int("q").set(leaf);                                                                         // Last child
          new For(mnl())                                                                                                // Back up along path
           {void body(Int Index, Bit Continue)
             {final Int    p = step.Dec();                                                                              // Parent branch index in path
              final Int    B = path.getInt(p);                                                                          // Parent branch node index
              final Branch b = branch(B);                                                                               // Parent branch
              new If (b.top().eq(q))                                                                                    // Child is the top of its parent
               {void Then()
                 {new If (b.empty())                                                                                    // Parent is now empty and so can also be removed
                   {void Then()
                     {q.set(B);                                                                                         // Child is branch made empty by removal of top child
                      Continue.set();                                                                                   // Continue up until we get to the top or find a parent with an upper moist child in its body
                     }
                    void Else()
                     {final Bint U = b.lastSlot();                                                                      // Last slot in body of parent
                      final Int  u = b.data(b.keyIndex(U.i()));                                                         // Uppermost child in body of parent
                      b.top(u);                                                                                         // Make upper most child top
                      b.deleteSlotAndKey(U.i());                                                                        // Remove uppermost child in body of parent now it has  been made top
                     }
                   };
                  step.dec();                                                                                           // Path is now shorter
                 }
               };
             }
           };
         }
       };

      new For(start, step)                                                                                              // Start at branch immediately above the leaf and work upwards
       {void body(Int Index, Bit Continue)
         {final Int             i = step.Sub(Index).add(start).dec();                                                   // Index of parent branch that contains the split siblings
          final Branch          p = branch(path.getInt(i));                                                             // Parent branch containing split children
          final Branch.StepDown d = p.new StepDown(key   );                                                             // Locate key slot
          mergeLeftLeft(  p, d.slot);
          mergeRightRight(p, d.slot);
          mergeLeft(      p, d.slot);
          mergeRight(     p, d.slot);
          new If (over) {void Then() {Continue.set(over.notValid().or(i.ge(over.i())));} void Else() {Continue.set();}};
         }
       };

      freeAllLocks();                                                                                                   // Free any remaining lock as the tree is now in a valid state
      squashRoot();                                                                                                     // Reduce the height of the tree if possible
      subFinish();
     }

    String printPath ()                                                                                                 // Print the path
     {immediateModeCheck();
      subStart("Tree.print.path");
      final StringBuilder s = new StringBuilder("Path:\n");

      s.append("key      : "+key  +"\n");                                                                               // Search key
      s.append("leaf     : "+leaf +"\n");                                                                               // Leaf that should contain the key
      s.append("start    : "+start+"\n");                                                                               // Current start position for entries in the path.  We leave some space at the front to insert the details of a split root branch if necessary
      s.append("step     : "+step +"\n");                                                                               // Current step in the path
      s.append("split    : "+split+"\n");                                                                               // The splitting branch is the uppermost branch directly connected to the leaf by intervening full branches which will all have to be split from the top down to permit the splitting of a full leaf
      s.append("over     : "+over +"\n");                                                                               // The splitting branch is the uppermost branch directly connected to the leaf by intervening full branches which will all have to be split from the top down to permit the splitting of a full leaf
      s.append("equal key: "+eqKey+"\n");                                                                               // A branch has a key that is equal to the search key
      s.append("last left: "+lastLeftStep+"\n");                                                                        // The node we stepped into on the last left turn

      s.append("Step  Node\n");                                                                                         // Steps along the path
      new ForCount(start, step)
       {void body(Int Index)
         {s.append(f("%4d  %4d\n", Index.i(), path.getInt(Index).i()));
         }
       };

      s.append(" "+leaf+" "+split+"\n");
      subFinish();
      return ""+s;
     }
   }

  void squashRoot ()                                                                                                    // Squash the root down if possible, Assume the entire tree is unlocked at this point as it is  in a valid, if not ldeal, state
   {if (immediate()) checkAllLocksHaveBeenReleased();                                                                   // Confirm that all the locks have been released
    final Branch R = branch(root());                                                                                    // Its a branch because this method gets called after splitting a leaf to make room for a new key,data pair to be inserted

    new If (R.empty())                                                                                                  // Reduce the height of the tree if the body of the root is now empty
     {void Then()
       {final Int t = R.top();                                                                                          // Top
        getLock(t);
        new If (isLeaf(t))                                                                                              // Root has leaves for children
         {void Then()
           {final Leaf L = makeLeaf(R.getLocation().i());                                                               // Make the root into a leaf
            final Leaf l = leaf(t);                                                                                     // Top as a leaf
            L.copy(l);                                                                                                  // Copy top into root decreasing height of tree
            l.unlockLeaf();                                                                                             // Unlock top
            free(l);                                                                                                    // Free top as no longer needed
           }
          void Else()                                                                                                   // Root has branches for children
           {final Branch b = branch(t);                                                                                 // Top as a branch
            R.copy(b);                                                                                                  // Copy top into root
            b.unlockBranch();                                                                                           // Unlock top
            free(b);                                                                                                    // Free top as no longer needed
           }
         };
        heightDec();                                                                                                    // Decrease the height of the tree
       }
     };
    freeLock(root());                                                                                                   // Free the root
   }

//D1 Public Methods                                                                                                     // Public methods for find, insert, delete etc.

  public Bint find (Int Key)                                                                                            // Find the data associated with the specified key in the tree
   {subStart("Tree.find");
    lockRoot();
    final FindLeafForKey l = new FindLeafForKey(Key);                                                                   // Find leaf that should contain the key
    final Bint        data = new Bint();

    final Leaf       L = leaf(l.leaf);                                                                                  // Load leaf
    final Slots.Find f = L.findKey(Key);                                                                                // Search for key in root
    new If (f.equal)
     {void Then()                                                                                                       // Key exists in leaf
       {final Int k = L.keyIndex(f.slot.i());                                                                           // Key slot
        data.set(L.data(k));                                                                                            // Data associated with key
       }
     };
    L.unlockLeaf();                                                                                                     // Safe to free the read lock on the leaf because we have the data associated with the key

    subFinish();
    return data;                                                                                                        // Will be set to invalid unless the key was found in which case it will contain the data associated with the key
   }

  public void insert (Int Key, Int Data)                                                                                // Insert a key, data pair into the tree
   {subStart("Tree.insert");
    lockRoot();
    new If (isRootLeaf())
     {void Then()                                                                                                       // New right hand leaf
       {final Leaf R = leaf(root());
        final Slots.Find f = R.findKey(Key);                                                                            // Perhaps the key is already present in the leaf root tree
        new If (f.equal)                                                                                                // Key exists in leaf root
         {void Then()
           {final Int p = R.keyIndex(f.slot.i());                                                                       // Position of key in leaf root slots
            R.data(p, Data);                                                                                            // Update data associated with key
            unlockRoot();
           }
          void Else()                                                                                                   // The key does not exist in the root leaf
           {new If (R.full())                                                                                           // Is the leaf full
             {void Then()                                                                                               // Split the root leaf to make room for  the new key
               {final Leaf l = leaf(), r = leaf();                                                                      // Child leaves of root branch
                l.copy(R);                                                                                              // Duplicate the root
                final Int   sk = l.splitRight(r);                                                                       // Split the root leaf in two
                final Branch b = makeBranch(root());                                                                    // Make the root into a branch
                b.insert(sk, l.getLocation().i());                                                                      // Insert the left leaf
                b.top(r.getLocation().i());                                                                             // The right leaf becomes top of the root branch
                new If (Key.le(sk)) {void Then() {l.insert(Key, Data);} void Else() {r.insert(Key, Data);}};            // Insert left or right leaf depending on key versus splitting key
                b.level(One);                                                                                           // Level of a root branch made by splitting a leaf
                heightInc();                                                                                            // The tree is now one level higher as we have split the root leaf
                l.unlockLeaf(); r.unlockLeaf();
               }
              void Else()                                                                                               // Root is a non full leaf that does not contain the key
               {R.insert(Key, Data);                                                                                    // Insert in non full leaf that does not contain the key
               }
             };
            countInc();                                                                                                 // Count inserted key
            unlockRoot();
           }
         };
       }
      void Else()                                                                                                       // The root is a branch
       {final FindLeafForKey f = new FindLeafForKey(Key);                                                               // Find the leaf for the key
        final Int            i = f.leaf;                                                                                // Index of leaf
        final Leaf           l = leaf(i);                                                                               // Leaf that should contain the key
        final Slots.Find     F = l.findKey(Key);                                                                        // Perhaps the key is already present in the leaf
        new If (F.equal)                                                                                                // Key exists in full leaf
         {void Then()
           {final Int p = l.keyIndex(F.slot.i());                                                                       // Position of key in leaf slots
            l.data(p, Data);                                                                                            // Update data  associated with key
            freeLock(i);                                                                                                // Release the lock on the leaf obtained by find leaf
           }
          void Else()                                                                                                   // Key is not present in the leaf
           {new If (l.full())                                                                                           // The target leaf is full
             {void Then() {freeLock(i); insertFullLeaf(Key, Data);}                                                     // Insert into a tree known to have a branch at the root and probably still has a full target leaf for the key
              void Else() {l.insert(Key, Data); freeLock(i);}                                                           // Insert a new key into a non full leaf
             };
            countInc();                                                                                                 // Count inserted key
           }
         };
       }
     };
    subFinish();
   }

  private void insertFullLeaf(Int Key, Int Data)                                                                        // Insert a key, data pair into the tree when tis known that the root is a branch and the target leaf is full and the key does not exist in the leaf
   {subStart("Tree.insertFullLeaf");
    final Path p = new Path(Key);                                                                                       // Path from root to full leaf
    p.splitPoint();                                                                                                     // The lowest branch in the tree that is full and has a non full parent
    p.splitDown();                                                                                                      // Split the branches down to the leaf as they are all full

    final Int    L = p.step.Dec();                                                                                      // Last step along path
    final Branch P = branch(p.path.getInt(L));                                                                          // Parent branch of full leaf
    final Leaf   r = leaf(p.leaf            );                                                                          // The full leaf into which the key should be inserted
    final Leaf   l = leaf();                                                                                            // New leaf
    final Int   sk = r.splitLeft(l);                                                                                    // Split the full leaf into the new leaf

    final Branch.StepDown d = P.new StepDown(Key);                                                                      // Step down from parent of last full branch in path

    final Bint s = new Bint();                                                                                          // Record the slot through which we are stepping from the parent of the last full branch in the path
    new If (d.slot.valid())                                                                                             // If the leaf was reached by stepping through top then insert the new left leaf high
     {void Then()
       {s.copy(d.slot);                                                                                                 // Insert new left leaf below the key in the indicated slot
       }
     };

    P.insert(sk, l.getLocation().i(), s);                                                                               // Insert new left leaf below the key in the indicated slot

    new If (Key.le(sk))                                                                                                 // Insert the key in the left leaf if it less than the splitting key
     {void Then() {l.insert(Key, Data);}                                                                                // Insert key in the left leaf and unlock the right leaf as it is no longer part of the path to the key
      void Else() {r.insert(Key, Data);}                                                                                // Insert key in the right leaf and unlock the left leaf as it is no longer part of the path to the key
     };

    p.mergeUp();                                                                                                        // Merge nodes on either side of the path going up from the leaf to towards the root
    subFinish();
   }

  public Int delete (Int Key)                                                                                           // Delete a key from the tree and return the associated data if the key was present in the tree
   {subStart("Tree.delete");
    final Int data = new Int();                                                                                         // Data associated with key if the key is present in the tree
    new If (isRootLeaf())
     {void Then()                                                                                                       // The root is a leaf
       {final Leaf       R = leaf(root());                                                                              // Load root
        final Slots.Find f = R.findKey(Key);                                                                            // Search for key in root
        new If (f.equal)
         {void Then()                                                                                                   // Key exists in leaf
           {data.set(R.data(R.keyIndex(f.slot.i())));                                                                   // Data associated with key
            R.deleteSlot(f.slot.i());                                                                                   // Remove key from root leaf comprising tree
            countDec();                                                                                                 // Count deleted key
           }
         };
        R.unlockLeaf();                                                                                                 // Unlock root
       }
      void Else()                                                                                                       // The root is a branch
       {final Path       p = new Path(Key);                                                                             // Path to leaf that should contain key
        final Leaf       l = leaf(p.leaf);                                                                              // Containing leaf
        final Slots.Find f = l.findKey(Key);                                                                            // Search for key in root
        new If (f.equal)
         {void Then()                                                                                                   // Key exists in leaf
           {data.set(l.data(l.keyIndex(f.slot.i())));                                                                   // Data associated with key
            l.deleteSlot(f.slot.i());                                                                                   // Remove key from leaf in tree tree
            p.mergeUp();                                                                                                // Merge leaf and nodes above
            countDec();                                                                                                 // Count deleted key
            new If (p.eqKey)                                                                                            // If the key being deleted was present in a branch we should find it and replace it with the new maximum key under the key being deleted to maintain the invariant that the key in the branch is equal to the maximum key in the tree below
             {void Then()
               {final FindBranch F = new FindBranch(Key);                                                               // Find the branch containing the key if it is still in the tree - it might have been merged out
                new If (F.branch)
                 {void Then()
                   {final Branch          b = branch(F.branch.i());                                                     // Branch containing search key
                    final Branch.StepDown d = b.new StepDown(Key);                                                      // Step down through key to sub tree referenced by the search key
                    final MaximumKey      m = new MaximumKey(d.node);                                                   // Maximum key in sub tree becomes the new key for this slot

                    new If (m.maxKey)                                                                                   // Maximum was found
                     {void Then()
                       {final Slots       s = b.slots;                                                                  // Slots to be updated in branch being updated
                        final Int         i = s.getSlotToKeyIndex(F.slot);                                              // Index of slot holding key
                        s.setSlotAndKey(F.slot, i, m.maxKey.i());                                                       // Replace the existing key with the maximum of its sub tree in order to maintain the variant that the branch key is equal to the largest key in its sub tree
                       }
                      void Else()                                                                                       // No maximum key
                       {new If (d.slot)
                         {void Then()
                           {b.deleteSlotAndKey(d.slot.i());                                                             // There is no maximum key in the sub tree so the tree must be empty so we can delete it
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
    subFinish();
    return data;                                                                                                        // Data associated with key if valid else no such key
   }

//D1 Split and Merge                                                                                                    // Split and merge nodes in the tree

//D2 Split                                                                                                              // Split nodes in the tree to make the tree wider

  class SplitRootBranch                                                                                                 // Split the root branch of the tree
   {final Branch root;                                                                                                  // The root branch
    final Branch left;                                                                                                  // The left branch of the split
    final Branch right;                                                                                                 // The left branch of the split
    final Int    splitKey;                                                                                              // The key at which the branch was split

    SplitRootBranch ()                                                                                                  // Split the root assuming that it is a branch
     {subStart("Tree.splitRootBranch");

      final Branch R = root = branch(root());                                                                           // The root
      if (immediate() && isRootLeaf()   .b()) stop("Cannot split the root as a branch because it is not a branch");     // Check that it is a branch
      if (immediate() && R.full().Flip().b()) stop("Cannot split the root because it is not full");                     // Check that the root is full

      final Branch l = left  = branch();                                                                                // New left branch
      final Branch r = right = branch();                                                                                // New right branch
      l.copy(R);                                                                                                        // Copy the root into the left branch

      final Int    k = splitKey = l.splitRight(r);                                                                      // Splitting key
      R.clear();                                                                                                        // Clear the root
      makeBranch(R.getLocation().i());                                                                                  // Mark the root as a branch

      R.insertEmpty(k, l.getLocation().i());                                                                            // Insert the left branch below the splitting key
      R.top(r.getLocation().i());                                                                                       // Insert right as top of root

      R.refLevel.putInt(l.refLevel.getInt().inc());                                                                     // Level of root is no one more than that of the branches below
      lockHeight(); refHeight.putInt(refHeight.getInt().inc()); unlockHeight();                                         // Increase height of tree

      //R.unlockBranch(); l.unlockBranch(); r.unlockBranch();                                                           // Unlock branches

      subFinish();
     }
   }

//D2 Merge                                                                                                              // Merge nodes in the tree to make the tree narrower

  void mergeLeftLeafIntoRightSibling (Branch Parent, Int LeftSlot, Leaf Left, Leaf Right, boolean Unlock)               // Merge the specified left leaf sibling into its right sibling if possible.  The left sibling is specified by the index of its slot in the specified parent, the right by a leaf description
   {subStart("Tree.mergeLeftLeafIntoRightSibling");
    final Branch P = Parent;
    new If (Right.mergeLeft(Left))                                                                                      // Successfully merged
     {void Then()
       {P.deleteSlot(LeftSlot);                                                                                         // The left sibling can now be unlocked and freed
        Left.unlockLeaf(); if (Unlock) Right.unlockLeaf();
        free(Left);
       }
      void Else()
       {Left.unlockLeaf(); if (Unlock) Right.unlockLeaf();
       }
     };
    subFinish();
   }

  void mergeLeftBranchIntoRightSibling (Branch Parent, Int LeftSlot, Branch Left, Branch Right, boolean Unlock)         // Merge the specified left branch sibling into its right sibling if possible separating them with the specified splitting key.  The left sibling is specified by the index of its slot in the specified parent, the right by a leaf description
   {subStart("Tree.mergeLeftBranchIntoRightSibling");
    final Branch P = Parent;
    final Int    k = P.keyValue(LeftSlot);                                                                              // The parent key for the left sibling
    new If (Right.mergeLeft(Left, k))                                                                                   // Successfully merged
     {void Then()                                                                                                       // The left sibling can now be freed
       {P.deleteSlot(LeftSlot);                                                                                         // Remove from parent
        Left.unlockBranch(); if (Unlock) Right.unlockBranch();
        free(Left);
       }
      void Else()
       {Left.unlockBranch(); if (Unlock) Right.unlockBranch();
       }
     };
    subFinish();
   }

  void mergeLeftIntoRightSibling (Branch Parent, Bint LeftSlot, Bint RightSlot, Int Left, Int Right, boolean Unlock)    // Merge the specified left sibling into its right sibling if possible.  The left sibling is specified by the index of its slot in the specified parent
   {subStart("Tree.mergeLeftIntoRightSibling");
    final Branch P = Parent;
    new If (isLeaf(P.top()))                                                                                            // Parent has leaves for children
     {void Then()                                                                                                       // Merge leaves
       {final Leaf l = leaf(Left );
        final Leaf r = leaf(Right);
        mergeLeftLeafIntoRightSibling(P, LeftSlot.i(), l, r, Unlock);
       }
      void Else()                                                                                                       // Merge branches
       {final Branch l = branch(Left );
        final Branch r = branch(Right);
        mergeLeftBranchIntoRightSibling(P, LeftSlot.i(), l, r, Unlock);
       }
     };
    subFinish();
   }

//D3 Merge Left                                                                                                         // Merge single and double left

  Int  stepDownAt (Branch Parent, Int Slot) {return Parent.data(Parent.keyIndex(Slot));}                                // Step down through a slot
  Int stepDownTop (Branch Parent)           {return Parent.top();}                                                      // Step down through top

  void mergeLeft (Branch Parent, Bint Pos)                                                                              // Merge into the specified sibling, referenced as a slot, from its left hand sibling and remove the left hand sibling if this is possible. The specified position is the slot number of the key relative to which to merge. If the specified position is invalid top is assumed.  Ensure that both nodes in the merge are locked
   {subStart("Tree.mergeLeft");
    final Branch P = Parent;                                                                                            // Parent containing siblings
    final Bint   L = new Bint();                                                                                        // Left child slot

    new If (Pos)                                                                                                        // Merging relative to top
     {void Then() {L.copy(P.prevSlot(Pos.i()));}                                                                        // There might be a previous sibling to the left from a child in the body of the parent
      void Else() {L.copy(P.lastSlot());}                                                                               // There might be a child in the body of the parent to merge with top
     };

    new If (L)                                                                                                          // A left child exists
     {void Then()
       {final Int l = stepDownAt(Parent, L.i());                                                                        // Index of left  node
        getLock(l);                                                                                                     // Lock left as not on already locked path, method "mergeLeftIntoRightSibling" will
        final Int r = new Int();
        new If (Pos)
         {void Then() {r.set(stepDownAt (Parent, Pos.i()));}                                                            // Siblings entirely within body of parent
          void Else() {r.set(stepDownTop(Parent));}                                                                     // Right node of merge is top of parent
         };
        mergeLeftIntoRightSibling(Parent, L, Pos, l, r, false);                                                         // Merge
       }
     };

    subFinish();
   }

  void mergeLeftLeft (Branch Parent, Bint Pos)                                                                          // Merge into the left hand sibling of the specified sibling from the left hand sibling of the left hand sibling of the specified sibling if this is possible. The specified position is the slot number of the key relative to which to merge. If the specified position is invalid top is assumed.  Ensure that both nodes in the merge are locked
   {subStart("mergeLeftLeft");
    final Branch P = Parent;                                                                                            // Parent containing siblings
    final Bint   R = new Bint();                                                                                        // Right child of merge

    new If (Pos)                                                                                                        // Merging relative to top
     {void Then() {R.copy(P.prevSlot(Pos.i()));}                                                                        // Merge entirely within body of parent
      void Else() {R.copy(P.lastSlot());}                                                                               // Left once from top
     };

    new If (R)                                                                                                          // There is a right child for the merge
     {void Then()
       {final Bint L = P.prevSlot(R.i());                                                                               // Left of right child
        new If (L)                                                                                                      // There is a left position
         {void Then()
           {final Int l = stepDownAt(Parent, L.i());                                                                    // Index of left child for merge
            final Int r = stepDownAt(Parent, R.i());                                                                    // Index of right child for merge
            getLock(l); getLock(r);                                                                                     // Both nodes are off the already locked path so they will need to be locked
            mergeLeftIntoRightSibling(Parent, L, R, l, r, true);                                                        // Merge
           }
         };
       }
     };
    subFinish();
   }

//D3 Merge Right                                                                                                        // Merge single and double right

  void mergeRight (Branch Parent, Bint Pos)                                                                             // Merge the specified sibling into its right hand sibling if this is possible. The specified position is the slot number of the key relative to which to merge. Ensure that both nodes in the merge are locked
   {subStart("Tree.mergeRight");
    final Branch P = Parent;                                                                                            // Parent containing siblings
    final Bint   L = Pos;
    new If (L)                                                                                                          // Lock right node of merge
     {void Then()                                                                                                       // Not top
       {final  Int l = stepDownAt(Parent, L.i());                                                                       // Index of left child for merge
        final Bint R = P.nextSlot(L.i());                                                                               // Next right sibling
        final Int  r = new Int("r");
        new If (R)
         {void Then() {r.set(stepDownAt(Parent, R.i()));}                                                               // Right sibling in body of parent is the right node of the merge
          void Else() {r.set(stepDownTop(Parent));}                                                                     // Top of parent is the right node for the merge
         };
        getLock(r);                                                                                                     // Lock the right node as it is off the already locked path
        mergeLeftIntoRightSibling(Parent, L, R, l, r, true);                                                            // Merge freeing right because we have completed the merge cycle at this level
       }
      void Else() {freeLock(Parent.top());}                                                                             // Top has to be freed as the last merge at this level and we want to clear the locks on this level
     };
    subFinish();
   }

  void mergeRightRight (Branch Parent, Bint Pos)                                                                        // Merge the right hand sibling of the specified sibling with the right hand sibling of the right hand sibling if this is possible. The specified position is the slot number of the key relative to which to merge. Ensure that both nodes in the merge are locked
   {subStart("Tree.mergeRightRight");
    final Bint L = new Bint();                                                                                          // Left child of merge

    new If (Pos)                                                                                                        // Not on top so there is something on the right
     {void Then()
       {L.copy(Parent.nextSlot(Pos.i()));                                                                               // Right once to get left child of merge
        new If (L)                                                                                                      // Left hand node of merge
         {void Then()                                                                                                   // Left hand node is not top so we can find the right hand node of the merge
           {final Int  l = stepDownAt(Parent, L.i());                                                                   // Index of left hand node for merge
            final Bint R = Parent.nextSlot(L.i());                                                                      // Next right sibling
            final Int  r = new Int("r");                                                                                // Index of right hand node of merge
            new If (R)
             {void Then() {r.set(stepDownAt(Parent, R.i()));}                                                           // Right hand node is a sibling in the body of the parent
              void Else() {r.set(stepDownTop(Parent));}                                                                 // Right hand node is top of parent
             };
            getLock(l); getLock(r);                                                                                     // Lock the nodes to be merged as they are off the already locked path
            mergeLeftIntoRightSibling(Parent, L, R, l, r, true);                                                        // Merge
           }
         };
       }
     };
    subFinish();
   }

//D2 Traverse the tree                                                                                                  // Traverse the tree in order
//                                                         16                                                                |
//                                                         (0)                                                               |
//                                                         [9,2]                                                             |
//        4             8                12                                20               24              28               |
//        (9,0,2)       (9,0,2)          (9,0,2)                           (6,0)            (6,0)           (6,0)            |
//        [3,0]         [4,2]            [7,4]                             [10,0]           [5,2]           [12,4]           |
// 1,2,3,4       5,6,7,8       9,10,11,12       13,14,15,16     17,18,19,20      21,22,23,24     25,26,27,28      29,30,31,32|
// (3,9,0)       (4,9,2)       (7,9,4)          (8,9)           (10,6,0)         (5,6,2)         (12,6,4)         (2,6)      |

  class TraverseTree                                                                                                    // Traverse the tree in order by maintaining a stack of outstanding actions
   {Slots slots(int Index, int Keys)                                                                                    // Slots for a node by index - the slots are always located starting at the second memory unit of the node
     {final int p = Index * build.nodeSize+1;
      return new Slots(new Slots.Build().numberOfKeys(Keys).memory(refNodes.step(p)).parent(program()));
     }

    boolean isLeaf (int Index)                                                                                          // Whether the indexed node is a leaf or not
     {final int p = Index * build.nodeSize;
      final int rootType = refNodes.getInt(p);
      return rootType == BranchOrLeaf.leaf.value;
     }

    void traverse (int Index, int Parent, int Depth)                                                                    // Traverse the branch at the indicated index
     {final boolean locked = isLocked(Index);                                                                           // Check whether the current branch is locked
      if (!locked) getLock(Index);                                                                                      // Lock the node if not already locked
      final Slots      s = slots(Index, maxBranchSize);                                                                 // Slots for branch
      final Memory.Ref N = refNodes.step(Index*build.nodeSize + 1 + s.build.size());                                    // Array of child nodes
      final int        t = refNodes.step(Index*build.nodeSize + build.branch.memoryPositions.posTop  ).getInt(0);       // Top reference
      final int        v = refNodes.step(Index*build.nodeSize + build.branch.memoryPositions.posLevel).getInt(0);       // Level of branch
      int           keys = 0;                                                                                           // Number of keys on branch
      if (isLeaf(t))
       {for(int i = 0; i < maxBranchSize*2; ++i)                                                                        // Each leaf
         {if (s.getSlotToKeysInUse(i))
           {final int k = s.getSlotToKeyValue(i);
            final int n = s.getSlotToKeyIndex(i);
            final int l = N.getInt(n);
            pLeaf(l, Depth+1, Index, i);
            branchSlot(keys++, Index, Depth, v, Parent, i, k, l, locked);                                               // Print the connection back to the parent node.
           }
         }
        branchTop(keys, t, Depth, locked);
        pLeaf(t, Depth+1, Index, -1);
       }
      else
       {for(int i = 0; i < maxBranchSize*2; ++i)                                                                        // Each sub branch
         {if (s.getSlotToKeysInUse(i))                                                                                  // Slot in use
           {final int k = s.getSlotToKeyValue(i);                                                                       // Key in slot
            final int n = s.getSlotToKeyIndex(i);                                                                       // Index of data associated with key
            final int c = N.getInt(n);                                                                                  // Child branch or leaf
            if (Depth <= maximumNumberOfLevels) traverse(c, Index, Depth+1);                                            // Terminate large traverses perhaps produced in error

            branchSlot(keys++, Index, Depth, v, Parent, i, k, c, locked);
           }
         }
        branchTop(keys, t, Depth, locked);
        if (Depth <= maximumNumberOfLevels) traverse (t, Index, Depth+1);
       }
      if (!locked) freeLock(Index);                                                                                     // Unlock the branch if it was not originally locked
     }

    TraverseTree ()                                                                                                     // Traverse the tree visiting each leaf and branch in order
     {immediateModeCheck();
      boolean isLeaf;                                                                                                   // Whether the root is a leaf
      if (isLocked(0))   isLeaf = isLeaf(0);                                                                            // Root already locked so we can check its status immediately
      else {getLock(0); isLeaf = isLeaf(0); freeLock(0);}                                                               // Root not locked so lock it so it can be read, then unlock it to restore the status quo ante

      if (isLeaf) pLeaf    (0, 0, 0, 0);
      else        traverse (0, 0, 0);
     }

    void pLeaf (int Index, int Depth, int Parent, int ParentSlot)                                                       // Process a leaf
     {final boolean locked = isLocked(Index);                                                                           // Check whether the current leaf is locked
      if (!locked) getLock(Index);                                                                                      // Temporarily lock the node if not already locked so we can access it and thus print it
      final Slots         slots = slots(Index, maxLeafSize);
      final Stack<Integer> keys = new Stack<>();
      for(int i = 0; i < maxLeafSize*2; ++i) if (slots.getSlotToKeysInUse(i)) keys.push(slots.getSlotToKeyValue(i));
      leaf(Index, Depth, Parent, ParentSlot, keys, locked);                                                             // Free the temporary lock we acquired to print this leaf
      if (!locked) freeLock(Index);                                                                                     // Unlock the leaf if it was not originally locked
     }

    void leaf (      int Index, int Depth, int Parent, int Slot, Stack<Integer> Keys, boolean Locked)                   // Print a leaf
     {say("LLLL", "Index", Index, "Depth", Depth, "Parent", Parent, "Slot", Slot, "Keys", Keys, slots(Index, maxLeafSize), "Locked", Locked);
     }

    void branchSlot (int K, int Index, int Depth, int Level, int Parent, int Slot, int Key, int Child, boolean Locked)  // Process branch slot by printing the tree to the left of the slot and then the slot
     {say("BBBB", "Key", K, "Index", Index, "Depth", Depth, "Level", Level, "Parent", Parent, "Slot", Slot, "Key",  Key, "Child", Child, slots(Index, maxBranchSize), "Locked", Locked);
     }

    void branchTop (int Keys, int Index, int Depth, boolean Locked)                                                     // Process branch top by printing its sub tree to the right
     {say("TTTT", "Keys", Keys,  "Index", Index, "Depth", Depth, "Locked", Locked);
     }
   }

  void check (StringBuilder A, String B) {Test.ok(""+A, B);}

//D2 Print
//                                                          16  br slot key                                                 |
//                                                          (0) br index                                                    |
//                                                          [9,2]2 child index, parent slot, level                          |
//         4             8                12                                20              24              28              |
//         (9,0,2)       (9,0,2)          (9,0,2)                           (6,0)           (6,0)           (6,0)           |
//         [12,0]1       [5,2]1           [10,4]1                           [7,0]1          [4,2]1          [3,4]1          |
// 1,2,3,4        5,6,7,8       9,10,11,12       13,14,15,16     17,18,19,20     21,22,23,24     25,26,27,28     29,30,31,32|
// (12,9,0)       (5,9,2)       (10,9,4)         (8,9)           (7,6,0)         (4,6,2)         (3,6,4)         (2,6)      |

  final class Print                                                                                                     // Print the tree
   {final Stack<StringBuilder> P = new Stack<>();

    Print (boolean Context)                                                                                             // Print the tree optionally supplying the context of each branch and leaf
     {subStart("Tree.Print");

      new TraverseTree()
       {@Override void leaf(int Index, int Depth, int Parent, int Slot, Stack<Integer> Keys, boolean Locked)            // Process a leaf
         {final StringJoiner k = new StringJoiner(",", Locked ? "{" : "", Locked ? "}" : "");
          for(Integer i: Keys) k.add(""+i);
          final int d = Depth * linesToPrintABranch;                                                                    // Line in output
          if (Context)                                                                                                  // Print relationships with surrounding nodes
           {pad(d+2);                                                                                                   // Pad the output area so that all the lines have the same length
            P.elementAt(d).append(""+k);                                                                                // Write first line
            if (Depth > 0)                                                                                              // Parent details if not root
             {final StringBuilder t = P.elementAt(d+1);
              if (Slot == -1) t.append("("+Index+","+Parent+")"); else t.append("("+Index+","+Parent+","+Slot+")");     // Format second line
             }
           }
          else                                                                                                          // Keys without connections to surrounding nodes
           {pad(d+1);
            P.elementAt(d).append(""+k);
           }
         }

        @Override void branchSlot                                                                                       // Print keys of branch and optionally the details of the parent and the children of this branch
         (int K, int Index, int Depth, int Level, int Parent, int Slot, int Key, int Child, boolean Locked)
         {final int d = Depth * linesToPrintABranch;
          if (Context)                                                                                                  // Print relationships with surrounding nodes
           {pad(d+3);                                                                                                   // Pad the output area so that all the lines have the same length
            if (K == 0 && Locked) P.elementAt(d).append("{");                                                           // Show start of lock
            P.elementAt(d).append(f("%04d", Key));                                                                      // Write key into output area
            if (Depth == 0) P.elementAt(d+1).append("("+Index+","+Slot+")");                                            // Format second line for a root
            else P.elementAt(d+1).append("("+Index+","+Parent+","+Slot+")");                                            // Format second line for a non root branch showing the parent of the branch and the slot in the parent this branch came from
            P.elementAt(d+3).append("["+Child+","+Slot+"]"+Level);                                                      // Format third line
           }
          else                                                                                                          // Keys without connections to surrounding nodes
           {pad(d+3);
            P.elementAt(d).append(f("%04d", Key));
           }
         }

        @Override void branchTop(int Keys, int Index, int Depth, boolean Locked)                                        // Print node referenced by top
         {if (Context)
           {final int d = Depth * linesToPrintABranch;
            pad(d+3);                                                                                                   // Pad the output area so that all the lines have the same length
            if (Locked) P.elementAt(d).append(Keys > 0 ? "}" : "{}");                                                   // Show end of lock if there were keys else show locked but no keys
            pad(d+3);                                                                                                   // Pad the output area so that all the lines have the same length
            P.elementAt(d+1).append(" "+Index);                                                                         // Add index of top to slot second line
           }
         }
       };
      subFinish();
     }

    void pad (int level)                                                                                                // Pad the strings at each level of the tree so we have a vertical face to continue with - a bit like Marc Brunel's tunneling shield
     {for (int i = P.size(); i < level; ++i) P.push(clearStringBuilder(new StringBuilder()));                           // Make sure we have a full deck of strings
      int m = 0;                                                                                                        // Maximum length
      for (StringBuilder s : P) m = m < s.length() ? s.length() : m;                                                    // Find maximum length
      for (StringBuilder s : P) if (s.length() < m) s.append(" ".repeat(m - s.length()));                               // Pad each string to the length of the longest string
     }

    StringBuilder printCollapsed ()                                                                                     // Collapse horizontal representation into a string
     {final StringBuilder t = new StringBuilder();                                                                      // Print the lines of the tree that are not blank
      pad(0);
      for  (StringBuilder s : P)
       {final String l = ""+s;
        if (!l.isBlank()) t.append(l+"|\n");
       }
      return t;
     }
   }

  StringBuilder printTree () {var s = new Print(true) .printCollapsed(); return s;}                                     // Print the keys in the tree and the connections between the nodes in the tree
  StringBuilder showTree ()  {var s = new Print(false).printCollapsed(); return s;}                                     // Print the keys in the tree

//D1 Tests                                                                                                              // Tests

  void testsStartHere () {super.testsStartHere();}                                                                      // Divider between code to be tested and code to drive testing

  final static int[]random_32 = {12, 3, 27, 1, 23, 20, 8, 18, 2, 31, 25, 16, 13, 32, 11, 21, 5, 24, 4, 10, 26, 30, 9, 6, 29, 17, 28, 15, 14, 19, 7, 22};
  final static int[]random    = {5918,5624,2514,4291,1791,5109,7993,60,1345,2705,5849,1034,2085,4208,4590,7740,9367,6582,4178,5578,1120,378,7120,8646,5112,4903,1482,8005,3801,5439,4534,9524,6111,204,5459,248,4284,8037,5369,7334,3384,5193,2847,1660,5605,7371,3430,1786,1216,4282,2146,1969,7236,2187,136,2726,9480,5,4515,6082,969,5017,7809,9321,3826,9179,5781,3351,4819,4545,8607,4146,6682,1043,2890,2964,7472,9405,4348,8333,2915,9674,7225,4743,995,1321,3885,6061,9958,3901,4710,4185,4776,5070,8892,8506,6988,2317,9342,3764,9859,4724,5195,673,359,9740,2089,9942,3749,9208,1,7446,7023,5496,4206,3272,3527,8593,809,3149,4173,9605,9021,5120,5265,7121,8667,6911,4717,2535,2743,1289,1494,3788,6380,9366,2732,1501,8543,8013,5612,2393,7041,3350,3204,288,7213,1741,1238,9830,6722,4687,6758,8067,4443,5013,5374,6986,282,6762,192,340,5075,6970,7723,5913,1060,1641,1495,5738,1618,157,6891,173,7535,4952,9166,8950,8680,1974,5466,2383,3387,3392,2188,3140,6806,3131,6237,6249,7952,1114,9017,4285,7193,3191,3763,9087,7284,9170,6116,3717,6695,6538,6165,6449,8960,2897,6814,3283,6600,6151,4624,3992,5860,9557,1884,5585,2966,1061,6414,2431,9543,6654,7417,2617,878,8848,8241,3790,3370,8768,1694,9875,9882,8802,7072,3772,2689,5301,7921,7774,1614,494,2338,8638,4161,4523,5709,4305,17,9626,843,9284,3492,7755,5525,4423,9718,2237,7401,2686,8751,1585,5919,9444,3271,1490,7004,5980,3904,370,5930,6304,7737,93,5941,9079,4968,9266,262,2766,4999,2450,9518,5137,8405,483,8840,2231,700,8049,8823,9811,9378,3811,8074,153,1940,1998,4354,7830,7086,6132,9967,5680,448,1976,4101,7839,3122,4379,9296,4881,1246,4334,9457,5401,1945,9548,8290,1184,3464,132,2458,7704,1056,7554,6203,2270,6070,4889,7369,1676,485,3648,357,1912,9661,4246,1576,1836,4521,7667,6907,2098,8825,7404,4019,8284,3710,7202,7050,9870,3348,3624,9224,6601,7897,6288,3713,932,5596,353,2615,3273,833,1446,8624,2489,3872,486,1091,2493,4157,3611,6570,7107,9153,4543,9504,4746,1342,9737,3247,8984,3640,5698,7814,307,8775,1150,4330,3059,5784,2370,5248,4806,6107,9700,231,3566,5627,3957,5317,5415,8119,2588,9440,2961,9786,4769,466,5411,3080,7623,5031,2378,9286,4801,797,1527,2325,847,6341,5310,1926,9481,2115,2165,5255,5465,5561,3606,7673,7443,7243,8447,2348,7925,6447,8311,6729,4441,7763,8107,267,8135,9194,6775,3883,9639,612,5024,1351,7557,9241,5181,2239,8002,5446,747,166,325,9925,3820,9531,5163,3545,558,7103,7658,5670,8323,4821,6263,7982,59,3700,1082,4474,4353,8637,9558,5191,842,5925,6455,4092,9929,9961,290,3523,6290,7787,8266,7986,7269,6408,3620,406,5964,7289,1620,6726,1257,1993,7006,5545,2913,5093,5066,3019,7081,6760,6779,7061,9051,8852,8118,2340,6596,4594,9708,8430,8659,8920,9268,5431,9203,2823,1427,2203,6422,6193,5214,9566,8791,4964,7575,4350,56,2227,8545,5646,3089,2204,4081,487,8496,2258,4336,6955,3452,556,8602,8251,8569,8636,9430,1025,9459,7137,8392,3553,5945,9414,3078,1688,5480,327,8117,2289,2195,8564,9423,103,7724,3091,8548,7298,5279,6042,2855,3286,3542,9361,420,7020,4112,5320,5366,6379,114,9174,9744,592,5346,3985,3174,5157,9890,1605,3082,8099,4346,7256,8670,5687,6613,6620,1458,1045,7917,2980,2399,1433,3315,4084,178,7056,2132,2728,4421,9195,4181,6017,6229,2945,4627,2809,8816,6737,18,8981,3813,8890,5304,3789,6959,7476,1856,4197,6944,9578,5915,3060,9932,3463,67,7393,9857,5822,3187,501,653,8453,3691,9736,6845,1365,9645,4120,2157,8471,4436,6435,2758,7591,9805,7142,7612,4891,7342,5764,8683,8365,2967,6947,441,2116,6612,1399,7585,972,6548,5481,7733,7209,222,5903,6161,9172,9628,7348,1588,5992,6094,7176,4214,8702,2987,74,8486,9788,7164,5788,8535,8422,6826,1800,8965,4965,565,5609,4686,2556,9324,5000,9809,1994,4737,63,8992,4783,2536,4462,8868,6346,5553,3980,2670,1601,4272,8725,4698,7333,7826,9233,4198,1997,1687,4851,62,7893,8149,8015,341,2230,1280,5559,9756,3761,7834,6805,9287,4622,5748,2320,1958,9129,9649,1644,4323,5096,9490,7529,6444,7478,7044,9525,7713,234,7553,9099,9885,7135,6493,9793,6268,8363,2267,9157,9451,1438,9292,1637,3739,695,1090,4731,4549,5171,5975,7347,5192,5243,1084,2216,9860,3318,5594,5790,1107,220,9397,3378,1353,4498,6497,5442,7929,7377,9541,9871,9895,6742,9146,9409,292,6278,50,5288,2217,4923,6790,4730,9240,3006,3547,9347,7863,4275,3287,2673,7485,1915,9837,2931,3918,635,9131,1197,6250,3853,4303,790,5548,9993,3702,2446,3862,9652,4432,973,41,3507,8585,2444,1633,956,5789,1523,8657,4869,8580,8474,7093,7812,2549,7363,9315,6731,1130,7645,7018,7852,362,1636,2905,8006,4040,6643,8052,7021,3665,8383,715,1876,2783,3065,604,4566,8761,7911,1983,3836,5547,8495,8144,1950,2537,8575,640,8730,8303,1454,8165,6647,4762,909,9449,8640,9253,7293,8767,3004,4623,6862,8994,2520,1215,6299,8414,2576,6148,1510,313,3693,9843,8757,5774,8871,8061,8832,5573,5275,9452,1248,228,9749,2730};

  static void test_tree(boolean Ex)
   {sayCurrentTestName();
    final Tree t = new Tree(new Build().maxLeafSize(2).maxBranchSize(3).numberOfNodes(4).immediate(Ex));
                                           t.freeChain.countAllZeros().ok(1);
    final Leaf   a = t.leaf(t.root());     t.freeChain.countAllZeros().ok(1);
    final Leaf   b = t.leaf();             t.freeChain.countAllZeros().ok(2);
    final Branch c = t.branch();           t.freeChain.countAllZeros().ok(3);
    a.insert(t.new Int(2), t.new Int(22)); t.countInc();
    b.insert(t.new Int(4), t.new Int(44)); t.countInc();
    c.insert(t.new Int(5), t.new Int(55));

    final Leaf   A = t.leaf  (a.at.i());   t.isAllocated(a.at.i()).ok(true);
    final Leaf   B = t.leaf  (b.at.i());   t.isAllocated(b.at.i()).ok(true);
    final Branch C = t.branch(c.at.i());   t.isAllocated(c.at.i()).ok(true);

    A.insert(t.One, t.new Int(11)); t.countInc();
    B.insert(t.new Int(3), t.new Int(33)); t.countInc();
    C.insert(t.new Int(6), t.new Int(66));
    t.dumpProgramState("AAAA");

    //stop(t.mainMemoryMd5Sum());
    t.ok(()->t.mainMemoryMd5Sum(), "d2ab8f0a05e9505a88142d9db1d4f133");

    //stop(t.dumpTree());
    if (Ex) ok  (t.dumpTree(), """
Tree memory dump
Leaf   size   :   23
Branch size   :   34
Node   size   :   64
MaxLeafSize   :    2
MaxBranchSize :    3
NumberOfNodes :    4
Allocations   :    3
Number of Keys:    4
Height        :    1
Leaf           size:   2, count:   2
 Ref   Key  Data
   1     1    11
   0     2    22
Leaf   at:   1 size:   2, count:   2
 Ref   Key  Data
   1     3    33
   0     4    44
Branch at:   2 size:   3, count:   2, top:   0, level:   0
 Ref   Key  Data
   0     5    55
   1     6    66
""");

               t.isAllocated(a.at.i()).ok(true);
    t.free(A); t.isAllocated(a.at.i()).ok(false);  t.countDec(); t.countDec();
    t.dumpProgramState("BBBB");

    //stop(t.mainMemoryMd5Sum());
    t.ok(()->t.mainMemoryMd5Sum(), "b7a97eeba1e44933748174112d97e21a");
    //stop(t.dumpTree());
    if (Ex) ok  (t.dumpTree(), """
Tree memory dump
Leaf   size   :   23
Branch size   :   34
Node   size   :   64
MaxLeafSize   :    2
MaxBranchSize :    3
NumberOfNodes :    4
Allocations   :    2
Number of Keys:    2
Height        :    1
Leaf   at:   1 size:   2, count:   2
 Ref   Key  Data
   1     3    33
   0     4    44
Branch at:   2 size:   3, count:   2, top:   0, level:   0
 Ref   Key  Data
   0     5    55
   1     6    66
""");
               t.isAllocated(b.at.i()).ok(true);
    t.free(b); t.isAllocated(b.at.i()).ok(false);   t.countDec(); t.countDec();
    t.dumpProgramState("CCCC");

    //stop(t.mainMemoryMd5Sum());
    t.ok(()->t.mainMemoryMd5Sum(), "0a70370c6a365b75300fc403b3449468");
    //stop(t.dumpTree);
    if (Ex) ok(t.dumpTree(), """
Tree memory dump
Leaf   size   :   23
Branch size   :   34
Node   size   :   64
MaxLeafSize   :    2
MaxBranchSize :    3
NumberOfNodes :    4
Allocations   :    1
Number of Keys:    0
Height        :    1
Branch at:   2 size:   3, count:   2, top:   0, level:   0
 Ref   Key  Data
   0     5    55
   1     6    66
""");

               t.isAllocated(c.at.i()).ok(true);
    t.free(c); t.isAllocated(c.at.i()).ok(false);
    t.dumpProgramState("DDDD");

    //stop(t.mainMemoryMd5Sum());
    t.ok(()->t.mainMemoryMd5Sum(), "499146b77085da8aac384998dfb7196c");
    //stop(t.dumpTree());
    if (Ex) ok(t.dumpTree(), """
Tree memory dump
Leaf   size   :   23
Branch size   :   34
Node   size   :   64
MaxLeafSize   :    2
MaxBranchSize :    3
NumberOfNodes :    4
Allocations   :    0
Number of Keys:    0
Height        :    1
""");

    t.freeAllLocks();
    t.maxSteps(999_999);
    t.execute();
   }

  static void test_tree()
   {test_tree(true);
    test_tree(false);
   }

  static void test_insert (boolean Ex)
   {sayCurrentTestName();

    final int  N = 32;
    final Tree t = new Tree(new Build().maxLeafSize(2).maxBranchSize(3).numberOfNodes(N).immediate(Ex))
     {void treeCode()
       {new ForCount(One, new Int(N+1))
         {void body(Int Index)
           {insert(Index, Index.Mul(11));
            dumpProgramState("AAAA");
           }
         };
        height().ok(3);

        //stop(mainMemoryMd5Sum());
        ok(()->mainMemoryMd5Sum(), "bc384643844129f8149324e242a989ed");

        //stop(printTree());
        if (Ex) ok(printTree(), """
                                                 0008                                                              0016                                                                0024                                                         |
                                                 (0,0)                                                             (0,2)                                                               (0,4)   5                                                    |
                                                 [9,0]2                                                            [14,2]2                                                             [21,4]2                                                      |
       0002          0004          0006                         0010            0012             0014                              0018             0020             0022                               0026           0028           0030          |
       (9,0,0)       (9,0,2)       (9,0,4) 8                    (14,0,0)        (14,0,2)         (14,0,4) 13                       (21,0,0)         (21,0,2)         (21,0,4) 18                        (5,0,0)        (5,0,2)        (5,0,4) 2     |
       [3,0]1        [4,2]1        [7,4]1                       [10,0]1         [6,2]1           [12,4]1                           [15,0]1          [11,2]1          [17,4]1                            [22,0]1        [16,2]1        [24,4]1       |
1,2           3,4           5,6             7,8        9,10             11,12           13,14               15,16         17,18            19,20            21,22               23,24           25,26          27,28          29,30            31,32|
(3,9,0)       (4,9,2)       (7,9,4)         (8,9)      (10,14,0)        (6,14,2)        (12,14,4)           (13,14)       (15,21,0)        (11,21,2)        (17,21,4)           (18,21)         (22,5,0)       (16,5,2)       (24,5,4)         (2,5)|
""");

        //stop(showTree());
        if (Ex) ok(showTree(), """
                        0008                               0016                                0024                                |
   0002   0004   0006           0010     0012     0014              0018     0020     0022              0026     0028     0030     |
1,2    3,4    5,6    7,8    9,10    11,12    13,14    15,16    17,18    19,20    21,22    23,24    25,26    27,28    29,30    31,32|
""");

        final FirstEntry F   = new FirstEntry(); freeLock(F.leaf.i()); F.leaf.ok(3);
        final LastEntry  L   = new  LastEntry(); freeLock(L.leaf.i()); L.leaf.ok(2);

        final FindBranch f_8 = new FindBranch(new Int( 8)); freeLock(f_8.branch.i());
        final FindBranch f_2 = new FindBranch(new Int( 2)); freeLock(f_2.branch.i());
        final FindBranch f14 = new FindBranch(new Int(14)); freeLock(f14.branch.i());
        final FindBranch f_1 = new FindBranch(new Int( 1));

        ok(()->f_8, "Find branch key: key=8, valid: true, branch:0, slot: slot=0\n");
        ok(()->f_2, "Find branch key: key=2, valid: true, branch:9, slot: slot=0\n");
        ok(()->f14, "Find branch key: key=14, valid: true, branch:14, slot: slot=4\n");
        ok(()->f_1, "Find branch key: key=1, valid: false\n");

        final MaximumKey m_0 = new MaximumKey(new Int( 0));
        final MaximumKey m21 = new MaximumKey(new Int(21));
        final MaximumKey m14 = new MaximumKey(new Int(14));
        final MaximumKey m_9 = new MaximumKey(new Int( 9));

        ok(()->m_0, "Branch:  0,  maximum key: 32\n");
        ok(()->m21, "Branch: 21,  maximum key: 24\n");
        ok(()->m14, "Branch: 14,  maximum key: 16\n");
        ok(()->m_9, "Branch:  9,  maximum key:  8\n");

        getLock(root());
        final FindLeafForNextKey n_1 = new FindLeafForNextKey(new Int(1)); freeLock(n_1.leaf); n_1.leaf.ok(3);
        final FindLeafForNextKey n_2 = new FindLeafForNextKey(new Int(2)); freeLock(n_2.leaf); n_2.leaf.ok(4);

        delete(new Int(16));

        //stop(printTree());
        if (Ex) ok(printTree(), """
                                                 {0008                                                             0015                                                                0024   }                                                      |
                                                 (0,0)                                                             (0,2)                                                               (0,4)    5                                                    |
                                                 [9,0]2                                                            [14,2]2                                                             [21,4]2                                                       |
       0002          0004          0006                         0010            0012             0014                              0018             0020             0022                                0026           0028           0030          |
       (9,0,0)       (9,0,2)       (9,0,4) 8                    (14,0,0)        (14,0,2)         (14,0,4) 13                       (21,0,0)         (21,0,2)         (21,0,4) 18                         (5,0,0)        (5,0,2)        (5,0,4) 2     |
       [3,0]1        [4,2]1        [7,4]1                       [10,0]1         [6,2]1           [12,4]1                           [15,0]1          [11,2]1          [17,4]1                             [22,0]1        [16,2]1        [24,4]1       |
1,2           3,4           5,6             7,8        9,10             11,12           13,14               15            17,18            19,20            21,22               23,24            25,26          27,28          29,30            31,32|
(3,9,0)       (4,9,2)       (7,9,4)         (8,9)      (10,14,0)        (6,14,2)        (12,14,4)           (13,14)       (15,21,0)        (11,21,2)        (17,21,4)           (18,21)          (22,5,0)       (16,5,2)       (24,5,4)         (2,5)|
""");

        ok(()->mainMemoryMd5Sum(), "0e71c12fa8fae5a4beba0e335636a2a5");                                                 // Confirm that main memory in immediate and delayed modes arrives at the same state

        delete(new Int(15));

        //stop(printTree());
        if (Ex) ok(printTree(), """
                                                 {0008                                            0014                                                                0024   }                                                      |
                                                 (0,0)                                            (0,2)                                                               (0,4)    5                                                    |
                                                 [9,0]2                                           [14,2]2                                                             [21,4]2                                                       |
       0002          0004          0006                         0010            0012                              0018             0020             0022                                0026           0028           0030          |
       (9,0,0)       (9,0,2)       (9,0,4) 8                    (14,0,0)        (14,0,2) 12                       (21,0,0)         (21,0,2)         (21,0,4) 18                         (5,0,0)        (5,0,2)        (5,0,4) 2     |
       [3,0]1        [4,2]1        [7,4]1                       [10,0]1         [6,2]1                            [15,0]1          [11,2]1          [17,4]1                             [22,0]1        [16,2]1        [24,4]1       |
1,2           3,4           5,6             7,8        9,10             11,12              13,14         17,18            19,20            21,22               23,24            25,26          27,28          29,30            31,32|
(3,9,0)       (4,9,2)       (7,9,4)         (8,9)      (10,14,0)        (6,14,2)           (12,14)       (15,21,0)        (11,21,2)        (17,21,4)           (18,21)          (22,5,0)       (16,5,2)       (24,5,4)         (2,5)|
""");

        ok(()->mainMemoryMd5Sum(), "0781400ee30ae789dfa9eda01884ab0d");                                                 // Confirm that main memory in immediate and delayed modes arrives at the same state

        freeAllLocks();
        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_insert ()
   {          test_insert(true);
              test_insert(false);
   }

  static void test_insertMerged(boolean Ex)
   {sayCurrentTestName();
    final int N = 32;
    final Tree t = new Tree(new Build().maxLeafSize(4).maxBranchSize(3).numberOfNodes(N).immediate(Ex))
     {void treeCode()
       {new ForCount(One, new Int(N+1))
         {void body(Int Index)
           {insert(Index, Index);
            dumpProgramState("AAAA");
           }
         };

        //stop(mainMemoryMd5Sum(), printTree(), showTree());
        ok(()->mainMemoryMd5Sum(), "e474db8316f4babf75a85304220eafba");

        //stop(printTree());
        if (Ex) ok(printTree(), """
                                                          0016                                                                       |
                                                          (0,2)  5                                                                   |
                                                          [9,2]2                                                                     |
       0004          0008             0012                                   0020              0024              0028                |
       (9,0,0)       (9,0,2)          (9,0,4) 8                              (5,0,0)           (5,0,2)           (5,0,4) 2           |
       [3,0]1        [4,2]1           [7,4]1                                 [10,0]1           [6,2]1            [12,4]1             |
1,2,3,4       5,6,7,8       9,10,11,12         13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
""");

        //stop(showTree());
        if (Ex) ok(showTree(), """
                                               0016                                                        |
       0004       0008          0012                          0020           0024           0028           |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
""");

        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_insertMerged()
   {          test_insertMerged(true);
              test_insertMerged(false);
   }

  static void test_insertReverse(boolean Ex)
   {sayCurrentTestName();
    final int N = 32;
    final Tree t = new Tree(new Build().maxLeafSize(4).maxBranchSize(3).numberOfNodes(N).immediate(Ex))
     {void treeCode()
       {new ForCount(new Int(N))
         {void body(Int Index)
           {insert(new Int(N).Sub(Index), Index);
            dumpProgramState("AAAA");
           }
         };

        //stop(mainMemoryMd5Sum(), printTree(), showTree());
        //ok(()->mainMemoryMd5Sum(), "eaeb17b251901bfbe4540a4e4e372a32");
        ok(()->mainMemoryMd5Sum(), "803497907d77b4fe9daccb4852963044");

        //stop(printTree());
        if (Ex) ok(printTree(), """
                                                           0016                                                                       |
                                                           (0,2)  5                                                                   |
                                                           [9,2]2                                                                     |
        0004          0008             0012                                   0020              0024              0028                |
        (9,0,0)       (9,0,2)          (9,0,4) 8                              (5,0,0)           (5,0,2)           (5,0,4) 2           |
        [12,0]1       [6,2]1           [10,4]1                                [7,0]1            [4,2]1            [3,4]1              |
1,2,3,4        5,6,7,8       9,10,11,12         13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
(12,9,0)       (6,9,2)       (10,9,4)           (8,9)              (7,5,0)           (4,5,2)           (3,5,4)             (2,5)      |
""");

        //stop(showTree());
        if (Ex) ok(showTree(), """
                                               0016                                                        |
       0004       0008          0012                          0020           0024           0028           |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
""");

        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_insertReverse()
   {          test_insertReverse(true);
              test_insertReverse(false);
   }

  static void test_insertRandom32(boolean Ex)
   {sayCurrentTestName();

    final int  N = random_32.length;
    final Tree t = new Tree(new Build().maxLeafSize(4).maxBranchSize(3).numberOfNodes(N).immediate(Ex))
     {void treeCode()
       {final VerilogArrays.Array a = verilogArrays().new Array("loadRandomKeys", random_32);                           // Create an array of the random keys to be inserted from Verilog

        new ForCount(N)
         {void body(Int Index)
           {final Int k = new Int("Key", Index);

            k.S();                                                                                                      // Load index of item we want
            new I()
             {void        a() {k.targetInt(random_32[Index.i()]); k.setValid();}
              String      v() {return intMemory().writeInt() + " <= "+a.dataRegisterName()+";";}                        // Translate index into key
              boolean trace() {return false;}
             };
            k.TW();                                                                                                     // Write key into variable
            insert(k, Index);
            dumpProgramState("AAAA");
           }
         };

        //stop(mainMemoryMd5Sum(), printTree(), showTree());
        ok(()->mainMemoryMd5Sum(), "1728b43d40e91b20bc2d093d662621a0");

        //stop(printTree());
        if (Ex) ok(printTree(), """
                                                       0012                                                                 0025                                         |
                                                       (0,2)                                                                (0,4)   6                                    |
                                                       [5,2]2                                                               [10,4]2                                      |
       0003           0005          0008                                0016               0020             0023                            0027           0030          |
       (5,0,1)        (5,0,2)       (5,0,4) 4                           (10,0,1)           (10,0,4)         (10,0,5) 7                      (6,0,2)        (6,0,4) 2     |
       [1,1]1         [13,2]1       [8,4]1                              [11,1]1            [3,4]1           [14,5]1                         [9,2]1         [12,4]1       |
1,2,3         4,5            6,7,8           9,10,11,12      13,14,15,16        17,18,19,20        21,22,23           24,25          26,27         28,29,30         31,32|
(1,5,1)       (13,5,2)       (8,5,4)         (4,5)           (11,10,1)          (3,10,4)           (14,10,5)          (7,10)         (9,6,2)       (12,6,4)         (2,6)|
""");

        //stop(showTree());
        if (Ex) ok(showTree(), """
                                   0012                                               0025                          |
     0003   0005     0008                         0016           0020        0023              0027        0030     |
1,2,3    4,5    6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23    24,25    26,27    28,29,30    31,32|
""");

        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_insertRandom32()
   {          test_insertRandom32(true);
              test_insertRandom32(false);
   }

  static void test_deleteAscending(boolean Ex)
   {sayCurrentTestName();
    final int N = 32;

    final Tree t = new Tree(new Build().maxLeafSize(4).maxBranchSize(3).numberOfNodes(N).immediate(Ex))
     {void treeCode()
       {new ForCount(One, new Int(N+1))
         {void body(Int Index)
           {insert(Index, Index.Mul(11));
            dumpProgramState("AAAA");
            if (immediate())
             {final int i = Index.i();
              if (i >= 1 && i <=  4) height().ok(1);
              if (i >= 5 && i <= 16) height().ok(2);
              if (i >= 17)           height().ok(3);
             }
           }
         };

        final StringBuilder s = Ex ? printTree() : null;
        final StringBuilder m = new StringBuilder();

        new ForCount(new Int(N))
         {void body(Int Index)
           {final Int k = Index.Inc();
            final FirstEntry f = new FirstEntry(); f.key.ok(k); freeLock(f.leaf.i());
            delete(k).ok(k.Mul(11));
            if (Ex) s.append(printTree());
            new I() {void a() {m.append(mainMemoryMd5Sum()+"\n");} boolean trace() {return false;}};
            dumpProgramState("BBBB");
            if (immediate())
             {final int i = Index.i();
              if (i >=  0 && i <= 14) height().ok(3);
              if (i >= 16 && i <= 26) height().ok(2);
              if (i >= 27)            height().ok(1);
             }
           }
         };
        final FirstEntry F = new FirstEntry(); F.key.valid().ok(false); freeLock(F.leaf.i());

        //stop(m);
        ok(()->m, """
5aa61a70a53234097012cacfab5d2f34
35cbfbe938db2740e8096a2dab5cebdb
3029f2b4c0644a50a8fc7c0add94f203
5d10b9fb8a272f92076808bac64a12e7
1ce6866cb3208503ff6b6cf41a6ac35c
baec6a5812288a535949f1b0512e1175
a5ce1b04bf3a5159b6c4cd0315a6d7bc
d8e13524ec375feb038762c7f34e5eb2
aea383673ede5cf8f434283703e4dbe9
4888238459fc902deba304e1fcdd36e2
b25f07772d91f7c2d9d57d3340f77178
7829404cea2465b455091ff9e8b098e6
2a8d8dabc597f13cf34d6f223d13034f
9b9ec03c961addc9b729bd2c978adab5
e7077cc4d8a4c5ccdebf118216341c35
6108696a802b0e28b0e9a3b681c21d5a
a2dd443d494addf3c397095c4eb80d8a
0346d9d2cf1844e0ff3b0f57d2cc10b8
142fcd8dae51af1f29482dd45d4e6dca
5526ecdf48ecbc4fc9d5c43a2f49f4d3
da815ee002a8004a97df68a40500846c
ef14026e4e1839dba44bc41dee8f6b56
4c35e5ce16d2229862a5c9ae5da21293
cade043f4f69e9a558b14150b7e19cc9
c8d770e2451d6e6086a97b84e4e7c5af
ec0ac2cc4142966a77f0d79fad30fcef
3b6f4c37f6bb6a5706c07b0607e0eb72
541f07d20765966ee23152ae4d6e9b26
e95851ca5f3d8ad9c95dc700b56eb752
bec5f5f8a10f48c58d2511ce6de2c856
9bedba789dec3ea66893077bbacab123
27c13a104305cc7f5fb4416354fcd276
""");

        //stop(s);
        if (Ex) ok(s, """
                                                          0016                                                                       |
                                                          (0,2)  5                                                                   |
                                                          [9,2]2                                                                     |
       0004          0008             0012                                   0020              0024              0028                |
       (9,0,0)       (9,0,2)          (9,0,4) 8                              (5,0,0)           (5,0,2)           (5,0,4) 2           |
       [3,0]1        [4,2]1           [7,4]1                                 [10,0]1           [6,2]1            [12,4]1             |
1,2,3,4       5,6,7,8       9,10,11,12         13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
                                                          0016                                                                       |
                                                          (0,2)  5                                                                   |
                                                          [9,2]2                                                                     |
       0004          0008             0012                                   0020              0024              0028                |
       (9,0,0)       (9,0,2)          (9,0,4) 8                              (5,0,0)           (5,0,2)           (5,0,4) 2           |
       [3,0]1        [4,2]1           [7,4]1                                 [10,0]1           [6,2]1            [12,4]1             |
2,3,4         5,6,7,8       9,10,11,12         13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
                                                          0016                                                                       |
                                                          (0,2)  5                                                                   |
                                                          [9,2]2                                                                     |
       0004          0008             0012                                   0020              0024              0028                |
       (9,0,0)       (9,0,2)          (9,0,4) 8                              (5,0,0)           (5,0,2)           (5,0,4) 2           |
       [3,0]1        [4,2]1           [7,4]1                                 [10,0]1           [6,2]1            [12,4]1             |
3,4           5,6,7,8       9,10,11,12         13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
                                                          0016                                                                       |
                                                          (0,2)  5                                                                   |
                                                          [9,2]2                                                                     |
       0004          0008             0012                                   0020              0024              0028                |
       (9,0,0)       (9,0,2)          (9,0,4) 8                              (5,0,0)           (5,0,2)           (5,0,4) 2           |
       [3,0]1        [4,2]1           [7,4]1                                 [10,0]1           [6,2]1            [12,4]1             |
4             5,6,7,8       9,10,11,12         13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
                                            0016                                                                       |
                                            (0,2)  5                                                                   |
                                            [9,2]2                                                                     |
       0008             0012                                   0020              0024              0028                |
       (9,0,2)          (9,0,4) 8                              (5,0,0)           (5,0,2)           (5,0,4) 2           |
       [4,2]1           [7,4]1                                 [10,0]1           [6,2]1            [12,4]1             |
5,6,7,8       9,10,11,12         13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
(4,9,2)       (7,9,4)            (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
                                            0016                                                                       |
                                            (0,2)  5                                                                   |
                                            [9,2]2                                                                     |
       0008             0012                                   0020              0024              0028                |
       (9,0,2)          (9,0,4) 8                              (5,0,0)           (5,0,2)           (5,0,4) 2           |
       [4,2]1           [7,4]1                                 [10,0]1           [6,2]1            [12,4]1             |
6,7,8         9,10,11,12         13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
(4,9,2)       (7,9,4)            (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
                                            0016                                                                       |
                                            (0,2)  5                                                                   |
                                            [9,2]2                                                                     |
       0008             0012                                   0020              0024              0028                |
       (9,0,2)          (9,0,4) 8                              (5,0,0)           (5,0,2)           (5,0,4) 2           |
       [4,2]1           [7,4]1                                 [10,0]1           [6,2]1            [12,4]1             |
7,8           9,10,11,12         13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
(4,9,2)       (7,9,4)            (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
                                            0016                                                                       |
                                            (0,2)  5                                                                   |
                                            [9,2]2                                                                     |
       0008             0012                                   0020              0024              0028                |
       (9,0,2)          (9,0,4) 8                              (5,0,0)           (5,0,2)           (5,0,4) 2           |
       [4,2]1           [7,4]1                                 [10,0]1           [6,2]1            [12,4]1             |
8             9,10,11,12         13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
(4,9,2)       (7,9,4)            (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
                              0016                                                                       |
                              (0,2)  5                                                                   |
                              [9,2]2                                                                     |
          0012                                   0020              0024              0028                |
          (9,0,4) 8                              (5,0,0)           (5,0,2)           (5,0,4) 2           |
          [7,4]1                                 [10,0]1           [6,2]1            [12,4]1             |
9,10,11,12         13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
(7,9,4)            (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
                            0016                                                                       |
                            (0,2)  5                                                                   |
                            [9,2]2                                                                     |
        0012                                   0020              0024              0028                |
        (9,0,4) 8                              (5,0,0)           (5,0,2)           (5,0,4) 2           |
        [7,4]1                                 [10,0]1           [6,2]1            [12,4]1             |
10,11,12         13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
(7,9,4)          (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
                           0016                                                                       |
                           (0,2)  5                                                                   |
                           [9,2]2                                                                     |
       0012                                   0020              0024              0028                |
       (9,0,4) 8                              (5,0,0)           (5,0,2)           (5,0,4) 2           |
       [7,4]1                                 [10,0]1           [6,2]1            [12,4]1             |
11,12           13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
(7,9,4)         (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
                           0016                                                                       |
                           (0,2)  5                                                                   |
                           [9,2]2                                                                     |
       0012                                   0020              0024              0028                |
       (9,0,4) 8                              (5,0,0)           (5,0,2)           (5,0,4) 2           |
       [7,4]1                                 [10,0]1           [6,2]1            [12,4]1             |
12              13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
(7,9,4)         (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
             0016                                                                       |
             (0,2)  5                                                                   |
             [9,2]2                                                                     |
                                0020              0024              0028                |
 8                              (5,0,0)           (5,0,2)           (5,0,4) 2           |
                                [10,0]1           [6,2]1            [12,4]1             |
  13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
  (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
          0016                                                                       |
          (0,2)  5                                                                   |
          [9,2]2                                                                     |
                             0020              0024              0028                |
 8                           (5,0,0)           (5,0,2)           (5,0,4) 2           |
                             [10,0]1           [6,2]1            [12,4]1             |
  14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
  (8,9)           (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
       0016                                                                       |
       (0,2)  5                                                                   |
       [9,2]2                                                                     |
                          0020              0024              0028                |
 8                        (5,0,0)           (5,0,2)           (5,0,4) 2           |
                          [10,0]1           [6,2]1            [12,4]1             |
  15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
  (8,9)        (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
       0016                                                                       |
       (0,2)  5                                                                   |
       [9,2]2                                                                     |
                          0020              0024              0028                |
 8                        (5,0,0)           (5,0,2)           (5,0,4) 2           |
                          [10,0]1           [6,2]1            [12,4]1             |
  16           17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
  (8,9)        (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
{}                                                                     |
   5                                                                   |
               0020              0024              0028                |
               (5,0,0)           (5,0,2)           (5,0,4) 2           |
               [10,0]1           [6,2]1            [12,4]1             |
    17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
    (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
        0020              0024             0028                |
        (0,0)             (0,2)            (0,4)   2           |
        [10,0]1           [6,2]1           [12,4]1             |
18,19,20       21,22,23,24      25,26,27,28         29,30,31,32|
(10,0,0)       (6,0,2)          (12,0,4)            (2,0)      |
        0020              0024             0028                |
        (0,0)             (0,2)            (0,4)   2           |
        [10,0]1           [6,2]1           [12,4]1             |
19,20          21,22,23,24      25,26,27,28         29,30,31,32|
(10,0,0)       (6,0,2)          (12,0,4)            (2,0)      |
        0020              0024             0028                |
        (0,0)             (0,2)            (0,4)   2           |
        [10,0]1           [6,2]1           [12,4]1             |
20             21,22,23,24      25,26,27,28         29,30,31,32|
(10,0,0)       (6,0,2)          (12,0,4)            (2,0)      |
           0024             0028                |
           (0,2)            (0,4)   2           |
           [6,2]1           [12,4]1             |
21,22,23,24      25,26,27,28         29,30,31,32|
(6,0,2)          (12,0,4)            (2,0)      |
        0024             0028                |
        (0,2)            (0,4)   2           |
        [6,2]1           [12,4]1             |
22,23,24      25,26,27,28         29,30,31,32|
(6,0,2)       (12,0,4)            (2,0)      |
       0024             0028                |
       (0,2)            (0,4)   2           |
       [6,2]1           [12,4]1             |
23,24        25,26,27,28         29,30,31,32|
(6,0,2)      (12,0,4)            (2,0)      |
       0024             0028                |
       (0,2)            (0,4)   2           |
       [6,2]1           [12,4]1             |
24           25,26,27,28         29,30,31,32|
(6,0,2)      (12,0,4)            (2,0)      |
           0028                |
           (0,4)   2           |
           [12,4]1             |
25,26,27,28         29,30,31,32|
(12,0,4)            (2,0)      |
        0028                |
        (0,4)   2           |
        [12,4]1             |
26,27,28         29,30,31,32|
(12,0,4)         (2,0)      |
        0028                |
        (0,4)   2           |
        [12,4]1             |
27,28            29,30,31,32|
(12,0,4)         (2,0)      |
        0028                |
        (0,4)   2           |
        [12,4]1             |
28               29,30,31,32|
(12,0,4)         (2,0)      |
29,30,31,32|
30,31,32|
31,32|
32|
""");
        scDieAreaX = 500; scDieAreaY = 550;
        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_deleteAscending()
   {          test_deleteAscending(true);
              test_deleteAscending(false);
   }

  static void test_deleteDescending(boolean Ex)
   {sayCurrentTestName();
    final int  N = 32;

    final Tree t = new Tree(new Build().maxLeafSize(4).maxBranchSize(3).numberOfNodes(N).immediate(Ex))
     {void treeCode()
       {new ForCount(One, new Int(N+1))
         {void body(Int Index)
           {insert(Index, Index.Mul(11));
           }
         };
        dumpProgramState("AAAA");

        final StringBuilder s = Ex ? printTree() : null;
        final StringBuilder m = new StringBuilder();

        new ForCount(new Int(N))
         {void body(Int Index)
           {final Int k = new Int(N).Sub(Index);
            delete(k).ok(k.Mul(11));
            if (Ex) s.append(printTree());
            new I() {void a() {m.append(mainMemoryMd5Sum()+"\n");} boolean trace() {return false;}};

            dumpProgramState("BBBB");
           }
         };

        //stop(m);
        ok(()->m, """
056f01a8991906d37869f1b1eab3fe17
671bdf213c9d78a5ae92de9a68b2291b
d7ace5214693bee34f1fbcfb24029b7b
d884790db40c3c8f9374c7204f5a04f9
2dd512ce0579c82b047a0e81a0b3b725
01b3310487b6e67d7fbf2389cccb9f37
3020219ccdb85f0fa731c58eb6f0238e
b2ee1a93457ae6111faa78f5cdbfb85a
76d8df719d7784d1c9139bc57b27fe95
34d07c08d347d17877a4687730949adf
c8c4926febd9594913528455adb02b2d
8321fd8f9b7378cb048eb2cf789883e5
bf497c1468d96580acec00e053248061
69c3376081386aa331d03bd82e296746
a1cdcf9c2a31cc601ba5e2971286a6b0
d12d589d94c630b86fe21f44d720a214
cbbf3880affe0bdfbb02aa2534d32ea6
cc6e2d87ec3a1bb3a1014b620b9d76af
c55abd9aa6a7e15e42f518b940f8c622
92f1b162b13bc69653273e8a8999160d
fbfb118bd2012fd8c48019c487d4c63e
c94e8c83bafbe46bb5c2f04cd66489c1
fd5dd230c0e53310110dbf804f86b219
27d8ccaf7d3b0c03d70cf227205b5f05
486019232a0286f9d7229efa2c8eac36
a3c78f3797b8e7aba3a5d47ca73d6e1a
ecbf16e57bf22bfe0498cacf072b8cfc
50e15c6601b510f80cc84f183f146863
2a4094107e0a425e8f6cc35933e46462
fbda5e7bd781172a68f5c98acdc9d28d
d81ad99751b570f08c2543632319f99e
aa5e343f6b3b1296e12384b4c49e105f
""");

        //stop(s);
        if (Ex) ok(""+s, """
                                                          0016                                                                       |
                                                          (0,2)  5                                                                   |
                                                          [9,2]2                                                                     |
       0004          0008             0012                                   0020              0024              0028                |
       (9,0,0)       (9,0,2)          (9,0,4) 8                              (5,0,0)           (5,0,2)           (5,0,4) 2           |
       [3,0]1        [4,2]1           [7,4]1                                 [10,0]1           [6,2]1            [12,4]1             |
1,2,3,4       5,6,7,8       9,10,11,12         13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
                                                          0016                                                                    |
                                                          (0,2)  5                                                                |
                                                          [9,2]2                                                                  |
       0004          0008             0012                                   0020              0024              0028             |
       (9,0,0)       (9,0,2)          (9,0,4) 8                              (5,0,0)           (5,0,2)           (5,0,4) 2        |
       [3,0]1        [4,2]1           [7,4]1                                 [10,0]1           [6,2]1            [12,4]1          |
1,2,3,4       5,6,7,8       9,10,11,12         13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31|
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)   |
                                                          0016                                                                 |
                                                          (0,2)  5                                                             |
                                                          [9,2]2                                                               |
       0004          0008             0012                                   0020              0024              0028          |
       (9,0,0)       (9,0,2)          (9,0,4) 8                              (5,0,0)           (5,0,2)           (5,0,4) 2     |
       [3,0]1        [4,2]1           [7,4]1                                 [10,0]1           [6,2]1            [12,4]1       |
1,2,3,4       5,6,7,8       9,10,11,12         13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30|
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)|
                                                          0016                                                                 |
                                                          (0,2)  5                                                             |
                                                          [9,2]2                                                               |
       0004          0008             0012                                   0020              0024              0028          |
       (9,0,0)       (9,0,2)          (9,0,4) 8                              (5,0,0)           (5,0,2)           (5,0,4) 2     |
       [3,0]1        [4,2]1           [7,4]1                                 [10,0]1           [6,2]1            [12,4]1       |
1,2,3,4       5,6,7,8       9,10,11,12         13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29   |
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)|
                                                          0016                                                      |
                                                          (0,2)  5                                                  |
                                                          [9,2]2                                                    |
       0004          0008             0012                                   0020              0024                 |
       (9,0,0)       (9,0,2)          (9,0,4) 8                              (5,0,0)           (5,0,2) 12           |
       [3,0]1        [4,2]1           [7,4]1                                 [10,0]1           [6,2]1               |
1,2,3,4       5,6,7,8       9,10,11,12         13,14,15,16        17,18,19,20       21,22,23,24          25,26,27,28|
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)              (10,5,0)          (6,5,2)              (12,5)     |
                                                          0016                                                   |
                                                          (0,2)  5                                               |
                                                          [9,2]2                                                 |
       0004          0008             0012                                   0020              0024              |
       (9,0,0)       (9,0,2)          (9,0,4) 8                              (5,0,0)           (5,0,2) 12        |
       [3,0]1        [4,2]1           [7,4]1                                 [10,0]1           [6,2]1            |
1,2,3,4       5,6,7,8       9,10,11,12         13,14,15,16        17,18,19,20       21,22,23,24          25,26,27|
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)              (10,5,0)          (6,5,2)              (12,5)  |
                                                          0016                                                 |
                                                          (0,2)  5                                             |
                                                          [9,2]2                                               |
       0004          0008             0012                                   0020              0024            |
       (9,0,0)       (9,0,2)          (9,0,4) 8                              (5,0,0)           (5,0,2) 12      |
       [3,0]1        [4,2]1           [7,4]1                                 [10,0]1           [6,2]1          |
1,2,3,4       5,6,7,8       9,10,11,12         13,14,15,16        17,18,19,20       21,22,23,24          25,26 |
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)              (10,5,0)          (6,5,2)              (12,5)|
                                                          0016                                                 |
                                                          (0,2)  5                                             |
                                                          [9,2]2                                               |
       0004          0008             0012                                   0020              0024            |
       (9,0,0)       (9,0,2)          (9,0,4) 8                              (5,0,0)           (5,0,2) 12      |
       [3,0]1        [4,2]1           [7,4]1                                 [10,0]1           [6,2]1          |
1,2,3,4       5,6,7,8       9,10,11,12         13,14,15,16        17,18,19,20       21,22,23,24          25    |
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)              (10,5,0)          (6,5,2)              (12,5)|
                                                          0016                                   |
                                                          (0,2)  5                               |
                                                          [9,2]2                                 |
       0004          0008             0012                                   0020                |
       (9,0,0)       (9,0,2)          (9,0,4) 8                              (5,0,0) 6           |
       [3,0]1        [4,2]1           [7,4]1                                 [10,0]1             |
1,2,3,4       5,6,7,8       9,10,11,12         13,14,15,16        17,18,19,20         21,22,23,24|
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)              (10,5,0)            (6,5)      |
                                                          0016                                |
                                                          (0,2)  5                            |
                                                          [9,2]2                              |
       0004          0008             0012                                   0020             |
       (9,0,0)       (9,0,2)          (9,0,4) 8                              (5,0,0) 6        |
       [3,0]1        [4,2]1           [7,4]1                                 [10,0]1          |
1,2,3,4       5,6,7,8       9,10,11,12         13,14,15,16        17,18,19,20         21,22,23|
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)              (10,5,0)            (6,5)   |
                                                          0016                             |
                                                          (0,2)  5                         |
                                                          [9,2]2                           |
       0004          0008             0012                                   0020          |
       (9,0,0)       (9,0,2)          (9,0,4) 8                              (5,0,0) 6     |
       [3,0]1        [4,2]1           [7,4]1                                 [10,0]1       |
1,2,3,4       5,6,7,8       9,10,11,12         13,14,15,16        17,18,19,20         21,22|
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)              (10,5,0)            (6,5)|
                                                          0016                             |
                                                          (0,2)  5                         |
                                                          [9,2]2                           |
       0004          0008             0012                                   0020          |
       (9,0,0)       (9,0,2)          (9,0,4) 8                              (5,0,0) 6     |
       [3,0]1        [4,2]1           [7,4]1                                 [10,0]1       |
1,2,3,4       5,6,7,8       9,10,11,12         13,14,15,16        17,18,19,20         21   |
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)              (10,5,0)            (6,5)|
                                                          0016                  |
                                                          (0,2)  5              |
                                                          [9,2]2                |
       0004          0008             0012                                      |
       (9,0,0)       (9,0,2)          (9,0,4) 8                    10           |
       [3,0]1        [4,2]1           [7,4]1                                    |
1,2,3,4       5,6,7,8       9,10,11,12         13,14,15,16           17,18,19,20|
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)                 (10,5)     |
                                                          0016               |
                                                          (0,2)  5           |
                                                          [9,2]2             |
       0004          0008             0012                                   |
       (9,0,0)       (9,0,2)          (9,0,4) 8                    10        |
       [3,0]1        [4,2]1           [7,4]1                                 |
1,2,3,4       5,6,7,8       9,10,11,12         13,14,15,16           17,18,19|
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)                 (10,5)  |
                                                          0016             |
                                                          (0,2)  5         |
                                                          [9,2]2           |
       0004          0008             0012                                 |
       (9,0,0)       (9,0,2)          (9,0,4) 8                    10      |
       [3,0]1        [4,2]1           [7,4]1                               |
1,2,3,4       5,6,7,8       9,10,11,12         13,14,15,16           17,18 |
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)                 (10,5)|
                                                          0016             |
                                                          (0,2)  5         |
                                                          [9,2]2           |
       0004          0008             0012                                 |
       (9,0,0)       (9,0,2)          (9,0,4) 8                    10      |
       [3,0]1        [4,2]1           [7,4]1                               |
1,2,3,4       5,6,7,8       9,10,11,12         13,14,15,16           17    |
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)                 (10,5)|
       0004         0008            0012               |
       (0,0)        (0,2)           (0,4)  8           |
       [3,0]1       [4,2]1          [7,4]1             |
1,2,3,4      5,6,7,8      9,10,11,12        13,14,15,16|
(3,0,0)      (4,0,2)      (7,0,4)           (8,0)      |
       0004         0008            0012            |
       (0,0)        (0,2)           (0,4)  8        |
       [3,0]1       [4,2]1          [7,4]1          |
1,2,3,4      5,6,7,8      9,10,11,12        13,14,15|
(3,0,0)      (4,0,2)      (7,0,4)           (8,0)   |
       0004         0008            0012         |
       (0,0)        (0,2)           (0,4)  8     |
       [3,0]1       [4,2]1          [7,4]1       |
1,2,3,4      5,6,7,8      9,10,11,12        13,14|
(3,0,0)      (4,0,2)      (7,0,4)           (8,0)|
       0004         0008            0012         |
       (0,0)        (0,2)           (0,4)  8     |
       [3,0]1       [4,2]1          [7,4]1       |
1,2,3,4      5,6,7,8      9,10,11,12        13   |
(3,0,0)      (4,0,2)      (7,0,4)           (8,0)|
       0004         0008              |
       (0,0)        (0,2)  7          |
       [3,0]1       [4,2]1            |
1,2,3,4      5,6,7,8        9,10,11,12|
(3,0,0)      (4,0,2)        (7,0)     |
       0004         0008           |
       (0,0)        (0,2)  7       |
       [3,0]1       [4,2]1         |
1,2,3,4      5,6,7,8        9,10,11|
(3,0,0)      (4,0,2)        (7,0)  |
       0004         0008         |
       (0,0)        (0,2)  7     |
       [3,0]1       [4,2]1       |
1,2,3,4      5,6,7,8        9,10 |
(3,0,0)      (4,0,2)        (7,0)|
       0004         0008         |
       (0,0)        (0,2)  7     |
       [3,0]1       [4,2]1       |
1,2,3,4      5,6,7,8        9    |
(3,0,0)      (4,0,2)        (7,0)|
       0004           |
       (0,0)  4       |
       [3,0]1         |
1,2,3,4        5,6,7,8|
(3,0,0)        (4,0)  |
       0004         |
       (0,0)  4     |
       [3,0]1       |
1,2,3,4        5,6,7|
(3,0,0)        (4,0)|
       0004         |
       (0,0)  4     |
       [3,0]1       |
1,2,3,4        5,6  |
(3,0,0)        (4,0)|
       0004         |
       (0,0)  4     |
       [3,0]1       |
1,2,3,4        5    |
(3,0,0)        (4,0)|
1,2,3,4|
1,2,3|
1,2|
1|
""");

        scDieAreaX = 700; scDieAreaY = 700;
        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_deleteDescending()
   {          test_deleteDescending(true);
              test_deleteDescending(false);
   }

  static void test_deleteRandom32(boolean Ex)
   {sayCurrentTestName();
    final int  N = random_32.length;

    final Tree t = new Tree(new Build().maxLeafSize(4).maxBranchSize(3).numberOfNodes(N).immediate(Ex))
     {void treeCode()
       {new ForCount(One, new Int(N+1))
         {void body(Int Index)
           {insert(Index, Index.Mul(11));
           }
         };
        dumpProgramState("AAAA");

        final StringBuilder s = Ex ? printTree() : null;
        final StringBuilder m = new StringBuilder();
        final VerilogArrays.Array a = verilogArrays().new Array("loadRandomKeys", random_32);                           // Create an array of the random keys to be deleted so that the array is accessible from Verilog

        new ForCount(new Int(N))
         {void body(Int Index)
           {final Int k = new Int("Key", Index);

            k.S();                                                                                                      // Load index of item we want
            new I()
             {void        a() {k.targetInt(random_32[Index.i()]); k.setValid();}
              String      v() {return intMemory().writeInt() + " <= "+a.dataRegisterName()+";";}                        // Translate index into key
              boolean trace() {return false;}
             };
            k.TW();                                                                                                     // Write key into variable
            delete(k);

            if (Ex) s.append(printTree());
            new I() {void a() {m.append(mainMemoryMd5Sum()+"\n");} boolean trace() {return false;}};
            dumpProgramState("BBBB");
           }
         };

        //stop(m);
        ok(()->m, """
f309667df3e9a927ec09dcd37fc7180d
b8c8c5d3fa2ef1d6cd6c20aa3580750a
12158bbdd82e6ed9a2d13804be750214
01e54f7ef68c6848edab27f6b8262494
9514dbc51f9d325a66bca9ed945169f2
3f7ae8c29cd74499533c692edb2e9dc0
7a789cb7832cf4720d7d61f408ba3f14
082770ab75a51c968f8b4333d5539a52
e415f1865af14d8a55b591cbda5f9b02
cff192a7b610230eef567cd71686d701
083f565fe2e499ab35d17c74f296a69b
c1cfb44cedd5682485cec9f76cdacd44
e2e4a119668b9dbbecd13cceb6376302
7cc737f30737fda2532913e68ca0309c
1bd8f1aa11748c8253adfe3dfaee1226
03ad656373de4431ee3d98e7afff8308
343ae3bc595a85378066e061bba627dc
96d79021c909b6dd853bb90d949295fc
0ea9452e5f04042c573152eff758446b
5579c5faad8a08b34f00f9647f294e5b
3e37ad0ae232fe336b7dc724dfcdeef1
78cc85ac0e4010407082984afee3e981
f1cfb3ee433234d7db18288f21d8a1e1
495e39cc3320fcf5aec7785056035e6a
dc65fbc9ef5ee017bfee6ef3a08fc2cc
ffedd3c26b6dbf87eb5a6b13bc9089fa
9d07c3c0b23c03f06419bc0de281bf03
17abbcc9c1158e9783fbe96b13425880
43118ac4441c452a3e814735294cc997
dccbfa70b58e9d65597159a702c6e62d
787947323df6e5a1b1ade879bb050280
1cadfad6c47722b79979e3bb8d575ef4
""");

        //stop(s);
        if (Ex) ok(s, """
                                                          0016                                                                       |
                                                          (0,2)  5                                                                   |
                                                          [9,2]2                                                                     |
       0004          0008             0012                                   0020              0024              0028                |
       (9,0,0)       (9,0,2)          (9,0,4) 8                              (5,0,0)           (5,0,2)           (5,0,4) 2           |
       [3,0]1        [4,2]1           [7,4]1                                 [10,0]1           [6,2]1            [12,4]1             |
1,2,3,4       5,6,7,8       9,10,11,12         13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
(3,9,0)       (4,9,2)       (7,9,4)            (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
                                                        0016                                                                       |
                                                        (0,2)  5                                                                   |
                                                        [9,2]2                                                                     |
       {0004         0008          0011   }                                0020              0024              0028                |
       (9,0,0)       (9,0,2)       (9,0,4)  8                              (5,0,0)           (5,0,2)           (5,0,4) 2           |
       [3,0]1        [4,2]1        [7,4]1                                  [10,0]1           [6,2]1            [12,4]1             |
1,2,3,4       5,6,7,8       9,10,11          13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
(3,9,0)       (4,9,2)       (7,9,4)          (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
                                                       0016                                                                       |
                                                       (0,2)  5                                                                   |
                                                       [9,2]2                                                                     |
       0004          0008          0011                                   0020              0024              0028                |
       (9,0,0)       (9,0,2)       (9,0,4) 8                              (5,0,0)           (5,0,2)           (5,0,4) 2           |
       [3,0]1        [4,2]1        [7,4]1                                 [10,0]1           [6,2]1            [12,4]1             |
1,2,4         5,6,7,8       9,10,11         13,14,15,16        17,18,19,20       21,22,23,24       25,26,27,28         29,30,31,32|
(3,9,0)       (4,9,2)       (7,9,4)         (8,9)              (10,5,0)          (6,5,2)           (12,5,4)            (2,5)      |
                                                       0016                                                                    |
                                                       (0,2)  5                                                                |
                                                       [9,2]2                                                                  |
       0004          0008          0011                                   0020              0024           0028                |
       (9,0,0)       (9,0,2)       (9,0,4) 8                              (5,0,0)           (5,0,2)        (5,0,4) 2           |
       [3,0]1        [4,2]1        [7,4]1                                 [10,0]1           [6,2]1         [12,4]1             |
1,2,4         5,6,7,8       9,10,11         13,14,15,16        17,18,19,20       21,22,23,24       25,26,28         29,30,31,32|
(3,9,0)       (4,9,2)       (7,9,4)         (8,9)              (10,5,0)          (6,5,2)           (12,5,4)         (2,5)      |
                                                       0016                                                                    |
                                                       (0,2)  5                                                                |
                                                       [9,2]2                                                                  |
       0004          0008          0011                                   0020              0024           0028                |
       (9,0,0)       (9,0,2)       (9,0,4) 8                              (5,0,0)           (5,0,2)        (5,0,4) 2           |
       [3,0]1        [4,2]1        [7,4]1                                 [10,0]1           [6,2]1         [12,4]1             |
2,4           5,6,7,8       9,10,11         13,14,15,16        17,18,19,20       21,22,23,24       25,26,28         29,30,31,32|
(3,9,0)       (4,9,2)       (7,9,4)         (8,9)              (10,5,0)          (6,5,2)           (12,5,4)         (2,5)      |
                                                       0016                                                                 |
                                                       (0,2)  5                                                             |
                                                       [9,2]2                                                               |
       0004          0008          0011                                   0020           0024           0028                |
       (9,0,0)       (9,0,2)       (9,0,4) 8                              (5,0,0)        (5,0,2)        (5,0,4) 2           |
       [3,0]1        [4,2]1        [7,4]1                                 [10,0]1        [6,2]1         [12,4]1             |
2,4           5,6,7,8       9,10,11         13,14,15,16        17,18,19,20       21,22,24       25,26,28         29,30,31,32|
(3,9,0)       (4,9,2)       (7,9,4)         (8,9)              (10,5,0)          (6,5,2)        (12,5,4)         (2,5)      |
                                                       0016                                                               |
                                                       (0,2)  5                                                           |
                                                       [9,2]2                                                             |
       0004          0008          0011                                {0019          0024           0028   }             |
       (9,0,0)       (9,0,2)       (9,0,4) 8                           (5,0,0)        (5,0,2)        (5,0,4)  2           |
       [3,0]1        [4,2]1        [7,4]1                              [10,0]1        [6,2]1         [12,4]1              |
2,4           5,6,7,8       9,10,11         13,14,15,16        17,18,19       21,22,24       25,26,28          29,30,31,32|
(3,9,0)       (4,9,2)       (7,9,4)         (8,9)              (10,5,0)       (6,5,2)        (12,5,4)          (2,5)      |
                                                        0016                                                              |
                                                        (0,2)  5                                                          |
                                                        [9,2]2                                                            |
       {0004         0007          0011   }                             0019           0024           0028                |
       (9,0,0)       (9,0,2)       (9,0,4)  8                           (5,0,0)        (5,0,2)        (5,0,4) 2           |
       [3,0]1        [4,2]1        [7,4]1                               [10,0]1        [6,2]1         [12,4]1             |
2,4           5,6,7         9,10,11          13,14,15,16        17,18,19       21,22,24       25,26,28         29,30,31,32|
(3,9,0)       (4,9,2)       (7,9,4)          (8,9)              (10,5,0)       (6,5,2)        (12,5,4)         (2,5)      |
                                                       0016                                                              |
                                                       (0,2)  5                                                          |
                                                       [9,2]2                                                            |
       0004          0007          0011                                0019           0024           0028                |
       (9,0,0)       (9,0,2)       (9,0,4) 8                           (5,0,0)        (5,0,2)        (5,0,4) 2           |
       [3,0]1        [4,2]1        [7,4]1                              [10,0]1        [6,2]1         [12,4]1             |
2,4           5,6,7         9,10,11         13,14,15,16        17,19          21,22,24       25,26,28         29,30,31,32|
(3,9,0)       (4,9,2)       (7,9,4)         (8,9)              (10,5,0)       (6,5,2)        (12,5,4)         (2,5)      |
                                         0016                                                              |
                                         (0,2)  5                                                          |
                                         [9,2]2                                                            |
       0007          0011                                0019           0024           0028                |
       (9,0,2)       (9,0,4) 8                           (5,0,0)        (5,0,2)        (5,0,4) 2           |
       [4,2]1        [7,4]1                              [10,0]1        [6,2]1         [12,4]1             |
4,5,6,7       9,10,11         13,14,15,16        17,19          21,22,24       25,26,28         29,30,31,32|
(4,9,2)       (7,9,4)         (8,9)              (10,5,0)       (6,5,2)        (12,5,4)         (2,5)      |
                                         0016                                                           |
                                         (0,2)  5                                                       |
                                         [9,2]2                                                         |
       0007          0011                                0019           0024           0028             |
       (9,0,2)       (9,0,4) 8                           (5,0,0)        (5,0,2)        (5,0,4) 2        |
       [4,2]1        [7,4]1                              [10,0]1        [6,2]1         [12,4]1          |
4,5,6,7       9,10,11         13,14,15,16        17,19          21,22,24       25,26,28         29,30,32|
(4,9,2)       (7,9,4)         (8,9)              (10,5,0)       (6,5,2)        (12,5,4)         (2,5)   |
                                         0016                                                           |
                                         (0,2)  5                                                       |
                                         [9,2]2                                                         |
       0007          0011                                0019           0024           0028             |
       (9,0,2)       (9,0,4) 8                           (5,0,0)        (5,0,2)        (5,0,4) 2        |
       [4,2]1        [7,4]1                              [10,0]1        [6,2]1         [12,4]1          |
4,5,6,7       9,10,11         13,14,15,16        17,19          21,22,24       26,28            29,30,32|
(4,9,2)       (7,9,4)         (8,9)              (10,5,0)       (6,5,2)        (12,5,4)         (2,5)   |
                                      {0015 }                                                         |
                                      (0,2)   5                                                       |
                                      [9,2]2                                                          |
       0007          0011                              0019           0024           0028             |
       (9,0,2)       (9,0,4) 8                         (5,0,0)        (5,0,2)        (5,0,4) 2        |
       [4,2]1        [7,4]1                            [10,0]1        [6,2]1         [12,4]1          |
4,5,6,7       9,10,11         13,14,15         17,19          21,22,24       26,28            29,30,32|
(4,9,2)       (7,9,4)         (8,9)            (10,5,0)       (6,5,2)        (12,5,4)         (2,5)   |
                                   0015                                                           |
                                   (0,2)  5                                                       |
                                   [9,2]2                                                         |
       0007          0011                          0019           0024           0028             |
       (9,0,2)       (9,0,4) 8                     (5,0,0)        (5,0,2)        (5,0,4) 2        |
       [4,2]1        [7,4]1                        [10,0]1        [6,2]1         [12,4]1          |
4,5,6,7       9,10,11         14,15        17,19          21,22,24       26,28            29,30,32|
(4,9,2)       (7,9,4)         (8,9)        (10,5,0)       (6,5,2)        (12,5,4)         (2,5)   |
                                   0015                                               |
                                   (0,2)  5                                           |
                                   [9,2]2                                             |
       0007          0011                          0019           0024                |
       (9,0,2)       (9,0,4) 8                     (5,0,0)        (5,0,2) 2           |
       [4,2]1        [7,4]1                        [10,0]1        [6,2]1              |
4,5,6,7       9,10,11         14,15        17,19          21,22,24         26,28,29,30|
(4,9,2)       (7,9,4)         (8,9)        (10,5,0)       (6,5,2)          (2,5)      |
                          0015                                               |
                          (0,2)  5                                           |
                          [9,2]2                                             |
       0007                               0019           0024                |
       (9,0,2) 8                          (5,0,0)        (5,0,2) 2           |
       [4,2]1                             [10,0]1        [6,2]1              |
4,5,6,7         9,10,14,15        17,19          21,22,24         26,28,29,30|
(4,9,2)         (8,9)             (10,5,0)       (6,5,2)          (2,5)      |
       0007            0015             0024               |
       (0,0)           (0,2)            (0,4)  2           |
       [4,0]1          [8,2]1           [6,4]1             |
4,5,6,7      9,10,14,15      17,19,22,24        26,28,29,30|
(4,0,0)      (8,0,2)         (6,0,4)            (2,0)      |
       0007            0015             0024               |
       (0,0)           (0,2)            (0,4)  2           |
       [4,0]1          [8,2]1           [6,4]1             |
4,6,7        9,10,14,15      17,19,22,24        26,28,29,30|
(4,0,0)      (8,0,2)         (6,0,4)            (2,0)      |
       {0007           0015          0022  }             |
       (0,0)           (0,2)         (0,4)   2           |
       [4,0]1          [8,2]1        [6,4]1              |
4,6,7        9,10,14,15      17,19,22         26,28,29,30|
(4,0,0)      (8,0,2)         (6,0,4)          (2,0)      |
       0007            0015          0022               |
       (0,0)           (0,2)         (0,4)  2           |
       [4,0]1          [8,2]1        [6,4]1             |
6,7          9,10,14,15      17,19,22        26,28,29,30|
(4,0,0)      (8,0,2)         (6,0,4)         (2,0)      |
       0007         0015          0022               |
       (0,0)        (0,2)         (0,4)  2           |
       [4,0]1       [8,2]1        [6,4]1             |
6,7          9,14,15      17,19,22        26,28,29,30|
(4,0,0)      (8,0,2)      (6,0,4)         (2,0)      |
       0007         0015          0022            |
       (0,0)        (0,2)         (0,4)  2        |
       [4,0]1       [8,2]1        [6,4]1          |
6,7          9,14,15      17,19,22        28,29,30|
(4,0,0)      (8,0,2)      (6,0,4)         (2,0)   |
       0007         0015          0022         |
       (0,0)        (0,2)         (0,4)  2     |
       [4,0]1       [8,2]1        [6,4]1       |
6,7          9,14,15      17,19,22        28,29|
(4,0,0)      (8,0,2)      (6,0,4)         (2,0)|
         0015          0022         |
         (0,2)         (0,4)  2     |
         [8,2]1        [6,4]1       |
6,7,14,15      17,19,22        28,29|
(8,0,2)        (6,0,4)         (2,0)|
       0015          0022         |
       (0,2)         (0,4)  2     |
       [8,2]1        [6,4]1       |
7,14,15      17,19,22        28,29|
(8,0,2)      (6,0,4)         (2,0)|
       0015               |
       (0,2)  2           |
       [8,2]1             |
7,14,15        17,19,22,28|
(8,0,2)        (2,0)      |
       0015            |
       (0,2)  2        |
       [8,2]1          |
7,14,15        19,22,28|
(8,0,2)        (2,0)   |
       0015         |
       (0,2)  2     |
       [8,2]1       |
7,14,15        19,22|
(8,0,2)        (2,0)|
7,14,19,22|
7,19,22|
7,22|
22|
""");

        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_deleteRandom32()
   {          test_deleteRandom32(true);
              test_deleteRandom32(false);
   }

  static void test_update(boolean Ex)
   {sayCurrentTestName();
    final int N = 16;
    final Tree t = new Tree(new Build().maxLeafSize(2).maxBranchSize(3).numberOfNodes(N).immediate(Ex))
     {void treeCode()
       {new ForCount(2)
         {void body(Int I)
           {new ForCount(N)
             {void body(Int J)
               {insert(J, J.Inc());
               }
             };
           }
         };
        //stop(mainMemoryMd5Sum());
        ok(()->mainMemoryMd5Sum(), "6f4745a2d16346e474fbedf82660ad54");

        //stop(dumpTree());
        if (Ex) ok(dumpTree(), """
Tree memory dump
Leaf   size   :   23
Branch size   :   34
Node   size   :   64
MaxLeafSize   :    2
MaxBranchSize :    3
NumberOfNodes :   16
Allocations   :   11
Number of Keys:   16
Height        :    3
Branch         size:   3, count:   1, top:   5, level:   2
 Ref   Key  Data
   1     7     9
Leaf   at:   2 size:   2, count:   2
 Ref   Key  Data
   0    14    15
   1    15    16
Leaf   at:   3 size:   2, count:   2
 Ref   Key  Data
   0     0     1
   1     1     2
Leaf   at:   4 size:   2, count:   2
 Ref   Key  Data
   0     2     3
   1     3     4
Branch at:   5 size:   3, count:   3, top:   2, level:   1
 Ref   Key  Data
   0     9    10
   1    11     6
   2    13    12
Leaf   at:   6 size:   2, count:   2
 Ref   Key  Data
   0    10    11
   1    11    12
Leaf   at:   7 size:   2, count:   2
 Ref   Key  Data
   0     4     5
   1     5     6
Leaf   at:   8 size:   2, count:   2
 Ref   Key  Data
   0     6     7
   1     7     8
Branch at:   9 size:   3, count:   3, top:   8, level:   1
 Ref   Key  Data
   0     1     3
   1     3     4
   2     5     7
Leaf   at:  10 size:   2, count:   2
 Ref   Key  Data
   0     8     9
   1     9    10
Leaf   at:  12 size:   2, count:   2
 Ref   Key  Data
   0    12    13
   1    13    14
""");
        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_update()
   {          test_update(true);
              test_update(false);
   }

  static void test_find(boolean Ex)
   {sayCurrentTestName();
    final int  N = 32;
    final Tree t = new Tree(new Build().maxLeafSize(4).maxBranchSize(3).numberOfNodes(N).immediate(Ex));
    t.new ForCount(t.One, t.new Int(N+1))
     {void body(Int Index)
       {t.insert(Index, Index.Mul(11));
       }
     };

    t.new ForCount(t.new Int(N+2))
     {void body(Int Index)
       {final Bint d = t.find(Index);
        t.new If (d)
         {void Then() {d.ok(Index.Mul(11));}
          void Else() {d.ok(false);}
         };
       }
     };

    t.dumpProgramState("AAAA");
    t.maxSteps(9_999_999);
    t.execute();
   }

  static void test_find()
   {          test_find(true);
              test_find(false);
   }

  static void test_rootFanOut (boolean Ex)
   {sayCurrentTestName();

    final int  N = 128;
    final Tree t = new Tree(new Build().maxLeafSize(2).maxBranchSize(9).numberOfNodes(N).immediate(Ex))
     {void treeCode()
       {new ForCount(One, new Int(N+1))
         {void body(Int Index)
           {insert(Index, Index.Mul(11));
            dumpProgramState("AAAA");
           }
         };

        height().ok(3);

        //stop(mainMemoryMd5Sum());
        ok(()->mainMemoryMd5Sum(), "47ddfd3a563175cc43e2a1984c305024");

        //stop(printTree());
        if (Ex) ok(printTree(), """
                                                                                                                                                                    0020                                                                                                                                                                              0040                                                                                                                                                                              0060                                                                                                                                                                               0080                                                                                                                                                                               0100                                                                              0110                                                                                                                                                               |
                                                                                                                                                                    (0,2)                                                                                                                                                                             (0,6)                                                                                                                                                                             (0,10)                                                                                                                                                                             (0,15)                                                                                                                                                                             (0,16)                                                                            (0,17)  11                                                                                                                                                         |
                                                                                                                                                                    [18,2]2                                                                                                                                                                           [29,6]2                                                                                                                                                                           [40,10]2                                                                                                                                                                           [51,15]2                                                                                                                                                                           [62,16]2                                                                          [1,17]2                                                                                                                                                            |
        0002            0004            0006            0008            0010             0012              0014               0016               0018                               0022             0024             0026             0028             0030              0032               0034               0036               0038                               0042             0044             0046             0048             0050              0052               0054               0056               0058                                0062             0064             0066             0068             0070              0072               0074               0076               0078                                0082             0084             0086             0088             0090              0092               0094               0096               0098                               0102           0104            0106             0108                                 0112             0114             0116             0118              0120               0122               0124               0126              |
        (18,0,0)        (18,0,2)        (18,0,4)        (18,0,6)        (18,0,8)         (18,0,10)         (18,0,12)          (18,0,14)          (18,0,16) 14                       (29,0,0)         (29,0,2)         (29,0,4)         (29,0,6)         (29,0,8)          (29,0,10)          (29,0,12)          (29,0,14)          (29,0,16) 25                       (40,0,0)         (40,0,2)         (40,0,4)         (40,0,6)         (40,0,8)          (40,0,10)          (40,0,12)          (40,0,14)          (40,0,16) 36                        (51,0,0)         (51,0,2)         (51,0,4)         (51,0,6)         (51,0,8)          (51,0,10)          (51,0,12)          (51,0,14)          (51,0,16) 47                        (62,0,0)         (62,0,2)         (62,0,4)         (62,0,6)         (62,0,8)          (62,0,10)          (62,0,12)          (62,0,14)          (62,0,16) 58                       (1,0,2)        (1,0,6)         (1,0,10)         (1,0,14) 56                          (11,0,2)         (11,0,4)         (11,0,6)         (11,0,8)          (11,0,10)          (11,0,12)          (11,0,15)          (11,0,17) 2       |
        [3,0]1          [4,2]1          [5,4]1          [6,6]1          [7,8]1           [8,10]1           [9,12]1            [10,14]1           [13,16]1                           [15,0]1          [16,2]1          [17,4]1          [19,6]1          [12,8]1           [20,10]1           [21,12]1           [22,14]1           [24,16]1                           [26,0]1          [27,2]1          [28,4]1          [30,6]1          [23,8]1           [31,10]1           [32,12]1           [33,14]1           [35,16]1                            [37,0]1          [38,2]1          [39,4]1          [41,6]1          [34,8]1           [42,10]1           [43,12]1           [44,14]1           [46,16]1                            [48,0]1          [49,2]1          [50,4]1          [52,6]1          [45,8]1           [53,10]1           [54,12]1           [55,14]1           [57,16]1                           [59,2]1        [60,6]1         [61,10]1         [63,14]1                             [64,2]1          [65,4]1          [66,6]1          [68,8]1           [69,10]1           [70,12]1           [71,15]1           [72,17]1          |
1,2             3,4             5,6             7,8             9,10            11,12             13,14             15,16              17,18                 19,20         21,22            23,24            25,26            27,28            29,30            31,32              33,34              35,36              37,38                 39,40         41,42            43,44            45,46            47,48            49,50            51,52              53,54              55,56              57,58                 59,60          61,62            63,64            65,66            67,68            69,70            71,72              73,74              75,76              77,78                 79,80          81,82            83,84            85,86            87,88            89,90            91,92              93,94              95,96              97,98                 99,100         101,102        103,104        105,106          107,108             109,110          111,112          113,114          115,116          117,118          119,120            121,122            123,124            125,126              127,128|
(3,18,0)        (4,18,2)        (5,18,4)        (6,18,6)        (7,18,8)        (8,18,10)         (9,18,12)         (10,18,14)         (13,18,16)            (14,18)       (15,29,0)        (16,29,2)        (17,29,4)        (19,29,6)        (12,29,8)        (20,29,10)         (21,29,12)         (22,29,14)         (24,29,16)            (25,29)       (26,40,0)        (27,40,2)        (28,40,4)        (30,40,6)        (23,40,8)        (31,40,10)         (32,40,12)         (33,40,14)         (35,40,16)            (36,40)        (37,51,0)        (38,51,2)        (39,51,4)        (41,51,6)        (34,51,8)        (42,51,10)         (43,51,12)         (44,51,14)         (46,51,16)            (47,51)        (48,62,0)        (49,62,2)        (50,62,4)        (52,62,6)        (45,62,8)        (53,62,10)         (54,62,12)         (55,62,14)         (57,62,16)            (58,62)        (59,1,2)       (60,1,6)       (61,1,10)        (63,1,14)           (56,1)           (64,11,2)        (65,11,4)        (66,11,6)        (68,11,8)        (69,11,10)         (70,11,12)         (71,11,15)         (72,11,17)           (2,11) |
""");

        //stop(showTree());
        if (Ex) ok(showTree(), """
                                                                             0020                                                                                      0040                                                                                      0060                                                                                      0080                                                                                       0100                                                   0110                                                                                               |
   0002   0004   0006   0008    0010     0012     0014     0016     0018              0022     0024     0026     0028     0030     0032     0034     0036     0038              0042     0044     0046     0048     0050     0052     0054     0056     0058              0062     0064     0066     0068     0070     0072     0074     0076     0078              0082     0084     0086     0088     0090     0092     0094     0096     0098                 0102       0104       0106       0108                  0112       0114       0116       0118       0120       0122       0124       0126       |
1,2    3,4    5,6    7,8    9,10    11,12    13,14    15,16    17,18    19,20    21,22    23,24    25,26    27,28    29,30    31,32    33,34    35,36    37,38    39,40    41,42    43,44    45,46    47,48    49,50    51,52    53,54    55,56    57,58    59,60    61,62    63,64    65,66    67,68    69,70    71,72    73,74    75,76    77,78    79,80    81,82    83,84    85,86    87,88    89,90    91,92    93,94    95,96    97,98    99,100    101,102    103,104    105,106    107,108    109,110    111,112    113,114    115,116    117,118    119,120    121,122    123,124    125,126    127,128|
""");

        maxSteps(999_999_999);
        execute();
       }
     };
   }

  static void test_rootFanOut ()
   {          test_rootFanOut(true);
              test_rootFanOut(false);
   }

  static void test_rootFanOut2 (boolean Ex)
   {sayCurrentTestName();

    final int  N = 128;
    final Tree t = new Tree(new Build().maxLeafSize(8).maxBranchSize(9).rootFanLevels(4).branchFanOut(3).leafFanOut(2).numberOfNodes(N).immediate(Ex))
     {void treeCode()
       {new ForCount(One, new Int(N+1))
         {void body(Int Index)
           {insert(Index, Index.Mul(11));
            dumpProgramState("AAAA");
           }
         };

        height().ok(4);

        //stop(mainMemoryMd5Sum());
        ok(()->mainMemoryMd5Sum(), "8f2b871a363574249a86cc692c39a841");

        //stop(printTree());
        if (Ex) ok(printTree(), """
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           0054                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           (0,9)   77                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           [76,9]4                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
                                                                                                                                                           0018                                                                                                                                                                          0036                                                                                                                                                                                                                                                                                                                                                              0072                                                                                                                                                                          0090                                                                                                                                                                                                                                                     |
                                                                                                                                                           (76,0,4)                                                                                                                                                                      (76,0,13) 50                                                                                                                                                                                                                                                                                                                                                      (77,0,8)                                                                                                                                                                      (77,0,13) 24                                                                                                                                                                                                                                             |
                                                                                                                                                           [23,4]3                                                                                                                                                                       [37,13]3                                                                                                                                                                                                                                                                                                                                                          [63,8]3                                                                                                                                                                       [78,13]3                                                                                                                                                                                                                                                 |
                                       0006                                                   0012                                                                                                                 0024                                                     0030                                                                                                                     0042                                                     0048                                                                                                                   0060                                                     0066                                                                                                                 0078                                                     0084                                                                                                                     0096                                                                    0106                                                                                                                 |
                                       (23,76,4)                                              (23,76,13) 15                                                                                                        (37,76,4)                                                (37,76,13) 29                                                                                                            (50,76,4)                                                (50,76,13) 42                                                                                                          (63,77,4)                                                (63,77,13) 55                                                                                                        (78,77,4)                                                (78,77,13) 68                                                                                                            (24,77,8)                                                               (24,77,13) 7                                                                                                         |
                                       [6,4]2                                                 [11,13]2                                                                                                             [19,4]2                                                  [25,13]2                                                                                                                 [33,4]2                                                  [38,13]2                                                                                                               [46,4]2                                                  [51,13]2                                                                                                             [59,4]2                                                  [64,13]2                                                                                                                 [72,8]2                                                                 [79,13]2                                                                                                             |
       0002            0004                             0008              0010                                      0014               0016                                 0020               0022                                  0026               0028                                      0032               0034                                     0038               0040                                  0044               0046                                      0050               0052                                   0056               0058                                  0062               0064                                      0068               0070                                 0074               0076                                  0080               0082                                      0086               0088                                     0092               0094                                  0098               0100                                                                          0114                                    0122                              |
       (6,23,4)        (6,23,13) 5                      (11,23,4)         (11,23,13) 10                             (15,23,4)          (15,23,13) 14                        (19,37,4)          (19,37,13) 18                         (25,37,4)          (25,37,13) 22                             (29,37,4)          (29,37,13) 28                            (33,50,4)          (33,50,13) 32                         (38,50,4)          (38,50,13) 36                             (42,50,4)          (42,50,13) 41                          (46,63,4)          (46,63,13) 45                         (51,63,4)          (51,63,13) 49                             (55,63,4)          (55,63,13) 54                        (59,78,4)          (59,78,13) 58                         (64,78,4)          (64,78,13) 62                             (68,78,4)          (68,78,13) 67                            (72,24,4)          (72,24,13) 71                         (79,24,4)          (79,24,13) 1                                                                  (7,24,13)                               (7,24,17) 2                       |
       [3,4]1          [4,13]1                          [8,4]1            [9,13]1                                   [12,4]1            [13,13]1                             [16,4]1            [17,13]1                              [20,4]1            [21,13]1                                  [26,4]1            [27,13]1                                 [30,4]1            [31,13]1                              [34,4]1            [35,13]1                                  [39,4]1            [40,13]1                               [43,4]1            [44,13]1                              [47,4]1            [48,13]1                                  [52,4]1            [53,13]1                             [56,4]1            [57,13]1                              [60,4]1            [61,13]1                                  [65,4]1            [66,13]1                                 [69,4]1            [70,13]1                              [73,4]1            [74,13]1                                                                      [80,13]1                                [81,17]1                          |
1,2            3,4                5,6           7,8              9,10                  11,12               13,14             15,16                  17,18          19,20             21,22                  23,24           25,26             27,28                  29,30               31,32             33,34                  35,36              37,38             39,40                  41,42           43,44             45,46                  47,48               49,50             51,52                  53,54            55,56             57,58                  59,60           61,62             63,64                  65,66               67,68             69,70                  71,72          73,74             75,76                  77,78           79,80             81,82                  83,84               85,86             87,88                  89,90              91,92             93,94                  95,96           97,98             99,100                101,102,103,104,105,106            107,108,109,110,111,112,113,114         115,116,117,118,119,120,121,122           123,124,125,126,127,128|
(3,6,4)        (4,6,13)           (5,6)         (8,11,4)         (9,11,13)             (10,11)             (12,15,4)         (13,15,13)             (14,15)        (16,19,4)         (17,19,13)             (18,19)         (20,25,4)         (21,25,13)             (22,25)             (26,29,4)         (27,29,13)             (28,29)            (30,33,4)         (31,33,13)             (32,33)         (34,38,4)         (35,38,13)             (36,38)             (39,42,4)         (40,42,13)             (41,42)          (43,46,4)         (44,46,13)             (45,46)         (47,51,4)         (48,51,13)             (49,51)             (52,55,4)         (53,55,13)             (54,55)        (56,59,4)         (57,59,13)             (58,59)         (60,64,4)         (61,64,13)             (62,64)             (65,68,4)         (66,68,13)             (67,68)            (69,72,4)         (70,72,13)             (71,72)         (73,79,4)         (74,79,13)            (1,79)                             (80,7,13)                               (81,7,17)                                 (2,7)                  |
""");

        //stop(showTree());
        if (Ex) ok(showTree(), """
                                                                                                                                                                                                                                      0054                                                                                                                                                                                                                                                                                                                                        |
                                                                    0018                                                                             0036                                                                                                                                                              0072                                                                             0090                                                                                                                                                                      |
                 0006                    0012                                                  0024                       0030                                                  0042                       0048                                                  0060                       0066                                                  0078                       0084                                                  0096                                          0106                                                                                             |
   0002   0004          0008    0010              0014     0016              0020     0022              0026     0028              0032     0034              0038     0040              0044     0046              0050     0052              0056     0058              0062     0064              0068     0070              0074     0076              0080     0082              0086     0088              0092     0094              0098      0100                                                          0114                               0122                       |
1,2    3,4    5,6    7,8    9,10    11,12    13,14    15,16    17,18    19,20    21,22    23,24    25,26    27,28    29,30    31,32    33,34    35,36    37,38    39,40    41,42    43,44    45,46    47,48    49,50    51,52    53,54    55,56    57,58    59,60    61,62    63,64    65,66    67,68    69,70    71,72    73,74    75,76    77,78    79,80    81,82    83,84    85,86    87,88    89,90    91,92    93,94    95,96    97,98    99,100    101,102,103,104,105,106    107,108,109,110,111,112,113,114    115,116,117,118,119,120,121,122    123,124,125,126,127,128|
""");

        maxSteps(999_999_999);
        execute();
       }
     };
   }

  static void test_rootFanOut2 ()
   {          test_rootFanOut2(true);
              test_rootFanOut2(false);
   }


  static void test_rootFanOut3 (boolean Ex)
   {sayCurrentTestName();
    //if (true) return;                                                                                                   // Not sure what this test proves
    final int  N = 256;
    final Tree t = new Tree(new Build().maxLeafSize(8).maxBranchSize(9).rootFanLevels(2).branchFanOut(3).leafFanOut(2).numberOfNodes(N).immediate(Ex))
     {void treeCode()
       {new ForCount(One, new Int(N+1))
         {void body(Int Index)
           {insert(Index, Index.Mul(11));
            dumpProgramState("AAAA");
           }
         };

        height().ok(3);

        //stop(mainMemoryMd5Sum());
        ok(()->mainMemoryMd5Sum(), "17021c6287b0beab0185c51e5064c45e");

        //stop(printTree());
        if (Ex) ok(printTree(), """
                                                                                                                                                                                                      0046                                                                                                                                                                                                                                                                     0110                                                                                                                                                                                                                                                                                                                          0174                                                                                                                                                                                                0214                                                                                                                                                                                                               |
                                                                                                                                                                                                      (0,14)                                                                                                                                                                                                                                                                   (0,15)                                                                                                                                                                                                                                                                                                                        (0,16)                                                                                                                                                                                              (0,17)  6                                                                                                                                                                                                          |
                                                                                                                                                                                                      [14,14]2                                                                                                                                                                                                                                                                 [23,15]2                                                                                                                                                                                                                                                                                                                      [32,16]2                                                                                                                                                                                            [1,17]2                                                                                                                                                                                                            |
        0002            0004            0006                        0014                           0022                            0030                            0038                                                              0054                           0062                           0070                           0078                           0086                            0094                               0102                                                                              0118                                   0126                                   0134                                   0142                                   0150                                    0158                                    0166                                                                              0182                                  0190                                  0198                                   0206                                                                              0222                                  0230                                  0238                                   0246                   0250                             |
        (14,0,2)        (14,0,4)        (14,0,6)                    (14,0,8)                       (14,0,10)                       (14,0,12)                       (14,0,14) 11                                                      (23,0,2)                       (23,0,4)                       (23,0,6)                       (23,0,8)                       (23,0,10)                       (23,0,12)                          (23,0,14) 21                                                                      (32,0,2)                               (32,0,4)                               (32,0,6)                               (32,0,8)                               (32,0,10)                               (32,0,12)                               (32,0,14) 30                                                                      (1,0,2)                               (1,0,6)                               (1,0,10)                               (1,0,14) 36                                                                       (6,0,2)                               (6,0,6)                               (6,0,10)                               (6,0,14)               (6,0,16) 2                       |
        [3,2]1          [4,4]1          [5,6]1                      [7,8]1                         [8,10]1                         [9,12]1                         [10,14]1                                                          [12,2]1                        [15,4]1                        [13,6]1                        [17,8]1                        [18,10]1                        [19,12]1                           [20,14]1                                                                          [22,2]1                                [24,4]1                                [16,6]1                                [26,8]1                                [27,10]1                                [28,12]1                                [29,14]1                                                                          [31,2]1                               [33,6]1                               [25,10]1                               [35,14]1                                                                          [37,2]1                               [38,6]1                               [39,10]1                               [40,14]1               [34,16]1                         |
1,2             3,4             5,6             7,8,9,10,11,12,13,14        15,16,17,18,19,20,21,22         23,24,25,26,27,28,29,30         31,32,33,34,35,36,37,38            39,40,41,42,43,44,45,46        47,48,49,50,51,52,53,54        55,56,57,58,59,60,61,62        63,64,65,66,67,68,69,70        71,72,73,74,75,76,77,78        79,80,81,82,83,84,85,86         87,88,89,90,91,92,93,94         95,96,97,98,99,100,101,102            103,104,105,106,107,108,109,110        111,112,113,114,115,116,117,118        119,120,121,122,123,124,125,126        127,128,129,130,131,132,133,134        135,136,137,138,139,140,141,142        143,144,145,146,147,148,149,150         151,152,153,154,155,156,157,158         159,160,161,162,163,164,165,166            167,168,169,170,171,172,173,174        175,176,177,178,179,180,181,182       183,184,185,186,187,188,189,190       191,192,193,194,195,196,197,198        199,200,201,202,203,204,205,206           207,208,209,210,211,212,213,214         215,216,217,218,219,220,221,222       223,224,225,226,227,228,229,230       231,232,233,234,235,236,237,238        239,240,241,242,243,244,245,246        247,248,249,250          251,252,253,254,255,256|
(3,14,2)        (4,14,4)        (5,14,6)        (7,14,8)                    (8,14,10)                       (9,14,12)                       (10,14,14)                         (11,14)                        (12,23,2)                      (15,23,4)                      (13,23,6)                      (17,23,8)                      (18,23,10)                      (19,23,12)                      (20,23,14)                            (21,23)                                (22,32,2)                              (24,32,4)                              (16,32,6)                              (26,32,8)                              (27,32,10)                              (28,32,12)                              (29,32,14)                                 (30,32)                                (31,1,2)                              (33,1,6)                              (25,1,10)                              (35,1,14)                                 (36,1)                                  (37,6,2)                              (38,6,6)                              (39,6,10)                              (40,6,14)                              (34,6,16)                (2,6)                  |
""");

//        stop(showTree());
//        if (Ex) ok(showTree(), """
//                                                                                                                       0036                                                                                                                                                                                                              0098                                                                                                                                                                                                                                                                                   0162                                                                                                                                                                                                                                                                                    0226                                                                                                                                |
//   0002   0004   0006              0012                 0018                 0024                 0030                                      0042                       0050                       0058                       0066                       0074                       0082                       0090                                                         0106                               0114                               0122                               0130                               0138                               0146                               0154                                                                  0170                               0178                               0186                               0194                               0202                               0210                               0218                                                                  0234                               0242                               0250                       |
//1,2    3,4    5,6    7,8,9,10,11,12    13,14,15,16,17,18    19,20,21,22,23,24    25,26,27,28,29,30    31,32,33,34,35,36    37,38,39,40,41,42    43,44,45,46,47,48,49,50    51,52,53,54,55,56,57,58    59,60,61,62,63,64,65,66    67,68,69,70,71,72,73,74    75,76,77,78,79,80,81,82    83,84,85,86,87,88,89,90    91,92,93,94,95,96,97,98    99,100,101,102,103,104,105,106    107,108,109,110,111,112,113,114    115,116,117,118,119,120,121,122    123,124,125,126,127,128,129,130    131,132,133,134,135,136,137,138    139,140,141,142,143,144,145,146    147,148,149,150,151,152,153,154    155,156,157,158,159,160,161,162    163,164,165,166,167,168,169,170    171,172,173,174,175,176,177,178    179,180,181,182,183,184,185,186    187,188,189,190,191,192,193,194    195,196,197,198,199,200,201,202    203,204,205,206,207,208,209,210    211,212,213,214,215,216,217,218    219,220,221,222,223,224,225,226    227,228,229,230,231,232,233,234    235,236,237,238,239,240,241,242    243,244,245,246,247,248,249,250    251,252,253,254,255,256|
//""");

        maxSteps(999_999_999);
        execute();
       }
     };
   }

  static void test_rootFanOut3 ()
   {          test_rootFanOut3(true);
            //test_rootFanOut3(false);                                                                                  // Github keeps running out of memory
   }

  static void test_traverse (boolean Ex)
   {sayCurrentTestName();

    final int  N = 16;
    final Tree t = new Tree(new Build().maxLeafSize(2).maxBranchSize(3).numberOfNodes(N).immediate(Ex))
     {void treeCode()
       {new Traverse()
         {void traverse(Int Index, Int Key, Int Data, Bit Continue)
           {if (immediate()) stop("Tree should be empty");
           }
         };

         new ForCount(One, new Int(N+1))
         {void body(Int Index)
           {insert(Index.Mul(10), Index.Mul(11));
            dumpProgramState("AAAA");
           }
         };

        //stop(mainMemoryMd5Sum());
        ok(()->mainMemoryMd5Sum(), "6cd747a27951e254b915a348dd0c4052");

        //stop(printTree());
        if (Ex) ok(printTree(), """
                                                 0080                                                         |
                                                 (0,2)  5                                                     |
                                                 [9,2]2                                                       |
       0020          0040          0060                          0100          0120           0140            |
       (9,0,0)       (9,0,2)       (9,0,4) 8                     (5,0,0)       (5,0,2)        (5,0,4) 2       |
       [3,0]1        [4,2]1        [7,4]1                        [10,0]1       [6,2]1         [12,4]1         |
10,20         30,40         50,60           70,80        90,100         110,120       130,140          150,160|
(3,9,0)       (4,9,2)       (7,9,4)         (8,9)        (10,5,0)       (6,5,2)       (12,5,4)         (2,5)  |
""");

        new Traverse()
         {void traverse(Int Index, Int Key, Int Data, Bit Continue)
           {Key .ok(Index.Add(1).mul(10));
            Data.ok(Index.Add(1).mul(11));
            freeAllLocks();                                                                                             // Unlock the leaf now we have read it
            Continue.set();
           }
         };

        maxSteps(9_999_999);
        execute();
       }
     };
   }

  static void test_traverse ()
   {          test_traverse(true);
              test_traverse(false);
   }

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {if (rtg( 1)) test_tree();
    if (rtg( 2)) test_insert();
    if (rtg( 3)) test_insertMerged();
    if (rtg( 4)) test_insertReverse();
    if (rtg( 5)) test_insertRandom32();
    if (rtg( 6)) test_deleteAscending();                                                                                // 68090 integers, 2374 fast integers, 24307 bits: with both For and ForCount in bitset with fast integer indexes.  Openroad takes hours with  fast integers. Die larger than 2000*2000?  Restricted fast integers to first 4 for loops in bitset:  68090 integers, 1366 fast integers, 24307 bits.
    if (rtg( 7)) test_deleteDescending();
    if (rtg( 8)) test_deleteRandom32();
    if (rtg( 9)) test_update();
    if (rtg(10)) test_find();
    //if (rtg(11)) test_rootFanOut();
    //if (rtg(12)) test_rootFanOut2();
    //if (rtg(13)) test_rootFanOut3();
    if (rtg(14)) test_traverse();
    if (rtg(15)) {};
    if (rtg(16)) {}
   }

  static void newTests()                                                                                                // Tests being worked on
   {//oldTests();
    test_deleteAscending(true);
   }

  public static void main(String[] args)                                                                                // Test if called as a program
   {testGroup = args.length > 0 ? args[0] : null;                                                                       // Test groups if supplied
    try                                                                                                                 // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {deleteAllFileInVerilogTestsFolder();                                                                              // Delete generated Verilog files created by a prior run of the current test
      if (github_action) oldTests(); else newTests();                                                                   // Tests to run
      //if (coverageAnalysis) coverageAnalysis(12);                                                                     // Coverage analysis
      //say(subPrint());
      printExecutionCoverageGlobal(4);                                                                                  // Find locations in the java code that generated instructions that were never tested
      testSummary();                                                                                                    // Summarize test results
      executionStatistics();                                                                                            // Program execution statistics
      System.exit(testsFailed);
     }
    catch(Exception e)                                                                                                  // Get a traceback in a format clickable in Geany
     {say(e);
      say(fullTraceBack(e));
      System.exit(1);
     }
   }
 }
// perl -M"MakeWithPerl" -e"MakeWithPerl::makeWithPerl" -I/home/phil/perl/cpan/MakeWithPerl/lib -- --run  "/home/phil/btreeList/Tree.java" --javaHome "/home/phil/btreeList"
