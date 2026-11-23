```java
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.net.StandardProtocolFamily;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class UnixDomainSocketDemo
{
  public static void main(String[] args) throws Exception
  {
    Path path = Path.of("/tmp/demo.sock");
    Files.deleteIfExists(path);

    try (ServerSocketChannel server =
           ServerSocketChannel.open(StandardProtocolFamily.UNIX))
    {
      server.bind(UnixDomainSocketAddress.of(path));

      System.out.println("Server bound once");

      for (int i = 1; i <= 3; i++)
      {
        final int n = i;

        new Thread(() ->
        {
          try (SocketChannel client =
                 SocketChannel.open(StandardProtocolFamily.UNIX))
          {
            client.connect(UnixDomainSocketAddress.of(path));

            client.write(ByteBuffer.wrap(
              ("Hello from client " + n).getBytes(StandardCharsets.UTF_8)));
          }
          catch (Exception e)
          {
            e.printStackTrace();
          }
        }).start();
      }

      for (int i = 1; i <= 3; i++)
      {
        try (SocketChannel connection = server.accept())
        {
          ByteBuffer buffer = ByteBuffer.allocate(100);
          connection.read(buffer);
          buffer.flip();

          System.out.println(
            StandardCharsets.UTF_8.decode(buffer));
        }
      }
    }

    Files.deleteIfExists(path);
  }
}
```
