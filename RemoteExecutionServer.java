//----------------------------------------------------------------------------------------------------------------------
// Execute a class remotely
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class RemoteExecutionServer extends Test
 {public static void main(String[] args)
   {final Path socket = Path.of("/tmp/example.sock");

    try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX))
     {Files.deleteIfExists(socket);
      server.bind(UnixDomainSocketAddress.of(socket));

      say("Server listening");

      try (SocketChannel client = server.accept())                                                                      // Accept one persistent client connection.
       {say("Client connected");

        while (true)                                                                                                    // Read one complete message.
         {final ByteBuffer buffer = ByteBuffer.allocate(4);

          if (!readFully(client, buffer)) break;

          buffer.flip();
          int length = buffer.getInt();

          ByteBuffer body = ByteBuffer.allocate(length);

          if (!readFully(client, body)) throw new IOException("Incomplete message");

          String request = new String(body.array(), StandardCharsets.UTF_8);

          String response = "ACK: " + request;                                                                          // Process the request.

          final byte[] bytes = response.getBytes(StandardCharsets.UTF_8);                                               // Send the response.

          final ByteBuffer reply = ByteBuffer.allocate(4 + bytes.length);

          reply.putInt(bytes.length);
          reply.put(bytes);
          reply.flip();

          while (reply.hasRemaining()) client.write(reply);
         }
        say("Client disconnected");
       }
      catch(IOException E) {say("AAAA",E);}
     }
    catch(IOException E) {say("AAAA",E);}
   }

  private static boolean readFully(SocketChannel channel, ByteBuffer buffer) throws IOException
   {while (buffer.hasRemaining())
     {int n = channel.read(buffer);

      if (n < 0)
       {if (buffer.position() == 0) return false;

        throw new IOException("Incomplete frame");
       }
     }
    return true;
   }
 }
