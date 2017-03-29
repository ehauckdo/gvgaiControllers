package controllers.singlePlayer.ehauckdo.KBEvoMCTS;

import core.game.Event;
import java.util.Random;
import core.game.Observation;
import core.game.StateObservation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;
import ontology.Types;
import tools.ElapsedCpuTimer;
import tools.Utils;
import tools.Vector2d;
import util.Util;

public class SingleTreeNode {

    private final double HUGE_NEGATIVE = -10000000.0;
    private final double HUGE_POSITIVE = 10000000.0;
    public final int ROLLOUT_DEPTH = 20;
    public double epsilon = 1e-6;
    public double egreedyEpsilon = 0.05;
    public Random m_rnd;

    public StateObservation state;
    public SingleTreeNode parent;
    public SingleTreeNode[] children;
    public Types.ACTIONS[] actions;

    public double totValue;
    public int nVisits;
    private int m_depth;
    public int num_actions;

    protected double[] bounds = new double[]{Double.MAX_VALUE, -Double.MAX_VALUE};
    public double K = Math.sqrt(2);
    public int iterations = 0;

    public SingleTreeNode(StateObservation stateObs, Random rnd, int num_actions, Types.ACTIONS[] actions) {
        this(stateObs, null, rnd, num_actions, actions);
    }

    public SingleTreeNode(StateObservation state, SingleTreeNode parent, Random rnd, int num_actions, Types.ACTIONS[] actions) {
        this.state = state;
        this.parent = parent;
        this.num_actions = num_actions;
        this.m_rnd = rnd;
        this.actions = actions;
        children = new SingleTreeNode[num_actions];
        totValue = 0.0;
        if (parent != null) {
            m_depth = parent.m_depth + 1;
        } else {
            m_depth = 0;
        }

    }

    public void mctsSearch(ElapsedCpuTimer elapsedTimer) {

        double avgTimeTaken = 0;
        double acumTimeTaken = 0;
        long remaining = elapsedTimer.remainingTimeMillis();
        int numIters = 0;

        int remainingLimit = 5;

        double delta = 0;
        MCTS.num_evolutions = 0;

        while (remaining > 2 * avgTimeTaken && remaining > remainingLimit) {
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();
            SingleTreeNode selected = treePolicy();

            delta = selected.rollOut();
            backUp(selected, delta);

            numIters++;
            acumTimeTaken += (elapsedTimerIteration.elapsedMillis());

            avgTimeTaken = acumTimeTaken / numIters;
            remaining = elapsedTimer.remainingTimeMillis();
            //System.out.println(elapsedTimerIteration.elapsedMillis() + " --> " + acumTimeTaken + " (" + remaining + ")");
            iterations++;
        }

        System.out.println("Iterations: " + iterations + ", Evolved: " + MCTS.num_evolutions);
        /*System.out.println("Selected MAtrix:");
        MCTS.weightMatrix.printMatrix();*/
        /*for(weightMatrix matrix : MCTS.mutated_matrixList){
            matrix.printMatrix();
            System.out.println("");
        }
        System.exit(0);*/

        /*
        System.out.println("Average time:" + avgTimeTaken);
        System.out.println("Cumulative time:"+acumTimeTaken);
        System.out.println("Highest delta: "+delta);
         */
    }

    public SingleTreeNode treePolicy() {

        SingleTreeNode cur = this;

        while (!cur.state.isGameOver() && cur.m_depth < ROLLOUT_DEPTH) {
            if (cur.notFullyExpanded()) {
                return cur.expand();

            } else {
                SingleTreeNode next = cur.uct();
                //SingleTreeNode next = cur.egreedy();
                cur = next;
            }
        }

        return cur;
    }

    public SingleTreeNode expand() {

        int bestAction = 0;
        double bestValue = -1;

        for (int i = 0; i < children.length; i++) {
            double x = m_rnd.nextDouble();
            if (x > bestValue && children[i] == null) {
                bestAction = i;
                bestValue = x;
            }
        }

        StateObservation nextState = state.copy();
        nextState.advance(actions[bestAction]);

        SingleTreeNode tn = new SingleTreeNode(nextState, this, this.m_rnd, num_actions, actions);
        children[bestAction] = tn;
        return tn;

    }

