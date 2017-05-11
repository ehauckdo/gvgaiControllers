package tracks.multiPlayer.ehauckdo.MCTS;

import core.game.Event;
import core.game.Observation;
import core.game.StateObservation;
import java.util.Random;

import core.game.StateObservationMulti;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import ontology.Types;
import org.apache.log4j.Level;
import tools.ElapsedCpuTimer;
import tools.Utils;
import tools.Vector2d;

public class TreeNode
{
    private final double HUGE_NEGATIVE = -10000000.0;
    private final double HUGE_POSITIVE =  10000000.0;
    public double epsilon = 1e-6;
    public double egreedyEpsilon = 0.05;
    public StateObservationMulti state;
    public TreeNode parent;
    public TreeNode[] children;
    public double totValue;
    public int nVisits;
    public Random m_rnd;
    private int m_depth;
    protected double[] bounds = new double[]{Double.MAX_VALUE, -Double.MAX_VALUE};

    int[] NUM_ACTIONS;
    public Types.ACTIONS[][] actions;
    public int ROLLOUT_DEPTH = 10;
    public final int EXPAND_TREE_DEPTH = 10;
    public double K = Math.sqrt(2);
    public int id, oppID, no_players;
    
    public HashMap<Integer, Observation> current_features = new HashMap<>();

    public TreeNode(Random rnd, int[] NUM_ACTIONS, Types.ACTIONS[][] actions, int id, int oppID, int no_players) {
        this(null, null, rnd, NUM_ACTIONS, actions, id, oppID, no_players);
    }

    public static int totalIters = 0;

    public TreeNode(StateObservationMulti state, TreeNode parent, Random rnd, int[] NUM_ACTIONS, Types.ACTIONS[][] actions, int id, int oppID, int no_players) {
        this.state = state;
        this.parent = parent;
        this.m_rnd = rnd;
        totValue = 0.0;
        if (parent != null)
            m_depth = parent.m_depth + 1;
        else
            m_depth = 0;

        this.id = id;
        this.oppID = oppID;
        this.no_players = no_players;

        this.NUM_ACTIONS = NUM_ACTIONS.clone();
        children = new TreeNode[NUM_ACTIONS[id]];

        this.actions = actions;
        if(state != null){
            this.current_features = getFeatures(state);
            MCTS.weightMatrix.updateMapping(this.current_features, state);
        }
    }


    public void mctsSearch(ElapsedCpuTimer elapsedTimer) {

        double avgTimeTaken = 0;
        double acumTimeTaken = 0;
        long remaining = elapsedTimer.remainingTimeMillis();
        int numIters = 0;
        MCTS.num_evolutions = 0;

        int remainingLimit = 5;
        //System.out.println(elapsedTimer.remainingTimeMillis());
        while(remaining > 2*avgTimeTaken && remaining > remainingLimit){
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();
            TreeNode selected = treePolicy();
            double delta = selected.rollOut();
            backUp(selected, delta);

            numIters++;
            //System.out.println("+"  + elapsedTimerIteration.elapsedMillis() + "," + remaining);

            acumTimeTaken += (elapsedTimerIteration.elapsedMillis()) ;

            avgTimeTaken  = acumTimeTaken/numIters;
            remaining = elapsedTimer.remainingTimeMillis();
            //System.out.println(elapsedTimerIteration.elapsedMillis() + " --> " + acumTimeTaken + " (" + remaining + ")");
        }
        //System.out.println("-- " + numIters + " -- ( " + avgTimeTaken + ") " + elapsedTimer.remainingTimeMillis() + "," + remaining);
        totalIters = numIters;
        MCTS.LOGGER.log(Level.ERROR, "Iterations: " + numIters + ", Evolved: " + MCTS.num_evolutions+", Average time: "+String.format("%.2f", avgTimeTaken)+",Cumulative time: "+ String.format("%.2f", acumTimeTaken));
      
        //MCTS.LOGGER.log(Level.INFO, "Average time:" + avgTimeTaken + ",Cumulative time:"+acumTimeTaken);
        //if(remaining < 6){
        //    MCTS.LOGGER.log(Level.INFO, "Remaining: "+remaining+", Average time:" + avgTimeTaken);  
        //}

        //ArcadeMachine.performance.add(numIters);
    }

    public TreeNode treePolicy() {

        TreeNode cur = this;

        while (!cur.state.isGameOver() && cur.m_depth < EXPAND_TREE_DEPTH)
        {
            if (cur.notFullyExpanded()) {
                return cur.expand();

            } else {
                TreeNode next = cur.uct();
                //SingleTreeNode next = cur.egreedy();
                cur = next;
            }
        }

        return cur;
    }


