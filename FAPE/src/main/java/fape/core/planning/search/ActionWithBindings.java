package fape.core.planning.search;


import planstack.anml.model.LVarRef;
import planstack.anml.model.abs.AbstractAction;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ActionWithBindings {
    public AbstractAction act;
    public Map<LVarRef, Collection<String>> values = new HashMap<>();
}