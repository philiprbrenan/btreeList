//----------------------------------------------------------------------------------------------------------------------
// Machine level programming in Java
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;
import java.util.function.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;

//D1 Construct                                                                                                          // Develop and test a java program to describe a chip and emulate its operation.

public class Program extends Test                                                                                       // Develop and test a java program to describe a chip and emulate its operation.
 {final  boolean                    supressTraceComments = true;                                                        // Add trace comments to trace output - requires a lot of memory
  final  boolean                         generateVerilog = true;                                                        // Generate verilog version of each program
  final  boolean                              runVerilog = true;                                                        // Execute  verilog version of each program
  final  boolean             suppressNamesInInstructions = true;                                                        // Include names in instructions
  final  boolean                    compressInstructions = true;                                                        // Compress out identical instructions
  public int                                    maxSteps = 9999;                                                        // Number of steps permitted in code execution

  static String                                testGroup = null;                                                        // Tests can be split into groups so that they can be run in parallel
  final static String                      verilogFolder = "verilog/";                                                  // Verilog folder
  final static String                   verilogTraceFile = fe("traceVerilog", "txt");                                   // Verilog trace file
  final static String                      javaTraceFile = fe("traceJava",    "txt");                                   // Java trace file
  final static String                      verilogSuffix = "v";                                                         // Suffix for verilog files

  final Stack<I>                                    code = new Stack<>();                                               // Machine code instructions
  final Stack<Label>                              labels = new Stack<>();                                               // Labels for instructions in this process
  final Program                            parentProgram;                                                               // Redirect the code and variables of one program to another to allow components to be tested in isolation before their code is integrated into a larger program.
  final UnitMemory                            unitMemory;                                                               // Optional memory associated with the program
  final boolean                                immediate;                                                               // Execute immediately if true else generate machine code and execute later
  public  I                                    executing = null;                                                        // Instruction currently being executed
  public  I                                    compiling = null;                                                        // Instruction currently being compiled
  private int                                  nextIntId = 0;                                                           // Unique id for each Int
  private int                                  lastIntId = 0;                                                           // The base for integers in this flow of control block
  private int                                 nextBoolId = 0;                                                           // Unique id for each Bool
  private int                                 lastBoolId = 0;                                                           // The base for booleans in this flow of control block
  private static int                            programs = 0;                                                           // Unique id for each program
  final   int                                  programId = ++programs;                                                  // Unique id for this program
  private int                                         pc;                                                               // Program counter indicating the instruction to be executed after the current one
  private int                                  currentPc;                                                               // Current program counter
  final        Stack<UnitMemory>                memories = new Stack<>();                                               // Memories used by this program and its dependent programs
  final        Stack<Int>                           ints = new Stack<>();                                               // Int variables
  final        Stack<Bool>                         bools = new Stack<>();                                               // Bool variables
  final static Stack<String>                        subs = new Stack<>();                                               // Name of the current method is cached here so that we can count instructions
        static       String                    subsTrace = null;                                                        // Traceback through the methods currently active
  final static TreeMap<String,Integer> instructionCounts = new TreeMap<>();                                             // Count instructions by subroutine in which they are added
  final static TreeMap<String,Stack<I>> matchingInstructions = new TreeMap<>();                                         // Combine instruction with identical verilog text
//final static TreeMap<String,Procedure> procedures      = new TreeMap<>();                                             // Procedures by name for this program
  final TreeSet<String>              extraVerilogMethods = new TreeSet<>();                                             // Save additional Verilog methods here prefixed by "x" - they will be incorporated into the generated Verilog and thus become available to instructions
  final TreeMap<Integer,FlowControl>         flowControl = new TreeMap<>();                                             // Instruction number to first instruction (the base instruction) in a flow control block
        int                                       jtrace = 0;                                                           // Count the number of  times jtrace() has been called to demonstrate that each instruction generates one matching call to jtrace
        int                                       vtrace = 0;                                                           // Count the number of  times vtrace() has been called to demonstrate that each instruction generates one matching call to vtrace

  final static class Build                                                                                              // Builder for this program
   {boolean immediate;                                                                                                  // Immediate mode
    boolean trace;                                                                                                      // Trace execution
    Program parent;                                                                                                     // Parent program
    Integer size;                                                                                                       // Memory allocated by this program
    Build immediate (boolean Immediate) {immediate = Immediate; return this;}
    Build parent (   Program Parent)    {parent    = Parent;    return this;}
    Build memory (   int     Size)      {size      = Size;      return this;}
    Build trace (    boolean Trace)     {trace     = Trace;     return this;}
   }

  Program (Build Build)                                                                                                 // Construct
   {immediate     = Build.immediate;                                                                                    // Immediate or delayed execution
    parentProgram = Build.parent == null ? this : Build.parent;                                                         // Parent program that will contain the code
    new FlowControl();                                                                                                  // Initialize flow control
    unitMemory    = Build.size   != null ? new UnitMemory(Build.size) : null;                                           // Memory associated with program if any
    makePath(verilogTestFolder());                                                                                      // Verilog folder for this test
    deleteAllFiles(verilogTestFolder(), 9);                                                                             // Delete generated Verilog files created by a prior run of the current test
    code();                                                                                                             // Load or execute the code associated with this program
   }

  void code ()         {}                                                                                               // Override to provide some code for this program
  boolean immediate () {return program().immediate;}                                                                    // Executing immediately via interpretation
  boolean executing () {return program().executing != null;}                                                            // Executing machine code
  Program   program () {return parentProgram;}                                                                          // Address this program

  void executingCheck ()     {if (!program().executing()) stop("Not executing");}                                       // Confirm that code is being executed and that consequently an instruction should be executed otherwise complain
  void parentProgramCheck () {if (program() != program().program()) stop("Parent program not set to parent program");}  // Check that code is being written to the expected program

  void  ai ()                                                                                                           // An executing program cannot be extended by adding new data or instructions
   {final I      i = program().executing;
    final String m = immediate() ? "immediate" : "delayed";
    if (i != null) stop("Allocation within an instruction while executing in", m, "mode:", i.traceBack, "====");
   }

  Program maxSteps (int MaxSteps) {program().maxSteps = MaxSteps; return this;}                                         // Set number of steps
  void jtraceInc() {++program().jtrace;}                                                                                // Count trace records written
  void vtraceInc() {++program().vtrace;}

//D1 Program                                                                                                            // Program execution structures.  the //D* comments are headers at different levels in the documentation describing this code

//D2 Flow control                                                                                                       // Flow  control identifies each section of code that could be jumped into so that references to a variable can be coded as a small constant offset from a variable base to increase the probability that instructions will have the same verilog allowing identical such instructions to be compressed out

  void insertFlowControl()                                                                                              // The integer and boolean base at the entry to a flow of control block
   {new FlowControl();                                                                                                  // Record the current position in the integer and boolean variables

    new I()                                                                                                             // Instruction to mark the start of a flow of control block
     {void     a()                                                                                                      // Only a trace record is needed in Java as variables are addressed via references
       {jTrace(f("%8d InsertLastBaseInstruction: %s\n", program().currentPc, instructionLocationAsComment()));
       }
      String   v()                                                                                                      // Set the current base for integers and booleans so that they can be applied symbolically in instructions just like a base register in S/360. The verilog code generated inline at different points in the program by the same java code will tend to have the same references patterns to variables resulting in more opportunities to identify instructions whose code is identical and can thus be compressed out by making multiple references to the lowest instruction with this code.
       {final FlowControl f = getFlowControlForInstructionBeingCompiled();                                              // Flow control details for this flow control instruction

        return "lastIntId <= "+f.intId+"; lastBoolId <= "+f.boolId+";"+
          vTrace("%8d InsertLastBaseInstruction: "+instructionLocationAsComment(), "pc");
       }
      int traces() {return 1;}
     };
   }

  class FlowControl                                                                                                     // Start a block of instructions at a jump in point
   {final int boolId;                                                                                                   // Base of boolean variables
    final int  intId;                                                                                                   // Base of integer variables
    FlowControl()
     {final Program p = program();
      boolId = p.nextBoolId;                                                                                            // Base of boolean variables
      intId  = p.nextIntId ;                                                                                            // Base of integer variables
      flowControl.put(p.codeSize(), this);                                                                              // Map location of flow control instruction to position in integer and boolean variables
     }
   }

  FlowControl getFlowControlForInstructionBeingCompiled()                                                               // Get flow control for instruction being compiled
   {final Integer k = program().flowControl.floorKey(program().compiling.instructionNumber);
    if (k == null) stop("Unable to get flow control details while compiling");
    final FlowControl f = program().flowControl.get(k);
    if (f == null) stop("Unable to get flow control details for key:", k, "in:", program().flowControl);
    return f;
   }

//D2 For loops                                                                                                          // For loops with fixed and variable number of iterations

  abstract class For                                                                                                    // For loop
   {For (Int Start, Int End)                                                                                            // Execute the loop the specified number of times
     {final Int index = new Int("Index");
      final Bool cont = new Bool("Continue");

      if (immediate())                                                                                                  // Immediate execution
       {index.set(Start);                                                                                               // Start index
        for(int i : range(Start.i(), End.i()))                                                                          // Iterate over the specified range
         {cont.clear();                                                                                                 // Terminate unless told otherwise
          body(index, cont);                                                                                            // Execute the loop
          index.inc();                                                                                                  // Set the index to each element of the specified range
          if (!cont.b()) break;                                                                                         // Terminate the loop unless continuation has been requested
         }
       }
      else                                                                                                              // Machine code
       {index.set(Start);                                                                                               // Start index
        final Label start = new Label();                                                                                // Start of for loop code
        final Label   end = new Label();                                                                                // End of for loop code
        insertFlowControl();                                                                                            // Start of flow of control block
        new I(I.Jump.might)                                                                                             // Will jump
         {void   a() {if (index.i() >=  End.i()) program().pc = end.offset;}                                            // Index out of range
          String v() {return "if ("+index.vn()+" >= "+End.vn()+") pc <= pc + "+ (end.offset - instructionNumber) + ";";}// Index out of range
          int traces() {return 0;}
         };
        cont.clear();                                                                                                   // Terminate unless told otherwise
        body(index, cont);                                                                                              // Execute the loop
        index.inc();                                                                                                    // Increment loop counter
        new I(I.Jump.will)
         {void   a() {program().pc = cont.b() ? start.offset : end.offset;}                                             // Continue execution of the loop as long as requested
          String v()
           {return "if ("+ cont.vn()+") pc <= pc + "+ (start.offset - instructionNumber) + "; "+
                                  "else pc <= pc + "+ (end  .offset - instructionNumber) + ";";}                        // Continue execution of the loop as long as requested
          int traces() {return 0;}
         };
        end.set();                                                                                                      // End of the loop
        insertFlowControl();                                                                                            // Start of flow of control block
       }
     }

    For (int End) {this(new Int("Start", 0), new Int("End", End));}                                                     // Execute the loop the specified number of times as long as it returns true
    For (Int End) {this(new Int("Start", 0),                End);}                                                      // Execute the loop the specified number of times as long as it returns true

    abstract void body (Int Index, Bool Continue);                                                                      // Body of the for loop - execute while in range and continuation has been requested
   }

  abstract class ForCount                                                                                               // For loop for a precomputed number of times
   {ForCount (Int Start, Int End)                                                                                       // Execute the loop the specified number of times
     {final Int index = new Int("Index");

      if (immediate())                                                                                                  // Immediate execution
       {index.set(Start);                                                                                               // Start index
        for(int i : range(Start.i(), End.i()))                                                                          // Iterate over the specified range
         {body(index);                                                                                                  // Execute the loop
          index.inc();                                                                                                  // Increment loop counter
         }
       }
      else                                                                                                              // Machine code
       {index.set(Start);                                                                                               // Start index
        final Label start = new Label();                                                                                // Start of for loop code
        final Label   end = new Label();                                                                                // End of for loop code
        insertFlowControl();                                                                                            // Start of flow of control block
        new I(I.Jump.might)                                                                                             // The for loop will not be executed if the execution count is less than 1
         {void   a()   {if (index.i() >=  End.i()) program().pc = end.offset;}                                          // Index out of range
          String v()   {return "if ("+index.vn()+" >= "+End.vn()+") pc <= pc + "+(end.offset-instructionNumber)+";";}   // Index out of range
          int traces() {return 0;}
         };
        body(index);                                                                                                    // Execute the loop
        index.inc();                                                                                                    // Increment loop counter
        new I(I.Jump.will)                                                                                              // Will jump
         {void   a() {program().pc = start.offset;}                                                                     // Restart loop
          String v() {return "pc <= pc + "+(start.offset - instructionNumber)+";";}                                     // Index out of range
          int traces() {return 0;}
         };
        end.set();                                                                                                      // End of the loop
        insertFlowControl();                                                                                            // Start of flow of control block
       }
     }

    ForCount (int  End) {this(new Int("Start", 0), new Int("End", End)   );}                                            // Execute the loop the specified number of times
    ForCount (Int  End) {this(new Int("Start", 0),                End    );}                                            // Execute the loop the specified number of times
    ForCount (Bint End) {this(new Int("Start", 0),                End.i());}                                            // Execute the loop the specified number of times

    abstract void body (Int Index);                                                                                     // Body of the for loop - execute while in range and continuation requested
   }

//D2 If                                                                                                                 // If then else

  abstract class If                                                                                                     // If statement
   {If (boolean Condition)                                                                                              // A constant that selects code at compile time
     {if (Condition) Then(); else Else();
     }
    If (Bool    Condition)
     {if (immediate())                                                                                                  // Immediate execution
       {if (Condition.b()) Then();
        else               Else();
       }
     else                                                                                                               // Machine code
       {final Label lse = new Label();                                                                                  // Start of else
        final Label end = new Label();                                                                                  // End of if
        new I(I.Jump.might)                                                                                             // Jump to else if condition is false
         {void   a() {          if (!Condition.b()) program().pc = lse.offset;}
          String v() {return "if (!"+Condition.vn() + ") pc <= pc + "+(lse.offset-instructionNumber)+";";}
          int traces() {return 0;}
         };
        Then();                                                                                                         // Then body
        new I(I.Jump.will)                                                                                              // Jump over else to end
         {void   a() {program().pc  = end.offset;}
          String v() {return "pc <= pc + "+(end.offset-instructionNumber)+";";}
          int traces() {return 0;}
         };
        lse.set();                                                                                                      // Start of else
        insertFlowControl();                                                                                            // Start of flow of control block
        Else();                                                                                                         // Else body
        end.set();                                                                                                      // End of the if statement
        insertFlowControl();                                                                                            // Start of flow of control block
       }
     }

    If (Bint Condition) {this(Condition.b);}                                                                            // If from boolean integer

    abstract void Then ();                                                                                              // Then clause
             void Else () {}                                                                                            // Else clause
   }

//  void If (Bool Choice, Runnable Then, Runnable Else)                                                                   // If then/else with lambdas
//   {new If (Choice)
//     {void Then() {Then.run();}
//      void Else() {Else.run();}
//     };
//   }

//  <T extends Int> T If (Bool Choice, T Set, Supplier<T> Then, Supplier<T> Else)                                         //N Choose between two alternatives.
//   {new If (Choice)
//     {void Then() {Set.set(Then.get());}
//      void Else() {Set.set(Else.get());}
//     };
//    return Set;
//   }

//D2 Procedure                                                                                                          // Procedure with parameters and return value.  Only works for static classes because it is unable to emulate "this" so commented out for the moment
/*
  abstract class Procedure                                                                                              // Procedure
   {final String      name;                                                                                             // The name of the procedure so it can be cataloged and reused later by name which allows the just in time generation of procedures
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
      Goto(returnAddress);                                                                                              // Address at which to resume execution after the subroutine call
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

    Procedure in(String Name, boolean B) {cbi(Name); inputBool.get(Name).set(B); return this;}                          // Set an input  boolean parameter by name to a boolean constant before calling the procedure
    Procedure in(String Name, Bool    B) {cbi(Name); inputBool.get(Name).set(B); return this;}                          // Set an input  boolean parameter by name to a boolean variable before calling the procedure
    Bool      ob(String Name)            {cbo(Name); return outputBool.get(Name);}                                      // Get an output boolean parameter after calling a procedure
   }
*/
//D1 Data                                                                                                               // Operations on boolean and integer data

//public sealed interface Data permits Bool, Int {}                                                                     // Known types of data

//D2 Boolean values                                                                                                     // Operations on boolean values

  final class Bool                                                                                                      // A boolean value
   {boolean    i = false;                                                                                               // Value of the boolean
    boolean    v = false;                                                                                               // Whether the current value of the integer is valid or not
    final int id = program().nextBoolId++;                                                                              // Unique id for Bool
    String  name = null;                                                                                                // The name of the variable

    enum Ops {and, eq, flip, ne, or, set};                                                                              // Boolean operation classification by argument types

    Bool (String Name)             {this();  name = Name;}                                                              // Constructors with name supplied

    Bool ()                        {ai(); invalidate();   bools.push(this);}                                            // Constructors
    Bool (boolean I)               {ai(); ie(Ops.set, I); bools.push(this);}
    Bool (Bool    I)               {ai(); ie(Ops.set, I); bools.push(this);}
    boolean       b ()             {x(); return i;}
    void          x ()             {if (!v) variableNotSet("Bool", name);}                                              // Check a value has been set for the boolean

    Bool        set ()             {return ie(Ops.set,  true); }                                                        // Boolean operations which modify the target
    Bool        set (boolean I)    {return ie(Ops.set,  I);    }
    Bool        set (Bool    I)    {return ie(Ops.set,  I);    }
    Bool        set (Bint    I)    {return ie(Ops.set,  I.i());}
    Bool      clear ()             {return ie(Ops.set,  false);}
    Bool       flip ()             {return ie(Ops.flip);       }
    Bool       Flip ()             {return dup().flip();}
    Bool         ne (Bool    I)    {return ie(Ops.ne,  I);}

    Bool ie (Ops Op)            {new I() {void a() {ex(Op   );} String v() {return ev(Op   );}}; return this;}          // Execute as an instruction because these are the building blocks of the chip with which we wish to construct the algorithm
    Bool ie (Ops Op, boolean I) {new I() {void a() {ex(Op, I);} String v() {return ev(Op, I);}}; return this;}
    Bool ie (Ops Op, Bool    I) {new I() {void a() {ex(Op, I);} String v() {return ev(Op, I);}}; return this;}
    Bool ie (Ops Op, Int     I) {zz(); new I() {void a() {ex(Op, I);} String v() {return ev(Op, I);}}; return this;}          //N

    Bool ex (Ops Op)                                                                                                    // Execute a zeradic boolean operation
     {executingCheck();
      switch(Op)
       {case flip -> {x(); i = !i;}
        default   -> stop("Op not implemented:", Op);
       }
      jtrace();
      return this;
     }

    Bool ex (Ops Op, boolean I)                                                                                         // Execute a monadic boolean operation on a constant
     {executingCheck();
      switch (Op)
       {case set -> {i  = I;          }
        case eq  -> {x(); i = i == I; }
        case ne  -> {x(); i = i != I; }
        default  -> stop("Op not implemented:", Op);
       }
      v = true;
      jtrace();
      return this;
     }

    Bool ex (Ops Op, Bool I)                                                                                            // Execute a monadic boolean operation on a variable
     {executingCheck();
      I.x(); return ex(Op, I.i);
     }

    Bool ex (Ops Op, Int I)                                                                                             // Execute a monadic boolean operation on an integer variable
     {executingCheck();
      switch(Op)
       {case set -> {I.x(); i = I.i > 0; v = true;}
        default  -> stop("Op not implemented:", Op);
       }
      jtrace();
      return this;
     }

    String ev (Ops Op)                                                                                                  // Execute a zeradic boolean operation
     {final String        n = vn();                                                                                     // Name of the variable in Verilog
      final StringBuilder s = new StringBuilder();
      switch(Op)
       {case flip -> {s.append("!"+n);}
        default   -> stop("Op not implemented:", Op);
       }
      return vtrace(s);                                                                                                 // Trace the operation
     }

    String ev (Ops Op, boolean I)                                                                                       // Execute a monadic boolean operation on a constant
     {final String        n = vn();                                                                                     // Name of the variable in Verilog
      final StringBuilder s = new StringBuilder();
      switch (Op)
       {case set -> {s.append(              (I ? 1 : 0));}
        case eq  -> {s.append(n + "  == " + (I ? 1 : 0));}
        case ne  -> {s.append(n + "  != " + (I ? 1 : 0));}
        default  -> stop("Op not implemented:", Op);
       }
      return vtrace(s);                                                                                                 // Trace the operation
     }

    String ev (Ops Op, Bool I)                                                                                          // Execute a monadic boolean operation on a variable
     {final String        n = vn(), i = I.vn();                                                                         // Name of the variable in Verilog
      final StringBuilder s = new StringBuilder();
      switch (Op)
       {case set -> {s.append(i);}
        case eq  -> {s.append(n + "  == " + i);}
        case ne  -> {s.append(n + "  != " + i);}
        default  -> stop("Op not implemented:", Op);
       }
      return vtrace(s);                                                                                                 // Trace the operation
     }

    String ev (Ops Op, Int I)                                                                                           // Execute a monadic boolean operation on an integer variable
     {final String        n = vn(), i = I.vn();                                                                         // Name of the variable in Verilog
      final StringBuilder s = new StringBuilder();
      switch (Op)
       {case set -> {s.append(i + "!= 0");}
        default  -> stop("Op not implemented:", Op);
       }
      return vtrace(s);                                                                                                 // Trace the operation
     }

    String vtrace (String Value)                                                                                        // Trace a boolean operation
     {vtraceInc();
      final FlowControl f = getFlowControlForInstructionBeingCompiled();
      final int         i = f.boolId;
      return vn()+" <= traceBool("+(id - i)+", "+Value+");";
     }

    String vtrace (StringBuilder Value) {return vtrace(""+Value);}                                                      // Trace a boolean operation

    Bool or (Bool b)                                                                                                    // "Or" without short circuit. Modifies the target.
     {new I()
       {void   a() {x(); b.x(); if (b.i) i = true; jtrace();}
        String v() {return vtrace(vn()+" || "+b.vn());}
       };
      return this;
     }

    Bool and (Bool b)                                                                                                   // "And" without short circuit. Modifies the target.
     {new I()
       {void   a() {x(); b.x(); if (!b.i) i = false; jtrace();}
        String v() {return vtrace(vn()+" && "+b.vn());}
       };
      return this;
     }

    Bool Or ( Bool b) {return dup().or(b);}                                                                             // "Or" without short circuit. Does not modify the target
    Bool And (Bool b) {return dup().and(b);}                                                                            // "And" without short circuit. Does not modify the target
    Bool dup ()       {return new Bool(this);}                                                                          // Duplicate a boolean so that the duplicated version can be modified without modifying the original

    private Bool invalidate ()                                                                                          // Invalidate the boolean
     {new I()
       {void   a() {       ex(Ops.set, false);}
        String v() {return ev(Ops.set, false);}
       };
     return this;
     }

    public String toString ()                                                                                           // Print the boolean
     {final String u = "undefined_Bool";
      if (name == null) return v ? ""+i       : u;
      else              return v ? name+"="+i : u+": "+name;
     }

    String   vn ()                                                                                                      // Verilog name of this variable
     {final FlowControl f = program().getFlowControlForInstructionBeingCompiled();
      final String n = suppressNamesInInstructions ? "" : name != null ? "/*"+name+"*/" : "";
      return pad("b[lastBoolId+"+(id - f.boolId)+"]"+n, 12);
     }

    void stop (final Object...O)                                                                                        // Conditionally print a message if true and stop
     {new If (this)
       {void Then()
         {new I(I.Jump.will)
           {void   a() {Test.stop(O);}
            String v() {return "pc <= -1;";}
            boolean trace() {return false;}
           };
         }
       };
     }

    Bool say () {new I() {void a() {Test.say(this);}}; return this;}                                                    // Say the boolean

    void jtrace () {jTrace(f("%8d b %8d = %8d\n", program().currentPc, id, (i ? 1 : 0)));}                              // Trace the boolean  operation by appending an entry to the java trace file

    Bool ok (boolean Value)                                                                                             // Memory trace from java makes this test redundant in Verilog if the Verilog trace matches the java trace
     {final  Bool got = this;
      new I()
       {void a()
         {if (!got.v) stop("Invalid Bool being tested at:", program().executing.instructionLocation());
          Test.ok(i, Value);
         }
        int traces() {return 0;         }
       };
      return this;
     }

    Bool ok (Bool Value)                                                                                                // Memory trace from java makes this test redundant in Verilog if the Verilog trace matches the java trace
     {final Bool got = this;
      if (immediate() && !Value.v) stop("Invalid expected Bool has been supplied for testing");
      new I()
       {void a()
         {if (!got.v) stop("Invalid Bool being tested at:", program().executing.instructionLocation());
          Test.ok(got.b(), Value.b());
         }
        int traces() {return 0;}
       };
      return this;
     }
   }                                                                                                                    // Bool

//D2 Integer values                                                                                                     // Operations on integer values

  final class Int                                                                                                       // An integer value
   {private int        i = 0;                                                                                           // Value of the integer
    private boolean    v = false;                                                                                       // Whether the current value of the integer is valid or not
            String  name = null;                                                                                        // The name of the variable
    final int id = program().nextIntId++;                                                                               // Unique id for Int

    int         i ()  {x(); return i;}                                                                                  // Current value
    void        x ()  {if (!v) variableNotSet("Int", name);}                                                            // Check a value has been set for the integer

    Int (String Name)        {this();  name = Name;}                                                                    // Constructors with name supplied
    Int (String Name, int I) {this(I); name = Name;}
    Int (String Name, Int I) {this(I); name = Name;}

    Int ()           {ai(); invalidate();   ints.push(this);}                                                           // Constructors without names
    Int (int I)      {ai(); ie(Ops.set, I); ints.push(this);}
    Int (Int I)      {ai(); ie(Ops.set, I); ints.push(this);}
                                                                                                                        // Possible integer operations
    enum Ops {abs, add, add2, dec, div, down, eq, ge, gt, inc, le, lt,
       mod, mul, neg, ne, set, sqrt, sub, up};

    Int  set (int  I) {return ie(Ops.set , I);}
    Int  set (Int  I) {return ie(Ops.set , I);}
    Int  set (Bint I) {return ie(Ops.set , I.i());}
    Int  add (int  I) {return ie(Ops.add , I);}
    Int  add (Int  I) {return ie(Ops.add , I);}
    Int  add2(Int  I) {zz(); return ie(Ops.add2, I);}                                                                         //N
    Int  sub (int  I) {return ie(Ops.sub , I);}
    Int  sub (Int  I) {return ie(Ops.sub , I);}
    Int  mul (int  I) {return ie(Ops.mul , I);}
    Int  mul (Int  I) {return ie(Ops.mul , I);}
    Int  div (int  I) {return ie(Ops.div , I);}
    Int  div (Int  I) {return ie(Ops.div , I);}
    Int  mod (int  I) {return ie(Ops.mod , I);}
    Int  mod (Int  I) {zz(); return ie(Ops.mod , I);}                                                                         //N
    Int  inc ()       {return ie(Ops.inc    );}
    Int  dec ()       {return ie(Ops.dec    );}
    Int  up  ()       {zz(); return ie(Ops.up     );}                                                                         //N
    Int  down()       {return ie(Ops.down   );}
    Int  sqrt()       {zz(); return ie(Ops.sqrt   );}                                                                         //N
    Int  neg ()       {zz(); return ie(Ops.neg    );}                                                                         //N
    Int  abs ()       {return ie(Ops.abs    );}

    Int ie (Ops Op)        {new I() {void a() {ex(Op   );} String v() {return ev(Op   );}}; return this;}               // Execute immediately or create an instruction for machine code to execute later
    Int ie (Ops Op, int I) {new I() {void a() {ex(Op, I);} String v() {return ev(Op, I);}}; return this;}
    Int ie (Ops Op, Int I) {new I() {void a() {ex(Op, I);} String v() {return ev(Op, I);}}; return this;}

    Int ex (Ops Op)                                                                                                     // Execute a zeradic integer operation
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

      jtrace();
      return this;
     }

    Int ex (Ops Op, int I)                                                                                              // Execute a monadic integer operation on a constant
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
      jtrace();
      return this;
     }

    Int ex (Ops Op, Int I)                                                                                              // Execute a monadic integer operation on a variable
     {executingCheck();
      I.x();
      return ex(Op, I.i());
     }

    String ev (Ops Op)                                                                                                  // Execute a zeradic integer operation in Verilog
     {final String        n = vn();                                                                                     // Name of the variable in Verilog
      final StringBuilder s = new StringBuilder();
      switch(Op)
       {case inc  -> {s.append(n+" + 1"     );}
        case dec  -> {s.append(n+" - 1"     );}
        case up   -> {s.append(n+"<< 1"     );}
        case down -> {s.append(n+">>>1"     );}
        case sqrt -> {s.append("sqrt("+n+")");}
        case neg  -> {s.append("-"+n        );}
        case abs  -> {s.append("(("+n+" < 0) ? -"+n+" : "+n+")");}
        default   -> stop("Op not implemented:", Op);
       }
      return vtrace(s);
     }

    String ev (Ops Op, int I)                                                                                           // Execute a monadic integer operation on a constant
     {final String        n = vn();                                                                                     // Name of the variable in Verilog
      final StringBuilder s = new StringBuilder();
      switch (Op)
       {case set  -> {s.append(        I);}
        case add  -> {s.append(n+" + "+I);}
        case sub  -> {s.append(n+" - "+I);}
        case mul  -> {s.append(n+" * "+I);}
        case div  -> {s.append(n+" / "+I);}
        case mod  -> {s.append(n+" % "+I);}
        case add2 -> {s.append(n+" + "+I+" + "+I);}
        default   -> stop("Op not implemented:", Op);
       }
      return vtrace(s);
     }

    String ev (Ops Op, Int I)                                                                                           // Execute a monadic integer operation on a variable
     {final String        n = vn(), i = I.vn();                                                                         // Name of the variable in Verilog
      final StringBuilder s = new StringBuilder();
      switch (Op)
       {case set  -> {s.append(        i);}
        case add  -> {s.append(n+" + "+i);}
        case sub  -> {s.append(n+" - "+i);}
        case mul  -> {s.append(n+" * "+i);}
        case div  -> {s.append(n+" / "+i);}
        case mod  -> {s.append(n+" % "+i);}
        case add2 -> {s.append(n+" + "+i+" + "+i);}
        default   -> stop("Op not implemented:", Op);
       }
      return vtrace(s);
     }

    String vtrace (String Value)                                                                                        // Trace an integer operation
     {vtraceInc();
      final FlowControl f = program().getFlowControlForInstructionBeingCompiled();
      final int         i = f.intId;
      return vn()+" <= traceInt ("+(id - i)+", "+Value+");";
     }

    String vtrace (StringBuilder Value) {return vtrace(""+Value);}                                                      // Trace an integer operation

    Int  Add (int I) {return dup().add(I) ;}                                                                            // Duplicate the target so that a copy is modified rather than the original integer
    Int  Add (Int I) {return dup().add(I) ;}
    Int  Add2(Int I) {zz(); return dup().add2(I);}                                                                            //N
    Int  Sub (int I) {return dup().sub(I) ;}
    Int  Sub (Int I) {return dup().sub(I) ;}
    Int  Mul (int I) {return dup().mul(I) ;}
    Int  Mul (Int I) {return dup().mul(I) ;}
    Int  Div (int I) {return dup().div(I) ;}
    Int  Div (Int I) {zz(); return dup().div(I) ;}                                                                            //N
    Int  Mod (int I) {return dup().mod(I) ;}
    Int  Mod (Int I) {zz(); return dup().mod(I) ;}                                                                            //N
    Int  Inc ()      {return dup().add(1) ;}
    Int  Dec ()      {return dup().sub(1) ;}
    Int  Up  ()      {zz(); return dup().up()   ;}                                                                            //N
    Int  Down()      {return dup().down() ;}
    Int  Sqrt()      {zz(); return dup().sqrt() ;}                                                                            //N
    Int  Neg ()      {zz(); return dup().neg()  ;}                                                                            //N
    Int  Abs ()      {zz(); return dup().abs()  ;}                                                                            //N

    Bool eq ( int I) {return bie(Ops.eq, I);}                                                                           // Comparisons with a constant integer
    Bool ne ( int I) {zz(); return bie(Ops.ne, I);}                                                                           //N
    Bool le ( int I) {return bie(Ops.le, I);}
    Bool lt ( int I) {return bie(Ops.lt, I);}
    Bool ge ( int I) {return bie(Ops.ge, I);}
    Bool gt ( int I) {return bie(Ops.gt, I);}

    Bool eq ( Int I) {return bie(Ops.eq, I);}                                                                           // Comparisons with a variable integer
    Bool ne ( Int I) {zz(); return bie(Ops.ne, I);}                                                                           //N
    Bool le ( Int I) {return bie(Ops.le, I);}
    Bool lt ( Int I) {return bie(Ops.lt, I);}
    Bool ge ( Int I) {zz(); return bie(Ops.ge, I);}                                                                           //N
    Bool gt ( Int I) {return bie(Ops.gt, I);}

    Bool bie (Ops Op, int I)                                                                                            // Instruction to perform a boolean comparison between an integer variable and an integer constant
     {final Bool b = new Bool();
      new I()
       {void   a() {       bex(Op, b, I);}
        String v() {return bev(Op, b, I);}
       };
      return b;
     }

    Bool bie (Ops Op, Int I)                                                                                            // Instruction to perform a boolean comparison between two integer variables
     {final Bool b = new Bool();
      new I()
       {void   a() {I.x(); bex(Op, b, I);}
        String v() {return bev(Op, b, I);}
       };
      return b;
     }

    void bex (Ops Op, Bool B, int I)                                                                                    // Boolean comparison between an integer variable and an integer constant
     {x();
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

    void bex (Ops Op, Bool B, Int I) {I.x(); bex(Op, B, I.i);}                                                          // Boolean comparison between two integer variables

    String bev (Ops Op, Bool B, int I)                                                                                  // Boolean comparison between an integer variable and an integer constant
     {final StringBuilder s = new StringBuilder();
      final String n = vn();
      switch(Op)
       {case eq -> s.append(n + " == "+I);
        case ne -> s.append(n + " != "+I);
        case le -> s.append(n + " <= "+I);
        case lt -> s.append(n + " <  "+I);
        case ge -> s.append(n + " >= "+I);
        case gt -> s.append(n + " >  "+I);
        default  -> stop("Op not implemented:", Op);
       }
      return B.vtrace(s);
     }

    String bev (Ops Op, Bool B, Int I)                                                                                  // Boolean comparison between two integer variables
     {final StringBuilder s = new StringBuilder();
      final String n = vn(), i = I.vn();
      switch(Op)
       {case eq -> s.append(n + " == "+i);
        case ne -> s.append(n + " != "+i);
        case le -> s.append(n + " <= "+i);
        case lt -> s.append(n + " <  "+i);
        case ge -> s.append(n + " >= "+i);
        case gt -> s.append(n + " >  "+i);
        default  -> stop("Op not implemented:", Op);
       }
      return B.vtrace(s);
     }

    Int dup () {return new Int(this);}                                                                                  // Duplicate an integer so that the duplicated version can be modified without modifying the original

    Bool valid ()                                                                                                       // Whether the integer is valid - these checks are not made in Verilog because it is assumed that of the memory traces match then the behavior of the Verilog is identical to that of the java and thus there is no need to test the validity of the integers
     {final Bool b = new Bool();
      new I() {void a() {b.i =  v; b.v = true;} int traces() {return 0;}};
      return b;
     }

    Bool notValid ()                                                                                                    // Whether the integer is invalid - these checks are not made in Verilog because it is assumed that of the memory traces match then the behavior of the Verilog is identical to that of the java and thus there is no need to test the validity of the integers
     {final Bool b = new Bool();
      new I() {void a() {b.i = !v; b.v = true;} int traces() {return 0;}};
      return b;
     }

    Int invalidate ()                                                                                                   // Invalidate the integer. The invalidation is done in such a way as to make the instruction sequences for java and Verilog match. Recall that that the Verilog integers do not carry a valid flag with them as this would be a waste of resources given that the algorithm is correct. The integers used in the java version do carry a valid flag to assist in validating the correctness of this implementation of the btree algorithm before handing it off to Verilog.
     {new I()
       {void   a() {ex(Ops.set, -1); v = false;}
        String v() {return ev(Ops.set, -1);}
       };
      return this;
     }

    Int copy (Int I)                                                                                                    // Copy the state of an integer without regard as to whether it is valid or not
     {new I()
       {void   a() {ex(Ops.set, I.i); v = I.v;}
        String v() {return ev(Ops.set, I);}
       };
      return this;
     }

    public String toString ()                                                                                           // Print the integer
     {final String u = "undefined_Int";
      if (name == null) return v ? ""+i       : u;
      else              return v ? name+"="+i : u+": "+name;
     }

    String   vn ()                                                                                                      // Verilog name of this variable
     {final FlowControl f = program().getFlowControlForInstructionBeingCompiled();
      final String n = suppressNamesInInstructions ? "" : name != null ? "/*"+name+"*/" : "";
      return pad("i[lastIntId+"+(id - f.intId)+"]"+n, 12);
     }

    Int     say () {final Int i = this; new I() {void a() {Test.say(i);} }; return this;}                               // Say the integer
    void jtrace () {jTrace(f("%8d i %8d = %8d\n",  program().currentPc, id, i));}                                       // Trace the integer operation

    Int ok (int Value)                                                                                                  // Check the integer
     {final Int got = this;
      new I()
       {void        a()
         {if (!got.v) stop("Invalid Int being tested at:", program().executing.instructionLocation());
          Test.ok(i, Value);
         }
        boolean trace() {return false;}                                                                                 // No need to test  under Verilog as long as all data accesses match
       };
      return this;
     }

    Int ok (Int Value)                                                                                                  // Test an Integer. The value expected and the value got must be valid during the java execution because the verilog execution deliberately removes this information on the basis that the java code is definitive and so if the verilog race matches the java trace the verilog code is working correctly. The purpose of the validity bit is to internally track whether the integer was ever set during program execution, it is not to convey application information. If an integer with an attached validity bit is required in application logic then Bint should be used.
     {final Int got = this;
      if (immediate() && !Value.v) stop("Invalid expected Int has been supplied for testing");
      new I()
       {void    a    ()
         {if (!got.v) stop("Invalid Int being tested at:", program().executing.instructionLocation());
           Test.ok(got.i(), Value.i());
         }
        boolean trace() {return false;}
       };
      return this;
     }
   }                                                                                                                    // Int

