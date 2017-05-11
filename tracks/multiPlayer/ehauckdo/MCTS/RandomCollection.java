package tracks.multiPlayer.ehauckdo.MCTS;

import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/*
    * @Author Peter Lawrey
    * Source: http://stackoverflow.com/questions/6409652/random-weighted-selection-in-java
 */
public class RandomCollection<E> {

    public final NavigableMap<Double, E> map = new TreeMap<Double, E>();
    private double total = 0;

    public void add(double weight, E result) {
        if (map.containsValue(result)) {
            return;
        }
        total += weight;
        map.put(total, result);
    }

    public E next() {
        double value = ThreadLocalRandom.current().nextDouble() * total;
        return map.ceilingEntry(value).getValue();
    }
    
    public void clear(){
        map.clear();
        total = 0;
    }
    
    public boolean isEmpty(){
        return map.isEmpty();
    }
    
    public int size(){
        return map.size();
    }
    
}
