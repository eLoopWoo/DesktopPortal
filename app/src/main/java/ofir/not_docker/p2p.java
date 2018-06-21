package ofir.not_docker;

import android.content.ClipboardManager;
import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.Buffer;

/**
 * Created by ofir1 on 6/21/2018.
 */

public class p2p {
    public void listenSocket(String ip, String port){
//Create socket connection


        try{
            Socket socket = new Socket(ip, Integer.parseInt(port));
            //set up driver
            PrintWriter out = new PrintWriter(socket.getOutputStream(),
                    true);
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    socket.getInputStream()));

            //send data

            out.println("hello");
            try{
                String line = in.readLine();
                System.out.println("Text received: " + line);
            } catch (IOException e){
                System.out.println("Read failed");
                //System.exit(1);
            }
        } catch (UnknownHostException e) {
            System.out.println("Unknown host: " + ip);
            //System.exit(1);
        } catch  (IOException e) {
            System.out.println("No I/O");
            //System.exit(1);
        }
    }


    public static void connect(String ip, String port){

    }

}
