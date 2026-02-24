//------------------------------------------------------------------------------
// Maintain key references in ascending order using distributed slots
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//------------------------------------------------------------------------------
// Add random inserts/deletes to stress locate/insert/delete
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.
import java.util.*;
import java.nio.ByteBuffer;

public class Slots extends Test                                                 // Maintain key references in ascending order using distributed slots
 {private final int        numberOfRefs;                                        // Number of references which should be equal to or smaller than the numnber of slots as slots are narrow and refences are wide allowing us to use more slots effectively
  private final int redistributionWidth;                                        // Redistribute if the next slot is further than this
  protected Memory               memory;                                        // Memory used by the slots. Cannot be final until we can call stuff before constructing super
  final static String         formatKey = "%3d";                                // Format a key for dumping during testing
  static boolean                  debug = false;                                // Debug if true

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

  static Slots fake(Slot Name)                                                  // Slots used during testing to mock attached branches and leaves
   {final Slots s = new Slots(0);
    s.name(Name);
    return s;
   }

  Slots duplicateSlots()                                                        // Copy the source slots
   {final Slots t = new Slots(numberOfRefs);
    t.copySlots(this);
    return t;                                                                   // The copied slots
   }

  Slots copySlots(Slots Source)                                                 // Copy the source slots
   {final Slot n = name();                                                      // Save name of target
    memory.copySlots(Source.memory);                                            // Copy memory
    name(n);                                                                    // Reload name of target
    return this;                                                                // The copied slots
   }

  void invalidate()   {memory.invalidate();}                                    // Invalidate the slots in such away that they are unlikely to work well if subsequently used
  int numberOfRefs()  {return numberOfRefs;}
  int numberOfSlots() {return numberOfRefs() * 2;}                              // Number of slots from number of ref

  public static final class Slot                                                // A reference to slots
   {final int value;
    Slot( int Value)  {value = Value;}                                          // A key
    int       value() {return  value;}
   }

//D2 Keys                                                                       // Define a key

  public static final class Key
   {final int value;
    Key(  int Value)  {value = Value;}                                          // A key
    int       value() {return  value;}
   }

  static Key    Key(int Key)   {return new Key(Key);}

//D2 Slots                                                                      // Manage the slots

  void setSlots(int...Slots)                                                    // Set slots as used
   {for (int i = 0; i < Slots.length; i++) usedSlots(new Slot(Slots[i]), true);
   }

  void clearSlots(int...Slots)                                                  // Set slots as not being used
   {for (int i = 0; i < Slots.length; i++) usedSlots(new Slot(Slots[i]), false);
   }

  void clearFirstSlot()                                                         // Set the first used slot to not used
   {final int N = numberOfSlots();
    for (int i = 0; i < N; i++)
     {if (usedSlots(new Slot(i)))
       {usedSlots(new Slot(i), false);
        return;
       }
     }
   }

  protected void clearSlotAndRef(int  I) {freeRef(memory.slots     (I)); clearSlots(I);}               // Remove a key from the slots
  protected Slot           slots(Slot I) {return  new Slot(memory.slots(I.value()));}                  // The indexed slot
  protected boolean    usedSlots(Slot I) {return  memory.usedSlots (I.value());}                       // The indexed slot usage indicator
  protected boolean     usedRefs(Slot I) {return  memory.usedRefs  (I.value());}                       // The indexed reference usage indicator
  Key                       keys(Slot I) {return  new Key(memory.keys(memory.slots(I.value())));}      // The indexed key

  protected void     slots(Slot I, Slot    Ref)   {memory.slots    (I.value(), Ref.value());}          // The indexed slot
  protected void usedSlots(Slot I, boolean Value) {memory.usedSlots(I.value(), Value);}                // The indexed slot usage indicator
  protected void  usedRefs(Slot I, boolean Value) {memory.usedRefs (I.value(), Value);}                // The indexed reference usage indicator
            void      keys(Slot I, Key     Key)   {memory.keys(memory.slots(I.value()), Key.value());} // The indexed key

  protected Key  key(Slot I)         {return new Key(memory.keys(I.value()));}  // Get the key directly
  protected void key(Slot I, Key Key) {memory.keys(I.value(), Key.value());}    // Set the key directly

  Slot name() {return new Slot(memory.name());}                                 // Get the name
  void name(Slot Name) {memory.name(Name.value());}                             // Set the name

  int  type()  {return memory.type();}                                          // Get the type
  void type(int Type) {memory.type(Type);}                                      // Set the type

//D2 Refs                                                                       // Allocate and free references to keys

  private int allocRef()                                                        // Allocate a reference to one of their keys. A linear search is used here because in hardware this will be done in parallel
   {for (int i = 0; i < numberOfRefs; i++)
     {final Slot I = new Slot(i);
      if (!usedRefs(I))
       {usedRefs(I, true);
        return i;
       }
     }
    stop("No more slots available");
    return -1;
   }

  private void freeRef(int Ref) {usedRefs(new Slot(Ref), false);}               // Free a reference to one of their keys - java checks for array bounds sdo no point in an explicit check.

//D1 Keys                                                                       // Operations on keys

  boolean eq(Key Key, Slot Slot) {return Key.value() == keys(Slot).value();}    // Search key is equal to indexed key
  boolean le(Key Key, Slot Slot) {return Key.value() <= keys(Slot).value();}    // Search key is less than or equal to indexed key
  boolean lt(Key Key, Slot Slot) {return !eq(Key, Slot) && le(Key, Slot);}      // Search key is less than or equal to indexed key
  boolean ge(Key Key, Slot Slot) {return  eq(Key, Slot) || gt(Key, Slot);}      // Search key is less than or equal to indexed key
  boolean gt(Key Key, Slot Slot) {return !le(Key, Slot);}                       // Search key is less than or equal to indexed key

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
    for (int i = 0; i < N; i++) if (usedSlots(new Slot(i))) ++n;
    return n;
   }

  boolean empty() {return countUsed() == 0;}                                    // All references are unused
  boolean full()  {return countUsed() == numberOfRefs;}                         // All references are in use

  boolean adjacentUsedSlots(int Start, int Finish)                              // Checks wether two used slots are adjacent
   {if (!usedSlots(new Slot(Start)))  stop("Start  slot  must be occupied but it is empty, slot:", Start);
    if (!usedSlots(new Slot(Finish))) stop("Finish slot  must be occupied but it is empty, slot:", Finish);
    if (Start >= Finish)    stop("Start must precede finish:", Start, Finish);

    for (int i = Start+1; i < Finish; i++) if (usedSlots(new Slot(i))) return false; // From start to finish looking for an intermediate used slot
    return true;
   }

