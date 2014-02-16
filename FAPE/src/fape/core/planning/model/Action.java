/*
 * Author:  Filip Dvořák <filip.dvorak@runbox.com>
 *
 * Copyright (c) 2013 Filip Dvořák <filip.dvorak@runbox.com>, all rights reserved
 *
 * Publishing, providing further or using this program is prohibited
 * without previous written permission of the author. Publishing or providing
 * further the contents of this file is prohibited without previous written
 * permission of the author.
 */
package fape.core.planning.model;

import fape.core.execution.model.ActionRef;
import fape.core.execution.model.Instance;
import fape.core.execution.model.Reference;
import fape.core.execution.model.TemporalConstraint;

import fape.core.planning.stn.TemporalVariable;
import fape.core.planning.temporaldatabases.events.TemporalEvent;
import fape.exceptions.FAPEException;
import fape.util.Pair;

import java.util.*;

/**
 * this is an action in the task network, it may be decomposed
 *
 * @author FD
 */
public class Action {

    public enum Status { FAILED, EXECUTED, EXECUTING, PENDING; }

    public static int idCounter = 0;
    public int mID = idCounter++;

    /**
     *
     */
    public float minDuration = -1.0f;
    public float maxDuration = -1.0f;

    /**
     *
     */
    public TemporalVariable start,
            /**
             *
             */
            end;

    /**
     *
     */
    public String name;
    //public List<ObjectVariable> parameters = new LinkedList<>(); // we should have all the parameters here

    /**
     *
     */
    public List<TemporalEvent> events = new LinkedList<>(); //all variables from the events map to parameters

    /**
     *
     */
    public List<Pair<List<ActionRef>, List<TemporalConstraint>>> refinementOptions; //those are the options how to decompose
    public List<Instance> params;
    public List<Reference> constantParams;
    public HashMap<Instance, ObjectVariableValues> parameterBindings = new HashMap<>();
    public Status status = Status.PENDING;

    /**
     *
     * @return
     */
    public boolean IsRefinable() {
        return refinementOptions.size() > 0 && decomposition == null;
    }
    public List<Action> decomposition; //this is the truly realized decomposition

    /**
     *
     * @return
     */
    public Action DeepCopy(List<TemporalEvent> updatedEvents) {
        Action a = new Action();
        a.status = this.status;
        a.mID = mID;
        a.params = this.params;
        a.constantParams = this.constantParams;
        if (this.decomposition == null) {
            a.decomposition = null;
        } else {
            a.decomposition = new LinkedList<>();
            for (Action b : this.decomposition) {
                a.decomposition.add(b.DeepCopy(updatedEvents));
            }
        }

        a.minDuration = this.minDuration;
        a.maxDuration = this.maxDuration;
        a.end = this.end;
        a.name = this.name;
        a.refinementOptions = this.refinementOptions;
        a.start = this.start;

        // events are mutable and hence need to be mapped to the events
        // cloned by the temporal database manager
        a.events = new LinkedList<>();
        for(TemporalEvent ev : this.events) {
            TemporalEvent updatedEv = null;
            for(TemporalEvent newEv : updatedEvents) {
                if(newEv.mID == ev.mID)
                    updatedEv = newEv;
            }
            if(updatedEv == null) {
                throw new FAPEException("Unable to find the updated event for " + ev);
            }
            a.events.add(updatedEv);
        }

        return a;
    }

    /**
     * Returns a reference where usage of a parameter is replaced by the appropriate variable.
     * @param ref
     * @return
     */
    public Reference BindedReference(Reference ref) {
        Reference ret = new Reference(ref);
        String first = ret.GetConstantReference();
        for(int i=0 ; i<params.size() ; i++) {
            if(params.get(i).name.equals(first)) {
                ret.ReplaceFirstReference(constantParams.get(i));
            }
        }
        return ret;

    }

    @Override
    public String toString() {
        return name;
    }

    public float GetCost() {
        return 1.0f;
    }

    /* TODO: recreate
    public List<String> ProduceParameters(State st) {
        List<String> ret = new LinkedList<>();
        String foundConstantValue = "";
        
        for (Instance i : this.params) {
            StateVariableValue val = null;
            //first search databases
            for (TemporalDatabase db : st.tdb.vars) {
                String param = db.actionAssociations.get(mID);
                if (param != null && param.equals(i.name)) {
                    StateVariable sv = db.domain.getFirst();
                    foundConstantValue = sv.name.split("\\.")[0];
                }
            }
            //then search event values
            for (TemporalEvent ev : this.events) {
                if (ev instanceof TransitionEvent) {
                    String fromVal = ((TransitionEvent) ev).from.valueDescription.split("\\.")[0];
                    String toVal = ((TransitionEvent) ev).to.valueDescription.split("\\.")[0];
                    if (fromVal.equals(i.name)) {
                        val = ((TransitionEvent) ev).from;
                    }
                    if (toVal.equals(i.name)) {
                        val = ((TransitionEvent) ev).to;
                    }
                } else if (ev instanceof PersistenceEvent) {
                    String value = ((PersistenceEvent) ev).value.valueDescription.split("\\.")[0];
                    if (value.equals(i.name)) {
                        val = ((PersistenceEvent) ev).value;
                    }
                }
            }
            if(val != null){
                StateVariableValue ev = (StateVariableValue)st.conNet.objectMapper.get(val.mID);
                foundConstantValue = ev.values.get(0);
            }
            //get the most recent version of the value
            
            if (foundConstantValue.equals("")) {
                throw new FAPEException("Cannot discover parameter value.");
            }
            ret.add(foundConstantValue);
        }
        return ret;
    }
    */

}
