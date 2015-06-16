package fape.core.planning.planninggraph;

import fape.core.inference.HReasoner;
import fape.core.inference.Predicate;
import fape.core.inference.Term;
import fape.core.planning.planner.APlanner;
import fape.core.planning.states.State;
import planstack.anml.model.LVarRef;
import planstack.anml.model.abs.AbstractAction;
import planstack.anml.model.abs.AbstractActionRef;
import planstack.anml.model.abs.AbstractDecomposition;
import planstack.anml.model.concrete.*;
import planstack.constraints.bindings.ValuesHolder;
import planstack.structures.Pair;

import java.util.*;

public class FeasibilityReasoner {

    final APlanner planner;
    public Map<String, LVarRef[]> varsOfAction = new HashMap<>();
    public Map<String, LVarRef[]> varsOfDecomposition = new HashMap<>();
    private Set<GAction> allActions;
//    final Set<GAction> filteredActions;

    /** Maps ground actions from their ID */
    public final HashMap<Integer, GAction> gactions = new HashMap<>();
    public final HashMap<ActRef, VarRef> groundedActVariable = new HashMap<>();
    public final HashMap<ActRef, VarRef> decompositionVariable = new HashMap<>();

    final HReasoner<Term> baseReasoner;
    public final GroundProblem base;

    public FeasibilityReasoner(APlanner planner, State initialState) {
        this.planner = planner;
        // this Problem contains all the ground actions
        base = new GroundProblem(initialState.pb);
        allActions = new HashSet<>(base.gActions);

        baseReasoner = new HReasoner<>();
        for(GAction ga : allActions) {
            ga.addClauses(baseReasoner);
        }
        for(Term t : baseReasoner.trueFacts())
            if(t instanceof Predicate && ((Predicate) t).name.equals("derivable"))
            System.out.println("  "+t);
        System.out.println(baseReasoner.trueFacts());

        for(GAction ga : allActions) {
            initialState.csp.bindings().addPossibleValue(ga.id);
            assert(!gactions.containsKey(ga.id));
            gactions.put(ga.id, ga);
        }

        for(GAction ga : allActions) {
            if(!varsOfAction.containsKey(ga.abs.name())) {
                varsOfAction.put(ga.abs.name(), ga.baseVars);
            }
        }

        allActions = getAllActions(initialState);
        base.gActions.clear();
        base.gActions.addAll(allActions);

        for(GAction act : allActions) {
            LinkedList<InstanceRef> args = new LinkedList<>();
            for(LVarRef var : act.abs.args())
                args.add(act.valueOf(var));
            GTaskCond tc = new GTaskCond(act.abs, args);
        }

        // get the maximum number of decompositions in the domain
        int maxNumDecompositions = 0;
        for(AbstractAction aa : initialState.pb.abstractActions()) {
            maxNumDecompositions = maxNumDecompositions > aa.jDecompositions().size() ?
                    maxNumDecompositions : aa.jDecompositions().size();
        }

        // create values for the decomposition variables
        List<String> decompositionVariablesDomain = new LinkedList<>();
        for(int i=0 ; i<maxNumDecompositions ; i++) {
            initialState.csp.bindings().addPossibleValue(decCSPValue(i));
        }

        for(GAction ga : allActions) {
            if(!varsOfAction.containsKey(ga.abs.name())) {
                varsOfAction.put(ga.abs.name(), ga.baseVars);
            }
            if(ga.decID != -1 && !varsOfDecomposition.containsKey(new Pair<>(ga.baseName(), ga.decID))) {
                varsOfDecomposition.put(ga.decomposedName(), ga.decVars);
            }

            // all variables of this action
            List<String> values = new LinkedList<>();
            for(LVarRef var : varsOfAction.get(ga.abs.name()))
                values.add(ga.valueOf(var).instance());

            initialState.csp.bindings().addValuesToValuesSet(ga.abs.name(), values, ga.id);
        }
    }

    public static String decCSPValue(int decNumber) {
        return "decnum:"+decNumber;
    }

    public Set<GAction> getAllActions(State st) {
        if(st.addableGroundActions != null)
            return st.addableGroundActions;

        HReasoner<Term> r = getReasoner(st);
        HashSet<GAction> feasibles = new HashSet<>();
        for(Term t : r.trueFacts()) {
            if(t instanceof Predicate && ((Predicate) t).name.equals("possible_in_plan")) //TODO make a selector for this
                feasibles.add((GAction) ((Predicate) t).var);
        }
        st.addableGroundActions = feasibles;
        return feasibles;
    }

    public HReasoner<Term> getReasoner(State st) {
        return getReasoner(st, allActions);
    }

    private HReasoner<Term> getReasoner(State st, Collection<GAction> acceptable) {
        if(st.reasoner != null)
            return st.reasoner;

        HReasoner<Term> r = new HReasoner<>(baseReasoner);
        for(Fluent f : GroundProblem.allFluents(st)) {
            r.set(f);
        }

        for(GAction acc : acceptable)
            r.set(new Predicate("acceptable", acc));

        for(ActionCondition ac : st.getOpenTaskConditions()) {
            LinkedList<List<InstanceRef>> varDomains = new LinkedList<>();
            for(VarRef v : ac.args()) {
                varDomains.add(new LinkedList<InstanceRef>());
                for(String value : st.domainOf(v)) {
                    varDomains.getLast().add(st.pb.instance(value));
                }
            }
            List<List<InstanceRef>> instantiations = PGUtils.allCombinations(varDomains);
            for(List<InstanceRef> instantiation : instantiations) {
                GTaskCond task = new GTaskCond(ac.abs(), instantiation);
                r.set(new Predicate("derivable_task", task));
            }
        }

        for(Action a : st.getAllActions()) {
            for(GAction ga : groundedVersions(a, st)) {
                r.set(new Predicate("in_plan", ga));
            }
        }

        for(Action a : st.getOpenLeaves()) {
            for(Integer gActID : st.csp.bindings().domainOfIntVar(groundedActVariable.get(a.id()))) {
                GAction ga = gactions.get(gActID);
                for(GTaskCond tc : ga.subTasks)
                    r.set(new Predicate("derivable_task", tc));
            }
        }

        Set<GAction> feasibles = new HashSet<>();

        for(Term t : r.trueFacts()) {
            if(t instanceof Predicate && ((Predicate) t).name.equals("possible_in_plan")) //TODO make a selector for this
                feasibles.add((GAction) ((Predicate) t).var);
        }

        // continue until a fixed point is reached
        if(feasibles.size() < acceptable.size()) {
            // number of possible actions was reduced, keep going
            return getReasoner(st, feasibles);
        } else {
            // fixed point reached
            st.reasoner = r;
            return r;
        }
    }

