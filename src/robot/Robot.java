package robot;

import java.net.*;
import java.io.*;
import java.util.Calendar;

public class Robot {
    public static void main(String[] args) throws IOException {
        switch (args.length) {
            case 1:
                System.out.println("Starting server...\n");
                Server.svr_main(Integer.parseInt(args[0]));
                break;
            case 2:
                System.out.println("Starting client...\n");
//                Client.cli_main(Integer.parseInt(args[1]), args[0]);
                break;
            default:
                System.err.println("Client: java robot.Robot <hostname> <port>");
                System.err.println("Server: java robot.Robot <port>");
                System.exit(1);
                break;
        }
    }
}
        
class Server {
    public static void svr_main(int port) throws IOException {    
        Socket client = null;
        ServerSocket server = null;
        try {
            server = new ServerSocket(port);
        }
        catch (IOException e) {
            System.err.println("Could not listen on port: " + port);
            System.exit(1);
        }
        while(true){
            try {
                client = server.accept();
            } 
            catch (IOException e) {
                System.err.println("Accept failed.");
                System.exit(1);
            }
            System.out.println("Client accepted " + client.getInetAddress() + ":" + client.getPort());
            (new Thread(new Connection(client))).start();
        }
    }
}
class Connection implements Runnable{
    private final Socket client;
    private BufferedInputStream in = null;
    private PrintWriter out = null;
    Calendar timeEnd;
    //FileWriter fileWriter; 
    
    Connection(Socket client) {
        timeEnd = Calendar.getInstance(); // gets a calendar using the default time zone and locale.
        timeEnd.add(Calendar.SECOND, 45);
        this.client = client;
        try {
            in = new BufferedInputStream(client.getInputStream());
            out = new PrintWriter(client.getOutputStream(), true);
        } 
        catch (IOException e) {
            System.err.println("Couldn't get I/O for " + client.getInetAddress());
        }
//        try{
//            fileWriter = new FileWriter("log.txt",true);
//        }
//        catch (IOException e) {
//            System.err.println("Couldn't create a writer to a file <log.txt> ");
//        }
    }
    @Override
    public void run() {        
        String msg;
        int userInDecimal;
        int passInDecimal;
        
        out.printf("200 LOGIN\r\n");
        userInDecimal = ReadLogin(in);
        out.printf("201 PASSWORD\r\n");
        passInDecimal = ReadPassword(in);
        if(userInDecimal != passInDecimal){
            out.printf("500 LOGIN FAILED\r\n");
            EndConnection();
            return;
        }
        out.printf("202 OK\r\n");
        do {
            msg = ReadMsg(in);   
            if("timeout".equals(msg)){
                out.printf("502 TIMEOUT\r\n");
                break;
            }
            if("syntax".equals(msg)){
                out.printf("501 SYNTAX ERROR\r\n");
                break;
            }
            if("sum".equals(msg))
                out.printf("300 BAD CHECKSUM\r\n");
            else
                out.printf("202 OK\r\n");
        }while (client.isConnected());
        EndConnection();
    }
    int ReadLogin(BufferedInputStream in){
        int toSend = 0;
        int i;
        boolean end = false;
        String robot = "";
        
        for(int j = 0; j < 5;++j){
            if(robot.endsWith("\r\n"))
                return -1;
            try{
                i = in.read();
                if(i == -1)
                    return -1;
                robot += (char)i;
                toSend += i;
            }
            catch(IOException e ){
                return -1;
            }
        }
        if(robot.equals("Robot") == false)
            toSend = -500;
        while(true){
            try{
                i = in.read();
                if(i == -1)
                    return -1;
                if(end && i == 10)
                    break;
                end = (i == 13);
                toSend += i;
            }
            catch(IOException e ){
                return -1;
            }
        }
        toSend -= 13;
        return toSend;
    }
    int ReadPassword(BufferedInputStream in){
        int toSend = 0;
        int i;
        
        while(true){
            try{
                i = in.read();
                if(i == -1)
                    return -2;
                if(i == 13){
                    i = in.read();
                    if(i == 10)
                        break;
                    else
                        return -2;
                }
                int digitIn = Character.digit((char)i, 10);
                if(digitIn == -1)
                    return -2;
                toSend *= 10;
                toSend += digitIn;
                    
            }
            catch(IOException e ){
                return -1;
            }
        }
        return toSend;
    }
    String ReadFoto(BufferedInputStream in){
        String lengthFoto = "";
        int sum = 0;
        int input;
        
        while(true){
            if(TimeOut())
                return "timeout";
            try{
                input = in.read();
                if(input == -1)
                    return "syntax";
                if(input > 47  &&  input < 58)
                    lengthFoto += (char)input;
                else if (input == 32)
                    break;
                else
                    return "syntax";
            }
            catch(IOException e ){
                return "syntax";
            }
        }
        int lenFoto = Integer.parseInt(lengthFoto);
        for(int i = 0; i < lenFoto; ++i){
            if(TimeOut())
                return "timeout";
            try{
                input = in.read();
                if(input == -1)
                    return "syntax";
                sum += input;
            }
            catch(IOException e ){
                return "syntax";
            }
        }
        
        String s;
        String checkSum = "";
        for(int i = 0; i < 4; i++){
            if(TimeOut())
                return "timeout";
            try{            
                input = in.read();
                if(input == -1)
                    return "syntax";
                s = Integer.toHexString(input);
                if(s.length() < 2)
                    s = "0" + Integer.toHexString(input);
                checkSum += s;
            }
            catch(IOException e ){
                return "syntax";
            }
        }
        if(sum != Long.parseLong(checkSum, 16))
            return "sum";
        return "OK";
    }
    String ReadMsg(BufferedInputStream in){
        String toSend = "";
        int i;
        boolean end = false;
        
        for(int j = 0; j < 5;++j){
            if(toSend.endsWith("\r\n"))
                return "syntax";
            try{
                i = in.read();
                if(i == -1)
                    return "syntax";
                if(j == 0  &&  (char)i != 'I'  &&  (char)i != 'F')
                    return "syntax";
                toSend += (char)i;
            }
            catch(IOException e ){
                return "syntax";
            }
        }
        if(toSend.equals("FOTO "))
            return ReadFoto(in);
        if(!toSend.equals("INFO "))
            return "syntax";
        while(true){
            if(TimeOut())
                return "timeout";
            try{
                i = in.read();
                if(i == -1)
                    return "syntax";
                if(end && i == 10)
                    break;
                end = (i == 13);
            }
            catch(IOException e ){
                return "syntax";
            }
        }
        return "OK";
    }
    void EndConnection(){
        out.close();
        try {
            client.close();
            System.out.println("succesfully closing session for client " + client.getInetAddress() + ":" + client.getPort());
        } 
        catch (IOException ex) {            
            System.out.println("Unsuccesfully closing session for client " + client.getInetAddress() + ":" + client.getPort());
        }
        
//        try{
//            fileWriter.close();
//        }
//        catch(IOException e){
//            System.err.println("Couldn't close a writer to a file <log.txt> ");
//        }
    }
    boolean TimeOut(){
        Calendar timeNow = Calendar.getInstance();
        if(!timeNow.getTime().before(timeEnd.getTime())){
            out.printf("502 TIMEOUT\r\n");
            EndConnection();
            return true;
        }
        return false;
    }
}
