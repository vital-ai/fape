package fape.core.planning.heuristics.temporal;

import fape.core.planning.grounding.GAction;
import fape.core.planning.grounding.GTaskCond;
import fape.core.planning.grounding.GroundProblem;
import fape.exceptions.FAPEException;
import fr.laas.fape.structures.Ident;
import fr.laas.fape.structures.ValueConstructor;
import lombok.ToString;
import lombok.Value;
import planstack.anml.model.AnmlProblem;
import planstack.anml.model.concrete.InstanceRef;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Value public class TempFluent {

    @Ident(DepGraphCore.Node.class) @ToString
    public static class DGFluent extends DepGraphCore.Node {
        public final String funcName;
        public final List<InstanceRef> argsAndValue;

        @ValueConstructor @Deprecated
        public DGFluent(String funcName, List<InstanceRef> argsAndValue) {
            this.funcName = funcName;
            this.argsAndValue = argsAndValue;
        }

        @Override public String toString() { return String.format("(%d) %s%s", getID(), funcName, argsAndValue); }

        public boolean isTask() { return funcName.startsWith("task-"); }
        public boolean isActionStart() { return funcName.startsWith("started-"); }

        public static DGFluent from(DeleteFreeActionsFactory.TempFluentTemplate template, GAction container, AnmlProblem pb, GStore store) {
            InstanceRef[] argsAndValue = new InstanceRef[template.args.length+1];
            for(int i=0 ; i<template.args.length ; i++) {
                argsAndValue[i] = container.valueOf(template.args[i], pb);
            }
            argsAndValue[argsAndValue.length-1] = container.valueOf(template.value, pb);
            return store.getFluent(template.funcName, Arrays.asList(argsAndValue)); //new Fluent(template.funcName, argsAndValue);
        }

        public static DGFluent from(fape.core.planning.grounding.Fluent f, GStore store) {
            InstanceRef[] args = Arrays.copyOf(f.sv.params, f.sv.params.length+1);
            args[args.length-1] = f.value;
            return store.getFluent(f.sv.f.name(), Arrays.asList(args)); //new Fluent(f.sv.f.name(), Arrays.asList(args));
        }
        public static DGFluent from(GTaskCond task, AnmlProblem pb, GStore store) {
            InstanceRef[] args = Arrays.copyOf(task.args, task.args.length+1);
            args[args.length-1] = pb.instance("true"); //TODO: fix this hack
            return store.getFluent("task-"+task.name, Arrays.asList(args)); //new Fluent("task-"+task.name, args);
        }
    }

    public final int time;
    public final DGFluent fluent;

    public static TempFluent from(DeleteFreeActionsFactory.TempFluentTemplate template, GAction container, GroundProblem pb, GStore store) {
        int time;
        if(template.time instanceof DeleteFreeActionsFactory.IntTime) {
            time = ((DeleteFreeActionsFactory.IntTime) template.time).getValue();
        } else if(template.time instanceof DeleteFreeActionsFactory.ParameterizedTime) {
            List<InstanceRef> params = ((DeleteFreeActionsFactory.ParameterizedTime) template.time).args.stream()
                    .map(v -> container.valueOf(v, pb.liftedPb))
                    .collect(Collectors.toList());
            time = pb.intInvariants.get(new GroundProblem.IntegerInvariantKey(((DeleteFreeActionsFactory.ParameterizedTime) template.time).f, params));
        } else {
            throw new FAPEException("Unsupported time template: "+template.time);
        }

        DGFluent fluent = DGFluent.from(template, container, pb.liftedPb, store);

        return new TempFluent(time, fluent);
    }
}
