package controllers.singlePlayer.KBFEvoMCTS.MCTS;

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
import tools.ElapsedCpuTimer;
import tools.Utils;
import tools.Vector2d;
import java.lang.Math;


public class SingleTreeNode {
    
    private final double HUGE_NEGATIVE = -10000000.0;
    private final double HUGE_POSITIVE = 10000000.0;
    public final int ROLLOUT_DEPTH = 10;
    public final int EXPAND_TREE_DEPTH = 10;
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

    protected double[] bounds = new double[]{HUGE_POSITIVE, HUGE_NEGATIVE};
    public double K = Math.sqrt(2);
    public int iterations = 0;
    public HashMap<Integer, Observation> current_features = new HashMap<>();
    public int ID;
    public SingleTreeNode root;
    
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
        this.root = this.parent;
        if (parent != null) {
            m_depth = parent.m_depth + 1;
            while(this.root.parent != null){
                this.root = this.root.parent;
            }
        } else {
            m_depth = 0;
        }
        this.current_features = getFeatures(state);
        MCTS.weightMatrix.updateMapping(this.current_features, state);
        this.ID = MCTS.ID++;
        
        
    }

    public void mctsSearch(ElapsedCpuTimer elapsedTimer) {

        double avgTimeTaken = 0;
        double acumTimeTaken = 0;
        //double lastTimeTaken = 0 ;
        long remaining = elapsedTimer.remainingTimeMillis();
        int numIters = 0;

        int remainingLimit = 6;

        double delta = 0;
        MCTS.num_evolutions = 0;

        while (remaining > 2 * avgTimeTaken && remaining > remainingLimit) {
            //lastTimeTaken = elapsedTimer.remainingTimeMillis();
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();
            SingleTreeNode selected = treePolicy();

            delta = selected.rollOut();
            backUp(selected, delta);

            numIters++;
            acumTimeTaken += (elapsedTimerIteration.elapsedMillis());

            avgTimeTaken = acumTimeTaken / numIters;
            remaining = elapsedTimer.remainingTimeMillis();
            iterations++;
            
            //System.out.println(elapsedTimerIteration.elapsedMillis() + " --> " + acumTimeTaken + " (" + remaining + ")");
            //MCTS.LOGGER.log(Level.INFO, "LastTimeTaken: "+(lastTimeTaken-elapsedTimer.remainingTimeMillis()));
        }

        MCTS.LOGGER.log(Level.ERROR, "Iterations: " + iterations + ", Evolved: " + MCTS.num_evolutions+", Average time: "+String.format("%.2f", avgTimeTaken)+",Cumulative time: "+ String.format("%.2f", acumTimeTaken));
      
        MCTS.LOGGER.log(Level.INFO, "Average time:" + avgTimeTaken + ",Cumulative time:"+acumTimeTaken);
        //if(remaining < 6){
        //    MCTS.LOGGER.log(Level.INFO, "Remaining: "+remaining+", Average time:" + avgTimeTaken);  
        //}
        /*
        System.out.println("Cumulative time:"+acumTimeTaken);
        System.out.println("Highest delta: "+delta);
         */
    }

    public SingleTreeNode treePolicy() {

        SingleTreeNode cur = this;
        while (!cur.state.isGameOver() && cur.m_depth < EXPAND_TREE_DEPTH) {
            if (cur.notFullyExpanded()) {
                return cur.expand();
            } else {
                SingleTreeNode next = cur.uct();
                //SingleTreeNode next = cur.egreedy();
                cur = next;
            }
            
        }
        MCTS.LOGGER.log(Level.INFO, "Finished expanding at node: "+cur.ID);
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
        MCTS.LOGGER.log(Level.ERROR, "\nCalculating UCT");
        MCTS.LOGGER.log(Level.ERROR, "ID: "+this.ID+", Min: "+bounds[0]+", Max: "+bounds[1]);
        int i = 0;
        for (SingleTreeNode child : this.children) {
            double hvVal = child.totValue;
            double childValue = hvVal / (child.nVisits + this.epsilon);
            
            childValue = Utils.normalise(childValue, bounds[0], bounds[1]);
            
            MCTS.LOGGER.log(Level.ERROR, Util.printAction(actions[i]));
            i += 1;
            double n = Math.sqrt(Math.log(this.nVisits + 1) / (child.nVisits + this.epsilon));
            MCTS.LOGGER.log(Level.ERROR, "tot: "+child.totValue+", visits:"+child.nVisits+", Q: "+childValue+ ", N:"+n);
            double uctValue = childValue
                    + K * Math.sqrt(Math.log(this.nVisits + 1) / (child.nVisits + this.epsilon));
            MCTS.LOGGER.log(Level.ERROR, "uct: "+uctValue);
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
        MCTS.LOGGER.log(Level.ERROR, "best value: "+bestValue);
        

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
        int thisDepth = 0;
        
        MCTS.LOGGER.log(Level.INFO, "OLD matrix:");
        //MCTS.weightMatrix.printMatrix();
        ArrayList<Integer> chosenActions = new ArrayList();
        
        // get a new mutated weight matrix 
        WeightMatrix mutated_weightmatrix = MCTS.weightMatrix.getMutatedMatrix();
        
        MCTS.LOGGER.log(Level.INFO, "NEW mutated matrix:");
        //mutated_weightmatrix.printMatrix();

        while (!finishRollout(rollerState, thisDepth)) {

            // get new features from this state
            queryState(rollerState);

            // insert new weights in the matrix for any new features  
            // which showed up on this new state
            mutated_weightmatrix.updateMapping(this.current_features, rollerState);
            
            // use mutated matrix to calculate next action for the rollout
            int action = calculateAction(mutated_weightmatrix);
            //int action = m_rnd.nextInt(num_actions);
            chosenActions.add(action);
            
            
            rollerState.advance(actions[action]);
            thisDepth++;
        }
        
        String chosenActionsLog = "Chosen: ";
        for(Integer i: chosenActions){
            chosenActionsLog += Util.printAction(actions[i]);
        }
        MCTS.LOGGER.log(Level.ERROR, chosenActionsLog);

        double newScore = value(rollerState);
        double delta_r = newScore - this.root.state.getGameScore();
        

        double delta_z = getKnowledgeChange(rollerState);
        double delta_d = dsChange(rollerState);
        
        double delta_final = 0;
        
        if (delta_r != 0) {
            delta_final = delta_r;
            MCTS.LOGGER.log(Level.INFO, "Using score ΔR: " + delta_final);
        } else {
            delta_final = (0.66 * delta_z) + (0.33 * delta_d);
            MCTS.LOGGER.log(Level.INFO, String.format("Using score ΔZ+ΔD: %.3f", delta_final));

        }

        // if the resulting delta gives best fitness, save the mutated matrix
        // KB FastEVo approach
        if (delta_final >= MCTS.weightMatrix.fitness) {
            mutated_weightmatrix.fitness = delta_final;
            MCTS.weightMatrix = mutated_weightmatrix;
            MCTS.num_evolutions += 1;
        }
        
        // New approach
        /*mutated_weightmatrix.fitness = newScore;//Utils.noise(delta, this.epsilon, this.m_rnd.nextDouble()); 
        MCTS.matrix_collection.add(mutated_weightmatrix.fitness, mutated_weightmatrix);*/

        if (Double.compare(delta_final, bounds[0]) < 0) {
            bounds[0] = delta_final;
        }

        if (Double.compare(delta_final, bounds[1]) > 0) {
            bounds[1] = delta_final;
        }
        MCTS.LOGGER.log(Level.INFO, "ID: "+this.ID+", Bounds: "+bounds[0]+", "+bounds[1]);

        return delta_final;
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
            if (result < n.bounds[0]) {
                n.bounds[0] = result;
            }
            if (result > n.bounds[1]) {
                n.bounds[1] = result;
            }
            n = n.parent;
        }
    }
    
    public int getNextAction(){
        //return bestAction();
        return mostVisitedAction();
    }

    public int mostVisitedAction() {
        int selected = -1;
        double bestValue = -Double.MAX_VALUE;
        boolean allEqual = true;
        double first = -1;
        
        String log = "";
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
                log += String.format(Util.printAction(actions[i])+"%.2f, %.2f; ", childValue, children[i].totValue);
            }
        }

        if (selected == -1) {
            System.out.println("Unexpected selection!");
            selected = 0;
        } else if (allEqual) {
            //If all are equal, we opt to choose for the one with the best Q.
            selected = bestAction();
        }
        MCTS.LOGGER.log(Level.ERROR, log);
        MCTS.LOGGER.log(Level.ERROR, "Selected: "+Util.printAction(actions[selected]));
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
        this.current_features.clear();
        HashMap<Integer, Observation> features = getFeatures(stateObs);
        this.current_features = features;
    }
    
    private HashMap<Integer, Observation> getFeatures(StateObservation stateObs) {
        HashMap<Integer, Observation> features = new HashMap();
        Vector2d playerPos= stateObs.getAvatarPosition();
        MCTS.LOGGER.log(Level.INFO, "Player: "+playerPos.x+","+playerPos.y);

        //MCTS.LOGGER.log(Level.INFO,"NPCS:");
        probeNearest(stateObs.getNPCPositions(playerPos), features);

        //MCTS.LOGGER.log(Level.INFO,"Movable:");
        probeNearest(stateObs.getMovablePositions(playerPos), features);

        //MCTS.LOGGER.log(Level.INFO,"Resources:");
        probeNearest(stateObs.getResourcesPositions(playerPos), features);

        //MCTS.LOGGER.log(Level.INFO,"Portals:");
        probeNearest(stateObs.getPortalsPositions(playerPos), features);

        return features;
    }
    
    private void probeNearest(ArrayList<Observation>[] observationLists, HashMap<Integer, Observation> features){
        if (observationLists != null) {
            for (ArrayList<Observation> list : observationLists) {
                if (!list.isEmpty() && list.get(0).sqDist > 0) {
                   Observation obs = list.get(0);
                    features.put(obs.itype, obs);
                    MCTS.LOGGER.log(Level.INFO, "Category:"+obs.category+", ID: "+obs.obsID+", iType:"+obs.itype+", Qtd: "+list.size()+", Dist: "+obs.sqDist);
                }
            }
        }
    }
    
    private HashMap<Integer, Observation> getUpdatedFeatures(StateObservation stateObs, HashMap<Integer, Observation> features) {
        HashMap<Integer, Observation> updatedFeatures = new HashMap();
        HashMap<Integer, Observation> allDistances = getAllDistances(stateObs);
        
        for(Integer typeId: features.keySet()){
            Observation obs = allDistances.get(features.get(typeId).obsID);
            if(obs != null){
                updatedFeatures.put(typeId, obs);
            }
        }
        return updatedFeatures;
    }
    
    private HashMap<Integer, Observation> getAllDistances(StateObservation stateObs) {
        HashMap<Integer, Observation> allDistances = new HashMap();
        Vector2d playerPos = stateObs.getAvatarPosition();
        probeAll(stateObs.getNPCPositions(playerPos), allDistances);
        probeAll(stateObs.getMovablePositions(playerPos), allDistances);
        probeAll(stateObs.getResourcesPositions(playerPos), allDistances);
        probeAll(stateObs.getPortalsPositions(playerPos), allDistances);
        return allDistances;
    }
    
    private void probeAll(ArrayList<Observation>[] observationLists, HashMap<Integer, Observation> features){
        if (observationLists != null) {
            for (ArrayList<Observation> list : observationLists) {
                for(Observation obs: list){
                    features.put(obs.obsID, obs);
                }
            }
        }
    }

    public int calculateAction(WeightMatrix weightMatrix) {

        double[] strenght = new double[num_actions];
        double stronghest = HUGE_NEGATIVE;
        double weakest = HUGE_POSITIVE;

        for (int action_id = 0; action_id < num_actions; action_id++) {
            strenght[action_id] = 0;
            HashMap<Integer, Double> currentMap = weightMatrix.actionHashMap[action_id];
            for (Integer feature_id : this.current_features.keySet()) {
                Observation obs = this.current_features.get(feature_id);
                double euDist = Util.calculateGridDistance(obs.position, obs.reference, this.state.getBlockSize());
                //strenght[action_id] += currentMap.get(feature_id) * Math.sqrt(this.current_features.get(feature_id).sqDist);
                strenght[action_id] += currentMap.get(feature_id) * euDist;

            }
            /*if(strenght[action_id] > stronghest)
                stronghest = strenght[action_id];
            if(strenght[action_id] < weakest)
                weakest = strenght[action_id];*/
        }
        
        MCTS.LOGGER.log(Level.WARN, "\nCalculating Actions... ");
        for (int action_id = 0; action_id < num_actions; action_id++) {
            strenght[action_id] = Utils.normalise(strenght[action_id], weakest, stronghest);
            MCTS.LOGGER.log(Level.WARN, "Strenght action "+action_id+": "+strenght[action_id]);
        }  
           
        //double[] mystrenght = {215.696, 215.696, 210.19327911886418, 253.05181801364353};
        //softmax(mystrenght, 4);
        
        return Util.softmax(strenght, num_actions);
        
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
            if (this.current_features.containsKey(e.passiveTypeId) ||
                    this.current_features.containsKey(e.activeTypeId)) {
                
                Integer event_id = Util.getCantorPairingId(e.activeTypeId, e.passiveTypeId);
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
            //MCTS.LOGGER.log(Level.INFO, "Feature: "+id);
            int Zi0 = MCTS.knowledgeBase.getOcurrences(id);
            int Zif = Zi0 + eventsHashMap.get(id);
            if (Zi0 == 0) {
                //MCTS.LOGGER.log(Level.INFO, "knowledgeChange += Zif = " + Zif);
                // Ki = Zif
                knowledgeChange += Zif;
            } else {
                //MCTS.LOGGER.log(Level.INFO, "knowledgeChange += (Zif / (double) Zi0) - 1 = (" +Zif+" / "+Zi0+")-1");
                //Ki = Zif/Zi0 - 1
                knowledgeChange += (Zif  / (double) Zi0) - 1;
            }

        }
        //MCTS.LOGGER.log(Level.WARN, "KnowledgeChange: "+knowledgeChange);
        return knowledgeChange;
    }

    private double dsChange(StateObservation stateObs) {
        double delta_d = 0;
        HashMap<Integer, Observation> Di_0 = getFeatures(this.parent.state);
        HashMap<Integer, Observation> Di_f = getUpdatedFeatures(stateObs, Di_0);
        double blockSize = stateObs.getBlockSize();
    
        MCTS.LOGGER.log(Level.INFO, "\nCalculating DistanceChange");
        for (Integer feature_id : Di_0.keySet()) {
            
            double Di_0_test = Util.calculateGridDistance(Di_0.get(feature_id).position, Di_0.get(feature_id).reference, blockSize);
            //double Di_f_test = calculateDistance(Di_f.get(feature_id).position, Di_f.get(feature_id).reference, blockSize);
   
            //MCTS.LOGGER.log(Level.INFO, "feature_id: "+feature_id+", "+"Di_0: "+Di_0.get(feature_id).sqDist+", "+"Di_f: "+Di_f.get(feature_id).sqDist);

            int occurrences = MCTS.knowledgeBase.getOcurrences(feature_id);
            double avg_scoreChange = MCTS.knowledgeBase.getAvgScoreChange(feature_id);

            if (occurrences == 0
                    || (Di_0.get(feature_id).sqDist > 0 && avg_scoreChange > 0)) {

                Observation obs_0 = Di_0.get(feature_id);
                Observation obs_f = Di_f.get(feature_id);
                int Di_0_obsID = Di_0.get(feature_id).obsID;
                double Di_0_euDist = Util.calculateGridDistance(obs_0.position, obs_0.reference, blockSize);
                MCTS.LOGGER.log(Level.INFO, Di_0_obsID+", "+feature_id+": "+Di_0_euDist);
                
                double Di_f_euDist = 0;
                String Di_f_obsID = "null";
                if(obs_f != null){
                    Di_f_obsID = String.valueOf(Di_f.get(feature_id).obsID);
                    Di_f_euDist = Util.calculateGridDistance(obs_f.position, obs_f.reference, blockSize);
                }
                MCTS.LOGGER.log(Level.INFO, Di_f_obsID+", "+feature_id+": "+Di_f_euDist);
    
                delta_d += 1 - (Di_f_euDist / (double)Di_0_euDist);
                //MCTS.LOGGER.log(Level.INFO, "feature_id: "+feature_id+", "+"Di_0: "+Di_0.get(feature_id).sqDist+", "+"Di_f: "+Di_fFinal);
            } else {
                delta_d += 0;
            }

        }
        MCTS.LOGGER.log(Level.INFO, "DistanceChange: "+delta_d);
        //if(delta_d < 0)
        //    delta_d = 0;
        return delta_d;
    }
    

    
    
    
      
}