    public SingleTreeNode uct() {

        SingleTreeNode selected = null;
        double bestValue = -Double.MAX_VALUE;
        for (SingleTreeNode child : this.children) {
            double hvVal = child.totValue;
            double childValue = hvVal / (child.nVisits + this.epsilon);

            childValue = Utils.normalise(childValue, bounds[0], bounds[1]);

            double uctValue = childValue
                    + K * Math.sqrt(Math.log(this.nVisits + 1) / (child.nVisits + this.epsilon));

            // small sampleRandom numbers: break ties in unexpanded nodes
            uctValue = Utils.noise(uctValue, this.epsilon, this.m_rnd.nextDouble());     //break ties randomly

            // small sampleRandom numbers: break ties in unexpanded nodes
            if (uctValue > bestValue) {
                selected = child;
                bestValue = uctValue;
            }
        }

        if (selected == null) {
            throw new RuntimeException("Warning! returning null: " + bestValue + " : " + this.children.length);
        }

        return selected;
    }

    public SingleTreeNode egreedy() {

        SingleTreeNode selected = null;

        if (m_rnd.nextDouble() < egreedyEpsilon) {
            //Choose randomly
            int selectedIdx = m_rnd.nextInt(children.length);
            selected = this.children[selectedIdx];

        } else {
            //pick the best Q.
            double bestValue = -Double.MAX_VALUE;
            for (SingleTreeNode child : this.children) {
                double hvVal = child.totValue;
                hvVal = Utils.noise(hvVal, this.epsilon, this.m_rnd.nextDouble());     //break ties randomly
                // small sampleRandom numbers: break ties in unexpanded nodes
                if (hvVal > bestValue) {
                    selected = child;
                    bestValue = hvVal;
                }
            }

        }

        if (selected == null) {
            throw new RuntimeException("Warning! returning null: " + this.children.length);
        }

        return selected;
    }

    public double rollOut() {
        StateObservation rollerState = state.copy();
        int thisDepth = this.m_depth;

        // get a new mutated weight matrix 
        weightMatrix mutated_weightmatrix = MCTS.weightMatrix.getMutatedMatrix();

        while (!finishRollout(rollerState, thisDepth)) {

            // get new features from this state
            queryState(rollerState);

            // insert new weights in the matrix for any new features  
            // which showed up on this new state
            mutated_weightmatrix.updateMapping(MCTS.current_features);

            // use mutated matrix to calculate next action for the rollout
            int action = calculateAction(mutated_weightmatrix);

            //int action = m_rnd.nextInt(num_actions);
            rollerState.advance(actions[action]);
            thisDepth++;
        }

        double delta_r = value(rollerState) - this.state.getGameScore();

        double delta_z = getKnowledgeChange(rollerState);
        //System.out.println("KnowledgeChange: " + delta_z);

        double delta_d = dsChange(rollerState);
        //System.out.println("DistanceChange: "+ delta_d);

        // if delta is not very low (game over && player lost), save mutated matrix
        if (delta_r > 0) {
            mutated_weightmatrix.fitness = delta_r;//Utils.noise(delta, this.epsilon, this.m_rnd.nextDouble());   
            MCTS.matrix_collection.add(mutated_weightmatrix.fitness, mutated_weightmatrix);
        }
        //System.out.println("Size:"+MCTS.matrix_collection.size());

        // if the resulting delta gives best fitness, save the mutated matrix
        if (delta_r > MCTS.current_bestFitness) {
            MCTS.num_evolutions += 1;
            MCTS.current_bestFitness = delta_r;
            MCTS.weightMatrix = mutated_weightmatrix;
        }

        if (delta_r < bounds[0]) {
            bounds[0] = delta_r;
        }

        if (delta_r > bounds[1]) {
            bounds[1] = delta_r;
        }

        if (delta_r > 0) {
            System.out.println("Using score ΔR: " + delta_r);
            return delta_r;
        } else {
            System.out.println("Using score ΔZ+ΔR: " + (0.66 * delta_z + 0.33 * delta_r));
            return 0.66 * delta_z + 0.33 * delta_r;
        }
    }

