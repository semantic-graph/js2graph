package com.ibm.wala.cast.js.translator;

import com.ibm.wala.cast.ir.translator.TranslatorToCAst;
import com.ibm.wala.cast.tree.CAst;
import com.ibm.wala.classLoader.ModuleEntry;

public class PatchedCAstRhinoTranslatorFactory implements JavaScriptTranslatorFactory {

    @Override
    public TranslatorToCAst make(CAst ast, ModuleEntry M) {
        return new PatchedCAstRhinoTranslator(M, false);
    }
}