//----------------------------------------------------------------------------------------------------------------------
// Btree with stucks implemented as distributed slots
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
// Set all fields private that can be set private  etc.
// Fix comments
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;

class Tree extends Program                                                                                              // A tree that translates keys into values to be implemented as an application specific integrated circuit
 {final int             maxLeafSize;                                                                                    // The maximum number of entries in a leaf of the tree
  final int           maxBranchSize;                                                                                    // The maximum number of entries in a branch of the tree
  final BitSet            freeChain;                                                                                    // Nodes currently free
  final int           numberOfNodes;                                                                                    // Maximum number of leaves plus branches in this tree
  final int   maximumNumberOfLevels;                                                                                    // Maximum number of levels in tree to prevent runaways while debugging
  final int              sizeOfNode;                                                                                    // The size of each node in the tree: a node must be able to hold a branch or a leaf
  final UnitMemory.Ref     refNodes;                                                                                    // The nodes associated with this tree
  final UnitMemory.Ref refFreeChain;                                                                                    // The free chain for this tree
  final UnitMemory.Ref     refCount;                                                                                    // The number of keys in this tree
  final Build                 build;                                                                                    // Memory containing the tree base followed by the leaves and branches of the tree
  final int     linesToPrintABranch = 4;                                                                                // The number of lines required to print a branch
  boolean           suppressMergeUp = false;                                                                            // Suppress merge up during development
  final UnitMemory        mergePath;                                                                                    // Memory for the steps taken along the merge path - each integer corresponds to the location of a branch in the path from the root to the leaf that should contain the key
  final UnitMemory     traverseNode;                                                                                    // Memory to hold outstanding branches and leaves during a traverse
  final UnitMemory   traverseAction;                                                                                    // Memory to hold requested action against each branch during a traverse

//D1 Construction                                                                                                       // Construct and layout a tree

  final static class Build                                                                                              // Parameters describing a tree
   {boolean       immediate = true;                                                                                     // Immediate execution mode
    boolean           trace = true;                                                                                     // Trace execution
    int          branchSize;                                                                                            // Size of a branch
    int            leafSize;                                                                                            // Size of a leaf
    int            nodeSize;                                                                                            // Size of a node: a leaf or a branch whichever is bigger. By using fixed size memory allocation we greatly simplify memory allocation - so it is worth adjusting the branch and leaf sizes to be as equal as possible.
    Integer     maxLeafSize;
    Integer   maxBranchSize;
    Integer   numberOfNodes;
    Boolean         execute;
    BitSet.Build  freeChain;
    Branch.Build     branch;
    Leaf  .Build       leaf;
    int unitsNeededForNodes;                                                                                            // Bytes needed for all the nodes
    int unitsNeededForFree;                                                                                             // Bytes needed for free chain
    MemoryPositions memoryPositions;                                                                                    // Layout of memory

    Build     immediate (boolean Immediate    ) {immediate     = Immediate;     return this;}
    Build         trace (boolean Trace        ) {trace         = Trace;         return this;}
    Build   maxLeafSize (int     MaxLeafSize  ) {maxLeafSize   = MaxLeafSize  ; return this;}
    Build maxBranchSize (int     MaxBranchSize) {maxBranchSize = MaxBranchSize; return this;}
    Build numberOfNodes (int     NumberOfNodes) {numberOfNodes = NumberOfNodes; return this;}
    Build       execute (boolean Execute      ) {execute       = Execute;       return this;}

    Program.Build build()                                                                                               // Describe the program used to execute the tree algorithm
     {final Program.Build p = new Program.Build();                                                                      // Description of containing program
      freeChain             = new BitSet .Build().bitSize(numberOfNodes); freeChain.build();                            // Size of free chain
      branch                = new Branch .Build().maxSize(maxBranchSize); branch   .build();                            // Size of a branch
      leaf                  = new Leaf   .Build().maxSize(maxLeafSize)  ; leaf     .build();                            // Size of a leaf
      leafSize              = leaf.size();
      branchSize            = branch.size();
      nodeSize              = max(branchSize, leafSize);
      unitsNeededForNodes   = numberOfNodes * nodeSize;
      unitsNeededForFree    = freeChain.units();
      memoryPositions       = new MemoryPositions();

      p.memory(   size());
      p.immediate(immediate);
      p.trace(    trace);
      return p;
     }

    class MemoryPositions                                                                                               // Layout of memory
     {final int posNodes     = 0;                                                                                       // A tree consists of nodes: leaves and branches. This field tells us which one we have
      final int posFreeChain = posNodes     + unitsNeededForNodes;
      final int posCount     = posFreeChain + unitsNeededForFree;
      final int size         = posCount     + ib();
     }

    int size () {return memoryPositions.size;}                                                                          // Bytes needed for the slots
   }

  Tree(Build Build)                                                                                                     // Create the tree
   {super(Build.build());
    maxLeafSize   = Build.maxLeafSize   == null ?  4 : Build.maxLeafSize;                                               // The maximum number of entries in a leaf
    maxBranchSize = Build.maxBranchSize == null ?  3 : Build.maxBranchSize;                                             // The maximum number of entries in a branch
    numberOfNodes = Build.numberOfNodes == null ? 99 : Build.numberOfNodes;                                             // The maximum number of leaves and branches combined
    maximumNumberOfLevels = logTwo(numberOfNodes);                                                                      // The maximum number of levels needed to step down through the tree because it is so well balanced

    final String m  = "The maximum ";
    final String m1 = m + "leaf size must be 2 or more, not: "   +maxLeafSize;
    final String m2 = m + "branch size must be 3 or more, not: " +maxBranchSize;
    final String m3 = m + "branch size must be odd, not: "       +maxBranchSize;

    final boolean b1 = maxLeafSize       <  2;                                                                          // Size checks
    final boolean b2 = maxBranchSize     <  3;
    final boolean b3 = maxBranchSize % 2 == 0;

    if (b1 && !b2 && !b3) stop(m1); else if (b1) say(m1);                                                               // Check parameters and describe any errors
    if (b2        && !b3) stop(m2); else if (b2) say(m2);
    if (b3              ) stop(m3);
    build         = Build;                                                                                              // Keep the build for future reference
    sizeOfNode    = build.nodeSize;                                                                                     // Size of a node in the tree

    final UnitMemory.Ref unitMemoryRef = unitMemory.new Ref(0);                                                         // Memory used by tree
    refNodes       = unitMemoryRef.step(build.memoryPositions.posNodes);                                                 // Memory for nodes
    refFreeChain   = unitMemoryRef.step(build.memoryPositions.posFreeChain);                                             // Memory for free chain
    refCount       = unitMemoryRef.step(build.memoryPositions.posCount);                                                 // Memory for key count

    mergePath      = new UnitMemory(ib(mnl()));                                                                          // Memory for the steps taken along the merge path - each integer corresponds to the location of a branch in the path from the root to the leaf that should contain the key
    traverseNode   = new UnitMemory(ib(2*mnl()));                                                                        // Memory to hold outstanding branches and leaves in a traverse
    traverseAction = new UnitMemory(ib(2*mnl()));                                                                        // Memory to hold requested action against each branch in a traverse

    freeChain  = new BitSet(build.freeChain.memory(refFreeChain).parent(this));                                         // Memory for free chain
    for (int i = 0, N = numberOfNodes; i < N; ++i) freeChain.set(new Int(i));                                           // Initial free chain with root as an allocated leaf. Each active leaf or branch resides in a node of the tree allocated from the free chain. Using a single node size greatly simplifies memory management which is crucial in long running processes like database systems.
    leaf();                                                                                                             // Initialize the root as a leaf
    treeBody();
   }

  void     treeBody () {}                                                                                               // Override to apply code to the tree

  int   maxLeafSize () {return maxLeafSize;}                                                                            // Maximum size of a leaf
  int maxBranchSize () {return maxBranchSize;}                                                                          // Maximum size of a branch
  int numberOfNodes () {return numberOfNodes;}                                                                          // Maximum number of nodes in tree
  int           mnl () {return maximumNumberOfLevels;}                                                                  // Maximum number of levels

  Int      allocate ()                                                                                                  // Allocate a leaf or a branch using the first free node on the free chain
   {final Bint A = freeChain.firstOne();                                                                                // First element on free chain
    A.elseStop("No more leaves or branches available for allocation");                                                  // Out of memory check
    final Int a = new Int("index") .set(A);                                                                             // First element on free chain
    freeChain.clear(a);                                                                                                 // Remove indexed node from free chain
    final Int c = freeChain.count();
    return a;
   }

  void free (Locatable Free)                                                                                            // Free a leaf or a branch and invalidate its contents
   {final Bint a = Free.getLocation();
    //unitMemory.invalidate(nodeAddress(a.i()), sizeOfNode);                                                            // Invalidate the memory
    freeChain.set(a.i());
   }

  Bool isAllocated (Int Node) {return freeChain.getBit(Node).Flip();}                                                   // Check whether a node is allocated

  Int nodeAddress  (Int Node)                                                                                           // Convert an index to a byte address of node in memory
   {if (immediate())
     {Node.lt(0)            .stop("Node less than zero:", Node);                                                        // Check not less than zero
      Node.gt(numberOfNodes).stop("Node too big:",        Node);                                                        // Check in range
      final Bool f = freeChain.getBit(Node);                                                                            // Check not freed
      f.stop("Attempting to access a branch or leaf that has been freed:", Node);                                       // Complain if the node has been freed and not reallocated
     }
    return Node.Mul(sizeOfNode);                                                                                        // Actual byte position of this node in memory
   }

  enum BranchOrLeaf                                                                                                     // Branch or leaf
   {leaf(1), branch(2);
    private final int value;
    BranchOrLeaf (int Value) {value = Value;}
    int value()              {return value;}
   }

  Int  root()         {return new Int(0);}                                                                              // The root is always at node zero
  Bool isRootLeaf  () {return checkType(root(), BranchOrLeaf.leaf);}                                                    // Whether the root is a leaf
  Bool isRootBranch() {return checkType(root(), BranchOrLeaf.branch);}                                                  // Whether the root is a branch

  Bool checkType(Int Node, BranchOrLeaf Type)                                                                           // Check the type of a node
   {final Int  a = nodeAddress(Node);
    final Int  t = unitMemory.getInt(a);
    final Bool r = new Bool(false);
    new If (t.eq(Type.value())) {void Then() {r.set(true);}};
    return r;
   }

  void setType(Int Node, BranchOrLeaf Type)                                                                             // Set the type of a node
   {final Int a = nodeAddress(Node);
    unitMemory.putInt(a, new Int(Type.value()));
   }

  Bool isBranch(Int Node) {return checkType(Node, BranchOrLeaf.branch);}                                                // Whether the indexed node a branch
  Bool isLeaf  (Int Node) {return checkType(Node, BranchOrLeaf.leaf  );}                                                // Whether the indexed node a leaf

  Leaf leaf(Int Node) {return leaf(Node, true);}                                                                        // Index an existing leaf in memory            confirming that it really is a leaf
  Leaf leaf(Int Node, boolean Check)                                                                                    // Index an existing leaf in memory optionally confirming that it really is a leaf
   {if (Check) isLeaf(Node).Flip().stop("Not a leaf:", Node);                                                           // Check the location actually holds a leaf
    final UnitMemory.Ref r = unitMemory.new Ref(nodeAddress(Node));                                                     // Address leaf
    return new Leaf(build.leaf.parent(program()).memory(r).at(Node));                                                   // Base leaf at the indexed address
   }

  Leaf makeLeaf(Int Node)                                                                                               // Make a leaf from the specified node
   {final Leaf l = leaf(Node, false);
    l.initializeMemory();
    setType(Node, BranchOrLeaf.leaf);
    return l;
   }

  Leaf   leaf ()   {return makeLeaf(allocate());}                                                                       // Create and initialize a branch in memory and return its index

  Branch branch (Int Node) {return branch(Node, true);}                                                                 // Index an existing branch in memory            confirming that it really is a branch
  Branch branch (Int Node, boolean Check)                                                                               // Index an existing branch in memory optionally confirming that it really is a branch
   {if (Check) isBranch(Node).Flip().stop("Not a branch:", Node);                                                       // Check the location actually holds a branch
    final UnitMemory.Ref r = unitMemory.new Ref(nodeAddress(Node));                                                     // Address branch
    return new Branch(build.branch.parent(program()).memory(r).at(Node));                                               // Base branch at the indexed address
   }

  Branch makeBranch (Int Node)                                                                                          // Make a branch from the specified node
   {final Branch b = branch(Node, false);
    b.initializeMemory();
    setType(Node, BranchOrLeaf.branch);
    return b;
   }

  Branch branch () {return makeBranch(allocate());}                                                                     // Create and initialize a branch in memory and return its index

  Int  count ()    {return refCount.getInt();}                                                                          // Number of keys in tree
  void countInc () {refCount.putInt(count().inc());}                                                                    // Increment the key count
  void countDec () {refCount.putInt(count().dec());}                                                                    // Increment the key count

  StringBuilder dumpTree ()                                                                                             // Dump the tree
   {subStart("Tree.dumpTree");
    final StringBuilder s = new StringBuilder();
    final Int           c = new Int(numberOfNodes).sub(freeChain.count());
    new I()                                                                                                             // Dump the tree statistics
     {void a()
       {s.setLength(0);
        s.append(f("Tree memory dump\n"));
        s.append(f("Leaf   size   : %4d\n", build.leafSize));
        s.append(f("Branch size   : %4d\n", build.branchSize));
        s.append(f("Node   size   : %4d\n", sizeOfNode));
        s.append(f("MaxLeafSize   : %4d\n", maxLeafSize));
        s.append(f("MaxBranchSize : %4d\n", maxBranchSize));
        s.append(f("NumberOfNodes : %4d\n", numberOfNodes));
        s.append(f("Allocations   : %4d\n", c.i()));
        s.append(f("Number of Keys: %4d\n", refCount.getInt(0)));
       }
      boolean trace() {return false;}
     };

    new ForCount(new Int(min(numberOfNodes, 20)))                                                                       // Dump the leaves and branches
     {void body(Int Index)
       {new If(isAllocated(Index))
         {void Then()
           {new If (isLeaf(Index))
             {void Then() {final StringBuilder t = leaf  (Index).print(); new I() {void a() {s.append(t);} boolean trace() {return false;}};}
              void Else() {final StringBuilder t = branch(Index).print(); new I() {void a() {s.append(t);} boolean trace() {return false;}};}
             };
           }
         };
       }
     };
    subFinish();
    return s;
   }

//D1 Find, Insert, Delete                                                                                               // Find, insert and delete

  Bint find (Int Key)                                                                                                   // Find the data associated with the specified key in the tree
   {subStart("Tree.find");
    final FindLeaf l = findLeaf(Key);                                                                                   // Find leaf that should contain the key
    final Bint  data = new Bint();

    new If (l.valid)
     {void Then()
       {final Leaf       L = leaf(l.leaf);                                                                              // Load leaf
        final Slots.Find f = L.slots.find(Key);                                                                         // Search for key in root
        new If (f.equal)
         {void Then()                                                                                                   // Key exists in leaf
           {final Int k = L.slots.getSlotToKeyIndex(f.slot.i());                                                        // Key slot
            data.set(L.data(k));                                                                                        // Data associated with key
           }
         };
       }
     };

    return data;                                                                                                        // Will be set to invalid unless the key was found in which case it will contain the data associated with the key
   }

  final class FindLeaf                                                                                                  // Find results
   {Bool valid = new Bool("valid");                                                                                     // Whether the search results are valid
    Int  key   = new Int ("key");                                                                                       // Search key
    Int  leaf  = new Int ("leaf index");                                                                                // Leaf that should contain the key

    void start(Int Key) {key.set(Key); valid.clear();}                                                                  // Start the find operation

    void set(Int Leaf)                                                                                                  // Set the find results
     {valid.set();
      leaf.set(Leaf);
     }

    public String toString()                                                                                            // Print the find results
     {subStart("Tree.toString");
      final StringBuilder s = new StringBuilder();
      new I() {void a() {s.append("Find : "+key+" "+valid+"\n");} boolean trace() {return false;}};
      final StringBuilder l = leaf(leaf).print();
      new I() {void a() {s.append(l);}                            boolean trace() {return false;}};
      subFinish();
      return ""+s;
     }
   }

  FindLeaf findLeaf(Int Key)                                                                                            // Find the specified key in a leaf in the tree
   {subStart("Tree.findLeaf");
    final Int      p = root();                                                                                          // Start at root
    final FindLeaf f = new FindLeaf();                                                                                  // Find results
    f.start(Key);
    new For(new Int(mnl()))                                                                                             // Step down from branch to branch
     {void body(Int Index, Bool Continue)
       {new If (isLeaf(p))                                                                                              // On a leaf
         {void Then()
           {f.set(p);                                                                                                   // Show the key and matching leaf
           }
          void Else()                                                                                                   // On a branch
           {final Branch.StepDown d = branch(p).stepDown(Key);                                                          // Step down details
            p.set(d.node);                                                                                              // Step down to next level
            Continue.set();                                                                                             // Continue search
           }
         };
       }
     };
    f.valid.Flip().stop("Find fell off the end of tree after this many searches:", mnl());
    subFinish();
    return f;
   }

  final class Path                                                                                                      // Record the path from the root to the leaf that should contain a key
   {final Int        key      = new Int("key");                                                                         // Search key
    final Int        leaf     = new Int("leaf");                                                                        // Leaf that should contain the key
    final Int        step     = new Int("step");                                                                        // Current step in the path
    final Bint       split    = new Bint();                                                                             // The splitting branch is the uppermost branch directly connected to the leaf by intervening full branches which will all have to be split from the top down to permit the splitting of a full leaf
    final UnitMemory.Ref path = mergePath.new Ref(0);                                                                   // Branches along path

    Path(Int Key)
     {subStart("Tree.Path");
      final Int p = root();                                                                                             // Start at root
      final Bool valid = new Bool(false);                                                                               // Whether a leaf was reached

      key .set(Key);                                                                                                    // Record search key
      step.set(0);                                                                                                      // Start at the root
      mergePath.clear();                                                                                                // Clear the path

      new For(new Int(mnl()))                                                                                           // Step down from branch to branch
       {void body(Int Index, Bool Continue)
         {new If (isLeaf(p))                                                                                            // On a leaf
           {void Then()
             {valid.set();                                                                                              // Reached a leaf
              leaf.set(p);                                                                                              // End the path on a leaf
             }
            void Else()                                                                                                 // On a branch
             {final Branch.StepDown d = branch(p).stepDown(key);                                                        // Step down
              path.putInt(step, p);
              step.inc();                                                                                               // Position for next step
              p.set(d.node);                                                                                            // Step down
              Continue.set();                                                                                           // Continue search
             }
           };
         }
       };
      valid.Flip().stop("Find fell off the end of tree after this many searches:", mnl());
      subFinish();
     }

    void splitPoint()                                                                                                   // Locate the split point: the uppermost full branch directly connected to the leaf by intervening full branches which will have to be split from the top back down to the parent of the leaf to permit the splitting of a full leaf
     {subStart("Tree.splitPoint");
      final Int u = new Int();                                                                                          // Location of split point
      new For(step)                                                                                                     // Number of steps in path
       {void body(Int Index, Bool Continue)                                                                             // Step up from leaf to root
         {final Int p = step.Sub(Index).Dec();                                                                          // Position on path
          final Int b = path.getInt(p);                                                                                 // Branch index
          new If (branch(b).full())                                                                                     // On a full branch
           {void Then()
             {split.set(p);                                                                                             // Highest full branch so far that might need splitting
              Continue.set();                                                                                           // Continue up from the leaf until a branch that is not full is encountered
             }
           };
         }
       };
      subFinish();
     }

    void splitDown()                                                                                                    // Split from the splitting top most splitting branch if such a branch exists
     {subStart("Tree.splitDown");
      new If (split)
       {void Then()
         {new If (split.i().eq(0))                                                                                      // Split the root branch
           {void Then()
             {final Int sk = splitRootBranch();
              final Int  z = root();
              new If (key.le(sk))                                                                                       // Update the path if the key to be inserted is less then the splitting key as the path will now go through the split out left branch
               {void Then()
                 {path.putInt(z, branch(z).data(z));                                                                    // Divert through first element of root now that it has been split
                 }
                void Else()
                 {path.putInt(z, branch(z).top());                                                                      // Divert through top
                 }
               };
              split.set(split.i().inc());                                                                               // Step up over split root which no longer needs splitting
             }
           };

          new ForCount(split.i(), step)                                                                                 // Split full branches which are not the root in descending order so that there is always enough room in the parent branch to accept the splitting key
           {void body(Int Index)
             {final Branch p = branch(path.getInt(Index.Dec()));                                                        // Parent branch whose child should be split
              final Branch c = branch(path.getInt(Index));                                                              // Child branch that should be split
              final Branch.StepDown d = p.stepDown(key);                                                                // Step down
              final Branch l = branch();                                                                                // Branch to split into
              final Int   sk = c.splitLeft(l);                                                                          // Splitting key

              new If (d.slot.notValid())                                                                                // Stepped through top
               {void Then() {p.insert(sk, l.getLocation().i(), new Bint());}                                            // Insert split out branch as last element of parent branch body
                void Else() {p.insert(sk, l.getLocation().i(), d.slot);}                                                // Insert split out branch just below the key in this slot
               };

              new If (key.le(sk))                                                                                       // Update the path if the key to be inserted is less then the splitting key as the path will now go through the split out left branch
               {void Then()
                 {path.putInt (Index, l.getLocation().i());                                                             // Update path with diversion through left branch
                 }
               };
             }
           };
         }
       };
      subFinish();
     }

    void mergeUp()                                                                                                      // Merge up from the leaf to the splitting point
     {subStart("Tree.mergeUp");

      new ForCount(step)                                                                                                // Start at branch immediately above the leaf and work upwards
       {void body(Int Index)
         {final Int             i = step.Sub(Index).dec();                                                              // Index of parent branch that contains the split siblings
          final Branch          p = branch(path.getInt(i));                                                             // Parent branch containing split children
          final Branch.StepDown d = p.stepDown(key);                                                                    // Locate key slot
          final Bint            L = new Bint();
          new ForCount(new Int(4))                                                                                      // Locate the left sibling
           {void body(Int Index)
             {new If (Index.eq(0))                                                                                      // This arrangement reduces the  amount of inline code produced by mergeLeftIntoRightSibling
               {void Then()                                 {L.copy(mergeLeftLeft(  p, d.slot));}
                void Else()
                 {new If (Index.eq(1))
                   {void Then()                             {L.copy(mergeRightRight(p, d.slot));}
                    void Else()
                     {new If (Index.eq(2))
                       {void Then()                         {L.copy(mergeLeft(      p, d.slot));}
                        void Else()
                         {new If (Index.eq(3)) {void Then() {L.copy(mergeRight(     p, d.slot));}};
                         }
                       };
                     }
                   };
                 }
               };
              new If (L) {void Then() {mergeLeftIntoRightSibling(p, L.i());}};                                          // Merge the left sibling into its right sibling
             }
           };
         }
       };

      final Branch R = branch(root());
      new If (R.slots.empty())                                                                                          // Reduce the height of the tree if the body of the root is now empty
       {void Then()
         {final Int t = R.top();                                                                                        // Top
          new If (isLeaf(t))                                                                                            // Root has leaves for children
           {void Then()
             {final Leaf L = makeLeaf(R.getLocation().i());                                                             // Make the root into a leaf
              final Leaf l = leaf(t);                                                                                   // Top as a leaf
              L.copy(l);                                                                                                // Copy top into root decreasing height of tree
              free(l);                                                                                                  // Free top as no longer needed
             }
            void Else()                                                                                                 // Root has branches for children
             {final Branch b = branch(t);                                                                               // Top as a branch
              R.copy(b);                                                                                                // Copy top into root
              free(b);                                                                                                  // Free top as no longer needed
             }
           };
         }
       };
      subFinish();
     }

    StringBuilder print()                                                                                               // Print the path
     {subStart("Tree.print.path");
      final StringBuilder s = new StringBuilder();
      new I() {void a() {s.setLength(0);                    } boolean trace() {return false;}};
      new I() {void a() {s.append("Path: "+step+" steps: ");} boolean trace() {return false;}};
      new ForCount(step)
       {void body(Int Index)
         {final Int v = path.getInt(Index);
          new I() {void a() {s.append(" "+v.i());}};
         }
       };
      new I()     {void a() {s.append(" "+leaf+" "+split+"\n");} boolean trace() {return false;}};
      subFinish();
      return s;
     }
   }

  Path path(Int Key)                                                                                                    // The path from the root to the leaf that should contain the specified key
   {final Path f = new Path(Key);                                                                                       // Find results
    return f;
   }

  public void insert(Int Key, Int Data)                                                                                 // Insert a key, data pair into the tree
   {subStart("Tree.insert");

    new If (isRootLeaf())
     {void Then()                                                                                                       // New right hand leaf
       {final Leaf R = leaf(root());
        final Slots.Find f = R.slots.find(Key);                                                                         // Perhaps the key is already present in the leaf root tree
        new If (f.equal)                                                                                                // Key exists in leaf root
         {void Then()
           {final Int p = R.slots.getSlotToKeyIndex(f.slot.i());                                                        // Position of key in leaf root slots
            R.data(p, Data);                                                                                            // Update data associated with key
           }
          void Else()                                                                                                   // The key does not exist in the root leaf
           {new If (R.full())                                                                                           // Is the leaf full
             {void Then()                                                                                               // Split the leaf to make room for  the new key
               {final Leaf l = leaf(), r = leaf();                                                                      // Child leaves of root branch
                l.copy(R);                                                                                              // Duplicate the root
                final Int   sk = l.splitRight(r);                                                                       // Split the root leaf in two
                final Branch b = makeBranch(root());                                                                    // Make the root into a branch
                b.insert(sk, l.getLocation().i());                                                                      // Insert the left leaf
                b.top(r.getLocation().i());                                                                             // The right leaf becomes top of the root branch
                new If (Key.le(sk)) {void Then() {l.insert(Key, Data);} void Else() {r.insert(Key, Data);}};            // Insert left or right leaf depending on key versus splitting key
               }
              void Else()                                                                                               // Root is a non full leaf that does not contain the key
               {R.insert(Key, Data);                                                                                    // Insert in non full leaf that does not contain the key
               }
             };
            countInc();                                                                                                 // Count inserted key
           }
         };
       }
      void Else()                                                                                                       // The root is a branch
       {final FindLeaf   f = findLeaf(Key);                                                                             // Find the leaf for the key
        final Leaf       l = leaf(f.leaf);                                                                              // Leaf that should contain the key
        final Slots.Find F = l.slots.find(Key);                                                                         // Perhaps the key is already present in the leaf
        new If (F.equal)                                                                                                // Key exists in full leaf
         {void Then()
           {final Int p = l.slots.getSlotToKeyIndex(F.slot.i());                                                        // Position of key in leaf slots
            l.data(p, Data);                                                                                            // Update data  associated with key
           }
          void Else()                                                                                                   // Key is not present in the leaf
           {new If (l.full())                                                                                           // The target leaf is full
             {void Then() {insertFullLeaf(Key, Data);}                                                                  // Insert into a tree known to have a branch at the root and a full target leaf for the key
              void Else() {l.insert(Key, Data);}                                                                        // Insert a new key into a non full leaf
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
    final Path p = path(Key);                                                                                           // Path from root to full leaf
    p.splitPoint();                                                                                                     // The lowest branch in the tree that is full and has a non full parent
    p.splitDown();                                                                                                      // Split the branches down to the leaf as they are all full
    final Int    L = p.step.Dec();                                                                                      // Last step along path
    final Branch P = branch(p.path.getInt(L));                                                                          // Parent branch of full leaf
    final Leaf   r = leaf(p.leaf);                                                                                      // The full leaf into which the key should be inserted
    final Leaf   l = leaf();                                                                                            // New leaf
    final Int   sk = r.splitLeft(l);                                                                                    // Split the full leaf into the new leaf

    final Branch.StepDown d = P.stepDown(Key);

    final Bint s = new Bint();
    new If (d.slot.valid())                                                                                             // If the leaf was reached by stepping through top then insert the new left leaf high
     {void Then()
       {s.copy(d.slot);                                                                                                 // Insert new left leaf below the key in the indicated slot
       }
     };
    P.insert(sk, l.getLocation().i(), s);                                                                               // Insert new left leaf below the key in the indicated slot

    new If (Key.le(sk))                                                                                                 // Insert the key in the left leaf if it less than the splitting key
     {void Then()
       {l.insert(Key, Data);                                                                                            // Insert key in the left leaf
       }
      void Else()
       {r.insert(Key, Data);                                                                                            // Insert key in the right leaf
       }
     };
    if (!suppressMergeUp) p.mergeUp();                                                                                  // Merge nodes on either side of the path going up from the leaf to towards the root
    subFinish();
   }

  public Int delete (Int Key)                                                                                           // Delete a key from the tree and return the associated data if the key was present in the tree
   {subStart("Tree.delete");
    final Int data = new Int();                                                                                         // Data associated with key if the key is present in the tree
    new If (isRootLeaf())
     {void Then()                                                                                                       // The root is a leaf
       {final Leaf       R = leaf(root());                                                                              // Load root
        final Slots.Find f = R.slots.find(Key);                                                                         // Search for key in root
        new If (f.equal)
         {void Then()                                                                                                   // Key exists in leaf
           {data.set(R.data(R.slots.getSlotToKeyValue(f.slot.i())));                                                    // Data associated with key
            R.slots.delete(f.slot.i());                                                                                 // Remove key from leaf comprising tree
            countDec();                                                                                                 // Count deleted key
           }
         };
       }
      void Else()                                                                                                       // The root is a branch
       {final Path       p = new Path(Key);                                                                             // Path to leaf that should contain key
        final Leaf       l = leaf(p.leaf);                                                                              // Containing leaf
        final Slots.Find f = l.slots.find(Key);                                                                         // Search for key in root
        new If (f.equal)
         {void Then()                                                                                                   // Key exists in leaf
           {data.set(l.data(l.slots.getSlotToKeyValue(f.slot.i())));                                                    // Data associated with key
            l.slots.delete(f.slot.i());                                                                                 // Remove key from leaf in tree tree
            p.mergeUp();                                                                                                // Merge leaf and nodes above
            countDec();                                                                                                 // Count deleted key
           }
         };
       }
     };
    subFinish();
    return data;                                                                                                        // Data associated with key if valid else no such key
   }

//D1 Split and Merge                                                                                                    // Split and merge nodes in the tree
//D2 Split                                                                                                              // Split nodes in the tree to make the tree wider

  private Int splitRootBranch()                                                                                         // Split the root assuming that it is a branch
   {subStart("Tree.splitRootBranch");
    final Branch R = branch(root());                                                                                    // The root
    if (immediate() && isRootLeaf()   .b()) stop("Cannot split the root because it is not a branch");                   // Check that it is a branch
    if (immediate() && R.full().Flip().b()) stop("Cannot split the root because it is not full");                       // Check that the root is full
    final Branch l = branch();                                                                                          // New left branch
    final Branch r = branch();                                                                                          // New right branch
    l.copy(R);                                                                                                          // Copy the root into the left branch
    final Int sk = l.splitRight(r);                                                                                     // Splitting key
    R.clear();                                                                                                          // Clear the root
    makeBranch(R.getLocation().i());                                                                                    // Mark the root as a branch
    R.insertEmpty(sk, l.getLocation().i());                                                                             // Insert the left branch below the splitting key
    R.top(r.getLocation().i());                                                                                         // Insert right as top of root
    subFinish();
    return sk;                                                                                                          // Return the splitting key
   }

//D2 Merge                                                                                                              // Merge nodes in the tree to make the tree narrower
//D3 Merge Left                                                                                                         // Merge single and double left

  Bool mergeLeftLeafIntoRightSibling (Branch Parent, Int Left, Leaf Right)                                              // Merge the specified left leaf sibling into its right sibling if possible.  The left sibling is specified by the index of its slot in the specified parent, the right by a leaf description
   {subStart("Tree.mergeLeftLeafIntoRightSibling");
    final Bool   m = new Bool(false);                                                                                   // Whether the merge was performed or not - assume it will not until we discover otherwise
    final Branch P = Parent;
    final Leaf   l = leaf(P.data(P.slots.getSlotToKeyIndex(Left)));                                                     // Left leaf of merge
    new If (Right.mergeLeft(l))                                                                                         // Successfully merged
     {void Then()
       {P.slots.delete(Left);                                                                                           // The left sibling can now be freed
        free(l);
        m.set();
       }
     };
    subFinish();
    return m;                                                                                                           // Whether the merge succeeded
   }

  Bool mergeLeftBranchIntoRightSibling (Branch Parent, Int Left, Branch Right)                                          // Merge the specified left branch sibling into its right sibling if possible separating them with the specified splitting key.  The left sibling is specified by the index of its slot in the specified parent, the right by a leaf description
   {subStart("Tree.mergeLeftBranchIntoRightSibling");
    final Bool   m = new Bool(false);                                                                                   // Whether the merge was performed or not - assume it will not until we discover otherwise
    final Branch P = Parent;
    final Branch l = branch(P.data(P.slots.getSlotToKeyIndex(Left)));                                                   // Left branch of merge
    final Int    k = P.slots.getSlotToKeyValue(Left);                                                                   // The parent key for the left sibling
    new If (Right.mergeLeft(l, k))                                                                                      // Successfully merged
     {void Then()                                                                                                       // The left sibling can now be freed
       {P.slots.delete(Left);                                                                                           // Remove from parent
        free(l);                                                                                                        // Free left branch
        m.set();                                                                                                        // Success
       }
     };
    subFinish();
    return m;                                                                                                           // Whether the merge succeeded
   }

  Bool mergeLeftIntoRightSibling (Branch Parent, Int Left)                                                              // Merge the specified left sibling into its right sibling if possible.  The left sibling is specified by the index of its slot in the specified parent
   {subStart("Tree.mergeLeftIntoRightSibling");
    final Bool   m = new Bool(false);                                                                                   // Whether the merge was performed or not - assume it will not until we discover otherwise
    final Branch P = Parent;
    final Int    l = new Int();                                                                                         // Next sibling location
    final Bint   R = P.slots.usedSlotsToKeys.nextOne(Left);                                                             // Right sibling via next valid slot
    new If (isLeaf(P.top()))                                                                                            // Root has leaves for children
     {void Then()
       {new If (R)                                                                                                      // Next slot exists and so references the right sibling
         {void Then() {l.set(P.data(P.slots.getSlotToKeyIndex(R.i())));}
          void Else() {l.set(P.top());}
         };
        m.set(mergeLeftLeafIntoRightSibling(P, Left, leaf(l)));                                                         // Merge sibling leaves
       }
      void Else()                                                                                                       // Merge last two branches
       {final Bint R = P.slots.usedSlotsToKeys.nextOne(Left);                                                           // Right sibling via next valid slot

        new If (R)                                                                                                      // Next slot exists and so references the right sibling
         {void Then() {l.set(P.data(P.slots.getSlotToKeyIndex(R.i())));}                                                // Next sibling is in the body of the parent
          void Else() {l.set(P.top());}                                                                                 // Next sibling is top
         };
        m.set(mergeLeftBranchIntoRightSibling(P, Left, branch(l)));                                                     // Merge sibling branches
       }
     };
    subFinish();
    return m;                                                                                                           // Whether the merge succeeded
   }

  Bint mergeLeft (Branch Parent, Bint Pos)                                                                              // Merge into the specified sibling, referenced as a slot, from its left hand sibling and remove the left hand sibling if this is possible. The specified position is the slot number of the key relative to which to merge. If the specified position is invalid top is assumed
   {subStart("Tree.mergeLeft");
    final Branch P = Parent;                                                                                            // Parent containing siblings
    final Bint   L = new Bint();                                                                                        // Left child
    new If (Pos)                                                                                                        // Merging relative to top
     {void Then() {L.copy(P.slots.usedSlotsToKeys.prevOne(Pos.i()));}                                                   // Merge entirely within body of parent
      void Else() {L.copy(P.slots.usedSlotsToKeys.lastOne());}                                                          // Last child in body of parent to be merged into top
     };
    subFinish();
    return L;                                                                                                           // Whether the merge was performed or not
   }

  Bint mergeLeftLeft (Branch Parent, Bint Pos)                                                                          // Merge into the left hand sibling of the specified sibling from the left hand sibling of the left hand sibling of the specified sibling if this is possible. The specified position is the slot number of the key relative to which to merge. If the specified position is invalid top is assumed
   {subStart("mergeLeftLeft");
    final Branch P = Parent;                                                                                            // Parent containing siblings
    final Bint   R = new Bint();                                                                                        // Right child of merge
    final Bint   L = new Bint();                                                                                        // Left child

    new If (Pos)                                                                                                        // Merging relative to top
     {void Then() {R.copy(P.slots.usedSlotsToKeys.prevOne(Pos.i()));}                                                   // Merge entirely within body of parent
      void Else() {R.copy(P.slots.usedSlotsToKeys.lastOne());}                                                          // Left once from top
     };

    new If (R.valid())                                                                                                  // There is a left position
     {void Then()
       {L.copy(P.slots.usedSlotsToKeys.prevOne(R.i()));                                                                 // Left of left of position
       }
     };
    subFinish();
    return L;                                                                                                           // Whether the merge was performed or not
   }

//D3 Merge Right                                                                                                        // Merge single and double right

  Bint mergeRight (Branch Parent, Bint Pos) {return Pos;}                                                               // Merge the specified sibling into its right hand sibling if this is possible. The specified position is the slot number of the key relative to which to merge.

  Bint mergeRightRight (Branch Parent, Bint Pos)                                                                        // Merge the right hand sibling of the specified sibling with the right hand sibling of the right hand sibling if this is possible. The specified position is the slot number of the key relative to which to merge.
   {subStart("Tree.mergeRightRight");
    final Bint L = new Bint();                                                                                          // Left child

    new If (Pos.valid())                                                                                                // Not on top
     {void Then()
       {L.copy(Parent.slots.usedSlotsToKeys.nextOne(Pos.i()));                                                          // Right once
       }
     };
    subFinish();
    return L;                                                                                                           // Whether the merge was performed or not
   }

//D2 Traverse the tree                                                                                                  // Traverse the tree in order

  class Traverse                                                                                                        // Traverse the tree in order by maintaining a stack of outstanding actions
   {final UnitMemory node   = traverseNode;                                                                             // Memory to hold outstanding branches and leaves
    final UnitMemory action = traverseAction;                                                                           // Memory to hold requested action against each branch
    final int action_first  = -1,                                                                                       // Add first child branch and update to slot of the first child. Process through the children indicated by positive values then go to top when there are no more children to process
              action_top    = -2,                                                                                       // Add top goto remove
              action_remove = -3;                                                                                       // Remove this branch from stack
    final Int        depth  = new Int("depth");                                                                         // Depth we have reached in the tree. -1 indicates thatthe stack is empty.

    final class LeafContext                                                                                             // The context of a leaf shows its relationship to its parent branch
     {final Bool root   = new Bool();                                                                                   // Whether the current leaf is the root or not
      final Int  parent = new Int();                                                                                    // If the current leaf is not the root then the parent branch of the current leaf
      final Int  leaf   = new Int();                                                                                    // The current leaf
      final Int  slot   = new Int();                                                                                    // The slot in the parent branch
      final Int  depth  = new Int();                                                                                    // Depth of this leaf

      LeafContext(Int Leaf, Int Slot, Int Depth, Int Parent)                                                            // Leaf under a branch
       {root  .set(false);
        parent.set(Parent);
        leaf  .set(Leaf);
        slot  .set(Slot);
        depth .set(Depth);
       }

      LeafContext()                                                                                                     // Leaf as a tree
       {root  .set(true);
        parent.set(0);
        leaf  .set(0);
        slot  .set(0);
        depth .set(0);
       }
     }

    final class BranchContext                                                                                           // The context of a branch shows its relationship to its parent and currently being processed child
     {final Bool root       = new Bool();                                                                               // Whether the current branch is the root or not
      final Int  parent     = new Int();                                                                                // If the current branch is not the root then the parent of the current branch
      final Int  parentSlot = new Int();                                                                                // If the current branch is not the root then the slot through which this branch was reached
      final Int  branch     = new Int();                                                                                // The current branch
      final Int  branchSlot = new Int();                                                                                // The slot in the current branch that has just been processed
      final Int  child      = new Int();                                                                                // The child of the current branch that has just been processed
      final Int  Depth      = new Int();                                                                                // Depth of this branch
      BranchContext()                                                                                                   // Set the current context
       {new If(depth.eq(0))                                                                                             // The branch currently being processed is the root
         {void Then()
           {root.set(true);
            parent.set(0); parentSlot.set(0);
            branch.set(0);
           }
          void Else()                                                                                                   // The branch being currently processed is not the root
           {root.set(false);                                                                                            // Not on the root
            parent     .set(node  .getInt(ib(depth.Dec())));                                                            // Parent branch
            parentSlot .set(action.getInt(ib(depth.Dec())));                                                            // Slot in parent whereby we reached the current branch
            branch     .set(node  .getInt(ib(depth)));                                                                  // The current branch
           }
         };
        branchSlot .set(action.getInt(ib(depth)));                                                                      // The slot in the current branch that has just been processed
        child      .set(node  .getInt(ib(depth.Inc())));                                                                // The leaf or branch below this branch that has just been processed
        Depth      .set(depth);                                                                                         // Show the depth of this branch
       }
     }

    Int parentBranch(Int Index)                                                                                         // Index of parent branch
     {final Int B = new Int(-1);
      new If (Index.ge(0))
       {void Then()
         {B.set(node.getInt(ib(Index)));                                                                                // Index of parent branch
         }
       };
      return B;
     }

    Traverse()                                                                                                          // Traverse the tree visiting each leaf and branch in order
     {node.clear(); action.clear(); depth.set(0); action.putInt(ib(depth), new Int(action_first));                      // Clear the branch stack. This has the effect of requesting the first child of the root be added to the stack
      final Tree tree = Tree.this;

      new If (isRootBranch())                                                                                           // Tree starts with a branch
       {void Then()
         {new For(numberOfNodes*2)                                                                                      // Each node in the tree
           {void body(Int Index, Bool Continue)                                                                         // Process each remaining branch
             {new If (depth.ge(0))                                                                                      // Branches waiting to be processed
               {void Then()                                                                                             // Branches still present on branches stack
                 {Continue.set();                                                                                       // Continue as long as there are branches to be processed
                  new If (isBranch(node.getInt(ib(depth))))                                                             // Processing a branch
                   {void Then()
                     {final Int    a = action.getInt(ib(depth));  a.name = "action";                                    // Action to be performed on branch
                      final Branch b = branch(node.getInt(ib(depth)));                                                  // Branch on which action is to be performed
                      new If (a.eq(new Int(action_first)))                                                              // Add first child
                       {void Then()
                         {final Bint c = b.slots.usedSlotsToKeys.firstOne();                                            // First child if any
                          new If (c)                                                                                    // Put first child on stack
                           {void Then()
                             {action.putInt(ib(depth), c.i());                                                          // Current child
                              depth.inc();                                                                              // Next child next time
                              node  .putInt(ib(depth), b.data(b.slots.getSlotToKeyIndex(c.i())));                       // First child
                              action.putInt(ib(depth), new Int(action_first));
                             }
                            void Else()
                             {action.putInt(ib(depth), new Int(action_top));                                            // No children so move to top
                             }
                           };
                         }

                        void Else()
                         {new If (a.eq(action_top))                                                                     // Add top
                           {void Then()
                             {action.putInt(ib(depth), new Int(action_remove));                                         // Remove after processing top
                              new If (b.slots.empty())                                                                  // Print a place holder if the body is empty
                               {void Then()
                                 {final BranchContext bc = new BranchContext();                                         // Context of current branch
                                  branchBodyEmpty(bc);                                                                  // Processed this slot in this branch
                                 }
                               };
                              depth.inc();                                                                              // Next child next time
                              node.putInt(ib(depth), b.top());                                                          // Add top
                              action.putInt(ib(depth), new Int(action_first));                                          // First child if any
                             }

                            void Else()                                                                                 // Remove
                             {new If (a.eq(action_remove))
                               {void Then()
                                 {depth.dec();                                                                          // Remove from stack uncovering previous item
                                 }
                                void Else()                                                                             // Next child
                                 {final BranchContext bc = new BranchContext();                                         // Context of current branch
                                  branchBody(bc);                                                                       // Processed this slot in this branch
                                  final Bint n = b.slots.usedSlotsToKeys.nextOne(bc.branchSlot);                        // Next child slot
                                  new If (n)                                                                            // Valid next child
                                   {void Then()
                                     {action.putInt(ib(depth), n.i());                                                  // Current child
                                      depth.inc();                                                                      // Next child next time
                                      final Int N = b.data(b.slots.getSlotToKeyIndex(n.i()));                           // Next child index
                                      node.putInt(ib(depth), N);                                                        // First child
                                      action.putInt(ib(depth), new Int(action_first));                                  // Request first child of added branch if it is a branch else it wil be processed as a leaf
                                     }
                                    void Else()                                                                         // No more children so move to top
                                     {action.putInt(ib(depth), new Int(action_top));
                                     }
                                   };
                                 }
                               };
                             }
                           };
                         }
                       };
                     }
                    void Else()                                                                                         // Process a leaf from the stack
                     {final Int   b = parentBranch(depth.Dec());
                      final Slots s = branch(b).slots;
                      leafBody(new LeafContext(node.getInt(ib(depth)), action.getInt(ib(depth.Dec())), depth, b));      // Process the referenced leaf
                      depth.dec();
                     }
                   };
                 }
               };
             }
           };
         }
        void Else()                                                                                                     // Process a tree consisting of a single leaf
         {leafBody(new LeafContext());
         }
       };
     }

    void leafBody       (LeafContext   LC) {}                                                                           // Override to process each leaf
    void branchBody     (BranchContext BC) {}                                                                           // Override to process each branch
    void branchBodyEmpty(BranchContext BC) {}                                                                           // Override to process branches that have a empty body
   }

//D2 Print                                                                                                              // Print the tree horizontally

  final class Print                                                                                                     // Print the tree
   {final Stack<StringBuilder> P = new Stack<>();

    Print(boolean Context)                                                                                              // Print the tree optionally supplying the context of each branch and leaf
     {subStart("Tree.Print");

      new I() {void a() {P.clear();} boolean trace() {return false;}};                                                                                 // Clear output area

      new Traverse()
       {@Override void leafBody(LeafContext LC)                                                                         // Print keys of leaf and optionally the details of the parent
         {final Leaf          l = leaf(LC.leaf);
          final StringBuilder s = new StringBuilder();
          new I() {void a() {clearStringBuilder(s);} boolean trace() {return false;}};                                                                 // Clear the print
          l.iterate((k,d)->s.append(k+","));                                                                            // Format keys
          new I()                                                                                                       // Print leaf keys
           {void a()
             {final int d = LC.depth.i() * linesToPrintABranch;                                                         // Line in output
              pad(d+1);                                                                                                 // Pad the output area so that all the lines have the same length
              chompStringBuilder(s);                                                                                    // Remove trailing comma
              P.elementAt(d).append(s);                                                                                 // Write first line
              if (Context && !LC.root.b())                                                                              // Parent details if requested
               {final StringBuilder t = clearStringBuilder(new StringBuilder());
                final int lI = LC.leaf.i(), lP = LC.parent.i(), lS = LC.slot.i();                                       // Components of second line: leaf number, parent branch number, slot in parent
                if (lS < 0) t.append("("+lI+","+lP+")"); else t.append("("+lI+","+lP+","+lS+")");                       // Format second line
                P.elementAt(d+1).append(t);                                                                             // Write second line
               }
             }
            boolean trace() {return false;}
           };
         }

        @Override void branchBody(BranchContext BC)                                                                     // Print keys of branch and optionally the details of the parent and the children of this branch
         {final Branch b = branch(BC.branch);
          final Int K = b.slots.getSlotToKeyValue(BC.branchSlot);                                                       // Key of slot

          new I()                                                                                                       // Place in output area
           {void a()
             {final int d = BC.Depth.i() * linesToPrintABranch;
              pad(d+2);                                                                                                 // Pad the output area so that all the lines have the same length
              P.elementAt(d).append(""+K.i());                                                                          // Write key into output area

              if (Context)                                                                                              // Context requested
               {if (BC.Depth.i() == 0)                                                                                  // Parent details if requested and there is a parent
                 {P.elementAt(d+1).append("("+BC.branch.i()+")");                                                       // Format second line for a root
                 }
                else
                 {final int bI = BC.branch.i(), bP = BC.parent.i(), bS = BC.parentSlot.i();                             // Components of second line: branch number, parent branch number, slot in parent
                  if (bS >= 0) P.elementAt(d+1).append("("+bI+","+bP+","+bS+")");                                       // Format second line for a non root branch showing the parent of the branch and the slot in the parent this branch came from
                  else         P.elementAt(d+1).append("("+bI+","+bP+")");                                              // Format second line for a non root branch showing the parent of the branch and that it came from top
                 }

                if (true)                                                                                               // Write directions to child as third line
                 {final int bS = BC.branchSlot.i(), bC = BC.child.i();                                                  // Components of second line: branch slot, child index
                  P.elementAt(d+2).append("["+bC+","+bS+"]");
                 }
               }
             }
            boolean trace() {return false;}
           };
         }

        @Override void branchBodyEmpty(BranchContext BC)                                                                // Print a branch with an empty body
         {final Branch b = branch(BC.branch);
          final Int    t = b.top();

          final Int K = b.slots.getSlotToKeyValue(BC.branchSlot);                                                       // Key of slot

          new I()                                                                                                       // Place in output area
           {void a()
             {final int d = BC.Depth.i() * linesToPrintABranch;
              pad(d+2);                                                                                                 // Pad the output area so that all the lines have the same length

              if (Context)                                                                                              // Context requested
               {if (BC.Depth.i() == 0)                                                                                  // Parent details if requested and there is a parent
                 {P.elementAt(d+1).append("("+BC.branch.i()+")");                                                       // Format second line for a root
                 }
                else
                 {final int bI = BC.branch.i(), bP = BC.parent.i(), bS = BC.parentSlot.i();                             // Components of second line: branch number, parent branch number, slot in parent
                  if (bS >= 0) P.elementAt(d+1).append("("+bI+","+bP+","+bS+")");                                       // Format second line for a non root branch showing the parent of the branch and the slot in the parent this branch came from
                  else         P.elementAt(d+1).append("("+bI+","+bP+")");                                              // Format second line for a non root branch showing the parent of the branch and that it came from top
                 }

                if (true)                                                                                               // Write directions to child as third line
                 {P.elementAt(d+2).append("["+t.i()+"]");
                 }
               }
             }
            boolean trace() {return false;}
           };
         }
       };
      subFinish();
     }

    void pad(int level)                                                                                                 // Pad the strings at each level of the tree so we have a vertical face to continue with - a bit like Marc Brunel's tunneling shield
     {for (int i = P.size(); i <= level; ++i) P.push(clearStringBuilder(new StringBuilder()));                          // Make sure we have a full deck of strings
      int m = 0;                                                                                                        // Maximum length
      for (StringBuilder s : P) m = m < s.length() ? s.length() : m;                                                    // Find maximum length
      for (StringBuilder s : P) if (s.length() < m) s.append(" ".repeat(m - s.length()));                               // Pad each string to the length of the longest string
     }

    StringBuilder printCollapsed()                                                                                      // Collapse horizontal representation into a string
     {final StringBuilder t = new StringBuilder();                                                                      // Print the lines of the tree that are not blank
      new I()
       {void a()
         {clearStringBuilder(t);
          pad(0);
          for  (StringBuilder s : P)
           {final String l = ""+s;
            if (!l.isBlank()) t.append(l+"|\n");
           }
         }
        boolean trace() {return false;}
       };
      return t;
     }
   }

  StringBuilder dump () {subStart("Tree.dump" ); var s = new Print(true) .printCollapsed(); subFinish(); return s;}     // Dump the tree
  StringBuilder print() {subStart("Tree.print"); var s = new Print(false).printCollapsed(); subFinish(); return s;}     // Print the tree

//D1 Tests                                                                                                              // Tests

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

    A.insert(t.new Int(1), t.new Int(11)); t.countInc();
    B.insert(t.new Int(3), t.new Int(33)); t.countInc();
    C.insert(t.new Int(6), t.new Int(66));

    t.check(t.dumpTree(), """
Tree memory dump
Leaf   size   :   23
Branch size   :   33
Node   size   :   33
MaxLeafSize   :    2
MaxBranchSize :    3
NumberOfNodes :    4
Allocations   :    3
Number of Keys:    4
Leaf           size:   2, count:   2
 Ref   Key  Data
   1     1    11
   0     2    22
Leaf   at:   1 size:   2, count:   2
 Ref   Key  Data
   1     3    33
   0     4    44
Branch at:   2 size:   3, count:   2, top:   0
 Ref   Key  Data
   0     5    55
   1     6    66
""");

               t.isAllocated(a.at.i()).ok(true);
    t.free(A); t.isAllocated(a.at.i()).ok(false);  t.countDec(); t.countDec();
    t.check(t.dumpTree(), """
Tree memory dump
Leaf   size   :   23
Branch size   :   33
Node   size   :   33
MaxLeafSize   :    2
MaxBranchSize :    3
NumberOfNodes :    4
Allocations   :    2
Number of Keys:    2
Leaf   at:   1 size:   2, count:   2
 Ref   Key  Data
   1     3    33
   0     4    44
Branch at:   2 size:   3, count:   2, top:   0
 Ref   Key  Data
   0     5    55
   1     6    66
""");
               t.isAllocated(b.at.i()).ok(true);
    t.free(b); t.isAllocated(b.at.i()).ok(false);   t.countDec(); t.countDec();
    t.check(t.dumpTree(), """
Tree memory dump
Leaf   size   :   23
Branch size   :   33
Node   size   :   33
MaxLeafSize   :    2
MaxBranchSize :    3
NumberOfNodes :    4
Allocations   :    1
Number of Keys:    0
Branch at:   2 size:   3, count:   2, top:   0
 Ref   Key  Data
   0     5    55
   1     6    66
""");

               t.isAllocated(c.at.i()).ok(true);
    t.free(c); t.isAllocated(c.at.i()).ok(false);
    t.check(t.dumpTree(), """
Tree memory dump
Leaf   size   :   23
Branch size   :   33
Node   size   :   33
MaxLeafSize   :    2
MaxBranchSize :    3
NumberOfNodes :    4
Allocations   :    0
Number of Keys:    0
""");

    t.maxSteps = 99_999;
    t.execute();
   }

  static void test_tree()
   {test_tree(true);
    test_tree(false);
   }

  static String[]tree6 = {"AAAAAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMAAAAAAAAAAAAAAAAAAAAAAAAAAAAAUggAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAUQAAAAEAAAACAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAACAAAAAAAAAAAAAAAAAAAAAAAAAAwAAAAYAAAAJAAAADAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAABAAAABQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAdSIAAAACAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADTAAAAAgAAAAEAAAACAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADAAAABgAAAAkAAAAMAAAAAEAAAAAAAAAAgAAAAAAAAAAAAAAAAAAAAMAAAAAAAAAAQAAAAYAAAAHAAAAAQAAAAUAAAAAAAAAAAAAAAAAAAAAAAR94gAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/8AAAAEAAAABQAAAAYAAAADAAAABAAAAAAAAAAAAAAAAAAAAAAAAAA8AAAASAAAACQAAAAwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABoAAAAAQAAAAYAAAAAAAAAAAAAAAA=", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="};

  static void test_saveReload(boolean Ex)
   {sayCurrentTestName();

    final boolean createNew = true;

    final Tree t = new Tree(new Build().maxLeafSize(4).maxBranchSize(3).numberOfNodes(4).immediate(Ex));

    if (createNew)
     {t.new ForCount(t.new Int(1), t.new Int(7))
       {void body(Int Index)
         {t.insert(t.new Int(Index), t.new Int(Index.Mul(11).add(Index)));
         }
       };
      t.new I() {void a() {say("  static String[]tree6 = "+ t.saveMemories()+";");} boolean trace() {return false;}};
     }
    else
     {t.reloadMemories(tree6);
     }

    t.check (t.dumpTree(), """
Tree memory dump
Leaf   size   :   41
Branch size   :   33
Node   size   :   41
MaxLeafSize   :    4
MaxBranchSize :    3
NumberOfNodes :    4
Allocations   :    3
Number of Keys:    6
Branch         size:   3, count:   1, top:   2
 Ref   Key  Data
   0     2     1
Leaf   at:   1 size:   4, count:   2
 Ref   Key  Data
   0     1    12
   1     2    24
Leaf   at:   2 size:   4, count:   4
 Ref   Key  Data
   2     3    36
   3     4    48
   0     5    60
   1     6    72
""");

    t.check(t.dump(), """
       2           |
       (0)         |
       [1,3]       |
1,2         3,4,5,6|
(1,0,3)     (2,0)  |
""");

    t.maxSteps = 99_999;
    //t.dumpMemoryEvery = 10;
    t.execute();
   }

  static void test_saveReload()
   {          test_saveReload(true);
              test_saveReload(false);
   }

  final static String[]tree32 = {"AAAAAgAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAACAAAAAAAAAAAAAAAAAAAAAAAAUgQAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAUgAAAAEAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAALAAAACQAAAAAAAAAGAAAAAAAAAAAAAAAAAAAAAAAAAAsAAAAWAAAAIQAAACwAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAABAAAABQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAdSIAAAACAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADTAAAAAgAAABkAAAAaAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABEwAAAR4AAAEpAAABNAAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAACAAAAAwAAAAEAAAAFAAAABgAAAAcAAAAAAAAAAAAAAAAAAAAAAAR94gAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/8AAAAEAAAAHQAAAB4AAAAfAAAAIAAAAAAAAAAAAAAAAAAAAAAAAAE/AAABSgAAAVUAAAFgAAAAAQAAAAAAAAAAAAAAAQAAAAAAAAACAAAAAAAAAAMAAAAAAAAAAAAAAAIAAAAEAAAABgAAAAAAAAAAAAAAAAAAAAAAAH9VAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/wAAAAQAAAABAAAAAgAAAAMAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAsAAAAWAAAAIQAAACwAAAABAAAAAAAAAAAAAAABAAAAAAAAAAIAAAAAAAAAAwAAAAAAAAAAAAAAAgAAAAQAAAAGAAAAAAAAAAAAAAAAAAAAAAAAf1UAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/AAAABAAAAAUAAAAGAAAABwAAAAgAAAAAAAAAAAAAAAAAAAAAAAAANwAAAEIAAABNAAAAWAAAAAEAAAAAAAAAAAAAAAEAAAAAAAAAAgAAAAAAAAADAAAAAAAAAAAAAAACAAAABAAAAAYAAAAAAAAAAAAAAAAAAAAAAAB/VQAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/8AAAAEAAAAFQAAABYAAAAXAAAAGAAAAAAAAAAAAAAAAAAAAAAAAADnAAAA8gAAAP0AAAEIAAAAAgAAAAAAAAAAAAAAAQAAAAAAAAACAAAAAAAAAAAAAAACAAAABAAAAAAAAAAAAAAAAAAAdxUAAAADAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA9wAAAAMAAAAUAAAAGAAAABwAAAAAAAAAAAAAAAAAAAAKAAAABQAAAAwAAAACAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAABAAAAAAAAAAIAAAAAAAAAAwAAAAAAAAAAAAAAAgAAAAQAAAAGAAAAAAAAAAAAAAAAAAAAAAAAf1UAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/AAAABAAAAAkAAAAKAAAACwAAAAwAAAAAAAAAAAAAAAAAAAAAAAAAYwAAAG4AAAB5AAAAhAAAAAEAAAAAAAAAAAAAAAEAAAAAAAAAAgAAAAAAAAADAAAAAAAAAAAAAAACAAAABAAAAAYAAAAAAAAAAAAAAAAAAAAAAAB/VQAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/8AAAAEAAAADQAAAA4AAAAPAAAAEAAAAAAAAAAAAAAAAAAAAAAAAACPAAAAmgAAAKUAAACwAAAAAgAAAAAAAAAAAAAAAQAAAAAAAAACAAAAAAAAAAAAAAACAAAABAAAAAAAAAAAAAAAAAAAdxUAAAADAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA9wAAAAMAAAAEAAAACAAAAAwAAAAAAAAAAAAAAAAAAAADAAAABAAAAAcAAAAIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAABAAAAAAAAAAIAAAAAAAAAAwAAAAAAAAAAAAAAAgAAAAQAAAAGAAAAAAAAAAAAAAAAAAAAAAAAf1UAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/AAAABAAAABEAAAASAAAAEwAAABQAAAAAAAAAAAAAAAAAAAAAAAAAuwAAAMYAAADRAAAA3AAAAAIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFIEAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFEAAAABAAAAFAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACgAAAAoAAAAFAAAABQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAQAAAAAAAAACAAAAAAAAAAMAAAAAAAAAAAAAAAIAAAAEAAAABgAAAAAAAAAAAAAAAAAAAAAAAH9VAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/wAAAAQAAAAZAAAAGgAAABsAAAAcAAAAAAAAAAAAAAAAAAAAAAAAARMAAAEeAAABKQAAATQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP//6AJ//f/hFnh/wAAAABUAAAAgAAAAAAAAAAAAAAAA", "AAAAAAAAAAYAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="};

  static void test_insert(boolean Ex)
   {sayCurrentTestName();

    final boolean createNew = true;

    final int  N = 32;
    final Tree t = new Tree(new Build().maxLeafSize(4).maxBranchSize(3).numberOfNodes(N).immediate(Ex));

    if (createNew)                                                                                                      // Create the tree and dump it
     {t.new ForCount(t.new Int(1), t.new Int(N+1))
       {void body(Int Index)
         {t.insert(Index, Index.Mul(11));
         }
       };
      t.new I() {void a() {say("final static String[]tree32 = "+ t.saveMemories()+";");} boolean trace() {return false;}};
     }
    else
     {t.reloadMemories(tree32);
     }

    //final StringBuilder s = t.dump();  t.new I() {void a() {stop(s);}};
    //final StringBuilder S = t.print(); t.new I() {void a() {stop(S);}};
    if (N == 32) t.check(t.dump(), """
                                                        16                                                                |
                                                        (0)                                                               |
                                                        [9,2]                                                             |
       4             8                12                                20               24              28               |
       (9,0,2)       (9,0,2)          (9,0,2)                           (6,0)            (6,0)           (6,0)            |
       [3,0]         [4,2]            [7,4]                             [10,0]           [5,2]           [12,4]           |
1,2,3,4       5,6,7,8       9,10,11,12       13,14,15,16     17,18,19,20      21,22,23,24     25,26,27,28      29,30,31,32|
(3,9,0)       (4,9,2)       (7,9,4)          (8,9)           (10,6,0)         (5,6,2)         (12,6,4)         (2,6)      |
""");

    t.maxSteps = 9_999_999;
    t.execute();
   }

  static void test_insert()
   {          test_insert(true);
              test_insert(false);
   }

  static Tree test_reloadTree(boolean Ex)                                                                               // Reload a tree from memory as faster than reconstructing it
   {final int  N = 32;
    final Tree t = new Tree(new Build().maxLeafSize(4).maxBranchSize(3).numberOfNodes(N).immediate(Ex))
     {void treeBody()
       {reloadMemories(tree32);
       }
     };
    return t;
   }

  static void test_insertMerged(boolean Ex)
   {sayCurrentTestName();
    final int N = 32;
    final Tree t = new Tree(new Build().maxLeafSize(4).maxBranchSize(3).numberOfNodes(N).immediate(Ex));
    t.new ForCount(t.new Int(1), t.new Int(N+1))
     {void body(Int Index)
       {t.insert(Index, Index);
       }
     };
    //final StringBuilder s = t.dump();  t.new I() {void a() {stop(s);}};
    //final StringBuilder S = t.print(); t.new I() {void a() {stop(S);}};

    if (N == 32) t.check(t.dump(), """
                                                        16                                                                |
                                                        (0)                                                               |
                                                        [9,2]                                                             |
       4             8                12                                20               24              28               |
       (9,0,2)       (9,0,2)          (9,0,2)                           (6,0)            (6,0)           (6,0)            |
       [3,0]         [4,2]            [7,4]                             [10,0]           [5,2]           [12,4]           |
1,2,3,4       5,6,7,8       9,10,11,12       13,14,15,16     17,18,19,20      21,22,23,24     25,26,27,28      29,30,31,32|
(3,9,0)       (4,9,2)       (7,9,4)          (8,9)           (10,6,0)         (5,6,2)         (12,6,4)         (2,6)      |
""");

    t.maxSteps = 9_999_999;
    t.execute();
   }

  static void test_insertMerged()
   {          test_insertMerged(true);
              test_insertMerged(false);
   }

  static void test_insertReverse(boolean Ex)
   {sayCurrentTestName();
    final int N = 32;
    final Tree t = new Tree(new Build().maxLeafSize(4).maxBranchSize(3).numberOfNodes(N).immediate(Ex));
    t.new ForCount(t.new Int(N))
     {void body(Int Index)
       {t.insert(t.new Int(N).sub(Index), Index);
       }
     };
    //final StringBuilder s = t.dump();  t.new I() {void a() {stop(s);}};
    //final StringBuilder S = t.print(); t.new I() {void a() {stop(S);}};

    if (N == 32) t.check(t.dump(), """
                                                         16                                                              |
                                                         (0)                                                             |
                                                         [9,2]                                                           |
        4             8                12                                20              24              28              |
        (9,0,2)       (9,0,2)          (9,0,2)                           (6,0)           (6,0)           (6,0)           |
        [12,0]        [5,2]            [10,4]                            [7,0]           [4,2]           [3,4]           |
1,2,3,4        5,6,7,8       9,10,11,12       13,14,15,16     17,18,19,20     21,22,23,24     25,26,27,28     29,30,31,32|
(12,9,0)       (5,9,2)       (10,9,4)         (8,9)           (7,6,0)         (4,6,2)         (3,6,4)         (2,6)      |
""");

    t.maxSteps = 9_999_999;
    t.execute();
   }

  static void test_insertReverse()
   {          test_insertReverse(true);
              test_insertReverse(false);
   }

  static void test_insertRandom32(boolean Ex)
   {sayCurrentTestName();

    final int  N = random_32.length;
    final Tree t = new Tree(new Build().maxLeafSize(4).maxBranchSize(3).numberOfNodes(N).immediate(Ex))
     {void treeBody()
       {if (!Ex) defineArrayViaVerilogFunction("loadRandomKeys", random_32);                                            // Create an array of the random keys to be inserted from Verilog

        new ForCount(new Int(N))
         {void body(Int Index)
           {final Int k = new Int();
            new I()
             {void   a() {       k.ex(Int.Ops.set, random_32[Index.i()]);}
              String v() {return k.vn()+" <= loadRandomKeys("+Index.vn()+");";}
              boolean trace() {return false;}
             };
            insert(k, Index);
           }
         };

        if (N == 32) check(dump(), """
                                                        15                                                            26                          |
                                                        (0)                                                           (0)                         |
                                                        [5,1]                                                         [11,4]                      |
        4             7               11                                19              21              24                             30         |
        (5,0,1)       (5,0,1)         (5,0,1)                           (11,0,4)        (11,0,4)        (11,0,4)                       (6,0)      |
        [14,0]        [1,2]           [9,4]                             [12,1]          [3,4]           [8,5]                          [10,2]     |
1,2,3,4        5,6,7         8,9,10,11       12,13,14,15     16,17,18,19        20,21           22,23,24        25,26       27,28,29,30      31,32|
(14,5,0)       (1,5,2)       (9,5,4)         (4,5)           (12,11,1)          (3,11,4)        (8,11,5)        (7,11)      (10,6,2)         (2,6)|
""");

        maxSteps = 9_999_999;
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
    final int           N = 32;

    final Tree          t = test_reloadTree(Ex);

    final StringBuilder s = new StringBuilder();
    final StringBuilder S = t.print();
    t.new I() {void a() {s.append(S);} boolean trace() {return false;}};

    t.new ForCount(t.new Int(N))
     {void body(Int Index)
       {t.delete(Index.Inc());
        final StringBuilder T = t.print();
        t.new I() {void a() {s.append(T);} boolean trace() {return false;}};
       }
     };
        //t.new I() {void a() {stop(s);}};
    t.check(s, """
                                       16                                                  |
       4       8          12                        20           24           28           |
1,2,3,4 5,6,7,8 9,10,11,12  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
                                     16                                                  |
     4       8          12                        20           24           28           |
2,3,4 5,6,7,8 9,10,11,12  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
                                   16                                                  |
   4       8          12                        20           24           28           |
3,4 5,6,7,8 9,10,11,12  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
                                 16                                                  |
 4       8          12                        20           24           28           |
4 5,6,7,8 9,10,11,12  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
                               16                                                  |
       8          12                        20           24           28           |
5,6,7,8 9,10,11,12  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
                             16                                                  |
     8          12                        20           24           28           |
6,7,8 9,10,11,12  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
                           16                                                  |
   8          12                        20           24           28           |
7,8 9,10,11,12  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
                         16                                                  |
 8          12                        20           24           28           |
8 9,10,11,12  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
                       16                                                  |
          12                        20           24           28           |
9,10,11,12  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
                     16                                                  |
        12                        20           24           28           |
10,11,12  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
                  16                                                  |
     12                        20           24           28           |
11,12  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
               16                                                  |
  12                        20           24           28           |
12  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
           16                                                  |
                        20           24           28           |
13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
        16                                                  |
                     20           24           28           |
14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
     16                                                  |
                  20           24           28           |
15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
  16                                                  |
               20           24           28           |
16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
16                                                  |
             20           24           28           |
  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
16                                               |
          20           24           28           |
  18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
16                                            |
       20           24           28           |
  19,20  21,22,23,24  25,26,27,28  29,30,31,32|
16                                         |
    20           24           28           |
  20  21,22,23,24  25,26,27,28  29,30,31,32|
16           24           28           |
  21,22,23,24  25,26,27,28  29,30,31,32|
        24           28           |
22,23,24  25,26,27,28  29,30,31,32|
     24           28           |
23,24  25,26,27,28  29,30,31,32|
  24           28           |
24  25,26,27,28  29,30,31,32|
           28           |
25,26,27,28  29,30,31,32|
        28           |
26,27,28  29,30,31,32|
     28           |
27,28  29,30,31,32|
  28           |
28  29,30,31,32|
29,30,31,32|
30,31,32|
31,32|
32|
""");
    t.maxSteps = 9_999_999;
    t.execute();
   }

  static void test_deleteAscending()
   {          test_deleteAscending(true);
              test_deleteAscending(false);
   }

  static void test_deleteDescending(boolean Ex)
   {sayCurrentTestName();
    final int  N = 32;

    final Tree t = test_reloadTree(Ex);
    t.reloadMemories(tree32);
    final StringBuilder s = new StringBuilder();
    final StringBuilder S = t.print();
    t.new I() {void a() {s.append(S);} boolean trace() {return false;}};

    t.new ForCount(t.new Int(N))
     {void body(Int Index)
       {t.delete(t.new Int(N).sub(Index));
        final StringBuilder T = t.print();
        t.new I() {void a() {s.append(T);} boolean trace() {return false;}};
       }
     };

        //new I() {void a() {stop(s);}};
    t.check(s, """
                                       16                                                  |
       4       8          12                        20           24           28           |
1,2,3,4 5,6,7,8 9,10,11,12  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
                                       16                                               |
       4       8          12                        20           24           28        |
1,2,3,4 5,6,7,8 9,10,11,12  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31|
                                       16                                            |
       4       8          12                        20           24           28     |
1,2,3,4 5,6,7,8 9,10,11,12  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30|
                                       16                                         |
       4       8          12                        20           24           28  |
1,2,3,4 5,6,7,8 9,10,11,12  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29|
                                       16                                     |
       4       8          12                        20           24           |
1,2,3,4 5,6,7,8 9,10,11,12  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28|
                                       16                                  |
       4       8          12                        20           24        |
1,2,3,4 5,6,7,8 9,10,11,12  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27|
                                       16                               |
       4       8          12                        20           24     |
1,2,3,4 5,6,7,8 9,10,11,12  13,14,15,16  17,18,19,20  21,22,23,24  25,26|
                                       16                            |
       4       8          12                        20           24  |
1,2,3,4 5,6,7,8 9,10,11,12  13,14,15,16  17,18,19,20  21,22,23,24  25|
                                       16                        |
       4       8          12                        20           |
1,2,3,4 5,6,7,8 9,10,11,12  13,14,15,16  17,18,19,20  21,22,23,24|
                                       16                     |
       4       8          12                        20        |
1,2,3,4 5,6,7,8 9,10,11,12  13,14,15,16  17,18,19,20  21,22,23|
                                       16                  |
       4       8          12                        20     |
1,2,3,4 5,6,7,8 9,10,11,12  13,14,15,16  17,18,19,20  21,22|
                                       16               |
       4       8          12                        20  |
1,2,3,4 5,6,7,8 9,10,11,12  13,14,15,16  17,18,19,20  21|
                                       16           |
       4       8          12                        |
1,2,3,4 5,6,7,8 9,10,11,12  13,14,15,16  17,18,19,20|
                                       16        |
       4       8          12                     |
1,2,3,4 5,6,7,8 9,10,11,12  13,14,15,16  17,18,19|
                                       16     |
       4       8          12                  |
1,2,3,4 5,6,7,8 9,10,11,12  13,14,15,16  17,18|
                                       16  |
       4       8          12               |
1,2,3,4 5,6,7,8 9,10,11,12  13,14,15,16  17|
                                       16|
       4       8          12             |
1,2,3,4 5,6,7,8 9,10,11,12  13,14,15,16  |
                                    16|
       4       8          12          |
1,2,3,4 5,6,7,8 9,10,11,12  13,14,15  |
                                 16|
       4       8          12       |
1,2,3,4 5,6,7,8 9,10,11,12  13,14  |
                              16|
       4       8          12    |
1,2,3,4 5,6,7,8 9,10,11,12  13  |
       4       8          16|
1,2,3,4 5,6,7,8 9,10,11,12  |
       4       8       |
1,2,3,4 5,6,7,8 9,10,11|
       4       8    |
1,2,3,4 5,6,7,8 9,10|
       4       8 |
1,2,3,4 5,6,7,8 9|
       4       |
1,2,3,4 5,6,7,8|
       4     |
1,2,3,4 5,6,7|
       4   |
1,2,3,4 5,6|
       4 |
1,2,3,4 5|
1,2,3,4|
1,2,3|
1,2|
1|
""");

    t.maxSteps = 9_999_999;
    t.execute();
   }

  static void test_deleteDescending()
   {          test_deleteDescending(true);
              test_deleteDescending(false);
   }

  static void test_deleteRandom32(boolean Ex)
   {sayCurrentTestName();
    final int  N = random_32.length;

    final Tree t = test_reloadTree(Ex);
    t.reloadMemories(tree32);

    final StringBuilder s = t.print();
    if (!Ex) t.defineArrayViaVerilogFunction("loadRandomKeys", random_32);                                              // Create an array of the random keys to be deleted so that the array is accessible from Verilog

    t.new ForCount(t.new Int(N))
     {void body(Int Index)
       {final Int k = t.new Int();
        t.new I()
         {void   a() {       k.ex(Int.Ops.set, random_32[Index.i()]);}
          String v() {return k.vn()+" <= loadRandomKeys("+Index.vn()+");";}
           boolean trace() {return false;}
         };

        t.delete(k);

        final StringBuilder T = t.print();
        t.new I() {void a() {s.append(T);} boolean trace() {return false;}};
       }
     };

    //new I() {void a() {stop(s);}};
    t.check(s, """
                                       16                                                  |
       4       8          12                        20           24           28           |
1,2,3,4 5,6,7,8 9,10,11,12  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
                                    16                                                  |
       4       8       12                        20           24           28           |
1,2,3,4 5,6,7,8 9,10,11  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
                                  16                                                  |
     4       8       12                        20           24           28           |
1,2,4 5,6,7,8 9,10,11  13,14,15,16  17,18,19,20  21,22,23,24  25,26,27,28  29,30,31,32|
                                  16                                               |
     4       8       12                        20           24        28           |
1,2,4 5,6,7,8 9,10,11  13,14,15,16  17,18,19,20  21,22,23,24  25,26,28  29,30,31,32|
                                16                                               |
   4       8       12                        20           24        28           |
2,4 5,6,7,8 9,10,11  13,14,15,16  17,18,19,20  21,22,23,24  25,26,28  29,30,31,32|
                                16                                            |
   4       8       12                        20        24        28           |
2,4 5,6,7,8 9,10,11  13,14,15,16  17,18,19,20  21,22,24  25,26,28  29,30,31,32|
                                16                                         |
   4       8       12                     20        24        28           |
2,4 5,6,7,8 9,10,11  13,14,15,16  17,18,19  21,22,24  25,26,28  29,30,31,32|
                              16                                         |
   4     8       12                     20        24        28           |
2,4 5,6,7 9,10,11  13,14,15,16  17,18,19  21,22,24  25,26,28  29,30,31,32|
                              16                                      |
   4     8       12                  20        24        28           |
2,4 5,6,7 9,10,11  13,14,15,16  17,19  21,22,24  25,26,28  29,30,31,32|
                            16                                      |
       8       12                  20        24        28           |
4,5,6,7 9,10,11  13,14,15,16  17,19  21,22,24  25,26,28  29,30,31,32|
                            16                                   |
       8       12                  20        24        28        |
4,5,6,7 9,10,11  13,14,15,16  17,19  21,22,24  25,26,28  29,30,32|
                            16                                |
       8       12                  20        24     28        |
4,5,6,7 9,10,11  13,14,15,16  17,19  21,22,24  26,28  29,30,32|
                         16                                |
       8       12               20        24     28        |
4,5,6,7 9,10,11  13,14,15  17,19  21,22,24  26,28  29,30,32|
                      16                                |
       8       12            20        24     28        |
4,5,6,7 9,10,11  14,15  17,19  21,22,24  26,28  29,30,32|
                      16                            |
       8       12            20        24           |
4,5,6,7 9,10,11  14,15  17,19  21,22,24  26,28,29,30|
                  16                            |
       8                 20        24           |
4,5,6,7 9,10,14,15  17,19  21,22,24  26,28,29,30|
       8          16           24           |
4,5,6,7 9,10,14,15  17,19,22,24  26,28,29,30|
     8          16           24           |
4,6,7 9,10,14,15  17,19,22,24  26,28,29,30|
     8          16        24           |
4,6,7 9,10,14,15  17,19,22  26,28,29,30|
   8          16        24           |
6,7 9,10,14,15  17,19,22  26,28,29,30|
   8       16        24           |
6,7 9,14,15  17,19,22  26,28,29,30|
   8       16        24        |
6,7 9,14,15  17,19,22  28,29,30|
   8       16        24     |
6,7 9,14,15  17,19,22  28,29|
         16        24     |
6,7,14,15  17,19,22  28,29|
       16        24     |
7,14,15  17,19,22  28,29|
       16           |
7,14,15  17,19,22,28|
       16        |
7,14,15  19,22,28|
       16     |
7,14,15  19,22|
7,14,19,22|
7,19,22|
7,22|
22|
""");

    t.maxSteps = 9_999_999;
    t.execute();
   }

  static void test_deleteRandom32()
   {          test_deleteRandom32(true);
              test_deleteRandom32(false);
   }

  static void test_update(boolean Ex)
   {sayCurrentTestName();
    final Tree t = new Tree(new Build().maxLeafSize(4).maxBranchSize(3).numberOfNodes(4).immediate(Ex))
     {void treeBody()
       {new ForCount(new Int(2))
         {void body(Int I)
           {new ForCount(new Int(2))
             {void body(Int J)
               {insert(J, I);
               }
             };
           }
         };
        check(dumpTree(), """
Tree memory dump
Leaf   size   :  161
Branch size   :  129
Node   size   :  161
MaxLeafSize   :    4
MaxBranchSize :    3
NumberOfNodes :    4
Allocations   :    1
Number of Keys:    2
Leaf           size:   4, count:   2
 Ref   Key  Data
   0     0     1
   1     1     1
""");
        maxSteps = 9_999_999;
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
    final Tree t = test_reloadTree(Ex);
    t.new ForCount(t.new Int(N+2))
     {void body(Int Index)
       {final Bint d = t.find(Index);
        t.new If (d)
         {void Then() {d.ok(Index.Mul(11));}
          void Else() {d.ok(false);}
         };
       }
     };

    t.maxSteps = 9_999_999;
    t.execute();
   }

  static void test_find()
   {          test_find(true);
              test_find(false);
   }


  static void oldTests()                                                                                                // Tests thought to be in good shape
   {if (rtg( 1)) test_tree();
    if (rtg( 2)) test_saveReload();
    if (rtg( 3)) test_insert();
    if (rtg( 4)) test_insertMerged();
    if (rtg( 5)) test_insertReverse();
    if (rtg( 6)) test_insertRandom32();
    if (rtg( 7)) test_deleteAscending();
    if (rtg( 8)) test_deleteDescending();
    if (rtg( 9)) test_deleteRandom32();
    if (rtg(10)) test_update();
    if (rtg(11)) test_find();
   }

  static void newTests()                                                                                                // Tests being worked on
   {//oldTests();
    test_saveReload(!true);
   }

  public static void main(String[] args)                                                                                // Test if called as a program
   {testGroup = args.length > 0 ? args[0] : null;                                                                       // Test groups if supplied
    try                                                                                                                 // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {deleteAllFiles(verilogFolder, 99);                                                                                // Delete generated Verilog files created by a prior run of the current test
      if (github_actions) oldTests(); else newTests();                                                                  // Tests to run
      if (coverageAnalysis) coverageAnalysis(12);                                                                       // Coverage analysis
      say(subPrint());
      testSummary();                                                                                                    // Summarize test results
      System.exit(testsFailed);
     }
    catch(Exception e)                                                                                                  // Get a traceback in a format clickable in Geany
     {say(e);
      say(fullTraceBack(e));
      System.exit(1);
     }
   }
 }
//rsync -az ~/btreeList/ nb:~/btreeList/;javac -g -d ~/btreeList/Classes -cp ~/btreeList/Classes ~/btreeList/*.java;ssh nb java -XX:+UseZGC -cp ~/btreeList/Classes/ com/AppaApps/Silicon/Tree
