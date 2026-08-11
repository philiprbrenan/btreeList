//----------------------------------------------------------------------------------------------------------------------
// Create Library Exchange Format files for blackboxes
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

public class Lef extends Test
 {final StringBuilder s = new StringBuilder();

  void s()         {s("");}
  void s(String S) {s.append(S+"\n");}

  Lef ()                                                                                                                // LEF file header
   {s("VERSION 5.8 ;");
    s("BUSBITCHARS \"[]\" ;");
    s("DIVIDERCHAR \"/\" ;");
    s();
   }

  void macro (String Name)                                                                                              // Define a macro
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

  void write(String File)                                                                                               // Write lef macros to a file
   {s();
    s("END LIBRARY");

    writeFile(File, s);
   }

  public static void main(String[] args)
   {final Lef l = new Lef();
              l.macro("array_pcConstant");
              l.macro("array_pcMatchSet");
   }
 }
