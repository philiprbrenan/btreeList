//------------------------------------------------------------------------------
// Test a java program.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2025
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.text.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPOutputStream;

//D1 Utility routines                                                           // Utility routines

//D2 Construct                                                                  // Develop and test a java program

public class Test                                                               // Describe a chip and emulate its operation.
 {final static boolean github_actions          =                                // Whether we are on a github
    "true".equals(System.getenv("GITHUB_ACTIONS"));
  final static long start                      = System.nanoTime();             // Start time
  final static Stack<String>     sayThisOrStop = new Stack<>();                 // The next says should say this or else we should stop
  final static TreeSet<String>    filesWritten = new TreeSet<>();               // Files written
  final static TreeSet<String>   testsExecuted = new TreeSet<>();               // Tests executed
  final static boolean theShorterIsTheDaughter = true;                          // True for a shorter traceback during tests to get more counts on the page at a time in Geany
  final static boolean        coverageAnalysis = false;                         // Enables coverage checks

  Test Test() {return this;}                                                    // Instance

//D2 String routines                                                            // String routines

  static String binaryString(int n, int width)                                  // Convert a integer to a binary string of specified width
   {final String b = "0".repeat(width)+Long.toBinaryString(n);
    return b.substring(b.length() - width);
   }

  static String   ones(int n) {return "1".repeat(n);}                           // A string of ones
  static String  zeros(int n) {return "0".repeat(n);}                           // A string of zeros

  static int longestLine(String s)                                              // Longest line  in a string
   {int l = 0, i = 0, j = 0;
    for (; i < s.length(); i++, j++)
     {if (s.charAt(i) == '\n')
       {l = max(l, j);
        j = 0;
       }
     }
    return max(l, j);
   }

  static String joinStrings(Stack<String> S, String join)                       // Perl join
   {final StringJoiner t = new StringJoiner(join);
    for  (String s : S) t.add(s);
    return ""+t;
   }

  static String joinStringBuilders(Stack<StringBuilder> S, String join)         // Perl join
   {final Stack<String> t = new Stack<>();
    for(StringBuilder s: S) t.push(s.toString());
    return joinStrings(t, join);
   }

  static String joinStrings(Set<String> S, String join)                         // Perl join
   {final StringJoiner t = new StringJoiner(join);
    for (String s: S) t.add(s);
    return ""+t;
   }

  static String joinLines(Stack<String> S) {return joinStrings(S, "\n");}       // Perl join lines

  static String differentiateLines(int margin, String input)                    // Show differences between each line and its predecessor
   {final String[] L = input.split("\n");
    final int      N = L.length;
    if (N < 2) return input;                                                    // No action required

    final StringBuilder D = new StringBuilder(L[0]+'\n');                       // First line is all new

    for (int i = 1; i < N; i++)                                                 // Check each subsequent line against the prior one
     {final String a = L[i-1], b = L[i];
      final int    v = min(a.length(), b.length());                             // Valid overlap

      if (v > margin)                                                           // Something beyond the margin in the valid overlap
       {int c = 0;
        final StringBuilder d = new StringBuilder(b.substring(0, margin));      // Load the margin
        for (int j = margin; j < v; j++)                                        // Compare each character in the valid overlap with the previous one
         {final char A = a.charAt(j), B = b.charAt(j);
          if (A == B && (A == '0' || A == '1')) d.append('.');
          else                           { ++c; d.append(B);}
         }
        d.append(b.substring(v, b.length())+'\n');                              // Remainder of current line outside valid overlap
        if (c > 0) D.append(d);
       }
      else D.append(b+'\n');                                                    // A short line that did not reach the margin
     }
    return ""+D;
   }

  static void writeProperties                                                   // Write a properties file
   (String fileName, TreeMap<String,String> properties)
   {final StringBuilder s = new StringBuilder();                                // Properties written out
    for (String k : properties.keySet())                                        // Each key
     {s.append(k.trim()+"="+properties.get(k).trim()+"\n");                     // One key=value per line
     }
    writeFile(fileName, s);                                                     // Write propertie to file
   }

  static TreeMap<String,String> readProperties(String filename)                 // Parse a properties file
   {final Properties              properties = new Properties();                // Properties parser
    final TreeMap<String, String> treeMap    = new TreeMap<> ();                // TreeMap to store key-value pairs

    try (final FileInputStream f = new FileInputStream(filename))               // Read file
     {properties.load(f);                                                       // Load properties from the file

      for (String key : properties.stringPropertyNames())                       // Insert key-value pair into TreeMap
       {treeMap.put(key.trim(), properties.getProperty(key).trim());            // Normalize keys and values
       }
     }
    catch(Exception e)
     {e.printStackTrace();
     }
    return treeMap;                                                             // Return treemap
   }

  static String md5Sum(String text)                                             // Md5 sum of a string
   {try
     {final MessageDigest m = MessageDigest.getInstance("MD5");
      final byte[]        b = m.digest(text.getBytes("UTF-8"));
      return HexFormat.of().formatHex(b);
     }
    catch(Exception e) {stop(e);}
    return null;
   }

  static String ref(Object obj)                                                 // Print the address of an object
   {return Integer.toHexString(System.identityHashCode(obj));
   }

  static void replaceAll(StringBuilder S, String s, String t)                   // Replace all instances of a source string with a target string on a string builder
   {if (S == null || s == null || s.isEmpty() || t == null) return;
    int i = 0;
    while((i = S.indexOf(s, i)) != -1)
     {S.replace(i, i + s.length(), t);
      i += t.length();
     }
   }

  static String dateTimeStamp()                                                 // Date and time stamp
   {return ZonedDateTime.now(ZoneOffset.UTC).
      format(DateTimeFormatter.ISO_INSTANT).replace(":", "-");
   }

// D1 BitSet                                                                    // Operations on BitSets

  static int bitSetToInt(BitSet B)                                              // Convert a bitset to an integer if possible
   {final long[]l = B.toLongArray();
    final int   L = l.length;
    if (L == 0) return 0;
    if (L > 1)               stop("BitSet way too big");
    if (l[0] > Integer.MAX_VALUE) stop("BitSet too big:", l[0]);
    return (int)l[0];
   }

  static String bitSetToHex(BitSet B)                                           // Print a bitset as a hex string
   {final int  L = modZero(B.length(), 4), N = L / 4;
    final int[]b = new int[N];
    for (int i = 0; i < L; i++)                                                 // Load bits into integers in fours so that each integer gas one hex digit in it
     {if (B.get(i)) b[i / 4] |= (1 << (i % 4));
     }
    final StringBuilder s = new StringBuilder();
    for (int i = 0; i < N; i++) s.append("0123456789abcdef".charAt(b[N-i-1]));  // Print integers
    return ""+s;
   }

  static String bitSetToHex(BitSet B, int Width)                                // Print a bitset as a hex string with enough leading zeros to fill the field to match icarus verilog
   {final String s = bitSetToHex(B);
    //if (s.equals("0")) return s;
    return "0".repeat(modZero(Width, 4)/4-s.length())+s;
   }

  static BitSet intToBitSet(int N)                                              // Convert an int to a bit set
   {final BitSet b = new BitSet();
    if (N < 0) stop("Positive integers only, not:", N);
    for (int i = 0; i < Integer.SIZE-1; i++) if (((1<<i) & N) > 0) b.set(i);    // Transfer bits from int to bitset
    return b;
   }

//D2 Numeric routines                                                           // Numeric routines

  static int abs(int i) {return i >= 0 ? +i : -i;}                              // Absolute value of integer

  static int ifs(String n)                                                      // Integer from string
   {final Integer i = Integer.parseInt(n);
    if (i == null) stop("Invalid integer;", n);
    return i;
   }

  static int max(int n, int...rest)                                             // Maximum of some numbers
   {int m = n;
    for (int i = 0; i < rest.length; i++) m = m < rest[i] ? rest[i] : m;
    return m;
   }

  static Integer maxInt(Integer n, Integer...rest)                              // Maximum of some Integers with null acting as minus infinity
   {Integer m = n;
    for (int i = 0; i < rest.length; i++)                                       // Find a non null value to act as the initial maximum
     {if (m == null && rest[i] != null) m = rest[i];
     }
    if (m == null) return null;                                                 // All the values are null
    for (int i = 0; i < rest.length; i++)
     {m = rest[i] == null ? m : max(m, rest[i]);
     }
    return m;
   }

  static int min(int n, int...rest)                                             // Minimum of some numbers
   {int m = n;
    for (int i = 0; i < rest.length; i++) m = m > rest[i] ? rest[i] : m;
    return m;
   }

  static Integer minInt(Integer n, Integer...rest)                              // Minimum of some Integers with null acting as plus infinity
   {Integer m = n;
    for (int i = 0; i < rest.length; i++)                                       // Find a non null value to act as the initial minimum
     {if (m == null && rest[i] != null) m = rest[i];
     }
    if (m == null) return null;                                                 // All the values are null
    for (int i = 0; i < rest.length; i++)
     {m = rest[i] == null ? m : min(m, rest[i]);
     }
    return m;
   }

  static double max(double n, double...rest)                                    // Maximum number from a list of one or more numbers
   {double m = n;
    for (int i = 0; i < rest.length; ++i) m = m < rest[i] ? rest[i] : m;
    return m;
   }

  static double min(double n, double...rest)                                    // Minimum number from a list of one or more numbers
   {double m = n;
    for (int i = 0; i < rest.length; ++i) m = m > rest[i] ? rest[i] : m;
    return m;
   }

  static int nextPowerOfTwo(int n)                                              // If this is a power of two return it, else return the next power of two greater than this number
   {int p = 1;
    for (int i = 0; i < 32; ++i, p *= 2) if (p >= n) return p;
    stop("Cannot find next power of two for", n);
    return -1;
   }

  static int prevPowerOfTwo(int n)                                              // If this is a power of two return it, else return the previous power of two
   {int p = 1;
    if (n == 0) stop("No previous power of two for zero");
    for (int i = 0; i < 32; ++i, p *= 2) if (p*2 > n) return p;
    stop("Cannot find previous power of two for", n);
    return -1;
   }

  static int logTwo(int n)                                                      // Log 2 of containing power of 2
   {int p = 1;
    for (int i = 0; i < 32; ++i, p *= 2) if (p >= n) return i;
    stop("Cannot find log two for", n);
    return -1;
   }

  static int powerTwo(int n) {return 1 << n;}                                   // Power of 2

  static int powerOf (int a, int b)                                             // Raise a to the power b
   {int v = 1; for (int i = 0; i < b; ++i) v *= a; return v;
   }

  static int modZero(int i, int m) {return i % m  == 0 ? i : i + m - (i % m);}  // Next integer conguent to zero modulus the specified base

  static int hexToInt(String hex)                                               // Convert a hexadecimal string to int
   {return Integer.parseInt(hex.replaceAll("[^0-9A-Fa-f]", ""), 16);
   }

  static int decToInt(String dec)                                               // Convert a decimal string to int
   {return Integer.parseInt(dec.replaceAll("[^0-9]", ""));
   }

//D2 Array routines                                                             // Routines operating on arrays

   static void reverseArray(Object[] array)                                     // Reverse an array in situ
    {final int N = array.length;
     for (int i = 0; i < N / 2; i++)
      {final Object temp = array[i];
       array[i] = array[N - 1 - i];
                  array[N - 1 - i] = temp;
      }
    }

   static void randomizeArray(Object[] array)                                   // Randomize an array
    {final Random random = new Random();
     for (int i = array.length - 1; i > 0; i--)
      {int j = random.nextInt(i + 1);
       Object temp = array[i];
                     array[i] = array[j];
                                array[j] = temp;
      }
    }

//D2 Traceback                                                                  // Trace back so we know where we are

  static String fullTraceBack(Exception e)                                      // Get a full stack trace that we can use in Geany
   {final StackTraceElement[]  t = e.getStackTrace();
    final StringBuilder        b = new StringBuilder();
    if (e.getMessage() != null)b.append(e.getMessage()+'\n');

    for(StackTraceElement s : t)
     {final String f = s.getFileName();
      final String c = s.getClassName();
      final String m = s.getMethodName();
      final String l = String.format("%04d", s.getLineNumber());
      b.append("  "+f+":"+l+":"+m+'\n');
     }
    return b.toString();
   }

  static String traceBack(Exception e)                                          // Get a stack trace that we can use in Geany
   {final int Skip = 2;
    final StackTraceElement[]  t = e.getStackTrace();
    final StringBuilder        b = new StringBuilder();
    if (e.getMessage() != null)b.append(e.getMessage()+'\n');

    int skipped = 0;
    for(StackTraceElement s : t)
     {final String f = s.getFileName();
      final String c = s.getClassName();
      final String m = s.getMethodName();
      final String l = String.format("%04d", s.getLineNumber());
      if (f.equals("Main.java") || f.equals("Method.java") || f.equals("DirectMethodHandleAccessor.java")) {}
      else if (skipped < Skip) ++skipped;
      else b.append("  "+f+":"+l+":"+m+'\n');
     }
    return b.toString();
   }

  static String traceBack()    {return traceBack(new Exception());}             // Get a stack trace that we can use in Geany

  static String traceDdd()                                                      // Locate line associated with a say statement
   {final StackTraceElement[]  t = new Exception().getStackTrace();
    for(int i = 0; i < t.length; ++i)
     {if (t[i].getMethodName().equals("ddd"))
       {final StackTraceElement s = t[i+1];
        final String f = s.getFileName();
        final String m = s.getMethodName();
        final String l = String.format("%04d", s.getLineNumber());
        return f+":"+l+":";
       }
     }
    return "";
   }

  static String currentTestName()                                               // Name of the current test
   {final StackTraceElement[] T = Thread.currentThread().getStackTrace();       // Current stack trace
    for (StackTraceElement t : T)                                               // Locate deepest method that starts with test
     {final String c = t.getMethodName();
      if (c.matches("\\Atest_.*\\Z")) return c;
     }
    return null;                                                                // Not called in a test
   }

  static int  currentTestNumber = 0;
  static long currentTestTime = System.nanoTime();

  static double elapsedTime()                                                   // Elapsed time since last call or start of run
   {final long    e = System.nanoTime();
    final double  d = (e - currentTestTime) / 1_000_000_000.0;
    currentTestTime = e;
    return d;
   }

  static void sayCurrentTestName()                                              // Name of the current test
   {say(String.format("%2d %8.2f", ++currentTestNumber, elapsedTime()),
      currentTestName());
   }

  static String testLine()                                                      // Locate line associated with the current test
   {final StackTraceElement[] t = new Exception().getStackTrace();
    final String T = currentTestName();                                         // Current test name
    for(int i = 0; i < t.length; ++i)
     {final StackTraceElement s = t[i];
      if (s.getMethodName().equals(T))
       {final String f = s.getFileName();
        final String m = s.getMethodName();
        final String l = String.format("%04d", s.getLineNumber());
        return f+":"+l+":";
       }
     }
    return null;
   }

  static String currentTestNameSuffix()                                         // Name of the current test
   {final String t = currentTestName();
    if (t == null) stop("Not in a test");
    final String[]s = t.split("_", 2);
    if (s.length < 2) stop("Not in a test_name");
    testsExecuted.add(s[1]);
    return s[1];
   }

  static String currentCallerName()                                             // Looks for the first method written in camel case
   {final StackTraceElement[] T = Thread.currentThread().getStackTrace();       // Current stack trace
    for (StackTraceElement t : T)                                               // Locate deepest method with a name written in camel case
     {final String c = t.getMethodName();
      if (c.matches("\\A.*_.*\\Z")) return c;
     }
    return null;                                                                // No method written in camel case
   }

  static String sourceFileName()                                                // Name of source file calling this method
   {final StackTraceElement e = Thread.currentThread().getStackTrace()[2];      // 0 is getStackTrace, 1 is this routine, 2 is calling method
    return e.getFileName();
   }

  static String callerFileAndLine2()                                            // Locate file and line number of caller of caller
   {final StackTraceElement[] t = new Exception().getStackTrace();
    if (t.length < 3) return null;
    final StackTraceElement s = t[2];
    final String f = s.getFileName();
    final String m = s.getMethodName();
    final String l = String.format("%04d", s.getLineNumber());
    return f+" "+m+" "+l;
   }

  static String traceComment()                                                  // Trace back comment
   {final String t = traceBack();
    return " /* "+t.replaceAll("\\n", " ")+" */";                               // Finish a statement and show where it came from
   }

//D2 Coverage                                                                   // Analyze code coverage

  static final TreeMap<String, Integer> coverage = new TreeMap<>();             // Count of how many times each line has been executed

  static void z()                                                               // A line that is being executed
   {final String s = callerFileAndLine2();                                      // File method line
    Integer c = coverage.get(s);
    coverage.put(s, c == null ? 1 : c+1);
   }

  static void zz()                                                              // A subroutine that is being executed
   {final String s = callerFileAndLine2();                                      // File method line
    Integer c = coverage.get(s);
    coverage.put(s, c == null ? 1 : c+1);
   }

  static class LineCount                                                        // Line count
   {final String line;
    final int count;
    LineCount(String Line, int Count) {line = Line; count = Count;}
   }

  static void printMostExecuted(Stack<LineCount> stack, String line, int n)     // Print a most frequently executed subroutine as in: 9843 Btree.java leafSize 0124
   {final String[]s = line.split("\\s");
    stack.push(new LineCount(String.format("%s:%s:%s", s[0], s[2], s[1]), n));
   }

  final static String coverageAnalysisBlock = "z();";                           // A string indicating the start of a block
  static void coverageAnalysis(String source, int top)                          // Coverage analysis: unexecuted lines and lines most frequently executed in the specified file in a Geany clickable format.
   {final Stack<String> sourceLines = readFile(source);                         // Lines of source from indicated file
    final Stack<String> notExecuted = new Stack<>();                            // Lines not executed
    final TreeSet<Integer> executed = new TreeSet<>();                          // Lines executed

    for (String s : coverage.keySet())                                          // Find lines that executed the line executed indicator function
     {final String[]fml = s.split("\\s+");
      if (fml[0].equals(source)) executed.add(Integer.parseInt(fml[2]));
     }

    for (int i = 1; i <= sourceLines.size(); i++)                               // Lines not executed in this file
     {final String line = sourceLines.elementAt(i-1);
      if (line.contains(coverageAnalysisBlock))                                 // Line has been marked as executable
       {if (!executed.contains(i))                                              // Line has not been executed
         {notExecuted.push(""+source+":"+i+":");
         }
       }
     }
    if (notExecuted.size() > 0)                                                 // Lines not executed
     {say("Not executed");
      for (int i = 1; i <= notExecuted.size(); i++)                             // Not executed lines as a table
       {say(notExecuted.elementAt(i-1));
       }
     }
    else say("All lines executed");                                             // All lines were executed

    if (top > 0)                                                                // Most frequently executed
     {final Stack<LineCount> lc = new Stack<>();                                // Lines executed most frequently
      coverage.entrySet().stream()                                              // Find most frequently executed lines
       .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))              // Sort by value in descending order
       .limit(top)                                                              // Take the most frequent elements
       .forEach(e -> printMostExecuted(lc, e.getKey(), e.getValue()));          // Print each entry

      int w = 1; for (LineCount l: lc) w = max(w, l.line.length());             // Maximum width of line executed specification
      final String f = "%-" + w + "s";
      say(String.format(f+"  %12s  %4s", "Most Executed", "Count", "#"));

      final int N = min(w, lc.size());
      for (int i = 1; i <= N; i++)                                              // Print lines executed most frequently
       {final LineCount l = lc.elementAt(i-1);
        final String    c = NumberFormat.getInstance().format(l.count);
        say(String.format(f+"  %12s  %4d", l.line, c, i));
       }
     }
   }

  static boolean coverageExecuted(String file, Integer line,                    // Coverage analysis: check that a line was executed
    TreeMap<String,TreeMap<Integer,Integer>> executed)
   {if (!executed.containsKey(file)) return false;                              // Nothing in this file was ever executed
    final TreeMap<Integer,Integer> e = executed.get(file);
    return e.containsKey(line);                                                 // Whether this line in this file was executed
   }

