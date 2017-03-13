package controllers.singlePlayer.ehauckdo;

import java.util.Random;

import core.ArcadeMachine;
import core.game.Observation;
import core.game.StateObservation;
import java.util.ArrayList;
import ontology.Types;
import tools.ElapsedCpuTimer;
import tools.Utils;
import tools.Vector2d;

public class SingleTreeNode
{
    private final double HUGE_NEGATIVE = -10000000.0;
    private final double HUGE_POSITIVE =  10000000.0;
    public final int ROLLOUT_DEPTH = 10;
    public double epsilon = 1e-6;
    public double egreedyEpsilon = 0.05;
    public Random m_rnd;
    
    public StateObservation state;
    public SingleTreeNode parent;
    public SingleTreeNode[] children;
    public Types.ACTIONS[] actions;
    
    public double totValue; // what is this??
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
        if(parent != null)
            m_depth = parent.m_depth+1;
        else
            m_depth = 0;

    }

    public void mctsSearch(ElapsedCpuTimer elapsedTimer) {

        double avgTimeTaken = 0;
        double acumTimeTaken = 0;
        long remaining = elapsedTimer.remainingTimeMillis();
        int numIters = 0;

        int remainingLimit = 5;
        double[][] new_weightMatrix = null;
        /*double[][] weightMatrix = new double[5][5];
        initializeWeightMatrix(weightMatrix, 5, 5);   
        
        double[][] new_weightMatrix = weightMatrix;*/
        double currentFitness = 0;
        double delta = 0;
        
        while(remaining > 2*avgTimeTaken && remaining > remainingLimit){
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();
            SingleTreeNode selected = treePolicy();
            
            /* Fast Evolution entre aqui */  
            
            /*new_weightMatrix = new double[5][5];
            initializeWeightMatrix(new_weightMatrix, 5, 5); */   
            
            /*System.out.println("New matrix:");
            for(int i = 0; i < 5; i++){
                for(int j = 0; j < 5; j++){
                    System.out.print(String.format( "%.2f", new_weightMatrix[i][j])+" ");
                }
                System.out.println("");
            }*/
            
            
            delta = selected.rollOut(new_weightMatrix);
            backUp(selected, delta);
            
            /*if(delta > currentFitness){
                currentFitness = delta;
                weightMatrix = new_weightMatrix;
            }
            
            System.out.println("Delta: "+delta);
            mutateWeightMatrix(new_weightMatrix, 5, 5);*/
            
            
            /* ******* */

            numIters++;
            acumTimeTaken += (elapsedTimerIteration.elapsedMillis()) ;

            avgTimeTaken  = acumTimeTaken/numIters;
            remaining = elapsedTimer.remainingTimeMillis();
            //System.out.println(elapsedTimerIteration.elapsedMillis() + " --> " + acumTimeTaken + " (" + remaining + ")");
            iterations++;
        }
         
        /*System.out.println("Iterations: "+iterations);
        System.out.println("Cumulative time:"+acumTimeTaken);
        System.out.println("Average time:"+avgTimeTaken);
        System.out.println("Highest delta: "+delta);
        if(weightMatrix != null){
            System.out.println("Chosen matrix:");
            for(int i = 0; i < 5; i++){
                for(int j = 0; j < 5; j++){
                    System.out.print(String.format( "%.2f", weightMatrix[i][j])+" ");
                }
                System.out.println("");
            }
        }
        
        System.exit(0);*/

    }

    public SingleTreeNode treePolicy() {

        SingleTreeNode cur = this;

        while (!cur.state.isGameOver() && cur.m_depth < ROLLOUT_DEPTH)
        {
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
        for (SingleTreeNode child : this.children)
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

    public SingleTreeNode egreedy() {


        SingleTreeNode selected = null;

        if(m_rnd.nextDouble() < egreedyEpsilon)
        {
            //Choose randomly
            int selectedIdx = m_rnd.nextInt(children.length);
            selected = this.children[selectedIdx];

        }else{
            //pick the best Q.
            double bestValue = -Double.MAX_VALUE;
            for (SingleTreeNode child : this.children)
            {
                double hvVal = child.totValue;
                hvVal = Utils.noise(hvVal, this.epsilon, this.m_rnd.nextDouble());     //break ties randomly
                // small sampleRandom numbers: break ties in unexpanded nodes
                if (hvVal > bestValue) {
                    selected = child;
                    bestValue = hvVal;
                }
            }

        }


        if (selected == null)
        {
            throw new RuntimeException("Warning! returning null: " + this.children.length);
        }

        return selected;
    }


    public double rollOut(double[][] weightMatrix)
    {
        StateObservation rollerState = state.copy();
        int thisDepth = this.m_depth;

        while (!finishRollout(rollerState,thisDepth)) {
            
            /* Fast Evolution entre aqui */  
            
            /*double[] features = queryState(rollerState);         
            int action = calculateAction(weightMatrix, features);*/
            
            int action = m_rnd.nextInt(num_actions);
            
            /* ************************ */
    
            rollerState.advance(actions[action]);
            thisDepth++;
        }

        double delta = value(rollerState);

        if(delta < bounds[0])
            bounds[0] = delta;

        if(delta > bounds[1])
            bounds[1] = delta;

        return delta;
    }

    public double value(StateObservation a_gameState) {

        boolean gameOver = a_gameState.isGameOver();
        Types.WINNER win = a_gameState.getGameWinner();
        double rawScore = a_gameState.getGameScore();

        if(gameOver && win == Types.WINNER.PLAYER_LOSES)
            rawScore += HUGE_NEGATIVE;

        if(gameOver && win == Types.WINNER.PLAYER_WINS)
            rawScore += HUGE_POSITIVE;

        return rawScore;
    }

    public boolean finishRollout(StateObservation rollerState, int depth)
    {
        if(depth >= ROLLOUT_DEPTH)      //rollout end condition.
            return true;

        if(rollerState.isGameOver())               //end of game
            return true;

        return false;
    }

    public void backUp(SingleTreeNode node, double result)
    {
        SingleTreeNode n = node;
        while(n != null)
        {
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
        for (SingleTreeNode tn : children) {
            if (tn == null) {
                return true;
            }
        }

        return false;
    }
    
    
    public double[] queryState(StateObservation stateObs){
        Vector2d position = stateObs.getAvatarPosition();
        //System.out.println("Got possition! "+position.toString());
        
        double[] features = new double[5];
        
        ArrayList<Observation>[] observationLists = stateObs.getNPCPositions(position);
        // If there is NPCs on this game

        if (observationLists != null){
            //System.out.println("===== "+ observationLists.length +" NPC Types =====");
            for (ArrayList<Observation> list : observationLists) {
                if(!list.isEmpty()){
                    System.out.println("Type 1: "+list.size()+" ocurrences");
                    //for(int i = 0; i < list.size(); i++){
                    //    Observation obs = list.get(i);
                    Observation obs = list.get(0);
                    features[0] = obs.sqDist;
                    //System.out.println("Category:"+obs.category+", ID: "+obs.obsID+", iType:"+obs.itype);
                    //System.out.println("Distance: "+obs.sqDist);
                    //    break;
                    //}
                }
                break;
            }
        }

        // If there is Immovable on this game
        observationLists = stateObs.getImmovablePositions(position);
        if (observationLists != null){
            //System.out.println("===== "+ observationLists.length +" Immovable Types =====");
            for (ArrayList<Observation> list : observationLists) {
                if(!list.isEmpty()){
                    //System.out.println("Type 1: "+list.size()+" ocurrences");
                    Observation obs = list.get(0);
                    features[1] = obs.sqDist;
                    //System.out.println("Category:"+obs.category+", ID: "+obs.obsID+", iType:"+obs.itype);
                    
                }
                break;
            }
        }

        // If there is Movable on this game
        observationLists = stateObs.getMovablePositions(position);
        if (observationLists != null){
            //System.out.println("===== "+ observationLists.length +" Movable Types =====");
            for (ArrayList<Observation> list : observationLists) {
                if(!list.isEmpty()){
                    //System.out.println("Type 1: "+list.size()+" ocurrences");
                    Observation obs = list.get(0);
                    features[2] = obs.sqDist;
                    //System.out.println("Category:"+obs.category+", ID: "+obs.obsID+", iType:"+obs.itype);
                }
                break;
            }
        }
        
        // If there is Resources on this game
        observationLists = stateObs.getResourcesPositions(position);
        if (observationLists != null){
            //System.out.println("===== "+ observationLists.length +" Resource Types =====");
            for (ArrayList<Observation> list : observationLists) {
                if(!list.isEmpty()){
                    //System.out.println("Type 1: "+list.size()+" ocurrences");
                    Observation obs = list.get(0);
                    features[3] = obs.sqDist;
                    //System.out.println("Category:"+obs.category+", ID: "+obs.obsID+", iType:"+obs.itype);
                }
                break;
            }
        }
        
        // If there is Portals on this game
        observationLists = stateObs.getPortalsPositions(position);
        if (observationLists != null){
            //System.out.println("===== "+ observationLists.length +" Portal Types =====");
            for (ArrayList<Observation> list : observationLists) {
                if(!list.isEmpty()){
                    //System.out.println("Type 1: "+list.size()+" ocurrences");
                    Observation obs = list.get(0);
                    features[4] = obs.sqDist;
                    //System.out.println("Category:"+obs.category+", ID: "+obs.obsID+", iType:"+obs.itype);
                    
                }
                break;
            }
        }
        
        return features;
        
    }
    
    public void initializeWeightMatrix(double[][] weightMatrix, int x, int y){
        Random rnd = new Random();
        for(int i = 0; i < x; i++)
            for(int j = 0; j < y; j++){
                weightMatrix[i][j] = rnd.nextDouble();
            }
    }
    
    public void mutateWeightMatrix(double[][] weightMatrix, int x, int y){
        /*for(int i = 0; i < x; i++)
            for(int j = 0; j < y; j++){
                weightMatrix[i][j] = m_rnd.nextDouble();
            }*/
        
        weightMatrix[m_rnd.nextInt(5)][m_rnd.nextInt(5)] = m_rnd.nextDouble();
        weightMatrix[m_rnd.nextInt(5)][m_rnd.nextInt(5)] = m_rnd.nextDouble();
        weightMatrix[m_rnd.nextInt(5)][m_rnd.nextInt(5)] = m_rnd.nextDouble();
        weightMatrix[m_rnd.nextInt(5)][m_rnd.nextInt(5)] = m_rnd.nextDouble();
        weightMatrix[m_rnd.nextInt(5)][m_rnd.nextInt(5)] = m_rnd.nextDouble();
    }
    
    public int calculateAction(double[][] weightMatrix, double[] features){
        
        double[] strenght = new double[5];
        for(int i = 0; i < 5; i++){
            strenght[i] = 0;
        }
        
        for(int i = 0; i < 5; i++){
            for(int j = 0; j < 5; j++){
                strenght[j] += weightMatrix[i][j] * features[j];
            }
        }
        
        int stronghest = 0;
        for(int i = 1; i < 5; i++){
            if(strenght[i] > strenght[stronghest])
                stronghest = i;
        }
        
        return stronghest;
    }
}