//D2 Boolean Integer                                                                                                    // An integer that can be specifically valid or invalid thus requiring an extra validity bit only for specified integers rather than all integers in the Verilog representationOperations on integer values

  final class Bint                                                                                                      // An integer that can be specified as valid or invalid
   {private final Bool b = new Bool(false);                                                                             // Whether the associated integer is valid or invalid
    private final Int  i = new Int();                                                                                   // The integer component
    Bint set (Int I) {b.set(); i.set(I); return this;}                                                                  // Set to a known value
    Bool   b ()      {return b;}                                                                                        // Return boolean component
    Int    i ()
     {b.Flip().stop("Requested int component from unset Bint");                                                         // Complain if there is no integer component to return
      return new Int(i);
     }

    Bool valid ()      {return b;}                                                                                      // Whether the boolean integer is valid
    Bool notValid ()   {return b.Flip();}                                                                               // Whether the boolean integer is invalid
    Bint invalidate () {b.clear(); return this;}                                                                        // Mark the integer as invalid after all

    Bint copy (Bint Source)                                                                                             // Copy a boolean integer
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

    Bint ok (boolean Value) {new I() {void a() {Test.ok(b.b(), Value);} boolean trace() {return false;}}; return this;} // Test the boolean value of the boolean integer
    Bint ok (int     Value) {new I() {void a() {Test.ok(i.i(), Value);} boolean trace() {return false;}}; return this;} // Test the integer value of the boolean integer
    Bint ok (Int Value) {new I() {void a() {Test.ok(i.i(), Value.i());} boolean trace() {return false;}}; return this;} // Test the integer value of the boolean integer

    void     stop (Object...O)                                                                                          // Conditionally print a message if false and stop
     {new If (this) {void Then() {new I() {void a() {Test.stop(O);} boolean trace() {return false;}};}};
     }
    void elseStop (Object...O)                                                                                          // Conditionally print a message if true and stop
     {new If (this) {void Then() {} void Else() {new I() {void a() {Test.stop(O);} boolean trace() {return false;}};}};
     }

    public String toString ()                                                                                           // Print the boolean integer
     {final StringBuilder s = new StringBuilder();
      new I()
       {void a(){if (b.b()) s.append("Bint("+i+")"); else s.append("Bint(invalid)");}
        boolean trace() {return false;}
       };
      return ""+s;
     }
   }

