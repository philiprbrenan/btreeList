//----------------------------------------------------------------------------------------------------------------------
// Machine level programming in Java
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2025
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.util.*;
import java.util.function.Supplier;

//D1 Construct                                                                                                          // Develop and test a java program to describe a chip and emulate its operation.

public class Programming extends Test                                                                                   // Develop and test a java program to describe a chip and emulate its operation.
 {final static boolean github_actions   =                                                                               // Whether we are on a github
    "true".equals(System.getenv("GITHUB_ACTIONS"));
  final static long start               = System.nanoTime();                                                            // Start time
  final static boolean coverageAnalysis = false;                                                                        // Enables coverage checks
  final Stack<I>       code = new Stack<>();                                                                            // Machine code instructions
  final Stack<Label> labels = new Stack<>();                                                                            // Labels for instructions in this process
  final Stack<String>   put = new Stack<>();                                                                            // Output from execution
  final Bool   True = new Bool(true);                                                                                   // Useful constants
  final Bool  False = new Bool(false);
  final boolean ex;                                                                                                     // Execute immediately if true else generate machine code and execute later

  final int maxSteps = 999;                                                                                             // Number of steps permitted in code execution
  int      nextIntId = 0;                                                                                               // Unique id for each Int
  int     nextBoolId = 0;                                                                                               // Unique id for each Bool
  int             pc;                                                                                                   // Program counter - set to something less than zero to stop with a return code

  Programming()           {this(true);}                                                                                 // Create a program which executes as it is written
  Programming(boolean Ex) {ex = Ex;}                                                                                    // Create a program as a list of instructins which are executed later

//D1 Programming                                                                                                        // Program structures

  abstract class For                                                                                                    // For loop
   {For(int Start, int End)                                                                                             // Execute the loop the specified number of times
     {final Int  index = new Int();
      final Bool  cont = new Bool();

      if (ex)                                                                                                           // Immediate execution
       {for(int i : range(Start, End))                                                                                  // Iterate over the specified range
         {index.set(i);                                                                                                 // Set the index to each element of the specified range
          cont.clear();                                                                                                 // Terminate unless told otherwise
          body(index, cont);                                                                                            // Execute the loop
          if (cont.Flip().b()) break;                                                                                   // Terminate the loop unless continuation requested
         }
       }
      else                                                                                                              // Machine code
       {index.set(Start);                                                                                               // Start index
        final Label start = new Label();                                                                                // Start of for loop code
        final Label   end = new Label();                                                                                // End of for loop code
        cont.clear();                                                                                                   // Terminate unless told otherwise
        body(index, cont);                                                                                              // Execute the loop
        index.inc();                                                                                                    // Increment lop counter
        new I(true)
         {void action()
           {pc = cont.b() && index.i() < End ? start.offset : end.offset;                                               // Continue while requested and maximum number of iterations has not been  surpassed
           }
         };
        end.set();                                                                                                      // End of the loop
       }
     }

    For(int End) {this(0, End);}                                                                                        // Execute the loop the specified number of times as long as it returns true
    For(Int End) {this(0, End.i());}                                                                                    // Execute the loop the specified number of times as long as it returns true

    abstract void body(Int Index, Bool Continue);                                                                       // Body of the for loop - execute while in range and continuation requested
   }

  abstract class If                                                                                                     // If statement
   {If (boolean Condition)                                                                                              // A constant that selects code at compile time
     {if (Condition) Then(); else Else();
     }
    If (Bool    Condition)
     {if (ex)                                                                                                           // Immediate execution
       {if (Condition.b()) Then(); else Else();
       }
     else                                                                                                               // Machine code
       {final Label lse = new Label();                                                                                  // Start of else
        final Label end = new Label();                                                                                  // End of if
        new I(true)                                                                                                     // Jump to else if condition is false
         {void action()
           {if (!Condition.b()) pc = lse.offset;
           }
         };
        Then();                                                                                                         // Then body
        new I(true)                                                                                                     // Jump over else to end
         {void action()
           {pc = end.offset;
           }
         };
        lse.set();                                                                                                      // Start of else
        Else();                                                                                                         // Else body
        end.set();                                                                                                      // End of the loop
       }
     }

    abstract void Then();                                                                                               // Then clause
             void Else() {}                                                                                             // Else clause
   }

  <T> T If(Bool Choice, Supplier<T> Then, Supplier<T> Else)                                                             // Choose between two alternatives
   {final Ref<T>r = new Ref<>();
    new If (Choice)
     {void Then() {r.set(Then.get());}
      void Else() {r.set(Else.get());}
     };
    return r.get();
   }

  class Bool                                                                                                            // An integer that can be passed as a parameter to a method and modified there-in
   {boolean    i = false;                                                                                               // Value of the integer
    boolean    v = false;                                                                                               // Whether the current value of the integer is valid or not
    String     n = null;                                                                                                // An optional name for this variable
    final int id = nextBoolId++;                                                                                        // Unique id for Bool

    enum Ops {eq, flip, ne, set};                                                                                       // Boolean operation classification by argument types

    Bool      valid() {return new Bool(v);}

    Bool           ()          {}
    Bool           (boolean I) {       i = I;   v = true;}
    Bool           (Bool    I) {I.x(); i = I.i; v = true;}

    boolean       b()          {x(); return i;}
    void          x()          {if (!v) stop("Bool has not been set yet");}
    Bool          X()          {v = true; return this;}

    Bool        set()          {return ie(Ops.set,  true); }
    Bool        set(boolean I) {return ie(Ops.set,  I);    }
    Bool        set(Bool    I) {return ie(Ops.set,  I);    }
    Bool        set(Int     I) {return ie(Ops.set,  I);    }
    Bool      clear()          {return ie(Ops.set,  false);}
    Bool       flip()          {return ie(Ops.flip);       }

    Bool        Set()          {return dup().set();}
    Bool        Set(boolean I) {return dup().set(I);}
    Bool        Set(Bool    I) {return dup().set(I);}
    Bool      Clear()          {return dup().clear();}
    Bool       Flip()          {return dup().flip();}

    Bool         eq(boolean I) {return ie(Ops.eq,  I);}
    Bool         ne(boolean I) {return ie(Ops.ne,  I);}

    Bool         eq(Bool    I) {return ie(Ops.eq,  I);}
    Bool         ne(Bool    I) {return ie(Ops.ne,  I);}

    Bool ie(Ops Op)            {if (ex) ex(Op   ); else new I() {void action() {ex(Op   );}}; return this;}             // Execute immediately or create an instruction for machine code to execute later
    Bool ie(Ops Op, boolean I) {if (ex) ex(Op, I); else new I() {void action() {ex(Op, I);}}; return this;}
    Bool ie(Ops Op, Bool    I) {if (ex) ex(Op, I); else new I() {void action() {ex(Op, I);}}; return this;}
    Bool ie(Ops Op, Int     I) {if (ex) ex(Op, I); else new I() {void action() {ex(Op, I);}}; return this;}

    Bool ex(Ops Op)                                                                                                     // Execute a zeradic boolean operation
     {switch(Op)
       {case flip -> {x(); i = !i;                }
        default   -> stop("Op not implemented:", Op);
       }
      return this;
     }

    Bool ex(Ops Op, boolean I)                                                                                          // Execute a monadic boolean operation on a constant
     {switch (Op)
       {case set -> {i  = I; v = true; }
        case eq  -> {x(); i = i == I; }
        case ne  -> {x(); i = i != I; }
        default  -> stop("Op not implemented:", Op);
       }
      return this;
     }

    Bool ex(Ops Op, Bool I)                                                                                             // Execute a monadic boolean operation on a variable
     {switch(Op)
       {case set -> {I.x(); i = I.i; v = true; }
        case eq  -> {x(); I.x(); i = i == I.i; }
        case ne  -> {x(); I.x(); i = i != I.i; }
        default  -> stop("Op not implemented:", Op);
       }
      return this;
     }

    Bool ex(Ops Op, Int I)                                                                                               // Execute a monadic boolean operation on an integer variable
     {switch(Op)
       {case set -> {I.x(); i = I.i > 0; v = true;}
        default  -> stop("Op not implemented:", Op);
       }
      return this;
     }

    @SafeVarargs
    final Bool or(Supplier<Bool>...b)                                                                                   // "Or" with short circuit
     {x();                                                                                                              // Start with the current value
      for (int i : range(b.length))                                                                                     // Test each additional value as necessary
       {if (b()) break;                                                                                                 // Finish when we know the result
        set(b[i].get());                                                                                                // Check additional operands
       }
      return this;
     }


    @SafeVarargs
    final Bool And(Supplier<Bool>...b)                                                                                  // "And" with short circuit
     {x(); final Bool r = new Bool(b());                                                                                // Start with the current value
      for (int i : range(b.length))                                                                                     // Test each additional value as necessary
       {if (!r.b()) break;                                                                                              // Finish when we know the result
        r.set(b[i].get());                                                                                              // Check additional operands
       }
      return r;
     }

    @SafeVarargs
    final Bool and(Supplier<Bool>...b)                                                                                  // "And" with short circuit modify target
     {x();
      for (int i : range(b.length))                                                                                     // Test each additional value as necessary
       {if (!b()) break;                                                                                                // Finish when we know the result
        set(b[i].get());                                                                                                // Check additional operands
       }
      return this;
     }

    Bool dup() {x(); final Bool I = new Bool(i); I.n = n; return I;}                                                    // Duplicate a valid boolean

    public String toString() {return (n == null ? "" : n+"=")+i;}                                                       // Print the boolean
   }

  class Int                                                                                                             // An integer that can be passed as a parameter to a method and modified there-in
   {private int        i = 0;                                                                                           // Value of the integer
    private boolean    v = false;                                                                                       // Whether the current value of the integer is valid or not
    private String     n = null;                                                                                        // An optional name for this variable
    private final int id = nextIntId++;                                                                                 // Unique id for Int

    Bool    valid()  {return new Bool( v);}                                                                             // A valid integer
    Bool notValid()  {return new Bool(!v);}                                                                             // A not valid integer
    int         i()  {x(); return i;}                                                                                   // Current value
    void x       ()  {if (!v) stop("Int has not been set yet");}                                                        // Confirm that the integer has a value

    Int (int I)      {i = I;   v = true;}
    Int (Int I)      {if (I != null) {i = I.i; v = I.v;}}
    Int      ()      {}

    Int  max (int I) {x(); return i < I ? new Int(I) : this;}
    Int  min (int I) {x(); return i > I ? new Int(I) : this;}

    enum Ops {X, abs, add, add2, dec, div, down, inc, max, min, mod, mul, neg, set, sqrt, sub, up};                     // Possible integer operations

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

    Int ie(Ops Op)        {if (ex) ex(Op   ); else new I() {void action() {ex(Op   );}}; return this;}                  // Execute immediately or create an instruction for machine code to execute later
    Int ie(Ops Op, int I) {if (ex) ex(Op, I); else new I() {void action() {ex(Op, I);}}; return this;}
    Int ie(Ops Op, Int I) {if (ex) ex(Op, I); else new I() {void action() {ex(Op, I);}}; return this;}

    Int ex(Ops Op)                                                                                                      // Execute a zeradic integer operation
     {switch(Op)
       {case inc -> {x(); i++;                   }
        case dec -> {x(); i--;                   }
        case up  -> {x(); i  <<= 1;              }
        case down-> {x(); i >>>= 1;              }
        case sqrt-> {x(); i = (int)Math.sqrt(i); }
        case neg -> {x(); i = -i;                }
        case abs -> {x(); i = i < 0 ? -i : i;    }
        default  -> stop("Op not implemented:", Op);
       }
      return this;
     }

    Int ex(Ops Op, int I)                                                                                               // Execute a monadic integer operation on a constant
     {switch (Op)
       {case set -> {      i  = I;     v = true; }
        case add -> { x(); i += I;     v = true; }
        case sub -> { x(); i -= I;     v = true; }
        case mul -> { x(); i *= I;     v = true; }
        case div -> { x(); i /= I;     v = true; }
        case mod -> { x(); i %= I;     v = true; }
        case add2-> { x(); i += I + I; v = true; }
        default  -> stop("Op not implemented:", Op);
       }
      return this;
     }

    Int ex(Ops Op, Int I)                                                                                               // Execute a monadic integer operation on a variable
     {switch(Op)
       {case set -> {i = I.i;              v = I.v; }
        default  -> {I.x(); ex(Op, I.i()); v = true;}
       }
      return this;
     }

    Int  Add (int I) {return dup().add(I);}                                                                             // Duplicate the target so that a copy is modified rather than the original integer
    Int  Add (Int I) {return dup().add(I);}
    Int  Add2(Int I) {return dup().add2(I);}
    Int  Sub (int I) {return dup().sub(I);}
    Int  Sub (Int I) {return dup().sub(I);}
    Int  Mul (int I) {return dup().mul(I);}
    Int  Mul (Int I) {return dup().mul(I);}
    Int  Div (int I) {return dup().div(I);}
    Int  Div (Int I) {return dup().div(I);}
    Int  Mod (int I) {return dup().mod(I);}
    Int  Mod (Int I) {return dup().mod(I);}
    Int  Inc ()      {return dup().add(1);}
    Int  Dec ()      {return dup().sub(1);}
    Int  Up  ()      {return dup().up();}
    Int  Down()      {return dup().down();}
    Int  Sqrt()      {return dup().sqrt();}
    Int  Neg()       {return dup().neg();}
    Int  Abs()       {return dup().abs();}

    Bool eq(int e){  x(); return new Bool(i == e);}                                                                     // Comparisons with a constant integer
    Bool ne(int e){  x(); return new Bool(i != e);}
    Bool le(int e){  x(); return new Bool(i <= e);}
    Bool lt(int e){  x(); return new Bool(i <  e);}
    Bool ge(int e){  x(); return new Bool(i >= e);}
    Bool gt(int e){  x(); return new Bool(i >  e);}

    Bool eq(Int e){e.x(); return eq(e.i);}
    Bool ne(Int e){e.x(); return ne(e.i);}                                                                              // Comparisons with a variable integer
    Bool le(Int e){e.x(); return le(e.i);}
    Bool lt(Int e){e.x(); return lt(e.i);}
    Bool ge(Int e){e.x(); return ge(e.i);}
    Bool gt(Int e){e.x(); return gt(e.i);}

    Int dup() {x(); final Int I = new Int(i); I.v = v; I.n = n; return I;}                                              // Duplicate a valid integer

    public String toString()                                                                                            // Print the integer
     {return (n == null ? "" : n+"=")+i;
     }
   }

  class Ref<T>                                                                                                          // A reference to an object
   {T i;                                                                                                                // Value of the object
    Ref()              {i = null;}                                                                                      // Create a null reference
    Ref(T I)           {i = I;}                                                                                         // Create a reference to the object
    void set(T I)      {i = I;}                                                                                         // Set the refernce
    void set(Ref<T> I) {i = I.get();}                                                                                   // Set the refernce
    T    get()         {return i;}                                                                                      // Dereference the reference
    Bool valid()       {return new Bool(i != null);}                                                                    // Check that the refence is valid

    public String toString()                                                                                            // Print the reference
     {return i == null ? "null" : "ref("+i+")";
     }
   }

  static int[]range(Int Limit) {return range(Limit.i());}                                                               // Range of integers
  static boolean ok(Bool b) {return ok(b.b());}                                                                         // Check test results match expected results.

  void put(Object...Values) {new I() {void action() {put.push(""+saySb(Values));}};}                                    // Say some values
  String output()           {return put.size() > 0 ? joinLines(put)+"\n" : "";}                                         // Output from execution

//D1 Machine Code                                                                                                       // Generate machine code instructions to implement the program

//D2 Instruction                                                                                                        // An instruction represents code to be executed by a process in a single clock cycle == process step

  abstract class I                                                                                                      // Instructions implement the action of a program
   {final int instructionNumber;                                                                                        // The number of this instruction
    final boolean     mightJump;                                                                                        // The instruction might cause a jump
    final String      traceBack = traceBack();                                                                          // Line at which this instruction was created

    final String traceBackOnOneLine()                                                                                   // Line at which this instruction was created represented with out new lines
     {return traceBack.replace("\n", "|").trim();
     }

    I(boolean MightJump)                                                                                                // Add this instruction to the code for the process
     {instructionNumber = code.size();                                                                                  // Number each instruction
      mightJump = MightJump;
      code.push(this);                                                                                                  // Save instruction
     }

    I() {this(false);}                                                                                                  // Add this instruction to the process's code assuning it will not jump

    abstract void action();                                                                                             // The action to be performed by the instruction
   }

  class Label                                                                                                           // Label jump targets in the program
   {int offset;                                                                                                         // The instruction location to which this labels applies
    Label()    {set(); labels.push(this);}                                                                              // A label assigned to an instruction location
    void set() {offset = code.size();}                                                                                  // Reassign the label to an instruction
   }

  void execute()                                                                                                        // Execute the current code
   {if (ex) return;                                                                                                     // The code has already been executed
    pc = 0;
    final int N = code.size();                                                                                          // Number of instructions
    for(int c = 0; c < maxSteps && pc >= 0 && pc < N; ++c)                                                              // Execute each instruction within a specified number of steps
     {final I i = code.elementAt(pc);
      try

       {pc++;                                                                                                           // This is the anticipated next instruction, but the instruction can set it to effect a branch in execution flow
        i.action();
       }
      catch(Exception e)
       {stop("Exception:", e, "while executing:", traceBack(e));
       }
      if (code.size() != N) stop("Instruction added during execution");
     }
   }

  void Goto(Label Target) {pc = Target.offset;}                                                                         // Goto a label unconditionally
  void Goto(Label Target, Bool If) {if ( If.b()) pc = Target.offset;}                                                   // Goto a label conditionally
  void Noto(Label Target, Bool If) {if (!If.b()) pc = Target.offset;}                                                   // Goto a label conditionally

//D1 Testing                                                                                                            // Test expected output against got output

  static int testsPassed = 0, testsFailed = 0;                                                                          // Number of tests passed and failed

  static void test_programming()
   {final Programming p = new Programming();
    final Int         i = p.new Int(0);
    class test_programming
     {test_programming(int N)
       {p.new For(N)
         {void body(Int Index, Bool Continue)
           {p.new If (Index.Mod(2).eq(0))
             {void Then() {i.add(Index);}
              void Else() {i.sub(Index);}
             };
            Continue.set();
           }
         };
       }
     }
    new test_programming(11);
    ok(i, 5);
    ok(i.valid().b());
   }

  static void test_bool()
   {final Programming P = new Programming();
    final Bool b1 = P.new Bool().clear();
    final Bool b2 = P.new Bool().set();
    ok(b1.or(  ()->{return b2;}).b() == true);
    b1.clear();
    ok(b1.And (()->{return b2;}).b() == false);
   }

  static void test_traceNames()
   {class A
     {void a()
       {class B

         {void b()
           {if (github_actions)
             {ok(traceNamesString(), "main.oldTests.test_traceNames.a.b");
             }
            else
             {ok(traceNamesString(), "main.newTests.oldTests.test_traceNames.a.b");
             }
           }
         }
        new B().b();
       }
     }
    new A().a();
   }

  static void test_add()
   {final Programming P = new Programming(false);
    final Int a = P.new Int(1);
    final Int b = P.new Int(2);
    final Int c = P.new Int(0);
    c.add(a);
    c.add(b);
    ok(c, 0);
    P.execute();
    ok(c, 3);
   }

  static void test_fibonnacci()
   {final Programming P = new Programming(false);
    final Int a = P.new Int(0);
    final Int b = P.new Int(1);
    final Int c = P.new Int(0);
    final Int N = P.new Int(10);
    P.new For(N)
     {void body(Int Index, Bool Continue)
       {c.set(a);
        c.add(b);
        a.set(b);
        b.set(c);
        P.put(c);
        Continue.set();
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

  static void test_mod()
   {final Programming P = new Programming(false);
    final Int  a = P.new Int ();
    final Bool b = P.new Bool();
    final Int  c = P.new Int (0);
    final Int  N = P.new Int (4);
    P.new For(N)
     {void body(Int Index, Bool Continue)
       {a.set(Index).mod(2);
        P.new If (b.set(a))
         {void Then() {c.dec();}
          void Else() {c.inc(); c.inc();}
         };
        P.put(c);
        Continue.set();
       }
     };
    ok(P.output(), "");
    P.execute();
    //stop(P.output());
    ok(P.output(), """
2
1
3
2
""");
   }

  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_programming();
    test_bool();
    test_traceNames();
    test_add();
    test_fibonnacci();
    test_mod();
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
