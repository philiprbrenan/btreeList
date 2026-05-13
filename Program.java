//----------------------------------------------------------------------------------------------------------------------
// Machine level programming in Java
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;
import java.util.function.Supplier;

//D1 Construct                                                                                                          // Develop and test a java program to describe a chip and emulate its operation.

public class Program extends Test                                                                                       // Develop and test a java program to describe a chip and emulate its operation.
 {final Stack<I>       code = new Stack<>();                                                                            // Machine code instructions
  final Stack<Label> labels = new Stack<>();                                                                            // Labels for instructions in this process
  final Stack<StringBuilder> put = new Stack<>();                                                                       // Output from execution

  Program           program = this;                                                                                     // Redirect the code and variables of one program to another to allow components to be tested in isolation before their code is integrated into a larger program.
  ByteMemory     byteMemory;                                                                                            // Optional memory associated with the program
  public  boolean immediate = true;                                                                                     // Exeute immediately if true else generate machine code and execute later
  public  I       executing = null;                                                                                     // Instruction being currently executed
  public  int      maxSteps = 9999;                                                                                     // Number of steps permitted in code execution
  private int     nextIntId = 0;                                                                                        // Unique id for each Int
  private int    nextBoolId = 0;                                                                                        // Unique id for each Bool
  private int            pc;                                                                                            // Program counter - set to something less than zero to stop with a return code

  Program() {code();}                                                                                                   // Create a program which executes as it is written
  Program(boolean Immediate) {immediate = Immediate; code();}                                                           // Create a local program that executes immediately or later as machine code - the immediate mode only affects the local program
  Program(Program Program)   {program = Program;     code();}                                                           // Access the specified remote program through this program

  void code() {}                                                                                                        // Override to provide some code for this program
  boolean immediate() {return program.immediate;}                                                                       // Executing immediately via interpretation
  boolean executing() {return program.executing != null;}                                                               // Executing machine code
  Program   program() {return program;}                                                                                 // Address this program
  Program immediate(boolean Immediate) {program.immediate = Immediate; return this;}                                    // Request immediate execution via interpretation
  final void program(Program Program)  {program = Program;}                                                             // Set remote program to accept subsequent code. Set final to prevent this-escape error in derived classes
  void executingOrInterpreting() {if (!immediate() && !executing()) stop("Not executing or interpreting");}             // Use standard Java operators rather than this class to execute code that is not executed as machine conde
  void  ai()                                                                                                            // An executing program cannot be extended by adding new data or instructionse
   {final I      i = program.executing;
    final String m = immediate() ? "immediate" : "delayed";
    if (i != null) stop("Allocation within an instruction while executing in", m, "mode:", i.traceBack, "====");
   }

  boolean trace = false;                                                                                                // Trace if true
  final Stack<String> traceLog = new Stack<>();                                                                         // Trace of execution if requested
  Program trace(boolean Trace) {trace = Trace; return this;}                                                            // Trace an operation

  void trace(String Message, String Location)                                                                           // Write a trace message
   {if (trace)
     {if (Location == null) traceLog.push(Message);
      else                  traceLog.push(f("%-32s  %s", Message, Location));
     }
   }

  void trace(String Message) {trace(Message, null);}                                                                    // Trace an operation

  String trace()
   {final StringBuilder s = new StringBuilder();
    final int N = traceLog.size(); if (N == 0) return "";
    for(int i = 0; i < N; ++i) s.append(f("%4d  %s\n", i, traceLog.elementAt(i)));
    return ""+s;
   }

//D1 Program                                                                                                            // Program structures

  abstract class For                                                                                                    // For loop
   {For(Int Start, Int End)                                                                                             // Execute the loop the specified number of times
     {final Int index = new Int();
      final Bool cont = new Bool();

      if (immediate())                                                                                                  // Immediate execution
       {for(int i : range(Start.i(), End.i()))                                                                          // Iterate over the specified range
         {if (trace) trace("For "+i);
          cont.clear();                                                                                                 // Terminate unless told otherwise
          index.set(i);                                                                                                 // Set the index to each element of the specified range
          body(index, cont);                                                                                            // Execute the loop
          if (cont.Flip().b()) break;                                                                                   // Terminate the loop unless continuation requested
         }
       }
      else                                                                                                              // Machine code
       {index.set(Start);                                                                                               // Start index
        final Label start = new Label();                                                                                // Start of for loop code
        final Label   end = new Label();                                                                                // End of for loop code
        if (trace) trace("For "+index.i);
        new I(true)
         {void action()
           {if (index.i() >=  End.i()) pc = end.offset;                                                                 // Index ut of range
           }
         };
        cont.clear();                                                                                                   // Terminate unless told otherwise
        body(index, cont);                                                                                              // Execute the loop
        index.inc();                                                                                                    // Increment lop counter
        new I(true)
         {void action()
           {pc = cont.b() ? start.offset : end.offset;                                                                  // Continue while requested
           }
         };
        end.set();                                                                                                      // End of the loop
       }
     }

    For(int End) {this(new Int(0), new Int(End));}                                                                      // Execute the loop the specified number of times as long as it returns true
    For(Int End) {this(new Int(0),         End);}                                                                       // Execute the loop the specified number of times as long as it returns true

    abstract void body(Int Index, Bool Continue);                                                                       // Body of the for loop - execute while in range and continuation requested
   }

  abstract class If                                                                                                     // If statement
   {If (boolean Condition)                                                                                              // A constant that selects code at compile time
     {if (Condition) Then(); else Else();
     }
    If (Bool    Condition)
     {if (immediate())                                                                                                  // Immediate execution
       {if (Condition.b())
         {if (trace) trace("Then");
          Then();
         }
        else
         {if (trace) trace("Else");
          Else();
         }
       }
     else                                                                                                               // Machine code
       {final Label lse = new Label();                                                                                  // Start of else
        final Label end = new Label();                                                                                  // End of if
        new I(true)                                                                                                     // Jump to else if condition is false
         {void action()
           {if (!Condition.b()) pc = lse.offset;
           }
         };
        if (trace) trace("Then2");
        Then();                                                                                                         // Then body
        new I(true)                                                                                                     // Jump over else to end
         {void action()
           {pc = end.offset;
           }
         };
        lse.set();                                                                                                      // Start of else
        if (trace) trace("Else2");
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

  class Bool                                                                                                            // An integer that can be passed as a parameter to a method and modified there-in
   {boolean    i = false;                                                                                               // Value of the integer
    boolean    v = false;                                                                                               // Whether the current value of the integer is valid or not
    final int id = program.nextBoolId++;                                                                                // Unique id for Bool
    private final String traceComment = trace ? traceComment() : null;                                                  // Location

    enum Ops {and, eq, flip, ne, or, set};                                                                              // Boolean operation classification by argument types

    Bool           ()          {ai(); }                                                                                 // Constructors
    Bool           (boolean I) {ai(); ie(Ops.set, I);}
    Bool           (Bool    I) {ai(); ie(Ops.set, I);}
    boolean       b()          {x(); return i;}
    boolean       v()          {     return v;}
    void          x()          {if (!v) variableNotSet("Bool");}                                                        // Check a value has been set for the boolean
    Bool          X()          {v = true; return this;}

    Bool        set()          {return ie(Ops.set,  true); }                                                            // Boolean operations which modify the target
    Bool        set(boolean I) {return ie(Ops.set,  I);    }
    Bool        set(Bool    I) {return ie(Ops.set,  I);    }
    Bool        set(Int     I) {return ie(Ops.set,  I);    }
    Bool      clear()          {return ie(Ops.set,  false);}
    Bool       flip()          {return ie(Ops.flip);       }

    Bool        Set()          {return dup().set();}                                                                    // Boolen operations that modify a copy of the target
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
     {executingOrInterpreting();
      switch(Op)
       {case flip -> {x(); i = !i;                }
        default   -> stop("Op not implemented:", Op);
       }
      if (trace) trace("Bool1 "+Op+" "+this, traceComment);
      return this;
     }

    Bool ex(Ops Op, boolean I)                                                                                          // Execute a monadic boolean operation on a constant
     {executingOrInterpreting();
      switch (Op)
       {case set -> {i  = I;          }
        case eq  -> {x(); i = i == I; }
        case ne  -> {x(); i = i != I; }
        default  -> stop("Op not implemented:", Op);
       }
      v = true;
      if (trace) trace("Bool2 "+Op+" "+this+" "+I, traceComment);
      return this;
     }

    Bool ex(Ops Op, Bool I)                                                                                             // Execute a monadic boolean operation on a variable
     {executingOrInterpreting();
      I.x(); return ex(Op, I.i);
     }

    Bool ex(Ops Op, Int I)                                                                                               // Execute a monadic boolean operation on an integer variable
     {executingOrInterpreting();
      switch(Op)
       {case set -> {I.x(); i = I.i > 0; v = true;}
        default  -> stop("Op not implemented:", Op);
       }
      if (trace) trace("Bool4 "+Op+" "+this+" "+I, traceComment);
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

    Bool dup   ()       {                                                                        return new Bool(this);}// Duplicate a boolean so that the duplicated version can be modified without modifying the original
    Bool copy  (Bool I) {                           new I() {void action() {i = I.i; v = I.v;}}; return this;}          // Copy the state of a boolean without regard as to whether it is valid or not
    Bool valid     ()   {final Bool b = new Bool(); new I() {void action() {b.set(v);        }}; return b;}             // Whether the boolean is valid
    Bool notValid  ()   {final Bool b = new Bool(); new I() {void action() {b.set(!v);       }}; return b;}             // Whether the boolean is invalid
    Bool invalidate()   {                           new I() {void action() {v = false;}};        return this;}          // Invalidate the boolean

    public String toString() {return v ? ""+i : "undefined Bool";}                                                      // Print the boolean
   }

  class Int                                                                                                             // An integer that can be passed as a parameter to a method and modified there-in
   {private int        i = 0;                                                                                           // Value of the integer
    private boolean    v = false;                                                                                       // Whether the current value of the integer is valid or not
    private final int id = program.nextIntId++;                                                                         // Unique id for Int
    private final String traceComment = trace ? traceComment() : null;                                                  // Location

    int         i()  {x(); return i;}                                                                                   // Current value
    boolean     v()  {     return v;}                                                                                   // Value has been set
    void        x()  {if (!v) variableNotSet("Int");}                                                                   // Check a value has been set for the integer

    Int      ()      {ai(); }                                                                                           // Constructors
    Int (int I)      {ai(); ie(Ops.set, I);}
    Int (Int I)      {ai(); ie(Ops.set, I);}

    Int  max (int I) {x(); return i < I ? new Int(I) : this;}
    Int  min (int I) {x(); return i > I ? new Int(I) : this;}
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
     {executingOrInterpreting();
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
      if (trace) trace("Int1 "+Op, traceComment);
      return this;
     }

    Int ex(Ops Op, int I)                                                                                               // Execute a monadic integer operation on a constant
     {executingOrInterpreting();
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
      if (trace) trace("Int2 "+Op+" "+this+" "+I, traceComment);
      return this;
     }

    Int ex(Ops Op, Int I)                                                                                               // Execute a monadic integer operation on a variable
     {executingOrInterpreting();
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
      if (trace) trace("Int3 "+Op+" "+this+" "+B+" "+I, traceComment);
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

    Int dup   ()      {return new Int(this);}                                                                           // Duplicate an integer so that the duplicated version can be modified without modifying the original
    Int copy  (Int I) {                           new I() {void action() {i = I.i; v = I.v;}};     return this;}        // Copy the state of an integer without regard as to whether it is valid or not
    Bool valid    ()  {final Bool b = new Bool(); new I() {void action() {b.i =  v; b.v = true;}}; return b;}           // Whether the integer is valid
    Bool notValid ()  {final Bool b = new Bool(); new I() {void action() {b.i = !v; b.v = true;}}; return b;}           // Whether the integer is invalid
    Int invalidate()  {                           new I() {void action() {v = false;}};            return this;}        // Invalidate the integer

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

    public String toString() {return v ? ""+i : "undefined Int";}                                                       // Print the integer
   }

//D1 Byte Memory                                                                                                        // Operations on memory backed by bytes

  class ByteMemory                                                                                                      // Bytes being used as the main memory program
   {byte[]bytes;                                                                                                        // Bytes of main memory

    ByteMemory(int Length) {bytes = new byte[Length];}                                                                  // Create the memory

    ByteMemory copy(Int Source, Int Target, Int Width)                                                                  // Copy the specified memory
     {new I() {void action() {System.arraycopy(bytes, Source.i(), bytes, Target.i(), Width.i());}};
      return this;
     }

    ByteMemory invalidate(int Start, int Width)                                                                         // Invalidate memory by setting it values unlikely to be valid
     {new I() {void action() {Arrays.fill(bytes, Start,  Start+Width, (byte)-1);}};
      return this;
     }

    ByteMemory invalidate(Int Start, Int Width) {return invalidate(Start.i(),  Width.i());}

    int size() {return bytes.length;}                                                                                   // Size of memory

    Int getByte(Int I)                                                                                                  // Get the byte at the indicated position
     {final Int r = new Int();
      new I() {void action() {r.set(bytes[I.i()]);}};
      return r;
     }

    Int getInt(Int I)                                                                                                   // Get the int at the indicated position
     {final int N = Integer.BYTES;
      final Int r = new Int();
      new I()
       {void action()
         {final int p = I.i();
          final int a = bytes[p+0];
          final int b = bytes[p+1];
          final int c = bytes[p+2];
          final int d = bytes[p+3];
          final int R = d | c | b | a;
          r.ex(Int.Ops.set, R);
         }
       };
      return r;
     }

    Bool getBool(Int I, Int J)                                                                                          // Get the bit in the specified byte at the specified position within the byte
     {Bool r = new Bool();
      new I()
       {void action()
         {r.ex(Bool.Ops.set, getBit(bytes[I.i()], J.i()));
         }
       };
      return r;
     }

    Bool    getBool(Int I) {return getBool(I.Div(Byte.SIZE), I.Mod(Byte.SIZE));}                                        // Get the bit at the bit indexed location
    boolean getBool(int I) {return getBit(bytes[I / Byte.SIZE], I % Byte.SIZE);}                                        // Get the bit at the bit indexed location - debugging

    ByteMemory putByte(Int I, Int J)                                                                                    // Set the byte at the indicated position relative to the start to the specified value
     {new I() {void action() {bytes[I.i()] = (byte)J.i();}};
      return this;
     }

    ByteMemory putInt(Int I, Int J)                                                                                     // Set the int at the indicated position relative to the start to the specified value
     {new I()
       {void action()
         {final int p = I.i(), v = J.i();
          bytes[p+0] = (byte)((v >>>  0) & 0xFF);
          bytes[p+1] = (byte)((v >>>  8) & 0xFF);
          bytes[p+2] = (byte)((v >>> 16) & 0xFF);
          bytes[p+3] = (byte)((v >>> 24) & 0xFF);
         }
       };
      return this;
     }

    ByteMemory putBool(Int I, Int J, Bool K)                                                                            // Set the bit at the indicated position in the byte at the specified position to the specified value
     {new I()
       {void action()
         {final int p = I.i();
          final int b = bytes[p];
          final int B = setBit(b, J.i(), K.b());
          bytes[p] = (byte)B;
         }
       };
      return this;
     }

    ByteMemory putBool(Int I, Bool K) {putBool(I.Div(Byte.SIZE), I.Mod(Byte.SIZE), K); return this;}                    // Set the bit at the bit indexed position

    class Ref                                                                                                           // Reference into memory
     {final Int offset;                                                                                                 // Offset of this reference in memory
      final ByteMemory m = ByteMemory.this;
      Ref(int Offset) {offset = new Int(Offset);}                                                                       // Offset this ref

      ByteMemory byteMemory() {return ByteMemory.this;}
      Program    program()    {return Program.this;}

      Ref  copy(Ref Source, Int Width)      {m.copy(Source.offset, offset, Width);       return this;}                  // Copy the specified memory
      Ref  invalidate(Int Width)            {m.invalidate(offset,     Width);            return this;}                  // Invalidate memory by setting it values unlikely to be valid
      Ref  invalidate(int Width)            {m.invalidate(offset.i(), Width);            return this;}                  // Invalidate memory by setting it values unlikely to be valid
      Int     getByte(Int I)                {return m.getByte(I.Add(offset));}                                          // Get the byte at the indicated position
      Int      getInt(Int I)                {return m.getInt (I.Add(offset));}                                          // Get the int at the indicated position
      Bool    getBool(Int I, Int J)         {return m.getBool(I.Add(offset), J);}                                       // Get the bit in the specified byte at the specified position within the byte
      Bool    getBool(Int I)                {return m.getBool(I.Add(offset.Mul(Byte.SIZE)));}                           // Get the bit at the bit indexed location
      Ref     putByte(Int I, Int J)         {m.putByte(I.Add(offset), J);                return this;}                  // Set the byte at the indicated position relative to the start to the specified value
      Ref     putInt (Int I, Int J)         {m.putByte(I.Add(offset), J);                return this;}                  // Set the int at the indicated position relative to the start to the specified value
      Ref     putBool(Int I, Int J, Bool K) {m.putBool(I.Add(offset), J, K);             return this;}                  // Set the bit at the indicated position in the byte at the specified position to the specified value
      Ref     putBool(Int I,        Bool K) {m.putBool(I.Add(offset.Mul(Byte.SIZE)), K); return this;}                  // Set the bit at the bit indexed position
      boolean getBool(int I) {return getBit((int)program.byteMemory.bytes[I / Byte.SIZE+offset.i()], I % Byte.SIZE);}   // Get the bit at the bit indexed location - debugging
     }

    public String toString()                                                                                            // Print memory
     {final StringBuilder s = new StringBuilder();
      for (int i = 0, N = size(); i < N; i++) s.append(f("%4d %3d\n", i, bytes[i]));
      return ""+s;
     }
   }

//D1 Testing                                                                                                            // Methods useful during testing of byte machine programs

  static int[]range(Int Limit) {return range(Limit.i());}                                                               // Range of integers
  static boolean ok(Bool b) {return ok(b.b());}                                                                         // Check test results match expected results.

  void put()                                                                                                            // Say a variable value on a separate line
   {new I() {void action() {push(new StringBuilder("\n"));}};
   }

  <T> void put(T Value)                                                                                                 // Say a variable value on a separate line
   {new I() {void action() {push(saySb(Value).append("\n"));}};
   }

  <T> void Put(T Value)                                                                                                 // Say a variable value on the same line
   {new I() {void action() {push(saySb(Value));}};
   }

  void push(StringBuilder Value)                                                                                        // Say on the same line
   {final Stack<StringBuilder> p = program.put;
    if (p.size() == 0) {p.push(Value); return;}
    final StringBuilder q = p.lastElement();

    final int           l = q.length();
    if (l > 0 && q.charAt(l-1) == '\n') p.push(Value);
    else if (l == 0)     q.append(Value);
    else {q.append(" "); q.append(Value);}
   }

  String output()                                                                                                       // Output from execution
   {final String r = program.put.size() > 0 ? joinStringBuilders(program.put, "") : "";
    return r.replaceAll("\\s+\\n", "\n").replaceAll("\\n+", "\n");
   }

//D1 Machine Code                                                                                                       // Generate machine code instructions to implement the program

//D2 Instruction                                                                                                        // An instruction represents code to be executed by a process in a single clock cycle == process step

  abstract class I                                                                                                      // Instructions implement the action of a program
   {final int instructionNumber;                                                                                        // The number of this instruction
    final boolean   mightJump;                                                                                          // The instruction might cause a jump
    final String    traceBack = traceBack();                                                                            // Line at which this instruction was created
    final String traceComment = traceComment();                                                                         // Line at which this instruction was created as a comment

    I(boolean MightJump)                                                                                                // Add this instruction to the code for the process
     {ai(); //if  (executing()) stop("Cannot add instructions during progam execution");
      instructionNumber = program.code.size();                                                                          // Number each instruction - hwever this only mke sens in delayed execution mode
      mightJump = MightJump;

      if (immediate()) {executing = this; action(); executing = null;}                                                  // Execute instruction immediately via interpretation if in immediate execution mode
      else  {program.code.push(this);}                                                                                                            // Save intruction in program for later execution if in delayed == non immediate execution mode
     }

    I() {this(false);}                                                                                                  // Add this instruction to the process's code assuming it will not jump

    abstract void action();                                                                                             // The action to be performed by the instruction
   }

  class Label                                                                                                           // Label jump targets in the program
   {int offset;                                                                                                         // The instruction location to which this labels applies
    Label()    {set(); program.labels.push(this);}                                                                      // A label assigned to an instruction location
    void set() {offset = code.size();}                                                                                  // Reassign the label to an instruction
   }

  void execute()                                                                                                        // Execute the current code
   {if (immediate) return;                                                                                              // The code has already been executed interpretively
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

  void Goto(Label Target) {program.pc = Target.offset;}                                                                 // Goto a label unconditionally
  void Goto(Label Target, Bool If) {if ( If.b()) program.pc = Target.offset;}                                           // Goto a label conditionally
  void Noto(Label Target, Bool If) {if (!If.b()) program.pc = Target.offset;}                                           // Goto a label not unconditionally

  void variableNotSet(String Type)                                                                                      // Variable not yet set message
   {final I i = program.executing;
    final String m = "has not been set yet";
    if (i != null) stop(Type, m, i.traceBack, "====");                                                                  // With trace back on failing instruction if possibep
    else           stop(Type, m);                                                                                       // No traceback available
   }

  <A, B> void ok(Supplier<A> a, B b) {new I() {void action() {ok(a.get(), b);}};}                                       // Test a result of delayed execution against a known result while the program is still executing

//D1 Testing                                                                                                            // Test expected output against got output

  static int testsPassed = 0, testsFailed = 0;                                                                          // Number of tests passed and failed

  static void test_programming(boolean Ex)
   {final Program P = new Program()
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
   {test_programming(true);
    test_programming(false);
   }

  static void test_bool(boolean Ex)
   {final Program  P = new Program(Ex)
     {void code()
       {final Bool z = new Bool().clear();
        final Bool o = new Bool().set();

        final Bool O = new Bool().set(z);
                   O.or(o);
        put(O);
        final Bool A = new Bool().set(o);
                   A.and(z);
        put(A);
       }
     };
    P.execute();
   }

  static void test_bool()
   {test_bool(true);
    test_bool(false);
   }

  static void test_add(boolean Ex)
   {final Program P = new Program(Ex)
     {void code()
       {final Int a = new Int(1);
        final Int b = new Int(0);
        final Int N = new Int(10);
        new For(N)
         {void body(Int Index, Bool Continue)
           {b.add(a.dup().inc());
            Put(a);
            put(b);
            Continue.set();
           }
         };
       }
     };
    P.execute();
    //stop(P.output());
    ok(P.output(), """
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
   }

  static void test_add()
   {test_add(true);
    test_add(false);
   }

  static void test_fibonnacci(boolean Ex)
   {final Program P = new Program(Ex)
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
            put(c);
            Continue.set();
           }
         };
       }
     };
    P.execute();
    //stop(P.output());
    ok(P.output(), """
1
2
3
5
8
13
21
34
55
89
""");
   }

  static void test_fibonnacci()
   {test_fibonnacci(true);
    test_fibonnacci(false);
   }

  static void test_mod(Boolean Ex)
   {final Program P = new Program(Ex)
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
            put(c);
            Continue.set();
           }
         };
       }
     };
    P.execute();
    //stop(P.output());
    ok(P.output(), """
2
1
3
2
""");
   }

  static void test_mod()
   {test_mod(true);
    test_mod(false);
   }

  static Program test_incremental(boolean Ex)
   {final Program P = new Program(Ex)
     {void code()
       {trace = true;
        final Int a = new Int(0);
                 put(a);
        a.inc(); put(a);
        a.inc(); put(a);
       }
     };
    P.execute();
    //stop(P.output());
    ok(P.output(), """
0
1
2
""");
    return P;
   }

  static void test_incremental()
   {final Program p = test_incremental(true), q = test_incremental(false);
    ok(p.trace(), q.trace());
   }

  static void test_bits(boolean Ex)
   {final Program P = new Program(Ex)
     {void code()
       {new For(new Int(2))
         {void body(Int Index, Bool Continue)
           {final Int a = new Int(0);
            a.set(0);
            a.bset(new Int(0));                 ok(()->a, 1);
            a.bset(new Int(1));                 ok(()->a, 3);
            a.bset(new Int(2));                 ok(()->a, 7);
            a.bclr(new Int(0));                 ok(()->a, 6);
            a.bclr(new Int(1));                 ok(()->a, 4);
            a.bclr(new Int(2));                 ok(()->a, 0);
            a.bset(new Int(3), new Bool(true)); ok(()->a, 8);
            final Bool b = a.bget(new Int(2));  ok(()->b, false);
            final Bool c = a.bget(new Int(3));  ok(()->c, true);
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
   {final Program P = new Program(Ex)
     {void code()
       {final Int a = new Int(1);
        a.add(2);
        ok(()->a, 3);
       }
     };
    final Program Q = new Program(P)
     {void code()
       {final Int a = new Int(1);
        a.add(3);
        ok(()->a, 4);
       }
     };
    P.execute();
   }

  static void test_remote()
   {test_remote(true);
    test_remote(false);
   }

  static void test_copy(boolean Ex)
   {final Program P = new Program(Ex)
     {void code()
       {final Int  a = new Int();
        final Int  A = new Int();
        A.copy(a);
        final Bool a1 = a.valid();    ok(()->a1, false);
        final Bool A1 = A.valid();    ok(()->A1, false);
        final Bool B1 = A.notValid(); ok(()->B1, true);
        a.set(1);
        A.copy(a);
        final Bool a2 = a.valid();    ok(()->a2, true);
        final Bool A2 = A.valid();    ok(()->A2, true);
        final Bool B2 = A.notValid(); ok(()->B2, false);
        a.invalidate();
        A.copy(a);
        final Bool a3 = a.valid();    ok(()->a3, false);
        final Bool A3 = A.valid();    ok(()->A3, false);
        final Bool B3 = A.notValid(); ok(()->B3, true);
       }
     };
    P.execute();
   }

  static void test_copy()
   {test_copy(true);
    test_copy(false);
   }

  static void test_byteMemory(boolean Ex)
   {final Program P = new Program(Ex)
     {void code()
       {final ByteMemory m = byteMemory = new ByteMemory(16);
        new For(2)
         {void body(Int Index, Bool Continue)
           {m.putInt(new Int(0), new Int(1));
            m.putInt(new Int(4), new Int(2));
            final Int a = m.getInt(new Int(0)); ok(()->a.i(), 1);
            final Int b = m.getInt(new Int(4)); ok(()->b.i(), 2);

            final Bool c = m.getBool(new Int(4), new Int(0)); ok(()->c.b(), false);
            final Bool d = m.getBool(new Int(4), new Int(1)); ok(()->d.b(), true );
            final Bool e = m.getBool(new Int(4), new Int(2)); ok(()->e.b(), false);
                           m.putBool(new Int(4), new Int(0), new Bool(true));
            final Int  f = m.getInt (new Int(4));             ok(()->f.i(), 3);

                           m.putBool(new Int(32), new Bool(false));
            final Bool C = m.getBool(new Int(32)); ok(()->C.b(), false);
            final Bool D = m.getBool(new Int(33)); ok(()->D.b(), true );
            final Bool E = m.getBool(new Int(34)); ok(()->E.b(), false);
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
   {final Program P = new Program(Ex)
     {void code()
       {final ByteMemory     M = byteMemory = new ByteMemory(16);
        final ByteMemory.Ref m = M.new Ref(8);
        new For(2)
         {void body(Int Index, Bool Continue)
           {m.putInt(new Int(0), new Int(1));
            m.putInt(new Int(4), new Int(2));
            final Int a = m.getInt(new Int(0)); ok(()->a.i(), 1);
            final Int b = m.getInt(new Int(4)); ok(()->b.i(), 2);

            final Bool c = m.getBool(new Int(4), new Int(0)); ok(()->c.b(), false);
            final Bool d = m.getBool(new Int(4), new Int(1)); ok(()->d.b(), true );
            final Bool e = m.getBool(new Int(4), new Int(2)); ok(()->e.b(), false);
                           m.putBool(new Int(4), new Int(0), new Bool(true));
            final Int  f = m.getInt (new Int(4));             ok(()->f.i(), 3);

                           m.putBool(new Int(32), new Bool(false));
            final Bool C = m.getBool(new Int(32)); ok(()->C.b(), false);
            final Bool D = m.getBool(new Int(33)); ok(()->D.b(), true );
            final Bool E = m.getBool(new Int(34)); ok(()->E.b(), false);
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

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_programming();
    test_bool();
    test_add();
    test_fibonnacci();
    test_mod();
    test_incremental();
    test_remote();
    test_bits();
    test_copy();
    test_byteMemory();
    test_byteMemoryRef();
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