//D1 Memory                                                                                                             // Operations on memory divided into units

  static int ib ()      {return Integer.BYTES;}                                                                         // Number of bytes in an integer
  static int ib (int I) {return I * ib();}                                                                              // Number of bytes in a number of integers
  static Int ib (Int I) {return I.Mul(ib());}                                                                           // Number of bytes in a number of integers

  final class UnitMemory                                                                                                // Memory made of units
   {private final int id;                                                                                               // Unique identifier for this memory
    private int[]units;                                                                                                 // Bytes of main memory
    static int bitsPerUnit() {return Integer.SIZE;}                                                                     // Bits per memory unit

    UnitMemory (int Length)                                                                                             // Create and clear some memory
     {units = new int[Length];
      clear(new Int(0), Length);
      final Stack<UnitMemory> m = program().memories; id = m.size(); m.push(this);                                      // Give the memory a unique identifier and save it in the main program
     }

    String i () {return ""+id;}                                                                                         // Number of memory a string for use in writing verilog
    String n () {return "m_"+id;}                                                                                       // Name of memory

    String dumpVerilogMemoryInDecimalName() {return "dumpDecimal_"+id;}                                                 // Name of the verilog routine to dump this memeory in decimal

    private int getUnit (int I, char Type)                                                                              // Get the value of a byte
     {final int b = units[I];
      jTrace(f("%8d %c %8d = %8d  %s\n", program().currentPc, Type, I, b, n()));
      return b;                                                                                                         // Get the value of a byte
     }

    private int getUnit    (int I) {return getUnit(I, 'R');}                                                            // Get the value of a byte
    private int getBitUnit (int I) {return getUnit(I, 'r');}                                                            // Get the value of a byte from which to read a bit

    private void putUnit (int I, int J, char Type)                                                                      // Put a byte into memory
     {final int A = units[I];                                                                                           // Previous value
                    units[I] = J;                                                                                       // Set the value of a byte from an integer
      jTrace(f("%8d %c %8d %8d < %8d  %s\n", program().currentPc, Type, I, A, J, n()));
     }

    private void    putUnit (int I, int J) {putUnit(I, J, 'W');}                                                        // Put a byte into memory
    private void putBitUnit (int I, int J) {putUnit(I, J, 'w');}                                                        // Put a byte into memory after modifying a bit

    UnitMemory copy (UnitMemory SourceMemory, Int SourceOffset, Int TargetOffset, int Width)                            // Copy the specified memory
     {subStart("Program.UnitMemory.copy");
      new ForCount(new Int(Width))
       {void body(Int Index)
         {new I()
           {void   a() {putUnit(TargetOffset.i() + Index.i(), SourceMemory.getUnit(SourceOffset.i() + Index.i()));}
            String v()
             {vtraceInc(); vtraceInc();
              return "putMemory_"+i()+"("   + TargetOffset.vn() + "+" + Index.vn()+
                   ", getMemory_"+SourceMemory.i()+"("  + SourceOffset.vn() + "+" + Index.vn()+"));";
             }
            int traces() {return 2;}
           };
         }
       };
      subFinish();
      return this;
     }

    UnitMemory clear ()                                                                                                 // Clear memory
     {subStart("Program.UnitMemory.clear(I)");
      new ForCount(new Int(size()))
       {void  body(Int Index)
         {new I()
           {void   a() {putUnit(Index.i(), 0);}
            String v() {vtraceInc(); return "putMemory_"+i()+"("+Index.vn() +   ", 0);";}
           };
         }
       };
      subFinish();
      return this;
     }

    UnitMemory clear (Int Start, int Width)                                                                             // Clear memory
     {subStart("Program.UnitMemory.clear(II)");
      new ForCount (Start, Start.Add(Width))
       {void body(Int Index)
         {new I()
           {void   a() {putUnit(Index.i(), 0);}
            String v() {vtraceInc(); return "putMemory_"+i()+"("+Index.vn()+", 0);";}
            int traces() {return 1;}
           };
         }
       };
      subFinish();
      return this;
     }

    int size() {return units.length;}                                                                                   //N Size of memory

    Int getInt (Int I)                                                                                                  // Get the int at the indicated position
     {final Int r = new Int();
      new I()
       {void   a()   {r.ex(Int.Ops.set, getUnit(I.i()));}
        String v()   {vtraceInc(); return r.vtrace(new StringBuilder("getMemory_"+i()+"("+I.vn()+")"));}
        int traces() {return 2;}
       };
      return r;
     }

    int getInt (int I) {return getUnit(I);}                                                                             // Get the int at the indicated position

    Bool getBool (Int I, Int J)                                                                                         // Get the bit in the specified byte at the specified position within the byte
     {Bool r = new Bool();
      new I()
       {void   a() {r.ex(Bool.Ops.set, getBit(getBitUnit(I.i()), J.i()));}
        String v() {vtraceInc(); return r.vtrace(new StringBuilder("getMemoryBool_"+i()+"("+I.vn()+", "+J.vn()+")"));}
        int traces() {return 2;}
       };
      return r;
     }

    Bool getBool (Int I) {return getBool(I.Div(Integer.SIZE), I.Mod(Integer.SIZE));}                                    // Get the bit at the bit indexed location

    UnitMemory putInt (Int I, Int J)                                                                                    // Set the int at the indicated position relative to the start to the specified value
     {new I()
       {void   a() {putUnit(I.i(), J.i());}
        String v() {vtraceInc(); return "putMemory_"+i()+"("+I.vn()+", "+J.vn()+");";}
       };
      return this;
     }

    UnitMemory putBool (Int I, Int J, Bool K)                                                                           // Set the bit at the indicated position in the byte at the specified position to the specified value
     {new I()
       {void a()
         {final int p = I.i();
          final int b = getBitUnit(p);
          final int B = setBit(b, J.i(), K.b());
          putBitUnit(p, B);
         }
        String v()
         {final String i = I.vn(), j = J.vn(), k = K.vn();
          vtraceInc(); vtraceInc();
          return "putMemoryBool_"+i()+"("+i+", "+j+", "+k+");";
         }
        int traces() {return 2;}
       };
      return this;
     }

    UnitMemory putBool (Int I, Bool K) {putBool(I.Div(Integer.SIZE), I.Mod(Integer.SIZE), K); return this;}             // Set the bit at the bit indexed position

//D2 Memory references                                                                                                  // References to byte memory

    final class Ref                                                                                                     // Reference into memory
     {final Int   offset = new Int("memoryReferenceOffset");                                                            // Offset of this reference in memory
      final UnitMemory m = UnitMemory.this;
      Ref (int Offset) {offset.set(Offset);}                                                                            // Offset this ref
      Ref (Int Offset) {offset.set(Offset);}                                                                            // Offset this ref

      UnitMemory memory () {return UnitMemory.this;}
      Program       program () {return Program   .this;}

      Ref       copy (Ref Source, int Width){m.copy(Source.m, Source.offset, offset, Width); return this;}              // Copy the specified memory possibly from another byte memory
      Ref      clear (int Width)            {m.clear     (offset, Width);                    return this;}              // Clear memory by setting its bytes to zero
      Int    getInt  (Int I)                {return m.getInt (I.Add(offset));}                                          // Get the int at the indicated position
      Bool   getBool (Int I)                {return m.getBool(I.Add(offset.Mul(Integer.SIZE)));}                        // Get the bit at the bit indexed location
      Ref    putInt  (Int I, Int J)         {m.putInt (I.Add(offset), J);                    return this;}              // Set the int at the indicated position relative to the start to the specified value
      Ref    putBool (Int I,        Bool K) {m.putBool(I.Add(offset.Mul(Integer.SIZE)), K);  return this;}              // Set the bit at the bit indexed position
      int     getInt (int I)                {return m.getInt (I+offset.i());}                                           // Get an int immediately when debugging
      Int     getInt ()
       {final Int r = m.getInt(offset);                                                                                 // Get the referenced int
        return r;
       }
      Ref     putInt (Int J)                {m.putInt (offset, J);                           return this;}              // Put the referenced int

      boolean getBool (int I) {return getBit(unitMemory.getBitUnit(I / Integer.SIZE+offset.i()), I % Integer.SIZE);}    // Get the bit at the bit indexed location - debugging

      Ref step (int Width) {return new Ref(offset.Add(Width));}                                                         // Step up from an existing ref to make a new one - only while not executing

      public String toString ()                                                                                         // Print memory reference
       {final StringBuilder s = saySb("Ref: " , offset.i());
        return ""+s;
       }
     }

    public String toString ()                                                                                           // Print memory
     {final StringBuilder s = new StringBuilder();
      for (int i = 0, N = size(); i < N; i++) s.append(f("%4d %3d\n", i, units[i]));
      return ""+s;
     }

    String dumpAsDecimal()                                                                                              // Dump memory in decimal format
     {final int N = 10;
      final StringBuilder s = new StringBuilder();
      s.append(f("Memory %d\n", id));
      s.append("         ");
      for (int i = 0; i < N; i++)                s.append(f("%4d ", i));
      s.append("\n");

      for (int i = 0; i < size(); i++)
       {if (i % N == 0)                          s.append(f("%08d ", i));

        final int b = units[i];
        if (b != 0) s.append(f("%4d ", b)); else s.append("     ");
        if ((i + 1) % N == 0)                    s.append("\n");
       }
      if (size() % N != 0)                       s.append("\n");
      return ""+s;
     }

    String save()                                                                                                       // Save memory to a string representation
     {final ByteBuffer b = ByteBuffer.allocate(ib(size()));
      for (int i : units) b.putInt(i);
      return Base64.getEncoder().encodeToString(b.array());
     }

    void reload(String s)                                                                                               // Reload memory from a saved string representation
     {final byte[]b = Base64.getDecoder().decode(s);
      if (b.length != ib(size()))
       {stop("Mismatched reloaded memory length in bytes for memory:", id, "expected:", b.length, "got:", ib(size()));
       }
      final ByteBuffer B = ByteBuffer.wrap(b);
      for (int i = 0; i < size(); i++) units[i] = B.getInt();
     }
   }

  interface Locatable {Bint getLocation();}                                                                             // The location of an object in memory

  String dumpMemory () {return program().unitMemory.dumpAsDecimal();}                                                   // Dump memory in decimal format

  String saveMemories ()                                                                                                // Save all the memories to an array of strings
   {final StringJoiner j = new StringJoiner("\", \"");
    for (UnitMemory m : memories) j.add(m.save());
    return "{\""+j+"\"}";
   }

  void reloadMemories (String[]Dump)                                                                                    // Reload saved memories
   {if (Dump.length != memories.size())                                                                                 // Check number of memories match
     {stop("Number of memories supplied and present differ:", Dump.length, memories.size());
     }
    for (int i = 0; i < Dump.length; ++i) memories.elementAt(i).reload(Dump[i]);                                        // Reload each memory
   }