    public double value(StateObservation a_gameState) {

        boolean gameOver = a_gameState.isGameOver();
        Types.WINNER win = a_gameState.getGameWinner();
        double rawScore = a_gameState.getGameScore();

        if (gameOver && win == Types.WINNER.PLAYER_LOSES) {
            rawScore += HUGE_NEGATIVE;
        }

        if (gameOver && win == Types.WINNER.PLAYER_WINS) {
            rawScore += HUGE_POSITIVE;
        }

        return rawScore;
    }

    public boolean finishRollout(StateObservation rollerState, int depth) {
        if (depth >= ROLLOUT_DEPTH) //rollout end condition.
        {
            return true;
        }

        if (rollerState.isGameOver()) //end of game
        {
            return true;
        }

        return false;
    }

    public void backUp(SingleTreeNode node, double result) {
        SingleTreeNode n = node;
        while (n != null) {
            n.nVisits++;
            n.totValue += result;
            n = n.parent;
        }
    }

    public int mostVisitedAction() {
        int selected = -1;
        double bestValue = -Double.MAX_VALUE;
        boolean allEqual = true;
        double first = -1;

        for (int i = 0; i < children.length; i++) {

            if (children[i] != null) {
                if (first == -1) {
                    first = children[i].nVisits;
                } else if (first != children[i].nVisits) {
                    allEqual = false;
                }

                double childValue = children[i].nVisits;
                childValue = Utils.noise(childValue, this.epsilon, this.m_rnd.nextDouble());     //break ties randomly
                if (childValue > bestValue) {
                    bestValue = childValue;
                    selected = i;
                }
            }
        }

        if (selected == -1) {
            System.out.println("Unexpected selection!");
            selected = 0;
        } else if (allEqual) {
            //If all are equal, we opt to choose for the one with the best Q.
            selected = bestAction();
        }
        return selected;
    }

    public int bestAction() {
        int selected = -1;
        double bestValue = -Double.MAX_VALUE;

        for (int i = 0; i < children.length; i++) {

            if (children[i] != null) {
                double childValue = children[i].totValue / (children[i].nVisits + this.epsilon);
                childValue = Utils.noise(childValue, this.epsilon, this.m_rnd.nextDouble());     //break ties randomly
                if (childValue > bestValue) {
                    bestValue = childValue;
                    selected = i;
                }
            }
        }

        if (selected == -1) {
            System.out.println("Unexpected selection!");
            selected = 0;
        }

        return selected;
    }

    public boolean notFullyExpanded() {
        for (SingleTreeNode tn : children) {
            if (tn == null) {
                return true;
            }
        }

        return false;
    }

    public void queryState(StateObservation stateObs) {
        MCTS.current_features.clear();
        HashMap<Integer, Double> features = getFeatures(stateObs);
        MCTS.current_features = features;
    }

    public HashMap<Integer, Double> getFeatures(StateObservation stateObs) {
        HashMap<Integer, Double> features = new HashMap();
        Vector2d playerPosition = stateObs.getAvatarPosition();
        ArrayList<Observation>[] observationLists;

        // If there is NPCs on this game
        observationLists = stateObs.getNPCPositions(playerPosition);
        probeObservationList(observationLists, features);

        // If there is Movable on this game
        observationLists = stateObs.getMovablePositions(playerPosition);
        probeObservationList(observationLists, features);

        // If there is Resources on this game
        observationLists = stateObs.getResourcesPositions(playerPosition);
        probeObservationList(observationLists, features);

        // If there is Portals on this game
        observationLists = stateObs.getPortalsPositions(playerPosition);
        probeObservationList(observationLists, features);

        return features;
    }
    
