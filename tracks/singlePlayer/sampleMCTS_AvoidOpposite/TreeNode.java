package tracks.singlePlayer.sampleMCTS_AvoidOpposite;

import java.util.Random;

import core.game.StateObservation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import ontology.Types;
import ontology.Types.ACTIONS;
import tools.ElapsedCpuTimer;
import tools.Utils;

public class TreeNode
{
    private final double HUGE_NEGATIVE = -10000000.0;
    private final double HUGE_POSITIVE =  10000000.0;
    public double epsilon = 1e-6;
    public double egreedyEpsilon = 0.05;
    public TreeNode parent;
    public TreeNode[] children;
    public double totValue;
    public int nVisits;
    public Random m_rnd;
    public int m_depth;
    protected double[] bounds = new double[]{Double.MAX_VALUE, -Double.MAX_VALUE};
    public int childIdx;
    public int numIters;

    public int num_actions;
    Types.ACTIONS[] actions;
    public int ROLLOUT_DEPTH = 10;
    public double K = Math.sqrt(2);

    public StateObservation rootState;
    
    // used to bias actions during rollout
    public RandomCollection<ACTIONS> rc = new RandomCollection<ACTIONS>(); 
    public HashMap<ACTIONS, Double> actionHashMap = new HashMap<ACTIONS, Double>();

    public TreeNode(Random rnd, int num_actions, Types.ACTIONS[] actions) {
        this(null, -1, rnd, num_actions, actions);
    }

    public TreeNode(TreeNode parent, int childIdx, Random rnd, int num_actions, Types.ACTIONS[] actions) {
        this.parent = parent;
        this.m_rnd = rnd;
        this.num_actions = num_actions;
        this.actions = actions;
        children = new TreeNode[num_actions];
        totValue = 0.0;
        this.childIdx = childIdx;
        if(parent != null)
            m_depth = parent.m_depth+1;
        else
            m_depth = 0;
        updateActionMap(null);
    }


    public void mctsSearch(ElapsedCpuTimer elapsedTimer) {

        double avgTimeTaken = 0;
        double acumTimeTaken = 0;
        long remaining = elapsedTimer.remainingTimeMillis();
        numIters = 0;

        int remainingLimit = 5;
        while(remaining > 2*avgTimeTaken && remaining > remainingLimit){
        //while(numIters < Agent.MCTS_ITERATIONS){

            StateObservation state = rootState.copy();

            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();
            TreeNode selected = treePolicy(state);
            double delta = selected.rollOut(state);
            backUp(selected, delta);

            numIters++;
            acumTimeTaken += (elapsedTimerIteration.elapsedMillis()) ;
            //System.out.println(elapsedTimerIteration.elapsedMillis() + " --> " + acumTimeTaken + " (" + remaining + ")");
            avgTimeTaken  = acumTimeTaken/numIters;
            remaining = elapsedTimer.remainingTimeMillis();
        }
    }

    public TreeNode treePolicy(StateObservation state) {

        TreeNode cur = this;

        while (!state.isGameOver() && cur.m_depth < ROLLOUT_DEPTH)
        {
            if (cur.notFullyExpanded()) {
                return cur.expand(state);

            } else {
                TreeNode next = cur.uct(state);
                cur = next;
            }
        }

        return cur;
    }


    public TreeNode expand(StateObservation state) {

        int bestAction = 0;
        double bestValue = -1;

        for (int i = 0; i < children.length; i++) {
            double x = m_rnd.nextDouble();
            if (x > bestValue && children[i] == null) {
                bestAction = i;
                bestValue = x;
            }
        }

        //Roll the state
        state.advance(actions[bestAction]);

        TreeNode tn = new TreeNode(this,bestAction,this.m_rnd,num_actions, actions);
        children[bestAction] = tn;
        return tn;
    }

    public TreeNode uct(StateObservation state) {

        TreeNode selected = null;
        double bestValue = -Double.MAX_VALUE;
        for (TreeNode child : this.children)
        {
            double hvVal = child.totValue;
            double childValue =  hvVal / (child.nVisits + this.epsilon);

            childValue = Utils.normalise(childValue, bounds[0], bounds[1]);
            //System.out.println("norm child value: " + childValue);

            double uctValue = childValue +
                    K * Math.sqrt(Math.log(this.nVisits + 1) / (child.nVisits + this.epsilon));

            uctValue = Utils.noise(uctValue, this.epsilon, this.m_rnd.nextDouble());     //break ties randomly

            // small sampleRandom numbers: break ties in unexpanded nodes
            if (uctValue > bestValue) {
                selected = child;
                bestValue = uctValue;
            }
        }
        if (selected == null)
        {
            throw new RuntimeException("Warning! returning null: " + bestValue + " : " + this.children.length + " " +
            + bounds[0] + " " + bounds[1]);
        }

        //Roll the state:
        state.advance(actions[selected.childIdx]);

        return selected;
    }


