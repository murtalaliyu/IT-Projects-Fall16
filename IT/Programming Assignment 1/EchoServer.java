import java.net.Socket;
import java.net.ServerSocket;
import java.io.*;
import java.net.InetAddress;

public class EchoServer {

    public static void main(String[] args) throws Exception {

        //string the server will receive from its client
        String input = "", message = "";
        InetAddress ip;

        //Return error message and exit if port number isn't entered into terminal upon compilation
        if (args.length != 1) {
            System.err.println("Usage: java EchoServer <port number>");
            return;
        }

        //get port number from command line argument
        int portNumber = Integer.parseInt(args[0]);

        // create socket
        try (ServerSocket serverSocket = new ServerSocket(portNumber)) {

            // repeatedly wait for connections, and process
            while (true) {

                //test
                System.out.println("Waiting for client on port " + serverSocket.getLocalPort() + " ...");

                // a "blocking" call which waits until a connection is requested
                Socket clientSocket = serverSocket.accept();

                //test
                System.out.println("Connected to " + clientSocket.getRemoteSocketAddress());

                // open up IO streams
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                //get server ip
                ip = InetAddress.getLocalHost();
                //System.out.println("Current IP address: " + ip.getHostAddress());

                // waits for data and reads it in until connection dies
                while ((input = in.readLine()) != null) {

                    //close client-server connecting when either # or $ is entered
                    if (input.equals("$") || input.equals("#")) {
                        in.close();
                        out.close();
                        clientSocket.close();
                        serverSocket.close();
                        return;
                    }

                    //print to client
                    out.println("Reverse: " + reverse(input));

                    //print to server console
                    System.out.println("Input: " + input + "\nOutput: " + reverse(input));
                }

                // close IO streams, then socket
                in.close();
                out.close();
                clientSocket.close();
            }
        } catch (IOException e) {

            //return error when something goes wrong when server is listening on specified port
            System.out.println("Client disconnected on port " + portNumber);
            System.out.println(e.getMessage());
        }
    }

    //implement string reversal method
    public static String reverse(String clientMessage) {
        String message = "";

        //reverse clientMessage and store in message string, then return
        for (int i = clientMessage.length()-1; i >= 0; i--) {
            message += clientMessage.charAt(i);
        }

        return message;
    }
}