// Uncomment zz for methods not called analysis
// Uncomment z  for blocks not called analysis
  final static String coverageAnalysisSubStart = "zz();";                       // A string indicating the start of a subroutine - method entries only
//final static String coverageAnalysisSubStart = "z();";                        // Any labelled statement

  static void coverageAnalysis(int top, String...Ignore)                        // Coverage analysis: unexecuted lines and top lines most frequently executed over all files encountered in a Geany clickable format.
   {final TreeMap<String,TreeSet<Integer>> notExecuted      = new TreeMap<>();  // File, lines not executed
    final TreeMap<String,TreeMap<Integer,Integer>> executed = new TreeMap<>();  // Lines executed
    final TreeSet<String> ignore                            = new TreeSet<>();
    for (String s : Ignore) ignore.add(s);                                      // Files to be ignored

    for (String s : coverage.keySet())                                          // Find lines that executed the line executed indicator function
     {final String[]fml = s.split("\\s+");
      final String f = fml[0];                                                  // The file containing the line executed
      final String m = fml[1];                                                  // Method name
      final int    l = Integer.parseInt(fml[2]);                                // Line number
      if (!executed.containsKey(f)) executed.put(f, new TreeMap<Integer,Integer>());
      final TreeMap<Integer,Integer> e = executed.get(f);
      if (e.containsKey(l)) e.put(l, e.get(l)+1); else e.put(l, 1);             // Add or update the number of times this line was executed
     }

    for (String source: executed.keySet())                                      // The files encountered
     {if (ignore.contains(source)) continue;                                    // Ignore specified files
      final Stack<String> sourceLines = readFile(source);                       // Lines of source code from a file encountered
      for (int l = 1; l <= sourceLines.size(); l++)                             // Lines not executed in this file
       {final String line = sourceLines.elementAt(l-1);
        final String search = coverageAnalysisSubStart;
        if (line.contains(search) && !coverageExecuted(source, l, executed))    // Line has been marked as executable but was not executed
         {final TreeSet<Integer> c = notExecuted.containsKey(source) ?
            notExecuted.get(source) : new TreeSet<>();
          c.add(l);
          notExecuted.put(source, c);
         }
       }
     }

    if (notExecuted.size() > 0)                                                 // Lines not executed
     {say("Not executed");
      for (String f: notExecuted.keySet())                                      // The files containing lines that have not been executed
       {for (Integer i : notExecuted.get(f)) say(f+":"+i+":");                  // Not executed lines
       }
     }
    else say("All methods executed");                                           // All lines were executed

    if (top > 0)                                                                // Most frequently executed
     {final Stack<LineCount> lc = new Stack<>();                                // Lines executed most frequently
      coverage.entrySet().stream()                                              // Find most frequently executed lines
       .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))              // Sort by value in descending order
       .limit(top)                                                              // Take the most frequent elements
       .forEach(e -> printMostExecuted(lc, e.getKey(), e.getValue()));          // Print each entry

      int w = 1; for (LineCount l: lc) w = max(w, l.line.length());             // Maximum width of line executed specification
      final String f = "%-" + w + "s";
      say(String.format(f+"  %12s  %4s", "Most Executed", "Count", "#"));

      final int N = min(w, lc.size());
      for (int i = 1; i <= N; i++)                                              // Print lines executed most frequently
       {final LineCount l = lc.elementAt(i-1);
        final String    c = NumberFormat.getInstance().format(l.count);
        say(String.format(f+"  %12s  %4d", l.line, c, i));
       }
     }
   }

