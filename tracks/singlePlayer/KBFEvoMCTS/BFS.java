package tracks.singlePlayer.KBFEvoMCTS;

import core.game.Observation;
import core.game.StateObservation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import tools.ElapsedCpuTimer;
import ontology.Types;
import ontology.Types.WINNER;
import tools.Vector2d;

/**
 *
 * @author hauck
 */
public class BFS extends CustomController {

    private static final int MIN_TIME = 9;
    private final int BREAK_FREE_MEMORY = 256 * 1024 * 1024;

    private static int NUM_ACTIONS;
    private static ArrayList<Types.ACTIONS> availableActions;

    private LinkedList<Node> queue;
    private HashSet<Long> exploredStates;
    private final LinkedList<Types.ACTIONS> calculatedActions;

    private final StateObservation prevStateObs;
    private boolean moved;
    private final int blockSize;
    private double prevScore;
    private boolean switchController;

    private final int ITERATIONS = 6;
    public double epsilon = 1e-6;
    public Random m_rnd = new Random();
    
    public boolean alert = false;
    public boolean alert2 = true;
    public int count = 0;

    public BFS(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {

        availableActions = stateObs.getAvailableActions();
        NUM_ACTIONS = stateObs.getAvailableActions().size();

        queue = new LinkedList<>();
        exploredStates = new HashSet<>();
        calculatedActions = new LinkedList<>();

        prevStateObs = stateObs;
        moved = false;
        blockSize = stateObs.getBlockSize();

        Node initialNode = new Node(stateObs.copy(), new LinkedList<Types.ACTIONS>(), NUM_ACTIONS);
        queue.add(initialNode);
    }

    

   

    public Types.ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {

        long remaining;
        //System.out.println("mem left:"+(Runtime.getRuntime().maxMemory()-Runtime.getRuntime().totalMemory())+", "+(count++));
        checkMemoryUsage();
        checkForEnd(stateObs);

        if (!moved && !compareStates(stateObs, prevStateObs)) {
            switchController = true;
            return getAction(stateObs);
        }

        reduceNodeCount();

        do {
            expand();

            if (queue.isEmpty()) {
                break;
            }

            remaining = elapsedTimer.remainingTimeMillis();
        } while (remaining > MIN_TIME);

        return getAction(stateObs);
    }

    public void expand() {
        if (queue.isEmpty()) {
            return;
        }

        Node currentNode = queue.getFirst();

        StateObservation state = currentNode.getState();
        Types.ACTIONS unexploredAction = currentNode.getUnexploredAction();

        state = advanceState(state, unexploredAction);

        if (state == null) {
            if (currentNode.noRemainingActions()) {
                if (queue.size() == 1) {
                    System.out.println("Queue size == 0");
                    executeNode(queue.getFirst());
                }
                queue.removeFirst();
            }
        } else {

            LinkedList<Types.ACTIONS> actionHistory = new LinkedList<>();
            copyObjectsToLinkedList(actionHistory, currentNode.getActionHistory());

            actionHistory.add(unexploredAction);

            Node followingNode = new Node(state, actionHistory, NUM_ACTIONS);
            queue.add(followingNode);

            if (currentNode.noRemainingActions()) {
                 if (queue.size() == 1) {
                    System.out.println("!!!!!!!!!!!Queue size == 0");
                    executeNode(queue.getFirst());
                }
                queue.removeFirst();
            }

            evaluate(state, followingNode);

        }

    }

    public StateObservation advanceState(StateObservation stateObs, Types.ACTIONS action) {

        StateObservation stCopy = stateObs.copy();

        stCopy.advance(action);

        if (stCopy.isGameOver() && stCopy.getGameWinner() == Types.WINNER.PLAYER_LOSES) {
            return null;
        }

        long stateId = calculateStateId(stCopy);

        // state already explored
        if (exploredStates.contains(stateId)) {
            return null;
        }

        exploredStates.add(stateId);

        return stCopy;
    }

    private void executeNode(Node node) {
        if (node == null) {
            return;
        }

        StateObservation state = node.getCurrentState().copy();
        copyObjectsToLinkedList(calculatedActions, node.getActionHistory());

        Iterator<Node> iter = queue.iterator();

        while (iter.hasNext()) {
            Node n = iter.next();
            n.clear();
        }

        queue.clear();
        exploredStates.clear();

        // This state becomes the new default state
        queue = new LinkedList<>();
        exploredStates = new HashSet<>();
        prevScore = state.getGameScore();

        Node initialNode = new Node(state, new LinkedList<Types.ACTIONS>(), NUM_ACTIONS);
        queue.add(initialNode);
    }

    private void evaluate(StateObservation stateObs, Node node) {
        if (stateObs.isGameOver() && stateObs.getGameWinner() == Types.WINNER.PLAYER_WINS) {
            copyObjectsToLinkedList(calculatedActions, node.getActionHistory());

            queue = new LinkedList<>();
            exploredStates = new HashSet<>();
        }
    }

    private Types.ACTIONS getAction(StateObservation stateObs) {

        if (!calculatedActions.isEmpty()) {
            if(!alert2){
                System.out.println("returning store action");
                alert2 = true;
            }
            Types.ACTIONS action = calculatedActions.getFirst();
            moved = true;

            stateObs = stateObs.copy();
            stateObs.advance(action);
            if (calculatedActions.isEmpty() == false) {
                stateObs.advance(calculatedActions.removeFirst());
            }

            if (stateObs.getGameWinner() == WINNER.PLAYER_LOSES) {
                switchController = true;
                return Types.ACTIONS.ACTION_NIL;
            }

            return action;

        }
        
        if(alert2){
            System.out.println("Empty store, return NIL");
            alert2 = false;
            
        }
        return Types.ACTIONS.ACTION_NIL;
    }

    public boolean switchController() {
        if (switchController || (queue.isEmpty() && calculatedActions.isEmpty())) {
            if(!alert){
                if(switchController)
                    System.out.println("Switching controller - Hit PLAYER_LOSES or GameOver");
                if (queue.isEmpty() && calculatedActions.isEmpty())
                    System.out.println("Switching controller - queue & calculatedActions empty");
                alert= true;
            }
            return true;
        }

        return false;
    }

    public long calculateStateId(StateObservation stateObs) {
        long h = 1125899906842597L;
        ArrayList<Observation>[][] observGrid = stateObs.getObservationGrid();

        for (int y = 0; y < observGrid[0].length; y++) {
            for (int x = 0; x < observGrid.length; x++) {
                for (int i = 0; i < observGrid[x][y].size(); i++) {
                    Observation observ = observGrid[x][y].get(i);

                    h = 31 * h + x;
                    h = 31 * h + y;
                    h = 31 * h + observ.category;
                    h = 31 * h + observ.itype;
                }
            }
        }

        h = 31 * h + (int) (stateObs.getAvatarPosition().x / blockSize);
        h = 31 * h + (int) (stateObs.getAvatarPosition().y / blockSize);
        h = 31 * h + stateObs.getAvatarType();
        h = 31 * h + stateObs.getAvatarResources().size();
        h = 31 * h + (int) (stateObs.getGameScore() * 100);

        return h;
    }

    private void copyObjectsToLinkedList(LinkedList<Types.ACTIONS> destList, LinkedList<Types.ACTIONS> sourceList) {
        Iterator<Types.ACTIONS> iter = sourceList.iterator();
        while (iter.hasNext()) {
            destList.add(iter.next());
        }
    }

    private void checkForEnd(StateObservation stateObs) {
        if (queue.size() > 0 && stateObs.getGameTick() + queue.getFirst().getCurrentState().getGameTick() + 10 >= 2000) {
            System.out.println("Close to End Game");
            executeNode(getOptimalNode());
        }
    }

    private void checkMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        if (runtime.maxMemory() - runtime.totalMemory() < BREAK_FREE_MEMORY) {
            //System.out.println("Total memory left: "+(runtime.maxMemory()-runtime.totalMemory())+",Freeing memory"+", queue size: "+queue.size());
            executeNode(getOptimalNode());
            //System.gc();
        }
    }

