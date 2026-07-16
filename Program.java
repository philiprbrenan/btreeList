//----------------------------------------------------------------------------------------------------------------------
// Machine level programming in Java
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
// use setValid everywhere where we I.v = true
// replace program().xxx with xxx() to make sore theta w are in the right program
// Replace ex(Int.Ops.set and for bool
// Place source/source2/target variables into Bool and Int as appropriate
// On assign to targetBool or targetInt make it valid by default
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;
import java.util.function.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;

//D1 Construct                                                                                                          // Develop and test a java program to describe a chip and emulate its operation.

public class Program extends Test                                                                                       // Develop and test a java program to describe a chip and emulate its operation.
 {final boolean                    suppressTraceComments = true;                                                        // Add trace comments to trace output to locate the point in the java code at which the verilog was generated - requires a lot of memory
  final boolean                          generateVerilog = true;                                                        // Generate verilog version of each program
  final boolean                               runVerilog = true;                                                        // Execute  verilog version of each program
  final boolean              suppressNamesInInstructions = true;                                                        // Include names in instructions
  final boolean                     compressInstructions = true;                                                        // Compress out identical instructions
  final boolean               suppressInstructionTracing = true;                                                        // Do not write a trace record for each instruction - the dump of program state at teh end opf the run will be the test of wether the program ran as expected
        int                                     maxSteps = 99_999;                                                      // Number of steps permitted in code execution - this provides some protection against endless loops during development

  final static String                      verilogFolder = "verilog/";                                                  // Verilog folder
  final static String                   verilogTraceFile = fe("traceVerilog", "txt");                                   // Verilog trace file
  final static String                      javaTraceFile = fe("traceJava",    "txt");                                   // Java trace file
  final static String                      verilogSuffix = "v";                                                         // Suffix for verilog files
  final static int padLabels = 20, padName = 12, padVerilog = 64;                                                       // Padding for components of the generated verilog copde

  final Stack<I>                                    code = new Stack<>();                                               // Machine code instructions
  final Stack<Label>                              labels = new Stack<>();                                               // Labels for instructions in this process
  final Program                            parentProgram;                                                               // Redirect the code and variables of one program to another to allow components to be tested in isolation before their code is integrated into a larger program.
  final UnitMemory                            unitMemory;                                                               // Optional memory associated with the program
  final boolean                                immediate;                                                               // Execute immediately if true else generate machine code and execute later
  public  I                                    executing = null;                                                        // Instruction currently being executed
  public  I                                    compiling = null;                                                        // Instruction currently being compiled
  private static int                            programs = 0;                                                           // Unique id for each program
  final   int                                  programId = ++programs;                                                  // Unique id for this program
  private int                                         pc;                                                               // Program counter indicating the instruction to be executed after the current one
  final        Stack<UnitMemory>                memories = new Stack<>();                                               // Memories used by this program and its dependent programs
  final        Stack<Int>                           ints = new Stack<>();                                               // Int variables
  final        Stack<Bool>                         bools = new Stack<>();                                               // Bool variables
  final static Stack<String>                        subs = new Stack<>();                                               // Name of the current method is cached here so that we can count instructions
        static       String                    subsTrace = null;                                                        // Traceback through the methods currently active
  final static TreeMap<String,Integer> instructionCounts = new TreeMap<>();                                             // Count instructions by subroutine in which they are added
  final TreeMap<String,Stack<I>>    matchingInstructions = new TreeMap<>();                                             // Combine instruction with identical verilog text
  final TreeMap<Integer,Integer>            pcVariableId = new TreeMap<>();                                             // Program counter to variable id
//final static TreeMap<String,Procedure> procedures      = new TreeMap<>();                                             // Procedures by name for this program
  final TreeSet<String>              extraVerilogMethods = new TreeSet<>();                                             // Save additional Verilog methods here prefixed by "x" - they will be incorporated into the generated Verilog and thus become available to instructions
  private int                                  currentPc = 0;                                                           // Current program counter
  private int                                     jtrace = 0;                                                           // Count the number of  times jtrace() has been called to demonstrate that each instruction generates one matching call to jtrace
  private int                                     vtrace = 0;                                                           // Count the number of  times vtrace() has been called to demonstrate that each instruction generates one matching call to vtrace
  private int                                  nextIntId = 0;                                                           // Unique id for each Int
  private int                                 nextBoolId = 0;                                                           // Unique id for each Bool
  private int                                sourceIntId = 0;                                                           // Id of source int
  private int                               source2IntId = 0;                                                           // Id of source2 int
  private int                                targetIntId = 0;                                                           // Id of target int
  private int                               sourceBoolId = 0;                                                           // Id of source bool
  private int                               targetBoolId = 0;                                                           // Id of target bool
  private boolean                             sourceBool = false;                                                       // Source value for a boolean  operation obtained from a variable
  private int                                  sourceInt = 0;                                                           // Source value for an integer operation obtained from a variable
  private int                                 source2Int = 0;                                                           // Second source value for an integer operation obtained from a variable
  private int                                  targetInt = 0;                                                           // Computed target integer value to be loaded into a variable
  private boolean                             targetBool = false;                                                       // Computed target boolean value to be loaded into a variable
  private boolean                        targetBoolValid = false;                                                       // Whether the value produced by a boolean operation is valid or not
  private boolean                         targetIntValid = false;                                                       // Whether the value produced by an integer operation is valid or not

  final static class Build                                                                                              // Builder for this program
   {boolean immediate;                                                                                                  // Immediate mode
    boolean trace;                                                                                                      // Trace execution
    Program parent;                                                                                                     // Parent program
    Integer size;                                                                                                       // Memory allocated by this program
    Build immediate (boolean Immediate) {immediate = Immediate; return this;}
    Build parent (   Program Parent)    {parent    = Parent;    return this;}
    Build memory (   int     Size)      {size      = Size;      return this;}
   }

  Program (Build Build)                                                                                                 // Construct
   {immediate       = Build.immediate;                                                                                  // Immediate or delayed execution
    parentProgram   = Build.parent == null ? this : Build.parent;                                                       // Parent program that will contain the code
    initializeRegisters();                                                                                              // Start registers in known state
    unitMemory      = Build.size   != null ? new UnitMemory(Build.size) : null;                                         // Memory associated with program if any
    deleteAllFiles(verilogTestFolder(), 9);                                                                             // Delete generated Verilog files created by a prior run of the current test
    makePath(verilogTestFolder());                                                                                      // Verilog folder for this test
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

  void  rx ()                                                                                                           // This register can only be accessed during execution
   {final I x = program().executing;
    if (!immediate() && x == null)
     {stop("Control register can only be accessed during execution:", x.traceBack, "====");
     }
   }

  void  rc ()                                                                                                           // This register can only be accessed during compilation
   {final I x = program().executing;
    if (x != null)
     {stop("Control registers can only be accessed during compilation:", x.traceBack, "====");
     }
   }

  Program maxSteps (int MaxSteps) {program().maxSteps = MaxSteps; return this;}                                         // Set number of steps
  void jtraceInc() {++program().jtrace;}                                                                                // Count trace records written
  void vtraceInc() {++program().vtrace;}

  Stack<Int>  ints ()           {return program().ints;}
  Stack<Bool> bools ()          {return program().bools;}

  int      currentPc()          {return program().     currentPc;}
  int    sourceIntId()          {return program().   sourceIntId;}
  int   source2IntId()          {return program().  source2IntId;}
  int    targetIntId()          {return program().   targetIntId;}
  int   sourceBoolId()          {return program().  sourceBoolId;}
  int   targetBoolId()          {return program().  targetBoolId;}
  int      sourceInt()          {return program().     sourceInt;}
  int     source2Int()          {return program().    source2Int;}
  int      targetInt()          {return program().     targetInt;}
  boolean sourceBool()          {return program().    sourceBool;}
  boolean targetBool()          {return program().    targetBool;}

  int      currentPc(int V)     {return program().     currentPc = V;}
  int    sourceIntId(int V)     {return program().   sourceIntId = V;}
  int   source2IntId(int V)     {return program().  source2IntId = V;}
  int    targetIntId(int V)     {return program().   targetIntId = V;}
  int   sourceBoolId(int V)     {return program().  sourceBoolId = V;}
  int   targetBoolId(int V)     {return program().  targetBoolId = V;}
  int      sourceInt(int V)     {return program().     sourceInt = V;}
  int     source2Int(int V)     {return program().    source2Int = V;}
  int      targetInt(int V)     {return program().     targetInt = V;}
  boolean sourceBool(boolean V) {return program().    sourceBool = V;}
  boolean targetBool(boolean V) {return program().    targetBool = V;}

  boolean targetBoolValid()          {return program().targetBoolValid;}
  boolean targetBoolValid(boolean V) {return program().targetBoolValid = V;}

  boolean targetIntValid ()          {return program().targetIntValid;}
  boolean targetIntValid (boolean V) {return program().targetIntValid = V;}

  void initializeRegisters()                                                                                            // Initialize registers
   {currentPc(0); sourceIntId(0); source2IntId(0); targetIntId(0); sourceBoolId(0); targetBoolId(0); sourceInt(0); source2Int(0); targetInt(0);
    sourceBool(false); targetBool(false);
   }

  TreeMap<Integer,Integer> pcVariableId() {return program().pcVariableId;}                                              // Address instruction number to variable for instructions that only manipulate one variable

//D1 Program                                                                                                            // Program execution structures.  the //D* comments are headers at different levels in the documentation describing this code

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
        index.T();                                                                                                      // Load index
        new I(false)                                                                                                    // Do not increment of program counter
         {void   a() {if (index.i() >=  End.i()) program().pc = end.offset;}                                            // Index out of range
          String v()                                                                                                    // Terminate loop when index is out of range
           {return "if (targetInt >= "+End.vn()+") pc <= pc + "+ (end.offset - instructionNumber) + ";"+
                   " else pc <= pc + 1;";
           }
          int traces() {return 0;}
         };
        cont.clear();                                                                                                   // Terminate unless told otherwise
        body(index, cont);                                                                                              // Execute the loop
        index.inc();                                                                                                    // Increment loop counter
        cont.T();                                                                                                       // Load continue
        new I(false)
         {void   a() {program().pc = cont.b() ? start.offset : end.offset;}                                             // Continue execution of the loop as long as requested
          String v()
           {return "if (targetBool) pc <= pc + "+ (start.offset - instructionNumber) + "; "+
                              "else pc <= pc + "+ (end  .offset - instructionNumber) + ";";}                            // Continue execution of the loop as long as requested
          int traces() {return 0;}
         };
        end.set();                                                                                                      // End of the loop
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
        index.T();                                                                                                      // Load index
        new I(false)                                                                                                    // The for loop will not be executed if the execution count is less than 1
         {void   a()   {if (index.i() >=  End.i()) program().pc = end.offset;}                                          // Index out of range
          String v()                                                                                                    // Terminate the loop when the index is out of range
           {return "if (targetInt >= "+End.vn()+") pc <= pc + "+
             (end.offset-instructionNumber)+"; else pc <= pc + 1;";
           }
          int traces() {return 0;}
         };
        body(index);                                                                                                    // Execute the loop
        index.inc();                                                                                                    // Increment loop counter
        new I(false)                                                                                                    // Will jump
         {void   a() {program().pc = start.offset;}                                                                     // Restart loop
          String v() {return "pc <= pc + "+(start.offset - instructionNumber)+";";}                                     // Index out of range
          int traces() {return 0;}
         };
        end.set();                                                                                                      // End of the loop
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
        Condition.T();                                                                                                  // Load target with condition
        new I(false)                                                                                                    // Jump to else if condition is false
         {void   a() {          if (!Condition.b()) program().pc = lse.offset;}
          String v()
           {return "if (!targetBool) pc <= pc + "+(lse.offset-instructionNumber)+
                   "; else pc <= pc + 1;";
           }
          int traces() {return 0;}
         };
        Then();                                                                                                         // Then body
        new I(false)                                                                                                    // Jump over else to end
         {void   a() {program().pc  = end.offset;}
          String v() {return "pc <= pc + "+(end.offset-instructionNumber)+";";}
          int traces() {return 0;}
         };
        lse.set();                                                                                                      // Start of else
        Else();                                                                                                         // Else body
        end.set();                                                                                                      // End of the if statement
       }
     }

    If (Bint Condition) {this(Condition.b);}                                                                            // If from boolean integer

    abstract void Then ();                                                                                              // Then clause
             void Else () {}                                                                                            // Else clause
   }