//D2 Files                                                                      // Operations on files

  static Long fileSize(String file)                                             // Size of a file
   {final Path path = Path.of(file);
    try
     {return Files.size(path);
     }
    catch (IOException e)
     {return null;
     }
   }

  static Long fileCompare(String a, String b)                                   // Compare two files
   {final Path A = Path.of(a);
    final Path B = Path.of(b);
    try
     {return Files.mismatch(A, B);
     }
    catch (IOException e)
     {return null;
     }
   }

  class FileCompareAndLocate                                                    // Compare two files A, B line by line and return the last instance of a line starting with last before their point of divergence or null if file B is a prefix of file A
   {final String last = "Location: ";                                           // Locator string identifying the nest section of the log
    final String fileA, fileB;
    int    line       = 0;                                                      // Line in the log at which the first failure as detected
    boolean matches   = false;                                                  // Whether the two files match or not
    String location   = null;                                                   // Last location record prior to point of failure
    final int N = 10;                                                           // Number of lines before and after error point
    final Deque<Match> q = new ArrayDeque<>(2*N);                               // The last so many lines before and after the point of failure

    class Match                                                                 // Record pairs of lines from the Java trace and the Verilog trace
     {final String  a, b;
      final int     line;
      final boolean ahead;
      Match(int Line, String A, String B)
       {line = Line; a = A; b = B; ahead = false;
       }
      Match(int Line, String A, String B, boolean Ahead)
       {line = Line; a = A; b = B; ahead = Ahead;
       }
     }


    FileCompareAndLocate(String a, String b)                                    // Compare two files A, B line by line and return the last instance of a line starting with last before their point of divergence or null if file B is a prefix of file A
     {fileA = a; fileB = b;
      final Path A = Path.of(a);
      final Path B = Path.of(b);

      try
       (final Stream<String> sa = Files.lines(A);
        final Stream<String> sb = Files.lines(B)
       )
       {final Iterator<String> ia = sa.iterator();
        final Iterator<String> ib = sb.iterator();
        while (ia.hasNext() && ib.hasNext())                                    // Read files line by line
         {final String aa = ia.next();
          final String bb = ib.next();
          ++line;
          if (q.size() >= N) q.removeFirst();
          q.addLast(new Match(line, aa, bb));

          if (aa.startsWith(last))                                              // Found a location record
           {location = aa.substring(last.length()).replaceAll("\\|", "\n");
           }
          else if (!aa.equals(bb))                                              // Found a differing line which is not a location line
           {for (int i = 1; i <= N && ia.hasNext() && ib.hasNext(); ++i)
             {q.addLast(new Match(line+i, ia.next(), ib.next(), true));
             }
            return;
           }
         }
        if (ib.hasNext())                                                       // File A has terminated earlier than B so no match
         {return;
         }
        matches = true;                                                         // Files are identical
       }
      catch (IOException e)
       {stop(e);
       }
     }
   }

  static Stack<String> readFile(String filePath)                                // Read a file into stack of strings
   {try
     {final Stack<String> S = new Stack<>();
      for(String s:  Files.readAllLines(Paths.get(filePath))) S.push(s);
      return S;
     }
    catch (Exception e)
     {stop("Cannot read file", filePath, e);
     }
    return null;
   }

  static String readFileAsString(String filePath)                               // Read a file into a string
   {final Stack<String> s = readFile(filePath);
    return joinLines(s);
   }

  static String tempFile()                                                      // Create a temporary file
   {try  {return ""+File.createTempFile("zzz", "111");}
    catch(Exception e) {stop(e);}
    return null;
   }

  static void appendFile(String filePath, StringBuilder string)                 // Append a string builder to a file
   {try
     {makePath(folderName(filePath));
      Files.write(Paths.get(filePath), string.toString().getBytes(),
        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      filesWritten.add(filePath);
     }
    catch (Exception e)
     {stop("Cannot append to file", filePath, e);
     }
   }

  static void appendFile(String filePath, String string)                        // Append a string to a file
   {appendFile(filePath, new StringBuilder(string));
   }

  static void writeFile(String filePath, StringBuilder string)                  // Write a string builder to a file
   {try
     {makePath(folderName(filePath));
      Files.write(Paths.get(filePath), string.toString().getBytes());
      filesWritten.add(filePath);
     }
    catch (Exception e)
     {stop("Cannot write file", filePath, e);
     }
   }

  static void writeFile(String filePath, String string)                         // Write a string to a file
   {writeFile(filePath, new StringBuilder(string));
   }

  static void deleteFile(String filePath, boolean required)                     // Delete a file
   {try
     {Files.delete(Paths.get(filePath));
     }
    catch (Exception e)
     {if (required) stop("Cannot delete file", filePath, e);
     }
   }
  static void deleteFile(String filePath) {deleteFile(filePath, false);}

  static void makePath(String folder)                                           // Make a path
   {if (folder == null) return;
    try
     {Files.createDirectories(Paths.get(folder));
     }
    catch (Exception e)
     {stop("Cannot make path", folder, e);
     }
   }

  static Stack<Path> findFiles(String filePath)                                 // Find all files in and below a folder
   {final Stack<Path> files = new Stack<>();
    try
     {final Path dir = Paths.get(filePath);
      Files.walk(dir).filter(Files::isRegularFile).forEach(files::push);
      final List<Path> f = new ArrayList<>(files);
      Collections.sort(f);
      files.clear();
      files.addAll(f);
     }
    catch (Exception e) {}
    return files;
   }

  static void deleteAllFiles(String filePath, int limit)                        // Delete files and folders in the specified folder and its sub folders if the number of such files is less than the limit specified
   {final Path dir = Paths.get(filePath);                                       // Specify the directory path
    final int[]limits = {limit};
    final int N = findFiles(filePath).size();
    if (N > limit)                                                              // Check that the request would not result in the deletion of an unexpectedly large number of files
     {stop("Delete request would delete "+N+
       " files, which is more than the specified limit of: "+limit+
       " files under folder:\n"+filePath);

     }
    try
     {Files.walkFileTree(dir, new SimpleFileVisitor<Path>()
       {public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
         {Files.delete(file);
          return FileVisitResult.CONTINUE;
         }
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException
         {Files.delete(dir);                                                    // Delete the folder
          return FileVisitResult.CONTINUE;
         }
       });
     }
    catch (NoSuchFileException e) {}                                            // No problem
    catch (Exception e)
     {stop("Cannot find files under", filePath, e);
     }
   }

  static boolean fileExists(String filePath)                                    // Check whether a file exists
   {final Path p = Paths.get(filePath);
    return Files.exists(p) && Files.isRegularFile(p);
   }

  static boolean folderExists(String folder)                                    // Check whether a folder exists
   {final Path p = Paths.get(folder);
    return Files.exists(p) && Files.isDirectory(p);
   }

  static String fileName(String filePath)                                       // Get the file name from a file path name
   {return Paths.get(filePath).getFileName().toString();
   }

  static String folderName(String filePath)                                     // Get the folder name from a file path name
   {final Path p = Paths.get(filePath).getParent();
    if (p == null) return null;
    return p.toString() + "/";
   }

  static String fileExt(String filePath)                                        // Get the extension name from a file path name
   {final int p = filePath.lastIndexOf(".");
    return p > 0 && p < filePath.length() - 1 ?
      filePath.substring(p + 1) : null;
   }

  static String fne(String...Names)                                             // Join file name components
   {final StringBuilder f = new StringBuilder();
    final int N = Names.length;
    for (int i = 0; i < N-2; i++)
     {f.append(Names[i]) ;
      while(f.length() > 0 && f.charAt(f.length()-1) == '/')
       {f.setLength(f.length()-1);
       }
      f.append("/");
     }
    return ""+f+Names[N-2]+"."+Names[N-1];
   }

  static String fn(String...Names)                                              // Join file name components
   {final StringBuilder f = new StringBuilder();
    final int N = Names.length;
    for (int i = 0; i < N-1; i++)
     {f.append(Names[i]) ;
      while(f.length() > 0 && f.charAt(f.length()-1) == '/')
       {f.setLength(f.length()-1);
       }
      f.append("/");
     }
    return ""+f+Names[N-1];
   }

  class CompressFile
   {final  String sourceFile;
    final  String compressedFile;
           String message;
    int           read = 0;
    CompressFile(String SourceFile, String CompressedFile)
     {final byte[] buffer = new byte[4096 * 4096];
      sourceFile     = SourceFile;
      compressedFile = CompressedFile;

      try
       (final FileInputStream  fis    = new FileInputStream(sourceFile);
        final FileOutputStream fos    = new FileOutputStream(compressedFile);
        final GZIPOutputStream gzipOS = new GZIPOutputStream(fos)
       )
       {int len;
        while ((len = fis.read(buffer)) != -1)
         {gzipOS.write(buffer, 0, len);
          read += len;
         }
        message = "Compressed "+read+" bytes from "+sourceFile+" to "+compressedFile;
       }
      catch (IOException e)
       {e.printStackTrace();
        stop("Unable to comress file:", sourceFile, "to", compressedFile);
       }
     }
   }

//D2 Timing                                                                     // Print log messages

  static class Timer                                                            // Time a section of code
   {final long start = System.nanoTime();
    public String toString()
     {return String.format("%6.2f %s", seconds(), currentCallerName());
     }
    double seconds()
     {final long duration = System.nanoTime() - start;
      return duration / 1e9;
     }
   }

  static Timer timer() {return new Timer();}                                    // Create a new timer

//D2 Printing                                                                   // Print log messages

  static void sayf(String format, Object...O)                                   // Say something under the control of a format string
   {System.err.println(String.format(format, O));
    return;
   }

  static StringBuilder saySb(Object...O)                                        // Say something into a string builder
   {final StringBuilder b = new StringBuilder();                                // Print as a series of whitespace separated items
    for (int i = 0; i <  O.length; ++i)
     {final Object o = O[i];
      if (o == null) {b.append("(null)"); continue;}
      final String s = o.toString();

      if (b.length() > 0 && s.length() > 0 && s.charAt(s.length()-1) == '\n')   // Print a string that has a new line at the end indicating it is vertically aligned
       {b.append("\n"+s);
       }

      else if (b.length() > 0 && s.length() > 0           &&                    // Offset the next item from the previous item with a space unless a space has been provided
        !Character.isWhitespace(b.charAt(b.length() - 1)) &&
        !Character.isWhitespace(s.charAt(0))) b.append(" "+s);
      else b.append(s);
     }
    return  b;
   }

  static void say(Object...O)                                                   // Say something
   {final StringBuilder b = saySb(O);                                           // Print as a series of whitespace separated items

    if (sayThisOrStop.size() > 0)                                               // Convert the say into a stop if the expected message does not eventuate
     {final String act = b.toString() .replace("\n", "\\n").trim();             // Message we actually got
      final String exp = sayThisOrStop.removeFirst().replace("\n","\\n").trim();// Message we expected
      if (!act.startsWith(exp))                                                 // Expected message does not match what we have got
       {stop("Actual message does not equal expected message:\n"+
          "Actual  :"+act+" length("+act.length()+")\n"+
          "Expected:"+exp+" length("+exp.length()+")\n");
       }
     }

    else if (b.length() > 0) System.err.println(b.toString());
   }

  static StringBuilder say(StringBuilder b, Object...O)                         // Say something in a string builder
   {for (int i = 0; i < O.length; i++)
     {if (i > 0) b.append(" ");
      b.append(O[i]);
     }
    b.append('\n');
    return b;
   }

  static void ddd(Object...O)                                                   // Debug something
   {final int W = 10;                                                           // Width of traceback
    if (O.length == 0)
     {System.err.println(traceDdd());                                           // Nothing to say
      return;
     }

    final StringBuilder b = new StringBuilder();                                // Concatenate objects as strings
    for (int i = 0; i < O.length; i++)
     {final Object o = O[i];
      if (i > 0) b.append(' ');
      b.append(o);
     }
    while (b.length() > 0)                                                      // Remove trailing white space
     {final int  l = b.length();
      final char c = b.charAt(l-1);
      if (!Character.isWhitespace(c)) break;
      b.setLength(l - 1);
     }

    StringBuilder p = new StringBuilder(traceDdd());                            // Create traceback line prefix
    p.append(" ".repeat(W - p.length() % W));
    final int     w = p.length();
    final String[]s = b.toString().split("\n");                                 // Print first line with line position and message in a format understood by Geany with a re of: ([a-zA-Z0-9./]):(\d+)
    System.err.println(p.toString()+" "+s[0]);

    for (int i=1; i < s.length; i++) System.err.println(" ".repeat(w)+" "+s[i]);// Any following lines are indented to match the first line
   }

  static void err(Object...O)                                                   // Say something and provide an error trace.
   {final boolean testing = sayThisOrStop.size() > 0;                           // We are testing something that would normally stop the system
    say(O);
    if (!testing) System.err.println(traceBack());
   }

  static void errTest(Object...O)                                               // Say something about the current test
   {say(O);
    final String t = testLine();
    if (t != null) System.err.println(t);
   }

  static void stop(Object...O)                                                  // Say something, provide an error trace and stop
   {final boolean sos = sayThisOrStop.size() > 0;                               // Say or stop checking in effect
    say(O);
    if (sos)
     {throw new RuntimeException("Stopping after say an error message");
     }
    else
     {System.err.println(traceBack());
      System.exit(1);
     }
   }

  static void sayThisOrStop(Object...O)                                         // The next things to be said
   {sayThisOrStop.clear();
    for (Object o : O) sayThisOrStop.push(o.toString());
   }

  private static void ii(Object s, Object[] a)
   {final Object[] b = new Object[a.length + 1];
    b[0] = s;
    System.arraycopy(a, 0, b, 1, a.length);
    say(b);
   }

  static void squeezeVerticalSpaces(Stack<StringBuilder>S)                      // Squeeze common vertical spaces out of a stack of strong builders
   {final int collapse = 3;
    int m = 0; for(StringBuilder s: S) if (s.length() > m) m = s.length();      // Maximum length

    for(int j = 0; j < S.size(); j++)                                           // Pad each row to the maximum length
     {S.elementAt(j).append(" ".repeat(m - S.elementAt(j).length()));
     }

    columns: for(int i = m; i >= collapse; i--)                                 // Each column working backwards through each string
     {for(StringBuilder s: S)                                                   // Check there are two spaces that can be squeezed to one in all rows in this column
       {if (!s.substring(i-collapse, i).equals(" ".repeat(collapse))) continue columns;
       }

      for(int j = 0; j < S.size(); j++)                                         // Squeeze common spaces in column
       {final String s = S.elementAt(j).toString();
        S.setElementAt(new StringBuilder(s.substring(0, i-collapse)+s.substring(i-collapse+1)), j);
       }
     }

    for(int j = 0; j < S.size(); j++)                                           // Remove trailing padding
     {final StringBuilder s = S.elementAt(j);
      for(;s.length() > 0 && s.charAt(s.length()-1) == ' ';)
       {s.setLength(s.length()-1);
       }
     }
   }

//D1 Testing                                                                    // Test expected output against got output

  static int testsPassed = 0, testsFailed = 0;                                  // Number of tests passed and failed

  static boolean ok(boolean b)                                                  // Check test results match expected results.
   {if (b) {++testsPassed; return true;}
    testsFailed++;
    err(currentTestName(), "failed\n");
    return false;
   }

  static boolean ok(Object a, Object b)                                         // Check test results match expected results.
   {if (a.toString().equals(b.toString())) {++testsPassed; return true;}
    final boolean n = b.toString().contains("\n");
    testsFailed++;
    if (n) err(currentTestName(), "Failed, got:\n"+a+"\n");
    else   err(a, "\ndoes not equal\n", b, "\nin", currentTestName());
    return false;
   }

  static boolean ok(String got, String expected, int Margin)                    // Confirm two strings match beyond the margin or show the first line of differences
   {final String G = got, E = expected;
    final int lg = G.length(), le = E.length();
    final StringBuilder b = new StringBuilder();

    boolean matchesLen = true, matches = true;
    if (le != lg)                                                               // Failed on length
     {matchesLen = false;
      say(b, currentTestName(), "Failed: mismatched length, expected",
        le, "got", lg);

//    for (int i = 0; i < G.length(); i++)                                      // Check each character side by side
//     {final int  g = G.charAt(i);
//      final int  e = i < E.length() ? E.charAt(i) : ' ';
//      final char c =                  G.charAt(i);
//      say(i, g, e, c);
//     }
     }

    int l = 1, c = 0;                                                           // Line and character of first failure
    final int N = min(le, lg);                                                  // Print to the end of the shortest string
    for (int i = 0; i < N && matches; i++)                                      // Check each character in the overlapping area of the got and expected strings
     {final int e = E.charAt(i), g = G.charAt(i);                               // Each character of the overlap
      if (c >= Margin && e != g)                                                // Character mismatch beyond margin between got and expected so print entire string highlighting the differences in the first line that differs
       {final String ee = e == '\n' ? "new-line" : ""+(char)e;                  // Handle new lines gracefully
        final String gg = g == '\n' ? "new-line" : ""+(char)g;
        final String ruler = "0----+----1----+----2----+----3----+----4----+----5----+----6----+----7----+----8----+----9----+----";
        final String error = "Line: "+l+" character: "+c+", expected="+ee+"= got="+gg+"=";   // Location of error

        say(b, error); //say(b, ruler);

        final String[]sg = G.split("\\n");                                      // Got as lines
        final String[]se = E.split("\\n");                                      // Expected as lines
        for(int k = 0; k < sg.length; k++)                                      // Show each line in the string
         {if (l == k+1)                                                         // Write the difference line for the first line with errors
           {final String Eg = sg[k], Ee = se[k];                                // Differing versions of first line with errors in it
            final StringBuilder dg = new StringBuilder();                       // The difference on got
            final StringBuilder de = new StringBuilder();                       // The difference on expected
            final StringBuilder dm = new StringBuilder();                       // The difference markers
            final int n = min(Eg.length(), Ee.length());                        // Overlaps between expected and got on first differing line
            if (n > 0)                                                          // Overlap exists
             {for(int j = 0; j < n; j++)                                        // Show each line in the string
               {final char cg = Eg.charAt(j), ce = Ee.charAt(j);                // Characters at current position
                dg.append(cg == ce ? ' ' : cg);                                 // Difference line for got
                de.append(cg == ce ? ' ' : ce);                                 // Difference line for expected
                dm.append(cg == ce ? ' ' : '^');                                // Difference markers
               }
              final String r = ruler.repeat(1 + n / 100);                       // A sufficiently long ruler to bracket the difference lines
              say(b, r);                                                        // Write the difference lines bracketed with rulers
              say(b, sg[k]);                                                    // What we got
              say(b, dg); say(b, de); say(b, dm);                               // Difference markers
              say(b, error); say(b, r);                                         // Error detail
             }
           }
          else say(b, sg[k]);                                                   // Line that is not the first error line
         }
        matches = false;
       }
      if (e == '\n') {++l; c = 0;} else c++;
     }

    final boolean pass = matchesLen && matches;
    if (pass) ++testsPassed; else {++testsFailed; err(b);}                      // Show error location with a trace back so we know where the failure occurred
    return pass;
   }

  static boolean ok(String got, String expected)                                // Confirm two strings match or show the first line of differences
   {return ok(got, expected, 0);
   }

  static boolean ok(int margin, String got, String expected)                    // Confirm two strings
   {final String G = differentiateLines(margin, got),
                 E = differentiateLines(margin, expected);
    return ok(G, E, margin);
   }

  static boolean ok(Integer G, Integer E)                                       // Check that two integers are equal
   {if (false)                        {                                                             return true;}
    else if ( G == null && E == null) {                                              ++testsPassed; return true;}
    else if ( G != null && E == null) {err(String.format("Expected null, got:", G)); ++testsFailed; return false;}
    else if ( G == null && E != null) {err(String.format("Got null, expected:", E)); ++testsFailed; return false;}
    else if (!G.equals(E))
     {if (theShorterIsTheDaughter)    {errTest(currentTestName(), G, "!=", E);       ++testsFailed; return false;}
      else                            {err(currentTestName(), G, "!=", E);           ++testsFailed; return false;}
     }
    else                              {                                              ++testsPassed; return true;}
   }

  static boolean ok(Long    G, Long    E)                                       // Check that two longs are equal
   {if (false)                        {return false;}
    else if ( G == null && E == null) {                                              ++testsPassed; return true;}
    else if ( G != null && E == null) {err(String.format("Expected null, got:", G)); ++testsFailed; return false;}
    else if ( G == null && E != null) {err(String.format("Got null, expected:", E)); ++testsFailed; return false;}
    else if (!G.equals(E))            {err(currentTestName(), G, "!=", E);           ++testsFailed; return false;}
    else                              {                                              ++testsPassed; return false;}
   }

  static boolean ok(Integer[]G, String E)                                       // Check that two integer arrays are are equal
   {return ok(""+G, E);
   }

  static boolean ok(Integer[]G, Integer[]E)                                     // Check that two integer arrays are are equal
   {final StringBuilder b = new StringBuilder();
    final int lg = G.length, le = E.length;

    if (le != lg)
     {err(currentTestName(), "Failed:",
       "mismatched length, got", lg, "expected", le, "got:\n"+G);
      ++testsFailed;
      return false;
     }

    int fails = 0, passes = 0;
    for (int i = 1; i <= lg; i++)
     {final Integer e = E[i-1], g = G[i-1];
      if (false)                       {}
      else if (e == null && g == null) {}
      else if (e != null && g == null) {b.append(String.format("Index %d expected %d, but got null\n", i, e   )); ++fails;}
      else if (e == null && g != null) {b.append(String.format("Index %d expected null, but got %d\n", i, g   )); ++fails;}
      else if (!e.equals(g))           {b.append(String.format("Index %d expected %d, but got %d\n",   i, e, g)); ++fails;}
      else ++passes;
     }
    if (fails > 0) err(b);
    testsPassed += passes; testsFailed += fails;                                // Passes and fails
    return fails > 0;
   }

  static void testSummary()                                                     // Print a summary of the testing
   {final double d = (System.nanoTime() - start) / (double)(1<<30);             // Run time in seconds
    final String
      a = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),      // Time at which run was executed
      C = Thread.currentThread().getStackTrace()[2].getClassName(),             // Containing class
      c = C.substring(C.lastIndexOf('.') + 1),                                  // Short name of containing class
      m = String.format("tests in %7.4f seconds using %s at %s", d, c, a);      // Format test summary
    if (false) {}                                                               // Analyze results of tests
    else if (testsPassed == 0 && testsFailed == 0) say("No",    m);             // No tests
    else if (testsFailed == 0)   say("PASSed ALL", testsPassed, m);             // Passed all tests
    else say("Passed "+testsPassed+",    FAILed:", testsFailed, m);             // Failed some tests
    System.exit(testsFailed > 0 ? 1 : 0);                                       // Set the return code
   }

