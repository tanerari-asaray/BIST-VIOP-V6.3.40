import tr.borsatakip.v5.analysis.*
import tr.borsatakip.v5.model.Candle
import kotlin.math.*
import kotlin.random.Random
var passed=0;var failed=0
fun checkTest(name:String,f:()->Unit){try{f();passed++}catch(e:Throwable){failed++;println("FAIL $name ${e.message}")}}
fun make(n:Int,step:Double=.15)= (0 until n).map{i-> val p=100+i*step;Candle(1_750_000_200_000+i*300_000L,p,p+1,p-1,p,1000.0+i)}
fun snap(c:List<Candle>,t:ChartTimeframe=ChartTimeframe.FIVE_MIN)=ReadableStockAnalysis.analyze(c,t)
fun close(a:Double,b:Double)=check(abs(a-b)<1e-7){"$a != $b"}
fun main(){
for(n in 0..30)checkTest("insufficient-$n"){check(snap(make(n))==null)}
checkTest("31 valid"){check(snap(make(31))!=null)}
checkTest("rising"){check(snap(make(60))!!.direction==ReadableStockAnalysis.Direction.RISING)}
checkTest("falling"){check(snap(make(60,-.15))!!.direction==ReadableStockAnalysis.Direction.FALLING)}
checkTest("mixed"){check(snap(make(60,0.0))!!.direction==ReadableStockAnalysis.Direction.MIXED)}
checkTest("last unconfirmed bar excluded"){val x=make(60);val y=x.dropLast(1)+x.last().copy(open=1.0,high=2000.0,low=1.0,close=2000.0,volume=1e9);check(snap(x)==snap(y))}
checkTest("invalid quotes rejected"){check(snap(make(60).map{it.copy(high=0.0)})==null)}
checkTest("invalid volumes rejected"){check(snap(make(60).map{it.copy(volume=-100.0)})==null)}
checkTest("nan rejected"){check(snap(make(60).map{it.copy(close=Double.NaN)})==null)}
checkTest("support window"){val x=make(60);val a=snap(x)!!;close(a.support,x.subList(38,58).minOf{it.low});close(a.resistance,x.subList(38,58).maxOf{it.high})}
checkTest("volume ratio"){val a=snap(make(60))!!;check(a.volumeRatio!=null&&a.volumeRatio>0)}
checkTest("ATR RSI values"){val a=snap(make(60))!!;check(a.rsi?.let{it in 0.0..100.0} == true && a.atrPercent?.let{it>0}==true)}
checkTest("10min aggregation"){val x=make(60);val a=OhlcvResampler.aggregate(x,5,10);check(a.size==30);a.forEachIndexed{i,c->close(c.open,x[i*2].open);close(c.close,x[i*2+1].close);close(c.volume,x[i*2].volume+x[i*2+1].volume)}}
checkTest("incomplete 10 min dropped"){check(OhlcvResampler.aggregate(make(60).drop(1),5,10).size==29)}
checkTest("ema seed"){close(ChartIndicators.calculate(make(21)).ema21.last()!!,make(21).map{it.close}.average())}
checkTest("rsi constant"){close(ChartIndicators.calculate(make(50,0.0)).rsi.last()!!,50.0)}
checkTest("rsi rising"){close(ChartIndicators.calculate(make(50)).rsi.last()!!,100.0)}
checkTest("no daily vwap"){check(ChartIndicators.calculate(make(50),false).vwap.all{it==null})}
for(seed in 0 until 150)checkTest("property-$seed"){val r=Random(seed);val n=r.nextInt(31,800);val x=make(n,r.nextDouble(-.06,.06));val a=snap(x)!!;check(a.support>0&&a.support<=a.resistance);check(a.rsi==null||a.rsi in 0.0..100.0);val y=x.dropLast(1)+x.last().copy(open=1.0,high=2000.0,low=1.0,close=1999.0,volume=1e8);check(a==snap(y))}
println("RESULT $passed PASS $failed FAIL")
if(failed>0)System.exit(1)
}