    public TreeNode expand() {

        int bestAction = 0;
        double bestValue = -1;

        for (int i = 0; i < children.length; i++) {
            double x = m_rnd.nextDouble();
            if (x > bestValue && children[i] == null) {
                bestAction = i;
                bestValue = x;
            }
        }

        /**
         * Advance state.
         */

        StateObservationMulti nextState = state.copy();

        //need to provide actions for all players to advance the forward model
        Types.ACTIONS[] acts = new Types.ACTIONS[no_players];

        //set this agent's action
        acts[id] = actions[id][bestAction];

        //get actions available to the opponent and assume they will do a random action
        Types.ACTIONS[] oppActions = actions[oppID];
        acts[oppID] = oppActions[new Random().nextInt(oppActions.length)];

        nextState.advance(acts);

        TreeNode tn = new TreeNode(nextState, this, this.m_rnd, NUM_ACTIONS, actions, id, oppID, no_players);
        children[bestAction] = tn;
        return tn;

    }

    public TreeNode uct() {

        TreeNode selected = null;
        double bestValue = -Double.MAX_VALUE;
        for (TreeNode child : this.children)
        {
            double hvVal = child.totValue;
            double childValue =  hvVal / (child.nVisits + this.epsilon);


            childValue = Utils.normalise(childValue, bounds[0], bounds[1]);

            double uctValue = childValue +
                    K * Math.sqrt(Math.log(this.nVisits + 1) / (child.nVisits + this.epsilon));

            // small sampleRandom numbers: break ties in unexpanded nodes
            uctValue = Utils.noise(uctValue, this.epsilon, this.m_rnd.nextDouble());     //break ties randomly

            // small sampleRandom numbers: break ties in unexpanded nodes
            if (uctValue > bestValue) {
                selected = child;
                bestValue = uctValue;
            }
        }

        if (selected == null)
        {
            throw new RuntimeException("Warning! returning null: " + bestValue + " : " + this.children.length);
        }

        return selected;
    }


    public double rollOut()
    {
        StateObservationMulti rollerState = state.copy();
        int thisDepth = this.m_depth;
        
        //MCTS.LOGGER.log(Level.INFO, "OLD matrix:");
        //MCTS.weightMatrix.printMatrix();
        ArrayList<Types.ACTIONS> chosenActions = new ArrayList();
        
        // get a new mutated weight matrix 
        //WeightMatrix mutated_weightmatrix = MCTS.weightMatrix.getMutatedMatrix();
        
       // MCTS.LOGGER.log(Level.INFO, "NEW mutated matrix:");
        //mutated_weightmatrix.printMatrix();

        while (!finishRollout(rollerState,thisDepth)) {
            
            // get new features from this state
            queryState(rollerState);
            
            // insert new weights in the matrix for any new features  
            // which showed up on this new state
            //mutated_weightmatrix.updateMapping(this.current_features, rollerState);

            //random move for all players
            Types.ACTIONS[] acts = new Types.ACTIONS[no_players];
            for (int i = 0; i < no_players; i++) {
                acts[i] = actions[i][m_rnd.nextInt(NUM_ACTIONS[i])];
            }
            
             // use mutated matrix to calculate next action for the rollout
            //int action = calculateAction(mutated_weightmatrix);
            chosenActions.add(acts[0]);

            rollerState.advance(acts);
            thisDepth++;
        }
        
        String chosenActionsLog = "Actions for this Simulation: ";
        for(Types.ACTIONS action: chosenActions){
            chosenActionsLog += Util.printAction(action);
        }
        MCTS.LOGGER.log(Level.ERROR, chosenActionsLog);

        double newScore = value(rollerState);
        double delta_r = newScore - this.parent.state.getGameScore();

        double delta_z = getKnowledgeChange(rollerState);
        double delta_d = dsChange(rollerState);
        
        double delta_final = 0;
        
        if (delta_r != 0) {
            delta_final = delta_r;
            MCTS.LOGGER.log(Level.ERROR, "Using score ΔR: " + delta_final);
        } else {
            delta_final = (0.66 * delta_z) + (0.33 * delta_d);
            MCTS.LOGGER.log(Level.ERROR, String.format("Using score ΔZ+ΔD: %.3f", delta_final));

        }
        
        // if the resulting delta gives best fitness, save the mutated matrix
        // KB FastEVo approach
        //if (delta_final >= MCTS.weightMatrix.fitness) {
        //    mutated_weightmatrix.fitness = delta_final;
        //    MCTS.weightMatrix = mutated_weightmatrix;
        //    MCTS.num_evolutions += 1;
        //}

        if (Double.compare(delta_final, bounds[0]) < 0) {
            bounds[0] = delta_final;
        }

        if (Double.compare(delta_final, bounds[1]) > 0) {
            bounds[1] = delta_final;
        }
        MCTS.LOGGER.log(Level.INFO, /*"ID: "+this.ID+*/", Bounds: "+bounds[0]+", "+bounds[1]);

        return delta_final;
    }

