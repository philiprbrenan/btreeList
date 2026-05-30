//----------------------------------------------------------------------------------------------------------------------
// Btree with stucks implemented as distributed slots
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

class Tree extends Program                                                                                              // A tree that translates keys into values to be implemented as an application specific integrated circuit
 {final int             maxLeafSize;                                                                                    // The maximum number of entries in a leaf of the tree
  final int           maxBranchSize;                                                                                    // The maximum number of entries in a branch of the tree
  final Slots            freeChain;                                                                                     // Unallocated modes: leaves and branches
  final int           numberOfNodes;                                                                                    // Maximum number of leaves plus branches in this tree
  final int   maximumNumberOfLevels;                                                                                    // Maximum number of levels in tree to prevent runaways while debugging
  final int              sizeOfNode;                                                                                    // The size of each node in the tree: a node may hold a branch or a leaf
  final Build                 build;                                                                                    // Memory containing the tree base followed by the leaves and branches of the tree
  final static String  formatKey = "%3d";                                                                               // Format a key for dumping during testing

//D1 Construction                                                                                                       // Construct and layout a tree

   static class Build                                                                                                   // Parameters describing a tree
    {boolean       immediate = true;                                                                                    // Immediate execution mode
     boolean           trace = true;                                                                                    // Trace execution
     int          branchSize;                                                                                           // Size of a branch
     int            leafSize;                                                                                           // Size of a leaf
     int            nodeSize;                                                                                           // Size of a node: a leaf or a branch which ever is bigger. By using fixed size memory allocation we greatly simplify memory allocation - so it is worth adjustiung the branch and leaf sizes to be as equal as possible.
     Integer     maxLeafSize;
     Integer   maxBranchSize;
     Integer   numberOfNodes;
     Boolean         execute;
     Slots .Build  freeChain;
     Branch.Build     branch;
     Leaf  .Build       leaf;
     int bytesNeededForNodes;                                                                                           // Bytes needed for all the nodes
     int bytesNeededForFree;                                                                                            // Bytes needed for free chain

     Build immediate    (boolean Immediate    ) {immediate     = Immediate;     return this;}
     Build trace        (boolean Trace        ) {trace         = Trace;         return this;}
     Build maxLeafSize  (int     MaxLeafSize  ) {maxLeafSize   = MaxLeafSize  ; return this;}
     Build maxBranchSize(int     MaxBranchSize) {maxBranchSize = MaxBranchSize; return this;}
     Build numberOfNodes(int     NumberOfNodes) {numberOfNodes = NumberOfNodes; return this;}
     Build execute      (boolean Execute      ) {execute       = Execute;       return this;}

    Program.Build build()                                                                                               // Create a description of the needed containing program
     {final Program.Build p = new Program.Build();                                                                      // Description of containing program
      freeChain             = new Slots .Build().numberOfKeys(numberOfNodes); freeChain.build();                        // Size of free chain
      branch                = new Branch.Build().maxSize(maxBranchSize)     ; branch   .build();                        // Size of a branch chain
      leaf                  = new Leaf  .Build().maxSize(maxLeafSize)       ; leaf     .build();                        // Size of a leaf chain
      leafSize              = leaf.size();
      branchSize            = branch.size();
      nodeSize              = max(branchSize, leafSize);
      bytesNeededForNodes   = numberOfNodes * nodeSize;
      bytesNeededForFree    = freeChain.size();
      p.memory   (bytesNeededForNodes);
      p.immediate(immediate);
      p.trace    (trace);
      return p;
     }
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

    if (b1 && !b2 && !b3) stop(m1); else if (b1) say(m1);
    if (b2        && !b3) stop(m2); else if (b2) say(m2);
    if (b3              ) stop(m3);
    build      = Build;
    sizeOfNode = build.nodeSize;

    freeChain  = new Slots(build.freeChain.parent(this));                                                               // Memory for free chain
    for (int i = 0, N = numberOfNodes; i < N; ++i) freeChain.setSlotAndKey(new Int(i), new Int(i), new Int(i));         // Initial free chain with root as an allocated leaf. Each active leaf or branch resides in a node of the tree allocated from the free chain. Using a single node size greatly simplifies memory management which is crucial in long running processes like database systems.
    leaf();                                                                                                             // Initialize the root as a leaf
   }

  int maxLeafSize  () {return maxLeafSize;}                                                                             // Maximum size of a leaf
  int maxBranchSize() {return maxBranchSize;}                                                                           // Maximum size of a branch
  int numberOfNodes() {return numberOfNodes;}                                                                           // Maximum number of nodes in tree
  int           mnl() {return maximumNumberOfLevels;}                                                                   // Maximum number of levels

  Int allocate()                                                                                                        // Allocate a leaf or a branch using the free chain slots as an array that can be searched for the first used slot in log time
   {freeChain.usedKeys.countOnes().eq(0).stop("No more leaves or branches");
    final Int i = new Int("index")    .set(freeChain.getSlotToKeyIndex(freeChain.locateFirstUsedSlot()));               // Index of the key slot holding the index of the node to be allocated
    final Int a = new Int("allocated").set(freeChain.getKeyValue(i));                                                   // Index of the node to be allocated
    freeChain.delSlotAndKey(i);                                                                                         // Remove indexed node from free chain
    return a;
   }

  void free(Locatable Free)                                                                                                   // Free a leaf or a branch
   {final Int a = Free.getLocation();
    nodeAddress(a);                                                                                                  // Check the viability of the node index
    byteMemory.invalidate(nodeAddress(a), sizeOfNode);                                                               // Invalidate the memory
    freeChain.setSlotAndKey(a, a, a);
   }

  Bool isAllocated(Int Node) {return freeChain.find(Node).equal.Flip();}                                                // Convert index to byte address of node in memory

  Int nodeAddress(Int Node)                                                                                             // Convert index to byte address of node in memory
   {Node.lt(0)            .stop("Node less than zero:", Node);                                                          // Check not less than zero
    Node.gt(numberOfNodes).stop("Node too big:",        Node);                                                          // Check in range
    final Slots.Find F = freeChain.find(Node);                                                                          // Check not freed
    F.equal.stop("Attempting to access a branch or leaf that has been freed:", Node);                                   // Complain of the node has been freed and not reallocated
    return Node.Mul(sizeOfNode);                                                                                        // Actual byte position of this node in memory
   }

  enum BranchOrLeaf                                                                                                     // Branch or leaf
   {leaf(1), branch(2);
    private final int value;
    BranchOrLeaf (int Value) {value = Value;}
    int value()              {return value;}
   }

  Bool isRootLeaf  () {return checkType(new Int(0), BranchOrLeaf.leaf);}                                                         // Whether the root is a leaf
  Bool isRootBranch() {return checkType(new Int(0), BranchOrLeaf.branch);}                                                       // Whether the root is a branch

  Bool checkType(Int Node, BranchOrLeaf Type)                                                                           // Check the type of a node
   {final Int  a = nodeAddress(Node);
    final Int  t = byteMemory.getInt(a);
    final Bool r = new Bool(false);
    new If (t.eq(Type.value())) {void Then() {r.set(true);}};
    return r;
   }

  void setType(Int Node, BranchOrLeaf Type)                                                                             // Check the type of a node
   {final Int a = nodeAddress(Node);
    byteMemory.putInt(a, new Int(Type.value()));
   }

  Bool isBranch(Int Node) {return checkType(Node, BranchOrLeaf.branch);}                                                // Is the indexed node a branch
  Bool isLeaf  (Int Node) {return checkType(Node, BranchOrLeaf.leaf  );}                                                // Is the indexed node a leaf

  Leaf leaf(Int Node) {return leaf(Node, true);}                                                                        // Index an existing leaf in memory            confirming that it really is a leaf
  Leaf leaf(Int Node, boolean Check)                                                                                    // Index an existing leaf in memory optionally confirming that it really is a leaf
   {if (Check) isLeaf(Node).Flip().stop("Not a leaf:", Node);                                                           // Check the location actually holds a leaf
    final ByteMemory.Ref r = byteMemory.new Ref(nodeAddress(Node));                                                     // Address leaf
    return new Leaf(build.leaf.parent(program()).memory(r).at(Node));                                                        // Base leaf at the indexed address
   }

  Leaf leaf()                                                                                                            // Create and initialize a leaf in memory and return its index
   {final Int  i = allocate();
    final Leaf l = leaf(i, false);
    l.initializeMemory();
    setType(i, BranchOrLeaf.leaf);
    return l;
   }

  Branch branch(Int Node) {return branch(Node, true);}                                                                  // Index an existing branch in memory            confirming that it really is a branch
  Branch branch(Int Node, boolean Check)                                                                                // Index an existing branch in memory optionally confirming that it really is a branch
   {if (Check) isBranch(Node).Flip().stop("Not a branch:", Node);                                                       // Check the location actually holds i branch
    final ByteMemory.Ref r = byteMemory.new Ref(nodeAddress(Node));                                                     // Address branch
    return new Branch(build.branch.parent(program()).memory(r).at(Node));                                               // Base branch at the indexed address
   }

  Branch branch()                                                                                                       // Create and initialize a branch in memory and return its index
   {final Int    i = allocate();
    final Branch b = branch(i, false);
    b.initializeMemory();
    setType(i, BranchOrLeaf.branch);
    return b;
   }

  void dumpTree()                                                                                                       // Dump the tree
   {final StringBuilder s = out;
    final Int           c = new Int(numberOfNodes).sub(freeChain.count());
    new I()
     {void action()
       {s.setLength(0);
        s.append(f("Tree memory dump\n"));
        s.append(f("Leaf   size   : %4d\n", build.leafSize));
        s.append(f("Branch size   : %4d\n", build.branchSize));
        s.append(f("Node   size   : %4d\n", sizeOfNode));
        s.append(f("MaxLeafSize   : %4d\n", maxLeafSize));
        s.append(f("MaxBranchSize : %4d\n", maxBranchSize));
        s.append(f("NumberOfNodes : %4d\n", numberOfNodes));
        s.append(f("Allocations   : %4d\n", c.i()));
       }
     };

    new ForCount(new Int(min(numberOfNodes, 20)))
     {void body(Int Index)
       {new If(isAllocated(Index))
         {void Then()
           {new I() {void action() {s.append(f("%4d ", Index.i())); }};

            new If (isLeaf(Index))
             {void Then() {s.append(leaf  (Index).toString());}
              void Else() {s.append(branch(Index).toString());}
             };
           }
         };
       }
     };
   }

  void dumpTree(String Expected)                                                                                        // Dump the tree
   {dumpTree();
    new I() {void action() {ok(""+out,  Expected);}};
   }

  void insert(Int Key, Int Data)                                                                                        // Insert a key, data pair into the tree
   {new If (isRootLeaf())
     {void Then()                                                                                                       //
       {final Leaf R = leaf(new Int(0));
        new If (R.full())
         {void Then()
           {
           }
          void Else()                                                                                                   // Root is a non full leaf
           {R.insert(Key, Data);
           }                                                                                                              //
         };
       }
     };
   }
/*
     final Ref<Branch> p = new Ref<>((Branch)root());                                                                    // Start at root
    new If (p.get().full())                                                                                             // Split full root branch
     {void Then()
       {final Branch P = p.get();
        p.set(p.get().split());
        root(p.get());
        P.free();
       }
     };

    final Ref<Branch> q = new Ref<>(find(Key).leaf.up());                                                               // First branch above leaf
    final Ref<Branch> b = new Ref<>(q.get().up());                                                                      // Parent of first branch above leaf
    final Bool        D = new Bool().clear();                                                                           // First unfull branch above leaf has been located when set
    new If (b.valid())
     {void Then()
       {new For(numberOfNodes)                                                                                          // Look for first unfull branch along path up from leaf
         {void body(Int i, Bool C)                                                                                      // Found the first unfull branch
           {new If (b.get().full().Flip())
             {void Then()
               {p.set(b.get()); D.set();
               }
              void Else()
               {q.set(b.get());
                b.set(q.get().up());
               }
             };
            C.set(D).flip().and(()->{return b.valid();});
           }
         };
       }
     };

    final Bool d = new Bool().clear();                                                                                  // Set when done
    new For(MaximumNumberOfLevels)                                                                                      // Step down through the tree from branch to branch splitting as we go until we reach a leaf
     {void body(Int i, Bool C)
       {final Slots q = p.get().stepDown(Key);                                                                          // Step down
        C.set();
        new If (q.isLeaf())                                                                                             // Step down to a leaf
         {void Then()
           {final Leaf r = (Leaf)q;                                                                                     // We have reached a leaf
            new If (r.full())                                                                                           // Split the leaf if it is full
             {void Then()
              {final Int  sk = r.splittingKey();                                                                        // Splitting key
                final Leaf l = r.splitLeft();                                                                           // Right leaf split out of the leaf
                p.get().insert(new Key(sk), l);                                                                         // The parent is known not to be full so the insert will work.  We are inserting left so this works even if we are splitting top
                final Leaf L = If (Key.value().le(sk), ()->l, ()->r);                                                   // Choose left or right leaf depending on key
                L.insert(Key, Data);                                                                                    // Insert into left or right leaf which will now have space
               }
              void Else() {r.insert(Key, Data);}                                                                        // Leaf has sufficient space
             };
            mergeAlongPath(Key);                                                                                        // Merge along the path taken by the key to compress the tree
            C.clear(); d.set();
           }
          void Else()                                                                                                   // Step down to a branch
           {final Branch r = (Branch)q;
            new If (r.full())                                                                                           // Split the leaf if it is full
             {void Then()
               {final Int         sk = r.splittingKey();                                                                // Splitting key
                final Branch.Split s = r.splitLeft();                                                                   // Branch split out on right from
                p.get().insert(new Key(sk), s.left);                                                                    // The parent is known not to be full so the insert will work.  We are inserting left so this works even if we are splitting top
                p.set(If (Key.value().le(sk), ()->s.left, ()->s.right));                                                // Traverse left or right
               }
              void Else() {p.set(r);}                                                                                   // Step down into non full branch
             };
           }
         };
       }
     };
    new If (d.Flip())                                                                                                   // Unable to insert
     {void Then()
       {stop("Insert fell off the end of tree after this many searches:", mnl());
       }
     };
   }

/*



//D2 Low Level                                                                                                          // Low level operations

  void mergeAlongPath(Key Key)                                                                                          // Merge along the path from the specified key to the root
   {final Find f = find(Key);                                                                                           // Locate the leaf that should contain the key
    //if (f == null) return;                                                                                            // Empty tree

    new If (f.leaf.up() != null)                                                                                        // Process path from leaf to root
     {void Then()
       {final Ref<Branch> B = new Ref<>(f.leaf.up());                                                                   // First branch
        new For(numberOfNodes)                                                                                          // Go up the tree merging as we go: only one merge is needed at each level
         {void body(Int i, Bool C)
           {final Branch b = B.get();
            final Bool m = new Bool().clear();                                                                          // Whether we have merged anything yet

            new If (m.Flip())
             {void Then()
               {final Slots.Slot l = b.locateFirstGe(Key);                                                              // Position of key
                m.set(l.valid().and(()->{return b.mergeRightSibling(l);}));                                             // Merge right sibling of keyed child
               }
             };

            new If (m.Flip())
             {void Then()
               {final Slots.Slot L = b.locateFirstGe(Key);                                                              // Position of key
                m.set(L.valid().and(()->{return b.mergeLeftSibling(L);}));                                              // Merge left sibling of keyed child
               }
             };

            new If (m.Flip())
             {void Then()
               {final Slots.Slot k = b.locateFirstGe(Key);                                                              // Look further left
                m.set(k.valid()
                 .and(()->{return b.mergeLeftSibling(k.stepLeft());}));                                                 // Merge further left sibling
                new If (m.Flip())                                                                                       // Top
                 {void Then()
                   {final Slots.Slot S = b.locateLastUsedSlot();
                    m.set(S.valid().and(()->{return b.mergeLeftSibling(S);}));                                          // Merge further left of top
                   }
                 };
               }
             };

            new If (m.Flip())
             {void Then()
               {final Slots.Slot r = b.locateFirstGe(Key);                                                              // Look further right
                m.set(r.valid()
                 .and(()->{return b.mergeRightSibling(r.stepRight());}));                                               // Merge further right sibling
               }
             };

            new If (m.Flip())                                                                                           // Migrate into top
             {void Then()
               {b.mergeLeftSibling(b.new Slot());
               }
             };
            B.set(b.up());                                                                                              // Go up the tree merging as we go: only one merge is needed at each level
            C.set(B.valid());
           }
         };
       }
     };
    mergeRoot(Key);                                                                                                     // Merge the root if possible
   }

  void mergeRoot(Key Key)                                                                                               // Collapse the root if possible
   {new If (root() != null)                                                                                             // Tree has content
     {void Then()
       {mergeRootNotEmpty(Key);                                                                                         // Merge non empty root if possible
       }
     };
    return;
   }

  void mergeRootNotEmpty(Key Key)                                                                                       // Collapse the root if possible
   {new If (root().isLeaf())                                                                                            // Leaf root
     {void Then()
       {final Leaf l = (Leaf)root();
        new If (l.empty()) {void Then() {l.free(); root((Leaf)null);}};                                                 // Free leaf if it is empty
       }
      void Else()
       {final Branch b = (Branch)root();                                                                                // Branch root
        new If (b.countUsed().eq(0))                                                                                    // Root body is empty so collapse to top
         {void Then()
           {final Slots t = b.top();
            b.free();
            new If (t.isLeaf())
             {void Then()
               {root((Leaf)t);
               }
              void Else()
               {root((Branch)t);
               }
             };
           }
         };

        new If (b.countUsed().eq(1))                                                                                    // Root body is right size to collapse
         {void Then()
           {new If (b.top().isLeaf())                                                                                   // Leaves for children
             {void Then()
               {final Leaf l = (Leaf)b.firstChild();
                final Leaf r = (Leaf)b.top();
                final Bool m = l.mergeFromRight(r);
                new If (m) {void Then() {b.free(); r.free(); root(l);}};                                                // Update root if the leaves were successfully merged
               }
              void Else()
               {final Branch l = (Branch)b.firstChild();                                                                // Root has branches for children
                final Branch r = (Branch)b.top();
                final Bool   m = r.mergeFromLeft(b.firstKey(), l);
                new If (m) {void Then() {b.free(); l.free(); root(r);}};                                                // Update root if the leaves were successfully merged
               }
             };
           }
         };
       }
     };
   }

//D2 High Level                                                                                                         // High level operations: insert, find, delete

  class Find                                                                                                            // Find results
   {final Leaf leaf;                                                                                                    // Leaf that should contain the key
    final Key key;                                                                                                      // Search key
    final Slots.Locate locate;                                                                                          // Location details for key

    Find(Key Key, Leaf Leaf)
     {key    = Key;
      leaf   = Leaf;
      locate = Leaf.new Locate(Key);
     }

    public String toString()
     {final StringBuilder s = new StringBuilder();
      s.append("Find Key : "+key.i()+"\n");
      if (leaf    != null) s.append(""+leaf);
      if (locate  != null) s.append("Locate      : "+locate   +"\n");
      final StringJoiner j = new StringJoiner(", ");
      for(Branch p = leaf.up(); p != null; p = p.up()) j.add(""+p.name());
      if (leaf.up() != null) s.append("Path        : "+j+"\n");
      return ""+s;
     }
   }

  Find find(Key Key)
   {final Slots r = root();                                                                                             // Root of tree
    final Ref<Find> f = new Ref<>();                                                                                    // Find details result
    new If (r != null)                                                                                                  // Non empty tree
     {void Then()
       {new If (r.isLeaf())                                                                                             // Leaf root
         {void Then()
           {final Leaf L = (Leaf)r;
            L.up(null);
            L.upIndex(r.new Slot());                                                                                    // Trace path taken to this leaf
            f.set(new Find(Key, L));
           }
          void Else()
           {final Branch R = (Branch)r;                                                                                 // Start search from root
            R.up(null);
            R.upIndex(r.new Slot());                                                                                    // Show that there is nothing above the root
            f.set(find(Key, R));                                                                                        // Start search from root
           }
         };
       }
     };
    return f.get();
   }

  Find find(Key Key, Branch Start)
   {final Ref<Branch> p = new Ref<>(Start);                                                                             // Start at root
    final Ref<Find>   f = new Ref<>();                                                                                  // Find the Key

    new For(MaximumNumberOfLevels)                                                                                      // Step down from branch to branch splitting as we go
     {void body(Int i, Bool C)
       {final Slots.Slot Q = p.get().locateFirstGe(Key);
        final Slots      q = p.get().child(Q);
        new If (q.isLeaf())                                                                                             // Step down to a leaf
         {void Then()
           {final Leaf l = (Leaf)q;
            l.up(p.get()); l.upIndex(Q);                                                                                // Parent of leaf along find path
            f.set(new Find(Key, l));
           }
          void Else()
           {final Branch b = (Branch)q;
            b.up(p.get());                                                                                              // Record parent branch
            b.upIndex(If (Q.valid(), ()->Q.value(), ()->b.new Slot()));
            p.set(b);                                                                                                   // Step down into non full branch
           }
         };
        C.set(f.valid()).flip();
       }
     };
    if (f.valid().Flip().b())
     {stop("Find fell off the end of tree after this many searches:", mnl());
     }
    return f.get();
   }

  void insert(Key Key, Data Data)                                                                                       // Insert a key, data pair or update key data pair in the tree
   {final Bool d = new Bool().clear();                                                                                  // Try various insertion methods until one succeeds
    new If (root() == null)                                                                                             // Empty tree
     {void Then()
       {final Leaf l = new Leaf(); root(l);                                                                             // Root is a leaf
        l.insert(Key, Data);                                                                                            // Insert into leaf root
        d.set();
       }
      void Else()                                                                                                       // Localize optimized insert for non full leaf or full leaf under non full parent
       {final Find F = find(Key);                                                                                       // See if key is already present
        new If (F.locate.found())                                                                                       // Key already present so update data associated with the key
         {void Then()
           {final Leaf l = F.leaf;                                                                                      // Child leaf
            l.data(F.locate, Data);                                                                                     // Update data
            d.set();                                                                                                    // Success
           }
         };
        new If (d.Flip().and(()->{return F.leaf.full().Flip();}))                                                       // Leaf not full so insert directly
         {void Then()
           {final Leaf l = F.leaf;                                                                                      // Child leaf
            l.insert(Key, Data);                                                                                        // Insert key
            d.set();                                                                                                    // Success
           }
         };
        new If (d.Flip().and(()->{return new Bool(F.leaf.up() != null);}, ()->{return F.leaf.up().full().Flip();}))     // Leaf is full, parent branch is not full so we can split leaf
         {void Then()
           {final Branch b = F.leaf.up();                                                                               // Parent branch
            final Leaf   r = F.leaf;
            final Int   sk = r.splittingKey();
            final Leaf   l = r.splitLeft();
            b.insert(new Key(sk), l);                                                                                   // Insert new left leaf into leaf

            new If (Key.value().le(sk))                                                                                 // Insert new key, data pair into left leaf or right leaf depending on key
             {void Then()
               {l.insert(Key, Data);
               }
              void Else()
               {r.insert(Key, Data);
               }
             };

            final Slots.Slot K = b.locateFirstGe(Key);                                                                  // Position of leaf in parent
            new If (d.Flip().and(()->{return b.mergeLeftSibling (K);})) {void Then() {d.set();}};                       // Merge inserted leaf into prior leaf if possible
            new If (d.Flip().and(()->{return b.mergeRightSibling(K);})) {void Then() {d.set();}};                       // Merge inserted leaf into next leaf if possible
            new If (d.Flip().and(()->{return K.valid();}))                                                              // Some where in the body of the parent branch
             {void Then()
               {final Slots.Slot L = K.stepLeft(), R = K.stepRight();                                                   // Further left and right if possible
                new If (d.Flip().and(()->{return L.valid();}, ()->{return b.mergeLeftSibling (L);})) {void Then() {d.set();}};
                new If (d.Flip().and(()->{return R.valid();}, ()->{return b.mergeLeftSibling (R);})) {void Then() {d.set();}};
                new If (d.Flip().and(()->{return R.valid();}, ()->{return b.mergeRightSibling(R);})) {void Then() {d.set();}};
               }
             };
            new If (d.Flip().and(()->{return K.notValid();}))                                                           // Some where in the body of the parent branch
             {void Then()
               {final Slots.Slot L = b.locateLastUsedSlot();
                new If (d.Flip().and(()->{return L.valid();}, ()->{return b.mergeLeftSibling(L);}))
                 {void Then()
                   {d.set();
                   }
                 };
               }
             };
            new If (d.Flip())                                                                                           // Merge towards top
             {void Then()
               {b.mergeLeftSibling(b.new Slot());
                d.set();
               }
             };
           }
         };
       }
     };

    new If (d.Flip())
     {void Then()
       {new If (root().isLeaf() )                                                                                       // Leaf root
         {void Then()
           {final Leaf l = (Leaf)root();
            new If (l.full().Flip())                                                                                    // Still space in leaf root
             {void Then()
               {l.insert(Key, Data);                                                                                    // Insert into leaf root
                return;
               }
              void Else()
               {final Branch b = l.split();                                                                             // Split full leaf root
                root(b);
                insertTree(Key, Data);                                                                                  // Insert a key, data pair or update key data pair in the tree
               }
             };
           }
          void Else() {insertTree(Key, Data);}                                                                          // Insert a key, data pair or update key data pair in the tree
         };
       }
     };
   }

  void insertTree(Key Key, Data Data)                                                                                   // Insert a key, data pair or update key data pair in the tree
   {final Ref<Branch> p = new Ref<>((Branch)root());                                                                    // Start at root
    new If (p.get().full())                                                                                             // Split full root branch
     {void Then()
       {final Branch P = p.get();
        p.set(p.get().split());
        root(p.get());
        P.free();
       }
     };

    final Ref<Branch> q = new Ref<>(find(Key).leaf.up());                                                               // First branch above leaf
    final Ref<Branch> b = new Ref<>(q.get().up());                                                                      // Parent of first branch above leaf
    final Bool        D = new Bool().clear();                                                                           // First unfull branch above leaf has been located when set
    new If (b.valid())
     {void Then()
       {new For(numberOfNodes)                                                                                          // Look for first unfull branch along path up from leaf
         {void body(Int i, Bool C)                                                                                      // Found the first unfull branch
           {new If (b.get().full().Flip())
             {void Then()
               {p.set(b.get()); D.set();
               }
              void Else()
               {q.set(b.get());
                b.set(q.get().up());
               }
             };
            C.set(D).flip().and(()->{return b.valid();});
           }
         };
       }
     };

    final Bool d = new Bool().clear();                                                                                  // Set when done
    new For(MaximumNumberOfLevels)                                                                                      // Step down through the tree from branch to branch splitting as we go until we reach a leaf
     {void body(Int i, Bool C)
       {final Slots q = p.get().stepDown(Key);                                                                          // Step down
        C.set();
        new If (q.isLeaf())                                                                                             // Step down to a leaf
         {void Then()
           {final Leaf r = (Leaf)q;                                                                                     // We have reached a leaf
            new If (r.full())                                                                                           // Split the leaf if it is full
             {void Then()
              {final Int  sk = r.splittingKey();                                                                        // Splitting key
                final Leaf l = r.splitLeft();                                                                           // Right leaf split out of the leaf
                p.get().insert(new Key(sk), l);                                                                         // The parent is known not to be full so the insert will work.  We are inserting left so this works even if we are splitting top
                final Leaf L = If (Key.value().le(sk), ()->l, ()->r);                                                   // Choose left or right leaf depending on key
                L.insert(Key, Data);                                                                                    // Insert into left or right leaf which will now have space
               }
              void Else() {r.insert(Key, Data);}                                                                        // Leaf has sufficient space
             };
            mergeAlongPath(Key);                                                                                        // Merge along the path taken by the key to compress the tree
            C.clear(); d.set();
           }
          void Else()                                                                                                   // Step down to a branch
           {final Branch r = (Branch)q;
            new If (r.full())                                                                                           // Split the leaf if it is full
             {void Then()
               {final Int         sk = r.splittingKey();                                                                // Splitting key
                final Branch.Split s = r.splitLeft();                                                                   // Branch split out on right from
                p.get().insert(new Key(sk), s.left);                                                                    // The parent is known not to be full so the insert will work.  We are inserting left so this works even if we are splitting top
                p.set(If (Key.value().le(sk), ()->s.left, ()->s.right));                                                // Traverse left or right
               }
              void Else() {p.set(r);}                                                                                   // Step down into non full branch
             };
           }
         };
       }
     };
    new If (d.Flip())                                                                                                   // Unable to insert
     {void Then()
       {stop("Insert fell off the end of tree after this many searches:", mnl());
       }
     };
   }

  void delete(Key Key)                                                                                                  // Delete a key from the tree
   {new If (root() != null)                                                                                             // The tree is not empty so there might be something to delete
     {void Then()
       {final Find f = find(Key);                                                                                       // Locate the key in the tree
        new If (f.locate.found())                                                                                       // Key found so delete it
         {void Then()
           {f.leaf.clearSlotAndRef(f.locate);                                                                           // Delete key and data from leaf
            mergeAlongPath(Key);
           }
         };
       }
     };
   }

  Int count()                                                                                                           // Count the number of entries in the tree
   {final Slots r = root();
    return If (new Bool(r == null),
      ()->new Int(0),
      ()->If (r.isLeaf(), ()->r.countUsed(), ()->((Branch)r).count()));
   }

//D2 Navigation                                                                                                         // First, Last key, or find the next or prev key from a given key

  Find first()                                                                                                          // Find the position of the first key in the key
   {final Ref<Find> f = new Ref<>();
    new If (root() != null)                                                                                             // Non empty tree
     {void Then()
       {new If (root().isLeaf() )                                                                                       // The tree is one leaf
         {void Then()
           {final Leaf       l = (Leaf)root();
            final Slots.Slot i = l.locateFirstUsedSlot();
            l.up(null); l.upIndex(i);
            f.set(new Find(l.keys(i), l));
           }
          void Else()                                                                                                   // The tree has at least one branch
           {f.set(goFirst((Branch)root()));                                                                             // Start at root and go all the way first
           }
         };
       }
     };
    return f.get();
   }

  Find goFirst(Branch Start)                                                                                            // Go all the way first
   {Ref<Branch> p = new Ref<>(Start);                                                                                   // Start
    Ref<Find>   f = new Ref<>();

    new For(MaximumNumberOfLevels)                                                                                      // Step down from branch to branch
     {void body(Int I, Bool c)
       {final Slots.Slot P = p.get().locateFirstUsedSlot();
        final Slots      q = p.get().child(P);

        new If (q.isLeaf())                                                                                             // Step down to a leaf
         {void Then()
           {final Leaf l = (Leaf)q;
            l.up(p.get()); l.upIndex(P);
            final Int i = l.locateFirstUsedSlot();
            f.set(new Find(l.keys(l.new Slot(i)), l));                                                                  // Reached a leaf
            c.clear();
           }
          void Else()
           {final Branch b = (Branch)q;
            b.up(p.get()); b.upIndex(P);                                                                                // Step down into non full branch
            p.set(b);
            c.set();
           }
         };
       }
     };
    new If (f.valid().Flip())
     {void Then()
       {stop("First fell off the end of tree after this many searches:", mnl());
       }
     };
    return f.get();
   }

  Find last()                                                                                                           // Find the position of the last key in the tree
   {final Ref<Find> f = new Ref<>();
    new If (root() != null)                                                                                             // Non empty tree
     {void Then()
       {new If (root().isLeaf() )
         {void Then()
           {final Leaf l = (Leaf)root();
            final Int  i = l.locateLastUsedSlot().value();
            l.up(null);
            l.upIndex(l.new Slot());
            f.set(new Find(l.keys(l. new Slot(i)), l));
           }
          void Else()
           {f.set(goLast((Branch)root()));                                                                              // Start at root and go all the way last
           }
         };
       }
     };
    return f.get();
   }

  Find goLast(Branch Start)                                                                                             // Go all the way last from the specified position
   {final Ref<Branch> p = new Ref<>(Start);                                                                             // Start
    final Ref<Find>   f = new Ref<>();

    new For(MaximumNumberOfLevels)                                                                                      // Step down from branch to branch splitting as we go
     {void body(Int i, Bool C)
       {final Slots q = p.get().top();
        new If (q.isLeaf())                                                                                             // Step down to a leaf
         {void Then()
           {final Leaf l = (Leaf)q;
            l.up(p.get()); l.upIndex(l.new Slot());
            f.set(new Find(l.keys(l.locateLastUsedSlot()), l));
           }
          void Else()
           {final Branch b = (Branch)q;
            b.up(p.get());                                                                                              // Reference parent
            p.set(b);                                                                                                   // Step down into non full branch
           }
         };
        C.set(f.valid()).flip();
       };
     };
    new If (f.valid().Flip())                                                                                           // Unable to find the last element
     {void Then()
       {stop("Last fell off the end of tree after this many searches:", mnl());
       }
     };
    return f.get();
   }

  Find next(Find Found)                                                                                                 // Find the next key beyond the one previously found assuming that the structure of the tree has not changed
   {final Ref<Find> f = new Ref<>();                                                                                    // Next key expressed as a find specification
    final Leaf l = Found.leaf;                                                                                          // Leaf we are currently traversing

    new If (root() != null && l.up() != null)                                                                           // Tree has branches
     {void Then()
       {final Slots.Slot r = Found.locate.stepRight();                                                                  // Next slot to the right in the leaf
        new If (r.valid())                                                                                              // There is a next slot to the right so go to it
          {void Then()
           {f.set(new Find(l.keys(r), l));                                                                              // There is a next slot to the right in the leaf so return it
           }
          void Else()                                                                                                   // We are at the end of the current branch
           {final Branch U = l.up();                                                                                    // Parent branch of the leaf

            new If (U.top().name().ne(l.name()))                                                                        // In the body of the parent branch of the leaf but not at the top of the parent
             {void Then()
               {final Slots.Slot u = l.upIndex(U);                                                                      // Next sibling slot right
                final Slots.Slot R = If (u.valid(), ()->u.stepRight(), ()->l.new Slot());                               // Next sibling slot right
                final Leaf       L = If (R.valid(), ()->U.data(R), ()->U.top());                                        // Next sibling leaf
                L.up(U); L.upIndex(R);
                f.set(new Find(L.firstKey(), L));
               }
              void Else()
               {final Ref<Branch> q = new Ref<>(l.up());                                                                // First branch above the leaf
                final Ref<Branch> p = new Ref<>(q.get().up());                                                          // Last point at which we went left

                new If (p.valid())                                                                                      // Branch above the leaf exists
                 {void Then()
                   {new For(numberOfNodes)                                                                              // Step up to turning point
                     {void body(Int i, Bool C)
                       {final Branch P = p.get(), Q = q.get();
                        new If (P.top().name().ne(Q.name()))                                                            // In the body of the parent branch of the leaf
                         {void Then()
                           {final Int    I = Q.upIndex();                                                               // Not null because we are not at the root
                            final Slots.Slot R = P.new Slot(I).stepRight();                                             // Next sibling to the right
                            final Bool   r = R.valid();                                                                 // Next sibling to the right exists
                            final Branch b = If (r,  ()->P.data(R), ()->P.top());                                       // Must be a branch as we are going up through the tree
                            b.up(p.get());
                            b.upIndex(If (r, ()->R, ()->b.new Slot()));
                            f.set(goFirst(b));
                           }
                          void Else()                                                                                   // Go up one more level
                           {q.set(p);
                            p.set(q.get().up());
                           }
                         };
                        C.set(f.valid()).flip().and(()->{return p.valid();});                                           // Continue until we find the first leaf
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

  Find prev(Find Found)                                                                                                 // Find the previous key before the one previously found assuming that the structure of the tree has not changed
   {final Ref<Find> f = new Ref<>();                                                                                    // Details of located previous key expressed as a find specification
    final Leaf l = Found.leaf;                                                                                          // The leaf we are currently traversing

    new If (root() != null && l.up() != null)                                                                           // Tree is not empty and not a leaf that we are at the end of
     {void Then()
       {final Slots.Slot s = Found.locate.stepLeft();                                                                   // Previous slot in leaf
        new If (s.valid())
         {void Then()
           {f.set(new Find(l.keys(s), l));
           }
          void Else()
           {final Branch P = l.up();                                                                                    // Parent
            new If (l.upIndex(P).notValid())                                                                            // Last leaf of parent
             {void Then()
               {final Slots.Slot I = P.locateLastUsedSlot();                                                            // Element prior to top
                final Leaf       L = (Leaf)P.data(I);
                L.upIndex(I);
                f.set(new Find(L.lastKey(), L));
               }
              void Else()
               {new If (l.upIndex(P).ne(l.locateFirstUsedSlot()))                                                       // Not the first leaf of the parent branch
                 {void Then()
                   {final Slots.Slot U = l.upIndex(P);
                    final Slots.Slot u = U.stepLeft();
                    final Leaf  L = If (u.valid(), ()->P.data(u), ()->P.top());
                    L.upIndex(u);
                    f.set(new Find(L.lastKey(), L));
                   }
                  void Else()
                   {final Ref<Branch> q = new Ref<>(l.up());                                                            // First branch above the leaf
                    final Ref<Branch> p = new Ref<>(q.get().up());                                                      // Locate last point at which we went left

                    new If (p.valid())
                     {void Then()
                       {new For(numberOfNodes)                                                                          // Go up to the last point where we went left
                         {void body(Int i, Bool C)
                           {new If (q.get().upIndex().valid().Flip())                                                   // In the body of the parent branch of the leaf
                             {void Then()
                               {final Branch     P = p.get();
                                final Slots.Slot I = P.locateLastUsedSlot();
                                final Branch     b = (Branch)P.data(I);
                                b.up(p.get());   b.upIndex(I);
                                f.set(goLast(b));
                               }
                              void Else()                                                                               // Go up to next branch
                               {q.set(p);
                                new If (q.valid())
                                 {void Then() {p.set(q.get().up());}
                                 };
                               }
                             };
                            C.set(f.valid()).flip().and(()->{return p.valid();});                                       // Continue until we find the first leaf
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

//D2 Print                                                                                                              // Print the tree horizontally

  final int linesToPrintABranch = 4;                                                                                    // The number of lines required to print a branch
  final int maxPrintLevels      = 3;                                                                                    // The maximum number of levels to print - this avoids endless print loops when something goes wrong

  void printLeaf                                                                                                        // Print leaf horizontally
   (Leaf Leaf, Stack<StringBuilder>P, int level, boolean Details,
    Branch Parent, Integer Index)
   {final Leaf L = Leaf;
    padStrings(P, level);

    final StringJoiner s = new StringJoiner(",");
    for (int i : range(L.numberOfSlots()))
     {final Slots.Slot l = L.new Slot(i);
      if (L.usedSlots(l).b()) s.add(""+L.keys(l).i());
     }
    final int N = level * linesToPrintABranch;                                                                          // Start line at which to print branch
    P.elementAt(N+0).append(s);
    final String U = Parent != null ? "" + Parent.name() : "*";
    final String I = Index  != null ? "" + Index         : "*";
    if (Details)
     {P.elementAt(N+1).append("("+L.name()+", "+U+", "+I+")");
     }
    padStrings(P, level);
   }

  void printBranch                                                                                                      // Print branch horizontally
   (Branch Branch, Stack<StringBuilder>P, int level, boolean Details,
    Branch Parent, Integer Index)                                                                                       // Details of parent which might differ from what is actually stored in the tree
   {final Branch B = Branch;
    final int L = level * linesToPrintABranch;                                                                          // Size of branch

    if (level > maxPrintLevels) return;
    padStrings(P, level);

    if (B.countUsed().gt(0).b())                                                                                        // Branch has key, next pairs
     {for (int i : range(B.numberOfSlots()))
       {if (B.usedSlots(B.new Slot(i)).b())
         {final Slots s = B.data(B.new Slot(i));
          if (s == null) continue;
          final Bool l = s.isLeaf();
          final Bool b = s.isBranch();

          if      (l.b()) printLeaf  ((Leaf)  s, P, level+1, Details, B, i);
          else if (b.b()) printBranch((Branch)s, P, level+1, Details, B, i);

            P.elementAt(L+0).append(" "+B.keys(B.new Slot(i)).i());                                                     // Key
          if (Details)
           {P.elementAt(L+1).append("["+B.name()+"."+i+"]");                                                            // Branch, key, next pair
            final String U = Parent != null ? ""+Parent.name() : "*";                                                   // Parent up from descent
            final String I = Index  != null ? ""+Index         : "*";                                                   // Index in parent up
            P.elementAt(L+2).append("("+s.name()+", "+U+", "+I+")");                                                    // Link to next level

            final String um = ""+B.memory.up();                                                                         // Parent up as recorded
            final Int    ii =    B.upIndex();                                                                           // Index in parent up as recorded
            final String im = ii.v() ? ""+ii.i() : "0";                                                                 // Index in parent up as string
            P.elementAt(L+3).append("("+s.name()+", "+um+", "+im+")");                                                  // Link to next level
           }
         }
       }
     }

    if (Details)                                                                                                        // Top of branch
     {P.elementAt(L+2).append
       ("{"+(B.top() != null ? B.top().name() : "null")+"}");
     }

    final Bool l = B.top().isLeaf();
    final Bool b = B.top().isBranch();                                                                                  // Print top leaf
    if      (l.b()) printLeaf  (  (Leaf)B.top(), P, level+1, Details, B, null);
    else if (b.b()) printBranch((Branch)B.top(), P, level+1, Details, B, null);

    padStrings(P, level);                                                                                               // Equalize the strings used to print the tree
   }

  String printBoxed()                                                                                                   // Print a tree in a box
   {final String  s = ""+this;
    final int     n = longestLine(s)-1;
    final String[]L = s.split("\n");
    final StringJoiner t = new StringJoiner("\n",  "", "\n");
    t.add("+"+("-".repeat(n))+"+");
    for(String l : L) t.add("| "+l);
    t.add("+"+("-".repeat(n))+"+");
    return ""+t;
   }

  void padStrings(Stack<StringBuilder> S, int level)                                                                    // Pad the strings at each level of the tree so we have a vertical face to continue with - a bit like Marc Brunel's tunneling shield
   {final int N = level * linesToPrintABranch + maxLeafSize;                                                            // Number of lines we might want
    for (int i = S.size(); i <= N; ++i) S.push(new StringBuilder());                                                    // Make sure we have a full deck of strings
    int m = 0;                                                                                                          // Maximum length
    for (StringBuilder s : S) m = m < s.length() ? s.length() : m;                                                      // Find maximum length
    for (StringBuilder s : S)                                                                                           // Pad each string to maximum length
     {if (s.length() < m) s.append(" ".repeat(m - s.length()));                                                         // Pad string to maximum length
     }
   }

  String printCollapsed(Stack<StringBuilder> S)                                                                         // Collapse horizontal representation into a string
   {final StringBuilder t = new StringBuilder();                                                                        // Print the lines of the tree that are not blank
    for  (StringBuilder s : S)
     {final String l = ""+s;
      if (!l.isBlank()) t.append(l+"|\n");
     }
    return ""+t;
   }

  public String toString() {return print(false);}                                                                       // Print the tree without details
  public String dump()     {return print(true); }                                                                       // Print the tree with details

  String print(boolean Details)                                                                                         // Print the tree with and without linkage details
   {final Stack<StringBuilder> P = new Stack<>();
    if (root() == null) return "|\n";                                                                                   // Empty tree
    final Bool lr = root().isLeaf() ;
    if (lr.b()) printLeaf  ((Leaf)  root(), P, 0, Details, null, null);                                                 // Tree is a single leaf
    else        printBranch((Branch)root(), P, 0, Details, null, null);                                                 // Tree has one or more branches
    return printCollapsed(P);                                                                                           // Remove blank lines and add right fence
   }

  String db()                                                                                                           // Raw dump of memory used by the tree to assist with debugging
   {final StringBuilder s = new StringBuilder();
    final ByteBuffer b = memory.bytes;
    final int N = b.capacity();
    for (int l = 0, c = 0; l < 10; ++l)                                                                                 // Print first few nodes one per line
     {final StringJoiner j = new StringJoiner(" ");
      for (int i : range(sizeOfNode))
       {if (c++ >= N) break;
        j.add(""+b.get(l * sizeOfNode + i));
       }
      s.append(""+j+"\n");
      if (c >= N) break;
     }
    return ""+s;
   }
*/
//D1 Tests                                                                                                              // Tests

  final static int[]random_32 = {12, 3, 27, 1, 23, 20, 8, 18, 2, 31, 25, 16, 13, 32, 11, 21, 5, 24, 4, 10, 26, 30, 9, 6, 29, 17, 28, 15, 14, 19, 7, 22};
  final static int[]random    = {5918,5624,2514,4291,1791,5109,7993,60,1345,2705,5849,1034,2085,4208,4590,7740,9367,6582,4178,5578,1120,378,7120,8646,5112,4903,1482,8005,3801,5439,4534,9524,6111,204,5459,248,4284,8037,5369,7334,3384,5193,2847,1660,5605,7371,3430,1786,1216,4282,2146,1969,7236,2187,136,2726,9480,5,4515,6082,969,5017,7809,9321,3826,9179,5781,3351,4819,4545,8607,4146,6682,1043,2890,2964,7472,9405,4348,8333,2915,9674,7225,4743,995,1321,3885,6061,9958,3901,4710,4185,4776,5070,8892,8506,6988,2317,9342,3764,9859,4724,5195,673,359,9740,2089,9942,3749,9208,1,7446,7023,5496,4206,3272,3527,8593,809,3149,4173,9605,9021,5120,5265,7121,8667,6911,4717,2535,2743,1289,1494,3788,6380,9366,2732,1501,8543,8013,5612,2393,7041,3350,3204,288,7213,1741,1238,9830,6722,4687,6758,8067,4443,5013,5374,6986,282,6762,192,340,5075,6970,7723,5913,1060,1641,1495,5738,1618,157,6891,173,7535,4952,9166,8950,8680,1974,5466,2383,3387,3392,2188,3140,6806,3131,6237,6249,7952,1114,9017,4285,7193,3191,3763,9087,7284,9170,6116,3717,6695,6538,6165,6449,8960,2897,6814,3283,6600,6151,4624,3992,5860,9557,1884,5585,2966,1061,6414,2431,9543,6654,7417,2617,878,8848,8241,3790,3370,8768,1694,9875,9882,8802,7072,3772,2689,5301,7921,7774,1614,494,2338,8638,4161,4523,5709,4305,17,9626,843,9284,3492,7755,5525,4423,9718,2237,7401,2686,8751,1585,5919,9444,3271,1490,7004,5980,3904,370,5930,6304,7737,93,5941,9079,4968,9266,262,2766,4999,2450,9518,5137,8405,483,8840,2231,700,8049,8823,9811,9378,3811,8074,153,1940,1998,4354,7830,7086,6132,9967,5680,448,1976,4101,7839,3122,4379,9296,4881,1246,4334,9457,5401,1945,9548,8290,1184,3464,132,2458,7704,1056,7554,6203,2270,6070,4889,7369,1676,485,3648,357,1912,9661,4246,1576,1836,4521,7667,6907,2098,8825,7404,4019,8284,3710,7202,7050,9870,3348,3624,9224,6601,7897,6288,3713,932,5596,353,2615,3273,833,1446,8624,2489,3872,486,1091,2493,4157,3611,6570,7107,9153,4543,9504,4746,1342,9737,3247,8984,3640,5698,7814,307,8775,1150,4330,3059,5784,2370,5248,4806,6107,9700,231,3566,5627,3957,5317,5415,8119,2588,9440,2961,9786,4769,466,5411,3080,7623,5031,2378,9286,4801,797,1527,2325,847,6341,5310,1926,9481,2115,2165,5255,5465,5561,3606,7673,7443,7243,8447,2348,7925,6447,8311,6729,4441,7763,8107,267,8135,9194,6775,3883,9639,612,5024,1351,7557,9241,5181,2239,8002,5446,747,166,325,9925,3820,9531,5163,3545,558,7103,7658,5670,8323,4821,6263,7982,59,3700,1082,4474,4353,8637,9558,5191,842,5925,6455,4092,9929,9961,290,3523,6290,7787,8266,7986,7269,6408,3620,406,5964,7289,1620,6726,1257,1993,7006,5545,2913,5093,5066,3019,7081,6760,6779,7061,9051,8852,8118,2340,6596,4594,9708,8430,8659,8920,9268,5431,9203,2823,1427,2203,6422,6193,5214,9566,8791,4964,7575,4350,56,2227,8545,5646,3089,2204,4081,487,8496,2258,4336,6955,3452,556,8602,8251,8569,8636,9430,1025,9459,7137,8392,3553,5945,9414,3078,1688,5480,327,8117,2289,2195,8564,9423,103,7724,3091,8548,7298,5279,6042,2855,3286,3542,9361,420,7020,4112,5320,5366,6379,114,9174,9744,592,5346,3985,3174,5157,9890,1605,3082,8099,4346,7256,8670,5687,6613,6620,1458,1045,7917,2980,2399,1433,3315,4084,178,7056,2132,2728,4421,9195,4181,6017,6229,2945,4627,2809,8816,6737,18,8981,3813,8890,5304,3789,6959,7476,1856,4197,6944,9578,5915,3060,9932,3463,67,7393,9857,5822,3187,501,653,8453,3691,9736,6845,1365,9645,4120,2157,8471,4436,6435,2758,7591,9805,7142,7612,4891,7342,5764,8683,8365,2967,6947,441,2116,6612,1399,7585,972,6548,5481,7733,7209,222,5903,6161,9172,9628,7348,1588,5992,6094,7176,4214,8702,2987,74,8486,9788,7164,5788,8535,8422,6826,1800,8965,4965,565,5609,4686,2556,9324,5000,9809,1994,4737,63,8992,4783,2536,4462,8868,6346,5553,3980,2670,1601,4272,8725,4698,7333,7826,9233,4198,1997,1687,4851,62,7893,8149,8015,341,2230,1280,5559,9756,3761,7834,6805,9287,4622,5748,2320,1958,9129,9649,1644,4323,5096,9490,7529,6444,7478,7044,9525,7713,234,7553,9099,9885,7135,6493,9793,6268,8363,2267,9157,9451,1438,9292,1637,3739,695,1090,4731,4549,5171,5975,7347,5192,5243,1084,2216,9860,3318,5594,5790,1107,220,9397,3378,1353,4498,6497,5442,7929,7377,9541,9871,9895,6742,9146,9409,292,6278,50,5288,2217,4923,6790,4730,9240,3006,3547,9347,7863,4275,3287,2673,7485,1915,9837,2931,3918,635,9131,1197,6250,3853,4303,790,5548,9993,3702,2446,3862,9652,4432,973,41,3507,8585,2444,1633,956,5789,1523,8657,4869,8580,8474,7093,7812,2549,7363,9315,6731,1130,7645,7018,7852,362,1636,2905,8006,4040,6643,8052,7021,3665,8383,715,1876,2783,3065,604,4566,8761,7911,1983,3836,5547,8495,8144,1950,2537,8575,640,8730,8303,1454,8165,6647,4762,909,9449,8640,9253,7293,8767,3004,4623,6862,8994,2520,1215,6299,8414,2576,6148,1510,313,3693,9843,8757,5774,8871,8061,8832,5573,5275,9452,1248,228,9749,2730};

  static void test_tree(boolean Ex)
   {final Tree t = new Tree(new Build().maxLeafSize(2).maxBranchSize(3).numberOfNodes(4).immediate(Ex));
                                 t.freeChain.usedKeys.countZeros().ok(0);
    final Leaf   a = t.leaf();   t.freeChain.usedKeys.countZeros().ok(1);
    final Leaf   b = t.leaf();   t.freeChain.usedKeys.countZeros().ok(2);
    final Branch c = t.branch(); t.freeChain.usedKeys.countZeros().ok(3);
    a.insert(t.new Int(2), t.new Int(22));
    b.insert(t.new Int(4), t.new Int(44));
    c.insert(t.new Int(5), t.new Int(55));

    final Leaf   A = t.leaf  (a.at); t.isAllocated(a.at).ok(true);
    final Leaf   B = t.leaf  (b.at); t.isAllocated(b.at).ok(true);
    final Branch C = t.branch(c.at); t.isAllocated(c.at).ok(true);

    A.insert(t.new Int(1), t.new Int(11));
    B.insert(t.new Int(3), t.new Int(33));
    C.insert(t.new Int(6), t.new Int(66));

    t.dumpTree();
    //t.new I() {void action() {stop(t.out);}};
    t.dumpTree("""
Tree memory dump
Leaf   size   :   79
Branch size   :  121
Node   size   :  121
MaxLeafSize   :    2
MaxBranchSize :    3
NumberOfNodes :    4
Allocations   :    3
   0 Leaf: size:   2
 Ref   Key  Data
   1     1    11
   0     2    22
   1 Leaf: size:   2
 Ref   Key  Data
   1     3    33
   0     4    44
   2 Branch: size:   3 top:   0
 Ref   Key  Data
   0     5    55
   1     6    66
""");

               t.isAllocated(a.at).ok(true);
    t.free(A); t.isAllocated(a.at).ok(false);
    t.dumpTree();
    //t.new I() {void action() {stop(t.out);}};
    t.dumpTree("""
Tree memory dump
Leaf   size   :   79
Branch size   :  121
Node   size   :  121
MaxLeafSize   :    2
MaxBranchSize :    3
NumberOfNodes :    4
Allocations   :    2
   1 Leaf: size:   2
 Ref   Key  Data
   1     3    33
   0     4    44
   2 Branch: size:   3 top:   0
 Ref   Key  Data
   0     5    55
   1     6    66
""");
               t.isAllocated(b.at).ok(true);
    t.free(b); t.isAllocated(b.at).ok(false);
    t.dumpTree();
    //t.new I() {void action() {stop(t.out);}};
    t.dumpTree("""
Tree memory dump
Leaf   size   :   79
Branch size   :  121
Node   size   :  121
MaxLeafSize   :    2
MaxBranchSize :    3
NumberOfNodes :    4
Allocations   :    1
   2 Branch: size:   3 top:   0
 Ref   Key  Data
   0     5    55
   1     6    66
""");

               t.isAllocated(c.at).ok(true);
    t.free(c); t.isAllocated(c.at).ok(false);
    t.dumpTree();
    //t.new I() {void action() {stop(t.out);}};
    t.dumpTree("""
Tree memory dump
Leaf   size   :   79
Branch size   :  121
Node   size   :  121
MaxLeafSize   :    2
MaxBranchSize :    3
NumberOfNodes :    4
Allocations   :    0
""");

    t.maxSteps = 99999;
    t.execute();
   }

  static void test_tree()
   {test_tree(true);
    test_tree(false);
   }

  static void test_insert(boolean Ex)
   {final Tree t = new Tree(new Build().maxLeafSize(4).maxBranchSize(3).numberOfNodes(4).immediate(Ex));

    t.insert(t.new Int(1), t.new Int(11));
    t.insert(t.new Int(2), t.new Int(22));
    t.insert(t.new Int(3), t.new Int(33));
    t.insert(t.new Int(4), t.new Int(44));
    t.dumpTree();
    t.dumpTree("""
Tree memory dump
Leaf   size   :  153
Branch size   :  121
Node   size   :  153
MaxLeafSize   :    4
MaxBranchSize :    3
NumberOfNodes :    4
Allocations   :    1
   0 Leaf: size:   4
 Ref   Key  Data
   0     1    11
   1     2    22
   2     3    33
   3     4    44
""");
    t.execute();
   }

  static void test_insert()
   {          test_insert(true);
//            test_insert(false);
   }

/*

  static void test_insert2()
   {final Tree t = new Tree(2, 3);
    final int N = 32;
    final StringBuilder s = new StringBuilder();
    for (int i = 1; i <= N; ++i)
     {Tree.debug = i >= 3;
      t.insert(t.new Key(i), t.new Data(i));
      t.freeCheck();
      s.append("Insert: "+i+"\n"+t);
     }
  //testStop(s);
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
    t.insert(t.new Key(11), t.new Data(21));
    ok(t, """
11|
""");
    t.insert(t.new Key(13), t.new Data(23));
    ok(t, """
11,13|
""");
    t.insert(t.new Key(12), t.new Data(22));
    ok(t, """
11,12,13|
""");
    t.insert(t.new Key(14), t.new Data(24));
    ok(t, """
11,12,13,14|
""");
    t.insert(t.new Key(15), t.new Data(25));
    ok(t, """
      12        |
11,12   13,14,15|
""");
    t.insert(t.new Key(16), t.new Data(26));
    ok(t, """
      12           |
11,12   13,14,15,16|
""");
    t.insert(t.new Key(17), t.new Data(27));
    ok(t, """
            14        |
11,12,13,14   15,16,17|
""");
    t.insert(t.new Key(18), t.new Data(28));
    ok(t, """
            14           |
11,12,13,14   15,16,17,18|
""");
    t.insert(t.new Key(19), t.new Data(29));
    ok(t, """
            14      16        |
11,12,13,14   15,16   17,18,19|
""");
    t.insert(t.new Key(20), t.new Data(30));
    ok(t, """
            14      16           |
11,12,13,14   15,16   17,18,19,20|
""");
    t.insert(t.new Key(21), t.new Data(31));
    ok(t, """
            14            18        |
11,12,13,14   15,16,17,18   19,20,21|
""");
    t.insert(t.new Key(22), t.new Data(32));
    ok(t, """
            14            18           |
11,12,13,14   15,16,17,18   19,20,21,22|
""");
    t.insert(t.new Key(23), t.new Data(33));
    ok(t, """
            14            18      20        |
11,12,13,14   15,16,17,18   19,20   21,22,23|
""");
    t.insert(t.new Key(24), t.new Data(34));
    ok(t, """
            14            18      20           |
11,12,13,14   15,16,17,18   19,20   21,22,23,24|
""");
    t.insert(t.new Key(25), t.new Data(35));
    ok(t, """
            14            18            22        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25|
""");
    ok(t.count(), 15);
    t.insert(t.new Key(26), t.new Data(36));
    ok(t, """
            14            18            22           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26|
""");
    ok(t.count(), 16);
    t.insert(t.new Key(27), t.new Data(37));
    ok(t, """
                          18                              |
            14                          22      24        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24   25,26,27|
""");
    ok(t.count(), 17);
    t.insert(t.new Key(28), t.new Data(38));
    ok(t, """
                          18                                 |
            14                          22      24           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24   25,26,27,28|
""");
    ok(t.count(), 18);
    t.insert(t.new Key(29), t.new Data(39));
    ok(t, """
                          18                                    |
            14                          22            26        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29|
""");
    t.insert(t.new Key(30), t.new Data(40));
    ok(t, """
                          18                                       |
            14                          22            26           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30|
""");
    t.insert(t.new Key(31), t.new Data(41));
    ok(t, """
                          18                                            |
            14                          22            26      28        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28   29,30,31|
""");
    t.insert(t.new Key(32), t.new Data(42));
    ok(t, """
                          18                                               |
            14                          22            26      28           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28   29,30,31,32|
""");
    t.insert(t.new Key(33), t.new Data(43));
    ok(""+t, """
                                                      26                      |
            14            18            22                          30        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33|
""");
    t.insert(t.new Key(34), t.new Data(44));
    ok(t, """
                                                      26                         |
            14            18            22                          30           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34|
""");
    t.insert(t.new Key(35), t.new Data(45));
    ok(t, """
                                                      26                              |
            14            18            22                          30      32        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32   33,34,35|
""");
    t.insert(t.new Key(36), t.new Data(46));
    ok(t, """
                                                      26                                 |
            14            18            22                          30      32           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32   33,34,35,36|
""");
    t.insert(t.new Key(37), t.new Data(47));
    ok(t, """
                                                      26                                    |
            14            18            22                          30            34        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36,37|
""");
    t.insert(t.new Key(38), t.new Data(48));
    ok(t, """
                                                      26                                       |
            14            18            22                          30            34           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36,37,38|
""");
    t.insert(t.new Key(39), t.new Data(49));
    ok(t, """
                                                      26                                            |
            14            18            22                          30            34      36        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36   37,38,39|
""");
    t.insert(t.new Key(40), t.new Data(50));
    ok(t, """
                                                      26                                               |
            14            18            22                          30            34      36           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36   37,38,39,40|
""");
    t.insert(t.new Key(41), t.new Data(51));
    ok(t, """
                                                      26                                                  |
            14            18            22                          30            34            38        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36,37,38   39,40,41|
""");
    t.insert(t.new Key(42), t.new Data(52));
    ok(t, """
                                                      26                                                     |
            14            18            22                          30            34            38           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36,37,38   39,40,41,42|
""");
    ok(t.count(), 32);

    ok(t.find(t.new Key(10)), """
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

    ok(t.find(t.new Key(23)), """
Find Key : 23
Leaf     : 10 up: 7 index: null
Slots    : name: 10, type:  0, refs:  4
positions:    0   1   2   3   4   5   6   7
slots    :    0   0   1   0   2   0   3   0
usedSlots:    X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X
keys     :   23  24  25  26
data     :   33  34  35  36
Locate      : 0 found
Path        : 7, 9
""");
   }

  static void test_insert_reverse()
   {final Tree t = new Tree(4, 3);
    final int N = 32;
    for (int i = N; i > 0; i--)
     {t.insert(t.new Key(i), t.new Data(i));
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
Locate      : 0 found
Path        : 7, 9
""");

    final Find  n2 = t.next(n1);  ok(n2.key.i(),   2);
    final Find  n3 = t.next(n2);  ok(n3.key.i(),   3);
    final Find  n4 = t.next(n3);  ok(n4.key.i(),   4);
    final Find  n5 = t.next(n4);  ok(n5.key.i(),   5);
    final Find  n6 = t.next(n5);  ok(n6.key.i(),   6);
    final Find  n7 = t.next(n6);  ok(n7.key.i(),   7);
    final Find  n8 = t.next(n7);  ok(n8.key.i(),   8);
    final Find  n9 = t.next(n8);  ok(n9.key.i(),   9);
    final Find n10 = t.next(n9);  ok(n10.key.i(), 10);
    final Find n11 = t.next(n10); ok(n11.key.i(), 11);
    final Find n12 = t.next(n11); ok(n12.key.i(), 12);
    final Find n13 = t.next(n12); ok(n13.key.i(), 13);
    final Find n14 = t.next(n13); ok(n14.key.i(), 14);
    final Find n15 = t.next(n14); ok(n15.key.i(), 15);
    final Find n16 = t.next(n15); ok(n16.key.i(), 16);
    final Find n17 = t.next(n16); ok(n17.key.i(), 17);
    final Find n18 = t.next(n17); ok(n18.key.i(), 18);
    final Find n19 = t.next(n18); ok(n19.key.i(), 19);
    final Find n20 = t.next(n19); ok(n20.key.i(), 20);
    final Find n21 = t.next(n20); ok(n21.key.i(), 21);
    final Find n22 = t.next(n21); ok(n22.key.i(), 22);
    final Find n23 = t.next(n22); ok(n23.key.i(), 23);
    final Find n24 = t.next(n23); ok(n24.key.i(), 24);
    final Find n25 = t.next(n24); ok(n25.key.i(), 25);
    final Find n26 = t.next(n25); ok(n26.key.i(), 26);
    final Find n27 = t.next(n26); ok(n27.key.i(), 27);
    final Find n28 = t.next(n27); ok(n28.key.i(), 28);
    final Find n29 = t.next(n28); ok(n29.key.i(), 29);
    final Find n30 = t.next(n29); ok(n30.key.i(), 30);
    final Find n31 = t.next(n30); ok(n31.key.i(), 31);
    final Find n32 = t.next(n31); ok(n32.key.i(), 32);
    final Find n33 = t.next(n32); ok(n33 == null);

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
Locate      : 6 found
Path        : 2, 9
""");


    final Find p31 = t.prev(n32); ok(p31.key.i(), 31);
    final Find p30 = t.prev(p31); ok(p30.key.i(), 30);
    final Find p29 = t.prev(p30); ok(p29.key.i(), 29);
    final Find p28 = t.prev(p29); ok(p28.key.i(), 28);
    final Find p27 = t.prev(p28); ok(p27.key.i(), 27);
    final Find p26 = t.prev(p27); ok(p26.key.i(), 26);
    final Find p25 = t.prev(p26); ok(p25.key.i(), 25);
    final Find p24 = t.prev(p25); ok(p24.key.i(), 24);
    final Find p23 = t.prev(p24); ok(p23.key.i(), 23);
    final Find p22 = t.prev(p23); ok(p22.key.i(), 22);
    final Find p21 = t.prev(p22); ok(p21.key.i(), 21);
    final Find p20 = t.prev(p21); ok(p20.key.i(), 20);
    final Find p19 = t.prev(p20); ok(p19.key.i(), 19);
    final Find p18 = t.prev(p19); ok(p18.key.i(), 18);
    final Find p17 = t.prev(p18); ok(p17.key.i(), 17);
    final Find p16 = t.prev(p17); ok(p16.key.i(), 16);
    final Find p15 = t.prev(p16); ok(p15.key.i(), 15);
    final Find p14 = t.prev(p15); ok(p14.key.i(), 14);
    final Find p13 = t.prev(p14); ok(p13.key.i(), 13);
    final Find p12 = t.prev(p13); ok(p12.key.i(), 12);
    final Find p11 = t.prev(p12); ok(p11.key.i(), 11);
    final Find p10 = t.prev(p11); ok(p10.key.i(), 10);
    final Find  p9 = t.prev(p10); ok(p9.key.i(),   9);
    final Find  p8 = t.prev(p9);  ok(p8.key.i(),   8);
    final Find  p7 = t.prev(p8);  ok(p7.key.i(),   7);
    final Find  p6 = t.prev(p7);  ok(p6.key.i(),   6);
    final Find  p5 = t.prev(p6);  ok(p5.key.i(),   5);
    final Find  p4 = t.prev(p5);  ok(p4.key.i(),   4);
    final Find  p3 = t.prev(p4);  ok(p3.key.i(),   3);
    final Find  p2 = t.prev(p3);  ok(p2.key.i(),   2);
    final Find  p1 = t.prev(p2);  ok(p1.key.i(),   1);
    final Find  p0 = t.prev(p1);  ok(p0 == null);
   }

  static Tree test_insert_random_32()
   {final Tree t = new Tree(4, 3);
    for (int i = 0; i < random_32.length; i++)
     {t.insert(t.new Key(random_32[i]), t.new Data(i));
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
     {t.insert(t.new Key(random[i]), t.new Data(i));
      t.freeCheck();
      ok(t.count(), i+1);
     }
  //testStop(t);
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
    for (int i = 1; i <= N; ++i) t.insert(t.new Key(i), t.new Data(i));

    final StringBuilder s = new StringBuilder();
    s.append("Start\n"+t);
    for (int i = 1; i <= N; ++i)
     {t.delete(t.new Key(i));
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
    for (int i = 1; i <= N; ++i) t.insert(t.new Key(i), t.new Data(i));

    final StringBuilder s = new StringBuilder();
    s.append("Start\n"+t);
    for (int i = N; i > 0; --i)
     {t.delete(t.new Key(i));
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
     {t.delete(t.new Key(random_32[i]));
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
    for (int i = 1; i <= N; ++i) t.insert(t.new Key(i), t.new Data(i));
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
    final Tree t = new Tree(new Tree.Build().maxLeafSize(2).maxBranchSize(3).numberOfNodes(32));
    for (int i = 1; i <= N; ++i) {t.insert(t.new Key(i), t.new Data(i)); t.freeCheck();}
    for (int i = 1; i <= N; ++i) {t.delete(t.new Key(i));                t.freeCheck();}
    for (int i = 1; i <= N; ++i) {t.insert(t.new Key(i), t.new Data(i)); t.freeCheck();}
  //testStop(t.dump());
    ok(t.dump(), """
                                                                               8                                                                                          16                                                                                     24                                                                            |
                                                                              [5.0]                                                                                      [5.2]                                                                                  [5.4]                                                                          |
                                                                              (20, *, *)                                                                                 (13, *, *)                                                                             (11, *, *){2}                                                                  |
                                                                              (20, 19, 0)                                                                                (13, 19, 0)                                                                            (11, 19, 0)                                                                    |
           2                    4                     6                                              10                    12                    14                                            18                   20                    22                                           26                28                30                  |
          [20.0]               [20.2]                [20.4]                                         [13.0]                [13.2]                [13.4]                                        [11.0]               [11.2]                [11.4]                                       [2.0]             [2.2]             [2.4]                |
          (3, 5, 0)            (21, 5, 0)            (22, 5, 0){23}                                 (16, 5, 2)            (17, 5, 2)            (24, 5, 2){14}                                (7, 5, 4)            (12, 5, 4)            (15, 5, 4){6}                                (1, 5, *)         (8, 5, *)         (4, 5, *){9}         |
          (3, -1, 0)           (21, -1, 0)           (22, -1, 0)                                    (16, -1, 0)           (17, -1, 0)           (24, -1, 0)                                   (7, -1, 0)           (12, -1, 0)           (15, -1, 0)                                  (1, 5, 0)         (8, 5, 0)         (4, 5, 0)            |
1,2                 3,4                   5,6                      7,8                   9,10                  11,12                 13,14                    15,16                 17,18               19,20                 21,22                   23,24                  25,26             27,28             29,30                31,32    |
(3, 20, 0)          (21, 20, 2)           (22, 20, 4)              (23, 20, *)           (16, 13, 0)           (17, 13, 2)           (24, 13, 4)              (14, 13, *)           (7, 11, 0)          (12, 11, 2)           (15, 11, 4)             (6, 11, *)             (1, 2, 0)         (8, 2, 2)         (4, 2, 4)            (9, 2, *)|
""");
   }
*/
  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_tree();
    test_insert();
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
    test_insert();
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