//D2 Command Execution                                                          // Execute a command and return its stdout and stderr

  static String pwd() {return System.getProperty("user.dir");}                  // Current working folder

  static class ExecCommand
   {final String    command;
    final StringBuilder out = new StringBuilder();
    final StringBuilder err = new StringBuilder();
    int exitCode;

    ExecCommand(String Command)
     {command = Command;
      try
       {final ProcessBuilder b = new ProcessBuilder("bash", "-c", command);
        final Process p = b.start();

        final Thread o = new Thread(() ->
         {try (final BufferedReader reader =
             new BufferedReader(new InputStreamReader(p.getInputStream())))
           {String line;
            while ((line = reader.readLine()) != null)
             {out.append(line).append(System.lineSeparator());
             }
           }
          catch (IOException x)
           {x.printStackTrace();
           }
         }); o.start();

        final Thread e = new Thread(() ->
         {try (final BufferedReader reader =
             new BufferedReader(new InputStreamReader(p.getErrorStream())))
           {String line;
            while ((line = reader.readLine()) != null)
             {err.append(line).append(System.lineSeparator());
             }
           }
          catch (IOException x)
           {x.printStackTrace();
           }
         }); e.start();

        exitCode = p.waitFor();

        o.join();
        e.join();
        if (exitCode != 0)
         {stop(
          "Command:", command,  "\n",
          "code   :", exitCode, "\n",
          "stdout :", out,      "\n",
          "stderr :", err);
         }
       }
      catch (Exception e) {e.printStackTrace();}
     }
    ExecCommand(StringBuilder command) {this(""+command);}
   }

