//----------------------------------------------------------------------------------------------------------------------
// Request remote execution
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2026
//----------------------------------------------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                                                           // Btree in a block on the surface of a silicon chip.

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class RemoteExecutionClient extends Test
 {public static void main(String[] args)
   {final Path socket = Path.of("/tmp/example.sock");

    try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX))                                       // Open the connection ONCE.
     {channel.connect(UnixDomainSocketAddress.of(socket));
                                                                                                                        // Exchange 10,000 messages over the same connection.
      for (int i = 1; i <= 10_000; i++)
       {String request = "Message " + i;

        byte[] bytes = request.getBytes(StandardCharsets.UTF_8);

        ByteBuffer frame = ByteBuffer.allocate(4 + bytes.length);                                                       // Send length prefix and message.


        frame.putInt(bytes.length);
        frame.put(bytes);
        frame.flip();

        while (frame.hasRemaining()) channel.write(frame);

        ByteBuffer header = ByteBuffer.allocate(4);                                                                     // Read response length.

        if (!readFully(channel, header)) throw new IOException("Server disconnected");

        header.flip();

        int length = header.getInt();

        ByteBuffer body = ByteBuffer.allocate(length);                                                                  // Read response body.

        if (!readFully(channel, body)) throw new IOException("Incomplete response");

        String response = new String( body.array(), StandardCharsets.UTF_8);

        if (!response.equals("ACK: " + request)) throw new IOException("Unexpected response");

        if (i % 1000 == 0) say("Completed " + i + " messages");
       }
     }
    catch(IOException E) {say("AAAA",E);}
    say("Client finished");
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
