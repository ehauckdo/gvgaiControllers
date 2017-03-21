package controllers.singlePlayer.ehauckdo.KBEvoMCTS;

import controllers.singlePlayer.ehauckdo.KBEvoMCTS.MCTS;
import controllers.singlePlayer.ehauckdo.KBEvoMCTS.weightMatrix;
import java.util.Random;
import core.game.Observation;
import core.game.StateObservation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import ontology.Types;
import tools.ElapsedCpuTimer;
import tools.Utils;
import tools.Vector2d;

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

        System.out.println("Iterations: " + iterations+", Evolved: "+MCTS.num_evolutions);
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
        
        double delta = value(rollerState);
        
        // update fitness value of the current matrix, add noise to break ties
        mutated_weightmatrix.fitness = Utils.noise(delta, this.epsilon, this.m_rnd.nextDouble());   
        MCTS.matrix_collection.add(mutated_weightmatrix.fitness, mutated_weightmatrix);
        System.out.println("Size:"+MCTS.matrix_collection.size());

        // if the resulting delta gives best fitness, save the mutated matrix
        if (delta > MCTS.current_bestFitness) {
            MCTS.num_evolutions += 1;
            MCTS.current_bestFitness = delta;
            MCTS.weightMatrix = mutated_weightmatrix;   
        }

        if (delta < bounds[0]) {
            bounds[0] = delta;
        }

        if (delta > bounds[1]) {
            bounds[1] = delta;
        }

        return delta;
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
        Vector2d position = stateObs.getAvatarPosition();
        
        MCTS.current_features.clear();

        ArrayList<Observation>[] observationLists = stateObs.getNPCPositions(position);
        
        // If there is NPCs on this game
        if (observationLists != null) {

            for (ArrayList<Observation> list : observationLists) {
                if (!list.isEmpty()) {

                    Observation obs = list.get(0);      
                    /*featureWeight weight = MCTS.weightHashMap.get(obs.itype);
                    if(weight == null)
                        weight = new featureWeight(obs.sqDist);
                    else
                        weight.distance = obs.sqDist;
                    
                    MCTS.weightHashMap.put(obs.itype, weight);*/
                    MCTS.current_features.put(obs.itype, obs.sqDist);
                    //System.out.println("Category:"+obs.category+", ID: "+obs.obsID+", iType:"+obs.itype);

                }
                break;
            }
        }

        // If there is Immovable on this game
        observationLists = stateObs.getImmovablePositions(position);
        if (observationLists != null) {
            //System.out.println("===== "+ observationLists.length +" Immovable Types =====");
            for (ArrayList<Observation> list : observationLists) {
                if (!list.isEmpty()) {

                    Observation obs = list.get(0);
                    /*featureWeight weight = MCTS.weightHashMap.get(obs.itype);
                    if(weight == null)
                        weight = new featureWeight(obs.sqDist);
                    else
                        weight.distance = obs.sqDist;
                    
                    MCTS.weightHashMap.put(obs.itype, weight);*/
                    MCTS.current_features.put(obs.itype, obs.sqDist);
                    //System.out.println("Category:"+obs.category+", ID: "+obs.obsID+", iType:"+obs.itype);

                }
                break;
            }
        }

        // If there is Movable on this game
        observationLists = stateObs.getMovablePositions(position);
        if (observationLists != null) {
            //System.out.println("===== "+ observationLists.length +" Movable Types =====");
            for (ArrayList<Observation> list : observationLists) {
                if (!list.isEmpty()) {

                    Observation obs = list.get(0);
                    /*featureWeight weight = MCTS.weightHashMap.get(obs.itype);
                    if(weight == null)
                        weight = new featureWeight(obs.sqDist);
                    else
                        weight.distance = obs.sqDist;
                    
                    MCTS.weightHashMap.put(obs.itype, weight);*/
                    MCTS.current_features.put(obs.itype, obs.sqDist);
                    //System.out.println("Category:"+obs.category+", ID: "+obs.obsID+", iType:"+obs.itype);
                }
                break;
            }
        }

        // If there is Resources on this game
        observationLists = stateObs.getResourcesPositions(position);
        if (observationLists != null) {
            //System.out.println("===== "+ observationLists.length +" Resource Types =====");
            for (ArrayList<Observation> list : observationLists) {
                if (!list.isEmpty()) {

                    Observation obs = list.get(0);
                    /*featureWeight weight = MCTS.weightHashMap.get(obs.itype);
                    if(weight == null)
                        weight = new featureWeight(obs.sqDist);
                    else
                        weight.distance = obs.sqDist;
                    
                    MCTS.weightHashMap.put(obs.itype, weight);*/
                    MCTS.current_features.put(obs.itype, obs.sqDist);
                    //System.out.println("Category:"+obs.category+", ID: "+obs.obsID+", iType:"+obs.itype);
                }
                break;
            }
        }

        // If there is Portals on this game
        observationLists = stateObs.getPortalsPositions(position);
        if (observationLists != null) {
            //System.out.println("===== "+ observationLists.length +" Portal Types =====");
            for (ArrayList<Observation> list : observationLists) {
                if (!list.isEmpty()) {
 
                    Observation obs = list.get(0);
                    /*featureWeight weight = MCTS.weightHashMap.get(obs.itype);
                    if(weight == null)
                        weight = new featureWeight(obs.sqDist);
                    else
                        weight.distance = obs.sqDist;
                    
                    MCTS.weightHashMap.put(obs.itype, weight);*/
                    MCTS.current_features.put(obs.itype, obs.sqDist);
                    
                    
                    //System.out.println("Category:"+obs.category+", ID: "+obs.obsID+", iType:"+obs.itype);

                }
                break;
            }
        }

    }

    public int calculateAction(weightMatrix weightMatrix) {

        double[] strenght = new double[num_actions];
        double sum = 0;
        
        return 0;
 
        /*for(int i = 0; i < num_actions; i++){
            strenght[i] = 0;
            for(Integer j: MCTS.current_features){
                featureWeight weight = weightMatrix.get(j);
                strenght[i] += weight.distance * weight.weight;
            }
            sum += strenght[i];
        }
        
        for(int i = 0; i < num_actions; i++){
            strenght[i] = 0;
            HashMap<Integer, Double> weights = MCTS.weightMatrix.actionHashMap[i];
            for(Integer feature_id : weights.keySet()){
                for(Integer j: MCTS.current_features){
                    strenght[i] += weights.get(feature_id) * MCTS.current_features.get(j);
                }
                sum += strenght[i];
            }
        }
        
        /*for(int action_id = 0; action_id < num_actions; action_id++){
            strenght[action_id] = 0;
            HashMap<Integer, Double> currentMap = weightMatrix.actionHashMap[action_id];
            for(Integer feature_id: MCTS.current_features.keySet()){
                strenght[action_id] +=  currentMap.get(feature_id) * MCTS.current_features.get(feature_id);
            }
        }
        
        
        RandomCollection<Integer> rnd = new RandomCollection<>();
        
        for (int i = 0; i < num_actions; i++) {
            rnd.add(strenght[i]/sum, i);
        }
        
      
        return rnd.next();*/


    }
    
}
