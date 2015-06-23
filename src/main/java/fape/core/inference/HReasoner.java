package fape.core.inference;

import java.util.*;

public class HReasoner<T> {
    private static final int baseNumVars = 100;
    private static final int baseNumClause = 1000;

    private final HashMap<T,Integer> varsIds;
    private T[] vars;
    private final Reasoner res;

    private int numVars = 0;

    private boolean locked = false;

    public HReasoner() {
        vars = (T[]) new Object[baseNumVars];
        varsIds = new HashMap<>();
        res = new Reasoner(baseNumVars, baseNumClause);
    }

    public HReasoner(HReasoner<T> toCopy, boolean lock) {
        locked = lock;
        this.numVars = toCopy.numVars;
        this.res = new Reasoner(toCopy.res, lock);

        if(locked && toCopy.locked) {
            this.varsIds = toCopy.varsIds;
            this.vars = toCopy.vars;
        } else {
            this.varsIds = new HashMap<>(toCopy.varsIds);
            this.vars = Arrays.copyOfRange(toCopy.vars, 0, numVars);
        }
    }

    public void lock() {
        this.locked = true;
        res.lock();
    }

    public boolean hasTerm(T term) {
        return varsIds.containsKey(term);
    }

    public void addHornClause(T left, Collection<T> right) {
        assert !locked;
        addHornClause(left, (T[]) right.toArray());
    }

    public final void addHornClause(final T left, final T... right) {
        assert !locked;
        if(!varsIds.containsKey(left))
            addVar(left);
        int[] rightIds = new int[right.length];
        for(int i=0 ; i<right.length ; i++) {
            if (!varsIds.containsKey(right[i]))
                addVar(right[i]);
            rightIds[i] = id(right[i]);
        }

        res.addHornClause(id(left), rightIds);
    }

    public void set(T o) {
        if(!varsIds.containsKey(o))
            addVar(o);
        res.set(id(o));
    }

    public Collection<T> trueFacts() {
        List<T> facts = new ArrayList<>();
        for(int var=0 ; var< numVars; var++) {
            if(res.varsStatus[var]) {
                facts.add(vars[var]);
            }
        }
        return facts;
    }

    public boolean isTrue(T term) {
        assert varsIds.containsKey(term) : "Term "+term+" is not a recorded variable.";
        return res.varsStatus[id(term)];
    }

    private int id(T o) {
        return varsIds.get(o);
    }

    public void addVar(T o) {
        assert !locked : "Error adding variable to locked Reasoner: "+o;
        assert !varsIds.containsKey(o) : "Var already registered";
        if(vars.length <= numVars) {
            vars = Arrays.copyOf(vars, vars.length*2);
        }
        int id = numVars++;
        vars[id] = o;
        varsIds.put(o, id);
        res.addVar(id);
    }


    public static void main(String[] args) {
        HReasoner<String> res = new HReasoner<>();
        res.addHornClause("A", "a", "b");
        res.addHornClause("c", "A");
        res.addHornClause("B", "c", "b");
        res.addHornClause("d", "B");

        System.out.println(res.trueFacts());
        res.set("c");
        System.out.println(res.trueFacts());
        res.set("b");
        System.out.println(res.trueFacts());
        res.set("e");
    }
}