    private void probeObservationList(ArrayList<Observation>[] observationLists, HashMap<Integer, Double> features){
        if (observationLists != null) {
            for (ArrayList<Observation> list : observationLists) {
                if (!list.isEmpty()) {
                    Observation obs = list.get(0);
                    features.put(obs.itype, obs.sqDist);
                    // System.out.println("Category:"+obs.category+", ID: "+obs.obsID+", iType:"+obs.itype+", Qtd: "+list.size());
                }
                break;
            }
        }
    }

    public int calculateAction(weightMatrix weightMatrix) {

        double[] strenght = new double[num_actions];
        double sum = 0;

        for (int action_id = 0; action_id < num_actions; action_id++) {
            strenght[action_id] = 0;
            HashMap<Integer, Double> currentMap = weightMatrix.actionHashMap[action_id];
            for (Integer feature_id : MCTS.current_features.keySet()) {
                strenght[action_id] += currentMap.get(feature_id) * MCTS.current_features.get(feature_id);
            }
        }

        RandomCollection<Integer> rnd = new RandomCollection<>();

        for (int i = 0; i < num_actions; i++) {
            rnd.add(strenght[i] / sum, i);
        }

        return rnd.next();

    }

    private double getKnowledgeChange(StateObservation newState) {
        //System.out.println("checking..." + this.state.getEventsHistory().size() + "," + newState.getEventsHistory().size());

        // there is no new events after simulation
        if (this.state.getEventsHistory().size() == newState.getEventsHistory().size()) {
            return 0;
        }
        //System.out.println("new events!");
        //eventHistory.printEventsInfo();

        // map new events into a hashmap
        HashMap<Integer, Integer> eventsHashMap = mapNewEvents(this.state, newState);

        return calculateKnowledgeChange(eventsHashMap);

    }

    private HashMap<Integer, Integer> mapNewEvents(StateObservation oldState, StateObservation newState) {

        HashMap<Integer, Integer> eventsHashMap = new HashMap();
        double scoreChange = this.state.getGameScore() - newState.getGameScore();

        int new_events = newState.getEventsHistory().size() - oldState.getEventsHistory().size();

        Iterator<Event> events = newState.getEventsHistory().descendingIterator();
        for (int i = 0; i < new_events; i++) {
            Event e = events.next();

            // TODO: verify if current event has activeType as player or projectile
            if (MCTS.current_features.containsKey(e.passiveTypeId)) {
                Integer event_id = util.Util.getCantorPairingId(e.activeTypeId, e.passiveTypeId);
                Integer occurrences = eventsHashMap.get(event_id);
                //System.out.println("event added, EventID: " + event_id + "Active Type: " + e.activeTypeId + ", Passive Type: " + e.passiveTypeId);
                if (occurrences == null) {
                    eventsHashMap.put(event_id, 1);
                } else {
                    eventsHashMap.put(event_id, occurrences + 1);
                }

                //int Zi0 = this.eventHistory.getOcurrences(e.activeTypeId, e.passiveTypeId);
                //System.out.println("Zi0: " + Zi0);
                // Add events to KB
                MCTS.knowledgeBase.add(e.activeTypeId, e.passiveTypeId, scoreChange);
                //this.knowledgeBase.add(e.activeTypeId, e.passiveTypeId, scoreChange);
            }
        }
        return eventsHashMap;
    }

    private double calculateKnowledgeChange(HashMap<Integer, Integer> eventsHashMap) {
        double knowledgeChange = 0;

        //Compare with previous events
        //System.out.println("Calculating Score Change");
        for (Integer id : eventsHashMap.keySet()) {
            int Zi0 = MCTS.knowledgeBase.getOcurrences(id);
            int Zif = Zi0 + eventsHashMap.get(id);
            if (Zi0 == 0) {
                //System.out.println("knowledgeChange += Zif = " + Zif);
                // Ki = Zif
                knowledgeChange += Zif;
            } else {
                //System.out.println("knowledgeChange += (Zif / (double) Zi0) - 1 = " + (((Zif + Zi0) / (double) Zi0) - 1));
                // Ki = Kif/Ki0 - 1
                knowledgeChange += ((Zif + Zi0) / (double) Zi0) - 1;
            }

        }
        return knowledgeChange;
    }