    public double value(StateObservationMulti a_gameState) {

        boolean gameOver = a_gameState.isGameOver();
        Types.WINNER win = a_gameState.getMultiGameWinner()[id];
        double rawScore = a_gameState.getGameScore(id);

        if(gameOver && win == Types.WINNER.PLAYER_LOSES)
            rawScore += HUGE_NEGATIVE;

        if(gameOver && win == Types.WINNER.PLAYER_WINS)
            rawScore += HUGE_POSITIVE;

        return rawScore;
    }

    public boolean finishRollout(StateObservationMulti rollerState, int depth)
    {
        if(depth >= ROLLOUT_DEPTH)      //rollout end condition.
            return true;

        if(rollerState.isGameOver())               //end of game
            return true;

        return false;
    }

    public void backUp(TreeNode node, double result)
    {
        TreeNode n = node;
        while(n != null)
        {
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

        //String log = "";
        for (int i=0; i<children.length; i++) {

            if(children[i] != null)
            {
                if(first == -1)
                    first = children[i].nVisits;
                else if(first != children[i].nVisits)
                {
                    allEqual = false;
                }

                double childValue = children[i].nVisits;
                childValue = Utils.noise(childValue, this.epsilon, this.m_rnd.nextDouble());     //break ties randomly
                if (childValue > bestValue) {
                    bestValue = childValue;
                    selected = i;
                }
                //log += String.format(Util.printAction(actions[0][i])+"%.2f, %.2f; ", childValue, children[i].totValue);
            }
        }

        if (selected == -1)
        {
            System.out.println("Unexpected selection!");
            selected = 0;
        }else if(allEqual)
        {
            //If all are equal, we opt to choose for the one with the best Q.
            selected = bestAction();
        }
        //MCTS.LOGGER.log(Level.ERROR, log);
        //MCTS.LOGGER.log(Level.ERROR, "Selected: "+Util.printAction(actions[0][selected]));
        return selected;
    }

    public int bestAction()
    {
        int selected = -1;
        double bestValue = -Double.MAX_VALUE;

        for (int i=0; i<children.length; i++) {

            if(children[i] != null) {
                double childValue = children[i].totValue / (children[i].nVisits + this.epsilon);
                childValue = Utils.noise(childValue, this.epsilon, this.m_rnd.nextDouble());     //break ties randomly
                if (childValue > bestValue) {
                    bestValue = childValue;
                    selected = i;
                }
            }
        }

        if (selected == -1)
        {
            System.out.println("Unexpected selection!");
            selected = 0;
        }

        return selected;
    }


    public boolean notFullyExpanded() {
        for (TreeNode tn : children) {
            if (tn == null) {
                return true;
            }
        }

        return false;
    }
    
    public void queryState(StateObservationMulti stateObs) {
        this.current_features.clear();
        HashMap<Integer, Observation> features = getFeatures(stateObs);
        this.current_features = features;
    }
    
    private HashMap<Integer, Observation> getFeatures(StateObservationMulti stateObs) {
        HashMap<Integer, Observation> features = new HashMap();
        Vector2d playerPos = stateObs.getAvatarPosition();
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
                    MCTS.LOGGER.log(Level.ERROR, "Category:"+obs.category+", ID: "+obs.obsID+", iType:"+obs.itype+", Qtd: "+list.size()+", Dist: "+obs.sqDist);
                }
            }
        }
    }
    
