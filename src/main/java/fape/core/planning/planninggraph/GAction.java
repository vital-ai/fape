package fape.core.planning.planninggraph;

import fape.exceptions.FAPEException;
import fape.exceptions.NotValidGroundAction;
import planstack.anml.model.AbstractParameterizedStateVariable;
import planstack.anml.model.AnmlProblem;
import planstack.anml.model.LVarRef;
import planstack.anml.model.PartialContext;
import planstack.anml.model.abs.AbstractAction;
import planstack.anml.model.abs.AbstractActionRef;
import planstack.anml.model.abs.statements.*;
import planstack.anml.model.concrete.EmptyVarRef;
import planstack.anml.model.concrete.InstanceRef;
import planstack.anml.model.concrete.VarRef;
import planstack.anml.model.concrete.statements.EqualityConstraint;
import planstack.anml.model.concrete.statements.Persistence;
import planstack.anml.parser.Instance;
import planstack.structures.Pair;
import scala.collection.JavaConversions;

import java.util.*;

public class GAction implements PGNode {

    public List<Fluent> pre = new LinkedList<>();
    public List<Fluent> add = new LinkedList<>();
    public final AbstractAction abs;

    public final LVarRef[] baseVars;
    protected final InstanceRef[] baseValues;
    public final LVarRef[] decVars;
    protected final InstanceRef[] decValues;

    public final int decID;
    public final ArrayList<GTaskCond> subTasks;

    private static int nextID = 0;
    public final int id;

    public GroundProblem.Invariant invariantOf(AbstractParameterizedStateVariable sv, GroundProblem gPb) {
        List<InstanceRef> params = new LinkedList<>();
        for(LVarRef v : sv.jArgs())
            params.add(valueOf(v, gPb.liftedPb));
        for(GroundProblem.Invariant inv : gPb.invariants) {
            if(inv.matches(sv.func(), params))
                return inv;
        }
        return null;
    }

    @Override
    public int hashCode() { return id; }

    @Override
    public boolean equals(Object o) {
        if(o instanceof GAction) return o == this;
        else if(o instanceof Integer) return this.id == (int) ((Integer) o);
        else return false;
    }

    public String baseName() { return abs.name(); }

    public String decomposedName() { return abs.name()+"["+decID+"]"; }

    public GAction(AbstractAction abs, int decID, Map<LVarRef, InstanceRef> vars, GroundProblem gPb) throws NotValidGroundAction {
        assert !(decID == -1 && abs.decompositions().size() != 0);
        AnmlProblem pb = gPb.liftedPb;
        this.abs = abs;
        assert decID < abs.jDecompositions().size();
        this.decID = decID;

        int numBaseVariables = 0;
        int numDecVariables = 0;
        for(LVarRef ref : vars.keySet()) {
            if(abs.context().contains(ref))
                numBaseVariables += 1;
            else
                numDecVariables += 1;
        }
        assert numDecVariables == 0 || decID != -1;

        if(decID != -1) // leave space for the decomposition variable
            numBaseVariables++;

        this.baseVars = new LVarRef[numBaseVariables];
        this.baseValues = new InstanceRef[numBaseVariables];
        this.decVars = new LVarRef[numDecVariables];
        this.decValues = new InstanceRef[numDecVariables];

        int iBase = 0, iDec=0;
        for(Map.Entry<LVarRef,InstanceRef> binding : vars.entrySet()) {
            if(abs.context().contains(binding.getKey())) {
                baseVars[iBase] = binding.getKey();
                baseValues[iBase] = binding.getValue();
                iBase++;
            } else {
                decVars[iDec] = binding.getKey();
                decValues[iDec] = binding.getValue();
                iDec++;
            }
        }
        if(decID != -1) { // the last one is a variable representing the number of the decomposition.
            baseVars[iBase] = new LVarRef("__dec__");
            baseValues[iBase] = new InstanceRef(PlanningGraphReachability.decCSPValue(decID));
        }

        List<AbstractStatement> statements;
        if(decID == -1) {
            statements = abs.jTemporalStatements();
        } else {
            statements = new LinkedList<>(abs.jTemporalStatements());
            statements.addAll(abs.jDecompositions().get(decID).jStatements());
        }

        for(AbstractStatement as : statements) {
            if(as instanceof AbstractEqualityConstraint) {
                AbstractEqualityConstraint ec = (AbstractEqualityConstraint) as;
                GroundProblem.Invariant inv = invariantOf(ec.sv(), gPb);
                if(inv == null || inv.value != valueOf(ec.variable(), pb)) {
                    throw new NotValidGroundAction("Action not valid1");
                }
            } else if(as instanceof AbstractInequalityConstraint) {
                AbstractInequalityConstraint ec = (AbstractInequalityConstraint) as;
                GroundProblem.Invariant inv = invariantOf(ec.sv(), gPb);
                if(inv == null || inv.value == valueOf(ec.variable(), pb)) {
                    throw new NotValidGroundAction("Action not valid2");
                }
            } else if(as instanceof AbstractVarEqualityConstraint) {
                AbstractVarEqualityConstraint ec = (AbstractVarEqualityConstraint) as;
                if(valueOf(ec.leftVar(), pb) != valueOf(ec.rightVar(), pb))
                    throw new NotValidGroundAction("Action not valid3");
            } else if(as instanceof AbstractVarInequalityConstraint) {
                AbstractVarInequalityConstraint ec = (AbstractVarInequalityConstraint) as;
                if(valueOf(ec.leftVar(), pb) == valueOf(ec.rightVar(), pb))
                    throw new NotValidGroundAction("Action not valid4");
            } else if(as instanceof AbstractTransition) {
                AbstractTransition t = (AbstractTransition) as;

                pre.add(fluent(t.sv(), t.from(), true, vars, pb));
                pre.add(fluent(t.sv(), t.from(), false, vars, pb));
                if(!fluent(t.sv(), t.from(), false, vars, pb).equals(fluent(t.sv(), t.to(), false, vars, pb))) {
                    add.add(fluent(t.sv(), t.to(), true, vars, pb));
                    add.add(fluent(t.sv(), t.to(), false, vars, pb));
                }
            } else if(as instanceof AbstractPersistence) {
                AbstractPersistence p = (AbstractPersistence) as;
                pre.add(fluent(p.sv(), p.value(), false, vars, pb));
            } else if(as instanceof AbstractAssignment) {
                AbstractAssignment a = (AbstractAssignment) as;
                add.add(fluent(a.sv(), a.value(), false, vars, pb));
                add.add(fluent(a.sv(), a.value(), true, vars, pb));
            }
        }

        // with temporal actions, a lot of actions can be self suportive
        pre.removeAll(add);

        this.id = nextID++;
        this.subTasks = initSubTasks(gPb.liftedPb);
    }