//D1 Testing                                                                                                            // Methods useful during testing of byte machine programs

  void check (StringBuilder G, String E)                                                                                // Test the supplied content against the specified string, then clear the output area ready for the next report
   {new I() {void a() {Test.ok(nws(G), nws(E));} int traces() {return 0;}};
   }

  void Check (StringBuilder G, String E)                                                                                // Test the supplied content against the specified string, print the actual output area contents and stop
   {new I() {void a() {if (!Test.ok(nws(G), nws(E))) stop(G, traceBack);} int traces() {return 0;}};
   }

  String verilogTestFolder () {return fp(verilogFolder,       currentTestNameSuffix());}                                // Folder for this test using Verilog
  String verilogTraceFile ()  {return fn(verilogTestFolder(), verilogTraceFile);}                                       // Verilog trace file
  String    javaTraceFile ()  {return fn(verilogTestFolder(), javaTraceFile);}                                          // Java trace file
  String VerilogCodeFile ()   {return fe(verilogTestFolder(), currentTestNameSuffix(), verilogSuffix);}                 // Verilog code file

  static boolean rtg (int i)  {return testGroup == null || testGroup.equals(""+i);}                                     // Whether to run the indicated test group

//D1 Machine Code                                                                                                       // Generate machine code instructions to implement the program

