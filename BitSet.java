//------------------------------------------------------------------------------
// Fixed size bit set
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.util.*;                                                             // Standard utility library.

abstract public class BitSet extends Test                                       // Abstract fixed-size bit set using byte-level storage.
 {final int bitSize;                                                            // Number of bits in the logical set.
  final int byteSize;                                                           // Number of bytes in the logical set.

  public BitSet(int BitSize)                                                    // Constructor specifying fixed size.
   {bitSize = BitSize;                                                          // Record size.
    if (bitSize < 0) stop("Size must be zero or positive");                     // Validate size.
    byteSize = bytesNeeded(BitSize);                                            // A tree of bits
   }

  abstract void setByte(int Index, byte Value);                                 // Write byte to storage backend.
  abstract byte getByte(int Index);                                             // Read byte from storage backend.

  public static int bytesNeeded(int Size)                                       // Number of bytes needed for a bit set of specified size. Need twice as many bits as specified to construct a tree of bits which must then be rounded up to the next byte.
   {return (Byte.SIZE - 1 + 2 * Size) / Byte.SIZE;
   }

  int size()                    {return bitSize;}                               // Bit set size.
  int bitOffset(int bitIndex)   {return bitIndex & 7;}                          // Offset inside byte.
  int byteIndex(int bitIndex)   {return bitIndex >>> 3;}                        // Byte index in storage.

  void checkIndex(int Index)                                                    // Validate bit index.
   {if (Index < 0 || Index >= bitSize)
     {stop("Index out of bounds, index:", Index, "bounds:", bitSize);
     }
   }

  public boolean getBit(int Index)                                              // Get bit value.
   {checkIndex(Index);                                                          // Validate index.
    final int bIndex = byteIndex(Index);                                        // Compute byte position.
    final int offset = bitOffset(Index);                                        // Compute bit offset.

    final byte b = getByte(bIndex);                                             // Load byte.
    return ((b >>> offset) & 1) != 0;                                           // Extract bit.
   }

  public void setBit(int Index, boolean value)                                  // Set bit value.
   {checkIndex(Index);                                                          // Validate index.
    final int bIndex = byteIndex(Index);                                        // Compute byte position.
    final int offset = bitOffset(Index);                                        // Compute bit offset.
    final byte b = getByte(bIndex);                                             // Load byte.
    setByte(bIndex, (byte) (value ? b | (1 << offset) : b & ~(1 << offset)));   // Modify bit.
   }

  public void setPath(int Index)                                                // Set bits along path to index
   {int b = Index, p = 0, r = bitSize;
    for(int i : range(bitSize))                                                 // Much more than necessary
     {setBit(p+b, true);                                                        // Validate index.
      p += r;
      r >>>= 1; if (r == 0) break;
      b >>>= 1;
     }
   }

  public void clearAll()                                                        // Clear all bits.
   {final int bytes = (bitSize + 7) >>> 3;                                      // Compute number of bytes.

    for (int i = 0; i < bytes; i++) setByte(i, (byte) 0);                       // Zero storage.
   }

  public String toString()                                                      // Clear all bits.
   {final StringBuilder s = new StringBuilder();                                      // Compute number of bytes.
    int p = 0, r = bitSize;

    for   (int i : range(1, bitSize))
     {s.append(f("%4d", i));                                                                                //
      for (int j : range(r)) s.append(f(" %1d", getBit(p + j)));                                                                                //
      s.append("\n");
      p += r; r >>>= 1;
      if (r == 0) break;
     }
    return ""+s;
   }

//D1 Tests                                                                      // Tests

  static BitSet test_bits()                                                     // Create test bitset.
   {final int N = 23;                                                           // Test size.
    final byte[]bytes = new byte[BitSet.bytesNeeded(N)];                        // Allocate backing storage.

    final BitSet b = new BitSet(21)                                             // Instantiate anonymous implementation.
     {void setByte(int Index, byte Value) {bytes[Index] = Value;}               // Backend write.
      byte getByte(int Index)      {return bytes[Index];}                       // Backend read.
     };
    return b;                                                                   // Return test bitset.
   }

  static void test_bitSet()                                                     // Test bit manipulation.
   {final BitSet b = test_bits();                                               // Get test bitset.
    final int N = b.size();                                                     // Get logical size.

    for (int i = 0; i < N; i++) b.setBit(i, i % 2 == 0);                        // Set alternating bits.

    ok(b.getBit(4), true);                                                      // Verify bit 4.
    ok(b.getBit(5), false);                                                     // Verify bit 5.
   }

  static void oldTests()                                                        // Tests thought to be stable.
   {test_bitSet();                                                              // Run bitset test.
   }

  static void newTests()                                                        // Tests under development.
   {oldTests();                                                                 // Run baseline tests.
   }

  public static void main(String[] args)                                        // Program entry point for testing.
   {try                                                                         // Protected execution block.
     {if (github_actions) oldTests(); else newTests();                          // Select tests.
      if (coverageAnalysis) coverageAnalysis(12);                               // Optional coverage analysis.
      testSummary();                                                            // Summarize test results.
      System.exit(testsFailed);                                                 // Exit with status.
     }
     catch(Exception e)                                                         // Exception reporting.
     {System.err.println(e);                                                    // Print exception.
      System.err.println(fullTraceBack(e));                                     // Print traceback.
      System.exit(1);                                                           // Exit failure if an exception occurred.
     }
   }
 }