//    public int calculateAction(WeightMatrix weightMatrix) {
//
//        double[] strenght = new double[NUM_ACTIONS[0]];
//        double stronghest = HUGE_NEGATIVE;
//        double weakest = HUGE_POSITIVE;
//
//        for (int action_id = 0; action_id < NUM_ACTIONS[0]; action_id++) {
//            strenght[action_id] = 0;
//            HashMap<Integer, Double> currentMap = weightMatrix.actionHashMap[action_id];
//            for (Integer feature_id : this.current_features.keySet()) {
//                Observation obs = this.current_features.get(feature_id);
//                double euDist = Util.calculateGridDistance(obs.position, obs.reference, this.state.getBlockSize());
//                //strenght[action_id] += currentMap.get(feature_id) * Math.sqrt(this.current_features.get(feature_id).sqDist);
//                strenght[action_id] += currentMap.get(feature_id) * euDist;
//
//            }
//            /*if(strenght[action_id] > stronghest)
//                stronghest = strenght[action_id];
//            if(strenght[action_id] < weakest)
//                weakest = strenght[action_id];*/
//        }
//        
//        MCTS.LOGGER.log(Level.WARN, "\nCalculating Actions... ");
//        for (int action_id = 0; action_id < NUM_ACTIONS[0]; action_id++) {
//            strenght[action_id] = Utils.normalise(strenght[action_id], weakest, stronghest);
//            MCTS.LOGGER.log(Level.WARN, "Strenght action "+action_id+": "+strenght[action_id]);
//        }  
//           
//        //double[] mystrenght = {215.696, 215.696, 210.19327911886418, 253.05181801364353};
//        //softmax(mystrenght, 4);
//        
//        return Util.softmax(strenght, NUM_ACTIONS[0]);
//        
//    }
    
    private double getKnowledgeChange(StateObservationMulti newState) {
        
        // there is no new events after simulation
        if (this.state.getEventsHistory().size() == newState.getEventsHistory().size()) {
            return 0;
        }

        // get new events from simulation
        ArrayList<Event> newEvents = mapNewEvents(this.state, newState);

        double delta_z = calculateKnowledgeChange(newEvents);
        
        // update knowledge base with new events
        double scoreChange = this.state.getGameScore() - newState.getGameScore();
        for(Event e: newEvents){
            MCTS.LOGGER.log(Level.ERROR, "event added, Active Type: " + e.activeTypeId + ", Passive Type: " + e.passiveTypeId);
            MCTS.knowledgeBase.add(e.activeTypeId, e.passiveTypeId, scoreChange);
        }
        
        return delta_z;
    }

    private ArrayList<Event> mapNewEvents(StateObservationMulti oldState, StateObservationMulti newState) {
        
        ArrayList<Event> eventsList = new ArrayList();
        int new_events = newState.getEventsHistory().size() - oldState.getEventsHistory().size();
        
        Iterator<Event> events = newState.getEventsHistory().descendingIterator();
        for (int i = 0; i < new_events; i++) {
            Event e = events.next();
      
            // check if the sprite collided with is one of those that we keep
            // track of for calculating ΔZ and ΔD
            if (this.current_features.containsKey(e.passiveTypeId) ||
                    this.current_features.containsKey(e.activeTypeId)) {      
                eventsList.add(e);
            }
        }
        return eventsList;
    }

    private double calculateKnowledgeChange(ArrayList<Event> newEvents) {

        HashMap<Integer, Integer> newEventsHashMap = new HashMap();
        // Map new events
        for(Event e: newEvents){
            Integer event_id = Util.getCantorPairingId(e.activeTypeId, e.passiveTypeId);
            Integer occurrences = newEventsHashMap.get(event_id);
            if (occurrences == null) {
                newEventsHashMap.put(event_id, 1);
            } else {
                newEventsHashMap.put(event_id, occurrences + 1);
            }
        }
        
        double knowledgeChange = 0;

        //Compare with previous events from KB
        MCTS.LOGGER.log(Level.INFO, "Calculating KnoweldgeChange");
        for (Integer id : newEventsHashMap.keySet()) {
            MCTS.LOGGER.log(Level.INFO, "Feature: "+id);
            int Zi0 = MCTS.knowledgeBase.getOcurrences(id);
            int Zif = Zi0 + newEventsHashMap.get(id);
            if (Zi0 == 0) {
                MCTS.LOGGER.log(Level.INFO, "knowledgeChange += Zif -> " + Zif);
                knowledgeChange += Zif;
            } else {
                MCTS.LOGGER.log(Level.INFO, "knowledgeChange += (Zif/(double) Zi0)-1 -> (" +Zif+" / "+Zi0+")-1");
                knowledgeChange += (Zif  / (double) Zi0) - 1;
            }
        }
        //MCTS.LOGGER.log(Level.WARN, "KnowledgeChange: "+knowledgeChange);
        return knowledgeChange;
    }
    
    private double dsChange(StateObservationMulti stateObs) {
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
        if(delta_d < 0)
            delta_d = 0;
        return delta_d;
    }
    
    private HashMap<Integer, Observation> getUpdatedFeatures(StateObservationMulti stateObs, HashMap<Integer, Observation> features) {
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
    
    private HashMap<Integer, Observation> getAllDistances(StateObservationMulti stateObs) {
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
}
