package client;

/**
 * * Created by Hebron on 5/8/2016.
 */

import java.io.*;
import java.net.*;
import java.util.*;

public class Bot {
    // For communicating with IRC
    BufferedWriter writer;
    BufferedReader reader;
    Socket sock;
    InputStream in;


    Properties prop = new Properties();

    // IRC variables
    String server;
    String nick;
    String user;
    String channels[];

    // Trivia variables
    String questionsFile;
    String keysFile;
    String joinTextFile;
    String exitTextFile;
    String startTextFile;
    String endTextFile;
    int numOfAllowedWinners;
    String[] playTimes;
    int numOfQuestionsPerRound;

    public void startBot() throws Exception {
        System.out.println("*** starting bot ***");
        readInConfigurations();

        sock = new Socket(server, 6667);
        writer = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream()));
        reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));

        push("NICK " + nick);
        push("USER " + user + " * * :Vashy's Riddler");

        String l;
        while ( (l = reader.readLine()) != null) {
            System.out.println(l);
            if (l.contains("004"))
                break;
            else if (l.contains("433")){
                System.out.println("*** NICK in use already ***");
                return;
            } else if (l.startsWith("PING ")) {
                // We must respond to PINGs to avoid being disconnected.
                push("PONG " + l.substring(5));
                System.out.println("PONG " + l.substring(5));
            }
        }
        System.out.println("*** joining channels *** ");
        for (String chan : channels) {
            push("JOIN " + chan);
            System.out.println("*** joined "  + chan + " ***");
        }

        while((l = reader.readLine()) != null) {
            System.out.println(l);
            Map line = parseLine(l);
            if (l.startsWith("PING ")) {
                // We must respond to PINGs to avoid being disconnected.
                push("PONG " + l.substring(5));
                System.out.println("PONG " + l.substring(5));
            } else if (line == null) {
                // couldn't parse this line
                // do nothing
                System.out.println("*** could not parse: " + l + " ***");
            } else {
                String type = line.get("type").toString();
                String replyTo = line.get("replyTo").toString(); // channel can be null

                if (line.get("command").equals(":!restart")) {
                    readInConfigurations();
                }
            }
        }
    }
    private void push(String s) {
        try {
            writer.write(s + "\r\n");
            writer.flush();
        } catch (IOException e) {
            System.out.println("*** failed to write to IRC: " + s + " ***");
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }
    private void readInConfigurations(){
        try {
            if (in != null)
                in.close();
        } catch (IOException ex) {
            System.out.println("*** error when closing config file ***");
        }
        try {
            in = new FileInputStream(System.getProperty("user.dir") + "/config.properties");
            prop.load(in);

            // read in IRC configs
            server = prop.getProperty("server");
            nick = prop.getProperty("nick");
            user = prop.getProperty("user");
            channels = prop.getProperty("channels").split(",");

            // read in Trivia configs
            questionsFile = prop.getProperty("questions");
            keysFile = prop.getProperty("keys");
            joinTextFile = prop.getProperty("join_text");
            exitTextFile = prop.getProperty("exit_text");
            startTextFile = prop.getProperty("start_text");
            endTextFile = prop.getProperty("end_text");
            numOfAllowedWinners = Integer.parseInt(prop.getProperty("num_of_winners"));
            playTimes = prop.getProperty("times_to_play").split(",");
            numOfQuestionsPerRound = Integer.parseInt(prop.getProperty("num_of_questions_per_round"));

            System.out.println("*** reloaded configs ***");
        } catch (FileNotFoundException ex) {
            System.out.println("*** config file not found ***");
            ex.printStackTrace();
        } catch (IOException ex) {
            System.out.println("*** IO exception when reading config file ***");
            ex.printStackTrace();

        }
    }
    private Map parseLine(String line) {
        Map input = new HashMap<String, String>();
        try {
            String portions[] = line.split(" ");
            if (portions[0].equals("PING")) {
                // PINGs don't need to be parsed, just PONGed back
                return null;
            } else {
                input.put("timestamp", new Date().toString());
                input.put("type", portions[1]); // e.g. PRIVMSG
                input.put("replyTo", portions[2]); // the user or channel to reply to
                input.put("command", portions[3]);
            }
        } catch (IndexOutOfBoundsException e) {
            System.out.println();
            // there's no channel
            return null;
        } catch (Exception e) {
            System.out.println("*** exception thrown when parsing line: " + line + " ***");
            System.out.println(e.getMessage());
            e.printStackTrace();
            return null;
        }
        return input;
    }
}