//D2 Instruction                                                                                                        // An instruction represents code to be executed by a process in a single clock cycle == process step

  abstract class I                                                                                                      // Instructions implement the action of a program
   {final int instructionNumber = program().code.size();                                                                // The number of this instruction
    final String      traceBack = supressTraceComments ?  null : traceBack();                                           // Line at which this instruction was created - suppressible because it imposes a lot of extra processing
    final String       traceSub = subsTrace;                                                                            // Sub during which this instruction was created
    final int         nextIntId = program().nextIntId;                                                                  // Record current position among the integers
    final int        nextBoolId = program().nextBoolId;                                                                 // Record current position among the booleans

    enum Jump {no, might, will};                                                                                        // Whether the instruction will jump
    final Jump jump;                                                                                                    // The instruction might cause a jump

    I (Jump Jump)                                                                                                       // Add this instruction to the code for the process
     {ai();                                                                                                             // Prevent addition of new instructions and allocations while compiling this instruction
      subInc();                                                                                                         // Count the number of instructions associated with each method
      jump = Jump;                                                                                                      // Ability to jump
      if (immediate())                                                                                                  // Execute instruction immediately via interpretation if in immediate execution mode
       {program().executing = this;                                                                                     // Show that we are executing an instruction
        program().jtrace = 0;
        a();
        if (trace() && program().jtrace != traces())                                                                    // Check traces written if tracing
         {stop("Wrong number of java traces generated, got: ", program().jtrace,
               "expected:", traces(), "at:", instructionLocation());
         }
        program().executing = null;                                                                                     // Show that we are no longer executing an instruction
       }
      else  {program().code.push(this);}                                                                                // Save instruction in program for later execution if in delayed == non immediate execution mode
     }

    I () {this(I.Jump.no);}                                                                                             // Add this instruction to the process's code assuming it will not jump

//D3 Overrides                                                                                                          // Methods that modify the behaviour of an instruction

    abstract void a ();                                                                                                 // The action to be performed by the instruction
    String        v () {return instructionLocationAsComment();};                                                        // Location of missing verilog instruction
    int      traces () {return 1;}                                                                                      // Number of trace records expected
    boolean   trace () {return true;}                                                                                   // Enable tracing

    String instructionLocation () {return traceBack != null ? traceBack : traceSub  != null ? traceSub : "";}           // Trace the location at which the instruction was generated
    String instructionLocationAsComment ()                                                                              // Trace the location at which the instruction was generated as a comment
     {if (!supressTraceComments)
       {if (traceBack != null) return "/*" + traceBack.replaceAll("\\n", ", ") + "*/";                                  // Appending trace comments makes the code easier to debug but inhibits code compression
        if (traceSub  != null) return "/*" + traceSub .replaceAll("\\n", ", ") + "*/";
       }
      return "";
     }

//D3 Verilog                                                                                                            // Generate verilog for an instruction

    void matchInstructions ()                                                                                           // Find base instructions as the instruction with the lowest label among a set of instructions with the same interior verilog code
     {final TreeMap<String,Stack<I>> b = matchingInstructions;                                                          // Shorten the name
      final String                   v = interiorVerilog();                                                             // Generate verilog code as key
      final Stack<I>                 m = b.containsKey(v) ? b.get(v) : new Stack<>();                                   // Matching instructions
      m.push(this);                                                                                                     // Add current instruction to matching instructions
      b.put(v, m);                                                                                                      // Record this set of matching instructions
     }

    String interiorVerilog ()                                                                                           // Generate the interior verilog code for an instruction
     {program().vtrace = 0;                                                                                             // Count number of trace calls made in instruction
      final StringBuilder s = new StringBuilder(v());                                                                   // Generated code
      if (jump == I.Jump.might) s.append(" else");                                                                      // Conditionally increment program counter to allow jumps to occur
      if (jump != I.Jump.will)  s.append(" pc <= pc + 1;");

      if (trace())
       {if (program().vtrace != traces())                                                                               // Complain if the wrong number of vtrace calls were generated
         {stop("Wrong number of calls to vtrace, got:", program().vtrace,
               "expected:", traces(), "at:", instructionLocation());
         }
        if (program().vtrace == 0)                                                                                      // Write current location to verilog trace log if no trace was supplied
         {s.append(vTrace("%8d Location: %s", "pc", "\""+instructionLocationAsComment()+"\""));
         }
       }
      return ""+s;                                                                                                      // Generated code
     }

    StringBuilder verilogCodeForOneInstruction(StringJoiner Labels, String Verilog)                                     // Verilog code for one instruction
     {final StringBuilder s = new StringBuilder();
      s.append(f("        %s : begin %s", pad(""+Labels, 20), pad(Verilog, 20)));                                       // Instruction numbers followed by code
      s.append(" c <= c + 1;");                                                                                         // Count instructions executed
      s.append(" end");
      s.append(instructionLocationAsComment());                                                                         // Trace java program location that generated the first instance of the instruction so that the verilog code can be tied back to the java code
      s.append("\n");
      return s;
     }

    String generateVerilog ()                                                                                           // Generate verilog code for an instruction
     {final String        v = interiorVerilog();                                                                        // Generate verilog code as key
      final Stack<I>      m = matchingInstructions.get(v);                                                              // Matching instructions
      final StringJoiner  l = new StringJoiner(", ");                                                                   // Labels
      final StringBuilder s = new StringBuilder();                                                                      // Generated code

      if (program().compressInstructions)                                                                               // Compress out identical instructions
       {if (this == m.firstElement())                                                                                   // Generate code for first instance of this instruction
         {for(I i : m) l.add(f("%4d", i.instructionNumber));                                                            // Collect labels for matching instructions
          s.append(verilogCodeForOneInstruction(l, v));                                                                 // Verilog code for base instruction with labels for all matching instructions
         }
       }
      else
       {l.add(f("%4d", instructionNumber));                                                                             // Instruction number
        s.append(verilogCodeForOneInstruction(l, v));                                                                   // Verilog code for instruction with its label
       }
      return ""+s;                                                                                                         // Generated code
     }
   }

  final class Label                                                                                                     // Label jump targets in the program
   {int offset;                                                                                                         // The instruction location to which this label applies
    Label ()    {set(); program().labels.push(this);}                                                                   // A label assigned to an instruction location
    void set () {offset = program().code.size();}                                                                       // Reassign the label to an instruction
   }

  void appendJavaTrace(String Message) {appendFile(javaTraceFile(), Message);}                                          // Append to the java trace file
  void jTrace (String Message) {jtraceInc(); if (program().executing.trace()) appendJavaTrace(Message);}                // Trace a java instruction by writing a message to the java trace file unless the instruction has suppressed tracing

  String vTrace (String Format, String...Message)                                                                       // Generate verilog code to write a message to the verilog trace log
   {vtraceInc();
    if (!program().compiling.trace()) return "";                                                                        // Suppress tracing for this instruction
    final StringBuilder s = new StringBuilder();
    //s.append(" traceFile = $fopen(\""+verilogTraceFile+"\", \"a\"); ");
    s.append("$fwrite(traceFile, \""+Format+"\"");
    for(int i = 0; i < Message.length; ++i) s.append(", "+Message[i]);
    s.append("); $fwrite(traceFile, \"\\n\");");
    //s.append(" $fclose(traceFile);");
    return ""+s;
   }

  void initializeJavaMemory()                                                                                           // Initialize java memory
   {for(UnitMemory m : memories) for (int i = 0, N = m.size(); i < N; ++i) m.units[i] = 0;                              // Clear all of memeory to zero
   }

  void initializeJavaVars()                                                                                             // Initialize java variables so that they start with a known value despite being invalid because the valid bit is not tracked in the verilog version
   {for (Int  i : ints)  {i.i = 0;     i.v = false;}
    for (Bool b : bools) {b.i = false; b.v = false;}
   }

  void dumpJavaMemories () {for(UnitMemory m : memories) appendJavaTrace(m.dumpAsDecimal());}                           // Dump all the memories

  void dumpJavaVars ()                                                                                                  // Dump all memories and variables to the java trace file
   {final StringBuilder s = new StringBuilder();
    for (Int  i  : ints)
     {s.append(f("Int  %8d == %8d", i.id, i.i));
      if (i.name != null) s.append(" "+i.name);
      s.append('\n');
     }
    for (Bool  b : bools)
     {s.append(f("Bool %8d == %8d", b.id, b.i ? 1 : 0));
      if (b.name != null) s.append(" "+b.name);
      s.append('\n');
     }
    appendJavaTrace(""+s);
   }

  void dumpJava ()                                                                                                      // Dump all memories and variables to the java trace file
   {dumpJavaMemories();
    dumpJavaVars();
   }

  void execute ()                                                                                                       // Execute the current code
   {if (immediate()) return;                                                                                            // The code has already been executed interpretively

    if (codeSize() == 0) stop("No code to execute"); else say(f("            Code size: %,7d", codeSize()));            // Code size check
    deleteFile(javaTraceFile());                                                                                        // Clear Java trace file
    currentPc   = pc = 0;                                                                                               // Reset program counter to start of program
    final int N = codeSize();                                                                                           // Number of instructions
          int c = 0;                                                                                                    // Number of instructions executed

    initializeJavaMemory();                                                                                             // Initialize memory
    initializeJavaVars();                                                                                               // Initialize variables

    for(; c < maxSteps && pc >= 0 && pc < N; ++c)                                                                       // Execute each instruction within a specified number of steps
     {final I i = code.elementAt(pc);
      try
       {currentPc = pc++;                                                                                               // This is the anticipated next instruction, but the instruction can set it to effect a branch in execution flow
        executing = i;                                                                                                  // Currently executing instruction
        jtrace = 0;
        i.a();
        if (i.trace())                                                                                                  // Check tracing
         {if (jtrace != i.traces())                                                                                     // Wrong number of trace calls
           {stop("Wrong number of java traces generated, got:", jtrace, "expected:", i.traces(),
                 "at:", i.instructionLocation());
           }
          if (jtrace == 0) jTrace(f("%8d Location: %s\n", currentPc, i.instructionLocationAsComment()));                // Append location to java trace log as no tracing was performed
         }

        executing = null;                                                                                               // Show no instruction currently being executed
       }
      catch(Exception e)
       {if (executing == null) stop("Exception:", e, "while executing:", traceBack(e));
        else stop("Exception:", e, "\nin instruction:", executing.traceBack, "\nwhile executing:", traceBack(e));
       }
     }

    if (c >= maxSteps) stop("Out of steps after step:", c);                                                             // Show abnormal termination reason
    dumpJavaMemories();                                                                                                 // Dump memory at the end of the run so it can be compared the corresponding verilog memeory

    if (generateVerilog)                                                                                                // Run verilog
     {generateVerilog();                                                                                                // Generate corresponding Verilog code and run it
      if (runVerilog)                                                                                                   // Run verilog
       {deleteFile(verilogTraceFile());                                                                                 // Clear Verilog trace file
        final ExecCommand x =                                                                                           // Return code 124 shows that the program run was timed out
          new ExecCommand(f("cd %s; rm -f x; "+                                                                         // Execute Verilog code
                            "iverilog -O2 -g2012 -o x %s.v && "+
//                          "timeout 1m ./x",
                            "./x",
                            verilogTestFolder(), currentTestNameSuffix()));
        say(""+x.out);

        ok(readFileAsString(verilogTraceFile()).equals(readFileAsString(javaTraceFile())));                             // Compare corresponding java and Verilog trace files -  says failed if it fails and provides a traceback
       }
     }
   }

  void variableNotSet (String Type, String Name)                                                                        // Variable not yet set message
   {final I i = program().executing;
    final String m = (Name != null ? '"'+Name+'"'+", " : "") + "has not been set yet";
    if (i != null) stop(Type, m, i.traceBack, "====");                                                                  // With traceback on failing instruction if possibe
    else           stop(Type, m);                                                                                       // No traceback available
   }

  void dumpProgramState (String Location)                                                                               // Dump memory
   {new I()
     {void    a()     {appendJavaTrace(Location); dumpJava();}
      String  v()     {return "$fwrite(traceFile, \""+Location+"\");"+dumpVerilog();}
      boolean trace() {return false;}
     };
   }

  <A, B> void ok (Supplier<A> a, B b)                                                                                   // Test a result of delayed execution against a known result while the program is still executing
   {new I()
     {void a() {if (!ok(a.get(), b)) if (traceBack != null) say("====\n", traceBack);}
      boolean trace() {return false;}
     };
   }

