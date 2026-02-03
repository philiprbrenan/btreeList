//------------------------------------------------------------------------------
// Slotted bit list
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.
import java.util.*;

class BitSlot extends Test                                                      // Manipulate a btree in a block of memory
 {final int numberOfBits;                                                       // Number of bits in the bit slot
  final boolean[]bits;                                                          // Whether each bit is in use or not
  static boolean debug = false;                                                 // Debug if enabled

//D1 Construction                                                               // Construct and layout a btree

  BitSlot(int NumberOfBits)                                                     // Create the BitSlot
   {numberOfBits = NumberOfBits;
    bits = new boolean[numberOfBits];
   }

  void set(int I, boolean value) {bits[I] = value;}                             // Set a bit in the bit slot to a specified value
  void set(int I)                {set(I, true);}                                // Set a bit in the bit slot

//D1 Overrides                                                                  // Manipulators for objects validated by the bits slots

  void shifter   (int To, int From) {}                                          // You should shift from From to To
  boolean eq     (int At)           {return false;}                             // Tell me if the specified element is equal to the search key
  boolean le     (int At)           {return false;}                             // Tell me if the specified element is less than or equal to the search key
  void setElement(int At) {}                                                    // Set the specified element to the current key

//D1 State                                                                      // Query the stata eof the Manipulators for objects validated by the bits slots

  boolean empty()                                                               // True if the bit slots are all empty
   {for (int i = 0; i < numberOfBits; i++) if ( bits[i]) return false;
    return true;
   }

  boolean full()                                                                // True if the bit slots are all full
   {for (int i = 0; i < numberOfBits; i++) if (!bits[i]) return false;
    return true;
   }

//D1 Manipulate                                                                 // Manipulate the bit slot

  Integer locateNearestFreeSlot(int Position)                                   // Relative position of the nearest free slot to the indicated position if there is one.
   {if (!bits[Position]) return 0;                                              // The slot is free already. If it it not free we do art least get an error if the specified positoin is invalid
    for (int i = 0; i < numberOfBits; i++)
     {final int p = Position + i, q = Position - i;
      if (q >= 0           && !bits[q]) return -i;                              // Look down preferentially to avoid mving the existing key if possible
      if (p < numberOfBits && !bits[p]) return +i;                              // Look up
     }
    return null;                                                                // No free slot - this is not actually an error.
   }

  void shift(int Position, int Width)                                           // Shift the specified number of bits around the specified position one bit left or right depending on teh sign of the width
   {if (Width > 0)                                                              // Shift up including the current slot
     {for (int i = Width; i > 0; --i)
       {final int p = Position+i;
        bits[p] = true;                                                         // We only move occupied slots
        shifter(p, p-1);
       }
     }
    else if (Width < 0)                                                         // Shift the preceding slots down.  This reduces the number of moves needed to insert keys in ascending order
     {for (int i = Width; i < 0; ++i)
       {final int p = Position+i;
        bits[p] = true;                                                         // We only move occupied slots
        shifter(p, p+1);
       }
     }
   }

  boolean insert()                                                              // Insert the current search key maintaining the order of the keys
   {if (full()) return false;                                                   // No space in which to insert
    for (int i = 0; i < numberOfBits; ++i)                                      // Search for the first greater than or equal key
     {if (bits[i])                                                              // Valid element
       {if (le(i))                                                              // First key we are less than or equal to
         {final int w = locateNearestFreeSlot(i);                               // Width of move and direction needed to make a slot here
          if (w > 0)                                                            // Move up liberating a sopace at the Spacve
           {shift(i, w);                                                        // Make a slot at this point
            setElement(i); set(i);                                              // Place the current key in the empty slot and mark it as set
           }
          else if (w < 0)                                                       // Make a slot below the current slot
           {shift(i-1, w + 1);                                                  // Shift any intervening slots blocking the slot below
            setElement(i-1); set(i-1);                                          // Insert into the slot below
           }
          return true;                                                          // Sucessfully inserted
         }
       }
     }
    final int last = numberOfBits - 1;                                          // Bigger than all keys so place at the end
    shift(last, locateNearestFreeSlot(last));                                   // Create an emty slot of needed
    setElement(last); set(last);                                                // Insert key in last slot
    return true;                                                                // Success
   }

//D1 Print                                                                      // Print the bit slot

  public String toString()
   {final StringBuilder s = new StringBuilder();
    for (int i = 0; i < numberOfBits; i++)
     {s.append(bits[i] ? "X" : ".");
     }
    return ""+s;
   }

//D1 Tests                                                                      // Test the bit slot

  static BitSlot load()
   {final BitSlot b = new BitSlot(16);
    b.set(2); b.set(3);
    b.set(5); b.set(6); b.set(7);
    b.set(9); b.set(11); b.set(13);
    return b;
   }

  static void test_load()
   {final BitSlot b = load();
    ok(b, "..XX.XXX.X.X.X..");
    ok(b.locateNearestFreeSlot(1),  0);
    ok(b.locateNearestFreeSlot(2), -1);
    ok(b.locateNearestFreeSlot(3), +1);
    ok(b.locateNearestFreeSlot(4),  0);
    ok(b.locateNearestFreeSlot(5), -1);
    ok(b.locateNearestFreeSlot(6), +2);
    ok(b.locateNearestFreeSlot(7), +1);
   }

  static void test_less()
   {final int  N = 8;
    final int[]n = new int[N];
    Integer[]Key = new Integer[1];

    final BitSlot b = new BitSlot(N)
     {void    shifter(int To, int From) {n[To] = n[From];}
      void setElement(int At)           {n[At] = Key[0];}
      boolean      le(int At)           {return Key[0] <= n[At];}
     };

    ok(b.empty(), true);
    ok(b.full(),  false);
    b.set(3); b.set(4);
        n[3] = 4; n[4] = 6;
    //     01234567
    //        46
    ok(b, "...XX...");  ok(n, new int[]{0, 0, 0, 4, 6, 0, 0, 0});

    Key[0] = 5; b.insert();
    //     01234567
    //        456
    ok(b, "...XXX..");  ok(n, new int[]{0, 0, 0, 4, 5, 6, 0, 0});

    Key[0] = 3; b.insert();
    //     01234567
    //       3456
    ok(b, "..XXXX.."); ok(n, new int[]{0, 0, 3, 4, 5, 6, 0, 0});

    Key[0] = 8; b.insert();
    //     01234567
    //       3456 8
    ok(b, "..XXXX.X"); ok(n, new int[]{0, 0, 3, 4, 5, 6, 0, 8});

    Key[0] = 7; b.insert();
    //     01234567
    //       345678
    ok(b, "..XXXXXX"); ok(n, new int[]{0, 0, 3, 4, 5, 6, 7, 8});

    Key[0] = 2; b.insert();
    //     01234567
    //      2345678
    ok(b, ".XXXXXXX"); ok(n, new int[]{0, 2, 3, 4, 5, 6, 7, 8});
    ok(b.empty(), false);
    ok(b.full(),  false);

    Key[0] = 1; b.insert();
    //     01234567
    //     12345678
    ok(b, "XXXXXXXX"); ok(n, new int[]{1, 2, 3, 4, 5, 6, 7, 8});
    ok(b.empty(), false); ok(b.full(),  true);
  }

  static void test_less_from_empty()
   {final int  N = 4;
    final int[]n = new int[N];
    Integer[]Key = new Integer[1];

    final BitSlot b = new BitSlot(N)
     {void shifter(int To, int From) {n[To] = n[From];}
      void setElement(int At) {n[At] = Key[0];}
      boolean le(int At) {return Key[0] <= n[At];}
     };

    Key[0] = 3; b.insert(); ok(b, "...X");
    Key[0] = 1; b.insert(); ok(b, "..XX");
    Key[0] = 2; b.insert(); ok(b, ".XXX");
    Key[0] = 4; b.insert(); ok(b, "XXXX");

    ok(n, new int[]{1, 2, 3, 4});
   }

  static void test_shift()
   {final BitSlot b = load();
    ok(b, "..XX.XXX.X.X.X..");
    b.shift(2, b.locateNearestFreeSlot(2)); ok(b, ".X.X.XXX.X.X.X..");
    b.shift(5, b.locateNearestFreeSlot(5)); ok(b, ".X.XX.XX.X.X.X..");
    b.shift(7, b.locateNearestFreeSlot(7)); ok(b, ".X.XX.X.XX.X.X..");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_load();
    test_shift();
    test_less();
   }

  static void newTests()                                                        // Tests being worked on
   {test_less();
    test_less_from_empty();
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
