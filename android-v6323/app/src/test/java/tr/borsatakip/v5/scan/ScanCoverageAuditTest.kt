package tr.borsatakip.v5.scan
import org.junit.Assert.*
import org.junit.Test
class ScanCoverageAuditTest {
 @Test fun partitionsCoverage() {val x=ScanCoverageAudit.reconcile(listOf("A","B","C","D"),listOf("A"),listOf("B"),listOf("C"));assertEquals(1,x.unexplained);assertTrue(x.partitionConsistent)}
 @Test fun unexpectedSymbolsAreLogged() {val x=ScanCoverageAudit.reconcile(listOf("A","A","B"),listOf("A","Z"),emptyList(),emptyList());assertEquals(1,x.duplicateUniverseSymbols);assertEquals(listOf("Z"),x.unexpectedFetchedSymbols)}
 @Test fun fetchedWinsOverError() {val x=ScanCoverageAudit.reconcile(listOf("A"),listOf("A"),listOf("A"),listOf("A"));assertTrue(x.complete)}
}
