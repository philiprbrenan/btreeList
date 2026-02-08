//------------------------------------------------------------------------------
// Btree with stucks implemented as slots
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Ttree in a block on the surface of a silicon chip.
import java.util.*;

class Tree extends Test                                                         // Manipulate a tree
 {final int           maxLeafSize;                                              // The maximum number of entries in a leaf
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

//D1 High level operations                                                      // Inserm find delete operations on the tree

  void insert(double Key, double Data)                                          // Insert a key value pair
   {if ((root != null) &&
       !(root instanceof Slots.Leaf) &&
       !(root instanceof Slots.Branch))
     {stop("Root nust be empty, or a leaf or a branch, not a: ",
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
