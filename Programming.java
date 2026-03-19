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
  boolean mc = false;                                                           // Generate machine code if true

//D1 Programming                                                                // Program structures

  abstract static class For                                                     // For loop
   {For(int Start, int End)                                                     // Execute the loop the specified number of times
     {for(int i : range(Start, End)) if (!body(new Int(i)).b()) break;          // Execute the loop as long as it returns true
     }

    For(int End) {this(0, End);}                                                // Execute the loop the specified number of times as long as it returns true
    For(Int End) {this(0, End.i());}                                            // Execute the loop the specified number of times as long as it returns true

    Bool body(Int Index) {return new Bool(false);}                              // Body of the for loop: return flse to terminate execution of the loop
   }

  abstract static class If                                                      // If statement
   {If (boolean condition)
     {if (condition) Then(); else Else();
     }
    If (Bool    condition)
     {if (condition.b()) Then(); else Else();
     }

    abstract void Then();                                                       // Then clause
             void Else() {}                                                     // Else clause
   }

  static class Bool                                                             // An integer that can be passed as a parameter to a method and modified there-in
   {private boolean i = false;                                                  // Value of the integer
    private boolean v = false;                                                  // Whether the current value of the integer is valid or not
    private String  n = null;                                                   // An optional name for this variable
    final static Bool  True = new Bool(true);                                   // Useful constants
    final static Bool False = new Bool(false);
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

    Bool or(boolean...b)                                                        // "Or" with no short circuit
     {x(); boolean r = b();
      for (int i : range(b.length))
       {if (r) break;
        r = b[i];
       }
      return new Bool(r);
     }

    @SafeVarargs
    final Bool or(Supplier<Bool>...b)                                           // "Or" with short circuit
     {x(); boolean r = b();
      for (int i : range(b.length))
       {if (r) break;
        final Bool B = b[i].get();
        B.x();
        r = B.b();
       }
      return new Bool(r);
     }

    Bool and(boolean...b)                                                       // "And" with no short circuit
     {x(); boolean r = b();
      for (int i : range(b.length))
       {if (!r) break;
        r = b[i];
       }
      return new Bool(r);
     }

    @SafeVarargs
    final Bool and(Supplier<Bool>...b)                                          // "And" with short circuit
     {x(); boolean r = b();
      for (int i : range(b.length))
       {if (!r) break;
        final Bool B = b[i].get();
        B.x();
        r = B.b();
       }
      return new Bool(r);
     }

    Bool  nor(boolean       ...b) {return  or(b).flip();}
    Bool nand(boolean       ...b) {return and(b).flip();}

    @SafeVarargs final Bool  nor(Supplier<Bool>...b) {return  or(b).flip();}
    @SafeVarargs final Bool nand(Supplier<Bool>...b) {return and(b).flip();}

    Bool dup() {x(); final Bool I = new Bool(i); I.n = n; return I;}            // Duplicate a valid boolean

    public String toString()                                                    // Print the boolean
     {return (n == null ? "" : n+"=")+i;
     }
   }

  static class Int                                                              // An integer that can be passed as a parameter to a method and modified there-in
   {private int     i = 0;                                                      // Value of the integer
    private boolean v = false;                                                  // Whether the current value of the integer is valid or not
    private String  n = null;                                                   // An optional name for this variable
    final static Int zero = new Int(0);                                         // Useful constants
    Bool    valid() {return new Bool( v);}                                      // A valid integer
    Bool notValid() {return new Bool(!v);}                                      // A not valid integer

    Int           (int I) {       i = I;   v = true;}
    Int           (Int I) {       if (I != null) {i = I.i; v = I.v;}}

    Int      ()      {}
    void x   ()      {if (!v) stop("Int has not been set yet");}
    Int  X   ()      {v = true;                     return this;}
    int  i   ()      {          x();                return i;}
    Int  i   (int I) {i     = I;     X();           return this;}
    Int  i   (Int I) {i     = I.i; v = I.v;         return this;}
    Int  add (int I) {i    += I;x(); X();           return this;}
    Int  add (Int I) {        I.x(); add(I.i());    return this;}
    Int  add2(Int I) {        I.x(); add(I.i()*2);  return this;}
    Int  sub (int I) {i    -= I;x(); X();           return this;}
    Int  sub (Int I) {        I.x(); sub(I.i());    return this;}
    Int  mul (int I) {i    *= I;x(); X();           return this;}
    Int  mul (Int I) {        I.x(); mul(I.i());    return this;}
    Int  div (int I) {i    /= I;x(); X();           return this;}
    Int  div (Int I) {        I.x(); div(I.i());    return this;}
    Int  mod (int I) {i    %= I;x(); X();           return this;}
    Int  mod (Int I) {        I.x(); mod(I.i());    return this;}
    Int  inc ()      {          x(); add(1);        return this;}
    Int  dec ()      {          x(); sub(1);        return this;}
    Int  up  ()      {i  <<= 1; x();                return this;}
    Int  down()      {i >>>= 1; x();                return this;}
    Int  sqrt()      {x(); i = (int)java.lang.Math.sqrt(i); X(); return this;}
    Int  neg ()      {x(); i = -i;   X();           return this;}
    Int  abs ()      {x(); i = i < 0 ? -i : i; X(); return this;}
    Int  max (int I) {x(); return i < I ? new Int(I) : this;}
    Int  max (Int I) {        I.x(); max(I.i);      return this;}
    Int  min (int I) {x(); return i > I ? new Int(I) : this;}
    Int  min (Int I) {        I.x(); min(I.i);      return this;}

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

  static class Ref<T>                                                           // A reference to an object
   {private T i;                                                                // Value of the object
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

//D1 Testing                                                                    // Test expected output against got output

  static int testsPassed = 0, testsFailed = 0;                                  // Number of tests passed and failed

  static void test_programming()
   {final Int i = new Int(0);
    class test_programming
     {test_programming(int N)
       {new For(N)
         {Bool body(Int Index)
           {new If (Index.Mod(2).eq(0))
             {void Then() {i.add(Index);}
              void Else() {i.sub(Index);}
             };
            return new Bool(true);
           }
         };
       }
     }
    new test_programming(11);
    ok(i, 5);
    ok(i.valid().b());
   }

  static void test_bool()
   {final Bool b1 = new Bool().clear();
    final Bool b2 = new Bool().set();
    ok(b1.or(  ()->{return b2;}).b() == true);
    ok(b1.nor (()->{return b2;}).b() == false);
    ok(b1.and (()->{return b2;}).b() == false);
    ok(b1.nand(()->{return b2;}).b() == true);
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

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_programming();
    test_bool();
    test_traceNames();
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
