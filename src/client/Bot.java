package client;

/**
 * * Created by Hebron on 5/8/2016.
 */

import com.sun.deploy.util.StringUtils;

import java.io.*;
import java.net.*;
import java.util.*;

public class Bot {
    // For communicating with IRC
    private BufferedWriter writer;
    private BufferedReader reader;
    private Socket sock;
    private InputStream in;


    private Properties prop = new Properties();

    // IRC variables
    private String server;
    private String nick;
    private String user;
    private String channels[];

    // Trivia variables
    private String questionsFile;
    private String keysFile;
    private String joinTextFile;
    private String exitTextFile;
    private String startTextFile;
    private String endTextFile;
    private String winnerCongratsTextFile;
    private int numOfAllowedWinners;
    private String[] playTimes;
    private int numOfQuestionsPerRound;

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
                String postedTo = line.get("postedTo").toString(); // channel can be null

                if (line.get("command").equals(":!restart")) {
                    readInConfigurations();
                } else if (line.get("command").equals(":!test")) {
                    String winners[] = {nick};
                    sendKeysToWinners(winners);
                } else if (line.get("command").toString().startsWith(":!addkey") && postedTo.equals(nick)) {
                    addKeyToFile(line.get("commandParams").toString());
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
            winnerCongratsTextFile = prop.getProperty("winner_congrats_text");
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
                input.put("postedTo", portions[2]); // the user or channel to reply to
                input.put("command", portions[3]);
                String[] commandParams;
                commandParams = Arrays.copyOfRange(portions, 4, portions.length);
                input.put("commandParams", StringUtils.join(Arrays.asList(commandParams), " "));
            }
        } catch (IndexOutOfBoundsException e) {
            System.out.println("*** exception thrown when parsing line: " + line + " ***");
            System.out.println(e.getMessage());
            e.printStackTrace();
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
    private void sendKeysToWinners(String[] winners){

        // Pull the congratulations text
        // which will be sent right before the key is sent to a winner
        String congratsText = null;
        try {
            FileReader fileReader = new FileReader(winnerCongratsTextFile);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            congratsText = bufferedReader.readLine();
            bufferedReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("ERROR: Couldn't find congrats text file: " + winnerCongratsTextFile);
        } catch (IOException e) {
            System.out.println("ERROR: Couldn't read from congrats text file: " + winnerCongratsTextFile );
        }


        for (String winner : winners) {
            String key = getKeyPrize();
            if (key == null || key.isEmpty())
                System.out.println("ERROR: key not found in key file for user: " + winner);
            else {
                if (congratsText.isEmpty() || congratsText == null) {
                    congratsText = "Congratulations!";
                }
                push("PRIVMSG " + winner + " :" + congratsText);
                push("PRIVMSG " + winner + " :" + key);
            }
        }

    }
    private String getKeyPrize() {

        String key = "";
        try {
            FileReader fileReader = new FileReader(keysFile);
            BufferedReader br = new BufferedReader(fileReader);
            key = br.readLine();
            br.close();

            removeFirstLine(keysFile);
        } catch (FileNotFoundException ex) {
            System.out.println("ERROR: Couldn't find keys file: " + keysFile);
        } catch (IOException ex) {
            System.out.println("ERROR: IO Exception when trying to read key from keys file: " + keysFile);
        }

        return key;
    }
    private void removeFirstLine(String fileName) throws IOException {
        /* Got this method from: http://stackoverflow.com/a/13178980/1496918 */
        RandomAccessFile raf = new RandomAccessFile(fileName, "rw");
        //Initial write position
        long writePosition = raf.getFilePointer();
        raf.readLine();
        // Shift the next lines upwards.
        long readPosition = raf.getFilePointer();

        byte[] buff = new byte[1024];
        int n;
        while (-1 != (n = raf.read(buff))) {
            raf.seek(writePosition);
            raf.write(buff, 0, n);
            readPosition += n;
            writePosition += n;
            raf.seek(readPosition);
        }
        raf.setLength(writePosition);
        raf.close();
    }
    private void addKeyToFile(String keyToWrite){
        try {
            FileWriter fw = new FileWriter(keysFile);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.append(keyToWrite);

            bw.close();
            fw.close();

        } catch (IOException ex){
            System.out.println("ERROR: IO Exception when trying to add key to keys file: " + keysFile);
        }
    }
}
