package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.Collections;
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
    private int playerIdWithSet;
    private boolean showHint; // if i showed the hint in this round
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
        this.terminate = false;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        // Vars we added:
        this.dealerThread = null; // will update on the thread
        legalSetCheckList = new LinkedList<LinkedList<Integer>>();
        legalSetOrderList = new LinkedList<Integer>();
        this.playersThreads = new Thread[players.length];
        this.startTime = Long.MAX_VALUE;
        this.playerIdWithSet = -1; // no set
        this.showHint = false;
    }

    /*
     * Methods we added
     */
    public Thread getDealerThread() {
        return this.dealerThread;
    }

    public Player[] getPlayers() {
        return this.players;
    }

    public Thread[] getPlayersThreads() {
        return this.playersThreads;
    }

    public List<Integer> getDeck() {
        return this.deck;
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

        for (Player p : players) {
            playersThreads[p.getId()] = new Thread(p, "Player " + p.getId());
            playersThreads[p.getId()].start();
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
        for (int i = playersThreads.length - 1; i >= 0; i--) {
            synchronized (players[i]) {
                players[i].notify();
            }
            players[i].setPlay(true);
            players[i].terminate();
            playersThreads[i].interrupt();
            try {
                playersThreads[i].join();
            } catch (InterruptedException ignore) {
            }
        }
        this.terminate = true;
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
            List<Integer> removeSets = new LinkedList<Integer>();

            // no sets to check by the dealer, so exit
            if (legalSetOrderList.isEmpty())
                return;

            int[] cardTokens = new int[0];
            List<Integer> set;
            int playerIdSet = 0;
            // checking all the sets, by fifo order, if they are legal. If they are legal
            // handle them accordingly
            for (int i = 0; i < legalSetCheckList.size(); i++) {
                set = legalSetCheckList.get(i);
                playerIdSet = legalSetOrderList.get(i);
                // set is smaller than 3. Its irrelevant so we continue to the next set
                if (set.size() < env.config.featureSize) {
                    removeSets.add(legalSetOrderList.get(i)); // only 2 cards? we will remove this set after this
                                                              // iteration
                    continue;
                } else {
                        cardTokens = Arrays.stream(set.toArray(new Integer[set.size()])).mapToInt(Integer::intValue)
                                .toArray();
                    ;
                }
                // Checking if its a legal set
                if (env.util.testSet(cardTokens)) {
                    for (int card : cardTokens) {
                        if (table.cardToSlot[card] != null)
                            env.ui.removeTokens(table.cardToSlot[card]);
                        for (Player p : players) {
                            if (p.getPlayerTokensCardsList().contains(card))
                                p.getPlayerTokensCardsList().remove(p.getPlayerTokensCardsList().indexOf(card));
                        }
                        // Its a legal set, we need to remove similar cards from future sets
                        for (int j = 0; j < legalSetCheckList.size(); j++) {
                            if (legalSetCheckList.get(j).contains(card)) {
                                legalSetCheckList.get(j).remove(legalSetCheckList.get(j).indexOf(card));
                            }
                        }
                        table.removeCard(table.cardToSlot[card]);
                    }
                    playerIdWithSet = playerIdSet;
                    players[playerIdSet].point();
                    env.ui.setCountdown(env.config.turnTimeoutMillis, false); // Fixes the 'not showing 60 on reset bug'

                    // usually, when placing cards we unblock the player and reset time, because
                    // there no more
                    // cards left, we will do it here.
                    if ((deck.size() + table.countCards()) < 12) {
                        // All the cards on the table, so we unblock the player with the specific set
                        // found
                        players[playerIdWithSet].setPlay(true);
                        synchronized (players[playerIdWithSet]) {
                            players[playerIdWithSet].notify();
                        }
                        startTime = 0;
                        // System.out.println("Releasing all players from blocked, because all cards on
                        // the table (Last cards)");
                    }
                    removeSets.add(playerIdSet); // legal set, we will remove this set after this iteration

                    for (int remove : removeSets) {
                        legalSetCheckList.remove(legalSetOrderList.indexOf(remove));
                        legalSetOrderList.remove(legalSetOrderList.indexOf(remove));
                    }
                    return;
                } else {
                    players[playerIdSet].getPlayerTokensCardsList().clear();
                    players[playerIdSet].penalty();
                    // We release the player, so we can define a different block based on time when
                    // called penalty. (On the start of the player main thread)
                    players[playerIdSet].setPlay(true);
                    synchronized (players[playerIdSet]) {
                        players[playerIdSet].notify();
                        players[playerIdSet].setPlay(true);
                    }
                    for (int card : set) {
                        if (table.cardToSlot[card] != null)
                            table.removeToken(playerIdSet, table.cardToSlot[card]);
                    }
                    removeSets.add(playerIdSet); // we gave him penalty, so we remove this set after this
                                                 // iteration
                    // System.out.println("Player " + legalSetOrderList.get(i)
                    // + " will be released to recieve penalty instantly after.");
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
        synchronized (table) {
            // adding the needed cards
            for (int i = 0; i < env.config.tableSize; i++) {
                if (deck.size() > 0) {
                    int randomPosition = ThreadLocalRandom.current().nextInt(0, positions.size()); // Random position
                    // Game started, we need to put all the 12 cards on random places
                    if (startTime == Long.MAX_VALUE) {
                        Collections.shuffle(deck);
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
        // System.out.println("Releasing all players from blocked, because all cards on
        // the table");
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {

        // sleep every second (almost), if last 5 seconds dont sleep
        if (reshuffleTime - System.currentTimeMillis() > env.config.turnTimeoutWarningMillis) {
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
        final int timeForHint = 15000;

        // start time is on default time, or 60 seconds passed, it will trigger the game
        // to reset the round here

        if (startTime == Long.MAX_VALUE || startTime == 0) {
            reshuffleTime = env.config.turnTimeoutMillis + System.currentTimeMillis();
            startTime = timeNow;
            this.showHint = false;
        }
        // time ended, block players
        else if (System.currentTimeMillis() >= reshuffleTime) {
            for (Player p : players) {
                p.setPlay(false);
                // System.out.println("Player " + p.getId() + " is blocked, because time
                // ended.");
            }
        }

        // deal with hints
        if (env.config.hints == true && showHint == false
                && (reshuffleTime - System.currentTimeMillis()) < timeForHint) {
            table.hints();
            this.showHint = true;
        }

        // still not 5 seconds left, so we display the left time without warning. Else
        // otherwise
        if (reshuffleTime - System.currentTimeMillis() > env.config.turnTimeoutWarningMillis)
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
        else {
            if (reshuffleTime - System.currentTimeMillis() > 0)
                env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), true);
            else
                env.ui.setCountdown(0, false);
        }

        if (env.config.pointFreezeMillis == 0 && env.config.penaltyFreezeMillis == 0)
            return;
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
        boolean lastCards = false;
        List<Integer> positions = new LinkedList<Integer>();
        int cardsLeft = table.countCards();
        env.ui.removeTokens();
        for (int i = 0; i < cardsLeft; i++)
            positions.add(i);
        // reset player vars
        for (Player p : players)
            p.resetAll();
        // reset dealer's lists
        legalSetCheckList.clear();
        legalSetOrderList.clear();
        // reset hint value
        showHint = false;
        // adding back to the deck the left visible cards
        for (int i = 0; i < table.slotToCard.length; i++) {
            if (table.slotToCard[i] != null)
                deck.add(table.slotToCard[i]);
        }

        // removing cards from grid
        synchronized (table) {
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
            // remove all tokens
            env.ui.removeTokens();

            startTime = Long.MAX_VALUE;
        }
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
        int winners = 0;
        // array with all the winner players id
        for (int i = 0; i < players.length; i++) {
            if (players[i].score() == biggestScore) {
                playersIds[winners] = players[i].getId();
                winners++;
            }
        }
        // announce to the grid the winners
        env.ui.announceWinner(playersIds);
        terminate();
    }
}
