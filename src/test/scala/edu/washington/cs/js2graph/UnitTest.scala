package edu.washington.cs.js2graph

import com.ibm.wala.cast.js.ssa.JavaScriptPropertyWrite
import com.ibm.wala.ssa.{ConstantValue, SymbolTable}
import org.junit.Assert._
import org.junit.Test

class UnitTest {
  @Test def testGetModuleFieldNames(): Unit = {
    var symTable = new SymbolTable(3)
    val memberRefIdx = 3
    val valueIdx = 4
    val writeInstruction = new JavaScriptPropertyWrite(1, 2, memberRefIdx, valueIdx)
    // Empty dataDeps and empty symbol table

    assertEquals(EntrypointAnalysis.getModuleFieldNames(symTable, writeInstruction, Map()), Set[String]())
    for (name <- Constants.moduleFieldNames) {
      // Non empty: v2.v3 = v4, in which v3 is one of Constants.moduleFieldNames and v3 is "exampleSubField".
      // In this case, getModuleFieldNames should return { "exampleSubField" }
      val subFieldName = "exampleSubField"
      val dataDeps = Map[AbsPath, Set[AbsVal]](
        AbsPath.Local(valueIdx) -> Set(AbsVal.HasField(subFieldName))
      )
      symTable = new SymbolTable(3)
      symTable.setConstantValue(memberRefIdx, new ConstantValue(name))
      assertEquals(EntrypointAnalysis.getModuleFieldNames(symTable, writeInstruction, dataDeps), Set[String](subFieldName))
    }
  }
}
