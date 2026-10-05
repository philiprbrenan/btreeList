//----------------------------------------------------------------------------------------------------------------------
// Btree with stucks implemented as distributed sparse slots
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
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
  final int            sizeOfNode;                                                                                      // The size of each node in the tree: a node must be able to hold a branch or a leaf
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
    Integer     maxLeafSize;
    Integer   maxBranchSize;
    Integer   numberOfNodes;
    Integer   rootFanLevels;
    Integer      leafFanOut;
    Integer    branchFanOut;
    Boolean         execute;
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
   }

  Tree(Build Build)                                                                                                     // Create the tree
   {super(Build.build());
    maxLeafSize   = Build.maxLeafSize   == null ?  4 : Build.maxLeafSize;                                               // The maximum number of entries in a leaf
    maxBranchSize = Build.maxBranchSize == null ?  3 : Build.maxBranchSize;                                             // The maximum number of entries in a branch
    numberOfNodes = Build.numberOfNodes == null ? 99 : Build.numberOfNodes;                                             // The maximum number of leaves and branches combined
    maximumNumberOfLevels = logTwo(numberOfNodes);                                                                      // The maximum number of levels needed to step down through the tree because it is so well balanced
    rootFanLevels = Build.rootFanLevels == null ?  0 : Build.rootFanLevels;                                             // Number of levels including the root to be subjected to root fan out
    leafFanOut    = Build.leafFanOut    == null ?  0 : min(Build.leafFanOut,   maxLeafSize);                            // Fan out at leaf if requested
    branchFanOut  = Build.branchFanOut  == null ?  0 : min(Build.branchFanOut, maxBranchSize);                          // Fan out at branch if requested

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

    final Memory.Ref unitMemoryRef = unitMemory.new Ref(0);                                                             // Memory used by tree
    refNodes       = unitMemoryRef.step(build.memoryPositions.posNodes);                                                // Memory for nodes
    refFreeChain   = unitMemoryRef.step(build.memoryPositions.posFreeChain);                                            // Memory for free chain
    refCount       = unitMemoryRef.step(build.memoryPositions.posCount);                                                // Memory for key count
    refHeight      = unitMemoryRef.step(build.memoryPositions.posHeight);                                               // Memory for height of tree

    mergePath      = new Memory(mnl(), "tree", false);                                                                  // Memory for the steps taken along the merge path - each integer corresponds to the location of a branch in the path from the root to the leaf that should contain the key

    freeChain  = new BitSet(build.freeChain.memory(refFreeChain).parent(this));                                         // Memory for free chain
    for (int i = 0, N = numberOfNodes; i < N; ++i) freeChain.set(new Int(i));                                           // Initial free chain with root as an allocated leaf. Each active leaf or branch resides in a node of the tree allocated from the free chain. Using a single node size greatly simplifies memory management which is crucial in long running processes like database systems.
    leaf();                                                                                                             // Initialize the root as a leaf
    refHeight.putInt(One);                                                                                              // Current height of the tree
    treeCode();
   }

  void     treeCode () {}                                                                                               // Override to apply code to the tree

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
    freeChain.set(a.i());
   }

  Bit isAllocated (Int Node) {return freeChain.getBit(Node).Flip();}                                                    // Check whether a node is allocated

  Int nodeAddress  (Int Node)                                                                                           // Convert an index to a byte address of node in memory
   {if (immediate())
     {if (Node.gt(new Int(numberOfNodes)).b()) stop("Node too big:",                                     Node);         // Check in range
      if (freeChain.getBit(Node).b()) stop("Attempting to access a branch or leaf that has been freed:", Node);         // Complain if the node has been freed and not reallocated
     }
    return Node.Mul(sizeOfNode);                                                                                        // Actual byte position of this node in memory
   }

  enum BranchOrLeaf                                                                                                     // Branch or leaf
   {leaf(1), branch(2);
    private final int value;
    BranchOrLeaf (int Value) {value = Value;}
    int value ()             {return value;}
   }

  Int         root () {return Zero;}                                                                                    // The root is always at node zero
  Bit   isRootLeaf () {return checkType(root(), BranchOrLeaf.leaf);}                                                    // Whether the root is a leaf
  Bit isRootBranch () {return checkType(root(), BranchOrLeaf.branch);}                                                  // Whether the root is a branch
  Int       height () {final Int h = refHeight.getInt(); h.name = "Height"; return h.constant();}                       // Height of tree

  Bit checkType (Int Node, BranchOrLeaf Type)                                                                           // Check the type of a node
   {final Int a = nodeAddress(Node);
    final Int t = unitMemory.getInt(a);
    final Bit r = new Bit(false);
    new If (t.eq(Type.value())) {void Then() {r.set(true);}};
    return r;
   }

  void setType (Int Node, BranchOrLeaf Type)                                                                            // Set the type of a node
   {final Int a = nodeAddress(Node);
    unitMemory.putInt(a, new Int(Type.value()));
   }

  Bit isBranch (Int Node) {return checkType(Node, BranchOrLeaf.branch);}                                                // Whether the indexed node a branch
  Bit   isLeaf (Int Node) {return checkType(Node, BranchOrLeaf.leaf  );}                                                // Whether the indexed node a leaf

  Leaf leaf (Int Node) {return leaf(Node, true);}                                                                       // Index an existing leaf in memory            confirming that it really is a leaf
  Leaf leaf (Int Node, boolean Check)                                                                                   // Index an existing leaf in memory optionally confirming that it really is a leaf
   {if (immediate() && Check && !isLeaf(Node).b()) stop("Not a leaf:", Node);                                           // Check the location actually holds a leaf
    final Memory.Ref r = unitMemory.new Ref(nodeAddress(Node));                                                         // Address leaf
    final Tree    tree = this;                                                                                          // Current tree

    return new Leaf(build.leaf.parent(program()).memory(r).at(Node))                                                    // Base leaf at the indexed address
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
       {final Int m = new Int("maxSize", maxSize());                                                                    // Full size
        if (leafFanOut > 0)                                                                                             // Leaf fan out has been requested
         {final Int h = tree.height();                                                                                  // Make leaves close to the root small to reduce queuing for the root branch when operating multiple parallel readers. This does waste siomemmeory, but not much over the entire tree whilst the reduced queuing at the root is expected to be a significant improvement
          new If (h.le(rootFanLevels)) {void Then() {m.min(leafFanOut);}};                                              // Close enough to the root to  leaf request fan out
         }
        return m;                                                                                                       // Revert to normal definition of full - i.e. when the branch is actually full
       }
     };
   }

  Leaf makeLeaf (Int Node)                                                                                              // Make a leaf from the specified node
   {final Leaf l = leaf(Node, false);
    l.initializeMemory();
    setType(Node, BranchOrLeaf.leaf);
    return l;
   }

  Leaf   leaf ()   {return makeLeaf(allocate());}                                                                       // Create and initialize a branch in memory and return its index

  Branch branch (Int Node) {return branch(Node, true);}                                                                 // Index an existing branch in memory            confirming that it really is a branch
  Branch branch (Int Node, boolean Check)                                                                               // Index an existing branch in memory optionally confirming that it really is a branch
   {if (immediate() && Check && !isBranch(Node).b()) stop("Not a branch:", Node);                                       // Check the location actually holds a branch
    final Memory.Ref   r = unitMemory.new Ref(nodeAddress(Node));                                                       // Address branch
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
       {final Int m = new Int("maxSize", maxSize());                                                                    // Full size
        if (branchFanOut > 0)                                                                                           // Branch fan out has been requested
         {final Int h = tree.height();                                                                                  // Make branches close to the root small to reduce queuing for the root branch when operating multiple parallel readers. This does waste siomemmeory, but not much over the entire tree whilst the reduced queuing at the root is expected to be a significant improvement
          new If (h.le(rootFanLevels)) {void Then() {m.min(branchFanOut);}};                                            // Close enough to the root to branch request fan out
         }
        return m;                                                                                                       // Revert to normal definition of full - i.e. when the branch is actually full
       }

     };
   }

  Branch makeBranch (Int Node)                                                                                          // Make a branch from the specified node
   {final Branch b = branch(Node, false);
    b.initializeMemory();
    setType(Node, BranchOrLeaf.branch);
    return b;
   }

  Branch branch () {return makeBranch(allocate());}                                                                     // Create and initialize a branch in memory and return its index
  Int     count () {return refCount.getInt();}                                                                          // Number of keys in tree
  void countInc () {refCount.putInt(count().inc());}                                                                    // Increment the key count
  void countDec () {refCount.putInt(count().dec());}                                                                    // Decrement the key count

  StringBuilder dumpTree ()                                                                                             // Dump the tree
   {subStart("Tree.dumpTree");
    final StringBuilder s = new StringBuilder();
    final Int           f = new Int(numberOfNodes()).sub(freeChain.count());
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
   {Bit valid = new Bit("valid");                                                                                       // Whether the search results are valid
    Int  key   = new Int ("key");                                                                                       // Search key
    Int  leaf  = new Int ("leaf index");                                                                                // Leaf that should contain the key

    void start (Int Key) {key.set(Key); valid.clear();}                                                                 // Start the find operation

    void set (Int Leaf)                                                                                                 // Set the find results
     {valid.set();
      leaf.set(Leaf);
     }

    public String toString ()                                                                                           // Print the find results
     {subStart("Tree.toString");
      final StringBuilder s = new StringBuilder();
      new I() {void a() {s.append("Find : "+key+" "+valid+"\n");} boolean trace() {return false;}};
      final StringBuilder l = leaf(leaf).print();
      new I() {void a() {s.append(l);}                            boolean trace() {return false;}};
      subFinish();
      return ""+s;
     }
   }

  FindLeaf findLeaf (Int Key)                                                                                           // Find the specified key in a leaf in the tree
   {subStart("Tree.findLeaf");
    final Int      p = root().dup();                                                                                    // Start at root
    final FindLeaf f = new FindLeaf();                                                                                  // Find results
    f.start(Key);

    new For(mnl())                                                                                                      // Step down from branch to branch
     {void body(Int Index, Bit Continue)
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

    if (immediate && !f.valid.b()) stop("Find fell off the end of tree after this many searches:", mnl());
    subFinish();
    return f;
   }

final Tree T = this;
  final class Path                                                                                                      // Record the path from the root to the leaf that should contain a key
   {final Int          key = new Int("key");                                                                            // Search key
    final Int         leaf = new Int("leaf");                                                                           // Leaf that should contain the key
    final Int         step = new Int("step");                                                                           // Current step in the path
    final Bint       split = new Bint();                                                                                // The splitting branch is the uppermost branch directly connected to the leaf by intervening full branches which will all have to be split from the top down to permit the splitting of a full leaf
    final Memory.Ref  path = mergePath.new Ref(0);                                                                      // Branches along path

    Path(Int Key)
     {subStart("Tree.Path");
      final Int p = root().dup();                                                                                       // Start at root
      final Bit valid = new Bit(false);                                                                                 // Whether a leaf was reached

      key .set(Key);                                                                                                    // Record search key
      step.set(0);                                                                                                      // Start at the root
      mergePath.clear();                                                                                                // Clear the path

      new For(mnl())                                                                                                    // Step down from branch to branch
       {void body(Int Index, Bit Continue)
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
      if (immediate() && !valid.b()) stop("Find fell off the end of tree after this many searches:", mnl());
      subFinish();
     }

    void splitPoint()                                                                                                   // Locate the split point: the uppermost full branch directly connected to the leaf by intervening full branches which will have to be split from the top back down to the parent of the leaf to permit the splitting of a full leaf
     {subStart("Tree.splitPoint");
      final Int u = new Int();                                                                                          // Location of split point
      new For(step)                                                                                                     // Number of steps in path
       {void body(Int Index, Bit Continue)                                                                              // Step up from leaf to root
         {final Int p = step.Sub(Index).dec();                                                                          // Position on path
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
      new If (split)                                                                                                    // The top most branch to split
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
          final Bint            L = new Bint();                                                                         // There are four possibilities to consider
          final Int            I0 = Zero;                                                                               // Non fast integer constants
          final Int            I1 = One;                                                                                // Non fast integer constants
          final Int            I2 = new Int(2).constant();                                                              // Non fast integer constants
          final Int            I3 = new Int(3).constant();                                                              // Non fast integer constants
          new ForCount(4)                                                                                               // Locate the left sibling
           {void body(Int Index)
             {new If (Index.eq(I0))                                                                                     // This arrangement reduces the  amount of inline code produced by mergeLeftIntoRightSibling
               {void Then()             {L.copy(mergeLeftLeft(  p, d.slot));}
                void Else()
                 {new If (Index.eq(I1))
                   {void Then()         {L.copy(mergeRightRight(p, d.slot));}
                    void Else()
                     {new If (Index.eq(I2))
                       {void Then()     {L.copy(mergeLeft(      p, d.slot));}
                        void Else()
                         {new If (Index.eq(I3))
                           {void Then() {L.copy(mergeRight(     p, d.slot));}
                           };
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
          refHeight.putInt(refHeight.getInt().dec());                                                                   // Decrease the height of the tree
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

  Path path (Int Key)                                                                                                   // The path from the root to the leaf that should contain the specified key
   {final Path f = new Path(Key);                                                                                       // Find results
    return f;
   }

  public void insert (Int Key, Int Data)                                                                                // Insert a key, data pair into the tree
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
             {void Then()                                                                                               // Split the root leaf to make room for  the new key
               {final Leaf l = leaf(), r = leaf();                                                                      // Child leaves of root branch
                l.copy(R);                                                                                              // Duplicate the root
                final Int   sk = l.splitRight(r);                                                                       // Split the root leaf in two
                final Branch b = makeBranch(root());                                                                    // Make the root into a branch
                b.insert(sk, l.getLocation().i());                                                                      // Insert the left leaf
                b.top(r.getLocation().i());                                                                             // The right leaf becomes top of the root branch
                new If (Key.le(sk)) {void Then() {l.insert(Key, Data);} void Else() {r.insert(Key, Data);}};            // Insert left or right leaf depending on key versus splitting key
                b.level(One);                                                                                           // Level of a root branch made by splitting a leaf
                refHeight .putInt(refHeight.getInt().inc());                                                            // Increase height of tree

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
    p.mergeUp();                                                                                                        // Merge nodes on either side of the path going up from the leaf to towards the root
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

  private Int splitRootBranch ()                                                                                        // Split the root assuming that it is a branch
   {subStart("Tree.splitRootBranch");
    final Branch R = branch(root());                                                                                    // The root
    if (immediate() && isRootLeaf()   .b()) stop("Cannot split the root as a branch because it is not a branch");       // Check that it is a branch
    if (immediate() && R.full().Flip().b()) stop("Cannot split the root because it is not full");                       // Check that the root is full
    final Branch l = branch();                                                                                          // New left branch
    final Branch r = branch();                                                                                          // New right branch
    l.copy(R);                                                                                                          // Copy the root into the left branch
    final Int sk = l.splitRight(r);                                                                                     // Splitting key
    R.clear();                                                                                                          // Clear the root
    makeBranch(R.getLocation().i());                                                                                    // Mark the root as a branch
    R.insertEmpty(sk, l.getLocation().i());                                                                             // Insert the left branch below the splitting key
    R.top(r.getLocation().i());                                                                                         // Insert right as top of root
    R.refLevel.putInt(l.refLevel.getInt().inc());                                                                       // Level of root is no one more than that of the branches below
    refHeight.putInt(refHeight.getInt().inc());                                                                         // Increase height of tree
    subFinish();
    return sk;                                                                                                          // Return the splitting key
   }

//D2 Merge                                                                                                              // Merge nodes in the tree to make the tree narrower
//D3 Merge Left                                                                                                         // Merge single and double left

  Bit mergeLeftLeafIntoRightSibling (Branch Parent, Int Left, Leaf Right)                                               // Merge the specified left leaf sibling into its right sibling if possible.  The left sibling is specified by the index of its slot in the specified parent, the right by a leaf description
   {subStart("Tree.mergeLeftLeafIntoRightSibling");
    final Bit   m = new Bit(false);                                                                                     // Whether the merge was performed or not - assume it will not until we discover otherwise
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

  Bit mergeLeftBranchIntoRightSibling (Branch Parent, Int Left, Branch Right)                                           // Merge the specified left branch sibling into its right sibling if possible separating them with the specified splitting key.  The left sibling is specified by the index of its slot in the specified parent, the right by a leaf description
   {subStart("Tree.mergeLeftBranchIntoRightSibling");
    final Bit    m = new Bit(false);                                                                                     // Whether the merge was performed or not - assume it will not until we discover otherwise
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

  Bit mergeLeftIntoRightSibling (Branch Parent, Int Left)                                                               // Merge the specified left sibling into its right sibling if possible.  The left sibling is specified by the index of its slot in the specified parent
   {subStart("Tree.mergeLeftIntoRightSibling");
    final Bit    m = new Bit(false);                                                                                    // Whether the merge was performed or not - assume it will not until we discover otherwise
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
//                                                         16                                                                |
//                                                         (0)                                                               |
//                                                         [9,2]                                                             |
//        4             8                12                                20               24              28               |
//        (9,0,2)       (9,0,2)          (9,0,2)                           (6,0)            (6,0)           (6,0)            |
//        [3,0]         [4,2]            [7,4]                             [10,0]           [5,2]           [12,4]           |
// 1,2,3,4       5,6,7,8       9,10,11,12       13,14,15,16     17,18,19,20      21,22,23,24     25,26,27,28      29,30,31,32|
// (3,9,0)       (4,9,2)       (7,9,4)          (8,9)           (10,6,0)         (5,6,2)         (12,6,4)         (2,6)      |

  class Traverse                                                                                                        // Traverse the tree in order by maintaining a stack of outstanding actions
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
     {final Slots          s = slots(Index, maxBranchSize);                                                             // Slots for branch
      final Memory.Ref nodes = refNodes.step(Index*build.nodeSize + 1 + s.build.size());                                // Array of child nodes
      final int            t = refNodes.step(Index*build.nodeSize + build.branch.memoryPositions.posTop  ).getInt(0);   // Top reference
      final int            v = refNodes.step(Index*build.nodeSize + build.branch.memoryPositions.posLevel).getInt(0);   // Level of branch

      if (isLeaf(t))
       {for(int i = 0; i < maxBranchSize*2; ++i)                                                                        // Each leaf
         {if (s.getSlotToKeysInUse(i))
           {final int k = s.getSlotToKeyValue(i);
            final int n = s.getSlotToKeyIndex(i);
            final int l = nodes.getInt(n);
            pLeaf(l, Depth+1, Index, i);
            branchSlot(Index, Depth, v, Parent, i, k, l);                                                               // Print the connection back to the parent node.
           }
         }
        branchTop(t, Depth);
        pLeaf(t, Depth+1, Index, -1);
       }
      else
       {for(int i = 0; i < maxBranchSize*2; ++i)                                                                        // Each sub branch
         {if (s.getSlotToKeysInUse(i))                                                                                  // Slot in use
           {final int k = s.getSlotToKeyValue(i);                                                                       // Key in slot
            final int n = s.getSlotToKeyIndex(i);                                                                       // Index of data associated with key
            final int c = nodes.getInt(n);                                                                              // Child branch or leaf
            if (Depth <= maximumNumberOfLevels) traverse(c, Index, Depth+1);                                            // Terminate large traverses perhaps produced in error

            branchSlot(Index, Depth, v, Parent, i, k, c);
           }
         }
        branchTop(t, Depth);
        if (Depth <= maximumNumberOfLevels) traverse (t, Index, Depth+1);
       }
     }

    Traverse ()                                                                                                         // Traverse the tree visiting each leaf and branch in order
     {if (isLeaf(0)) pLeaf    (0, 0, 0, 0);
      else           traverse (0, 0, 0);
     }

    void pLeaf(int Index, int Depth, int Parent, int ParentSlot)                                                        // Process a leaf
     {final Slots         slots = slots(Index, maxLeafSize);
      final Stack<Integer> keys = new Stack<>();
      for(int i = 0; i < maxLeafSize*2; ++i) if (slots.getSlotToKeysInUse(i)) keys.push(slots.getSlotToKeyValue(i));
      leaf(Index, Depth, Parent, ParentSlot, keys);
     }

    void leaf (      int Index, int Depth, int Parent, int Slot, Stack<Integer> Keys)                                   // Process a leaf
     {say("LLLL", "Index", Index, "Depth", Depth, "Parent", Parent, "Slot", Slot, "Keys", Keys, slots(Index, maxLeafSize));
     }

    void branchSlot (int Index, int Depth, int Level, int Parent, int Slot, int Key, int Child)                         // Process branch slot by printing the tree to the left of the slot and then the slot
     {say("BBBB", "Index", Index, "Depth", Depth, "Level", Level, "Parent", Parent, "Slot", Slot, "Key",  Key, "Child", Child, slots(Index, maxBranchSize));
     }

    void branchTop ( int Index, int Depth)                                                                              // Process branch top by printing its sub tree to the right
     {say("TTTT", "Index", Index, "Depth", Depth);
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

    Print(boolean Context)                                                                                              // Print the tree optionally supplying the context of each branch and leaf
     {subStart("Tree.Print");

      new Traverse()
       {@Override void leaf(int Index, int Depth, int Parent, int Slot, Stack<Integer> Keys)                            // Process a leaf
         {final StringJoiner k = new StringJoiner(",");
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

        @Override void branchSlot(int Index, int Depth, int Level, int Parent, int Slot, int Key, int Child)            // Print keys of branch and optionally the details of the parent and the children of this branch
         {final int d = Depth * linesToPrintABranch;
          if (Context)                                                                                                  // Print relationships with surrounding nodes
           {pad(d+3);                                                                                                   // Pad the output area so that all the lines have the same length
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

        @Override void branchTop(int Index, int Depth)                                                                  // Print node referenced by top
         {if (Context)
           {final int d = Depth * linesToPrintABranch;
            pad(d+3);                                                                                                   // Pad the output area so that all the lines have the same length
            trimRight(P.elementAt(d+1)).append(""+Index);                                                               // Add index of top to slot second line
           }
         }
       };
      subFinish();
     }

    void pad(int level)                                                                                                 // Pad the strings at each level of the tree so we have a vertical face to continue with - a bit like Marc Brunel's tunneling shield
     {for (int i = P.size(); i < level; ++i) P.push(clearStringBuilder(new StringBuilder()));                           // Make sure we have a full deck of strings
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
        boolean trace () {return false;}
       };
      return t;
     }
   }

  StringBuilder  dump () {subStart("Tree.dump" ); var s = new Print(true) .printCollapsed(); subFinish(); return s;}    // Dump the tree
  StringBuilder print () {subStart("Tree.print"); var s = new Print(false).printCollapsed(); subFinish(); return s;}    // Print the tree

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
    t.ok(()->t.mainMemoryMd5Sum(), "22a420772637045e7d9ae61e800574cb");

    //stop(t.dumpTree());
    if (Ex) ok  (t.dumpTree(), """
Tree memory dump
Leaf   size   :   23
Branch size   :   34
Node   size   :   34
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
    t.ok(()->t.mainMemoryMd5Sum(), "3d21553ae48ccd5ffaabaa5e92eab08d");
    //stop(t.dumpTree());
    if (Ex) ok  (t.dumpTree(), """
Tree memory dump
Leaf   size   :   23
Branch size   :   34
Node   size   :   34
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
    t.ok(()->t.mainMemoryMd5Sum(), "c6dbe9dd44a0eecce5cfcf65c22389ae");
    //stop(t.dumpTree);
    if (Ex) ok(t.dumpTree(), """
Tree memory dump
Leaf   size   :   23
Branch size   :   34
Node   size   :   34
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
    t.ok(()->t.mainMemoryMd5Sum(), "834280feda4034532dd30fb92002eea5");
    //stop(t.dumpTree());
    if (Ex) ok(t.dumpTree(), """
Tree memory dump
Leaf   size   :   23
Branch size   :   34
Node   size   :   34
MaxLeafSize   :    2
MaxBranchSize :    3
NumberOfNodes :    4
Allocations   :    0
Number of Keys:    0
Height        :    1
""");

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
        height().ok(4);

        //stop(mainMemoryMd5Sum());
        ok(()->mainMemoryMd5Sum(), "c039873361f0bdc304c9b2def96e7ab0");

        //stop(dump());
        if (Ex) ok(dump(), """
                                                                                                                           0016                                                                                                                                 |
                                                                                                                           (0,3)20                                                                                                                              |
                                                                                                                           [19,3]3                                                                                                                              |
                                                   0008                                                                                                                                          0024                                                           |
                                                   (19,0,2)14                                                                                                                                    (20,0,2)6                                                      |
                                                   [9,2]2                                                                                                                                        [21,2]2                                                        |
       0002           0004           0006                             0010             0012              0014                              0018              0020              0022                               0026            0028            0030          |
       (9,19,0)       (9,19,2)       (9,19,4)8                        (14,19,0)        (14,19,2)         (14,19,4)13                       (21,20,0)         (21,20,2)         (21,20,4)18                        (6,20,0)        (6,20,2)        (6,20,4)2     |
       [3,0]1         [4,2]1         [7,4]1                           [10,0]1          [5,2]1            [12,4]1                           [15,0]1           [11,2]1           [17,4]1                            [22,0]1         [16,2]1         [24,4]1       |
1,2            3,4            5,6             7,8            9,10              11,12            13,14               15,16         17,18             19,20             21,22               23,24           25,26           27,28           29,30            31,32|
(3,9,0)        (4,9,2)        (7,9,4)         (8,9)          (10,14,0)         (5,14,2)         (12,14,4)           (13,14)       (15,21,0)         (11,21,2)         (17,21,4)           (18,21)         (22,6,0)        (16,6,2)        (24,6,4)         (2,6)|
""");

        //stop(print());
        if (Ex) ok(print(), """
                                                           0016                                                                    |
                        0008                                                                   0024                                |
   0002   0004   0006           0010     0012     0014              0018     0020     0022              0026     0028     0030     |
1,2    3,4    5,6    7,8    9,10    11,12    13,14    15,16    17,18    19,20    21,22    23,24    25,26    27,28    29,30    31,32|
""");

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

        //stop(mainMemoryMd5Sum(), dump(), print());
        ok(()->mainMemoryMd5Sum(), "c8fd801ae2fbd65d306cb86b4cb36ce6");

        //stop(dump());
        if (Ex) ok(dump(), """
                                                         0016                                                                    |
                                                         (0,2)6                                                                  |
                                                         [9,2]2                                                                  |
       0004          0008             0012                                0020              0024              0028               |
       (9,0,0)       (9,0,2)          (9,0,4)8                            (6,0,0)           (6,0,2)           (6,0,4)2           |
       [3,0]1        [4,2]1           [7,4]1                              [10,0]1           [5,2]1            [12,4]1            |
1,2,3,4       5,6,7,8       9,10,11,12        13,14,15,16      17,18,19,20       21,22,23,24       25,26,27,28        29,30,31,32|
(3,9,0)       (4,9,2)       (7,9,4)           (8,9)            (10,6,0)          (5,6,2)           (12,6,4)           (2,6)      |
""");

        //stop(print());
        if (Ex) ok(print(), """
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
           {insert(new Int(N).sub(Index), Index);
            dumpProgramState("AAAA");
           }
         };

        //stop(mainMemoryMd5Sum(), dump(), print());
        ok(()->mainMemoryMd5Sum(), "6b2ad5d037c4e256818af9d17310f1cf");

        //stop(dump());
        if (Ex) ok(dump(), """
                                                          0016                                                                    |
                                                          (0,2)6                                                                  |
                                                          [9,2]2                                                                  |
        0004          0008             0012                                0020              0024              0028               |
        (9,0,0)       (9,0,2)          (9,0,4)8                            (6,0,0)           (6,0,2)           (6,0,4)2           |
        [12,0]1       [5,2]1           [10,4]1                             [7,0]1            [4,2]1            [3,4]1             |
1,2,3,4        5,6,7,8       9,10,11,12        13,14,15,16      17,18,19,20       21,22,23,24       25,26,27,28        29,30,31,32|
(12,9,0)       (5,9,2)       (10,9,4)          (8,9)            (7,6,0)           (4,6,2)           (3,6,4)            (2,6)      |
""");

        //stop(print());
        if (Ex) ok(print(), """
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

        //stop(mainMemoryMd5Sum(), dump(), print());
        ok(()->mainMemoryMd5Sum(), "9ecc3079854a5469e66f53479057706f");

        //stop(dump());
        if (Ex) ok(dump(), """
                                                         0015                                                            0026                           |
                                                         (0,1)                                                           (0,4)6                         |
                                                         [5,1]2                                                          [11,4]2                        |
        0004          0007            0011                                0019            0021            0024                             0030         |
        (5,0,0)       (5,0,2)         (5,0,4)4                            (11,0,1)        (11,0,4)        (11,0,5)7                        (6,0,2)2     |
        [14,0]1       [1,2]1          [9,4]1                              [12,1]1         [3,4]1          [8,5]1                           [10,2]1      |
1,2,3,4        5,6,7         8,9,10,11        12,13,14,15      16,17,18,19        20,21           22,23,24         25,26        27,28,29,30        31,32|
(14,5,0)       (1,5,2)       (9,5,4)          (4,5)            (12,11,1)          (3,11,4)        (8,11,5)         (7,11)       (10,6,2)           (2,6)|
""");

        //stop(print());
        if (Ex) ok(print(), """
                                            0015                                         0026                    |
       0004     0007         0011                          0019     0021        0024                    0030     |
1,2,3,4    5,6,7    8,9,10,11    12,13,14,15    16,17,18,19    20,21    22,23,24    25,26    27,28,29,30    31,32|
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
              if (i >= 5 && i <= 14) height().ok(2);
              if (i >= 15)           height().ok(3);
             }
           }
         };

        final StringBuilder s = Ex ? print() : null;
        final StringBuilder m = new StringBuilder();

        new ForCount(new Int(N))
         {void body(Int Index)
           {delete(Index.Inc());
            if (Ex) s.append(print());
            new I() {void a() {m.append(mainMemoryMd5Sum()+"\n");} boolean trace() {return false;}};
            dumpProgramState("BBBB");
            if (immediate())
             {final int i = Index.i();
              if (i >=  0 && i <= 18) height().ok(3);
              if (i >= 19 && i <= 26) height().ok(2);
              if (i >= 27)            height().ok(1);
             }
           }
         };

        //stop(m);
        ok(()->m, """
cf86f7d811120e97c57bd0c85ae0f924
9a3c6287f7516a68681461c97133ccef
14d14b76c50882fe5bc876520258cd37
41b48525e0fb8f55c48889bc53cc9275
de54f716ba595c1e123a14e2559c7523
a5b0fed3fe44ddace85d74da7fdff245
73b77865a44c42903026dbb40135b152
83248a092c997ffc9902840ddb9c37c9
385a0295f0c1e7f65219355e4ae60f73
88bcd7a964015dc3a75ab5c4698b4ec1
e7146136e874017d412b393f47ca66b8
85ec1e7c4ec637e4c77e0455d47e2c4b
3c5a9a54dcc9f963243b09ca79752944
e50d1c0c0d9928f7aba4ad37e0a17dad
61cff5305ddd574f04e16dfd4f4fa621
1d15b2a4b6a94db69a4cf8b12147fc4e
c5444ed854e830a3551d0e4befe7a4e7
4c40789ee6ebe9fea2c6730ed0bb32de
5922a7eb7b3f0a24d04a58c8632563f0
bcbeaaf9d6ac873453f3338df4bc9ed3
38239a8bf485fd2724456e00cd61e42f
e39773ec7325c7d89b750fe274243df7
6385ff5bb82a410f35715340ccb36c20
f2c82a35686a39c996053916dd1b535a
95d21bd9a64884751740f7e87896d466
99d4748b65056ccacfd98451d9cf2846
384e39537873c257e3d26e3fcc0802d2
55ff5b5255fd28b94898c2dd8a7ff2bf
cf54d09bad8fa62e79f49a3f11b492b2
055a1d0c765b2d9bcf0cd490f78bc8c4
2a434d0989a96061fe60c3e58357064b
7f3339bf5a5a1f8fd07ed045a50fa6b8
""");

        //stop(s);
        if (Ex) ok(""+s, """
                                               0016                                                        |
       0004       0008          0012                          0020           0024           0028           |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
                                             0016                                                        |
     0004       0008          0012                          0020           0024           0028           |
2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
                                           0016                                                        |
   0004       0008          0012                          0020           0024           0028           |
3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
                                         0016                                                        |
 0004       0008          0012                          0020           0024           0028           |
4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
                                    0016                                                        |
       0008          0012                          0020           0024           0028           |
5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
                                  0016                                                        |
     0008          0012                          0020           0024           0028           |
6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
                                0016                                                        |
   0008          0012                          0020           0024           0028           |
7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
                              0016                                                        |
 0008          0012                          0020           0024           0028           |
8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
                         0016                                                        |
          0012                          0020           0024           0028           |
9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
                       0016                                                        |
        0012                          0020           0024           0028           |
10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
                    0016                                                        |
     0012                          0020           0024           0028           |
11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
                 0016                                                        |
  0012                          0020           0024           0028           |
12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
           0016                                                        |
                          0020           0024           0028           |
13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
        0016                                                        |
                       0020           0024           0028           |
14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
     0016                                                        |
                    0020           0024           0028           |
15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
  0016                                                        |
                 0020           0024           0028           |
16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
0016                                                        |
               0020           0024           0028           |
    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
0016                                                     |
            0020           0024           0028           |
    18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
0016                                                  |
         0020           0024           0028           |
    19,20    21,22,23,24    25,26,27,28    29,30,31,32|
0016                                               |
      0020           0024           0028           |
    20    21,22,23,24    25,26,27,28    29,30,31,32|
0016           0024           0028           |
    21,22,23,24    25,26,27,28    29,30,31,32|
        0024           0028           |
22,23,24    25,26,27,28    29,30,31,32|
     0024           0028           |
23,24    25,26,27,28    29,30,31,32|
  0024           0028           |
24    25,26,27,28    29,30,31,32|
           0028           |
25,26,27,28    29,30,31,32|
        0028           |
26,27,28    29,30,31,32|
     0028           |
27,28    29,30,31,32|
  0028           |
28    29,30,31,32|
29,30,31,32|
30,31,32|
31,32|
32|
""");
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

        final StringBuilder s = Ex ? print() : null;
        final StringBuilder m = new StringBuilder();

        new ForCount(new Int(N))
         {void body(Int Index)
           {delete(new Int(N).sub(Index));

            if (Ex) s.append(print());
            new I() {void a() {m.append(mainMemoryMd5Sum()+"\n");} boolean trace() {return false;}};

            dumpProgramState("BBBB");
           }
         };

        //stop(m);
        ok(()->m, """
0238830bc488436dc1d0e68971375527
2e60ed03a063b98e9244122509f7d992
313bd1df2a36622d3a778adf22ddfc0f
debb0050354944e100f0725e8179dfb2
c50a8311b5ff38ade22e570a8cc55ae7
c1b9d71b0125a63bd7c54eb8e0b366b1
e223d65576a7b30eba91b97bba61301b
75fe5b036fb2352a2fa73d22d4d848ca
5ea1880a0de68a61c97ac29cd74e51bc
68f022cdfef7e9a7107ea304f3efb243
ef92c66b409f8ed9029f175fe7b18f95
40f75aa6ca118b973941414b55d35fee
969f7798ffa6ae9001460971e9370817
cb3c06baeb04c9d03fdcf8d21e403fb1
b6f03ae2825760bf62cdc5012fad8a3f
7d9f67daeac06a97e591478787be26cd
40e1a7535bd53a9b776cf72b5a931f0c
04fd87ea88ca4a0dd61fe6080defce29
0058a17ab2ee62252ff5022f67ab17f5
c249f78cd826a84783434a7c888aef91
9b1722dca20a068589fa4071c822b969
4368afabc3ac71265d806e6c8a87257b
2b4316a32bf1879dff022a1e54d29c5d
48af8451482e0e56b1f90b7eed0f099f
b0223e6f6a2572fc3044220ef5d754a5
ece194bbd81eca04ed9fe72079a2cddb
f020b9caf3a9c35d7eaac1e3681dba24
4da15381637ab333e5f4a4adad6e26f1
47685c0df76a15a6cd6c7c682cb45a45
d07ee2494ca6b00607f1649bdee411c2
5bded26293d5bfc2fc1adf490a6a0802
bfa4741216b0f0629d2a4899154e98f9
""");

        //stop(s);
        if (Ex) ok(""+s, """
                                               0016                                                        |
       0004       0008          0012                          0020           0024           0028           |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
                                               0016                                                     |
       0004       0008          0012                          0020           0024           0028        |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31|
                                               0016                                                  |
       0004       0008          0012                          0020           0024           0028     |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30|
                                               0016                                               |
       0004       0008          0012                          0020           0024           0028  |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29|
                                               0016                                         |
       0004       0008          0012                          0020           0024           |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28|
                                               0016                                      |
       0004       0008          0012                          0020           0024        |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27|
                                               0016                                   |
       0004       0008          0012                          0020           0024     |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26|
                                               0016                                |
       0004       0008          0012                          0020           0024  |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25|
                                               0016                          |
       0004       0008          0012                          0020           |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24|
                                               0016                       |
       0004       0008          0012                          0020        |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23|
                                               0016                    |
       0004       0008          0012                          0020     |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22|
                                               0016                 |
       0004       0008          0012                          0020  |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21|
                                               0016           |
       0004       0008          0012                          |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20|
                                               0016        |
       0004       0008          0012                       |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19|
                                               0016     |
       0004       0008          0012                    |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18|
                                               0016  |
       0004       0008          0012                 |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17|
                                               0016|
       0004       0008          0012               |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    |
                                            0016|
       0004       0008          0012            |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15    |
                                         0016|
       0004       0008          0012         |
1,2,3,4    5,6,7,8    9,10,11,12    13,14    |
                                      0016|
       0004       0008          0012      |
1,2,3,4    5,6,7,8    9,10,11,12    13    |
       0004       0008          0016|
1,2,3,4    5,6,7,8    9,10,11,12    |
       0004       0008       |
1,2,3,4    5,6,7,8    9,10,11|
       0004       0008    |
1,2,3,4    5,6,7,8    9,10|
       0004       0008 |
1,2,3,4    5,6,7,8    9|
       0004       |
1,2,3,4    5,6,7,8|
       0004     |
1,2,3,4    5,6,7|
       0004   |
1,2,3,4    5,6|
       0004 |
1,2,3,4    5|
1,2,3,4|
1,2,3|
1,2|
1|
""");

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

        final StringBuilder s = Ex ? print() : null;
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

            if (Ex) s.append(print());
            new I() {void a() {m.append(mainMemoryMd5Sum()+"\n");} boolean trace() {return false;}};
            dumpProgramState("BBBB");
           }
         };

        //stop(m);
        ok(()->m, """
78e490a168eda3f46dec99d4b930bacc
8cc323dcac345399639e558ffd0796e4
2b72962cccc76e81e3864a48e1b151d7
5d499946d9d2ea3a284b494f0b982364
e4a91c88dddd6312b4bce910c4462cb7
99642541846b99f4c68fb3dd87e95626
1d554b6aeaa61604ae8f23c6e8b949c7
35ee14fa668e09e490e35e3cf956826f
a9fe3e38beb8db0ccae401c800e82961
e15df7e7f5c11073eff6ca6980a367be
6bc13c31fe65e82a971e5a596bfbe611
dbe68969f14ac68c532f369fda701ed2
32c5af01729a6709ab582eae05fe21cf
4a4ae9ef41319e2d848091414c59344a
4ed395b468847c72fe96d81690d2d4bd
4d90e829821abe366589db4d7c128e20
778e24dee43460eb8fbc340d726875e7
e9600c72def3e63f13a98f0f606bac55
9cb83735ecedb9ce219e27ddf848853d
90a012ecb4cc00637689e4a1b8bf3c9e
76370fd6d2533032f50486a5a8aba77d
127911abb919d7998c27b1c8921b87ac
f0eb240379b227b85b90f6b8a2efe9f1
5b65077a365a9ad42963a2625446d7ac
988058b3791f7c0cd535a9a9a2b2d7cc
902634826e0d95c8065a78ca134d0cd9
0b7c8cc31469444da00f55d1d42ba175
01d2834a1c1200828e5c3177c054efaf
d8cbee1e2b1e7ea3d661af0d78393841
82b587b7268254a61509bac8c35c754c
007cda1acd6c514fc227ed9f2c282a49
d40966b68e48bc20b91312f416774d2c
""");

        //stop(s);
        if (Ex) ok(s, """
                                               0016                                                        |
       0004       0008          0012                          0020           0024           0028           |
1,2,3,4    5,6,7,8    9,10,11,12    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
                                            0016                                                        |
       0004       0008       0012                          0020           0024           0028           |
1,2,3,4    5,6,7,8    9,10,11    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
                                          0016                                                        |
     0004       0008       0012                          0020           0024           0028           |
1,2,4    5,6,7,8    9,10,11    13,14,15,16    17,18,19,20    21,22,23,24    25,26,27,28    29,30,31,32|
                                          0016                                                     |
     0004       0008       0012                          0020           0024        0028           |
1,2,4    5,6,7,8    9,10,11    13,14,15,16    17,18,19,20    21,22,23,24    25,26,28    29,30,31,32|
                                        0016                                                     |
   0004       0008       0012                          0020           0024        0028           |
2,4    5,6,7,8    9,10,11    13,14,15,16    17,18,19,20    21,22,23,24    25,26,28    29,30,31,32|
                                        0016                                                  |
   0004       0008       0012                          0020        0024        0028           |
2,4    5,6,7,8    9,10,11    13,14,15,16    17,18,19,20    21,22,24    25,26,28    29,30,31,32|
                                        0016                                               |
   0004       0008       0012                       0020        0024        0028           |
2,4    5,6,7,8    9,10,11    13,14,15,16    17,18,19    21,22,24    25,26,28    29,30,31,32|
                                      0016                                               |
   0004     0008       0012                       0020        0024        0028           |
2,4    5,6,7    9,10,11    13,14,15,16    17,18,19    21,22,24    25,26,28    29,30,31,32|
                                      0016                                            |
   0004     0008       0012                    0020        0024        0028           |
2,4    5,6,7    9,10,11    13,14,15,16    17,19    21,22,24    25,26,28    29,30,31,32|
                                 0016                                            |
       0008       0012                    0020        0024        0028           |
4,5,6,7    9,10,11    13,14,15,16    17,19    21,22,24    25,26,28    29,30,31,32|
                                 0016                                         |
       0008       0012                    0020        0024        0028        |
4,5,6,7    9,10,11    13,14,15,16    17,19    21,22,24    25,26,28    29,30,32|
                                 0016                                      |
       0008       0012                    0020        0024     0028        |
4,5,6,7    9,10,11    13,14,15,16    17,19    21,22,24    26,28    29,30,32|
                              0016                                      |
       0008       0012                 0020        0024     0028        |
4,5,6,7    9,10,11    13,14,15    17,19    21,22,24    26,28    29,30,32|
                           0016                                      |
       0008       0012              0020        0024     0028        |
4,5,6,7    9,10,11    14,15    17,19    21,22,24    26,28    29,30,32|
                           0016                                |
       0008       0012              0020        0024           |
4,5,6,7    9,10,11    14,15    17,19    21,22,24    26,28,29,30|
                     0016                                |
       0008                   0020        0024           |
4,5,6,7    9,10,14,15    17,19    21,22,24    26,28,29,30|
       0008          0016           0024           |
4,5,6,7    9,10,14,15    17,19,22,24    26,28,29,30|
     0008          0016           0024           |
4,6,7    9,10,14,15    17,19,22,24    26,28,29,30|
     0008          0016        0024           |
4,6,7    9,10,14,15    17,19,22    26,28,29,30|
   0008          0016        0024           |
6,7    9,10,14,15    17,19,22    26,28,29,30|
   0008       0016        0024           |
6,7    9,14,15    17,19,22    26,28,29,30|
   0008       0016        0024        |
6,7    9,14,15    17,19,22    28,29,30|
   0008       0016        0024     |
6,7    9,14,15    17,19,22    28,29|
         0016        0024     |
6,7,14,15    17,19,22    28,29|
       0016        0024     |
7,14,15    17,19,22    28,29|
       0016           |
7,14,15    17,19,22,28|
       0016        |
7,14,15    19,22,28|
       0016     |
7,14,15    19,22|
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
    final Tree t = new Tree(new Build().maxLeafSize(4).maxBranchSize(3).numberOfNodes(4).immediate(Ex))
     {void treeCode()
       {new ForCount(2)
         {void body(Int I)
           {new ForCount(2)
             {void body(Int J)
               {insert(J, I);
               }
             };
           }
         };
        //stop(mainMemoryMd5Sum());
        ok(()->mainMemoryMd5Sum(), "0e1ee28654479e2608a482c294f15e12");
        //stop(dumpTree());
        if (Ex) ok(dumpTree(), """
Tree memory dump
Leaf   size   :   41
Branch size   :   34
Node   size   :   41
MaxLeafSize   :    4
MaxBranchSize :    3
NumberOfNodes :    4
Allocations   :    1
Number of Keys:    2
Height        :    1
Leaf           size:   4, count:   2
 Ref   Key  Data
   0     0     1
   1     1     1
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
        ok(()->mainMemoryMd5Sum(), "2aac7256bd1345cb2bb27f81a9a8fcdb");

        //stop(dump());
        if (Ex) ok(dump(), """
                                                                                                                                                                   0020                                                                                                                                                                             0040                                                                                                                                                                             0060                                                                                                                                                                              0080                                                                                                                                                                              0100                                                                             0110                                                                                                                                                            |
                                                                                                                                                                   (0,2)                                                                                                                                                                            (0,6)                                                                                                                                                                            (0,10)                                                                                                                                                                            (0,15)                                                                                                                                                                            (0,16)                                                                           (0,17)12                                                                                                                                                        |
                                                                                                                                                                   [18,2]2                                                                                                                                                                          [29,6]2                                                                                                                                                                          [40,10]2                                                                                                                                                                          [51,15]2                                                                                                                                                                          [62,16]2                                                                         [1,17]2                                                                                                                                                         |
        0002            0004            0006            0008            0010             0012              0014               0016               0018                              0022             0024             0026             0028             0030              0032               0034               0036               0038                              0042             0044             0046             0048             0050              0052               0054               0056               0058                               0062             0064             0066             0068             0070              0072               0074               0076               0078                               0082             0084             0086             0088             0090              0092               0094               0096               0098                              0102           0104            0106             0108                              0112             0114             0116             0118              0120               0122               0124               0126             |
        (18,0,0)        (18,0,2)        (18,0,4)        (18,0,6)        (18,0,8)         (18,0,10)         (18,0,12)          (18,0,14)          (18,0,16)14                       (29,0,0)         (29,0,2)         (29,0,4)         (29,0,6)         (29,0,8)          (29,0,10)          (29,0,12)          (29,0,14)          (29,0,16)25                       (40,0,0)         (40,0,2)         (40,0,4)         (40,0,6)         (40,0,8)          (40,0,10)          (40,0,12)          (40,0,14)          (40,0,16)36                        (51,0,0)         (51,0,2)         (51,0,4)         (51,0,6)         (51,0,8)          (51,0,10)          (51,0,12)          (51,0,14)          (51,0,16)47                        (62,0,0)         (62,0,2)         (62,0,4)         (62,0,6)         (62,0,8)          (62,0,10)          (62,0,12)          (62,0,14)          (62,0,16)58                       (1,0,2)        (1,0,6)         (1,0,10)         (1,0,14)56                        (12,0,2)         (12,0,4)         (12,0,6)         (12,0,8)          (12,0,10)          (12,0,12)          (12,0,15)          (12,0,17)2       |
        [3,0]1          [4,2]1          [5,4]1          [6,6]1          [7,8]1           [8,10]1           [9,12]1            [10,14]1           [13,16]1                          [15,0]1          [16,2]1          [17,4]1          [19,6]1          [11,8]1           [20,10]1           [21,12]1           [22,14]1           [24,16]1                          [26,0]1          [27,2]1          [28,4]1          [30,6]1          [23,8]1           [31,10]1           [32,12]1           [33,14]1           [35,16]1                           [37,0]1          [38,2]1          [39,4]1          [41,6]1          [34,8]1           [42,10]1           [43,12]1           [44,14]1           [46,16]1                           [48,0]1          [49,2]1          [50,4]1          [52,6]1          [45,8]1           [53,10]1           [54,12]1           [55,14]1           [57,16]1                          [59,2]1        [60,6]1         [61,10]1         [63,14]1                          [64,2]1          [65,4]1          [66,6]1          [68,8]1           [69,10]1           [70,12]1           [71,15]1           [72,17]1         |
1,2             3,4             5,6             7,8             9,10            11,12             13,14             15,16              17,18                19,20         21,22            23,24            25,26            27,28            29,30            31,32              33,34              35,36              37,38                39,40         41,42            43,44            45,46            47,48            49,50            51,52              53,54              55,56              57,58                59,60          61,62            63,64            65,66            67,68            69,70            71,72              73,74              75,76              77,78                79,80          81,82            83,84            85,86            87,88            89,90            91,92              93,94              95,96              97,98                99,100         101,102        103,104        105,106          107,108            109,110        111,112          113,114          115,116          117,118          119,120            121,122            123,124            125,126             127,128|
(3,18,0)        (4,18,2)        (5,18,4)        (6,18,6)        (7,18,8)        (8,18,10)         (9,18,12)         (10,18,14)         (13,18,16)           (14,18)       (15,29,0)        (16,29,2)        (17,29,4)        (19,29,6)        (11,29,8)        (20,29,10)         (21,29,12)         (22,29,14)         (24,29,16)           (25,29)       (26,40,0)        (27,40,2)        (28,40,4)        (30,40,6)        (23,40,8)        (31,40,10)         (32,40,12)         (33,40,14)         (35,40,16)           (36,40)        (37,51,0)        (38,51,2)        (39,51,4)        (41,51,6)        (34,51,8)        (42,51,10)         (43,51,12)         (44,51,14)         (46,51,16)           (47,51)        (48,62,0)        (49,62,2)        (50,62,4)        (52,62,6)        (45,62,8)        (53,62,10)         (54,62,12)         (55,62,14)         (57,62,16)           (58,62)        (59,1,2)       (60,1,6)       (61,1,10)        (63,1,14)          (56,1)         (64,12,2)        (65,12,4)        (66,12,6)        (68,12,8)        (69,12,10)         (70,12,12)         (71,12,15)         (72,12,17)          (2,12) |
""");

        //stop(print());
        if (Ex) ok(print(), """
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
        ok(()->mainMemoryMd5Sum(), "28809d8ecdcd4f84d086589eb46c2b18");

        //(dump());
        if (Ex) ok(dump(), """
                                                                                                                                                     0018                                                                                                                                                                   0036                                                                                                                                                                   0054                                                                                                                                                                                                                           0078                                                                                                                                                                                                                                                                                                                                                                                                                                |
                                                                                                                                                     (0,2)                                                                                                                                                                  (0,8)                                                                                                                                                                  (0,14)                                                                                                                                                                                                                         (0,16)24                                                                                                                                                                                                                                                                                                                                                                                                                            |
                                                                                                                                                     [23,2]3                                                                                                                                                                [37,8]3                                                                                                                                                                [50,14]3                                                                                                                                                                                                                       [76,16]3                                                                                                                                                                                                                                                                                                                                                                                                                            |
                                      0006                                                 0012                                                                                                            0024                                                   0030                                                                                                            0042                                                   0048                                                                                                             0060                                                   0066                                                   0072                                                                                                             0084                                                                                                                                                                                   0106                                                                0116                                                    0122                                                      |
                                      (23,0,4)                                             (23,0,13)15                                                                                                     (37,0,4)                                               (37,0,13)29                                                                                                     (50,0,4)                                               (50,0,13)42                                                                                                      (76,0,2)                                               (76,0,8)                                               (76,0,14)59                                                                                                      (24,0,4)                                                                                                                                                                               (24,0,13)                                                           (24,0,16)                                               (24,0,17)7                                                |
                                      [6,4]2                                               [11,13]2                                                                                                        [19,4]2                                                [25,13]2                                                                                                        [33,4]2                                                [38,13]2                                                                                                         [46,2]2                                                [51,8]2                                                [55,14]2                                                                                                         [64,4]2                                                                                                                                                                                [75,13]2                                                            [78,16]2                                                [82,17]2                                                  |
       0002            0004                           0008              0010                                   0014               0016                               0020               0022                                0026               0028                                   0032               0034                               0038               0040                                0044               0046                                   0050               0052                                0056               0058                                0062               0064                                0068               0070                                   0074               0076                                0080               0082                                0086              0088              0090              0092              0094               0096                0098                0100                                                 0108                            0114                                0118               0120                                  0124              0125                 |
       (6,23,4)        (6,23,13)5                     (11,23,4)         (11,23,13)10                           (15,23,4)          (15,23,13)14                       (19,37,4)          (19,37,13)18                        (25,37,4)          (25,37,13)22                           (29,37,4)          (29,37,13)28                       (33,50,4)          (33,50,13)32                        (38,50,4)          (38,50,13)36                           (42,50,4)          (42,50,13)41                        (46,76,4)          (46,76,13)45                        (51,76,4)          (51,76,13)49                        (55,76,4)          (55,76,13)54                           (59,76,4)          (59,76,13)58                        (64,24,4)          (64,24,13)62                        (75,24,1)         (75,24,3)         (75,24,5)         (75,24,7)         (75,24,9)          (75,24,11)          (75,24,13)          (75,24,15)72                                         (78,24,4)                       (78,24,13)1                         (82,24,4)          (82,24,13)81                          (7,24,13)         (7,24,16)2           |
       [3,4]1          [4,13]1                        [8,4]1            [9,13]1                                [12,4]1            [13,13]1                           [16,4]1            [17,13]1                            [20,4]1            [21,13]1                               [26,4]1            [27,13]1                           [30,4]1            [31,13]1                            [34,4]1            [35,13]1                               [39,4]1            [40,13]1                            [43,4]1            [44,13]1                            [47,4]1            [48,13]1                            [52,4]1            [53,13]1                               [56,4]1            [57,13]1                            [60,4]1            [61,13]1                            [65,1]1           [66,3]1           [67,5]1           [69,7]1           [70,9]1            [71,11]1            [73,13]1            [74,15]1                                             [68,4]1                         [77,13]1                            [63,4]1            [80,13]1                              [83,13]1          [79,16]1             |
1,2            3,4               5,6          7,8              9,10                 11,12             13,14             15,16                 17,18         19,20             21,22                 23,24          25,26             27,28                 29,30             31,32             33,34                 35,36         37,38             39,40                 41,42          43,44             45,46                 47,48             49,50             51,52                 53,54          55,56             57,58                 59,60          61,62             63,64                 65,66          67,68             69,70                 71,72             73,74             75,76                 77,78          79,80             81,82                 83,84          85,86             87,88             89,90             91,92             93,94             95,96               97,98               99,100                101,102,103,104,105,106         107,108           109,110,111,112,113,114           115,116         117,118           119,120               121,122          123,124           125                126,127,128|
(3,6,4)        (4,6,13)          (5,6)        (8,11,4)         (9,11,13)            (10,11)           (12,15,4)         (13,15,13)            (14,15)       (16,19,4)         (17,19,13)            (18,19)        (20,25,4)         (21,25,13)            (22,25)           (26,29,4)         (27,29,13)            (28,29)       (30,33,4)         (31,33,13)            (32,33)        (34,38,4)         (35,38,13)            (36,38)           (39,42,4)         (40,42,13)            (41,42)        (43,46,4)         (44,46,13)            (45,46)        (47,51,4)         (48,51,13)            (49,51)        (52,55,4)         (53,55,13)            (54,55)           (56,59,4)         (57,59,13)            (58,59)        (60,64,4)         (61,64,13)            (62,64)        (65,75,1)         (66,75,3)         (67,75,5)         (69,75,7)         (70,75,9)         (71,75,11)          (73,75,13)          (74,75,15)            (72,75)                         (68,78,4)         (77,78,13)                        (1,78)          (63,82,4)         (80,82,13)            (81,82)          (83,7,13)         (79,7,16)          (2,7)      |
""");

        //stop(print());
        if (Ex) ok(print(), """
                                                                    0018                                                                             0036                                                                             0054                                                                                                        0078                                                                                                                                                                                                                                              |
                 0006                    0012                                                  0024                       0030                                                  0042                       0048                                                  0060                       0066                       0072                                                  0084                                                                                                0106                                             0116                             0122                             |
   0002   0004          0008    0010              0014     0016              0020     0022              0026     0028              0032     0034              0038     0040              0044     0046              0050     0052              0056     0058              0062     0064              0068     0070              0074     0076              0080     0082              0086     0088     0090     0092     0094     0096     0098      0100                                  0108                       0114                  0118       0120                  0124   0125           |
1,2    3,4    5,6    7,8    9,10    11,12    13,14    15,16    17,18    19,20    21,22    23,24    25,26    27,28    29,30    31,32    33,34    35,36    37,38    39,40    41,42    43,44    45,46    47,48    49,50    51,52    53,54    55,56    57,58    59,60    61,62    63,64    65,66    67,68    69,70    71,72    73,74    75,76    77,78    79,80    81,82    83,84    85,86    87,88    89,90    91,92    93,94    95,96    97,98    99,100    101,102,103,104,105,106    107,108    109,110,111,112,113,114    115,116    117,118    119,120    121,122    123,124    125    126,127,128|
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
        ok(()->mainMemoryMd5Sum(), "962c6ddd589688d621fe91712a1d1132");

        //(dump());
        if (Ex) ok(dump(), """
                                                                                                                                                             0036                                                                                                                                                                                                                                                 0098                                                                                                                                                                                                                                                                                                                       0162                                                                                                                                                                                                                                                                                                                         0226                                                                                                                                                |
                                                                                                                                                             (0,2)                                                                                                                                                                                                                                                (0,6)                                                                                                                                                                                                                                                                                                                      (0,10)                                                                                                                                                                                                                                                                                                                       (0,14)7                                                                                                                                             |
                                                                                                                                                             [1,2]2                                                                                                                                                                                                                                               [23,6]2                                                                                                                                                                                                                                                                                                                    [32,10]2                                                                                                                                                                                                                                                                                                                     [43,14]2                                                                                                                                            |
       0002          0004          0006                 0012                    0018                     0024                     0030                                              0042                           0050                           0058                           0066                           0074                            0082                            0090                                                                   0106                                   0114                                   0122                                   0130                                   0138                                    0146                                    0154                                                                             0170                                   0178                                   0186                                   0194                                   0202                                    0210                                    0218                                                                             0234                                  0242                                   0250                            |
       (1,0,2)       (1,0,4)       (1,0,6)              (1,0,8)                 (1,0,10)                 (1,0,12)                 (1,0,14)12                                        (23,0,2)                       (23,0,4)                       (23,0,6)                       (23,0,8)                       (23,0,10)                       (23,0,12)                       (23,0,14)21                                                            (32,0,2)                               (32,0,4)                               (32,0,6)                               (32,0,8)                               (32,0,10)                               (32,0,12)                               (32,0,14)30                                                                      (43,0,2)                               (43,0,4)                               (43,0,6)                               (43,0,8)                               (43,0,10)                               (43,0,12)                               (43,0,14)39                                                                      (7,0,4)                               (7,0,15)                               (7,0,17)2                       |
       [3,2]1        [4,4]1        [5,6]1               [8,8]1                  [9,10]1                  [10,12]1                 [11,14]1                                          [13,2]1                        [6,4]1                         [15,6]1                        [17,8]1                        [18,10]1                        [19,12]1                        [20,14]1                                                               [22,2]1                                [24,4]1                                [16,6]1                                [26,8]1                                [27,10]1                                [28,12]1                                [29,14]1                                                                         [31,2]1                                [33,4]1                                [25,6]1                                [35,8]1                                [36,10]1                                [37,12]1                                [38,14]1                                                                         [40,4]1                               [44,15]1                               [34,17]1                        |
1,2           3,4           5,6           7,8,9,10,11,12       13,14,15,16,17,18        19,20,21,22,23,24        25,26,27,28,29,30          31,32,33,34,35,36      37,38,39,40,41,42        43,44,45,46,47,48,49,50        51,52,53,54,55,56,57,58        59,60,61,62,63,64,65,66        67,68,69,70,71,72,73,74         75,76,77,78,79,80,81,82         83,84,85,86,87,88,89,90           91,92,93,94,95,96,97,98       99,100,101,102,103,104,105,106        107,108,109,110,111,112,113,114        115,116,117,118,119,120,121,122        123,124,125,126,127,128,129,130        131,132,133,134,135,136,137,138         139,140,141,142,143,144,145,146         147,148,149,150,151,152,153,154           155,156,157,158,159,160,161,162        163,164,165,166,167,168,169,170        171,172,173,174,175,176,177,178        179,180,181,182,183,184,185,186        187,188,189,190,191,192,193,194        195,196,197,198,199,200,201,202         203,204,205,206,207,208,209,210         211,212,213,214,215,216,217,218           219,220,221,222,223,224,225,226        227,228,229,230,231,232,233,234       235,236,237,238,239,240,241,242        243,244,245,246,247,248,249,250         251,252,253,254,255,256|
(3,1,2)       (4,1,4)       (5,1,6)       (8,1,8)              (9,1,10)                 (10,1,12)                (11,1,14)                  (12,1)                 (13,23,2)                (6,23,4)                       (15,23,6)                      (17,23,8)                      (18,23,10)                      (19,23,12)                      (20,23,14)                        (21,23)                       (22,32,2)                             (24,32,4)                              (16,32,6)                              (26,32,8)                              (27,32,10)                              (28,32,12)                              (29,32,14)                                (30,32)                                (31,43,2)                              (33,43,4)                              (25,43,6)                              (35,43,8)                              (36,43,10)                              (37,43,12)                              (38,43,14)                                (39,43)                                (40,7,4)                              (44,7,15)                              (34,7,17)                               (2,7)                  |
""");

        //stop(print());
        if (Ex) ok(print(), """
                                                                                                                       0036                                                                                                                                                                                                              0098                                                                                                                                                                                                                                                                                   0162                                                                                                                                                                                                                                                                                    0226                                                                                                                                |
   0002   0004   0006              0012                 0018                 0024                 0030                                      0042                       0050                       0058                       0066                       0074                       0082                       0090                                                         0106                               0114                               0122                               0130                               0138                               0146                               0154                                                                  0170                               0178                               0186                               0194                               0202                               0210                               0218                                                                  0234                               0242                               0250                       |
1,2    3,4    5,6    7,8,9,10,11,12    13,14,15,16,17,18    19,20,21,22,23,24    25,26,27,28,29,30    31,32,33,34,35,36    37,38,39,40,41,42    43,44,45,46,47,48,49,50    51,52,53,54,55,56,57,58    59,60,61,62,63,64,65,66    67,68,69,70,71,72,73,74    75,76,77,78,79,80,81,82    83,84,85,86,87,88,89,90    91,92,93,94,95,96,97,98    99,100,101,102,103,104,105,106    107,108,109,110,111,112,113,114    115,116,117,118,119,120,121,122    123,124,125,126,127,128,129,130    131,132,133,134,135,136,137,138    139,140,141,142,143,144,145,146    147,148,149,150,151,152,153,154    155,156,157,158,159,160,161,162    163,164,165,166,167,168,169,170    171,172,173,174,175,176,177,178    179,180,181,182,183,184,185,186    187,188,189,190,191,192,193,194    195,196,197,198,199,200,201,202    203,204,205,206,207,208,209,210    211,212,213,214,215,216,217,218    219,220,221,222,223,224,225,226    227,228,229,230,231,232,233,234    235,236,237,238,239,240,241,242    243,244,245,246,247,248,249,250    251,252,253,254,255,256|
""");

        maxSteps(999_999_999);
        execute();
       }
     };
   }

  static void test_rootFanOut3 ()
   {          test_rootFanOut3(true);
              test_rootFanOut3(false);
   }

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {if (rtg( 1)) test_tree();
    //if (rtg( 2)) test_rootFanOut();
    if (rtg( 3)) test_insert();
    if (rtg( 4)) test_insertMerged();
    if (rtg( 5)) test_insertReverse();
    if (rtg( 6)) test_insertRandom32();
    if (rtg( 7)) test_deleteAscending();                                                                                // 68090 integers, 2374 fast integers, 24307 bits: with both For and ForCount in bitset with fast integer indexes.  Openroad takes hours with  fast integers. Die larger than 2000*2000?  Restricted fast integers to first 4 for loops in bitset:  68090 integers, 1366 fast integers, 24307 bits.
    if (rtg( 8)) test_deleteDescending();
    if (rtg( 9)) test_deleteRandom32();
    if (rtg(10)) test_update();
    if (rtg(11)) test_find();
    if (rtg(12)) test_rootFanOut();
    if (rtg(13)) test_rootFanOut2();
    if (rtg(14)) test_rootFanOut3();
   }

  static void newTests()                                                                                                // Tests being worked on
   {//oldTests();
    //test_insert(!true);
    test_rootFanOut3(true);
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
