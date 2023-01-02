import java.util.List;
import java.util.Random;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class FieldRace {
    private static final int PLAYER_COUNT = 10;
    private static final int CHECKPOINT_COUNT = 5;
    private static final AtomicBoolean isOn = new AtomicBoolean(false);
    private static final ConcurrentHashMap<Integer, Integer> scores = new ConcurrentHashMap<>(PLAYER_COUNT);
    private static final ArrayList<AtomicInteger> checkpointScores = new ArrayList<>(PLAYER_COUNT);
    private static final List<BlockingQueue<AtomicInteger>> checkpointQueues = Collections.synchronizedList(new ArrayList<>(CHECKPOINT_COUNT));

    /**
     * Function to write the points every second to the screen
     */
    public static void writeStats(){
        ArrayList<Integer> PlayerScores = new ArrayList<>(PLAYER_COUNT);
        ArrayList<Integer> PlayerIDs = new ArrayList<>(PLAYER_COUNT);
        //scores.forEach((iD, score) -> System.out.print(iD+1 + "=" + score + ", "));

        scores.forEach((iD, score) -> {
            if(PlayerScores.size() == 0){
                PlayerScores.add(score);
                PlayerIDs.add(iD+1);
            }
            else{
                boolean notPlaced = true;
                for(int i=0; i<PlayerScores.size(); i++){
                    if(score <= PlayerScores.get(i) && notPlaced){
                        PlayerScores.add(i, score);
                        PlayerIDs.add(i, (iD+1));
                        notPlaced = false;
                    }
                }
                if(notPlaced){
                    PlayerScores.add(score);
                    PlayerIDs.add(iD+1);
                }
            }
        });
        System.out.print("Scores: [");

        for (int i=PlayerScores.size()-1;i>=0; i--){
            System.out.print(PlayerIDs.get(i)+"="+PlayerScores.get(i));
            if(i != 0){
                System.out.print(", ");
            }
        }

        System.out.print("]\n");
    }

    /**
     * Function for the Racer Threads
     */
    public static void RacerFunk(int RacerID){
        Random rnd = new Random();
        int tmp = 0;
        while(isOn.get()) {
            try {
                int checkpoint = rnd.nextInt(0, CHECKPOINT_COUNT);
                Thread.sleep(rnd.nextInt(500, 2001));
                synchronized (checkpointScores.get(RacerID)){
                    checkpointQueues.get(checkpoint).offer(checkpointScores.get(RacerID));
                    while (checkpointScores.get(RacerID).get() == 0){
                        if (!isOn.get()){
                            return;
                        }
                        checkpointScores.get(RacerID).wait(3000);
                    }
                    //checkpointQueues.get(checkpoint).clear();
                    System.out.println("Player " + (RacerID+1) + " got " + checkpointScores.get(RacerID).get() + " points at checkpoint: " + (checkpoint+1));
                    tmp = scores.get(RacerID);
                    scores.put(RacerID, checkpointScores.get(RacerID).get()+tmp);
                    checkpointScores.get(RacerID).set(0);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Function to control the Checkpoint Threads
     */
    public static void CheckPointFunk(int QueueCount){
        Random rnd = new Random();
        AtomicInteger newScore;
        while(isOn.get()){
            try{
                newScore = checkpointQueues.get(QueueCount).poll(2, TimeUnit.SECONDS);
                if(newScore != null){
                    synchronized (newScore){
                        newScore.set(rnd.nextInt(10, 101));
                        newScore.notify();
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {

        /*
            Initialising lists
        */
        for(int i=0; i<PLAYER_COUNT; i++){
            scores.put(i, 0);
        }
        for (int i=0; i<PLAYER_COUNT; i++){
            checkpointScores.add(new AtomicInteger(0));
        }
        for (int i=0; i<CHECKPOINT_COUNT; i++){
            checkpointQueues.add(new LinkedBlockingQueue<AtomicInteger>());
        }

        isOn.set(true);

        var ex = Executors.newFixedThreadPool(PLAYER_COUNT + CHECKPOINT_COUNT + 1);
        for(int i=0; i<PLAYER_COUNT; i++){
            int ID = i;
            ex.submit(()->RacerFunk(ID));
        }
        for(int i=0;i<CHECKPOINT_COUNT; i++){
            int Counter = i;
            ex.submit(()->CheckPointFunk(Counter));
        }
        ex.submit(()->{
            while(isOn.get()){
                try {
                    writeStats();
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        Thread.sleep(10000);

        isOn.set(false);

        try{
            ex.shutdown();
            ex.awaitTermination(3, TimeUnit.SECONDS);
            ex.shutdownNow();
        }
        catch (InterruptedException e){
            e.printStackTrace();
        }

        writeStats();

    }
}
