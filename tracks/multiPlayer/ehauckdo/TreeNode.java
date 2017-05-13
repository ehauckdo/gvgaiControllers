package tracks.multiPlayer.ehauckdo;

import tracks.multiPlayer.advanced.sampleMCTS.*;
import java.util.Random;

import core.game.StateObservationMulti;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;
import ontology.Types;
import static ontology.Types.ACTIONS;
import tools.ElapsedCpuTimer;
import tools.Utils;
import tools.Vector2d;

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

    public int MCTS_ITERATIONS = 100;
    public int ROLLOUT_DEPTH = 10;
    public double K = Math.sqrt(2);
    public double REWARD_DISCOUNT = 1.00;
    public int[] NUM_ACTIONS;
    public Types.ACTIONS[][] actions;
    public int id, oppID, no_players;

    public StateObservationMulti rootState;
    
    public boolean gameOver = false; // true if this node ever seen a GameOver
    public RandomCollection rc = new RandomCollection();
    public HashMap<ACTIONS, Double> actionHashMap = new HashMap();

    public TreeNode(Random rnd, int[] NUM_ACTIONS, Types.ACTIONS[][] actions, int id, int oppID, int no_players) {
        this(null, -1, rnd, id, oppID, no_players, NUM_ACTIONS, actions);
    }

    public TreeNode(TreeNode parent, int childIdx, Random rnd, int id, int oppID, int no_players, int[] NUM_ACTIONS, Types.ACTIONS[][] actions) {
        this.id = id;
        this.oppID = oppID;
        this.no_players = no_players;
        this.parent = parent;
        this.m_rnd = rnd;
        totValue = 0.0;
        this.childIdx = childIdx;
        if(parent != null)
            m_depth = parent.m_depth+1;
        else
            m_depth = 0;
        this.NUM_ACTIONS = NUM_ACTIONS;
        children = new TreeNode[NUM_ACTIONS[id]];
        this.actions = actions;
        
        double weight = 1/(double)NUM_ACTIONS[id];
        for(int i = 0; i < actions[this.id].length; i++){
            //System.out.println("Action: "+Util.printAction( actions[this.id][i])+", weight: "+weight);
            actionHashMap.put(actions[this.id][i], weight);
            rc.add(weight, actions[this.id][i]);
        }
        
        /*HashMap<Types.ACTIONS, Integer> actionsHashMap = new HashMap();
        for(int i = 0; i < 1000; i++){
            Types.ACTIONS action = (Types.ACTIONS) rc.next();
            Integer j = actionsHashMap.get(action);
            if(j == null){
                actionsHashMap.put(action, 1);
            }
            else{
                actionsHashMap.put(action, j+1);
            }
        }
        for(Types.ACTIONS ac : actionsHashMap.keySet()){
            System.out.println(Util.printAction(ac)+": "+actionsHashMap.get(ac));
        }*/
    }


    public void mctsSearch(ElapsedCpuTimer elapsedTimer) {

        double avgTimeTaken = 0;
        double acumTimeTaken = 0;
        long remaining = elapsedTimer.remainingTimeMillis();
        int numIters = 0;

        int remainingLimit = 7;
        while(remaining > 2*avgTimeTaken && remaining > remainingLimit){
        //while(numIters < Agent.MCTS_ITERATIONS){

            StateObservationMulti state = rootState.copy();

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

        System.out.println("(ehauckdo) -- " + numIters + " -- ( " + avgTimeTaken + ")");
    }

    public TreeNode treePolicy(StateObservationMulti state) {

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


    public TreeNode expand(StateObservationMulti state) {

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

        //need to provide actions for all players to advance the forward model
        Types.ACTIONS[] acts = new Types.ACTIONS[no_players];

        //set this agent's action
        acts[id] = actions[id][bestAction];

        //get actions available to the opponent and assume they will do a random action
        Types.ACTIONS[] oppActions = actions[oppID];
        acts[oppID] = oppActions[new Random().nextInt(oppActions.length)];

        state.advance(acts);

        TreeNode tn = new TreeNode(this,bestAction,this.m_rnd, id, oppID, no_players, NUM_ACTIONS, actions);
        children[bestAction] = tn;
        
        if(isPlayerLoses(state))
            children[bestAction].gameOver = true;
        
        return tn;
    }

    public TreeNode uct(StateObservationMulti state) {

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

        //need to provide actions for all players to advance the forward model
        Types.ACTIONS[] acts = new Types.ACTIONS[no_players];

        //set this agent's action
        acts[id] = actions[id][selected.childIdx];

        //get actions available to the opponent and assume they will do a random action
        Types.ACTIONS[] oppActions = actions[oppID];
        acts[oppID] = oppActions[new Random().nextInt(oppActions.length)];

        state.advance(acts);
        if(isPlayerLoses(state))
            selected.gameOver = true;

        return selected;
    }


    public double rollOut(StateObservationMulti state)
    {
        int thisDepth = this.m_depth;

        while (!finishRollout(state,thisDepth)) {

            //random move for all players
            Types.ACTIONS[] acts = new Types.ACTIONS[no_players];
            for (int i = 0; i < no_players; i++) {
                acts[i] = actions[i][m_rnd.nextInt(NUM_ACTIONS[i])];
            }
            
            //acts[this.id] = nextRolloutAction();
            
            state.advance(acts);
            thisDepth++;
        }

        double delta = value(state);
        
        delta = penaltyRepeatedSqm(delta, state, true);

        if(delta < bounds[0])
            bounds[0] = delta;
        if(delta > bounds[1])
            bounds[1] = delta;

        return delta;
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


    public int mostVisitedAction() {
        int selected = -1;
        double bestValue = -Double.MAX_VALUE;
        boolean allEqual = true;
        double first = -1;
        //System.out.println("Children lenght: "+children.length);

        for (int i=0; i<children.length; i++) {
            //System.out.print(Util.printAction(actions[this.id][i])+": ");
            if(children[i] != null)
            {
                if(!children[i].gameOver){
                    //System.out.print("(Tick "+(rootState.getGameTick()+1)+") OK\n");
                    if(first == -1){
                        first = children[i].nVisits;
                        selected = i;
                    }else if(first != children[i].nVisits)
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
                else{
                    //System.out.print("GameOver\n");
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
        //System.out.println("Selected: "+selected);
        return selected;
    }

    public int bestAction()
    {
        int selected = -1;
        double bestValue = -Double.MAX_VALUE;

        for (int i=0; i<children.length; i++) {

            if(children[i] != null && !children[i].gameOver) {
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
    * Return true if the passed state is Game Over AND the current player lost
    * the game (either individually or in co-op)
    */
    public boolean isPlayerLoses(StateObservationMulti so){
        if(so.isGameOver()){
            return so.getMultiGameWinner()[id] != Types.WINNER.PLAYER_WINS;
        }
        else
            return false;
    }

    private Types.ACTIONS nextRolloutAction() {
        ACTIONS chosen_action = (Types.ACTIONS) rc.next();
        //System.out.println("Chosen Action: "+chosen_action);
        
        ACTIONS opposite_action = Util.getOppositeAction(chosen_action);
        /*System.out.println("Opposite Action: "+opposite_action);
        
        System.out.println("HASHMAP BEFORE: ");
        for(ACTIONS ac : actionHashMap.keySet()){
            System.out.println(Util.printAction(ac)+": "+actionHashMap.get(ac));
        }*/
        
        if(opposite_action != null){
            rc.clear();
            for(Entry<ACTIONS, Double> set: actionHashMap.entrySet()){
                if(set.getKey() == opposite_action){
                    set.setValue(set.getValue()-0.10);
                }
                else{
                    set.setValue(set.getValue()+0.2);
                }
                rc.add(set.getValue(), set.getKey());
            }
        }
        
        /*System.out.println("HASHMAP AFTER: ");
        for(ACTIONS ac : actionHashMap.keySet()){
            System.out.println(Util.printAction(ac)+": "+actionHashMap.get(ac));
        }*/
        
        return chosen_action;
    }

    private double penaltyRepeatedSqm(double delta, StateObservationMulti so, boolean flag) {
        if(flag == false)
            return delta;
        else{
            Vector2d pos = Util.getCurrentGridPosition(so, id);
            double weight = MCTSPlayer.tileSet[(int)pos.x][(int)pos.y];
            delta = (weight >= 1) ? delta : weight*delta;
            //System.out.println("Rollout Pos: "+pos.x+","+pos.y+"\t w: "+((weight >= 1)?1:weight)+", Delta: "+delta);
            return delta;
        }
    }
    
    
    
     
}
