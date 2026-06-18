//----------------------------------------------------------------------------------------------------------------------
//----------------------------------------------------------------------------------------------------------------------
// Machine level programming in Java
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;
import java.util.function.*;
import java.util.function.*;

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
  final static Stack<String>                        subs = new Stack<>();                                               // Name of the current method is cached here so that we can count instructions
  final static TreeMap<String,Integer> instructionCounts = new TreeMap<>();                                             // Count instructions by subroutine in which they are added
//final static TreeMap<String,Procedure> procedures      = new TreeMap<>();                                             // Procedures by name for this program

  final static class Build                                                                                              // Builder for this program
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

  void executingCheck()     {if (!executing()) stop("Not executing");}                                                  // Confirm that code is being executed and that consequently an instruction should be executed otherwise complain
  void parentProgramCheck() {if (program() != program().program()) stop("Parent program not set to parent program");}   // Check that code is being written to the expected program

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

//D1 Program                                                                                                            // Program execution structures

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
        new I(I.Jump.might)                                                                                             // Will jump
         {void   a() {if (index.i() >=  End.i()) program().pc = end.offset;}                                            // Index out of range
          String v() {return "if ("+index.vn()+" >= "+End.vn()+") pc <= "+ end.offset + ";"+traceComment();}            // Index out of range
         };
        if (tracing()) new I() {void a() {trace("For "+index.i);} String v() {return "";}};                             // Trace at run time
        cont.clear();                                                                                                   // Terminate unless told otherwise
        body(index, cont);                                                                                              // Execute the loop
        index.inc();                                                                                                    // Increment loop counter
        new I(I.Jump.will)
         {void   a() {program().pc = cont.b() ? start.offset : end.offset;}                                             // Continue execution of the loop as long as requested
          String v() {return "if ("+ cont.vn()+") pc <= "+start.offset+"; else pc <= "+end.offset+";"+traceComment();}  // Continue execution of the loop as long as requested
         };
        end.set();                                                                                                      // End of the loop
       }
     }

    For(int End) {this(new Int("Start", 0), new Int("End", End));}                                                      // Execute the loop the specified number of times as long as it returns true
    For(Int End) {this(new Int("Start", 0),                End);}                                                       // Execute the loop the specified number of times as long as it returns true

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
        new I(I.Jump.might)                                                                                             // The for loop will not be executed if the execution count is less than 1
         {void a()
           {if (index.i() >=  End.i()) program().pc = end.offset;                                                       // Index out of range
           }
         };
        if (tracing()) new I() {void a() {trace("ForCount "+index.i);}};                                                // Trace at run time
        body(index);                                                                                                    // Execute the loop
        index.inc();                                                                                                    // Increment loop counter
        new I(I.Jump.will)                                                                                              // Will jump
         {void a()
           {program().pc = start.offset;                                                                                // Restart loop
           }
         };
        end.set();                                                                                                      // End of the loop
       }
     }

    ForCount(int  End) {this(new Int("Start", 0), new Int("End", End)   );}                                             // Execute the loop the specified number of times
    ForCount(Int  End) {this(new Int("Start", 0),                End    );}                                             // Execute the loop the specified number of times
    ForCount(Bint End) {this(new Int("Start", 0),                End.i());}                                             // Execute the loop the specified number of times

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
        new I(I.Jump.might)                                                                                             // Jump to else if condition is false
         {void a()
           {if (!Condition.b()) program().pc = lse.offset;
           }
         };
        if (tracing()) new I() {void a() {trace("Then");}};                                                             // Trace at run time
        Then();                                                                                                         // Then body
        new I(I.Jump.will)                                                                                              // Jump over else to end
         {void a()
           {program().pc = end.offset;
           }
         };
        lse.set();                                                                                                      // Start of else
        if (tracing()) new I() {void a() {trace("Else");}};                                                             // Trace at run time
        Else();                                                                                                         // Else body
        end.set();                                                                                                      // End of the loop
       }
     }

    If (Bint Condition) {this(Condition.b);}                                                                            // If from boolean integer

    abstract void Then();                                                                                               // Then clause
             void Else() {}                                                                                             // Else clause
   }

  void If(Bool Choice, Runnable Then, Runnable Else)                                                                    // If then/else with lambdas
   {new If (Choice)
     {void Then() {Then.run();}
      void Else() {Else.run();}
     };
   }

  <T extends Int> T If(Bool Choice, T Set, Supplier<T> Then, Supplier<T> Else)                                          //N Choose between two alternatives
   {new If (Choice)
     {void Then() {Set.set(Then.get());}
      void Else() {Set.set(Else.get());}
     };
    return Set;
   }