    private Node getOptimalNode() {
        if (queue.isEmpty()) {
            return null;
        }

        Node optNode = queue.getFirst();
        double maxScore = prevScore;
        Iterator<Node> iter = queue.iterator();

        while (iter.hasNext()) {
            Node node = iter.next();

            if (node.getCurrentState().getGameScore() > maxScore) {
                maxScore = node.getCurrentState().getGameScore();
                optNode = node;
            }
        }

        return optNode;
    }
    
    private boolean compareStates(StateObservation stateObs1, StateObservation stateObs2) {
        if (!compareObservationLists(stateObs1.getImmovablePositions(), stateObs2.getImmovablePositions())) {
            return false;
        }
        if (!compareObservationLists(stateObs1.getMovablePositions(), stateObs2.getMovablePositions())) {
            return false;
        }
        return true;
    }

    private boolean compareObservationLists(ArrayList<Observation>[] obsList1, ArrayList<Observation>[] obsList2) {
        if (obsList1 == null || obsList2 == null) {
            if (obsList1 == null && obsList2 == null) {
                return true;
            }
            return false;
        }

        if (obsList1.length != obsList2.length) {
            return false;
        }

        for (int type = 0; type < obsList1.length; type++) {
            if (obsList1[type].size() != obsList2[type].size()) {
                return false;
            }

            for (int i = 0; i < obsList1[type].size(); i++) {
                Vector2d pos1 = obsList1[type].get(i).position;
                Vector2d pos2 = obsList2[type].get(i).position;

                if (pos1.x != pos2.x || pos1.y != pos2.y) {
                    return false;
                }
            }
        }

        return true;
    }
    
