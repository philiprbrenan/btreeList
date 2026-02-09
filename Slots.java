//------------------------------------------------------------------------------
// Maintain key references in ascending order using distributed slots
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//------------------------------------------------------------------------------
// Add random inserts/deletes to stress locate/insert/delete
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.
import java.util.*;

public class Slots extends Test                                                 // Maintain key references in ascending order using distributed slots
 {protected final int      numberOfSlots;                                       // Number of slots
  protected final int      numberOfRefs;                                        // Number of references which shopuld be equal to or smaller than the numnber of slots as slots are narrow and refences are wide allowing us to use more slots effectively
  protected final int      redistributionWidth;                                 // Redistribute if the next slot is further than this
  protected final int    []slots;                                               // Key ordering
  protected final boolean[]usedSlots;                                           // Slots in use. I could have used BitSet but this would hide implementation details. Writing the code makes the actions explicit.
  protected final boolean[]usedRefs;                                            // Index of each key. This index is stable even when the slots are redistributed to make insertions faster.
  protected final double []keys;                                                // Keys
  String         name;                                                          // String name of these slots for debugging purposes
  static boolean debug = false;                                                 // Debug if true

//D1 Construction                                                               // Construct and layout the slots

  public Slots(int NumberOfRefs)                                                // Create the slots
   {numberOfSlots = NumberOfRefs * 2;                                           // The lots are narrow while the refs are wide so we having more slots reduces the amount of slot movement withouit greatly increasing memory requirements
    numberOfRefs  = NumberOfRefs;
    redistributionWidth  = (int)java.lang.Math.sqrt(numberOfRefs);
    slots     = new int    [numberOfSlots];
    usedSlots = new boolean[numberOfSlots];
    usedRefs  = new boolean[numberOfRefs];
    keys      = new double [numberOfRefs];
   }

  public Slots(String Name) {this(0); name = Name;}                             // Create empty named slots to assist with debugging

  private Slots duplicate()                                                     // Duplicate a set of slots
   {final Slots s = new Slots(numberOfRefs);
    s.copy(this);
    return s;
   }

  protected void copy(Slots Source)                                             // Copy the source slots
   {if (Source.numberOfSlots != numberOfSlots)
     {stop("Different number of slots:", Source.numberOfSlots, numberOfSlots);
     }
    if (Source.numberOfRefs != numberOfRefs)
     {stop("Different number of refs:", Source.numberOfRefs, numberOfRefs);
     }
    for (int i = 0; i < Source.numberOfSlots; i++)                              // Copy the slots from source to target
     {slots    [i] = Source.slots    [i];
      usedSlots[i] = Source.usedSlots[i];
     }

    for (int i = 0; i < numberOfRefs; i++)                                      // Copy the references from source to target
     {usedRefs [i] = Source.usedRefs [i];
      keys     [i] = Source.keys     [i];
     }
   }

  int numberOfSlots() {return numberOfSlots;}
  int numberOfRefs()  {return numberOfRefs;}

//D2 Slots                                                                      // Manage the slots

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

  protected void clearSlotAndRef(int I) {freeRef(slots[I]); clearSlots(I);}     // Remove a key from the slots
  protected int            slots(int I) {return slots    [I];}                  // The indexed slot
  protected boolean    usedSlots(int I) {return usedSlots[I];}                  // The indexed slot usage indicator
  protected boolean     usedRefs(int I) {return usedRefs [I];}                  // The indexed reference usage indicator
  double                     key(int I) {return keys[slots[I]];}                // The indexed key

//D2 Refs                                                                       // Allocate and free references to keys

  private int allocRef()                                                        // Allocate a reference to one of their keys. A linear search is used here because in hardware this will be done in parallel
   {for (int i = 0; i < numberOfRefs; i++)
     {if (!usedRefs(i))
       {usedRefs[i] = true;
        return i;
       }
     }
    stop("No more slots available");
    return -1;
   }

  private void freeRef(int Ref) {usedRefs[Ref] = false;}                        // Free a reference to one of their keys - java checks for array bounds sdo no point in an explicit check.

//D1 Keys                                                                       // Operations on keys

  boolean eq(double Key, int Slot) {return Key == keys[slots[Slot]];}           // Search key is equal to indexed key
  boolean le(double Key, int Slot) {return Key <= keys[slots[Slot]];}           // Search key is less than or equal to indexed key
  boolean lt(double Key, int Slot) {return !eq(Key, Slot) && le(Key, Slot);}    // Search key is less than or equal to indexed key
  boolean ge(double Key, int Slot) {return  eq(Key, Slot) || gt(Key, Slot);}    // Search key is less than or equal to indexed key
  boolean gt(double Key, int Slot) {return !le(Key, Slot);}                     // Search key is less than or equal to indexed key
  String getKey(int Slot)          {return ""+keys[slots[Slot]];}               // Value of the referenced key as a string

//D1 Statistics                                                                 // Query the state of the slots

  int countUsed()                                                               // Number or slots in use. How can we do this quickly in parallel?
   {int n = 0;
    for (int i = 0; i < numberOfSlots; i++) if (usedSlots[i]) ++n;
    return n;
   }

  boolean empty() {return countUsed() == 0;}                                    // All references are unused
  boolean full()  {return countUsed() == numberOfRefs;}                         // All references are in use

  boolean adjacentUsedSlots(int Start, int Finish)                              // Checks wether two used slots are adjacent
   {if (!usedSlots[Start])  stop("Start  slot  must be occupied but it is empty, slot:", Start);
    if (!usedSlots[Finish]) stop("Finish slot  must be occupied but it is empty, slot:", Finish);
    if (Start >= Finish)    stop("Start must precede finish:", Start, Finish);

    for (int i = Start+1; i < Finish; i++) if (usedSlots[i]) return false;      // From start to finish looking for an intermediate used slot
    return true;
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

  Integer locateFirstUsedSlot()                                                 // Absolute position of the first slot in use
   {for (int i = 0; i < numberOfSlots; ++i)        if (usedSlots[i]) return i;
    return null;                                                                // No free slot
   }

  Integer locateLastUsedSlot()                                                  // Absolute position of the last slot in use
   {for (int i = numberOfSlots-1; i >= 0; i--)     if (usedSlots[i]) return i;
    return null;                                                                // No free slot
   }

  private Integer locatePrevUsedSlot(int Position)                              // Absolute position of this slot if it is in use or else the next lower used slot
   {for (int i = Position; i >= 0; i--)            if (usedSlots[i]) return i;
    return null;                                                                // No free slot
   }

  private Integer locateNextUsedSlot(int Position)                              // Absolute position of this slot if it is in use or else the next higher used slot
   {for (int i = Position; i < numberOfSlots; ++i) if (usedSlots[i]) return i;
    return null;                                                                // No free slot
   }

  private Integer locateFirstEmptySlot()                                        // Absolute position of the first free slot
   {for (int i = 0; i < numberOfSlots; ++i)        if (!usedSlots[i]) return i;
    return null;                                                                // No free slot
   }

  private Integer locateLastEmptySlot()                                         // Absolute position of the last free slot
   {for (int i = numberOfSlots-1; i >= 0; i--)     if (!usedSlots[i]) return i;
    return null;                                                                // No free slot
   }

  private Integer locatePrevEmptySlot(int Position)                             // Absolute position of this slot if it is free or the nearest lower free slot before this position.
   {for (int i = Position; i >= 0; i--)            if (!usedSlots[i]) return i;
    return null;                                                                // No free slot
   }

  private Integer locateNextEmptySlot(int Position)                             // Absolute position of this slot if it is in use or the nearest higher free slot after this position.
   {for (int i = Position; i < numberOfSlots; ++i) if (!usedSlots[i]) return i;
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

  protected void redistribute()                                                 // Redistribute the unused slots evenly with a slight bias to having a free slot at the end to assist with data previously sorted into ascending order
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
    for(int i = 0; i < numberOfSlots; ++i)                                      // Copy redistribution back into original avoiding use of java array methods to make everything explicit for hardware conversion
     {slots[i] = s[i]; usedSlots[i] = u[i];
     }
   }

  void reset()                                                                  // Reset the slots
   {for (int i = 0; i < numberOfSlots; i++)
     {usedSlots[i] = false; slots[i] = 0;
     }
    for (int i = 0; i < numberOfRefs; i++)
     {usedRefs[i] = false; keys[i] = 0;
     }
   }

  void compactLeft()                                                            // Compact the used slots to the left end
   {if (empty() || full()) return;                                              // Nothing to compact
    final Slots d = duplicate(); reset();
    int p = 0;
    for (int i = 0; i < numberOfSlots; i++)                                     // Each slot
     {if (d.usedSlots[i])                                                       // Each used slot
       {usedSlots[p] = usedRefs[p] = true;
            slots[p] = p;
             keys[p] = d.keys[d.slots[i]];
        ++p;
       }
     }
   }

  void compactRight()                                                           // Squeeze the used slots to the left end
   {if (empty() || full()) return;                                              // Nothing to squeeze
    final Slots d = duplicate(); reset();
    int p = numberOfRefs - 1;
    for (int i = numberOfSlots - 1; i >= 0; --i)
     {if (d.usedSlots[i])
       {usedSlots[p] = usedRefs[p] = true;
            slots[p] = p;
             keys[p] = d.keys[d.slots[i]];
        --p;
       }
     }
   }

  void mergeCompacted(Slots Left, Slots Right)                                  // Merge left and right compacted slots into the current slots
   {final Slots l = Left, r = Right;
    reset();
    for (int i = 0; i < numberOfRefs; ++i)
     {if (l.usedSlots(i))
       {    slots[i] = l.    slots[i];
        usedSlots[i] = l.usedSlots[i];
         usedRefs[i] = l. usedRefs[i];
             keys[i] = l.     keys[i];
       }
      else if (r.usedSlots(i))
       {    slots[i] = r.    slots[i];
        usedSlots[i] = r.usedSlots[i];
         usedRefs[i] = r. usedRefs[i];
             keys[i] = r.     keys[i];
       }
      else usedSlots[i] = usedRefs[i] = false;
     }
   }

  boolean mergeOnRight(Slots Right)                                             // Merge the specified slots from the right
   {if (countUsed() + Right.countUsed() > numberOfSlots) return false;
    final Slots l = duplicate(), r = Right.duplicate();
    l.compactLeft(); r.compactRight();
    mergeCompacted(l, r);
    return true;
   }

  boolean mergeOnLeft(Slots Left)                                               // Merge the specified slots from the left
   {if (Left.countUsed() + countUsed() > numberOfSlots) return false;
    final Slots l = Left.duplicate(), r = duplicate();
    l.compactLeft(); r.compactRight();
    mergeCompacted(l, r);
    return true;
   }

//D1 High level operations                                                      // Find, insert, delete values in the slots

  public Integer insert(double Key)                                             // Insert a key into the slots maintaining the order of all the keys in the slots and returning the index of the reference to the key
   {if (full()) return null;                                                    // No slot available in which to insert a new key
    final int slot = allocRef();                                                // The location in which to store the search key
    keys[slot] = Key;                                                           // Store the new key in the referenced location
    final Locate l = new Locate(Key);                                           // Search for the slot containing the key closest to their search key
    if ( l.above && l.below) {}                                                 // Found
    else if (!l.above && !l.below)                                              // Empty place the key in the middle
     {slots    [numberOfSlots/2] = slot;
      usedSlots[numberOfSlots/2] = true;
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
      if (java.lang.Math.abs(w) >= redistributionWidth) redistribute();          // Redistribute if the used slots are densely packed
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
      if (java.lang.Math.abs(w) >= redistributionWidth) redistribute();        // Redistribute if the used slots are densely packed
     }
    return slot;                                                                // The index of the reference to the key
   }

  class Locate                                                                  // Locate the slot containing the search key if possible else the key immediately above or below the search key.
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
    void found(int At) {pos(At, true,  true);}                                   // Found their search key
    void none ()       {}                                                       // Slots are empty

    Locate(double Key)                                                          // Locate the slot containing the search key if possible.
     {if (empty()) {none(); return;}                                            // Empty so their search key cannot be found
      Integer a = locateNextUsedSlot(0),b = locatePrevUsedSlot(numberOfSlots-1);// Lower limit, upper limit
      if ( eq(Key, a)) {found(a); return;}                                      // Found at the start of the range
      if ( eq(Key, b)) {found(b); return;}                                      // Found at the end of the range
      if ( le(Key, a)) {below(a); return;}                                      // Smaller than any key
      if (!le(Key, b)) {above(b); return;}                                      // Greater than any key

      for(int i = 0; i < numberOfSlots; ++i)                                    // Perform a reasonable number of searches knowing the key, if it is present, is within the current range. NB this is not a linear search, the slots are searched using binary search with an upper limit that has fooled some reviewers into thinking that a linear search is being performed.
       {final int M = (a + b) / 2;                                              // Desired mid point - but there might not be a slot in use at this point
        final int ma = locatePrevUsedSlot(M);                                   // Occupied slot preceding mid point
        final int mb = locateNextUsedSlot(M);                                   // Occupied slot succeeding mid point

        if      (ma != a && ge(Key, ma)) a = ma;
        else if (ma != b && le(Key, ma)) b = ma;
        else if (mb != a && ge(Key, mb)) a = mb;
        else if (mb != b && le(Key, mb)) b = mb;
        else                                                                    // The slots must be adjacent
         {if (eq(Key, a)) {found(a); return;};                                 // Found the search key at the lower end
          if (eq(Key, b)) {found(b); return;};                                 // Found the search key at the upper end
          below(b);
          return;
         }                                                                      // New mid point
       }
      stop("Searched more than the maximum number of times:", numberOfSlots);
     }
   }

  Integer locateFirstGe(double Key)                                             // Locate the slot containing the first key greater than or equal to the search key
   {final Locate l = new Locate(Key);
    if (l.below) return l.at;
    return locateNextUsedSlot(l.at+1);
   }

  public Integer locate(double Key)                                             // Locate the slot containing the current search key if possible.
   {final Locate l = new Locate(Key);                                           // Locate the search key
    if (l.above && l.below) return l.at;                                        // Found
    return null;                                                                // Not found
   }

  public Integer find(double Key)                                               // Find the index of the current key in the slots
   {final Integer i = locate(Key);
    return i == null ? null : slots[i];
   }

  public boolean delete(double Key)                                             // Delete the specified key
   {final Integer i = locate(Key);                                              // Locate the search key
    if (i == null) return false;                                                // Their key is not in the slots
    clearSlotAndRef(i);                                                         // Delete key
    return true;                                                                // Indicate that the key was deleted
   }

//D1 Print                                                                      // Print the bit slot

  protected String printSlots()                                                 // Print the occupancy of each slot
   {final StringBuilder s = new StringBuilder();
    for (int i = 0; i < numberOfSlots; i++) s.append(usedSlots[i] ? "X" : ".");
    return ""+s;
   }

  protected String dump()                                                       // Dump the slots
   {final StringBuilder s = new StringBuilder();
    final int N = numberOfSlots, R = numberOfRefs;
    s.append("positions: ");
    for (int i = 0; i < N; i++) s.append(String.format(" %3d", i));
    s.append("\nslots    : ");
    for (int i = 0; i < N; i++) s.append(String.format(" %3d", slots[i]));
    s.append("\nusedSlots: ");
    for (int i = 0; i < N; i++) s.append(usedSlots(i) ? "   X" : "   .");
    s.append("\nusedRefs : ");
    for (int i = 0; i < R; i++) s.append(usedRefs (i) ? "   X" : "   .");
    s.append("\nkeys     : ");
    for (int i = 0; i < R; i++) s.append(String.format(" %3.1f", keys[i]));
    return ""+s+"\n";
   }

  public String toString()                                                      // Print the values in the used slots
   {final StringJoiner s = new StringJoiner(", ");
    for (int i = 0; i < numberOfSlots; i++)
     {if (usedSlots[i]) s.add(""+keys[slots[i]]);
     }
    return ""+s;
   }

//D1 Tests                                                                      // Test the slots

  static void test_locateNearestFreeSlot()
   {final Slots b = new Slots(8);
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

    ok(b.locateFirstUsedSlot(),      2);
    ok(b.locateLastUsedSlot(),      13);
    ok(b.locatePrevUsedSlot( 9),     9);
    ok(b.locatePrevUsedSlot(10),     9);
    ok(b.locateNextUsedSlot(10),    11);
    ok(b.locateNextUsedSlot(11),    11);
    ok(b.locateFirstEmptySlot(),     0);
    ok(b.locateLastEmptySlot(),     15);
    ok(b.locatePrevEmptySlot(4),     4);
    ok(b.locatePrevEmptySlot(5),     4);
    ok(b.locateNextEmptySlot(4),     4);
    ok(b.locateNextEmptySlot(5),     8);

    ok(b.locatePrevUsedSlot ( 1),   null);
    ok(b.locateNextUsedSlot (14),   null);

    b.setSlots(0, 15);
    ok(b.locatePrevEmptySlot( 0),   null);
    ok(b.locateNextEmptySlot(15),   null);
   }

  static void test_redistribute()
   {final Slots b = new Slots(8);
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

  static void test_ifd()
   {final Slots b = new Slots(8);
                    ok(b.empty(), true);  ok(b.full(), false);
    b.insert(1.4);  ok(b.empty(), false); ok(b.full(), false);
    b.insert(1.3);  ok(b.countUsed(), 2);
    b.insert(1.6);
    b.insert(1.5);
    b.insert(1.8);
    b.insert(1.7);
    b.insert(1.2);
    b.insert(1.1);
    ok(b, "1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8");
    ok(b.empty(), false);
    ok(b.full(), true);
    ok(b.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   0   7   6   1   0   3   2   5   4   0   0   0
usedSlots:    .   .   .   .   .   X   X   X   X   X   X   X   X   .   .   .
usedRefs :    X   X   X   X   X   X   X   X
keys     :  1.4 1.3 1.6 1.5 1.8 1.7 1.2 1.1
""");
    ok(b.locate(1.1),  5);
    ok(b.locate(1.2),  6);
    ok(b.locate(1.3),  7);
    ok(b.locate(1.4),  8);
    ok(b.locate(1.5),  9);
    ok(b.locate(1.6), 10);
    ok(b.locate(1.7), 11);
    ok(b.locate(1.8), 12);
    ok(b.locate(1.0), null);
    ok(b.locate(2.0), null);

    ok(b.keys[b.find(1.4)], 1.4); ok(b.delete(1.4), true); ok(b, "1.1, 1.2, 1.3, 1.5, 1.6, 1.7, 1.8");
    ok(b.keys[b.find(1.2)], 1.2); ok(b.delete(1.2), true); ok(b, "1.1, 1.3, 1.5, 1.6, 1.7, 1.8");
    ok(b.keys[b.find(1.3)], 1.3); ok(b.delete(1.3), true); ok(b, "1.1, 1.5, 1.6, 1.7, 1.8");
    ok(b.keys[b.find(1.6)], 1.6); ok(b.delete(1.6), true); ok(b, "1.1, 1.5, 1.7, 1.8");
    ok(b.keys[b.find(1.8)], 1.8); ok(b.delete(1.8), true); ok(b, "1.1, 1.5, 1.7");
    ok(b.keys[b.find(1.1)], 1.1); ok(b.delete(1.1), true); ok(b, "1.5, 1.7");
    ok(b.keys[b.find(1.7)], 1.7); ok(b.delete(1.7), true); ok(b, "1.5");
    ok(b.keys[b.find(1.5)], 1.5); ok(b.delete(1.5), true); ok(b, "");

    ok(b.locate(1.0), null); ok(b.delete(1.0), false);
   }

  static void test_idn()                                                        // Repeated inserts and deletes
   {final Slots b = new Slots(8);

    for (int i = 0; i < b.numberOfSlots*10; i++)
     {b.insert(1.4); b.redistribute();
      b.insert(1.3); b.redistribute();
      b.insert(1.6); b.redistribute();
      b.insert(1.5); b.redistribute();
      ok(b, "1.3, 1.4, 1.5, 1.6");
      ok(b.countUsed(), 4);
      b.delete(1.4); b.redistribute();
      b.delete(1.3); b.redistribute();
      b.delete(1.6); b.redistribute();
      b.delete(1.5); b.redistribute();
      ok(b, "");
      ok(b.countUsed(), 0);
     }
   }

  static void test_tooManySearches()
   {final Slots b = new Slots(8);

    b.insert(10.0);
    b.insert(20.0);
    ok(b.find(15.0), null);
   }

  static void test_locateFirstGe()
   {final Slots b = new Slots(8);
    b.usedSlots[ 1] = true; b.slots[ 1] = 7; b.usedRefs[7] = true; b.keys[7] = 1.1;
    b.usedSlots[ 5] = true; b.slots[ 5] = 4; b.usedRefs[4] = true; b.keys[4] = 1.2;
    b.usedSlots[ 9] = true; b.slots[ 9] = 2; b.usedRefs[2] = true; b.keys[2] = 1.3;
    b.usedSlots[14] = true; b.slots[14] = 0; b.usedRefs[0] = true; b.keys[0] = 1.4;
    ok(b.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   4   0   0   0   2   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   .   X   .
usedRefs :    X   .   X   .   X   .   .   X
keys     :  1.4 0.0 1.3 0.0 1.2 0.0 0.0 1.1
""");
    ok(b.locateFirstGe(1.15),    5);
    ok(b.locateFirstGe(1.2),     5);
    ok(b.locateFirstGe(1.24),    9);
    ok(b.locateFirstGe(1.5),  null);
   }

  static void test_compactLeft()
   {final Slots b = new Slots(8);
    b.usedSlots[ 1] = true; b.slots[ 1] = 7; b.usedRefs[7] = true; b.keys[7] = 1.1;
    b.usedSlots[ 5] = true; b.slots[ 5] = 4; b.usedRefs[4] = true; b.keys[4] = 1.2;
    b.usedSlots[ 9] = true; b.slots[ 9] = 2; b.usedRefs[2] = true; b.keys[2] = 1.3;
    b.usedSlots[14] = true; b.slots[14] = 0; b.usedRefs[0] = true; b.keys[0] = 1.4;
    ok(b.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   4   0   0   0   2   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   .   X   .
usedRefs :    X   .   X   .   X   .   .   X
keys     :  1.4 0.0 1.3 0.0 1.2 0.0 0.0 1.1
""");
    b.compactLeft();
    ok(b.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   1   2   3   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    X   X   X   X   .   .   .   .   .   .   .   .   .   .   .   .
usedRefs :    X   X   X   X   .   .   .   .
keys     :  1.1 1.2 1.3 1.4 0.0 0.0 0.0 0.0
""");
   }

  static void test_compactRight()
   {final Slots b = new Slots(8);
    b.usedSlots[ 1] = true; b.slots[ 1] = 7; b.usedRefs[7] = true; b.keys[7] = 1.1;
    b.usedSlots[ 5] = true; b.slots[ 5] = 4; b.usedRefs[4] = true; b.keys[4] = 1.2;
    b.usedSlots[ 9] = true; b.slots[ 9] = 2; b.usedRefs[2] = true; b.keys[2] = 1.3;
    b.usedSlots[14] = true; b.slots[14] = 0; b.usedRefs[0] = true; b.keys[0] = 1.4;
    ok(b.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   4   0   0   0   2   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   .   X   .
usedRefs :    X   .   X   .   X   .   .   X
keys     :  1.4 0.0 1.3 0.0 1.2 0.0 0.0 1.1
""");
    b.compactRight();
    ok(b.dump(), """
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   4   5   6   7   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   X   X   X   X   .   .   .   .   .   .   .   .
usedRefs :    .   .   .   .   X   X   X   X
keys     :  0.0 0.0 0.0 0.0 1.1 1.2 1.3 1.4
""");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_locateNearestFreeSlot();
    test_redistribute();
    test_ifd();
    test_idn();
    test_tooManySearches();
    test_locateFirstGe();
    test_compactLeft();
    test_compactRight();
   }

  static void newTests()                                                        // Tests being worked on
   {oldTests();
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
