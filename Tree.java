//----------------------------------------------------------------------------------------------------------------------
// Btree with stucks implemented as distributed sparse slots
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;

class Tree extends Program                                                                                              // A tree that translates keys into values to be implemented as an application specific integrated circuit
 {final int           maxLeafSize;                                                                                      // The maximum number of entries in a leaf of the tree
  final int         maxBranchSize;                                                                                      // The maximum number of entries in a branch of the tree
  final BitSet          freeChain;                                                                                      // Nodes currently free
  final int         numberOfNodes;                                                                                      // Maximum number of leaves plus branches in this tree
  final int maximumNumberOfLevels;                                                                                      // Maximum number of levels in tree to prevent runaways while debugging
  final int            sizeOfNode;                                                                                      // The size of each node in the tree: a node must be able to hold a branch or a leaf
  final Memory.Ref       refNodes;                                                                                      // The nodes associated with this tree
  final Memory.Ref   refFreeChain;                                                                                      // The free chain for this tree
  final Memory.Ref       refCount;                                                                                      // The number of keys in this tree
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
      final int posFreeChain = posNodes     + unitsNeededForNodes;
      final int posCount     = posFreeChain + unitsNeededForFree;
      final int size         = posCount     + 1;
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

    final Memory.Ref unitMemoryRef = unitMemory.new Ref(0);                                                             // Memory used by tree
    refNodes       = unitMemoryRef.step(build.memoryPositions.posNodes);                                                // Memory for nodes
    refFreeChain   = unitMemoryRef.step(build.memoryPositions.posFreeChain);                                            // Memory for free chain
    refCount       = unitMemoryRef.step(build.memoryPositions.posCount);                                                // Memory for key count

    mergePath      = new Memory(mnl());                                                                             // Memory for the steps taken along the merge path - each integer corresponds to the location of a branch in the path from the root to the leaf that should contain the key

    freeChain  = new BitSet(build.freeChain.memory(refFreeChain).parent(this));                                         // Memory for free chain
    for (int i = 0, N = numberOfNodes; i < N; ++i) freeChain.set(new Int(i));                                           // Initial free chain with root as an allocated leaf. Each active leaf or branch resides in a node of the tree allocated from the free chain. Using a single node size greatly simplifies memory management which is crucial in long running processes like database systems.
    leaf();                                                                                                             // Initialize the root as a leaf
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
     {if (Node.lt(0).b())             stop("Node less than zero:",                                       Node);         // Check not less than zero
      if (Node.gt(numberOfNodes).b()) stop("Node too big:",                                              Node);         // Check in range
      if (freeChain.getBit(Node).b()) stop("Attempting to access a branch or leaf that has been freed:", Node);         // Complain if the node has been freed and not reallocated
     }
    return Node.Mul(sizeOfNode);                                                                                        // Actual byte position of this node in memory
   }

  enum BranchOrLeaf                                                                                                     // Branch or leaf
   {leaf(1), branch(2);
    private final int value;
    BranchOrLeaf (int Value) {value = Value;}
    int value()              {return value;}
   }

  Int         root () {return new Int(0);}                                                                              // The root is always at node zero
  Bit   isRootLeaf () {return checkType(root(), BranchOrLeaf.leaf);}                                                    // Whether the root is a leaf
  Bit isRootBranch () {return checkType(root(), BranchOrLeaf.branch);}                                                  // Whether the root is a branch

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
    return new Leaf(build.leaf.parent(program()).memory(r).at(Node));                                                   // Base leaf at the indexed address
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
    final Memory.Ref r = unitMemory.new Ref(nodeAddress(Node));                                                         // Address branch
    return new Branch(build.branch.parent(program()).memory(r).at(Node));                                               // Base branch at the indexed address
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
        s.append(f("Number of Keys: %4d\n", refCount.getInt(0)));
       }
      boolean trace() {return false;}
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

  final class Path                                                                                                      // Record the path from the root to the leaf that should contain a key
   {final Int          key = new Int("key");                                                                            // Search key
    final Int         leaf = new Int("leaf");                                                                           // Leaf that should contain the key
    final Int         step = new Int("step");                                                                           // Current step in the path
    final Bint       split = new Bint();                                                                                // The splitting branch is the uppermost branch directly connected to the leaf by intervening full branches which will all have to be split from the top down to permit the splitting of a full leaf
    final Memory.Ref  path = mergePath.new Ref(0);                                                                      // Branches along path

    Path(Int Key)
     {subStart("Tree.Path");
      final Int p = root();                                                                                             // Start at root
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
          final Bint            L = new Bint();                                                                         // There are four possibilities to consider
          new ForCount(4)                                                                                               // Locate the left sibling
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
    final Bit   m = new Bit(false);                                                                                     // Whether the merge was performed or not - assume it will not until we discover otherwise
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
    final Bit   m = new Bit(false);                                                                                     // Whether the merge was performed or not - assume it will not until we discover otherwise
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

    void traverse (int Index, int Parent, int Depth)                                                                 // Traverse the branch at the indicated index
     {final Slots          s = slots(Index, maxBranchSize);                                                             // Slots for branch
      final Memory.Ref nodes = refNodes.step(Index*build.nodeSize + 1 + s.build.size());                                // Array of child nodes
      final int            t = nodes.getInt(maxBranchSize);

      if (isLeaf(t))
       {for(int i = 0; i < maxBranchSize*2; ++i)                                                                        // Each leaf
         {if (s.getSlotToKeysInUse(i))
           {final int k = s.getSlotToKeyValue(i);
            final int n = s.getSlotToKeyIndex(i);
            final int l = nodes.getInt(n);
            pLeaf(l, Depth+1, Index, i);
            branchSlot(Index, Depth, Parent, i, k, l);
           }
         }
        branchTop(t, Depth);
        pLeaf(t, Depth+1, Index, -1);
       }
      else
       {for(int i = 0; i < maxBranchSize*2; ++i)                                                                        // Each sub branch
         {if (s.getSlotToKeysInUse(i))
           {final int k = s.getSlotToKeyValue(i);
            final int n = s.getSlotToKeyIndex(i);
            final int c = nodes.getInt(n);
            if (Depth <= maximumNumberOfLevels) traverse(c, Index, Depth+1);                                            // Terminate large traverses perhaps produced in error

            branchSlot(Index, Depth, Parent, i, k, c);
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

    void branchSlot (int Index, int Depth, int Parent, int Slot, int Key, int Child)                                    // Process branch slot by printing the tree to the left of the slot and then the slot
     {say("BBBB", "Index", Index, "Depth", Depth, "Parent", Parent, "Slot", Slot, "Key",  Key, "Child", Child, slots(Index, maxBranchSize));
     }

    void branchTop ( int Index, int Depth)                                                                              // Process branch top by printing its sub tree to the right
     {say("TTTT", "Index", Index, "Depth", Depth);
     }
   }

  void check (StringBuilder A, String B) {Test.ok(""+A, B);}

//D2 Print
//                                                          16  br slot key                                                            |
//                                                          (0) br index                                                            |
//                                                          [9,2] child index, slot                                                          |
//         4             8                12                                20              24              28              |
//         (9,0,2)       (9,0,2)          (9,0,2)                           (6,0)           (6,0)           (6,0)           |
//         [12,0]        [5,2]            [10,4]                            [7,0]           [4,2]           [3,4]           |
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

        @Override void branchSlot(int Index, int Depth, int Parent, int Slot, int Key, int Child)                       // Print keys of branch and optionally the details of the parent and the children of this branch
         {final int d = Depth * linesToPrintABranch;
          if (Context)                                                                                                  // Print relationships with surrounding nodes
           {pad(d+3);                                                                                                   // Pad the output area so that all the lines have the same length
            P.elementAt(d).append(f("%04d", Key));                                                                      // Write key into output area
            if (Depth == 0) P.elementAt(d+1).append("("+Index+","+Slot+")");                                            // Format second line for a root
            else P.elementAt(d+1).append("("+Index+","+Parent+","+Slot+")");                                            // Format second line for a non root branch showing the parent of the branch and the slot in the parent this branch came from
            P.elementAt(d+3).append("["+Child+","+Slot+"]");                                                            // Format third line
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
            trimRight(P.elementAt(d+1)).append(""+Index);                                                         // Add index of top to slot second line
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
        boolean trace() {return false;}
       };
      return t;
     }
   }

  StringBuilder dump () {subStart("Tree.dump" ); var s = new Print(true) .printCollapsed(); subFinish(); return s;}     // Dump the tree
  StringBuilder print() {subStart("Tree.print"); var s = new Print(false).printCollapsed(); subFinish(); return s;}     // Print the tree

//D1 Tests                                                                                                              // Tests

  void testsStartHere() {super.testsStartHere();}                                                                       // Divider between code to be tested and code to drive testing

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
    t.dumpProgramState("AAAA");

    //stop(t.memoriesMd5Sum());
    t.ok(()->t.memoriesMd5Sum(), "{f371c5bdbcbff6af8e6531681045dd0e, b4b147bc522828731f1a016bfa72c073}");

    if (Ex) ok(t.dumpTree(), """
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
    t.dumpProgramState("BBBB");

    //stop(t.memoriesMd5Sum());
    t.ok(()->t.memoriesMd5Sum(), "{11b051f5015cfffe5e1b8dac5472013e, b4b147bc522828731f1a016bfa72c073}");
    if (Ex) ok(t.dumpTree(), """
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
    t.dumpProgramState("CCCC");

    //stop(t.memoriesMd5Sum());
    t.ok(()->t.memoriesMd5Sum(), "{623d8ee8624f6871fc426c828d6cefb6, b4b147bc522828731f1a016bfa72c073}");
    if (Ex) ok(t.dumpTree(), """
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
    t.dumpProgramState("DDDD");

    //stop(t.memoriesMd5Sum());
    t.ok(()->t.memoriesMd5Sum(), "{ff5da33d1ca92818dc85b214aabca86f, b4b147bc522828731f1a016bfa72c073}");
    if (Ex) ok(t.dumpTree(), """
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
    final Tree t = new Tree(new Build().maxLeafSize(4).maxBranchSize(3).numberOfNodes(N).immediate(Ex))
     {void treeCode()
       {new ForCount(new Int(1), new Int(N+1))
         {void body(Int Index)
           {insert(Index, Index.Mul(11));
            dumpProgramState("AAAA");
           }
         };


        //stop(memoriesMd5Sum());
        ok(()->memoriesMd5Sum(), "{995d7c9b4b37d2a72d6be9d7eb521a65, c77d99f7299b1247cf51cdcb396e65df}");

        if (Ex) ok(dump(), """
                                                         0016                                                                    |
                                                         (0,2)6                                                                  |
                                                         [9,2]                                                                   |
       0004          0008             0012                                0020              0024              0028               |
       (9,0,0)       (9,0,2)          (9,0,4)8                            (6,0,0)           (6,0,2)           (6,0,4)2           |
       [3,0]         [4,2]            [7,4]                               [10,0]            [5,2]             [12,4]             |
1,2,3,4       5,6,7,8       9,10,11,12        13,14,15,16      17,18,19,20       21,22,23,24       25,26,27,28        29,30,31,32|
(3,9,0)       (4,9,2)       (7,9,4)           (8,9)            (10,6,0)          (5,6,2)           (12,6,4)           (2,6)      |
""");

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

  static void test_insert ()
   {          test_insert(true);
              test_insert(false);
   }

  static void test_insertMerged(boolean Ex)
   {sayCurrentTestName();
    final int N = 32;
    final Tree t = new Tree(new Build().maxLeafSize(4).maxBranchSize(3).numberOfNodes(N).immediate(Ex))
     {void treeCode()
       {new ForCount(new Int(1), new Int(N+1))
         {void body(Int Index)
           {insert(Index, Index);
            dumpProgramState("AAAA");
           }
         };

        //stop(memoriesMd5Sum(), dump(), print());
        ok(()->memoriesMd5Sum(), "{c1b89dca198d6405658b8573f8ccc391, c77d99f7299b1247cf51cdcb396e65df}");

        if (Ex) ok(dump(), """
                                                         0016                                                                    |
                                                         (0,2)6                                                                  |
                                                         [9,2]                                                                   |
       0004          0008             0012                                0020              0024              0028               |
       (9,0,0)       (9,0,2)          (9,0,4)8                            (6,0,0)           (6,0,2)           (6,0,4)2           |
       [3,0]         [4,2]            [7,4]                               [10,0]            [5,2]             [12,4]             |
1,2,3,4       5,6,7,8       9,10,11,12        13,14,15,16      17,18,19,20       21,22,23,24       25,26,27,28        29,30,31,32|
(3,9,0)       (4,9,2)       (7,9,4)           (8,9)            (10,6,0)          (5,6,2)           (12,6,4)           (2,6)      |
""");

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

        //stop(memoriesMd5Sum(), dump(), print());
        ok(()->memoriesMd5Sum(), "{220e1d7ea10d7a38c27d020bd18ba875, 161a1b8081b22d264fcfb3777fe6c142}");

        if (Ex) ok(dump(), """
                                                          0016                                                                    |
                                                          (0,2)6                                                                  |
                                                          [9,2]                                                                   |
        0004          0008             0012                                0020              0024              0028               |
        (9,0,0)       (9,0,2)          (9,0,4)8                            (6,0,0)           (6,0,2)           (6,0,4)2           |
        [12,0]        [5,2]            [10,4]                              [7,0]             [4,2]             [3,4]              |
1,2,3,4        5,6,7,8       9,10,11,12        13,14,15,16      17,18,19,20       21,22,23,24       25,26,27,28        29,30,31,32|
(12,9,0)       (5,9,2)       (10,9,4)          (8,9)            (7,6,0)           (4,6,2)           (3,6,4)            (2,6)      |
""");

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
           {final Int k = new Int("Key", 0);

            Index.S();                                                                                                  // Load index of item we want
            new I()
             {void        a() {       intMemory(). writeInt        =     random_32[Index.i()];}
              String      v() {return intMemory().vWriteInt() + " <= "+a.dataRegisterName()+";";}                       // Translate index into key
              boolean trace() {return false;}
             };
            k.W();                                                                                                      // Write key into variable
            insert(k, Index);
            dumpProgramState("AAAA");
           }
         };

        //stop(memoriesMd5Sum(), dump(), print());
        ok(()->memoriesMd5Sum(), "{4e361ad72f7d7bd06ccdf2ada5a8901c, 161a1b8081b22d264fcfb3777fe6c142}");

        if (Ex) ok(dump(), """
                                                         0015                                                           0026                          |
                                                         (0,1)                                                          (0,4)6                        |
                                                         [5,1]                                                          [11,4]                        |
        0004          0007            0011                               0019            0021            0024                            0030         |
        (5,0,0)       (5,0,2)         (5,0,4)4                           (11,0,1)        (11,0,4)        (11,0,5)7                       (6,0,2)2     |
        [14,0]        [1,2]           [9,4]                              [12,1]          [3,4]           [8,5]                           [10,2]       |
1,2,3,4        5,6,7         8,9,10,11        12,13,14,15     16,17,18,19        20,21           22,23,24         25,26       27,28,29,30        31,32|
(14,5,0)       (1,5,2)       (9,5,4)          (4,5)           (12,11,1)          (3,11,4)        (8,11,5)         (7,11)      (10,6,2)           (2,6)|
""");

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
       {new ForCount(new Int(1), new Int(N+1))
         {void body(Int Index)
           {insert(Index, Index.Mul(11));
            dumpProgramState("AAAA");
           }
         };

        final StringBuilder s = Ex ? print() : null;
        final StringBuilder m = new StringBuilder();

        new ForCount(new Int(N))
         {void body(Int Index)
           {delete(Index.Inc());
            if (Ex) s.append(print());
            new I() {void a() {m.append(memoriesMd5Sum()+"\n");} boolean trace() {return false;}};
            dumpProgramState("BBBB");
           }
         };

        //stop(memoriesMd5Sum(), dump(), print());
        ok(()->m, """
{e699ab5ad1aeb32d9c344d65edc2d221, 2e586fbe7fa9003a2b31dd8e9145b093}
{e3974b1a8803b45e2d7a34d44dc5632f, 2e586fbe7fa9003a2b31dd8e9145b093}
{bbd70ba5bb6856b257a12bb4ac1b6e0e, 2e586fbe7fa9003a2b31dd8e9145b093}
{15a1193fa9b4ce61c199caef5c776630, 2e586fbe7fa9003a2b31dd8e9145b093}
{0b4552ecb6dd773ed386e2bf8d8f058c, 2e586fbe7fa9003a2b31dd8e9145b093}
{1716d5620d57f83e49713a5fd1892a24, 2e586fbe7fa9003a2b31dd8e9145b093}
{3a66b54abc12904c52d05755cdb829cf, 2e586fbe7fa9003a2b31dd8e9145b093}
{672ece5123275e07dcb31fe0becdf50e, 2e586fbe7fa9003a2b31dd8e9145b093}
{68aa3885701cd4f413bc51fe76493097, 2e586fbe7fa9003a2b31dd8e9145b093}
{f9899a3555746bc7b334267ad7bfff25, 2e586fbe7fa9003a2b31dd8e9145b093}
{54b908c577de168b271154fcf79b444a, 2e586fbe7fa9003a2b31dd8e9145b093}
{97194606d35628a511f69f91bd2d0a2e, 2e586fbe7fa9003a2b31dd8e9145b093}
{2e97429cfb41285865e0ff73158ad493, 2e586fbe7fa9003a2b31dd8e9145b093}
{24789c6298f032942b7e0745fc4906f9, 2e586fbe7fa9003a2b31dd8e9145b093}
{8b7bcce27bbb5aa181a41836ed9aeb3e, 2e586fbe7fa9003a2b31dd8e9145b093}
{cd6c818865b7c8c673a31b274ad1212d, 2e586fbe7fa9003a2b31dd8e9145b093}
{7a442d26eeec380228e4a49f17d5cd7e, c77d99f7299b1247cf51cdcb396e65df}
{50820ae788392b6be9c9386489924e86, c77d99f7299b1247cf51cdcb396e65df}
{eaf435fcb70a71a281ff850cd49193c0, c77d99f7299b1247cf51cdcb396e65df}
{dbb7335a6c53a957f4c8ce8df94c04b4, c77d99f7299b1247cf51cdcb396e65df}
{2c00bd6195e34685f038045714402564, dcddb75469b4b4875094e14561e573d8}
{dcd555bc6030933776da0fc610744788, dcddb75469b4b4875094e14561e573d8}
{f35f01a41ab37b239144b773aa94dae2, dcddb75469b4b4875094e14561e573d8}
{4b738bb1f9825b8ce81fbe184d2b38dc, dcddb75469b4b4875094e14561e573d8}
{81f3ce6d78302a664e8442b466e00f17, dcddb75469b4b4875094e14561e573d8}
{478b702293a5b207b8759b50731aa963, dcddb75469b4b4875094e14561e573d8}
{0f0e5a0b5cacb7cd326c5eea7172f553, dcddb75469b4b4875094e14561e573d8}
{0b1e2d7ae1ce6c127d25473e57e03137, dcddb75469b4b4875094e14561e573d8}
{913e585346e5c61c15497e5897a73d5b, dcddb75469b4b4875094e14561e573d8}
{d1c0a464b5b8ac73d2bb4ea7904d2df7, dcddb75469b4b4875094e14561e573d8}
{41e8047d4e6a159233e52ff54f290861, dcddb75469b4b4875094e14561e573d8}
{3d12323d9344cecfb78083f1ea94da7e, dcddb75469b4b4875094e14561e573d8}
""");

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
       {new ForCount(new Int(1), new Int(N+1))
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
            new I() {void a() {m.append(memoriesMd5Sum()+"\n");} boolean trace() {return false;}};

            dumpProgramState("BBBB");
           }
         };

        //stop(memoriesMd5Sum(), dump(), print());
        ok(()->m, """
{8cd7b6edd4f5a53d0a64415c99255e55, c77d99f7299b1247cf51cdcb396e65df}
{d87a1a2aa68ff6ce8a0578183903df67, c77d99f7299b1247cf51cdcb396e65df}
{559442154c192bed372836c1f1d1fc9a, c77d99f7299b1247cf51cdcb396e65df}
{9435c36b9721ca1c2075ae3d898af49e, c77d99f7299b1247cf51cdcb396e65df}
{14a11d1015ef19292099c130052be7db, c77d99f7299b1247cf51cdcb396e65df}
{0ba346f91feef857880bc72681a25197, c77d99f7299b1247cf51cdcb396e65df}
{4f66c068df12ceb256e971f8969296dd, c77d99f7299b1247cf51cdcb396e65df}
{4045a909c0f14c96801c5e0089c90eff, c77d99f7299b1247cf51cdcb396e65df}
{3eedd622adb50464e79cc258d5f5133a, c77d99f7299b1247cf51cdcb396e65df}
{0ae7882c74c4364e198817ce0d410d3a, c77d99f7299b1247cf51cdcb396e65df}
{7127fa9854c103561a215f5531fbb6e8, c77d99f7299b1247cf51cdcb396e65df}
{76ad5a7bffb8c447d91a4a1af94b83e9, c77d99f7299b1247cf51cdcb396e65df}
{44cab04caa14cb82aec09d5ae177f20c, c77d99f7299b1247cf51cdcb396e65df}
{8ba2d69edd19f0e4a898503e1ee250df, c77d99f7299b1247cf51cdcb396e65df}
{d69611aca85a7696a1a618bdbb9075b2, c77d99f7299b1247cf51cdcb396e65df}
{823016f89497fe52e6650df0a8507552, c77d99f7299b1247cf51cdcb396e65df}
{74dead968d75c372b18336818cffc728, 2e586fbe7fa9003a2b31dd8e9145b093}
{9be9b6be7ef1f15129075a5c9f47104d, 2e586fbe7fa9003a2b31dd8e9145b093}
{ba9d1a979f592c42711b19d43a88f126, 2e586fbe7fa9003a2b31dd8e9145b093}
{d93980db1394e334ddf399fdcd07f54a, 2e586fbe7fa9003a2b31dd8e9145b093}
{8dadbc6abf89c3bdbd906cf48f8e1c60, dcddb75469b4b4875094e14561e573d8}
{8c94fc8e11e4146eab596a3441b35397, dcddb75469b4b4875094e14561e573d8}
{a3079a419880b4e6a5f9642f0a770a41, dcddb75469b4b4875094e14561e573d8}
{0ce65aec1f86b1944d04db9397d0a679, dcddb75469b4b4875094e14561e573d8}
{4248deb2cf877c9c5737e68352f0355c, dcddb75469b4b4875094e14561e573d8}
{5327849c1de0146f4c824a57ff0e683b, dcddb75469b4b4875094e14561e573d8}
{40030e7e23da2fc530fc33f93c92e5d2, dcddb75469b4b4875094e14561e573d8}
{295cf1248d2ddba1591aa9e72fa1c9b4, dcddb75469b4b4875094e14561e573d8}
{81bc85ddcb8d57e8e7d801d633249c2e, dcddb75469b4b4875094e14561e573d8}
{cfeb99b4dea241872fea93930eecb04c, dcddb75469b4b4875094e14561e573d8}
{60eecb4595246c8407de49fef50eedae, dcddb75469b4b4875094e14561e573d8}
{84bcde4bd3de56578146738419b93767, dcddb75469b4b4875094e14561e573d8}
""");

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

    //final Tree t = test_reloadTree(Ex);
    //t.reloadMemories(tree32);
    final Tree t = new Tree(new Build().maxLeafSize(4).maxBranchSize(3).numberOfNodes(N).immediate(Ex))
     {void treeCode()
       {new ForCount(new Int(1), new Int(N+1))
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
           {final Int k = new Int("Key", 0);
            Index.S();                                                                                                  // Load index of item we want
            new I()
             {void        a() {       intMemory(). writeInt        =     random_32[Index.i()];}
              String      v() {return intMemory().vWriteInt() + " <= "+a.dataRegisterName()+";";}                       // Translate index into key
              boolean trace() {return false;}
             };
            k.W();                                                                                                      // Write key into variable
            delete(k);

            if (Ex) s.append(print());
            new I() {void a() {m.append(memoriesMd5Sum()+"\n");} boolean trace() {return false;}};
            dumpProgramState("BBBB");
           }
         };

        ok(()->m, """
{ca1eda908041938c9f59707602409d56, 2e586fbe7fa9003a2b31dd8e9145b093}
{74440ac5db2ea3b7fc4b1bb4e1807cc9, 2e586fbe7fa9003a2b31dd8e9145b093}
{4ed092267df92b8d8b97e98430c67617, c77d99f7299b1247cf51cdcb396e65df}
{43fec9208c3b3f2c5100b6a6305aaad8, 2e586fbe7fa9003a2b31dd8e9145b093}
{a4767b98cf46eab002edfd6ecf9ad286, c77d99f7299b1247cf51cdcb396e65df}
{af691d61be819b051da1c8a2194583dc, c77d99f7299b1247cf51cdcb396e65df}
{378ea11ccc791ecdff76a87ae98de6c1, 2e586fbe7fa9003a2b31dd8e9145b093}
{2bb8a5256872ec8880476a0b5e3babfe, c77d99f7299b1247cf51cdcb396e65df}
{fe7c9ee9c0ee6b217d4307839c8796b9, 2e586fbe7fa9003a2b31dd8e9145b093}
{7c9502a8757550b2a1657028b847d232, c77d99f7299b1247cf51cdcb396e65df}
{272762cf342c63a284cc8006424eede5, c77d99f7299b1247cf51cdcb396e65df}
{231f2945cf5831d2e02cd85c442d1f78, 2e586fbe7fa9003a2b31dd8e9145b093}
{c6873147996892ecdb3e889a09ec88d0, 2e586fbe7fa9003a2b31dd8e9145b093}
{a9145b6374322d65756ea58d13a56017, c77d99f7299b1247cf51cdcb396e65df}
{88c9b8a3d397b28e08ed927f7856ff58, 2e586fbe7fa9003a2b31dd8e9145b093}
{e1979e902aaf957d550b7699cff25b62, c77d99f7299b1247cf51cdcb396e65df}
{89535337d4e8c7383a31c30ed2100180, dcddb75469b4b4875094e14561e573d8}
{e2baae252fef0bc5bc5d0f1d479f0375, dcddb75469b4b4875094e14561e573d8}
{261eb374f221e9b47febc9f93c4421c3, dcddb75469b4b4875094e14561e573d8}
{fad13aaa017ae090f3639c4477ef64eb, dcddb75469b4b4875094e14561e573d8}
{cf8982addf1fc6c6fee9dc8852da45e6, dcddb75469b4b4875094e14561e573d8}
{e94b4f165aa0f6894bf01275a8d80193, dcddb75469b4b4875094e14561e573d8}
{68bf62b8172388be30c8eb9080f7c54d, dcddb75469b4b4875094e14561e573d8}
{39b29e37bff3fb40855319de1f89faa9, dcddb75469b4b4875094e14561e573d8}
{cf52fd24bd558b3192189da4e5034bd8, dcddb75469b4b4875094e14561e573d8}
{02fec17f2b0d54e475fdc420f10f63bc, dcddb75469b4b4875094e14561e573d8}
{6af6e5d6b60f7fb40d35013950333a66, dcddb75469b4b4875094e14561e573d8}
{7184a73828eaf1ef432c37c2d2eac1a0, dcddb75469b4b4875094e14561e573d8}
{6779c7f3b0adb4b11c526d7b39c88911, dcddb75469b4b4875094e14561e573d8}
{c6d05d145f5c37eefcd57e46fca3a2ed, dcddb75469b4b4875094e14561e573d8}
{0db888dc1d032d416735b1bfa3c16f36, dcddb75469b4b4875094e14561e573d8}
{99af2e269fc2b882dbc0359723639b31, dcddb75469b4b4875094e14561e573d8}
""");
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
        //stop(memoriesMd5Sum());
        ok(()->memoriesMd5Sum(), "{65dd292088a9fd3ef5ef0fe905a1a1a6, b4b147bc522828731f1a016bfa72c073}");
        if (Ex) ok(dumpTree(), """
Tree memory dump
Leaf   size   :   41
Branch size   :   33
Node   size   :   41
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
    t.new ForCount(t.new Int(1), t.new Int(N+1))
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


  static void oldTests()                                                                                                // Tests thought to be in good shape
   {if (rtg( 1)) test_tree();
    //if (rtg( 2)) test_saveReload();
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
    test_deleteRandom32(false);
   }

  public static void main(String[] args)                                                                                // Test if called as a program
   {testGroup = args.length > 0 ? args[0] : null;                                                                       // Test groups if supplied
    try                                                                                                                 // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {deleteAllFileInVerilogTestsFolder();                                                                              // Delete generated Verilog files created by a prior run of the current test
      if (github_actions) oldTests(); else newTests();                                                                  // Tests to run
      //if (coverageAnalysis) coverageAnalysis(12);                                                                       // Coverage analysis
      //say(subPrint());
      printExecutionCoverageGlobal(4);                                                                                 // Find locations in the java code that generated instructions that were never tested
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
// perl -M"MakeWithPerl" -e"MakeWithPerl::makeWithPerl" -I/home/phil/perl/cpan/MakeWithPerl/lib -- --run  "/home/phil/btreeList/Tree.java" --javaHome "/home/phil/btreeList"
