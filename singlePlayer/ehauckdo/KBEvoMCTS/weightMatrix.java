package controllers.singlePlayer.ehauckdo.KBEvoMCTS;

import core.game.Observation;
import core.game.StateObservation;
import java.util.HashMap;
import org.apache.log4j.Level;
import util.Util;

/**
 *
 * @author hauck
 */
public class weightMatrix {

    // current fitness of this matrix
    public double fitness = 0;

    // currently mapped features
    public HashMap<Integer, Double> mapped_features = new HashMap<>();

    // array of maps featureId -> weight
    // each index in the array is an action_id
    public HashMap<Integer, Double>[] actionHashMap;

    public weightMatrix(int num_actions) {
        actionHashMap = new HashMap[num_actions];
        for (int i = 0; i < num_actions; i++) {
            actionHashMap[i] = new HashMap<>();
        }
    }

    public weightMatrix(int num_actions, HashMap<Integer, Double> features, HashMap<Integer, Double>[] hashMap) {
        this.actionHashMap = new HashMap[num_actions];
        for (int i = 0; i < num_actions; i++) {
            actionHashMap[i] = new HashMap<>();
        }
        // make sure we deep copy values
        for (Integer i : features.keySet()) {
            this.mapped_features.put(new Integer(i), new Double(features.get(i)));
        }
        for (int i = 0; i < hashMap.length; i++) {
            for (Integer key : hashMap[i].keySet()) {
                this.actionHashMap[i].put(new Integer(key), new Double(hashMap[i].get(key)));
            }
        }
    }

    public void setWeight(int action_key, int feature_id, double weight) {
        actionHashMap[action_key].put(feature_id, weight);
    }

    public double getWeight(int action_key, int feature_id) {
        return actionHashMap[action_key].get(action_key);
    }

    public void mutateMatrix() {
        for (HashMap<Integer, Double> actionHashMap1 : actionHashMap) {
            for (Integer feature_id : actionHashMap1.keySet()) {
                if (MCTS.m_rnd.nextFloat() > 0.8) {
                    double currentWeight = actionHashMap1.get(feature_id);
                    actionHashMap1.put(feature_id, currentWeight + MCTS.m_rnd.nextGaussian() * 0.1);
                }
            }
        }
    }

    public weightMatrix getMutatedMatrix() {

        weightMatrix mutatedWeightMatrix = new weightMatrix(actionHashMap.length, mapped_features, actionHashMap);

        for (HashMap<Integer, Double> actionHashMap1 : mutatedWeightMatrix.actionHashMap) {
            for (Integer feature_id : actionHashMap1.keySet()) {
                if (MCTS.m_rnd.nextFloat() > 0.8) {
                    double currentWeight = actionHashMap1.get(feature_id);
                    actionHashMap1.put(feature_id, currentWeight + MCTS.m_rnd.nextGaussian() * 0.1);
                }
            }
        }
        
        return mutatedWeightMatrix;
    }

    public void updateMapping(HashMap<Integer, Observation> features, StateObservation stateObs) {
        for (HashMap<Integer, Double> actionKey_hashMap : actionHashMap) {
            for (Integer feature_id : features.keySet()) {
                if(actionKey_hashMap.get(feature_id) == null){
                    actionKey_hashMap.put(feature_id, 1.0);
                    Observation obs = features.get(feature_id);
                    //mapped_features.put(feature_id, features.get(feature_id).sqDist);
                    mapped_features.put(feature_id, Util.calculateGridDistance(obs.position,
                                obs.reference, stateObs.getBlockSize()));
                }
            }
        }
    }

    public void printMatrix() {
        for (HashMap<Integer, Double> actionKey_hashMap : actionHashMap) {
            for (Integer feature_id : mapped_features.keySet()) {
                MCTS.LOGGER.log(Level.INFO, actionKey_hashMap.get(feature_id)+" ");
            }
            System.out.print("\n");
        }
    }

}
