package bguspl.set.ex;

import java.util.LinkedList;
import java.util.List;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * Vars we added
     */
    private Dealer dealer; //getting the dealer instance
    private List<Integer> playerTokensCardsList; //list with all of the player token on table
    private List<Integer> playerActionsList; //list of all keyboard actions the player trying to register
    private long penaltyTime; //The amount of time the player needs to be in penalty (usually 1/3 seconds)
    private long penaltyOverallTime; //The current time + penalty time to know how long the player needs to be in penalty

    private boolean play; //if the specific player can play or not. (false means it will get blocked)

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate
     * key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided
     *               manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;

        // Vars we added:
        this.dealer = dealer;
        this.penaltyTime = 0; //default time for non penalty
        this.penaltyOverallTime = 0;
        this.playerTokensCardsList = new LinkedList<Integer>();
        this.playerActionsList = new LinkedList<Integer>();
        this.play = false;
    }

    /*
     * Methods we added
     */

    public int getId() {
        return id;
    }

    public List<Integer> getPlayerTokensCardsList() {
        return this.playerTokensCardsList;
    }

    public List<Integer> getPlayerActionsList() {
        return this.playerActionsList;
    }

    public long getPenaltyTime() {
        return penaltyTime;
    }

    public void setPenaltyTime(long time) {
        this.penaltyTime = time;
    }

    public long getPenaltyOverallTime() {
        return penaltyOverallTime;
    }

    //reset all the values to the default values (usually for a new round)
    public void resetAll() {
        this.penaltyTime = 0; //no penalty time
        this.penaltyOverallTime = 0;
        this.playerTokensCardsList = new LinkedList<Integer>();
        this.playerActionsList = new LinkedList<Integer>();
    }
    public boolean getPlay() {
        return play;
    }

    public void setPlay(boolean play) {
        this.play = play;
    }

    //if needs to be in penalty, sleep for penalty duration and release afterwards the player
    public void dealWithPenalties() {
        if (penaltyTime != 0) {
            try {
                System.out.println("Player " + this.id + " will now sleep for " + (penaltyTime / 1000) + " seconds.");
                Thread.sleep(penaltyTime);
            } catch (InterruptedException e) {
            }
            synchronized (this) {
                this.notify();
                this.play = true;
                System.out.println("Player " + this.id + " penalty is over, so we unblock him.");
                env.ui.setFreeze(this.id, 0); //make sure its removed
            }
            //define that the players doesn't have penalty anymore
            penaltyTime = 0;
        }
    }

    //deal with token placement requests by the player
    public void dealWithPlayerActions() {
        while (!playerActionsList.isEmpty()) {
            int currentCard = playerActionsList.remove(0);
            //checking if to remove token from the list and table
            if (playerTokensCardsList.contains(currentCard)) {
                table.removeToken(this.id, table.cardToSlot[currentCard]);
                playerTokensCardsList.remove(playerTokensCardsList.indexOf(currentCard));
            } 
            //checking if to add to the list and table
            else {
                if (playerTokensCardsList.size() < env.config.featureSize) {
                    table.placeToken(this.id, table.cardToSlot[currentCard]);
                    playerTokensCardsList.add(currentCard);
                }
            }
            //if he has 3 tokens placed, we need to block the player and allow the dealer to deal with the set
            if (playerTokensCardsList.size() == env.config.featureSize) {                                                        
                synchronized (dealer) {
                    Dealer.legalSetCheckList.add(new LinkedList<Integer>(playerTokensCardsList));
                    Dealer.legalSetOrderList.add(this.id);
                    //player has 3 tokens, so we block him from putting more
                    this.play = false;
                    System.out.println("Player " + this.id + " is blocked, becuase he has 3 tokens");
                    dealer.getDealerThread().interrupt();
                    System.out.println("We awake dealer, because there is a set.");
                }
            }
        }
    }

    /**
     * The main player thread of each player starts here (main loop for the player
     * thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human)
            createArtificialIntelligence();

        //main player thread loop
        while (!terminate) {
            //if shouldn't play, we will block the player's thread
            while (!play) {
                synchronized(this) {
                    try {
                        wait();
                    } catch(InterruptedException e) {};
                }    
            }
            //if needs to be in penalty, sleep for penalty duration and release afterwards the player
            dealWithPenalties();

            //deal with token placement requests by the player
            dealWithPlayerActions();
        }
        if (!human)
            try {
                aiThread.join();
            } catch (InterruptedException ignored) {
            }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of
     * this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it
     * is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                //try {
                    //synchronized (this) {
                    //    wait();
                    //}
                //} catch (InterruptedException ignored) {
                //}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        //Checks if the thread is in WAIT or is SLEEPING, so we block it from creating new actions while its blocked (Thank you stackoverflow <3)
        if ((dealer.getPlayersThreads()[this.id]).getState() == Thread.State.WAITING || (dealer.getPlayersThreads()[this.id]).getState() == Thread.State.TIMED_WAITING) 
            return;

        //if we have 3 actions already, block it from adding more
        if (playerActionsList.size() >= env.config.featureSize)
            return;

        //adding new key action
        if (table.slotToCard[slot] != null)
            playerActionsList.add(table.slotToCard[slot]);
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        env.ui.setScore(id, ++score);
        env.ui.setFreeze(this.id, env.config.pointFreezeMillis);
        //telling the thread that the player has a penalty, will handle it on the player thread loop
        penaltyTime = env.config.pointFreezeMillis;
        penaltyOverallTime = System.currentTimeMillis() + env.config.pointFreezeMillis;
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        env.ui.setFreeze(this.id, env.config.penaltyFreezeMillis);
        //telling the thread that the player has a penalty, will handle it on the player thread loop
        penaltyTime = env.config.penaltyFreezeMillis;
        penaltyOverallTime = System.currentTimeMillis() + env.config.penaltyFreezeMillis;
    }

    public int score() {
        return score;
    }
}