    @Override
    public String toString() {
        String ret = "("+id+")";
        ret += abs.name()+ (decID == -1 ? "" : "-"+decID) + "(";
        for(int j=0 ; j<abs.args().size() ; j++) {
            ret += valueOf(abs.args().get(j));
            if(j < abs.args().size()-1)
                ret += ", ";
        }
        ret +=") ";
        for(int i=0 ; i<baseVars.length ; i++) {
            if(!abs.args().contains(baseVars[i]))
                ret += baseVars[i] +":"+ baseValues[i]+" ";
        }
        for(int i=0 ; i<decVars.length ; i++) {
            if(!abs.args().contains(decVars[i]))
                ret += decVars[i] +":"+ decValues[i]+" ";
        }
        return ret;
    }

    /** Returns the instance corresponding to this local variable.
     *  This instance must have been declared in the action (ie. not a global variable. */
    public InstanceRef valueOf(LVarRef v) {
        for(int i=0 ; i<decVars.length ; i++)
            if(decVars[i].equals(v))
                return decValues[i];
        for(int i=0 ; i<baseVars.length ; i++)
            if(baseVars[i].equals(v))
                return baseValues[i];
        throw new FAPEException("This local variable was not found: "+v);
    }

    /** Returns the instance corresponding to this local ref.
     *  If this ref was not declared in the action, it will look for global variables. */
    public InstanceRef valueOf(LVarRef v, AnmlProblem pb) {
        // first look in local variables
        for(int i=0 ; i<decVars.length ; i++)
            if(decVars[i].equals(v))
                return decValues[i];
        for(int i=0 ; i<baseVars.length ; i++)
            if(baseVars[i].equals(v))
                return baseValues[i];

        // it does not appear in local variables, it is a global one
        return (InstanceRef) pb.context().getDefinition(v)._2();
    }

    public Fluent fluent(AbstractParameterizedStateVariable sv, LVarRef value, boolean partOfTransition, Map<LVarRef, InstanceRef> vars, AnmlProblem pb) {
        List<VarRef> svParams = new LinkedList<>();
        for(LVarRef v : sv.jArgs()) {
            svParams.add(valueOf(v, pb));
        }
        return new Fluent(sv.func(), svParams, valueOf(value, pb), partOfTransition);
    }

