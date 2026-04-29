//----------------------------------------------------------------------------------------------------------------------
// Test a java program
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
                                                                                                                        
  final int maxSteps = 999;                                                                                             // Number of steps permitted in code execution
  int      nextIntId = 0;                                                                                               // Unique id for each Int
  int     nextBoolId = 0;                                                                                               // Unique id for each Bool
  boolean         ex = true;                                                                                            // Execute immediately if true else generate machine code and execute later
  int             pc;                                                                                                   // Program counter - set to something less than zero to stop with a return code
                                                                                                                        
//D1 Programming                                                                                                        // Program structures
                                                                                                                        
  abstract class For                                                                                                    // For loop
   {For(int Start, int End)                                                                                             // Execute the loop the specified number of times
     {final Int  index = new Int();                                                                                     
      final Bool  cont = new Bool();                                                                                    
                                                                                                                        
      if (ex)                                                                                                           // Immediate execution
       {for(int i : range(Start, End))                                                                                  // Iterate over the specified range
         {index.i(i);                                                                                                   // Set the index to each element of the specified range
          cont.clear();                                                                                                 // Terminate unless told otherwise
          body(index, cont);                                                                                            // Execute the loop
          if (cont.Flip().b()) break;                                                                                   // Terminate the loop unless continuation requested
         }                                                                                                              
       }                                                                                                                
      else                                                                                                              // Machine code
       {index.i(Start);                                                                                                 // Start index
        final Label start = new Label();                                                                                // Start of for loop code
        final Label   end = new Label();                                                                                // End of for loop code
        cont.clear();                                                                                                   // Terminate unless told otherwise
        body(index, cont);                                                                                              // Execute the loop
        index.inc();                                                                                                    // Increment lop counter
        new I()                                                                                                         
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
        new I()                                                                                                         // Jump to else if condition is false            
         {void action()                                                                                                 
           {if (!Condition.b()) pc = lse.offset;                                                                        
           }                                                                                                            
         };                                                                                                             
        Then();                                                                                                         // Then body
        new I()                                                                                                         // Jump over else to end
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
                                                                                                                        
    enum Op {Eq, Flip, Ne, Set};                                                                                        // Boolean operation classification by argument types

    Bool      valid() {return new Bool(v);}

    Bool           ()          {}
    Bool           (boolean I) {i(Op.Set, I); i = I; v = true;}
    Bool           (Bool    I) {i(Op.Set, I); I.x(); i = I.i; v = I.v;}

    boolean       b()          {      x();               return i;}
    void          x()          {if (!v) stop("Bool has not been set yet");}
    Bool          X()          {v = true; return this;}

    Bool        set()          {i(Op.Set,  true);  i = true;           v = true; return this;}
    Bool        set(boolean I) {i(Op.Set,  I);     i = I;              v = true; return this;}
    Bool        set(Bool    I) {i(Op.Set,  I);     I.x(); i = I.i;     v = I.v;  return this;}
    Bool        set(Int     I) {i(Op.Set,  I);     I.x(); i = I.i > 0; v = I.v;  return this;}
    Bool      clear()          {i(Op.Set,  false); i = false;          v = true; return this;}
    Bool       flip()          {i(Op.Flip); x();   i = !i;                       return this;}

    Bool        Set()          {return dup().set();}
    Bool        Set(boolean I) {return dup().set(I);}
    Bool        Set(Bool    I) {return dup().set(I);}
    Bool      Clear()          {return dup().clear();}
    Bool       Flip()          {return dup().flip();}

    Bool         eq(boolean I) {i(Op.Eq,  I);   x(); return new Bool(i == I);}
    Bool         ne(boolean I) {i(Op.Ne,  I);   x(); return new Bool(i != I);}

    Bool         eq(Bool    I) {i(Op.Eq,  I); I.x(); return eq(I.i);}
    Bool         ne(Bool    I) {i(Op.Ne,  I); I.x(); return ne(I.i);}

    //boolean oR(boolean...b)                                                                                             // "Or" with no short circuit
    // {x(); boolean r = b();                                                                                          
    //  for (int i : range(b.length))                                                                                  
    //   {if (r) break;                                                                                                
    //    r = b[i];                                                                                                    
    //   }                                                                                                             
    //  return r;                                                                                                      
    // }                                                                                                               
    //Bool or(boolean...b) {set(oR(b)); return this;}                                                                     // "Or" with no short circuit - modify in place
    //Bool Or(boolean...b) {return new Bool(oR(b));}                                                                      // "Or" with no short circuit - duplicate
                                                                                                                       
    @SafeVarargs                                                                                                       
    final Bool Or(Supplier<Bool>...b)                                                                                   // "Or" with short circuit
     {x(); final Bool r = new Bool(b());                                                                                // Start with the current value
      for (int i : range(b.length))                                                                                     // Test each additional value as necessary
       {if (r.b()) break;                                                                                               // Finish when we know the result
        r.set(b[i].get());                                                                                              // Check additional operands
       }                                                                                                               
      return r;                                                                                                        
     }                                                                                                                 
    @SafeVarargs final Bool or(Supplier<Bool>...b) {set(Or(b)); return this;}                                           // "Or" with short circuit - modify in place
                                                                                                                       
    //Bool anD(boolean...b)                                                                                               // "And" without short circuit
    // {x(); boolean r = b();                                                                                          
    //  for (int i : range(b.length))                                                                                  
    //   {if (!r) break;                                                                                               
    //    r = b[i];                                                                                                    
    //   }                                                                                                             
    //  return new Bool(r);                                                                                            
    // }                                                                                                               
    //Bool and(boolean...b) {set(anD(b)); return this;}                                                                   // "And" without short circuit - modify in place
    //Bool And(boolean...b) {return new Bool(anD(b));}                                                                    // "And" without short circuit - duplicate
                                                                                                                       
    @SafeVarargs                                                                                                       
    final Bool And(Supplier<Bool>...b)                                                                                  // "And" with short circuit
     {x(); final Bool r = new Bool(b());                                                                                // Start with the current value
      for (int i : range(b.length))                                                                                     // Test each additional value as necessary
       {if (!r.b()) break;                                                                                              // Finish when we know the result
        r.set(b[i].get());                                                                                              // Check additional operands
       }                                                                                                               
      return r;                                                                                                        
     }                                                                                                                 
    @SafeVarargs final Bool and(Supplier<Bool>...b) {set(And(b)); return this;}                                         // "And" with short circuit - modify in place
                                                                                                                       
    //Bool  Nor(boolean       ...b) {return  Or(b).flip();}                                                               // Not of "or"
    //Bool Nand(boolean       ...b) {return And(b).flip();}                                                               // Not of "and"
                                                                                                                       
    //@SafeVarargs final Bool  Nor(Supplier<Bool>...b) {return  Or(b).flip();}                                            // Not of short circuited "or"
    //@SafeVarargs final Bool Nand(Supplier<Bool>...b) {return And(b).flip();}                                            // Not of short circuited "and"
                                                                                                                       
    Bool dup() {x(); final Bool I = new Bool(i); I.n = n; return I;}                                                    // Duplicate a valid boolean
                                                                                                                       
    public String toString()                                                                                            // Print the boolean
     {return (n == null ? "" : n+"=")+i;                                                                               
     }                                                                                                                 
                                                                                                                       
    void i(Op Op)                                                                                                       // Generate instruction 
     {if (!ex) return;                                                                                                  // Avoid generating code when executing directly as the amount of code generated can be large
     }                                                                                                                 
                                                                                                                       
    void i(Op Op, boolean I)                                                                                            // Generate instruction for single boolean argument
     {if (!ex) return;                                                                                                  // Avoid generating code when executing directly as the amount of code generated can be large
     }                                                                                                                 
                                                                                                                       
    void i(Op Op, Bool    I)                                                                                            // Generate instruction for single Bool argument
     {if (!ex) return;                                                                                                  // Avoid generating code when executing directly as the amount of code generated can be large
     }                                                                                                                 
                                                                                                                       
    void i(Op Op, Int     I)                                                                                            // Generate instruction for single Int argument
     {if (!ex) return;                                                                                                  // Avoid generating code when executing directly as the amount of code generated can be large
      switch(Op)                   
       {case Set -> {new I() {void action() {say("CCCC");I.x(); i = I.i > 0; v = true;}};}
        default  -> stop("Op not implemented:", Op);
       }
     }                                                                                                                  
   }                                                                                                                   
                                                                                                                       
  class Int                                                                                                             // An integer that can be passed as a parameter to a method and modified there-in
   {private int        i = 0;                                                                                           // Value of the integer
    private boolean    v = false;                                                                                       // Whether the current value of the integer is valid or not
    private String     n = null;                                                                                        // An optional name for this variable
    private final int id = nextIntId++;                                                                                 // Unique id for Int
                                                                                                                       
    Bool    valid() {return new Bool( v);}                                                                              // A valid integer
    Bool notValid() {return new Bool(!v);}                                                                              // A not valid integer
    int         i() {x(); return i;}                                                                                    // Current value

    Int (int I)      {i = I;   v = true;}
    Int (Int I)      {if (I != null) {i = I.i; v = I.v;}}
    Int      ()      {}

    Int  max (int I) {x(); return i < I ? new Int(I) : this;}
  //Int  max (Int I) {        I.x(); max(I.i);      return mc("max");}
    Int  min (int I) {x(); return i > I ? new Int(I) : this;}
  //Int  min (Int I) {        I.x(); min(I.i);      return mc("min");}

    enum Ops {X, i, add, add2, sub, mul, div, mod, inc, dec, up, down, sqrt, neg, abs, max, min};

    Int  X   ()      {return ie(Ops.X      );}
    Int  i   (int I) {return ie(Ops.i   , I);}
    Int  i   (Int I) {return ie(Ops.i   , I);}
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

    void x   ()      {if (!v) stop("Int has not been set yet");}

    Int  ie  (Ops Op)        {return ex ? ex(Op   ) : in(Op   );}
    Int  ie  (Ops Op, int I) {return ex ? ex(Op, I) : in(Op, I);}
    Int  ie  (Ops Op, Int I) {return ex ? ex(Op, I) : in(Op, I);}

    Int ex(Ops Op)
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

    Int ex(Ops Op, int I)
     {switch (Op)
       {case i   -> {      i  = I;     v = true; }
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

    Int ex(Ops Op, Int I)
     {switch(Op)
       {case i  -> {i = I.i;  v = I.v;   }
        default -> {I.x(); ex(Op, I.i());}
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
  String output()           {return joinLines(put)+"\n";        
}                                                               // Output from execution
                                                                                                                        
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
                                                                                                                        
    I() {this(false);}                                         
                                                                 // Add this instruction to the process's code assunming it will not jump
                                                                                                                        
    abstract void action();                                                                                             // The action to be performed by the instruction
   }                                                                                                                    
                                                                                                                        
  class Label                                                                                                           // Label jump targets in the program
   {int offset;                                                                                                         // The instruction location to which this labels applies
    Label()    {set(); labels.push(this);}                                                                              // A label assigned to an instruction location
    void set() {offset = code.size();}                                                                                  // Reassign the label to an instruction
   }                                                                                                                    
                                                                                                                        
  void execute()                                                                                                        // Execute the current code
   {pc = 0;                                                                                                             
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
    ok(b1.Or(  ()->{return b2;}).b() == true);
    //ok(b1.Nor (()->{return b2;}).b() == false);
    ok(b1.And (()->{return b2;}).b() == false);
    //ok(b1.Nand(()->{return b2;}).b() == true);
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
    P.ex = false;
    final Int a = P.new Int(0);
    final Int b = P.new Int(1);
    final Int c = P.new Int(0);
    final Int N = P.new Int(10);
    P.new For(N)
     {void body(Int Index, Bool Continue)
       {c.i(a);
        c.add(b);
        a.i(b);
        b.i(c);
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
   {final Programming P = new Programming();
    P.ex = false;
    final Int  a = P.new Int();
    final Bool c = P.new Bool();
    final Int  N = P.new Int(10);
    P.new For(N)
     {void body(Int Index, Bool Continue)
       {a.i(Index);
        a.mod(2);
        c.set(a);
        P.put(a);
        Continue.set();
       }
     };
    P.execute();
    stop(P.output());
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
  
  static void oldTests()                                                                                                // Tests thought to be in good shape
   {test_programming();                                                                                                 
    test_bool();                                                                                                        
    test_traceNames();                                                                                                  
    test_add();                                                                                                         
    test_fibonnacci();                                                                                                  
   }                                                                                                                    
                                                                                                                        
  static void newTests()                                                                                                // Tests being worked on
   {oldTests();                                                                                                       
    //test_mod();                                                                                                  
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
