//------------------------------------------------------------------------------
// Maintain key references in ascending order using distributed slots
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//------------------------------------------------------------------------------
// Add random inserts/deletes to stress locate/insert/delete
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.
import java.util.*;
import java.nio.ByteBuffer;

public class Slots extends Test                                                 // Maintain key references in ascending order using distributed slots
 {private final int numberOfRefs;                                               // Number of references which should be equal to or smaller than the numnber of slots as slots are narrow and refences are wide allowing us to use more slots effectively
  private final int redistributionWidth;                                        // Redistribute if the next slot is further than this
  Memory            memory;                                                     // Memory used by the slots
  final String      formatKey = "%3d";                                          // Format a key for dumping during testing
  private int       name;                                                       // Numeric name for these slots for debugging purposes
  static boolean    debug = false;                                              // Debug if true

//D1 Construction                                                               // Construct and layout the slots

  Slots(int NumberOfRefs, ByteBuffer Bytes)                                     // Create the slots using the specified memeory
   {numberOfRefs        = NumberOfRefs;                                         // Number of slots referenced
    redistributionWidth = (int)java.lang.Math.sqrt(numberOfRefs);               // Redistribute the slots if we see a run of more than these that are all occupied to make insertion easier.
    memory = Bytes == null ? new Memory() : new Memory(Bytes);                  // Memory used by the slots
   }

  public Slots(int NumberOfRefs) {this(NumberOfRefs, null);}                    // Create the slots and some memory to hold them

  private Slots(int NumberOfRefs, boolean usable)                               // Create the slots just to find out how big they will be
   {numberOfRefs        = NumberOfRefs;                                         // Number of slots referenced
    redistributionWidth = 0;
    memory = new Memory();
   }

  void setMemory(ByteBuffer Bytes) {memory = new Memory(Bytes);}                // Set memory to be used

  static Slots fake(int Name)                                                   // Slots used during testing to mock attached branches and leaves
   {final Slots s = new Slots(0);
    s.name(Name);
    return s;
   }

  Slots duplicateSlots()                                                        // Copy the source slots
   {final Slots t = new Slots(numberOfRefs);
    t.copy(this);
    return t;                                                                   // The copied slots
   }

  Slots copy(Slots Source)                                                      // Copy the source slots
   {final int  n = name();                                                      // Save name of target
    memory.copy(Source.memory);                                                 // Copy memory
    name(n);                                                                    // Reload name of target
    return this;                                                                // The copied slots
   }

  void invalidate()   {memory.invalidate();}                                    // Invalidate the slots in such away that they are unlikely to work well if subsequently used
  int numberOfRefs()  {return numberOfRefs;}
  int numberOfSlots() {return numberOfRefs() * 2;}                              // Number of slots from number of ref

  public record Slot(int  index) {}                                             // A slot

//D2 Keys                                                                       // Define a key

  public record Key(int  value) {}                                              // A key
  static Key Key(int  Key) {return new Key(Key);}

//D2 Slots                                                                      // Manage the slots

  void setSlots(int...Slots)                                                    // Set slots as used
   {for (int i = 0; i < Slots.length; i++) usedSlots(Slots[i], true);
   }

  void clearSlots(int...Slots)                                                  // Set slots as not being used
   {for (int i = 0; i < Slots.length; i++) usedSlots(Slots[i], false);
   }

  void clearFirstSlot()                                                         // Set the first used slot to not used
   {final int N = numberOfSlots();
    for (int i = 0; i < N; i++)
     {if (usedSlots(i))
       {usedSlots(i, false);
        return;
       }
     }
   }

  protected void clearSlotAndRef(int I) {freeRef(memory.slots     (I)); clearSlots(I);} // Remove a key from the slots
  protected int            slots(int I) {return  memory.slots     (I);}         // The indexed slot
  protected boolean    usedSlots(int I) {return  memory.usedSlots (I);}         // The indexed slot usage indicator
  protected boolean     usedRefs(int I) {return  memory.usedRefs  (I);}         // The indexed reference usage indicator
  Key                       keys(int I) {return  new Key(memory.keys(memory.slots(I)));} // The indexed key

  protected void     slots(int I, int     Value) {memory.slots    (I, Value);}  // The indexed slot
  protected void usedSlots(int I, boolean Value) {memory.usedSlots(I, Value);}  // The indexed slot usage indicator
  protected void  usedRefs(int I, boolean Value) {memory.usedRefs (I, Value);}  // The indexed reference usage indicator
            void      keys(int I, Key     Key)   {memory.keys(memory.slots(I), Key.value());} // The indexed key

  protected Key  key(int I)          {return new Key(memory.keys(I));}          // Get the key directly
  protected void key(int I, Key Key) {memory.keys(I, Key.value());}             // Set the key directly

  int  name() {return   memory.name();}                                         // Get the name
  void name(int  Name) {memory.name(Name);}                                     // Set the name

  int  type()  {return memory.type();}                                          // Get the type
  void type(int Type) {memory.type(Type);}                                      // Set the type

//D2 Refs                                                                       // Allocate and free references to keys

  private int allocRef()                                                        // Allocate a reference to one of their keys. A linear search is used here because in hardware this will be done in parallel
   {for (int i = 0; i < numberOfRefs; i++)
     {if (!usedRefs(i))
       {usedRefs(i, true);
        return i;
       }
     }
    stop("No more slots available");
    return -1;
   }

  private void freeRef(int Ref) {usedRefs(Ref, false);}                         // Free a reference to one of their keys - java checks for array bounds sdo no point in an explicit check.

//D1 Keys                                                                       // Operations on keys

  boolean eq(Key Key, int Slot) {return Key.value() == keys(Slot).value();}     // Search key is equal to indexed key
  boolean le(Key Key, int Slot) {return Key.value() <= keys(Slot).value();}     // Search key is less than or equal to indexed key
  boolean lt(Key Key, int Slot) {return !eq(Key, Slot) && le(Key, Slot);}       // Search key is less than or equal to indexed key
  boolean ge(Key Key, int Slot) {return  eq(Key, Slot) || gt(Key, Slot);}       // Search key is less than or equal to indexed key
  boolean gt(Key Key, int Slot) {return !le(Key, Slot);}                        // Search key is less than or equal to indexed key

  Key firstKey()                                                                // First key in slots
   {if (empty()) stop("No first key in empty slots");                           // First key in slots if there is one
    return keys(locateFirstUsedSlot());
   }

  Key lastKey()                                                                 // Last key in slots
   {if (empty()) stop("No last key in empty slots");                            // Last key in slots if there is one
    return keys(locateLastUsedSlot());
   }

//D1 Statistics                                                                 // Query the state of the slots

  int countUsed()                                                               // Number or slots in use. How can we do this quickly in parallel?
   {final int N = numberOfSlots();
    int n = 0;
    for (int i = 0; i < N; i++) if (usedSlots(i)) ++n;
    return n;
   }

  boolean empty() {return countUsed() == 0;}                                    // All references are unused
  boolean full()  {return countUsed() == numberOfRefs;}                         // All references are in use

  boolean adjacentUsedSlots(int Start, int Finish)                              // Checks wether two used slots are adjacent
   {if (!usedSlots(Start))  stop("Start  slot  must be occupied but it is empty, slot:", Start);
    if (!usedSlots(Finish)) stop("Finish slot  must be occupied but it is empty, slot:", Finish);
    if (Start >= Finish)    stop("Start must precede finish:", Start, Finish);

    for (int i = Start+1; i < Finish; i++) if (usedSlots(i)) return false;      // From start to finish looking for an intermediate used slot
    return true;
   }

//D1 Low level operations                                                       // Low level operations on slots

  Integer locateNearestFreeSlot(int Position)                                   // Relative position of the nearest free slot to the indicated position if there is one.
   {final int N = numberOfSlots();
    if (!usedSlots(Position)) return 0;                                         // The slot is free already. If it is not free we do at least get an error if the specified position is invalid
    for (int i = 1; i < N; i++)
     {final int p = Position + i, q = Position - i;
      if (q >= 0 && !usedSlots(q)) return -i;                                   // Look down preferentially to avoid moving the existing key if possible
      if (p <  N && !usedSlots(p)) return +i;                                   // Look up
     }
    return null;                                                                // No free slot - this is not actually an error.
   }

  Integer locateFirstUsedSlot()                                                 // Absolute position of the first slot in use
   {final int N = numberOfSlots();
    for (int i = 0; i < N; ++i) if ( usedSlots(i)) return i;
    return null;                                                                // No free slot
   }

  Integer locateLastUsedSlot()                                                  // Absolute position of the last slot in use
   {for (int i = numberOfSlots()-1; i >= 0; i--) if ( usedSlots(i)) return i;
    return null;                                                                // No free slot
   }

  Integer locatePrevUsedSlot(int Position)                                      // Absolute position of this slot if it is in use or else the next lower used slot
   {for (int i = Position; i >= 0; i--)            if ( usedSlots(i)) return i;
    return null;                                                                // No free slot
   }

  Integer locateNextUsedSlot(int Position)                                      // Absolute position of this slot if it is in use or else the next higher used slot
   {final int N = numberOfSlots();
    for (int i = Position; i < N; ++i) if ( usedSlots(i)) return i;
    return null;                                                                // No free slot
   }

  Integer locateFirstEmptySlot()                                                // Absolute position of the first free slot
   {final int N = numberOfSlots();
    for (int i = 0; i < N; ++i)        if (!usedSlots(i)) return i;
    return null;                                                                // No free slot
   }

  Integer locateLastEmptySlot()                                                 // Absolute position of the last free slot
   {for (int i = numberOfSlots()-1; i >= 0; i--)     if (!usedSlots(i)) return i;
    return null;                                                                // No free slot
   }

  Integer locatePrevEmptySlot(int Position)                                     // Absolute position of this slot if it is free or the nearest lower free slot before this position.
   {for (int i = Position; i >= 0; i--)            if (!usedSlots(i)) return i;
    return null;                                                                // No free slot
   }

  Integer locateNextEmptySlot(int Position)                                     // Absolute position of this slot if it is in use or the nearest higher free slot after this position.
   {final int N = numberOfSlots();
    for (int i = Position; i < N; ++i) if (!usedSlots(i)) return i;
    return null;                                                                // No free slot
   }

  void shift(int Position, int Width)                                           // Shift the specified number of slots around the specified position one bit left or right depending on the sign of the width.  The liberated slot is not initialized.
   {if (Width > 0)                                                              // Shift up including the current slot
     {for (int i = Width; i > 0; --i)                                           // Move each slot
       {final int p = Position+i;                                               // Index of target
        slots(p, slots(p-1));                                                   // Move slot
       }
      usedSlots(Position+Width, true);                                          // We only move occupied slots
     }
    else if (Width < 0)                                                         // Shift the preceding slots down.  This reduces the number of moves needed to insert keys in ascending order
     {for (int i = Width; i < 0; ++i)                                           // Move each slot
       {final int p = Position+i;                                               // Index of target
        slots(p, slots(p+1));                                                   // Move slot
       }
      usedSlots(Position+Width, true);                                          // We only move occupied slots
     }
   }

  protected void redistribute()                                                 // Redistribute the unused slots evenly with a slight bias to having a free slot at the end to assist with data previously sorted into ascending order
   {if (empty()) return;                                                        // Nothing to redistribute
    final int      N = numberOfSlots(), c = countUsed(), space = (N - c) / c,   // Space between used slots
               cover = (space+1)*(c-1)+1, remainder = max(0, N - cover);        // Covered space from first used slot to last used slot, uncovered remainder
    final int    []s = new int    [N];                                          // New slots distribution
    final boolean[]u = new boolean[N];                                          // New used slots distribution
    int p = remainder / 2;                                                      // Start position for first used slot
    for (int i = 0; i < N; ++i)                                                 // Redistribute slots
     {if (usedSlots(i))                                                         // Redistribute active slots
       {s[p] = slots(i); u[p] = true; p += space+1;                             // Spread the used slots out
       }
     }
    for(int i = 0; i < N; ++i)                                                  // Copy redistribution back into original avoiding use of java array methods to make everything explicit for hardware conversion
     {slots(i, s[i]); usedSlots(i, u[i]);
     }
   }

  void reset()                                                                  // Reset the slots
   {final int N = numberOfSlots();
    for (int i = 0; i < N; i++)
     {usedSlots(i, false); slots(i, 0);
     }
    for (int i = 0; i < numberOfRefs; i++)
     {usedRefs(i, false); key(i, Key(0));
     }
   }

  void compactLeft()                                                            // Compact the used slots to the left end
   {final int N = numberOfSlots();
    if (empty()) return;                                                        // Nothing to compact
    final Slots d = duplicateSlots();
    reset();
    int p = 0;
    for (int i = 0; i < N; i++)                                                 // Each slot
     {if (d.usedSlots(i))                                                       // Each used slot
       {usedSlots(p, true); usedRefs(p, true);
            slots(p, p);
             keys(p, d.keys(i));
        ++p;
       }
     }
   }

  void compactRight()                                                           // Squeeze the used slots to the left end
   {if (empty()) return;                                                        // Nothing to squeeze
    final Slots d = duplicateSlots(); reset();
    int p = numberOfRefs - 1;
    for (int i = numberOfSlots() - 1; i >= 0; --i)
     {if (d.usedSlots(i))
       {usedSlots(p, true); usedRefs(p, true);
            slots(p, p);
             keys(p, d.keys(i));
        --p;
       }
     }
   }

  void mergeCompacted(Slots Left, Slots Right)                                  // Merge left and right compacted slots into the current slots
   {final Slots l = Left, r = Right;
    reset();
    for (int i = 0; i < numberOfRefs; ++i)
     {if (l.usedSlots(i))
       {    slots(i, l.    slots(i));
        usedSlots(i, l.usedSlots(i));
         usedRefs(i, l. usedRefs(i));
             keys(i, l.     keys(i));
       }
      else if (r.usedSlots(i))
       {    slots(i, r.    slots(i));
        usedSlots(i, r.usedSlots(i));
         usedRefs(i, r. usedRefs(i));
             keys(i, r.     keys(i));
       }
      else {usedSlots(i, false); usedRefs(i, false);}
     }
   }

  boolean mergeOnRight(Slots Right)                                             // Merge the specified slots from the right
   {if (countUsed() + Right.countUsed() > numberOfSlots()) return false;
    final Slots l = duplicateSlots(), r = Right.duplicateSlots();
    l.compactLeft(); r.compactRight();
    mergeCompacted(l, r);
    return true;
   }

  boolean mergeOnLeft(Slots Left)                                               // Merge the specified slots from the left
   {if (Left.countUsed() + countUsed() > numberOfSlots()) return false;
    final Slots l = Left.duplicateSlots(), r = duplicateSlots();
    l.compactLeft(); r.compactRight();
    mergeCompacted(l, r);
    return true;
   }

//D1 High level operations                                                      // Find, insert, delete values in the slots

  public Integer insert(Key Key)                                                // Insert a key into the slots maintaining the order of all the keys in the slots and returning the index of the reference to the key
   {final int N = numberOfSlots();
    if (full()) return null;                                                    // No slot available in which to insert a new key
    final int slot = allocRef();                                                // The location in which to store the search key
    key(slot, Key);                                                             // Store the new key in the referenced location
    final Locate l = new Locate(Key);                                           // Search for the slot containing the key closest to their search key
    if ( l.above && l.below) {}                                                 // Found
    else if (!l.above && !l.below)                                              // Empty place the key in the middle
     {slots    (N/2, slot);
      usedSlots(N/2, true);
     }
    else if (l.above)                                                           // Insert their key above the found key
     {final int i = l.at;
      final int w = locateNearestFreeSlot(i);                                   // Width of move and direction needed to liberate a slot here - we know there is one because we know the slots are not full
      if (w > 0)                                                                // Move up
       {shift    (i+1, w-1);                                                    // Liberate a slot at this point
        slots    (i+1, slot);                                                   // Place their current key in the empty slot, it has already been marked as set so there is no point in setting it again
        usedSlots(i+1, true);
       }
      else if (w < 0)                                                           // Liberate a slot below the current slot
       {shift(i, w);                                                            // Shift any intervening slots blocking the slot below
        slots(i, slot);                                                         // Insert into the slot below
       }
      if (java.lang.Math.abs(w) >= redistributionWidth) redistribute();         // Redistribute if the used slots are densely packed
     }
    else if (l.below)                                                           // Insert their key below the found key
     {final int i = l.at;
      final int w = locateNearestFreeSlot(i);                                   // Width of move and direction needed to liberate a slot here - we know there is one because we know the slots are not full
      if (w > 0)                                                                // Move up
       {shift(i, w);                                                            // Liberate a slot at this point
        slots(i, slot);                                                         // Place their current key in the empty slot, it has already been marked as set so there is no point in setting it again
       }
      else if (w < 0)                                                           // Liberate a slot below the current slot
       {shift    (i-1, w + 1);                                                  // Shift any intervening slots blocking the slot below
        slots    (i-1, slot);                                                   // Insert into the slot below
        usedSlots(i-1, true);                                                   // Mark the free slot at the start of the range of occupied slots as now in use
       }
      if (java.lang.Math.abs(w) >= redistributionWidth) redistribute();         // Redistribute if the used slots are densely packed
     }
    return slot;                                                                // The index of the reference to the key
   }

  class Locate                                                                  // Locate the slot containing the search key if possible else the key immediately above or below the search key.
   {int at;                                                                     // The point at which the closest key was found
    boolean above;                                                              // The search key is above or equal to the found key
    boolean below;                                                              // The search key is below or equal to the found key
    boolean all;                                                                // Above all or below all if true

    public String toString()                                                    // Print the location
     {if (exact()) return String.format("%d exact", at);
      return String.format("%2d %s %s %s", at, above ? "above" : "",
                                               below ? "below" : "",
                                               all   ? "all"   : "");
     }

    void pos(int At, boolean Above, boolean Below)                              // Specify the position of the location
     {at = At; above = Above; below = Below;
     }

    void above(int At) {pos(At, true, false);}                                  // Their search key is above this key
    void below(int At) {pos(At, false, true);}                                  // Their search key is below this key
    void found(int At) {pos(At, true,  true);}                                  // Found their search key
    void none ()       {}                                                       // Slots are empty

    boolean exact() {return above && below;}                                    // Oh America - my new found land.

    Locate(Key Key)                                                             // Locate the slot containing the search key if possible.
     {final int N = numberOfSlots();
      if (empty()) {none(); return;}                                            // Empty so their search key cannot be found
      Integer a = locateNextUsedSlot(0),b = locatePrevUsedSlot(N-1);            // Lower limit, upper limit
      if ( eq(Key, a)) {found(a); return;}                                      // Found at the start of the range
      if ( eq(Key, b)) {found(b); return;}                                      // Found at the end of the range
      if ( le(Key, a)) {below(a); all = true; return;}                          // Smaller than any key
      if (!le(Key, b)) {above(b); all = true; return;}                          // Greater than any key

      for(int i = 0; i < N; ++i)                                                // Perform a reasonable number of searches knowing the key, if it is present, is within the current range. NB this is not a linear search, the slots are searched using binary search with an upper limit that has fooled some reviewers into thinking that a linear search is being performed.
       {final int M = (a + b) / 2;                                              // Desired mid point - but there might not be a slot in use at this point
        final int ma = locatePrevUsedSlot(M);                                   // Occupied slot preceding mid point
        final int mb = locateNextUsedSlot(M);                                   // Occupied slot succeeding mid point

        if      (ma != a && ge(Key, ma)) a = ma;
        else if (ma != b && le(Key, ma)) b = ma;
        else if (mb != a && ge(Key, mb)) a = mb;
        else if (mb != b && le(Key, mb)) b = mb;
        else                                                                    // The slots must be adjacent
         {if (eq(Key, a)) {found(a); return;};                                  // Found the search key at the lower end
          if (eq(Key, b)) {found(b); return;};                                  // Found the search key at the upper end
          below(b);
          return;
         }                                                                      // New mid point
       }
      stop("Searched more than the maximum number of times:", N);
     }
   }

  Integer locateFirstGe(Key Key)                                                // Locate the slot containing the first key greater than or equal to the search key
   {final Locate l = new Locate(Key);
    if (l.below) return l.at;
    return locateNextUsedSlot(l.at+1);
   }

  public Integer locate(Key Key)                                                // Locate the slot containing the current search key if possible.
   {final Locate l = new Locate(Key);                                           // Locate the search key
    if (l.exact()) return l.at;                                                 // Found
    return null;                                                                // Not found
   }

  public Integer find(Key Key)                                                  // Find the index of the current key in the slots
   {final Integer i = locate(Key);
    return i == null ? null : slots(i);
   }

  public boolean delete(Key Key)                                                // Delete the specified key
   {final Integer i = locate(Key);                                              // Locate the search key
    if (i == null) return false;                                                // Their key is not in the slots
    clearSlotAndRef(i);                                                         // Delete key
    return true;                                                                // Indicate that the key was deleted
   }

//D1 Print                                                                      // Print the bit slot

  protected String printSlots()                                                 // Print the occupancy of each slot
   {final int N = numberOfSlots();
    final StringBuilder s = new StringBuilder();
    for (int i = 0; i < N; i++) s.append(usedSlots(i) ? "X" : ".");
    return ""+s;
   }

  protected String dump()                                                       // Dump the slots
   {final StringBuilder s = new StringBuilder();
    final int N = numberOfSlots(), R = numberOfRefs;
    s.append(String.format("Slots    : name: %2d, type: %2d, refs: %2d\n", name(), type(), R));
    s.append("positions: ");
    for (int i = 0; i < N; i++) s.append(String.format(" "+formatKey, i));
    s.append("\nslots    : ");
    for (int i = 0; i < N; i++) s.append(String.format(" "+formatKey, slots(i)));
    s.append("\nusedSlots: ");
    for (int i = 0; i < N; i++) s.append(usedSlots(i) ? "   X" : "   .");
    s.append("\nusedRefs : ");
    for (int i = 0; i < R; i++) s.append(usedRefs (i) ? "   X" : "   .");
    s.append("\nkeys     : ");
    for (int i = 0; i < R; i++) s.append(String.format(" "+formatKey, key(i) != null ? key(i).value() : 0));
    return ""+s+"\n";
   }

  public String toString()                                                      // Print the values in the used slots
   {final StringJoiner s = new StringJoiner(", ");
    final int N = numberOfSlots();
    for (int i = 0; i < N; i++)
     {if (usedSlots(i)) s.add(""+keys(i).value());
     }
    return ""+s;
   }

//D1 Memory                                                                     // Read and write from an array of bytes

  class SlotsMemoryPositions                                                         // Positions of fields in memory
   {final int N = numberOfSlots(), R = numberOfRefs;
    final int posType         = 0;
    final int posSlots        = posType      + Integer.BYTES;
    final int posUsedSlots    = posSlots     + Integer.BYTES * N;
    final int posUsedRefs     = posUsedSlots + N;
    final int posKeys         = posUsedRefs  + R;
    final int posName         = posKeys      + Integer.BYTES * R;
    final int size            = posName      + Integer.BYTES;
   }

  class Memory extends SlotsMemoryPositions                                     // Memory required to hold bytes
   {final ByteBuffer bytes;                                                     // Bytes used by this set of slots

    void copy(Memory Memory)                                                    // Copy a set of slots from the specified memory into this memory
     {for (int i = 0; i < size; i++) bytes.put(i, Memory.bytes.get(i));
     }

    void invalidate()                                                           // Invalidate the slots in such away that they are unlikely to work well if subsequently used
     {for (int i = 0; i < size; i++) bytes.put(i, (byte)-1);
     }

    Memory()                                                                    // Create our own memory for testing
     {bytes = ByteBuffer.allocate(size);
     }

    Memory(ByteBuffer Bytes)                                                    // Use a specified memory
     {bytes = Bytes;
     }

    int     slots       (int Index) {return bytes.getInt(posSlots        + Index * Integer.BYTES);}
    boolean usedSlots   (int Index) {return bytes.get   (posUsedSlots    + Index                ) > 0 ? true : false;}
    boolean usedRefs    (int Index) {return bytes.get   (posUsedRefs     + Index                ) > 0 ? true : false;}
    int     keys        (int Index) {return bytes.getInt(posKeys         + Index * Integer.BYTES);}
    int     name        (         ) {return bytes.getInt(posName                                );}

    void    slots       (int Index, int     Value) {bytes.putInt(posSlots        + Index * Integer.BYTES, Value);}
    void    usedSlots   (int Index, boolean Value) {bytes.put   (posUsedSlots    + Index                , Value ? (byte)1 : (byte)0);}
    void    usedRefs    (int Index, boolean Value) {bytes.put   (posUsedRefs     + Index                , Value ? (byte)1 : (byte)0);}
    void    keys        (int Index, int     Value) {bytes.putInt(posKeys         + Index * Integer.BYTES, Value);}
    void    name        (           int     Value) {bytes.putInt(posName                                , Value);}


    void type(int Type) {       bytes.putInt(posType, Type);}                   // Type of object in which the slots are embedded
    int  type()         {return bytes.getInt(posType);}

   }

  static int memorySize(int NumberOfRefs)                                       // Size of memory for a specified number of references
   {final Slots s = new Slots(NumberOfRefs, false);
    return s.memory.size;
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
    for (int i = 0; i < b.numberOfSlots(); i++) b.setSlots(i);
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
    b.insert(Key(14));  ok(b.empty(), false); ok(b.full(), false);
    b.insert(Key(13));  ok(b.countUsed(), 2);
    b.insert(Key(16));
    b.insert(Key(15));
    b.insert(Key(18));
    b.insert(Key(17));
    b.insert(Key(12));
    b.insert(Key(11));
    ok(b, "11, 12, 13, 14, 15, 16, 17, 18");
    ok(b.empty(), false);
    ok(b.full(), true);
    ok(b.dump(), """
Slots    : name:  0, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   0   7   6   1   0   3   2   5   4   0   0   0
usedSlots:    .   .   .   .   .   X   X   X   X   X   X   X   X   .   .   .
usedRefs :    X   X   X   X   X   X   X   X
keys     :   14  13  16  15  18  17  12  11
""");
    ok(b.locate(Key(11)),  5);
    ok(b.locate(Key(12)),  6);
    ok(b.locate(Key(13)),  7);
    ok(b.locate(Key(14)),  8);
    ok(b.locate(Key(15)),  9);
    ok(b.locate(Key(16)), 10);
    ok(b.locate(Key(17)), 11);
    ok(b.locate(Key(18)), 12);
    ok(b.locate(Key(10)), null);
    ok(b.locate(Key(20)), null);

    ok(b.key(b.find(Key(14))).value(), 14); ok(b.delete(Key(14)), true); ok(b, "11, 12, 13, 15, 16, 17, 18");
    ok(b.key(b.find(Key(12))).value(), 12); ok(b.delete(Key(12)), true); ok(b, "11, 13, 15, 16, 17, 18");
    ok(b.key(b.find(Key(13))).value(), 13); ok(b.delete(Key(13)), true); ok(b, "11, 15, 16, 17, 18");
    ok(b.key(b.find(Key(16))).value(), 16); ok(b.delete(Key(16)), true); ok(b, "11, 15, 17, 18");
    ok(b.key(b.find(Key(18))).value(), 18); ok(b.delete(Key(18)), true); ok(b, "11, 15, 17");
    ok(b.key(b.find(Key(11))).value(), 11); ok(b.delete(Key(11)), true); ok(b, "15, 17");
    ok(b.key(b.find(Key(17))).value(), 17); ok(b.delete(Key(17)), true); ok(b, "15");
    ok(b.key(b.find(Key(15))).value(), 15); ok(b.delete(Key(15)), true); ok(b, "");

    ok(b.locate(Key(10)), null); ok(b.delete(Key(10)), false);
   }

  static void test_idn()                                                        // Repeated inserts and deletes
   {final Slots b = new Slots(8);

    for (int i = 0; i < b.numberOfSlots()*10; i++)
     {b.insert(Key(14)); b.redistribute();
      b.insert(Key(13)); b.redistribute();
      b.insert(Key(16)); b.redistribute();
      b.insert(Key(15)); b.redistribute();
      ok(b, "13, 14, 15, 16");
      ok(b.countUsed(), 4);
      b.delete(Key(14)); b.redistribute();
      b.delete(Key(13)); b.redistribute();
      b.delete(Key(16)); b.redistribute();
      b.delete(Key(15)); b.redistribute();
      ok(b, "");
      ok(b.countUsed(), 0);
     }
   }

  static void test_tooManySearches()
   {final Slots b = new Slots(8);

    b.insert(Key(10));
    b.insert(Key(20));
    ok(b.find(Key(15)), null);
   }

  static void test_locateFirstGe()
   {final Slots b = new Slots(8);
    b.usedSlots( 1, true); b.slots( 1, 7); b.usedRefs(7, true); b.key(7, Key(22));
    b.usedSlots( 5, true); b.slots( 5, 4); b.usedRefs(4, true); b.key(4, Key(24));
    b.usedSlots( 9, true); b.slots( 9, 2); b.usedRefs(2, true); b.key(2, Key(26));
    b.usedSlots(14, true); b.slots(14, 0); b.usedRefs(0, true); b.key(0, Key(28));
    ok(b.dump(), """
Slots    : name:  0, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   4   0   0   0   2   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   .   X   .
usedRefs :    X   .   X   .   X   .   .   X
keys     :   28   0  26   0  24   0   0  22
""");
    ok(b.locateFirstGe(Key(23)),    5);
    ok(b.locateFirstGe(Key(24)),    5);
    ok(b.locateFirstGe(Key(25)),    9);
    ok(b.locateFirstGe(Key(30)), null);
   }

  static void test_compactLeft()
   {final Slots b = new Slots(8);
    b.usedSlots( 1, true); b.slots( 1, 7); b.usedRefs(7, true); b.key(7, Key(11));
    b.usedSlots( 5, true); b.slots( 5, 4); b.usedRefs(4, true); b.key(4, Key(12));
    b.usedSlots( 9, true); b.slots( 9, 2); b.usedRefs(2, true); b.key(2, Key(13));
    b.usedSlots(14, true); b.slots(14, 0); b.usedRefs(0, true); b.key(0, Key(14));
    ok(b.dump(), """
Slots    : name:  0, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   4   0   0   0   2   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   .   X   .
usedRefs :    X   .   X   .   X   .   .   X
keys     :   14   0  13   0  12   0   0  11
""");
    b.compactLeft();

    ok(b.dump(), """
Slots    : name:  0, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   1   2   3   0   0   0   0   0   0   0   0   0   0   0   0
usedSlots:    X   X   X   X   .   .   .   .   .   .   .   .   .   .   .   .
usedRefs :    X   X   X   X   .   .   .   .
keys     :   11  12  13  14   0   0   0   0
""");
   }

  static void test_compactRight()
   {final Slots b = new Slots(8);
    b.usedSlots( 1, true); b.slots( 1, 7); b.usedRefs(7, true); b.key(7, Key(11));
    b.usedSlots( 5, true); b.slots( 5, 4); b.usedRefs(4, true); b.key(4, Key(12));
    b.usedSlots( 9, true); b.slots( 9, 2); b.usedRefs(2, true); b.key(2, Key(13));
    b.usedSlots(14, true); b.slots(14, 0); b.usedRefs(0, true); b.key(0, Key(14));
    ok(b.dump(), """
Slots    : name:  0, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   4   0   0   0   2   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   .   X   .
usedRefs :    X   .   X   .   X   .   .   X
keys     :   14   0  13   0  12   0   0  11
""");
    b.compactRight();
    ok(b.dump(), """
Slots    : name:  0, type:  0, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   0   0   0   4   5   6   7   0   0   0   0   0   0   0   0
usedSlots:    .   .   .   .   X   X   X   X   .   .   .   .   .   .   .   .
usedRefs :    .   .   .   .   X   X   X   X
keys     :    0   0   0   0  11  12  13  14
""");

    ok(b.firstKey().value(), 11);
    ok(b. lastKey().value(), 14);
   }

  static void test_memory()
   {final Slots      b = new Slots(8, ByteBuffer.allocate(200));

    b.usedSlots( 1, true); b.slots( 1, 7); b.usedRefs(7, true); b.key(7, Key(11));
    b.usedSlots( 5, true); b.slots( 5, 4); b.usedRefs(4, true); b.key(4, Key(12));
    b.usedSlots( 9, true); b.slots( 9, 2); b.usedRefs(2, true); b.key(2, Key(13));
    b.usedSlots(14, true); b.slots(14, 0); b.usedRefs(0, true); b.key(0, Key(14));
    b.type     (11);
    ok(b.dump(), """
Slots    : name:  0, type: 11, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   4   0   0   0   2   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   .   X   .
usedRefs :    X   .   X   .   X   .   .   X
keys     :   14   0  13   0  12   0   0  11
""");

    ok(memorySize(8), 128);

    final Slots        B = b.duplicateSlots();
    final Slots.Memory m = B.memory;

    ok(B.dump(), b.dump());

    ok(m.slots       (0), 0);
    ok(m.slots       (1), 7);
    ok(m.slots       (2), 0);
    ok(m.slots       (3), 0);
    ok(m.slots       (4), 0);
    ok(m.slots       (5), 4);
    ok(m.slots       (6), 0);
    ok(m.usedSlots   (0), false);
    ok(m.usedSlots   (1), true);
    ok(m.usedSlots   (2), false);
    ok(m.usedSlots   (3), false);
    ok(m.usedSlots   (4), false);
    ok(m.usedSlots   (5), true);
    ok(m.usedSlots   (6), false);
    ok(m.usedRefs    (0), true);
    ok(m.usedRefs    (1), false);
    ok(m.usedRefs    (2), true);
    ok(m.usedRefs    (3), false);
    ok(m.usedRefs    (4), true);
    ok(m.usedRefs    (5), false);
    ok(m.usedRefs    (6), false);
    ok(m.keys        (0), 14);
    ok(m.keys        (1),  0);
    ok(m.keys        (2), 13);
    ok(m.keys        (3),  0);
    ok(m.keys        (4), 12);
    ok(m.keys        (5),  0);
    ok(m.keys        (6),  0);

    m.slots    (13, 6);
    m.usedSlots(13, true);
    m.usedRefs(  6, true);
    m.keys    (  6, 10);

    ok(B.dump(), """
Slots    : name:  0, type: 11, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   4   0   0   0   2   0   0   0   6   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   X   X   .
usedRefs :    X   .   X   .   X   .   X   X
keys     :   14   0  13   0  12   0  10  11
""");
    ok(B.type(), 11);
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
    test_memory();
   }

  static void newTests()                                                        // Tests being worked on
   {oldTests();
    test_memory();
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
