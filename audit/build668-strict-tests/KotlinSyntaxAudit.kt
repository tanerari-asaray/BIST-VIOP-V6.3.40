import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File
fun main(args:Array<String>){
 val d=Disposer.newDisposable()
 try{
  val c=CompilerConfiguration();c.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,MessageCollector.NONE)
  val env=KotlinCoreEnvironment.createForProduction(d,c,EnvironmentConfigFiles.JVM_CONFIG_FILES)
  var failures=0
  for(arg in args){
   val f=File(arg);val k=KtPsiFactory(env.project).createFile(f.name,f.readText())
   val errors=PsiTreeUtil.collectElementsOfType(k,PsiErrorElement::class.java)
   for(e in errors){System.err.println("KOTLIN_SYNTAX_ERROR ${f.name}: ${e.errorDescription}");failures++}
  }
  println("KOTLIN_PARSER_FILES=${args.size} ERRORS=$failures")
  if(failures>0)System.exit(1)
 }finally{Disposer.dispose(d)}
}