//D1 Low level operations                                                       // Low level operations on slots

  Integer locateNearestFreeSlot(Slot Position)                                  // Relative position of the nearest free slot to the indicated position if there is one.
   {final int N = numberOfSlots();
    if (!usedSlots(Position)) return 0;                                         // The slot is free already. If it is not free we do at least get an error if the specified position is invalid
    for (int i = 1; i < N; i++)
     {final int p = Position.value() + i, q = Position.value() - i;
      if (Integer.compare(q, 0) != -1 && !usedSlots(new Slot(q))) return -i;    // Look down preferentially to avoid moving the existing key if possible
      if (Integer.compare(p, N) == -1 && !usedSlots(new Slot(p))) return +i;    // Look up
     }
    return null;                                                                // No free slot - this is not actually an error.
   }

  Slot locateFirstUsedSlot()                                                    // Absolute position of the first slot in use
   {final int N = numberOfSlots();
    for (int i = 0; i < N; ++i)                  if ( usedSlots(new Slot(i))) return new Slot(i);
    return null;                                                                // No free slot
   }

  Slot locateLastUsedSlot()                                                     // Absolute position of the last slot in use
   {for (int i = numberOfSlots()-1; i >= 0; i--) if ( usedSlots(new Slot(i))) return new Slot(i);
    return null;                                                                // No free slot
   }

  Integer locatePrevUsedSlot(Slot Position)                                     // Absolute position of this slot if it is in use or else the next lower used slot
   {for (int i = Position.value(); i >= 0; i--)  if ( usedSlots(new Slot(i))) return i;
    return null;                                                                // No free slot
   }

  Integer locateNextUsedSlot(Slot Position)                                     // Absolute position of this slot if it is in use or else the next higher used slot
   {final int N = numberOfSlots();
    for (int i = Position.value(); i < N; ++i)   if ( usedSlots(new Slot(i))) return i;
    return null;                                                                // No free slot
   }

  Integer locateFirstEmptySlot()                                                // Absolute position of the first free slot
   {final int N = numberOfSlots();
    for (int i = 0; i < N; ++i)                  if (!usedSlots(new Slot(i))) return i;
    return null;                                                                // No free slot
   }

  Integer locateLastEmptySlot()                                                 // Absolute position of the last free slot
   {for (int i = numberOfSlots()-1; i >= 0; i--) if (!usedSlots(new Slot(i))) return i;
    return null;                                                                // No free slot
   }

  Integer locatePrevEmptySlot(int Position)                                     // Absolute position of this slot if it is free or the nearest lower free slot before this position.
   {for (int i = Position; i >= 0; i--)          if (!usedSlots(new Slot(i))) return i;
    return null;                                                                // No free slot
   }

  Integer locateNextEmptySlot(int Position)                                     // Absolute position of this slot if it is in use or the nearest higher free slot after this position.
   {final int N = numberOfSlots();
    for (int i = Position; i < N; ++i)           if (!usedSlots(new Slot(i))) return i;
    return null;                                                                // No free slot
   }

  void shift(int Position, int Width)                                           // Shift the specified number of slots around the specified position one bit left or right depending on the sign of the width.  The liberated slot is not initialized.
   {if (Width > 0)                                                              // Shift up including the current slot
     {for (int i = Width; i > 0; --i)                                           // Move each slot
       {final int p = Position+i;                                               // Index of target
        slots(new Slot(p), slots(new Slot(p-1)));                               // Move slot
       }
      usedSlots(new Slot(Position+Width), true);                                // We only move occupied slots
     }
    else if (Width < 0)                                                         // Shift the preceding slots down.  This reduces the number of moves needed to insert keys in ascending order
     {for (int i = Width; i < 0; ++i)                                           // Move each slot
       {final int p = Position+i;                                               // Index of target
        slots(new Slot(p), slots(new Slot(p+1)));                               // Move slot
       }
      usedSlots(new Slot(Position+Width), true);                                // We only move occupied slots
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
     {final Slot I = new Slot(i);
      if (usedSlots(I))                                                         // Redistribute active slots
       {s[p] = slots(I).value(); u[p] = true; p += space+1;                     // Spread the used slots out
       }
     }
    for(int i = 0; i < N; ++i)                                                  // Copy redistribution back into original avoiding use of java array methods to make everything explicit for hardware conversion
     {final Slot I = new Slot(i);
      slots(I, new Slot(s[i])); usedSlots(I, u[i]);
     }
   }

  void reset()                                                                  // Reset the slots
   {final int N = numberOfSlots();
    for (int i = 0; i < N; i++)
     {final Slot I = new Slot(i);
      usedSlots(I, false); slots(I, new Slot(0));
     }
    for (int i = 0; i < numberOfRefs; i++)
     {usedRefs(new Slot(i), false); key(new Slot(i), Key(0));
     }
   }

  void compactLeft()                                                            // Compact the used slots to the left end
   {final int N = numberOfSlots();
    if (empty()) return;                                                        // Nothing to compact
    final Slots d = duplicateSlots();
    reset();
    int p = 0;
    for (int i = 0; i < N; i++)                                                 // Each slot
     {final Slot I = new Slot(i), P = new Slot(p);
      if (d.usedSlots(I))                                                       // Each used slot
       {usedSlots(P, true); usedRefs(P, true);
            slots(P, P);
             keys(P, d.keys(I));
        ++p;
       }
     }
   }

  void compactRight()                                                           // Squeeze the used slots to the left end
   {if (empty()) return;                                                        // Nothing to squeeze
    final Slots d = duplicateSlots(); reset();
    int p = numberOfRefs - 1;
    for (int i = numberOfSlots() - 1; i >= 0; --i)
     {final Slot I = new Slot(i), P = new Slot(p);
      if (d.usedSlots(I))
       {usedSlots(P, true); usedRefs(P, true);
            slots(P, P);
             keys(P, d.keys(I));
        --p;
       }
     }
   }

  void mergeCompacted(Slots Left, Slots Right)                                  // Merge left and right compacted slots into the current slots
   {final Slots l = Left, r = Right;
    reset();
    for (int i = 0; i < numberOfRefs; ++i)
     {final Slot I = new Slot(i);
       if (l.usedSlots(I))
       {    slots(I, l.    slots(I));
        usedSlots(I, l.usedSlots(I));
         usedRefs(I, l. usedRefs(I));
             keys(I, l.     keys(I));
       }
      else if (r.usedSlots(new Slot(i)))
       {    slots(I, r.    slots(I));
        usedSlots(I, r.usedSlots(I));
         usedRefs(I, r. usedRefs(I));
             keys(I, r.     keys(I));
       }
      else {usedSlots(new Slot(i), false); usedRefs(new Slot(i), false);}
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
    key(new Slot(slot), Key);                                                   // Store the new key in the referenced location
    final Locate l = new Locate(Key);                                           // Search for the slot containing the key closest to their search key
    if ( l.above && l.below) {}                                                 // Found
    else if (!l.above && !l.below)                                              // Empty place the key in the middle
     {slots    (new Slot(N/2), new Slot(slot));
      usedSlots(new Slot(N/2), true);
     }
    else if (l.above)                                                           // Insert their key above the found key
     {final int i = l.at.value();
      final int w = locateNearestFreeSlot(l.at);                                // Width of move and direction needed to liberate a slot here - we know there is one because we know the slots are not full
      if (w > 0)                                                                // Move up
       {shift    (i+1, w-1);                                                    // Liberate a slot at this point
        slots    (new Slot(i+1), new Slot(slot));                               // Place their current key in the empty slot, it has already been marked as set so there is no point in setting it again
        usedSlots(new Slot(i+1), true);
       }
      else if (w < 0)                                                           // Liberate a slot below the current slot
       {shift(i, w);                                                            // Shift any intervening slots blocking the slot below
        slots(new Slot(i), new Slot(slot));                                     // Insert into the slot below
       }
      if (java.lang.Math.abs(w) >= redistributionWidth) redistribute();         // Redistribute if the used slots are densely packed
     }
    else if (l.below)                                                           // Insert their key below the found key
     {final int i = l.at.value();
      final int w = locateNearestFreeSlot(l.at);                                // Width of move and direction needed to liberate a slot here - we know there is one because we know the slots are not full
      if (w > 0)                                                                // Move up
       {shift(i, w);                                                            // Liberate a slot at this point
        slots(l.at, new Slot(slot));                                            // Place their current key in the empty slot, it has already been marked as set so there is no point in setting it again
       }
      else if (w < 0)                                                           // Liberate a slot below the current slot
       {shift    (i-1, w + 1);                                                  // Shift any intervening slots blocking the slot below
        slots    (new Slot(i-1), new Slot(slot));                               // Insert into the slot below
        usedSlots(new Slot(i-1), true);                                         // Mark the free slot at the start of the range of occupied slots as now in use
       }
      if (java.lang.Math.abs(w) >= redistributionWidth) redistribute();         // Redistribute if the used slots are densely packed
     }
    return slot;                                                                // The index of the reference to the key
   }

  class Locate                                                                  // Locate the slot containing the search key if possible else the key immediately above or below the search key.
   {Slot at;                                                                    // The point at which the closest key was found
    boolean above;                                                              // The search key is above or equal to the found key
    boolean below;                                                              // The search key is below or equal to the found key
    boolean all;                                                                // Above all or below all if true

    public String toString()                                                    // Print the location
     {if (exact()) return String.format("%d exact", at.value());
      return String.format("%2d %s %s %s", at.value(),
                                           above ? "above" : "",
                                           below ? "below" : "",
                                           all   ? "all"   : "");
     }

    void pos(Slot At, boolean Above, boolean Below)                             // Specify the position of the location
     {at = At; above = Above; below = Below;
     }

    void above(Slot At) {pos(At, true, false);}                                 // Their search key is above this key
    void below(Slot At) {pos(At, false, true);}                                 // Their search key is below this key
    void found(Slot At) {pos(At, true,  true);}                                 // Found their search key
    void none ()       {}                                                       // Slots are empty

    boolean exact() {return above && below;}                                    // Oh America - my new found land.

    Locate(Key Key)                                                             // Locate the slot containing the search key if possible.
     {final int N = numberOfSlots();
      if (empty()) {none(); return;}                                            // Empty so their search key cannot be found
      Slot a = locateFirstUsedSlot(), b = locateLastUsedSlot();                 // Lower limit, upper limit
      if ( eq(Key, a)) {found(a); return;}                                      // Found at the start of the range
      if ( eq(Key, b)) {found(b); return;}                                      // Found at the end of the range
      if ( le(Key, a)) {below(a); all = true; return;}                          // Smaller than any key
      if (!le(Key, b)) {above(b); all = true; return;}                          // Greater than any key

      for(int i = 0; i < N; ++i)                                                // Perform a reasonable number of searches knowing the key, if it is present, is within the current range. NB this is not a linear search, the slots are searched using binary search with an upper limit that has fooled some reviewers into thinking that a linear search is being performed.
       {final Slot M = new Slot((a.value() + b.value()) / 2);                   // Desired mid point - but there might not be a slot in use at this point
        final int ma = locatePrevUsedSlot(M);                                   // Occupied slot preceding mid point
        final int mb = locateNextUsedSlot(M);                                   // Occupied slot succeeding mid point

        if      (ma != a.value() && ge(Key, new Slot(ma))) a = new Slot(ma);
        else if (ma != b.value() && le(Key, new Slot(ma))) b = new Slot(ma);
        else if (mb != a.value() && ge(Key, new Slot(mb))) a = new Slot(mb);
        else if (mb != b.value() && le(Key, new Slot(mb))) b = new Slot(mb);
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
    if (l.at == null) return null;
    if (l.below) return l.at.value();
    return locateNextUsedSlot(new Slot(l.at.value()+1));
   }

  public Integer locate(Key Key)                                                // Locate the slot containing the current search key if possible.
   {final Locate l = new Locate(Key);                                           // Locate the search key
    if (l.exact()) return l.at.value();                                         // Found
    return null;                                                                // Not found
   }

  public Integer find(Key Key)                                                  // Find the index of the current key in the slots
   {final Integer i = locate(Key);
    return i == null ? null : slots(new Slot(i)).value();
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
    for (int i = 0; i < N; i++) s.append(usedSlots(new Slot(i)) ? "X" : ".");
    return ""+s;
   }

  protected String dump()                                                       // Dump the slots
   {final StringBuilder s = new StringBuilder();
    final int N = numberOfSlots(), R = numberOfRefs;
    s.append(String.format("Slots    : name: %2d, type: %2d, refs: %2d\n",      // Title line
                            name().value(), type(), R));
    s.append("positions: ");
    for (int i = 0; i < N; i++) s.append(String.format(" "+formatKey, i));
    s.append("\nslots    : ");
    for (int i = 0; i < N; i++) s.append(String.format(" "+formatKey, slots(new Slot(i)).value()));
    s.append("\nusedSlots: ");
    for (int i = 0; i < N; i++) s.append(usedSlots(new Slot(i)) ? "   X" : "   .");
    s.append("\nusedRefs : ");
    for (int i = 0; i < R; i++) s.append(usedRefs (new Slot(i)) ? "   X" : "   .");
    s.append("\nkeys     : ");
    for (int i = 0; i < R; i++) s.append(String.format(" "+formatKey, key(new Slot(i)) != null ? key(new Slot(i)).value() : 0));
    return ""+s+"\n";
   }

  public String toString()                                                      // Print the values in the used slots
   {final StringJoiner s = new StringJoiner(", ");
    final int N = numberOfSlots();
    for (int i = 0; i < N; i++)
     {if (usedSlots(new Slot(i))) s.add(""+keys(new Slot(i)).value());
     }
    return ""+s;
   }

//D1 Memory                                                                     // Read and write from an array of bytes

  class SlotsMemoryPositions                                                    // Positions of fields in memory
   {final int N = numberOfSlots(), R = numberOfRefs;
    final int posType         = 0;
    final int posSlots        = posType      + Integer.BYTES;
    final int posUsedSlots    = posSlots     + Integer.BYTES * N;
    final int posUsedRefs     = posUsedSlots + BitSet.bytesNeeded(N);           // Amount of space needed to store these bits in bytes
    final int posKeys         = posUsedRefs  + BitSet.bytesNeeded(R);
    final int posName         = posKeys      + Integer.BYTES * R;
    final int size            = posName      + Integer.BYTES;
   }

  class Memory extends SlotsMemoryPositions                                     // Memory required to hold bytes
   {final ByteBuffer bytes;                                                     // Bytes used by this set of slots
    final BitSet usedSlotsBits = new BitSet(numberOfSlots())                    // Bit storage for used slots
     {void setByte(int Index, byte Value) {bytes.put(posUsedSlots+Index,Value);}// Save used slot bit
      byte getByte(int Index)      {return bytes.get(posUsedSlots+Index);}      // Get used slot bit
     };
    final BitSet usedRefsBits  = new BitSet(numberOfRefs)                       // Bit storage for used refs
     {void setByte(int Index, byte Value) {bytes.put(posUsedRefs+Index, Value);}// Save used ref bit
      byte getByte(int Index)      {return bytes.get(posUsedRefs+Index);}       // Get used ref bit
     };

    void copySlots(Memory Memory)                                               // Copy a set of slots from the specified memory into this memory
     {for (int i = 0; i < size; i++)
       {final byte b = Memory.bytes.get(i);
        bytes.put(i, b);
       }
     }

    void invalidate()                                                           // Invalidate the slots in such away that they are unlikely to work well if subsequently used
     {for (int i = 0; i < size; i++) bytes.put(i, (byte)-1);
     }

    void clear()                                                                // Clear all bytes in memory to zero which has the beneficial efefect of setting all slots to unused
     {for (int i = 0; i < size; i++) bytes.put(i, (byte)0);
     }

    Memory()                                                                    // Create our own memory for testing
     {bytes = ByteBuffer.allocate(size);
     }

    Memory(ByteBuffer Bytes)                                                    // Use a specified memory
     {bytes = Bytes;
     }

    int     slots       (int Index) {return bytes.getInt(posSlots + Index * Integer.BYTES);}
    boolean usedSlots   (int Index) {return usedSlotsBits.getBit(Index);}
    boolean usedRefs    (int Index) {return usedRefsBits .getBit(Index);}
    int     keys        (int Index) {return bytes.getInt(posKeys  + Index * Integer.BYTES);}
    int     name        (         ) {return bytes.getInt(posName);}

    void    slots       (int Index, int     Value) {bytes.putInt(posSlots + Index * Integer.BYTES, Value);}
    void    usedSlots   (int Index, boolean Value) {usedSlotsBits.setBit(Index, Value);}
    void    usedRefs    (int Index, boolean Value) {usedRefsBits .setBit(Index, Value);}
    void    keys        (int Index, int     Value) {bytes.putInt(posKeys  + Index * Integer.BYTES, Value);}
    void    name        (           int     Value) {bytes.putInt(posName                         , Value);} // Save the name of the node in memory to assist debugging

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
    ok(b.locateNearestFreeSlot(new Slot( 0)),  0);
    ok(b.locateNearestFreeSlot(new Slot( 1)),  0);
    ok(b.locateNearestFreeSlot(new Slot( 2)), -1);
    ok(b.locateNearestFreeSlot(new Slot( 3)), +1);
    ok(b.locateNearestFreeSlot(new Slot( 4)),  0);
    ok(b.locateNearestFreeSlot(new Slot( 5)), -1);
    ok(b.locateNearestFreeSlot(new Slot( 6)), -2);
    ok(b.locateNearestFreeSlot(new Slot( 8)),  0);
    ok(b.locateNearestFreeSlot(new Slot( 9)), -1);
    ok(b.locateNearestFreeSlot(new Slot(10)),  0);
    ok(b.locateNearestFreeSlot(new Slot(11)), -1);
    ok(b.locateNearestFreeSlot(new Slot(12)),  0);
    ok(b.locateNearestFreeSlot(new Slot(13)), -1);
    ok(b.locateNearestFreeSlot(new Slot(14)),  0);
    ok(b.locateNearestFreeSlot(new Slot(15)),  0);

    ok(b.locateFirstUsedSlot().value(),      2);
    ok(b.locateLastUsedSlot ().value(),      13);
    ok(b.locatePrevUsedSlot(new Slot( 9)),     9);
    ok(b.locatePrevUsedSlot(new Slot(10)),     9);
    ok(b.locateNextUsedSlot(new Slot(10)),    11);
    ok(b.locateNextUsedSlot(new Slot(11)),    11);
    ok(b.locateFirstEmptySlot(),     0);
    ok(b.locateLastEmptySlot(),     15);
    ok(b.locatePrevEmptySlot(4),     4);
    ok(b.locatePrevEmptySlot(5),     4);
    ok(b.locateNextEmptySlot(4),     4);
    ok(b.locateNextEmptySlot(5),     8);

    ok(b.locatePrevUsedSlot (new Slot( 1)),   null);
    ok(b.locateNextUsedSlot (new Slot(14)),   null);

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

    ok(b.key(new Slot(b.find(Key(14)))).value(), 14); ok(b.delete(Key(14)), true); ok(b, "11, 12, 13, 15, 16, 17, 18");
    ok(b.key(new Slot(b.find(Key(12)))).value(), 12); ok(b.delete(Key(12)), true); ok(b, "11, 13, 15, 16, 17, 18");
    ok(b.key(new Slot(b.find(Key(13)))).value(), 13); ok(b.delete(Key(13)), true); ok(b, "11, 15, 16, 17, 18");
    ok(b.key(new Slot(b.find(Key(16)))).value(), 16); ok(b.delete(Key(16)), true); ok(b, "11, 15, 17, 18");
    ok(b.key(new Slot(b.find(Key(18)))).value(), 18); ok(b.delete(Key(18)), true); ok(b, "11, 15, 17");
    ok(b.key(new Slot(b.find(Key(11)))).value(), 11); ok(b.delete(Key(11)), true); ok(b, "15, 17");
    ok(b.key(new Slot(b.find(Key(17)))).value(), 17); ok(b.delete(Key(17)), true); ok(b, "15");
    ok(b.key(new Slot(b.find(Key(15)))).value(), 15); ok(b.delete(Key(15)), true); ok(b, "");

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
    b.usedSlots(new Slot( 1), true); b.slots(new Slot( 1), new Slot(7)); b.usedRefs(new Slot(7), true); b.key(new Slot(7), Key(22));
    b.usedSlots(new Slot( 5), true); b.slots(new Slot( 5), new Slot(4)); b.usedRefs(new Slot(4), true); b.key(new Slot(4), Key(24));
    b.usedSlots(new Slot( 9), true); b.slots(new Slot( 9), new Slot(2)); b.usedRefs(new Slot(2), true); b.key(new Slot(2), Key(26));
    b.usedSlots(new Slot(14), true); b.slots(new Slot(14), new Slot(0)); b.usedRefs(new Slot(0), true); b.key(new Slot(0), Key(28));
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
    b.usedSlots(new Slot( 1), true); b.slots(new Slot( 1), new Slot(7)); b.usedRefs(new Slot(7), true); b.key(new Slot(7), Key(11));
    b.usedSlots(new Slot( 5), true); b.slots(new Slot( 5), new Slot(4)); b.usedRefs(new Slot(4), true); b.key(new Slot(4), Key(12));
    b.usedSlots(new Slot( 9), true); b.slots(new Slot( 9), new Slot(2)); b.usedRefs(new Slot(2), true); b.key(new Slot(2), Key(13));
    b.usedSlots(new Slot(14), true); b.slots(new Slot(14), new Slot(0)); b.usedRefs(new Slot(0), true); b.key(new Slot(0), Key(14));
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
    b.usedSlots(new Slot( 1), true); b.slots(new Slot( 1), new Slot(7)); b.usedRefs(new Slot(7), true); b.key(new Slot(7), Key(11));
    b.usedSlots(new Slot( 5), true); b.slots(new Slot( 5), new Slot(4)); b.usedRefs(new Slot(4), true); b.key(new Slot(4), Key(12));
    b.usedSlots(new Slot( 9), true); b.slots(new Slot( 9), new Slot(2)); b.usedRefs(new Slot(2), true); b.key(new Slot(2), Key(13));
    b.usedSlots(new Slot(14), true); b.slots(new Slot(14), new Slot(0)); b.usedRefs(new Slot(0), true); b.key(new Slot(0), Key(14));
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

    b.usedSlots(new Slot( 1), true); b.slots(new Slot( 1), new Slot(7)); b.usedRefs(new Slot(7), true); b.key(new Slot(7), Key(11));
    b.usedSlots(new Slot( 5), true); b.slots(new Slot( 5), new Slot(4)); b.usedRefs(new Slot(4), true); b.key(new Slot(4), Key(12));
    b.usedSlots(new Slot( 9), true); b.slots(new Slot( 9), new Slot(2)); b.usedRefs(new Slot(2), true); b.key(new Slot(2), Key(13));
    b.usedSlots(new Slot(14), true); b.slots(new Slot(14), new Slot(0)); b.usedRefs(new Slot(0), true); b.key(new Slot(0), Key(14));
    b.type     (11);
    ok(b.dump(), """
Slots    : name:  0, type: 11, refs:  8
positions:    0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
slots    :    0   7   0   0   0   4   0   0   0   2   0   0   0   0   0   0
usedSlots:    .   X   .   .   .   X   .   .   .   X   .   .   .   .   X   .
usedRefs :    X   .   X   .   X   .   .   X
keys     :   14   0  13   0  12   0   0  11
""");

    ok(memorySize(8), 109);

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
// Conversion Steps
// 1 replace array accesses with subroutine calls to the greatest extent possible
// 2 Convert to ByteBuffer memory
// 3 Remove as many opewratror expressions as possible especially assignment.
