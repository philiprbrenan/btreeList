//------------------------------------------------------------------------------
// Maintain key references in ascending order using distributed slots
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//------------------------------------------------------------------------------
// Add random inserts/deletes to stress locate/insert/delete
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.
import java.util.*;

public class Slots extends Test                                                 // Maintain key references in ascending order using distributed slots
 {final int      numberOfSlots;                                                 // Number of slots
  final int    []slots;                                                         // Key ordering
  final boolean[]usedSlots;                                                     // Slots in use. I could have used BitSet but this would hide implementation details. Writing the code makes the actions explicit.
  final boolean[]usedRefs;                                                      // Index of each key in their storage. This index is stable even when the slots are redistributed to make it insertions faster
  final int      redistributeFrequency;                                         // Call redistribute after this many actions
  final Object   userSpace;                                                     // Space in which the user might store date to respond to the overrideable methods
  int actions = 0;                                                              // Number of actions performed

//D1 Construction                                                               // Construct and layout the slots

  public Slots(int NumberOfSlots, Object UserSpace)                                               // Create the slots
   {numberOfSlots         = NumberOfSlots;
    redistributeFrequency = (int)java.lang.Math.sqrt(numberOfSlots);            // Call redistribute after this many actions
    userSpace = UserSpace;
    slots     = new int    [numberOfSlots];
    usedSlots = new boolean[numberOfSlots];
    usedRefs  = new boolean[numberOfSlots];
   }

  private Slots(int NumberOfSlots) {this(NumberOfSlots,  null);}                // Make a new set of slots the same size as the current set


  private Slots like(Object UserSpace)                                          // Make a new set of slots the same size as the current set
   {return new Slots(numberOfSlots,  UserSpace);
   }

  private void setSlots(int...Slots)                                            // Set slots as used
   {for (int i = 0; i < Slots.length; i++) usedSlots[Slots[i]] = true;
   }

  private void clearSlots(int...Slots)                                          // Set slots as not being used
   {for (int i = 0; i < Slots.length; i++) usedSlots[Slots[i]] = false;
   }

  private void clearFirstSlot()                                                 // Set the first used slot to not used
   {for (int i = 0; i < numberOfSlots; i++)
     {if (usedSlots[i])
       {usedSlots[i] = false;
        return;
       }
     }
   }

  private int allocRef()                                                        // Allocate a reference to one of their keys. A linear search is used here because in hardware this will be done in parallel
   {for (int i = 0; i < numberOfSlots; i++)
     {if (!usedRefs[i])
       {usedRefs[i] = true;
        return i;
       }
     }
    stop("No more slots available");
    return -1;
   }

  private void freeRef(int Ref) {usedRefs[Ref] = false;}                        // Free a reference to one of their keys - java checks for array bounds sdo no point in an explicit check.

//D1 Overrides                                                                  // Overides which enable them to tell us about their keys

  protected void storeKey(int Ref) {}                                           // Store the current key at this location in their storage
  protected boolean    eq(int Ref) {return false;}                              // Tell me if the indexed key is equal to the search key
  protected boolean    le(int Ref) {return false;}                              // Tell me if the indexed key is less than or equal to the search key
  protected String getKey(int Ref) {return "";}                                 // Value of the referenced key as a string
  protected String    key()        {return "";}                                 // Value of the current key

//D1 State                                                                      // Query the state of the slots

  boolean empty()                                                               // True if the bit slots are all empty. The linear scan can be replaced with a parallel one in hardware.
   {for (int i = 0; i < numberOfSlots; i++) if ( usedSlots[i]) return false;
    return true;
   }

  boolean full()                                                                // True if the bit slots are all full. The linear scan can be replaced with a parallel one in hardware.
   {for (int i = 0; i < numberOfSlots; i++) if (!usedSlots[i]) return false;
    return true;
   }

  int countUsed()                                                               // Number or slots in use. How can we do this quickly in parallel?
   {int n = 0;
    for (int i = 0; i < numberOfSlots; i++) if (usedSlots[i]) ++n;
    return n;
   }

//D1 Low level operations                                                       // Low level operations on slots

  private Integer locateNearestFreeSlot(int Position)                           // Relative position of the nearest free slot to the indicated position if there is one.
   {if (!usedSlots[Position]) return 0;                                         // The slot is free already. If it is not free we do at least get an error if the specified position is invalid
    for (int i = 1; i < numberOfSlots; i++)
     {final int p = Position + i, q = Position - i;
      if (q >= 0            && !usedSlots[q]) return -i;                        // Look down preferentially to avoid moving the existing key if possible
      if (p < numberOfSlots && !usedSlots[p]) return +i;                        // Look up
     }
    return null;                                                                // No free slot - this is not actually an error.
   }

  private Integer locatePrevUsedSlot(int Position)                              // Absolute position of this slot if it is in use or the nearest lower used slot to this position.
   {for (int i = Position; i >= 0; i--) if (usedSlots[i]) return i;
    return null;                                                                // No free slot
   }

  private Integer locateNextUsedSlot(int Position)                              // Absolute position of this slot if it is in use or the nearest higher used slot to this position.
   {for (int i = Position; i < numberOfSlots; ++i) if (usedSlots[i]) return i;
    return null;                                                                // No free slot
   }

  private void shift(int Position, int Width)                                   // Shift the specified number of slots around the specified position one bit left or right depending on the sign of the width.  The liberated slot is not initialized.
   {if (Width > 0)                                                              // Shift up including the current slot
     {for (int i = Width; i > 0; --i)                                           // Move each slot
       {final int p = Position+i;                                               // Index of target
        slots[p] = slots[p-1];                                                  // Move slot
       }
      usedSlots[Position+Width] = true;                                         // We only move occupied slots
     }
    else if (Width < 0)                                                         // Shift the preceding slots down.  This reduces the number of moves needed to insert keys in ascending order
     {for (int i = Width; i < 0; ++i)                                           // Move each slot
       {final int p = Position+i;                                               // Index of target
        slots[p] = slots[p+1];                                                  // Move slot
       }
      usedSlots[Position+Width] = true;                                         // We only move occupied slots
     }
   }

  private void redistribute()                                                   // Redistribute the unused slots evenly with a slight bias to having a free slot at the end to assist with data previously sorted into ascending order
   {if (empty()) return;                                                        // Nothing to redistribute
    final int      N = numberOfSlots, c = countUsed(), space = (N - c) / c,     // Space between used slots
               cover = (space+1)*(c-1)+1, remainder = max(0, N - cover);        // Covered space from first used slot to last used slot, uncovered remainder
    final int    []s = new int    [numberOfSlots];                              // New slots distribution
    final boolean[]u = new boolean[numberOfSlots];                              // New used slots distribution
    int p = remainder / 2;                                                      // Start position for first used slot
    for (int i = 0; i < numberOfSlots; ++i)                                     // Redistribute slots
     {if (usedSlots[i])                                                         // Redistribute active slots
       {s[p] = slots[i]; u[p] = true; p += space+1;                             // Spread the used slots out
       }
     }
    for(int i = 0; i < numberOfSlots; ++i)                                      // Copy redistribution back into original avoiding use of java array methods to make everything explici for hardware conversion
     {slots[i] = s[i]; usedSlots[i] = u[i];
     }
   }

  boolean redistributeNow()                                                     // Whether we should request a redistribution of free slots - avoids a redistibution on the first insert or delete.
   {actions = (actions + 1) & 0x7fffffff;
    return actions % redistributeFrequency == 0;
   }

//D1 High level operations                                                      // Find, insert, delete values in the slots

  public boolean insert()                                                       // Insert their current search key maintaining the order of the keys in the slots
   {if (full()) return false;                                                   // No slot available in which to insert a new key
    final int slot = allocRef();                                                // Their location in which to store the search key
    storeKey(slot);                                                             // Tell the caller to store the key in the indexed location
    final Locate l = new Locate();                                              // Search for the slot containing the key closest to their search key
    if ( l.above &&  l.below) {}                                                // Found
    else if (!l.above && !l.below)                                              // Empty
     {slots    [numberOfSlots/2] = slot;
      usedSlots[numberOfSlots/2] = true;
      return true;
     }
    else if (l.above)                                                           // Insert their key above the found key
     {final int i = l.at;
      final int w = locateNearestFreeSlot(i);                                   // Width of move and direction needed to liberate a slot here - we know there is one because we know the slots are not full
      if (w > 0)                                                                // Move up
       {shift(i+1, w-1);                                                        // Liberate a slot at this point
        slots[i+1] = slot;                                                      // Place their current key in the empty slot, it has already been marked as set so there is no point in setting it again
        usedSlots[i+1] = true;
       }
      else if (w < 0)                                                           // Liberate a slot below the current slot
       {shift(i, w);                                                            // Shift any intervening slots blocking the slot below
        slots[i] = slot;                                                        // Insert into the slot below
       }
      if (redistributeNow()) redistribute();                                    // Redistribute the remaining free slots
     }
    else if (l.below)                                                           // Insert their key below the found key
     {final int i = l.at;
      final int w = locateNearestFreeSlot(i);                                   // Width of move and direction needed to liberate a slot here - we know there is one because we know the slots are not full
      if (w > 0)                                                                // Move up
       {shift(i, w);                                                            // Liberate a slot at this point
        slots[i] = slot;                                                        // Place their current key in the empty slot, it has already been marked as set so there is no point in setting it again
       }
      else if (w < 0)                                                           // Liberate a slot below the current slot
       {shift(i-1, w + 1);                                                      // Shift any intervening slots blocking the slot below
        slots[i-1] = slot;                                                      // Insert into the slot below
        usedSlots[i-1] = true;                                                  // Mark the free slot at the start of the range of occupied slots as now in use
       }
      if (redistributeNow()) redistribute();                                    // Redistribute the remaining free slots
     }
    return true;
   }

  class Locate                                                                  // Locate the slot containing their current search key if possible.
   {int at;                                                                     // The point at which the closest key was found
    boolean above;                                                              // The search key is above or equal to the found key
    boolean below;                                                              // The search key is below or equal to the found key

    public String toString()
     {return String.format("%2d %s %s", at, above ? "above" : "",
                                            below ? "below" : "");
     }

    void pos(int At, boolean Above, boolean Below)
     {at = At; above = Above; below = Below;
     }

    void above(int At) {pos(At, true, false);}                                  // Their search key is above this key
    void below(int At) {pos(At, false, true);}                                  // Their search key is below this key
    void found(int At) {at = At; above = true; below = true;}                   // Found their search key
    void none() {}                                                              // Slots are empty

    Locate()                                                                    // Locate the slot containing their current search key if possible.
     {if (empty()) {none(); return;}                                            // Empty so their search key cannot be found
      Integer a = locateNextUsedSlot(0),b = locatePrevUsedSlot(numberOfSlots-1);// Lower limit, upper limit
      if ( le(slots[a]) && !eq(slots[a])) {below(a); return;}                   // Smaller than any key
      if (!le(slots[b]))                  {above(b); return;}                   // Greater than any key
      if (eq(slots[a]))                   {found(a); return;}                   // Found at the start of the range
      if (eq(slots[b]))                   {found(b); return;}                   // Found at the end of the range

      for(int i = 0; i < numberOfSlots; ++i)                                    // Perform a reasonable number of searches knowing the key, if it is present, is within the current range. NB this i snot a linear search, the slots are searched using binary search with an upper limit that has fooled some reviewers into thinking that a linear search is being performed.
       {if (a == b) {pos(a, false, false); return;}                             // Narrowed to one possible key so no more searching is possible
        final int M = (a + b) / 2;                                              // Desired mid point - but there might not be a slot in use at this point

        final Integer ma = locatePrevUsedSlot(M);                               // Occupied slot preceding mid point
        if (ma != null)
         {if (eq(slots[ma])) {found(ma); return;};                              // Found their search key at lower end
          if (le(slots[ma]))                                                    // Their key is less than the lower mod point
           {if (b == ma)     {below(b);  return;}                               // We have been here before so we are not going to find their search key
            else b = ma;                                                        // New upper limit
           }
          else if (a == ma)  {above(a);  return;}                               // We have been here before so we are not going to find their search key
          else     a =  ma;                                                     // New lower limit
          continue;
         }

        final Integer mb = locateNextUsedSlot(M);                               // Occupied slot succeeding mid point
        if (mb != null)
         {if (eq(slots[mb])) {found(mb); return;}                               // Found their search key at upper end
          if (le(slots[mb]))                                                    // Their search key is less than the upper mid point
           {if (b == mb)     {below(b);  return;}                               // We have been here before so we are not going to find their search key
            else b = mb;                                                        // New upper limit
           }
          else if (a == mb)  {above(a);  return;}                               // We have been here before so we are not going to find their search key
          else     a =  mb;                                                     // New lower limit
          continue;
         }
        stop("This should not happen:", a, b, ma, mb);                          // We know there is at least one occupied slot so there will be a lower or upper limit to the range
       }
      stop("Searched more than the maximum number of times:", numberOfSlots);
     }
   }

  public Integer locate()                                                       // Locate the slot containing their current search key if possible.
   {final Locate l = new Locate();                                              // Locate their search key
    if (l.above && l.below) return l.at;                                        // Found
    return null;                                                                // Not found
   }

  public Integer find()                                                         // Find the index in user space of the current key
   {final Integer i = locate();
    return i == null ? null : slots[i];
   }

  public boolean delete()                                                       // Delete the current key
   {final Integer i = locate();                                                 // Locate their key
    if (i == null) return false;                                                // Their key is not in the slots
    clearSlots(i);                                                              // Delete key
    freeRef(slots[i]);                                                          // Mark the key refence is being available for a new key
    if (redistributeNow()) redistribute();                                      // Redistribute the remaining free slots
    return true;                                                                // Indicate that the key was deleted
   }

//D1 Print                                                                      // Print the bit slot

  private String printSlots()                                                   // Print the occupancy of each slot
   {final StringBuilder s = new StringBuilder();
    for (int i = 0; i < numberOfSlots; i++) s.append(usedSlots[i] ? "X" : ".");
    return ""+s;
   }

  public String toString()                                                      // Print the values in the used slots
   {final StringJoiner s = new StringJoiner(", ");
    for (int i = 0; i < numberOfSlots; i++)
     {if (usedSlots[i]) s.add(getKey(slots[i]));
     }
    return ""+s;
   }

//D1 Tests                                                                      // Test the slots

  static void test_locateNearestFreeSlot()
   {final Slots b = new Slots(16);
    b.setSlots(2, 3, 5, 6, 7, 9, 11, 13);
                      //0123456789012345
    ok(b.printSlots(), "..XX.XXX.X.X.X..");
    ok(b.locateNearestFreeSlot( 0),  0);
    ok(b.locateNearestFreeSlot( 1),  0);
    ok(b.locateNearestFreeSlot( 2), -1);
    ok(b.locateNearestFreeSlot( 3), +1);
    ok(b.locateNearestFreeSlot( 4),  0);
    ok(b.locateNearestFreeSlot( 5), -1);
    ok(b.locateNearestFreeSlot( 6), -2);
    ok(b.locateNearestFreeSlot( 8),  0);
    ok(b.locateNearestFreeSlot( 9), -1);
    ok(b.locateNearestFreeSlot(10),  0);
    ok(b.locateNearestFreeSlot(11), -1);
    ok(b.locateNearestFreeSlot(12),  0);
    ok(b.locateNearestFreeSlot(13), -1);
    ok(b.locateNearestFreeSlot(14),  0);
    ok(b.locateNearestFreeSlot(15),  0);
   }

  static void test_redistribute()
   {final Slots b = new Slots(16);
    for (int i = 0; i < b.numberOfSlots; i++) b.setSlots(i);
                                                            //0123456789012345
                                          ok(b.printSlots(), "XXXXXXXXXXXXXXXX");
                        b.redistribute(); ok(b.printSlots(), "XXXXXXXXXXXXXXXX");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), "XXXXXXXXXXXXXXX.");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), ".XXXXXXXXXXXXXX.");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), ".XXXXXXXXXXXXX..");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), "..XXXXXXXXXXXX..");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), "..XXXXXXXXXXX...");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), "...XXXXXXXXXX...");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), "...XXXXXXXXX....");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), "X.X.X.X.X.X.X.X.");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), ".X.X.X.X.X.X.X..");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), "..X.X.X.X.X.X...");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), ".X..X..X..X..X..");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), ".X...X...X...X..");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), "..X....X....X...");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), "...X.......X....");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), ".......X........");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), "................");
   }

  static void test_redistribute_odd()
   {final Slots b = new Slots(15);
    for (int i = 0; i < b.numberOfSlots; i++) b.setSlots(i);
                                                            //012345689012345
                                          ok(b.printSlots(), "XXXXXXXXXXXXXXX");
                        b.redistribute(); ok(b.printSlots(), "XXXXXXXXXXXXXXX");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), "XXXXXXXXXXXXXX.");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), ".XXXXXXXXXXXXX.");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), ".XXXXXXXXXXXX..");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), "..XXXXXXXXXXX..");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), "..XXXXXXXXXX...");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), "...XXXXXXXXX...");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), "...XXXXXXXX....");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), ".X.X.X.X.X.X.X.");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), "..X.X.X.X.X.X..");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), ".X..X..X..X..X.");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), "..X..X..X..X...");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), "..X....X....X..");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), "...X......X....");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), ".......X.......");
    b.clearFirstSlot(); b.redistribute(); ok(b.printSlots(), "...............");
   }

  static void test_ifd()
   {final int    N = 8;
          float[]K = new float[1];

    final Slots b = new Slots(N, new float[N])
     {protected void storeKey(int Ref) {((float[])userSpace)[Ref] = K[0];}         // Store the current key at this location
      protected boolean    eq(int Ref) {return K[0] == ((float[])userSpace)[Ref];} // Tell me if the indexed Key is equal to the search key
      protected boolean    le(int Ref) {return K[0] <= ((float[])userSpace)[Ref];} // Tell me if the indexed Key is less than or equal to the search key
      protected String getKey(int Ref) {return ""+     ((float[])userSpace)[Ref];} // Value of the referenced key as a string
      protected String    key()        {return ""+K[0];}                           // Value of the current key
     };
                              ok(b.empty(), true);  ok(b.full(), false);
    K[0] = 1.4f; b.insert();  ok(b.empty(), false); ok(b.full(), false);
    K[0] = 1.3f; b.insert();  ok(b.countUsed(), 2);
    K[0] = 1.6f; b.insert();
    K[0] = 1.5f; b.insert();
    K[0] = 1.8f; b.insert();
    K[0] = 1.7f; b.insert();
    K[0] = 1.2f; b.insert();
    K[0] = 1.1f; b.insert();
    ok(b.empty(), false);
    ok(b.full(), true);
    //     0    1    2    3    4    5    6    7
    ok(b, "1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8");

    K[0] = 1.4f; ok(b.locate(), 3);
    K[0] = 1.3f; ok(b.locate(), 2);
    K[0] = 1.6f; ok(b.locate(), 5);
    K[0] = 1.5f; ok(b.locate(), 4);
    K[0] = 1.8f; ok(b.locate(), 7);
    K[0] = 1.7f; ok(b.locate(), 6);
    K[0] = 1.2f; ok(b.locate(), 1);
    K[0] = 1.1f; ok(b.locate(), 0);
    K[0] = 1.0f; ok(b.locate(), null);
    K[0] = 2.0f; ok(b.locate(), null);

    final float[]F = (float[])b.userSpace;
    K[0] = 1.4f; ok(F[b.find()], K[0]); ok(b.delete(), true); b.redistribute(); ok(b, "1.1, 1.2, 1.3, 1.5, 1.6, 1.7, 1.8"); ok(b.printSlots(), "XXXXXXX.");
    K[0] = 1.2f; ok(F[b.find()], K[0]); ok(b.delete(), true); b.redistribute(); ok(b, "1.1, 1.3, 1.5, 1.6, 1.7, 1.8");      ok(b.printSlots(), ".XXXXXX.");
    K[0] = 1.3f; ok(F[b.find()], K[0]); ok(b.delete(), true); b.redistribute(); ok(b, "1.1, 1.5, 1.6, 1.7, 1.8");           ok(b.printSlots(), ".XXXXX..");
    K[0] = 1.6f; ok(F[b.find()], K[0]); ok(b.delete(), true); b.redistribute(); ok(b, "1.1, 1.5, 1.7, 1.8");                ok(b.printSlots(), "X.X.X.X.");
    K[0] = 1.8f; ok(F[b.find()], K[0]); ok(b.delete(), true); b.redistribute(); ok(b, "1.1, 1.5, 1.7");                     ok(b.printSlots(), ".X.X.X..");
    K[0] = 1.1f; ok(F[b.find()], K[0]); ok(b.delete(), true); b.redistribute(); ok(b, "1.5, 1.7");                          ok(b.printSlots(), ".X...X..");
    K[0] = 1.7f; ok(F[b.find()], K[0]); ok(b.delete(), true); b.redistribute(); ok(b, "1.5");                               ok(b.printSlots(), "...X....");
    K[0] = 1.5f; ok(F[b.find()], K[0]); ok(b.delete(), true); b.redistribute(); ok(b, "");                                  ok(b.printSlots(), "........");

    K[0] = 1.0f; ok(b.locate(), null); ok(b.delete(), false);
   }

  static void test_idn()                                                        // Repeated inserts and deletes
   {final int    N = 8;
    final float[]F = new float[N];
          float[]K = new float[1];

    final Slots b = new Slots(N, F)
     {protected void storeKey(int Ref) {F[Ref] = K[0];}
      protected boolean    eq(int Ref) {return K[0] == ((float[])userSpace)[Ref];}
      protected boolean    le(int Ref) {return K[0] <= ((float[])userSpace)[Ref];}
      protected String getKey(int Ref) {return ""+     ((float[])userSpace)[Ref];}
      protected String    key()        {return ""+K[0];}                        // Value of the current key
     };

    for (int i = 0; i < b.numberOfSlots*10; i++)
     {K[0] = 1.4f; b.insert(); b.redistribute();
      K[0] = 1.3f; b.insert(); b.redistribute();
      K[0] = 1.6f; b.insert(); b.redistribute();
      K[0] = 1.5f; b.insert(); b.redistribute();
      ok(b, "1.3, 1.4, 1.5, 1.6");
      ok(b.printSlots(), "X.X.X.X.");
      K[0] = 1.4f; b.delete(); b.redistribute();
      K[0] = 1.3f; b.delete(); b.redistribute();
      K[0] = 1.6f; b.delete(); b.redistribute();
      K[0] = 1.5f; b.delete(); b.redistribute();
      ok(b, "");
      ok(b.printSlots(), "........");
     }
   }

  static void test_tooManySearches()
   {final int    N = 8;
    final float[]F = new float[N];
          float[]K = new float[1];

    final Slots b = new Slots(N, F)
     {protected void storeKey(int Ref) {F[Ref] = K[0];}
      protected boolean    eq(int Ref) {return K[0] == ((float[])userSpace)[Ref];}
      protected boolean    le(int Ref) {return K[0] <= ((float[])userSpace)[Ref];}
      protected String getKey(int Ref) {return ""+((float[])userSpace)[Ref];}
      protected String    key()        {return ""+K[0];}                        // Value of the current key
     };

    K[0] = 10f; b.insert();
    K[0] = 20f; b.insert();
    K[0] = 15f; ok(b.find(), null);
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_locateNearestFreeSlot();
    test_redistribute();
    test_redistribute_odd();
    test_ifd();
    test_idn();
   }

  static void newTests()                                                        // Tests being worked on
   {test_locateNearestFreeSlot();
    test_redistribute();
    test_redistribute_odd();
    test_ifd();
    test_idn();
    test_tooManySearches();
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