    private double dsChange(StateObservation stateObs) {
        double delta_d = 0;
        HashMap<Integer, Double> Di_0 = getFeatures(this.state);
        HashMap<Integer, Double> Di_f = getFeatures(stateObs);

        /*System.out.println("MCTS.current_features:");
        for(Integer feature_id: MCTS.current_features.keySet()){
            System.out.println(feature_id+": "+MCTS.current_features.get(feature_id));
        }
        System.out.println("Di_0 features:");
        for(Integer feature_id: Di_0.keySet()){
            System.out.println(feature_id+": "+Di_0.get(feature_id));
        }*/
        for (Integer feature_id : Di_0.keySet()) {
            //System.out.println("feature_id: "+feature_id+", "+"Di_0: "+Di_0.get(feature_id)+", "+"Di_f: "+Di_f.get(feature_id));

            // this sprite no longer exists after rollout, ignore it
            if (Di_f.get(feature_id) == null) {
                continue;
            }

            int occurrences = MCTS.knowledgeBase.getOcurrences(feature_id);
            double avg_scoreChange = MCTS.knowledgeBase.getAvgScoreChange(feature_id);

            if (occurrences == 0
                    || (Di_0.get(feature_id) > 0 && avg_scoreChange > 0)) {

                delta_d += 1 - (Di_f.get(feature_id) / (double) Di_0.get(feature_id));
                //System.out.println("feature_id: "+feature_id+", "+"Di_0: "+Di_0.get(feature_id)+", "+"Di_f: "+Di_f.get(feature_id));

                //System.out.println("Not zero! :"+delta_d);
            } else {
                delta_d += 0;
            }

        }

        return delta_d;
    }
    

    /*private class EventHistory {

        public HashMap<Integer, Integer> eventsHistory = new HashMap();

        public EventHistory() {
        }

        public EventHistory(StateObservation stateObs) {
            TreeSet<Event> stateObs_events = stateObs.getEventsHistory();
            Iterator<Event> events_iterator = stateObs_events.iterator();

            while (events_iterator.hasNext()) {
                Event e = events_iterator.next();
                // TODO: verify if current event has activeType as player or projectile
                if (MCTS.current_features.containsKey(e.passiveTypeId)) {
                    this.addOcurrence(e.activeTypeId, e.passiveTypeId);
                }
            }
        }

        public final void addOcurrence(int activeTypeId, int passiveTypeId) {
            int event_id = Util.getCantorPairingId(activeTypeId, passiveTypeId);
            Integer occurrences = eventsHistory.get(event_id);
            if (occurrences == null) {
                eventsHistory.put(event_id, 1);
            } else {
                eventsHistory.put(event_id, occurrences + 1);
            }
        }

        public final int getOcurrences(int activeTypeId, int passiveTypeId) {
            int event_id = Util.getCantorPairingId(activeTypeId, passiveTypeId);
            Integer occurrences = eventsHistory.get(event_id);
            return occurrences == null ? 0 : occurrences;
        }

        public final int getOcurrences(int event_id) {
            Integer occurrences = eventsHistory.get(event_id);
            return occurrences == null ? 0 : occurrences;
        }

        public final int getTotalNumberEvents() {
            int number_events = 0;
            for (Integer occurrences : eventsHistory.values()) {
                number_events += occurrences;
            }
            return number_events;
        }

        public void printEventsInfo() {
            for (Integer i : eventsHistory.keySet()) {
                System.out.println("Event ID: " + i);
                System.out.println("Occurrences: " + eventsHistory.get(i));
            }
        }

    }*/

}