//D2 Procedure                                                                                                          // Procedure with parameters and return value.  Only works for static classes because it is unable to emulate "this" so commented out for the moment
/* Good idea but not reliable enough yet
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
    boolean   nd = false;                                                                                               // If true the boolean should not be dumped because it represents the validity of an integer variable and no such determination is possible in the Verilog code.
    final int id = program().nextBoolId++;                                                                              // Unique id for Bool
    String  name = null;                                                                                                // The name of the variable

    enum Ops {and, del, eq, flip, ne, or, set};                                                                         // Boolean operation classification by argument types

    Bool (String Name)             {this();  name = Name;}                                                              // Constructors with name supplied

    Bool ()                        {ai(); del(false);     bools().push(this);}                                          // Constructors. Set newly constructed integers to invalid and minus one
    Bool (boolean I)               {ai(); ie(Ops.set, I); bools().push(this);}
    Bool (Bool    I)               {ai(); ie(Ops.set, I); bools().push(this);}
    boolean       b ()             {x(); return i;}
    void          x ()             {if (!v) variableNotSet("Bool", name);}                                              // Check a value has been set for the boolean

    Bool        set ()             {return ie(Ops.set,  true); }                                                        // Boolean operations which modify the target
    Bool        set (boolean I)    {return ie(Ops.set,  I);    }
    Bool        set (Bool    I)    {return ie(Ops.set,  I);    }
    Bool      clear ()             {return ie(Ops.set,  false);}
    Bool        del (boolean I)    {return ie(Ops.del,  I);    }
    Bool       flip ()             {return ie(Ops.flip);       }
    Bool       Flip ()             {return dup().flip();}
    Bool         ne (Bool    I)    {return ie(Ops.ne,  I);}
    Bool         or (Bool    I)    {return ie(Ops.or,  I);}                                                             // "Or" without short circuit. Modifies the target.
    Bool        and (Bool    I)    {return ie(Ops.and, I);}                                                             // "And" without short circuit. Modifies the target.
    Bool         Or (Bool    I)    {return dup().or (I);}                                                               // "Or" without short circuit. Does not modify the target
    Bool        And (Bool    I)    {return dup().and(I);}                                                               // "And" without short circuit. Does not modify the target
    Bool        dup ()             {return new Bool(this);}                                                             // Duplicate a boolean so that the duplicated version can be modified without modifying the original
                                                                                                                        // Execute as an instruction because these are the building blocks of the chip with which we wish to construct the algorithm
    Bool ie (Ops Op)            {T();        new I() {void a() {ex(Op   );} String v() {return ev(Op);}}; W(); return this;}
    Bool ie (Ops Op, boolean I) {T(); S(I);  new I() {void a() {ex(Op, I);} String v() {return eV(Op);}}; W(); return this;}
    Bool ie (Ops Op, Bool    I) {T(); I.S(); new I() {void a() {ex(Op, I);} String v() {return eV(Op);}}; W(); return this;}

    int pc() {return currentPc();}                                                                                      // Address of instruction

    abstract class LoadSourceOrTarget
     {LoadSourceOrTarget(Bool B, String RegisterId, String RegisterValue)                                               // Load source or target value via id of boolean
       {final String ri = RegisterId;                                                                                   // Id register
        final String rv = RegisterValue;                                                                                // Value register

        pcVariableId().put(codeSize(), id);                                                                             // Id of variable being addressed by these instructions

        final I i = new I()                                                                                             // Load id of variable
         {void   a() {loadId(id);                         jTrace(f("%8d "+ri+" = %8d",  pc(),   id));}
          String v() {return ri+" <= pcVariableId(pc); "+ vTrace(  "%8d "+ri+" = %8d", "pc", ""+id);}
         };

        new I()                                                                                                         // Load source value
         {void   a() {loadValue(B.i);                 jTrace(f("%8d "+rv+" %8d",  pc(),  B.i ? 1 : 0));}
          String v() {return rv + " <= b["+ri+"]; " + vTrace(  "%8d "+rv+" %8d", "pc",  "b["+ ri +"]");}
         };
       }
      int pc() {return currentPc();}                                                                                    // Address of this instruction
      abstract void loadId   (int I);                                                                                   // Override to load the id of the variable
      abstract void loadValue(boolean V);                                                                               // Override to record the value of the variable
     }

    void S ()                                                                                                           // Load source delta and value
     {new LoadSourceOrTarget(this, "sourceBoolId", "sourceBool")
       {void loadId   (int I)     {sourceBoolId(I);}
        void loadValue(boolean V) {sourceBool  (V);}
       };
     }

    void S (boolean I)                                                                                                  // Load source constant
     {final int v = I ? 1 : 0;
      new I()
       {void   a() {sourceBool(I);                 jTrace(f("%8d boolLoadConstant %8d",  pc(),   v));}
        String v() {return "sourceBool <= "+v+"; "+vTrace(  "%8d boolLoadConstant %8d", "pc", ""+v);}
       };
     }

    void T ()                                                                                                           // Load target delta and value
     {new LoadSourceOrTarget(this, "targetBoolId", "targetBool")
       {void loadId   (int     I) {targetBoolId(I);}
        void loadValue(boolean V) {targetBool  (V);}
       };
     }

    void W ()                                                                                                           // Write result back into variable
     {final Bool b = this;
      new I()                                                                                                           // Load value
       {void   a() {i = targetBool(); v = targetBoolValid(); jTrace(f("%8d writeBool %8d = %8d",  pc(), b.id,         b.i ? 1 : 0));}
        String v() {return "b[targetBoolId] <= targetBool; "+vTrace(  "%8d writeBool %8d = %8d", "pc", "targetBoolId", "targetBool");}
       };
     }

    Bool ex (Ops Op)                                                                                                    // Execute a monadic boolean operation
     {executingCheck();
      switch(Op)
       {case flip -> {x(); targetBool(!targetBool());}
        default   -> Test.stop("Op not implemented:", Op);
       }
      jtrace();
      return this;
     }

    Bool ex (Ops Op, boolean I)                                                                                         // Execute a dyadic boolean operation on a constant
     {executingCheck();
      targetBoolValid(true);
      switch (Op)
       {case set -> {     targetBool(sourceBool());}
        case del -> {     targetBool(sourceBool()); targetBoolValid(false);}
        case eq  -> {x(); targetBool(targetBool() == sourceBool());}
        case ne  -> {x(); targetBool(targetBool() != sourceBool());}
        case and -> {x(); targetBool(targetBool() && sourceBool());}
        case or  -> {x(); targetBool(targetBool() || sourceBool());}
        default  -> Test.stop("Op not implemented:", Op);
       }
      jtrace();
      return this;
     }

    Bool ex (Ops Op, Bool I)                                                                                            // Execute a dyadic boolean operation on a variable
     {executingCheck();
      I.x();
      return ex(Op, I.i);
     }

//    Bool ex (Ops Op, Int I)                                                                                             // Execute a dyadic boolean operation on an integer variable
//     {executingCheck();
//      switch(Op)
//       {case set -> {I.x();  = I.i > 0; v = true;}
//        default  -> Test.stop("Op not implemented:", Op);
//       }
//      jtrace();
//      return this;
//     }

    String ev (Ops Op)                                                                                                  // Execute a monadic boolean operation
     {final String        n = vn();                                                                                     // Name of the variable in Verilog
      final StringBuilder s = new StringBuilder();
      switch(Op)
       {case flip -> {s.append("!targetBool");}
        default   -> Test.stop("Op not implemented:", Op);
       }
      return vtrace(s);                                                                                                 // Trace the operation
     }

    String eV (Ops Op)                                                                                                  // Execute a dyadic boolean operation
     {final StringBuilder s = new StringBuilder();
      switch (Op)
       {case set -> {s.append(              "sourceBool");}
        case del -> {s.append(              "sourceBool");}
        case eq  -> {s.append("targetBool == sourceBool");}
        case ne  -> {s.append("targetBool != sourceBool");}
        case and -> {s.append("targetBool && sourceBool");}
        case or  -> {s.append("targetBool || sourceBool");}
        default  -> Test.stop("Op not implemented:", Op);
       }
      return vtrace(s);                                                                                                 // Trace the operation
     }


    String vtrace (StringBuilder Value)                                                                                 // Trace a verilog boolean operation
     {vtraceInc();
      return "targetBool <= "+Value+
        "; $fdisplay(traceFile, \"%8d bool %8d = %8d\", pc,          targetBoolId, "+Value+");";
     }
    void jtrace ()     {jTrace(f("%8d bool %8d = %8d",  currentPc(), id,             targetBool() ? 1 : 0));}           // Trace a java    boolean operation

    public String toString ()                                                                                           // Print the boolean
     {final String u = "undefined_Bool";
      if (name == null) return v ? ""+i       : u;
      else              return v ? name+"="+i : u+": "+name;
     }

    String   vn ()                                                                                                      // Verilog name of this boolean variable
     {final String n = suppressNamesInInstructions ? "" : name != null ? "/*"+name+"*/" : "";
      return pad("b["+id+"]"+n, padName);
     }

