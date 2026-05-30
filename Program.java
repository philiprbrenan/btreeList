//----------------------------------------------------------------------------------------------------------------------
// Machine level programming in Java
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;
import java.util.function.Supplier;

//D1 Construct                                                                                                          // Develop and test a java program to describe a chip and emulate its operation.

public class Program extends Test                                                                                       // Develop and test a java program to describe a chip and emulate its operation.
 {final Stack<I>         code = new Stack<>();                                                                          // Machine code instructions
  final Stack<Label>   labels = new Stack<>();                                                                          // Labels for instructions in this process

  final Program parentProgram;                                                                                          // Redirect the code and variables of one program to another to allow components to be tested in isolation before their code is integrated into a larger program.
  final ByteMemory byteMemory;                                                                                          // Optional memory associated with the program
  final String        tracing;                                                                                          // Trace to this file
  final boolean     immediate;                                                                                          // Execute immediately if true else generate machine code and execute later
  public  I         executing = null;                                                                                   // Instruction being currently executed
  public  int        maxSteps = 9999;                                                                                   // Number of steps permitted in code execution
  private int       nextIntId = 0;                                                                                      // Unique id for each Int
  private int      nextBoolId = 0;                                                                                      // Unique id for each Bool
  private static int programs = 0;                                                                                      // Unique id for each program
  final   int       programId = ++programs;                                                                             // Unique id for this program
  private int              pc;                                                                                          // Number the programs
  final StringBuilder     out = new StringBuilder();                                                                    // Text output area

  static class Build                                                                                                    // Builder for this program
   {boolean immediate;                                                                                                  // Immediate mode
    boolean trace;                                                                                                      // Trace execution
    Program parent;                                                                                                     // Parent program
    Integer size;                                                                                                       // Memory allocated by this program
    Build immediate(boolean Immediate) {immediate = Immediate; return this;}
    Build parent   (Program Parent)    {parent    = Parent;    return this;}
    Build memory   (int     Size)      {size      = Size;      return this;}
    Build trace    (boolean Trace)     {trace     = Trace;     return this;}
   }

  Program(Build Build)                                                                                                  // Construct
   {immediate     = Build.immediate;                                                                                    // Immediate or delayed execution
    parentProgram = Build.parent == null ? this : Build.parent;                                                         // Parent program that will contain the code
    byteMemory    = Build.size   != null ? new ByteMemory(Build.size) : null;                                           // Memory associated with program if any
    tracing       = Build.trace  ? ("trace/"+(immediate ? "A" : "B")+".txt") : null;                                    // Tracing if any
    if (tracing()) deleteFile(tracing);                                                                                 // Delete any existing trace file so we can start a new
    code();                                                                                                             // Load or execute the code associated with this program
   }

  void code() {}                                                                                                        // Override to provide some code for this program
  boolean immediate() {return program().immediate;}                                                                     // Executing immediately via interpretation
  boolean executing() {return program().executing != null;}                                                             // Executing machine code
  boolean   tracing() {return tracing != null;}                                                                         // Trace execution
  Program   program() {return parentProgram;}                                                                           // Address this program

  void executingCheck()     {if (!executing()) stop("Not executing or interpreting");}                                  // Use standard Java operators rather than this class to execute code that is not executed as machine code
  void parentProgramCheck() {if (program() != program().program()) stop("Parent program not set to parent program");}   // Use standard Java operators rather than this class to execute code that is not executed as machine code

  void  ai()                                                                                                            // An executing program cannot be extended by adding new data or instructions
   {final I      i = parentProgram.executing;
    final String m = immediate() ? "immediate" : "delayed";
    if (i != null) stop("Allocation within an instruction while executing in", m, "mode:", i.traceBack, "====");
   }

  void trace(String Message)  {if (tracing()) {appendFile(tracing, Message+"\n");}}                                     // Write a trace message with location information

  void trace(String Message, String Location)                                                                           // Write a trace message with location information
   {if (tracing())
     {final String m = Location == null ? Message+"\n" : f("%-32s  %s\n", Message, Location);
      appendFile(tracing, m);
     }
   }

//D1 Program                                                                                                            // Program ececution structures

//D2 For loops                                                                                                          // For loops with fixed and variable number of iterations

  abstract class For                                                                                                    // For loop
   {For(Int Start, Int End)                                                                                             // Execute the loop the specified number of times
     {final Int index = new Int ("Index");
      final Bool cont = new Bool("Continue");

      if (immediate())                                                                                                  // Immediate execution
       {index.set(Start);                                                                                               // Start index
        for(int i : range(Start.i(), End.i()))                                                                          // Iterate over the specified range
         {if (tracing()) trace("For "+i);
          cont.clear();                                                                                                 // Terminate unless told otherwise
          body(index, cont);                                                                                            // Execute the loop
          index.inc();                                                                                                  // Set the index to each element of the specified range
          if (!cont.b()) break;                                                                                         // Terminate the loop unless continuation requested
         }
       }
      else                                                                                                              // Machine code
       {index.set(Start);                                                                                               // Start index
        final Label start = new Label();                                                                                // Start of for loop code
        final Label   end = new Label();                                                                                // End of for loop code
        new I(true)
         {void action()
           {if (index.i() >=  End.i()) program().pc = end.offset;                                                       // Index out of range
           }
         };
        if (tracing()) new I() {void action() {trace("For "+index.i); }};                                               // Trace at run time
        cont.clear();                                                                                                   // Terminate unless told otherwise
        body(index, cont);                                                                                              // Execute the loop
        index.inc();                                                                                                    // Increment loop counter
        new I(true)
         {void action()
           {program().pc = cont.b() ? start.offset : end.offset;                                                        // Continue while requested
           }
         };
        end.set();                                                                                                      // End of the loop
       }
     }

    For(int End) {this(new Int(0), new Int(End));}                                                                      // Execute the loop the specified number of times as long as it returns true
    For(Int End) {this(new Int(0),         End);}                                                                       // Execute the loop the specified number of times as long as it returns true

    abstract void body(Int Index, Bool Continue);                                                                       // Body of the for loop - execute while in range and continuation requested
   }

  abstract class ForCount                                                                                               // For loop for a precomputed number of times
   {ForCount(Int Start, Int End)                                                                                        // Execute the loop the specified number of times
     {final Int index = new Int("Index");

      if (immediate())                                                                                                  // Immediate execution
       {index.set(Start);                                                                                               // Start index
        for(int i : range(Start.i(), End.i()))                                                                          // Iterate over the specified range
         {if (tracing()) trace("ForCount "+i);
          body(index);                                                                                                  // Execute the loop
          index.inc();                                                                                                  // Increment loop counter
         }
       }
      else                                                                                                              // Machine code
       {index.set(Start);                                                                                               // Start index
        final Label start = new Label();                                                                                // Start of for loop code
        final Label   end = new Label();                                                                                // End of for loop code
        new I(true)                                                                                                     // The for loop will not be executed if the execution count is less than 1
         {void action()
           {if (index.i() >=  End.i()) program().pc = end.offset;                                                       // Index out of range
           }
         };
        if (tracing()) new I() {void action() {trace("ForCount "+index.i);}};                                           // Trace at run time
        body(index);                                                                                                    // Execute the loop
        index.inc();                                                                                                    // Increment loop counter
        new I(true)
         {void action()
           {program().pc = start.offset;                                                                                // Restart loop
           }
         };
        end.set();                                                                                                      // End of the loop
       }
     }

    ForCount(int End) {this(new Int(0), new Int(End));}                                                                 // Execute the loop the specified number of times as long as it returns true
    ForCount(Int End) {this(new Int(0),         End);}                                                                  // Execute the loop the specified number of times as long as it returns true

    abstract void body(Int Index);                                                                                      // Body of the for loop - execute while in range and continuation requested
   }

//D2 If                                                                                                                 // If then else

  abstract class If                                                                                                     // If statement
   {If (boolean Condition)                                                                                              // A constant that selects code at compile time
     {if (Condition) Then(); else Else();
     }
    If (Bool    Condition)
     {if (immediate())                                                                                                  // Immediate execution
       {if (Condition.b())
         {if (tracing()) trace("Then");
          Then();
         }
        else
         {if (tracing()) trace("Else");
          Else();
         }
       }
     else                                                                                                               // Machine code
       {final Label lse = new Label();                                                                                  // Start of else
        final Label end = new Label();                                                                                  // End of if
        new I(true)                                                                                                     // Jump to else if condition is false
         {void action()
           {if (!Condition.b()) program().pc = lse.offset;
           }
         };
        if (tracing()) new I() {void action() {trace("Then");}};                                                        // Trace at run time
        Then();                                                                                                         // Then body
        new I(true)                                                                                                     // Jump over else to end
         {void action()
           {program().pc = end.offset;
           }
         };
        lse.set();                                                                                                      // Start of else
        if (tracing()) new I() {void action() {trace("Else");}};                                                        // Trace at run time
        Else();                                                                                                         // Else body
        end.set();                                                                                                      // End of the loop
       }
     }

    abstract void Then();                                                                                               // Then clause
             void Else() {}                                                                                             // Else clause
   }

  <T extends Int> T If(Bool Choice, T Set, Supplier<T> Then, Supplier<T> Else)                                          // Choose between two alternatives
   {new If (Choice)
     {void Then() {Set.set(Then.get());}
      void Else() {Set.set(Else.get());}
     };
    return Set;
   }

//D1 Data                                                                                                               // Operations on boolean and integer data

//D2 Boolean values                                                                                                     // Operations on boolean values

  class Bool                                                                                                            // An integer that can be passed as a parameter to a method and modified there-in
   {boolean    i = false;                                                                                               // Value of the integer
    boolean    v = false;                                                                                               // Whether the current value of the integer is valid or not
    final int id = parentProgram.nextBoolId++;                                                                          // Unique id for Bool
    private final String traceComment = tracing() ? traceComment() : null;                                              // Location
    String  name = null;                                                                                                // The name of the variable

    enum Ops {and, eq, flip, ne, or, set};                                                                              // Boolean operation classification by argument types

    Bool (String Name)             {this();  name = Name;}                                                              // Constructors with name supplied
    Bool (String Name, boolean  I) {this(I); name = Name;}
    Bool (String Name, Bool     I) {this(I); name = Name;}

    Bool           ()          {ai(); invalidate();}                                                                    // Constructors
    Bool           (boolean I) {ai(); ie(Ops.set, I);}
    Bool           (Bool    I) {ai(); ie(Ops.set, I);}
    boolean       b()          {x(); return i;}
    boolean       v()          {     return v;}
    void          x()          {if (!v) variableNotSet("Bool", name);}                                                  // Check a value has been set for the boolean
    Bool          X()          {v = true; return this;}

    Bool        set()          {return ie(Ops.set,  true); }                                                            // Boolean operations which modify the target
    Bool        set(boolean I) {return ie(Ops.set,  I);    }
    Bool        set(Bool    I) {return ie(Ops.set,  I);    }
    Bool        set(Int     I) {return ie(Ops.set,  I);    }
    Bool      clear()          {return ie(Ops.set,  false);}
    Bool       flip()          {return ie(Ops.flip);       }

    Bool        Set()          {return dup().set();}                                                                    // Boolean operations that modify a copy of the target
    Bool        Set(boolean I) {return dup().set(I);}
    Bool        Set(Bool    I) {return dup().set(I);}
    Bool      Clear()          {return dup().clear();}
    Bool       Flip()          {return dup().flip();}

    Bool         eq(boolean I) {return ie(Ops.eq,  I);}
    Bool         ne(boolean I) {return ie(Ops.ne,  I);}

    Bool         eq(Bool    I) {return ie(Ops.eq,  I);}
    Bool         ne(Bool    I) {return ie(Ops.ne,  I);}

    Bool ie(Ops Op)            {new I() {void action() {ex(Op   );}}; return this;}                                     // Execute as an instruction because these are the building blocks of the chip with which we wish to construct the algorithm
    Bool ie(Ops Op, boolean I) {new I() {void action() {ex(Op, I);}}; return this;}
    Bool ie(Ops Op, Bool    I) {new I() {void action() {ex(Op, I);}}; return this;}
    Bool ie(Ops Op, Int     I) {new I() {void action() {ex(Op, I);}}; return this;}

    Bool ex(Ops Op)                                                                                                     // Execute a zeradic boolean operation
     {executingCheck();
      switch(Op)
       {case flip -> {x(); i = !i;                }
        default   -> stop("Op not implemented:", Op);
       }
      if (tracing()) trace("Bool1 "+Op+" "+this, traceComment);
      return this;
     }

    Bool ex(Ops Op, boolean I)                                                                                          // Execute a monadic boolean operation on a constant
     {executingCheck();
      switch (Op)
       {case set -> {i  = I;          }
        case eq  -> {x(); i = i == I; }
        case ne  -> {x(); i = i != I; }
        default  -> stop("Op not implemented:", Op);
       }
      v = true;
      if (tracing()) trace("Bool2 "+Op+" "+this+" "+I, traceComment);
      return this;
     }

    Bool ex(Ops Op, Bool I)                                                                                             // Execute a monadic boolean operation on a variable
     {executingCheck();
      I.x(); return ex(Op, I.i);
     }

    Bool ex(Ops Op, Int I)                                                                                              // Execute a monadic boolean operation on an integer variable
     {executingCheck();
      switch(Op)
       {case set -> {I.x(); i = I.i > 0; v = true;}
        default  -> stop("Op not implemented:", Op);
       }
      if (tracing()) trace("Bool4 "+Op+" "+this+" "+I, traceComment);
      return this;
     }

    @SafeVarargs final Bool or(Supplier<Bool>...b) {new I() {void action() {orEx(b);}}; return this;}                   // "Or" with short circuit

    @SafeVarargs
    final void orEx(Supplier<Bool>...b)                                                                                 // "Or" with short circuit
     {x();                                                                                                              // Start with the current value
      for (int i : range(b.length))                                                                                     // Test each additional value as necessary
       {if (b()) break;                                                                                                 // Finish when we know the result
        ex(Ops.set, b[i].get());                                                                                        // Check additional operands
       }
     }

    Bool or(Bool...b)                                                                                                   // "Or" without short circuit. Modifies the target.
     {new I()
       {void action()
         {x();
          for (int j : range(b.length))
           {b[j].x();
            if (b[j].i) i = true;
           }
         }
       };
      return this;
     }

    Bool Or(Bool...b)                                                                                                   // "Or" without short circuit. Does not modify the target
     {final Bool r = new Bool(this);
      r.set(this).or(b);
      return r;
     }

    @SafeVarargs
    final Bool And(Supplier<Bool>...b)                                                                                  // "And" with short circuit
     {final Bool r = new Bool();
      new I() {void action() {AndEx(r, b);}};
      return r;
     }

    @SafeVarargs
    final void AndEx(Bool r, Supplier<Bool>...b)                                                                        // "And" with short circuit
     {x(); r.set(b());                                                                                                  // Start with the current value
      for (int i : range(b.length))                                                                                     // Test each additional value as necessary
       {if (!r.b()) break;                                                                                              // Finish when we know the result
        r.set(b[i].get());                                                                                              // Check additional operands
       }
     }

    @SafeVarargs
    final Bool and(Supplier<Bool>...b)                                                                                  // "And" with short circuit modify target
     {new I() {void action() {andEx(b);}};
      return this;
     }

    @SafeVarargs
    final void andEx(Supplier<Bool>...b)                                                                                // "And" with short circuit modify target
      {x();
      for (int i : range(b.length))                                                                                     // Test each additional value as necessary
       {if (!b()) break;                                                                                                // Finish when we know the result
        ex(Ops.set, b[i].get());                                                                                        // Check additional operands
       }
     }

    Bool and(Bool...b)                                                                                                  // "And" without short circuit. Modifies the target.
     {new I()
       {void action()
         {x();
          for (int j : range(b.length))
           {b[j].x();
            if (!b[j].i) i = false;
           }
         }
       };
      return this;
     }

    Bool And(Bool...b)                                                                                                  // "And" without short circuit. Does not modify the target
     {final Bool r = new Bool(this);
      r.and(b);
      return r;
     }

    Bool dup   ()       {                                             return new Bool(this);}                           // Duplicate a boolean so that the duplicated version can be modified without modifying the original
    Bool copy  (Bool I) {new I() {void action() {i = I.i; v = I.v;}}; return this;}                                     // Copy the state of a boolean without regard as to whether it is valid or not
    Bool valid     ()   {return new Bool( v);}                                                                          // Whether the boolean is valid
    Bool notValid  ()   {return new Bool(!v);}                                                                          // Whether the boolean is invalid
    Bool invalidate()   {new I() {void action() {v = false;}};        return this;}                                     // Invalidate the boolean

    public String toString()                                                                                            // Print the boolean
     {if (name == null) return v ? ""+i       : "undefined Bool";
      else              return v ? name+"="+i : "undefined Bool: "+name;
     }

    void stop(final Object...O)                                                                                         // Conditionally print a message and stop
     {if (O.length == 0)               new I() {void action() {Test.stop(out());}};                                     // Print the contents of the output are if no parameters supplied and stop
      else new If (this) {void Then() {new I() {void action() {Test.stop(O)    ;}};}};                                  // Print supplied message and stop
     }

    Bool say() {final Bool i = this; new I() {void action() {Test.say(i)          ;}}; return this;}                    // Say the boolean
    Bool out() {final Bool i = this; new I() {void action() {out.append(""+i+" " );}}; return this;}                    // Write the boolean value to the output area
    Bool Out() {final Bool i = this; new I() {void action() {out.append(""+i+"\n");}}; return this;}                    // Write the boolean value to the output area

    Bool ok(Boolean Value)
     {new I()
       {void action()
         {if (Value != null) {x(); Test.ok(i, Value);}
          else               {     Test.ok(v, false);}
         }
       };
      return this;
     }

    Bool ok(Bool Value)
     {final Bool got = this;
       new If (Value.valid())
       {void Then()
         {new I() {void action()  {Test.ok(got.b(), Value.b()); }};
         }
        void Else()
         {new I() {void action() {Test.ok(got.notValid(), true);}};
         }
       };
      return this;
     }
   }

//D2 Integer values                                                                                                     // Operations on integer values

  class Int                                                                                                             // An integer that can be passed as a parameter to a method and modified there-in
   {private int        i = 0;                                                                                           // Value of the integer
    private boolean    v = false;                                                                                       // Whether the current value of the integer is valid or not
            String  name = null;                                                                                        // The name of the variable
    private final int id = parentProgram.nextIntId++;                                                                   // Unique id for Int
    private final String traceComment = tracing() ? traceComment() : null;                                              // Location

    int         i()  {x(); return i;}                                                                                   // Current value
    boolean     v()  {     return v;}                                                                                   // Value has been set
    void        x()  {if (!v) variableNotSet("Int", name);}                                                             // Check a value has been set for the integer

    Int (String Name)        {this();  name = Name;}                                                                    // Constructors with name supplied
    Int (String Name, int I) {this(I); name = Name;}
    Int (String Name, Int I) {this(I); name = Name;}

    Int      ()      {ai(); invalidate();}                                                                              // Constructors
    Int (int I)      {ai(); ie(Ops.set, I);}
    Int (Int I)      {ai(); ie(Ops.set, I);}

    Int  max (int I) {x(); return i < I ? new Int(I) : this;}
    Int  min (int I) {x(); return i > I ? new Int(I) : this;}
    Int  max (Int I) {final Int r = this; new If (lt(I)) {void Then() {r.set(I);}}; return r;}
    Int  min (Int I) {final Int r = this; new If (gt(I)) {void Then() {r.set(I);}}; return r;}
                                                                                                                        // Possible integer operations
    enum Ops {X, abs, add, add2, bclr, bget, bset, dec, div, down, eq, ge, gt, inc, le, lt,
       max, min, mod, mul, neg, ne, set, sqrt, sub, up};

    Int  X   ()      {return ie(Ops.X      );}                                                                          // Integer operations
    Int  set (int I) {return ie(Ops.set , I);}
    Int  set (Int I) {return ie(Ops.set , I);}
    Int  add (int I) {return ie(Ops.add , I);}
    Int  add (Int I) {return ie(Ops.add , I);}
    Int  add2(Int I) {return ie(Ops.add2, I);}
    Int  sub (int I) {return ie(Ops.sub , I);}
    Int  sub (Int I) {return ie(Ops.sub , I);}
    Int  mul (int I) {return ie(Ops.mul , I);}
    Int  mul (Int I) {return ie(Ops.mul , I);}
    Int  div (int I) {return ie(Ops.div , I);}
    Int  div (Int I) {return ie(Ops.div , I);}
    Int  mod (int I) {return ie(Ops.mod , I);}
    Int  mod (Int I) {return ie(Ops.mod , I);}
    Int  inc ()      {return ie(Ops.inc    );}
    Int  dec ()      {return ie(Ops.dec    );}
    Int  up  ()      {return ie(Ops.up     );}
    Int  down()      {return ie(Ops.down   );}
    Int  sqrt()      {return ie(Ops.sqrt   );}
    Int  neg ()      {return ie(Ops.neg    );}
    Int  abs ()      {return ie(Ops.abs    );}

    Int ie(Ops Op)        {new I() {void action() {ex(Op   );}}; return this;}                                          // Execute immediately or create an instruction for machine code to execute later
    Int ie(Ops Op, int I) {new I() {void action() {ex(Op, I);}}; return this;}
    Int ie(Ops Op, Int I) {new I() {void action() {ex(Op, I);}}; return this;}

    Int ex(Ops Op)                                                                                                      // Execute a zeradic integer operation
     {executingCheck();
      x();
      switch(Op)
       {case inc  -> {i++;                   }
        case dec  -> {i--;                   }
        case up   -> {i  <<= 1;              }
        case down -> {i >>>= 1;              }
        case sqrt -> {i = (int)Math.sqrt(i); }
        case neg  -> {i = -i;                }
        case abs  -> {i = i < 0 ? -i : i;    }
        default   -> stop("Op not implemented:", Op);
       }

      if (tracing()) trace("Int1 "+Op, traceComment);
      return this;
     }

    Int ex(Ops Op, int I)                                                                                               // Execute a monadic integer operation on a constant
     {executingCheck();
      switch (Op)
       {case set  -> {      i  = I;}
        case add  -> { x(); i += I;}
        case sub  -> { x(); i -= I;}
        case mul  -> { x(); i *= I;}
        case div  -> { x(); i /= I;}
        case mod  -> { x(); i %= I;}
        case add2 -> { x(); i += I + I;}
        default   -> stop("Op not implemented:", Op);
       }
      v = true;
      if (tracing()) trace("Int2 "+Op+" "+this+" "+I, traceComment);
      return this;
     }

    Int ex(Ops Op, Int I)                                                                                               // Execute a monadic integer operation on a variable
     {executingCheck();
      I.x(); return ex(Op, I.i());
     }

    Int  Add (int I) {return dup().add(I) ;}                                                                            // Duplicate the target so that a copy is modified rather than the original integer
    Int  Add (Int I) {return dup().add(I) ;}
    Int  Add2(Int I) {return dup().add2(I);}
    Int  Sub (int I) {return dup().sub(I) ;}
    Int  Sub (Int I) {return dup().sub(I) ;}
    Int  Mul (int I) {return dup().mul(I) ;}
    Int  Mul (Int I) {return dup().mul(I) ;}
    Int  Div (int I) {return dup().div(I) ;}
    Int  Div (Int I) {return dup().div(I) ;}
    Int  Mod (int I) {return dup().mod(I) ;}
    Int  Mod (Int I) {return dup().mod(I) ;}
    Int  Inc ()      {return dup().add(1) ;}
    Int  Dec ()      {return dup().sub(1) ;}
    Int  Up  ()      {return dup().up()   ;}
    Int  Down()      {return dup().down() ;}
    Int  Sqrt()      {return dup().sqrt() ;}
    Int  Neg()       {return dup().neg()  ;}
    Int  Abs()       {return dup().abs()  ;}

    Bool eq(int I) {return bie(Ops.eq, I);}                                                                             // Comparisons with a constant integer
    Bool ne(int I) {return bie(Ops.ne, I);}
    Bool le(int I) {return bie(Ops.le, I);}
    Bool lt(int I) {return bie(Ops.lt, I);}
    Bool ge(int I) {return bie(Ops.ge, I);}
    Bool gt(int I) {return bie(Ops.gt, I);}

    Bool eq(Int I) {return bie(Ops.eq, I);}                                                                             // Comparisons with a variable integer
    Bool ne(Int I) {return bie(Ops.ne, I);}
    Bool le(Int I) {return bie(Ops.le, I);}
    Bool lt(Int I) {return bie(Ops.lt, I);}
    Bool ge(Int I) {return bie(Ops.ge, I);}
    Bool gt(Int I) {return bie(Ops.gt, I);}

    Bool bie(Ops Op, int I)                                                                                             // Execute immediately or create an instruction for machine code to execute later
     {final Bool b = new Bool();
      new I() {void action() {bex(Op, b, I);}};
      return b;
     }

    Bool bie(Ops Op, Int I)
     {final Bool b = new Bool();
      new I() {void action() {I.x(); bex(Op, b, I);}};
      return b;
     }

    void bex(Ops Op, Bool B, int I)
     {x();
      if (tracing()) trace("Int3 "+Op+" "+this+" "+B+" "+I, traceComment);
      switch(Op)
       {case eq -> B.ex(Bool.Ops.set, i == I);
        case ne -> B.ex(Bool.Ops.set, i != I);
        case le -> B.ex(Bool.Ops.set, i <= I);
        case lt -> B.ex(Bool.Ops.set, i <  I);
        case ge -> B.ex(Bool.Ops.set, i >= I);
        case gt -> B.ex(Bool.Ops.set, i >  I);
        default  -> stop("Op not implemented:", Op);
       }
     }

    void bex(Ops Op, Bool B, Int I) {I.x(); bex(Op, B, I.i);}

    Int  dup       () {return new Int(this);}                                                                           // Duplicate an integer so that the duplicated version can be modified without modifying the original
    Int  copy (Int I) {                           new I() {void action() {i = I.i; v = I.v;}};     return this;}        // Copy the state of an integer without regard as to whether it is valid or not
    Bool valid     () {final Bool b = new Bool(); new I() {void action() {b.i =  v; b.v = true;}}; return b;}           // Whether the integer is valid
    Bool notValid  () {final Bool b = new Bool(); new I() {void action() {b.i = !v; b.v = true;}}; return b;}           // Whether the integer is invalid
    Int  invalidate() {                           new I() {void action() {v = false;}};            return this;}        // Invalidate the integer

    Int  bclr (Int I) {new I() {void action() {bclrEx(I);}}; return this;}                                              // Clear the indicated bit
    Int  bset (Int I) {new I() {void action() {bsetEx(I);}}; return this;}                                              // Set the indicated bit
    Int  bset (Int I, boolean V)                                                                                        // Set the indicated bit in the integer to the specified value
     {new I() {void action() {bsetEx(I, V);}};
      return this;
     }
    Int  bset (Int I, Bool V)                                                                                           // Set the indicated bit in the integer to the specified value
     {new I() {void action() {bsetEx(I, V);}};
      return this;
     }
    Bool bget(Int I)                                                                                                    // Get the indicated bit from the integer
     {final Bool B = new Bool();
      new I() {void action() {bgetEx(B, I);}};
      return B;
     }
    void bclrEx(Int I)            {x(); I.x();        ex(Int .Ops.set, clrBit(i(), I.i()));}                            // Clear the specified bit
    void bsetEx(Int I)            {x(); I.x();        ex(Int .Ops.set, setBit(i(), I.i()));}                            // Set the indicated bit in the integer
    void bsetEx(Int I, boolean V) {x(); I.x();        ex(Int .Ops.set, setBit(i(), I.i(), V));}                         // Set the indicated bit in the integer to the specified value
    void bsetEx(Int I, Bool    V) {x(); I.x(); V.x(); ex(Int .Ops.set, setBit(i(), I.i(), V.b()));}                     // Get the indicated bit in the integer
    void bgetEx(Bool B, Int    I) {x(); I.x();      B.ex(Bool.Ops.set, getBit(i(), I.i()));}

    public String toString()                                                                                            // Print the integer
     {if (name == null) return v ? ""+i       : "undefined Int";
      else              return v ? name+"="+i : "undefined Int: "+name;
     }

    Int say() {final Int i = this; new I() {void action() {Test.say(i);}};           return this;}                      // Say the integer
    Int out() {final Int i = this; new I() {void action() {out.append(""+i+" " );}}; return this;}                      // Write the integer value to the output area
    Int Out() {final Int i = this; new I() {void action() {out.append(""+i+"\n");}}; return this;}                      // Write the integer value to the output area

    Int ok(Integer Value)                                                                                               // Check the integer
     {new I()
       {void action()
         {if (Value != null) {x(); Test.ok(i, Value);}
          else               {     Test.ok(v, false);}
         }
       };
      return this;
     }

    Int ok(Int Value)
     {final Int got = this;
       new If (Value.valid())
       {void Then()
         {new I() {void action() {Test.ok(got.i(), Value.i());}};
         }
        void Else()
         {new I() {void action() {Test.ok(got.notValid(), true);}};
         }
       };
      return this;
     }

   }

//D1 Byte Memory                                                                                                        // Operations on memory backed by bytes

  static int ib()      {return Integer.BYTES;}                                                                          // Number of bytes in an integer
  static int ib(int I) {return I * ib();}                                                                               // Number of bytes in a number of integers
  static Int ib(Int I) {return I.Mul(ib());}                                                                            // Number of bytes in a number of integers

  class ByteMemory                                                                                                      // Bytes being used as the main memory program
   {static int byteMemoryIds = 0;
    final  int byteMemoryId  = ++byteMemoryIds;
    byte[]bytes;                                                                                                        // Bytes of main memory

    private byte getByte(int I)                                                                                         // Get the value of a byte
     {if (tracing()) trace("memory get byte: "+I+" value:"+bytes[I]);                                                   // Trace
      return bytes[I];                                                                                                  // Get the value of a byte
     }

    private void putByte(int I, byte J)                                                                                 // Get the value of a byte
     {if (tracing()) trace("memory put byte: "+I+" was:"+bytes[I]+" set:"+J);                                           // Trace
      bytes[I] = J;                                                                                                     // Set the value of a byte
     }

    ByteMemory(int Length) {bytes = new byte[Length];  clear(new Int(0), Length);}                                      // Create the memory

    ByteMemory copy(ByteMemory SourceMemory, Int SourceOffset, Int TargetOffset, int Width)                             // Copy the specified memory
     {new I()
       {void action()
         {System.arraycopy(SourceMemory.bytes, SourceOffset.i(), bytes, TargetOffset.i(), Width);
         }
       };
      return this;
     }

    ByteMemory clear(Int Start, int Width)
     {final Int w = Start.Add(Width);
      new I() {void action() {Arrays.fill(bytes, Start.i(),  Start.i()+Width, (byte)0);}};
      return this;
     }

    ByteMemory invalidate(int Start, int Width)                                                                         // Invalidate memory by setting it values unlikely to be valid
     {Arrays.fill(bytes, Start,  Start+Width, (byte)-1);
      return this;
     }

    ByteMemory invalidate(Int Start, int Width)
     {new I() {void action() {invalidate(Start.i(),  Width);}};
      return this;
     }

    int size() {return bytes.length;}                                                                                   // Size of memory

    Int getByte(Int I)                                                                                                  // Get the byte at the indicated position
     {final Int r = new Int();
      new I() {void action() {r.set(getByte(I.i()));}};
      return r;
     }

    Int getInt(Int I)                                                                                                   // Get the int at the indicated position
     {final int N = Integer.BYTES;
      final Int r = new Int();
      new I()
       {void action()
         {final int p = I.i();
          final int a = getByte(p+0);
          final int b = getByte(p+1);
          final int c = getByte(p+2);
          final int d = getByte(p+3);
          final int R = d | c | b | a;
          r.ex(Int.Ops.set, R);
         }
       };
      return r;
     }

    int getInt(int I)                                                                                                   // Get the int at the indicated position
     {final int a = getByte(I+0);
      final int b = getByte(I+1);
      final int c = getByte(I+2);
      final int d = getByte(I+3);
      return d | c | b | a;
     }

    Bool getBool(Int I, Int J)                                                                                          // Get the bit in the specified byte at the specified position within the byte
     {Bool r = new Bool();
      new I()
       {void action()
         {r.ex(Bool.Ops.set, getBit(getByte(I.i()), J.i()));
         }
       };
      return r;
     }

    Bool    getBool(Int I) {return getBool(I.Div(Byte.SIZE), I.Mod(Byte.SIZE));}                                        // Get the bit at the bit indexed location
    boolean getBool(int I) {return getBit(getByte(I / Byte.SIZE), I % Byte.SIZE);}                                      // Get the bit at the bit indexed location - debugging

    ByteMemory putByte(Int I, Int J)                                                                                    // Set the byte at the indicated position relative to the start to the specified value
     {new I() {void action() {putByte(I.i(), (byte)J.i());}};
      return this;
     }

    ByteMemory putInt(Int I, Int J)                                                                                     // Set the int at the indicated position relative to the start to the specified value
     {new I()
       {void action()
         {final int p = I.i(), v = J.i();
          putByte(p+0, (byte)((v >>>  0) & 0xFF));
          putByte(p+1, (byte)((v >>>  8) & 0xFF));
          putByte(p+2, (byte)((v >>> 16) & 0xFF));
          putByte(p+3, (byte)((v >>> 24) & 0xFF));
         }
       };
      return this;
     }

    ByteMemory putBool(Int I, Int J, Bool K)                                                                            // Set the bit at the indicated position in the byte at the specified position to the specified value
     {new I()
       {void action()
         {final int p = I.i();
          final int b = getByte(p);
          final int B = setBit(b, J.i(), K.b());
          putByte(p, (byte)B);
         }
       };
      return this;
     }

    ByteMemory putBool(Int I, Bool K) {putBool(I.Div(Byte.SIZE), I.Mod(Byte.SIZE), K); return this;}                    // Set the bit at the bit indexed position

//D2 Memory references                                                                                                  // References to byte memory

    class Ref                                                                                                           // Reference into memory
     {final Int offset = new Int("memoryReferenceOffset");                                                              // Offset of this reference in memory
      final int N = Integer.BYTES;
      final ByteMemory m = ByteMemory.this;
      Ref(int Offset) {offset.set(Offset);}                                                                             // Offset this ref
      Ref(Int Offset) {offset.set(Offset);}                                                                             // Offset this ref

      ByteMemory byteMemory() {return ByteMemory.this;}
      Program    program()    {return Program.this;}

      Ref        copy(Ref Source, int Width){m.copy(Source.m, Source.offset, offset, Width); return this;}              // Copy the specified memory possibly from another byte memory
      Ref       clear(int Width)            {m.clear     (offset, Width);                    return this;}              // Clear memory by setting its bytes to zero
      Ref  invalidate(int Width)            {m.invalidate(offset, Width);                    return this;}              // Invalidate memory by setting its bytes to values unlikely to be valid
      Int     getByte(Int I)                {return m.getByte(I.Add(offset));}                                          // Get the byte at the indicated position
      Int     getInt (Int I)                {return m.getInt (I.Mul(N).add(offset));}                                   // Get the int at the indicated position
      Bool    getBool(Int I, Int J)         {return m.getBool(I.Add(offset), J);}                                       // Get the bit in the specified byte at the specified position within the byte
      Bool    getBool(Int I)                {return m.getBool(I.Add(offset.Mul(Byte.SIZE)));}                           // Get the bit at the bit indexed location
      Ref     putByte(Int I, Int J)         {m.putByte(I.Add(offset), J);                    return this;}              // Set the byte at the indicated position relative to the start to the specified value
      Ref     putInt (Int I, Int J)         {m.putInt (I.Mul(N).add(offset), J);             return this;}              // Set the int at the indicated position relative to the start to the specified value
      Ref     putBool(Int I, Int J, Bool K) {m.putBool(I.Add(offset), J, K);                 return this;}              // Set the bit at the indicated position in the byte at the specified position to the specified value
      Ref     putBool(Int I,        Bool K) {m.putBool(I.Add(offset.Mul(Byte.SIZE)), K);     return this;}              // Set the bit at the bit indexed position
      int      getInt(int I)                {return m.getInt (I*N+offset.i());}                                         // Get an int immediately when debugging
      Int      getInt()                     {                                                return m.getInt (offset);} // Get the referenced int
      Ref      putInt(Int J)                {m.putInt (offset, J);                           return this;}              // Put the referenced int

      boolean getBool(int I) {return getBit((int)byteMemory.bytes[I / Byte.SIZE+offset.i()], I % Byte.SIZE);}           // Get the bit at the bit indexed location - debugging

      Ref step(int Width) {return new Ref(offset.Add(Width));}                                                          // Step up from an existing ref to make a new one - only while not executing
      Ref step(Int Width) {return new Ref(offset.Add(Width));}                                                          // Step up from an existing ref to make a new one - only while not executing

      public String toString()                                                                                          // Print memory reference
       {final StringBuilder s = saySb("Ref: " , offset.i());
        return ""+s;
       }
     }

    public String toString()                                                                                            // Print memory
     {final StringBuilder s = new StringBuilder();
      for (int i = 0, N = size(); i < N; i++) s.append(f("%4d %3d\n", i, bytes[i]));
      return ""+s;
     }

    String dumpHex()                                                                                                    // Dump memory in hexadecimal format
     {final StringBuilder s = new StringBuilder();
      s.append(f("Memory for program %4d\n", programId));
      s.append("         ");
      for (int i = 0; i < 16; i++) s.append(f("%02X ", i));
      s.append("\n");

      for (int i = 0; i < bytes.length; i++)
       {if (i % 16 == 0) s.append(f("%08d ", i));

        final byte b = bytes[i];
        if (b != 0) s.append(f("%02X ", b)); else s.append("  ");
        if ((i + 1) % 16 == 0) s.append("\n");
       }
      if (bytes.length % 16 != 0) s.append("\n");
      return ""+s;
     }
   }

  interface Locatable                                                                                                   // The location of an object in memory
   {Int getLocation();
   }

  String dumpMemory() {return program().byteMemory.dumpHex();}                                                          // Dump memory in hexadecimal format

//D1 Testing                                                                                                            // Methods useful during testing of byte machine programs

  <T> void put(T Value) {new I() {void action() {out.append(""+Value+' ' );}};}                                         // Write anything that has a string representation to the output area and separate from the next item with a space
  <T> void Put(T Value) {new I() {void action() {out.append(""+Value+'\n');}};}                                         // Write anything that has a string representation to the output area and separate from the next item with a new line

  private String output()                                                                                               // Normalize and return the content of the output area, then clear the output area for the next report
   {final String r = ""+out;                                                                                            // Get content
    final String s = r.replaceAll("\\s*\\z", "\n").replaceAll("\\s+\\n", "\n").replaceAll("\\n+", "\n");                // Normalize white space
//  say("AAAA"+s+"BBBB length", s.length());
//  for (int i = 0; i < s.length(); i++)
//   {final char c = s.charAt(i);
//    switch(c)
//     {case ' '  -> {say(i, "space");}
//      case '\n' -> {say(i, "new-line");}
//      default   -> {say(i, c);}
//     }
//   }

    return s;
   }

  void check()                {new I() {void action() {                                  stop(output())  ;}};}          // Say the normalized content of the output area and stop
  void check(String Expected) {new I() {void action() {     Test.ok(output(), Expected); out.setLength(0);}};}          // Test the normalized content of the output area against the specified string, then clear the output area ready for the next report
  void Check(String Expected) {new I() {void action() {if (!Test.ok(output(), Expected)) stop(output())  ;}};}          // Test the normalized content of the output area against the specified string, print the actual output area contents and stop

  void check(StringBuilder Got, String Expected) {new I() {void action() {     Test.ok(""+Got, Expected); out.setLength(0);}};} // Test the supplied content against the specified string, then clear the output area ready for the next report
  void Check(StringBuilder Got, String Expected) {new I() {void action() {if (!Test.ok(""+Got, Expected)) stop(output())  ;}};} // Test the supplied content against the specified string, print the actual output area contents and stop

//D1 Machine Code                                                                                                       // Generate machine code instructions to implement the program

//D2 Instruction                                                                                                        // An instruction represents code to be executed by a process in a single clock cycle == process step

  abstract class I                                                                                                      // Instructions implement the action of a program
   {final int instructionNumber;                                                                                        // The number of this instruction
    final boolean   mightJump;                                                                                          // The instruction might cause a jump
    final String    traceBack = traceBack();                                                                            // Line at which this instruction was created
    final String traceComment = tracing() ? traceComment() : null;                                                      // Line at which this instruction was created as a comment

    I(boolean MightJump)                                                                                                // Add this instruction to the code for the process
     {ai();
      instructionNumber = parentProgram.code.size();                                                                    // Number each instruction - however this only make sens in delayed execution mode
      mightJump = MightJump;
      if (immediate()) {parentProgram.executing = this; action(); parentProgram.executing = null;}                      // Execute instruction immediately via interpretation if in immediate execution mode
      else  {program().code.push(this);}                                                                                // Save instruction in program for later execution if in delayed == non immediate execution mode
     }

    I() {this(false);}                                                                                                  // Add this instruction to the process's code assuming it will not jump

    abstract void action();                                                                                             // The action to be performed by the instruction
   }

  class Label                                                                                                           // Label jump targets in the program
   {int offset;                                                                                                         // The instruction location to which this label applies
    Label()    {set(); program().labels.push(this);}                                                                    // A label assigned to an instruction location
    void set() {offset = program().code.size();}                                                                        // Reassign the label to an instruction
   }

  void execute()                                                                                                        // Execute the current code
   {if (immediate()) return;                                                                                            // The code has already been executed interpretively
    if (tracing()) deleteFile(tracing);
    if (code.size() == 0) stop("No code to execute");
    pc = 0;
    int c, N;
    for(c = 0, N = code.size(); c < maxSteps && pc >= 0 && pc < N; ++c)                                                 // Execute each instruction within a specified number of steps
     {final I i = code.elementAt(pc);
      try
       {pc++;                                                                                                           // This is the anticipated next instruction, but the instruction can set it to effect a branch in execution flow
        executing = i;
        i.action();
        executing = null;
       }
      catch(Exception e)
       {if (executing == null) stop("Exception:", e, "while executing:", traceBack(e));
        else stop("Exception:", e, "\nin instruction:", executing.traceBack, "\nwhile executing:", traceBack(e));
       }
     }
    if (c >= maxSteps) stop("Out of steps after step:", c);
   }

  void Goto(Label Target) {parentProgram.pc = Target.offset;}                                                           // Goto a label unconditionally
  void Goto(Label Target, Bool If) {if ( If.b()) parentProgram.pc = Target.offset;}                                     // Goto a label conditionally
  void Noto(Label Target, Bool If) {if (!If.b()) parentProgram.pc = Target.offset;}                                     // Goto a label not unconditionally

  void variableNotSet(String Type, String Name)                                                                         // Variable not yet set message
   {final I i = parentProgram.executing;
    final String m = (Name != null ? '"'+Name+'"'+" " : "") + "has not been set yet";
    if (i != null) stop(Type, m, i.traceBack, "====");                                                                  // With traceback on failing instruction if possibe
    else           stop(Type, m);                                                                                       // No traceback available
   }

  <A, B> void ok(Supplier<A> a, B b)                                                                                    // Test a result of delayed execution against a known result while the program is still executing
   {new I()
     {void action()
       {if (!ok(a.get(), b)) say("====\n", traceBack);
       }
     };
   }

//D1 Testing                                                                                                            // Test expected output against got output

  static int testsPassed = 0, testsFailed = 0;                                                                          // Number of tests passed and failed

  static void test_programming(boolean Ex)
   {final Program P = new Program(new Build().immediate(Ex).trace(true))
     {void code()
       {final Int i = new Int(0);
        final Int N = new Int(11);
        new For(N)
         {void body(Int Index, Bool Continue)
           {final Int  m = new Int();
            final Bool z = new Bool();
            m.set(Index.Mod(2));
            z.set(m.eq(0));
            new If (z)
             {void Then() {i.add(Index);}
              void Else() {i.sub(Index);}
             };
            Continue.set();
           }
         };
        ok(()->i, 5);
        ok(()->i.v, true);
       }
     };
    P.execute();
   }

  static void test_programming()
   {test_programming(true); test_programming(false);
   }

  static void test_bool(boolean Ex)
   {final Program  P = new Program(new Build().immediate(Ex))
     {void code()
       {final Bool z = new Bool().clear();
        final Bool o = new Bool().set();

        final Bool O = new Bool().set(z);
                   O.or(o);
        O.Out();
        final Bool A = new Bool().set(o);
                   A.and(z);
        A.Out();
       }
     };
    P.execute();
   }

  static void test_bool()
   {test_bool(true);
    test_bool(false);
   }


  static void test_andOr(boolean Ex)
   {final Program  P = new Program(new Build().immediate(Ex))
     {void code()
       {final Bool z = new Bool().clear();
        final Bool o = new Bool().set();
        z.Or (z, z).ok(false);
        z.Or (z, o).ok(true);
        z.Or (o, z).ok(true);
        z.Or (o, o).ok(true);
        o.And(z, z).ok(false);
        o.And(z, o).ok(false);
        o.And(o, z).ok(false);
        o.And(o, o).ok(true);
        execute();
       }
     };
   }

  static void test_andOr()
   {test_andOr(true);
    test_andOr(false);
   }


  static void test_add(boolean Ex)
   {final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Int a = new Int(1);
        final Int b = new Int(0);
        final Int N = new Int(10);
        new For(N)
         {void body(Int Index, Bool Continue)
           {b.add(a.dup().inc());
            put(a);
            Put(b);
            Continue.set();
           }
         };
        check("""
1 2
1 4
1 6
1 8
1 10
1 12
1 14
1 16
1 18
1 20
""");
        execute();
       }
     };
   }

  static void test_add()
   {test_add(true);
    test_add(false);
   }

  static void test_fibonnacci(boolean Ex)
   {final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Int a = new Int(0);
        final Int b = new Int(1);
        final Int c = new Int(0);
        final Int N = new Int(10);
        new For(N)
         {void body(Int Index, Bool Continue)
           {c.set(a);
            c.add(b);
            a.set(b);
            b.set(c);
            c.out();
            Continue.set();
           }
         };
        Check("""
1 2 3 5 8 13 21 34 55 89
""");
        execute();
       }
     };
   }

  static void test_fibonnacci()
   {test_fibonnacci(true);
    test_fibonnacci(false);
   }

  static void test_mod(Boolean Ex)
   {final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Int  a = new Int ();
        final Bool b = new Bool();
        final Int  c = new Int (0);
        final Int  N = new Int (4);
        new For(N)
         {void body(Int Index, Bool Continue)
           {a.set(Index.Inc()).mod(2);
            new If (b.set(a).flip())
             {void Then() {c.dec();}
              void Else() {c.inc(); c.inc();}
             };
            c.out();
            Continue.set();
           }
         };
      check("""
2 1 3 2
""");
        execute();
       }
     };
    //testStop(P.output());
   }

  static void test_mod()
   {test_mod(true);
    test_mod(false);
   }

  static Program test_incremental(boolean Ex)
   {final Program P = new Program(new Build().immediate(Ex).trace(true))
     {void code()
       {final Int a = new Int(0);
                 a.out();
        a.inc(); a.out();
        a.inc(); a.out();
        check("""
0 1 2
""");
        execute();
       }
     };
    return P;
   }

  static void test_incremental()
   {final Program p = test_incremental(true), q = test_incremental(false);
    ok(readFile(p.tracing), readFile(q.tracing));
   }

  static void test_bits(boolean Ex)
   {final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {new For(new Int(2))
         {void body(Int Index, Bool Continue)
           {final Int a = new Int(0);
            a.set(0);
            a.bset(new Int(0))                .ok(1);
            a.bset(new Int(1))                .ok(3);
            a.bset(new Int(2))                .ok(7);
            a.bclr(new Int(0))                .ok(6);
            a.bclr(new Int(1))                .ok(4);
            a.bclr(new Int(2))                .ok(0);
            a.bset(new Int(3), new Bool(true)).ok(8);
            final Bool b = a.bget(new Int(2)) .ok(false);
            final Bool c = a.bget(new Int(3)) .ok(true);
            Continue.set();
           }
         };
       }
     };
    P.execute();
   }

  static void test_bits()
   {test_bits(true);
    test_bits(false);
   }

  static void test_remote(boolean Ex)
   {final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Int a = new Int(1);
        a.add(2).ok(3);
       }
     };
    final Program Q = new Program(new Build().immediate(Ex).parent(P))
     {void code()
       {final Int a = new Int(1);
        a.add(3).ok(4);
       }
     };
    P.execute();
   }

  static void test_remote()
   {test_remote(true);
    test_remote(false);
   }

  static void test_copy(boolean Ex)
   {final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Int  a = new Int();
        final Int  A = new Int();
        A.copy(a);
        a.valid()   .ok(false);
        A.valid()   .ok(false);
        A.notValid().ok(true);
        a.set(1);
        A.copy(a);
        a.valid()   .ok(true);
        A.valid()   .ok(true);
        A.notValid().ok(false);
        a.invalidate();
        A.copy(a);
        a.valid()   .ok(false);
        A.valid()   .ok(false);
        A.notValid().ok(true);
       }
     };
    P.execute();
   }

  static void test_copy()
   {test_copy(true);
    test_copy(false);
   }

  static void test_byteMemory(boolean Ex)
   {final Program P = new Program(new Build().immediate(Ex).memory(16))
     {void code()
       {final ByteMemory m = byteMemory;
        new For(2)
         {void body(Int Index, Bool Continue)
           {m.putInt(new Int(0), new Int(1));
            m.putInt(new Int(4), new Int(2));
            m.getInt(new Int(0)).ok(1);
            m.getInt(new Int(4)).ok(2);

            m.getBool(new Int(4), new Int(0)).ok(false);
            m.getBool(new Int(4), new Int(1)).ok(true );
            m.getBool(new Int(4), new Int(2)).ok(false);
            m.putBool(new Int(4), new Int(0), new Bool(true));
            m.getInt (new Int(4)).            ok(3);

            m.putBool(new Int(32), new Bool(false));
            m.getBool(new Int(32)).ok(false);
            m.getBool(new Int(33)).ok(true );
            m.getBool(new Int(34)).ok(false);
           }
         };
       }
     };
    P.execute();
    ok(P.byteMemory.getBool(32), false);
    ok(P.byteMemory.getBool(33), true);
   }

  static void test_byteMemory()
   {test_byteMemory(true);
    test_byteMemory(false);
   }

  static void test_byteMemoryRef(boolean Ex)
   {final Program P = new Program(new Build().immediate(Ex).memory(16))
     {void code()
       {final ByteMemory     M = byteMemory;
        final ByteMemory.Ref m = M.new Ref(8);
        new For(2)
         {void body(Int Index, Bool Continue)
           {m.putInt(new Int(0), new Int(1));
            m.putInt(new Int(1), new Int(2));
            m.getInt(new Int(0)).ok(1);
            m.getInt(new Int(1)).ok(2);

            m.getBool(new Int(4), new Int(0)).ok(false);
            m.getBool(new Int(4), new Int(1)).ok(true );
            m.getBool(new Int(4), new Int(2)).ok(false);
            m.putBool(new Int(4), new Int(0), new Bool(true));
            m.getInt (new Int(1)).            ok(3);

            m.putBool(new Int(32), new Bool(false));
            m.getBool(new Int(32)).ok(false);
            m.getBool(new Int(33)).ok(true );
            m.getBool(new Int(34)).ok(false);
           }
         };
       }
     };
    P.execute();
    ok(P.byteMemory.getBool(64+32), false);
    ok(P.byteMemory.getBool(64+33), true);
   }

  static void test_byteMemoryRef()
   {test_byteMemoryRef(true);
    test_byteMemoryRef(false);
   }

  static void test_invalidate(boolean Ex)
   {final Program P = new Program(new Build().immediate(Ex).memory(16))
     {void code()
       {final ByteMemory     M = byteMemory;
        final ByteMemory.Ref m = M.new Ref(8);
        m.invalidate(8);
        m.clear     (4);
       }
     };
    P.execute();
    ok(P.byteMemory, """
   0   0
   1   0
   2   0
   3   0
   4   0
   5   0
   6   0
   7   0
   8   0
   9   0
  10   0
  11   0
  12  -1
  13  -1
  14  -1
  15  -1
""");
   }

  static void test_invalidate()
   {test_invalidate(true);
    test_invalidate(false);
   }

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_programming();
    test_bool();
    test_andOr();
    test_add();
    test_fibonnacci();
    test_mod();
    test_incremental();
    test_remote();
    test_bits();
    test_copy();
    test_byteMemory();
    test_byteMemoryRef();
    test_invalidate();
   }

  static void newTests()                                                                                                // Tests being worked on
   {oldTests();
   }

  public static void main(String[] args)                                                                                // Test if called as a program
   {try                                                                                                                 // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {if (github_actions) oldTests(); else newTests();                                                                  // Tests to run
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