//D2 Procedure                                                                                                          // Procedure with parameters and return value.  Only works for static classes because unable to emulate "this".
/*
  abstract class Procedure                                                                                              // Procedure
   {final String      name;                                                                                             // The name of the procedure so it be cataloged and reused later by name which allows the just in time generation of procedures
    final Label       start = new Label(), end = new Label();                                                           // The location of the start and end of the procedure
    final Int returnAddress = new Int();                                                                                // the address to be returned to after the subroutine has been called
    final TreeSet<String>       parameters = new TreeSet<>();                                                           // Parameter names  must be unique over input or output, integer or boolean
    final TreeMap<String, Int>   inputInt  = new TreeMap<>();                                                           // Input integer parameters
    final TreeMap<String, Bool>  inputBool = new TreeMap<>();                                                           // Input boolean parameters
    final TreeMap<String, Int>  outputInt  = new TreeMap<>();                                                           // Output integer parameters
    final TreeMap<String, Bool> outputBool = new TreeMap<>();                                                           // Output boolean parameters

    Procedure input(Data Data)                                                                                          // Add an input parameter
     {final TreeSet<String>       P = parameters;
      final TreeMap<String, Int>  I = inputInt;
      final TreeMap<String, Bool> B = inputBool;
      switch (Data)
       {case Bool b ->
         {if (b.name == null)     stop("No name supplied for input boolean parameter");
          if (P.contains(b.name)) stop("Input parameter has already been defined:", b.name);
          P.add(b.name); B.put(b.name, b);
         }
        case Int i ->
         {if (i.name == null)     stop("No name supplied for integer parameter");
          if (P.contains(i.name)) stop("Input parameter has already been defined:", i.name);
          P.add(i.name); I.put(i.name, i);
         }
       }
      return this;
     }

    Procedure output(Data Data)                                                                                         // Add an output parameter
     {final TreeSet<String>       P = parameters;
      final TreeMap<String, Int>  I = outputInt;
      final TreeMap<String, Bool> B = outputBool;
      switch (Data)
       {case Bool b ->
         {if (b.name == null)     stop("No name supplied for output boolean parameter");
          if (P.contains(b.name)) stop("Output parameter has already been defined:", b.name);
          P.add(b.name); B.put(b.name, b);
         }
        case Int i ->
         {if (i.name == null)     stop("No name supplied for integer parameter");
          if (P.contains(i.name)) stop("Output parameter has already been defined:", i.name);
          P.add(i.name); I.put(i.name, i);
         }
       }
      return this;
     }

    void generate()                                                                                                     // Generate the procedure
     {Goto(end);                                                                                                        // Jump over the code of the subroutine when it is being defined
      start.set();                                                                                                      // Start of subroutine code
      body();                                                                                                           // Code of subroutine
      Goto(returnAddress);                                                                                              // Address at at which to resume execution after the subroutine call
      end.set();                                                                                                        // End of the procedure
     }

    Procedure()                                                                                                         // Define an unnamed procedure
     {name = null;                                                                                                      // No name
      if (!immediate()) generate();                                                                                     // Generate the procedure
     }

    Procedure(String Name)                                                                                              // Define the procedure in terms of its parameters
     {name = Name;                                                                                                      // Name of the procedure
      if (immediate()) {}                                                                                               // In immediate mode the body is called as needed
      else if (!procedures.containsKey(Name))                                                                           // In non immediate mode the procedure is generated if it does not already exist
       {generate();
        procedures.put(Name, this);                                                                                     // Make procedure reusable
       }
     }

    Procedure call()                                                                                                    // Call the procedure.  Set the input parameters as needed.
     {if (immediate()) {body();}                                                                                        // In immediate mode the body is executed as needed
      else                                                                                                              // In non immediate mode the body is saved and then recalled
       {new I() {void a() {returnAddress.ex(Int.Ops.set, pc+1);}};                                                      // Address of next instruction at which execution will resume after the call
        Goto(start);
       }
      return this;
     }

    abstract void body();                                                                                               // Body of procedure

    public String toString()                                                                                            // Print the procedure parameters
     {final StringBuilder s = new StringBuilder();
      s.append("Input  integers:");
      for(String n: inputInt .keySet()) s.append(" "+n);
      s.append("\nOutput integers:");
      for(String n: outputInt.keySet()) s.append(" "+n);
      return "\n"+s;
     }

    void cii(String Name) {new I() {void a() {if (! inputInt.containsKey(Name)) stop("No "+  "input integer parameter called:", Name, ""+Procedure.this);}};}              // Input  integer
    void cio(String Name) {new I() {void a() {if (!outputInt.containsKey(Name)) stop("No "+ "output integer parameter called:", Name, ""+Procedure.this);}};}              // Output integer

    Int       getInt(String Name)        {cii(Name); final Int a = new Int(); new I() {void a() {                  a.ex(Int.Ops.set, inputInt.get(Name));}}; return a;   } // Get an input  integer parameter inside the procedure
    Procedure putInt(String Name, Int I) {cio(Name);                          new I() {void a() {outputInt.get(Name).ex(Int.Ops.set, I);}};                  return this;} // Put an output integer parameter inside the procedure

    Procedure in(String Name, int I) {cii(Name); inputInt.get(Name).set(I); return this;}                               // Set an input  integer parameter by name to an integer constant before calling the procedure
    Procedure in(String Name, Int I) {cii(Name); inputInt.get(Name).set(I); return this;}                               // Set an input  integer parameter by name to an integer variable before calling the procedure
    Int       oi(String Name)        {cio(Name); return outputInt.get(Name);}                                           // Get an output integer parameter after calling a procedure

    void cbi(String Name) {new I() {void a() {if (! inputBool.containsKey(Name)) stop("No "+  "input boolean parameter called:", Name, ""+Procedure.this);}};}              // Input  boolean
    void cbo(String Name) {new I() {void a() {if (!outputBool.containsKey(Name)) stop("No "+ "output boolean parameter called:", Name, ""+Procedure.this);}};}              // Output boolean

    Bool      getBool(String Name)        {cbi(Name); final Bool a = new Bool(); new I() {void a() {                   a.ex(Bool.Ops.set, inputBool.get(Name));}}; return a;   } // Get an input  boolean parameter inside the procedure
    Procedure putBool(String Name, Int B) {cbo(Name);                            new I() {void a() {outputBool.get(Name).ex(Bool.Ops.set, B);}};                   return this;} // Put an output boolean parameter inside the procedure

    Procedure in(String Name, boolean B) {cbi(Name); inputBool.get(Name).set(B); return this;}                          // Set an input  boolean parameter by name to an boolean constant before calling the procedure
    Procedure in(String Name, Bool    B) {cbi(Name); inputBool.get(Name).set(B); return this;}                          // Set an input  boolean parameter by name to an boolean variable before calling the procedure
    Bool      ob(String Name)            {cbo(Name); return outputBool.get(Name);}                                      // Get an output boolean parameter after calling a procedure
   }
*/
//D1 Data                                                                                                               // Operations on boolean and integer data

//public sealed interface Data permits Bool, Int {}                                                                     // Known types of data