    public double rollOut(StateObservation state)
    {
        List<Types.ACTIONS> simulation = new ArrayList();
        int thisDepth = this.m_depth;

        while (!finishRollout(state,thisDepth)) {
            //int action = m_rnd.nextInt(num_actions);
            //Types.ACTIONS act = actions[action];

            Types.ACTIONS act = nextRolloutAction();
            simulation.add(act);
            state.advance(act);
            thisDepth++;
        }
        
        MCTS.simulations.add(simulation);

        double delta = value(state);

        if(delta < bounds[0])
            bounds[0] = delta;
        if(delta > bounds[1])
            bounds[1] = delta;

        //double normDelta = Utils.normalise(delta ,lastBounds[0], lastBounds[1]);

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
                //double tieBreaker = m_rnd.nextDouble() * epsilon;
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
    
    /*
    * Reset action map so that every action has the weight
    */
    private void resetActionMap() {
        double weight = 1/(double)actions.length;
        for (ACTIONS action : actions) {
            //System.out.println("Action: "+Util.printAction( actions[this.id][i])+", weight: "+weight);
            actionHashMap.put(action, weight);
            rc.add(weight, action);
        }
    }
    
    /*
    * Reset action map so that every action has the weight
    */
    private void updateActionMap(Types.ACTIONS last_action) {
        double weight = 1/(double)actions.length;
        double decremented_weight = 0;
        List<Types.ACTIONS> opposite_actions = new ArrayList();
        
        if(last_action != null){
            opposite_actions = getOppositeAction(last_action);
            if(opposite_actions.size() > 0){
                decremented_weight = weight * 0.5/(double)opposite_actions.size();
                weight = weight + (weight*0.5)/(double)(actions.length-opposite_actions.size());
            }
        }   
        
        for (ACTIONS action : actions) {
            //System.out.println("Action: "+Util.printAction( actions[this.id][i])+", weight: "+weight);
            if(!opposite_actions.contains(action)){
                actionHashMap.put(action, weight);
                rc.add(weight, action);
            }
            else{
                actionHashMap.put(action, decremented_weight);
                rc.add(decremented_weight, action);
            }

        }
    }
    
    /*
    * Get next action in the rollout, weighting the probabilities to avoid
    * actions that nulify the previous ones (e.g. ->, <-)
    */
    private Types.ACTIONS nextRolloutAction() {
        ACTIONS chosen_action = (Types.ACTIONS) rc.next();
        
//        System.out.println("Chosen Action: "+chosen_action);
//        System.out.println("HASHMAP BEFORE: ");
//        for(ACTIONS ac : actionHashMap.keySet()){
//            System.out.println(printAction(ac)+": "+actionHashMap.get(ac));
//        }
        
        updateActionMap(chosen_action);
        
//        System.out.println("HASHMAP AFTER: ");
//        for(ACTIONS ac : actionHashMap.keySet()){
//            System.out.println(printAction(ac)+": "+actionHashMap.get(ac));
//        }
        
        return chosen_action;
    }
    
    public static List<Types.ACTIONS> getOppositeAction(Types.ACTIONS action){
        /*List<Types.ACTIONS> opposite_actions = new ArrayList();
        switch (action) {
            case ACTION_UP:
               opposite_actions.add(Types.ACTIONS.ACTION_DOWN);
               break;
               //return Types.ACTIONS.ACTION_DOWN;
            case ACTION_DOWN:
               opposite_actions.add(Types.ACTIONS.ACTION_UP);
               break;
               //return Types.ACTIONS.ACTION_UP;
            case ACTION_LEFT:
                opposite_actions.add(Types.ACTIONS.ACTION_RIGHT);
                break;
                //return Types.ACTIONS.ACTION_RIGHT;
            case ACTION_RIGHT:
                opposite_actions.add(Types.ACTIONS.ACTION_LEFT);
                break;
                //return Types.ACTIONS.ACTION_LEFT;
        }
        return opposite_actions;*/
        return MCTS.redundantActionsList.get(action);
    }
    
    public static String printAction(Types.ACTIONS action){
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
        return character;
    }
}
