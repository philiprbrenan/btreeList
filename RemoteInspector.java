//----------------------------------------------------------------------------------------------------------------------
// Convert classes to remote execution
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.lang.reflect.*;
import java.util.*;

public class RemoteInspector extends Test
 {static void inspect(Class<?> root)
   {for (Class<?> c : root.getDeclaredClasses())
     {if (Program.RemoteExecutable.class.isAssignableFrom(c)) inspectClass(c);
      inspect(c);
     }
   }

  static void inspectClass(Class<?> c)                                                                                  // Constructors
   {say("REMOTE CLASS: " + c.getName());


    for (Constructor<?> constructor : c.getDeclaredConstructors())
     {say("Constructor:");
      for (Parameter p : constructor.getParameters())
       {say("  " +p.getType().getSimpleName() + " " + p.getName());
       }
     }

    System.err.println("Final instance fields:");                                                                       // Final instance fields

    for (Field f : c.getDeclaredFields())
     {int m = f.getModifiers();

      if (!Modifier.isFinal(m)) continue;
      if (Modifier.isStatic(m)) continue;

      say("  " + f.getType().getSimpleName() + " " + f.getName());
     }
   }

  public static void main(String[] args)
   {inspect(Program.class);
   }
 }
