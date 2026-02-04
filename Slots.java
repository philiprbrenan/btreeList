//------------------------------------------------------------------------------
// Slots for key references
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.
import java.util.*;

class Slots extends Test                                                        // Manipulate a btree in a block of memory
 {final int numberOfSlots;                                                      // Number of slots in the bit slot
  final int[]slots;                                                             // Key order
  final boolean[]usedSlots;                                                     // Slots in use
  final boolean[]usedRefs;                                                      // Positions for keys

//D1 Construction                                                               // Construct and layout a btree

  Slots(int NumberOfSlots)                                                      // Create the Slots
   {numberOfSlots = NumberOfSlots;
    slots     = new int    [numberOfSlots];
    usedSlots = new boolean[numberOfSlots];
    usedRefs  = new boolean[numberOfSlots];
   }

  void setSlots(int...Slots)                                                    // Set these slots as used
   {for (int i = 0; i < Slots.length; i++) usedSlots[Slots[i]] = true;
   }

  void clearSlots(int...Slots)                                                  // Set these slots as not in being used
   {for (int i = 0; i < Slots.length; i++) usedSlots[Slots[i]] = false;
   }

  int allocRef()                                                                // Allocate a reference
   {for (int i = 0; i < numberOfSlots; i++)
     {if (!usedRefs[i])
       {usedRefs[i] = true;
        return i;
       }
     }
    stop("No more refs available");
    return -1;
   }

  void freeRef(int Ref) {usedRefs[Ref] = false;}                                 // Free a ref

//D1 Overrides                                                                  // Manipulators for objects validated by the slots slots

  void storeKey(int Ref) {}                                                     // Store the current key at this lcoatgion
  boolean    eq(int Ref) {return false;}                                        // Tell me if the indexed Key is equal to the search key
  boolean    le(int Ref) {return false;}                                        // Tell me if the indexed Key is less than or equal to the search key
  String getRef(int Ref) {return "";}                                           // Value of the referenced key as a string

//D1 State                                                                      // Query the stata eof the Manipulators for objects validated by the slots slots

  boolean empty()                                                               // True if the bit slots are all empty
   {for (int i = 0; i < numberOfSlots; i++) if ( usedSlots[i]) return false;
    return true;
   }

  boolean full()                                                                // True if the bit slots are all full
   {for (int i = 0; i < numberOfSlots; i++) if (!usedSlots[i]) return false;
    return true;
   }

  int countUsed()                                                               // Number or slots in use
   {int n = 0;
    for (int i = 0; i < numberOfSlots; i++) if (usedSlots[i]) ++n;
    return n;
   }

//D1 Manipulate                                                                 // Manipulate the bit slot

  Integer locateNearestFreeSlot(int Position)                                   // Relative position of the nearest free slot to the indicated position if there is one.
   {if (!usedSlots[Position]) return 0;                                         // The slot is free already. If it it not free we do art least get an error if the specified positoin is invalid
    for (int i = 1; i < numberOfSlots; i++)
     {final int p = Position + i, q = Position - i;
      if (q >= 0            && !usedSlots[q]) return -i;                        // Look down preferentially to avoid mving the existing key if possible
      if (p < numberOfSlots && !usedSlots[p]) return +i;                        // Look up
     }
    return null;                                                                // No free slot - this is not actually an error.
   }

  void shift(int Position, int Width)                                           // Shift the specified number of slots around the specified position one bit left or right depending on teh sign of the width
   {if (Width > 0)                                                              // Shift up including the current slot
     {for (int i = Width; i > 0; --i)
       {final int p = Position+i;
        slots[p] = slots[p-1];
        usedSlots[p] = true;
       }
     }
    else if (Width < 0)                                                         // Shift the preceding slots down.  This reduces the number of moves needed to insert keys in ascending order
     {for (int i = Width; i < 0; ++i)
       {final int p = Position+i;
        slots[p] = slots[p+1];                                                  // We only move occupied slots
        usedSlots[p] = true;
       }
     }
   }

  void redistribute()                                                           // Redistribute the unused slots evenly with a slight bias to having a free slot at the end to assist with data previously sorted into ascending order
   {if (empty()) return;
    final int      N = numberOfSlots, c = countUsed(), space = (N - c) / c,     // Space between used slots
               cover = (space+1)*(c-1)+1, remainder = max(0, N - cover);        // Covered space from first used slot to last used slot, uncovered remainder
    final int    []s = new int    [numberOfSlots];                              // New slots distribution
    final boolean[]u = new boolean[numberOfSlots];                              // New used slots distribution
    int p = remainder / 2;                                                      // Start position for first used slt
    for (int i = 0; i < numberOfSlots; ++i)                                     // Redistribute slots
     {if (usedSlots[i])                                                         // Redistribute active slots
       {s[p] = slots[i]; u[p] = true; p += space+1;                             // Spread the used slots out
       }
     }
    for(int i = 0; i < numberOfSlots; ++i)                                      // Copy redistribution back into original
     {slots[i] = s[i]; usedSlots[i] = u[i];
     }
   }

  boolean insert()                                                              // Insert the current search key maintaining the order of the keys
   {if (full()) return false;                                                   // No space in which to insert
    final int ref = allocRef();                                                 // Location to store key
    storeKey(ref);                                                              // Tell the caller to store the key in the indexed location
    for (int i = 0; i < numberOfSlots; ++i)                                     // Search for the first greater than or equal key
     {if (usedSlots[i])                                                         // Valid slot
       {if (le(slots[i]))                                                       // First key we are less than or equal to
         {final int w = locateNearestFreeSlot(i);                               // Width of move and direction needed to make a slot here
          if (w > 0)                                                            // Move up liberating a sopace at the Spacve
           {shift(i, w);                                                        // Make a slot at this point
            slots[i] = ref;                                                     // Place the current key in the empty slot and mark it as set
           }
          else if (w < 0)                                                       // Make a slot below the current slot
           {shift(i-1, w + 1);                                                  // Shift any intervening slots blocking the slot below
            slots[i-1] = ref;                                                   // Insert into the slot below
            usedSlots[i-1] = true;
           }
          return true;                                                          // Sucessfully inserted
         }
       }
     }
    final int last = numberOfSlots - 1;                                         // Bigger than all keys so place at the end
    shift(last, locateNearestFreeSlot(last));                                   // Create an empty slot if needed
    slots    [last] = ref;                                                      // Insert key in last slot
    usedSlots[last] = true;                                                     // Insert key in last slot
    return true;                                                                // Success
   }

//D1 Print                                                                      // Print the bit slot

  String printSlots()                                                           // Print the occupancy of each slot
   {final StringBuilder s = new StringBuilder();
    for (int i = 0; i < numberOfSlots; i++) s.append(usedSlots[i] ? "X" : ".");
    return ""+s;
   }

  public String toString()                                                      // Print the valus in the used slots
   {final StringJoiner s = new StringJoiner(", ");
    for (int i = 0; i < numberOfSlots; i++)
     {if (usedSlots[i]) s.add(getRef(slots[i]));
     }
    return ""+s;
   }

//D1 Tests                                                                      // Test the bit slot

  static void test_locateNearestFreeSlot()
   {final Slots b = new Slots(16);
    b.setSlots(2, 3, 5, 6, 7, 9, 11, 13);
                      //0123456789012345
    ok(b.printSlots(), "..XX.XXX.X.X.X..");
    ok(b.locateNearestFreeSlot(1),  0);
    ok(b.locateNearestFreeSlot(2), -1);
    ok(b.locateNearestFreeSlot(3), +1);
    ok(b.locateNearestFreeSlot(4),  0);
    ok(b.locateNearestFreeSlot(5), -1);
    ok(b.locateNearestFreeSlot(6), -2);
    ok(b.locateNearestFreeSlot(7), +1);
   }

  static void test_redistribute()
   {final Slots b = new Slots(16);
    for (int i = 0; i < b.numberOfSlots; i++) b.setSlots(i);
                                                         //0123456789012345
                                       ok(b.printSlots(), "XXXXXXXXXXXXXXXX");
                     b.redistribute(); ok(b.printSlots(), "XXXXXXXXXXXXXXXX");
    b.clearSlots(0); b.redistribute(); ok(b.printSlots(), "XXXXXXXXXXXXXXX.");
    b.clearSlots(0); b.redistribute(); ok(b.printSlots(), ".XXXXXXXXXXXXXX.");
    b.clearSlots(1); b.redistribute(); ok(b.printSlots(), ".XXXXXXXXXXXXX..");
    b.clearSlots(1); b.redistribute(); ok(b.printSlots(), "..XXXXXXXXXXXX..");
    b.clearSlots(2); b.redistribute(); ok(b.printSlots(), "..XXXXXXXXXXX...");
    b.clearSlots(2); b.redistribute(); ok(b.printSlots(), "...XXXXXXXXXX...");
    b.clearSlots(3); b.redistribute(); ok(b.printSlots(), "...XXXXXXXXX....");
    b.clearSlots(3); b.redistribute(); ok(b.printSlots(), "X.X.X.X.X.X.X.X.");
    b.clearSlots(0); b.redistribute(); ok(b.printSlots(), ".X.X.X.X.X.X.X..");
    b.clearSlots(1); b.redistribute(); ok(b.printSlots(), "..X.X.X.X.X.X...");
    b.clearSlots(2); b.redistribute(); ok(b.printSlots(), ".X..X..X..X..X..");
    b.clearSlots(1); b.redistribute(); ok(b.printSlots(), ".X...X...X...X..");
    b.clearSlots(1); b.redistribute(); ok(b.printSlots(), "..X....X....X...");
    b.clearSlots(2); b.redistribute(); ok(b.printSlots(), "...X.......X....");
    b.clearSlots(3); b.redistribute(); ok(b.printSlots(), ".......X........");
    b.clearSlots(7); b.redistribute(); ok(b.printSlots(), "................");
   }

  static void test_redistribute_odd()
   {final Slots b = new Slots(15);
    for (int i = 0; i < b.numberOfSlots; i++) b.setSlots(i);
                                                         //012345689012345
                                       ok(b.printSlots(), "XXXXXXXXXXXXXXX");
                     b.redistribute(); ok(b.printSlots(), "XXXXXXXXXXXXXXX");
    b.clearSlots(0); b.redistribute(); ok(b.printSlots(), "XXXXXXXXXXXXXX.");
    b.clearSlots(0); b.redistribute(); ok(b.printSlots(), ".XXXXXXXXXXXXX.");
    b.clearSlots(1); b.redistribute(); ok(b.printSlots(), ".XXXXXXXXXXXX..");
    b.clearSlots(1); b.redistribute(); ok(b.printSlots(), "..XXXXXXXXXXX..");
    b.clearSlots(2); b.redistribute(); ok(b.printSlots(), "..XXXXXXXXXX...");
    b.clearSlots(2); b.redistribute(); ok(b.printSlots(), "...XXXXXXXXX...");
    b.clearSlots(3); b.redistribute(); ok(b.printSlots(), "...XXXXXXXX....");
    b.clearSlots(3); b.redistribute(); ok(b.printSlots(), ".X.X.X.X.X.X.X.");
    b.clearSlots(1); b.redistribute(); ok(b.printSlots(), "..X.X.X.X.X.X..");
    b.clearSlots(2); b.redistribute(); ok(b.printSlots(), ".X..X..X..X..X.");
    b.clearSlots(1); b.redistribute(); ok(b.printSlots(), "..X..X..X..X...");
    b.clearSlots(2); b.redistribute(); ok(b.printSlots(), "..X....X....X..");
    b.clearSlots(2); b.redistribute(); ok(b.printSlots(), "...X......X....");
    b.clearSlots(3); b.redistribute(); ok(b.printSlots(), ".......X.......");
    b.clearSlots(7); b.redistribute(); ok(b.printSlots(), "...............");
   }

  static void test_less()
   {final int    N = 8;
    final float[]F = new float[N];
          float[]K = new float[1];

    final Slots b = new Slots(N)
     {void storeKey(int Ref) {F[Ref] = K[0];}                                   // Store the current key at this lcoatgion
      boolean    eq(int Ref) {return F[Ref] == K[0];}                           // Tell me if the indexed Key is equal to the search key
      boolean    le(int Ref) {return F[Ref] >= K[0];}                           // Tell me if the indexed Key is less than or equal to the search key
      String getRef(int Ref) {return ""+F[Ref];}                                // Value of the referenced key as a string
     };

                              ok(b.empty(), true);  ok(b.full(), false);
    K[0] = 1.4f; b.insert();  ok(b.empty(), false); ok(b.full(), false);
    K[0] = 1.3f; b.insert();  ok(b.countUsed(), 2);
    K[0] = 1.6f; b.insert();
    K[0] = 1.5f; b.insert();
    K[0] = 1.8f; b.insert();
    K[0] = 1.7f; b.insert();
    K[0] = 1.2f; b.insert();
    K[0] = 1.1f; b.insert();  ok(b.empty(), false); ok(b.full(), true);
    ok(b, "1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8");
  }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_locateNearestFreeSlot();
    test_redistribute();
    test_redistribute_odd();
    test_less();
   }

  static void newTests()                                                        // Tests being worked on
   {test_locateNearestFreeSlot();
    test_redistribute();
    test_redistribute_odd();
    test_less();
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
