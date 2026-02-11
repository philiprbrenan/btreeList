//------------------------------------------------------------------------------
// Btree with stucks implemented as slots
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Ttree in a block on the surface of a silicon chip.
import java.util.*;

class Tree extends Test                                                         // Manipulate a tree
 {final int           maxLeafSize;                                              // The maximum number of entries in a leaf
  final int         maxBranchSize;                                              // The maximum number of entries in a branch
  Slots                      root;                                              // The root of the tree
  final int MaximumNumberOfLevels = 99;                                         // Maximum number of levels in tree
  static boolean  createTestTrees = false;                                      // Create trees to assist testing
  static boolean            debug = false;                                      // Debug if enabled
  int leaves = 0, branches = 0;                                                 // Labels for the leaves and branches to assist in debugging

//D1 Construction                                                               // Construct and layout a tree

  Tree(int MaxLeafSize, int MaxBranchSize)                                      // Create the tree
   {final String m  = "The maximum ";
    final String m1 = m + "leaf size must be 2 or more, not: "   +MaxLeafSize;
    final String m2 = m + "branch size must be 3 or more, not: " +MaxBranchSize;
    final String m3 = m + "branch size must be odd, not: "       +MaxBranchSize;

    if (MaxLeafSize      <  2) stop(m1);
    if (MaxBranchSize    <  3) stop(m2);
    if (MaxBranchSize %2 == 0) stop(m3);

    maxLeafSize   = MaxLeafSize;                                                // The maximum number of entries in a leaf
    maxBranchSize = MaxBranchSize;                                              // The maximum number of entries in a branch
   }

  int maxLeafSize()   {return maxLeafSize;}                                     // Maximum size of a leaf
  int maxBranchSize() {return maxBranchSize;}                                   // Maximum size of a branch

//D1 Leaf                                                                       // Use the slots to model a leaf

  class Leaf extends Slots                                                      // Leaf
   {final long[]data = new long[maxLeafSize()];                                 // Data corresponding to each key in the leaf

    Leaf()                                                                      // Create a leaf
     {super(maxLeafSize);                                                       // Slots for leaf
      name = ""+(leaves++);                                                     // Name the leaf to help in debugging
     }

    int splitSize() {return maxLeafSize / 2;}                                   // Size of a split leaf

    Leaf duplicate()                                                            // Duplicate a leaf
     {final Leaf d = new Leaf();
      d.copy((Slots)this);                                                      // Copy slots
      for (int i = 0; i < numberOfRefs; i++) d.data[i] = data[i];               // Copy data associated wuth leaf keys
      return d;
     }

    Leaf splitRight() {return splitRight(duplicate());}                         // Split a left leaf into a new right leaf

    Leaf splitRight(Leaf Right)                                                 // Split a left leaf into an existing right leaf
     {if (!full()) return null;                                                 // Only full leaves can be split
      final int Count = splitSize();
      int s = 0;                                                                // Count slots used
      for (int i = 0; i < numberOfSlots; i++)                                   // Each slot
       {if (usedSlots[i])                                                       // Slot is in use
         {if (s < Count)                                                        // Still in left leaf
           {Right.clearSlotAndRef(i);                                           // Free the entry from the right leaf as it is being used in the left leaf
            s++;                                                                // Number of entries active in left leaf
           }
          else clearSlotAndRef(i);                                              // Clear slot being used in right leaf
         }
       }                                                                        // The new right leaf
      redistribute(); Right.redistribute();
      return Right;
     }

    Leaf splitLeft()                                                            // Split a right leaf into a new left leaf
     {final Leaf l = duplicate();
      l.splitRight(this);
      return l;
     }

    long splittingKey()                                                         // Splitting key from a leaf
     {if (!full()) stop("Leaf not full");                                       // The leaf must be full if we are going to split it
      long k = 0;                                                               // Splitting key
      int    p = 0;                                                             // Position in leaf
      for (int i = 0; i < numberOfSlots; i++)                                   // Scan for splitting keys
       {if (usedSlots(i))                                                       // Used slot
         {if (p == splitSize()-1 || p == splitSize()) k += keys(i);             // Accumulate splitting key as last on left and first on right of split
          ++p;                                                                  // Next position
         }
       }
      return k /= 2;                                                            // Average splitting key
     }

    Branch split()                                                              // Split a leaf into two leaves and a branch
     {final long  sk = splittingKey();
      final Leaf   l = duplicate(), r = l.splitRight();
      final Branch b = new Branch();
      b.insert(sk, l); b.top = r;
      return b;
     }

    Integer insert(long Key, long Data)                                         // Insert a key data pair into a leaf
     {final Integer i = insert(Key);
      if (i != null) data[i] = Data;                                            // Save data in allocated reference
      return i;
     }

    public String toString()                                                    // Print the values in the used slots
     {final StringJoiner k = new StringJoiner(", ");
      final StringJoiner d = new StringJoiner(", ");
      for (int i = 0; i < numberOfSlots; i++)
       {if (usedSlots[i]) k.add(""+keys[slots[i]]);
       }
      for (int i = 0; i < numberOfSlots; i++)
       {if (usedSlots[i]) d.add(""+data[slots[i]]);
       }
      return "keys: "+k+"\n"+"data: "+d+"\n";
     }

    protected String dump()                                                     // Dump a leaf
     {final StringBuilder d = new StringBuilder();
      final int N = numberOfRefs();
      for (int i = 0; i < N; i++) d.append(String.format(" %3d", data[i]));
      return "Leaf     : "+name+"\n"+super.dump() + "data     : "+d+"\n";
     }

    void compactLeft()                                                          // Compact the leaf to the left
     {final int N = numberOfSlots(), R = numberOfRefs();
      final long[]d = new long[R];
      int p = 0;
      for (int i = 0; i < N; i++) if (usedSlots[i]) d[p++] = data[slots[i]];
      super.compactLeft();
      for (int i = 0; i < R; i++) data[i] = d[i];
     }

    void compactRight()                                                         // Compact the leaf to the right
     {final int N = numberOfSlots(), R = numberOfRefs();
      final long[]d = new long[R];
      int p = R-1;
      for (int i = N-1; i >= 0; --i) if (usedSlots[i]) d[p--] = data[slots[i]];
      super.compactRight();
      for (int i = 0; i < R; i++) data[i] = d[i];
     }

    void mergeData(Leaf Left, Leaf Right)                                       // Merge the data from the compacted left and right slots
     {final Leaf l = Left, r = Right;
      for (int i = 0; i < maxLeafSize; ++i)
       {if      (l.usedRefs(i)) data[i] = l.data[i];
        else if (r.usedRefs(i)) data[i] = r.data[i];
       }
     }

    boolean mergeOnRight(Leaf Right)                                            // Merge the specified slots from the right
     {if (countUsed() + Right.countUsed() > maxLeafSize) return false;
      final Leaf l =       duplicate(),
                 r = Right.duplicate();
      l.compactLeft(); r.compactRight();
      mergeCompacted((Slots)l, (Slots)r);
      mergeData(l, r);
      redistribute();
      return true;
     }

    boolean mergeOnLeft(Leaf Left)                                              // Merge the specified slots from the right
     {if (Left.countUsed() + countUsed() > maxLeafSize) return false;
      final Leaf l = Left.duplicate(),
                 r =      duplicate();
      l.compactLeft(); r.compactRight();
      mergeCompacted((Slots)l, (Slots)r);
      mergeData(l, r);
      redistribute();
      return true;
     }

    Tree tree() {return Tree.this;}                                             // Containing tree
   }

//D1 Branch                                                                     // Use the slots to model a branch

  class Branch extends Slots                                                    // Branch
   {final Slots[]data = new Slots[maxBranchSize()];                             // Data corresponding to each key in the branch
    Slots top;                                                                  // Top most element

    Branch()                                                                    // Create a branch
     {super(maxBranchSize);                                                     // Slots for branch
      name = ""+(branches++);                                                   // Name the branch to help in debugging
     }

    int splitSize()       {return maxBranchSize / 2;}                           // Size of a split branch
    Slots data(int Index) {return data[slots(Index)];}                          // Data at the indexed slot
    Slots firstChild()    {return data[slots[locateFirstUsedSlot()]];}          // First child assuming the is one

    Branch duplicate()                                                          // Duplicate a branch
     {final Branch d = new Branch();
      d.copy((Slots)this);                                                      // Copy slots
      for (int i = 0; i < numberOfRefs; i++) d.data[i] = data[i];               // Copy data associated wuth branch keys
      d.top = top;
      return d;
     }

    Split splitRight() {return splitRight(duplicate());}                        // Split a left branch into a new right branch

    class Split                                                                 // The result of splitting a branch
     {final long key;                                                           // The splitting key
      final Branch left, right;                                                 // Left and right sides of split branch
      Split(long Key, Branch Left, Branch Right)
       {key = Key; left = Left; right = Right;
       }
     }

    Split splitRight(Branch Right)                                              // Split a left branch into an existing right branch
     {if (!full()) return null;                                                 // Only full branches can be split
      final int Count = splitSize();
      int s = 0;                                                                // Count slots used
      long sk = 0;                                                              // Splitting key
      for (int i = 0; i < numberOfSlots; i++)                                   // Each slot
       {if (usedSlots[i])                                                       // Slot is in use
         {if (s < Count)                                                        // Still in left branch
           {Right.clearSlotAndRef(i);                                           // Free the entry from the right branch as it is being used in the left branch
            s++;                                                                // Number of entries active in left branch
           }
          else if (s == Count)                                                  // Splitting key
           {sk  = keys(i);
            top = data(i);
                  clearSlotAndRef(i);
            Right.clearSlotAndRef(i);
            s++;                                                                // Number of entries active in left branch
           }
          else clearSlotAndRef(i);                                              // Clear slot being used in right branch
         }
       }                                                                        // The new right branch
      redistribute(); Right.redistribute();
      return new Split(sk, this, Right);
     }

    Split splitLeft() {return duplicate().splitRight(this);}                    // Split a right branch into a new left branch

    long splittingKey()                                                         // Splitting key from a branch
     {if (!full()) stop("Branch not full");                                     // The branch must be full if we are going to split it
      long k = 0;                                                               // Splitting key
      int    p = 0;                                                             // Position in leaf
      for (int i = 0; i < numberOfSlots; i++)                                   // Scan for splitting keys
       {if (usedSlots(i))                                                       // Used slot
         {if (p == splitSize()) k += keys(i);                                   // Splitting key as last on left and first on right of split
          ++p;                                                                  // Next position
         }
       }
      return k;                                                                 // Splitting key
     }

    Branch split()                                                              // Split a branch
     {final long        sk = splittingKey();
      final Branch       l = duplicate();
      final Branch.Split s = l.splitRight();
      final Branch b = new Branch();
      b.insert(sk, s.left); b.top = s.right;
      return b;
     }

    Integer insert(long Key, Slots Data)                                        // Insert a key data pair into a branch
     {final Integer i = insert(Key);
      if (i != null) data[i] = Data;
      return i;
     }

    public String toString()                                                    // Print the values in the used slots
     {final StringJoiner k = new StringJoiner(", ");
      final StringJoiner d = new StringJoiner(", ");
      for (int i = 0; i < numberOfSlots; i++)
       {if (usedSlots[i]) k.add(""+keys[slots[i]]);
       }
      for (int i = 0; i < numberOfSlots; i++)
       {if (usedSlots[i]) d.add(""+data[slots[i]].name);
       }
      return "keys: "+k+"\n"+"data: "+d+"\ntop : "+top.name+"\n";
     }

    protected String dump()                                                     // Dump a branch
     {final StringJoiner d = new StringJoiner(" ");
      final int N = numberOfRefs();
      for (int i = 0; i < N; i++)
       {final Slots s = data[i];
        if (s == null) d.add("  ."); else d.add(s.name);
        }
      return "Branch   : "+name+"\n"+super.dump() + "data     :  "+d+"\ntop      :  "+top.name+"\n";
     }

    void compactLeft()                                                          // Compact the branch to the left
     {final int N = numberOfSlots(), R = numberOfRefs();
      final Slots[]d = new Slots[R];
      int p = 0;
      for (int i = 0; i < N; i++) if (usedSlots[i]) d[p++] = data[slots[i]];
      super.compactLeft();
      for (int i = 0; i < R; i++) data[i] = d[i];
     }

    void compactRight()                                                         // Compact the branch to the right
     {final int N = numberOfSlots(), R = numberOfRefs();
      final Slots[]d = new Slots[R];
      int p = R-1;
      for (int i = N-1; i >= 0; --i) if (usedSlots[i]) d[p--] = data[slots[i]];
      super.compactRight();
      for (int i = 0; i < R; i++) data[i] = d[i];
     }

    void mergeData(long Key, Branch Left, Branch Right)                         // Merge the data from the compacted left and right slots
     {final Branch l = Left, r = Right;
      for (int i = 0; i < maxBranchSize; ++i)                                   // Each slot
       {if      (l.usedRefs(i)) data[i] = l.data[i];                            // Merge from left first
        else if (r.usedRefs(i)) data[i] = r.data[i];                            // Merge from right last
       }
      insert(Key, Left.top);                                                    // Insert left top
      top = Right.top;
     }

    boolean mergeOnRight(long Key, Branch Right)                                // Merge the specified slots from the right
     {if (countUsed() + Right.countUsed() >= maxBranchSize) return false;
      final Branch l =       duplicate(),
                   r = Right.duplicate();
      l.compactLeft(); r.compactRight();
      mergeCompacted((Slots)l, (Slots)r);
      mergeData(Key, l, r);
      redistribute();
      return true;
     }

    boolean mergeOnLeft(long Key, Branch Left)                                  // Merge the specified slots from the right
     {if (Left.countUsed() + countUsed() >= maxBranchSize) return false;
      final Branch l = Left.duplicate();
      final Branch r =      duplicate();
      l.compactLeft(); r.compactRight();
      mergeCompacted((Slots)l, (Slots)r);
      mergeData(Key, l, r);
      redistribute();
      return true;
     }

    boolean canStepLeft(Integer Location)                                       // Whether we can step left from this location. A location of null means top.
     {if (Location == null) return locateLastUsedSlot() != null;                // From top
      return locatePrevUsedSlot(Location-1) != null;                            // From body
     }

    boolean canStepRight(Integer Location)                                      // Whether we can step right from this location. A location of null means top.
     {return Location != null;                                                  // Cannot step right from top otherwose we can
     }

    Integer stepLeft(Integer Loc)                                               // Step left to prior occupied slot assuming that such a step has been checked as possible
     {return Loc != null ? locatePrevUsedSlot(Loc-1) : locateLastUsedSlot();
     }

    Integer stepRight(Integer Location)                                         // Step left to prior occupied slot assuming that such a step has been checked as possible
     {return locateNextUsedSlot(Location+1);
     }

    boolean mergeLeftSibling(Integer Right)                                     // Merge the indicated child with its left sibling if possible.  If the index is null merge into top
     {if (!canStepLeft(Right)) return  false;                                   // Cannot step left
      final Integer left = stepLeft(Right);                                     // Left sibling from right child
      final Slots L = data(left);                                               // Left sibling as slots
      if (L instanceof Leaf)                                                    // Merging leaves
       {final Leaf l = (Leaf)L;                                                 // Left  leaf sibling
        final Leaf r = (Leaf)(Right != null ? data[slots[Right]] : top);        // Right leaf sibling
        final boolean m = r.mergeOnLeft(l);                                     // Merge left sibling into right
        if (m)                                                                  // Merge left sibling into right
         {clearSlotAndRef(left);                                                // Remove left sibling from parent now that ut has been merged with its right sibling
          return true;
         }
       }
      else                                                                      // Children are branches
       {final Branch l = (Branch)L;                                             // Left  branch sibling
        final Branch r = (Branch)(Right != null ? data[slots[Right]] : top);    // Right leaf sibling
        if (r.mergeOnLeft(keys(left), l))                                       // Merge left sibling into right
         {clearSlotAndRef(left);                                                // Remove left sibling from parent now that ut has been merged with its right sibling
          return true;
         }
       }
      return false;
     }

    boolean mergeRightSibling(Integer Left)                                     // Merge the indicated child with its right sibling if possible.  If the index is null merge into top
     {if (!canStepRight(Left)) return false;                                    // Nothing to right of top
      return mergeLeftSibling(stepRight(Left));                                 // Recast as a merge from the left
     }

    Slots child(Integer Index)                                                  // The indexed child. The index must be valid or null - if null, top is returned
     {if (Index == null) return top;                                            // A null index produces top
      if (!usedSlots(Index)) stop("Indexing unused slot:", Index);              // The slot must be valid
      return data[slots(Index)];                                                // The indicated child
     }

    Tree tree() {return Tree.this;}                                             // Containing tree

    private Slots stepDown(long Key)                                            // Step down from this branch
     {return child(locateFirstGe(Key));
     }
   }

//D1 Low Level                                                                  // Low level operations

  void mergeRoot()                                                              // Collapse the root if possible
   {if (root == null) return;                                                   // Empty tree
    if (root instanceof Leaf)                                                   // Leaf root
     {final Leaf l = (Leaf)root;
      if (l.empty()) root = null;                                               // Free leaf if it is empty
      return;
     }
    final Branch b = (Branch)root;                                              // Branch root
    if (b.countUsed() == 0) {root = b.top; return;}                             // Root body is empty so collapse to top
    if (b.countUsed() >  1) return;                                             // Root body too big too collapse

    if (b.top instanceof Leaf)                                                  // Leaves for children
     {final Leaf l = (Leaf)b.firstChild();
      final Leaf r = (Leaf)b.top;
      if (l.mergeOnRight(r)) root = l;                                          // Update root if the leaves were successfully merged
      return;
     }
    final Branch l = (Branch)b.firstChild();                                    // Root has branches for children
    final Branch r = (Branch)b.top;
    final boolean m = r.mergeOnLeft(b.firstKey(), l);
    if (m) root = r;                                                            // Update root if the branches were successfully merged
   }

//D1 High Level                                                                 // High level operations: insert, find, delete

  class Find                                                                    // Find results
   {Branch  branch;                                                             // Last branch
    Leaf    leaf;                                                               // Leaf that should contain the key
    Integer parentIndex;                                                        // Slot used in parent for leaf
    Integer childIndex;                                                         // Slot used for key in child if present
    long    key;                                                                // Search key
    Slots.Locate locate;                                                        // Location details for key
    final Stack<Branch> path;                                                   // The path taken to perform the find

    Find(long Key, Branch Branch, Leaf Leaf,
      Integer ParentIndex, Stack<Branch> Path)
     {key         = Key;
      branch      = Branch;
      leaf        = Leaf;
      parentIndex = ParentIndex;
      locate      = Leaf.new Locate(Key);
      childIndex  = Leaf.locate(Key);
      path        = Path;
     }

    public String toString()
     {final StringBuilder s = new StringBuilder();
      s.append("Find Key : "+key+"\n");
      if (branch       != null) s.append(branch.dump());
      if (leaf         != null) s.append(leaf  .dump());
      if (parentIndex  != null) s.append("ParentIndex : "+parentIndex  +"\n");
      if (childIndex   != null) s.append("ChildIndex  : "+childIndex   +"\n");
      if (locate       != null) s.append("Locate      : "+locate   +"\n");
      if (path         != null)                                                 // Path taken by find
       {final StringJoiner j = new StringJoiner(", ");
        for(Branch p : path) j.add(p.name);
        s.append("Path        : "+j+"\n");
       }
      return ""+s;
     }
   }

  Find find(long Key)
   {if (root == null) return null;                                              // Empty tree
    if (root instanceof Leaf)                                                   // Leaf root
     {final Leaf l = (Leaf)root;
      return new Find(Key, null, l, null, null);
     }

    final Stack<Branch> path = new Stack<>();                                   // The path taken to perform the find
    Branch p = (Branch)root;                                                    // Start at root

    for (int i = 0; i < MaximumNumberOfLevels; i++)                             // Step down from branch splitting as we go
     {final Integer P = p.locateFirstGe(Key);
      final Slots q = p.child(P);
      if (q instanceof Leaf)                                                    // Step down to a leaf
       {final Leaf l = (Leaf)q;
        return new Find(Key, p, l, P, path);
       }
      p = (Branch)q;                                                            // Step down into non full branch
      path.push(p);
     }
    stop("Find fell off the end of tree after this many searches:", MaximumNumberOfLevels);
    return null;
   }

  void insert(long Key, long Data)                                              // Insert a key, data pair or update key data pair in the tree
   {if (root == null)                                                           // Empty tree
     {final Leaf l = new Leaf(); root = l;                                      // Root is a leaf
      l.insert(Key, Data);                                                      // Insert into leaf root
      return;
     }

    final Find F = find(Key);                                                   // See if key is already present
    if (F.childIndex != null)                                                   // Key already present so update data associated with the key
     {final Leaf l = F.leaf;                                                    // Child leaf
      l.data[l.slots[F.childIndex]] = Data;                                     // Update data                                                                            //
      return;
     }
    else if (!F.leaf.full())                                                    // Leaf not full so insert directly
     {final Leaf l = F.leaf;                                                    // Child leaf
      l.insert(Key, Data);                                                      // Insert key
      return;
     }
    else if (F.branch != null && !F.branch.full())                              // Leaf is full, parent branch is not full so we can split leaf
     {final Branch b = F.branch;                                                // Parent branch
      final Leaf   r = F.leaf;
      final long  sk = r.splittingKey();
      final Leaf   l = r.splitLeft();
      b.insert(sk, l);                                                          // Insert new left leaf into leaf
      if (Key <= sk)                                                            // Insert new key, data pair into left leaf
       {l.insert(Key, Data);                                                    // Insert key, data
       }
      else                                                                      // Insert new key, data pair into right leaf
       {r.insert(Key, Data);                                                    // Insert key, data
       }
      final Integer K = b.locateFirstGe(Key);                                   // Position of leaf in parent
      b.mergeLeftSibling (K);                                                   // Merge left leaf into prior leaf if possible
      b.mergeRightSibling(K);                                                   // Merge left leaf into prior leaf if possible
      if (b.canStepLeft  (K)) b.mergeLeftSibling (b.stepLeft (K));              // Merge right leaf into next leaf if possible
      if (b.canStepRight (K)) b.mergeRightSibling(b.stepRight(K));              // Merge right leaf into next leaf if possible
      return;
     }

    if (root instanceof Leaf)                                                   // Leaf root
     {final Leaf l = (Leaf)root;
      if (!l.full())                                                            // Still space in leaf root
       {l.insert(Key, Data);                                                    // Insert into leaf root
        return;
       }
      else                                                                      // Leaf root is full
       {root = l.split();                                                       // Split full leaf root
       }
     }

    Branch p = (Branch)root;                                                    // Start at root
    if (p.full()) {root = p.split(); p = (Branch)root;}                         // Split full root branch

    for (int i = 0; i < MaximumNumberOfLevels; i++)                             // Step down from branch splitting as we go
     {final Slots q = p.stepDown(Key);                                          // Step down
      if (q instanceof Leaf)                                                    // Step down to a leaf
       {final Leaf r = (Leaf)q;
        if (r.full())                                                           // Split the leaf if it is full
         {final long sk = r.splittingKey();
          final Leaf l  = r.splitLeft();
          p.insert(sk, l);                                                      // The parent is known not to be full so the insert will work.  We are inserting left so this works even if we are splitting top
          if (Key <= sk) l.insert(Key, Data); else r.insert(Key, Data);         // Insert into left or right leaf which will now have space
         }
        else r.insert(Key, Data);                                               // Leaf has sufficient space

        mergeAlongPath(Key);                                                    // Merge along the path taken by the key to compress the tree
        return;
       }
      final Branch r = (Branch)q;
      if (r.full())                                                             // Split the leaf if it is full
       {final long      sk = r.splittingKey();
        final Branch.Split s = r.splitLeft();
        p.insert(sk, s.left);                                                   // The parent is known not to be full so the insert will work.  We are inserting left so this works even if we are splitting top
        if (Key <= sk) p = s.left; else p = s.right;                            // Traverse left or right
       }
      else p = r;                                                               // Step down into non full branch
     }
    stop("Insert fell off the end of tree after this many searches:", MaximumNumberOfLevels);
   }

  void delete(long Key)                                                         // Delete a key from the tree
   {if (root == null) return;                                                   // Empty tree
    final Find f = find(Key);
    if (!f.locate.exact()) return;                                              // Key not found so nothing to delete
    f.leaf.clearSlotAndRef(f.locate.at);                                        // Delete key and data
   }

  void mergeAlongPath(long Key)                                                 // Merge along the path from the specified key to the root
   {final Find f = find(Key);                                                   // Locate the leaf that should contain the key
    if (f == null) return;                                                      // Empty tree
    final int N = f.path.size();
    for (int i = N-1; i >= 0; --i)                                              // Go up the tree merging as we go
     {final Branch b = f.path.elementAt(i);                                     // Parent  branch some of whose siblings might be mergable
      final Integer l = b.locateFirstGe(Key);                                   // Position of key
      b.mergeRightSibling(l);                                                   // Merge right sibling of keyed child
      b.mergeLeftSibling(l);                                                    // Merge left sibling of keyed child

      final Integer k = b.locateFirstGe(Key);                                   // Look further left
      if (k != null)
       {final Integer K = b.locatePrevUsedSlot(k-1);
        if (K != null)
         {b.mergeLeftSibling(K);
         }
       }
      else
       {final Integer K = b.locateLastUsedSlot();
        if (K != null)
         {b.mergeLeftSibling(K);
         }
       }
      final Integer m = b.locateFirstGe(Key);                                   // Look further right
      if (m != null)
       {final Integer M = b.locateNextUsedSlot(m+1);
        if (M != null)
         {b.mergeRightSibling(M);
         }
       }
     }
    mergeRoot();                                                                // Merge the root if possible
   }

//D1 Print                                                                      // Print the tree horizontally

  final int linesToPrintABranch =  4;                                           // The number of lines required to print a branch
  final int maxPrintLevels      =  3;                                           // The maximum number of levels to print `- this avoids endless print loops when something goes wrong

  void printLeaf(Leaf Leaf, Stack<StringBuilder>P, int level, boolean Details)  // Print leaf horizontally
   {padStrings(P, level);

    final StringJoiner s = new StringJoiner(",");
    for (int i = 0; i < Leaf.numberOfSlots; i++)
     {if (Leaf.usedSlots(i)) s.add(""+Leaf.keys(i));
     }
    final int L = level * linesToPrintABranch;                                  // Start line at which to print branch
    P.elementAt(L+0).append(s);
    if (Details) P.elementAt(L+1).append("("+Leaf.name+")");
    padStrings(P, level);
   }

  void printBranch(Branch Branch, Stack<StringBuilder>P, int level, boolean Details) // Print branch horizontally
   {if (level > maxPrintLevels) return;
    padStrings(P, level);
    final int L = level * linesToPrintABranch;                                  // Start line at which to print branch
    final int K = Branch.countUsed();                                           // Size of branch

    if (K > 0)                                                                  // Branch has key, next pairs
     {for  (int i = 0; i < Branch.numberOfSlots; i++)
       {if (!Branch.usedSlots(i)) continue;
        final Slots s = Branch.data[Branch.slots(i)];

        if (s instanceof Leaf)
         {printLeaf  ((Leaf)s,   P, level+1, Details);
         }
        else if (s instanceof Branch)
         {printBranch((Branch)s, P, level+1, Details);
         }
        //final int key  = stuckKeys.memoryGet(BtreeIndex, i);
        //final int data = stuckData.memoryGet(BtreeIndex, i);

        P.elementAt(L+0).append(" "+Branch.keys(i));                            // Key
        if (Details)
         {P.elementAt(L+1).append("["+Branch.name+(i > 0 ?  "."+i : "")+"]");     // Branch, key, next pair
          P.elementAt(L+2).append("("+s.name+")");                                // Link to next level
         }
       }
     }
    else                                                                        // Branch is empty so print just the index of the branch
     {//P.elementAt(L+0).append(""+BtreeIndex+"Empty");
     }
    if (Details) P.elementAt(L+2).append("{"+Branch.top.name+"}");                           // Top of branch
    //final int top = stuckData.memoryGet(BtreeIndex, K);                       // Top next will always be present
    //P.elementAt(L+3).append(top);                                             // Append top next

    if      (Branch.top instanceof Leaf)                                        // Print leaf
     {printLeaf  (  (Leaf)Branch.top, P, level+1, Details);
     }
    else if (Branch.top instanceof Branch)                                      // Print leaf
     {printBranch((Branch)Branch.top, P, level+1, Details);
     }

    padStrings(P, level);
   }

 String printBoxed()                                                            // Print a tree in a box
  {final String  s = toString();
   final int     n = longestLine(s)-1;
   final String[]L = s.split("\n");
   final StringJoiner t = new StringJoiner("\n",  "", "\n");
   t.add("+"+("-".repeat(n))+"+");
   for(String l : L) t.add("| "+l);
   t.add("+"+("-".repeat(n))+"+");
   return ""+t;
  }

  void padStrings(Stack<StringBuilder> S, int level)                            // Pad the strings at each level of the tree so we have a vertical face to continue with - a bit like Marc Brunel's tunneling shield
   {final int N = level * linesToPrintABranch + maxLeafSize;                    // Number of lines we might want
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
     {final String l = s.toString();
      if (l.isBlank()) continue;
      t.append(l+"|\n");
     }
    return t.toString();
   }

  public String toString() {return print(false);}                               // Print the tree without details
  public String dump()     {return debug ? print(true) : "";}                               // Print the tree without details

  String print(boolean Details)                                                 // Print the tree with and without details
   {final Stack<StringBuilder> P = new Stack<>();
    if (root instanceof Leaf) printLeaf  ((Leaf)  root, P, 0, Details);
    else                      printBranch((Branch)root, P, 0, Details);
    return printCollapsed(P);
   }

//D1 Tests                                                                      // Test the btree

  final static int[]random_32 = {12, 3, 27, 1, 23, 20, 8, 18, 2, 31, 25, 16, 13, 32, 11, 21, 5, 24, 4, 10, 26, 30, 9, 6, 29, 17, 28, 15, 14, 19, 7, 22};

  static void test_compactLeafLeft()
   {final Leaf l = new Tree(8, 7).new Leaf();
    l.insert(13, 23);
    l.insert(12, 22);
    l.insert(14, 24);
    l.insert(11, 21);

    ok(l.dump(), """
Leaf     : 0
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   0   0   3   1   0   2   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X   X   X   .   .   .   .   .   .
usedRefs :    X   X   X   X   .   .   .   .
keys     :   13  12  14  11   0   0   0   0
data     :   23  22  24  21   0   0   0   0
""");
    l.compactLeft();
    ok(l.dump(), """
Leaf     : 0
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   1   2   3   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    X   X   X   X   .   .   .   .   .   .   .   .   .   .   .   .
usedRefs :    X   X   X   X   .   .   .   .
keys     :   11  12  13  14   0   0   0   0
data     :   21  22  23  24   0   0   0   0
""");
   }

  static void test_compactLeafRight()
   {final Leaf l = new Tree(8, 7).new Leaf();
    l.insert(13, 23);
    l.insert(12, 22);
    l.insert(14, 24);
    l.insert(11, 21);
    ok(l.dump(), """
Leaf     : 0
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   0   0   3   1   0   2   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X   X   X   .   .   .   .   .   .
usedRefs :    X   X   X   X   .   .   .   .
keys     :   13  12  14  11   0   0   0   0
data     :   23  22  24  21   0   0   0   0
""");
    l.compactRight();
    ok(l.dump(), """
Leaf     : 0
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   4   5   6   7   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   X   X   X   X   .   .   .   .   .   .   .   .
usedRefs :    .   .   .   .   X   X   X   X
keys     :    0   0   0   0  11  12  13  14
data     :    0   0   0   0  21  22  23  24
""");
   }

  static void test_compactBranchLeft()
   {final Branch b = new Tree(8, 7).new Branch();
    b.insert(12, new Slots(" 12"));
    b.insert(11, new Slots(" 11"));
    b.insert(13, new Slots(" 13"));
    b.top = new Slots("  4");
    ok(b.dump(), """
Branch   : 0
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slots    :    0   0   0   0   0   0   1   0   2   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X   X   .   .   .   .   .
usedRefs :    X   X   X   .   .   .   .
keys     :   12  11  13   0   0   0   0
data     :   12  11  13   .   .   .   .
top      :    4
""");

    b.compactLeft();

    ok(b.dump(), """
Branch   : 0
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slots    :    0   1   2   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    X   X   X   .   .   .   .   .   .   .   .   .   .   .
usedRefs :    X   X   X   .   .   .   .
keys     :   11  12  13   0   0   0   0
data     :   11  12  13   .   .   .   .
top      :    4
""");
   }

  static void test_compactBranchRight()
   {final Branch b = new Tree(8, 7).new Branch();
    b.insert(12, new Slots(" 12"));
    b.insert(11, new Slots(" 11"));
    b.insert(13, new Slots(" 13"));
    b.top = new Slots("  4");
    ok(b.dump(), """
Branch   : 0
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slots    :    0   0   0   0   0   0   1   0   2   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X   X   .   .   .   .   .
usedRefs :    X   X   X   .   .   .   .
keys     :   12  11  13   0   0   0   0
data     :   12  11  13   .   .   .   .
top      :    4
""");

    b.compactRight();
    ok(b.dump(), """
Branch   : 0
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slots    :    0   0   0   0   4   5   6   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   X   X   X   .   .   .   .   .   .   .
usedRefs :    .   .   .   .   X   X   X
keys     :    0   0   0   0  11  12  13
data     :    .   .   .   .  11  12  13
top      :    4
""");
   }

  static Leaf test_leaf()
   {final Leaf l = new Tree(8, 7).new Leaf();
    final long   []d = new long[]{13, 16, 15, 18, 17, 14, 12, 11};
    for (int i = 0; i < d.length; i++) l.insert(d[i], d[i]);
    return l;
   }

  static void test_splitLeftLeafIntoRight()
   {final Leaf l = test_leaf();
    ok(l.dump(), """
Leaf     : 0
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    7   6   0   0   5   0   2   0   1   0   4   0   3   0   0   0
usedSlots:    X   X   X   .   X   .   X   .   X   .   X   .   X   .   .   .
usedRefs :    X   X   X   X   X   X   X   X
keys     :   13  16  15  18  17  14  12  11
data     :   13  16  15  18  17  14  12  11
""");
    final Leaf r = l.splitRight();
    ok(l.dump(), """
Leaf     : 0
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   6   0   0   0   0   0   0   0   5   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   X   .   .
usedRefs :    X   .   .   .   .   X   X   X
keys     :   13  16  15  18  17  14  12  11
data     :   13  16  15  18  17  14  12  11
""");
    ok(r.dump(), """
Leaf     : 1
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   2   0   0   0   1   0   0   0   4   0   0   0   3   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   X   .   .
usedRefs :    .   X   X   X   X   .   .   .
keys     :   13  16  15  18  17  14  12  11
data     :   13  16  15  18  17  14  12  11
""");
    ok(l, """
keys: 11, 12, 13, 14
data: 11, 12, 13, 14
""");
    ok(r, """
keys: 15, 16, 17, 18
data: 15, 16, 17, 18
""");
   }

  static void test_splitRightLeafIntoLeft()
   {final Leaf r = test_leaf();
    final Leaf l = r.splitLeft();
    ok(l, """
keys: 11, 12, 13, 14
data: 11, 12, 13, 14
""");
    ok(r, """
keys: 15, 16, 17, 18
data: 15, 16, 17, 18
""");
   }

  static Branch test_branch()
   {final Branch b = new Tree(8, 7).new Branch();

    final long[]k = new long[]{13, 16, 15, 17, 14, 12, 11};
    final String[]d = new String[]{"  3", "  6", "  5", "  7", "  4", "  2", "  1"};
    for (int i = 0; i < d.length; i++) b.insert(k[i], new Slots(d[i]));
    b.top = new Slots("  8");
    return b;
   }

  static void test_splitLeftBranchIntoRight()
   {final Branch       b = test_branch();
    final Branch.Split s = b.splitRight();
    ok(s.left, """
keys: 11, 12, 13
data:   1,   2,   3
top :   4
""");
    ok(s.right, """
keys: 15, 16, 17
data:   5,   6,   7
top :   8
""");
    ok(s.key, 14);
   }

  static void test_splitRightBranchIntoLeft()
   {final Branch       r = test_branch();
    final Branch.Split s = r.splitLeft();
    ok(s.left, """
keys: 11, 12, 13
data:   1,   2,   3
top :   4
""");
    ok(s.right, """
keys: 15, 16, 17
data:   5,   6,   7
top :   8
""");
    ok(s.key, 14);
   }

  static Leaf test_leaf1()
   {final Leaf    l = new Tree(8,7).new Leaf();
    final long[]d = new long[]{13, 14, 12, 11};
    for (int i = 0; i < d.length; i++) l.insert(d[i], d[i]);
    return l;
   }

  static Leaf test_leaf2()
   {final Leaf    l = new Tree(8,7).new Leaf();
    final long[]d = new long[]{16, 15, 18, 17};
    for (int i = 0; i < d.length; i++) l.insert(d[i], d[i]);
    return l;
   }

  static void test_mergeLeafLeft()
   {final Leaf l = test_leaf1();
    final Leaf r = test_leaf2();
    l.mergeOnRight(r);
    ok(l.dump(), """
Leaf     : 0
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
    r.mergeOnLeft(l);
    ok(r.dump(), """
Leaf     : 0
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   1   0   2   0   3   0   4   0   5   0   6   0   7   0
usedSlots:    X   .   X   .   X   .   X   .   X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X   X   X   X   X
keys     :   11  12  13  14  15  16  17  18
data     :   11  12  13  14  15  16  17  18
""");
   }

  static Branch test_branch1()
   {final Branch b = new Tree(8, 7).new Branch();
    final long[]k = new long[]{13, 12, 11};
    final String[]d = new String[]{"  3", "  2", "  1"};
    for (int i = 0; i < k.length; i++) b.insert(k[i], new Slots(d[i]));
    b.top = new Slots("  4");
    return b;
   }

  static Branch test_branch2()
   {final Branch b = new Tree(8, 7).new Branch();
    final long[]k = new long[]{16, 15, 17};
    final String[]d = new String[]{"  6", "  5", "  7"};
    for (int i = 0; i < k.length; i++) b.insert(k[i], new Slots(d[i]));
    b.top = new Slots("  8");
    return b;
   }

  static void test_mergeBranchLeft()
   {final Branch l = test_branch1();
    final Branch r = test_branch2();
    l.mergeOnRight(14, r);
    ok(l.dump(), """
Branch   : 0
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slots    :    0   0   1   0   2   0   3   0   4   0   5   0   6   0
usedSlots:    X   .   X   .   X   .   X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X   X   X   X
keys     :   11  12  13  14  15  16  17
data     :    1   2   3   4   5   6   7
top      :    8
""");
   }

  static void test_mergeBranchRight()
   {final Branch l = test_branch1();
    final Branch r = test_branch2();
    r.mergeOnLeft(14, l);
    ok(r.dump(), """
Branch   : 0
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slots    :    0   0   1   0   2   0   3   0   4   0   5   0   6   0
usedSlots:    X   .   X   .   X   .   X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X   X   X   X
keys     :   11  12  13  14  15  16  17
data     :    1   2   3   4   5   6   7
top      :    8
""");
   }

  static void test_locateFirstGe()
   {final Slots b = new Slots(16);
    b.insert(1);
    b.insert(5);
    b.insert(3);
    b.redistribute();
    //stop(b.dump());
    ok(b.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   0   0   0   2   0   0   0   0   1   0   0   0
usedSlots:    .   .   X   .   .   .   .   X   .   .   .   .   X   .   .   .
usedRefs :    X   X   X   .   .   .   .   .   .   .   .   .   .   .   .   .
keys     :  1.0 5.0 3.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0
""");

    ok(b.locateFirstGe(0),  2);
    ok(b.locateFirstGe(1),  2);
    ok(b.locateFirstGe(2),  7);
    ok(b.locateFirstGe(3),  7);
    ok(b.locateFirstGe(4), 12);
    ok(b.locateFirstGe(5), 12);
    ok(b.locateFirstGe(6), null);
   }

  static void test_splitLeaf()
   {final Branch b = test_leaf().split();
    final Tree   t = b.tree();
    t.root = b;
    ok(t, """
            14           |
11,12,13,14   15,16,17,18|
""");
   }

  static void test_splitBranch()
   {final Branch b = test_branch().split();
    final Tree   t = b.tree();
    t.root = b;
    ok(t, """
          14         |
 11 12 13    15 16 17|
""");
   }

  static void test_insert()
   {final Tree t = new Tree(4, 3);

    t.insert(11, 21);
    ok(t, """
11|
""");
    t.insert(13, 23);
    ok(t, """
11,13|
""");
    t.insert(12, 22);
    ok(t, """
11,12,13|
""");
    t.insert(14, 24);
    ok(t, """
11,12,13,14|
""");
    t.insert(15, 25);
    ok(t, """
      12        |
11,12   13,14,15|
""");
    t.insert(16, 26);
    ok(t, """
      12           |
11,12   13,14,15,16|
""");
    t.insert(17, 27);
    ok(t, """
            14        |
11,12,13,14   15,16,17|
""");
    t.insert(18, 28);
    ok(t, """
            14           |
11,12,13,14   15,16,17,18|
""");
    t.insert(19, 29);
    ok(t, """
            14      16        |
11,12,13,14   15,16   17,18,19|
""");
    t.insert(20, 30);
    ok(t, """
            14      16           |
11,12,13,14   15,16   17,18,19,20|
""");
    t.insert(21, 31);
    ok(t, """
            14            18        |
11,12,13,14   15,16,17,18   19,20,21|
""");
    t.insert(22, 32);
    ok(t, """
            14            18           |
11,12,13,14   15,16,17,18   19,20,21,22|
""");
    t.insert(23, 33);
    ok(t, """
            14            18      20        |
11,12,13,14   15,16,17,18   19,20   21,22,23|
""");
    t.insert(24, 34);
    ok(t, """
            14            18      20           |
11,12,13,14   15,16,17,18   19,20   21,22,23,24|
""");
    t.insert(25, 35);
    ok(t, """
            14            18            22        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25|
""");
    t.insert(26, 36);
    ok(t, """
            14            18            22           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26|
""");
    t.insert(27, 37);
    ok(t, """
                          18                              |
            14                          22      24        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24   25,26,27|
""");
    t.insert(28, 38);
    ok(t, """
                          18                                 |
            14                          22      24           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24   25,26,27,28|
""");
    t.insert(29, 39);
    ok(t, """
                          18                                    |
            14                          22            26        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29|
""");
    t.insert(30, 40);
    ok(t, """
                          18                                       |
            14                          22            26           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30|
""");
    t.insert(31, 41);
    ok(t, """
                          18                                            |
            14                          22            26      28        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28   29,30,31|
""");
    t.insert(32, 42);
    ok(t, """
                          18                                               |
            14                          22            26      28           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28   29,30,31,32|
""");
    t.insert(33, 43);
    ok(t, """
                          18                          26                      |
            14                          22                          30        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33|
""");
    t.insert(34, 44);
    ok(t, """
                          18                          26                         |
            14                          22                          30           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34|
""");
    t.insert(35, 45);
    ok(t, """
                          18                          26                              |
            14                          22                          30      32        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32   33,34,35|
""");
    t.insert(36, 46);
    ok(t, """
                          18                          26                                 |
            14                          22                          30      32           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32   33,34,35,36|
""");
    t.insert(37, 47);
    ok(t, """
                          18                          26                                    |
            14                          22                          30            34        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36,37|
""");
    t.insert(38, 48);
    ok(t, """
                          18                          26                                       |
            14                          22                          30            34           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36,37,38|
""");
    t.insert(39, 49);
    ok(t, """
                          18                          26                                            |
            14                          22                          30            34      36        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36   37,38,39|
""");
    t.insert(40, 50);
    ok(t, """
                          18                          26                                               |
            14                          22                          30            34      36           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36   37,38,39,40|
""");
    t.insert(41, 51);
    ok(t, """
                          18                          26                          34                      |
            14                          22                          30                          38        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36,37,38   39,40,41|
""");
    t.insert(42, 52);
    ok(t, """
                          18                          26                          34                         |
            14                          22                          30                          38           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36,37,38   39,40,41,42|
""");

    ok(t.find(10), """
Find Key : 10
Branch   : 6
positions:    0   1   2   3   4   5
slots    :    0   0   0   0   0   0
usedSlots:    .   .   X   .   .   .
usedRefs :    X   .   .
keys     :   14  18  22
data     :  3 7 11
top      :  7
Leaf     : 3
positions:    0   1   2   3   4   5   6   7
slots    :    0   0   1   0   2   0   3   0
usedSlots:    X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X
keys     :   11  12  13  14
data     :   21  22  23  24
ParentIndex : 2
Locate      :  0  below all
Path        : 6
""");

    ok(t.find(23), """
Find Key : 23
Branch   : 9
positions:    0   1   2   3   4   5
slots    :    0   0   2   0   0   0
usedSlots:    .   .   X   .   .   .
usedRefs :    .   .   X
keys     :   28  26  22
data     :  18 15 11
top      :  15
Leaf     : 15
positions:    0   1   2   3   4   5   6   7
slots    :    0   0   1   0   2   0   3   0
usedSlots:    X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X
keys     :   23  24  25  26
data     :   33  34  35  36
ChildIndex  : 0
Locate      : 0 exact
Path        : 9
""");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_compactLeafLeft();
    test_compactLeafRight();
    test_compactBranchLeft();
    test_compactBranchRight();
    test_splitLeftLeafIntoRight();
    test_splitRightLeafIntoLeft();
    test_splitLeftBranchIntoRight();
    test_splitRightBranchIntoLeft();
    test_mergeLeafLeft();
    test_mergeLeafRight();
    test_mergeBranchLeft();
    test_mergeBranchRight();
    test_splitLeaf();
    test_splitBranch();
   }

  static void newTests()                                                        // Tests being worked on
   {oldTests();
    test_insert();
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
