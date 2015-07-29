package fape.core.planning.heuristics.relaxed;

import fape.core.planning.grounding.Fluent;
import fape.core.planning.grounding.GAction;
import fape.core.planning.grounding.GStateVariable;
import fape.core.planning.states.State;
import fape.util.Utils;
import planstack.anml.model.concrete.Action;
import planstack.anml.model.concrete.TPRef;

import java.util.Collection;
import java.util.Iterator;

public abstract class DomainTransitionGraph {

    private static int nextDTGID = 1;
    private static int getNextDTGID() {
        nextDTGID += 2;
        return nextDTGID -2;
    }

    public DomainTransitionGraph() {
        id = getNextDTGID();
    }

    final int id;
    public boolean hasBeenExtended = false;

    public class DTNode {

        public final int lvl;
        public final int containerID;
        public final Fluent value;
        public final TPRef start;
        public final TPRef end;

        public DTNode(Fluent value, int containerID) {
            this.value = value;
            this.lvl = 0;
            assert containerID == id();
            this.containerID = containerID;
            start = null;
            end = null;
        }
        public DTNode(Fluent value, int lvl, int containerID, TPRef start, TPRef end) {
            this.value = value;
            this.lvl = lvl;
            this.containerID = containerID;
            assert containerID == id();
            this.start = start;
            this.end = end;
        }

        /**
         * Returns true if the node n (a fluent value and a temporal interval) can be merged
         * in this node (i.e. same value and the interval of n fits into this one).
         */
        public boolean canSupportValue(Fluent f, TPRef start, TPRef end, State st) {
            if(!hasFluent(f))
                return false;
            if(this.start != null && start != null && !st.canBeBefore(this.start, start))
                return false;
            if(this.end != null && end != null && !st.canBeBefore(end, this.end))
                return false;
            return true;
        }

        public boolean hasSameFluent(DTNode n) {
            if(value == null) return n.value == null;
            else if(n.value == null) return false;
            else return n.value.equals(value);
        }
        public boolean hasFluent(Fluent f) {
            assert f != null;
            if(value == null) return false;
            else return value.equals(f);
        }

        @Override public int hashCode() {
            return (value != null ? value.hashCode() : 0) + lvl;
        }
        @Override public boolean equals(Object o) {
            if(o instanceof DTNode)
                return lvl == ((DTNode) o).lvl && Utils.eq(value, ((DTNode) o).value) && containerID == ((DTNode) o).containerID;
            else
                return false;
        }

        @Override public String toString() {
            String base = isAccepting(this) ? "(acc) " : "";
            if(value != null) return "("+containerID+") "+base+value.toString()+" "+lvl;
            else return "("+containerID+") "+base+"null "+lvl;
        }
    }

    public static class DTEdge {
        public final DTNode from;
        public final DTNode to;
        public final Action act;
        public final GAction ga;
        public DTEdge(DTNode from, DTNode to, Action act, GAction ga) {
            assert from != null;
            assert to != null;
            this.act = act;
            this.ga = ga;
            this.from = from;
            this.to = to;
        }

        public GStateVariable sv() {
            return to.value.sv;
        }
        @Override public String toString() {
            return from +" "+ga+"  "+to;
//            if(act != null) return "ag";
//            if(ga != null) return "g"; //return ga.toString();
//            else return "";
        }
    }

    public abstract Iterator<DTEdge> inEdges(DTNode n);
    public abstract Iterator<DTEdge> outEdges(DTNode n);

    public abstract DTNode startNodeForFluent(Fluent f);

    public final int id() { return id; }

    public DTNode baseNode(Fluent f) {
        return new DTNode(f, 0, id(), null, null);
    }

    public abstract boolean isAccepting(DTNode n);

    public abstract DTNode possibleEntryPointFrom(DTNode n);

    public abstract Collection<DTNode> unifiableNodes(Fluent f, TPRef start, TPRef end, State st);

    /** Returns true if the cost of this edge should not be counted (already accounted for another local solution) */
    public abstract boolean isFree(DTEdge e);

    public abstract Collection<DTNode> getAllNodes();
}
