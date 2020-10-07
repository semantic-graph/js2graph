package com.ibm.wala.cast.js.translator;

import com.ibm.wala.cast.ir.translator.RewritingTranslatorToCAst;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.classLoader.ModuleEntry;

public class PatchedCAstRhinoTranslator extends RewritingTranslatorToCAst {
    public PatchedCAstRhinoTranslator(ModuleEntry m, boolean replicateForDoLoops) {
        super(m, new PatchedRhinoToAstTranslator(new CAstImpl(), m, m.getName(), replicateForDoLoops));
    }
}