//D2 Boolean values                                                                                                     // Operations on boolean values

  final class Bool                                                                                                      // An integer that can be passed as a parameter to a method and modified there-in
   {boolean    i = false;                                                                                               // Value of the integer
    boolean    v = false;                                                                                               // Whether the current value of the integer is valid or not
    final int id = parentProgram.nextBoolId++;                                                                          // Unique id for Bool
    private final String traceComment = tracing() ? traceComment() : null;                                              // Location
    String  name = null;                                                                                                // The name of the variable

    enum Ops {and, eq, flip, ne, or, set};                                                                              // Boolean operation classification by argument types

    Bool (String Name)             {this();  name = Name;}                                                              // Constructors with name supplied
    Bool (String Name, boolean  I) {this(I); name = Name;}                                                              //N
    Bool (String Name, Bool     I) {this(I); name = Name;}                                                              //N

    Bool           ()          {ai(); invalidate();}                                                                    // Constructors
    Bool           (boolean I) {ai(); ie(Ops.set, I);}
    Bool           (Bool    I) {ai(); ie(Ops.set, I);}
    boolean       b()          {x(); return i;}
    boolean       v()          {     return v;}                                                                         //N
    void          x()          {if (!v) variableNotSet("Bool", name);}                                                  // Check a value has been set for the boolean
    Bool          X()          {v = true; return this;}                                                                 //N

    Bool        set()          {return ie(Ops.set,  true); }                                                            // Boolean operations which modify the target
    Bool        set(boolean I) {return ie(Ops.set,  I);    }
    Bool        set(Bool    I) {return ie(Ops.set,  I);    }
    Bool        set(Int     I) {return ie(Ops.set,  I);    }                                                            //N
    Bool        set(Bint    I) {return ie(Ops.set,  I.i());}
    Bool      clear()          {return ie(Ops.set,  false);}
    Bool       flip()          {return ie(Ops.flip);       }

    Bool        Set()          {return dup().set();}                                                                    //N Boolean operations that modify a copy of the target
    Bool        Set(boolean I) {return dup().set(I);}                                                                   //N
    Bool        Set(Bool    I) {return dup().set(I);}                                                                   //N
    Bool      Clear()          {return dup().clear();}                                                                  //N
    Bool       Flip()          {return dup().flip();}

    Bool         eq(boolean I) {return ie(Ops.eq,  I);}                                                                 //N
    Bool         ne(boolean I) {return ie(Ops.ne,  I);}                                                                 //N

    Bool         eq(Bool    I) {return ie(Ops.eq,  I);}                                                                 //N
    Bool         ne(Bool    I) {return ie(Ops.ne,  I);}

    Bool ie(Ops Op)            {new I() {void a() {ex(Op   );} String v() {return ev(Op   );}}; return this;}                  // Execute as an instruction because these are the building blocks of the chip with which we wish to construct the algorithm
    Bool ie(Ops Op, boolean I) {new I() {void a() {ex(Op, I);} String v() {return ev(Op, I);}}; return this;}
    Bool ie(Ops Op, Bool    I) {new I() {void a() {ex(Op, I);} String v() {return ev(Op, I);}}; return this;}
    Bool ie(Ops Op, Int     I) {new I() {void a() {ex(Op, I);} String v() {return ev(Op, I);}}; return this;}                   //N

    Bool ex(Ops Op)                                                                                                     // Execute a zeradic boolean operation
     {executingCheck();
      switch(Op)
       {case flip -> {x(); i = !i;}
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

    String ev(Ops Op)                                                                                                     // Execute a zeradic boolean operation
     {final String        n = vn();                                                                                     // Name of the variable in verilog
      final StringBuilder s = new StringBuilder();
      switch(Op)
       {case flip -> {s.append(n + " <= ~"+n+";");}
        default   -> stop("Op not implemented:", Op);
       }
      return ""+s;
     }

    String ev(Ops Op, boolean I)                                                                                        // Execute a monadic boolean operation on a constant
     {final String        n = vn();                                                                                     // Name of the variable in verilog
      final StringBuilder s = new StringBuilder();
      switch (Op)
       {case set -> {s.append(n + " <= " +               (I ? 1 : 0) + ";");}
        case eq  -> {s.append(n + " <= " + n + "  == " + (I ? 1 : 0) + ";");}
        case ne  -> {s.append(n + " <= " + n + "  != " + (I ? 1 : 0) + ";");}
        default  -> stop("Op not implemented:", Op);
       }
      return ""+s;
     }

    String ev(Ops Op, Bool I)                                                                                           // Execute a monadic boolean operation on a variable
     {final String        n = vn(), i = I.vn();                                                                         // Name of the variable in verilog
      final StringBuilder s = new StringBuilder();
      switch (Op)
       {case set -> {s.append(n + " <= " + i + ";");}
        case eq  -> {s.append(n + " <= " + n + "  == " + i + ";");}
        case ne  -> {s.append(n + " <= " + n + "  != " + i + ";");}
        default  -> stop("Op not implemented:", Op);
       }
      return ""+s;
     }

    String ev(Ops Op, Int I)                                                                                            // Execute a monadic boolean operation on an integer variable
     {final String        n = vn(), i = I.vn();                                                                         // Name of the variable in verilog
      final StringBuilder s = new StringBuilder();
      switch (Op)
       {case set -> {s.append(n + " <= " + i + "!= 0;");}
        default  -> stop("Op not implemented:", Op);
       }
      return ""+s;
     }

    Bool or (Bool b) {new I() {void a() {x(); b.x(); if (b.i) i = true;}}; return this;}                                // "Or" without short circuit. Modifies the target.
    Bool Or (Bool b) {return dup().or(b);}                                                                              //N "Or" without short circuit. Does not modify the target
    Bool and(Bool b) {new I() {void a() {x(); b.x(); if (!b.i) i = false;}}; return this;}                              // "And" without short circuit. Modifies the target.
    Bool And(Bool b) {return dup().and(b);}                                                                             //N "And" without short circuit. Does not modify the target

            Bool dup       ()       {                                        return new Bool(this);}                    // Duplicate a boolean so that the duplicated version can be modified without modifying the original
    private Bool valid     ()       {                                        return new Bool( v);}                      //N Whether the boolean is valid
    private Bool notValid  ()       {                                        return new Bool(!v);}                      //N Whether the boolean is invalid
    private Bool invalidate()       {new I() {void a() {i = v = false;   } String v() {return ev(Ops.set, false);}}; return this;} // Invalidate the boolean
    private Bool copy      (Bool I) {new I() {void a() {i = I.i; v = I.v;} String v() {return ev(Ops.set, I    );}}; return this;} //N Copy the state of a boolean without regard as to whether it is valid or not

    public String toString()                                                                                            // Print the boolean
     {final String u = "undefined_Bool";
      if (name == null) return v ? ""+i       : u;
      else              return v ? name+"="+i : u+": "+name;
     }

    String vn() {return pad("b["+ id+"]"+(name != null ? "/*"+name+"*/" : ""), 12);}                                    // Verilog name of this variable

    void stop    (final Object...O) {new If (this)   {void Then()  {new I() {void a() {Test.stop(O);}};}};}             // Conditionally print a message if true and stop
    void elseStop(final Object...O) {new If (Flip()) {void Then()  {new I() {void a() {Test.stop(O);}};}};}             //N Conditionally print a message if false and stop
    Bool say() {final Bool i = this; new I() {void a() {Test.say(i) ;}}; return this;}                                  //N Say the boolean

    Bool ok(Boolean Value)
     {new I()
       {void a()
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
         {new I() {void a()  {Test.ok(got.b(), Value.b()); }};
         }
        void Else()
         {new I() {void a() {Test.ok(got.notValid(), true);}};
         }
       };
      return this;
     }
   }

//D2 Integer values                                                                                                     // Operations on integer values

  final class Int                                                                                                       // An integer that can be passed as a parameter to a method and modified there-in
   {private int        i = 0;                                                                                           // Value of the integer
    private boolean    v = false;                                                                                       // Whether the current value of the integer is valid or not
            String  name = null;                                                                                        // The name of the variable
    private final int id = parentProgram.nextIntId++;                                                                   // Unique id for Int
    private final String traceComment = tracing() ? traceComment() : null;                                              // Location

    int         i()  {x(); return i;}                                                                                   // Current value
    boolean     v()  {     return v;}                                                                                   //N Value has been set
    void        x()  {if (!v) variableNotSet("Int", name);}                                                             // Check a value has been set for the integer

    Int (String Name)        {this();  name = Name;}                                                                    // Constructors with name supplied
    Int (String Name, int I) {this(I); name = Name;}
    Int (String Name, Int I) {this(I); name = Name;}

    Int      ()      {ai(); invalidate();}                                                                              // Constructors
    Int (int I)      {ai(); ie(Ops.set, I);}
    Int (Int I)      {ai(); ie(Ops.set, I);}

    Int  max (int I) {x(); return i < I ? new Int(I) : this;}                                                           //N
    Int  min (int I) {x(); return i > I ? new Int(I) : this;}                                                           //N
    Int  max (Int I) {final Int r = this; new If (lt(I)) {void Then() {r.set(I);}}; return r;}                          //N
    Int  min (Int I) {final Int r = this; new If (gt(I)) {void Then() {r.set(I);}}; return r;}                          //N
                                                                                                                        // Possible integer operations
    enum Ops {X, abs, add, add2, bclr, bget, bset, dec, div, down, eq, ge, gt, inc, le, lt,
       max, min, mod, mul, neg, ne, set, sqrt, sub, up};

    Int  X   ()       {return ie(Ops.X      );}                                                                         //N Integer operations
    Int  set (int  I) {return ie(Ops.set , I);}
    Int  set (Int  I) {return ie(Ops.set , I);}
    Int  set (Bint I) {return ie(Ops.set , I.i());}
    Int  add (int  I) {return ie(Ops.add , I);}
    Int  add (Int  I) {return ie(Ops.add , I);}
    Int  add2(Int  I) {return ie(Ops.add2, I);}                                                                         //N
    Int  sub (int  I) {return ie(Ops.sub , I);}
    Int  sub (Int  I) {return ie(Ops.sub , I);}
    Int  mul (int  I) {return ie(Ops.mul , I);}
    Int  mul (Int  I) {return ie(Ops.mul , I);}
    Int  div (int  I) {return ie(Ops.div , I);}
    Int  div (Int  I) {return ie(Ops.div , I);}
    Int  mod (int  I) {return ie(Ops.mod , I);}
    Int  mod (Int  I) {return ie(Ops.mod , I);}                                                                         //N
    Int  inc ()       {return ie(Ops.inc    );}
    Int  dec ()       {return ie(Ops.dec    );}
    Int  up  ()       {return ie(Ops.up     );}                                                                         //N
    Int  down()       {return ie(Ops.down   );}
    Int  sqrt()       {return ie(Ops.sqrt   );}                                                                         //N
    Int  neg ()       {return ie(Ops.neg    );}                                                                         //N
    Int  abs ()       {return ie(Ops.abs    );}

    Int ie(Ops Op)        {new I() {void a() {ex(Op   );} String v() {return ev(Op   );}}; return this;}                // Execute immediately or create an instruction for machine code to execute later
    Int ie(Ops Op, int I) {new I() {void a() {ex(Op, I);} String v() {return ev(Op, I);}}; return this;}
    Int ie(Ops Op, Int I) {new I() {void a() {ex(Op, I);} String v() {return ev(Op, I);}}; return this;}

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

    String ev(Ops Op)                                                                                                   // Execute a zeradic integer operation in verilog
     {final String        n = vn();                                                                                     // Name of the variable in verilog
      final StringBuilder s = new StringBuilder();
      switch(Op)
       {case inc  -> {s.append(n +" <= "+n+" + 1;"  );}
        case dec  -> {s.append(n +" <= "+n+" - 1;"  );}
        case up   -> {s.append(n +" <= "+n+"<< 1;"  );}
        case down -> {s.append(n +" <= "+n+">>>1;"  );}
        case sqrt -> {s.append(n +" <= sqrt("+n+");");}
        case neg  -> {s.append(n +" <= -"+n+";"     );}
        case abs  -> {s.append(n +" <= abs("+n+");" );}
        default   -> stop("Op not implemented:", Op);
       }
      return ""+s;
     }

    String ev(Ops Op, int I)                                                                                            // Execute a monadic integer operation on a constant
     {final String        n = vn();                                                                                     // Name of the variable in verilog
      final StringBuilder s = new StringBuilder();
      switch (Op)
       {case set  -> {s.append(n + " <= "        +I+";");}
        case add  -> {s.append(n + " <= "+n+" + "+I+";");}
        case sub  -> {s.append(n + " <= "+n+" - "+I+";");}
        case mul  -> {s.append(n + " <= "+n+" * "+I+";");}
        case div  -> {s.append(n + " <= "+n+" / "+I+";");}
        case mod  -> {s.append(n + " <= "+n+" % "+I+";");}
        case add2 -> {s.append(n + " <= "+n+" + "+I+" + "+I+";");}
        default   -> stop("Op not implemented:", Op);
       }
      return ""+s;
     }

    String ev(Ops Op, Int I)                                                                                            // Execute a monadic integer operation on a variable
     {final String        n = vn(), i = I.vn();                                                                         // Name of the variable in verilog
      final StringBuilder s = new StringBuilder();
      switch (Op)
       {case set  -> {s.append(n + " <= "        +i+";");}
        case add  -> {s.append(n + " <= "+n+" + "+i+";");}
        case sub  -> {s.append(n + " <= "+n+" - "+i+";");}
        case mul  -> {s.append(n + " <= "+n+" * "+i+";");}
        case div  -> {s.append(n + " <= "+n+" / "+i+";");}
        case mod  -> {s.append(n + " <= "+n+" % "+i+";");}
        case add2 -> {s.append(n + " <= "+n+" + "+i+" + "+i+";");}
        default   -> stop("Op not implemented:", Op);
       }
      return ""+s;
     }

    Int  Add (int I) {return dup().add(I) ;}                                                                            // Duplicate the target so that a copy is modified rather than the original integer
    Int  Add (Int I) {return dup().add(I) ;}
    Int  Add2(Int I) {return dup().add2(I);}                                                                            //N
    Int  Sub (int I) {return dup().sub(I) ;}
    Int  Sub (Int I) {return dup().sub(I) ;}
    Int  Mul (int I) {return dup().mul(I) ;}
    Int  Mul (Int I) {return dup().mul(I) ;}
    Int  Div (int I) {return dup().div(I) ;}
    Int  Div (Int I) {return dup().div(I) ;}                                                                            //N
    Int  Mod (int I) {return dup().mod(I) ;}
    Int  Mod (Int I) {return dup().mod(I) ;}                                                                            //N
    Int  Inc ()      {return dup().add(1) ;}
    Int  Dec ()      {return dup().sub(1) ;}
    Int  Up  ()      {return dup().up()   ;}                                                                            //N
    Int  Down()      {return dup().down() ;}
    Int  Sqrt()      {return dup().sqrt() ;}                                                                            //N
    Int  Neg ()      {return dup().neg()  ;}                                                                            //N
    Int  Abs ()      {return dup().abs()  ;}                                                                            //N

    Bool eq  (int I) {return bie(Ops.eq, I);}                                                                           // Comparisons with a constant integer
    Bool ne  (int I) {return bie(Ops.ne, I);}                                                                           //N
    Bool le  (int I) {return bie(Ops.le, I);}
    Bool lt  (int I) {return bie(Ops.lt, I);}
    Bool ge  (int I) {return bie(Ops.ge, I);}
    Bool gt  (int I) {return bie(Ops.gt, I);}

    Bool eq  (Int I) {return bie(Ops.eq, I);}                                                                           // Comparisons with a variable integer
    Bool ne  (Int I) {return bie(Ops.ne, I);}                                                                           //N
    Bool le  (Int I) {return bie(Ops.le, I);}
    Bool lt  (Int I) {return bie(Ops.lt, I);}
    Bool ge  (Int I) {return bie(Ops.ge, I);}                                                                           //N
    Bool gt  (Int I) {return bie(Ops.gt, I);}

    Bool bie (Ops Op, int I)                                                                                            // Execute immediately or create an instruction for machine code to execute later
     {final Bool b = new Bool();
      new I() {void a() {bex(Op, b, I);}};
      return b;
     }

    Bool bie(Ops Op, Int I)
     {final Bool b = new Bool();
      new I() {void a() {I.x(); bex(Op, b, I);}};
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

            Int  dup       () {return new Int(this);}                                                                   // Duplicate an integer so that the duplicated version can be modified without modifying the original
    private Bool valid     () {final Bool b = new Bool(); new I() {void a() {b.i =  v; b.v = true; }}; return b;}       // Whether the integer is valid
    private Bool notValid  () {final Bool b = new Bool(); new I() {void a() {b.i = !v; b.v = true; }}; return b;}       // Whether the integer is invalid
    private Int  invalidate() {                           new I() {void a() {  i = -1;   v = false;} String v() {return ev(Ops.set, -1);}}; return this;} // Invalidate the integer
    private Int  copy (Int I) {                           new I() {void a() {i = I.i;    v = I.v;  } String v() {return ev(Ops.set,  I);}}; return this;} // Copy the state of an integer without regard as to whether it is valid or not

    Int  bclr (Int I) {new I() {void a() {bclrEx(I);}}; return this;}                                                   //N Clear the indicated bit
    Int  bset (Int I) {new I() {void a() {bsetEx(I);}}; return this;}                                                   //N Set the indicated bit
    Int  bset (Int I, boolean V)                                                                                        // Set the indicated bit in the integer to the specified value
     {new I() {void a() {bsetEx(I, V);}};
      return this;
     }
    Int  bset (Int I, Bool V)                                                                                           // Set the indicated bit in the integer to the specified value
     {new I() {void a() {bsetEx(I, V);}};
      return this;
     }
    Bool bget(Int I)                                                                                                    // Get the indicated bit from the integer
     {final Bool B = new Bool();
      new I() {void a() {bgetEx(B, I);}};
      return B;
     }
    void bclrEx(Int I)            {x(); I.x();        ex(Int .Ops.set, clrBit(i(), I.i()));}                            //N Clear the specified bit
    void bsetEx(Int I)            {x(); I.x();        ex(Int .Ops.set, setBit(i(), I.i()));}                            //N Set the indicated bit in the integer
    void bsetEx(Int I, boolean V) {x(); I.x();        ex(Int .Ops.set, setBit(i(), I.i(), V));}                         //N Set the indicated bit in the integer to the specified value
    void bsetEx(Int I, Bool    V) {x(); I.x(); V.x(); ex(Int .Ops.set, setBit(i(), I.i(), V.b()));}                     //N Get the indicated bit in the integer
    void bgetEx(Bool B, Int    I) {x(); I.x();      B.ex(Bool.Ops.set, getBit(i(), I.i()));}                            //N

    public String toString()                                                                                            // Print the integer
     {final String u = "undefined_Int";
      if (name == null) return v ? ""+i       : u;
      else              return v ? name+"="+i : u+": "+name;
     }

    String vn() {return pad("i[" +id+"]"+(name != null ? "/*"+name+"*/" : ""), 12);}                                    // Verilog name of this variable

    Int say() {final Int i = this; new I() {void a() {Test.say(i);}};           return this;}                           // Say the integer

    Int ok(Integer Value)                                                                                               // Check the integer
     {new I()
       {void a()
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
         {new I() {void a() {Test.ok(got.i(), Value.i());}};
         }
        void Else()
         {new I() {void a() {Test.ok(got.notValid(), true);}};
         }
       };
      return this;
     }
   }

//D2 Boolean Integer                                                                                                    // An integer that can be specifically valid or invalid thus requiring an extra validity bit only for specified integers rather than all integers in the verilog representationOperations on integer values

  final class Bint                                                                                                      // An integer that can be specified as valid or invalid
   {private final Bool b = new Bool(false);                                                                             // Whether the associated integer is valid or invalid
    private final Int  i = new Int();                                                                                   // The integer component
    Bint set(Int I) {b.set(); i.set(I); return this;}                                                                   // Set to a known value
    Bool  b()       {return b;}                                                                                         // Return boolean component
    Int   i()
     {final Int I = new Int();
      b.Flip().stop("Requested int component from unset Bint");                                                         // Complain if there is no integer component to return
      new I() {void a() {I.ex(Int.Ops.set, i);}};                                                                       // Return integer component
      return I;
     }

    Bool valid     () {return b;}                                                                                       // Whether the boolean integer is valid
    Bool notValid  () {return b.Flip();}                                                                                // Whether the boolean integer is invalid
    Bint invalidate() {b.clear(); return this;}                                                                         // Mark the integer as invalid after all

    Bint copy(Bint Source)                                                                                              // Copy a boolean integer
     {new If (Source.b)
       {void Then()                                                                                                     // The source has been set
         {b.set(); i.set(Source.i());                                                                                   // Set target as valid and copy the associated integer
         }
        void Else()
         {b.clear();
         }                                                                                                              // The source has not been set yet so mark the target as unset too
       };
      return this;
     }

    Bint ok(boolean Value) {new I() {void a() {Test.ok(b.b(), Value);    }}; return this;}                              // Test the boolean value of the boolean integer
    Bint ok(int     Value) {new I() {void a() {Test.ok(i.i(), Value);    }}; return this;}                              // Test the integer value of the boolean integer
    Bint ok(Int     Value) {new I() {void a() {Test.ok(i.i(), Value.i());}}; return this;}                              // Test the integer value of the boolean integer

    void     stop(final Object...O) {new If (this) {void Then() {               new I() {void a() {Test.stop(O);}};}};} // Conditionally print a message if false and stop
    void elseStop(final Object...O) {new If (this) {void Then() {} void Else() {new I() {void a() {Test.stop(O);}};}};} // Conditionally print a message if true and stop

    public String toString()
     {final StringBuilder s = new StringBuilder();
      new I()
       {void a()
         {if (b.b()) s.append("Bint("+i+")"); else s.append("Bint(invalid)");
         }
       };
      return ""+s;
     }
   }

//D1 Byte Memory                                                                                                        // Operations on memory backed by bytes

  static int ib()      {return Integer.BYTES;}                                                                          // Number of bytes in an integer
  static int ib(int I) {return I * ib();}                                                                               // Number of bytes in a number of integers
  static Int ib(Int I) {return I.Mul(ib());}                                                                            // Number of bytes in a number of integers

  final class ByteMemory                                                                                                // Bytes being used as the main memory program
   {static int byteMemoryIds = 0;
    final  int byteMemoryId  = ++byteMemoryIds;
    byte[]bytes;                                                                                                        // Bytes of main memory

    ByteMemory(int Length) {bytes = new byte[Length];  clear(new Int(0), Length);}                                      // Create the memory

    private byte getByte(int I)                                                                                         // Get the value of a byte
     {if (tracing()) trace("memory get byte: "+I+" value:"+bytes[I]);                                                   // Trace
      return bytes[I];                                                                                                  // Get the value of a byte
     }

    private void putByte(int I, int J)                                                                                  // Put a byte into memory
     {if (tracing()) trace("memory put byte: "+I+" was:"+bytes[I]+" set:"+J);                                           // Trace
      bytes[I] = (byte)(J & 0xFF);                                                                                      // Set the value of a byte from an integer
     }

    ByteMemory copy(ByteMemory SourceMemory, Int SourceOffset, Int TargetOffset, int Width)                             // Copy the specified memory
     {new I()
       {void a()
         {System.arraycopy(SourceMemory.bytes, SourceOffset.i(), bytes, TargetOffset.i(), Width);
         }
       };
      return this;
     }

    ByteMemory clear()
     {new I() {void a() {Arrays.fill(bytes, 0, bytes.length, (byte)0);}};
      return this;
     }

    ByteMemory clear(Int Start, int Width)
     {final Int w = Start.Add(Width);
      new I() {void a() {Arrays.fill(bytes, Start.i(),  Start.i()+Width, (byte)0);}};
      return this;
     }

    ByteMemory invalidate(int Start, int Width)                                                                         // Invalidate memory by setting it values unlikely to be valid
     {Arrays.fill(bytes, Start,  Start+Width, (byte)-1);
      return this;
     }

    ByteMemory invalidate(Int Start, int Width)
     {new I() {void a() {invalidate(Start.i(),  Width);}};
      return this;
     }

    int size() {return bytes.length;}                                                                                   //N Size of memory

    Int getByte(Int I)                                                                                                  //N Get the byte at the indicated position
     {final Int r = new Int();
      new I() {void a() {r.set(getByte(I.i()));}};
      return r;
     }

    Int getInt(Int I)                                                                                                   // Get the int at the indicated position
     {final Int r = new Int();
      new I()
       {void a()
         {final int p = I.i();
          final int a = Byte.toUnsignedInt(bytes[p+0]) <<  0;
          final int b = Byte.toUnsignedInt(bytes[p+1]) <<  8;
          final int c = Byte.toUnsignedInt(bytes[p+2]) << 16;
          final int d = Byte.toUnsignedInt(bytes[p+3]) << 24;
          final int R = d | c | b | a;
          r.ex(Int.Ops.set, R);
         }
       };
      return r;
     }

    int getInt(int I)                                                                                                   // Get the int at the indicated position
     {final int a = Byte.toUnsignedInt(bytes[I+0]) <<  0;
      final int b = Byte.toUnsignedInt(bytes[I+1]) <<  8;
      final int c = Byte.toUnsignedInt(bytes[I+2]) << 16;
      final int d = Byte.toUnsignedInt(bytes[I+3]) << 24;
      return d | c | b | a;
     }

    Bool getBool(Int I, Int J)                                                                                          // Get the bit in the specified byte at the specified position within the byte
     {Bool r = new Bool();
      new I()
       {void a()
         {r.ex(Bool.Ops.set, getBit(getByte(I.i()), J.i()));
         }
       };
      return r;
     }

    Bool    getBool(Int I) {return getBool(I.Div(Byte.SIZE), I.Mod(Byte.SIZE));}                                        // Get the bit at the bit indexed location
    boolean getBool(int I) {return getBit(getByte(I / Byte.SIZE), I % Byte.SIZE);}                                      //N Get the bit at the bit indexed location - debugging

    ByteMemory putByte(Int I, Int J)                                                                                    //N Set the byte at the indicated position relative to the start to the specified value
     {new I() {void a() {putByte(I.i(), J.i());}};
      return this;
     }

    ByteMemory putInt(Int I, Int J)                                                                                     // Set the int at the indicated position relative to the start to the specified value
     {new I()
       {void a()
         {final int p = I.i(), v = J.i();
          putByte(p+0, v >>>  0);
          putByte(p+1, v >>>  8);
          putByte(p+2, v >>> 16);
          putByte(p+3, v >>> 24);
         }
       };
      return this;
     }

    ByteMemory putBool(Int I, Int J, Bool K)                                                                            // Set the bit at the indicated position in the byte at the specified position to the specified value
     {new I()
       {void a()
         {final int p = I.i();
          final int b = getByte(p);
          final int B = setBit(b, J.i(), K.b());
          putByte(p, B);
         }
       };
      return this;
     }

    ByteMemory putBool(Int I, Bool K) {putBool(I.Div(Byte.SIZE), I.Mod(Byte.SIZE), K); return this;}                    // Set the bit at the bit indexed position

//D2 Memory references                                                                                                  // References to byte memory

    final class Ref                                                                                                     // Reference into memory
     {final Int offset = new Int("memoryReferenceOffset");                                                              // Offset of this reference in memory
      final int N = Integer.BYTES;
      final ByteMemory m = ByteMemory.this;
      Ref(int Offset) {offset.set(Offset);}                                                                             // Offset this ref
      Ref(Int Offset) {offset.set(Offset);}                                                                             // Offset this ref

      ByteMemory byteMemory() {return ByteMemory.this;}
      Program    program()    {return Program.this;}

      Ref       copy(Ref Source, int Width){m.copy(Source.m, Source.offset, offset, Width); return this;}               // Copy the specified memory possibly from another byte memory
      Ref      clear(int Width)            {m.clear     (offset, Width);                    return this;}               // Clear memory by setting its bytes to zero
      Ref invalidate(int Width)            {m.invalidate(offset, Width);                    return this;}               //N Invalidate memory by setting its bytes to values unlikely to be valid
      Int    getByte(Int I)                {return m.getByte(I.Add(offset));}                                           //N Get the byte at the indicated position
      Int    getInt (Int I)                {return m.getInt (I.Mul(N).add(offset));}                                    //N Get the int at the indicated position
      Bool   getBool(Int I, Int J)         {return m.getBool(I.Add(offset), J);}                                        //N Get the bit in the specified byte at the specified position within the byte
      Bool   getBool(Int I)                {return m.getBool(I.Add(offset.Mul(Byte.SIZE)));}                            // Get the bit at the bit indexed location
      Ref    putByte(Int I, Int J)         {m.putByte(I.Add(offset), J);                    return this;}               //N Set the byte at the indicated position relative to the start to the specified value
      Ref    putInt (Int I, Int J)         {m.putInt (I.Mul(N).add(offset), J);             return this;}               // Set the int at the indicated position relative to the start to the specified value
      Ref    putBool(Int I, Int J, Bool K) {m.putBool(I.Add(offset), J, K);                 return this;}               //N Set the bit at the indicated position in the byte at the specified position to the specified value
      Ref    putBool(Int I,        Bool K) {m.putBool(I.Add(offset.Mul(Byte.SIZE)), K);     return this;}               // Set the bit at the bit indexed position
      int     getInt(int I)                {return m.getInt (I*N+offset.i());}                                          // Get an int immediately when debugging
      Int     getInt()                     {                                                return m.getInt (offset);}  // Get the referenced int
      Ref     putInt(Int J)                {m.putInt (offset, J);                           return this;}               // Put the referenced int

      boolean getBool(int I) {return getBit((int)byteMemory.bytes[I / Byte.SIZE+offset.i()], I % Byte.SIZE);}           // Get the bit at the bit indexed location - debugging

      Ref step(int Width) {return new Ref(offset.Add(Width));}                                                          // Step up from an existing ref to make a new one - only while not executing
      Ref step(Int Width) {return new Ref(offset.Add(Width));}                                                          //N Step up from an existing ref to make a new one - only while not executing

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
        if (b != 0) s.append(f("%02X ", b)); else s.append("   ");
        if ((i + 1) % 16 == 0) s.append("\n");
       }
      if (bytes.length % 16 != 0) s.append("\n");
      return (""+s).replaceAll("\\s*\n", "\n");
     }

    String save()
     {return Base64.getEncoder().encodeToString(bytes);
     }

    void reload(String Dump)
     {final byte[]decoded = Base64.getDecoder().decode(Dump);
      if (decoded.length != bytes.length) stop("Mismatched reloaded memory length:", decoded.length, bytes.length);
      System.arraycopy(decoded, 0, bytes, 0, bytes.length);
     }
   }

  interface Locatable                                                                                                   // The location of an object in memory
   {Bint getLocation();
   }

  String dumpMemory() {return program().byteMemory.dumpHex();}                                                          // Dump memory in hexadecimal format

//D1 Testing                                                                                                            // Methods useful during testing of byte machine programs

  void check(StringBuilder G, String E) {new I() {void a() {     Test.ok(nws(G), nws(E))                    ;} String v() {return "";}};} // Test the supplied content against the specified string, then clear the output area ready for the next report
  void Check(StringBuilder G, String E) {new I() {void a() {if (!Test.ok(nws(G), nws(E))) stop(G, traceBack);} String v() {return "";}};} // Test the supplied content against the specified string, print the actual output area contents and stop

//D1 Machine Code                                                                                                       // Generate machine code instructions to implement the program

//D2 Instruction                                                                                                        // An instruction represents code to be executed by a process in a single clock cycle == process step

  abstract class I                                                                                                      // Instructions implement the action of a program
   {final int instructionNumber;                                                                                        // The number of this instruction
    final String    traceBack = traceBack();                                                                            // Line at which this instruction was created
    final String traceComment = tracing() ? traceComment() : null;                                                      // Line at which this instruction was created as a comment
    enum Jump {no, might, will};                                                                                        // Whether the instruction will jump
    final Jump jump;                                                                                                    // The instruction might cause a jump

    I(Jump Jump)                                                                                                        // Add this instruction to the code for the process
     {ai();
      instructionNumber = parentProgram.code.size();                                                                    // Number each instruction - however this only make sens in delayed execution mode
      subInc();                                                                                                         // Count the number of instructions associated with each method
      jump = Jump;                                                                                                      // Ability to jump
      if (immediate()) {parentProgram.executing = this; a(); parentProgram.executing = null;}                           // Execute instruction immediately via interpretation if in immediate execution mode
      else  {program().code.push(this);}                                                                                // Save instruction in program for later execution if in delayed == non immediate execution mode
      //if (!immediate() && codeSize() % 100_000 == 1) say("CodeSize", codeSize());
     }

    I() {this(I.Jump.no);}                                                                                              // Add this instruction to the process's code assuming it will not jump

    abstract void     a();                                                                                              // The action to be performed by the instruction
    String            v() {return "Not set" + traceComment();}                                                          // The action to be performed by the instruction written in verilog
    String traceComment() {return traceComment != null ? traceComment : "";}                                            // Trace comment if it exists
   }

  final class Label                                                                                                     // Label jump targets in the program
   {int offset;                                                                                                         // The instruction location to which this label applies
    Label()    {set(); program().labels.push(this);}                                                                    // A label assigned to an instruction location
    void set() {offset = program().code.size();}                                                                        // Reassign the label to an instruction
   }

  void execute()                                                                                                        // Execute the current code
   {if (immediate()) return;                                                                                            // The code has already been executed interpretively
    if (tracing()) deleteFile(tracing);

    if (codeSize() == 0) stop("No code to execute"); else say(f("            Code size: %,d", codeSize()));
    pc = 0;
    int c, N;
    for(c = 0, N = code.size(); c < maxSteps && pc >= 0 && pc < N; ++c)                                                 // Execute each instruction within a specified number of steps
     {final I i = code.elementAt(pc);
      try
       {pc++;                                                                                                           // This is the anticipated next instruction, but the instruction can set it to effect a branch in execution flow
        executing = i;
        i.a();
        executing = null;
       }
      catch(Exception e)
       {if (executing == null) stop("Exception:", e, "while executing:", traceBack(e));
        else stop("Exception:", e, "\nin instruction:", executing.traceBack, "\nwhile executing:", traceBack(e));
       }
     }
    if (c >= maxSteps) stop("Out of steps after step:", c);
   }

  void Goto(Label Target)                                           {new I() {void a() {parentProgram.pc = Target.offset;}};}       // Goto a label unconditionally
  void Goto(Label Target, Bool If) {new If (If.b())    {void Then() {new I() {void a() {parentProgram.pc = Target.offset;}};}};}    // Goto a label if the condition is true
  void Noto(Label Target, Bool If) {new If (If.Flip()) {void Then() {new I() {void a() {parentProgram.pc = Target.offset;}};}};}    // Goto a label if the condition is false
  void Goto(Int   Target)                                           {new I() {void a() {parentProgram.pc = Target.i()   ;}};}       // Goto a saved address

  void variableNotSet(String Type, String Name)                                                                         // Variable not yet set message
   {final I i = parentProgram.executing;
    final String m = (Name != null ? '"'+Name+'"'+" " : "") + "has not been set yet";
    if (i != null) stop(Type, m, i.traceBack, "====");                                                                  // With traceback on failing instruction if possibe
    else           stop(Type, m);                                                                                       // No traceback available
   }

  <A, B> void ok(Supplier<A> a, B b)                                                                                    // Test a result of delayed execution against a known result while the program is still executing
   {new I()
     {void a()
       {if (!ok(a.get(), b)) say("====\n", traceBack);
       }
     };
   }

//D2 Instruction counts                                                                                                 // Count the number of instructions in each subroutine minus the instructions supplied by called subroutines

  int codeSize() {return program().code.size();}                                                                        // Number of instructions in current program

  static void subStart(String Name)
   {subs.push(Name);
    if (!instructionCounts.containsKey(Name)) instructionCounts.put(Name, 0);                                           // Initialize instruction count for this subroutine
   }

  static void subInc()                                                                                                  // Increment the number of instructions associated with a method
   {if (subs.size() > 0)
     {final String n = subs.lastElement();
      instructionCounts.put(n, instructionCounts.get(n) + 1);
     }
   }

  static void subFinish()                                                                                               // Finish a subroutine definition
   {if (subs.size() == 0) stop("No matching subStart()");
    subs.pop();
   }

  static String subPrint()                                                                                              // Print instruction counts
   {final StringBuilder s = new StringBuilder();
    int N = 0;
    final List<Map.Entry<String, Integer>> sorted = instructionCounts.entrySet().stream()
                                                   .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                                                   .toList();
    for (Map.Entry<String, Integer> e : sorted)
     {s.append(f("%,12d  %s\n", e.getValue(), e.getKey()));
      N += e.getValue();
     }
    s.append(f("%,8d  Total\n", N));
    return ""+s;
   }

//D1 Verilog                                                                                                            // Generate verilog

  String generateVerilog()
   {final String     name    = currentTestNameSuffix();                                                                 // Name of program
    final int  sizeMemory    = byteMemory != null ? byteMemory.size() : 0;                                              // Size of memory
    final int  numberOfInts  = nextIntId;                                                                               // Size of memory
    final int  numberOfBools = nextBoolId;                                                                              // Size of memory
    final StringBuilder s    = new StringBuilder();                                                                     // Verilog
    s.append(f("""
module %s;                                                                                                              // Bit machine to support current test
  parameter  MEMORY    = %d;                                                                                            // Amount of memory
  parameter  INT_VARS  = %d;                                                                                            // Number of integer variables
  parameter  BOOL_VARS = %d;                                                                                            // Number of boolean variables
  reg        clock;                                                                                                     // Clock for chip
  reg        reset;                                                                                                     // Reset for chip
  integer    pc;                                                                                                        // Program counter for stepping through user code
  integer    index;
  reg[7:0]   m[MEMORY-1:0];                                                                                             // Byte memory
  integer    i[INT_VARS-1:0];                                                                                           // Integers
  reg        b[BOOL_VARS-1:0];                                                                                          // Booleans

  typedef enum integer                                                                                                  // Possible states of machine
   {state_clearMemory = 0,
    state_clearInts   = 1,
    state_clearBool   = 2,
    state_execute     = 3
   } State;

  State state;                                                                                                          // Current state of machine

  always @(posedge clock) begin
    if (reset) begin                                                                                                    // Reset
      state = state_clearMemory;
      index = 0;
      pc = 0;
    end
    else begin                                                                                                          // Initialize bit machine then execute user code
      case (state)
        state_clearMemory: clearMemory();
        state_clearInts  : clearInts  ();
        state_clearBool  : clearBool  ();
        state_execute    : execute();
      endcase
    end
  end

  initial begin                                                                                                         // Clock generation
    clock = 0;
    forever #1 clock = ~clock;
  end

  initial begin                                                                                                         // Reset then wait for the program to execute in a reasonable amount of time
       reset = 0;
    #1 reset = 1;
    #1 reset = 0;
//  #200 $finish;
  end

  task automatic clearMemory;                                                                                           // Clear memory element by element
    begin
      m[index] = 0;
      index = index + 1;
      if (index >= MEMORY) state = state_clearInts;
    end
  endtask

  task automatic clearInts;                                                                                             // Clear integers
    begin
      i[index] = 0;
      index = index + 1;
      if (index >= INT_VARS) state = state_clearBool;
    end
  endtask

  task automatic clearBool;                                                                                             // Clear bools
    begin
      b[index] = 0;
      index = index + 1;
      if (index >= BOOL_VARS) state = state_execute;
    end
  endtask

  task automatic execute;                                                                                               // Execute actual code
    begin
      case(pc)
""", name, sizeMemory, numberOfInts, numberOfBools));

    for(I i : code)
     {s.append(f("        %4d: begin %s", i.instructionNumber, i.v()));
      if (i.jump == I.Jump.might) s.append(" else");
      if (i.jump != I.Jump.will)  s.append(" pc <= pc + 1;");
      s.append(" end\n");
     }

    s.append("""
        default: $finish;
      endcase
      pc = pc + 1;
    end
  endtask
endmodule
""");
    return ""+s;
   }

//D1 Testing                                                                                                            // Test expected output against got output

  static void test_programming(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex).trace(true))
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

  static void test_andOr(boolean Ex)
   {sayCurrentTestName();
    final Program  P = new Program(new Build().immediate(Ex))
     {void code()
       {final Bool z = new Bool().clear();
        final Bool o = new Bool().set();
        z.Or (z).ok(false);
        z.Or (o).ok(true);
        o.Or (z).ok(true);
        o.Or (o).ok(true);

        z.And(z).ok(false);
        z.And(o).ok(false);
        o.And(z).ok(false);
        o.And(o).ok(true);
        execute();
       }
     };
   }

  static void test_andOr()
   {          test_andOr(true);
              test_andOr(false);
   }

  static void test_add(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Int a = new Int(1);
        final Int b = new Int(0);
        final Int N = new Int(10);
        final StringBuilder s = new StringBuilder();
        new For(N)
         {void body(Int Index, Bool Continue)
           {b.add(a.dup().inc());
            new I() {void a() {s.append(f("%2d  %2d\n", a.i(), b.i()));}};
            Continue.set();
           }
         };
        Check(s, """
 1   2
 1   4
 1   6
 1   8
 1  10
 1  12
 1  14
 1  16
 1  18
 1  20
""");
        execute();
       }
     };
   }

  static void test_add()
   {          test_add(true);
              test_add(false);
   }

  static void test_fibonnacci(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex).trace(true))
     {void code()
       {final Int a = new Int("a", 0);
        final Int b = new Int("b", 1);
        final Int c = new Int("c", 0);
        final Int N = new Int("N", 10);
        final StringBuilder s = new StringBuilder();
        new For(N)
         {void body(Int Index, Bool Continue)
           {c.set(a);
            c.add(b);
            a.set(b);
            b.set(c);
            new I() {void a() {s.append(""+c+" ");} String v() {return "";}};
            Continue.set();
           }
         };
        Check(s, "c=1 c=2 c=3 c=5 c=8 c=13 c=21 c=34 c=55 c=89");
        execute();
        say(generateVerilog());
       }
     };
   }

  static void test_fibonnacci()
   {          test_fibonnacci(true);
              test_fibonnacci(false);
   }

  static void test_mod(Boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Int  a = new Int ();
        final Bool b = new Bool();
        final Int  c = new Int (0);
        final Int  N = new Int (4);
        final StringBuilder s = new StringBuilder();
        new For(N)
         {void body(Int Index, Bool Continue)
           {a.set(Index.Inc()).mod(2);
            new If (b.set(a).flip())
             {void Then() {c.dec();}
              void Else() {c.inc(); c.inc();}
             };
            new I() {void a() {s.append(""+c+" ");}};
            Continue.set();
           }
         };
        Check(s, "2 1 3 2");
        execute();
       }
     };
   }

  static void test_mod()
   {          test_mod(true);
              test_mod(false);
   }

  static Program test_incremental(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex).trace(true))
     {void code()
       {final Int a = new Int(0);
        final StringBuilder s = new StringBuilder();
                 new I() {void a() {s.append(" "+a);}};
        a.inc(); new I() {void a() {s.append(" "+a);}};
        a.inc(); new I() {void a() {s.append(" "+a);}};
        Check(s, " 0 1 2");
        execute();
       }
     };
    return P;
   }

  static void test_incremental()
   {sayCurrentTestName();
    final Program p = test_incremental(true), q = test_incremental(false);
    ok(readFile(p.tracing), readFile(q.tracing));
   }

  static void test_bits(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex))
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
   {          test_bits(true);
              test_bits(false);
   }

  static void test_remote(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex))
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
   {          test_remote(true);
              test_remote(false);
   }

  static void test_copy(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex))
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
   {          test_copy(true);
              test_copy(false);
   }

  static void test_byteMemory(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex).memory(16))
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

  static void test_byteMemoryNegative(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex).memory(8))
     {void code()
       {final ByteMemory m = byteMemory;
        new For(2)
         {void body(Int Index, Bool Continue)
           {m.putInt(new Int(0), new Int(-2));
            m.putInt(new Int(4), new Int(-3));
            m.getInt(new Int(0)).ok(-2);
            m.getInt(new Int(4)).ok(-3);
            ok(()->m.getInt( 0), -2);
            ok(()->m.getInt( 4), -3);
            execute();
           }
         };
       }
     };
   }

  static void test_byteMemoryNegative()
   {test_byteMemoryNegative(true);
    test_byteMemoryNegative(false);
   }

  static void test_byteMemoryRef(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex).memory(16))
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
   {          test_byteMemoryRef(true);
              test_byteMemoryRef(false);
   }

  static void test_invalidate(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex).memory(16))
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
   {          test_invalidate(true);
              test_invalidate(false);
   }
/*
  static void test_procedureCall(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex).memory(16))
     {void code()
       {final Procedure d = new Procedure()
         {void body()
           {final Int a = getInt("A");
            putInt("B", a.Up());
           }
         }.input(new Int("A")).output(new Int("B"));

       final Procedure e = new Procedure("e")
         {void body()
           {d.in("A", getInt("a"));
            d.call();
            putInt("b", d.oi("B"));
           }
         }.input(new Int("a")).output(new Int("b"));

        new ForCount(new Int(3))
         {void body(Int Index)
           {e.in("a", Index);
            e.call();
            e.oi("b").ok(Index.Up());
           }
         };

        maxSteps = 999;
        execute();
       }
     };
   }

  static void test_procedureCall()
   {          test_procedureCall(true);
              test_procedureCall(false);
   }
*/
  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_programming();
    test_andOr();
    test_add();
    test_fibonnacci();
    test_mod();
    test_incremental();
    test_remote();
    test_bits();
    test_copy();
    test_byteMemory();
    test_byteMemoryNegative();
    test_byteMemoryRef();
    test_invalidate();
    //test_procedureCall();
   }

  static void newTests()                                                                                                // Tests being worked on
   {//oldTests();
    test_fibonnacci(false);

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
