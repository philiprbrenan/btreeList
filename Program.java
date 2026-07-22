//----------------------------------------------------------------------------------------------------------------------
// Machine level programming in Java
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;
import java.util.function.*;
import java.nio.*;
import java.nio.file.*;

//D1 Construct                                                                                                          // Generate the Btree algorithm in Verilog from the equivalent java code to produce the kernel of "Database on a Chip"

public class Program extends Test                                                                                       // Develop and test a java program to describe a chip and emulate its operation.
 {final boolean               suppressInstructionTracing = true;                                                        // Do not write a trace record for each instruction - the dump of program state at the end of the run will be the test of whether the program ran as expected
  final boolean                    suppressTraceComments = true;                                                        // Add trace comments to trace output to locate the point in the java code at which the verilog was generated - requires a lot of memory
  final boolean                     compressInstructions = true;                                                        // Compress out identical instructions
  final boolean                compressInstructionLabels = true;                                                        // Reduce the instruction loop case statement by using an array to find the first instruction in the equivalence class associated with each instruction and recording that single instruction id as the sole label for each case statement possibilities
  final boolean                          generateVerilog = true;                                                        // Generate verilog version of each program
  final boolean                               runVerilog = true;                                                        // Execute  verilog version of each program
  final boolean              suppressNamesInInstructions = true;                                                        // Include names in instructions
  final int                               verilogTimeOut = 4000;                                                        // Time out a verilog run after this many seconds if running locally
        int                                     maxSteps = 99_999;                                                      // Number of steps permitted in code execution - this provides some protection against endless loops during development

  final static String                      verilogFolder = "verilog/";                                                  // Verilog folder
  final static String                   verilogTraceFile = fe("traceVerilog", "txt");                                   // Verilog trace file
  final static String                      javaTraceFile = fe("traceJava",    "txt");                                   // Java trace file
  final static String                      verilogSuffix = "v";                                                         // Suffix for verilog files
  final static String                  verilogArrayFiles = "array";                                                     // Suffix for verilog files containing array definitions
  final static int padName = 12, padCR = 16,  padVerilog = 64;                                                          // Padding for components of the generated verilog code

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
  final VerilogArrays                      verilogArrays = new VerilogArrays();                                         // Verilog array definitions
  final TreeMap<Integer,Integer>              pcConstant = new TreeMap<>();                                             // Each instruction touches at most either the one variable or the one memory location given by this map
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

  void               code () {}                                                                                         // Override to provide some code for this program
  boolean       immediate () {return program().immediate;}                                                              // Executing immediately via interpretation
  boolean     isExecuting () {return program().executing != null;}                                                      // Executing machine code
  Program         program () {return parentProgram;}                                                                    // Address this program
  void     executingCheck () {if (!isExecuting()) stop("Not executing");}                                               // Confirm that code is being executed and that consequently an instruction should be executed otherwise complain
  void parentProgramCheck () {if (program() != program().program()) stop("Parent program not set to parent program");}  // Check that code is being written to the expected program

  void  ai ()                                                                                                           // An executing program cannot be extended by adding new data or instructions
   {final I      i = executing();
    final String m = immediate() ? "immediate" : "delayed";
    if (i != null) stop("Allocation within an instruction while executing in", m, "mode:", i.traceBack, "====");
   }

  void  rx ()                                                                                                           // This register can only be accessed during execution
   {final I x = executing();
    if (!immediate() && x == null)
     {stop("Control register can only be accessed during execution:", x.traceBack, "====");
     }
   }

  void  rc ()                                                                                                           // This register can only be accessed during compilation
   {final I x = executing();
    if (x != null)
     {stop("Control registers can only be accessed during compilation:", x.traceBack, "====");
     }
   }

  Program maxSteps (int MaxSteps) {program().maxSteps = MaxSteps; return this;}                                         // Set number of steps

  I compiling()    {return program().compiling;}                                                                        // Instruction currently being compiled
  I executing()    {return program().executing;}                                                                        // Instruction currently being executed
  I compiling(I I) {return program().compiling = I;}                                                                    // Instruction currently being compiled
  I executing(I I) {return program().executing = I;}                                                                    // Instruction currently being executed

  Stack<Int>  ints ()           {return program().ints;}
  Stack<Bool> bools ()          {return program().bools;}
  Stack<UnitMemory> memories () {return program().memories;}

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
  boolean sourceBool(boolean V) {return program().    sourceBool = V;}
  int      targetInt(int V)     {targetIntValid (true); return program().targetInt  = V;}
  boolean targetBool(boolean V) {targetBoolValid(true); return program().targetBool = V;}

  boolean targetBoolValid()          {return program().targetBoolValid;}
  boolean targetBoolValid(boolean V) {return program().targetBoolValid = V;}

  boolean targetIntValid ()          {return program().targetIntValid;}
  boolean targetIntValid (boolean V) {return program().targetIntValid = V;}

  void initializeRegisters()                                                                                            // Initialize registers
   {currentPc(0); sourceIntId(0); source2IntId(0); targetIntId(0); sourceBoolId(0); targetBoolId(0);
    sourceInt(0); source2Int(0); targetInt(0);
    sourceBool(false); targetBool(false);
   }

  TreeMap<Integer,Integer> pcConstant ()    {return program().pcConstant;}                                              // Instruction number to variable or memory
  VerilogArrays            verilogArrays () {return program().verilogArrays;}                                           // Verilog array definitions

  void pcConstant(I I, Label Target) {pcConstant().put(I.instructionNumber, Target.offset);}                            // Save a constant label into the instruction to constant map
  void pcConstant(I I, int   Target) {pcConstant().put(I.instructionNumber, Target);}                                   // Save a constant integer into the instruction to constant map

  String pName (String Text) {return pad(Text, padName   );}                                                            // Pad Verilog names
  String pCR (  String Text) {return pad(Text, padCR     );}                                                            // Pad Verilog control register names
  String pExpr (String Text) {return pad(Text, padVerilog);}                                                            // Pad Verilog expressions

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
        final Bool   done = index.ge(End);                                                                              // Start of loop - make sure the index is still in range - we will use the side effect of this instruction in the next instruction
        index.T();                                                                                                      // Load index
        final I S = new I(false)                                                                                        // Start of loop - make sure the index is still in range
         {void   a()   {if (index.i() >= End.i()) program().pc = end.offset;}                                           // Index out of range
          String v()   {return "if (targetBool) pc <= array_pcConstant[pc]; else pc <= pc + 1;";}                       // Terminate loop when index is out of range relying on the side effect of the previous instruction having set target bool
          int traces() {return 0;}
         };
        cont.clear();                                                                                                   // Terminate unless told otherwise
        body(index, cont);                                                                                              // Execute the loop
        index.inc();                                                                                                    // Increment loop counter
        cont.T();                                                                                                       // Load continue
        final I E = new I(false)
         {void   a() {program().pc = cont.b() ? start.offset : end.offset;}                                             // Continue execution of the loop as long as requested
          String v()
           {return "if (targetBool) pc <= array_pcConstant[pc]; else pc <= pc + 1;";}
          int traces() {return 0;}
         };
        end.set();                                                                                                      // End of the loop
        pcConstant(S, end);                                                                                             // Set end of loop jump now we know its target
        pcConstant(E, start);                                                                                           // Store jump to restart the loop in the instruction to constants map
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
        final Bool   done = index.ge(End);                                                                              // Start of loop - make sure the index is still in range - we will use the side effect of this instruction in the next instruction
        index.T();                                                                                                      // Load index
        final I S = new I(false)                                                                                        // Start of loop - make sure the index is still in range
         {void   a()   {if (index.i() >=  End.i()) program().pc = end.offset;}                                          // Index out of range
          String v()   {return "if (targetBool) pc <= array_pcConstant[pc]; else pc <= pc + 1;";}                       // Terminate the loop when the index is out of range. The if statement relies on the side effect of the previous instruction having set the target boolean value
          int traces() {return 0;}
         };
        body(index);                                                                                                    // Execute the loop
        index.inc();                                                                                                    // Increment loop counter
        final I E = new I(false)                                                                                        // Restart loop
         {void   a()   {program().pc = start.offset;}
          String v()   {return "pc <= array_pcConstant[pc];";}
          int traces() {return 0;}
         };
        end.set();                                                                                                      // End of the loop
        pcConstant(S, end);                                                                                             // Set end   of loop jump now we know its target
        pcConstant(E, start);                                                                                           // Set start of loop jump now we know its target
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

        final I Then = new I(false)                                                                                     // Jump to else if condition is false
         {void   a() {if (!Condition.b()) program().pc = lse.offset;}
          String v()
           {return "if (!targetBool) pc <= array_pcConstant[pc]; else pc <= pc + 1;";
           }
          int traces() {return 0;}
         };
        Then();                                                                                                         // Then body
        final I Else = new I(false)                                                                                     // Jump over else to end
         {void     a() {program().pc  = end.offset;}
          String   v() {return "pc <= array_pcConstant[pc];";}
          int traces() {return 0;}
         };
        lse.set();                                                                                                      // Start of else
        Else();                                                                                                         // Else body
        end.set();                                                                                                      // End of the if statement
        pcConstant(Then, lse);                                                                                          // Set then jump now we know its target
        pcConstant(Else, end);                                                                                          // Set else jump now we know its target
       }
     }

    If (Bint Condition) {this(Condition.b);}                                                                            // If from boolean integer

    abstract void Then ();                                                                                              // Then clause
             void Else () {}                                                                                            // Else clause
   }