//D2 Instruction counts                                                                                                 // Count the number of instructions in each subroutine minus the instructions supplied by called subroutines

  int codeSize () {return program().code.size();}                                                                       // Number of instructions in current program

  static void subStart (String Name)
   {subs.push(Name);
    subsTrace = joinStrings(subs, "\n");                                                                                // Trace of active subs
    if (!instructionCounts.containsKey(Name)) instructionCounts.put(Name, 0);                                           // Initialize instruction count for this subroutine
   }

  static void subInc ()                                                                                                 // Increment the number of instructions associated with a method
   {if (subs.size() > 0)
     {final String n = subs.lastElement();
      instructionCounts.put(n, instructionCounts.get(n) + 1);
     }
   }

  static void subFinish ()                                                                                              // Finish a subroutine definition
   {if (subs.size() == 0) stop("No matching subStart()");
    subs.pop();
   }

  static String subPrint ()                                                                                             // Print instruction counts
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

//D1 Verilog                                                                                                            // Generate Verilog

  void generateVerilog ()                                                                                               // Generate and execute the corresponding Verilog
   {final String       name = currentTestNameSuffix();                                                                  // Name of program
    final String  traceFile = verilogTraceFile;                                                                         // Trace file name relative to Verilog code
    final String   codeFile = VerilogCodeFile();                                                                        // Code file
    final int    sizeMemory = unitMemory != null ? unitMemory.size() : 0;                                               // Size of memory
    final int  numberOfInts = nextIntId;                                                                                // Number of integers needed
    final int numberOfBools = nextBoolId;                                                                               // Number of bools needed

    try
     (final PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(codeFile),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING)))
     {//final StringBuilder   s = new StringBuilder();                                                                  // Verilog
    /*Module*/out.write(substitute("""
module {name};                                                                                                          // Bit machine to support current test
""", "name", name));

      for(UnitMemory m : memories)                                                                                      // Each memory attached to this program
       {/*Memory*/out.write(substitute("""
  parameter  MEMORY_{memoryId}    = {memory_size};                                                                      // Amount of memory
  integer   {memoryName}[MEMORY_{memoryId}:0];                                                                          // Declare byte memory
""", "memoryId", m.i(), "memoryName", m.n(), "memory_size", ""+m.size()));
       }

      if (true)                                                                                                         // State machine to sequence the initialization of memories
       {int state = 0;
        out.write("  typedef enum integer {\n");                                                                        // State machine to initialize each memory and the variables used by the main program
        for(UnitMemory m : memories)  out.write("    state_clearMemory_"+m.i()+" = "+(state++)+",\n");                  // State to clear each memory                                                    // Each memory attached to this program
        out.write("    state_clearInts   = "+(state++)+",\n");                                                          // State for clearing integers
        out.write("    state_clearBool   = "+(state++)+",\n");                                                          // States for clearing bools
        out.write("    state_execute     = "+(state++)+"\n");                                                           // States for executing code
        out.write("   } State;\n");
       }

      /*Execution State Variables*/out.write(substitute("""
  parameter    INT_VARS  = {numberOfInts};                                                                              // Number of integer variables
  parameter    BOOL_VARS = {numberOfBools};                                                                             // Number of boolean variables
  reg          clock;                                                                                                   // Clock for chip
  reg          reset;                                                                                                   // Reset for chip
  integer          c;                                                                                                   // Count of instructions executed
  integer         pc;                                                                                                   // Program counter for stepping through user code
  integer     lastPc;                                                                                                   // The instruction which started the latest flow of control block
  integer      index;                                                                                                   // Index for clearing memory
  integer  lastIntId;                                                                                                   // Base for integer references
  integer lastBoolId;                                                                                                   // Base for boolean references
  integer  traceFile;                                                                                                   // File to which trace messages will be written
  integer          i[INT_VARS:0];                                                                                       // Integers
  reg              b[BOOL_VARS:0];                                                                                      // Booleans

""", "numberOfInts", ""+numberOfInts, "numberOfBools", ""+numberOfBools));

      /*Reset*/out.write("""

  State state;                                                                                                          // Current state of machine

  always @(posedge clock) begin
    if (reset) begin                                                                                                    // Reset
""");
      if (memories.size() > 0) /*Reset memory*/out.write(substitute("""
      state = state_clearMemory_{start};
""", "start", memories.firstElement().i()));

      else /*No memory*/out.write("""
      state = state_clearInts;
""");

        /*Initialize*/out.write(substitute("""
      index = 0;
      c     = 0;
      pc    = 0;
      lastBoolId = 0;
      lastIntId  = 0;

      traceFile = $fopen("{traceFile}", "w");                                                                           // Clear the trace file
      if (traceFile == 0) begin
        $display("ERROR: Could not open file '{traceFile}' for writing.");
        $finish;
      end
      traceFile = $fopen("{traceFile}", "a");                                                                           // Start appending to the emptied trace file
      if (traceFile == 0) begin
        $display("ERROR: Could not open file '{traceFile}' for appending.");
        $finish;
      end
    end
    else begin                                                                                                          // Initialize bit machine then execute user code
      case (state)
""", "traceFile", traceFile));

        for(UnitMemory m : memories)                                                                                    // Clear each memory one after the other
         {/*Memory Clear*/out.write(substitute("""
        state_clearMemory_{memoryId}: clearMemory_{memoryId}();
""", "memoryId", m.i()));
         }

        /*Execute*/out.write("""
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
  end

  task automatic clearInts;                                                                                             // Clear integers
    begin
      i[index] = 0;
      index = index + 1;
      if (index >= INT_VARS) begin state = state_clearBool; index = 0; end
    end
  endtask

  task automatic clearBool;                                                                                             // Clear bools
    begin
      b[index] = 0;
      index = index + 1;
      if (index >= BOOL_VARS) state = state_execute;
    end
  endtask

""");

      /*traceBool*/out.write(traceVerilogVariable("traceBool",  "lastBoolId", "b", traceFile));                         // Memory and variable tracind
      /*traceInt*/ out.write(traceVerilogVariable("traceInt",   "lastIntId",  "i", traceFile));
      /*dumpVars*/ out.write(dumpVerilogVariables());

      for(int i = 0; i < memories.size(); ++i)                                                                          // Actions for each memory
       {final UnitMemory m = memories.elementAt(i);
        out.write(traceVerilogMemoryPutInt    (m));
        out.write(traceVerilogMemoryGetInt    (m));
        out.write(traceVerilogMemoryPutBool(m));
        out.write(traceVerilogMemoryGetBool(m));
        out.write(              clearMemory(m, i < memories.size()-1 ?
         "state_clearMemory_"+memories.elementAt(i+1).i() :
         "state_clearInts"));
        out.write(   dumpVerilogMemoryInDecimal(m));
       }

      for(String m : extraVerilogMethods) out.write(m);                                                                 // Incorporate extra Verilog methods required to support generated instructions

      /*Execute*/out.write("""
  task automatic execute;                                                                                               // Execute actual code
    begin
      case(pc)
""");

      matchingInstructions.clear();                                                                                     // New base instructions
      for(I i : code) {program().compiling = i;           program().compiling.matchInstructions();}                     // Find the base instructions
      for(I i : code) {program().compiling = i; out.write(program().compiling.generateVerilog());}                      // Compile each instruction to Verilog
      if (true)                                                                                                         // Instruction reduction statistics
       {final int m = matchingInstructions.size(), c = code.size(), p = 100*(c-m)/c;
        say(f("Instruction reduction to: %4d, percent: %4d", m, p));
       }
      matchingInstructions.clear();                                                                                     // Release storage occupied by base instructions

      /* Execute default*/out.write("""
        default: begin
""");
      for(UnitMemory m: memories) out.write("        "+m.dumpVerilogMemoryInDecimalName()+"();");                       // Dump memory at end if used

    /*Execute end*/out.write("""
          $fclose(traceFile);
          $finish(0);
        end
      endcase
      pc = pc + 1;
    end
  endtask
endmodule
""");

     }
    catch(Exception e) {stop(fullTraceBack(e));}
   }

  String clearMemory(UnitMemory M, String Next)                                                                         // Verilog procedure to clear a memory
   {return substitute("""
  task automatic clearMemory_{memoryId};                                                                                // Clear memory element by element
    begin
      for(index = 0; index < MEMORY_{memoryId}; index = index + 1) {memoryName}[index] = 0;
      state = {Next};
    end
  endtask
""", "memoryId", M.i(), "memoryName", M.n(), "Next", Next);
   }

  String traceVerilogVariable(String Procedure, String Last, String Type, String TraceFile)                             // Verilog procedure to trace a variable
   {final String display = "$fdisplay(traceFile, \"%8d "+Type+" %8d = %8d\", pc, ("+Last+"+Id), Value);";               // Trace line to be written out. Id and Value are parameters of the generated function

    return f("""
  function automatic integer %s(input integer Id, input integer Value);                                                 // Trace variable
    begin
      %s = Value;                                                                                                       // Return value
//      traceFile = $fopen("%s", "a");                                                                                  // Open named trace file
      %s
//      $fclose(traceFile);
    end
  endfunction
""",
Procedure, Procedure, TraceFile, display);
   }

  void defineArrayViaVerilogFunction(String Name, int[]Array)                                                           // Define a verilog function from an array
   {final StringBuilder s = new StringBuilder("function automatic integer "+Name+"(input integer i);");
    s.append("""
  begin
    case (i)
""");
    for (int i = 0; i < Array.length; ++i)
     {final int v = Array[i];
      if (v != 0) s.append(f("      %4d: %s = %4d;\n", i, Name, v));                                                    // The java proves the code works so we can collapse zeros into default
     }
    s.append(f("""
      default: %s = 0;
    endcase
  end
endfunction
""", Name));
    program().extraVerilogMethods.add(""+s);
   }

  String dumpVerilogMemoryInDecimal (UnitMemory M)                                                                      // Dump memory in decimal
   {return substitute("""
  task {dumpVerilogMemoryInDecimalName};
    integer i;
    integer I;
    parameter integer N = 10;
    begin
      //traceFile = $fopen("{traceFile}", "a");

      $fwrite(traceFile, "Memory %s\\n", "{memoryId}");

      $fwrite(traceFile, "         ");
      for (i = 0; i < N; i = i + 1)   $fwrite(traceFile, "%4d ", i);
                                      $fwrite(traceFile, "\\n");

      for (i = 0; i < MEMORY_{memoryId}; i = i + 1)
      begin
        if (i % N == 0)               $fwrite(traceFile, "%08d ", i);

        I = {memoryName}[i];

        if (I != 0)                   $fwrite(traceFile, "%4d ", I);
        else                          $fwrite(traceFile, "     ");

        if ((i + 1) % N == 0)         $fwrite(traceFile, "\\n");
      end

      if (MEMORY_{memoryId} % N != 0) $fwrite(traceFile, "\\n");

      //$fclose(traceFile);
    end
  endtask
""", "traceFile", verilogTraceFile, "memoryId", M.i(), "memoryName", M.n(),
"dumpVerilogMemoryInDecimalName", M.dumpVerilogMemoryInDecimalName());
  }

  String dumpVerilogVariablesName () {return "dumpVerilogVariables";}                                                   // Name of the verilog method to dump all the vars to the trace file
  String dumpVerilogVariables ()                                                                                        // Dump the value of an integer to the verilog trace file
   {final StringBuilder s = new StringBuilder();
    s.append(substitute("""
  task automatic {name} ();
    begin
""", "name", dumpVerilogVariablesName()));

    for(Int i : ints)
     {if (i.name != null) s.append(substitute("""
      $fdisplay(traceFile, "Int  %8d == %8d {name}", {id}, i[{id}]);
""", "name", i.name, "id", ""+i.id));
      else s.append(substitute("""
      $fdisplay(traceFile, "Int  %8d == %8d", {id}, i[{id}]);
""", "id", ""+i.id));
     }

    for(Bool b : bools)
     {if (b.name != null) s.append(substitute("""
      $fdisplay(traceFile, "Bool %8d == %8d {name}", {id}, b[{id}]);
""", "name", b.name, "id", ""+b.id));
      else s.append(substitute("""
      $fdisplay(traceFile, "Bool %8d == %8d", {id}, b[{id}]);
""", "id", ""+b.id));
     }

    s.append("""
    end
  endtask
""");
    return ""+s;
   }

  String traceVerilogMemoryGetInt (UnitMemory M)                                                                        // Trace byte read from memory
   {vtraceInc();
    return substitute("""
  function automatic integer getMemory_{memoryId} (input integer Addr);
    begin
      getMemory_{memoryId} = {memoryName}[Addr];

      //traceFile = $fopen("{traceFile}", "a");
      $fdisplay(traceFile, "%8d R %8d = %8d  %s", pc, Addr, {memoryName}[Addr], "{memoryName}");
      //$fclose(traceFile);
    end
  endfunction
""", "traceFile", verilogTraceFile, "memoryId", M.i(), "memoryName", M.n());
  }

  String traceVerilogMemoryGetBool (UnitMemory M)                                                                       // Trace bool read from memory
   {vtraceInc();
    return substitute("""
  function automatic reg getMemoryBool_{memoryId} (input integer Addr, input integer Bit);
    integer f;
    begin
      getMemoryBool_{memoryId} = {memoryName}[Addr][Bit];

      //traceFile = $fopen("{traceFile}", "a");
      $fdisplay(traceFile, "%8d r %8d = %8d  %s", pc, Addr, {memoryName}[Addr], "{memoryName}");
      //$fclose(traceFile);
    end
  endfunction
""", "traceFile", verilogTraceFile, "memoryId", M.i(), "memoryName", M.n());
  }

  String traceVerilogMemoryPutInt (UnitMemory M)                                                                        // Trace byte written to memory
   {vtraceInc();
    return substitute("""
  task automatic putMemory_{memoryId} (input integer Addr, input integer Value);
    integer f;
    integer a;
    begin
      a = {memoryName}[Addr];
          {memoryName}[Addr] = Value;

      //traceFile = $fopen("{traceFile}", "a");
      $fdisplay(traceFile, "%8d W %8d %8d < %8d  %s", pc, Addr, a, Value, "{memoryName}");
      //$fclose(traceFile);
    end
  endtask
""", "traceFile", verilogTraceFile, "memoryId", M.i(), "memoryName", M.n());
  }

  String traceVerilogMemoryPutBool (UnitMemory M)                                                                       // Trace bit written to memory
   {vtraceInc();
    return substitute("""
  task automatic putMemoryBool_{memoryId} (input integer Addr, input integer Bit, input integer Value);
    integer f;
    integer a;
    integer b;
    begin
      a = {memoryName}[Addr];
          {memoryName}[Addr][Bit] = Value[0];
      b = {memoryName}[Addr];

      //traceFile = $fopen("{traceFile}", "a");
      $fdisplay(traceFile, "%8d r %8d = %8d  %s",     pc, Addr, a,    "{memoryName}");
      $fdisplay(traceFile, "%8d w %8d %8d < %8d  %s", pc, Addr, a, b, "{memoryName}");
      //$fclose(traceFile);
    end
  endtask
""", "traceFile", verilogTraceFile, "memoryId", M.i(), "memoryName", M.n());
  }

  String dumpVerilog ()
   {final StringBuilder s = new StringBuilder();
    for(UnitMemory m : memories) s.append(m.dumpVerilogMemoryInDecimalName()+"(); ");
    s.append(dumpVerilogVariablesName()+"();");
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
        i.ok(5);
        i.valid().ok(true);
       }
     };
    P.execute();
   }

  static void test_programming()
   {          test_programming(true);
              test_programming(false);
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
            new I()
             {void   a() {s.append(f("%2d  %2d\n", a.i(), b.i()));}
              int traces() {return 0;}
             };
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

  static void test_fibonacci(boolean Ex)
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
            new I() {void a() {s.append(""+c+" ");} int traces() {return 0;}};
            Continue.set();
           }
         };
        Check(s, "c=1 c=2 c=3 c=5 c=8 c=13 c=21 c=34 c=55 c=89");
        execute();
       }
     };
   }

  static void test_fibonacci()
   {          test_fibonacci(true);
              test_fibonacci(false);
   }

  static void test_mod(Boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Int  a = new Int ("a");
        final Bool b = new Bool("b");
        final Int  c = new Int ("c").set(0);
        final Int  N = new Int ("N").set(4);
        final StringBuilder s = new StringBuilder();
        new For(N)
         {void body(Int Index, Bool Continue)
           {a.set(Index.Inc()).mod(2);
            new If (b.set(a.ne(0)).flip())
             {void Then() {c.dec();}
              void Else() {c.inc(); c.inc();}
             };
            new I() {void a() {s.append(""+c+" ");} int traces() {return 0;}};
            Continue.set();
           }
         };
        check(s, "c=2 c=1 c=3 c=2");
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
              a.ok(0); new I() {void a() {s.append(a+" ");} int traces() {return 0;}};
        a.inc().ok(1); new I() {void a() {s.append(a+" ");} int traces() {return 0;}};
        a.inc().ok(2); new I() {void a() {s.append(a+" ");} int traces() {return 0;}};
        Check(s, "0 1 2");
        execute();
       }
     };
    return P;
   }

  static void test_incremental()
   {sayCurrentTestName();
              test_incremental(true);
              test_incremental(false);
   }

