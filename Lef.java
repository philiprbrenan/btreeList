//----------------------------------------------------------------------------------------------------------------------
// Create Library Exchange Format files for blackboxes
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

public class Lef extends Test
 {final StringBuilder s = new StringBuilder();

  void s ()         {s("");}
  void s (String S) {s.append(S+"\n");}

  Lef ()                                                                                                                // LEF file header
   {s("VERSION 5.8 ;");
    s("BUSBITCHARS \"[]\" ;");
    s("DIVIDERCHAR \"/\" ;");
    s();
   }


  class ArrayLef
   {ArrayLef (String Name)                                                                                         // Define a macro
     {final int WIDTH  = 100;
      final int HEIGHT = 200;
      final int BITS   = 32;

      s("MACRO "+Name);
      s("  CLASS BLOCK ;");
      s("  ORIGIN 0 0 ;");
      s("  SIZE " + WIDTH + " BY " + HEIGHT + " ;");
      s("  SYMMETRY X Y R90 ;");
      s();

      for (int bit = 0; bit < BITS; bit++)                                                                                // Address input pins on left side
       {int y = 5 + bit * 5;

        s("  PIN address[" + bit + "]");
        s("    DIRECTION INPUT ;");
        s("    USE SIGNAL ;");
        s("    PORT");
        s("      LAYER met1 ;");
        s("        RECT 0 " + y + " 2 " + (y + 2) + " ;");
        s("    END");
        s("  END address[" + bit + "]");
        s();
       }

      for (int bit = 0; bit < BITS; bit++)                                                                                // Data output pins on right side
       {int y = 5 + bit * 5;

        s("  PIN data[" + bit + "]");
        s("    DIRECTION OUTPUT ;");
        s("    USE SIGNAL ;");
        s("    PORT");
        s("      LAYER met1 ;");
        s("        RECT " + (WIDTH - 2) + " " + y + " " + WIDTH + " " + (y + 2) + " ;");
        s("    END");
        s("  END data[" + bit + "]");
        s();
       }

      s("END "+Name);
     }
   } // ArrayLef

  public class MemoryLef                                                                                                // LEF for a memory
   {final String      MACRO;
    final String      LAYER = "met1";
    final int          SIZE = 100;
    final int     PIN_WIDTH =   2;
    final int          PINS = 260;
    final int PINS_PER_SIDE = (PINS + 3) / 4;

    final String      INPUT = "INPUT";
    final String     OUTPUT = "OUTPUT";

    MemoryLef(String Name)
     {MACRO = Name;
      s.append(substitute("""
            MACRO {n}
              CLASS BLOCK ;
              ORIGIN 0 0 ;
              SIZE 100 BY 100 ;
              SYMMETRY X Y R90 ;
            """, "n", Name));
      int n = 0;

      n = bus("writeIntIndex",   INPUT,  n);
      n = bus("writeBoolIndex",  INPUT,  n);
      n = bus("writeInt",        INPUT,  n);
      n = bus("writeBool",       INPUT,  n);
      n = bus("readIntIndex",    INPUT,  n);
      n = bus("readBoolIndex",   INPUT,  n);
      n = bus("readInt",         OUTPUT, n);
      n = pin("clock",           INPUT,  n);
      n = pin("writeIntEnable",  INPUT,  n);
      n = pin("writeBoolEnable", INPUT,  n);
      n = pin("readBool",        OUTPUT, n);

      s.append("END ").append(MACRO).append('\n');
     }

    int bus (String name, String direction, int n)
     {for (int bit = 0; bit < 32; ++bit) n = pin(name + "[" + bit + "]", direction, n);
      return n;
     }

    int pin (String name, String direction, int n)
     {final int    side = n / PINS_PER_SIDE;
      final int       p = n % PINS_PER_SIDE;
      final int spacing = (SIZE - PIN_WIDTH) / PINS_PER_SIDE;

      int x1, y1, x2, y2;

      switch (side)                                                                                                     // Layout for each side
       {case 0 ->                                                                                                       // Bottom: left → right
         {x1 = p * spacing;
          y1 = 0;
          x2 = x1 + PIN_WIDTH;
          y2 = PIN_WIDTH;
         }

        case 1 ->                                                                                                       // Right: bottom → top
         {x1 = SIZE - PIN_WIDTH;
          y1 = p * spacing;
          x2 = SIZE;
          y2 = y1 + PIN_WIDTH;
         }

        case 2 ->                                                                                                       // Top: right → left
         {x1 = SIZE - p * spacing - PIN_WIDTH;
          y1 = SIZE - PIN_WIDTH;
          x2 = SIZE - p * spacing;
          y2 = SIZE;
         }

        default ->                                                                                                      // Left: top → bottom
         {x1 = 0;
          y1 = SIZE - p * spacing - PIN_WIDTH;
          x2 = PIN_WIDTH;
          y2 = SIZE - p * spacing;
         }
       }

      s.append("""

              PIN %s
                DIRECTION %s ;
                USE SIGNAL ;
                PORT
                  LAYER %s ;
                    RECT %d %d %d %d ;
                END
              END %s
            """.formatted(name, direction, LAYER, x1, y1, x2, y2, name));
      return n + 1;
     }
   } // MemoryLef

  void write (String File)                                                                                              // Write lef macros to a file
   {s();
    s("END LIBRARY");

    writeFile(File, s);
   }

  public static void main (String[] args)
   {final Lef l = new Lef();
              l.new ArrayLef ("array_pcConstant");
              l.new ArrayLef ("array_pcMatchSet");
              l.new MemoryLef("memory_0");
    ok(md5Sum(""+l.s), "dac3bc05e5ee7e56a9ef1ba1d911e401");
   }
 }