//D0                                                                            // Tests

  static void test_log_two()
   {ok(logTwo(0), 0);
    ok(logTwo(1), 0);
    ok(logTwo(2), 1);
    ok(logTwo(3), 2);
    ok(logTwo(4), 2);
    ok(logTwo(5), 3);
    ok(logTwo(6), 3);
    ok(logTwo(7), 3);
    ok(logTwo(8), 3);
    ok(logTwo(9), 4);
   }

  static void test_power_two()
   {ok(nextPowerOfTwo(0),  1);
    ok(nextPowerOfTwo(1),  1); ok(prevPowerOfTwo( 1), 1);
    ok(nextPowerOfTwo(2),  2); ok(prevPowerOfTwo( 2), 2);
    ok(nextPowerOfTwo(3),  4); ok(prevPowerOfTwo( 3), 2);
    ok(nextPowerOfTwo(4),  4); ok(prevPowerOfTwo( 4), 4);
    ok(nextPowerOfTwo(5),  8); ok(prevPowerOfTwo( 5), 4);
    ok(nextPowerOfTwo(6),  8); ok(prevPowerOfTwo( 6), 4);
    ok(nextPowerOfTwo(7),  8); ok(prevPowerOfTwo( 7), 4);
    ok(nextPowerOfTwo(8),  8); ok(prevPowerOfTwo( 8), 8);
    ok(nextPowerOfTwo(9), 16); ok(prevPowerOfTwo( 9), 8);
   }

  static void test_max_min()
   {ok(min(3,  2,  1),  1);
    ok(max(1,  2,  3),  3);
    ok(min(3d, 2d, 1d), 1d);
    ok(max(1d, 2d, 3d), 3d);
    ok(maxInt(null, null, 1),    1);
    ok(minInt(null, 2, null, 3), 2);
   }

  static void test_string()                                                     // Confirm an error message
   {String e = """
AAAAA
BBBBB
CCCCC
""";
    String g = """
AAAAA
BBDBB
CCCCC
""";


    sayThisOrStop("""
Character 2, expected=D= got=B=
0----+----1----+----2----+----3----+----4----+----5----+----6----+----7----+----8----+----9----+----
AAAAA
BBBBB
  ^
CCCCC
""");

    ok(e, g); --testsFailed;
   }

  static void test_longest_line()
   {ok(longestLine(""),      0);
    ok(longestLine("A"),     1);
    ok(longestLine("\n"),    1);
    ok(longestLine("\n\n"),  1);
    ok(longestLine("\nA\n"), 2);
    ok(longestLine("A\nBBB\nCC\n"), 4);
   }

  static void test_files()
   {final String p = "/tmp/z/z/", a = p+"aaa.txt", b = p+"bbb.txt";
    final StringBuilder s = new StringBuilder();
    s.append("Hello\nWorld");
    writeFile(a, s);
    writeFile(b, s);
    ok(readFile(a),  "[Hello, World]");
    final Stack<Path> f = findFiles(p);
    ok(findFiles(p), "[/tmp/z/z/aaa.txt, /tmp/z/z/bbb.txt]");
    sayThisOrStop("Delete request would delete 2 files, which is more than the specified limit of: 1");
    try {deleteAllFiles(p, 1);} catch(Exception e) {}
    ok(findFiles(p), "[/tmp/z/z/aaa.txt, /tmp/z/z/bbb.txt]");
    deleteAllFiles(p, 2);
    ok(findFiles(p), "[]");
    ok(!folderExists("/tmp/z/z/"));
    ok(fileName(a), "aaa.txt");
    ok(fileExt(a), "txt");
    ok(folderName(a), "/tmp/z/z/");
   }

  static void test_join_stack()
   {final Stack<String> s = new Stack<>();
    ok(joinStrings(s, ","), "");
    s.push("a");
    ok(joinStrings(s, ","), "a");
    s.push("b");
    ok(joinStrings(s, ","), "a,b");
   }

  static void test_join_set()
   {final TreeSet<String> s = new TreeSet<>();
    ok(joinStrings(s, ","), "");
    s.add("a");
    ok(joinStrings(s, ","), "a");
    s.add("b");
    ok(joinStrings(s, ","), "a,b");
   }

  static void test_command()
   {final ExecCommand e = new ExecCommand("echo AAAA; echo BBBB 1>&2");
    ok(e.out, """
AAAA
""");
    ok(e.err, """
BBBB
""");
    ok(e.exitCode, 0);
   }

  static void test_differentiate_lines()
   {say(differentiateLines(4, """
 1  AAAAAAAA
 2  BBBBAAAA
 3  CCCCAAAA
 4  CCCCAAAA
 5  DDDDAAAA
"""));
   }

  static void test_properties()
   {final String f = "/tmp/z.txt";
    final TreeMap<String,String> p = new TreeMap<>();
    p.put("A", "a");
    p.put("B", "b");
    p.put("C", "c");
    writeProperties(f, p);
    final Stack<String> s = readFile(f);
    final TreeMap<String,String> q = readProperties(f);
    ok(s, "[A=a, B=b, C=c]");
    ok(q, "{A=a, B=b, C=c}");
   }

  static void test_ifs()
   {ok(ifs("22"), 22);
   }

  static void test_md5()
   {ok(md5Sum("Hello World"), "b10a8db164e0754105b7a99be72e3fe5");
   }

  static void test_fileNames()
   {ok(fne("/home/phil", "a", "b", "c"), "/home/phil/a/b.c");
    ok(fn ("/home/phil", "a", "b.c"),    "/home/phil/a/b.c");
   }

  static void test_executed()
   {currentTestNameSuffix();
    ok(testsExecuted, "[executed]");
   }

  static void test_squeezeVerticalSpaces()
   {final Stack<StringBuilder>S = new Stack<>();
    S.push(new StringBuilder("a   aa           aaa"));
    S.push(new StringBuilder("bb  bbb          bbbb"));
    S.push(new StringBuilder("ccc cccc         ccccc"));
    squeezeVerticalSpaces(S);
    final String s = joinStringBuilders(S, "\n")+"\n";
    //stop(s);
    ok(s, """
a   aa    aaa
bb  bbb   bbbb
ccc cccc  ccccc
""");
   }

  static void test_replaceAll()
   {final StringBuilder s = new StringBuilder();
    s.append("""
a   aa    aaa
bb  bbb   bbbb
a   aa    aaa
""");
    replaceAll(s, "aaa", "AAA");
    ok(s, """
a   aa    AAA
bb  bbb   bbbb
a   aa    AAA
""");
   }

  static void test_modZero()
   {ok(modZero(0, 4), 0);
    ok(modZero(1, 4), 4);
    ok(modZero(2, 4), 4);
    ok(modZero(3, 4), 4);
    ok(modZero(4, 4), 4);
    ok(modZero(5, 4), 8);
   }

  static void test_bitSetToHex()
   {if (true)
     {final BitSet b = new BitSet();
      b.set(5, true);
      b.set(3, true);
      b.set(1, true);
      ok(bitSetToHex(b),       "2a");
      ok(bitSetToHex(b, 9),   "02a");
      ok(bitSetToHex(b, 12),  "02a");
      ok(bitSetToHex(b, 13), "002a");
      ok(bitSetToInt(b), 42);
     }
    if (true)
     {final BitSet b = new BitSet();
      b.set(4, true);
      b.set(3, true);
      b.set(1, true);
      ok(bitSetToHex(b), "1a");
     }
    if (true)
     {final BitSet b = intToBitSet(11);
      ok(bitSetToHex(b), "b");
     }
   }

  static void test_hextoInt()
   {ok(hexToInt("axxx"),  10);
    ok(hexToInt("x1zbx"), 27);
    ok(decToInt("1x1xx"), 11);
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_log_two();
    test_power_two();
    test_max_min();
    //test_string();
    test_longest_line();
    test_files();
    test_join_stack();
    test_join_set();
    test_command();
    //test_string();
    test_properties();
    test_ifs();
    test_md5();
    test_fileNames();
    test_executed();
    test_squeezeVerticalSpaces();
    test_modZero();
    test_bitSetToHex();
    test_hextoInt();
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
