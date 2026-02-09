//------------------------------------------------------------------------------
// Btree with stucks implemented as slots
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Ttree in a block on the surface of a silicon chip.
import java.util.*;

class Tree extends Test                                                         // Manipulate a tree
 {final int           maxLeafSize;                                              // The maximum number of entries in a leaf - the number of slots in a leaf and one more than the number of slots in a branch
  Slots.LeafOrBranch         root;                                              // The root of the tree
  final int MaximumNumberOfLevels = 99;                                         // Maximum number of levels in tree
  boolean           suppressMerge = false;                                      // Suppress merges during put to allow merge steps to be tested individually.  If this is on the trees built for testing are already merged so there is nothing to test.
  static boolean  createTestTrees = false;                                      // Create trees to assist testing
  static boolean            debug = false;                                      // Debug if enabled

//D1 Construction                                                               // Construct and layout a tree

  Tree(int MaxLeafSize)                                                         // Create the tree
   {if (MaxLeafSize % 2 == 1) stop("The stuck size must be even, not:", MaxLeafSize);
    if (MaxLeafSize < 4)      stop("The stuck size must be greater than equal to 4, not:", MaxLeafSize);

    maxLeafSize = MaxLeafSize;                                                  // The maximum number of entries in a leaf
   }

//D1 Low level operations                                                       // Low level operations

  int leafSize()   {return maxLeafSize;}                                        // Maximum size of a leaf
  int branchSize() {return maxLeafSize-1;}                                      // Maximum size of a branch

  double leafSplittingKey(Slots.Leaf Leaf)                                      // Splitting key from a leaf
   {final Slots l = Leaf.parentSlots;
    if (!l.full()) stop("Leaf not full");                                       // The leaf must be full if we are going to split it
    final int   m = leafSize() / 2;                                             // Index of mid point
    return (l.keys[l.slots[m-1]]+l.keys[l.slots[m]]) / 2;                       // The splitting key
   }

  double branchSplittingKey(Slots.Branch Branch)                                // Splitting key from a branch
   {final Slots b = Branch.parentSlots;
    if (!b.full()) stop("Branch not full");                                     // The branch must be full if we are going to split it
    final int   m = branchSize() / 2;                                           // Index of mid point
    return b.keys[b.slots[m]];                                                  // The splitting key
   }

  Slots.LeafOrBranch stepDown(double Key, Slots.Branch Branch)                  // Step down from a branch to the next level down
   {return Branch.child(Branch.parentSlots.locateFirstGe(Key));
   }

  boolean split(Slots.Branch Parent, Integer Index)                             // Split the indexed child of the specified branch. Split top if the index is null
   {final Slots p = Parent.parentSlots;
    if (p.full()) stop("Attempting to split the child of a full parent");       // Programming error
    final Slots.LeafOrBranch c = Parent.child(Index);                           // The indexed child
    if (!c.full()) return false;                                                // Cannot split child unless it is full

    if (!(c instanceof Slots.Leaf) && !(c instanceof Slots.Branch))             // If it is not a leaf or a branch something has gone wrong
     {stop("Invalid object in tree, not a leaf or a branch:",
       root.getClass().getName());
     }

    if (c instanceof Slots.Leaf)                                                // Split a leaf
     {final Slots.Leaf r = (Slots.Leaf)c;
      final double k = leafSplittingKey(r);
      final Slots.Leaf l = r.splitLeft(leafSize() / 2);
      Parent.insert(k, l);
      return true;
     }

    if (c instanceof Slots.Branch)                                              // Split a branch
     {final Slots.Branch r = (Slots.Branch)c;
      final double k = branchSplittingKey(r);
      final Slots.Branch.Split l = r.splitLeft(branchSize() / 2);
      Parent.insert(k, l.left);
      return true;
     }
    stop("Should not happen"); return false;
   }

//D1 High level operations                                                      // Insert find delete operations on the tree

  void insert(double Key, double Data)                                          // Insert a key value pair
   {if ((root != null) &&
       !(root instanceof Slots.Leaf) &&
       !(root instanceof Slots.Branch))
     {stop("Root must be empty, or a leaf or a branch, not a: ",
       root.getClass().getName());                                                                                 //
     }

    if (root == null)                                                           // Empty tree
     {root = Slots.Leaf(maxLeafSize);
      final Slots.Leaf r = (Slots.Leaf) root;
      r.insert(Key, Data);
      return;
     }
    if (root instanceof Slots.Leaf)                                             // Tree is a single leaf
     {final Slots.Leaf R = (Slots.Leaf) root;
      if (R.insert(Key, Data) != null) return;                                  // Sufficient space to insert into root leaf
      final Slots.Leaf l = R;
      final Slots.Leaf r = l.splitRight(maxLeafSize / 2);                       // Split the root leaf
      final Slots     sl = l.parentSlots;
      final Slots     sr = r.parentSlots;
      final Slots.Branch b = Slots.Branch(maxLeafSize-1);                       // The root will be a branch
      final double ll = sl.keys[sl.slots[sl.locateLastUsedSlot()]];
      final double rf = sr.keys[0];
      root = b; b.insert((ll+rf)/2, l); b.top = r;                              // The root now points to the two leaves
      return;
     }
   }

//D1 Tests                                                                      // Test the btree

  final static int[]random_32 = {12, 3, 27, 1, 23, 20, 8, 18, 2, 31, 25, 16, 13, 32, 11, 21, 5, 24, 4, 10, 26, 30, 9, 6, 29, 17, 28, 15, 14, 19, 7, 22};

  static void oldTests()                                                        // Tests thought to be in good shape
   {
   }

  static void newTests()                                                        // Tests being worked on
   {
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
