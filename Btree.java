//------------------------------------------------------------------------------
// Btree with stucks implemented as indexed lists
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.
import java.util.*;

class Btree extends Test                                                        // Manipulate a btree in a block of memory
 {final int      maxStuckSize;                                                  // The maximum number of entries in the stuck.
  boolean        suppressMerge    = false;                                      // Suppress merges during put to allow merge steps to be tested individually.  If this is on the trees built for testing are already merged so there is nothing to test.
  static boolean createTestTrees  = false;                                      // Create trees to assist testing
  static boolean debug            = false;                                      // Debug if enabled

//D1 Construction                                                               // Construct and layout a btree

  Btree(int MaxStuckSize)                                                       // Create the Btree
   {if (MaxStuckSize % 2 == 1) stop("The stuck size must be even, not:", MaxStuckSize);
    if (MaxStuckSize < 4)      stop("The stuck size must be greater than equal to 4, not:", MaxStuckSize);

    maxStuckSize = MaxStuckSize;                                                // The maximum number of entries in the stuck.
   }

//D2 Stuck                                                                      // Get and set stucks within btree

  int stuckNumber = 0;                                                          // A unique number for each stuck

  class Stuck                                                                   // A stuck is a node of the Btree
   {final int number;                                                           // Stuck number
    final int []pairs;                                                          // The key, data pair indices in key order
    final Key []keys;                                                           // Keys
    final Data[]data;                                                           // Data
    int size;                                                                   // Number of active key, data pirs in this stuck
    int freeChainHead;                                                          // Head of the free chain
    final int[]freeChain;                                                       // The free chain

    Stuck()                                                                     // Mirror a stuck in memory with one in registers.
     {number = stuckNumber++;                                                   // Assign a unique number to each stuck to help track it during debugging
      size = 0; freeChainHead = 0;
      pairs     = new int [maxStuckSize];                                       // The allocated key, data pairs in key order
      keys      = new Key [maxStuckSize];                                       // Keys
      data      = new Data[maxStuckSize];                                       // Data
      freeChain = new int [maxStuckSize];                                       // Free chain
      freeChain[maxStuckSize-1] = -1;                                           // End of chain
      for (int i = 0; i < maxStuckSize-1; i++) freeChain[i] = i + 1;            // Create free chain
      for (int i = 0; i < maxStuckSize;   i++) keys[i] = new Key (0);           // Create empty keys
      for (int i = 0; i < maxStuckSize;   i++) data[i] = new Data(0);           // Create empty data
     }

    class Key                                                                   // Key used in the stuck
     {int value;
      Key(int Key) {value = Key;}
      boolean equals(Key that)                                                  // Whether two keys are equal
       {return value == that.value;
       }
      boolean lessThan(Key that)                                                // Whether this key is less than the specified key
       {return value <= that.value;
       }
      void set(Key Key)                                                         // Set the value of this key element
       {value = Key.value;
       }
      public String toString()                                                  // Key as a string
       {return "Key="+value+"\n";
       }
     }

    class Data                                                                  // Data used in the stuck
     {int value;
      Data(int Data) {value = Data;}
      void set(Data Data)                                                       // Set the value of this data element
       {value = Data.value;
       }
      public String toString()                                                  // Data as a string
       {return "Data"+value+"\n";
       }
     }

    void confirmNotFull()                                                       // Confirm that the stuck is not full
     {if (size >= maxStuckSize) stop("Stuck  full");
     }

    int allocatePair()                                                          // Allocate a key, data pair index
     {confirmNotFull();
      final int a = freeChainHead, b = freeChain[a];                            // First two elements of free chain
      freeChainHead = b;
      size++;                                                                   // New key, data pair in active use
      return a;
     }

    void freePair(int Pair)                                                     // Free a key, data pair index
     {freeChain[Pair] = freeChainHead; freeChainHead = Pair;
      size--;                                                                   // New key, data pair removed from active sue
     }

    void shiftUp(int Index)                                                     // Shift up from the indicated index
     {for(int i = maxStuckSize-1; i > Index; --i) pairs[i] = pairs[i-1];
     }

    void shiftDown(int Index)                                                   // Shift down to the indicated index
     {for(int i = Index; i < maxStuckSize-1; ++i) pairs[i] = pairs[i+1];
     }

    void insert(Key Key, Data Data)                                             // Insert a key, data pair in the stuck
     {confirmNotFull();
      for (int i = 0; i < size; i++)
       {final int  p = pairs[i];
        final Key  k = keys[p];
        final Data d = data[p];
        if (k.equals(Key))                                                      // Found an equal key - update corresponding data
         {d.set(Data);
          return;
         }
        if (Key.lessThan(k))                                                    // Found first greater key
         {final int index = allocatePair();                                     // Allocate a new key, data pair
          shiftUp(i);                                                           // Move higher indices up one
          pairs[i] = index;                                                     // Index allocated key,data pair
          keys[index].set(Key);                                                 // Insert new key
          data[index].set(Data);                                                // Insert new data
          return;
         }
       }
      final int index = allocatePair();                                         // Allocate a new key, data pair

      pairs[size-1] = index;                                                    // Index allocated key,data pair
      final Key  k = keys[index];
      final Data d = data[index];
      k.set(Key);                                                               // Insert new key
      d.set(Data);                                                              // Insert new data
     }

    Data remove(Key Key)                                                        // Remove the indicated key if it exists
     {for (int i = 0; i < size; i++)
       {final int  p = pairs[i];
        final Key  k = keys[p];
        final Data d = data[p];
        if (k.equals(Key))                                                      // Found an equal key - update corresponding data
         {freePair(p);                                                          // Free this key, data pair
          shiftDown(i);                                                         // Move higher indices down to elimate this position
          return d;
         }
       }
      return null;
     }

    public String toString()
     {final StringBuilder s = new StringBuilder();
      s.append("Stuck("+number+"): size="+size);

      if (freeChainHead > -1)
       {s.append("  Free="+freeChainHead);
        for (int i = 0, p = freeChain[freeChainHead];
            i < maxStuckSize && p > -1;
            ++i, p = freeChain[p])
         {s.append(", "+p);
         }
       }
      s.append("\n");

      final int N = size;
      for (int i = 0; i < N; i++)
       {final int  p = pairs[i];
        final Key  k = keys[p];
        final Data d = data[p];
        s.append(String.format("%02d  %4d: %d\n", i, k.value, d.value));
       }
      return ""+s;
     }
   }

//D1 Tests                                                                      // Test the btree

  final static int[]random_32 = {12, 3, 27, 1, 23, 20, 8, 18, 2, 31, 25, 16, 13, 32, 11, 21, 5, 24, 4, 10, 26, 30, 9, 6, 29, 17, 28, 15, 14, 19, 7, 22};

  static void stuckLoad()
   {final Btree b = new Btree(4);
    final Stuck s = b.new Stuck();
    s.insert(s.new Key(1), s.new Data(1));
    s.insert(s.new Key(2), s.new Data(22));
    s.insert(s.new Key(3), s.new Data(333));
    ok(s, """
Stuck(0): size=3  Free=3
00     1: 1
01     2: 22
02     3: 333
""");
    s.remove(s.new Key(2));
    s.insert(s.new Key(4), s.new Data(4444));
    ok(s, """
Stuck(0): size=3  Free=3
00     1: 1
01     3: 333
02     4: 4444
""");
    s.remove(s.new Key(3));
    ok(s, """
Stuck(0): size=2  Free=2, 3
00     1: 1
01     4: 4444
""");
    s.insert(s.new Key(2), s.new Data(22));
    ok(s, """
Stuck(0): size=3  Free=3
00     1: 1
01     2: 22
02     4: 4444
""");
    s.insert(s.new Key(3), s.new Data(333));
    ok(s, """
Stuck(0): size=4
00     1: 1
01     2: 22
02     3: 333
03     4: 4444
""");
   }


  static void oldTests()                                                        // Tests thought to be in good shape
   {stuckLoad();
   }

  static void newTests()                                                        // Tests being worked on
   {stuckLoad();
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
