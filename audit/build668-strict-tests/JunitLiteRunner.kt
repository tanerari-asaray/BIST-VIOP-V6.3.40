fun main() {
 val classes = listOf("tr.borsatakip.v5.analysis.ReadableStockAnalysisTest", "tr.borsatakip.v5.analysis.Build668ScreenContractTest")
 var passed = 0; var failed = 0
 for (klass in classes) {
  val c = Class.forName(klass)
  val instance = c.getConstructor().newInstance()
  for (method in c.declaredMethods.filter{it.isAnnotationPresent(org.junit.Test::class.java)}) {
   try {method.invoke(instance);println("PASS $klass.${method.name}");passed++} catch(t:Throwable){println("FAIL $klass.${method.name}: ${t.cause?:t}");failed++}
  }
 }
 println("JUNIT_COMPAT $passed PASS $failed FAIL")
 if (failed>0)System.exit(1)
}