    /**
     * Returns all possible instanciation of the the abstract action in the given decomposition.
     * A possible instanciation is a map from all its local references to instances.
     *
     * If the decoposition ID is not -1, variables declared inside the decomposition will be accounted for as well.
     */
    public static List<Map<LVarRef, InstanceRef>> getPossibleInstanciations(GroundProblem gPb, AbstractAction aa, int decID) {
        AnmlProblem pb = gPb.liftedPb;

        // this will store all local variables, those can be defined as action parameters, inside the action or within the given decomposition
        List<LVarRef> allLocalVars = new LinkedList<>();
        // local vars from the action
        for(LVarRef ref : scala.collection.JavaConversions.asJavaIterable(aa.context().variables().keys()))
            allLocalVars.add(ref);
        if(decID >= 0) // local vars from the definition
            for(LVarRef ref : scala.collection.JavaConversions.asJavaIterable(aa.jDecompositions().get(decID).context().variables().keys()))
                allLocalVars.add(ref);

        // context from which to look for the definition of local variable
        PartialContext context;
        if(decID == -1) // no decomposition, take the action's context
            context = aa.context();
        else // take the context of the decomposition (which include the one of the action
            context = aa.jDecompositions().get(decID).context();

        List<LVarRef> vars = new LinkedList<>();
        List<List<InstanceRef>> possibleValues = new LinkedList<>();
        for(LVarRef ref : allLocalVars) {
            vars.add(ref);
            List<InstanceRef> varSet = new LinkedList<>();
            if(!context.getDefinition(ref)._2().isEmpty()) {
                assert (context.getDefinition(ref)._2() instanceof InstanceRef) :
                        "ERRROR: "+context.getDefinition(ref)._2();
                varSet.add((InstanceRef) context.getDefinition(ref)._2());
            } else {
                // get <></>ype of the argument and add all possible values to the argument list.
                List<String> instanceSet = pb.instances().instancesOfType(context.getType(ref));
                for (String instance : instanceSet) {
                    varSet.add(pb.instances().referenceOf(instance));
                }
            }
            possibleValues.add(varSet);
        }

        List<List<InstanceRef>> instanciations = PGUtils.allCombinations(possibleValues);
        List<Map<LVarRef, InstanceRef>> paramsLists = new LinkedList<>();

        for(List<InstanceRef> instanciation : instanciations) {
            Map<LVarRef, InstanceRef> params = new HashMap<>();
            assert instanciation.size() == vars.size();
            for(int i=0 ; i< vars.size() ; i++) {
                params.put(vars.get(i), instanciation.get(i));
            }
            paramsLists.add(params);
        }
        return paramsLists;
    }

    public static List<GAction> groundActions(GroundProblem gPb, AbstractAction aa) {
        // all ground actions corresponding to aa
        List<GAction> actions = new LinkedList<>();

        if(aa.jDecompositions().size() == 0) {
            List<Map<LVarRef, InstanceRef>> paramsLists = getPossibleInstanciations(gPb, aa, -1);
            for(Map<LVarRef, InstanceRef> params : paramsLists) {
                try {
                    actions.add(new GAction(aa, -1, params, gPb));
                } catch (NotValidGroundAction e) {}
            }
        } else {
            for(int decID=0 ; decID<aa.jDecompositions().size() ; decID++) {
                List<Map<LVarRef, InstanceRef>> paramsLists = getPossibleInstanciations(gPb, aa, decID);
                for(Map<LVarRef, InstanceRef> params : paramsLists) {
                    try {
                        actions.add(new GAction(aa, decID, params, gPb));
                    } catch (NotValidGroundAction e) {}
                }
            }
        }

        return actions;
    }

    public ArrayList<GTaskCond> getActionRefs() {
        return subTasks;
    }

    public ArrayList<GTaskCond> initSubTasks(AnmlProblem pb) {
        List<AbstractActionRef> refs;
        List<GTaskCond> ret = new LinkedList<>();
        if(decID == -1)
            refs = this.abs.jActions();
        else {
            refs = new LinkedList<>(this.abs.jActions());
            refs.addAll(this.abs.jDecompositions().get(decID).jActions());
        }
        for(AbstractActionRef ref : refs) {
            List<InstanceRef> args = new LinkedList<>();
            for(LVarRef v : ref.jArgs()) {
                args.add(valueOf(v, pb));
            }
            ret.add(new GTaskCond(pb.getAction(ref.name()), args));
        }

        ArrayList<GTaskCond> subTasks = new ArrayList<>(ret.size());
        for(int i=0 ; i<ret.size() ; i++)
            subTasks.add(ret.get(i));

        return subTasks;
    }
}
