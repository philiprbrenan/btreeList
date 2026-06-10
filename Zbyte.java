package com.AppaApps.Silicon;

import java.util.*;

class Zbyte extends Test
 {public static void main(String[] args)                                        // Test if called as a program
   {int  a  = 256;
    byte b1 = (byte)((a>>> 0) & 0xFF);
    byte b2 = (byte)((a>>> 8) & 0xFF);
    byte b3 = (byte)((a>>>16) & 0xFF);
    byte b4 = (byte)((a>>>24) & 0xFF);
say("AAAA", b1, b2, b3, b4);
    int  i1 = Byte.toUnsignedInt(b1);
    int  i2 = Byte.toUnsignedInt(b2)<< 8;
    int  i3 = Byte.toUnsignedInt(b3)<<16;
    int  i4 = Byte.toUnsignedInt(b4)<<24;
    int  i  = i4 | i3 | i2 | i1;
    System.err.println(i);
   }
 }
