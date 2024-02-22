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
     * Vars WE ADDED!
     */
    private List<Integer> playerTokensCardsList;
    private List<Integer> playerActionsList;
    private Dealer dealer;

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
        this.playerTokensCardsList = new LinkedList<Integer>();
        this.playerActionsList = new LinkedList<Integer>();
    }

    // Added methods by me
    public List<Integer> getPlayerTokensCardsList() {
        return this.playerTokensCardsList;
    }

    public List<Integer> getPlayerActionsList() {
        return this.playerActionsList;
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

        while (!terminate) {
            // TODO implement main player loop
            while (!playerActionsList.isEmpty()) {
                int currentCard = playerActionsList.remove(0);
                System.out.println("hi");
                if (playerTokensCardsList.contains(currentCard)) {
                    table.removeToken(this.id, table.cardToSlot[currentCard]);
                    playerTokensCardsList.remove(playerTokensCardsList.indexOf(currentCard));
                } else {
                    if (playerTokensCardsList.size() < env.config.featureSize) {
                        table.placeToken(this.id, table.cardToSlot[currentCard]);
                        playerTokensCardsList.add(currentCard);
                    }
                }
                if (playerTokensCardsList.size() == env.config.featureSize) { // Notify the dealer and wait until the
                                                                              // dealder checks if it is a legal set or
                                                                              // not
                    synchronized (dealer) {
                        Dealer.legalSetCheckList.add(new LinkedList<Integer>(playerTokensCardsList));
                        Dealer.legalSetOrderList.add(this.id);

                        try {
                            dealer.wait();
                        } catch (InterruptedException e) {
                        }
                        ;
                    }
                }
            }
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
                try {
                    synchronized (this) {
                        wait();
                    }
                } catch (InterruptedException ignored) {
                }
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
        // TODO implement
        if ((dealer.getPlayersThreads()[this.id]).getState() == Thread.State.WAITING)
            return;
        if (playerActionsList.size() >= env.config.featureSize)
            return;

        playerActionsList.add(table.slotToCard[slot]);
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        env.ui.setFreeze(this.id, env.config.pointFreezeMillis);
        try {
            Thread.sleep(env.config.pointFreezeMillis);
        } catch (InterruptedException e) {
        }
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        env.ui.setFreeze(this.id, env.config.penaltyFreezeMillis);
        try {
            Thread.sleep(env.config.penaltyFreezeMillis);
        } catch (InterruptedException e) {
        }
        synchronized (dealer) {
            dealer.notify();
        }
    }

    public int score() {
        return score;
    }
}
