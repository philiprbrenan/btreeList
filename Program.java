//----------------------------------------------------------------------------------------------------------------------
// Create a micro-coded cpu in synthesizable Verilog from a Java program coded using integers, bits, bints and memory
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
// Start with memory randomized
// Write pc on memory dump title
// Convert references to constant: arrayData_pcConstant to get name via a procedure call
// Check how often each variable is read or written to eliminate variables that are only used once.
// add .v = tryue to bitset and tree whne setting keys from an array
// T () needs to know whether to load the target or not when called outside class Bit or Int
// Improve name of gETBit()
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;
import java.util.function.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;

//D1 Construct                                                                                                          // Generate the Btree algorithm in Verilog from the equivalent java code to produce the kernel of "Database on a Chip"

public class Program extends Test                                                                                       // Develop and test a Java program to create a micro-coded cpu in Verilog
 {final boolean               suppressInstructionTracing =!true;                                                        // Do not write a trace record for each instruction - the dump of program state at the end of the run will be the test of whether the program ran as expected
  final boolean                    suppressTraceComments =!true;                                                        // Add trace comments to trace output to locate the point in the java code at which the verilog was generated - requires a lot of memory
  final boolean                     compressInstructions =!true;                                                        // Compress out identical instructions. Doing so makes Yosys run a lot faster.
  final boolean                compressInstructionLabels = true;                                                        // Reduce the instruction loop case statement by using an array to find the first instruction in the equivalence class associated with each instruction and recording that single instruction id as the sole label for each case statement possibilities
  final boolean                          generateVerilog = true;                                                        // Generate verilog version of each program
  final boolean                               runVerilog = true;                                                        // Execute  verilog version of each program
  final boolean              suppressNamesInInstructions = true;                                                        // Include names in instructions
  final boolean                       runSiliconCompiler =!true;                                                        // Run silicon compiler
  final boolean                                 runYosys =!true;                                                        // Run synthesis via Yosys to provide a fast check as to whether the verilog code is synthesizable
  final int                               verilogTimeOut = 4000;                                                        // Time out a verilog run after this many seconds if running locally
        int                                        steps =    0;                                                        // Number of instruction steps executed so far during the latest execution of this program
        int                                     maxSteps = 99_999;                                                      // Number of steps permitted in code execution - this provides some protection against endless loops during development

  final static FileNames                   verilogFolder = new FileNames(fp("verilog"));                                // Verilog folder contains temporary files which hold the generated verilog and related files
  final static FileNames              verilogTestsFolder = verilogFolder.down("test");                                  // Verilog tests
  final FileNames                      verilogTestFolder = verilogTestsFolder.down(testName()).same(testName());        // Verilog test
  final FileNames              verilogTestIncludesFolder = verilogTestFolder.down("includes");                          // Verilog test includes folder containing the include files needed for running verilog tests
  final FileNames                       verilogLogFolder = verilogFolder.down("log");                                   // Verilog log folder
  final FileNames                         blackBoxFolder = verilogTestFolder.down("blackboxes");                        // Verilog black boxes
  final FileNames                             traceFiles = verilogTestFolder.same("traceFile");                         // Verilog trace file
  final Stack<FileNames>                      blackBoxes = new Stack<>();                                               // Black box files created
  final static String          siliconCompilerImageLocal = "ghcr.io/philiprbrenan/sc_local:latest";                     // Podman container containing silicon compiler when running locally
  final static String         siliconCompilerImageGitHub = "ghcr.io/philiprbrenan/sc_github:latest";                    // Podman container containing silicon compiler when running on github
  final static int padName = 12, padCR = 16, padVerilog = 64;                                                           // Padding for components of the generated verilog code

  final Stack<I>                                    code = new Stack<>();                                               // Machine code instructions
  final Stack<Label>                              labels = new Stack<>();                                               // Labels for instructions in this process
  final Program                            parentProgram;                                                               // Redirect the code and variables of one program to another to allow components to be tested in isolation before their code is integrated into a larger program.
  final Memory                                unitMemory;                                                               // Optional memory associated with the program
  final boolean                                immediate;                                                               // Execute immediately if true else generate machine code and execute later
  public  I                                    executing = null;                                                        // Instruction currently being executed
  public  I                                    compiling = null;                                                        // Instruction currently being compiled
  private static int                            programs = 0;                                                           // Unique id for each program
  final   int                                  programId = ++programs;                                                  // Unique id for this program
  private int                                         pc;                                                               // Program counter indicating the instruction to be executed after the current one
  final        Stack<Memory>                    memories = new Stack<>();                                               // Memories used by this program and its dependent programs
  final        Stack<Int>                           ints = new Stack<>();                                               // Int variables. These are addressed individually by Java and Verilog and expanded into named registers by Yosys.
  final        Stack<Bit>                           bits = new Stack<>();                                               // Bit variables processed in the same way as ints.
  final static Stack<String>                        subs = new Stack<>();                                               // Name of the current method is cached here so that we can count instructions
        static       String                    subsTrace = null;                                                        // Traceback through the methods currently active
  final static TreeMap<String,Integer> instructionCounts = new TreeMap<>();                                             // Count instructions by subroutine in which they are added
  final DumpLocations                      dumpLocations = new DumpLocations();                                         // Locations in the code at which dumps have been requested
  final VerilogArrays                      verilogArrays = new VerilogArrays();                                         // Verilog read only array definitions tat are maoed to Read Only Memory to prevent Yosys from expanding them.
        VerilogArrays.Array              pcConstantArray = null;                                                        // Instruction to variable or memory used by the instruction. Mapped to read only memory so that Yos ys does not expand them into registers. Prefetched one instruction in advance to keep the main instruction loop fully occupied except at branches where a one instruction wait has to be inserted to allow the prefetch loop to get ahead again.
        VerilogArrays.Array              pcMatchSetArray = null;                                                        // Constants in instructions identified by program counter as above.
  final TreeMap<Integer,Integer>              pcConstant = new TreeMap<>();                                             // Instruction equivalence set identified by program counter
  final Memory                                 intMemory;                                                               // Integer memory - the Java phases use their own storage for integers and booleans but do rely on the memory control registers
  final Memory                                 bitMemory;                                                               // Boolean memory - the Java phases use their own storage for integers and booleans but do rely on the memory control registers
  int                                          currentPc = 0;                                                           // Current program counter
  int                                             jtrace = 0;                                                           // Count the number of  times jtrace() has been called to demonstrate that each instruction generates one matching call to jtrace
  int                                             vtrace = 0;                                                           // Count the number of  times vtrace() has been called to demonstrate that each instruction generates one matching call to vtrace
  int                                          nextIntId = 0;                                                           // Unique id for each Int
  int                                          nextBitId = 0;                                                           // Unique id for each Bit
  int                                         scDieAreaX = 1000;                                                        // Default size of x dimension for chip
  int                                         scDieAreaY = 1000;                                                        // Default size of y dimension for chip
  boolean                              generatingVerilog = false;                                                       // Whether or not we are generating verilog at the moment

  final static class Build                                                                                              // Builder for this program
   {boolean immediate;                                                                                                  // Immediate mode
    boolean trace;                                                                                                      // Trace execution
    Program parent;                                                                                                     // Parent program
    Integer size;                                                                                                       // Memory allocated by this program
    Build immediate (boolean Immediate) {immediate = Immediate; return this;}
    Build parent (   Program Parent)    {parent    = Parent;    return this;}
    Build memory (   int     Size)      {size      = Size;      return this;}
   }  // Build

  Program (Build Build)                                                                                                 // Construct
   {immediate       = Build.immediate;                                                                                  // Immediate or delayed execution
    parentProgram   = Build.parent == null ? this : Build.parent;                                                       // Parent program that will contain the code
    unitMemory      = Build.size   != null ? new Memory(Build.size) : null;                                             // Memory associated with program if any
    deleteAllFiles(verilogTestFolder.folder, 999);                                                                      // Delete generated Verilog files created by a prior run of the current test
    makePath(verilogTestFolder.folder);                                                                                 // Verilog folder for this test
    intMemory       = new Memory(0, "Ints");                                                                            // Integer memory - the Java phases use their own storage for integers and booleans but do rely on the memory control registers
    bitMemory       = new Memory(0, "Bits");                                                                            // Boolean memory - the Java phases use their own storage for integers and booleans but do rely on the memory control registers
    initializeRegisters();                                                                                              // Start registers in known state
    code();                                                                                                             // Load or execute the code associated with this program
   }

  void               code () {}                                                                                         // Override to provide some code for this program
  boolean       immediate () {return program().immediate;}                                                              // Executing immediately via interpretation
  boolean     isExecuting () {return program().executing != null;}                                                      // Executing machine code
  Program         program () {return parentProgram;}                                                                    // Address this program
  void     executingCheck () {if (!isExecuting()) stop("Not executing");}                                               // Confirm that code is being executed and that consequently an instruction should be executed otherwise complain
  void parentProgramCheck () {if (program() != program().program()) stop("Parent program not set to parent program");}  // Check that code is being written to the expected program

  void ai ()                                                                                                            // An executing program cannot be extended by adding new data or instructions
   {final I      i = executing();
    final String m = immediate() ? "immediate" : "delayed";
    if (i != null) stop("Allocation within an instruction while executing in", m, "mode:", i.traceBack, "====");
   }

  void rx ()                                                                                                            // This register can only be accessed during execution
   {final I x = executing();
    if (!immediate() && x == null)
     {stop("Control register can only be accessed during execution:", x.traceBack, "====");
     }
   }

  void rc ()                                                                                                            // This register can only be accessed during compilation
   {final I x = executing();
    if (x != null)
     {stop("Control registers can only be accessed during compilation:", x.traceBack, "====");
     }
   }

  Program maxSteps (int MaxSteps) {program().maxSteps = MaxSteps; return this;}                                         // Set number of steps

  I compiling ()                 {return program().compiling;}                                                          // Instruction currently being compiled
  I executing ()                 {return program().executing;}                                                          // Instruction currently being executed
  I compiling (I I)              {return program().compiling = I;}                                                      // Instruction currently being compiled
  I executing (I I)              {return program().executing = I;}                                                      // Instruction currently being executed

  Stack<Int>  ints ()            {return program().ints;}
  Stack<Bit>  bits ()            {return program().bits;}
  Stack<Memory> memories ()      {return program().memories;}

  int      currentPc ()          {return program()  .currentPc;}
  int    targetIntId ()          {return intMemory().read1IntIndex;}
  int    sourceIntId ()          {return intMemory().read2IntIndex;}
  int   source2IntId ()          {return intMemory().read3IntIndex;}
  int    targetBitId ()          {return bitMemory().read1IntIndex;}
  int    sourceBitId ()          {return bitMemory().read2IntIndex;}
  int      targetInt ()          {return intMemory().read1Int;}
  int      sourceInt ()          {return intMemory().read2Int;}
  int     source2Int ()          {return intMemory().read3Int;}
  boolean  targetBit ()          {return bitMemory().read1Int != 0;}
  boolean  sourceBit ()          {return bitMemory().read2Int != 0;}

  void    currentPc (int V)     {ngv(); program()  .currentPc = V;}
  void  targetIntId (int V)     {ngv(); intMemory().read1IntIndex = V;}
  void  sourceIntId (int V)     {ngv(); intMemory().read2IntIndex = V;}
  void source2IntId (int V)     {ngv(); intMemory().read3IntIndex = V;}
  void  targetBitId (int V)     {ngv(); bitMemory().read1IntIndex = V;}
  void  sourceBitId (int V)     {ngv(); bitMemory().read2IntIndex = V;}
  void    targetInt (int V)     {ngv(); intMemory().writeInt      = V;}                                                 // Currently bits are stored wastefully as integers hoping that yosys will remove unused paths
  void    sourceInt (int V)     {ngv(); intMemory().read2Int      = V;}
  void   source2Int (int V)     {ngv(); intMemory().read3Int      = V;}
  void    targetBit (boolean V) {ngv(); bitMemory().writeInt      = V ? 1 : 0;}
  void    sourceBit (boolean V) {ngv(); bitMemory().read2Int      = V ? 1 : 0;}

  void  ngv() {if (generatingVerilog) stop("Cannot call this function while generating verilog");}                      // The variable is owned by the associated verilog memory module and so cannot be written to by the main module although the main module can read the value

  Memory   intMemory ()          {return program().intMemory;}
  Memory   bitMemory ()          {return program().bitMemory;}

  void initializeRegisters ()                                                                                           // Initialize registers
   {currentPc(0);
    sourceIntId(0); source2IntId(0); sourceBitId(0); sourceInt(0); source2Int(0); sourceBit(false);
    targetIntId(0); targetBitId(0);
    targetInt(0);   targetBit(false);
   }

  TreeMap<Integer,Integer> pcConstant () {return program().pcConstant;}                                                 // Instruction number to variable or memory
  VerilogArrays         verilogArrays () {return program().verilogArrays;}                                              // Verilog array definitions
  DumpLocations         dumpLocations () {return program().dumpLocations;}                                              // Verilog array definitions

  void pcConstant (I I, Label Target)    {pcConstant().put(I.instructionNumber, Target.offset);}                        // Save a constant label into the instruction to constant map
  void pcConstant (I I, int   Target)    {pcConstant().put(I.instructionNumber, Target);}                               // Save a constant integer into the instruction to constant map

  String pName ( String Text)            {return pad(Text,    padName   );}                                             // Pad Verilog names
  String pCR (   String Text)            {return pad(Text,    padCR     );}                                             // Pad Verilog control register names
  String pExpr ( String Text)            {return pad(Text,    padVerilog);}                                             // Pad Verilog expressions

  String pqName (String Text)            {return pad(q(Text), padName   );}                                             // Pad Verilog names and quote

//D1 Program                                                                                                            // Program execution structures.  the //D* comments are headers at different levels in the documentation describing this code

//D2 For loops                                                                                                          // For loops with fixed and variable number of iterations

  abstract class For                                                                                                    // For loop: executed a specified number of times as long as the iterated code requests continuation
   {For (Int Start, Int End)                                                                                            // Execute the loop the specified number of times
     {final Int index = new Int("Index");
      final Bit cont  = new Bit("Continue");

      if (immediate())                                                                                                  // Immediate execution
       {index.set(Start);                                                                                               // Start index
        for(int i : range(Start.i(), End.i()))                                                                          // Iterate over the specified range
         {body(index, cont.clear());                                                                                    // Execute the loop body
          index.inc();                                                                                                  // Set the index to each element of the specified range
          if (!cont.b()) break;                                                                                         // Terminate the loop unless continuation has been requested
         }
       }
      else                                                                                                              // Machine code
       {index.set(Start);                                                                                               // Start index
        final Label start = new Label();                                                                                // Start of for loop code
        final Label   end = new Label();                                                                                // End of for loop code
        final Bit    done = index.ge(End);                                                                              // Start of loop - make sure the index is still in range - we will use the side effect of this instruction in the next instruction
      //index.T();                                                                                                      // Load index from memory
        final I S = new I(false)                                                                                        // Start of loop - make sure the index is still in range
         {void   a()   {if (index.i() >= End.i()) program().pc = end.offset;}                                           // Index out of range. Program counter has already been incremented so we do not need to do it again
          String v()   {return "if ("+bitMemory().vWriteInt()+") pc <= arrayData_pcConstant; else pc <= pc + 1;";}      // Terminate loop when index is out of range relying on the side effect of the previous instruction having set target bool
          int traces() {return 0;}
         };
        body(index, cont.clear());                                                                                      // Execute the loop body
        index.inc();                                                                                                    // Increment loop counter
        cont.T();                                                                                                       // Load continue
        final I E = new I(false)
         {void   a()   {program().pc = cont.b() ? start.offset : end.offset;}                                           // Continue execution of the loop as long as requested
          String v()   {return "if ("+bitMemory().vRead1Int()+") pc <= arrayData_pcConstant; else pc <= pc + 1;";}
          int traces() {return 0;}
         };
        end.set();                                                                                                      // End of the loop
        pcConstant(S, end);                                                                                             // Set end of loop jump now we know its target
        pcConstant(E, start);                                                                                           // Store jump to restart the loop in the instruction to constants map
       }
     }

    For (int End) {this(new Int("Start", 0), new Int("End", End));}                                                     // Execute the loop the specified number of times as long as it returns true
    For (Int End) {this(new Int("Start", 0),                End);}                                                      // Execute the loop the specified number of times as long as it returns true

    abstract void body (Int Index, Bit Continue);                                                                       // Body of the for loop - execute while in range and continuation has been requested
   } // For

  abstract class ForCount                                                                                               // For loop for a precomputed number of times
   {ForCount (Int Start, Int End)                                                                                       // Execute the loop the specified number of times
     {final Int index = new Int("Index");
      if (Start == null) index.set(0); else index.set(Start);                                                           // Start index

      if (immediate())                                                                                                  // Immediate execution
       {for(int i : range(index.i(), End.i()))                                                                          // Iterate over the specified range
         {body(index);                                                                                                  // Execute the loop
          index.inc();                                                                                                  // Increment loop counter
         }
       }
      else                                                                                                              // Machine code
       {final Label start = new Label();                                                                                // Start of for loop code
        final Label   end = new Label();                                                                                // End of for loop code
        final Bit    done = index.ge(End);                                                                              // Start of loop - make sure the index is still in range - we will use the side effect of this instruction in the next instruction
      //index.T();                                                                                                      // Load index
        final I S = new I(false)                                                                                        // Start of loop - make sure the index is still in range
         {void   a()   {if (index.i() >=  End.i()) program().pc = end.offset;}                                          // Index out of range
          String v()   {return "if ("+bitMemory().vWriteInt()+") pc <= arrayData_pcConstant; else pc <= pc + 1;";}      // Terminate the loop when the index is out of range. The if statement relies on the side effect of the previous instruction having set the target boolean value
          int traces() {return 0;}
         };
        body(index);                                                                                                    // Execute the loop
        index.inc();                                                                                                    // Increment loop counter
        final I E = new I(false)                                                                                        // Restart loop
         {void   a()   {program().pc = start.offset;}
          String v()   {return "pc <= arrayData_pcConstant;";}
          int traces() {return 0;}
         };
        end.set();                                                                                                      // End of the loop
        pcConstant(S, end);                                                                                             // Set end   of loop jump now we know its target
        pcConstant(E, start);                                                                                           // Set start of loop jump now we know its target
       }
     }

    ForCount (Int End)            {this(null,                                   End );}                                 // Execute the loop the specified number of times
    ForCount (int End)            {this(null,                    new Int("End", End));}                                 // Execute the loop the specified number of times
    ForCount (int Start, int End) {this(new Int("Start", Start), new Int("End", End));}                                 // Execute the loop the known number of times

    abstract void body (Int Index);                                                                                     // Body of the for loop - execute while in range and continuation requested
   } // ForCount

//D2 If                                                                                                                 // If then else

  abstract class If                                                                                                     // If statement
   {If (boolean Condition)                                                                                              // A constant that selects code at compile time
     {if (Condition) Then(); else Else();
     }

    If (Bit Condition)
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
           {return "if (!"+bitMemory().vRead1Int()+") pc <= arrayData_pcConstant; else pc <= pc + 1;";
           }
          int traces() {return 0;}
         };
        Then();                                                                                                         // Then body
        final I Else = new I(false)                                                                                     // Jump over else to end
         {void     a() {program().pc  = end.offset;}
          String   v() {return "pc <= arrayData_pcConstant;";}
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
   } // If

//D1 Data                                                                                                               // Operations on boolean and integer data

//D2 Boolean values                                                                                                     // Operations on boolean values

  final class Bit                                                                                                       // A boolean value
   {boolean    i = false;                                                                                               // Value of the boolean
    boolean    v = false;                                                                                               // Whether the current value of the integer is valid or not
    boolean   nd = false;                                                                                               // If true the boolean should not be dumped because it represents the validity of an integer variable and no such determination is possible in the Verilog code.
    final int id = program().nextBitId++;                                                                               // Unique id for Bit
    final boolean    top = callerName() == "code";                                                                      // A declaration at the top level
          boolean     in = false;                                                                                       // An input wire if true and named and at the top
          boolean    out = false;                                                                                       // An output register if true and named and at the top
    String  name = null;                                                                                                // The name of the variable

    enum Ops {and, del, eq, flip, ne, or, set};                                                                         // Boolean operation classification by argument types

    Bit (String Name)            {this();  name = Name; out = top;}                                                     // Constructor with name supplied. Output register if it is at the top
    Bit (String Name, boolean I) {this(I); name = Name; in  = top;}                                                     // Input wire if we know its value at the start and it is at the top
    Bit (String Name, Bit     I) {this(I); name = Name; out = top;}                                                     // Output register if its value is unknown at the start and is at the top

    Bit ()                       {ai();  del(false);     bits().push(this);}                                            // Constructors. Set newly constructed integers to invalid and minus one
    Bit (boolean I)              {ai();  ie(Ops.set, I); bits().push(this);}
    Bit (Bit     I)              {ai();  ie(Ops.set, I); bits().push(this);}
    boolean      b ()            {x(); return i;}
    void         x ()            {if (!v) variableNotSet("Bit", name);}                                                 // Check a value has been set for the boolean

    Bit        set ()            {return ie(Ops.set,  true); }                                                          // Boolean operations which modify the target
    Bit        set (boolean I)   {return ie(Ops.set,  I);    }
    Bit        set (Bit     I)   {return ie(Ops.set,  I);    }
    Bit      clear ()            {return ie(Ops.set,  false);}
    Bit        del (boolean I)   {return ie(Ops.del,  I);    }
    Bit       flip ()            {return ie(Ops.flip);       }
    Bit       Flip ()            {return dup().flip();}
    Bit         ne (Bit     I)   {return ie(Ops.ne,  I);}
    Bit         ne (boolean I)   {return ie(Ops.ne,  I);}
    Bit         or (Bit     I)   {return ie(Ops.or,  I);}                                                               // "Or" without short circuit. Modifies the target.
    Bit        and (Bit     I)   {return ie(Ops.and, I);}                                                               // "And" without short circuit. Modifies the target.
    Bit         Or (Bit     I)   {return dup().or (I);}                                                                 // "Or" without short circuit. Does not modify the target
    Bit        And (Bit     I)   {return dup().and(I);}                                                                 // "And" without short circuit. Does not modify the target
    Bit        dup ()            {return new Bit(this);}                                                                // Duplicate a boolean so that the duplicated version can be modified without modifying the original
                                                                                                                        // Execute as an instruction because these are the building blocks of the chip with which we wish to construct the algorithm
    Bit ie (Ops Op)        {T();        new I() {void a() {ex(Op   );} String v() {return ev(Op);}}; W(); return this;}
    Bit ie (Ops Op, Bit I) {T(); I.S(); new I() {void a() {ex(Op, I);} String v() {return eV(Op);}}; W(); return this;}
    Bit ie (Ops Op, boolean I)
     {T(Op);
      new I() {void a() {ex(Op, I);} String v() {return eV(Op, I);}};
      W();
      return this;
     }

    int        pc () {return currentPc();}                                                                              // Address of instruction
    void setValid () {v = true;}                                                                                        // Mark a bit as valid

    abstract class LoadSourceOrTarget
     {LoadSourceOrTarget(Bit B, String MemoryIndex, String MemoryValue, boolean LoadValue)                              // Load source or target value via id of boolean
       {final String mi = pCR(MemoryIndex);                                                                             // Index
        final String mv = pCR(MemoryValue);                                                                             // Value

        final I i = new I()                                                                                             // Load id of variable if requested
         {void   a() {loadId(id);                                      jTrace(f("%8d BST1 "+mi+" = %8d",  pc(), id)                  );}
          String v() {return  mi + pExpr(" <= arrayData_pcConstant; ")+vTrace(  "%8d BST1 "+mi+" = %8d", "pc", "arrayData_pcConstant");}
         };
        pcConstant(i, id);                                                                                              // Id of variable being addressed by these instructions

        if (LoadValue)                                                                                                  // Load value if requested
         {new I()                                                                                                       // Load source value
           {void   a() {loadValue(B.i); jTrace(f("%8d BST2 "+mv+" %8d",  pc(),  B.i ? 1 : 0));}
            String v() {return          vTrace(  "%8d BST2 "+mv+" %8d", "pc",   bitMemory().memory(MemoryIndex));}
           };
         }
       }
      int pc() {return currentPc();}                                                                                    // Address of this instruction
      abstract void loadId   (int I);                                                                                   // Override to load the id of the variable
      abstract void loadValue(boolean V);                                                                               // Override to record the value of the variable
     } // LoadSourceOrTarget

    void S ()                                                                                                           // Address source boolean value and load its value
     {new LoadSourceOrTarget(this, bitMemory().vRead2IntIndex(), bitMemory().vRead2Int(), true)
       {void loadId   (int     I) {sourceBitId(I);}
        void loadValue(boolean V) {sourceBit  (V);}
       };
     }

    void T ()       {T(true);}                                                                                          // Ops that do need the target value to be loaded first
    void T (Ops Op) {T(Op != Ops.del && Op != Ops.set);}                                                                // Ops that do not need the target value to be loaded first

    void T (boolean LoadValue)                                                                                          // Load target index and optionally value at that index
     {new LoadSourceOrTarget(this, bitMemory().vRead1IntIndex(), bitMemory().vRead1Int(), LoadValue)
       {void loadId   (int     I) {targetBitId(I);}
        void loadValue(boolean V) {bitMemory().read1Int = V ? 1 : 0;}
       };
     }

    void W ()                                                                                                           // Write result back into variable assuming that the index tro write at has already been set
     {final Bit    b = this;
      final Memory M = bitMemory();
      new I()                                                                                                           // Write value of bit into memory
       {final String f = "%8d writeBit %8d = %8d";
        void   a() {i = M.writeInt != 0;  M.writeIntEnable = true; jTrace(f(f,  pc(), b.id,                         b.i ? 1 : 0));}
        String v() {return M.vWriteIntEnable() + " <= 1; " +       vTrace(  f, "pc",  bitMemory().vRead1IntIndex(), bitMemory().vWriteInt());}
       };
      new I()                                                                                                           // Lower  right enable - which could be merged with the next instruction
       {void   a() {if (!immediate()) M.units[M.read1IntIndex] = M.writeInt; M.writeIntEnable = false; jTrace(f("%8d Disable write", currentPc()));}
        String v() {return M.vWriteIntEnable() + " <= 0; "+                                            vTrace(  "%8d Disable write", "pc")+" /* Finish integer write */";}
       };
     }

    Bit ex (Ops Op)                                                                                                     // Execute a monadic boolean operation
     {executingCheck();
      v = true;
      switch(Op)
       {case flip -> {x(); targetBit(!targetBit());}
        default   -> Test.stop("Op not implemented:", Op);
       }
      jtrace();
      return this;
     }

    Bit ex (Ops Op, boolean I)                                                                                          // Execute a dyadic boolean operation on a constant
     {executingCheck();
      v = true;
      switch (Op)
       {case set -> {     targetBit(I);}
        case del -> {     targetBit(I); v = false;}                                                                     // Mark the bit as having no defined value
        case eq  -> {x(); targetBit(targetBit() == I);}
        case ne  -> {x(); targetBit(targetBit() != I);}
        case and -> {x(); targetBit(targetBit() && I);}
        case or  -> {x(); targetBit(targetBit() || I);}
        default  -> Test.stop("Op not implemented:", Op);
       }
      jtrace();
      return this;
     }

    Bit ex (Ops Op, Bit I)                                                                                              // Execute a dyadic boolean operation on a variable
     {executingCheck();
      I.x();
      return ex(Op, I.i);
     }

    String ev (Ops Op)                                                                                                  // Execute a monadic boolean operation
     {final String        n = vn();                                                                                     // Name of the variable in Verilog
      final StringBuilder s = new StringBuilder();
      switch(Op)
       {case flip -> {s.append("!"+bitMemory().vWriteInt()+"/*flip*/");}
        default   -> Test.stop("Op not implemented:", Op);
       }
      return vtrace(s);                                                                                                 // Trace the operation
     }

    String eV (Ops Op)            {return eV(Op, bitMemory.vRead2Int());}                                               // Dyadic boolean operation against memory
    String eV (Ops Op, boolean I) {return eV(Op, I ? "1" : "0");}                                                       // Dyadic boolean operation against constant

    String eV (Ops Op, String Source)                                                                                   // Execute a dyadic boolean operation
     {final StringBuilder s = new StringBuilder();
      final String S = Source;
      final String T = bitMemory().vWriteInt();
      switch (Op)
       {case set -> {s.append(         S+"/*setb*/");}
        case del -> {s.append(         S+"/*delb*/");}
        case eq  -> {s.append(T+" == "+S+"/*eqb*/" );}
        case ne  -> {s.append(T+" != "+S+"/*neb*/" );}
        case and -> {s.append(T+" && "+S+"/*andb*/");}
        case or  -> {s.append(T+" || "+S+"/*orb*/" );}
        default  -> Test.stop("Op not implemented:", Op);
       }
      return vtrace(s);                                                                                                 // Trace the operation
     }

    String vtrace (StringBuilder Value)                                                                                 // Trace a verilog boolean operation
     {final String id = bitMemory().vRead1IntIndex();
      return bitMemory().vWriteInt() + " <= "+pExpr(""+Value+";")+" "+
      vTrace(  "%8d bit %8d = %8d",  "pc",        id,  ""+Value);
     }
    void jtrace ()                                                                                                      // Trace a java    boolean operation
     {jTrace(f("%8d bit %8d = %8d",  currentPc(), id, bitMemory().writeInt));
     }

    public String toString ()                                                                                           // Print the boolean
     {final String u = "undefined_Bit";
      if (name == null) return v ? ""+i       : u;
      else              return v ? name+"="+i : u+": "+name;
     }

    String   vn ()                                                                                                      // Verilog name of this boolean variable
     {final String n = suppressNamesInInstructions ? "" : name != null ? "/*"+name+"*/" : "";
      return pName("b["+id+"]"+n);
     }

    Bit ok (boolean Value)                                                                                              // Memory trace from java makes this test redundant in Verilog if the Verilog trace matches the java trace and so there will be an empty instruction generated in the verilog to "regulate the service"
     {final Bit got = this;
      new I()
       {void a()
         {if (!got.v) stop("Invalid Bit being tested at:", executing().instructionLocation());
          Test.ok(i, Value);
         }
        String v()   {return "/* Bit ok(boolean) */";}
        int traces() {return 0;}
       };
      return this;
     }

    Bit ok (Bit Value)                                                                                                  // Memory trace from java makes this test redundant in Verilog if the Verilog trace matches the java trace  and so there will be an empty instruction generated in the verilog to "regulate the service"
     {final Bit got = this;
      if (immediate() && !Value.v) stop("Invalid expected Bit has been supplied for testing");
      new I()
       {void a()
         {if (!got.v) stop("Invalid Bit being tested at:", executing().instructionLocation());
          Test.ok(got.b(), Value.b());
         }
        String v() {return "/* Bit ok(Bit) */";}
        int traces() {return 0;}
       };
      return this;
     }

    boolean nio () {return !in && !out;}                                                                                // Not an input wire or an output register
   } // Bit

//D2 Integer values                                                                                                     // Operations on integer values

  final class Int                                                                                                       // An integer value
   {private int        i = 0;                                                                                           // Value of the integer
    private boolean    v = false;                                                                                       // Whether the current value of the integer is valid or not
            String  name = null;                                                                                        // The name of the variable
    final int         id = program().nextIntId++;                                                                       // Unique id for Int
    final boolean    top = callerName() == "code"; // Can be removed once we have variables in their own memory working                                                                     // A declaration at the top level
          boolean     in = false;                                                                                       // An input wire if true and named and at the top
          boolean    out = false;                                                                                       // An output register if true and named and at the top

    int         i ()  {x(); return i;}                                                                                  // Current value
    void        x ()  {if (!v) variableNotSet("Int", name);}                                                            // Check a value has been set for the integer

    Int (String Name)        {this();  name = Name; out = top;}                                                         // Constructor with name supplied. Output register if it is at the top
    Int (String Name, int I) {this(I); name = Name; in  = top;}                                                         // Input wire if we know its value at the start and it is at the top
    Int (String Name, Int I) {this(I); name = Name; out = top;}                                                         // Output register if its value is unknown at the start and is at the top

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

    Int ie (Ops Op)       {T();        new I(){void a() {ex(Op   );} String v() {return ev(Op   );}}; W(); return this;}// Create an instruction that can either be executed immediately one by one or later en masse
    Int ie (Ops Op, Int I){T(); I.S(); new I(){void a() {ex(Op, I);} String v() {return ev(Op, I);}}; W(); return this;}
    Int ie (Ops Op, int I)                                                                                              // Selectively loaded target, store constant for this instruction in the constants map
     {T(Op);                                                                                                            // Instruction to load target details if needed for the operation otherwise just the index of the target as in the cases of set and del
      final I i = new I() {void a() {ex(Op, I);} String v() {return ev(Op, I);}};                                       // Perform operation
      W();                                                                                                              // Write results back into a variable
      pcConstant(i, I);                                                                                                 // Record the constant used in this operation in the map from instructions to constants used
      return this;                                                                                                      // The current integer
     }

    abstract class LoadSourceOrTarget                                                                                   // Set index and values of memory for integer variables
     {LoadSourceOrTarget(Int I, String MemoryIndex, String MemoryValue, boolean LoadValue)                              // The value should not be set for operations where the target already contains the value to store
       {final String mi = pCR(MemoryIndex);                                                                             // Index
        final String mv = pCR(MemoryValue);                                                                             // Value

        final I i = new I()                                                                                             // Load index of integer
         {final String c = mi + pExpr(" <= arrayData_pcConstant; /*"+I.id+"*/");
          void   a() {loadId(id);  jTrace(f("%8d ILST1 "+mi+" = %8d",  pc(), id)                              );}
          String v() {return c+" "+vTrace(  "%8d ILST1 "+mi+" = %8d", "pc", "arrayData_pcConstant/*"+I.id+"*/");}
         };
        pcConstant(i, I.id);                                                                                            // Id of variable being addressed by these instructions is saved in the PC constant table to allow it to be used on this instruction

        if (LoadValue) new I()                                                                                          // Value of integer
         {void   a() {loadValue(I.i); jTrace(f("%8d ILST2 "+mv+" = %8d",  pc(), I.i));}
          String v() {return          vTrace(  "%8d ILST2 "+mv+" = %8d", "pc",  intMemory().memory(MemoryIndex));}      // The memory module loads the corresponding value field automatically at the end of this instruction cycle
         };
       }

      int pc() {return currentPc();}                                                                                    // Address of this instruction
      abstract void loadId   (int I);                                                                                   // Override to save index of current integer variable
      abstract void loadValue(int V);                                                                                   // Override to save the current value of the integer variable
     } // LoadSourceOrTarget

    void S ()                                                                                                           // Address first source integer and load its value
     {new LoadSourceOrTarget(this, intMemory().vRead2IntIndex(), intMemory().vRead2Int(), true)
       {void loadId   (int I) {sourceIntId(I);}
        void loadValue(int V) {sourceInt  (V);}
       };
     }

    void S2 ()                                                                                                          // Address second source integer and loads its value
     {new LoadSourceOrTarget(this, intMemory().vRead3IntIndex(), intMemory().vRead3Int(), true)
       {void loadId   (int I) {source2IntId(I);}
        void loadValue(int V) {source2Int  (V);}
       };
     }

    void T () {T(true);}                                                                                                // Address target and load its value
    void T (Ops Op) {T(Op != Ops.set && Op != Ops.del);}                                                                // Address target and load its value if needed

    void T (boolean LoadValue)                                                                                          // Address target optionally loading its value
     {new LoadSourceOrTarget(this, intMemory().vRead1IntIndex(), intMemory().vRead1Int(), LoadValue)
       {void loadId   (int I) {targetIntId(I);}
        void loadValue(int V) {intMemory().read1Int = V;}                                                               // targetInt(V) would have set the write file rather than the read field
       };
     }

    void W ()                                                                                                           // Write result back into an integer variable whicse index has been loaded by T ()
     {final Int    w = this;                                                                                            // Set index locating the integer to be written to
      final Memory M = intMemory();
      new I()                                                                                                           // Load value into integer or memory
       {final String f = "%8d writeInt %8d = %8d";
        void   a() {i = M.writeInt; M. writeIntEnable = true;        jTrace(f(f,  currentPc(), M. read1IntIndex,   M. writeInt ));}
        String v() {return          M.vWriteIntEnable() + " <= 1; "+ vTrace(  f, "pc",         M.vRead1IntIndex(), M.vWriteInt());}
       };
      new I()                                                                                                           // Lower  right enable - which could be merged with the next instruction
       {void   a() {if (!immediate()) M.units[M.read1IntIndex] = M.writeInt; M.writeIntEnable = false; jTrace(f("%8d Disable write", currentPc()));}
        String v() {return M.vWriteIntEnable() + " <= 0; "+                                            vTrace(  "%8d Disable write", "pc")+" /* Finish integer write */";}
       };
     }

    void TW() {T(false); W();}                                                                                          // Load the target index of an integer value and write its value assuming that the value to be written has already been loaded into the write integer register

    Int ex (Ops Op)                                                                                                     // Execute a monadic integer operation
     {executingCheck();
      x();
      v = true;
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
      v = true;
      switch (Op)
       {case set  -> {      targetInt(              I);}
        case del  -> {      targetInt(              I); v = false;}                                                     // Mark the integer as having no defined value
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
     {final String        n = intMemory().vRead1Int();                                                                  // Name of the variable in Verilog
      final StringBuilder s = new StringBuilder();
      switch(Op)
       {case inc  -> {s.append(n+" + 1 /*inc*/"  );}
        case dec  -> {s.append(n+" - 1 /*dec*/"  );}
        case up   -> {s.append(n+"<< 1 /*up*/"   );}
        case down -> {s.append(n+">>>1 /*down*/" );}
        case sqrt -> {s.append("sqrt("+n+")"     );}
        case neg  -> {s.append("-"    +n+"/*neg/");}
        case abs  -> {s.append("(("   +n+" < 0) ? -"+n+" : "+n+") /*abs*/");}
        default   -> stop("Op not implemented:", Op);
       }
      return vExecuteAndTrace(""+s);
     }

    String ev (Ops Op, int I)                                                                                           // Execute a monadic integer operation on a constant
     {final String        n = intMemory().vRead1Int(), c = pCR("arrayData_pcConstant/*"+I+"*/");                        // The constant will be stored in the instruction to constant map
      final StringBuilder s = new StringBuilder();
      switch (Op)
       {case set  -> {s.append(        c+"/*seti*/");}
        case del  -> {s.append(        c+"/*deli*/");}
        case add  -> {s.append(n+" + "+c+"/*addi*/");}
        case sub  -> {s.append(n+" - "+c+"/*subi*/");}
        case mul  -> {s.append(n+" * "+c+"/*muli*/");}
        case div  -> {s.append(n+" / "+c+"/*divi*/");}
        case mod  -> {s.append(n+" % "+c+"/*modi*/");}
        case add2 -> {s.append(n+" + "+c+"*2/*add2i*/");}
        default   -> stop("Op not implemented:", Op);
       }
      return vExecuteAndTrace(""+s);
     }

    String ev (Ops Op, Int I)                                                                                           // Execute a monadic integer operation on a variable
     {final String        n = intMemory().vRead1Int(), i = intMemory().vRead2Int();                                     // Memory fields for target and source integers
      final StringBuilder s = new StringBuilder();
      switch (Op)
       {case set  -> {s.append(        i+"/*setI*/");}
        case add  -> {s.append(n+" + "+i+"/*addI*/");}
        case sub  -> {s.append(n+" - "+i+"/*subI*/");}
        case mul  -> {s.append(n+" * "+i+"/*mulI*/");}
        case div  -> {s.append(n+" / "+i+"/*divI*/");}
        case mod  -> {s.append(n+" % "+i+"/*modI*/");}
        case add2 -> {s.append(n+" + "+i+" + "+i+"/*add2I*/");}
        default   -> stop("Op not implemented:", Op);
       }
      return vExecuteAndTrace(""+s);
     }

    final String atf = "%8d assign writeInt = %8d";                                                                     // Trace format for an assign statement

    String vExecuteAndTrace (String Value)                                                                              // Execute and trace an integer operation in Verilog
     {final String t = intMemory().vWriteInt();
      final String s = pExpr(Value+";");
      final String v = vTrace(  atf, "pc",         Value);
      return t + " <= " + s + v;
     }
    void jtrace ()    {jTrace(f(atf,  currentPc(), intMemory().writeInt));}                                             // Trace the integer operation in Java

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

    Bit eq ( int I) {return bie(Ops.eq, I);}                                                                            // Comparisons with a constant integer
    Bit ne ( int I) {return bie(Ops.ne, I);}                                                                            //N
    Bit le ( int I) {return bie(Ops.le, I);}
    Bit lt ( int I) {return bie(Ops.lt, I);}
    Bit ge ( int I) {return bie(Ops.ge, I);}
    Bit gt ( int I) {return bie(Ops.gt, I);}

    Bit eq ( Int I) {return bie(Ops.eq, I);}                                                                            // Comparisons with a variable integer
    Bit ne ( Int I) {return bie(Ops.ne, I);}                                                                            //N
    Bit le ( Int I) {return bie(Ops.le, I);}
    Bit lt ( Int I) {return bie(Ops.lt, I);}
    Bit ge ( Int I) {return bie(Ops.ge, I);}                                                                            //N
    Bit gt ( Int I) {return bie(Ops.gt, I);}

    Bit bie (Ops Op, int I)                                                                                             // Instruction to perform a boolean comparison between an integer variable and an integer constant
     {final Bit b = new Bit();
      S(); b.T();
      final I i = new I()
       {void   a() {       bex(Op, b, I);}
        String v() {return bev(Op, b, I);}
       };
      pcConstant(i, I);
      b.W();
      return b;
     }

    Bit bie (Ops Op, Int I)                                                                                             // Instruction to perform a boolean comparison between two integer variables
     {final Bit b = new Bit();
      S(); I.S2(); b.T();
      new I()
       {void   a() {I.x(); bex(Op, b, I);}
        String v() {return bev(Op, b);}
       };
      b.W();
      return b;
     }

    void bex (Ops Op, Bit B, int I)                                                                                     // Boolean comparison between an integer variable and an integer constant
     {x();
      B.v = true;
      switch(Op)
       {case eq -> targetBit(sourceInt() == I);
        case ne -> targetBit(sourceInt() != I);
        case le -> targetBit(sourceInt() <= I);
        case lt -> targetBit(sourceInt() <  I);
        case ge -> targetBit(sourceInt() >= I);
        case gt -> targetBit(sourceInt() >  I);
        default -> stop("Op not implemented:", Op);
       }
      B.jtrace();
     }

    void bex (Ops Op, Bit B, Int I) {I.x(); bex(Op, B, I.i);}                                                           // Boolean comparison between two integer variables

    String bev (Ops Op, Bit B)                                                                                          // Boolean comparison between two integers
     {final StringBuilder s = new StringBuilder();
      final String a = intMemory().vRead2Int(), b = intMemory().vRead3Int();
      switch(Op)
       {case eq -> s.append(a + " == " + b+"/*eq*/");
        case ne -> s.append(a + " != " + b+"/*ne*/");
        case le -> s.append(a + " <= " + b+"/*le*/");
        case lt -> s.append(a + " <  " + b+"/*lt*/");
        case ge -> s.append(a + " >= " + b+"/*ge*/");
        case gt -> s.append(a + " >  " + b+"/*gt*/");
        default -> stop("Op not implemented:", Op);
       }
      return B.vtrace(s);
     }

    String bev (Ops Op, Bit B, int I)                                                                                          // Boolean comparison between two integers
     {final StringBuilder s = new StringBuilder();
      final String a = intMemory().vRead2Int(), b = pCR("arrayData_pcConstant/*"+I+"*/");
      switch(Op)
       {case eq -> s.append(a + " == " + b+"/*eqi*/");
        case ne -> s.append(a + " != " + b+"/*nei*/");
        case le -> s.append(a + " <= " + b+"/*lei*/");
        case lt -> s.append(a + " <  " + b+"/*lti*/");
        case ge -> s.append(a + " >= " + b+"/*gei*/");
        case gt -> s.append(a + " >  " + b+"/*gti*/");
        default -> stop("Op not implemented:", Op);
       }
      return B.vtrace(s);
     }

    Int dup () {return new Int(this);}                                                                                  // Duplicate an integer so that the duplicated version can be modified without modifying the original

    void setValid () {v = true;}                                                                                        // Mark an integer as valid

    Bit valid ()                                                                                                        // Whether the integer is valid - these checks are not made in Verilog because it is assumed that of the memory traces match then the behavior of the Verilog is identical to that of the java and thus there is no need to test the validity of the integers
     {final Bit b = new Bit(); b.nd = true;                                                                             // Do not dump this boolean variable because it holds a value that has no analog in the Verilog code
      new I() {void a() {b.i = v; b.v = true;} int traces() {return 0;}};
      return b;
     }

    Bit notValid ()                                                                                                     // Whether the integer is invalid - these checks are not made in Verilog because it is assumed that of the memory traces match then the behavior of the Verilog is identical to that of the java and thus there is no need to test the validity of the integers
     {final Bit b = new Bit(); b.nd = true;                                                                             // Do not dump this boolean variable because it holds a value that has no analog in the Verilog code
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
        String v() {return "/* Int ok(int) */";}
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
        String v() {return "/* Int ok(Int) */";}
        boolean trace() {return false;}
       };
      return this;
     }

    boolean nio () {return !in && !out;}                                                                                // Not an input wire or an output register
   } // Int                                                                                                             // Int

//D2 Boolean Integer                                                                                                    // An integer that can be specifically valid or invalid thus requiring an extra validity bit only for specified integers rather than all integers in the Verilog representationOperations on integer values

  final class Bint                                                                                                      // An integer that can be specified as valid or invalid
   {private final Bit b = new Bit(false);                                                                               // Whether the associated integer is valid or invalid
    private final Int i = new Int();                                                                                    // The integer component
    Bint set (Int I) {b.set(); i.set(I); return this;}                                                                  // Set to a known value
    Bit    b ()      {return b;}                                                                                        // Return boolean component
    Int    i ()
     {new If (b.Flip()) {void Then() {stop("Requested int component from unset Bint");}};                               // Complain if there is no integer component to return
      return new Int(i);
     }

    Bit       valid () {return b;}                                                                                      // Whether the boolean integer is valid
    Bit    notValid () {return b.Flip();}                                                                               // Whether the boolean integer is invalid
    Bint invalidate () {b.clear(); return this;}                                                                        // Mark the integer as invalid after all

    Bint copy (Bint Source)                                                                                             // Copy a boolean integer
     {new If (Source.b)
       {void Then() {b.set(); i.set(Source.i());}                                                                       // Set target as valid to match source and copy the source integer
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
   } // Bint

//D2 Do nothing                                                                                                         // But do it very well

  I nop()                                                                                                  // Test an Integer. The value expected and the value got must be valid during the java execution because the verilog execution deliberately removes this information on the basis that the java code is definitive and so if the verilog trace matches the java trace the verilog code is working correctly. The purpose of the validity bit is to internally track whether the integer was ever set during program execution, it is not to convey application information. If an integer with an attached validity bit is required in application logic then Bint should be used.  This feature does not exist in the Verilog code and so there will be an empty instruction generated in the verilog to "regulate the service"
   {return new I()
     {void   a() {                     jTrace(f("%8d NOP", currentPc()));}
      String v() {return "/* NOP */" + vTrace(  "%8d NOP", "pc"        );}
     };
   }

//D1 Memory                                                                                                             // Operations on memory divided into units

  static int ib ()      {return Integer.BYTES;}                                                                         // Number of bytes in an integer
  static int ib (int I) {return I * ib();}                                                                              // Number of bytes in a number of integers
  static Int ib (Int I) {return I.Mul(ib());}                                                                           // Number of bytes in a number of integers

  class Memory                                                                                                          // Memory made of units
   {final String name;                                                                                                  // Optional name for the memory
    private final int  id;                                                                                              // Unique identifier for this memory
    private int []  units;                                                                                              // Bytes of main memory
    boolean       read1Bit = false;                                                                                     // Boolean read from memory first memory port
    boolean       read2Bit = false;                                                                                     // Boolean read from memory second memory port
    boolean       read3Bit = false;                                                                                     // Boolean read from memory third memory port
    boolean       writeBit = false;                                                                                     // Boolean to write into memory
    int           read1Int = 0;                                                                                         // Integer read from memory first memory port
    int           read2Int = 0;                                                                                         // Integer read from memory second memory port
    int           read3Int = 0;                                                                                         // Integer read from memory third memory port
    int           writeInt = 0;                                                                                         // Integer to write into memory
    int      read1IntIndex = 0;                                                                                         // Index at which to read an integer from first memory port and also write into the memory
    int      read2IntIndex = 0;                                                                                         // Index at which to read an integer from second memory port
    int      read3IntIndex = 0;                                                                                         // Index at which to read an integer from third memory port
    int      read1BitIndex = 0;                                                                                         // Index within an integer from which to get a bit first memory port or write into the memory
    int      read2BitIndex = 0;                                                                                         // Index within an integer from which to get a bit second memory port
    int      read3BitIndex = 0;                                                                                         // Index within an integer from which to get a bit third memory port
//    int      writeIntIndex = 0;                                                                                         // Index at which to write an integer into memory
//    int      writeBitIndex = 0;                                                                                         // Index within an integer at which to set a bit to represent a boolean
    boolean writeIntEnable = false;                                                                                     // Enable write for an integer
    boolean writeBitEnable = false;                                                                                     // Enable write for a boolean

    void reallocate (final int Size) {units = new int[Size];}                                                           // Resize the memory

    static int bitsPerUnit() {return Integer.SIZE;}                                                                     // Bits per memory unit

    Memory (int Length, String Name)                                                                                    // Create and clear some memory
     {name = Name;
      units = new int[Length];
      for(int i = 0; i < Length; ++i) units[i] = 0;                                                                     // Clear memory. In Verilog this is done using readmemh in an initial block. For a real chip perhaps an instruction to do this?
      final Stack<Memory> m = memories(); id = m.size(); m.push(this);                                                  // Give the memory a unique identifier and save it in the main program
     }

    Memory (int Length) {this(Length, null);}                                                                           // Create and clear some unnamed memory

    int size ()                    {return units.length;}                                                               // Size of memory
    String i ()                    {return ""+id;}                                                                      // Number of memory a string for use in writing verilog
    String n ()                    {return "m_"+id;}                                                                    // Name of memory
    String m ()                    {return "memory_"+id;}                                                               // Name of memory module used to externalize memory for yosys
    String n (String Index)        {return n() + "["+Index+"]";}                                                        // Name of indexed memory
    String n (String I, String J)  {return n() + "["+I+"]["+J+"]";}                                                     // Name of indexed memory
    String name ()                 {return name == null ? "" :     name;}                                               // Name of the verilog routine to dump this memory in decimal
    String nameSp ()               {return name == null ? "" : " "+name;}                                               // Simplifies code that would otherwise leave a trailing blank when a name was not supplied by the caller
    String dumpVerilogMemoryInDecimalName () {return "dumpDecimal_"+id;}                                                // Name of the verilog routine to dump this memory in decimal

    void                im (Int I) {pcConstant(compiling(), I.id);}                                                     // Save the integer variable used for this memory access at this instruction
    void                im (Bit B) {pcConstant(compiling(), B.id);}                                                     // Save the boolean variable used for this memory access at this instruction

    String             wdi ()      {return vWriteIntEnable() + " <= 0; ";}                                              // Write disable integer
    String             wdb ()      {return vWriteBitEnable() + " <= 0; ";}                                              // Write disable boolean
    String             wei ()      {return vWriteIntEnable() + " <= 1; ";}                                              // Write enable integer
    String             web ()      {return vWriteBitEnable() + " <= 1; ";}                                              // Write enable boolean

    String       vRead1Bit ()      {return pName(n() + "_read1Bit"       );}                                            // First boolean read from memory
    String       vRead2Bit ()      {return pName(n() + "_read2Bit"       );}                                            // Second boolean read from memory
    String       vRead3Bit ()      {return pName(n() + "_read3Bit"       );}                                            // Third boolean read from memory
    String       vWriteBit ()      {return pName(n() + "_writeBit"       );}                                            // Boolean to write into memory
    String       vRead1Int ()      {return pName(n() + "_read1Int"       );}                                            // First  integer read from memory
    String       vRead2Int ()      {return pName(n() + "_read2Int"       );}                                            // Second integer read from memory
    String       vRead3Int ()      {return pName(n() + "_read3Int"       );}                                            // Third integer read from memory
    String       vWriteInt ()      {return pName(n() + "_writeInt"       );}                                            // Integer to write into memory
    String  vRead1IntIndex ()      {return pName(n() + "_read1IntIndex"  );}                                            // Index at which to read first  integer from memory
    String  vRead2IntIndex ()      {return pName(n() + "_read2IntIndex"  );}                                            // Index at which to read second integer from memory
    String  vRead3IntIndex ()      {return pName(n() + "_read3IntIndex"  );}                                            // Index at which to read third integer from memory
    String  vRead1BitIndex ()      {return pName(n() + "_read1BitIndex"  );}                                            // Index within first integer from which to get a bit
    String  vRead2BitIndex ()      {return pName(n() + "_read2BitIndex"  );}                                            // Index within second integer from which to get a bit
    String  vRead3BitIndex ()      {return pName(n() + "_read3BitIndex"  );}                                            // Index within third integer from which to get a bit
//    String  vWriteIntIndex ()      {return pName(n() + "_writeIntIndex"  );}                                            // Index at which to write an integer into memory
//    String  vWriteBitIndex ()      {return pName(n() + "_writeBitIndex"  );}                                            // Index within an integer at which to set a bit
    String vWriteIntEnable ()      {return pName(n() + "_writeIntEnable" );}                                            // Write enable flag
    String vWriteBitEnable ()      {return pName(n() + "_writeBitEnable" );}                                            // Write enable flag
    String         vMemory (int I) {return pName(n() + ".memory["+I+"]"  );}                                            // Memory reference

    String       read1IntV ()      {return                                                                                            vTrace(  "%8d read1Int       %8d",         "pc",       vRead1IntIndex()                                 );}
    String       read2IntV ()      {return                                                                                            vTrace(  "%8d read2Int       %8d",         "pc",       vRead2IntIndex()                                 );}
    String       read3IntV ()      {return                                                                                            vTrace(  "%8d read3Int       %8d",         "pc",       vRead3IntIndex()                                 );}
    String       read1BitV ()      {return                                                                                            vTrace(  "%8d read1Bit       %8d.%8d",     "pc",       vRead1IntIndex(),  vRead1BitIndex()              );}
    String       read2BitV ()      {return                                                                                            vTrace(  "%8d read2Bit       %8d.%8d",     "pc",       vRead2IntIndex(),  vRead2BitIndex()              );}
    String       read3BitV ()      {return                                                                                            vTrace(  "%8d read3Bit       %8d.%8d",     "pc",       vRead3IntIndex(),  vRead3BitIndex()              );}
    String       writeIntV ()      {return                                                                                            vTrace(  "%8d writeInt       %8d<%8d",     "pc",       vRead1IntIndex(),  vWriteInt     ()              );}
    String       writeBitV ()      {return                                                                                            vTrace(  "%8d writeBit       %8d.%8d<%8d", "pc",       vRead1IntIndex(),  vRead1BitIndex(),  vWriteBit());}
    String  read1IntIndexV (Int I) {im(I); return vRead1IntIndex()  + "<= i[arrayData_pcConstant]; "+                                 vTrace(  "%8d read1IntIndex  %8d=%8d",     "pc", ""+I.id, I.vn());}
    String  read2IntIndexV (Int I) {im(I); return vRead2IntIndex()  + "<= i[arrayData_pcConstant]; "+                                 vTrace(  "%8d read2IntIndex  %8d=%8d",     "pc", ""+I.id, I.vn());}
    String  read3IntIndexV (Int I) {im(I); return vRead3IntIndex()  + "<= i[arrayData_pcConstant]; "+                                 vTrace(  "%8d read3IntIndex  %8d=%8d",     "pc", ""+I.id, I.vn());}
    String  read1BitIndexV (Int I) {im(I); return vRead1BitIndex()  + "<= i[arrayData_pcConstant]; "+                                 vTrace(  "%8d read1BitIndex  %8d=%8d",     "pc", ""+I.id, I.vn());}
    String  read2BitIndexV (Int I) {im(I); return vRead2BitIndex()  + "<= i[arrayData_pcConstant]; "+                                 vTrace(  "%8d read2BitIndex  %8d=%8d",     "pc", ""+I.id, I.vn());}
    String  read3BitIndexV (Int I) {im(I); return vRead3BitIndex()  + "<= i[arrayData_pcConstant]; "+                                 vTrace(  "%8d read3BitIndex  %8d=%8d",     "pc", ""+I.id, I.vn());}
//  String  writeIntIndexV (Int I) {im(I); return vWriteIntIndex()  + "<= i[arrayData_pcConstant]; "+                                 vTrace(  "%8d writeIntIndex  %8d=%8d",     "pc", ""+I.id, I.vn());}
//  String  writeBitIndexV (Int I) {im(I); return vWriteBitIndex()  + "<= i[arrayData_pcConstant]; "+                                 vTrace(  "%8d writeBitIndex  %8d=%8d",     "pc", ""+I.id, I.vn());}

    void         read1IntJ ()      {read1Int = units[read1IntIndex];                                                                  jTrace(f("%8d read1Int       %8d",          pc(),      read1IntIndex));}
    void         read2IntJ ()      {read2Int = units[read2IntIndex];                                                                  jTrace(f("%8d read2Int       %8d",          pc(),      read2IntIndex));}
    void         read3IntJ ()      {read3Int = units[read3IntIndex];                                                                  jTrace(f("%8d read3Int       %8d",          pc(),      read3IntIndex));}
    void         read1BitJ ()      {read1Bit = Test.getBit(units[read1IntIndex], read1BitIndex);                                      jTrace(f("%8d read1Bit       %8d.%8d",      pc(),      read1IntIndex,      read1BitIndex));}
    void         read2BitJ ()      {read2Bit = Test.getBit(units[read2IntIndex], read2BitIndex);                                      jTrace(f("%8d read2Bit       %8d.%8d",      pc(),      read2IntIndex,      read2BitIndex));}
    void         read3BitJ ()      {read3Bit = Test.getBit(units[read3IntIndex], read3BitIndex);                                      jTrace(f("%8d read3Bit       %8d.%8d",      pc(),      read3IntIndex,      read3BitIndex));}
    void         writeIntJ ()      {final int i = read1IntIndex, p = units[i]; units[i] = writeInt;                                   jTrace(f("%8d writeInt       %8d<%8d",      pc(),   i, writeInt));}
    void         writeBitJ ()      {final int i = read1IntIndex, b = read1BitIndex, p = units[i]; units[i] = setBit(p, b, writeBit);  jTrace(f("%8d writeBit       %8d.%8d<%8d",  pc(),   i, b, writeBit ? 1 : 0));}
    void    read1IntIndexJ (Int I) {read1IntIndex  = I.i();                                                                           jTrace(f("%8d read1IntIndex  %8d=%8d",      pc(),   I.id, I.i()));}
    void    read2IntIndexJ (Int I) {read2IntIndex  = I.i();                                                                           jTrace(f("%8d read2IntIndex  %8d=%8d",      pc(),   I.id, I.i()));}
    void    read3IntIndexJ (Int I) {read3IntIndex  = I.i();                                                                           jTrace(f("%8d read3IntIndex  %8d=%8d",      pc(),   I.id, I.i()));}
    void    read1BitIndexJ (Int I) {read1BitIndex  = I.i();                                                                           jTrace(f("%8d read1BitIndex  %8d=%8d",      pc(),   I.id, I.i()));}
    void    read2BitIndexJ (Int I) {read2BitIndex  = I.i();                                                                           jTrace(f("%8d read2BitIndex  %8d=%8d",      pc(),   I.id, I.i()));}
    void    read3BitIndexJ (Int I) {read3BitIndex  = I.i();                                                                           jTrace(f("%8d read3BitIndex  %8d=%8d",      pc(),   I.id, I.i()));}
//  void    writeIntIndexJ (Int I) {writeIntIndex = I.i();                                                                            jTrace(f("%8d writeIntIndex  %8d=%8d",      pc(),   I.id, I.i()));}
//  void    writeBitIndexJ (Int I) {writeBitIndex = I.i();                                                                            jTrace(f("%8d writeBitIndex  %8d=%8d",      pc(),   I.id, I.i()));}

    int pc() {return currentPc();}

    Memory copy (Memory SourceMemory, Int SourceOffset, Int TargetOffset, int Width)                                    // Copy the specified memory
     {subStart("Program.Memory.copy");
      final Memory S = SourceMemory;
      new ForCount(Width)
       {void body(Int Index)
         {final Int s = SourceOffset.Add(Index);
          final Int t = TargetOffset.Add(Index);
          new I()                                                                                                       // Set source index
           {void   a() {       SourceMemory.read1IntIndexJ(s);}
            String v() {return SourceMemory.read1IntIndexV(s);}
           };
          new I()                                                                                                       // Read from source memory
           {void   a() {       SourceMemory.read1IntJ();}
            String v() {return SourceMemory.read1IntV();}
           };
          new I()                                                                                                       // Set target index
           {void   a() {       read1IntIndexJ(t);}
            String v() {return read1IntIndexV(t);}
           };
          new I()                                                                                                       // Set write from read
           {final String f = "%8d writeInt=read1Int %8d";
            void   a() {              writeInt        =   S. read1Int;         jTrace(f(f,  pc(),   writeInt));}        // Java updates variables immediately so their value can be used later in the same expression
            String v() {return wei()+vWriteInt() + " <= "+S.vRead1Int() + "; "+vTrace(  f, "pc",  S.vRead1Int());}      // Verilog updates at the end of the block so we have to supply the original expression
           };
          new I()                                                                                                       // Write into target memory
           {void   a() {             writeIntJ();}
            String v() {return wdi()+writeIntV();}
           };
         }
       };
      subFinish();
      return this;
     }

    Memory clearUnit (Int Index)                                                                                        // Clear memory unit
     {subStart("Program.Memory.clearUnit(I)");
      new I()                                                                                                           // Set target index
       {void   a() {       read1IntIndexJ(Index);}
        String v() {return read1IntIndexV(Index);}
       };
      new I()                                                                                                           // Set write from read
       {void   a() {              writeInt = 0;          jTrace(f("%8d writeInt=0",  pc()));}
        String v() {return wei()+vWriteInt() + " <= 0; "+vTrace(  "%8d writeInt=0", "pc"  );}
       };
      new I()                                                                                                           // Write into target memory
       {void   a() {             writeIntJ();}
        String v() {return wdi()+writeIntV();}
       };
      subFinish();
      return this;
     }

    Memory clear ()                                                                                                     // Clear memory in Java
     {subStart("Program.Memory.clear(I)");
      new ForCount(size()) {void  body(Int Index) {clearUnit(Index);}};
      subFinish();
      return this;
     }

    Memory clear (Int Start, int Width)                                                                                 // Clear memory range in Java
     {subStart("Program.Memory.clear(II)");
      new ForCount (Start, Start.Add(Width)) {void  body(Int Index) {clearUnit(Index);}};
      subFinish();
      return this;
     }

    Int getInt (Int I)                                                                                                  // Retrieve the indicated integer from this memory using its first read port
     {final Int       r = new Int();                                                                                    // Integer retrieved from memory holding integers
      final Memory ints = intMemory();                                                                                  // Integer memory

      I.T();                                                                                                            // Retrieve value of the indexing integer from the memory that holds integers so it can be used to index this memory

      new I()                                                                                                           // Set the target index to read from this memory
       {void   a() {        read1IntIndex        =     ints. read1Int;       jTrace(f("%8d getInt1 Get index %8d",  currentPc(), ints. read1Int ));}
        String v() {return vRead1IntIndex() + " <= " + ints.vRead1Int()+"; "+vTrace(  "%8d getInt1 Get index %8d", "pc",         ints.vRead1Int());}
       };

      final I i = new I()                                                                                               // Prepare to write the result read from this memory back into the memory used to hold integers
       {void   a() {       ints. read1IntIndex        =  r.id;                               jTrace(f("%8d getInt2 Set write index %8d",  currentPc(), r.id)                );}
        String v() {return ints.vRead1IntIndex() + " <= arrayData_pcConstant/*"+r.id+"*/;" + vTrace(  "%8d getInt2 Set write index %8d", "pc",        "arrayData_pcConstant");}
       };
      pcConstant(i, r.id);

      new I()                                                                                                           // Write integer obtained from this memory back into the memory that holds integers
       {void   a() {read1Int = r.i = units[I.i]; r.v = true; ints. writeInt        =      read1Int;       ints. writeIntEnable        = true; jTrace(f("%8d getInt3 save %8d = %8d",  currentPc(), ints. read1IntIndex,    read1Int ));}
        String v() {return                                   ints.vWriteInt() + " <= " + vRead1Int()+"; "+ints.vWriteIntEnable() + " <= 1;" + vTrace(  "%8d getInt3 save %8d = %8d", "pc",         ints.vRead1IntIndex(), vRead1Int());}
       };

      new I()                                                                                                           // Complete write
       {void   a() {ints.units[r.id] = r.i; ints. writeIntEnable        = false; jTrace(f("%8d getInt4 disable write",  currentPc()));}
        String v() {return                  ints.vWriteIntEnable() + " <= 0;" +  vTrace(  "%8d getInt4 disable write", "pc"         );}
       };
      return r;
     }

    Bit getBit (Int I, Int J)                                                                                           // Get the bit in the specified byte at the specified position within the byte using the first memory read port and save it into the memory used to store bits
     {final Bit       r = new Bit();
      final Memory ints = intMemory();                                                                                  // Integer memory
      final Memory bits = bitMemory();                                                                                  // Integer memory

      I.T(); J.S();                                                                                                     // Retrieve value of the indexing integers from the memory that holds integers so it can be used to index this memory for the desired bit.  Obviously at some point these instructions should be executed in parallel as we have enough read ports to do so

      new I()                                                                                                           // Set the target index to read from this memory
       {void   a() {        read1IntIndex        =     ints. read1Int;       read1BitIndex        =     ints. read2Int;       jTrace(f("%8d getBit1 Get index %8d.%8d",  currentPc(), ints. read1Int,   ints. read2Int ));}
        String v() {return vRead1IntIndex() + " <= " + ints.vRead1Int()+";"+vRead1BitIndex() + " <= " + ints.vRead2Int()+"; "+vTrace(  "%8d getBit1 Get index %8d.%8d", "pc",         ints.vRead1Int(), ints.vRead2Int());}
       };

      final I i = new I()                                                                                               // Prepare to write the result read from this memory back into the memory used to hold bits
       {void   a() {       bits. read1IntIndex        =  r.id;                               jTrace(f("%8d getBit2 Set write index %8d",  currentPc(), r.id)                );}
        String v() {return bits.vRead1IntIndex() + " <= arrayData_pcConstant/*"+r.id+"*/;" + vTrace(  "%8d getBit2 Set write index %8d", "pc",        "arrayData_pcConstant");}
       };
      pcConstant(i, r.id);

      new I()                                                                                                           // Write bit obtained from this memory back into the memory that holds bits
       {void   a() {read1Int = (r.i = Test.getBit(units[I.i], J.i)) ? 1 : 0; r.v = true; bits. writeInt        =      read1Int;       bits. writeIntEnable        = true; jTrace(f("%8d getBit3 save %8d = %8d",  currentPc(), bits. read1IntIndex,    read1Int ));}
        String v() {return                                                               bits.vWriteInt() + " <= " + vRead1Bit()+"; "+bits.vWriteIntEnable() + " <= 1;" + vTrace(  "%8d getBit3 save %8d = %8d", "pc",         bits.vRead1IntIndex(), vRead1Bit());}
       };

      new I()                                                                                                           // Complete write
       {void   a() {bits.units[r.id] = r.i ? 1 : 0; bits. writeIntEnable        = false; jTrace(f("%8d getBit4 disable write",  currentPc()));}
        String v() {return                          bits.vWriteIntEnable() + " <= 0;" +  vTrace(  "%8d getBit4 disable write", "pc"         );}
       };
      return r;
     }

    Bit getBit (Int I) {return getBit(I.Div(Integer.SIZE), I.Mod(Integer.SIZE));}                                       // Get the bit at the bit indexed location using the first memory port

    Memory putInt (Int Index, Int Value)                                                                                // Write to the indexed memory location the value of the specified source integer
     {final Int I = Index, J = Value;                                                                                   // Load the index and the value
      final Memory ints = intMemory();
      I.S(); J.S2();
      new I()                                                                                                           // Set target index of memory to be written
       {void   a() {read1IntIndex = ints.read2Int;                           jTrace(f("%8d putInt2 Index %8d",  currentPc(), ints. read2Int ));}
        String v() {return vRead1IntIndex() + " <= " + ints.vRead2Int()+"; "+vTrace(  "%8d putInt2 Index %8d", "pc",         ints.vRead2Int());}
       };
      new I()                                                                                                           // Integer to write
       {void   a() {        writeInt        =     ints. read3Int;        writeIntEnable        = true; jTrace(f("%8d putInt3 Value %8d",  currentPc(), ints. read3Int ));}
        String v() {return vWriteInt() + " <= " + ints.vRead3Int()+"; "+vWriteIntEnable() + " <= 1;" + vTrace(  "%8d putInt3 Value %8d", "pc",         ints.vRead3Int());}
       };
      new I()                                                                                                           // Finish write
       {void   a() {units[I.i] = J.i;  writeIntEnable        = false; jTrace(f("%8d putInt4 Finish",  currentPc()));}
        String v() {return            vWriteIntEnable() + " <= 0;" +  vTrace(  "%8d putInt4 Finish", "pc"         );}
       };
      return this;
     }

    Memory putBit (Int I, Int J, Bit K)                                                                                 // Set the bit at the indicated position in the byte at the specified position to the specified value
     {if (I != null) new I()                                                                                            // Set target index if not already set
       {void   a() {              read1IntIndexJ(I);}
        String v() {im(I); return read1IntIndexV(I);}
       };
      if (J != null) new I()                                                                                            // Set target bit index if not already set
       {void   a() {              read1BitIndexJ(J);}
        String v() {im(J); return read1BitIndexV(J);}
       };
      if (K != null) new I()                                                                                            // If a value to be written has been supplied then put it into the control register, else assume the control register has already been set
       {final String f = "%8d writeBit2 %8d, %8d = %8d < %8d";
         void  a() {                     writeBit = K.b();                           jTrace(f(f,  pc(), read1IntIndex,    read1BitIndex,        K.i ? 1 : 0,  writeBit ? 1 : 0));}
        String v() {im(K); return web()+vWriteBit() + "<= b[arrayData_pcConstant]; "+vTrace(  f, "pc", vRead1IntIndex(), vRead1BitIndex(), "b["+K.id+"]", "b["+K.id+"]");}
       };
      else stop("Bit to write not set");                                                                                // Writes must have the Bit to be written as we need the instruction to write enable - the too, too clever scheme for reusing an existing value has melted, thawed, resolved itself into dew and does not work any more
      new I()                                                                                                           // Write into memory
       {void   a() {             writeBitJ();}
        String v() {return wdb()+writeBitV();}
       };
      return this;
     }

    Memory putBit (Int I, Bit K) {putBit(I.Div(Integer.SIZE), I.Mod(Integer.SIZE), K); return this;}                    // Set the bit at the bit indexed position

//D2 Memory references                                                                                                  // References to byte memory

    final class Ref                                                                                                     // Reference into memory
     {final Int   offset = new Int("memoryReferenceOffset");                                                            // Offset of this reference in memory
      final Memory m     = Memory.this;

      Ref (int Offset) {offset.set(Offset);}                                                                            // Offset this ref
      Ref (Int Offset) {offset.set(Offset);}                                                                            // Offset this ref

      Ref        copy (Ref Source, int Width){m.copy(Source.m, Source.offset, offset, Width);       return this;}       // Copy the specified memory possibly from another byte memory
      Ref       clear (int Width)            {m.clear(offset, Width);                               return this;}       // Clear memory by setting its bytes to zero
      Int      getInt (Int I)                {return m.getInt(I.Add(offset));}                                          // Get the int at the indicated position
      Bit      getBit (Int I)                {return m.getBit(I.Add(offset.Mul(Integer.SIZE)));}                        // Get the bit at the bit indexed location
      Int      getInt ()                     {return m.getInt(offset);}                                                 // Get the referenced int
      Ref      putInt (Int J)                {m.putInt (offset, J);                                 return this;}       // Put the referenced int at zero offset in this memory reference
      Ref      putInt (Int I, Int  J)        {m.putInt(        I.Add(offset), J);                   return this;}       // Set the int at the indicated position relative to the start to the specified value
      Ref      putBit (Int I, Bit K)         {m.putBit(        I.Add(offset.Mul(Integer.SIZE)), K); return this;}       // Set the bit at the bit indexed position
      Ref        step (int Width)            {return new Ref(offset.Add(Width));}                                       // Step up from an existing ref to make a new one - only while not executing

      int      getInt (int I) {                                        return units[I+offset.i];}                       // Get an integer immediately when debugging
      boolean  getBit (int I) {final int i = getInt(I / Integer.SIZE); return Test.getBit(i, I % Integer.SIZE);}        // Get a boolean  immediately when debugging

      public String toString () {final StringBuilder s = saySb("Ref: " , offset.i()); return ""+s;}                     // Print memory reference
     } // Ref

//D2 Dump memory                                                                                                        // Dump or print memory

    public String toString ()                                                                                           // Print memory
     {final StringBuilder s = new StringBuilder();
      for (int i = 0, N = size(); i < N; i++) s.append(f("%4d %3d\n", i, units[i]));
      return ""+s;
     }

    String dumpAsDecimal ()                                                                                             // Dump memory in decimal format
     {final int N = 10;
      final StringBuilder s = new StringBuilder();

      s.append(substitute("Memory {i}{n}\n", "i", ""+id, "n", nameSp()));                                               // Title
      s.append("         ");

      for (int i = 0; i < N; i++)                s.append(f("%4d ", i));                                                // Column headers
      s.append("\n");

      for (int i = 0; i < size(); i++)                                                                                  // Memory values
       {if (i % N == 0)                          s.append(f("%08d ", i));

        final int b = units[i];
        if (b != 0) s.append(f("%4d ", b)); else s.append("     ");
        if ((i + 1) % N == 0)                    s.append("\n");
       }
      if (size() % N != 0)                       s.append("\n");
      return ""+s;
     }

    String save ()                                                                                                      // Save memory to a string representation
     {final ByteBuffer b = ByteBuffer.allocate(ib(size()));
      for (int i : units) b.putInt(i);
      return Base64.getEncoder().encodeToString(b.array());
     }

    void reload (String s)                                                                                              // Reload memory from a saved string representation
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

    String memoryModule ()                                                                                              // Verilog module representing memory
     {final StringBuilder s = new StringBuilder(substitute("""

(* blackbox *) module {name}                                                                                            // Memory module
 (input  wire    clock,                                                                                                 // Clock
  input  wire    writeIntEnable,                                                                                        // Write enabled for an integer
  input  wire    writeBitEnable,                                                                                        // Write enabled for a boolean
//input  reg[31:0] writeIntIndex,                                                                                       // Write Integer address
//input  reg[31:0] writeBitIndex,                                                                                       // Write boolean address
  input  integer        writeInt,                                                                                       // Write integer
  input  reg            writeBit,                                                                                       // Write boolean
  input  reg[31:0] read1IntIndex,                                                                                       // Read first  integer address
  input  reg[31:0] read2IntIndex,                                                                                       // Read second integer address
  input  reg[31:0] read3IntIndex,                                                                                       // Read third integer address
  input  reg[31:0] read1BitIndex,                                                                                       // Read first  boolean address
  input  reg[31:0] read2BitIndex,                                                                                       // Read second boolean address
  input  reg[31:0] read3BitIndex,                                                                                       // Read second boolean address
  output integer read1Int,                                                                                              // Integer data read from first memory port
  output integer read2Int,                                                                                              // Integer data read from second memory port
  output integer read3Int,                                                                                              // Integer data read from third memory port
  output reg     read1Bit,                                                                                              // Boolean data read from first memory port
  output reg     read2Bit,                                                                                              // Boolean data read from second memory port
  output reg     read3Bit);                                                                                             // Boolean data read from third memory port
`ifdef __ICARUS__
  integer memory [0:{size}-1];
  integer i;                                                                                                            // Index

  initial for (i = 0; i < {size}; i = i + 1) memory[i] = 0;                                                             // Clear memory to zeros at start

  always @(posedge clock) begin                                                                                         // Synchronous memory access
    if      (writeBitEnable) memory[read1IntIndex][read1BitIndex] <= writeBit;                                          // Write a bit using the first read port index as the write address
    else if (writeIntEnable) memory[read1IntIndex]                <= writeInt;                                          // Write an integer using the first read port index as the write address
    else begin                                                                                                          // Read data from three pots simultaneously
             read1Int <= memory[read1IntIndex ];
             read2Int <= memory[read2IntIndex ];
             read3Int <= memory[read3IntIndex ];
             read1Bit <= memory[read1IntIndex ][read1BitIndex];
             read2Bit <= memory[read2IntIndex ][read2BitIndex];
             read3Bit <= memory[read3IntIndex ][read3BitIndex];
    end
  end
`endif
endmodule
""", "name", m(), "size", ""+size()));

      final FileNames f = blackBoxFolder.same(m());
      writeFile(f.v$(), ""+s);
      blackBoxes.push(f);

      return "\n`ifndef SYNTHESIS"+s+"`endif\n";                                                                        // During testing the black boxes are included in line and iverilog simulates them directly ignoring the black box flag. When using silicon compiler, the black boxes are put in separate files whiach are added to the project phase via the appropriate file set.
     }

    String instantiateModule ()                                                                                         // Instantiate a memory module
     {final StringBuilder s = new StringBuilder();
      s.append("\n// Memory module: "+ n() + " " + name() + "\n");                                                      // Memory module title
      s.append("  reg       "+ pName(      vRead1Bit())+";\n");                                                         // First boolean read from memory
      s.append("  reg       "+ pName(      vRead2Bit())+";\n");                                                         // Second boolean read from memory
      s.append("  reg       "+ pName(      vRead3Bit())+";\n");                                                         // Third boolean read from memory
      s.append("  reg       "+ pName(      vWriteBit())+"; initial "+pName(      vWriteBit()) + "= 0;\n");              // Boolean to write into memory
      s.append("  integer   "+ pName(      vRead1Int())+";\n");                                                         // First integer read from memory
      s.append("  integer   "+ pName(      vRead2Int())+";\n");                                                         // Second integer read from memory
      s.append("  integer   "+ pName(      vRead3Int())+";\n");                                                         // Third integer read from memory
      s.append("  integer   "+ pName(      vWriteInt())+"; initial "+pName(      vWriteInt()) + "= 0;\n");              // Integer to write into memory
      s.append("  reg[31:0] "+ pName( vRead1IntIndex())+"; initial "+pName( vRead1IntIndex()) + "= 0;\n");              // Index at which to read first integer from memory
      s.append("  reg[31:0] "+ pName( vRead2IntIndex())+"; initial "+pName( vRead2IntIndex()) + "= 0;\n");              // Index at which to read second integer from memory
      s.append("  reg[31:0] "+ pName( vRead3IntIndex())+"; initial "+pName( vRead3IntIndex()) + "= 0;\n");              // Index at which to read third integer from memory
      s.append("  reg[31:0] "+ pName( vRead1BitIndex())+"; initial "+pName( vRead1BitIndex()) + "= 0;\n");              // Index within first integer from which to get a bit
      s.append("  reg[31:0] "+ pName( vRead2BitIndex())+"; initial "+pName( vRead2BitIndex()) + "= 0;\n");              // Index within second integer from which to get a bit
      s.append("  reg[31:0] "+ pName( vRead3BitIndex())+"; initial "+pName( vRead3BitIndex()) + "= 0;\n");              // Index within third integer from which to get a bit
      s.append("  reg       "+ pName(vWriteIntEnable())+"; initial "+pName(vWriteIntEnable()) + "= 0;\n");              // Write enable when writing integer data into memory
      s.append("  reg       "+ pName(vWriteBitEnable())+"; initial "+pName(vWriteBitEnable()) + "= 0;\n");              // Write enable when writing boolean data into memory
      s.append("  "+ connectMemoryModule());                                                                            // Connect to memory module
      return ""+s;
     }

    String connectMemoryModule ()                                                                                       // Connect main process to memory module
     {return substitute("""

  {moduleName} {n}                                                                                                      // Memory module {name}
   (.clock           (clock),                                                                                           // Clock
    .writeIntEnable  ({n}_writeIntEnable ),                                                                             // Write enabled for an integer
    .writeBitEnable  ({n}_writeBitEnable ),                                                                             // Write enabled for a boolean
    .writeInt        ({n}_writeInt       ),                                                                             // Write data
    .writeBit        ({n}_writeBit       ),                                                                             // Write data
    .read1IntIndex   ({n}_read1IntIndex  ),                                                                             // Read first integer address
    .read2IntIndex   ({n}_read2IntIndex  ),                                                                             // Read second integer address
    .read3IntIndex   ({n}_read3IntIndex  ),                                                                             // Read second integer address
    .read1BitIndex   ({n}_read1BitIndex  ),                                                                             // Read first boolean address
    .read2BitIndex   ({n}_read2BitIndex  ),                                                                             // Read second boolean address
    .read3BitIndex   ({n}_read3BitIndex  ),                                                                             // Read second boolean address
    .read1Int        ({n}_read1Int       ),                                                                             // First integer data read
    .read2Int        ({n}_read2Int       ),                                                                             // Second integer data read
    .read3Int        ({n}_read3Int       ),                                                                             // Third integer data read
    .read1Bit        ({n}_read1Bit       ),                                                                             // First  boolean data read
    .read2Bit        ({n}_read2Bit       ),                                                                             // Second boolean data read
    .read3Bit        ({n}_read3Bit       ));                                                                            // Third boolean data read
""", "moduleName", m(), "n", n(), "name", name());
     }

//D2 Memory Dumps                                                                                                       // Overridable dump memory methods for Java and Verilog

    String dumpJava ()    {return dumpAsDecimal();}
    String dumpVerilog () {return dumpVerilogMemoryInDecimalName()+"();";}
    String memory(String Index) {return substitute("{n}.memory[{i}]", "n", n(), "i", Index);}                           // Verilog to get the indexed location in memory

   } // Memory

  interface Locatable {Bint getLocation();}                                                                             // The location of an object in memory

//  String dumpMemory () {return program().unitMemory.dumpAsDecimal();}                                                 // Dump memory in decimal format

  String saveMemories ()                                                                                                // Save all the memories to an array of strings
   {final StringJoiner j = new StringJoiner(", ");
    for (Memory m : memories()) j.add(q(m.save()));
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
   } // I

  final class Label                                                                                                     // Label jump targets in the program
   {int offset;                                                                                                         // The instruction location to which this label applies
    Label ()    {set(); program().labels.push(this);}                                                                   // A label assigned to an instruction location
    void set () {offset = program().code.size();}                                                                       // Reassign the label to an instruction
   } // Label

  void appendJavaTrace(String Message) {appendFile(traceFiles.java$(), Message);}                                       // Append to the java trace file

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

// D2 Execute                                                                                                           // Execute the code in the current program

  void execute ()                                                                                                       // Execute the current code
   {if (immediate()) return;                                                                                            // The code has already been executed interpretively

    if (codeSize() == 0)        stop("No code to execute");                                                             // Complain if there is no code to execute
    else if (!generateVerilog) say(f("            Code size: %,12d", codeSize()));                                      // Code size check unless we are executing Veilog in which case the code size will be printed after the preparation of the Verilog equivalent so that the uncompressed code size can be compared with the compressed code size
    traceFiles.delete_java();                                                                                           // Clear Java trace file
    dumpProgramState("Finished");                                                                                       // Dump program state at end of execution

    currentPc   = pc = 0;                                                                                               // Reset program counter to start of program
    final int N = codeSize();                                                                                           // Number of instructions

    intMemory().reallocate(nextIntId);                                                                                  // Resize integer memory now we know how big to make it
    bitMemory().reallocate(nextBitId);                                                                                  // Resize boolean memory now we know how big to make it

    initializeJavaMemory();                                                                                             // Initialize memory
    initializeJavaVars();                                                                                               // Initialize variables

    for(steps = 0; steps < maxSteps && pc >= 0 && pc < N; ++steps)                                                      // Execute each instruction within a specified number of steps
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

    if (steps >= maxSteps) stop("Out of steps after step:", steps);                                                     // Show ran out of steps
    else if (!generateVerilog) say(f("            Execution: %,12d", steps));                                           // Show number of steps unless we are going to print this in during the verilog process

    if (generateVerilog)                                                                                                // Run verilog
     {final GenerateVerilog g = new GenerateVerilog();                                                                  // Generate corresponding Verilog code and run it
      final StringBuilder  message = new StringBuilder(g.message());                                                    // Message describing outcome of execution (all on one line)
      final StringBuilder     json = new StringBuilder(g.json   ());                                                    // Json describing outcome of execution (all on one line)
//podman run {c} --rm --network host --userns=keep-id -v {f}:{f} -w {f} "{image}" python3 {p}                             # Silicon compiler command
//podman run {c} --rm --network host --user=phil      -v {f}:{f} -w {f} "{image}" python3 {p}                             # Silicon compiler command

      final String scCmd = github_actions                                                                               // Silicon compiler command to perform ASIC flow
        ? substitute("""
cd {f}; python3 {p}                                                                                                     # Silicon compiler command already inside container
""",
"f", verilogTestFolder.folder,                                                                                          // Work folder
"p", verilogTestFolder.py())                                                                                            // Python file

        : substitute("""
cd {f} && docker run {c} --rm --network host -v {f}:{n} -w {n} "{i}" python3 {p}                                        # Silicon compiler command running a new container
""",
"c", "", //"--userns=keep-id",                                                                                          // Use the same userid inside the container to avoid file permission problems
"f", verilogTestFolder.folderWithCwd(),                                                                                 // Work folder
"i", siliconCompilerImageLocal,                                                                                         // Python to run in which image
"n", fp("/home/phil/btreeList/verilog/test", verilogTestFolder.folder),                                                 // Folder name in container - which we control
"p", verilogTestFolder.py());

      final String ysCmd = substitute("""
cd {f}; yosys -q {y}                                                                                                    # Yosys command
""", "f", verilogTestFolder.folderWithCwd(), "y", verilogTestFolder.ys());
                                                                                                                        // Yosys command
      g.generateSiliconCompiler(); /*if (runSiliconCompiler)*/ say("C=sc; " + scCmd);                                   // Generate python to drive silicon compiler
      g.generateYosys();           /*if (runYosys)          */ say("C=ys; " + ysCmd);                                   // Generate tcl to drive yosys
      g.lef();                                                                                                          // Generate LEF files
      g.gds();                                                                                                          // Generate gds files to match lef files

      if (runVerilog)                                                                                                   // Run verilog
       {traceFiles.delete_v();                                                                                          // Clear Verilog trace file
        final StringBuilder s = new StringBuilder();
        final boolean       r = github_actions || aws_run;                                                              // Running remotely
      //final String        v = "vvp -M../../vpi -mwall_time " +testName();                                             // Command to run verilog simulation
        final String        v = "vvp " +testName();                                                                     // Command to run verilog simulation

        s.append(substitute("cd {f}; rm -f {n}; iverilog -g2012 -o {n} {v} && {t} vvp {n}",                             // Construct command
                            "f", verilogTestFolder.folder,
                            "n", testName(),
                            "v", verilogTestFolder.v(),
                            "t", github_actions || aws_run ? "" : f("timeout %ds ", verilogTimeOut)));                  // Time out if running locally.  The progfrsam will return a coed of 124 if it times out

        final ExecCommand x = new ExecCommand(s);                                                                       // Execute verilog commands
        message.append(f(" %11.2f seconds for: %s",                    x.timer.seconds(), x.command));                  // Execution time of command in message
        json   .append(f(", \"seconds\": %11.2f, \"command\": \"%s\"", x.timer.seconds(), x.command));                  // Execution time of command in json

        ok(readFileAsString(traceFiles.v$()).equals(readFileAsString(traceFiles.java$())));                             // Compare corresponding java and Verilog trace files -  says failed if it fails and provides a traceback

        if (runSiliconCompiler)                                                                                         // Run synthesis in a podman container containing silicon compiler and the associated tools needed for ASIC
         {final ExecCommand X = new ExecCommand(scCmd);                                                                 // Execute silicon compiler commands
          message.append(f(" %11.2f seconds for: %s",                    X.timer.seconds(), X.command));                // Execution time of command in message
          json   .append(f(", \"seconds\": %11.2f, \"command\": \"%s\"", X.timer.seconds(), X.command));                // Execution time of command in json
         }

        if (runYosys)                                                                                                   // Run yosys to get a faster check on whether the verilog can be synthesized
         {final ExecCommand X = new ExecCommand(ysCmd);                                                                 // Execute silicon compiler commands
          message.append(f(" %11.2f seconds for: %s",                    X.timer.seconds(), X.command));                // Execution time of command in message
          json   .append(f(", \"seconds\": %11.2f, \"command\": \"%s\"", X.timer.seconds(), X.command));                // Execution time of command in json
          ok(X.exitCode == 0);                                                                                          // Check return code from Yosys
         }
       }

      say(message);                                                                                                     // Report Verilog statistics
      appendFile(verilogLogFolder.log$(),  message+ "\n");                                                              // Log in text format
      appendFile(verilogLogFolder.json$(), "{"+json+"}\n");                                                             // Log in json format
     }
   }

  void variableNotSet (String Type, String Name)                                                                        // Variable not yet set message
   {final I i = executing();
    final String m = (Name != null ? '"'+Name+'"'+", " : "") + "has not been set yet";
    if (i != null) stop(Type, m, i.traceBack, "====");                                                                  // With traceback on failing instruction if possible
    else           stop(Type, m);                                                                                       // No traceback available
   }

//D2 Dump                                                                                                               // Dump the state of the program at requested locations during execution of both Java and Verilog so that the evolution of memories, variables, registers can be confirmed

  class DumpLocations                                                                                                   // Create a dump location definition to write the title of the dump without having to use string parameters which do not seem to work on iverilog
   {final Stack<Location> locations = new Stack<>();                                                                    // Locations in the code at which dumps have been requested
    final TreeSet<String>   defined = new TreeSet<>();                                                                  // Location dump routines defined

    class Location                                                                                                      // Create a dump location definition to write the title of the dump without having to use string parameters which do not seem to work on iverilog
     {final int location;                                                                                               // Location in program of dump
      final String title;                                                                                               // Title of dump

      Location(int Location, String Title)
       {location = Location; title = Title;
        locations.push(this);
       }

      String called() {return f("dumpLocation_"+location+"(); ");}                                                      // The name of the dump location

      String define()                                                                                                   // Define a dump location
       {final String n = called();
        if (defined.contains(n)) return ""; else defined.add(n);                                                        // The dump routine should only be defined once
        return  substitute("""

  task automatic {name}                                                                                                 // Write dump title
    begin
`ifndef SYNTHESIS
      $fwrite(traceFile, "{title}\\n"); $fflush(traceFile);
`endif
    end
  endtask
""", "name", called(), "title", title);
       }
     } //Location
   } //DumpLocations

  void initializeJavaMemory () {for(Memory m : memories()) for (int i = 0, N = m.size(); i < N;++i) m.units[i] = 0;}    // Clear all of memory to zero
  void dumpJavaMemories ()     {for(Memory m : memories()) appendJavaTrace(m.dumpJava());}                              // Dump all the memories

  void initializeJavaVars()                                                                                             // Initialize java variables so that they start with a known value despite being invalid because the valid bit is not tracked in the verilog version
   {for (Int i : ints()) {i.i = 0;     i.v = false;}
    for (Bit b : bits()) {b.i = false; b.v = false;}
   }

  void dumpJavaVariables ()                                                                                             // Dump all memories and variables to the java trace file
   {final StringBuilder s = new StringBuilder();
    for (Int  i  : ints())                                                                                              // Dump ints
     {s.append(f("Int  %8d ==    %8d", i.id, i.i));
      if (i.name != null) s.append(" "+i.name);
      s.append('\n');
     }
    for (Bit b : bits())                                                                                                // Dump bools
     {if (b.nd) continue;                                                                                               // Omit bools that were created as a result of testing the validity of an Int because the Verilog code does not retain this information
      s.append(f("Bit  %8d ==    %8d", b.id, b.i ? 1 : 0));
      if (b.name != null) s.append(" "+b.name);
      s.append('\n');
     }
    appendJavaTrace(""+s);
   }

  void dumpJavaRegisters ()                                                                                             // Dump all memories and variables to the java trace file. Cannot dump verilog array definmitions because they have not been created yet.
   {final StringBuilder s = new StringBuilder();
    s.append(f("     currentPc = %8d\n",         pc-1));
    appendJavaTrace(""+s);
   }

  void dumpJava ()                                                                                                      // Dump all memories and variables to the java trace file
   {dumpJavaMemories();
    dumpJavaVariables();
    dumpJavaRegisters();
   }

  void dumpProgramState (String Title)                                                                                  // Dump program memories and variables
   {new I()
     {void    a()     {appendJavaTrace(Title+"\n");                                         dumpJava();}
      String  v()     {return dumpLocations.new Location(instructionNumber, Title).called()+dumpVerilog();}             // Dump entire state of program: memories, variables, registers
      boolean trace() {return false;}
     };
   }

  void dumpProgramMemories (String Title)                                                                               // Dump program memories
   {new I()
     {void    a()     {appendJavaTrace(Title+"\n");                                         dumpJavaMemories();}
      String  v()     {return dumpLocations.new Location(instructionNumber, Title).called()+dumpVerilogMemories();}
      boolean trace() {return false;}
     };
   }

  void dumpProgramVariables (String Title)                                                                              // Dump program variable
   {new I()
     {final int location = codeSize()-2;                                                                                // Record instruction location
      void    a()     {appendJavaTrace(Title+"\n");                                         dumpJavaVariables();}
      String  v()     {return dumpLocations.new Location(instructionNumber, Title).called()+dumpVerilogVariables();}
      boolean trace() {return false;}
     };
   }

  void dumpProgramRegisters (String Title)                                                                              // Dump program registers
   {new I()
     {final int location = codeSize();                                                                                  // Record instruction location
      void    a()     {appendJavaTrace(Title+"\n");                                         dumpJavaRegisters();}
      String  v()     {return dumpLocations.new Location(instructionNumber, Title).called()+dumpVerilogRegisters();}
      boolean trace() {return false;}
     };
   }

  <A, B> void ok (Supplier<A> a, B b)                                                                                   // Test a result of delayed execution against a known result while the program is still executing
   {new I()
     {void a() {if (!ok(""+a.get(), ""+b)) if (traceBack != null) say("====\n", traceBack);}
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

  class GenerateVerilog                                                                                                 // Generate verilog
   {final String         name = testName();                                                                             // Name of test
    final String       source = fnx(mainFileName());                                                                    // Main source file
    final String     dateTime = dateTime();                                                                             // Date and time of test
    final int       execSteps = steps;                                                                                  // Number of execution steps
    final int        codeSize = codeSize();                                                                             // Original uncompressed code size
    final int instructionSets;                                                                                          // Number of instruction equivalence classes
    final BufferedWriter  out;                                                                                          // Write verilog to the file represented by this writer
    final String     traceFile = traceFiles.v();                                                                        // Trace file name relative to Verilog code
    final String      codeFile = verilogTestFolder.v$();                                                                // Code file
    final String        indent = " ".repeat(6);                                                                         // Indentation for verilog code
    final int       sizeMemory = unitMemory != null ? unitMemory.size() : 0;                                            // Size of memory
    final int     numberOfInts = nextIntId;                                                                             // Number of integers needed
    final int     numberOfBits = nextBitId;                                                                             // Number of booleans needed
    final String dimensionInts = ""+(nextIntId-1);                                                                      // Number of integers needed
    final String dimensionBits = ""+(nextBitId-1);                                                                      // Number of booleans needed
    final InstructionMatches instructionMatches = new InstructionMatches();                                             // Mapping from instructions to blocks of matching instructions

    void put(final String Verilog)                                                                                      // Write some verilog
     {try {out.write(Verilog);}
      catch(Exception e) {stop("Unable to write to file:", codeFile, e, fullTraceBack(e));}
     }

    String message()                                                                                                    // Message describing statistics
     {return f("%s:  %30s  %,9d execution,  %3d after,  %,9d before, %7.4f percent",
               dateTime,  source+"."+name, execSteps, instructionSets, codeSize, percent());
     }

    String json()                                                                                                       // Json describing statistics
     {final StringBuilder s = new StringBuilder();
      s.append(f(("'log': 'verilogStatistics', 'dateTime': %s, 'sourceFile': %s, 'testName': %s,"+
                " 'executionSteps': %8d, 'instructions': %4d, 'codeSize': %8d, 'percent': %9.4f").replaceAll("'", "\""),
                q(dateTime),  pqName(source), pqName(name), execSteps, instructionSets, codeSize, percent()));

      s.append(f( ", \"suppressInstructionTracing\" : \"%d\"",  suppressInstructionTracing ? 1 : 0));                   // Do not write a trace record for each instruction - the dump of program state at the end of the run will be the test of whether the program ran as expected
      s.append(f(      ", \"suppressTraceComments\" : \"%d\"",       suppressTraceComments ? 1 : 0));                   // Add trace comments to trace output to locate the point in the java code at which the verilog was generated - requires a lot of memory
      s.append(f(       ", \"compressInstructions\" : \"%d\"",        compressInstructions ? 1 : 0));                   // Compress out identical instructions
      s.append(f(  ", \"compressInstructionLabels\" : \"%d\"",   compressInstructionLabels ? 1 : 0));                   // Reduce the instruction loop case statement by using an array to find the first instruction in the equivalence class associated with each instruction and recording that single instruction id as the sole label for each case statement possibilities
      s.append(f(            ", \"generateVerilog\" : \"%d\"",             generateVerilog ? 1 : 0));                   // Generate verilog version of each program
      s.append(f(                 ", \"runVerilog\" : \"%d\"",                  runVerilog ? 1 : 0));                   // Execute  verilog version of each program
      s.append(f(", \"suppressNamesInInstructions\" : \"%d\"", suppressNamesInInstructions ? 1 : 0));                   // Include names in instructions
      s.append(f(",          \"runSiliconCompiler\" : \"%d\"",          runSiliconCompiler ? 1 : 0));                   // Run synthesis
      if (github_commit_sha != null) s.append(f(", \"github_commit_sha\" : \"%s\"", github_commit_sha));                // Commit sha if available
      return ""+s;
     }

    double percent()                                                                                                    // Percentage reduction in program size after compression based on interior verilog for each instruction
     {final int m = instructionSets, c = code.size();
      return 100 * (c - m) / (double)c;
     }

    GenerateVerilog ()                                                                                                  // Generate the Verilog corresponding to the java code
     {int countInstructionSets = 0;                                                                                     // Count of instructions in instruction set before we make it final

//      intMemory = new Memory(numberOfInts, "Ints")                                                                      // Memory for integers
//       {String            dumpJava () {return "";}
//        String         dumpVerilog () {return "";}
//       };
//
//      bitMemory = new Memory(numberOfBits, "Bits")                                                                      // Memory for bits
//       {String dumpJava ()    {return "";}
//        String dumpVerilog () {return "";}
//       };

      for(I i : code) {compiling(i); instructionMatches.add(i);}                                                        // Match instructions
      pcConstantArray = verilogArrays().new Array("pcConstant", pcConstant());                                          // Instruction to variable or memory used by the instruction. Defined here so that the state enum can be generated
      pcMatchSetArray = verilogArrays().new Array("pcMatchSet", instructionMatches.pcMatchSet());                       // Translate instruction numbers to first instances of that instruction to compress labels on execution loop case statement

      try {out = Files.newBufferedWriter(Path.of(codeFile));}
      catch(Exception e) {stop("Unable to open file:", codeFile, e, fullTraceBack(e)); throw new RuntimeException(e);}

      /*Module*/put(substitute("""
`ifdef SYNTHESIS
module {name} (                                                                                                         // Bint machine - callable module for synthesis
""", "name", name));

      for(Int i : ints) if (i.in)  put("  input  wire[31:0] i_"+i.name+",\n");                                          // Input ports  - silicon compiler cannot handle logic or integer or signed in port definitions
      for(Int i : ints) if (i.out) put("  output reg [31:0] o_"+i.name+",\n");                                          // Output ports - silicon compiler cannot handle logic or integer or signed in port definitions
      for(Bit b : bits) if (b.in)  put("  input  wire       i_"+b.name+",\n");                                          // Input ports  - silicon compiler cannot handle logic or integer or signed in port definitions
      for(Bit b : bits) if (b.out) put("  output reg        o_"+b.name+",\n");                                          // Output ports - silicon compiler cannot handle logic or integer or signed in port definitions

      /*Parameters*/put(substitute("""
  input wire clock,                                                                                                     // Clock pin
  input wire reset);                                                                                                    // Reset pin
`else
module {name};                                                                                                          // Bint machine - standalone for execution
`endif
""", "name", name));

        /*Execution State Variables*/put("""
`ifndef SYNTHESIS
  reg                 clock;                                                                                            // Program clock to drive instruction execution
  wire                reset;                                                                                            // Program reset
`endif
  integer                pc;                                                                                            // Program counter for stepping through user code
  integer         traceFile;                                                                                            // Write verilog trace records to this file
""");

//      /*Declare integers*/if (numberOfInts > 0) put(substitute("""
//  integer                 i[{i}:0]; integer index_ints;                                                                 // Integers
//  initial begin                                                                                                         // Clear integers and booleans in verilog
//    for(index_ints = 0; index_ints <= {i}; index_ints = index_ints + 1) i[index_ints] = 0;
//  end
//""", "i", dimensionInts));
//
//      /*Declare booleans*/if (numberOfBits > 0) put(substitute("""
//  reg                     b[{b}:0]; integer index_bits;                                                                 // Booleans
//  initial begin                                                                                                         // Clear integers and booleans in verilog
//    for(index_bits = 0; index_bits <= {b}; index_bits = index_bits + 1) b[index_bits] = 0;
//  end
//""", "b", dimensionBits));

      for(VerilogArrays.Array a : verilogArrays.arrays()) put(a.connectModule());                                       // Connect to verilog array modules
      for(Memory m              : memories              ) put(m.instantiateModule());                                   // Instantiate each memory

      /*Execute*/put("""

`ifndef SYNTHESIS
  initial begin
    #10;                                                                                                                // Let all the initialization complete
    clock = 0;                                                                                                          // Initialize the clock - failure to do this will result in an infinite loop as the clock cannot transition on an undefined value
    forever #1 clock = ~clock;                                                                                          // Execute instructions
  end                                                                                                                   // Execute instructions
`endif                                                                                                                  // Clock - only needed during icarus verilog simulation not during synthesis

  always_ff @(posedge clock) begin                                                                                      // Decode and execute instructions by iterating a case statement
""");

      if (!compressInstructions || !compressInstructionLabels)                                                          // No compression of instruction labels
        /*Execute case*/put("""
    case(pc)
""");
      else                                                                                                              // Compress instruction labels
        /*Execute case*/put(substitute("""
    case ({pcMatchSet})                                                                                                 // Decode the instruction to be executed
""", "pcMatchSet", pcMatchSetArray.dataRegisterName()));

      if (compressInstructions)                                                                                         // Compress instructions
       {if  (!compressInstructionLabels)                                                                                // Compress by writing all labels against the first instance of an instruction
         {for (InstructionMatches.Match m : instructionMatches.sequence)                                                // Each block of identical instructions
           {final String v = m.first().formatVerilogCode(m.verilog);
            put(indent + m.labels() + v);
           }
         }
        else                                                                                                            // Compress each block to a single sequential instruction and map pc at head of case statement accordingly
         {for (InstructionMatches.Match m : instructionMatches.sequence)                                                // Each block of identical instructions
           {final String v = m.first().formatVerilogCode(m.verilog);
            put(indent + f("%4d", m.block) + v);
           }
         }
       }
      else                                                                                                              // Write instructions without compression
       {for (I i : program().code)                                                                                      // Each identical instruction
         {compiling(i);
          final String v = i.formatVerilogCode(i.interiorVerilog());
          put(indent + f("%4d", i.instructionNumber) + v);
         }
       }
      countInstructionSets = instructionMatches.matches.size();                                                         // Instruction set size

      /* Execute default*/put("""
      default: begin
`ifndef SYNTHESIS
        $fclose(traceFile);                                                                                             // Close trace file
        $finish(0);
`endif
      end
    endcase
  end
""");

      put("`ifdef SYNTHESIS\n");
      for(Int i : ints)                                                                                                 // Update variables from inputs and outputs from variables during synthesis
       {if (i.in) put(substitute("""
  always_ff @(posedge clock) begin if (reset) i[{i}] <= 0;  else i[{i}]  <= $signed(i_{n}); end                         // Update variables from inputs - the inputs cannot be signed because silicon compiler seems to have difficulty with signed parameters.
""", "i", ""+i.id, "n", i.name));

        if (i.out) put(substitute("""
  always_ff @(posedge clock) begin if (reset) o_{n} <= 0; else o_{n} <= i[{i}];  end                                    // Update variables from outputs - Verilog just assigns bits without interpreting whether there is a sign present or not
""", "i", ""+i.id, "n", i.name));
         }
      put("`endif\n");

      /*Clear registers*/put(substitute("""

  initial begin                                                                                                         // Clear registers
    pc           = 0;
"""));

      /*Open trace file*/put(substitute("""

`ifndef SYNTHESIS
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
`endif
  end
""", "traceFile", traceFile));

      for(Memory                 m : memories())                put(dumpVerilogMemoryInDecimal(m));                     // Dump memories in Verilog
      for(DumpLocations.Location d : dumpLocations().locations) put(d.define());                                        // Locations in program that have requested dumps

      put(dumpVerilogVariables());                                                                                      // Dump verilog variables task
      put(dumpVerilogRegisters());                                                                                      // Dump verilog variables task
      /*End*/put("""
endmodule
""");
      for(VerilogArrays.Array    a : verilogArrays.arrays())    put(a.module());                                        // Write memory module definitions for read only arrays
      for(Memory                 m : memories())                put(m.memoryModule());                                  // Memory modules

      try (out) {}                                                                                                      // Close output file
      catch(Exception e)                                                                                                // Failed to generate verilog
       {stop(e, fullTraceBack(e));                                                                                      // Write the error and stop
       }
      instructionSets = countInstructionSets;                                                                           // Finalize instruction set size
     }

//D2 Dumps                                                                                                              // Dump memory, variables, registers

    String dumpVerilogMemoryInDecimal (Memory M)                                                                        // Dump memory in decimal
     {final String h = substitute("""

  task {dumpVerilogMemoryInDecimalName};                                                                                // Dump verilog memories in decimal
    integer i;
    integer I;
    parameter integer N = 10;
    begin
`ifndef SYNTHESIS
""", "dumpVerilogMemoryInDecimalName", M.dumpVerilogMemoryInDecimalName());

      final String m = substitute("""
      $fwrite(traceFile, "Memory {memoryId}{n}\\n");
""", "memoryId", M.i(), "n", M.nameSp());

      final String t = substitute("""
      $fwrite(traceFile, "         ");
      for (i = 0; i < N; i = i + 1)   $fwrite(traceFile, "%4d ", i);
                                      $fwrite(traceFile, "\\n");

      for (i = 0; i < {size}; i = i + 1)
      begin
        if (i % N == 0)               $fwrite(traceFile, "%08d ", i);

        I = {memoryName}.memory[i];

        if (I != 0)                   $fwrite(traceFile, "%4d ", I);
        else                          $fwrite(traceFile, "     ");

        if ((i + 1) % N == 0)         $fwrite(traceFile, "\\n");
      end

      if ({size} % N != 0) $fwrite(traceFile, "\\n");
      $fflush(traceFile);
`endif
    end
  endtask
""", "memoryName", M.n(), "size", ""+M.size());

     return h + m + t;
    }

//D2 Instruction Matching                                                                                               // Classify instructions into blocks of identical instructions and then compressing out the duplicates to reduce code size

    class InstructionMatches                                                                                            // Matching set of instructions
     {final TreeMap<String,  Match> matches  = new TreeMap<>();                                                         // Matches by verilog
      final TreeMap<Integer, Match> inMatch  = new TreeMap<>();                                                         // Matches by instruction number
      final Stack           <Match> sequence = new Stack  <>();                                                         // Sequence of matches

      class Match                                                                                                       // Matching set of instructions
       {final String   verilog;                                                                                         // Interior verilog for this match
        final int        block = sequence.size();                                                                       // Match number in sequence
        final Stack<I> matches = new Stack<I>();                                                                        // Instructions in this set of identical instructions

        Match(String Verilog, I I) {verilog = Verilog; sequence.push(this); matches.push(I);}                           // Create a new match set and add it to the existing matching instructions

        void push (I I) {matches.push(I);}                                                                              // Add another instruction to the match set
        int  size ()    {return matches.size();}                                                                        // Number of instructions in the match set
        I   first ()    {return matches.size() == 0 ? null : matches.firstElement();}                                   // First instruction in match set

        String labels()                                                                                                 // Instruction numbers formatted as a comma separated list for attachment to the always case statement
         {final StringJoiner j = new StringJoiner(", ");
          for (I i : matches) j.add(""+i.instructionNumber);
          return ""+j;
         }
       } // Match

      void add(I I)                                                                                                     // Add an instruction
       {final String v = I.interiorVerilog();
        if (matches.containsKey(v))                                                                                     // Add to an existing set of matches
         {final Match m = matches.get(v);
          m.push(I);
          inMatch.put(I.instructionNumber, m);
         }
        else                                                                                                            // Create a new set of matches
         {final Match m = new Match(v, I);
          matches.put(v, m);
          inMatch.put(I.instructionNumber, m);
         }
       }

      Match firstMatch(I I)                                                                                             // Is this instruction the first of a match block of equivalent instructions
       {final Match m = inMatch.get(I.instructionNumber);
        return m.matches.firstElement() == I ? m : null;
       }

      TreeMap<Integer,Integer> pcMatchSet()                                                                             // Match instructions to sets of equivalent instructions
       {final TreeMap<Integer,Integer> M = new TreeMap<>();                                                             // Instruction number to class of equivalent instructions
        for (Match m : sequence) for (I i : m.matches) M.put(i.instructionNumber, m.block);                             // Instruction to matching instructions block number
        return M;
       }
     } // InstructionMatches

//D2 Silicon compiler                                                                                                   // Create driving python to compile the verilog code using silicon compiler

    String generateSiliconCompiler ()                                                                                   // Python code to drive silicon compiler
     {final StringBuilder s = new StringBuilder();                                                                      // Generated code
      final StringBuilder b = new StringBuilder();                                                                      // Black boxes
      final FileNames     t = verilogTestFolder;

      for (FileNames x : blackBoxes) b.append("    macros.add_file(\""+x.minus(verilogTestFolder).v$()+"\")\n");        // Black box verilog files relative to input file

      s.append(substitute("""
#!/usr/bin/env python3
import sys
from siliconcompiler         import ASIC, Design
from siliconcompiler.tools.yosys import YosysStdCellLibrary
from siliconcompiler.targets import skywater130_demo

def gen(module):
  design = Design     (module)                                                                                          # Silicon compiler work flow driver
  design.set_topmodule(f"{module}", fileset="rtl")                                                                      # Name the top module
  design.add_file     (f"{v}",      fileset="rtl")                                                                      # Verilog
  design.add_define   ("SYNTHESIS", fileset="rtl")                                                                      # Set a macro variable to differentiate between testing using iverilog and synthesizing with silicon compiler

  macros = YosysStdCellLibrary()
  macros.set_name("{n}_macros")
  macros.set_dataroot("local", __file__)
  macros.add_asic_pdk("skywater130")

# Physical view for place-and-route
  with macros.active_dataroot("local"), macros.active_fileset("models.physical"):
    macros.add_file("{l}")                                                                                              # Add lef file
    macros.add_file("{g}")                                                                                              # Add gdes file
    macros.add_asic_aprfileset()

# Blackbox stubs for synthesis
  with macros.active_dataroot("local"), macros.active_fileset("models.blackbox"):
{b}  macros.add_yosys_blackbox_fileset("models.blackbox")

  project = ASIC(design)                                                                                                # Create a specific ASIC design
  project.add_fileset(["rtl"])                                                                                          # Source files
  skywater130_demo(project)                                                                                             # Technology

  project.add_asiclib(macros)
  project.constraint.area.set_diearea_rectangle({dx}, {dy}, coremargin=1)                                               # Area constraints

  project.logger.setLevel("WARNING")                                                                                    # "DEBUG" or "INFO" (the default) "WARNING", "ERROR"
  project.check_manifest()                                                                                              # Check set up
  project.run()                                                                                                         # Run asic flow
  project.summary()                                                                                                     # Summarize results

if __name__ == "__main__":
    gen(sys.argv[1] if len(sys.argv) > 1 else "{n}")
""", "lef", t.lef$(), "v", t.v(), "g", t.gds(), "l", t.lef(), "n", name, "b", ""+b,
"dx", ""+scDieAreaX, "dy", ""+scDieAreaY));

      return writeFile(t.py$(), s);
     }

    void lef ()                                                                                                         // Generate LEF macros in one file
     {final Lef l = new Lef();
      for(VerilogArrays.Array a : verilogArrays().arrays()) l.new ArrayLef(a.arrayName());                              // Lef for each array
      for(Memory              m : memories())               l.new MemoryLef(m.m());                                     // Lef for each memory
      l.write(verilogTestFolder.lef$());
     }

    void gds ()                                                                                                         // Generate gds blocks pretending to be the designs for the black boxes
     {final Gds g = new Gds(verilogTestFolder.gds$());
      for(VerilogArrays.Array a : verilogArrays.arrays()) g.rectangle(a.arrayName(), 100, 200, 68, 20);                 // Gds block for each  array - non functional just to allow silicon compiler to complete
      for(Memory              m : memories())             g.rectangle(m.m(),         100, 100, 68, 20);                 // Gds block for each memory - non functional just to allow silicon compiler to complete
      g.close();
     }

//D2 Yosys                                                                                                              // Generate yosys commands

    String generateYosys()                                                                                              // Tcl to drive yosys
     {final StringBuilder s = new StringBuilder();                                                                      // Generated code
      s.append(substitute("""
read_verilog -sv -D SYNTHESIS {n}.v                                                                                     # Yosys code to confirm the verilog can be synthesised
hierarchy -top {n}
proc
check
""", "n", name));

      return writeFile(verilogTestFolder.ys$(), s);
     }
   } // GenerateVerilog

//D2 Dump Verilog                                                                                                       // Dump the state of the Verilog implementation of the bit machine into the trace file for comparison with the equivalent state of the java implementation of the bit machine

  String dumpVerilog ()                                                                                                 // Dump verilog memory and variables
   {final StringBuilder s = new StringBuilder();
    s.append(dumpVerilogMemories());
    s.append(dumpVerilogVariablesName()+"(); ");
    s.append(dumpVerilogRegistersName()+"(); ");
    return ""+s;
   }

  String removeTracing(String V)                                                                                        // Remove tracing if necessary
   {return suppressInstructionTracing ? V.replaceAll("(?s)\\$fdisplay.*?;", "")
                                         .replaceAll("(?s)\\$fflush.*?;"  , "") : V;
   }

  String dumpVerilogMemories ()                                                                                         // Dump verilog memories
   {final StringBuilder s = new StringBuilder();
    for(Memory m : memories()) s.append(m.dumpVerilog()+" ");
    return ""+s;
   }

  String dumpVerilogVariablesName () {return "dumpVerilogVariables";}                                                   // Name of the verilog method to dump all the variables to the trace file
  String dumpVerilogVariables ()                                                                                        // Dump the value of the integer and boolean variables to the verilog trace file
   {final FileNames includeFile = verilogTestIncludesFolder.same("variables");                                          // Put the dump code into a file that can be switched in and out by the preprocessor.  ifdef preprocessor statements fail if there are too many intervening statements before the closing endif
    final StringBuilder       s = new StringBuilder();
    s.append(substitute("""

  task automatic {name} ();                                                                                             // Dump variables
    begin
`ifndef SYNTHESIS
   `include "{includeFile}"
    $fflush(traceFile);
`endif
    end
  endtask
""", "name", dumpVerilogVariablesName(), "includeFile", includeFile.minus(verilogTestFolder).v$()));

    final StringBuilder v = new StringBuilder();                                                                        // Dump each variable
    for(Int i : ints)                                                                                                   // Dump integers
     {if (i.name != null) v.append(substitute("""
      $fdisplay(traceFile, "Int  %8d ==    %8d {name}", {id}, {v});
""", "name", i.name, "id", ""+i.id, "v", intMemory().vMemory(i.id)));
      else v.append(substitute("""
      $fdisplay(traceFile, "Int  %8d ==    %8d",        {id}, {v});
""",                 "id", ""+i.id, "v", intMemory().vMemory(i.id)));
     }

    for(Bit b : bits)                                                                                                   // Dump booleans
     {if (b.nd) continue;                                                                                               // Omit bools that were created as a result of testing the validity of an Int because the Verilog code does not retain this information
      if (b.name != null) v.append(substitute("""
      $fdisplay(traceFile, "Bit  %8d ==    %8d {name}", {id}, {v});
""", "name", b.name, "id", ""+b.id, "v", bitMemory().vMemory(b.id)));
      else v.append(substitute("""
      $fdisplay(traceFile, "Bit  %8d ==    %8d",        {id}, {v});
""",                 "id", ""+b.id, "v", bitMemory().vMemory(b.id)));
     }

    writeFile(includeFile.v$(), v);
    return ""+s;
   }

  String dumpVerilogRegistersName () {return "dumpVerilogRegisters";}                                                   // Name of the verilog method to dump all the registers to the trace file
  String dumpVerilogRegisters ()                                                                                        // Dump all verilog registers except those of the memory modules because there are no corresponding entries in the java version - to match the verilog we would have to emulate a continuous assign in Java or provide a read enable flag to synchronize the execution of the java and verilog versions. As the memory results show up very quickly in the other control registers it should be possible to proceed without dumping these extra variables
   {final StringBuilder s = new StringBuilder();
    s.append(substitute("""

  task automatic {name} ();
    begin
`ifndef SYNTHESIS
    $fwrite(traceFile, \"     currentPc = %8d\\n\", pc          );
    $fflush(traceFile);
`endif
    end
  endtask
""", "name", dumpVerilogRegistersName()));
    return ""+s;
   }

//D2 Verilog Arrays                                                                                                     // Define arrays in verilog to match this used in Java

  class VerilogArrays                                                                                                   // Define arrays in verilog to match this used in Java
   {final TreeMap<String, Array> arrays = new TreeMap<>();                                                              // Arrays defined by name - same name assumes same content

    Collection<Array> arrays() {return arrays.values();}                                                                // The arrays being defined

    class Array                                                                                                         // Matching set of instructions
     {final String       name;                                                                                          // Name of the array
      final int          size;                                                                                          // Size of the array
      final int []      array;                                                                                          // Array to map inputs to outputs
      final boolean pcIndexed;                                                                                          // Indexed by the program counter if true else by a generated register associated with the array

      Array (String Name, int[]Array)                                                                                   // Create a new array possibly indexed by the program counter else a generated register
       {name = Name; pcIndexed = false; array = Array; size = Array.length;
        arrays.put(name, this);
       }

      Array (String Name, TreeMap<Integer,Integer> map)                                                                 // Define a verilog array from a java tree map
       {size = codeSize();
        name  = Name;
        array = new int[size];
        Arrays.fill(array, -1);
        for (Integer i : map.keySet()) array[i] = map.get(i);
        arrays.put(name, this);
        pcIndexed = true;
       }

      String indexRegisterName () {return pcIndexed ? "pc" : "sourceInt";}                                              // Name of the register used to index the array
      String  dataRegisterName () {return "arrayData_" + name;}                                                         // Name of the register to contain the result from the indexed location in the array
      String          loadName () {return "load_"       +name;}                                                         // Free data associated with instruction matching as it can get quite big
      String         arrayName () {return "array_"      +name;}                                                         // Free data associated with instruction matching as it can get quite big
      String      indexVarName () {return "index_array_"+name;}                                                         // Index name for clearing this array

      String define ()                                                                                                  // Define the array
       {return   substitute("""
  integer {name}[{size}-1:0];
  integer {index};
""", "name", arrayName(), "index", indexVarName(), "size", ""+size);
       }

      String connectModule ()                                                                                           // Connect the main module to the array module
       {if (!pcIndexed) return substitute("""
  integer   {dr};                                                                                                       // Array data register
  {name} {name} (.address({ir}), .data({dr}));                                                                          // Connect to array
""", "dr", dataRegisterName(), "ir", indexRegisterName(), "name", arrayName());

        else return substitute("""
  integer   {dr};                                                                                                       // Define array data register
  {name} {name} (.address(pc), .data({dr}));                                                                            // Connect to module providing array
""", "dr", dataRegisterName(),  "name", arrayName());
       }

      void writeInHex ()                                                                                                // Write the array to a file in hexadecimal
       {final StringBuilder s = new StringBuilder();
        for(int i = 0; i < array.length; ++i) s.append(f("%8x\n", array[i]));
        writeFile(verilogTestIncludesFolder.same(name).v$(), s);
       }

      String module()                                                                                                   // Create a Verilog module to represent a memory
       {writeInHex();                                                                                                   // Write hex representation of array
        final StringBuilder s = new StringBuilder();
        s.append(substitute("""
(* blackbox *) module {array}                                                                                           // Memory module definitions for asynchronus read only memory
 (input  integer address,
  output integer data);
`ifdef __ICARUS__
  integer memory [0:{size}];
  initial $readmemh("{file}", memory, 0, {size});
  assign data = memory[address];
`endif
endmodule
""",
"name",  name,        "file", verilogTestIncludesFolder.same(name).minus(verilogTestFolder).v$(),
"array", arrayName(), "size", ""+(array.length-1)));

        final FileNames f = blackBoxFolder.same(name);
        writeFile(f.v$(), ""+s);
        blackBoxes.push(f);

        return "\n`ifndef SYNTHESIS\n"+s+"`endif\n";
       }
     } // Array
   } // VerilogArrays

//D1 Testing                                                                                                            // Methods useful during testing of byte machine programs

  static void deleteAllFileInVerilogTestsFolder() {deleteAllFiles(verilogTestsFolder.folder, 1999);}                    // Delete generated Verilog files created by a prior run of the current test

  void check (StringBuilder G, String E)                                                                                // Test the supplied content against the specified string, then clear the output area ready for the next report
   {new I() {void a() {Test.ok(nws(G), nws(E));} int traces() {return 0;}};
   }

  void Check (StringBuilder G, String E)                                                                                // Test the supplied content against the specified string, print the actual output area contents and stop
   {new I() {void a() {if (!Test.ok(nws(G), nws(E))) stop(G, traceBack);} int traces() {return 0;}};
   }

  static void test_ifThen(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Bit b = new Bit("b", true);
        final Int a = new Int("a", 1);
        new If (b)
         {void Then() {a.set(2);}
          void Else() {a.set(3);}
         };
        a.ok(2);
        scDieAreaX = 500; scDieAreaY = 400;
        execute();
       }
     };
   }

  static void test_ifThen()
   {          test_ifThen(true);
              test_ifThen(false);
   }

  static void test_ifElse(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Bit b = new Bit("b", true);
        final Int a = new Int("a", 1);
        new If (b.Flip())
         {void Then() {a.set(2);}
          void Else() {a.set(3);}
         };
        a.ok(3);
        scDieAreaX = 500; scDieAreaY = 400;
        execute();
       }
     };
   }

  static void test_ifElse()
   {          test_ifElse(true);
              test_ifElse(false);
   }

  static void test_ForCount(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Int N = new Int("N", 2);
        new ForCount(N)
         {void body(Int Index)
           {final Int m = new Int("m");
            dumpProgramState("AAAA");
           }
         };
        scDieAreaX = 500; scDieAreaY = 400;
        execute();
       }
     };
   }

  static void test_ForCount()
   {          test_ForCount(true);
              test_ForCount(false);
   }

  static void test_For(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Int N = new Int("N", 2);
        new For(N)
         {void body(Int Index, Bit Continue)
           {//final Int m = new Int("m");
            dumpProgramState("AAAA");
            Continue.set();
            dumpProgramState("BBBB");
           }
         };
        scDieAreaX = 500; scDieAreaY = 400;
        execute();
       }
     };
   }

  static void test_For()
   {          test_For(true);
              test_For(false);
   }

  static void test_programming(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Int i = new Int("i").set(0);
        final Int N = new Int("N", 11);
        new For(N)
         {void body(Int Index, Bit Continue)
           {final Int m = new Int("m");
            final Bit z = new Bit("z");
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
        scDieAreaX = 500; scDieAreaY = 400;
        execute();
       }
     };
   }

  static void test_addition(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Int a = new Int("a", 1);
        //final Int b = new Int("b", a.Add(2));
        a.ok(1);
        //b.ok(3);
        dumpProgramState("AAAA");
        scDieAreaX = 300; scDieAreaY = 400;
        execute();
       }
     };
   }

  static void test_addition()
   {          test_addition(true);
              test_addition(false);
   }

  static void test_programming()
   {          test_programming(true);
              test_programming(false);
   }

  static void test_clearSet(boolean Ex)
   {sayCurrentTestName();
    final Program  P = new Program(new Build().immediate(Ex))
     {void code()
       {final Bit a = new Bit("a", true);
        a.ok(true);
        a.clear();       a.ok(false);
        a.clear();       a.ok(false);
        a.set  ();       a.ok(true);
        a.set  ();       a.ok(true);
        a.set  (false);  a.ok(false);
        a.set  (true);   a.ok(true);
        execute();
       }
     };
   }

  static void test_clearSet()
   {          test_clearSet(true);
              test_clearSet(false);
   }

  static void test_andOr(boolean Ex)
   {sayCurrentTestName();
    final Program  P = new Program(new Build().immediate(Ex))
     {void code()
       {final Bit Z = new Bit("zero", false);
        final Bit O = new Bit("one",   true);

        final Bit z = new Bit("z").set(Z);
        final Bit o = new Bit("o").set(O);

        z.Or (z).ok(false);
        z.Or (o).ok(true);
        o.Or (z).ok(true);
        o.Or (o).ok(true);

        z.And(z).ok(false);
        z.And(o).ok(false);
        o.And(z).ok(false);
        o.And(o).ok(true);
        final Bit a = new Bit("a", true);
        final Bit b = new Bit("b", false);
        final Bit c = new Bit("c").set(a.dup().or(b).flip().ok(false));
        final Bit d = new Bit("d").set(b.dup().or(a).flip().ok(false));

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
       {final Int a = new Int("a").set(1);
        final Int b = new Int(0);
        final Int N = new Int(10);
        final StringBuilder s = new StringBuilder();
        new For(N)
         {void body(Int Index, Bit Continue)
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
       {final Int a = new Int("a").set(0);
        final Int b = new Int("b", 1);
        final Int c = new Int("c");
        final Int N = new Int("N", 10);
        final StringBuilder s = new StringBuilder();
        new For(N)
         {void body(Int Index, Bit Continue)
           {c.set(a);
            c.add(b);
            a.set(b);
            b.set(c);
            new I() {void a() {s.append(""+c+" ");} int traces() {return 0;}};
            Continue.set();
           }
         };
        Check(s, "c=1 c=2 c=3 c=5 c=8 c=13 c=21 c=34 c=55 c=89");
        scDieAreaX = 500; scDieAreaY = 500;
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
       {final Int a = new Int("a");
        final Bit b = new Bit("b");
        final Int c = new Int("c").set(0);
        final Int N = new Int("N").set(4);
        final StringBuilder s = new StringBuilder();
        new For(N)
         {void body(Int Index, Bit Continue)
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
//      scDieAreaX = 500; scDieAreaY = 500;
        scDieAreaX = 400; scDieAreaY = 500;
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
       {final Int a = new Int("a", 0);
        final StringBuilder s = new StringBuilder();
              a.ok(0);
        a.inc().ok(1);
        a.inc().ok(2);
        final Int A = new Int("A").set(a);
        scDieAreaX = 300; scDieAreaY = 300;
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
       {final Int a = new Int("a").set(1);
        a.add(2).ok(3);
       }
     };
    final Program Q = new Program(new Build().immediate(Ex).parent(P))
     {void code()
       {final Int a = new Int("A").set(1);
        a.ok(1);
        a.add(3).ok(4);
       }
     };
    ok(P.ints.size(), 2);
    ok(Q.ints.size(), 0);
    P.scDieAreaX = 300; P.scDieAreaY = 400;
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
        final Int i = new Int("i", 1);
        final Bit b = new Bit("b", true);
        final Bit o = new Bit("o");
        dumpProgramState("BBBB");
        final Int I = new Int("I", i);
        final Bit B = new Bit("B", b);
        dumpProgramState("CCCC");
        I.add(2).ok(3);
        B.flip();
        dumpProgramState("DDDD");
        scDieAreaX = 300; scDieAreaY = 400;
        execute();
       }
     };
   }

  static void test_variables()
   {test_variables(true);
    test_variables(false);
   }

  static void test_mem(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex).memory(2))
     {void code()
       {final Memory m = unitMemory;
        final Int a = new Int("a"); a.set(2) ;                   m.putInt(new Int(1), a);
        final Int b = m.getInt(new Int(1));                      b.name = "b"; b.ok(2);
        dumpProgramState("AAAA");
        final Bit c = m.getBit(new Int(1), new Int(1));          c.name = "c"; c.ok(true);
        final Bit d = m.getBit(new Int(1), new Int(0));          d.name = "d"; d.ok(false);
        //m.putBit(new Int(1), new Int(0),   new Bit(true));
        //m.putBit(new Int(1), new Int(1),   new Bit(false));
        //m.putBit(new Int(0), new Int(13),  new Bit(true));
        //final Int e = new Int("e"); e.set(m.getInt(new Int(1)));  e.name = "e"; e.ok(1);
        scDieAreaX = 500; scDieAreaY = 500;
        execute();
       }
     };
   }

  static void test_mem()
   {test_mem(true);
    test_mem(false);
   }

  static void test_namedMemory(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Memory m = new Memory(2, "test memory name");
        final Int a = new Int("a"); a.set(2); m.putInt(new Int(1), a);
        dumpProgramState("aaaa");
        scDieAreaX = 500; scDieAreaY = 500;
        execute();
       }
     };
   }

  static void test_namedMemory()
   {test_namedMemory(true);
    test_namedMemory(false);
   }

  static void test_memory(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex).memory(2))
     {void code()
       {final Memory m = unitMemory;
        final Int Z = new Int("Z", 0);
        final Int O = new Int("O", 1);
        final Int T = new Int("T", 2);
        new ForCount(2)
         {void body(Int Index)
           {m.putInt(Z, O);
            m.putInt(O, T);
            m.getInt(Z).ok(1);
            m.getInt(O).ok(2);
            m.getInt(Z).ok(1);
            dumpProgramState("AAAA");
            m.getBit(O, Z).ok(false);
            m.getBit(O, O).ok(true );
            m.getBit(O, T).ok(false);
            m.putBit(O, Z, new Bit(true));
            m.getInt (O)         .ok(3);
            dumpProgramState("BBBB");
            m.putBit(new Int(32), new Bit(false));
            m.getBit(new Int(32)).ok(false);
            m.getBit(new Int(33)).ok(true );
            m.getBit(new Int(34)).ok(false);
            dumpProgramState("CCCC");
            m.putBit(O, new Int(9), new Bit(true));
            m.getBit(O, new Int(9)).ok(true);
           }
         };
        final Int r = new Int(m.getInt(O));
        r.ok(514);
        scDieAreaX = 1000; scDieAreaY = 1000;
        execute();
       }
     };
   }

  static void test_memory()
   {test_memory(true);
    test_memory(false);
   }

  static void test_memoryNegative(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex).memory(8))
     {void code()
       {final Memory m = unitMemory;
        new ForCount(2)
         {void body(Int Index)
           {m.putInt(new Int(0), new Int(-2));
            m.putInt(new Int(4), new Int(-3));
            m.getInt(new Int(0)).ok(-2);
            m.getInt(new Int(4)).ok(-3);
           }
         };
        execute();
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
       {final Memory     M = unitMemory;
        final Memory.Ref m = M.new Ref(2);
        final Memory.Ref n = M.new Ref(3);
        new ForCount(2)
         {void body(Int Index)
           {m.putInt(new Int(0), new Int(1));

            m.putInt(new Int(1), new Int(-1));
            m.putInt(new Int(1), new Int(2));

            new If (Index.eq(0))
             {void Then()
               {//stop(nws(M.dumpAsDecimal()));
                ok(()->nws(M.dumpAsDecimal()), """
Memory 0
            0    1    2    3    4    5    6    7    8    9
00000000              1    2
""");
               }
              void Else()
               {//stop(nws(M.dumpAsDecimal()));
                ok(()->nws(M.dumpAsDecimal()), """
Memory 0
            0    1    2    3    4    5    6    7    8    9
00000000              1    2
""");
               }
             };
            m.getInt(new Int(0)).ok(1);
            m.getInt(new Int(1)).ok(2);

            m.getBit(new Int(32)).ok(false);
            m.getBit(new Int(33)).ok(true);
            m.putBit(new Int(32), new Bit(true));
            m.putBit(new Int(34), new Bit(true));
            dumpProgramState("AAAA1111");
            m.getInt (new Int( 1)).ok(7);
            dumpProgramState("AAAA2222");
            //stop(nws(M.dumpAsDecimal()));
            ok(()->nws(M.dumpAsDecimal()), """
Memory 0
            0    1    2    3    4    5    6    7    8    9
00000000              1    7
""");

            dumpProgramState("AAAA3333");
            m.putBit(new Int(32), new Bit(false));
            m.getBit(new Int(32)).ok(false);
            m.getBit(new Int(33)).ok(true );
            m.getBit(new Int(34)).ok(true);
            m.getInt (new Int( 1)).ok(6);
            //stop(nws(M.dumpAsDecimal()));
            ok(()->nws(M.dumpAsDecimal()), """
Memory 0
            0    1    2    3    4    5    6    7    8    9
00000000              1    6
""");
            m.clear(1);
            //stop(nws(M.dumpAsDecimal()));
            ok(()->nws(M.dumpAsDecimal()), """
Memory 0
            0    1    2    3    4    5    6    7    8    9
00000000                   6
""");
            m.copy(n, 1);
            //stop(nws(M.dumpAsDecimal()));
            ok(()->nws(M.dumpAsDecimal()), """
Memory 0
            0    1    2    3    4    5    6    7    8    9
00000000              6    6
""");
            M.clear();
            //stop(nws(M.dumpAsDecimal()));
            ok(()->nws(M.dumpAsDecimal()), """
Memory 0
            0    1    2    3    4    5    6    7    8    9
00000000
""");
           }
         };
        maxSteps(9_999);
        execute();
       }
     };
   }

  static void test_memoryRef()
   {          test_memoryRef(true);
              test_memoryRef(false);
   }

  static void test_verilogArray(boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex).memory(16))
     {void code()
       {final int[]array = {1, 3, 5, 2, 4, 6};                                                                          // Array
        final VerilogArrays.Array A = verilogArrays().new Array("array", array);                                        // Verilog versoin of array
        dumpProgramState("AAAA");
        final Int i = new Int("i").set(2);                                                                              // Input
        final Int o = new Int("o");                                                                                     // Output
        i.S();                                                                                                          // Load the index of the array
        new I()
         {void        a() {targetInt(array[i.i()]); o.setValid();}                                                      // Load from array
          String      v() {return "targetInt <= "+A.dataRegisterName()+";";}                                            // Load from array data register
          boolean trace() {return false;}
         };
        o.W();                                                                                                          // Write array element into output
        o.ok(5);
        scDieAreaX = 300; scDieAreaY = 600;
        execute();
       }
     };
   }

  static void test_verilogArray()
   {          test_verilogArray(true);
              test_verilogArray(false);
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
        new ForCount(N)
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
    final int N = 10;
    final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Int  a = new Int("a").set(N);
        new ForCount(N)
         {void body(Int Index)
           {final Bit b = new Bit(false);
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

  static void test_forLoops(Boolean Ex)
   {sayCurrentTestName();
    final Program P = new Program(new Build().immediate(Ex))
     {void code()
       {final Int  a = new Int("a").set(0);
        new For(new Int(1), new Int(10))
         {void body(Int Index, Bit Continue)
           {new If (Index.le(2))
             {void Then() {a.add(01);}
              void Else() {a.add(11);}
             };
            Continue.set(Index.lt(3));
           }
         };
        a.ok(13);
        dumpProgramState("AAAA");
        a.set(0);
        new ForCount(1, 4)
         {void body(Int Index)
           {new If (Index.le(2))
             {void Then() {a.add(02);}
              void Else() {a.add(22);}
             };
           }
         };
        a.ok(26);
        dumpProgramState("BBBB");
        execute();
       }
     };
   }

  static void test_forLoops()
   {          test_forLoops(true);
              test_forLoops(false);
   }

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_ifThen();
    test_ifElse();
    test_ForCount();
    test_For();
    test_addition();
    test_programming();
    test_clearSet();
    test_andOr();
    test_add();
    test_fibonacci();
    test_mod();
    test_incremental();
    test_remote();
    test_variables();
    test_mem();
    test_namedMemory();
    test_memory();
    test_memoryNegative();
    test_memoryRef();
    test_verilogArray();
    test_lastInstructionBase();
    test_boolean();
    test_ifInc();
    test_forLoops();
   }

  static void newTests()                                                                                                // Tests being worked on
   {//oldTests();
    test_mem(false);
   }

  public static void main(String[] args)                                                                                // Test if called as a program
   {try                                                                                                                 // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {deleteAllFileInVerilogTestsFolder();                                                                              // Delete generated Verilog files created by a prior run of the current test
      if (github_actions) oldTests(); else newTests();                                                                  // Tests to run
      if (coverageAnalysis) coverageAnalysis(12);                                                                       // Code coverage
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
//https://github.com/philiprbrenan/btreeList/compare/oldSha...newSha
// ^.{10,119}//
