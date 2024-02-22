package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * Vars WE ADDED!
     */
    private Thread[] playersThreads;
    private long startTime;
    public static List<LinkedList<Integer>> legalSetCheckList;
    public static List<Integer> legalSetOrderList;

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        // Vars we added:
        legalSetCheckList = new LinkedList<LinkedList<Integer>>();
        legalSetOrderList = new LinkedList<Integer>();
        this.playersThreads = new Thread[players.length];
        this.startTime = Long.MAX_VALUE;
    }

    /*
     * Methods WE ADDED!
     */

    public Thread[] getPlayersThreads() {
        return this.playersThreads;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        env.ui.setCountdown(env.config.turnTimeoutMillis, false); // We started the game with 60 seconds on the clock

        for (int i = 0; i < players.length; i++) {
            Thread playerThread = new Thread(players[i], "Player " + players[i].id);
            playersThreads[i] = playerThread;
            playerThread.start();
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement

        //synchronized (table) {
            if (legalSetOrderList.isEmpty())
                return;
            for (int i = 0; i < legalSetCheckList.size(); i++) {
                List<Integer> set = legalSetCheckList.get(i);
                Integer[] cardsArrInteger = set.toArray(new Integer[set.size()]); // convert list to array
                int[] cardTokens = Arrays.stream(cardsArrInteger).mapToInt(Integer::intValue).toArray();
                if (!env.util.testSet(cardTokens)) {
                    for (int card : set) {
                        table.removeToken(legalSetOrderList.get(i), table.cardToSlot[card]);
                    }
                    players[legalSetOrderList.get(i)].getPlayerTokensCardsList().clear();
                    players[legalSetOrderList.get(i)].penalty();
                }
            }

            for (int i = 0; i < legalSetCheckList.size(); i++) {
                List<Integer> set = legalSetCheckList.get(i);
                Integer[] cardsArrInteger = set.toArray(new Integer[set.size()]); // convert list to array
                int[] cardTokens = Arrays.stream(cardsArrInteger).mapToInt(Integer::intValue).toArray();
                if (set.size() < env.config.featureSize) {
                    continue;
                }
                if (env.util.testSet(cardTokens)) {
                    for (int card : cardTokens) {
                        env.ui.removeTokens(table.cardToSlot[card]);
                        players[legalSetOrderList.get(i)].getPlayerTokensCardsList().clear();

                        for (int j = 0; j < legalSetCheckList.size(); j++) {
                            if (legalSetCheckList.get(j).contains(card)) {
                                legalSetCheckList.get(j).remove(legalSetCheckList.get(j).indexOf(card));
                            }
                        }
                    }
                }
            }
            legalSetCheckList.clear();
            legalSetOrderList.clear();
        //}
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        if (startTime == Long.MAX_VALUE) // Game started
        {
            int cardsNeeded = env.config.tableSize - table.countCards();
            List<Integer> positions = new LinkedList<Integer>();

            for (int i = 0; i < cardsNeeded; i++)
                positions.add(i);
            for (int i = 0; i < cardsNeeded; i++) {
                if (deck.size() > 0) {
                    int cardId = deck.get(i);
                    int randomPosition = ThreadLocalRandom.current().nextInt(0, positions.size()); // Random position
                    table.placeCard(cardId, positions.remove(randomPosition));
                }
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        long timeNow = System.currentTimeMillis();
        if (startTime == Long.MAX_VALUE) {
            reshuffleTime = env.config.turnTimeoutMillis + System.currentTimeMillis();
            startTime = timeNow;
        }
        env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
    }
}
