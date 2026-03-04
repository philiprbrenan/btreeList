//jar maven/target/javaparser-1.0.0.jar
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.io.File;
import javax.tools.*;
import com.sun.source.util.*;
import com.sun.source.tree.*;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import java.util.Random;

public class Syntax extends Test
 {public static void parse() throws Exception
   {JavaCompiler                       compiler    = ToolProvider.getSystemJavaCompiler();
    StandardJavaFileManager            fileManager = compiler.getStandardFileManager(null, null, null);
    Iterable<? extends JavaFileObject> files       = fileManager.getJavaFileObjects(new File("BitSet.java"));

    JavacTask task = (JavacTask) compiler.getTask(null, fileManager, null, null, null, files);

    Iterable<? extends CompilationUnitTree> trees = task.parse();

    for (CompilationUnitTree tree : trees)
     {var p = new TreeScanner<Void, Integer>()
       {String in(int Depth) {return "  ".repeat(Depth);}
        public Void scan(Tree node, Integer depth)
         {if (node == null) return null;
          return super.scan(node, depth);
         }

        public Void visitCompilationUnit(CompilationUnitTree node, Integer depth)
         {say(in(depth), "CompilationUnit");
          return super.visitCompilationUnit(node, depth + 1);
         }

        public Void visitClass(ClassTree node, Integer depth)
         {say(in(depth),  "Class:      " + node.getSimpleName());
          return super.visitClass(node, depth + 1);
         }

        public Void visitMethod(MethodTree node, Integer depth)
         {say(in(depth),  "Method:     ", node.getName());
          return super.visitMethod(node, depth + 1);
         }

        public Void visitVariable(VariableTree node, Integer depth)
         {say(in(depth),  "Variable:   ", node.getName(), node.getType());
          return super.visitVariable(node, depth + 1);
         }

        public Void visitIdentifier(IdentifierTree node, Integer depth)
         {say(in(depth),  "Identifier: ", node.getName());
          return super.visitIdentifier(node, depth + 1);
         }
       };

      tree.accept(p, 0);
     }
   }

  public static void main(String[] args)
   {try
     {parse();
     }
    catch(Exception e) {say(e);}
    Random r = new Random();
    int a = r.nextInt(), b = r.nextInt(), c = a + b;
   }
 }
