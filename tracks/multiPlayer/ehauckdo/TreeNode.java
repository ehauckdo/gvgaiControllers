package tracks.multiPlayer.ehauckdo;

import core.game.Event;
import core.game.Observation;
import java.util.Random;

import core.game.StateObservationMulti;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
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

    public int ROLLOUT_DEPTH = 3;
    public int TREE_DEPTH = 10;
    public double K = Math.sqrt(2);
    public double REWARD_DISCOUNT = 1.00;
    public int[] NUM_ACTIONS;
    public Types.ACTIONS[][] actions;
    public int id, oppID, no_players;

    public StateObservationMulti rootState;
    
    // true if this node ever seen a GameOver
    public boolean gameOver = false; 
    
    // used to bias actions during rollout
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
    
    }

    public void mctsSearch(ElapsedCpuTimer elapsedTimer) {

        double avgTimeTaken = 0;
        double acumTimeTaken = 0;
        long remaining = elapsedTimer.remainingTimeMillis();
        int numIters = 0;

        int remainingLimit = 7;
        while(remaining > 2*avgTimeTaken && remaining > remainingLimit){

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

        //System.out.println("(ehauckdo) -- " + numIters + " -- ( " + avgTimeTaken + ")");
    }

    public TreeNode treePolicy(StateObservationMulti state) {

       TreeNode cur = this;

        while (!state.isGameOver() && cur.m_depth < TREE_DEPTH)
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
        StateObservationMulti rollerState = state.copy();
        resetActionMap();

        while (!finishRollout(rollerState,thisDepth)) {
            
            //random move for all players
            Types.ACTIONS[] acts = new Types.ACTIONS[no_players];
            for (int i = 0; i < no_players; i++) {
                acts[i] = actions[i][m_rnd.nextInt(NUM_ACTIONS[i])];
            }
            
            acts[this.id] = nextRolloutAction();
            
            rollerState.advance(acts);
            thisDepth++;
        }
        
        //updateFeatures(rollerState);

        double delta = value(rollerState) - value(state);
        
        //delta = penaltyRepeatedSqm(delta, rollerState, true);
        
        updateKnowledgeBase(state, rollerState);
        double delta_d = getDistanceChange(state, rollerState);

        if(delta < bounds[0])
            bounds[0] = delta;
        if(delta > bounds[1])
            bounds[1] = delta;

        if(delta != 0)
            return delta;
        else
            return delta_d;
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

    /*
    * Reset action map so that every action has the weight
    */
    private void resetActionMap() {
        double weight = 1/(double)NUM_ACTIONS[id];
        for (ACTIONS action : actions[this.id]) {
            //System.out.println("Action: "+Util.printAction( actions[this.id][i])+", weight: "+weight);
            actionHashMap.put(action, weight);
            rc.add(weight, action);
        }
    }
    
    /*
    * Get next action in the rollout, weighting the probabilities to avoid
    * actions that nulify the previous ones (e.g. ->, <-)
    */
    private Types.ACTIONS nextRolloutAction() {
        ACTIONS chosen_action = (Types.ACTIONS) rc.next();
        //System.out.println("Chosen Action: "+chosen_action);
        
        ACTIONS opposite_action = Util.getOppositeAction(chosen_action);
        //System.out.println("Opposite Action: "+opposite_action);
        
        //System.out.println("HASHMAP BEFORE: ");
        //for(ACTIONS ac : actionHashMap.keySet()){
        //    System.out.println(Util.printAction(ac)+": "+actionHashMap.get(ac));
        //}
        
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
        
        //System.out.println("HASHMAP AFTER: ");
        //for(ACTIONS ac : actionHashMap.keySet()){
        //    System.out.println(Util.printAction(ac)+": "+actionHashMap.get(ac));
        //}
        
        return chosen_action;
    }

    /*
    * Apply penalty on the reward obtained by the simulation if it ended
    * on a sqm that has been recently stepped on before
    */
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
    
   /*
    * Add new events happened between oldso and newso to the knowledge base
    */
    public void updateKnowledgeBase(StateObservationMulti oldso, StateObservationMulti newso){
        if (oldso.getEventsHistory().size() == newso.getEventsHistory().size()) {
            return;
        }
        double scoreChange = newso.getGameScore() - oldso.getGameScore();
         
        if(scoreChange > 0){
            ArrayList<Event> newEvents = mapNewEvents(oldso, newso); 
            for(Event e: newEvents){
                //System.out.println("Event added, Active Type: " + e.activeTypeId + ", Passive Type: " + e.passiveTypeId);
                MCTSPlayer.knowledgeBase.add(e.activeTypeId, e.passiveTypeId, scoreChange);
            }
            if(newEvents.size() > 0)
                MCTSPlayer.knowledgeBase.printKnowledgeBase();
        }
    }
     private ArrayList<Event> mapNewEvents(StateObservationMulti oldso, StateObservationMulti newso){
        ArrayList<Event> eventsList = new ArrayList();
        int new_events = newso.getEventsHistory().size() - oldso.getEventsHistory().size();
         
        Iterator<Event> events = newso.getEventsHistory().descendingIterator();
        for (int i = 0; i < new_events; i++) {
            Event e = events.next();    
            eventsList.add(e);
        }
        return eventsList;
    }   
    
    /*
     * Returns an array of Observations containing all features from the
     * state passed as parameter ordered by distance
     */
    private ArrayList<Observation> getFeatures(StateObservationMulti stateObs) {
        ArrayList<Observation> features = new ArrayList();  
        ArrayList<Observation> ordered = new ArrayList();
        Vector2d playerPos= stateObs.getAvatarPosition();
        
        //System.out.println("NPCS:");
        fetchObservations(stateObs.getNPCPositions(playerPos), features);
        //System.out.println("Movable:");
        fetchObservations(stateObs.getMovablePositions(playerPos), features);
        //System.out.println("Resources:");
        fetchObservations(stateObs.getResourcesPositions(playerPos), features);
        //System.out.println("Portals:");
        fetchObservations(stateObs.getPortalsPositions(playerPos), features);
          
        for(Observation obs: features){
            int oldSize = ordered.size();
            for(int i = 0; i < ordered.size(); i++){
                if(obs.sqDist < ordered.get(i).sqDist){
                    ordered.add(i, obs);
                    break;
                }
            }
            if(ordered.size() == oldSize)
                ordered.add(obs);
        }
        
        //System.out.println("Ordered:");
        //for(Observation obs :ordered){
        //    System.out.println("Category:"+obs.category+", ID: "+obs.obsID+
        //                    ", iType:"+obs.itype+", Dist: "+Math.sqrt(obs.sqDist));
        //}
        
        
        return features;
    }
    private void fetchObservations(ArrayList<Observation>[] observationLists, ArrayList<Observation> features){
        if (observationLists != null) {
            for (ArrayList<Observation> list : observationLists) {
                list.stream().forEach((obs) -> {
                    features.add(obs);
                });
            }
        }
    }
      
    /*
    * Returns current sprite to be targeted, or a new one if any
    */
    private Observation getNextFeature(ArrayList<Observation> Di_0, StateObservationMulti stateObs){
        if(MCTSPlayer.currentTarget != -1){
            Observation obs = getObservation(Di_0, MCTSPlayer.currentTarget);
            //System.out.println("Current Tracking Feature (count:"+MCTSPlayer.chaseTargetTimer+","+stateObs.getGameTick()+"): "+MCTSPlayer.currentTarget);
            
            if(stateObs.getGameTick() > MCTSPlayer.chaseTargetTimer+400){
                MCTSPlayer.currentTarget = -1;
                MCTSPlayer.chaseTargetTimer = 0;
                MCTSPlayer.ignoreTargets.add(obs);
            }
            if(obs != null){
                //System.out.println("Category:"+obs.category+", ID: "+obs.obsID+
                //            ", iType:"+obs.itype+", Dist: "+Math.sqrt(obs.sqDist));
                return obs;
            }
            //System.out.println("Feature is destroyed. Fecthing a new one...");
            MCTSPlayer.currentTarget = -1;
            MCTSPlayer.chaseTargetTimer = 0;
            MCTSPlayer.ignoreTargets.add(obs);
        }
            
        for(Observation obs: Di_0){
            if(MCTSPlayer.ignoreTargets.contains(obs) == false){
                Integer feature_id = obs.itype;
                int occurrences = MCTSPlayer.knowledgeBase.getOcurrences(feature_id);
                double avg_scoreChange = MCTSPlayer.knowledgeBase.getAvgScoreChange(feature_id);
                 if (occurrences == 0
                        || (Di_0.get(feature_id).sqDist > 0 && avg_scoreChange > 0)) {
                     MCTSPlayer.currentTarget = obs.obsID;
                     MCTSPlayer.chaseTargetTimer = stateObs.getGameTick();
                     //System.out.println("Chosen Feature:");
                     //System.out.println("Category:"+obs.category+", ID: "+obs.obsID+
                     //           ", iType:"+obs.itype+", Dist: "+Math.sqrt(obs.sqDist));
                     return obs;
                 }
            }
        }
        
        return null;
    }
    private Observation getObservation(ArrayList<Observation> Di_0, int obsId){
        for(Observation obs: Di_0){
            if(obs.obsID == obsId)
                return obs;
        }
        return null;
    }
    
    /*
    * Search for an instance of the given observation inside the passed state
    */
    private Observation getUpdatedFeature(StateObservationMulti so, Observation obs) {
        if(obs != null){         
            ArrayList<Observation> allDistances = new ArrayList(); 
            Vector2d playerPos = so.getAvatarPosition();
            fetchObservations(so.getNPCPositions(playerPos), allDistances);
            fetchObservations(so.getMovablePositions(playerPos), allDistances);
            fetchObservations(so.getResourcesPositions(playerPos), allDistances);
            fetchObservations(so.getPortalsPositions(playerPos), allDistances);
            for(Observation o: allDistances){
                if(o.obsID == obs.obsID)
                    return o;
            }
        }   
        return null;
    }
    
    /*
    * Get distance scoring function from two states
    */
    private double getDistanceChange(StateObservationMulti oldso, StateObservationMulti newso) {
        double delta_d = 0;
        double blockSize = oldso.getBlockSize();
        ArrayList<Observation> allFeatures = getFeatures(oldso);
        
        Observation Di_0 = getNextFeature(allFeatures, oldso);
        Observation Di_f = getUpdatedFeature(newso, Di_0);
        if(Di_0 != null){
            
            if(Di_f == null){
                // avatar probably reached this sprite
                delta_d = 1;
            }
            else{
                double Di_0_euDist = Util.calculateGridDistance(Di_0.position, Di_0.reference, blockSize);
                double Di_f_euDist = Util.calculateGridDistance(Di_f.position, Di_f.reference, blockSize);

                //System.out.println("Initial Dist: "+Di_0_euDist);
                //System.out.println("Final Dist: "+Di_f_euDist);

                delta_d = 1 - (Di_f_euDist / (double)Di_0_euDist);
            }

        }
        
        //System.out.println("DsChange: "+delta_d);
        
        return delta_d;
    }
    
}
