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
  boolean           suppressMerge = true;                                       // Suppress merges during put to allow merge steps to be tested individually.  If this is on the trees built for testing are already merged so there is nothing to test.
  static boolean  createTestTrees = false;                                      // Create trees to assist testing
  static boolean            debug = false;                                      // Debug if enabled
  int leaves = 0, branches = 0;                                                 // Labels for the leaves and branches to assist in debugging

//D1 Construction                                                               // Construct and layout a tree

  Tree(int MaxLeafSize, int MaxBranchSize)                                      // Create the tree
   {if (MaxLeafSize   < 2) stop("The maximum leaf size must be 2 or more, not:",   MaxLeafSize);
    if (MaxBranchSize < 3) stop("The maximum branch size must be 3 or more, not:", MaxBranchSize);
    if (MaxBranchSize %2 == 0) stop("The maximum branch size must be odd, not:",   MaxBranchSize);

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

    int splitSize() {return maxBranchSize / 2;}                                 // Size of a split branch
    Slots data(int Index) {return data[slots(Index)];}                          // Data at the indexed slot

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
      for (int i = 0; i < maxBranchSize; ++i)
       {if      (l.usedRefs(i)) data[i] = l.data[i];
        else if (r.usedRefs(i)) data[i] = r.data[i];
       }
      keys[splitSize()] = Key;
      data[splitSize()] = Left.top;
      usedSlots[splitSize()] = usedRefs[splitSize()] = true;
      top = Right.top;
     }

    boolean mergeOnRight(long Key, Branch Right)                                // Merge the specified slots from the right
     {if (countUsed() + Right.countUsed() > maxBranchSize) return false;
      final Branch l =       duplicate(),
                   r = Right.duplicate();
      l.compactLeft(); r.compactRight();
      mergeCompacted((Slots)l, (Slots)r);
      mergeData(Key, l, r);
      redistribute();
      return true;
     }

    boolean mergeOnLeft(long Key, Branch Left)                                  // Merge the specified slots from the right
     {if (Left.countUsed() + countUsed() > maxBranchSize) return false;
      final Branch l = Left.duplicate(),
                   r =      duplicate();
      l.compactLeft(); r.compactRight();
      mergeCompacted((Slots)l, (Slots)r);
      mergeData(Key, l, r);
      redistribute();
      return true;
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

//D1 High Level                                                                 // High level operations: insert, find, delete

  class Find                                                                    // Find results
   {Branch  branch;                                                             // Last branch
    Leaf    leaf;                                                               // Leaf that should contain the key
    Integer parentIndex;                                                        // Slot used in parent for leaf
    Integer childIndex;                                                         // Slot used for key in child if present
    long    key;                                                                // Search key
    Find(long Key, Branch Branch, Leaf Leaf,
         Integer ParentIndex, Integer ChildIndex)
     {key         = Key;
      branch      = Branch;
      leaf        = Leaf;
      parentIndex = ParentIndex;
      childIndex  = ChildIndex;
     }
    public String toString()
     {final StringBuilder s = new StringBuilder();
      s.append("Find Key : "+key+"\n");
      if (branch       != null) s.append(branch.dump());
      if (leaf         != null) s.append(leaf  .dump());
      if (parentIndex  != null) s.append("ParentIndex : "+parentIndex  +"\n");
      if (childIndex   != null) s.append("ChildIndex  : "+childIndex   +"\n");
      return ""+s;
     }
   }

  Find find(long Key)
   {if (root == null) return null;                                              // Empty tree
    if (root instanceof Leaf)                                                   // Leaf root
     {final Leaf l = (Leaf)root;
      return new Find(Key, null, l, null, l.locate(Key));
     }
    Branch p = (Branch)root;                                                    // Start at root
    for (int i = 0; i < MaximumNumberOfLevels; i++)                             // Step down from branch splitting as we go
     {final Integer P = p.locateFirstGe(Key);
      final Slots q = p.child(P);
      if (q instanceof Leaf)                                                    // Step down to a leaf
       {final Leaf l = (Leaf)q;
        return new Find(Key, p, l, P, l.locate(Key));
       }
      p = (Branch)q;                                                            // Step down into non full branch
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
      b.insert(sk, l);                                                          // Insert new left leaf into branch
      if (Key <= sk) l.insert(Key, Data); else r.insert(Key, Data);             // insert new key, data pair into leaf
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
        else r.insert(Key, Data);                                               // leaf has suffucient space
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

//D1 Print                                                                      // Print the tree horizontally

  final int linesToPrintABranch =  4;                                           // The number of lines required to print a branch
  final int maxPrintLevels      =  3;                                           // The maximum number of levels to print `- this avoids endless print loops when something goes wrong

  void printLeaf(Leaf leaf, Stack<StringBuilder>P, int level)                   // Print leaf horizontally
   {padStrings(P, level);

    final StringJoiner s = new StringJoiner(",");
    for (int i = 0; i < leaf.numberOfSlots; i++)
     {if (leaf.usedSlots(i)) s.add(""+leaf.keys(i));
     }
    P.elementAt(level*linesToPrintABranch).append(s);
    padStrings(P, level);
   }

  void printBranch(Branch branch, Stack<StringBuilder>P, int level)             // Print branch horizontally
   {if (level > maxPrintLevels) return;
    padStrings(P, level);
    final int L = level * linesToPrintABranch;                                  // Start line at which to print branch
    final int K = branch.countUsed();                                           // Size of branch

    if (K > 0)                                                                  // Branch has key, next pairs
     {for  (int i = 0; i < branch.numberOfSlots; i++)
       {if (!branch.usedSlots(i)) continue;
        final Slots s = branch.data[branch.slots(i)];

        if (s instanceof Leaf)
         {printLeaf  ((Leaf)s,   P, level+1);
         }
        else if (s instanceof Branch)
         {printBranch((Branch)s, P, level+1);
         }
        //final int key  = stuckKeys.memoryGet(BtreeIndex, i);
        //final int data = stuckData.memoryGet(BtreeIndex, i);

        P.elementAt(L+0).append(" "+branch.keys(i));                            // Key
        //P.elementAt(L+1).append(""+BtreeIndex+(i > 0 ?  "."+i : ""));         // Branch,key, next pair
        //P.elementAt(L+2).append(""+stuckData.memoryGet(BtreeIndex, i));
       }
     }
    else                                                                        // Branch is empty so print just the index of the branch
     {//P.elementAt(L+0).append(""+BtreeIndex+"Empty");
     }
    //final int top = stuckData.memoryGet(BtreeIndex, K);                       // Top next will always be present
    //P.elementAt(L+3).append(top);                                             // Append top next

    if (branch.top instanceof Leaf)                                             // Print leaf
     {printLeaf  (  (Leaf)branch.top, P, level+1);
     }
    else if (branch.top instanceof Branch)                                      // Print leaf
     {printBranch((Branch)branch.top, P, level+1);
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

  public String toString()
   {final Stack<StringBuilder> P = new Stack<>();
    if (root instanceof Leaf) printLeaf  ((Leaf)  root, P, 0);
    else                      printBranch((Branch)root, P, 0);
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
slots    :    0   0   1   0   2   0   0   0   4   0   5   0   6   0
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
slots    :    0   0   1   0   2   0   0   0   4   0   5   0   6   0
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
    t.suppressMerge = false;

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
      12      14        |
11,12   13,14   15,16,17|
""");
    t.insert(18, 28);
    ok(t, """
      12      14           |
11,12   13,14   15,16,17,18|
""");
    t.insert(19, 29);
    ok(t, """
      12      14      16        |
11,12   13,14   15,16   17,18,19|
""");
    t.insert(20, 30);
    ok(t, """
      12      14      16           |
11,12   13,14   15,16   17,18,19,20|
""");
    t.insert(21, 31);
    ok(t, """
              14                        |
      12              16      18        |
11,12   13,14   15,16   17,18   19,20,21|
""");
    t.insert(22, 32);
    ok(t, """
              14                           |
      12              16      18           |
11,12   13,14   15,16   17,18   19,20,21,22|
""");
    t.insert(23, 33);
    ok(t, """
              14                                |
      12              16      18      20        |
11,12   13,14   15,16   17,18   19,20   21,22,23|
""");
    t.insert(24, 34);
    ok(t, """
              14                                   |
      12              16      18      20           |
11,12   13,14   15,16   17,18   19,20   21,22,23,24|
""");
    t.insert(25, 35);
    ok(t, """
              14              18                        |
      12              16              20      22        |
11,12   13,14   15,16   17,18   19,20   21,22   23,24,25|
""");
    t.insert(26, 36);
    ok(t, """
              14              18                           |
      12              16              20      22           |
11,12   13,14   15,16   17,18   19,20   21,22   23,24,25,26|
""");
    t.insert(27, 37);
    ok(t, """
              14              18                                |
      12              16              20      22      24        |
11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26,27|
""");
    t.insert(28, 38);
    ok(t, """
              14              18                                   |
      12              16              20      22      24           |
11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26,27,28|
""");
    t.insert(29, 39);
    ok(t, """
              14              18              22                        |
      12              16              20              24      26        |
11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28,29|
""");
    t.insert(30, 40);
    ok(t, """
              14              18              22                           |
      12              16              20              24      26           |
11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28,29,30|
""");
    t.insert(31, 41);
    ok(t, """
              14              18              22                                |
      12              16              20              24      26      28        |
11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28   29,30,31|
""");
    t.insert(32, 42);
    ok(t, """
              14              18              22                                   |
      12              16              20              24      26      28           |
11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28   29,30,31,32|
""");
    t.insert(33, 43);
    ok(t, """
                              18                                                        |
              14                              22              26                        |
      12              16              20              24              28      30        |
11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28   29,30   31,32,33|
""");
    t.insert(34, 44);
    ok(t, """
                              18                                                           |
              14                              22              26                           |
      12              16              20              24              28      30           |
11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28   29,30   31,32,33,34|
""");
    t.insert(35, 45);
    ok(t, """
                              18                                                                |
              14                              22              26                                |
      12              16              20              24              28      30      32        |
11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28   29,30   31,32   33,34,35|
""");
    t.insert(36, 46);
    ok(t, """
                              18                                                                   |
              14                              22              26                                   |
      12              16              20              24              28      30      32           |
11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28   29,30   31,32   33,34,35,36|
""");
    t.insert(37, 47);
    ok(t, """
                              18                                                                        |
              14                              22              26              30                        |
      12              16              20              24              28              32      34        |
11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28   29,30   31,32   33,34   35,36,37|
""");
    t.insert(38, 48);
    ok(t, """
                              18                                                                           |
              14                              22              26              30                           |
      12              16              20              24              28              32      34           |
11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28   29,30   31,32   33,34   35,36,37,38|
""");
    t.insert(39, 49);
    ok(t, """
                              18                                                                                |
              14                              22              26              30                                |
      12              16              20              24              28              32      34      36        |
11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28   29,30   31,32   33,34   35,36   37,38,39|
""");
    t.insert(40, 50);
    ok(t, """
                              18                                                                                   |
              14                              22              26              30                                   |
      12              16              20              24              28              32      34      36           |
11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28   29,30   31,32   33,34   35,36   37,38,39,40|
""");
    t.insert(41, 51);
    ok(t, """
                              18                              26                                                        |
              14                              22                              30              34                        |
      12              16              20              24              28              32              36      38        |
11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28   29,30   31,32   33,34   35,36   37,38   39,40,41|
""");
    t.insert(42, 52);
    ok(t, """
                              18                              26                                                           |
              14                              22                              30              34                           |
      12              16              20              24              28              32              36      38           |
11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28   29,30   31,32   33,34   35,36   37,38   39,40,41,42|
""");

    ok(t.find(10), """
Find Key : 10
Branch   : 1
positions:    0   1   2   3   4   5
slots    :    0   0   0   0   0   0
usedSlots:    .   .   X   .   .   .
usedRefs :    X   .   .
keys     :   12  14  16
data     :  1 3 4
top      :  3
Leaf     : 1
positions:    0   1   2   3   4   5   6   7
slots    :    0   0   0   0   0   2   0   0
usedSlots:    .   X   .   .   .   X   .   .
usedRefs :    X   .   X   .
keys     :   11  13  12  14
data     :   21  23  22  24
ParentIndex : 2
""");

    ok(t.find(23), """
Find Key : 23
Branch   : 9
positions:    0   1   2   3   4   5
slots    :    0   0   2   0   0   0
usedSlots:    .   .   X   .   .   .
usedRefs :    .   .   X
keys     :   26  28  24
data     :  9 10 8
top      :  9
Leaf     : 8
positions:    0   1   2   3   4   5   6   7
slots    :    0   0   0   0   0   2   0   0
usedSlots:    .   X   .   .   .   X   .   .
usedRefs :    X   .   X   .
keys     :   23  25  24  26
data     :   33  35  34  36
ParentIndex : 2
ChildIndex  : 1
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
