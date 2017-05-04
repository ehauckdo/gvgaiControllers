package controllers.tracks.singlePlayer.KBFEvoMCTS.MCTS;

import core.game.Event;
import java.util.HashMap;
import ontology.Types;
import org.apache.log4j.Level;
import tools.Vector2d;

/**
 *
 * @author hauck
 */
public class Util {
    
    public static int softmax(double[] strenght, int size){
        double sum = 0;
        for(int i = 0; i < size; i++){
            sum += Math.pow(Math.E, -strenght[i]);   
        }
        RandomCollection rc = new RandomCollection();
        
        //MCTS.LOGGER.log(Level.INFO, "Actions through Softmax");
        for(int i = 0; i < size; i++){
            double value = Math.pow(Math.E, -strenght[i])/sum;
            MCTS.LOGGER.log(Level.WARN, i+" "+strenght[i]+" --> "+value);
            rc.add(value, i);
        }
        
        /*int[] results = {0, 0, 0, 0};
        for(int i =0; i < 100; i++){
            results[(int)rc.next()] += 1;
        }
        for(int i =0; i < size; i++){
            System.out.println(i+": "+results[i]);
        }*/
        return (int) rc.next();
        
    }
       
    public static int getCantorPairingId(int a, int b){
        return (a + b) * (a + b + 1) / 2 + a;
    }
    
    public static double calculateGridDistance(Vector2d a, Vector2d b, double blockSize){
        Vector2d grid_a = new Vector2d(a.x/blockSize, a.y/blockSize);
        Vector2d grid_b = new Vector2d(b.x/blockSize, b.y/blockSize);
        return grid_a.dist(grid_b);
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
    
    public static void printEvent(Event e){
        System.out.println("GameStep: "+e.gameStep);
        System.out.println("Collision With Player: "+(e.fromAvatar?"NO":"YES"));
        System.out.println("Sprite Collided with: "+e.passiveSpriteId);
        System.out.println("Position: "+e.position.toString());

    }
    
    public static void testSampleMatrix(RandomCollection<WeightMatrix> matrix_collection){
        System.out.println("Sampling from collection of "+matrix_collection.map.size()+" matrices");
        for(Double d: matrix_collection.map.keySet()){
            System.out.println("Score: "+matrix_collection.map.get(d).fitness);
        }
        if(matrix_collection.map.keySet().size() > 5){
            HashMap<WeightMatrix, Integer> myhash = new HashMap<>();
            for(int i = 0; i < 1500; i++){
                WeightMatrix wm = matrix_collection.next();
                Integer oc = myhash.get(wm);
                if(oc == null){
                    myhash.put(wm, 1);
                }else{
                    myhash.put(wm, oc+1);
                }
            }
            for(Double d: matrix_collection.map.keySet()){  
                System.out.println("Score: "+matrix_collection.map.get(d).fitness);
                System.out.println("Occurences: "+myhash.get(matrix_collection.map.get(d)));
            }
            System.exit(0);
        }
    }
}