     private void reduceNodeCount() {
        int nodeCount = queue.size();

        if (nodeCount > 1 && nodeCount > 20000) {
            System.out.println("Reducing Node Count");
            double eventsMin = Integer.MAX_VALUE;
            double eventsMax = 0;
            double scoreMin = Double.POSITIVE_INFINITY;
            double scoreMax = Double.NEGATIVE_INFINITY;

            Iterator<Node> iter = queue.iterator();

            while (iter.hasNext()) {
                Node node = iter.next();
                StateObservation state = node.getCurrentState();
                int events = state.getEventsHistory().size();
                double score = state.getGameScore();

                if (events < eventsMin) {
                    eventsMin = events;
                } else if (events > eventsMax) {
                    eventsMax = events;
                }

                if (score < scoreMin) {
                    scoreMin = score;
                } else if (score > scoreMax) {
                    scoreMax = score;
                }
            }

            if (scoreMax != scoreMin || eventsMax != eventsMin) {
                // Reduce Nodes
                iter = queue.iterator();
                while (iter.hasNext()) {
                    Node node = iter.next();
                    StateObservation state = node.getCurrentState();
                    int events = state.getEventsHistory().size();
                    double score = state.getGameScore();

                    if (events <= eventsMin || score <= scoreMin) {
                        node.clear();
                        iter.remove();
                    }
                }
            }
        }

    }

   
    /*
    *   Node used to perform breadth first search
     */
    public class Node {

        private StateObservation state;
        private Types.ACTIONS executedAction;
        private LinkedList<Types.ACTIONS> actionHistory;
        private LinkedList<Types.ACTIONS> unexploredActions;

        public Node(StateObservation state, LinkedList<Types.ACTIONS> actionHistory, int numActions) {
            this.state = state;
            this.actionHistory = actionHistory;
            unexploredActions = new LinkedList<>();

            for (Types.ACTIONS action : availableActions) {
                unexploredActions.add(action);
            }
        }

        public boolean noRemainingActions() {
            return unexploredActions.isEmpty();
        }

        public StateObservation getCurrentState() {
            return this.state;
        }

        public LinkedList<Types.ACTIONS> getActionHistory() {
            return actionHistory;
        }

        public Types.ACTIONS getUnexploredAction() {
            return unexploredActions.remove();
        }

        public StateObservation getState() {
            return state;
        }

        public void setState(StateObservation state) {
            this.state = state;
        }

        public double getScore() {
            return state.getGameScore();
        }

        public void clear() {
            actionHistory.clear();
            unexploredActions.clear();
            state = null;
            actionHistory = null;
            unexploredActions = null;
        }
    }

}
