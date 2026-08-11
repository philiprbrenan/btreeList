//----------------------------------------------------------------------------------------------------------------------
// Write simple gds files per ChatGPT
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;

public final class Gds extends Test                                                                                     // Minimal GDSII writer. Coordinates supplied to this class are in micrometres. The generated GDS uses: user unit = 1 um, database unit   = 1 nm. Thus a coordinate of 100.0 means 100 um and is stored internally as 100000 GDS database units.
 {static final int HEADER    = 0x0002;
  static final int BGNLIB    = 0x0102;
  static final int LIBNAME   = 0x0206;
  static final int UNITS     = 0x0305;
  static final int ENDLIB    = 0x0400;

  static final int BGNSTR    = 0x0502;
  static final int STRNAME   = 0x0606;
  static final int ENDSTR    = 0x0700;

  static final int BOUNDARY  = 0x0800;
  static final int PATH      = 0x0900;
  static final int SREF      = 0x0A00;
  static final int AREF      = 0x0B00;

  static final int LAYER     = 0x0D02;
  static final int DATATYPE  = 0x0E02;
  static final int WIDTH     = 0x0F03;
  static final int XY        = 0x1003;
  static final int ENDEL     = 0x1100;

  static final int[] dates =
   {2026, 8, 11, 0, 0, 0,
    2026, 8, 11, 0, 0, 0
   };

  DataOutputStream out;

  boolean cellOpen;

  static final long DBU_PER_UM = 1000;                                                                                  // Database units per micrometer

  Gds(String FileName)                                                                                                  // Create a new GDSII file.
   {try
     {out = new DataOutputStream(
              new BufferedOutputStream(
                new FileOutputStream(FileName)));
      writeHeader();
     }
    catch(Exception e) {stop("Cannot open file:", FileName, e, fullTraceBack(e));}
   }

  void writeHeader()                                                                                                    // Write the GDSII library header.
   {int2(HEADER, 600);
    final byte[] data = new byte[24];                                                                                   // Write library header
    for (int i = 0; i < dates.length; ++i) putInt16(data, i * 2, dates[i]);
    record(BGNLIB, data);
    string(LIBNAME, "JavaGDS");                                                                                         // Name of program creating file

    final byte[] units = new byte[16];
    putReal8(units, 0, 1e-6);                                                                                           // UNITS: first real  = size of one user unit in metres, second real = size of one database unit in metres. We use: 1 user unit = 1um = 1e-6m, 1 database unit = 1 nm = 1e-9m.
    putReal8(units, 8, 1e-9);
    record(UNITS, units);
   }

  void beginCell(String name)                                                                                           // Create a new GDS cell.
   {final byte[] data = new byte[24];
    if (cellOpen) stop("A GDS cell is already open");
    for (int i = 0; i < dates.length; ++i) putInt16(data, i * 2, dates[i]);
    record(BGNSTR, data);
    string(STRNAME, name);
    cellOpen = true;
   }

  void endCell()                                                                                                        // Finish the current GDS cell.
   {if (!cellOpen) stop("No GDS cell is open");
    noData(ENDSTR);
    cellOpen = false;
   }

  void rectangle(String cellName, double width, double height, int layer, int datatype)                                 // Write a rectangle whose lower-left corner is (0,0). Dimensions are in micrometres.
   {beginCell(cellName);
    rectangle(0, 0, width, height, layer, datatype);
    endCell();
   }

  void rectangle(double x, double y, double width, double height, int layer, int datatype)                              // Write a rectangle into the currently open cell. x, y, width and height are in micrometres.
   {requireCell();
    final long x1 = dbu(x);
    final long y1 = dbu(y);
    final long x2 = dbu(x + width);
    final long y2 = dbu(y + height);

    noData(BOUNDARY);

    int2(LAYER, layer);
    int2(DATATYPE, datatype);

    xy(x1, y1,
       x2, y1,
       x2, y2,
       x1, y2,
       x1, y1);

    noData(ENDEL);
   }

  void polygon (double[] x, double[] y, int layer, int datatype)                                                        // Write a polygon into the currently open cell. Coordinates are in micrometres. The polygon is automatically closed.
   {requireCell();
    if (x.length != y.length) stop("x and y arrays must have the same length, not x:", x.length, "y:", y.length);
    if (x.length < 3) stop("A polygon needs at least three points");

    noData(BOUNDARY);

    int2(LAYER, layer);
    int2(DATATYPE, datatype);

    final long[] points = new long[(x.length + 1) * 2];

    for (int i = 0; i < x.length; ++i)
     {points[i * 2]     = dbu(x[i]);
      points[i * 2 + 1] = dbu(y[i]);
     }

    points[x.length * 2]     = dbu(x[0]);                                                                               // GDS polygons must be explicitly closed.
    points[x.length * 2 + 1] = dbu(y[0]);

    xy(points);

    noData(ENDEL);
   }

  public void close()                                                                                                   // Close the GDS library.
   {try
     {if (cellOpen) stop("GDS cell is still open");
      noData(ENDLIB);
      out.close();
     }
    catch(Exception e) {stop("Error:", e, fullTraceBack(e));}
   }

  static long dbu(double micrometres) {return Math.round(micrometres * DBU_PER_UM);}                                    // Convert micrometres to GDS database units.

  void requireCell() {if (!cellOpen) stop("No GDS cell is open");}

  void record(int type, byte[] data)                                                                                    // Write a GDS2 record
   {try
     {out.writeShort(data.length + 4);
      out.writeShort(type);
      out.write(data);
     }
    catch(Exception e) {stop("Error:", e, fullTraceBack(e));}
   }

  void noData(int type)  {record(type, new byte[0]);}                                                                   // Write a GDS record containing no data.

  void int2(int type, int value)                                                                                        // Write a 2-byte signed GDS integer
   {try
     {out.writeShort(6);
      out.writeShort(type);
      out.writeShort(value);
     }
    catch(Exception e) {stop("Error:", e, fullTraceBack(e));}
   }

  void string(int type, String value)                                                                                   // Write an ASCII GDS string. GDS strings must have an even number of bytes.
   {byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
    if ((bytes.length & 1) != 0)
     {byte[] padded = new byte[bytes.length + 1];
      System.arraycopy(bytes,  0, padded, 0, bytes.length); bytes = padded;
     }
    record(type, bytes);
   }

  void xy(long... coordinates)                                                                                          // Write XY coordinates.
   {if ((coordinates.length & 1) != 0) stop("Odd number of coordinates");
    final byte[] data = new byte[coordinates.length * 4];
    for (int i = 0; i < coordinates.length; ++i) putInt32(data, i * 4, coordinates[i]);
    record(XY, data);
  }

  static void putInt16(byte[] data, int offset, long value)                                                             // Put a 16-bit big-endian integer into an array.
   {data[offset]     = (byte)(value >>> 8);
    data[offset + 1] = (byte)value;
   }

  static void putInt32(byte[] data, int offset, long value)                                                             // Put a 32-bit big-endian integer into an array.
   {data[offset] = (byte)(value >>> 24);
    data[offset + 1] = (byte)(value >>> 16);
    data[offset + 2] = (byte)(value >>> 8);
    data[offset + 3] = (byte)value;
   }

  static void putReal8 (byte[] data, int offset, double value)                                                          // Encode a GDSII 8-byte real number. GDSII does NOT use IEEE-754 here. The format is: 1 sign/exponent byte 7 mantissa bytes The base is 16.
   {if (value == 0.0)
     {for (int i = 0; i < 8; ++i) data[offset + i] = 0;
      return;
     }

    boolean negative = value < 0;
    double v = Math.abs(value);
    int exponent = 0;

    while (v >= 1.0) {v /= 16.0; ++exponent;}                                                                           // Normalize: 1/16 <= mantissa < 1 such that: value = mantissa * 16^exponent
    while (v < (1.0 / 16.0)) {v *= 16.0; --exponent;}

    long mantissa =  Math.round(v * 72057594037927936.0);                                                         // 2^56 The seven mantissa bytes represent: mantissa * 2^56
    int first = (negative ? 0x80 : 0) | ((exponent + 64) & 0x7f);

    data[offset] = (byte)first;

    for (int i = 7; i >= 1; --i) {data[offset + i] = (byte)mantissa; mantissa >>>= 8;}
   }

  static void test_macros()
   {final String f = tempFile();
    final Gds g = new Gds(f);
    g.rectangle("array_pcConstant",  100, 200, 68, 20);
    g.rectangle("array_pcMatchSet",  100, 200, 68, 20);
    g.close();
    ok(md5SumFile(f), "80eea71a195b450aa837333ab7f03905");
   }
  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_macros();
   }

  static void newTests()                                                                                                // Tests being worked on
   {oldTests();
   }

  public static void main(String[] args)                                                                                // Test if called as a program
   {try                                                                                                                 // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {if (github_actions) oldTests(); else newTests();                                                                  // Tests to run
      if (coverageAnalysis) coverageAnalysis(12);                                                                       // Code coverage
      testSummary();                                                                                                    // Summarize test results
      System.exit(testsFailed);
     }
    catch(Exception e)                                                                                                  // Get a traceback in a format clickable in Geany
     {System.err.println(e);
      System.err.println(fullTraceBack(e));
      System.exit(testsFailed);
     }
   }
}