    public boolean checkFeasibility(State st) {
        Set<GAction> acts = getAllActions(st);

        for(Action a : st.getUnmotivatedActions()) {
            boolean derivable = false;
            for(GAction ga : groundedVersions(a, st)) {
                if (acts.contains(ga)) {
                    derivable = true;
                    break;
                }
            }
            if(!derivable) {
                // this unmotivated action cannot be derived from the current HTN
                return false;
            }
        }

        for(Action a : st.getAllActions()) {
            boolean feasibleAct = false;
            for(GAction ga : groundedVersions(a, st)) {
                if(st.reasoner.isTrue(new Predicate("possible_in_plan", ga))) {
                    feasibleAct = true;
                    break;
                }

            }
            if(!feasibleAct) {
                // there is no feasible ground versions of this action
                return false;
            }
        }

//        for(Timeline cons : st.consumers) {
//            System.out.print(" "+st.consumers.size());
//            GroundProblem subpb = new GroundProblem(pb, st, cons);
//            RelaxedPlanningGraph rpg = new RelaxedPlanningGraph(subpb, derivableOnly);
//            int depth = rpg.buildUntil(new DisjunctiveFluent(cons.stateVariable, cons.getGlobalConsumeValue(), st));
//            if(depth > 1000) {
//                // this consumer cannot be derived (none of its ground versions appear in the planning graph)
//                return false;
//            }
//        }
        Set<AbstractAction> addableActions = new HashSet<>();
        for(GAction ga : getAllActions(st))
            addableActions.add(ga.abs);
        Set<AbstractAction> nonAddable = new HashSet<>(st.pb.abstractActions());
        nonAddable.removeAll(addableActions);
        st.notAddable = nonAddable;

        return true;
    }

    public Set<GAction> groundedVersions(Action a, State st) {
        Set<GAction> ret = new HashSet<>();
        assert(groundedActVariable.containsKey(a.id()));
        for(Integer i : st.csp.bindings().domainOfIntVar(this.groundedActVariable.get(a.id())))
            ret.add(gactions.get(i));

        return ret;
    }

    /** This will associate with an action a variable in the CSP representing its
     * possible ground versions.
     * @param act Action for which we need to create the variable.
     * @param st  State in which the action appears (needed to update the CSP)
     */
    public void createGroundActionVariables(Action act, State st) {
        assert !groundedActVariable.containsKey(act.id()) : "The action already has a variable for its ground version.";

        // all ground versions of this actions (represented by their ID)
        LVarRef[] vars = varsOfAction.get(act.abs().name());
        List<VarRef> values = new LinkedList<>();
        for(LVarRef v : vars) {
            if(v.id().equals("__dec__")) {
                VarRef decVar = new VarRef();
                List<String> domain = new LinkedList<>();
                for(int i=0 ; i< act.decompositions().size() ; i++)
                    domain.add(decCSPValue(i));
                st.csp.bindings().AddVariable(decVar, domain, "decomposition_variable");
                decompositionVariable.put(act.id(), decVar);
                values.add(decVar);
            } else {
                values.add(act.context().getDefinition(v)._2());
            }
        }
        // Variable representing the ground versions of this action
        VarRef gAction = new VarRef();
        st.csp.bindings().AddIntVariable(gAction);
        values.add(gAction);
        groundedActVariable.put(act.id(), gAction);
        st.addValuesSetConstraint(values, act.abs().name());
    }

    private Map<AbstractAction, List<GAction>> groundedActs = new HashMap<>();

    public List<GAction> getGrounded(AbstractAction abs) {
        if(!groundedActs.containsKey(abs)) {
            List<GAction> grounded = new LinkedList<>();
            for (GAction a : allActions)
                if (a.abs == abs)
                    grounded.add(a);
            groundedActs.put(abs, grounded);
        }
        return groundedActs.get(abs);
    }

    public Set<GAction> actionsInState(State st, Set<GAction> rpgFeasibleActions) {
        Set<GAction> ret = new HashSet<>();
        ValuesHolder current = new ValuesHolder(new LinkedList<Integer>());
        for(Action a : st.getAllActions()) {
            assert(groundedActVariable.containsKey(a.id()));
            ValuesHolder toAdd = st.csp.bindings().rawDomain(groundedActVariable.get(a.id()));
            current = current.union(toAdd);
        }
        for(Integer gaRawID : current.values()) {
            Integer gaID = st.csp.bindings().intValueOfRawID(gaRawID);
            assert(gactions.containsKey(gaID));
            GAction ga = gactions.get(gaID);
            assert ga != null;
            if(rpgFeasibleActions.contains(ga))
                ret.add(gactions.get(gaID));
        }
        return ret;
    }
}