//  static void test_bits(boolean Ex)
//   {sayCurrentTestName();
//    final Program P = new Program(new Build().immediate(Ex))
//     {void code()
//       {new For(new Int(2))
//         {void body(Int Index, Bool Continue)
//           {final Int a = new Int(0);
//            a.set(0);
//            a.bset(new Int(0))                .ok(1);
//            a.bset(new Int(1))                .ok(3);
//            a.bset(new Int(2))                .ok(7);
//            a.bclr(new Int(0))                .ok(6);
//            a.bclr(new Int(1))                .ok(4);
//            a.bclr(new Int(2))                .ok(0);
//            a.bset(new Int(3), new Bool(true)).ok(8);
//            final Bool b = a.bget(new Int(2)) .ok(false);
//            final Bool c = a.bget(new Int(3)) .ok(true);
//            Continue.set();
//           }
//         };
//       }
//     };
//    P.execute();
//    P.testVerilog(Ex);
//   }
//
//  static void test_bits()
//   {          test_bits(true);
//              test_bits(false);
//   }

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

  static void test_variables(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex).memory(3))
     {void code()
       {dumpProgramState("AAAA");
        final Int  i = new Int ("i");
        final Bool b = new Bool("b");
        dumpProgramState("BBBB");
        i.set(1);
        b.set(true);
         dumpProgramState("CCCC");
        i.set(2);
        b.set(false);
        dumpProgramState("DDDD");
        execute();
       }
     };
   }

  static void test_variables()
   {test_variables(true);
    test_variables(false);
   }

  static void test_memory(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex).memory(2))
     {void code()
       {final UnitMemory m = unitMemory;
        new For(2)
         {void body(Int Index, Bool Continue)
           {m.putInt(new Int(0), new Int(1));
            m.putInt(new Int(1), new Int(2));
            m.getInt(new Int(0)).ok(1);
            m.getInt(new Int(1)).ok(2);
            dumpProgramState("AAAA");
            m.getBool(new Int(1), new Int(0)).ok(false);
            m.getBool(new Int(1), new Int(1)).ok(true );
            m.getBool(new Int(1), new Int(2)).ok(false);
            m.putBool(new Int(1), new Int(0), new Bool(true));
            m.getInt (new Int(1)).            ok(3);
            dumpProgramState("BBBB");
            m.putBool(new Int(32), new Bool(false));
            m.getBool(new Int(32)).ok(false);
            m.getBool(new Int(33)).ok(true );
            m.getBool(new Int(34)).ok(false);
            dumpProgramState("CCCC");
            m.putBool(new Int(1), new Int(9), new Bool(true));
            m.getBool(new Int(1), new Int(9)).ok(true);
           }
         };
       }
     };
    P.execute();
