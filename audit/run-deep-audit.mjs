import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import * as diagnostics from '../server/gateway_diagnostics.mjs';
import {runDeepAudit} from './deep-audit-core.mjs';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const result=await runDeepAudit(p=>fs.readFileSync(path.join(root,p),'utf8'),diagnostics);
console.log(JSON.stringify({runtime:process.version,...result},null,2));
const defects=result.jsDiagnostics.failures.length>0 ||
  result.kotlinSourceExpressions.shortExcursion.some(x=>!x.conventionalEntryNotionalCorrect) ||
  !result.kotlinSourceContracts.automaticSuccess.reportAcceptsAWorkerOutcome;
process.exitCode=defects?1:0;
