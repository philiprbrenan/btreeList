//------------------------------------------------------------------------------
// Test a java program.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2025
//------------------------------------------------------------------------------
// change boolean eq() to Bool eq()
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.util.*;
import java.util.function.Supplier;

//D1 Construct                                                                  // Develop and test a java program to describe a chip and emulate its operation.

public class Programming extends Test                                           // Develop and test a java program to describe a chip and emulate its operation.
 {final static boolean github_actions   =                                       // Whether we are on a github
    "true".equals(System.getenv("GITHUB_ACTIONS"));
  final static long start               = System.nanoTime();                    // Start time
  final static boolean coverageAnalysis = false;                                // Enables coverage checks
  final Stack<I>       code = new Stack<>();                                    // Machine code instructions
  final Stack<Label> labels = new Stack<>();                                    // Labels for instructions in this process
  final Bool   True = new Bool(true);                                           // Useful constants
  final Bool  False = new Bool(false);

  final int maxSteps = 999;                                                     // Number of steps permitted in code execution
  int      nextIntId = 0;                                                       // Unique id for each Int
  int     nextBoolId = 0;                                                       // Unique id for each Bool
  boolean         ex = true;                                                    // Execute immediately if true else generate machine code and execute later
  int             pc;                                                           // Program counter - set to something less than zero to stop with a return code

//D1 Programming                                                                // Program structures

  abstract class For                                                            // For loop
   {For(int Start, int End)                                                     // Execute the loop the specified number of times
     {if (ex)
       {final Int  index = new Int();
        final Bool  cont = new Bool();

        for(int i : range(Start, End))                                          // Iterate over the specified range
         {index.i(i);                                                           // Set the index to each element of the specified range
          cont.clear();                                                         // Terminate unless told otherwise
          body(index, cont);                                                    // Execute the loop
          if (cont.Flip().b()) break;                                           // Terminate the loop unless continuation requested
         }
       }
      else
       {final Label start = new Label();                                        // Start of for loop code
        final Label   end = new Label();                                        // End of for loop code
       }
     }

    For(int End) {this(0, End);}                                                // Execute the loop the specified number of times as long as it returns true
    For(Int End) {this(0, End.i());}                                            // Execute the loop the specified number of times as long as it returns true

    abstract void body(Int Index, Bool Continue);                               // Body of the for loop - execute while in range and continuation requested
   }

  abstract class If                                                             // If statement
   {If (boolean condition)
     {if (condition) Then(); else Else();
     }
    If (Bool    condition)
     {if (condition.b()) Then(); else Else();
     }

    abstract void Then();                                                       // Then clause
             void Else() {}                                                     // Else clause
   }

  <T> T If(Bool Choice, Supplier<T> Then, Supplier<T> Else)                     // Choose between two alternatives
   {final Ref<T>r = new Ref<>();
    new If (Choice)
     {void Then() {r.set(Then.get());}
      void Else() {r.set(Else.get());}
     };
    return r.get();
   }

  class Bool                                                                    // An integer that can be passed as a parameter to a method and modified there-in
   {boolean         i = false;                                                  // Value of the integer
    boolean         v = false;                                                  // Whether the current value of the integer is valid or not
    String          n = null;                                                   // An optional name for this variable
    final int      id = nextBoolId++;                                           // Unique id for Bool

    Bool      valid() {return new Bool(v);}

    Bool           ()          {}
    Bool           (boolean I) {i = I; v = true;}
    Bool           (Bool    I) {I.x(); i = I.i; v = I.v;}

    boolean       b()          {      x();               return i;}
    void          x()          {if (!v) stop("Bool has not been set yet");}
    Bool          X()          {v = true; return this;}

    Bool        set()          {i = true;  v = true;     return this;}
    Bool        set(boolean I) {i = I;     v = true;     return this;}
    Bool        set(Bool    I) {I.x(); i = I.i; v = I.v; return this;}
    Bool      clear()          {i = false; v = true;     return this;}
    Bool       flip()          {      x(); i = !i;       return this;}

    Bool        Set()          {return dup().set();}
    Bool        Set(boolean I) {return dup().set(I);}
    Bool        Set(Bool    I) {return dup().set(I);}
    Bool      Clear()          {return dup().clear();}
    Bool       Flip()          {return dup().flip();}

    Bool         eq(boolean e){  x(); return new Bool(i == e);}
    Bool         ne(boolean e){  x(); return new Bool(i != e);}

    Bool         eq(Bool    e){e.x(); return eq(e.i);}
    Bool         ne(Bool    e){e.x(); return ne(e.i);}

    boolean oR(boolean...b)                                                     // "Or" with no short circuit
     {x(); boolean r = b();
      for (int i : range(b.length))
       {if (r) break;
        r = b[i];
       }
      return r;
     }
    Bool or(boolean...b) {set(oR(b)); return this;}                             // "Or" with no short circuit - modify in place
    Bool Or(boolean...b) {return new Bool(oR(b));}                              // "Or" with no short circuit - duplicate

    @SafeVarargs
    final Bool oR(Supplier<Bool>...b)                                           // "Or" with short circuit
     {x(); boolean r = b();
      for (int i : range(b.length))
       {if (r) break;
        final Bool B = b[i].get();
        B.x();
        r = B.b();
       }
      return new Bool(r);
     }
    @SafeVarargs final Bool or(Supplier<Bool>...b) {set(oR(b)); return this;}   // "Or" with short circuit - modify in place
    @SafeVarargs final Bool Or(Supplier<Bool>...b) {return new Bool(oR(b));}    // "Or" with short circuit - duplicate

    Bool anD(boolean...b)                                                       // "And" without short circuit
     {x(); boolean r = b();
      for (int i : range(b.length))
       {if (!r) break;
        r = b[i];
       }
      return new Bool(r);
     }
    Bool and(boolean...b) {set(anD(b)); return this;}                           // "And" without short circuit - modify in place
    Bool And(boolean...b) {return new Bool(anD(b));}                            // "And" without short circuit - duplicate

    @SafeVarargs
    final Bool anD(Supplier<Bool>...b)                                          // "And" with short circuit
     {x(); boolean r = b();
      for (int i : range(b.length))
       {if (!r) break;
        final Bool B = b[i].get();
        B.x();
        r = B.b();
       }
      return new Bool(r);
     }
    @SafeVarargs final Bool and(Supplier<Bool>...b) {set(anD(b)); return this;} // "And" with short circuit - modify in place
    @SafeVarargs final Bool And(Supplier<Bool>...b) {return new Bool(anD(b));}  // "And" with short circuit - duplicate

    Bool  Nor(boolean       ...b) {return  Or(b).flip();}                       // Not of "or"
    Bool Nand(boolean       ...b) {return And(b).flip();}                       // Not of "and"

    @SafeVarargs final Bool  Nor(Supplier<Bool>...b) {return  Or(b).flip();}    // Not of short circuited "or"
    @SafeVarargs final Bool Nand(Supplier<Bool>...b) {return And(b).flip();}    // Not of short circuited "and"

    Bool dup() {x(); final Bool I = new Bool(i); I.n = n; return I;}            // Duplicate a valid boolean

    public String toString()                                                    // Print the boolean
     {return (n == null ? "" : n+"=")+i;
     }
   }

  class Int                                                                     // An integer that can be passed as a parameter to a method and modified there-in
   {private int        i = 0;                                                   // Value of the integer
    private boolean    v = false;                                               // Whether the current value of the integer is valid or not
    private String     n = null;                                                // An optional name for this variable
    private final int id = nextIntId++;                                         // Unique id for Int

    Bool    valid() {return new Bool( v);}                                      // A valid integer
    Bool notValid() {return new Bool(!v);}                                      // A not valid integer
    int         i() {x(); return i;}                                            // Current value

    Int (int I)      {i = I;   v = true;}
    Int (Int I)      {if (I != null) {i = I.i; v = I.v;}}
    Int      ()      {}

    Int  max (int I) {x(); return i < I ? new Int(I) : this;}
  //Int  max (Int I) {        I.x(); max(I.i);      return mc("max");}
    Int  min (int I) {x(); return i > I ? new Int(I) : this;}
  //Int  min (Int I) {        I.x(); min(I.i);      return mc("min");}

    enum Ops {X, i, add, add2, sub, mul, div, mod, inc, dec, up, down, sqrt, neg, abs, max, min};

    Int  X   ()      {return ex ? ex(Ops.X      ) : in(Ops.X      );}
    Int  i   (int I) {return ex ? ex(Ops.i   , I) : in(Ops.i   , I);}
    Int  i   (Int I) {return ex ? ex(Ops.i   , I) : in(Ops.i   , I);}
    Int  add (int I) {return ex ? ex(Ops.add , I) : in(Ops.add , I);}
    Int  add (Int I) {return ex ? ex(Ops.add , I) : in(Ops.add , I);}
    Int  add2(Int I) {return ex ? ex(Ops.add2, I) : in(Ops.add2, I);}
    Int  sub (int I) {return ex ? ex(Ops.sub , I) : in(Ops.sub , I);}
    Int  sub (Int I) {return ex ? ex(Ops.sub , I) : in(Ops.sub , I);}
    Int  mul (int I) {return ex ? ex(Ops.mul , I) : in(Ops.mul , I);}
    Int  mul (Int I) {return ex ? ex(Ops.mul , I) : in(Ops.mul , I);}
    Int  div (int I) {return ex ? ex(Ops.div , I) : in(Ops.div , I);}
    Int  div (Int I) {return ex ? ex(Ops.div , I) : in(Ops.div , I);}
    Int  mod (int I) {return ex ? ex(Ops.mod , I) : in(Ops.mod , I);}
    Int  mod (Int I) {return ex ? ex(Ops.mod , I) : in(Ops.mod , I);}
    Int  inc ()      {return ex ? ex(Ops.inc    ) : in(Ops.inc    );}
    Int  dec ()      {return ex ? ex(Ops.dec    ) : in(Ops.dec    );}
    Int  up  ()      {return ex ? ex(Ops.up     ) : in(Ops.up     );}
    Int  down()      {return ex ? ex(Ops.down   ) : in(Ops.down   );}
    Int  sqrt()      {return ex ? ex(Ops.sqrt   ) : in(Ops.sqrt   );}
    Int  neg ()      {return ex ? ex(Ops.neg    ) : in(Ops.neg    );}
    Int  abs ()      {return ex ? ex(Ops.abs    ) : in(Ops.abs    );}

    void x   ()      {if (!v) stop("Int has not been set yet");}

    Int ex(Ops op)
     {switch(op)
       {case inc -> {x(); add(1);                }
        case dec -> {x(); sub(1);                }
        case up  -> {x(); i  <<= 1;              }
        case down-> {x(); i >>>= 1;              }
        case sqrt-> {x(); i = (int)Math.sqrt(i); }
        case neg -> {x(); i = -i;                }
        case abs -> {x(); i = i < 0 ? -i : i;    }
        default  -> stop("Op not implemented:", op);
       }
      return this;
     }

    Int ex(Ops op, int I)
     {switch (op)
       {case i   -> {      i  = I;     v = true; }
        case add -> { x(); i += I;     v = true; }
        case sub -> { x(); i -= I;     v = true; }
        case mul -> { x(); i *= I;     v = true; }
        case div -> { x(); i /= I;     v = true; }
        case mod -> { x(); i %= I;     v = true; }
        case add2-> { x(); i += I + I; v = true; }
        default  -> stop("Op not implemented:", op);
       }
      return this;
     }

    Int ex(Ops op, Int I)
     {switch(op)
       {case i  -> {i = I.i;  v = I.v;   }
        default -> {I.x(); ex(op, I.i());}
       }
      return this;
     }

    Int in(Ops op)        {new I() {void action() {ex(op)   ;}}; return this;}
    Int in(Ops op, int I) {new I() {void action() {ex(op, I);}}; return this;}
    Int in(Ops op, Int I) {new I() {void action() {ex(op, I);}}; return this;}

    Int  Add (int I) {return dup().add(I);}
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

    Bool eq(int e){  x(); return new Bool(i == e);}
    Bool ne(int e){  x(); return new Bool(i != e);}
    Bool le(int e){  x(); return new Bool(i <= e);}
    Bool lt(int e){  x(); return new Bool(i <  e);}
    Bool ge(int e){  x(); return new Bool(i >= e);}
    Bool gt(int e){  x(); return new Bool(i >  e);}

    Bool eq(Int e){e.x(); return eq(e.i);}
    Bool ne(Int e){e.x(); return ne(e.i);}
    Bool le(Int e){e.x(); return le(e.i);}
    Bool lt(Int e){e.x(); return lt(e.i);}
    Bool ge(Int e){e.x(); return ge(e.i);}
    Bool gt(Int e){e.x(); return gt(e.i);}

    Bool Eq(int e){  x(); return new Bool(eq(e));}
    Bool Ne(int e){  x(); return new Bool(ne(e));}
    Bool Le(int e){  x(); return new Bool(le(e));}
    Bool Lt(int e){  x(); return new Bool(lt(e));}
    Bool Ge(int e){  x(); return new Bool(ge(e));}
    Bool Gt(int e){  x(); return new Bool(gt(e));}

    Bool Eq(Int e){e.x(); return Eq(e.i);}
    Bool Ne(Int e){e.x(); return Ne(e.i);}
    Bool Le(Int e){e.x(); return Le(e.i);}
    Bool Lt(Int e){e.x(); return Lt(e.i);}
    Bool Ge(Int e){e.x(); return Ge(e.i);}
    Bool Gt(Int e){e.x(); return Gt(e.i);}

    Int dup() {x(); final Int I = new Int(i); I.v = v; I.n = n; return I;}      // Duplicate a valid integer

    public String toString()                                                    // Print the integer
     {return (n == null ? "" : n+"=")+i;
     }
   }

  class Ref<T>                                                                  // A reference to an object
   {T i;                                                                        // Value of the object
    Ref()              {i = null;}                                              // Create a null reference
    Ref(T I)           {i = I;}                                                 // Create a reference to the object
    void set(T I)      {i = I;}                                                 // Set the refernce
    void set(Ref<T> I) {i = I.get();}                                           // Set the refernce
    T    get()         {return i;}                                              // Dereference the reference
    Bool valid()       {return new Bool(i != null);}                            // Check that the refence is valid

    public String toString()                                                    // Print the reference
     {return i == null ? "null" : "ref("+i+")";
     }
   }

  static int[]range(Int Limit) {return range(Limit.i());}                       // Range of integers
  static boolean ok(Bool b) {return ok(b.b());}                                 // Check test results match expected results.

//D1 Machine Code                                                               // Generate machine code instructions to implement the program

//D2 Instruction                                                                // An instruction represents code to be executed by a process in a single clock cycle == process step

  abstract class I                                                              // Instructions implement the action of a program
   {final int instructionNumber;                                                // The number of this instruction
    final boolean     mightJump;                                                // The instruction might cause a jump
    final String      traceBack = traceBack();                                  // Line at which this instruction was created

    final String traceBackOnOneLine()                                           // Line at which this instruction was created represented with out new lines
     {return traceBack.replace("\n", "|").trim();
     }

    I(boolean MightJump)                                                        // Add this instruction to the code for the process
     {instructionNumber = code.size();                                          // Number each instruction
      mightJump = MightJump;
      code.push(this);                                                          // Save instruction
     }

    I() {this(false);}                                                          // Add this instruction to the process's code assunming it will not jump

    abstract void action();                                                     // The action to be performed by the instruction
   }

  class Label                                                                   // Label jump targets in the program
   {int offset;                                                                 // The instruction location to which this labels applies
    Label()    {set(); labels.push(this);}                                      // A label assigned to an instruction location
    void set() {offset = code.size();}                                          // Reassign the label to an instruction
   }

  void execute()                                                                // Execute the current code
   {pc = 0;
    final int N = code.size();                                                  // Number of instructions
    for(int c = 0; c < maxSteps && pc >= 0 && pc < N; ++c)                      // Execute each instruction within a specified number of steps
     {final I i = code.elementAt(pc);
      try
       {pc++;                                                                   // This is the anticipated next instruction, but the instruction can set it to effect a branch in execution flow
        i.action();
       }
      catch(Exception e)
       {stop("Exception:", e, "while executing:", traceBack(e));
       }
      if (code.size() != N) stop("Instruction added");
     }
   }

//D1 Testing                                                                    // Test expected output against got output

  static int testsPassed = 0, testsFailed = 0;                                  // Number of tests passed and failed

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
    ok(b1.Or(  ()->{return b2;}).b() == true);
    ok(b1.Nor (()->{return b2;}).b() == false);
    ok(b1.And (()->{return b2;}).b() == false);
    ok(b1.Nand(()->{return b2;}).b() == true);
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
   {final Programming P = new Programming();
    P.ex = false;
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
   {final Programming P = new Programming();
    final Int a = P.new Int(0);
    final Int b = P.new Int(1);
    final Int c = P.new Int(0);
    final Int i = P.new Int(10);
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_programming();
    test_bool();
    test_traceNames();
    test_add();
   }

  static void newTests()                                                        // Tests being worked on
   {oldTests();
   }

  public static void main(String[] args)                                        // Test if called as a program
   {try                                                                         // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {if (github_actions) oldTests(); else newTests();                          // Tests to run
      testSummary();                                                            // Summarize test results
      System.exit(testsFailed);
     }
    catch(Exception e)                                                          // Get a traceback in a format clickable in Geany
     {System.err.println(e);
      System.err.println(fullTraceBack(e));
      System.exit(testsFailed);
     }
   }
 }
