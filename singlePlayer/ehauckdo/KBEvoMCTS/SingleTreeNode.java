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
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
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

        MCTS.LOGGER.log(Level.INFO, "Iterations: " + iterations + ", Evolved: " + MCTS.num_evolutions);

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
        
        //MCTS.LOGGER.log(Level.INFO, "OLD matrix:");
        //MCTS.weightMatrix.printMatrix();
        
        // get a new mutated weight matrix 
        weightMatrix mutated_weightmatrix = MCTS.weightMatrix.getMutatedMatrix();
        
        // MCTS.LOGGER.log(Level.INFO, "NEW mutated matrix:");
        //mutated_weightmatrix.printMatrix();

        while (!finishRollout(rollerState, thisDepth)) {

            // get new features from this state
            queryState(rollerState);

            // insert new weights in the matrix for any new features  
            // which showed up on this new state
            mutated_weightmatrix.updateMapping(MCTS.current_features);

            // use mutated matrix to calculate next action for the rollout
            int action = calculateAction(mutated_weightmatrix);
            
            //int action = m_rnd.nextInt(num_actions);
            
            //printAction(actions[action]);

            //int action = m_rnd.nextInt(num_actions);
            rollerState.advance(actions[action]);
            thisDepth++;
        }

        double newScore = value(rollerState);
        double delta_r = newScore - this.state.getGameScore();

        double delta_z = getKnowledgeChange(rollerState);
        double delta_d = dsChange(rollerState);

        // if the resulting delta gives best fitness, save the mutated matrix
        // KB FastEVo approach
        if (newScore > MCTS.current_bestFitness) {
            MCTS.num_evolutions += 1;
            MCTS.current_bestFitness = newScore;
            MCTS.weightMatrix = mutated_weightmatrix;
            MCTS.weightMatrix.fitness = newScore;
        }
        
        // New approach
        /*mutated_weightmatrix.fitness = newScore;//Utils.noise(delta, this.epsilon, this.m_rnd.nextDouble()); 
        MCTS.matrix_collection.add(mutated_weightmatrix.fitness, mutated_weightmatrix);*/

        if (delta_r < bounds[0]) {
            bounds[0] = delta_r;
        }

        if (delta_r > bounds[1]) {
            bounds[1] = delta_r;
        }

        if (delta_r != 0) {
            //MCTS.LOGGER.log(Level.INFO, "Using score ΔR: " + delta_r);
            return delta_r;
        } else {
            //MCTS.LOGGER.log(Level.INFO, "Using score ΔZ+ΔD: " + (0.66 * delta_z + 0.33 * delta_d));
            return (0.66 * delta_z + 0.33 * delta_d);
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
        
        // Update typeIds of player projectiles, this could probably be handled 
        // in a more fashonable way
        observationLists = stateObs.getFromAvatarSpritesPositions();
        if (observationLists != null) {
            for (ArrayList<Observation> list : observationLists) {
                for(Observation obs : list){
                    MCTS.player_projectiles.put(obs.itype, 0);
                }
            }
        }

        return features;
    }
    
    private void probeObservationList(ArrayList<Observation>[] observationLists, HashMap<Integer, Double> features){
        if (observationLists != null) {
            for (ArrayList<Observation> list : observationLists) {
                if (!list.isEmpty()) {
                    Observation obs = list.get(0);
                    features.put(obs.itype, obs.sqDist);
                    //MCTS.LOGGER.log(Level.INFO, "Category:"+obs.category+", ID: "+obs.obsID+", iType:"+obs.itype+", Qtd: "+list.size()+", Dist: "+obs.sqDist);
                }
                break;
            }
        }
    }

    public int calculateAction(weightMatrix weightMatrix) {

        double[] strenght = new double[num_actions];
        double sum = 0;
        int stronghest_id = 0;
        int weakest_id = 0;

        for (int action_id = 0; action_id < num_actions; action_id++) {
            strenght[action_id] = 0;
            HashMap<Integer, Double> currentMap = weightMatrix.actionHashMap[action_id];
            for (Integer feature_id : MCTS.current_features.keySet()) {
                strenght[action_id] += currentMap.get(feature_id) * MCTS.current_features.get(feature_id);
            }
            if(strenght[action_id] >= strenght[stronghest_id]){
                stronghest_id = action_id;
            }
            if(strenght[action_id] <= strenght[weakest_id]){
                weakest_id = action_id;
            }
        }
        
        /*MCTS.LOGGER.log(Level.INFO, "\nCalculating Actions... ");
        for (int action_id = 0; action_id < num_actions; action_id++) {
            MCTS.LOGGER.log(Level.INFO, "Strenght action "+action_id+": "+strenght[action_id]);
        }*/
        
        double stronghest = strenght[stronghest_id];
        double weakest = strenght[weakest_id];
        
        //if(weakest < 0){
        for (int action_id = 0; action_id < num_actions; action_id++) {
            //strenght[action_id] += Math.abs(weakest);
            strenght[action_id] = Utils.normalise(strenght[action_id], weakest, stronghest);
        }
        //}
        /*MCTS.LOGGER.log(Level.INFO, "\nNormalized Actions... ");
        for (int action_id = 0; action_id < num_actions; action_id++) {
            MCTS.LOGGER.log(Level.INFO, "Strenght action "+action_id+": "+strenght[action_id]);
        }*/

        RandomCollection<Integer> rnd = new RandomCollection<>();

        for (int i = 0; i < num_actions; i++) {
            rnd.add(strenght[i], i);
        }

        //return stronghest;
        return rnd.next();

    }

    private double getKnowledgeChange(StateObservation newState) {
        
        // there is no new events after simulation
        if (this.state.getEventsHistory().size() == newState.getEventsHistory().size()) {
            return 0;
        }

        // map new events into a hashmap
        HashMap<Integer, Integer> eventsHashMap = mapNewEvents(this.state, newState);

        return calculateKnowledgeChange(eventsHashMap);

    }

    private HashMap<Integer, Integer> mapNewEvents(StateObservation oldState, StateObservation newState) {
        
        newState.getFromAvatarSpritesPositions();

        HashMap<Integer, Integer> eventsHashMap = new HashMap();
        double scoreChange = this.state.getGameScore() - newState.getGameScore();

        int new_events = newState.getEventsHistory().size() - oldState.getEventsHistory().size();
        
        //MCTS.LOGGER.log(Level.INFO, "\nKB checking... "+new_events+" new events! ");

        Iterator<Event> events = newState.getEventsHistory().descendingIterator();
        for (int i = 0; i < new_events; i++) {
            Event e = events.next();
      
            // check if the sprite collided with is one of those that we keep
            // track of for calculating ΔZ and ΔD
            if (MCTS.current_features.containsKey(e.passiveTypeId) ||
                    MCTS.current_features.containsKey(e.activeTypeId)) {
                
                Integer event_id = util.Util.getCantorPairingId(e.activeTypeId, e.passiveTypeId);
                Integer occurrences = eventsHashMap.get(event_id);
                //MCTS.LOGGER.log(Level.INFO, "event added, EventID: " + event_id + "Active Type: " + e.activeTypeId + ", Passive Type: " + e.passiveTypeId);
                if (occurrences == null) {
                    eventsHashMap.put(event_id, 1);
                } else {
                    eventsHashMap.put(event_id, occurrences + 1);
                }

                // Add events to KB
                MCTS.knowledgeBase.add(e.activeTypeId, e.passiveTypeId, scoreChange);
                //int Zi0 = MCTS.knowledgeBase.getOcurrences(e.activeTypeId, e.passiveTypeId);
                //MCTS.LOGGER.log(Level.INFO, "Zi0: " + Zi0);
            }
        }
        return eventsHashMap;
    }

    private double calculateKnowledgeChange(HashMap<Integer, Integer> eventsHashMap) {
        double knowledgeChange = 0;

        //Compare with previous events
        //MCTS.LOGGER.log(Level.INFO, "Calculating KnoweldgeChange");
        for (Integer id : eventsHashMap.keySet()) {
            int Zi0 = MCTS.knowledgeBase.getOcurrences(id);
            int Zif = Zi0 + eventsHashMap.get(id);
            if (Zi0 == 0) {
                //MCTS.LOGGER.log(Level.INFO, "knowledgeChange += Zif = " + Zif);
                // Ki = Zif
                knowledgeChange += Zif;
            } else {
                //MCTS.LOGGER.log(Level.INFO, "knowledgeChange += (Zif / (double) Zi0) - 1 = " + (((Zif + Zi0) / (double) Zi0) - 1));
                // Ki = Kif/Ki0 - 1
                knowledgeChange += ((Zif + Zi0) / (double) Zi0) - 1;
            }

        }
        //MCTS.LOGGER.log(Level.INFO, "KnowledgeChange: "+knowledgeChange);
        return knowledgeChange;
    }

    private double dsChange(StateObservation stateObs) {
        double delta_d = 0;
        HashMap<Integer, Double> Di_0 = getFeatures(this.state);
        HashMap<Integer, Double> Di_f = getFeatures(stateObs);

        /*MCTS.LOGGER.log(Level.INFO, "MCTS.current_features:");
        for(Integer feature_id: MCTS.current_features.keySet()){
            MCTS.LOGGER.log(Level.INFO, feature_id+": "+MCTS.current_features.get(feature_id));
        }
        MCTS.LOGGER.log(Level.INFO, "Di_0 features:");
        for(Integer feature_id: Di_0.keySet()){
            MCTS.LOGGER.log(Level.INFO, feature_id+": "+Di_0.get(feature_id));
        }*/
        
        //MCTS.LOGGER.log(Level.INFO, "\nCalculating DistanceChange");
        for (Integer feature_id : Di_0.keySet()) {
            //MCTS.LOGGER.log(Level.INFO, "feature_id: "+feature_id+", "+"Di_0: "+Di_0.get(feature_id)+", "+"Di_f: "+Di_f.get(feature_id));

            // this sprite no longer exists after rollout, ignore it
            // this could mean that the avatar hit the sprite, if that's the
            // case, we can just rely on the score change to move the avatar
            if (Di_f.get(feature_id) == null) {
                continue;
            }

            int occurrences = MCTS.knowledgeBase.getOcurrences(feature_id);
            double avg_scoreChange = MCTS.knowledgeBase.getAvgScoreChange(feature_id);

            if (occurrences == 0
                    || (Di_0.get(feature_id) > 0 && avg_scoreChange > 0)) {

                delta_d += 1 - (Di_f.get(feature_id) / (double) Di_0.get(feature_id));
                //MCTS.LOGGER.log(Level.INFO, "feature_id: "+feature_id+", "+"Di_0: "+Di_0.get(feature_id)+", "+"Di_f: "+Di_f.get(feature_id));

            } else {
                delta_d += 0;
            }

        }
        //MCTS.LOGGER.log(Level.INFO, "DistanceChange: "+delta_d);
        return delta_d;
    }
    

    public void printAction(Types.ACTIONS action){
        String character = "";
        if(null != action)
            switch (action) {
            case ACTION_UP:
                character = "↑";
                break;
            case ACTION_DOWN:
                character = "↓";
                break;
            case ACTION_LEFT:
                character = "←";
                break;
            case ACTION_RIGHT:
                character = "→";
                break;
            case ACTION_USE:
                character = "USE";
                break;
            default:
                break;
        }
        MCTS.LOGGER.log(Level.INFO, character);

    }

}
