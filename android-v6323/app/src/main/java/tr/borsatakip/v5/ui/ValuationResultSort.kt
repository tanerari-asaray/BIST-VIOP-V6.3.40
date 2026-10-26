package tr.borsatakip.v5.ui

import tr.borsatakip.v5.data.FullUniversePolicy
import tr.borsatakip.v5.scan.FundamentalValuationEngine as Engine

/** Null/invalid values sort last; net-cash positions do not enter leveraged-company debt ranks. */
object ValuationResultSort {
    enum class Order(val label:String) {
        PRICE_ASC("Fiyat: düşükten yükseğe"),
        PE_ASC("Sektör içi F/K: düşükten yükseğe"),
        PB_ASC("Sektör içi PD/DD: düşükten yükseğe"),
        DEBT_ASC("Net Borç/FAVÖK: düşükten yükseğe"),
        ROE_DESC("Özkaynak kârlılığı: yüksekten düşüğe"),
        PROFIT_GROWTH_DESC("Kâr büyümesi: yüksekten düşüğe")
    }
    fun sort(items:List<FullUniversePolicy.Entry>, valuations:Map<String,Engine.Assessment>, order:Order,
             valuationMode:Boolean):List<FullUniversePolicy.Entry> {
        val applied=if(!valuationMode) Order.PRICE_ASC else order
        fun value(e:FullUniversePolicy.Entry):Double? {
            val a=valuations[e.symbol]
            if(applied==Order.PRICE_ASC) return e.price?.takeIf{it.isFinite()&&it>0}
            if(e.market!=FullUniversePolicy.Market.BIST || a?.snapshot==null ||
                Engine.validate(a.snapshot,System.currentTimeMillis())!=null) return null
            val m=a.multiples ?: return null
            return when(applied) {
                Order.PE_ASC -> m.pe
                Order.PB_ASC -> m.pb
                Order.DEBT_ASC -> if(a.snapshot.businessType==Engine.BusinessType.NON_FINANCIAL &&
                    (a.snapshot.netDebtTry?:-1.0)>=0) m.netDebtToEbitda else null
                Order.ROE_DESC -> m.roe
                Order.PROFIT_GROWTH_DESC -> Engine.profitGrowthPct(a.snapshot)
                else -> e.price
            }?.takeIf{it.isFinite()}
        }
        return items.sortedWith(compareBy<FullUniversePolicy.Entry> { value(it)==null }
            .thenComparator { a,b ->
                val av=value(a);val bv=value(b)
                if(av==null||bv==null) 0 else if(applied in setOf(Order.ROE_DESC,Order.PROFIT_GROWTH_DESC))
                    bv.compareTo(av) else av.compareTo(bv)
            }.thenBy { it.symbol })
    }
}
