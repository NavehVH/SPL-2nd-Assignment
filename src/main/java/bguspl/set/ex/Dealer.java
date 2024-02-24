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
     * Vars we added
     */
    private Thread dealerThread; // the dealer's thread
    private Thread[] playersThreads; // all of the players threads
    private long startTime; // when we start the round (Usually equals to Long.Max\0 to tell it to reset)
    public static List<LinkedList<Integer>> legalSetCheckList; // All the sets we need to check if legal, by order
    public static List<Integer> legalSetOrderList;// All the IDs of the players, with the order of legalSetCheckList

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
        this.dealerThread = null; // will update on the thread
        legalSetCheckList = new LinkedList<LinkedList<Integer>>();
        legalSetOrderList = new LinkedList<Integer>();
        this.playersThreads = new Thread[players.length];
        this.startTime = Long.MAX_VALUE;
    }

    /*
     * Methods we added
     */
    public Thread getDealerThread() {
        return this.dealerThread;
    }

    public Thread[] getPlayersThreads() {
        return this.playersThreads;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        dealerThread = Thread.currentThread();
        env.ui.setCountdown(env.config.turnTimeoutMillis, false); // We started the game with 60 seconds on the clock

        // creating the players threads and running them
        for (int i = 0; i < players.length; i++) {
            Thread playerThread = new Thread(players[i], "Player " + players[i].id);
            playersThreads[i] = playerThread;
            playerThread.start();
        }

        // main thread loop that determines a full game round while there cards left or
        // we press exit
        while (!shouldFinish()) {
            placeCardsOnTable();
            updateTimerDisplay(false);
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
        synchronized (table) {
            // no sets to check by the dealer, so exit
            if (legalSetOrderList.isEmpty())
                return;

            // giving penalty to all bad sets, so we can delete them lateron without
            // worrying
            for (int i = 0; i < legalSetCheckList.size(); i++) {
                List<Integer> set = legalSetCheckList.get(i);
                Integer[] cardsArrInteger = set.toArray(new Integer[set.size()]); // convert list to array
                int[] cardTokens = Arrays.stream(cardsArrInteger).mapToInt(Integer::intValue).toArray();
                if (!env.util.testSet(cardTokens)) {
                    for (int card : set) {
                        if (table.cardToSlot[card] != null)
                            table.removeToken(legalSetOrderList.get(i), table.cardToSlot[card]);
                    }
                    players[legalSetOrderList.get(i)].getPlayerTokensCardsList().clear();
                    players[legalSetOrderList.get(i)].penalty();
                    // We release the player, so we can define a different block based on time when
                    // called penalty. (On the start of the player main thread)
                    players[legalSetOrderList.get(i)].setPlay(true);
                    synchronized (players[legalSetOrderList.get(i)]) {
                        players[legalSetOrderList.get(i)].notify();
                        players[legalSetOrderList.get(i)].setPlay(true);
                    }
                    System.out.println("Player " + legalSetOrderList.get(i)
                            + " will be released to recieve penalty instantly after.");
                }
            }

            // checking all the sets, by fifo order, if they are legal. If they are legal
            // handle them accordingly
            for (int i = 0; i < legalSetCheckList.size(); i++) {
                List<Integer> set = legalSetCheckList.get(i);
                Integer[] cardsArrInteger = set.toArray(new Integer[set.size()]); // convert list to array
                int[] cardTokens = Arrays.stream(cardsArrInteger).mapToInt(Integer::intValue).toArray();
                // set is smaller than 3. Its irrelevant so we continue to the next set
                if (set.size() < env.config.featureSize) {
                    continue;
                }
                // Checking if its a legal set
                if (env.util.testSet(cardTokens)) {
                    for (int card : cardTokens) {
                        env.ui.removeTokens(table.cardToSlot[card]);
                        players[legalSetOrderList.get(i)].getPlayerTokensCardsList().clear();
                        // Its a legal set, we need to remove similar cards from future sets
                        for (int j = 0; j < legalSetCheckList.size(); j++) {
                            if (legalSetCheckList.get(j).contains(card)) {
                                legalSetCheckList.get(j).remove(legalSetCheckList.get(j).indexOf(card));
                            }
                        }
                        table.removeCard(table.cardToSlot[card]);
                    }
                    players[legalSetOrderList.get(i)].point();
                    env.ui.setCountdown(env.config.turnTimeoutMillis, false); // Fixes the 'not showing 60 on reset bug'

                    // usually, when placing cards we unblock the player and reset time, because there no more
                    // cards left, we will do it here.
                    if ((deck.size() + table.countCards()) < 12) {
                        // All the cards on the table, so we can unblock the players to play
                        for (Player p : players) {
                            p.setPlay(true);
                            synchronized (p) {
                                p.notify();
                            }
                        }
                        startTime = 0;
                        System.out.println("Releasing all players from blocked, because all cards on the table (Last cards)");
                    }
                }
            }
            // clearing the list used tokens
            legalSetCheckList.clear();
            legalSetOrderList.clear();
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        int cardsNeeded = env.config.tableSize - table.countCards();
        int cardsInGame = deck.size() + table.countCards();

        // We don't need to add cards, so exit
        if (cardsNeeded == 0 || (deck.size() + table.countCards()) == 0)
            return;

        // we are on the last cards, we only need to put cards if round restarted
        if (cardsInGame < 12 && table.countCards() != 0) {
            return;
        }

        // to make it put cards on random places
        List<Integer> positions = new LinkedList<Integer>();
        for (int i = 0; i < env.config.tableSize; i++)
            positions.add(i);

        // adding the needed cards
        for (int i = 0; i < env.config.tableSize; i++) {
            if (deck.size() > 0) {
                int randomPosition = ThreadLocalRandom.current().nextInt(0, positions.size()); // Random position
                // Game started, we need to put all the 12 cards on random places
                if (startTime == Long.MAX_VALUE) {
                    int cardId = deck.remove(0);
                    table.placeCard(cardId, positions.remove(randomPosition));
                }
                // adding cards to missing places after a point was made
                else if (table.slotToCard[i] == null) {
                    int cardId = deck.remove(0);
                    table.placeCard(cardId, i);
                }
            }
        }
        // After giving a point we need to restart the timer, so we make sure it will
        // reset it next rotation by doing this
        startTime = 0; // reset timer

        // All the cards on the table, so we can allow the players to play
        for (Player p : players) {
            p.setPlay(true);
            synchronized (p) {
                p.notify();
            }
        }
        System.out.println("Releasing all players from blocked, because all cards on the table");
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {

        // sleep every second (almost), if last 5 seconds dont sleep
        if (reshuffleTime - System.currentTimeMillis() > env.config.endGamePauseMillies) {
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
            }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {

        long timeNow = System.currentTimeMillis();

        // start time is on default time, or 60 seconds passed, it will trigger the game
        // to reset the round here

        if (startTime == Long.MAX_VALUE || startTime == 0) {
            reshuffleTime = env.config.turnTimeoutMillis + System.currentTimeMillis();
            startTime = timeNow;
        }
        // time ended, block players
        else if (System.currentTimeMillis() >= reshuffleTime) {
            for (Player p : players) {
                p.setPlay(false);
                System.out.println("Player " + p.getId() + " is blocked, because time ended.");
            }
        }

        // still not 5 seconds left, so we display the left time without warning. Else
        // otherwise
        if (reshuffleTime - System.currentTimeMillis() > env.config.endGamePauseMillies)
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
        else
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), true);

        // passing all the players and checking if they are freezed, if so, update their
        // freeze time until its finished
        for (Player p : players) {
            // checking if he is in penalty (penaltyTime == 0 means its not)
            if (p.getPenaltyTime() != 0) {
                if (System.currentTimeMillis() < p.getPenaltyOverallTime())
                    env.ui.setFreeze(p.getId(), p.getPenaltyOverallTime() - System.currentTimeMillis() + 1000);
                else {
                    // not in penalty anymore, so we set to 0
                    p.setPenaltyTime(0);
                    // remove the freeze message on the player's name
                    env.ui.setFreeze(p.getId(), 0);
                }
            }
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        synchronized (players) {

        }
        boolean lastCards = false;
        List<Integer> positions = new LinkedList<Integer>();
        int cardsLeft = table.countCards();
        for (int i = 0; i < cardsLeft; i++)
            positions.add(i);
        // remove all tokens
        env.ui.removeTokens();
        // reset player vars
        for (Player p : players)
            p.resetAll();
        // reset dealer's lists
        legalSetCheckList.clear();
        legalSetOrderList.clear();
        // adding back to the deck the left visible cards
        for (int i = 0; i < table.slotToCard.length; i++) {
            if (table.slotToCard[i] != null)
                deck.add(table.slotToCard[i]);
        }

        // removing cards from grid
        for (int i = 0; i < cardsLeft; i++) {
            int randomPosition = ThreadLocalRandom.current().nextInt(0, positions.size()); // Random position
            // all cards are here, so remove them randomly
            if (cardsLeft == 12)
                table.removeCard(positions.get(randomPosition));
            // we don't have 12 cards, means we are at the end of the game so we will deal
            // it after the loop
            else {
                lastCards = true;
                break;
            }
            positions.remove(randomPosition);
        }
        // last cards, if they are visible, remove them from the correct slots
        if (lastCards) {
            for (int i = 0; i < table.slotToCard.length; i++) {
                if (table.slotToCard[i] != null)
                    table.removeCard(i);
            }
        }
        startTime = Long.MAX_VALUE;
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int count = 0;
        int biggestScore = 0;
        // getting biggest score
        for (int i = 0; i < players.length; i++)
            if (players[i].score() > biggestScore)
                biggestScore = players[i].score();
        // getting amount of winners with that score
        for (int i = 0; i < players.length; i++)
            if (players[i].score() == biggestScore)
                count++;
        int[] playersIds = new int[count];
        // array with all the winner players id
        for (int i = 0; i < players.length; i++)
            if (players[i].score() == biggestScore)
                playersIds[i] = players[i].getId();
        // announce to the grid the winners
        env.ui.announceWinner(playersIds);
    }
}
