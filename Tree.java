//------------------------------------------------------------------------------
// Btree with stucks implemented as distributed slots
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.util.*;

class Tree extends Test                                                         // Manipulate a tree
 {final int           maxLeafSize;                                              // The maximum number of entries in a leaf
  final int         maxBranchSize;                                              // The maximum number of entries in a branch
  Slots                      root;                                              // The root of the tree
  final int MaximumNumberOfLevels = 99;                                         // Maximum number of levels in tree
  static boolean            debug = false;                                      // Debug if enabled
  int leaves = 0, branches = 0;                                                 // Labels for the leaves and branches to assist in debugging

//D1 Construction                                                               // Construct and layout a tree

  Tree(int MaxLeafSize, int MaxBranchSize)                                      // Create the tree
   {final String m  = "The maximum ";
    final String m1 = m + "leaf size must be 2 or more, not: "   +MaxLeafSize;
    final String m2 = m + "branch size must be 3 or more, not: " +MaxBranchSize;
    final String m3 = m + "branch size must be odd, not: "       +MaxBranchSize;

    final boolean b1 = MaxLeafSize      <  2,
                  b2 = MaxBranchSize    <  3,
                  b3 = MaxBranchSize %2 == 0;

    if (b1 && !b2 && !b3) stop(m1); else if (b1) say(m1);
    if (b2        && !b3) stop(m2); else if (b2) say(m2);
    if (b3              ) stop(m3);

    maxLeafSize   = MaxLeafSize;                                                // The maximum number of entries in a leaf
    maxBranchSize = MaxBranchSize;                                              // The maximum number of entries in a branch
   }

  int maxLeafSize  () {return maxLeafSize;}                                     // Maximum size of a leaf
  int maxBranchSize() {return maxBranchSize;}                                   // Maximum size of a branch
  int           mnl() {return MaximumNumberOfLevels;}                           // Maximum number of levels

  static Slots.Key Key(long Value) {return new Slots.Key(Value);}

//D1 Leaf                                                                       // Use the slots to model a leaf

  class Leaf extends Slots                                                      // Leaf
   {Branch up; Integer upIndex;                                                 // The branch above
    final Data[]data = new Data[maxLeafSize()];                                 // Data corresponding to each key in the leaf

    Leaf()                                                                      // Create a leaf
     {super(maxLeafSize);                                                       // Slots for leaf
      name = leaves++;                                                          // Name the leaf to help in debugging
     }

    public record Data(long value) {}                                           // A data value in a leaf

    int splitSize()              {return maxLeafSize / 2;}                      // Size of a split leaf
    Data data(int I)             {return data[I];}                              // Get value of data field at index
    void data(int I, Data Value) {       data[I] = Value;}                      // Set value of data field at index

    Leaf duplicate()                                                            // Duplicate a leaf
     {final Leaf d = new Leaf();
      d.copy(this);                                                             // Copy slots
      final int R = numberOfRefs();
      for (int i = 0; i < R; i++) d.data(i, data(i));                           // Copy data associated with leaf keys
      return d;
     }

    Leaf splitRight() {return splitRight(duplicate());}                         // Split a left leaf into a new right leaf

    Leaf splitRight(Leaf Right)                                                 // Split a left leaf into an existing right leaf
     {if (!full()) return null;                                                 // Only full leaves can be split
      final int Count = splitSize();
      int s = 0;                                                                // Count slots used
      final int S = numberOfSlots();
      for (int i = 0; i < S; i++)                                               // Each slot
       {if (usedSlots(i))                                                       // Slot is in use
         {if (s++ < Count) Right.clearSlotAndRef(i);                            // Still in left leaf
          else                   clearSlotAndRef(i);                            // Clear slot being used in right leaf
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
      int  p = 0;                                                               // Position in leaf
      final int S = numberOfSlots();
      for (int i = 0; i < S; i++)                                               // Scan for splitting keys
       {if (usedSlots(i))                                                       // Used slot
         {if (p == splitSize()-1 || p == splitSize()) k += keys(i).value();     // Accumulate splitting key as last on left and first on right of split
          ++p;                                                                  // Next position
         }
       }
      return k /= 2;                                                            // Average splitting key
     }

    Branch split()                                                              // Split a leaf into two leaves and a branch
     {final long  sk = splittingKey();
      final Leaf   l = duplicate(), r = l.splitRight();
      final Branch b = new Branch();
      b.insert(new Key(sk), l); b.top = r;
      return b;
     }

    Integer insert(Key Key, Data Data)                                          // Insert a key data pair into a leaf
     {final Integer i = insert(Key);
      if (i != null) data(i, Data);                                             // Save data in allocated reference
      return i;
     }

    void compactLeft()                                                          // Compact the leaf to the left
     {final int   N = numberOfSlots(), R = numberOfRefs();
      final Data[]d = new Data[R];
      int p = 0;
      for (int i = 0; i < N; i++) if (usedSlots(i)) d[p++] = data(slots(i));
      super.compactLeft();

      for (int i = 0; i < R; i++) data(i, d[i]);
     }

    void compactRight()                                                         // Compact the leaf to the right
     {final int   N = numberOfSlots(), R = numberOfRefs();
      final Data[]d = new Data[R];
      int p = R-1;
      for (int i = N-1; i >= 0; --i) if (usedSlots(i)) d[p--] = data(slots(i));
      super.compactRight();
      for (int i = 0; i < R; i++) data(i, d[i]);
     }

    void mergeData(Leaf Left, Leaf Right)                                       // Merge the data from the compacted left and right slots
     {final Leaf l = Left, r = Right;
      for (int i = 0; i < maxLeafSize; ++i)
       {if      (l.usedRefs(i)) data(i, l.data(i));
        else if (r.usedRefs(i)) data(i, r.data(i));
       }
     }

    boolean mergeFromRight(Leaf Right)                                          // Merge the specified slots from the right
     {if (countUsed() + Right.countUsed() > maxLeafSize) return false;
      final Leaf l =       duplicate();
      final Leaf r = Right.duplicate();
      l.compactLeft(); r.compactRight();
      mergeCompacted(l, r);
      mergeData(l, r);
      redistribute();
      return true;
     }

    boolean mergeFromLeft(Leaf Left)                                            // Merge the specified slots from the left
     {if (Left.countUsed() + countUsed() > maxLeafSize) return false;
      final Leaf l = Left.duplicate(),
                 r =      duplicate();
      l.compactLeft(); r.compactRight();
      mergeCompacted(l, r);
      mergeData(l, r);
      redistribute();
      return true;
     }

    public String toString()                                                    // Print the values in the used slots
     {final StringJoiner k = new StringJoiner(", ");
      final StringJoiner d = new StringJoiner(", ");
      final int S = numberOfSlots();
      for (int i = 0; i < S; i++)
       {if (usedSlots(i)) {k.add(""+keys(i).value()); d.add(""+data(slots(i)).value());}
       }
      return "keys: "+k+"\n"+"data: "+d+"\n";
     }

    protected String dump()                                                     // Dump a leaf
     {final StringJoiner d = new StringJoiner(" ");
      final int N = numberOfRefs();
      for (int i = 0; i < N; i++) d.add(String.format(formatKey, data(i) != null ? data(i).value() : 0));
      final String U = " up: "   +(up      != null ? up.name : "null");
      final String I = " index: "+(upIndex != null ? upIndex : "null");
      return "Leaf     : "+name+U+I+"\n"+super.dump() + "data     :  "+d+"\n";
     }
   }

//D1 Branch                                                                     // Use the slots to model a branch

  class Branch extends Slots                                                    // Branch
   {final Slots[]data = new Slots[maxBranchSize()];                             // Data corresponding to each key in the branch
    Slots top;                                                                  // Top most element
    Branch up; Integer upIndex;                                                 // The branch above

    Branch()                                                                    // Create a branch
     {super(maxBranchSize);                                                     // Slots for branch
      name = branches++;                                                        // Name the branch to help in debugging
     }

    int splitSize()             {return maxBranchSize / 2;}                     // Size of a split branch
    Slots firstChild()          {return data(locateFirstUsedSlot());}           // First child assuming there is one
    Slots data      (int Index) {return data[slots(Index)];}                    // Data at the indexed slot
    Slots dataDirect(int Index) {return data[Index];}                           // Data directly
    void data       (int Index, Slots Slots) {data[slots(Index)] = Slots;}      // Child slots via index
    void dataDirect (int Index, Slots Slots) {data[Index]        = Slots;}      // Child slots directly

    Branch duplicate()                                                          // Duplicate a branch
     {final Branch d = new Branch();
      d.copy(this);                                                             // Copy slots
      final int S = numberOfSlots();
      for (int i = 0; i < S; i++)
       {if (usedSlots(i)) d.data(i, data(i));                                   // Copy used data
       }
      d.top = top;
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
     {if (!full()) return null;                                                 // Only full branches can be split
      final int Count = splitSize();
      int  s  = 0;                                                              // Count slots used
      Key  sk = null;                                                           // Splitting key
      final int S = numberOfSlots();
      for (int i = 0; i < S; i++)                                               // Each slot
       {if (usedSlots(i))                                                       // Slot is in use
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
      final int S = numberOfSlots();
      for (int i = 0, p = 0; i < S; i++)                                        // Scan for splitting keys
       {if (usedSlots(i) && p++ == splitSize()) k += keys(i).value();           // Splitting key as last on left and first on right of split
       }
      return k;                                                                 // Splitting key
     }

    Branch split()                                                              // Split a branch
     {final long        sk = splittingKey();
      final Branch       l = duplicate();
      final Branch.Split s = l.splitRight();
      final Branch       b = new Branch();
      b.insert(new Key(sk), s.left); b.top = s.right;
      return b;
     }

    Integer insert(Key Key, Slots Data)                                         // Insert a key data pair into a branch
     {final Integer i = insert(Key);
      if (i != null) dataDirect(i, Data);
      return i;
     }

    public String toString()                                                    // Print the values in the used slots
     {final StringJoiner k = new StringJoiner(", ");
      final StringJoiner d = new StringJoiner(", ");
      final int S = numberOfSlots();
      for (int i = 0; i < S; i++)
       {if (usedSlots(i)) {k.add(""+keys(i).value()); d.add(""+data(i).name);}
       }
      return "keys: "+k+"\n"+"data: "+d+"\ntop : "+top.name+"\n";
     }

    protected String dump()                                                     // Dump a branch
     {final StringJoiner d = new StringJoiner(" ");
      final int N = numberOfRefs();
      for (int i = 0; i < N; i++)
       {if (dataDirect(i) == null) d.add("  .");
        else d.add(String.format(formatKey, dataDirect(i).name));
       }
      return "Branch   : "+name+"\n"+super.dump() +
             "data     :  "+d+"\ntop      :  "+String.format(formatKey, top.name)+"\n";
     }

    void compactLeft()                                                          // Compact the branch to the left
     {final int    N = numberOfSlots(), R = numberOfRefs();
      final Slots[]d = new Slots[R];
      for (int i = 0, p = 0; i < N; i++) if (usedSlots(i)) d[p++] = data(i);
      super.compactLeft();
      for (int i = 0; i < R; i++) dataDirect(i, d[i]);
     }

    void compactRight()                                                         // Compact the branch to the right
     {final int    N = numberOfSlots(), R = numberOfRefs();
      final Slots[]d = new Slots[R];
      for (int i = N-1, p = R-1; i >= 0;--i) if (usedSlots(i)) d[p--] = data(i);
      super.compactRight();
      for (int i = 0; i < R; i++) dataDirect(i, d[i]);
     }

    void mergeData(Key Key, Branch Left, Branch Right)                         // Merge the data from the compacted left and right slots
     {final Branch l = Left, r = Right;
      for (int i = 0; i < maxBranchSize; ++i)                                   // Each slot
       {if      (l.usedRefs(i)) dataDirect(i, l.data(i));                       // Merge from left first
        else if (r.usedRefs(i)) dataDirect(i, r.data(i));                       // Merge from right last
       }
      insert(Key, l.top); top = r.top;                                          // Insert left top
     }

    boolean mergeFromRight(Key Key, Branch Right)                               // Merge the specified slots from the right
     {if (countUsed() + Right.countUsed() >= maxBranchSize) return false;
      final Branch l =       duplicate(), r = Right.duplicate();
      l.compactLeft(); r.compactRight();
      mergeCompacted(l, r);
      mergeData(Key, l, r);
      redistribute();
      return true;
     }

    boolean mergeFromLeft(Key Key, Branch Left)                                 // Merge the specified slots from the right
     {if (Left.countUsed() + countUsed() >= maxBranchSize) return false;
      final Branch l = Left.duplicate(), r = duplicate();
      l.compactLeft(); r.compactRight();
      mergeCompacted(l, r);
      mergeData(Key, l, r);
      redistribute();
      return true;
     }

    boolean canStepLeft(Integer Location)                                       // Whether we can step left from this location. A location of null means top.
     {if (Location == null) return locateLastUsedSlot() != null;                // From top
      return locatePrevUsedSlot(Location-1) != null;                            // From body
     }

    boolean canStepRight(Integer Location) {return Location != null;}           // Whether we can step right from this location. A location of null means top.

    Integer stepLeft(Integer Loc)                                               // Step left to prior occupied slot assuming that such a step is possible
     {return Loc != null ? locatePrevUsedSlot(Loc-1) : locateLastUsedSlot();
     }

    Integer stepRight(Integer Index) {return locateNextUsedSlot(Index+1);}      // Step right to next occupied slot assuming that such a step is possible

    boolean mergeLeftSibling(Integer Right)                                     // Merge the indicated child with its left sibling if possible.  If the index is null merge into top
     {if (!canStepLeft(Right)) return false;                                    // Cannot step left
      final Integer left = stepLeft(Right);                                     // Left sibling from right child
      final Slots L = data(left);                                               // Left sibling as slots
      if (L instanceof Leaf)                                                    // Merging leaves
       {final Leaf r = (Leaf)(Right != null ? data(Right) : top);               // Right leaf sibling
        if (r.mergeFromLeft((Leaf)L))                                           // Merge left sibling into right
         {clearSlotAndRef(left);                                                // Remove left sibling from parent now that it has been merged with its right sibling
          return true;
         }
       }
      else                                                                      // Children are branches
       {final Branch r = (Branch)(Right != null ? data(Right) : top);           // Right leaf sibling
        if (r.mergeFromLeft(keys(left), (Branch)L))                             // Merge left sibling into right
         {clearSlotAndRef(left);                                                // Remove left sibling from parent now that it has been merged with its right sibling
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
      return data(Index);                                                       // The indicated child
     }

    Tree tree()                     {return Tree.this;}                         // Containing tree
    private Slots stepDown(Key Key) {return child(locateFirstGe(Key));}         // Step down from this branch

    private int count()                                                         // Count the number of entries under this branch
     {int n = 0;
      final int S = numberOfSlots();
      for  (int i = 0; i < S; i++)                                              // Each slot
       {if (usedSlots(i))                                                       // Active slot
         {final Slots s = data(i);
          if      (s instanceof Leaf)   n += s.countUsed();
          else if (s instanceof Branch) n += ((Branch)s).count();
         }
       }
      final Slots s = top;                                                      // Count entries below top
      if      (s instanceof Leaf)   n += s.countUsed();                         // Top is a leaf
      else if (s instanceof Branch) n += ((Branch)s).count();                   // Top is a branch
      return n;                                                                 // Number below this branch
     }
   }

//D1 Low Level                                                                  // Low level operations

  void mergeAlongPath(Slots.Key Key)                                            // Merge along the path from the specified key to the root
   {final Find f = find(Key);                                                   // Locate the leaf that should contain the key
    if (f == null) return;                                                      // Empty tree
    if (f.leaf.up != null)                                                      // Process path from leaf to root
     {for (Branch b = f.leaf.up; b != null; b = b.up)                           // Go up the tree merging as we go: only one merge is needed at each level
       {final Integer l = b.locateFirstGe(Key);                                 // Position of key
        if (b.mergeRightSibling(l)) continue;                                   // Merge right sibling of keyed child

        final Integer L = b.locateFirstGe(Key);                                 // Position of key
        if (b.mergeLeftSibling(L)) continue;                                    // Merge left sibling of keyed child

        final Integer k = b.locateFirstGe(Key);                                 // Look further left
        if (k != null)                                                          // Not top
         {final Integer K = b.locatePrevUsedSlot(k-1);
          if (K != null && b.mergeLeftSibling(K)) continue;                     // Merge further left sibling
         }
        else                                                                    // Top
         {final Integer K = b.locateLastUsedSlot();
          if (K != null && b.mergeLeftSibling(K)) continue;                     // Merge further left of top
         }

        final Integer m = b.locateFirstGe(Key);                                 // Look further right
        if (m != null)
         {final Integer M = b.locateNextUsedSlot(m+1);
          if (M != null && b.mergeRightSibling(M)) continue;                    // Merge further right sibling
         }
        b.mergeLeftSibling(null);                                               // Migrate into top
       }
     }

    mergeRoot(Key);                                                             // Merge the root if possible
   }

  void mergeRoot(Slots.Key Key)                                                 // Collapse the root if possible
   {if (root == null) return;                                                   // Empty tree
    if (root instanceof Leaf)                                                   // Leaf root
     {final Leaf l = (Leaf)root;
      if (l.empty()) root = null;                                               // Free leaf if it is empty
      return;
     }

    final Branch b = (Branch)root;                                              // Branch root
    if (b.countUsed() == 0) root = b.top;                                       // Root body is empty so collapse to top
    if (b.countUsed() != 1) return;                                             // Root body too big to collapse

    if (b.top instanceof Leaf)                                                  // Leaves for children
     {final Leaf    l = (Leaf)b.firstChild();
      final Leaf    r = (Leaf)b.top;
      final boolean m = l.mergeFromRight(r);

      if (m) root = l;                                                          // Update root if the leaves were successfully merged
      return;
     }
    final Branch  l = (Branch)b.firstChild();                                   // Root has branches for children
    final Branch  r = (Branch)b.top;
    final boolean m = r.mergeFromLeft(b.firstKey(), l);
    if (m) root = r;
   }

//D1 High Level                                                                 // High level operations: insert, find, delete

  class Find                                                                    // Find results
   {final Leaf leaf;                                                            // Leaf that should contain the key
    final Slots.Key key;                                                             // Search key
    final Slots.Locate locate;                                                  // Location details for key

    Find(Slots.Key Key, Leaf Leaf)
     {key    = Key;
      leaf   = Leaf;
      locate = Leaf.new Locate(Key);
     }

    public String toString()
     {final StringBuilder s = new StringBuilder();
      s.append("Find Key : "+key.value()+"\n");
      if (leaf    != null) s.append(leaf.dump());
      if (locate  != null) s.append("Locate      : "+locate   +"\n");
      final StringJoiner j = new StringJoiner(", ");
      for(Branch p = leaf.up; p != null; p = p.up) j.add(""+p.name);
      if (leaf.up != null) s.append("Path        : "+j+"\n");
      return ""+s;
     }
   }

  Find find(Slots.Key Key)
   {if (root == null) return null;                                              // Empty tree
    if (root instanceof Leaf)                                                   // Leaf root
     {final Leaf l = (Leaf)root;
      l.up = null; l.upIndex = null;                                            // Trace path taken to this leaf
      return new Find(Key, l);
     }
    return find(Key, (Branch)root);                                             // Start search from root
   }

  Find find(Slots.Key Key, Branch Start)
   {Branch p = Start;                                                           // Start at root

    for (int i = 0; i < MaximumNumberOfLevels; i++)                             // Step down from branch splitting as we go
     {final Integer P = p.locateFirstGe(Key);
      final Slots   q = p.child(P);
      if (q instanceof Leaf)                                                    // Step down to a leaf
       {final Leaf l = (Leaf)q;
        l.up = p; l.upIndex = P;                                                // Parent of leaf along find path
        return new Find(Key, l);
       }
      final Branch b = (Branch)q;
      b.up = p; b.upIndex = P;                                                  // Record parent branch
      p = b;                                                                    // Step down into non full branch
     }
    stop("Find fell off the end of tree after this many searches:", mnl());
    return null;
   }

  void insert(Slots.Key Key, Leaf.Data Data)                                    // Insert a key, data pair or update key data pair in the tree
   {if (root == null)                                                           // Empty tree
     {final Leaf l = new Leaf(); root = l;                                      // Root is a leaf
      l.insert(Key, Data);                                                      // Insert into leaf root
      return;
     }
    else                                                                        // Localize optimized insert for non full leaf or full leaf under non full parent
     {final Find F = find(Key);                                                 // See if key is already present
      if (F.locate.exact())                                                     // Key already present so update data associated with the key
       {final Leaf l = F.leaf;                                                  // Child leaf
        l.data(F.locate.at, Data);                                              // Update data
        return;
       }
      else if (!F.leaf.full())                                                  // Leaf not full so insert directly
       {final Leaf l = F.leaf;                                                  // Child leaf
        l.insert(Key, Data);                                                    // Insert key
        return;
       }
      else if (F.leaf.up != null && !F.leaf.up.full())                          // Leaf is full, parent branch is not full so we can split leaf
       {final Branch b = F.leaf.up;                                             // Parent branch
        final Leaf   r = F.leaf;
        final long  sk = r.splittingKey();
        final Leaf   l = r.splitLeft();
        b.insert(Key(sk), l);                                                  // Insert new left leaf into leaf
        if (Key.value() <= sk) l.insert(Key, Data); else r.insert(Key, Data);           // Insert new key, data pair into left leaf
        final Integer K = b.locateFirstGe(Key);                                 // Position of leaf in parent
        b.mergeLeftSibling (K);                                                 // Merge left leaf into prior leaf if possible
        b.mergeRightSibling(K);                                                 // Merge left leaf into prior leaf if possible
        if (b.canStepLeft  (K)) b.mergeLeftSibling (b.stepLeft (K));            // Merge right leaf into next leaf if possible
        if (b.canStepRight (K)) b.mergeRightSibling(b.stepRight(K));            // Merge right leaf into next leaf if possible
        return;
       }
     }

    if (root instanceof Leaf)                                                   // Leaf root
     {final Leaf l = (Leaf)root;
      if (!l.full())                                                            // Still space in leaf root
       {l.insert(Key, Data);                                                    // Insert into leaf root
        return;
       }
      else root = l.split();                                                    // Split full leaf root
     }

    Branch p = (Branch)root;                                                    // Start at root
    if (p.full()) {root = p.split(); p = (Branch)root;}                         // Split full root branch

    for (Branch q = find(Key).leaf.up, b = q.up; b != null; q = b, b = q.up)    // Path back from leaf to first full branch below a non full branch - the point at which we have to start splitting
     {if (!b.full()) {p = b; break;}
     }

    for (int i = 0; i < MaximumNumberOfLevels; i++)                             // Step down through the tree from branch to branch splitting as we go until we reach a leaf
     {final Slots q = p.stepDown(Key);                                          // Step down
      if (q instanceof Leaf)                                                    // Step down to a leaf
       {final Leaf r = (Leaf)q;                                                 // We have reached a leaf
        if (r.full())                                                           // Split the leaf if it is full
         {final long sk = r.splittingKey();                                     // Splitting key
          final Leaf  l = r.splitLeft();                                        // Right leaf split out of the leaf
          p.insert(Key(sk), l);                                                // The parent is known not to be full so the insert will work.  We are inserting left so this works even if we are splitting top
          if (Key.value() <= sk) l.insert(Key, Data); else r.insert(Key, Data);         // Insert into left or right leaf which will now have space
         }
        else r.insert(Key, Data);                                               // Leaf has sufficient space

        mergeAlongPath(Key);                                                    // Merge along the path taken by the key to compress the tree
        return;
       }
      final Branch r = (Branch)q;
      if (r.full())                                                             // Split the leaf if it is full
       {final long        sk = r.splittingKey();                                // Splitting key
        final Branch.Split s = r.splitLeft();                                   // Branch split out on right from
        p.insert(Key(sk), s.left);                                                   // The parent is known not to be full so the insert will work.  We are inserting left so this works even if we are splitting top
        if (Key.value() <= sk) p = s.left; else p = s.right;                            // Traverse left or right
       }
      else p = r;                                                               // Step down into non full branch
     }
    stop("Insert fell off the end of tree after this many searches:", mnl());
   }

  void delete(Slots.Key Key)                                                         // Delete a key from the tree
   {if (root == null) return;                                                   // The tree is empty tree so there is nothing to delete
    final Find f = find(Key);                                                   // Locate the key in the tree
    if (!f.locate.exact()) return;                                              // Key not found so nothing to delete
    f.leaf.clearSlotAndRef(f.locate.at);                                        // Delete key and data from leaf
    mergeAlongPath(Key);
   }

  int count()                                                                   // Print the tree with and without details
   {if (root == null) return 0;                                                 // Empty tree
    if (root instanceof Leaf) return root.countUsed();                          // Tree is a single leaf
    return ((Branch)root).count();                                              // Tree has one or more branches
   }

//D1 Navigation                                                                 // First, Last key, or find the next or prev key from a given key

  Find first()                                                                  // Find the position of the first key in the key
   {if (root == null) return null;                                              // Empty tree does not have a first key
    if (root instanceof Leaf)
     {final Leaf l = (Leaf)root;
      final int  i = l.locateFirstUsedSlot();
      l.up = null; l.upIndex = i;
      return new Find(l.keys(i), l);
     }

    return goFirst((Branch)root);                                               // Start at root and go all the way first
   }

  Find goFirst(Branch Start)                                                    // Go all the way first
   {Branch p = Start;                                                           // Start

    for (int j = 0; j < MaximumNumberOfLevels; j++)                             // Step down from branch splitting as we go
     {final int    P = p.locateFirstUsedSlot();
      final Slots  q = p.child(P);
      if (q instanceof Leaf)                                                    // Step down to a leaf
       {final Leaf l = (Leaf)q;
        l.up = p; l.upIndex = P;
        final int       i = l.locateFirstUsedSlot();
        return new Find(l.keys(i), l);
       }
      final Branch b = (Branch)q;
          b.up = p; b.upIndex = P;                                              // Step down into non full branch
      p = b;                                                                    // Step down into non full branch
     }
    stop("First fell off the end of tree after this many searches:", mnl());
    return null;
   }

  Find last()                                                                   // Find the position of the last key in the tree
   {if (root == null) return null;                                              // Empty tree does not have a last key
    if (root instanceof Leaf)
     {final Leaf l = (Leaf)root;
      final int  i = l.locateLastUsedSlot();
      l.up = null; l.upIndex = null;
      return new Find(l.keys(i), l);
     }

    return goLast((Branch)root);                                                // Start at root and go all the way last
   }

  Find goLast(Branch Start)                                                     // Go all the way last from the specified position
   {Branch p = Start;                                                           // Start

    for (int j = 0; j < MaximumNumberOfLevels; j++)                             // Step down from branch splitting as we go
     {final Slots q = p.top;
      if (q instanceof Leaf)                                                    // Step down to a leaf
       {final Leaf l = (Leaf)q;
        final int  i = l.locateLastUsedSlot();
        l.up = p; l.upIndex = null;
        return new Find(l.keys(i), l);
       }
         ((Branch)q).up = p;
      p = (Branch)q;                                                            // Step down into non full branch
     }
    stop("Last fell off the end of tree after this many searches:", mnl());
    return null;
   }

  Find next(Find Found)                                                         // Find the next key beyond the one previously found assuming that the structure of the tree has not changed
   {if (root == null) return null;                                              // Empty tree does not have a next key
    final Leaf l = Found.leaf;
    if (l.up == null) return null;                                              // Root is a leaf and we are at the end of it

    final Integer i = l.locateNextUsedSlot(Found.locate.at+1);                  // Next slot in leaf
    if (i != null) return new Find(l.keys(i), l);

    if (l.up.top != l)                                                          // In the body of the parent branch of the leaf
     {final Integer I = l.up.locateNextUsedSlot(l.upIndex+1);
      final Leaf    L = I != null ? (Leaf)l.up.data(I) : (Leaf)l.up.top;
      L.up = l.up; L.upIndex = I;
      return new Find(L.firstKey(), L);
     }
    Branch p;                                                                   // Last point at which we went left
    Branch q = l.up;
    for(p = q.up; p != null; q = p, p = q.up)
     {if (p.top != q)                                                           // In the body of the parent branch of the leaf
       {final Integer I = p.locateNextUsedSlot(q.upIndex+1);
        final Branch  b = I != null ? (Branch)p.data(I) : (Branch)p.top;
        b.up = p; b.upIndex = I;
        return goFirst(b);
       }
     }
    return null;
   }

  Find prev(Find Found)                                                         // Find the previous key before the one previously found assuming that the structure of the tree has not changed
   {if (root == null) return null;                                              // Empty tree does not have a next key
    final Leaf    l = Found.leaf;
    if (l.up == null) return null;                                              // Root is a leaf and we are at the end of it

    final Integer i = l.locatePrevUsedSlot(Found.locate.at-1);                  // Previous slot in leaf
    if (i != null) return new Find(l.keys(i), l);

    if (l.upIndex == null)                                                      // Last leaf of parent
     {final Integer I = l.up.locateLastUsedSlot();
      final Leaf    L = (Leaf)l.up.data(I);
      L.up = l.up; L.upIndex = I;
      return new Find(L.lastKey(), L);
     }
    else if (l.upIndex != l.locateFirstUsedSlot())                              // Not the first leaf of the parent branch
     {final Integer I = l.up.locatePrevUsedSlot(l.upIndex-1);
      final Leaf    L = I != null ? (Leaf)l.up.data(I) : (Leaf)l.up.top;
      L.up = l.up; L.upIndex = I;
      return new Find(L.lastKey(), L);
     }
    for(Branch q = l.up, p = q.up; p != null; q = p, p = q.up)                  // Go up to the last point where we went left
     {if (q.upIndex == null)                                                    // In the body of the parent branch of the leaf
       {final Integer I = p.locateLastUsedSlot();
        final Branch  b = (Branch)p.data(I);
        b.up = p; b.upIndex = I;
        return goLast(b);
       }
     }
    return null;
   }

//D1 Print                                                                      // Print the tree horizontally

  private final int linesToPrintABranch = 4;                                    // The number of lines required to print a branch
  private final int maxPrintLevels      = 3;                                    // The maximum number of levels to print `- this avoids endless print loops when something goes wrong

  private void printLeaf                                                        // Print leaf horizontally
   (Leaf Leaf, Stack<StringBuilder>P, int level, boolean Details)
   {padStrings(P, level);

    final StringJoiner s = new StringJoiner(",");
    final int S = Leaf.numberOfSlots();
    for (int i = 0; i < S; i++)
     {if (Leaf.usedSlots(i)) s.add(""+Leaf.keys(i).value());
     }
    final int L = level * linesToPrintABranch;                                  // Start line at which to print branch
    P.elementAt(L+0).append(s);
    final String U = Leaf.up      != null ? ""+Leaf.up.name : "null";
    final String I = Leaf.upIndex != null ? ""+Leaf.upIndex : "null";
    if (Details) P.elementAt(L+1).append("("+Leaf.name+", "+U+", "+I+")");
    padStrings(P, level);
   }

  private void printBranch                                                      // Print branch horizontally
   (Branch Branch, Stack<StringBuilder>P, int level, boolean Details)
   {if (level > maxPrintLevels) return;
    padStrings(P, level);
    final Branch B = Branch;
    final int L = level * linesToPrintABranch, K = B.countUsed();               // Size of branch

    if (K > 0)                                                                  // Branch has key, next pairs
     {final int S = B.numberOfSlots();
      for  (int i = 0; i < S; i++)
       {if (B.usedSlots(i))
         {final Slots   s = B.data(i);
          final boolean l = s instanceof Leaf, b = s instanceof Branch;

          if      (l) printLeaf  ((Leaf)  s, P, level+1, Details);
          else if (b) printBranch((Branch)s, P, level+1, Details);

          P.elementAt(L+0).append(" "+B.keys(i).value());                       // Key
          if (Details)
           {P.elementAt(L+1).append("["+Branch.name+"."+i+"]");                 // Branch, key, next pair
            final String U = B.up      != null ? ""+B.up.name : "null";
            final String I = B.upIndex != null ? ""+B.upIndex : "null";
            P.elementAt(L+2).append("("+s.name+", "+U+", "+I+")");              // Link to next level
           }
         }
       }
     }

    if (Details) P.elementAt(L+2).append("{"+B.top.name+"}");                   // Top of branch

    final boolean l = B.top instanceof Leaf, b = B.top instanceof Branch;       // Print top leaf
    if      (l) printLeaf  (  (Leaf)B.top, P, level+1, Details);
    else if (b) printBranch((Branch)B.top, P, level+1, Details);

    padStrings(P, level);                                                       // Equalize the strings used to print the tree
   }

 private String printBoxed()                                                    // Print a tree in a box
  {final String  s = ""+this;
   final int     n = longestLine(s)-1;
   final String[]L = s.split("\n");
   final StringJoiner t = new StringJoiner("\n",  "", "\n");
   t.add("+"+("-".repeat(n))+"+");
   for(String l : L) t.add("| "+l);
   t.add("+"+("-".repeat(n))+"+");
   return ""+t;
  }

  private void padStrings(Stack<StringBuilder> S, int level)                    // Pad the strings at each level of the tree so we have a vertical face to continue with - a bit like Marc Brunel's tunneling shield
   {final int N = level * linesToPrintABranch + maxLeafSize;                    // Number of lines we might want
    for (int i = S.size(); i <= N; ++i) S.push(new StringBuilder());            // Make sure we have a full deck of strings
    int m = 0;                                                                  // Maximum length
    for (StringBuilder s : S) m = m < s.length() ? s.length() : m;              // Find maximum length
    for (StringBuilder s : S)                                                   // Pad each string to maximum length
     {if (s.length() < m) s.append(" ".repeat(m - s.length()));                 // Pad string to maximum length
     }
   }

  private String printCollapsed(Stack<StringBuilder> S)                         // Collapse horizontal representation into a string
   {final StringBuilder t = new StringBuilder();                                // Print the lines of the tree that are not blank
    for  (StringBuilder s : S)
     {final String l = ""+s;
      if (!l.isBlank()) t.append(l+"|\n");
     }
    return ""+t;
   }

  public String toString() {return print(false);}                               // Print the tree without details
  public String dump()     {return print(true); }                               // Print the tree with details

  String print(boolean Details)                                                 // Print the tree with and without details
   {final Stack<StringBuilder> P = new Stack<>();
    if (root == null) return "|\n";                                             // Empty tree
    if (root instanceof Leaf) printLeaf  ((Leaf)  root, P, 0, Details);         // Tree is a single leaf
    else                      printBranch((Branch)root, P, 0, Details);         // Tree has one or more branches
    return printCollapsed(P);                                                   // Remove blank lines and add right fence
   }

//D1 Tests                                                                      // Test the btree

  final static int[]random_32 = {12, 3, 27, 1, 23, 20, 8, 18, 2, 31, 25, 16, 13, 32, 11, 21, 5, 24, 4, 10, 26, 30, 9, 6, 29, 17, 28, 15, 14, 19, 7, 22};
  final static int[]random    = {5918,5624,2514,4291,1791,5109,7993,60,1345,2705,5849,1034,2085,4208,4590,7740,9367,6582,4178,5578,1120,378,7120,8646,5112,4903,1482,8005,3801,5439,4534,9524,6111,204,5459,248,4284,8037,5369,7334,3384,5193,2847,1660,5605,7371,3430,1786,1216,4282,2146,1969,7236,2187,136,2726,9480,5,4515,6082,969,5017,7809,9321,3826,9179,5781,3351,4819,4545,8607,4146,6682,1043,2890,2964,7472,9405,4348,8333,2915,9674,7225,4743,995,1321,3885,6061,9958,3901,4710,4185,4776,5070,8892,8506,6988,2317,9342,3764,9859,4724,5195,673,359,9740,2089,9942,3749,9208,1,7446,7023,5496,4206,3272,3527,8593,809,3149,4173,9605,9021,5120,5265,7121,8667,6911,4717,2535,2743,1289,1494,3788,6380,9366,2732,1501,8543,8013,5612,2393,7041,3350,3204,288,7213,1741,1238,9830,6722,4687,6758,8067,4443,5013,5374,6986,282,6762,192,340,5075,6970,7723,5913,1060,1641,1495,5738,1618,157,6891,173,7535,4952,9166,8950,8680,1974,5466,2383,3387,3392,2188,3140,6806,3131,6237,6249,7952,1114,9017,4285,7193,3191,3763,9087,7284,9170,6116,3717,6695,6538,6165,6449,8960,2897,6814,3283,6600,6151,4624,3992,5860,9557,1884,5585,2966,1061,6414,2431,9543,6654,7417,2617,878,8848,8241,3790,3370,8768,1694,9875,9882,8802,7072,3772,2689,5301,7921,7774,1614,494,2338,8638,4161,4523,5709,4305,17,9626,843,9284,3492,7755,5525,4423,9718,2237,7401,2686,8751,1585,5919,9444,3271,1490,7004,5980,3904,370,5930,6304,7737,93,5941,9079,4968,9266,262,2766,4999,2450,9518,5137,8405,483,8840,2231,700,8049,8823,9811,9378,3811,8074,153,1940,1998,4354,7830,7086,6132,9967,5680,448,1976,4101,7839,3122,4379,9296,4881,1246,4334,9457,5401,1945,9548,8290,1184,3464,132,2458,7704,1056,7554,6203,2270,6070,4889,7369,1676,485,3648,357,1912,9661,4246,1576,1836,4521,7667,6907,2098,8825,7404,4019,8284,3710,7202,7050,9870,3348,3624,9224,6601,7897,6288,3713,932,5596,353,2615,3273,833,1446,8624,2489,3872,486,1091,2493,4157,3611,6570,7107,9153,4543,9504,4746,1342,9737,3247,8984,3640,5698,7814,307,8775,1150,4330,3059,5784,2370,5248,4806,6107,9700,231,3566,5627,3957,5317,5415,8119,2588,9440,2961,9786,4769,466,5411,3080,7623,5031,2378,9286,4801,797,1527,2325,847,6341,5310,1926,9481,2115,2165,5255,5465,5561,3606,7673,7443,7243,8447,2348,7925,6447,8311,6729,4441,7763,8107,267,8135,9194,6775,3883,9639,612,5024,1351,7557,9241,5181,2239,8002,5446,747,166,325,9925,3820,9531,5163,3545,558,7103,7658,5670,8323,4821,6263,7982,59,3700,1082,4474,4353,8637,9558,5191,842,5925,6455,4092,9929,9961,290,3523,6290,7787,8266,7986,7269,6408,3620,406,5964,7289,1620,6726,1257,1993,7006,5545,2913,5093,5066,3019,7081,6760,6779,7061,9051,8852,8118,2340,6596,4594,9708,8430,8659,8920,9268,5431,9203,2823,1427,2203,6422,6193,5214,9566,8791,4964,7575,4350,56,2227,8545,5646,3089,2204,4081,487,8496,2258,4336,6955,3452,556,8602,8251,8569,8636,9430,1025,9459,7137,8392,3553,5945,9414,3078,1688,5480,327,8117,2289,2195,8564,9423,103,7724,3091,8548,7298,5279,6042,2855,3286,3542,9361,420,7020,4112,5320,5366,6379,114,9174,9744,592,5346,3985,3174,5157,9890,1605,3082,8099,4346,7256,8670,5687,6613,6620,1458,1045,7917,2980,2399,1433,3315,4084,178,7056,2132,2728,4421,9195,4181,6017,6229,2945,4627,2809,8816,6737,18,8981,3813,8890,5304,3789,6959,7476,1856,4197,6944,9578,5915,3060,9932,3463,67,7393,9857,5822,3187,501,653,8453,3691,9736,6845,1365,9645,4120,2157,8471,4436,6435,2758,7591,9805,7142,7612,4891,7342,5764,8683,8365,2967,6947,441,2116,6612,1399,7585,972,6548,5481,7733,7209,222,5903,6161,9172,9628,7348,1588,5992,6094,7176,4214,8702,2987,74,8486,9788,7164,5788,8535,8422,6826,1800,8965,4965,565,5609,4686,2556,9324,5000,9809,1994,4737,63,8992,4783,2536,4462,8868,6346,5553,3980,2670,1601,4272,8725,4698,7333,7826,9233,4198,1997,1687,4851,62,7893,8149,8015,341,2230,1280,5559,9756,3761,7834,6805,9287,4622,5748,2320,1958,9129,9649,1644,4323,5096,9490,7529,6444,7478,7044,9525,7713,234,7553,9099,9885,7135,6493,9793,6268,8363,2267,9157,9451,1438,9292,1637,3739,695,1090,4731,4549,5171,5975,7347,5192,5243,1084,2216,9860,3318,5594,5790,1107,220,9397,3378,1353,4498,6497,5442,7929,7377,9541,9871,9895,6742,9146,9409,292,6278,50,5288,2217,4923,6790,4730,9240,3006,3547,9347,7863,4275,3287,2673,7485,1915,9837,2931,3918,635,9131,1197,6250,3853,4303,790,5548,9993,3702,2446,3862,9652,4432,973,41,3507,8585,2444,1633,956,5789,1523,8657,4869,8580,8474,7093,7812,2549,7363,9315,6731,1130,7645,7018,7852,362,1636,2905,8006,4040,6643,8052,7021,3665,8383,715,1876,2783,3065,604,4566,8761,7911,1983,3836,5547,8495,8144,1950,2537,8575,640,8730,8303,1454,8165,6647,4762,909,9449,8640,9253,7293,8767,3004,4623,6862,8994,2520,1215,6299,8414,2576,6148,1510,313,3693,9843,8757,5774,8871,8061,8832,5573,5275,9452,1248,228,9749,2730};

  static void test_compactLeafLeft()
   {final Leaf l = new Tree(8, 7).new Leaf();
    l.insert(Key(13), new Leaf.Data(23));
    l.insert(Key(12), new Leaf.Data(22));
    l.insert(Key(14), new Leaf.Data(24));
    l.insert(Key(11), new Leaf.Data(21));
    ok(l.dump(), """
Leaf     : 0 up: null index: null
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   0   0   3   1   0   2   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X   X   X   .   .   .   .   .   .
usedRefs :    X   X   X   X   .   .   .   .
keys     :   13  12  14  11   0   0   0   0
data     :   23  22  24  21   0   0   0   0
""");
    l.compactLeft();
    ok(l.dump(), """
Leaf     : 0 up: null index: null
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
    l.insert(Key(13), new Leaf.Data(23));
    l.insert(Key(12), new Leaf.Data(22));
    l.insert(Key(14), new Leaf.Data(24));
    l.insert(Key(11), new Leaf.Data(21));
    ok(l.dump(), """
Leaf     : 0 up: null index: null
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   0   0   3   1   0   2   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X   X   X   .   .   .   .   .   .
usedRefs :    X   X   X   X   .   .   .   .
keys     :   13  12  14  11   0   0   0   0
data     :   23  22  24  21   0   0   0   0
""");
    l.compactRight();
    ok(l.dump(), """
Leaf     : 0 up: null index: null
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
    b.insert(Key(12), Slots.fake(12));
    b.insert(Key(11), Slots.fake(11));
    b.insert(Key(13), Slots.fake(13));
    b.top =      Slots.fake(4);
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
    b.insert(Key(12), Slots.fake(12));
    b.insert(Key(11), Slots.fake(11));
    b.insert(Key(13), Slots.fake(13));
    b.top =      Slots.fake(4);
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

  static Leaf.Data[]test_leaf_data(int...Values)
   {final Leaf.Data[]d = new Leaf.Data[Values.length];
    for (int i = 0; i < d.length; i++) d[i] = new Leaf.Data(Values[i]);
    return d;
   }

  static Leaf test_leaf()
   {final Leaf       l = new Tree(8, 7).new Leaf();
    final Leaf.Data[]d = test_leaf_data(13, 16, 15, 18, 17, 14, 12, 11);
    for (int i = 0; i < d.length; i++) l.insert(Key(d[i].value()), d[i]);
    return l;
   }

  static void test_splitLeftLeafIntoRight()
   {final Leaf l = test_leaf();
    ok(l.dump(), """
Leaf     : 0 up: null index: null
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    7   6   0   0   5   0   2   0   1   0   4   0   3   0   0   0
usedSlots:    X   X   X   .   X   .   X   .   X   .   X   .   X   .   .   .
usedRefs :    X   X   X   X   X   X   X   X
keys     :   13  16  15  18  17  14  12  11
data     :   13  16  15  18  17  14  12  11
""");
    final Leaf r = l.splitRight();
    ok(l.dump(), """
Leaf     : 0 up: null index: null
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   6   0   0   0   0   0   0   0   5   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   X   .   .
usedRefs :    X   .   .   .   .   X   X   X
keys     :   13  16  15  18  17  14  12  11
data     :   13  16  15  18  17  14  12  11
""");
    ok(r.dump(), """
Leaf     : 1 up: null index: null
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

    final long[]k = new long[] {13, 16, 15, 17, 14, 12, 11};
    final int []d = new int [] {3,   6,  5,  7,  4,  2, 1};
    for (int i = 0; i < d.length; i++) b.insert(Key(k[i]), Slots.fake(d[i]));
    b.top = Slots.fake(8);
    return b;
   }

  static void test_splitLeftBranchIntoRight()
   {final Branch       b = test_branch();
    final Branch.Split s = b.splitRight();
    ok(s.left, """
keys: 11, 12, 13
data: 1, 2, 3
top : 4
""");
    ok(s.right, """
keys: 15, 16, 17
data: 5, 6, 7
top : 8
""");
    ok(s.key.value(), 14);
   }

  static void test_splitRightBranchIntoLeft()
   {final Branch       r = test_branch();
    final Branch.Split s = r.splitLeft();
    ok(s.left, """
keys: 11, 12, 13
data: 1, 2, 3
top : 4
""");
    ok(s.right, """
keys: 15, 16, 17
data: 5, 6, 7
top : 8
""");
    ok(s.key.value(), 14);
   }

  static Leaf test_leaf1()
   {final Leaf       l = new Tree(8,7).new Leaf();
    final Leaf.Data[]d = test_leaf_data(13, 14, 12, 11);
    for (int i = 0; i < d.length; i++) l.insert(Key(d[i].value()), d[i]);
    return l;
   }

  static Leaf test_leaf2()
   {final Leaf       l = new Tree(8,7).new Leaf();
    final Leaf.Data[]d = test_leaf_data(16, 15, 18, 17);
    for (int i = 0; i < d.length; i++) l.insert(Key(d[i].value()), d[i]);
    return l;
   }

  static void test_mergeLeafLeft()
   {final Leaf l = test_leaf1();
    final Leaf r = test_leaf2();
    l.mergeFromRight(r);
    ok(l.dump(), """
Leaf     : 0 up: null index: null
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
    ok(r.dump(), """
Leaf     : 0 up: null index: null
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
    final int []d = new int []{ 3,  2,  1};
    for (int i = 0; i < k.length; i++) b.insert(Key(k[i]), Slots.fake(d[i]));
    b.top = Slots.fake(4);
    return b;
   }

  static Branch test_branch2()
   {final Branch b = new Tree(8, 7).new Branch();
    final long[]k = new long[]{16, 15, 17};
    final int []d = new int[]{6, 5, 7};
    for (int i = 0; i < k.length; i++) b.insert(Key(k[i]), Slots.fake(d[i]));
    b.top = Slots.fake(8);
    return b;
   }

  static void test_mergeBranchLeft()
   {final Branch l = test_branch1();
    final Branch r = test_branch2();
    l.mergeFromRight(Key(14), r);
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
    r.mergeFromLeft(Key(14), l);
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
    b.insert(Key(1));
    b.insert(Key(5));
    b.insert(Key(3));
    b.redistribute();
    //stop(b.dump());
    ok(b.dump(), """
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
    ok(t.count(), 0);
    t.insert(Key(11), new Leaf.Data(21));
    ok(t, """
11|
""");
    t.insert(Key(13), new Leaf.Data(23));
    ok(t, """
11,13|
""");
    t.insert(Key(12), new Leaf.Data(22));
    ok(t, """
11,12,13|
""");
    t.insert(Key(14), new Leaf.Data(24));
    ok(t, """
11,12,13,14|
""");
    t.insert(Key(15), new Leaf.Data(25));
    ok(t, """
      12        |
11,12   13,14,15|
""");
    t.insert(Key(16), new Leaf.Data(26));
    ok(t, """
      12           |
11,12   13,14,15,16|
""");
    t.insert(Key(17), new Leaf.Data(27));
    ok(t, """
            14        |
11,12,13,14   15,16,17|
""");
    t.insert(Key(18), new Leaf.Data(28));
    ok(t, """
            14           |
11,12,13,14   15,16,17,18|
""");
    t.insert(Key(19), new Leaf.Data(29));
    ok(t, """
            14      16        |
11,12,13,14   15,16   17,18,19|
""");
    t.insert(Key(20), new Leaf.Data(30));
    ok(t, """
            14      16           |
11,12,13,14   15,16   17,18,19,20|
""");
    t.insert(Key(21), new Leaf.Data(31));
    ok(t, """
            14            18        |
11,12,13,14   15,16,17,18   19,20,21|
""");
    t.insert(Key(22), new Leaf.Data(32));
    ok(t, """
            14            18           |
11,12,13,14   15,16,17,18   19,20,21,22|
""");
    t.insert(Key(23), new Leaf.Data(33));
    ok(t, """
            14            18      20        |
11,12,13,14   15,16,17,18   19,20   21,22,23|
""");
    t.insert(Key(24), new Leaf.Data(34));
    ok(t, """
            14            18      20           |
11,12,13,14   15,16,17,18   19,20   21,22,23,24|
""");
    t.insert(Key(25), new Leaf.Data(35));
    ok(t, """
            14            18            22        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25|
""");
    ok(t.count(), 15);
    t.insert(Key(26), new Leaf.Data(36));
    ok(t, """
            14            18            22           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26|
""");
    ok(t.count(), 16);
    t.insert(Key(27), new Leaf.Data(37));
    ok(t, """
                          18                              |
            14                          22      24        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24   25,26,27|
""");
    ok(t.count(), 17);
    t.insert(Key(28), new Leaf.Data(38));
    ok(t, """
                          18                                 |
            14                          22      24           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24   25,26,27,28|
""");
    ok(t.count(), 18);
    t.insert(Key(29), new Leaf.Data(39));
    ok(t, """
                          18                                    |
            14                          22            26        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29|
""");
    t.insert(Key(30), new Leaf.Data(40));
    ok(t, """
                          18                                       |
            14                          22            26           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30|
""");
    t.insert(Key(31), new Leaf.Data(41));
    ok(t, """
                          18                                            |
            14                          22            26      28        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28   29,30,31|
""");
    t.insert(Key(32), new Leaf.Data(42));
    ok(t, """
                          18                                               |
            14                          22            26      28           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28   29,30,31,32|
""");
    t.insert(Key(33), new Leaf.Data(43));
    ok(""+t, """
                          18                                                  |
            14                          22            26            30        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33|
""");
    t.insert(Key(34), new Leaf.Data(44));
    ok(t, """
                          18                                                     |
            14                          22            26            30           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34|
""");
    t.insert(Key(35), new Leaf.Data(45));
    ok(t, """
                                                      26                              |
            14            18            22                          30      32        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32   33,34,35|
""");
    t.insert(Key(36), new Leaf.Data(46));
    ok(t, """
                                                      26                                 |
            14            18            22                          30      32           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32   33,34,35,36|
""");
    t.insert(Key(37), new Leaf.Data(47));
    ok(t, """
                                                      26                                    |
            14            18            22                          30            34        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36,37|
""");
    t.insert(Key(38), new Leaf.Data(48));
    ok(t, """
                                                      26                                       |
            14            18            22                          30            34           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36,37,38|
""");
    t.insert(Key(39), new Leaf.Data(49));
    ok(t, """
                                                      26                                            |
            14            18            22                          30            34      36        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36   37,38,39|
""");
    t.insert(Key(40), new Leaf.Data(50));
    ok(t, """
                                                      26                                               |
            14            18            22                          30            34      36           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36   37,38,39,40|
""");
    t.insert(Key(41), new Leaf.Data(51));
    ok(t, """
                                                      26                                                  |
            14            18            22                          30            34            38        |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36,37,38   39,40,41|
""");
    t.insert(Key(42), new Leaf.Data(52));
    ok(t, """
                                                      26                                                     |
            14            18            22                          30            34            38           |
11,12,13,14   15,16,17,18   19,20,21,22   23,24,25,26   27,28,29,30   31,32,33,34   35,36,37,38   39,40,41,42|
""");
    ok(t.count(), 32);

    ok(t.find(Key(10)), """
Find Key : 10
Leaf     : 3 up: 12 index: 0
positions:    0   1   2   3   4   5   6   7
slots    :    0   0   1   0   2   0   3   0
usedSlots:    X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X
keys     :   11  12  13  14
data     :   21  22  23  24
Locate      :  0  below all
Path        : 12, 8
""");

    ok(t.find(Key(23)), """
Find Key : 23
Leaf     : 15 up: 12 index: null
positions:    0   1   2   3   4   5   6   7
slots    :    0   0   1   0   2   0   3   0
usedSlots:    X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X
keys     :   23  24  25  26
data     :   33  34  35  36
Locate      : 0 exact
Path        : 12, 8
""");
   }

  static void test_insert_reverse()
   {final Tree t = new Tree(4, 3);
    final int N = 32;
    for (int i = N; i > 0; i--)
     {t.insert(Key(i), new Leaf.Data(i));
      ok(t.count(), N-i+1);
     }
    ok(t, """
                                           16                                                     |
        4        8           12                          20            24            28           |
1,2,3,4  5,6,7,8  9,10,11,12   13,14,15,16   17,18,19,20   21,22,23,24   25,26,27,28   29,30,31,32|
""");

    //stop(t.first());
    final Find n1 = t.first();
    ok(n1, """
Find Key : 1
Leaf     : 27 up: 12 index: 0
positions:    0   1   2   3   4   5   6   7
slots    :    3   0   2   0   1   0   0   0
usedSlots:    X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X
keys     :    4   3   2   1
data     :    4   3   2   1
Locate      : 0 exact
Path        : 12, 8
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
Leaf     : 2 up: 7 index: null
positions:    0   1   2   3   4   5   6   7
slots    :    0   0   1   0   2   0   3   0
usedSlots:    X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X
keys     :   29  30  31  32
data     :   29  30  31  32
Locate      : 6 exact
Path        : 7, 8
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
     {t.insert(Key(random_32[i]), new Leaf.Data(i));
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
    for (int i = 0; i < random.length; i++)
     {t.insert(Key(random[i]), new Leaf.Data(i));
      ok(t.count(), i+1);
     }
    ok(t, """
                                                                                            2152                                                                                                                              4957                                                                                      6829                                                                                                                                                           |
                        706                                                                                                                             3441                                    4249                                                        5518                                                                                                               7866                               8626                                                                 |
            368                           1232                1571           1788                          2453           2776                3210                3622           3855                          4524                     5156                          5736           6014           6346                          7071                7352      7606                               8325                          8921           9250                9599               |
 98 226 285     484 583     838 1014 1081      1333 1430 1488      1616 1650      1971 2093      2253 2350      2545 2697      2927 3048 3126      3367      3536      3713 3782      4023 4167      4326 4401      4713 4797      5043      5221 5345 5449      5608      5815 5918      6133 6243      6493 6632 6740      7005      7120 7167 7256      7459      7713 7757      7997 8052 8157      8471 8549      8673 8779      9035 9172      9354 9423 9499      9687 9799 9864|
""");
    return t;
   }

  static void test_delete()
   {final Tree t = new Tree(4, 3);
    final int N = 32;
    for (int i = 1; i <= N; ++i) t.insert(Key(i), new Leaf.Data(i));

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
    for (int i = 1; i <= N; ++i) t.insert(Key(i), new Leaf.Data(i));

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
    for (int i = 1; i <= N; ++i) t.insert(Key(i), new Leaf.Data(i));
    ok(t, """
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            128                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
                                                                                                                 32                                                                                                                              64                                                                                                                              96                                                                                                                                                                                                                                                                                                                                                         160                                                                                                                                                                             192                                                                                                                                                                             224                                                                                                                                                                            |
                   8                             16                              24                                                              40                              48                              56                                                              72                              80                              88                                                                     104                                         112                                         120                                                                                     136                                         144                                         152                                                                                     168                                         176                                         184                                                                                     200                                         208                                         216                                                                                     232                                         240                                         248                                        |
    2    4    6          10      12      14              18      20      22              26      28      30              34      36      38              42      44      46              50      52      54              58      60      62              66      68      70              74      76      78              82      84      86              90      92      94              98       100        102                   106        108        110                   114        116        118                   122        124        126                   130        132        134                   138        140        142                   146        148        150                   154        156        158                   162        164        166                   170        172        174                   178        180        182                   186        188        190                   194        196        198                   202        204        206                   210        212        214                   218        220        222                   226        228        230                   234        236        238                   242        244        246                   250        252        254       |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28   29,30   31,32   33,34   35,36   37,38   39,40   41,42   43,44   45,46   47,48   49,50   51,52   53,54   55,56   57,58   59,60   61,62   63,64   65,66   67,68   69,70   71,72   73,74   75,76   77,78   79,80   81,82   83,84   85,86   87,88   89,90   91,92   93,94   95,96   97,98   99,100    101,102    103,104    105,106    107,108    109,110    111,112    113,114    115,116    117,118    119,120    121,122    123,124    125,126    127,128    129,130    131,132    133,134    135,136    137,138    139,140    141,142    143,144    145,146    147,148    149,150    151,152    153,154    155,156    157,158    159,160    161,162    163,164    165,166    167,168    169,170    171,172    173,174    175,176    177,178    179,180    181,182    183,184    185,186    187,188    189,190    191,192    193,194    195,196    197,198    199,200    201,202    203,204    205,206    207,208    209,210    211,212    213,214    215,216    217,218    219,220    221,222    223,224    225,226    227,228    229,230    231,232    233,234    235,236    237,238    239,240    241,242    243,244    245,246    247,248    249,250    251,252    253,254    255,256|
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
    test_insert();
    test_insert_reverse();
    test_insert_random();
    test_insert_random_32();
    test_delete();
    test_delete_descending();
    test_delete_random_32();
    test_deep();
   }

  static void newTests()                                                        // Tests being worked on
   {oldTests();
    test_deep();
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