//  ok(P.unitMemory.getBool(32), false);
//  ok(P.unitMemory.getBool(33), true);
   }

  static void test_memory()
   {test_memory(true);
    test_memory(false);
   }

  static void test_memoryNegative(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex).memory(8))
     {void code()
       {final UnitMemory m = unitMemory;
        new For(2)
         {void body(Int Index, Bool Continue)
           {m.putInt(new Int(0), new Int(-2));
            m.putInt(new Int(4), new Int(-3));
            m.getInt(new Int(0)).ok(-2);
            m.getInt(new Int(4)).ok(-3);
            execute();
           }
         };
       }
     };
   }

  static void test_memoryNegative()
   {test_memoryNegative(true);
    test_memoryNegative(false);
   }

  static void test_memoryRef(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex).memory(10))
     {void code()
       {final UnitMemory     M = unitMemory;
        final UnitMemory.Ref m = M.new Ref(2);
        final UnitMemory.Ref n = M.new Ref(3);
        new For(2)
         {void body(Int Index, Bool Continue)
           {m.putInt(new Int(0), new Int(1));

            m.putInt(new Int(1), new Int(-1));
            m.putInt(new Int(1), new Int(2));
            ok(()->nws(M.dumpAsDecimal()), """
Memory 0
            0    1    2    3    4    5    6    7    8    9
00000000              1    2
""");
            m.getInt(new Int(0)).ok(1);
            m.getInt(new Int(1)).ok(2);

            m.getBool(new Int(32)).ok(false);
            m.getBool(new Int(33)).ok(true );
            m.putBool(new Int(32), new Bool(true));
            m.putBool(new Int(34), new Bool(true));
            m.getInt (new Int( 1)).ok(7);
            ok(()->nws(M.dumpAsDecimal()), """
Memory 0
            0    1    2    3    4    5    6    7    8    9
00000000              1    7
""");

            m.putBool(new Int(32), new Bool(false));
            m.getBool(new Int(32)).ok(false);
            m.getBool(new Int(33)).ok(true );
            m.getBool(new Int(34)).ok(true);
            m.getInt (new Int( 1)).ok(6);
            ok(()->nws(M.dumpAsDecimal()), """
Memory 0
            0    1    2    3    4    5    6    7    8    9
00000000              1    6
""");
            m.clear(1);
            ok(()->nws(M.dumpAsDecimal()), """
Memory 0
            0    1    2    3    4    5    6    7    8    9
00000000                   6
""");
            m.copy(n, 1);
            ok(()->nws(M.dumpAsDecimal()), """
Memory 0
            0    1    2    3    4    5    6    7    8    9
00000000              6    6
""");
            M.clear();
            ok(()->nws(M.dumpAsDecimal()), """
Memory 0
            0    1    2    3    4    5    6    7    8    9
00000000
""");
            maxSteps(999);
            execute();
           }
         };
       }
     };
   }

  static void test_memoryRef()
   {          test_memoryRef(true);
              test_memoryRef(false);
   }

//  static void test_invalidate(boolean Ex)
//   {sayCurrentTestName();
//    final Program P = new Program(new Build().immediate(Ex).memory(16))
//     {void code()
//       {final UnitMemory     M = memory;
//        final UnitMemory.Ref m = M.new Ref(8);
//        m.invalidate(8);
//        m.clear     (4);
//       }
//     };
//    P.execute();
//    ok(P.memory, """
//   0   0
//   1   0
//   2   0
//   3   0
//   4   0
//   5   0
//   6   0
//   7   0
//   8   0
//   9   0
//  10   0
//  11   0
//  12  -1
//  13  -1
//  14  -1
//  15  -1
//""");
//    P.testVerilog(Ex);
//   }
//
//  static void test_invalidate()
//   {          test_invalidate(true);
//              test_invalidate(false);
//   }
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

  static void test_defineArrayViaVerilogFunction()
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(false).memory(16))
     {void code()
       {final int[]array = {0, 0, 0, 2, 4, 6};
        defineArrayViaVerilogFunction("array", array);
//      generateVerilog();
        execute();
       }
     };
   }

  static void test_lastInstructionBase(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex).memory(8))
     {void code()
       {final Int a = new Int(2);
        final Int b = new Int();
        a.ok(2);
        new If (a.eq(1))
         {void Then()
           {b.set(1);
           }
          void Else()
           {b.set(2);
           }
         };
        b.ok(2);
        new If (a.eq(2))
         {void Then()
           {b.set(1);
           }
          void Else()
           {b.set(2);
           }
         };
        b.ok(1);
        execute();
       }
     };
   }

  static void test_lastInstructionBase()
   {test_lastInstructionBase(true);
    test_lastInstructionBase(false);
   }

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_programming();
    test_andOr();
    test_add();
    test_fibonacci();
    test_mod();
    test_incremental();
    test_remote();
    //test_bits();
    test_copy();
    test_memory();
    test_memoryNegative();
    test_memoryRef();
    //test_invalidate();
    //test_procedureCall();
    test_defineArrayViaVerilogFunction();
    test_lastInstructionBase();
    test_variables();
   }

  static void newTests()                                                                                                // Tests being worked on
   {oldTests();
   }

  public static void main(String[] args)                                                                                // Test if called as a program
   {try                                                                                                                 // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {deleteAllFiles(verilogFolder, 99);                                                                                // Delete generated Verilog files created by a prior run of the current test
      if (github_actions) oldTests(); else newTests();                                                                  // Tests to run
      testSummary();                                                                                                    // Summarize test results
      coverageAnalysis(12);
      System.exit(testsFailed);
     }
    catch(Exception e)                                                                                                  // Get a traceback in a format clickable in Geany
     {System.err.println(e);
      System.err.println(fullTraceBack(e));
      System.exit(testsFailed);
     }
   }
 }
//https://github.com/philiprbrenan/btreeList/compare/oldSha...newSha package com.AppaApps.Silicon;
