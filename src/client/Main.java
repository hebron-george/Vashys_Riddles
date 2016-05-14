package client;

/**
 * Created by Hebron on 5/8/2016.
 */
public class Main {
    public static void main(String[] args) {
        Bot riddler = new Bot();
        try {
            riddler.startBot();
        } catch (Exception e) {
            System.out.println("*** Exception thrown ***");
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }
}
