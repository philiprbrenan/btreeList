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
  boolean           suppressMerge = false;                                      // Suppress merges during put to allow merge steps to be tested individually.  If this is on the trees built for testing are already merged so there is nothing to test.
  static boolean  createTestTrees = false;                                      // Create trees to assist testing
  static boolean            debug = false;                                      // Debug if enabled

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
   {final double[]data = new double[maxLeafSize()];                             // Data corresponding to each key in the leaf

    Leaf()                                                                      // Create a leaf
     {super(maxLeafSize);                                                       // Slots for leaf
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

    double splittingKey()                                                       // Splitting key from a leaf
     {if (!full()) stop("Leaf not full");                                       // The leaf must be full if we are going to split it
      double k = 0;                                                             // Splitting key
      int    p = 0;                                                             // Position in leaf
      for (int i = 0; i < numberOfSlots; i++)                                   // Scan for splitting keys
       {if (usedSlots(i))                                                       // Used slot
         {if (p == splitSize()-1 || p == splitSize()) k += key(i);              // Accumulate splitting key as last on left and first on right of split
          ++p;                                                                  // Next position
         }
       }
      return k /= 2;                                                            // Average splitting ley
     }

    Branch splitLeafRoot()                                                      // Split a leaf into two leaves and a branch
     {final double sk = splittingKey();
      final Leaf l = duplicate(), r = l.splitRight();
      final Branch b = new Branch();
      b.insert(sk, l); b.top = r;
      return b;
     }

    Integer insert(double Key, double Data)                                     // Insert a key data pair into a leaf
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
      for (int i = 0; i < N; i++) d.append(String.format(" %3.1f", data[i]));
      return super.dump() + "data     : "+d+"\n";
     }

    void compactLeft()                                                          // Compact the leaf to the left
     {final int N = numberOfSlots(), R = numberOfRefs();
      final double[]d = new double[R];
      int p = 0;
      for (int i = 0; i < N; i++) if (usedSlots[i]) d[p++] = data[slots[i]];
      super.compactLeft();
      for (int i = 0; i < R; i++) data[i] = d[i];
     }

    void compactRight()                                                         // Compact the leaf to the right
     {final int N = numberOfSlots(), R = numberOfRefs();
      final double[]d = new double[R];
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
     }

    int splitSize() {return maxBranchSize / 2;}                                 // Size of a split branch

    Branch duplicate()                                                          // Duplicate a branch
     {final Branch d = new Branch();
      d.copy((Slots)this);                                                      // Copy slots
      for (int i = 0; i < numberOfRefs; i++) d.data[i] = data[i];               // Copy data associated wuth branch keys
      d.top = top;
      return d;
     }

    Split splitRight() {return splitRight(duplicate());}                        // Split a left branch into a new right branch

    class Split                                                                 // The result of splitting a branch
     {final double key;
      final Branch left, right;
      Split(double Key, Branch Left, Branch Right)
       {key = Key; left = Left; right = Right;
       }
     }

    Split splitRight(Branch Right)                                              // Split a left branch into an existing right branch
     {if (!full()) return null;                                                 // Only full branches can be split
      final int Count = splitSize();
      int s = 0;                                                                // Count slots used
      double sk = 0;                                                            // Splitting key
      for (int i = 0; i < numberOfSlots; i++)                                   // Each slot
       {if (usedSlots[i])                                                       // Slot is in use
         {if (s < Count)                                                        // Still in left branch
           {Right.clearSlotAndRef(i);                                           // Free the entry from the right branch as it is being used in the left branch
            s++;                                                                // Number of entries active in left branch
           }
          else if (s == Count)                                                  // Splitting key
           {sk  = keys[i];
            top = data[i];
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

    double splittingKey()                                                       // Splitting key from a branch
     {if (!full()) stop("Branch not full");                                     // The branch must be full if we are going to split it
      return key(splitSize());                                                 // The splitting key
     }

    Integer insert(double Key, Slots Data)                                      // Insert a key data pair into a branch
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
      return super.dump() + "data     :  "+d+"\ntop      :  "+top.name+"\n";
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

    void mergeData(double Key, Branch Left, Branch Right)                       // Merge the data from the compacted left and right slots
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

    boolean mergeOnRight(double Key, Branch Right)                              // Merge the specified slots from the right
     {if (countUsed() + Right.countUsed() > maxBranchSize) return false;
      final Branch l =       duplicate(),
                   r = Right.duplicate();
      l.compactLeft(); r.compactRight();
      mergeCompacted((Slots)l, (Slots)r);
      mergeData(Key, l, r);
      redistribute();
      return true;
     }

    boolean mergeOnLeft(double Key, Branch Left)                                // Merge the specified slots from the right
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
   }

//D1 Print                                                                      // Print the tree horizontally

  final int linesToPrintABranch =  4;                                           // The number of lines required to print a branch
  final int maxPrintLevels      =  3;                                           // The maximum number of levels to print `- this avoids endless print loops when something goes wrong

  void printLeaf(Leaf leaf, Stack<StringBuilder>P, int level)                   // Print leaf horizontally
   {padStrings(P, level);

    final StringJoiner s = new StringJoiner(",");
    for (int i = 0; i < leaf.numberOfSlots; i++)
     {if (leaf.usedSlots(i)) s.add(""+leaf.key(i));
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
        else
         {printBranch((Branch)s, P, level+1);
         }
        //final int key  = stuckKeys.memoryGet(BtreeIndex, i);
        //final int data = stuckData.memoryGet(BtreeIndex, i);

        P.elementAt(L+0).append(" "+branch.key(i));                             // Key
        //P.elementAt(L+1).append(""+BtreeIndex+(i > 0 ?  "."+i : ""));         // Branch,key, next pair
        //P.elementAt(L+2).append(""+stuckData.memoryGet(BtreeIndex, i));
       }
     }
    else                                                                        // Branch is empty so print just the index of the branch
     {//P.elementAt(L+0).append(""+BtreeIndex+"Empty");
     }
    //final int top = stuckData.memoryGet(BtreeIndex, K);                       // Top next will always be present
    //P.elementAt(L+3).append(top);                                             // Append top next

    if (branch.top instanceof Leaf)                                                    // Print leaf
     {printLeaf  (  (Leaf)branch.top, P, level+1);
     }
    else                                                                        // Print branch
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

  String print()                                                                // Print a tree horizontally
   {final Stack<StringBuilder> P = new Stack<>();
    if (root instanceof Leaf) printLeaf  ((Leaf)  root, P, 0);
    else                      printBranch((Branch)root, P, 0);
    return printCollapsed(P);
   }

//D1 Tests                                                                      // Test the btree

  final static int[]random_32 = {12, 3, 27, 1, 23, 20, 8, 18, 2, 31, 25, 16, 13, 32, 11, 21, 5, 24, 4, 10, 26, 30, 9, 6, 29, 17, 28, 15, 14, 19, 7, 22};

//  static void test_insert()
//   {final Tree t = new Tree(4, 3);
//    t.insert(1.1, 2.1);
//    ok(t, """
//""");
//   }

  static void test_compactLeafLeft()
   {final Leaf l = new Tree(8, 7).new Leaf();
    l.insert(1.3, 2.3);
    l.insert(1.2, 2.2);
    l.insert(1.4, 2.4);
    l.insert(1.1, 2.1);
    ok(l.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   0   0   3   1   0   2   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X   X   X   .   .   .   .   .   .
usedRefs :    X   X   X   X   .   .   .   .
keys     :  1.3 1.2 1.4 1.1 0.0 0.0 0.0 0.0
data     :  2.3 2.2 2.4 2.1 0.0 0.0 0.0 0.0
""");
    l.compactLeft();
    ok(l.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   1   2   3   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    X   X   X   X   .   .   .   .   .   .   .   .   .   .   .   .
usedRefs :    X   X   X   X   .   .   .   .
keys     :  1.1 1.2 1.3 1.4 0.0 0.0 0.0 0.0
data     :  2.1 2.2 2.3 2.4 0.0 0.0 0.0 0.0
""");
   }

  static void test_compactLeafRight()
   {final Leaf l = new Tree(8, 7).new Leaf();
    l.insert(1.3, 2.3);
    l.insert(1.2, 2.2);
    l.insert(1.4, 2.4);
    l.insert(1.1, 2.1);
    ok(l.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   0   0   3   1   0   2   0   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X   X   X   .   .   .   .   .   .
usedRefs :    X   X   X   X   .   .   .   .
keys     :  1.3 1.2 1.4 1.1 0.0 0.0 0.0 0.0
data     :  2.3 2.2 2.4 2.1 0.0 0.0 0.0 0.0
""");
    l.compactRight();
//  stop(l.dump());
    ok(l.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   4   5   6   7   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   X   X   X   X   .   .   .   .   .   .   .   .
usedRefs :    .   .   .   .   X   X   X   X
keys     :  0.0 0.0 0.0 0.0 1.1 1.2 1.3 1.4
data     :  0.0 0.0 0.0 0.0 2.1 2.2 2.3 2.4
""");
   }

  static void test_compactBranchLeft()
   {final Branch b = new Tree(8, 7).new Branch();
    b.insert(1.2, new Slots("1.2"));
    b.insert(1.1, new Slots("1.1"));
    b.insert(1.3, new Slots("1.3"));
    b.top = new Slots("  4");
    ok(b.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slots    :    0   0   0   0   0   0   1   0   2   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X   X   .   .   .   .   .
usedRefs :    X   X   X   .   .   .   .
keys     :  1.2 1.1 1.3 0.0 0.0 0.0 0.0
data     :  1.2 1.1 1.3   .   .   .   .
top      :    4
""");

    b.compactLeft();

    ok(b.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slots    :    0   1   2   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    X   X   X   .   .   .   .   .   .   .   .   .   .   .
usedRefs :    X   X   X   .   .   .   .
keys     :  1.1 1.2 1.3 0.0 0.0 0.0 0.0
data     :  1.1 1.2 1.3   .   .   .   .
top      :    4
""");
   }

  static void test_compactBranchRight()
   {final Branch b = new Tree(8, 7).new Branch();
    b.insert(1.2, new Slots("1.2"));
    b.insert(1.1, new Slots("1.1"));
    b.insert(1.3, new Slots("1.3"));
    b.top = new Slots("  4");
    ok(b.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slots    :    0   0   0   0   0   0   1   0   2   0   0   0   0   0
usedSlots:    .   .   .   .   .   .   X   X   X   .   .   .   .   .
usedRefs :    X   X   X   .   .   .   .
keys     :  1.2 1.1 1.3 0.0 0.0 0.0 0.0
data     :  1.2 1.1 1.3   .   .   .   .
top      :    4
""");

    b.compactRight();
    ok(b.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slots    :    0   0   0   0   4   5   6   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   X   X   X   .   .   .   .   .   .   .
usedRefs :    .   .   .   .   X   X   X
keys     :  0.0 0.0 0.0 0.0 1.1 1.2 1.3
data     :    .   .   .   . 1.1 1.2 1.3
top      :    4
""");
   }

  static Leaf test_leaf()
   {final Leaf l = new Tree(8, 7).new Leaf();
    final double   []d = new double[]{1.3, 1.6, 1.5, 1.8, 1.7, 1.4, 1.2, 1.1};
    for (int i = 0; i < d.length; i++) l.insert(d[i], d[i]);
    return l;
   }

  static void test_splitLeftLeafIntoRight()
   {final Leaf l = test_leaf();
    ok(l.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    7   6   0   0   5   0   2   0   1   0   4   0   3   0   0   0
usedSlots:    X   X   X   .   X   .   X   .   X   .   X   .   X   .   .   .
usedRefs :    X   X   X   X   X   X   X   X
keys     :  1.3 1.6 1.5 1.8 1.7 1.4 1.2 1.1
data     :  1.3 1.6 1.5 1.8 1.7 1.4 1.2 1.1
""");
    final Leaf r = l.splitRight();
    ok(l.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   6   0   0   0   0   0   0   0   5   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   X   .   .
usedRefs :    X   .   .   .   .   X   X   X
keys     :  1.3 1.6 1.5 1.8 1.7 1.4 1.2 1.1
data     :  1.3 1.6 1.5 1.8 1.7 1.4 1.2 1.1
""");
    ok(r.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   2   0   0   0   1   0   0   0   4   0   0   0   3   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   X   .   .
usedRefs :    .   X   X   X   X   .   .   .
keys     :  1.3 1.6 1.5 1.8 1.7 1.4 1.2 1.1
data     :  1.3 1.6 1.5 1.8 1.7 1.4 1.2 1.1
""");
    ok(l, """
keys: 1.1, 1.2, 1.3, 1.4
data: 1.1, 1.2, 1.3, 1.4
""");
    ok(r, """
keys: 1.5, 1.6, 1.7, 1.8
data: 1.5, 1.6, 1.7, 1.8
""");
   }

  static void test_splitRightLeafIntoLeft()
   {final Leaf r = test_leaf();
    final Leaf l = r.splitLeft();
    ok(l, """
keys: 1.1, 1.2, 1.3, 1.4
data: 1.1, 1.2, 1.3, 1.4
""");
    ok(r, """
keys: 1.5, 1.6, 1.7, 1.8
data: 1.5, 1.6, 1.7, 1.8
""");
   }

  static Branch test_branch()
   {final Branch b = new Tree(8, 7).new Branch();

    final double[]k = new double[]{1.3, 1.6, 1.5, 1.7, 1.4, 1.2, 1.1};
    final String[]d = new String[]{"  3", "  6", "  5", "  7", "  4", "  2", "  1"};
    for (int i = 0; i < d.length; i++) b.insert(k[i], new Slots(d[i]));
    b.top = new Slots("  8");
    return b;
   }

  static void test_splitLeftBranchIntoRight()
   {final Branch       b = test_branch();
    final Branch.Split s = b.splitRight();
    ok(s.left, """
keys: 1.1, 1.2, 1.3
data:   1,   2,   3
top :   4
""");
    ok(s.right, """
keys: 1.5, 1.6, 1.7
data:   5,   6,   7
top :   8
""");
    ok(s.key, 1.4);
   }

  static void test_splitRightBranchIntoLeft()
   {final Branch       r = test_branch();
    final Branch.Split s = r.splitLeft();
    ok(s.left, """
keys: 1.1, 1.2, 1.3
data:   1,   2,   3
top :   4
""");
    ok(s.right, """
keys: 1.5, 1.6, 1.7
data:   5,   6,   7
top :   8
""");
    ok(s.key, 1.4);
   }

  static Leaf test_leaf1()
   {final Leaf    l = new Tree(8,7).new Leaf();
    final double[]d = new double[]{1.3, 1.4, 1.2, 1.1};
    for (int i = 0; i < d.length; i++) l.insert(d[i], d[i]);
    return l;
   }

  static Leaf test_leaf2()
   {final Leaf    l = new Tree(8,7).new Leaf();
    final double[]d = new double[]{1.6, 1.5, 1.8, 1.7};
    for (int i = 0; i < d.length; i++) l.insert(d[i], d[i]);
    return l;
   }

  static void test_mergeLeafLeft()
   {final Leaf l = test_leaf1();
    final Leaf r = test_leaf2();
    l.mergeOnRight(r);
    ok(l.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   1   0   2   0   3   0   4   0   5   0   6   0   7   0
usedSlots:    X   .   X   .   X   .   X   .   X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X   X   X   X   X
keys     :  1.1 1.2 1.3 1.4 1.5 1.6 1.7 1.8
data     :  1.1 1.2 1.3 1.4 1.5 1.6 1.7 1.8
""");
   }

  static void test_mergeLeafRight()
   {final Leaf l = test_leaf1();
    final Leaf r = test_leaf2();
    r.mergeOnLeft(l);
    ok(r.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   1   0   2   0   3   0   4   0   5   0   6   0   7   0
usedSlots:    X   .   X   .   X   .   X   .   X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X   X   X   X   X
keys     :  1.1 1.2 1.3 1.4 1.5 1.6 1.7 1.8
data     :  1.1 1.2 1.3 1.4 1.5 1.6 1.7 1.8
""");
   }

  static Branch test_branch1()
   {final Branch b = new Tree(8, 7).new Branch();
    final double[]k = new double[]{ 1.3,   1.2,   1.1};
    final String[]d = new String[]{"  3", "  2", "  1"};
    for (int i = 0; i < k.length; i++) b.insert(k[i], new Slots(d[i]));
    b.top = new Slots("  4");
    return b;
   }

  static Branch test_branch2()
   {final Branch b = new Tree(8, 7).new Branch();
    final double[]k = new double[]{ 1.6,    1.5,  1.7};
    final String[]d = new String[]{"  6", "  5", "  7"};
    for (int i = 0; i < k.length; i++) b.insert(k[i], new Slots(d[i]));
    b.top = new Slots("  8");
    return b;
   }

  static void test_mergeBranchLeft()
   {final Branch l = test_branch1();
    final Branch r = test_branch2();
    l.mergeOnRight(1.4, r);
    ok(l.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slots    :    0   0   1   0   2   0   0   0   4   0   5   0   6   0
usedSlots:    X   .   X   .   X   .   X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X   X   X   X
keys     :  1.1 1.2 1.3 1.4 1.5 1.6 1.7
data     :    1   2   3   4   5   6   7
top      :    8
""");
   }

  static void test_mergeBranchRight()
   {final Branch l = test_branch1();
    final Branch r = test_branch2();
    r.mergeOnLeft(1.4, l);
    ok(r.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13
slots    :    0   0   1   0   2   0   0   0   4   0   5   0   6   0
usedSlots:    X   .   X   .   X   .   X   .   X   .   X   .   X   .
usedRefs :    X   X   X   X   X   X   X
keys     :  1.1 1.2 1.3 1.4 1.5 1.6 1.7
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

  static void test_splitLeafRoot()
   {final Branch b = test_leaf().splitLeafRoot();
    final Tree   t = b.tree();
    t.root = b;
    ok(t.print(), """
                1.45               |
1.1,1.2,1.3,1.4     1.5,1.6,1.7,1.8|
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
   }

  static void newTests()                                                        // Tests being worked on
   {test_splitLeafRoot();
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