//D1 Data                                                                                                               // Operations on boolean and integer data

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

        final I i = new I()                                                                                             // Load id of variable
         {void   a() {loadId(id);                                    jTrace(f("%8d "+ri+" = %8d",  pc(),   id));}
          String v() {return pCR(ri) + " <= array_pcConstant[pc]; "+ vTrace(  "%8d "+ri+" = %8d", "pc", ""+id);}
         };

        pcConstant(i, id);                                                                                              // Id of variable being addressed by these instructions

        new I()                                                                                                         // Load source value
         {void   a() {loadValue(B.i);                      jTrace(f("%8d "+rv+" %8d",  pc(),  B.i ? 1 : 0));}
          String v() {return pCR(rv) + " <= b["+ri+"]; " + vTrace(  "%8d "+rv+" %8d", "pc",  "b["+ ri +"]");}
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
       {void   a() {sourceBool(I);                           jTrace(f("%8d boolLoadConstant %8d",  pc(),   v));}
        String v() {return pCR("sourceBool") + " <= "+v+"; "+vTrace(  "%8d boolLoadConstant %8d", "pc", ""+v);}
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
       {final String f = "%8d writeBool %8d = %8d";
        void   a() {i = targetBool(); v = targetBoolValid();           jTrace(f(f,  pc(), b.id,           b.i ? 1 : 0));}
        String v() {return pCR("b[targetBoolId]") + " <= targetBool; "+vTrace(  f, "pc", "targetBoolId", "targetBool");}
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
     {return pCR("targetBool")+ " <= "+pExpr(""+Value+";")+" "+
                        vTrace("%8d bool %8d = %8d",   "pc",        "targetBoolId", ""+Value);
     }
    void jtrace ()     {jTrace(f("%8d bool %8d = %8d",  currentPc(), id,             targetBool() ? 1 : 0));}           // Trace a java    boolean operation

    public String toString ()                                                                                           // Print the boolean
     {final String u = "undefined_Bool";
      if (name == null) return v ? ""+i       : u;
      else              return v ? name+"="+i : u+": "+name;
     }

    String   vn ()                                                                                                      // Verilog name of this boolean variable
     {final String n = suppressNamesInInstructions ? "" : name != null ? "/*"+name+"*/" : "";
      return pName("b["+id+"]"+n);
     }

    Bool ok (boolean Value)                                                                                             // Memory trace from java makes this test redundant in Verilog if the Verilog trace matches the java trace and so there will be an empty instruction generated in the verilog to "regulate the service"
     {final  Bool got = this;
      new I()
       {void a()
         {if (!got.v) stop("Invalid Bool being tested at:", executing().instructionLocation());
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
         {if (!got.v) stop("Invalid Bool being tested at:", executing().instructionLocation());
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

    Int ()           {ai(); del(-1);        ints().push(this);}                                                         // Constructors without name. Invalidate the integer. The invalidation is done in such a way as to make the instruction trace sequences for java and Verilog match. Recall that the Verilog integers do not carry a valid flag with them as this would be a waste of resources given that the correctness of the algorithm has been already been established by successfully executing the tests associated with the java version . The integers used in the java version do carry a valid flag which has been helpful in validating the correctness of this implementation of the btree algorithm before handing it off to Verilog.

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
    Int ie (Ops Op, Int I) {T(); I.S(); new I() {void a() {ex(Op, I);} String v() {return ev(Op, I);}}; W(); return this;}
    Int ie (Ops Op, int I)                                                                                              // Selectively loaded target, store constant for this instruction in the constants map
     {T(Op);                                                                                                            // Instruction to load target details if needed for the operation
      final I i = new I() {void a() {ex(Op, I);} String v() {return ev(Op, I);}};                                       // Perform operation
      W();                                                                                                              // Write results back into a variable
      pcConstant(i, I);                                                                                                 // Record the constant used in this operation in the map from instructions to constants used
      return this;                                                                                                      // The current integer
     }

    abstract class LoadSourceOrTarget
     {LoadSourceOrTarget(Int I, String RegisterId, String RegisterValue, boolean LoadValue)                             // Load source or target index and possibly value via integer id
       {final String ri = RegisterId;                                                                                   // Shorten name
        final String rv = RegisterValue;                                                                                // Shorten name

        final I i = new I()                                                                                             // Load index of integer
         {final String c = pExpr("array_pcConstant[pc];");
          void   a() {loadId(id);                    jTrace(f("%8d LST1 "+ri+" = %8d",  pc(),   id));}
          String v() {return pCR(ri) + " <= "+c+" "+ vTrace(  "%8d LST1 "+ri+" = %8d", "pc", ""+id) ;}
         };
        pcConstant(i, I.id);                                                                                            // Id of variable being addressed by these instructions

        if (LoadValue) new I()                                                                                          // Value of integer
         {final String v = pExpr("i["+ri+"];");
          void   a() {loadValue(I.i);               jTrace(f("%8d LST2 "+rv+" = %8d",  pc(),  I.i));}
          String v() {return pCR(rv)+" <= "+v+" " + vTrace(  "%8d LST2 "+rv+" = %8d", "pc",  "i["+ri+"]");}
         };
       }
      LoadSourceOrTarget(Int I, String RegisterId, String RegisterValue) {this(I, RegisterId, RegisterValue, true);}    // Load source or target value via integer id
      int pc() {return currentPc();}                                                                                    // Address of this instruction
      abstract void loadId   (int I);                                                                                   // Override to save delta from last integer base
      abstract void loadValue(int V);                                                                                   // Override to save the current value of the integer variable
     }

    abstract class LoadConstant
     {LoadConstant(int I, String Register)                                                                              // Load source constant into source register to increase compressibility of instructions
       {final String ac = pCR(Register) + pExpr(" <= array_pcConstant[pc];") + " ";                                     // Assign the constant to the source register
        final I i = new I()
         {void   a() {load(I);    jTrace(f("%8d "+Register+" constant %8d",  currentPc(), I));}
          String v() {return ac + vTrace(  "%8d "+Register+" constant %8d", "pc",      ""+I);}
         };
        pcConstant(i, I);                                                                                               // Save constant in instruction to constant map
       }
      int pc() {return currentPc();}                                                                                    // Address of this instruction
      abstract void load(int C);                                                                                        // Override to load the constant value of the integer variable being loaded into a java variable
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

    void T (Ops Op)                                                                                                     // Save target delta without loading value
     {new LoadSourceOrTarget(this, "targetIntId", "targetInt", Op != Ops.set && Op != Ops.del)
       {void loadId   (int I) {targetIntId(I);}
        void loadValue(int V) {targetInt  (V);}
       };
     }

    void W ()                                                                                                           // Write result back into variable
     {final Int w = this;
      new I()                                                                                                           // Load value
       {final String f = "%8d writeInt %8d = %8d";
        void   a() {            i = targetInt();             v = targetIntValid(); jTrace(f(f,  currentPc(),  targetIntId(), targetInt()));}
        String v() {return pCR("i[targetIntId]") + " <= "+pExpr("targetInt;")+" " +vTrace(  f, "pc",         "targetIntId", "targetInt");}
       };
     }

    Int ex (Ops Op)                                                                                                     // Execute a monadic integer operation
     {executingCheck();
      x();
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
     {final String        n = "targetInt", c = "array_pcConstant[pc]";                                                  // The constant will be stored in the instruction to constant map
      final StringBuilder s = new StringBuilder();
      switch (Op)
       {case set  -> {s.append(        c);}
        case del  -> {s.append(        c);}
        case add  -> {s.append(n+" + "+c);}
        case sub  -> {s.append(n+" - "+c);}
        case mul  -> {s.append(n+" * "+c);}
        case div  -> {s.append(n+" / "+c);}
        case mod  -> {s.append(n+" % "+c);}
        case add2 -> {s.append(n+" + "+c+"*2");}
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

    final String atf = "%8d assign targetInt = %8d";                                                                    // Trace format for an assign statement
    String vExecuteAndTrace (String Value)                                                                              // Execute and trace an integer operation in Verilog
     {return pCR("targetInt") + " <= "+pExpr(Value+";")+ vTrace(atf, "pc",           Value);
     }

    void jtrace ()                                      {jTrace(f(atf,  currentPc(), targetInt()));}                    // Trace the integer operation in Java

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
      targetBoolValid(true);
      switch(Op)
       {case eq -> targetBool(sourceInt() == source2Int());
        case ne -> targetBool(sourceInt() != source2Int());
        case le -> targetBool(sourceInt() <= source2Int());
        case lt -> targetBool(sourceInt() <  source2Int());
        case ge -> targetBool(sourceInt() >= source2Int());
        case gt -> targetBool(sourceInt() >  source2Int());
        default -> stop("Op not implemented:", Op);
       }
      B.jtrace();
     }

    void bex (Ops Op, Bool B, Int I) {I.x(); bex(Op, B, I.i);}                                                          // Boolean comparison between two integer variables

    String bev (Ops Op, Bool B)                                                                                         // Boolean comparison between two integers
     {final StringBuilder s = new StringBuilder();
      final String a = pCR("sourceInt"), b = pCR("source2Int");
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
      return pName("i["+id+"]"+n);
     }

    Int ok (int Value)                                                                                                  // Check the integer. There is no corresponding check in Verilog other than the execution logs matching so there will be an empty instruction generated in the verilog to "regulate the service"
     {final Int got = this;
      new I()
       {void        a()
         {if (!got.v) stop("Invalid Int being tested at:", executing().instructionLocation());
          Test.ok(i, Value);
         }
        boolean trace() {return false;}                                                                                 // No need to test  under Verilog as long as all data accesses match
       };
      return this;
     }

    Int ok (Int Value)                                                                                                  // Test an Integer. The value expected and the value got must be valid during the java execution because the verilog execution deliberately removes this information on the basis that the java code is definitive and so if the verilog trace matches the java trace the verilog code is working correctly. The purpose of the validity bit is to internally track whether the integer was ever set during program execution, it is not to convey application information. If an integer with an attached validity bit is required in application logic then Bint should be used.  This feature does not exist in the Verilog code and so there will be an empty instruction generated in the verilog to "regulate the service"
     {final Int got = this;
      if (immediate() && !Value.v) stop("Invalid expected Int has been supplied for testing");
      new I()
       {void    a    ()
         {if (!got.v) stop("Invalid Int being tested at:", executing().instructionLocation());
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
     {new If (b.Flip()) {void Then() {stop("Requested int component from unset Bint");}};                               // Complain if there is no integer component to return
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
      final Stack<UnitMemory> m = memories(); id = m.size(); m.push(this);                                              // Give the memory a unique identifier and save it in the main program
     }

    int size()  {return units.length;}                                                                                  // Size of memory
    String i () {return ""+id;}                                                                                         // Number of memory a string for use in writing verilog
    String n () {return "m_"+id;}                                                                                       // Name of memory
    String n (String Index)         {return n() + "["+Index+"]";}                                                       // Name of indexed memory
    String n (String I1, String I2) {return n() + "["+I1+"]["+I2+"]";}                                                  // Name of indexed memory

    void im(Int  I) {pcConstant(compiling(), I.id);}                                                                    // Save the integer variable used for this memory access at this instruction
    void im(Bool B) {pcConstant(compiling(), B.id);}                                                                    // Save the boolean variable used for this memory access at this instruction

    String      vReadBool()      {return n() + "_readBool     ";}                                                       // Boolean read from memory
    String     vWriteBool()      {return n() + "_writeBool    ";}                                                       // Boolean to write into memory
    String       vReadInt()      {return n() + "_readInt      ";}                                                       // Integer read from memory
    String      vWriteInt()      {return n() + "_writeInt     ";}                                                       // Integer to write into memory
    String  vReadIntIndex()      {return n() + "_readIntIndex ";}                                                       // Index at which to read an integer from memory
    String  vReadBitIndex()      {return n() + "_readBitIndex ";}                                                       // Index within an integer from which to get a bit to make a boolean
    String vWriteIntIndex()      {return n() + "_writeIntIndex";}                                                       // Index at which to write an integer into memory
    String vWriteBitIndex()      {return n() + "_writeBitIndex";}                                                       // Index within an integer at which to set a bit to represent a boolean

    String       readIntV()      {return vReadInt () + "<= "+n()+"["+vReadIntIndex()+"];                            "+              vTrace(  "%8d readInt       %8d",     "pc", n(vReadIntIndex ())                                 );}
    String      readBoolV()      {return vReadBool() + "<= "+n()+"["+vReadIntIndex()+"]["+vReadBitIndex()+"];       "+              vTrace(  "%8d readBool      %8d",     "pc", n(vReadIntIndex (),  vReadBitIndex ())              );}
    String      writeIntV()      {return n()+"["+vWriteIntIndex()+"]                       <= " + vWriteInt () + "; "+              vTrace(  "%8d writeInt      %8d<%8d", "pc", n(vWriteIntIndex()), vWriteInt     ()               );}
    String     writeBoolV()      {return n()+"["+vWriteIntIndex()+"]["+vWriteBitIndex()+"] <= " + vWriteBool() + "; "+              vTrace(  "%8d writeBool     %8d<%8d", "pc", n(vWriteIntIndex(),  vWriteBitIndex()), vWriteBool());}
    String  readIntIndexV(Int I) {im(I); return vReadIntIndex () + "<= i[array_pcConstant[pc]];                     "+              vTrace(  "%8d readIntIndex  %8d=%8d", "pc", ""+I.id, I.vn());}
    String  readBitIndexV(Int I) {im(I); return vReadBitIndex () + "<= i[array_pcConstant[pc]];                     "+              vTrace(  "%8d readBitIndex  %8d=%8d", "pc", ""+I.id, I.vn());}
    String writeIntIndexV(Int I) {im(I); return vWriteIntIndex() + "<= i[array_pcConstant[pc]];                     "+              vTrace(  "%8d writeIntIndex %8d=%8d", "pc", ""+I.id, I.vn());}
    String writeBitIndexV(Int I) {im(I); return vWriteBitIndex() + "<= i[array_pcConstant[pc]];                     "+              vTrace(  "%8d writeBitIndex %8d=%8d", "pc", ""+I.id, I.vn());}

    void         readIntJ()      {readInt  = units[readIntIndex];                                                                   jTrace(f("%8d readInt       %8d",      pc(), readInt          ));}
    void        readBoolJ()      {readBool = getBit(units[readIntIndex], readBitIndex);                                             jTrace(f("%8d readBool      %8d",      pc(), readBool  ? 1 : 0));}
    void        writeIntJ()      {final int i = writeIntIndex, p = units[i]; units[i] = writeInt;                                   jTrace(f("%8d writeInt      %8d<%8d",  pc(), p, writeInt));}
    void       writeBoolJ()      {final int i = writeIntIndex, b = writeBitIndex, p = units[i]; units[i] = setBit(p, b, writeBool); jTrace(f("%8d writeBool     %8d<%8d",  pc(), getBit(p, b) ? 1 : 0, writeBool ? 1 : 0));}
    void    readIntIndexJ(Int I) {readIntIndex  = I.i();                                                                            jTrace(f("%8d readIntIndex  %8d=%8d",  pc(),   I.id, I.i()));}
    void    readBitIndexJ(Int I) {readBitIndex  = I.i();                                                                            jTrace(f("%8d readBitIndex  %8d=%8d",  pc(),   I.id, I.i()));}
    void   writeIntIndexJ(Int I) {writeIntIndex = I.i();                                                                            jTrace(f("%8d writeIntIndex %8d=%8d",  pc(),   I.id, I.i()));}
    void   writeBitIndexJ(Int I) {writeBitIndex = I.i();                                                                            jTrace(f("%8d writeBitIndex %8d=%8d",  pc(),   I.id, I.i()));}

    int pc() {return currentPc();}

    int     setBit(int X, int I, boolean V) {return  X & ~(1 << I) | (V ? 1 : 0) << I;}                                 // Set a bit in an integer
    boolean getBit(int X, int I)            {return (X >> I & 1) != 0;}                                                 // Get a bit from an integer

    String dumpVerilogMemoryInDecimalName() {return "dumpDecimal_"+id;}                                                 // Name of the verilog routine to dump this memory in decimal

    UnitMemory copy (UnitMemory SourceMemory, Int SourceOffset, Int TargetOffset, int Width)                            // Copy the specified memory
     {subStart("Program.UnitMemory.copy");
      final UnitMemory S = SourceMemory;
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
           {final String f = "%8d writeInt=readInt %8d";
            void   a() {         writeInt       =   S. readInt;         jTrace(f(f,  pc(),   writeInt));}               // Java updates variables immediately so their value can be used later in the same expression
            String v() {return vWriteInt() + " <= "+S.vReadInt() + "; "+vTrace(  f, "pc",  S.vReadInt());}              // Verilog updates at the end of the block so we have to supply the original expression
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
       {void   a() {writeInt = 0;                  jTrace(f("%8d writeInt=0",  pc()));}
        String v() {return vWriteInt() + " <= 0; "+vTrace(  "%8d writeInt=0", "pc"  );}
       };
      new I()                                                                                                           // Write into target memory
       {void   a() {       writeIntJ();}
        String v() {return writeIntV();}
       };
      subFinish();
      return this;
     }

    UnitMemory clear ()                                                                                                 // Clear memory in Java
     {subStart("Program.UnitMemory.clear(I)");
      new ForCount(new Int(size())) {void  body(Int Index) {clearUnit(Index);}};
      subFinish();
      return this;
     }

    UnitMemory clear (Int Start, int Width)                                                                             // Clear memory range in Java
     {subStart("Program.UnitMemory.clear(II)");
      new ForCount (Start, Start.Add(Width)) {void  body(Int Index) {clearUnit(Index);}};
      subFinish();
      return this;
     }

    Int getInt (Int I)                                                                                                  // Get the int at the indicated position
     {final Int r = new Int();
      new I()                                                                                                           // Set index
       {void   a() {              readIntIndexJ(I);}
        String v() {im(I); return readIntIndexV(I);}
       };
      new I()                                                                                                           // Read from memory
       {void   a() {        readIntJ();}
        String v() {return readIntV();}
       };
      new I()                                                                                                           // Set target index
       {final String f = "%8d ReadInt from Memory %8d = %8d";
        void   a() {r.i = readInt; r.v = true;                                  jTrace(f(f,  pc(),   r.id,                     I.id));}
        String v() {im(r); return "i[array_pcConstant[pc]] <= "+vReadInt()+"; "+vTrace(  f, "pc",  "array_pcConstant[pc]", ""+I.id);}
       };
      return r;
     }

    Bool getBool (Int I, Int J)                                                                                         // Get the bit in the specified byte at the specified position within the byte
     {Bool r = new Bool();
      new I()                                                                                                           // Set int index
       {void   a() {       readIntIndexJ(I);}
        String v() {im(I); return readIntIndexV(I);}
       };
      new I()                                                                                                           // Set bit index
       {void   a() {       readBitIndexJ(J);}
        String v() {im(J); return readBitIndexV(J);}
       };
      new I()                                                                                                           // Read from memory
       {void   a() {       readBoolJ();}
        String v() {return readBoolV();}
       };
      new I()                                                                                                           // Set target index
       {final String f = "%8d ReadBool from Memory %8d = %8d";
        void   a() {r.i = readBool; r.v = true;                                  jTrace(f(f,  pc(),   r.id,                   readBool ? 1 : 0));}
        String v() {im(r); return "b[array_pcConstant[pc]] <= "+vReadBool()+"; "+vTrace(  f, "pc",   "array_pcConstant[pc]", vReadBool());}
       };
      return r;
     }

    Bool getBool (Int I) {return getBool(I.Div(Integer.SIZE), I.Mod(Integer.SIZE));}                                    // Get the bit at the bit indexed location

    UnitMemory putInt (Int I, Int J)                                                                                    // Write to the indexed memory location the value of the specified source integer
     {new I()                                                                                                           // Set target index of memory to be written to
       {void   a() {              writeIntIndexJ(I);}
        String v() {im(I); return writeIntIndexV(I);}
       };
      new I()                                                                                                           // Read from source integer
       {final String f = "%8d writeInt2 %8d = %8d < %8d";
        void   a() {final int p = writeInt; writeInt = J.i();                  jTrace(f(f,  pc(),  writeIntIndex,         J.i,      p));}
        String v() {im(J); return vWriteInt() + "<= i[array_pcConstant[pc]]; "+vTrace(  f, "pc", vWriteIntIndex(),  "i["+J.id+"]", vWriteInt());}
       };
      new I()                                                                                                           // Write source integer value into target memory at indexed location
       {void   a() {       writeIntJ();}
        String v() {return writeIntV();}
       };
      return this;
     }

    UnitMemory putBool (Int I, Int J, Bool K)                                                                           // Set the bit at the indicated position in the byte at the specified position to the specified value
     {new I()                                                                                                           // Set target index
       {void   a() {              writeIntIndexJ(I);}
        String v() {im(I); return writeIntIndexV(I);}
       };
      new I()                                                                                                           // Set target index
       {void   a() {              writeBitIndexJ(J);}
        String v() {im(J); return writeBitIndexV(J);}
       };
      new I()                                                                                                           // Set write from read
       {final String f = "%8d writeBool2 %8d, %8d = %8d < %8d";
         void   a() {writeBool = K.b();                                         jTrace(f(f,  pc(),  writeIntIndex,    writeBitIndex,        K.i ? 1 : 0,  writeBool ? 1 : 0));}
        String v() {im(K); return vWriteBool() + "<= b[array_pcConstant[pc]]; "+vTrace(  f, "pc", vWriteIntIndex(), vWriteBitIndex(), "b["+K.id+"]", "b["+K.id+"]");}
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

//D3 Verilog                                                                                                            // Verilog representation of memory

    String index ()         {return "index_memory_"+id;}                                                                // Integer to index this memory
    String sizeParameter () {return "MEMORY_"+id;}                                                                      // Amount of memory
   }

  interface Locatable {Bint getLocation();}                                                                             // The location of an object in memory

  String dumpMemory () {return program().unitMemory.dumpAsDecimal();}                                                   // Dump memory in decimal format

  String saveMemories ()                                                                                                // Save all the memories to an array of strings
   {final StringJoiner j = new StringJoiner(", ");
    for (UnitMemory m : memories()) j.add(q(m.save()));
    return "{"+j+"}";
   }

  void reloadMemories (String[]Dump)                                                                                    // Reload saved memories
   {if (Dump.length != memories().size())                                                                               // Check number of memories match
     {stop("Number of memories supplied and present differ:", Dump.length, memories().size());
     }
    for (int i = 0; i < Dump.length; ++i) memories().elementAt(i).reload(Dump[i]);                                      // Reload each memory
   }

//D1 Machine Code                                                                                                       // Generate machine code instructions to implement the program

//D2 Instruction                                                                                                        // An instruction represents code to be executed by a process in a single clock cycle == process step

  abstract class I                                                                                                      // Instructions implement the action of a program
   {final int instructionNumber = program().code.size();                                                                // The number of this instruction
    final String      traceBack = suppressTraceComments ?  null : traceBack();                                          // Line at which this instruction was created - suppressible because it imposes a lot of extra processing
    final String       traceSub = subsTrace;                                                                            // Sub during which this instruction was created
    final boolean        noJump;                                                                                        // The instruction will handle setting the program counter  if false

    I (boolean NoJump)                                                                                                  // Add this instruction to the code for the process
     {ai();                                                                                                             // Prevent addition of new instructions and allocations while compiling this instruction
      subInc();                                                                                                         // Count the number of instructions associated with each method
      noJump = NoJump;                                                                                                  // Ability to jump
      if (immediate())                                                                                                  // Execute instruction immediately via interpretation if in immediate execution mode
       {executing(this);                                                                                                // Show that we are executing an instruction
        program().jtrace = 0;
        a();
        if (trace() && program().jtrace != traces())                                                                    // Check traces written if tracing
         {stop("Wrong number of java traces generated, got: ", program().jtrace,
               "expected:", traces(), "at:", instructionLocation());
         }
        executing(null);                                                                                                // Show that we are no longer executing an instruction
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

    String interiorVerilog ()                                                                                           // Generate the interior verilog code for an instruction
     {program().vtrace = 0;                                                                                             // Count number of trace calls made in instruction
      final String        v = removeTracing(v());                                                                       // Generate verilog and remove tracing if requested
      final StringBuilder s = new StringBuilder();                                                                      // Generated code
      if (noJump)  s.append("pc <= pc + 1; ");                                                                          // Next instruction
      s.append(v);                                                                                                      // Generated code

      if (trace())
       {if (program().vtrace != traces())                                                                               // Complain if the wrong number of vtrace calls were generated
         {stop("Wrong number of calls to vtrace, got:", program().vtrace,
               "expected:", traces(), "at:", instructionLocation());
         }
        if (program().vtrace == 0 && !suppressInstructionTracing)                                                       // Write current location to verilog trace log if no trace was supplied and tracing is not being suppressed
         {s.append(vTrace("%8d Location: %s", "pc", q(instructionLocationAsComment())));
         }
       }
      return ""+s;                                                                                                      // Generated code
     }

    String formatVerilogCode (String Verilog)                                                                           // Verilog code for an instruction
     {final StringBuilder s = new StringBuilder();
      s.append(" : begin "+pExpr(Verilog));                                                                             // Instruction numbers followed by code
      s.append(" end");
      s.append(instructionLocationAsComment());                                                                         // Trace java program location that generated the first instance of the instruction so that the verilog code can be tied back to the java code
      s.append("\n");
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
   {++program().jtrace;                                                                                                 // Count trace records written
    if (program().suppressInstructionTracing) return;                                                                   // Suppress instruction tracing
    if (!executing().trace()) return;                                                                                   // Not tracing this instruction
    appendJavaTrace(Message+"\n");                                                                                      // Write tracing message
   }

  String vTrace (String Format, String...Message)                                                                       // Generate verilog code to write a message to the verilog trace log
   {++program().vtrace;
    if (!compiling().trace()) return "";                                                                                // Suppress tracing for this instruction
    final StringBuilder s = new StringBuilder();
    s.append("$fdisplay(traceFile, "+q(Format));
    for(int i = 0; i < Message.length; ++i) s.append(", "+Message[i]);
    s.append("); $fflush(traceFile);");
    return ""+s;
   }

  void initializeJavaMemory () {for(UnitMemory m : memories()) for (int i = 0, N = m.size(); i < N;++i) m.units[i] = 0;}// Clear all of memory to zero
  void dumpJavaMemories ()     {for(UnitMemory m : memories()) appendJavaTrace(m.dumpAsDecimal());}                     // Dump all the memories

  void initializeJavaVars()                                                                                             // Initialize java variables so that they start with a known value despite being invalid because the valid bit is not tracked in the verilog version
   {for (Int  i : ints())  {i.i = 0;     i.v = false;}
    for (Bool b : bools()) {b.i = false; b.v = false;}
   }

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

    if (codeSize() == 0) stop("No code to execute"); else say(f("            Code size: %,12d", codeSize()));            // Code size check
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
        final boolean       r = github_actions || aws_run;                                                              // Running remotely
        final String        v = "vvp -M../../vpi -mwall_time " +currentTestNameSuffix();                                // Command to run verilog simulation

        s.append(substitute("cd {f}; rm -f {n}; iverilog -g2012 -o {n} {n}.v && {t} {v}",                               // Construct command
                            "f", verilogTestFolder(),
                            "n", currentTestNameSuffix(),
                            "v", v,
                            "t", github_actions || aws_run ? "" : f("timeout %ds ", verilogTimeOut)));                  // Time out if running locally.  The progfrsam will return a coed of 124 if it times out
        say(""+s, r ? "running remotely" : "running locally");                                                          // Confirm location of run
        final ExecCommand x = new ExecCommand(s);
        say(x.out);
        say(x.err);

        ok(readFileAsString(verilogTraceFile()).equals(readFileAsString(javaTraceFile())));                             // Compare corresponding java and Verilog trace files -  says failed if it fails and provides a traceback
       }
     }
   }

  void variableNotSet (String Type, String Name)                                                                        // Variable not yet set message
   {final I i = executing();
    final String m = (Name != null ? '"'+Name+'"'+", " : "") + "has not been set yet";
    if (i != null) stop(Type, m, i.traceBack, "====");                                                                  // With traceback on failing instruction if possible
    else           stop(Type, m);                                                                                       // No traceback available
   }

  void dumpProgramState (String Location)                                                                               // Dump program memories and variables
   {new I()
     {void    a()     {appendJavaTrace(Location+"\n");                                         dumpJava();}
      String  v()     {return "$fwrite(traceFile, \""+Location+"\\n\"); $fflush(traceFile);\n"+dumpVerilog();}          // fdisplay gets removed by trace suppression
      boolean trace() {return false;}
     };
   }

  void dumpProgramMemories (String Location)                                                                            // Dump program memories
   {new I()
     {void    a()     {appendJavaTrace(Location+"\n");                                         dumpJavaMemories();}
      String  v()     {return "$fwrite(traceFile, \""+Location+"\\n\"); $fflush(traceFile);\n"+dumpVerilogMemories();}  // fdisplay gets removed by trace suppression
      boolean trace() {return false;}
     };
   }

  void dumpProgramRegisters (String Location)                                                                           // Dump program registers
   {new I()
     {void    a()     {appendJavaTrace(Location+"\n");                                         dumpJavaRegisters();}
      String  v()     {return "$fwrite(traceFile, \""+Location+"\\n\"); $fflush(traceFile);\n"+dumpVerilogRegisters();} // fdisplay gets removed by trace suppression
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
    final String     indent = " ".repeat(8);                                                                            // Indentation for verilog code
    final int    sizeMemory = unitMemory != null ? unitMemory.size() : 0;                                               // Size of memory
    final int  numberOfInts =  nextIntId;                                                                               // Number of integers needed
    final int numberOfBools = nextBoolId;                                                                               // Number of bools needed
    final InstructionMatches instructionMatches = new InstructionMatches();                                             // Mapping from instructions to blocks of matching instructions

    for(I i : code) {compiling(i); instructionMatches.add(i);}                                                          // Match instructions
    verilogArrays().add("pcConstant",   pcConstant(),                   -1);                                            // Instruction to variable or memory used by the instruction. Defined here so that the state enum can be generated
    verilogArrays().add("pcToMatchSet", instructionMatches.pcToMatch(), -1);                                            // Translate instruction numbers to first instances of that instruction to compress labels on execution loop case statement

    try
     (final var out = Files.newBufferedWriter(Path.of(codeFile)))                                                       // Write the verilog to a file
     {/*Module*/out.write(substitute("""
module {name};                                                                                                          // Bit machine
""", "name", name));

      for(UnitMemory m : memories)                                                                                      // Each memory attached to this program
       {/*Memory*/out.write(substitute("""
  parameter {SIZE} = {size};                                                                                            // Amount of memory
  integer   {name}[{SIZE}:0];                                                                                           // Declare memory made of integer
  integer   {index};                                                                                                    // Integer to index this memory
""",  "name", m.n(), "index", m.index(), "size", ""+m.size(), "SIZE", ""+m.sizeParameter()));
       }

      /*Execution State Variables*/out.write(substitute("""
  parameter        INT_VARS = {numberOfInts};                                                                           // Number of integer variables
  parameter       BOOL_VARS = {numberOfBools};                                                                          // Number of boolean variables
  integer                pc;                                                                                            // Program counter for stepping through user code
  integer            lastPc;                                                                                            // The instruction which started the latest flow of control block
  integer         traceFile;                                                                                            // Write verilog trace records to this file
  integer             index;                                                                                            // Index for clearing memory
  integer        upperIndex;                                                                                            // Upper limit of a block of array entries being loaded in parallel as array load times are very slow if done one element at a time
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

  integer                 i[INT_VARS:0];  integer index_ints;                                                           // Integers
  reg                     b[BOOL_VARS:0]; integer index_bool;                                                           // Booleans

""", "numberOfInts", ""+numberOfInts, "numberOfBools", ""+numberOfBools));

      for(VerilogArrays.Array a : verilogArrays.arrays()) out.write(a.define());                                        // Define verilog arrays

      for(UnitMemory m : memories)                                                                                      // Control registers for each memory
       {out.write("  integer "+ m.     vReadBool() + ";\n");                                                            // Boolean read from memory
        out.write("  integer "+ m.    vWriteBool() + ";\n");                                                            // Boolean to write into memory
        out.write("  integer "+ m.      vReadInt() + ";\n");                                                            // Integer read from memory
        out.write("  integer "+ m.     vWriteInt() + ";\n");                                                            // Integer to write into memory
        out.write("  integer "+ m. vReadIntIndex() + ";\n");                                                            // Index at which to read an integer from memory
        out.write("  integer "+ m. vReadBitIndex() + ";\n");                                                            // Index within an integer from which to get a bit to make a boolean
        out.write("  integer "+ m.vWriteIntIndex() + ";\n");                                                            // Index at which to write an integer into memory
        out.write("  integer "+ m.vWriteBitIndex() + ";\n");                                                            // Index within an integer at which to set a bit to represent a boolean
       }

      /*Execute*/out.write("""
  initial begin                                                                                                         // Execute actual code
    #10;                                                                                                                // Let all the initialization complete
    forever #1 begin                                                                                                    // Execute instructions
""");

      if (!compressInstructions || !compressInstructionLabels)                                                          // No compression of instruction labels
      /*Execute case*/out.write("""
      case(pc)
""");
      else                                                                                                              // Compress instruction labels
      /*Execute case*/out.write("""
      case(array_pcToMatchSet[pc])
""");

      if (compressInstructions)                                                                                         // Compress instructions
       {if  (!compressInstructionLabels)                                                                                // Compress by writing all labels against the first instance of an instruction
         {for (InstructionMatches.Match m : instructionMatches.sequence)                                                // Each block of identical instructions
           {final String v = m.first().formatVerilogCode(m.verilog);
            out.write(indent + m.labels() + v);
           }
         }
        else                                                                                                            // Compress each block to a single sequential instruction and map pc at head of case statement accordingly
         {for (InstructionMatches.Match m : instructionMatches.sequence)                                                // Each block of identical instructions
           {final String v = m.first().formatVerilogCode(m.verilog);
            out.write(indent + f("%4d", m.block) + v);
           }
         }
       }
      else                                                                                                              // Write instructions without compression
       {for (I i : program().code)                                                                                      // Each identical instruction
         {compiling(i);
          final String v = i.formatVerilogCode(i.interiorVerilog());
          out.write(indent + f("%4d", i.instructionNumber) + v);
         }
       }

      if (true)                                                                                                         // Instruction reduction statistics
       {final int m = instructionMatches.sequence.size(), c = code.size();
        final double p = 100 * (c - m) / (double)c;
        say(f("Instruction reduction: %,12d, percent: %7.4f", m, p));
       }

      /* Execute default*/out.write("""
        default: begin
          $fclose(traceFile);
          $finish(0);
        end
      endcase
    end
  end
""");

      /*Clear registers*/out.write(substitute("""
  initial begin                                                                                                         // Clear registers
    index        = 0;
    pc           = 0;
     sourceIntId = 0;                                                                                                   // Id of source int
    source2IntId = 0;                                                                                                   // Id of source2 int
     targetIntId = 0;                                                                                                   // Id of target int
    sourceBoolId = 0;                                                                                                   // Id of source bool
    targetBoolId = 0;                                                                                                   // Id of target bool
      sourceBool = 0;                                                                                                   // Source value for a boolean  operation obtained from a variable
       sourceInt = 0;                                                                                                   // Source value for an integer operation obtained from a variable
      source2Int = 0;                                                                                                   // Second source value for an integer operation obtained from a variable
       targetInt = 0;                                                                                                   // Computed target integer value to be loaded into a variable
      targetBool = 0;                                                                                                   // Computed target boolean value to be loaded into a variable

    traceFile = $fopen("{traceFile}", "w");                                                                             // Clear the trace file
    if (traceFile == 0) begin
      $display("ERROR: Could not open file '{traceFile}' for writing.");
      $finish;
    end
    traceFile = $fopen("{traceFile}", "a");                                                                             // Start appending to the emptied trace file
    if (traceFile == 0) begin
      $display("ERROR: Could not open file '{traceFile}' for appending.");
      $finish;
    end
  end
""", "traceFile", traceFile));

      /*Clear variables*/out.write("""
  initial begin;                                                                                                        // Clear integers and booleans in verilog
    for(index_ints = 0; index_ints < INT_VARS;  index_ints = index_ints + 1) i[index_ints] = 0;
    for(index_bool = 0; index_bool < BOOL_VARS; index_bool = index_bool + 1) b[index_bool] = 0;
  end
""");

    /*Clear memory*/for(UnitMemory m : memories)                                                                        // Control registers for each memory
       {out.write(substitute("""
  initial begin                                                                                                         // Clear memory in Verilog
    for ({index} = 0; {index} < {SIZE}; {index} = {index} + 1) {name}[{index}] = 0;
  end
""",  "index", m.index(),  "name", m.n(), "SIZE", ""+m.sizeParameter()));
       }

      for(VerilogArrays.Array a : verilogArrays.arrays()) out.write(a.load());                                          // Write array definitions
      for(UnitMemory          m : memories)               out.write(   dumpVerilogMemoryInDecimal(m)+"\n");             // Dump memories in Verilog

      /*Clear variables*/ out.write(dumpVerilogVariables()+"\n");                                                       // Dump verilog variables task
      /*End*/out.write("""
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

//D2 Dumps                                                                                                              // Dump memory, variables, registers

  String dumpVerilogMemoryInDecimal (UnitMemory M)                                                                      // Dump memory in decimal
   {return substitute("""
  task {dumpVerilogMemoryInDecimalName};
    integer i;
    integer I;
    parameter integer N = 10;
    begin
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
      $fflush(traceFile);
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
      $fdisplay(traceFile, "Int  %8d == %8d",        {id}, i[{id}]);
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
    $fflush(traceFile);
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
    s.append("$fwrite(traceFile, \"      sourceBool = %8d\\n\", sourceBool  );\n");
    s.append("$fwrite(traceFile, \"      targetBool = %8d\\n\", targetBool  );\n");
    s.append("$fflush(traceFile);\n");
    return ""+s;
   }

  String dumpVerilog ()                                                                                                 // Dump verilog memory and variables
   {final StringBuilder s = new StringBuilder();
    s.append(dumpVerilogMemories());
    s.append(dumpVerilogVariablesName()+"();\n");
    s.append(dumpVerilogRegisters());
    return ""+s;
   }

  String removeTracing(String V)                                                                                        // Remove tracing if necessary
   {return suppressInstructionTracing ? V.replaceAll("(?s)\\$fdisplay.*?;", "")
                                         .replaceAll("(?s)\\$fflush.*?;"  , "") : V;
   }

//D2 Instruction Matching                                                                                               // Classify instructions into blocks of identical instructions and then compressing out the duplicates to reduce code size

  class InstructionMatches                                                                                              // Matching set of instructions
   {final TreeMap<String,  Match> matches  = new TreeMap<>();                                                           // Matches by verilog
    final TreeMap<Integer, Match> inMatch  = new TreeMap<>();                                                           // Matches by instruction number
    final Stack           <Match> sequence = new Stack  <>();                                                           // Sequence of matches

    class Match                                                                                                         // Matching set of instructions
     {final String   verilog;                                                                                           // Interior verilog for this match
      final int        block = sequence.size();                                                                         // Match number in sequence
      final Stack<I> matches = new Stack<I>();                                                                          // Instructions in this set of identical instructions

      Match(String Verilog, I I) {verilog = Verilog; sequence.push(this); matches.push(I);}                             // Create a new match set and add it to the existing matching instructions

      void push (I I) {matches.push(I);}                                                                                // Add another instruction to the match set
      int  size ()    {return matches.size();}                                                                          // Number of instructions in the match set
      I   first ()    {return matches.size() == 0 ? null : matches.firstElement();}                                     // First instruction in match set

      String labels()                                                                                                   // Instruction numbers formatted as a comma separated list for attachment to the always case statement
       {final StringJoiner j = new StringJoiner(", ");
        for (I i : matches) j.add(""+i.instructionNumber);
        return ""+j;
       }
     }

    void add(I I)                                                                                                       // Add an instruction
     {final String v = I.interiorVerilog();
      if (matches.containsKey(v))                                                                                       // Add to an existing set of matches
       {final Match m = matches.get(v);
        m.push(I);
        inMatch.put(I.instructionNumber, m);
       }
      else                                                                                                              // Create a new set of matches
       {final Match m = new Match(v, I);
        matches.put(v, m);
        inMatch.put(I.instructionNumber, m);
       }
     }

    Match firstMatch(I I)                                                                                               // Is this instruction the first of a match block
     {final Match m = inMatch.get(I.instructionNumber);
      return m.matches.firstElement() == I ? m : null;
     }

    TreeMap<Integer,Integer> pcToMatch()                                                                                // Match instructions to sets of matching instructions
     {final TreeMap<Integer,Integer> M = new TreeMap<>();                                                               //
      for (Match m : sequence) for (I i : m.matches) M.put(i.instructionNumber, m.block);                               // Instruction to matching instructions block number
      return M;
     }
    void clear() {matches.clear(); sequence.clear();}                                                                   // Free data associated with instruction matching as it can get quite big
   }

//D2 Verilog Arrays                                                                                                     // Define arrays in verilog to match this used in Java

  class VerilogArrays                                                                                                   // Define arrays in verilog to match this used in Java
   {final TreeMap<String, Array> arrays = new TreeMap<>();                                                              // Arrays defined by name - same name assumes same content

    Collection<Array> arrays() {return arrays.values();}                                                                // The arrays being defined

    class Array                                                                                                         // Matching set of instructions
     {final String  name;                                                                                               // Name of the array
      final int [] array;                                                                                               // Array to map inputs to outputs
      final int    error;                                                                                               // Value to be used on output for an undefined input

      Array (String Name, int[]Array, int Error) {name = Name; array = Array; error = Error;}                           // Create a new match set and add it to the existing matching instructions

      String clear ()                                                                                                   // Clear an array
       {final StringBuilder s = new StringBuilder();
        s.append(substitute("""
  initial begin
    for({index} = 0; {index} < {limit}; {index} = {index} + 1) array}[{index}] = 0;
  end
""", "name", name));
        return ""+s;
       }

      String define ()                                                                                                  // Define the array
       {return   substitute("""
  integer   {name}[{length}:0]; integer {index};
""", "name", arrayName(), "index", index(), "length", ""+array.length);
       }
 // Load

      String writeInHex ()                                                                                              // Write the array to a file in hexadecimal
       {final StringBuilder s = new StringBuilder();
        for(int i = 0; i < array.length; ++i) s.append(f("%8x\n", array[i]));
        s.append("0\n");                                                                                                // The array is deliberately made one element bigger than strictly needed - why I cannot recall.
        final String        f = fe(verilogTestArrayFolder(), name, "txt");
        return writeFile(f, s);
       }

      String load ()                                                                                                    // Load the array by writing its values in hex to a file and then loading that file via $readmemh
       {final String File = writeInHex();                                                                               // Absolute path name to array file
        final String file = fn(verilogArrayFiles, fnex(File));                                                          // Relative path name to array file
        return   substitute("""
  initial $readmemh("{file}", {array}, 0, {size});
""", "file", file, "array", arrayName(), "size", ""+array.length);
       }

      String loadName ()       {return "load_"       +name;}                                                            // Free data associated with instruction matching as it can get quite big
      String arrayName ()      {return "array_"      +name;}                                                            // Free data associated with instruction matching as it can get quite big
      String index ()          {return "index_array_"+name;}                                                            // Index name for clearing this array
     }

    void add (String Name, TreeMap<Integer,Integer> map, int Error)                                                     // Define a verilog array from a java tree map
     {final int  size  = map.size() == 0 ? 0 : map.lastKey() + 1;
      final int[]array = new int[size];
      Arrays.fill(array, Error);
      for (Integer i : map.keySet()) array[i] = map.get(i);
      add(Name, array, Error);
     }

    void add (String Name, int[]Array, int Error) {arrays.put(Name, new Array(Name, Array, Error));}                    // Define a verilog array from a java array
    void add (String Name, int[]Array)            {add                 (Name, Array, -1);}                              // Define a verilog function from an array with a default output for undefined inputs
   }

//D1 Testing                                                                                                            // Methods useful during testing of byte machine programs

  void check (StringBuilder G, String E)                                                                                // Test the supplied content against the specified string, then clear the output area ready for the next report
   {new I() {void a() {Test.ok(nws(G), nws(E));} int traces() {return 0;}};
   }

  void Check (StringBuilder G, String E)                                                                                // Test the supplied content against the specified string, print the actual output area contents and stop
   {new I() {void a() {if (!Test.ok(nws(G), nws(E))) stop(G, traceBack);} int traces() {return 0;}};
   }

  String      verilogTestFolder () {return fp(verilogFolder,       currentTestNameSuffix());}                           // Folder for this test using Verilog
  String verilogTestArrayFolder () {return fp(verilogTestFolder(), verilogArrayFiles);}                                 // Folder for arrays used in this test using Verilog
  String       verilogTraceFile () {return fn(verilogTestFolder(), verilogTraceFile);}                                  // Verilog trace file
  String          javaTraceFile () {return fn(verilogTestFolder(), javaTraceFile);}                                     // Java trace file
  String        VerilogCodeFile () {return fe(verilogTestFolder(), currentTestNameSuffix(), verilogSuffix);}            // Verilog code file

  static void test_addition(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Int a = new Int(1).add(2);
        a.ok(3);
        dumpProgramState("AAAA");
        execute();
       }
     };
   }

  static void test_addition()
   {          test_addition(true);
              test_addition(false);
   }

  static void test_programming(boolean Ex)
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
            dumpProgramState("AAAA");
           }
         };
        i.ok(5);
        i.valid().ok(true);
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
       {final Bool z = new Bool("zero").clear();
        final Bool o = new Bool("one" ).set();
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

  static void test_defineArrayViaVerilogFunction()
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(false).memory(16))
     {void code()
       {final int[]array = {0, 0, 0, 2, 4, 6};
        verilogArrays().add("array", array);
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
   {test_addition();
    test_programming();
    test_andOr();
    test_add();
    test_fibonacci();
    test_mod();
    test_incremental();
    test_remote();
    test_memory();
    test_memoryNegative();
    test_memoryRef();
    test_defineArrayViaVerilogFunction();
    test_lastInstructionBase();
    test_variables();
    test_boolean();
    test_ifInc();
   }

  static void newTests()                                                                                                // Tests being worked on
   {oldTests();
    //test_memory(false);
   }

  public static void main(String[] args)                                                                                // Test if called as a program
   {try                                                                                                                 // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {deleteAllFiles(verilogFolder, 999);                                                                                // Delete generated Verilog files created by a prior run of the current test
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