//    void stop (final Object...O)                                                                                        // Conditionally print a message if true and stop
//     {new If (this)
//       {void Then()
//         {new I(false)
//           {void   a() {Test.stop(O);}
//            String v() {return "pc <= -1;";}
//            boolean trace() {return false;}
//           };
//         }
//       };
//     }
//
//    Bool say () {new I() {void a() {Test.say(this);}}; return this;}                                                    // Say the boolean

    Bool ok (boolean Value)                                                                                             // Memory trace from java makes this test redundant in Verilog if the Verilog trace matches the java trace and so there will be an empty instruction generated in the verilog to "regulate the service"
     {final  Bool got = this;
      new I()
       {void a()
         {if (!got.v) stop("Invalid Bool being tested at:", program().executing.instructionLocation());
          Test.ok(i, Value);
         }
        int traces() {return 0;}
       };
      return this;
     }

    Bool ok (Bool Value)                                                                                                // Memory trace from java makes this test redundant in Verilog if the Verilog trace matches the java trace  and so there will be an empty instruction generated in the verilog to "regulate the service"
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

    Int ()           {ai(); del(-1);        ints().push(this);}                                                 // Constructors without name. Invalidate the integer. The invalidation is done in such a way as to make the instruction sequences for java and Verilog match. Recall that that the Verilog integers do not carry a valid flag with them as this would be a waste of resources given that the algorithm is correct. The integers used in the java version do carry a valid flag to assist in validating the correctness of this implementation of the btree algorithm before handing it off to Verilog.

    Int (int I)      {ai(); ie(Ops.set, I); ints().push(this);}
    Int (Int I)      {ai(); ie(Ops.set, I); ints().push(this);}
                                                                                                                        // Possible integer operations
    enum Ops {abs, add, add2, dec, del, div, down, eq, ge, gt, inc, le, lt,
       mod, mul, neg, ne, set, sqrt, sub, up};

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
    Int  del (int  I) {return ie(Ops.del , I);}

    Int ie (Ops Op)        {T();        new I() {void a() {ex(Op   );} String v() {return ev(Op   );}}; W(); return this;} // Execute immediately or create an instruction for machine code to execute later
    Int ie (Ops Op, int I) {T(); S(I);  new I() {void a() {ex(Op, I);} String v() {return ev(Op, I);}}; W(); return this;}
    Int ie (Ops Op, Int I) {T(); I.S(); new I() {void a() {ex(Op, I);} String v() {return ev(Op, I);}}; W(); return this;}

    abstract class LoadSourceOrTarget
     {LoadSourceOrTarget(Int I, String RegisterID, String RegisterValue)                                                // Load source or target value via integer id
       {final String ri = RegisterID;                                                                                   // Shorten name
        final String rv = RegisterValue;                                                                                // Shorten name

        pcVariableId().put(codeSize(), id);                                                                             // Id of variable being addressed by these instructions

        new I()                                                                                                         // Id of integer
         {void   a() {loadId(id);                           jTrace(f("%8d "+ri+" = %8d",  pc(),   id));}
          String v() {return ri + " <= pcVariableId(pc); "+ vTrace(  "%8d "+ri+" = %8d", "pc", ""+id) ;}
         };

        new I()                                                                                                         // Value of integer
         {void   a() {loadValue(I.i);                jTrace(f("%8d "+rv+" = %8d",  pc(),  I.i));}
          String v() {return rv + " <= i["+ri+"]; "+ vTrace(  "%8d "+rv+" = %8d", "pc",  "i["+ri+"]");}
         };
       }
      int pc() {return currentPc();}                                                                                    // Address of this instruction
      abstract void loadId   (int I);                                                                                   // Override to save delta from last integer base
      abstract void loadValue(int V);                                                                                   // Override to save the current value of the integer variable
     }

    abstract class LoadConstant
     {LoadConstant(int I, String Register)                                                                              // Load source constant into source register to increase compressability of instructions
       {final String ac = pad(Register + " <= "+I+"; ", padName);                                                       // Assign the constant to the source register
        new I()
         {void   a() {load(I);        jTrace(f("%8d "+Register+" constant %8d",  currentPc(), I));}
          String v() {return ac +     vTrace(  "%8d "+Register+" constant %8d", "pc",      ""+I);}
         };
       }
      int pc() {return currentPc();}                                                                                    // Address of this instruction
      abstract void load(int C);                                                                                        // Override to load the constantalue of the integer variable being loaded into a java variable
     }

    void S ()                                                                                                           // Save source delta and value
     {new LoadSourceOrTarget(this, "sourceIntId", "sourceInt")
       {void loadId   (int I) {sourceIntId(I);}
        void loadValue(int V) {sourceInt  (V);}
       };
     }

    void S2 ()                                                                                                          // Save second source delta and value
     {new LoadSourceOrTarget(this, "source2IntId",  "source2Int")
       {void loadId   (int I) {source2IntId(I);}
        void loadValue(int V) {source2Int  (V);}
       };
     }

    void S (int I) {new LoadConstant(I, "sourceInt")   {void load(int C) {sourceInt (C);}};}                            // Save source constant
    void S2(int I) {new LoadConstant(I, "source2Int")  {void load(int C) {source2Int(C);}};}                            // Save second source constant

    void T ()                                                                                                           // Save target delta and value
     {new LoadSourceOrTarget(this, "targetIntId", "targetInt")
       {void loadId   (int I) {targetIntId(I);}
        void loadValue(int V) {targetInt  (V);}
       };
     }

    void W ()                                                                                                           // Write result back into variable
     {final Int w = this;
      new I()                                                                                                           // Load value
       {void   a() {i = targetInt(); v = targetIntValid(); jTrace(f("%8d writeInt %8d = %8d",  currentPc(),  targetIntId(),   targetInt()));} // We are assuming that
        String v() {return "i[targetIntId] <= targetInt; "+vTrace(  "%8d writeInt %8d = %8d", "pc",         "targetIntId", "targetInt");}
       };
     }

    Int ex (Ops Op)                                                                                                     // Execute a monadic integer operation
     {executingCheck();
      x(); targetIntValid(true);
      switch(Op)
       {case inc  -> {targetInt(targetInt()   + 1);}
        case dec  -> {targetInt(targetInt()   - 1);}
        case up   -> {targetInt(targetInt()  << 1);}
        case down -> {targetInt(targetInt() >>> 1);}
        case sqrt -> {targetInt((int)Math.sqrt(targetInt()));}
        case neg  -> {targetInt(- targetInt());}
        case abs  -> {targetInt(targetInt() < 0 ? -targetInt() : targetInt());}
        default   -> stop("Op not implemented:", Op);
       }

      jtrace();
      return this;
     }

    Int ex (Ops Op, int I)                                                                                              // Execute a dyadic integer operation on a constant
     {executingCheck();
      targetIntValid(true);
      switch (Op)
       {case set  -> {      targetInt(              I);}
        case del  -> {      targetInt(              I); targetIntValid(false);}
        case add  -> { x(); targetInt(targetInt() + I);}
        case sub  -> { x(); targetInt(targetInt() - I);}
        case mul  -> { x(); targetInt(targetInt() * I);}
        case div  -> { x(); targetInt(targetInt() / I);}
        case mod  -> { x(); targetInt(targetInt() % I);}
        case add2 -> { x(); targetInt(targetInt() + I + I);}
        default   -> stop("Op not implemented:", Op);
       }
      jtrace();
      return this;
     }

    Int ex (Ops Op, Int I)                                                                                              // Execute a monadic integer operation on a variable
     {executingCheck();
      I.x();
      return ex(Op, I.i());
     }

    String ev (Ops Op)                                                                                                  // Execute a monadic integer operation in Verilog
     {final String        n = "targetInt";                                                                              // Name of the variable in Verilog
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
      return vExecuteAndTrace(""+s);
     }

    String ev (Ops Op, int I)                                                                                           // Execute a monadic integer operation on a constant
     {final String        n = "targetInt";                                                                              // Name of the variable in Verilog
      final StringBuilder s = new StringBuilder();
      switch (Op)
       {case set  -> {s.append(        I);}
        case del  -> {s.append(        I);}
        case add  -> {s.append(n+" + "+I);}
        case sub  -> {s.append(n+" - "+I);}
        case mul  -> {s.append(n+" * "+I);}
        case div  -> {s.append(n+" / "+I);}
        case mod  -> {s.append(n+" % "+I);}
        case add2 -> {s.append(n+" + "+I+" + "+I);}
        default   -> stop("Op not implemented:", Op);
       }
      return vExecuteAndTrace(""+s);
     }

    String ev (Ops Op, Int I)                                                                                           // Execute a monadic integer operation on a variable
     {final String        n = "targetInt", i = "sourceInt";                                                             // Name of the variable in Verilog
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
      return vExecuteAndTrace(""+s);
     }

    String vExecuteAndTrace (String Value)                                                                              // Execute and trace an integer operation in Verilog
     {vtraceInc();
      return "targetInt <= "+Value+"; $fdisplay(traceFile, \"%8d assign targetInt = %8d\", pc,          "+Value+");";
     }

    void jtrace () {                               jTrace(f("%8d assign targetInt = %8d",  currentPc(),   targetInt()));} // Trace the integer operation in Java

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

    Bool eq ( int I) {return bie(Ops.eq, I);}                                                                           // Comparisons with a constant integer
    Bool ne ( int I) {return bie(Ops.ne, I);}                                                                           //N
    Bool le ( int I) {return bie(Ops.le, I);}
    Bool lt ( int I) {return bie(Ops.lt, I);}
    Bool ge ( int I) {return bie(Ops.ge, I);}
    Bool gt ( int I) {return bie(Ops.gt, I);}

    Bool eq ( Int I) {return bie(Ops.eq, I);}                                                                           // Comparisons with a variable integer
    Bool ne ( Int I) {return bie(Ops.ne, I);}                                                                           //N
    Bool le ( Int I) {return bie(Ops.le, I);}
    Bool lt ( Int I) {return bie(Ops.lt, I);}
    Bool ge ( Int I) {return bie(Ops.ge, I);}                                                                           //N
    Bool gt ( Int I) {return bie(Ops.gt, I);}

    Bool bie (Ops Op, int I)                                                                                            // Instruction to perform a boolean comparison between an integer variable and an integer constant
     {final Bool b = new Bool();
      S(); S2(I); b.T();
      new I()
       {void   a() {       bex(Op, b, I);}
        String v() {return bev(Op, b);}
       };
      b.W();
      return b;
     }

    Bool bie (Ops Op, Int I)                                                                                            // Instruction to perform a boolean comparison between two integer variables
     {final Bool b = new Bool();
      S(); I.S2(); b.T();
      new I()
       {void   a() {I.x(); bex(Op, b, I);}
        String v() {return bev(Op, b);}
       };
      b.W();
      return b;
     }

    void bex (Ops Op, Bool B, int I)                                                                                    // Boolean comparison between an integer variable and an integer constant
     {x();
      switch(Op)
       {case eq -> targetBool(sourceInt() == source2Int());
        case ne -> targetBool(sourceInt() != source2Int());
        case le -> targetBool(sourceInt() <= source2Int());
        case lt -> targetBool(sourceInt() <  source2Int());
        case ge -> targetBool(sourceInt() >= source2Int());
        case gt -> targetBool(sourceInt() >  source2Int());
        default -> stop("Op not implemented:", Op);
       }
      targetBoolValid(true);
      B.jtrace();
      //jTrace(f("%8d bool %8d = %8d", currentPc, B.id, targetBool ? 1 : 0));
     }

    void bex (Ops Op, Bool B, Int I) {I.x(); bex(Op, B, I.i);}                                                          // Boolean comparison between two integer variables

    String bev (Ops Op, Bool B)                                                                                         // Boolean comparison between two integers
     {final StringBuilder s = new StringBuilder();
      final String a = "sourceInt", b = "source2Int";
      switch(Op)
       {case eq -> s.append(a + " == " + b);
        case ne -> s.append(a + " != " + b);
        case le -> s.append(a + " <= " + b);
        case lt -> s.append(a + " <  " + b);
        case ge -> s.append(a + " >= " + b);
        case gt -> s.append(a + " >  " + b);
        default -> stop("Op not implemented:", Op);
       }
      return B.vtrace(s);
     }

    Int dup () {return new Int(this);}                                                                                  // Duplicate an integer so that the duplicated version can be modified without modifying the original

    void setValid () {v = true;}                                                                                        // Mark an integer as valid

    Bool valid ()                                                                                                       // Whether the integer is valid - these checks are not made in Verilog because it is assumed that of the memory traces match then the behavior of the Verilog is identical to that of the java and thus there is no need to test the validity of the integers
     {final Bool b = new Bool(); b.nd = true;                                                                           // Do not dump this boolean variable because it holds a value that has no analog in the Verilog code
      new I() {void a() {b.i = v; b.v = true;} int traces() {return 0;}};
      return b;
     }

    Bool notValid ()                                                                                                    // Whether the integer is invalid - these checks are not made in Verilog because it is assumed that of the memory traces match then the behavior of the Verilog is identical to that of the java and thus there is no need to test the validity of the integers
     {final Bool b = new Bool(); b.nd = true;                                                                           // Do not dump this boolean variable because it holds a value that has no analog in the Verilog code
      new I() {void a() {b.i = !v; b.v = true;} int traces() {return 0;}};
      return b;
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
     {final String n = suppressNamesInInstructions ? "" : name != null ? "/*"+name+"*/" : "";
      return pad("i["+id+"]"+n, padName);
     }

//  Int     say () {final Int i = this; new I() {void a() {Test.say(i);} }; return this;}                               // Say the integer

    Int ok (int Value)                                                                                                  // Check the integer. Ther is no corresponding check in Verilog other than the execution logs matching so there will be an empty instruction generated in the verilog to "regulate the service"
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

    Int ok (Int Value)                                                                                                  // Test an Integer. The value expected and the value got must be valid during the java execution because the verilog execution deliberately removes this information on the basis that the java code is definitive and so if the verilog race matches the java trace the verilog code is working correctly. The purpose of the validity bit is to internally track whether the integer was ever set during program execution, it is not to convey application information. If an integer with an attached validity bit is required in application logic then Bint should be used.  This features does not exist in the Verilog code and so there will be an empty instruction generated in the verilog to "regulate the service"
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
     {new If (b.Flip()) {void Then() {stop("Requested int component from unset Bint");}};                                // Complain if there is no integer component to return
      return new Int(i);
     }

    Bool valid ()      {return b;}                                                                                      // Whether the boolean integer is valid
    Bool notValid ()   {return b.Flip();}                                                                               // Whether the boolean integer is invalid
    Bint invalidate () {b.clear(); return this;}                                                                        // Mark the integer as invalid after all

    Bint copy (Bint Source)                                                                                             // Copy a boolean integer
     {new If (Source.b)
       {void Then() {b.set(); i.set(Source.i());}                                                                       // Set target as valid to match source and copy the source integer                                                                                                    // The source has been set
        void Else() {b.clear();};                                                                                       // Set target as invalid to match source
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
    boolean   readBool = false;                                                                                         // Boolean read from memory
    boolean  writeBool = false;                                                                                         // Boolean to write into memory
    int        readInt = 0;                                                                                             // Integer read from memory
    int       writeInt = 0;                                                                                             // Integer to write into memory
    int   readIntIndex = 0;                                                                                             // Index at which to read an integer from memory
    int   readBitIndex = 0;                                                                                             // Index within an integer from which to get a bit to make a boolean
    int  writeIntIndex = 0;                                                                                             // Index at which to write an integer into memory
    int  writeBitIndex = 0;                                                                                             // Index within an integer at which to set a bit to represent a boolean

    static int bitsPerUnit() {return Integer.SIZE;}                                                                     // Bits per memory unit

    UnitMemory (int Length)                                                                                             // Create and clear some memory
     {units = new int[Length];
      clear(new Int(0), Length);
      final Stack<UnitMemory> m = program().memories; id = m.size(); m.push(this);                                      // Give the memory a unique identifier and save it in the main program
     }

    int size()  {return units.length;}                                                                                  // Size of memory
    String i () {return ""+id;}                                                                                         // Number of memory a string for use in writing verilog
    String n () {return "m_"+id;}                                                                                       // Name of memory

    String      vReadBool()      {return n() + "_readBool"     ;}                                                       // Boolean read from memory
    String     vWriteBool()      {return n() + "_writeBool"    ;}                                                       // Boolean to write into memory
    String       vReadInt()      {return n() + "_readInt"      ;}                                                       // Integer read from memory
    String      vWriteInt()      {return n() + "_writeInt"     ;}                                                       // Integer to write into memory
    String  vReadIntIndex()      {return n() + "_readIntIndex" ;}                                                       // Index at which to read an integer from memory
    String  vReadBitIndex()      {return n() + "_readBitIndex" ;}                                                       // Index within an integer from which to get a bit to make a boolean
    String vWriteIntIndex()      {return n() + "_writeIntIndex";}                                                       // Index at which to write an integer into memory
    String vWriteBitIndex()      {return n() + "_writeBitIndex";}                                                       // Index within an integer at which to set a bit to represent a boolean

    String       readIntV()      {vtraceInc(); return vReadInt () + " <= "+n()+"["+vReadIntIndex()+"];                           $fdisplay(traceFile, \"%8d readInt       %8d\", pc, "    +n()+"["+vReadIntIndex ()                                          +"]);";}
    String      readBoolV()      {vtraceInc(); return vReadBool() + " <= "+n()+"["+vReadIntIndex()+"]["+vReadBitIndex()+"];      $fdisplay(traceFile, \"%8d readBool      %8d\", pc, "    +n()+"["+vReadIntIndex ()+"]["+vReadBitIndex ()                    +"]);";}
    String      writeIntV()      {vtraceInc(); return n()+"["+vWriteIntIndex()+"]                       <= " + vWriteInt () + "; $fdisplay(traceFile, \"%8d writeInt      %8d<%8d\", pc, "+n()+"["+vWriteIntIndex()+"],"+vWriteInt     ()                    + ");";}
    String     writeBoolV()      {vtraceInc(); return n()+"["+vWriteIntIndex()+"]["+vWriteBitIndex()+"] <= " + vWriteBool() + "; $fdisplay(traceFile, \"%8d writeBool     %8d<%8d\", pc, "+n()+"["+vWriteIntIndex()+"]["+vWriteBitIndex()+"]," + vWriteBool()+ ");";}
    String  readIntIndexV(Int I) {vtraceInc(); return vReadIntIndex () + " <= i["+I.id+"];                                       $fdisplay(traceFile, \"%8d readIntIndex  %8d=%8d\", pc, "+I.id+", i["+I.id+"]);";}
    String  readBitIndexV(Int I) {vtraceInc(); return vReadBitIndex () + " <= i["+I.id+"];                                       $fdisplay(traceFile, \"%8d readBitIndex  %8d=%8d\", pc, "+I.id+", i["+I.id+"]);";}
    String writeIntIndexV(Int I) {vtraceInc(); return vWriteIntIndex() + " <= i["+I.id+"];                                       $fdisplay(traceFile, \"%8d writeIntIndex %8d=%8d\", pc, "+I.id+", i["+I.id+"]);";}
    String writeBitIndexV(Int I) {vtraceInc(); return vWriteBitIndex() + " <= i["+I.id+"];                                       $fdisplay(traceFile, \"%8d writeBitIndex %8d=%8d\", pc, "+I.id+", i["+I.id+"]);";}

    void         readIntJ()      {readInt  = units[readIntIndex];                                                                             jTrace(f("%8d readInt       %8d",     pc(), readInt          ));}
    void        readBoolJ()      {readBool = getBit(units[readIntIndex], readBitIndex);                                                       jTrace(f("%8d readBool      %8d",     pc(), readBool  ? 1 : 0));}
    void        writeIntJ()      {final int i = writeIntIndex, p = units[i]; units[i] = writeInt;                                             jTrace(f("%8d writeInt      %8d<%8d", pc(), p, writeInt));}
    void       writeBoolJ()      {final int i = writeIntIndex, b = writeBitIndex, p = units[i]; units[i] = setBit(p, b, writeBool);           jTrace(f("%8d writeBool     %8d<%8d", pc(), getBit(p, b) ? 1 : 0, writeBool ? 1 : 0));}
    void    readIntIndexJ(Int I) {readIntIndex  = I.i();                                                                                      jTrace(f("%8d readIntIndex  %8d=%8d", pc(), I.id, I.i()      ));}
    void    readBitIndexJ(Int I) {readBitIndex  = I.i();                                                                                      jTrace(f("%8d readBitIndex  %8d=%8d", pc(), I.id, I.i()      ));}
    void   writeIntIndexJ(Int I) {writeIntIndex = I.i();                                                                                      jTrace(f("%8d writeIntIndex %8d=%8d", pc(), I.id, I.i()      ));}
    void   writeBitIndexJ(Int I) {writeBitIndex = I.i();                                                                                      jTrace(f("%8d writeBitIndex %8d=%8d", pc(), I.id, I.i()      ));}

    int pc() {return currentPc();}

    int     setBit(int X, int I, boolean V) {return  X & ~(1 << I) | (V ? 1 : 0) << I;}                                 // Set a bit in an integer
    boolean getBit(int X, int I)            {return (X >> I & 1) != 0;}                                                 // Get a bit from an integer

    String dumpVerilogMemoryInDecimalName() {return "dumpDecimal_"+id;}                                                 // Name of the verilog routine to dump this memeory in decimal

    UnitMemory copy (UnitMemory SourceMemory, Int SourceOffset, Int TargetOffset, int Width)                            // Copy the specified memory
     {subStart("Program.UnitMemory.copy");
      new ForCount(new Int(Width))
       {void body(Int Index)
         {final Int s = SourceOffset.Add(Index);
          final Int t = TargetOffset.Add(Index);
          new I()                                                                                                       // Set source index
           {void   a() {       SourceMemory.readIntIndexJ(s);}
            String v() {return SourceMemory.readIntIndexV(s);}
           };
          new I()                                                                                                       // Read from source memory
           {void   a() {       SourceMemory.readIntJ();}
            String v() {return SourceMemory.readIntV();}
           };
          new I()                                                                                                       // Set target index
           {void   a() {       writeIntIndexJ(t);}
            String v() {return writeIntIndexV(t);}
           };
          new I()                                                                                                       // Set write from read
           {void   a() {writeInt = SourceMemory.readInt;                                                   jTrace(f("%8d writeInt=readInt %8d",  pc(), writeInt));}
            String v() {vtraceInc(); return vWriteInt() + " <= "+SourceMemory.vReadInt() + "; $fdisplay(traceFile, \"%8d writeInt=readInt %8d\", pc, "+SourceMemory.vReadInt()+ ");";}
           };
          new I()                                                                                                       // Write into target memory
           {void   a() {       writeIntJ();}
            String v() {return writeIntV();}
           };
         }
       };
      subFinish();
      return this;
     }

    UnitMemory clearUnit (Int Index)                                                                                    // Clear memory unit
     {subStart("Program.UnitMemory.clearUnit(I)");
      new I()                                                                                                           // Set target index
       {void   a() {       writeIntIndexJ(Index);}
        String v() {return writeIntIndexV(Index);}
       };
      new I()                                                                                                           // Set write from read
       {void   a() {writeInt = 0;                                          jTrace(f("%8d writeInt=0",  pc()));}
        String v() {vtraceInc(); return vWriteInt() + " <= 0; $fdisplay(traceFile, \"%8d writeInt=0\", pc);";}
       };
      new I()                                                                                                           // Write into target memory
       {void   a() {       writeIntJ();}
        String v() {return writeIntV();}
       };
      subFinish();
      return this;
     }

    UnitMemory clear ()                                                                                                 // Clear memory
     {subStart("Program.UnitMemory.clear(I)");
      new ForCount(new Int(size())) {void  body(Int Index) {clearUnit(Index);}};
      subFinish();
      return this;
     }

    UnitMemory clear (Int Start, int Width)                                                                             // Clear memory
     {subStart("Program.UnitMemory.clear(II)");
      new ForCount (Start, Start.Add(Width)) {void  body(Int Index) {clearUnit(Index);}};
      subFinish();
      return this;
     }

    Int getInt (Int I)                                                                                                  // Get the int at the indicated position
     {final Int r = new Int();
      new I()                                                                                                           // Set index
       {void   a() {       readIntIndexJ(I);}
        String v() {return readIntIndexV(I);}
       };
      new I()                                                                                                           // Read from memory
       {void   a() {       readIntJ();}
        String v() {return readIntV();}
       };
      new I()                                                                                                           // Set target index
       {void   a() {r.i = readInt; r.v = true;                                       jTrace(f("%8d ReadInt from Memory %8d = %8d",  pc(), r.id,     I.id));}
        String v() {vtraceInc(); return "i["+r.id+"] <= "+vReadInt()+"; $fdisplay(traceFile, \"%8d ReadInt from Memory %8d = %8d\", pc, "+r.id+", "+I.id+");";}
       };
      return r;
     }

    Bool getBool (Int I, Int J)                                                                                         // Get the bit in the specified byte at the specified position within the byte
     {Bool r = new Bool();
      new I()                                                                                                           // Set int index
       {void   a() {       readIntIndexJ(I);}
        String v() {return readIntIndexV(I);}
       };
      new I()                                                                                                           // Set bit index
       {void   a() {       readBitIndexJ(J);}
        String v() {return readBitIndexV(J);}
       };
      new I()                                                                                                           // Read from memory
       {void   a() {       readBoolJ();}
        String v() {return readBoolV();}
       };
      new I()                                                                                                           // Set target index
       {void   a() {r.i = readBool; r.v = true;                                       jTrace(f("%8d ReadBool from Memory %8d = %8d",  pc(), r.id,     readBool ? 1 : 0));}
        String v() {vtraceInc(); return "b["+r.id+"] <= "+vReadBool()+"; $fdisplay(traceFile, \"%8d ReadBool from Memory %8d = %8d\", pc, "+r.id+", "+vReadBool()+");";}
       };
      return r;
     }

    Bool getBool (Int I) {return getBool(I.Div(Integer.SIZE), I.Mod(Integer.SIZE));}                                    // Get the bit at the bit indexed location

    UnitMemory putInt (Int I, Int J)                                                                                    // Set the int at the indicated position relative to the start to the specified value
     {new I()                                                                                                           // Set target index
       {void   a() {       writeIntIndexJ(I);}
        String v() {return writeIntIndexV(I);}
       };
      new I()                                                                                                           // Set write from read
       {void   a() {final             int p = writeInt; writeInt = J.i();              jTrace(f("%8d writeInt2 %8d = %8d < %8d",  pc(),  writeIntIndex,          J.i,          p));}
        String v() {vtraceInc(); return vWriteInt() + " <= i["+J.id + "]; $fdisplay(traceFile, \"%8d writeInt2 %8d = %8d < %8d\", pc, "+vWriteIntIndex()+ ", i["+J.id + "], "+vWriteInt()+ ");";}
       };
      new I()                                                                                                           // Write into target memory
       {void   a() {       writeIntJ();}
        String v() {return writeIntV();}
       };
      return this;
     }

    UnitMemory putBool (Int I, Int J, Bool K)                                                                           // Set the bit at the indicated position in the byte at the specified position to the specified value
     {new I()                                                                                                           // Set target index
       {void   a() {       writeIntIndexJ(I);}
        String v() {return writeIntIndexV(I);}
       };
      new I()                                                                                                           // Set target index
       {void   a() {       writeBitIndexJ(J);}
        String v() {return writeBitIndexV(J);}
       };
      new I()                                                                                                           // Set write from read
       {void   a() {writeBool = K.b();                                                  jTrace(f("%8d writeBool2 %8d, %8d = %8d < %8d",  pc(),  writeIntIndex,         writeBitIndex,          K.i ? 1 : 0,  writeBool ? 1 : 0));}
        String v() {vtraceInc(); return vWriteBool() + " <= b["+K.id + "]; $fdisplay(traceFile, \"%8d writeBool2 %8d, %8d = %8d < %8d\", pc, "+vWriteIntIndex()+ ", "+vWriteBitIndex()+ ", b["+K.id + "], b["+K.id + "]);";}
       };
      new I()                                                                                                           // Write into memory
       {void   a() {       writeBoolJ();}
        String v() {return writeBoolV();}
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

      Ref        copy (Ref Source, int Width){m.copy(Source.m, Source.offset, offset, Width);       return this;}       // Copy the specified memory possibly from another byte memory
      Ref       clear (int Width)            {m.clear(offset, Width);                               return this;}       // Clear memory by setting its bytes to zero
      Int      getInt (Int I)                {return m.getInt( I.Add(offset));}                                         // Get the int at the indicated position
      Bool    getBool (Int I)                {return m.getBool(I.Add(offset.Mul(Integer.SIZE)));}                       // Get the bit at the bit indexed location
      Int      getInt ()                     {return m.getInt(offset);}                                                 // Get the referenced int
      Ref      putInt (Int J)                {m.putInt (offset, J);                                 return this;}       // Put the referenced int
      Ref      putInt (Int I, Int  J)        {m.putInt(        I.Add(offset), J);                   return this;}       // Set the int at the indicated position relative to the start to the specified value
      Ref     putBool (Int I, Bool K)        {m.putBool(       I.Add(offset.Mul(Integer.SIZE)), K); return this;}       // Set the bit at the bit indexed position
      Ref        step (int Width)            {return new Ref(offset.Add(Width));}                                       // Step up from an existing ref to make a new one - only while not executing


      int      getInt (int I) {                                        return units[I+offset.i];}                       // Get an integer immediately when debugging
      boolean getBool (int I) {final int i = getInt(I / Integer.SIZE); return getBit(i, I % Integer.SIZE);}             // Get a boolean  immediately when debugging

      public String toString () {final StringBuilder s = saySb("Ref: " , offset.i()); return ""+s;}                     // Print memory reference
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

//D1 Machine Code                                                                                                       // Generate machine code instructions to implement the program

//D2 Instruction                                                                                                        // An instruction represents code to be executed by a process in a single clock cycle == process step

  abstract class I                                                                                                      // Instructions implement the action of a program
   {final int instructionNumber = program().code.size();                                                                // The number of this instruction
    final String        traceBack = suppressTraceComments ?  null : traceBack();                                         // Line at which this instruction was created - suppressible because it imposes a lot of extra processing
    final String         traceSub = subsTrace;                                                                          // Sub during which this instruction was created
    final boolean          noJump;                                                                                      // The instruction will handle setting the program counter  if false

    I (boolean NoJump)                                                                                                  // Add this instruction to the code for the process
     {ai();                                                                                                             // Prevent addition of new instructions and allocations while compiling this instruction
      subInc();                                                                                                         // Count the number of instructions associated with each method
      noJump = NoJump;                                                                                                  // Ability to jump
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

    I () {this(true);}                                                                                                  // Add this instruction to the process's code assuming it will not jump

//D3 Overrides                                                                                                          // Methods that modify the behaviour of an instruction

    abstract void a ();                                                                                                 // The action to be performed by the instruction
    String        v () {return "";};                                                                                    // Verilog code
    int      traces () {return 1;}                                                                                      // Number of trace records expected
    boolean   trace () {return true;}                                                                                   // Enable tracing

    String instructionLocation () {return traceBack != null ? traceBack : traceSub  != null ? traceSub : "";}           // Trace the location at which the instruction was generated
    String instructionLocationAsComment ()                                                                              // Trace the location at which the instruction was generated as a comment
     {if (!suppressTraceComments)
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
      final String        v = suppressInstructionTracing ? v().replaceAll("\\$fd.*?;", "") : v();                       // Generate verilog and remove tracing if requested
      final StringBuilder s = new StringBuilder();                                                                      // Generated code
      if (noJump)  s.append(" pc <= pc + 1; ");                                                                         // Next instruction
      s.append(v);                                                                                                      // Generated code

      if (trace())
       {if (program().vtrace != traces())                                                                               // Complain if the wrong number of vtrace calls were generated
         {stop("Wrong number of calls to vtrace, got:", program().vtrace,
               "expected:", traces(), "at:", instructionLocation());
         }
        if (program().vtrace == 0 && !suppressInstructionTracing)                                                       // Write current location to verilog trace log if no trace was supplied and tracing is not being suppressed
         {s.append(vTrace("%8d Location: %s", "pc", "\""+instructionLocationAsComment()+"\""));
         }
       }
      return ""+s;                                                                                                      // Generated code
     }

    StringBuilder verilogCodeForOneInstruction(StringJoiner Labels, String Verilog)                                     // Verilog code for one instruction
     {final StringBuilder s = new StringBuilder();
      s.append(f("        %s : begin %s", pad(""+Labels, padLabels), pad(Verilog, padVerilog)));                        // Instruction numbers followed by code
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
      return ""+s;                                                                                                      // Generated code
     }
   }

  final class Label                                                                                                     // Label jump targets in the program
   {int offset;                                                                                                         // The instruction location to which this label applies
    Label ()    {set(); program().labels.push(this);}                                                                   // A label assigned to an instruction location
    void set () {offset = program().code.size();}                                                                       // Reassign the label to an instruction
   }

  void appendJavaTrace(String Message) {appendFile(javaTraceFile(), Message);}                                          // Append to the java trace file
  void jTrace (String Message)                                                                                          // Trace a java instruction by writing a message to the java trace file unless the instruction has suppressed tracing
   {jtraceInc();
    if (program().suppressInstructionTracing) return;                                                                   // Suppress instruction tracing
    if (!program().executing.trace()) return;                                                                           // Not tracing this instruction
    appendJavaTrace(Message+"\n");                                                                                      // Write tracing message
   }

  String vTrace (String Format, String...Message)                                                                       // Generate verilog code to write a message to the verilog trace log
   {vtraceInc();
    if (!program().compiling.trace()) return "";                                                                        // Suppress tracing for this instruction
    final StringBuilder s = new StringBuilder();
    //s.append(" traceFile = $fopen(\""+verilogTraceFile+"\", \"a\"); ");
    s.append("$fdisplay(traceFile, \""+Format+"\"");
    for(int i = 0; i < Message.length; ++i) s.append(", "+Message[i]);
    s.append(");");
    //s.append(" $fclose(traceFile);");
    return ""+s;
   }

  void initializeJavaMemory()                                                                                           // Initialize java memory
   {for(UnitMemory m : memories) for (int i = 0, N = m.size(); i < N; ++i) m.units[i] = 0;                              // Clear all of memeory to zero
   }

  void initializeJavaVars()                                                                                             // Initialize java variables so that they start with a known value despite being invalid because the valid bit is not tracked in the verilog version
   {for (Int  i : ints())  {i.i = 0;     i.v = false;}
    for (Bool b : bools()) {b.i = false; b.v = false;}
   }

  void dumpJavaMemories () {for(UnitMemory m : memories) appendJavaTrace(m.dumpAsDecimal());}                           // Dump all the memories

  void dumpJavaVars ()                                                                                                  // Dump all memories and variables to the java trace file
   {final StringBuilder s = new StringBuilder();
    for (Int  i  : ints())                                                                                              // Dump ints
     {s.append(f("Int  %8d == %8d", i.id, i.i));
      if (i.name != null) s.append(" "+i.name);
      s.append('\n');
     }
    for (Bool b : bools())                                                                                              // Dump bools
     {if (b.nd) continue;                                                                                               // Omit bools that were created as a result of testing the validity of an Int because the Verilog code does not retain this information
      s.append(f("Bool %8d == %8d", b.id, b.i ? 1 : 0));
      if (b.name != null) s.append(" "+b.name);
      s.append('\n');
     }
    appendJavaTrace(""+s);
   }

  void dumpJavaRegisters ()                                                                                             // Dump all memories and variables to the java trace file
   {final StringBuilder s = new StringBuilder();
    s.append(f("       currentPc = %8d\n",         pc-1));
    s.append(f("     sourceIntId = %8d\n",  sourceIntId()));
    s.append(f("    source2IntId = %8d\n", source2IntId()));
    s.append(f("     targetIntId = %8d\n",  targetIntId()));
    s.append(f("    sourceBoolId = %8d\n", sourceBoolId()));
    s.append(f("    targetBoolId = %8d\n", targetBoolId()));
    s.append(f("       sourceInt = %8d\n",    sourceInt()));
    s.append(f("      source2Int = %8d\n",   source2Int()));
    s.append(f("       targetInt = %8d\n",    targetInt()));
    s.append(f("      sourceBool = %8d\n",   sourceBool() ? 1 : 0));
    s.append(f("      targetBool = %8d\n",   targetBool() ? 1 : 0));
    appendJavaTrace(""+s);
   }

  void dumpJava ()                                                                                                      // Dump all memories and variables to the java trace file
   {dumpJavaMemories();
    dumpJavaVars();
    dumpJavaRegisters();
   }

  void execute ()                                                                                                       // Execute the current code
   {if (immediate()) return;                                                                                            // The code has already been executed interpretively

    if (codeSize() == 0) stop("No code to execute"); else say(f("            Code size: %,7d", codeSize()));            // Code size check
    deleteFile(javaTraceFile());                                                                                        // Clear Java trace file
    dumpProgramState("Finished");                                                                                       // Dump program state at end of execution

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
          if (jtrace == 0) jTrace(f("%8d Location: %s", currentPc, i.instructionLocationAsComment()));                  // Append location to java trace log as no tracing was performed
         }

        executing = null;                                                                                               // Show no instruction currently being executed
       }
      catch(Exception e)
       {if (executing == null) stop("Exception:", e, "while executing:", traceBack(e));
        else stop("Exception:", e, "\nin instruction:", executing.traceBack, "\nwhile executing:", traceBack(e));
       }
     }

    if (c >= maxSteps) stop("Out of steps after step:", c);                                                             // Show abnormal termination reason

    if (generateVerilog)                                                                                                // Run verilog
     {generateVerilog();                                                                                                // Generate corresponding Verilog code and run it
      if (runVerilog)                                                                                                   // Run verilog
       {deleteFile(verilogTraceFile());                                                                                 // Clear Verilog trace file
        final StringBuilder s = new StringBuilder();

        s.append(substitute("cd {f}; rm -f {n}; iverilog -g2012 -o {n} {n}.v && {t} ./{n}",                             // Construct command
                            "f", verilogTestFolder(),
                            "n", currentTestNameSuffix(),
                            "t", github_actions ? "" : "timeout 1m "));
        say(s);
        final ExecCommand x = new ExecCommand(s);
        say(x.out);

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

  void dumpProgramState (String Location)                                                                               // Dump program memories and variables
   {new I()
     {void    a()     {appendJavaTrace(Location+"\n");                   dumpJava();}
      String  v()     {return "$fwrite(traceFile, \""+Location+"\\n\");"+dumpVerilog();}                                // fdisplay gets removed by trace suppression
      boolean trace() {return false;}
     };
   }

  void dumpProgramMemories (String Location)                                                                            // Dump program memories
   {new I()
     {void    a()     {appendJavaTrace(Location+"\n");                   dumpJavaMemories();}
      String  v()     {return "$fwrite(traceFile, \""+Location+"\\n\");"+dumpVerilogMemories();}                        // fdisplay gets removed by trace suppression
      boolean trace() {return false;}
     };
   }

  void dumpProgramRegisters (String Location)                                                                           // Dump program registers
   {new I()
     {void    a()     {appendJavaTrace(Location+"\n");                   dumpJavaRegisters();}
      String  v()     {return "$fwrite(traceFile, \""+Location+"\\n\");"+dumpVerilogRegisters();}                       // fdisplay gets removed by trace suppression
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
    final int  numberOfInts =  nextIntId;                                                                               // Number of integers needed
    final int numberOfBools = nextBoolId;                                                                               // Number of bools needed

    generatePcVariableId();                                                                                             // Generate array to map pc to id of variable to be loaded or written

    try
     (final PrintWriter out = new PrintWriter(codeFile))                                                                // Write the verilog to a file
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
        for(UnitMemory m : memories)  out.write("    state_clearMemory_"+m.i()+" = "+(state++)+",\n");                  // State to clear each memory
        out.write("    state_clearInts   = "+(state++)+",\n");                                                          // State for clearing integers
        out.write("    state_clearBool   = "+(state++)+",\n");                                                          // States for clearing bools
        out.write("    state_execute     = "+(state++)+"\n");                                                           // States for executing code
        out.write("   } State;\n");
       }

      /*Execution State Variables*/out.write(substitute("""
  parameter        INT_VARS = {numberOfInts};                                                                           // Number of integer variables
  parameter       BOOL_VARS = {numberOfBools};                                                                          // Number of boolean variables
  reg                 clock;                                                                                            // Clock for chip
  reg                 reset;                                                                                            // Reset for chip
  integer                pc;                                                                                            // Program counter for stepping through user code
  integer            lastPc;                                                                                            // The instruction which started the latest flow of control block
  integer         traceFile;                                                                                            // Write verilog trace records to this file
  integer             index;                                                                                            // Index for clearing memory
  integer       sourceIntId;                                                                                            // Id of source int
  integer      source2IntId;                                                                                            // Id of source2 int
  integer       targetIntId;                                                                                            // Id of target int
  integer      sourceBoolId;                                                                                            // Id of source bool
  integer      targetBoolId;                                                                                            // Id of target bool
  integer        sourceBool;                                                                                            // Source value for a boolean  operation obtained from a variable
  integer         sourceInt;                                                                                            // Source value for an integer operation obtained from a variable
  integer        source2Int;                                                                                            // Second source value for an integer operation obtained from a variable
  integer         targetInt;                                                                                            // Computed target integer value to be loaded into a variable
  integer        targetBool;                                                                                            // Computed target boolean value to be loaded into a variable

  integer                 i[INT_VARS:0];                                                                                // Integers
  reg                     b[BOOL_VARS:0];                                                                               // Booleans

""", "numberOfInts", ""+numberOfInts, "numberOfBools", ""+numberOfBools));

      /*Reset*/out.write("""

  State state;                                                                                                          // Current state of machine

  always @(posedge clock) begin
    if (reset) begin                                                                                                    // Reset
""");
      if (memories.size() > 0) /*Reset memory*/out.write(substitute("""
      index = 0;
      state = state_clearMemory_{start};
""", "start", memories.firstElement().i()));

      else /*No memory*/out.write("""
      state = state_clearInts;
""");

      /*Initialize*/out.write(substitute("""
      index        = 0;
      pc           = 0;
       sourceIntId = 0;                                                                                                 // Id of source int
      source2IntId = 0;                                                                                                 // Id of source2 int
       targetIntId = 0;                                                                                                 // Id of target int
      sourceBoolId = 0;                                                                                                 // Id of source bool
      targetBoolId = 0;                                                                                                 // Id of target bool
        sourceBool = 0;                                                                                                 // Source value for a boolean  operation obtained from a variable
         sourceInt = 0;                                                                                                 // Source value for an integer operation obtained from a variable
        source2Int = 0;                                                                                                 // Second source value for an integer operation obtained from a variable
         targetInt = 0;                                                                                                 // Computed target integer value to be loaded into a variable
        targetBool = 0;                                                                                                 // Computed target boolean value to be loaded into a variable

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
        state_clearInts  : clearInts();
        state_clearBool  : clearBool();
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
      if (index >= INT_VARS) begin index = 0; state = state_clearBool; end
    end
  endtask

  task automatic clearBool;                                                                                             // Clear bools
    begin
      b[index] = 0;
      index = index + 1;
      if (index >= BOOL_VARS) begin index = 0; state = state_execute; end
    end
  endtask

""");

      /*dumpVars*/ out.write(dumpVerilogVariables());

      for(int i = 0; i < memories.size(); ++i)                                                                          // Actions for each memory
       {final UnitMemory m = memories.elementAt(i);
        out.write(              clearMemory(m, i < memories.size()-1 ?
         "state_clearMemory_"+memories.elementAt(i+1).i() :
         "state_clearInts"));
        out.write(   dumpVerilogMemoryInDecimal(m));

        out.write("  integer "+ m.     vReadBool() + ";\n");                                                            // Boolean read from memory
        out.write("  integer "+ m.    vWriteBool() + ";\n");                                                            // Boolean to write into memory
        out.write("  integer "+ m.      vReadInt() + ";\n");                                                            // Integer read from memory
        out.write("  integer "+ m.     vWriteInt() + ";\n");                                                            // Integer to write into memory
        out.write("  integer "+ m. vReadIntIndex() + ";\n");                                                            // Index at which to read an integer from memory
        out.write("  integer "+ m. vReadBitIndex() + ";\n");                                                            // Index within an integer from which to get a bit to make a boolean
        out.write("  integer "+ m.vWriteIntIndex() + ";\n");                                                            // Index at which to write an integer into memory
        out.write("  integer "+ m.vWriteBitIndex() + ";\n");                                                            // Index within an integer at which to set a bit to represent a boolean
       }

      for(String m : program().extraVerilogMethods) out.write(m);                                                       // Incorporate extra Verilog methods required to support generated instructions

      /*Execute*/out.write("""
  task automatic execute;                                                                                               // Execute actual code
    begin
      case(pc)
""");

      matchingInstructions.clear();                                                                                     // New base instructions
      for(I i : code) {program().compiling = i;           i.matchInstructions();}                                       // Find the base instructions
      for(I i : code) {program().compiling = i;           i.matchInstructions();}                                       // Find the base instructions
      for(I i : code) {program().compiling = i; out.write(i.generateVerilog());}                                        // Compile each instruction to Verilog

      if (true)                                                                                                         // Instruction reduction statistics
       {final int m = matchingInstructions.size(), c = code.size(), p = 100*(c-m)/c;
        say(f("Instruction reduction to: %4d, percent: %4d", m, p));
       }
      matchingInstructions.clear();                                                                                     // Release storage occupied by base instructions

      /* Execute default*/out.write("""
        default: begin
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
    catch(Exception e) {stop(e, fullTraceBack(e));}
   }

  String clearMemory(UnitMemory M, String Next)                                                                         // Verilog procedure to clear a memory
   {return substitute("""
  task automatic clearMemory_{memoryId};                                                                                // Clear memory element by element
    begin
      {memoryName}[index] = 0;
      index = index + 1;
      if (index >= MEMORY_{memoryId}) begin index = 0; state = {Next}; end
    end
  endtask
""", "memoryId", M.i(), "memoryName", M.n(), "Next", Next);
   }

  void defineArrayViaVerilogFunction(String Name, int[]Array) {defineArrayViaVerilogFunction(Name, Array, 0);}          // Define a verilog function from an array
  void defineArrayViaVerilogFunction(String Name, int[]Array, int Error)                                                // Define a verilog function from an array
   {final StringBuilder s = new StringBuilder("function automatic integer "+Name+"(input integer i);");
    s.append("""
  begin                                                                                                                 // From: defineArrayViaVerilogFunction
    case (i)
""");
    for (int i = 0; i < Array.length; ++i)
     {final int v = Array[i];
      if (v != Error) s.append(f("      %4d: %s = %4d;\n", i, Name, v));                                                // The java proves the code works so we can collapse zeros into default
     }
    s.append(f("""
      default: %s = %d;
    endcase
  end
endfunction
""", Name, Error));
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

    for(Int i : ints)                                                                                                   // Dump integers
     {if (i.name != null) s.append(substitute("""
      $fdisplay(traceFile, "Int  %8d == %8d {name}", {id}, i[{id}]);
""", "name", i.name, "id", ""+i.id));
      else s.append(substitute("""
      $fdisplay(traceFile, "Int  %8d == %8d", {id}, i[{id}]);
""", "id", ""+i.id));
     }

    for(Bool b : bools)                                                                                                 // Dump booleans
     {if (b.nd) continue;                                                                                               // Omit bools that were created as a result of testing the validity of an Int because the Verilog code does not retain this information
      if (b.name != null) s.append(substitute("""
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

  String dumpVerilogMemories ()                                                                                         // Dump verilog memories
   {final StringBuilder s = new StringBuilder();
    for(UnitMemory m : memories) s.append(m.dumpVerilogMemoryInDecimalName()+"(); ");
    return ""+s;
   }

  String dumpVerilogRegisters ()                                                                                        // Dump all verilog registers
   {final StringBuilder s = new StringBuilder();
    s.append("$fwrite(traceFile, \"       currentPc = %8d\\n\", pc          );\n");
    s.append("$fwrite(traceFile, \"     sourceIntId = %8d\\n\", sourceIntId );\n");
    s.append("$fwrite(traceFile, \"    source2IntId = %8d\\n\", source2IntId);\n");
    s.append("$fwrite(traceFile, \"     targetIntId = %8d\\n\", targetIntId );\n");
    s.append("$fwrite(traceFile, \"    sourceBoolId = %8d\\n\", sourceBoolId);\n");
    s.append("$fwrite(traceFile, \"    targetBoolId = %8d\\n\", targetBoolId);\n");
    s.append("$fwrite(traceFile, \"       sourceInt = %8d\\n\", sourceInt   );\n");
    s.append("$fwrite(traceFile, \"      source2Int = %8d\\n\", source2Int  );\n");
    s.append("$fwrite(traceFile, \"       targetInt = %8d\\n\", targetInt   );\n");
    s.append("$fwrite(traceFile, \"      sourceBool = %8d\\n\",  sourceBool );\n");
    s.append("$fwrite(traceFile, \"      targetBool = %8d\\n\",  targetBool );\n");
    return ""+s;
   }

  String dumpVerilog ()                                                                                                 // Dump verilog memory and variables
   {final StringBuilder s = new StringBuilder();
    s.append(" "+dumpVerilogMemories());
    s.append(" "+dumpVerilogVariablesName()+"();");
    s.append(" "+dumpVerilogRegisters());
    return ""+s;
   }

  String removeTracing(String V) {return suppressInstructionTracing ? V.replaceAll("(?s)\\$fd.*?;", "") : V;}           // Remove tracing if necessary


  void generatePcVariableId()                                                                                           // Generate array to map pc to id of variable to be loaded or written
   {final TreeMap<Integer,Integer>  m = pcVariableId();
    if (m.size() == 0) return;
    final int    l = m.lastKey()+1;
    final int [] a = new int[l];
    for(int i = 0; i < l; ++i)              a[i] = 0;
    for (Integer i : m.keySet()) a[i] = m.get(i);
    defineArrayViaVerilogFunction("pcVariableId", a, -1);
   }

//D1 Testing                                                                                                            // Test expected output against got output

  static void test_programming(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Int a = new Int(22);
        dumpProgramState("AAAA");
        final Int b = new Int(33);
        dumpProgramState("BBBB");
        final Int c = b.dup();
        dumpProgramState("CCCC");
        execute();
       }
     };
   }

  static void test_programming22(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex))
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
        final Bool j = i.valid();
        j.ok(true);
        execute();
       }
     };
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
        final Bool a = new Bool(true);
        final Bool b = new Bool(false);
        final Bool c = a.dup().or(b).flip().ok(false);
        final Bool d = b.dup().or(a).flip().ok(false);

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
           {dumpProgramState("AAAA");
            b.add(a.dup().inc());
            dumpProgramState("BBBB");
            new I()
             {void   a() {s.append(f("%2d  %2d\n", a.i(), b.i()));}
              int traces() {return 0;}
             };
            Continue.set();
            dumpProgramState("CCCC");
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
    final Program P = new Program(new Build().immediate(Ex))
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
           {dumpProgramState("AAAA");
            a.set(Index.Inc()).mod(2);
            dumpProgramState("BBBB");

            new If (b.set(a.ne(0)).flip())
             {void Then() {c.dec();}
              void Else() {c.inc(); c.inc();}
             };
            dumpProgramState("CCCC");
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
    final Program P = new Program(new Build().immediate(Ex))
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
    ok(P.ints.size(), 2);
    ok(Q.ints.size(), 0);
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
        a.del(-1);
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
            m.getBool(new Int(33)).ok(true);
            m.putBool(new Int(32), new Bool(true));
            m.putBool(new Int(34), new Bool(true));
            dumpProgramState("AAAA1111");
            m.getInt (new Int( 1)).ok(7);
            dumpProgramState("AAAA2222");
            ok(()->nws(M.dumpAsDecimal()), """
Memory 0
            0    1    2    3    4    5    6    7    8    9
00000000              1    7
""");

            dumpProgramState("AAAA3333");
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
            maxSteps(9_999);
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
/* Good idea but not reliable enough yet
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

  static void test_boolean(Boolean Ex)
   {sayCurrentTestName();
    final int N = 8;
    final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Int z = new Int ("z");
        final Int a = new Int ("a").set(N/2);
        final StringBuilder s = new StringBuilder();
        new ForCount(new Int(N))
         {void body(Int Index)
           {new If (a.eq(Index))
             {void Then() {new I() {void a() {s.append(f("%d     == %d\n", a.i(), Index.i()));} boolean trace() {return false;}};}
              void Else() {new I() {void a() {s.append(f("%d NOT == %d\n", a.i(), Index.i()));} boolean trace() {return false;}};}
             };
            dumpProgramState("AAAA");
            new If (a.ne(Index))
             {void Then() {new I() {void a() {s.append(f("%d     != %d\n", a.i(), Index.i()));} boolean trace() {return false;}};}
              void Else() {new I() {void a() {s.append(f("%d NOT != %d\n", a.i(), Index.i()));} boolean trace() {return false;}};}
             };
            new If (a.lt(Index))
             {void Then() {new I() {void a() {s.append(f("%d     <  %d\n", a.i(), Index.i()));} boolean trace() {return false;}};}
              void Else() {new I() {void a() {s.append(f("%d NOT <  %d\n", a.i(), Index.i()));} boolean trace() {return false;}};}
             };
            new If (a.le(Index))
             {void Then() {new I() {void a() {s.append(f("%d     <= %d\n", a.i(), Index.i()));} boolean trace() {return false;}};}
              void Else() {new I() {void a() {s.append(f("%d NOT <= %d\n", a.i(), Index.i()));} boolean trace() {return false;}};}
             };
            new If (a.gt(Index))
             {void Then() {new I() {void a() {s.append(f("%d     >  %d\n", a.i(), Index.i()));} boolean trace() {return false;}};}
              void Else() {new I() {void a() {s.append(f("%d NOT >  %d\n", a.i(), Index.i()));} boolean trace() {return false;}};}
             };
            new If (a.ge(Index))
             {void Then() {new I() {void a() {s.append(f("%d     >= %d\n", a.i(), Index.i()));} boolean trace() {return false;}};}
              void Else() {new I() {void a() {s.append(f("%d NOT >= %d\n", a.i(), Index.i()));} boolean trace() {return false;}};}
             };
           }
         };
        Check(s, """
4 NOT == 0
4     != 0
4 NOT <  0
4 NOT <= 0
4     >  0
4     >= 0
4 NOT == 1
4     != 1
4 NOT <  1
4 NOT <= 1
4     >  1
4     >= 1
4 NOT == 2
4     != 2
4 NOT <  2
4 NOT <= 2
4     >  2
4     >= 2
4 NOT == 3
4     != 3
4 NOT <  3
4 NOT <= 3
4     >  3
4     >= 3
4     == 4
4 NOT != 4
4 NOT <  4
4     <= 4
4 NOT >  4
4     >= 4
4 NOT == 5
4     != 5
4     <  5
4     <= 5
4 NOT >  5
4 NOT >= 5
4 NOT == 6
4     != 6
4     <  6
4     <= 6
4 NOT >  6
4 NOT >= 6
4 NOT == 7
4     != 7
4     <  7
4     <= 7
4 NOT >  7
4 NOT >= 7
""");
        dumpProgramState("AAAA");
        execute();
       }
     };
   }

  static void test_boolean()
   {          test_boolean(true);
              test_boolean(false);
   }

  static void test_ifInc(Boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Int  a = new Int(10);
        new ForCount(new Int(10))
         {void body(Int Index)
           {final Bool b = new Bool(false);
            new If (b)
             {void Then() {final Int t = new Int();}
              void Else()
               {new If (b)
                 {void Then() {final Int t = new Int();}
                  void Else() {final Int i = new Int(10); i.inc(); i.ok(11); a.inc(); a.ok(Index.Add(11));}
                 };
               }
             };
           }
         };
        dumpProgramState("AAAA");
        execute();
       }
     };
   }

  static void test_ifInc()
   {          test_ifInc(true);
              test_ifInc(false);
   }

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_programming();
    test_andOr();
    test_add();
    test_fibonacci();
    test_mod();
    test_incremental();
    test_remote();
    test_copy();
    test_memory();
    test_memoryNegative();
    test_memoryRef();
    //test_procedureCall();
    test_defineArrayViaVerilogFunction();
    test_lastInstructionBase();
    test_variables();
    test_boolean();
    test_ifInc();
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
//https://github.com/philiprbrenan/btreeList/compare/oldSha...newSha
//https://github.com/philiprbrenan/btreeList/compare/4ccd0f2a...0f6a014517ba
